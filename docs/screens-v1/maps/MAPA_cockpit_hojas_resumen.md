# Mapa · cockpit, hojas, opciones, reordenar, resumen, compartir y álbum (2026-10-16, HEAD 41f8e6653 + LV4)

Hecho por un explorador de solo lectura (antes del merge de LV4, que añadió `LiveSessionReorder`, cierres de hojas y las píldoras
de pendientes del asa). `K = android-native/app/src/main/java/com/example/kpkn`. Líneas aproximadas.

## 1. Barra inferior, cockpit y modo dios (hoy son el MISMO estado)
- `WorkoutScreen.kt`: `roadmapMode` (`RoadmapMode.COMPACT/EXPANDED`, `rememberSaveable`), `roadmapSelecting`, `dockBottomClearance`.
  Atrás en expandido → `WorkoutBackAction.COLLAPSE_GOD_MODE` (`WorkoutBackNavigation.kt`, prueba `WorkoutBackNavigationTest`).
- `components/WorkoutRoadmapBar.kt`: tocar zona vacía o el asa (48 dp, «Desplegar roadmap») alterna; sin gesto de deslizar.
  Contenido: asa, «Deshacer {label}» (modo dios → `viewModel.revertGodModeChange`), ranura del stepper de series
  (`WorkoutSetPager`), `LazyRow` del carrusel de ejercicios. LV4 añadió píldoras «Terminar»/«Estado previo» junto al asa.
- Acciones del carrusel: tocar → `selectExercise`/`selectSupersetGroup`; punto de serie → `selectWorkoutStep`; pulsación larga
  (solo compacto) → opciones del ejercicio; **modo dios**: «+» (`GodModePlusCard` → catálogo con `addExerciseAfterId`), X →
  `removeExerciseFromSession`, X de superserie → `dissolveLiveSuperset`, arrastrar → reordenar, mantener → selección con barra de
  lotes («Superserie» `createLiveSuperset`, «Omitir» `skipExercises`, «Eliminar» `removeExercisesFromSession`, «Ultrarrápido»
  diálogo Modo ultrarrápido/Drop-set/Rest-pause → `updatePlannedSeriesTechnique` por ejercicio). Deshacer del modo dios:
  `pushGodModeUndo`, `revertGodModeChange`, `godModeUndoStack` (persistido).
- Panel expandido `components/WorkoutSessionCockpit.kt` («Cockpit de la sesión», «Progreso: x/y»), pestañas: **Resumen** (hitos
  `sessionMilestones`, «Ejercicios estrella» e1RM vs `goal1RM`), **Fotos** (cámara, galería, reintentar, borrar: `mediaCapture.*`,
  `addSessionPhoto`, `retryPendingMediaCaptures`, `removeSessionMedia/Photo`), **Herramientas** (temporizador local, calculadora
  RM `WorkoutRmCalcContent`, calorías `sessionCalorieStatus`), **Notas** (`setSessionNotes`, `saveSessionNote`, tareas
  `add/toggle/removeSessionChecklistItem`). Montaje en `WorkoutScreen.kt` con `AnimatedVisibility(roadmapMode == EXPANDED)`,
  vidrio `kpknGlassOrFallback(overlayHazeState)`.

## 2. Hojas de la cabecera (`WorkoutHeaderBar.kt`, montada en `WorkoutV2Body.kt`)
- **Etiquetas:** chips de etiqueta activa → `tagManagerTagId`; subetiqueta → `toggleSubTagActive`; «Etiquetas/Sin etiquetas» →
  `WorkoutTagListOverlay` (`WorkoutExerciseSheets.kt`: filas con `lastLoadLabel`, educación, «Crear etiqueta nueva», «Cerrar»;
  `selectMainTag`, `markWorkoutTagEducationSeen`); `WorkoutCreateTagOverlay` (nombre, SET-UP opcional: máquina, carga base,
  notas; `createTag`); `WorkoutUntaggedAdoptionDialog` («Usar historial»/«Dejar limpia»); `WorkoutTagManagerModal` (renombrar,
  set-up, historial, subetiquetas, reiniciar historial, eliminar). Sistema heredado aparte: `ExerciseTagSheetContent`
  (`setExerciseTag`/`clearExerciseTag` sobre `uiState.exerciseTags`). **Dos sistemas conviven: no unificar sin cuidado.**
- **Tiempo:** `WorkoutChronometer` (chip con anillo de ritmo) → `KpknAlertDialog`: restante, atajos 30/45/60/90, «Asignar tiempo»
  (`KpknNativeTimePickerDialog`), −5/+5/+15, «Quitar», «Solo esta vez»/«Permanente», Avisos «Nada · 15 y 5 min · Ritmo · Por
  ejercicio» (`PacingAlertMode`), acordeón del Ultrarrápido (activo con ahorro y «Deshacer», o botón). VM:
  `setAbsoluteSessionTimeLimit`, `clearSessionTimeLimit`, `setPacingAlertMode`; objetivo `resolveEffectiveSessionTargetMinutes`;
  ritmo `evaluatePace` (`WorkoutPacingController.kt`). **No hay proyección de fin.**
- **Ultrarrápido:** `components/UltraFastSheet.kt` `UltraFastPreviewSheet` (calculando, duración estándar vs con ultrarrápido,
  ahorro, AJUSTES POR EJERCICIO con estado y −/+ 1–6, «Permitir técnica intensa/Mantener protegido», SUPERSETS, Cancelar/Aplicar,
  «Reversible»). VM `previewUltraFast`, `toggleUltraFastManualOverride`, `applyUltraFast`, `revertUltraFast`; motor
  `domain/sessionassistant/UltraFastEngine.kt`. **Comprime el plan; NO existe registro rápido de series.**
- **Voz:** segmentado Manos libres/Música (`setVoiceCaptureMode`) e icono de micrófono (`toggleVoiceSession`, con permisos);
  `VoiceCaptureModeDialog` («¿Cómo quieres entrenar hoy?», dos tarjetas largas, «Empezar»/«Ahora no»); en el estado previo
  `SessionVoiceReadinessCard`. `voiceVerbosity`/`ttsSpeechRate` existen en Settings sin UI.

## 3. Opciones
- **Serie** (pulsación larga en el stepper, solo página NORMAL): `SeriesTypeSheet` (nodos S1…Sn, hechos deshabilitados, mantener
  marca varios, «Desde S{n} hasta el final»/«Solo S{n}», TIPO «Normal · Dropset · Rest-Pause», Cancelar/Aplicar →
  `updatePlannedSeriesTechnique`). Código muerto `GodModeSetActionsOverlay` (Eliminar serie `removeSetFromExercise`, Omitir serie
  `omitSet`). Editar series: `WorkoutEditSeriesSheet`. Añadir serie: `addSetToCurrentExercise`.
- **Ejercicio** (pulsación larga en el carrusel, `WorkoutStructureSheetsHost.kt`): fuerza «Historial · Etiquetas · Editar ·
  Reemplazar · Agregar otro · Reordenar · Crear superserie · Omitir»; superserie «Ir a la superserie · Rondas · Agregar de sesión ·
  Agregar del catálogo · MIEMBROS · Disolver»; cardio «Editar cardio · Reemplazar · Omitir». Cambios de estructura → alcance «Solo
  esta vez · Guardar en esta semana · Aplicar a todo el bloque» (`commitStructuralPersistence`).

## 4. Reordenar
Hoja con flechas `WorkoutReorderSheets` («Reordenar ejercicios», aviso «Cambio de grupo») → `applyReorderAndPromptPersistence`;
arrastre solo en modo dios. LV4 unificó la regla en `domain/workout/LiveSessionReorder.kt` (orden pedido aplicado, cambios de
grupo mínimos, omitidos conservados, cardio aparte, superseries como unidad) y arregló «para siempre». **No existe fijar lo hecho
y lo en curso.**

## 5. Resumen final
`WorkoutFinishHost.kt` `FinishWorkoutSheet` (`KpknSheet`, 0,86), una columna: título y sesión, sesión vacía, `PendingSeriesNotice`,
anillos `FinishDrainRingsBlock` + «Mañana…», acordeón «Recalibrar Rings», «Resumen de Carga» (duración, tonelaje, series, cardio,
«Ver detalles»), aviso de intensidad genérica, «Intensidad» y «Técnica», «Molestias reportadas» (buscador, «Molestia adicional»),
«Nota rápida». Botonera: «Compartir en Instagram Stories», FAB ✓ «Guardar y terminar», «Volver». **No hay sección de récords**
(los hitos se guardan pero no se muestran). Guardado: `executeConfirm` → `SessionClosingFeedback` → `viewModel.finishWorkout` →
`WorkoutFinishController.finish` (guardas, `awaitRecordingIdle`, sesión vacía, log, `repository.finalizeWorkout` en
`NonCancellable`) → `onComplete` (baterías manuales o `refresh`). `LaunchedEffect(voiceFinalConfirmTriggered)` guarda por voz
desde la hoja: no puede quedar en una pestaña que no se compone. LV4 añadió `finishClosedPending` y `onNotesDraft`.

## 6. Compartir
`WorkoutShareService`: `shareToInstagramStory` (PNG 1080×1920, FileProvider), `launchShareIntents` (Instagram o selector),
`renderMinimalStoryCard` (la usada), `renderStoryCard` (sin uso). Sin selector de plantilla. VM `shareWorkoutToStory`,
`latestCompletedSessionSnapshot`, `WorkoutShareSnapshot`.

## 7. Álbum
`WorkoutMedia` (PHOTO/VIDEO, exerciseId, canonicalExerciseId, setIndex, side, weightKg, reps, isPr, sessionKey, workoutLogId),
tabla `workout_media`, `WorkoutMediaRepository` (`listForSessionKey`, `listForExercise` sin uso, `delete` definitivo),
`WorkoutMediaCaptureController`, `WorkoutSessionAlbumSheet` (`ModalBottomSheet`, «Álbum de esta sesión», borrar sin deshacer),
álbumes fuera de la sesión `screens/albums/*`.

## 8. Hojas y animación
`KpknSheet` vía `KpknPortal` (zIndex 400, entrada con muelle, cierre 180 ms, arrastre); diálogos `KpknGlassDialog`/`KpknAlertDialog`
sin animación; `WorkoutDrawer` sin portal y su salida no se ve. Regla: una pieza compuesta dentro de la página no lee
`LocalHazeState` (usa `kpknGlassOrFallback(null, …)`).

## Riesgos
Separar cockpit y modo dios exige conservar `enableLongPress = !EXPANDED`, Atrás y su prueba. Dos sistemas de etiquetas. El guardado
del resumen y el guardado por voz. Pruebas que fijan textos: `LiveSessionOptionsRenderSheetTest`, `LiveSessionPrepRenderSheetTest`,
`LiveSessionTechniqueRenderSheetTest`, `WorkoutBackNavigationTest`.
