package com.classdrop.exception;

public class InvalidPassphraseException extends ClassDropException {
    public InvalidPassphraseException(String message) {
        super(403, "INVALID_PASSPHRASE", message);
    }
}
