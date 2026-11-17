package com.cmc.launcher.bridge;

import androidx.annotation.Keep;

@Keep
public final class LoggerBridge {

    @Keep public static native void start(String filePath);

    @Keep public static native void append(String log);

    @Keep public static native void setListener(EventLogListener listener);

    @Keep
    public interface EventLogListener {
        @Keep
        void onEventLogged(String text);
    }

    public static void appendTitle(String title) {
        String logText = "==================== " + title + " ====================";
        append(logText);
    }

    public static void appendInfo(String info) {
        String logText = "▷ " + info;
        append(logText);
    }

    static {
        NativeLibraryLoader.loadPojavLib();
    }
}
