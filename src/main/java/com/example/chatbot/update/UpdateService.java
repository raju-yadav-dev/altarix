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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    public CompletableFuture<UpdateCheckResult> checkForUpdates(Stage owner) {
        return CompletableFuture
            .supplyAsync(this::fetchLatestUpdate)
            .thenApply(update -> {
                if (update == null || !update.isValid()) {
                    return UpdateCheckResult.UNAVAILABLE;
                }
                if (!VersionUtil.isNewer(CURRENT_VERSION, update.getVersion())) {
                    return UpdateCheckResult.UP_TO_DATE;
                }
                Platform.runLater(() -> showUpdateDialog(owner, update));
                return UpdateCheckResult.UPDATE_AVAILABLE;
            })
            .exceptionally(error -> UpdateCheckResult.UNAVAILABLE);
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
            Path target = createInstallerTarget(update, ext);
            DownloadManager manager = new DownloadManager();
            return manager.downloadAsync(URI.create(resolvedUrl), target, progress)
                .thenApply(path -> {
                    try {
                        validateInstallerFile(path, ext);
                        return path;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
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
        String targetVersion = normalizeVersion(update.getVersion());

        String matchingCurrentVersion = findMatchingDownloadUrl(update, ext, targetVersion, true);
        if (!matchingCurrentVersion.isEmpty()) {
            return matchingCurrentVersion;
        }

        String matchingAnyVersion = findMatchingDownloadUrl(update, ext, targetVersion, false);
        if (!matchingAnyVersion.isEmpty()) {
            return matchingAnyVersion;
        }

        String url = update.getDownloadUrl();
        if (url.isEmpty()) return "";

        if (url.contains("{os}") || url.contains("{ext}")) {
            return url.replace("{os}", osToken).replace("{ext}", ext);
        }
        if (urlLooksLikeInstaller(url) && !urlLooksLikeInstallerType(url, ext)) {
            return "";
        }
        return url;
    }

    private String findMatchingDownloadUrl(UpdateInfo update, String ext, String targetVersion, boolean currentVersionOnly) {
        for (UpdateInfo.UpdateRow row : update.getUpdates()) {
            if (row == null || row.getDownloadUrl().isEmpty()) {
                continue;
            }

            if (currentVersionOnly && !normalizeVersion(row.getVersion()).equals(targetVersion)) {
                continue;
            }
            String type = row.getType();
            String downloadUrl = row.getDownloadUrl();
            if (matchesInstallerType(type, ext) || urlLooksLikeInstallerType(downloadUrl, ext)) {
                return row.getDownloadUrl();
            }
        }
        return "";
    }

    private boolean matchesInstallerType(String type, String ext) {
        String normalized = String.valueOf(type == null ? "" : type).trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(ext)) return true;
        if (ext.equals("deb") && normalized.equals("linux")) return true;
        if (ext.equals("dmg") && (normalized.equals("mac") || normalized.equals("macos"))) return true;
        return ext.equals("exe") && (normalized.equals("windows") || normalized.equals("win"));
    }

    private boolean urlLooksLikeInstaller(String url) {
        return String.valueOf(url).toLowerCase(Locale.ROOT).matches(".*\\.(exe|msi|deb|dmg|pkg|apk|aab)([?#].*)?$");
    }

    private boolean urlLooksLikeInstallerType(String url, String ext) {
        return String.valueOf(url).toLowerCase(Locale.ROOT).matches(".*\\." + ext + "([?#].*)?$");
    }

    private String normalizeVersion(String version) {
        String value = String.valueOf(version == null ? "" : version).trim().toLowerCase(Locale.ROOT);
        return value.startsWith("v") ? value.substring(1) : value;
    }

    private Path createInstallerTarget(UpdateInfo update, String ext) throws Exception {
        String version = normalizeVersion(update == null ? "" : update.getVersion()).replaceAll("[^0-9a-zA-Z._-]", "");
        if (version.isEmpty()) {
            version = "latest";
        }
        Path target = Path.of(System.getProperty("java.io.tmpdir"), "Altarix-" + version + "-installer" + ext);
        Files.deleteIfExists(target);
        return target;
    }

    private void validateInstallerFile(Path installerPath, String ext) throws Exception {
        if (installerPath == null || !Files.isRegularFile(installerPath)) {
            throw new IllegalStateException("Downloaded installer was not created.");
        }
        long size = Files.size(installerPath);
        if (size <= 0) {
            throw new IllegalStateException("Downloaded installer is empty.");
        }
        if (".exe".equalsIgnoreCase(ext) && !hasWindowsExecutableHeader(installerPath)) {
            throw new IllegalStateException("Downloaded file is not a Windows installer.");
        }
        if (".deb".equalsIgnoreCase(ext) && !hasDebianPackageHeader(installerPath)) {
            throw new IllegalStateException("Downloaded file is not a Debian package.");
        }
    }

    private boolean hasWindowsExecutableHeader(Path installerPath) throws Exception {
        byte[] header = readHeader(installerPath, 2);
        return header.length >= 2 && header[0] == 'M' && header[1] == 'Z';
    }

    private boolean hasDebianPackageHeader(Path installerPath) throws Exception {
        byte[] header = readHeader(installerPath, 8);
        if (header.length < 8) {
            return false;
        }
        String magic = new String(header, 0, 8, StandardCharsets.US_ASCII);
        return "!<arch>\n".equals(magic);
    }

    private byte[] readHeader(Path path, int length) throws Exception {
        byte[] buffer = new byte[length];
        try (InputStream input = Files.newInputStream(path)) {
            int read = input.read(buffer);
            if (read <= 0) {
                return new byte[0];
            }
            if (read == length) {
                return buffer;
            }
            byte[] partial = new byte[read];
            System.arraycopy(buffer, 0, partial, 0, read);
            return partial;
        }
    }

    public void launchInstallerAndExit(Path installerPath) throws Exception {
        if (installerPath == null || !Files.isRegularFile(installerPath)) {
            throw new IllegalStateException("Installer file is missing.");
        }

        if (OsDetector.getOsType() == OsDetector.OsType.WINDOWS) {
            new ProcessBuilder("cmd", "/c", "start", "", installerPath.toAbsolutePath().toString()).start();
        } else {
            new ProcessBuilder(installerPath.toAbsolutePath().toString()).start();
        }
        exitApplication();
    }

    public void exitApplication() {
        Platform.exit();
        System.exit(0);
    }

    public enum UpdateCheckResult {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        UNAVAILABLE
    }
}
