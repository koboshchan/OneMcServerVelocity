package com.kobosh.oneMcServerVelocity.listeners;

import com.kobosh.oneMcServerVelocity.auth.AuthManager;
import com.kobosh.oneMcServerVelocity.config.PluginConfig;
import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Reads the virtual host from the handshake, determines premium/cracked status,
 * and either allows/denies the connection.
 *
 * State shared with later listeners:
 *  - pendingLoginsByUsername : username → ServerEntry (set here, consumed in PostLoginListener)
 *  - pendingCrackAuthUsernames : usernames of cracked players needing /register or /login
 */
public class PreLoginListener {

    private final PluginConfig config;
    private final AuthManager authManager;
    private final Logger logger;
    private final Executor executor;

    /** username → target ServerEntry; populated during PreLoginEvent, consumed after PostLogin */
    public final Map<String, ServerEntry> pendingLoginsByUsername = new ConcurrentHashMap<>();
    /** usernames that still need cracked authentication */
    public final Set<String> pendingCrackAuthUsernames = ConcurrentHashMap.newKeySet();

    public PreLoginListener(PluginConfig config, AuthManager authManager, Logger logger, Executor executor) {
        this.config = config;
        this.authManager = authManager;
        this.logger = logger;
        this.executor = executor;
    }

    @Subscribe
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            String username = event.getUsername();

            // Resolve virtual host
            Optional<InetSocketAddress> vHostOpt = event.getConnection().getVirtualHost();
            String host = vHostOpt.map(InetSocketAddress::getHostString).orElse("").toLowerCase();

            ServerEntry entry = config.getServer(host);
            if (entry == null) {
                String msg = config.translation("domain.unknown.disconnect", host);
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(msg)));
                logger.info("Denied {} – unknown domain '{}'", username, host);
                return;
            }

            // Mojang premium check (uses DB cache)
            Optional<AuthManager.PremiumProfile> premium =
                    authManager.checkPremium(username, executor).join();

            if (premium.isPresent()) {
                // Online-mode: Velocity will verify with Mojang
                event.setResult(PreLoginEvent.PreLoginComponentResult.allowed());
                logger.info("{} is PREMIUM → online mode", username);
            } else {
                if (!entry.isCrackedPlayers()) {
                    String msg = config.translation("online.mode.disconnect");
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(msg)));
                    logger.info("Denied {} – cracked players disabled for {}", username, host);
                    return;
                }
                // Force offline mode for cracked player
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                pendingCrackAuthUsernames.add(username);
                logger.info("{} is CRACKED → offline mode", username);
            }

            pendingLoginsByUsername.put(username, entry);
        });
    }
}
