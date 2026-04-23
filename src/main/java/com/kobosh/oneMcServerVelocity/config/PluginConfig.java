package com.kobosh.oneMcServerVelocity.config;

import com.google.gson.*;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PluginConfig {

    private final Map<String, ServerEntry> servers = new LinkedHashMap<>();
    private String mongoConnectionString = "mongodb://mongo:27017";
    private String mongoDatabase = "onemcserver";
    private String limboHost = "mc";
    private int limboPort = 25565;
    private boolean kickLegacy = false;
    private final Map<String, String> translations = new HashMap<>();

    private static final Map<String, String> DEFAULT_TRANSLATIONS = Map.of(
            "domain.unknown.disconnect", "Unknown domain: %s",
            "domain.unknown.motd", "Unknown Domain",
            "server.offline.motd", "The server is currently offline.",
            "authentication.failed.disconnect", "That name is registered to a premium account. Please log in with your official account.",
            "token.invalid.disconnect", "Invalid verify token. Please restart your client and try again.",
            "online.mode.disconnect", "This server is in Online Mode. Please log in with your official account."
    );

    private Path configFile;
    private JsonObject rawRoot;

    public static PluginConfig load(Path dataDir, Logger logger) throws IOException {
        PluginConfig cfg = new PluginConfig();
        Path cfgPath = dataDir.resolve("config.json");
        cfg.configFile = cfgPath;

        if (!Files.exists(cfgPath)) {
            Files.createDirectories(dataDir);
            cfg.writeDefault(cfgPath);
            logger.info("Generated default config.json");
        }

        try (Reader reader = Files.newBufferedReader(cfgPath)) {
            JsonElement root = JsonParser.parseReader(reader);
            cfg.rawRoot = root.getAsJsonObject();
            cfg.parse(cfg.rawRoot);
        }

        return cfg;
    }

    private void parse(JsonObject root) {
        JsonArray serverArr = root.getAsJsonArray("servers");
        if (serverArr != null) {
            for (JsonElement el : serverArr) {
                JsonObject obj = el.getAsJsonObject();
                String host = obj.get("host").getAsString().toLowerCase();
                JsonArray transferTo = obj.getAsJsonArray("transfer_to");
                String tHost = transferTo.get(0).getAsString();
                int tPort = transferTo.get(1).getAsInt();
                boolean cracked = obj.has("cracked_players") && obj.get("cracked_players").getAsBoolean();
                servers.put(host, new ServerEntry(host, tHost, tPort, cracked));
            }
        }

        if (root.has("mongodb_connection_string")) {
            mongoConnectionString = root.get("mongodb_connection_string").getAsString();
        }
        if (root.has("mongodb_database")) {
            mongoDatabase = root.get("mongodb_database").getAsString();
        }
        if (root.has("kick_legacy")) {
            kickLegacy = root.get("kick_legacy").getAsBoolean();
        }

        if (root.has("limbo_server")) {
            JsonArray limboArr = root.getAsJsonArray("limbo_server");
            limboHost = limboArr.get(0).getAsString();
            limboPort = limboArr.get(1).getAsInt();
        }

        DEFAULT_TRANSLATIONS.forEach(translations::put);
        JsonObject trans = root.getAsJsonObject("translations");
        if (trans != null) {
            trans.entrySet().forEach(e -> translations.put(e.getKey(), e.getValue().getAsString()));
        }
    }

    private void writeDefault(Path path) throws IOException {
        String json = "{\n" +
                "  \"servers\": [\n" +
                "    {\n" +
                "      \"host\": \"play.example.com\",\n" +
                "      \"transfer_to\": [\"127.0.0.1\", 25565],\n" +
                "      \"cracked_players\": false\n" +
                "    }\n" +
                "  ],\n" +
                "  \"limbo_server\": [\"mc\", 25565],\n" +
                "  \"mongodb_connection_string\": \"mongodb://mongo:27017\",\n" +
                "  \"mongodb_database\": \"onemcserver\",\n" +
                "  \"kick_legacy\": false,\n" +
                "  \"translations\": {\n" +
                "    \"domain.unknown.disconnect\": \"Unknown domain: %s\",\n" +
                "    \"domain.unknown.motd\": \"Unknown Domain\",\n" +
                "    \"server.offline.motd\": \"The server is currently offline.\",\n" +
                "    \"authentication.failed.disconnect\": \"That name is registered to a premium account. Please log in with your official account.\",\n" +
                "    \"online.mode.disconnect\": \"This server is in Online Mode. Please log in with your official account.\"\n" +
                "  }\n" +
                "}\n";
        Files.writeString(path, json);
    }

    public ServerEntry getServer(String host) { return servers.get(host.toLowerCase()); }
    public Map<String, ServerEntry> getServers() { return servers; }
    public String getMongoConnectionString() { return mongoConnectionString; }
    public String getMongoDatabase() { return mongoDatabase; }
    public boolean isKickLegacy() { return kickLegacy; }
    public boolean hasLimbo() { return limboHost != null; }
    public String getLimboHost() { return limboHost; }
    public int getLimboPort() { return limboPort; }

    public String translation(String key, Object... args) {
        String text = translations.getOrDefault(key, key);
        if (args.length > 0 && text.contains("%s")) {
            return String.format(text, args);
        }
        return text;
    }
}
