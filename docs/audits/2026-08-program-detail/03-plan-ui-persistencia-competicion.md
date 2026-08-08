# Plan 03 — UI Program Detail, persistencia y competiciones

> **Origen:** `00-auditoria-program-detail.md` (PD-02, PD-03, PD-10, PD-11, PD-12, PD-14) · **Esfuerzo:** M-L (4-7 dias en 2 fases) · **Riesgo:** god-file 131KB, documento JSON gigante, competiciones sin flujo end-to-end.

## Objetivo

Desacoplar la UI del detalle, blindar la persistencia JSON evolutiva y cerrar el flujo macrociclo -> sesion competicion -> `CompetitionRecord` -> recordatorio.

## Tareas

### T1 — Descomponer `MacrocycleEditor` (P2/MEJORA) — Fase A
**Archivo:** `screens/programdetail/components/MacrocycleEditor.kt:131086 bytes` (god-file)
- Extraer: `MacrocycleRoadmapSection`, `KeyDatesSection`, `CalendarSection`, `LibrarySection` (plantillas+protocolos), + dialogs `Block/Meso/Week/KeyDate` a `screens/programdetail/components/editor/`.
- Hoist estado al `ProgramDetailViewModel` (ya expone `roadmapBlocks`, `simpleRoadmapLoopMarkers`, `weeklyAdherence`, etc. en `ProgramDetailViewModel.kt:149-396`).
- Mantener `onCreateSessionForWeek / onCompetitionKeyDateSaved / onFocusWeek` como callbacks; no cambiar `Navigation.kt`.
- Criterio: `MacrocycleEditor.kt` < 400 lineas tras extraccion; cada seccion < 350.

### T2 — Sesion competicion desde semana (P1) — Fase A
**Archivos:** `strings.xml:431` (nota pendiente), `MacrocycleEditor.kt:110-119`, `DayView.kt:127-`, `CompetitionConfigSheet.kt`, `CompetitionSessionSync.kt:23-93`, `CompetitionRepository.kt:57-89`, `Session.kt:22-43` (`isCompetitionMeet`, `competition*`)
- `DayView`/`MacrocycleEditor`: si `WeekWithMeta.keyDateType==COMPETITION` mostrar CTA "Crear sesion de competicion" (en vez de solo nota).
- Crear `Session(isMeetDay=true, competitionKeyDateId, competitionDetails)` + `CompetitionRecord` via `CompetitionSessionSync.merge(programId, weekId)` + `CompetitionRepository.upsert` + `CompetitionReminderManager.schedule`.
- Navegar al editor de sesion; no duplicar logica de `CompetitionScreen.kt:49KB`.

### T3 — `ProtocolsView` dedicada o documentar decision (P1) — Fase A
**Archivos:** `ProgramProtocolEngine.kt:25-27` ("Usado por ProtocolsView y MacrocycleEditor"), `MacrocycleEditor.kt:91-94` `PROTOCOL_LIBRARY`
- Opcion A: crear `screens/programdetail/components/ProtocolsView.kt` (browse + preview bloques + apply + `SplitApplicationEngine` preview).
- Opcion B: corregir comentario y documentar en `docs/ANDROID_UI_SCREENS_MAP.md` que protocolos viven en sheet del editor.
- Cualquiera de las dos cierra PD-03; elegir y no dejar fantasma.

### T4 — Persistencia JSON: medir y blindar (P2) — Fase B
**Archivos:** `data/repository/ProgramRepository.kt:58-76,286-320` (serializa `Program` a `ProgramEntity.data TEXT`), `data/db/KpknDatabase.kt:10-14,77-543` (v20, 19 migraciones), `data/models/Program.kt` (56 campos, todos con default)
- Test `ProgramJsonSizeTest`: serializa programa 20 sem x 6 ses x 8 ej y aserta tamano; si >300KB log warning (no fail) para decidir normalizacion futura.
- Blindaje evolutivo: regla "todo campo nuevo en `Program` debe tener default" (lint o test que deserializa JSON v19 sin el campo).
- Verificar `CompetitionReminderManager` re-agenda tras reboot: `AndroidManifest` registra `BOOT_COMPLETED` receiver y test `ShadowAlarmManager` para week/48h/start/post_result.

### T5 — `AndroidManifest` + permisos recordatorio (P2) — Fase B
**Archivos:** `services/competition/CompetitionReminderManager.kt:25-74` (canal + `setExactAndAllowWhileIdle`, `POST_NOTIFICATIONS`), `CompetitionRepository.kt:68`
- Verificar `RECEIVER` boot + `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` segun targetSdk; anadir test Robolectric si falta.

## Validacion
```powershell
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.ProgramDetailViewModelTest' --tests '*.CompetitionSessionSyncTest' --tests '*.CompetitionReminderManagerTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.ProgramJsonSizeTest'"
```

## Metricas exito
- `MacrocycleEditor.kt` < 400 lineas, sin regresion visual.
- Flujo semana competicion -> sesion -> record -> notificacion end-to-end manual OK.
- 0 `program.copy(loops` fuera de `LoopEngine` (complementa Plan 01).
- Programa grande < 500KB JSON; si supera, decision normalizacion documentada.
