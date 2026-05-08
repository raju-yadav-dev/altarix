package com.aicodehelper.app;

import com.aicodehelper.ui.MainLayout;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX application entry point.
 */
public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        new MainLayout().init(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
