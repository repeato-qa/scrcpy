package com.genymobile.scrcpy;

import android.graphics.Rect;
import android.util.Log;

import java.nio.ByteBuffer;

public final class ScreenInfo {
    private static String TAG = "scrcpy";
    /**
     * Device (physical) size, possibly cropped
     */
    private final Rect contentRect; // device size, possibly cropped

    /**
     * Video size, possibly smaller than the device size, already taking the device rotation and crop into account.
     * <p>
     * However, it does not include the locked video orientation.
     */
    private final Size unlockedVideoSize;

    /**
     * Device rotation, related to the natural device orientation (0, 1, 2 or 3)
     */
    private final int deviceRotation;

    /**
     * The locked video orientation (-1: disabled, 0: normal, 1: 90° CCW, 2: 180°, 3: 90° CW)
     */
    private final int lockedVideoOrientation;

    public ScreenInfo(Rect contentRect, Size unlockedVideoSize, int deviceRotation, int lockedVideoOrientation) {
        this.contentRect = contentRect;
        this.unlockedVideoSize = unlockedVideoSize;
        this.deviceRotation = deviceRotation;
        this.lockedVideoOrientation = lockedVideoOrientation;
    }

    public Rect getContentRect() {
        return contentRect;
    }

    /**
     * Return the video size as if locked video orientation was not set.
     *
     * @return the unlocked video size
     */
    public Size getUnlockedVideoSize() {
        return unlockedVideoSize;
    }

    /**
     * Return the actual video size if locked video orientation is set.
     *
     * @return the actual video size
     */
    public Size getVideoSize() {
        if (getVideoRotation() % 2 == 0) {
            return unlockedVideoSize;
        }

        return unlockedVideoSize.rotate();
    }

    public int getDeviceRotation() {
        return deviceRotation;
    }

    public ScreenInfo withDeviceRotation(int newDeviceRotation) {
        if (newDeviceRotation == deviceRotation) {
            return this;
        }
        // true if changed between portrait and landscape
        boolean orientationChanged = (deviceRotation + newDeviceRotation) % 2 != 0;
        Rect newContentRect;
        Size newUnlockedVideoSize;
        if (orientationChanged) {
            newContentRect = flipRect(contentRect);
            newUnlockedVideoSize = unlockedVideoSize.rotate();
        } else {
            newContentRect = contentRect;
            newUnlockedVideoSize = unlockedVideoSize;
        }
        return new ScreenInfo(newContentRect, newUnlockedVideoSize, newDeviceRotation, lockedVideoOrientation);
    }

    public static ScreenInfo computeScreenInfo(DisplayInfo displayInfo, VideoSettings videoSettings) {
        int lockedVideoOrientation = videoSettings.getLockedVideoOrientation();
        Rect crop = videoSettings.getCrop();
        int rotation = displayInfo.getRotation();

        if (lockedVideoOrientation == Device.LOCK_VIDEO_ORIENTATION_INITIAL) {
            // The user requested to lock the video orientation to the current orientation
            lockedVideoOrientation = rotation;
        }

        Size deviceSize = displayInfo.getSize();
        Rect contentRect = new Rect(0, 0, deviceSize.getWidth(), deviceSize.getHeight());
        if (crop != null) {
            if (rotation % 2 != 0) { // 180s preserve dimensions
                // the crop (provided by the user) is expressed in the natural orientation
                crop = flipRect(crop);
            }
            if (!contentRect.intersect(crop)) {
                // intersect() changes contentRect so that it is intersected with crop
                Ln.w("Crop rectangle (" + formatCrop(crop) + ") does not intersect device screen (" + formatCrop(deviceSize.toRect()) + ")");
                contentRect = new Rect(); // empty
            }
        }

        Size bounds = videoSettings.getBounds();
        Log.d(TAG, "Bounds: " + bounds);

        Size videoSize = computeVideoSize(contentRect.width(), contentRect.height(), bounds);

        // the output video might have a different aspect ratio than the device screen, due to the fact that h264 encoders can only transfer resolutions multiple of 16
        // so we have 2 options in such a case:
        // 1. keep aspect ratio -> black bar(s) right and bottom of video
        // 2. distort video -> slightly different video, but hardly visible
        // In this case we prefer option 1 (black bars). TODO: make KeepAspectRatio configureable
        boolean KeepAspectRatio = true;
        if (KeepAspectRatio) {
            int paddingRight = videoSize.getWidth() - contentRect.width();
            if (paddingRight != 0) {
                contentRect.right += paddingRight;
            }

            int paddingBottom = videoSize.getHeight() - contentRect.height();
            if (paddingBottom != 0) {
                contentRect.bottom += paddingBottom;
            }
            //Log.d(TAG, "contentRect after" + contentRect.toString());
            Log.d(TAG, "videoSize width: " + videoSize.getWidth() + " videoSize.height:" + videoSize.getHeight());
        }
        return new ScreenInfo(contentRect, videoSize, rotation, lockedVideoOrientation);
    }

    private static String formatCrop(Rect rect) {
        return rect.width() + ":" + rect.height() + ":" + rect.left + ":" + rect.top;
    }

    private static int roundUp(int n, int multiple) {
        return (int) Math.floor((n + multiple - 1) / multiple) * multiple;
    }

    private static Size computeVideoSize(int w, int h, Size bounds) {
        int multiple = 16;
        if (bounds == null) {
            // in case it's not a multiple of 16
            w = roundUp(w, multiple);
            h = roundUp(h, multiple);
            return new Size(w, h);
        }
        int boundsWidth = bounds.getWidth();
        int boundsHeight = bounds.getHeight();
        int scaledHeight;
        int scaledWidth;
        if (boundsWidth > w) {
            scaledHeight = h;
        } else {
            scaledHeight = boundsWidth * h / w;
        }
        if (boundsHeight > scaledHeight) {
            boundsHeight = scaledHeight;
        }
        if (boundsHeight == h) {
            scaledWidth = w;
        } else {
            scaledWidth = boundsHeight * w / h;
        }
        if (boundsWidth > scaledWidth) {
            boundsWidth = scaledWidth;
        }
        boundsWidth = roundUp(boundsWidth, multiple);
        boundsHeight = roundUp(boundsHeight, multiple);
        return new Size(boundsWidth, boundsHeight);
    }

    private static Rect flipRect(Rect crop) {
        return new Rect(crop.top, crop.left, crop.bottom, crop.right);
    }

    /**
     * Return the rotation to apply to the device rotation to get the requested locked video orientation
     *
     * @return the rotation offset
     */
    public int getVideoRotation() {
        if (lockedVideoOrientation == -1) {
            // no offset
            return 0;
        }
        return (deviceRotation + 4 - lockedVideoOrientation) % 4;
    }

    /**
     * Return the rotation to apply to the requested locked video orientation to get the device rotation
     *
     * @return the (reverse) rotation offset
     */
    public int getReverseVideoRotation() {
        if (lockedVideoOrientation == -1) {
            // no offset
            return 0;
        }
        return (lockedVideoOrientation + 4 - deviceRotation) % 4;
    }

    public byte[] toByteArray() {
        ByteBuffer temp = ByteBuffer.allocate(6 * 4 + 1);
        temp.putInt(contentRect.left);
        temp.putInt(contentRect.top);
        temp.putInt(contentRect.right);
        temp.putInt(contentRect.bottom);
        temp.putInt(unlockedVideoSize.getWidth());
        temp.putInt(unlockedVideoSize.getHeight());
        temp.put((byte) getVideoRotation());
        return temp.array();
    }
}
