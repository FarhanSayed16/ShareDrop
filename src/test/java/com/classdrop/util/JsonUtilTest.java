package com.classdrop.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void parsesStringFields() {
        Map<String, String> map = JsonUtil.parseObject("{\"roomCode\":\"123456\",\"deviceName\":\"Farhan\"}");
        assertEquals("123456", JsonUtil.getString(map, "roomCode"));
        assertEquals("Farhan", JsonUtil.getString(map, "deviceName"));
    }

    @Test
    void parsesEscapedBody() {
        Map<String, String> map = JsonUtil.parseObject("{\"body\":\"line1\\nline2\",\"language\":\"java\"}");
        assertEquals("line1\nline2", JsonUtil.getString(map, "body"));
        assertEquals("java", JsonUtil.getString(map, "language"));
    }

    @Test
    void quoteEscapes() {
        assertEquals("\"a\\\"b\"", JsonUtil.quote("a\"b"));
    }
}
