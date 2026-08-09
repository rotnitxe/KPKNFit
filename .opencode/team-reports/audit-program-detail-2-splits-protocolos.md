# Auditoría 2/5 — Splits, protocolos y plantillas de programa

> **Pista:** `SPLIT_TEMPLATES` (52), `ProtocolLibrary` (13), `ProtocolExerciseLibrary`, `ProgramProtocolEngine`, `PeriodizationEngine`, `SplitApplicationEngine`, `SessionPrefillBridge` · **Infra real vs promesa**

## Resumen ejecutivo

**Splits sí tienen infra real** (`SplitApplicationEngine` 30KB con `GLOBAL/PER_BLOCK × MIGRATE/CLEAN/PREBUILT`, `SPLIT_TEMPLATES` 52 entries, `SplitView` operativo). **Protocolos también generan estructura ejecutable** (vía `ProgramProtocolEngine` + `PeriodizationEngine`) pero hoy entregan **1 ejercicio por parte → 3 por sesión** sin variación por día del split (torso ≠ pierna). **Plantillas** existen (`SessionTemplates.kt` 3.287 líneas, `SessionTemplateSuggestionEngine`, `SessionTemplateCatalogPolicy`) — no es cosmética.

Riesgo: `defaultSplit` sin gate centralizado (fallback silencioso `ul_x4`) y `prefillIfEmpty` que deja semanas vacías parciales.

## Tabla de hallazgos

| ID | Sev | Título | Archivo:línea |
|---|---|---|---|
| S-01 | P1 | `defaultSplit` protocolos sin verificación centralizada; fallback silencioso `ul_x4` | `ProgramProtocolEngine.kt:248-263` `ProtocolLibrary.kt:14-15` (13 protocolos) `SessionPrefillBridge.kt:17-38` |
| S-02 | P1 | Protocolos generan 3 ej/sesión fijos (1 por parte) sin diferenciación por día del split | `ProgramProtocolEngine.kt:124-175,195-238` `ProtocolExerciseLibrary.kt:45-135` |
| S-03 | P1 | 5/3/1 modelado como 4 bloques ×1 semana: rampa % correcta pero reps no varían por semana (5+/3+/1+) | `ProtocolLibrary.kt:55-61` `PeriodizationEngine.kt:22-77` |
| S-04 | P2 | `SessionPrefillBridge.prefillIfEmpty` no-op si hay cualquier sesión → semanas vacías parciales | `SessionPrefillBridge.kt:45-59` `ProgramTemplateEngine.kt:58-65` |
| S-05 | P2 | `SessionTemplateSuggestionEngine` scoring no penaliza desbalance semanal fuerte | `SessionTemplateSuggestionEngine.kt` |
| S-06 | MEJORA | `ProtocolLibrary` como `List<Protocol>` en código: conviene asset JSON versionado si crece | `ProtocolLibrary.kt` |

## Hallazgos detallados

### S-01 — `defaultSplit` sin gate (P1)
**Evidencia:** `resolveSplitId`/`resolveSplitPattern` mapean blando (`531_bbb→531_bbb`, `texas_method` etc.) y si no encuentra cae a `ul_x4` patrón sin error visible (`ProgramProtocolEngine.kt:261-263`). 13 protocolos con `defaultSplit` no tienen test que falle si se introduce ID inexistente.

**Dirección:** `ProtocolLibraryTest.allDefaultSplitsExist` + `scripts/protocol_split_gate.py`.

### S-02 — 3 ej fijos por sesión (P1)
**Evidencia:** `buildSessions()` resuelve `trainingDays` desde `splitPattern` y crea `parts = sessionCategories.mapIndexed { partIdx → exercises=[prescribedExercise(liftForPart(partIdx))] }` — exactamente 1 por parte (`ProgramProtocolEngine.kt:155-172`). `liftForPart` usa mainLift para 0-1 y accesorio para ≥2; `prescribedExercise` siempre 1 `Exercise` (216-223).

**Impacto:** GZCL/Juggernaut/PHAT entregan esqueleto correcto pero sin densidad real por día; usuario completa volumen a mano.

### S-04 — `prefillIfEmpty` (P2)
`hasSessionContent` true aunque solo 1 semana tenga contenido → semanas vacías dispersas quedan vacías tras plantilla 16-20 sem.

**Dirección:** `prefillEmptyWeeks(program, split)` que solo rellene `weeks.filter { sessions.isEmpty() }`.

## Cobertura tests y gaps
Existentes: `SplitApplicationEngineTest`, `ProgramProtocolEngineTest`, `SplitTemplatesTest`, `ProtocolLibraryTest`. Gaps: multi-bloque snapshot, `PREBUILT` sin plantilla para `dayLabel` (debe caer a `blankSession+warning`), 5/3/1 reps/semana.

## Preguntas abiertas
1. ¿Número de accesorios por día (torso 2, pierna 3, full 2+1) como regla configurable?
2. ¿`PROTOCOL_LIBRARY` pasa a asset JSON versionado?
