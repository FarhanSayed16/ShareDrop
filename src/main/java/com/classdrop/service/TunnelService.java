package com.classdrop.service;

import com.classdrop.util.Config;
import com.classdrop.util.ShareUrlBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Starts a Cloudflare quick tunnel (cloudflared) to expose local ClassDrop over HTTPS,
 * or uses a manually configured public.base.url.
 */
public final class TunnelService {
    private static final Logger LOGGER = Logger.getLogger(TunnelService.class.getName());
    private static final Pattern URL_PATTERN = Pattern.compile("https://[a-zA-Z0-9.-]+\\.trycloudflare\\.com");

    private final int localPort;
    private final AtomicReference<String> publicBaseUrl = new AtomicReference<>();
    private volatile Process tunnelProcess;
    private volatile Thread readerThread;
    private volatile boolean running;

    public TunnelService(int localPort) {
        this.localPort = localPort;
        String configured = ShareUrlBuilder.normalizeBaseUrl(Config.getString("public.base.url", ""));
        if (configured != null) {
            publicBaseUrl.set(configured);
        }
    }

    public synchronized boolean startInternetShare() {
        String configured = ShareUrlBuilder.normalizeBaseUrl(Config.getString("public.base.url", ""));
        if (configured != null) {
            publicBaseUrl.set(configured);
            running = true;
            LOGGER.info("[ClassDrop-Tunnel] Using configured public.base.url: " + configured);
            return true;
        }

        if (tunnelProcess != null && tunnelProcess.isAlive()) {
            running = true;
            return publicBaseUrl.get() != null;
        }

        String command = Config.getString("tunnel.command", "cloudflared");
        ProcessBuilder pb = new ProcessBuilder(
            command,
            "tunnel",
            "--url",
            "http://127.0.0.1:" + localPort
        );
        pb.redirectErrorStream(true);

        try {
            tunnelProcess = pb.start();
            running = true;
            readerThread = new Thread(this::readTunnelOutput, "ClassDrop-Tunnel-Reader");
            readerThread.setDaemon(true);
            readerThread.start();

            long deadline = System.currentTimeMillis() + 20_000L;
            while (System.currentTimeMillis() < deadline) {
                if (publicBaseUrl.get() != null) {
                    return true;
                }
                if (!tunnelProcess.isAlive()) {
                    running = false;
                    LOGGER.warning("[ClassDrop-Tunnel] cloudflared exited before publishing a URL");
                    cleanupFailedProcess();
                    return false;
                }
                Thread.sleep(250);
            }
            LOGGER.warning("[ClassDrop-Tunnel] Timed out waiting for public URL. Is cloudflared installed?");
            if (publicBaseUrl.get() == null) {
                // Do not leave an unmanaged zombie process
                cleanupFailedProcess();
                return false;
            }
            return true;
        } catch (Exception e) {
            running = false;
            cleanupFailedProcess();
            LOGGER.log(Level.WARNING, "[ClassDrop-Tunnel] Failed to start tunnel: {0}", e.getMessage());
            return false;
        }
    }

    private void cleanupFailedProcess() {
        Process proc = tunnelProcess;
        tunnelProcess = null;
        running = false;
        String configured = ShareUrlBuilder.normalizeBaseUrl(Config.getString("public.base.url", ""));
        if (configured == null) {
            publicBaseUrl.set(null);
        } else {
            publicBaseUrl.set(configured);
        }
        if (proc != null) {
            proc.destroy();
            try {
                if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                proc.destroyForcibly();
            }
        }
    }

    private void readTunnelOutput() {
        Process proc = tunnelProcess;
        if (proc == null) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = URL_PATTERN.matcher(line);
                if (m.find()) {
                    String url = ShareUrlBuilder.normalizeBaseUrl(m.group());
                    publicBaseUrl.set(url);
                    LOGGER.info("[ClassDrop-Tunnel] Public URL ready: " + url);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Tunnel reader stopped: {0}", e.getMessage());
        }
    }

    public synchronized void stopInternetShare() {
        running = false;
        // Keep configured public.base.url if it came from properties; clear ephemeral tunnel URL
        String configured = ShareUrlBuilder.normalizeBaseUrl(Config.getString("public.base.url", ""));
        if (configured == null) {
            publicBaseUrl.set(null);
        } else {
            publicBaseUrl.set(configured);
        }

        Process proc = tunnelProcess;
        tunnelProcess = null;
        if (proc != null) {
            proc.destroy();
            try {
                if (!proc.waitFor(3, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                proc.destroyForcibly();
            }
        }
    }

    public void setManualPublicBaseUrl(String url) {
        publicBaseUrl.set(ShareUrlBuilder.normalizeBaseUrl(url));
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl.get();
    }

    public boolean isRunning() {
        return running && publicBaseUrl.get() != null;
    }

    public boolean hasPublicUrl() {
        return publicBaseUrl.get() != null;
    }
}
