package com.example.chatbot.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.net.URLEncoder;
import java.util.Properties;

public final class AuthApiClient {
    private static final String APP_PROPERTIES_FILE = "app.properties";
    private static final String DEFAULT_API_BASE_URL = "https://altarix.vercel.app";
    private static final String DEFAULT_AUTHORIZE_PATH = "/login.html";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String authorizePath;

    public AuthApiClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.apiBaseUrl = resolveApiBaseUrl();
        this.authorizePath = resolveAuthorizePath();
    }

    private String resolveAuthorizePath() {
        Properties properties = new Properties();
        Path externalPath = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(APP_PROPERTIES_FILE);
        if (loadProperties(properties, externalPath)) {
            return normalizeAuthorizePath(properties.getProperty("auth_authorize_path", DEFAULT_AUTHORIZE_PATH));
        }

        try (InputStream input = AuthApiClient.class.getResourceAsStream("/" + APP_PROPERTIES_FILE)) {
            if (input != null) {
                properties.load(input);
                return normalizeAuthorizePath(properties.getProperty("auth_authorize_path", DEFAULT_AUTHORIZE_PATH));
            }
        } catch (IOException ignored) {
            // fall through
        }

        return DEFAULT_AUTHORIZE_PATH;
    }

    private String normalizeAuthorizePath(String path) {
        if (path == null) return DEFAULT_AUTHORIZE_PATH;
        String p = path.trim();
        if (p.isEmpty()) return DEFAULT_AUTHORIZE_PATH;
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }

    public AuthResponse signup(String name, String username, String email, String password) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("username", username);
        payload.addProperty("email", email);
        payload.addProperty("password", password);
        return sendJsonRequest("/api/auth/signup", "POST", payload, null);
    }

    public AuthResponse login(String identifier, String password) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("identifier", identifier);
        payload.addProperty("password", password);
        return sendJsonRequest("/api/auth/login", "POST", payload, null);
    }

    public AuthResponse logout(String token) throws IOException, InterruptedException {
        return sendJsonRequest("/api/auth/logout", "POST", null, token);
    }

    public AuthResponse me(String token) throws IOException, InterruptedException {
        return sendJsonRequest("/api/auth/me", "GET", null, token);
    }

    public AuthResponse exchangeCode(String code) throws IOException, InterruptedException {
        if (code == null) return new AuthResponse(400, "Invalid code", "", null);
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("code", code);
        return sendJsonRequest("/api/auth/exchange", "POST", payload, null);
    }

    public AuthResponse updateProfile(String token, String name, String username, String bio) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("username", username);
        payload.addProperty("bio", bio == null ? "" : bio);
        return sendJsonRequest("/api/profile", "PATCH", payload, token);
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getAuthorizeUrl(String redirect) {
        try {
            String path = authorizePath == null ? DEFAULT_AUTHORIZE_PATH : authorizePath;
            String sep = path.contains("?") ? "&" : "?";
            return apiBaseUrl + path + sep + "redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return apiBaseUrl + DEFAULT_AUTHORIZE_PATH + "?redirect=" + redirect;
        }
    }

    private AuthResponse sendJsonRequest(String path, String method, JsonObject payload, String bearerToken)
        throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + path))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json");

        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        if (payload != null) {
            builder.header("Content-Type", "application/json; charset=UTF-8");
            builder.method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject body = parseBody(response.body());
        String message = extractMessage(body, response.statusCode());
        UserProfile user = extractUser(body);
        String token = extractToken(body);
        return new AuthResponse(response.statusCode(), message, token, user);
    }

    private JsonObject parseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(rawBody).getAsJsonObject();
        } catch (Exception ignored) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("error", rawBody);
            return fallback;
        }
    }

    private String extractMessage(JsonObject body, int statusCode) {
        if (body.has("error") && !body.get("error").isJsonNull()) {
            return body.get("error").getAsString();
        }
        if (body.has("message") && !body.get("message").isJsonNull()) {
            return body.get("message").getAsString();
        }
        return statusCode >= 200 && statusCode < 300 ? "Request completed." : "Request failed.";
    }

    private String extractToken(JsonObject body) {
        if (body.has("token") && !body.get("token").isJsonNull()) {
            return body.get("token").getAsString();
        }
        return "";
    }

    private UserProfile extractUser(JsonObject body) {
        if (!body.has("user") || body.get("user").isJsonNull() || !body.get("user").isJsonObject()) {
            return null;
        }
        JsonObject user = body.getAsJsonObject("user");
        return new UserProfile(
            getString(user, "id"),
            getString(user, "name"),
            getString(user, "username"),
            getString(user, "email"),
            getString(user, "role"),
            getString(user, "bio")
        );
    }

    private String getString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private String resolveApiBaseUrl() {
        Properties properties = new Properties();
        Path externalPath = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(APP_PROPERTIES_FILE);
        if (loadProperties(properties, externalPath)) {
            return normalizeBaseUrl(properties.getProperty("auth_api_base_url", DEFAULT_API_BASE_URL));
        }

        try (InputStream input = AuthApiClient.class.getResourceAsStream("/" + APP_PROPERTIES_FILE)) {
            if (input != null) {
                properties.load(input);
                return normalizeBaseUrl(properties.getProperty("auth_api_base_url", DEFAULT_API_BASE_URL));
            }
        } catch (IOException ignored) {
            // Fall through to default URL.
        }

        return DEFAULT_API_BASE_URL;
    }

    private boolean loadProperties(Properties properties, Path path) {
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

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.isEmpty()) {
            value = DEFAULT_API_BASE_URL;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public record AuthResponse(int statusCode, String message, String token, UserProfile user) {
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    public record UserProfile(String id, String name, String username, String email, String role, String bio) {
    }
}
