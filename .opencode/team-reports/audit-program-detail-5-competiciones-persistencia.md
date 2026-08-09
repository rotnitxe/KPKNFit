# Auditoría 5/5 — Competiciones y persistencia

> **Pista:** `CompetitionScreen.kt` 49KB, `CompetitionModels.kt`, `CompetitionRepository.kt`, `CompetitionReminderManager.kt`, `ProgramRepository.kt` 43KB, `KpknDatabase.kt` v20 (19 migraciones), `WorkoutLog` `calendarBreakId`

## Resumen ejecutivo

**Competiciones tienen infra real, no cosmética.** `CompetitionRecord` ↔ `Session` se sincroniza vía `CompetitionSessionSync.merge` que **nunca pisa `attempts`/`bestValidWeightKg`** de bloques existentes y preserva bloques sin ejercicio correspondiente (SBD auto-generados). `CompetitionReminderManager` agenda **`AlarmManager.setExactAndAllowWhileIdle`** real para `week/48h/start/post_result` con `NotificationChannel` + deep links `competition/$id`. `CompetitionRepository` es SSoT con `upsert→schedule` y `delete→cancel`.

**Persistencia JSON en Room es intencional y razonada:** `ProgramEntity.data TEXT` con todos los campos con `default`, 19 migraciones encadenadas (`MIGRATION_1_2…19_20`). `ProgramRepository` write-through cache con `StateFlow` + `withTransaction` en `finalizeWorkout`. Riesgo: evolución sin default rompe JSON viejo y documento grande re-serializado en cada micro-edición.

## Tabla de hallazgos

| ID | Sev | Título | Archivo:línea |
|---|---|---|---|
| C-01 | P2 | `CompetitionReminderManager` re-agendado tras `BOOT_COMPLETED` no verificado en esta pasada | `CompetitionReminderManager.kt:25-108` `AndroidManifest.xml` (no leído) |
| C-02 | P2 | `POST_NOTIFICATIONS` runtime (Tiramisu) + `canScheduleExactAlarms()` (S) — fallback `set()` si no concedido, pero sin UX de guía | `CompetitionReminderManager.kt:68-73` `PermissionGuideHelper.kt` |
| C-03 | P2 | `CompetitionSessionSync` preserva `attempts` pero no resuelve conflicto si sesión cambia `movementType` y record ya tiene intentos de otro tipo | `CompetitionSessionSync.kt:49-70` |
| C-04 | P2 | `ProgramRepository` cada `updateProgram` hace `_programs.update + db.upsert` serializando todo `Program`; `finalizeWorkout` sí es transaccional | `ProgramRepository.kt:58-76,286-320` |
| C-05 | P2 | `Program` 56 campos con defaults: añadir campo sin default rompería deserialización de JSON viejo (Room TEXT) | `Program.kt:12-56` `KpknDatabase.kt:77-543` |
| C-06 | P2 | `ActiveProgramEntity` huérfano si `deleteProgram(programId)` mientras es activo — `clearActiveProgram()` sí se invoca, verificado | `ProgramRepository.kt:165-181` |
| C-07 | MEJORA | `WorkoutLog.calendarBreakId` desacopla logs de break del run cíclico — falta test `breakLog not counted in cycle completion` | `WorkoutLog.kt:47` `ProgramProgressEngine.kt:382-449` |

## Hallazgos detallados

### C-01 — Boot receiver (P2)
**Evidencia:** `CompetitionReminderManager.schedule/cancel/scheduleIfEnabled` existen y son invocados por `CompetitionRepository.upsert/delete`. No se leyó `AndroidManifest` en esta pasada para confirmar `RECEIVER BOOT_COMPLETED` que re-agenda tras reinicio.

**Dirección:** verificar `AndroidManifest.xml` registra `CompetitionReminderBootReceiver` con `android.permission.RECEIVE_BOOT_COMPLETED` y test `ShadowAlarmManager`.

### C-04 — Write amplification (P2)
`updateProgram(normalized)` hace `normalizedIdentityFields()` + `_programs.update { map }` + `scope.launch { db.programDao().upsert(normalized.toEntity()) }` donde `toEntity()` serializa todo el `Program` a JSON. Correcto y durable, pero O(n) en tamaño del programa por cada `updateWeekMetadata` etc.

**Dirección:** medir con `ProgramJsonSizeTest` (20 sem×6 ses×8 ej). Si >300KB, patch por `weekId` o normalización.

### C-05 — Evolución JSON (P2)
Todos los campos nuevos en `Program.kt` hoy tienen default (`= emptyList()`, `= null`, etc.) — bien. Pero es disciplina, no gate. Un campo sin default rompería `kotlinx.serialization` al leer JSON viejo de `ProgramEntity.data`.

**Dirección:** test que deserializa JSON v19 sin el campo nuevo; lint “todo campo nuevo en `Program` debe tener default”.

## Validación real verificada

- `CompetitionReminderManager` crea `NotificationChannel` (importance DEFAULT, vibration), calcula `triggerMs = eventAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()` y agenda `TYPE_WEEK (-7d)`, `TYPE_48H (-48h)`, `TYPE_START (eventAt)`, `TYPE_POST_RESULT (+5h)` solo si `startTime` existe.
- `CompetitionSessionSync.merge` es SSoT record: `existingBlock?.attempts.orEmpty()` preservado, `sessionBlockIds` + `untouchedBlocks` merge sin pérdida SBD.

## Cobertura tests y gaps

Existentes: `CompetitionSessionSyncTest`, `CompetitionRepositoryTest` (parcial). Gaps: `CompetitionReminderManagerTest` con `ShadowAlarmManager`, `ProgramJsonSizeTest`, `WorkoutLog.calendarBreakId` integration, `BOOT_COMPLETED` robolectric.

## Preguntas abiertas

1. ¿Conflicto `movementType` cambiado en sesión con intentos existentes — migrar intentos o bloquear cambio?
2. ¿Presupuesto JSON `Program` antes de normalizar (300KB/500KB)?
