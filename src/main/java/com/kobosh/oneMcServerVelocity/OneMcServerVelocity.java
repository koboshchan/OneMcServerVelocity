package com.kobosh.oneMcServerVelocity;

import com.google.inject.Inject;
import com.kobosh.oneMcServerVelocity.auth.AuthManager;
import com.kobosh.oneMcServerVelocity.commands.LoginCommand;
import com.kobosh.oneMcServerVelocity.commands.RegisterCommand;
import com.kobosh.oneMcServerVelocity.commands.ChangePassCommand;
import com.kobosh.oneMcServerVelocity.config.PluginConfig;
import com.kobosh.oneMcServerVelocity.config.VelocityTomlUpdater;
import com.kobosh.oneMcServerVelocity.database.DatabaseManager;
import com.kobosh.oneMcServerVelocity.listeners.*;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Plugin(id = "onemcservervelocity", name = "OneMcServerVelocity", version = "${version}")
public class OneMcServerVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private DatabaseManager db;

    @Inject
    public OneMcServerVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            // Config
            PluginConfig config = PluginConfig.load(dataDir, logger);
            logger.info("Loaded config with {} server(s)", config.getServers().size());

            // Enforce required proxy settings in velocity.toml (applies after restart)
            boolean tomlChanged = VelocityTomlUpdater.enforce(dataDir, logger);
            if (tomlChanged) {
                logger.warn("velocity.toml was updated by onemcservervelocity; restarting proxy now so changes apply.");
                proxy.shutdown(Component.text("Restarting to apply enforced velocity.toml settings"));
                return;
            }

            // Geyser / Floodgate dependency check
            boolean geyserLoaded = proxy.getPluginManager().isLoaded("geyser");
            boolean floodgateLoaded = proxy.getPluginManager().isLoaded("floodgate");

            if (geyserLoaded) {
                if (!floodgateLoaded) {
                    logger.error("CRITICAL: Geyser is installed but Floodgate is not! Bedrock players cannot be authenticated securely.");
                    proxy.shutdown(Component.text("Geyser is installed but Floodgate is missing. Please install Floodgate to allow Bedrock players."));
                    throw new IllegalStateException("Geyser is installed but Floodgate is missing.");
                }

                // Check Geyser configuration auth-type
                String authType = parseGeyserAuthType(dataDir.getParent(), logger);
                if (authType != null && !authType.equals("floodgate")) {
                    logger.error("CRITICAL: Geyser's java.auth-type is set to '{}'! Bedrock players cannot be authenticated securely.", authType);
                    proxy.shutdown(Component.text("Geyser's auth-type is set to '" + authType + "'. Please edit plugins/Geyser-Velocity/config.yml and set java.auth-type to 'floodgate'."));
                    throw new IllegalStateException("Geyser java.auth-type is set to '" + authType + "', but must be set to 'floodgate'.");
                }
            }

            // Database
            db = new DatabaseManager(config, logger);

            // Auth manager
            AuthManager authManager = new AuthManager(db, logger);

            // Thread pool for async Mojang API calls
            Executor executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "onemcserver-worker");
                t.setDaemon(true);
                return t;
            });

            // Listeners
            PreLoginListener preLogin = new PreLoginListener(proxy, config, authManager, logger, executor);
            PostLoginListener postLogin = new PostLoginListener(preLogin, logger);
            PlayerChooseServerListener chooseServer =
                    new PlayerChooseServerListener(config, postLogin, proxy, logger);
            LimboTransferListener limboTransfer =
                    new LimboTransferListener(config, chooseServer, postLogin, logger);
            DisconnectListener disconnect = new DisconnectListener(preLogin, postLogin);
            PingPassthroughListener pingPassthrough =
                    new PingPassthroughListener(config, chooseServer, logger);

            proxy.getEventManager().register(this, preLogin);
            proxy.getEventManager().register(this, postLogin);
            proxy.getEventManager().register(this, chooseServer);
            proxy.getEventManager().register(this, limboTransfer);
            proxy.getEventManager().register(this, disconnect);
            proxy.getEventManager().register(this, pingPassthrough);

            // Commands
            LoginCommand loginCmd = new LoginCommand(db, postLogin, chooseServer, logger, executor);
            RegisterCommand registerCmd = new RegisterCommand(db, postLogin, chooseServer, logger, executor);
            ChangePassCommand changePassCmd = new ChangePassCommand(db, postLogin, logger, executor);

            proxy.getCommandManager().register(
                    proxy.getCommandManager().metaBuilder("login").plugin(this).build(), loginCmd);
            proxy.getCommandManager().register(
                    proxy.getCommandManager().metaBuilder("register").plugin(this).build(), registerCmd);
                proxy.getCommandManager().register(
                    proxy.getCommandManager().metaBuilder("changepass").plugin(this).build(), changePassCmd);
                logger.info("OneMcServerVelocity ready.");

        } catch (Exception e) {
            logger.error("Failed to initialise OneMcServerVelocity", e);
        }
    }

    private static String parseGeyserAuthType(Path pluginsDir, Logger logger) {
        Path[] candidates = {
            pluginsDir.resolve("Geyser-Velocity/config.yml"),
            pluginsDir.resolve("geyser/config.yml")
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    List<String> lines = Files.readAllLines(candidate);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("auth-type:")) {
                            String value = trimmed.substring("auth-type:".length()).trim();
                            // remove inline comments
                            int hashIndex = value.indexOf('#');
                            if (hashIndex != -1) {
                                value = value.substring(0, hashIndex).trim();
                            }
                            // remove quotes
                            value = value.replace("\"", "").replace("'", "");
                            return value.toLowerCase();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to read Geyser configuration at {}", candidate, e);
                }
            }
        }
        return null;
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (db != null) db.close();
    }
}
