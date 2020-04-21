package com.genymobile.scrcpy;

import android.graphics.Rect;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class Connection implements Device.RotationListener {
    protected final ControlMessageReader reader = new ControlMessageReader();
    protected static final int DEVICE_NAME_FIELD_LENGTH = 64;
    protected StreamInvalidateListener streamInvalidateListener;
    protected Device device;
    protected VideoSettings videoSettings;
    protected Options options;
    protected Controller controller;
    protected ScreenEncoder screenEncoder;

    abstract void send(ByteBuffer data) throws IOException;

    abstract void sendDeviceMessage(DeviceMessage msg) throws IOException;

    abstract void close() throws Exception;

    abstract boolean hasConnections();

    public Connection(Options options, VideoSettings videoSettings) {
        this.videoSettings = videoSettings;
        this.options = options;
        device = new Device(options, videoSettings);
        device.setRotationListener(this);
        controller = new Controller(device, this);
        startDeviceMessageSender(controller.getSender());
    }

    public void setVideoSettings(byte[] bytes) {
        ByteBuffer data = ByteBuffer.wrap(bytes);
        int bitRate = data.getInt();
        byte frameRate = data.get();
        byte iFrameInterval = data.get();
        int maxSize = data.getInt();
        int left = data.getShort();
        int top = data.getShort();
        int right = data.getShort();
        int bottom = data.getShort();
        boolean sendMetaFrame = data.get() != 0;
        int lockedVideoOrientation = data.get();
        videoSettings.setBitRate(bitRate);
        videoSettings.setFrameRate(frameRate);
        videoSettings.setIFrameInterval(iFrameInterval);
        videoSettings.setMaxSize(maxSize);
        if (left == 0 && right == 0 && top == 0 && bottom == 0) {
            videoSettings.setCrop(null);
        } else {
            videoSettings.setCrop(new Rect(left, top, right, bottom));
        }
        videoSettings.setSendFrameMeta(sendMetaFrame);
        videoSettings.setLockedVideoOrientation(lockedVideoOrientation);
        device.applyNewVideoSetting(videoSettings);
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


    private static void startDeviceMessageSender(final DeviceMessageSender sender) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sender.loop();
                } catch (IOException | InterruptedException e) {
                    // this is expected on close
                    Ln.d("Device message sender stopped");
                }
            }
        }).start();
    }
}
