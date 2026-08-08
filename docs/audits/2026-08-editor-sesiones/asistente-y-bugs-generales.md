# Auditoría — Asistente de sesiones y barrido general del editor

- **Fecha:** 2026-08
- **Alcance:** FRENTE A (Asistente de sesiones: `AssistantSheet.kt`, wiring en `SessionEditorScreen.kt`/`Navigation.kt`, asistente en vivo en `services/workout/WorkoutPacingNotificationManager.kt`, `screens/workout/WorkoutPacingController.kt`, `WorkoutRestTimerOrchestrator.kt`) y FRENTE B (barrido general de bugs del editor: `SessionEditorCloneHelpers.kt`, `SessionEditorSessionHelpers.kt`, `SessionEditorContracts.kt`, `SessionEditorViewModelCoverClone.kt`, `SessionEditorViewModelNavigation.kt`, `SessionEditorViewModelTemplates.kt`, `SessionEditorViewModelVariants.kt`, `TrainedSessionVersionStore.kt`, `components/sheets/ClonerSaveWarmupSheets.kt`).
- **Exclusiones del barrido:** rendimiento, supersets, unilateral y reglas (las cubren otros agentes).
- **Nota:** auditoría de solo lectura; no se ha editado código.

---

# SECCIÓN 1 — ASISTENTE

## 1.1 Respuestas directas a las preguntas del encargo

1. **¿El asistente funciona de punta a punta (sus sugerencias aplican cambios reales al modelo)?**
   **Parcialmente.** La ruta viva (pestaña *Sugerencias*: ajustes por rings de volumen y por tiempo) **sí aplica cambios reales al modelo** (`updateSession` → autosave + recálculo AUGE). Sin embargo, el motor fue recortado: el reporte se devuelve con campos forzados/vacíos y toda una generación de funciones (riesgos, oportunidades, ghost cards, plantillas compatibles) quedó muerta, con handlers/UI huérfanos en tres capas (pantalla, hoja y sheets genéricas).
2. **¿Botones sin handler, TODOs, estados muertos, lógica duplicada/desactualizada?**
   Sí: callbacks recibidos y descartados explícitamente en la hoja, handlers con snackbars que nunca se disparan, callbacks muertos en `SessionEditorSheets`, ~350 líneas de lógica del engine nunca llamada desde `evaluate()`, e instrumentación de depuración con red y disco dejada en producción. No hay TODO/FIXME en los archivos auditados del frente.
3. **¿El asistente en vivo usa los valores configurados en la sesión (descansos, duración objetivo)?**
   **Sí.** El temporizador de descansos parte del `restTime` configurado por ejercicio y el countdown de sesión parte de `customTargetDurationMinutes ?: session.targetDurationMinutes`. Se detectó una inconsistencia interna de conteo de series (dedupe unilateral) documentada en A5.

## 1.2 Hallazgos

---

### [CRÍTICO] A1 — Instrumentación de depuración con red y disco activa en producción

**Archivos:**
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorDebugLog.kt:21-36, 60-86`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/AssistantSheet.kt:291-305`
- `android-native/app/src/test/java/com/example/kpkn/screens/sessioneditor/SessionEditorAuditDebugTest.kt:16-26`

**Descripción:**
Al abrir el panel de volumen del asistente (click en el encabezado "Volumen de entreno de la sesión"), `toggleVolumeExpanded()` dispara `SessionEditorDebugLog.log(hypothesisId = "H-A", ..., runId = "post-fix")` (`AssistantSheet.kt:291-305`, bloque marcado `// #region agent log`). El logger (`SessionEditorDebugLog.kt`):

1. **Red:** crea un `Thread` por llamada que hace HTTP POST a `http://127.0.0.1:7803/ingest/3bdafb84-916f-463c-b94a-538b38a08483` y `http://10.0.2.2:7803/ingest/...` (líneas 68-86, constantes `INGEST`/`INGEST_EMU` en líneas 21-24).
2. **Disco:** escribe en `/sdcard/Download/debug-9ba5f2.log` y `/storage/emulated/0/Download/debug-9ba5f2.log` (líneas 33-36), y en rutas host (líneas 27-31).
3. **Ruta de desarrollador hardcodeada:** `C:/Users/valen/Documents/KPKNFit/debug-9ba5f2.log` (línea 28).

No hay `BuildConfig.DEBUG` ni flag alguna que lo desactive; los metadatos (`hypothesisId = "H-A"`, `runId = "post-fix"`) evidencian una auditoría olvidada. Además existen logs commiteados en el repositorio (`android-native/app/debug-9ba5f2.log`, `android-native/debug-9ba5f2.log`).

**Impacto:** en dispositivos de usuarios, una interacción de UI produce escritura en almacenamiento externo (descargas), hilos extra y tráfico de red hacia un endpoint de depuración local. Fuga de comportamiento de debug a producción.

---

### [ALTO] A2 — `evaluate()` del engine devuelve un reporte recortado/hardcodeado

**Archivo:** `android-native/app/src/main/java/com/example/kpkn/domain/sessionassistant/SessionAssistantEngine.kt:57-72`

**Evidencia:**
```kotlin
return SessionAssistantReport(
    veredicto = Verdict.OPTIMAL,
    scoreEstimado = 0,
    riesgos = emptyList(),
    ajustes = ajustes + timeAjustes,
    oportunidades = emptyList(),
    tarjetasFantasma = emptyList(),
    plantillasCompatibles = emptyList(),
    ...
    resumenTexto = "",
)
```
Solo `ajustes` se calcula realmente (`generarAjustesPorRings` + `buildTimeSuggestions`, líneas 38 y 50-55). El resto del reporte llega fijo.

**Consecuencias verificadas:**
- `SessionEditorViewModel.kt:647`: `ghostExerciseCards = assistantReport?.tarjetasFantasma ?: emptyList()` → **siempre vacío** en el estado de UI (`SessionEditorModels.kt:222`).
- `SessionEditorViewModelAugeActions.kt:91-115` (`addGhostExercise`): busca sobre una lista siempre vacía → **no-op permanente**.
- **Lógica desactualizada/muerta dentro del propio engine** (nunca llamada desde `evaluate()` ni desde ningún otro sitio, verificado por búsqueda global): `generarAjustes` (l. 359+), `generarOportunidades` (l. 682+), `generarTarjetasFantasma`/`buscarTarjetasFantasma` (l. 826-883), `buscarPlantillasCompatibles` (l. 893+), `estimarImpactoDrenajeCns` (l. 885-889) y los helpers `buildRiesgos*` (~350 líneas en total).
- El test `android-native/app/src/test/java/com/example/kpkn/domain/sessionassistant/SessionAssistantEngineTest.kt:19` ("assistant report no longer exposes verdict score risks templates or ghost cards") confirma que el recorte fue **intencional**, pero la deuda (modelo con campos fijos + funciones muertas + plumbing) quedó en el binario.

---

### [ALTO] A3 — Handlers del asistente sin conectar en tres capas

**Capa hoja:**
`components/sheets/AssistantSheet.kt:147-159` — `AssistantSheet` recibe `onApplyAugeCorrection` y `onAddGhostExercise` y los descarta explícitamente:
```kotlin
// Keep unused callbacks referenced so signature stays stable for callers.
@Suppress("UNUSED_EXPRESSION")
onApplyAugeCorrection
@Suppress("UNUSED_EXPRESSION")
onAddGhostExercise
```

**Capa pantalla:**
`SessionEditorScreen.kt:706-717` (para `AssistantGlassOverlay`) y `810-821` (para `SessionEditorSheets`) pasan handlers que muestran snackbars de éxito (`"Ajuste aplicado"`, `"Ejercicio añadido a la sesión"`) que **jamás se dispararán** por estas vías, porque la hoja nunca invoca los callbacks.

**Capa sheets genéricas:**
`components/sheets/SessionEditorSheets.kt:367-369` declara `onApplyAugeCorrection`, `onAddGhostExercise`, `onApplyAssistantSuggestion` y añade `import ... AssistantSheet` (línea 263), pero **nunca los usa** — superficie duplicada muerta del asistente (el overlay se renderiza aparte en `SessionEditorScreen.kt:700-729`).


### [CONFIRMADO + bordes] A4 — La ruta viva sí muta el modelo (con dos bordes)

**Cadena funcional verificada:**
1. UI: `AssistantSuggestionsTab` (`AssistantSheet.kt:200-203`) → `AssistantSuggestionCard` con checkboxes por detalle (l. ~736-767).
2. Screen: `onApplyAssistantSuggestion` (`SessionEditorScreen.kt:718-723`).
3. VM: `applyAssistantSuggestion` / `applyAssistantDetail` (`SessionEditorViewModelAugeActions.kt:117-212`).
4. Modelo: `updateSession` (`SessionEditorViewModel.kt:517-534`) → autosave (`scheduleAutoSave`, l. 532-533) + recálculo AUGE (300 ms, l. 542-552).

Las acciones vivas (`REDUCE_SET`, `LowerRpe`, `ReduceRest`, `RemoveFailure`, `CONVERT_TO_SUPERSET`, `ConvertToDropSet`) transforman ejercicios/sets reales (`AugeActions.kt:41-74, 183-346`).

**Borde (a) — [BAJO]:** el `when (suggestion.type)` legacy (`AugeActions.kt:~168-178`) cae en `else -> Unit` silencioso para `APPLY_TEMPLATE`, `ADD_GHOST_EXERCISE`, `KEEP`, `BLOCK_ADD` (definidos en `SessionAssistantModels.kt:41-53`). Hoy inalcanzables porque esos tipos solo los emite el código muerto de A2, pero quedará expuesto si se reactiva.

**Borde (b) — [MEDIO]:** `buildTimeSuggestions` (`SessionAssistantEngine.kt:236-261`) propone supersets **solapados** (`for (i in 0 until minOf(exercises.size - 1, MAX_SUPERSET_SUGGESTIONS))` sugiere ex₀–ex₁ y ex₁–ex₂ como detalles del mismo `time-supersets`). Si el usuario marca ambos checkboxes, `applyAssistantDetail` los aplica en cadena sobre estado intermedio sin revalidar (`AugeActions.kt:135-137`) → agrupación potencialmente incoherente de supersets (el segundo `createSuperset` opera sobre el resultado del primero).

---

### [CONFIRMADO + inconsistencia] A5 — El asistente en vivo SÍ usa la configuración de la sesión

**Descansos configurados:**
`WorkoutSetRecorder.kt:519-528` arranca el temporizador con `plannedRestForKind` (derivado del `restTime` configurado en el editor) y lo ajusta con `adjustRestTimeForPace(plannedRest)`/`adjustRestTimeForPace(adaptiveRest)`. Reglas: mínimo 10 s en modo `STANDARD`, y 10 s forzados en la última serie si el descanso planificado es ≤ 0 (l. 525-526).

**Duración objetivo:**
- Hidratación: `WorkoutSessionHydrator.kt:274-275` restaura `customTargetDurationMinutes` y `targetDurationMinutes = resumedState?.customTargetDurationMinutes ?: restoredSession.targetDurationMinutes`; l. 374-378 calcula `remainingSeconds` con `elapsed` real y llama `ports.startSessionTimer(remainingSeconds)`.
- Precedencia uniforme en vivo: `WorkoutPacingController.kt:142-143` y `252-255` (`customTargetDurationMinutes ?: state.targetDurationMinutes ?: session?.targetDurationMinutes`); expuesto vía VM en `WorkoutViewModel.kt:2360-2372` y usado por la header (`WorkoutV2Body.kt:167-169`).
- Ajustes en vivo persisten en la sesión: `WorkoutViewModel.kt:2483-2498` (`persistSessionTargetDuration` → `repository.upsertSessionInProgram`).

**[MEDIO/BAJO] Inconsistencia interna de conteo:**
`WorkoutPacingController.kt:260-263` (`adjustRestTimeForPace`) usa `state.completedSets.size` **sin deduplicar lados**, mientras `evaluatePace` (l. 201-204) sí deduplica con `"${parts[0]}_${parts[1]}"`. El progreso se infla en ejercicios unilaterales y `needsHurry` (l. 263) se activa más tarde de lo previsto. (La problemática unilateral general la cubre otro agente; aquí se reporta la duplicidad de lógica local del pacing.)

**Verificado y descartado (no es bug):** el aparente NPE en `WorkoutRestTimerOrchestrator.kt:82` (`it.restModalState.isManualOverride` dentro de `it.restModalState?.copy(...)`) no existe: Kotlin smart-castea el receiver dentro de los argumentos del safe-call (propiedad `val` del mismo módulo).

**Componentes en buen estado:**
- `WorkoutPacingNotificationManager.kt`: canal dedicado silencioso (l. 62-72), chequeo de permiso `POST_NOTIFICATIONS` en Tiramisu+ (l. 47-56), notify/cancel correctos.
- Cooldown anti-spam: `WorkoutPacingController.kt:47-61` (8 min, `sameKind`); el aviso "agotado" fuerza con `force = alertChanged` (l. 224-229).
- Orquestador de descanso: estados completos en `updateState` (l. 70-100), voz contextual por kind (l. 110-123), política de fin de descanso con feedback post-ejercicio (l. 264-320).

---

### [BAJO] A6 — Estados visuales, pestañas y navegación

- Pestañas actuales del asistente: Métricas / Sugerencias / Plantillas (`AssistantSheet.kt:163-164`). La tarjeta de molestias es display-only (correcto; `buildDiscomfortByExercise`, l. 796-820). `selectedTab` con `rememberSaveable` persiste entre aperturas — sin bug.
- El asistente **no tiene ruta propia**: es la hoja `SessionEditorSheet.AUGE` dentro del editor (`Navigation.kt:28`, `SessionEditorContracts.kt:13`); se abre con `openSheet(SessionEditorSheet.AUGE)` desde el FAB (`SessionEditorScreen.kt:647`) y `BackHandler` en l. 364. El FAB "TIEMPO" (`SessionEditorScreen.kt:668`) abre la hoja RULES con tab 1 (`openRulesSheet(initialTab = 1)`, `SessionEditorViewModel.kt:748-758`), que dispara `refreshTimeCoachSuggestions()` (l. 765-787) — TimeCoach vive fuera del asistente AUGE, separación intencional (comentario l. 633).
- TimeCoach aplica cambios reales: `applyTimeCoachSuggestion` (`SessionEditorViewModel.kt:807-830`) via `TimeCoachEngine.apply(session, action)`.
- `AssistantMainTab` muestra rings, duración estimada y volumen desde `uiState.augeSummary`/`sessionTimeBreakdown` (recalculados en `SessionEditorViewModel.kt:564-650`) — coherente, sin estado muerto de UI.

## 1.3 Priorización sugerida (Asistente)

1. **A1 (CRÍTICO):** eliminar o proteger con flag `SessionEditorDebugLog` y borrar los `debug-9ba5f2.log` commiteados.
2. **A2 + A3 (ALTO):** decidir el destino del asistente — o se eliminan engine muerto + handlers + parámetros en las 3 capas, o se reconectan/re-implementan.
3. **A4b (MEDIO):** superset suggestions solapadas — revalidar detalles tras cada aplicación.

---


# SECCIÓN 2 — BARRIDO GENERAL (editor de sesiones)

## 2.1 Hallazgos

---

### [ALTO] B1 — Feature de variantes B/C/D rota: la edición nunca alcanza la variante

**Archivos:**
- `SessionEditorScreen.kt:147` — `val session = uiState.session` (la pantalla **siempre** renderiza la sesión A).
- `SessionEditorModels.kt:233-238` — getter `activeVariantSession` **sin consumidor**.
- `SessionEditorViewModelVariants.kt:76-108, 111-135, 141-159` — create/delete/switch/commit.
- `SessionEditorViewModel.kt:517-534` — `updateSession` muta siempre `state.session` (A).

**Descripción:**
El wiring de UI existe (`SessionEditorScreen.kt:488-495`, `SessionContextNavigator.kt:351-356`), pero:
1. `switchVariant` (`Variants.kt:133-135`) solo cambia el flag `activeVariant`; no intercambia contenido.
2. La pantalla nunca renderiza `session?.sessionB/C/D`.
3. Todas las ediciones caen sobre la sesión A base.
4. `commitActiveVariantChanges` (`Variants.kt:145-157`) para B/C/D copia `sessionB → sessionB` (**no-op literal**).

Resultado: el usuario "conmuta" a la variante B, ve y edita A sin saberlo, y B queda congelada como copia del momento de su creación. **Estados muertos end-to-end** (`activeVariant`, `availableVariants`, `onCreateVariant/onDeleteVariant/onSwitchVariant`).

**Agravante (ids duplicados):** `createVariant` (`Variants.kt:87-91`) clona con `base.copy(id = ...)` **sin regenerar ids** de ejercicios/sets/parts → ids duplicados entre A y B dentro del mismo objeto persistido (contrasta con el clonador, que sí los regenera: `SessionEditorCloneHelpers.kt:97-108`).

---

### [ALTO] B2 — `applyTemplateInternal` aplica la plantilla dos veces

**Archivo:** `SessionEditorViewModelTemplates.kt:102-119`

**Evidencia:** la función lanza `viewModelScope.launch { ... updateSession { result }; updateUi { ... } }` (l. 102-108) y, **sin `return` ni `else`**, ejecuta a continuación el "fallback for tests" síncrono (l. 109-119) con el mismo `SessionTemplateEngine.applyTemplate` + `updateSession` + `updateUi`.

**Impacto:** doble aplicación de plantilla, doble autosave y doble recálculo AUGE por cada apply (incluye `selectTemplate` en sesión vacía y `confirmTemplateApply`). Si `SessionTemplateEngine.applyTemplate` genera UUIDs nuevos, el resultado asíncrono (ids distintos) pisa al síncrono → keys inestables/parpadeo. El comentario "fallback for tests without scope - keep original sync path for now" delata el olvido.

---

### [MEDIO] B3 — Hoja WARMUP omite ejercicios con exactamente 1 aproximación

**Archivo:** `components/sheets/ClonerSaveWarmupSheets.kt:428`

**Evidencia:** `val exercisesWithWarmup = currentSession.allExercises().filter { it.warmupSets.size > 1 }`. El editor soporta una única aproximación (botón "Agregar aproximación" parte de 40 %, l. 597-605; el guardado persiste cualquier lista vía `onSave` → `SessionEditorScreen.kt:829-833` → `SessionEditorViewModelStructure.kt:592`).

**Impacto:** un ejercicio con exactamente 1 set de entrada en calor desaparece del editor WARMUP de sesión → inconsistencia UI vs modelo (el set existe pero no es editable desde esa hoja).

---

### [MEDIO] B4 — Persistencia pesada fuera de dispatcher en el camino de navegación

**Archivos:**
- `SessionEditorViewModelNavigation.kt:304-312` (`persistRecoverableSession` → `persistDraft`)
- `SessionEditorViewModel.kt:215-239` (`persistDraft`: `draftJson.encodeToString(payload)` + `draftPrefs.edit().apply()` en el hilo llamante)

**Descripción:** `scheduleAutoSave` sí envuelve en `Dispatchers.IO` (`SessionEditorViewModel.kt:135-144`), pero el camino de cambio de sesión (`requestSessionSwitch` l. 48-63, `createSessionForDay` l. 78+) llama `persistRecoverableSession()` desde UI: serialización JSON de una `Session` completa + SharedPreferences en el hilo principal. Viola la regla del proyecto (trabajo bloqueante en `Dispatchers.IO`; los `.apply()` son asíncronos pero el `encodeToString` no).


---

### [MEDIO] B5 — Código muerto y dedupe defectuoso

- `SessionEditorViewModelNavigation.kt:425-455`: `appendDraftSnapshot` y `buildDraftSnapshot` **definidas y nunca llamadas** (verificado por búsqueda global de llamadas; el historial local se puebla solo desde `TrainedSessionVersionStore` → `WorkoutFinishController.kt:293-301`). Además, su dedupe (`last.session == snapshot.session`, l. 430) sería inútil porque `Session` incluye `lastModifiedAtMs`, que cambia en cada edición (`SessionEditorViewModel.kt:663`).
- `SessionEditorViewModelCoverClone.kt:75-100`: `exportToSession` (`@Deprecated`) e `importFromSourceSessionLegacy` (`@Deprecated`) sin llamadas.
- `SessionEditorViewModelTemplates.kt:74-76, 99-101`: "guardas" de plantillas grandes (>12 ejercicios) solo hacen `Log.w` sin efecto — warnings muertos que sugieren un límite inexistente.

---

### [MEDIO] B6 — Clonado REPLACE conserva variantes/competición del destino con contenido nuevo

**Archivo:** `SessionEditorCloneHelpers.kt:150-164` (`createSessionFromPayload` con `preserveIdentityFrom`)

**Descripción:** al reemplazar un día existente se hace `identity.copy(exercises = payload.looseExercises, parts = payload.parts, ...)` conservando `sessionB/C/D` del destino (variantes con los ejercicios **viejos**), `trainingBackup`, `isMeetDay` y metadatos de competición. Tras el REPLACE, la variante B del destino queda desincronizada respecto a la nueva variante A.

**Lo que sí está bien en el clonador:** ids de ejercicio/sets/warmup regenerados (l. 96-108), sin duplicación de sueltos (l. 60-65), superset groups remapeados y filtrados a ≥2 miembros (l. 68-83), límites de 3 targets y validaciones (`SessionEditorViewModelCoverClone.kt:100-131`, `ClonerSaveWarmupSheets.kt:82-83, 90-140`).

---

### [MEDIO] B7 — Propagación a mesociclo clona sin regenerar ids estructurales

**Archivo:** `SessionEditorViewModelNavigation.kt:496-524` (`applySessionToMesocycle`)

**Evidencia:**
```kotlin
val cloneForWeek = if (week.id == state.weekId) draft else draft.copy(id = UUID.randomUUID().toString())
```
Solo cambia el id de sesión: parts/ejercicios/sets/supersetGroups comparten ids con la semana origen en el resto de semanas. Además el matching por `(dayOfWeek + isMainSession)` (l. 509-516) puede sobrescribir la sesión equivocada si el draft cambió de rol principal desde su creación.

---

### [BAJO] B8 — Inconsistencias menores de estado

- `SessionEditorViewModelVariants.kt:5-17` (`setTargetDuration`): usa `updateUi` directo en lugar de `updateSession` → no actualiza `lastModifiedAtMs` (sí marca `hasUnsavedChanges` y programa autosave recalc manualmente). Además resetea `dismissedTimeCoachIds = emptySet()` re-sirviendo sugerencias descartadas al cambiar la duración (sin documentar).
- `SessionEditorViewModelStructure.kt:110-124`: `toggleExerciseSelection`/`setExerciseSelection`/`clearExerciseSelection` disparan `scheduleAutoSave()` aunque la selección no es contenido de sesión (autosave innecesario tras cada selección).
- `ClonerSaveWarmupSheets.kt:98-99, 121-124`: claves seleccionadas del clonador en `rememberSaveable` no se purgan si cambian las opciones (`cloneDayOptions`); se filtran al aplicar — impacto bajo.
- `SessionEditorViewModelVariants.kt:76-108` (`createVariant`/`deleteVariant`) y `distributeTargetDurationAcrossParts` (l. 44-66) usan `updateUi` con `copy(session=...)` sin pasar por `updateSession` → `dayOfWeek` no se resincroniza y `lastModifiedAtMs` queda obsoleto (los cambios sí autosalvan).


---

### [BAJO] B9 — Comprobaciones que salieron limpias (sin hallazgo)

- **Invariante de exposición de estado respetado:** `SessionEditorViewModel.kt:167-168` expone `_uiState.asStateFlow()`; ningún `MutableStateFlow` público en los archivos del barrido (también OK en `WorkoutPacingController.kt:28-29`).
- **Autosave con debounce correcto:** 2000 ms (`SessionEditorViewModel.kt:135-144`), recálculo AUGE con debounce 300 ms (l. 542-552); `setAutoSaveEnabled` correcto (l. 146-149).
- **Clonador de sesiones:** sin TODO/FIXME; `buildCloneDayOptions`/`buildCloneSourceOptions` globales consistentes (índice de mesociclo global correcto tras el fix de `SessionEditorCloneHelpers.kt:231-296`); `normalizeMainSessions` (una principal por día) correcto (`SessionEditorViewModelNavigation.kt:526-540`); tras guardar se reconstruyen `cloneDayOptions`/`cloneSourceOptions` (l. 400-402).
- **`TrainedSessionVersionStore.kt`:** escritura vía `SharedPreferences.apply()`, dedupe estructural con `sessionForVersioning` (l. 92-106) bien resuelto (elimina campos cosméticos/runtime), cap a 20 versiones (l. 67-79); invocado al finalizar entreno (`WorkoutFinishController.kt:293-301`).
- **`onWarmupSave`** resuelve el partId y persiste en ambas ubicaciones (sueltos y parts): `SessionEditorScreen.kt:829-833` → `SessionEditorViewModelStructure.kt:592` → `updateExercise` (l. 383-392).
- **Sin TODO/FIXME** en ninguno de los archivos del FRENTE B (verificado con búsqueda dedicada).
- **Navegación del editor:** ruta única en `navigation/Navigation.kt:28` (`session-editor/{programId}/{sessionId}?weekId=...&configureCompetition=...`); el asistente no es ruta separada (correcto).

---

## 2.2 Priorización sugerida (Barrido general)

1. **B1 (ALTO):** variantes B/C/D — o se renderiza `activeVariantSession` con swap de contenido (y se regeneran ids al crear), o se oculta la UI de variantes.
2. **B2 (ALTO):** quitar el "fallback" síncrono en `applyTemplateInternal` (falta un `return` dentro del `launch`).
3. **B3, B5, B6, B7 (MEDIO):** filtro warmup, código muerto + dedupe, REPLACE conservando variantes viejas, propagación sin regenerar ids.
4. **B8 (BAJO):** alinear updates con `updateSession` y limpiar autosaves innecesarios.

---

## 2.3 Relación con el Frente A

- La instrumentación **A1** (CRÍTICO) también afecta al barrido por los logs commiteados (`android-native/app/debug-9ba5f2.log`, `android-native/debug-9ba5f2.log`) y el test asociado (`SessionEditorAuditDebugTest.kt:16-26`).
- Los callbacks muertos **A3** se originan en el mismo cableado que el barrido (handlers duplicados en `SessionEditorScreen.kt:706-717` vs `810-821`).

---
