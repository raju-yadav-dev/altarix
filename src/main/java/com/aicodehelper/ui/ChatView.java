package com.aicodehelper.ui;

import com.aicodehelper.util.AppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * ChatView represents the main chat panel containing:
 * - Header with theme toggle
 * - Scrollable message area
 * - Input composer with ChatGPT-style keyboard behavior
 * - Typing indicator
 *
 * Keyboard Behavior (ChatGPT-style):
 * - ENTER: Send message (scroll down and clear input)
 * - SHIFT+ENTER: New line in input field
 * - Input area supports multi-line text composition
 * - Dynamic resizing keeps UI layout intact during multi-line input
 *
 * Responsibilities:
 * - Display messages with proper styling
 * - Provide input area for user messages with responsive resizing
 * - Show status indicators (typing animation)
 * - Expose components for controller wiring
 *
 * Design Pattern: View component in MVP architecture
 * - Does NOT contain business logic
 * - Purely presentational
 * - Exposes components for external event wiring (e.g., keyboard binding in MainLayout)
 */
public class ChatView extends BorderPane {
    private final VBox messagesBox = new VBox(AppConfig.MESSAGE_SPACING);
    private final ScrollPane scrollPane = new ScrollPane(messagesBox);
    private final TextArea inputArea = new TextArea();
    private final Button sendIconButton = new Button(AppConfig.SEND_BUTTON_ICON);
    private final MenuButton themeMenuButton = new MenuButton(AppConfig.THEME_BUTTON_TEXT);
    private final MenuItem purpleDarkItem = new MenuItem(AppConfig.THEME_PURPLE_DARK);
    private final MenuItem greenDarkItem = new MenuItem(AppConfig.THEME_GREEN_DARK);
    private final MenuItem lightModeItem = new MenuItem(AppConfig.THEME_LIGHT);
    private final Label typingLabel = new Label(AppConfig.TYPING_INDICATOR_TEXT);

    /**
     * Constructs the chat view and initializes all sub-components.
     */
    public ChatView() {
        getStyleClass().add("chat-root");
        setPadding(new Insets(16));

        // ===== HEADER: Theme menu =====
        themeMenuButton.getStyleClass().add("theme-button");
        themeMenuButton.getItems().addAll(purpleDarkItem, greenDarkItem, lightModeItem);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerRow = new HBox(10, spacer, themeMenuButton);
        headerRow.setAlignment(Pos.CENTER);
        
        // ===== TYPING INDICATOR =====
        typingLabel.getStyleClass().add("typing-label");
        typingLabel.setVisible(false);
        typingLabel.setManaged(false);
        
        VBox top = new VBox(6, headerRow, typingLabel);
        setTop(top);

        // ===== MESSAGE AREA =====
        messagesBox.getStyleClass().add("messages-box");
        messagesBox.setPadding(new Insets(8));

        scrollPane.getStyleClass().add("chat-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        StackPane chatContainer = new StackPane(scrollPane);
        chatContainer.getStyleClass().add("chat-container");
        setCenter(chatContainer);

        // ===== INPUT AREA: ChatGPT-style composition =====
        // Features:
        // - Multi-line input with dynamic resizing
        // - Enter key sends message (handled in MainLayout keyboard listener)
        // - Shift+Enter inserts a new line
        // - Text wraps automatically
        // - Grows as user types (up to max 4 rows)
        inputArea.setPromptText(AppConfig.INPUT_PLACEHOLDER);
        inputArea.setWrapText(true);
        inputArea.getStyleClass().add("input-area");
        inputArea.setPrefRowCount(AppConfig.INPUT_AREA_DEFAULT_ROWS);
        inputArea.setMinHeight(72);
        // Dynamically resize input area as content changes
        // This allows the UI to adapt to multi-line messages
        inputArea.textProperty().addListener((obs, oldText, newText) -> resizeInput());

        // ===== SEND BUTTON =====
        sendIconButton.getStyleClass().add("send-icon-button");

        // Stack input and send button together
        StackPane inputContainer = new StackPane(inputArea, sendIconButton);
        StackPane.setAlignment(sendIconButton, Pos.CENTER_RIGHT);
        StackPane.setMargin(sendIconButton, new Insets(0, 12, 0, 0));

        HBox inputRow = new HBox(10, inputContainer);
        inputRow.getStyleClass().add("composer");
        inputRow.setAlignment(Pos.BOTTOM_RIGHT);
        HBox.setHgrow(inputContainer, Priority.ALWAYS);
        inputRow.setPadding(new Insets(10));

        setBottom(inputRow);
    }

    /**
     * Dynamically resizes the input area based on content.
     * This method ensures the UI layout doesn't break when multi-line text is added.
     * 
     * Behavior:
     * - Expands as user types more lines (up to max of 4 rows)
     * - Shrinks when content is removed, returning to minimum rows
     * - Maintains proper spacing and alignment with other UI elements
     * 
     * Implementation: Counts actual line breaks (\R regex) and caps at MIN/MAX row count.
     */
    private void resizeInput() {
        int lines = Math.max(AppConfig.INPUT_AREA_MIN_ROWS,
                Math.min(AppConfig.INPUT_AREA_MAX_ROWS, inputArea.getText().split("\\R", -1).length));
        inputArea.setPrefRowCount(lines);
    }

    // ========== PUBLIC API ==========

    public VBox getMessagesBox() {
        return messagesBox;
    }

    public ScrollPane getScrollPane() {
        return scrollPane;
    }

    public TextArea getInputArea() {
        return inputArea;
    }

    public Button getSendIconButton() {
        return sendIconButton;
    }

    public MenuButton getThemeMenuButton() {
        return themeMenuButton;
    }

    public MenuItem getPurpleDarkItem() {
        return purpleDarkItem;
    }

    public MenuItem getGreenDarkItem() {
        return greenDarkItem;
    }

    public MenuItem getLightModeItem() {
        return lightModeItem;
    }

    public Label getTypingLabel() {
        return typingLabel;
    }

    /**
     * Clears the input area and resets size to default.
     */
    public void resetInput() {
        inputArea.clear();
        inputArea.setPrefRowCount(AppConfig.INPUT_AREA_DEFAULT_ROWS);
    }
}
