# AUD3 · AMRAP y AMRAP calibrador

Auditoría estática de solo lectura, 20 de octubre de 2026. Worktree `C:\kw\u11`, rama `feat/screens-v2`, HEAD consultado `cacde0aaed426e1bec76448097f0de17a2e148d4`. Se examinó el contenido del worktree, incluidos cambios en curso de AUD2; no se modificaron sus archivos. Única escritura de esta auditoría: este documento. No se ejecutaron Gradle, pruebas, adb, la aplicación ni acciones sobre el teléfono; no se hicieron commits.

Las referencias `M/archivo:línea` parten de `android-native/app/src/main/java/com/example/kpkn/`; `T/archivo:línea`, de `android-native/app/src/test/java/com/example/kpkn/`; `IT/archivo:línea`, de `android-native/app/src/androidTest/java/com/example/kpkn/`. Son referencias al código leído, no resultados de ejecución. El editor está siendo modificado concurrentemente: sus números de línea y conclusiones deben revisarse al integrar AUD2.

## 1. Veredicto

| Función | Veredicto | Resultado observable en el código |
| --- | --- | --- |
| AMRAP normal | **Parcial, con infraestructura y efectos reales.** | Registra el resultado real, mínimo, esfuerzo y déficit; participa en rendimiento estimado, PR, historial, sugerencias y AUGE. Puede cambiar el TM y cargas futuras en recetas compatibles. Faltan coherencia entre consumidores, preservar series AMRAP individuales al editar y pruebas de extremo a extremo. |
| AMRAP calibrador | **Vacía como función de calibración diferenciada.** | Hay dos indicadores persistibles y una etiqueta. Ninguno activa una calibración de capacidad o una política propia de carga. Si la serie también está marcada como AMRAP, hereda los efectos del AMRAP normal. Algunas rutas la excluyen de cálculos de planificación/progresión, pero excluirla no constituye calibrar. |

La regla **1,10 / 0,90 solo escribe un mensaje en memoria**. No cambia cargas, TM, referencias de 1RM ni parámetros de RINGS. Además, actualmente ese mensaje no se representa en la tarjeta y compara magnitudes distintas. Evidencia principal: `M/screens/workout/WorkoutSetRecorder.kt:983`, `M/screens/workout/WorkoutV2Body.kt:2194`, `M/screens/workout/components/SetExecutionCard.kt:852`, `M/domain/workout/WorkoutPerformanceHomologationEngine.kt:244` y `:550`.

## 2. Modelo, valores por defecto y persistencia

| Capa | Representación y valores por defecto | Evidencia |
| --- | --- | --- |
| Ejercicio | `trainingMode = REPS`; existe `TrainingMode.AMRAP`. `isCalibratorAmrap = false` es otro campo del ejercicio. Referencias: `reference1RM`, `prFor1RM`, `calculated1RM`. | `M/data/models/Session.kt:290`, `:294`, `:311`, `:319`, `:322`, `:538`. |
| Serie planificada | `isAmrap = false`, `isCalibrator = false`; `intensityMode` nullable, con `IntensityMode.AMRAP`. Reps, rango, peso, RPE, RIR y porcentaje son opcionales. No existe un tipo de calibración con objetivo, alcance o resultado. | `M/data/models/Session.kt:596`, `:600`, `:614`, `:633`, `:664`. |
| Mínimo | No hay `minimumAmrapReps` específico en el plan. Se obtiene de `targetRepsRange.min` o del antiguo `targetReps`. `RepRange` exige mínimo positivo y máximo ≥ mínimo. El ancla general de cargas es el máximo del rango. | `M/data/models/Session.kt:562`, `:576`, `:580`. |
| Registro realizado | `CompletedSet`: peso, reps, RPE/RIR, fallo, modo/valor de intensidad, `amrapPerformed = false`, `amrapMinimumReps = null`, `amrapBelowMinimum = false`, `debt = 0.0`, contexto, payload y resultados de rendimiento. No conserva un propósito «calibrador» propio. | `M/data/models/WorkoutLog.kt:137`, `:160`, `:172`. |
| Payload y evaluación | `RecordedSetPayload.amrapPerformed`; técnica `SetTechniqueV2.AMRAP`; `SetEntryV2.amrapOverride` y resultados con métrica, PR y sugerencia. El payload tampoco contiene `isCalibrator` ni un resultado de calibración. | `M/data/models/WorkoutV2Models.kt:80`, `:175`, `:193`, `:232`; `M/screens/workout/WorkoutSetRecorder.kt:324`, `:326`, `:367`. |

Los indicadores de calibrador son independientes: poner solo `isCalibrator = true` o `isCalibratorAmrap = true` **no activa AMRAP**. La tarjeta reconoce el modo del ejercicio, `set.isAmrap` o su intensidad AMRAP; el recorder resuelve los indicadores de serie y los overrides. Ninguna de estas decisiones consulta los indicadores de calibrador (`M/screens/workout/components/SetExecutionCard.kt:932`; `M/screens/workout/WorkoutFeedbackModels.kt:54`).

Room guarda programas, entrenos y estado en curso como JSON dentro de columnas `data`. `dbJson` ignora claves desconocidas, codifica defaults y permite coerción de valores. Las nuevas propiedades ausentes recuperan sus defaults de los modelos. La base está en versión **29** y la migración **16→17** documenta AMRAP y es deliberadamente un no-op: no altera el esquema SQL ni transforma registros históricos (`M/data/db/Entities.kt:16`, `:57`, `:62`, `:118`; `M/data/db/KpknDatabase.kt:69`, `:432`). No encontré una migración que unifique los dos indicadores de calibrador ni genere un resultado de calibración antiguo.

Al registrar, el recorder construye un `CompletedSet` con las reps realmente introducidas, mínimo y déficit; construye además el payload y el resultado de rendimiento. Espera la persistencia del estado en curso antes de devolver `Created/Updated` (`M/screens/workout/WorkoutSetRecorder.kt:311`, `:441`, `:620`). La persistencia incluye sesión, `completedSets`, cargas dinámicas, borradores y desviaciones (`M/screens/workout/WorkoutPersistenceController.kt:188`, `:196`, `:217`, `:238`). El borrador permite conservar override AMRAP, mínimo, fallo y reserva, como demuestra la prueba de serialización `T/data/models/SessionSerializationTest.kt:318`.

Al finalizar, el log incorpora series, desviaciones y estados contextuales/globales; repositorio inserta log y cambios del programa en una transacción (`M/screens/workout/WorkoutFinishController.kt:364`, `:389`, `:400`; `M/data/repository/ProgramRepository.kt:1027`). El historial final no contiene un snapshot tipado del propósito calibrador ni su decisión aplicada; guardar la sesión entrenada en otra ruta no sustituye ese contrato (`M/data/models/WorkoutLog.kt:6`, `:109`, `:137`; `M/screens/workout/WorkoutFinishController.kt:650`).

## 3. Qué permite configurar el editor

**AMRAP normal.** Se selecciona entre los siete modos del ejercicio. La fila muestra «AMRAP / mín.» o «Reps mín.» y permite editar la cantidad; presenta el mínimo del rango cuando lo hay. Permite tipo de carga externa/corporal/lastre/asistida y los campos de carga de la fila. Hay objetivos por lado en unilaterales. Evidencia: `M/screens/sessioneditor/components/ExerciseOptionsBar.kt:228`; `M/screens/sessioneditor/components/InlineSetRow.kt:183`, `:205`, `:267`, `:365`, `:456`.

La normalización del ejercicio AMRAP pone todas sus series en AMRAP y limpia RPE, RIR, fallo y porcentaje del plan. Por tanto, **el modo no permite prescribir simultáneamente un RIR objetivo AMRAP independiente**; el esfuerzo real se informa durante la sesión. La carga puede calcularse desde una referencia existente; la normalización general prioriza carga calculada cuando la obtiene, por lo que no basta con que el campo permita escribir kg para garantizar que conserve siempre ese valor al guardar (`M/screens/sessioneditor/SessionEditorSessionHelpers.kt:580`, `:653`).

**Calibrador.** No encontré un control que active `isCalibrator` o `isCalibratorAmrap`, ni parámetros de calibración, alcance o aplicación. `AmrapTag` es explícitamente de solo lectura: cambia el texto a «AMRAP calibrador» cuando `set.isCalibrator` ya viene marcado. Copiar configuración conserva ese campo, pero no define un cálculo (`M/screens/sessioneditor/components/InlineSetRow.kt:816`, `:1164`; `M/screens/sessioneditor/SessionEditorViewModelSets.kt:29`). La búsqueda de ambos identificadores en producción solo conduce a modelo, copia, presentación y exclusiones de planificación/progresión.

**Validaciones.** El parser de rangos rechaza cero, negativos, rangos invertidos y textos incompletos. Sin embargo, el commit usa `parsed?.max ?: input.safeIntOrNull()`: cero o un entero negativo pueden pasar como `targetReps` aunque el parser los rechace. `safeIntOrNull` no impone positividad. El guardado valida nombre, límites opcionales de esfuerzo/volumen/patrones e identidad de catálogo; no añade una validación AMRAP de mínimo positivo o coherencia calibrador→AMRAP (`M/domain/training/RepRangeParser.kt:9`; `M/screens/sessioneditor/components/InlineSetRow.kt:374`, `:388`; `M/screens/sessioneditor/SessionEditorUiUtils.kt:13`; `M/screens/sessioneditor/SessionEditorRulesEngine.kt:243`; `M/screens/sessioneditor/SessionEditorViewModelNavigation.kt:449`).

**Pérdida de AMRAP individuales al editar: hallazgo de prioridad alta.** El materializador crea ejercicios REPS/RM y marca determinadas series AMRAP. El editor interpreta el modo del ejercicio como fuente de verdad y limpia la marca AMRAP cuando no es `TrainingMode.AMRAP`. Al guardar una edición de una sesión con contenido modificado se normaliza todo el borrador. Por trazado, una última serie AMRAP de una receta RM/REPS puede perder su condición aunque la edición fuera de otro campo. No ejecuté la UI para reproducirlo. Evidencia: `M/domain/training/PlanMaterializer.kt:1216`, `:1344`, `:1360`; `M/screens/sessioneditor/SessionEditorSessionHelpers.kt:474`, `:598`; `M/screens/sessioneditor/SessionEditorViewModelNavigation.kt:414`. La prueba de cambio de modo valida que se limpie, pero no distingue cambio explícito de modo de simple normalización (`T/screens/sessioneditor/SessionEditorAmrapTest.kt:35`).

## 4. Registro durante la sesión

La tarjeta reconoce AMRAP, toma como sugerencia inicial el mínimo, permite informar carga/reps y exige una marca explícita de esfuerzo. El resultado **no se eleva silenciosamente hasta el mínimo** (`M/screens/workout/components/SetExecutionCard.kt:932`, `:956`, `:2926`, `:3015`). El recorder vuelve a validar el esfuerzo en su propia frontera (`M/screens/workout/WorkoutSetRecorder.kt:160`; `M/screens/workout/WorkoutRecordingEffort.kt:36`).

| Situación | Qué ocurre |
| --- | --- |
| 3 reps con mínimo 4 | Conserva 3; calcula deuda 1; guarda `amrapBelowMinimum = true`; genera `AMRAP_BELOW_MINIMUM`. La evaluación de rendimiento resulta roja por deuda. No implica por sí mismo error de ejecución ni una bajada inmediata de carga. |
| 4 reps con mínimo 4 | Mínimo cumplido, deuda 0. Haberlo cumplido no determina por sí solo un incremento. |
| 12 reps con mínimo 4 | Conserva 12; no aplica techo ni penalización por exceso; deuda 0 y sin desviación `REPS_HIGH` por ese exceso AMRAP. El rendimiento registrado puede alimentar PR y las reglas que correspondan. |
| Sin mínimo válido | Puede registrarse el resultado, pero no existe comparación contra un mínimo ausente. `evaluateRepRange` devuelve null si no hay rango; tampoco genera desviación AMRAP sin rango objetivo. |
| Esfuerzo sin seleccionar | La tarjeta bloquea el registro y muestra la ayuda. El recorder también rechaza un informe sin esfuerzo real. |
| Selección equivalente a RPE 9 | En AMRAP se convierte en reserva **RIR 1**; se conserva modo AMRAP y no se marca fallo. |
| Fallo seleccionado | Guarda `FAILURE`, valor 10, `isFailure = true`; llegar al fallo no convierte automáticamente la serie en error de ejecución. |

Evidencia: `M/screens/workout/WorkoutFeedbackModels.kt:74`, `:232`, `:261`; `M/screens/workout/WorkoutSetRecorder.kt:259`, `:441`, `:475`; `M/domain/workout/WorkoutPerformanceHomologationEngine.kt:75`, `:304`; `M/screens/workout/EffortScale.kt:162`; `M/screens/workout/components/SetExecutionCard.kt:1615`, `:2539`. El recorder admite por separado el caso de cero reps completas con parciales y ciertos registros fallidos; no es un AMRAP mínimo cumplido (`M/screens/workout/WorkoutSetRecorder.kt:237`).

Existe código de una hoja `AmrapConfigSheet` para mínimo/fallo/reserva. **No debe contarse como interfaz accesible**: `showAmrapSheet` se inicia en false y sus demás asignaciones lo ponen en false; no encontré ninguna apertura a true. La hoja, además, admite mínimo 0 al filtrar solo dígitos y aplicar `toIntOrNull`. Los overrides están implementados en datos y helpers, pero esto no demuestra un control vivo para activarlos/desactivarlos en esta tarjeta (`M/screens/workout/components/SetExecutionCard.kt:139`, `:155`, `:167`, `:1225`, `:2335`).

## 5. El calibrador, seguido hasta sus consumidores

### La regla 1,10 / 0,90

El bloque del recorder hace exactamente esto:

1. Se ejecuta ante **cualquier** `amrapActive`, sin consultar `isCalibrator` ni `isCalibratorAmrap`.
2. Lee la EWMA contextual previa del estado capturado al entrar al registro.
3. Calcula `amrapVolume = weight * actualValue` y `ratio = amrapVolume / ewma`.
4. Con ratio **> 1,10** o **< 0,90**, construye el texto «Sugerencia: aumentar/reducir carga». En los límites exactos no genera mensaje.
5. Solo asigna `amrapCalibrationMessage`; no devuelve ni persiste una nueva carga o referencia. Si no hay histórico positivo tampoco produce resultado.

Evidencia: `M/screens/workout/WorkoutSetRecorder.kt:983`–`:998`.

**Problema dimensional.** La EWMA contextual se actualiza con `computeMetric`: para carga externa por reps es e1RM, para corporal son reps y para asistido es un índice propio. No es una media de volumen (`M/domain/workout/WorkoutPerformanceHomologationEngine.kt:45`, `:244`, `:503`, `:539`). El numerador también usa la carga introducida: en corporal es 0; en asistido mide asistencia, no trabajo efectivo. Por eso los umbrales carecen de una comparación homogénea.

Ejemplo aritmético derivado del código, sin ejecutar la app: 100 kg × 8 reps da e1RM AMRAP 127,2 kg con la fórmula vigente. Con una EWMA histórica idéntica de 127,2, repetir exactamente la serie produce `800 / 127,2 = 6,289…`, y el texto sería **«AMRAP: +528% vs histórico. Sugerencia: aumentar carga.»** No hubo mejora. Fórmula: `M/domain/calculations/Calculations.kt:60`; comparación: `M/screens/workout/WorkoutSetRecorder.kt:986`.

**Mensaje sin consumidor visual.** Se declara en `WorkoutUiState`, se escribe en el recorder y se pasa desde `WorkoutV2Body` a la tarjeta. Dentro de `SetExecutionCard.kt` solo aparece como parámetro: no se lee ni se dibuja. Tampoco hay un resultado de calibración equivalente en los modelos persistidos. No basta con «conectar el mensaje»: primero hay que sustituir la comparación (`M/screens/workout/WorkoutUiModels.kt:267`; `M/screens/workout/WorkoutV2Body.kt:2194`; `M/screens/workout/components/SetExecutionCard.kt:852`).

### Matriz de efectos reales

| Consumidor | AMRAP normal | Qué aporta marcar además «calibrador» | Evidencia |
| --- | --- | --- | --- |
| Siguientes series de la sesión | Tras guardar se refrescan sugerencias. El motor compartido usa series realizadas, capacidad, fatiga, base manual e historial. Puede cambiar una sugerencia; no garantiza cambio si manda el plan o hay override. | Ninguna política adicional. La regla 1,10/0,90 no participa. | `M/screens/workout/WorkoutSetRecorder.kt:639`; `M/screens/workout/WorkoutLoadSuggestionController.kt:86`, `:533`, `:611`, `:681`. |
| Próxima sesión | El historial puede aportar `homologatedResultV3.suggestedNextLoad`; se obtiene la serie correspondiente del último entreno. También influye en las reglas del programa compatibles. | No crea referencia aceptada ni prescripción futura de calibración. | `M/screens/workout/WorkoutViewModel.kt:7240`, `:7288`; `M/domain/workout/LoadSuggestionEngine.kt:149`. |
| e1RM y PR contextual/global | Sí: la evaluación calcula métrica, estadísticas y PR, y guarda el resultado en la serie y la caché. Flags de evaluación activos por defecto. | No modifica el cálculo ni lo valida especialmente. | `M/screens/workout/WorkoutSetRecorder.kt:396`, `:473`; `M/screens/workout/WorkoutViewModel.kt:1586`; `M/data/models/WorkoutV2Models.kt:6`; `M/domain/workout/WorkoutPerformanceHomologationEngine.kt:48`, `:268`, `:550`. |
| 1RM declarado y TM del perfil | El registro no escribe automáticamente un nuevo 1RM declarado. El motor semanal puede modificar TM bajo condiciones de receta/modo. | Ningún ajuste propio de referencia, 1RM o TM. | `M/domain/training/ProgramAutoregulationEngine.kt:825`, `:840`, `:1194`; `T/domain/training/ProgramAutoregulationEngineTest.kt:398`. |
| Carga inteligente del editor | Puede recuperar capacidad y sugerencia del historial, y calcular kg según el modo y objetivo. No siempre reemplaza una referencia existente. | No hay rama calibradora ni alcance explícito. | `M/screens/sessioneditor/SessionEditorSessionHelpers.kt:507`, `:541`; `M/domain/calculations/Calculations.kt:219`, `:237`, `:302`. |
| RINGS/AUGE | Cuenta el esfuerzo y las reps reales en fatiga, descanso y recuperación. Puede contribuir al aprendizaje general de recuperación si cumple sus condiciones. | No recalibra tanques ni recuperación por la etiqueta o el ratio. | `M/screens/workout/WorkoutSetRecorder.kt:862`, `:964`; `M/domain/auge/AugeFatigueEngine.kt:249`, `:412`; `M/screens/auge/AugeViewModel.kt:885`. |

El cierre guarda cachés de rendimiento y snapshots como trabajo posterior al guardado principal; las escrituras contextuales/globales se delegan al repositorio. Esta infraestructura existe, pero no constituye una transacción de calibración diferenciada (`M/screens/workout/WorkoutFinishController.kt:639`; `M/data/repository/ProgramRepository.kt:1794`).

Los usos adicionales del flag de serie son administrativos: el resumen del plan no cuenta calibradores como series ordinarias, el materializador los conserva al ajustar series de trabajo, la progresión nativa los excluye y la descarga limpia la marca. Son efectos reales de planificación, sin estimación ni aplicación de calibración (`M/screens/programdetail/PlanDetailsSummary.kt:260`; `M/domain/training/PlanMaterializer.kt:1145`; `M/domain/training/NativeWorkoutProgressionRuntime.kt:1007`; `M/domain/training/BlockTransitionEngine.kt:306`). El flag del ejercicio `isCalibratorAmrap` no tiene un consumidor operativo de calibración en la producción examinada.

## 6. AMRAP normal: integración útil y límites

**Rendimiento y récords.** Para LOAD y LASTRE, la homologación usa carga normalizada y `calculateHybrid1RM(..., isAmrap = true)`. La fórmula es Brzycki hasta 10 reps, Epley entre 11 y 20 y extrapolación posterior; aplica un **+2,5% adicional cuando AMRAP tiene más de 3 reps**. Es una heurística implementada: el código citado no ofrece una justificación externa para ese bonus (`M/domain/calculations/Calculations.kt:60`; `M/domain/workout/WorkoutPerformanceHomologationEngine.kt:503`, `:550`). Con corporal/asistido se usan métricas diferentes y la homologación evita presentarlas como e1RM en kg (`:99`, `:544`).

El mismo AMRAP sí entra en el historial y los récords genéricos. Sin embargo, historial de ejercicio, hitos de PR, curvas de cierre y snapshots recalculan `calculateHybrid1RM(weight,reps)` **sin** la marca AMRAP. Así pueden coexistir 127,2 kg en homologación y 124,1 kg en historial/PR para 100 × 8. `LoadSuggestionEngine.estimatedCapacity` prefiere la estimación homologada, pero su fallback tampoco pasa la marca. No hay una capacidad AMRAP única consumida por todas las pantallas (`M/screens/workout/WorkoutViewModel.kt:5975`, `:7084`; `M/screens/workout/WorkoutFinishPresentation.kt:57`; `M/screens/workout/PerformanceRangeStore.kt:61`; `M/domain/workout/LoadSuggestionEngine.kt:94`).

**Sugerencias.** La homologación no trata un déficit como una calibración de carga: con deuda conserva la carga en su rama principal; para rendimiento favorable de carga externa puede proponer factores 1,025 o 1,015. Son reglas distintas del mensaje 1,10/0,90 (`M/domain/workout/WorkoutPerformanceHomologationEngine.kt:304`, `:355`). Las sugerencias vivas añaden su propia lógica y pueden priorizar plan/base manual. Si el plan tiene peso o capacidad calculable, `shouldRespectPlan` anula el factor de mejora de sesión. Los ejercicios gestionados por progresión nativa desactivan la sugerencia genérica (`M/screens/workout/WorkoutLoadSuggestionController.kt:539`, `:655`; `M/domain/workout/LoadSuggestionEngine.kt:17`). Por tanto, «registrar más reps» no promete una subida inmediata ni una edición del programa.

**Progresión del programa: sí hay un circuito con cambios reales.**

- El cierre pasa historial y señales semanales a `ProgramProgressEngine`; este llama a `ProgramAutoregulationEngine` si hay receta y autorregulación habilitada (`M/data/repository/ProgramRepository.kt:963`, `:1118`; `M/domain/training/ProgramProgressEngine.kt:1128`).
- `collectAmrapHits` busca AMRAP planificados y realizados, filtrando ocurrencia/ciclo/run y levantamiento de receta. No todo AMRAP suelto tiene un TM que modificar (`M/domain/training/ProgramAutoregulationEngine.kt:553`, `:1016`).
- `AmrapDrivenTm` usa la tabla de la receta y exige porcentaje conocido ≥85% del TM para subir; la tabla por defecto propone 0 / 2,5 / 5 / 7,5 kg según reps. `RepTargetDrivenTm` usa sus porcentajes por reps extra/faltantes. Un AMRAP corto puede reducir TM o congelar el incremento de autor. Una receta `None` sin hook `AMRAP_TM` no cambia TM (`M/domain/training/ProgramAutoregulationEngine.kt:1061`, `:1085`, `:1110`; `T/domain/training/ProgramAutoregulationEngineTest.kt:247`, `:347`).
- OFF no propone; PROPOSE deja una acción pendiente; AUTO aplica con auditoría, o deja pendiente si falta el proveedor necesario. Cambiar TM actualiza el perfil y rematerializa la semana futura, protegiendo sesiones ejecutadas. No reemplaza el 1RM declarado (`M/domain/training/ProgramAutoregulationEngine.kt:155`, `:198`, `:825`, `:872`; `T/domain/training/ProgramAutoregulationEngineTest.kt:378`).
- `BlockTransitionEngine` por sí mismo decide avance/descarga/test a partir de completitud, AUGE, estrés y objetivo. No compara el rendimiento AMRAP. Si debe generar el siguiente bloque, usa `BlockProgressionEngine`, que prescribe por objetivos/esquema, sin recibir series realizadas. La congelación por AMRAP corto en cierres está en la orquestación de `ProgramProgressEngine`, no en un calibrador (`M/domain/training/BlockTransitionEngine.kt:124`, `:137`, `:205`; `M/domain/training/BlockProgressionEngine.kt:66`, `:226`; `M/domain/training/ProgramProgressEngine.kt:696`, `:1267`).
- La otra progresión automática, `NativeWorkoutProgressionRuntime`, excluye del trabajo elegible los AMRAP, calibradores y otras técnicas, y descarta exposiciones con AMRAP realizado en vivo. No debe afirmarse que toda progresión automática consume AMRAP (`M/domain/training/NativeWorkoutProgressionRuntime.kt:1005`, `:1018`, `:1117`).

**Dos incoherencias entre registro y progresión que impiden llamarlo completo:**

1. **Mínimo frente a máximo.** El registro usa `effectiveRepRange().min`, pero `collectAmrapHits` elige primero `prescribed.targetReps`, después rango mínimo y finalmente mínimo realizado. Como el editor refleja el máximo en `targetReps`, un AMRAP rango 4–6 con 5 reps cumple el mínimo en vivo, pero el motor puede considerarlo corto contra 6. Tampoco prioriza una modificación del mínimo informada durante la sesión (`M/screens/workout/WorkoutSetRecorder.kt:441`; `M/data/models/Session.kt:557`; `M/domain/training/ProgramAutoregulationEngine.kt:574`, `:1073`).
2. **Overrides e identidad.** `pairedSets` solo empieza si hay series planificadas objetivo. Un AMRAP activado sobre una serie ordinaria puede quedar fuera. A la inversa, si una serie planificada AMRAP se desactiva en vivo, no hay realizadas marcadas pero el fallback por posición puede volver a considerarla AMRAP cuando el log tiene todas las series. El fallback no distingue un log antiguo sin información de un payload moderno que informa explícitamente AMRAP desactivado (`M/domain/training/ProgramAutoregulationEngine.kt:569`, `:749`; helpers de override en `M/screens/workout/WorkoutFeedbackModels.kt:54`). Son consecuencias del trazado estático; faltan pruebas de integración de ambos escenarios.

**RINGS.** El esfuerzo AMRAP con reserva llega a `rir`; AUGE lo convierte a RPE efectivo. Fallo y reps alteran el drenaje y la recuperación como en cualquier serie real. Existe además aprendizaje general desde rendimiento (`PerformanceTauLearner`), con comparabilidad de esfuerzo, historial mínimo y otras condiciones; puede ajustar horas de recuperación al procesar el entreno reciente, no simplemente por pulsar registrar. Su eRM vuelve a calcularse sin bonus AMRAP. No encontré una rama especial del calibrador en este circuito (`M/domain/auge/AugeFatigueEngine.kt:249`, `:412`; `M/domain/auge/AugeRecoveryEngine.kt:435`; `M/domain/auge/PerformanceTauLearner.kt:56`, `:417`, `:441`; `M/screens/auge/AugeViewModel.kt:865`, `:885`, `:898`).

## 7. Pruebas existentes y huecos

Todas las siguientes son **pruebas leídas, no ejecutadas**. Su existencia no certifica que pasen en este worktree.

| Comportamiento | Cobertura existente comprobada | Hueco relevante |
| --- | --- | --- |
| Modelo y compatibilidad | `T/data/models/SessionSerializationTest.kt:19`, `:165`, `:245`, `:276`: defaults legacy y flag `isCalibrator` conservado en sesión. `:318`, `:356`: borrador AMRAP con mínimo/fallo/reserva. | No prueba que el calibrador haga algo. No encontré una prueba específica de migración 16→17 AMRAP ni round-trip Room de una calibración aplicada. |
| Selección y editor | `T/screens/sessioneditor/SessionEditorAmrapTest.kt:15`, `:35`: normalización al elegir/salir del modo. `T/screens/sessioneditor/components/ExerciseOptionsBarComposeTest.kt:40`: siete modos. `T/screens/sessioneditor/components/ExerciseSetListComposeTest.kt:101`: controles AMRAP. | Sin activación de calibrador; sin preservar AMRAP individual de una receta RM/REPS al editar otro dato. |
| Parser y mínimo | `T/domain/training/RepRangeParserTest.kt:1`; `T/screens/workout/PlannedIntensityPolicyTest.kt:39`, `:96`: sin techo AMRAP y desviación bajo mínimo. | Sin comprobar toda la ruta UI→recorder→Room→finalización con reps inferiores/superiores. No encontré referencias a `amrapBelowMinimum` en pruebas. Validar el parser no prueba el fallback numérico del commit. |
| Overrides | `T/screens/workout/PlannedIntensityPolicyTest.kt:186`, `:198`, `:205`, `:212`: helper activa/desactiva y detecta desviación según override. | No cubren cómo `collectAmrapHits` interpreta el registro después ni la accesibilidad del control en vivo. |
| Esfuerzo real | `T/screens/workout/EffortScaleTest.kt:169`; `T/screens/workout/components/SetExecutionCardEffortTest.kt:392`, `:407`: reserva y fallo AMRAP. `:293`: bloqueo sin esfuerzo. | Las pruebas de tarjeta usan callback de registro; no verifican persistencia/evaluación completa de ese AMRAP. |
| Guardado durable | `T/screens/workout/WorkoutSetRecorderDurableRoomTest.kt:150`, `:169`, `:185`: reapertura, fallos/reintento y actualizaciones. | La cobertura es genérica; no comprueba una identidad calibradora, sus efectos futuros ni la diferencia normal/calibrador. |
| Rendimiento/PR y carga | `T/domain/workout/WorkoutPerformanceHomologationEngineTest.kt:20`, `:40`, `:346`, `:410`; `T/domain/workout/LoadSuggestionEngineTest.kt:36`, `:90`; `T/domain/calculations/SmartLoadModeSuggestionTest.kt:14`, `:35`, `:51`. | Sin contraste del mismo AMRAP entre estimación homologada, historial, PR, editor y snapshot; sin prueba específica del bonus AMRAP y de reserva real en todos esos consumidores. |
| TM y cargas futuras | `T/domain/training/ProgramAutoregulationEngineTest.kt:234`, `:247`, `:273`, `:285`, `:378`, `:475`, `:524`, `:538`, `:779`: tabla, límites, modos, aplicación, ciclo y fallback legacy. | Faltan rango mínimo frente a máximo, mínimo cambiado, AMRAP desactivado explícitamente y activación viva sobre serie ordinaria. |
| Cierres del programa | `T/domain/training/ProgramProgressCycleCloseTest.kt:831`, `:860`, `:877`, `:1093`: congelación por AMRAP corto y aislamiento de ciclos/bloques. `T/domain/training/BlockTransitionEngineTest.kt:135`, `:193`: descarga y variantes. | No prueban una calibración ni convierten el motor de transición en un consumidor directo de rendimiento AMRAP. |
| RINGS/aprendizaje | `T/domain/auge/AugeRingCharacterizationTest.kt:32`; `T/domain/auge/PerformanceTauObservationTest.kt:45`, `:58`, `:70`, `:120`, `:175`: fatiga/recuperación y aprendizaje condicionado. | Cobertura general, sin comparar la misma serie como normal/AMRAP/calibrador y comprobar que solo cambie lo definido por su esfuerzo o política. |
| Mensaje 1,10/0,90 | No encontré pruebas que referencien `amrapCalibrationMessage`. | Sin unidades homogéneas, límites, ausencia de histórico, consumidor visual, persistencia o aplicación. |

Las pruebas instrumentadas de `IT/screens/workout/components/SetExecutionCardUiTest.kt:464` y `:534` preservan el mínimo dentro de un payload guiado/reintentado; no demuestran calibración. No se ejecutaron ni se usó dispositivo.

## 8. Propuesta concreta de funciones diferenciadas

Lo siguiente es una **propuesta de producto e implementación**, no comportamiento existente ni una prescripción de entrenamiento. Reutiliza cálculos del repositorio; las decisiones nuevas se identifican expresamente.

### Contrato común

Representar el propósito a nivel de serie: AMRAP normal o AMRAP calibrador. Unificar los dos flags legacy con una conversión documentada y defaults compatibles, y guardar el propósito realizado en `CompletedSet`/payload. Cambiar explícitamente el modo debe diferenciarse de normalizar una receta mixta. Conservar siempre mínimo efectivo, carga original y normalizada, lado, equipo/contexto, reps completas y reserva/fallo realmente elegidos.

Usar un único mínimo efectivo en UI, registro y progresión. Un entero mínimo configurado debe ser positivo; vacío puede significar «sin mínimo», de forma explícita. Bajo el mínimo se guarda el resultado real y se informa «mínimo no alcanzado». Los consumidores modernos deben respetar AMRAP desactivado; el fallback por posición se reserva para logs legacy sin evidencia suficiente.

### AMRAP normal

Su función sería **registrar una serie de rendimiento abierto y aportar evidencia al historial y a la progresión que el programa haya elegido**. Debe mostrar mínimo, reps logradas y reserva/fallo, y explicar el efecto: «registrado», «sin regla de progresión», «TM pendiente de aceptar» o «TM aplicado». No toda serie tiene que cambiar una carga para ser útil.

Centralizar capacidad, PR e historial en una misma estimación. Propongo reutilizar el híbrido actual **sin el +2,5% automático por etiqueta**, ya utilizado por historial y compatible con la inversa de cargas. Es una decisión propuesta para eliminar discrepancias y evitar atribuir más capacidad solo por cambiar la etiqueta; no una afirmación de validación científica. Si se decide conservar el bonus, debe versionarse y aplicarse igual en todos los consumidores, con compatibilidad del histórico. No sumar automáticamente RIR a las reps sin una política explícita y comprobada (`M/domain/calculations/Calculations.kt:60`, `:74`, `:90`).

La receta conserva la autoridad sobre TM y semanas futuras, con los actuales OFF/PROPOSE/AUTO. La progresión nativa debe seguir excluyendo AMRAP hasta que exista un contrato específico; la interfaz debe explicar esta exclusión en vez de prometer que cualquier AMRAP cambia el programa.

### AMRAP calibrador

Su función sería **estimar capacidad actual y producir una decisión revisable sobre cargas concretas**, además de guardar la serie normal. Flujo propuesto:

1. En el editor se elige calibrador, mínimo, carga/tipo y alcance: resto del ejercicio en esta sesión o propuesta para próximas sesiones. Debe poder existir una serie calibradora seguida de trabajo ordinario.
2. Al registrar se conserva el resultado aunque no sea apto para calibrar. Para un primer alcance verificable propongo aceptar calibración de carga externa con reps completas, sin error/parciales/ayuda, esfuerzo explícito y reserva 0–1 o fallo reportado. El límite de reserva es una política propuesta para reducir incertidumbre, no un umbral actualmente validado. En otros casos mostrar «registrado; calibración no aplicada» con motivo. Corporal y asistido usan sus métricas específicas; no fabricar un e1RM en kg.
3. Calcular `C`, capacidad actual, con el estimador común. Compararla con `B`, referencia previa del mismo ejercicio/configuración, carga/unidad y lado. Una referencia declarada puede servir como arranque; no comparar volumen con e1RM ni usar un récord de otro contexto.
4. Reutilizar, como primera política conservadora, la lógica existente de `computeSessionImprovementAdjustment`: `r=C/B`; desde `r>=1,025`, factor `min(r,1,05)`; hasta `r<=0,97`, factor `max(r,0,92)`; en medio, factor 1. Es una **reutilización propuesta** de `M/screens/workout/WorkoutLoadSuggestionController.kt:681`, no una fórmula nueva ni una garantía de adecuación. Aplicar el factor a la carga base de las series pendientes compatibles, redondeando con equipo real, sin acumularlo de nuevo sobre una carga ya ajustada.
5. Sin `B` válido, informar «primera referencia» y proponer cargas a partir de `C` con `calculateWeightFrom1RMAndIntensity`/`calculateWeightFrom1RM` para trabajo compatible. No mostrar porcentaje de mejora sin histórico. Distinguir %1RM de %TM; en planes gestionados no reinterpretar TM como 1RM ni reemplazar la receta mediante una regla genérica.
6. Mostrar una decisión concreta: referencia anterior/actual, series afectadas, kg antes→después y motivo; permitir aplicar o conservar. Respetar cargas manuales bloqueadas y series ya realizadas. Para programas gestionados, canalizar el cambio futuro por su sistema de propuestas y autoridad de progresión.
7. Persistir una decisión tipada con serie y revisión de origen, contexto, capacidad, referencia, alcance, cargas propuestas y resolución. Aplicar una vez; editar, reintentar o revertir no debe duplicar cambios. El siguiente entreno debe consumir la última referencia aceptada de ese contexto, sin confundir ajuste de sesión con actualización del 1RM declarado.
8. RINGS sigue contando el trabajo y esfuerzo reales. El aprendizaje general de recuperación puede aprovechar esa evidencia cuando cumpla sus reglas. La etiqueta calibrador no debe por sí sola rellenar anillos o modificar tiempos de recuperación.

Así las funciones tendrían resultados distintos y comprobables: el AMRAP normal **documenta rendimiento y alimenta la regla del plan**; el calibrador **produce y guarda una calibración con cargas afectadas y resolución explícita**.

## 9. Trabajo necesario, ordenado por valor

Tamaños orientativos: **S**, cambio local; **M**, varios componentes con pruebas de integración; **L**, estado durable y coordinación entre registro, historial y programa. Cada entrega debe incluir sus pruebas significativas; aquí no se implementó ninguna.

| Orden | Entrega | Función | Tamaño | Criterio de aceptación |
| --- | --- | --- | --- | --- |
| 1 | Corregir contrato de serie/propósito y mínimo; preservar AMRAP individual al editar y respetar overrides modernos en la progresión. | Ambas | M | Una receta RM con última serie AMRAP sobrevive a editar otro campo. 5 reps con mínimo 4 nunca se consideran cortas por el máximo legacy 6. Un AMRAP desactivado no reaparece por fallback. |
| 2 | Implementar evaluación calibradora tipada y reemplazar la comparación volumen/EWMA. | Calibrador | M | Serie idéntica a su referencia no anuncia mejora; produce decisión cuantificada, primera referencia o motivo de no aplicación. Solo propósito calibrador activa esta evaluación. |
| 3 | Incorporar controles accesibles de normal/calibrador, mínimo y alcance; resolver hoja muerta y resultado sin consumidor. | Ambas | M | Se crea una calibradora seguida de trabajo normal y la persona puede ver qué cargas cambiarían y elegir. Se valida mínimo y reserva sin fabricar esfuerzo. |
| 4 | Conectar calibración aceptada con cargas pendientes y próximas sesiones, autoridad del plan, persistencia, reintento y reversión. | Calibrador | L | Aplicar cambia las cargas identificadas; reiniciar conserva la decisión; reintentar no multiplica el factor; el siguiente entreno usa lo aceptado. OFF/PROPOSE/AUTO y cargas manuales se respetan. |
| 5 | Unificar capacidad/e1RM, PR, historial, editor y snapshots; decidir/versionar el bonus AMRAP. | Ambas | M | La misma serie/contexto ofrece la misma capacidad en todos los consumidores. Corporal y asistencia no aparecen como e1RM de barra. |
| 6 | Añadir validación semántica del plan y mensajes del efecto real de la progresión. | Ambas | S | Cero/negativos no pasan como mínimo válido; cada resultado explica si solo se registró, si hay propuesta o si cambió una carga/TM. |
| 7 | Cerrar cobertura de extremo a extremo y caracterizar el efecto sobre RINGS. | Ambas | M | Casos bajo/igual/sobre mínimo, reserva/fallo, sin histórico, rangos, lados, cambios en vivo, guardado/reapertura, aceptación/rechazo y modos de programa. RINGS cuenta la carga real y no aplica una bonificación ficticia por etiqueta. |

Para declarar **completo el AMRAP normal** hacen falta las entregas 1, 5, 6 y su parte de 3/7, conservando la progresión condicional que ya funciona. Para declarar **completo el calibrador** hacen falta además evaluación, aplicación durable y consumo futuro verificable: entregas 2–4. Hacer visible el texto actual de 1,10/0,90 por sí solo no satisface el encargo.
