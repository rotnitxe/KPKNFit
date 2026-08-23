---
flags: [voice, auge, nutrition, room]
---

# Plan — Fixes auditoría logs 2026-08-22/23

## Rutas
- `android-native/app/src/main/java/com/example/kpkn/data/db/` — índices nutrición, migración si hace falta, schema v23
- `android-native/app/src/main/java/com/example/kpkn/domain/auge/` — motor fatiga live (historyCount, cumulativeFatigue)
- `android-native/app/src/main/java/com/example/kpkn/domain/workout/` + `domain/exercises/` — validación peso, exerciseId variantes Sentadilla, RPE/intensity mapping, encoding UTF-8
- `android-native/app/src/main/java/com/example/kpkn/services/workout/` — Voice foreground (Vosk), `WorkoutVoiceService`, `VoicePipeline`, `AudioRoute`, `RestTimerManager` / debouncing
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/` + `screens/nutrition/` — UI validación, ranking candidatos, tag resolve
- `android-native/app/src/main/java/com/example/kpkn/data/logging/` — FileLogger duplicado secuencia 142, health SAF, level WARN
- `docs/ARCHITECTURE.md`, `docs/ANDROID_ARCHITECTURE_MAP.md`

## Impacto
- Corrige pesos imposibles (laterales 68-81kg) que distorsionan volumen 8837.5kg, stress 52.3 y cálculo AUGE.
- Corrige fatiga AUGE estancada 79 durante 232 min (neural 68/spinal 71) → readiness/fatiga real.
- Elimina tormenta rest_timer (4 eventos/1.5s) y sesiones fantasma 2026-08-23 (doble session_started sin finish).
- Reduce resolve_tags de 3455ms (94% del análisis nutrición) y fallos resolved 0 / kcal unknown.
- Normaliza encoding � y exerciseId colisionado be25d58c (Baja vs Alta).
- Reduce wakeups voz (4s enable sin comandos, duplicado audio_route) y descartes de frames.

## Pruebas
- Unitarias: `SessionTemplateCatalogTest`, `AugeEngineTest`, `NutritionParserTest`, `WorkoutLoggerTest` — asserts peso lateral >50kg warn, exerciseId variantes, RPE 0/10 mapping, rest debounce, sequence idempotencia.
- Instrumentadas/dirigidas: `testBaseDebugUnitTest --tests '*.RestTimerTest'` y `*VoicePipelineTest*` + logs simulados 2026-08-22 replay
- Validación manual: `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "test"` y `assembleDebug`; reproducir sesión 3313ad2e 32 sets y comprobar AUGE live, volumen corregido, sin bursts
- Verificación logs post-fix: re-exportar jsonl y correr script auditor (0 level ERROR, 0 pesos >50 laterales, seq sin dup, resolve_tags <500ms, kcalRangeKnown true)

## Riesgos
- Cambiar exerciseId de Sentadilla rompe FK histórica → mitigar con migración Room + alias mapping
- Validación peso estricta puede bloquear máquinas con stack alto → poner WARN no BLOCK + config por ejercicio
- AUGE live requiere recalcular histórico → divergencia con v23 schema, testear retrocompat
- Debounce rest_timer puede ocultar timer legítimo corto → ventana 500ms y kind STANDARD

## Plan por fases
### Fase 1 — Logging y encoding (bajo riesgo)
- Fix FileLogger duplicado seq 142 (`data/logging/FileLogger.kt:??` — flush idempotente, dedup por eventId)
- Fix UTF-8 exerciseName/sessionName (Extensión, Elevación, Sesión Sábado) — forzar `StandardCharsets.UTF_8` en logger y Room `TypeConverter`
- Añadir level WARN para peso improbable y RPE inconsistente
- SAF mirror still unconfigured → log health ok, no fix requerido ahora

### Fase 2 — Workout core (alto impacto)
- `domain/exercises/ExerciseCatalog` — separar IDs Baja vs Alta (be25d58c colisión) + migración
- `domain/workout/SetValidator` — validación peso por grupo muscular: laterales max 30kg, talones max 300kg con escala; unidad kg/lbs; corregir 81→8.1 si factor 10
- Normalizar `set_recorded` schema dual: unificar `intensity/rpe/failedSet` vs `metricValue/ERM/rir` en single source (WorkoutRepository)
- RPE null (Aducciones 2026-08-23) vs 0 → distinguir "no informado" null de 0 esfuerzo; UI no autocompleta 0
- `RestTimerManager` debounce 500ms, coalescer bursts 20:03:19 y 20:31:43
- `WorkoutSessionManager` fix doble session_started (00:06:10 y 00:22:25) — guard `if activeSession != null` y `session_finished` siempre al abandonar

### Fase 3 — AUGE live
- `domain/auge/FatigueEngine.kt:??` — alimentar historyCount live desde sets de sesión activa (no solo history persistido); recalcular muscularBattery/neural/spinal por set; cumulativeFatigue 51.7 debe crecer incremental no solo post-finish
- ContextHash f55716eb debe actualizar por bloque, no solo fin
- Test: replay 32 sets y assert overallScore decrece 100→79→60 progresivo no salto

### Fase 4 — Voice
- `services/workout/VoicePipeline` — evitar enable 3.9s vacío: no auto-enable sin comando, o timeout 30s; deduplicar `audio_route_request` (121/123ms)
- Corregir `voice_capture_gate discardedFrames 1 config_callback_pending` — esperar config_callback antes de LISTENING
- Consolidar doble stop binder_stop + self_destroy → single destroy chain
- Corregir session_summary durationMs 6859972 (workout) vs voz 4s → medir `voiceActiveMs` separado
- BatteryOptimization prompt si `batteryOptimizationIgnored false` en `voice_environment`

### Fase 5 — Nutrición
- `domain/nutrition/ResolveTags` — indexar tags, cache datasetPrepare, target <500ms (hoy 3455ms); explicar `resolved 0` con `tags 2`
- Ranking `candidate_selected` x5 rank 0 — mejorar scoring por kcalRangeKnown false → fallback externo AI o pedir peso
- UI: no requerir 5 selecciones para save; `meal_saved` ok pero `analysis_end` debe alertar kcal unknown

## Auditoría resumida (fuente: 4 subagentes XHIGH Muse Spark 1.2 Free)
- **APP**: 399 líneas 22-08 app-145022, 0 ERROR. Dup seq 142 (eventId 4919a3cc). AUGE scores 100 (history 0) hasta 79 estancado 4h, luego 60/67 post-workout. SAF unconfigured, SCHEDULE_EXACT_ALARM blocker false benigno.
- **WORKOUT**: 32 sets (persistence) vs 35 (rest log) → 3 fantasmas. Laterales 81/75/70/68kg imposibles. Sentadilla ID colisión be25d58c. RPE 0/10 flips, Talones 10,10,0,10. Bursts rest_timer 4/1.5s. Sesiones 23-08 sin finish. Encoding �. Volumen 8837.5 stress 52.3 ok pero distorsionado.
- **VOICE**: 16+21 eventos 22-08, 0 comandos, enable 4s waste, duplicate audio_route, discardedFrames 1, doble stop, summary duration bug, 23-08 sin voz.
- **NUTRITION**: 26 eventos 22-08, import 5766 rows 5.4s ok, analysis 3659ms (resolve 3455), resolved 0 kcal false, 5 candidate selections, 1 meal BREAKFAST 2 foods, 23-08 empty.
