# Arreglos v1 · plan de implementación (aprobado el 29-oct-2026)

El usuario aprobó TODO el lienzo (`README.md`, artifact YBycPadM4g1VTxWWTMMxQX) con las cinco opciones recomendadas:
huella radial, macros en tres tonos de tinta, fuera «Con mantequilla», ficha del asistente en el catálogo y en «Sobre el
programa», y «Tus últimas 4 semanas» = las 4 semanas de calendario que terminan hoy.

**Añadido del usuario al aprobar (literal):** «cuando un ejercicio tenga al lado una miniatura, esa debe ser la imagen del
ejercicio que TIENE KPKN. No otra cosa.» → toda miniatura de ejercicio = `ExerciseTechniqueImageLookup.resolveImageResId(...)`
(o el archivo del catálogo cuando la pieza ya lo usa). Si KPKN no tiene imagen de ese ejercicio, la fila va SIN miniatura
(nada de glifos de patrón, iniciales ni iconos en su lugar). Vale también para la ficha del asistente (Ejercicios principales).

## Reglas de la implementación

- 1:1 con el lienzo (`src/scenes-*.js` = estructura y textos; `src/arreglos.css` = medidas, colores y tipos). Lo que la
  pantalla vieja tiene y el lienzo no, se elimina; las FUNCIONES alcanzables se conservan.
- Colores y tipos comunes ya añadidos en `LoggerSheetParts.kt`: `LoggerColors.surface1/2/3`, `sheet`, `hair`, `track`,
  `macroP/macroC/macroF`, `accent`; `LoggerText.number(size)` (Inter SemiBold tabular) y `LoggerText.total` (Syne 30).
- Room congelado. Sin Gradle en los agentes (lo compila el orquestador por la cola al final). Sin commits de los agentes.
- Cada agente toca SOLO sus archivos. Si necesita un cambio en un archivo ajeno, lo describe en su informe.

## Paquetes y dueños de archivos

| Paquete | Modelo | Archivos (lista cerrada) |
|---|---|---|
| N1 Huella y nutrientes | Sonnet high | `screens/nutrition/nutrients/*.kt` (+ nuevos ahí), sus pruebas |
| N2 Registro | Sonnet high | `logger/LoggerDescribeField.kt`, `LoggerSummaryCard.kt`, `LoggerAnalysisNotice.kt`, `LoggerFoodItem.kt`, `LoggerFoodClarification.kt`, `LoggerSaveBar.kt`, `LoggerConfirmCard.kt`, `LoggerAnalysisPanel.kt`, sus pruebas |
| N3 Hoja de alimento + transición | Opus high | `logger/LoggerFoodSheet.kt`, `LoggerFoodDetailSheet.kt`, `LoggerNaturalUnit.kt`, `LoggerMeasuresSheet.kt`, `components/FoodLoggerDrawer.kt`, `ui/components/KpknSheet.kt`, `KpknSheetTheme.kt`, lo de `domain/nutrition/` que haga falta para las preparaciones, sus pruebas |
| N4 Inicio y día de Nutrición | Sonnet high | `screens/home/HomeScreen.kt`, `screens/home/design/HomeNutritionToday.kt` (+ el archivo de `HomeQuickLoggerBar`), `screens/nutrition/NutritionScreen.kt`, `nutrition/design/NutritionDayModule.kt`, `NutritionArcGauge.kt`, `NutritionMiniRing.kt`, sus pruebas |
| P1 Barra de vidrio y ⋯ | Sonnet high | `programdetail/design/ProgramFloatingBar.kt`, `programdetail/ProgramDetailScreen.kt`, el archivo de la cabecera/portada del programa, sus pruebas |
| P2 Tus últimas 4 semanas | Opus high | nuevos en `programdetail/design/progress/` (modelo puro + pantalla), `ProgramProgressVolume.kt`, `navigation/Navigation.kt`, `MainActivity.kt`, sus pruebas |
| P3 Ficha del asistente reutilizada | Opus high | `onboarding/design/entreno/plan/PlanDetailOverlay.kt` (+ nuevos), `programs/PlanInfoSheet.kt`, `programdetail/design/ProgramPlanSheet.kt`, `programs/ProgramCatalogPage.kt`, `programs/ProtocolDetailSheet.kt`, sus pruebas |

Cruces previstos: la llamada a `ProgramPlanSheet` en `ProgramDetailScreen.kt` (dueño P1) la cablea el orquestador con lo
que pida P3. La huella entra en el registro, la hoja y el día a través de las composables de `nutrients/` que ya se llaman
desde fuera: N1 cambia su aspecto sin cambiar sus firmas públicas.

## Estado

| Paquete | Estado |
|---|---|
| Tokens comunes | hecho (orquestador) |
| N1 · N2 · N3 · N4 · P1 · P2 · P3 | hechos (agentes, 29-oct ~01:35–02:30); diffs revisados |
| Cableado de cruces | hecho: ficha con su programa en Program Detail, tarjeta de 4 semanas con datos, «Compartir» en Progreso, fuente original en la atribución, rangos de Progreso reutilizados, código muerto de la barra rápida de Inicio fuera |
| Compilación + pruebas | `compileBaseDebugKotlin` en verde; 1.059 pruebas de las clases tocadas en verde (más repeticiones de las afectadas) |
| Revisión visual | `render/ArreglosV1RenderSheetTest` (360 dp, 100 % y 130 %); arreglos en `PARIDAD_1A1.md` |
| Integración en master (turno) | ver el commit de integración |

Diferencias con el lienzo y decisiones de implementación: `PARIDAD_1A1.md`.
