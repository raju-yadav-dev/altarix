package com.example.chatbot.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Builds the About section content so MainController stays focused on window flow.
 */
public final class AboutSectionView {
    private AboutSectionView() {
        // Utility class.
    }

    public static VBox createAboutContent(Runnable onCheckForUpdates) {
        Label appName = new Label("Cortex");
        appName.getStyleClass().add("about-app-name");

        Label versionLabel = new Label("Version: 1.5.0");
        versionLabel.getStyleClass().add("about-version");

        Button updateButton = new Button("Check for Updates");
        updateButton.getStyleClass().add("about-update-button");
        updateButton.setOnAction(event -> {
            updateButton.setText("\u2713 You are running the latest version.");
            updateButton.setDisable(true);
            if (onCheckForUpdates != null) {
                onCheckForUpdates.run();
            }
        });

        VBox content = new VBox(14, appName, versionLabel, updateButton);
        content.getStyleClass().add("about-dialog-root");
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(28, 36, 28, 36));
        return content;
    }
}
