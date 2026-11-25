# Aura Agent — Gemini-Inspired Android AI Agent

An open-source Android AI Agent built with **Kotlin**, **Jetpack Compose**, and **Material 3**, inspired by the Google Gemini app design, with full support for **Gems**, **Agent Skills (`SKILL.md` + Function Calling Tools + HTTP Webhooks)**, and **Multi-API Providers**.

## Features
- **Gemini-Inspired Material 3 UI**: Deep charcoal `#131314` dark theme (plus light mode), signature sparkle accents, floating pill composer, collapsible navigation drawer, RTL/Persian support, and smooth SSE streaming.
- **Gems (Custom Personas)**: Create, edit, and launch custom Gems with dedicated system instructions, accent colors, and default tools.
- **Skills & Agentic Tool Loop**:
  - **Built-in Tools**: `http_fetch` (REST/web fetch), `calculator`, `device_time`, and `memory_notes` (persistent local notes).
  - **Prompt Skills (`SKILL.md`)**: Paste or import Markdown skills with YAML frontmatter (`name`, `description`).
  - **Custom HTTP Webhook Tools**: Connect your own REST APIs (GET/POST) as callable agent tools.
- **Multi-API Support**:
  - Google Gemini (native REST + SSE streaming + function calling)
  - OpenAI (`gpt-4o`, `o3-mini`, etc.)
  - Anthropic Claude (`claude-3-7-sonnet`, etc.)
  - OpenRouter (200+ models), DeepSeek, Groq, Ollama/Local servers, and any custom OpenAI-compatible endpoint.

## Auto-Build APK on GitHub Actions (ساخت خودکار APK با گیت‌هاب)

This repository includes `.github/workflows/build-apk.yml` which automatically compiles `app-debug.apk` on every push to `main`:

```bash
git init
git add .
git commit -m "Initial commit: Aura Agent Android app"
git branch -M main
git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
git push -u origin main
```

Then open **Actions → Build Android APK → Artifacts → `AuraAgent-debug-apk`** to download the compiled APK and install it on your Android device.
