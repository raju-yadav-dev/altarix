package com.example.chatbot.update;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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
    private String activeThemeStylesheet;
    private String activeThemeMode;

    public void setActiveTheme(String themeStylesheet, String themeMode) {
        this.activeThemeStylesheet = themeStylesheet;
        this.activeThemeMode = themeMode;
    }

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
            dialog.initStyle(StageStyle.TRANSPARENT);
            dialog.setResizable(false);

            VBox wrapper = createDialogShell(dialog, root, controller, update);

            Scene scene = new Scene(wrapper, 620, 480);
            scene.setFill(Color.TRANSPARENT);
            addStylesheetIfMissing(scene, getClass().getResource("/css/styles.css").toExternalForm());
            if (owner != null && owner.getScene() != null) {
                for (String stylesheet : owner.getScene().getStylesheets()) {
                    addStylesheetIfMissing(scene, stylesheet);
                }
            }
            addStylesheetIfMissing(scene, activeThemeStylesheet);

            dialog.setScene(scene);
            dialog.setOnShown(event -> centerOnOwner(dialog, owner));

            controller.bind(update, dialog, this);

            // Probe download URL for Content-Length (if available) to show size
            CompletableFuture.runAsync(() -> {
                try {
                    String resolved = resolveDownloadUrl(update);
                    if (resolved != null && !resolved.isEmpty()) {
                        try {
                            HttpRequest head = HttpRequest.newBuilder(URI.create(resolved)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
                            HttpResponse<Void> headResp = client.send(head, HttpResponse.BodyHandlers.discarding());
                            String len = headResp.headers().firstValue("Content-Length").orElse("");
                            if (!len.isEmpty()) {
                                long bytes = Long.parseLong(len);
                                controller.setDownloadSize(bytes);
                            }
                        } catch (Exception ignored) {
                            // Try a lightweight GET probe if HEAD not supported
                            try {
                                HttpRequest get = HttpRequest.newBuilder(URI.create(resolved)).method("GET", HttpRequest.BodyPublishers.noBody()).header("Range", "bytes=0-0").build();
                                HttpResponse<Void> getResp = client.send(get, HttpResponse.BodyHandlers.discarding());
                                String len2 = getResp.headers().firstValue("Content-Range").orElse("");
                                if (len2.contains("/")) {
                                    String total = len2.substring(len2.indexOf('/') + 1);
                                    if (!total.isEmpty() && !total.equals("*")) {
                                        long bytes = Long.parseLong(total);
                                        controller.setDownloadSize(bytes);
                                    }
                                }
                            } catch (Exception __) {
                                // ignore
                            }
                        }
                    }
                } catch (Exception ex) {
                    // ignore size probe failures
                }
            });

            dialog.show();
        } catch (Exception ex) {
            // Dialog errors should not block the app.
        }
    }

    private VBox createDialogShell(Stage dialog, Parent content, UpdateDialogController controller, UpdateInfo update) {
        Label titleLabel = new Label("Update Available");
        titleLabel.getStyleClass().add("update-dialog-window-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("\u2715");
        closeButton.getStyleClass().add("settings-close-button");
        closeButton.getStyleClass().add("update-dialog-close-button");
        closeButton.setFocusTraversable(false);
        closeButton.setDisable(update != null && update.isMandatory());
        closeButton.setOnAction(event -> controller.requestClose());

        HBox titleBar = new HBox(titleLabel, spacer, closeButton);
        titleBar.getStyleClass().add("update-dialog-title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        enableDialogDrag(dialog, titleBar);

        VBox wrapper = new VBox(titleBar, content);
        wrapper.getStyleClass().add("window-root");
        wrapper.getStyleClass().add("update-dialog-root");
        wrapper.getStyleClass().add(resolveThemeMode(dialog.getOwner() instanceof Stage owner ? owner : null));
        VBox.setVgrow(content, Priority.ALWAYS);

        Rectangle clip = new Rectangle(620, 480);
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(wrapper.widthProperty());
        clip.heightProperty().bind(wrapper.heightProperty());
        wrapper.setClip(clip);

        return wrapper;
    }

    private void enableDialogDrag(Stage dialog, HBox titleBar) {
        final double[] dragOffset = new double[2];
        titleBar.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            dragOffset[0] = event.getSceneX();
            dragOffset[1] = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }
            dialog.setX(event.getScreenX() - dragOffset[0]);
            dialog.setY(event.getScreenY() - dragOffset[1]);
        });
    }

    private void centerOnOwner(Stage dialog, Stage owner) {
        if (owner == null) {
            return;
        }
        dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2.0);
        dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2.0);
    }

    private void addStylesheetIfMissing(Scene scene, String stylesheet) {
        if (stylesheet == null || stylesheet.isEmpty() || scene.getStylesheets().contains(stylesheet)) {
            return;
        }
        scene.getStylesheets().add(stylesheet);
    }

    private String resolveThemeMode(Stage owner) {
        if (activeThemeMode != null && !activeThemeMode.isEmpty()) {
            return activeThemeMode;
        }
        String ownerMode = extractThemeMode(owner != null && owner.getScene() != null ? owner.getScene().getRoot() : null);
        return ownerMode.isEmpty() ? "theme-dark" : ownerMode;
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

    private String extractThemeMode(javafx.scene.Node node) {
        if (node == null) return "";
        if (node instanceof javafx.scene.layout.Region region) {
            for (String styleClass : region.getStyleClass()) {
                if ("theme-dark".equals(styleClass) || "theme-light".equals(styleClass)) {
                    return styleClass;
                }
            }
        }
        return "";
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
