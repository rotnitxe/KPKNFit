# Repository Structure

This repository contains the KPKN Fit product: a native Kotlin Android application (`android-native/`), an iOS port in progress (`ios-native/`), a Python backend (`backend/`), and the datasets, design assets, and scripts that support them. All legacy PWA, React, and Capacitor code has been removed.

## Top-Level Directories

*   📂 **`android-native/`** — The core Android application, written in native Kotlin (Jetpack Compose, Room, Coroutines/Flows, Clean Architecture). **This is the main product.** See the deep dive below.
*   📂 **`ios-native/`** — Native iOS port (Swift/SwiftUI) under `ios-native/KPKNFit/`, plus `watch_and_deploy.sh` for device deployment. See `docs/IOS_DEVELOPMENT_PLAN.md`.
*   📂 **`backend/`** — Python FastAPI backend: `main.py` entrypoint, `routers/` (HTTP endpoints), `engines/` (server-side logic), `models/` (pydantic schemas), `supabase_migration.sql`, and `requirements.txt`. The app is offline-first; the backend is a secondary sync/AI layer.
*   📂 **`data/`** — Source-of-truth static datasets (TypeScript/JSON): exercise databases and expansions, food databases (USDA, OpenFoodFacts, Chilean foods), muscle/joint/tendon catalogs, split and program templates. These are compiled into the app's bundled assets by scripts.
*   📂 **`design_assets/`** — Logos, icons, illustrations, and backgrounds (PNG/SVG).
*   📂 **`docs/`** — Living documentation:
    *   `docs/android/` — Focused Android skill guides (Compose canvas, Room offline, ViewModel flows, native hardware, local AI).
    *   `docs/archive/` — Historical reports and the old React Native migration notes.
    *   `docs/parity/` — Android↔iOS parity tracking.
*   📂 **`scripts/`** — Helper scripts (Python and Node.js) for dataset normalization, exercise expansion, voice/sound generation, and database export (e.g. `build_usda_offline_db.py`, `expand_exercise_variants.py`).

## Core Config Files at Root

*   📄 **`CLAUDE.md`** — Cheat sheet of build, test, and style guidelines for AI assistants.
*   📄 **`README.md`** — Public project profile and feature list.
*   📄 **`.env` / `.env.example`** — Environment variables for API keys and endpoints (never commit real keys).
*   📄 **`convert_dataset.py`**, **`translate_concepts.rb`**, **`add_to_xcode.rb`** — Root-level data/Xcode utilities.

---

## 📱 `android-native/` Deep Dive

Gradle project named `KPKN` with a single module `:app`. All Android commands run from this directory (`./gradlew assembleDebug`, `./gradlew test`, ...).

### Gradle & Build Files

| File | Purpose |
| :--- | :--- |
| `settings.gradle.kts` | Project name (`KPKN`), includes `:app`. |
| `build.gradle.kts` (root) | Plugin declarations. |
| `app/build.gradle.kts` | App config: `compileSdk 36`, `minSdk 24`, `targetSdk 35`, flavors `base` / `health` (Health Connect), release signing config, dependencies. |
| `gradle/libs.versions.toml` | Version catalog: Kotlin 2.2.10, Compose BOM 2025.07.00, Room 2.7.1, Navigation 2.8.9, Glance 1.1.1, Haze, Coil, LeakCanary (debug). |
| `local.properties` | Local SDK path (not committed). |

### Source Layout — `app/src/main/java/com/example/kpkn/`

```
com.example.kpkn/
├── KpknApplication.kt          # Application class (StrictMode, bootstrapping)
├── MainActivity.kt             # Single-activity host: NavHost, bottom navigation,
│                               #   manual constructor DI for repositories/ViewModels
│
├── data/                       # ── DATA LAYER (Android-aware) ──
│   ├── db/                     # Room: KpknDatabase (v19), Entities, WikiLabEntities,
│   │                           #   PerformanceRange/Snapshot entities, Daos, WikiLabDao,
│   │                           #   DatabaseBackupHelper (full JSON export/import)
│   ├── models/                 # Serializable domain models (Program, Session, WorkoutLog,
│   │                           #   AugeModels, NutritionModels, WorkoutV2Models, catalogs)
│   ├── repository/             # Single source of truth: ProgramRepository, AugeRepository,
│   │                           #   NutritionRepository, WikiLabRepository, CompetitionRepository,
│   │                           #   SessionTemplateRepository, CustomExerciseRepository,
│   │                           #   LearnRepository, AugeMetricsRepository
│   ├── exercises/              # ExerciseDatabase loader (bundled JSON catalog)
│   ├── food/                   # FoodDatabase, FoodImporter (USDA/OFF prepopulation),
│   │                           #   FoodDescriptionParser
│   ├── voice/                  # VoiceNutritionRecognizer (speech → food log)
│   ├── remote/                 # ExternalAiService (Gemini/OpenAI/DeepSeek fallback) + DTOs
│   ├── programs/               # ProgramTemplates (bundled program presets)
│   ├── sessions/               # SessionTemplate models + bundled templates
│   ├── splits/                 # SplitTemplates (weekly split presets)
│   ├── protocols/              # ProtocolLibrary (training protocols)
│   ├── learn/                  # LearnContent / LearnData (courses, quizzes, badges)
│   ├── wikilab/                # TrainingConceptsData (concept encyclopedia)
│   └── WikiLabPrepopulate.kt   # First-launch anatomy JSON → Room import
│
├── domain/                     # ── DOMAIN LAYER (pure Kotlin, no android.*) ──
│   ├── auge/                   # AUGE recovery system: AugeRecoveryEngine, AugeFatigueEngine,
│   │                           #   AugeTtcEngine, InterferenceEngine, ExerciseReadinessEngine,
│   │                           #   AugeAdaptiveEngine, classifiers, discomfort aggregation
│   ├── nutrition/              # FoodParser, SmartFoodResolver, TextNormalizer, PhoneticEs,
│   │                           #   SubjectivePortionEngine, MacroCalculator, cooking factors
│   ├── training/               # LoopEngine (progression), VolumeCalculator,
│   │                           #   ProgramCalendar/Analytics engines, SplitApplicationEngine
│   ├── workout/                # SupersetRules, WorkoutContextRecurrenceEngine,
│   │                           #   WorkoutPerformanceHomologationEngine
│   ├── exercises/              # ExerciseIdentity, ExerciseMatchEngine, variant indexing,
│   │                           #   anatomy insights, filters
│   ├── sessionassistant/       # SessionAssistantEngine (time optimization suggestions)
│   ├── biomechanics/           # BiomechanicsEngine (levers, anthropometrics)
│   ├── calculations/           # PlateCalculator, shared math helpers
│   ├── energy/                 # TrainingEnergyEngine
│   ├── performance/            # PerformanceRangeCalculator
│   └── templates/              # SessionTemplateEngine + catalog policy
│
├── screens/                    # ── PRESENTATION LAYER (Compose, by feature) ──
│   ├── home/                   # Dashboard: HomeScreen + HomeViewModel + Rings/Header/
│   │                           #   Session/Cards/Corners/WikiLab sections
│   ├── workout/                # Live session: WorkoutScreen/ViewModel, set pager, rest
│   │                           #   overlays, plate visualizer, voice UI, + components/
│   ├── nutrition/              # NutritionScreen/ViewModel, MealHistoryScreen,
│   │                           #   BodyProgressScreen + components/ (logger drawer, wizard)
│   ├── programs/               # ProgramsScreen/ViewModel (program list)
│   ├── programdetail/          # ProgramDetailScreen/ViewModel (microcycle calendar)
│   ├── sessioneditor/          # SessionEditorScreen/ViewModel + rules engine + components/
│   ├── settings/               # SettingsScreen hub + General/Profile/Nutrition/Training/
│   │                           #   Auge/Notifications/Data subscreens + SettingsViewModel
│   ├── auge/                   # ReadinessSheet, PostExerciseSheet, PostSessionSheet, AugeViewModel
│   ├── wikilab/                # Anatomy explorer: muscles, joints, tendons, patterns,
│   │                           #   biomechanics, exercise detail, custom exercise creator
│   ├── learn/                  # LearnHome/Course/Reader/Quiz/Badge screens
│   ├── profile/                # ProfileScreen
│   └── competitions/           # CompetitionScreen
│
├── navigation/                 # Navigation.kt (KpknRoute sealed routes + NavHost),
│                               #   DeepLinkRouter, KpknDeepLinks, NavigationBus
├── services/                   # ── BACKGROUND / HARDWARE ──
│   ├── workout/                # WorkoutRestForegroundService, WorkoutContinuousVoiceEngine,
│   │                           #   WorkoutVoiceCommandParser/Controller, WorkoutTtsManager,
│   │                           #   WorkoutReminderManager + BootReceiver, rest alerts,
│   │                           #   SystemAudioHelper (audio focus ducking)
│   ├── nutrition/              # NutritionNotificationManager (+ alert receiver)
│   └── competition/            # CompetitionReminderManager
├── telemetry/                  # KpknTelemetry, TelemetryEvents, TelemetryHelper
├── widgets/                    # NutritionQuickActionWidget (Glance AppWidget)
└── ui/                         # theme/ (Color, Theme, Type — neon dark design system),
                                #   components/ (shared composables, icons), locale/ (LocaleManager)
```

### Bundled Assets — `app/src/main/assets/`

| Asset | Size | Purpose |
| :--- | :--- | :--- |
| `exercise_database.json` | ~1 MB | Exercise catalog with muscle mappings and biomechanics. |
| `exercise_id_aliases.json` | ~28 KB | Canonical exercise ID alias resolution. |
| `food_data/` | ~80 MB | USDA/OFF CSVs imported into Room on first launch, plus compiled offline semantic index `dataset_knowledge.bin` (~1.3 MB gzip, 19,405 examples) used by `SemanticPortionRetriever`. |
| `wikilab/` | ~104 KB | `muscles.json`, `joints.json`, `tendons.json`, `movement_patterns.json`, `kinetic_chains.json` (anatomy catalog). |

### Tests — `app/src/test/` (JUnit 4 + Robolectric + coroutines-test)

Mirrors the main package tree. Strongest coverage in `domain/` (AUGE engines, nutrition parsers, training engines), plus `data/`, `screens/` (ViewModel and rules tests), `services/`, `navigation/`, and `telemetry/`. Run with `./gradlew test`.

### Miscellaneous

*   `qa-screenshots/` — Device screenshots from QA passes.
*   `scripts/` — Android-specific helper scripts (deploy, sounds, cleanup).
*   `build_last_compile*.txt`, `hs_err_pid*.log` — Local build artifacts/logs (safe to ignore).
