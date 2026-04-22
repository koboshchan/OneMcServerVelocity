package com.kobosh.oneMcServerVelocity.listeners;

import com.kobosh.oneMcServerVelocity.auth.AuthManager;
import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Injects the signed auth cookie into the player's client immediately before
 * they connect to any backend that is NOT the limbo server.
 *
 * The client will present this cookie to the backend on connection,
 * which can then verify authenticity using the Ed25519 public key.
 */
public class ServerPreConnectListener {


    private final AuthManager authManager;
    private final PostLoginListener postLogin;
    private final Logger logger;

    public ServerPreConnectListener(AuthManager authManager, PostLoginListener postLogin, Logger logger) {
        this.authManager = authManager;
        this.postLogin = postLogin;
        this.logger = logger;
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (!event.getResult().isAllowed()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Only inject cookie if player is not awaiting cracked auth
        if (postLogin.pendingCrackAuth.contains(uuid)) return;

        ServerEntry entry = postLogin.playerTargets.get(uuid);
        if (entry == null) return;

        // Skip if connecting to limbo
        String targetName = event.getResult().getServer().map(s -> s.getServerInfo().getName()).orElse("");
        if (targetName.equals("onemcserver_limbo")) return;

        try {
            authManager.storeAuthCookie(player);
        } catch (Exception e) {
            logger.error("Failed to build auth cookie for {}: {}", player.getUsername(), e.getMessage());
        }
    }
}
