package com.genymobile.scrcpy;

import android.media.MediaCodecInfo;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;

import java.io.IOException;
import java.net.BindException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class WebSocketConnection extends Connection implements WSServer.EventsHandler {
    private WSServer wsServer;
    private short receivingClientsCount = 0;
    private static final byte[] MAGIC_BYTES_INITIAL = "scrcpy_initial".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MAGIC_BYTES_MESSAGE = "scrcpy_message".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DEVICE_NAME_BYTES = Device.getDeviceName().getBytes(StandardCharsets.UTF_8);

    public WebSocketConnection(Options options, VideoSettings videoSettings) throws IOException {
        super(options, videoSettings);
        wsServer = new WSServer(this, options.getPortNumber());
        wsServer.setReuseAddr(true);
        wsServer.run();
    }

    private static final class ConnectionInfo {
        private static final HashSet<Short> INSTANCES_BY_ID = new HashSet<>();
        private final short id;
        private boolean isReceiving;

        ConnectionInfo(short id, boolean isReceiving) {
            this.id = id;
            this.isReceiving = isReceiving;
            INSTANCES_BY_ID.add(id);
        }
        public static short getNextClientId() {
            short nextClientId = 0;
            while (INSTANCES_BY_ID.contains(++nextClientId)) {
                if (nextClientId == Short.MAX_VALUE) {
                    return -1;
                }
            }
            return nextClientId;
        }

        public short getId() {
            return id;
        }

        public boolean getIsReceiving() {
            return isReceiving;
        }

        public void setIsReceiving(boolean isReceiving) {
            this.isReceiving = isReceiving;
        }
        public void release() {
            INSTANCES_BY_ID.remove(id);
        }
    }

    public static ByteBuffer deviceMessageToByteBuffer(DeviceMessage msg) {
        ByteBuffer buffer = ByteBuffer.wrap(msg.writeToByteArray(MAGIC_BYTES_MESSAGE.length));
        buffer.put(MAGIC_BYTES_MESSAGE);
        buffer.position(0);
        return buffer;
    }

    @Override
    void send(ByteBuffer data) {
        Collection<WebSocket> connections = wsServer.getConnections();
        if (connections.isEmpty()) {
            return;
        }
        for (WebSocket conn : wsServer.getConnections()) {
            ConnectionInfo info = conn.getAttachment();
            if (!conn.isOpen() || info == null) {
                continue;
            }
            if (info.getIsReceiving()) {
                conn.send(data);
            }
        }
    }

    private void sendInitialInfoToAll() {
        Collection<WebSocket> connections = wsServer.getConnections();
        if (connections.isEmpty()) {
            return;
        }
        for (WebSocket conn : wsServer.getConnections()) {
            ConnectionInfo info = conn.getAttachment();
            if (!conn.isOpen() && info == null) {
                continue;
            }
            short clientId = info.getId();
            conn.send(getInitialInfo(clientId, receivingClientsCount));
        }

    }

    public void sendDeviceMessage(DeviceMessage msg) {
        ByteBuffer buffer = deviceMessageToByteBuffer(msg);
        send(buffer);
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
        sendInitialInfoToAll();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    protected ByteBuffer getInitialInfo(short clientId, short clientsCount) {
        byte[] screenInfoBytes = device.getScreenInfo().toByteArray();
        byte[] videoSettingsBytes = videoSettings.toByteArray();
        int magicBytesLength = MAGIC_BYTES_INITIAL.length;

        int baseLength = magicBytesLength
                + DEVICE_NAME_FIELD_LENGTH
                + screenInfoBytes.length
                + videoSettingsBytes.length
                + 2   // short clientId
                + 2;  // short clientsCount
        int additionalLength = 0;
        MediaCodecInfo[] encoders = ScreenEncoder.listEncoders();
        List<byte[]> encodersNames = new ArrayList<>();
        if (encoders != null && encoders.length > 0) {
            additionalLength += 4;
            for (MediaCodecInfo encoder : encoders) {
                byte[] nameBytes = encoder.getName().getBytes(StandardCharsets.UTF_8);
                additionalLength += 4 + nameBytes.length;
                encodersNames.add(nameBytes);
            }
        }
        byte[] fullInfo = new byte[baseLength + additionalLength];
        System.arraycopy(MAGIC_BYTES_INITIAL, 0, fullInfo, 0, magicBytesLength);
        int len = Math.min(DEVICE_NAME_FIELD_LENGTH - 1, DEVICE_NAME_BYTES.length);
        System.arraycopy(DEVICE_NAME_BYTES, 0, fullInfo, magicBytesLength, len);
        ByteBuffer full = ByteBuffer.wrap(fullInfo);
        full.position(magicBytesLength + DEVICE_NAME_FIELD_LENGTH);
        full.put(screenInfoBytes);
        full.put(videoSettingsBytes);
        full.putShort(clientId);
        full.putShort(clientsCount);
        full.putInt(encodersNames.size());
        for (byte[] encoderNameBytes: encodersNames) {
            full.putInt(encoderNameBytes.length);
            full.put(encoderNameBytes);
        }
        full.rewind();
        return full;
    }

    private void checkConnectionsCount() {
        if (receivingClientsCount == 0) {
            Ln.d("No receiving clients.");
            device.setRotationListener(null);
        } else {
            if (receivingClientsCount == 1 && !device.isScreenOn()) {
                controller.turnScreenOn();
            }
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
            short clientId = ConnectionInfo.getNextClientId();
            if (clientId == -1) {
                conn.close(CloseFrame.TRY_AGAIN_LATER);
                return;
            }
            ConnectionInfo info = new ConnectionInfo(clientId, false);
            conn.setAttachment(info);
            conn.send(getInitialInfo(clientId, receivingClientsCount));
            Ln.d("Client entered the room!");
            if (this.streamInvalidateListener != null) {
                streamInvalidateListener.onStreamInvalidate();
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Ln.d("Client has left the room!");
        FilePushHandler.cancelAllForConnection(conn);
        receivingClientsCount--;
        ConnectionInfo info = conn.getAttachment();
        if (info != null) {
            info.release();
        }
        checkConnectionsCount();
        sendInitialInfoToAll();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String address = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        Ln.d("+  " + address + ": " + message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        String address = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        ControlMessage controlMessage = reader.parseEvent(message);
        if (controlMessage != null) {
            if (controlMessage.getType() == ControlMessage.TYPE_PUSH_FILE) {
                FilePushHandler.handlePush(conn, controlMessage);
                return;
            }
            if (controlMessage.getType() == ControlMessage.TYPE_CHANGE_STREAM_PARAMETERS) {
                ConnectionInfo info = conn.getAttachment();
                if (info == null) {
                    Ln.e("No info attached to connection");
                    return;
                }
                if (!info.getIsReceiving()) {
                    info.setIsReceiving(true);
                    receivingClientsCount++;
                    checkConnectionsCount();
                }
            }
            controller.handleEvent(controlMessage);

        } else {
            Ln.d("-1 " + address + ": " + message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Ln.e("WebSocket error", ex);
        if (conn != null) {
            // some errors like port binding failed may not be assignable to a specific websocket
            FilePushHandler.cancelAllForConnection(conn);
        }
        if (ex instanceof BindException) {
            System.exit(1);
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
        sendInitialInfoToAll();
    }
}
