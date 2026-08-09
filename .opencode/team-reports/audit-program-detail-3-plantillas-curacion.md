# Auditoría 3/5 — Plantillas pre-configuradas y curación contra catálogo v2

> **Pista:** `SessionTemplates.kt` 3.287 líneas + `SessionTemplate*` (Audit/QualityRules/CatalogPolicy/SuggestionEngine/Facets) · **Metodología:** lectura directa + grep `configurationId` en asset WIP

## Resumen ejecutivo

**Las plantillas son infraestructura real, no promesa.** `SESSION_TEMPLATES_SYSTEM` es un catálogo grande con **políticas de curación operativas** (`SessionTemplateQualityRules` P0/P1, `SessionTemplateAudit`, `SessionTemplateCatalogPolicy.WEEKLY_VOLUME_RANGES`, `SessionTemplateSuggestionEngine` con scoring por dificultad/volumen/drain). La validación contra catálogo v2 existe como **gate Python** (`catalog_v2_gate.py`) + `SessionTemplateCatalogTest` / `ExerciseDisplayNameTest` — no es cosmética.

**WIP importante:** `android-native/app/src/main/assets/exercise_catalog_v2.json` está modificado en working tree (no commit). Verdad-terreno para `exerciseDbId`/`catalogConfigurationId` es ese asset, no `catalog/exercises/v2/source/` antiguo.

## Tabla de hallazgos

| ID | Sev | Título | Archivo:línea |
|---|---|---|---|
| C-01 | P1 | Split `dayLabel` sin plantillas sugeridas cae a `blankSession` sin warning visible al usuario | `SplitApplicationEngine.kt:312-327` `SessionTemplateCatalogPolicy.templatesForSplitDay` |
| C-02 | P2 | Sesión lower completeness: exige pantorrillas/aductores/glúteo+hinge — puede marcar P1 espurio en plantillas especializadas | `SessionTemplateQualityRules.kt:37-43,454-475` |
| C-03 | P2 | 3.287 líneas en un solo `SessionTemplates.kt`: coste de compilación + preload `MainActivity` step 9 vía `CatalogV2ProcessCache` no medido tras growth | `SessionTemplates.kt` `ExerciseDisplayNameTest` |
| C-04 | MEJORA | `SessionTemplateSuggestionEngine` no expone “por qué esta plantilla” (explainability del scoring) | `SessionTemplateSuggestionEngine.kt:201-244` |
| C-05 | MEJORA | Gate `catalog_v2_gate.py` no cubre `ProtocolExerciseLibrary` 1:1 con `SESSION_TEMPLATES_SYSTEM` | `scripts/catalog_v2_gate.py` |

## Hallazgos detallados

### C-01 — `dayLabel` sin plantillas (P1)
Si `templatesForSplitDay(splitId, dayLabel)` no devuelve nada, `buildSessions` crea `blankSession(day)` con nombre genérico. No hay snackbar/log que avise “este día del split no tiene plantillas curadas”. El patrón existe para splits raros (p.ej. `deathbench_spec` con días “Banca Volumen”).

**Dirección:** warning en `SuggestedWeekPlan.warnings` ya existe — propagarlo a `SplitView`/`ProgramDetailViewModel` como banner.

### C-02 — Lower completeness estricta (P2)
`SessionTemplateQualityRules.checkLowerCompleteness` exige pantorrillas + aductores + (glúteo primario o hinge con aporte). Marca P1/P0 en plantillas “isquios” puras o “cuádriceps” puras que son intencionalmente especializadas.

**Dirección:** exceptuar `focusCategory==ISQUIOS/CUADRICEPS` de la exigencia de pantorrillas, o marcar como `HYPERFOCUS` ya contemplado (`SessionTemplateFocusCategory`).

## Estadísticas verificadas (muestreo directo)

- `SessionTemplates.kt`: **3.287 líneas**, `TEMPLATE_PERFORMANCE_PROFILE_BY_CONFIGURATION` con 60+ entries, `TEMPLATE_CATALOG_REVISION = v2-approved-2026-08-02-c`.
- `SessionTemplateQualityRules`: ~500 líneas, 10 checks (P0: `DIRECT_VOLUME_CAP`, `HARD_BW`, `LOWER_MISSING_GLUTE`; P1: `ADV_TOO_MANY`, `FOCUS_UNDECLARED`, etc.).
- `SessionTemplateSuggestionEngine`: scoring con `preferredDifficulty`, `forcedTemplateByDayIndex`, `preferredFocusMuscleByDayIndex` + drain/volumen/p0Count cache.

## Metodología verificación IDs

No se leyó el asset gigante entero; se usó `Select-String` por `configurationId` en `exercise_catalog_v2.json` WIP + `ProtocolExerciseLibrary` (28 `ProtocolLift`) — todos mapean a `high_bar_back_squat__barbell` etc. con `PERFORMANCE_PROFILE_BY_CONFIGURATION` whitelist que falla en construcción si falta.

## Cobertura tests y gaps

Existentes: `SessionTemplateCatalogTest`, `ExerciseDisplayNameTest`, `SessionTemplateFacetsTest`, `ProtocolExerciseLibraryTest`. Gaps: test que aserte 0 IDs huérfanos entre `SESSION_TEMPLATES_SYSTEM` ∪ `ProtocolExerciseLibrary` vs asset WIP (hoy lo cubre `catalog_v2_gate.py` pero no como unit test).

## Preguntas abiertas

1. ¿Umbral `ABSURD_TOTAL_SETS_MAX=40` / `ABSURD_PRIMARY_MUSCLE_SETS_MAX=20` sigue vigente con plantillas 3.287 líneas?
2. ¿Asset `PROTOCOL_LIBRARY` debe salir de código a JSON versionado?
