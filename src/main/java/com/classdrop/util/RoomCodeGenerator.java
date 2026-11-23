package com.classdrop.util;

import java.security.SecureRandom;
import java.util.Set;

public final class RoomCodeGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> TRIVIAL_CODES = Set.of(
        "000000", "111111", "222222", "333333", "444444",
        "555555", "666666", "777777", "888888", "999999",
        "123456", "654321", "012345", "543210"
    );

    public String generate() {
        while (true) {
            int num = RANDOM.nextInt(1_000_000);
            String code = String.format("%06d", num);
            if (!TRIVIAL_CODES.contains(code)) {
                return code;
            }
        }
    }

    public boolean isValidFormat(String code) {
        if (code == null || code.length() != Constants.ROOM_CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
