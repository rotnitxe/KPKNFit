# INT-C3 · Integración de rondas y focos ED-B

Estado actualizado: la primera corrida centralizada integrada compiló y ejecutó 76 pruebas, con cuatro fallos de la clase nueva C3. Se corrigió el arnés tras leer su XML; la repetición sigue pendiente del orquestador. No se ejecutó Gradle ni adb en este worktree. No se declara verde.

## Base y fusiones

Worktree `C:/kw/u24-int-c3`, rama `codex/sv2-int-c3`, base `feat/screens-v2@6bf182d81`.

1. `git merge-tree --write-tree HEAD 385cab731`: sin conflictos, árbol `b3df59c9d6d0d1eee2dda3272e2226ab01ebaa56`. Merge editor `c629ff315`.
2. Preflight cardio `8f8a02b95`: conflicto en `ExerciseEditorCard.kt`; árbol provisional `b5b6890daf3c50b3ab1c08bce6a8b39d121a9193`. Merge `1a549cfb0` conserva la condición `memberBox` del fondo de ED-D y los contenidos, etiqueta unilateral y focos ED-B. Se elimina el bloque parcialmente duplicado del conflicto, sin perder la miniatura/insignia ni estrella y flecha.

## Adaptación

- `SupersetGroupEditorCard` transmite `onUpdateExercise` al carrusel y a la ficha de cada miembro; los focos RM permiten editar 1RM/PR o TM según la base real de la serie.
- Ambas rutas de C3, bilateral y unilateral, despachan primero `purpose.isAmrap` a `AmrapSetFocus`, después RM a `RmSetFocus`, y conservan `InlineSetRow` para el resto, incluido su TM declarado.
- Las acciones Subir/Bajar/Eliminar se mantienen para RM/AMRAP con objetivos de 48 dp y una sola copia por serie (primer lado visible). Los límites del orden y el hueco para añadir el lado ausente permanecen intactos.
- Se conserva la selección y expansión por ronda/miembro, el orden L/R de `unilateralSideOrder`, las cargas y dosis explícitas de cada lado. El porcentaje y la referencia RM siguen siendo globales según ED-B; al editarlos el recálculo común actualiza ambos lados sin borrar sus rangos/RIR. AMRAP usa la carga y vista previa del lado correspondiente.
- Los descansos siguen siendo del grupo/ronda: no se añade ningún descanso individual que la ejecución C3 no consuma.
- Revisión de `SupersetSmartLoadDialog`: ya tiene TM separado y avisa cuando falta; no infiere TM desde 1RM. No necesitó cambios.
- El ViewModel ya transforma las sesiones mediante `withExplicitRmLoads(previous).normalizeSession()` (`SessionEditorViewModel.kt`); C3 reenvía los transformadores al mismo carril. No se duplica esa normalización dentro de la presentación. El arnés nuevo reproduce `withExplicitRmLoads(previous).normalizeExercise()` para probar el cálculo.

## Pruebas preparadas

Nueva clase nativa `com.example.kpkn.screens.sessioneditor.components.SupersetRoundsFocusIntegrationTest`:

- RM con tres cifras enlazadas, % editado por click, recálculo, conservación de RIR/rango y acciones Bajar/Eliminar.
- Edición de TM desde el grupo, recálculo exclusivo de series %TM y preservación de las %1RM.
- Aviso de TM ausente aunque exista 1RM.
- Cargas L/R distintas, orden R/L, acciones únicas y % global con dosis laterales conservadas.
- AMRAP en ejercicio RM tiene prioridad, vista previa del mismo lado y cambios de mínimo/propósito sin perder las cargas laterales.
- AMRAP bilateral conserva carga/rango/RIR al cambiar mínimo.
- Segunda ronda conserva Subir/Eliminar y excluye Bajar.
- Escenas nativas RM/TM y AMRAP lateral al 100 y 130 %, escritas solo con `KPKN_RENDER_DIR`.

Clases concretas para la corrida centralizada, además de `:app:compileBaseDebugKotlin`:

```text
com.example.kpkn.screens.sessioneditor.components.SupersetRoundsFocusIntegrationTest
com.example.kpkn.screens.sessioneditor.components.SupersetRoundsC3ComposeTest
com.example.kpkn.screens.sessioneditor.components.EdbFocusControlsTest
com.example.kpkn.screens.sessioneditor.components.SessionEditorSupersetFrameComposeTest
com.example.kpkn.screens.sessioneditor.components.sheets.SupersetCreatorC2ComposeTest
com.example.kpkn.render.SessionEditorEddRenderSheetTest
```

Para generar los cuatro renders nuevos, definir `KPKN_RENDER_DIR` y ejecutar la clase nueva completa; sin esa variable sus dos métodos render se omiten. Abrir y revisar los PNG antes de declarar completada la validación visual. Los renders anteriores C2–C7 siguen disponibles en `SessionEditorEddRenderSheetTest`.

Revisión estática realizada: `git diff --check`, sin errores. Ningún resultado de ejecución está atribuido a estas pruebas todavía.

## Límites y riesgos

No se modifican Room, entidades, migraciones ni schemas, ni focos compartidos ED-B. No hay push, master, instalación ni QA. La integración posterior con ED-C deberá conservar su slot opcional de preparación y los valores predeterminados ED-B en `ExerciseEditorCard`; no se anticipa ese conflicto en este paquete. El conflicto real ED-B/ED-D resuelto aquí debe cotejarse con la fusión principal al integrar. Los tests y renders nuevos aún pueden requerir ajuste al correr; no son evidencia de ejecución.


## Diagnóstico de la primera corrida integrada y corrección del arnés

Se leyó el XML real `C:/kw/u11/android-native/app/build/test-results/testBaseDebugUnitTest/TEST-com.example.kpkn.screens.sessioneditor.components.SupersetRoundsFocusIntegrationTest.xml`, timestamp `2026-10-23T04:49:27.699Z`: nueve casos ejecutados, cuatro fallos, sin errores ni omitidos. Los cinco casos que pasaron incluyen TM por el callback del grupo, falta TM, AMRAP con vista previa lateral y ambos métodos de render. La corrida global de 76 casos fue ejecutada por el orquestador, no desde u24.

- Tres fallos (`rmTieneTresCifrasEnlazadasConservaRangosYRirYAcciones`, `amrapBilateralConservaCargaYRangoAlCambiarElMinimo`, `segundaRondaConservaSubirEliminarYNoBajar`) son selectores ambiguos: «Eliminar» aparece también en la cabecera de rondas. El XML identifica dos nodos y aborta antes del click o del recuento. Los selectores de acciones ahora exigen el ancestro `round-set-focus-s1` o `round-set-focus-s2`; se conserva el click nativo y se verifica el mismo callback, sin ocultar ni retirar ninguna acción.
- `rmLateralRespetaOrdenYCargasYRecalculaAmbosLadosConElPorcentajeComun` falló al comparar `boundsInRoot.top`: los dos focos pueden estar fuera del viewport y sus límites quedan recortados al mismo borde. Se usa `getUnclippedBoundsInRoot`, API ya utilizada en `WeekBleedTest`, para exigir R antes de L y ausencia de solapamiento. Además se desplaza hasta la carga de cada lado y se exige que sea visible. Se mantienen todas las cifras de carga, rangos y RIR; el porcentaje sigue editándose con click nativo. Su recuento de acciones también se limita a ambos focos y comprueba que el segundo lado no duplique Eliminar.

La evidencia del XML no señala un fallo de producción en esos cuatro puntos: son errores concretos de selección/medición del arnés. La corrección modifica solo la clase de pruebas y este informe, nunca producción. No hay una segunda ejecución todavía, por lo que el resultado corregido sigue pendiente de validación centralizada.


## Corrección visual real del alcance AMRAP al 130 %

Tras la primera corrida se inspeccionó el PNG nativo `C:/kw/shots/int-edd/int_c3_amrap_lateral_130.png`. Se confirmó un defecto de producción independiente de los cuatro fallos del arnés: el alcance «Próximas sesiones» aparecía elidido dentro del foco C3 anidado.

`AmrapSetFocus` conserva el segmentado común cuando ambas etiquetas caben, incluso con su reducción tipográfica existente. Si la etiqueta más ancha no cabe ni al mínimo de 11 sp del segmentado, el alcance pasa localmente a dos `LoadTypeCell` comunes en columna: nombres completos, selección y callbacks idénticos y objetivos de 48 dp. La decisión depende de ancho disponible y medida real del texto con la densidad/fuente actual; no cambia el componente global ni su altura táctil.

Se añadió `alcanceAmrapEnC3Al130MuestraAmbasDecisionesCompletasYConservaSusClicks`: comprueba `TextLayoutResult` completo, sin elipsis ni desbordes, y pulsa ambos alcances en el foco lateral C3 al 130 %, verificando sus valores de `CalibrationScope` y la conservación de cargas laterales. El fixture exclusivo del render RM/TM ahora calcula su peso con `rmSetLoadKg`: TM 100 × 80 % muestra 80 kg, en lugar de mantener los 95 kg explícitos usados en las pruebas de callbacks. Estas últimas conservan sus cifras/aserciones.

Repetición necesaria: clase C3 nueva completa, `EdbFocusControlsTest` y `EdbRenderSheetTest`, por tocar el foco compartido. Sin ejecución Gradle desde este worktree. El nuevo layout y sus pruebas/renders siguen pendientes de validación centralizada.


La inspección del mismo PNG también mostró que AMRAP había perdido la identificación del lado que `InlineSetRow` ofrecía. Se restituye «Lado R» / «Lado L» en el foco AMRAP cuando recibe `fixedUnilateralSide`, sin cambiar el texto exacto de la carga ni duplicar etiquetas RM. La prueba lateral exige ambos rótulos dentro de su foco correspondiente y los alcanza con desplazamiento real. Es una preservación funcional del consumidor, no una nueva lateralidad ni un nuevo porcentaje por lado.
