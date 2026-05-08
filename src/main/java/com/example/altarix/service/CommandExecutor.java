package com.example.altarix.service;

import javafx.application.Platform;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * CommandExecutor handles one-off command execution with output streaming.
 *
 * Unlike TerminalService (which maintains a persistent shell),
 * CommandExecutor is used for running individual commands that don't
 * require an interactive session (e.g., executing code snippets, running scripts).
 *
 * Features:
 * - Execute commands synchronously or asynchronously
 * - Stream output in real-time (no blocking)
 * - Capture full output for non-streamed mode
 * - Timeout support to prevent hanging
 * - Runs all operations on background threads
 *
 * Example Usage:
 * ```
 * CommandExecutor executor = new CommandExecutor(
 *     line -> appendTerminalLine(line),              // stdout
 *     error -> appendTerminalLine("[ERR] " + error)  // stderr
 * );
 *
 * // Execute with streaming output
 * executor.executeAsync(
 *     new String[]{"python", "script.py"},
 *     Path.of("/home/user"),
 *     line -> System.out.println(line)
 * );
 *
 * // Or execute synchronously
 * CommandExecutor.Result result = executor.execute(
 *     new String[]{"dir"},
 *     Path.of("."),
 *     45  // 45-second timeout
 * );
 *
 * System.out.println("Exit code: " + result.exitCode);
 * System.out.println("Output: " + result.output);
 * ```
 */
public class CommandExecutor {
    private Consumer<String> onOutput;
    private Consumer<String> onError;
    private static final long DEFAULT_TIMEOUT_SECONDS = 45;

    /**
     * Result record for command execution.
     * @param exitCode Process exit code
     * @param output Full stdout output (or empty if streamed)
     * @param error Full stderr output (or empty if streamed)
     * @param timedOut true if process exceeded timeout
     */
    public record Result(int exitCode, String output, String error, boolean timedOut) {}

    /**
     * Constructor with output handlers.
     *
     * @param onOutput Consumer called for each stdout line (thread-safe)
     * @param onError Consumer called for each stderr line (thread-safe)
     */
    public CommandExecutor(Consumer<String> onOutput, Consumer<String> onError) {
        this.onOutput = onOutput != null ? onOutput : line -> {};
        this.onError = onError != null ? onError : error -> {};
    }

    /**
     * Executes a command synchronously with streaming output.
     * Blocks until process completes or timeout is reached.
     *
     * @param command Command array (e.g., ["python", "script.py"])
     * @param workingDirectory Working directory for the process
     * @param timeoutSeconds Maximum time to wait for completion
     * @return CommandExecutor.Result with exit code and output
     */
    public Result execute(String[] command, Path workingDirectory, long timeoutSeconds) {
        return executeInternal(command, workingDirectory, timeoutSeconds, true, null);
    }

    /**
     * Executes a command synchronously without timeout.
     *
     * @param command Command array
     * @param workingDirectory Working directory
     * @return CommandExecutor.Result
     */
    public Result execute(String[] command, Path workingDirectory) {
        return execute(command, workingDirectory, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Executes a command asynchronously with streaming output.
     * Returns immediately; output is streamed via callbacks.
     *
     * @param command Command array
     * @param workingDirectory Working directory
     * @param onComplete Callback invoked when process completes with Result
     */
    public void executeAsync(String[] command, Path workingDirectory, Consumer<Result> onComplete) {
        new Thread(() -> {
            Result result = execute(command, workingDirectory);
            if (onComplete != null) {
                Platform.runLater(() -> onComplete.accept(result));
            }
        }, "CommandExecutor-" + System.identityHashCode(this)).start();
    }

    /**
     * Executes a command asynchronously with custom timeout.
     * @param command Command array
     * @param workingDirectory Working directory
     * @param timeoutSeconds Timeout in seconds
     * @param onComplete Callback when process completes
     */
    public void executeAsync(String[] command, Path workingDirectory, long timeoutSeconds, Consumer<Result> onComplete) {
        new Thread(() -> {
            Result result = execute(command, workingDirectory, timeoutSeconds);
            if (onComplete != null) {
                Platform.runLater(() -> onComplete.accept(result));
            }
        }, "CommandExecutor-" + System.identityHashCode(this)).start();
    }

    /**
     * Internal implementation of command execution.
     *
     * @param command Command to execute
     * @param workingDirectory Working directory
     * @param timeoutSeconds Timeout limit
     * @param captureOutput Whether to capture output as string
     * @param outputCallback Optional callback for streaming output
     * @return CommandExecutor.Result
     */
    private Result executeInternal(String[] command, Path workingDirectory, long timeoutSeconds,
                                   boolean captureOutput, Consumer<String> outputCallback) {
        StringBuilder outputBuilder = captureOutput ? new StringBuilder() : null;
        StringBuilder errorBuilder = new StringBuilder();
        boolean timedOut = false;

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(false);

            Process process = builder.start();
            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);

            // Stream stdout
            Thread outThread = new Thread(() -> streamOutput(
                    process.getInputStream(),
                    outputBuilder,
                    outputCallback
            ), "CommandExecutor-Output");
            outThread.setDaemon(true);
            outThread.start();

            // Stream stderr
            Thread errThread = new Thread(() -> streamOutput(
                    process.getErrorStream(),
                    errorBuilder,
                    null
            ), "CommandExecutor-Error");
            errThread.setDaemon(true);
            errThread.start();

            // Wait for completion with timeout
            while (true) {
                if (process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    // Process finished
                    outThread.join(1000);
                    errThread.join(1000);
                    break;
                }

                if (System.nanoTime() >= deadlineNanos) {
                    // Timeout reached
                    timedOut = true;
                    process.destroyForcibly();
                    String timeoutMsg = "Process timed out after " + timeoutSeconds + " seconds";
                    onError.accept(timeoutMsg);
                    break;
                }
            }

            int exitCode = process.isAlive() ? -1 : process.exitValue();
            return new Result(
                    exitCode,
                    outputBuilder != null ? outputBuilder.toString() : "",
                    errorBuilder.toString(),
                    timedOut
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(-1, "", "Interrupted: " + ex.getMessage(), false);
        } catch (IOException ex) {
            String error = "Failed to execute command: " + ex.getMessage();
            onError.accept(error);
            return new Result(-1, "", error, false);
        }
    }

    /**
     * Streams lines from an InputStream to output handler and optional StringBuilder.
     *
     * @param inputStream Stream to read from
     * @param outputBuilder Optional StringBuilder to capture output
     * @param callback Optional callback for each line
     */
    private void streamOutput(InputStream inputStream, StringBuilder outputBuilder, Consumer<String> callback) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // Capture to StringBuilder if provided
                if (outputBuilder != null) {
                    outputBuilder.append(line).append("\n");
                }

                // Call callback if provided
                if (callback != null) {
                    final String finalLine = line;
                    Platform.runLater(() -> callback.accept(finalLine));
                }

                // Always call onOutput
                final String finalLine = line;
                if (callback == null) { // Don't duplicate if callback was provided
                    Platform.runLater(() -> onOutput.accept(finalLine));
                }
            }
        } catch (IOException ex) {
            if (!"Stream closed".equals(ex.getMessage())) {
                final String errorMsg = "Stream error: " + ex.getMessage();
                Platform.runLater(() -> onError.accept(errorMsg));
            }
        }
    }

    /**
     * Executes a command in the system shell and returns the full output.
     * Useful for running shell scripts or commands that require shell features.
     *
     * @param shellCommand Shell command (e.g., "dir" on Windows, "ls" on Unix)
     * @param workingDirectory Working directory
     * @return CommandExecutor.Result with full output
     */
    public Result executeShellCommand(String shellCommand, Path workingDirectory) {
        String[] command;
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            command = new String[]{"cmd", "/c", shellCommand};
        } else {
            command = new String[]{"/bin/sh", "-c", shellCommand};
        }

        return execute(command, workingDirectory);
    }
}
