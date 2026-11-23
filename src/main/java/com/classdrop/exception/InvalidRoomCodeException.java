package com.classdrop.exception;

public class InvalidRoomCodeException extends ClassDropException {
    public InvalidRoomCodeException(String message) {
        super(403, "INVALID_ROOM_CODE", message);
    }
}
