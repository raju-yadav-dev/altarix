package com.example.chatbot.service;

import com.example.chatbot.service.LanguageConfigService.LanguageEntry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handles code execution with structured terminal output, timing,
 * timeout enforcement, command preview, and error formatting.
 */
public class CodeExecutionService {

    private static final String SEPARATOR = "========";
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)\\b");

    private final LanguageConfigService langConfig;
    private final Consumer<String> rawOutputCallback;

    public CodeExecutionService(LanguageConfigService langConfig, Consumer<String> rawOutputCallback) {
        this.langConfig = langConfig;
        this.rawOutputCallback = rawOutputCallback;
    }

    // ================= PUBLIC API =================

    /**
     * Execute a code snippet, handling compilation, execution, timing, and structured output.
     * All output is streamed via rawOutputCallback. Returns the final exit code.
     */
    public ExecutionResult execute(String languageKey, String code, ExecutionHandle handle) {
        LanguageEntry lang = langConfig.getLanguage(languageKey);
        if (lang == null) {
            return new ExecutionResult(-1, "Unsupported language: " + languageKey, false);
        }

        String displayName = lang.getDisplayName();
        int timeoutSeconds = lang.getTimeoutSeconds();

        appendLine("");
        appendLine(SEPARATOR + " " + displayName + " Execution " + SEPARATOR);

        try {
            return switch (languageKey) {
                case "python", "javascript", "powershell", "bash" ->
                        executeInterpreted(lang, code, handle, timeoutSeconds);
                case "java" -> executeJava(code, handle, timeoutSeconds);
                case "c" -> executeC(code, false, handle, timeoutSeconds);
                case "cpp" -> executeC(code, true, handle, timeoutSeconds);
                default -> new ExecutionResult(-1, "Unsupported language: " + languageKey, false);
            };
        } catch (Exception ex) {
            appendLine("");
            appendLine(SEPARATOR + " Error " + SEPARATOR);
            appendLine(ex.getMessage());
            return new ExecutionResult(-1, ex.getMessage(), true);
        }
    }

    // ================= INTERPRETED LANGUAGES =================

    private ExecutionResult executeInterpreted(LanguageEntry lang, String code,
            ExecutionHandle handle, int timeoutSeconds) throws IOException, InterruptedException {

        String resolvedCmd = langConfig.resolveCommand(lang.key);
        if (resolvedCmd == null) {
            appendLine(lang.getDisplayName() + " runtime is not installed.");
            return exitResult(-1, lang.getDisplayName(), 0);
        }

        Path tempDir = Files.createTempDirectory("Altarix-run-");
        try {
            String ext = (lang.extensions != null && !lang.extensions.isEmpty())
                    ? lang.extensions.get(0) : ".tmp";
            Path scriptFile = tempDir.resolve("snippet" + ext);
            Files.writeString(scriptFile, code, StandardCharsets.UTF_8);

            List<String> command = buildInterpretedCommand(resolvedCmd, lang, scriptFile);

            long start = System.nanoTime();
            RunProcessResult result = runProcess(command, tempDir, handle, timeoutSeconds);
            double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;

            return exitResult(result.exitCode, lang.getDisplayName(), elapsed);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private List<String> buildInterpretedCommand(String resolvedCmd, LanguageEntry lang, Path scriptFile) {
        List<String> command = new ArrayList<>();

        // Handle special cases for python and powershell
        if ("python".equals(lang.key)) {
            command.add(resolvedCmd);
            if ("py".equals(resolvedCmd)) command.add("-3");
        } else if ("powershell".equals(lang.key)) {
            command.add(resolvedCmd);
            command.add("-NoProfile");
            command.add("-ExecutionPolicy");
            command.add("Bypass");
            command.add("-File");
        } else {
            command.add(resolvedCmd);
        }

        command.add(scriptFile.toAbsolutePath().toString());
        return command;
    }

    // ================= JAVA =================

    private ExecutionResult executeJava(String code, ExecutionHandle handle, int timeoutSeconds)
            throws IOException, InterruptedException {

        if (!langConfig.isCommandAvailable("javac") || !langConfig.isCommandAvailable("java")) {
            appendLine("Java runtime/compiler (javac/java) is not installed.");
            return exitResult(-1, "Java", 0);
        }

        Path tempDir = Files.createTempDirectory("Altarix-java-run-");
        try {
            String source = code;
            String className = extractJavaClassName(code);

            if (className == null) {
                className = "SnippetMain";
                source = "public class " + className + " {\n"
                        + "    public static void main(String[] args) {\n"
                        + code.indent(8)
                        + "    }\n"
                        + "}\n";
            }

            Path sourceFile = tempDir.resolve(className + ".java");
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            // Compile
            List<String> compileCmd = List.of("javac", sourceFile.toAbsolutePath().toString());
            long start = System.nanoTime();
            RunProcessResult compileResult = runProcess(compileCmd, tempDir, handle, timeoutSeconds);
            if (compileResult.exitCode != 0) {
                double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
                appendLine("");
                appendLine(SEPARATOR + " Compilation Error " + SEPARATOR);
                return exitResult(compileResult.exitCode, "Java", elapsed);
            }

            // Run
            List<String> runCmd = List.of("java", "-cp", tempDir.toAbsolutePath().toString(), className);
            RunProcessResult runResult = runProcess(runCmd, tempDir, handle, timeoutSeconds);
            double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;

            return exitResult(runResult.exitCode, "Java", elapsed);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    // ================= C / C++ =================

    private ExecutionResult executeC(String code, boolean isCpp, ExecutionHandle handle, int timeoutSeconds)
            throws IOException, InterruptedException {

        String langLabel = isCpp ? "C++" : "C";
        String resolvedCompiler = langConfig.resolveCommand(isCpp ? "cpp" : "c");
        if (resolvedCompiler == null) {
            appendLine(langLabel + " compiler is not installed.");
            return exitResult(-1, langLabel, 0);
        }

        Path tempDir = Files.createTempDirectory("Altarix-" + (isCpp ? "cpp" : "c") + "-run-");
        try {
            String sourceExt = isCpp ? ".cpp" : ".c";
            boolean isWindows = isWindows();
            Path sourceFile = tempDir.resolve("snippet" + sourceExt);
            Path binaryFile = tempDir.resolve(isWindows ? "snippet.exe" : "snippet.out");
            Files.writeString(sourceFile, code, StandardCharsets.UTF_8);

            // Compile
            List<String> compileCmd = buildCCompileCommand(resolvedCompiler, sourceFile, binaryFile);
            long start = System.nanoTime();
            RunProcessResult compileResult = runProcess(compileCmd, tempDir, handle, timeoutSeconds);
            if (compileResult.exitCode != 0) {
                double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
                appendLine("");
                appendLine(SEPARATOR + " Compilation Error " + SEPARATOR);
                return exitResult(compileResult.exitCode, langLabel, elapsed);
            }

            // Run
            List<String> runCmd = List.of(binaryFile.toAbsolutePath().toString());
            RunProcessResult runResult = runProcess(runCmd, tempDir, handle, timeoutSeconds);
            double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;

            return exitResult(runResult.exitCode, langLabel, elapsed);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private List<String> buildCCompileCommand(String compiler, Path sourceFile, Path binaryFile) {
        List<String> cmd = new ArrayList<>();
        String lowerCompiler = compiler.toLowerCase(Locale.ROOT);

        if (lowerCompiler.equals("cl")) {
            cmd.add(compiler);
            cmd.add("/Fe:" + binaryFile.toAbsolutePath());
            cmd.add(sourceFile.toAbsolutePath().toString());
        } else {
            cmd.add(compiler);
            cmd.add(sourceFile.toAbsolutePath().toString());
            cmd.add("-o");
            cmd.add(binaryFile.toAbsolutePath().toString());
        }
        return cmd;
    }

    // ================= PROCESS EXECUTION =================

    private RunProcessResult runProcess(List<String> command, Path workingDirectory,
            ExecutionHandle handle, int timeoutSeconds) throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        handle.process = process;
        handle.processInput = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        CompletableFuture<Void> outputPump = CompletableFuture.runAsync(() -> {
            try (InputStreamReader reader = new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    appendRaw(new String(buffer, 0, read));
                }
            } catch (IOException ex) {
                if (!handle.cancelRequested && ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    appendLine("[Run] Output stream closed: " + ex.getMessage());
                }
            }
        });

        // Timeout enforcement
        long deadlineMs = timeoutSeconds * 1000L;
        long startMs = System.currentTimeMillis();

        while (true) {
            if (handle.cancelRequested) {
                process.destroyForcibly();
                break;
            }
            long elapsed = System.currentTimeMillis() - startMs;
            if (elapsed >= deadlineMs) {
                process.destroyForcibly();
                appendLine("");
                appendLine(SEPARATOR + " Timeout " + SEPARATOR);
                appendLine("Execution stopped after " + timeoutSeconds + " seconds (possible infinite loop).");
                handle.timedOut = true;
                break;
            }
            if (process.waitFor(120, TimeUnit.MILLISECONDS)) {
                break;
            }
        }

        process.waitFor(2, TimeUnit.SECONDS);
        outputPump.join();

        BufferedWriter processInput = handle.processInput;
        if (processInput != null) {
            try { processInput.close(); } catch (IOException ignored) { }
        }
        handle.processInput = null;
        handle.process = null;

        if (handle.cancelRequested || handle.timedOut) {
            return new RunProcessResult(-1);
        }
        return new RunProcessResult(process.isAlive() ? -1 : process.exitValue());
    }

    // ================= OUTPUT FORMATTING =================

    private ExecutionResult exitResult(int exitCode, String displayName, double elapsedSeconds) {
        appendLine("");
        if (elapsedSeconds > 0) {
            appendLine(String.format("Execution time: %.2f seconds", elapsedSeconds));
        }
        appendLine(SEPARATOR + " Exit code: " + exitCode + " " + SEPARATOR);
        return new ExecutionResult(exitCode, "", true);
    }

    private void appendRaw(String text) {
        if (rawOutputCallback != null) rawOutputCallback.accept(text);
    }

    private void appendLine(String line) {
        appendRaw(line + System.lineSeparator());
    }

    // ================= UTILITIES =================

    private String extractJavaClassName(String code) {
        Matcher publicMatcher = PUBLIC_CLASS_PATTERN.matcher(code);
        if (publicMatcher.find()) return publicMatcher.group(1);
        Matcher classMatcher = CLASS_PATTERN.matcher(code);
        if (classMatcher.find()) return classMatcher.group(1);
        return null;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    // ================= RESULT TYPES =================

    public record ExecutionResult(int exitCode, String message, boolean streamed) {
    }

    /** Handle for the caller to track/cancel the running process. */
    public static final class ExecutionHandle {
        public volatile Process process;
        public volatile BufferedWriter processInput;
        public volatile boolean cancelRequested;
        public volatile boolean stopRequestedByUser;
        public volatile boolean timedOut;
    }

    private record RunProcessResult(int exitCode) {
    }
}

