package com.example.chatbot.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Singleton settings manager backed by a JSON configuration file.
 * Settings are organized by dot-separated keys (e.g. "appearance.uiFontSize").
 */
public final class SettingsManager {
    private static final String CONFIG_FILE_NAME = "cortex-settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();
    private static final SettingsManager INSTANCE = new SettingsManager();

    private final Map<String, Object> settings = new LinkedHashMap<>();
    private final Map<String, Consumer<Object>> listeners = new ConcurrentHashMap<>();
    private Path configPath;

    private SettingsManager() {
        configPath = resolveConfigPath();
        populateDefaults();
        load();
    }

    public static SettingsManager getInstance() {
        return INSTANCE;
    }

    // ================= DEFAULTS =================
    private void populateDefaults() {
        // Appearance
        putDefault("appearance.theme", "theme-dark-purple");
        putDefault("appearance.uiFontSize", 14);
        putDefault("appearance.chatFontSize", 14);
        putDefault("appearance.codeFontSize", 13);
        putDefault("appearance.terminalFontSize", 13);
        putDefault("appearance.modalBlurEnabled", true);
        putDefault("appearance.modalBlurRadius", 5.5);

        // Chat Behavior
        putDefault("chat.responseStyle", "Detailed");
        putDefault("chat.streamingEnabled", true);
        putDefault("chat.autoScroll", true);
        putDefault("chat.historyEnabled", true);

        // Code Execution
        putDefault("execution.timeoutSeconds", 10);
        putDefault("execution.maxOutputSize", 50000);
        putDefault("execution.autoCleanTempFiles", true);
        putDefault("execution.confirmBeforeRun", false);

        // Terminal
        putDefault("terminal.defaultShell", "Cortex");
        putDefault("terminal.clearBeforeRun", false);
        putDefault("terminal.scrollbackSize", 10000);
        putDefault("terminal.showExecutionTime", true);

        // Language Runtime Paths
        putDefault("runtime.pythonPath", "");
        putDefault("runtime.nodePath", "");
        putDefault("runtime.javaPath", "");
        putDefault("runtime.gccPath", "");
        putDefault("runtime.tsNodePath", "");
        putDefault("runtime.rubyPath", "");
        putDefault("runtime.phpPath", "");
        putDefault("runtime.luaPath", "");
        putDefault("runtime.perlPath", "");
        putDefault("runtime.rPath", "");
        putDefault("runtime.dartPath", "");
        putDefault("runtime.groovyPath", "");
        putDefault("runtime.swiftPath", "");
        putDefault("runtime.juliaPath", "");

        // AI Model
        putDefault("ai.apiKey", "");
        putDefault("ai.baseUrl", "https://api.openai.com");
        putDefault("ai.modelName", "gpt-4.1-mini");
        putDefault("ai.temperature", 0.4);
        putDefault("ai.maxTokens", 4096);
        putDefault("ai.systemPrompt", "");

        // Privacy
        putDefault("privacy.saveChatHistory", true);

        // Profile
        putDefault("profile.name", "Cortex User");
        putDefault("profile.email", "user@example.com");
        putDefault("profile.plan", "Free");

        // Advanced
        putDefault("advanced.debugLogs", false);
        putDefault("advanced.experimentalFeatures", false);
    }

    private void putDefault(String key, Object value) {
        settings.putIfAbsent(key, value);
    }

    // ================= GETTERS =================
    public String getString(String key, String fallback) {
        Object value = settings.get(key);
        return value instanceof String s ? s : fallback;
    }

    public int getInt(String key, int fallback) {
        Object value = settings.get(key);
        if (value instanceof Number n) return n.intValue();
        return fallback;
    }

    public double getDouble(String key, double fallback) {
        Object value = settings.get(key);
        if (value instanceof Number n) return n.doubleValue();
        return fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = settings.get(key);
        if (value instanceof Boolean b) return b;
        return fallback;
    }

    // ================= SETTERS =================
    public void set(String key, Object value) {
        Object previous = settings.put(key, value);
        if (!java.util.Objects.equals(previous, value)) {
            notifyListener(key, value);
        }
    }

    // ================= LISTENERS =================
    public void addListener(String key, Consumer<Object> listener) {
        listeners.put(key, listener);
    }

    public void removeListener(String key) {
        listeners.remove(key);
    }

    private void notifyListener(String key, Object value) {
        Consumer<Object> listener = listeners.get(key);
        if (listener != null) {
            listener.accept(value);
        }
    }

    // ================= PERSISTENCE =================
    public void load() {
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return;
        }
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            Map<String, Object> loaded = GSON.fromJson(json, MAP_TYPE);
            if (loaded != null) {
                // Merge loaded values over defaults (preserves new defaults for new keys)
                for (Map.Entry<String, Object> entry : loaded.entrySet()) {
                    settings.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception ex) {
            System.err.println("[SettingsManager] Failed to load settings: " + ex.getMessage());
        }
    }

    public void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(settings, MAP_TYPE);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("[SettingsManager] Failed to save settings: " + ex.getMessage());
        }
    }

    // ================= CONFIG PATH =================
    private Path resolveConfigPath() {
        // Store in user home under .cortex
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return Path.of(CONFIG_FILE_NAME);
        }
        return Path.of(home, ".cortex", CONFIG_FILE_NAME);
    }

    // ================= BULK ACCESS (for dialog) =================
    public Map<String, Object> getAllSettings() {
        return new LinkedHashMap<>(settings);
    }

    public void clearConversationData() {
        // Placeholder - actual clearing depends on conversation storage implementation
        System.out.println("[SettingsManager] Clear all conversations requested.");
    }

    public void clearCache() {
        System.out.println("[SettingsManager] Clear cache requested.");
    }
}
