# Wizard de alta: página larga integrada

Referencia de cómo se comporta y cómo se toca el wizard de alta (`screens/onboarding/`). El wizard ya no es una pantalla por paso: es **una sola página vertical** sobre una superficie negra continua. No hay tarjetas ni láminas por paso: cada paso es un tramo de la misma página, separado del siguiente por un filete y por aire.

## Qué ve la persona

- **Paso activo**: tramo desplegado con etiqueta («PASO 2 DE 5 · DATOS BÁSICOS»), título, subtítulo y control. Siempre esa anatomía y siempre los mismos tamaños, alineado a la izquierda.
- **Pasos ya confirmados**: se pliegan en una **fila de lista** (marca, etiqueta corta, valor de una línea y un filete). Tocarla edita ese paso (`vm.editStep`).
- **Paso siguiente**: solo **asoma** bajo el activo y la página sigue hasta el borde inferior (etiqueta y título tenues, control desenfocado y desvaneciéndose hacia abajo). Está inerte: no recibe toques ni lo anuncia TalkBack.
- **Check** (abajo): confirma y es lo único que genera el paso siguiente. La página se **desliza** hasta él (no hay página nueva) mientras el anterior se pliega.
- **Scroll**: se puede volver atrás a ver lo respondido, pero no adelantarse a lo que el check no ha generado ([`WizardScrollLock`]).
- **Cabecera**: atrás, progreso por bloques (un tramo por bloque del recorrido) y salir. No repite la pregunta. Es de cristal real: desenfoca lo que pasa por debajo.

## Piezas

| Pieza | Archivo | Qué hace |
|---|---|---|
| Host | `SetupWizardHost.kt` | Compone la página, decide el modo de cada página, lanza el deslizado y bloquea el scroll. |
| Página (modo + animación) | `design/WizardSection.kt` | `WizardPageItem` anima `focus` (asoma→activa) y `collapse` (desplegada→plegada); cuerpo del paso y `WizardSummaryRow`. |
| Cromo fijo | `design/WizardPageChrome.kt` | Cabecera de cristal, velo superior y botón de confirmar con su velo. |
| Vidrio | `design/WizardGlass.kt` | Estilo `haze` de la cabecera y del botón. |
| Geometría y bloqueo | `design/WizardPageMetrics.kt` | Fórmulas del deslizado y del límite de scroll, más `WizardScrollLock`. |
| Textos y posiciones | `SetupWizardSteps.kt` | Título/subtítulo por página, etiquetas, progreso por bloque, contenido de hitos. |
| Resúmenes | `SetupStepSummaries.kt` | Etiqueta corta y valor de una línea de cada paso confirmado. |
| Tokens | `design/WizardDesignTokens.kt` | Roles tipográficos, espaciado y colores de cristal. |

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
5. **Sin adornos**: nada de tarjetas por paso, resplandores de color, degradados de borde ni sombras de color. El color de bloque no tiñe la página; el progreso es blanco sobre gris.
6. **Marcas de prueba**: la sección activa lleva `setup-step-<ID>`, la fila-resumen `setup-summary-<ID>` y el check `setup-continue` (con `Role.Button` y estado real de habilitado).
7. **El deslizado sale de una fórmula cerrada** (`WizardPageMetrics.target`) porque todo lo anterior al paso activo son filas de alto fijo. Si una fila-resumen dejara de medir lo mismo, hay que medir posiciones en lugar de usar la fórmula.
8. **El resumen se calcula una vez**, al confirmar la página, y se guarda; recalcular todas las filas en cada pulsación sería caro.
9. Se compone solo lo que se ve: una página plegada no compone su paso.

## Cómo añadir un paso

1. Añade la definición en `SetupStepDefinitions` (título ≤ 44, subtítulo ≤ 100) y el control en el `SetupXStepContent` de su bloque (sin título).
2. Dale etiqueta y valor en `SetupStepSummaries.kt` (el `when` es exhaustivo: el build avisa si falta).
3. Si el paso fusiona dos (como alias+edad o altura+peso), registra la fusión en `wizardPresentationSteps`/`wizardPageOf` y pon su texto en `wizardPageCopy`.
