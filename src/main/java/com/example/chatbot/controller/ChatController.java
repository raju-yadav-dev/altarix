package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.model.Message;
import com.example.chatbot.service.ChatService;
import com.example.chatbot.service.CodeExecutionService;
import com.example.chatbot.service.ExportService;
import com.example.chatbot.service.LanguageConfigService;
import com.example.chatbot.service.SettingsManager;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.control.Tooltip;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ChatController {
    // ================= CHAT VIEW NODES =================
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox messageBox;
    @FXML
    private TextArea inputArea;
    @FXML
    private HBox exportToastBanner;
    @FXML
    private Label exportToastLabel;
    @FXML
    private Button exportToastCloseButton;
    @FXML
    private Button sendButton;
    @FXML
    private HBox inputShell;
    @FXML
    private MenuButton exportButton;
    @FXML
    private SplitPane chatSplitPane;
    @FXML
    private Button terminalToggleButton;
    @FXML
    private VBox terminalPane;
    @FXML
    private Label terminalTitleLabel;
    @FXML
    private Label terminalStatusLabel;
    @FXML
    private StackPane terminalContentHost;
    @FXML
    private TextArea terminalOutputArea;
    @FXML
    private Button terminalClearButton;
    @FXML
    private Button terminalCloseButton;
    @FXML
    private Button terminalDockLeftButton;
    @FXML
    private Button terminalDockRightButton;
    @FXML
    private Button terminalDockBottomButton;
    @FXML
    private Button terminalDockTopButton;

    // ================= STATE =================
    private Conversation conversation;
    private ChatService chatService = new ChatService();
    private Runnable onConversationUpdated;
    private CompletableFuture<Message> inFlightRequest;
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\R([\\s\\S]*?)```");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,3})\\s+(.*)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^(\\d+)\\.\\s+(.*)$");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^[-*]\\s+(.*)$");
    private static final Pattern INLINE_MARKDOWN_PATTERN = Pattern.compile("(\\*\\*([^*]+)\\*\\*)|(`([^`]+)`)|(\\*([^*]+)\\*)");
    private static final Pattern MINOR_EDIT_REQUEST_PATTERN = Pattern.compile("\\b(edit|change|modify|update|fix|correct|adjust|reword|tweak|replace|shorten|lengthen|refine|improve|remove|add)\\b");
    private static final Pattern MAJOR_REWRITE_REQUEST_PATTERN = Pattern.compile("\\b(rewrite|from scratch|completely new|new version|start over|regenerate)\\b");
    private static final double MESSAGE_WIDTH_RATIO = 0.70;
    private static final double MESSAGE_MAX_WIDTH_CAP = 920;
    private static final double MESSAGE_MIN_WIDTH = 260;
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final double TERMINAL_MIN_WIDTH = 280;
    private static final double TERMINAL_DEFAULT_WIDTH = 360;
    private static final double TERMINAL_MAX_WIDTH = 520;
    private static final double TERMINAL_WINDOWED_WIDTH_BOOST = 10;
    private static final double TERMINAL_MIN_HEIGHT = 180;
    private static final double TERMINAL_DEFAULT_HEIGHT = 240;
    private static final double TERMINAL_MAX_HEIGHT = 420;
    private static final String TERMINAL_PROMPT_SUFFIX = "> ";
    private final BooleanProperty waitingForResponse = new SimpleBooleanProperty(false);
    private final LanguageConfigService langConfigService = new LanguageConfigService();
    private final CodeExecutionService codeExecutionService = new CodeExecutionService(langConfigService, this::appendTerminalRaw);
    private final SettingsManager settingsManager = SettingsManager.getInstance();
    private PauseTransition exportToastTimer;
    private volatile RunningExecution activeExecution;
    private Path terminalWorkingDirectory = Path.of(System.getProperty("user.home"));
    private int terminalInputStartIndex;
    private TerminalDockPosition terminalDockPosition = TerminalDockPosition.RIGHT;
    private Timeline generatingIndicatorTimeline;
    private Timeline responseTypingTimeline;
    private HBox pendingBotBubbleRow;
    private Label pendingBotBubbleLabel;
    private VBox pendingBubbleContentBox;
    private int streamingFinalizedCount;
    private Node streamingActiveNode;
    private VBox streamingActiveVBox;
    private boolean streamingActiveIsCode;
    private WebView webPreviewView;
    private PanelMode panelMode = PanelMode.TERMINAL;

    private enum PanelMode {
        TERMINAL,
        WEB_PREVIEW
    }

    private enum TerminalDockPosition {
        LEFT,
        RIGHT,
        BOTTOM,
        TOP
    }

    // ================= INITIALIZATION =================
    @FXML
    public void initialize() {
        if (scrollPane != null) {
            scrollPane.setFitToWidth(true);
        }
        if (messageBox != null) {
            messageBox.setFillWidth(true);
            messageBox.setMaxWidth(Double.MAX_VALUE);
        }

        // ---- Disable Send For Empty Input ----
        sendButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> waitingForResponse.get() || inputArea.getText() == null || inputArea.getText().trim().isEmpty(),
                        inputArea.textProperty(),
                        waitingForResponse
                )
        );

        // ---- Send Actions ----
        sendButton.setOnAction(e -> sendMessage());

        // ---- Enter To Send, Shift+Enter For Newline ----
        inputArea.setWrapText(true);
        Tooltip inputTooltip = new Tooltip("Enter to send\nShift+Enter for new line\nCtrl+C to copy selected text");
        inputTooltip.setStyle("-fx-font-size: 11px;");
        Tooltip.install(inputArea, inputTooltip);
        
        inputArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    int pos = inputArea.getCaretPosition();
                    inputArea.insertText(pos, "\n");
                } else {
                    sendMessage();
                }
                event.consume();
            }
        });

        inputArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                if (!inputShell.getStyleClass().contains("input-focused")) {
                    inputShell.getStyleClass().add("input-focused");
                }
            } else {
                inputShell.getStyleClass().remove("input-focused");
            }
        });

        // ---- Auto-resize input height (1–4 lines) ----
        inputArea.textProperty().addListener((obs, oldText, newText) -> adjustInputHeight(newText));
        adjustInputHeight(inputArea.getText());

        if (exportToastCloseButton != null) {
            exportToastCloseButton.setOnAction(event -> hideNotificationBanner(true));
        }

        if (terminalClearButton != null) {
            terminalClearButton.setOnAction(event -> clearTerminal());
        }
        if (terminalCloseButton != null) {
            terminalCloseButton.setOnAction(event -> hideTerminalPanel());
        }
        if (terminalToggleButton != null) {
            terminalToggleButton.setOnAction(event -> toggleTerminalPanel());
        }
        if (terminalDockLeftButton != null) {
            terminalDockLeftButton.setOnAction(event -> setTerminalDockPosition(TerminalDockPosition.LEFT));
        }
        if (terminalDockRightButton != null) {
            terminalDockRightButton.setOnAction(event -> setTerminalDockPosition(TerminalDockPosition.RIGHT));
        }
        if (terminalDockBottomButton != null) {
            terminalDockBottomButton.setOnAction(event -> setTerminalDockPosition(TerminalDockPosition.BOTTOM));
        }
        if (terminalDockTopButton != null) {
            terminalDockTopButton.setOnAction(event -> setTerminalDockPosition(TerminalDockPosition.TOP));
        }
        initializeTerminalDockIcons();
        updateTerminalDockButtons();
        initializeTerminalConsole();
        initializeWebPreview();
        hideTerminalPanel();
        setTerminalStatus("Terminal hidden");
    }

    // ================= CONVERSATION BINDING =================
    public void setConversation(Conversation conversation) {
        stopResponseAnimations();
        this.conversation = conversation;
        refreshMessages();
    }

    public void setOnConversationUpdated(Runnable onConversationUpdated) {
        this.onConversationUpdated = onConversationUpdated;
    }

    public void setChatService(ChatService chatService) {
        if (chatService != null) {
            this.chatService = chatService;
        }
    }

    // ================= MESSAGE RENDER =================
    private void refreshMessages() {
        stopResponseAnimations();
        messageBox.getChildren().clear();
        if (conversation == null) {
            return;
        }
        for (Message msg : conversation.getMessages()) {
            messageBox.getChildren().add(createBubble(msg));
        }
        scrollToBottom();
    }

    // ================= INPUT AUTO-RESIZE =================
    private static final double INPUT_LINE_HEIGHT = 20.0;
    private static final double INPUT_BASE_HEIGHT = 34.0;
    private static final int INPUT_MAX_LINES = 3;
    private boolean inputExpanded = false;

    private void adjustInputHeight(String text) {
        int lineCount = 1;
        if (text != null && !text.isEmpty()) {
            lineCount = text.split("\n", -1).length;
        }

        if (lineCount > 2) {
            // Expand to max 3 lines and mark as sticky-expanded
            inputExpanded = true;
            lineCount = Math.min(lineCount, INPUT_MAX_LINES);
        } else if (lineCount <= 2) {
            // Shrink back only when text is 2 lines or fewer
            inputExpanded = false;
        }

        int effectiveLines = inputExpanded ? INPUT_MAX_LINES : Math.max(1, lineCount);
        double height = INPUT_BASE_HEIGHT + (effectiveLines - 1) * INPUT_LINE_HEIGHT;
        inputArea.setPrefHeight(height);
        inputArea.setMinHeight(height);
    }

    // ================= SEND FLOW =================
    private void sendMessage() {
        Conversation requestConversation = conversation;
        if (requestConversation == null) {
            return;
        }

        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (inFlightRequest != null && !inFlightRequest.isDone()) {
            return;
        }

        int previousSize = requestConversation.getMessages().size();
        String previousTitle = requestConversation.getTitle();
        Message editTarget = isMinorEditRequest(text) ? findLastAssistantMessage(requestConversation) : null;
        inputArea.clear();
        setComposerBusy(true);
        stopResponseAnimations();

        CompletableFuture<Message> requestFuture = chatService.sendMessageAsync(requestConversation, text);
        inFlightRequest = requestFuture;

        for (int i = previousSize; i < requestConversation.getMessages().size(); i++) {
            HBox bubble = createBubble(requestConversation.getMessages().get(i));
            messageBox.getChildren().add(bubble);
            playFadeIn(bubble);
        }

        if (onConversationUpdated != null && !Objects.equals(previousTitle, requestConversation.getTitle())) {
            onConversationUpdated.run();
        }

        HBox pendingBubble = createGeneratingBubble();
        messageBox.getChildren().add(pendingBubble);
        playFadeIn(pendingBubble);
        startGeneratingIndicator();
        scrollToBottom();

        requestFuture.whenComplete((botMessage, error) -> Platform.runLater(() -> {
            Message responseMessage = botMessage;
            if (error != null) {
                String errorMessage = error.getMessage() == null ? error.toString() : error.getMessage();
                responseMessage = new Message(
                        Message.Sender.BOT,
                        "I could not generate a reply.\n\n- " + errorMessage
                );
            }
            if (responseMessage == null) {
                responseMessage = new Message(
                        Message.Sender.BOT,
                        "I could not generate a reply.\n\n- Empty response from assistant."
                );
            }

            if (editTarget != null && error == null) {
                applyMinorEditResponse(editTarget, responseMessage);
                inFlightRequest = null;
                setComposerBusy(false);
                return;
            }

            chatService.appendAssistantMessage(requestConversation, responseMessage);
            animateAssistantResponse(responseMessage, () -> {
                inFlightRequest = null;
                setComposerBusy(false);
            });
        }));
    }

    private boolean isMinorEditRequest(String userText) {
        if (userText == null) {
            return false;
        }
        String normalized = userText.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 280) {
            return false;
        }
        if (!MINOR_EDIT_REQUEST_PATTERN.matcher(normalized).find()) {
            return false;
        }
        return !MAJOR_REWRITE_REQUEST_PATTERN.matcher(normalized).find();
    }

    private Message findLastAssistantMessage(Conversation targetConversation) {
        if (targetConversation == null) {
            return null;
        }
        List<Message> messages = targetConversation.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message candidate = messages.get(i);
            if (candidate.getSender() == Message.Sender.BOT) {
                return candidate;
            }
        }
        return null;
    }

    private void applyMinorEditResponse(Message editTarget, Message responseMessage) {
        if (editTarget == null || responseMessage == null) {
            return;
        }

        stopGeneratingIndicator();
        stopResponseTyping();
        editTarget.editContent(responseMessage.getContent());

        if (pendingBotBubbleRow != null && messageBox != null) {
            messageBox.getChildren().remove(pendingBotBubbleRow);
        }
        clearPendingBotBubble();
        refreshMessages();
    }

    private HBox createGeneratingBubble() {
        HBox row = new HBox();
        row.getStyleClass().add("message-row");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment(Pos.TOP_LEFT);

        Label generatingLabel = new Label("Cortex is generating");
        generatingLabel.setWrapText(true);
        applyResponsiveMaxWidth(generatingLabel);
        generatingLabel.getStyleClass().addAll("message-text", "message-generating-text");

        VBox contentBox = new VBox(8, generatingLabel);

        VBox bubble = new VBox(contentBox);
        bubble.getStyleClass().addAll("message-bubble", "bot-bubble", "generating-bubble");
        bubble.setFillWidth(true);
        applyResponsivePrefWidth(bubble);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(bubble, spacer);

        pendingBotBubbleRow = row;
        pendingBotBubbleLabel = generatingLabel;
        pendingBubbleContentBox = contentBox;
        return row;
    }

    private void startGeneratingIndicator() {
        stopGeneratingIndicator();
        if (pendingBotBubbleLabel == null) {
            return;
        }

        final String base = "Cortex is generating";
        final int[] dots = {0};
        pendingBotBubbleLabel.setText(base);

        generatingIndicatorTimeline = new Timeline(new KeyFrame(Duration.millis(260), event -> {
            if (pendingBotBubbleLabel == null) {
                stopGeneratingIndicator();
                return;
            }
            dots[0] = (dots[0] + 1) % 4;
            pendingBotBubbleLabel.setText(base + ".".repeat(dots[0]));
            scrollToBottomNow();
        }));
        generatingIndicatorTimeline.setCycleCount(Timeline.INDEFINITE);
        generatingIndicatorTimeline.play();
    }

    private void stopGeneratingIndicator() {
        if (generatingIndicatorTimeline != null) {
            generatingIndicatorTimeline.stop();
            generatingIndicatorTimeline = null;
        }
    }

    private void stopResponseTyping() {
        if (responseTypingTimeline != null) {
            responseTypingTimeline.stop();
            responseTypingTimeline = null;
        }
    }

    private void clearPendingBotBubble() {
        pendingBotBubbleRow = null;
        pendingBotBubbleLabel = null;
        pendingBubbleContentBox = null;
        streamingActiveNode = null;
        streamingActiveVBox = null;
        streamingFinalizedCount = 0;
    }

    private void stopResponseAnimations() {
        stopGeneratingIndicator();
        stopResponseTyping();
        clearPendingBotBubble();
    }

    private void animateAssistantResponse(Message responseMessage, Runnable onComplete) {
        stopGeneratingIndicator();

        if (responseMessage == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        if (pendingBotBubbleRow == null
                || pendingBubbleContentBox == null
                || !messageBox.getChildren().contains(pendingBotBubbleRow)) {
            HBox bubble = createBubbleSafely(responseMessage);
            messageBox.getChildren().add(bubble);
            playFadeIn(bubble);
            scrollToBottomNow();
            clearPendingBotBubble();
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        stopResponseTyping();

        String fullText = responseMessage.getContent() == null ? "" : responseMessage.getContent();
        if (fullText.isEmpty()) {
            replacePendingBubbleWithFinal(responseMessage);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // Initialize streaming state
        pendingBubbleContentBox.getChildren().clear();
        streamingFinalizedCount = 0;
        streamingActiveNode = null;
        streamingActiveVBox = null;
        streamingActiveIsCode = false;

        final int totalLength = fullText.length();
        final int[] cursor = {0};

        responseTypingTimeline = new Timeline(new KeyFrame(Duration.millis(14), event -> {
            int remaining = totalLength - cursor[0];
            int step = remaining > 420 ? 8
                    : remaining > 220 ? 6
                    : remaining > 120 ? 4
                    : remaining > 60 ? 3
                    : 1;

            cursor[0] = Math.min(totalLength, cursor[0] + step);
            updateStreamingContent(fullText, cursor[0]);
            scrollToBottomNow();

            if (cursor[0] >= totalLength) {
                stopResponseTyping();
                replacePendingBubbleWithFinal(responseMessage);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }));
        responseTypingTimeline.setCycleCount(Timeline.INDEFINITE);
        responseTypingTimeline.play();
    }

    // ================= STREAMING MARKDOWN RENDERER =================
    private void updateStreamingContent(String fullText, int upTo) {
        String partial = fullText.substring(0, upTo);
        String[] parts = partial.split("```", -1);
        int totalParts = parts.length;
        int completeCount = totalParts - 1; // all except the last are complete

        // Finalize complete segments not yet rendered
        while (streamingFinalizedCount < completeCount) {
            int i = streamingFinalizedCount;
            String raw = parts[i];
            boolean isCode = (i % 2 == 1);
            Node rendered = null;

            if (isCode) {
                int nl = raw.indexOf('\n');
                String lang = nl >= 0 ? raw.substring(0, nl).trim() : raw.trim();
                String code = nl >= 0 ? raw.substring(nl + 1).stripTrailing() : "";
                if (lang.isEmpty()) lang = "text";
                rendered = createCodeBlock(lang, code, fullText);
            } else {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    rendered = createMarkdownBlock(trimmed);
                }
            }

            if (streamingActiveNode != null) {
                int idx = pendingBubbleContentBox.getChildren().indexOf(streamingActiveNode);
                if (rendered != null && idx >= 0) {
                    pendingBubbleContentBox.getChildren().set(idx, rendered);
                } else {
                    if (idx >= 0) pendingBubbleContentBox.getChildren().remove(idx);
                    if (rendered != null) pendingBubbleContentBox.getChildren().add(rendered);
                }
                streamingActiveNode = null;
                streamingActiveVBox = null;
            } else if (rendered != null) {
                pendingBubbleContentBox.getChildren().add(rendered);
            }

            streamingFinalizedCount++;
        }

        // Update the active (last) segment
        String activeRaw = parts[totalParts - 1];
        boolean activeIsCode = ((totalParts - 1) % 2 == 1);

        if (activeIsCode) {
            // Streaming code block
            int nl = activeRaw.indexOf('\n');
            String displayText = nl >= 0 ? activeRaw.substring(nl + 1) : "";

            if (streamingActiveVBox == null || !streamingActiveIsCode) {
                if (streamingActiveNode != null) {
                    pendingBubbleContentBox.getChildren().remove(streamingActiveNode);
                }
                streamingActiveIsCode = true;

                String lang = nl >= 0 ? activeRaw.substring(0, nl).trim() : activeRaw.trim();
                if (lang.isEmpty()) lang = "text";
                Label langLabel = new Label(lang);
                langLabel.getStyleClass().add("code-language");

                TextArea codeArea = new TextArea(displayText);
                codeArea.setEditable(false);
                codeArea.setWrapText(false);
                codeArea.setFocusTraversable(false);
                applyResponsiveMaxWidth(codeArea);
                codeArea.getStyleClass().add("message-code");
                int lineCount = Math.max(1, displayText.split("\\R", -1).length);
                codeArea.setPrefRowCount(lineCount);
                codeArea.setMinHeight(Region.USE_PREF_SIZE);

                VBox codeWrapper = new VBox(6, langLabel, codeArea);
                codeWrapper.getStyleClass().add("message-code-block");
                applyResponsiveMaxWidth(codeWrapper);

                streamingActiveVBox = codeWrapper;
                streamingActiveNode = codeWrapper;
                pendingBubbleContentBox.getChildren().add(streamingActiveNode);
            } else {
                // Update existing code block TextArea
                Node last = streamingActiveVBox.getChildren().size() > 1
                        ? streamingActiveVBox.getChildren().get(1) : null;
                if (last instanceof TextArea codeArea) {
                    codeArea.setText(displayText);
                    int lineCount = Math.max(1, displayText.split("\\R", -1).length);
                    codeArea.setPrefRowCount(lineCount);
                }
            }
        } else {
            // Streaming text block — render formatted lines
            String trimmed = activeRaw.trim();
            if (trimmed.isEmpty()) {
                if (streamingActiveNode != null) {
                    pendingBubbleContentBox.getChildren().remove(streamingActiveNode);
                    streamingActiveNode = null;
                    streamingActiveVBox = null;
                }
                return;
            }

            if (streamingActiveVBox == null || streamingActiveIsCode) {
                if (streamingActiveNode != null) {
                    pendingBubbleContentBox.getChildren().remove(streamingActiveNode);
                }
                streamingActiveIsCode = false;
                streamingActiveVBox = new VBox(4);
                streamingActiveVBox.getStyleClass().add("message-markdown");
                applyResponsiveMaxWidth(streamingActiveVBox);
                streamingActiveNode = streamingActiveVBox;
                pendingBubbleContentBox.getChildren().add(streamingActiveNode);
            }

            // Rebuild formatted lines in the active VBox
            populateFormattedLines(streamingActiveVBox, trimmed);
        }
    }

    /** Populate a markdown VBox with per-line containers so inline queries can anchor to a specific line. */
    private void populateFormattedLines(VBox container, String rawText) {
        String[] lines = rawText.split("\\R", -1);
        int lineIdx = 0;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmedLine = line.trim();

            // Skip empty lines but keep spacing
            if (trimmedLine.isEmpty()) {
                if (lineIdx < container.getChildren().size()) {
                    Node existing = container.getChildren().get(lineIdx);
                    if (existing instanceof Region r && r.getStyleClass().contains("streaming-spacer")) {
                        lineIdx++;
                        continue;
                    }
                }
                Region spacer = new Region();
                spacer.setMinHeight(6);
                spacer.getStyleClass().add("streaming-spacer");
                if (lineIdx < container.getChildren().size()) {
                    container.getChildren().set(lineIdx, spacer);
                } else {
                    container.getChildren().add(spacer);
                }
                lineIdx++;
                continue;
            }

            // Strip trailing unclosed markdown markers during streaming
            String cleanLine = trimmedLine.replaceAll("[*`]+$", "");

            String displayText;
            String lineStyleClass = "message-line-plain";
            Matcher headingMatcher = HEADING_PATTERN.matcher(cleanLine);
            if (headingMatcher.matches()) {
                int level = headingMatcher.group(1).length();
                displayText = stripInlineMarkdownMarkers(headingMatcher.group(2));
                lineStyleClass = "message-line-heading-" + level;
            } else {
                Matcher orderedMatcher = ORDERED_LIST_PATTERN.matcher(cleanLine);
                if (orderedMatcher.matches()) {
                    displayText = orderedMatcher.group(1) + ". " + stripInlineMarkdownMarkers(orderedMatcher.group(2));
                    lineStyleClass = "message-line-list";
                } else {
                    Matcher unorderedMatcher = UNORDERED_LIST_PATTERN.matcher(cleanLine);
                    if (unorderedMatcher.matches()) {
                        displayText = "\u2022 " + stripInlineMarkdownMarkers(unorderedMatcher.group(1));
                        lineStyleClass = "message-line-list";
                    } else {
                        displayText = stripInlineMarkdownMarkers(cleanLine);
                    }
                }
            }

            VBox lineContainer = new VBox();
            lineContainer.getStyleClass().add("message-line-container");

            TextArea lineText = new TextArea(displayText.isEmpty() ? " " : displayText);
            lineText.setEditable(false);
            lineText.setWrapText(true);
            lineText.setFocusTraversable(false);
            lineText.getStyleClass().addAll("message-line-text", lineStyleClass);
            applyResponsiveMaxWidth(lineText);

            int wrappedLines = Math.max(1, (int) Math.ceil((displayText.length() + 1) / 62.0));
            lineText.setPrefRowCount(wrappedLines);
            lineText.setMinHeight(Region.USE_PREF_SIZE);

            lineContainer.getChildren().add(lineText);

            if (lineIdx < container.getChildren().size()) {
                container.getChildren().set(lineIdx, lineContainer);
            } else {
                container.getChildren().add(lineContainer);
            }
            lineIdx++;
        }

        // Remove excess old children
        while (container.getChildren().size() > lineIdx) {
            container.getChildren().remove(container.getChildren().size() - 1);
        }
    }

    private String stripInlineMarkdownMarkers(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String cleaned = value;
        cleaned = cleaned.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        cleaned = cleaned.replaceAll("`(.+?)`", "$1");
        cleaned = cleaned.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "$1");
        return cleaned;
    }

    private void replacePendingBubbleWithFinal(Message responseMessage) {
        int index = pendingBotBubbleRow == null ? -1 : messageBox.getChildren().indexOf(pendingBotBubbleRow);
        HBox finalBubble = createBubbleSafely(responseMessage);

        if (index >= 0) {
            messageBox.getChildren().set(index, finalBubble);
        } else {
            messageBox.getChildren().add(finalBubble);
            playFadeIn(finalBubble);
        }

        clearPendingBotBubble();
        scrollToBottomNow();
    }

    // ================= BUBBLE FACTORY =================
    private HBox createBubble(Message msg) {
        HBox row = new HBox();
        row.getStyleClass().add("message-row");
        row.setMaxWidth(Double.MAX_VALUE);

        Node content = msg.getSender() == Message.Sender.USER
            ? buildCompactUserContent(msg.getContent())
            : buildMessageContent(msg.getContent());

        VBox bubble = new VBox(6);
        bubble.getChildren().add(content);
        appendEditedIndicator(msg, bubble);
        bubble.getStyleClass().add("message-bubble");
        applyResponsiveMaxWidth(bubble);

        // Copy icon outside the bubble
        Button copyIcon = new Button("\uD83D\uDCCB"); // clipboard emoji
        copyIcon.getStyleClass().add("bubble-copy-icon");
        copyIcon.setFocusTraversable(false);
        copyIcon.setOnAction(e -> {
            copyCodeToClipboard(msg.getContent());
            copyIcon.setText("\u2713");
            PauseTransition revert = new PauseTransition(Duration.millis(1200));
            revert.setOnFinished(ev -> copyIcon.setText("\uD83D\uDCCB"));
            revert.play();
        });

        // Wrap bubble + copy icon in a VBox, with the icon below at the appropriate corner
        HBox copyRow = new HBox(copyIcon);
        copyRow.getStyleClass().add("bubble-copy-row");

        VBox bubbleWrapper = new VBox(2, bubble, copyRow);
        bubbleWrapper.getStyleClass().add("bubble-wrapper");

        // Context menu: Copy + Ask about this
        attachBubbleContextMenu(bubbleWrapper, msg.getContent());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (msg.getSender() == Message.Sender.USER) {
            row.setAlignment(Pos.TOP_RIGHT);
            bubble.getStyleClass().add("user-bubble");
            bubble.setFillWidth(false);
            copyRow.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(spacer, bubbleWrapper);
        } else {
            row.setAlignment(Pos.TOP_LEFT);
            bubble.getStyleClass().add("bot-bubble");
            bubble.setFillWidth(true);
            applyResponsivePrefWidth(bubble);
            copyRow.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().addAll(bubbleWrapper, spacer);
        }

        return row;
    }

    private void appendEditedIndicator(Message msg, VBox bubbleContent) {
        if (msg == null || bubbleContent == null) {
            return;
        }
        if (msg.getSender() != Message.Sender.BOT || !msg.isEdited()) {
            return;
        }

        String previousContent = msg.getPreviousContent();
        if (previousContent == null || previousContent.isBlank()) {
            return;
        }

        Button editedButton = new Button("edited");
        editedButton.getStyleClass().add("message-edited-link");
        editedButton.setFocusTraversable(false);

        HBox metaRow = new HBox(editedButton);
        metaRow.getStyleClass().add("message-meta-row");

        Label previousLabel = new Label("Previous version");
        previousLabel.getStyleClass().add("message-previous-title");

        Node previousContentNode = buildMessageContent(previousContent);
        VBox previousContainer = new VBox(8, previousLabel, previousContentNode);
        previousContainer.getStyleClass().add("message-previous-version");
        previousContainer.setVisible(false);
        previousContainer.setManaged(false);

        editedButton.setOnAction(event -> {
            boolean showPrevious = !previousContainer.isVisible();
            previousContainer.setVisible(showPrevious);
            previousContainer.setManaged(showPrevious);
            editedButton.setText(showPrevious ? "hide edit" : "edited");
            scrollToBottomNow();
        });

        bubbleContent.getChildren().add(metaRow);
        bubbleContent.getChildren().add(previousContainer);
    }

    // ================= ASK ABOUT SELECTION =================
    private void attachBubbleContextMenu(VBox bubbleWrapper, String fullText) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> {
            SelectedLineContext selectedContext = getSelectedLineContext(bubbleWrapper);
            String selected = selectedContext != null ? selectedContext.selectedText() : getSelectedTextFromBubble(bubbleWrapper);
            copyCodeToClipboard(selected != null && !selected.isBlank() ? selected : fullText);
        });

        MenuItem askItem = new MenuItem("Ask about this");
        askItem.setOnAction(e -> {
            SelectedLineContext selectedContext = getSelectedLineContext(bubbleWrapper);
            if (selectedContext != null) {
                insertInlineQuestion(selectedContext);
                return;
            }

            String selected = getSelectedTextFromBubble(bubbleWrapper);
            String context = (selected != null && !selected.isBlank()) ? selected : fullText;
            insertInlineQuestion(new SelectedLineContext(context, bubbleWrapper, bubbleWrapper, null));
        });

        contextMenu.getItems().addAll(copyItem, askItem);
        contextMenu.getStyleClass().add("bubble-context-menu");

        // Event filter intercepts context-menu requests before child TextAreas handle them
        bubbleWrapper.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            contextMenu.show(bubbleWrapper, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        bubbleWrapper.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.SECONDARY) {
                contextMenu.hide();
            }
        });
    }

    private String getSelectedTextFromBubble(VBox bubbleWrapper) {
        // Check code block and message text TextAreas for selected text
        for (Node child : bubbleWrapper.lookupAll("TextArea")) {
            if (child instanceof TextArea ta) {
                String selected = ta.getSelectedText();
                if (selected != null && !selected.isBlank()) return selected;
            }
        }
        return null;
    }

    private SelectedLineContext getSelectedLineContext(VBox bubbleWrapper) {
        for (Node child : bubbleWrapper.lookupAll(".message-line-text")) {
            if (!(child instanceof TextArea selectedArea)) {
                continue;
            }
            SelectedLineContext context = resolveSelectedLineContext(selectedArea);
            if (context != null) {
                return context;
            }
        }
        return null;
    }

    private SelectedLineContext resolveSelectedLineContext(TextArea selectedArea) {
        if (selectedArea == null) {
            return null;
        }

        String selectedText = selectedArea.getSelectedText();
        if (selectedText == null || selectedText.isBlank()) {
            return null;
        }

        Node lineContainerNode = selectedArea;
        while (lineContainerNode != null
                && (!lineContainerNode.getStyleClass().contains("message-line-container")
                || !(lineContainerNode.getParent() instanceof VBox))) {
            lineContainerNode = lineContainerNode.getParent();
        }

        if (lineContainerNode != null && lineContainerNode.getParent() instanceof VBox markdownContainer
                && markdownContainer.getStyleClass().contains("message-markdown")) {
            return new SelectedLineContext(selectedText, lineContainerNode, markdownContainer, selectedArea);
        }
        return null;
    }

    private void insertInlineQuestion(SelectedLineContext selectedContext) {
        VBox targetContainer = selectedContext.markdownContainer() instanceof VBox v ? v : null;
        Node selectedLineNode = selectedContext.lineNode();
        String selectedText = selectedContext.selectedText();

        if (targetContainer == null || selectedLineNode == null) {
            return;
        }

        final double previousScroll = scrollPane != null ? scrollPane.getVvalue() : -1;

        // Keep only one inline ask widget per markdown block.
        List<Node> staleThreads = new ArrayList<>();
        for (Node child : targetContainer.getChildren()) {
            if (child.getStyleClass().contains("inline-thread-group")) {
                staleThreads.add(child);
            }
        }
        targetContainer.getChildren().removeAll(staleThreads);

        VBox discussionGroup = new VBox(4);
        discussionGroup.getStyleClass().add("inline-thread-group");

        HBox discussionRow = new HBox(8);
        discussionRow.getStyleClass().add("inline-discussion-row");

        Region threadLine = new Region();
        threadLine.getStyleClass().add("inline-thread-line");

        // Build inline discussion container
        VBox discussion = new VBox(6);
        discussion.getStyleClass().add("inline-discussion");
        threadLine.prefHeightProperty().bind(discussion.heightProperty());

        Button collapseThreadBtn = new Button("\u2212");
        collapseThreadBtn.getStyleClass().add("inline-discussion-collapse-btn");
        collapseThreadBtn.setFocusTraversable(false);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(8, headerSpacer, collapseThreadBtn);

        // Question input
        TextField questionField = new TextField();
        questionField.setPromptText("Ask about this...");
        questionField.getStyleClass().add("inline-discussion-input");
        questionField.setMaxWidth(500);

        // Answer container (initially empty)
        VBox answerBox = new VBox(4);
        answerBox.getStyleClass().add("inline-discussion-answer");

        discussion.getChildren().addAll(headerRow, questionField, answerBox);

        Label collapsedPlaceholder = new Label("...");
        collapsedPlaceholder.getStyleClass().add("inline-thread-placeholder");
        collapsedPlaceholder.setVisible(true);
        collapsedPlaceholder.setManaged(true);

        ContextMenu collapsedMenu = new ContextMenu();
        MenuItem deleteInlineItem = new MenuItem("Delete");
        deleteInlineItem.setOnAction(e -> targetContainer.getChildren().remove(discussionGroup));
        collapsedMenu.getItems().add(deleteInlineItem);

        collapseThreadBtn.setOnAction(e -> {
            collapsedMenu.hide();
            // Keep collapsed state to a single compact row.
            discussionGroup.getChildren().setAll(collapsedPlaceholder);
        });

        collapsedPlaceholder.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                collapsedMenu.show(collapsedPlaceholder, e.getScreenX(), e.getScreenY());
                return;
            }
            discussionGroup.getChildren().setAll(discussionRow);
            collapsedMenu.hide();
        });

        discussionRow.getChildren().addAll(threadLine, discussion);
        discussionGroup.getChildren().addAll(discussionRow);

        // Submit on Enter
        questionField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !questionField.getText().isBlank()) {
                String question = questionField.getText().trim();
                questionField.setEditable(false);
                questionField.setOpacity(0.7);
                submitInlineQuestion(selectedText, question, answerBox);
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                // Remove the discussion if user presses Escape without asking
                targetContainer.getChildren().remove(discussionGroup);
                event.consume();
            }
        });

        int lineIndex = targetContainer.getChildren().indexOf(selectedLineNode);
        int insertIndex = lineIndex >= 0 ? lineIndex + 1 : targetContainer.getChildren().size();
        targetContainer.getChildren().add(insertIndex, discussionGroup);

        // Focus and fade in
        discussionGroup.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(200), discussionGroup);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
        Platform.runLater(() -> {
            questionField.requestFocus();
            if (scrollPane != null && previousScroll >= 0) {
                scrollPane.setVvalue(previousScroll);
            }
        });
    }

    private record SelectedLineContext(String selectedText, Node lineNode, Node markdownContainer, TextArea selectedArea) {}

    private void submitInlineQuestion(String selectedText, String question, VBox answerBox) {
        // Show loading indicator
        Label loading = new Label("Cortex is thinking...");
        loading.getStyleClass().addAll("inline-discussion-loading");
        answerBox.getChildren().setAll(loading);

        chatService.askAboutSelection(selectedText, question)
                .whenComplete((answer, error) -> Platform.runLater(() -> {
                    answerBox.getChildren().clear();
                    String content = error != null
                            ? "Could not generate a response: " + error.getMessage()
                            : (answer != null ? answer : "No response.");

                    // Build answer content
                    Node answerContent = buildMessageContent(content);
                    VBox answerBody = new VBox(answerContent);
                    answerBody.getStyleClass().add("inline-discussion-answer-body");

                    HBox headerRow = new HBox(6);
                    headerRow.setAlignment(Pos.CENTER_LEFT);
                    Label aiLabel = new Label("Cortex");
                    aiLabel.getStyleClass().add("inline-discussion-ai-label");
                    Region hSpacer = new Region();
                    HBox.setHgrow(hSpacer, Priority.ALWAYS);
                    headerRow.getChildren().addAll(aiLabel, hSpacer);

                    answerBox.getChildren().setAll(headerRow, answerBody);
                }));
    }

    private HBox createBubbleSafely(Message msg) {
        try {
            return createBubble(msg);
        } catch (Exception ex) {
            Message fallback = new Message(
                    msg.getSender(),
                    msg.getContent() + "\n\n(Render warning: " + ex.getMessage() + ")"
            );
            return createPlainTextBubble(fallback);
        }
    }

    private HBox createPlainTextBubble(Message msg) {
        HBox row = new HBox();
        row.getStyleClass().add("message-row");
        row.setMaxWidth(Double.MAX_VALUE);

        Label content = new Label(msg.getContent() == null ? "" : msg.getContent());
        content.setWrapText(true);
        applyResponsiveMaxWidth(content);
        content.getStyleClass().add("message-text");

        // Add copy button for non-empty messages
        Button copyButton = null;
        if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
            copyButton = new Button("Copy");
            copyButton.getStyleClass().add("message-copy-button");
            copyButton.setFocusTraversable(false);
            copyButton.setOnAction(event -> copyCodeToClipboard(msg.getContent()));
        }

        VBox bubble;
        if (copyButton != null) {
            VBox contentContainer = new VBox(4, content);
            contentContainer.getStyleClass().add("message-content-container");
            HBox buttonBar = new HBox(copyButton);
            buttonBar.getStyleClass().add("message-button-bar");
            bubble = new VBox(6, contentContainer, buttonBar);
        } else {
            bubble = new VBox(content);
        }

        appendEditedIndicator(msg, bubble);
        bubble.getStyleClass().add("message-bubble");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (msg.getSender() == Message.Sender.USER) {
            row.setAlignment(Pos.TOP_RIGHT);
            bubble.getStyleClass().add("user-bubble");
            row.getChildren().addAll(spacer, bubble);
        } else {
            row.setAlignment(Pos.TOP_LEFT);
            bubble.getStyleClass().add("bot-bubble");
            applyResponsivePrefWidth(bubble);
            row.getChildren().addAll(bubble, spacer);
        }

        return row;
    }

    // ================= MESSAGE ANIMATION =================
    private void playFadeIn(HBox bubbleRow) {
        bubbleRow.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(220), bubbleRow);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    // ================= SCROLL HELPERS =================
    private void scrollToBottom() {
        Platform.runLater(this::scrollToBottomNow);
    }

    private void scrollToBottomNow() {
        if (scrollPane == null) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::scrollToBottomNow);
            return;
        }
        scrollPane.setVvalue(1.0);
    }

    private Node buildMessageContent(String content) {
        VBox container = new VBox(8);
        String safeContent = content == null ? "" : content;
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(safeContent);
        int current = 0;

        while (matcher.find()) {
            if (matcher.start() > current) {
                String textPart = safeContent.substring(current, matcher.start()).trim();
                if (!textPart.isEmpty()) {
                    container.getChildren().add(createMarkdownBlock(textPart));
                }
            }

            String language = matcher.group(1) == null ? "text" : matcher.group(1).trim();
            String code = matcher.group(2) == null ? "" : matcher.group(2).stripTrailing();
            container.getChildren().add(createCodeBlock(language, code, safeContent));
            current = matcher.end();
        }

        if (current < safeContent.length()) {
            String tail = safeContent.substring(current).trim();
            if (!tail.isEmpty()) {
                container.getChildren().add(createMarkdownBlock(tail));
            }
        }

        if (container.getChildren().isEmpty()) {
            container.getChildren().add(createMarkdownBlock(content == null ? "" : content));
        }
        return container;
    }

    private Node buildCompactUserContent(String content) {
        String text = content == null ? "" : content;
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("message-text");
        applyResponsiveMaxWidth(label);
        return label;
    }

    private Node createMarkdownBlock(String textValue) {
        String safeText = textValue == null ? "" : textValue;
        VBox markdownBlock = new VBox(6);
        markdownBlock.getStyleClass().add("message-markdown");
        applyResponsiveMaxWidth(markdownBlock);
        populateFormattedLines(markdownBlock, safeText);

        if (markdownBlock.getChildren().isEmpty()) {
            TextFlow empty = createInlineTextFlow(" ");
            markdownBlock.getChildren().add(empty);
        }
        return markdownBlock;
    }

    private TextFlow createInlineTextFlow(String textValue) {
        String safeText = textValue == null ? "" : textValue;
        TextFlow flow = new TextFlow();
        applyResponsiveMaxWidth(flow);
        flow.getStyleClass().add("message-flow");

        Matcher matcher = INLINE_MARKDOWN_PATTERN.matcher(safeText);
        int current = 0;
        while (matcher.find()) {
            if (matcher.start() > current) {
                flow.getChildren().add(createStyledText(safeText.substring(current, matcher.start()), "message-inline-plain"));
            }

            if (matcher.group(2) != null) {
                flow.getChildren().add(createStyledText(matcher.group(2), "message-inline-bold"));
            } else if (matcher.group(4) != null) {
                flow.getChildren().add(createStyledText(matcher.group(4), "message-inline-code"));
            } else if (matcher.group(6) != null) {
                flow.getChildren().add(createStyledText(matcher.group(6), "message-inline-italic"));
            }
            current = matcher.end();
        }

        if (current < safeText.length()) {
            flow.getChildren().add(createStyledText(safeText.substring(current), "message-inline-plain"));
        }
        if (flow.getChildren().isEmpty()) {
            flow.getChildren().add(createStyledText("", "message-inline-plain"));
        }
        return flow;
    }

    private Text createStyledText(String textValue, String styleClass) {
        Text text = new Text(textValue == null ? "" : textValue);
        text.getStyleClass().add(styleClass);
        return text;
    }

    private VBox createCodeBlock(String language, String code, String sourceMessageContent) {
        Label languageLabel = new Label(language);
        languageLabel.getStyleClass().add("code-language");

        Button copyButton = new Button("Copy");
        copyButton.getStyleClass().add("message-code-copy");
        copyButton.setFocusTraversable(false);
        copyButton.setOnAction(event -> copyCodeToClipboard(code));

        String normalizedLanguage = normalizeLanguage(language);
        boolean webPreviewLanguage = isWebPreviewLanguage(normalizedLanguage);
        Button runButton = new Button(webPreviewLanguage ? "Preview" : "Run");
        runButton.getStyleClass().addAll("message-code-copy", "message-code-run");
        runButton.setFocusTraversable(false);
        runButton.setDisable(webPreviewLanguage ? false : !isRuntimeAvailableForLanguage(normalizedLanguage));

        Button stopButton = new Button("x");
        stopButton.getStyleClass().addAll("message-code-copy", "message-code-stop");
        stopButton.setFocusTraversable(false);
        stopButton.setVisible(false);
        stopButton.setManaged(false);
        stopButton.setOnAction(event -> stopActiveExecution());

        runButton.setOnAction(event -> {
            if (webPreviewLanguage) {
                previewWebSnippet(normalizedLanguage, code, sourceMessageContent, runButton);
                return;
            }
            runCodeSnippet(normalizedLanguage, code, runButton, stopButton);
        });

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(8, languageLabel, headerSpacer, copyButton, runButton, stopButton);
        header.getStyleClass().add("message-code-header");
        header.setAlignment(Pos.CENTER_LEFT);

        TextArea codeArea = new TextArea(code);
        codeArea.setEditable(false);
        codeArea.setWrapText(false);
        codeArea.setFocusTraversable(false);
        codeArea.setPrefRowCount(Math.max(3, code.lines().toList().size()));
        codeArea.getStyleClass().add("message-code");
        applyResponsiveMaxWidth(codeArea);

        VBox codeBox = new VBox(6, header, codeArea);
        codeBox.getStyleClass().add("message-code-block");
        applyResponsiveMaxWidth(codeBox);
        return codeBox;
    }

    private double computeMessageMaxWidth() {
        if (messageBox == null) {
            return 520;
        }
        double preferred = messageBox.getWidth() * MESSAGE_WIDTH_RATIO;
        return Math.max(MESSAGE_MIN_WIDTH, Math.min(MESSAGE_MAX_WIDTH_CAP, preferred));
    }

    private void applyResponsiveMaxWidth(Region node) {
        if (node == null || messageBox == null) {
            return;
        }
        node.maxWidthProperty().bind(Bindings.createDoubleBinding(
                this::computeMessageMaxWidth,
                messageBox.widthProperty()
        ));
    }

    private void applyResponsivePrefWidth(Region node) {
        if (node == null || messageBox == null) {
            return;
        }
        node.prefWidthProperty().bind(Bindings.createDoubleBinding(
                this::computeMessageMaxWidth,
                messageBox.widthProperty()
        ));
    }

    private void copyCodeToClipboard(String code) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(code == null ? "" : code);
        clipboard.setContent(content);
    }

    private void runCodeSnippet(String language, String code, Button runButton, Button stopButton) {
        switchToTerminalMode();
        if (activeExecution != null) {
            setTerminalStatus("Run in progress");
            appendTerminalLine("[Run] Another command is already running. Stop it first.");
            return;
        }

        String normalizedLanguage = langConfigService.normalizeLanguage(language);
        if (!langConfigService.isRunnable(normalizedLanguage)) {
            setTerminalStatus("Unsupported language");
            appendTerminalLine("[Run] Language '" + normalizedLanguage + "' is not supported.");
            return;
        }
        if (!langConfigService.isRuntimeAvailable(normalizedLanguage)) {
            setTerminalStatus("Runtime missing");
            appendTerminalLine("[Run] Runtime for '" + normalizedLanguage + "' is not installed.");
            runButton.setDisable(true);
            return;
        }
        if (code == null || code.isBlank()) {
            setTerminalStatus("Nothing to run");
            appendTerminalLine("[Run] Code block is empty.");
            return;
        }

        RunningExecution execution = new RunningExecution(normalizedLanguage, runButton, stopButton);
        activeExecution = execution;
        updateExecutionControls(execution, true);

        LanguageConfigService.LanguageEntry langEntry = langConfigService.getLanguage(normalizedLanguage);
        String displayName = langEntry != null ? langEntry.getDisplayName() : normalizedLanguage;
        setTerminalStatus("Running " + displayName + "...");

        CodeExecutionService.ExecutionHandle handle = new CodeExecutionService.ExecutionHandle();
        execution.executionHandle = handle;

        CompletableFuture
                .supplyAsync(() -> codeExecutionService.execute(normalizedLanguage, code, handle))
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    try {
                        if (error != null) {
                            setTerminalStatus("Run failed");
                            appendTerminalLine("[Error] " + error.getMessage());
                            return;
                        }

                        int exitCode = result == null ? -1 : result.exitCode();
                        if (handle.timedOut) {
                            setTerminalStatus("Run timed out");
                        } else if (execution.stopRequestedByUser) {
                            appendTerminalLine("[Run] Execution stopped by user.");
                            setTerminalStatus("Run stopped");
                        } else {
                            setTerminalStatus(exitCode == 0 ? "Run completed" : "Run failed");
                        }
                    } finally {
                        if (activeExecution == execution) {
                            activeExecution = null;
                        }
                        updateExecutionControls(execution, false);
                        printTerminalPrompt();
                    }
                }));
    }

    private String normalizeLanguage(String language) {
        return langConfigService.normalizeLanguage(language);
    }

    private boolean isRunnableLanguage(String normalizedLanguage) {
        return langConfigService.isRunnable(normalizedLanguage);
    }

    private boolean isRuntimeAvailableForLanguage(String normalizedLanguage) {
        return langConfigService.isRuntimeAvailable(normalizedLanguage);
    }

    private boolean isCommandAvailable(String commandName) {
        return langConfigService.isCommandAvailable(commandName);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private void initializeTerminalConsole() {
        if (terminalOutputArea == null) {
            return;
        }
        terminalOutputArea.setWrapText(true);
        
        // Add tooltip for copy functionality
        Tooltip terminalTooltip = new Tooltip("Click to enter input\nSelect text and Ctrl+C to copy\nClear button resets output");
        terminalTooltip.setStyle("-fx-font-size: 11px;");
        Tooltip.install(terminalOutputArea, terminalTooltip);
        
        terminalOutputArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleTerminalKeyPressed);
        terminalOutputArea.addEventFilter(KeyEvent.KEY_TYPED, this::handleTerminalKeyTyped);
        terminalOutputArea.setOnMouseClicked(event -> {
            if (terminalOutputArea.getCaretPosition() < terminalInputStartIndex) {
                Platform.runLater(() -> terminalOutputArea.positionCaret(terminalOutputArea.getLength()));
            }
        });
        clearTerminal();
    }

    private void initializeWebPreview() {
        if (terminalContentHost == null || webPreviewView != null) {
            return;
        }
        webPreviewView = new WebView();
        webPreviewView.getStyleClass().add("web-preview");
        webPreviewView.setContextMenuEnabled(false);
        webPreviewView.setVisible(false);
        webPreviewView.setManaged(false);
        terminalContentHost.getChildren().add(webPreviewView);
    }

    private boolean isWebPreviewLanguage(String normalizedLanguage) {
        String safe = normalizedLanguage == null ? "" : normalizedLanguage.trim().toLowerCase(Locale.ROOT);
        return safe.equals("html") || safe.equals("css") || safe.equals("javascript") || safe.equals("js");
    }

    private void previewWebSnippet(String language, String code, String messageContent, Button triggerButton) {
        initializeWebPreview();
        if (webPreviewView == null) {
            showNotification("✗ Web preview is unavailable");
            return;
        }

        String mergedHtml = buildMergedWebPreviewHtml(language, code, messageContent);
        switchToWebPreviewMode();
        openTerminalPanel();
        webPreviewView.getEngine().loadContent(mergedHtml, "text/html");
        setTerminalStatus("Live preview active");
        if (triggerButton != null) {
            triggerButton.setText("Preview");
        }
    }

    private String buildMergedWebPreviewHtml(String language, String code, String messageContent) {
        String html = "";
        String css = "";
        String js = "";

        String safeContent = messageContent == null ? "" : messageContent;
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(safeContent);
        while (matcher.find()) {
            String blockLanguage = normalizeLanguage(matcher.group(1) == null ? "text" : matcher.group(1));
            String blockCode = matcher.group(2) == null ? "" : matcher.group(2).strip();
            if ("html".equals(blockLanguage)) {
                html = blockCode;
            } else if ("css".equals(blockLanguage)) {
                css = blockCode;
            } else if ("javascript".equals(blockLanguage) || "js".equals(blockLanguage)) {
                js = blockCode;
            }
        }

        String normalizedInputLanguage = normalizeLanguage(language);
        String safeCode = code == null ? "" : code;
        if (html.isBlank() && "html".equals(normalizedInputLanguage)) {
            html = safeCode;
        }
        if (css.isBlank() && "css".equals(normalizedInputLanguage)) {
            css = safeCode;
        }
        if (js.isBlank() && ("javascript".equals(normalizedInputLanguage) || "js".equals(normalizedInputLanguage))) {
            js = safeCode;
        }

        if (html.isBlank()) {
            html = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta charset=\"UTF-8\" />
                      <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                      <title>Web Preview</title>
                    </head>
                    <body>
                      <div id=\"app\">Live preview</div>
                    </body>
                    </html>
                    """;
        }

        return injectCssAndJs(html, css, js);
    }

    private String injectCssAndJs(String html, String css, String js) {
        String merged = html == null ? "" : html;
        String styleTag = css == null || css.isBlank() ? "" : "<style>\n" + css + "\n</style>\n";
        String scriptTag = js == null || js.isBlank() ? "" : "<script>\n" + js + "\n</script>\n";

        if (!styleTag.isBlank()) {
            if (merged.contains("</head>")) {
                merged = merged.replace("</head>", styleTag + "</head>");
            } else {
                merged = styleTag + merged;
            }
        }

        if (!scriptTag.isBlank()) {
            if (merged.contains("</body>")) {
                merged = merged.replace("</body>", scriptTag + "</body>");
            } else {
                merged = merged + scriptTag;
            }
        }

        return merged;
    }

    private void switchToWebPreviewMode() {
        panelMode = PanelMode.WEB_PREVIEW;
        if (terminalTitleLabel != null) {
            terminalTitleLabel.setText("Web Preview");
        }
        if (terminalClearButton != null) {
            terminalClearButton.setText("Reload");
        }
        if (terminalOutputArea != null) {
            terminalOutputArea.setVisible(false);
            terminalOutputArea.setManaged(false);
        }
        if (webPreviewView != null) {
            webPreviewView.setVisible(true);
            webPreviewView.setManaged(true);
        }
    }

    private void switchToTerminalMode() {
        panelMode = PanelMode.TERMINAL;
        if (terminalTitleLabel != null) {
            terminalTitleLabel.setText("Terminal");
        }
        if (terminalClearButton != null) {
            terminalClearButton.setText("Clear");
        }
        if (webPreviewView != null) {
            webPreviewView.setVisible(false);
            webPreviewView.setManaged(false);
        }
        if (terminalOutputArea != null) {
            terminalOutputArea.setVisible(true);
            terminalOutputArea.setManaged(true);
        }
    }

    private void handleTerminalKeyPressed(KeyEvent event) {
        if (terminalOutputArea == null) {
            return;
        }

        int caret = terminalOutputArea.getCaretPosition();
        KeyCode code = event.getCode();

        if (code == KeyCode.ENTER) {
            event.consume();
            executeTerminalCommandFromConsole();
            return;
        }
        if (code == KeyCode.HOME) {
            event.consume();
            terminalOutputArea.positionCaret(terminalInputStartIndex);
            return;
        }
        if (code == KeyCode.BACK_SPACE && caret <= terminalInputStartIndex) {
            event.consume();
            return;
        }
        if (code == KeyCode.DELETE && caret < terminalInputStartIndex) {
            event.consume();
            return;
        }
        if (code == KeyCode.LEFT && caret <= terminalInputStartIndex) {
            event.consume();
            return;
        }
        if (caret < terminalInputStartIndex && !event.isControlDown() && !event.isMetaDown()) {
            event.consume();
            terminalOutputArea.positionCaret(terminalOutputArea.getLength());
        }
    }

    private void handleTerminalKeyTyped(KeyEvent event) {
        if (terminalOutputArea == null) {
            return;
        }
        if (terminalOutputArea.getCaretPosition() < terminalInputStartIndex) {
            event.consume();
            terminalOutputArea.positionCaret(terminalOutputArea.getLength());
        }
    }

    private void executeTerminalCommandFromConsole() {
        switchToTerminalMode();
        if (terminalOutputArea == null) {
            return;
        }

        String allText = terminalOutputArea.getText() == null ? "" : terminalOutputArea.getText();
        int start = Math.min(terminalInputStartIndex, allText.length());
        String command = allText.substring(start).trim();

        appendTerminalRaw(System.lineSeparator());

        if (command.isEmpty()) {
            printTerminalPrompt();
            return;
        }

        if (forwardInputToActiveExecution(command)) {
            return;
        }

        if (handleTerminalBuiltins(command)) {
            printTerminalPrompt();
            return;
        }

        if (activeExecution != null) {
            setTerminalStatus("Run in progress");
            appendTerminalLine("[Run] Another command is already running. Stop it first.");
            printTerminalPrompt();
            return;
        }

        RunningExecution execution = new RunningExecution("terminal", null, null);
        activeExecution = execution;
        setTerminalStatus("Running command...");

        CompletableFuture
                .supplyAsync(() -> runShellCommand(command, execution))
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    try {
                        if (error != null) {
                            setTerminalStatus("Command failed");
                            appendTerminalLine("[Error] " + error.getMessage());
                            return;
                        }

                        if (result != null && !result.streamed() && result.output() != null && !result.output().isBlank()) {
                            appendTerminalLine(result.output().stripTrailing());
                        }

                        int exitCode = result == null ? -1 : result.exitCode();
                        appendTerminalLine("[Exit code] " + exitCode);
                        if (execution.stopRequestedByUser) {
                            appendTerminalLine("[Run] Execution stopped by user.");
                            setTerminalStatus("Run stopped");
                        } else {
                            setTerminalStatus(exitCode == 0 ? "Ready" : "Command failed");
                        }
                    } finally {
                        if (activeExecution == execution) {
                            activeExecution = null;
                        }
                        printTerminalPrompt();
                    }
                }));
    }

    private RunResult runShellCommand(String command, RunningExecution execution) {
        try {
            String shellPref = settingsManager.getString("terminal.defaultShell", "Cortex").trim().toLowerCase(Locale.ROOT);
            if (isWindows() && ("cmd".equals(shellPref) || "powershell".equals(shellPref))) {
                boolean launched = launchExternalShellWindow(shellPref, command);
                if (!launched) {
                    return new RunResult(-1, "[Run] Failed to open external " + shellPref + " window.", false);
                }
                return new RunResult(0, "[Run] Opened command in external " + shellPref + " window.", false);
            }

            List<String> shellCommand = buildDefaultShellCommand(command);
            if (shellCommand == null || shellCommand.isEmpty()) {
                return new RunResult(-1, "[Run] No default shell is available on this system.", false);
            }
            return runShellProcess(shellCommand, terminalWorkingDirectory, execution);
        } catch (Exception ex) {
            return new RunResult(-1, "[Run] Failed to execute command: " + ex.getMessage(), false);
        }
    }

    private boolean launchExternalShellWindow(String shell, String command) {
        try {
            List<String> launch;
            if ("cmd".equals(shell)) {
                launch = List.of("cmd", "/c", "start", "\"Cortex CMD\"", "cmd", "/k", command);
            } else {
                launch = List.of("cmd", "/c", "start", "\"Cortex PowerShell\"", "powershell", "-NoExit", "-Command", command);
            }
            ProcessBuilder builder = new ProcessBuilder(launch);
            builder.directory(terminalWorkingDirectory.toFile());
            builder.start();
            return true;
        } catch (Exception ex) {
            appendTerminalLine("[Run] Could not open external shell window: " + ex.getMessage());
            return false;
        }
    }

    private RunResult runShellProcess(List<String> command, Path workingDirectory, RunningExecution execution)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        execution.process = process;
        execution.processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        CompletableFuture<Void> outputPump = CompletableFuture.runAsync(() -> {
            try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    appendTerminalRaw(new String(buffer, 0, read));
                }
            } catch (IOException ex) {
                if (!execution.cancelRequested && ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    appendTerminalLine("[Run] Output stream closed: " + ex.getMessage());
                }
            }
        });

        while (true) {
            if (execution.cancelRequested) {
                process.destroyForcibly();
                break;
            }
            if (process.waitFor(120, TimeUnit.MILLISECONDS)) {
                break;
            }
        }

        process.waitFor(2, TimeUnit.SECONDS);
        outputPump.join();
        BufferedWriter pInput = execution.processInput;
        if (pInput != null) {
            try { pInput.close(); } catch (IOException ignored) { }
        }
        execution.processInput = null;
        execution.process = null;

        if (execution.cancelRequested) {
            return new RunResult(-1, "", true);
        }
        return new RunResult(process.isAlive() ? -1 : process.exitValue(), "", true);
    }

    private List<String> buildDefaultShellCommand(String command) {
        String shellPref = settingsManager.getString("terminal.defaultShell", "Cortex").trim().toLowerCase(Locale.ROOT);

        return switch (shellPref) {
            case "cmd" -> isWindows() ? List.of("cmd", "/c", command) : buildAutoShellCommand(command);
            case "powershell" -> {
                if (isCommandAvailable("pwsh")) {
                    yield List.of("pwsh", "-NoProfile", "-Command", command);
                }
                if (isCommandAvailable("powershell")) {
                    yield List.of("powershell", "-NoProfile", "-Command", command);
                }
                yield buildAutoShellCommand(command);
            }
            case "bash" -> isCommandAvailable("bash") ? List.of("bash", "-lc", command) : buildAutoShellCommand(command);
            case "cortex" -> buildAutoShellCommand(command);
            default -> buildAutoShellCommand(command);
        };
    }

    private List<String> buildAutoShellCommand(String command) {
        if (isWindows()) {
            if (isCommandAvailable("pwsh")) {
                return List.of("pwsh", "-NoProfile", "-Command", command);
            }
            if (isCommandAvailable("powershell")) {
                return List.of("powershell", "-NoProfile", "-Command", command);
            }
            return List.of("cmd", "/c", command);
        }
        if (isCommandAvailable("bash")) {
            return List.of("bash", "-lc", command);
        }
        return List.of("sh", "-lc", command);
    }

    private boolean handleTerminalBuiltins(String command) {
        if (!isCdCommand(command)) {
            return false;
        }

        String target = command.length() <= 2 ? "~" : command.substring(2).trim();
        if (target.isEmpty()) {
            target = "~";
        }
        target = unquote(target);

        Path nextDirectory;
        if ("~".equals(target)) {
            nextDirectory = Path.of(System.getProperty("user.home"));
        } else {
            Path rawPath = Path.of(target);
            nextDirectory = rawPath.isAbsolute() ? rawPath : terminalWorkingDirectory.resolve(rawPath);
        }
        nextDirectory = nextDirectory.normalize();

        if (!Files.exists(nextDirectory) || !Files.isDirectory(nextDirectory)) {
            setTerminalStatus("Invalid directory");
            appendTerminalLine("[Shell] Directory not found: " + nextDirectory);
            return true;
        }

        terminalWorkingDirectory = nextDirectory;
        setTerminalStatus("Directory: " + terminalWorkingDirectory);
        return true;
    }

    private boolean isCdCommand(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.equals("cd") || normalized.startsWith("cd ");
    }

    private String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        boolean quotedWithDouble = value.startsWith("\"") && value.endsWith("\"");
        boolean quotedWithSingle = value.startsWith("'") && value.endsWith("'");
        if (quotedWithDouble || quotedWithSingle) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String terminalPrompt() {
        return ">>" + TERMINAL_PROMPT_SUFFIX;
    }

    private void toggleTerminalPanel() {
        if (isTerminalOpen()) {
            hideTerminalPanel();
        } else {
            openTerminalPanel();
            if (terminalOutputArea != null) {
                Platform.runLater(() -> terminalOutputArea.requestFocus());
            }
        }
    }

    private boolean isTerminalOpen() {
        return chatSplitPane != null
                && terminalPane != null
                && chatSplitPane.getItems().contains(terminalPane)
                && terminalPane.isVisible();
    }

    private void stopActiveExecution() {
        RunningExecution execution = activeExecution;
        if (execution == null || execution.cancelRequested) {
            return;
        }

        execution.cancelRequested = true;
        execution.stopRequestedByUser = true;
        if (execution.stopButton != null) {
            execution.stopButton.setDisable(true);
        }
        setTerminalStatus("Stopping...");
        appendTerminalLine("[Run] Stop requested...");

        Process process = execution.process;
        BufferedWriter processInput = execution.processInput;
        if (processInput != null) {
            try {
                processInput.close();
            } catch (IOException ignored) {
                // Best-effort close while stopping active process.
            }
            execution.processInput = null;
        }
        if (process != null) {
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean forwardInputToActiveExecution(String input) {
        RunningExecution execution = activeExecution;
        if (execution == null || execution.cancelRequested) {
            return false;
        }

        BufferedWriter processInput = execution.processInput;
        if (processInput == null) {
            return false;
        }

        try {
            processInput.write(input);
            processInput.newLine();
            processInput.flush();
            setTerminalStatus("Input sent");
            return true;
        } catch (IOException ex) {
            appendTerminalLine("[Run] Failed to send input: " + ex.getMessage());
            return false;
        }
    }

    private void updateExecutionControls(RunningExecution execution, boolean running) {
        if (execution == null) {
            return;
        }

        Button runButton = execution.runButton;
        Button stopButton = execution.stopButton;
        if (runButton != null) {
            runButton.setText(running ? "Running..." : "Run");
            runButton.setDisable(running || !isRuntimeAvailableForLanguage(execution.language));
        }
        if (stopButton != null) {
            stopButton.setManaged(running);
            stopButton.setVisible(running);
            stopButton.setDisable(false);
        }
    }

    private void setTerminalStatus(String status) {
        if (terminalStatusLabel == null) {
            return;
        }
        terminalStatusLabel.setText(status == null || status.isBlank() ? "Ready" : status);
    }

    private void hideTerminalPanel() {
        if (terminalPane != null) {
            terminalPane.setVisible(false);
            terminalPane.setManaged(false);
        }
        if (chatSplitPane != null && terminalPane != null) {
            chatSplitPane.getItems().remove(terminalPane);
        }
        if (terminalToggleButton != null) {
            terminalToggleButton.setText(">_");
        }
        setTerminalStatus("Terminal hidden");
    }

    private void openTerminalPanel() {
        if (chatSplitPane == null || terminalPane == null) {
            return;
        }

        boolean wasOpen = isTerminalOpen();

        ensureTerminalInSplitPane();
        applyTerminalDockLayout();
        terminalPane.setManaged(true);
        terminalPane.setVisible(true);
        if (terminalToggleButton != null) {
            terminalToggleButton.setText("<");
        }

        if (!wasOpen) {
            if (terminalOutputArea != null && terminalOutputArea.getText().isEmpty()) {
                printTerminalPrompt();
            }
            Platform.runLater(this::positionTerminalDivider);
        }
    }

    private void positionTerminalDivider() {
        if (chatSplitPane == null
                || terminalPane == null
                || chatSplitPane.getItems().size() < 2
                || !chatSplitPane.getItems().contains(terminalPane)) {
            return;
        }

        boolean verticalDock = isVerticalDockPosition(terminalDockPosition);
        double availableSize = verticalDock ? chatSplitPane.getHeight() : chatSplitPane.getWidth();
        if (availableSize <= 0 && chatSplitPane.getScene() != null) {
            availableSize = verticalDock ? chatSplitPane.getScene().getHeight() : chatSplitPane.getScene().getWidth();
        }
        if (availableSize <= 0) {
            chatSplitPane.setDividerPositions(defaultDividerPositionForDock(terminalDockPosition));
            return;
        }

        double desiredSize = verticalDock
                ? clamp(TERMINAL_DEFAULT_HEIGHT, TERMINAL_MIN_HEIGHT, TERMINAL_MAX_HEIGHT)
            : clamp(effectiveTerminalDefaultWidth(), TERMINAL_MIN_WIDTH, TERMINAL_MAX_WIDTH);
        desiredSize = Math.min(desiredSize, availableSize * 0.45);

        double terminalShare = clamp(desiredSize / availableSize, 0.16, 0.55);
        double divider = isTerminalLeading(terminalDockPosition)
                ? terminalShare
                : 1.0 - terminalShare;
        divider = clamp(divider, 0.12, 0.88);
        chatSplitPane.setDividerPositions(divider);
    }

    private void setTerminalDockPosition(TerminalDockPosition position) {
        if (position == null) {
            return;
        }
        terminalDockPosition = position;
        updateTerminalDockButtons();
        applyTerminalDockLayout();
        if (isTerminalOpen()) {
            Platform.runLater(this::positionTerminalDivider);
        }
    }

    private void initializeTerminalDockIcons() {
        configureDockButtonIcon(terminalDockLeftButton, TerminalDockPosition.LEFT);
        configureDockButtonIcon(terminalDockRightButton, TerminalDockPosition.RIGHT);
        configureDockButtonIcon(terminalDockBottomButton, TerminalDockPosition.BOTTOM);
        configureDockButtonIcon(terminalDockTopButton, TerminalDockPosition.TOP);
    }

    private void configureDockButtonIcon(Button button, TerminalDockPosition position) {
        if (button == null || position == null) {
            return;
        }
        button.setGraphic(null);
        button.setText(switch (position) {
            case LEFT -> "←";
            case RIGHT -> "→";
            case TOP -> "↑";
            case BOTTOM -> "↓";
        });
    }

    private void updateTerminalDockButtons() {
        updateDockButtonState(terminalDockLeftButton, terminalDockPosition == TerminalDockPosition.LEFT);
        updateDockButtonState(terminalDockRightButton, terminalDockPosition == TerminalDockPosition.RIGHT);
        updateDockButtonState(terminalDockBottomButton, terminalDockPosition == TerminalDockPosition.BOTTOM);
        updateDockButtonState(terminalDockTopButton, terminalDockPosition == TerminalDockPosition.TOP);
    }

    private void updateDockButtonState(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        if (selected) {
            if (!button.getStyleClass().contains("terminal-dock-button-selected")) {
                button.getStyleClass().add("terminal-dock-button-selected");
            }
        } else {
            button.getStyleClass().remove("terminal-dock-button-selected");
        }
    }

    private void ensureTerminalInSplitPane() {
        if (chatSplitPane == null || terminalPane == null) {
            return;
        }
        if (!chatSplitPane.getItems().contains(terminalPane)) {
            int insertIndex = isTerminalLeading(terminalDockPosition) ? 0 : chatSplitPane.getItems().size();
            chatSplitPane.getItems().add(insertIndex, terminalPane);
        }
    }

    private void applyTerminalDockLayout() {
        if (chatSplitPane == null || terminalPane == null || !chatSplitPane.getItems().contains(terminalPane)) {
            return;
        }

        boolean verticalDock = isVerticalDockPosition(terminalDockPosition);
        chatSplitPane.setOrientation(verticalDock ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        applyTerminalPaneSizeConstraints(verticalDock);

        int desiredIndex = isTerminalLeading(terminalDockPosition) ? 0 : 1;
        int currentIndex = chatSplitPane.getItems().indexOf(terminalPane);
        if (currentIndex != desiredIndex && chatSplitPane.getItems().size() >= 2) {
            chatSplitPane.getItems().remove(terminalPane);
            chatSplitPane.getItems().add(Math.min(desiredIndex, chatSplitPane.getItems().size()), terminalPane);
        }
    }

    private void applyTerminalPaneSizeConstraints(boolean verticalDock) {
        if (terminalPane == null) {
            return;
        }
        if (verticalDock) {
            terminalPane.setMinWidth(0);
            terminalPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
            terminalPane.setMaxWidth(Double.MAX_VALUE);
            terminalPane.setMinHeight(TERMINAL_MIN_HEIGHT);
            terminalPane.setPrefHeight(TERMINAL_DEFAULT_HEIGHT);
            terminalPane.setMaxHeight(TERMINAL_MAX_HEIGHT);
        } else {
            terminalPane.setMinWidth(TERMINAL_MIN_WIDTH);
            terminalPane.setPrefWidth(effectiveTerminalDefaultWidth());
            terminalPane.setMaxWidth(TERMINAL_MAX_WIDTH);
            terminalPane.setMinHeight(0);
            terminalPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            terminalPane.setMaxHeight(Double.MAX_VALUE);
        }
    }

    private double effectiveTerminalDefaultWidth() {
        return isStageFullScreenLike()
                ? TERMINAL_DEFAULT_WIDTH
                : TERMINAL_DEFAULT_WIDTH + TERMINAL_WINDOWED_WIDTH_BOOST;
    }

    private boolean isStageFullScreenLike() {
        if (chatSplitPane == null || chatSplitPane.getScene() == null) {
            return false;
        }
        Window window = chatSplitPane.getScene().getWindow();
        if (!(window instanceof Stage stage)) {
            return false;
        }
        return stage.isFullScreen() || stage.isMaximized();
    }

    private boolean isTerminalLeading(TerminalDockPosition position) {
        return position == TerminalDockPosition.LEFT || position == TerminalDockPosition.TOP;
    }

    private boolean isVerticalDockPosition(TerminalDockPosition position) {
        return position == TerminalDockPosition.TOP || position == TerminalDockPosition.BOTTOM;
    }

    private double defaultDividerPositionForDock(TerminalDockPosition position) {
        return isTerminalLeading(position) ? 0.28 : 0.72;
    }

    private void clearTerminal() {
        if (panelMode == PanelMode.WEB_PREVIEW && webPreviewView != null) {
            webPreviewView.getEngine().reload();
            setTerminalStatus("Live preview reloaded");
            return;
        }
        if (terminalOutputArea != null) {
            terminalOutputArea.clear();
        }
        terminalInputStartIndex = 0;
        setTerminalStatus("Ready");
        printTerminalPrompt();
    }

    private void printTerminalPrompt() {
        if (terminalOutputArea == null) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::printTerminalPrompt);
            return;
        }

        if (!terminalOutputArea.getText().isEmpty() && !terminalOutputArea.getText().endsWith(System.lineSeparator())) {
            terminalOutputArea.appendText(System.lineSeparator());
        }
        terminalOutputArea.appendText(terminalPrompt());
        terminalInputStartIndex = terminalOutputArea.getLength();
        terminalOutputArea.positionCaret(terminalOutputArea.getLength());
    }

    private void appendTerminalRaw(String text) {
        switchToTerminalMode();
        if (terminalOutputArea == null) {
            return;
        }
        String safeText = text == null ? "" : text;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendTerminalRaw(safeText));
            return;
        }
        openTerminalPanel();
        terminalOutputArea.appendText(safeText);
        terminalInputStartIndex = terminalOutputArea.getLength();
        terminalOutputArea.positionCaret(terminalOutputArea.getLength());
    }

    private void appendTerminalLine(String line) {
        switchToTerminalMode();
        if (terminalOutputArea == null) {
            return;
        }
        String safeLine = line == null ? "" : line;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendTerminalLine(safeLine));
            return;
        }

        openTerminalPanel();

        if (!terminalOutputArea.getText().isEmpty() && !terminalOutputArea.getText().endsWith(System.lineSeparator())) {
            terminalOutputArea.appendText(System.lineSeparator());
        }
        terminalOutputArea.appendText(safeLine + System.lineSeparator());
        terminalInputStartIndex = terminalOutputArea.getLength();
        terminalOutputArea.positionCaret(terminalOutputArea.getLength());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup for temporary run artifacts.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup for temporary run artifacts.
        }
    }

    private record RunResult(int exitCode, String output, boolean streamed) {
    }

    private static final class RunningExecution {
        private final String language;
        private final Button runButton;
        private final Button stopButton;
        private volatile Process process;
        private volatile BufferedWriter processInput;
        private volatile boolean cancelRequested;
        private volatile boolean stopRequestedByUser;
        private volatile CodeExecutionService.ExecutionHandle executionHandle;

        private RunningExecution(String language, Button runButton, Button stopButton) {
            this.language = language;
            this.runButton = runButton;
            this.stopButton = stopButton;
        }
    }

    private void setComposerBusy(boolean busy) {
        waitingForResponse.set(busy);
        inputArea.setDisable(busy);
        if (!busy) {
            inputArea.requestFocus();
        }
    }

    // ================= EXPORT METHODS =================
    @FXML
    private void copyAsMarkdown() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to copy");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(conversation.getTitle()).append("\n\n");
        for (Message msg : conversation.getMessages()) {
            String sender = msg.getSender() == Message.Sender.USER ? "👤 User" : "🤖 Cortex";
            sb.append("## ").append(sender).append("\n\n");
            sb.append(msg.getContent()).append("\n\n---\n\n");
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
        showNotification("\u2713 Chat copied as Markdown");
    }

    @FXML
    private void copyAsText() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to copy");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : conversation.getMessages()) {
            String sender = msg.getSender() == Message.Sender.USER ? "USER" : "CORTEX";
            sb.append(sender).append(":\n").append(msg.getContent()).append("\n\n");
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
        showNotification("\u2713 Chat copied as text");
    }

    @FXML
    private void screenshotToClipboard() {
        if (messageBox == null || messageBox.getChildren().isEmpty()) {
            showNotification("Nothing to capture");
            return;
        }
        try {
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);
            // Snapshot the full message list
            javafx.scene.image.WritableImage snapshot = messageBox.snapshot(params, null);
            ClipboardContent cc = new ClipboardContent();
            cc.putImage(snapshot);
            Clipboard.getSystemClipboard().setContent(cc);
            showNotification("\u2713 Chat screenshot copied to clipboard");
        } catch (Exception ex) {
            showNotification("\u2717 Screenshot failed: " + ex.getMessage());
        }
    }

    @FXML
    private void exportAsText() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to export");
            return;
        }
        
        handleExport("Text File", "*.txt", file -> 
            ExportService.exportToText(conversation, file.toPath())
        );
    }

    @FXML
    private void exportAsMarkdown() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to export");
            return;
        }
        
        handleExport("Markdown File", "*.md", file -> 
            ExportService.exportToMarkdown(conversation, file.toPath())
        );
    }

    @FXML
    private void exportAsPDF() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to export");
            return;
        }
        
        handleExport("PDF File", "*.pdf", file -> 
            ExportService.exportToPDF(conversation, file.toPath())
        );
    }

    @FXML
    private void exportAsWord() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to export");
            return;
        }
        
        handleExport("Word Document", "*.docx", file -> 
            ExportService.exportToWord(conversation, file.toPath())
        );
    }

    private void handleExport(String format, String fileExtension, ExportHandler handler) {
        try {
            Stage stage = (Stage) messageBox.getScene().getWindow();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Chat as " + format);
            fileChooser.setInitialFileName(sanitizeFileName(conversation.getTitle()) + fileExtension.substring(1));
            
            FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(format, fileExtension);
            fileChooser.getExtensionFilters().add(filter);
            
            java.io.File selectedFile = fileChooser.showSaveDialog(stage);
            if (selectedFile != null) {
                boolean success = handler.export(selectedFile);
                if (success) {
                    showNotification("✓ Chat exported successfully to " + selectedFile.getName());
                } else {
                    showNotification("✗ Failed to export chat");
                }
            }
        } catch (Exception ex) {
            showNotification("✗ Export error: " + ex.getMessage());
        }
    }

    private String sanitizeFileName(String filename) {
        return filename.replaceAll("[<>:\"/\\\\|?*]", "_").replaceAll("\\s+", "_");
    }

    private void showNotification(String message) {
        Platform.runLater(() -> {
            if (exportToastBanner == null || exportToastLabel == null) {
                return;
            }

            if (exportToastTimer != null) {
                exportToastTimer.stop();
            }

            exportToastLabel.setText(message == null ? "" : message);
            exportToastBanner.getStyleClass().removeAll("export-toast-success", "export-toast-error", "export-toast-info");
            if (message != null && message.startsWith("✓")) {
                exportToastBanner.getStyleClass().add("export-toast-success");
            } else if (message != null && message.startsWith("✗")) {
                exportToastBanner.getStyleClass().add("export-toast-error");
            } else {
                exportToastBanner.getStyleClass().add("export-toast-info");
            }
            exportToastBanner.setOpacity(1);
            exportToastBanner.setManaged(true);
            exportToastBanner.setVisible(true);

            exportToastTimer = new PauseTransition(Duration.seconds(2));
            exportToastTimer.setOnFinished(event -> hideNotificationBanner(false));
            exportToastTimer.playFromStart();
        });
    }

    private void hideNotificationBanner(boolean instant) {
        if (exportToastBanner == null) {
            return;
        }
        if (exportToastTimer != null) {
            exportToastTimer.stop();
            exportToastTimer = null;
        }

        if (instant) {
            exportToastBanner.setVisible(false);
            exportToastBanner.setManaged(false);
            exportToastBanner.setOpacity(1);
            return;
        }

        FadeTransition fade = new FadeTransition(Duration.millis(260), exportToastBanner);
        fade.setFromValue(exportToastBanner.getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(event -> {
            exportToastBanner.setVisible(false);
            exportToastBanner.setManaged(false);
            exportToastBanner.setOpacity(1);
        });
        fade.play();
    }

    @FunctionalInterface
    private interface ExportHandler {
        boolean export(java.io.File file) throws IOException;
    }

}

