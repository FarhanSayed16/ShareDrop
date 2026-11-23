package com.classdrop.exception;

public class ClassDropException extends Exception {
    private final int httpStatusCode;
    private final String errorCode;

    public ClassDropException(int httpStatusCode, String errorCode, String message) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
    }

    public ClassDropException(int httpStatusCode, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
