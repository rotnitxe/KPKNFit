# Wizard de alta: página larga de cristal

Referencia de cómo se comporta y cómo se toca el wizard de alta (`screens/onboarding/`). El wizard ya no es una pantalla por paso: es **una sola página vertical** con secciones de cristal.

## Qué ve la persona

- **Paso activo**: sección desplegada con etiqueta («PASO 2 DE 5 · DATOS BÁSICOS»), título, subtítulo y control. Siempre esa anatomía y siempre los mismos tamaños.
- **Pasos ya confirmados**: se pliegan en una **fila-resumen** (etiqueta corta + valor de una línea). Tocarla edita ese paso (`vm.editStep`).
- **Paso siguiente**: solo **asoma** bajo el activo (etiqueta y título tenues, control desenfocado y desvaneciéndose). Está inerte: no recibe toques ni lo anuncia TalkBack.
- **Check** (abajo): confirma y es lo único que genera el paso siguiente. La página se **desliza** hasta él (no hay página nueva) mientras el anterior se pliega.
- **Scroll**: se puede volver atrás a ver lo respondido, pero no adelantarse a lo que el check no ha generado ([`WizardScrollLock`]).
- **Cabecera**: atrás, progreso por bloques (un tramo por bloque del recorrido) y salir. No repite la pregunta.

## Piezas

| Pieza | Archivo | Qué hace |
|---|---|---|
| Host | `SetupWizardHost.kt` | Compone la página, decide el modo de cada página, lanza el deslizado y bloquea el scroll. |
| Página (modo + animación) | `design/WizardSection.kt` | `WizardPageItem` anima `focus` (asoma→activa) y `collapse` (desplegada→plegada); `WizardSectionCard` y `WizardSummaryRow`. |
| Cromo fijo | `design/WizardPageChrome.kt` | Cabecera de cristal, velo superior y botón de confirmar con su velo. |
| Vidrio y fondo | `design/WizardGlass.kt` | `Modifier.wizardGlass`, estilo `haze` y fondo ambiental. |
| Geometría y bloqueo | `design/WizardPageMetrics.kt` | Fórmulas del deslizado y del límite de scroll, más `WizardScrollLock`. |
| Textos y posiciones | `SetupWizardSteps.kt` | Título/subtítulo por página, etiquetas, progreso por bloque, contenido de hitos. |
| Resúmenes | `SetupStepSummaries.kt` | Etiqueta corta y valor de una línea de cada paso confirmado. |
| Tokens | `design/WizardDesignTokens.kt` | Roles tipográficos, espaciado y colores de cristal. |

## Reglas que no hay que romper

1. **Tipografía solo por roles** de `WizardTypography` (`eyebrow`, `stepTitle`, `stepSubtitle`, `controlLabel`, `controlValue`, `note`…). Mínimo 13 sp. Los pasos no fijan tamaños propios.
2. **El contenido de un paso no repite su título ni su subtítulo**: los pinta la sección. Un paso nuevo solo aporta su control.
3. **Títulos de pregunta ≤ 40 caracteres y subtítulos ≤ 100** (`SetupStepCopyRulesTest` lo exige).
4. **Desenfoque solo donde corresponde**: el `RenderEffect` de la sección que asoma se aplica únicamente a su control, nunca a la etiqueta, al título ni al contenedor; `haze` solo bajo la cabecera y el botón. Nada de `Modifier.blur` suelto sobre una página entera.
5. **Marcas de prueba**: la sección activa lleva `setup-step-<ID>`, la fila-resumen `setup-summary-<ID>` y el check `setup-continue` (con `Role.Button` y estado real de habilitado).
6. **El deslizado sale de una fórmula cerrada** (`WizardPageMetrics.target`) porque todo lo anterior al paso activo son filas de alto fijo. Si una fila-resumen dejara de medir lo mismo, hay que medir posiciones en lugar de usar la fórmula.
7. **El resumen se calcula una vez**, al confirmar la página, y se guarda; recalcular todas las filas en cada pulsación sería caro.
8. Se compone solo lo que se ve: una página plegada no compone su paso.

## Cómo añadir un paso

1. Añade la definición en `SetupStepDefinitions` (título ≤ 40, subtítulo ≤ 100) y el control en el `SetupXStepContent` de su bloque (sin título).
2. Dale etiqueta y valor en `SetupStepSummaries.kt` (el `when` es exhaustivo: el build avisa si falta).
3. Si el paso fusiona dos (como alias+edad o altura+peso), registra la fusión en `wizardPresentationSteps`/`wizardPageOf` y pon su texto en `wizardPageCopy`.
