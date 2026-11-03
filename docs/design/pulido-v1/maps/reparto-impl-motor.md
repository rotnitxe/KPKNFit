# Reparto · motor y datos implementados (rama `pulido/rp`, worktree `C:\kw\pu-rp`)

Contrato: `maps/reparto-motor.md`. Todo en `android-native/app/src/main/java/com/example/kpkn/` (dominio y datos; Room intacto, v31).
Pruebas por el carril JVM: 188 pruebas del paquete `domain/training/split` + `SplitApplicationEngineTest`, todas en verde (41 del planificador, 8 del catálogo, 1 barrido de 210 planes).

## API para la interfaz (`domain/training/split/`)
```kotlin
// Planificar, arrastrar, aplicar, deshacer (SplitMigrationPlanner)
fun plan(request: MigrationRequest): MigrationPlan                    // Ok | Refused(reason, canUseCleanOrPrebuilt, diagnostic)
fun rearrange(request, plan: MigrationPlan.Ok, fromWeekday: Int, toWeekday: Int): Rearranged   // .request (guardar) + .plan
fun apply(request): MigrationOutcome  |  fun apply(plan: MigrationPlan.Ok): Program            // = plan.program
fun proposeWeekdays(program, weekId, split, startDay = 1): List<Int>  // R2
fun undoState(program): UndoState  // NONE | AVAILABLE | EDITED
fun undo(program, force = false): Program
// Petición: program, split, weekdays, resolver, baseWeekId, scopeWeekIds, scope, blockId, startDay, completedSessionIds,
//           todayWeekday, currentWeekId, placements (unitKey -> índice de día), targetMinutes, fitToTime, allowAuthored, nowMs
// MigrationPlan.Ok: weeks: List<WeekPlan>, base, program, warnings, decisions, notes, weekdays
// WeekPlan: mode (BASE|PARALLEL|INDEPENDENT|SKIPPED), days: List<DayPlan> (kind OPEN|LOCKED|EXTRA, mark, minutes, focus),
//           moves (STAYS|MOVED|LOCKED), volume/frequency: grupo -> (antes, después), trims, merges, units, isDeload
// Catálogo (SplitCatalog): entries(), entry(id), view(filter, haveDays, now) -> CatalogView(now, groups), now(program, weekId, startDay),
//           timesPerMuscle(split), frequencyByGroup(split), SplitDayMarks.of(label)/strip(pattern, startDay)/WEEKDAY_LETTERS
// Avisos: PlanWarning.text(); PlanWarnings.sentence(w).   Grupos: SplitMuscleGroups.ALL / MAIN
```
`completedSessionIds` = `ProgramRepository.executedTrainingEvidence(program).sessionIds` (menos la sesión en curso), lo mismo que `ProgramDetailViewModel.completedSessionIds`.
`Program.lastSplitChange: SplitChangeSnapshot?` viaja en el JSON del programa (campo con valor por defecto; programas viejos se leen igual).

## Reglas hechas
R0 (todas las frases; más «lugares distintos» y «tantas sesiones hechas como días») · R1 · R2 · R3–R4 (`DayAssigner`) · R5 estabilidad (+1,2 por unidad fuera de su día de origen) · R6 (volumen intacto verificado en 210 planes) · R7 (paralelas repiten día; distintas prefieren el día del mismo ejercicio, +3) · R8 descarga (sin recorte) · R9 · R10 · R11 · R12 · R13 · R14 · R15.

## Decisiones (donde el contrato no dice o se decidió)
1. **Solo las sesiones HECHAS ocupan un día del reparto.** Las «fijadas por naturaleza» (opcional, competición, otro deporte, solo core/movilidad/cardio) se quedan intactas como `EXTRA`, no ocupan día y cuentan en el volumen (§8 del contrato: fijadas).
2. Los días nuevos usan `weekdays[j]` si caen libres y ≥ hoy; si no, los primeros días libres en orden. Un día hecho conserva su propio día.
3. **Arrastrar = `rearrange`**: cambia `weekdays` y clava con `placements` quién lleva qué; se recalcula el plan completo (barato, 50 ms para 12 semanas).
4. `MigrationRequest` añade `scope`, `blockId`, `startDay`, `currentWeekId` (el contrato no decía cómo escribir selecciones ni a qué semana aplicar «hoy»).
5. Con `fitToTime = false` el plan trae igual la decisión `FitTime` (con las series que se quitarían); el recorte solo se aplica si se activa.
6. Frecuencia/volumen por grupo (Pecho, Espalda…, **Lumbar** aparte, como el lienzo), no por `focusLabel` del código (que une lumbar con espalda).
7. `ADJACENT_HEAVY`: las dos sesiones seguidas deben cargar ≥ 3 series ponderadas del mismo grupo grande (como el lienzo), máx. 2 avisos, sin enlazar domingo con lunes.
8. Deshacer guarda el cambio anterior un nivel (se descarta el anterior a ese): deshacer es exacto dos cambios seguidos; el tercero pierde el más viejo (evita crecer el JSON sin fin). Se invalida (`EDITED`) por huella del contenido de las semanas cambiadas.
9. MIGRATE en `SplitApplicationEngine.apply` usa el planificador si la petición trae `traitResolver` (y rechaza → camino de siempre); no escala al MAV (R6). `buildSessionsForSplit` acepta `traitResolver` para una semana suelta.
10. Descripciones (35) literales en `data/splits/SplitCardDescriptions.kt`; `Difficulty` como nivel; nombres llanos ya existían (la prueba exige los 35 del lienzo).

## Abierto / riesgos
- No se probó en Android: `SplitRedistributorRealProgramsTest`/`ReportTest` (fuera del carril JVM) ni compilación del módulo completo; mis cambios solo añaden parámetros con valor por defecto.
- 19 casos del barrido tienen dos pesados del mismo patrón en un día de un reparto con un solo día de pierna/empuje (no hay otro hueco con encaje); no se asegura.
- Con superseries, el mínimo de 3 ejercicios por día puede no cumplirse (un par se mueve entero).
- Las semanas vacías dentro de un alcance se saltan (antes se creaban sesiones vacías).
