package com.kobosh.oneMcServerVelocity.auth;

import com.google.gson.Gson;
import com.kobosh.oneMcServerVelocity.config.PluginConfig;
import com.kobosh.oneMcServerVelocity.database.DatabaseManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Handles Ed25519 key management, Mojang premium lookups (with DB cache),
 * and auth-cookie construction.
 *
 * Cookie format (mirrors Python proxy):
 *   <minified-JSON-bytes> + <hex-encoded-64-byte-Ed25519-signature-ASCII-bytes>
 *
 * Cookie key: onemcserver:auth
 */
public class AuthManager {

    public static final String COOKIE_CHANNEL = "onemcserver:auth";
    public static final net.kyori.adventure.key.Key COOKIE_KEY =
            net.kyori.adventure.key.Key.key("onemcserver", "auth");

    private PrivateKey privateKey;
    private String publicKeyHex;

    private final PluginConfig config;
    private final DatabaseManager db;
    private final Logger logger;
    private final HttpClient http;
    private final Gson gson = new Gson();

    // Ed25519 PKCS8 DER header (16 bytes) that wraps 32-byte raw seed
    private static final byte[] PKCS8_HEADER = {
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06,
            0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
    };
    // Ed25519 X.509 SubjectPublicKeyInfo DER header (12 bytes) that wraps 32-byte raw key
    private static final byte[] X509_HEADER = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
            0x70, 0x03, 0x21, 0x00
    };

    public AuthManager(PluginConfig config, DatabaseManager db, Logger logger) {
        this.config = config;
        this.db = db;
        this.logger = logger;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ── Key management ──────────────────────────────────────────────────────

    public void initKeys() throws Exception {
        String privHex = config.getPrivateKey().strip();
        String pubHex = config.getPublicKey().strip();

        if (privHex.isEmpty() || pubHex.isEmpty()) {
            generateAndSaveKeys();
        } else {
            loadKeys(privHex, pubHex);
        }
        logger.info("Ed25519 public key: {}", publicKeyHex);
    }

    private void generateAndSaveKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        privateKey = kp.getPrivate();

        byte[] privRaw = rawPrivateKeyBytes(kp.getPrivate());
        byte[] pubRaw = rawPublicKeyBytes(kp.getPublic());
        String privHex = HexFormat.of().formatHex(privRaw);
        String pubHex = HexFormat.of().formatHex(pubRaw);
        publicKeyHex = pubHex;

        config.saveKeys(privHex, pubHex);
        logger.info("Generated new Ed25519 key pair");
    }

    private void loadKeys(String privHex, String pubHex) throws Exception {
        byte[] privRaw = HexFormat.of().parseHex(privHex);
        byte[] pubRaw = HexFormat.of().parseHex(pubHex);

        byte[] pkcs8 = new byte[PKCS8_HEADER.length + 32];
        System.arraycopy(PKCS8_HEADER, 0, pkcs8, 0, PKCS8_HEADER.length);
        System.arraycopy(privRaw, 0, pkcs8, PKCS8_HEADER.length, 32);

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        publicKeyHex = pubHex;
    }

    /** Extract raw 32-byte seed from a Java Ed25519 PrivateKey. */
    private static byte[] rawPrivateKeyBytes(PrivateKey key) {
        byte[] enc = key.getEncoded(); // PKCS8, 48 bytes
        return Arrays.copyOfRange(enc, PKCS8_HEADER.length, enc.length);
    }

    /** Extract raw 32-byte key from a Java Ed25519 PublicKey. */
    private static byte[] rawPublicKeyBytes(PublicKey key) {
        byte[] enc = key.getEncoded(); // X.509, 44 bytes
        return Arrays.copyOfRange(enc, X509_HEADER.length, enc.length);
    }

    // ── Cookie building ─────────────────────────────────────────────────────

    /**
     * Build the onemcserver:auth cookie value.
     * Format: minified JSON bytes + hex-string bytes of Ed25519 signature (128 ASCII bytes).
     */
    public byte[] buildAuthCookie(String username, String uuid, boolean cracked) throws Exception {
        String json = gson.toJson(Map.of(
                "username", username,
                "uuid", uuid,
                "cracked", cracked,
                "time", System.currentTimeMillis() / 1000L
        ));
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(payload);
        byte[] signature = sig.sign();

        String sigHex = HexFormat.of().formatHex(signature); // 128 chars
        byte[] sigBytes = sigHex.getBytes(StandardCharsets.US_ASCII);

        byte[] result = new byte[payload.length + sigBytes.length];
        System.arraycopy(payload, 0, result, 0, payload.length);
        System.arraycopy(sigBytes, 0, result, payload.length, sigBytes.length);
        return result;
    }

    public void storeAuthCookie(Player player) throws Exception {
        boolean cracked = !player.isOnlineMode();
        byte[] cookie = buildAuthCookie(player.getUsername(), player.getUniqueId().toString(), cracked);
        player.storeCookie(COOKIE_KEY, cookie);
        logger.info("Stored auth cookie for {} (cracked={})", player.getUsername(), cracked);
    }

    // ── Mojang API ──────────────────────────────────────────────────────────

    public record PremiumProfile(String id, String name) {}

    /**
     * Check whether a username belongs to a premium account.
     * Results are cached in the DB for 1 hour.
     */
    public CompletableFuture<Optional<PremiumProfile>> checkPremium(String username, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // DB cache check
                var cached = db.getCachedProfile(username);
                if (cached.isPresent()) {
                    var cp = cached.get();
                    if (cp.premium()) return Optional.of(new PremiumProfile(cp.uuid(), username));
                    return Optional.empty();
                }

                // Mojang API
                String url = "https://api.mojang.com/users/profiles/minecraft/" + username;
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(4))
                        .GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> body = gson.fromJson(resp.body(), Map.class);
                    String id = body.get("id");
                    String name = body.get("name");
                    db.setCachedProfile(username, true, id);
                    return Optional.of(new PremiumProfile(id, name));
                } else {
                    // 204 or 404 → cracked / not found
                    db.setCachedProfile(username, false, null);
                    return Optional.<PremiumProfile>empty();
                }
            } catch (Exception e) {
                logger.warn("Mojang API error for {}: {}", username, e.getMessage());
                return Optional.<PremiumProfile>empty();
            }
        }, executor);
    }

    public String getPublicKeyHex() { return publicKeyHex; }
}
