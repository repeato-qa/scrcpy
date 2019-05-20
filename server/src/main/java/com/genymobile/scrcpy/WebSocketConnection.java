package com.genymobile.scrcpy;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.nio.ByteBuffer;
import java.util.Collection;

public class WebSocketConnection extends DesktopConnection implements WSServer.EventsHandler {
    private WSServer wsServer;
    private int receivingClientsCount = 0;
    private final ControlEventReader reader = new ControlEventReader();

    public WebSocketConnection(Options options) {
        super(options);
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

    public void setStreamParameters(byte[] bytes) {
        super.setStreamParameters(bytes);
        send(getDeviceInfo());
    }

    private void checkConnectionsCount() {
        if (receivingClientsCount == 0) {
            Ln.d("No receiving clients.");
            device.setRotationListener(null);
        } else {
            if (screenEncoder == null || !screenEncoder.isAlive()) {
                Ln.d("New connection while encoder is dead.");
                device.setRotationListener(this);
                screenEncoder = new ScreenEncoder(options);
                screenEncoder.start(device, this);
            }
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (conn.isOpen()) {
            conn.send(getDeviceInfo());
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
                    controlEvent.getAction() == ControlEvent.COMMAND_CHANGE_STREAM_PARAMETERS) {
                Boolean streaming = conn.<Boolean>getAttachment();
                if (streaming == null || !streaming) {
                    conn.send(getDeviceInfo());
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
        send(getDeviceInfo());
    }
}
