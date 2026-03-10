package com.example.chatbot.service;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.model.Message;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-memory chat service with provider routing and API key failover.
 */
public class ChatService {
    // ================= IN-MEMORY STORE =================
    private final List<Conversation> conversations = new ArrayList<>();
    private static final int TITLE_MAX_LENGTH = 28;
    private static final String DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile";
    private static final String DEFAULT_GROQ_BASE_URL = "https://api.groq.com/openai";
    private static final String DEFAULT_GOOGLE_MODEL = "gemini-2.0-flash";
    private static final String DEFAULT_GOOGLE_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_LEONARDO_BASE_URL = "https://cloud.leonardo.ai/api/rest/v1";
    private static final String DEFAULT_LEONARDO_MODEL_ID = "phoenix";
    private static final String APP_PROPERTIES_FILE = "app.properties";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final int LEONARDO_POLL_ATTEMPTS = 8;
    private static final long LEONARDO_POLL_DELAY_MS = 1100;

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "but", "by",
            "for", "from", "how", "i", "in", "is", "it", "me", "my",
            "of", "on", "or", "please", "so", "that", "the", "this",
            "to", "we", "what", "when", "where", "which", "who", "why",
            "with", "you", "your", "about", "can", "could", "would", "should"
    ));
    private static final String SYSTEM_PROMPT = """
            You are a structured educational assistant.

            Respond ONLY in clean, valid Markdown optimized for HTML rendering inside a JavaFX WebView.

            Formatting rules:
            - Use ## for the main title.
            - Use ### for section headings.
            - Do NOT use horizontal rules like ---.
            - Do NOT manually insert separators.
            - Use numbered lists for methods or types.
            - Use bullet points for explanations.
            - Keep paragraphs short (2-3 lines max).
            - Do NOT generate code unless the user explicitly asks for code, implementation, snippet, or example.
            - When code is explicitly requested, use proper fenced code blocks with language tags.
            - Do not use emojis.
            - Do not add conversational text.
            - Do not repeat the question.
            - Maintain professional tone.
            - Add spacing between sections naturally through Markdown headings.

            The output must render cleanly when converted to HTML and styled with CSS.
            """;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final ExecutorService apiExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("openai-api-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final SettingsManager settingsManager = SettingsManager.getInstance();

    public ChatService() {
        // Configuration is resolved lazily per request so Settings updates take effect immediately.
    }

    // ================= CONVERSATION API =================
    public Conversation createConversation() {
        Conversation conv = new Conversation("New Chat");
        conversations.add(conv);
        return conv;
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    // ================= MESSAGE API =================
    public CompletableFuture<Message> sendMessageAsync(Conversation conv, String text) {
        if (shouldAutoRenameConversation(conv)) {
            conv.setTitle(buildTitleFromUserText(text));
            conv.setTitleFinalized(true);
        }

        Message userMsg = new Message(Message.Sender.USER, text);
        conv.addMessage(userMsg);
        List<Message> historySnapshot = new ArrayList<>(conv.getMessages());

        return CompletableFuture.supplyAsync(() -> requestAssistantReply(historySnapshot), apiExecutor);
    }

    public void appendAssistantMessage(Conversation conv, Message botMessage) {
        if (conv != null && botMessage != null) {
            conv.addMessage(botMessage);
        }
    }

    /**
     * Ask a contextual question about selected text. Returns a future with the AI response.
     */
    public CompletableFuture<String> askAboutSelection(String selectedText, String question) {
        return CompletableFuture.supplyAsync(() -> {
            String contextPrompt = "The user selected the following text:\n\n"
                    + selectedText + "\n\nUser question: " + question;
            List<Message> context = List.of(new Message(Message.Sender.USER, contextPrompt));
            Message reply = requestAssistantReply(context);
            return reply.getContent();
        }, apiExecutor);
    }

    private Message requestAssistantReply(List<Message> historySnapshot) {
        LoadedProperties loaded = loadAppProperties();
        String latestUserText = extractLatestUserMessage(historySnapshot);
        ProviderType requestedProvider = resolveRequestedProvider(latestUserText);
        List<ProviderType> attemptOrder = buildProviderAttemptOrder(requestedProvider, latestUserText);

        List<String> missingProviders = new ArrayList<>();
        String lastError = null;

        for (ProviderType providerType : attemptOrder) {
            ProviderConfig config = resolveProviderConfig(providerType, loaded);
            if (config.apiKeys().isEmpty()) {
                missingProviders.add(providerDisplayName(providerType));
                continue;
            }

            ProviderAttemptResult result = requestWithProviderFailover(config, historySnapshot, latestUserText);
            if (result.success()) {
                return new Message(Message.Sender.BOT, result.content());
            }
            lastError = result.error();
        }

        if (!missingProviders.isEmpty() && lastError == null) {
            return new Message(Message.Sender.BOT, buildMissingApiKeyMessage(loaded.source(), missingProviders));
        }

        String errorDetail = (lastError == null || lastError.isBlank()) ? "Unknown API error." : lastError;
        return new Message(Message.Sender.BOT, "I could not call the AI API.\n\n- " + errorDetail);
    }

    private ProviderAttemptResult requestWithProviderFailover(ProviderConfig config,
                                                              List<Message> historySnapshot,
                                                              String latestUserText) {
        String lastError = null;
        for (String apiKey : config.apiKeys()) {
            ProviderCallResult callResult = switch (config.providerType()) {
                case GROQ -> callGroqChat(config, apiKey, historySnapshot);
                case GOOGLE_AI_STUDIO -> callGoogleChat(config, apiKey, historySnapshot);
                case LEONARDO -> callLeonardoImage(config, apiKey, latestUserText);
            };

            if (callResult.success()) {
                return new ProviderAttemptResult(true, callResult.content(), null);
            }

            lastError = callResult.error();
            if (!callResult.retryWithNextKey()) {
                break;
            }
        }

        String fallbackError = (lastError == null || lastError.isBlank())
                ? providerDisplayName(config.providerType()) + " request failed."
                : lastError;
        return new ProviderAttemptResult(false, null, fallbackError);
    }

    private ProviderCallResult callGroqChat(ProviderConfig config, String apiKey, List<Message> historySnapshot) {
        try {
            String body = buildOpenAiChatRequestJson(historySnapshot, config.modelName());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(config.baseUrl()) + "/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String content = extractAssistantContent(response.body());
                return ProviderCallResult.success(content);
            }

            String error = extractErrorMessage(response.body());
            String details = providerDisplayName(config.providerType()) + " API HTTP "
                    + response.statusCode() + " - " + error;
            return ProviderCallResult.failure(details, shouldRetryWithNextKey(response.statusCode()));
        } catch (Throwable ex) {
            String message = providerDisplayName(config.providerType()) + " request failed: " + ex.getMessage();
            return ProviderCallResult.failure(message, true);
        }
    }

    private ProviderCallResult callGoogleChat(ProviderConfig config, String apiKey, List<Message> historySnapshot) {
        try {
            String endpoint = trimTrailingSlash(config.baseUrl())
                    + "/v1beta/models/"
                    + config.modelName()
                    + ":generateContent?key="
                    + urlEncode(apiKey);
            String body = buildGoogleChatRequestJson(historySnapshot);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String content = extractGoogleAssistantContent(response.body());
                if (content == null || content.isBlank()) {
                    return ProviderCallResult.failure("Google AI Studio returned an empty response.", false);
                }
                return ProviderCallResult.success(content);
            }

            String error = extractErrorMessage(response.body());
            String details = providerDisplayName(config.providerType()) + " API HTTP "
                    + response.statusCode() + " - " + error;
            return ProviderCallResult.failure(details, shouldRetryWithNextKey(response.statusCode()));
        } catch (Throwable ex) {
            String message = providerDisplayName(config.providerType()) + " request failed: " + ex.getMessage();
            return ProviderCallResult.failure(message, true);
        }
    }

    private ProviderCallResult callLeonardoImage(ProviderConfig config, String apiKey, String latestUserText) {
        try {
            if (!isImageGenerationPrompt(latestUserText)) {
                return ProviderCallResult.failure(
                        "Leonardo is dedicated to image generation. Ask to generate an image, logo, art, or illustration.",
                        false
                );
            }

            String endpoint = trimTrailingSlash(config.baseUrl()) + "/generations";
            String body = buildLeonardoGenerationRequestJson(latestUserText, config.modelName());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String error = extractErrorMessage(response.body());
                String details = providerDisplayName(config.providerType()) + " API HTTP "
                        + response.statusCode() + " - " + error;
                return ProviderCallResult.failure(details, shouldRetryWithNextKey(response.statusCode()));
            }

            String generationId = extractLeonardoGenerationId(response.body());
            String imageUrl = extractFirstUrl(response.body());

            if ((imageUrl == null || imageUrl.isBlank()) && generationId != null && !generationId.isBlank()) {
                imageUrl = pollLeonardoImageUrl(config.baseUrl(), apiKey, generationId);
            }

            String content = buildLeonardoResponseContent(imageUrl, generationId);
            return ProviderCallResult.success(content);
        } catch (Throwable ex) {
            String message = providerDisplayName(config.providerType()) + " request failed: " + ex.getMessage();
            return ProviderCallResult.failure(message, true);
        }
    }

    private String pollLeonardoImageUrl(String baseUrl, String apiKey, String generationId) {
        String endpoint = trimTrailingSlash(baseUrl) + "/generations/" + generationId;
        for (int i = 0; i < LEONARDO_POLL_ATTEMPTS; i++) {
            try {
                Thread.sleep(LEONARDO_POLL_DELAY_MS);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String imageUrl = extractFirstUrl(response.body());
                    if (imageUrl != null && !imageUrl.isBlank()) {
                        return imageUrl;
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Throwable ignored) {
                // Keep polling until attempts are exhausted.
            }
        }
        return null;
    }

    private String buildLeonardoResponseContent(String imageUrl, String generationId) {
        StringBuilder out = new StringBuilder("Leonardo image generation completed.");
        if (imageUrl != null && !imageUrl.isBlank()) {
            out.append("\n\n![Generated image](").append(imageUrl).append(")");
        } else {
            out.append("\n\nThe image request was accepted, but no direct URL was returned yet.");
        }
        if (generationId != null && !generationId.isBlank()) {
            out.append("\n\nGeneration ID: `").append(generationId).append("`");
        }
        return out.toString();
    }

    private String buildOpenAiChatRequestJson(List<Message> historySnapshot, String modelName) {
        List<Message> sorted = historySnapshot.stream()
                .sorted(Comparator.comparing(Message::getTimestamp))
                .toList();

        double temperature = settingsManager.getDouble("ai.temperature", 0.4);
        int maxTokens = settingsManager.getInt("ai.maxTokens", 4096);
        String customPrompt = settingsManager.getString("ai.systemPrompt", "");
        String effectivePrompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : SYSTEM_PROMPT;

        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"model\":\"").append(jsonEscape(modelName)).append("\",");
        builder.append("\"temperature\":").append(temperature).append(",");
        builder.append("\"max_tokens\":").append(maxTokens).append(",");
        builder.append("\"messages\":[");
        builder.append("{\"role\":\"system\",\"content\":\"").append(jsonEscape(effectivePrompt)).append("\"}");

        for (Message msg : sorted) {
            String role = msg.getSender() == Message.Sender.USER ? "user" : "assistant";
            builder.append(",{\"role\":\"")
                    .append(role)
                    .append("\",\"content\":\"")
                    .append(jsonEscape(msg.getContent()))
                    .append("\"}");
        }

        builder.append("]}");
        return builder.toString();
    }

    private String buildGoogleChatRequestJson(List<Message> historySnapshot) {
        List<Message> sorted = historySnapshot.stream()
                .sorted(Comparator.comparing(Message::getTimestamp))
                .toList();

        double temperature = settingsManager.getDouble("ai.temperature", 0.4);
        int maxTokens = settingsManager.getInt("ai.maxTokens", 4096);
        String customPrompt = settingsManager.getString("ai.systemPrompt", "");
        String effectivePrompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : SYSTEM_PROMPT;

        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"systemInstruction\":{\"parts\":[{\"text\":\"")
                .append(jsonEscape(effectivePrompt))
                .append("\"}]},");
        builder.append("\"generationConfig\":{")
                .append("\"temperature\":").append(temperature).append(",")
                .append("\"maxOutputTokens\":").append(maxTokens)
                .append("},");
        builder.append("\"contents\":[");

        boolean first = true;
        for (Message msg : sorted) {
            if (!first) {
                builder.append(",");
            }
            String role = msg.getSender() == Message.Sender.USER ? "user" : "model";
            builder.append("{\"role\":\"").append(role).append("\",\"parts\":[{\"text\":\"")
                    .append(jsonEscape(msg.getContent()))
                    .append("\"}]}");
            first = false;
        }

        if (first) {
            builder.append("{\"role\":\"user\",\"parts\":[{\"text\":\"Hello\"}]}");
        }

        builder.append("]}");
        return builder.toString();
    }

    private String buildLeonardoGenerationRequestJson(String prompt, String modelId) {
        String effectivePrompt = (prompt == null || prompt.isBlank())
                ? "Create a high quality image"
                : prompt.trim();
        return "{" +
                "\"modelId\":\"" + jsonEscape(modelId) + "\"," +
                "\"prompt\":\"" + jsonEscape(effectivePrompt) + "\"," +
                "\"num_images\":1," +
                "\"width\":1024," +
                "\"height\":1024" +
                "}";
    }

    private boolean shouldAutoRenameConversation(Conversation conv) {
        if (conv.isTitleFinalized()) {
            return false;
        }
        String currentTitle = conv.getTitle();
        return currentTitle == null || currentTitle.isBlank() || "New Chat".equalsIgnoreCase(currentTitle.trim());
    }

    private String buildTitleFromUserText(String text) {
        if (text == null) {
            return "New Chat";
        }
        String normalized = text.replaceAll("https?://\\S+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "New Chat";
        }

        String inferred = inferTopicTitle(normalized);
        if (inferred.length() <= TITLE_MAX_LENGTH) {
            return inferred;
        }
        return inferred.substring(0, TITLE_MAX_LENGTH - 3).trim() + "...";
    }

    private String inferTopicTitle(String normalized) {
        String[] words = normalized.replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .split(" ");

        List<String> keywords = new ArrayList<>();
        for (String rawWord : words) {
            if (rawWord.isBlank()) {
                continue;
            }
            String lower = rawWord.toLowerCase(Locale.ROOT);
            if (lower.length() < 3 || STOP_WORDS.contains(lower)) {
                continue;
            }
            keywords.add(toTitleCase(lower));
            if (keywords.size() == 4) {
                break;
            }
        }

        if (!keywords.isEmpty()) {
            return String.join(" ", keywords);
        }

        String fallback = normalized;
        int questionMark = fallback.indexOf('?');
        if (questionMark > 0) {
            fallback = fallback.substring(0, questionMark);
        }
        fallback = fallback.trim();
        if (fallback.isEmpty()) {
            return "New Chat";
        }
        String[] fallbackWords = fallback.split(" ");
        int take = Math.min(4, fallbackWords.length);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < take; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(toTitleCase(fallbackWords[i].toLowerCase(Locale.ROOT)));
        }
        return builder.toString();
    }

    private String toTitleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private LoadedProperties loadAppProperties() {
        Properties properties = new Properties();
        // Preferred order:
        // 1) Cortex folder (outside ai-project),
        // 2) ai-project/src/main/resources/app.properties,
        // 3) classpath fallback (/app.properties).

        Path projectDir = resolveProjectDirectory();
        Path externalPath = projectDir != null && projectDir.getParent() != null
                ? projectDir.getParent().resolve(APP_PROPERTIES_FILE).toAbsolutePath().normalize()
                : null;
        if (loadPropertiesFromFile(properties, externalPath)) {
            return new LoadedProperties(properties, AppPropertiesSource.CORTEX_ROOT);
        }

        Path resourcePath = resolveResourcePropertiesPath(projectDir);
        if (loadPropertiesFromFile(properties, resourcePath)) {
            return new LoadedProperties(properties, AppPropertiesSource.RESOURCE_FALLBACK);
        }

        try (InputStream input = getClass().getResourceAsStream("/" + APP_PROPERTIES_FILE)) {
            if (input != null) {
                properties.load(input);
                return new LoadedProperties(properties, AppPropertiesSource.RESOURCE_FALLBACK);
            }
        } catch (IOException ignored) {
            // Keep defaults if config file cannot be read.
        }
        return new LoadedProperties(properties, AppPropertiesSource.NOT_FOUND);
    }

    private Path resolveProjectDirectory() {
        try {
            Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            if ("ai-project".equalsIgnoreCase(userDir.getFileName() != null ? userDir.getFileName().toString() : "")) {
                return userDir;
            }

            Path nestedAiProject = userDir.resolve("ai-project");
            if (Files.isDirectory(nestedAiProject)) {
                return nestedAiProject.toAbsolutePath().normalize();
            }

            return userDir;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ProviderConfig resolveProviderConfig(ProviderType providerType, LoadedProperties loaded) {
        Properties props = loaded.properties();
        AppPropertiesSource source = loaded.source();

        return switch (providerType) {
            case GROQ -> resolveGroqConfig(props, source);
            case GOOGLE_AI_STUDIO -> resolveGoogleConfig(props, source);
            case LEONARDO -> resolveLeonardoConfig(props, source);
        };
    }

    private ProviderConfig resolveGroqConfig(Properties props, AppPropertiesSource source) {
        String configuredBaseUrl = settingIfCustomized("ai.baseUrl", "https://api.openai.com");
        String configuredModelName = settingIfCustomized("ai.modelName", "gpt-4.1-mini");

        String baseUrl = firstNonBlank(
                System.getenv("GROQ_BASE_URL"),
                props.getProperty("groq_base_url"),
                configuredBaseUrl,
                System.getenv("OPENAI_BASE_URL"),
                props.getProperty("openai_base_url"),
                DEFAULT_GROQ_BASE_URL
        );

        String modelName = firstNonBlank(
                System.getenv("GROQ_MODEL"),
                props.getProperty("groq_model"),
                configuredModelName,
                System.getenv("OPENAI_MODEL"),
                props.getProperty("openai_model"),
                DEFAULT_GROQ_MODEL
        );

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addCsvValues(keys, System.getenv("GROQ_API_KEYS"));
        addIfPresent(keys, System.getenv("GROQ_API_KEY"));
        addIfPresent(keys, System.getenv("OPENAI_API_KEY"));
        addIfPresent(keys, System.getenv("PAST_API"));
        addCsvValues(keys, props.getProperty("groq_api_keys"));
        addCsvValues(keys, props.getProperty("past_api_keys"));
        readIndexedPropertyValues(props, "past_api_").forEach(value -> addIfPresent(keys, value));
        readIndexedPropertyValues(props, "groq_api_").forEach(value -> addIfPresent(keys, value));
        addIfPresent(keys, props.getProperty("past_api"));
        addIfPresent(keys, settingsManager.getString("ai.apiKey", ""));

        return new ProviderConfig(ProviderType.GROQ, baseUrl, modelName, List.copyOf(keys), source);
    }

    private ProviderConfig resolveGoogleConfig(Properties props, AppPropertiesSource source) {
        String baseUrl = firstNonBlank(
                System.getenv("GOOGLE_BASE_URL"),
                props.getProperty("google_base_url"),
                DEFAULT_GOOGLE_BASE_URL
        );

        String modelName = firstNonBlank(
                System.getenv("GOOGLE_MODEL"),
                props.getProperty("google_model"),
                DEFAULT_GOOGLE_MODEL
        );

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addCsvValues(keys, System.getenv("GOOGLE_API_KEYS"));
        addIfPresent(keys, System.getenv("GOOGLE_API_KEY"));
        addCsvValues(keys, props.getProperty("google_ai_studio_api_keys"));
        readIndexedPropertyValues(props, "google_ai_studio_api_").forEach(value -> addIfPresent(keys, value));
        addIfPresent(keys, props.getProperty("google_ai_studio_api"));

        return new ProviderConfig(ProviderType.GOOGLE_AI_STUDIO, baseUrl, modelName, List.copyOf(keys), source);
    }

    private ProviderConfig resolveLeonardoConfig(Properties props, AppPropertiesSource source) {
        String baseUrl = firstNonBlank(
                System.getenv("LEONARDO_BASE_URL"),
                props.getProperty("leonardo_base_url"),
                DEFAULT_LEONARDO_BASE_URL
        );

        String modelName = firstNonBlank(
                System.getenv("LEONARDO_MODEL_ID"),
                props.getProperty("leonardo_model_id"),
                DEFAULT_LEONARDO_MODEL_ID
        );

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addCsvValues(keys, System.getenv("LEONARDO_API_KEYS"));
        addIfPresent(keys, System.getenv("LEONARDO_API_KEY"));
        addCsvValues(keys, props.getProperty("leonardo_api_keys"));
        readIndexedPropertyValues(props, "leonardo_api_").forEach(value -> addIfPresent(keys, value));
        addIfPresent(keys, props.getProperty("leonardo_api"));

        return new ProviderConfig(ProviderType.LEONARDO, baseUrl, modelName, List.copyOf(keys), source);
    }

    private ProviderType resolveRequestedProvider(String latestUserText) {
        String normalized = latestUserText == null ? "" : latestUserText.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "use leonardo", "with leonardo", "leonardo api", "leonardo")) {
            return ProviderType.LEONARDO;
        }
        if (containsAny(normalized, "use google", "google ai studio", "gemini", "use gemini")) {
            return ProviderType.GOOGLE_AI_STUDIO;
        }
        if (containsAny(normalized, "use groq", "groq api", "use llama", "llama 3", "llama-3")) {
            return ProviderType.GROQ;
        }
        if (isImageGenerationPrompt(normalized)) {
            return ProviderType.LEONARDO;
        }
        if (isImageUnderstandingPrompt(normalized)) {
            return ProviderType.GOOGLE_AI_STUDIO;
        }
        return ProviderType.GROQ;
    }

    private List<ProviderType> buildProviderAttemptOrder(ProviderType requestedProvider, String latestUserText) {
        List<ProviderType> order = new ArrayList<>();
        if (isImageGenerationPrompt(latestUserText)) {
            addProvider(order, ProviderType.LEONARDO);
            addProvider(order, ProviderType.GROQ);
            addProvider(order, ProviderType.GOOGLE_AI_STUDIO);
            return order;
        }

        addProvider(order, requestedProvider);
        switch (requestedProvider) {
            case GROQ -> {
                addProvider(order, ProviderType.GOOGLE_AI_STUDIO);
            }
            case GOOGLE_AI_STUDIO -> {
                addProvider(order, ProviderType.GROQ);
            }
            case LEONARDO -> {
                addProvider(order, ProviderType.GROQ);
                addProvider(order, ProviderType.GOOGLE_AI_STUDIO);
            }
        }
        return order;
    }

    private void addProvider(List<ProviderType> order, ProviderType providerType) {
        if (!order.contains(providerType)) {
            order.add(providerType);
        }
    }

    private boolean isImageGenerationPrompt(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        boolean mentionsVisual = containsAny(normalized,
                "image", "photo", "picture", "art", "illustration", "logo", "icon", "poster", "wallpaper");
        boolean mentionsCreate = containsAny(normalized,
                "generate", "create", "make", "design", "draw", "render");
        return containsAny(normalized,
                "text to image", "image generation", "generate an image", "create an image", "make an image")
                || (mentionsVisual && mentionsCreate);
    }

    private boolean isImageUnderstandingPrompt(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return containsAny(normalized,
                "analyze image",
                "describe image",
                "what is in this image",
                "read text from image",
                "image understanding",
                "image analysis");
    }

    private boolean containsAny(String text, String... terms) {
        if (text == null || terms == null) {
            return false;
        }
        for (String term : terms) {
            if (term != null && !term.isBlank() && text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String extractLatestUserMessage(List<Message> historySnapshot) {
        if (historySnapshot == null || historySnapshot.isEmpty()) {
            return "";
        }
        for (int i = historySnapshot.size() - 1; i >= 0; i--) {
            Message msg = historySnapshot.get(i);
            if (msg != null && msg.getSender() == Message.Sender.USER) {
                return msg.getContent() == null ? "" : msg.getContent();
            }
        }
        return "";
    }

    private String settingIfCustomized(String key, String defaultValue) {
        String value = settingsManager.getString(key, "");
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (defaultValue != null && trimmed.equals(defaultValue)) {
            return null;
        }
        return trimmed;
    }

    private void addIfPresent(Set<String> target, String value) {
        if (target == null || value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || "PASTE_YOUR_API_KEY_HERE".equalsIgnoreCase(trimmed)) {
            return;
        }
        target.add(trimmed);
    }

    private void addCsvValues(Set<String> target, String csv) {
        if (target == null || csv == null || csv.isBlank()) {
            return;
        }
        String[] parts = csv.split(",");
        for (String part : parts) {
            addIfPresent(target, part);
        }
    }

    private List<String> readIndexedPropertyValues(Properties props, String prefix) {
        List<String> names = props.stringPropertyNames().stream()
                .filter(name -> name != null && name.startsWith(prefix))
                .sorted((a, b) -> {
                    int aNum = extractTrailingNumber(a, prefix);
                    int bNum = extractTrailingNumber(b, prefix);
                    if (aNum != bNum) {
                        return Integer.compare(aNum, bNum);
                    }
                    return a.compareToIgnoreCase(b);
                })
                .toList();

        List<String> values = new ArrayList<>();
        for (String name : names) {
            values.add(props.getProperty(name));
        }
        return values;
    }

    private int extractTrailingNumber(String value, String prefix) {
        if (value == null || prefix == null || !value.startsWith(prefix)) {
            return Integer.MAX_VALUE;
        }
        String suffix = value.substring(prefix.length()).trim();
        if (suffix.isEmpty()) {
            return Integer.MAX_VALUE - 1;
        }
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private String buildMissingApiKeyMessage(AppPropertiesSource source, List<String> missingProviders) {
        String providers = (missingProviders == null || missingProviders.isEmpty())
                ? "Groq / Google AI Studio / Leonardo"
                : String.join(", ", missingProviders);

        if (source == AppPropertiesSource.RESOURCE_FALLBACK) {
            return ("""
                    API keys are missing for: %s.

                    `Cortex/app.properties` was not found, so the app used bundled fallback properties.

                    Add provider keys in `Cortex/app.properties`, for example:

                    ```properties
                    past_api_1=YOUR_GROQ_KEY_1
                    past_api_2=YOUR_GROQ_KEY_2
                    google_ai_studio_api_1=YOUR_GOOGLE_KEY_1
                    leonardo_api_1=YOUR_LEONARDO_KEY_1
                    ```
                    """).formatted(providers);
        }

        return ("""
                API keys are missing for: %s.

                Configure your provider keys in `Cortex/app.properties`:

                ```properties
                past_api_1=YOUR_GROQ_KEY_1
                past_api_2=YOUR_GROQ_KEY_2
                google_ai_studio_api_1=YOUR_GOOGLE_KEY_1
                leonardo_api_1=YOUR_LEONARDO_KEY_1
                ```
                """).formatted(providers);
    }

    private record LoadedProperties(Properties properties, AppPropertiesSource source) {
    }

    private enum AppPropertiesSource {
        CORTEX_ROOT,
        RESOURCE_FALLBACK,
        NOT_FOUND
    }

    private Path resolveResourcePropertiesPath(Path projectDir) {
        if (projectDir == null) {
            return null;
        }
        return projectDir.resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve(APP_PROPERTIES_FILE)
                .toAbsolutePath()
                .normalize();
    }

    private boolean loadPropertiesFromFile(Properties properties, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private String extractAssistantContent(String json) {
        int messageIndex = json.indexOf("\"message\"");
        if (messageIndex < 0) {
            return "I received a response, but could not parse assistant content.";
        }
        int contentKeyIndex = json.indexOf("\"content\"", messageIndex);
        String content = extractJsonStringValueAtKey(json, contentKeyIndex);
        return content == null ? "I received a response, but could not parse assistant content." : content;
    }

    private String extractGoogleAssistantContent(String json) {
        int candidatesIndex = json.indexOf("\"candidates\"");
        if (candidatesIndex < 0) {
            return null;
        }
        int contentIndex = json.indexOf("\"content\"", candidatesIndex);
        if (contentIndex < 0) {
            return null;
        }
        int partsIndex = json.indexOf("\"parts\"", contentIndex);
        if (partsIndex < 0) {
            return null;
        }
        int textKeyIndex = json.indexOf("\"text\"", partsIndex);
        return extractJsonStringValueAtKey(json, textKeyIndex);
    }

    private String extractLeonardoGenerationId(String json) {
        int idIndex = json.indexOf("\"generationId\"");
        return extractJsonStringValueAtKey(json, idIndex);
    }

    private String extractFirstUrl(String json) {
        int urlIndex = json.indexOf("\"url\"");
        return extractJsonStringValueAtKey(json, urlIndex);
    }

    private String extractErrorMessage(String json) {
        int errorIndex = json.indexOf("\"error\"");
        int messageKeyIndex = errorIndex >= 0
                ? json.indexOf("\"message\"", errorIndex)
                : json.indexOf("\"message\"");
        String message = extractJsonStringValueAtKey(json, messageKeyIndex);
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "Unknown API error.";
    }

    private String extractJsonStringValueAtKey(String json, int keyIndex) {
        if (json == null || keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            return null;
        }

        int startQuote = -1;
        for (int i = colonIndex + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == '"') {
                startQuote = i;
            }
            break;
        }
        if (startQuote < 0) {
            return null;
        }

        StringBuilder raw = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                raw.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                raw.append(ch);
                escaped = true;
                continue;
            }
            if (ch == '"') {
                return jsonUnescape(raw.toString());
            }
            raw.append(ch);
        }
        return null;
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    private String jsonUnescape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 < value.length()) {
                            String hex = value.substring(i + 1, i + 5);
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> out.append(next);
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private boolean shouldRetryWithNextKey(int statusCode) {
        return statusCode == 401 || statusCode == 403 || statusCode == 429 || statusCode >= 500;
    }

    private String providerDisplayName(ProviderType providerType) {
        return switch (providerType) {
            case GROQ -> "Groq";
            case GOOGLE_AI_STUDIO -> "Google AI Studio";
            case LEONARDO -> "Leonardo";
        };
    }

    private enum ProviderType {
        GROQ,
        GOOGLE_AI_STUDIO,
        LEONARDO
    }

    private record ProviderConfig(ProviderType providerType,
                                  String baseUrl,
                                  String modelName,
                                  List<String> apiKeys,
                                  AppPropertiesSource appPropertiesSource) {
    }

    private record ProviderAttemptResult(boolean success, String content, String error) {
    }

    private record ProviderCallResult(boolean success, String content, String error, boolean retryWithNextKey) {
        private static ProviderCallResult success(String content) {
            return new ProviderCallResult(true, content, null, false);
        }

        private static ProviderCallResult failure(String error, boolean retryWithNextKey) {
            return new ProviderCallResult(false, null, error, retryWithNextKey);
        }
    }
}
