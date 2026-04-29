package com.example.chatbot.update;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Simple JavaFX controller for the update dialog.
 */
public class UpdateDialogController {
    @FXML
    private Label currentVersionLabel;
    @FXML
    private Label newVersionLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label sizeLabel;
    @FXML
    private Label mandatoryLabel;
    @FXML
    private TextArea releaseNotesArea;
    @FXML
    private ProgressBar downloadProgress;
    @FXML
    private Button updateButton;
    @FXML
    private Button laterButton;

    private UpdateInfo updateInfo;
    private Stage dialogStage;
    private UpdateService updateService;
    private boolean downloadStarted;

    public void bind(UpdateInfo info, Stage dialog, UpdateService service) {
        this.updateInfo = info;
        this.dialogStage = dialog;
        this.updateService = service;

        currentVersionLabel.setText("Current version: " + UpdateService.CURRENT_VERSION);
        newVersionLabel.setText("New version: " + info.getVersion());
        String notes = info.getReleaseNotes();
        releaseNotesArea.setText(notes.isEmpty() ? "No release notes provided." : notes);
        statusLabel.setText("Ready to download.");
        downloadProgress.setProgress(0);
        sizeLabel.setText("Size: --");

        if (info.isMandatory()) {
            mandatoryLabel.setText("This update is required to continue.");
            mandatoryLabel.setManaged(true);
            mandatoryLabel.setVisible(true);
            laterButton.setDisable(true);
            if (dialogStage != null) {
                dialogStage.setOnCloseRequest(event -> event.consume());
            }
            if (!downloadStarted) {
                downloadStarted = true;
                Platform.runLater(this::onUpdateNow);
            }
        } else {
            mandatoryLabel.setText("You can install this update later.");
            mandatoryLabel.setManaged(false);
            mandatoryLabel.setVisible(false);
        }
    }

    @FXML
    private void onUpdateNow() {
        if (updateInfo == null || updateService == null) {
            statusLabel.setText("Update details are missing.");
            return;
        }

        updateButton.setDisable(true);
        laterButton.setDisable(true);
        statusLabel.setText("Downloading update...");

        updateService
            .downloadUpdate(updateInfo, this::updateProgress)
            .thenAccept(path -> Platform.runLater(() -> finishUpdate(path)))
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    statusLabel.setText("Download failed. Please try again.");
                    updateButton.setDisable(false);
                    laterButton.setDisable(false);
                });
                return null;
            });
    }

    @FXML
    private void onLater() {
        if (updateInfo != null && updateInfo.isMandatory()) {
            statusLabel.setText("This update is mandatory.");
            return;
        }
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    public void requestClose() {
        onLater();
    }

    private void updateProgress(long downloaded, long total) {
        Platform.runLater(() -> {
            if (total > 0) {
                double ratio = (double) downloaded / (double) total;
                downloadProgress.setProgress(Math.min(1.0, ratio));
                statusLabel.setText("Downloading... " + Math.round(ratio * 100) + "%");
            } else {
                downloadProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                statusLabel.setText("Downloading...");
            }
        });
    }

    public void setDownloadSize(long bytes) {
        Platform.runLater(() -> {
            if (bytes <= 0) {
                sizeLabel.setText("Size: Unknown");
                return;
            }
            String human;
            double value = bytes;
            String unit = "B";
            if (value >= 1024) { value /= 1024; unit = "KB"; }
            if (value >= 1024) { value /= 1024; unit = "MB"; }
            if (value >= 1024) { value /= 1024; unit = "GB"; }
            human = String.format("%s %s", value >= 10 ? String.format("%.0f", value) : String.format("%.1f", value), unit);
            sizeLabel.setText("Size: " + human);
        });
    }

    private void finishUpdate(Path installerPath) {
        statusLabel.setText("Launching installer...");
        try {
            updateService.launchInstallerAndExit(installerPath);
        } catch (Exception ex) {
            statusLabel.setText("Unable to launch installer.");
            updateButton.setDisable(false);
            laterButton.setDisable(false);
        }
    }
}
