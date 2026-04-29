package com.example.chatbot;

import com.altarix.util.IconResources;
import com.example.chatbot.controller.MainController;
import com.example.chatbot.update.UpdateService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainApp extends Application {
    // ================= WINDOW RESIZE CONSTANTS =================
    private static final double RESIZE_BORDER = 12;
    private static final double CORNER_HIT_SIZE = 24;

    // ================= APPLICATION STARTUP =================
    @Override
    public void start(Stage primaryStage) throws Exception {
        // ---- Load Main Layout + Controller ----
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        MainController controller = loader.getController();

        // ---- Configure Scene + Styles ----
        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        scene.setFill(Color.TRANSPARENT);

        // ---- Configure Stage ----
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setTitle("Altarix");
        primaryStage.setScene(scene);
        IconResources.addStageIcons(primaryStage, getClass());
        primaryStage.setResizable(true);

        // ---- Fit Initial Window Into Current Screen (60% of screen, centered) ----
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        
        double startWidth = visualBounds.getWidth() * 0.6;
        double startHeight = visualBounds.getHeight() * 0.6;
        primaryStage.setWidth(startWidth);
        primaryStage.setHeight(startHeight);
        primaryStage.setX(visualBounds.getMinX() + (visualBounds.getWidth() - startWidth) / 2);
        primaryStage.setY(visualBounds.getMinY() + (visualBounds.getHeight() - startHeight) / 2);
        
        double maxStartupWidth = Math.max(640, visualBounds.getWidth());
        double maxStartupHeight = Math.max(480, visualBounds.getHeight());
        double minWidth = Math.min(960, maxStartupWidth);
        double minHeight = Math.min(620, maxStartupHeight);

        primaryStage.setMinWidth(minWidth);
        primaryStage.setMinHeight(minHeight);

        // ---- Create UpdateService (will be configured by controller) ----
        UpdateService updateService = new UpdateService();

        // ---- Connect Controller + Enable Border Resize ----
        controller.setStage(primaryStage);
        controller.setUpdateService(updateService);
        enableResize(primaryStage, scene);

        // ---- Show Window ----
        primaryStage.show();
    }

    // ================= CUSTOM WINDOW RESIZE =================
    private void enableResize(Stage stage, Scene scene) {
        // ---- Resize Edge Tracking State ----
        final boolean[] resizeTop = {false};
        final boolean[] resizeBottom = {false};
        final boolean[] resizeLeft = {false};
        final boolean[] resizeRight = {false};
        final double[] pressScreenX = {0};
        final double[] pressScreenY = {0};
        final double[] pressStageX = {0};
        final double[] pressStageY = {0};
        final double[] pressWidth = {0};
        final double[] pressHeight = {0};

        // ---- Update Cursor When Hovering Resize Borders ----
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (stage.isMaximized()) {
                scene.setCursor(Cursor.DEFAULT);
                return;
            }
            boolean left = event.getSceneX() <= RESIZE_BORDER;
            boolean right = event.getSceneX() >= scene.getWidth() - RESIZE_BORDER;
            boolean top = event.getSceneY() <= RESIZE_BORDER;
            boolean bottom = event.getSceneY() >= scene.getHeight() - RESIZE_BORDER;

            // Make corner grabbing easier than plain edges.
            if (event.getSceneX() <= CORNER_HIT_SIZE && event.getSceneY() <= CORNER_HIT_SIZE) {
                left = true;
                top = true;
            } else if (event.getSceneX() >= scene.getWidth() - CORNER_HIT_SIZE && event.getSceneY() <= CORNER_HIT_SIZE) {
                right = true;
                top = true;
            } else if (event.getSceneX() <= CORNER_HIT_SIZE && event.getSceneY() >= scene.getHeight() - CORNER_HIT_SIZE) {
                left = true;
                bottom = true;
            } else if (event.getSceneX() >= scene.getWidth() - CORNER_HIT_SIZE
                    && event.getSceneY() >= scene.getHeight() - CORNER_HIT_SIZE) {
                right = true;
                bottom = true;
            }
            scene.setCursor(getCursor(top, right, bottom, left));
        });

        // ---- Capture Initial Pointer + Stage Bounds ----
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (stage.isMaximized() || event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            boolean left = event.getSceneX() <= RESIZE_BORDER;
            boolean right = event.getSceneX() >= scene.getWidth() - RESIZE_BORDER;
            boolean top = event.getSceneY() <= RESIZE_BORDER;
            boolean bottom = event.getSceneY() >= scene.getHeight() - RESIZE_BORDER;

            // Use the same expanded corner area for press-start detection.
            if (event.getSceneX() <= CORNER_HIT_SIZE && event.getSceneY() <= CORNER_HIT_SIZE) {
                left = true;
                top = true;
            } else if (event.getSceneX() >= scene.getWidth() - CORNER_HIT_SIZE && event.getSceneY() <= CORNER_HIT_SIZE) {
                right = true;
                top = true;
            } else if (event.getSceneX() <= CORNER_HIT_SIZE && event.getSceneY() >= scene.getHeight() - CORNER_HIT_SIZE) {
                left = true;
                bottom = true;
            } else if (event.getSceneX() >= scene.getWidth() - CORNER_HIT_SIZE
                    && event.getSceneY() >= scene.getHeight() - CORNER_HIT_SIZE) {
                right = true;
                bottom = true;
            }

            resizeTop[0] = top;
            resizeBottom[0] = bottom;
            resizeLeft[0] = left;
            resizeRight[0] = right;
            pressScreenX[0] = event.getScreenX();
            pressScreenY[0] = event.getScreenY();
            pressStageX[0] = stage.getX();
            pressStageY[0] = stage.getY();
            pressWidth[0] = stage.getWidth();
            pressHeight[0] = stage.getHeight();
            if (resizeTop[0] || resizeBottom[0] || resizeLeft[0] || resizeRight[0]) {
                event.consume();
            }
        });

        // ---- Apply Resize While Dragging ----
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (stage.isMaximized() || !event.isPrimaryButtonDown()) {
                return;
            }
            if (!resizeTop[0] && !resizeBottom[0] && !resizeLeft[0] && !resizeRight[0]) {
                return;
            }

            Rectangle2D bounds = getVisualBoundsForStage(stage);
            double minWidth = stage.getMinWidth();
            double minHeight = stage.getMinHeight();
            double dx = event.getScreenX() - pressScreenX[0];
            double dy = event.getScreenY() - pressScreenY[0];
            double newX = pressStageX[0];
            double newY = pressStageY[0];
            double newWidth = pressWidth[0];
            double newHeight = pressHeight[0];

            if (resizeRight[0]) {
                double maxWidth = Math.max(minWidth, bounds.getMaxX() - pressStageX[0]);
                newWidth = clamp(pressWidth[0] + dx, minWidth, maxWidth);
            }
            if (resizeBottom[0]) {
                double maxHeight = Math.max(minHeight, bounds.getMaxY() - pressStageY[0]);
                newHeight = clamp(pressHeight[0] + dy, minHeight, maxHeight);
            }
            if (resizeLeft[0]) {
                double rightEdge = pressStageX[0] + pressWidth[0];
                double minX = bounds.getMinX();
                double maxX = rightEdge - minWidth;
                double desiredX = pressStageX[0] + dx;
                newX = clamp(desiredX, minX, maxX);
                newWidth = rightEdge - newX;
            }
            if (resizeTop[0]) {
                double bottomEdge = pressStageY[0] + pressHeight[0];
                double minY = bounds.getMinY();
                double maxY = bottomEdge - minHeight;
                double desiredY = pressStageY[0] + dy;
                newY = clamp(desiredY, minY, maxY);
                newHeight = bottomEdge - newY;
            }
            stage.setX(newX);
            stage.setY(newY);
            stage.setWidth(newWidth);
            stage.setHeight(newHeight);
            event.consume();
        });

        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            boolean wasResizing = resizeTop[0] || resizeBottom[0] || resizeLeft[0] || resizeRight[0];
            resizeTop[0] = false;
            resizeBottom[0] = false;
            resizeLeft[0] = false;
            resizeRight[0] = false;
            if (wasResizing) {
                event.consume();
            }
        });
    }

    private Rectangle2D getVisualBoundsForStage(Stage stage) {
        return Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    // ================= CURSOR MAPPING =================
    private Cursor getCursor(boolean top, boolean right, boolean bottom, boolean left) {
        if (top && left) {
            return Cursor.NW_RESIZE;
        }
        if (top && right) {
            return Cursor.NE_RESIZE;
        }
        if (bottom && left) {
            return Cursor.SW_RESIZE;
        }
        if (bottom && right) {
            return Cursor.SE_RESIZE;
        }
        if (top) {
            return Cursor.N_RESIZE;
        }
        if (bottom) {
            return Cursor.S_RESIZE;
        }
        if (left) {
            return Cursor.W_RESIZE;
        }
        if (right) {
            return Cursor.E_RESIZE;
        }
        return Cursor.DEFAULT;
    }

    // ================= ENTRY POINT =================
    public static void main(String[] args) {
        launch(args);
    }
}

