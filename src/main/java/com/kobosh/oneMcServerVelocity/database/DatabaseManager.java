package com.kobosh.oneMcServerVelocity.database;

import com.kobosh.oneMcServerVelocity.config.PluginConfig;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB-backed store for:
 *  - user_cache: Mojang premium lookup results (TTL: 1 hour)
 *  - user_credentials: cracked-player passwords
 */
public class DatabaseManager implements AutoCloseable {

    private final MongoClient client;
    private final MongoCollection<Document> cacheCol;
    private final MongoCollection<Document> credentialsCol;
    private final Logger logger;

    public record CachedProfile(boolean premium, String uuid) {}
    public record Credentials(String password, String uuid) {}

    public DatabaseManager(PluginConfig config, Logger logger) {
        this.logger = logger;
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(config.getMongoConnectionString()))
                .build();
        client = MongoClients.create(settings);

        MongoDatabase db = client.getDatabase(config.getMongoDatabase());
        cacheCol = db.getCollection("user_cache");
        credentialsCol = db.getCollection("user_credentials");

        // Expire cache docs one hour after their creation timestamp.
        cacheCol.createIndex(
                Indexes.ascending("created"),
                new IndexOptions().expireAfter(3600L, TimeUnit.SECONDS)
        );

        logger.info("MongoDB initialized: {} ({})", config.getMongoDatabase(), config.getMongoConnectionString());
    }

    // ── user_cache ──────────────────────────────────────────────────────────

    public Optional<CachedProfile> getCachedProfile(String name) {
        Document d = cacheCol.find(Filters.eq("_id", name)).first();
        if (d == null) return Optional.empty();
        return Optional.of(new CachedProfile(d.getBoolean("microsoft", false), d.getString("uuid")));
    }

    public void setCachedProfile(String name, boolean premium, String uuid) {
        cacheCol.updateOne(
                Filters.eq("_id", name),
                Updates.combine(
                        Updates.set("microsoft", premium),
                        Updates.set("uuid", uuid),
                        Updates.set("created", new java.util.Date())
                ),
                new UpdateOptions().upsert(true)
        );
    }

    // ── user_credentials ────────────────────────────────────────────────────

    public Optional<Credentials> getCredentials(String name) {
        Document d = credentialsCol.find(Filters.eq("_id", name)).first();
        if (d == null) return Optional.empty();
        return Optional.of(new Credentials(d.getString("password"), d.getString("uuid")));
    }

    public void setCredentials(String name, String password, String uuid) {
        credentialsCol.updateOne(
                Filters.eq("_id", name),
                Updates.combine(
                        Updates.set("password", password),
                        Updates.set("uuid", uuid),
                        Updates.set("created", new java.util.Date())
                ),
                new UpdateOptions().upsert(true)
        );
    }

    public boolean changePassword(String name, String oldPassword, String newPassword) {
        Document creds = credentialsCol.find(Filters.eq("_id", name)).first();
        if (creds == null) return false;
        if (!oldPassword.equals(creds.getString("password"))) return false;

        credentialsCol.updateOne(
                Filters.eq("_id", name),
                Updates.combine(
                        Updates.set("password", newPassword),
                        Updates.set("created", new java.util.Date())
                )
        );
        return true;
    }

    public boolean hasCredentials(String name) {
        return credentialsCol.find(Filters.eq("_id", name)).limit(1).first() != null;
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            logger.warn("MongoDB close error", e);
        }
    }
}
