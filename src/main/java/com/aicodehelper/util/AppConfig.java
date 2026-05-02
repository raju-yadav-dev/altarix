package com.aicodehelper.util;

import com.example.chatbot.update.VersionProperties;

/**
 * Application-wide configuration and constants.
 * Centralizes all magic numbers, strings, and settings for easy maintenance.
 *
 * Best Practices Demonstrated:
 * - All constants are uppercase (Java convention)
 * - Grouped logically by feature (UI, timing, etc.)
 * - Documented with context about each constant
 * - Easy to externalize to properties/YAML files later
 */
public final class AppConfig {

    // ========== APPLICATION METADATA ==========
    public static final String APP_NAME = "Altarix";
    public static final String APP_VERSION = VersionProperties.getCurrentVersion();
    public static final String APP_DESCRIPTION = "AI-powered coding assistant for beginners";

    // ========== WINDOW CONFIGURATION ==========
    public static final double DEFAULT_WINDOW_WIDTH = 1200;
    public static final double DEFAULT_WINDOW_HEIGHT = 760;
    public static final double MIN_WINDOW_WIDTH = 780;
    public static final double MIN_WINDOW_HEIGHT = 540;
    public static final double WINDOW_WIDTH_RATIO = 0.9; // 90% of screen width
    public static final double WINDOW_HEIGHT_RATIO = 0.9; // 90% of screen height

    // ========== SIDEBAR CONFIGURATION ==========
    public static final double SIDEBAR_PREFERRED_WIDTH = 230;
    public static final double SIDEBAR_MIN_WIDTH = 190;
    public static final double SIDEBAR_MAX_WIDTH = 320;

    // ========== TIMING & ANIMATION ==========
    /** Duration for fade/slide animations when adding messages, in milliseconds */
    public static final int MESSAGE_ANIMATION_DURATION_MS = 300;
    
    /** Duration for smooth scroll to bottom animation, in milliseconds */
    public static final int SCROLL_ANIMATION_DURATION_MS = 300;
    
    /** Duration of the typing indicator animation, in milliseconds */
    public static final int TYPING_ANIMATION_DURATION_MS = 900;
    
    /** Duration for temporary status message (e.g., "Copied to clipboard"), in milliseconds */
    public static final int STATUS_MESSAGE_DURATION_MS = 1200;

    // ========== TEXT EDITOR CONFIGURATION ==========
    /** Default number of rows for input area */
    public static final int INPUT_AREA_DEFAULT_ROWS = 2;
    
    /** Minimum rows the input area can shrink to */
    public static final int INPUT_AREA_MIN_ROWS = 1;
    
    /** Maximum rows the input area can expand to */
    public static final int INPUT_AREA_MAX_ROWS = 4;

    // ========== MESSAGE BUBBLE CONFIGURATION ==========
    /** Maximum width of a message bubble, in pixels */
    public static final double BUBBLE_MAX_WIDTH = 700;
    
    /** Spacing between messages in the chat area, in pixels */
    public static final int MESSAGE_SPACING = 12;

    // ========== CHAT HISTORY CONFIGURATION ==========
    /** Maximum number of conversations to keep in memory (before cleaning oldest) */
    public static final int MAX_CONVERSATIONS_IN_MEMORY = 100;
    
    /** Maximum characters to display in conversation title */
    public static final int CONVERSATION_TITLE_MAX_LENGTH = 50;

    // ========== CODE DETECTION ==========
    /** Monospace fonts for code display */
    public static final String CODE_FONT_FAMILY = "Consolas, 'Courier New', monospace";
    
    /** Font size for code blocks, in points */
    public static final int CODE_FONT_SIZE = 12;

    // ========== THEME CONFIGURATION ==========
    public static final String DARK_MODE_CLASS = ""; // Default
    public static final String LIGHT_MODE_CLASS = "light-mode";
    public static final String CSS_PURPLE_THEME = "/styles/app.css";
    public static final String CSS_GREEN_THEME = "/styles/app-green.css";
    
    // Theme Names
    public static final String THEME_PURPLE_DARK = "Purple Dark";
    public static final String THEME_GREEN_DARK = "Green Dark";
    public static final String THEME_LIGHT = "Light Mode";

    // ========== LOGGING ==========
    public static final String LOG_LEVEL = "INFO"; // DEBUG, INFO, WARN, ERROR

    // ========== UI MESSAGES ==========
    public static final String TYPING_INDICATOR_TEXT = "AI is typing...";
    public static final String COPIED_CONFIRMATION_TEXT = "Copied to clipboard";
    public static final String EMPTY_INPUT_WARNING = "Please enter a message";
    public static final String NEW_CHAT_DEFAULT_TITLE = "New Chat";
    public static final String INPUT_PLACEHOLDER = "Ask a coding question, paste code, or describe an error...";
    public static final String SEARCH_PLACEHOLDER = "Search conversations...";

    // ========== BUTTON LABELS ==========
    public static final String NEW_CHAT_BUTTON_TEXT = "Start New Chat";
    public static final String SEND_BUTTON_ICON = "â†‘";
    public static final String COPY_BUTTON_TEXT = "Copy";
    public static final String THEME_BUTTON_TEXT = "Theme";
    public static final String THEME_BUTTON_LIGHT_TEXT = "Light";
    public static final String THEME_BUTTON_DARK_TEXT = "Dark";

    // ========== ERROR MESSAGES ==========
    public static final String ERROR_LOAD_CSS = "Failed to load CSS resource";
    public static final String ERROR_CREATE_MESSAGE = "Failed to create message";
    public static final String ERROR_INVALID_INPUT = "Invalid input provided";

    // ========== PRIVATE CONSTRUCTOR ==========
    private AppConfig() {
        // Utility class - no instances allowed
        throw new AssertionError("AppConfig is a utility class and cannot be instantiated");
    }

    /**
     * Example of retrieving responsive window size based on screen dimensions.
     * @param screenWidth The primary screen width
     * @param screenHeight The primary screen height
     * @return Calculated optimal window width
     */
    public static double calculateResponsiveWidth(double screenWidth) {
        return Math.min(DEFAULT_WINDOW_WIDTH, Math.max(MIN_WINDOW_WIDTH, screenWidth * WINDOW_WIDTH_RATIO));
    }

    /**
     * Example of retrieving responsive window height.
     * @param screenHeight The primary screen height
     * @return Calculated optimal window height
     */
    public static double calculateResponsiveHeight(double screenHeight) {
        return Math.min(DEFAULT_WINDOW_HEIGHT, Math.max(MIN_WINDOW_HEIGHT, screenHeight * WINDOW_HEIGHT_RATIO));
    }
}

