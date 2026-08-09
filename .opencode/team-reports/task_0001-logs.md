# INFORME FORENSE — Sesión de voz KPKN Fit (2026-08-07)

**Device**: samsung SM-F731B (b5q), SDK 36, auricular BT `bt_sco:ULT WEAR`. Sin audio guardado (`audioStored: false` en todos los traces).
**Artefactos de análisis**: `analyze1..6.py` y `sem_dump.txt`, `analyze3_out.txt`, `analyze4_out.txt`, `analyze5_out.txt`, `analyze6_out.txt` en `.opencode\tmp-voice\`.

## 1. Estructura de archivos y sesiones (paso 1)

| Archivo | Líneas | Primer TS | Último TS | Contenido |
|---|---|---|---|---|
| `...151949-07709bc8` | 23 | 15:19:49.014 | 15:19:56.682 | Pipeline UI (enable→disable, sin ASR) |
| `...151949-b52a46d1` | 31 | 15:19:49.145 | 15:19:55.334 | Capture `generation-1` (cierra limpio con `diagnostic_closed`) |
| `...153838-51555bda` | **1306** | 15:38:38.970 | **17:15:12.192** | Capture `generation-1`: 1145 `voice_capture_health` |
| `...153838-cac8314a` | 648 | 15:38:38.825 | **17:27:17.311** | **Trace semántico** (ASR/comandos/persistencia) |
| `...171512-31f993c7` | 199 | 17:15:12.837 | **17:27:12.261** | Capture `generation-3`: 142 `voice_capture_health` |

Los 5 files comparten `programId: 20df5bbe-...` y los de pipeline comparten `sessionId: c2331783-f0cc-4051-83da-8b9048ca6a2a` → **es UN solo workout**, con voz habilitada en 3 segmentos: (a) 15:19:49–15:19:56 (~7 s, abortado: `voice_disable_requested` 15:19:55.161), (b) 15:38:38–17:15:12, (c) 17:15:12–17:27:17.

## 2. Flujo de reconocimiento/parse/acción alrededor de "cargas" (paso 2)

Patrón repetido por serie (ventana típica ~30–40 s), ejemplos exactos:

- **15:41:53.022** `vosk_fragment` L21 → **15:41:55.244** `asr_final {transcript: "ciento sesenta kilos cinco repeticiones rir dos", confidence: 0.0, confidenceKnown: false, route: "phone"}` L22 → **15:41:55.691** `command_parsed {commandType: "n25", command: "RegisterSet(...weightKg=160.0, metricValue=5, intensityValue=2.0, intensityKind=RIR...)", exerciseId: d3d1df8c..., setIndex: 1}` L24 → TTS 15:41:55.701→15:42:01.131 (lectura de confirmación) → `confirmation_rearm_requested {stage: CONFIRM_WAIT}` 15:42:01.447 L29 → `confirmation_input_received {transcript: "sí"}` 15:42:03.336 L30 → `confirm_capture_paused {acknowledged: true}` 15:42:03.420 → `set_persistence_started/succeeded` 15:42:03.448/.946 L36/40 → `feedback_prompt_shown {origin: "voice_rest_start"}` 15:42:03.601 L37.

**Series persistidas (9)** — extracto de `set_persistence_succeeded`:

| Hora | Línea | Ejercicio | Set | Transcript / peso |
|---|---|---|---|---|
| 15:42:03 | L40 | d3d1df8c | 1 | "ciento sesenta kilos…" 160 kg ×5 RIR2 |
| 16:27:28 | L112 | 64b9c35d | 0 | 100 kg ×5 RIR1 |
| 16:36:24 | L162 | 64b9c35d | 1 | "…rir voz"→corregido a RIR2, 90 kg ×5 |
| 16:50:11 | L214 | 45f4c053 | 0 | 80 kg ×5 RIR2 |
| 16:54:33 | L262 | 45f4c053 | 1 | 75 kg ×6 RIR2 |
| 17:02:33 | L340 | c8187119 | 1 | 65 kg ×5 RIR2 |
| 17:07:22 | L410 | c8187119 | 2 | 60 kg ×5 RIR2 |
| 17:10:59 | L461 | c8259042 | 0 | 30 kg ×5 RIR1 |
| 17:20:05 | L581 | 842f236e | 0 | "**veinte kilos veinte kilos** cinco repeticiones rir dos" (duplicado ASR) 20 kg ×5 |

**Preguntas de carga (`guided_clarification`)**, todas con `slot: "WEIGHT"`:
- 16:59:46.523 L289 `MissingSlot` tras "setenta repeticiones" (sin peso, `fields=[VALUE]`) → resuelta `cancelled` 17:02:08.933 L316 tras decir solo "kilos" (17:02:08.623).
- 17:06:41.495 L368 `ConfirmSuggestedLoad` tras "sesenta repeticiones cinco" → usuario responde "sesenta kilos cinco repeticiones rir"/"esta kilos" → resuelta `cancelled` 17:07:03.677 L385; la serie se re-dictó entera (17:07:14) y persistió.
- 17:14:27.789 L490 `ConfirmSuggestedLoad` tras "veinticinco repeticiones" → usuario dice "lado me quedo" (17:14:36) y "veinticinco kilos" (17:14:43) → `guided_clarification_resolved {kind: "MissingSlot", slot: "WEIGHT", result: "value"}` 17:14:43.768 L507 + **nuevo** ask `ConfirmSuggestedLoad` 17:14:43.785 L509 → **nunca resuelto** (ver hallazgo 7).
- 17:22:23.323 L610 `ConfirmSuggestedLoad, escalated: true` tras "crear subir set"/"subir set" → `unknown_command_logged` con `secondUnresolved: true` (17:22:23.226 L609) → resuelta `cancelled` 17:25:02.067 L631 tras "terminar apaga".
- 16:36:38.415 L170: "sugerido" → `commandType: "c35"` (aceptar carga sugerida) — único uso exitoso.

Nota: los substrings **'carga', 'peso', 'kg', 'serie(s)' NO aparecen** en ningún campo de texto de ningún archivo (verificado por scan sobre todos los strings). El usuario dicta solo "X kilos Y repeticiones rir Z".

## 3. Evidencia de prompts de calidad/técnica/molestia/dolor (paso 3)

- **'calidad' y 'dolor': CERO apariciones** en cualquier campo de texto de los 5 archivos de sesión (scan exhaustivo).
- **'tecnica': 1 sola aparición, y es habla del usuario**: `vosk_fragment` 17:15:01.002 L521 y `asr_final {transcript: "tecnica diez de diez", route: "phone"}` 17:15:03.210 L522 — exactamente 9–11 s después de `feedback_prompt_shown {exerciseId: c8259042..., origin: "voice_rest_start"}` (17:14:52.332 L515) y de un TTS 17:14:52.402→17:14:57.390. **Conclusión: el post-set feedback prompt sí pregunta por técnica y el usuario respondió "técnica diez de diez"**; esa respuesta NO generó `command_parsed`/evento de respuesta de feedback logueado.
- **'molestia': solo en texto TTS del resumen final**: `session_summary_announced {text: "Tu sesión fue intensa. No registraste molestias. Tu RING muscular quedó en 99 por ciento…"}` ×3 (17:26:40.417 L639, 17:26:46.252 L640, 17:27:10.398 L646).
- **Momentos de los 7 feedback prompts** (todos `origin: "voice_rest_start"`, sin campo de texto — el texto no se loguea): 15:42:03.601, 16:36:24.445, 16:54:33.460, 17:07:21.734, 17:14:52.332, 17:24:38.906, 17:24:51.727. Tras cada uno hay ventana TTS (ej. 16:54:33.806→16:54:41.862). Las respuestas del usuario a estos prompts quedan como `unknown_command_logged` ("confirmar" ×8, "sí" ×1) → fricción sistemática.
- Además `transcript_corrected {from: "noventa kilos cinco repeticiones rir voz", to: "…rir dos"}` 16:36:14.070 L145: Vosk confunde "dos"→"voz".


## 4. Final de la sesión (paso 4)

Secuencia exacta (cac8314a):
1. 17:24:59.779/17:25:01.984: usuario dice **"terminar apaga"** (L628/629) → resuelve la clarificación pendiente como `cancelled`.
2. 17:26:40.417 `session_summary_announced` ("…RING muscular 99%… energía 86% y tu columna en 95 por ciento. No existe una próxima sesión programada fiable. **Para finalizar, di sesión terminada.**") — se repite 17:26:46.252 (variante: "columna en **100** por ciento") y 17:27:10.398.
3. 17:27:11.671 L647 **`workout_completed`** (sin campo extra; sin ASR que lo dispare ─ probable tap en UI).
4. 17:27:17.311 L648 **`export_started`** — **última línea del archivo**. No existe `export_finished`, ni `diagnostic_closed`, ni más eventos.
5. El trace de captura 31f993c7 termina incluso antes: último evento `voice_capture_health {healthy: true, route: "phone"}` 17:27:12.261 L199, **sin `voice_stop_requested` ni `diagnostic_closed`** (comparar con el cierre limpio de 51555bda, que sí tiene ambos).

## 5. Cruce con recovery logs (paso 5)

Recovery (schema 2, `event: application_exit`, proceso `com.example.kpkn`):

| TS | reason | reasonCode | importance | rssKiB |
|---|---|---|---|---|
| 2026-08-06 17:55:12.337 | JAVA_CRASH | 4 | 100 | 188344 |
| 2026-08-06 17:55:43.905 | JAVA_CRASH | 4 | 100 | 0 |
| 2026-08-07 00:05:45.859 | LOW_MEMORY | 3 | 400 | 39384 |
| 2026-08-07 14:48:28.436 | LOW_MEMORY | 3 | 400 | 72096 |

**Conclusión: NO, la sesión grande (15:38) no murió por LOW_MEMORY al final.** Evidencia:
1. El último LOW_MEMORY (14:48:28) es **50 minutos antes** del inicio de la sesión; ambos LOW_MEMORY ocurrieron con `importance: 400` (background), mientras que durante el workout el proceso registró `processImportance: 100/125` (foreground) en los ~1290 eventos que lo reportan — los kills por low memory de foreground son rarísimos.
2. La captura estuvo sana hasta el final: 1145 `voice_capture_health` en 51555bda + 142 en 31f993c7, **100% `healthy: true`, 0 `readErrors`, 0 `silenced: true`**, cadencia regular de ~5 s (gap máximo 5.155 s).
3. El corte de 51555bda a las 17:15:12 **fue deliberado, no muerte**: `voice_disable_requested` 17:15:11.804 → `voice_stop_requested {origin: "binder_stop"}` 17:15:11.816 → `{origin: "self_destroy", reason: "voice_process_stopped"}` 17:15:12.187 → `diagnostic_closed {reason: "voice_process_stopped"}` 17:15:12.192 (L1303–1306); 645 ms después nace `31f993c7` (`diagnostic_started {sessionId: "generation-3"}` 17:15:12.837, `voice_enable_result {enabled: true}` 17:15:12.821, LISTENING 17:15:14.082).
4. **PERO**: la terminación a las ~17:27:12–17:27:17 es abrupta y no registrada — ambos traces activos cortan sin `diagnostic_closed` y `export_started` queda sin cierre. No existe recovery file con timestamp ≥17:27 (si el sistema hubiera matado la app ahí, el próximo arranque habría generado un `application_exit` nuevo; no existe). La lectura consistente: el workout se completó (17:27:11), comenzó el export (17:27:17.311) y la sesión de diagnóstico se cerró sin escribir el evento de cierre (o el proceso terminó en esos ~5 s sin flush). **Sin evidencia de kill externo; hay evidencia de fin de traces no persistido/ abrupto post-`workout_completed`.**



## 6. Sesión posterior `kpkn-voice-20260807-171512-31f993c7.jsonl` (paso 6)

No es una sesión nueva: es la **continuación del mismo workout** tras la rotación del voice-process. 199 líneas, 17:15:12.837→17:27:12.261: solo telemetría de captura (142 `voice_capture_health`, 23 `voice_phase`, 23 `audio_route_request`), 1 `vosk_empty_final_partial_used`, 1 `audio_route_observed {route: "bt_sco:ULT WEAR", recordDeviceId: 8777}` 17:15:13.858. Sin `diagnostic_closed` (ver hallazgo 5.4). Toda la semántica de ese tramo está en cac8314a (el último ejercicio 842f236e 20 kg, intentos "subir set", clarificación escalada, summary ×3, `workout_completed`, `export_started`).

## 7. Hallazgos adicionales de fricción/bugs observados

1. **10 confirmaciones fantasma**: tras cada `set_persistence_succeeded`, el usuario repite "confirmar"/"sí" y cae como `commandType: "b35"` `Unknown(raw=confirmar)` + `unknown_command_logged` (15:42:20, 16:27:37, 16:50:19, 16:54:50, 17:02:41, 17:07:38, 17:11:07, 17:20:13) — incl. 7 salvados por `vosk_empty_final_partial_used`.
2. **`transcript duplicado por Vosk`** al retomar dictado: "veinte kilos veinte kilos cinco repeticiones rir dos" (17:19:56) y persistió con `weightKg=20.0` correcto pero transcript contaminado.
3. **Clarificación huérfana**: `ConfirmSuggestedLoad` L509 (17:14:43.785) jamás recibió `guided_clarification_resolved` — quedó colgada durante el disable/re-enable de 17:15:11–12; la siguiente pregunta escalada solo llegó 7.6 min después tras dos unknowns.
4. **Resumen anunciado 3 veces** con una inconsistencia de datos: "columna en 95 por ciento" (17:26:40 y 17:27:10) vs "columna en 100 por ciento" (17:26:46) — el texto TTS se regeneró entre anuncios.
5. Rutas de audio: el workout empezó por micrófono del teléfono (15:38–16:24), cambió a BT `bt_sco:ULT WEAR` (~16:24:06, `sco_link_state`), perdió la ruta BT a las 16:53:09 (`audio_route_revoked` ×3, removedIds [8477, 8478, 8481]) y siguió por `phone` hasta el final; nueva conexión BT a las 17:15:13 (`communication_device_selected`) y de nuevo `phone` a las 17:15:21 (`music_mode_suppressed`).

**Scripts usados**: `analyze1.py` (conteos/timestamps), `analyze2.py` (dump semántico UTF-8), `analyze3.py` (timeline + keyword scan), `analyze4.py` (detalles completos + tails/heads + variantes de salud), `analyze5.py` (restart, rutas, sesión 15:19, gaps, unknowns), `analyze6.py` (ventanas TTS + tabla de sets). Todo parseo hecho con `json.loads` en Python 3.14 — ningún `ConvertFrom-Json`.
