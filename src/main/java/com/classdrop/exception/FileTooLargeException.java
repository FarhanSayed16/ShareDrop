package com.classdrop.exception;

public class FileTooLargeException extends ClassDropException {
    public FileTooLargeException(String message) {
        super(413, "FILE_TOO_LARGE", message);
    }
}
