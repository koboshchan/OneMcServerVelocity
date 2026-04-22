package com.kobosh.oneMcServerVelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;

import java.util.UUID;

/** Cleans up per-player state when a player disconnects. */
public class DisconnectListener {

    private final PreLoginListener preLogin;
    private final PostLoginListener postLogin;

    public DisconnectListener(PreLoginListener preLogin, PostLoginListener postLogin) {
        this.preLogin = preLogin;
        this.postLogin = postLogin;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        String username = event.getPlayer().getUsername();
        UUID uuid = event.getPlayer().getUniqueId();
        preLogin.pendingLoginsByUsername.remove(username);
        preLogin.pendingCrackAuthUsernames.remove(username);
        postLogin.cleanup(uuid);
    }
}
