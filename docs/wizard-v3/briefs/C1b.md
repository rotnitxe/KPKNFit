# C1b · UI de «Tus tres básicos / variantes» y de las marcas con repeticiones (punto 10, segunda parte)

Worktree indicado en tu encargo, rama `v3/c1b` (sale de `feat/wizard-v3` con C1 y B3a ya integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, del plan «Punto 10» y el brief `docs/wizard-v3/briefs/C1.md` (los datos, setters y textos ya existen:
aquí solo va la interfaz). C1c hace los otros cuatro controles y reutiliza la regla de conteo que creas aquí.

## Lo que ya existe (léelo entero antes de tocar)
- Enganche de cada paso: `screens/onboarding/SetupTrainingSteps.kt:53-78` (`when (step)`; cada paso de Entreno tiene su archivo
  `screens/onboarding/entreno/Entreno<Paso>Step.kt` con la firma `(state, vm)`).
- Marcas: `entreno/EntrenoMarksStep.kt` (escritura con espera de 220 ms) y `design/entreno/LiftMarksPicker.kt` (fila plegada
  `LiftRow` ~222, cuerpo desplegado `ExpandedMark` ~308, regla `MarkRuler` ~409, conmutador de texto `UnitSwitch` ~183, símbolo
  `LiftSymbol` ~621). `MarksMath` en `design/entreno/MarksMath.kt`; 1RM híbrido en `domain/calculations/Calculations.kt:60-71`.
- De C1 (en tu rama): `draft.liftMarkReps`, `draft.liftVariants`, `LiftVariants.optionsFor/defaultFor/labelOf/liftsFor`,
  setters `vm.setLiftMarkReps(lift, reps)` y `vm.setLiftVariant(lift, configId)` (busca los nombres exactos con Grep; si difieren, usa
  los reales). `SetupStepId.TRAINING_VARIANTS` ya está en la ruta y sin control (`else -> Unit`).

## 1. `TRAINING_VARIANTS` · archivo nuevo `entreno/EntrenoVariantsStep.kt` (+ rama en `SetupTrainingSteps.kt`)
- Una fila por levantamiento de `LiftVariants.liftsFor(goal)`, separadas por el filete de 1 dp (`WizardColors.divider`), sin cajas:
  - A la izquierda, la figura del levantamiento de 72 × 72 dp (reutiliza `LiftSymbol`; si es `private`, hazla `internal` sin cambiar
    su dibujo). Solo corre la fila tocada por última vez (un reloj compartido, `rememberFigClock`); con movimiento reducido, estática.
  - A la derecha, el nombre (`WizardTypography.controlLabel`, tinta) y debajo un **selector de palabras**: las opciones de
    `optionsFor` separadas por «  ·  », como `UnitSwitch` (la elegida en `WizardColors.text` y subrayada, el resto en `textFaint`).
    Cada palabra es un objetivo de 48 dp con `Role.RadioButton`, `selected` y `contentDescription` «Sentadilla: Barra baja».
    Si no caben en una línea, `FlowRow` (sin partir una palabra).
  - Elegida = la guardada o, si no hay, la de siempre (`defaultFor`), que se ve elegida pero en tinta al 70 % hasta que se toque
    (es una sugerencia, no una respuesta; igual que la regla de marcas sin declarar).
- Escribe SOLO con `vm.setLiftVariant`. Tocar la ya elegida no hace nada.
- Nota bajo la lista (`EntrenoStepNote`): PL «Puedes cambiarlas cuando quieras desde tu programa.»; PB «Las usamos como ejercicios
  principales de tus sesiones.»
- Marcas de prueba: `setup-variant-<LIFT>` (fila) y `setup-variant-<LIFT>-<configId>` (palabra).

## 2. Marcas con repeticiones (`LiftMarksPicker.kt` + `EntrenoMarksStep.kt`)
- En `ExpandedMark`, encima del valor grande: conmutador de texto **«1RM  ·  Mi mejor serie»** (mismo estilo que `UnitSwitch`,
  `Role.Switch`, `stateDescription` «1RM» / «Mi mejor serie», 48 dp). Arranca en «Mi mejor serie» si hay reps > 1 guardadas.
- Con «Mi mejor serie»: debajo de la regla de kg aparece (expandVertically 220 ms; nada con movimiento reducido) una **regla de
  repeticiones** de 2 a 10 y la línea «≈ 1RM estimado: 142,5 kg» (`WizardTypography.note`, `textMuted`; en la unidad elegida, con
  el redondeo de `MarksMath`). El valor grande pasa a «100 kg × 5».
- Volver a «1RM» escribe reps `null` (el kg se conserva).
- Fila plegada: «100 kg × 5» con PR, «100 kg» con 1RM, «No la sé» sin marca. En Powerbuilding, el nombre lleva la variante:
  «Sentadilla · Frontal» (`LiftVariants.labelOf`).
- Las reps se escriben con la misma espera y el mismo «escribir al irse» que los kg (`EntrenoMarksStep`): un mapa `pendingReps` igual
  que `pending`. Sin escrituras por cuadro.
- La nota final cambia a «Con una serie de 2 a 10 repeticiones estimamos tu 1RM.» (el subtítulo del paso ya dice lo demás).
- `LiftMarksPicker` gana parámetros con valor por defecto (`repsByLift: Map<LiftMark, Int> = emptyMap()`,
  `onReps: (LiftMark, Int?) -> Unit = { _, _ -> }`, `variantLabels: Map<LiftMark, String> = emptyMap()`): quien no los pasa ve lo de hoy.

## 3. Regla de conteo compartida · archivo nuevo `design/entreno/CountRuler.kt`
- `@Composable internal fun CountRuler(range: IntRange, value: Int?, onValue: (Int) -> Unit, unitLabel: String, modifier,
  tag: String)`: regla horizontal de enteros con el lenguaje visual de `MarkRuler` (marcas, número cada 5, cursor verde
  `WizardColors.ruleCursor`, bordes que se funden), 28 dp por unidad, ajuste al entero al soltar, háptico por escalón (como
  `MarkRuler`), sin valor = cursor atenuado y la posición de arranque no es respuesta (el primer contacto declara).
- Semántica: `progressBarRangeInfo` + `setProgress` y acciones «Subir uno»/«Bajar uno» como la regla de kg; `contentDescription`
  «Repeticiones: 5».
- La usará C1c (dominadas, flexiones y fondos de 0 a 30): mantenla genérica.

## Archivos permitidos
`screens/onboarding/SetupTrainingSteps.kt` (solo la rama `TRAINING_VARIANTS`), nuevos `entreno/EntrenoVariantsStep.kt` y
`design/entreno/CountRuler.kt`, `entreno/EntrenoMarksStep.kt`, `design/entreno/LiftMarksPicker.kt` (sin tocar el dibujo del banco, que es
de B3a), y pruebas. Nada de dominio ni del ViewModel: si te falta un setter, descríbelo en el informe.

## Pruebas
- `LiftMarksPickerUiTest` (amplía): el conmutador muestra y oculta la regla de reps; elegir 5 llama `onReps(lift, 5)`; volver a 1RM
  llama `onReps(lift, null)`; la fila plegada dice «100 kg × 5»; la línea del 1RM estimado coincide con `calculateHybrid1RM`.
- Nueva `EntrenoVariantsStepTest` (Compose, como `EntrenoStepsSmokeTest`): PL muestra 2 filas, PB 4; la de siempre sale elegida;
  tocar «Sumo» llama a `setLiftVariant`; sin barra no se ofrece «Frontal».
- Nueva `CountRulerTest`: arrastre y acciones de accesibilidad dan enteros dentro del rango.
- Hoja de render (`KPKN_RENDER_DIR=C:/kw/shots/v3/c1b`): el paso de variantes de PB y una marca desplegada con «Mi mejor serie».
- Filtro: `*LiftMarksPicker*`, `*EntrenoVariantsStep*`, `*CountRuler*`, `*EntrenoStepsSmoke*`, `*EntrenoAccessibility*`. Antes,
  `:app:compileDebugKotlin`.
