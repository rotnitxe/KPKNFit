# KPKN Parity Sources Map

Use this map to choose the right anchors before auditing parity. The repo contains overlapping migration histories, so source selection matters.

## Trust order

1. Actual PWA behavior in code
2. Actual `android-native` implementation in code
3. Slice-specific Kotlin migration plans that clearly target `android-native`
4. Historical React Native docs and parity matrices
5. File counts, LOC, or old status summaries

Never reverse this order.

## Historical context vs current target

- PWA root:
  - current behavior oracle for many features
- `android-native`:
  - current Kotlin/Compose migration target
- `apps/mobile`:
  - historical React Native target
- Old parity docs:
  - useful to find candidate gaps
  - not enough to prove current Kotlin coverage

## Area map

### Navigation and routing

- PWA anchors:
  - `routes/navigation.ts`
  - `routes/router.ts`
  - `types.ts`
- Kotlin anchors:
  - `android-native/app/src/main/java/com/example/kpkn/navigation/Navigation.kt`
  - `android-native/app/src/main/java/com/example/kpkn/MainActivity.kt`
- Historical hints:
  - `docs/parity/pwa-rn-master-matrix.md`
- Audit notes:
  - Compare route reachability, arguments, back behavior, and entry points.
  - A similarly named route is not enough if the flow cannot be completed.

### AUGE, readiness, and recovery

- PWA anchors:
  - `services/auge.ts`
  - `services/fatigueService.ts`
  - `services/recoveryService.ts`
  - `services/volumeCalculator.ts`
  - `services/computeWorkerService.ts`
- Kotlin anchors:
  - `android-native/app/src/main/java/com/example/kpkn/domain/auge/`
  - `android-native/app/src/main/java/com/example/kpkn/data/models/AugeModels.kt`
  - `android-native/app/src/main/java/com/example/kpkn/data/repository/AugeRepository.kt`
  - `android-native/app/src/main/java/com/example/kpkn/screens/auge/AugeViewModel.kt`
- Historical hints:
  - `PLAN_MAESTRO_MIGRACION.md`
  - `implementation_plan.md`
- Audit notes:
  - Prioritize formulas, defaults, muscle mapping, and derived scores over screen parity.

### Program, session, and workout flow

- PWA anchors:
  - `stores/programStore.ts`
  - `stores/workoutStore.ts`
  - `utils/programHelpers.ts`
  - `routes/navigation.ts`
  - related `components/` editors and workout surfaces
- Kotlin anchors:
  - `android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt`
  - `android-native/app/src/main/java/com/example/kpkn/data/models/Program.kt`
  - `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt`
  - `android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt`
  - `android-native/app/src/main/java/com/example/kpkn/screens/programeditor/`
  - `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/`
  - `android-native/app/src/main/java/com/example/kpkn/screens/workout/`
- Historical hints:
  - `program-editor-session-workout-migration.md`
- Audit notes:
  - Compare task completion, draft persistence, session mutation, workout lifecycle, and history writes.

### Nutrition AI and food logging

- PWA anchors:
  - `components/nutrition/RegisterFoodDrawer.tsx`
  - `services/aiNutritionParser.ts`
  - `services/localAiService.ts`
  - `stores/nutritionStore.ts`
  - `tests/nutritionLoggingRegression.ts`
- Kotlin anchors:
  - `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/`
  - `android-native/app/src/main/java/com/example/kpkn/data/localai/`
  - `android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt`
  - `android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt`
- Historical hints:
  - `MODELOS.md`
  - `docs/local-ai-functiongemma-android.md`
- Audit notes:
  - Compare parser output semantics, review states, offline fallback, and persistence.

### Persistence, settings, and stored state

- PWA anchors:
  - `services/storageService.ts`
  - `stores/*.ts`
  - `contexts/AppContext.tsx`
- Kotlin anchors:
  - `android-native/app/src/main/java/com/example/kpkn/data/db/`
  - `android-native/app/src/main/java/com/example/kpkn/data/repository/`
  - `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt`
- Historical hints:
  - `FILES_MANIFEST.md`
  - `implementation_plan.md`
- Audit notes:
  - Compare meaning and lifecycle of stored state, not storage API shape.

### Home dashboard and supporting surfaces

- PWA anchors:
  - related `components/home/`
  - `components/MyRingsView.tsx`
  - `components/BatteryRingCard.tsx`
  - supporting services and stores
- Kotlin anchors:
  - `android-native/app/src/main/java/com/example/kpkn/screens/home/`
  - `android-native/app/src/main/java/com/example/kpkn/screens/auge/`
- Historical hints:
  - `PORT_SUMMARY.md`
- Audit notes:
  - Compare high-value cards and quick actions by user job, not by exact card framing.

### WikiLab and static catalogs

- PWA anchors:
  - `data/`
  - supporting wiki components and route mapping
- Kotlin anchors:
  - `android-native/app/src/main/java/com/example/kpkn/data/WikiLabPrepopulate.kt`
  - `android-native/app/src/main/java/com/example/kpkn/data/db/WikiLabDao.kt`
  - `android-native/app/src/main/java/com/example/kpkn/data/repository/WikiLabRepository.kt`
  - `android-native/app/src/main/java/com/example/kpkn/screens/wikilab/`
- Historical hints:
  - `FILES_MANIFEST.md`
- Audit notes:
  - Verify data coverage, navigation reachability, and detail screen integrity.

### Build, manifests, and platform wiring

- PWA anchors:
  - `capacitor.config.json`
  - `manifest.json`
  - service worker and build scripts as needed
- Kotlin anchors:
  - `android-native/app/build.gradle.kts`
  - `android-native/app/src/main/AndroidManifest.xml`
  - `android-native/app/src/main/java/com/example/kpkn/MainActivity.kt`
- Historical hints:
  - old Android instructions in `AGENTS.md`
- Audit notes:
  - Compare capability wiring, not one-to-one platform APIs.

## Practical rule

If a document says "full parity" but you cannot find the Kotlin route, model, repository path, or user flow in `android-native`, treat the document as stale until proven otherwise.
