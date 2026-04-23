package com.kobosh.oneMcServerVelocity.auth;

import com.google.gson.Gson;
import com.kobosh.oneMcServerVelocity.database.DatabaseManager;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Handles Mojang premium lookups with DB caching.
 */
public class AuthManager {
    private final DatabaseManager db;
    private final Logger logger;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public AuthManager(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
}
