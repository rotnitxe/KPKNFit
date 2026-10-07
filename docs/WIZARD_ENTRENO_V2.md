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

**Catálogo de ejercicios** (`assets/exercise_catalog_v2.json`: 96 familias, 206 definiciones, 521 configuraciones; metadatos medidos al 100 %:
patrón de movimiento (63), región, cadena cinética, músculos primarios, articulaciones, dificultad técnica 3,5–7,0, tiempo de montaje 10–55 s,
fatiga, `articulationType`)
- Es de gimnasio: solo 38 de 521 configuraciones son `bodyweight`. **Con cero material hay 14 ejercicios reales, ninguno de tracción, empuje
  vertical, bisagra, bíceps ni hombros.** Con «parque de calistenia» (barra de dominadas, paralelas, barra baja, banco) hay 36 configuraciones / 26
  ejercicios; sigue sin empuje vertical, con core pobre.
- Hay variantes por implemento (`configurations[]` con `implement`), pero solo 3 definiciones mezclan «peso corporal» como implemento
  (`reverse_lunge`, `glutes_puente_gluteos`, `calf_raise`). Faltan 18 variantes `__bodyweight` evidentes (zancadas, hip thrust, step-up, búlgara,
  patadas…) y definiciones de progresión (pike/handstand, australianas, remo con toalla…). No existen escaleras de progresión como dato: solo una
  hardcodeada en `NativeWorkoutProgressionRuntime.kt:28`.
- No hay dato de nivel por ejercicio, de «ejercicio pesado/1RM» ni de soportes por configuración (viven en `supportRequirementsFor`). `role`
  no marca los básicos (la sentadilla es `accessory_compound`): los básicos están hardcodeados en `NativeCandidateTable`.
- Disciplinas: calistenia 28 definiciones (faltan muscle-up, handstand, levers, L-sit); **halterofilia 0** (ni arranque ni dos tiempos);
  strongman: paseo del granjero ×4, Zercher ×10, sin yugo/piedras/log/trineo; armwrestling: 10 definiciones con antebrazo primario (faltan
  desviación radial/cubital, presión lateral, dedos, gripper); powerlifting completo.
- Material del catálogo (`equipmentId`, 19 valores) ≠ material del wizard (11 categorías + 20 llaves): `trx`, `ab_wheel`, `sliders`, `ghd`,
  `wrist_roller`, `plate`, `hex_bar`, `safety_bar`, `t_bar`, `h_bar` no se acreditan desde ninguna categoría (32 configuraciones inalcanzables).

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
   poleas y máquinas, así que desaparecen las preguntas «¿tienes rack?».
4. `AthleteType` lo escribe la activación a partir del objetivo (afecta a AUGE).

## 2. Detalle por paso

*(se completa a medida que cada paquete aterriza)*
