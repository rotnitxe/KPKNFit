# INT-G1 · Adaptación de la edición de series en vivo a ED-B

Fecha: 23-oct-2026. Worktree exclusivo: `C:/kw/u25-int-g1`, rama `codex/sv2-int-g1`.

## Base y fusiones

- Base `feat/screens-v2@6bf182d81`; checkout limpio antes de fusionar.
- Preflight `git merge-tree --write-tree HEAD 385cab731`: árbol `b3df59c9d6d0d1eee2dda3272e2226ab01ebaa56`, sin conflictos. Fusión `sv2/editor@385cab731` en `7b922f211`.
- Preflight posterior `git merge-tree --write-tree HEAD d5e7c7ac4`: árbol `24535d6bc72a5f97368d836b9df235d571563901`, sin conflictos. Fusión `sv2/otros@d5e7c7ac4` en `ee4797404`.
- Producción adaptada en `ceb329a7e`; pruebas en `7f35f777c`.

## Cambios y decisiones

`WorkoutEditSeriesBody.kt` / `LiveEditSeriesBody` conserva cabecera «Serie X de N», carrusel y fichas G1 de **56 dp**, navegación anterior/siguiente, añadir, aplicar a todas con confirmación, duplicar, subir, bajar, eliminar, lateralidad, descanso del ejercicio y carga inteligente. No se modifica el footer fijo de `WorkoutEditSeriesSheet` ni su `fullHeight = true`.

El dispatch sigue el contrato de `ExerciseSetList`: `purpose.isAmrap` tiene prioridad, incluso dentro de una receta RM; usa `AmrapSetFocus`. Las demás series RM usan `RmSetFocus` con callback completo de ejercicio; los otros modos conservan `InlineSetRow`, ahora con `trainingMax = exercise.referenceTrainingMax`. El cálculo memorizado incluye el TM. La carga y la vista previa AMRAP reciben `fixedUnilateralSide`; el lado inicial respeta `RIGHT_LEFT` y la selección manual persiste al cambiar el orden.

`LiveEditModePill` conserva su píldora visual de 44 dp dentro del objetivo de 48 dp. Sustituye el dropdown inmediato por `ExerciseModeSheet`: elegir marca una opción; solo «Aplicar Tiempo» (o el modo elegido) transforma el ejercicio mediante `withTrainingMode`.

`WorkoutStructureSheetsHost.kt` / `WorkoutEditSeriesSheet` transforma el borrador mediante `transform(previous).normalizeLiveEditRmLoads(previous)` tanto en el foco como en carga inteligente. Ese helper **solo en RM** ejecuta `withExplicitRmLoads(previous).normalizeExercise()`; en los demás modos conserva el resultado de la transformación, como antes de la adaptación. La extensión real de esta base es `normalizeExercise`, no existe `normalizedLoads`. Se conserva la política canónica: 1RM/TM independientes, redondeo existente y conservación de prescripción híbrida (rango/RIR) al editar el porcentaje o la referencia. No se infiere TM. Se acota explícitamente a RM porque `normalizeExercise()` también recalcula cargas REPS/TIME/CUSTOM desde la referencia: usarlo en todo el borrador modificaría cargas manuales de 100/105 kg al editar descansos, duplicar o aplicar a todas. Se mantienen las aserciones originales de cargas y se añade una regresión de descanso en Tiempo; el descanso en Reps comprueba toda la lista 100/100/100/105.

El foco añade el control común `SetRestControl`, que edita exclusivamente `restAfterSeconds` de la serie o lo vuelve a `null` para heredar. Tiene consumo real: `WorkoutSetRecorder.kt`, al resolver el descanso tras una serie, prioriza el `restAfterSeconds` de la serie objetivo antes de `baseRest` (aprox. línea 888). Por ello no es una interfaz sin efecto. La regla especial de rest-pause conserva su prioridad existente.

## Pruebas preparadas y expectativas migradas

- `com.example.kpkn.screens.workout.WorkoutEditSeriesSheetBoardTest`: 39 casos declarados (incluye G2 heredado). Se conservan las pruebas funcionales de acciones, cargas, selección y guardado. Se añaden casos de edición RM con recálculo, %TM por su referencia, cambios de TM sin afectar series %1RM, referencias ausentes, cambio de 1RM desde carga inteligente, propósito AMRAP dentro de RM, RIGHT_LEFT, carga/vista previa por lado y descanso individual/restablecer herencia.
- Las técnicas Drop-set/Rest-pause ahora se esperan **ausentes en RIR** y presentes en una serie que declara fallo junto a «¿Vas más allá del fallo?», conforme a ED-B. No se eliminan acciones de técnica: se comprueba su condición correcta.
- El cambio a Tiempo pulsa «Aplicar Tiempo» y comprueba que la píldora continúa mostrando Reps tras seleccionar Tiempo, antes de confirmar. `com.example.kpkn.screens.workout.WorkoutEditSeriesAuditTest` (4 casos) mantiene sus aserciones de borrador/recuperación y añade ese paso al helper.
- Las pruebas de descanso del ejercicio pasan del contrato anterior de dos relojes «Aceptar» a la hoja común B7: Series y Entre lados juntos, una confirmación «Listo», conservando los valores esperados 150/45 segundos. La dosis siguiente usa el NBSP aprobado de «RIR 2».
- `com.example.kpkn.render.LiveEditG1IntegrationRenderTest`: 6 casos nativos con `KpknOverlayHostContent` real, RM/TM a 360×880 y 360×740 con fuentes 100/130 %, y AMRAP a ambas alturas al 130 %. Comprueba tamaño real de escena, ficha de 56 dp, objetivo de modo/confirmación de 48 dp, acceso por desplazamiento a acciones y footer visible antes/después. Con `KPKN_RENDER_DIR` genera PNG inicial y de acciones por escenario; sin la variable, mantiene las aserciones de medidas.

## Validación pendiente

**No se ejecutó Gradle, JVM, adb, compilación ni render en este worktree**, por instrucción expresa del orquestador: todas las corridas están centralizadas. No se afirma verde ni revisión visual nueva. Se revisaron firmas y contratos reales y `git diff --check` no informa errores.

Corrida centralizada propuesta, por la cola v10 y solo tras terminar la corrida propia activa:

```text
bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests com.example.kpkn.screens.workout.WorkoutEditSeriesSheetBoardTest --tests com.example.kpkn.screens.workout.WorkoutEditSeriesAuditTest --tests com.example.kpkn.render.LiveEditG1IntegrationRenderTest
```

Fijar `KPKN_RENDER_DIR` en la corrida centralizada para producir los PNG y abrirlos antes de cerrar la QA visual. Los renders existentes de `OtrosEditoresRenderSheetTest` siguen disponibles; no se modificaron.

## Riesgos de integración y límites

- Requiere ED-B (`RmSetFocus`, `AmrapSetFocus`, `ExerciseModeSheet`, `SetRestControl`) y G previamente fusionados, como en este worktree. Root coordina la integración final en u11.
- `WorkoutStructureSheetsHost.kt` es una zona compartida: limitar resolución a los dos callbacks del borrador, preservando G2 y cambios de otras ramas.
- El cálculo/normalización y los focos son los canónicos de ED-B; esta adaptación no redefine sus políticas ni añade modelos paralelos. Los cambios de porcentaje siguen siendo comunes a ambos lados, conservando los RIR/rangos individuales existentes.
- Room v30 permanece congelado. Sin cambios de entidades, migraciones, schemas, `KpknDatabase`, datasets, build scripts, paquetes personales ni secretos. Sin push, master, adb ni parada de Java.
