package com.example.chatbot.controller;

import com.example.chatbot.update.UpdateService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Builds the About section content so MainController stays focused on window flow.
 */
public final class AboutSectionView {
    private static final Path VERSION_PROPERTIES_PATH = Paths.get("version.properties");

    private AboutSectionView() {
        // Utility class.
    }

    public static VBox createAboutContent(Supplier<CompletableFuture<UpdateService.UpdateCheckResult>> onCheckForUpdates) {
        Label appName = new Label("Altarix");
        appName.getStyleClass().add("about-app-name");

        String version = loadVersion();

        Label versionLabel = new Label("Version: " + version);
        versionLabel.getStyleClass().add("about-version");

        Button updateButton = new Button("Check for Updates");
        updateButton.getStyleClass().add("about-update-button");
        updateButton.setOnAction(event -> {
            if (onCheckForUpdates != null) {
                runUpdateCheck(updateButton, onCheckForUpdates);
            } else {
                updateButton.setText("Update checker unavailable.");
                updateButton.setDisable(false);
            }
        });

        VBox content = new VBox(14, appName, versionLabel, updateButton);
        content.getStyleClass().add("about-dialog-root");
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(28, 36, 28, 36));
        return content;
    }

    private static void runUpdateCheck(
        Button updateButton,
        Supplier<CompletableFuture<UpdateService.UpdateCheckResult>> onCheckForUpdates
    ) {
        updateButton.setText("Checking for updates...");
        updateButton.setDisable(true);

        try {
            CompletableFuture<UpdateService.UpdateCheckResult> check = onCheckForUpdates.get();
            if (check == null) {
                updateButton.setText("Could not check for updates.");
                updateButton.setDisable(false);
                return;
            }
            check.whenComplete((result, error) -> Platform.runLater(() -> {
                if (error != null || result == null || result == UpdateService.UpdateCheckResult.UNAVAILABLE) {
                    updateButton.setText("Could not check for updates.");
                    updateButton.setDisable(false);
                    return;
                }
                if (result == UpdateService.UpdateCheckResult.UPDATE_AVAILABLE) {
                    updateButton.setText("Update available.");
                    updateButton.setDisable(false);
                    return;
                }
                updateButton.setText("\u2713 You are running the latest version.");
                updateButton.setDisable(true);
            }));
        } catch (Exception ex) {
            updateButton.setText("Could not check for updates.");
            updateButton.setDisable(false);
        }
    }

    private static String loadVersion() {
        String version = "Unknown";
        try (InputStream stream = openVersionProperties()) {
            if (stream == null) {
                return version;
            }
            Properties props = new Properties();
            props.load(stream);
            version = props.getProperty("version", version);
        } catch (IOException ignored) {
            // Keep About dialog usable even if version metadata is unavailable.
        }
        return version;
    }

    private static InputStream openVersionProperties() throws IOException {
        if (Files.exists(VERSION_PROPERTIES_PATH)) {
            return Files.newInputStream(VERSION_PROPERTIES_PATH);
        }
        return AboutSectionView.class.getResourceAsStream("/version.properties");
    }
}

