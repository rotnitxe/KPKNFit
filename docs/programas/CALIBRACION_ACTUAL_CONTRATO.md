# Evidencia y objetivos de volumen (adaptación T-90–94)

La orden del 25-oct-2026 sustituye las deducciones del plan del 22-oct por hechos registrados y elecciones explícitas. Este contrato guía la implementación; no acredita código o QA terminado.

| Tarea original | Contrato vigente |
|---|---|
| T-90, respuestas DERIVED de técnica/fuerza/movilidad | Resumen de registros de una ventana explícita: cantidad de sesiones registradas, semanas con registros, cobertura sólo con denominador planificado conocido y procedencia. No asignar respuestas ni puntuaciones a partir de vídeo, dolor, RIR o peso corporal. |
| T-91, MRV aprendido por check-in | Rangos de planificación elegidos o propuestos desde respuestas semanales. La propuesta usa pasos de hasta 2 series y una envolvente editorial de ±40 %, sin atribuirle capacidad fisiológica medida. Su aceptación es una elección explícita, con grupo, rango, fecha, nota y procedencia. |
| T-92, recalibración automática | Conversión de objetivos explícitos a límites para propuestas confirmables. Las tablas generales son referencias editoriales. Las respuestas originales se conservan; los perfiles antiguos no se presentan como capacidad fisiológica medida. |
| T-93, tarjeta y eliminación de puerta previa | Tarjeta de evidencia y objetivos. Editor manual con valores visibles y acción Guardar. Crear no exige responder un cuestionario de rasgos; un punto de partida sugerido se etiqueta como tal. |
| T-94, aprendizaje al cerrar ciclo/bloque | Resumen y propuestas de planificación del ciclo cerrado. No modificar límites al cerrar: mostrar antes/después y motivo; confirmar o descartar. La revisión de bloque puede enlazar esta misma revisión y el editor explícito. |

## Identidad y evidencia

El resumen recibe fecha de corte y ventana como parámetros; no consulta el reloj desde el dominio. Se filtra por programa y ejecución. No se suman dos veces registros duplicados y no se cuentan registros de otro run como cumplimiento actual. Fechas inválidas o futuras no aportan cobertura. Si el calendario no da un denominador comparable, se muestran registros, sin inventar una tasa de cumplimiento.

Ausencia de registros significa ausencia de registros disponibles del programa. No afirma inactividad, descanso, pérdida de capacidad o una movilidad determinada. Las molestias y respuestas se conservan como declaraciones originales, separadas de las metas de series.

## Confirmación

Elegir un rango sólo cambia el objetivo de planificación. No reescribe sesiones ejecutadas ni aplica ajustes a la prescripción por sí solo. Una propuesta semanal usa el objetivo elegido y explica el cambio antes de la confirmación posterior. La persistencia protege la instantánea revisada frente a ediciones o sesiones iniciadas entre revisión y confirmación.

Las propuestas del ciclo conservan la matriz del plan: dos respuestas consecutivas de rendimiento 4/4 con dosis planificada al menos en la referencia intermedia → proponer hasta −2 en el máximo; cinco respuestas consecutivas de rendimiento y agujetas 1–2/4 cerca del máximo elegido → proponer hasta +2. Se usa la receta efectiva de la semana a la que se refiere la respuesta, con ejecución y ciclo conocidos. Las respuestas sin esa identidad se conservan pero no generan esta propuesta. El máximo no salta fuera de la envolvente editorial ni fuerza a volver a ella un rango manual diferente.

La petición distingue la semana completada de la semana de destino. Al entrar en descarga se recoge la respuesta de la última semana de entrenamiento, conservando la descarga sin ajustes. Cada propuesta tiene identidad por ejecución/ciclo/grupo; al confirmar o descartar se registra su decisión y no se vuelve a ofrecer en ese ciclo. La vista previa caduca ante cambios del programa o de los datos usados para calcularla. Los rangos son del programa y no se transfieren implícitamente a programas con otra finalidad.

La nota de la hoja histórica se transporta tras su callback original de tres valores mediante un callback de composición en ProgramHeroWidgets. ProgramDetail conserva la nota sobre el último programa y en el perfil guardado; no se modifica VolumeView ni sus cálculos. Las notas propias de un rango se guardan dentro del objetivo, separadas del texto original del perfil.

Los protocolos de autor conservan su receta. Los objetivos propios no convierten una receta de autor en un programa KPKN ajustable. No se añaden variantes, puntuaciones de recuperación ni contratos nuevos con los nombres de motores retirados.

## Transporte entre plataformas

Program.swift ya transporta los campos desconocidos mediante opaqueFields y JSONValue; weeklyCheckins, pendingWeeklyCheckin, programDecisions, planningVolumeTargets y volumeCalibrationNotes se conservan por esa vía sin interpretarlos como un motor de iOS. AlgorithmSettings incorpora weeklyCheckinEnabled opcional para conservar false explícito al transportar JSON; la ausencia heredada conserva el default Android true. No hay compilador Swift disponible en este host: este cambio de transporte requiere validación nativa posterior y no acredita paridad de interfaz o motores iOS.

Los WorkoutLog y sus observaciones originales no reciben campos nuevos en esta ola. Los campos nuevos de Program son JSON aditivo; no hay cambios de tablas o de versión Room. El backend de análisis no se usa para recalcular respuestas ni rangos de planificación.
