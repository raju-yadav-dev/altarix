package com.aicodehelper.ui;

import com.aicodehelper.util.AppConfig;
import com.cortex.util.IconResources;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * SidebarView displays the left navigation panel with:
 * - App logo and branding
 * - "New Chat" button for starting conversations
 * - Search/filter field for conversation history
 * - Scrollable list of conversation history
 *
 * Responsibilities:
 * - Display conversation history
 * - Expose new chat button
 * - Expose history list for interaction
 *
 * Design Pattern: View component in MVP architecture
 * - Does NOT contain business logic
 * - Purely presentational
 * - Exposes components for external event wiring
 *
 * Future Enhancements:
 * - Right-click menu to delete conversations
 * - Drag & drop to reorder conversations
 * - Conversation search/filtering
 * - Conversation preview on hover
 */
public class SidebarView extends VBox {
    private final Button newChatButton = new Button(AppConfig.NEW_CHAT_BUTTON_TEXT);
    private final ListView<String> historyView = new ListView<>();
    private final TextField searchField = new TextField();

    /**
     * Constructs the sidebar with all UI elements.
     */
    public SidebarView() {
        getStyleClass().add("sidebar");
        setPadding(new Insets(16));
        setSpacing(12);
        setPrefWidth(AppConfig.SIDEBAR_PREFERRED_WIDTH);
        setMinWidth(AppConfig.SIDEBAR_MIN_WIDTH);
        setMaxWidth(AppConfig.SIDEBAR_MAX_WIDTH);

        // ===== LOGO & BRANDING =====
        HBox logoContainer = new HBox(8);
        logoContainer.setAlignment(Pos.CENTER_LEFT);
        
        // Icon image
        try {
            Image iconImage = IconResources.loadBestFit(getClass(), 32);
            if (iconImage != null) {
                ImageView iconView = new ImageView(iconImage);
                iconView.setFitWidth(24);
                iconView.setFitHeight(24);
                iconView.setPreserveRatio(true);
                logoContainer.getChildren().add(iconView);
            }
        } catch (Exception e) {
            System.err.println("Failed to load icon: " + e.getMessage());
        }
        
        Label logo = new Label(AppConfig.APP_NAME);
        logo.getStyleClass().add("logo");
        logoContainer.getChildren().add(logo);

        Label subtitle = new Label("AI Coding Assistant");
        subtitle.getStyleClass().add("logo-subtitle");

        // ===== NEW CHAT BUTTON =====
        newChatButton.getStyleClass().add("new-chat-button");
        newChatButton.setMaxWidth(Double.MAX_VALUE);
        newChatButton.setPrefHeight(40);

        // ===== SEARCH FIELD =====
        searchField.setPromptText(AppConfig.SEARCH_PLACEHOLDER);
        searchField.getStyleClass().add("sidebar-search");
        searchField.setFocusTraversable(true);

        // ===== HISTORY LIST =====
        historyView.getStyleClass().add("history-list");
        VBox.setVgrow(historyView, Priority.ALWAYS);

        // Add all components
        getChildren().addAll(logoContainer, subtitle, newChatButton, searchField, historyView);
        setAlignment(Pos.TOP_LEFT);
    }

    // ========== PUBLIC API ==========

    public Button getNewChatButton() {
        return newChatButton;
    }

    public ListView<String> getHistoryView() {
        return historyView;
    }

    public TextField getSearchField() {
        return searchField;
    }
}
