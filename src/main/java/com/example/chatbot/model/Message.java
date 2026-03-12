package com.example.chatbot.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Message {
    // ================= MESSAGE SENDER =================
    public enum Sender { USER, BOT }

    // ================= DATA =================
    private final Sender sender;
    private String content;
    private final LocalDateTime timestamp;
    private final String imageFileName;
    private final String imageMimeType;
    private final byte[] imageData;
    private final List<String> editHistory = new ArrayList<>();
    private LocalDateTime lastEditedAt;

    // ================= CONSTRUCTOR =================
    public Message(Sender sender, String content) {
        this(sender, content, null, null, null);
    }

    public Message(Sender sender, String content, String imageFileName, String imageMimeType, byte[] imageData) {
        this.sender = sender;
        this.content = content == null ? "" : content;
        this.timestamp = LocalDateTime.now();
        byte[] normalizedImageData = imageData == null ? new byte[0] : Arrays.copyOf(imageData, imageData.length);
        this.imageData = normalizedImageData;
        this.imageFileName = normalizedImageData.length == 0
                ? null
                : (imageFileName == null || imageFileName.isBlank() ? "image" : imageFileName.trim());
        this.imageMimeType = normalizedImageData.length == 0
                ? null
                : (imageMimeType == null || imageMimeType.isBlank()
                ? "image/png"
                : imageMimeType.trim().toLowerCase(Locale.ROOT));
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

    public boolean hasImageAttachment() {
        return imageData.length > 0;
    }

    public String getImageFileName() {
        return imageFileName;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public byte[] getImageData() {
        return Arrays.copyOf(imageData, imageData.length);
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
