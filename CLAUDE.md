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

## 📂 Architecture Guides

*   See [REPO_STRUCTURE.md](docs/REPO_STRUCTURE.md) for directory organization.
*   See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for layers and logic overview.
