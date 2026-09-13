---
flags: []
---

# F7 — UI/UX de la sesión en vivo

Depende de overlays/voz ya corregidos en F5/F6 donde aplique. Cierra: **UI-02, UI-03, UI-04, UI-05, UI-07, UI-08, UI-09, UI-10, UI-11, UI-12, UI-13, UI-14**. UI-01 es F0. UI-06 stubs se borran en F4.

Sin flag voice/room/auge: solo Compose/`screens/workout` y `WorkoutShareService`.

## Objetivo

Back no abandona por error ni atrapa. Rotación no tira notas/molestias. Chips de coach no mienten. Share no ANR. Overlays de feedback/God Mode tienen salida. Números y plurales en español.

## Rutas

| Ítem | Archivos |
|------|----------|
| Voz diálogo | `components/VoiceCaptureModeDialog.kt`, `WorkoutScreen.kt:1614-1622` |
| Back map | `WorkoutBackNavigation.kt`, `WorkoutScreen.kt:304-308,1282-1317`, `WorkoutSessionOverlaysHost.kt`, `WorkoutFinishHost.kt:346`, `WorkoutDrawerHost.kt` |
| Saveable | `WorkoutReadinessSheet.kt`, `WorkoutFinishHost.kt:263-280`, `WorkoutStructureSheetsHost.kt:157`, `WorkoutV2Body.kt` warmup drafts / guided phase |
| Coach chips | `WorkoutGuidanceComponents.kt:220-223` |
| I/O Main | `WorkoutShareService.kt:39-70`, `SetCardExerciseMediaBack.kt:89-91,401-420` |
| FAB | `WorkoutScreen.kt:1121`, `WorkoutV2Body.kt:215-217` `recordActionHolder` |
| Feedback overlay | `WorkoutRestOverlay.kt` FeedbackContent, `WorkoutPostExerciseFeedbackHost.kt` |
| God Mode back | `WorkoutRoadmapBar.kt`, `GodModeChrome.kt` |
| Copy | `GodModeSessionBoard.kt:502`, `WorkoutContinuityComponents.kt:109+`, `WorkoutCommandDock.kt:207`, `WorkoutUiCommon.kt` `toTrimmedNumberString` |
| A11y | `SetExecutionCard` IconButton 30.dp, `RestLiveCard` Unspecified min size, media `contentDescription=null` |
| LaunchedEffect | `SetExecutionCard.kt:1235,1301,1575`, `WorkoutHorizontalWheelPicker.kt:96` |
| Pager ejes | `WorkoutV2Body.kt` HorizontalPager + `WorkoutSetPager` (no reescribir layout; solo no early-return sticky F3) |

## Impacto

### UI-02 — Diálogo de modo de voz cancelable

`onDismissRequest` → `viewModel.hideVoiceCaptureModeDialog()` **sin** `enableVoice()`. Botón «Ahora no». Primera vez: se puede abortar; el mic no queda a medias. No dejar `{}` (el BackHandler de `KpknGlassDialog` traga el back de toda la sesión).

### UI-04 — Mapa de Back

Extender `WorkoutOverlayFlags` / `hasChildBackOverlay`:

| Overlay | Back |
|---------|------|
| Rest | minimizar rest, no exit dialog |
| Feedback post-ejercicio | cerrar feedback («Ahora no»), no abandonar sesión |
| Warmup / mobility overlay | Skip o volver (ya hay Skip); no exit |
| God Mode expandido | `RoadmapMode.COMPACT` |
| Finish | hide finish (ya) |
| VolumeAdvance | omitir (ya, no-op BH con botón Omitir) |
| Readiness | no dismissible; FAB sigue siendo CTA (UI-03 no abre exit) |
| Carga `session==null` | **no** `onBack()` crudo si hay ongoing F0/F1; esperar o “Cancelar carga” que no borre draft |

`WorkoutBackNavigationTest` ampliar con estos flags. El árbol real de BH no se puede testear en JVM; el resolver puro sí.

### UI-03 / UI-07 — Rotación

`rememberSaveable` (o estado VM) para:

- Readiness: `selectedDiscomforts`, `muscleAdjustments` (anillos ya Saveable).
- Finish: `notes`, `additionalDiscomfortNote`, `selectedDiscomforts`, `shareToStory`.
- `warmupWeightDrafts` y fase guiada drop/RP → meter en `WorkoutSetDraft` (F1 ya persiste drafts).
- `showExitDialog`: Saveable.
- Sheets de estructura: cerrar en rotación es aceptable (P2); no bloquear F7.

### UI-05 — Chips coach

Si hay `message.action`, callback real (`skipExercise`, `addRestTime`, bajar sugerencia). Si no se puede cablear en este diff: chip **no clickable** (texto plano). Nunca `AssistChip(onClick={})` con label de acción.

### UI-09 — Fuera de Main

- `shareToInstagramStory`: `viewModelScope.launch(IO)` render+compress; Intent en Main. Toast de error ya existe.
- `onDispose` media: `addListener` / `Dispatchers.Main.immediate` sin `Future.get()`.
- `ExerciseUserMediaStore.list()`: `produceState`/`LaunchedEffect` IO, no `remember { list() }` en composition.

### UI-12 — FAB y errores

No poner `recordActionHolder.action = null` al cambiar de serie sin `visible=false` o disabled. Gallery `copy` fallido → `captureError`. Un canal: snackbar para finish/warnings; no duplicar Toast+Snackbar si es trivial dejar Toast de share.

### UI-13 — CTA de overlays

- Feedback: «Ahora no» + back cierra (UI-04).
- Warmup Continue disabled: copy “Saltar para empezar” junto al Skip existente.
- God Mode: back compact (UI-04).
- VolumeAdvance: se queda Omitir.

### UI-08 — LaunchedEffect

- Timer de serie: no resetear si ya corre y solo cambia `plannedTarget`.
- Countdown rest-pause: clave `index`+`total`, no el data class entero cada tick.
- Reset técnica: comparar ids/técnicas, no identidad de lista reconstruida.
- Wheel: keyear por `items` / hash, no solo `size`.

### UI-10 — Copy

- Plural `serie/series` (`GodModeSessionBoard`).
- Tildes Continuity.
- Quitar “fallback” / “vosk local” del dock de producto (o paleta debug).
- Coach “Info” → “Info” es aceptable en es; preferir “Dato”.
- `DecimalFormat` locale `es` en `toTrimmedNumberString` (coma).

### UI-11 — Targets

Mínimo 48.dp táctil (padding invisible si el icono es 24). Restaurar `LocalMinimumInteractiveComponentSize` en RestLiveCard. `contentDescription` en cámara/video/Save/timer del header. Placeholders alpha ≥ 0.6.

### UI-14

No fusionar los tres pagers en F7 (riesgo de layout). F3 ya arregla sticky. Nested scroll: el card pager gana sobre el rail si hay conflicto obvio; si no, no tocar.

## Pruebas

JVM: ampliar `WorkoutBackNavigationTest` con rest/feedback/warmup/godmode. `PrepLiveCardFabActionTest` no se rompe. `CockpitCalorieCopyTest` / display name.

No hay `createComposeRule` en el módulo; no añadir suite Compose en esta ola salvo que ya exista infra. Verificar FAB/share con test del holder (`RecordActionHolderTest`: action no null mientras visible).

```bash
./gradlew --no-daemon --console=plain testBaseDebugUnitTest --tests '*WorkoutBack*' --tests '*RecordAction*' --tests '*WorkoutUi*' --tests '*PrepLive*'
```

QA emulador (cierre de ola, no de F7 aislado): rotación en readiness y finish; back en rest; diálogo voz Cancelar; share story (si hay IG, si no solo que no ANR).

## Riesgos

- Saveable de molestias: tipos no Parcelable → `mapSaver` / listas de ids.
- Chips coach con skip real: no saltar sin el mismo guard que God Mode (F4 undo).
- Padding 48.dp puede mover el cockpit; preferir `minimumInteractiveComponentSize`.

## Fuera de alcance

Rediseño visual, i18n strings.xml completo, tests instrumentados Compose, iOS.
