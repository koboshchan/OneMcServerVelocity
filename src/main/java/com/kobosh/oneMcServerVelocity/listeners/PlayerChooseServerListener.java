package com.kobosh.oneMcServerVelocity.listeners;

import com.kobosh.oneMcServerVelocity.config.PluginConfig;
import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

/**
 * Routes the player to either:
 *  - The target backend (premium, or cracked-already-authed)
 *  - The limbo server (cracked, not yet authenticated)
 */
public class PlayerChooseServerListener {

    private static final String LIMBO_SERVER_NAME = "onemcserver_limbo";

    private final PluginConfig config;
    private final PostLoginListener postLogin;
    private final ProxyServer proxy;
    private final Logger logger;

    public PlayerChooseServerListener(PluginConfig config, PostLoginListener postLogin,
                                      ProxyServer proxy, Logger logger) {
        this.config = config;
        this.postLogin = postLogin;
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ServerEntry entry = postLogin.playerTargets.get(uuid);
        if (entry == null) return; // no tracked entry – leave Velocity default

        if (!config.hasLimbo()) {
            if (postLogin.pendingCrackAuth.contains(uuid)) {
                event.getPlayer().disconnect(Component.text(
                        "§cCracked players need a limbo server configured. Contact the admin."));
                logger.warn("{} cracked but no limbo_server configured – disconnecting", event.getPlayer().getUsername());
                return;
            }

            RegisteredServer backend = getOrRegisterBackend(entry);
            event.setInitialServer(backend);
            logger.info("{} → backend {}", event.getPlayer().getUsername(), backend.getServerInfo().getName());
            return;
        }

        RegisteredServer limbo = getOrRegisterLimbo();
        event.setInitialServer(limbo);
        if (postLogin.pendingCrackAuth.contains(uuid)) {
            logger.info("{} → limbo (awaiting auth)", event.getPlayer().getUsername());
        } else {
            logger.info("{} → limbo (awaiting transfer)", event.getPlayer().getUsername());
        }
    }

    private RegisteredServer getOrRegisterLimbo() {
        Optional<RegisteredServer> existing = proxy.getServer(LIMBO_SERVER_NAME);
        if (existing.isPresent()) return existing.get();
        ServerInfo info = new ServerInfo(LIMBO_SERVER_NAME,
                new InetSocketAddress(config.getLimboHost(), config.getLimboPort()));
        return proxy.registerServer(info);
    }

    public RegisteredServer getOrRegisterBackend(ServerEntry entry) {
        String name = entry.getServerName();
        Optional<RegisteredServer> existing = proxy.getServer(name);
        if (existing.isPresent()) return existing.get();
        ServerInfo info = new ServerInfo(name,
                new InetSocketAddress(entry.getTransferHost(), entry.getTransferPort()));
        return proxy.registerServer(info);
    }
}
