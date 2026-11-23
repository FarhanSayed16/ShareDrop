package com.classdrop.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ShareLinks {
    private ShareLinks() {}

    public static String whatsappUrl(String shareUrl) {
        String text = "Join my ClassDrop room: " + shareUrl;
        return "https://wa.me/?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    public static String emailUrl(String shareUrl) {
        String subject = URLEncoder.encode("ClassDrop Room Link", StandardCharsets.UTF_8);
        String body = URLEncoder.encode("Join my ClassDrop room at: " + shareUrl, StandardCharsets.UTF_8);
        return "mailto:?subject=" + subject + "&body=" + body;
    }
}
