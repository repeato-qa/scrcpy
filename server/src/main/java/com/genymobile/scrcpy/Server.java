package com.genymobile.scrcpy;

import android.graphics.Rect;

import java.io.File;
import java.io.IOException;

public final class Server {

    private static final String SERVER_PATH = "/data/local/tmp/scrcpy-server.jar";

    private Server() {
        // not instantiable
    }

//    private static void scrcpy(Options options) throws IOException {
//        final Device device = new Device(options);
//        boolean tunnelForward = options.isTunnelForward();
//        try (DesktopConnection connection = DesktopConnection.open(device, tunnelForward)) {
//            ScreenEncoder screenEncoder = new ScreenEncoder(options.getSendFrameMeta(), options.getBitRate());
//
//            if (options.getControl()) {
//                Controller controller = new Controller(device, connection);
//
//                // asynchronous
//                startController(controller);
//                startDeviceMessageSender(controller.getSender());
//            }
//
//            try {
//                // synchronous
//                screenEncoder.streamScreen(device, connection.getVideoFd());
//            } catch (IOException e) {
//                // this is expected on close
//                Ln.d("Screen streaming stopped");
//            }
//        }
//    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void parseArguments(Options options, VideoSettings videoSettings, String... args) {
        if (args.length < 6) {
            throw new IllegalArgumentException("Expecting at least 6 parameters");
        }

        int maxSize = Integer.parseInt(args[0]) & ~7; // multiple of 8
        if (maxSize != 0) {
            videoSettings.setBounds(new Size(maxSize, maxSize));
        }

        int bitRate = Integer.parseInt(args[1]);
        videoSettings.setBitRate(bitRate);

        // use "adb forward" instead of "adb tunnel"? (so the server must listen)
        boolean tunnelForward = Boolean.parseBoolean(args[2]);
        options.setTunnelForward(tunnelForward);

        Rect crop = parseCrop(args[3]);
        videoSettings.setCrop(crop);

        boolean sendFrameMeta = Boolean.parseBoolean(args[4]);
        videoSettings.setSendFrameMeta(sendFrameMeta);

        boolean control = Boolean.parseBoolean(args[5]);
        options.setControl(control);

        if (args.length > 7) {
            throw new IllegalArgumentException("Expecting no more then 6 parameters");
        }

        if (args.length == 7 && args[6].toLowerCase().equals("web")) {
            options.setServerType(Options.TYPE_WEB_SOCKET);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
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

    public static void main(String... args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Ln.e("Exception on thread " + t, e);
            }
        });

        unlinkSelf();
        Options options = new Options();
        VideoSettings videoSettings = new VideoSettings();
        parseArguments(options, videoSettings, args);
        if (options.getServerType() == Options.TYPE_LOCAL_SOCKET) {
            new DesktopConnection(options, videoSettings);
        } else if (options.getServerType() == Options.TYPE_WEB_SOCKET) {
            new WebSocketConnection(videoSettings);
        }
    }
}
