package com.example.chatbot.update;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates the update check, UI, download, and installer launch.
 */
public final class UpdateService {
    // Current app version for comparison with the API.
    public static final String CURRENT_VERSION = VersionProperties.getCurrentVersion();
    private static final String UPDATE_API_URL = "https://altarix.vercel.app/api/update";

    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final Gson gson = new Gson();

    public void checkForUpdates(Stage owner) {
        CompletableFuture
            .supplyAsync(this::fetchLatestUpdate)
            .thenAccept(update -> {
                if (update == null || !update.isValid()) return;
                if (!VersionUtil.isNewer(CURRENT_VERSION, update.getVersion())) return;
                Platform.runLater(() -> showUpdateDialog(owner, update));
            })
            .exceptionally(error -> {
                // Keep startup smooth even if update check fails.
                return null;
            });
    }

    private UpdateInfo fetchLatestUpdate() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(UPDATE_API_URL)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            UpdateInfo info = gson.fromJson(response.body(), UpdateInfo.class);
            return info != null && info.isValid() ? info : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void showUpdateDialog(Stage owner, UpdateInfo update) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/update-dialog.fxml"));
            Parent root = loader.load();
            UpdateDialogController controller = loader.getController();

            Stage dialog = new Stage();
            dialog.setTitle("Update Available");
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setResizable(false);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            dialog.setScene(scene);

            controller.bind(update, dialog, this);
            dialog.show();
        } catch (Exception ex) {
            // Dialog errors should not block the app.
        }
    }

    public CompletableFuture<Path> downloadUpdate(UpdateInfo update, java.util.function.BiConsumer<Long, Long> progress) {
        try {
            String resolvedUrl = resolveDownloadUrl(update);
            if (resolvedUrl.isEmpty()) {
                throw new IllegalStateException("Update URL is missing.");
            }
            String ext = "." + OsDetector.getInstallerExtension();
            Path target = Files.createTempFile("altarix-update-", ext);
            DownloadManager manager = new DownloadManager();
            return manager.downloadAsync(URI.create(resolvedUrl), target, progress);
        } catch (Exception ex) {
            CompletableFuture<Path> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    private String resolveDownloadUrl(UpdateInfo update) {
        if (update == null) return "";
        String ext = OsDetector.getInstallerExtension().toLowerCase(Locale.ROOT);
        String osToken = OsDetector.getOsToken().toLowerCase(Locale.ROOT);

        for (UpdateInfo.UpdateRow row : update.getUpdates()) {
            if (row == null || row.getDownloadUrl().isEmpty()) {
                continue;
            }

            String type = row.getType();
            if (type.equals(ext)) {
                return row.getDownloadUrl();
            }
            if (ext.equals("deb") && type.equals("linux")) {
                return row.getDownloadUrl();
            }
            if (ext.equals("dmg") && (type.equals("mac") || type.equals("macos"))) {
                return row.getDownloadUrl();
            }
            if (ext.equals("exe") && type.equals("windows")) {
                return row.getDownloadUrl();
            }
        }

        String url = update.getDownloadUrl();
        if (url.isEmpty()) return "";

        if (url.contains("{os}") || url.contains("{ext}")) {
            return url.replace("{os}", osToken).replace("{ext}", ext);
        }
        return url;
    }

    public void launchInstallerAndExit(Path installerPath) throws Exception {
        new ProcessBuilder(installerPath.toString()).start();
        Platform.exit();
        System.exit(0);
    }
}
