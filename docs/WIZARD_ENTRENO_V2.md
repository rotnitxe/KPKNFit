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
| 12 | `PRIORITIES` | ¿Qué músculos priorizas? | símbolos de músculo (los populares); «Omitir» claro arriba | — (omitible) |
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
| `WEEKDAYS` | `WeekCalendar` | `toggleWeekday`, `setWeekStart`, `setDayPlace` → `selectedWeekdays`, `weekStartDay`, `dayPlaces` | el contador grande muestra «–» cuando no hay días (`weekCounterText`; el cero de Syne se leía como «O»); el lugar de cada día solo aparece con dos o más lugares; si el día con más energía se quita de los de entreno, una nota dice adónde pasa la sesión más fuerte (`freshDayNote`: el primer día de entreno posterior, la misma cuenta que `WeekPlanner.mainDay` del generador) |
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

### 2.1b La semana armada (`WEEK_LAYOUT`): el tablero de siete filas

`WeekLayoutBoard` (`design/entreno/layout/`) es una **lista vertical de siete filas**, de la primera a la última de la semana de la persona.
La tira horizontal anterior enseñaba tres días y medio a 360 dp y obligaba a deslizar de lado para llevar una sesión a otro día; ahora
caben los siete a 360 y a 320 dp, con la letra normal y al 130 %, sin desplazarse de lado (la página sí se desplaza hacia abajo: con letra
grande la lista mide ≈ 630 dp).

| Pieza | Qué es |
|---|---|
| Fila de un día | su nombre corto («Lun»), el disco de la ranura (vidrio neutro tenue; un guion si es de descanso), la palabra «Descanso» y un filete debajo. Todas miden lo mismo (≥ 64 dp, más con letra grande): el alto sale de **medir los textos reales** (título en una línea sin bajar de 14 sp o en dos, detalle en una o en dos líneas según el ancho que queda), así ninguna ficha cambia de tamaño al moverse. |
| Ficha de una sesión | la mancuerna (chispas de energía en la sesión principal), el título, «60 min · 6 ejercicios» (con el glifo del lugar delante cuando el programa reparte sus sesiones entre varios) y el **asa** de seis puntos. Va en una capa propia sobre las filas y se coloca con un resorte (`FichaMotion`). |
| Mover | **pulsación larga** en la ficha o **arrastre vertical desde su asa** (agarra al instante, sin esperar): la ficha se levanta, la fila bajo ella se enciende y, si está ocupada, su sesión ya se desliza al hueco (vista previa del intercambio); soltarla por encima o por debajo de la lista cancela. Cerca del borde visible de la página, esta se desplaza sola (`LocalWizardPageScroll`, con el mismo límite que la persona). |
| Sin arrastre | tocar una sesión la elige y enciende los días válidos; tocar un día la mueve. TalkBack: cada ficha ofrece «Mover a <día>»; los días de descanso se leen como «Martes, descanso» y, con una sesión elegida, ofrecen «Mover aquí la sesión elegida». |

Se conservan sin cambios de contrato el selector de repartos, las notas, el estado «adaptando» (la lista se atenúa y no deja tocar) y
«Restablecer». La firma pública de `WeekLayoutBoard` no cambió (la usa `EntrenoWeekLayoutStep`); `WeekLayoutSession` ganó un campo opcional,
`place`, y mientras `EntrenoWeekLayoutStep` no lo rellene el lugar se lee del final del foco («Pecho y espalda · En casa»).

### 2.2 Cómo se revisa un paso en el teléfono

`WizardHarnessActivity` (solo `src/debug`) abre el asistente REAL (modo solo entreno) ya colocado en el paso que se pida, con un
borrador construido caminando la ruta de verdad. Extras (`--es start GOAL --es persona home …`): `start`, `persona`
(`gym`/`home`/`park`/`multi`/`all`), `answers` (`places=GYM,HOME/minutes=75`…), `width`, `fontScale`, `reducedMotion`, `fps` (medidor de
fluidez: por paso, la ENTRADA —el primer 1,1 s— y el resto), `sample` (con `fps`: dónde estaba el hilo principal en cada bache),
`blur` (`on`|`off`: fuerza la rama con o sin desenfoque de los overlays; el teléfono de pruebas lo tiene desactivado), `autonext`
(recorre el bloque solo: paso vacío, respuesta, «Continuar») con `until`, y `autoselect` (elige el primer programa
cuando PLAN termina). Se conduce SIEMPRE con `C:\kw\tools\phone_run.py` (candado del teléfono, solo el applicationId `.dbg`).

El medidor (`fps`) cuenta además, por paso, `s`: el cuadro más largo SOLO mientras la página se desliza (los primeros 0,62 s de la entrada),
para distinguir lo que se ve moverse de lo que llega después de la animación (el control del paso que asoma se compone tras ella). Para
varias actividades y gestos con el dedo mantenido (arrastrar una sesión) en una sola sesión del teléfono, `C:\kw\shots\w3\phone_probe.py`
reutiliza el candado de `phone_run.py` y se corta si el teléfono está cerrado (con el Flip5 plegado la pantalla principal no enciende y las
capturas salen negras), si su pantalla principal está encendida (la persona lo usa) o si la persona abre otra cosa.

Sin teléfono (o para ver la composición antes de instalar nada): `WizardRenderSheetTest` (`src/test/.../render/`) dibuja el tablero de la
semana y las filas-resumen con las tipografías reales a 360 dp y a 320 dp, con la letra al 100 y al 130 %, y escribe PNG en la carpeta de
`KPKN_RENDER_DIR` (sin esa variable se salta entera): `KPKN_RENDER_DIR=C:/kw/shots/w3/render bash C:/kw/tools/gradle_slot.sh
C:/kw/<worktree>/android-native :app:testBaseDebugUnitTest --tests "com.example.kpkn.render.*"`. No dibuja el desenfoque ni la GPU: sirve para
ver medidas y textos, no para medir fluidez.
