package com.kobosh.oneMcServerVelocity.listeners;

import com.kobosh.oneMcServerVelocity.config.PluginConfig;
import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * After a player reaches the shared limbo/lobby server, premium players are immediately
 * connected to the backend through Velocity. Cracked players stay until /login or /register.
 */
public class LimboTransferListener {

    private static final String LIMBO_SERVER_NAME = "onemcserver_limbo";

    private final PluginConfig config;
    private final PlayerChooseServerListener chooseServer;
    private final PostLoginListener postLogin;
    private final Logger logger;

    public LimboTransferListener(PluginConfig config, PlayerChooseServerListener chooseServer,
                                 PostLoginListener postLogin, Logger logger) {
        this.config = config;
        this.chooseServer = chooseServer;
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

        player.createConnectionRequest(chooseServer.getOrRegisterBackend(entry))
                .connect()
                .thenAccept(result -> {
                    ConnectionRequestBuilder.Status status = result.getStatus();
                    if (status == ConnectionRequestBuilder.Status.SUCCESS
                            || status == ConnectionRequestBuilder.Status.ALREADY_CONNECTED) {
                        logger.info("{} proxied from limbo → {}:{}", player.getUsername(), entry.getTransferHost(), entry.getTransferPort());
                        return;
                    }

                    logger.warn("Failed to connect {} from limbo: {}", player.getUsername(), status);
                    player.disconnect(Component.text("§cFailed to connect you to the backend."));
                });
    }
}
