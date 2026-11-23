package com.classdrop.exception;

public class TransferFailedException extends ClassDropException {
    public TransferFailedException(String message) {
        super(500, "TRANSFER_FAILED", message);
    }

    public TransferFailedException(String message, Throwable cause) {
        super(500, "TRANSFER_FAILED", message, cause);
    }
}
