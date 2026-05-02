/**
 * EXAMPLE CODE CORRECTIONS FOR RESOURCE LOADING
 * 
 * This file shows corrected patterns for your existing code.
 * Your code is ALREADY MOSTLY CORRECT, but these patterns are recommended.
 */

// ============================================================================
// 1. CSS LOADING - Use in MainApp.java, MainController.java, etc.
// ============================================================================

// ✅ CORRECT - Current approach (keep as-is):
public void initializeScene(Stage primaryStage) {
    Scene scene = new Scene(root, 1200, 760);
    
    // Method 1: Direct classpath loading (CURRENT - already correct)
    scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
    
    // Method 2: Using ResourceLoader utility (RECOMMENDED for new code)
    // ResourceLoader.loadStylesheet(scene, "/css/styles.css");
}

// ✅ CORRECT - Multiple stylesheets:
public void applyTheme(Scene scene, String themeKey) {
    Map<String, String> themes = Map.ofEntries(
        Map.entry("theme-dark-purple", "/styles/themes/dark/dark-purple.css"),
        Map.entry("theme-light", "/styles/themes/light/light.css")
    );
    
    String themePath = themes.get(themeKey);
    if (themePath != null) {
        scene.getStylesheets().add(getClass().getResource(themePath).toExternalForm());
        // OR: ResourceLoader.loadStylesheet(scene, themePath);
    }
}

// ============================================================================
// 2. FXML LOADING - Use in MainApp.java, MainController.java, UpdateService.java
// ============================================================================

// ✅ CORRECT - Current approach (keep as-is):
public Parent loadMainFXML() throws IOException {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
    return loader.load();
}

// ✅ CORRECT - With controller retrieval:
public void openSettingsDialog() throws IOException {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
    Parent root = loader.load();
    SettingsDialogController controller = loader.getController();
    
    // Configure controller with data
    controller.setData(someData);
}

// ✅ RECOMMENDED - Using ResourceLoader (for new code):
public void openSettingsDialog() throws IOException {
    // Simplified version
    Parent root = ResourceLoader.loadFXML("/fxml/settings.fxml");
    // Scene scene = new Scene(root);
}

// ============================================================================
// 3. IMAGE/ICON LOADING - Use in IconResources.java, MainController.java
// ============================================================================

// ✅ CORRECT - Current approach in IconResources.java (keep as-is):
public static void addStageIcons(Stage stage, Class<?> resourceAnchor) {
    List<String> iconPaths = List.of(
        "/icon/Altarix-16.png",
        "/icon/Altarix-32.png",
        "/icon/Altarix-64.png",
        "/icon/Altarix-128.png",
        "/icon/Altarix-256.png",
        "/icon/Altarix-512.png"
    );
    
    for (String path : iconPaths) {
        URL resource = resourceAnchor.getResource(path);
        if (resource != null) {
            stage.getIcons().add(new Image(resource.toExternalForm()));
        }
    }
}

// ✅ RECOMMENDED - Using ResourceLoader (for new code):
public static void addStageIcons(Stage stage) {
    ResourceLoader.loadStageIcons(stage,
        "/icon/Altarix-16.png",
        "/icon/Altarix-32.png",
        "/icon/Altarix-64.png",
        "/icon/Altarix-128.png",
        "/icon/Altarix-256.png",
        "/icon/Altarix-512.png"
    );
}

// ✅ CORRECT - Load specific image with size:
public Image loadIconWithSize(String iconPath, int size) {
    URL iconUrl = getClass().getResource(iconPath);
    if (iconUrl == null) {
        System.err.println("Icon not found: " + iconPath);
        return null;
    }
    return new Image(iconUrl.toExternalForm(), size, size, true, true);
}

// ✅ RECOMMENDED - Using ResourceLoader:
public Image loadIconWithSize(String iconPath, int size) {
    try {
        return ResourceLoader.loadImage(iconPath, size, size, true, true);
    } catch (IllegalArgumentException ex) {
        System.err.println("Icon not found: " + iconPath);
        return null;
    }
}

// ============================================================================
// 4. JSON CONFIG LOADING - Use in LanguageConfigService.java
// ============================================================================

// ✅ CORRECT - Current approach (keep as-is):
private void loadConfig() {
    try (InputStream is = getClass().getResourceAsStream("/config/languages.json")) {
        if (is == null) {
            System.err.println("Config not found: /config/languages.json");
            return;
        }
        Gson gson = new Gson();
        Map<String, LanguageEntry> raw = gson.fromJson(
            new InputStreamReader(is, StandardCharsets.UTF_8),
            new TypeToken<Map<String, LanguageEntry>>() {}.getType()
        );
        // Process config
    } catch (Exception ex) {
        System.err.println("Failed to load config: " + ex.getMessage());
    }
}

// ✅ RECOMMENDED - Using ResourceLoader:
private void loadConfig() {
    try {
        String jsonContent = ResourceLoader.getResourceAsString("/config/languages.json");
        Gson gson = new Gson();
        Map<String, LanguageEntry> raw = gson.fromJson(
            jsonContent,
            new TypeToken<Map<String, LanguageEntry>>() {}.getType()
        );
        // Process config
    } catch (Exception ex) {
        System.err.println("Failed to load config: " + ex.getMessage());
    }
}

// ============================================================================
// 5. RESOURCE VERIFICATION - For debugging
// ============================================================================

// ✅ CHECK: Verify all resources exist on startup
public static void verifyResources() {
    String[] requiredResources = {
        "/css/styles.css",
        "/css/code-visualizer.css",
        "/fxml/main.fxml",
        "/fxml/chat.fxml",
        "/fxml/settings.fxml",
        "/icon/Altarix-16.png",
        "/config/languages.json",
        "/version.properties"
    };
    
    for (String resource : requiredResources) {
        if (ResourceLoader.resourceExists(resource)) {
            System.out.println("✓ Resource found: " + resource);
        } else {
            System.err.println("✗ MISSING RESOURCE: " + resource);
        }
    }
}

// ============================================================================
// 6. WEBVIEW SPECIAL HANDLING - For dynamic HTML/CSS content
// ============================================================================

// ✅ CORRECT - Current approach in ChatController.java (keep as-is):
private void previewWebSnippet(String language, String code, String messageContent) {
    String mergedHtml = buildMergedWebPreviewHtml(language, code, messageContent);
    webPreviewView.getEngine().loadContent(mergedHtml, "text/html");
}

// ✅ WHY THIS WORKS:
// The HTML is built as a STRING with embedded CSS and JavaScript.
// No external resources are referenced, so it works in both IDE and packaged app.

// ✅ IF YOU NEED EXTERNAL CSS IN WEBVIEW:
private String injectWebViewCSS(String html) throws IOException {
    // Load CSS from classpath resource as string
    String cssContent = ResourceLoader.getResourceAsString("/css/webview-markdown.css");
    
    // Inject as inline style tag
    String styleTag = "<style>\n" + cssContent + "\n</style>\n";
    
    if (html.contains("</head>")) {
        return html.replace("</head>", styleTag + "</head>");
    } else {
        return styleTag + html;
    }
}

// ============================================================================
// 7. VERSION PROPERTIES - Handle both packaged and development
// ============================================================================

// ✅ CURRENT APPROACH - Already handles both cases correctly:
public static String getCurrentVersion() {
    String version = "0.0.0";
    Properties props = new Properties();
    
    try (InputStream input = openVersionStream()) {
        if (input == null) {
            return version;
        }
        props.load(input);
        version = props.getProperty("version", version).trim();
    } catch (Exception ignored) {
        // Fall back to default
    }
    
    return version;
}

private static InputStream openVersionStream() throws IOException {
    Path localVersionPath = Paths.get("version.properties");
    
    // Try local file first (for packaged app in development mode)
    if (Files.exists(localVersionPath)) {
        return Files.newInputStream(localVersionPath);
    }
    
    // Fall back to classpath (both packaged and IDE)
    return VersionProperties.class.getResourceAsStream("/version.properties");
}

// ✅ IMPROVED VERSION (optional):
private static InputStream openVersionStream() throws IOException {
    // Try classpath first (works in both IDE and packaged app)
    InputStream fromClasspath = VersionProperties.class.getResourceAsStream("/version.properties");
    if (fromClasspath != null) {
        return fromClasspath;
    }
    
    // Fall back to local file for development
    Path localVersionPath = Paths.get("version.properties");
    if (Files.exists(localVersionPath)) {
        return Files.newInputStream(localVersionPath);
    }
    
    return null;
}

// ============================================================================
// KEY TAKEAWAYS
// ============================================================================
/*
 * YOUR CODE IS MOSTLY CORRECT ALREADY! The main issues are typically:
 * 
 * 1. ✓ ALREADY CORRECT: MainApp.java - CSS and FXML loading
 * 2. ✓ ALREADY CORRECT: CodeVisualizationDialog.java - CSS loading  
 * 3. ✓ ALREADY CORRECT: IconResources.java - Image loading
 * 4. ✓ ALREADY CORRECT: LanguageConfigService.java - JSON loading
 * 5. ✓ ALREADY CORRECT: ChatController.java - WebView HTML handling
 * 
 * WHAT TO VERIFY:
 * - All resource paths start with "/"
 * - No "src/main/resources" in code
 * - Using getClass().getResource() consistently
 * - build.gradle includes all resources in JAR
 * - Running clean build before packaging
 * 
 * OPTIONAL IMPROVEMENTS:
 * - Use ResourceLoader utility for new code (consistency)
 * - Add verifyResources() on app startup (for debugging)
 * - Use ResourceLoader.resourceExists() for safer loading
 */
