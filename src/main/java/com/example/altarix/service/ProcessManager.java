package com.example.altarix.service;

import javafx.application.Platform;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * ProcessManager handles the lifecycle and I/O of a system process.
 *
 * Responsibilities:
 * - Manage process creation and termination
 * - Stream stdout/stderr to output consumer
 * - Accept stdin input from user
 * - Handle process communication on background threads
 * - Provide process state information
 *
 * Thread Safety:
 * - All I/O operations run on background threads (ExecutorService)
 * - Output consumer callbacks are thread-safe via Platform.runLater()
 * - Input writing is synchronized on the output stream
 */
public class ProcessManager {
    private volatile Process process;
    private volatile PrintWriter stdin;
    private volatile boolean isRunning;
    private Consumer<String> outputConsumer;
    private Consumer<String> errorConsumer;
    private Runnable onProcessEnd;
    private CompletableFuture<Integer> processFuture;

    /**
     * Constructor accepting output/error handlers.
     * @param outputConsumer Callback invoked for each stdout line (thread-safe, runs on FX thread)
     * @param errorConsumer  Callback invoked for each stderr line (thread-safe, runs on FX thread)
     * @param onProcessEnd   Callback invoked when process terminates (runs on FX thread)
     */
    public ProcessManager(Consumer<String> outputConsumer, Consumer<String> errorConsumer, Runnable onProcessEnd) {
        this.outputConsumer = outputConsumer;
        this.errorConsumer = errorConsumer;
        this.onProcessEnd = onProcessEnd;
        this.isRunning = false;
    }

    /**
     * Starts a new process with the specified command.
     * This method blocks until the process is fully initialized.
     *
     * @param command Command to execute (e.g., ["cmd", "/c", "echo hello"])
     * @param workingDirectory Working directory for the process (as string)
     * @return true if process started successfully, false otherwise
     */
    public boolean startProcess(String[] command, String workingDirectory) {
        if (isRunning) {
            logError("Process already running. Stop it first.");
            return false;
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File(workingDirectory));
            builder.redirectErrorStream(false); // Keep stderr separate for better handling

            process = builder.start();
            stdin = new PrintWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8),
                    true // Auto-flush
            );
            isRunning = true;

            // Start output streaming threads
            startOutputStreamThread();
            startErrorStreamThread();
            startProcessWaiter();

            return true;
        } catch (IOException ex) {
            logError("Failed to start process: " + ex.getMessage());
            isRunning = false;
            return false;
        }
    }

    /**
     * Sends input to the running process stdin.
     * Safe to call from UI thread.
     *
     * @param input Command or input data to send to process
     */
    public void sendInput(String input) {
        if (!isRunning || stdin == null) {
            logError("Process not running. Cannot send input.");
            return;
        }

        try {
            synchronized (stdin) {
                stdin.println(input);
                stdin.flush();
            }
        } catch (Exception ex) {
            logError("Failed to send input: " + ex.getMessage());
        }
    }

    /**
     * Sends raw input without adding newline.
     * Useful for programs that don't expect line-delimited input.
     *
     * @param input Raw characters to send
     */
    public void sendRawInput(String input) {
        if (!isRunning || stdin == null) {
            logError("Process not running. Cannot send input.");
            return;
        }

        try {
            synchronized (stdin) {
                stdin.print(input);
                stdin.flush();
            }
        } catch (Exception ex) {
            logError("Failed to send raw input: " + ex.getMessage());
        }
    }

    /**
     * Terminates the process gracefully.
     */
    public void terminate() {
        if (process == null) {
            return;
        }

        try {
            if (stdin != null) {
                stdin.close();
            }
            process.destroy();
            isRunning = false;
        } catch (Exception ex) {
            logError("Error terminating process: " + ex.getMessage());
        }
    }

    /**
     * Force-kills the process if graceful termination fails.
     */
    public void forceKill() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            isRunning = false;
        }
    }

    /**
     * Checks if process is currently running.
     * @return true if process is alive, false otherwise
     */
    public boolean isProcessRunning() {
        return isRunning && process != null && process.isAlive();
    }

    /**
     * Gets the exit code of the terminated process.
     * @return Exit code, or -1 if process still running or not started
     */
    public int getExitCode() {
        if (process == null || process.isAlive()) {
            return -1;
        }
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException ex) {
            return -1;
        }
    }

    /**
     * Streams all lines from stdout in a background thread.
     * Lines are passed to outputConsumer via Platform.runLater().
     */
    private void startOutputStreamThread() {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null && isRunning) {
                    final String output = line;
                    if (outputConsumer != null) {
                        Platform.runLater(() -> outputConsumer.accept(output));
                    }
                }
            } catch (IOException ex) {
                if (isRunning && !ex.getMessage().contains("Stream closed")) {
                    logError("Output stream error: " + ex.getMessage());
                }
            }
        });
        thread.setDaemon(true);
        thread.setName("ProcessManager-Stdout");
        thread.start();
    }

    /**
     * Streams all lines from stderr in a background thread.
     * Lines are passed to errorConsumer via Platform.runLater().
     */
    private void startErrorStreamThread() {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null && isRunning) {
                    final String error = line;
                    if (errorConsumer != null) {
                        Platform.runLater(() -> errorConsumer.accept(error));
                    }
                }
            } catch (IOException ex) {
                if (isRunning && !ex.getMessage().contains("Stream closed")) {
                    logError("Error stream error: " + ex.getMessage());
                }
            }
        });
        thread.setDaemon(true);
        thread.setName("ProcessManager-Stderr");
        thread.start();
    }

    /**
     * Waits for process termination and triggers cleanup.
     */
    private void startProcessWaiter() {
        Thread thread = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                isRunning = false;

                if (onProcessEnd != null) {
                    Platform.runLater(() -> {
                        try {
                            onProcessEnd.run();
                        } catch (Exception ex) {
                            logError("Error in process end handler: " + ex.getMessage());
                        }
                    });
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        thread.setDaemon(true);
        thread.setName("ProcessManager-Waiter");
        thread.start();
    }

    /**
     * Logs error messages safely on the FX thread.
     */
    private void logError(String message) {
        Platform.runLater(() -> {
            if (errorConsumer != null) {
                errorConsumer.accept("[ProcessManager] " + message);
            }
        });
    }
}
