package com.example.altarix.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Conversation {
    // ================= DATA =================
    private String title;
    private final List<Message> messages = new ArrayList<>();
    private boolean pinned;
    private boolean titleFinalized;

    // ================= CONSTRUCTOR =================
    public Conversation(String title) {
        this.title = title;
    }

    // ================= ACCESSORS =================
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isTitleFinalized() {
        return titleFinalized;
    }

    public void setTitleFinalized(boolean titleFinalized) {
        this.titleFinalized = titleFinalized;
    }

    // ================= MUTATION =================
    public void addMessage(Message message) {
        messages.add(message);
    }
}
