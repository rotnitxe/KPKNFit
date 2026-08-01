# KPKN Fit Agent Guide

KPKN Fit is a local-first native Android application with an iOS parity port and an optional FastAPI analysis backend. The primary product is `android-native/`.

## Validation

- Run Android commands from `android-native/`.
- Debug build: `gradlew.bat assembleDebug`.
- Unit tests: `gradlew.bat test`.
- Install locally: `gradlew.bat installDebug`.
- Prefer targeted tests before a full build; the repository contains large offline datasets and a bundled Vosk model.

## Architecture Rules

- Use Clean Architecture and MVVM with unidirectional data flow.
- Keep `domain/` pure Kotlin. It must not import `android.*`.
- Put Room, file, network, and platform work in `data/`, `services/`, or presentation boundaries.
- ViewModels expose read-only `StateFlow` using `asStateFlow()`; never expose `MutableStateFlow`.
- Run blocking work on `Dispatchers.IO` and update UI state on `Dispatchers.Main`.
- Use manual constructor injection and follow the existing feature-based package layout.

## Authoritative Locations

- Android source: `android-native/app/src/main/java/com/example/kpkn/`.
- Persistence: `data/db/`, `data/repository/`, and exported schemas under `app/schemas/`.
- Pure engines: `domain/auge/`, `domain/nutrition/`, `domain/training/`, `domain/workout/`, `domain/exercises/`, `domain/biomechanics/`.
- Screens and ViewModels: `screens/<feature>/`.
- Navigation: `navigation/Navigation.kt` and `navigation/DeepLinkRouter.kt`.
- Voice and hardware: `services/workout/`; changes require focused tests and diagnostics.
- Cross-platform behavior: keep Android, iOS, and backend implementations aligned when changing AUGE, nutrition, or recovery logic.

## Project Context

- Read `.opencode/kpkn-map.md` before broad repository searches.
- Durable agent memory lives in `.opencode/memory/MEMORY.md` and `.opencode/memory/USER.md`.
- Architecture references are `docs/ARCHITECTURE.md`, `docs/ANDROID_ARCHITECTURE_MAP.md`, and `docs/ANDROID_UI_SCREENS_MAP.md`.
- The code and exported Room schema are authoritative when documentation disagrees. The current Room database is v20; some older docs still say v19.

## Safety

- Never read, print, or commit `.env` contents, signing credentials, keystores, or MCP tokens.
- Do not regenerate large food, exercise, Vosk, or dataset assets manually. Use the documented scripts.
- Treat `scripts/telegramBot.js` as high-risk remote shell code; do not invoke it unless explicitly requested.
