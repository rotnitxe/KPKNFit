# AUD1 · Motores del editor y del catálogo

Fecha: 20 de octubre de 2026. Worktree: C:/kw/u11. Rama: feat/screens-v2. HEAD leído: cacde0aaed426e1bec76448097f0de17a2e148d4.

## Alcance y método

Se ejecutó el encargo de docs/screens-v2/briefs/AUD1.md y se respetaron las decisiones de DECISIONES_PROTOTIPOS_2026-10-20.md. Esta es una auditoría de solo lectura del código, datos y pruebas existentes. El único archivo creado o modificado por este trabajo es docs/screens-v2/audits/AUD1_motores_editor.md. No se ejecutaron Gradle, adb, emuladores, teléfono, pruebas JVM ni commits. No se consultó ni modificó el estado del dispositivo.

La evidencia combina lectura de Kotlin con análisis de JSON y réplicas deterministas de las funciones puras mediante Python -I, enviadas por entrada estándar y mantenidas en memoria. **Los resultados numéricos son reproducciones estáticas, no ejecución del binario Kotlin ni validación en Android.** Se conservaron las fórmulas, constantes, orden del catálogo y desempates relevantes. Los casos de sesiones usan parámetros explícitos; no representan el programa ni el historial privado del usuario.

Catálogo analizado: android-native/app/src/main/assets/exercise_catalog_v2.json:1; revisión v2-approved-2026-09-29-a; 96 familias, 228 definiciones y 553 configuraciones. SHA-256 del archivo: 81db1f950148a7769b7d1d1dd8f066746439efe726ca00d35d87d52119bcf91c. El JSON está minificado en una sola línea: por eso se acompaña la referencia :1 con IDs de definición/configuración, que permiten localizar cada dato sin inventar números de línea.

Las referencias abreviadas siguientes son nombres de archivo únicos dentro de estas rutas, todas bajo android-native/app/src/main/java/com/example/kpkn/:

| Archivo citado | Ruta |
|---|---|
| SessionEditorAugeComputation.kt, SessionEditorViewModel.kt, SessionEditorViewModelAugeActions.kt | screens/sessioneditor/ |
| AssistantSheet.kt, AssistantImpactTab.kt, AssistantSuggestionsTab.kt | screens/sessioneditor/components/sheets/ |
| SessionAssistantEngine.kt, SessionAssistantModels.kt, TimeCoachEngine.kt | domain/sessionassistant/ |
| AugeFatigueEngine.kt, AugeUtils.kt, AugeClassifiers.kt, InterferenceEngine.kt, CardioRingDrainEngine.kt | domain/auge/ |
| SmartExerciseCreator.kt, AutoExerciseDescription.kt, ExerciseMatchLexicon.kt, ExercisePatternDetector.kt, ExerciseMatchEngine.kt, ExerciseMuscleResolver.kt | domain/exercises/ |
| ExerciseCatalogV2Resolver.kt, AprendeSimilarityEngine.kt | domain/exercises/catalogv2/ |
| ExerciseCatalogV2LegacyAdapter.kt | data/exercises/catalogv2/ |
| ExerciseDatabase.kt, ExerciseTechniqueImageLookup.kt | data/exercises/ |
| SessionTemplates.kt | data/sessions/ |
| CardioFeaturedTemplates.kt, ExerciseMuscleInfo.kt, WorkoutMedia.kt | data/models/ |
| VolumeCalculator.kt, SessionDurationEstimator.kt | domain/training/ |
| Calculations.kt | domain/calculations/ |
| ExercisePickerV2Catalog.kt, ExerciseCatalogV2Info.kt, ExerciseFicha.kt | screens/sessioneditor/components/ |
| CatalogCustomExerciseEditor.kt, CatalogExerciseRow.kt | screens/sessioneditor/design/ |
| CustomExerciseRepository.kt, WorkoutMediaRepository.kt | data/repository/ |
| Entities.kt | data/db/ |
| WorkoutMediaStore.kt | data/media/ |

Prioridades: P0 = dato o acción incorrectos con efecto sobre la sesión; P1 = calidad/contexto; P2 = ampliación. Tamaños de implementación, incluidas pruebas: S ≈ medio a un día; M ≈ dos a cuatro días; L ≈ cinco a diez días de una persona familiarizada con el proyecto. Son estimaciones de esfuerzo, no trabajo realizado ni plazos comprometidos.

## 1. Impacto: Columna en cero

### 1.1 Recorrido real y hallazgo principal

La hoja actual es AssistantSheet, montada desde las hojas del editor. AssistantImpactTab.kt:57-69 lee uiState.augeSummary y asigna directamente:

- Columna = summary.sessionDrain.spinal.
- Músculos = summary.sessionDrain.muscular.
- Energía = summary.sessionDrain.cns.

SessionEditorViewModel.kt:983-1008 obtiene computeSessionAugeComputation y buildAugeSummaryFromMetrics. La ruta de respaldo de las líneas 1012-1021 usa buildAugeSummary, que acaba en el mismo cálculo por sesión. El índice viene de catalogExerciseIndex (SessionEditorViewModel.kt:121-122); la resolución prioriza la configuración explícita (SessionEditorAugeComputation.kt:844-853).

**P0 — Hay una divergencia verificable entre el cálculo del editor y el cálculo general. No hay evidencia de que el catálogo tenga toda la carga axial en cero.** La pasada del editor acumula porcentajes por serie y los redondea directamente a enteros en SessionEditorAugeComputation.kt:702-706. El motor general finaliza esos acumuladores mediante scaleSpinalDrainToUi y ensureMinimumHardSessionDrain en AugeFatigueEngine.kt:637-660. Esa finalización no se invoca en la pasada del editor.

El índice sí conserva los campos: ExerciseCatalogV2LegacyAdapter.kt:86-90 copia efc, cnc, ssc y axialLoadFactor del perfil exacto. En las 553 configuraciones, las 553 tienen los cuatro valores; 157 tienen axialLoadFactor positivo y 396 declaran cero. Esos ceros son parte del contrato actual, no valores faltantes.

| Configuración real del JSON:1 | EFC | CNC | SSC | axialLoadFactor | Compuerta axial |
|---|---:|---:|---:|---:|---:|
| high_bar_back_squat__barbell | 2,8 | 2,2 | 0,5 | 0,7 | 0,7 |
| low_bar_back_squat__barbell | 2,8 | 2,2 | 0,5 | 0,7 | 0,7 |
| conventional_deadlift__bilateral__barbell | 2,8 | 2,2 | 1,0 | 1,0 | 1,0 |
| romanian_deadlift__bilateral__barbell | 2,8 | 2,2 | 1,0 | 1,0 | 1,0 |
| bench_press__barbell | Perfil propio | Perfil propio | Positivo | 0,0 | 0,0 |

spinalAxialGate devuelve 0 para axial ≤ 0, el factor limitado a 0..1,5 cuando es positivo y 1 para null (AugeFatigueEngine.kt:45-52). Así se distingue «sin carga axial declarada» de «sin dato». El editor aplica esta compuerta en las líneas 621-624 y 649; SSC positivo por sí solo no implica un coste visible de Columna.

### 1.2 Reproducción numérica con sentadilla y peso muerto

Convención de las tablas: M/E/C = Músculos/Energía/Columna. Sin historial, sin técnicas de intensidad, sin superserie, series efectivas, RPE 8, cinco repeticiones y 180 s de descanso. Pesos: 100 kg en sentadilla trasera alta y 140 kg en peso muerto convencional. El catálogo resuelve las dos configuraciones anteriores.

**Caso A: una serie de cada ejercicio, perfil POWERLIFTER.** Tanques M/E/C = 324/390/6720 (AugeFatigueEngine.kt:90-108).

| Paso | M | E | C |
|---|---:|---:|---:|
| Primera serie de sentadilla, salida de calculateSetBatteryDrain | 6,650542 | 2,313031 | 0,204186 |
| Sentadilla, después de axialGate = 0,7 | 6,650542 | 2,313031 | 0,142930 |
| Sentadilla, aporte conservado antes del soft cap | 5,652961 | 1,966076 | 0,121491 |
| Serie de peso muerto, salida bruta | 6,650542 | 2,313031 | 0,428656 |
| Peso muerto, aporte conservado antes del soft cap | 5,559718 | 1,933647 | 0,358347 |
| Acumulado final del editor, después de soft cap | 11,027803 | 3,875962 | 0,479591 |
| Enteros que recibe Impacto | **11** | **4** | **0** |

La demanda espinal existe; se pierde al redondear 0,479591. La suma axial de la sesión es 1,7, no cero. La finalización general tampoco garantiza un entero positivo en una sesión tan pequeña: escala ese acumulado a 0,508472 y lo trunca a 0; el mínimo de sesión dura no se aplica con solo dos series. Por tanto, **compartir la finalización resuelve la incoherencia, pero mostrar fracciones exige además corregir la representación del valor**.

**Caso B: tres series de cada ejercicio, perfil ENTHUSIAST, resto de parámetros idénticos.** Tanques M/E/C = 310,5/258,75/4140.

| Paso | M | E | C |
|---|---:|---:|---:|
| Primera serie de sentadilla, coste bruto | 6,939696 | 3,486307 | 0,331432 |
| Su coste espinal después de axialGate | — | — | 0,232002 |
| Su aporte espinal conservado | — | — | 0,197202 |
| Acumulado al acabar las tres sentadillas | 17,513575 | 9,143382 | 0,620643 |
| Primera serie de peso muerto, coste bruto | 6,939696 | 3,486307 | 0,695789 |
| Acumulado al acabar las seis series | 32,219926 | 17,249722 | 2,366759 |
| Impacto actual del editor | **32** | **17** | **2** |
| Motor general actual | **32** | **17** | **8** |

La suma axial es 3 × 0,7 + 3 × 1,0 = 5,1. La transformación espinal general produce 4,001186; después se aplica el mínimo 8 para seis series duras con carga axial (AugeFatigueEngine.kt:788-806). El editor omite ambos pasos.

Las fórmulas de la reproducción son las de AugeFatigueEngine.kt:412-535: repeticiones elevadas a 0,65, multiplicador de RPE, progresión de series, descanso, densidad y carga; rawSpinal incluye SSC, no EFC/CNC. Después se aplican conservación 0,85, decaimiento 0,65, compuerta axial y soft cap (AugeUtils.kt:19-22,163-171; SessionEditorAugeComputation.kt:646-656).

**Conclusión sobre «siempre cero».** El código actual permite reproducir ceros indebidos por pérdida de precisión y costes espinales subestimados respecto del resto de la app. No respalda una afirmación universal: el caso B y otras sesiones con suficiente volumen devuelven un valor positivo. Sin el estado de la sesión observada ni ejecución del dispositivo, no se puede atribuir honestamente cada cero del usuario a una sola causa. La divergencia del cálculo y la cuantización sí están demostradas.

### 1.3 Por qué sí aparecen Músculos y Energía; otras causas concretas

1. **Escalas diferentes.** Músculos y Energía usan EFC/CNC y tanques mucho menores; no pasan por axialGate. En el caso A sobreviven como 11 y 4 mientras C queda por debajo de medio punto. La UI recibe esos enteros directamente; el problema ocurre antes del dibujo.

2. **EMA agrava valores pequeños.** SessionEditorAugeComputation.kt:708-723 aplica EMA después del primer redondeo. AugeFatigueEngine.kt:1016-1041 vuelve a truncar a entero. Con dos o más sesiones y un factor 0,925, C = 1 pasa a 0; M = 32 sigue siendo positivo. Es un agravante condicionado por el historial, no una explicación demostrada para todas las sesiones.

3. **Ejercicios propios: una segunda vía a cero.** SmartExerciseCreator.kt:144 guarda el axial del primer parecido o 0,0 si falta. Un nombre de bisagra puede recibir como referencia un curl sin carga axial y quedar explícitamente en cero. Ese valor desactiva también el respaldo heurístico, porque null y 0 tienen significado diferente. El bloque 3 reproduce este fallo de selección. Es causa directa para esos ejercicios personalizados, distinta de la pérdida de precisión de los ejercicios nativos.

4. **P0 independiente: cardio no sumado al acumulador global.** SessionEditorAugeComputation.kt:519-541 calcula cardioDrain, rellena mapas musculares y devuelve el insight, pero no incrementa pipelinedTotalMuscular/Cns/Spinal ni pipelinedAccumulatedDrain. Una sesión solo cardio deja fromPass a cero y, como totalSets aumenta, activa el respaldo M/E/C = 14/10/8 de AugeUtils.kt:61. Las sesiones mixtas omiten el cardio en los totales. Esto refuta además la idea de que todo cero de Columna comparta origen.

5. **Caché incompleta, P1.** SessionEditorViewModel.kt:974-987 usa tamaño del índice como versión y no incluye el historial en la clave. Cambiar metadatos conservando el número de entradas o cambiar EMA puede reutilizar un resultado anterior. Es un riesgo de obsolescencia verificable por lectura; no se ha demostrado que causara la observación del usuario.

### 1.4 Arreglo exacto propuesto

Primer parche: en computeSessionAugeComputation, obtener el coste global con AugeFatigueEngine.calculateAdjustedPredictedDrain(session, exerciseIndex, settings), aplicar calculateMesocycleStressEMA/adjustPredictedDrainWithEMA una sola vez y usar ese resultado para summary.sessionDrain. La pasada local puede conservarse temporalmente para volumen e insights, pero sus acumulados no deben sustituir el coste global.

~~~kotlin
// Propuesta de cambio; no aplicada.
val baseDrain =
    AugeFatigueEngine.calculateAdjustedPredictedDrain(session, exerciseIndex, settings)
val ema = AugeFatigueEngine.calculateMesocycleStressEMA(programLogs, programId, mesoIndex)
val predictedDrain = AugeFatigueEngine.adjustPredictedDrainWithEMA(baseDrain, ema)
~~~

El cierre correcto del problema requiere extraer una proyección compartida del motor que devuelva global, valores sin redondear, aportes por ejercicio/músculo y procedencia. Tanto el editor como el asistente deben consumirla. La proyección debe incorporar cardio antes de finalizar, conservar la configuración exacta, aplicar la compuerta axial una sola vez y seguir los mismos criterios de series, RM, unilateralidad y descansos.

Para la UI: cero cuando el coste es exactamente cero; «<1» cuando el coste positivo final es menor de uno; «Sin estimación» para dato desconocido. Mantener decimales hasta formatear, incluido EMA. Ese cambio de contrato debe hacerse también en el motor compartido: el parche de la llamada existente, por sí solo, no recupera los decimales que su PredictedDrain ya perdió.

Mantener el contrato axial = 0 de ejercicios nativos que lo declaran. En propios, la ausencia de evidencia se guarda como desconocida; la imagen o un parecido débil no deben certificar carga axial. Para los mapas espinales, utilizar los aportes de la misma proyección y documentar el efecto del mínimo de sesión; repartir según contribuciones positivas conocidas, sin inventar músculos para cuadrar un total.

### 1.5 Pruebas que fijarían el problema y paquetes

No se escribieron ni ejecutaron estas pruebas:

- Integración con el asset aprobado: los casos A y B, configuración exacta, costes globales iguales entre editor y motor; B fija el contrato actual 32/17/8 antes de cualquier recalibración.
- Precisión: coste final positivo inferior a uno se presenta como «<1»; axial = 0 produce 0; desconocido se distingue de ambos; EMA no convierte silenciosamente una estimación positiva en «sin carga».
- Contraste real: high_bar_back_squat__barbell, conventional_deadlift__bilateral__barbell y bench_press__barbell. La carga axial del catálogo debe llegar intacta a la proyección.
- CardioFeaturedTemplates.steady_z2_30, solo y mezclado con sentadilla: el total usa los aportes reales del cardio y no el respaldo 14/10/8.
- Un cambio de perfil sin variar el tamaño del catálogo y un cambio de historial invalidan el resultado cacheado.
- Invariantes entre proyección global, detalle por ejercicio y atribución muscular, con tolerancia de formato explícita y sin exigir sumas de enteros que el redondeo no garantiza.
- La prueba existente src/test/java/com/example/kpkn/screens/sessioneditor/SessionEditorAugeAxialGateTest.kt:14-84 usa un squat sintético con SSC = 1 y axial = 1, frente a 0,5 y 0,7 del real. Comprueba positividad, no igualdad con la finalización general ni conservación de fracciones. Necesita complementarse con el catálogo real.

| Paquete | Prioridad | Tamaño | Entregable revisable |
|---|---|---|---|
| I1. Coste global compartido | P0 | M | Mismo resultado en Impacto y asistente; cardio incluido; fixtures reales |
| I2. Precisión y procedencia | P0 | M | DTO sin cuantización prematura y formato 0 / <1 / sin estimación |
| I3. Caché y contrato del asset | P1 | S | Claves por revisión/contenido e historial; regresiones de invalidación |
| I4. Eliminar pasadas duplicadas | P1 | L | Una proyección global y por músculo/ejercicio, consumida por todas las superficies |

## 2. Sugerencias: inventario, sesiones y rediseño

### 2.1 Qué motor alimenta hoy la pestaña

AssistantSheet.kt:155-159 pasa report.ajustes a AssistantSuggestionsTab. El reporte se calcula con SessionAssistantEngine.evaluate en SessionEditorViewModel.kt:1037-1063. **No se muestra directamente augeSummary.suggestions.**

evaluate (SessionAssistantEngine.kt:36-78) ejecuta tres familias activas: ajustes por RINGS, tiempo y solapamiento. Devuelve la concatenación en ese orden. Fija veredicto = OPTIMAL, score = 0 y riesgos/oportunidades/fantasmas/plantillas vacíos; varias funciones antiguas del mismo archivo siguen existiendo, pero no participan en esa ruta.

AssistantSuggestion no tiene severidad (SessionAssistantModels.kt:89-100). Tiene prioridad; los valores WARNING/BLOCKING pertenecen a SessionRisk, actualmente fuera de evaluate. La UI tampoco transforma la prioridad en una severidad clínica: muestra texto y casillas. Por eso el alarmismo actual procede sobre todo del lenguaje y del disparador.

### 2.2 Inventario completo de las reglas activas

Los textos siguientes son los de esta revisión; los campos variables se expresan con N, músculo o nombre del ejercicio.

| ID / regla | Disparador y umbral | Texto que ve la persona | Severidad / orden | Evidencia |
|---|---|---|---|---|
| A1. session_too_fatiguing | max(C,E,M) ≥ 60 | «Sesión demasiado exigente». «Es una sesión demasiado fatigante y puede afectar tu recuperación para lo que viene. Elige qué quieres suavizar.» | Sin severidad; priority = 1 | SessionAssistantEngine.kt:83-87,724-781 |
| A1a. Quitar serie | A1 y M ≥ 60; existe músculo con volumen efectivo máximo | «Quitar 1 serie de [músculo]» | Detalle aceptado por defecto | SessionAssistantEngine.kt:744-752 |
| A1b. Bajar esfuerzo de ejercicios | A1; cualquier coste bruto de un ejercicio ≥ 60. Orden por máximo coste y nombre, máximo tres ejercicios | «Bajar un poco la intensidad en [ejercicio]»; LowerRpe(amount = 0,5) | Detalles aceptados por defecto | SessionAssistantEngine.kt:732-742,755-760 |
| A1c. Respaldo de esfuerzo | A1; ningún detalle anterior; hay ejercicios | La misma acción, sobre el primer ejercicio de la sesión | Aceptado por defecto | SessionAssistantEngine.kt:763-769 |
| A2. time_overage | Hay targetDurationMinutes, duración interna supera el objetivo y exceso ≥ 8 min | «La sesión se alarga demasiado». «Pasa unos N min del tiempo previsto. Elige qué ajustes quieres aplicar.» | Sin severidad; priority = 20 | SessionAssistantEngine.kt:45-59,89-95,150-159 |
| A2a. Bajar descansos | A2; media no ponderada de exercise.restTime, con respaldo 90, > 45 s | «Bajar 15 s de descanso entre series» | Aceptado por defecto; afecta a todos los ejercicios, mínimo 30 s | SessionAssistantEngine.kt:97-103; SessionEditorViewModelAugeActions.kt:115-119 |
| A2b. Superserie | A2; hay ≥ 2 ejercicios; los dos primeros no tienen isDropSet/isRestPause | «Unir [primero] con [segundo] en superserie» | No aceptado por defecto | SessionAssistantEngine.kt:106-118 |
| A2c. Drop-set | A2; primeros dos ejercicios elegibles con > 1 serie, sin drop ni rest-pause; no exige fallo | «Acortar descansos en [ejercicio] (drop-set)» | No aceptado por defecto | SessionAssistantEngine.kt:121-134 |
| A2d. Quitar serie por tiempo | A2; hay algún detalle anterior y algún músculo con flat > 0; toma la primera clave del mapa | «Quitar 1 serie de [músculo]» | No aceptado por defecto | SessionAssistantEngine.kt:137-147 |
| A3. overlap-[sessionAId] | InterferenceEngine devuelve interferencia ≥ 10 %, máximo dos sesiones | «Poco descanso para [hasta dos músculos]». «Entrenaste [sesión] [cuándo] y esta sesión vuelve a pedir [músculos]. Baja un poco el volumen o cambia el orden.» | Sin severidad; priority = 2; se añade después de tiempo | SessionAssistantEngine.kt:784-827 |
| A3a. Quitar serie por solapamiento | A3; existe músculo con mayor déficit estimado | «Quitar 1 serie de [músculo]» | No aceptado por defecto | SessionAssistantEngine.kt:819-825 |

Matices que cambian el comportamiento real:

- MAX_SUPERSET_SUGGESTIONS = 2 no produce dos parejas: el bucle recorre i = 0 únicamente al usar min(n - 1, 2) y step 2. MAX_REDUCE_REST_SUGGESTIONS está declarado pero no se usa. Evidencia: SessionAssistantEngine.kt:83-85,107.
- Hay un máximo estructural actual de cuatro tarjetas: una por RINGS, una por tiempo y dos por solapamiento. No existe un presupuesto global explícito de avisos ni deduplicación semántica entre familias. RINGS puede tener cuatro detalles y tiempo cinco. No hay ordenación final por priority: el aviso de tiempo 20 puede preceder a solapamiento 2 (línea 67).
- Los insights usados por A1b son sumas brutas y no pasan por la finalización global ni por axialGate en este motor (SessionAssistantEngine.kt:281-295). El global sí usa calculateAdjustedPredictedDrain y EMA (341-359). «Ejercicio pesado» y «sesión costosa» no están expresados en la misma escala.
- LowerRpe actúa según intensidad de cada serie, no según un RIR global. En modos no cubiertos por RPE/RIR/FAILURE se queda igual (SessionEditorViewModelAugeActions.kt:133-156). No garantiza una modificación en SOLO_RM; el objetivo RM exige editar su porcentaje/referencia con el contrato RM.
- ReduceSet(muscle) busca el primer ejercicio que involucre el músculo y tenga > 1 serie, incluso si el músculo es secundario. No busca la mejor serie accesoria ni la de mayor aporte (SessionEditorViewModelAugeActions.kt:204-221).
- La UI bloquea «Aplicar selección» cuando no hay casillas marcadas y muestra los cambios antes de aplicar; ese comportamiento es aprovechable (AssistantSuggestionsTab.kt:67-106).

La subregla A3 depende de este inventario de interferencia, también activo:

| Etapa | Umbral / cálculo | Consecuencia | Evidencia |
|---|---|---|---|
| Ventana | Historial previo ≤ 96 h; otras sesiones con día asignado y separación modular de 1 a 4 días | Las sesiones sin fecha/día quedan fuera; sesiones de días anteriores se infieren respecto del día de la semana | InterferenceEngine.kt:40,188-252 |
| Trabajo planificado de A | clamp(EFC / 5 × 0,18 × max(series,1), 0,08..0,85) por ejercicio; por músculo toma el máximo × rol | No usa RPE, fase, series completadas ni suma de todos los ejercicios | InterferenceEngine.kt:355-390 |
| Uso de B | Máximo aporte de rol; normalización por el máximo de la sesión | Una serie ligera puede recibir el mismo uso relativo que varias duras | InterferenceEngine.kt:393-426 |
| Músculo elegible | Drain de A ≥ 0,08; uso de B ≥ 0,25; residual tras decaimiento ≥ 0,03 | Se considera músculo compartido | InterferenceEngine.kt:450-460 |
| Recuperación | Residual = drainA × exp(-ln(2) × horas / semivida). Semividas 24/48/72/96 h, respaldo 48 | Constantes por grupo; no estimación individual | InterferenceEngine.kt:25-38,456-458 |
| Porcentaje final | Media de residual × uso, ponderada otra vez por uso; × 100 y truncado a entero | A3 muestra aviso desde 10 | InterferenceEngine.kt:462-482; SessionAssistantEngine.kt:799 |
| Texto interno descartado por A3 | ≥ 70 alta; ≥ 45 moderada; ≥ 25 leve; < 25 «Interferencia mínima. El split está bien organizado para estos dos días.» | El texto de A3 puede alarmar para el mismo porcentaje que esta función llama mínimo | InterferenceEngine.kt:502-511 |

### 2.3 Reglas existentes que no se ejecutan en evaluate

Este inventario evita atribuir a la pantalla actual advertencias que quedaron en código. Los títulos/textos se incluyen para revisar una eventual eliminación o migración; no se recomienda reactivar todo el bloque.

| Regla antigua | Disparador / umbral | Texto literal principal / acción | Severidad | Evidencia |
|---|---|---|---|---|
| RPE rígido | rigidLimits y RPE efectivo > maxRPE, si maxRPE < 10 | «RPE excesivo (límite rígido)»; «Reducir RPE o eliminar serie.» | BLOCKING | SessionAssistantEngine.kt:403-427 |
| Volumen rígido sesión | rigidLimits y effective ≥ maxVolumePerMuscleSession | «Volumen rígido excedido: [músculo]»; «Reducir series o bajar intensidad para [músculo].» | BLOCKING | 430-444 |
| MRV sesión | flat ≥ mrv | «[Músculo] en o sobre MRV»; «No añadir más volumen.» | BLOCKING | 447-463 |
| MAV sesión | flat > mav, sin llegar a mrv | «[Músculo] sobre MAV»; «Añadir volumen tiene rendimientos decrecientes.» | WARNING | 464-476 |
| Límite genérico sesión | effective > límite genérico | «Volumen elevado: [músculo]»; «Reducir 1-2 series o bajar intensidad.» | WARNING | 479-501 |
| Axial | suma de axialLoadFactor por serie > 25; crítico si suma > 40 o drain C > 30 | «Carga axial moderada/elevada»; «Bajar series o intensidad del ejercicio más demandante protege la columna.» / «Ajustar densidad o intensidad mejora la tolerancia.» | WARNING / BLOCKING | 504-524 |
| SNC | E ≥ 85 o RPE medio ≥ 9,3; bloqueo si E ≥ 90 | «Fatiga SNC elevada»; «Reducir intensidad global.» | WARNING / BLOCKING | 527-542 |
| Fallo | ≥ 3 series y proporción ≥ 0,5; bloqueo si ≥ 0,7 | «Exceso de series al fallo»; «Esto acumula fatiga SNC y articular más de lo necesario.» | WARNING / BLOCKING | 545-572 |
| Codos | > 8 series con nombres de francés/rompecráneos/extensión en polea tríceps; > 12 eleva severidad | «Estrés de codos»; «ángulos agresivos»; «usar variantes más seguras» | INFO / WARNING | 307-312,575-585 |
| Rodillas | > 8 series cuyo nombre contiene extensión y cuadr/sissy; > 12 eleva severidad | «Estrés de rodillas»; «Bajar densidad o reforzar calentamiento mejora tolerancia.» | INFO / WARNING | 313-315,587-597 |
| Volumen semanal manual | semanal > maxVolumePerMuscleWeekly | «Volumen semanal alto: [músculo]»; «Repartir el estímulo ayuda a la recuperación.» | WARNING | 623-641 |
| MRV semanal | semanal ≥ mrv de sesión × 3, o límite manual de respaldo | «[Músculo] en o sobre MRV semanal»; «No añadir más volumen este día.» | BLOCKING | 644-659 |
| Acción por fallo | Existe riesgo FAILURE | «Reducir series al fallo»; «Pasar a RIR 1-3 o bajar RPE.» | Sin severidad; prioridad 1 | 664-681 |
| Acción axial | Riesgo SPINE distinto de INFO | «Bajar intensidad para proteger columna» | Sin severidad; prioridad 2 | 684-693 |
| Acción SNC | Existe riesgo CNS | «Bajar intensidad para reducir fatiga SNC»; «Subir RIR o bajar RPE en 0.5-1.0 puntos.» | Sin severidad; prioridad 3 | 696-705 |
| Acción volumen | Riesgo VOLUME y BLOCKING | «Reducir series de [músculo/ejercicio]»; reutiliza mensaje del riesgo | Sin severidad; prioridad 4 | 708-721 |
| Oportunidad de volumen | E < 70 y C < 20; músculo no bloqueado; sesión < MEV y semana < 0,8 × MRV semanal | «[Músculo] bajo MEV»; «series de margen real» | Sin severidad; prioridad 10 | 831-862 |
| Tarjeta fantasma | E < 75 y C < 25; bajo MEV; semana bajo límites; tras añadir E < 90 y C < 40 | «[Músculo] está bajo MEV»; 1..3 series a RPE 7,5, hasta dos candidatos por músculo | No es riesgo; compatibleConSplit fijado a true | 867-935 |
| Avisos de plantilla | Plantilla comparte músculos; E > 80, C > 25 o M > 70 | «Alto drenaje SNC» / «Carga axial elevada» / «Drenaje muscular alto» | Strings, sin severidad | 946-993 |
| Veredicto | Algún bloqueo → CRITICAL; ≥ 2 WARNING → FATIGUING; algún riesgo o E > 60 o C > 20 → WARNING; resto OPTIMAL | Nombres del veredicto | Derivada; no usada por evaluate actual | 1031-1040 |

Límites genéricos: sesión base 8/9/10 por déficit/mantenimiento/superávit, ajuste +0,8 para bodybuilding/powerbuilding y -0,4 para powerlifting/halterofilia, redondeado y mínimo 6; semana 20/24/28. Los umbrales personalizados o de respaldo se dividen entre tres para la sesión, sin considerar la frecuencia real del músculo (SessionAssistantEngine.kt:1102-1155). El recuento semanal antiguo multiplica por esfuerzo y no agrega igual que el flat de sesión (602-620). checkGenericVolumeLimits busca bloqueos en su lista local recién creada, no en los riesgos previos: su supuesto filtro de duplicados tampoco los evitaría al reactivarlo (483-488).

También queda un segundo juego de recomendaciones en SessionEditorAugeComputation.kt:97-237 y 306-435. Todas son INFO, pero **no alimentan esta pestaña**:

| Regla del resumen AUGE | Umbral | Texto principal | Evidencia |
|---|---|---|---|
| Volumen sesión | effective > límite anterior; fallo/flat ≥ 0,7, ≤ 0,3 o resto; rol estabilizador/secundario ≥ 0,45 | «Muchas series cerca del fallo… Mejor recortar series…» / «N pts sobre límite… Puedes bajar series…» / «Recorta un poco…» | 102-139,310-347 |
| Volumen semana | flat > 20/24/28 | «N series equivalentes sobre límite. Repartir el estímulo de la semana te deja más fresco.» | 152-164,356-368 |
| Columna | suma axial > 25, texto más fuerte si > 40 | «Bajar series o intensidad… protege la columna» / «Ajustar densidad o intensidad puede mejorar la tolerancia» | 167-183,370-386 |
| Energía | E ≥ 85 o RPE medio ≥ 9,3 | «Tu Energía ya va alta para esta sesión…» | 186-197,388-399 |
| Codos / rodillas | > 8 series por heurística de nombre | «ángulos agresivos» / «Extensiones puras o patrones similares se están acumulando» | 200-217,401-417,682-687 |
| Déficit | Siempre que el objetivo calórico sea DEFICIT | «conviene apretar menos el volumen efectivo por sesión para que la recuperación no se caiga» | 220-227,419-426 |
| Sesión liviana | 0,1 ≤ RPE medio < 6,5 | «Si el objetivo era más estímulo, aún hay margen para subir la intensidad…» | 230-237,428-435 |
| Insight por ejercicio | C > 75; si no E > 85; si no M > 88 | «Fatiga de columna alta…» / «Carga neural elevada…» / «Volumen muscular alto…» | 833-841 |

Su estado global asume cinco sesiones/semana y presupuestos 220/260/300 según objetivo calórico; clasifica con 0,90 y 1,25 del presupuesto restante. Es una tercera clasificación distinta del veredicto fijo de evaluate y del pico 60 (SessionEditorAugeComputation.kt:803-822). No debe presentarse como evidencia individual de riesgo.

TimeCoachEngine y UltraFastEngine corresponden a otros flujos de Reglas/Tiempo. No se mezclaron sus reglas con las de la pestaña auditada. TimeCoach ya dispone de perfiles de descanso, estimación de ahorro y deduplicación reutilizables, pero aún requiere revisar sus criterios: por ejemplo, acepta una pareja si comparte estación **o** es antagonista **o** no comparte primarios (TimeCoachEngine.kt:701-711), y su bloque de técnicas tampoco exige fallo (247-279).

### 2.4 Prueba en papel con sesiones publicadas

Se analizaron **once plantillas de fuerza publicadas**, la plantilla de cardio Z2 y una variante con superserie creada a partir de una de las once. La variante S13 se identifica como tal: no se encontró una plantilla literal de fuerza con superserie ya formada en SessionTemplates.kt. El editor sí permite materializarla, y el generador integrado también construye grupos (domain/training/generator/SessionAssembler.kt:633-647,786-789). No se presenta una sesión inventada como si fuera un programa precargado.

En todas las filas de base: perfil ENTHUSIAST, mantenimiento, sin historial, sin otras sesiones de semana, sin restricciones manuales adicionales, sin técnicas, con 60 kg de respaldo cuando la plantilla no prescribe peso. Es el respaldo real del motor, no una afirmación sobre los kilos que debería usar alguien. El programa se deja null para aislar las reglas. Solo se asigna el límite de tiempo indicado. M/E/C es el coste **del motor que consume Sugerencias**, no el del editor defectuoso.

**Se reconstruyó la publicación V3.** Los literales antiguos no llegan directamente a la UI (SessionTemplates.kt:3284-3297). Se aplicaron normalización de esfuerzo, límites directos, orden, pisos de descanso y relleno de series: líneas 3557-3607,3661-3717,3752-3821,4380-4395,4429-4511. Por ejemplo, S3 acaba con diez series, S6 con cuatro series de remo y S7 con 180 s en sus dos primeros compuestos. Medir solo los literales habría producido avisos distintos.

La duración de la tabla es **la interna de SessionAssistantEngine**, calculada con calculateSessionTimeBreakdown: 60 s de preparación por ejercicio, 45 s por serie y descansos reales de la estructura (Calculations.kt:604-611,648-650,681-750). No se confundió con la duración declarada de la plantilla ni con el reloj actual de la cabecera.

| Caso | Procedencia y contexto | Series | Min internos | M/E/C | Tiempo disponible | Sugerencias que salen |
|---|---|---:|---:|---|---|---|
| S1 | sys-v3-pl-sbd-1 · Fuerza: SBD Sentadilla | 12 | 32 | 53/43/8 | Sin límite | Ninguna |
| S2 | sys-v3-pl-sbd-2 · Fuerza: SBD Peso muerto | 12 | 33 | 53/41/8 | Sin límite | Ninguna |
| S3 | sys-v3-empuje-libre · Hipertrofia: Empuje Peso libre | 10 | 22 | 56/44/0 | Sin límite | Ninguna |
| S4 | sys-v3-pierna-maquinas · Pierna Maquinas | 10 | 24 | 50/41/1 | Sin límite | Ninguna |
| S5 | sys-v3-hf-torso · Torso Dosis compacta | 8 | 14 | 43/29/0 | Sin límite | Ninguna |
| S6 | sys-v3-full-equilibrado-a · Full Body A | 10 | 26 | 51/37/1 | Sin límite | Ninguna |
| S7 | sys-v3-hf-full · Corta Full 30 min | 8 | 19 | 46/30/0 | 30 | Ninguna |
| S8 | sys-v3-recuperacion-activa · Recuperacion Activa | 8 | 20 | 45/30/2 | Sin límite | Ninguna |
| S9 | sys-v3-hf-brazos · Brazos Dosis compacta | 8 | 15 | 41/31/0 | Sin límite | Ninguna |
| S10 | sys-v3-ant-chain-ap · Cadena anterior | 16 | 37 | 62/54/0 | 30 min | A1: «Sesión demasiado exigente»; quitar una serie de Deltoides |
| S11 | sys-v3-full-demanding · Cuerpo completo exigente | 14 | 38 | 57/42/3 | 30 min | A2: «La sesión se alarga demasiado»; exceso 8 min |
| S12 | steady_z2_30 aplicado a bicicleta estática · solo cardio | 0 de fuerza | 31 | 10/1/1 | 30 min | Ninguna |
| S13 | Variante de sys-v3-hf-brazos: primera pareja en superserie | 8 | 14 | 42/35/0 | Sin límite | Ninguna |

**Fuentes de las sesiones.** S1/S2: SessionTemplates.kt:3927-3951. S3: 4025-4026,4058-4064. S4: 4029,4074-4080. S5: 4204,4257-4267. S6: 4037,4144-4150 y relleno 4468-4511. S7: 4206,4278-4284. S8: 4048,4052-4053,4144-4150. S9: 4205,4270-4275. S10: 1382-1424 y compilación V3. S11: 3245-3280 y compilación V3. S12: CardioFeaturedTemplates.kt:20,42-50; coste en CardioRingDrainEngine.kt:25-88 y MET de bicicleta nivel 5 = 8,5 en domain/cardio/CardioIntervalEngine.kt:173-184. S13: receta de S9, dos primeros miembros con supersetId y supersetGroupRef, 30 s entre ejercicios y 75 s entre rondas; operación soportada por SessionEditorViewModelAugeActions.kt:179-200.

Las nueve filas iniciales y S12/S13 no merecen inventar problemas para llenar la pestaña. La ausencia de sugerencias en ese contexto es razonable. S12, sin embargo, presenta el dato equivocado de Impacto descrito en 1.3: el motor general da 10/1/1 y el editor muestra su respaldo 14/10/8.

**Juicio sobre las sugerencias malas, con entradas concretas:**

- **S10, fatiga:** el pico muscular 62 dispara «demasiado fatigante», aunque ningún ejercicio tiene insight bruto ≥ 60. El único detalle es quitar una serie de Deltoides, elegido por su volumen efectivo 4,2. Al aplicar, el primer ejercicio con ese músculo y > 1 serie es la banca con mancuernas, donde el deltoides es secundario; no el press de hombro. No se usa objetivo, rol ni fase para justificar esa elección. Es mala especificidad y tono excesivo; el coste 62 por sí solo no demuestra mala recuperación.
- **S10, tiempo:** su exceso interno de siete minutos no genera tarjeta por el corte fijo de ocho. El reloj del editor usa otra ruta y añade calentamiento general y duración por repeticiones, por lo que la persona puede ver una desviación todavía mayor.
- **S11, tiempo:** produce los detalles «bajar 15 s», unir sentadilla con banca, drop-set de sentadilla, drop-set de banca y quitar una serie de Cuádriceps. No detecta que las dos primeras acciones de densidad afectan movimientos principales ni comprueba equipo, objetivo o fallo programado. La reducción de descansos está marcada; las otras opciones no.
- **S11, efecto real del drop-set:** convertExerciseToDropSet mantiene el número de series y el descanso, y añade un mini-drop a cada serie (SessionEditorViewModelAugeActions.kt:161-175). El reloj suma al menos seis segundos por mini-drop (Calculations.kt:689-698). Aplicar solo el drop-set a la sentadilla de tres series añade 18 s; no ahorra tiempo. Aplicar los dos añade 36 s. **P0 de contrato entre el texto y la acción.**
- **S11, efecto de la opción por defecto:** hay ocho descansos entre series; recortar 15 s ahorra 120 s: 38 → 36 min internos. Sigue excediendo 30 por seis minutos, pero A2 desaparece al quedar por debajo de ocho. No hay simulación que verifique que el cambio cumple la restricción.
- **S13, contexto de tiempo adicional:** con un límite explícito de cinco minutos, A2 aparece (14 - 5 = 9). Ofrece volver a unir la primera pareja, aunque ya está agrupada. Al aplicar se omite esa acción (SessionEditorViewModelAugeActions.kt:31-39), y ReduceRest cambia restTime del ejercicio, no restBetweenExercises/restAfterSuperset del grupo: no reduce el descanso que el reloj cuenta para esa pareja. Se ofrecen acciones sin efecto, además de drops sin fallo.
- **S1 lunes → S2 martes, ambas planificadas:** la reproducción del motor de interferencia da 13 %. Sale «Poco descanso para Pectorales y Cuádriceps», con «Entrenaste… al día siguiente» y una propuesta de quitar serie de Pectorales. La primera sesión no tiene por qué haberse realizado. El propio buildRecommendation clasifica 13 % como interferencia mínima. La advertencia y el tiempo verbal son malos; la información de músculos compartidos sí puede ser útil si se presenta como estimación.
- **S6 lunes → S8 martes, recuperación planificada:** da 12 %, dominado por Dorsales y Pectorales. Se pide bajar volumen sin reconocer que S8 es una sesión inicial/de recuperación a RPE 6,5–7. El uso de B depende de roles, no de esa intención ni de su esfuerzo. No hay evidencia suficiente para la instrucción categórica.
- **S9 lunes → S5 martes:** da 7 %, por lo que no genera solapamiento. Es un control negativo útil: el simple hecho de compartir brazos no activa siempre la regla.

Los porcentajes de solapamiento se obtuvieron con las fórmulas de 2.2 y las configuraciones exactas de las recetas; no se sustituyeron por valores inyectados en customDrain.

### 2.5 Recetas completas para repetir la prueba en papel

Notación: configuración exacta, series × repeticiones, esfuerzo por serie y descanso por ejercicio. Todas las series de cada entrada comparten esos valores. La publicación se aplica antes de evaluar.

**S1 · sys-v3-pl-sbd-1.**

- low_bar_back_squat__barbell: 3 × 4, RPE 8, descanso 210 s.
- bench_press__barbell: 3 × 5, RPE 7,5, descanso 180 s.
- chest_supported_row__dumbbells__wide: 2 × 8, RPE 8, descanso 120 s.
- quads_extension_cuadriceps__machine__bilateral: 2 × 12, RPE 8, descanso 75 s.
- triceps_pushdown__bilateral__cable: 2 × 10, RPE 8, descanso 75 s.

**S2 · sys-v3-pl-sbd-2.**

- conventional_deadlift__bilateral__barbell: 3 × 3, RPE 8, descanso 240 s.
- bench_press__barbell: 3 × 6, RPE 7,5, descanso 180 s.
- lat_pulldown__bilateral__cable: 2 × 8, RPE 8, descanso 120 s.
- hip_thrust__bilateral__machine: 2 × 10, RPE 8, descanso 90 s.
- core_press_pallof__default: 2 × 12, RPE 7,5, descanso 60 s.

**S3 · sys-v3-empuje-libre.**

- bench_press__dumbbells: 3 × 8, RPE 7,5, descanso 180 s.
- flat_chest_fly__dumbbells: 2 × 12, RPE 8, descanso 75 s.
- seated_lateral_raise__dumbbells: 2 × 12, RPE 8, descanso 60 s.
- triceps_patada__cable__bilateral: 3 × 12, RPE 8, descanso 60 s.

**S4 · sys-v3-pierna-maquinas.**

- quads_sentadilla_hack__machine: 3 × 8, RPE 7,5, descanso 180 s.
- bulgarian_split_squat__dumbbells: 2 × 8, RPE 7,5, descanso 180 s.
- quads_extension_cuadriceps__machine__unilateral: 2 × 12, RPE 8, descanso 75 s.
- calf_raise__unilateral__machine: 3 × 12, RPE 8, descanso 60 s.

**S5 · sys-v3-hf-torso.**

- seated_shoulder_press__machine: 1 × 8, RPE 7, descanso 180 s.
- seated_machine_pullover__machine: 1 × 10, RPE 7, descanso 90 s.
- flat_chest_fly__machine: 1 × 12, RPE 7, descanso 75 s.
- standing_lateral_raise__machine: 1 × 12, RPE 7,5, descanso 60 s.
- deltoides_face_pull__default: 1 × 12, RPE 7,5, descanso 60 s.
- preacher_curl__machine: 2 × 10, RPE 7,5, descanso 60 s.
- triceps_pushdown__bilateral__cable: 1 × 10, RPE 7,5, descanso 60 s.

**S6 · sys-v3-full-equilibrado-a.**

- quads_prensa_piernas__bilateral: 2 × 8, RPE 7, descanso 180 s.
- bench_press__machine: 2 × 8, RPE 7, descanso 180 s.
- chest_supported_row__machine__medium: 4 × 10, RPE 7, descanso 120 s.
- romanian_deadlift__bilateral__dumbbells: 2 × 8, RPE 7, descanso 120 s.

**S7 · sys-v3-hf-full.**

- quads_prensa_piernas__bilateral: 2 × 8, RPE 6,5, descanso 180 s.
- bench_press__machine: 2 × 8, RPE 6,5, descanso 180 s.
- seated_machine_pullover__machine: 2 × 10, RPE 7, descanso 90 s.
- calves_tibial_anterior__default: 1 × 15, RPE 7, descanso 60 s.
- calf_raise__bilateral__machine: 1 × 12, RPE 7, descanso 60 s.

**S8 · sys-v3-recuperacion-activa.**

- quads_sentadilla_copa__default: 2 × 8, RPE 6,5, descanso 180 s.
- bench_press__machine: 2 × 8, RPE 6,5, descanso 180 s.
- chest_supported_row__machine__medium: 2 × 10, RPE 7, descanso 120 s.
- romanian_deadlift__bilateral__dumbbells: 2 × 8, RPE 7, descanso 120 s.

**S9 · sys-v3-hf-brazos.**

- preacher_curl__machine: 2 × 10, RPE 7, descanso 75 s.
- triceps_pushdown__bilateral__cable: 2 × 10, RPE 7,5, descanso 75 s.
- concentration_curl__dumbbells: 2 × 12, RPE 7,5, descanso 60 s.
- crossbody_triceps__cable__bilateral: 2 × 12, RPE 7,5, descanso 60 s.

**S10 · sys-v3-ant-chain-ap.**

- quads_sentadilla_hack__machine: 2 × 8, RPE 7,5, descanso 180 s.
- bench_press__dumbbells: 3 × 8, RPE 7,5, descanso 120 s.
- quads_prensa_piernas__bilateral: 4 × 10, RPE 7,5, descanso 120 s.
- seated_shoulder_press__barbell: 4 × 10, RPE 7,5, descanso 90 s.
- flat_chest_fly__machine: 3 × 12, RPE 7,5, descanso 75 s.

**S11 · sys-v3-full-demanding.**

- high_bar_back_squat__barbell: 3 × 5, RPE 7,5, descanso 210 s.
- bench_press__barbell: 3 × 6, RPE 7,5, descanso 180 s.
- lat_pulldown__bilateral__cable: 3 × 8, RPE 7,5, descanso 150 s.
- romanian_deadlift__bilateral__barbell: 2 × 8, RPE 7,5, descanso 150 s.
- standing_lateral_raise__dumbbells: 2 × 12, RPE 7,5, descanso 60 s.
- calf_raise__bilateral__machine: 1 × 12, RPE 7,5, descanso 45 s.

**S12.** CardioFeaturedTemplates.apply("steady_z2_30", CardioDetails de BIKE_STATIONARY): duración 1800 s, nivel/RPE 5, sin bloques ni HIIT. Sus aportes brutos M/E/C son 10,406957/1,328536/0,635870. La finalización general da 10/1/1. El reloj interno añade un minuto de preparación: 31 min.

**S13.** Mismas cuatro entradas de S9, con los dos primeros ejercicios en una superserie de dos rondas, 30 s entre ejercicios y 75 s entre rondas. Los otros dos siguen independientes. Ese cambio de agrupación lleva M/E/C de 41/31/0 a 42/35/0 en la réplica; el coste permanece por debajo del disparador 60.

### 2.6 Diagnóstico de fondo

**Contexto disponible, pero sin efecto decisorio.** SessionAssistantInput admite program, mesoIndex, ruleLimits, weekSessions e historial. En la ruta activa, los límites personalizados calculados no deciden los ajustes por RINGS; mesoIndex sirve para EMA, y los límites manuales no se comprueban mediante detectarRiesgos. No hay objetivo explícito de la sesión, nivel usado por las reglas, fase del bloque, dosis pretendida, rol protegido del ejercicio ni evidencia suficiente de tolerancia individual. El tipo de atleta sí cambia tanques: no sería correcto afirmar que no existe ninguna personalización.

**Tres relojes.** evaluate usa directamente calculateSessionTimeBreakdown (SessionAssistantEngine.kt:45-53). El editor usa SessionDurationEstimator y lo convierte a desglose (SessionEditorViewModel.kt:960-962,1032-1033). Este último añade 180 s de calentamiento general, estima el extremo del rango de reps y usa ceil (SessionDurationEstimator.kt:32-34,54-78,191-207). La plantilla tiene además una estimación editorial. «Un solo reloj» en el comentario no coincide con el recorrido actual. Para S11, con sus prescripciones sin rangos adicionales, el reloj visible llega a 42 min frente a 38 internos.

**Umbrales sin interpretación contextual.** Pico 60 mezcla tres canales y convierte coste modelado en juicio sobre recuperación. No hay referencia individual para afirmar «demasiado». El presupuesto de tiempo tolera siete minutos adicionales en una sesión de 30 y el solapamiento usa 10 pese a llamar «mínimo» a valores inferiores a 25 en otra función. Los umbrales MEV/MAV/MRV antiguos y la división semanal entre tres tampoco representan una sesión específica de fuerza, técnica, descarga o cardio.

**Acciones sin verificación.** Selección del primer músculo/ejercicio, modos de RM sin cambio, superseries ya existentes, drop-set que añade tiempo y cambios globales de descanso. El ahorro, el coste y la selección de series no se simulan antes de ofrecerse.

**Duplicados y señales contradictorias.** Fatiga, tiempo y solapamiento pueden proponer quitar una misma serie. El orden no sigue priority. La política está repartida entre el resumen AUGE, el asistente y el motor de tiempo. El reporte declara OPTIMAL incluso cuando acaba de emitir «Sesión demasiado exigente».

**Tono y certeza.** «Demasiado», «protege la columna», «ángulos agresivos», «variantes más seguras», «no añadir» y «Entrenaste» para una planificación exceden la evidencia del cálculo. Las frases sobre columna/articulaciones de las tablas antiguas están localizadas para evitar su reintroducción accidental.

### 2.7 Rediseño concreto

**Entradas comunes.** Un SessionAnalysisContext construido desde el borrador activo debe contener: tipo de sesión (fuerza/cardio/mixta), objetivo específico, rol de cada ejercicio y serie, condición de competición/técnica, entrenamiento por serie (RPE/RIR/fallo/%RM), experiencia y preferencias, fase y dosis prevista del bloque, disponibilidad real en segundos, agrupación y descansos por serie/ronda/lado, configuración exacta y confianza del catálogo, historial realizado con fechas y dosis comparables, semana planificada y molestias declaradas si ya existen. No introducir RIR global: el esfuerzo sigue perteneciendo a ejercicio/serie, conforme a la decisión A1.

**Una proyección de coste y una de tiempo.** Consumir el arreglo del bloque 1 y SessionDurationEstimator sobre la misma sesión. Pasar el análisis compartido a los tres módulos, en vez de recalcular con criterios distintos. Diferenciar explícitamente realizado, planificado, estimado y desconocido.

| Regla nueva o recalibrada | Decisión concreta | Fundamento y calibración |
|---|---|---|
| Presupuesto de tiempo | Proponer ajuste si exceso en segundos > max(120 s, 10 % del tiempo disponible); comunicar una estimación también para excesos menores, sin tarjeta insistente | Restricción explícita del usuario, no límite fisiológico. 120 s/10 % son valores iniciales de producto a contrastar con error de estimación y tiempos reales; resuelven el salto arbitrario 7/8 min |
| Coste alto | Un pico ≥ 60 puede describirse como coste estimado; no determina por sí solo «sesión excesiva» ni recorte automático | El mismo 60 tiene distinto significado según objetivo y dosis planificada. Mantenerlo inicialmente como señal interna permite comparar con la línea base sin convertirlo en alarma |
| Desviación de dosis | Sugerencia de reducción solo si supera el presupuesto explícito de ese bloque/sesión o hay desviación respecto de sesiones comparables más evidencia contextual; con historial insuficiente, informar sin certificar tolerancia | Priorizar el plan y la respuesta individual. Para construir una referencia inicial, exigir al menos seis sesiones comparables y mostrar su dispersión; el número es una política de confianza a validar, no una frontera de salud |
| Volumen | Comparar directo/indirecto y esfuerzo con la distribución semanal real y el objetivo; no dividir sistemáticamente entre tres ni recortar un primario por un secundario alto | Roles y contribuciones 1/0,5/0,4 ya definidos por la app; frecuencia y dosis provienen del plan, no de un supuesto universal |
| Solapamiento | 10–24 %: dato contextual opcional, sin «poco descanso» ni acción marcada; desde 25 %, valorar ajuste solo con demanda relevante de B y evidencia suficiente; 45/70 conservan bandas descriptivas internas, nunca bloqueos automáticos de salud | Alineación con las bandas existentes de InterferenceEngine; después calibrar con rendimiento e historial. Separar previsión semanal de entrenamiento realizado |
| Orden de ajustes | Primero accesorios de menor prioridad que permitan cumplir el objetivo; proteger competición/T1/técnica; esfuerzo RM modifica %RM, no un RPE ausente | Rol e intención explícitos. Cada detalle debe apuntar a exerciseId y, si procede, setId; la selección no depende del orden del mapa muscular |
| Descanso | Ajustar ejercicios/series elegibles respetando pisos y agrupación; una superserie modifica sus descansos de grupo/ronda | El contrato real del reloj. Reutilizar perfiles de TimeCoach tras revisar sus supuestos, no copiarlo íntegramente |
| Superserie | Pareja viable por estación, movimiento, capacidad, rondas y ausencia de grupo previo; preferir accesorios compatibles | Evita ocupar material incompatible y alterar ejercicios principales arbitrariamente. No basta compartir una etiqueta de equipo |
| Drop-set/rest-pause | Disponibles como elección sobre una serie programada a FALLO y un ejercicio compatible; no solución automática de tiempo; conservar configuración explícita | Decisión B2 del usuario. La acción debe cambiar la estructura real y mostrar el ahorro/coste calculado antes de aplicar |
| Acción viable | Simular el borrador resultante; ocultar acciones sin cambio o sin beneficio para el objetivo declarado; mostrar delta de tiempo, series e intensidad | Verificación del contrato de la acción, no una promesa genérica de «más recuperación» |
| Cardio | Respetar sesión autónoma, modalidad, duración e intención; evitar propuestas de RPE de fuerza, superseries de fuerza o localización «antes/después» cuando no hay fuerza | Decisión A2 y estructura real de CardioDetails |

**Prioridad y techo.** Presupuesto global de tres tarjetas: una principal y hasta dos secundarias, máximo tres acciones por tarjeta. Ordenar por relevancia al objetivo, confianza, efecto y coste del cambio. Una restricción explícita incumplida tiene prioridad sobre una estimación débil. No usar severidades de lesión: etiquetas «Información», «Ajuste opcional» y «Límite que elegiste» son suficientes para este flujo.

**Deduplicación.** Clave por objetivo + ejercicio/serie + tipo de acción y músculo canónico cuando corresponda. Fusionar causas que proponen el mismo cambio. Simular la selección conjunta, resolver incompatibilidades y recalcular tras cada aplicación. Si una sola acción ya corrige dos motivos, mostrar una acción con dos razones. Dismissal estable hasta que cambie de forma relevante la dosis o el contexto.

**Tono.** Describir un dato verificable, explicar su relación con el objetivo y ofrecer una posibilidad. Incluir modalidad y alcance de la modificación. No inferir lesiones ni prometer protección. La persona puede mantener una sesión deliberadamente costosa; el asistente debe poder explicar por qué encaja.

| Antes | Después propuesto |
|---|---|
| «Sesión demasiado exigente… puede afectar tu recuperación…» | «Coste muscular estimado: 62. Si hoy buscas una sesión más corta, puedes quitar una serie del accesorio de hombro. El movimiento principal se mantiene.» Solo nombrar ese accesorio si el plan lo identifica |
| «Entrenaste SBD… al día siguiente… Baja…» para dos sesiones planificadas | «Las dos sesiones previstas comparten pectorales y cuádriceps, con unas 24 h entre ellas. El solapamiento estimado es bajo; puedes mantener el plan y revisar cómo llegas ese día.» |
| «La sesión se alarga demasiado» con ocho minutos internos de exceso | «La estimación supera tus 30 min. Esta opción ahorra unos 2 min; la sesión seguiría por encima del objetivo.» Usar siempre las cifras del reloj compartido |
| «Acortar descansos en Sentadilla (drop-set)» | No ofrecer ese cambio si la serie no está a fallo o no ahorra tiempo. Para una serie compatible: «Convertir esta última serie en drop-set: cambia la estructura y el esfuerzo indicados en la vista previa» |
| «Bajar intensidad para proteger columna» | «La mayor parte del coste espinal estimado viene de [ejercicio confirmado]. Si prefieres reducirlo hoy, esta variante cambia el coste estimado de X a Y.» Solo mostrar X/Y tras simular |

### 2.8 Pruebas propuestas y paquetes

- Fixtures del asset y recetas S1–S13: salida exacta actual documentada; nuevos resultados esperados según objetivo, con cambios intencionados identificados.
- Límites 59/60 y 9/10/24/25/44/45 de interferencia para comprobar bandas, tono y ausencia de conclusiones sobre lesiones. Los porcentajes son fronteras de producto, no validaciones clínicas.
- Mismas series bajo técnica/descarga/acumulación: la decisión cambia de forma explicable por contexto, sin inventar un RIR global.
- Tiempo visible = tiempo usado por reglas; excesos de 7 y 8 min frente a 30 no producen un salto de diagnóstico; ahorro calculado sobre segundos reales.
- Aplicar cada acción sobre un borrador y validar el efecto: drop no debe anunciar ahorro si añade segundos; grupo existente no se ofrece de nuevo; descanso de grupo se modifica de verdad; RM cambia una magnitud válida.
- Seleccionar dos motivos que apuntan a una serie elimina solo una serie; la suma de acciones mantiene rondas e identidad.
- El músculo secundario de la banca no provoca quitar la banca cuando el objetivo protege ese movimiento y existe un accesorio elegible.
- Una sesión planificada nunca usa «entrenaste»; historial realizado y semana planificada no se cuentan dos veces; recuperación ligera y cardio autónomo se reconocen.
- Techo de tres tarjetas y tres acciones, deduplicación estable y casos en que la salida correcta es vacía.

Las pruebas existentes de SessionAssistantEngineTest.kt:20-44 usan customDrain para fijar los disparadores, y 59-105 comprueban que aparece solapamiento. Son útiles para el contrato actual, pero no demuestran calidad con las dosis finales del catálogo ni beneficio de aplicar una acción. Se leyeron, sin ejecutarlas.

| Paquete | Prioridad | Tamaño | Entregable |
|---|---|---|---|
| S1. Corregir acciones engañosas y tono | P0 | M | Cada acción apunta a un cambio válido y muestra su efecto; drops sujetos al contrato de fallo |
| S2. Contexto y reloj compartido | P0 | M | Una duración y una proyección; objetivos/roles/fase presentes en las decisiones |
| S3. Política, prioridades y deduplicación | P1 | M | Tres tarjetas; fusión de motivos; bandas y lenguaje coherentes |
| S4. Historial y personalización | P1 | L | Referencias comparables, confianza y distinción realizado/planificado |
| S5. Retirada de reglas duplicadas | P1 | S | Una ruta de decisión, con referencias actualizadas y las regresiones de esta auditoría |


## 3. Crear ejercicio: parecidos, descripción e imágenes

### 3.1 Motores y recorrido actuales

| Pieza | Función real | Hallazgo |
|---|---|---|
| ExerciseMatchLexicon.kt:15-186 | Normaliza acentos, singular/plural ligero, sinónimos y Levenshtein; calcula similitud por conjuntos | Las stop words no se eliminan dentro de tokenSimilarity. Las equivalencias son por token, insuficientes para distinguir bench press, leg curl y press genérico |
| ExercisePatternDetector.kt:31-175 | Reglas por frases fuertes/débiles; primero gana entre reglas de igual confianza | bench press cae en press genérico → empuje vertical; curl femoral → flexión de codo. El vocabulario de patrones no coincide siempre con el catálogo |
| SmartExerciseCreator.kt:72-82,90-164 | Dos pasadas de preview; inicialmente implemento vacío se convierte en Mancuerna; el primer match decide material para la segunda | Sesgo inicial hacia mancuernas; no extrae como restricción el material escrito; solo recorre las configuraciones por defecto de las definiciones |
| SmartExerciseCreator.kt:193-244 | Ranking de hasta ocho candidatos por similitud, material, patrón, frase y músculo | Comparación contra alias si existe, excluyendo entonces el nombre canónico como campo independiente. No hay filtro de contradicción ni mínimo de confianza semántica |
| ExerciseMatchEngine.kt:183-342 | Promedios de métricas, unión de músculos y cues de varios matches | La mezcla puede crear un ejercicio anatómicamente incoherente, aunque el primer parecido sea correcto |
| ExerciseCatalogV2Resolver.kt:152-232 | Recuperación V2 por términos AND, índice amplio, ranking y propuesta de configuración | Mejor que el creador en esta muestra, pero admite coincidencias por texto técnico ajeno a la identidad y por opciones de distintas configuraciones |
| AprendeSimilarityEngine.kt:52-119 | Relación entre dos ejercicios ya estructurados; bandas de equivalencia/patrón/transferencia anatómica | No es un motor de búsqueda por nombre. Puede servir después de resolver una referencia, con filtros y explicación |
| AutoExerciseDescription.kt:7-19 | Dos frases a partir de nombre, músculos PRIMARY, patrón y equipo | Verbaliza las inferencias como hechos; no valida ni añade instrucciones específicas |
| ExercisePickerV2Catalog.kt:1257-1281,1629-1650 | Acción de crear solo en estado sin resultados; createAutomatic + upsert inmediato | No muestra antes los parecidos ni una vista de revisión de la inferencia en ese camino |
| CatalogCustomExerciseEditor.kt:129-156,195-240 | Edición posterior de material, texto y músculos; banner de patrón | Permite corrección, pero el texto generado no aparece como borrador dentro del campo de descripción antes de guardar |

exerciseCatalogSnapshot combina las 228 definiciones por defecto y los propios, no las 553 configuraciones como candidatos independientes (ExerciseCatalogV2LegacyAdapter.kt:23-35; ExerciseDatabase.kt:100-114). La muestra de abajo usa solo las 228 entradas nativas, sin propios previos; un overlay personal distinto cambiaría el ranking. El creador recuerda una instantánea en la composición (ExercisePickerV2Catalog.kt:1636), de modo que tampoco es una suscripción reactiva al catálogo.

### 3.2 Medición de 36 nombres

**Protocolo.** Se replicaron tokenSimilarity, detector, ranking privado de SmartExerciseCreator y las dos pasadas de createAutomatic, utilizando el catálogo real proyectado por el adaptador. Por separado se replicó ExerciseCatalogV2Resolver.search sin filtros, con localización de material/músculos, AND, bonus y desempates. Los empates de Smart se resolvieron conservando el orden del catálogo; V2 usa puntuación, longitud y orden alfabético de nombre.

La intención relevante se anota en la columna «Referencia aceptada». Se permiten variantes próximas explícitas cuando cumplen la intención: pausa/cerrado/Spoto como parecidos a banca genérica; Seal como parecido al remo apoyado; jalón convencional/cerrado para el agarre neutro; lateral de pie/sentada sin estación indicada. No se consideran equivalentes un curl de brazo y uno femoral, press horizontal y vertical, ni rumano y convencional cuando la variante está especificada.

Estas métricas evalúan **relevancia de la definición de referencia**. No certifican material, estación, agarre o lateralidad del resultado final; de hecho, el material se informa aparte y revela errores adicionales. Por ejemplo, una definición acertada de curl bayesiano con mancuerna sigue siendo un mal ejercicio creado si se escribió polea. El caso 7 considera como objetivo peso muerto convencional con trap bar: encontrar otra bisagra en primer lugar no identifica esa intención.

- P@1: consultas con primer resultado relevante / 36.
- Acierto@3: consultas con al menos un resultado relevante entre los tres primeros / 36.
- P@3: resultados relevantes / 108 posiciones posibles. Si se devuelven menos de tres, los huecos cuentan como no relevantes. No se llama «precisión» al mero acierto@3.
- La lista de referencias aceptadas es finita; P@3 no exige que haya tres referencias igualmente apropiadas para cada consulta. Se informa también acierto@3 por ser más útil para elegir un parecido.
- Es una muestra exploratoria de nombres cotidianos con errores, inglés/español, sinónimos y material; no una estimación de precisión poblacional ni un benchmark independiente de producción.

| Motor | P@1 | Acierto@3 | P@3 |
|---|---:|---:|---:|
| Creador automático, dos pasadas | **15/36 = 41,7 %** | **23/36 = 63,9 %** | **27/108 = 25,0 %** |
| Búsqueda V2 | **26/36 = 72,2 %** | **31/36 = 86,1 %** | **35/108 = 32,4 %** |

**Resultados del creador.** «1/3» en la última columna significa P@1 binaria / acierto@3 binario. La puntuación es el score bruto del primer resultado, no una probabilidad; puede superar 1.

| N | Nombre escrito | Referencia aceptada | Equipo final inferido | Primeros tres parecidos, en orden | Score primero | 1/3 |
|---|---|---|---|---|---:|---|
| 1 | sentadila trasera barra alta | high_bar_back_squat | Barra | 1. high_bar_back_squat<br>2. low_bar_back_squat<br>3. quads_sentadilla_zercher_barra_recta | 0.795 | 1/1 |
| 2 | sentadilla barra baja | low_bar_back_squat | Mancuerna | 1. quads_sentadilla_copa<br>2. low_bar_back_squat<br>3. quads_sentadilla_zercher_barra_recta | 0.692333 | 0/1 |
| 3 | front squat barra | front_squat | Mancuerna | 1. quads_sentadilla_copa<br>2. front_squat<br>3. high_bar_back_squat | 0.657333 | 0/1 |
| 4 | peso mueto rumano con mancuernas | romanian_deadlift | Mancuerna | 1. biceps_curl_zottman<br>2. tate_press<br>3. back_remo_gorilla_mancuernas | 0.574 | 0/0 |
| 5 | RDL con barra | romanian_deadlift | Mancuerna | 1. biceps_curl_zottman<br>2. tate_press<br>3. back_remo_gorilla_mancuernas | 0.498333 | 0/0 |
| 6 | peso muerto sumo barra | sumo_deadlift | Barra | 1. good_morning_zercher<br>2. sumo_deadlift<br>3. romanian_sumo_deadlift | 1.11475 | 0/1 |
| 7 | deadlift trap bar | conventional_deadlift | Barra | 1. good_morning<br>2. good_morning_zercher<br>3. conventional_deadlift | 0.650333 | 0/1 |
| 8 | press banca con barra | bench_press, paused_bench_press, close_grip_bench_press, tren_superior_press_spoto_barra | Barra | 1. paused_bench_press<br>2. bench_press<br>3. close_grip_bench_press | 1.14625 | 1/1 |
| 9 | bench press dumbbells | bench_press | Mancuerna | 1. arnold_press<br>2. tate_press<br>3. tren_superior_squeeze_press_mancuernas | 0.806667 | 0/0 |
| 10 | pres inclinado mancuernas | incline_bench_press | Mancuerna | 1. incline_biceps_curl<br>2. biceps_curl_bayesian<br>3. biceps_curl_zottman | 0.65 | 0/0 |
| 11 | press militar con barra | military_press | Mancuerna | 1. arnold_press<br>2. military_press<br>3. tate_press | 0.765833 | 0/1 |
| 12 | seated shoulder press machine | seated_shoulder_press | Barra | 1. seated_shoulder_press<br>2. military_press<br>3. deltoides_push_press | 1.09875 | 1/1 |
| 13 | remo pendlay barra | pendlay_row | Barra | 1. pendlay_row<br>2. seal_row<br>3. conventional_row | 1.012 | 1/1 |
| 14 | remo pecho apoyado mancuernas | chest_supported_row, seal_row | Mancuerna | 1. chest_supported_row<br>2. back_remo_gorilla_mancuernas<br>3. back_remo_renegado_mancuernas | 0.94875 | 1/1 |
| 15 | lat pulldown polea | lat_pulldown, close_grip_lat_pulldown | Mancuerna | 1. lying_pullover<br>2. lat_pulldown<br>3. pullover | 0.74 | 0/1 |
| 16 | jalon al pecho agarre neutro | lat_pulldown, close_grip_lat_pulldown | Polea | 1. lat_pulldown<br>2. close_grip_lat_pulldown<br>3. pullover | 1.041 | 1/1 |
| 17 | dominadas pronas | pull_up | Peso Corporal | 1. pull_up<br>2. rack_chin<br>3. negative_pull_up | 0.785 | 1/1 |
| 18 | chin ups supinas | pull_up | Peso Corporal | 1. pull_up<br>2. rack_chin | 0.695833 | 1/1 |
| 19 | curl martiyo mancuernas | hammer_curl | Mancuerna | 1. biceps_curl_zottman<br>2. incline_biceps_curl<br>3. hammer_curl | 0.904667 | 0/1 |
| 20 | bayesian curl polea | biceps_curl_bayesian | Mancuerna | 1. biceps_curl_bayesian<br>2. biceps_curl_sentado_banco_plano<br>3. biceps_curl_zottman | 0.946667 | 1/1 |
| 21 | curl predicador maquina | preacher_curl | Mancuerna | 1. biceps_curl_bayesian<br>2. biceps_curl_sentado_banco_plano<br>3. biceps_curl_zottman | 0.692333 | 0/0 |
| 22 | triceps pushdown cuerda | triceps_pushdown | Mancuerna | 1. triceps_rolling_extension<br>2. triceps_patada<br>3. triceps_extension_pjr_mancuerna | 0.798333 | 0/0 |
| 23 | rompecraneos barra ez | triceps_press_frances | Barra | 1. california_press<br>2. z_press<br>3. tren_superior_press_spoto_barra | 0.661667 | 0/0 |
| 24 | lateral raises cable | standing_lateral_raise, seated_lateral_raise | Mancuerna | 1. core_inclinacion_lateral<br>2. seated_lateral_raise<br>3. standing_lateral_raise | 0.512333 | 0/1 |
| 25 | vuelo lateral con mancuernas | standing_lateral_raise, seated_lateral_raise | Mancuerna | 1. biceps_curl_zottman<br>2. tate_press<br>3. back_remo_gorilla_mancuernas | 0.6325 | 0/0 |
| 26 | facepull polea | deltoides_face_pull | Mancuerna | 1. arnold_press<br>2. tren_superior_press_unilateral_polea<br>3. core_lenador_polea | 0.545 | 0/0 |
| 27 | hip thrust barra | hip_thrust | Barra | 1. hip_thrust<br>2. good_morning<br>3. good_morning_zercher | 0.986667 | 1/1 |
| 28 | curl femoral sentado maquina | seated_leg_curl | Mancuerna | 1. biceps_curl_sentado_banco_plano<br>2. biceps_curl_bayesian<br>3. biceps_curl_zottman | 0.8125 | 0/0 |
| 29 | extension de piernas maquina | quads_extension_cuadriceps | Mancuerna | 1. triceps_rolling_extension<br>2. forearms_curl_muneca_inverso_sentado<br>3. triceps_extension_pjr_mancuerna | 0.6325 | 0/0 |
| 30 | prensa piernas | quads_prensa_piernas | Máquina | 1. quads_prensa_piernas<br>2. quads_sentadilla_sin_carga<br>3. core_elevacion_piernas | 0.9 | 1/1 |
| 31 | bulgarian split squat dumbbells | bulgarian_split_squat | Mancuerna | 1. bulgarian_split_squat<br>2. quads_sentadilla_copa<br>3. incline_biceps_curl | 0.79875 | 1/1 |
| 32 | goblet squat mancuerna | quads_sentadilla_copa | Mancuerna | 1. quads_sentadilla_copa<br>2. biceps_curl_zottman<br>3. tate_press | 0.657333 | 1/1 |
| 33 | plancha abdominal | core_plancha | Peso Corporal | 1. core_plancha<br>2. dead_bug<br>3. side_plank | 0.98 | 1/1 |
| 34 | pallof press cable | core_press_pallof | Mancuerna | 1. arnold_press<br>2. tate_press<br>3. tren_superior_squeeze_press_mancuernas | 0.626667 | 0/0 |
| 35 | ab wheel | core_rueda_abdominal | Mancuerna | Sin resultado | — | 0/0 |
| 36 | calf raise maquina | calf_raise | Máquina | 1. calf_raise<br>2. quads_zancada_inversa_maquina_hack<br>3. quads_zancada_inversa_maquina_v_squat | 0.816667 | 1/1 |

**Resultados de V2 para las mismas 36 consultas.** La numeración remite a la tabla anterior. Se evalúa la misma lista de referencias; el score V2 tampoco es comparable directamente con el del creador.

| N | Primeros tres resultados V2, en orden | Score primero | 1/3 |
|---|---|---:|---|
| 1 | 1. high_bar_back_squat<br>2. quads_sentadilla_cajon<br>3. front_squat | 162 | 1/1 |
| 2 | 1. low_bar_back_squat<br>2. bulgarian_zercher<br>3. high_bar_back_squat | 204 | 1/1 |
| 3 | 1. front_squat<br>2. quads_sentadilla_sumo_frontal<br>3. quads_sentadilla_anderson_frontal_barra_recta | 128 | 1/1 |
| 4 | 1. romanian_deadlift<br>2. romanian_sumo_deadlift | 156 | 1/1 |
| 5 | 1. good_morning_zercher<br>2. jm_press<br>3. pendlay_row | 102 | 0/0 |
| 6 | 1. sumo_deadlift<br>2. romanian_sumo_deadlift<br>3. hams_peso_muerto_sumo_deficit | 190 | 1/1 |
| 7 | 1. romanian_deadlift<br>2. romanian_sumo_deadlift<br>3. conventional_deadlift | 150 | 0/1 |
| 8 | 1. bench_press<br>2. tren_superior_press_spoto_barra<br>3. paused_bench_press | 150 | 1/1 |
| 9 | 1. california_press<br>2. bench_press<br>3. decline_bench_press | 136 | 0/1 |
| 10 | 1. incline_bench_press<br>2. z_press<br>3. bench_press | 150 | 1/1 |
| 11 | 1. military_press | 150 | 1/1 |
| 12 | 1. calf_raise<br>2. seated_shoulder_press<br>3. seated_machine_pullover | 176 | 0/1 |
| 13 | 1. pendlay_row | 150 | 1/1 |
| 14 | 1. chest_supported_row<br>2. seal_row<br>3. back_remo_gorilla_mancuernas | 190 | 1/1 |
| 15 | 1. pullover<br>2. lat_pulldown<br>3. triceps_pushdown | 164 | 0/1 |
| 16 | 1. close_grip_lat_pulldown | 174 | 1/1 |
| 17 | 1. pull_up<br>2. rack_chin | 142 | 1/1 |
| 18 | Sin resultado | — | 0/0 |
| 19 | 1. hammer_curl | 116 | 1/1 |
| 20 | 1. biceps_curl_bayesian | 150 | 1/1 |
| 21 | 1. preacher_curl | 150 | 1/1 |
| 22 | 1. triceps_pushdown<br>2. pullover | 92 | 1/1 |
| 23 | Sin resultado | — | 0/0 |
| 24 | 1. calf_raise<br>2. standing_lateral_raise<br>3. seated_lateral_raise | 116 | 0/1 |
| 25 | 1. reverse_pec_fly<br>2. reverse_curl<br>3. glutes_puente_gluteos | 130 | 0/0 |
| 26 | Sin resultado | — | 0/0 |
| 27 | 1. hip_thrust | 150 | 1/1 |
| 28 | 1. seated_leg_curl | 176 | 1/1 |
| 29 | 1. quads_extension_cuadriceps<br>2. stiff_leg_deadlift<br>3. quads_zancada_inversa_maquina_v_squat | 150 | 1/1 |
| 30 | 1. quads_prensa_piernas<br>2. quads_sentadilla_copa<br>3. quads_sentadilla_hack | 128 | 1/1 |
| 31 | 1. bulgarian_split_squat | 140 | 1/1 |
| 32 | 1. quads_sentadilla_copa | 114 | 1/1 |
| 33 | 1. core_plancha<br>2. side_plank | 518 | 1/1 |
| 34 | 1. core_press_pallof | 150 | 1/1 |
| 35 | 1. core_rueda_abdominal | 150 | 1/1 |
| 36 | 1. calf_raise | 114 | 1/1 |

Configuraciones que muestran límites adicionales de estas métricas:

- Consulta 4: V2 propone romanian_deadlift__bilateral__dumbbells; el creador se va a Curl Zottman, Tate y Remo Gorilla.
- Consulta 7: V2 coloca primero romanian_deadlift__bilateral__hex_bar y tercero conventional_deadlift__bilateral__hex_bar; la familia/material no bastan para diferenciar la variante solicitada.
- Consulta 12: el creador acierta seated_shoulder_press como definición, pero termina con Barra. V2 pone calf_raise__bilateral__seated_machine primero y seated_shoulder_press__smith_machine segundo pese a que se buscó un press de hombro sentado en máquina.
- Consulta 20: la búsqueda V2 encuentra biceps_curl_bayesian__cable__supinated; el creador conserva Mancuerna desde la configuración por defecto.
- Consulta 24: V2 sitúa calf_raise__bilateral__cable antes de las elevaciones laterales de hombro.
- Consulta 16: el único hit V2 es close_grip_lat_pulldown, sin configuración sugerida; que una definición pase el AND no garantiza haber identificado el agarre.

**Controles de abstención**, adicionales a las 36 consultas:

| Entrada | Salida del creador | Interpretación |
|---|---|---|
| XQZ 123 | Sin matches; patrón null; descripción null | Abstención correcta |
| mi ejercicio inventado | reverse_curl, score 0,335; patrón null; genera texto de Antebrazo | Un parecido débil se convierte en afirmación anatómica |
| dragón lunar mancuernas | Zottman/Tate/Gorilla, score 0,498333; patrón null; mezcla cinco primarios | El material basta para contaminar el resultado; debe permitir crear con datos desconocidos |
| press de piernas con banda | Arnold primero; patrón de empuje vertical MEDIUM; equipo Mancuerna | El detector no resuelve la intención de pierna y el creador no respeta banda |

Hay que distinguir las dos superficies: la acción inline de crear solo aparece cuando la búsqueda V2 global y la de propios no devuelven resultados (ExercisePickerV2Catalog.kt:355-367,1257-1267). En esta muestra, eso ocurre en las consultas 18,23,26, suponiendo ausencia de propios. Las demás mediciones del creador son evaluaciones directas de su motor y de la inferencia usada al editar; no se afirma que todas esas creaciones se ofrezcan desde la UI actual. Cuando V2 produce un falso positivo, además puede impedir que aparezca «Crear ejercicio».

### 3.3 Causas raíz y arreglo de parecidos

**P0 — Material por defecto convertido en evidencia.** preview usa Mancuerna si implementoId es vacío/desconocido (SmartExerciseCreator.kt:91-94). createAutomatic primero llama justamente con implemento vacío y después usa el material del primer match (72-82). El nombre nunca se analiza para imponer «barra», «polea» o «máquina». Un bonus de 0,30 por mismo equipo o 0,20 por grupo compite con similitud de nombre × 0,7. Así un término genérico de material puede fijar una referencia incorrecta y reforzarla en la segunda pasada.

**P0 — Datos incoherentes por mezcla.** El score suma hasta 0,18 por patrón, 0,22 por frase y 0,12 por músculo; el mínimo admitido es apenas > 0,15 y basta similitud positiva (SmartExerciseCreator.kt:207-243). inferFromMatches mezcla hasta ocho referencias, toma los seis músculos con mayor score por rol, los cues de varios ejercicios y promedios de EFC/CNC/SSC (ExerciseMatchEngine.kt:221-277,315-325). buildInvolvedMuscles solo reajusta un músculo si existe en el primer match: los primarios ajenos sobreviven (SmartExerciseCreator.kt:177-188). D1 y D4, abajo, tienen el primer match correcto y aun así generan primarios falsos.

Las métricas de los matches ya dependen del implemento del catálogo, pero inferFromMatches vuelve a multiplicarlas por material y por una clasificación axial genérica (ExerciseMatchEngine.kt:236-250). Por ejemplo, polea/máquina aplican EFC × 0,8 y CNC × 0,6. Eso no es una transformación validada de la configuración elegida. A la vez, TTC y axial se copian solo del primero. El resultado combina fuentes incompatibles como si pertenecieran a un ejercicio único.

**P1 — Léxico y patrones incompletos.** NAME_TOKENS_TO_IGNORE solo decide si la consulta está vacía; tokenSimilarity sigue contando con/de/otros términos (SmartExerciseCreator.kt:200,240,246-253; ExerciseMatchLexicon.kt:166-185). El alias se concatena en una sola bolsa y puede ocultar un nombre canónico más pertinente. RDL se equipara a deadlift genérico y se pierde «rumano»; pull up/chin up/facepull no se tokenizan de forma uniforme (ExerciseMatchLexicon.kt:46-85). El stem se aplica antes del diccionario: varias claves plurales no se consultan tal cual. El detector usa frases sin tolerancia a typos y precedencia por orden; press genérico favorece vertical_push y curl genérico favorece elbow_flexion (ExercisePatternDetector.kt:50-100,154-175). El catálogo utiliza deadlift, romanian_deadlift o unilateral_knee_dominant, que no siempre coinciden con hip_hinge/knee_dominant del detector. No hay una ontología común que explique esas relaciones.

**P1 — Recuperación V2 excesivamente amplia.** buildIndex concatena nombre, descripción, aliases, nombre de familia, opciones, equipos, músculos y cues de todas las configuraciones (ExerciseCatalogV2Resolver.kt:235-271). El AND exige presencia de palabras en ese conjunto, no una intención coherente ni una sola configuración que satisfaga todas las restricciones. bestConfigurationMatch suma términos sin vetar contradicciones (307-329). De ahí que un músculo o una palabra en una comparación técnica ayuden a recuperar un movimiento ajeno. Los bonus por equipo/alias no compensan siempre el problema.

Además, la prioridad editorial de «remo/row» conserva seis IDs back_* retirados respecto de las definiciones actuales conventional_row, pendlay_row, chest_supported_row, etc. (ExerciseCatalogV2Resolver.kt:530-545; JSON:1). Esa tabla debe validarse contra los IDs actuales: sus bonus específicos no se aplican a las definiciones pretendidas.

**P1 — Confianza falsa.** manualRecommended depende de ausencia de matches o patrón no HIGH; no mira score, margen entre candidatos, contradicción ni calidad de los músculos (SmartExerciseCreator.kt:155-157). «peso mueto rumano con mancuernas» activa hip_hinge HIGH y permite una inferencia cuya primera referencia es Zottman. El score bruto no debe mostrarse como «porcentaje de coincidencia».

**AprendeSimilarityEngine.** El motor ya usa identidad, intención, anatomía por rol y articulaciones; es una base mejor para relaciones estructuradas. Sus pesos son: mismo replacementGroup 100; preservesIntent 28; misma definición 22; patrón 18; primario compartido 12, secundario 8, estabilizador 5 (o músculo genérico 7); articulación 6; región 4; cadena 3; lateralidad 2; equipo 1 (AprendeSimilarityEngine.kt:71-109). Las bandas son EQUIVALENT por intención, PATTERN_VARIANT por definición/patrón y ANATOMICAL_TRANSFER por cualquier músculo o articulación compartidos.

Límites: no recibe un nombre libre ni confianza de su interpretación; admite transferencia por una articulación; material y lateralidad pesan muy poco; ordena por score, no primero por banda; no incluye rango de movimiento ni modo de resistencia en su contrato. No debe utilizarse para copiar automáticamente una imagen técnica o certificar equivalencia de prescripción. Puede relacionar candidatos después de resolver una configuración válida y filtrar lo que el usuario pidió explícitamente.

**Diseño propuesto, determinista y explicable:**

1. Analizar la consulta en movimiento/variante, material, estación, lateralidad, agarre y altura. Preservar las frases distintivas antes de expandir sinónimos: bench press, leg curl, RDL, face pull y Pallof. La corrección de typo debe conservar alternativas y permitir abstención.
2. Recuperar por V2 como fuente única y añadir propios mediante un índice con el mismo contrato. Campos separados con prioridad identidad/alias exacto > variante > configuración > explicación técnica. El texto de cues sirve para ampliar candidatos, no para vencer por sí solo a una identidad coherente.
3. Exigir compatibilidad de las restricciones explícitas dentro de una configuración materializada. Si no existe esa variante, indicar que es un ejercicio propio con datos pendientes; un parecido no debe convertirse en una configuración inventada.
4. Resolver patrones con una ontología compartida: bisagra como familia, convencional/rumano como variantes; distinguir flexión de codo y de rodilla. Las aliases RDL y rompecráneos deben apuntar a identidades, no solo a tokens vagos.
5. Reordenar por intención y variante; usar Aprende para explicar «mismo patrón» o «transferencia», con bandas separadas de equivalencia. Mostrar tres referencias diversas con motivo concreto y material visible; evitar tres variantes casi idénticas cuando no añaden valor.
6. Umbral de abstención y confianza calibrados con nombres etiquetados y controles negativos. Elegir cortes según errores de autocompletado en un conjunto de validación; no inventar que score = 0,8 equivale a 80 %.
7. Para inferir un propio, utilizar **una referencia/configuración confirmada** y copiar sus roles/cues compatibles con procedencia. Sin referencia suficiente, mantener anatomía/carga axial desconocidas y pedir completar los campos necesarios dentro del editor; no mezclar ocho ejercicios.
8. Mantener nombre e ID custom:uuid propios. Guardar referenceDefinitionId/configurationId como procedencia separada de la identidad del ejercicio; actualizar el catálogo no debe cambiar silenciosamente el propio ni reescribir sus datos manuales.

### 3.4 Diez descripciones: salida actual y evaluación

La salida se reprodujo recorriendo la inferencia real de las dos pasadas y AutoExerciseDescription. Las diez filas son una **muestra de diagnóstico** escogida para cubrir patrones, material, errores de nombre y ausencia de referencia; no se presenta su tasa de errores como la de todas las descripciones de la app. Se muestran completas, incluido el texto genérico compartido.

| Caso | Qué genera hoy | Incorrecto o no justificado | Qué falta / cambio necesario |
|---|---|---|---|
| D1 (consulta 1) | «sentadila trasera barra alta es un ejercicio orientado a Cuádriceps y Glúteos y Pectorales y Dorsales y Trapecio. Se realiza con Barra, priorizando un rango controlado y una progresión técnica estable.» | Músculos ajenos al patrón: pectorales, dorsales y trapecio se presentan como objetivos de la sentadilla. El typo impide detectar el patrón, aunque el primer parecido es la sentadilla correcta. | Apoyo, colocación de barra alta, trayectoria y secuencia; usar solo los músculos del perfil confirmado. |
| D2 (consulta 4) | «peso mueto rumano con mancuernas es un ejercicio orientado a Bíceps y Tríceps y Dorsales y Pectorales y Isquiosurales y Glúteos dentro del patrón de bisagra de cadera. Se realiza con Mancuerna, priorizando un rango controlado y una progresión técnica estable.» | La referencia es Curl Zottman. Mezcla flexión de codo, tríceps y pecho con una bisagra; la confianza del detector es HIGH, pese a la inferencia incompatible. | Identificar rumano y material; describir bisagra de cadera y apoyo; separar los roles de isquios/glúteos de estabilizadores. |
| D3 (consulta 9) | «bench press dumbbells es un ejercicio orientado a Deltoides y Pectorales y Bíceps y Dorsales dentro del patrón de empuje vertical. Se realiza con Mancuerna, priorizando un rango controlado y una progresión técnica estable.» | El detector llama empuje vertical a una banca y se añaden bíceps/dorsales como primarios. El material sí es mancuerna. | Banco plano, apoyo y empuje horizontal; la palabra genérica press no debe ganar a bench press. |
| D4 (consulta 14) | «remo pecho apoyado mancuernas es un ejercicio orientado a Dorsales y Pectorales dentro del patrón de tirón horizontal. Se realiza con Mancuerna, priorizando un rango controlado y una progresión técnica estable.» | Pectorales se incorporan como primarios aunque el primer match correcto es un remo con pecho apoyado. | Apoyo del tórax, dirección del tirón, agarre/codos y rol de cada músculo; no mezclar otro ejercicio de los ocho candidatos. |
| D5 (consulta 20) | «bayesian curl polea es un ejercicio orientado a Bíceps dentro del patrón de flexión de codo. Se realiza con Mancuerna, priorizando un rango controlado y una progresión técnica estable.» | Patrón y bíceps son coherentes, pero el ejercicio se declara con mancuerna pese a «polea». | Configuración de polea y posición del hombro; preservar el material escrito/confirmado. |
| D6 (consulta 24) | «lateral raises cable es un ejercicio orientado a Abdomen y Deltoides y Trapecio dentro del patrón de abducción de hombro. Se realiza con Mancuerna, priorizando un rango controlado y una progresión técnica estable.» | Primer parecido Inclinación Lateral; abdomen pasa a primario y se declara mancuerna aunque se pidió cable. | Abducción del hombro, estación de polea y lateralidad confirmadas; evitar confundir lateral de tronco con elevación lateral. |
| D7 (consulta 28) | «curl femoral sentado maquina es un ejercicio orientado a Bíceps dentro del patrón de flexión de codo. Se realiza con Mancuerna, priorizando un rango controlado y una progresión técnica estable.» | Asigna bíceps y flexión de codo a un curl femoral sentado; también cambia máquina por mancuerna. | Flexión de rodilla, apoyo sentado y ajuste de máquina; desambiguar curl antes de escoger músculos. |
| D8 (consulta 33) | «plancha abdominal es un ejercicio orientado a Core y Glúteo Medio y Aductores dentro del patrón de anti-extensión. Se realiza con Peso Corporal, priorizando un rango controlado y una progresión técnica estable.» | El patrón anti-extensión es coherente, pero glúteo medio y aductores se convierten en objetivos por unión con otras planchas/casos del catálogo. | Apoyos de antebrazos y pies y mantener la posición; los roles deben salir de una configuración concreta, no de nombres compartidos. |
| D9 (consulta 34) | «pallof press cable es un ejercicio orientado a Deltoides y Pectorales y Core dentro del patrón de anti-rotación. Se realiza con Mancuerna, priorizando un rango controlado y una progresión técnica estable.» | El patrón anti-rotación se detecta, pero el primer match es Press Arnold; se añaden deltoides/pectorales y se cambia cable por mancuerna. | Posición lateral respecto de la polea y resistencia a la rotación; preferir la señal distintiva Pallof al término press. |
| D10 (consulta 35) | null: no se genera descripción | No genera descripción aunque detecta anti-extensión con confianza HIGH: el nombre inglés no encuentra ningún match en el creador. La búsqueda V2 sí encuentra la rueda. | Usar la referencia validada de V2; si faltan datos, ofrecer un borrador limitado y edición, con la falta de información visible. |

Causa inmediata: AutoExerciseDescription.kt:11-19 enumera los PRIMARY recibidos y siempre añade «priorizando un rango controlado y una progresión técnica estable». No consulta ejecución, estación, lateralidad, agarre, altura, resistencia, articulaciones ni datos de la configuración. En createAutomatic, la descripción se genera si existe cualquier match; un patrón detectado sin match no basta (SmartExerciseCreator.kt:112-124).

La causa importante está aguas arriba: el generador no distingue datos revisados, inferidos y desconocidos. Generar texto más fluido sin corregir esa procedencia haría más convincentes los mismos errores. La descripción manual sí se respeta; debe preservarse ese contrato (SmartExerciseCreator.kt:113; CatalogCustomExerciseEditor.kt:129,146).

**Propuesta concreta:**

- Borrador por plantilla de patrón y configuración: una frase que identifica movimiento/material/estación y una o dos instrucciones tomadas de cues compatibles. Seleccionar uno o dos primarios confirmados y, si aporta algo, distinguir secundarios/estabilizadores; no enumerar una unión de seis primarios.
- Datos necesarios separados del texto. Un campo desconocido se omite o se muestra pendiente; la falta de dato axial no se convierte en «sin carga». No inferir técnica exacta desde una imagen parecida.
- Mostrar el borrador completo antes de guardar, editable, con «Basado en…» y posibilidad de regenerar solo las partes automáticas. Registrar origen (usuario/generado), referencia, revisión de catálogo y versión del generador. Mantener las ediciones del usuario al cambiar otros campos.
- Si el nombre es contradictorio o solo identifica un patrón, redactar un borrador limitado con esos hechos; ofrecer completar la configuración dentro del flujo. La confianza no sustituye a la elección del usuario.
- La versión inicial puede ser local y determinista; no necesita introducir un proveedor de generación remota para resolver los fallos observados.

Ejemplos de salida **después de confirmar la configuración y sus cues**, no resultados del motor actual:

- Rumano con mancuernas: «Bisagra de cadera con mancuernas, orientada a isquiosurales y glúteos. Lleva la cadera hacia atrás mientras las cargas descienden cerca de las piernas y vuelve a extender la cadera.» La redacción final debe derivarse del perfil confirmado.
- Curl femoral sentado: «Flexión de rodilla en máquina sentada, orientada a los isquiosurales. Ajusta los apoyos de la máquina y flexiona las rodillas sin perder la posición del tronco.» No asignar bíceps del brazo.
- Pallof en polea: «Ejercicio de anti-rotación con polea. Extiende los brazos delante del cuerpo manteniendo el tronco orientado al frente.» La estación y lateralidad solo se añaden si están confirmadas.

### 3.5 Imágenes: lo que existe y cómo conectarlo

**Existe hoy:**

- ExerciseMuscleInfo incluye images y videos opcionales (ExerciseMuscleInfo.kt:64-66). El propio completo se serializa en custom_exercises.data de Room: Entities.kt:483-503; CustomExerciseRepository.kt:50-62. Hay capacidad de persistir metadatos, pero no un selector de portada en el creador.
- SmartExerciseCreator no rellena imágenes, vídeo ni una referencia persistida al parecido; SmartCreatePreview.reference es transitorio (SmartExerciseCreator.kt:126-153,293-299). La edición reconstruye ExerciseMuscleInfo y tampoco tiene contrato para preservar una portada.
- La fila de un propio fuerza thumbnailResId = null (ExercisePickerV2Catalog.kt:1577), y la ficha de respaldo de un ejercicio sin perfil V2 fuerza imageVariants = emptyList (ExerciseCatalogV2Info.kt:170-187). Guardar un string en images por sí solo no resuelve su visualización.
- El catálogo nativo ya dispone de imágenes revisadas por configuración: ReviewedExerciseTechniqueImages y ExerciseTechniqueImageLookup; se exige respetar la configuración autoritativa (ExerciseTechniqueImageLookup.kt:1098-1127). exerciseCatalogImageVariants preserva la selección exacta y puede devolver vacío ante una configuración sin imagen apropiada (ExercisePickerV2Catalog.kt:370-395).
- La ficha consulta medios por identidad y muestra fotos del álbum (ExerciseFicha.kt:130-166,341-347). El álbum de editor/catálogo reutiliza el repositorio, con claves de ejercicio y sin sesión ficticia: screens/workout/components/ExerciseAlbumSheet.kt:129-162. Es una base de visualización, no el formulario de carga/portada de Crear ejercicio.
- WorkoutMedia ya permite sessionKey/sessionId/workoutLogId null y guarda exerciseId/canonicalExerciseId (WorkoutMedia.kt:13-27). WorkoutMediaRepository ofrece submitUriCapture, ingestFile y listForExerciseKeys (277,463,596-633).
- Almacenamiento actual: contexto privado de Android, **filesDir/workout_media/yyyy-MM/<id>.jpg** y **filesDir/workout_media/thumbs/<id>.jpg**, Room workout_media para metadatos (WorkoutMediaStore.kt:11-48). El antiguo ExerciseUserMediaStore guarda en filesDir/exercise_user_media/<clave>/photo_<timestamp>.jpg; no conviene crear otra ruta paralela para la portada.

| Vía | Flujo propuesto | Dónde guardar | Validación y presentación | Tamaño |
|---|---|---|---|---|
| Foto propia | Elegir foto o tomarla desde Crear/Editar; mostrar recorte/rotación y elegir portada; poder quitar/cambiar | Archivo/miniatura en WorkoutMediaStore; registro WorkoutMedia asociado al UUID custom; coverMediaId en los datos del propio | Sobrevive a reinicio y renombrado; error de importación conserva la edición; eliminar una portada no borra otros medios del álbum | M |
| Imagen de un parecido | Elegir una referencia explícita entre resultados validados y confirmar configuración compatible | Referencia estable a configuración/clave de recurso revisado y revisión del catálogo; sin duplicar el archivo del APK ni guardar un entero R.drawable como identidad persistente | Etiqueta «Imagen de referencia» cuando ilustra una variante próxima; cambio de material obliga a revisar compatibilidad; no copia identidad de entrenamiento ni mezcla álbumes | S–M |
| Ilustración del patrón | Usar el sistema de glifos/ilustraciones de patrones como respaldo; ampliar con una biblioteca revisada si se busca más detalle | Patrón + versión de estilo; vector/recurso del APK. Una ilustración personalizada futura podría ir al mismo almacén privado | Etiqueta de ilustración, no demostración técnica exacta. Respaldo consistente cuando no hay foto ni imagen compatible | S para glifo existente; L para biblioteca nueva |

CatalogRowThumbnail ya admite el respaldo por patrón cuando no hay recurso (CatalogExerciseRow.kt:179-199). Falta derivar un patrón fiable para propios y admitir una fuente de imagen tipada, no solo thumbnailResId.

**Contrato de presentación propuesto:** ExerciseCover = UserPhoto(mediaId) / CatalogReference(configurationId, assetKey, revision) / PatternIllustration(patternId, styleVersion). Se puede serializar con valores por defecto en el JSON del propio; si se normaliza en columnas/tablas, hará falta la migración correspondiente. La precedencia debe ser la elección explícita de la persona, con respaldo a ilustración cuando la referencia ya no esté disponible. UUID del propio estable, referencias de presentación separadas de catalogConfigurationId de ejecución.

No generar ni capturar imágenes forma parte de esta auditoría. La propuesta utiliza las capacidades existentes de la app y señala la integración que falta.

### 3.6 Pruebas y paquetes de implementación

**Parecidos e inferencia:**

- Convertir las 36 consultas y los controles en fixtures Kotlin sobre el asset real; verificar top-1, acierto@3 y material/configuración. Conservar estos resultados como línea base, sin fijar los errores como comportamiento deseado.
- Aserciones mínimas: RDL → rumano; curl femoral → flexión de rodilla; bench press → horizontal; Pallof → anti-rotación; facepull/face pull → misma referencia. El equipo explícito prevalece sobre el respaldo.
- Test de configuración completa: ningún resultado satisfecho por mezclar cable de una configuración y estación de otra; variante no materializada queda como propia pendiente, sin fabricar identidad V2.
- Consulta desconocida con material no recibe anatomía certificada; ausencia axial permanece null hasta tener evidencia.
- Primer match correcto no incorpora primarios/cues de matches ajenos. Copia de aportes 1/0,5/0,4, TTC/axial y procedencia desde una referencia confirmada, sin promedios entre movimientos distintos.
- Validar automáticamente que todos los IDs de tablas de prioridades editoriales existen en el asset.
- Aprende: clasificación explicable de equivalente/patrón/transferencia, restricciones de material/lateralidad cuando son explícitas y límite de banda para reutilizar una imagen.

**Descripción:**

- Las diez filas, con configuración corregida, generan solo hechos sustentados por ese perfil. Una sentadilla no adquiere pectorales ni un curl femoral bíceps del brazo.
- Patrón claro sin referencia produce un borrador limitado o estado pendiente; no una descripción completamente inventada ni un formulario vacío sin explicación.
- Edición manual se conserva al guardar, reabrir y cambiar imagen/material; regenerar es explícito y solo sustituye texto automático.
- Los tests existentes SmartExerciseCreatorRegressionTest.kt usan catálogos de una o pocas entradas y comprueban texto no vacío/contiene un músculo. No detectan contaminación al competir las 228 definiciones. También se leyeron SmartExerciseCreatorTest, ExerciseMatchLexiconTest y ExerciseCatalogV2ResolverTest, sin ejecutarlos.

**Imágenes:**

- Crear, importar foto, persistir, reiniciar y reabrir con el mismo UUID; miniatura/portada coherentes en catálogo, ficha y editor.
- Catálogo revisado sin imagen compatible muestra ilustración; cambiar polea por mancuerna no conserva una imagen técnica incompatible silenciosamente.
- Referencia retirada o medio borrado conserva el ejercicio y su descripción; borrar portada conserva las demás fotos.
- Elegir imagen de referencia no altera métricas, IDs de entrenamiento ni medios del ejercicio nativo; lectura de álbum por UUID propio.

| Paquete | Prioridad | Tamaño | Resultado |
|---|---|---|---|
| E1. Recuperación y normalización compartidas | P0 | M | V2 + propios; frases, typos y material; tabla editorial válida |
| E2. Inferencia con procedencia y abstención | P0 | M | Una configuración confirmada; roles/cues coherentes; axial desconocido distinto de cero |
| E3. Descripción editable antes de guardar | P1 | M | Borrador sustentado, procedencia y conservación del texto manual |
| E4. Portada con foto propia | P1 | M | Reutilización de WorkoutMedia, portada estable e integración en las tres superficies |
| E5. Referencia de catálogo e ilustración | P1 | S–M | Imagen compatible elegida y respaldo por patrón, sin mezclar identidades |
| E6. Biblioteca ilustrada ampliada | P2 | L | Recursos coherentes y revisados para patrones sin foto |
| E7. Evaluación sostenida | P1 | S inicial | Corpus de esta auditoría + conjunto separado de nombres nuevos; métricas por idioma/typo/material y abstención |


## 4. Orden recomendado y criterios de cierre

1. **Coherencia del dato:** I1/I2 antes de recalibrar avisos. Impacto, detalle y asistente deben explicar el mismo coste, con cardio y precisión correctamente tratados.
2. **Acciones verificables:** S1/S2. Prioridad a corregir drop-set, cambios de descanso sin efecto, selección de ejercicio equivocado y reloj divergente. La vista previa debe mostrar el borrador que realmente se aplicará.
3. **Referencias fiables:** E1/E2. Compartir recuperación, validar configuración y conservar desconocidos. Ese paso mejora a la vez músculos, carga axial, descripción e imagen de referencia.
4. **Contexto y lenguaje:** S3/S4 con los casos de esta auditoría y nuevos casos independientes. Un aviso debe justificar qué restricción ayuda a cumplir, con qué certeza y a qué coste.
5. **Presentación de propios:** E3/E4/E5. Descripción e imagen visibles y editables antes de guardar; persistencia por UUID; datos manuales protegidos.
6. **Consolidación:** I4/S5 y evaluación E7. Retirar las rutas duplicadas cuando sus consumidores estén migrados. Los tamaños no deben sumarse mecánicamente: varios paquetes comparten proyección, contexto y pruebas.

Cierre verificable de una futura implementación: igualdad de costes entre superficies para fixtures reales; reloj único; ninguna acción anunciada como ahorro que aumente o conserve el tiempo; ninguna serie principal recortada por un secundario sin contexto; nombres ambiguos con abstención explicable; músculos/descripción/portada procedentes de una configuración confirmada; conservación de ejercicios propios al reiniciar y actualizar catálogo.

### Evidencia y límites de esta auditoría

- Se revisó código y datos locales de la revisión indicada; el mapa del editor se usó como orientación y se contrastó con las llamadas actuales.
- El único análisis ejecutable fue Python aislado en memoria. No se generaron scripts, snapshots, imágenes ni archivos de resultados auxiliares.
- Los valores intermedios, recetas, consultas, referencias aceptadas, resultados y fórmulas quedan en este archivo. Las pruebas Kotlin se proponen para confirmar la equivalencia con el ejecutable y fijar las regresiones; no se declara que hayan pasado.
- La medición de nombres usa un catálogo sin overlay de propios previos y mide definiciones; no mide latencia, interfaz, precisión de imagen ni validación de técnica. Sus resultados no se extrapolan a toda la población de búsquedas.
- La reproducción estática refuta el alcance universal de «siempre cero» y demuestra fallos concretos. La sesión exacta observada en el teléfono no se inspeccionó, conforme al encargo.
- Los umbrales propuestos son decisiones iniciales de producto trazables al plan, al tiempo disponible y al historial; no se presentan como límites médicos o garantías de recuperación.
- Control final del worktree: git diff --name-only no mostró diferencias rastreadas. Al inicio, git status --short solo mostraba docs/screens-v2/ como no rastreado. Al cierre aparecieron además tres pruebas y un directorio de artefactos: android-native/app/src/test/java/com/example/kpkn/screens/sessioneditor/SessionEditorAuditCopiesTest.kt, SessionEditorAuditRegressionTest.kt, android-native/app/src/test/java/com/example/kpkn/screens/sessioneditor/components/CatalogAuditStateTest.kt y artifacts/aud2-editor-catalogo-20261020/. Son cambios externos a las escrituras de esta auditoría; se dejaron intactos y no se ejecutaron.

## Resumen de una página para el usuario

La auditoría está terminada en [AUD1_motores_editor.md](C:/kw/u11/docs/screens-v2/audits/AUD1_motores_editor.md). Solo escribí ese informe; no modifiqué código ni pruebas, ni utilicé Gradle, adb, teléfono o commits. Durante el trabajo aparecieron otros archivos no rastreados; no los creé ni modifiqué.

**Columna.** El catálogo sí contiene carga axial para sentadillas y pesos muertos. El editor duplica parte del cálculo y omite la finalización del motor general: una sesión de seis series devuelve 2 puntos de Columna donde el motor general devuelve 8. En sesiones pequeñas, el redondeo puede ocultar una demanda positiva. La afirmación «siempre en 0» no se reproduce universalmente con este código. También encontré un fallo independiente: el cardio calcula sus aportes, pero el editor no los incorpora a los totales. La propuesta es compartir el cálculo completo, conservar decimales y distinguir cero, valores menores de uno y datos desconocidos.

**Sugerencias.** Inventarié las reglas activas y separé las reglas antiguas que ya no alimentan esta pestaña. Contrasté once plantillas publicadas, una plantilla de cardio y una variante con superserie, además de escenarios de tiempo y solapamiento. Varias sesiones compactas no generan avisos, correctamente. Los fallos más claros son el tono categórico desde un umbral fijo, presentar sesiones planificadas como entrenamientos realizados y proponer cambios sin comprobar su efecto. «Acortar descansos (drop-set)» conserva los descansos y añade trabajo: puede alargar la sesión. El rediseño incorpora objetivo, rol de cada ejercicio, fase, historial y tiempo disponible; protege los movimientos principales, verifica cada acción y limita los avisos a tres, sin duplicados ni afirmaciones sobre lesiones.

**Crear ejercicios.** En una muestra explícita de 36 nombres, la réplica del creador automático acertó el primer parecido útil en 15 casos (41,7 %) y encontró alguno entre los tres primeros en 23 (63,9 %). La búsqueda del catálogo obtuvo 72,2 % y 86,1 %, respectivamente. Son mediciones estáticas reproducidas en Python, sin ejecutar Kotlin. Los errores proceden del sesgo inicial hacia mancuernas, aliases mezclados, patrones incompletos y referencias incompatibles. Las diez descripciones evaluadas muestran cómo esos errores se convierten en músculos, patrones o material incorrectos.

La prioridad propuesta es corregir la coherencia de Impacto y las acciones del asistente; después, unificar búsqueda e inferencia y generar descripciones desde datos confirmados. Para imágenes, ya existe almacenamiento privado y álbum: falta conectarlos al ejercicio propio y permitir elegir portada, reutilizar una imagen compatible o mostrar una ilustración del patrón. El informe detalla evidencia por archivo y línea, pruebas propuestas y paquetes S/M/L.

