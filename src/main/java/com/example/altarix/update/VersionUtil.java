package com.example.altarix.update;

/**
 * Basic semantic version comparison utility.
 */
public final class VersionUtil {
    private VersionUtil() {
    }

    public static int compare(String left, String right) {
        int[] leftParts = parse(left);
        int[] rightParts = parse(right);
        int length = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < length; i++) {
            int a = i < leftParts.length ? leftParts[i] : 0;
            int b = i < rightParts.length ? rightParts[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    public static boolean isNewer(String current, String candidate) {
        return compare(candidate, current) > 0;
    }

    private static int[] parse(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        String[] parts = value.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].replaceAll("[^0-9]", "");
            if (part.isEmpty()) {
                numbers[i] = 0;
            } else {
                numbers[i] = Integer.parseInt(part);
            }
        }
        return numbers;
    }
}
