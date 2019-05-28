package com.genymobile.scrcpy;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class LocalSocketConnection extends DesktopConnection {

    private static final String SOCKET_NAME = "scrcpy";

    private final LocalSocket socket;
    private final InputStream inputStream;
    private final FileDescriptor fd;

    private final ControlEventReader reader = new ControlEventReader();

    public LocalSocketConnection(Options options, VideoSettings videoSettings) throws IOException {
        super(videoSettings);
        boolean tunnelForward = options.isTunnelForward();
        if (tunnelForward) {
            socket = listenAndAccept(SOCKET_NAME);
            // send one byte so the client may read() to detect a connection error
            socket.getOutputStream().write(0);
        } else {
            socket = connect(SOCKET_NAME);
        }
        inputStream = socket.getInputStream();
        fd = socket.getFileDescriptor();
        startEventController();
        send(getInitialInfo());
        screenEncoder = new ScreenEncoder(videoSettings);
        screenEncoder.setDevice(device);
        screenEncoder.setConnection(this);
        screenEncoder.run();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Override
    protected ByteBuffer getInitialInfo() {
        // device name and video size
        String deviceName = Device.getDeviceName();
        Size videoSize = device.getScreenInfo().getVideoSize();
        int width = videoSize.getWidth();
        int height = videoSize.getHeight();
        byte[] buffer = new byte[DEVICE_NAME_FIELD_LENGTH + 4];

        byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(DEVICE_NAME_FIELD_LENGTH - 1, deviceNameBytes.length);
        System.arraycopy(deviceNameBytes, 0, buffer, 0, len);
        // byte[] are always 0-initialized in java, no need to set '\0' explicitly

        buffer[DEVICE_NAME_FIELD_LENGTH] = (byte) (width >> 8);
        buffer[DEVICE_NAME_FIELD_LENGTH + 1] = (byte) width;
        buffer[DEVICE_NAME_FIELD_LENGTH + 2] = (byte) (height >> 8);
        buffer[DEVICE_NAME_FIELD_LENGTH + 3] = (byte) height;
        return ByteBuffer.wrap(buffer);
    }

    private static LocalSocket connect(String abstractName) throws IOException {
        LocalSocket localSocket = new LocalSocket();
        localSocket.connect(new LocalSocketAddress(abstractName));
        return localSocket;
    }

    private static LocalSocket listenAndAccept(String abstractName) throws IOException {
        LocalServerSocket localServerSocket = new LocalServerSocket(abstractName);
        try {
            return localServerSocket.accept();
        } finally {
            localServerSocket.close();
        }
    }

    public void close() throws IOException {
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
    }

    public void send(ByteBuffer data) {
        try {
            IO.writeFully(fd, data);
        } catch (IOException e) {
            Ln.e("Failed to send data", e);
        }
    }

    @Override
    boolean hasConnections() {
        return true;
    }

    private void startEventController() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        ControlEvent controlEvent = receiveControlEvent();
                        if (controlEvent != null) {
                            eventController.handleEvent(controlEvent);
                        }
                    }

                } catch (IOException e) {
                    // this is expected on close
                    Ln.d("Event controller stopped");
                }
            }
        }).start();
    }

    public ControlEvent receiveControlEvent() throws IOException {
        ControlEvent event = reader.next();
        while (event == null) {
            reader.readFrom(inputStream);
            event = reader.next();
        }
        return event;
    }
}
