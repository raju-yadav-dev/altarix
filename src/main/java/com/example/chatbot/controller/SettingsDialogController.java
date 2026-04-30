package com.example.chatbot.controller;

import com.example.chatbot.service.AiProviderSetupSupport;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        pages.put("Code Visualizer", buildCodeVisualizerPage());
        pages.put("Terminal", buildTerminalPage());
        pages.put("Supporting language", buildRuntimePage());
        pages.put("AI Model", buildAIPage());
    }

    private ObservableList<SidebarEntry> buildSidebarEntries(DialogMode mode) {
        if (mode == DialogMode.SETTINGS) {
            return FXCollections.observableArrayList(
                    SidebarEntry.page("Code Execution"),
                SidebarEntry.page("Code Visualizer"),
                    SidebarEntry.page("Terminal"),
                    SidebarEntry.page("Supporting language"),
                    SidebarEntry.page("AI Model")
            );
        }
        return FXCollections.observableArrayList(
                SidebarEntry.page("Appearance"),
                SidebarEntry.page("Chat Behavior"),
            SidebarEntry.page("Code Visualizer"),
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

        private VBox buildCodeVisualizerPage() {
        VBox page = createPage("Code Visualizer");
        Label hint = new Label("Control section popout dialogs and linked-list visualization behavior.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-hint");
        page.getChildren().add(hint);

        page.getChildren().add(createToggleRow(
            "Enable section popout icons",
            "codeVisualizer.enableSectionPopout",
            settings.getBoolean("codeVisualizer.enableSectionPopout", true)
        ));

        page.getChildren().add(createComboRow(
            "Default variable shape",
            "codeVisualizer.defaultVariableShape",
            new String[]{"Bubble", "Rectangle"},
            settings.getString("codeVisualizer.defaultVariableShape", "Bubble")
        ));

        page.getChildren().add(createSpinnerRow(
            "Visualizer font size",
            "codeVisualizer.fontSize",
            10,
            18,
            settings.getInt("codeVisualizer.fontSize", 12)
        ));

        page.getChildren().add(createComboRow(
            "Visualizer text color",
            "codeVisualizer.textColorMode",
            new String[]{"Default", "High Contrast", "Soft"},
            settings.getString("codeVisualizer.textColorMode", "Default")
        ));

        page.getChildren().add(createComboRow(
            "Playback speed",
            "codeVisualizer.playbackSpeed",
            new String[]{"Slow", "Normal", "Fast"},
            settings.getString("codeVisualizer.playbackSpeed", "Normal")
        ));

        page.getChildren().add(createToggleRow(
            "Enable stack-focus mode by default",
            "codeVisualizer.stackFocusDefault",
            settings.getBoolean("codeVisualizer.stackFocusDefault", false)
        ));

        page.getChildren().add(createToggleRow(
            "Linked-list floating animation",
            "codeVisualizer.linkedListFloatingEnabled",
            settings.getBoolean("codeVisualizer.linkedListFloatingEnabled", true)
        ));

        page.getChildren().add(createToggleRow(
            "Show wrapped row markers",
            "codeVisualizer.linkedListRowMarkersEnabled",
            settings.getBoolean("codeVisualizer.linkedListRowMarkersEnabled", true)
        ));

        page.getChildren().add(createToggleRow(
            "Show next/prev legend",
            "codeVisualizer.linkedListLegendEnabled",
            settings.getBoolean("codeVisualizer.linkedListLegendEnabled", true)
        ));

        return page;
        }

    // ================= TERMINAL =================
    private VBox buildTerminalPage() {
        VBox page = createPage("Terminal");
        page.getChildren().add(createComboRow("Default shell", "terminal.defaultShell",
            new String[]{"altarix", "cmd", "powershell", "bash"},
            settings.getString("terminal.defaultShell", "altarix")));
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

        Label setupsHint = new Label(
                "Add one or more provider setups. Each setup can have its own API keys, base URL, and model name. " +
                "When Input mode default is Best, Altarix automatically uses the most suitable configured provider."
        );
        setupsHint.setWrapText(true);
        setupsHint.getStyleClass().add("settings-hint");
        page.getChildren().add(setupsHint);
        page.getChildren().add(createProviderSetupsSection());
        page.getChildren().add(createComboRow(
                "Input mode default",
                "chat.inputModeDefault",
                new String[]{"Best", "Groq", "Google Vision", "Leonardo", "Freepik"},
                settings.getString("chat.inputModeDefault", "Best")
        ));

        // Temperature slider 0.0 â€“ 2.0
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
        promptArea.setPromptText("Leave empty to use default Altarix system prompt");
        promptBox.getChildren().addAll(promptLabel, promptArea);
        promptBox.setUserData(new SettingBinding("ai.systemPrompt", promptArea::getText));
        page.getChildren().add(promptBox);

        return page;
    }

    private VBox createProviderSetupsSection() {
        VBox wrapper = new VBox(10);
        VBox setupsContainer = new VBox(12);
        setupsContainer.setFillWidth(true);

        List<AiProviderSetupSupport.ProviderSetup> setups = AiProviderSetupSupport.loadFromSettings(settings);
        if (setups.isEmpty()) {
            setups = List.of(AiProviderSetupSupport.defaultSetup());
        }
        for (AiProviderSetupSupport.ProviderSetup setup : setups) {
            setupsContainer.getChildren().add(createProviderSetupCard(setup, setupsContainer));
        }

        Button addSetupButton = new Button("+ Add provider setup");
        addSetupButton.setFocusTraversable(false);
        addSetupButton.getStyleClass().add("settings-action-button");
        addSetupButton.setOnAction(event ->
                setupsContainer.getChildren().add(createProviderSetupCard(AiProviderSetupSupport.defaultSetup(), setupsContainer)));

        wrapper.getChildren().addAll(setupsContainer, addSetupButton);
        wrapper.setUserData(new SettingBinding(
                AiProviderSetupSupport.SETTINGS_KEY,
                () -> AiProviderSetupSupport.toJson(collectProviderSetups(setupsContainer))
        ));
        return wrapper;
    }

    private VBox createProviderSetupCard(AiProviderSetupSupport.ProviderSetup initialSetup, VBox owner) {
        AiProviderSetupSupport.ProviderDefinition initialDefinition =
                AiProviderSetupSupport.definitionForId(initialSetup.providerId());

        VBox card = new VBox(10);
        card.getStyleClass().add("settings-provider-card");

        Label titleLabel = new Label(initialDefinition.displayName() + " setup");
        titleLabel.getStyleClass().add("settings-card-title");

        Button removeSetupButton = new Button("Remove");
        removeSetupButton.setFocusTraversable(false);
        removeSetupButton.getStyleClass().add("settings-browse-button");

        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        headerRow.getChildren().addAll(titleLabel, headerSpacer, removeSetupButton);

        TextField providerField = new TextField(initialDefinition.displayName());
        providerField.setPromptText(AiProviderSetupSupport.providerNamePrompt());
        providerField.getStyleClass().add("settings-text-field");
        HBox.setHgrow(providerField, Priority.ALWAYS);
        providerField.getProperties().put("resolvedProviderId", initialDefinition.id());

        HBox providerRow = createUnboundTextFieldRow("Provider name", providerField);

        Label providerHint = new Label("Type a supported provider such as "
                + AiProviderSetupSupport.providerNamePrompt() + ".");
        providerHint.getStyleClass().add("settings-hint");
        providerHint.setWrapText(true);

        Label apiKeysLabel = new Label("API keys");
        apiKeysLabel.getStyleClass().add("settings-label");

        Label apiKeysHint = new Label("Add one or more keys for this provider.");
        apiKeysHint.getStyleClass().add("settings-hint");
        apiKeysHint.setWrapText(true);

        VBox keysContainer = new VBox(6);
        keysContainer.setFillWidth(true);
        List<String> initialKeys = initialSetup.apiKeys().isEmpty() ? List.of("") : initialSetup.apiKeys();
        for (String keyValue : initialKeys) {
            keysContainer.getChildren().add(createApiKeyEditorRow(keyValue, keysContainer));
        }

        Button addKeyButton = new Button("+");
        addKeyButton.setFocusTraversable(false);
        addKeyButton.getStyleClass().add("settings-browse-button");
        addKeyButton.setOnAction(event -> keysContainer.getChildren().add(createApiKeyEditorRow("", keysContainer)));

        HBox addKeyRow = new HBox(addKeyButton);
        addKeyRow.setAlignment(Pos.CENTER_LEFT);

        TextField baseUrlField = new TextField(
            initialSetup.baseUrl()
        );
        baseUrlField.setPromptText(initialDefinition.defaultBaseUrl());
        baseUrlField.getStyleClass().add("settings-text-field");
        HBox.setHgrow(baseUrlField, Priority.ALWAYS);

        HBox baseUrlRow = createUnboundTextFieldRow("Base URL", baseUrlField);

        TextField modelNameField = new TextField(
            initialSetup.modelName()
        );
        modelNameField.setPromptText(initialDefinition.defaultModelName());
        modelNameField.getStyleClass().add("settings-text-field");
        HBox.setHgrow(modelNameField, Priority.ALWAYS);

        HBox modelRow = createUnboundTextFieldRow("Model name", modelNameField);

        ProviderSetupEditor editor = new ProviderSetupEditor(card, providerField, keysContainer, baseUrlField, modelNameField);
        card.getProperties().put("providerSetupEditor", editor);

        final AiProviderSetupSupport.ProviderDefinition[] previousDefinition = {initialDefinition};
        providerField.textProperty().addListener((obs, oldValue, newValue) -> {
            AiProviderSetupSupport.ProviderDefinition nextDefinition =
                    AiProviderSetupSupport.tryDefinitionForInput(newValue);
            if (nextDefinition == null) {
                titleLabel.setText("Provider setup");
                return;
            }
            applyProviderDefaults(baseUrlField, modelNameField, previousDefinition[0], nextDefinition);
            titleLabel.setText(nextDefinition.displayName() + " setup");
            previousDefinition[0] = nextDefinition;
            providerField.getProperties().put("resolvedProviderId", nextDefinition.id());
        });
        providerField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                return;
            }
            AiProviderSetupSupport.ProviderDefinition definition =
                    AiProviderSetupSupport.tryDefinitionForInput(providerField.getText());
            if (definition != null) {
                providerField.setText(definition.displayName());
            }
        });

        removeSetupButton.setOnAction(event -> {
            owner.getChildren().remove(card);
            if (owner.getChildren().isEmpty()) {
                owner.getChildren().add(createProviderSetupCard(AiProviderSetupSupport.defaultSetup(), owner));
            }
        });

        card.getChildren().addAll(
                headerRow,
                providerRow,
                providerHint,
                apiKeysLabel,
                apiKeysHint,
                keysContainer,
                addKeyRow,
                baseUrlRow,
                modelRow
        );
        return card;
    }

    private HBox createUnboundTextFieldRow(String labelText, TextField field) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(labelText);
        label.setMinWidth(200);
        label.getStyleClass().add("settings-label");
        row.getChildren().addAll(label, field);
        return row;
    }

    private void applyProviderDefaults(TextField baseUrlField,
                                       TextField modelNameField,
                                       AiProviderSetupSupport.ProviderDefinition previous,
                                       AiProviderSetupSupport.ProviderDefinition next) {
        if (next == null) {
            return;
        }

        if (shouldReplaceWithProviderDefault(baseUrlField.getText(), previous == null ? null : previous.defaultBaseUrl())) {
            baseUrlField.setText(next.defaultBaseUrl());
        }
        if (shouldReplaceWithProviderDefault(modelNameField.getText(), previous == null ? null : previous.defaultModelName())) {
            modelNameField.setText(next.defaultModelName());
        }

        baseUrlField.setPromptText(next.defaultBaseUrl());
        modelNameField.setPromptText(next.defaultModelName());
    }

    private boolean shouldReplaceWithProviderDefault(String value, String previousDefault) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isBlank() || (previousDefault != null && trimmed.equals(previousDefault));
    }

    private AiProviderSetupSupport.ProviderDefinition resolveProviderDefinition(TextField providerField) {
        AiProviderSetupSupport.ProviderDefinition typedDefinition =
                AiProviderSetupSupport.tryDefinitionForInput(providerField.getText());
        if (typedDefinition != null) {
            providerField.getProperties().put("resolvedProviderId", typedDefinition.id());
            return typedDefinition;
        }
        Object resolvedProviderId = providerField.getProperties().get("resolvedProviderId");
        if (resolvedProviderId instanceof String providerId && !providerId.isBlank()) {
            return AiProviderSetupSupport.definitionForId(providerId);
        }
        return AiProviderSetupSupport.definitionForId(AiProviderSetupSupport.PROVIDER_GROQ);
    }

    private List<AiProviderSetupSupport.ProviderSetup> collectProviderSetups(VBox setupsContainer) {
        List<AiProviderSetupSupport.ProviderSetup> setups = new ArrayList<>();
        for (Node node : setupsContainer.getChildren()) {
            Object editorObject = node.getProperties().get("providerSetupEditor");
            if (!(editorObject instanceof ProviderSetupEditor editor)) {
                continue;
            }
            AiProviderSetupSupport.ProviderDefinition definition =
                    resolveProviderDefinition(editor.providerField());
            List<String> apiKeys = parseCommaSeparated(collectApiKeys(editor.keysContainer()));
            setups.add(new AiProviderSetupSupport.ProviderSetup(
                    definition.id(),
                    editor.baseUrlField().getText(),
                    editor.modelNameField().getText(),
                    apiKeys
            ));
        }
        return setups;
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

    private VBox createMultiSecretFieldRow(String label, String csvKey, String legacyKey, String hintText) {
        VBox wrapper = new VBox(8);

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("settings-label");

        Label hintNode = new Label(hintText);
        hintNode.setWrapText(true);
        hintNode.getStyleClass().add("settings-hint");

        VBox keysContainer = new VBox(6);
        keysContainer.setFillWidth(true);

        List<String> initialKeys = parseCommaSeparated(settings.getString(csvKey, ""));
        if (initialKeys.isEmpty()) {
            String legacy = settings.getString(legacyKey, "").trim();
            if (!legacy.isEmpty()) {
                initialKeys.add(legacy);
            }
        }
        if (initialKeys.isEmpty()) {
            initialKeys.add("");
        }

        for (String keyValue : initialKeys) {
            keysContainer.getChildren().add(createApiKeyEditorRow(keyValue, keysContainer));
        }

        Button addButton = new Button("+");
        addButton.setFocusTraversable(false);
        addButton.getStyleClass().add("settings-browse-button");
        addButton.setOnAction(event -> keysContainer.getChildren().add(createApiKeyEditorRow("", keysContainer)));

        HBox addRow = new HBox(addButton);
        addRow.setAlignment(Pos.CENTER_LEFT);

        wrapper.getChildren().addAll(labelNode, hintNode, keysContainer, addRow);
        wrapper.setUserData(new SettingBinding(csvKey, () -> collectApiKeys(keysContainer)));
        return wrapper;
    }

    private HBox createApiKeyEditorRow(String initialValue, VBox owner) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        PasswordField field = new PasswordField();
        field.setText(initialValue == null ? "" : initialValue.trim());
        field.setPromptText("sk-...");
        field.getStyleClass().add("settings-text-field");
        HBox.setHgrow(field, Priority.ALWAYS);

        Button remove = new Button("-");
        remove.setFocusTraversable(false);
        remove.getStyleClass().add("settings-browse-button");
        remove.setOnAction(event -> {
            owner.getChildren().remove(row);
            if (owner.getChildren().isEmpty()) {
                owner.getChildren().add(createApiKeyEditorRow("", owner));
            }
        });

        row.getChildren().addAll(field, remove);
        return row;
    }

    private String collectApiKeys(VBox keysContainer) {
        List<String> values = new ArrayList<>();
        for (Node node : keysContainer.getChildren()) {
            if (!(node instanceof HBox row)) {
                continue;
            }
            for (Node child : row.getChildren()) {
                if (child instanceof TextField field) {
                    String value = field.getText() == null ? "" : field.getText().trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                    break;
                }
            }
        }
        return String.join(",", values);
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
        page.getChildren().add(createToggleRow(
            "Check for updates when Altarix opens",
            "advanced.autoCheckUpdatesOnStartup",
            settings.getBoolean("advanced.autoCheckUpdatesOnStartup", true)
        ));
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
        if (key != null && key.startsWith("codeVisualizer.")) {
            spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    settings.set(key, newVal);
                }
            });
        }
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
        if (key != null && key.startsWith("codeVisualizer.")) {
            combo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    settings.set(key, newVal);
                }
            });
        }
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
        syncLegacyAiSettingsFromProviderSetups();
        persistAiConfigToResourceProperties();
        settings.save();
        if (onSave != null) {
            onSave.run();
        }
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
        Path resourcePath = resolveAppPropertiesPath();
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

        List<AiProviderSetupSupport.ProviderSetup> setups = AiProviderSetupSupport.loadFromSettings(settings);
        writeProviderProperties(properties, setups, AiProviderSetupSupport.PROVIDER_GROQ);
        writeProviderProperties(properties, setups, AiProviderSetupSupport.PROVIDER_GOOGLE);
        writeProviderProperties(properties, setups, AiProviderSetupSupport.PROVIDER_LEONARDO);
        writeProviderProperties(properties, setups, AiProviderSetupSupport.PROVIDER_FREEPIK);

        AiProviderSetupSupport.ProviderSetup groqSetup =
                AiProviderSetupSupport.mergeForProvider(setups, AiProviderSetupSupport.PROVIDER_GROQ);
        clearExactKeys(properties, "past_api", "past_api_keys", "openai_base_url", "openai_model");
        clearIndexedKeys(properties, "past_api_");
        if (groqSetup != null) {
            writePropertyIfPresent(properties, "openai_base_url", groqSetup.baseUrl());
            writePropertyIfPresent(properties, "openai_model", groqSetup.modelName());
            writeApiKeyProperties(properties, "past_api", "past_api_keys", "past_api_", groqSetup.apiKeys());
        }

        try {
            Files.createDirectories(resourcePath.getParent());
            try (OutputStream output = Files.newOutputStream(resourcePath)) {
                properties.store(output, "Updated from Settings > AI Model");
            }
        } catch (IOException ignored) {
            // Avoid blocking Save if resource file write fails.
        }
    }

    private void syncLegacyAiSettingsFromProviderSetups() {
        List<AiProviderSetupSupport.ProviderSetup> setups = AiProviderSetupSupport.loadFromSettings(settings);
        AiProviderSetupSupport.ProviderSetup groqSetup =
                AiProviderSetupSupport.mergeForProvider(setups, AiProviderSetupSupport.PROVIDER_GROQ);
        AiProviderSetupSupport.ProviderSetup primarySetup = groqSetup;
        if (primarySetup == null && !setups.isEmpty()) {
            primarySetup = setups.get(0);
        }
        if (primarySetup == null) {
            primarySetup = AiProviderSetupSupport.defaultSetup();
        }

        List<String> primaryKeys = groqSetup == null ? List.of() : groqSetup.apiKeys();
        settings.set("ai.apiKeys", String.join(",", primaryKeys));
        settings.set("ai.apiKey", primaryKeys.isEmpty() ? "" : primaryKeys.get(0));
        settings.set("ai.baseUrl", primarySetup.baseUrl());
        settings.set("ai.modelName", primarySetup.modelName());
    }

    private void writeProviderProperties(Properties properties,
                                         List<AiProviderSetupSupport.ProviderSetup> setups,
                                         String providerId) {
        AiProviderSetupSupport.ProviderDefinition definition = AiProviderSetupSupport.definitionForId(providerId);
        AiProviderSetupSupport.ProviderSetup mergedSetup = AiProviderSetupSupport.mergeForProvider(setups, providerId);

        clearExactKeys(
                properties,
                definition.baseUrlProperty(),
                definition.modelProperty(),
                definition.singleApiProperty(),
                definition.csvApiProperty()
        );
        clearIndexedKeys(properties, definition.indexedApiPrefix());

        if (mergedSetup == null) {
            return;
        }

        writePropertyIfPresent(properties, definition.baseUrlProperty(), mergedSetup.baseUrl());
        writePropertyIfPresent(properties, definition.modelProperty(), mergedSetup.modelName());
        writeApiKeyProperties(
                properties,
                definition.singleApiProperty(),
                definition.csvApiProperty(),
                definition.indexedApiPrefix(),
                mergedSetup.apiKeys()
        );
    }

    private void writeApiKeyProperties(Properties properties,
                                       String singleKeyProperty,
                                       String csvKeyProperty,
                                       String indexedPrefix,
                                       List<String> apiKeys) {
        clearExactKeys(properties, singleKeyProperty, csvKeyProperty);
        clearIndexedKeys(properties, indexedPrefix);
        if (apiKeys == null || apiKeys.isEmpty()) {
            return;
        }
        properties.setProperty(singleKeyProperty, apiKeys.get(0));
        properties.setProperty(csvKeyProperty, String.join(",", apiKeys));
        for (int i = 0; i < apiKeys.size(); i++) {
            properties.setProperty(indexedPrefix + (i + 1), apiKeys.get(i));
        }
    }

    private void writePropertyIfPresent(Properties properties, String key, String value) {
        if (value == null || value.isBlank()) {
            properties.remove(key);
            return;
        }
        properties.setProperty(key, value.trim());
    }

    private void clearExactKeys(Properties properties, String... keys) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                properties.remove(key);
            }
        }
    }

    private Path resolveAppPropertiesPath() {
        try {
            Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            Path projectDir;
            if ("ai-project".equalsIgnoreCase(userDir.getFileName() != null ? userDir.getFileName().toString() : "")) {
                projectDir = userDir;
            } else {
                Path nested = userDir.resolve("ai-project");
                projectDir = Files.isDirectory(nested) ? nested : userDir;
            }
            return projectDir.resolve(APP_PROPERTIES_FILE).toAbsolutePath().normalize();
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

    private List<String> parseCommaSeparated(String csv) {
        List<String> values = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return values;
        }
        for (String part : csv.split(",")) {
            String value = part == null ? "" : part.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private void clearIndexedKeys(Properties properties, String prefix) {
        List<String> keysToRemove = new ArrayList<>();
        for (String name : properties.stringPropertyNames()) {
            if (name != null && name.startsWith(prefix)) {
                keysToRemove.add(name);
            }
        }
        for (String key : keysToRemove) {
            properties.remove(key);
        }
    }

    // ================= BINDING RECORD =================
    private record SettingBinding(String key, java.util.function.Supplier<Object> valueGetter) {}

    private record ProviderSetupEditor(VBox root,
                                       TextField providerField,
                                       VBox keysContainer,
                                       TextField baseUrlField,
                                       TextField modelNameField) {}

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

