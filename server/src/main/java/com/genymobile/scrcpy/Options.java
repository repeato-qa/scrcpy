package com.genymobile.scrcpy;

import android.graphics.Rect;

public class Options {
    public static int TYPE_LOCAL_SOCKET = 1;
    public static int TYPE_WEB_SOCKET = 2;

    private static final int DEFAULT_FRAME_RATE = 60; // fps

    private int maxSize;
    private int bitRate;
    private boolean tunnelForward;
    private int frameRate = DEFAULT_FRAME_RATE;
    private Rect crop;
    private boolean sendFrameMeta; // send PTS so that the client may record properly
    private int serverType = TYPE_LOCAL_SOCKET;

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = (maxSize / 8) * 8;
    }

    public int getBitRate() {
        return bitRate;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int bitRate) {
        this.frameRate = bitRate;
    }

    public boolean isTunnelForward() {
        return tunnelForward;
    }

    public void setTunnelForward(boolean tunnelForward) {
        this.tunnelForward = tunnelForward;
    }

    public Rect getCrop() {
        return crop;
    }

    public void setCrop(Rect crop) {
        this.crop = crop;
    }

    public boolean getSendFrameMeta() {
        return sendFrameMeta;
    }

    public void setSendFrameMeta(boolean sendFrameMeta) {
        this.sendFrameMeta = sendFrameMeta;
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
                + "maxSize=" + maxSize
                + ", bitRate=" + bitRate
                + ", frameRate=" + frameRate
                + ", tunnelForward=" + tunnelForward
                + ", crop=" + crop
                + ", sendFrameMeta=" + sendFrameMeta
                + ", serverType=" + (serverType == TYPE_LOCAL_SOCKET ? "local" : "web")
                + '}';
    }
}
