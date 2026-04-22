package com.kobosh.oneMcServerVelocity.config;
public class ServerEntry {
    private final String host;
    private final String transferHost;
    private final int transferPort;
    private final boolean crackedPlayers;
    public ServerEntry(String host, String transferHost, int transferPort, boolean crackedPlayers) {
        this.host = host;
        this.transferHost = transferHost;
        this.transferPort = transferPort;
        this.crackedPlayers = crackedPlayers;
    }
    public String getHost() { return host; }
    public String getTransferHost() { return transferHost; }
    public int getTransferPort() { return transferPort; }
    public boolean isCrackedPlayers() { return crackedPlayers; }
    public String getServerName() {
        return "onemcserver_" + transferHost.replace('.', '_') + "_" + transferPort;
    }
}
