package com.genymobile.scrcpy;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class WSServer extends WebSocketServer {

    public interface EventsHandler {
        void onOpen(WebSocket conn, ClientHandshake handshake);

        void onClose(WebSocket conn, int code, String reason, boolean remote);

        void onMessage(WebSocket conn, String message);

        void onMessage(WebSocket conn, ByteBuffer message);

        void onError(WebSocket conn, Exception ex);

        void onStart();
    }

    final static private int DEFAULT_PORT_NUMBER = 8886; // 843 flash policy port

    private EventsHandler eventsHandler;

    public WSServer(EventsHandler handler) {
        super(new InetSocketAddress(DEFAULT_PORT_NUMBER));
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