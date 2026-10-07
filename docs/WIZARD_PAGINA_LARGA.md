# Wizard de alta: página larga integrada

Referencia de cómo se comporta y cómo se toca el wizard de alta (`screens/onboarding/`). El wizard ya no es una pantalla por paso: es **una sola página vertical** sobre una superficie negra continua. No hay tarjetas ni láminas por paso: cada paso es un tramo de la misma página, separado del siguiente por un filete y por aire.

## Qué ve la persona

- **Paso activo**: tramo desplegado con etiqueta («PASO 2 DE 5 · DATOS BÁSICOS»), título, subtítulo y control. Siempre esa anatomía y siempre los mismos tamaños, alineado a la izquierda.
- **Pasos ya confirmados**: se pliegan en una **fila de lista** (marca, etiqueta corta, valor de una línea y un filete). Tocarla edita ese paso (`vm.editStep`).
- **Paso siguiente**: solo **asoma** bajo el activo y la página sigue hasta el borde inferior (etiqueta y título tenues, control desenfocado y desvaneciéndose hacia abajo). Está inerte: no recibe toques ni lo anuncia TalkBack.
- **Check** (abajo): confirma y es lo único que genera el paso siguiente. La página se **desliza** hasta él (no hay página nueva) mientras el anterior se pliega.
- **Scroll**: se puede volver atrás a ver lo respondido, pero no adelantarse a lo que el check no ha generado ([`WizardScrollLock`]).
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
- Cambiar de figura solo cambia `physiqueModel`: nunca el sexo de cálculo.
- Marca de prueba de la regla: `setup-bodyfat-ruler`.

## Reglas que no hay que romper

1. **Tipografía solo por roles** de `WizardTypography` (`eyebrow`, `stepTitle`, `stepSubtitle`, `controlLabel`, `controlValue`, `note`…). Mínimo 13 sp. Los pasos no fijan tamaños propios.
2. **El contenido de un paso no repite su título ni su subtítulo**: los pinta la sección. Un paso nuevo solo aporta su control.
3. **Títulos de pregunta ≤ 44 caracteres y subtítulos ≤ 100** (`SetupStepCopyRulesTest` lo exige).
4. **Desenfoque solo donde corresponde**: el `RenderEffect` del paso que asoma se aplica únicamente a su control, nunca a la etiqueta ni al título; `haze` solo bajo la cabecera, y el fondo va dentro de la fuente de `haze` para que el cristal sea opaco. Nada de `Modifier.blur` suelto sobre una página entera.
5. **Sin adornos**: nada de tarjetas por paso, resplandores de color, degradados de borde ni sombras de color. El color de bloque no tiñe la página; el progreso es tinta crema sobre gris y verde de marca cuando el bloque se completa. El texto y los controles usan la **tinta cálida de la marca** (`WizardColors.text` = #F2EEE6), no blanco puro.
6. **Marcas de prueba**: la sección activa lleva `setup-step-<ID>`, la fila-resumen `setup-summary-<ID>` y el check `setup-continue` (con `Role.Button` y estado real de habilitado).
7. **El deslizado sale de una fórmula cerrada** (`WizardPageMetrics.target`) porque todo lo anterior al paso activo son filas de alto fijo. Si una fila-resumen dejara de medir lo mismo, hay que medir posiciones en lugar de usar la fórmula.
8. **El resumen se calcula una vez**, al confirmar la página, y se guarda; recalcular todas las filas en cada pulsación sería caro.
9. Se compone solo lo que se ve: una página plegada no compone su paso.

## Cómo añadir un paso

1. Añade la definición en `SetupStepDefinitions` (título ≤ 44, subtítulo ≤ 100) y el control en el `SetupXStepContent` de su bloque (sin título).
2. Dale etiqueta y valor en `SetupStepSummaries.kt` (el `when` es exhaustivo: el build avisa si falta).
3. Si el paso fusiona dos (como alias+edad o altura+peso), registra la fusión en `wizardPresentationSteps`/`wizardPageOf` y pon su texto en `wizardPageCopy`.

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
