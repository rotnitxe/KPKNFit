# S-A · Columna vertebral del módulo de Entreno (datos, ruta, reductores, validación)

Lee antes `docs/entreno-v2/BRIEF_COMUN.md`, **todo** `docs/WIZARD_ENTRENO_V2.md` (flujo, principios y hallazgos de la auditoría) y
`docs/WIZARD_PAGINA_LARGA.md`. Eres el dueño de los archivos compartidos del wizard en esta ola: **nadie más** toca
`SetupStepGraph.kt`, `SetupStepDefinitions.kt`, `SetupWizardModels.kt`, `SetupStepAnswers.kt`, `SetupWizardSteps.kt`,
`SetupStepSummaries.kt`, `SetupDraftCompatibility.kt` ni los pasos de entreno hasta que entregues. Es el paquete más delicado de la ola:
sin atajos, y con las pruebas del wizard en verde al final.

## Objetivo
Dejar el bloque Entreno **recorrible de punta a punta con la ruta nueva y todos sus datos reales** (borrador, reductores, validación, resúmenes,
revisión, activación, compatibilidad con borradores viejos), con **controles provisionales funcionales** (sin diseño final) que otros
agentes reemplazarán por los símbolos animados. Los motores nuevos (generador, aproximación, reparto) NO son tuyos: el PLAN sigue usando la
lista de candidatos actual hasta que el orquestador integre el generador (paquete S-B).

## 1. Ruta (`SetupStepGraph`, `SetupStepContext`)
Nuevos `SetupStepId`: `FRESH_DAY`, `CAPABILITIES`, `WEEK_LAYOUT` (bloque TRAINING). Ruta del bloque cuando `includeTraining`:
`EXPERIENCE, EQUIPMENT, AVAILABILITY, GOAL, FRESH_DAY, WEEKDAYS, SESSION_TIME, [CARDIO_TYPE, CARDIO_TIME], [VOLUME_TECHNIQUE],
VOLUME_CONSISTENCY, VOLUME_STRENGTH, VOLUME_MOBILITY, [CAPABILITIES], PRIORITIES, [TRAINING_MAX], PLAN, [WEEK_LAYOUT], MILESTONE_TRAINING`.
- Condicionales (campos nuevos de `SetupStepContext`, derivados SOLO de datos del borrador, nunca de «respondido»):
  `asksTechnique = experiencia != NEW` (el novato nunca ve la técnica; su respuesta se **deriva** como «1 · Aprendiendo» con procedencia DERIVED, no la inventa el usuario),
  `goalIncludesCardio` (STRENGTH_CARDIO, FUNCTIONAL_HEALTH y los legacy MIXED/COMPLETE_ATHLETE) para CARDIO_*, `asksCapabilities` (ver abajo),
  `asksMarks = MarksContext.liftsFor(...).isNotEmpty()`, `hasWeekLayout = programRoute != LATER`.
- `DAYS`, `STYLE`, `SPLIT`, `AUTOREGULATION`, `AUTOREGULATION_CONFIRM`, `WARMUPS`, `TRAINING_REVIEW`, `TRAINING_MARKS`, `ROUTE`: **fuera de la ruta productiva**. Conserva
  los valores del enum y sus definiciones (con `legacyOnly`/comentario) para leer borradores viejos. `daysPerWeek` pasa a ser **derivado** de `selectedWeekdays.size`
  (la validación antigua `selectedWeekdays.size == daysPerWeek` desaparece; mantén `daysPerWeek` sincronizado en el reductor de WEEKDAYS porque el motor lo lee).
- `SetupStepGraph.REVISION` 3 → 4. `SetupDraftCompatibility.repairStepProgress` ya recoloca cursores en pasos retirados (primer paso pendiente de la ruta nueva): compruébalo con pruebas.
- Anclas (`extraAnchors`) para los pasos nuevos; `SetupDependencyRules`/`SetupInputFootprint`/`SetupChangeDetector`: añade lo nuevo (lugares, perfil de objetivo, inicio de semana,
  día de más energía, lugares por día, capacidades, marcas) a la huella y a las reglas (lugares/material → EQUIPMENT; objetivo/capacidades/marcas → PROTOCOL; días/inicio/lugares por día → FREQUENCY/CALENDAR;
  prioridades → PRIORITIES), de modo que cambiar algo anterior deje `PLAN` pendiente de revisión.

## 2. Datos del borrador (`SetupWizardDraft`, todos con valor por defecto y `@Serializable`; el JSON viejo debe seguir leyéndose)
`trainingPlaces: Set<TrainingPlace>`, `goalProfile: TrainingGoalProfile?`, `weekStartDay: Int?` (1..7), `freshestDay: Int?`, `dayPlaces: Map<Int, TrainingPlace>`,
`capabilities: Map<CapabilitySkill, CapabilityLevel>`, `liftMarks: Map<LiftMark, Double>` (kg), `marksUnit: String = "kg"`, `weekLayoutOverrides: Map<String, Int>` (clave de sesión → día; lo usa S-B),
`adaptedSplitId: String?` (lo usa S-B), `planVariantSeed: Int = 0`.
Compatibilidad con el motor actual (que sigue leyendo los campos viejos): el reductor mantiene **derivados** `trainingEnvironment` (GYM→"gym", si no HOME o PUBLIC→"home"),
`equipment` (GYM→`SetupEquipment.GYM`), `daysPerWeek`, `selectedWeekdays`, `goal` (ver §3), `knowsTrainingMarks` (hay alguna marca) y `powerliftingProfile`
(squat/bench/deadlift desde `liftMarks`; la marca de press militar y las demás solo se guardan en `liftMarks`). `minutesPerSession` pasa a rango 20..180.
Migración (`SetupDraftCompatibility`): `trainingEnvironment` viejo → `trainingPlaces` (gym/machines→GYM; home/none→HOME) y la disponibilidad existente se conserva; `goal` viejo → `goalProfile`
(STRENGTH→POWERLIFTING, MUSCLE→BODYBUILDING, STRENGTH_MUSCLE→STRENGTH_MUSCLE, COMPLETE_ATHLETE→STRENGTH_CARDIO, HEALTH/MIXED→FUNCTIONAL_HEALTH / STRENGTH_CARDIO), sin marcar nada como confirmado.

## 3. Reductores y reglas puras (archivos nuevos en `domain/onboarding/`, con pruebas)
Ya existen (míos, en tu rama): `EntrenoContracts.kt` (enums) y `EquipmentSymbols.kt` (símbolos ↔ `EquipmentAvailability`, con `seedFor`, `reseed`, `toggle`, `availabilityOf`,
`selectedFrom`). **Revísalos, completa lo que falte y escribe sus pruebas** (`EquipmentSymbolsTest`). Añade:
- `TrainingGoalRequirements`: `isCompatible(profile, availability)` y `missingText(profile, availability): String?` («Necesita barra, rack y banco»). Reglas:
  POWERLIFTING = BARBELL ∧ RACK ∧ BENCH; POWERBUILDING = (BARBELL ∧ RACK ∧ BENCH) ∨ DUMBBELLS; BODYBUILDING = al menos uno de BARBELL, DUMBBELLS, MACHINES, CABLE, SMITH;
  CALISTHENICS = PULL_UP_BAR ∨ RINGS; WEIGHTLIFTING = BARBELL ∧ RACK; STRONGMAN = BARBELL ∧ (DUMBBELLS ∨ KETTLEBELL); ARMWRESTLING = al menos uno de DUMBBELLS, CABLE, BANDS, BARBELL, KETTLEBELL;
  los tres generales siempre compatibles (el generador se adapta a cualquier material, incluido solo el cuerpo).
- `GoalProfileMapping`: de `TrainingGoalProfile` a `SetupGoal` legacy (añade a `SetupGoal` los valores `FUNCTIONAL`, `CALISTHENICS`, `WEIGHTLIFTING`, `ARMWRESTLING`, `STRONGMAN` y arregla cada
  `when` exhaustivo — `planGoalProfileOf`, `inferredTrainingStyle`, `requiresCardio`, etc.; para los generales y las cuatro disciplinas nuevas el plan lo servirá S-B, así que aquí basta una asignación
  segura que no rompa los candidatos actuales), a `TrainingStyle` de calibración (POWERLIFTING/WEIGHTLIFTING→POWERLIFTER; BODYBUILDING/CALISTHENICS/ARMWRESTLING→BODYBUILDER; el resto→POWERBUILDER) y a
  `AthleteType` (STRENGTH_MUSCLE→ENTHUSIAST, STRENGTH_CARDIO→HYBRID, FUNCTIONAL_HEALTH→ENTHUSIAST, POWERBUILDING→POWERBUILDER, CALISTHENICS→CALISTHENICS, BODYBUILDING→BODYBUILDER,
  WEIGHTLIFTING→WEIGHTLIFTER, ARMWRESTLING→ENTHUSIAST, STRONGMAN→POWERLIFTER, POWERLIFTING→POWERLIFTER). **Cablea `AthleteType` al parche de ajustes de la activación** (`SetupSettingsPatch`,
  `buildSettingsPatch` en el VM) solo cuando el paso GOAL se respondió en este alta; comprueba qué campo de `Settings` lo guarda y que la activación lo persiste (prueba).
- `MarksContext.liftsFor(profile, experience, hasOlympicLifts = false): List<LiftMark>`: novato → vacío. STRENGTH_MUSCLE→[SQUAT, BENCH, DEADLIFT] (solo con barra); POWERLIFTING y POWERBUILDING→[SQUAT, BENCH, DEADLIFT];
  STRONGMAN→[DEADLIFT, SQUAT, OVERHEAD_PRESS]; WEIGHTLIFTING→[SQUAT] (+[SNATCH, CLEAN_AND_JERK] solo si `hasOlympicLifts`); el resto→vacío. Cuando la lista es vacía el paso TRAINING_MAX no está en la ruta.
- `MuscleSuggestions.forProfile(profile): Set<MuscleSymbol>` (POWERLIFTING→CHEST, GLUTES, QUADS, HAMSTRINGS, ABS; ARMWRESTLING→FOREARMS, BICEPS; BODYBUILDING→vacío (libre);
  POWERBUILDING→CHEST, BACK, QUADS, HAMSTRINGS; CALISTHENICS→BACK, CHEST, ABS, SHOULDERS; WEIGHTLIFTING→SHOULDERS, QUADS, GLUTES, TRAPS, ABS; STRONGMAN→BACK, TRAPS, ABS, QUADS, FOREARMS; generales→vacío)
  y `MuscleSymbols.canonical(symbol)` hacia los músculos canónicos del motor de orden (`ORDER_MUSCLE_OPTIONS` + `OrderPrioritiesContract`: CHEST→«Pectorales», BACK→«Dorsales», SHOULDERS→«Deltoides»,
  TRAPS→«Trapecio», ABS→«Abdomen»… y FOREARMS→«Antebrazos»: **comprueba** si el motor y el catálogo conocen «Antebrazos»; si no, añádelo a la lista canónica y haz que cuente de verdad en la bolsa de orden).
- `capabilityAsks(draft)` = CAPABILITIES entra en la ruta cuando el objetivo es general o CALISTHENICS **y** (novato, o el material es ligero: sin BARBELL∧RACK ni MACHINES) — el motor las usa para elegir la variante
  de flexión/dominada/fondo/pistola; `SetupStepDefinitions` fija qué habilidades se ofrecen según el material (PULL_UP solo con barra de dominadas; DIP con paralelas o banco).
Reductores en `SetupStepAnswers.kt` (todos puros, con pruebas en `SetupStepAnswersTest` y nuevos archivos):
- `EQUIPMENT`: alterna lugares (multi; ≥ 1), recalcula la semilla con `EquipmentSymbols.reseed`, escribe `trainingOptions.availability` con `availabilityOf`, deriva los campos legacy y **retira la confirmación de AVAILABILITY**
  si cambió el material. `AVAILABILITY`: alterna símbolos con `EquipmentSymbols.toggle`, escribe la disponibilidad; selección vacía equivale a solo peso corporal (válido). El subpanel de aparatos (Sí/No/No sé) **desaparece** del paso.
- `GOAL`: guarda `goalProfile`, deriva `goal`, el estilo de calibración y `AthleteType`; **valida** que el perfil específico sea compatible con el material (`TrainingGoalRequirements`) — un perfil incompatible no se puede confirmar;
  si cambia el material y el perfil elegido deja de ser compatible, el paso queda pendiente de revisión (nunca se borra la respuesta en silencio).
- `FRESH_DAY`: guarda `freshestDay` y, si la persona aún no tocó el inicio de semana, `weekStartDay = freshestDay`. `WEEKDAYS`: 1–7 días; sincroniza `daysPerWeek`; descarta `dayPlaces` de días que ya no están.
  El lugar por día solo existe con ≥ 2 lugares; por defecto el primero en orden `GYM, HOME, PUBLIC`. `SESSION_TIME`: entero 20..180, múltiplo de 5 (redondea), sin campo de texto libre en el reductor.
- `CAPABILITIES`, `PRIORITIES` (conjunto de hasta 5 `MuscleSymbol` → `priorityMuscles` y una bolsa de orden `orderPriorities` de 1 punto por músculo canónico; «omitir» = vacío y vale; sugerencias de `MuscleSuggestions` se
  **precargan** solo si la persona aún no tocó el paso y se rotulan como sugeridas, nunca se confirman solas), `TRAINING_MAX` (mapa de marcas en kg; `liftMarks` y derivados; sin marcas = válido).
- Derivados al confirmar pasos: novato → `VOLUME_TECHNIQUE` derivado (procedencia DERIVED); `autoregulationMode` fijo en `PROPOSE` (sugerir y confirmar) y `automaticConfirmed=false`; `trainingOptions.warmup` queda en `null` (el preset de hoy)
  hasta que llegue el paquete D3 de aproximación.

## 4. Definiciones, textos, resúmenes y revisión
- `SetupStepDefinitions`: títulos ≤ 44 y subtítulos ≤ 100 (`SetupStepCopyRulesTest`); nuevos `SetupControlKind` (`PLACES`, `EQUIPMENT_SYMBOLS`, `GOAL_PROFILES`, `FRESH_DAY`, `WEEK_CALENDAR`, `SESSION_DIAL`, `CAPABILITIES`, `MUSCLE_SYMBOLS`, `LIFT_MARKS`, `PLAN_REVEAL`, `WEEK_LAYOUT`).
  Copia (pásala por tu criterio de UX writing: breve, cálida, sin género gramatical): EQUIPMENT «¿Dónde entrenas?» / «Elige uno o varios lugares.»; AVAILABILITY «¿Con qué material entrenas?» / «Marca lo que tienes y quieres usar.»
  (en gimnasio todo lo habitual ya viene marcado); GOAL «¿Cuál es tu objetivo?» / «Elige un perfil general o una disciplina.»; FRESH_DAY «¿Qué día llegas con más energía?» / «Tu sesión más fuerte caerá ese día.»;
  WEEKDAYS «¿Qué días puedes entrenar?» / «Entre 1 y 7. El plan se adapta a tu semana.»; SESSION_TIME «¿Cuánto tiempo tienes por sesión?» / «Un rango: el plan se ajusta a ti.»; CAPABILITIES «¿Qué ejercicios ya te salen?» /
  «Así elegimos variantes a tu medida.»; PRIORITIES «¿Qué músculos quieres mejorar más?» / «Elige hasta 5. Puedes omitirlo.»; TRAINING_MAX «¿Conoces tus marcas?» / «Con una basta; sin marcas el plan sigue siendo válido.»;
  PLAN «Tu programa» (general) o «Elige tu programa» (disciplina) — el título cambia según el perfil (`wizardPageCopy`); WEEK_LAYOUT «Así queda tu semana» / «Mueve las sesiones a tu gusto.»
- `SetupStepSummaries` (etiqueta corta + valor de una línea) para cada paso nuevo o cambiado; `SetupReviewStep` (filas de entreno: lugares, material, objetivo, días y tiempo, programa) y `SetupWizardValidation.validateStep/validateAll`
  sin referencias a los pasos retirados; `trainingMilestoneRows` al día. Nada de código muerto: borra lo que ya nadie usa (UI de WARMUPS/AUTOREGULATION/TRAINING_REVIEW/SPLIT/TRAINING_MARKS y sus resúmenes) **dejando** las funciones puras que otros
  módulos o tests siguen usando (`compatibleSplitTemplates`, `splitDisplayName`, `warmupRecipesFromRaw`…) si tienen consumidores; si no los tienen, bórralas con sus pruebas.

## 5. Controles provisionales (archivos nuevos en `screens/onboarding/entreno/`, uno por paso)
`EntrenoPlacesStep.kt` (EQUIPMENT), `EntrenoMaterialStep.kt` (AVAILABILITY), `EntrenoGoalStep.kt`, `EntrenoFreshDayStep.kt`, `EntrenoWeekdaysStep.kt`, `EntrenoSessionTimeStep.kt`, `EntrenoCapabilitiesStep.kt`,
`EntrenoMusclesStep.kt` (PRIORITIES), `EntrenoMarksStep.kt` (TRAINING_MAX), `EntrenoPlanStep.kt` (PLAN: delega en el control actual de candidatos), `EntrenoWeekLayoutStep.kt` (solo lectura por ahora).
Cada uno es `@Composable internal fun EntrenoXxxStep(state: SetupWizardState, vm: SetupWizardViewModel)` y solo escribe por la API del VM (`setStepChoice(s)`, `toggleStepChoice`, `updateStep`, `setStepNumber`…). Provisionales = funcionales y sobrios (texto + chips/filas
simples), **sin pretender diseño final**; el nombre y la firma de cada archivo son el punto de enganche donde otros agentes sustituirán el cuerpo por los símbolos animados (`PlaceSymbolRow`, `EquipmentSymbolGrid`, `FreshDayRow`,
`WeekCalendar`, `SessionClockDial`, `MuscleSymbolGrid`, `LiftMarksPicker`, `CapabilitySymbols`). `SetupTrainingStepContent` solo delega. El `testTag` de cada paso activo sigue siendo `setup-step-<ID>`.
Añade, en `SetupWizardViewModel`, las funciones mínimas que hagan falta (`togglePlace`, `toggleEquipmentSymbol`, `setGoalProfile`, `setFreshDay`, `toggleWeekday`, `setWeekStart`, `setDayPlace`, `setSessionMinutes`, `setCapability`, `toggleMuscle`, `setLiftMark`, `setMarksUnit`) como envoltorios finos de `updateStep`
(una sola vía de escritura; la API vieja `setStepChoice` debe seguir funcionando con los valores estables de cada paso).

## 6. Pruebas (obligatorias)
Actualiza o borra las que describían el flujo viejo (hay ~35 archivos que citan SPLIT/AUTOREGULATION/WARMUPS/DAYS…; muchas solo necesitan que la ruta cambie) y añade: ruta por perfil/experiencia/material (novato sin técnica, objetivos con y sin cardio, marcas contextuales, capacidades),
reductores de cada paso, validación (≥ 1 lugar; perfil incompatible bloqueado; 1–7 días; 20–180 min), compatibilidad con borradores viejos (cursor en un paso retirado, `trainingEnvironment`/`goal` migrados), `EquipmentSymbols`, `TrainingGoalRequirements`, `MarksContext`, `MuscleSuggestions`,
escritura de `AthleteType` en la activación, y un recorrido completo del bloque hasta el hito. Corre por filtro `com.example.kpkn.screens.onboarding.*` y `com.example.kpkn.domain.onboarding.*` (+ `domain.training.*` si tocaste algo ahí). **Entrega con 0 fallos o, para cada fallo que no puedas arreglar en
3 iteraciones, un diagnóstico escrito** (no persigas pruebas de dispositivo: los `androidTest` solo deben compilar; ajusta los que cambien de firma).

## No hagas
No toques `design/entreno/*` (de U1–U3), ni `domain/training/generator/*` (D1), ni `domain/training/approach/*` (D3), ni el catálogo (`assets/`, `catalog/`). No reescribas `SimpleCyclePersonalizer`, `PlanCandidateEvaluator` ni
`PlanRepair*` (S-B decide qué se relaja); solo lo imprescindible para compilar. No cambies el comportamiento de nutrición, rings ni los hitos.
