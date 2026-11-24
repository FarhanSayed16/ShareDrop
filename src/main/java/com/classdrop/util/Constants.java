package com.classdrop.util;

public final class Constants {
    private Constants() {}

    public static final int PORT = 8080;
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100 MB
    public static final int ROOM_CODE_LENGTH = 6;
    public static final int CHUNK_BUFFER_SIZE = 65536; // 64 KB
    /** Short HTTP requests (upload/download/API). */
    public static final int THREAD_POOL_SIZE = 16;
    /**
     * Extra threads reserved so SSE /events clients do not starve file transfers
     * (JDK HttpServer runs handlers on its executor; SSE must occupy a thread).
     */
    public static final int SSE_MAX_CLIENTS = 48;
    public static final int HTTP_EXECUTOR_SIZE = THREAD_POOL_SIZE + SSE_MAX_CLIENTS;
    public static final int CLEANUP_INTERVAL_SECONDS = 30;
    public static final int SESSION_TIMEOUT_MINUTES = 120;
    public static final int INTERNET_SESSION_TIMEOUT_MINUTES = 60;
    public static final int BRUTE_FORCE_MAX_ATTEMPTS = 5;
    public static final int BRUTE_FORCE_WINDOW_SECONDS = 60;
    public static final int CLIPBOARD_MAX_BODY_BYTES = 65536; // 64 KB
    public static final int POLLING_INTERVAL_MS = 3000;

    /** Dangerous / unnecessary extensions blocked on upload */
    public static final String[] BLOCKED_EXTENSIONS = {
        ".exe", ".bat", ".cmd", ".com", ".msi", ".scr", ".ps1", ".vbs", ".jse",
        ".wsf", ".wsh", ".dll", ".sys", ".apk", ".dmg"
    };
}
