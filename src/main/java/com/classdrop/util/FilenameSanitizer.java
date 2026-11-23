package com.classdrop.util;

import com.classdrop.exception.TransferFailedException;

public final class FilenameSanitizer {

    public String sanitize(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "unnamed_file";
        }

        // Replace backslashes with slashes
        String name = originalName.replace('\\', '/');

        // Extract last component if path was provided
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Strip directory traversal sequences
        name = name.replace("..", "");

        // Remove control characters (ASCII 0-31 and 127)
        StringBuilder clean = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= 32 && c != 127) {
                clean.append(c);
            }
        }
        name = clean.toString().trim();

        // Check if empty or only dots
        if (name.isEmpty() || name.matches("^\\.++$")) {
            return "unnamed_file";
        }

        // Cap length at 255 chars preserving extension if possible
        if (name.length() > 255) {
            int extIdx = name.lastIndexOf('.');
            if (extIdx > 200) {
                String ext = name.substring(extIdx);
                name = name.substring(0, 255 - ext.length()) + ext;
            } else {
                name = name.substring(0, 255);
            }
        }

        return name;
    }

    public void rejectIfPathTraversal(String originalName) throws TransferFailedException {
        if (originalName == null) return;
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new TransferFailedException("Potential path traversal detected in filename: " + originalName);
        }
    }
}
