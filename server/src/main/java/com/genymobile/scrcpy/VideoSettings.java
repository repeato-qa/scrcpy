package com.genymobile.scrcpy;

import android.graphics.Rect;

import java.nio.ByteBuffer;

public class VideoSettings {
    private static final int DEFAULT_BIT_RATE = 8000000;
    private static final byte DEFAULT_FRAME_RATE = 60;
    private static final byte DEFAULT_I_FRAME_INTERVAL = 10; // seconds

    private int maxSize;
    private int bitRate = DEFAULT_BIT_RATE;
    private int maxFps;
    private int lockedVideoOrientation;
    private byte frameRate = DEFAULT_FRAME_RATE;
    private byte iFrameInterval = DEFAULT_I_FRAME_INTERVAL;
    private Rect crop;
    private boolean sendFrameMeta; // send PTS so that the client may record properly

    public int getBitRate() {
        return bitRate;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(byte frameRate) {
        this.frameRate = frameRate;
    }

    public int getIFrameInterval() {
        return iFrameInterval;
    }

    public void setIFrameInterval(byte iFrameInterval) {
        this.iFrameInterval = iFrameInterval;
    };

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

    public int getMaxFps() {
        return maxFps;
    }

    public void setMaxFps(int maxFps) {
        this.maxFps = maxFps;
    }

    public int getLockedVideoOrientation() {
        return lockedVideoOrientation;
    }

    public void setLockedVideoOrientation(int lockedVideoOrientation) {
        this.lockedVideoOrientation = lockedVideoOrientation;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = (maxSize / 8) * 8;
    }

    public byte[] toByteArray() {
        ByteBuffer temp = ByteBuffer.allocate(20);
        temp.putInt(bitRate);
        temp.put(frameRate);
        temp.put(iFrameInterval);
        temp.putInt(maxSize);
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;
        if (crop != null) {
            left = crop.left;
            top = crop.top;
            right = crop.right;
            bottom = crop.bottom;
        }
        temp.putShort((short) left);
        temp.putShort((short) top);
        temp.putShort((short) right);
        temp.putShort((short) bottom);
        temp.put((byte) (sendFrameMeta ? 1 : 0));
        temp.put((byte) lockedVideoOrientation);
        return temp.array();
    }

    public String toString() {
        return "VideoSettings{"
                + "bitRate=" + bitRate + ", "
                + "frameRate=" + frameRate + ", "
                + "iFrameInterval=" + iFrameInterval + ", "
                + "maxSize=" + maxSize + ", "
                + "crop=" + crop + ", "
                + "metaFrame=" + sendFrameMeta + ", "
                + "lockedVideoOrientation=" + lockedVideoOrientation
                + "}";
    }

}
