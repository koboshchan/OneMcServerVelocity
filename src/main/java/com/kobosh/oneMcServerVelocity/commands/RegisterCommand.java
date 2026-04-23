package com.kobosh.oneMcServerVelocity.commands;

import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.kobosh.oneMcServerVelocity.database.DatabaseManager;
import com.kobosh.oneMcServerVelocity.listeners.PlayerChooseServerListener;
import com.kobosh.oneMcServerVelocity.listeners.PostLoginListener;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Handles /register <password> for cracked players on the limbo server.
 */
public class RegisterCommand implements SimpleCommand {

    private final DatabaseManager db;
    private final PostLoginListener postLogin;
    private final PlayerChooseServerListener chooseServer;
    private final Logger logger;
    private final Executor executor;

    public RegisterCommand(DatabaseManager db,
                           PostLoginListener postLogin, PlayerChooseServerListener chooseServer,
                           Logger logger, Executor executor) {
        this.db = db;
        this.postLogin = postLogin;
        this.chooseServer = chooseServer;
        this.logger = logger;
        this.executor = executor;
    }

    @Override
    public void execute(Invocation inv) {
        if (!(inv.source() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (!postLogin.pendingCrackAuth.contains(uuid)) {
            player.sendMessage(Component.text("§cYou are not awaiting authentication."));
            return;
        }

        String[] args = inv.arguments();
        if (args.length < 1) {
            player.sendMessage(Component.text("§cUsage: /register <password>"));
            return;
        }
        String password = args[0];

        CompletableFuture.runAsync(() -> {
            try {
                if (db.hasCredentials(player.getUsername())) {
                    player.sendMessage(Component.text("§cAlready registered! Use /login <password>"));
                    return;
                }

                db.setCredentials(player.getUsername(), password, uuid.toString());

                // Auth successful
                postLogin.pendingCrackAuth.remove(uuid);
                player.sendMessage(Component.text("§aRegistered! Connecting..."));

                ServerEntry entry = postLogin.playerTargets.get(uuid);
                if (entry == null) {
                    player.disconnect(Component.text("§cInternal error: no target server."));
                    return;
                }

                player.createConnectionRequest(chooseServer.getOrRegisterBackend(entry))
                        .connect()
                        .thenAccept(result -> {
                            ConnectionRequestBuilder.Status status = result.getStatus();
                            if (status == ConnectionRequestBuilder.Status.SUCCESS
                                    || status == ConnectionRequestBuilder.Status.ALREADY_CONNECTED) {
                                logger.info("{} registered → {}:{}", player.getUsername(), entry.getTransferHost(), entry.getTransferPort());
                                return;
                            }

                            logger.warn("Register connect failed for {}: {}", player.getUsername(), status);
                            player.sendMessage(Component.text("§cFailed to connect you to the backend."));
                        });

            } catch (Exception e) {
                logger.error("Register error for {}: {}", player.getUsername(), e.getMessage());
                player.sendMessage(Component.text("§cInternal error during registration."));
            }
        }, executor);
    }
}
