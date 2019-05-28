package com.genymobile.scrcpy;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class WebSocketConnection extends DesktopConnection implements WSServer.EventsHandler {
    private WSServer wsServer;
    private int receivingClientsCount = 0;
    private final ControlEventReader reader = new ControlEventReader();
    private final byte[] MAGIC_BYTES = "scrcpy".getBytes(StandardCharsets.UTF_8);
    private final byte[] DEVICE_NAME_BYTES = Device.getDeviceName().getBytes(StandardCharsets.UTF_8);

    public WebSocketConnection(VideoSettings videoSettings) {
        super(videoSettings);
        wsServer = new WSServer(this);
        wsServer.setReuseAddr(true);
        wsServer.run();
    }

    @Override
    void send(ByteBuffer data) {
        Collection<WebSocket> connections = wsServer.getConnections();
        if (connections.isEmpty()) {
            return;
        }
        for (WebSocket conn : wsServer.getConnections()) {
            Boolean streaming = conn.<Boolean>getAttachment();
            if (conn.isOpen() && streaming != null && streaming) {
                conn.send(data);
            }
        }
    }

    @Override
    public boolean hasConnections() {
        return receivingClientsCount > 0;
    }

    @Override
    public void close() throws Exception {
        wsServer.stop();
    }

    public void setVideoSettings(byte[] bytes) {
        super.setVideoSettings(bytes);
        send(getInitialInfo());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Override
    protected ByteBuffer getInitialInfo() {
        byte[] screenInfoBytes = device.getScreenInfo().toByteArray();
        byte[] videoSettingsBytes = videoSettings.toByteArray();

        byte[] fullInfo = new byte[MAGIC_BYTES.length + DEVICE_NAME_FIELD_LENGTH +
                screenInfoBytes.length + videoSettingsBytes.length];
        System.arraycopy(MAGIC_BYTES, 0, fullInfo, 0, MAGIC_BYTES.length);
        int len = Math.min(DEVICE_NAME_FIELD_LENGTH - 1, DEVICE_NAME_BYTES.length);
        System.arraycopy(DEVICE_NAME_BYTES, 0, fullInfo, MAGIC_BYTES.length, len);
        ByteBuffer temp = ByteBuffer.wrap(fullInfo, MAGIC_BYTES.length + DEVICE_NAME_FIELD_LENGTH,
                screenInfoBytes.length + videoSettingsBytes.length);
        temp.put(screenInfoBytes);
        temp.put(videoSettingsBytes);
        return ByteBuffer.wrap(fullInfo);
    }

    private void checkConnectionsCount() {
        if (receivingClientsCount == 0) {
            Ln.d("No receiving clients.");
            device.setRotationListener(null);
        } else {
            if (screenEncoder == null || !screenEncoder.isAlive()) {
                Ln.d("New connection while encoder is dead.");
                device.setRotationListener(this);
                screenEncoder = new ScreenEncoder(videoSettings);
                screenEncoder.start(device, this);
            }
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (conn.isOpen()) {
            conn.send(getInitialInfo());
            Ln.d("Client entered the room!");
            if (this.streamInvalidateListener != null) {
                streamInvalidateListener.onStreamInvalidate();
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Ln.d("Client has left the room!");
        receivingClientsCount--;
        checkConnectionsCount();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String address = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        Ln.d("+  " + address + ": " + message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        String address = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        ControlEvent controlEvent = reader.parseEvent(message);
        if (controlEvent != null) {
            eventController.handleEvent(controlEvent);
            if (controlEvent.getType() == ControlEvent.TYPE_COMMAND &&
                    controlEvent.getAction() == ControlEvent.COMMAND_SET_VIDEO_SETTINGS) {
                Boolean streaming = conn.<Boolean>getAttachment();
                if (streaming == null || !streaming) {
                    conn.send(getInitialInfo());
                    conn.setAttachment(true);
                    receivingClientsCount++;
                    checkConnectionsCount();
                }
            }
        } else {
            Ln.d("-1 " + address + ": " + message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Ln.e("WebSocket error", ex);
        if (conn != null) {
            // some errors like port binding failed may not be assignable to a specific websocket
        }
    }

    @Override
    public void onStart() {
        Ln.d("Server started!");
        wsServer.setConnectionLostTimeout(0);
        wsServer.setConnectionLostTimeout(100);
    }

    public void onRotationChanged(int rotation) {
        super.onRotationChanged(rotation);
        send(getInitialInfo());
    }
}
