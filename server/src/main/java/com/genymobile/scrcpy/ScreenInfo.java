package com.genymobile.scrcpy;

import android.graphics.Rect;

import java.nio.ByteBuffer;

public final class ScreenInfo {
    private final Rect contentRect; // device size, possibly cropped
    private final Size videoSize;
    private final boolean rotated;

    public ScreenInfo(Rect contentRect, Size videoSize, boolean rotated) {
        this.contentRect = contentRect;
        this.videoSize = videoSize;
        this.rotated = rotated;
    }

    public Rect getContentRect() {
        return contentRect;
    }

    public Size getVideoSize() {
        return videoSize;
    }

    public ScreenInfo withRotation(int rotation) {
        boolean newRotated = (rotation & 1) != 0;
        if (rotated == newRotated) {
            return this;
        }
        return new ScreenInfo(Device.flipRect(contentRect), videoSize.rotate(), newRotated);
    }

    public byte[] toByteArray() {
        ByteBuffer temp = ByteBuffer.allocate(4 * 2 + 2 * 2 + 1);
        temp.putShort((short) contentRect.left);
        temp.putShort((short) contentRect.top);
        temp.putShort((short) contentRect.right);
        temp.putShort((short) contentRect.bottom);
        temp.putShort((short) videoSize.getWidth());
        temp.putShort((short) videoSize.getHeight());
        temp.put((byte) (rotated ? 1 : 0));
        return temp.array();
    }
}
