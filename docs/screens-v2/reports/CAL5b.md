# CAL5b · Referencias y TM por ejercicio

Estado central23-oct08:20: núcleo135casos JVM y144Android verdes (corridas2). El press militar del wizard y su resumen de cuatro levantamientos están implementados; su nueva prueba pasó los2casos funcionales y falló4medidas por seleccionar el layout del título fusionado. Corregido el selector en4c37d4c4c; repetición central pendiente. INT-CAL5B e INT-4 contienen la secuencia completa. Los apartados de preparación inferiores son históricos y no sustituyen esta evidencia.

Estado: implementado en `codex/sv2-cal5b` sobre base provisional. Compilación, pruebas y renders pendientes de la cola central del agente principal; no se ejecutó Gradle, JVM ni adb en este trabajo. No se cambió Room.

## Almacenamiento y contrato compartido

Se reutiliza `Program.exerciseLoadReferences` y `PlanLoadReference` serializados. `ExerciseLoadReferenceStore` es puro: resuelve KIND/configuración/convenio/lado/contextKey exactos; OBSERVED añade rango, origen y slot real. No convierte XRM en 1RM ni TM. Captura más reciente por fecha conocida, revisión e inserción; fechas históricas desconocidas siguen null. Capturar solo almacena. La aplicación pendiente es otra operación, protegida por evidencia de ejecución y edición manual.

Metadata opcional conserva origen, motivo, sourceId estable, sourceSetId, fracción real, incremento, revisión e invalidación explícita. Un mismo evento y payload no duplica; una corrección legítima deja revisión nueva. Invalidar conserva el historial y evita resucitar un TM borrado al hidratar. `sourceSlotId` representa exclusivamente un slot real de receta.

`ExerciseTrainingMax` migra TM ya almacenados del perfil y las prescripciones, incluidos press militar y variantes B/C/D, con configuración exacta y sin inventar fechas ni un 1RM a partir de TM. La UI global captura side=null. Capacidades unilaterales no se convierten en base global. Ejercicios fuera de los cuatro principales no necesitan perfil legacy.

## Persistencia, progresión y materializador

`withConfirmedExerciseReferences` se invoca en el guardado del editor y en la confirmación de la edición en vivo. El foco cancelable no escribe la bolsa. Detalle y preparación también guardan solo al confirmar. La referencia y la reconstrucción pendiente comparten mutación durable: si reconstruir falla, se conservan ambas anteriores.

TM y 1RM se resuelven por KIND independientemente; una declaración pendiente admite captura compatible posterior. El perfil y la bolsa se sincronizan al confirmar el canónico y al aplicar progresión propia; no se propagan ediciones manuales a variantes por parecido de LiftSlot. Una variante derivada sigue avances de su receta real mientras no tenga override manual. Se conservan semanas/sesiones hechas y manualSessionOverrides; no existe una bandera ficticia por serie.

El clamp WeeklyKg ya declaraba que sin 1RM no hay tope: se elimina la inferencia TM/fracción que fabricaba ese tope. Se conservan los incrementos de la receta. TM90 + incremento20 produce110 sin inventar 1RM100. `%1RM` aislado no rellena TM. `PROMOTE_TM` compara con el 1RM real y mantiene su condición temporal.

`observedApreXrmBlock` comparte la validación estricta del bloque APRE y referencia OBSERVED/APRE_XRM/rango/configuración/convenio. Solo ese bloque conserva los kilos XRM frente al recálculo genérico RM; un marcador inválido mantiene el cálculo normal. El agente principal posee captura y progresión APRE y su hook de materialización.

## Interfaz

La tarjeta en vivo muestra porcentaje/base TM/kilos. El foco RM existente conserva 1RM y TM independientes. El detalle añade referencias por ejercicio, press militar, motivo e historial. Los controles de confirmar/editar/cancelar usan componentes de 48dp o más.

Preparación pide únicamente TM necesario ausente, propone 1RM × fracción real cuando existen ambos datos y requiere Confirmar TM. Ahora no conserva el pendiente. No inventa fracción ni guarda al abrir. La reconstrucción es automática tras confirmar; se retira la acción Re-materializar. El estado antiguo pendiente se intenta reconstruir al abrir el módulo, con fallo honesto y datos anteriores conservados.

## Pruebas preparadas, aún no ejecutadas

- ExerciseLoadReferenceStoreTest: identidad, latest, revisión/corrección y borrado con historial.
- ExerciseTrainingMaxTest: cuatro legacy, militar, fuera de cuatro, variante manual/ciclo, pendiente, evidencia y variantes B/C/D.
- Cal5bMaterializerReferenceTest y SessionEditorApreXrmLoadTest: separación KIND, pending y exclusión APRE válida.
- FixTmMaterializerLoadBasisTest y WeeklyKgCapTest: sin inferencias TM/1RM y clamp sin capacidad real.
- Cal5bTrainingMaxUiTest: confirmación/cancelación/valor real y medidas/renders 360×740/880, fuente100/130, controles48dp; PNG opcionales vía KPKN_RENDER_DIR.
- Cal5bTrainingMaxPanelTest: press militar y remo sin perfil, edición real, cancelar y confirmar con OverlayHost real.
- ProgramTrainingMaxRowTest: militar en resumen legacy.
- ProgramDetailViewModelTest: el fallo de reconstrucción ahora exige conservar referencia, bolsa y semanas anteriores; reemplaza la expectativa antigua de guardar TM incoherente hasta pulsar Re-materializar.

Solo se verificó estáticamente `git diff --check`. No se afirma compilación ni QA visual verde. Renders y pruebas centrales deben comprobar overlays, variantes, guardado durable y convivencia con la integración APRE. La base provisional se actualizará desde integración verde antes de entregar validación.
