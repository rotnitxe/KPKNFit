# Técnica en vídeo: evidencia publicada y listón de fiabilidad

Fecha de corte y consulta: **20 de octubre de 2026**. Producto: KPKN Fit Android. Este informe es investigación documental; no se evaluó el código ni se midió el rendimiento de KPKN.

**Conclusión:** «la misma fiabilidad que las apps especializadas» necesita especificar ejercicio, métrica, versión y encuadre. Hay evidencia de buena medición de velocidad por vídeo en condiciones controladas, pero no una garantía común del mercado para contar series completas desde cualquier posición. La validación de seguimiento del disco tampoco valida un contador basado en pose.

El criterio propuesto es **95% de series exactas en básicos y 90% en los demás ejercicios admitidos**, con captura buena; **99% de series exactas entre las etiquetadas «seguro»**; y, para velocidad media con disco calibrado, **MAE ≤0,03 m/s y RMSE ≤0,04 m/s**. Son decisiones de aceptación de este informe, pendientes de validación en KPKN; sus fundamentos y condiciones están en la sección 6.

## 1. Alcance y reglas de lectura

Se buscaron estudios y documentación de Metric VBT/Lift, Qwik VBT, Iron Path, WL Analysis, My Lift/PowerLift/My Jump Lab, Bar Path y herramientas similares; Kemtai, Onyx, Tempo, Peloton Guide y Zenia; y trabajos con MediaPipe, OpenPose y MoveNet. Se priorizaron editoriales científicas, PubMed/PMC, repositorios institucionales y documentación del proveedor. Se leyeron páginas web, resúmenes y fragmentos indexados de las fuentes originales, sin descargar archivos, vídeos, conjuntos de datos ni anexos. Es una búsqueda dirigida, no una revisión sistemática exhaustiva.

Cada fila identifica su naturaleza: **revisado por pares**, **preprint**, **documentación oficial** o **marketing**. Una descripción comercial alojada por Apple o Google sigue siendo marketing. Un informe institucional sin revisión acreditada se identifica como documentación oficial, no como artículo revisado por pares. Cuando se utiliza una revisión para recuperar cifras de un original de acceso restringido, se indica expresamente.

**NR** significa «no reportado o no verificable en el material consultado»; no demuestra que el dato nunca se haya publicado. «No encontrado» describe el resultado de esta búsqueda. Las páginas sin fecha se citan como **s. f.; consultadas en 2026**, sin atribuirles un año de publicación inventado.

Las métricas no son intercambiables:

- **Serie exacta:** el número previsto coincide con el conteo humano de toda la serie, sin tolerancia. Debe acompañarse de comprobación de eventos: una omisión y una repetición fantasma pueden compensarse en el total.
- **Precisión por repetición:** TP/(TP+FP). **Recall:** TP/(TP+FN). Requieren correspondencia temporal con las repeticiones reales; dividir dos totales no basta.
- **MAE de conteo:** promedio de abs(conteo automático − conteo real), en repeticiones por serie. Algunos trabajos publican una versión normalizada por el conteo real, que no tiene unidades de repeticiones.
- **MAE, RMSE, sesgo y LoA de velocidad:** errores distintos, expresados en m/s. El sesgo puede ser pequeño aunque los errores individuales sean grandes. Los LoA describen acuerdo entre mediciones; no son el intervalo de confianza del sesgo.
- **r, ICC y CCC:** se conservan sus nombres y finalidad originales. Una correlación alta no demuestra acuerdo absoluto. El ICC de repetibilidad entre dispositivos tampoco equivale al acuerdo con el patrón de referencia.
- **MV/MCV:** velocidad media concéntrica; **PV:** velocidad pico; **ROM:** recorrido. Hay que distinguir velocidad vertical, resultante en el plano y resultante espacial antes de comparar instrumentos.

## 2. Velocidad y trayectoria mediante cámara

### 2.1. Renner, Mitter y Baca: Qwik VBT, Metric VBT y MyLift

**Renner et al. (2024), PMC11575817:** VBT del disco, no pose. 20 powerlifters; 589 repeticiones individuales de sentadilla/banca/peso muerto, 45–90% del máximo competitivo; iPhone SE 2022 lateral, trípode a 3 m y altura de 1 m; disco de 450 mm.

| App / clasificación | Qué mide y referencia | Resultado publicado | Condiciones particulares | Fuente y año |
|---|---|---|---|---|
| Qwik VBT v0.94 — **revisado por pares** | MCV/detección; Vicon 200 Hz; comparador RepOne | Sin omisiones/fantasmas. Qwik y RepOne: RMSE **0,01–0,04 m/s**, rango conjunto del resumen. | Importación 1080p/60 fps; selección del disco y recorte manual. | [Renner et al.](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0313919), 2024 |
| Metric VBT v2.3.1 — **revisado por pares** | MCV/detección; Vicon | **52 omisiones**: 7/12/33 por ejercicio; **16 fantasmas**. RMSE banca: **0,04 m/s**. | Grabación en app; revisión de fantasmas; fps NR. | [Renner et al.](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0313919), 2024 |
| MyLift, My Jump Lab v3.2.9 — **revisado por pares** | MCV/detección; Vicon | **175 omisiones**: 3/168/4; sin fantasmas. Detectó **16%** en banca. | Disco detectado antes de cada intento; fps NR. | [Renner et al.](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0313919), 2024 |

Series largas exactas NR. Qwik se evaluó con asistencia manual.

### 2.2. Metric VBT: Taber et al.

| Clasificación / versión | Qué mide | Referencia y resultado | Condiciones | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; v0.5.4 | MCV y ROM | Captura de movimiento 3D. MCV: **r=0,67–0,95**, **ICC=0,79–0,94**, sesgo de magnitud **<0,06 m/s**. ROM: **ICC=0,67–0,98**, sesgo **<2 cm**. No son cifras de MAE; conteo, precisión/recall y series exactas NR. | 9 participantes; sentadilla, banca y peso muerto; 40 kg; ejecución rápida/lenta, sesiones separadas por una semana. iPad Pro vertical en trípode, a **2,5 m**, altura **99 cm**, **1080p/60 fps**. Calibración del disco NR en el resumen. | [Taber et al., artículo](https://journal.iusca.org/index.php/Journal/article/view/263), 2023; [métodos indexados del original en repositorio institucional](https://sacredheart.elsevierpure.com/ws/portalfiles/portal/39924743/Validity%20and%20Reliability%20of%20a%20Computer%20Vision%20System%20to%20Determine.pdf), 2023 |

El segundo enlace identifica el original cuyos métodos aparecen indexados; no se descargó el PDF.

### 2.3. Metric VBT: Trowell et al.

24 participantes; v0.6.0; Vicon a 200 Hz. Dispositivos en trípodes a **1,50 m del lateral del rack**, altura **1,30 m**, ángulos respecto de la posición perpendicular de −20° a +20°; **720p/60 fps**; disco exterior de **450 mm**. Los resultados siguientes corresponden a **0°**; el estudio informa MCV resultante y ROM vertical.

| Clasificación / ejercicio | Qué mide | Resultado con referencia Vicon | Condiciones de carga | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; sentadilla | Repeticiones, MCV, ROM | Precisión/recall: **1,000/1,000**. Velocidad: sesgo **+0,10 m/s**, LoA **[−0,01; +0,21] m/s**. ROM: sesgo **−0,08 cm**, LoA **[−4,17; +4,01] cm**. | 25–100% 1RM | [Trowell et al., tablas 1–2](https://pmc.ncbi.nlm.nih.gov/articles/PMC11283170/), 2024 |
| **Revisado por pares**; banca | Repeticiones, MCV, ROM | Precisión/recall: **0,959/0,985**. Velocidad: sesgo **+0,08 m/s**, LoA **[−0,06; +0,22] m/s**. ROM: sesgo **−1,15 cm**, LoA **[−6,04; +3,74] cm**. | 50–100% 1RM | [Trowell et al., tablas 1 y 3](https://pmc.ncbi.nlm.nih.gov/articles/PMC11283170/), 2024 |

Series exactas NR. El resumen contiene valores de LoA de velocidad de banca aparentemente copiados de ROM; se usan las tablas del cuerpo, no esa línea. Este resultado muestra por qué detectar repeticiones correctamente no valida su velocidad.

### 2.4. Metric: Puppyn et al., validación más reciente con GymAware

25 reclutados: principal **23 con v4.8.1**; **2 con v5.1.0**, aparte. Sentadilla/banca/peso muerto hasta 1RM. iPhone 11/13 mini verticales, perpendicular a **2 m**, alturas **1,17/0,87/0,87 m**; buena luz; diámetro de discos pequeños manual; fps NR.

| Clasificación / métrica | Referencia | Resultado publicado | Alcance | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; MCV sentadilla | GymAware | MAE **0,026 m/s**; sesgo **+0,007**; LoA **[−0,071; +0,084] m/s**; CCC **0,986**. | Validez concurrente | [Puppyn et al., tabla 3](https://journals.sagepub.com/doi/10.1177/17479541251391967), 2025 |
| **Revisado por pares**; MCV banca | GymAware | MAE **0,052 m/s**; sesgo **+0,043**; LoA **[−0,102; +0,188] m/s**; CCC **0,935**. | Validez concurrente | [Puppyn et al.](https://journals.sagepub.com/doi/10.1177/17479541251391967), 2025 |
| **Revisado por pares**; MCV peso muerto | GymAware | MAE **0,047 m/s**; sesgo **−0,012**; LoA **[−0,153; +0,127] m/s**; CCC **0,931**. | Validez concurrente | [Puppyn et al.](https://journals.sagepub.com/doi/10.1177/17479541251391967), 2025 |
| **Revisado por pares**; repetibilidad MCV | Dos dispositivos Metric simultáneos | ICC sentadilla/banca/peso muerto: **0,994/0,981/0,941**; SDC: **0,050/0,082/0,130 m/s**. | No es ICC frente a GymAware. | [Puppyn et al., tabla 2](https://journals.sagepub.com/doi/10.1177/17479541251391967), 2025 |

Detección: **94,3%**, F1 **0,971**. Precisión definida como uno sin evaluar negativos: no acredita precisión perfecta en uso libre. Submuestra actualizada insuficiente para generalizar.

### 2.5. Metric VBT: Grossi et al., velocidad pico y ROM

| Clasificación / métrica | Referencia y resultado | Condiciones | Limitación | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; MV | Vitruve LPT; ICC **0,612**; sesgo **−0,02 m/s**; LoA **[−0,07; +0,04] m/s** | 18 hombres; banca en Smith a 1RM | Comparación entre instrumentos, sin test–retest; conteo NR. | [Grossi et al.](https://www.mdpi.com/2411-5142/11/2/197), 2026 |
| **Revisado por pares**; PV | Vitruve LPT; ICC **0,888**; sesgo **+0,02 m/s**; LoA **[−0,08; +0,11] m/s** | Mismo protocolo | No extrapolar a levantamientos rápidos. | [Grossi et al.](https://www.mdpi.com/2411-5142/11/2/197), 2026 |
| **Revisado por pares**; ROM | Vitruve LPT; ICC **0,236**; sesgo **+1,64 cm**; LoA **[−13,82; +17,10] cm** | Posición, fps, versión y calibración: no verificados en el material recuperado. | Un sesgo pequeño no elimina la dispersión individual. | [Grossi et al.](https://www.mdpi.com/2411-5142/11/2/197), 2026 |

### 2.6. My Lift, PowerLift y My Jump Lab: no mezclar generaciones

Los nombres comerciales y los procedimientos han cambiado. No corresponde trasladar automáticamente una validación antigua de medición de velocidad a un detector de repeticiones actual.

| Clasificación / sistema | Qué mide | Referencia y resultado | Condiciones y datos ausentes | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; PowerLift original | Velocidad media, estimación de 1RM | Transductor lineal: **r=0,94**, SEE **0,028 m/s**, ICC **0,965**; diferencia media publicada **0,008 ±0,03 m/s**. | 10 powerlifters; banca, 75–100% 1RM. No demuestra detección automática de series; posición, fps y calibración NR en el resumen. | [Balsalobre-Fernández et al.](https://pubmed.ncbi.nlm.nih.gov/28097928/), 2018; publicación electrónica 2017 |
| **Revisado por pares**; My Jump Lab con IA en tiempo real | Velocidad de barra | GymAware. Al 50/75% 1RM: **r=0,90/0,92**; sesgo **−0,010/−0,026 m/s**; CV de la app **8,17/13,55%**. | 27 participantes, 5 repeticiones por carga, iPhone 12 Pro. Exactitud de serie, precisión/recall, MAE y LoA NR en el resumen; fps/encuadre/calibración NR. | [Balsalobre-Fernández et al.](https://pubmed.ncbi.nlm.nih.gov/38015739/), 2023 |

Los resultados negativos de MyLift en banca en Renner se conservan en la sección 2.1; no se sustituyen por la correlación de otro protocolo.

### 2.7. Iron Path

| Clasificación / sistema | Qué mide | Referencia y resultado | Condiciones | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; Iron Path | Velocidad concéntrica media y ROM | Open Barbell System, transductor lineal. **IC del 95% del ICC**, según levantamiento: velocidad desde **[0,648; 0,876]** hasta **[0,849; 0,930]**; ROM desde **[−0,053; 0,480]** hasta **[0,545; 0,770]**. Son intervalos, no ICC puntuales. Subestima velocidad en sentadillas; LoA descritos como amplios, sin cifras en el resumen. | 27 participantes; sentadilla frontal/trasera y peso muerto convencional/sumo; protocolo de máximo modificado en sesiones separadas. Conteo, MAE en m/s, fps, distancia y calibración NR en el resumen. | [Kasovic et al.](https://pubmed.ncbi.nlm.nih.gov/33666594/), 2021 |

Los autores desaconsejan sustituir el transductor validado por esta app para estas mediciones.

### 2.8. WL Analysis

| Clasificación / fuente | Qué mide | Referencia y resultado | Condiciones | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; Lemus et al. | PV en arrancada y cargada | Captura de movimiento con 12 cámaras; el resumen concluye que los dispositivos distintos de GymAware RS subestimaron PV. Las cifras específicas de WL siguientes proceden de la revisión indicada abajo. | 12 levantadores, 40/70% 1RM. La revisión identifica OptiTrack e iPhone 13 Pro; versión, fps, distancia y calibración del ensayo NR en el material accesible. | [Original de Lemus et al.](https://journals.sagepub.com/doi/10.1177/17479541241266248), 2024 |
| **Revisado por pares**; extracción secundaria de Lemus | PV | Cargada al 40/70%: sesgo **−0,142/−0,209 m/s**, error aleatorio **0,129/0,106 m/s**. Arrancada: sesgo **−0,069/−0,079**, error aleatorio **0,133/0,130 m/s**. No son MAE ni LoA. | Cifras transcritas de tabla 5 de la revisión; original completo restringido. Conteo/series exactas NR. | [Puppyn y Brughelli, tabla 5](https://journals.sagepub.com/doi/10.1177/17479541261447622), 2026, sobre Lemus 2024 |
| **Documentación oficial** | Trayectoria del disco; desplazamiento horizontal/vertical; velocidad media, mínima y máxima del fragmento elegido; fuerza/potencia calculadas | No publica error ni validación del contador. La media usa el fragmento concéntrico seleccionado. | Cámara lateral nivelada, altura a mitad de ROM, **2,5–3,5 m**; escala por diámetro, predeterminado **45 cm**; introducir fps correctos si el vídeo está ralentizado. | [Tutorial del desarrollador](https://wlanalysis.com/tutorial.html), s. f.; consulta 2026 |

### 2.9. Bar Path y otras herramientas de trayectoria

Estas fichas acreditan funciones anunciadas, no su precisión. No se localizaron validaciones cuantitativas de estas versiones.

#### Bar Path

| App / clasificación | Qué anuncia medir | Referencia y resultado | Condiciones publicadas | Fuente y año |
|---|---|---|---|---|
| Bar Path, Robert Frederick — **marketing** | Trayectoria, ROM, consistencia entre repeticiones, potencia | Sin referencia experimental ni MAE/ICC/LoA o detección de reps publicados en la ficha. | Distancia, fps y calibración NR. | [Ficha del desarrollador en App Store](https://apps.apple.com/us/app/bar-path/id1097402002), s. f.; consulta 2026 |

#### Barbell Metrics

| App / clasificación | Qué anuncia medir | Referencia y resultado | Condiciones publicadas | Fuente y año |
|---|---|---|---|---|
| Barbell Metrics, Kevin Xue — **marketing** | Trayectoria, velocidad y tiempos excéntrico, concéntrico y de pausa | Sin validación cuantitativa localizada. No basta para fijar precisión temporal. | Encuadre, fps y calibración NR en la ficha. | [Ficha del desarrollador en App Store](https://apps.apple.com/us/app/barbell-metrics/id6742751119), s. f.; consulta 2026 |

#### Barway

| App / clasificación | Qué anuncia medir | Referencia y resultado | Condiciones publicadas | Fuente y año |
|---|---|---|---|---|
| Barway, Yang Li — **marketing** | Trayectoria mediante seguimiento de un punto seleccionado | Sin validación cuantitativa localizada; no se acredita velocidad calibrada ni conteo exacto. | Vídeo lateral, recorrido completo, cámara estable y contraste; selección manual inicial de la barra. | [Ficha del desarrollador en App Store](https://apps.apple.com/ca/app/barway/id6760556983), s. f.; consulta 2026 |

### 2.10. Qué aporta la revisión de mercado de 2026

| Clasificación / estudio | Qué revisa | Resultado | Condiciones y alcance | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; revisión de Puppyn y Brughelli | Validez y fiabilidad de apps de VBT | **18 estudios, 6 aplicaciones**. Considera aceptable fiabilidad con ICC **≥0,900**, CV **≤10%** y ES **≤0,60**; distingue requisitos de investigación más estrictos: ICC **≥0,997**, CV **≤3,5%**. | Es el criterio de esa revisión, no una norma contractual del mercado. Heterogeneidad de versiones, ejercicios, referencias y procedimientos; no establece un porcentaje universal de series exactas. | [Revisión sistemática](https://journals.sagepub.com/doi/10.1177/17479541261447622), 2026 |

Se priorizaron los originales: por ejemplo, el sesgo de My Jump Lab en la fuente primaria de 2023 tiene un decimal distinto del transcrito en la revisión. Tampoco se utiliza una celda de esa revisión que etiqueta un valor negativo como RMSE de Renner: un RMSE no puede ser negativo.

## 3. Apps comerciales de conteo por pose

### 3.1. Kemtai

| Clasificación / fuente | Qué mide | Referencia y resultado | Condiciones y límites | Fuente y año |
|---|---|---|---|---|
| **Documentación oficial** | Número de repeticiones, duración, ROM y adherencia | La descripción funcional no aporta series exactas, precisión/recall ni MAE de conteo. | Cámara de teléfono, tableta u ordenador; no se verifican fps ni distancia de un ensayo de conteo. | [Datos que entrega Kemtai](https://kemtai.com/product/physical-therapy-data/), s. f.; consulta 2026 |
| **Revisado por pares**; Nedović et al. | ROM de flexión de rodilla/extensión de cadera | EasyAngle. Repetibilidad de Kemtai: ICC **0,91/0,93**; acuerdo entre instrumentos: ICC **0,71/0,66**. Tendencia a sobreestimar ROM. | Movimientos clínicos evaluados por examinadores; no evalúa un contador de series de fuerza. Encuadre/fps NR en el resumen. | [Nedović et al.](https://mjssm.me/?artid=283&sekcija=abstract), 2024 |
| **Documentación oficial**, informe técnico institucional; revisión por pares no acreditada en la página | Seguimiento de puntos anatómicos | Página de ALYN describe comparación con Vicon; no aporta resultados de precisión/recall de repeticiones. | El resumen institucional trata posiciones y movimientos controlados; no se descargó el informe adjunto. | [ALYN, informe de marzo](https://www.alyn.org/Kemtai-more-info), 2025 |

Afirmación comercial separada de la evidencia anterior:

| Clasificación / fuente | Qué anuncia | Cifra y significado | Límite | Fuente y año |
|---|---|---|---|---|
| **Marketing** | Precisión general del seguimiento | La FAQ afirma validación científica y seguimiento de **111 puntos**. Esa cifra describe puntos del cuerpo, no porcentaje de reps correctas. | No convierte validación geométrica en exactitud de conteo. | [FAQ de Kemtai](https://kemtai.com/faqs/), s. f.; consulta 2026 |

### 3.2. Onyx

| Clasificación / sistema | Qué mide o anuncia | Referencia y resultado | Condiciones / alcance | Fuente y año |
|---|---|---|---|---|
| **Marketing**; Onyx original, posteriormente adquirido por Cure.fit | Repeticiones, corrección de forma y seguimiento de rendimiento mediante visión en el dispositivo | El comunicado lo describe como de conteo muy preciso, pero no publica porcentaje, MAE, precisión/recall, referencia ni exactitud por serie. | Cámara del móvil; posición, distancia y fps NR. Se identifica la app original, no otras apps actuales homónimas. | [Comunicado emitido por Cure.fit](https://www.prnewswire.com/news-releases/curefit-acquires-digital-fitness-company-onyx-aims-to-accelerate-international-offering-301212500.html), 2021 |

### 3.3. Tempo

| Clasificación / sistema | Qué mide | Referencia y resultado | Condiciones | Fuente y año |
|---|---|---|---|---|
| **Documentación oficial**; Studio | Reps, ROM, ritmo y feedback de forma | Reconoce doble conteo y omisiones por errores de seguimiento. No publica tasas, MAE o series exactas contra conteo humano. | Sensor 3D propio; recomienda **6 pies** de separación, espacio despejado y posición acorde al instructor. Algunas modificaciones no se reconocen. | [3D Tempo Vision y feedback](https://support.tempo.fit/support/solutions/articles/151000154714-3d-tempo-vision-form-feedback), s. f.; consulta 2026 |
| **Documentación oficial**; Move/Core | Conteo y feedback de ejercicios compatibles | Sin validación cuantitativa de conteo localizada. Utiliza la cámara de profundidad del iPhone; no equivale a MediaPipe con RGB monocular. | Cámara sin obstáculos, contraluz ni reflejos. Soporte identifica Face ID como necesario. | [Resolución de problemas Move/Core](https://support.tempo.fit/support/solutions/articles/151000210407-tempo-move-core-troubleshooting), s. f.; consulta 2026 |
| **Documentación oficial**; modo solo teléfono | Reps y medidor de ROM, con opción de entrenar sin seguimiento | Al elegir «sin seguimiento», asigna crédito de reps/peso recomendados; ese crédito no es una medición. No publica precisión. | Teléfono vertical y cuerpo de cabeza a pies visible. | [Phone Only Workout](https://support.tempo.fit/support/solutions/articles/151000178713-phone-only-workout), actualización 2025 |

### 3.4. Peloton Guide

| Clasificación / sistema | Qué mide | Referencia y resultado | Condiciones y distinciones | Fuente y año |
|---|---|---|---|---|
| **Documentación oficial**; Guide | Rep Tracking en clases compatibles; también ofrece Movement Tracker | No se localizaron exactitud por serie, precisión/recall o MAE de conteo validados. El propio soporte aclara que **Movement Tracker no cuenta repeticiones**. | Cámara de Guide conectada al televisor, no cámara del móvil. Para Movement Tracker: buena luz, persona dentro del encuadre; lateral para suelo y frontal para ejercicios de pie. No extrapolar esas instrucciones a una validación de Rep Tracking. | [Soporte de Movement Tracker y distinción con Rep Tracking](https://support.onepeloton.com/s/article/Peloton-Hardware-Movement-Tracker?language=en_CA), s. f.; consulta 2026; [descripción corporativa de Guide](https://www.sec.gov/Archives/edgar/data/1639825/000163982523000132/pton-20230630.htm), 2023 |

### 3.5. Zenia

| Clasificación / sistema | Qué mide o anuncia | Referencia y resultado | Condiciones / alcance | Fuente y año |
|---|---|---|---|---|
| **Marketing**; Zenia, asistente de yoga | Seguimiento de movimientos y feedback de posturas | No se encontró validación primaria de conteo de fuerza, series exactas, precisión/recall o MAE. Reconocer una asana es una tarea diferente. | Smartphone; sin protocolo de encuadre/fps verificable en la descripción de la empresa. No se afirma su disponibilidad actual. | [Descripción de la propia empresa](https://www.linkedin.com/company/zenia-ai-yoga-teacher), s. f.; consulta 2026 |

## 4. Estudios académicos de conteo por pose

### 4.1. Oliosi et al. (2026), JMIR mHealth e82412

Dotmoovs/MoveNet, no MediaPipe. Referencia: dos anotadores; **44 personas**. Teléfonos horizontales en el suelo, **1080p/30 fps**; frontal/diagonal/lateral; **0,9/1,8/2,0/3,6 m**.

| Clasificación / ejercicio | Qué mide | Resultado publicado | Encuadre | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; sentadilla | Conteo por vídeo; MAE y tasa denominada detection rate | Global: tasa **61,5%**, MAE **1,11 reps/vídeo**. Diagonal/2 m: **95,5%**, MAE **0,045**. Lateral/2 m: **36,4%**, MAE **0,636**. Lateral/0,9 m: **0%**, MAE **5**. | Series de aproximadamente 5 reps; condiciones controladas. | [Oliosi et al., métodos y tabla 3](https://mhealth.jmir.org/2026/1/e82412), 2026 |
| **Revisado por pares**; flexiones | Mismas métricas | Global: tasa **61,1%**, MAE **1,08**. Diagonal/1,8 m: **85,7%**, MAE **0,286**. Lateral/2 m: **66,7%**, MAE **1,095**. Frontal/3,6 m: **20%**, MAE **2,700**. | Misma captura; no demuestra desempeño en banca con barra. | [Oliosi et al., tabla 2](https://mhealth.jmir.org/2026/1/e82412), 2026 |

**Ambigüedad:** se define acierto primario como conteo exacto por vídeo, pero también detection rate como razón de conteos. Se conserva la etiqueta publicada; no se interpreta como recall de eventos. Precisión/recall de eventos NR.

### 4.2. Sousa et al. (2026): MediaPipe y máquina de estados

| Clasificación / sistema | Qué mide | Referencia y resultado | Ejercicios y encuadre | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; Movement Description Language, MediaPipe | Conteo/fases | Conteo manual: **1863/1916**, «accuracy» **97,2%**. Sentadilla **546/572**; peso muerto **360/379**; flexiones **234/238**; press de hombros **355/356**. Series exactas, MAE y precisión/recall de eventos NR. | Vídeos propios, públicos y competitivos; perspectivas variadas; 9 participantes propios. Rendimiento computacional: vídeo móvil **1080p/30 fps**, procesado en ordenador. Distancia NR. | [Sousa et al., tabla 2 y resultados](https://www.mdpi.com/2411-5142/11/2/162), 2026 |
| **Revisado por pares**; fases del mismo sistema | Ciclos internos de la máquina de estados | Cobertura de fases **98,7%**, transiciones falsas **1,2%**, según sus registros internos. **No hay anotación externa de fases** que valide error temporal o TUT. | Incluye sentadilla, peso muerto, presses y movimientos compuestos; clases pequeñas y desbalanceadas. | [Sousa et al., evaluación de fases](https://www.mdpi.com/2411-5142/11/2/162), 2026 |

El porcentaje no acredita series exactas. Los distintos totales de vídeos entre apartados impiden reconstruir aquí su denominador de series.

### 4.3. Hsu et al.: OpenPose y conteo por periodicidad

| Clasificación / sistema | Qué mide | Referencia y resultado | Condiciones | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; MM-Fit | Conteo sobre esqueletos, incluidos los obtenidos con OpenPose | Etiquetas del conjunto: **MAE normalizado 0,06**, OBOA **0,94**. No significa 0,06 repeticiones ni 94% de series exactas. | Incluye sentadilla, flexiones, press, zancadas, remo y curls; cámara frontal del conjunto original. | [Hsu et al., métodos y resultados](https://pmc.ncbi.nlm.nih.gov/articles/PMC10692053/), volumen 2024; online 2023 |
| **Revisado por pares**; UI-PRMD | Conteo sobre esqueletos Kinect | **MAE normalizado 0,06**, OBOA **0,95**. | Rehabilitación, cámara de profundidad frontal; comparación contextual, no prueba de RGB móvil. | [Hsu et al.](https://pmc.ncbi.nlm.nih.gov/articles/PMC10692053/), 2023/2024 |
| **Revisado por pares**; conjunto adicional con OpenPose | Conteo multivista y concurrente | Dos anotadores; **MAE normalizado 0,07**, OBOA **0,91**. Precisión/recall de eventos y series exactas NR. | 11 adultos, 57 secuencias de esqueletos; iPad Pro **30 fps**; frontal/izquierda/derecha; sentadillas, elevaciones laterales y zancadas. Distancia NR. | [Hsu et al.](https://pmc.ncbi.nlm.nih.gov/articles/PMC10692053/), 2023/2024 |

OBOA admite un error de hasta una repetición. Su MAE se divide por el conteo real de cada vídeo; se conserva como error relativo.

### 4.4. Fitcam: OpenPose y LSTM

| Clasificación / sistema | Qué mide | Referencia y resultado | Condiciones y límites | Fuente y año |
|---|---|---|---|---|
| **Revisado por pares**; Fitcam | Clasificación de poses/segmentos y posterior conteo | Etiquetado de vídeos; resumen: accuracy de modelos **>90%**; clasificación multiclase **99,965%** en sus pruebas. **No son porcentajes de series exactas**. MAE de conteo por serie y precisión/recall de eventos NR. | 3 sujetos; flexiones, sentadillas, abdominales y plancha; cuerpo visible; distancia no verificable por errata de unidad en el original; **720p/30 fps**, preprocesado a **24 fps**. Segmentación manual de repeticiones para entrenar/evaluar modelos. | [Japhne et al., método y evaluación](https://link.springer.com/article/10.1186/s40537-024-00915-8), 2024 |

### 4.5. Pūioio: sistema móvil en tiempo real

| Clasificación / sistema | Qué mide | Referencia y resultado accesible | Condiciones y límites | Fuente y año |
|---|---|---|---|---|
| **Preprint**; Pūioio | Conteo mediante pose, umbrales, flujo óptico y máquina de estados | El resumen informa accuracy **98,89%** en pruebas reales y hasta **98,85%** en vídeos grabados. No se verifica en esa página su equivalencia con serie exacta o precisión/recall. | Sentadilla, flexiones y dominadas; inferencia en el móvil. Definición detallada de accuracy, MAE, fps, distancias y reparto de participantes no verificados en la página del resumen; no se descargó el texto completo. | [Sinclair, Kautai y Shahamiri, arXiv:2308.02420](https://arxiv.org/abs/2308.02420), 2023 |

## 5. Abstención y comunicación de confianza

### 5.1. Qué se encontró en productos

| App / clasificación | Cuándo reconoce problemas | Cómo lo presenta y qué hace | Qué no demuestra | Fuente y año |
|---|---|---|---|---|
| Metric Lift — **documentación oficial** | Disco no detectado, ángulo/encuadre incorrecto, distancia inadecuada, frames perdidos; vídeo por debajo de **50 fps** | Indicadores de estabilidad y disco rojos/verdes antes de grabar; advertencias posteriores, por ejemplo «No reps detected», «Wrong angle» o «Slow frame rate». | Son avisos de captura; no publican probabilidad calibrada de conteo correcto ni eficacia de rechazo. | [Grabar una serie](https://metric.coach/docs/recording-a-set), actualización 2026 |
| Metric Lift — **documentación oficial** | Pérdida del disco durante el recorrido | Puede eliminar toda o parte de una repetición. La documentación advierte que conserva números medidos con cámara temblorosa y que ese movimiento **no genera necesariamente aviso**. | No es abstención completa ante datos dudosos; el total puede quedar incompleto. | [Problemas de grabación](https://metric.coach/docs/common-recording-issues), actualización 2026 |
| WL Analysis — **documentación oficial** | Disco ausente, selección errónea u oclusión | Permite seleccionar el disco manualmente; explica cuándo pierde el seguimiento. | La advertencia sobre errores de su análisis con IA no es una confianza cuantificada del contador o de la velocidad. | [Tutorial](https://wlanalysis.com/tutorial.html), s. f.; consulta 2026 |
| Tempo — **documentación oficial** | Seguimiento no utilizado | Ofrece continuar sin seguimiento. La ayuda explica que el crédito de entrenamiento puede basarse en las reps recomendadas. | No debe interpretarse ese crédito como conteo observado ni como abstención con resultado metrológico válido. | [Modo solo teléfono](https://support.tempo.fit/support/solutions/articles/151000178713-phone-only-workout), 2025 |

Para Onyx, Kemtai, Peloton Guide y Zenia no se encontró documentación suficientemente concreta de una etiqueta de confianza del conteo acompañada de evaluación cuantitativa. Eso no prueba que carezcan de avisos de visibilidad.

### 5.2. MediaPipe no entrega la probabilidad de una serie correcta

| Clasificación / sistema | Qué aporta | Resultado de fiabilidad disponible | Implicación para KPKN | Fuente y año |
|---|---|---|---|---|
| **Documentación oficial**; MediaPipe Pose Landmarker | Coordenadas de pose y umbrales de confianza de detección, presencia y seguimiento | La guía no publica exactitud de series de fuerza ni calibración de una etiqueta «seguro». | Un umbral de pose es un filtro del modelo. Interpretarlo como probabilidad de que el conteo total sea correcto sería una inferencia sin validar. | [Google, Pose Landmarker](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker), actualización 2026 |

## 6. Listón propuesto para KPKN

### 6.1. Dominio de uso que debe declararse

**Todos los números de esta sección son propuestas originales de aceptación de este informe (2026), no resultados medidos de KPKN ni cifras atribuidas al mercado.** Las fuentes de cada fila justifican el orden de exigencia o la necesidad de validar la métrica; no publican necesariamente el umbral propuesto.

**Encuadre bueno, definición solicitada para KPKN:** móvil fijo de lado, cuerpo entero visible, a **2–4 m**, con luz suficiente y recorrido completo. Para VBT, además: disco visible durante toda la serie, diámetro real configurado, cámara aproximadamente perpendicular al plano del movimiento, altura cercana a la mitad del recorrido y vídeo de **al menos 60 fps efectivos** como objetivo inicial de captura. Los **30 fps** de pose deben validarse como una condición propia. Estas frecuencias son requisitos propuestos; no una garantía por sí solas. Los protocolos de [Taber 2023](https://journal.iusca.org/index.php/Journal/article/view/263) y [Renner 2024](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0313919), y los [avisos de Metric 2026](https://metric.coach/docs/recording-a-set), fundamentan comprobar captura y escala.

**Precaución de alcance:** lateral y cuerpo entero no aseguran pose suficiente. Los resultados de [Oliosi 2026](https://mhealth.jmir.org/2026/1/e82412) obligan a probar ese encuadre por ejercicio; si una vista diagonal funciona mejor, debe ofrecerse como modo validado distinto. No se puede excluir retrospectivamente un vídeo lateral bien grabado solo porque el contador falle.

**Cualquier encuadre:** incluye frontal, oblicuo, demasiado cerca/lejos, recortes, oclusiones y cámara móvil. No hay base publicada para prometer la misma exactitud en todo ese dominio. KPKN debe medirlo y rechazar resultados no fiables; no convertirlo silenciosamente en «encuadre bueno».

Para aceptación, **básicos** significa sentadilla, peso muerto, press banca, press sobre la cabeza, remo, dominadas y flexiones. Cada ejercicio y variante admitida debe aprobar por separado, incluyendo su ejecución con carga cuando corresponda. «Resto» significa otros ejercicios expresamente validados; no todo el catálogo por defecto. Esta clasificación es una decisión de alcance de KPKN, no una taxonomía tomada de los estudios.

### 6.2. Umbrales de aceptación

| Métrica | Encuadre bueno: propuesta de este informe | Cualquier encuadre | Justificación y fuente |
|---|---|---|---|
| **Series exactas, básicos** | **≥95%**, por ejercicio; MAE de conteo **≤0,10 reps/serie**. El denominador incluye todas las series elegibles grabadas, también abstenciones y fallos de procesamiento. | No prometer un mínimo universal. Publicar acierto, error y abstención por encuadre; permitir corrección humana. | Se elige una meta exigente de producto porque los trabajos no ofrecen un estándar comparable de serie exacta. La dependencia de vista en [Oliosi 2026](https://mhealth.jmir.org/2026/1/e82412) impide trasladar el mejor caso al uso libre. |
| **Series exactas, resto admitido** | **≥90%**, por ejercicio; MAE **≤0,25 reps/serie**. No habilitar un ejercicio por el promedio del catálogo. | Mismo criterio de transparencia; fuera de vistas validadas, resultado provisional o abstención. | El conteo por repetición y las clases pequeñas de [Sousa 2026](https://www.mdpi.com/2411-5142/11/2/162) no prueban series largas exactas. La diferencia entre básicos/resto es una decisión de despliegue. |
| **Eventos de repetición** | Básicos: precisión y recall **≥99%** cada uno. Resto: **≥98%** cada uno. Evaluar omisiones y fantasmas por separado, además del total. | Informar las mismas métricas por vista; no confundir menor cobertura con menor tasa de falsos positivos. | Las omisiones y fantasmas observadas en [Renner 2024](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0313919) exigen ambas métricas. Los umbrales son propuestos. |
| **Cuando dice «seguro»** | **≥99% de series exactas** entre las aceptadas con esa etiqueta, y **precisión de eventos ≥99%** dentro de ellas. Para ambas tasas, exigir **límite inferior del IC del 95% ≥99%**. Cobertura de «seguro» **≥80%** de series elegibles en básicos y **≥70%** en el resto. | La exigencia de acierto de la etiqueta se conserva para todo resultado que la muestre; si no hay evidencia, abstenerse. No se exige cobertura mínima en encuadres adversos. | No se encontró benchmark comercial de confianza calibrada. Es un contrato propuesto para evitar un «seguro» vacío o basado solo en [confianza de pose de MediaPipe](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker), documentación 2026. |
| **Velocidad media concéntrica, disco calibrado** | **MAE ≤0,03 m/s; RMSE ≤0,04 m/s; abs(sesgo) ≤0,02 m/s; LoA del 95% dentro de [−0,10; +0,10] m/s**. Cada ejercicio, carga y rango de velocidad deben estar representados. | Sin escala fiable, disco visible o captura adecuada: no mostrar m/s como medición válida. No derivar escala física de la altura estimada por pose. | Busca aproximarse a los mejores resultados de cámara de [Renner 2024](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0313919), con MAE contrastable con [Puppyn 2025](https://journals.sagepub.com/doi/10.1177/17479541251391967). Es más exigente que algunas versiones de Metric, especialmente en banca. |
| **Velocidad pico, si se publica** | Objetivo provisional distinto: **MAE ≤0,10 m/s; RMSE ≤0,15 m/s**. Aprobación independiente de MCV y por tipo de levantamiento. | No mostrarla como validada por haber aprobado velocidad media. Abstenerse si el muestreo no sostiene el resultado. | Los estudios de [Grossi 2026](https://www.mdpi.com/2411-5142/11/2/197) y [Lemus 2024](https://journals.sagepub.com/doi/10.1177/17479541241266248) muestran que pico y media requieren evaluaciones distintas. Estas tolerancias son provisionales, no consenso publicado. |
| **ROM y trayectoria calibrada** | ROM vertical: **MAE ≤2 cm** y LoA dentro de **±5 cm**. Trayectoria en el plano: **RMSE espacial ≤2 cm** frente a trayectoria de referencia proyectada al mismo plano. | Sin calibración, permitir una traza visual identificada como cualitativa; no asignarle precisión en centímetros ni trayectoria 3D. | Objetivos propios, separados de velocidad; los LoA de ROM de [Trowell 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11283170/) y el acuerdo de [Iron Path 2021](https://pubmed.ncbi.nlm.nih.gov/33666594/) muestran que no basta validar MCV. |
| **Pérdida de velocidad** | **MAE ≤5 puntos porcentuales; percentil 95 del error absoluto ≤10 puntos; abs(sesgo) ≤2 puntos** frente a la pérdida calculada con la referencia. Serie y extremos completos y fiables. | Abstenerse si hay una repetición omitida/dudosa que pueda cambiar el máximo o la última repetición, o si la incertidumbre supera el umbral. | No se encontró validación directa suficiente de esta salida en las apps estudiadas. La [definición oficial de Metric 2026](https://metric.coach/docs/training-metrics) identifica qué comparar; los errores de MCV de [Puppyn 2025](https://journals.sagepub.com/doi/10.1177/17479541251391967) no demuestran precisión de esa razón. |
| **Fases: excéntrica, pausa inferior, concéntrica y pausa superior** | Objetivo provisional: **MAE de duración por fase ≤0,15 s**; percentil 95 del error de cada frontera temporal **≤0,30 s**. Evaluar cada fase por separado. | Marcar fase indeterminada ante oclusión, corte o estado ambiguo. No rellenarla con un tempo esperado. | [Metric 2026](https://metric.coach/docs/training-metrics) documenta las salidas, pero no su error. La validación interna de fases de [Sousa 2026](https://www.mdpi.com/2411-5142/11/2/162) no proporciona un patrón temporal externo. No hay un listón de mercado publicado verificable para esas tolerancias. |
| **Duración de rep / tiempo bajo tensión estimado** | Objetivo provisional: **MAE ≤0,20 s por repetición**; MAE de acumulado por serie **≤máx(1,0 s; 5% del tiempo de referencia)**. | No acumular segmentos sin seguimiento como si estuvieran medidos. Identificar las partes excluidas y el resultado parcial. | Es una propuesta de calidad temporal, no una cifra de validación comercial. La [documentación de Metric 2026](https://metric.coach/docs/training-metrics) define acumulados; no demuestra tensión muscular real. |

«Precisión cuando dice seguro» queda así definida tanto por **serie** como por **evento**, sin usar una para sustituir a la otra. La cobertura evita aprobar un sistema que se abstiene casi siempre. Los intervalos deben respetar agrupación por persona y sesión; miles de frames de pocas personas no constituyen miles de pruebas independientes.

### 6.3. Definiciones para velocidad perdida y tiempo

Para comparar la pérdida de velocidad se propone fijar la misma fórmula en app y referencia:

**VL = 100 × (1 − velocidad media de la última repetición válida / mayor velocidad media de una repetición válida de la serie).**

Coincide con la definición funcional documentada por [Metric, 2026](https://metric.coach/docs/training-metrics). No mezclarla con pérdida respecto de la primera rep, respecto de un promedio o basada en velocidad pico. El error se expresa en **puntos porcentuales**, no como porcentaje relativo de VL. Una tolerancia absoluta de velocidad no garantiza por sí sola la tolerancia de esta razón; hay que validarla en series con fatiga.

La cámara observa tiempos de movimiento y pausas, no tensión muscular. Para KPKN, el término **«tiempo bajo tensión estimado»** debe indicar qué fases y pausas suma, y qué descansos excluye. Su referencia debe usar exactamente esa convención. Una plancha no debe evaluarse como si sus segundos fueran repeticiones dinámicas. Esta es una definición de producto propuesta, no una conclusión fisiológica extraída de una traza del disco.

### 6.4. Cuándo abstenerse y cómo presentarlo en KPKN

Propuesta de interfaz, no comportamiento ya implementado:

- **Antes de grabar:** comprobar cuerpo/articulaciones relevantes, disco y escala cuando corresponda, estabilidad y captura. Dar instrucciones concretas: «Aleja el móvil», «Deja el disco completo a la vista» o «Apoya el teléfono».
- **Resultado seguro:** «8 repeticiones · conteo seguro», únicamente tras calibrar y validar la etiqueta con el criterio anterior. Es un ejemplo de texto, no una medición de KPKN.
- **Resultado dudoso:** «Conteo por revisar» y acceso al vídeo con los eventos marcados. No presentar un total parcial como serie completa. La confianza del conteo, la velocidad y los tiempos se evalúan por separado.
- **Sin medición fiable:** «No pude contar esta serie: el cuerpo salió del encuadre» o «Velocidad no disponible: se perdió el disco». Conservar el vídeo y permitir introducción manual.
- **Pérdida temporal del seguimiento:** no asumir que la persona se detuvo ni inventar repeticiones o fases para cubrir el hueco. La recuperación posterior no certifica el segmento perdido.

La confianza debe entrenarse/calibrarse contra el error real de la salida final, con datos separados de ajuste y prueba. Visibilidad de articulaciones, coherencia temporal y continuidad de la traza pueden ayudar a decidir, pero no equivalen por sí solas a probabilidad de acierto.

### 6.5. Evidencia mínima para declarar equivalencia

Este es un protocolo propuesto, no una prueba ejecutada en esta investigación:

1. **Conteo completo:** dos evaluadores independientes anotan vídeo continuo, inicio/final de cada rep, serie y movimientos que no deben contar. Resolver desacuerdos antes de puntuar. Incluir entrada/salida del rack, reposiciones, medias reps, pausas, fallos y descansos; definir qué se considera rep válida.
2. **Separación real:** personas y sesiones de prueba no usadas para ajustar reglas o confianza. Probar Android de distintas gamas, cámaras, luz, ropa, complexiones, oclusiones y fatiga. Una validación en iPhone no certifica el pipeline Android.
3. **Duraciones y carga:** como diseño inicial, series de **1–5, 6–12 y 13–20 repeticiones**, varios tempos y cargas. Publicar por ejercicio y longitud; los buenos resultados de intentos individuales no prueban series largas.
4. **Velocidad y recorrido:** comparación simultánea con Vicon/OptiTrack u otro patrón adecuado, o con un LPT validado como referencia práctica declarada. Igualar eje, trayectoria, intervalo concéntrico, filtrado y escala; no tratar señal vertical y resultante como la misma magnitud. Informar errores solo sobre mediciones emparejadas **y, aparte, cuántas no pudieron medirse**.
5. **VL y tiempo:** para VL, series continuas con cambios reales de velocidad; para fases, anotación temporal externa, idealmente con vídeo de alta frecuencia sincronizado. No validar tiempos contra los estados internos que produce el propio contador.
6. **Informe de aceptación:** versión de app/modelo, dispositivos, participantes, series y reps, cifras por condición, precisión/recall, serie exacta, MAE, cobertura/abstención y acuerdo con referencia. Intervalos del **95%** agrupados por persona; para «seguro», curva de error frente a cobertura. No aprobar por un promedio que oculte ejercicios o vistas fallidas.

No se prescribe un tamaño muestral universal: debe calcularse para demostrar los mínimos y sus intervalos, especialmente la etiqueta «seguro». Las cantidades y bandas de este protocolo proceden de la propuesta de este informe, no de una supuesta norma del mercado.

## 7. Lo no encontrado, no publicado o no verificable

| Pregunta | Resultado de la búsqueda / efecto en el listón |
|---|---|
| ¿Qué porcentaje de series completas cuentan exactamente Kemtai, Onyx, Tempo, Peloton Guide o Zenia? | No se encontró una validación primaria pública y comparable que lo establezca por ejercicio, encuadre y longitud de serie. No se les atribuye un porcentaje. |
| ¿Publican precisión y recall de cada repetición? | No se localizaron para esas apps comerciales en un protocolo reproducible de fuerza. Las descripciones de capacidades, ROM o reconocimiento de posturas no responden esa pregunta. |
| ¿Una etiqueta de confianza garantiza cierta precisión? | No se encontró una evaluación pública de calibración del conteo y cobertura de aceptación de las apps estudiadas. Sí hay documentación de avisos de captura y pérdida de seguimiento. |
| ¿MediaPipe acredita por sí solo el conteo? | No. Sus coordenadas y scores no incluyen una validación del contador específico de KPKN. Los estudios de otros pipelines orientan el ensayo, no certifican una implementación distinta. |
| ¿Hay equivalencia con cualquier encuadre? | No se encontró evidencia que sostenga una garantía universal. Los mejores casos no eliminan los fallos de otras vistas; «multivista» u OBOA tampoco significan series exactas desde toda posición. |
| ¿Qué error tiene la pérdida de velocidad comercial? | No se encontró evidencia directa suficiente por serie para fijar tolerancias de error de VL de todas estas apps. Una buena MCV, r alto o ICC alto no valida automáticamente VL. |
| ¿Qué error tienen fases y TUT? | Se encontraron funciones anunciadas y definiciones oficiales; no un benchmark independiente comparable de MAE temporal de las apps citadas. La métrica interna de cobertura de fases de MDL no sustituye anotaciones externas. |
| ¿Y la trayectoria completa? | Validaciones de ROM/desplazamiento no demuestran necesariamente error de cada punto de la trayectoria. Tampoco prueban la componente fuera del plano de una cámara. |
| ¿Se sabe qué hacen hoy todas las versiones Android? | No. Las versiones estudiadas y las plataformas se consignan; actualizaciones y documentación actual no revalidan automáticamente algoritmos, dispositivos o sistemas operativos. |
| ¿Están todas las condiciones de captura publicadas? | No en los materiales accesibles de todos los estudios. Se marcó NR, sin completar fps, distancia o diámetro por analogía con otra app. |
| ¿Se revisaron todos los textos y anexos? | No. Algunos originales tienen acceso restringido o errores de recuperación, aunque sus resúmenes/fragmentos están indexados. Se distingue ese límite de la ausencia de publicación; no se descargaron anexos para completarlos. |

**Decisión de producto derivada de la evidencia:** declarar equivalencia solo para la combinación de ejercicio, métrica, plataforma y captura que KPKN haya probado. Los umbrales propuestos convierten una aspiración comercial en resultados medibles; no afirman que KPKN ya los cumpla.

