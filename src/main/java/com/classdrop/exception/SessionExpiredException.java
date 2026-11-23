package com.classdrop.exception;

public class SessionExpiredException extends ClassDropException {
    public SessionExpiredException(String message) {
        super(410, "SESSION_EXPIRED", message);
    }
}
