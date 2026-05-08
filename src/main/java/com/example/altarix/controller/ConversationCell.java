package com.example.altarix.controller;

import com.example.altarix.model.Conversation;
import javafx.beans.binding.Bindings;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/**
 * Cell that shows conversation title with inline rename and row options.
 */
public class ConversationCell extends ListCell<Conversation> {
    private static final double CHAT_ROW_MAX_WIDTH = 200;
    private final Consumer<Conversation> onDelete;
    private final Consumer<Conversation> onTogglePin;
    private TextField renameField;

    public ConversationCell(Consumer<Conversation> onDelete, Consumer<Conversation> onTogglePin) {
        this.onDelete = onDelete;
        this.onTogglePin = onTogglePin;
        setEditable(true);
    }

    @Override
    public void startEdit() {
        Conversation item = getItem();
        if (item == null) {
            return;
        }
        super.startEdit();
        if (renameField == null) {
            renameField = createRenameField();
        }
        renameField.setText(item.getTitle());
        setText(null);
        setGraphic(createRow(item, renameField, true));
        Platform.runLater(() -> {
            renameField.requestFocus();
            renameField.selectAll();
        });
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        Conversation item = getItem();
        if (item == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        setText(null);
        setGraphic(createRow(item, createTitleLabel(item), false));
    }

    @Override
    protected void updateItem(Conversation item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        if (isEditing()) {
            if (renameField == null) {
                renameField = createRenameField();
            }
            renameField.setText(item.getTitle());
            setText(null);
            setGraphic(createRow(item, renameField, true));
        } else {
            setText(null);
            setGraphic(createRow(item, createTitleLabel(item), false));
        }
    }

    private Label createTitleLabel(Conversation item) {
        Label titleLabel = new Label(item.isPinned() ? "\uD83D\uDCCC " + item.getTitle() : item.getTitle());
        titleLabel.getStyleClass().add("conversation-title");
        titleLabel.setWrapText(false);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setEllipsisString("...");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        return titleLabel;
    }

    private TextField createRenameField() {
        TextField field = new TextField();
        field.getStyleClass().add("conversation-rename-field");
        field.setOnAction(e -> commitInlineRename());
        field.focusedProperty().addListener((obs, oldFocused, isFocused) -> {
            if (!isFocused && isEditing()) {
                commitInlineRename();
            }
        });
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
                event.consume();
            }
        });
        field.setMinWidth(0);
        HBox.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private HBox createRow(Conversation item, javafx.scene.Node titleNode, boolean editing) {
        MenuItem pinItem = new MenuItem(item.isPinned() ? "Unpin Chat" : "Pin Chat");
        pinItem.setOnAction(e -> onTogglePin.accept(item));

        MenuItem renameItem = new MenuItem("Rename Chat");
        renameItem.setOnAction(e -> startEdit());

        MenuItem deleteItem = new MenuItem("Delete Chat");
        deleteItem.setOnAction(e -> onDelete.accept(item));

        ContextMenu optionsMenu = new ContextMenu(pinItem, renameItem, deleteItem);

        Button optionsButton = new Button("...");
        optionsButton.getStyleClass().add("conversation-options-button");
        optionsButton.setFocusTraversable(false);
        optionsButton.setVisible(!editing);
        optionsButton.setManaged(!editing);
        optionsButton.setOnAction(e -> {
            if (optionsMenu.isShowing()) {
                optionsMenu.hide();
            } else {
                optionsMenu.show(optionsButton, Side.BOTTOM, 0, 2);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, titleNode, spacer, optionsButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.prefWidthProperty().bind(Bindings.min(CHAT_ROW_MAX_WIDTH, Bindings.max(0, widthProperty().subtract(16))));
        row.maxWidthProperty().bind(Bindings.min(CHAT_ROW_MAX_WIDTH, Bindings.max(0, widthProperty().subtract(16))));
        return row;
    }

    private void commitInlineRename() {
        Conversation item = getItem();
        if (item == null || renameField == null) {
            cancelEdit();
            return;
        }
        String renamed = renameField.getText() == null ? "" : renameField.getText().trim();
        if (!renamed.isEmpty() && !renamed.equals(item.getTitle())) {
            item.setTitle(renamed);
            item.setTitleFinalized(true);
            if (getListView() != null) {
                getListView().refresh();
            }
        }
        cancelEdit();
    }
}
