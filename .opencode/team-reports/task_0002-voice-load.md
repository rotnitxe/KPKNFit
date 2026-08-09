# Diagnóstico task_0002 — Respuesta a «¿Qué carga usaste?» ignorada la 1ª vez

**Síntoma reportado:** en modo voz, la respuesta a la pregunta de carga («¿Qué carga usaste?» / «¿Usaste los X kilos?») es ignorada la primera vez; solo se acepta cuando el usuario repite la serie completa.

## TL;DR

No es gramática ni parser: **la primera respuesta se la come el pipeline de captura post-TTS**. Hay dos caminos de pérdida:

- **A (siempre activo):** guardia anti-eco / PCM-discard de 600 ms + supresión de respaldos hasta 1000 ms tras el TTS ⇒ la respuesta rápida natural se descarta en silencio.
- **B (amplificador condicional):** una segunda TTS encadenada (anuncio de carga sugerida / lado unilateral) o un rebote de stage mata el final por `captureEpoch` → `stale_final_discarded`.

Ambos caminos desembocan en el contador de fallos (`MAX_CLARIFICATION_MISSES = 2`) que **cancela la clarificación** con «No te entendí. Dime la serie completa cuando quieras.» (`WorkoutVoiceController.kt:1707`, réplicas en `:1788` y `:1869`). Tras la cancelación ya no hay clarificación viva, así que lo único que funciona es dictar la serie completa. De ahí el síntoma exacto.

## Flujo real de la pregunta de carga

1. `handleRegisterSet` detecta falta de carga:
   - `WorkoutVoiceController.kt:2493-2498` — `effectiveLoadMode`, `requiresWeight`, `metricMissing`, `weightMissing = requiresWeight && finalInterpretation.weightKg == null`.
2. Crea el pending action y habla (UNA pregunta por turno):
   - Con sugerencia (`suggestedWeight != null`) → `ConfirmSuggestedLoad` + TTS «¿Usaste los X kilos…?»: `WorkoutVoiceController.kt:2531-2544`; texto en `WorkoutTtsManager.kt:280-283` (`speakAskSuggestedWeight`).
   - Sin sugerencia → `MissingSlot(WEIGHT)` + TTS «¿Qué carga usaste?»: `WorkoutVoiceController.kt:2545-2558`; texto en `WorkoutTtsManager.kt:272-274` (`speakAskWeight`).
   - (Análogos para reps: `ConfirmPlannedValue` `:2502-2516`, `MissingSlot(VALUE)` `:2517-2530`; textos `WorkoutTtsManager.kt:264-266`, `:276-278`.)
   - Definiciones de pending actions: `WorkoutVoiceSessionState.kt:103-121` (`MissingSlot`, `ConfirmPlannedValue`, `ConfirmSuggestedLoad`; sealed completa `:53-135`).
3. Ambos se emiten con `runSpeakingOrSkip(onComplete = { resumeListening() })`; durante el TTS el stage es `TTS_SPEAKING` (`WorkoutVoiceController.kt:806-854`; `updateStage(TTS_SPEAKING)` en `:821`).
4. Al terminar el TTS: `resumeListening()` (`WorkoutVoiceController.kt:3314-3354`):
   - `pushGrammar(LISTENING)` `:3335`;
   - `continuousEngine.resumeDecoderAfterTts(0)` `:3343` (implementación `WorkoutContinuousVoiceEngine.kt:317-332`);
   - `updateStage(LISTENING)` `:3345`;
   - anuncios encadenados `announceSuggestedLoadForCurrentSetOnce()` `:3352` y `announcePendingUnilateralSideOnce()` `:3353` (relevante para camino B).


## Camino de pérdida A (siempre activo) — la respuesta rápida se descarta en silencio

1. Al procesar `EngineCommand.Resume`, el engine **descarta todo PCM durante 600 ms**:
   - `WorkoutContinuousVoiceEngine.kt:902-908` — `discardPcmOnly = true; postTtsGuardUntilMs = clockMs() + POST_TTS_GUARD_MS`.
   - Constante `POST_TTS_GUARD_MS = 600L`: `WorkoutContinuousVoiceEngine.kt:1531` (`TTS_RESUME_DELAY_MS = 300L` en `:1543`).
   - Con `discardPcmOnly` activo los frames NO entran a Vosk: `WorkoutContinuousVoiceEngine.kt:1200-1204` (`if (!discardPcmOnly) { recognizer.acceptWaveForm(...) }`); la guardia se libera a los 600 ms en `:1112-1117`.
   - Durante el TTS la captura ya pausó/descarta: `pause()` `WorkoutContinuousVoiceEngine.kt:283-294`; el controller pausa al recibir el final previo en `WorkoutVoiceController.kt:1519-1524` (`continuousEngine.pause()` en `:1520`).
2. Los dos respaldos que salvarían una respuesta corta también se suprimen:
   - **Final vacío + partial:** si Vosk emite final vacío pero el partial traía la frase, dentro de la guardia se suprime (`vosk_empty_final_partial_suppressed`, reason `post_tts_guard`): `WorkoutContinuousVoiceEngine.kt:1220-1234`.
   - **Partial-fallback del controller:** suprimido durante 1000 ms tras `lastTtsCompletedAtMs` (`partial_fallback_suppressed_post_tts`): `WorkoutVoiceController.kt:1346-1357`; constante `PARTIAL_FALLBACK_POST_TTS_WINDOW_MS = 1_000L` en `:3644`; `lastTtsCompletedAtMs` se fija al cerrar el TTS en `:834`.
3. El usuario responde con naturalidad ~200–800 ms después de la pregunta ⇒ su «con 60 kilos» cae dentro de la ventana de 600–1000 ms. Dos desenlaces:
   - **No se ve nada** → silencio total; `pendingAction` sigue vivo; no hay re-pregunta: respuesta «ignorada» (= síntoma reportado).
   - **Se ve una cola decapitada** («…senta kilos», «kilos») sin número útil → rama `MissingSlot` (`WorkoutVoiceController.kt:1688-1722`): con `value == null` (`extractFirstVoiceDecimalNumber ?: extractFirstVoiceNumber` en `:1690-1694`) → `clarificationMisses++` (`:1696`) → re-pregunta «¿Qué carga usaste?» (`:1709-1719`), que **recrea la misma ventana post-TTS**.
4. Si la segunda respuesta también se pierde → `clarificationMisses >= MAX_CLARIFICATION_MISSES`:
   - `MAX_CLARIFICATION_MISSES = 2`: `WorkoutVoiceController.kt:3646` (contador declarado en `:163`).
   - Cancelación: `pendingAction = null` + `voskAccumulator.reset()` + TTS **«No te entendí. Dime la serie completa cuando quieras.»**: `WorkoutVoiceController.kt:1697-1708`. Mismo patrón en `ConfirmPlannedValue` (`:1772-1789`) y `ConfirmSuggestedLoad` (`:1853-1870`).

**Consecuencia observable:** tras la cancelación ya no hay clarificación viva; la única vía es dictar **la serie completa** (reps + carga), que entra como `RegisterSet` nuevo vía `WorkoutVoiceIntentMatcher.match` (`WorkoutVoiceController.kt:2016-2042`) → `handleRegisterSet` (`:2068-2070`). Es exactamente el comportamiento descripto — incluida la instrucción literal de «decir la serie completa».

## Camino de pérdida B (amplificador condicional) — segunda TTS / epoch stale

- Al final de `resumeListening()` se encadenan anuncios que hablan **inmediatamente después de la pregunta**:
  - `announceSuggestedLoadForCurrentSetOnce()`: llamada `WorkoutVoiceController.kt:3352`; implementación `:3374-3393` (gate `autoSuggestLoadsProvider`, `:3375`; texto `speakSuggestedForSet` `WorkoutTtsManager.kt:285-292`).
  - `announcePendingUnilateralSideOnce()`: llamada `WorkoutVoiceController.kt:3353`; implementación `:3357-3371` (unilaterales con lado pendiente).
  - Ambos usan `speakWhilePaused` (`WorkoutVoiceController.kt:723-761`): pausa el engine (`:738`) y emite otro TTS vía `runSpeakingOrSkip` (`:749-760`).
- Si el usuario responde la pregunta durante ese segundo aviso:
  - PCM pausado/descartado ⇒ nada llega a Vosk.
  - Además, `updateStage` incrementa `captureEpoch` en **cada** transición (`WorkoutVoiceController.kt:3491-3495`; declarado `:77`; otro bump en `:2893`).
  - El fragmento quedó agendado con epoch viejo (`scheduleVoskCloseWindow` captura epoch al agendar: `WorkoutVoiceController.kt:1380-1387`; `VOSK_FRAGMENT_GRACE_MS = 2_200L` en `:3632`) y al disparar falla el chequeo de `handleFinalResult` (`WorkoutVoiceController.kt:1453-1469`).
  - La gracia `isStaleConfirmGraceEligible` (`WorkoutVoiceController.kt:3676-3677`; `isConfirmOrCancelPhrase` `:3670-3674`) **solo acepta `CONFIRM_WAIT` + frase sí/no** — pero las clarificaciones guiadas **se resuelven en `LISTENING`** (diseño: `docs/PLAN VOZ POTENCIADA.md` L18; resolución `WorkoutVoiceController.kt:1583-1741`) ⇒ `stale_final_discarded` (`:1465-1469`).
- Variante del mismo mecanismo: un evento de captura (route churn, RECONNECTING) mueve el stage mientras la close-window de 2,2 s está armada — el colector SÍ cambia stage desde LISTENING (`WorkoutVoiceController.kt:972-980` + `WorkoutVoiceSessionGate.kt:52-72`, que solo protege CONFIRM_WAIT/TTS/PROCESSING/RECOVERING). Relacionado con B15 de `docs/AUDITORIA_SISTEMA_VOZ_2026-08.md` L247-248 (160 route requests en 89 min por re-adquisición de ruta en cada pause/resume de TTS).
- Precedente documentado del patrón: `docs/AUDITORIA_SISTEMA_VOZ_2026-08.md` L139-143 («CONF_INPUT "no" → STALE_DROP epoch=15→16») — para clarificaciones en LISTENING ni siquiera existe la gracia.

## Lo que NO es el problema (descartado con evidencia)

- **Gramática Vosk activa al responder** = gramática de LISTENING (+ sí/no), con números incluidos:
  - `grammarTokensForStage` añade `defaultNumericGrammarTokens()` en todo stage ≠ CONFIRM_WAIT: `WorkoutVoiceCommandParser.kt:237-239`; contenido (palabras-número, «kilos», «con ayuda»/«con el cuerpo» que aportan «con»): `WorkoutVoiceCommandParser.kt:263-291`.
  - Productos «número + unidad» e «intensidad + número»: `WorkoutVoiceGrammarBuilder.kt:62-77`.
  - Sí/no inyectados con `pendingClarification=true` en LISTENING: `WorkoutVoiceGrammarBuilder.kt:78-82` (tokens: `WorkoutVoiceCommandParser.kt:293-302`); el flag se empuja vivo en `WorkoutVoiceController.kt:3557-3563` (`pushGrammar`); dedup por hash cubierto en `WorkoutVoiceGrammarKeyTest.kt`.
  - Expansiones léxicas (kg→kilos…): `WorkoutVoiceGrammarLexicon.kt`. Vosk small-es trata la gramática como bag-of-words: «con sesenta kilos» / «sesenta» son reconocibles. El fix de vocabulario de confirmación ya se hizo en commit f268bb0b (`docs/PLAN VOZ POTENCIADA.md:395`).
- **Parser / resolución de la respuesta numérica:** con texto íntegro funciona:
  - `extractFirstVoiceDecimalNumber` / `extractFirstVoiceNumber`: `screens/workout/WorkoutVoiceInput.kt:295-311`.
  - Rama `MissingSlot`: merge en la base → `RegisterSet` (`WorkoutVoiceController.kt:1688-1741`; WEIGHT `:1735-1738`, VALUE `:1730-1734`).
  - Rama `ConfirmSuggestedLoad`: sí → sugerido (+ reps planificadas) `:1801-1823`; no → cancela `:1824-1836`; número hablado → ese peso `:1837-1851`. Rama `ConfirmPlannedValue` análoga `:1743-1797`.
  - Merge final con la base y limpieza del pending en `handleRegisterSet`: `WorkoutVoiceController.kt:2351-2367`. `isAffirmativeReply`/`isNegativeReply`: `:167-180`.
- **TTS→LISTENING nominal:** si la respuesta tarda > ~1 s, todo entra (por eso el segundo intento pausado o la serie completa sí funcionan): `resumeListening` `WorkoutVoiceController.kt:3314-3354`; gates `WorkoutVoiceSessionGate.kt:74-84` (`shouldAcceptFinalResult`/`shouldProcessCommand` permiten LISTENING).

## Propuesta de fix concreta

### F1 (raíz, camino A) — scopear la guardia anti-eco al contexto del prompt

1. Flag en el comando: `EngineCommand.Resume(..., antiEchoGuard: Boolean = true)`; en `WorkoutContinuousVoiceEngine.kt:902-908` aplicar `discardPcmOnly`/`postTtsGuardUntilMs` solo si `antiEchoGuard` (o guardia acortada ~150 ms cuando se espera respuesta).
2. Propagar por `resumeDecoderAfterTts` (`WorkoutContinuousVoiceEngine.kt:317-332`), la firma del puerto `WorkoutVoiceEnginePort.kt:~30-34` y el cliente remoto `WorkoutRemoteVoiceEngineClient.kt:221-225` si lo implementa.
3. Resumir SIN guardia (o mínima) cuando el TTS es **pregunta de clarificación**:
   - Prompts iniciales: `WorkoutVoiceController.kt:2511-2514` (`ConfirmPlannedValue`), `:2525-2528` (`MissingSlot` VALUE), `:2539-2542` (`ConfirmSuggestedLoad`), `:2553-2556` (`MissingSlot` WEIGHT).
   - Re-preguntas: `WorkoutVoiceController.kt:1705-1719`, `:1786-1794`, `:1867-1875`; por consistencia también `IntensityKind`/`ReaskIntensity` (`:2433-2440`, `:2458-2461`), `TechniqueDetails` (`:2478`) y `LoadMode` (`:2487-2490`).
4. Complemento controller-side: no suprimir el partial-fallback post-TTS con clarificación pendiente — condicionar el chequeo de `WorkoutVoiceController.kt:1351` (`shouldSuppressPartialFallbackAfterTts`, helper `:3695-3699`) a `_state.value.pendingAction == null`.
5. Alternativa mínima: guardia reducida solo cuando hay clarificación activa — el engine ya conoce `pendingClarificationActive` (`WorkoutContinuousVoiceEngine.kt:81-85`, seteado en `UpdateGrammar` `:922-928`): acortar ahí `POST_TTS_GUARD_MS` (`:1531`) en esos resumes.

### F2 (camino B) — gracia stale para clarificaciones + no pisar la pregunta con anuncios

1. Extender la gracia: en `WorkoutVoiceController.kt:1453-1462`, si el stage actual es `LISTENING` y `pendingAction` es `MissingSlot`/`ConfirmPlannedValue`/`ConfirmSuggestedLoad` y el transcript es respuesta plausible (`extractFirstVoiceDecimalNumber(text) != null || extractFirstVoiceNumber(text) != null || isAffirmativeReply(text) || isNegativeReply(text)`), procesarlo como final normal (re-entrar a `handleFinalResult` → `processCommand`) en vez de `stale_final_discarded`. NO rutar a `handleConfirmInput` (eso es solo para sí/no de CONFIRM_WAIT, `:2926`).
   - Sugerencia: cambiar `isStaleConfirmGraceEligible` (`WorkoutVoiceController.kt:3676-3677`) para recibir `pendingAction` y devolver enum (`ACCEPT_AS_CONFIRM` / `ACCEPT_AS_CLARIFICATION` / `DROP`).
2. No disparar anuncios sobre clarificación viva: en `resumeListening` (`WorkoutVoiceController.kt:3352-3353`) saltar `announceSuggestedLoadForCurrentSetOnce()` y `announcePendingUnilateralSideOnce()` cuando `_state.value.pendingAction != null` (reagendar tras resolución — p.ej. tras el `handleRegisterSet` exitoso).

### F3 (robustez) — no castigar al usuario por lo que tragó la guardia

- En `WorkoutVoiceController.kt:1695-1697`, `:1772`, `:1853`: no incrementar `clarificationMisses` cuando el transcript es `isNoiseTranscript` (`:1528-1533`) o cuando el último vacío provino de la ventana post-TTS (los eventos `vosk_empty_final_partial_suppressed` — engine `:1222-1229` — y `partial_fallback_suppressed_post_tts` — controller `:1351-1356` — ya se loguean; guardar timestamp/flag y consultarlo). Re-preguntar sin consumir intento.

### Nota secundaria (relacionada, no causal directa)

- `extractFirstVoiceDecimalNumber` abarca del primer al último token numérico (`screens/workout/WorkoutVoiceInput.kt:308-310`). Con acumulador sucio (hallazgo **B12**, `docs/AUDITORIA_SISTEMA_VOZ_2026-08.md` L245: sin reset tras resolución concatena utterances) un «sesenta» puede llegar como «diez repeticiones sesenta» y colapsar el parseo. Resetear `voskAccumulator` también al CREAR la clarificación (`WorkoutVoiceController.kt:2505`, `:2519`, `:2533`, `:2547`) — hoy solo se resetea al final del comando (`:2058`) y en cancelaciones.

## Cómo confirmar el diagnóstico en logs JSONL

Secuencia esperada del bug:
1. `guided_clarification_asked` (kind `MissingSlot`/`ConfirmSuggestedLoad`, slot `WEIGHT`).
2. **Ausencia de `asr_final`** para la respuesta, o `asr_final` con cola decapitada sin número (camino A). Si hubo segundo TTS/anuncio o route churn: `stale_final_discarded` con el transcript legítimo (camino B).
3. (Opcional) re-pregunta y segundo miss → `guided_clarification_resolved` con `result=cancelled`.
4. La serie completa posterior → `command_parsed` `RegisterSet` → registro OK.

Eventos útiles: `guided_clarification_asked/resolved`, `asr_final`, `vosk_fragment` (`WorkoutVoiceController.kt:1339-1342`), `stale_final_discarded`, `vosk_empty_final_partial_suppressed`, `partial_fallback_suppressed_post_tts`, `duplicate_final_ignored` (`:1319-1324`, ventana `DUPLICATE_FINAL_WINDOW_MS = 500L` en `:3642`). `scripts/analyze_voice_logs.py:58-62` ya cuenta eventos de clarificación/timeout.

## Archivos y rangos consultados

- `services/workout/WorkoutVoiceController.kt`: :77, :163-180, :723-854, :883-900, :972-980, :1060-1110, :1296-1526, :1559-1560, :1583-1960, :1985-2075, :2240-2270, :2342-2600, :2866-2930, :3314-3393, :3480-3563, :3632-3646, :3670-3699.
- `services/workout/WorkoutContinuousVoiceEngine.kt`: :81-85, :283-360, :886-935, :1040-1180, :1200-1270, :1531-1547.
- `services/workout/WorkoutVoiceSessionState.kt` (sealed `VoicePendingAction` :53-135); `WorkoutVoiceCommandParser.kt` :160-302; `WorkoutVoiceGrammarBuilder.kt` :11-138; `WorkoutTtsManager.kt` :240-292; `WorkoutVoiceSessionGate.kt` :1-95; `VoskUtteranceAccumulator.kt`, `WorkoutVoiceCaptureGate.kt`, `WorkoutVoiceUtteranceGuard.kt` (completos); `screens/workout/WorkoutVoiceInput.kt` :255-340.
- Docs: `docs/PLAN VOZ POTENCIADA.md` L18, L381-407; `docs/AUDITORIA_SISTEMA_VOZ_2026-08.md` L139-143, L244-248.

**Estado:** SOLO DIAGNÓSTICO — sin ediciones de código realizadas.





