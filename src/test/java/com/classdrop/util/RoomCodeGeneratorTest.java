package com.classdrop.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoomCodeGeneratorTest {

    private RoomCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RoomCodeGenerator();
    }

    @Test
    void testGenerateProducesSixDigits() {
        for (int i = 0; i < 50; i++) {
            String code = generator.generate();
            assertNotNull(code);
            assertEquals(6, code.length());
            assertTrue(generator.isValidFormat(code));
        }
    }

    @Test
    void testIsValidFormat() {
        assertTrue(generator.isValidFormat("482917"));
        assertTrue(generator.isValidFormat("000001"));
        assertFalse(generator.isValidFormat("12ab"));
        assertFalse(generator.isValidFormat("12345"));
        assertFalse(generator.isValidFormat("1234567"));
        assertFalse(generator.isValidFormat(""));
        assertFalse(generator.isValidFormat(null));
    }

    @Test
    void testGenerateUniquenessSample() {
        Set<String> sample = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            sample.add(generator.generate());
        }
        // At least 95% unique in a sample of 200 out of 1,000,000
        assertTrue(sample.size() >= 190);
    }
}
