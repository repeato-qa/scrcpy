package com.genymobile.scrcpy;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class WSServer extends WebSocketServer {

    public interface EventsHandler {
        void onOpen(WebSocket conn, ClientHandshake handshake);

        void onClose(WebSocket conn, int code, String reason, boolean remote);

        void onMessage(WebSocket conn, String message);

        void onMessage(WebSocket conn, ByteBuffer message);

        void onError(WebSocket conn, Exception ex);

        void onStart();
    }

    private EventsHandler eventsHandler;

    public WSServer(EventsHandler handler, int portNumber) {
        super(new InetSocketAddress(portNumber));
        eventsHandler = handler;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (eventsHandler != null) {
            eventsHandler.onOpen(conn, handshake);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        if (eventsHandler != null) {
            eventsHandler.onClose(conn, code, reason, remote);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (eventsHandler != null) {
            eventsHandler.onMessage(conn, message);
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        if (eventsHandler != null) {
            eventsHandler.onMessage(conn, message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (eventsHandler != null) {
            eventsHandler.onError(conn, ex);
        }
    }

    @Override
    public void onStart() {
        if (eventsHandler != null) {
            eventsHandler.onStart();
        }
    }
}
