package com.example.chatbot.service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Loads language configurations from /config/languages.json and provides
 * detection, resolution, and lookup support for code execution.
 */
public class LanguageConfigService {

    private static final String CONFIG_PATH = "/config/languages.json";

    private final Map<String, LanguageEntry> languagesByKey = new HashMap<>();
    private final Map<String, String> extensionToKey = new HashMap<>();
    private final Map<String, String> aliasToKey = new HashMap<>();
    private final Map<String, Boolean> commandAvailabilityCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> runtimeResolutionCache = new ConcurrentHashMap<>();
    private final SettingsManager settingsManager = SettingsManager.getInstance();

    public LanguageConfigService() {
        loadConfig();
    }

    // ================= CONFIG LOADING =================

    private void loadConfig() {
        try (InputStream is = getClass().getResourceAsStream(CONFIG_PATH)) {
            if (is == null) {
                System.err.println("[LanguageConfigService] languages.json not found at " + CONFIG_PATH);
                return;
            }
            Gson gson = new Gson();
            Map<String, LanguageEntry> raw = gson.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, LanguageEntry>>() {}.getType()
            );
            if (raw == null) return;

            for (Map.Entry<String, LanguageEntry> entry : raw.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                LanguageEntry lang = entry.getValue();
                lang.key = key;
                languagesByKey.put(key, lang);

                if (lang.extensions != null) {
                    for (String ext : lang.extensions) {
                        extensionToKey.put(ext.toLowerCase(Locale.ROOT), key);
                    }
                }
                if (lang.aliases != null) {
                    for (String alias : lang.aliases) {
                        aliasToKey.put(alias.toLowerCase(Locale.ROOT), key);
                    }
                }
                // The key itself is also an alias
                aliasToKey.put(key, key);
            }
        } catch (Exception ex) {
            System.err.println("[LanguageConfigService] Failed to load languages.json: " + ex.getMessage());
        }
    }

    // ================= LANGUAGE LOOKUP =================

    /** Detect language key from a file extension (e.g. ".py" → "python"). */
    public String detectLanguageFromExtension(String extension) {
        if (extension == null) return null;
        String ext = extension.toLowerCase(Locale.ROOT);
        if (!ext.startsWith(".")) ext = "." + ext;
        return extensionToKey.get(ext);
    }

    /** Detect language key from a file name (e.g. "main.cpp" → "cpp"). */
    public String detectLanguageFromFileName(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return null;
        return detectLanguageFromExtension(fileName.substring(dot));
    }

    /** Normalize a language alias/name to its canonical key. */
    public String normalizeLanguage(String language) {
        if (language == null) return "text";
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("txt") || normalized.equals("text") || normalized.equals("plaintext")) {
            return "text";
        }
        String key = aliasToKey.get(normalized);
        return key != null ? key : normalized;
    }

    /** Get the language entry for a canonical key. */
    public LanguageEntry getLanguage(String key) {
        return languagesByKey.get(key);
    }

    /** Get all configured language entries. */
    public Map<String, LanguageEntry> getAllLanguages() {
        return Collections.unmodifiableMap(languagesByKey);
    }

    /** Check if a language key is runnable (has a config entry). */
    public boolean isRunnable(String languageKey) {
        return languagesByKey.containsKey(languageKey);
    }

    // ================= RUNTIME DETECTION =================

    /** Check if a runtime is available for the given language. */
    public boolean isRuntimeAvailable(String languageKey) {
        LanguageEntry lang = languagesByKey.get(languageKey);
        if (lang == null || lang.detectCommands == null) return false;
        for (String cmd : lang.detectCommands) {
            if (isCommandAvailable(cmd)) return true;
        }
        return false;
    }

    /** Resolve the first available command for a language from its detectCommands list. */
    public String resolveCommand(String languageKey) {
        String configuredPath = resolveConfiguredRuntimeCommand(languageKey);
        if (configuredPath != null) {
            return configuredPath;
        }

        LanguageEntry lang = languagesByKey.get(languageKey);
        if (lang == null || lang.detectCommands == null) return null;
        for (String cmd : lang.detectCommands) {
            if (isCommandAvailable(cmd)) return cmd;
        }
        return null;
    }

    private String resolveConfiguredRuntimeCommand(String languageKey) {
        String runtimeKey = switch (languageKey) {
            case "python" -> "runtime.pythonPath";
            case "javascript" -> "runtime.nodePath";
            case "java" -> "runtime.javaPath";
            case "c", "cpp" -> "runtime.gccPath";
            case "typescript" -> "runtime.tsNodePath";
            case "ruby" -> "runtime.rubyPath";
            case "php" -> "runtime.phpPath";
            case "lua" -> "runtime.luaPath";
            case "perl" -> "runtime.perlPath";
            case "r" -> "runtime.rPath";
            case "dart" -> "runtime.dartPath";
            case "groovy" -> "runtime.groovyPath";
            case "swift" -> "runtime.swiftPath";
            case "julia" -> "runtime.juliaPath";
            default -> null;
        };

        if (runtimeKey == null) {
            return null;
        }

        String configured = settingsManager.getString(runtimeKey, "").trim();
        return configured.isEmpty() ? null : configured;
    }

    /**
     * Detect all installed languages and return a map of language display name → installed command.
     */
    public Map<String, String> detectInstalledLanguages() {
        Map<String, String> installed = new HashMap<>();
        for (LanguageEntry lang : languagesByKey.values()) {
            String cmd = resolveCommand(lang.key);
            if (cmd != null) {
                installed.put(lang.displayName, cmd);
            }
        }
        return installed;
    }

    /** Check if a system command is available (cached). */
    public boolean isCommandAvailable(String commandName) {
        if (commandName == null || commandName.isBlank()) return false;
        Boolean cached = commandAvailabilityCache.get(commandName);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }

        boolean available = probeCommandAvailability(commandName);
        if (available) {
            commandAvailabilityCache.put(commandName, true);
        } else {
            // Do not cache misses forever; environment/PATH can change while app is running.
            commandAvailabilityCache.remove(commandName);
        }
        return available;
    }

    private boolean probeCommandAvailability(String commandName) {
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<String> probe = isWindows
                ? List.of("cmd", "/c", "where", commandName)
                : List.of("sh", "-lc", "command -v " + commandName);
        try {
            Process process = new ProcessBuilder(probe).redirectErrorStream(true).start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    // ================= LANGUAGE ENTRY MODEL =================

    public static class LanguageEntry {
        public String key;
        public String displayName;
        public List<String> extensions;
        public List<String> aliases;
        public String command;
        public String compileCommand;
        public List<String> detectCommands;
        public boolean needsCompilation;
        public int timeout = 10;

        public String getDisplayName() {
            return displayName != null ? displayName : (key != null ? key : "Unknown");
        }

        public int getTimeoutSeconds() {
            return timeout > 0 ? timeout : 10;
        }
    }
}
