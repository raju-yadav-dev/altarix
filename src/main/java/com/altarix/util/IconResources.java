package com.altarix.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;

public final class IconResources {
    private static final List<String> STAGE_ICON_PATHS = List.of(
        "icon/Altarix-16.png",
        "icon/Altarix-32.png",
        "icon/Altarix-64.png",
        "icon/Altarix-128.png",
        "icon/Altarix-256.png",
        "icon/Altarix-512.png"
    );

    private IconResources() {
    }

    public static void addStageIcons(Stage stage, Class<?> resourceAnchor) {
        if (stage == null) {
            return;
        }

        for (String path : STAGE_ICON_PATHS) {
            URL resource = IconResources.class.getClassLoader().getResource(path);
            if (resource != null) {
                stage.getIcons().add(new Image(resource.toExternalForm()));
            }
        }
    }

    public static Image loadBestFit(int preferredSize) {
        List<String> candidates = preferredSize <= 32
            ? List.of(
                "icon/Altarix-16.png",
                "icon/Altarix-32.png",
                "icon/Altarix-64.png",
                "icon/Altarix-128.png"
            )
            : List.of(
                "icon/Altarix-64.png",
                "icon/Altarix-128.png",
                "icon/Altarix-256.png",
                "icon/Altarix-512.png"
            );

        for (String path : candidates) {
            URL resource = IconResources.class.getClassLoader().getResource(path);
            if (resource != null) {
                return new Image(resource.toExternalForm());
            }
        }

        URL fallback = IconResources.class.getClassLoader().getResource("icon/Altarix-256.png");
        return fallback != null ? new Image(fallback.toExternalForm()) : null;
    }
}

