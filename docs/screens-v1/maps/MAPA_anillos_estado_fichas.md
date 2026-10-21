# Mapa · anillos, estado previo, fichas e Inicio (2026-10-16, HEAD 41f8e6653)

Hecho por un explorador de solo lectura; verificado a grandes rasgos por el orquestador. Rutas bajo
`android-native/app/src/main/java/com/example/kpkn/`. Las líneas son aproximadas: búscalas por nombre.

## A. Anillos
### A1. Quién los dibuja
- **`ui/components/rings/KpknTowerRings.kt`** (canónico): `RingChannel {COLUMNA, MUSCULOS, ENERGIA}`, `TowerRingValues(columna,
  musculos, energia: Int?)` (null = sin calibrar: pista discontinua, sin arco), `KpknRingColors`. Funciones puras con prueba:
  `towerSweepDegrees`, `towerChannelAtUnit`, `towerTrackIsDashed`, `towerRingsDescription`, `towerRingCenter` (ancla del popup del
  estado previo), `towerRingBandFractions` (recorte de la hoja de detalle). `enum TowerRingsStyle {CLASSIC, LUMINOUS}`; LUMINOUS =
  pista oscura, degradado, halo y destello cada ~6 s (hoy solo en el estado previo). Firma:
  `KpknTowerRings(values, modifier, from, visible, highlighted, onRingTap, animate=true, style=CLASSIC, forcedGleamProgress)`.
  Zonas táctiles `TowerRingZones`/`TowerZone` (≥ 48 dp).
- Usos de `KpknTowerRings`: Inicio `screens/home/HomeRingsSection.kt` (112 dp, `onRingTap`); detalle `screens/home/RingDetailSheet.kt`
  (un canal recortado); estado previo `screens/workout/components/WorkoutReadinessSheet.kt` (LUMINOUS, `animate=false`); final del
  entreno `screens/workout/WorkoutFinishHost.kt` (`FinishDrainRingsBlock`, animación antes→después con `from`); editor
  `screens/sessioneditor/components/sheets/AssistantImpactTab.kt`; wizard `RingsIntroStep.kt`, `RingsIntroOverlay.kt`
  (`RingsDropTower`), `RingsResultStep.kt`, `screens/onboarding/design/ActivationOverlay.kt` (`ActivationRingsMoment`).
- Código muerto: `SingleRingCanvas`/`drawAugeRing*` en `HomeRingsSection.kt` (import muerto en `SessionEditorSheets.kt`),
  `SessionEstimatedRings`/`EstimatedRingsRow` (`SessionEditorControls.kt`), `WorkoutViewModel.shouldShowRealtimeRingsWidget()`,
  import muerto de `AdjustableRingCompact` en `WorkoutScreen.kt`. Quitar con cuidado (un import roto rompe la compilación).
- `RingColors` (`HomeRingsSection.kt`) es una lista POR ÍNDICE [Músculos, Energía, Columna] usada por el estado previo y el final:
  no reordenar. `augeRingsHostHeightDp` también se comparte.
- Torres de marca sin datos: `WelcomeRingsScene`, `PlanPreparingOverlay.BrandTower`, `WizardPrologueOverlay.prologueMiniTower`.
- Mini-anillos: `MuscleRing` 16 dp en `screens/home/design/HomeMuscleChips.kt`; `drawMuscleRing` del wizard.

### A2. Ajuste manual (escala 0–100 en pantalla)
- Inicio → `RingDetailSheet`: `RingsChannelSlider` (`screens/onboarding/rings/RingsSlider.kt`, `onPreview`/`onCommit`), tag
  `home-ring-adjust`, «Guardar ajuste» solo si cambió → `HomeScreen.kt` → `AugeViewModel.applyManualBatteries(muscular=|neural=|spinal=)`.
- Estado previo: tocar un anillo abre un `Popup` anclado con `towerRingCenter` → `ReadinessAdjustCard`: Energía/Columna con
  `ReadinessSliderRow` (Slider 0..100), Músculos con `MinimalMuscleSlider` por músculo de la sesión.
- Final del entreno (`WorkoutFinishHost.kt`, acordeón «Recalibrar Rings»): `AdjustableRingCompact` (arrastre vertical) para Energía
  y Columna, `MinimalMuscleSlider` por músculo; estado `neuralFinal`/`spinalFinal`/`muscleFinal`, `editedMuscleKeys`,
  `recalibratedCanonicalMuscularBattery`; va en `SessionClosingFeedback`; se guarda en `WorkoutScreen.kt` (onComplete de
  `finishWorkout`) con `augeViewModel.applyManualBatteries(neural?, spinal?, perMuscleDelta…)` o `refresh()`.
- Wizard `RingsResultStep.kt`: `AdjustRow` Energía/Columna con `RingsChannelSlider`, fichas por grupo (`RINGS_MUSCLE_GROUPS`);
  `SetupWizardViewModel.setRingsManual`/`setMuscleManual`/`resetMuscleManual`.
- **Qué se guarda** (`screens/auge/AugeViewModel.kt`): `applyManualBatteries` escribe anclas V2 por músculo
  (`ManualMuscleBatteryOverride`, claves normalizadas con `VolumeCalculator.normalizeCanonicalMuscleGroup`), los globales
  `manualNeuralBattery`/`manualMuscularBattery`/`manualSpinalBattery` (**Columna invertida** con `invertDisplayedStructure` →
  `AugeRecoveryEngine.invertStructureRingScore`), `manualBatteryAnchorMs`, guarda con `augeRepo.saveWellbeingLog`, aprende τ
  (`learnFromManualAdjustment`) y recalcula. `saveWellbeing(log)` (estado previo) fusiona con `mergeWellbeingForSave`
  (`domain/auge/WellbeingSaveMerge.kt`) para no borrar las anclas V2.

### A3. Valores por músculo
- `AugeRecoveryEngine.getPerMuscleBatteries(...)` → `Map<String, MuscleRecoveryStatus>` (`recoveryScore` 0–100,
  `hoursToRecovery`, `hoursSinceLastSession`, `effectiveSets`, `status`); publicado en `AugeViewModel.perMuscle`.
- Ids = pilares en español (`domain/auge/AugeMuscleNormalization.kt`: `getAugeMusclePillarId`, `remapMuscleIntMapToPillars`).
  **13 pilares**: Pectorales, Dorsales, Deltoides, Bíceps, Tríceps, Cuádriceps, Isquiosurales, Glúteos, Pantorrillas, Abdomen,
  Trapecio, Erectores Espinales, Core.
- Músculos del estado previo: pilares PRIMARY/SECONDARY de los ejercicios de la sesión (`WorkoutScreen.kt`), valor de `perMuscle`.

## B. Estado previo
- `screens/workout/components/WorkoutReadinessSheet.kt`, `WorkoutReadinessSheet(...)`: `KpknSheet` no descartable; título;
  `InterlacedRingsBlock` (torre LUMINOUS, popup de ajuste, etiquetas, «Toca un anillo para ajustarlo»); consejo de patrón
  (`ExerciseReadinessEngine.buildPatternCoaching`); aviso AUGE; molestias `PreWorkoutDiscomfortSelector` (sugerencias de
  `DiscomfortSuggestionEngine` según los pilares de la sesión, búsqueda y chips); voz `SessionVoiceReadinessCard` (interruptor
  «Entrenar con Comandos de Voz», selector Manos libres/Música, popups de información `VoiceModeInfoCard`); ✓ de 64 dp.
- Al confirmar envía solo los canales editados (`manualMuscular` siempre null). Se monta en
  `screens/workout/WorkoutSessionOverlaysHost.kt`: construye el `DailyWellbeingLog` (Columna invertida, músculos V1 remapeados,
  `preWorkoutDiscomforts`), `augeViewModel.saveWellbeing(log)`, `viewModel.saveReadinessAdjustments(...)` y `onReadinessDismissed()`.
- Se muestra mientras `!readinessSheetDismissed && uiState.readinessNeuralOverride == null`.

## C. Fichas de ejercicio
- **Catálogo y editor**: `screens/sessioneditor/components/ExerciseCatalogInfoDialog.kt`, `ExerciseCatalogInfoDialog(model,
  onDismiss, onToggleSelection, onSelectOption, onImplementationSettled, showFooter=true)` (`KpknSheet` al 94 %), cuerpo
  `CatalogDetailContent`, modelo `CatalogDetailModel` (name, caption, description, imageVariants, selectedImplementation, axes,
  configurationLine, configurationIncomplete, muscleInfo, joints, patternId, isSelected, canSelect). Secciones: cerrar, carrusel
  `CatalogExerciseImageCarousel` con píldoras de implemento, nombre y caption, «Músculos · aporte por 1 serie»
  (`CatalogMusclesModule`, 4 y «Ver todos», tocar abre `CanonicalKnowledgeOverlay`), descripción (`CatalogDescription`, 4 líneas y
  «Leer más»), «Configuración» (ejes no implemento), `CatalogInvolvementAccordions` (chip «Patrón · X», «Articulaciones»), pie
  «Añadir»/«Quitar». NO muestra cues ni historial ni fotos.
- Quién la abre: selector `ExercisePickerV2Catalog.kt` (catálogo de `MainActivity`, `ExercisePickerSheet` del editor y de la sesión
  en vivo en `WorkoutStructureSheetsHost.kt`); editor sin pie `SessionExerciseDetailSheet` (`ExerciseCatalogV2Info.kt`, modelo de
  `sessionExerciseDetailModel`), desde `SessionEditorScreen.kt` (acción «Ficha»).
- **Wizard**: `screens/onboarding/design/entreno/plan/ExerciseInfoOverlay.kt` (`ExerciseInfoLayer`, modelo `ExerciseInfo` con
  `exerciseInfoOf`, que devuelve null antes que inventar), abierta desde `PlanDetailOverlay.kt`.
- **Sesión en vivo**: no hay ficha; «Modo visual» es `SetCardExerciseMediaBack.kt` (cámara CameraX, Técnica KPKN 88 dp, Foto/Vídeo/
  Galería, Álbum). Cajón de historial `ExerciseHistoryContent`; set-up `WorkoutExerciseSetupContent` (único sitio con cues).
- Datos: cues en `ExerciseMuscleInfo.setupCues/executionCues`, `Exercise.executionCues` y el perfil v2. Historial:
  `ProgramRepository.history`; en la sesión `WorkoutViewModel.getExerciseHistory`, `bestEstimated1RmForExercise`; fuera,
  `Exercise.resolveSharedPerformance(history)` (`SessionEditorSessionHelpers.kt`), `buildStarredExerciseProgress`,
  `calculateHybrid1RM`. No hay «última vez» hecho: sale de la fecha del `WorkoutLog` más reciente. Claves de identidad:
  `identityKeysForExercise`, `resolvedCanonicalExerciseId`, `analyticsExerciseKey`; ojo con el filtro estricto por etiqueta.

## D. Inicio
- `screens/home/HomeRingsSection.kt`: «Tus RINGS» con (i) → `RingsIntroOverlay(replay=true)`, titular del motor, torre 112 dp con
  `tourAnchor(TourAnchors.HOME_RINGS)` (sin programa al 50 %), tres `HomeRingRow` (orden Energía → Músculos → Columna), «Confianza…
  · Toca un anillo para ver por qué». `scoreOf` da null sin cobertura (nunca un 100 inventado).
- Tocar → `onSelectedChannelChange` → `RingDetailSheet` (título, torre recortada, rango habitual, POR QUÉ, QUÉ HACER HOY, POR
  MÚSCULO, ÚLTIMAS SESIONES, aviso, «¿TE SIENTES DISTINTO?» con slider y «Guardar ajuste», confianza).
- Pruebas: `HomeRingsSectionTest`, `RingDetailSheetTest`, `KpknTowerRingsTest`, `KpknTowerRingsSemanticsTest`,
  `KpknTowerRingsRenderSheetTest`.

## Riesgos
- `KpknTowerRings` la comparten ~12 sitios: conservar null = sin calibrar, `towerRingCenter`, `towerRingBandFractions`, zonas de
  48 dp.
- Columna invertida: la UI trabaja con el valor visible; se guarda la batería cruda; no invertir dos veces.
- Claves de músculo siempre como pilar. V1 y V2 conviven (estado previo escribe V1 vía `saveWellbeing` + `mergeWellbeingForSave`;
  Inicio y final escriben V2). `manualMuscular` va a null a propósito.
- Estado previo: no descartable; si `saveReadinessAdjustments` no fija `readinessNeuralOverride`, la hoja reaparece.
- Fichas: `CatalogDetailContent` lo comparten selector (con pie) y editor (sin pie); `ExerciseInfoOverlay` no inventa; la cámara
  de «Modo visual» respeta `isSettledPage` y `stopIfRecording`.
