package net.burningtnt.terracotta;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Keep
public final class TerracottaAndroidAPI {

    public interface VpnServiceCallback {
        void onStartVpnService();
    }

    public interface VpnServiceRequest {

        ParcelFileDescriptor startVpnService(VpnService.Builder builder);

        void reject();
    }

    public static final class Metadata {
        private final String terracottaVersion;

        private final long terracottaCompileTime;

        private final String easyTierVersion;

        public Metadata(String terracottaVersion, long terracottaCompileTime, String easyTierVersion) {
            this.terracottaVersion = terracottaVersion;
            this.terracottaCompileTime = terracottaCompileTime;
            this.easyTierVersion = easyTierVersion;
        }

        public String getTerracottaVersion() {
            return terracottaVersion;
        }

        public long getTerracottaCompileTime() {
            return terracottaCompileTime;
        }

        public String getEasyTierVersion() {
            return easyTierVersion;
        }
    }

    static {
        System.setProperty("net.burningtnt.terracotta.native_location", TerracottaAndroidAPI.class.getName().replace('.', '/'));
        System.loadLibrary("terracotta");
    }

    private static volatile VpnServiceRequest pendingRequest = null;

    private static final class RuntimeContext {
        private final VpnServiceCallback vpnServiceCallback;

        private final RandomAccessFile logging;

        public RuntimeContext(VpnServiceCallback vpnServiceCallback, RandomAccessFile logging) {
            this.vpnServiceCallback = vpnServiceCallback;
            this.logging = logging;
        }
    }

    private static volatile RuntimeContext runtimeContext = null;

    public static VpnServiceRequest getPendingVpnServiceRequest() {
        VpnServiceRequest handle = pendingRequest;
        if (handle == null) {
            throw new IllegalStateException("There's no pending VpnService request.");
        }
        return handle;
    }

    public static synchronized Metadata initialize(String root, String logFile, VpnServiceCallback callback) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(logFile, "logFile");
        Objects.requireNonNull(callback, "callback");

        if (root.isEmpty()) {
            throw new IllegalArgumentException("The path {root} cannot be an empty string.");
        }

        if (logFile.isEmpty()) {
            throw new IllegalArgumentException("The path {logFile} cannot be an empty string.");
        }

        if (runtimeContext != null) {
            throw new IllegalStateException("Terracotta Android has already started.");
        }

        File base = new File(root, "rs");
        base.mkdirs();
        if (!base.isDirectory()) {
            throw new RuntimeException("Cannot create net.burningtnt.terracotta/rs directory.");
        }

        RandomAccessFile logging;
        int fd;
        try {
            logging = new RandomAccessFile(new File(logFile), "rw");
            fd = ParcelFileDescriptor.dup(logging.getFD()).detachFd();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int code = start0(base.getAbsolutePath(), fd);
        if (code != 0) {
            throw new RuntimeException("Cannot start Terracotta Android: " + code);
        }

        runtimeContext = new RuntimeContext(callback, logging);

        String[] parts = getMetadata0().split("\0", 4);
        if (parts.length != 3) {
            throw new AssertionError("Should NOT be here.");
        }
        return new Metadata(parts[0], Long.parseLong(parts[1]), parts[2]);
    }

    public static String getState() {
        assertStarted();
        return getState0();
    }

    public static void setWaiting() {
        assertStarted();
        setWaiting0();
    }

    public static void setScanning(@Nullable String room, @Nullable String player, @Nullable List<String> extraNodes) {
        assertStarted();
        setScanning0(room, player, extraNodes == null ? null : String.join("\0", extraNodes));
    }

    public static boolean setGuesting(String room, @Nullable String player, @Nullable List<String> extraNodes) {
        Objects.requireNonNull(room, "room");

        assertStarted();
        return setGuesting0(room, player, extraNodes == null ? null : String.join("\0", extraNodes));
    }

    public enum RoomType {
        TERRACOTTA_LEGACY, PCL2CE, SCAFFOLDING
    }

    public static RoomType parseRoomCode(String room) {
        Objects.requireNonNull(room, "room");

        assertStarted();
        switch (verifyRoomCode0(room)) {
            case -1:
                return null;
            case 1:
                return RoomType.TERRACOTTA_LEGACY;
            case 2:
                return RoomType.PCL2CE;
            case 3:
                return RoomType.SCAFFOLDING;
            default:
                throw new AssertionError("Should NOT be here.");
        }
    }

    public static Reader collectLogs() throws IOException {
        assertStarted();

        long ptr = prepareExportLogs0();
        if (ptr == 0) {
            throw new RuntimeException("Cannot export logs from Terracotta Android.");
        }

        RandomAccessFile file = runtimeContext.logging;
        file.seek(0);

        return new BufferedReader(new InputStreamReader(new InputStream() {
            private final AtomicBoolean closed = new AtomicBoolean(false);

            @Override
            public int read() throws IOException {
                assertOpen();
                return file.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                assertOpen();
                return file.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                assertOpen();
                return file.read(b, off, len);
            }

            @Override
            public int available() throws IOException {
                assertOpen();
                return Math.toIntExact(file.length() - file.getFilePointer());
            }

            @Override
            public void close() throws IOException {
                super.close();
                cleanup();
            }

            @Override
            protected void finalize() throws Throwable {
                super.finalize();
                cleanup();
            }

            private void assertOpen() throws IOException {
                if (closed.get()) {
                    throw new IOException("Stream has already been closed");
                }
            }

            private void cleanup() {
                if (closed.compareAndSet(false, true)) {
                    finishExportLogs0(ptr);
                }
            }
        }, StandardCharsets.UTF_8));
    }

    @Deprecated
    public static void panic() {
        panic0();
        throw new AssertionError("Should NOT be here: A RuntimeException should be thrown in panic0");
    }

    private static final long FD_PENDING = ((long) Integer.MAX_VALUE) + 1;
    private static final long FD_REJECT = FD_PENDING + 1;

    @Keep
    @SuppressWarnings("unused")
    private static int onVpnServiceStateChanged(byte ip1, byte ip2, byte ip3, byte ip4, short network_length, String cidr) throws UnknownHostException {
        if (pendingRequest != null) {
            throw new AssertionError("Should NOT be here.");
        }

        AtomicLong fd = new AtomicLong(FD_PENDING);
        InetAddress address = InetAddress.getByAddress(new byte[]{ip1, ip2, ip3, ip4});

        pendingRequest = new VpnServiceRequest() {
            @Override
            public ParcelFileDescriptor startVpnService(VpnService.Builder builder) {
                builder.addAddress(address, network_length)
                        .addDnsServer("223.5.5.5")
                        .addDnsServer("114.114.114.114");

                if (!cidr.isEmpty()) {
                    for (String part : cidr.split("\0")) {
                        String[] parts = part.split("/", 3);
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("Illegal CIDR: " + Arrays.toString(parts));
                        }
                        builder.addRoute(parts[0], Integer.parseInt(parts[1]));
                    }
                }

                ParcelFileDescriptor connection = builder.establish();
                if (connection == null) {
                    throw new RuntimeException("Cannot establish a VPN connection.");
                }

                fd.set(connection.getFd());
                return connection;
            }

            @Override
            public void reject() {
                fd.set(FD_REJECT);
            }
        };

        TerracottaAndroidAPI.runtimeContext.vpnServiceCallback.onStartVpnService();

        long timestamp = System.currentTimeMillis();
        while (true) {
            long value = fd.get();
            if (value == FD_PENDING) {
                if (System.currentTimeMillis() - timestamp >= 30000) {
                    Log.wtf("TerracottaAndroidAPI", "VpnService Request hasn't been fulfilled in 30s.");
                    throw new IllegalStateException();
                }
                Thread.yield();
            } else if (value == FD_REJECT) {
                pendingRequest = null;
                throw new IllegalStateException();
            } else {
                pendingRequest = null;

                if ((int) value != value) {
                    throw new AssertionError("Should NOT be here.");
                }
                return (int) value;
            }
        }
    }

    private static void assertStarted() {
        if (runtimeContext == null) {
            throw new IllegalStateException("Terracotta Android hasn't started yet.");
        }
    }

    @Keep
    private static native int start0(String baseDir, int loggingFD);

    @Keep
    private static native String getState0();

    @Keep
    private static native void setWaiting0();

    @Keep
    private static native void setScanning0(String room, String player, String extraNodes);

    @Keep
    private static native boolean setGuesting0(String room, String player, String extraNodes);

    @Keep
    private static native int verifyRoomCode0(String room);

    @Keep
    private static native String getMetadata0();

    @Keep
    private static native long prepareExportLogs0();

    @Keep
    private static native void finishExportLogs0(long pointer);

    @Keep
    private static native void panic0();
}
