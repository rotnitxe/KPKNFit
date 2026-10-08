# Wizard de alta: página larga integrada

Referencia de cómo se comporta y cómo se toca el wizard de alta (`screens/onboarding/`). El wizard ya no es una pantalla por paso: es **una sola página vertical** sobre una superficie negra continua. No hay tarjetas ni láminas por paso: cada paso es un tramo de la misma página, separado del siguiente por un filete y por aire.

## Qué ve la persona

- **Paso activo**: tramo desplegado con etiqueta («PASO 2 DE 5 · DATOS BÁSICOS»), título, subtítulo y control. Siempre esa anatomía y siempre los mismos tamaños, alineado a la izquierda.
- **Pasos ya confirmados**: se pliegan en una **fila de lista** (marca, etiqueta corta, valor en hasta dos líneas y un filete). Tocarla edita ese paso (`vm.editStep`). El valor nunca acaba a media palabra: si no cabe en dos líneas se corta en la última palabra entera con «…» (`SummaryValue`), y las listas largas se acortan en el texto («Sentadilla, Press banca +1» en lugar de los pesos).
- **Paso siguiente**: solo **asoma** bajo el activo y la página sigue hasta el borde inferior (etiqueta y título tenues, control desenfocado y desvaneciéndose hacia abajo). Está inerte: no recibe toques ni lo anuncia TalkBack. Su **control se compone tras la animación de llegada** (`SlideMillis` + un respiro) y entra con un fundido corto; la etiqueta, el título y el subtítulo sí están desde el primer cuadro. Si la persona confirma antes, el paso ya es el activo y se compone en el acto.
- **Check** (abajo): confirma y es lo único que genera el paso siguiente. La página se **desliza** hasta él (no hay página nueva) mientras el anterior se pliega.
- **Scroll**: se puede volver atrás a ver lo respondido, pero no adelantarse a lo que el check no ha generado ([`WizardScrollLock`]).
- **El final del paso, sobre el botón**: al abrirse un paso cuyo final quedaría bajo el botón de confirmar y su velo (con la letra al 130 %, «Ver detalles» de PLAN), la página sube lo justo para que quede por encima (`WizardPageMetrics.openExtra`: nunca más de una fila-resumen, de modo que la pregunta sigue a la vista; nunca con el teclado abierto; y solo mientras la persona no haya movido la página). Se hace una vez en el anfitrión, no paso por paso, y se repite si el paso crece después de abrirse (PLAN se revela al terminar el barrido).
- **Cabecera**: atrás, progreso por bloques (un tramo por bloque del recorrido) y salir. No repite la pregunta: lleva la Torre de la marca, el bloque y cuánto llevas. Es de cristal real: desenfoca lo que pasa por debajo. El tramo de un bloque completo se pinta en verde de marca; el que se recorre, en tinta.
- **Al terminar un bloque** sale el overlay de «bloque completado» (ver abajo) encima de su última pregunta, que ya está confirmada; **al abrir un borrador sin empezar** sale el mismo overlay sin nada completado.

## Piezas

| Pieza | Archivo | Qué hace |
|---|---|---|
| Host | `SetupWizardHost.kt` | Compone la página, decide el modo de cada página, lanza el deslizado y bloquea el scroll. |
| Página (modo + animación) | `design/WizardSection.kt` | `WizardPageItem` anima `focus` (asoma→activa) y `collapse` (desplegada→plegada); cuerpo del paso y `WizardSummaryRow`. |
| Cromo fijo | `design/WizardPageChrome.kt` | Cabecera de cristal, velo superior y botón de confirmar con su velo. |
| Vidrio | `design/WizardGlass.kt` | Estilo `haze` de la cabecera y del botón. |
| Geometría y bloqueo | `design/WizardPageMetrics.kt` | Fórmulas del deslizado y del límite de scroll, más `WizardScrollLock`. |
| Desplazamiento a petición | `design/WizardPageScroll.kt` | `LocalWizardPageScroll`: deja que un control que arrastra algo hasta el borde visible (el tablero de la semana) desplace la página, con el mismo límite que la persona (`pageScrollStep`) y dentro de la franja que dejan la cabecera y el botón de confirmar. |
| Textos y posiciones | `SetupWizardSteps.kt` | Título/subtítulo por página, etiquetas, progreso por bloque y las etapas del overlay de hito (`milestoneStages`, `introStages`). |
| Overlay de hito y de arranque | `design/ModuleCompleteOverlay.kt` | Animación propia por bloque (`KpknModule`), fila de etapas con la guía y la variante `INTRO`. |
| Resúmenes | `SetupStepSummaries.kt` | Etiqueta corta y valor de una línea de cada paso confirmado. |
| Tokens | `design/WizardDesignTokens.kt` | Roles tipográficos, espaciado y colores de cristal. |

## Hitos entre bloques y pantalla de arranque

Un hito (`MILESTONE_*`) **ya no es una página**: es el overlay de la guía de marca (`ModuleCompleteOverlay`) sobre la última pregunta del bloque.

- **Qué muestra**: la animación del bloque (datos básicos, entreno, nutrición o rings), el título y una línea, y la **fila de etapas**: un riel con un nodo por bloque de la ruta (más la revisión final) y una guía luminosa que lo recorre de uno en uno desde el primero; los completos se encienden en verde con su check, el que acaba de cerrarse lanza una onda y la guía se detiene en el siguiente, señalado en tinta. Las etapas salen de la ruta efectiva del borrador (`milestoneStages`): un asistente solo de entreno no habla de Nutrición ni de Rings.
- **Pantalla de arranque** (`KpknModule.INTRO`): la misma composición **sin nada completado ni color de «completado»** (torre neutra, solo la primera etapa señalada). Sale mientras el cursor nunca haya salido de la primera pregunta (`visited.size <= 1`; los valores que traen los ajustes no cuentan) y se omite en pruebas con `showIntro = false`.
- **Página durante el hito**: `wizardCurrentPage` deja la página en la última pregunta del bloque, así volver atrás o terminar el overlay no la mueve y el deslizado al bloque siguiente ocurre **después**, al continuar. Continuar confirma el hito (`submitCurrentStep`); atrás (`goBack`) vuelve a la última pregunta.
- **Atrás no se detiene en un hito** (ni en la edad): desde la primera pregunta de un bloque vuelve a la última del anterior, sin repetir la celebración.
- **Robustez**: al pulsar, el overlay desvanece contenido y desenfoque antes de avisar y, si la acción no avanza, vuelve a mostrarse en lugar de quedar invisible. Sin desenfoque del sistema el fondo tapa casi todo (0,96). Con la escala de animaciones a 0 se ve el estado final.
- **Pruebas**: el overlay del hito es `setup-step-MILESTONE_*` y su botón `setup-milestone-continue`; la pantalla de arranque, `setup-intro` y `setup-intro-start`. El botón se habilita al terminar la animación.

## Paso de grasa corporal: figura y regla vertical

`BODY_FAT` es obligatorio (sin «omitir», sin campo de medición exacta) y se reparte en tres piezas:

| Pieza | Archivo | Qué hace |
|---|---|---|
| Conexión | `SetupBasicSteps.kt` (`SetupBodyFatControl`) | Une el borrador con el selector. `bodyFatRulerPercent()` da lo declarado, el dato de Ajustes o la figura de arranque (≈ 25 %); `withBodyFatRulerValue()` declara fuente, porcentaje entero y posición de la figura en una sola escritura. |
| Escenario y lectura | `design/WizardBodyFatPicker.kt` | Figura grande sin marco (`ContentScale.Fit`, 380 dp de alto en un teléfono de 390 dp de ancho), botones ♀/♂ de 36 dp en la esquina superior izquierda, la regla a la derecha y, debajo, el porcentaje grande con una frase. |
| Regla | `design/WizardBodyFatRuler.kt` | `Canvas` de 5 % (arriba) a 50 % (abajo): marca fina cada 1 %, intermedia cada 5 % y larga con número cada 10 %, con un cursor verde cuya punta mira a la figura. Tocar o arrastrar fija enteros con un háptico leve. |

- **Declarar es mover**: la posición de arranque no es una respuesta (la lectura sale al 50 % de opacidad y el check sigue bloqueado). El primer contacto con la regla, arrastrar o tocar, declara el valor y habilita el check sin pulsar nada más.
- La frase sale de `domain/nutrition/PhysiqueDescriptors.kt` (`bodyFatDescriptor`) y el fotograma, de `physiqueSliderPositionForBodyFat` (la inversa de `bodyFatForSliderPos`).
- La figura parte de lo que se eligió en el género (mujer, mujer trans o estrógenos → ♀; hombre, hombre trans o andrógenos → ♂; «equilibrio» y «No lo sé» no la mueven) y es solo una referencia visual que se puede cambiar con ♀/♂. Cambiar de figura solo cambia `physiqueModel`: nunca el sexo de cálculo, y con la grasa ya declarada la figura no se mueve sola (`withReferenceFigureFor`).
- Marca de prueba de la regla: `setup-bodyfat-ruler`.

## Reglas que no hay que romper

1. **Tipografía solo por roles** de `WizardTypography` (`eyebrow`, `stepTitle`, `stepSubtitle`, `controlLabel`, `controlValue`, `note`…). Mínimo 13 sp. Los pasos no fijan tamaños propios.
2. **El contenido de un paso no repite su título ni su subtítulo**: los pinta la sección. Un paso nuevo solo aporta su control.
3. **Títulos de pregunta ≤ 44 caracteres y subtítulos ≤ 100** (`SetupStepCopyRulesTest` lo exige).
4. **Desenfoque solo donde corresponde**: el `RenderEffect` del paso que asoma se aplica únicamente a su control, nunca a la etiqueta ni al título; `haze` solo bajo la cabecera, y el fondo va dentro de la fuente de `haze` para que el cristal sea opaco. Nada de `Modifier.blur` suelto sobre una página entera.
5. **Sin adornos**: nada de tarjetas por paso, resplandores de color, degradados de borde ni sombras de color. El color de bloque no tiñe la página; el progreso es tinta crema sobre gris y verde de marca cuando el bloque se completa. El texto y los controles usan la **tinta cálida de la marca** (`WizardColors.text` = #F2EEE6), no blanco puro.
6. **Marcas de prueba**: la sección activa lleva `setup-step-<ID>`, la fila-resumen `setup-summary-<ID>` y el check `setup-continue` (con `Role.Button` y estado real de habilitado).
7. **El deslizado sale de una fórmula cerrada** (`WizardPageMetrics.target`) porque todo lo anterior al paso activo son filas de alto fijo. El alto de la fila-resumen es función SOLO de la escala de letra (`WizardSpacing.summaryRowHeightFor`: etiqueta y dos líneas de valor, 72 dp con letra normal y 90 dp al 130 %), nunca del texto. Si una fila-resumen dejara de medir lo mismo, hay que medir posiciones en lugar de usar la fórmula.
8. **El resumen se calcula una vez**, al confirmar la página, y se guarda; recalcular todas las filas en cada pulsación sería caro.
9. Se compone solo lo que se ve: una página plegada no compone su paso, y la que asoma no compone su control hasta que termina la animación de llegada (el cuadro de la confirmación ya lleva el deslizado, el plegado de la fila y el enfoque del activo; componer además un control entero lo alargaba hasta 250–500 ms en el APK debug).

## Cómo añadir un paso

1. Añade la definición en `SetupStepDefinitions` (título ≤ 44, subtítulo ≤ 100) y el control en el `SetupXStepContent` de su bloque (sin título).
2. Dale etiqueta y valor en `SetupStepSummaries.kt` (el `when` es exhaustivo: el build avisa si falta).
3. Si el paso fusiona dos (como alias+edad o altura+peso), registra la fusión en `wizardPresentationSteps`/`wizardPageOf` y pon su texto en `wizardPageCopy`.

## Entreno v2: ruta, datos y controles

La ruta del bloque Entreno (`SetupStepGraph.nodes`) sale SOLO de datos del borrador, nunca de «respondido»:

`EXPERIENCE, EQUIPMENT, AVAILABILITY, GOAL, FRESH_DAY, WEEKDAYS, SESSION_TIME, [CARDIO_TYPE, CARDIO_TIME], [VOLUME_TECHNIQUE], VOLUME_CONSISTENCY, VOLUME_STRENGTH, VOLUME_MOBILITY, [CAPABILITIES], PRIORITIES, [TRAINING_MAX], PLAN, [WEEK_LAYOUT], MILESTONE_TRAINING`

| Rama | Entra cuando (`SetupStepContext`, derivado en `stepContext()`) |
|---|---|
| `CARDIO_*` | `goalIncludesCardio`: Fuerza y cardio, Funcional y saludable (y los objetivos antiguos Atleta completo / Fuerza + cardio). |
| `VOLUME_TECHNIQUE` | `asksTechnique`: la experiencia no es «Estoy empezando». Quien empieza recibe «1 · Aprendiendo» con procedencia `DERIVED` al confirmar la experiencia. |
| `CAPABILITIES` | `asksCapabilities` (`CapabilityRules.asks`): perfil general o Calistenia y, además, persona novata o material ligero. |
| `TRAINING_MAX` | `asksMarks`: `MarksContext.liftsFor` devuelve algún levantamiento (nunca para novatos). Halterofilia pregunta además el arranque y los dos tiempos, pero solo desde el nivel intermedio: las reservas del generador los programan (y leen esas marcas) a partir de ahí. |
| `WEEK_LAYOUT` | `hasWeekLayout`: el programa no se aplaza («lo armaré más adelante»). |

`ROUTE`, `STYLE`, `DAYS`, `SPLIT`, `AUTOREGULATION(_CONFIRM)`, `WARMUPS`, `TRAINING_MARKS` y `TRAINING_REVIEW` ya no son preguntas: el enum y sus definiciones (`legacyOnly`) siguen para leer borradores antiguos.

**Datos del borrador y derivados.** Cada paso escribe un dato real (`trainingPlaces`, `trainingOptions.availability`, `goalProfile`, `freshestDay`/`weekStartDay`, `selectedWeekdays`, `dayPlaces`, `minutesPerSession`, `capabilities`, `priorityMuscles`/`orderPriorities`, `liftMarks`) y el reductor mantiene los campos que el motor actual todavía lee (`trainingEnvironment`, `equipment`, `goal`, `daysPerWeek`, `knowsTrainingMarks`, `powerliftingProfile`). Las selecciones de estos pasos se leen de los datos (no se guardan aparte en `stepSelections`).

**Un archivo por control.** Cada paso es `@Composable internal fun EntrenoXxxStep(state, vm)` en `screens/onboarding/entreno/` y solo escribe por el ViewModel. Ya no quedan controles provisionales: cada archivo envuelve el control definitivo de `design/entreno/` (qué escribe cada uno y sus detalles, en `WIZARD_ENTRENO_V2.md` §2). Las notas de una línea bajo un control las pinta `EntrenoStepNote` (sin caja, sin hueco si no hay texto), y las escrituras que se disparan en cada muesca (el dial del tiempo y la regla de las marcas) se agrupan: una sola cuando el dedo se detiene, porque cada escritura relanza el barrido de programas.

| Paso | Archivo `entreno/` | VM | Reductor puro (`SetupStepAnswers.kt`) |
|---|---|---|---|
| `EQUIPMENT` | `EntrenoPlacesStep` | `togglePlace` | `withPlaces`, `withPlaceToggled` |
| `AVAILABILITY` | `EntrenoMaterialStep` | `toggleEquipmentSymbol` | `withMaterial`, `withMaterialToggled` |
| `GOAL` | `EntrenoGoalStep` | `setGoalProfile` | `withGoalProfile` |
| `FRESH_DAY` | `EntrenoFreshDayStep` | `setFreshDay` | `withFreshestDay` |
| `WEEKDAYS` | `EntrenoWeekdaysStep` | `toggleWeekday`, `setWeekStart`, `setDayPlace` | `withWeekdays`, `withWeekStart`, `withDayPlace` |
| `SESSION_TIME` | `EntrenoSessionTimeStep` | `setSessionMinutes` | `withSessionMinutes` (20..180, de 5 en 5) |
| `CAPABILITIES` | `EntrenoCapabilitiesStep` | `setCapability` | `withCapability` |
| `PRIORITIES` | `EntrenoMusclesStep` | `toggleMuscle`, `clearMuscles` | `withMuscles`, `withMuscleToggled` |
| `TRAINING_MAX` | `EntrenoMarksStep` | `setLiftMark`, `setMarksUnit` | `withLiftMark`, `withMarksUnit` |
| `PLAN` | `EntrenoPlanStep` | `selectPlan`, `anotherPlanVersion`, `deferProgramUntilLater`, `setProgramRoute(CUSTOMIZABLE)` (volver de un aplazado) | — (lee `planSweep` y `planReveals`; `entreno/EntrenoPlanModels.kt` los traduce a los modelos de U4) |
| `WEEK_LAYOUT` | `EntrenoWeekLayoutStep` (`WeekLayoutBoard` de U5) | `moveSession` (el tablero lo ve al instante), `adaptToSplit` (tras la confirmación de COPY), `resetWeekLayout` | `applyLayout` (`SetupWeekLayouts.kt`; lee `weekLayout`) |

Reglas de dominio que viven fuera de la UI (`domain/onboarding/`): `EquipmentSymbols` (símbolos de material ↔ disponibilidad del motor, ida y vuelta exacta), `TrainingGoalRequirements` (qué material pide cada disciplina; un perfil incompatible se escribe pero no se puede confirmar), `MarksContext`, `CapabilityRules`, `MuscleSymbols`/`MuscleSuggestions` (las sugerencias solo se precargan si la persona no tocó el paso) y `EntrenoStepValues` (valores estables y alias antiguos). `GoalProfileMapping` (en `screens/onboarding/`) traduce el perfil al objetivo del motor, al estilo de calibración y al tipo de atleta.

**Activación.** El perfil de objetivo escribe `Settings.athleteType` por `SetupSettingsPatch.athleteType`, solo si el paso GOAL se respondió en este alta (`athleteTypeToPersist`).

**Programa y semana (PLAN, WEEK_LAYOUT).** El barrido arma los programas según el perfil (`GeneratedPlans.sourcesFor`): los tres generales reciben solo su programa «a medida» del generador de rutinas; Powerlifting, Powerbuilding y Culturismo, el «a medida» al frente y detrás los propios y de autor del planificador; Calistenia, Armwrestling, Strongman y Halterofilia, solo su «a medida» rotulado «versión inicial». Los programas «a medida» son entradas NATIVE sin listar del catálogo (`generated:<perfil>`, `PersonalizedPlanCatalog.GENERATED_IDS`), así que el nombre, el modo, la hoja del plan y el detalle del programa los tratan como a los propios. El pedido del generador sale solo del borrador (`routineRequest`: días desde el inicio de semana, día con más energía, minutos, nivel y calibraciones, material por lugar con `PlaceMaterial`, músculos, capacidades, cardio, marcas y `planVariantSeed`).

- Estado publicado: `planSweep` (cargando / listo / fallido con el texto de COPY y «Reintentar») y `planReveals` (portada, detalle, semana tipo, razones y notas de cada programa viable, sacados del programa ya preparado). Con el paso activo, cada barrido abre `PlanPreparingOverlay` (variante general o de disciplina) y la página se revela cuando el overlay avisa: el perfil general enseña su portada «a medida» con «Tu programa está listo», sus razones y «Otra versión»; las disciplinas, `PlanCarousel`; «Ver detalles» abre `PlanDetailOverlay` con el programa real. Un resultado ya revelado no repite el overlay al girar la pantalla (`revealKeyOf`).
- Los planes de autor caben con un 15 % más de tiempo del pedido (`timeBudgetWithTolerance`, con la nota «~N min por sesión») y los propios se arman con `min(minutos, 100)`. Los minutos de la revisión final se miden sobre el programa YA armado (semana, aproximación y movilidad incluidas) con `SessionDurationEstimator` (`longestSessionMinutes`) y una única regla de «¿cabe?» (`SessionTimeFit`): ±1 min para los programas «a medida» (que, si pasan, solo llevan la nota «~N min por sesión: un poco más de los M que pediste.») y el 15 % para los de autor (que, por encima, bloquean la activación). El estimador suma un calentamiento fijo de 180 s a toda sesión de resistencia, exactamente 3 min más cuando el programa ya trae aproximación y movilidad explícitas.
- La semana armada vive en `weekLayoutOverrides` (sesión → día) y `adaptedSplitId`; un único punto, `applyLayout(draft, program)`, la aplica al final de la vista previa (redistribuidor de repartos y `WeekAssignment`), y la activación guarda ese mismo programa. Cambiar respuestas, de plan o de versión la vacía y deja WEEK_LAYOUT pendiente de revisar (`withInvalidatedWeekLayout`: la respuesta se conserva, el bloque deja de contarse como completo; «Restablecer» solo la vacía). Con dos o más lugares el mismo punto contrasta cada sesión con el material del lugar de su día (`SessionPlaceFit`, que usa el filtro único `ConfigurationEquipmentFilter`): si cabe, la sesión toma ese lugar sin ruido; si no, conserva su lugar y el tablero y la revisión final lo avisan de forma persistente (`SetupWeekLayout.placeConflicts`; se deshace moviendo otra vez o con «Restablecer»). En pantalla, `WeekLayoutBoard` recibe esa asignación (día → sesión) y avisa de cada movimiento; «Adaptar mi programa a este reparto» pide antes la confirmación de COPY (con el aviso de reparto de autor en los planes con receta) y, sin repartos que ofrecer (sesiones en varios lugares), «Restablecer» lo pone el paso. Los avisos de PLAN y WEEK_LAYOUT van sin caja (`EntrenoPlanNotice`) y sus acciones de texto («Otra versión», «Lo haré más adelante», «Reintentar», «Restablecer») salen de `EntrenoTextAction`, a ras del texto del paso.
- «Configurar este plan» desde la biblioteca: la preselección prefija el perfil que sirve al plan. Si ese perfil es general y solo ofrece su programa «a medida» (Atleta completo → Fuerza y cardio), el plan de la biblioteca no «cae» con un aviso de error: el aviso es informativo («Este programa de la biblioteca ahora se arma a medida en el asistente.») y deja «Elegir el programa a medida» a un toque (`SetupDroppedSelection.tailoredId`). Un plan de autor nunca recibe esta sustitución.
- Revisado en el teléfono real (360 dp, 130 % de letra y 320 dp): el desenfoque del sistema puede estar desactivado (ajuste «reducir transparencia y desenfoque»), así que el velo de reserva de `PlanPreparingOverlay` es opaco; los títulos de las portadas (`FittedDisplayText`) bajan de tamaño hasta caber también en sus líneas; los títulos de varias palabras de una ficha del tablero se parten entre palabras (solo los de una palabra usan la holgura lateral) y los nombres de reparto admiten tres líneas. Con las métricas reales de Syne caben enteros los 65 títulos de ficha y las palabras de las portadas a 360 dp (100 y 130 % de letra); a 320 dp con 130 %, tres títulos y «Recuperación» bajan a 11–12 sp (`fitTitle`). El título entero sigue en la descripción accesible y en el detalle del programa.

**Borradores antiguos** (`SetupDraftCompatibility.migrateEntrenoV2`, idempotente y sin confirmar nada): el entorno se lee como lugar, el objetivo como perfil (Salud y Fuerza + cardio quedan marcados para revisar), `powerliftingProfile` pasa a `liftMarks`, `daysPerWeek` es el número de días elegidos y un cursor sobre un paso retirado vuelve al primer paso pendiente de la ruta nueva (`SetupStepGraph.REVISION` = 4).

## Plan de alimentación (`NUTRITION_RESULT`)

El resultado de nutrición no es una lista de referencias: es un **panel visual** que se afina en vivo (título del paso: «Tu plan de alimentación»). Anillos con las kcal en grande, franja de días (solo con reparto variable), avisos, control de ritmo y un deslizador por macro. Sin tarjetas: secciones separadas por filetes.

| Pieza | Archivo | Qué hace |
|---|---|---|
| Lógica pura | `domain/nutrition/NutritionPlanTuning.kt` | `PlanTuning`: macros ↔ kcal (Atwater 4/4/9), ritmo ↔ kcal (EER ± ajuste, 7700 kcal/kg), límites de los deslizadores, zonas de ritmo, avisos y `hardStop`. Sin textos. |
| Panel | `design/WizardNutritionPlan.kt` | `WizardNutritionPlanPanel(model, callbacks)`: sin estado salvo animación. Lo componen `WizardMacroRings`, `WizardMacroSlider`, `WizardPaceGauge`, `WizardPlanWarnings` (`WizardPlanWarningChip`) y `WizardDayStrip`. |
| Formato y textos | `design/WizardNutritionFormat.kt` | Cifras en español («2.300», «−0,45») y textos cortos de avisos y zonas. |
| Estado en vivo | `design/WizardNutritionLive.kt` | Plan «en vivo» frente al confirmado: ni un arrastre ni una escritura sin confirmar se pisan. |
| Cableado | `SetupNutritionSteps.kt` (bloque `NUTRITION_RESULT`) | Lee la preparación real, escribe el borrador y cierra «Continuar» con `nutritionResultGate`. |

Reglas que no hay que romper:

1. **Lo que se ve es lo que se activa.** El panel trabaja sobre un plan en vivo para que los anillos respondan al instante, pero **solo al soltar** escribe en el borrador los cuatro números manuales (`manualCalorieTargetText`, `manualProteinText`, `manualCarbsText`, `manualFatText`). El motor ([`NutritionPlanPreparation`]) los traduce a una base manual y devuelve exactamente esos números; el activar re-prepara desde el borrador. Si el plan vuelve a ser el que el motor recomienda solo, los cuatro campos se **vacían** y el plan sigue siendo automático.
2. **El ritmo mostrado siempre sale de las kcal reales frente al EER** (`weeklyChangeFor`); nunca se guarda aparte. Mover un macro mueve el ritmo y mover el ritmo reescala los tres macros (`scaleMacrosToCalories`).
3. **El motor no lee `pacePreset`**: `preparationInputOf` no lo pasa y solo «Medio» coincide con su ritmo por defecto. Al abrir el resultado en automático, si el ritmo elegido en el paso anterior difiere, el panel **siembra** el borrador con el plan de ese ritmo (`presetSeedValues`) para que lo visible y lo activado coincidan.
4. **Umbrales de aviso = `buildNutritionRiskFlags`**: calorías < 1500/1200 (mujer 1200/1000), pérdida > 1,0 y > 1,5 kg/sem, ganancia > 0,5 y > 0,75 kg/sem; proteína < 1,2 g/kg en déficit y grasas < 20 % de la energía. Sexo desconocido: los umbrales estrictos. Hay `hardStop` con calorías duras o pérdida extrema (una ganancia extrema avisa pero no detiene, como en los avisos de riesgo).
5. **Textos**: un aviso es un icono y como mucho seis palabras. Nada de párrafos ni de enlaces «Editar …»: las filas-resumen de los pasos anteriores ya permiten volver.
6. **Marcas de prueba**: `setup-nutrition-rings`, `-protein`, `-carbs`, `-fat`, `-pace` (y `-pace-slider`), `-warning`, `-reset` y `-day-<n>`.
7. **Anillos de 260 dp como mucho** (más estrechos en pantallas pequeñas). La letra de Syne es ancha: el número central parte de 44 sp y se achica lo justo para caber en el hueco de los anillos (≈ 30 sp con cuatro cifras), medido con las cifras más anchas para que no se salga mientras cuenta (`macroRingsHeroScale`). Si se tocan el tamaño o el trazo, hay que recalcular ese hueco.

Vista previa de diseño (solo debug, sin recorrer el alta): `adb shell am start -n com.example.kpkn/com.example.kpkn.debug.NutritionPlanPreviewActivity --es scenario deficit_medio` (`deficit_extremo`, `ritmo_agresivo`, `superavit`, `mantenimiento`, `variable`, `propios_sin_eer`).

## Bienvenida con escenas (antes del wizard)

`SetupWelcomeScreen` es un carrusel de tres páginas (Entreno · Nutrición · Recuperación): un teléfono con la app simulada que se reproduce sola en bucle y, debajo, un mensaje breve. Mismo contrato de siempre: `SetupWelcomeScreen(onStart, actionLabel = "Comenzar", secondaryLabel, onSecondary, onDetails)`.

| Pieza | Archivo (`screens/onboarding/`) | Qué hace |
|---|---|---|
| Anfitrión con estado | `SetupWelcomeScreen.kt` | Levanta el pager y el reloj; lee «reducir movimiento» y el ciclo de vida. |
| Estructura | `welcome/WelcomeShell.kt` | Logo, carrusel, texto, indicador y botón; reparte el alto (`welcomePhoneHeight`: teléfono ≈ 60 % del alto útil, cede si abajo no cabe). |
| Teléfono | `welcome/WelcomeShellPhone.kt` | Chasis y escala uniforme del lienzo de 300 × 620 dp al interior de la pantalla; el teléfono es UNA entidad semántica. |
| Reloj | `welcome/WelcomeShellClock.kt` | `sceneTimeAt`, cuadro representativo, fundido opcional del bucle (≈ 0,5 s) y estado `WelcomeSceneClock`; corre solo en la página asentada y con la app en primer plano. |
| Textos | `welcome/WelcomeShellCopy.kt` | `WelcomePages`: etiqueta, acento, título (≤ 40), mensaje (≤ 130), descripción para TalkBack y periodo. |
| Escenas | `welcome/Welcome{Entreno,Nutricion,Rings}Scene.kt` | Funciones puras de `t` (contrato en `WelcomeScene.kt`). |

- Las tres escenas cierran solas (un velo del color de la pantalla en cada cambio de ciclo, con la barra de estado a la vista), así que el marco no las vuelve a fundir: `WelcomePageCopy.shellFadesLoop = false`. Una escena nueva que no cierre sola lo pone en `true`.
- El reloj usa `withInfiniteAnimationFrameNanos`: las pruebas de Compose cancelan el bucle y `waitForIdle()` sigue volviendo. Sin reloj (pruebas, app en segundo plano, movimiento reducido) cada escena muestra su cuadro representativo, `periodo × 0,7`.
- El acento de cada apartado (músculo, ok, columna) solo tiñe la etiqueta sobre el título.
- Etiquetas de prueba: `welcome-phone-<id>`, `welcome-text-<id>`, `welcome-cta`; los segmentos se anuncian «Vista N de 3».
- Para añadir una página: una entrada en `WelcomePages`, su escena en `WelcomeSceneOf` y su periodo; el carrusel y el indicador salen de la lista.
