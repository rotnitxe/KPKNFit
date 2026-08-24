---
flags: []
stage: construction
---

# Modo Ultrarrápido + Edición de tipo de serie en sesión en vivo

## Rutas

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt` — integración `WorkoutV2Body` + `WorkoutRoadmapBar` + botón tiempo (header `WorkoutHeaderBar`/`WorkoutSessionCockpit`) + hosts de sheets ultrarrápido.
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt` + `WorkoutSessionHydrator`/`WorkoutPersistenceController`/`WorkoutStructuralPersistenceController` — estado vivo `Session` (variante activa `WeekVariant`), `visibleExercises()`, `persistOngoingState()`, nuevo estado `ultraFast`.
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSetPager.kt` — timeline stepper; añadir `combinedClickable` por dot + mini-stepper contextual.
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/WorkoutRoadmapBar.kt` — `ExerciseRoadmapCard`/`SupersetRoadmapCard` long-press → `SeriesTypeSheet` con mini stepper.
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/SeriesTypeSheet.kt` (nuevo) — mini stepper S1…Sn + chips Normal/Dropset/Rest-Pause + toggle override.
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/UltraFastPreviewSheet.kt` (nuevo) — preview/confirmación y banner de ahorro.
- `android-native/app/src/main/java/com/example/kpkn/domain/sessionassistant/UltraFastConfig.kt` (nuevo) — familias protegidas (sentadilla alta/baja/frontal/zercher/búlgara, peso muerto barra/zercher, press banca plano barra).
- `android-native/app/src/main/java/com/example/kpkn/domain/sessionassistant/UltraFastEngine.kt` (nuevo) + `UltraFastModels.kt` (nuevo) — lógica pura de clasificación, reducción volumen, densificación y superset polea/smith.
- `android-native/app/src/main/java/com/example/kpkn/domain/sessionassistant/TimeCoachEngine.kt` + `android-native/app/src/main/java/com/example/kpkn/domain/calculations/Calculations.kt` — base `calculateSessionTimeBreakdown` para cálculo ahorro.
- `android-native/app/src/main/java/com/example/kpkn/domain/workout/SupersetRules.kt` — creación supersets ultrarrápido.
- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt` + `WorkoutV2Models.kt` + `WorkoutUiModels.kt` — extender `WorkoutUiState` con `ultraFast: UltraFastUiState?` + `ultraFastSnapshot` + `ultraFastManualOverrides` sin migración Room (v23, OngoingWorkout serializa Session).
- Tests: `android-native/app/src/test/java/com/example/kpkn/domain/sessionassistant/UltraFastEngineTest.kt` (nuevo).

## Impacto

- Solo Android nativo. Room v23 intacta (transformaciones son `copy()` sobre `Session` en memoria + snapshot en `OngoingWorkout`).
- `domain/` permanece pure Kotlin sin `android.*`; `UltraFastEngine` es puro y testeable JVM.
- `WorkoutUiState` añade `ultraFastSnapshot`, `ultraFastApplied`, `ultraFastManualOverrides`, `showSeriesTypeSheet`; ViewModel expone `StateFlow` read-only vía `asStateFlow()`.
- Transformaciones ultrarrápido son solo sesión viva (variante activa `WeekVariant`). Reversible con “Deshacer” que restaura snapshot; no toca `sessionB/C/D` ni plantillas. Scope `SESSION_ONLY`.
- Paridad iOS/backend documentada para futuro, sin tocarlos ahora.
- Clasificación curada por familia (no 3 ids): sentadillas alta/baja/frontal/zercher/búlgaras, pesos muertos barra/zercher, press banca plano barra. Override manual por ejercicio permite forzar técnicas en protegidos.

## Pruebas

- Unit `UltraFastEngineTest` (JVM): protected_reduced_4to2_3to2_2to1, dangerous_tier0_not_densified, isolation_machine_becomes_single_drop_or_restpause, same_pulley_antagonist_creates_superset, different_machine_no_superset, already_in_superset_skipped, no_touch_completed_sets, time_saved_positive, undo_restores_snapshot, unilateral_preserves_sides, manual_override_allows_protected_densification.
- ViewModel: `updatePlannedSeriesTechnique` solo futuras, `applyUltraFast` actualiza índices seguros, `persistOngoingState` invocado.
- UI previews Compose para `SeriesTypeSheet` y `UltraFastPreviewSheet`.
- Manual: sesión 3 ejercicios (1 básico 4x, 1 polea bíceps 3x + polea tríceps 3x antagónicos misma polea, 1 máquina aislado 4x) → Ultrarrápido → verificar preview y dump `OngoingWorkout`.
- Validación: `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.UltraFastEngineTest'"` luego `compileBaseDebugKotlin` targeted antes de `assembleDebug`.

## Riesgos

- Definición familias protegidas: hardcodear mal puede quitar volumen donde sí tolera drops. Mitigado con lista curada editable + override manual + warning.
- Detección misma máquina frágil (brand texto libre). Mitigado normalizando `equipmentId` del catálogo V2 + solo polea/smith (`polea, cable, smith, maquina_smith`) y brand lowercased; solo parear si `machineKey` idéntico no vacío.
- AUGE/fatiga: drops/rest-pause eleva `effectiveRPE` y `densityMultiplier`. Mitigado usando `calculateSessionTimeBreakdown` con `supersetGroups` y rests adaptados; no tocar `AugeFatigueEngine` ahora.
- Persistencia: snapshot en `OngoingWorkout` debe serializar nuevas técnicas con defaults. Mitigado: `ExerciseSet` ya tiene defaults para `dropSets/restPauses`.
- UX long-press colisión con drawer existente. Mitigado: sheet reemplaza drawer cuando target es editar series.
- Worktree compartido: no `reset/clean/stash/checkout`, no reformateos masivos.
