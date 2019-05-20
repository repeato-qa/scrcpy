package com.genymobile.scrcpy;

import android.graphics.Rect;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public abstract class DesktopConnection implements Device.RotationListener {
    protected static final int DEVICE_NAME_FIELD_LENGTH = 64;
    protected StreamInvalidateListener streamInvalidateListener;
    protected Device device;
    protected Options options;
    protected EventController eventController;
    protected ScreenEncoder screenEncoder;

    abstract void send(ByteBuffer data);

    abstract void close() throws Exception;

    abstract boolean hasConnections();

    public DesktopConnection(Options options) {
        this.options = options;
        device = new Device(options);
        device.setRotationListener(this);
        eventController = new EventController(device, this);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    protected ByteBuffer getDeviceInfo() {
        String deviceName = Device.getDeviceName();
        String magic = "scrcpy";
        Size videoSize = device.getScreenInfo().getVideoSize();
        int width = videoSize.getWidth();
        int height = videoSize.getHeight();
        int bitRate = options.getBitRate();
        int frameRate = options.getFrameRate();
        byte[] buffer = new byte[DEVICE_NAME_FIELD_LENGTH + 9 + magic.length()];
        ByteBuffer temp = ByteBuffer.wrap(buffer, DEVICE_NAME_FIELD_LENGTH, 9);
        temp.putShort((short) width);
        temp.putShort((short) height);
        temp.putInt(bitRate);
        temp.put((byte) frameRate);

        byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(DEVICE_NAME_FIELD_LENGTH - 1, deviceNameBytes.length);
        System.arraycopy(deviceNameBytes, 0, buffer, 0, len);
        // byte[] are always 0-initialized in java, no need to set '\0' explicitly
        byte[] magicBytes = magic.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(magicBytes, 0, buffer, DEVICE_NAME_FIELD_LENGTH + 9, magic.length());

        return ByteBuffer.wrap(buffer);
    }

    public void setStreamParameters(byte[] bytes) {
        ByteBuffer data = ByteBuffer.wrap(bytes);
        int w = data.getShort();
        int h = data.getShort();
        int bitRate = data.getInt();
        int frameRate = data.get();
        Rect contentRect = device.getScreenInfo().getContentRect();
        int deviceW = contentRect.width();
        int deviceH = contentRect.height();
        w = Math.min(w, deviceW);
        int rH = w * deviceH / deviceW;
        h = Math.min(h, deviceH);
        int rW = h * deviceW / deviceH;
        options.setMaxSize(Math.max(Math.min(rW, w), Math.min(rH, h)));
        options.setBitRate(bitRate);
        options.setFrameRate(frameRate);
        device.setScreenInfo(device.computeScreenInfo(options));
        if (this.streamInvalidateListener != null) {
            streamInvalidateListener.onStreamInvalidate();
        }
    }

    public void setStreamInvalidateListener(StreamInvalidateListener listener) {
        this.streamInvalidateListener = listener;
    }

    public interface StreamInvalidateListener {
        void onStreamInvalidate();
    }

    public void onRotationChanged(int rotation) {
        if (this.streamInvalidateListener != null) {
            streamInvalidateListener.onStreamInvalidate();
        }
    }
}
