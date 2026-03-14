# Cortex Desktop Application (JavaFX)

Cortex is a JavaFX desktop AI assistant built with Java 25 and Gradle. It supports multi-conversation chat, markdown rendering, inline "ask about selection" threads, code execution, terminal tooling, theming, settings, and export options.

## Current Status

The project is in active development with a working desktop app and integrated AI request pipeline.

Implemented:
- Chat UI with multiple conversations and animated streaming responses
- Markdown-aware message rendering (headings, lists, code blocks)
- Inline follow-up questions on selected response text
- Run/Stop code snippets directly from code blocks
- Integrated terminal panel with dock positions (left/right/top/bottom)
- Preferences and Settings dialogs with AI, runtime, execution, privacy, and appearance options
- About dialog extracted into a separate view component
- Export options: Markdown, text, PDF, Word, screenshot, clipboard copy

## Requirements

- Java 17+ JDK
- Gradle wrapper (already included)
- Valid API credentials for your provider
- WiX Toolset installed on Windows if you want native `.exe` and `.msi` installers

## Configuration (Important)

Cortex resolves `app.properties` in this order:
1. `Cortex/app.properties` (project root, outside `ai-project`)
2. `ai-project/src/main/resources/app.properties`
3. Classpath fallback `/app.properties`

Expected keys:

```properties
past_api=YOUR_REAL_API_KEY
openai_model=YOUR_MODEL_NAME
openai_base_url=YOUR_BASE_URL
```

Example:

```properties
past_api=sk-or-provider-key
openai_model=gpt-4.1-mini
openai_base_url=https://api.openai.com
```

Notes:
- Root `app.properties` is preferred for safer local developer use.
- Saving values in **Settings > AI Model** writes to `ai-project/src/main/resources/app.properties`.
- Environment variables can still override config values.

## Environment Variable Overrides

- `OPENAI_API_KEY` (highest precedence for API key)
- `PAST_API` (alternate key env var)
- `OPENAI_BASE_URL`
- `OPENAI_MODEL`

## Run and Build

From `cortex`:

Run app:

```powershell
.\gradlew.bat run
```

Build the project:

```powershell
.\gradlew.bat clean build
```

Build a Windows `.exe` installer:

```powershell
.\gradlew.bat packageExe
```

Build a Windows `.msi` installer:

```powershell
.\gradlew.bat packageMsi
```

Build both installers:

```powershell
.\gradlew.bat packageInstallers
```

The generated installers are written to `build\installers`.

## Code Execution Languages

Supported runtimes/compilers include:
- C (`gcc`, `clang`, `cl`, `cc`)
- C++ (`g++`, `clang++`, `cl`, `c++`)
- Python
- JavaScript (Node.js)
- Java
- Bash
- PowerShell

Language/runtime availability is detected dynamically at runtime.

## Project Structure

- `src/main/java/com/example/chatbot/controller`: UI controllers
- `src/main/java/com/example/chatbot/service`: AI/config/runtime services
- `src/main/resources/fxml`: JavaFX layouts
- `src/main/resources/css`: styles
- `src/main/resources/config`: language config

## Security Guidance

- Do not commit real API keys.
- Keep secrets in local `Cortex/app.properties` or environment variables.
- `.gitignore` is configured to ignore local `app.properties` paths used for API secrets.

## Troubleshooting

- `HTTP 401`: API key/provider mismatch or invalid key.
- `API key is missing`: add `past_api` to `Cortex/app.properties` or configure via Settings > AI Model.
- Runtime not found for code execution: install the required compiler/runtime or set custom runtime paths in settings.
