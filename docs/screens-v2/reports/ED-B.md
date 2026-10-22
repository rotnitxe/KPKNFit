ED-B · Ejercicio y series en el editor — base y estado (PARCIAL)

Sesión de 2026-10-22 (relevo de la ola screens-v2). No se ha implementado ningún tablero nuevo. Este informe deja la base tras la fusión `db5aa08cf`, la clasificación de fallos de la corrida base y el diseño de B2 ya extraído del tablero, para continuar sin repetir trabajo.

## Corrida base (edb-3)

- Comando: `bash /c/kw/tools/gradle_slot.sh /c/kw/u14/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.domain.training.*" --tests "com.example.kpkn.domain.calculations.*"`
- Log: `C:\kw\logs\edb-3.log` (22 min 15 s, BUILD FAILED). El intento previo `edb-2` no llegó a compilar: faltaba `import com.example.kpkn.ui.design.KpknSetFormat` en `SessionEditorControls.kt` (usa `KpknSetFormat.doseTime` en el stepper de descansos del diff). Corregido.
- Resultado: **2407 tests, 9 fallos**, clasificados:

| Fallo | Origen | Qué hacer |
|---|---|---|
| `ExerciseOptionsBarComposeTest.laHojaDeLateralidadHaceLoMismoQueToggledBilateralUnilateral` | **Real (diff previo)** | El clic en «Unilateral» de la hoja no llega a `onUpdateExercise`: el assert da `expected:<Exercise(... isUnilateral=true, unilateralMode=UNILATERAL_PAIRED ...)> but was:<null>`. El menú «Lateralidad: Bilateral» y la hoja sí se abren; el problema está en el `clickable` de la opción o en cómo la prueba lo pulsa (`LateralitySheetBody`, `ExerciseOptionsBar.kt:185-191`). |
| `SessionExerciseDoseTest.dosisDeFuerzaConRangoPorcentajeRirYDescanso` | Pruebas | El diff pone espacio no separable en «RIR»/«RPE»/«por lado» (retoque ED-A); las aserciones buscan espacio normal. Adaptar las pruebas. |
| `SessionExerciseDoseTest.unilateralAcabaConPorLado` | Pruebas | Ídem («por lado»). |
| `SessionExerciseDoseTest.unaSuperserieNoRepiteElDescanso` | Pruebas | Ídem. |
| `SessionListBehaviorTest.laFilaPlegadaTieneSuDosis` | Pruebas | Ídem. |
| `SessionListBehaviorTest.unaFilaUnilateralEscribePorLado` | Pruebas | Ídem. |
| `ExerciseSetListComposeTest.enUnilateralSinLadoDerechoSeOfreceAnadirD` | Pruebas | B5: I/D pasó a L/R; la prueba pide «Añadir D» → adaptar a «Añadir R». |
| `ExerciseCatalogImageCoverageTest` ×2 | Entorno | Checkout disperso (ya documentado en el brief). |

## Qué hay en el worktree (sin commitear, continuar y no descartar)

- Diff de **B1, B4, B5, B7, B8 y el espacio no separable**: `SessionEditorControls.kt`, `SessionExerciseDose.kt`, `ExerciseEditorCard.kt`, `ExerciseOptionsBar.kt`, `ExerciseSetList.kt` (289+/41−), más `ExerciseOptionsBarComposeTest.kt` actualizado al tablero (lateralidad en ⋯, «Orden L/R»). Incluye la corrección del import de `KpknSetFormat`.
- No se commitea porque la ola no commitea trabajo a medias y porque 7 de los 9 fallos vienen de ahí.

## B2 ya diseñado (sin implementar)

Fuente: tablero `B2-FocoSerie.dc.html` (accesible en `…\claude\...\kpkn_editor_design\project\`; ojo, un relevo anterior decía que no lo era).

1. **Foco de serie** (solo lo de la serie): cabecera «Serie n de total» + flechas anterior/siguiente, tipo de carga, **carga de solo lectura** (el tablero la enseña como `output`, no como campo), «Reps / rango» editable, el valor de intensidad según modo, el segmentado de intensidad (RIR / RPE / Fallo), «Descanso de esta serie» con −/+ de 15 s y las acciones («Aplicar a todas», «Duplicar», «Subir», «Bajar», «Eliminar»). Lateralidad, modo y carga inteligente quedan fuera (ya lo están: viven en `ExerciseOptionsBar`).
2. **Drop-set y rest-pause solo a FALLO**, detrás de «¿Vas más allá del fallo?», como mini tarjetas con su configuración (ya existen en `InlineSetRowTechniqueChips`). Implementación: envolver `TechniqueSection()` (`InlineSetRow.kt`, línea ~921) con una puerta `if (!set.isFailure) return` y el rótulo encima de los chips.
3. **«Descanso de esta serie»**: el modelo ya tiene `ExerciseSet.restAfterSeconds` (null = usar el del ejercicio). Enlazar el stepper a ese campo; **no toca Room**.
4. **Pruebas**: adaptar `ExerciseSetListComposeTest.openedRowShows` (quitar «Drop-set»/«Rest-pause» de las series que no están a FALLO) y añadir las de la puerta (no aparecen sin FALLO; aparecen con la pregunta cuando `intensityMode = FAILURE`).

## Riesgos de integración

- **ED-C (u18)** toca el bloque de preparación de `ExerciseEditorCard`; B2 no lo toca.
- **ED-D (u17)** trabaja superseries y cardio; el espacio no separable toca textos compartidos («por lado»), coordinar con sus pruebas.
- **LV-C (u11)** cambió `ExerciseEditorExpanded.kt` y `PreparationCarouselCards.kt` al carrusel `warmupTargetLoadKg`; al fusionar ED-B con u11 habrá que mezclar sobre eso.
- Estas pruebas son Robolectric/Compose: **no** pasan por el carril JVM (`kpkn_jvm.sh`); la verificación es `:app:testBaseDebugUnitTest` por la cola Android, y los renders con los `*RenderSheetTest` (`KPKN_RENDER_DIR=C:\kw\shots\edb`).
