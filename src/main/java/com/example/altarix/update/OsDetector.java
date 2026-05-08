package com.example.altarix.update;

/**
 * Maps the current operating system to update file naming.
 */
public final class OsDetector {
    public enum OsType {
        WINDOWS,
        MAC,
        LINUX,
        OTHER
    }

    private OsDetector() {
    }

    public static OsType getOsType() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("win")) return OsType.WINDOWS;
        if (name.contains("mac")) return OsType.MAC;
        if (name.contains("nix") || name.contains("nux") || name.contains("aix")) return OsType.LINUX;
        return OsType.OTHER;
    }

    public static String getOsToken() {
        return switch (getOsType()) {
            case WINDOWS -> "windows";
            case MAC -> "macos";
            case LINUX -> "linux";
            default -> "unknown";
        };
    }

    public static String getInstallerExtension() {
        return switch (getOsType()) {
            case WINDOWS -> "exe";
            case MAC -> "dmg";
            case LINUX -> "deb";
            default -> "bin";
        };
    }
}
