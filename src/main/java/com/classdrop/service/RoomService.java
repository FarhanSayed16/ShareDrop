package com.classdrop.service;

import com.classdrop.exception.InvalidRoomCodeException;
import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.util.Constants;
import com.classdrop.util.RoomCodeGenerator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoomService {
    private final RoomCodeGenerator codeGenerator;
    private final com.classdrop.db.SessionRepository sessionRepository;
    private final com.classdrop.db.DeviceRepository deviceRepository;
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();

    private volatile RoomSession currentSession;

    // Brute-force throttling state
    private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();

    public RoomService() {
        this(new RoomCodeGenerator(), null, null);
    }

    public RoomService(RoomCodeGenerator codeGenerator) {
        this(codeGenerator, null, null);
    }

    public RoomService(RoomCodeGenerator codeGenerator, com.classdrop.db.SessionRepository sessionRepository, com.classdrop.db.DeviceRepository deviceRepository) {
        this.codeGenerator = codeGenerator;
        this.sessionRepository = sessionRepository;
        this.deviceRepository = deviceRepository;
    }

    public synchronized RoomSession createSession(NetworkMode mode, String hostIp) {
        if (currentSession != null && currentSession.isActive()) {
            currentSession.end();
            if (sessionRepository != null) {
                sessionRepository.updateEndTime(currentSession.getId(), currentSession.getEndedAt());
            }
            notifySessionEnded(currentSession);
        }

        String code = codeGenerator.generate();
        currentSession = new RoomSession(code, mode, hostIp);
        failedAttempts.clear();
        blockedUntil.clear();

        if (sessionRepository != null) {
            sessionRepository.insert(currentSession);
        }

        notifySessionStarted(currentSession);
        return currentSession;
    }

    public synchronized Device join(String roomCode, String displayName, String ip)
            throws InvalidRoomCodeException, SessionExpiredException {
        
        if (currentSession == null || !currentSession.isActive()) {
            throw new SessionExpiredException("No active ClassDrop session. Please ask the host to start one.");
        }

        // Check throttle
        if (ip != null) {
            Long unblockTime = blockedUntil.get(ip);
            if (unblockTime != null && System.currentTimeMillis() < unblockTime) {
                long remainingSecs = (unblockTime - System.currentTimeMillis()) / 1000;
                throw new InvalidRoomCodeException("Too many failed attempts. Try again in " + remainingSecs + " seconds.");
            }
        }

        // Validate code
        if (roomCode == null || !currentSession.getRoomCode().equals(roomCode.trim())) {
            if (ip != null) {
                AtomicInteger attempts = failedAttempts.computeIfAbsent(ip, k -> new AtomicInteger(0));
                if (attempts.incrementAndGet() >= Constants.BRUTE_FORCE_MAX_ATTEMPTS) {
                    blockedUntil.put(ip, System.currentTimeMillis() + (Constants.BRUTE_FORCE_WINDOW_SECONDS * 1000L));
                    attempts.set(0);
                }
            }
            throw new InvalidRoomCodeException("Invalid 6-digit room code.");
        }

        // Reset failed counter on success
        if (ip != null) {
            failedAttempts.remove(ip);
            blockedUntil.remove(ip);
        }

        String name = (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : "Anonymous";
        Device device = new Device(name, ip);
        currentSession.addDevice(device);

        if (deviceRepository != null) {
            deviceRepository.insert(device, currentSession.getId());
        }

        notifyDeviceJoined(device);
        return device;
    }

    public Device validateToken(String token) throws SessionExpiredException {
        if (currentSession == null || !currentSession.isActive()) {
            throw new SessionExpiredException("Session is no longer active.");
        }
        Device d = currentSession.findDeviceByToken(token);
        if (d != null && deviceRepository != null) {
            deviceRepository.updateLastSeen(d.getId(), d.getLastSeen());
        }
        return d;
    }

    public synchronized void endSession() {
        if (currentSession != null && currentSession.isActive()) {
            currentSession.end();
            if (sessionRepository != null) {
                sessionRepository.updateEndTime(currentSession.getId(), currentSession.getEndedAt());
            }
            notifySessionEnded(currentSession);
        }
    }

    public RoomSession current() throws SessionExpiredException {
        if (currentSession == null || !currentSession.isActive()) {
            throw new SessionExpiredException("No active ClassDrop session.");
        }
        return currentSession;
    }

    public RoomSession getCurrentSessionOrNull() {
        return currentSession;
    }

    public synchronized void restoreSession(RoomSession restoredSession) {
        if (this.currentSession != null && this.currentSession.isActive()) {
            this.currentSession.end();
        }
        this.currentSession = restoredSession;
        RoomSession.resetActiveUsers();
        if (restoredSession != null) {
            for (int i = 0; i < restoredSession.getDevices().size(); i++) {
                RoomSession.incrementActiveUsers();
            }
            notifySessionStarted(restoredSession);
        }
    }

    public void addListener(SessionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    public List<SessionListener> getListeners() {
        return listeners;
    }

    private void notifySessionStarted(RoomSession session) {
        for (SessionListener l : listeners) {
            try { l.onSessionStarted(session); } catch (Exception ignored) {}
        }
    }

    private void notifySessionEnded(RoomSession session) {
        for (SessionListener l : listeners) {
            try { l.onSessionEnded(session); } catch (Exception ignored) {}
        }
    }

    private void notifyDeviceJoined(Device device) {
        for (SessionListener l : listeners) {
            try { l.onDeviceJoined(device); } catch (Exception ignored) {}
        }
    }
}
