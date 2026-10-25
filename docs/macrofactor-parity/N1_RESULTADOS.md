# N1 · Resultados del estimador de gasto

## Protocolo fijado antes del ajuste

Banco determinista de 112 días, 32 semillas (1101–1132), evaluación estable en los días 57–112. S1 cruza agua con desviación típica de 0,3 / 0,6 / 1,0 kg y pesaje diario, tres veces por semana e irregular. Las métricas se agregan por escenario y patrón; no se exige precisión individual a cada trayectoria ruidosa.

Umbrales invariables de D7: sesgo absoluto ≤50 kcal/día, MAE ≤150 kcal/día con pesaje diario, cero inversiones de una propuesta semanal por un pesaje aislado de ±0,5 kg, abstención correcta ≥95 % cuando faltan datos y sesgo absoluto de S3 <10 kcal/día.

S2 introduce un escalón de −300 o +250 kcal/día en el día 50. La respuesta media de las 32 semillas debe entrar en el último 10 % del escalón durante siete días consecutivos antes de 42 días, sin sobrepasar el nuevo gasto en más de 100 kcal/día. Se comprueba también sin ruido. Los 42 días presupuestan 21 de ventana, 14 de adaptación de pendiente y siete de estabilización; 100 kcal es menos de la mitad del menor escalón. Estos límites se fijan antes de ejecutar o ajustar el modelo.

La banda debe cubrir el gasto verdadero en al menos el 80 % de los días estables de S1. S6 limita a 100 kcal/día la desviación de la respuesta media durante los 14 días posteriores al cambio de objetivo. S7 exige igualdad exacta de las estimaciones anteriores a una corrección.

Máximo de tres rondas de parámetros. Si persiste un incumplimiento, se conserva el umbral y se documenta como falla conocida en su test específico.

## Parámetros finales de V1

Las magnitudes de ruido son desviaciones típicas; el filtro eleva al cuadrado para obtener varianzas. Los valores se eligieron para este banco, sin importar coeficientes de terceros.

| WeightTrendParams | Valor | Razón |
|---|---|---|
| observationWeightFraction | 0,0045 | Escala de medición proporcional al peso corporal. |
| minObservationStdKg / maxObservationStdKg | 0,18 / 0,90 kg | Evitar confianza extrema en pesos pequeños o grandes. |
| levelProcessStdKg | 0,018 kg/día | Permitir movimiento lento del nivel además de la pendiente. |
| slopeProcessStdKgPerDay | 0,0032 kg/día | La segunda ronda reduce la latencia máxima de 52 a 40 días, manteniendo MAE diario ≤150. |
| transientProcessWeightFraction | 0,0055 | Variabilidad del agua proporcional al tamaño corporal. |
| transientHalfLifeDays / slopeHalfLifeDays | 2 / 120 días | Separar agua de corta duración y cambio de tejido persistente. |
| initialSlopeStdKgPerDay | 0,045 kg/día | Inicio incierto, sin imponer un ritmo de objetivo. |
| huberObservationMultiples | 2,4 | Acotar innovaciones grandes y reducir su ganancia; el pico doble verifica la robustez. |

| ExpenditureParams | Valor | Razón |
|---|---|---|
| windowDays / minimumWindowDays | 21 / 14 días | Tres semanas amortiguan agua; dos permiten recuperar cobertura tras un hueco. |
| minimumEligibleFraction | 6/7 | Admitir como máximo un día excluido por semana, proporcional a la ventana. |
| minimumWeighIns / minimumWeightSpanDays / maximumWeightAgeDays | 4 / 7 / 6 | Evitar pendientes sostenidas por pocos datos, datos agrupados o peso antiguo. |
| energyDensityKcalPerKg | 7700 kcal/kg | Balance simétrico ya utilizado por KPKN. |
| updateFraction / maximumDailyChangeKcal | 0,18 / 40 kcal | Filtrado exponencial y límite diario independientes. |
| confidenceRampDays | 14 actualizaciones válidas | Confianza lineal basada en evidencia, sin progresar durante pausas. |
| stableToleranceKcal | 80 kcal | Estado estable cuando candidato y publicación se aproximan después de la rampa. |
| lowIntakeAbsoluteKcal / lowIntakeTypicalFraction | 500 kcal / 0,62 | Revisar colaciones aisladas y medios registros, sin excluir confirmaciones explícitas. |
| typicalIntakeLookbackDays / minimumTypicalIntakeDays | 28 / 3 | Mediana reciente causal, con mínimo absoluto antes de tener referencia. |
| maximumIntakeKcal | 18000 kcal | Límite conservador de plausibilidad del total diario, independiente de la revisión de registros bajos. |
| intakeSystematicErrorFraction / intakeDailyErrorFraction | 0,08 / 0,12 | Separar error persistente de registro y componente diario que disminuye con la muestra. |
| bandMultiplier / initialStdErrorKcal | 1,35 / 350 kcal | Banda por suma de varianzas y una incertidumbre mínima durante inicio o pausa. |
| goalChangeDays / goalUpdateFactor / goalBandFactor | 14 / 0,25 / 1,6 | Mismo suavizado con menor paso y mayor incertidumbre durante rebote de agua. |
| weightTrendParams | WeightTrendParams.V1 | Configuración única y explícita del filtro utilizado por el gasto. |

## Reglas de integración

El nivel se inicializa con la mediana de los primeros uno, dos y tres pesajes disponibles en cada fecha; nunca se reescribe el prefijo. `WeightTrendPoint.isProvisional` es verdadero con menos de tres pesajes. Los huecos solo predicen. El filtro conserva covarianza mediante la forma de Joseph; el error de pendiente procede de su diagonal.

La covarianza inicial asigna al transitorio cuatro veces la varianza de observación, para absorber la incertidumbre inicial de agua antes que imponer una pendiente. El peso robusto tiene un mínimo numérico de 1/20: limita la inflación de varianza a 400 veces y evita divisiones inestables; la innovación sigue acotada incluso en ese límite. Estos valores derivan de la inicialización y estabilidad del algoritmo.

El candidato usa el cambio de nivel filtrado entre los extremos temporales de la ventana, no el último intervalo ni pesajes brutos. La ventana se acorta de uno en uno solamente por cobertura de ingesta. La calidad describe la ventana realmente elegida. Los datos futuros se eliminan antes de clasificar y no figuran en la calidad; `FUTURE` queda reservado en el contrato. La mediana de referencia incluye los días elegibles recientes, incluido ayuno como cero. Los parciales y registros pendientes de revisión no alteran la referencia.

Antes de 14 días: STARTING y punto inicial congelado. Después, cobertura insuficiente: PAUSED/INSUFFICIENT_LOGGING; pesos insuficientes, agrupados o antiguos: PAUSED/INSUFFICIENT_WEIGHTS. Una ventana válida produce LEARNING durante la rampa, un cambio de objetivo o una distancia al candidato mayor de 80 kcal; en otro caso, STABLE. PAUSED congela el valor, no la incertidumbre. El suelo se aplica desde el primer punto.

La banda combina en cuadratura error de pendiente por densidad, error sistemático de ingesta, error diario dividido por raíz del tamaño de muestra y distancia entre candidato y publicación. Su extremo inferior respeta el suelo. El controlador redondea sobre una rejilla compatible con suelo y paso máximo; si el objetivo actual ya incumple el suelo, corregirlo tiene prioridad sobre el paso y la banda muerta. Los totales diarios duplicados se rechazan; los pesos repetidos se reducen a mediana. No se mutan las entradas.

## Archivos y API

Todo es nuevo. En `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/expenditure/`: `ExpenditureModels.kt`, `ExpenditureParams.kt`, `DayEligibility.kt`, `PartialDaySuggester.kt`, `WeightTrendEstimator.kt`, `ExpenditureEstimator.kt` y `CalorieTargetController.kt`.

API de cálculo: `WeightTrendEstimator(params).estimate(weights, from, through)`, `ExpenditureEstimator(params).estimateSeries(input)`, `CalorieTargetController.propose(expenditureKcal, goalRateKgPerWeek, currentTargetKcal, floorKcal, maxStepKcal, roundToKcal, deadBandKcal)` y `PartialDaySuggester.suggest(days, through)`, con los tipos y valores por defecto del brief. Los parámetros públicos son `WeightTrendParams.V1` y `ExpenditureParams.V1`. El único campo añadido a los modelos del brief es `WeightTrendPoint.isProvisional: Boolean = false`. Versión: `expenditure-v1`.

En la ruta espejo de `src/test/`: `WeightTrendEstimatorTest.kt`, `ExpenditureEstimatorTest.kt`, `PartialDaySuggesterTest.kt`, `CalorieTargetControllerTest.kt`, `ExpenditureSimulation.kt` y `ExpenditureSimulationTest.kt`. El simulador y sus auxiliares son internos a los tests. El único documento es este informe.

## Rondas y resultados

| Ronda | Configuración | Resultado y decisión |
|---|---|---|
| 1 | Todos los parámetros finales salvo ruido de proceso de pendiente 0,0018 kg/día. | Compilación de app y tests correcta. 56/57 tests verdes, sin omisiones. Solo incumple la latencia S2: 45/45 días para −300 y 52/46 para +250 (con/sin ruido). Se mantiene el límite de 42 días. |
| 2 | Único cambio: ruido de proceso de pendiente a 0,0032 kg/día. | 57/57 tests verdes, sin omisiones. Mayor adaptación de pendiente: latencia máxima 40 días. El MAE diario máximo sube de 126,08 a 146,19 kcal/día y sigue dentro del umbral. Se cierra el ajuste tras dos rondas. |

Resumen observado de la ronda 1: sesgo máximo absoluto S1 de 2,68 kcal/día; MAE diario 47,78 / 86,99 / 126,08 kcal/día según agua; cobertura conjunta 98,78 %; suelo normal 0 %. S3: sesgos +6,00 / −5,81 kcal/día. S4: sesgo 0,40 con parciales marcados o detectados y −169,83 si se confirman como completos. Pausas correctas: registros 288/288 y pesos 256/256; paso máximo al reanudar 40 kcal. S6: ajustes falsos medios máximos de 30,81 y 12,00 kcal. S7 y determinismo: 32/32; inversiones por pesaje aislado: 0/6720. Error al día 42 desde puntos iniciales ±500: 24,59 y 6,30 kcal/día.

### S1 final: gasto constante y agua

Sesgo y MAE en kcal/día; RMSE de la pendiente de nivel sobre 21 días en kg/día. Cada fila agrega 32 trayectorias y 56 días estables.

| Pesaje | Agua σ (kg) | Sesgo | MAE | Cobertura | RMSE pendiente |
|---|---:|---:|---:|---:|---:|
| Diario | 0,3 | 1,01 | 54,84 | 100 % | 0,01045 |
| Diario | 0,6 | 3,74 | 100,88 | 99,27 % | 0,01906 |
| Diario | 1,0 | 6,18 | 146,19 | 94,64 % | 0,02768 |
| Tres/semana | 0,3 | 1,02 | 57,08 | 100 % | 0,01082 |
| Tres/semana | 0,6 | 2,52 | 100,00 | 99,67 % | 0,01867 |
| Tres/semana | 1,0 | 1,38 | 134,02 | 97,49 % | 0,02408 |
| Irregular | 0,3 | 2,64 | 58,21 | 100 % | 0,01094 |
| Irregular | 0,6 | 5,81 | 98,08 | 99,22 % | 0,01836 |
| Irregular | 1,0 | 6,48 | 127,82 | 96,82 % | 0,02357 |

Cumple |sesgo| ≤50 en todos los patrones y MAE diario ≤150 en los tres niveles de agua. El RMSE de pendiente con agua de 0,6 kg y tres pesajes semanales es 0,01867 frente al límite adicional de 0,025. El suelo actúa en 0 % de los días estables normales. Cobertura conjunta: 98,57 % de 16128 estimaciones, por encima del mínimo declarado de 80 %.

### S2–S7 y contratos finales

| Escenario | Resultado final | Umbral / contrato |
|---|---|---|
| S2 −300 | 39 días con ruido / 38 sin ruido; sobreoscilación 39,15 / 35,93 kcal. | ≤42 días y ≤100 kcal. |
| S2 +250 | 40 días con ruido / 38 sin ruido; sobreoscilación 39,95 / 30,87 kcal. | ≤42 días y ≤100 kcal. |
| S3 | Sesgo +9,22 / −9,02 kcal/día en las dos fases simétricas. | Sesgo absoluto <10; densidad única en ambos sentidos. |
| S4 | Sesgo +3,44 con parciales marcados o detectados; −166,78 si se fuerzan como completos. | ≤50 con exclusión; evidencia del sesgo de medio registro. |
| S5 registros | 288/288 pausas correctas; valor congelado en cada punto PAUSED. | ≥95 % y congelación exacta. |
| S5 pesos | 256/256 pausas correctas por falta de pesajes recientes. | ≥95 %. |
| Reanudación | Paso máximo 40 kcal. | ≤maximumDailyChangeKcal. |
| S6 | Rebotes de 1 / 1,5 kg: ajuste falso medio máximo 24,33 / 4,13 kcal en los 14 días siguientes. | ≤100 kcal; banda ensanchada y paso amortiguado. |
| S7 | Prefijos y repetición exactos en 32/32 historiales. | Solo cambia desde el día corregido. |
| Pesaje aislado ±0,5 kg | 0/6720 inversiones de propuesta. | Cero inversiones; cinco objetivos y siete posibles fechas de revisión tras cada pico. |
| Error inicial ±500 kcal | Error al día 42: 24,88 / 6,24 kcal. | ≤100 kcal. |
| Contratos de API | 44 tests adicionales verdes. | Ingesta +300, colación sin revisar/confirmada, ayuno, parciales, plausibilidad, futuro, causalidad, huecos, arranque, suelo, calidad, controlador y determinismo. |

La tendencia conserva el signo ante ±1,5 kg durante dos días en las trayectorias de prueba. Los huecos aumentan el error de pendiente, y con menos de tres pesajes se declara provisional. El controlador verifica banda muerta, suelo, paso, redondeo y sus intersecciones, incluido el límite de Int.

## Corridas de Gradle y XML

1. `bash /c/kw/tools/gradle_solo.sh /c/kw/mfp/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.expenditure.*" > /c/kw/logs/mfp-N1-1.log 2>&1`. Resultado: `BUILD FAILED in 10m 18s`, exclusivamente por latencia S2. `:app:compileBaseDebugKotlin` correcto. XML: 57 tests en cinco suites, 56 correctos, un fallo, cero errores y cero omitidos. Resumen completo con métricas archivado en `C:\kw\logs\mfp-N1-1-tests.log`. Espera previa registrada por el wrapper: 1680 + 525 segundos.
2. `bash /c/kw/tools/gradle_solo.sh /c/kw/mfp/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.expenditure.*" --offline > /c/kw/logs/mfp-N1-2.log 2>&1`. Resultado: `BUILD SUCCESSFUL in 6m 21s`, incluida `:app:compileBaseDebugKotlin`. XML: 57 tests en cinco suites, 57 correctos, cero fallos, errores u omitidos. Resumen completo con métricas archivado en `C:\kw\logs\mfp-N1-2-tests.log`. Espera previa registrada: 600 segundos.

Cuenta XML final: CalorieTargetControllerTest 11, ExpenditureEstimatorTest 19, ExpenditureSimulationTest 13, PartialDaySuggesterTest 4 y WeightTrendEstimatorTest 10. No hay `@Ignore`. La shell se mantuvo en primer plano hasta terminar; la herramienta disponible ofrece cesión mediante `yield_time_ms`, sin un parámetro de timeout ni corte de la sesión al ceder.

## Alcance

El banco verifica contratos y comportamiento bajo un generador conocido, con peso inicial de 80 kg. La evaluación estable usa fechas fijas, independientemente de la etiqueta STABLE. Las pruebas de escala de ruido cubren además 50 y 110 kg, sin estimar precisión fisiológica en esos pesos. S2 y S6 acotan respuestas medias del banco, no el peor individuo. La cobertura observada es conservadora y no equivale a un intervalo clínico calibrado individualmente.

El margen observado del peor MAE diario es 3,81 kcal/día y el de S3 es 0,78 kcal/día; otras semillas o dinámicas de agua pueden superar esos límites. No se extrapolan estos umbrales a personas individuales. No se verifican datos reales ni la integración de pantallas, repositorios o persistencia, que están fuera de N1.

No se demuestra precisión fisiológica individual ni se permite diagnosticar metabolismo, composición corporal o calidad real de los registros. La EER y el suelo los aporta quien llama. Este paquete no cambia consumidores ni registros existentes. No se necesitan cambios fuera de las rutas autorizadas; la conexión a la app corresponde a N2.
