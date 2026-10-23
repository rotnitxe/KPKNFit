# CAL7 · Recorridos con Room real

## Estado y límites

Preparación provisional en `C:/kw/u23`, `codex/sv2-cal7`, desde `bfaa78fc5`. Solo pruebas y este informe; ninguna función de producción modificada. **No se ejecutó Gradle, JVM ni adb.** Todos los resultados están pendientes de la corrida central. Antes de validar/integrar se actualizará la base con Fase3 verde y las dependencias APRE, CAL5b y PLAN-OBS.

## Primeros recorridos preparados

`Cal7FreeSessionRoomTest` reutiliza `LiveSessionVmFixture`, `RoomTestRule`, `ProgramRepository.initForTests` (Room en memoria), `WorkoutViewModel`, grabador, persistencia y cierre reales. No usa puertos simulados ni inserta registros de entrenamiento fabricados.

1. Sesión de atleta sin receta gestionada: registrar calibrador, aplicar, recrear ViewModel desde Room, revertir, registrar otra fuente con alcance NEXT_SESSIONS, aceptar, completar las cinco series reales, terminar, leer el log/decisiones durables y abrir una ejecución nueva. Comprueba 60→62,5→60 hoy, referencia65 en la próxima, resolución en resumen, realizadas intactas y el ejercicio contiguo40kg sin alteración.
2. Cancelar por `WorkoutViewModel.cancelWorkout`: decisión aceptada previa, borrado confirmado en Room, cero logs/referencias aceptadas, bolsa sin cambios y ejecución nueva sin referencia residual.
3. RINGS: dos registros y cierres reales del mismo contexto con69kg×10, RIR1; uno normal y otro calibrador conservado. Los logs se evalúan en el mismo instante y con idénticos metadatos musculares. Resultado esperado: mismos anillos neural/columna/muscular, mismos drenajes y perMuscle; el drenaje debe ser positivo para evitar una igualdad vacía. La etiqueta calibrador no añade bonificación. Los valores numéricos observados se anotarán tras ejecutar.

`Cal7OneRmRoomJourneyTest` materializa una receta de banca y press militar, migra explícitamente el pool como precondición, abre la hoja real con OverlayHost real y pulsa Guardar y continuar. Comprueba que no pregunta sentadilla/peso muerto, el control48dp, ausencia de persistencia antes del click, valores127,5/82,5 confirmados, auditoría y final del gate, perfil y bolsa1RM/TM actualizados juntos, cargas70%TM de la semana siguiente y principales ajenos conservados. Esta prueba espera la corrección asignada al agente de revisión CAL5b; no se rebaja para aceptar la bolsa obsoleta.

## Matriz gestionada pendiente de dependencias

`Cal7ManagedRoomFixture` prepara recetas publicadas y ejecución COMPLEX real en Room, abre cada sesión mediante el ViewModel, registra todas sus series por el grabador y termina por el controlador real. Verifica identidad de todas las series en el log. No genera logs sintéticos para simular semanas completas. Las cargas accesorias sin kg autor usan una entrada explícita del atleta del fixture40kg, que no se convierte en referencia de capacidad.

Se completarán15 recorridos: `wendler-531-bbb`, `nsuns-531-lp-4d`, `juggernaut-2`, `gzclp` y `apre-mann`, cada uno en OFF/PROPOSE/AUTO. Se elegirá el cierre autor pertinente (ciclo5/3/1, ola Juggernaut, semana nSuns/GZCLP/APRE). Las aserciones incluirán referencia, kilos y series protegidas, propuesta antes de aceptar y efecto después, registro sin cambio automático en OFF y el alcance AUTO real que permita cada receta. No se añaden placeholders verdes ni pruebas omitidas como sustituto de esos recorridos.

## Defectos identificados por lectura y coordinación

- `recordPendingOneRmTestMarks` en la base provisional aún cambia el perfil sin sincronizar el pool de referencias y admite guardar el resultado cuando reconstruir falla. Se contrastó con `u21@cc94b308a`; la revisión independiente confirmó el defecto y el agente principal asignó su corrección de producción a `adaptar_c3`. CAL7 solo aporta el recorrido de regresión.
- La revisión independiente también notificó dos ocurrencias de la misma configuración con TM ausente: confirmar una captura para ambas identidades pero puede dejar la segunda referencia runtime sin hidratar. Espera corrección CAL5b antes de ampliar el recorrido de preparación.

## Evidencia actual

`git diff --check` y revisión de firmas/fixtures únicamente. Sin compilación, resultados de pruebas ni renders generados. Riesgos: base provisional; APIs APRE de S4/propuestas aún se integran; volumen de la matriz exige la cola central única. No se cambió Room/DAO/entidades/esquemas ni se tocó u21/u11.
