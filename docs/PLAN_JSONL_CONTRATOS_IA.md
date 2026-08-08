# Contratos JSONL v2 y Prompts de IA — Sistema de Diagnósticos KPKN

> Documento hermano de `docs/PLAN_MAESTRO_SISTEMA_JSONL.md`. Define formatos exactos
> para que Android, iOS, el script de PC y la IA externa hablen el mismo idioma.

---

## 1. Esquema base de evento (schemaVersion = 2)

Toda línea de todo JSONL lleva estos campos obligatorios:

```json
{
  "schemaVersion": 2,
  "eventId": "evt-<uuid>",
  "timestamp": "2026-08-08T15:04:22.113Z",
  "elapsedMs": 84210,
  "area": "voice | workout | nutrition | performance | auge | reports",
  "subsystem": "tts | assistant | programs | … (opcional)",
  "event": "set_recorded",
  "screen": "workout/{programId}/{sessionId}",
  "sessionId": "uuid-de-sesion-de-app-o-entreno",
  "traceId": "uuid-opcional-por-operacion",
  "process": "main | :voice",
  "...campos específicos del evento...": "…"
}
```

Reglas:
- Append-only, una línea JSON por evento, UTF-8, flush por línea (sobrevive a muerte del proceso — patrón ya probado en `WorkoutVoiceDiagnosticLogger`).
- Strings saneados (sin secretos; hereda las reglas de redacción actuales) y truncados a 12.000 chars.
- `screen` lo inyecta el bus desde `setCurrentScreen`; los productores no lo pasan a mano (salvo el proceso `:voice`, que lo recibe del controller).
- Los `Double`/`Float` no finitos se serializan como `null` (regla ya existente en NutriTelemetry).

## 2. Layout en disco

```
filesDir/kpkn_logs/
  voice/20260808/voice-<sessionId>-<hhmmss>.jsonl        (un archivo por sesión de voz)
  workout/20260808/workout-<sessionId>-<hhmmss>.jsonl    (un archivo por entreno)
  nutrition/20260808/nutrition-<fecha>-<seq>.jsonl       (rotación por tamaño)
  performance/20260808/performance-<fecha>-<seq>.jsonl
  auge/20260808/auge-<fecha>-<seq>.jsonl
  reports/report-<reportId>.jsonl                        (evidencia de comentarios)
  reports/daily/YYYY-MM-DD/*.md                          (salida IA, también espejo SAF)

SAF (espejo automático, misma jerarquía):
  KPKN/logs/<area>/<yyyyMMdd>/…
  KPKN/reports/…
```

Migración: `voice_diagnostics/`, `nutrition_telemetry/` y `kpkn_diagnostics/` se importan a la raíz nueva una sola vez (mover, idempotente, nunca borrar).

---

## 3. Catálogo de eventos por apartado (mínimos de contrato)

### I. `voice` — sesión en vivo con voz
Hereda los eventos actuales de `WorkoutVoiceDiagnosticLogger` (asr_final, asr_partial_relevant, command parsed/rejected, confirmation_*, set_persistence_*, voice_fgs_*, audio_route_*, etc.) y añade:
- `session_summary` {durationMs, commandsOk, commandsFailed, nativeFallbacks, voiceReports: [reportId], endedBy}
- `user_comment` {reportId, text, captureMs, retries} — espejo del comentario por voz dentro del JSONL de la sesión (conexión bidireccional).
- `report_voice_started / description_captured / saved / failed` (ya existen; se conservan).

### II. `workout` — sesión en vivo sin voz (nuevo)
- `session_started` {programId, sessionId, plannedExercises, voiceEnabled:false}
- `set_recorded` {exerciseId, setIndex, side?, weightKg?, reps?, timeSec?, rpe?, rir?, technique?:"dropset|rest_pause|failure|unilateral", source:"manual_ui"}
- `set_persistence_succeeded / set_persistence_failed` {exerciseId, setIndex, side?, error?}
- `rest_started / rest_skipped / rest_finished` {plannedSec, actualSec}
- `exercise_swapped` {fromId, toId, scope} · `superset_created/dissolved` {groupId, members}
- `edit_during_live` {field, exerciseId} · `finish_blocked_empty_session` {}
- `session_finished` {durationMs, setsDone, setsPlanned, volumeKg, savedLogId} / `session_abandoned` {reason}

### III. `nutrition` — registro por descripción
Hereda NutriTelemetry (session_start, trace por análisis con spans, in-flight, crash) y añade:
- `analysis_verdict` {engine:"local|deepseek|fallback", itemsTotal, itemsResolved, itemsReviewRequired, overallConfidence, elapsedMs, fallbackUsed}
- `macros_divergence` {aiCalories, heuristicCalories, deltaPct} — detector de inconsistencias IA vs heurística.

### IV. `performance` — rendimiento de la app
- `cold_start` {msToFirstFrame, msToInteractive} · `screen_open` {route, ms}
- `frame_jank` {route, jankPct, p95FrameMs} (muestreado, tope por minuto)
- `room_query_slow` {dao, method, ms} (>250 ms) · `catalog_load` {asset, decodeMs, indexMs, cached}
- `memory_pressure` {usedMb, availableMb, level} · `trace_started/stopped/metric` (API `KpknTelemetry.Trace`, ya existe)
- `logs_health_check` {area, filesToday, bytesToday, lastEventAgeMin, safMirror:"ok|lost|unconfigured"}

### V. `auge` — RINGS/AUGE
- `auge_computed` {contextHash, engines:{fatigue:{…}, recovery:{…}, ttc:{…}, readiness:{…}}, durationMs}
- `rings_state` {rings:[{id, value, target, pct}], transitionFrom?, anomalous:boolean}
- `auge_divergence` {kind:"editor_vs_live|uni_volume|…", expected, actual, ratio, exerciseId?}

### `reports` — comentarios del usuario (Pilar 3)
Hereda `report_created / report_context / report_ai_pending / report_ai_enrichment / report_ai_failed` de `KpknReportManager`, con `origin:"GESTURE|VOICE"`, `screen`, `category`, `reportId`.


---

## 4. Contrato de prompt — reporte diario por apartado (`daily-report-v1`)

**System prompt (versión fija, versionada):**

```
Eres el analista de calidad de KPKN Fit. Recibirás las líneas JSONL del día de UN
apartado, cada una prefijada con su referencia física [archivo#LíneaInicio-LíneaFin],
más los comentarios del usuario (manuales y de voz) intercalados por tiempo.
Devuelve EXCLUSIVAMENTE JSON válido con este esquema:
{
  "area": "...", "date": "YYYY-MM-DD",
  "summary": "...",
  "healthScore": 0-100,
  "facts": [{"text", "evidenceRefs": [{"file", "lineStart", "lineEnd", "eventId"}]}],
  "userClaims": [{"reportId", "text", "screen", "linkedEventRefs": [...]}],
  "hypotheses": [{"text", "basedOn": [evidenceRefs], "confidence": 0.0-1.0}],
  "inconsistencies": [{"text", "evidenceRefs": [...], "severity": "low|medium|high"}],
  "missingEvidence": ["..."],
  "suggestedChecks": ["..."],
  "tags": ["..."]
}
Reglas duras: nunca presentes hipótesis como hechos; toda afirmación lleva
evidenceRefs que existan en el bundle; no inventes eventos ni líneas; si el bundle
fue pre-agregado, cita el agregado y marca lo omitido en missingEvidence; no
propongas acciones destructivas.
```

**Pre-agregación determinista (antes de la IA):** ráfagas del mismo evento →
`{"aggregated": "frame_jank", "count": 312, "p95": …, "refRange": [archivo#L10-L320]}`.
Errores, crashes, comentarios y eventos raros **nunca** se agregan: van íntegros.

## 5. Formato del `.md` generado (lo que leés y subís a tu IA externa)

```markdown
# Reporte diario — VOZ — 2026-08-08
> healthScore: 72 · generado por deepseek-v4-flash (req …) · bundle: 1.204 líneas (948 citadas)

## Hechos
- La sesión de voz se cerró sin persistir 2 series. [voice/20260808/voice-a1b2-153022.jsonl#L118-L124]
…
## Lo que reportaste
- [rpt-…] "los rings se quedaron en 99%" (pantalla auge) → vinculado a [auge/…/auge-….jsonl#L44-L51]
## Hipótesis (etiquetadas, con confianza)
## Inconsistencias detectadas
## Evidencia faltante
## Chequeos sugeridos
```

Toda referencia es **verificable mecánicamente**: `scripts/validate_report_refs.py` comprueba que cada `file#line` citado existe y contiene el `eventId` citado. Un reporte con referencias inválidas se marca `refs_invalid` y se re-genera una vez; si persiste, se entrega marcado explícitamente como no verificado.

## 6. Contrato de proveedores IA

```
interface AiReportProvider {
  id: String                      // "deepseek-v4-flash" | "muse-spark-1.2" | …
  fun isConfigured(): Boolean     // clave/endpoint disponibles
  suspend fun completeJson(system, user, maxTokens): Result<Completion>
}
```
- **Primario:** DeepSeek V4 Flash (cliente existente).
- **Contributor (opcional):** "Muse Spark 1.2" — *no verificable hoy*; si su API existe, se registra como segundo provider y su salida se anexa como sección "Revisión del contributor" (discrepancias contra el reporte primario), nunca como reemplazo. Si no existe, la sección no se genera y el sistema no se degrada.
- El script de PC (`scripts/generate_daily_reports.py`) usa la misma interfaz vía variables de entorno (`KPKN_AI_PROVIDER`, `KPKN_AI_API_KEY`, `KPKN_AI_ENDPOINT`, `KPKN_AI_MODEL`), así cambiar de modelo no toca código de la app.

## 7. Validación continua
- `scripts/validate_jsonl_schema.py`: valida líneas exportadas contra el esquema v2 (campos, tipos, áreas válidas).
- `scripts/validate_report_refs.py`: valida evidenceRefs de los `.md`.
- Tests JVM: store, sanitizer, agregador, parser de respuesta IA, y contratos de eventos por apartado.
