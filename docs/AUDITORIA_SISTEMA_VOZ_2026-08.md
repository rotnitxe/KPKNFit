# Auditoría del Sistema de Control de Voz KPKN — 2026-08-04

**Alcance:** logs JSONL de las sesiones del 03-08-2026 23:20 → 04-08-2026 01:43 (modo Música y Manos Libres), archivos de recovery (`application_exit`) y correlación con el código fuente (`android-native/`). No se modificó código de la app.

**Archivos analizados** (carpeta `KPKN/voice` del teléfono + recovery en `KPKN/`):

| Archivo | Proceso | Contenido |
|---|---|---|
| `...-3538c4f9.jsonl` (1176 eventos) | `com.example.kpkn` | Sesión de entrenamiento completa 23:20→01:29 (termina en el JAVA_CRASH) |
| `...-22b9af39.jsonl` (417) | `:voice` | Generación 1 del motor, 23:20→23:27 (muere por LOW_MEMORY) |
| `...-34161f94.jsonl` (20) | `:voice` | Generación 5, vive 14 segundos |
| `...-6530a5d1.jsonl` (52) | `:voice` | Generación 8, vive 107 segundos |
| `...-ec931bbf.jsonl` (1311) | `:voice` | Generación 11, 00:14→01:43 (sobrevive al crash de la app) |
| `...-965e5fb8.jsonl` / `...-d9f673b1.jsonl` | ambos | Sesión post-crash 01:43 (cierre del entrenamiento) |
| `kpkn-voice-recovery-1785806949313.jsonl` | — | LOW_MEMORY de `:voice` (23:29:58) + **JAVA_CRASH del proceso principal (01:29:09)** |
| `kpkn-voice-recovery-1785686331979.jsonl` | — | LOW_MEMORY del 02-08 (previo) |
| 111 archivos pequeños | ambos | Espejo por evento del logger compartido (ver §5.1) |

**Corrección aplicada:** 111 archivos con extensión errónea `*.jsonl (N)` renombrados a `* (N).jsonl`. La causa raíz del nombre defectuoso está en §5.1 — **es un bug de la app, no de la sincronización**.

---

## 1. Resumen ejecutivo

El sistema funciona y registra series de principio a fin en el camino feliz, pero los logs confirman **todos** los síntomas reportados y revelan una cadena causal concreta para el más grave (el crash). Severidad ordenada:

| # | Problema | Severidad | Modo |
|---|---|---|---|
| B1 | Duplicación de hipótesis Vosk en modo Música → doble parse → doble TTS → **doble persistencia (corrupción de datos) → JAVA_CRASH** | 🔴 Crítico | Música |
| B2 | Números "X y Z" interpretados como decimal X.Z (70.17, 60.8, 80.5) | 🔴 Crítico | Ambos |
| B3 | "Sí/No" del usuario descartados como *stale* durante re-preguntas TTS | 🔴 Crítico | Ambos |
| B4 | Clarificaciones guiadas (¿hiciste las reps? ¿usar sugerido?) **no pueden aceptar "sí/no"** (la gramática Vosk no los contiene) → loops de hasta 5,5 min | 🔴 Crítico | Ambos |
| B5 | Calidad técnica y molestias: el código de los prompts existe pero **nunca se invoca** (dead code) | 🟠 Alto | Ambos |
| B6 | Alucinaciones: eco del propio TTS promovido a comando ("confirmar" ×3 tras persistir; "repeticiones" ×4) | 🟠 Alto | Ambos, peor en Música |
| B7 | Kill LOW_MEMORY del proceso `:voice` a los 9 min de sesión | 🟠 Alto | Ambos |
| B8 | Apagado espontáneo del motor (MIC_BUSY → `voice_process_stopped`) | 🟠 Alto | Manos libres |
| B9 | El motor tarda 4 intentos / 2 min en arrancar (11 generaciones en ~2 h) | 🟡 Medio | Ambos |
| B10 | Mishearings sistemáticos "rir dos"→"rir voz/doce/ocho/toca/kilos"; confianza ASR siempre 0.0 | 🟡 Medio | Ambos |
| B11 | Vocabulario de cancelación estrecho ("borrar" no cancela en CONFIRM_WAIT) | 🟡 Medio | Ambos |
| B12 | Acumulador de enunciados no se limpia → transcripción doblada | 🟡 Medio | Manos libres |
| B13 | Logger: escritura síncrona con `fd.sync()` por evento + espejo SAF por evento (explosión de archivos " (N)") | 🟡 Medio | Ambos |
| B14 | Eventos con etiqueta de ruta obsoleta tras cambio a Música (dificulta diagnóstico) | 🔵 Bajo | Música |

**Evidencia de tasa de éxito:** en ~2 h de sesión con voz activa: ~70 finales ASR, 53 comandos parseados, 21 rearmados de confirmación, 19 inputs de confirmación, 12 timeout diferidos, **solo 9 series persistidas** — y una de ellas quedó corrupta (B1).

---

## 2. Línea de tiempo reconstruida de la sesión

```
23:20:20  Voz ON. Usuario elige HANDS_FREE (23:22), tras probar MUSIC 5 s.
23:23:18  "setenta y siete kilos rir dos" → ASR "setenta diecisiete kilos rir voz" → 70.17 kg (B2, B10)
23:23:24  Usuario dice "no" → cancela. Corrige: "no setenta siete repeticiones rir dos" → aclaración ConfirmSuggestedLoad
23:27:34  MIC_BUSY → motor se detiene solo (B8)
23:29:58  Proceso :voice asesinado por LOW_MEMORY (B7) → voice_ipc_died en la app
23:29→00:12  *** 48 MINUTOS SIN VOZ ***
00:12:33→00:14:35  Usuario pulsa activar voz 4 veces; generaciones 5 y 8 mueren en segundos (B9); gen 11 queda estable
00:17→00:19  Bucle de confirmación: 3× "no" descartados como stale (B3); 12 timeouts diferidos en ráfaga
00:44→00:50  LOOP de 5,5 minutos: "dos cinco repeticiones ritmo doce" → sugiere 90 kg → usuario dice "no" y el sistema no puede oírlo (B4)
01:16:08  Usuario cambia a modo MUSIC
01:16:09  *** Empieza la duplicación de fragmentos Vosk (B1) ***
01:19:27  Serie 20 kg persistida OK… pero el eco "confirmar" se sigue reconociendo ×3 (B6)
01:29:08  Doble CONF_INPUT "confirmar" (11 ms) → doble persistencia (50743b06 ✔ y 4a0efbd0 ✖) 
01:29:09  JAVA_CRASH del proceso principal (RSS 365 MB) — app muere con voz LISTENING
01:29:14  El proceso :voice sigue vivo y reconoce "confirmar" (eco) hablando con una app muerta
01:43:15  Usuario reabre la app; entrenamiento auto-guardado; resumen: "No registraste molestias" (B5)
```

---

## 3. Bugs críticos (con evidencia)

### 🔴 B1 — Modo Música: duplicación de hipótesis → doble TTS → doble persistencia → crash + corrupción de datos

**Es el bug más grave de la auditoría: causa el crash, la repetición de frases 2-3 veces y una corrupción silenciosa de una serie.**

Evidencia — los `vosk_fragment` llegan **en pares idénticos separados 3-28 ms**, y solo desde el cambio a MUSIC (01:16:08):

```
01:18:55.465 / .493  "veinte kilos cinco repeticiones"   (Δ28 ms)
01:18:57.554 / .570  "rir cero"                         (Δ16 ms)
01:19:06.789 / .802  "borrar"                           (Δ13 ms)
01:28:53.471 (+9 ms) "veintidos coma cinco kilos cinco repeticiones rir dos"
```

En todo el período HANDS_FREE (00:12→01:15) no hay ni un solo par duplicado. La duplicación comienza exactamente tras el reinicio del motor por cambio de modo: el teardown/apertura de ruta del modo Música deja **dos rutas de decodificación vivas** (actor anterior + actor nuevo, o callback registrado dos veces), y cada final se entrega dos veces por el binder.

Efectos en cascada, todos visibles en el log de 01:18→01:29:

1. Cada `asr_final` se procesa dos veces → dos `command_parsed` (01:19:00.093/.526).
2. Dos `voice_phase TTS/START` para la misma frase (01:19:00.104/.551, Δ447 ms) → **el usuario oye la confirmación repetida/solapada 2-3 veces**. En 01:29:09 hay tres TTS/START en 77 ms justo antes del crash.
3. La doble confirmación armada consume **una sola** respuesta del usuario dos veces:

```
01:29:08.944  CONF_INPUT "confirmar"
01:29:08.955  CONF_INPUT "confirmar"          (Δ11 ms — imposible humano)
01:29:08.973  set_persistence_started ex=50743b06 set=0 w=22.5   ← ejercicio actual ✔
01:29:08.980  set_persistence_started ex=4a0efbd0 set=0 w=22.5   ← ejercicio ANTERIOR ✖
01:29:09.088  PERSIST_OK 50743b06
01:29:09.185  PERSIST_OK 4a0efbd0   ← sobrescribe la serie de 20 kg registrada a las 01:19 con 22.5 kg
01:29:09.313  JAVA_CRASH (recovery: reason=4, rssKiB=365520, voice=LISTENING)
```

La segunda persistencia usa el `lastCommand` obsoleto del ejercicio previo (`draft.updatedAtMs` del 01:19): **la serie de 20 kg del ejercicio 4a0efbd0 quedó sobrescrita con 22.5 kg**. Dos escrituras concurrentes + tormenta TTS en el hilo principal → excepción → muerte del proceso.

**Código implicado:** `WorkoutContinuousVoiceEngine.kt` (handler `EngineCommand.UpdateCaptureMode` 778-797 y cierre de actor 799-814), `WorkoutVoiceForegroundService.kt` (callback binder 195-204), `WorkoutVoiceController.kt` (`handleFinalResult` 1131-1214 — sin deduplicación por `eventId`/contenido+ventana), `doConfirm` (2626-2674 — sin guarda de re-entrada), `WorkoutVoiceCommandHandler.persistVoiceSet` (887-1029).

**Fixes requeridos (plan, §7):** (a) garantizar un solo actor/callback tras `UpdateCaptureMode` (await del cierre antes de reabrir); (b) deduplicación de finales idénticos en ventana <500 ms en el controller; (c) guarda idempotente en `doConfirm` (token de confirmación consumido una vez); (d) la persistencia debe usar el exerciseId capturado al armar la confirmación, nunca `lastCommand` mutable.

---

### 🔴 B2 — Números "X y Z" → decimal X.Z

Evidencia (todas las cargas con decimales espurios de la sesión):

| El usuario dijo | ASR emitió | Persistió |
|---|---|---|
| "setenta y siete kilos" | `setenta diecisiete kilos` | **70.17 kg** |
| "sesenta y ocho kilos" | `sesenta ocho kilos` | **60.8 kg** (×3 intentos) |
| "ochenta y cinco kilos" | `ochenta cinco kilos` | **80.5 kg** |

Cadena causal (confirmada en código):
1. La gramática restringida de Vosk **no incluye la conjunción "y"** — `WorkoutVoiceCommandParser.defaultNumericGrammarTokens()` (líneas 246-274). El reconocedor no puede emitir "y"; fuerza "diecisiete"/"ocho"/"cinco".
2. El parser recibe `[setenta, diecisiete]` y, sin separador explícito, cae en `gymDecimal()` (`WorkoutVoiceInput.kt:509-532`): divide en entero|fracción → 70 + 17/100 = **70.17**; `[cincuenta, uno]` → 50 + 1/10 = **50.1** (el "cincuenta coma uno" que oíste es el TTS leyendo 50.1 vía `WorkoutTtsManager.formatWeight`).
3. `gymDecimal` tiene guardas para "y" (líneas 517-518) y el parser la maneja bien ("treinta y cinco" → 35.0 en tests) — **pero la "y" jamás sobrevive al reconocedor**, así que la guarda es código muerto en producción.

Agravante: "ciento cincuenta" (150), "ciento treinta" (130), "ciento diez" (110) funcionan porque "ciento X" no toma el camino gymDecimal — por eso el bug es intermitente y confunde más.

**Fixes:** (a) agregar "y" a la gramática numérica (costo casi nulo, es una palabra más del vocabulario); (b) restringir `gymDecimal` a fracciones plausibles de gimnasio (fracción ≤ 5 o ∈ {25, 50, 75}) — "70.17" y "60.8" nunca deben existir; (c) validación de plausibilidad: decimales de 2 dígitos distintos de .25/.50/.75 requieren confirmación explícita.

---

### 🔴 B3 — "Sí/No" descartados como *stale* durante re-preguntas (loop de confirmación)

Evidencia — el patrón se repite 5 veces en la sesión:

```
00:17:21  REARM (CONFIRM_WAIT)
00:17:23  CONF_INPUT "no"          ← respuesta del usuario
00:17:25  STALE_DROP "no" epoch=15→16   ← el final legítimo se descarta
00:17:34  (el usuario tiene que re-dictar la serie completa)
…
00:50:31  REARM → 00:50:32 CONF_INPUT "borrar" → 00:50:35 STALE_DROP "borrar"
00:50:52  REARM → 00:50:53 CONF_INPUT "no"     → 00:50:56 STALE_DROP "no"
01:19:06  CONF_INPUT "borrar" → (TTS re-ask) → 01:19:09 STALE_DROP "borrar" → 01:19:20 CONF_TIMEOUT
```

Mecánica: cada `updateStage()` incrementa `captureEpoch` (`WorkoutVoiceController.kt:2984`); la re-pregunta TTS (ruido→"Di sí para confirmar o no para cancelar", 2549-2558, o reprompt por timeout 2718-2801) cambia el stage a TTS_SPEAKING y de vuelta a CONFIRM_WAIT; el final del usuario que ya estaba en vuelo llega con el epoch viejo y se descarta (`handleFinalResult` 1150-1158, logueado `stale_final_discarded`). La respuesta instantánea por parcial a veces salva la respuesta (por eso a veces sí funciona) — es una **carrera**, de ahí la errática.

Agravante: la re-pregunta se hace **sin pausar el motor** (`runSpeakingOrSkip` no llama `pause()`), así que durante el re-prompt el reconocedor sigue escuchando con gramática de confirmación y la propia frase "Di **sí** para **confirmar** o **no** para **cancelar**" es auto-disparante (ver B6).

**Fixes:** (a) no descartar finales de la ventana de confirmación solo por epoch si el stage sigue siendo CONFIRM_WAIT (grace epoch ±1); (b) pausar el decoder durante re-preguntas (mismo camino que `speakWhilePaused`); (c) suprimir el re-prompt por "ruido" cuando el input era una palabra válida de confirmación mal cronometrada.

---

### 🔴 B4 — Clarificaciones guiadas sordas a "sí/no" (loops de minutos)

El peor loop de la sesión, **5 minutos 20 segundos** para una serie:

```
00:44:51  ASR "dos cinco repeticiones ritmo doce" → parse sin peso
00:44:52  CLARIF_ASK ConfirmSuggestedLoad WEIGHT   ("¿Usar 90 kilos?")
00:45:10  "noventa kilos" → resuelve → RE-PREGUNTA ConfirmSuggestedLoad
00:45:39  ASR "no"  ← el usuario rechaza la sugerencia… y NO pasa nada (sin CLARIF_RES)
00:45:45  "noventa kilos" → resuelve otra vez → parse con transcripción DOBLADA (B12) → re-pregunta otra vez
00:50:12  CLARIF_RES cancelled (el usuario se rindió y dictó otra cosa)
```

Causa raíz (código): las aclaraciones `VoicePendingAction.ConfirmPlannedValue/ConfirmSuggestedLoad` se resuelven en stage **LISTENING** con la gramática completa de comandos, que **no contiene sí/no/confirmar/cancelar** — esas palabras solo se inyectan en la gramática de `CONFIRM_WAIT` (`WorkoutVoiceCommandParser.kt:183-187` vs 188-219). Con gramática restringida, Vosk no puede emitir "sí": devuelve `[unk]` (descartado como ruido) o una palabra forzada. `isAffirmativeReply()` (controller 136-142) tiene 14 sinónimos de sí — pero nunca los recibe. Solo "listo" funciona, por accidente (es keyword de SkipRest). El doc de diseño (`docs/PLAN VOZ POTENCIADA.md:395`) afirma que "keywords CONFIRM/CANCEL ya operan en LISTENING" — **falso**, y esa suposición dejó el bug invisible.

Lo mismo aplica a "¿Pudiste hacer las N repeticiones?" (`ConfirmPlannedValue`) — en la sesión se pregunta 2 veces seguidas la misma cosa (00:13:43/00:13:58) y el usuario responde números sueltos ("seis", "cinco") que sí están en gramática.

**Fixes:** (a) incluir vocabulario sí/no en la gramática de LISTENING cuando hay `pendingClarification` activa (la gramática ya se reconstruye por stage — agregar variante); (b) o mover estas aclaraciones a CONFIRM_WAIT; (c) cap de re-preguntas: tras 1 fallo de comprensión, ofrecer alternativa ("di otro peso, o 'cancela'"); (d) corregir el doc de diseño.

---

### 🟠 B5 — Calidad técnica y molestias: prompts muertos (feature pedida, nunca llega)

Confirmado en código y en logs (cero eventos de feedback en 2 h; resumen final: *"No registraste molestias"*):

- `WorkoutVoiceController.announceFeedbackSheetPrompt()` (437-450) — contiene exactamente las frases pedidas ("Di la calidad técnica del 1 al 10, o una molestia") — **su único llamador es `WorkoutViewModel.requestPostExerciseFeedback()` (2547-2553), que no tiene ningún call site**. Dead code completo.
- El flujo automático abre el sheet **en silencio**: `WorkoutStepNavigator.nextSet()` (323-429) y `WorkoutRestTimerOrchestrator` aparcan los ids en `voicePendingFeedbackExerciseIds` — campo que se persiste/hidrata pero **nunca se consume para disparar un prompt**.
- Al iniciar el descanso, `speakRestStarted*` solo anuncia duración; **no existe ninguna frase de molestias en `WorkoutTtsManager`**.
- El parser sí sabe parsearlas: `parseFeedbackCommand()`/`parseFinalFeedbackCommand()` (parser 698-793) con gramática "calidad/tecnica/molestia/dolor…" — otra isla de código lista pero desconectada.

**Fixes:** (a) consumir `voicePendingFeedbackExerciseIds` al inicio del rest → anunciar y habilitar gramática de feedback (ya existe, gateada por `showPostExerciseSheet`); (b) agregar pregunta de molestias al inicio del timer (frase nueva en `WorkoutTtsManager` + rama en el flujo); (c) log de auditoría `feedback_prompt_shown` / `feedback_registered` para verificar registro.

---

### 🟠 B6 — Alucinaciones: el sistema se escucha a sí mismo

Evidencia de eco TTS promovido a comando:

```
00:29:58  PERSIST_OK → 00:30:04 vosk_empty_final_partial_used "sí" → 00:30:06 ASR "sí" (LISTENING) → FB_EVAL
00:57:47  PERSIST_OK → 00:57:53 vosk_empty_final_partial_used "confirmar" → 00:57:55 ASR "confirmar" → FB_EVAL
01:19:27  PERSIST_OK → 01:19:30 PARTIAL_FB "confirmar" → 01:19:35 "confirmar" → 01:19:38 "confirmar" ×2 → LOW_CONF "no te entendí"
01:25:25  PARTIAL_FB "repeticiones" ×2 → ASR "repeticiones" ×4 → 2 aclaraciones canceladas solas
01:29:14  (app ya muerta) el :voice sigue reconociendo "confirmar"
```

Vectores (código):
1. **Pausa IPC asíncrona:** `WorkoutRemoteVoiceEngineClient.pause()` es fire-and-forget (182-188) — el TTS arranca antes de que el decoder remoto se silencie; el onset del TTS se decodifica.
2. **Guardia post-TTS corta:** `POST_TTS_GUARD_MS=250` + `onDone` puede disparar antes de drenar el audio; el watchdog de 8 s fuerza resume aunque el TTS siga sonando (y no llama `tts.stop()` → solapes, `WorkoutVoiceUtteranceGuard`).
3. **Sin AEC:** en Música se usa `AudioSource.VOICE_RECOGNITION` (sin cancelador) con TTS por `USAGE_ASSISTANT`; en Manos Libres el acoplamiento es acústico directo (SCO full-duplex).
4. **Amplificadores:** `vosk_empty_final_partial_used` (engine 1131-1151) y `PARTIAL_FINAL_FALLBACK_MS=2800` (controller) promueven parciales — diseñados para rescatar voz débil, pero con eco producen comandos fantasma. La gramática de CONFIRM_WAIT contiene exactamente las palabras del propio prompt ("Di sí para confirmar…") → auto-confirmación/auto-ruido.

**Fixes:** (a) `pauseAndAwait` antes de hablar en TODOS los caminos (hoy solo en persistencia); (b) subir guardia post-TTS a ~600 ms y basar el resume en `onDone` real + drain, no en watchdog; (c) el watchdog debe llamar `tts.stop()`; (d) no promover parciales a finales durante ±1 s tras TTS; (e) reformular los prompts para que no contengan las palabras gatillo de su propia gramática ("Responde sí… o no" en vez de repetir "confirmar/cancelar"); (f) evaluar `VOICE_COMMUNICATION` (con AEC) también en modo Música cuando no hay BT.

---

### 🟠 B7 — Proceso `:voice` asesinado por LOW_MEMORY a los 9 minutos

```
recovery: 2026-08-03T23:29:58  process=com.example.kpkn:voice  reason=LOW_MEMORY  voice=DISABLED;gen=1
```

El modelo Vosk small-es expandido + 2 recognizers en caché LRU + AudioRecord, con `largeHeap=true` pero en proceso separado de ~hundreds of MB, y **el unload por inactividad (12 min) solo corre en modo PUSH_TO_TALK** (`WorkoutVoiceController.kt:992-1030`) — en continuo el modelo queda cargado toda la sesión. `onTrimMemory` no libera si hay sesión activa (by design), así que bajo presión el LMK mata el proceso. Resultado: 48 minutos sin voz hasta que el usuario lo notó y reactivó manualmente.

**Fixes:** (a) unload del modelo tras N min sin actividad de voz también en continuo (recargar en ~1 s cuesta menos que perder 48 min); (b) reducir `RECOGNIZER_CACHE_SIZE` a 1 (las gramáticas cambian por stage; el cache de 2 duplica memoria de FST); (c) reportar `voice_trim_memory` con umbrales y reaccionar a TRIM_MEMORY_MODERATE cerrando recognizers (no el modelo).

---

### 🟠 B8 — Apagado espontáneo del motor (MIC_BUSY → stop)

```
23:27:32.118  CAPTURE STARTING
23:27:32.146  audio_record_config present=True silenced=True
23:27:32.237  CAPTURE MIC_BUSY
23:27:34.707  CAPTURE IDLE
23:27:34.835  diagnostic_closed reason=voice_process_stopped
```

En medio de una aclaración pendiente (ConfirmSuggestedLoad del set de 77 kg), el motor declaró MIC_BUSY y el servicio se detuvo por completo 2,5 s después. No hay reintento visible; el usuario quedó hablándole a un micrófono muerto (la app seguía preguntando por TTS, presumiblemente). El log no registra **quién** decidió el stop — falta un evento `voice_stop_requested origin=…`.

**Fixes:** (a) instrumentar el origen del stop; (b) MIC_BUSY debe gatillar reintento con backoff (ya existe `scheduleAfterFailure` — verificar por qué no corrió o por qué se canceló); (c) si el motor muere con aclaración pendiente, la UI/TTS debe avisar ("voz desactivada, toca para reactivar") en vez de seguir preguntando a sordas.

---

## 4. Bugs medios y menores

- **B9 — Arranque frágil del motor:** 4 pulsaciones del usuario y generaciones 2→11 en 2 min (gen 5 vivió 14 s, gen 8 vivió 107 s; `diagnostic_closed reason=superseded_by_new_workout` encadenadas). Cada enable re-dispara `start` que supersede al anterior; falta debounce/estado "starting" visible.
- **B10 — Mishearings sin red:** "rir dos" → "rir voz" (×3), "rir doce" (×2), "rir ocho", "rir toca", "rir kilos", "ritmo doce"; además `confidence=0.0 / confidenceKnown=false` en **todas** las finales → el gateo por confianza (`voice_low_confidence_reask` dispara con conf=0) está roto de fábrica. `WorkoutVoiceMishearingCorrections` salta tokens <4 chars (nunca corrige "y", "dos"→"voz"). Nivel de señal bajo: `rmsAvgDb ≈ -36…-42 dB` (BT lejano / teléfono en bolsillo).
- **B11 — Vocabulario de cancelación estrecho:** el usuario dijo "borrar" dos veces para cancelar una confirmación; "borrar" no es CANCEL_KEYWORD → re-pregunta → timeout → frustación. Agregar "borrar/elimina/quita/olvida" a cancelación en CONFIRM_WAIT.
- **B12 — Acumulador sin limpiar:** transcripción persistida `dos cinco repeticiones ritmo doce dos cinco repeticiones ritmo doce` (00:45:46) — `VoskUtteranceAccumulator` no se resetea tras resolución de aclaración; concatena el enunciado viejo con el nuevo.
- **B13 — "diecisiete coma cinco" sin "kilos" no registra peso:** requiere keyword de peso sí o sí; con aclaración MissingSlot el flujo enreda al usuario (01:22:45→01:25:36, termina cancelado). Si el draft tiene ejercicio con carga, un número decimal solo debería proponerse como peso.
- **B14 — Etiqueta de ruta obsoleta:** tras el cambio a MUSIC, `audio_route_observed` muestra `route=phone` correcto, pero los 324 `voice_capture_health` y los `asr_final` siguen diciendo `bt_sco:ULT WEAR` — etiqueta cacheada, dificulta todo diagnóstico de ruta.
- **B15 — Doble `audio_route_request` a 5 ms:** ambos `communication_device_selected` (00:12:34.756/.761) — carrera en `acquire()` (el segundo debería caer en `already_selected`); y **160 route requests en 89 min** (119 `already_selected`): cada pause/resume de TTS re-adquiere el dispositivo de comunicación. Churn innecesario.

## 5. Inconsistencias de implementación y logging

### 5.1 El desastre de archivos " (N)" es un bug de la app
Hay **dos loggers** escribiendo lo mismo: `WorkoutVoiceDiagnosticLogger` (sesión, nombre con traceId) y `KpknDiagnosticLogger` (compartido, nombre con **segmento aleatorio no relacionado con el traceId** — por eso `2c5dac55` contiene eventos con traceId `22b9af39…`). El espejo SAF del segundo (`KpknDiagnosticStorage.enqueueMirror` → `ensureFile` = `findChild` o `createDocument`) corre **por evento**; en proveedores con sincronización nube (consistencia eventual) el `findChild` no ve el archivo creado ms antes y crea otro con el mismo nombre → el proveedor renombra a `name (1)`, `(2)`… — 111 archivos-evento en esta sesión. Además `mirrorRecoveryFiles()` crea documentos **sin chequeo de existencia** en cada arranque. Y la retención (10 archivos/10 MB) borró los logs de las generaciones 2-4, 6-7 y 9-10 — justo la evidencia de los reinicios.

### 5.2 Otras inconsistencias
- Dos esquemas JSONL distintos para el mismo evento (con/sin `eventId`, `processName`, `namespace`); recovery usa `schemaVersion:2`. Unificar.
- `sessionId` inconsistente: `voice-process::generation-N` vs `<programId>::<uuid>`; las generaciones saltan de 5→8→11 (3 starts por enable).
- Duplicación de mapas de números en `WorkoutVoiceInput.kt:642-699` y `WorkoutVoiceCommandParser.kt:686-696` (ya divergirán).
- El doc de diseño contradice el código (§B4): "CONFIRM/CANCEL operan en LISTENING" — falso.
- `CONTINUOUS_MUSIC_FIRST` existe como alias deprecado pero el engine siempre adquiere `CONTINUOUS_VOICE_FIRST` — deuda confusa.
- `WorkoutVoiceRuntime.requestStopCapture*/stopEngine*WithoutUiCallback` (88-109) sin llamadores — la app nunca libera voz al ir a background.

## 6. Análisis de rendimiento

| Métrica (sesión 89 min, gen 11) | Valor | Evaluación |
|---|---|---|
| `voice_capture_health` | 1053 eventos (1/5 s), 100% healthy, 0 readErrors | Captura estable ✔ |
| Señal mic (`rmsAvgDb`) | −36…−42 dB | Baja → degrada ASR; considerar AGC o aviso al usuario |
| `audio_route_request` | 160 (119 redundantes) | Re-adquisición por cada ciclo TTS; cachear ruta activa |
| TTS/START | 126 en 2 h; ráfagas de 3 en 77 ms pre-crash | Sin tormenta salvo en modo Música (B1) |
| Escritura de log | 1 línea + `fd.sync()` **en el hilo llamante** por evento + 2 espejos SAF asíncronos | `fd.sync` por evento en hilo principal (command_parsed, TTS) = jank y batería; mover a writer único con batching |
| Memoria | RSS 365 MB en crash (main), `:voice` kill por LMK | Vosk FST + cache 2 recognizers + largeHeap; ver B7 |
| Disponibilidad de voz | 2 h 23 min sesión: 48 min sin voz (B7) + 2 min arranque (B9) + 14 min post-crash | **Disponibilidad real ≈ 55%** |
| Batería/IO | 121 archivos creados en el proveedor SAF por 1 sesión | El espejo por evento amplifica IO ×3-4 |

## 7. Plan de mejoras propuesto (priorizado)

**P0 — Estabilidad y datos (esta semana)**
1. B1a: `UpdateCaptureMode` debe esperar cierre completo del actor/recognizer antes de reabrir (o serializar por generación y descartar callbacks de generaciones viejas). *Archivos: `WorkoutContinuousVoiceEngine.kt`, `WorkoutVoiceForegroundService.kt`.*
2. B1b: dedupe de finales idénticos <500 ms + guarda idempotente en `doConfirm` + persistencia con exerciseId capturado al armar. *Archivos: `WorkoutVoiceController.kt`, `WorkoutVoiceCommandHandler.kt`.*
3. B2a: agregar "y" a `defaultNumericGrammarTokens()`; B2b: `gymDecimal` solo con fracción ∈ {5, 25, 50, 75} o ≤5; tests con "setenta y siete" / "cincuenta y uno".
4. B4: gramática de LISTENING con sí/no cuando hay `pendingClarification` (o mover aclaraciones a CONFIRM_WAIT). *Archivos: `WorkoutVoiceCommandParser.kt`, `WorkoutVoiceGrammarBuilder.kt`.*
5. B3a: no stale-drop de finales de confirmación en CONFIRM_WAIT (grace ±1 epoch); B3b: pausar decoder en re-preguntas.

**P1 — Funcionalidad pedida y alucinaciones**
6. B5: conectar `voicePendingFeedbackExerciseIds` → prompt al iniciar rest (calidad técnica 1-10) + nueva pregunta de molestias en `WorkoutTtsManager`; eventos `feedback_prompt_shown`/`feedback_registered`.
7. B6: `pauseAndAwait` en todos los caminos de habla; guardia post-TTS 600 ms; watchdog con `tts.stop()`; prompts sin auto-gatillantes; no promover parciales ±1 s post-TTS.
8. B7/B8: unload de modelo en continuo tras inactividad; `RECOGNIZER_CACHE_SIZE=1`; evento `voice_stop_requested origin=…`; reintento MIC_BUSY con aviso al usuario.

**P2 — Robustez ASR**
9. B10: correcciones de mishearing para tokens cortos ("voz"→"dos" en contexto "rir _"); calibrar confianza (promedio de word-conf de Vosk ya disponible) o eliminar el gateo; AGC/aviso de señal baja.
10. B11: ampliar CANCEL_KEYWORDS ("borrar", "elimina", "quita", "olvida", "incorrecto").
11. B12: reset de `VoskUtteranceAccumulator` tras parse/aclaración.
12. B13: número decimal solo → proponer como peso del ejercicio actual.

**P3 — Logging, rendimiento y deuda**
13. B13-log/5.1: un solo logger; espejo SAF por sesión (no por evento) con `ensureFile` cacheado; quitar `fd.sync()` del hilo llamante; retención por sesión completa (no perder generaciones); unificar esquema (eventId+traceId+processName siempre); nombre de archivo = traceId siempre.
14. B14/B15: etiqueta de ruta desde la fuente actual al emitir el evento; dedupe/serialize `acquire()`; cachear ruta activa entre pause/resume.
15. Deuda: eliminar alias deprecado, liberar voz en background o eliminar API muerta, unificar mapas de números, corregir `docs/PLAN VOZ POTENCIADA.md`.

**Verificación sugerida:** tests unitarios para números compuestos y gymDecimal; test de integración del flujo confirmación con finales atrasados (race epoch); test de `UpdateCaptureMode` (single-actor); test de dedupe de `doConfirm`; test end-to-end de prompts de calidad/molestias; y una sesión de campo con los nuevos eventos `feedback_*` + `voice_stop_requested` para validar.

---

## 8. Estado final de los archivos de log

- 111 archivos renombrados de `kpkn-voice-….jsonl (N)` → `kpkn-voice-… (N).jsonl` (0 colisiones, 0 remanentes con extensión errónea; 121 archivos en total en `KPKN/voice`).
- No se detectaron duplicados exactos por hash entre archivos (cada archivo-evento es un evento distinto).
- Los archivos pequeños `2c5dac55`/`a68501fc`/`68ebeb99`/`effadacc` son el espejo por evento de las sesiones grandes (mismo contenido, esquema extendido) — redundantes para análisis futuros si se conserva la sesión completa.
