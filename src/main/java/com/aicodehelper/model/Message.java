package com.aicodehelper.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable chat message model for both user and assistant messages.
 */
public final class Message {
    public enum Sender {
        USER,
        BOT
    }

    private final Sender sender;
    private final String content;
    private final LocalDateTime timestamp;
    private final boolean codeBlock;

    public Message(Sender sender, String content, LocalDateTime timestamp, boolean codeBlock) {
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.codeBlock = codeBlock;
    }

    public Sender getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public boolean isCodeBlock() {
        return codeBlock;
    }
}
