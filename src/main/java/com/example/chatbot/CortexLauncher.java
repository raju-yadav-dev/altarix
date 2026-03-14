package com.example.chatbot;

import javafx.application.Application;

/**
 * Plain Java entry point used for packaged builds.
 * This avoids the JavaFX launcher check that occurs when the main class
 * itself extends Application.
 */
public final class CortexLauncher {
    private CortexLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
