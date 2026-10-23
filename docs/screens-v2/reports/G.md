# G · Otros editores · 22-oct-2026

Se continúa y conserva el diff recibido de G1–G4. El paquete supera sus pruebas concretas; la integración y el QA en emulador siguen pendientes.

| Tablero | Implementación y funciones migradas |
|---|---|
| G1 | `WorkoutEditSeriesBody.kt`, `WorkoutStructureSheetsHost.kt`, `WorkoutDrawerHost.kt`: ejercicio, selección de serie, foco, flechas, descanso, duplicar/mover/eliminar y confirmación sobre el estado real. El movimiento conserva el id seleccionado. La lateralidad pasa al botón ⋯ y a la hoja correspondiente; conserva objetivos L/R y su orden mediante `toggledBilateralUnilateral`. |
| G2 | Los mismos anfitriones abren la prescripción y las plantillas de cardio reales. Se conserva la confirmación y la edición del estado en vivo. |
| G3 | `ImportExerciseEditor.kt`, `ImportPreview.kt`, `ImportProgramScreen.kt`, `ImportAddExerciseSheet.kt`: ejercicio abierto, ajustes, deshacer y acciones inferiores para añadir desde el catálogo contextual y guardar. Se conserva el motor de importación y los días. |
| G4 | `PlanDetailOverlay.kt`: sustitución sobre el día desplegado, con cancelar o confirmar antes de aplicar la sustitución existente. |

## Validación

Por v10, una corrida propia a la vez, sin compilar instrumentación Android:

- `C:/kw/logs/g-codex-20261022-1.log`: BUILD SUCCESSFUL, 7 min 47 s. 53 pruebas, 0 fallos y 0 omitidas: `OtrosEditoresRenderSheetTest` (9), `PlanDetailDayExercisesTest` (14), `WorkoutEditSeriesAuditTest` (4), `WorkoutEditSeriesSheetBoardTest` (26).
- Dos filtros del primer comando apuntaban equivocadamente al paquete `screens.programs.import`. Por eso esa corrida no validó el importador. Se corrigió el filtro a su paquete declarado `com.example.kpkn.screens.programs.importer` y se ejecutó por separado.
- `C:/kw/logs/g-codex-20261022-2.log`: BUILD SUCCESSFUL, 44 s. `ImportBoardG3Test` (17) e `ImportWeekdaysTest` (6): 23 pruebas, 0 fallos y 0 omitidas.
- Total disjunto: 76 pruebas, seis clases, 0 fallos y 0 omitidas.

`WorkoutEditSeriesSheetBoardTest` utiliza el renderizador NATIVE para comprobar el nuevo recorrido de lateralidad con clics físicos sobre las hojas visibles. Se conservan las aserciones finales de los dos lados, datos y selección; las utilidades semánticas históricas del resto de la clase no se ampliaron para este recorrido.

Renders: nueve PNG en `C:/kw/shots/g/`: G1, G2, G3 y G4 a 360 × 880 y 360 × 740 dp, más G3 añadiendo ejercicio a 360 × 880. Se revisaron las dos alturas de G1 y escenas de G2–G4. Son capturas con escala de fuente predeterminada; no constituyen QA de fuente al 130 % ni de dispositivo.

## Riesgos y tareas de integración

G nace de una base anterior a los focos y hojas nuevos de ED-B. Al integrar, debe completarse la adaptación de sus consumidores:

- En G1, despachar AMRAP por propósito a `AmrapSetFocus` y RM a `RmSetFocus`, conservando `fixedUnilateralSide` y la selección L/R existentes. Para los demás modos, pasar el TM declarado a `InlineSetRow`.
- Verificar que las actualizaciones en vivo aplican `withExplicitRmLoads(previous)` y conservan las recetas RIR/rango al editar porcentajes. Añadir cobertura de TM y lados en esta superficie.
- Cambiar el desplegable inmediato del modo por `ExerciseModeSheet`, con la confirmación común de ED-B; adaptar la prueba que elige Tiempo para que confirme su aplicación.
- La puerta de técnicas a FALLO y el descanso de serie deben venir de los controles comunes integrados. Las capturas aisladas de esta rama todavía muestran los controles anteriores.
- Revisar también los consumidores de focos del importador y la regla de fichas compartidas frente al tablero G1, sin descartar su edición contextual.
- QA final: alturas compactas, fuente al 130 %, hojas y acciones inferiores, incluida la confirmación real de G4.

Room congelado. Sin cambios de entidades, migraciones o esquemas, sin push, merge en master, instalación, adb ni firma.
