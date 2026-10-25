<div align="center">
  <img src="design_assets/KPKN-LOGO-OFICIAL-black.svg" alt="KPKN Fit Logo" width="200"/>
  <h1>KPKN Fit</h1>
  <p>Native Kotlin Mobile Application for Training Records, Program Protocols, and Nutrition Tracking</p>
</div>

---

## Core Features

### 🏋️‍♂️ Smart Workout & Training Assistant
*   **Active Workout Tracking:** Record performed sessions, results, explicit effort and optional context locally.
*   **Training Evidence:** Sport-specific recorded values with units, provenance, history windows and strict comparisons. Missing data does not imply rest. No recovery battery or injury prediction.
*   **Superset & Drop-set Management:** Advanced rules for workout optimization and structure templates.
*   **Workout Volume & Biomechanics Insights:** Built-in calculators for plate layout, total training volume, and movement pattern tracking.
*   **Session Assistant:** Automated timing and rest alerts with foreground service notifications.

### 🎙️ Continuous Voice Control & Assistant
*   **Hands-free Logging:** Control training sessions and nutrition logs through dedicated offline voice commands.
*   **Voice Command Parsing:** Real-time semantic voice recognition to parse inputs without distracting you from your workouts.

### 🥗 Advanced Nutrition & Macro Tracking
*   **Smart Food Resolver:** Context-aware and semantic parsing engine for complex food description inputs.
*   **Local Food Databases:** Integrated USDA foundation database and custom offline catalogs for instant access.
*   **Macro Calculations:** Automatic determination of proteins, fats, carbs, and calories based on subjective portion sizing.
*   **Home Screen Widget:** Android widgets for quick macro logging.

### ⚙️ Local-First & High Performance
*   **Offline Databases:** Powered by SQLite/Room database for offline-first performance.
*   **Settings & Customization:** Dark/Light theme personalization and persistent user configurations.

## Private post-Rings implementation

The approved scope and preservation contract are in [the implementation plan](docs/post-rings/cloud-transfer/PLAN_COMPLETO.md).
The wizard follows Profile → Training → optional Nutrition → Review. Program changes use
explicit protocol proposals and confirmation. Historical original declarations remain recoverable;
retired derived states are removed by migrations/import boundaries. See
[the research dossier](docs/research/training-evidence/DOSSIER.md) for verified limits and
[the catalog retirement process](docs/post-rings/CATALOG_RETIREMENT.md).

Cloud validation is recorded separately from pending macOS and physical-device gates.
This branch is private work in progress and has not been published to the public origin.
