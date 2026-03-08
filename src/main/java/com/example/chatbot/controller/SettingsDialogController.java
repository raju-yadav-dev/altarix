package com.example.chatbot.controller;

import com.example.chatbot.service.SettingsManager;
import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Controller for the settings dialog. Builds category pages dynamically
 * and reads/writes through SettingsManager.
 */
public class SettingsDialogController {
    private static final String APP_PROPERTIES_FILE = "app.properties";

    @FXML private ListView<SidebarEntry> categoryList;
    @FXML private StackPane pageContainer;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final SettingsManager settings = SettingsManager.getInstance();
    private final Map<String, VBox> pages = new LinkedHashMap<>();
    private Runnable onSave;
    private Consumer<BlurPreviewState> blurPreviewListener;
    private HostServices hostServices;
    private DialogMode dialogMode = DialogMode.PREFERENCES;

    @FXML
    public void initialize() {
        buildPages();
        categoryList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SidebarEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.label());
            }
        });

        categoryList.setItems(buildSidebarEntries(dialogMode));
        categoryList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                return;
            }
            showPage(selected.pageName());
        });

        saveButton.setOnAction(e -> doSave());
        cancelButton.setOnAction(e -> closeDialog());

        selectFirstPage();
    }

    public void setOnSave(Runnable onSave) {
        this.onSave = onSave;
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public void setBlurPreviewListener(Consumer<BlurPreviewState> blurPreviewListener) {
        this.blurPreviewListener = blurPreviewListener;
    }

    public void setDialogMode(DialogMode dialogMode) {
        this.dialogMode = dialogMode == null ? DialogMode.PREFERENCES : dialogMode;
        if (categoryList != null) {
            categoryList.setItems(buildSidebarEntries(this.dialogMode));
            selectFirstPage();
        }
    }

    // ================= PAGE FACTORY =================
    private void buildPages() {
        pages.put("Appearance", buildAppearancePage());
        pages.put("Chat Behavior", buildChatPage());
        pages.put("Privacy", buildPrivacyPage());
        pages.put("Advanced", buildAdvancedPage());

        pages.put("Code Execution", buildExecutionPage());
        pages.put("Terminal", buildTerminalPage());
        pages.put("Supporting language", buildRuntimePage());
        pages.put("AI Model", buildAIPage());
    }

    private ObservableList<SidebarEntry> buildSidebarEntries(DialogMode mode) {
        if (mode == DialogMode.SETTINGS) {
            return FXCollections.observableArrayList(
                    SidebarEntry.page("Code Execution"),
                    SidebarEntry.page("Terminal"),
                    SidebarEntry.page("Supporting language"),
                    SidebarEntry.page("AI Model")
            );
        }
        return FXCollections.observableArrayList(
                SidebarEntry.page("Appearance"),
                SidebarEntry.page("Chat Behavior"),
                SidebarEntry.page("Privacy"),
                SidebarEntry.page("Advanced")
        );
    }

    private void selectFirstPage() {
        for (SidebarEntry entry : categoryList.getItems()) {
            categoryList.getSelectionModel().select(entry);
            return;
        }
    }

    private void showPage(String name) {
        if (name == null) return;
        VBox page = pages.get(name);
        if (page == null) return;
        pageContainer.getChildren().setAll(page);
    }

    // ================= APPEARANCE =================
    private VBox buildAppearancePage() {
        VBox page = createPage("Appearance");

        // Theme is handled by main menu, just show current
        Label themeNote = new Label("Theme is controlled from the Settings menu in the title bar.");
        themeNote.setWrapText(true);
        themeNote.getStyleClass().add("settings-hint");
        page.getChildren().add(themeNote);

        page.getChildren().add(createSpinnerRow("UI Font Size", "appearance.uiFontSize", 8, 24, settings.getInt("appearance.uiFontSize", 14)));
        page.getChildren().add(createSpinnerRow("Chat Font Size", "appearance.chatFontSize", 8, 24, settings.getInt("appearance.chatFontSize", 14)));
        page.getChildren().add(createSpinnerRow("Code Block Font Size", "appearance.codeFontSize", 8, 24, settings.getInt("appearance.codeFontSize", 13)));
        page.getChildren().add(createSpinnerRow("Terminal Font Size", "appearance.terminalFontSize", 8, 24, settings.getInt("appearance.terminalFontSize", 13)));

        CheckBox blurEnabled = new CheckBox();
        blurEnabled.setSelected(settings.getBoolean("appearance.modalBlurEnabled", true));
        blurEnabled.getStyleClass().add("settings-checkbox");

        HBox blurToggleRow = new HBox(12);
        blurToggleRow.setAlignment(Pos.CENTER_LEFT);
        Label blurToggleLabel = new Label("Blur background when dialogs open");
        blurToggleLabel.setMinWidth(200);
        blurToggleLabel.getStyleClass().add("settings-label");
        Region blurToggleSpacer = new Region();
        HBox.setHgrow(blurToggleSpacer, Priority.ALWAYS);
        blurToggleRow.getChildren().addAll(blurToggleLabel, blurToggleSpacer, blurEnabled);
        blurToggleRow.setUserData(new SettingBinding("appearance.modalBlurEnabled", blurEnabled::isSelected));
        page.getChildren().add(blurToggleRow);

        HBox blurLevelRow = new HBox(12);
        blurLevelRow.setAlignment(Pos.CENTER_LEFT);
        Label blurLevelLabel = new Label("Dialog blur level");
        blurLevelLabel.setMinWidth(140);
        blurLevelLabel.getStyleClass().add("settings-label");
        Slider blurLevelSlider = new Slider(0, 12.0, settings.getDouble("appearance.modalBlurRadius", 5.5));
        blurLevelSlider.setMajorTickUnit(3);
        blurLevelSlider.setMinorTickCount(2);
        blurLevelSlider.setShowTickLabels(true);
        blurLevelSlider.setShowTickMarks(true);
        blurLevelSlider.getStyleClass().add("settings-slider");
        blurLevelSlider.disableProperty().bind(blurEnabled.selectedProperty().not());
        HBox.setHgrow(blurLevelSlider, Priority.ALWAYS);
        Label blurLevelValue = new Label(String.format("%.1f", blurLevelSlider.getValue()));
        blurLevelValue.getStyleClass().add("settings-value");
        blurLevelValue.setMinWidth(34);
        blurLevelSlider.valueProperty().addListener((obs, o, n) -> blurLevelValue.setText(String.format("%.1f", n.doubleValue())));
        blurEnabled.selectedProperty().addListener((obs, oldVal, enabled) ->
            notifyBlurPreview(enabled, blurLevelSlider.getValue()));
        blurLevelSlider.valueProperty().addListener((obs, oldVal, newVal) ->
            notifyBlurPreview(blurEnabled.isSelected(), newVal.doubleValue()));
        blurLevelRow.getChildren().addAll(blurLevelLabel, blurLevelSlider, blurLevelValue);
        blurLevelRow.setUserData(new SettingBinding("appearance.modalBlurRadius", blurLevelSlider::getValue));
        page.getChildren().add(blurLevelRow);

        Label blurHint = new Label("Set to 0.0 for no blur while keeping dialogs functional.");
        blurHint.setWrapText(true);
        blurHint.getStyleClass().add("settings-hint");
        page.getChildren().add(blurHint);

        // Initialize preview with current values when Appearance page is created.
        notifyBlurPreview(blurEnabled.isSelected(), blurLevelSlider.getValue());
        return page;
    }

    // ================= CHAT BEHAVIOR =================
    private VBox buildChatPage() {
        VBox page = createPage("Chat Behavior");
        page.getChildren().add(createComboRow("Response Style", "chat.responseStyle",
                new String[]{"Concise", "Detailed", "Step-by-step"},
                settings.getString("chat.responseStyle", "Detailed")));
        page.getChildren().add(createToggleRow("Enable streaming responses", "chat.streamingEnabled", settings.getBoolean("chat.streamingEnabled", true)));
        page.getChildren().add(createToggleRow("Auto-scroll to newest message", "chat.autoScroll", settings.getBoolean("chat.autoScroll", true)));
        page.getChildren().add(createToggleRow("Enable chat history", "chat.historyEnabled", settings.getBoolean("chat.historyEnabled", true)));
        return page;
    }

    // ================= CODE EXECUTION =================
    private VBox buildExecutionPage() {
        VBox page = createPage("Code Execution");
        page.getChildren().add(createSpinnerRow("Execution timeout (seconds)", "execution.timeoutSeconds", 1, 120, settings.getInt("execution.timeoutSeconds", 10)));
        page.getChildren().add(createSpinnerRow("Max terminal output size", "execution.maxOutputSize", 1000, 500000, settings.getInt("execution.maxOutputSize", 50000)));
        page.getChildren().add(createToggleRow("Auto-clean temporary files", "execution.autoCleanTempFiles", settings.getBoolean("execution.autoCleanTempFiles", true)));
        page.getChildren().add(createToggleRow("Confirm before running code", "execution.confirmBeforeRun", settings.getBoolean("execution.confirmBeforeRun", false)));
        return page;
    }

    // ================= TERMINAL =================
    private VBox buildTerminalPage() {
        VBox page = createPage("Terminal");
        page.getChildren().add(createComboRow("Default shell", "terminal.defaultShell",
            new String[]{"Cortex", "cmd", "powershell", "bash"},
            settings.getString("terminal.defaultShell", "Cortex")));
        page.getChildren().add(createToggleRow("Clear terminal before running code", "terminal.clearBeforeRun", settings.getBoolean("terminal.clearBeforeRun", false)));
        page.getChildren().add(createSpinnerRow("Scrollback buffer size", "terminal.scrollbackSize", 1000, 100000, settings.getInt("terminal.scrollbackSize", 10000)));
        page.getChildren().add(createToggleRow("Show execution time", "terminal.showExecutionTime", settings.getBoolean("terminal.showExecutionTime", true)));
        return page;
    }

    // ================= SUPPORTING LANGUAGE =================
    private VBox buildRuntimePage() {
        VBox page = createPage("Supporting language");
        Label hint = new Label("Set custom paths for language runtimes not found in your system PATH.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-hint");
        page.getChildren().add(hint);

        page.getChildren().add(createPathRow("Python path", "runtime.pythonPath"));
        page.getChildren().add(createPathRow("Node.js path", "runtime.nodePath"));
        page.getChildren().add(createPathRow("Java path", "runtime.javaPath"));
        page.getChildren().add(createPathRow("GCC / G++ path", "runtime.gccPath"));
        page.getChildren().add(createPathRow("TypeScript (ts-node) path", "runtime.tsNodePath"));
        page.getChildren().add(createPathRow("Ruby path", "runtime.rubyPath"));
        page.getChildren().add(createPathRow("PHP path", "runtime.phpPath"));
        page.getChildren().add(createPathRow("Lua path", "runtime.luaPath"));
        page.getChildren().add(createPathRow("Perl path", "runtime.perlPath"));
        page.getChildren().add(createPathRow("Rscript path", "runtime.rPath"));
        page.getChildren().add(createPathRow("Dart path", "runtime.dartPath"));
        page.getChildren().add(createPathRow("Groovy path", "runtime.groovyPath"));
        page.getChildren().add(createPathRow("Swift path", "runtime.swiftPath"));
        page.getChildren().add(createPathRow("Julia path", "runtime.juliaPath"));
        return page;
    }

    // ================= AI MODEL =================
    private VBox buildAIPage() {
        VBox page = createPage("AI Model");

        page.getChildren().add(createSecretFieldRow(
            "API key",
            "ai.apiKey",
            settings.getString("ai.apiKey", ""),
            "sk-..."
        ));
        page.getChildren().add(createTextFieldRow(
            "Base URL",
            "ai.baseUrl",
            settings.getString("ai.baseUrl", "https://api.openai.com"),
            "https://api.openai.com"
        ));
        page.getChildren().add(createTextFieldRow(
            "Model name",
            "ai.modelName",
            settings.getString("ai.modelName", "gpt-4.1-mini"),
            "gpt-4.1-mini"
        ));

        // Temperature slider 0.0 – 2.0
        HBox tempRow = new HBox(12);
        tempRow.setAlignment(Pos.CENTER_LEFT);
        Label tempLabel = new Label("Temperature");
        tempLabel.setMinWidth(140);
        tempLabel.getStyleClass().add("settings-label");
        Slider tempSlider = new Slider(0, 2.0, settings.getDouble("ai.temperature", 0.4));
        tempSlider.setMajorTickUnit(0.5);
        tempSlider.setMinorTickCount(4);
        tempSlider.setShowTickLabels(true);
        tempSlider.setShowTickMarks(true);
        tempSlider.getStyleClass().add("settings-slider");
        HBox.setHgrow(tempSlider, Priority.ALWAYS);
        Label tempValue = new Label(String.format("%.1f", tempSlider.getValue()));
        tempValue.setMinWidth(30);
        tempSlider.valueProperty().addListener((obs, o, n) -> tempValue.setText(String.format("%.1f", n.doubleValue())));
        tempRow.getChildren().addAll(tempLabel, tempSlider, tempValue);
        tempRow.setUserData(new SettingBinding("ai.temperature", () -> tempSlider.getValue()));
        page.getChildren().add(tempRow);

        page.getChildren().add(createSpinnerRow("Max tokens", "ai.maxTokens", 256, 128000, settings.getInt("ai.maxTokens", 4096)));

        // System prompt
        VBox promptBox = new VBox(4);
        Label promptLabel = new Label("Custom system prompt");
        promptLabel.getStyleClass().add("settings-label");
        TextArea promptArea = new TextArea(settings.getString("ai.systemPrompt", ""));
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(4);
        promptArea.getStyleClass().add("settings-textarea");
        promptArea.setPromptText("Leave empty to use default Cortex system prompt");
        promptBox.getChildren().addAll(promptLabel, promptArea);
        promptBox.setUserData(new SettingBinding("ai.systemPrompt", promptArea::getText));
        page.getChildren().add(promptBox);

        return page;
    }

    private HBox createTextFieldRow(String label, String key, String current, String prompt) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(200);
        lbl.getStyleClass().add("settings-label");

        TextField field = new TextField(current == null ? "" : current);
        field.setPromptText(prompt == null ? "" : prompt);
        field.getStyleClass().add("settings-text-field");
        HBox.setHgrow(field, Priority.ALWAYS);

        row.getChildren().addAll(lbl, field);
        row.setUserData(new SettingBinding(key, field::getText));
        return row;
    }

    private HBox createSecretFieldRow(String label, String key, String current, String prompt) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(200);
        lbl.getStyleClass().add("settings-label");

        PasswordField field = new PasswordField();
        field.setText(current == null ? "" : current);
        field.setPromptText(prompt == null ? "" : prompt);
        field.getStyleClass().add("settings-text-field");
        HBox.setHgrow(field, Priority.ALWAYS);

        row.getChildren().addAll(lbl, field);
        row.setUserData(new SettingBinding(key, field::getText));
        return row;
    }

    // ================= PRIVACY =================
    private VBox buildPrivacyPage() {
        VBox page = createPage("Privacy");
        page.getChildren().add(createToggleRow("Save chat history", "privacy.saveChatHistory", settings.getBoolean("privacy.saveChatHistory", true)));

        Button clearConvButton = new Button("Clear All Conversations");
        clearConvButton.getStyleClass().add("settings-danger-button");
        clearConvButton.setOnAction(e -> {
            settings.clearConversationData();
            showSettingsToast(clearConvButton, "Conversations cleared");
        });

        Button clearCacheButton = new Button("Clear Cache");
        clearCacheButton.getStyleClass().add("settings-danger-button");
        clearCacheButton.setOnAction(e -> {
            settings.clearCache();
            showSettingsToast(clearCacheButton, "Cache cleared");
        });

        HBox actionRow = new HBox(10, clearConvButton, clearCacheButton);
        actionRow.setPadding(new Insets(8, 0, 0, 0));
        page.getChildren().add(actionRow);
        return page;
    }

    // ================= ADVANCED =================
    private VBox buildAdvancedPage() {
        VBox page = createPage("Advanced");
        page.getChildren().add(createToggleRow("Enable debug logs", "advanced.debugLogs", settings.getBoolean("advanced.debugLogs", false)));
        page.getChildren().add(createToggleRow("Enable experimental features", "advanced.experimentalFeatures", settings.getBoolean("advanced.experimentalFeatures", false)));
        return page;
    }

    // ================= ROW BUILDERS =================
    private VBox createPage(String title) {
        VBox page = new VBox(12);
        page.getStyleClass().add("settings-page");
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-page-title");
        page.getChildren().add(heading);
        return page;
    }

    private HBox createToggleRow(String label, String key, boolean current) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(200);
        lbl.getStyleClass().add("settings-label");
        CheckBox cb = new CheckBox();
        cb.setSelected(current);
        cb.getStyleClass().add("settings-checkbox");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, cb);
        row.setUserData(new SettingBinding(key, cb::isSelected));
        return row;
    }

    private HBox createSpinnerRow(String label, String key, int min, int max, int current) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(200);
        lbl.getStyleClass().add("settings-label");
        Spinner<Integer> spinner = new Spinner<>(min, max, current);
        spinner.setEditable(true);
        spinner.setPrefWidth(120);
        spinner.getStyleClass().add("settings-spinner");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, spinner);
        row.setUserData(new SettingBinding(key, spinner::getValue));
        return row;
    }

    private HBox createComboRow(String label, String key, String[] options, String current) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(200);
        lbl.getStyleClass().add("settings-label");
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(options));
        combo.setValue(current);
        combo.setPrefWidth(160);
        combo.getStyleClass().add("settings-combo");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, combo);
        row.setUserData(new SettingBinding(key, combo::getValue));
        return row;
    }

    private HBox createPathRow(String label, String key) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(140);
        lbl.getStyleClass().add("settings-label");
        TextField field = new TextField(settings.getString(key, ""));
        field.setPromptText("Auto-detect");
        field.getStyleClass().add("settings-text-field");
        HBox.setHgrow(field, Priority.ALWAYS);

        Button browse = new Button("Browse");
        browse.getStyleClass().add("settings-browse-button");
        browse.setFocusTraversable(false);
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select " + label);
            Stage stage = (Stage) browse.getScene().getWindow();
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                field.setText(file.getAbsolutePath());
            }
        });

        row.getChildren().addAll(lbl, field, browse);
        row.setUserData(new SettingBinding(key, field::getText));
        return row;
    }

    // ================= SAVE / CLOSE =================
    private void doSave() {
        // Walk through all pages and collect values
        for (VBox page : pages.values()) {
            collectBindings(page);
        }
        persistAiConfigToResourceProperties();
        settings.save();
        if (onSave != null) {
            onSave.run();
        }
        closeDialog();
    }

    private void collectBindings(Node node) {
        if (node.getUserData() instanceof SettingBinding binding) {
            Object value = binding.valueGetter.get();
            settings.set(binding.key, value);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectBindings(child);
            }
        }
    }

    private void closeDialog() {
        restoreSavedBlurPreview();
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void notifyBlurPreview(boolean blurEnabled, double blurRadius) {
        if (blurPreviewListener == null) {
            return;
        }
        double clamped = Math.max(0.0, Math.min(12.0, blurRadius));
        blurPreviewListener.accept(new BlurPreviewState(blurEnabled, clamped));
    }

    private void restoreSavedBlurPreview() {
        notifyBlurPreview(
                settings.getBoolean("appearance.modalBlurEnabled", true),
                settings.getDouble("appearance.modalBlurRadius", 5.5)
        );
    }

    private void persistAiConfigToResourceProperties() {
        Path resourcePath = resolveResourceAppPropertiesPath();
        if (resourcePath == null) {
            return;
        }

        Properties properties = new Properties();
        if (Files.isRegularFile(resourcePath)) {
            try (InputStream input = Files.newInputStream(resourcePath)) {
                properties.load(input);
            } catch (IOException ignored) {
                // Keep best-effort behavior and overwrite using current settings values.
            }
        }

        properties.setProperty("past_api", settings.getString("ai.apiKey", "").trim());
        properties.setProperty("openai_base_url", settings.getString("ai.baseUrl", "https://api.openai.com").trim());
        properties.setProperty("openai_model", settings.getString("ai.modelName", "gpt-4.1-mini").trim());

        try {
            Files.createDirectories(resourcePath.getParent());
            try (OutputStream output = Files.newOutputStream(resourcePath)) {
                properties.store(output, "Updated from Settings > AI Model");
            }
        } catch (IOException ignored) {
            // Avoid blocking Save if resource file write fails.
        }
    }

    private Path resolveResourceAppPropertiesPath() {
        try {
            Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            Path projectDir;
            if ("ai-project".equalsIgnoreCase(userDir.getFileName() != null ? userDir.getFileName().toString() : "")) {
                projectDir = userDir;
            } else {
                Path nested = userDir.resolve("ai-project");
                projectDir = Files.isDirectory(nested) ? nested : userDir;
            }
            return projectDir.resolve("src").resolve("main").resolve("resources").resolve(APP_PROPERTIES_FILE)
                    .toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showSettingsToast(Node anchor, String message) {
        Tooltip tip = new Tooltip(message);
        tip.setAutoHide(true);
        tip.show(anchor, 
            anchor.localToScreen(anchor.getBoundsInLocal()).getMinX(),
            anchor.localToScreen(anchor.getBoundsInLocal()).getMaxY() + 4);
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> tip.hide());
        pause.play();
    }

    // ================= BINDING RECORD =================
    private record SettingBinding(String key, java.util.function.Supplier<Object> valueGetter) {}

    public record BlurPreviewState(boolean enabled, double radius) {}

    private record SidebarEntry(String label, String pageName, boolean groupHeader) {
        private static SidebarEntry page(String name) {
            return new SidebarEntry(name, name, false);
        }
    }

    public enum DialogMode {
        PREFERENCES,
        SETTINGS
    }
}
