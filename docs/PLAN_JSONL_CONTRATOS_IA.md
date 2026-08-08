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
