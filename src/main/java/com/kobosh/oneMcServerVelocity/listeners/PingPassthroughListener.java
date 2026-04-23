package com.kobosh.oneMcServerVelocity.listeners;

import com.kobosh.oneMcServerVelocity.config.PluginConfig;
import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;

public class PingPassthroughListener {

    private final PluginConfig config;
    private final PlayerChooseServerListener chooseServer;
    private final Logger logger;

    public PingPassthroughListener(PluginConfig config, PlayerChooseServerListener chooseServer, Logger logger) {
        this.config = config;
        this.chooseServer = chooseServer;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onProxyPing(ProxyPingEvent event) {
        return EventTask.async(() -> {
            String host = event.getConnection()
                    .getVirtualHost()
                    .map(InetSocketAddress::getHostString)
                    .orElse("")
                    .toLowerCase();

            ServerEntry entry = config.getServer(host);
            if (entry == null) {
                String msg = config.translation("domain.unknown.motd", host);
                event.setPing(event.getPing().asBuilder()
                        .description(Component.text(msg))
                        .build());
                return;
            }

            try {
                event.setPing(chooseServer.getOrRegisterBackend(entry).ping().join());
            } catch (Exception e) {
                String msg = config.translation("server.offline.motd");
                event.setPing(event.getPing().asBuilder()
                        .description(Component.text(msg))
                        .build());
                logger.debug("Failed to ping backend {}:{} for host {}",
                        entry.getTransferHost(), entry.getTransferPort(), host, e);
            }
        });
    }
}
