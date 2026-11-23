package com.classdrop.model;

import java.time.LocalDateTime;

public final class CodeSnippet extends SharedItem {
    private static final long serialVersionUID = 1L;
    private final String language;
    private final String body;

    public CodeSnippet(String originalName, long sizeBytes, String uploaderDeviceId, String language, String body) {
        super(originalName, sizeBytes, uploaderDeviceId);
        this.language = language != null ? language : "text";
        this.body = body != null ? body : "";
    }

    public CodeSnippet(String id, String originalName, long sizeBytes, String uploaderDeviceId, LocalDateTime createdAt, String language, String body) {
        super(id, originalName, sizeBytes, uploaderDeviceId, createdAt);
        this.language = language != null ? language : "text";
        this.body = body != null ? body : "";
    }

    @Override
    public String getKind() {
        return "SNIPPET";
    }

    @Override
    public String getContentType() {
        return "text/plain; charset=utf-8";
    }

    public String getLanguage() {
        return language;
    }

    public String getBody() {
        return body;
    }
}
