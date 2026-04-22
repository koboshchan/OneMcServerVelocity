package com.kobosh.oneMcServerVelocity.listeners;

import com.kobosh.oneMcServerVelocity.auth.AuthManager;
import com.kobosh.oneMcServerVelocity.config.PluginConfig;
import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * After a player reaches the shared limbo/lobby server, premium players are immediately
 * handed off with the client-side transfer packet. Cracked players stay until /login or /register.
 */
public class LimboTransferListener {

    private static final String LIMBO_SERVER_NAME = "onemcserver_limbo";

    private final PluginConfig config;
    private final AuthManager authManager;
    private final PostLoginListener postLogin;
    private final Logger logger;

    public LimboTransferListener(PluginConfig config, AuthManager authManager,
                                 PostLoginListener postLogin, Logger logger) {
        this.config = config;
        this.authManager = authManager;
        this.postLogin = postLogin;
        this.logger = logger;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (!config.hasLimbo()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String connectedName = event.getServer().getServerInfo().getName();
        if (!LIMBO_SERVER_NAME.equals(connectedName)) return;

        if (postLogin.pendingCrackAuth.contains(uuid)) {
            player.sendMessage(Component.text("§6[onemcserver] §eAuthenticate to continue."));
            player.sendMessage(Component.text("§eIf you already have an account: §b/login <password>"));
            player.sendMessage(Component.text("§eIf you are new: §b/register <password>"));
            player.sendMessage(Component.text("§eTo change your password: §b/changepass <old> <new>"));
            return;
        }

        ServerEntry entry = postLogin.playerTargets.get(uuid);
        if (entry == null) return;

        try {
            authManager.storeAuthCookie(player);
            player.transferToHost(new InetSocketAddress(entry.getTransferHost(), entry.getTransferPort()));
            logger.info("{} transferred via packet → {}:{}", player.getUsername(), entry.getTransferHost(), entry.getTransferPort());
        } catch (Exception e) {
            logger.error("Failed to transfer {}: {}", player.getUsername(), e.getMessage());
            player.disconnect(Component.text("§cFailed to transfer you to the backend."));
        }
    }
}
