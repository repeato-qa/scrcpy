package com.genymobile.scrcpy;

import android.graphics.Rect;

import java.nio.ByteBuffer;
import java.util.List;

public class VideoSettings {
    private static final int DEFAULT_BIT_RATE = 8000000;
    private static final byte DEFAULT_MAX_FPS = 60;
    private static final byte DEFAULT_I_FRAME_INTERVAL = 10; // seconds

    private int maxSize;
    private int bitRate = DEFAULT_BIT_RATE;
    private int maxFps;
    private int lockedVideoOrientation;
    private byte iFrameInterval = DEFAULT_I_FRAME_INTERVAL;
    private Rect crop;
    private boolean sendFrameMeta; // send PTS so that the client may record properly
    private List<CodecOption> codecOptions;

    public int getBitRate() {
        return bitRate;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
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

    public List<CodecOption> getCodecOptions() {
        return codecOptions;
    }

    public void setCodecOptions(List<CodecOption> codecOptions) {
        this.codecOptions = codecOptions;
    }

    public byte[] toByteArray() {
        ByteBuffer temp = ByteBuffer.allocate(23);
        temp.putInt(bitRate);
        temp.putInt(maxFps);
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

    @Override
    public String toString() {
        return "VideoSettings{"
                + "bitRate=" + bitRate + ", "
                + "maxFps=" + maxFps + ", "
                + "iFrameInterval=" + iFrameInterval + ", "
                + "maxSize=" + maxSize + ", "
                + "crop=" + crop + ", "
                + "metaFrame=" + sendFrameMeta + ", "
                + "lockedVideoOrientation=" + lockedVideoOrientation
                + "}";
    }

}
