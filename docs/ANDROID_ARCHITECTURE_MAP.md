# Technical Mapping: KPKN Fit Android Architecture (For iOS Porting)

**Rama privada POST-RINGS · 24-oct-2026:** esquema efectivo Room 34, JSON backup 7, iOS SQLite 23. El estado VT de publicación del párrafo siguiente se conserva; no se fusiona ni publica esta rama. Plan y límites de validación: [post-rings/VALIDATION_STATUS.md](post-rings/VALIDATION_STATUS.md).

**Estado VT vigente · 24-oct-2026:** unión actual motor10/Room30. Android release permanece equivalente al APK oficial52; pruebas físicas únicamente en una KPKN Dev estable (`com.example.kpkn.dbg`, firma debug compatible), aparte de la personal. Rutas/worker/sidecars siguen vigentes. Recorrido funcional original y privacidad acotada documentados en `video-technique/QA_FASE5_RESULTADOS_20261023.md`; ≈m/s y1RMbeta sin referencia externa. No nueva entrega ni FFmaster hasta petición humana.

This document provides a comprehensive technical mapping of the native Android Kotlin codebase of KPKN Fit. It outlines every system, database table, domain logic engine, screen, and background service to ensure a 100% parity port to iOS (using SwiftUI, Swift Concurrency, SwiftData/CoreData, etc.).

> Source root for everything below: `android-native/app/src/main/java/com/example/kpkn/`
> For the plain directory tree, see [REPO_STRUCTURE.md](REPO_STRUCTURE.md).

---

## 🗂️ 1. General Architecture Stack

*   **Language:** Kotlin 2.2.10 (native Android)
*   **UI Framework:** Jetpack Compose (BOM 2025.07.00, Material 3) + Haze (glassmorphism)
*   **Database:** Room 2.7.1 (SQLite) with FTS4 full-text search — database **version 34**
*   **Concurrency:** Kotlin Coroutines & Flows (reactive UI updates)
*   **Dependency Injection:** Manual constructor injection orchestrated in `MainActivity.kt` (no Hilt/Dagger overhead)
*   **Build:** Single module `:app`; product flavors `base` (minSdk 24) and `health` (minSdk 26, adds Health Connect); `compileSdk 36`, `targetSdk 35`
*   **Extras:** DataStore Preferences, Glance AppWidget, Coil, Kotlinx Serialization, LeakCanary (debug only)
*   **Performance Monitoring:** Android `StrictMode` enabled in debug builds to catch main-thread disk I/O and SQLite leaks

---

## 🗄️ 2. Database Schema & Data Persistence

KPKN Fit uses a **local-first (offline-first)** data architecture. The Room database is defined in `data/db/KpknDatabase.kt` (**version 29**, `exportSchema = true`). Most complex objects are serialized to JSON strings using Kotlinx Serialization and stored directly in a `data` text column. Recetas de programa (`TrainingPlanRecipe`), TM/`PowerliftingProfile` y `autoregulationMode` viven en el JSON de `ProgramEntity` (sin bump de columnas). Unified workout photos/videos live in `workout_media` (`WorkoutMediaEntity`) plus private files under `filesDir/workout_media`.

### 2.1 SQLite Table Definitions

**Core tables (`data/db/Entities.kt`):**

| Table Name | Entity Class | Primary Key | Indices | Serialization Details |
| :--- | :--- | :--- | :--- | :--- |
| `programs` | `ProgramEntity` | `id` (String UUID) | None | Full `Program` struct serialized to JSON string in `data`. |
| `workout_logs` | `WorkoutLogEntity` | `id` (String UUID) | `programId`, `date`, `sessionId` | Serialized `WorkoutLog` JSON string in `data`. |
| `competition_records` | `CompetitionRecordEntity` | `id` (String UUID) | `eventDate`, `status`, `sportType`, `plannedSessionId` | Serialized `CompetitionRecord` JSON string in `data`. |
| `settings` | `SettingsEntity` | `rowId` (Int, default 1) | None | Serialized `Settings` JSON string in `data`. |
| `active_program` | `ActiveProgramEntity` | `rowId` (Int, default 1) | None | Serialized `ActiveProgramState` JSON string in `data`. |
| `ongoing_workout` | `OngoingWorkoutEntity` | `rowId` (Int, default 1) | None | Serialized `OngoingWorkoutState` JSON string in `data` (tracks running session state in case of app crashes). |
| `workout_context_performance` | `WorkoutContextPerformanceEntity` | `contextKey` (String) | None | Serialized `ContextPerformanceStateV2` JSON string. |
| `workout_global_performance` | `WorkoutGlobalPerformanceEntity` | `globalKey` (String) | None | Serialized `GlobalPerformanceStateV3` JSON string. |
| `workout_context_profiles` | `WorkoutContextProfileEntity` | `id` (String) | `exerciseKey`, `lastUsedAt` | Serialized `WorkoutContextProfile` JSON string. |
| `workout_replacement_decisions` | `WorkoutReplacementDecisionEntity` | `id` (String) | None | Serialized `ExerciseReplacementDecisionV2` JSON string. |
| `workout_media` | `WorkoutMediaEntity` | `id` (String) | `workoutLogId`, `canonicalExerciseId`, `createdAtMs`, `isPr` | Unified session photos/videos (Room v26). File bytes stay in `filesDir/workout_media`; `WorkoutLog.exercisePhotos`/`sessionPhotos` remain read-only JSON. |
| `workout_media_session_associations` | `WorkoutMediaSessionAssociationEntity` | `sessionKey` (String) | None | Durable session-to-workout-log association added in Room v28. |
| `video_analysis` | `VideoAnalysisEntity` | `mediaId` (String) | `status`, `exerciseKey` + `recordedAtMs` | Reconstructible technique CACHE (v29); authoritative report and traces are private sidecars. |
| `training_observations` | `TrainingObservationEntity` | `sourceIdentity` | None | Original historical declarations, date, scale and provenance; no inferred results. |
| `nutrition_logs` | `NutritionLogEntity` | `id` (String UUID) | `date` | Serialized `NutritionLog` JSON string. |
| `nutrition_plans` | `NutritionPlanEntity` | `id` (String UUID) | None | Serialized `NutritionPlan` JSON. |
| `nutrition_active_state` | `NutritionActiveStateEntity` | `rowId` (Int, default 1) | None | Tracks active plan UUID. |
| `nutrition_pantry` | `PantryItemEntity` | `id` (String) | None | Serialized `PantryItem` JSON. |
| `nutrition_templates` | `MealTemplateEntity` | `id` (String UUID) | None | Serialized `MealTemplate` JSON. |
| `nutrition_custom_foods` | `CustomFoodEntity` | `id` (String) | `name`, `normalizedName`, `normalizedBrand` | Serialized custom food description and macro details. |
| `session_templates` | `SessionTemplateEntity` | `id` (String) | `sourceType`, `sortOrder`, `createdAt` | Serialized `SessionTemplate` JSON. |
| `custom_exercises` | `CustomExerciseEntity` | `id` (String) | `name` | Serialized `ExerciseMuscleInfo` JSON. |
| `global_foods` | `GlobalFoodEntity` | `foodId` (String) | `name`, `normalizedName`, `normalizedBrand` | Cleaned-up USDA and OpenFoodFacts Chile food data. |
| `global_foods_fts` | `GlobalFoodFtsEntity` | Virtual FTS4 | None | SQLite full-text search content entity pointing to `global_foods`. |
| `learned_resolutions` | `LearnedResolutionEntity` | `id` (String) | `queryKey` (Unique) | Maps raw user voice strings (e.g. "two eggs") to specific food database IDs. |

**WikiLab anatomy tables (`data/db/WikiLabEntities.kt`, DAO in `WikiLabDao.kt`):**

| Table Name | Entity Class | Content |
| :--- | :--- | :--- |
| `muscle_groups` | `MuscleGroupEntity` | Muscle catalog with hierarchy and recovery profiles. |
| `joints` | `JointEntity` | Articular points (shoulder, elbow, knee, hip, ankle, cervical, lumbar). |
| `tendons` | `TendonEntity` | Tendon catalog with recovery status. |
| `movement_patterns` | `MovementPatternEntity` | Biomechanical movement patterns (squat, hinge, push, pull...). |
| `kinetic_chains` | `KineticChainEntity` | Relational chains linking muscles, joints, and tendons. |

**Performance tracking tables (`data/db/PerformanceRangeEntity.kt`, `PerformanceSnapshotEntity.kt` — added in v15):**

| Table Name | Entity Class | Content |
| :--- | :--- | :--- |
| `performance_range` | `PerformanceRangeEntity` | RMS (rep-max spectrum) performance ranges per exercise/context. |
| `performance_snapshot` | `PerformanceSnapshotEntity` | Point-in-time performance snapshots for homologation. |

### 2.2 Repositories (`data/repository/`)

Single source of truth per feature, all backed by Room:

`ProgramRepository`, `NeutralTrainingObservationRepository`, `NutritionRepository`, `WikiLabRepository`, `CompetitionRepository`, `SessionTemplateRepository`, `CustomExerciseRepository`, `LearnRepository`.

### 2.3 Advanced Catalogs & JSON Mobility (`data/models/` & `data/db/`)

*   **`DiscomfortCatalog.kt` & `MobilityExerciseCatalog.kt`:** Catálogos de molestias y movilidad; el feedback muestra declaraciones previas, sin inferir causas, lesión o protección.
*   **Models:** `Program.kt`, `Session.kt`, `WorkoutLog.kt`, `Settings.kt`, `RecordedWellbeingModels.kt`, `NutritionModels.kt`, `CompetitionModels.kt`, `WorkoutV2Models.kt`, `EnergyModels.kt`, `ExerciseMuscleInfo.kt`.
*   **Respaldos:** `DatabaseBackupHelper.kt` valida y restaura snapshots SQLite transaccionalmente; `SettingsJsonBackup.kt` importa JSON heredado v1–v6 hacia v7 mediante extracción raw de originales. El respaldo externo no se modifica.

### 2.4 Static Database Assets (Pre-populated on App Launch)

Located in `android-native/app/src/main/assets/`:

1.  **`exercise_catalog_v2.json`:** The sole approved exercise runtime asset. It contains 228 parent/specialty definitions, 553 explicit configurations, exact catalog identities, rich muscle/biomechanics/programming metadata sin coeficientes de fatiga, and hierarchical chip axes. It is loaded by `data/exercises/ExerciseDatabase.kt` and validated against the approved catalog revision/hash. The former `exercise_database.json` and `exercise_id_aliases.json` are not runtime fallbacks; legacy copies exist only as curation evidence under `catalog/exercises/v2/curation/evidence/legacy/`.
2.  **`wikilab/` (`joints.json`, `kinetic_chains.json`, `movement_patterns.json`, `muscles.json`, `tendons.json` — ~104 KB):** Full relational catalog representing the anatomical connectivity of the human body (imported by `data/WikiLabPrepopulate.kt`).
3.  **`food_data/` (`food.csv`, `food_nutrient.csv`, `food_portion.csv`, `food_category.csv`, `measure_unit.csv`, `usda_es_aliases.csv`):** USDA FoodData Central tables read by `FoodImporter`. WP-S11 trimmed the folder to the files the app reads; the 20 unused FDC CSV/XLSX tables now live in `android-native/datasets/usda_fdc_raw/` and are not bundled.
4.  **`food_data/off_chile.csv`:** OpenFoodFacts Chile TSV dataset (~54 MB; the whole `food_data/` folder, with `dataset_knowledge.bin` and a few small JSON files, is ~72 MB).
5.  **`food_data/food_knowledge_v1.json`:** Hand-maintained knowledge asset (~75 KB) with the raw tables of the description pipeline: protected phrases, typos and synonyms, household unit weights, containers, utensil volumes, liquid densities and dish compositions (the dishes `FoodCombinationParser` knows by name, with the proportion and role of each component, plus the recipe notes of the `RECIPE_ESTIMATE` catalog rows) ([contract](contracts/food_knowledge_v1.md), WP-N13). `FoodKnowledgeStore.kt` reads it and `NutritionRepository` installs it once at startup.
6.  **`food_data/manifest.json` (generated):** Not under `src/main/assets/`: the Gradle task `generateFoodDataManifest` (WP-S10) writes it to `android-native/app/build/generated/foodDataManifest/food_data/manifest.json` (sha256 and size per CSV plus one fingerprint) and registers that folder as an assets source, so it ships in every variant. `FoodImporter.expectedFingerprint` reads it at start.

### 2.5 Food Database Import Flow (`data/food/FoodImporter.kt`)

*   **Mechanism:** Parses USDA `foundation_food` rows from `food.csv` + `food_nutrient.csv` (energy IDs `2048`/`2047`/`1008`, plus macro and micronutrient IDs) and OFF Chile rows with declared, coherent nutrition from `off_chile.csv` (column indices: `0`=barcode, `10`=name, `18`=brand, `89`=kcal, `92`=fat, `129`=carbs, `130`=sugar, `146`=fiber, `150`=protein, `156`=sodium).
*   **Text Normalization:** Lowercases, strips accents, removes non-letter characters, and builds search alias arrays (`normalizeSearch` / `encodeAliases`).
*   **Database Pre-population:** The CSVs are parsed off-thread without holding a Room lock (WP-S8); one short transaction then replaces the `global_foods` rows (inserted in chunks of `BATCH_SIZE = 2000`), keeps the per-food usage counters, and an FTS index rebuild follows. The import gate compares the stored fingerprint with `FoodImporter.expectedFingerprint(context)` = `"v<DATA_VERSION>+<manifest sha256>"` (`"v<DATA_VERSION>"` when the manifest is missing), so a CSV that changes triggers one re-import without bumping `DATA_VERSION` (WP-S10).

### 2.6 Additional Data Layer Services

*   **Nutrition parsing:** Android descriptions use the local parser and curated offline dataset. There is no active DeepSeek/remote fallback in the native app; old settings values are decoded only for migration compatibility.
*   **WikiLab Prepopulation (`data/WikiLabPrepopulate.kt`):** Executes on first launch to parse and relate the anatomical JSONs (joints, tendons, muscles) into the local Room database.
*   **Bundled content loaders:** `data/programs/ProgramTemplates.kt`, `data/sessions/SessionTemplates.kt` (+ models), `data/splits/SplitTemplates.kt`, `data/protocols/ProtocolLibrary.kt`, `data/learn/` (courses/quizzes), `data/wikilab/TrainingConceptsData.kt`.
*   **Voice (`data/voice/VoiceNutritionRecognizer.kt`):** Recognizer pipeline that turns dictated meals into parsed food logs.

---

## 🌀 3. Core Business Logic (Domain Layer)

> All engines below are pure Kotlin (no `android.*` imports) and unit-tested in `app/src/test/domain/`.

### 3.1 Entrenamiento y respuesta (`domain/training/insights/`)

`TrainingHistoryProjection` lee `WorkoutLog` y sus documentos deportivos existentes.
`TrainingInsightsEngine` aplica reloj explícito, identidad de fuente, unidades y contexto
conocido; no calcula recuperación ni riesgo. La vista filtra 1/4/12 semanas (4 por defecto)
y compara hasta seis observaciones anteriores en 90 días, con mediana desde tres.
Son opciones descriptivas de presentación, no umbrales clínicos.

`training_observations` conserva originales antes ligados a tablas retiradas: identidad,
fecha, escala, procedencia, notas y precisión. Los importadores raw aíslan compatibilidad;
no se crea otro historial de actividades. La falta de datos permanece desconocida.

Las propuestas ADJUST_TM/ADJUST_XRM pertenecen a protocolos activos y requieren
confirmación. AUTO heredado no autoriza nuevas mutaciones silenciosas. No hay motor,
cache, worker, cuestionario ni semáforo de baterías o de lesión en el producto activo.

El conteo de series lógicas y los modos de carga originales se conservan para registro,
volumen y protocolos; nunca se transforman en drenaje o tiempo hasta recuperación.

### 3.2 Nutrition Engine (`domain/nutrition/`)

The current description contract is [nutrition interpretation revision 2](contracts/nutrition_interpretation_v2.md).
Food identity/attributes, eaten grams and the nutrient profile's reference weight are
separate concerns. A V2 interpretation per resolved mention feeds editing and saving;
an omitted household portion is estimated, while material identity/preparation doubts
require an explicit response. Current text overrides meal templates and personal
priors. See the [September reliability audit](audits/2026-09-nutrition-reliability/README.md)
for regression evidence and validation status.

*   **Macro Calculators (`MacroCalculator.kt`, `NutrientBasis.kt`):** Scale the source nutrient profile using its reference weight and the grams consumed. Distinguish genuine per-serving values from per-100-g values and convert volume references consistently. Atwater-style checks are plausibility diagnostics, not replacements for declared source energy.
*   **Heuristic Parser (`FoodParser.kt` + `data/food/FoodDescriptionParser.kt`):** Takes natural user input (e.g. "platano con una cucharada de avena") and breaks it down:
    *   Checks for cooking methods (boiled, fried, baked) with `COOKING_PATTERNS` in `FoodParser.kt` and applies the matching transformation once (`cookingTransformFor` in `MacroCalculator.kt`; per-method factors in `CookingFactors.kt`).
    *   Extracts quantities and maps household measures ("taza", "unidad", "rebanada", "plato") to consumed grams using `SubjectivePortionEngine.kt` and `HouseholdPortions.kt`, preserving declared raw/cooked state. The semantic retriever does not supply authoritative eaten weights.
    *   Fuzzy match database queries using phonetic index codes in Spanish (`PhoneticEs.kt`), `TextNormalizer.kt`, `FoodIndex.kt`, `SmartFoodResolver.kt`, `FoodCombinationParser.kt`.
    *   Offline semantic dataset: compiled asset `food_data/dataset_knowledge.bin` (19,405 examples) loaded by `DatasetKnowledgeStore.kt` into `SemanticPortionRetriever.kt`; context priors via `ContextDetector.kt`. Fallback: `NutritionHeuristicEstimator.kt`. Dataset never overwrites verified USDA/OFF macros.
    *   Knowledge tables: the protected phrases, typos/synonyms, household unit weights, container words, utensil volumes, densities, dish compositions and recipe notes live in `food_data/food_knowledge_v1.json`, installed once at startup into `FoodKnowledge.kt` (strict `parseFoodKnowledge`); `ProtectedPhrases.kt`, `TextNormalizer.kt`, `HouseholdPortions.kt`, `SubjectivePortionEngine.kt`, the singularizer of `FoodParser.kt` and `FoodCombinationParser.kt` (its dishes; `MassBoundDish.kt` caches which keys are dishes per snapshot) read the installed snapshot, while the recipe notes are read by nothing yet. The Kotlin copy in `FoodKnowledgeDefaults.kt` is the default and the fallback until the parity test lets it go.

### 3.3 Training Engine (`domain/training/`)

*   **`PlanMaterializer.kt`:** Compiles a `TrainingPlanRecipe` (week → day → slot recipes with %TM/RPE/AMRAP) into an executable `Program`. Strict: visible content must pass `SessionCompositionPolicy` (H1–H10 / S / W / block contracts). Deterministic given the same `IdProvider`. Rotates `startDay` from the recipe weekday. Relative percentages are resolved by `PercentResolver` (`PercentResolver.kt`, pure and shared with the composition policy: a `PERCENT_OF_TOP_SET` set hangs from the top set of its own slot or, failing that, the week's top set of the same lift) and `PercentBasis` expresses any load basis as an effective %1RM, so rule H11 measures the load a set really resolves to instead of the raw recipe value, and H11b (Epley: more reps than fit at that %1RM) is a hard rule that no exemption can lift.
*   **`SessionCompositionPolicy.kt` + `CompositionTaxonomy.kt` + `ProgramRecipeValidator.kt`:** Hard rules for session order (T1/T2 compound before isolation/core), pattern/muscle crowding, replacement groups, axial budget, rest floors, and weekly landmarks. Catalog `AISLADO` does **not** turn a compound pattern (hip thrust, squat, hinge, press, row) into an isolation. Third-party protocols may declare matrix 2.2 exemptions; KPKN_NATIVE has none.
*   **`ProgramAutoregulationEngine.kt`:** Propuestas ADJUST_TM/ADJUST_XRM desde reglas activas y resultados registrados; siempre pendientes de confirmación. No consume readiness, estrés calculado ni alertas de carga.
*   **`ProgramProtocolEngine.kt`:** Applies a visible protocol **only** if it has a day-by-day recipe; delegates to `PlanMaterializer`. Hidden/unverified definitions are rejected. Generic synthesis by day label is gone and must not be reintroduced under a third-party name.
*   **`ProgramTemplateEngine.kt` / `ProgramProgressEngine.kt` / `BlockProgressionEngine.kt` / `BlockTransitionEngine.kt`:** Advanced templates, week/block advance, and deload/peak gates. `PeriodizationEngine.prescriptionFor` remains for in-block progression diffs of existing sessions, not as the source of published protocol sessions. `AuthoredProgressionEngine.kt` (pure) holds the method's own progression, which is recipe content and always applies: `CycleIncrement` (scope cycle or block) raises the training max when `ProgramProgressEngine` closes a cycle (`completeCycle`) or a block, and `WeeklyKg` adds kilograms per week when `PlanMaterializer.materializeSet` resolves a set; performance-driven progression (AMRAP, top set, rep max) is never applied directly but travels as an `ADJUST_TM` proposal from `ProgramAutoregulationEngine` that respects the OFF / PROPOSE / AUTO mode, and `ProgressionConsumers` registers which progression rules have a real consumer.
*   **`LoopEngine.kt`:** Routine progression engine (Normal, Warmup, Drop-set, Myo-reps, Failure) and target volume metrics.
*   **`VolumeCalculator.kt`:** Weekly/per-muscle volume analytics.
*   **`ProgramCalendarEngine.kt` / `ProgramAnalyticsEngine.kt` / `ProgramDetailHelpers.kt`:** Microcycle calendar and program analytics.
*   **`SplitApplicationEngine.kt`:** Applies weekly split templates to programs.
*   **Plate Calculator (`domain/calculations/PlateCalculator.kt`):** Solves a linear matching problem to determine exactly which weight plates to put on each side of the barbell for any given target weight (e.g. using 20kg, 15kg, 10kg, 5kg, 2.5kg, 1.25kg plates, assuming a 20kg barbell).

#### Program curation contracts (Android, 2026-10-05)

*  **Editorial and library:** PlanEditorial, PlanEditorialTable and PlanLabels provide 55 records with naming, provenance, kind, rank and listed flags. Five historical entries stay readable but hidden from new listings. findForProgram resolves saved provenance; a new fiche does not migrate Program.sourceRecipe. Recipes and metadata are delivered together in f56cd6e76 (32 routes total), wizard code in 54668bcb8, Q-07 in ede1123d4 and editorial tests in 6fcd47e08.
*  **Evidence and rejections:** AuthoredPlanMaterializer, PlanAdaptationResolver and SetupWizardViewModel share structured apparatus evidence. UNKNOWN confirmation requires an actual panel key. PlanRepairAdvisor verifies repairs before exposing them. A.B6/WIZFIX are committed in 54668bcb8; public UI acceptance is still open.
*  **Generation and recipe contracts:** Recipe C1–C9 are HARD and C10 remains SOFT; shared effective %1RM checks are committed in f56cd6e76. H11/H11b enforce resolved load and Epley feasibility. The final PlanCoverageContractTest FULL run passed with exit 0: 2 tests, 25,650 rows, 13,498 Ready and 12,152 honest rejections, all repaired with zero violations of the seven classes. It used native-cycle-2 and six zero ratchets. This establishes generation C1–C5; complete Base and the TM consumer have separate evidence.
*  **Progress and persistence:** Shared block-entry logic, WeeklyKg cap and author TM proposals are delivered. Saved sourceRecipe and manual overrides do not migrate automatically. The only published BLOCK program, Juggernaut, retains the wave AMRAP through its deload in the same block. A separate custom/legacy taper block remains a static reservation; no published counterexample is claimed.
*  **TM consumer:** Calculations, SessionEditorSessionHelpers, InlineSetRow and AuthoredTmLoadConsumerContractTest are committed in 7c0c27e5b. RM+PERCENT_TM shares TrainingMaxResolver, preserves 174/178.35 kg and prescribed reps, and leaves an absent TM pending rather than inferring it from PR/history. Manual/null/other bases and BW/Assisted/Lastre priority remain intact. The one human-authorized exceptional run 73134 passed exit 0/BUILD SUCCESSFUL in 2 min 42 s: nine suites, 143 tests with no failures, errors or skips, including 13 new tests. C verified the nine archived XML and four source hashes. The single exception is consumed; no further automatic runs or full Base authorization are implied. No GHD key or generation-policy change is introduced.
*  **APK and startup:** Independent assemble-only 51687 passed exit 0/BUILD SUCCESSFUL in 44 s. APK SHA 09AE8062…6ADD8, 553,916,698 bytes, was installed successfully at 03:52:17 UTC. The additional cold launch took 4,883 ms and showed MainActivity and Welcome in XML/PNG at 03:52:49 without a system modal. This proves startup, separately from product QA.
*  **Acceptance and preservation:** Nutrition 5/0 with p95 median 26.2 ms < 50 ms and editorial 71/0 are measured; their structured final exits were not observable. Complete Base remains incomplete after the C1 timeout (exit 124 at 3,600 s), and another JBR21 run is not authorized. The temporary public FULL flow was parked at training experience before configuration or activation. All 28 public cases remain NOT_RUN; no FULL fixture was accepted. Root preserved a private 259-member partial QA backup and restored ORIGINAL with exit 0/PASS at 04:05:22.5294762 UTC: 207 files and WAL with equivalent bytes, app stopped. The new QEMU47564 was stopped with exit 0; config and RAM-backing bytes/metadata remained equal only within that stop, between 04:09:18 and 04:09:27 UTC. Ports and the shared lock are free; food verification received Gradle/ADB5554 priority at 04:10:10 UTC. Target5556 was untouched.
*  **Human authorization:** Codex subagents may edit after Sonnet quota exhaustion. Only the two owner-approved Madcow oracle statements, 174/178.35, were changed in the repo plan; its external original remains untouched. The owner permitted one temporary 5554 recovery from 6,144 to 4,096 MB and one additional launch, with CPU6 and persistent config unchanged and `-no-snapshot` flags. The RAM backing changed across boots: 6,442,516,480 to 4,295,032,832 bytes, SHA D12DE041…6424FA to BBA36E59…263607. Equality before/after each stop is local to that stop, without proof of cross-boot snapshot equality. No prior RAM backing copy is available for restoration; Quick Boot corruption or whole-disk byte state is not established. That launch and the separate nine-filter test exception are consumed. No human pause or further full Base approval is inferred.

This documentary commit completes the F0.3 evidence index. Phase 1 remains open for the 28 public QA cases, complete Base acceptance and the recorded custom/legacy/C10 limits; the new QEMU is stopped; config is unchanged and RAM-backing equality is proven only within each stop. The Athlete fix preserves cardio and minima. PRIMARY volume limits preserve visible totals and weights 1/.5/.4, with glute 17.5 only for own plans. iOS/backend parity stays in its approved later phases. [Curation index](audits/2026-10-programs/README.md).

### 3.4 Workout Engines (`domain/workout/`)

*   **`SupersetRules.kt`:** Validation and rules for superset/drop-set bundling.
*   **`WorkoutContextRecurrenceEngine.kt`:** Detects recurring context patterns for autofill.
*   **`WorkoutPerformanceHomologationEngine.kt`:** Homologates performance across contexts (V2 performance system, backed by `performance_range` / `performance_snapshot` tables).

### 3.5 Exercise Intelligence (`domain/exercises/`)

*   **`ExerciseIdentity.kt` / `ExerciseMatchEngine.kt`:** Canonical identity and fuzzy matching of exercises.
*   **`ExerciseMuscleResolver.kt` / `ExerciseAnatomy.kt`:** Resolves involved muscles and anatomy per exercise.
*   **`VariantGroupIndex.kt` / `VariantPreferenceStore.kt`:** Exercise variant grouping and user preferences.
*   **`ExerciseCatalogInsights.kt` / `ExerciseFilters.kt` / `TechnicalAspectEngine.kt` / creación con metadatos originales, sin inferencia de fatiga.**

### 3.6 Session Assistant Engine (`domain/sessionassistant/SessionAssistantEngine.kt`)

*   **Smart Session Parsing:** Describes authored session contents, volume and planned time without a physiological verdict.
*   **Time Optimization Suggestions:** If a session exceeds the user's target duration, the engine generates actionable suggestions such as:
    *   Reducing rest times between sets.
    *   Converting linear sets into Supersets (pairing antagonistic muscles) or Drop-sets.
    *   Dropping lower-priority auxiliary volume.

### 3.7 Video technique (`domain/biomechanics/`)

*   **Pure engine:** `metrics/TechniqueAnalysisEngine` (engine version 10) prepares timestamped `PoseTrackV2` and `BarTrack`, resolves oriented signals, counts full/partial/failed repetitions and calculates available ROM, phases, TUT, path and velocity metrics. Catalog profiles come from `profiles/AnalysisProfileResolver`, including exercise/equipment-specific MVT restrictions. The four-fold MediaPipe M5 guard selection is integrated; the 98/90 targets remain unmet. Physical QA on the Z Flip5 is partial: the R8 motor-10/FPS QA APK was installed and prior analyses were recomputed from existing pose sidecars without another pose pass or duplicate report. Effective capture was about 30 fps with the conditional 60-fps preference on and off; the 60-fps path is not reported as passed.
*   **Android boundary:** `data/media/video/` decodes frames; `data/media/pose/MediaPipePoseEstimator` selects the full model with CPU fallback. `data/media/technique/TechniqueAnalysisPipeline` writes pose/bar traces and the authoritative `<id>.analysis.json`; `data/work/TechniqueAnalysisScheduler` and `TechniqueAnalysisWorker` serialize FULL, RECOMPUTE and PLATE_ONLY requests.
*   **Local files and persistence:** `WorkoutMediaStore` keeps captured/imported MP4s in `filesDir/workout_media/yyyy-MM/<id>.mp4`. Beside each video, `TechniqueSidecars` manages `<id>.capture.json`, `<id>.pose2.json.gz`, `<id>.bar.json.gz`, `<id>.analysis.json` and the transient `<id>.pose.part`. Room v29 adds `video_analysis`, a reconstructible cache with `mediaId` foreign key and delete cascade, and catalog definition/configuration IDs on `workout_media`; migration lives in `VideoAnalysisMigration.kt`. M4b uses the existing schema, with no new migration.
*   **Presentation:** `screens/technique/` contains analysis, player, trends, comparison, hub and gallery import. Explicit user requests work even when automatic analysis is disabled. Missing metrics stay absent; absolute velocity requires a calibrated plate and remains approximate until device validation. Technique estimates never update recorded reps automatically.
*   **M4b integrated (`108146025`):** `VideoAnalysisRepository` selects other existing DONE reports for the same `catalogConfigurationId`, or the same exercise when that ID is absent. `PersonalRangeReference` requires engine >= 9, ALTA/MEDIA count confidence, a compatible `countSignal` (and view where its unit requires one), and at least two valid full amplitudes; it passes their median as a PERSONAL range reference to the worker/pipeline, including RECOMPUTE. Partial counts have separate confidence and zone: ALTA names a common stretched/shortened zone, MEDIA marks the count approximate, and BAJA offers no partial count. The analysis summary and rep bars show available zone and approximate percentage of a full range. The live chip, rest line and Usar/Corregir actions share this confidence rule; BAJA must preserve manually entered partial reps.
*   **Live workout:** `screens/workout/WorkoutVideoAnalysisController` resolves the latest completed analysis for each exact set/side and publishes a read-only map through `WorkoutViewModel`. Active cards apply estimates to their local draft only. Completed cards and rest overlays use `CompletedSetPatch` through the shared voice mutation gate; the same candidate is persisted in Room before UI publication, including invalidation of the corrected draft and its bilateral fallback. Other sides, timers, undo and stored RM/PR remain intact.
*   **Capture lifecycle:** `screens/workout/technique/TechniqueRecorderScreen` hosts the full-screen CameraX recorder. `WorkoutMediaCaptureController` reserves a private destination and journal, writes capture metadata at Finalize, then registers the video so analysis can start. Preview gravity remains available for framing; `TechniqueCaptureMetadataInterval` admits samples from the actual recording Start until stop/finalize/cancellation and freezes them before file IO. A delayed Start cannot reopen a closed attempt.
*   **Capture identity:** `WorkoutMediaCaptureRequestPolicy` preserves catalog fallbacks and normalizes side against the exercise before the visual-mode callback and initial capture journal, then applies the same policy at Finalize. Recovery therefore retains the configuration and a bilateral capture does not inherit the card's selected left side.
*   **Privacy evidence boundary:** A static trace of the recorder, worker, pipeline and sidecar writes shows local file/Room I/O and no video-upload or network call in that path. `AndroidManifest.xml` disables datatransport's `JobInfoSchedulerService` and `AlarmManagerSchedulerBroadcastReceiver`; `proguard-rules.pro` keeps MediaPipe/protobuf and Flogger call boundaries for R8. The user's manual ZIP export is a separate path and includes the media tree with its sidecars; individual export from a video viewer is a different action and no individual `capture.json` export was observed. No statement here promises that a video can never leave the phone.
    On 23-oct, a bounded physical-device measurement on the installed QA APK (`.dbg`, SHA-256 `0afd5a5af80d9249936a5c8222a626b85e14ee8a76d858381d760c4a36b21fc4`) bracketed a MediaPipe FULL pass with two complete `mAppUidStatsMap` reads. The app UID key was absent in both healthy maps (`mAppUidStatsMap`, `mStatsMapA`, and `mStatsMapB` reported OK), with the same boot, process, and UID. Under the AOSP reader semantics this supports an inference of zero bytes recorded in that accumulator during that window; it is not an explicit byte counter or a universal no-traffic guarantee. The OEM `trafficcontroller --short` output contained only its header, not program state. This was the QA APK, not the signed delivery artifact.


### 3.8 Supporting Domain Packages

*   **`domain/energy/TrainingEnergyEngine.kt`:** Energy system modeling for sessions.
*   **`domain/performance/PerformanceRangeCalculator.kt`:** RMS performance range math.
*   **`domain/templates/SessionTemplateEngine.kt` (+ `SessionTemplateCatalogPolicy.kt`):** Template generation and catalog policy.

---

## 📱 4. UI Presentation Layer & Screens

The app UI is written in Jetpack Compose, organized by feature under `screens/` (each feature typically pairs `FooScreen.kt` with `FooViewModel.kt`, with sub-composables in a local `components/` folder):

```
screens/
├── home/                 # Home + historial observado + sections (Header, History, Session,
│                         #   Cards, Corners, Programs, WikiLab, CreateProgramCard)
├── workout/              # Live Session tracker, rest timer, plate visualizer, voice UI
│                         #   (+ components/: SetExecutionCard, rest overlays, roadmap bar...)
├── nutrition/            # Macro dashboard + MealHistoryScreen + BodyProgressScreen
│                         #   (+ components/: FoodLoggerDrawer, wizard, plan editor)
├── onboarding/           # Typed candidate evaluation, repairs, intent and staged review
├── programs/             # Program list, editorial library and PlanInfoSheet
├── programdetail/        # Microcycle calendar & volume charts
├── sessioneditor/        # Supersets/Target editor + rules engine
├── settings/             # Hub + General/Profile/Nutrition/Training/Notifications/Data
├── wikilab/              # Anatomy explorer: muscles, joints, tendons, patterns,
│                         #   biomechanics, exercise detail, custom exercise creator
├── learn/                # Courses: LearnHome, Course, Reader, Quiz, Badge screens
├── profile/              # User stats
└── competitions/         # Leaderboards
```

### 4.1 The "My Rings" System (Home Screen)

El historial presenta observaciones y autoinformes con sus unidades y contexto.
No existe torre, semáforo o porcentaje de preparación. Las visualizaciones separan
puntos cuyo método/protocolo/contexto no es comparable.

### 4.2 Screens Description

1.  **`home`:** Próxima sesión, actividad reciente, historial con unidades y fuentes, nutrición y programas.
3.  **`workout`:** Registro de objetivos y ejecución, descanso prescrito/manual, voz, contexto breve opcional y cierre con esfuerzo global opcional. Las propuestas de protocolos requieren confirmación.
4.  **`sessioneditor`:** Interface for creating splits, sequencing exercises, and bundling exercises into supersets or drop-sets.
5.  **`nutrition`:** Displays macro bars (Protein, Carbs, Fats) and a log of meals. Includes a quick-add drawer (`FoodLoggerDrawer`), search with FTS4, a **Voice Dictation Bar**, meal history, body progress tracking, and a plan wizard.
6.  **`wikilab`:** Interactive anatomical explorer divided into muscles, joints, tendons, and movement patterns, plus biomechanics visuals and a custom exercise creator.
7.  **`settings`:** Perfil, entrenamiento, nutrición, notificaciones y datos; sin ajustes de baterías ni calibración corporal.
8.  **`learn`:** Guided education: course list, reader, quizzes, and badge rewards.
9.  **`competitions` / `profile`:** Leaderboards and user stats.

### 4.3 Navigation & Routing (`navigation/`)

*   **`Navigation.kt`:** Declares all `KpknRoute` objects and the `NavHost`. Main tabs: `home`, `training`, `nutrition`, `wikilab`. Parameterized routes include `program/{programId}`, `session-editor/{programId}/{sessionId}`, `workout/{programId}/{sessionId}`, `competition/{competitionId}`, `settings/<sub>`, `wikilab/<...>` (muscles/joints/tendons/patterns/chains/exercises/concepts/biomechanics), `nutrition/<wizard|body-progress|meal-history|action/{action}>`, `learn/<...>`, and `settings/health-connect` (health flavor only).
*   **`DeepLinkRouter.kt` / `KpknDeepLinks.kt`:** URL scheme handler for `kpkn://` and `https://kpkn.fit` (declared in the manifest), pushing routes into the `NavHost` (used by notifications and widgets).
*   **`NavigationBus.kt`:** Decoupled in-app navigation events.
*   **Library plan intent:** 54668bcb8 preserves planId/preselectedPlanId and review, using TRAINING_ONLY after completed onboarding/FULL before it. Only blank structures create directly; real recipes use the evaluator and legacy strength-cardio stays read-only. Routing/reachability and Library/PlanInfo guards passed; UI/persistence QA remains NOT_RUN.
*   **Madcow acceptance oracle:** owner-approved initial1RM200/TM174 gives week4 Monday174kg/Fridaytriple178.35kg (102.5%TM, below1RM). Two repo plan statements corrected; external original preserved. The TM consumer passed 143 tests and is committed in 7c0c27e5b; manual product UI acceptance remains pending; no stored-program migration policy changed.

### 4.4 Design System & Theming (`ui/theme/`)

*   **High Contrast Dark Mode:** The app deliberately eschews standard Material colors for an aggressive, premium dark mode (`Color.kt`, `Theme.kt`, `Type.kt`).
*   **Color Palette:** Pitch black backgrounds (`#000000`), accented strictly by neon highlight colors: Neon Yellow (Primary), Neon Cyan (Secondary), and Magenta (Tertiary). Text is high-contrast white. Swift parity should enforce these exact RGB values globally.
*   **Extras:** Haze glassmorphism effects, custom icon set in `ui/components/icons/`, ES/EN strings via `ui/locale/LocaleManager.kt`.

---

## 🎙️ 5. Services & Hardware Integration

### 5.1 Continuous Voice Logging System (`services/workout/`)

*   **`WorkoutContinuousVoiceEngine.kt`:** Local Vosk engine running in the separate `:voice` process. A single actor owns `AudioRecord` (16 kHz) + a Vosk `Recognizer` with **restricted grammars per stage** (folded phrases the recognizer can emit), plus a native one-shot on-device fallback. Auto-recovery of capture with backoff.
*   **IPC (`WorkoutVoiceForegroundService` + `WorkoutRemoteVoiceEngineClient`):** the `:voice` process is a foreground service (type `microphone`) accessed over AIDL; the client keeps monotonic generations, a `DeathRecipient` and a heartbeat.
*   **Command Parsing (`WorkoutVoiceCommandParser.kt` + `WorkoutVoiceController.kt`):** Parses Spanish voice triggers in a live session, e.g.:
    *   "ochenta kilos por ocho RPE 7", "me quedaron dos en reserva", "dándolo todo"
    *   "siguiente ejercicio", "omitir descanso", "añade una serie"
    *   "cuánto drenaje llevo", "qué serie voy", "qué lado falta"
*   **Auto-recovery ("fénix", `WorkoutVoiceRecoveryPolicy` + controller):** if the `:voice` process dies or hangs, the session reconnects with backoff (0/1/2/5/10/30 s) and re-asks a pending confirmation once; the model is never unloaded during a session.
*   **`WorkoutVoiceSessionState.kt` / `WorkoutVoicePermissionHelper.kt` / `PermissionGuideHelper.kt`:** Session state machine and permission UX.

### 5.2 Workout Rest Foreground Service (`services/workout/WorkoutRestForegroundService.kt`)

*   An Android `Service` marked `foregroundServiceType="dataSync"` in the manifest.
*   Keeps the active workout timer running and updates notifications when the app is in the background.
*   Rest alerts (`WorkoutRestAlertManager.kt` + `WorkoutRestAlertRules.kt`) and media-button handling from locked screens (`TimerNotificationActionReceiver`).
*   **`ActiveWorkoutHolder.kt`:** Process-wide holder of the running session.

### 5.3 Audio & TTS Subsystem (`services/workout/`)

*   **`WorkoutTtsManager.kt`:** Uses Android's `TextToSpeech` engine to narrate rest periods, targets ("Log 80 kilos for 8 reps"), and encouragements.
*   **`SystemAudioHelper.kt`:** Manages Audio Focus, ensuring that background music (e.g., Spotify) is "ducked" (volume lowered) when the TTS speaks or when the rest timer alarm rings.

### 5.4 Home Widgets (`widgets/NutritionQuickActionWidget.kt`)

*   Glance-based `AppWidgetProvider` (`NutritionQuickActionWidgetReceiver` in the manifest).
*   Exposes circular progress rings on the Android launcher screen, showing protein and calorie progress at a glance, with quick launch buttons to log water or open voice search.

---

## 📋 6. Auxiliary Systems (Telemetry, Routing, Notifications)

### 6.1 Telemetry & Analytics (`telemetry/`)

*   **`KpknTelemetry.kt` / `TelemetryEvents.kt` / `TelemetryHelper.kt`:** A custom analytics wrapper that records user flows (e.g., workout completion rates, feature usage).

### 6.2 Deep Linking (`navigation/`)

*   See §4.3 — `kpkn://` and `https://kpkn.fit` schemes plus `SEND` text/plain intent handling in `MainActivity`.

### 6.3 Local Alarm & Notification Managers

*   **`services/nutrition/NutritionNotificationManager.kt`:** Schedules meal reminders (`NutritionAlertReceiver`).
*   **`services/workout/WorkoutReminderManager.kt`:** Schedules workout-day reminders (`WorkoutReminderReceiver`).
*   **`services/workout/WorkoutReminderBootReceiver.kt`:** A `BroadcastReceiver` (exported, `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`) that re-registers all alarms if the Android device is rebooted.
*   **`services/competition/CompetitionReminderManager.kt`:** Competition reminders (`CompetitionReminderReceiver`).
*   **`LoopNotificationManager.kt`:** In-session loop notifications.

### 6.4 Manifest-declared Components

*   Application class: `.KpknApplication`; single exported activity: `.MainActivity`.
*   Permissions: `INTERNET`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `FOREGROUND_SERVICE(_DATA_SYNC)`, `WAKE_LOCK`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED`.
*   `FileProvider` (`${applicationId}.fileprovider`) for sharing files (e.g. backups, workout shares via `WorkoutShareService.kt`).
*   `android:largeHeap="true"` (the ~72 MB food dataset import requires it).

---

## 🧪 7. Testing Layout

*   **Unit tests (`app/src/test/`, JUnit 4 + Robolectric + kotlinx-coroutines-test):** Mirrors the main package tree. Heaviest coverage in `domain/` (training evidence/migrations, nutrition parsers, authored training/workout engines), plus `data/` (database load, serialization), `screens/` (ViewModel and rules tests for workout/session editor/program detail/nutrition), `services/workout/`, `navigation/DeepLinkRouterTest.kt`, and `telemetry/`.
*   **Instrumented (`app/src/androidTest/`):** Compose UI test scaffolding (espresso + compose-ui-test).

---

## 🍏 8. Key Parity Guidelines for iOS (Swift)

For the Swift implementation (`ios-native/KPKNFit/`), use the following technology mapping:

| Android Native System (Kotlin) | iOS Native Parity System (Swift) |
| :--- | :--- |
| **Room Database** | **SwiftData** (using `@Model` macro) or **CoreData** (with custom SQLite indexes and FTS5). |
| **FTS4 Virtual Tables** | **FTS5** virtual table in SQLite database via SQLite.swift. |
| **Coroutines & Flows** | **Swift Concurrency** (`Task`, `async/await`) & `AsyncStream` / `Combine`. |
| **SpeechRecognizer** | **Speech framework** (`SFSpeechRecognizer`) with a continuous audio buffer. |
| **Foreground Service** | **Live Activities** & **ActivityKit** (to display workout timer on Lock Screen and Dynamic Island). |
| **AppWidgetProvider (Glance)** | **WidgetKit** (using SwiftUI layouts). |
| **TextToSpeech** | **AVFoundation** (`AVSpeechSynthesizer`). |
| **Jetpack Compose** | **SwiftUI** (using grids, custom canvas paths for Rings). |
| **DataStore Preferences** | **`@AppStorage` / UserDefaults**. |
| **Health Connect (flavor `health`)** | **HealthKit**. |
## Programas y periodización — ampliación local del 25-oct-2026

Esta sección describe la continuación sobre Room 35; no acredita por sí sola build, instalación o QA. El estado y los recibos vigentes están en docs/audits/2026-10-programs-v2/EJECUCION_ACTUAL.md. Los motores retirados no forman parte de este diseño.

`domain/training/ProgramScope.kt` traduce la estructura de ejecución a alcance visible, sin presentar SIMPLE/COMPLEX como nivel del atleta. Prueba: ProgramScopeTest. La UI consume PlanLabels para fichas y cabeceras.

`domain/training/RpeTopSetEngine.kt` consume top sets RPE comparables y produce propuestas de referencia confirmables; no cambia el runtime de objetivos de MFP. Prueba: RpeTopSetEngineTest y resolución por ProgramAutoregulationResolutionTest en Android.

`domain/training/cycle/` contiene RirWave, WeeklyCheckinEngine y WeeklyAdjustmentEngine. La onda pertenece a recetas propias nuevas; una respuesta original identifica ejecución, ciclo y semana de referencia. Se preservan T1/T2, AMRAP, calentamientos, sesiones ejecutadas/iniciadas y días manuales. Ajustes de accesorios y límites elegidos requieren vista previa y confirmación. Pruebas: RirWaveTest, WeeklyCheckinEngineTest, WeeklyAdjustmentEngineTest, WeeklyCheckinJsonTest; persistencia/UI en ProgramDetailViewModelTest y WeeklyCheckinSheetTest.

`domain/training/block/` contiene BlockReviewEngine, NextBlockAdjuster y BlockReviewLifecycle. Describe cobertura y tendencias de configuraciones comparables; no diagnostica recuperación. El wrapper del progreso observa el cierre real, mantiene separados ciclo revisado y ciclo de destino y ofrece continuación también tras el 1RM final. ContinuationProgramFactory prepara otra copia sin borrar la ejecución terminada. Pruebas: BlockReviewEngineTest, NextBlockAdjusterTest, BlockReviewLifecycleTest, BlockReviewJsonTest y ContinuationProgramFactoryTest. Las escrituras pareadas de origen/copia usan una transacción del repositorio antes de activar.

`domain/training/ProgramProgressReport.kt` limita métricas al ciclo y ejecución vigentes. Los originales se distinguen de decisiones derivadas; la cobertura cuenta registros, no ausencia inferida de entrenamiento. Prueba: ProgramProgressReportTest. ProgramDetailViewModel expone el informe en StateFlow y el módulo mínimo muestra detalles desplazables con fuente ampliada.

`domain/training/periodization/BlockVariantRotation.kt` declara variantes por fase; RoutinePeriodizer comprueba material del lugar concreto y conserva la identidad de los principales al recortar accesorios. No traslada referencias de fuerza entre configuraciones. Pruebas: BlockVariantRotationTest, RoutinePeriodizerTest y FidelityRegressionTest. ProgramExerciseSwap permite tres alcances, conserva sesiones ejecutadas y guarda personalización manual sin reescribir sourceRecipe; pruebas ProgramExerciseSwapTest y ExerciseSwapOptionsTest.

`domain/training/planning/PlannedLoadProjector.kt` describe series previstas por músculo y objetivos explícitos de esfuerzo alto. Distingue base TM/1RM y siguiente plantilla de ciclo; no calcula readiness. Prueba: PlannedLoadProjectorTest; módulo UI ProgramPlannedLoadModuleTest.

`domain/training/calibration/` separa CalibrationEvidence (registros observados), PlanningVolumeTargets (límites elegidos), propuestas confirmables de rango y WeeklyCycleEvidence (resumen al cerrar). No deriva técnica, movilidad o MRV fisiológico de respuestas o ausencia de registros. Pruebas: CalibrationEvidenceTest, PlanningVolumeTargetsTest, PlanningRangeProposalsTest y WeeklyCycleEvidenceTest; UI/persistencia en ProgramDetailViewModelTest, VolumeCalibrationOriginalsTest y ProgramPlanningVolumeTargetSheetTest.

### Campos JSON aditivos de Program en esta ola

`domain/training/schedule/MissedSessionPolicy.kt` conserva elecciones explícitas por ejecución/ciclo/semana/sesión. SKIP añade claves separadas, nunca logs; DO_THIS_WEEK conserva requisitos y permite reconciliar un backfill real. SOFT_RETURN protege registros, sesiones iniciadas y personalización manual. `InactivityDetector.kt` describe fechas válidas de registros, sin inferir entrenamiento fuera de KPKN. Pruebas: MissedSessionPolicyTest, MissedSessionJsonTest, InactivityDetectorTest y ProgramProgressCycleCloseTest. ProgramScheduleModule separa revisión de confirmación; repositorio valida la instantánea y la ausencia de sesión en curso.

`domain/competitions/` contiene AttemptPlanner, MeetDayPlan, WeighInPlanner y MeetResultToProfile. Los tres intentos y calentamientos se limitan a SBD compatible; una proyección matemática de peso conserva su procedencia y los resultados sólo actualizan referencias tras revisión/confirmación. Pruebas: CompetitionPlanningTest, CompetitionModelsJsonTest, HomeCompetitionResolverTest y casos Room/rollback en ProgramDetailViewModelTest. ProgramRepository y CompetitionRepository coordinan versiones, locks y transacción pareada programa/record antes de publicar cachés; no se modifica un resultado original. ProgramCompetitionModule abre la ficha existente para registrar resultados, también en otras modalidades.

`domain/season/SeasonAdvisor.kt` describe fase, dirección nutricional confirmada y tendencia de peso observada. No cambia metas ni contabilidad. ProgramRepository lee las tablas de planes/estado nutricional y observaciones corporales confirmadas en la misma Room; un observador de invalidación refresca el StateFlow del VM. Una propuesta semanal vuelve a validar ese contexto dentro de la transacción de escritura del programa. Pruebas: SeasonAdvisorTest, SetupNutritionPreparationTest, WeeklyAdjustmentEngineTest y casos de invalidación/edición concurrente en ProgramDetailViewModelTest. Los límites editoriales no permiten cambiar AMRAP, calentamientos ni sesiones protegidas.

Todos conservan defaults y no requieren tablas ni migración. Los historiales publicados de Room permanecen intactos.

- `programDecisions = emptyList()`: decisiones con origen, motivo, fecha y referencias de semana/bloque.
- `weeklyCheckins = emptyList()`: respuestas originales por ejecución/ciclo/semana, incluidas omisiones.
- `pendingWeeklyCheckin = null`: petición para una ocurrencia vigente y semana de referencia explícita.
- `planningVolumeTargets = emptyList()`: límites de planificación confirmados por grupo.
- `volumeCalibrationNotes = ""`: nota original del cuestionario, sin inferir un rasgo físico.
- `resolvedPlanningRangeProposalIds = emptyList()`: idempotencia de propuestas aceptadas/descartadas.
- `blockReviews = emptyList()`: revisiones descriptivas por ejecución/ciclo/bloque.
- `pendingBlockReview = null`: propuesta revisable para un bloque/ciclo concreto.
- `pendingContinuation = null`: elección tras finalizar una ejecución finita.
- `skippedSessionKeys = emptyList()`: omisiones declaradas por ocurrencia, separadas de registros originales.
- `pendingMissedSessions = null`: petición revisable de sesiones pendientes en una ejecución/ciclo concretos.

VolumeCalibrationProfile añade `notes = ""`; AlgorithmSettings añade `weeklyCheckinEnabled = true`. CompetitionRecord añade `plannedAttempts = emptyList()`, `targetWeightClass = null`, `targetBodyweightKg = null` y `meetResultAppliedAtMs = null`; no hay cambios de tablas. El transporte iOS conserva campos de Program mediante opaqueFields, la preferencia nueva como opcional y la calibración declarada como JSONValue opcional. Los campos de competición son opcionales y sobreviven al helper copy. Las pruebas Swift están preparadas, sin compilación Swift ni QA iOS en este host.
