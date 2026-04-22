package com.kobosh.oneMcServerVelocity.commands;

import com.kobosh.oneMcServerVelocity.database.DatabaseManager;
import com.kobosh.oneMcServerVelocity.listeners.PostLoginListener;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ChangePassCommand implements SimpleCommand {

    private final DatabaseManager db;
    private final PostLoginListener postLogin;
    private final Logger logger;
    private final Executor executor;

    public ChangePassCommand(DatabaseManager db, PostLoginListener postLogin, Logger logger, Executor executor) {
        this.db = db;
        this.postLogin = postLogin;
        this.logger = logger;
        this.executor = executor;
    }

    @Override
    public void execute(Invocation inv) {
        if (!(inv.source() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (!postLogin.pendingCrackAuth.contains(uuid)) {
            player.sendMessage(Component.text("§cYou can only use this while authenticating in limbo."));
            return;
        }

        String[] args = inv.arguments();
        if (args.length < 2) {
            player.sendMessage(Component.text("§cUsage: /changepass <old> <new>"));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                boolean changed = db.changePassword(player.getUsername(), args[0], args[1]);
                if (!changed) {
                    player.sendMessage(Component.text("§cOld password is incorrect or you are not registered."));
                    return;
                }
                player.sendMessage(Component.text("§aPassword changed successfully. Use /login <new> to continue."));
                logger.info("{} changed password", player.getUsername());
            } catch (Exception e) {
                logger.error("ChangePass error for {}: {}", player.getUsername(), e.getMessage());
                player.sendMessage(Component.text("§cInternal error while changing password."));
            }
        }, executor);
    }
}