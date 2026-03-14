package com.example.chatbot.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Shared model and JSON helpers for multi-provider AI configuration.
 */
public final class AiProviderSetupSupport {
    public static final String SETTINGS_KEY = "ai.providerSetups";

    public static final String PROVIDER_GROQ = "groq";
    public static final String PROVIDER_GOOGLE = "google_ai_studio";
    public static final String PROVIDER_LEONARDO = "leonardo";
    public static final String PROVIDER_FREEPIK = "freepik";

    private static final Gson GSON = new Gson();
    private static final Type SETUP_LIST_TYPE = new TypeToken<List<ProviderSetup>>() { }.getType();

    private static final ProviderDefinition GROQ = new ProviderDefinition(
            PROVIDER_GROQ,
            "Groq",
            "https://api.groq.com/openai",
            "llama-3.3-70b-versatile",
            "groq_base_url",
            "groq_model",
            "groq_api",
            "groq_api_keys",
            "groq_api_"
    );
    private static final ProviderDefinition GOOGLE = new ProviderDefinition(
            PROVIDER_GOOGLE,
            "Google AI Studio",
            "https://generativelanguage.googleapis.com",
            "gemini-2.0-flash",
            "google_base_url",
            "google_model",
            "google_ai_studio_api",
            "google_ai_studio_api_keys",
            "google_ai_studio_api_"
    );
    private static final ProviderDefinition LEONARDO = new ProviderDefinition(
            PROVIDER_LEONARDO,
            "Leonardo",
            "https://cloud.leonardo.ai/api/rest/v1",
            "phoenix",
            "leonardo_base_url",
            "leonardo_model_id",
            "leonardo_api",
            "leonardo_api_keys",
            "leonardo_api_"
    );
    private static final ProviderDefinition FREEPIK = new ProviderDefinition(
            PROVIDER_FREEPIK,
            "Freepik",
            "https://api.freepik.com/v1/ai",
            "z-image",
            "freepik_base_url",
            "freepik_model",
            "freepik_api",
            "freepik_api_keys",
            "freepik_api_"
    );

    private static final List<ProviderDefinition> DEFINITIONS = List.of(GROQ, GOOGLE, LEONARDO, FREEPIK);

    private AiProviderSetupSupport() {
    }

    public static List<ProviderDefinition> definitions() {
        return DEFINITIONS;
    }

    public static ProviderDefinition definitionForId(String providerId) {
        String normalized = normalizeProviderId(providerId);
        for (ProviderDefinition definition : DEFINITIONS) {
            if (definition.id().equals(normalized)) {
                return definition;
            }
        }
        return GROQ;
    }

    public static ProviderDefinition definitionForDisplayName(String displayName) {
        ProviderDefinition definition = tryDefinitionForInput(displayName);
        return definition == null ? GROQ : definition;
    }

    public static String displayName(String providerId) {
        return definitionForId(providerId).displayName();
    }

    public static ProviderDefinition tryDefinitionForInput(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (ProviderDefinition definition : DEFINITIONS) {
            if (definition.displayName().equalsIgnoreCase(trimmed)) {
                return definition;
            }
        }

        String normalized = normalizeProviderAlias(trimmed);
        return switch (normalized) {
            case "groq" -> GROQ;
            case "google", "googleai", "googleaistudio", "googlevision", "gemini" -> GOOGLE;
            case "leonardo" -> LEONARDO;
            case "freepik", "freepick" -> FREEPIK;
            default -> null;
        };
    }

    public static String providerNamePrompt() {
        return "Groq, Google AI Studio, Leonardo, or Freepik";
    }

    public static String normalizeProviderId(String value) {
        ProviderDefinition definition = tryDefinitionForInput(value);
        return definition == null ? PROVIDER_GROQ : definition.id();
    }

    public static ProviderSetup defaultSetup() {
        return createDefaultSetup(PROVIDER_GROQ);
    }

    public static ProviderSetup createDefaultSetup(String providerId) {
        ProviderDefinition definition = definitionForId(providerId);
        return new ProviderSetup(
                definition.id(),
                definition.defaultBaseUrl(),
                definition.defaultModelName(),
                List.of()
        );
    }

    public static List<ProviderSetup> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ProviderSetup> parsed = GSON.fromJson(json, SETUP_LIST_TYPE);
            return sanitizeSetups(parsed);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static String toJson(List<ProviderSetup> setups) {
        return GSON.toJson(sanitizeSetups(setups), SETUP_LIST_TYPE);
    }

    public static List<ProviderSetup> loadFromSettings(SettingsManager settings) {
        List<ProviderSetup> parsed = parse(settings.getString(SETTINGS_KEY, ""));
        if (!parsed.isEmpty()) {
            return parsed;
        }

        ProviderSetup legacy = legacySetupFromSettings(settings);
        if (legacy != null) {
            return List.of(legacy);
        }
        return List.of(defaultSetup());
    }

    public static ProviderSetup mergeForProvider(List<ProviderSetup> setups, String providerId) {
        ProviderDefinition definition = definitionForId(providerId);
        String baseUrl = null;
        String modelName = null;
        LinkedHashSet<String> apiKeys = new LinkedHashSet<>();

        if (setups != null) {
            for (ProviderSetup setup : setups) {
                if (setup == null || !definition.id().equals(setup.providerId())) {
                    continue;
                }
                if (baseUrl == null && setup.baseUrl() != null && !setup.baseUrl().isBlank()) {
                    baseUrl = setup.baseUrl();
                }
                if (modelName == null && setup.modelName() != null && !setup.modelName().isBlank()) {
                    modelName = setup.modelName();
                }
                apiKeys.addAll(setup.apiKeys());
            }
        }

        if (baseUrl == null && modelName == null && apiKeys.isEmpty()) {
            return null;
        }
        return new ProviderSetup(
                definition.id(),
                baseUrl == null ? definition.defaultBaseUrl() : baseUrl,
                modelName == null ? definition.defaultModelName() : modelName,
                List.copyOf(apiKeys)
        );
    }

    public static List<String> normalizeApiKeys(List<String> keys) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (keys != null) {
            for (String key : keys) {
                if (key == null) {
                    continue;
                }
                String trimmed = key.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        }
        return List.copyOf(values);
    }

    public static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        String[] parts = csv.split(",");
        List<String> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static List<ProviderSetup> sanitizeSetups(List<ProviderSetup> setups) {
        List<ProviderSetup> sanitized = new ArrayList<>();
        if (setups == null) {
            return sanitized;
        }
        for (ProviderSetup setup : setups) {
            if (setup == null) {
                continue;
            }
            ProviderSetup normalized = new ProviderSetup(
                    setup.providerId(),
                    setup.baseUrl(),
                    setup.modelName(),
                    setup.apiKeys()
            );
            if (normalized.isMeaningful()) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    private static ProviderSetup legacySetupFromSettings(SettingsManager settings) {
        List<String> apiKeys = parseCsv(settings.getString("ai.apiKeys", ""));
        String singleKey = settings.getString("ai.apiKey", "").trim();
        if (!singleKey.isEmpty()) {
            apiKeys.add(0, singleKey);
        }
        apiKeys = normalizeApiKeys(apiKeys);

        String baseUrl = safeTrim(settings.getString("ai.baseUrl", ""));
        String modelName = safeTrim(settings.getString("ai.modelName", ""));
        String providerId = inferProviderFromLegacySettings(
                settings.getString("chat.inputModeDefault", "Best"),
                baseUrl,
                modelName
        );

        boolean hasAnyValue = !apiKeys.isEmpty()
                || (baseUrl != null && !baseUrl.isBlank())
                || (modelName != null && !modelName.isBlank());
        if (!hasAnyValue) {
            return null;
        }

        ProviderDefinition definition = definitionForId(providerId);
        return new ProviderSetup(
                providerId,
                baseUrl == null || baseUrl.isBlank() ? definition.defaultBaseUrl() : baseUrl,
                modelName == null || modelName.isBlank() ? definition.defaultModelName() : modelName,
                apiKeys
        );
    }

    private static String inferProviderFromLegacySettings(String inputModeDefault, String baseUrl, String modelName) {
        String normalizedMode = inputModeDefault == null ? "" : inputModeDefault.trim().toLowerCase(Locale.ROOT);
        if (normalizedMode.contains("freepik")) {
            return PROVIDER_FREEPIK;
        }
        if (normalizedMode.contains("leonardo")) {
            return PROVIDER_LEONARDO;
        }
        if (normalizedMode.contains("google")) {
            return PROVIDER_GOOGLE;
        }
        if (normalizedMode.contains("groq")) {
            return PROVIDER_GROQ;
        }

        String signature = ((baseUrl == null ? "" : baseUrl) + " " + (modelName == null ? "" : modelName))
                .toLowerCase(Locale.ROOT);
        if (signature.contains("freepik") || signature.contains("z-image")) {
            return PROVIDER_FREEPIK;
        }
        if (signature.contains("leonardo") || signature.contains("phoenix")) {
            return PROVIDER_LEONARDO;
        }
        if (signature.contains("generativelanguage") || signature.contains("gemini")) {
            return PROVIDER_GOOGLE;
        }
        return PROVIDER_GROQ;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeProviderAlias(String value) {
        String normalized = safeTrim(value).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    public record ProviderDefinition(String id,
                                     String displayName,
                                     String defaultBaseUrl,
                                     String defaultModelName,
                                     String baseUrlProperty,
                                     String modelProperty,
                                     String singleApiProperty,
                                     String csvApiProperty,
                                     String indexedApiPrefix) {
    }

    public record ProviderSetup(String providerId, String baseUrl, String modelName, List<String> apiKeys) {
        public ProviderSetup {
            providerId = normalizeProviderId(providerId);
            baseUrl = safeTrim(baseUrl);
            modelName = safeTrim(modelName);
            apiKeys = normalizeApiKeys(apiKeys);
        }

        public boolean isMeaningful() {
            return !baseUrl.isBlank() || !modelName.isBlank() || !apiKeys.isEmpty();
        }
    }
}
