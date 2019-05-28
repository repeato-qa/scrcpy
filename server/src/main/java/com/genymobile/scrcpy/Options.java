package com.genymobile.scrcpy;

public class Options {
    public static int TYPE_LOCAL_SOCKET = 1;
    public static int TYPE_WEB_SOCKET = 2;

    private boolean tunnelForward;
    private int serverType = TYPE_LOCAL_SOCKET;

    public boolean isTunnelForward() {
        return tunnelForward;
    }

    public void setTunnelForward(boolean tunnelForward) {
        this.tunnelForward = tunnelForward;
    }

    public int getServerType() {
        return serverType;
    }

    public void setServerType(int type) {
        if (type == TYPE_LOCAL_SOCKET || type == TYPE_WEB_SOCKET) {
            this.serverType = type;
        }
    }

    @Override
    public String toString() {
        return "Options{"
                + "tunnelForward=" + tunnelForward
                + ", serverType=" + (serverType == TYPE_LOCAL_SOCKET ? "local" : "web")
                + '}';
    }
}
