# PLAN VOZ POTENCIADA — KPKN Fit (Android nativo)

> **Objetivo**: (1) selector de modo de captura Modo Música / Modo Manos Libres con diálogo obligatorio y pre-activación desde la tarjeta de hoy; (2) correcciones residuales del modo de voz; (3) vocabulario mucho más flexible; (4) clarificación guiada inteligente; (5) configuración avanzada (auto-relleno de sugerencias, frases de intensidad personalizadas); (6) asistente proactivo "ojos del usuario" y estructura por voz — todo dentro de los márgenes de estabilidad de la función básica: **registrar series**.
> **Ejecutor**: otro agente de IA. Este documento es la fuente de verdad: rutas absolutas, anclas de código, snippets objetivo y tests ya definidos. **No inventes alternativas**: si algo aquí contradice el código, gana el código y documenta la desviación en el resumen de la fase.
> **Alcance**: SOLO `android-native/`. No tocar iOS, backend, AUGE (cálculo), Room (entidades/migraciones), datasets ni assets.

---

## 0. REGLAS DE EJECUCIÓN (obligatorias)

1. **NO hacer `git commit`, `git push`, `git reset` ni ninguna mutación de git.** El usuario revisa después.
2. Raíz del repo: `C:\Users\valen\Documents\KPKNFit`. Comandos Gradle con `workdir = C:\Users\valen\Documents\KPKNFit\android-native`.
3. Las **líneas citadas son anclas referenciales** del working tree actual (con los fixes de voz ya aplicados). Localiza por el snippet `// ANCLA:`, no por número.
4. Validar al final de CADA fase, en este orden:
   - `gradlew.bat :app:compileBaseDebugKotlin --console=plain`
   - `gradlew.bat :app:testBaseDebugUnitTest --console=plain`
5. **Regla de oro del vocabulario**: toda frase/keyword nueva entra A LA VEZ en: parser (`WorkoutVoiceInput.kt` o `WorkoutVoiceCommandParser.kt`), gramática (`grammarTokensForStage` / `defaultNumericGrammarTokens` / `WorkoutVoiceGrammarBuilder`), lexicon (`WorkoutVoiceGrammarLexicon`), scorer (`GYM_SIGNAL_WORDS` en `WorkoutVoiceHypothesisScorer`) y, si aplica, `WorkoutVoiceMishearingCorrections` (`DOMAIN_LEXICON`/`TOKEN_CORRECTIONS`/`STOPWORDS`). El test de paridad `generatedCommandAliasesRemainParseable` (`WorkoutVoiceGrammarBuilderTest.kt` ≈L96-138) debe seguir verde.
6. **Estabilidad**: `CONFIRM_WAIT` sigue SIN gramática numérica; las clarificaciones con respuesta numérica se resuelven en `LISTENING`. El recognizer se cachea LRU=2 por hash de gramática: no inflar la gramática sin medida (los productos cruzados ya generan ~400 frases).
7. No leer/imprimir `.env`, keystores ni tokens. No ejecutar `scripts/telegramBot.js`. No regenerar datasets/assets.
8. Estilo del repo: Kotlin, DI manual, lógica de decisión pura y testeable (sin `android.*` en lógica nueva), corrutinas IO para bloqueo, `StateFlow` de solo lectura.
9. Al terminar cada fase: resumen de archivos tocados y desviaciones (si las hubo) en el chat.

## 1. DECISIONES DE PRODUCTO BLOQUEADAS (del usuario — no reabrir)

| # | Decisión |
|---|---|
| D1 | El aviso hablado de **60 s de descanso se ELIMINA**; se conserva solo el de 10 s. |
| D2 | Frases verbales de intensidad: **"dándolo todo" / "lo di todo" → al fallo** (`reachedFailure=true`); **"quedé muy cansado" / "sin energía" / "agotado" → RPE 9 SIN fallo**. |
| D3 | **Sin modo por defecto**: la primera activación de voz exige elegir modo en el diálogo (obligatorio, copy no técnico ya redactado en §4.3). Después se recuerda la última elección. |
| D4 | El modo MÚSICA sacrifica el mic de auriculares (usa el del teléfono, música intacta); MANOS LIBRES es el comportamiento actual (mic de auriculares, música degradada). No prometer en UI nada distinto a esta física. |
| D5 | Reemplazo de ejercicio por voz y reorder completo dictado quedan **fuera** de este plan (alto riesgo). |

---

## 2. FASE 0 — CORRECCIONES RESIDUALES (bajo riesgo)

### 2.1 RPE→fallo: parchar el tercer foco (el único que queda)

El working tree ya tiene la guarda `intensityKind != null → reachedFailure=false` en `WorkoutVoiceController.handleRegisterSet` (≈L1394-1402) y en `WorkoutVoiceCommandHandler` (≈L868-870 y ≈L928-930). **Falta el flujo one-shot:**

**Archivo**: `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutVoiceCommandHandler.kt`

**ANCLA**: `confirmVoiceInput` (≈L287), patrón:
```kotlin
reachedFailure = if (WorkoutVoiceField.FAILURE in interpretation.fields) {
    interpretation.reachedFailure
} else {
    draft?.reachedFailure == true
}
```

**Cambio** (idéntico al de los otros dos focos):
```kotlin
reachedFailure = when {
    interpretation.intensityKind != null -> false // "…RPE 9" no es "al fallo"
    WorkoutVoiceField.FAILURE in interpretation.fields -> interpretation.reachedFailure
    else -> draft?.reachedFailure == true
}
```

**Test** (nuevo, en `app/src/test/java/com/example/kpkn/screens/workout/`, archivo `WorkoutVoiceRpeFailureGuardTest.kt` o dentro del test del handler existente): interpretación con `intensityKind=RPE, intensityValue=9.0, FAILURE ∉ fields` + draft `reachedFailure=true` → resultado `reachedFailure=false` e `intensityText="9"`. Extraer la decisión a función pura si hace falta para testearla sin Android (mismo patrón que las demás funciones puras del repo).

### 2.2 Eliminar el aviso de 60 s de descanso (D1)

**Archivo**: `android-native/app/src/main/java/com/example/kpkn/services/workout/WorkoutVoiceController.kt`

**ANCLA**: `onRestCountdownTick` (≈L361-372):
```kotlin
if (remainingSeconds == 60 && !announcedOneMinuteForRest && allows(VoiceAnnouncementKind.COMPLETE)) {
    announcedOneMinuteForRest = true
    ttsManager.speakOneMinuteLeft()
}
```

**Cambios**:
1. Eliminar ese bloque `== 60` completo (conservar el de `== 10`).
2. Eliminar la var `announcedOneMinuteForRest` (≈L97) y sus resets (≈L322-324, ≈L336-337, ≈L349-350).
3. En `WorkoutTtsManager.kt` eliminar `speakOneMinuteLeft()` (≈L191-193) y su string si quedó huérfano.
4. Actualizar el comentario de `VoiceAnnouncementKind.COMPLETE` en `WorkoutVoiceAutoConfirmGate.kt` (≈L7-14) para que ya no mencione el aviso del minuto.

**Test**: en el test de reglas de descanso o uno nuevo JVM puro: con el tick en 60 no se invoca TTS; con tick en 10 sí. (Si el tick no es testeable puro, cubrir con test de la rama extraída a función pura.)

### 2.3 Cue de SkipRest con confirmación y número de serie

Hoy: `WorkoutVoiceCommandHandler` rama `SkipRest` (≈L475-478) llama `ports.stopRestTimer()` + `speakCurrentStepAnnouncementIfEnabled(prefix="Descanso omitido. ")` (≈L618-656) → gated ESSENTIAL y sin número de serie (en el trazo físico quedó en silencio).

**Cambio definido**:
1. Nuevo método en `WorkoutTtsManager.kt`:
```kotlin
fun speakRestSkipped(setIndex: Int, totalSets: Int, exerciseName: String) {
    speak("Descanso omitido. Serie ${setIndex + 1} de $totalSets, $exerciseName.")
}
```
2. Nuevo path CRITICAL en `WorkoutVoiceController` (sin gate de verbosidad, igual que las confirmaciones de serie):
```kotlin
fun speakRestSkippedAnnouncement(setIndex: Int, totalSets: Int, exerciseName: String) {
    if (!sessionWanted) return
    speakWhilePaused(priority = WorkoutSpeechPriority.HIGH) {
        ttsManager.speakRestSkipped(setIndex, totalSets, exerciseName)
    }
}
```
3. En el `Ports`/wiring del handler (`WorkoutViewModel.kt` donde se construyen los ports de `WorkoutVoiceCommandHandler`): nuevo port `speakRestSkipped()` que lea del `WorkoutUiState` actual `setIndex`/`totalSets`/nombre del ejercicio en curso y llame al método del controller del punto 2. Llamarlo desde la rama `SkipRest` del handler **después** de `ports.stopRestTimer()`, reemplazando el `speakCurrentStepAnnouncementIfEnabled(prefix="Descanso omitido. ")` para este comando.
4. Edge: si el ejercicio está en superset o es unilateral, NO complicar el texto: usar setIndex/totalSets del ejercicio actual tal como ya los expone el estado.

**Verificación física** (Fase 7): tras dictar "omitir descanso" debe aparecer `voice_phase TTS START` en el JSONL y oírse la frase completa.

### 2.4 Menores (baratos, mismo PR de fase)

1. **"sugerido" a secas**: añadir `"sugerido"` a `USE_ADAPTIVE_REST` en `WorkoutVoiceCommandParser.kt` (≈L102) + gramática + lexicon. Asegurar que la rama rest-aware (`parseRestAwareCommand` ≈L453-468) lo evalúe ANTES que `SUGGEST_WEIGHT` (≈L40) cuando hay descanso activo. Tests: con rest activo → `UseAdaptiveRest`; sin rest → `SuggestWeight`.
2. **`audio_mode_changed` solo si cambió**: en `WorkoutVoiceMicRouter.applyCommunicationAudioMode()` y `restoreAudioModeIfNeeded()` (agregados en la pasada anterior), emitir el evento SOLO si `current != previous`; y en restore, restaurar solo si `am.mode == AudioManager.MODE_IN_COMMUNICATION` (no pisar el modo de otra app):
```kotlin
val before = am.mode
am.mode = AudioManager.MODE_IN_COMMUNICATION
val after = am.mode
if (after != before) { /* log */ }
```
3. **"Sí" fantasma post-confirmación**: NO tocar lógica en esta fase (los dos eventos observados parsearon como `Unknown` inocuo). Dejar documentado en el resumen; se reevalúa con los trazos de la Fase 7.

**Criterio de done Fase 0**: compila; suite verde (incl. tests nuevos 2.1, 2.2, 2.4.1); sin cambios de comportamiento fuera de lo listado.

---

## 3. FASE 1 — SELECTOR DE MODO DE CAPTURA (Modo Música / Modo Manos Libres)

### 3.1 Modelo persistido (sin migración Room)

**Archivo**: `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt`

1. Nuevo enum junto a `VoiceInputMode` (≈L113):
```kotlin
enum class VoiceCaptureMode { HANDS_FREE, MUSIC }
```
2. Nuevos campos en `Settings` (≈L28-33, zona de voz):
```kotlin
val voiceCaptureMode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
val hasChosenVoiceCaptureMode: Boolean = false,
val voiceArmForNextSession: Boolean = false,
```
`Settings` se persiste como blob JSON en `SettingsEntity(rowId, data)` con `ignoreUnknownKeys = true` (`Entities.kt` ≈L13, ≈L85-88) ⇒ **sin migración, Room queda v20**.

### 3.2 Semántica del modo en el subsistema de voz

**Archivo**: `android-native/app/src/main/java/com/example/kpkn/services/workout/WorkoutVoiceMicRouter.kt`

1. Nueva propiedad:
```kotlin
/** En modo MÚSICA no se solicita ruta de comunicación: el SCO queda libre y la música intacta. */
var externalRouteEnabled: Boolean = true
```
2. En `requestCommunicationRoute()`: guarda al inicio:
```kotlin
if (!externalRouteEnabled) {
    logRouteRequest(requested = null, accepted = true, reason = "music_mode_suppressed")
    _activeRouteLabel.value = "phone"
    return
}
```
3. `hasExternalRouteRequested()`: `return externalRouteEnabled && communicationDeviceRequested`.
4. En `applyPreferredDeviceTo()`: si `!externalRouteEnabled` → `runCatching { record.setPreferredDevice(null) }`, `_activeRouteLabel.value = "phone"`, `lastPreferredId = null`, y `return`.

Con esto, en modo MÚSICA: `WorkoutVoiceAudioSourcePolicy.select(..., externalCommunicationRouteActive=false)` ya devuelve `VOICE_RECOGNITION` y el engine no espera ruta (Fases 2-3 del plan anterior, ya implementadas).

### 3.3 Propagación del modo (el engine vive en el proceso `:voice` vía AIDL)

El único camino de producción es `WorkoutRemoteVoiceEngineClient` (`WorkoutVoiceRuntime.speechEngine()` ≈L60-63). El modo debe cruzar el AIDL:

1. **AIDL** — `android-native/app/src/main/aidl/com/example/kpkn/services/workout/IWorkoutVoiceEngineService.aidl`:
```aidl
void start(long generation, boolean holdMicRouteAcrossPause, String grammarJson, int stageOrdinal, int noiseProfileOrdinal, int captureModeOrdinal);
void updateCaptureMode(long generation, int captureModeOrdinal);
```
2. **Puerto** — `WorkoutVoiceEnginePort.kt`: añadir
```kotlin
fun updateCaptureMode(mode: VoiceCaptureMode)
```
y cambiar `start` a `fun start(scope: CoroutineScope, holdMicRouteAcrossPause: Boolean = true, captureMode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE)`.
3. **Cliente** — `WorkoutRemoteVoiceEngineClient.kt`: nueva var `private var captureMode = VoiceCaptureMode.HANDS_FREE`; `updateCaptureMode` la actualiza y, si `activeRequested && !failedTerminal`, llama `remote?.updateCaptureMode(generation, mode.ordinal)` (con `runCatching {}.onFailure { handleBinderDeath() }`, patrón existente); `sendStart()` pasa `captureMode.ordinal` como último arg.
4. **Servicio** — `WorkoutVoiceForegroundService.kt` Stub: `start(...)`: antes de `engine.start(...)`, `engine.updateCaptureMode(VoiceCaptureMode.entries.getOrElse(captureModeOrdinal) { VoiceCaptureMode.HANDS_FREE })`. Nuevo `updateCaptureMode(generation, captureModeOrdinal)`: si `generation == clientGeneration` → `engine.updateCaptureMode(...)`.
5. **Engine** — `WorkoutContinuousVoiceEngine.kt`:
   - `override fun updateCaptureMode(mode: VoiceCaptureMode)` → `commands.trySend(EngineCommand.UpdateCaptureMode(generationCounter.get(), mode))`.
   - En `start(...)`: recordar el modo actual (`captureMode` param nuevo con default) y aplicarlo en el handler `Start` antes de `micRouter.acquire(...)`: `micRouter.externalRouteEnabled = (mode == VoiceCaptureMode.HANDS_FREE)`.
   - Nuevo comando `data class UpdateCaptureMode(val generation: Long, val mode: VoiceCaptureMode) : EngineCommand` y su handler EN EL ACTOR (invariante actor único):
```kotlin
is EngineCommand.UpdateCaptureMode -> {
    if (command.generation != actorGeneration) return
    val enableExternal = command.mode == VoiceCaptureMode.HANDS_FREE
    if (micRouter.externalRouteEnabled == enableExternal) return
    micRouter.externalRouteEnabled = enableExternal
    WorkoutVoiceDiagnosticLogger.event(
        "voice_capture_mode_changed",
        mapOf("mode" to command.mode.name) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
    )
    if (!running) return
    if (enableExternal) {
        micRouter.acquire(WorkoutVoiceMicRouter.RouteMode.CONTINUOUS_VOICE_FIRST)
    } else {
        micRouter.release()
    }
    releaseRecord()
    resetRecovery(clockMs(), immediate = true)
    _captureState.value = VoiceCaptureState.RECONNECTING
}
```
   El loop reabre el record solo: en MÚSICA con `VOICE_RECOGNITION`/mic interno; en MANOS LIBRES con await de ruta + `VOICE_COMMUNICATION` (ya implementado).

6. **Controller** — `WorkoutVoiceController.kt`: nuevo `captureModeProvider: (() -> VoiceCaptureMode)? = null` (junto a `inputModeProvider` ≈L94). En `startListening()`/`resumeListening()` pasar `captureMode = captureModeProvider?.invoke() ?: VoiceCaptureMode.HANDS_FREE` al `continuousEngine.start(...)`. Nuevo método público:
```kotlin
fun setCaptureMode(mode: VoiceCaptureMode) {
    continuousEngine.updateCaptureMode(mode)
}
```
7. **ViewModel** — `WorkoutViewModel.kt` (zona de providers ≈L597-599):
```kotlin
voiceController.captureModeProvider = { repository.settings.value.voiceCaptureMode }
```
y nuevo:
```kotlin
fun setVoiceCaptureMode(mode: VoiceCaptureMode) {
    repository.updateSettings { it.copy(voiceCaptureMode = mode, hasChosenVoiceCaptureMode = true) }
    if (voiceController.isEnabled()) voiceController.setCaptureMode(mode)
}
```

### 3.4 Diálogo obligatorio de primera elección (D3, copy cerrado)

**Nuevo archivo**: `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/VoiceCaptureModeDialog.kt`

Patrón a copiar: `WelcomeTourDialog` (`screens/programdetail/components/WelcomeTourDialog.kt` ≈L100-234) sobre `KpknGlassDialog` (`ui/components/KpknGlassDialog.kt` ≈L29-85). Contenido: título "¿Cómo quieres entrenar hoy?" + dos tarjetas seleccionables + botón "Empezar" (deshabilitado hasta elegir). **Textos exactos (no editar salvo tildes)**:

- Tarjeta 1 — título **"Modo Música"**:
  "Sabemos que entrenar con música te encanta, por eso diseñamos este modo donde tus canciones favoritas no se escucharán en baja calidad como si estuvieras en una llamada; solo necesitas acercar tu teléfono para registrar las series, sin necesidad de desbloquearlo. Así entrenas sin distracciones y disfrutando de tu playlist favorita."
- Tarjeta 2 — título **"Modo Manos Libres"**:
  "Si eres de esos usuarios que quiere la máxima concentración en sus entrenamientos y olvidarse del celular durante la sesión, puedes registrar tus entrenamientos usando directamente el micrófono de tus auriculares, sin necesidad de sacar tu teléfono. Esto degradará la calidad de tu música, pero será tu forma favorita de registrar tus marcas si quieres máxima libertad y cero distracciones."

**Comportamiento**: `onDismissRequest = {}` (no cancelable la primera vez; en reaperturas posteriores desde Ajustes sí es cancelable). Al confirmar: `viewModel.setVoiceCaptureMode(elegido)` + continuar el flujo de enable que lo invocó.

**Gating** — `WorkoutViewModel.enableVoice()` (≈L1253-1270): al inicio:
```kotlin
if (!repository.settings.value.hasChosenVoiceCaptureMode) {
    _uiState.update { it.copy(showVoiceCaptureModeDialog = true) }
    return
}
```
Nuevo campo en `WorkoutUiState` (`WorkoutUiModels.kt`): `val showVoiceCaptureModeDialog: Boolean = false`. `WorkoutScreen.kt` lo renderiza; onConfirm: limpiar el flag + `enableVoice()` de nuevo (ya con modo elegido). Eventos JSONL: `voice_mode_dialog_shown`, `voice_mode_dialog_chosen` (con `mode`).

### 3.5 Switch en el hero header (solo con voz activa)

**Archivo**: `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutHeaderBar.kt`

**ANCLA**: Row superior `SpaceBetween` (≈L303-307); el lado derecho está libre (solo `Column(Modifier.weight(1f))` ≈L308).

**Cambio**: nuevos parámetros del composable (con defaults para no romper otros callers):
```kotlin
voiceCaptureMode: VoiceCaptureMode? = null,
onVoiceCaptureModeChange: ((VoiceCaptureMode) -> Unit)? = null,
```
En el Row, tras la columna del título, si `voiceCaptureMode != null && onVoiceCaptureModeChange != null`: `SingleChoiceSegmentedButtonRow` con dos `SegmentedButton` **solo texto** (sin iconos — el proyecto no garantiza material-icons-extended): "Manos libres" y "Música", `selected` según el modo, `onClick = { onVoiceCaptureModeChange(...) }`.

**Wiring**: `WorkoutV2Body.kt` (call site ≈L150-176): pasar desde `WorkoutScreen.kt` — `settings.voiceCaptureMode.takeIf { uiState.voiceSessionEnabled }` y `viewModel::setVoiceCaptureMode`. `settings` ya se colecta en `WorkoutScreen.kt` ≈L278.

### 3.6 Pre-activación desde la tarjeta de hoy

**Archivo**: `android-native/app/src/main/java/com/example/kpkn/screens/home/HomeSessionSection.kt`

**ANCLA**: `SessionCard` (≈L130-386); insertar una fila nueva entre el header Box (fin ≈L278) y el strip de músculos (≈L280).

**Contenido de la fila**: `SettingsSwitchItem`-like (patrón `screens/settings/components/SettingsListItems.kt` ≈L56-108) con texto **"Entrenar con Comandos de Voz"**; al activarlo, debajo, `SingleChoiceSegmentedButtonRow` con "Manos libres" / "Música" (selección = `settings.voiceCaptureMode`). Reglas:
- Switch ON → `repository.updateSettings { copy(voiceArmForNextSession = true) }`; OFF → `false`.
- Si ON y `!hasChosenVoiceCaptureMode` → abrir el MISMO diálogo de §3.4 (elevar su estado a `HomeViewModel`/pantalla o navegar con flag; definir `showVoiceCaptureModeDialog` también en `HomeUiState` y renderizar el mismo composable).
- El segmented de la tarjeta actualiza `voiceCaptureMode` (y `hasChosenVoiceCaptureMode = true`) vía el mismo método (extraer `setVoiceCaptureMode` a nivel de repository helper o duplicar la escritura de settings — NO crear lógica nueva de voz en Home).

**Consumo de la intención** — `WorkoutScreen.kt`: nuevo `LaunchedEffect(uiState.voiceArmForNextSessionPending)`:
1. El flag llega en `settings`; el ViewModel, al hidratar la sesión (`WorkoutSessionHydrator` ≈L316-349 completa), expone `voiceArmForNextSessionPending = settings.voiceArmForNextSession` en `WorkoutUiState` y limpia: `repository.updateSettings { copy(voiceArmForNextSession = false) }` (una sola vez, guard con `rememberSaveable`/flag interno).
2. En el `LaunchedEffect`: si `hasChosenVoiceCaptureMode == false` → mostrar el diálogo (§3.4) y al confirmar → lanzar el flujo de permiso + enable EXISTENTE (el mismo launcher de `WorkoutScreen.kt` ≈L209-220/:698-713: si `RECORD_AUDIO` concedido → `viewModel.enableVoice()`; si no → `launcher.launch(...)` y en callback de grant → `enableVoice()`).
3. Si el permiso se deniega: dejar la voz apagada y mostrar el estado normal (no insistir).

### 3.7 Ajustes de voz

**Archivo**: `android-native/app/src/main/java/com/example/kpkn/screens/settings/SettingsTrainingScreen.kt` (sección junto a "Modo de micrófono" ≈L183-194): `SettingsSegmentedButtonItem` "Modo de captura de voz" con las dos opciones (escribe `voiceCaptureMode` + `hasChosenVoiceCaptureMode=true`) + botón "Ver explicación" que reabre el diálogo (§3.4, versión cancelable). Enganchar el reset en `SettingsViewModel.resetOnboarding()` (≈L125-132): `hasChosenVoiceCaptureMode = false`.

### 3.8 Tests Fase 1

- `WorkoutVoiceMicRouter` puro: con `externalRouteEnabled=false` → `hasExternalRouteRequested()==false` y request suprimido (test de la decisión; el router usa AudioManager — cubrir la rama pura o con Robolectric `ShadowAudioManager`, disponible).
- `WorkoutVoiceAudioSourcePolicyTest`: `select(sdk=34, external=false) == VOICE_RECOGNITION` (ya existe, conservar).
- Settings: encode/decode `Settings` con los campos nuevos (kotlinx.serialization, JVM puro).
- `WorkoutContinuousVoiceEngine`: contrato puro del handler `UpdateCaptureMode` si es extraíble a función pura (enable/disable external); si no, cubrir en el router.

**Criterio de done Fase 1**: compila; suite verde; en físico (Fase 7): modo MÚSICA no degrada música y registra con teléfono cerca bloqueado; MANOS LIBRES = baseline actual; cambio en caliente por el header re-rutea sin reiniciar la sesión (`voice_capture_mode_changed` en JSONL).

---

## 4. FASE 2 — VOCABULARIO FLEXIBLE (Kotlin puro, riesgo bajo)

> Aplica la **regla de oro** (§0.5) en CADA ítem. Archivos base: parser de series `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutVoiceInput.kt`; parser de comandos `.../services/workout/WorkoutVoiceCommandParser.kt`; gramática `.../services/workout/WorkoutVoiceGrammarBuilder.kt` + `WorkoutVoiceGrammarLexicon.kt`; correcciones `.../services/workout/WorkoutVoiceMishearingCorrections.kt`; scorer `.../services/workout/WorkoutVoiceHypothesisScorer.kt`.

### 4.1 Conector "equis" (formato compacto "54x5")

1. `WorkoutVoiceInput.kt` — `normalizeWorkoutVoiceTranscriptString` (≈L336-347, donde ya se reescribe "erre pe e"→"rpe"): añadir reescritura `\bequis\b` → `"x"` (después de las reescrituras de letras). `CONNECTOR_KEYWORDS` (≈L576) ya contiene `"x"`.
2. Gramática: añadir `"equis"` a `defaultNumericGrammarTokens` (`WorkoutVoiceCommandParser.kt` ≈L194-214).
3. Lexicon/scorer/correcciones: `"equis"` en `GYM_SIGNAL_WORDS` (≈L23-31) y `DOMAIN_LEXICON` (≈L21) para que no lo "corrija".
4. **Tests** (`WorkoutVoiceInputTest`): "cincuenta y cuatro equis cinco" → weight 54, reps 5; "cincuenta y cuatro equis cinco rir dos" → + RIR 2; "cincuenta y cuatro por cinco" sigue igual.

### 4.2 RIR verbal ("me quedaron dos en reserva"…)

1. Nueva tabla en `WorkoutVoiceInput.kt` (zona de keyword tables ≈L576-605):
```kotlin
private val RIR_VERBAL_PATTERNS: List<Regex> = listOf(
    Regex("""\bme quedaron (\w+) en (?:reserva|recamara)\b"""),
    Regex("""\bquedaban (\w+)(?: en (?:reserva|recamara))?\b"""),
    Regex("""\b(\w+) en (?:reserva|recamara)\b"""),
)
```
2. En `parseWorkoutVoiceTranscript` (≈L110-271), ANTES del parseo de tokens de intensidad: para cada patrón, si matchea → `intensityKind = RIR`, `intensityValue = parseVoiceInteger(group(1))` (≈L514-551; soporta palabras y dígitos) y remover el span matcheado del transcript de trabajo (igual que se hace con otros spans). Si el número no parsea, ignorar el patrón (no romper el resto).
3. Gramática: tokens nuevos "me quedaron", "quedaban", "en reserva", "en recámara"/"en recamara" (variantes con/sin tilde ya las emite el builder ≈L184-191) + números ya existentes.
4. **Tests**: "me quedaron dos en reserva" → RIR 2; "quedaban tres" → RIR 3; "54 por 5 me quedaron dos en reserva" → 54×5 RIR 2; frase sin número → no aplica.

### 4.3 Fallo verbal (D2a)

1. `FAILURE_PHRASES` (≈L585): añadir (normalizadas sin tilde): `"dandolo todo"`, `"lo di todo"`, `"di todo"`, `"hasta el fallo"`, `"no me quedo nada"`, `"no quedo nada"`.
2. Gramática: las mismas frases como tokens (multi-palabra se permite: la gramática es lista de frases).
3. Verificar que `"todo"` está en `STOPWORDS` de `WorkoutVoiceMishearingCorrections` (≈L38-44) para que el Levenshtein no reescriba la frase (ya lo está — confirmar y dejar test).
4. **Tests**: "cincuenta y cuatro por cinco dandolo todo" → `reachedFailure=true`, `FAILURE ∈ fields`; "lo di todo" solo → fallo registrado.

### 4.4 Cansancio alto → RPE 9 SIN fallo (D2b)

1. Nueva tabla en `WorkoutVoiceInput.kt`:
```kotlin
private val HIGH_EXERTION_PHRASES = setOf(
    "quede muy cansado", "quede muy cansada", "muy cansado", "muy cansada",
    "sin energia", "agotado", "agotada", "quede agotado", "quede agotada",
)
```
2. Rama de parseo (antes de intensidad por keyword): si el transcript contiene alguna → `intensityKind=RPE`, `intensityValue=9.0`, `reachedFailure=false`, remover el span.
3. **Guard de colisión con FatigueAdvice**: en `WorkoutVoiceCommandParser.parseCommand`, la rama fatiga/ritmo/motivo (≈L266-274, tabla `FATIGUE` ≈L117-120) debe evaluarse SOLO si el transcript NO parece registro de serie. Añadir guarda:
```kotlin
val looksLikeSet = containsWeightMetricPair(normalized) // peso+métrica parseables
if (!looksLikeSet) { /* rama fatigue existente */ }
```
`containsWeightMetricPair`: helper puro nuevo (regex de conector "por|x" entre números, o número junto a WEIGHT_KEYWORDS y número junto a REP_KEYWORDS). Test: "quede muy cansado" solo → `FatigueAdvice`; "cincuenta y cuatro por cinco quede muy cansado" → `RegisterSet` RPE 9.
4. Gramática/scorer/correcciones: añadir las frases y sus tokens clave ("cansado","cansada","agotado","agotada","energia") a gramática + `GYM_SIGNAL_WORDS` + `DOMAIN_LEXICON`.

### 4.5 Orden libre del usuario (bloqueo con tests — ya funciona por keyword-vecino)

Tests nuevos que fijan el comportamiento (sin cambiar código salvo que fallen): "cinco repeticiones con cincuenta y cuatro kilos" ≡ "cincuenta y cuatro kilos por cinco" ≡ "cinco por cincuenta y cuatro kilos"? — definir matriz mínima: reps-first con "con", weight-first con "por", intensidad al final y al inicio ("rir dos, cincuenta y cuatro por cinco"). Si alguno falla, ajustar SOLO el orden de extracción (keyword-vecino ya existe, ≈L410-427) y documentar.

### 4.6 Suite de fase

Cada ítem: test de parser en `app/src/test/java/com/example/kpkn/screens/workout/WorkoutVoiceInputTest.kt` + presencia en gramática en `WorkoutVoiceGrammarBuilderTest.kt` + el test de paridad verde + mishearing no corrompe las frases nuevas.

**Criterio de done Fase 2**: suite verde con ≥12 tests nuevos; en físico (Fase 7) los dictados "54x5 rir 2", "me quedaron 2 en reserva", "dándolo todo", "quedé muy cansado" se registran como se definió.

---

## 5. FASE 3 — CLARIFICACIÓN GUIADA (maquinaria `VoicePendingAction` existente, riesgo medio)

**Archivos base**: `WorkoutVoiceSessionState.kt` (`VoicePendingAction` ≈L52-89), `WorkoutVoiceController.kt` (resolución de pendientes ≈L1110-1175; bloque de faltantes ≈L1482-1492; merge ≈L1361-1373), `WorkoutTtsManager.kt`, `WorkoutVoiceCommandParser.kt`.

### 5.1 Nuevos pending actions

```kotlin
data class MissingSlot(
    val slot: WorkoutVoiceField, // WEIGHT o VALUE
    val baseInterpretation: WorkoutVoiceInterpretation,
) : VoicePendingAction()

data class ConfirmPlannedValue(
    val slot: WorkoutVoiceField,
    val plannedValue: Double,
    val baseInterpretation: WorkoutVoiceInterpretation,
) : VoicePendingAction()

data class ConfirmSuggestedLoad(
    val suggestedWeight: Double,
    val plannedReps: Int?,
    val baseInterpretation: WorkoutVoiceInterpretation,
) : VoicePendingAction()
```
(Ajustar la forma exacta al sealed interface existente de `VoicePendingAction` ≈L52-89; seguir el patrón de `IntensityKind`.)

### 5.2 Flujo de faltantes (reemplaza el mensaje genérico)

En `handleRegisterSet`, bloque `missing` (≈L1482-1492). Nueva precedencia (UNA pregunta por turno; al responder se re-entra a `handleRegisterSet` con el merge y se pregunta lo siguiente si aplica):

1. Falta `VALUE` (reps) y hay reps programadas (`VoiceCommandContext.setDraft.valueText` parseable a número): `ConfirmPlannedValue(VALUE, plannedReps)` + TTS `"¿Pudiste hacer las $reps repeticiones que programaste?"`.
2. Falta `VALUE` y no hay programadas: `MissingSlot(VALUE)` + TTS `"¿Cuántas repeticiones hiciste?"`.
3. Falta `WEIGHT` y hay sugerencia (`exerciseInfo.suggestedWeight`): `ConfirmSuggestedLoad(suggested, plannedReps)` + TTS `"¿Usaste los $kg kilos?"`.
4. Falta `WEIGHT` sin sugerencia: `MissingSlot(WEIGHT)` + TTS `"¿Qué carga usaste?"`.

Los TTS son funciones nuevas en `WorkoutTtsManager` (`speakAskPlannedReps(n)`, `speakAskReps()`, `speakAskSuggestedWeight(kg)`, `speakAskWeight()`) vía `runSpeakingOrSkip` con prioridad HIGH, **sin gate de verbosidad** (son CRITICAL funcionales).

### 5.3 Resolución (en `processCommand`, `when (pendingClarification)` ≈L1110-1175)

- `MissingSlot`: parsear el transcript entrante como número (`parseVoiceInteger` sobre tokens; si trae más texto, extraer el primer número válido; si trae keyword de peso/reps usarlo para el slot correcto). Merge en `baseInterpretation` (`weightKg` o `metricValue`/`metricDecimalValue`) → `handleRegisterSet` de nuevo. Si no hay número: re-preguntar UNA vez; si falla otra vez → cancelar clarificación con `"No te entendí. Dime la serie completa cuando quieras."` y `resumeListening()`.
- `ConfirmPlannedValue` / `ConfirmSuggestedLoad`: "sí"/"no" (keywords `CONFIRM`/`CANCEL` ya operan en LISTENING). Sí → merge del valor planificado/sugerido (y reps planificadas si aplica en `ConfirmSuggestedLoad`) → `handleRegisterSet`. No → degradar a `MissingSlot` del mismo slot.

### 5.4 Escalado tras no entender (engancha `secondUnresolved` existente)

En `WorkoutVoiceController`, donde hoy solo se loguea `native_fallback_trigger_evaluated` (≈L1217-1240, `fallbackTriggerPolicy`): cuando `secondUnresolved == true`, NO haya pendingAction y exista sugerencia de carga: disparar el flujo 5.2.3 con TTS `"No te entendí. ¿Usaste los $kg kilos sugeridos${reps?.let { " por $it repeticiones" } ?: ""}?"` → `ConfirmSuggestedLoad`. Mantener el fallback nativo intacto (solo se añade esta rama antes de evaluar fallback; no romper `WorkoutVoiceFallbackTriggerPolicy`).

### 5.5 Tests Fase 3

- Parser de confirm wait existente (`WorkoutVoiceConfirmWaitParserTest`) sigue verde.
- Nuevos tests JVM puros (extraer la lógica de resolución a helpers puros si hace falta): MissingSlot reps con "veinte" → merge 20; ConfirmPlannedValue "sí" → planned; "no" → MissingSlot; ConfirmSuggestedLoad "sí" → weightKg sugerido; doble fallo → cancelación amable.
- Eventos JSONL nuevos: `guided_clarification_asked` (`kind`, `slot`), `guided_clarification_resolved` (`kind`, `result`: confirmed/value/cancelled).

**Criterio de done Fase 3**: suite verde; en físico: dictar solo "cincuenta kilos" → pregunta de reps; dictar solo "veinte" → pregunta de carga (o confirmación de sugerida); "sí/no" resuelven; nada queda colgado en CONFIRM_WAIT.

---

## 6. FASE 4 — CONFIGURACIÓN AVANZADA (opt-in)

### 6.1 Auto-relleno de cargas sugeridas con confirmación

1. **Setting**: `Settings.voiceAutoSuggestLoads: Boolean = false` (`Settings.kt`, zona de voz). Item en `SettingsTrainingScreen` con `SettingsSwitchItem`.
2. **Cue por serie nueva** (cuando el setting está ON): en `WorkoutVoiceController`, donde se empuja el contexto (`updateCommandContext` ≈L542, ≈L753, ≈L1310, ≈L1912), detectar cambio de `(exerciseId, setIndex)` (vars `lastAnnouncedSetKey: String?`) y, si `exerciseInfo.suggestedWeight != null`, hablar UNA vez por serie (gate ESSENTIAL):
   `"Para esta serie te recomiendo $kg kilos${reps?.let { " por $it repeticiones" } ?: ""}. Di 'sugerencia aplicada' o dime tus números."`
   Nueva `speakSuggestedForSet(kg, reps)` en `WorkoutTtsManager`. Evento `suggested_load_prompted`.
3. **Comando nuevo** `VoiceSessionCommand.ApplySuggestedLoad` (`WorkoutVoiceSessionState.kt` ≈L91-156):
   - Keywords (`WorkoutVoiceCommandParser.kt`, nueva tabla `APPLY_SUGGESTED`): `"sugerencia aplicada"`, `"aplica la sugerencia"`, `"aplicar sugerencia"`, `"usa la sugerencia"`, `"la sugerida"`. (+ gramática/lexicon/scorer por la regla de oro.)
   - Handler (`WorkoutVoiceCommandHandler`, nuevo case antes de `Unknown`): leer `loadSuggestions[workoutSetKey(exId, setIdx, side)]` (`WorkoutUiState.loadSuggestions` ≈L136; `workoutSetKey` en `WorkoutSessionContracts.kt` ≈L49-53) o `ExerciseInfo.suggestedWeight` + reps programadas del draft → construir interpretación con esos valores y despachar `handleRegisterSet` por el flujo NORMAL (con su confirmación TTS estándar — la "confirmación del usuario" es ese sí/no). Si no hay sugerencia disponible: `speakError("No tengo sugerencia para esta serie")`. Evento `suggested_load_applied`.
   - IntentMatcher: permitirlo en LISTENING y con rest activo.

### 6.2 Frases de intensidad personalizadas

1. **Modelo** (`Settings.kt`):
```kotlin
@Serializable
data class CustomIntensityPhrase(
    val phrase: String,
    val kind: String, // "RPE" | "RIR" | "PERCENT_RM" | "FALLO"
    val value: Double? = null, // null solo si kind == "FALLO"
)
```
Campo: `val voiceCustomIntensityPhrases: List<CustomIntensityPhrase> = emptyList()`.
2. **Reescritura a priori** — nuevo objeto puro `android-native/app/src/main/java/com/example/kpkn/services/workout/WorkoutVoiceCustomPhraseRewriter.kt`:
```kotlin
object WorkoutVoiceCustomPhraseRewriter {
    /** Reescribe frases del usuario a tokens canónicos del parser. Normaliza igual que el parser. */
    fun rewrite(normalizedTranscript: String, phrases: List<CustomIntensityPhrase>): String
}
```
Mapeo: kind FALLO → "al fallo"; RPE/RIR/PERCENT_RM → "rpe N"/"rir N"/"porcentaje N" (o "rm N"). Normalizar la frase del usuario con la MISMA normalización del parser antes de comparar (lowercase, sin tildes, colapsar espacios); reemplazo por frase completa con bordes de palabra.
3. **Aplicación**: en `WorkoutVoiceController.handleFinalResult` (≈L1032-1077), DESPUÉS de sanitizar y ANTES de `processCommand`: `transcript = WorkoutVoiceCustomPhraseRewriter.rewrite(transcript, customPhrasesProvider?.invoke().orEmpty())` + evento `custom_phrase_rewritten` (`from`, `to`). Nuevo provider en el controller (patrón `inputModeProvider`): `customPhrasesProvider = { repository.settings.value.voiceCustomIntensityPhrases }` (ViewModel ≈L599).
4. **Gramática**: `VoiceCommandContext` (`VoiceCommandContext.kt` ≈L12-37) nuevo campo `customIntensityPhrases: List<String> = emptyList()` (solo los textos); el controller los incluye en `currentVoiceContext()` (≈L2073-2100); `WorkoutVoiceGrammarBuilder.build` los añade como tokens (zona de inyección de contexto ≈L22-49).
5. **Editor en Ajustes** (`SettingsTrainingScreen`): sección "Frases de intensidad personalizadas": lista (frase → "RPE 9", etc.) con eliminar + formulario de alta (campo de texto + selector kind: RPE/RIR/%RM/Al fallo + campo número oculto si FALLO). Validación: frase no vacía, ≥2 palabras o ≥4 chars; valor en rango (RPE 1-10, RIR 0-10, %RM 1-100). Escritura vía `repository.updateSettings`.
6. **Tests**: rewriter puro (frase con tildes/mayúsculas mixtas; no reescribe dentro de otra palabra; FALLO sin valor); Settings encode/decode; gramática incluye la frase cuando está en contexto.

**Criterio de done Fase 4**: suite verde; setting OFF no cambia nada (regresión); con ON, "sugerencia aplicada" registra con valores sugeridos tras su confirmación; frase custom "rompiendo la barra"→RPE 10 funciona en físico.

---

## 7. FASE 5 — ASISTENTE "OJOS DEL USUARIO" I (read-only, bajo riesgo)

**Archivos base**: `WorkoutVoiceSessionState.kt` (`VoiceSessionCommand` ≈L91-156), `WorkoutVoiceCommandParser.kt` (tablas ≈L10-132 + dispatch ≈L216-354), `WorkoutVoiceIntentMatcher.kt` (allowlist ≈L11-98), `WorkoutVoiceCommandHandler.kt` (dispatch ≈L401-520), `WorkoutTtsManager.kt`.

### 7.1 Nuevos comandos de consulta (keywords exactas definidas)

| Comando | Keywords (normalizadas) | Respuesta TTS (definida) |
|---|---|---|
| `QueryDrainage` | "cuanto drenaje llevo", "drenaje acumulado", "como voy de drenaje" | "Llevas X por ciento de drenaje muscular y Y por ciento del sistema nervioso." (+ " Z espinal." solo si ≥10) |
| `QueryCurrentSet` | "que serie voy", "en que serie estoy", "cuantas series quedan" | "Vas en la serie N de M de [ejercicio]." |
| `QueryPendingSide` | "que lado falta", "que lado me falta", "cual lado falta" | "Te falta el lado izquierdo/derecho." / "Ya registraste ambos lados." |
| (alias) `PaceStatus` | añadir "cuanto tiempo queda de sesion", "cuanto llevo de sesion" | la respuesta existente de `PaceStatus` |
| (alias) `SetSessionTimeLimit` | añadir "pon limite de N minutos", "limite de N minutos" | flujo existente |

Implementación:
- `QueryDrainage`: nuevo port en el handler que construya `List<CompletedExercise>` desde `completedSets` (patrón `WorkoutViewModel.recomputeLiveEnergy` ≈L1385-1420) y llame `AugeFatigueEngine.calculateCompletedSessionDrain(...)` (`domain/auge/AugeFatigueEngine.kt` ≈L657-667) → redondear a enteros. NO mutar nada.
- `QueryCurrentSet`: leer del contexto/estado (`exercise`, `setIndex`, `totalSets`).
- `QueryPendingSide`: `expectedSidesForSet` + `completedSets` (patrón del provider ≈L615-653).
- Todos: `VoiceAnnouncementKind.ESSENTIAL` (gate estándar), `IntentMatcher` permitirlos en LISTENING y con rest activo.

### 7.2 Anuncio proactivo de lado en unilaterales

En el controller, mismo detector de cambio de `(exerciseId, setIndex)` de §6.1.2: si `isUnilateral && pendingUnilateralSide != null` → cue ESSENTIAL una vez por serie: `"Lado izquierdo"` / `"Lado derecho"` (nueva `speakUnilateralSide(side)`).

### 7.3 Tests

Parser: cada keyword → su comando (incl. aliases); handler: respuestas con estado fake (seguir patrón de tests de handler si existe; si el handler no es testeable puro, extraer la composición de la frase a función pura y testearla). IntentMatcher: permitido/bloqueado por stage.

**Criterio de done Fase 5**: suite verde; en físico: las 4 consultas responden con datos reales de la sesión; el lado se anuncia al entrar a serie unilateral.

---

## 8. FASE 6 — ESTRUCTURA POR VOZ (riesgo medio, SIEMPRE con confirmación sí/no)

### 8.1 Mover ejercicio actual

- Keywords: `"sube este ejercicio"`, `"adelanta este ejercicio"` → `MoveCurrentExercise(direction=-1)`; `"baja este ejercicio"`, `"retrasa este ejercicio"` → `+1`.
- Flujo: confirmación TTS `"¿Mover ${ejercicio} ${antes/después} de ${vecino}?"` → sí/no (`VoicePendingAction` nuevo `ConfirmStructureAction`) → ejecutar `WorkoutViewModel.moveExercise(currentExerciseId, direction)` (≈L1771). Si el flujo de persistencia estructural existente marca `PendingStructuralChange`, se resuelve por su canal habitual (no crear uno nuevo).
- Evento `voice_structure_action` (`type="move"`, `target`).

### 8.2 Superseries guiadas

1. `"crea superserie"`, `"crear superserie"`, `"arma superserie"` → `VoicePendingAction.SupersetCollectMembers(members = setOf(currentExerciseId))` + TTS `"¿Con qué ejercicios? Dímelos uno por uno. Di 'listo' para terminar."`
2. Resolución de nombres: `WorkoutVoiceExerciseAliasMatcher` contra ejercicios DE LA SESIÓN (no catálogo global en v1); cada acierto se añade y se confirma hablado ("Añado press militar. ¿Otro más o 'listo'?"); nombre no resuelto → `"No encontré ese ejercicio en la sesión. Repite o di 'listo'."` (máx 2 fallos → cancelar).
3. `"listo"`/`"listo"` con ≥2 miembros → confirmación `"¿Crear superserie con A, B y C?"` → sí → ejecutar.
4. **Corrección de persistencia (obligatoria)**: `WorkoutViewModel.createLiveSuperset` (≈L1672) hoy persiste al programa silenciosamente (≈L1691, ≈L1972-1977). Cambiar la llamada desde voz para que quede alineada con el flujo de `PendingStructuralChange` (igual que reorder): persistToProgram=false + marcar `PendingStructuralChange` para que el usuario decida al final como con reorder. NO cambiar el comportamiento del botón UI existente salvo que comparta el mismo método — si lo comparte, alinear ambos al flujo con prompt (documentar en el resumen).
5. `"disuelve la superserie"`, `"disolver superserie"` → confirmación → `dissolveLiveSuperset(groupId)` (≈L1744).

### 8.3 Editar lado de la última serie

- `VoiceSetEditPatch` (`WorkoutVoiceAutoConfirmGate.kt` ≈L26-35): añadir `val side: String? = null`.
- `parseEditLastSet` (`WorkoutVoiceCommandParser.kt` ≈L363-447): patrones `"fue con el lado (izquierdo|derecho)"`, `"lado (izquierdo|derecho)"` tras trigger de edición.
- Aplicar en el handler donde se aplican los patches de edición (recalcular clave `workoutSetKey` con el lado nuevo al guardar).

### 8.4 Tests Fase 6

- Parser: keywords de move/superset/disolver/edit-side.
- `SupersetCollectMembers`: máquina de estados pura (extraerla a objeto puro `WorkoutVoiceSupersetFlow` para testeo JVM: add member, duplicado → aviso, "listo" con <2 → error, ≥2 → confirm).
- Guard: sin confirmación explícita no se ejecuta NADA estructural (test de contrato).

**Criterio de done Fase 6**: suite verde; en físico: "sube este ejercicio" (confirmado) reordena; "crea superserie" guía y crea tras confirmación; "disuelve la superserie" la deshace; ninguna acción estructural ocurre sin el "sí".

---

## 9. FASE 7 — VALIDACIÓN

### 9.1 Puertas por fase (obligatorias)

1. `gradlew.bat :app:compileBaseDebugKotlin --console=plain` ✅
2. `gradlew.bat :app:testBaseDebugUnitTest --console=plain` ✅ (138 clases / ~921 tests al inicio; debe terminar ≥, 0 fallos)
3. Al final de todo: `gradlew.bat :app:compileBaseDebugAndroidTestKotlin --console=plain` ✅

### 9.2 Validación física (Z Flip5 + ULT WEAR) — checklist por fase

- **F1**: modo MÚSICA: música suena intacta + registro con teléfono cerca y bloqueado (`asr_final` con `keyguardLocked=true`); MANOS LIBRES: baseline actual; switch del header en caliente → `voice_capture_mode_changed` en JSONL y la música cambia de calidad al instante; diálogo aparece la 1ª vez y nunca más salvo "ver explicación".
- **F2**: "cincuenta y cuatro equis cinco rir dos", "me quedaron dos en reserva", "cincuenta por cinco dándolo todo" (→ fallo), "cincuenta por cinco, quedé muy cansado" (→ RPE 9, NO fallo).
- **F3**: dictar solo peso → pregunta de reps; solo reps → pregunta/confirmación de carga; "sí/no" resuelven; dos no-entendidos → oferta de sugerida.
- **F4**: con auto-suggest ON: cue por serie + "sugerencia aplicada" → confirma y registra; frase custom registrada.
- **F5**: "cuánto drenaje llevo", "qué serie voy", "qué lado falta", "cuánto tiempo queda de sesión".
- **F6**: "sube este ejercicio", "crea superserie" (flujo completo), "disuelve la superserie", "la última fue con el lado derecho".
- **F0**: "omitir descanso" → se oye "Descanso omitido. Serie N de M, …"; a los 60 s de descanso NO hay aviso; a los 10 s SÍ.

### 9.3 Eventos JSONL nuevos (todos por `WorkoutVoiceDiagnosticLogger.event`)

`voice_capture_mode_changed`, `voice_mode_dialog_shown`, `voice_mode_dialog_chosen`, `guided_clarification_asked`, `guided_clarification_resolved`, `suggested_load_prompted`, `suggested_load_applied`, `custom_phrase_rewritten`, `voice_structure_action`.

---

## 10. NO-OBJETIVOS Y GUARDARRAÍLES

- **Fuera de alcance**: reemplazo de ejercicio por voz (multi-turno con catálogo), reorden completo dictado, edición de programa/sesiones futuras, iOS/backend, cambios en AUGE/Room/datasets.
- `CONFIRM_WAIT` sin gramática numérica (clarificaciones en LISTENING).
- Vigilar tamaño de gramática: si las adiciones disparan el coste del recognizer, recortar PRIMERO los productos cruzados (`WorkoutVoiceGrammarBuilder` ≈L59-65), nunca la cobertura de frases del usuario.
- Toda acción estructural (Fase 6) requiere confirmación sí/no explícita. Nada de persistencia silenciosa al programa desde voz.
- La función básica (registrar series) no cambia de comportamiento salvo donde este plan lo define con test.

## 11. CHECKLIST FINAL

1. [ ] Fase 0 → verde (2.1–2.4)
2. [ ] Fase 1 → verde (modo, diálogo, header, tarjeta, ajustes)
3. [ ] Fase 2 → verde (≥12 tests nuevos)
4. [ ] Fase 3 → verde (clarificación guiada)
5. [ ] Fase 4 → verde (auto-suggest + frases custom)
6. [ ] Fase 5 → verde (consultas + lado proactivo)
7. [ ] Fase 6 → verde (estructura con confirmación)
8. [ ] Fase 7 → puertas Gradle + checklist físico
9. [ ] Resumen final: archivos tocados, desviaciones, trazos físicos

**Criterio global de éxito**: el usuario registra una sesión completa en cada modo sin tocar el teléfono (salvo acercarlo en Modo Música), con vocabulario flexible, clarificación guiada cuando falta algo, y cero acciones estructurales sin confirmación — sin una sola regresión en los 921+ tests ni en los trazos A/B del plan anterior.
