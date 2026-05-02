package com.example.chatbot.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads the current app version from the bundled version.properties file.
 */
public final class VersionProperties {
    private static final String RESOURCE_PATH = "/version.properties";
    private static final Path LOCAL_VERSION_PATH = Paths.get("version.properties");

    private VersionProperties() {
    }

    public static String getCurrentVersion() {
        String version = "0.0.0";
        Properties props = new Properties();
        try (InputStream input = openVersionStream()) {
            if (input == null) {
                return version;
            }
            props.load(input);
            version = props.getProperty("version", version).trim();
        } catch (Exception ignored) {
            // Ignore and fall back to default.
        }
        return version;
    }

    private static InputStream openVersionStream() throws IOException {
        if (Files.exists(LOCAL_VERSION_PATH)) {
            return Files.newInputStream(LOCAL_VERSION_PATH);
        }
        return VersionProperties.class.getResourceAsStream(RESOURCE_PATH);
    }
}
