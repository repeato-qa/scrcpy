package com.genymobile.scrcpy;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.os.Build;

import java.io.File;
import java.io.IOException;

public final class Server {

    private static final String SERVER_PATH = "/data/local/tmp/scrcpy-server.jar";

    private Server() {
        // not instantiable
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void parseArguments(Options options, VideoSettings videoSettings, String... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing client version");
        }

        String clientVersion = args[0];
        if (!clientVersion.equals(BuildConfig.VERSION_NAME)) {
            throw new IllegalArgumentException(
                    "The server version (" + BuildConfig.VERSION_NAME + ") does not match the client " + "(" + clientVersion + ")");
        }

        if (args.length < 10) {
            throw new IllegalArgumentException("Expecting at least 6 parameters");
        }

        int maxSize = Integer.parseInt(args[1]) & ~7; // multiple of 8
        if (maxSize != 0) {
            videoSettings.setMaxSize(maxSize);
        }

        int bitRate = Integer.parseInt(args[2]);
        videoSettings.setBitRate(bitRate);

        int maxFps = Integer.parseInt(args[3]);
        videoSettings.setMaxFps(maxFps);

        int lockedVideoOrientation = Integer.parseInt(args[4]);
        videoSettings.setLockedVideoOrientation(lockedVideoOrientation);

        // use "adb forward" instead of "adb tunnel"? (so the server must listen)
        boolean tunnelForward = Boolean.parseBoolean(args[5]);
        options.setTunnelForward(tunnelForward);

        Rect crop = parseCrop(args[6]);
        videoSettings.setCrop(crop);

        boolean sendFrameMeta = Boolean.parseBoolean(args[7]);
        videoSettings.setSendFrameMeta(sendFrameMeta);

        boolean control = Boolean.parseBoolean(args[8]);
        options.setControl(control);

        int displayId = Integer.parseInt(args[9]);
        options.setDisplayId(displayId);

        if (args.length > 12) {
            throw new IllegalArgumentException("Expecting no more then 11 parameters");
        }

        if (args.length > 10) {
            if (args[10].toLowerCase().equals("web")) {
                options.setServerType(Options.TYPE_WEB_SOCKET);
            }
        }
        if (args.length > 11) {
            int portNumber = Integer.parseInt(args[11]);
            options.setPortNumber(portNumber);
        }
    }

    private static Rect parseCrop(String crop) {
        if ("-".equals(crop)) {
            return null;
        }
        // input format: "width:height:x:y"
        String[] tokens = crop.split(":");
        if (tokens.length != 4) {
            throw new IllegalArgumentException("Crop must contains 4 values separated by colons: \"" + crop + "\"");
        }
        int width = Integer.parseInt(tokens[0]);
        int height = Integer.parseInt(tokens[1]);
        int x = Integer.parseInt(tokens[2]);
        int y = Integer.parseInt(tokens[3]);
        return new Rect(x, y, x + width, y + height);
    }

    private static void unlinkSelf() {
        try {
            new File(SERVER_PATH).delete();
        } catch (Exception e) {
            Ln.e("Could not unlink server", e);
        }
    }

    private static void suggestFix(Throwable e) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (e instanceof MediaCodec.CodecException) {
                MediaCodec.CodecException mce = (MediaCodec.CodecException) e;
                if (mce.getErrorCode() == 0xfffffc0e) {
                    Ln.e("The hardware encoder is not able to encode at the given definition.");
                    Ln.e("Try with a lower definition:");
                    Ln.e("    scrcpy -m 1024");
                }
            }
        }
        if (e instanceof InvalidDisplayIdException) {
            InvalidDisplayIdException idie = (InvalidDisplayIdException) e;
            int[] displayIds = idie.getAvailableDisplayIds();
            if (displayIds != null && displayIds.length > 0) {
                Ln.e("Try to use one of the available display ids:");
                for (int id : displayIds) {
                    Ln.e("    scrcpy --display " + id);
                }
            }
        }
    }

    public static void main(String... args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Ln.e("Exception on thread " + t, e);
                suggestFix(e);
            }
        });

        unlinkSelf();
        Options options = new Options();
        VideoSettings videoSettings = new VideoSettings();
        parseArguments(options, videoSettings, args);
        if (options.getServerType() == Options.TYPE_LOCAL_SOCKET) {
            new DesktopConnection(options, videoSettings);
        } else if (options.getServerType() == Options.TYPE_WEB_SOCKET) {
            new WebSocketConnection(options, videoSettings);
        }
    }
}
