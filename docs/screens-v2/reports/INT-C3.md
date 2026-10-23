# INT-C3 · Integración de rondas y focos ED-B

Estado: implementación preparada; compilación, pruebas y renders pendientes de la corrida centralizada del orquestador. No se ejecutó Gradle ni adb en este worktree. No se declara verde.

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
