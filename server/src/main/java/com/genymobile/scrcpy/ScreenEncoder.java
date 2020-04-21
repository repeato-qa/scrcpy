package com.genymobile.scrcpy;

import com.genymobile.scrcpy.wrappers.SurfaceControl;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.IBinder;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenEncoder implements Connection.StreamInvalidateListener, Runnable {

    private static final int DEFAULT_I_FRAME_INTERVAL = 10; // seconds
    private static final int REPEAT_FRAME_DELAY_US = 100_000; // repeat after 100ms
    private static final String KEY_MAX_FPS_TO_ENCODER = "max-fps-to-encoder";

    private static final int NO_PTS = -1;

    private final AtomicBoolean streamIsInvalide = new AtomicBoolean();
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(12);
    private Thread selectorThread;

    private int bitRate;
    private boolean sendFrameMeta;
    private long ptsOrigin;
    private Device device;
    private Connection connection;
    private VideoSettings videoSettings;
    private MediaFormat format;

    public ScreenEncoder(VideoSettings videoSettings) {
        this.videoSettings = videoSettings;
        updateFormat();
    }

    private void updateFormat() {
        format = createFormat(videoSettings.getBitRate(), videoSettings.getFrameRate(), videoSettings.getIFrameInterval());
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    @Override
    public void onStreamInvalidate() {
        Ln.d("invalidate stream");
        streamIsInvalide.set(true);
        updateFormat();
    }

    public boolean consumeStreamInvalidation() {
        return streamIsInvalide.getAndSet(false);
    }

    public boolean isAlive() {
        return selectorThread != null && selectorThread.isAlive();
    }

    public void streamScreen() throws IOException {
        Workarounds.prepareMainLooper();
        Workarounds.fillAppInfo();
        boolean alive;
        updateFormat();
        connection.setStreamInvalidateListener(this);
        do {
            MediaCodec codec = createCodec();
            IBinder display = createDisplay();
            ScreenInfo screenInfo = device.getScreenInfo();
            Rect contentRect = screenInfo.getContentRect();
            // include the locked video orientation
                Rect videoRect = screenInfo.getVideoSize().toRect();
            // does not include the locked video orientation
                Rect unlockedVideoRect = screenInfo.getUnlockedVideoSize().toRect();
                int videoRotation = screenInfo.getVideoRotation();
                int layerStack = device.getLayerStack();setSize(format, videoRect.width(), videoRect.height());
            configure(codec, format);
            Surface surface = codec.createInputSurface();
            setDisplaySurface(display, surface, videoRotation, contentRect, unlockedVideoRect, layerStack);
            codec.start();
            try {
                alive = encode(codec);
                // do not call stop() on exception, it would trigger an IllegalStateException
                codec.stop();
            } finally {
                destroyDisplay(display);
                codec.release();
                surface.release();
            }
        } while (alive);
        Ln.d("Screen streaming was stopped");
    }

    private boolean encode(MediaCodec codec) throws IOException {
        boolean eof = false;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (!consumeStreamInvalidation() && !eof && connection.hasConnections()) {
            int outputBufferId = codec.dequeueOutputBuffer(bufferInfo, -1);
            eof = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            try {
                if (consumeStreamInvalidation()) {
                    // must restart encoding with new size
                    break;
                }
                if (outputBufferId >= 0) {
                    ByteBuffer codecBuffer = codec.getOutputBuffer(outputBufferId);

                    if (videoSettings.getSendFrameMeta()) {
                        writeFrameMeta(bufferInfo, codecBuffer.remaining());
                    }

                    connection.send(codecBuffer);
                }
            } finally {
                if (outputBufferId >= 0) {
                    codec.releaseOutputBuffer(outputBufferId, false);
                }
            }
        }

        return !eof && connection.hasConnections();
    }

    private void writeFrameMeta(MediaCodec.BufferInfo bufferInfo, int packetSize) throws IOException {
        headerBuffer.clear();

        long pts;
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            pts = NO_PTS; // non-media data packet
        } else {
            if (ptsOrigin == 0) {
                ptsOrigin = bufferInfo.presentationTimeUs;
            }
            pts = bufferInfo.presentationTimeUs - ptsOrigin;
        }

        headerBuffer.putLong(pts);
        headerBuffer.putInt(packetSize);
        headerBuffer.flip();
        connection.send(headerBuffer);
    }

    private static MediaCodec createCodec() throws IOException {
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
    }

    private static MediaFormat createFormat(int bitRate, int maxFps, int iFrameInterval) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_AVC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        // must be present to configure the encoder, but does not impact the actual frame rate, which is variable
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
        // display the very first frame, and recover from bad quality when no new frames
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_US); // µs
        if (maxFps > 0) {
            // The key existed privately before Android 10:
            // <https://android.googlesource.com/platform/frameworks/base/+/625f0aad9f7a259b6881006ad8710adce57d1384%5E%21/>
            // <https://github.com/Genymobile/scrcpy/issues/488#issuecomment-567321437>
            format.setFloat(KEY_MAX_FPS_TO_ENCODER, maxFps);
        }
        return format;
    }

    private static IBinder createDisplay() {
        return SurfaceControl.createDisplay("scrcpy", true);
    }

    private static void configure(MediaCodec codec, MediaFormat format) {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private static void setSize(MediaFormat format, int width, int height) {
        format.setInteger(MediaFormat.KEY_WIDTH, width);
        format.setInteger(MediaFormat.KEY_HEIGHT, height);
    }

    private static void setDisplaySurface(IBinder display, Surface surface, int orientation, Rect deviceRect, Rect displayRect, int layerStack) {
        SurfaceControl.openTransaction();
        try {
            SurfaceControl.setDisplaySurface(display, surface);
            SurfaceControl.setDisplayProjection(display, orientation, deviceRect, displayRect);
            SurfaceControl.setDisplayLayerStack(display, layerStack);
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    private static void destroyDisplay(IBinder display) {
        SurfaceControl.destroyDisplay(display);
    }

    @Override
    public void run() {
        synchronized (this) {
            if (selectorThread != null && selectorThread.isAlive()) {
                throw new IllegalStateException(getClass().getName() + " can only be started once.");
            }
            selectorThread = Thread.currentThread();
        }
        try {
            this.streamScreen();
        } catch (IOException e) {
            Ln.e("Failed to start screen recorder", e);
        }
    }

    public void start(Device device, Connection connection) {
        this.device = device;
        this.connection = connection;
        if (selectorThread != null && selectorThread.isAlive())
            throw new IllegalStateException(getClass().getName() + " can only be started once.");
        new Thread(this).start();
    }
}
