# workoutui - paquete de UI del entreno en vivo

Alcance: CardioLiveCard, SetExecutionCard, hoja de resumen/final (WorkoutFinishController + WorkoutFinishHost),
WorkoutStepNavigator y los androidTest de `screens/workout`. Sin Gradle/adb/emulador (solo lectura/edicion).
Los archivos CardioLiveCard.kt y SetExecutionCard.kt ya traian cambios de otros paquetes (grabacion durable,
guided commit retry); los mios son quirurgicos y no los tocan.

## (1) MEDIO - tile "Km" cardio con double crudo

- Diagnostico: `distanceText` se inicializa con `completedSet.distanceKm.toString()` (p. ej. `0.10811738104249106`,
  distancia por GPS) y la tile mostraba `distanceText.ifBlank { "—" }` cuando `gpsHasData == false`
  (tras registrar/restaurar el GPS ya no tiene datos). El texto es el estado interno de la rueda de distancia,
  no un campo tecleado a mano; no se toca.
- Cambio (CardioLiveCard.kt): nueva `internal fun cardioDistancePillText(gpsHasData, gpsDistanceKm, enteredDistanceKm)`
  que formatea el valor numerico derivado con el mismo `formatCardioDistance` de la linea "Distancia"
  (`"%.2f km"`), y "—" si no hay valor. `distanceText` y lo que se envia a `onRequestRecord/onRecord`
  conservan la precision completa (solo cambia la visualizacion).
- Dispositivo: repetir cardio-two-series; tras registrar, la tile Km debe decir `0.11 km` (no `0.108117...`)
  y coincidir con "Distancia 0.11 km". Reabrir/restaurar: igual.
- JVM: `CardioDistancePillTextTest` (nuevo): double crudo restaurado -> "0.11 km"; GPS gana con datos;
  sin valor -> "—".

## (2) MEDIO - rom=100 en todas las series + 4 SetExecutionCardUiTest rojos

- Diagnostico de producto: `romValue` se inicializaba `initialDraft?.rom ?: sessionCompletedSet?.rom ?: 100` y se
  enviaba siempre en `SetAdvancedFeedback.rom`, en el borrador y en el calculo de `isDirty`, aunque el slider
  solo se muestra con `exercise.trackRom` (la ruta por voz ya hacia `rom = if (trackRom) ... else null`).
  Efectos: Room con `rom=100` en ejercicios sin ROM; borrador "sucio" permanente (100 != null) para toda serie visitada;
  factor de descanso adaptativo `romFactor(100)=1.02` aplicado de mas a todos.
- Cambio (SetExecutionCard.kt): `DEFAULT_ROM_PERCENT = 100` solo si `exercise.trackRom`;
  `pristineRomValue = completedSet.rom ?: (100 si trackRom)`; `romValue` arranca en borrador (solo si trackRom) ?: pristine;
  `remember` ahora tambien depende de `trackRom`; `isDirty` compara contra `pristineRomValue` (estable, no contra
  el borrador que evoluciona). Un `rom` ya guardado en una serie completada se conserva (editar no borra datos).
  Un borrador heredado con rom=100 de un ejercicio sin ROM se ignora.
- Tests por causa (evidencia: instrumentation\base\20261002T044411027438Z-9b8526-workout-editor-ui\summary.json):
  - `guidedDropFinalCommitRetryDoesNotAppendAnotherRow`: unica diferencia `rom=100` vs `rom=null` -> DEFECTO DE PRODUCTO
    (arreglado arriba; la expectativa del test era correcta).
  - `guidedRestPauseFinalCommitRetryDoesNotAppendAnotherRow`: `rom` (producto, arreglado) y ademas `restTime`
    esperado 20 vs 15. DEFECTO DEL TEST: el rest-pause guiado tiene pausa fija
    `RestPausePlanDefaults.PauseSeconds` (=15, `ScheduledTechniqueDefaults.REST_PAUSE_SECONDS`; la propia UI dice
    "Pausa fija de 15s", la cuenta atras arranca en ese valor y `beginRestPauseCountdown` la usa; igual en el
    baseline). La fila nueva registra el descanso realmente corrido (15); el 20 era un numero arbitrario copiado
    de la fila restaurada. Se mantiene la fila restaurada en 20 (distinto a proposito: prueba que se conserva verbatim) y
    la fila nueva espera `RestPausePlanDefaults.PauseSeconds`. Es mas estricto, no mas laxo.
  - `guidedSkipRetryKeepsExplicitEmptyCommitSeparateFromEnteredRows` y
    `guidedRestPauseCountdownSkipRetryKeepsExplicitEmptyCommit` (`expected:<1> but was:<0>`): DEFECTO DEL TEST.
    "Saltar técnica y registrar solo la serie" abre un `AlertDialog` de confirmacion ("¿Registrar solo la serie
    principal?" / "Sí, solo la serie") en GuidedTechniquePanel, igual en el baseline; el test nunca confirmaba, asi que
    `onRecordV2` no se llamaba. Ahora el test pulsa "Sí, solo la serie". Las demas aserciones no cambian
    (y quedan verificadas por razonamiento con las mismas rutas que ya pasaban en los otros dos tests).
- Tests nuevos (androidTest): `romIsNotReportedWhenTheExerciseDoesNotTrackIt`, `romStartsAtFullRangeWhenTheExerciseTracksIt`.
- Dispositivo: strength-s1-s2: `completed_sets.rom` NULL en ejercicios sin "Medir ROM"; con ROM activado, 100 por defecto
  o el valor del slider. Ya no debe aparecer borrador persistido en sets sin tocar (menos "cambios pendientes" espurios).
- Nota (fuera de alcance, no tocado): el flujo guiado ignora `params["pauseSeconds"]` de una `PlannedTechnique`
  apilada (el scheduled si lo respeta). Hoy todos los productores escriben 15, asi que no hay divergencia real.

## (3) MEDIO - terminar con 0 series bloqueado en silencio

- Diagnostico: el guard P0 de `WorkoutFinishController` (evento `finish_blocked_empty_session`) ya llama a
  `onEmptySession` -> toast "No registré ninguna serie; no guardé un entrenamiento vacío." (+ voz si esta activa),
  pero la hoja seguia mostrando el boton sin ninguna explicacion persistente (el toast es efimero y no aparece
  en el volcado de accesibilidad).
- Cambio (sin tocar regla ni contrato):
  - WorkoutFinishController.kt (nivel de archivo): `FINISH_EMPTY_SESSION_GUIDANCE =
    "Registra al menos una serie para terminar o abandona sin guardar."`, `isFinishBlockedForEmptySession(...)`
    (el mismo predicado `completedExercises.isEmpty()` que ahora usa el guard) y `finishEmptySessionGuidance(...)`.
  - WorkoutFinishHost.kt (`FinishWorkoutSheet`): si el guard bloquearia, banner ambar bajo el titulo con
    `liveRegion = Polite` (TalkBack lo anuncia) y `stateDescription` en el boton de confirmar; la
    `contentDescription` "Guardar y terminar entrenamiento" no cambia (la usan los drivers).
    El boton sigue activo: tocarlo ejecuta el guard, el evento y el toast como antes.
- JVM: `WorkoutFinishEmptySessionTest` (nuevo, Robolectric como el de timeout): texto exacto, el mensaje solo aparece
  cuando el guard bloquea, y el controller con sesion vacia sigue llamando `onEmptySession` una vez, no persiste log,
  no completa, no falla, deja la hoja abierta e `isFinishingWorkout=false`.
- Dispositivo: abrir salida -> "Terminar hasta acá" sin series: ver el banner en la hoja y que no se crea log.
  Tras registrar una serie y reabrir, el banner no aparece.
- Sugerencia no aplicada (WorkoutViewModel no es de este paquete): alinear el texto del toast de
  `handleEmptySessionFinishBlocked` con `FINISH_EMPTY_SESSION_GUIDANCE`.

## (4) BAJO - ultima serie de cardio abre el resumen con fuerza pendiente: DIFERIDO

- Diagnostico: `nextIncompleteStepAfter` solo mira pasos posteriores (salvo la cola de voz) y cardio va al final del carril.
- Por que no se toca (riesgo alto para el beneficio): la navegacion hacia adelante es diseno explicito
  (`WorkoutStepNavigatorResumeTest.nextIncompleteStepAfter_doesNotJumpBackToIncompleteMobilityAfterWarmup`);
  `skipExerciseAndAdvance`, el recorder (`nextStepForRest`) y el orquestador de descanso tratan "sin pasos despues" como fin;
  envolver en `nextSet` rebota a quien salto calentamiento/movilidad o decidio terminar antes, y no se puede validar
  en dispositivo ni con la suite aqui. Es una decision de producto (envolver vs. avisar).
- Opciones futuras: envolver solo desde pasos CARDIO hacia WORKING_SET incompletos con test de `WorkoutStepNavigator`,
  o dejar el resumen y mostrar "Quedan N series sin registrar" en la hoja.

## (5) MEDIO - 4 tests obsoletos de WorkoutV2UiTest (UI vigente = correcta)

Evidencia: instrumentation\base\*workout-avd*attempt2* (y repeat1-4): fallan siempre los mismos 4.
Producto correcto: "Opciones avanzadas" es el CTA vigente (SetExecutionCardUiTest.advancedOptionsCtaUsesNewCopyAndHidesReportLabel
lo exige), la cara trasera ofrece "Añadir drop-sets"/"Añadir rest-pause"/"Error de ejecución"; la pila unilateral muestra
puntos "L"/"R" (UnilateralSetStackNode); las nubes del stepper son `ActivityCloudArea.label` en mayusculas.
- `title_stays_single_line_and_mode_chips_visible` y `technique_menu_can_toggle_drop_set`: ahora abren
  "Opciones avanzadas", pulsan "Añadir drop-sets" y afirman el estado ("Drop-sets" presente, "Añadir drop-sets" ausente;
  "Añadir rest-pause" y "Error de ejecución" presentes). Antes varias lineas eran `onNodeWithText(...)` sin aserción (sin efecto);
  ahora son `assertExists`. El clic a "AMRAP" se elimina: AMRAP ya no es un conmutador manual de la tarjeta (solo viene del plan).
- `compact_pager_card_keeps_side_chip_and_status_text`: "Izq" -> chips "L" y "R".
- `stepper_renders_both_activity_cloud_areas`: "Preparación"/"Series efectivas" -> "PREPARACIÓN"/"SERIES EFECTIVAS".
- Los 3 `WorkoutWarmupInventoryIntegrationTest` SKIPPED requieren el usuario QA 10: no son defecto.
- Limitacion: no pude ejecutar los androidTest; las expectativas nuevas se derivaron del codigo vigente
  (SetCardTechniqueBack, WorkoutSetPager, SetExecutionCard). Correr `WorkoutV2UiTest` y `SetExecutionCardUiTest`.

## (6) BAJO - FAB "Registrar serie" tapado por el teclado: DIFERIDO

- Diagnostico: el FAB vive en WorkoutScreen (`BottomEnd`, `navigationBarsPadding` + `dockBottomClearance + 12.dp`),
  con edge-to-edge el teclado lo cubre pero sigue en el arbol de accesibilidad (lo que usa TalkBack/Switch Access).
- Por que no se toca: `imePadding()` en su contenedor lo subiria `dockBottomClearance` por encima del teclado
  (la clearance es la del dock que el teclado ya cubre) y taparia los campos que se estan editando; ocultarlo con el IME
  quita la unica via de registrar para TalkBack con el teclado abierto. Hace falta una pasada de layout en dispositivo.
  WorkoutScreen.kt tampoco es de este paquete.
- Opcion para la siguiente ola: anclar a `WindowInsets.ime` con `bottom = 12.dp` cuando el IME esta visible y validar solape.

## Archivos
Producto: screens/workout/CardioLiveCard.kt, screens/workout/components/SetExecutionCard.kt,
screens/workout/WorkoutFinishController.kt, screens/workout/WorkoutFinishHost.kt.
androidTest: screens/workout/WorkoutV2UiTest.kt, screens/workout/components/SetExecutionCardUiTest.kt.
JVM nuevos: CardioDistancePillTextTest.kt, WorkoutFinishEmptySessionTest.kt (screens/workout).
Fin de linea: LF en CardioLiveCard.kt, WorkoutV2UiTest.kt y los dos JVM nuevos (CardioDistancePillTextTest.kt, WorkoutFinishEmptySessionTest.kt); CRLF en SetExecutionCard.kt, WorkoutFinishController.kt, WorkoutFinishHost.kt y SetExecutionCardUiTest.kt (verificado por conteo de bytes, sin archivos mixtos). SetExecutionCard.kt venia mezclado
(64 lineas LF); la herramienta de edicion lo dejo uniformemente CRLF (git normaliza con autocrlf=true, el diff no cambia).
