package com.example.chatbot.visualizer;

import com.cortex.util.IconResources;
import com.example.chatbot.service.SettingsManager;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeVisualizationDialog {
    private static final Pattern DECLARATION_PATTERN = Pattern.compile(
            "^(?:final\\s+)?(?:[A-Za-z_$][\\w$<>\\[\\],?]*\\s+)+([A-Za-z_$][\\w$]*)\\s*(?:=\\s*(.+?))?;?$");
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile(
            "^([A-Za-z_$][\\w$]*(?:\\[[^\\]]+])?)\\s*=\\s*(.+?);?$");
    private static final Pattern CALL_PATTERN = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)");
        private static final Pattern OBJECT_TYPE_PATTERN = Pattern.compile("new\\s+([A-Za-z_$][\\w$]*)");
    private static final List<String> NON_CALL_KEYWORDS = List.of(
            "if", "for", "while", "switch", "catch", "new", "return", "throw", "assert"
    );
        private static final String FONT_SIZE_KEY = "codeVisualizer.fontSize";
        private static final String TEXT_COLOR_KEY = "codeVisualizer.textColorMode";

    private final Stage stage = new Stage();
    private final String language;
    private final String originalCode;
    private final Scene ownerScene;
    private final Node ownerRootNode;
    private final List<String> inheritedThemeClasses;
    private VBox rootContainer;
    private ListChangeListener<String> ownerThemeListener;

    private final TextArea codeEditor = new TextArea();
    private final Label stepIndicator = new Label("Step 0/0");
    private final Label currentLineLabel = new Label("Line: -");
    private final FlowPane variableFlow = new FlowPane();
    private final AnchorPane structurePane = new AnchorPane();
    private final VBox callStackBox = new VBox(8);
    private final Button runButton = new Button("Run");
    private final Label runActivityLabel = new Label("...");
    private final Button stepForwardButton = new Button("Step Forward");
    private final Button stepBackButton = new Button("Step Back");
    private final Button resetButton = new Button("Reset to Original Code");
    private final Button exportPngButton = new Button("Export PNG");
    private final ComboBox<String> speedPresetCombo = new ComboBox<>();
    private final ComboBox<String> scenarioPresetCombo = new ComboBox<>();
    private final Slider stackTimelineSlider = new Slider(0, 0, 0);
    private final Label stackTimelineLabel = new Label("Stack Step: 0/0");
    private final ToggleButton stackFocusToggle = new ToggleButton("Stack Focus");
    private final ToggleButton compareModeToggle = new ToggleButton("Compare Steps");
    private final Slider compareBaseSlider = new Slider(0, 0, 0);
    private final Label compareBaseLabel = new Label("Base Step: 1");
    private final Button swapCompareButton = new Button("Swap A/B");
    private final Map<String, Integer> previousCapacityByStructure = new HashMap<>();
    private final Map<String, Integer> previousLinkedListNodeCount = new HashMap<>();
    private final List<TranslateTransition> activeFloatingTransitions = new ArrayList<>();
    private final SettingsManager settingsManager = SettingsManager.getInstance();

    private FlowPane variablePopupFlow;
    private AnchorPane structurePopupPane;
    private VBox callStackPopupBox;
    private TextArea popupCodeEditor;
    private Stage editorPopupStage;
    private Stage variablePopupStage;
    private Stage structurePopupStage;
    private Stage stackPopupStage;

    private VariableShape variableShape = VariableShape.BUBBLE;
    private volatile boolean playbackRunning;
    private Thread playbackThread;
    private List<String> previousFrameNames = List.of();

    private List<ExecutionSnapshot> timeline = List.of();
    private int timelineIndex = -1;
    private int previousStackDepth = 0;
    private boolean recursionDetected;
    private long playbackIntervalMs = 520;
    private double floatingSpeedScale = 1.0;
    private boolean syncingStackSlider;
    private boolean syncingCompareSlider;
    private boolean stackFocusMode;
    private boolean compareModeEnabled;
    private Timeline runActivityTimeline;

    private enum VariableShape {
        BUBBLE,
        RECTANGLE
    }

    public static void show(Window owner, Scene ownerScene, Node ownerRootNode, String language, String code,
                            List<String> inheritedThemeClasses) {
        CodeVisualizationDialog dialog = new CodeVisualizationDialog(
                owner,
                ownerScene,
                ownerRootNode,
                language,
                code,
                inheritedThemeClasses
        );
        dialog.stage.showAndWait();
    }

    private CodeVisualizationDialog(Window owner,
                                    Scene ownerScene,
                                    Node ownerRootNode,
                                    String language,
                                    String code,
                                    List<String> inheritedThemeClasses) {
        this.language = (language == null || language.isBlank()) ? "text" : language;
        this.originalCode = code == null ? "" : code;
        this.variableShape = "Rectangle".equalsIgnoreCase(
            settingsManager.getString("codeVisualizer.defaultVariableShape", "Bubble"))
            ? VariableShape.RECTANGLE
            : VariableShape.BUBBLE;
        this.stackFocusMode = settingsManager.getBoolean("codeVisualizer.stackFocusDefault", false);
        applyPlaybackSpeed(settingsManager.getString("codeVisualizer.playbackSpeed", "Normal"));
        this.ownerScene = ownerScene;
        this.ownerRootNode = ownerRootNode;
        this.inheritedThemeClasses = inheritedThemeClasses == null ? List.of() : new ArrayList<>(inheritedThemeClasses);

        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        IconResources.addStageIcons(stage, getClass());

        Scene scene = new Scene(buildRoot(), 1120, 760);
        scene.setFill(Color.TRANSPARENT);
        inheritParentStylesheets(scene);
        stage.setScene(scene);
        attachLiveThemeSync();
        applyVisualizerTextPreferences();
        attachSettingsLivePreview();

        stage.setOnCloseRequest(event -> stopPlayback());
        stage.setOnHidden(event -> {
            detachLiveThemeSync();
            detachSettingsLivePreview();
            shutdownExecutor();
        });

        rebuildTimelineFromEditor();
    }

    private void inheritParentStylesheets(Scene targetScene) {
        if (ownerScene != null) {
            targetScene.getStylesheets().setAll(ownerScene.getStylesheets());
        }

        String visualizerStylesheet = Objects.requireNonNull(getClass().getResource("/css/code-visualizer.css")).toExternalForm();
        if (!targetScene.getStylesheets().contains(visualizerStylesheet)) {
            targetScene.getStylesheets().add(visualizerStylesheet);
        }

        String baseStylesheet = Objects.requireNonNull(getClass().getResource("/css/styles.css")).toExternalForm();
        if (!targetScene.getStylesheets().contains(baseStylesheet)) {
            targetScene.getStylesheets().add(baseStylesheet);
        }
    }

    private void attachLiveThemeSync() {
        syncThemeClasses();
        if (ownerRootNode == null) {
            return;
        }

        ownerThemeListener = change -> syncThemeClasses();
        ownerRootNode.getStyleClass().addListener(ownerThemeListener);
    }

    private void detachLiveThemeSync() {
        if (ownerRootNode != null && ownerThemeListener != null) {
            ownerRootNode.getStyleClass().removeListener(ownerThemeListener);
        }
        ownerThemeListener = null;
    }

    private void attachSettingsLivePreview() {
        settingsManager.addListener(FONT_SIZE_KEY, value -> Platform.runLater(this::applyVisualizerTextPreferences));
        settingsManager.addListener(TEXT_COLOR_KEY, value -> Platform.runLater(this::applyVisualizerTextPreferences));
    }

    private void detachSettingsLivePreview() {
        settingsManager.removeListener(FONT_SIZE_KEY);
        settingsManager.removeListener(TEXT_COLOR_KEY);
    }

    private void syncThemeClasses() {
        if (rootContainer == null) {
            return;
        }

        rootContainer.getStyleClass().removeIf(styleClass -> styleClass.startsWith("theme-"));
        if (!rootContainer.getStyleClass().contains("window-root")) {
            rootContainer.getStyleClass().add("window-root");
        }

        if (ownerRootNode != null) {
            for (String styleClass : ownerRootNode.getStyleClass()) {
                if (styleClass != null && styleClass.startsWith("theme-")
                        && !rootContainer.getStyleClass().contains(styleClass)) {
                    rootContainer.getStyleClass().add(styleClass);
                }
            }
        }

        for (String styleClass : inheritedThemeClasses) {
            if (styleClass != null && styleClass.startsWith("theme-")
                    && !rootContainer.getStyleClass().contains(styleClass)) {
                rootContainer.getStyleClass().add(styleClass);
            }
        }

        applyVisualizerTextPreferences();
    }

    private void applyVisualizerTextPreferences() {
        if (rootContainer == null) {
            return;
        }

        rootContainer.getStyleClass().removeIf(styleClass ->
                styleClass.equals("viz-font-small")
                        || styleClass.equals("viz-font-medium")
                        || styleClass.equals("viz-font-large")
                        || styleClass.equals("viz-text-default")
                        || styleClass.equals("viz-text-soft")
                        || styleClass.equals("viz-text-high-contrast")
        );

        int fontSize = settingsManager.getInt("codeVisualizer.fontSize", 12);
        if (fontSize <= 11) {
            rootContainer.getStyleClass().add("viz-font-small");
        } else if (fontSize >= 14) {
            rootContainer.getStyleClass().add("viz-font-large");
        } else {
            rootContainer.getStyleClass().add("viz-font-medium");
        }

        String textMode = settingsManager.getString("codeVisualizer.textColorMode", "Default");
        switch (textMode.toLowerCase(Locale.ROOT)) {
            case "high contrast" -> rootContainer.getStyleClass().add("viz-text-high-contrast");
            case "soft" -> rootContainer.getStyleClass().add("viz-text-soft");
            default -> rootContainer.getStyleClass().add("viz-text-default");
        }
    }

    private VBox buildRoot() {
        VBox root = new VBox();
        rootContainer = root;
        root.getStyleClass().addAll("window-root", "code-visualizer-root", "visualization-root-pane");
        for (String styleClass : inheritedThemeClasses) {
            if (!root.getStyleClass().contains(styleClass)) {
                root.getStyleClass().add(styleClass);
            }
        }

        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("code-visualizer-split-pane");
        splitPane.getItems().addAll(buildEditorPane(), buildVisualizationPane());
        splitPane.setDividerPositions(0.47);

        HBox resizeBar = buildResizeBar();
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        root.getChildren().addAll(buildTitleBar(), splitPane, resizeBar);
        return root;
    }

    private HBox buildResizeBar() {
        HBox resizeBar = new HBox();
        resizeBar.getStyleClass().add("code-visualizer-resize-bar");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label handle = new Label("◢");
        handle.getStyleClass().add("code-visualizer-resize-handle");

        final double[] dragStartX = new double[1];
        final double[] dragStartY = new double[1];
        final double[] startWidth = new double[1];
        final double[] startHeight = new double[1];

        handle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            dragStartX[0] = event.getScreenX();
            dragStartY[0] = event.getScreenY();
            startWidth[0] = stage.getWidth();
            startHeight[0] = stage.getHeight();
            event.consume();
        });
        handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            double widthDelta = event.getScreenX() - dragStartX[0];
            double heightDelta = event.getScreenY() - dragStartY[0];
            stage.setWidth(Math.max(900, startWidth[0] + widthDelta));
            stage.setHeight(Math.max(620, startHeight[0] + heightDelta));
            event.consume();
        });

        resizeBar.getChildren().addAll(spacer, handle);
        return resizeBar;
    }

    private HBox buildTitleBar() {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("code-visualizer-titlebar");

        Label title = new Label("Code Visualization");
        title.getStyleClass().add("code-visualizer-titlebar-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("x");
        closeButton.getStyleClass().add("code-visualizer-titlebar-close");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(event -> stage.close());

        titleBar.getChildren().addAll(title, spacer, closeButton);

        final double[] dragOffsetX = new double[1];
        final double[] dragOffsetY = new double[1];
        titleBar.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            dragOffsetX[0] = event.getScreenX() - stage.getX();
            dragOffsetY[0] = event.getScreenY() - stage.getY();
        });
        titleBar.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            stage.setX(event.getScreenX() - dragOffsetX[0]);
            stage.setY(event.getScreenY() - dragOffsetY[0]);
        });

        return titleBar;
    }

    private VBox buildEditorPane() {
        VBox pane = new VBox(10);
        pane.getStyleClass().add("code-visualizer-editor-pane");
        pane.setPadding(new Insets(14));

        Label title = new Label("Editable Code");
        title.getStyleClass().add("code-visualizer-title");
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        Button editorPopout = createSectionPopoutButton("Code Editor", this::openEditorPopup);
        HBox titleRow = new HBox(8, title, titleSpacer, editorPopout);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label languageLabel = new Label("Language: " + language.toUpperCase(Locale.ROOT));
        languageLabel.getStyleClass().add("code-visualizer-subtitle");

        codeEditor.setText(originalCode);
        codeEditor.setWrapText(false);
        codeEditor.getStyleClass().add("code-visualizer-editor");
        ScrollPane editorScrollPane = new ScrollPane(codeEditor);
        editorScrollPane.setFitToWidth(true);
        editorScrollPane.setFitToHeight(true);
        editorScrollPane.getStyleClass().addAll("viz-scroll", "custom-scroll-pane", "editor-scroll-pane");
        VBox.setVgrow(editorScrollPane, Priority.ALWAYS);

        resetButton.getStyleClass().add("code-visualizer-reset");
        resetButton.setOnAction(event -> {
            stopPlayback();
            codeEditor.setText(originalCode);
            rebuildTimelineFromEditor();
        });

        runButton.getStyleClass().add("code-visualizer-run");
        runButton.setMinWidth(Region.USE_PREF_SIZE);
        runButton.setOnAction(event -> {
            if (isPlaying()) {
                stopPlayback();
            } else {
                playTimeline();
            }
        });
        runActivityLabel.getStyleClass().add("code-visualizer-run-dots");
        runActivityLabel.setVisible(false);
        runActivityLabel.setManaged(false);

        stepForwardButton.getStyleClass().add("code-visualizer-step");
        stepForwardButton.setMinWidth(Region.USE_PREF_SIZE);
        stepForwardButton.setOnAction(event -> {
            stopPlayback();
            stepForward();
        });

        stepBackButton.getStyleClass().add("code-visualizer-step");
        stepBackButton.setMinWidth(Region.USE_PREF_SIZE);
        stepBackButton.setOnAction(event -> {
            stopPlayback();
            stepBack();
        });

        exportPngButton.getStyleClass().add("code-visualizer-step");
        exportPngButton.setMinWidth(Region.USE_PREF_SIZE);
        exportPngButton.setOnAction(event -> exportSnapshotToPng());

        speedPresetCombo.getItems().setAll("Slow", "Normal", "Fast");
        String savedSpeed = settingsManager.getString("codeVisualizer.playbackSpeed", "Normal");
        speedPresetCombo.setValue(speedPresetCombo.getItems().contains(savedSpeed) ? savedSpeed : "Normal");
        speedPresetCombo.getStyleClass().add("code-visualizer-mini-combo");
        speedPresetCombo.setOnAction(event -> {
            String selected = speedPresetCombo.getValue() == null ? "Normal" : speedPresetCombo.getValue();
            applyPlaybackSpeed(selected);
        });

        scenarioPresetCombo.getItems().setAll(
            "Custom",
            "Recursion Demo",
            "Linked List Demo",
            "HashMap Collision Demo",
            "ArrayList Growth Demo"
        );
        scenarioPresetCombo.setValue("Custom");
        scenarioPresetCombo.getStyleClass().add("code-visualizer-mini-combo");
        scenarioPresetCombo.setOnAction(event -> applyScenarioPreset(scenarioPresetCombo.getValue()));

        FlowPane playbackBar = new FlowPane(
            8,
            8,
            runButton,
            runActivityLabel,
            stepBackButton,
            stepForwardButton,
            resetButton,
            exportPngButton,
            new Label("Speed"),
            speedPresetCombo
        );
        playbackBar.setAlignment(Pos.CENTER_LEFT);
        playbackBar.setHgap(8);
        playbackBar.setVgap(8);
        playbackBar.setPrefWrapLength(760);
        playbackBar.getStyleClass().add("code-visualizer-controls");

        Label presetLabel = new Label("Preset");
        presetLabel.getStyleClass().add("code-visualizer-control-label");
        HBox presetRow = new HBox(8, presetLabel, scenarioPresetCombo);
        presetRow.setAlignment(Pos.CENTER_LEFT);
        presetRow.getStyleClass().add("code-visualizer-shape-row");

        ToggleGroup shapeToggle = new ToggleGroup();
        RadioButton bubbleToggle = new RadioButton("Bubble");
        bubbleToggle.getStyleClass().add("code-visualizer-shape-toggle");
        bubbleToggle.setToggleGroup(shapeToggle);
        bubbleToggle.setSelected(variableShape == VariableShape.BUBBLE);
        bubbleToggle.setOnAction(event -> {
            variableShape = VariableShape.BUBBLE;
            renderCurrentSnapshot();
        });

        RadioButton rectangleToggle = new RadioButton("Rectangle");
        rectangleToggle.getStyleClass().add("code-visualizer-shape-toggle");
        rectangleToggle.setToggleGroup(shapeToggle);
        rectangleToggle.setSelected(variableShape == VariableShape.RECTANGLE);
        rectangleToggle.setOnAction(event -> {
            variableShape = VariableShape.RECTANGLE;
            renderCurrentSnapshot();
        });

        Label variableShapeLabel = new Label("Variable Shape:");
        variableShapeLabel.getStyleClass().add("code-visualizer-control-label");
        HBox shapeRow = new HBox(8, variableShapeLabel, bubbleToggle, rectangleToggle);
        shapeRow.getStyleClass().add("code-visualizer-shape-row");
        shapeRow.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusRow = new HBox(10,
                new Label("Track values, scope, and garbage state"),
                spacer,
                currentLineLabel,
                new Separator(),
                stepIndicator
        );
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getStyleClass().add("code-visualizer-status");

        pane.getStyleClass().add("visualizer-pane");
        pane.getChildren().addAll(titleRow, languageLabel, playbackBar, presetRow, shapeRow, statusRow, editorScrollPane);
        return pane;
    }

    private VBox buildVisualizationPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(14));
        pane.getStyleClass().addAll("code-visualizer-right-pane", "panel-container");

        SplitPane vertical = new SplitPane();
        vertical.setOrientation(javafx.geometry.Orientation.VERTICAL);
        vertical.getStyleClass().add("code-visualizer-vertical-split");

        VBox variablePanel = new VBox(8);
        variablePanel.getStyleClass().addAll("viz-panel", "visualizer-pane");
        Label variableTitle = new Label("Variable Tracking");
        variableTitle.getStyleClass().add("viz-panel-title");
        Region variableSpacer = new Region();
        HBox.setHgrow(variableSpacer, Priority.ALWAYS);
        Button variablePopout = createSectionPopoutButton("Variable Tracking", this::openVariablePopup);
        HBox variableHeader = new HBox(8, variableTitle, variableSpacer, variablePopout);
        variableHeader.setAlignment(Pos.CENTER_LEFT);
        variableFlow.setHgap(10);
        variableFlow.setVgap(10);
        variableFlow.setPadding(new Insets(6));
        variableFlow.getStyleClass().add("viz-variable-flow");
        ScrollPane variableScroll = new ScrollPane(variableFlow);
        variableScroll.setFitToWidth(true);
        variableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        variableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        variableScroll.getStyleClass().addAll("viz-scroll", "custom-scroll-pane");
        VBox.setVgrow(variableScroll, Priority.ALWAYS);
        variablePanel.getChildren().addAll(variableHeader, variableScroll);

        VBox structurePanel = new VBox(8);
        structurePanel.getStyleClass().addAll("viz-panel", "visualizer-pane");
        Label structureTitle = new Label("Data Structure Visualizer");
        structureTitle.getStyleClass().add("viz-panel-title");
        Region structureSpacer = new Region();
        HBox.setHgrow(structureSpacer, Priority.ALWAYS);
        Button structurePopout = createSectionPopoutButton("Data Structure Visualizer", this::openStructurePopup);
        HBox structureHeader = new HBox(8, structureTitle, structureSpacer, structurePopout);
        structureHeader.setAlignment(Pos.CENTER_LEFT);
        structurePane.getStyleClass().add("viz-structure-pane");
        structurePane.setMinHeight(220);
        ScrollPane structureScroll = new ScrollPane(structurePane);
        structureScroll.setFitToWidth(true);
        structureScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        structureScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        structureScroll.getStyleClass().addAll("viz-scroll", "custom-scroll-pane");
        VBox.setVgrow(structureScroll, Priority.ALWAYS);
        structurePanel.getChildren().addAll(structureHeader, structureScroll);

        VBox callStackPanel = new VBox(8);
        callStackPanel.getStyleClass().addAll("viz-panel", "visualizer-pane");
        Label stackTitle = new Label("Recursion & Call Stack");
        stackTitle.getStyleClass().add("viz-panel-title");
        Region stackSpacer = new Region();
        HBox.setHgrow(stackSpacer, Priority.ALWAYS);
        Button stackPopout = createSectionPopoutButton("Recursion & Call Stack", this::openStackPopup);
        stackFocusToggle.setSelected(stackFocusMode);
        stackFocusToggle.getStyleClass().add("code-visualizer-step");
        stackFocusToggle.setOnAction(event -> {
            stackFocusMode = stackFocusToggle.isSelected();
            settingsManager.set("codeVisualizer.stackFocusDefault", stackFocusMode);
            settingsManager.save();
            renderCurrentSnapshot();
        });

        compareModeToggle.setSelected(compareModeEnabled);
        compareModeToggle.getStyleClass().add("code-visualizer-step");
        compareModeToggle.setOnAction(event -> {
            compareModeEnabled = compareModeToggle.isSelected();
            renderCurrentSnapshot();
        });

        swapCompareButton.getStyleClass().add("code-visualizer-step");
        swapCompareButton.setOnAction(event -> swapCompareSteps());
        swapCompareButton.managedProperty().bind(compareModeToggle.selectedProperty());
        swapCompareButton.visibleProperty().bind(compareModeToggle.selectedProperty());

        HBox stackHeader = new HBox(8, stackTitle, stackSpacer, stackFocusToggle, compareModeToggle, swapCompareButton, stackPopout);
        stackHeader.setAlignment(Pos.CENTER_LEFT);

        stackTimelineSlider.setMin(0);
        stackTimelineSlider.setMax(0);
        stackTimelineSlider.setValue(0);
        stackTimelineSlider.setMajorTickUnit(1);
        stackTimelineSlider.setMinorTickCount(0);
        stackTimelineSlider.setSnapToTicks(true);
        stackTimelineSlider.getStyleClass().add("code-visualizer-stack-slider");
        stackTimelineSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncingStackSlider || timeline.isEmpty()) {
                return;
            }
            int selectedIndex = Math.max(0, Math.min((int) Math.round(newVal.doubleValue()), timeline.size() - 1));
            if (selectedIndex != timelineIndex) {
                stopPlayback();
                timelineIndex = selectedIndex;
                renderCurrentSnapshot();
            }
        });
        HBox stackTimelineRow = new HBox(8, stackTimelineLabel, stackTimelineSlider);
        stackTimelineRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(stackTimelineSlider, Priority.ALWAYS);
        stackTimelineRow.getStyleClass().add("code-visualizer-shape-row");

        compareBaseSlider.setMin(0);
        compareBaseSlider.setMax(0);
        compareBaseSlider.setValue(0);
        compareBaseSlider.setMajorTickUnit(1);
        compareBaseSlider.setMinorTickCount(0);
        compareBaseSlider.setSnapToTicks(true);
        compareBaseSlider.getStyleClass().add("code-visualizer-stack-slider");
        compareBaseSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncingCompareSlider || timeline.isEmpty() || !compareModeEnabled) {
                return;
            }
            renderCurrentSnapshot();
        });
        HBox compareBaseRow = new HBox(8, compareBaseLabel, compareBaseSlider);
        compareBaseRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(compareBaseSlider, Priority.ALWAYS);
        compareBaseRow.getStyleClass().add("code-visualizer-shape-row");
        compareBaseRow.managedProperty().bind(compareModeToggle.selectedProperty());
        compareBaseRow.visibleProperty().bind(compareModeToggle.selectedProperty());

        callStackBox.getStyleClass().add("viz-call-stack");
        ScrollPane stackScroll = new ScrollPane(callStackBox);
        stackScroll.setFitToWidth(true);
        stackScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        stackScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        stackScroll.getStyleClass().addAll("viz-scroll", "custom-scroll-pane");
        VBox.setVgrow(stackScroll, Priority.ALWAYS);
        callStackPanel.getChildren().addAll(stackHeader, stackTimelineRow, compareBaseRow, stackScroll);

        vertical.getItems().addAll(variablePanel, structurePanel, callStackPanel);
        vertical.setDividerPositions(0.35, 0.72);
        VBox.setVgrow(vertical, Priority.ALWAYS);

        pane.getChildren().add(vertical);
        return pane;
    }

    private void swapCompareSteps() {
        if (timeline == null || timeline.isEmpty()) {
            return;
        }

        int currentStep = Math.max(0, Math.min(timelineIndex, timeline.size() - 1));
        int baseStep = getCompareBaseStepIndex();

        syncingStackSlider = true;
        stackTimelineSlider.setValue(baseStep);
        syncingStackSlider = false;

        syncingCompareSlider = true;
        compareBaseSlider.setValue(currentStep);
        syncingCompareSlider = false;

        timelineIndex = baseStep;
        renderCurrentSnapshot();
    }

    private void applyPlaybackSpeed(String speedPreset) {
        String normalized = speedPreset == null ? "Normal" : speedPreset;
        switch (normalized.toLowerCase(Locale.ROOT)) {
            case "slow" -> {
                playbackIntervalMs = 900;
                floatingSpeedScale = 1.3;
                speedPresetCombo.setValue("Slow");
                settingsManager.set("codeVisualizer.playbackSpeed", "Slow");
            }
            case "fast" -> {
                playbackIntervalMs = 280;
                floatingSpeedScale = 0.75;
                speedPresetCombo.setValue("Fast");
                settingsManager.set("codeVisualizer.playbackSpeed", "Fast");
            }
            default -> {
                playbackIntervalMs = 520;
                floatingSpeedScale = 1.0;
                speedPresetCombo.setValue("Normal");
                settingsManager.set("codeVisualizer.playbackSpeed", "Normal");
            }
        }
        settingsManager.save();
    }

    private void applyScenarioPreset(String preset) {
        if (preset == null || "Custom".equalsIgnoreCase(preset)) {
            return;
        }

        stopPlayback();
        switch (preset) {
            case "Recursion Demo" -> codeEditor.setText(getRecursionPresetCode());
            case "Linked List Demo" -> codeEditor.setText(getLinkedListPresetCode());
            case "HashMap Collision Demo" -> codeEditor.setText(getHashMapPresetCode());
            case "ArrayList Growth Demo" -> codeEditor.setText(getArrayListPresetCode());
            default -> {
                return;
            }
        }
        rebuildTimelineFromEditor();
    }

    private void exportSnapshotToPng() {
        if (rootContainer == null || rootContainer.getScene() == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Code Visualizer Snapshot");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        chooser.setInitialFileName("code-visualizer-step-" + Math.max(0, timelineIndex + 1) + ".png");
        File output = chooser.showSaveDialog(stage);
        if (output == null) {
            return;
        }

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage image = rootContainer.snapshot(parameters, null);
        try {
            BufferedImage buffered = toBufferedImage(image);
            ImageIO.write(buffered, "png", output);
        } catch (IOException ignored) {
            // Keep the visualizer responsive if file export fails.
        }
    }

    private BufferedImage toBufferedImage(WritableImage image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return buffered;
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return buffered;
    }

    private String getRecursionPresetCode() {
        return "public class RecursionDemo {\n"
                + "    static int fib(int n) {\n"
                + "        if (n <= 1) return n;\n"
                + "        return fib(n - 1) + fib(n - 2);\n"
                + "    }\n"
                + "    public static void main(String[] args) {\n"
                + "        int n = 5;\n"
                + "        int result = fib(n);\n"
                + "        System.out.println(result);\n"
                + "    }\n"
                + "}";
    }

    private String getLinkedListPresetCode() {
        return "public class LinkedListDemo {\n"
                + "    public static void main(String[] args) {\n"
                + "        String linked = \"1->45->60->12\";\n"
                + "        String doubly = \"A<->B<->C<->D\";\n"
                + "        System.out.println(linked + doubly);\n"
                + "    }\n"
                + "}";
    }

    private String getHashMapPresetCode() {
        return "import java.util.HashMap;\n"
                + "public class HashMapDemo {\n"
                + "    public static void main(String[] args) {\n"
                + "        String map = \"{k1=10, k11=20, k21=30, k2=40}\";\n"
                + "        String set = \"{a,b,c,d}\";\n"
                + "        System.out.println(map + set);\n"
                + "    }\n"
                + "}";
    }

    private String getArrayListPresetCode() {
        return "public class ArrayListDemo {\n"
                + "    public static void main(String[] args) {\n"
                + "        String arr = \"[3, 5, 7, 9]\";\n"
                + "        String list = \"ArrayList[3,5,7,9,11]\";\n"
                + "        System.out.println(arr + list);\n"
                + "    }\n"
                + "}";
    }

    private void rebuildTimelineFromEditor() {
        timeline = CodeTimelineEngine.buildSnapshots(codeEditor.getText());
        recursionDetected = CodeTimelineEngine.detectRecursion(codeEditor.getText());
        timelineIndex = timeline.isEmpty() ? -1 : 0;
        previousStackDepth = 0;
        previousFrameNames = List.of();
        previousCapacityByStructure.clear();
        previousLinkedListNodeCount.clear();
        renderCurrentSnapshot();
    }

    private void stepForward() {
        if (timeline.isEmpty()) {
            rebuildTimelineFromEditor();
            return;
        }
        if (timelineIndex < timeline.size() - 1) {
            timelineIndex++;
            renderCurrentSnapshot();
        }
    }

    private void stepBack() {
        if (timeline.isEmpty()) {
            rebuildTimelineFromEditor();
            return;
        }
        if (timelineIndex > 0) {
            timelineIndex--;
            renderCurrentSnapshot();
        }
    }

    private void playTimeline() {
        if (timeline.isEmpty()) {
            rebuildTimelineFromEditor();
        }
        if (timeline.isEmpty()) {
            return;
        }

        if (timelineIndex >= timeline.size() - 1) {
            timelineIndex = 0;
            renderCurrentSnapshot();
        }

        stopPlayback();
        runButton.setText("Pause");
        startRunActivityIndicator();

        playbackRunning = true;
        Task<Void> playbackLoop = new Task<>() {
            @Override
            protected Void call() {
                while (playbackRunning && !isCancelled()) {
                    Platform.runLater(() -> {
                        if (!playbackRunning) {
                            return;
                        }
                        if (timelineIndex >= timeline.size() - 1) {
                            stopPlayback();
                            return;
                        }
                        timelineIndex++;
                        renderCurrentSnapshot();
                    });

                    try {
                        Thread.sleep(playbackIntervalMs);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return null;
            }
        };

        playbackThread = new Thread(playbackLoop, "code-visualizer-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private boolean isPlaying() {
        return playbackRunning;
    }

    private void stopPlayback() {
        playbackRunning = false;
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
        runButton.setText("Run");
        stopRunActivityIndicator();
    }

    private void startRunActivityIndicator() {
        runActivityLabel.setVisible(true);
        runActivityLabel.setManaged(true);
        if (runActivityTimeline != null) {
            runActivityTimeline.stop();
        }

        runActivityTimeline = new Timeline(
                new KeyFrame(Duration.millis(0), event -> runActivityLabel.setText(".")),
                new KeyFrame(Duration.millis(220), event -> runActivityLabel.setText("..")),
                new KeyFrame(Duration.millis(440), event -> runActivityLabel.setText("..."))
        );
        runActivityTimeline.setCycleCount(Animation.INDEFINITE);
        runActivityTimeline.play();
    }

    private void stopRunActivityIndicator() {
        if (runActivityTimeline != null) {
            runActivityTimeline.stop();
            runActivityTimeline = null;
        }
        runActivityLabel.setText("...");
        runActivityLabel.setVisible(false);
        runActivityLabel.setManaged(false);
    }

    private void shutdownExecutor() {
        stopPlayback();
        stopFloatingTransitions();
        closeSectionPopups();
    }

    private void closeSectionPopups() {
        if (editorPopupStage != null) {
            editorPopupStage.close();
        }
        if (variablePopupStage != null) {
            variablePopupStage.close();
        }
        if (structurePopupStage != null) {
            structurePopupStage.close();
        }
        if (stackPopupStage != null) {
            stackPopupStage.close();
        }
    }

    private void renderCurrentSnapshot() {
        if (timeline.isEmpty() || timelineIndex < 0 || timelineIndex >= timeline.size()) {
            variableFlow.getChildren().clear();
            structurePane.getChildren().clear();
            callStackBox.getChildren().clear();
            currentLineLabel.setText("Line: -");
            stepIndicator.setText("Step 0/0");
            syncingStackSlider = true;
            stackTimelineSlider.setMin(0);
            stackTimelineSlider.setMax(0);
            stackTimelineSlider.setValue(0);
            stackTimelineLabel.setText("Stack Step: 0/0");
            syncingStackSlider = false;

            syncingCompareSlider = true;
            compareBaseSlider.setMin(0);
            compareBaseSlider.setMax(0);
            compareBaseSlider.setValue(0);
            compareBaseLabel.setText("Base Step: 0/0");
            syncingCompareSlider = false;
            return;
        }

        ExecutionSnapshot snapshot = timeline.get(timelineIndex);
        currentLineLabel.setText("Line: " + snapshot.lineNumber());
        stepIndicator.setText("Step " + (timelineIndex + 1) + "/" + timeline.size());

        syncingStackSlider = true;
        stackTimelineSlider.setMin(0);
        stackTimelineSlider.setMax(Math.max(0, timeline.size() - 1));
        stackTimelineSlider.setValue(timelineIndex);
        stackTimelineLabel.setText("Stack Step: " + (timelineIndex + 1) + "/" + timeline.size());
        syncingStackSlider = false;

        syncingCompareSlider = true;
        compareBaseSlider.setMin(0);
        compareBaseSlider.setMax(Math.max(0, timeline.size() - 1));
        int compareBaseStep = getCompareBaseStepIndex();
        compareBaseSlider.setValue(compareBaseStep);
        compareBaseLabel.setText("Base Step: " + (compareBaseStep + 1) + "/" + timeline.size());
        syncingCompareSlider = false;

        if (stackFocusMode) {
            renderCallStack(snapshot.callStack());
            if (callStackPopupBox != null) {
                renderCallStackOn(callStackPopupBox, snapshot.callStack());
            }
            updateStepButtons();
            return;
        }

        renderVariables(snapshot.variables());
        renderDataStructures(snapshot.dataStructures());
        renderCallStack(snapshot.callStack());

        if (variablePopupFlow != null) {
            renderVariablesOn(variablePopupFlow, snapshot.variables());
        }
        if (structurePopupPane != null) {
            renderDataStructuresOn(structurePopupPane, snapshot.dataStructures());
        }
        if (callStackPopupBox != null) {
            renderCallStackOn(callStackPopupBox, snapshot.callStack());
        }
        updateStepButtons();
    }

    private void updateStepButtons() {
        boolean hasTimeline = !timeline.isEmpty();
        stepBackButton.setDisable(!hasTimeline || timelineIndex <= 0);
        stepForwardButton.setDisable(!hasTimeline || timelineIndex >= timeline.size() - 1);
    }

    private Button createSectionPopoutButton(String title, Runnable action) {
        Button popout = new Button("⧉");
        popout.getStyleClass().add("code-visualizer-popout");
        popout.setFocusTraversable(false);
        popout.setOnAction(event -> action.run());
        popout.setTooltip(new Tooltip("Open " + title + " in a separate dialog"));

        boolean enabled = settingsManager.getBoolean("codeVisualizer.enableSectionPopout", true);
        popout.setVisible(enabled);
        popout.setManaged(enabled);
        return popout;
    }

    private VBox createPopupRoot(String title, Node content, Runnable dockAction) {
        VBox popupRoot = new VBox(10);
        popupRoot.getStyleClass().addAll("window-root", "code-visualizer-root", "visualization-root-pane");
        if (rootContainer != null) {
            for (String cls : rootContainer.getStyleClass()) {
                if (cls.startsWith("theme-") && !popupRoot.getStyleClass().contains(cls)) {
                    popupRoot.getStyleClass().add(cls);
                }
            }
        }

        Label header = new Label(title);
        header.getStyleClass().add("code-visualizer-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        Button dockBackButton = new Button("↩");
        dockBackButton.getStyleClass().add("code-visualizer-dock-back");
        dockBackButton.setFocusTraversable(false);
        dockBackButton.setTooltip(new Tooltip("Dock back to main visualizer"));
        dockBackButton.setOnAction(event -> {
            if (dockAction != null) {
                dockAction.run();
            }
        });

        HBox headerRow = new HBox(8, header, headerSpacer, dockBackButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        popupRoot.setPadding(new Insets(14));
        VBox.setVgrow(content, Priority.ALWAYS);
        popupRoot.getChildren().addAll(headerRow, content);
        return popupRoot;
    }

    private Stage createSectionStage(String title, Node content, int width, int height, Runnable onHiddenCleanup) {
        Stage popup = new Stage();
        popup.initOwner(stage);
        popup.initModality(Modality.NONE);
        popup.setTitle(title);
        IconResources.addStageIcons(popup, getClass());

        Scene popupScene = new Scene(createPopupRoot(title, content, () -> {
            popup.close();
            stage.toFront();
            stage.requestFocus();
        }), width, height);
        inheritParentStylesheets(popupScene);
        popup.setScene(popupScene);

        popup.setOnHidden(event -> {
            if (onHiddenCleanup != null) {
                onHiddenCleanup.run();
            }
        });
        return popup;
    }

    private void openEditorPopup() {
        if (editorPopupStage != null && editorPopupStage.isShowing()) {
            editorPopupStage.requestFocus();
            return;
        }

        TextArea popupEditor = new TextArea();
        popupEditor.setWrapText(false);
        popupEditor.getStyleClass().add("code-visualizer-editor");
        popupEditor.textProperty().bindBidirectional(codeEditor.textProperty());
        popupCodeEditor = popupEditor;

        ScrollPane popupScroll = new ScrollPane(popupEditor);
        popupScroll.setFitToWidth(true);
        popupScroll.setFitToHeight(true);
        popupScroll.getStyleClass().addAll("viz-scroll", "custom-scroll-pane", "editor-scroll-pane");

        editorPopupStage = createSectionStage("Code Editor", popupScroll, 760, 620, () -> {
            if (popupCodeEditor != null) {
                popupCodeEditor.textProperty().unbindBidirectional(codeEditor.textProperty());
            }
            popupCodeEditor = null;
            editorPopupStage = null;
        });
        editorPopupStage.show();
    }

    private void openVariablePopup() {
        if (variablePopupStage != null && variablePopupStage.isShowing()) {
            variablePopupStage.requestFocus();
            return;
        }

        FlowPane popupFlow = new FlowPane();
        popupFlow.setHgap(variableFlow.getHgap());
        popupFlow.setVgap(variableFlow.getVgap());
        popupFlow.setPadding(new Insets(6));
        popupFlow.getStyleClass().add("viz-variable-flow");
        variablePopupFlow = popupFlow;

        ScrollPane popupScroll = new ScrollPane(popupFlow);
        popupScroll.setFitToWidth(true);
        popupScroll.getStyleClass().addAll("viz-scroll", "custom-scroll-pane");

        variablePopupStage = createSectionStage("Variable Tracking", popupScroll, 720, 420, () -> {
            variablePopupFlow = null;
            variablePopupStage = null;
        });
        variablePopupStage.show();
        renderCurrentSnapshot();
    }

    private void openStructurePopup() {
        if (structurePopupStage != null && structurePopupStage.isShowing()) {
            structurePopupStage.requestFocus();
            return;
        }

        AnchorPane popupPane = new AnchorPane();
        popupPane.getStyleClass().add("viz-structure-pane");
        popupPane.setMinHeight(260);
        structurePopupPane = popupPane;

        structurePopupStage = createSectionStage("Data Structure Visualizer", popupPane, 860, 460, () -> {
            structurePopupPane = null;
            structurePopupStage = null;
        });
        structurePopupStage.show();
        renderCurrentSnapshot();
    }

    private void openStackPopup() {
        if (stackPopupStage != null && stackPopupStage.isShowing()) {
            stackPopupStage.requestFocus();
            return;
        }

        VBox popupStack = new VBox(8);
        popupStack.getStyleClass().add("viz-call-stack");
        callStackPopupBox = popupStack;

        ScrollPane popupScroll = new ScrollPane(popupStack);
        popupScroll.setFitToWidth(true);
        popupScroll.getStyleClass().addAll("viz-scroll", "custom-scroll-pane");

        stackPopupStage = createSectionStage("Recursion & Call Stack", popupScroll, 620, 440, () -> {
            callStackPopupBox = null;
            stackPopupStage = null;
        });
        stackPopupStage.show();
        renderCurrentSnapshot();
    }

    private void renderVariables(List<VariableState> variables) {
        renderVariablesOn(variableFlow, variables);
    }

    private void renderVariablesOn(FlowPane targetFlow, List<VariableState> variables) {
        targetFlow.getChildren().clear();
        if (variables == null || variables.isEmpty()) {
            Label empty = new Label("No active variables yet.");
            empty.getStyleClass().add("viz-empty-label");
            targetFlow.getChildren().add(empty);
            return;
        }

        for (VariableState variable : variables) {
            StackPane node = new StackPane();
            node.getStyleClass().add("viz-variable-node");
            if (variable.isGarbage()) {
                node.getStyleClass().add("viz-garbage");
            }

            Label label = new Label(buildVariableBubbleText(variable));
            label.setWrapText(true);
            label.getStyleClass().add("viz-variable-text");
            if (variable.isGarbage()) {
                label.getStyleClass().add("viz-garbage-label");
            }
            label.setMaxWidth(62);
            label.setAlignment(Pos.CENTER);
            label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            if (variableShape == VariableShape.RECTANGLE) {
                Rectangle rectangle = new Rectangle(78, 60);
                rectangle.getStyleClass().add("viz-variable-rect-shape");
                if (variable.isGarbage()) {
                    rectangle.getStyleClass().add("viz-garbage-shape");
                }
                node.getChildren().addAll(rectangle, label);
            } else {
                Circle circle = new Circle(34);
                circle.getStyleClass().add("viz-variable-bubble-shape");
                if (variable.isGarbage()) {
                    circle.getStyleClass().add("viz-garbage-shape");
                }
                node.getChildren().addAll(circle, label);
            }
            targetFlow.getChildren().add(node);
        }
    }

    private String buildVariableBubbleText(VariableState variable) {
        String name = compactLabel(variable.name(), 14);
        String objectLabel = compactLabel(extractObjectLabel(variable.value()), 14);
        if (objectLabel.isBlank()) {
            return name;
        }
        return name + "\n" + objectLabel;
    }

    private String extractObjectLabel(String value) {
        if (value == null || value.isBlank() || "(uninitialized)".equalsIgnoreCase(value.trim())) {
            return "uninit";
        }

        String normalized = value.trim();
        if ("null".equalsIgnoreCase(normalized)) {
            return "null";
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return "Array";
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("arraylist")) {
            return "ArrayList";
        }
        if (lower.contains("linkedlist") || normalized.contains("->")) {
            return "LinkedList";
        }
        if (lower.contains("hashmap")) {
            return "HashMap";
        }
        if (lower.contains("hashset")) {
            return "HashSet";
        }

        Matcher objectMatcher = OBJECT_TYPE_PATTERN.matcher(normalized);
        if (objectMatcher.find()) {
            return objectMatcher.group(1);
        }

        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            return "String";
        }
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return "Boolean";
        }
        if (normalized.matches("-?\\d+(\\.\\d+)?")) {
            return "Number";
        }
        return normalized;
    }

    private String compactLabel(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private void renderDataStructures(List<DataStructureState> structures) {
        stopFloatingTransitions();
        renderDataStructuresOn(structurePane, structures);
    }

    private void renderDataStructuresOn(AnchorPane targetPane, List<DataStructureState> structures) {
        targetPane.getChildren().clear();
        if (structures == null || structures.isEmpty()) {
            Label empty = new Label("No array/list/map/set/linked list detected in the current step.");
            empty.getStyleClass().add("viz-empty-label");
            AnchorPane.setTopAnchor(empty, 8.0);
            AnchorPane.setLeftAnchor(empty, 8.0);
            targetPane.getChildren().add(empty);
            return;
        }

        double y = 14;
        for (DataStructureState state : structures) {
            Label title = new Label(state.name() + " (" + state.type().label + ")");
            title.getStyleClass().add("viz-structure-title");
            title.setLayoutX(10);
            title.setLayoutY(y);
            targetPane.getChildren().add(title);
            y += 24;

            switch (state.type()) {
                case ARRAY, ARRAY_LIST -> y = drawArrayLike(targetPane, state, y);
                case HASH_MAP, HASH_SET -> y = drawHashLike(targetPane, state, y);
                case LINKED_LIST -> y = drawLinkedList(targetPane, state, y, targetPane == structurePane);
            }

            y += 14;
        }
    }

    private double drawArrayLike(AnchorPane targetPane, DataStructureState state, double y) {
        double x = 10;
        List<String> values = state.values();
        int previousCapacity = previousCapacityByStructure.getOrDefault(state.name(), values.size());

        for (int i = 0; i < values.size(); i++) {
            Rectangle cell = new Rectangle(56, 34);
            cell.getStyleClass().add("viz-array-cell");
            cell.setLayoutX(x);
            cell.setLayoutY(y);

            Label value = new Label(values.get(i));
            value.getStyleClass().add("viz-array-value");
            value.setLayoutX(x + 8);
            value.setLayoutY(y + 7);

            Label index = new Label("[" + i + "]");
            index.getStyleClass().add("viz-array-index");
            index.setLayoutX(x + 14);
            index.setLayoutY(y + 36);

            targetPane.getChildren().addAll(cell, value, index);
            x += 62;
        }

        if (state.type() == DataStructureType.ARRAY_LIST && state.capacity() > values.size()) {
            for (int i = values.size(); i < state.capacity(); i++) {
                Rectangle freeCell = new Rectangle(56, 34);
                freeCell.getStyleClass().addAll("viz-array-cell", "viz-capacity-cell");
                freeCell.setLayoutX(x);
                freeCell.setLayoutY(y);
                if (state.capacity() > previousCapacity) {
                    freeCell.setOpacity(0);
                    FadeTransition growIn = new FadeTransition(Duration.millis(260), freeCell);
                    growIn.setFromValue(0);
                    growIn.setToValue(1);
                    growIn.play();
                }
                targetPane.getChildren().add(freeCell);
                x += 62;
            }
        }

        previousCapacityByStructure.put(state.name(), state.capacity());

        return y + 54;
    }

    private double drawHashLike(AnchorPane targetPane, DataStructureState state, double y) {
        int bucketCount = Math.max(4, state.capacity());
        double startX = 10;

        for (int i = 0; i < bucketCount; i++) {
            Rectangle bucket = new Rectangle(84, 28);
            bucket.getStyleClass().add("viz-hash-bucket");
            bucket.setLayoutX(startX);
            bucket.setLayoutY(y);
            Label bucketLabel = new Label("B" + i);
            bucketLabel.getStyleClass().add("viz-hash-bucket-label");
            bucketLabel.setLayoutX(startX + 8);
            bucketLabel.setLayoutY(y + 6);
            targetPane.getChildren().addAll(bucket, bucketLabel);
            startX += 94;
        }

        Map<Integer, List<Map.Entry<String, String>>> collisions = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : state.entries().entrySet()) {
            int bucketIndex = Math.abs(entry.getKey().hashCode()) % bucketCount;
            collisions.computeIfAbsent(bucketIndex, key -> new ArrayList<>()).add(entry);
        }

        double maxY = y + 40;
        for (Map.Entry<Integer, List<Map.Entry<String, String>>> bucketEntries : collisions.entrySet()) {
            int bucketIndex = bucketEntries.getKey();
            double bucketX = 10 + (bucketIndex * 94);
            double itemY = y + 40;
            Rectangle previousNode = null;

            for (Map.Entry<String, String> entry : bucketEntries.getValue()) {
                Rectangle node = new Rectangle(120, 26);
                node.getStyleClass().add("viz-hash-entry");
                node.setLayoutX(bucketX + 18);
                node.setLayoutY(itemY);

                Label nodeLabel = new Label(entry.getKey() + " -> " + entry.getValue());
                nodeLabel.getStyleClass().add("viz-hash-entry-label");
                nodeLabel.setLayoutX(bucketX + 24);
                nodeLabel.setLayoutY(itemY + 5);

                Line chain = new Line();
                chain.getStyleClass().add("viz-pointer-line");
                if (previousNode == null) {
                    chain.startXProperty().set(bucketX + 42);
                    chain.startYProperty().set(y + 28);
                } else {
                    chain.startXProperty().bind(previousNode.layoutXProperty().add(previousNode.widthProperty().divide(2)));
                    chain.startYProperty().bind(previousNode.layoutYProperty().add(previousNode.heightProperty()));
                }
                chain.endXProperty().bind(node.layoutXProperty().add(node.widthProperty().divide(2)));
                chain.endYProperty().bind(node.layoutYProperty());

                targetPane.getChildren().addAll(chain, node, nodeLabel);
                previousNode = node;
                itemY += 34;
            }
            maxY = Math.max(maxY, itemY);
        }

        return maxY + 6;
    }

    private double drawLinkedList(AnchorPane targetPane, DataStructureState state, double y, boolean animate) {
        List<String> values = state.values();
        int nodeCount = values.size();
        int previousCount = previousLinkedListNodeCount.getOrDefault(state.name(), 0);
        if (values.isEmpty()) {
            Label empty = new Label("Head -> Null");
            empty.getStyleClass().add("viz-empty-label");
            empty.setLayoutX(10);
            empty.setLayoutY(y);
            targetPane.getChildren().add(empty);
            previousLinkedListNodeCount.put(state.name(), 0);
            return y + 28;
        }

        Label head = new Label("Head");
        head.getStyleClass().add("viz-head-label");
        head.setLayoutX(10);
        head.setLayoutY(y + 12);
        targetPane.getChildren().add(head);

        double availableWidth = targetPane.getWidth() > 220 ? targetPane.getWidth() - 120 : 660;
        double panelWidth = targetPane.getWidth() > 220 ? targetPane.getWidth() : 780;
        double minNodeSpacing = 54;
        double maxNodeSpacing = 86;
        int maxNodesPerRow = Math.max(2, (int) Math.floor((availableWidth - 24) / minNodeSpacing));
        maxNodesPerRow = Math.max(1, maxNodesPerRow);
        int totalRows = (int) Math.ceil(nodeCount / (double) maxNodesPerRow);
        double rowHeight = (state.doublyLinked() && totalRows > 1) ? 88 : 74;
        double rowBaseX = 86;

        int[] rowCounts = new int[totalRows];
        for (int row = 0; row < totalRows; row++) {
            int remaining = nodeCount - (row * maxNodesPerRow);
            rowCounts[row] = Math.min(maxNodesPerRow, Math.max(0, remaining));
        }

        double baseSpacing = nodeCount <= 1
                ? 74
                : Math.max(minNodeSpacing, Math.min(maxNodeSpacing, availableWidth / Math.max(1, maxNodesPerRow)));
        double curveDepth = Math.max(10, Math.min(22, baseSpacing * 0.34));
        double floatAmplitude = Math.max(1.2, Math.min(3.8, 4.2 - (nodeCount * 0.14)));

        if (state.doublyLinked() && settingsManager.getBoolean("codeVisualizer.linkedListLegendEnabled", true)) {
            HBox legend = new HBox(8);
            legend.getStyleClass().add("viz-linked-legend");

            Label nextLegend = new Label("Next");
            nextLegend.getStyleClass().addAll("viz-linked-legend-chip", "viz-linked-legend-next");
            Label prevLegend = new Label("Prev");
            prevLegend.getStyleClass().addAll("viz-linked-legend-chip", "viz-linked-legend-prev");

            legend.getChildren().addAll(nextLegend, prevLegend);
            legend.setLayoutX(Math.max(220, panelWidth - 180));
            legend.setLayoutY(y + 4);
            targetPane.getChildren().add(legend);
        }

        if (totalRows > 1 && settingsManager.getBoolean("codeVisualizer.linkedListRowMarkersEnabled", true)) {
            for (int row = 0; row < totalRows; row++) {
                Label rowLabel = new Label("Row " + (row + 1));
                rowLabel.getStyleClass().add("viz-row-marker");
                rowLabel.setLayoutX(14);
                rowLabel.setLayoutY(y + (row * rowHeight) + 10);
                targetPane.getChildren().add(rowLabel);
            }
        }

        List<StackPane> nodes = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            int row = i / maxNodesPerRow;
            int logicalCol = i % maxNodesPerRow;
            int rowCount = rowCounts[row];
            boolean reverseRow = (row % 2 == 1);
            int visualCol = reverseRow ? (rowCount - 1 - logicalCol) : logicalCol;
            double rowSpacing = rowCount <= 1
                    ? 0
                    : Math.max(minNodeSpacing, Math.min(maxNodeSpacing, (availableWidth - 24) / (rowCount - 1)));
            double centerX = rowBaseX + (rowCount <= 1 ? 0 : (visualCol * rowSpacing));
            double nodeY = y + (row * rowHeight);

            Circle nodeShape = new Circle(20);
            nodeShape.getStyleClass().add("viz-linked-node");

            Label value = new Label(values.get(i));
            value.getStyleClass().add("viz-linked-value");

            StackPane nodeStack = new StackPane(nodeShape, value);
            nodeStack.getStyleClass().add("viz-linked-node-stack");
            nodeStack.setPrefSize(40, 40);
            nodeStack.setMinSize(40, 40);
            nodeStack.setMaxSize(40, 40);
            nodeStack.setLayoutX(centerX - 20);
            nodeStack.setLayoutY(nodeY);
            nodes.add(nodeStack);
            targetPane.getChildren().add(nodeStack);

            if (animate && i >= previousCount) {
                nodeStack.setOpacity(0);
                nodeStack.setScaleX(0.65);
                nodeStack.setScaleY(0.65);

                FadeTransition appear = new FadeTransition(Duration.millis(260), nodeStack);
                appear.setFromValue(0);
                appear.setToValue(1);

                ScaleTransition grow = new ScaleTransition(Duration.millis(260), nodeStack);
                grow.setFromX(0.65);
                grow.setFromY(0.65);
                grow.setToX(1);
                grow.setToY(1);

                appear.play();
                grow.play();
            }

            if (animate && settingsManager.getBoolean("codeVisualizer.linkedListFloatingEnabled", true)) {
                TranslateTransition floatTransition = new TranslateTransition(
                    Duration.millis((1800 + (i * 120L)) * floatingSpeedScale),
                    nodeStack
                );
                floatTransition.setFromY((i % 2 == 0) ? -floatAmplitude : floatAmplitude);
                floatTransition.setToY((i % 2 == 0) ? floatAmplitude : -floatAmplitude);
                floatTransition.setAutoReverse(true);
                floatTransition.setCycleCount(Animation.INDEFINITE);
                floatTransition.play();
                activeFloatingTransitions.add(floatTransition);
            }
        }

        for (int i = 0; i < nodes.size() - 1; i++) {
            StackPane current = nodes.get(i);
            StackPane next = nodes.get(i + 1);
            double dy = Math.abs(next.getLayoutY() - current.getLayoutY());
            double rowTurnBoost = (dy > 8 && state.doublyLinked()) ? 6 : 0;
            double perEdgeDepth = dy > 8 ? curveDepth + 8 + rowTurnBoost : curveDepth;
            CubicCurve nextCurve = createRopeCurve(current, next, false, perEdgeDepth);
            nextCurve.getStyleClass().addAll("viz-rope", "next-pointer");
            Polygon nextArrow = createArrowForCurve(nextCurve, "next-pointer");
            targetPane.getChildren().addAll(nextCurve, nextArrow);

            if (state.doublyLinked()) {
                double prevSeparation = dy > 8 ? 9 : 3;
                CubicCurve prevCurve = createRopeCurve(next, current, true, perEdgeDepth + prevSeparation);
                prevCurve.getStyleClass().addAll("viz-rope", "prev-pointer");
                Polygon prevArrow = createArrowForCurve(prevCurve, "prev-pointer");
                targetPane.getChildren().addAll(prevCurve, prevArrow);
            }
        }

        if (!nodes.isEmpty()) {
            CubicCurve headPointer = new CubicCurve();
            headPointer.getStyleClass().addAll("viz-rope", "next-pointer");
            StackPane first = nodes.get(0);
            headPointer.startXProperty().set(40);
            headPointer.startYProperty().set(y + 20);
            headPointer.endXProperty().bind(nodeLeftX(first));
            headPointer.endYProperty().bind(nodeCenterY(first));
            headPointer.controlX1Property().set(50);
            headPointer.controlY1Property().set(y + 6);
            headPointer.controlX2Property().bind(nodeLeftX(first).subtract(16));
            headPointer.controlY2Property().bind(nodeCenterY(first).subtract(6));
            Polygon headArrow = createArrowForCurve(headPointer, "next-pointer");
            targetPane.getChildren().addAll(headPointer, headArrow);
        }

        Label nullLabel = new Label("Null");
        nullLabel.getStyleClass().add("viz-null-label");
        StackPane tailNode = nodes.get(nodes.size() - 1);
        double tailCenterY = tailNode.getLayoutY() + (tailNode.getHeight() > 0 ? tailNode.getHeight() / 2 : 20);
        double nullX = tailNode.getLayoutX() + (tailNode.getWidth() > 0 ? tailNode.getWidth() : 40) + 28;
        nullLabel.setLayoutX(nullX);
        nullLabel.setLayoutY(tailCenterY - 8);
        targetPane.getChildren().add(nullLabel);

        if (!nodes.isEmpty()) {
            StackPane tail = tailNode;
            CubicCurve tailPointer = new CubicCurve();
            tailPointer.getStyleClass().addAll("viz-rope", "next-pointer");
            tailPointer.startXProperty().bind(tail.layoutXProperty().add(tail.widthProperty()));
            tailPointer.startYProperty().bind(tail.layoutYProperty().add(tail.heightProperty().divide(2)));
            tailPointer.endXProperty().set(nullX - 2);
            tailPointer.endYProperty().set(tailCenterY + 1);
            tailPointer.controlX1Property().bind(tail.layoutXProperty().add(tail.widthProperty()).add(14));
            tailPointer.controlY1Property().bind(tail.layoutYProperty().add(tail.heightProperty().divide(2)).add(8));
            tailPointer.controlX2Property().set(nullX - 14);
            tailPointer.controlY2Property().set(tailCenterY - 6);
            Polygon tailArrow = createArrowForCurve(tailPointer, "next-pointer");
            targetPane.getChildren().addAll(tailPointer, tailArrow);
        }

        previousLinkedListNodeCount.put(state.name(), values.size());

        return y + (totalRows * rowHeight) + 18;
    }

    private CubicCurve createRopeCurve(StackPane fromNode, StackPane toNode, boolean returnArc, double curveDepth) {
        CubicCurve curve = new CubicCurve();
        DoubleBinding fromCenterX = nodeCenterX(fromNode);
        DoubleBinding toCenterX = nodeCenterX(toNode);
        DoubleBinding fromCenterY = nodeCenterY(fromNode);
        DoubleBinding toCenterY = nodeCenterY(toNode);
        DoubleBinding fromRight = nodeRightX(fromNode);
        DoubleBinding fromLeft = nodeLeftX(fromNode);
        DoubleBinding toLeft = nodeLeftX(toNode);
        DoubleBinding toRight = nodeRightX(toNode);

        curve.startXProperty().bind(Bindings.when(toCenterX.greaterThanOrEqualTo(fromCenterX)).then(fromRight).otherwise(fromLeft));
        curve.startYProperty().bind(fromCenterY);
        curve.endXProperty().bind(Bindings.when(toCenterX.greaterThanOrEqualTo(fromCenterX)).then(toLeft).otherwise(toRight));
        curve.endYProperty().bind(toCenterY);

        DoubleBinding deltaX = toCenterX.subtract(fromCenterX);
        DoubleBinding deltaY = toCenterY.subtract(fromCenterY);
        DoubleBinding absDeltaY = Bindings.createDoubleBinding(
            () -> Math.abs(deltaY.get()),
            deltaY
        );
        DoubleBinding absDeltaX = Bindings.createDoubleBinding(
            () -> Math.abs(deltaX.get()),
            deltaX
        );
        DoubleBinding horizontalSign = Bindings.createDoubleBinding(
            () -> deltaX.get() >= 0 ? 1.0 : -1.0,
            deltaX
        );
        DoubleBinding rowTurnWeight = Bindings.createDoubleBinding(
            () -> {
                double dy = absDeltaY.get();
                return Math.max(0.0, Math.min(1.0, dy / 28.0));
            },
            absDeltaY
        );

        DoubleBinding controlXOffset = Bindings.createDoubleBinding(
            () -> {
                double dx = absDeltaX.get();
                double rowTurn = rowTurnWeight.get();
                double base = Math.max(10.0, Math.min(26.0, dx * 0.24));
                return base + (rowTurn * 8.0);
            },
            absDeltaX,
            rowTurnWeight
        );
        DoubleBinding verticalBend = Bindings.createDoubleBinding(
            () -> {
                double rowTurn = rowTurnWeight.get();
                if (rowTurn < 0.08) {
                    return returnArc ? 2.4 : -2.4;
                }
                double bend = curveDepth * (0.62 + (rowTurn * 0.5));
                return returnArc ? bend : -bend;
            },
            rowTurnWeight
        );

        curve.controlX1Property().bind(curve.startXProperty().add(horizontalSign.multiply(controlXOffset)));
        curve.controlY1Property().bind(curve.startYProperty().add(verticalBend));
        curve.controlX2Property().bind(curve.endXProperty().subtract(horizontalSign.multiply(controlXOffset)));
        curve.controlY2Property().bind(curve.endYProperty().add(verticalBend));
        return curve;
    }

    private DoubleBinding nodeCenterX(StackPane node) {
        return node.layoutXProperty().add(node.translateXProperty()).add(node.widthProperty().divide(2));
    }

    private DoubleBinding nodeCenterY(StackPane node) {
        return node.layoutYProperty().add(node.translateYProperty()).add(node.heightProperty().divide(2));
    }

    private DoubleBinding nodeLeftX(StackPane node) {
        return node.layoutXProperty().add(node.translateXProperty());
    }

    private DoubleBinding nodeRightX(StackPane node) {
        return node.layoutXProperty().add(node.translateXProperty()).add(node.widthProperty());
    }

    private Polygon createArrowForCurve(CubicCurve curve, String directionClass) {
        Polygon arrow = new Polygon();
        arrow.getStyleClass().addAll("viz-arrowhead", directionClass);

        Runnable updateArrow = () -> {
            double endX = curve.getEndX();
            double endY = curve.getEndY();
            double refX = curve.getControlX2();
            double refY = curve.getControlY2();

            double dx = endX - refX;
            double dy = endY - refY;
            double len = Math.hypot(dx, dy);
            if (len < 0.001) {
                len = 1;
            }
            double ux = dx / len;
            double uy = dy / len;
            double px = -uy;
            double py = ux;

            double arrowLength = Math.max(8, Math.min(12, len * 0.24));
            double arrowWidth = Math.max(4, Math.min(6, arrowLength * 0.5));

            double bx = endX - (ux * arrowLength);
            double by = endY - (uy * arrowLength);

            arrow.getPoints().setAll(
                    endX, endY,
                    bx + (px * arrowWidth), by + (py * arrowWidth),
                    bx - (px * arrowWidth), by - (py * arrowWidth)
            );
        };

        curve.endXProperty().addListener((obs, oldVal, newVal) -> updateArrow.run());
        curve.endYProperty().addListener((obs, oldVal, newVal) -> updateArrow.run());
        curve.controlX2Property().addListener((obs, oldVal, newVal) -> updateArrow.run());
        curve.controlY2Property().addListener((obs, oldVal, newVal) -> updateArrow.run());
        updateArrow.run();
        return arrow;
    }

    private void stopFloatingTransitions() {
        for (TranslateTransition transition : activeFloatingTransitions) {
            transition.stop();
        }
        activeFloatingTransitions.clear();
    }

    private void renderCallStack(List<CallFrameState> frames) {
        renderCallStackOn(callStackBox, frames);
    }

    private void renderCallStackOn(VBox targetBox, List<CallFrameState> frames) {
        targetBox.getChildren().clear();
        targetBox.setAlignment(Pos.TOP_CENTER);
        targetBox.setFillWidth(false);
        if (!recursionDetected) {
            Label info = new Label("No recursion detected. Stack panel is active only for recursive code.");
            info.getStyleClass().add("viz-empty-label");
            targetBox.getChildren().add(info);
            previousFrameNames = List.of();
            previousStackDepth = 0;
            return;
        }

        if (frames == null || frames.isEmpty()) {
            Label empty = new Label("No call stack frames yet.");
            empty.getStyleClass().add("viz-empty-label");
            targetBox.getChildren().add(empty);
            previousFrameNames = List.of();
            previousStackDepth = 0;
            return;
        }

        List<StackHistoryItem> history = buildStackHistoryUntil(timelineIndex);
        int compareBaseStep = getCompareBaseStepIndex();
        Map<String, ValueChange> stepChanges = compareModeEnabled
            ? computeVariableChangesBetween(compareBaseStep, timelineIndex)
            : computeStepVariableChanges(timelineIndex);
        int topActiveInvocation = history.stream()
            .filter(StackHistoryItem::active)
            .mapToInt(StackHistoryItem::invocationId)
            .max()
            .orElse(-1);

        Label topMarker = new Label("TOP OF STACK");
        topMarker.getStyleClass().add("viz-stack-top-marker");
        VBox stackColumn = new VBox(6);
        stackColumn.getStyleClass().add("viz-stack-column");

        if (compareModeEnabled) {
            Label compareMarker = new Label("Comparing step " + (compareBaseStep + 1) + " -> " + (timelineIndex + 1));
            compareMarker.getStyleClass().add("viz-stack-compare-marker");
            targetBox.getChildren().add(compareMarker);
        }

        int newDepth = (int) history.stream().filter(StackHistoryItem::active).count();
        boolean stackGrew = newDepth > previousStackDepth;

        for (int i = history.size() - 1; i >= 0; i--) {
            StackHistoryItem item = history.get(i);
            VBox card = new VBox(4);
            card.getStyleClass().addAll("viz-stack-frame", "viz-stack-history-frame", "viz-stack-frame-block");
            card.setMinWidth(220);
            card.setPrefWidth(220);
            card.setMaxWidth(220);
            card.setMinHeight(56);
            if (item.active()) {
                card.getStyleClass().add("viz-stack-frame-active");
            } else {
                card.getStyleClass().add("viz-stack-history-inactive");
            }

            Label frameName = new Label(item.name() + "  #" + item.invocationId());
            frameName.getStyleClass().add("viz-stack-frame-title");
            card.getChildren().add(frameName);

            Label meta = new Label("Called at step " + item.firstSeenStep());
            meta.getStyleClass().add("viz-stack-history-meta");
            card.getChildren().add(meta);

            if (!item.active()) {
                card.getChildren().add(createStackBadge("RETURNED", "viz-stack-badge-returned"));
            }

            Label localHeader = new Label("Locals");
            localHeader.getStyleClass().add("viz-stack-locals-header");
            card.getChildren().add(localHeader);

            if (item.locals().isEmpty()) {
                Label noLocals = new Label("No locals");
                noLocals.getStyleClass().add("viz-stack-local-line");
                card.getChildren().add(noLocals);
            } else {
                for (Map.Entry<String, String> local : item.locals().entrySet()) {
                    ValueChange change = stepChanges.get(local.getKey());
                    HBox localRow = new HBox(6);
                    localRow.setAlignment(Pos.CENTER_LEFT);

                    Label localLine = new Label(buildStackVariableLine(local.getKey(), local.getValue(), change));
                    localLine.getStyleClass().add("viz-stack-local-line");
                    if (change != null) {
                        localLine.getStyleClass().add("viz-stack-local-line-changed");
                    }
                    localRow.getChildren().add(localLine);

                    if (change != null) {
                        localRow.getChildren().add(createStackBadge("UPDATED", "viz-stack-badge-updated"));
                    }
                    card.getChildren().add(localRow);
                }
            }

            if (stackGrew && item.active() && item.invocationId() == topActiveInvocation) {
                card.setOpacity(0);
                card.setTranslateY(-12);
                FadeTransition fade = new FadeTransition(Duration.millis(220), card);
                fade.setFromValue(0);
                fade.setToValue(1);
                TranslateTransition slide = new TranslateTransition(Duration.millis(220), card);
                slide.setFromY(-12);
                slide.setToY(0);
                fade.play();
                slide.play();
            }

            stackColumn.getChildren().add(card);
        }

        targetBox.getChildren().addAll(topMarker, stackColumn);

        previousStackDepth = newDepth;
        previousFrameNames = frames.stream().map(CallFrameState::name).toList();
    }

    private Label createStackBadge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("viz-stack-badge", styleClass);
        return badge;
    }

    private List<StackHistoryItem> buildStackHistoryUntil(int stepIndex) {
        if (timeline == null || timeline.isEmpty() || stepIndex < 0) {
            return List.of();
        }

        List<StackHistoryMutable> history = new ArrayList<>();
        Deque<Integer> activeInvocationIndices = new ArrayDeque<>();
        List<String> previousFrameNames = List.of();
        int invocationCounter = 0;

        int lastIndex = Math.min(stepIndex, timeline.size() - 1);
        for (int i = 0; i <= lastIndex; i++) {
            ExecutionSnapshot snapshot = timeline.get(i);
            List<CallFrameState> currentFrames = snapshot.callStack();
            List<String> currentFrameNames = currentFrames.stream().map(CallFrameState::name).toList();

            int commonPrefix = 0;
            int maxCommon = Math.min(previousFrameNames.size(), currentFrameNames.size());
            while (commonPrefix < maxCommon
                    && previousFrameNames.get(commonPrefix).equals(currentFrameNames.get(commonPrefix))) {
                commonPrefix++;
            }

            while (activeInvocationIndices.size() > commonPrefix) {
                int popIndex = activeInvocationIndices.removeLast();
                history.get(popIndex).active = false;
            }

            for (int frameIdx = commonPrefix; frameIdx < currentFrames.size(); frameIdx++) {
                invocationCounter++;
                CallFrameState frame = currentFrames.get(frameIdx);
                StackHistoryMutable item = new StackHistoryMutable(frame.name(), i + 1, invocationCounter);
                history.add(item);
                activeInvocationIndices.addLast(history.size() - 1);
            }

            int position = 0;
            for (Integer activeIndex : activeInvocationIndices) {
                if (position >= currentFrames.size()) {
                    break;
                }
                history.get(activeIndex).locals = new LinkedHashMap<>(currentFrames.get(position).locals());
                position++;
            }

            previousFrameNames = currentFrameNames;
        }

        List<StackHistoryItem> finalized = new ArrayList<>(history.size());
        for (StackHistoryMutable item : history) {
            finalized.add(new StackHistoryItem(
                    item.name,
                    item.firstSeenStep,
                    item.invocationId,
                    item.active,
                    new LinkedHashMap<>(item.locals)
            ));
        }
        return finalized;
    }

    private Map<String, ValueChange> computeStepVariableChanges(int stepIndex) {
        if (timeline == null || timeline.isEmpty() || stepIndex <= 0 || stepIndex >= timeline.size()) {
            return Map.of();
        }
        return computeVariableChangesBetween(stepIndex - 1, stepIndex);
    }

    private Map<String, ValueChange> computeVariableChangesBetween(int fromStepIndex, int toStepIndex) {
        if (timeline == null || timeline.isEmpty()) {
            return Map.of();
        }

        int safeFrom = Math.max(0, Math.min(fromStepIndex, timeline.size() - 1));
        int safeTo = Math.max(0, Math.min(toStepIndex, timeline.size() - 1));
        if (safeFrom == safeTo) {
            return Map.of();
        }

        Map<String, String> previousValues = new LinkedHashMap<>();
        for (VariableState previous : timeline.get(safeFrom).variables()) {
            previousValues.put(previous.name(), previous.value());
        }

        Map<String, String> currentValues = new LinkedHashMap<>();
        for (VariableState current : timeline.get(safeTo).variables()) {
            currentValues.put(current.name(), current.value());
        }

        Set<String> variableNames = new LinkedHashSet<>();
        variableNames.addAll(previousValues.keySet());
        variableNames.addAll(currentValues.keySet());

        Map<String, ValueChange> changes = new LinkedHashMap<>();
        for (String variableName : variableNames) {
            String oldValue = previousValues.getOrDefault(variableName, "(unset)");
            String newValue = currentValues.getOrDefault(variableName, "(unset)");
            if (!Objects.equals(oldValue, newValue)) {
                changes.put(variableName, new ValueChange(oldValue, newValue));
            }
        }
        return changes;
    }

    private int getCompareBaseStepIndex() {
        if (timeline == null || timeline.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min((int) Math.round(compareBaseSlider.getValue()), timeline.size() - 1));
    }

    private String buildStackVariableLine(String name, String value, ValueChange change) {
        String safeName = compactLabel(name, 16);
        if (change == null) {
            return safeName + " = " + compactLabel(value, 18);
        }
        return safeName + ": " + compactLabel(change.previous(), 10) + " -> " + compactLabel(change.current(), 10);
    }

    private record ExecutionSnapshot(
            int lineNumber,
            List<VariableState> variables,
            List<DataStructureState> dataStructures,
            List<CallFrameState> callStack
    ) {
    }

    private record VariableState(String name, String value, boolean isGarbage) {
    }

    private record CallFrameState(String name, Map<String, String> locals) {
    }

    private record StackHistoryItem(String name, int firstSeenStep, int invocationId, boolean active,
                                    Map<String, String> locals) {
    }

    private record ValueChange(String previous, String current) {
    }

    private static final class StackHistoryMutable {
        private final String name;
        private final int firstSeenStep;
        private final int invocationId;
        private boolean active = true;
        private Map<String, String> locals = new LinkedHashMap<>();

        private StackHistoryMutable(String name, int firstSeenStep, int invocationId) {
            this.name = name;
            this.firstSeenStep = firstSeenStep;
            this.invocationId = invocationId;
        }
    }

    private enum DataStructureType {
        ARRAY("Array"),
        ARRAY_LIST("ArrayList"),
        HASH_MAP("HashMap"),
        HASH_SET("HashSet"),
        LINKED_LIST("LinkedList");

        private final String label;

        DataStructureType(String label) {
            this.label = label;
        }
    }

    private record DataStructureState(
            String name,
            DataStructureType type,
            List<String> values,
            Map<String, String> entries,
            int capacity,
            boolean doublyLinked
    ) {
    }

    private static final class CodeTimelineEngine {
        private static final Pattern METHOD_DECLARATION_PATTERN = Pattern.compile(
                "^(?:public|private|protected|static|final|synchronized|native|abstract|\\s)+[A-Za-z_$][\\w$<>\\[\\]]*\\s+([A-Za-z_$][\\w$]*)\\s*\\([^;]*\\)\\s*\\{?");

        private CodeTimelineEngine() {
        }

        static boolean detectRecursion(String codeText) {
            String safeCode = codeText == null ? "" : codeText;
            String[] lines = safeCode.split("\\R", -1);

            String currentMethod = null;
            int methodBraceDepth = 0;

            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }

                if (currentMethod == null) {
                    Matcher declarationMatcher = METHOD_DECLARATION_PATTERN.matcher(line);
                    if (declarationMatcher.find()) {
                        currentMethod = declarationMatcher.group(1);
                        methodBraceDepth = countChar(line, '{') - countChar(line, '}');
                        continue;
                    }
                } else {
                    boolean likelySelfCall = line.contains(currentMethod + "(")
                            && !line.matches(".*\\b(class|new)\\s+" + Pattern.quote(currentMethod) + "\\b.*");
                    if (likelySelfCall) {
                        return true;
                    }

                    methodBraceDepth += countChar(line, '{');
                    methodBraceDepth -= countChar(line, '}');
                    if (methodBraceDepth <= 0) {
                        currentMethod = null;
                        methodBraceDepth = 0;
                    }
                }
            }
            return false;
        }

        static List<ExecutionSnapshot> buildSnapshots(String codeText) {
            String safeCode = codeText == null ? "" : codeText;
            String[] lines = safeCode.split("\\R", -1);
            List<ExecutionSnapshot> snapshots = new ArrayList<>();

            LinkedHashMap<String, VariableTracker> tracker = new LinkedHashMap<>();
            Deque<String> callStack = new ArrayDeque<>();
            callStack.push("main()");

            int scopeDepth = 0;

            for (int i = 0; i < lines.length; i++) {
                String rawLine = lines[i];
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("//")) {
                    snapshots.add(snapshotFor(i + 1, tracker, scopeDepth, callStack));
                    scopeDepth += countChar(line, '{');
                    scopeDepth = Math.max(0, scopeDepth - countChar(line, '}'));
                    continue;
                }

                scopeDepth = Math.max(0, scopeDepth - countChar(line, '}'));

                parseVariableMutation(line, scopeDepth, i, tracker);
                detectCallStackChange(line, callStack);
                scopeDepth += countChar(line, '{');

                snapshots.add(snapshotFor(i + 1, tracker, scopeDepth, callStack));
            }

            if (snapshots.isEmpty()) {
                snapshots.add(new ExecutionSnapshot(0, List.of(), List.of(), List.of(new CallFrameState("main()", Map.of()))));
            }
            return snapshots;
        }

        private static void parseVariableMutation(String line,
                                                  int scopeDepth,
                                                  int lineIndex,
                                                  LinkedHashMap<String, VariableTracker> tracker) {
            Matcher declaration = DECLARATION_PATTERN.matcher(line);
            if (declaration.matches()) {
                String name = declaration.group(1);
                String value = normalizeValue(declaration.group(2));
                VariableTracker variable = tracker.computeIfAbsent(name, key -> new VariableTracker(name));
                variable.value = value;
                variable.scopeDepth = scopeDepth;
                variable.lastTouchedLine = lineIndex;
                return;
            }

            Matcher assignment = ASSIGNMENT_PATTERN.matcher(line);
            if (assignment.matches()) {
                String target = assignment.group(1);
                String baseName = target.contains("[") ? target.substring(0, target.indexOf('[')) : target;
                String value = normalizeValue(assignment.group(2));

                VariableTracker variable = tracker.computeIfAbsent(baseName, key -> new VariableTracker(baseName));
                variable.value = value;
                variable.lastTouchedLine = lineIndex;
            }
        }

        private static void detectCallStackChange(String line, Deque<String> callStack) {
            String normalized = line.trim();
            if (normalized.startsWith("return") && callStack.size() > 1) {
                callStack.pop();
            }

            Matcher callMatcher = CALL_PATTERN.matcher(normalized);
            while (callMatcher.find()) {
                String callName = callMatcher.group(1);
                if (NON_CALL_KEYWORDS.contains(callName)) {
                    continue;
                }
                if (normalized.startsWith("class ") || normalized.startsWith("public class")) {
                    continue;
                }
                if (normalized.contains("new " + callName + "(")) {
                    continue;
                }
                String active = callStack.peek();
                String frameName = callName + "(...)";
                if (active == null || !active.equals(frameName)) {
                    callStack.push(frameName);
                }
                break;
            }

            if (normalized.endsWith("}") && callStack.size() > 1 && !normalized.contains("if") && !normalized.contains("for")) {
                callStack.pop();
            }
        }

        private static ExecutionSnapshot snapshotFor(int lineNumber,
                                                     LinkedHashMap<String, VariableTracker> tracker,
                                                     int scopeDepth,
                                                     Deque<String> callStack) {
            List<VariableState> variables = new ArrayList<>();
            List<DataStructureState> structures = new ArrayList<>();

            for (VariableTracker variable : tracker.values()) {
                boolean outOfScope = variable.scopeDepth > scopeDepth;
                boolean staleValue = (lineNumber - 1 - variable.lastTouchedLine) >= 5;
                boolean explicitGarbage = variable.value.equalsIgnoreCase("null") || variable.value.equalsIgnoreCase("garbage");
                boolean garbage = outOfScope || staleValue || explicitGarbage;

                variables.add(new VariableState(variable.name, variable.value, garbage));
                inferDataStructure(variable.name, variable.value).ifPresent(structures::add);
            }

            List<CallFrameState> frames = new ArrayList<>();
            List<String> stackCopy = new ArrayList<>(callStack);
            for (int i = stackCopy.size() - 1; i >= 0; i--) {
                String frameName = stackCopy.get(i);
                Map<String, String> locals = new LinkedHashMap<>();
                for (VariableState variable : variables) {
                    if (!variable.isGarbage()) {
                        locals.put(variable.name(), variable.value());
                    }
                    if (locals.size() >= 4) {
                        break;
                    }
                }
                frames.add(new CallFrameState(frameName, locals));
            }

            return new ExecutionSnapshot(lineNumber, variables, structures, frames);
        }

        private static Optional<DataStructureState> inferDataStructure(String name, String rawValue) {
            String value = rawValue == null ? "" : rawValue.trim();
            String lower = value.toLowerCase(Locale.ROOT);
            String nameLower = name.toLowerCase(Locale.ROOT);

            if (value.contains("->")) {
                boolean doublyLinked = value.contains("<->");
                String delimiter = doublyLinked ? "<->" : "->";
                return Optional.of(new DataStructureState(
                        name,
                        DataStructureType.LINKED_LIST,
                    Arrays.stream(value.split(Pattern.quote(delimiter))).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                        Map.of(),
                    0,
                    doublyLinked
                ));
            }

            if (lower.contains("linkedlist") || nameLower.contains("linked")) {
                List<String> linkedValues = parseLinkedValues(value);
                return Optional.of(new DataStructureState(
                        name,
                        DataStructureType.LINKED_LIST,
                        linkedValues,
                        Map.of(),
                        0,
                        lower.contains("double") || lower.contains("doubly")
                ));
            }

            if (lower.startsWith("[") && lower.endsWith("]")) {
                return Optional.of(new DataStructureState(
                        name,
                        nameLower.contains("list") ? DataStructureType.ARRAY_LIST : DataStructureType.ARRAY,
                        parseArrayValues(value),
                        Map.of(),
                    parseArrayValues(value).size(),
                    false
                ));
            }

            if (lower.contains("arraylist") || nameLower.contains("list")) {
                List<String> values = parseBracketValues(value);
                int capacity = Math.max(values.size(), values.size() + 2);
                return Optional.of(new DataStructureState(name, DataStructureType.ARRAY_LIST, values, Map.of(), capacity, false));
            }

            if (lower.contains("hashmap") || (value.startsWith("{") && value.contains("="))) {
                Map<String, String> entries = parseMapEntries(value);
                return Optional.of(new DataStructureState(name, DataStructureType.HASH_MAP, List.of(), entries, 4, false));
            }

            if (lower.contains("hashset") || (value.startsWith("{") && value.endsWith("}") && !value.contains("="))) {
                Map<String, String> entries = new LinkedHashMap<>();
                for (String item : parseSetValues(value)) {
                    entries.put(item, item);
                }
                return Optional.of(new DataStructureState(name, DataStructureType.HASH_SET, List.of(), entries, 4, false));
            }

            return Optional.empty();
        }

        private static List<String> parseLinkedValues(String value) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            if (value.contains("<->") || value.contains("->")) {
                String delimiter = value.contains("<->") ? "<->" : "->";
                return Arrays.stream(value.split(Pattern.quote(delimiter)))
                        .map(String::trim)
                        .filter(item -> !item.isBlank())
                        .toList();
            }

            List<String> fromBrackets = parseBracketValues(value);
            if (!fromBrackets.isEmpty()) {
                return fromBrackets;
            }

            int firstBrace = value.indexOf('{');
            int lastBrace = value.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                String body = value.substring(firstBrace + 1, lastBrace);
                return Arrays.stream(body.split(","))
                        .map(String::trim)
                        .filter(item -> !item.isBlank())
                        .toList();
            }
            return List.of();
        }

        private static List<String> parseArrayValues(String value) {
            if (value == null || value.length() < 2) {
                return List.of();
            }
            String body = value.substring(1, value.length() - 1).trim();
            if (body.isEmpty()) {
                return List.of();
            }
            return Arrays.stream(body.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }

        private static List<String> parseBracketValues(String value) {
            int first = value.indexOf('[');
            int last = value.lastIndexOf(']');
            if (first < 0 || last <= first) {
                return List.of();
            }
            return parseArrayValues(value.substring(first, last + 1));
        }

        private static Map<String, String> parseMapEntries(String value) {
            int first = value.indexOf('{');
            int last = value.lastIndexOf('}');
            if (first < 0 || last <= first) {
                return Map.of();
            }
            String body = value.substring(first + 1, last).trim();
            if (body.isEmpty()) {
                return Map.of();
            }

            LinkedHashMap<String, String> entries = new LinkedHashMap<>();
            String[] parts = body.split(",");
            for (String part : parts) {
                String candidate = part.trim();
                if (candidate.isEmpty()) {
                    continue;
                }
                int separator = candidate.indexOf('=');
                if (separator > 0) {
                    String key = candidate.substring(0, separator).trim();
                    String val = candidate.substring(separator + 1).trim();
                    entries.put(key, val);
                } else {
                    entries.put(candidate, candidate);
                }
            }
            return entries;
        }

        private static List<String> parseSetValues(String value) {
            int first = value.indexOf('{');
            int last = value.lastIndexOf('}');
            if (first < 0 || last <= first) {
                return List.of();
            }
            String body = value.substring(first + 1, last).trim();
            if (body.isEmpty()) {
                return List.of();
            }
            return Arrays.stream(body.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();
        }

        private static String normalizeValue(String value) {
            if (value == null || value.isBlank()) {
                return "(uninitialized)";
            }
            String normalized = value.trim();
            if (normalized.endsWith(";")) {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
            }
            return normalized;
        }

        private static int countChar(String line, char target) {
            int count = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == target) {
                    count++;
                }
            }
            return count;
        }

        private static final class VariableTracker {
            private final String name;
            private String value = "(uninitialized)";
            private int scopeDepth;
            private int lastTouchedLine;

            private VariableTracker(String name) {
                this.name = name;
            }
        }
    }
}
