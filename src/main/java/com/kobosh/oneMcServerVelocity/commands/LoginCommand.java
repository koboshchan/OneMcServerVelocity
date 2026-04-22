package com.kobosh.oneMcServerVelocity.commands;

import com.kobosh.oneMcServerVelocity.auth.AuthManager;
import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.kobosh.oneMcServerVelocity.database.DatabaseManager;
import com.kobosh.oneMcServerVelocity.listeners.PlayerChooseServerListener;
import com.kobosh.oneMcServerVelocity.listeners.PostLoginListener;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Handles /login <password> for cracked players on the limbo server.
 */
public class LoginCommand implements SimpleCommand {

    private final DatabaseManager db;
    private final AuthManager authManager;
    private final PostLoginListener postLogin;
    private final PlayerChooseServerListener chooseServer;
    private final Logger logger;
    private final Executor executor;

    public LoginCommand(DatabaseManager db, AuthManager authManager,
                        PostLoginListener postLogin, PlayerChooseServerListener chooseServer,
                        Logger logger, Executor executor) {
        this.db = db;
        this.authManager = authManager;
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
            player.sendMessage(Component.text("§cUsage: /login <password>"));
            return;
        }
        String password = args[0];

        CompletableFuture.runAsync(() -> {
            try {
                var creds = db.getCredentials(player.getUsername());
                if (creds.isEmpty()) {
                    player.sendMessage(Component.text("§cNot registered yet. Use /register <password>"));
                    return;
                }
                if (!creds.get().password().equals(password)) {
                    player.sendMessage(Component.text("§cIncorrect password!"));
                    return;
                }

                // Auth successful
                postLogin.pendingCrackAuth.remove(uuid);
                player.sendMessage(Component.text("§aAuthenticated! Transferring..."));

                // Store cookie then connect
                byte[] cookie = authManager.buildAuthCookie(
                        player.getUsername(), uuid.toString(), true);
                player.storeCookie(AuthManager.COOKIE_KEY, cookie);

                ServerEntry entry = postLogin.playerTargets.get(uuid);
                if (entry == null) {
                    player.disconnect(Component.text("§cInternal error: no target server."));
                    return;
                }
                var backend = chooseServer.getOrRegisterBackend(entry);
                player.createConnectionRequest(backend).fireAndForget();
                logger.info("{} authenticated (login) → {}", player.getUsername(), backend.getServerInfo().getName());

            } catch (Exception e) {
                logger.error("Login error for {}: {}", player.getUsername(), e.getMessage());
                player.sendMessage(Component.text("§cInternal error during login."));
            }
        }, executor);
    }
}
