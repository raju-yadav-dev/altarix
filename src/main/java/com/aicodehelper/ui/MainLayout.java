package com.aicodehelper.ui;

import com.aicodehelper.controller.ChatController;
import com.aicodehelper.model.Message;
import com.aicodehelper.util.AppConfig;
import com.cortex.util.IconResources;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * MainLayout orchestrates the application UI structure and wires controller logic.
 *
 * Responsibilities:
 * - Compose layout (sidebar | divider | chat area)
 * - Wire UI events to controller methods
 * - Manage message rendering and animations
 * - Handle ChatGPT-style keyboard input:
 *   • Enter = Send message
 *   • Shift+Enter = New line in input field
 * - Implement theme switching (dark/light mode)
 * - Coordinate responsive window sizing
 *
 * Architecture: Follows MVP pattern as the View layer:
 * - Binds UI actions to controller methods
 * - Displays controller state changes
 * - Delegates business logic to ChatController
 *
 * Best Practices:
 * - Methods are focused and single-responsibility
 * - UI updates use Platform.runLater for thread safety
 * - Animations provide visual feedback without blocking UI
 * - CSS classes allow easy styling changes
 * - Event consumption prevents default TextArea behavior when appropriate
 */
public class MainLayout {
    private final ChatController chatController = new ChatController();
    private final SidebarView sidebarView = new SidebarView();
    private final ChatView chatView = new ChatView();
    private BorderPane root;
    private Stage stage;
    private String currentThemeCss = AppConfig.CSS_PURPLE_THEME; // Default to purple

    /**
     * Initializes the application UI and wires all event handlers.
     * @param stage The primary JavaFX stage
     */
    public void init(Stage stage) {
        this.stage = stage;
        buildLayout();
        wireActions();
        setupStage();
        
        // Create initial chat
        chatController.startNewChat();
        refreshHistory();
    }

    /**
     * Constructs the main layout structure:
     * [Sidebar | Divider | Chat Area]
     */
    private void buildLayout() {
        root = new BorderPane();
        root.getStyleClass().add("app-root");

        // Divider between sidebar and chat
        Region divider = new Region();
        divider.getStyleClass().add("divider");
        divider.setPrefWidth(1);

        // Main layout container
        HBox centerContainer = new HBox(sidebarView, divider, chatView);
        HBox.setHgrow(chatView, Priority.ALWAYS);
        centerContainer.setMinSize(0, 0);

        root.setCenter(centerContainer);
        root.setMinSize(0, 0);
    }

    /**
     * Wires all UI component event handlers to controller methods.
     * This is where user interactions are connected to business logic.
     */
    private void wireActions() {
        // New Chat button
        sidebarView.getNewChatButton().setOnAction(e -> handleNewChat());

        // Chat send functionality
        chatView.getSendIconButton().setOnAction(e -> sendMessage());
        
        // Theme menu items
        chatView.getPurpleDarkItem().setOnAction(e -> switchTheme(AppConfig.CSS_PURPLE_THEME, false));
        chatView.getGreenDarkItem().setOnAction(e -> switchTheme(AppConfig.CSS_GREEN_THEME, false));
        chatView.getLightModeItem().setOnAction(e -> switchTheme(currentThemeCss, true));

        // Keyboard input: ChatGPT-style input handling
        // - Enter (alone) sends the message
        // - Shift+Enter inserts a new line in the input field
        // - Unlike standard TextArea, we suppress the default Enter behavior
        chatView.getInputArea().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    // Shift+Enter: Insert newline at cursor position
                    // This allows multi-line message composition
                    int caretPos = chatView.getInputArea().getCaretPosition();
                    chatView.getInputArea().insertText(caretPos, "\n");
                    event.consume(); // Prevent default Enter behavior
                } else {
                    // Enter (alone): Send the message
                    // Same behavior as clicking the send button
                    sendMessage();
                    event.consume(); // Prevent default Enter behavior
                }
            }
        });

        // Sidebar history: switch conversations on click
        sidebarView.getHistoryView().setOnMouseClicked(event -> {
            int selectedIndex = sidebarView.getHistoryView().getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0) {
                chatController.switchToConversation(selectedIndex);
                loadCurrentConversation();
            }
        });
    }

    /**
     * Configures the stage (window) properties.
     */
    private void setupStage() {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double sceneWidth = AppConfig.calculateResponsiveWidth(visualBounds.getWidth());
        double sceneHeight = AppConfig.calculateResponsiveHeight(visualBounds.getHeight());

        Scene scene = new Scene(root, sceneWidth, sceneHeight);
        scene.getStylesheets().add(getClass().getResource(currentThemeCss).toExternalForm());

        IconResources.addStageIcons(stage, getClass());

        stage.setTitle(AppConfig.APP_NAME);
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.MIN_WINDOW_WIDTH);
        stage.setMinHeight(AppConfig.MIN_WINDOW_HEIGHT);
        stage.setMaxWidth(visualBounds.getWidth());
        stage.setMaxHeight(visualBounds.getHeight());
        stage.show();
    }

    // ========== EVENT HANDLERS ==========

    /**
     * Handles "New Chat" button click.
     * Creates new conversation and refreshes UI.
     */
    private void handleNewChat() {
        chatController.startNewChat();
        chatView.getMessagesBox().getChildren().clear();
        refreshHistory();
    }

    /**
     * Handles send button click or Enter key press.
     * Extracts input, creates message, animates response.
     */
    private void sendMessage() {
        String input = chatView.getInputArea().getText();
        if (input == null || input.isBlank()) {
            return;
        }

        try {
            // Create and display user message
            Message userMessage = chatController.createUserMessage(input.trim());
            addMessageBubble(userMessage);
            chatView.resetInput();

            // Update conversation title if first message
            chatController.getCurrentConversation().ifPresent(conv -> {
                if (conv.getMessageCount() == 1) {
                    conv.getMessages().get(0);
                }
            });

            // Show typing animation and generate response
            runTypingAnimation(() -> {
                Message botMessage = chatController.createBotReply(input);
                addMessageBubble(botMessage);
            });

        } catch (IllegalArgumentException e) {
            showTemporaryStatus(e.getMessage());
        }
    }

    /**
     * Switches between different themes (Purple Dark, Green Dark, Light).
     */
    private void switchTheme(String themeCss, boolean lightMode) {
        Scene scene = stage.getScene();
        
        // Remove old stylesheet
        scene.getStylesheets().clear();
        
        // Update current theme CSS if not switching to light mode
        if (!lightMode) {
            currentThemeCss = themeCss;
        }
        
        // Add new stylesheet
        scene.getStylesheets().add(getClass().getResource(currentThemeCss).toExternalForm());
        
        // Toggle light mode class
        if (lightMode) {
            if (!root.getStyleClass().contains(AppConfig.LIGHT_MODE_CLASS)) {
                root.getStyleClass().add(AppConfig.LIGHT_MODE_CLASS);
            }
        } else {
            root.getStyleClass().remove(AppConfig.LIGHT_MODE_CLASS);
        }
    }

    // ...existing code...

    // ========== UI UPDATES ==========

    /**
     * Adds a message bubble to the chat view with slide-in animation.
     */
    private void addMessageBubble(Message message) {
        ChatBubble bubble = new ChatBubble(message, copiedText -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(copiedText);
            clipboard.setContent(content);
            showTemporaryStatus(AppConfig.COPIED_CONFIRMATION_TEXT);
        });

        // Start invisible and slightly offset
        bubble.setOpacity(0);
        bubble.setTranslateY(10);

        chatView.getMessagesBox().getChildren().add(bubble);

        // Animate in with fade + slide
        FadeTransition fade = new FadeTransition(Duration.millis(AppConfig.MESSAGE_ANIMATION_DURATION_MS), bubble);
        fade.setToValue(1);
        
        TranslateTransition slide = new TranslateTransition(Duration.millis(AppConfig.MESSAGE_ANIMATION_DURATION_MS), bubble);
        slide.setToY(0);
        
        ParallelTransition parallel = new ParallelTransition(fade, slide);
        parallel.setOnFinished(e -> autoScrollToBottom());
        parallel.play();
    }

    /**
     * Loads messages from current conversation into the chat view.
     */
    private void loadCurrentConversation() {
        chatView.getMessagesBox().getChildren().clear();
        chatController.getCurrentMessages().forEach(this::addMessageBubble);
    }

    /**
     * Automatically scrolls chat area to the bottom.
     * Works smoothly with animation.
     */
    private void autoScrollToBottom() {
        Platform.runLater(() -> {
            Timeline scrollAnimation = new Timeline(
                    new KeyFrame(Duration.millis(AppConfig.SCROLL_ANIMATION_DURATION_MS),
                            e -> chatView.getScrollPane().setVvalue(1.0))
            );
            scrollAnimation.play();
        });
    }

    /**
     * Shows typing indicator animation.
     * Simulates AI thinking before response appears.
     */
    private void runTypingAnimation(Runnable onFinished) {
        chatView.getTypingLabel().setManaged(true);
        chatView.getTypingLabel().setVisible(true);
        chatView.getTypingLabel().setText(AppConfig.TYPING_INDICATOR_TEXT);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(AppConfig.TYPING_ANIMATION_DURATION_MS), event -> {
                    chatView.getTypingLabel().setVisible(false);
                    chatView.getTypingLabel().setManaged(false);
                    onFinished.run();
                })
        );
        timeline.setCycleCount(1);
        timeline.play();
    }

    /**
     * Displays a temporary status message (e.g., "Copied to clipboard").
     */
    private void showTemporaryStatus(String status) {
        chatView.getTypingLabel().setText(status);
        chatView.getTypingLabel().setManaged(true);
        chatView.getTypingLabel().setVisible(true);

        Timeline clear = new Timeline(new KeyFrame(Duration.millis(AppConfig.STATUS_MESSAGE_DURATION_MS), event -> {
            chatView.getTypingLabel().setVisible(false);
            chatView.getTypingLabel().setManaged(false);
        }));
        clear.setCycleCount(1);
        clear.play();
    }

    /**
     * Refreshes the conversation history list in the sidebar.
     */
    private void refreshHistory() {
        sidebarView.getHistoryView().setItems(FXCollections.observableArrayList(chatController.getChatHistory()));
    }
}
