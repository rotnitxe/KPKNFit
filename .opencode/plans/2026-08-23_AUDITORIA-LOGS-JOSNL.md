# Auditoría JOSNL KPKN — 2026-08-22 y 2026-08-23

**Fuente:** `C:\Users\valen\CrossDevice\Z Flip5 de Matias (1)\storage\KPKN\KPKN\logs\` — 4 áreas: `app`, `workout`, `voice`, `nutrition` — dividido por fecha `20260822` y `20260823`
**Método:** 4 subagentes Muse Spark 1.2 Free XHIGH en paralelo (app/auge, workout, voice, nutrition) + verificación central con python. Cada subagente releyó sus `*.jsonl` con evidencia `file:line timestamp`.
**Día principal analizado:** 2026-08-22 (sesión larga 17:33-21:25, 232 min). 2026-08-23 como contraste (sesiones fantasma).
**Niveles:** 0 eventos `level:ERROR/WARN` en todo el dump → logging sin severidad, oculta bugs.

---

## 1) APP / General — `app/20260822/app-145022.jsonl:1` (399 líneas), `app/20260823/app-part591-000423.jsonl:1` (158 líneas)

| ID | Sev | Hallazgo | Evidencia | Impacto |
|---|---|---|---|---|
| APP-01 | media | **Duplicado sequence 142** mismo `eventId 4919a3cc-0bdc-47a5-9ca8-b84a321f66b3` `app_foreground` 2× `2026-08-22T16:27:57.391Z elapsed 5855299` | `app-145022.jsonl:142,143` Counter dup [142] | FileLogger flush no idempotente, infla métricas foreground |
| APP-02 | info | Gaps secuencias 4..588 con 398 únicas, missing `[7,8,9,10,15,22,42,76...]` | `python Counter` vs `workout/nutrition` seqs | No es bug: secuencias globales shardeadas por área. Falta doc, confunde auditoría |
| APP-03 | alta | **AUGE estancado intra-workout**: `overall 100→79` clavado 4h (30 sets), luego salto `60/67` post-finish. `muscular 100 neural 68 spinal 71 historyCount 0 cumulativeFatigue 0.0` durante entreno, `51.7 history 1` solo al final | `app-145022.jsonl:18 (100),196 (79),585 (60),586 (67)` + `app-part591:00:04:24 67, 00:06:18 69 ... 05:36:52 70` | `FatigueEngine` solo lee history persistido, no sets live. Readiness falso todo el entreno |
| APP-04 | baja | `logs_health_check:11` `safMirror unconfigured` `rotationBytes 1048576 retention 30 queueDepth 6` | `app-145022.jsonl:11` | Sin mirror SAF, localStore ok, no crítico |
| APP-05 | baja | `permission_issue:13` `SCHEDULE_EXACT_ALARM blocker false` | `app-145022.jsonl:13` | Benigno |
| APP-06 | info | `deep_link_open 20:03:01 path training` | `app-145022.jsonl:383` | Ok |
| APP-07 | baja | `auge_computed durationMs 972ms` cold start vs 17-30ms warm | `app-145022.jsonl:18` | Normal |

---

## 2) WORKOUT (sesión sola) — `workout-3313ad2e-56b4-4094-b37e-49e58420872f-173301.jsonl:1` (persistence, 32 sets) y `workout-d27e15ad-6ebb-457c-8d18-af73fde53009-145022.jsonl:1` (rest_timer, 35 sets/42 timers)

**Sesión principal 3313ad2e** `session_started 2026-08-22T17:33:01.518Z` → `session_finished 2026-08-22T21:25:41.607Z` `workout-3313ad2e-173301.jsonl:193,579` duration 13959883ms 232min `completedSetCount 30 planned 30 volume 8837.5 stress 52.304` 9 ejercicios. Todos `set_persistence_succeeded` ok.

| ID | Sev | Hallazgo | Evidencia | Impacto |
|---|---|---|---|---|
| WO-01 | **crítica** | **Peso imposible Laterales** `81.0,75.0,70.0,68.0 kg x5 reps RPE 10/1` | `workout-3313ad2e-173301.jsonl:537,541,545,549` `2026-08-22T21:23:27-52` + dual `workout-d27e15ad:210` | Distorsiona volumen 8837.5 y stress. Factor x10 (8.1kg) o stack lbs no convertido |
| WO-02 | media | RPE/intensity flips `0↔10` sin fallo: `Press Francés 0,0,1` `Press Hombros 25kg x4 10, x4 10, x2 0 failedSet false` `Plancha 0kg 0→10→10` `Talones 160 10,140 10,140 0,120 10` | `workout-3313ad2e:466,490,503,511,553,557,561,565,573` | UI autocompleta intensity 0 cuando null, no refleja esfuerzo real |
| WO-03 | media | **Colisión exerciseId** `be25d58c` para Baja `[0,1] 130/110kg` y Alta `[0,1,2] 130/110/100kg` | `workout-3313ad2e:212,233,237,255,260` | Catálogo variantes sin ID propio, rompe FK y métricas por ejercicio |
| WO-04 | alta | **Rest timer tormenta** 4 eventos/1.5s `20:03:18.255,19.310,19.470,19.616` y 3/436ms `20:31:43.278,43.439,43.714` | `workout-d27e15ad:390-396,464-470` `elapsed 18776162-18777672` | Debounce faltante, timers solapados, UX confusa |
| WO-05 | media | **Schema dual divergente** persistence `weightKg/reps/intensity/rpe/completedSetCount/failedSet` vs rest `weight/value/unitMode/loadMode/rpe/rir/isFailure/metricType ERM metricValue` | `w1 keys {weight,weightKg,intensity...}` vs `w2 keys {metricValue,rir...}` | Dificulta auditoría y replay AUGE |
| WO-06 | media | Count mismatch 32 vs 35 sets, 42 timers → 3 sets fantasma, timers huérfanos `18:36:00` sin set | `w1 32` vs `w2 35` | Sesión con timers sin set (18:33:26 set sin persistence en dual) |
| WO-07 | media | **Sesiones fantasma 23-08** `d9039278 session_started 00:06:10 voiceEnabled false` + `00:22:25 planned 4` sin finish; `part706 05:36:49` sin finish | `workout-d9039278-000610.jsonl:611,657` `workout-part706:1` | Doble start sin close, abandonada o crash |
| WO-08 | baja | **Encoding** `Extensión Cuádriceps`, `Press Francés/Inclinado`, `Plancha Copenhague Dinámica`, `Elevación Talones`, `Sesión Sábado` → `�` | `workout-3313ad2e:all` + `workout-d903:sessionName Sesión Sábado` | Logger no fuerza UTF-8 |
| WO-09 | info | `rpe null` 23-08 `Aducciones 52→42→10kg` drop 76% último set | `workout-d27e15ad-part617:615,620,624` y `workout-d903:618,622,626` | 10kg pinta error entrada |
| WO-10 | info | 53 ciclos `activity_start/stop` durante 232min | `app-145022:53 stops` | Device sleep/wake normal en descansos largos |

---

## 3) VOZ (modo voz sesión) — `voice-3313ad2e-193121.jsonl:1` (16 eventos, main) + `voice-generation-1-193122.jsonl:1` (21 eventos, :voice)

| ID | Sev | Hallazgo | Evidencia | Impacto |
|---|---|---|---|---|
| VO-01 | media | **Enable vacío 3.9s** `19:31:21.667 request → 19:31:25.579 disable` sin comandos, `session_summary durationMs 6859972 commandsOk 0 nativeFallbacks 0` (duración es workout 1h54 no voz) | `voice-3313:340,349,581` `elapsed 16859574-16863486` | Wakeup Vosk inútil, gasto batería |
| VO-02 | baja | Duplicado `audio_route_request` 121ms y 123ms `mode CONTINUOUS_VOICE_FIRST music_mode_suppressed` | `voice-generation-1:11,12 elapsed 121,123` | Doble request |
| VO-03 | baja | `voice_capture_gate discardedFrames 1 reason config_callback_pending_or_silenced` | `voice-generation-1:20 elapsed 1853` | Race config_callback |
| VO-04 | baja | `audio_route_observed phone requested phone recordDeviceId 20` ok | `voice-generation-1:19` | — |
| VO-05 | baja | Doble stop `binder_stop 3805ms` + `self_destroy voice_process_stopped 3981ms` 176ms | `voice-generation-1:23,25 elapsed 3805,3981` | Doble destroy ruidoso |
| VO-06 | info | `MODEL_LOAD START 805ms → READY 1602ms` `RECOGNIZER_CREATE grammarHash -790104689` OK | `voice-generation-1:15,16` | Performance ok |
| VO-07 | media | `voice_environment batteryOptimizationIgnored false bluetooth true interactive true powerSave false` | `voice-3313:339` `voice-generation-1:8` | Foreground matable por Doze, avisar whitelist |
| VO-08 | info | `native_fallback_changed false`, `pipeline RECONNECTING→TTS_SPEAKING→DISABLED`, `TTS START/DONE` | `voice-3313:341-344,350-352` | Pipeline ok |
| VO-09 | media | **2026-08-23 sin voz**: `voice-001715,052333,053654` solo `area_bootstrap` 1 línea pese a workouts | `voice/20260823/*.jsonl:2` | Voz no inicializada ese día, aunque sesión lo esperaría |

---

## 4) NUTRICIÓN — `nutrition-145022.jsonl:1` (26 líneas) vs vacíos 23-08

| ID | Sev | Hallazgo | Evidencia | Impacto |
|---|---|---|---|---|
| NU-01 | info | `catalog_import 5766 rows v8 14:50:22.590→28.063 5473ms` ok | `nutrition-145022:15,22` | — |
| NU-02 | **alta** | **Resolve lento** `analysis 15:01:36.777→40.437 3659ms` con `resolve_tags 3455ms` (94%) | `nutrition-145022:87,88 seq 10,11 duration 3455` | Tag lookup sin índice, 5766 scan |
| NU-03 | alta | `analysis_end items 2 tags 2 resolved 0 engine deterministic aiInferred 0 kcalRangeKnown false outcome completed` → tags sin resolver, kcal desconocida | `nutrition-145022:89` | Parser falla, requiere selección manual |
| NU-04 | media | `candidate_selected x5 rank 0` `15:01:54,15:02:06,17,18,18.9s` manual | `nutrition-145022:91-95` | Ranking no discrimina, usuario cicla 36s |
| NU-05 | media | `meal_saved 15:02:30 foodCount 2 BREAKFAST date 2026-08-22` + `save_log foodCount2 tagCount2 fromDescription true descLen49` ok pero contradict `resolved 0` | `nutrition-145022:96,97` | Guardado ok pese a unresolved |
| NU-06 | baja | `nutrition_open 14:59:28,15:00:58,16:28:27` solo 1 save | `nutrition-145022:42,76,151` | — |
| NU-07 | info | 23-08 `nutrition-001715,052333,053654,193121,212542` solo `area_bootstrap` (process :voice curioso) | `nutrition/20260823/*.jsonl:3` | Sin análisis ese día pese a app abierta |

---

## Síntesis cross-cutting
- 0 WARN/ERROR oculta bugs que deberían ser WARN (peso >50 laterales, RPE flip, resolve 0).
- Dual-log workout explica gaps secuencias pero duplica verdad: persistence vs rest son 2 vistas del mismo set con schemas distintos → unificar.
- AUGE y volumen acoplados al bug peso lateral → corregir peso cambia stress 52.3 y cumulativeFatigue 51.7.
- Encoding y dup seq son deuda logging, no funcional pero ensucian auditoría.
- Voz y nutrición el 23-08 vacíos indican día sin uso real voz/nutri, no error pero confirma 22-08 es día representativo.

Subagentes: 4× Muse Spark 1.2 Free XHIGH paralelos verificaron cada área con relectura python; resultados agregados aquí. Logs re-verificados 2026-08-23T execution: counts, dups, bursts, AUGE histories confirmados.

Plan fixes → `.opencode/plans/2026-08-23_audit-logs-fixes.md` (flags [voice,auge,nutrition,room], secciones Rutas/Impacto/Pruebas/Riesgos).
