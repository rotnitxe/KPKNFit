# Métricas de Program Detail

La pantalla responde «¿voy bien y por qué?». Cada cifra debe identificar su ventana, su procedencia y la decisión que puede explicar. Una semana futura no es un incumplimiento y una estimación no es una marca medida.

| Módulo | Qué muestra | Fuente | Para qué sirve | Detalle que vive en otra vista |
|---|---|---|---|---|
| Posición | Alcance, bloque, semana actual, ciclo y próxima descarga o test | `ProgramProgressReport.position`, `ProgramScopes`, cursor persistido | Saber dónde se está dentro del plan | Calendario y estructura: pestaña Macrociclo |
| Cumplimiento | Sesiones obligatorias registradas respecto a las planificadas hasta la semana actual; sesiones de la semana actual | `ProgramProgressReport.adherencePercent` y `weeks` | Distinguir falta de entrenamiento de falta de progreso | Ejercicios omitidos: `ProgramAnalyticsReport.adherence` |
| Fuerza | Hasta tres tendencias de e1RM, con el mismo cálculo ya usado en el análisis | `ProgramAnalyticsReport.progression` a través de `ProgramProgressReport.lifts` | Mostrar cambios observados sin prometer un test de 1RM | Progreso por ejercicio y récords: MFP T5b |
| Decisiones | Fecha, origen, título y motivo de cambios y propuestas; últimas 50 entradas | `ProgramProgressReport.decisions` | Explicar cambios del plan y conservar trazabilidad | TM por ejercicio y su historial: CAL5b |
| Volumen | Series y reparto muscular del análisis | `ProgramAnalyticsReport.coverage` y pestaña Volumen | Comprender cobertura y balance | Volumen por ventana: MFP T5a; no se replica aquí |
| Entrenamiento registrado | Hechos originales, contexto y cobertura disponible | `TrainingHistoryProjection`, `TrainingInsightsEngine` | Comparar registros compatibles con su procedencia y ventana | Historial de entrenamiento; sin calcular recuperación ni estados fisiológicos |
| Referencias de carga | TM/1RM y origen por ejercicio | CAL5b | Entender de qué marca sale cada carga | Herramientas de marcas y test: CAL5/CAL5b |

## Tarjeta mínima y decisiones

La tarjeta «Progreso del programa» se sitúa entre la información del programa y las pestañas. Muestra posición y próximo hito, cumplimiento, sesiones de la semana actual y número de decisiones, y hasta tres tendencias de fuerza. Si el informe no existe todavía, no se muestra una cifra provisional.

«Decisiones» abre una hoja también disponible desde «Más opciones → Decisiones del sistema». Ordena de más reciente a más antigua las decisiones propias, propuestas aplicadas de la receta, auditoría de autorregulación y progresión nativa aplicada. Las propuestas de check-in y revisión de bloque que tienen su propia decisión no se duplican. Sin entradas: «Todavía no hay decisiones registradas».

El cumplimiento filtra programa y ejecución; en ciclos también filtra el ciclo registrado. Cuenta cada sesión obligatoria una sola vez. Los registros opcionales, duplicados o de otro programa no inflan la cifra. Las semanas futuras quedan fuera del denominador.

## Dependencias de otras olas

- CAL5/CAL5b ya integrados: reglas de AMRAP/TM, test de 1RM, origen e historial de referencias por ejercicio; conservar sus contratos.
- MFP T3 integrado: `ProgramAnalyticsEngine` usa `StrengthModel`; el informe reutiliza sus tendencias sin introducir otra fórmula.
- MFP T4/T5a/T5b integrados: reutilizar objetivo automático, volumen por ventana y progreso por ejercicio, sin duplicar esos paneles.
- AUGE/RINGS retirados: no hay snapshot, readiness ni proyección de recuperación que integrar. Las decisiones actuales se identifican como decisiones del protocolo.
- Screens-v2 integrado: mantener su composición y sus herramientas de carga; la tarjeta añade el informe de progreso.

## Ajustes necesarios de integración

T-50 adelanta de T-21 exclusivamente `ProgramDecision` y `Program.programDecisions` con default. Su nuevo dato semanal se llama `ProgramWeekAdherence`: el nombre `WeekAdherence` ya está ocupado por otro contrato y permanece intacto. Las métricas de músculo por semana y de revisión de bloque se incorporarán desde sus motores específicos, sin duplicar el análisis de volumen.
