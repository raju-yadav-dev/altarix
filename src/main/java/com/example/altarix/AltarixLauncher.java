package com.example.altarix;

import javafx.application.Application;

/**
 * Plain Java entry point used for packaged builds.
 * This avoids the JavaFX launcher check that occurs when the main class
 * itself extends Application.
 */
public final class AltarixLauncher {
    private AltarixLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
