package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.service.ChatService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.geometry.Rectangle2D;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.chatbot.service.SettingsManager;

public class MainController {
    private static final String DEFAULT_THEME = "theme-dark-purple";

    private static final Map<String, String> THEME_STYLESHEETS = Map.ofEntries(
        Map.entry("theme-dark-purple", "/styles/themes/dark/dark-purple.css"),
        Map.entry("theme-dark-green", "/styles/themes/dark/dark-green.css"),
        Map.entry("theme-dark-ocean", "/styles/themes/dark/dark-ocean.css"),
        Map.entry("theme-dark-ember", "/styles/themes/dark/dark-ember.css"),
        Map.entry("theme-dark-slate", "/styles/themes/dark/dark-slate.css"),
        Map.entry("theme-light", "/styles/themes/light/light.css"),
        Map.entry("theme-light-sky", "/styles/themes/light/light-sky.css"),
        Map.entry("theme-light-mint", "/styles/themes/light/light-mint.css"),
        Map.entry("theme-light-rose", "/styles/themes/light/light-rose.css"),
        Map.entry("theme-light-sand", "/styles/themes/light/light-sand.css")
    );

    private static final Map<String, String> THEME_MODE = Map.ofEntries(
        Map.entry("theme-dark-purple", "theme-dark"),
        Map.entry("theme-dark-green", "theme-dark"),
        Map.entry("theme-dark-ocean", "theme-dark"),
        Map.entry("theme-dark-ember", "theme-dark"),
        Map.entry("theme-dark-slate", "theme-dark"),
        Map.entry("theme-light", "theme-light"),
        Map.entry("theme-light-sky", "theme-light"),
        Map.entry("theme-light-mint", "theme-light"),
        Map.entry("theme-light-rose", "theme-light"),
        Map.entry("theme-light-sand", "theme-light")
    );

    // ================= SIDEBAR + CONTENT NODES =================
    @FXML
    private VBox sidebar;
    @FXML
    private Button newChatButton;
    @FXML
    private ListView<Conversation> chatList;
    @FXML
    private VBox chatContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private ImageView appIconView;
    @FXML
    private BorderPane appShell;
    @FXML
    private StackPane windowRoot;

    // ================= TITLE BAR ACTION NODES =================
    @FXML
    private MenuButton profileButton;
    @FXML
    private MenuButton settingsButton;
    @FXML
    private Menu themeMenu;
    @FXML
    private RadioMenuItem themeDarkPurpleItem;
    @FXML
    private RadioMenuItem themeDarkGreenItem;
    @FXML
    private RadioMenuItem themeDarkOceanItem;
    @FXML
    private RadioMenuItem themeDarkEmberItem;
    @FXML
    private RadioMenuItem themeDarkSlateItem;
    @FXML
    private RadioMenuItem themeLightItem;
    @FXML
    private RadioMenuItem themeLightSkyItem;
    @FXML
    private RadioMenuItem themeLightMintItem;
    @FXML
    private RadioMenuItem themeLightRoseItem;
    @FXML
    private RadioMenuItem themeLightSandItem;
    @FXML
    private Button minimizeButton;
    @FXML
    private Button maximizeButton;
    @FXML
    private Button closeButton;

    // ================= STATE =================
    private final ChatService chatService = new ChatService();
    private Stage stage;
    private double dragOffsetX;
    private double dragOffsetY;
    private Rectangle windowRootClip;
    private Rectangle shellClip;
    private String activeThemeStylesheet;
    private String committedThemeKey;
    private final Map<RadioMenuItem, String> themeMenuBindings = new LinkedHashMap<>();
    private final SettingsManager settingsManager = SettingsManager.getInstance();
    private final GaussianBlur modalBlur = new GaussianBlur(0);
    private Region modalOverlay;
    private Timeline modalBackdropTimeline;
    private int modalDepth;

    // ================= INITIALIZATION =================
    @FXML
    public void initialize() {
        // ---- Sidebar Actions + Selection ----
        newChatButton.setOnAction(e -> createNewConversation());
        chatList.setEditable(true);
        chatList.setCellFactory(list -> new ConversationCell(this::deleteConversation, this::togglePinConversation));
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadConversation(newVal);
            }
        });

        // ---- Window Chrome ----
        setupTitleBarDrag();
        applyRoundedClip();
        wireWindowButtons();
        wireThemeMenu();
        addMenuIcons();
        addProfileMenuIcons();
        setButtonIcons();
        loadTitleBarIcon();
        fixPopupTransparency();

        // ---- Default Theme (from saved settings) + First Conversation ----
        committedThemeKey = settingsManager.getString("appearance.theme", DEFAULT_THEME);
        applyTheme(committedThemeKey);

        Conversation first = chatService.createConversation();
        chatList.getItems().add(first);
        chatList.getSelectionModel().select(first);
    }

    private void loadTitleBarIcon() {
        if (appIconView == null) {
            return;
        }
        try {
            var resource = getClass().getResource("/icon/Cortex.png");
            if (resource != null) {
                appIconView.setImage(new Image(resource.toExternalForm()));
                return;
            }
            var icoResource = getClass().getResource("/icon/Cortex.ico");
            if (icoResource != null) {
                appIconView.setImage(new Image(icoResource.toExternalForm()));
            }
        } catch (Exception ignored) {
            // Keep UI stable even if icon resource is unavailable.
        }
    }

    // ================= STAGE BINDING =================
    public void setStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            stage.maximizedProperty().addListener((obs, oldValue, maximized) -> updateWindowChromeStateDeferred());
            stage.fullScreenProperty().addListener((obs, oldValue, fullScreen) -> updateWindowChromeStateDeferred());
            // Listen for window bounds changes to detect pseudo-maximized state
            stage.widthProperty().addListener((obs, oldValue, newValue) -> updateWindowChromeStateDeferred());
            stage.heightProperty().addListener((obs, oldValue, newValue) -> updateWindowChromeStateDeferred());
            updateWindowChromeStateDeferred();
        }
    }

    // ================= TITLE BAR DRAG =================
    private void setupTitleBarDrag() {
        titleBar.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || isWindowControl(event.getTarget()) || isResizeCursorActive()) {
                return;
            }
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            if (stage == null || event.getButton() != MouseButton.PRIMARY || isWindowControl(event.getTarget()) || isResizeCursorActive()) {
                return;
            }

            if (stage.isMaximized()) {
                double dragRatio = event.getSceneX() / Math.max(1.0, titleBar.getWidth());
                stage.setMaximized(false);
                Platform.runLater(() -> {
                    stage.setX(event.getScreenX() - stage.getWidth() * dragRatio);
                    stage.setY(event.getScreenY() - dragOffsetY);
                });
                return;
            }

            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        titleBar.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !isWindowControl(event.getTarget())) {
                toggleMaximize();
            }
        });
    }

    private boolean isResizeCursorActive() {
        if (titleBar == null || titleBar.getScene() == null) {
            return false;
        }
        Cursor cursor = titleBar.getScene().getCursor();
        return cursor == Cursor.N_RESIZE
                || cursor == Cursor.NE_RESIZE
                || cursor == Cursor.E_RESIZE
                || cursor == Cursor.SE_RESIZE
                || cursor == Cursor.S_RESIZE
                || cursor == Cursor.SW_RESIZE
                || cursor == Cursor.W_RESIZE
                || cursor == Cursor.NW_RESIZE;
    }

    // ================= TITLE BAR HIT TEST =================
    private boolean isWindowControl(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node current = node;
        while (current != null) {
            if (current instanceof ButtonBase) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    // ================= WINDOW BUTTON ACTIONS =================
    private void wireWindowButtons() {
        bindWindowAction(closeButton, () -> stage.close());
        bindWindowAction(minimizeButton, () -> stage.setIconified(true));
        bindWindowAction(maximizeButton, this::toggleMaximize);
    }

    // ================= BUTTON HELPER =================
    private void bindWindowAction(Button button, Runnable action) {
        button.setOnAction(e -> {
            if (stage != null) {
                action.run();
            }
        });
    }

    // ================= MAXIMIZE TOGGLE =================
    private void toggleMaximize() {
        if (stage == null) {
            return;
        }
        stage.setMaximized(!stage.isMaximized());
        updateWindowChromeStateDeferred();
    }

    // ================= MAXIMIZED CSS STATE =================
    private void updateWindowChromeStateDeferred() {
        Platform.runLater(() -> {
            if (stage == null) {
                return;
            }
            boolean isMaximized = stage.isMaximized() || stage.isFullScreen() || isWindowFillingScreen();
            updateMaximizedClass(isMaximized);
        });
    }
    
    private boolean isWindowFillingScreen() {
        if (stage == null) {
            return false;
        }
        try {
            Rectangle2D visualBounds = Screen.getScreensForRectangle(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()
            ).get(0).getVisualBounds();
            
            // Check if window is filling the screen (within 10px tolerance)
            return Math.abs(stage.getX() - visualBounds.getMinX()) < 10 &&
                   Math.abs(stage.getY() - visualBounds.getMinY()) < 10 &&
                   Math.abs(stage.getWidth() - visualBounds.getWidth()) < 10 &&
                   Math.abs(stage.getHeight() - visualBounds.getHeight()) < 10;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateMaximizedClass(boolean maximized) {
        if (appShell == null || windowRoot == null) {
            return;
        }
        // Update maximize button icon: □ (maximize) ↔ ❐ (restore)
        if (maximizeButton != null) {
            maximizeButton.setText(maximized ? "\u2750" : "\u25A1");
        }
        if (maximized) {
            if (!appShell.getStyleClass().contains("maximized")) {
                appShell.getStyleClass().add("maximized");
            }
            if (!windowRoot.getStyleClass().contains("maximized")) {
                windowRoot.getStyleClass().add("maximized");
            }
        } else {
            appShell.getStyleClass().remove("maximized");
            windowRoot.getStyleClass().remove("maximized");
                if (windowRootClip != null) {
                    windowRootClip.setArcHeight(maximized ? 0 : 32);
                }
        }
        if (shellClip != null) {
            shellClip.setArcWidth(maximized ? 0 : 32);
            shellClip.setArcHeight(maximized ? 0 : 32);
        }
    }

    private void createNewConversation() {
        Conversation conversation = chatService.createConversation();
        chatList.getItems().add(conversation);
        reorderConversations();
        chatList.getSelectionModel().select(conversation);
        loadConversation(conversation);
        chatList.refresh();
    }

    private void deleteConversation(Conversation conversation) {
        if (conversation == null || chatList == null) {
            return;
        }

        List<Conversation> items = chatList.getItems();
        int index = items.indexOf(conversation);
        if (index < 0) {
            return;
        }

        boolean wasSelected = chatList.getSelectionModel().getSelectedItem() == conversation;
        items.remove(index);

        if (items.isEmpty()) {
            createNewConversation();
            return;
        }

        if (wasSelected) {
            int nextIndex = Math.min(index, items.size() - 1);
            chatList.getSelectionModel().select(nextIndex);
        }
        chatList.refresh();
    }

    private void togglePinConversation(Conversation conversation) {
        if (conversation == null) {
            return;
        }
        conversation.setPinned(!conversation.isPinned());
        reorderConversations();
        chatList.getSelectionModel().select(conversation);
        chatList.refresh();
    }

    private void reorderConversations() {
        List<Conversation> current = new ArrayList<>(chatList.getItems());
        List<Conversation> pinned = new ArrayList<>();
        List<Conversation> unpinned = new ArrayList<>();

        for (Conversation conv : current) {
            if (conv.isPinned()) {
                pinned.add(conv);
            } else {
                unpinned.add(conv);
            }
        }

        chatList.getItems().setAll(pinned);
        chatList.getItems().addAll(unpinned);
    }

    private int getPinnedSectionSize() {
        int pinnedCount = 0;
        for (Conversation conv : chatList.getItems()) {
            if (conv.isPinned()) {
                pinnedCount++;
            }
        }
        return pinnedCount;
    }

    // ================= CHAT VIEW LOADING =================
    private void loadConversation(Conversation conversation) {
        try {
            // ---- Load Chat FXML + Inject Conversation ----
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Node chatPane = loader.load();
            ChatController controller = loader.getController();
            controller.setChatService(chatService);
            controller.setConversation(conversation);
            controller.setOnConversationUpdated(chatList::refresh);

            // ---- Replace Center Content + Stretch to Fill ----
            chatContainer.getChildren().setAll(chatPane);
            VBox.setVgrow(chatPane, Priority.ALWAYS);
            if (chatPane instanceof Region region) {
                region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void applyRoundedClip() {
        if (windowRoot != null) {
            windowRootClip = new Rectangle();
            windowRootClip.setArcWidth(32);
            windowRootClip.setArcHeight(32);
            windowRootClip.widthProperty().bind(windowRoot.widthProperty());
            windowRootClip.heightProperty().bind(windowRoot.heightProperty());
            windowRoot.setClip(windowRootClip);
        }
        if (appShell != null) {
            shellClip = new Rectangle();
            shellClip.setArcWidth(32);
            shellClip.setArcHeight(32);
            shellClip.widthProperty().bind(appShell.widthProperty());
            shellClip.heightProperty().bind(appShell.heightProperty());
            appShell.setClip(shellClip);
        }
    }

    // ================= POPUP ROUNDED CORNERS =================
    private void fixPopupTransparency() {
        // On Windows, popup windows (context menus) have a native opaque rectangular
        // background. Setting the scene fill to transparent removes that background
        // so CSS border-radius actually produces visible rounded corners.
        Window.getWindows().addListener((javafx.collections.ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Window w : change.getAddedSubList()) {
                        if (w instanceof PopupWindow popup) {
                            if (popup.getScene() != null) {
                                stylePopupWindow(popup);
                                Platform.runLater(() -> stylePopupWindow(popup));
                            }
                            w.sceneProperty().addListener((obs, oldScene, newScene) -> {
                                if (newScene != null) {
                                    stylePopupWindow(popup);
                                    Platform.runLater(() -> stylePopupWindow(popup));
                                }
                            });
                        }
                    }
                }
            }
        });
        // Also handle any popups already open
        for (Window w : Window.getWindows()) {
            if (w instanceof PopupWindow popup && w.getScene() != null) {
                stylePopupWindow(popup);
                Platform.runLater(() -> stylePopupWindow(popup));
            }
        }
    }

    private void stylePopupWindow(PopupWindow popup) {
        if (popup == null || popup.getScene() == null) {
            return;
        }
        Parent popupRoot = popup.getScene().getRoot();
        if (popupRoot == null) {
            return;
        }

        boolean isComboPopup = popupRoot.getStyleClass().contains("combo-box-popup")
                || popupRoot.lookup(".combo-box-popup") != null;

        if (isComboPopup) {
            String modeClass = THEME_MODE.getOrDefault(committedThemeKey, "theme-dark");
            popupRoot.getStyleClass().removeAll("theme-dark", "theme-light");
            popupRoot.getStyleClass().add(modeClass);
            // Keep popup scene transparent so no rectangular frame appears behind
            // the curved dropdown list view.
            popup.getScene().setFill(Color.TRANSPARENT);
        } else {
            popup.getScene().setFill(Color.TRANSPARENT);
        }
    }

    private void ensureModalOverlay() {
        if (windowRoot == null || modalOverlay != null) {
            return;
        }
        Region overlay = new Region();
        overlay.getStyleClass().add("modal-overlay");
        overlay.setManaged(false);
        overlay.setVisible(false);
        overlay.setOpacity(0);
        // Never intercept pointer events intended for dialog controls.
        overlay.setMouseTransparent(true);
        overlay.prefWidthProperty().bind(windowRoot.widthProperty());
        overlay.prefHeightProperty().bind(windowRoot.heightProperty());
        windowRoot.getChildren().add(overlay);
        modalOverlay = overlay;
    }

    private void setModalBackdropActive(boolean active) {
        if (windowRoot == null || appShell == null) {
            return;
        }
        ensureModalOverlay();
        if (modalOverlay == null) {
            return;
        }

        if (active) {
            modalDepth++;
            if (modalDepth > 1) {
                return;
            }
            boolean blurEnabled = settingsManager.getBoolean("appearance.modalBlurEnabled", true);
            double blurRadius = Math.max(0.0, Math.min(12.0, settingsManager.getDouble("appearance.modalBlurRadius", 5.5)));
            double overlayOpacity = blurEnabled && blurRadius > 0.0 ? 0.24 : 0.0;
            if (!windowRoot.getChildren().contains(modalOverlay)) {
                windowRoot.getChildren().add(modalOverlay);
            }
            modalOverlay.toFront();
            modalOverlay.setVisible(true);
            appShell.setEffect(modalBlur);
            animateBackdrop(blurEnabled ? blurRadius : 0.0, overlayOpacity, null);
            return;
        }

        if (modalDepth == 0) {
            return;
        }
        modalDepth--;
        if (modalDepth > 0) {
            return;
        }
        animateBackdrop(0.0, 0.0, () -> {
            modalOverlay.setVisible(false);
            appShell.setEffect(null);
        });
    }

    private void updateModalBackdropPreview(boolean blurEnabled, double blurRadius) {
        if (windowRoot == null || appShell == null || modalDepth <= 0) {
            return;
        }
        ensureModalOverlay();
        if (modalOverlay == null) {
            return;
        }

        double clampedRadius = Math.max(0.0, Math.min(12.0, blurRadius));
        double overlayOpacity = blurEnabled && clampedRadius > 0.0 ? 0.24 : 0.0;

        if (!windowRoot.getChildren().contains(modalOverlay)) {
            windowRoot.getChildren().add(modalOverlay);
        }
        modalOverlay.toFront();
        modalOverlay.setVisible(true);
        appShell.setEffect(modalBlur);
        animateBackdrop(blurEnabled ? clampedRadius : 0.0, overlayOpacity, null);
    }

    private void animateBackdrop(double blurRadius, double overlayOpacity, Runnable onFinished) {
        if (modalBackdropTimeline != null) {
            modalBackdropTimeline.stop();
        }
        modalBackdropTimeline = new Timeline(
                new KeyFrame(Duration.millis(170),
                        new javafx.animation.KeyValue(modalBlur.radiusProperty(), blurRadius),
                        new javafx.animation.KeyValue(modalOverlay.opacityProperty(), overlayOpacity))
        );
        if (onFinished != null) {
            modalBackdropTimeline.setOnFinished(e -> onFinished.run());
        }
        modalBackdropTimeline.play();
    }

    private void showDialogWithBackdrop(Stage dialog) {
        setModalBackdropActive(true);
        try {
            dialog.showAndWait();
        } finally {
            setModalBackdropActive(false);
        }
    }

    // ================= THEME MENU =================
    private void wireThemeMenu() {
        // ---- Mutually Exclusive Theme Items ----
        ToggleGroup group = new ToggleGroup();

        themeMenuBindings.clear();
        themeMenuBindings.put(themeDarkPurpleItem, "theme-dark-purple");
        themeMenuBindings.put(themeDarkGreenItem, "theme-dark-green");
        themeMenuBindings.put(themeDarkOceanItem, "theme-dark-ocean");
        themeMenuBindings.put(themeDarkEmberItem, "theme-dark-ember");
        themeMenuBindings.put(themeDarkSlateItem, "theme-dark-slate");
        themeMenuBindings.put(themeLightItem, "theme-light");
        themeMenuBindings.put(themeLightSkyItem, "theme-light-sky");
        themeMenuBindings.put(themeLightMintItem, "theme-light-mint");
        themeMenuBindings.put(themeLightRoseItem, "theme-light-rose");
        themeMenuBindings.put(themeLightSandItem, "theme-light-sand");

        for (RadioMenuItem item : themeMenuBindings.keySet()) {
            item.setToggleGroup(group);
        }

        // ---- Theme Switch Actions (commits the theme) ----
        themeMenuBindings.forEach((item, themeKey) -> item.setOnAction(e -> {
            committedThemeKey = themeKey;
            applyTheme(themeKey);
            settingsManager.set("appearance.theme", themeKey);
            settingsManager.save();
        }));

        // ---- Hover Preview: temporarily show theme on hover ----
        if (themeMenu != null) {
            themeMenu.setOnShowing(e -> Platform.runLater(() -> attachThemeHoverPreview(themeMenu)));
            themeMenu.setOnHidden(e -> applyTheme(committedThemeKey));
        }
    }

    // ================= MENU ICONS =================
    private void addMenuIcons() {
        // Add icons to each top-level menu item
        for (MenuItem item : settingsButton.getItems()) {
            if (item instanceof SeparatorMenuItem) continue;
            String text = item.getText();
            if (text == null) continue;
            switch (text) {
                case "Preferences" -> item.setGraphic(createMenuIcon("\u2699")); // ⚙ (but different from gear button)
                case "Themes" -> item.setGraphic(createMenuIcon("\uD83C\uDFA8")); // 🎨
                case "Settings" -> item.setGraphic(createMenuIcon("\uD83D\uDD27")); // 🔧
                case "About" -> item.setGraphic(createMenuIcon("\u2139")); // ℹ
            }
        }
    }

    private Label createMenuIcon(String icon) {
        Label label = new Label(icon);
        label.getStyleClass().add("gear-menu-icon");
        return label;
    }

    // ================= THEME HOVER PREVIEW =================
    private void attachThemeHoverPreview(Menu themeMenu) {
        // Build ordered list of theme keys for RadioMenuItems
        List<String> orderedKeys = new ArrayList<>();
        for (MenuItem item : themeMenu.getItems()) {
            if (item instanceof RadioMenuItem radio) {
                String key = themeMenuBindings.get(radio);
                if (key != null) orderedKeys.add(key);
            }
        }
        if (orderedKeys.isEmpty()) return;

        // Get the submenu popup from the first RadioMenuItem
        RadioMenuItem firstRadio = null;
        for (MenuItem item : themeMenu.getItems()) {
            if (item instanceof RadioMenuItem r) { firstRadio = r; break; }
        }
        if (firstRadio == null) return;

        var popup = firstRadio.getParentPopup();
        if (popup == null || popup.getSkin() == null) {
            if (themeMenu.isShowing()) {
                Platform.runLater(() -> attachThemeHoverPreview(themeMenu));
            }
            return;
        }

        Node popupContent = popup.getSkin().getNode();
        var radioNodes = popupContent.lookupAll(".radio-menu-item");

        int i = 0;
        for (Node node : radioNodes) {
            if (i >= orderedKeys.size()) break;
            final String themeKey = orderedKeys.get(i);
            node.setOnMouseEntered(me -> previewTheme(themeKey));
            i++;
        }
    }

    private void previewTheme(String themeKey) {
        String resolvedTheme = THEME_STYLESHEETS.containsKey(themeKey) ? themeKey : DEFAULT_THEME;
        String modeClass = THEME_MODE.getOrDefault(resolvedTheme, "theme-dark");
        windowRoot.getStyleClass().removeAll("theme-dark", "theme-light");
        windowRoot.getStyleClass().add(modeClass);
        applyThemeStylesheet(resolvedTheme);
    }

    // ================= PREFERENCES DIALOG =================
    @FXML
    private void openPreferences() {
        openSettingsDialog("Preferences", SettingsDialogController.DialogMode.PREFERENCES);
    }

    @FXML
    private void openSettings() {
        openSettingsDialog("Settings", SettingsDialogController.DialogMode.SETTINGS);
    }

    private void openSettingsDialog(String dialogTitle, SettingsDialogController.DialogMode mode) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            javafx.scene.Parent root = loader.load();
            SettingsDialogController controller = loader.getController();
            controller.setDialogMode(mode);
            controller.setOnSave(this::applySettingsFromManager);
                controller.setBlurPreviewListener(preview ->
                    updateModalBackdropPreview(preview.enabled(), preview.radius()));
            try {
                javafx.application.HostServices hs = (javafx.application.HostServices)
                        stage.getProperties().get("hostServices");
                if (hs != null) controller.setHostServices(hs);
            } catch (Exception ignored) {}

            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.initOwner(stage);
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            dialog.setTitle(dialogTitle);

            // Custom title bar with close button
            Label titleLabel = new Label(dialogTitle);
            titleLabel.getStyleClass().add("settings-title-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button closeBtn = new Button("\u2715");
            closeBtn.getStyleClass().add("settings-close-button");
            closeBtn.setOnAction(e -> dialog.close());
            HBox titleBarBox = new HBox(titleLabel, spacer, closeBtn);
            titleBarBox.getStyleClass().add("settings-title-bar");
            titleBarBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Draggable title bar
            final double[] dragOffset = new double[2];
            titleBarBox.setOnMousePressed(e -> {
                dragOffset[0] = e.getSceneX();
                dragOffset[1] = e.getSceneY();
            });
            titleBarBox.setOnMouseDragged(e -> {
                dialog.setX(e.getScreenX() - dragOffset[0]);
                dialog.setY(e.getScreenY() - dragOffset[1]);
            });

            VBox wrapper = new VBox(titleBarBox, root);
            VBox.setVgrow(root, Priority.ALWAYS);
            wrapper.getStyleClass().addAll("window-root", "settings-root");
            String modeClass = THEME_MODE.getOrDefault(committedThemeKey, "theme-dark");
            wrapper.getStyleClass().add(modeClass);

            // Remove settings-root from the inner BorderPane so only wrapper has it
            root.getStyleClass().remove("settings-root");

            // Rounded clip
            Rectangle clip = new Rectangle(680, 540);
            clip.setArcWidth(32);
            clip.setArcHeight(32);
            clip.widthProperty().bind(wrapper.widthProperty());
            clip.heightProperty().bind(wrapper.heightProperty());
            wrapper.setClip(clip);

            javafx.scene.Scene scene = new javafx.scene.Scene(wrapper, 680, 540);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            if (activeThemeStylesheet != null) {
                scene.getStylesheets().add(activeThemeStylesheet);
            }
            dialog.setScene(scene);
            dialog.setResizable(false);
            showDialogWithBackdrop(dialog);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ================= BUTTON SVG ICONS =================
    private void setButtonIcons() {
        // Person silhouette for profile
        Region personIcon = new Region();
        personIcon.getStyleClass().add("profile-person-icon");
        personIcon.setPrefSize(16, 16);
        personIcon.setMinSize(16, 16);
        personIcon.setMaxSize(16, 16);
        profileButton.setGraphic(personIcon);
        profileButton.setText("");

        // Gear for settings
        Region gearIcon = new Region();
        gearIcon.getStyleClass().add("settings-gear-icon");
        gearIcon.setPrefSize(16, 16);
        gearIcon.setMinSize(16, 16);
        gearIcon.setMaxSize(16, 16);
        settingsButton.setGraphic(gearIcon);
        settingsButton.setText("");
    }

    // ================= PROFILE MENU ICONS =================
    private void addProfileMenuIcons() {
        for (MenuItem item : profileButton.getItems()) {
            if (item instanceof SeparatorMenuItem) continue;
            String text = item.getText();
            if (text == null) continue;
            switch (text) {
                case "User Profile" -> item.setGraphic(createMenuIcon("\uD83D\uDC64")); // 👤
                case "Account Information" -> item.setGraphic(createMenuIcon("\uD83D\uDCCB")); // 📋
                case "Logout" -> item.setGraphic(createMenuIcon("\uD83D\uDEAA")); // 🚪
            }
        }
    }

    // ================= DIALOG HELPER =================
    private javafx.stage.Stage createStyledDialog(String title, javafx.scene.Parent content, double width, double height, javafx.stage.Stage owner) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.initOwner(owner != null ? owner : stage);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        dialog.setTitle(title);

        // Title bar
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-title-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("\u2715");
        closeBtn.getStyleClass().add("settings-close-button");
        closeBtn.setOnAction(e -> dialog.close());
        HBox titleBarBox = new HBox(titleLabel, spacer, closeBtn);
        titleBarBox.getStyleClass().add("settings-title-bar");
        titleBarBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Draggable
        final double[] dragOff = new double[2];
        titleBarBox.setOnMousePressed(e -> { dragOff[0] = e.getSceneX(); dragOff[1] = e.getSceneY(); });
        titleBarBox.setOnMouseDragged(e -> { dialog.setX(e.getScreenX() - dragOff[0]); dialog.setY(e.getScreenY() - dragOff[1]); });

        VBox wrapper = new VBox(titleBarBox, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        wrapper.getStyleClass().addAll("window-root", "settings-root");
        // Apply the current theme mode class so CSS looked-up colors resolve
        String modeClass = THEME_MODE.getOrDefault(committedThemeKey, "theme-dark");
        wrapper.getStyleClass().add(modeClass);
        content.getStyleClass().remove("settings-root");

        // Rounded clip
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(wrapper.widthProperty());
        clip.heightProperty().bind(wrapper.heightProperty());
        wrapper.setClip(clip);

        javafx.scene.Scene scene = new javafx.scene.Scene(wrapper, width, height);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        if (activeThemeStylesheet != null) {
            scene.getStylesheets().add(activeThemeStylesheet);
        }
        dialog.setScene(scene);
        dialog.setResizable(false);
        return dialog;
    }

    // ================= PROFILE PANEL =================
    @FXML
    private void openUserProfile() {
        String userName = settingsManager.getString("profile.name", "Cortex User");
        String userEmail = settingsManager.getString("profile.email", "user@example.com");
        String userPlan = settingsManager.getString("profile.plan", "Free");

        // Avatar circle
        String initials = buildInitials(userName);
        Label avatarLabel = new Label(initials);
        avatarLabel.getStyleClass().add("profile-avatar");

        Label nameLabel = new Label(userName);
        nameLabel.getStyleClass().add("profile-detail-name");

        Label emailLabel = new Label(userEmail);
        emailLabel.getStyleClass().add("profile-detail-email");

        Label planLabel = new Label("Plan: " + userPlan);
        planLabel.getStyleClass().add("profile-detail-plan");

        javafx.scene.control.Button editBtn = new javafx.scene.control.Button("Edit Profile");
        editBtn.getStyleClass().add("profile-edit-button");

        VBox content = new VBox(12, avatarLabel, nameLabel, emailLabel, planLabel, editBtn);
        content.getStyleClass().add("profile-panel-root");
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPadding(new javafx.geometry.Insets(28, 36, 28, 36));

        javafx.stage.Stage dialog = createStyledDialog("User Profile", content, 340, 360, null);
        editBtn.setOnAction(e -> openEditProfile(dialog, nameLabel, emailLabel, planLabel, avatarLabel));
        dialog.showAndWait();
    }

    private String buildInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return ("" + parts[0].charAt(0)).toUpperCase();
    }

    private void openEditProfile(javafx.stage.Stage ownerDialog, Label nameLabel, Label emailLabel, Label planLabel, Label avatarLabel) {
        javafx.scene.control.TextField nameField = new javafx.scene.control.TextField(nameLabel.getText());
        nameField.setPromptText("Name");
        nameField.getStyleClass().add("profile-edit-field");

        javafx.scene.control.TextField emailField = new javafx.scene.control.TextField(emailLabel.getText());
        emailField.setPromptText("Email");
        emailField.getStyleClass().add("profile-edit-field");

        javafx.scene.control.Button saveBtn = new javafx.scene.control.Button("Save");
        saveBtn.getStyleClass().add("profile-edit-button");

        VBox editContent = new VBox(12,
                new Label("Name:"), nameField,
                new Label("Email:"), emailField,
                saveBtn);
        editContent.getStyleClass().add("profile-panel-root");
        editContent.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        editContent.setPadding(new javafx.geometry.Insets(20, 30, 20, 30));

        javafx.stage.Stage editDialog = createStyledDialog("Edit Profile", editContent, 340, 320, ownerDialog);
        saveBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();
            String newEmail = emailField.getText().trim();
            if (!newName.isEmpty()) {
                settingsManager.set("profile.name", newName);
                nameLabel.setText(newName);
                avatarLabel.setText(buildInitials(newName));
            }
            if (!newEmail.isEmpty()) {
                settingsManager.set("profile.email", newEmail);
                emailLabel.setText(newEmail);
            }
            settingsManager.save();
            editDialog.close();
        });
        editDialog.showAndWait();
    }

    @FXML
    private void openAccountInfo() {
        String userName = settingsManager.getString("profile.name", "Cortex User");
        String userEmail = settingsManager.getString("profile.email", "user@example.com");
        String userPlan = settingsManager.getString("profile.plan", "Free");

        Label heading = new Label("Account Information");
        heading.getStyleClass().add("about-app-name");

        Label nameRow = new Label("Name: " + userName);
        nameRow.getStyleClass().add("profile-detail-email");

        Label emailRow = new Label("Email: " + userEmail);
        emailRow.getStyleClass().add("profile-detail-email");

        Label planRow = new Label("Plan: " + userPlan);
        planRow.getStyleClass().add("profile-detail-plan");

        VBox content = new VBox(12, heading, nameRow, emailRow, planRow);
        content.getStyleClass().add("profile-panel-root");
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPadding(new javafx.geometry.Insets(28, 36, 28, 36));

        javafx.stage.Stage dialog = createStyledDialog("Account Information", content, 360, 280, null);
        dialog.showAndWait();
    }

    @FXML
    private void handleLogout() {
        Label icon = new Label("!");
        icon.getStyleClass().add("logout-confirm-icon");

        Label title = new Label("Confirm Logout");
        title.getStyleClass().add("logout-confirm-title");

        Label message = new Label("Are you sure you want to logout from this session?");
        message.setWrapText(true);
        message.getStyleClass().add("logout-confirm-message");

        Button noButton = new Button("Cancel");
        noButton.getStyleClass().add("logout-confirm-no");

        Button yesButton = new Button("Logout");
        yesButton.getStyleClass().add("logout-confirm-yes");

        HBox actions = new HBox(10, noButton, yesButton);
        actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        actions.getStyleClass().add("logout-confirm-actions");

        HBox header = new HBox(12, icon, title);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox content = new VBox(14, header, message, actions);
        content.getStyleClass().add("logout-confirm-root");
        content.setPadding(new javafx.geometry.Insets(20, 24, 20, 24));

        javafx.stage.Stage dialog = createStyledDialog("Logout", content, 420, 220, null);
        final boolean[] confirmed = {false};

        noButton.setOnAction(e -> dialog.close());
        yesButton.setOnAction(e -> {
            confirmed[0] = true;
            dialog.close();
        });

        showDialogWithBackdrop(dialog);

        if (confirmed[0]) {
            // Clear session state — future auth integration point
            settingsManager.set("profile.name", "Cortex User");
            settingsManager.set("profile.email", "user@example.com");
            settingsManager.save();
        }
    }

    // ================= ABOUT DIALOG =================
    @FXML
    private void openAbout() {
        VBox content = AboutSectionView.createAboutContent(null);

        javafx.stage.Stage dialog = createStyledDialog("About Cortex", content, 360, 260, null);
        showDialogWithBackdrop(dialog);
    }

    private void applySettingsFromManager() {
        // Re-apply theme if changed
        String savedTheme = settingsManager.getString("appearance.theme", DEFAULT_THEME);
        if (!savedTheme.equals(committedThemeKey)) {
            committedThemeKey = savedTheme;
            applyTheme(savedTheme);
        }
    }

    // ================= THEME APPLY =================
    private void applyTheme(String themeKey) {
        String resolvedTheme = THEME_STYLESHEETS.containsKey(themeKey) ? themeKey : DEFAULT_THEME;
        String modeClass = THEME_MODE.getOrDefault(resolvedTheme, "theme-dark");
        windowRoot.getStyleClass().removeAll("theme-dark", "theme-light");
        windowRoot.getStyleClass().add(modeClass);

        themeMenuBindings.forEach((item, key) -> item.setSelected(key.equals(resolvedTheme)));
        applyThemeStylesheet(resolvedTheme);
    }

    private void applyThemeStylesheet(String themeClass) {
        String cssResourcePath = THEME_STYLESHEETS.get(themeClass);
        if (cssResourcePath == null) {
            return;
        }
        if (windowRoot.getScene() == null) {
            Platform.runLater(() -> applyThemeStylesheet(themeClass));
            return;
        }

        String stylesheet = getClass().getResource(cssResourcePath) != null
                ? getClass().getResource(cssResourcePath).toExternalForm()
                : null;
        if (stylesheet == null) {
            return;
        }

        if (activeThemeStylesheet != null) {
            windowRoot.getScene().getStylesheets().remove(activeThemeStylesheet);
        }
        windowRoot.getScene().getStylesheets().add(stylesheet);
        activeThemeStylesheet = stylesheet;
    }
}
