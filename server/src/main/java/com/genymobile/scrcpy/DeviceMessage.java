package com.genymobile.scrcpy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public abstract class DeviceMessage {
    public static final int MAX_EVENT_SIZE = 4096;
    public static final int TYPE_CLIPBOARD = 0;

    protected int type;

    private DeviceMessage() {
    }

    private final static class ClipboardMessage extends DeviceMessage {
        public static final int CLIPBOARD_TEXT_MAX_LENGTH = MAX_EVENT_SIZE - 3;
        private byte[] raw;
        private int size;
        private ClipboardMessage(String text) {
            this.type = TYPE_CLIPBOARD;
            this.raw = text.getBytes(StandardCharsets.UTF_8);
            this.size = StringUtils.getUtf8TruncationIndex(raw, CLIPBOARD_TEXT_MAX_LENGTH);
        }
        public void writeToByteArray(byte[] array, int offset) {
            ByteBuffer buffer = ByteBuffer.wrap(array, offset, array.length - offset);
            buffer.put((byte) type);
            buffer.putShort((short) size);
            buffer.put(raw, 0, size);
        }
        public int getSize() {
            return 3 + size;
        }
    }

    public static DeviceMessage createClipboard(String text) {
        return new ClipboardMessage(text);
    }

    public int getType() {
        return type;
    }
    public void writeToByteArray(byte[] array) {
        writeToByteArray(array, 0);
    }
    public byte[] writeToByteArray(int offset) {
        byte[] temp = new byte[offset + this.getSize()];
        writeToByteArray(temp, offset);
        return temp;
    }
    public abstract void writeToByteArray(byte[] array, int offset);
    public abstract int getSize();
}
