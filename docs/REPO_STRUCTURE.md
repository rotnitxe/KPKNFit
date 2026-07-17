# Repository Structure

This repository is dedicated entirely to the native Kotlin Android application (`android-native/`) and its supporting backend services. All legacy PWA, React, and Capacitor code has been removed to keep the codebase focused, clean, and professional.

## Top-Level Directories

*   📂 **`android-native/`**
    The core Android mobile application, written in native Kotlin. Built using Jetpack Compose, Room Database, and Coroutines/Flows following Clean Architecture principles.
*   📂 **`backend/`**
    Backend server configurations, schema management, and shared AI endpoints.
*   📂 **`data/`**
    Static datasets and databases (e.g., USDA food database JSON files, exercise libraries) used for pre-populating local app databases.
*   📂 **`design_assets/`**
    Logos, icons, illustrations, and backgrounds in PNG and SVG formats.
*   📂 **`docs/`**
    Living documentation, architectural guides, and past development reports.
*   📂 **`scripts/`**
    Helper scripts (Python and Node.js) for dataset normalization, voice generation, and database exports.

## Core Config Files at Root

*   📄 **`CLAUDE.md`**: Cheat sheet of build, test, and style guidelines for AI assistants.
*   📄 **`README.md`**: Main public project profile and list of features.
*   📄 **`.env` / `.env.example`**: Environment variables for API keys and endpoint paths.
*   📄 **`.gitignore`**: Git path exclusions.
