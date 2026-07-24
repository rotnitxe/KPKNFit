# KPKN Fit Guidelines

This file serves as a reference for AI coding agents working on the KPKN Fit native Kotlin project.

---

## 🛠️ Build & Test Commands

All Android build and test commands must be run from the `android-native/` subdirectory:

*   **Clean Build:**
    ```bash
    cd android-native && ./gradlew clean
    ```
*   **Compile Debug Build:**
    ```bash
    cd android-native && ./gradlew assembleDebug
    ```
*   **Run Unit Tests:**
    ```bash
    cd android-native && ./gradlew test
    ```
*   **Install App on Device/Emulator:**
    ```bash
    cd android-native && ./gradlew installDebug
    ```

---

## 💻 Kotlin Coding Style & Standards

*   **Naming Conventions:**
    *   **Classes, Interfaces, Objects:** `PascalCase` (e.g. `AugeFatigueEngine`, `FoodParser`).
    *   **Variables, Functions, Properties:** `camelCase` (e.g. `calculateSystemicFatigue()`, `userProfile`).
    *   **Packages:** Lowercase, dot-separated (e.g. `com.example.kpkn.data.db`).
*   **Code Guidelines:**
    *   **Immutability:** Use `val` for variables and `data class` properties unless mutation is strictly necessary (`var`).
    *   **State Management:** Always expose read-only state from ViewModels using `StateFlow` (e.g., via `asStateFlow()`). Do not expose mutable `MutableStateFlow` directly.
    *   **Clean Architecture:** Keep `domain/` free of Android libraries (`android.*` imports). All database and file system handling belongs in `data/`.
    *   **Coroutines:** Run heavy operations on `Dispatchers.IO` (database, parses, I/O) and update UI state on `Dispatchers.Main`.
    *   **Compose Guidelines:** Use descriptive state-hoisting, and keep composables stateless where possible.

---

## 🗺️ Key Locations (Quick Reference)

All app code lives in `android-native/app/src/main/java/com/example/kpkn/`:

*   **Entry points:** `KpknApplication.kt`, `MainActivity.kt` (NavHost + manual DI).
*   **Data layer:** `data/db/` (Room, `KpknDatabase` **v20**), `data/repository/`, `data/models/`, plus loaders (`data/exercises/`, `data/food/`, `data/wikilab/`...).
*   **Domain engines (pure Kotlin):** `domain/auge/`, `domain/nutrition/`, `domain/training/`, `domain/workout/`, `domain/exercises/`, `domain/sessionassistant/`, `domain/biomechanics/`, `domain/calculations/`.
*   **UI screens:** `screens/<feature>/` (`home`, `workout`, `nutrition`, `programs`, `programdetail`, `sessioneditor`, `settings`, `auge`, `wikilab`, `learn`, `profile`, `competitions`).
*   **Navigation:** `navigation/Navigation.kt` (all `KpknRoute` routes), deep links in `navigation/DeepLinkRouter.kt`.
*   **Background/hardware:** `services/workout/` (voice engine, TTS, foreground service, reminders), `services/nutrition/`, `services/competition/`, `widgets/`.
*   **Theme:** `ui/theme/` (pitch-black + neon palette), shared composables in `ui/components/`.
*   **Bundled assets:** `android-native/app/src/main/assets/` (exercise JSONs, ~80 MB food CSVs, wikilab anatomy JSONs).
*   **Unit tests:** `android-native/app/src/test/` (mirrors main packages; domain-heavy).
*   **Build flavors:** `base` (minSdk 24) and `health` (minSdk 26, adds Health Connect) — e.g. `./gradlew assembleBaseDebug`.

---

## 📂 Architecture Guides

*   See [REPO_STRUCTURE.md](docs/REPO_STRUCTURE.md) for directory organization (full tree, assets, tests).
*   See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for general layers and logic overview.
*   See [ANDROID_ARCHITECTURE_MAP.md](docs/ANDROID_ARCHITECTURE_MAP.md) for a comprehensive technical mapping of all systems and databases for parity reference.
*   See [ANDROID_UI_SCREENS_MAP.md](docs/ANDROID_UI_SCREENS_MAP.md) for detailed mappings of Jetpack Compose screens, routes, ViewModels, and UI layouts.
*   See [IOS_DEVELOPMENT_PLAN.md](docs/IOS_DEVELOPMENT_PLAN.md) for the 5-phase execution strategy to port the app to Swift/iOS.
