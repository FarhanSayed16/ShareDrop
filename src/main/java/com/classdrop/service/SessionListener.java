package com.classdrop.service;

import com.classdrop.model.ClipboardEntry;
import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.model.TransferRecord;

public interface SessionListener {
    default void onDeviceJoined(Device device) {}
    default void onDeviceLeft(Device device) {}
    default void onTransferStarted(TransferRecord record) {}
    default void onTransferProgress(String transferId, long bytesTransferred, long totalBytes) {}
    default void onTransferCompleted(TransferRecord record) {}
    default void onTransferFailed(TransferRecord record, String reason) {}
    default void onSessionStarted(RoomSession session) {}
    default void onSessionEnded(RoomSession session) {}
    default void onClipboardUpdated(ClipboardEntry entry) {}
}
