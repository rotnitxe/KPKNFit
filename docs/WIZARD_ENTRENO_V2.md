# Wizard de alta · módulo de Entreno v2

Rediseño del bloque **Entreno** del wizard de alta (`screens/onboarding/`, `domain/onboarding/`, `domain/training/`).
Rama de trabajo: `feat/wizard-entreno-v2`. Este documento es el **contrato** de la ola: lo que ve la persona, qué
datos escribe cada paso, qué lee el motor y qué se elimina. Los briefs de los agentes apuntan aquí.

> Regla de oro del usuario: **nada de botones vacíos ni de UI de relleno**. Toda opción nueva escribe un dato real
> del borrador y ese dato cambia lo que genera el motor (programas, sesiones, calentamientos, activación).

## 0. Principios de diseño (no negociables)

1. **Símbolos e ilustraciones animadas, nunca tarjetas.** Las opciones visuales (lugares, material, músculos,
   objetivos) son dibujos que se tocan directamente sobre la página negra continua. La selección se expresa con el
   propio dibujo: se enciende, se mueve, gana color de módulo y un trazo de «hecho». Sin cajas, sin bordes con
   brillo, sin radios de lista (ver `docs/WIZARD_PAGINA_LARGA.md`, reglas 1–9).
2. **Estética KPKN / Liquid Glass**, no «Android Compose genérico»: Syne en lo importante, Inter en lectura,
   tinta cálida `#F2EEE6` sobre negro, acentos de módulo (músculo `#F49A6E`, columna `#8FB2FF`, energía `#F7CF73`,
   mente `#C9B8FF`, ok `#43D18C`). Vidrio real solo donde ya existe (cabecera, botón, overlays con desenfoque).
3. **Menos preguntas, mejores defaults.** Lo que se puede deducir no se pregunta; lo que se pregunta se responde
   con un toque. Todo es editable después (el wizard sugiere, la persona confirma).
4. **Movimiento con sentido**: una animación explica la opción (un dominada en el parque, una mancuerna que sube)
   y se respeta «reducir movimiento» (`wizardReducedMotion()` → cuadro final estático).
5. **Texto mínimo**: títulos de pregunta ≤ 44 caracteres, subtítulos ≤ 100 (`SetupStepCopyRulesTest`). Castellano
   neutro y sin género gramatical cuando se pueda («¿Qué día llegas con más energía?»).

## 1. Flujo nuevo del bloque Entreno

| # | Paso (`SetupStepId`) | Pregunta | Control (símbolos) | Se salta cuando |
|---|---|---|---|---|
| 1 | `EXPERIENCE` | ¿Cuánta experiencia tienes? | opciones (sin cambios) | — |
| 2 | `EQUIPMENT` | ¿Dónde entrenas? | 3 escenas animadas: **Gimnasio · En casa · En espacios públicos** (multi) | — |
| 3 | `AVAILABILITY` | ¿Con qué material entrenas? | cuadrícula de símbolos animados de implementos | — (gimnasio: todo lo habitual ya viene marcado) |
| 4 | `GOAL` | ¿Cuál es tu objetivo? | generales (3) + específicos (7, condicionados al material) | — |
| 5 | `FRESH_DAY` *(nuevo)* | ¿Qué día llegas con más energía? | 7 días en fila, uno encendido | — |
| 6 | `WEEKDAYS` | ¿Qué días puedes entrenar? | calendario semanal (1–7 días) con inicio de semana | — |
| 7 | `SESSION_TIME` | ¿Cuánto tiempo tienes por sesión? | dial de reloj (20–180 min) | — |
| 8 | `CARDIO_TYPE`, `CARDIO_TIME` | cardio | (sin cambios de fondo) | el objetivo no incluye cardio |
| 9 | `VOLUME_TECHNIQUE` | ¿Cómo sientes tu técnica? | opciones | **experiencia = «Estoy empezando»** |
| 10 | `VOLUME_CONSISTENCY`, `VOLUME_STRENGTH`, `VOLUME_MOBILITY` | calibración | opciones | — |
| 11 | `CAPABILITIES` *(nuevo)* | ¿Qué ejercicios ya te salen? | 3–4 símbolos con nivel (nada / pocos / varios) | el material no hace falta para peso corporal y no es calistenia ni novato |
| 12 | `PRIORITIES` | ¿Qué músculos quieres mejorar más? | símbolos de músculo (los populares); «Omitir» claro arriba | — (omitible) |
| 13 | `TRAINING_MAX` / `TRAINING_MARKS` | ¿Conoces tus marcas? | regla deslizante kg/lb por levantamiento | novato, o disciplina que no usa marcas |
| 14 | `PLAN` | tu programa | overlay animado «preparando…» → revelado / carrusel | — |
| 15 | `WEEK_LAYOUT` *(nuevo)* | Así queda tu semana | sesiones arrastrables entre días + «adaptar a un reparto» | — |

Se **eliminan de la ruta** (el enum se conserva para leer borradores viejos): `DAYS`, `STYLE`, `SPLIT`,
`AUTOREGULATION`, `AUTOREGULATION_CONFIRM`, `WARMUPS`, `TRAINING_REVIEW`.

## 1b. Hallazgos de la auditoría (2026-10-07, solo lectura)

Base real sobre la que se construye; todo lo de abajo está verificado en el código salvo lo marcado «inferido».

**Programas**
- El catálogo de planes (55 entradas: 12 nativos, 10 plantillas, 29 protocolos, 4 autorados) vive en **código Kotlin**, no en JSON
  (`data/programs/PersonalizedPlanCatalog.kt`, `PlanEditorialTable.kt`, `data/protocols/*`). Solo hay **3 disciplinas** (`TrainingReference`:
  POWERLIFTING / HYPERTROPHY / POWERBUILDING). **No existe ningún programa de calistenia, halterofilia, armwrestling ni strongman**.
- Solo los 4 planes propios (`native:{strength,muscle,powerbuilding}-foundation-v2`, `native:complete-athlete-v2`) generan algo para cualquier
  combinación de 1–6 días; las recetas fijas exigen **días exactos** (`SetupTrainingPlanner.kt:60`) y verifican TODO el material.
- `SimpleCyclePersonalizer` (SCP, 2442 líneas) es el único generador: ruta (a) calendario fijo por días 1–6 con candidatos por slot según
  material (`NativeCandidateTable`), ruta (b) semana repetible con presupuesto MEV/MAV/MRV (`curatedPools`). Rechaza minutos fuera de 20–100
  (`SimpleCyclePersonalizer.kt:232`), y «gimnasio completo» NO acredita rack ni banco (quedan UNKNOWN hasta confirmar el panel de aparatos).
- Rechazos y textos: `PlanCandidateEvaluator.kt` + `PlanRejectionPresenter.kt`. El texto literal «no tenemos un programa» no existe; los reales
  son «Falta confirmar si tienes rack y banco», «Este plan usa 4 días distintos; elegiste 3», «no cabe en los M min…», etc.
- Splits: 36 publicados (`data/splits/SplitTemplates.kt`; 1 día: ninguno). `SplitApplicationEngine.MIGRATE` reasigna sesiones ENTERAS a días por
  palabras clave, **no redistribuye ejercicios**. Hay arrastrar-y-soltar de sesiones entre días en `screens/programdetail/components/DayView.kt`.
- Calentamientos: `Exercise.warmupSets` (% de la carga de trabajo) ya se ejecuta en vivo; `PlanMaterializer.assignWarmups` solo pone el preset al
  primer compuesto de cada patrón. **No hay generador de rampa ni de movilidad previa por articulación**; sí hay metadatos `jointInvolvement` (15
  articulaciones) por configuración y un catálogo de movilidad aparte (`MobilityExerciseCatalog`, 162 movimientos).
- Autorregulación: `PROPOSE` (sugerir y confirmar) es el valor por defecto y ya existe el flujo `PendingProgramAction(CONFIRM_AUTOREGULATION)`.
  Quitar los pasos solo exige fijar ese valor.
- Activación (`SetupCommitCoordinator`): los minutos NO se guardan como preferencia; el material va a `Settings.equipmentAvailability`; la
  prioridad a `Program.planOrderPriorities`; las marcas a `Program.powerliftingProfile` solo en recetas fijas. `AthleteType`
  (ENTHUSIAST, HYBRID, CALISTHENICS, BODYBUILDER, POWERBUILDER, POWERLIFTER, WEIGHTLIFTER, ZERCHER_LIFTER) alimenta la capacidad de fatiga del
  motor AUGE (`AugeFatigueEngine`) y **el alta nunca lo escribe**: el objetivo del wizard debe escribirlo.

**Catálogo de ejercicios** (`assets/exercise_catalog_v2.json`; en la auditoría: 96 familias, 206 definiciones, 521 configuraciones; **hoy 96 / 228 / 553**
tras los lotes BW-1 y OL-1; metadatos medidos al 100 %: patrón de movimiento (63), región, cadena cinética, músculos primarios, articulaciones,
dificultad técnica 3,5–7,0, tiempo de montaje 10–55 s, fatiga, `articulationType`). Las cifras de las viñetas siguientes son las de la auditoría; lo que
cambiaron los lotes está en «Estado tras los lotes BW-1 y OL-1», al final de esta sección.
- Es de gimnasio: solo 38 de 521 configuraciones son `bodyweight`. **Con cero material hay 14 ejercicios reales, ninguno de tracción, empuje
  vertical, bisagra, bíceps ni hombros.** Con «parque de calistenia» (barra de dominadas, paralelas, barra baja, banco) hay 36 configuraciones / 26
  ejercicios; sigue sin empuje vertical, con core pobre.
- Hay variantes por implemento (`configurations[]` con `implement`), pero solo 3 definiciones mezclan «peso corporal» como implemento
  (`reverse_lunge`, `glutes_puente_gluteos`, `calf_raise`). Faltan 18 variantes `__bodyweight` evidentes (zancadas, hip thrust, step-up, búlgara,
  patadas…) y definiciones de progresión (pike/handstand, australianas, remo con toalla…). No existen escaleras de progresión como dato: solo una
  hardcodeada en `NativeWorkoutProgressionRuntime.kt:28`.
- No hay dato de nivel por ejercicio, de «ejercicio pesado/1RM» ni de soportes por configuración (viven en `supportRequirementsFor`). `role`
  no marca los básicos (la sentadilla es `accessory_compound`): los básicos están hardcodeados en `NativeCandidateTable`.
- Disciplinas: calistenia 28 definiciones (faltan muscle-up, handstand, levers, L-sit); **halterofilia 0** (ni arranque ni dos tiempos; OL-1 los añadió, ver «Estado tras los lotes» más abajo);
  strongman: paseo del granjero ×4, Zercher ×10, sin yugo/piedras/log/trineo; armwrestling: 10 definiciones con antebrazo primario (faltan
  desviación radial/cubital, presión lateral, dedos, gripper); powerlifting completo.
- Material del catálogo (`equipmentId`, 19 valores) ≠ material del wizard (11 categorías + 20 llaves del subpanel + 11 llaves de símbolo).
  Medido con `EquipmentReachTest` (`app/build/reports/equipment-reach/reach.txt`): antes de los paquetes E y E2, **89 de 521 configuraciones eran
  inalcanzables** desde cualquier símbolo (56 `machine` sin llave curada; 32 por implemento: `trx`, `plate`, `hex_bar`, `safety_bar`, `t_bar`,
  `ab_wheel`, `h_bar`, `sliders`, `ghd`, `wrist_roller`; y el curl nórdico). **Hoy quedan 8**: los cuatro implementos raros que no se acreditan a
  propósito (`safety_bar` ×4, `h_bar`, `sliders`, `wrist_roller`) y el curl nórdico (`nordic_anchor` no tiene símbolo ni llave).

**Estado tras los lotes BW-1 y OL-1** (aprobados por el usuario para la rama `feat/wizard-entreno-v2`; el paso a `master` sigue pendiente de su OK)
- **BW-1** (`docs/entreno-v2/lote-bw1-report.md`): 9 definiciones nuevas (pica plana y con pies elevados, dominada negativa, flexiones diamante y arquero, hollow body, dead bug, plancha
  lateral, bird dog, sentada en pared) y 8 configuraciones `__bodyweight` en definiciones ya curadas (zancada, zancada caminando, step-up, búlgara, sumo, sissy, buenos días y rumano a una
  pierna sin carga). Catálogo 206 / 521 → 215 / 539; configuraciones de peso corporal 38 → 56. Alcance medido con `EquipmentReachTest`: solo cuerpo 20 → 34, parque 35 → 53, casa con anillas
  y cajón 23 → 41, gimnasio completo 510 → 528.
- **OL-1** (`docs/entreno-v2/lote-ol1-report.md`): 13 definiciones y 14 configuraciones de levantamientos olímpicos (cargadas y arranques de potencia, desde colgado y completos, tirones, envión de
  empuje y de tijera, sentadilla de arranque) y de acarreos (maletín y Zercher). Catálogo 215 / 539 → 228 / 553; los enviones y la sentadilla de arranque piden rack
  (`supportRequirementsFor`); gimnasio completo 528 → 542. Siguen sin existir yugo, piedras, tronco, eje, trineo, saco de arena, barril ni neumático: **Strongman** queda limitado a paseos y
  Zercher hasta un lote posterior.
- **Marcas olímpicas** (S-B2, con la evidencia de D1b): las reservas de halterofilia cuelgan cada levantamiento olímpico de la marca del arranque (`SNATCH`) o de los dos tiempos (`CLEAN_AND_JERK`) con una razón estimada de 0,65–1,0 y ninguno es «básico» (nivel intermedio o avanzado). Por eso `MarksContext.CATALOG_HAS_OLYMPIC_LIFTS = true`: Halterofilia pregunta sentadilla, arranque y dos tiempos, pero solo desde el nivel intermedio (`MarksContext.OLYMPIC_MIN_LEVEL`): quien empieza o vuelve no recibe esos levantamientos y no ve esos controles. `OlympicMarksTest` mide con el generador real a qué ids corresponde cada marca, que declararla cambia solo la carga de sus ejercicios (sin marca, «carga pendiente») y que se pregunta si y solo si el programa los lleva. Strongman lee la marca de los dos tiempos en su envión de empuje pero no la pregunta (ese envión sale con «carga pendiente»).
- Inalcanzables desde cualquier símbolo: **8 de 553** (los mismos cuatro implementos raros y el curl nórdico).

**Decisiones que de aquí se derivan**
1. Los **objetivos generales** (y todo caso en que ningún plan de autor encaje) los resuelve un **generador nuevo** (`domain/training/generator`)
   con reservas curadas de ejercicios por patrón y escaleras de progresión, ajustado a minutos con `SessionDurationEstimator`. Nunca falla por
   días, minutos o material: si el catálogo no da para algo (p. ej. tracción sin barra) lo dice con una nota honesta.
2. Los **objetivos específicos** Powerlifting / Powerbuilding / Culturismo usan los planes de autor y propios existentes (más una versión
   «a medida» del generador para cualquier combinación de días y minutos). **Calistenia, Armwrestling, Strongman y Halterofilia** no tienen
   planes de autor: se sirven con el generador en modo disciplina sobre el catálogo actual y se rotulan «versión inicial»; Halterofilia
   y Strongman quedan limitados hasta que el catálogo incorpore sus levantamientos (altas con el flujo de curaduría del catálogo, con OK del
   usuario). Ningún perfil se ofrece como si tuviera contenido que no existe.
3. El **material** del paso se traduce con `EquipmentSymbols` (dominio) a categorías + llaves `PRESENT/ABSENT`; «gimnasio» marca presentes rack, banco,
   poleas y máquinas, así que desaparecen las preguntas «¿tienes rack?». Un único resolutor (`resolveEffectiveEquipment`) y un único filtro
   (`ConfigurationEquipmentFilter`) sirven al planificador, a los planes de autor y al generador. **«Máquinas» es una sala de máquinas**
   (`EquipmentAvailability.machinesAsCategory`): la categoría basta para las 73 configuraciones `machine` (dos piden además banco), sin el modo de
   configuración exacta (DEV-r2-06), y las nueve llaves curadas siguen presentes para las recetas de autor. Los extras sin símbolo propio los acreditan sus símbolos
   madre: los discos con la barra (en cualquier lugar); barra hexagonal, barra T, GHD, rueda abdominal y bancos declinado y de hiperextensión
   solo con gimnasio entre los lugares; la barra baja, con la barra de dominadas de un parque.
4. `AthleteType` lo escribe la activación a partir del objetivo (afecta a AUGE).

## 2. Detalle por paso

### 2.1 Pasos con símbolos (`EQUIPMENT` … `TRAINING_MAX`)

Cada paso es `EntrenoXxxStep(state, vm)` en `screens/onboarding/entreno/`: lee `state.draft` y escribe SOLO por el ViewModel
(`SetupWizardViewModel`). El título y el subtítulo los pinta la sección; el paso aporta su control y, si hace falta, una nota de
una línea (`EntrenoStepNote`: sin caja, con el texto cambiando por fundido y sin dejar hueco cuando no hay nota).

| Paso | Control (`design/entreno/`) | Escribe (VM → dato del borrador) | Lo que conviene saber |
|---|---|---|---|
| `EQUIPMENT` | `PlaceSymbolRow` (tres escenas) | `togglePlace` → `trainingPlaces` | siempre queda un lugar; con dos o más, nota «Después podrás elegir dónde entrenas cada día.»; «En espacios públicos» ocupa tres líneas con la letra al 130 % |
| `AVAILABILITY` | `EquipmentSymbolGrid` | `toggleEquipmentSymbol` → disponibilidad de material | «Solo peso corporal» es exclusivo; en gimnasio lo habitual viene marcado; sin lugares elegidos solo hay la nota «Elige primero dónde entrenas.» |
| `GOAL` | `GoalProfileList` | `setGoalProfile` → `goalProfile` | un perfil que el material no cumple sale atenuado con su razón en una línea; tocarlo ofrece «Cambiar mi material» (`editStep(AVAILABILITY)`) y al continuar desde allí se vuelve a este paso |
| `FRESH_DAY` | `FreshDayRow` | `setFreshDay` → `freshestDay` | un solo día; el inicio de semana lo sigue mientras no se toque |
| `WEEKDAYS` | `WeekCalendar` | `toggleWeekday`, `setWeekStart`, `setDayPlace` → `selectedWeekdays`, `weekStartDay`, `dayPlaces` | el contador grande muestra «–» cuando no hay días (`weekCounterText`; el cero de Syne se leía como «O»); el lugar de cada día solo aparece con dos o más lugares |
| `SESSION_TIME` | `SessionClockDial` | `setSessionMinutes` → `minutesPerSession` | el dial avisa en cada muesca y cada escritura relanza el barrido de programas: se escribe una vez a los 220 ms de que el dedo se detiene (y al cerrar el paso si quedó algo sin escribir); sin valor arranca en 60 min atenuado y el check sigue apagado |
| `CAPABILITIES` | `CapabilitySymbols` | `setCapability` → `capabilities` | la figura hace el movimiento con el ritmo del nivel («Aún no» quieta, «Algunas» tres repeticiones y pausa, «Varias» continuo); tocar la figura avanza el nivel y cada segmento lo fija |
| `PRIORITIES` | `MuscleSymbolGrid` + «Omitir» | `toggleMuscle`, `clearMuscles` → `priorityMuscles` / `orderPriorities` | hasta 5; las sugerencias del perfil llegan precargadas y rotuladas «Sugerido» mientras no se toque el paso; «Omitir» (arriba a la derecha, 48 dp) limpia y confirma el paso, y el resumen dice «Sin preferencia» |
| `TRAINING_MAX` | `LiftMarksPicker` | `setLiftMark`, `setMarksUnit` → `liftMarks`, `marksUnit` | regla kg/lb por levantamiento; como el dial, escribe una vez cuando el dedo se detiene; «No la sé» borra la marca; qué levantamientos salen lo decide `MarksContext` (Halterofilia pregunta además el arranque y los dos tiempos desde el nivel intermedio: `CATALOG_HAS_OLYMPIC_LIFTS = true` desde que las reservas de D1b leen esas marcas, ver §1b) |

Reglas comunes: ningún control lleva caja ni borde; los objetivos táctiles miden 48 dp (también los siete días del calendario: la tira
sangra hasta 24 dp por lado hacia los márgenes, 48 dp a 360 dp y 45,7 dp a 320 dp); los títulos de ficha y de portada bajan de tamaño
antes que cortarse (`fitTitle`: parten solo en espacios, hasta 13 sp y, si una palabra suelta no cabe, 11–12 sp); con «reducir
movimiento» (`wizardReducedMotion()`) cada símbolo queda en su cuadro final. La lectura y la escritura del paso se prueban en `EntrenoStepsSmokeTest` (Robolectric, marcas `setup-place-*`,
`setup-equipment-*`, `setup-goal-*`, `setup-freshday-*`, `setup-weekday-*`, `setup-sessiontime-*`, `setup-capability-*`,
`setup-muscle-*`, `setup-mark-*`).

### 2.2 Cómo se revisa un paso en el teléfono

`WizardHarnessActivity` (solo `src/debug`) abre el asistente REAL (modo solo entreno) ya colocado en el paso que se pida, con un
borrador construido caminando la ruta de verdad. Extras (`--es start GOAL --es persona home …`): `start`, `persona`
(`gym`/`home`/`park`/`multi`/`all`), `answers` (`places=GYM,HOME/minutes=75`…), `width`, `fontScale`, `reducedMotion`, `fps` (medidor de
fluidez: por paso, la ENTRADA —el primer 1,1 s— y el resto), `sample` (con `fps`: dónde estaba el hilo principal en cada bache),
`blur` (`on`|`off`: fuerza la rama con o sin desenfoque de los overlays; el teléfono de pruebas lo tiene desactivado), `autonext`
(recorre el bloque solo: paso vacío, respuesta, «Continuar») con `until`, y `autoselect` (elige el primer programa
cuando PLAN termina). Se conduce SIEMPRE con `C:\kw\tools\phone_run.py` (candado del teléfono, solo el applicationId `.dbg`).

Hasta «Activar» (auditoría Q): `persona` admite también los seis recorridos de la auditoría (`qa` … `qf`: gimnasio y fuerza y masa muscular,
casa con mancuernas y banco y culturismo, espacios públicos y calistenia, gimnasio y casa con powerlifting, solo cuerpo y funcional, y
halterofilia); con `autonext` y `until REVIEW_ACTIVATE` el recorrido llega a la revisión final con el botón «Activar y entrar a KPKN» a
la vista (`activate` en milisegundos lo pulsa solo con la misma función, `commit()`), y al activar el arnés enseña el RESUMEN de lo que
quedó, leído de `ProgramRepository` y no del estado del asistente: nombre del programa, días, sesión principal, lugar, ejercicios y
minutos de cada sesión, y los Ajustes que escribió el alta (`summary`: `names` añade los ejercicios con su aproximación `aprox.` y
movilidad `mov.`; `only` reabre el resumen del programa activo). `rotate` (`landscape+5000,portrait+4000`) gira la actividad con
`requestedOrientation` —la recreación real de un giro, sin tocar ningún ajuste del teléfono— y, al recrearse, el borrador NO se
reconstruye; matar el proceso es volver a lanzar con `--ez reset false`.

## 3. Qué lee cada dato declarado («declarado → efecto», auditoría Q)

Regla de oro: lo que se declara en el asistente cambia de verdad lo que se activa. Tres pruebas la vigilan y fallan si un dato deja de leerse:

- `EntrenoDeclarationEffectTest` (Robolectric) recorre el asistente REAL —ViewModel, generador de rutinas, semana armada y planificador reales; Room en memoria y el coordinador de altas reales; solo los Ajustes de lectura son fijos— con una persona base, cambia UN solo dato y exige que cambie el canal que lo gobierna: en el programa guardado (ejercicios, series, días, minutos, aproximación, movilidad, lugar de cada sesión, sesión principal, cargas, cardio) o en lo que escribe la activación (Ajustes: material, tipo de atleta, calibración de volumen, unidad de peso; programa: bolsa de prioridades, marcas, autorregulación «sugerir y confirmar»). Cada caso deja una fila (dato · caso → canales que cambian) en `app/build/reports/entreno-declaration-effect/table.txt`.
- `EntrenoMaterialCoherenceSweepTest` genera y activa 7 perfiles de material × los 10 objetivos × 1–7 días × 9 tiempos (20–180 min): ejecutable, ninguna sesión pide material que el lugar de su día no tiene (filtro único `ConfigurationEquipmentFilter` y contraste de `SessionPlaceFit`), la sesión más larga cabe en lo pedido (+1 min) desde 60 min, la principal cae en el día con más energía y toda sesión pesada lleva aproximación y movilidad. Además mide que cada marca que se pregunta cambia las cargas en las 12 configuraciones típicas que prueba (3–5 días, 60–90 min, intermedio y avanzado).
- `EntrenoDraftRobustnessTest`: matar el proceso en cualquier paso del bloque, o volver atrás desde cualquiera, conserva el cursor, lo respondido, el programa elegido y la semana armada.

| Dato del borrador | Lo escribe | Lo lee | Efecto verificado |
|---|---|---|---|
| `trainingPlaces` | `withPlaces`, `togglePlace` | `routineRequest` (`places`, `dayPlaces`, `availabilityByPlace` por `PlaceMaterial.byPlace`) → `RoutineGenerator` (`placeOf`, `availabilityFor`); `generatedProgramOf` (`Session.placeId`); `reconcileSessionPlaces`; `EquipmentSymbols.reseed`; `buildSettingsPatch` | lugar de cada sesión, ejercicios (en casa sin material solo caben los del cuerpo) y `Settings.equipmentAvailability` |
| Material por símbolo (16) | `withMaterial`, `toggleEquipmentSymbol` | `EquipmentSymbols.availabilityOf` → `routineRequest.availability` → `DayEquipment.allows` → `ConfigurationEquipmentFilter`; planes del catálogo: `effectiveEquipmentIds` (`resolveEffectiveEquipment`); `buildSettingsPatch` | los 16 cambian `Settings.equipmentAvailability`; 14 cambian el programa de casa (quitar un implemento del gimnasio retira sus ejercicios). **Sin consumidor alcanzable desde el asistente: «Cuerda de saltar» y «Cardio» (máquinas)**: ningún ejercicio del catálogo pide cuerda (`DayEquipment.hasJumpRope` no lo lee nadie) y el cardio sale del tipo que la persona elige en CARDIO_TYPE (caminar, correr o bicicleta al aire libre), de modo que `DayEquipment.cardioTypes` solo elegiría una máquina si no hubiera tipo declarado |
| `dayPlaces` | `withDayPlace` | `effectiveDayPlaces` → `RoutineRequest.dayPlaces`; `reconcileSessionPlaces` | la sesión de ese día usa el material de ese lugar y lleva su `placeId`; las demás no cambian |
| `goalProfile` (10) | `withGoalProfile`, `setGoalProfile` | `GeneratedPlans.modeFor` (modo del generador), `GoalProfileMapping` (objetivo del motor, estilo de calibración, tipo de atleta), `TrainingGoalRequirements` (puerta de GOAL), `buildSettingsPatch.athleteType` | cada perfil da su programa y su `AthleteType`; «Fuerza y masa muscular» y «Powerbuilding» arman el mismo reparto con los mismos ejercicios (se distinguen por nombre, tipo de atleta y planes del catálogo que ofrece PLAN) |
| `experience` | `setStepChoice(EXPERIENCE)` | `toRoutineLevel` → `RoutineRequest.level` (`GenContext.rankProfile`, `VolumeBudgets.levelFactor`, nivel de `ApproachPlanner`); `MarksContext`; ruta (`asksTechnique`) | ejercicios, series, cargas, aproximación y movilidad |
| Técnica, constancia, fuerza, movilidad | `setStepChoice(VOLUME_*)` | `volumeAnswers` → `RoutineRequest` → `VolumeBudgets.of` (`VolumeCalibrationEngine`); `buildSettingsPatch.volumeCalibrationProfile` | las cuatro cambian `Settings.volumeCalibrationProfile`; bajar una respuesta cambia el volumen del programa (subirla a «3» no lo cambia con 60 min: el tiempo, no el volumen recuperable, es el tope) |
| `freshestDay` | `withFreshestDay`, `setFreshDay` | `routineRequest.freshestDay` → `WeekPlanner.mainDay`/`arrange`; `effectiveWeekStart` | `Session.isMainSession` cae ese día (o el primero de entreno posterior) y la semana empieza ahí mientras no se toque |
| `selectedWeekdays` | `withWeekdays`, `toggleWeekday` | `orderedWeekdays()` → `RoutineRequest.weekdays` → `RoutineGenerator` (`ProgramSchedulePlan.trainingDays`) | una sesión por día elegido, con el reparto que corresponde a ese número de días |
| `weekStartDay` | `withWeekStart`, `setWeekStart` | `effectiveWeekStart()` → `RoutineRequest.weekStartDay` → `Program.startDay`, `ProgramSchedulePlan.weekStartDay` | inicio de semana del programa (y orden de armado de las sesiones) |
| `minutesPerSession` | `withSessionMinutes`, `setSessionMinutes` | `RoutineRequest.targetMinutes` → `GenContext.windowMinutes`/`toleratedMaxMinutes` → `SessionAssembler` (ajuste fino) y `SessionDurationEstimator`; revisión: `SessionTimeFit` | la sesión más larga mide lo pedido (+1 min, `RoutineGenerator.TIME_TOLERANCE_MINUTES`) desde 60 min; con menos, la estructura de la sesión puede pasar (ver «Límites») y la nota de tiempo lo dice |
| Cardio (tipo y minutos) | `setStepChoice(CARDIO_TYPE/CARDIO_TIME)` | `cardioPreference()` (solo con objetivo de cardio) → `RoutineRequest.cardio` → `GenContext.cardioBlockMinutes`, `CardioBuilder.typeFor` | tipo del cardio y minutos del bloque que cierra cada sesión de fuerza |
| `capabilities` | `withCapability`, `setCapability` | `RoutineRequest.capabilities` → `BodyweightLadders.tierFor` ← `ExerciseSelector` | peldaño de dominadas, flexiones, fondos y sentadilla a una pierna |
| `priorityMuscles` | `withMuscles`, `toggleMuscle` | `priorityMuscleSymbols()` → `RoutineRequest.priorityMuscles` → `WeekPlanner.applyPriorities`, `VolumeBudgets`; `generatedProgramOf` (`planOrderPriorities`) | ejercicios y series de esos músculos y la bolsa de prioridades del programa |
| `liftMarks` | `withLiftMark`, `setLiftMark` | `RoutineRequest.marks` → `SessionAssembler` (`reference1RM`, % del 1RM); `ExerciseSelector.leastUsedMark` (reparte las alternativas empatadas entre marcas distintas, para que una marca declarada tenga ejercicio que la lea); `powerliftingProfile` (sentadilla, banca, peso muerto) | cargas de los ejercicios de cada marca y `Program.powerliftingProfile`; las 15 marcas que se preguntan (por perfil) cambian las cargas en las 12 configuraciones típicas probadas |
| `marksUnit` | `withMarksUnit`, `setMarksUnit` | `buildSettingsPatch.weightUnit` | «lb» deja `Settings.weightUnit = LBS` (si no se tocó la unidad del peso corporal); la marca guardada sigue en kg. Decisión de producto por confirmar: la unidad de las marcas mueve la unidad de peso de toda la app |
| `planVariantSeed` | `anotherPlanVersion` | `RoutineRequest.variantSeed` → `ExerciseSelector.choose` | otra elección entre ejercicios equivalentes |
| `weekLayoutOverrides` | `moveSession` | `applyLayout` → `WeekAssignment.applyAssignment` | día de la sesión movida (o intercambio) en el programa guardado |
| `adaptedSplitId` | `adaptToSplit` | `applyLayout` → `SplitRedistributor.redistribute` | sesiones y ejercicios del reparto elegido |

**Planes del catálogo (propios y de autor)**: el camino de PLAN que no es el programa «a medida». Reciben los días, el nivel, el material, el cardio, la calibración y los músculos, y los minutos valen como TOPE (el plan que cabe, con un 15 % de tolerancia, no se estira ni se recorta; el que no cabe no se ofrece). **No reciben** el día con más energía, el inicio de semana ni las marcas (su carga se aprende de la primera sesión) ni el lugar de cada día: la estructura del autor manda y la semana armada permite mover las sesiones. Es una decisión de producto pendiente (si debe pesar el día con más energía en los planes con receta fija) y `EntrenoDeclarationEffectTest` lo deja en la tabla con el prefijo `[plan propio]`.

**Límites conocidos y medidos**:
- Con menos de 60 min la estructura de la sesión puede pesar más que el último minuto: el calentamiento fijo de 3 min del estimador, la aproximación, la movilidad, tres ejercicios con sus descansos mínimos, el bloque de cardio (que no se recorta) y las series prioritarias (que el ajuste fino no sacrifica por uno o dos minutos) llegan a medir más que lo pedido —hasta +10 min con 20 min—. El generador deja la sesión dentro de su ventana (85–110 %) y la revisión lo dice con la nota «~N min por sesión». El informe del barrido (`app/build/reports/entreno-material-sweep/report.txt`) cuenta cuántas celdas pasan de +1 min por cada tiempo pedido.
- Con un solo día solo cabe un hueco olímpico, así que Halterofilia usa una de las dos marcas olímpicas.
- Reabrir el borrador: confirmar un paso cierra SU marca de «por revisar» (`SetupStepProgress.recordAnswer`) y elegir el programa en PLAN solo marca PLAN y WEEK_LAYOUT (`SetupChangeSource.PLAN_CHOICE`, no las marcas que van antes), de modo que el cursor no vuelve a un paso que ya se pasó. Adaptar el reparto en la semana armada SÍ deja el programa por revisar (decisión de las reglas de dependencias): si se sale justo ahí, el borrador se reabre en PLAN con todo lo respondido.
