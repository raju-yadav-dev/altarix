package com.example.chatbot.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

/**
 * ResourceLoader - Utility class for proper classpath-based resource loading.
 * 
 * This utility ensures all resources (CSS, FXML, Images) are loaded correctly
 * in both development (IDE) and packaged (EXE) environments.
 * 
 * Key principles:
 * - All resource paths MUST start with "/" (absolute classpath path)
 * - Never use File-based loading or src/main/resources in code
 * - Always use getClass().getResource() or getClass().getResourceAsStream()
 * - Prefer this utility over direct classpath loading for consistency
 */
public final class ResourceLoader {
    
    private ResourceLoader() {
        // Utility class
    }
    
    // ================= CSS LOADING =================
    
    /**
     * Load a CSS stylesheet and add it to a Stage's scene.
     * 
     * Usage:
     * ResourceLoader.loadStylesheet(scene, "/css/styles.css");
     * 
     * @param stage The Stage whose scene will receive the stylesheet
     * @param cssResourcePath Absolute classpath path (e.g., "/css/styles.css")
     * @throws IllegalArgumentException if resource not found
     */
    public static void loadStylesheet(Stage stage, String cssResourcePath) {
        if (stage == null || stage.getScene() == null) {
            throw new IllegalArgumentException("Stage or Scene is null");
        }
        loadStylesheet(stage.getScene(), cssResourcePath);
    }
    
    /**
     * Load a CSS stylesheet and add it to a Scene.
     * 
     * Usage:
     * ResourceLoader.loadStylesheet(scene, "/css/styles.css");
     * 
     * @param scene The Scene to apply the stylesheet to
     * @param cssResourcePath Absolute classpath path (e.g., "/css/styles.css")
     * @throws IllegalArgumentException if resource not found
     */
    public static void loadStylesheet(javafx.scene.Scene scene, String cssResourcePath) {
        if (scene == null) {
            throw new IllegalArgumentException("Scene is null");
        }
        
        String externalForm = getResourceExternalForm(cssResourcePath);
        if (!scene.getStylesheets().contains(externalForm)) {
            scene.getStylesheets().add(externalForm);
        }
    }
    
    /**
     * Get the external form URL of a CSS resource for manual stylesheet management.
     * 
     * Usage:
     * String cssUrl = ResourceLoader.getCssUrl("/css/styles.css");
     * scene.getStylesheets().add(cssUrl);
     * 
     * @param cssResourcePath Absolute classpath path
     * @return External form URL string
     */
    public static String getCssUrl(String cssResourcePath) {
        return getResourceExternalForm(cssResourcePath);
    }
    
    // ================= FXML LOADING =================
    
    /**
     * Load an FXML file using FXMLLoader.
     * 
     * Usage:
     * FXMLLoader loader = ResourceLoader.createFXMLLoader("/fxml/main.fxml");
     * Parent root = loader.load();
     * 
     * @param fxmlResourcePath Absolute classpath path (e.g., "/fxml/main.fxml")
     * @return Configured FXMLLoader instance
     * @throws IllegalArgumentException if resource not found
     */
    public static FXMLLoader createFXMLLoader(String fxmlResourcePath) {
        URL fxmlUrl = getResourceUrl(fxmlResourcePath);
        return new FXMLLoader(fxmlUrl);
    }
    
    /**
     * Load an FXML file and return the root Node.
     * 
     * Usage:
     * Parent root = ResourceLoader.loadFXML(getClass(), "/fxml/main.fxml");
     * 
     * @param fxmlResourcePath Absolute classpath path
     * @return Loaded FXML root Node
     * @throws IOException if FXML loading fails
     */
    public static Node loadFXML(String fxmlResourcePath) throws IOException {
        FXMLLoader loader = createFXMLLoader(fxmlResourcePath);
        return loader.load();
    }
    
    /**
     * Load an FXML file and return the controller.
     * 
     * Usage:
     * MyController controller = ResourceLoader.loadFXMLWithController("/fxml/main.fxml", MyController.class);
     * 
     * @param fxmlResourcePath Absolute classpath path
     * @param controllerClass Expected controller type
     * @param <T> Controller type
     * @return The controller instance
     * @throws IOException if FXML loading fails
     */
    public static <T> T loadFXMLWithController(String fxmlResourcePath, Class<T> controllerClass) throws IOException {
        FXMLLoader loader = createFXMLLoader(fxmlResourcePath);
        loader.load();
        return loader.getController();
    }
    
    // ================= IMAGE LOADING =================
    
    /**
     * Load an image from classpath resources.
     * 
     * Usage:
     * Image icon = ResourceLoader.loadImage("/icon/app-icon.png");
     * 
     * @param imageResourcePath Absolute classpath path (e.g., "/icon/icon.png")
     * @return Loaded Image object
     * @throws IllegalArgumentException if resource not found
     */
    public static Image loadImage(String imageResourcePath) {
        String externalForm = getResourceExternalForm(imageResourcePath);
        return new Image(externalForm);
    }
    
    /**
     * Load an image with specific width and height.
     * 
     * Usage:
     * Image icon = ResourceLoader.loadImage("/icon/icon.png", 32, 32, true, true);
     * 
     * @param imageResourcePath Absolute classpath path
     * @param width Desired width
     * @param height Desired height
     * @param preserveRatio Whether to preserve aspect ratio
     * @param smooth Whether to smooth the image
     * @return Loaded Image object
     */
    public static Image loadImage(String imageResourcePath, double width, double height, 
                                  boolean preserveRatio, boolean smooth) {
        String externalForm = getResourceExternalForm(imageResourcePath);
        return new Image(externalForm, width, height, preserveRatio, smooth);
    }
    
    /**
     * Load multiple icon sizes for a Stage (for best display across OS).
     * 
     * Usage:
     * ResourceLoader.loadStageIcons(stage, 
     *     "/icon/app-16.png", 
     *     "/icon/app-32.png", 
     *     "/icon/app-64.png");
     * 
     * @param stage The Stage to apply icons to
     * @param iconResourcePaths Absolute classpath paths for icon files
     */
    public static void loadStageIcons(Stage stage, String... iconResourcePaths) {
        if (stage == null) {
            throw new IllegalArgumentException("Stage is null");
        }
        
        for (String iconPath : iconResourcePaths) {
            try {
                Image icon = loadImage(iconPath);
                stage.getIcons().add(icon);
            } catch (Exception ex) {
                System.err.println("[ResourceLoader] Failed to load icon: " + iconPath + " - " + ex.getMessage());
            }
        }
    }
    
    // ================= RESOURCE STREAM LOADING =================
    
    /**
     * Load a resource as an InputStream (for JSON, config files, etc.).
     * 
     * Usage:
     * try (InputStream is = ResourceLoader.getResourceStream("/config/settings.json")) {
     *     // Process stream
     * }
     * 
     * @param resourcePath Absolute classpath path
     * @return InputStream for the resource
     * @throws IllegalArgumentException if resource not found
     */
    public static InputStream getResourceStream(String resourcePath) {
        InputStream is = ResourceLoader.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }
        return is;
    }
    
    /**
     * Get resource as string content (for small files).
     * 
     * Usage:
     * String jsonContent = ResourceLoader.getResourceAsString("/config/settings.json");
     * 
     * @param resourcePath Absolute classpath path
     * @return File contents as String
     * @throws IOException if reading fails
     */
    public static String getResourceAsString(String resourcePath) throws IOException {
        try (InputStream is = getResourceStream(resourcePath)) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    // ================= INTERNAL HELPERS =================
    
    /**
     * Get the URL for a classpath resource.
     * 
     * @param resourcePath Absolute classpath path (must start with "/")
     * @return URL object
     * @throws IllegalArgumentException if resource not found
     */
    private static URL getResourceUrl(String resourcePath) {
        if (!resourcePath.startsWith("/")) {
            throw new IllegalArgumentException("Resource path must start with '/': " + resourcePath);
        }
        
        URL url = ResourceLoader.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found in classpath: " + resourcePath);
        }
        
        return url;
    }
    
    /**
     * Get the external form (string URL) for a resource.
     * 
     * @param resourcePath Absolute classpath path
     * @return External form string suitable for CSS stylesheets
     */
    private static String getResourceExternalForm(String resourcePath) {
        return Objects.requireNonNull(
            getResourceUrl(resourcePath),
            "Resource not found: " + resourcePath
        ).toExternalForm();
    }
    
    /**
     * Verify if a resource exists in the classpath.
     * 
     * Usage:
     * if (ResourceLoader.resourceExists("/css/theme.css")) {
     *     loadStylesheet(scene, "/css/theme.css");
     * }
     * 
     * @param resourcePath Absolute classpath path
     * @return true if resource exists, false otherwise
     */
    public static boolean resourceExists(String resourcePath) {
        return ResourceLoader.class.getResource(resourcePath) != null;
    }
}
