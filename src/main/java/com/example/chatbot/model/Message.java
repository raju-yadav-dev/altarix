package com.example.chatbot.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Message {
    // ================= MESSAGE SENDER =================
    public enum Sender { USER, BOT }

    // ================= DATA =================
    private final Sender sender;
    private String content;
    private final LocalDateTime timestamp;
    private final List<String> editHistory = new ArrayList<>();
    private LocalDateTime lastEditedAt;

    // ================= CONSTRUCTOR =================
    public Message(Sender sender, String content) {
        this.sender = sender;
        this.content = content == null ? "" : content;
        this.timestamp = LocalDateTime.now();
    }

    // ================= ACCESSORS =================
    public Sender getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public boolean isEdited() {
        return !editHistory.isEmpty();
    }

    public String getPreviousContent() {
        if (editHistory.isEmpty()) {
            return null;
        }
        return editHistory.get(editHistory.size() - 1);
    }

    public List<String> getEditHistory() {
        return Collections.unmodifiableList(editHistory);
    }

    public LocalDateTime getLastEditedAt() {
        return lastEditedAt;
    }

    public boolean editContent(String updatedContent) {
        String nextValue = updatedContent == null ? "" : updatedContent;
        if (Objects.equals(content, nextValue)) {
            return false;
        }
        editHistory.add(content);
        content = nextValue;
        lastEditedAt = LocalDateTime.now();
        return true;
    }
}
