# Auditoría profunda — Sistema de programas (Program Detail)

> **Fecha:** 2026-08-08 · **Auditor:** coordinador (modo directo, infra `/team` bloqueada por límite Clinepass 5h) · **Alcance:** gestión de programas simples/avanzados, splits, macrociclo/bloques, semanas calendarizadas, sesiones de competición, eventos cíclicos/loops, fechas clave, calendarización, protocolos y curación de plantillas pre-configuradas · **Base:** `android-native/app/src/main/java/com/example/kpkn/` + tests

## Resumen ejecutivo

El sistema es **funcionalmente rico pero acoplado**: `Program` (56 campos) vive como documento JSON gigante en `ProgramEntity.data`. Encima conviven ~8 motores temporales (`LoopEngine`, `ProgramCalendarEngine`, `ProgramProgressEngine`, `ProgramKeyDateEngine`, `ProgramMigrationEngine`, `ProgramActiveStateEngine`, `ProgramHierarchyIndex`, `HomeSessionResolver`) que deben mantenerse coherentes.

Hallazgos más relevantes:

1. **Temporal flotante vs fechado funciona**, con `pausedCyclicSnapshot` como freezer correcto; el riesgo principal es el **estado de loops triplicado** (`loops[]` + `loopState` + `loopOccurrences[]`) cuya fuente canónica (`LoopEngine.syncOccurrences`) no se invoca en todos los caminos que mutan loops.
2. **Fechas clave y calendarización tienen infraestructura real** (`ProgramKeyDateEngine.applyAdvancedCalendarSave`, `ProgramCalendarEngine.project`, `CompetitionReminderManager` con `AlarmManager`), pero la **creación de sesión de competición desde semana “con competición” sigue prometida** (`macro_editor_competition_week_note`) y huérfana.
3. **Splits y protocolos sí generan programas ejecutables**, pero los **protocolos hoy entregan 3 ejercicios/sesión** (1 por parte) sin variación por día del split ni autorregulación semanal real (5/3/1 como 4 bloques de 1 semana es aproximación). El puente `SessionPrefillBridge` + `SplitApplicationEngine(PREBUILT)` + `SessionTemplateSuggestionEngine` existe pero es frágil si faltan plantillas por patrón.
4. **Plantillas de sesión pre-configuradas son grandes** (`SessionTemplates.kt` 3.287 líneas, `SESSION_TEMPLATES_SYSTEM` + políticas `SessionTemplateCatalogPolicy`/`Audit`/`QualityRules`); la validación contra catálogo v2 existe (gate Python + tests) — no es cosmética.
5. **UI del detalle es god-file** (`MacrocycleEditor.kt` 131 KB) y concentra roadmap, key-dates, plantillas/protocolos, calendario y mutaciones. No hay `ProtocolsView.kt` pese a comentario “Usado por ProtocolsView y MacrocycleEditor” — está integrado como sheet.
6. **Persistencia JSON en Room es intencional y razonada**, con defaults en todos los campos nuevos y `MIGRATION_1_2…19_20`. Riesgos: evolución sin default y documento grande (re-serializar todo en cada micro-edición).

Estado: **no hay funcionalidades vacías vendidas como reales** salvo competición-desde-semana; el riesgo es **consistencia entre motores, cobertura de IDs split/protocolo y deuda UI monolítica**.

## Tabla de hallazgos

| ID | Sev. | Título | Evidencia principal |
|---|---|---|---|
| PD-01 | P1 | Estado loops triplicado: `loops` + `loopState` + `loopOccurrences`, canónico solo si `syncOccurrences` se invoca | `Program.kt:25-26,55` · `ProgramScheduleModels.kt:49-63` · `LoopEngine.kt:48-346` |
| PD-02 | P1 | Sesión competición desde semana “con competición” prometida pero no implementada | `strings.xml:431` · `MacrocycleEditor.kt:110-119` · `CompetitionConfigSheet.kt` solo desde editor sesión |
| PD-03 | P1 | `ProtocolsView` referenciado en comentario no existe; protocolos solo vía sheet del editor | `ProgramProtocolEngine.kt:25-27` · grep `ProtocolsView` sin resultados en `screens/` |
| PD-04 | P1 | Protocolos generan 3 ejercicios/sesión fijos (1 por parte); sin diferenciación por día del split | `ProgramProtocolEngine.kt:124-175,195-238` · `ProtocolExerciseLibrary.kt:45-135` |
| PD-05 | P1 | `defaultSplit` de protocolos sin verificación centralizada contra `SPLIT_TEMPLATES`; fallback silencioso a `ul_x4` | `ProgramProtocolEngine.kt:248-263` · `ProtocolLibrary.kt` 13 protocolos |
| PD-06 | P1 | Calendarizado simple: loops `excluded`/`postponed` no modelados en `CalendarBreak` | `Program.kt:43,63,326-332` · `ProgramKeyDateEngine.kt` |
| PD-07 | P2 | Resolución “semana actual” duplicada en 4 sitios (Progress/Calendar/ActiveState/Home) | `ProgramProgressEngine.kt:39-84` · `ProgramCalendarEngine.kt` · `ProgramActiveStateEngine.kt` · `HomeSessionResolver.kt` |
| PD-08 | P2 | `SessionPrefillBridge.prefillIfEmpty` no-op si hay cualquier sesión; deja semanas vacías parciales | `SessionPrefillBridge.kt:45-59` · `ProgramTemplateEngine.kt:58-65` |
| PD-09 | P2 | Campos huérfanos: `blockLabel`, `exerciseGoals`, `tags`, `author/isPublic`, `ProgramEvent.events` legacy | `Program.kt:19,23-27` · grep sin consumidores en `programdetail` |
| PD-10 | P2 | Documento `Program` gigante en columna JSON: cada micro-edición re-serializa `upsert` completo | `ProgramRepository.kt:58-76,286-320` · `KpknDatabase.kt:10-14` |
| PD-11 | P2 | God-file `MacrocycleEditor.kt` (131 KB) concentra roadmap, key-dates, plantillas/protocolos, calendario | `MacrocycleEditor.kt:1-160+` · 131086 bytes |
| PD-12 | P2 | Competiciones: `CompetitionReminderManager` sí agenda `AlarmManager` real; re-agendado tras reboot no verificado | `CompetitionReminderManager.kt:25-108` · `CompetitionRepository.kt:57-83` |
| PD-13 | MEJORA | Protocolos → planes completos (diferenciación por día, rampa 5/3/1 real, deload loops) | `ProgramProtocolEngine.kt:81-175` · `PeriodizationEngine.kt:22-77` |
| PD-14 | MEJORA | Desdoblar `MacrocycleEditor` en módulos (Roadmap, KeyDates, Calendar, Library) | `MacrocycleEditor.kt` + `BlockRoadmap.kt:32KB` etc. |
| PD-15 | MEJORA | Unificar `schedulePlan` como SSoT y auditar `LocalDate.parse` sin try/catch | `ProgramScheduleModels.kt:12-20` · `Program.kt:684-694` |
