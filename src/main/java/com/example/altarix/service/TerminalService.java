package com.example.altarix.service;

import javafx.application.Platform;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * TerminalService manages an interactive shell terminal with persistent process.
 *
 * Key Features:
 * - Maintains a single, persistent shell process (cmd.exe, powershell, or bash)
 * - Supports interactive programs that require stdin/stdout
 * - Handles large outputs without blocking the UI
 * - Provides thread-safe input/output methods
 * - Manages working directory changes
 *
 * Architecture:
 * - Uses ProcessManager for low-level process I/O
 * - Creates a shell process on demand (lazy initialization)
 * - Streams all output through callback functions (thread-safe)
 * - All operations are UI-thread safe
 *
 * Example Usage in ChatController:
 * ```
 * TerminalService terminal = new TerminalService(
 *     line -> appendTerminalLine(line),          // stdout handler
 *     error -> appendTerminalLine("[ERR] " + error), // stderr handler
 *     () -> updateStatus("Ready")                 // process end handler
 * );
 *
 * // Initialize persistent shell
 * terminal.initialize(terminalWorkingDirectory);
 *
 * // Send commands (user input from UI)
 * terminal.executeCommand("python script.py");
 *
 * // Or send interactive input
 * terminal.sendInput(userInput);
 *
 * // Cleanup when application exits
 * terminal.shutdown();
 * ```
 *
 * Shell Detection:
 * - Windows: PowerShell (pwsh) → PowerShell → cmd.exe
 * - Linux/macOS: bash → sh
 */
public class TerminalService {
    private ProcessManager processManager;
    private Consumer<String> onOutput;
    private Consumer<String> onError;
    private Runnable onProcessEnd;
    private Path currentWorkingDirectory;
    private boolean isInitialized;
    private volatile boolean shutdownRequested;

    /**
     * Constructor with output handlers.
     *
     * @param onOutput     Consumer called for each stdout line (thread-safe)
     * @param onError      Consumer called for each stderr line (thread-safe)
     * @param onProcessEnd Callback when shell process terminates (thread-safe)
     */
    public TerminalService(Consumer<String> onOutput, Consumer<String> onError, Runnable onProcessEnd) {
        this.onOutput = onOutput != null ? onOutput : line -> {};
        this.onError = onError != null ? onError : error -> {};
        this.onProcessEnd = onProcessEnd != null ? onProcessEnd : () -> {};
        this.isInitialized = false;
        this.shutdownRequested = false;
    }

    /**
     * Initializes the persistent shell process.
     *
     * @param workingDirectory Starting directory for the shell
     * @return true if shell started successfully, false otherwise
     */
    public boolean initialize(Path workingDirectory) {
        if (isInitialized) {
            logMessage("Terminal already initialized");
            return true;
        }

        this.currentWorkingDirectory = workingDirectory != null
                ? workingDirectory
                : Path.of(System.getProperty("user.home"));

        try {
            processManager = new ProcessManager(onOutput, onError, onProcessEnd);
            String[] shellCommand = buildShellCommand();
            boolean started = processManager.startProcess(shellCommand, currentWorkingDirectory.toString());

            if (started) {
                isInitialized = true;
                logMessage("Terminal initialized with " + getShellName());
                return true;
            } else {
                logError("Failed to start shell process");
                return false;
            }
        } catch (Exception ex) {
            logError("Exception during initialization: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Executes a command in the persistent shell.
     * This method sends the command followed by a newline to the shell.
     *
     * @param command The command to execute (e.g., "dir", "ls", "python script.py")
     */
    public void executeCommand(String command) {
        if (!isInitialized || processManager == null) {
            logError("Terminal not initialized. Call initialize() first.");
            return;
        }

        if (command == null || command.trim().isEmpty()) {
            logMessage(""); // Just print empty line
            return;
        }

        processManager.sendInput(command);
    }

    /**
     * Sends raw input to the shell (without adding newline).
     * Useful for programs that read single characters or don't expect line-delimited input.
     *
     * @param input Raw input to send (e.g., "y" for yes/no prompt, "5" for menu selection)
     */
    public void sendInput(String input) {
        if (!isInitialized || processManager == null) {
            logError("Terminal not initialized. Call initialize() first.");
            return;
        }

        if (input == null) {
            return;
        }

        processManager.sendRawInput(input);
    }

    /**
     * Sends input followed by a newline.
     * Most interactive programs expect line-delimited input.
     *
     * @param input Input line to send
     */
    public void sendInputLine(String input) {
        if (!isInitialized || processManager == null) {
            logError("Terminal not initialized. Call initialize() first.");
            return;
        }

        if (input == null) {
            processManager.sendInput("");
        } else {
            processManager.sendInput(input);
        }
    }

    /**
     * Changes the working directory for subsequent commands.
     * This sends a cd command to the shell.
     *
     * @param path New directory path
     * @return true if directory change was sent, false if initialization failed
     */
    public boolean changeDirectory(Path path) {
        if (!isInitialized || processManager == null) {
            logError("Terminal not initialized. Call initialize() first.");
            return false;
        }

        Path nextDirectory = path.toAbsolutePath().normalize();

        if (!java.nio.file.Files.exists(nextDirectory)) {
            logError("Directory does not exist: " + nextDirectory);
            return false;
        }

        if (!java.nio.file.Files.isDirectory(nextDirectory)) {
            logError("Path is not a directory: " + nextDirectory);
            return false;
        }

        currentWorkingDirectory = nextDirectory;
        String cdCommand = isWindows() ? "cd \"" + nextDirectory + "\"" : "cd \"" + nextDirectory + "\"";
        processManager.sendInput(cdCommand);

        return true;
    }

    /**
     * Gets the current working directory of the shell.
     * @return Current working directory path
     */
    public Path getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    /**
     * Checks if the shell is currently running.
     * @return true if shell process is alive
     */
    public boolean isShellRunning() {
        return isInitialized && processManager != null && processManager.isProcessRunning();
    }

    /**
     * Gets the exit code of the (terminated) shell process.
     * @return Exit code, or -1 if shell still running
     */
    public int getExitCode() {
        if (processManager == null) {
            return -1;
        }
        return processManager.getExitCode();
    }

    /**
     * Terminates the shell process gracefully.
     * Useful at application shutdown.
     */
    public void shutdown() {
        shutdownRequested = true;
        if (processManager != null) {
            try {
                processManager.terminate();
                // Wait a bit for graceful shutdown
                Thread.sleep(500);
                if (processManager.isProcessRunning()) {
                    processManager.forceKill();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            processManager = null;
        }
        isInitialized = false;
    }

    /**
     * Restarts the shell process (e.g., for recovery after crash).
     * @return true if restart successful
     */
    public boolean restart() {
        shutdown();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return initialize(currentWorkingDirectory);
    }

    // ================= SHELL DETECTION =================

    /**
     * Detects and builds the appropriate shell command for the OS.
     * Windows: PowerShell (preferred) → cmd.exe
     * Unix: bash → sh
     *
     * @return Command array to start the shell
     */
    private String[] buildShellCommand() {
        if (isWindows()) {
            // Try PowerShell first (better functionality)
            if (isCommandAvailable("pwsh")) {
                return new String[]{"pwsh", "-NoProfile", "-NoExit", "-Command", ""};
            }
            if (isCommandAvailable("powershell")) {
                return new String[]{"powershell", "-NoProfile", "-NoExit"};
            }
            // Fall back to cmd.exe
            return new String[]{"cmd", "/k", "cd /d " + currentWorkingDirectory};
        } else {
            // Unix-like systems
            if (isCommandAvailable("bash")) {
                return new String[]{"bash", "-i"};
            }
            return new String[]{"sh", "-i"};
        }
    }

    /**
     * Gets the name of the detected shell for display purposes.
     * @return Shell name (e.g., "PowerShell", "cmd.exe", "bash")
     */
    public String getShellName() {
        if (isWindows()) {
            if (isCommandAvailable("pwsh")) {
                return "PowerShell (pwsh)";
            }
            if (isCommandAvailable("powershell")) {
                return "PowerShell";
            }
            return "cmd.exe";
        }
        if (isCommandAvailable("bash")) {
            return "bash";
        }
        return "sh";
    }

    /**
     * Checks if a command is available in the system PATH.
     * @param commandName Command to check (e.g., "pwsh", "bash")
     * @return true if command exists, false otherwise
     */
    private boolean isCommandAvailable(String commandName) {
        try {
            String[] probe = isWindows()
                    ? new String[]{"cmd", "/c", "where", commandName}
                    : new String[]{"sh", "-c", "command -v " + commandName};

            ProcessBuilder pb = new ProcessBuilder(probe);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Checks if the operating system is Windows.
     * @return true if running on Windows
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    // ================= LOGGING =================

    /**
     * Logs informational messages.
     */
    private void logMessage(String message) {
        Platform.runLater(() -> onOutput.accept("[Terminal] " + message));
    }

    /**
     * Logs error messages.
     */
    private void logError(String message) {
        Platform.runLater(() -> onError.accept("[Terminal] " + message));
    }
}
