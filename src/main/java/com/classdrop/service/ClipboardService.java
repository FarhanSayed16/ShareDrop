package com.classdrop.service;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.ClipboardEntry;
import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClipboardService {
    private final RoomService roomService;
    private final com.classdrop.db.SnippetRepository snippetRepository;
    private final List<ClipboardEntry> entries = new CopyOnWriteArrayList<>();

    public ClipboardService(RoomService roomService) {
        this(roomService, null);
    }

    public ClipboardService(RoomService roomService, com.classdrop.db.SnippetRepository snippetRepository) {
        this.roomService = Objects.requireNonNull(roomService);
        this.snippetRepository = snippetRepository;
    }

    public ClipboardEntry post(Device device, String title, String language, String body)
            throws SessionExpiredException {

        RoomSession session = roomService.current();

        if (body == null) {
            body = "";
        }

        // Cap snippet body to 64 KB (Constants.CLIPBOARD_MAX_BODY_BYTES)
        if (body.length() > Constants.CLIPBOARD_MAX_BODY_BYTES) {
            body = body.substring(0, Constants.CLIPBOARD_MAX_BODY_BYTES);
        }

        String devId = (device != null) ? device.getId() : "host";
        String devName = (device != null) ? device.getDisplayName() : "Host";
        String cleanTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : null;
        String lang = (language != null && !language.trim().isEmpty()) ? language.trim().toLowerCase() : "text";

        ClipboardEntry entry = new ClipboardEntry(
            session.getId(),
            devId,
            devName,
            cleanTitle,
            lang,
            body
        );

        entries.add(entry);
        if (snippetRepository != null) {
            snippetRepository.insert(entry, session.getId(), devId);
        }
        notifyClipboardUpdated(entry);

        return entry;
    }

    public List<ClipboardEntry> list() throws SessionExpiredException {
        roomService.current(); // Validate session is active
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public void clear() {
        entries.clear();
    }

    public void restoreEntries(List<ClipboardEntry> restored) {
        entries.clear();
        if (restored != null) {
            entries.addAll(restored);
        }
    }

    private void notifyClipboardUpdated(ClipboardEntry entry) {
        for (SessionListener l : roomService.getListeners()) {
            try { l.onClipboardUpdated(entry); } catch (Exception ignored) {}
        }
    }
}
