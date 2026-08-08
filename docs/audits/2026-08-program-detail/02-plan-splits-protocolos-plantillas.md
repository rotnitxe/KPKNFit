# Plan 02 — Splits, protocolos y plantillas: de esqueleto a plan completo

> **Origen:** `00-auditoria-program-detail.md` (PD-04, PD-05, PD-08, PD-13) · **Esfuerzo:** L (5-8 dias) · **Riesgo:** protocolos con 3 ej/sesion sin variacion por dia, IDs huerfanos silenciosos, semanas vacias.

## Objetivo

Convertir protocolos en generadores de **planes completos** (sesiones diferenciadas por dia del split, rampa semanal real, variantes tecnicas, deload) y dejar splits+plantillas con infra verificada.

## Tareas

### T1 — Verificacion `defaultSplit` (P1)
**Archivos:** `ProgramProtocolEngine.kt:248-263`, `ProtocolLibrary.kt:14-15`, `SplitTemplates.kt` (52 templates), `SessionPrefillBridge.kt:17-38`
- Test `ProtocolLibraryTest.allDefaultSplitsExist` que falle si `defaultSplit` no existe en `SPLIT_TEMPLATES` tras `resolveSplitId`.
- Eliminar fallback silencioso a `ul_x4`; si no existe, error visible (exception o snackbar), no generico.
- Gate `scripts/protocol_split_gate.py` analogo a `catalog_v2_gate.py`.

### T2 — Prefill por semanas vacias (P2)
**Archivos:** `SessionPrefillBridge.kt:45-59`, `ProgramTemplateEngine.kt:58-65`, `SplitApplicationEngine.kt:148-226`
- Nuevo `prefillEmptyWeeks(program, split)` que solo rellene `weeks.filter { sessions.isEmpty() }`.
- `ProgramTemplateEngine.applyTemplate` usa `prefillEmptyWeeks` en vez de `prefillIfEmpty` (programa completo).
- Test: 16 sem con 2 vacias -> solo esas 2 se rellenan.

### T3 — Protocolo diferenciado por dia (MEJORA/P1)
**Archivos:** `ProgramProtocolEngine.kt:81-175`, `ProtocolExerciseLibrary.kt:45-135`, `PeriodizationEngine.kt:22-77`
- `ProtocolSessionRecipe(dayLabel -> focus -> parts)` : torso 2 accesorios, pierna 3, full-body 2+1, etc.
- Rampa 5/3/1 real: 531 ya tiene `65-85/70-90/75-95/40-60` pero `prescriptionFor` debe variar reps por semana (5/3/1), no solo por `goal`.
- Flag `enhancedDayDifferentiation=true` opt-in, firma `applyProtocol` intacta.
- Snapshot test `gzcl_ul_x4_hasDifferentiatedDays` : torso != pierna en accesorios.

### T4 — Validacion contra catalogo v2 (infra real)
**Archivos:** `SessionTemplates.kt:3287 lineas`, `domain/templates/*`, asset `exercise_catalog_v2.json` (WIP en working tree), `SessionTemplateCatalogTest.kt`
- `SessionTemplateCatalogTest` aserta cada `exerciseDbId` de `SESSION_TEMPLATES_SYSTEM` y `ProtocolExerciseLibrary` existe como `configurationId` en el asset WIP.
- `SplitApplicationEngine(PREBUILT)` sin plantilla para un `dayLabel` -> `blankSession` + warning, no crash.

## Validacion
```powershell
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.ProtocolLibraryTest' --tests '*.ProgramProtocolEngineTest' --tests '*.SplitApplicationEngineTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.SessionTemplateCatalogTest'"
python scripts/catalog_v2_gate.py
```

## Nota WIP
Working tree modifica `android-native/app/src/main/assets/exercise_catalog_v2.json` + loaders `domain/exercises/catalogv2/*`. Verdad-terreno = ese asset (grep `configurationId`).
