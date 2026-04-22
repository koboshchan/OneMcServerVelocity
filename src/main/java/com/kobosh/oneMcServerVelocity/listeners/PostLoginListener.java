package com.kobosh.oneMcServerVelocity.listeners;

import com.kobosh.oneMcServerVelocity.config.ServerEntry;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs after Velocity has fully authenticated the player.
 * Moves the per-username tracking from PreLoginListener into UUID-keyed maps
 * that subsequent listeners use.
 */
public class PostLoginListener {

    private final PreLoginListener preLogin;
    private final Logger logger;

    /** UUID → target ServerEntry; used by PlayerChooseServerListener */
    public final Map<UUID, ServerEntry> playerTargets = new ConcurrentHashMap<>();
    /** UUIDs of cracked players who still need /register or /login */
    public final Set<UUID> pendingCrackAuth = ConcurrentHashMap.newKeySet();

    public PostLoginListener(PreLoginListener preLogin, Logger logger) {
        this.preLogin = preLogin;
        this.logger = logger;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        String username = event.getPlayer().getUsername();
        UUID uuid = event.getPlayer().getUniqueId();

        ServerEntry entry = preLogin.pendingLoginsByUsername.remove(username);
        if (entry == null) return; // not tracked (shouldn't happen)

        playerTargets.put(uuid, entry);

        if (preLogin.pendingCrackAuthUsernames.remove(username)) {
            pendingCrackAuth.add(uuid);
            logger.info("{} ({}) queued for cracked auth", username, uuid);
        }
    }

    public void cleanup(UUID uuid) {
        playerTargets.remove(uuid);
        pendingCrackAuth.remove(uuid);
    }
}
