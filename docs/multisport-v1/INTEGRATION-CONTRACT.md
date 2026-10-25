# Integración de motores sin cambios visuales

El usuario rechazó las galerías v1/v2 y pidió continuar la implementación dejando la UI para después. Este corte conecta el núcleo con los modelos nativos y el repositorio existente. Las maquetas no definen sesiones, navegación ni requisitos de captura.

## Propuestas sobre sesiones reales

`HybridPlanningService.prepareAndSave` recibe programa/semana, ventanas y compromisos declarados, y vínculos explícitos entre sesión existente y objetivo. Usa `HybridProgramPlanner` para traducir las sesiones base a candidatos de `HybridWeekPlanner`. El presupuesto de tiempo es el que declara el vínculo o `Session.targetDurationMinutes`; no se interpreta como duración medida, ni se genera una dosis deportiva.

Un resultado preparado significa que esos candidatos caben bajo los límites declarados. No certifica que todos los objetivos del atleta estén cubiertos ni que el contenido materializado dure ese presupuesto. La integración de la estimación de duración existente y la prescripción/compatibilidad deportiva siguen pendientes antes de un aplicador de producto.

Cada sesión base debe tener un vínculo. Su lugar existente no se relaja para hacer encajar la propuesta; el material y los demás límites se verifican según las restricciones declaradas. Las variantes B/C/D son alternativas y se conservan completas, sin sumarlas como sesiones simultáneas. Se distinguen entrada inválida, imposibilidad de colocación, presupuesto de búsqueda agotado y omisiones opcionales con sus motivos. Las sesiones de competición no se recolocan como entrenamiento corriente; requieren el tratamiento de evento fijo de una fase posterior. Los planes sin intención declarada conservan su comportamiento anterior.

La configuración es opcional en `Program.hybridPlanning`; la propuesta es opcional en `ProgramWeek.hybridProposal`. Ambas viven en el JSON existente de `ProgramEntity`. No se añaden tablas, no se cambia la versión Room y no se crean logs. Guardar propuesta no modifica `Session`, sus ejercicios/series, `dayOfWeek`, `assignedDays`, el calendario ni el cursor del run. El writer existente sigue aplicando su normalización habitual; se verifica por separado con Room y programas calendarizados activos.

La propuesta se vincula al ID del programa, el fingerprint de la semana, la intención y la configuración usadas. `isCurrent` verifica identidad y vigencia del origen; **no valida la integridad de las asignaciones ni autoriza aplicarlas**. Antes de un futuro aplicador deberá recalcularse el resultado y resolverse el alcance por ocurrencia/run, fechas, variantes elegidas y aceptación. La propuesta actual corresponde a la plantilla de sesiones base de una semana, nunca a un nuevo calendario activo.

El cálculo y la serialización corren fuera del monitor de escritura. El servicio compara el programa completo con la instantánea antes de guardar; una edición concurrente produce conflicto y obliga a recalcular. El éxito llega después del commit durable. Una cancelación puede llegar después de que Room haya escrito: quien retome debe releer el programa antes de reintentar.

## Actividad realizada sin doble conteo

`ProgramRepository.summarizeCompletedTraining` consulta el historial existente en `Dispatchers.Default` y delega en `WorkoutActivityAccounting`. Una sesión mixta sigue siendo una actividad. El ID del log es la identidad; repeticiones exactas cuentan una vez, mientras que variantes conflictivas se conservan para resolver y se excluyen de los totales. Los IDs vacíos no se fusionan.

Los minutos vienen de `WorkoutLog.durationMinutes`. La energía viene una sola vez de `energySummary.totalKcal` con fuente `FINAL`; no se suman encima calorías de sets, contribuciones de ejercicios ni una segunda estimación de cardio. `FINAL` significa estimación final del motor existente y no una medición inferida. Las estimaciones `PLANNED`/`LIVE`, datos ausentes, rangos inválidos, cero conocido y totales parciales quedan diferenciados. La suma usa `Long`.

Esta consulta todavía no reemplaza consumidores de nutrición, AUGE o vistas actuales. Tampoco convierte propuestas en actividad realizada. No cambia fórmulas ni atribuye un deporte por el nombre de la sesión o sus ejercicios.

## Límites vigentes

La prescripción fisiológica híbrida, los motores deportivos especializados, el aplicador de calendario y la nueva UI siguen siendo fases posteriores. No se ha comprobado Huawei Watch ni se ha usado un dispositivo físico en este corte. La implementación se prueba en el worktree aislado de multideporte; los trabajos de cardio y técnica permanecen separados.

Los logs y resultados de este corte se conservan en `artifacts/multisport-integration-20261018/`. La evidencia del APK y de las 87 pruebas anteriores corresponde al primer candidato y se conserva como historial.
