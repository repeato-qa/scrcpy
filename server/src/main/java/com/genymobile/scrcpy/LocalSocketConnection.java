package com.genymobile.scrcpy;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class LocalSocketConnection extends DesktopConnection {

    private static final String SOCKET_NAME = "scrcpy";

    private final LocalSocket socket;
    private final InputStream inputStream;
    private final FileDescriptor fd;

    private final ControlEventReader reader = new ControlEventReader();

    public LocalSocketConnection(Options options) throws IOException {
        super(options);
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
        send(getDeviceInfo());
        screenEncoder = new ScreenEncoder(options);
        screenEncoder.setDevice(device);
        screenEncoder.setConnection(this);
        screenEncoder.run();
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
