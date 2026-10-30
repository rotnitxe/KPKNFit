# Motor de migración de reparto · contrato para el implementador (punto 23)

Pedido del usuario: «un motor que permita que los ejercicios migren al nuevo sistema sin perjudicar la lógica y objetivos del usuario, así no tiene que crear series de nuevo». Este documento fija reglas, entradas/salidas, casos borde y pruebas. La simulación de `src/scenes-rp.js` (`rpRun`, `rpAssign`, `rpWarn`) sigue estas reglas en pequeño y sirve de referencia visual; **el motor real es `SplitRedistributor`**, que ya existe.

## 0. Punto de partida (leído del código)
| Pieza | Estado |
|---|---|
| `SplitApplicationEngine.apply` + `buildSessionsForSplit` (MIGRATE) | Es lo que usa Program Detail. Reasigna **sesiones enteras** a días (`bestTrainingDayForSession`), sin repartir ejercicios; con más sesiones que días las amontona (`unassignedDays.ifEmpty { trainingDays }`) y con menos crea sesiones vacías no principales. Sin informe ni vista previa. Reescribe todas las sesiones de las semanas destino, hechas o no. |
| `domain/training/split/SplitRedistributor.redistribute` | Reparte **ejercicios** (no sesiones) entre los días del reparto. Puro y determinista. Hoy solo lo llama el asistente (`SetupWeekLayouts.applyLayout`). Ya cumple: volumen intacto, superseries juntas, cardio/movilidad como bloques, calentamiento con su primer ejercicio, ≥ 3 ejercicios por día si hay de dónde sacar, minutos parejos, sin pesados del mismo patrón el mismo día ni en días seguidos, orden por nivel, aproximación y movilidad pegadas, recorte por tiempo (`DayTimeFit`), informe (`RedistributionReport`: `moves`, `volumeBefore/After`, `setTrims`, `exerciseMerges`, `unclassified`), propagación a semanas paralelas, protección de la receta (`manualSessionOverrides`). |
| Lo que **no** hace | Sesiones hechas/registradas; semana en curso y días pasados; decisiones del usuario sobre unidades concretas; estabilidad (preferir dejar cada ejercicio donde estaba); día preferido de un ejercicio entre semanas no paralelas; avisos de frecuencia por músculo; plan sin aplicar (preview) como API única; deshacer; descarga explícita; sesiones opcionales/fijadas. |

**Decisión de diseño**: no se escribe un motor nuevo. Se conecta MIGRATE a `SplitRedistributor`, se añade una fachada pura `SplitMigrationPlanner.plan()` (lo que pinta la vista previa y la revisión; `apply` consume el mismo plan: «lo que ves es lo que se aplica») y se amplía el motor con las reglas marcadas **NUEVA**.

## 1. Entradas y salidas
```kotlin
data class MigrationRequest(
  val program: Program,
  val scopeWeekIds: List<String>,           // semanas destino ya resueltas por alcance (Semana/Varias/Bloque/Programa)
  val baseWeekId: String,                   // la semana que se muestra en la vista previa
  val split: SplitTemplate,                 // catálogo o propio (nunca el «custom» vacío)
  val weekdays: List<Int>,                  // 1..7 ascendente; size == SplitCatalogRules.trainingDays(split)
  val completedSessionIds: Set<String>,     // hechas o con series registradas (del historial)
  val todayWeekday: Int?,                   // solo para la semana en curso
  val placements: Map<String, Int> = emptyMap(),   // unitKey → índice de día (decisiones del usuario)
  val targetMinutes: Int? = null, val fitToTime: Boolean = false,   // recorte por tiempo: solo si el usuario lo acepta
  val allowAuthored: Boolean = false,       // permiso explícito en planes de autor
  val resolver: ExerciseTraitResolver,
)
sealed interface MigrationPlan {
  data class Refused(val reason: String, val canUseCleanOrPrebuilt: Boolean) : MigrationPlan
  data class Ok(val weeks: List<WeekPlan>, val program: Program /* resultado de aplicar */,
                val warnings: List<PlanWarning>, val decisions: List<PlanDecision>) : MigrationPlan
}
data class WeekPlan(val weekId: String, val mode: PARALLEL|INDEPENDENT|SKIPPED(reason),
  val days: List<DayPlan>,                  // dayIndex, weekday, label, title, sessionId, locked, exerciseCount, minutes, focus
  val moves: List<ExerciseMove>,            // + kind: STAYS | MOVED | LOCKED
  val volume: Map<String, Pair<Double, Double>>, val frequency: Map<String, Pair<Int, Int>>,   // antes→después por músculo
  val trims: List<SetTrim>, val merges: List<ExerciseMerge>)
```
`PlanWarning(kind, severity, params)`: `DAYS_CHANGED(added, removed)`, `FREQ_DOWN(muscle, from, to)`, `FREQ_UP(muscles)`, `ADJACENT_HEAVY(a, b, muscle)`, `SHORT_DAY(title, n)`, `TIME_OVER(title, min, target)`, `UNCLASSIFIED(names)`, `MISFIT(names)`, `LOCKED_KEPT(sessionIds)`, `SKIPPED_WEEK(weekId, reason)`, `DELOAD_KEPT(weekId)`.
`PlanDecision(unitKey, kind: PLACE_UNIT|FIT_TIME, options, proposed)`: nunca bloquea; la propuesta ya está aplicada en el plan.
Muestra en la UI: frecuencia y volumen se **agrupan** por `SplitMuscles.focusLabel` (Pecho, Espalda, Hombros, Bíceps, Tríceps, Cuádriceps, Isquios, Glúteos, Pantorrillas, Lumbar, Core); el informe del motor sigue siendo por músculo canónico.

## 2. Reglas
**R0 · Puertas (existen; mismas frases).** En este orden devuelven `Refused`: días fuera de 1..7 o repetidos («Elige entre 1 y 7 días distintos de la semana.») · plan de autor sin `allowAuthored` («Este plan trae su reparto de autor.», en la UI: aviso del asistente) · reparto oculto («Este reparto no está disponible.») · `weekdays.size != días del reparto` («Este reparto tiene N días de entreno y elegiste M.») · semana inexistente · competición en la semana («La semana incluye una competición.») · sesiones con variantes B/C/D («La semana tiene sesiones con variantes (B, C o D).») · sin ejercicios («Tu programa no tiene ejercicios para repartir.») · menos unidades que días («Hay menos ejercicios que días de entreno: X ejercicios para N días.») · falta un levantamiento que el reparto pide («Tu programa no tiene press de banca y este reparto lo necesita.»). Si `canUseCleanOrPrebuilt`, la UI ofrece «Sesiones vacías»/«Con plantilla». Error inesperado: «No se pudo adaptar el programa a este reparto.» + `diagnostic` aparte.

**R1 · Unidades indivisibles (existe, `WeekGather`).** Un ejercicio viaja entero con sus series, repeticiones, RIR, descansos, carga, aproximación, movilidad y notas; una **superserie** completa; una parte de **cardio** entera; un bloque de **movilidad** de una parte; el calentamiento general con el primer ejercicio de fuerza de su sesión. `Exercise.id` no cambia: el historial por ejercicio se conserva. Nunca se cambian series, repeticiones, RIR, descanso ni orden interno de una superserie.

**R2 · Días.** Se toman las definiciones de entreno del reparto en orden (`SplitCatalogRules.trainingDefinitions`), con títulos «Torso A/B» (`titlesOf`) y `weekdays` ascendentes en ese orden. La UI propone `weekdays` así: los actuales si coincide el número; si no, los del propio reparto desde el día de inicio (`patternToTrainingDays`). Nunca se llama al motor con un número distinto. Arrastrar en la vista previa cambia `weekdays` (A día libre: se muda; a día ocupado: intercambio, `WeekAssignment.move`) **sin recalcular** quién lleva qué.

**R3 · Encaje (existe, `SplitAffinity`).** `fit = 0,8·encaje(músculos primarios) + 0,2·encaje(secundarios)` contra el mapa de pesos del día (`DayFocusParser`). Elegible = mejor − 0,12, o el suelo del día («Torso/Full Body»). Sin día natural (máx < 0,35: cardio, core, sin clasificar) = cualquier día, `neutral 0,5`.

**R4 · Objetivo (existe, `DayAssigner.cost`).** Se minimiza, por orden de peso: reglas duras 60/violación (dos pesados del mismo patrón el mismo día; > 2 compuestos de una familia o > 3 ejercicios de un músculo primario por día; día de cuerpo completo sin pierna, empuje y tirón) · mínimo 3 ejercicios por día si hay de dónde sacar (120 por ejercicio que falta) · minutos parejos (30 × varianza relativa) · encaje (12) · amontonar un músculo / repetir ejercicio / pesados en días seguidos (1,5 / 6 / 3). Voraz por nivel y segundos, y búsqueda local (mover o intercambiar, ≤ 200 pasadas; sin intercambios con > 60 unidades). Sin azar; desempates por índice.

**R5 · Estabilidad (NUEVA).** Coste +1,2 por unidad que no esté en su **día de origen**. El día de origen de cada sesión se calcula antes: se empareja cada sesión vieja con el día nuevo de mayor encaje (ponderado por segundos), uno a uno, y a igual encaje (±0,04) gana el día más cercano en la semana (distancia circular). Motivo: a igual foco, no mover. Peso deliberadamente bajo: el mínimo de 3 ejercicios y la mezcla empuje/tirón de un «Torso» pesan más.

**R6 · Volumen intacto (existe, a verificar siempre).** Por músculo, series semanales antes = después (primario 1, secundario 0,5, igual que el volumen de la app: `MuscleVolume.of`). Solo cambia con recortes por tiempo (R11), y entonces `maxVolumeDeviation == Σ recortes × peso` y todo recorte va en el plan.

**R7 · Progresión entre semanas (parcialmente nueva).** Con `scopeWeekIds > 1`: una semana **paralela** (mismas sesiones y mismas unidades por `MovableUnit.key`) repite el día de cada unidad de la semana base → el mismo ejercicio cae en el mismo día todas las semanas y la progresión (carga, RIR, series por semana) no se rompe. Una semana con otros ejercicios es **independiente**: se reparte aparte, pero **NUEVA** recibe un día preferido (+3) para las unidades cuyo `exerciseKeys` ya están en la base. Las semanas no se tocan en la receta: cada semana ya trae sus prescripciones; el motor solo coloca.

**R8 · Descarga (NUEVA).** Una semana `executionKind == DELOAD` o `isLoopWeek` conserva su prescripción reducida (mitad de series, RIR alto): se redistribuye por clave como las paralelas, **nunca** se le aplica recorte por tiempo y su volumen «antes» es el suyo (no se compara con la semana normal). Semanas `REST` o vacías: `SKIPPED("sin sesiones")`.

**R9 · Sesiones bloqueadas (NUEVA).** Bloqueada = `id ∈ completedSessionIds` (hecha o con series registradas) **o** fijada: `requirement == OPTIONAL`, `isMeetDay`, multideporte (`sportDocuments`), o sin ejercicios de fuerza (solo cardio/movilidad). Una bloqueada conserva id, nombre, día, contenido y sus `manualSessionOverrides`; no entra en las unidades y **sí** cuenta en volumen y frecuencia (antes y después). Ocupa el día del reparto con más encaje (voraz, a igual encaje el primero); los días restantes del reparto se colocan, en orden, en los `weekdays` libres ≥ `todayWeekday` (en semanas futuras, todos los libres). Si no hay tantos días libres: `Refused("Quedan N días de entreno y tu semana solo tiene M libres.", canUseCleanOrPrebuilt=false)`; la UI propone aplicarlo desde la semana siguiente. Una semana con todas sus sesiones bloqueadas o pasada es `SKIPPED` (los círculos de «Varias» salen apagados).

**R10 · Decisiones (NUEVA).** Unidades sin día natural (cardio, remates de solo core, sin clasificar, `misfits`) generan `PLACE_UNIT` con propuesta = día con menos segundos de entre los elegibles. `placements` las fuerza (`dayOfUnit[i] = día`), y el resto se recalcula con esa unidad ya puesta. Cambiarlas no cambia el volumen.

**R11 · Tiempo (existe, `DayTimeFit`; el interruptor es NUEVO).** Solo con `fitToTime`: un día que pasa 1,10 × `targetMinutes` pierde series de accesorios y aislamientos (último primero, mínimo 2 series) y, si aún no cabe, fusiona dos aislamientos del mismo músculo. No tocar nunca principales (`T1_MAIN`, `SPEED`, `T2_SUPPLEMENTAL`, `TECHNIQUE`), compuestos ni superseries. Si aun así se pasa, queda `TIME_OVER` y se dice. Por defecto apagado: el usuario lo activa como decisión explícita.

**R12 · Orden y metadatos (existe, `WeekBuilder`).** Dentro del día: nivel (principal, compuestos, aislamiento, remates), alternando empuje/tirón, aproximación y movilidad pegadas, `ApproachPlanner` otra vez. `isMainSession` por `mainDaysOf`; ids de sesión = los de las sesiones de origen en orden y, si hay más días, `"$weekId-d$n"`. `schedulePlan.trainingDays` y `weekStartDay` se reescriben; `trainingDayDates` se filtra a los días de entreno; con calendario, `materializeWeekDates`. En programas con receta, cada sesión nueva se marca «Reparto adaptado: …» (`manualSessionOverrides`) para que una reconstrucción posterior no deshaga el reparto. `selectedSplitId`/`weekSplitSelections`/`blockSplitSelections` se escriben como hoy (`SplitApplicationEngine.apply`).

**R13 · Avisos (NUEVA, `PlanWarnings.evaluate(plan)`, función pura y barata; se recalcula al arrastrar).** Frecuencia por músculo = nº de sesiones con ≥ 2 series ponderadas del grupo. `FREQ_DOWN` por cada grupo principal que baja; `FREQ_UP` agrupado; `ADJACENT_HEAVY`: dos sesiones en días consecutivos (lun-mar) que suman ≥ 3 series ponderadas del mismo grupo grande (pecho, espalda=dorsal+espalda alta, cuádriceps, isquios, glúteos); `SHORT_DAY` < 3 ejercicios de fuerza; `TIME_OVER` > 1,10 × `targetMinutes`; `DAYS_CHANGED` si el nº de días del reparto no es el de la semana.

**R14 · Determinismo y pureza.** Misma entrada, mismo plan, mismo `Program` (sin azar, sin reloj: `nowMs` fijo en pruebas). `plan()` no toca el programa; `apply()` = `plan().program`. Todo en `Dispatchers.Default`.

**R15 · Deshacer (NUEVA).** Antes de escribir, `apply` guarda en `Program.lastSplitChange` (nullable, JSON) una copia de las semanas afectadas, de las selecciones de reparto (`selectedSplitId`, `blockSplitSelections`, `weekSplitSelections`, `customSplit*`) y de `manualSessionOverrides`. «Deshacer» la restaura exacta y la borra. También se borra al editar cualquier sesión de esas semanas (propuesta; si se prefiere solo-hoja-abierta, basta una instantánea en el `ViewModel`). Sin migración de Room (Program es JSON).

## 3. Algoritmo (por semana destino)
1. Aplicar R0. 2. `weekdays` ← R2. 3. Separar sesiones bloqueadas (R9) y casarlas con días del reparto; los días abiertos toman los `weekdays` libres. 4. `WeekGather.gather` de las sesiones abiertas → unidades (R1). 5. Día de origen por sesión (R5) y, si no es la semana base, día preferido por clave (R7). 6. `placements` fijados (R10). 7. `DayAssigner.assign` (R4) → `dayOfUnit`; `fillEmptyDays`. 8. `WeekBuilder.build` → sesiones; `ApproachPlanner`; si `fitToTime`, `DayTimeFit` (R11). 9. Sellar duración (`SessionDurationEstimator`) si el origen la traía. 10. `RedistributionReport` (volumen, movimientos, recortes) + `PlanWarnings`. 11. Propagar (R7/R8) al resto de `scopeWeekIds`; las no usables → `SKIPPED` con motivo. 12. Armar `Program` (R12), instantánea (R15).
Tras arrastrar en la UI: solo `weekdays` y `WeekAssignment.applyAssignment`; se llama otra vez a `PlanWarnings`, no a `DayAssigner`.

## 4. Casos borde
| Caso | Comportamiento |
|---|---|
| Semana vacía | `Refused("Tu programa no tiene ejercicios para repartir.", true)`; la UI ofrece vacías / plantilla (por defecto plantilla). |
| Sesiones hechas | R9. Se ven con check y sin asa; su volumen cuenta. |
| Semana en curso (hoy = miércoles) | `todayWeekday = 3`: los días nuevos solo en mié–dom libres. |
| Descarga | R8. |
| Cardio / movilidad | Bloque indivisible; propuesta en el día con menos segundos; `cardioFirst` se conserva por origen. |
| Ejercicio sin músculo claro | `unclassified`; va al día más corto; aviso `UNCLASSIFIED`. |
| Menos ejercicios que 3 × días | Se aplica; `SHORT_DAY` o «Tu programa trae N ejercicios para M días: algunos días quedan cortos». |
| Reparto de powerlifting sin sentadilla/banca/peso muerto | R0, frase con el levantamiento. |
| Días con `foci` (SQUAT/BENCH/DEADLIFT) | El día «necesita» el levantamiento: `satisfies(focus, unit)`; si no lo lleva, nota ««X» no lleva sentadilla…». |
| Plan de autor / sourceRecipe | `allowAuthored` explícito y `manualSessionOverrides` (R12). |
| Varias lugares (`Session.placeId` distintos) | Como el asistente: se rechaza repartir («Cada sesión usa el material de su lugar: mueve las sesiones en lugar de cambiar el reparto.»). |
| Una semana trae otros ejercicios | `INDEPENDENT`, con día preferido (R7); nota «N semanas traen otros ejercicios». |
| Programa con calendario | `weekStartDay` lo fija el calendario; se rematerializan las fechas. |
| Alcance «Bloque»/«Programa» con semanas pasadas | `SKIPPED("ya hecha")`. |
| Mismo ejercicio en dos sesiones (sentadilla en cada día de cuerpo completo) | Se cuenta una vez por sesión; `same` evita repetirlo en un día (6). |

## 5. Ejemplo (el de la escena `rp-semana`)
Entrada: semana 5, «Personalizado» L M J V: `Pierna A` [sq, prensa, rdl, curlf, gem, rueda] · `Empuje` [bp, inc, ohp, lat+pushd (superserie), aper] · `Tirón` [dom, remo, jalon, face+martillo (superserie), bici] · `Pierna B` [pm, bulg, hip, ext, gems]. Reparto `ul_x4`, `weekdays=[1,2,4,5]`.
Salida (resumen; 22 ejercicios, 70 series, 14 movidos, volumen idéntico):
```json
{"status":"OK","weeks":[{"weekId":"w5","mode":"PARALLEL","days":[
 {"title":"Torso A","weekday":1,"min":66,"exercises":["bp<Empuje","dom<Tirón","ohp<Empuje","lat+pushd<Empuje","aper<Empuje"]},
 {"title":"Pierna A","weekday":2,"min":57,"exercises":["prensa","rdl","bulg<Pierna B","curlf","gem","rueda"]},
 {"title":"Torso B","weekday":4,"min":64,"exercises":["inc<Empuje","remo<Tirón","jalon<Tirón","face+martillo<Tirón","bici<Tirón"]},
 {"title":"Pierna B","weekday":5,"min":57,"exercises":["sq<Pierna A","pm","hip","ext","gems"]}],
 "volume":{"Pecho":[9,9],"Espalda":[18.5,18.5],"Cuádriceps":[14.5,14.5]},"frequency":{"Pecho":[1,2],"Bíceps":[1,2]},
 "trims":[],"merges":[]}],
 "warnings":[{"kind":"FREQ_UP","muscles":["Pecho","Espalda","Bíceps","Tríceps"]}],
 "decisions":[{"unitKey":"bici","kind":"PLACE_UNIT","proposed":2},{"unitKey":"rueda","kind":"PLACE_UNIT","proposed":1}]}
```
Con `completedSessionIds=["s1","s2"]` (lun y mar hechos, hoy = miércoles): salen `Pierna A` y `Empuje` idénticas (`locked:true`), `Torso B` (mié) con [dom, remo, jalon, face+martillo] y `Pierna B` (vie) con [pm, bulg, hip, ext, gems, bici]; el volumen de la semana no cambia. Con `fullbody_x3` y `targetMinutes=75, fitToTime=true`: «Cuerpo completo A» pasa de 86 a 82 min quitando 2 series de accesorios (`trims`) y la revisión dice «Se recortan 2 series para que quepan en 75 min».

## 6. Pruebas que exige (JUnit en `app/src/test/.../domain/training/split/`; reutilizan `SplitTestSupport` y los programas de `SplitRedistributorRealProgramsTest`)
- **Invariantes por catálogo** (`RedistributionSweepTest` ampliado): para cada reparto publicado × cada programa real compatible: (1) cada `Exercise.id` aparece exactamente una vez; (2) sets/reps/RIR/descanso/carga iguales; (3) superseries juntas; (4) `volumeBefore == volumeAfter` salvo `trims`; (5) determinismo (dos llamadas, mismo `Program`); (6) ningún día con < 3 ejercicios si `strength ≥ 3·días`; (7) ningún día con dos pesados del mismo patrón si hay otro hueco.
- **Bloqueo**: `completed = {s1,s2}` → `program.week.sessions` de s1,s2 `==` originales (`assertEquals`); el resto suma lo restante; `volumeAfter == volumeBefore`.
- **Paridad plan = aplicación**: `plan(req).program == apply(req)` y `apply` no toca semanas `SKIPPED`.
- **Días**: `weekdays.size != días` → `Refused` con la frase; `placeDays` desde patrón con `startDay`.
- **Deshacer**: `undo(apply(p)) == p` (incluye selecciones y overrides).
- **Tiempo**: con `targetMinutes=60, fitToTime=true`: ningún día > 66 min salvo `TIME_OVER`; ningún accesorio < 2 series; los `T1_MAIN` intactos; `Σ trims == desviación de volumen`.
- **Semanas paralelas**: misma clave → mismo día en todas; una semana con otros ejercicios → `INDEPENDENT` + nota; descarga: sin recorte y mismo día.
- **Decisiones**: `placements={bici→1}` → la unidad cae en el día 1 y el volumen no cambia.
- **Estabilidad**: con un programa Pierna A/Pierna B → UL×4, ≥ 80 % de los ejercicios de pierna siguen en «Pierna A/B» (umbral a fijar con el sweep).
- **Avisos**: golden: `FREQ_DOWN(Espalda,3,2)` al pasar de 4 días a `fullbody_x3`; `ADJACENT_HEAVY` aparece al arrastrar «Torso B» al martes y desaparece al volver.
- **Rechazos**: una prueba por frase de R0 (competición, variantes, vacía, `units < días`, falta levantamiento).
Rendimiento (instrumentada, no en CI): 1 semana de 25 ejercicios ≤ 150 ms; 12 semanas paralelas ≤ 400 ms en un Z Flip5.

## 7. Coste (días de una persona, sin contar QA en teléfono)
Conectar MIGRATE a `SplitRedistributor` y `SplitMigrationPlanner` (fachada, plan, informe) 2 · bloqueo y días libres (R9) 1,5 · estabilidad y día preferido (R5, R7) 1 · decisiones, tiempo opcional y avisos (R10, R11, R13) 1,5 · descarga y alcance multi-semana (R8) 1 · deshacer (R15) 1 · pruebas (§6) 2. **≈ 10 días de motor y datos**; UI (catálogo, vista previa reutilizando el tablero, revisión, hojas) ≈ 9 días más. Riesgo principal: la calidad de la asignación en programas con pocos ejercicios; ya hay red (`RedistributionSweepTest`, `SplitRadicalConversionTest`).

## 8. Preguntas para quien implemente
`completedSessionIds`: ¿se obtiene del registro de entrenos (`WorkoutLog`) o ya existe un resolvedor (`ProgramDetailViewModel.completedSessionIds`)? · Dónde vive `Program.lastSplitChange` sin tocar Room (el campo viaja en el JSON del programa). · `Session.requirement == OPTIONAL` y «Movilidad y core»: ¿fijadas (propuesto) o repartibles?
