package com.cortex.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;

public final class IconResources {
    private static final List<String> STAGE_ICON_PATHS = List.of(
        "/icon/Cortex 32x32.png",
        "/icon/Cortex 64x64.png",
        "/icon/Cortex 128x128.png",
        "/icon/Cortex 256x256.png",
        "/icon/Cortex 512x512.png",
        "/icon/Cortex.png"
    );

    private IconResources() {
    }

    public static void addStageIcons(Stage stage, Class<?> resourceAnchor) {
        if (stage == null || resourceAnchor == null) {
            return;
        }

        for (String path : STAGE_ICON_PATHS) {
            URL resource = resourceAnchor.getResource(path);
            if (resource != null) {
                stage.getIcons().add(new Image(resource.toExternalForm()));
            }
        }
    }

    public static Image loadBestFit(Class<?> resourceAnchor, int preferredSize) {
        if (resourceAnchor == null) {
            return null;
        }

        List<String> candidates = preferredSize <= 32
            ? List.of(
                "/icon/Cortex 32x32.png",
                "/icon/Cortex 64x64.png",
                "/icon/Cortex 128x128.png",
                "/icon/Cortex.png"
            )
            : List.of(
                "/icon/Cortex 64x64.png",
                "/icon/Cortex 128x128.png",
                "/icon/Cortex 256x256.png",
                "/icon/Cortex.png"
            );

        for (String path : candidates) {
            URL resource = resourceAnchor.getResource(path);
            if (resource != null) {
                return new Image(resource.toExternalForm());
            }
        }

        URL fallback = resourceAnchor.getResource("/icon/Cortex.ico");
        return fallback != null ? new Image(fallback.toExternalForm()) : null;
    }
}
