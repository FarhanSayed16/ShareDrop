package com.classdrop.util;

public final class Constants {
    private Constants() {}

    public static final int PORT = 8080;
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100 MB
    public static final int ROOM_CODE_LENGTH = 6;
    public static final int CHUNK_BUFFER_SIZE = 65536; // 64 KB
    public static final int THREAD_POOL_SIZE = 16;
    public static final int CLEANUP_INTERVAL_SECONDS = 30;
    public static final int SESSION_TIMEOUT_MINUTES = 120;
    public static final int BRUTE_FORCE_MAX_ATTEMPTS = 5;
    public static final int BRUTE_FORCE_WINDOW_SECONDS = 60;
    public static final int CLIPBOARD_MAX_BODY_BYTES = 65536; // 64 KB
    public static final int POLLING_INTERVAL_MS = 3000;
}
