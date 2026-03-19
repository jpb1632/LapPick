package lappick.common.util;

public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    public static String maskCardNumberForStorage(String rawValue) {
        String digits = extractDigits(rawValue);
        if (digits.length() < 12 || digits.length() > 19) {
            throw new IllegalArgumentException("카드번호 형식이 올바르지 않습니다.");
        }
        return maskCardDigits(digits);
    }

    public static String maskCardNumberForDisplay(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }
        if (storedValue.contains("*")) {
            return storedValue;
        }

        String digits = extractDigits(storedValue);
        if (digits.length() < 4) {
            return "****";
        }
        return maskCardDigits(digits);
    }

    public static String maskJuminForStorage(String rawValue) {
        String digits = extractDigits(rawValue);
        if (digits.length() != 13) {
            throw new IllegalArgumentException("주민번호 형식이 올바르지 않습니다.");
        }
        return digits.substring(0, 6) + "-" + digits.charAt(6) + "******";
    }

    public static String maskJuminForDisplay(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }
        if (storedValue.contains("*")) {
            return storedValue;
        }

        String digits = extractDigits(storedValue);
        if (digits.length() != 13) {
            return storedValue;
        }
        return digits.substring(0, 6) + "-" + digits.charAt(6) + "******";
    }

    private static String extractDigits(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }

    private static String maskCardDigits(String digits) {
        int visibleLength = Math.min(4, digits.length());
        String lastDigits = digits.substring(digits.length() - visibleLength);
        String maskedPrefix = "*".repeat(Math.max(0, digits.length() - visibleLength));
        return maskedPrefix + lastDigits;
    }
}
