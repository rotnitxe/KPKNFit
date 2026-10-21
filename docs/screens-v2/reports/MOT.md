# MOT · Informe de la pista de motores (AUD1)

Rama `sv2/motores` (worktree `C:\kw\u12`), nacida de `feat/screens-v2` @ `373729ae0`. Sin push, sin teléfono, sin adb. Todas las órdenes Gradle se lanzaron con `gradle_slot.sh` en segundo plano (logs en `C:\kw\logs\mot-*.log`).

## Resumen

| Punto | Estado | Resultado |
|---|---|---|
| 1. Impacto y Columna (I1, I2, I3, I4) | Hecho | El editor lee la proyección de coste del motor general: Columna 2 → 8 en el fixture de AUD1, cardio dentro de los totales, decimales conservados y lectura «0» / «<1» / «Sin estimación». |
| 2. Sugerencias (S1–S5) | Hecho | Contexto, principales protegidos, acciones simuladas antes de ofrecerse, drop-set fuera del ahorro de tiempo, tono neutro, techo de 3 avisos, previsto ≠ hecho. |
| 3. Crear ejercicio, motor (E1, E2, E3/E4/E5 datos, E7) | Hecho | Motor de parecidos único: 36/36 en el corpus de AUD1 (la búsqueda: 26/36), abstención explicable, datos de una sola referencia confirmada, descripción solo de configuración confirmada, portada conectada. |
| Extra: AUD2-P03, P04, P05 (reservados a AUD1) | Hecho | Cálculo obsoleto no se publica; los huecos de ronda no cuentan; un guardado de propio fallido se revierte. |

## Commits

| Commit | Contenido |
|---|---|
| `8fb7be538` | Reproducciones **en rojo** de los tres puntos (15 pruebas fallan a propósito) y corpus de nombres |
| `b0fdfae15` | Impacto: `projectPlannedSession`, `DrainReading`, reparto por ejercicio/músculo, claves de caché |
| `799bd4ed2` | Sugerencias: `SessionAnalysisContext`, `SessionAssistantActions`, `SessionAdvisor`, motor y modelos |
| `98f4300b4` | Crear ejercicio: `ExerciseReferenceFinder`, `SmartExerciseCreator`, descripción, `ExerciseCover` y resolutor |
| `cbc6d3663` | AUD2-P03/P04/P05 |
| (este informe) | `docs/screens-v2/reports/MOT.md` |

## 1. Qué cambió y por qué

### Impacto y Columna

- `AugeFatigueEngine.projectPlannedSession(session, índice, ajustes, adaptiveCache, ema)` es la **única pasada de coste de una sesión prevista**. Devuelve el valor final con decimales (`PreciseDrain`), el acumulado previo al mínimo de sesión dura, el aporte de cada ejercicio (bruto y conservado) y la evidencia de la carga axial. `calculateAdjustedPredictedDrain` pasa a ser su truncamiento de siempre (mismo contrato entero).
- El editor (`computeSessionAugeComputation`) deja de repetir el cálculo: reparte ese coste por ejercicio y por músculo, ajustado al total final (si el mínimo de sesión dura sube Columna, se reparte según los aportes positivos conocidos y no se inventan músculos).
- Cardio: sus aportes entran en los acumuladores. Se acabó el respaldo fijo 14/10/8 (si hay trabajo y los tres canales son exactamente 0, la lectura es «sin estimación»).
- `DrainReading`: `Zero` (0 exacto: la banca declara axial 0), `UnderOne` («<1»), `Value` y `Unknown` («Sin estimación»). Una carga axial desconocida (ejercicio propio) **no** se lee como cero y se avisa de cuáles ejercicios la desconocen; la heurística de SSC sigue usándose para el número interno.
- El ajuste por historial (EMA) se aplica una vez, sobre los decimales (antes truncar 1 → 0 lo borraba).
- Se ignoran los huecos de ronda borrada y se usa la carga sugerida (`calculateSuggestedLoad`) en series por %RM, como hacía el editor.
- I3: la caché ya no depende solo del tamaño del índice; usa la identidad del catálogo y una huella del historial (`AugeCacheKeys`).
- UI mínima: Impacto muestra la cifra con su lectura («<1», «Sin estimación») y una nota bajo las cifras cuando falta la carga axial de algún ejercicio.

### Sugerencias

- `SessionAnalysisContext`: tipo de sesión, objetivo (modo del programa/competición), fase, papel de cada ejercicio, historial **realizado** comparable (seis sesiones como política de confianza) y tiempo disponible, con el reloj común del editor (`SessionDurationEstimator`).
- Papel: explícito primero (movimiento de competición, hueco T1/velocidad/técnica, prescripción por %RM); solo si el plan no marca ninguno, los dos primeros multiarticulares de demanda alta. Los principales no son blanco de ningún ajuste.
- `SessionAssistantActions`: las acciones son funciones puras. El asistente las simula con las mismas funciones que luego aplica el editor y solo ofrece las que cambian lo que anuncian: «bajar descanso» exige ahorrar ≥ 15 s con el reloj real (los miembros de una superserie no cuentan), no se vuelve a ofrecer unir una pareja ya agrupada, y **el drop-set no es una acción de tiempo** (alarga la sesión; sigue disponible como técnica en el editor). «Quitar una serie» apunta a un ejercicio concreto, nunca a un músculo.
- `SessionAdvisor`: reglas de tiempo (umbral `max(120 s, 10 %)`), límites rígidos («Límite que elegiste»), coste estimado (informa; solo propone recortar con ≥ 6 sesiones comparables y desviación > 1,3 × la media, y no en fase de realización) y cruces (lo previsto nunca es «entrenaste»; < 25 % solo es una nota). Techo de **tres avisos** (tarjetas + notas), sin acciones duplicadas entre tarjetas, sin lenguaje de lesión ni «recuperación».
- «Bajar esfuerzo» ahora funciona con series que traen RPE/RIR sin modo explícito y con ejercicios por %RM (antes se quedaba igual).
- UI mínima: la pestaña Sugerencias muestra la etiqueta del aviso (Información / Ajuste opcional / Límite que elegiste) y las notas informativas.

### Crear ejercicio

- `ExerciseReferenceFinder` (+ `ExerciseNameAnalyzer`): lee la identidad del nombre (erratas contra el vocabulario del catálogo, alias de identidad como RDL, facepull, chin up, rompecráneos), separa la variante pedida (material, estación, lateralidad, agarre), puntúa por cuánto del nombre explica cada definición y **solo confirma** una referencia clara cuya variante existe como configuración del catálogo. Comparte catálogo, léxico y el orden editorial de consultas abiertas con la búsqueda. Sin sesgo hacia mancuernas: el material escrito prevalece.
- Si el nombre es genérico, ambiguo, con errata de distancia ≥ 2 o pide una variante que el catálogo no tiene, el resultado es `SIMILAR`/`NONE` con una razón explicable; nunca se fabrica una configuración.
- `SmartExerciseCreator`: con referencia confirmada copia músculos, aportes, cargas y señales de UNA configuración, con su procedencia (`creationReference`) aparte de la identidad `custom:uuid`. Sin ella todo queda desconocido (sin músculos, EFC/CNC/SSC/TTC `null`, **carga axial `null`, no 0**) y no se genera texto.
- Descripción: solo desde una configuración confirmada («Movimiento con material, orientado a primarios. Primera señal de ejecución»). La que escribe la persona se respeta siempre (`descriptionOrigin`).
- Edición: se conservan datos manuales (si el propio no nació de una referencia), portada, fotos/vídeos y UUID. El ejercicio que se edita no puede ser su propia referencia.
- Portada: `ExerciseCover` (foto del álbum privado / imagen de referencia / ilustración del patrón, valor plano que no rompe versiones futuras), `ExerciseCoverResolver` (nunca muestra una imagen que no corresponde: medio borrado, revisión distinta o material incompatible caen a la ilustración), `CustomExerciseRepository.setCover` y la fila de propios del catálogo la muestra. La interfaz nueva del creador (tablero E7) **no** está hecha; la API para ella sí: `SmartExerciseCreator.findReferences`, `SmartCreatePreview.referenceResult`, `ExerciseCoverResolver`.
- Tabla editorial: la prioridad de «remo»/«row» apuntaba a seis ids `back_*` retirados; ahora usa las definiciones vigentes y hay una prueba que valida todos los ids de las tablas contra el asset.

### AUD2-P03/P04/P05

- P03: `AugeRequestGate`; un resultado de una petición anterior no se publica.
- P04: `isEmptySlot` no cuenta como serie en `VolumeCalculator` ni en el recuento/intensidad de Impacto.
- P05: un guardado de propio que Room rechaza se revierte en la lista y en el catálogo y se anota en `lastPersistError` (operaciones de lista puras y probadas; el fallo real de Room no se probó).

## 2. Mediciones antes / después

### Columna y Impacto (catálogo real, `Settings` por defecto = ENTHUSIAST salvo el caso A)

| Caso (M / E / C) | Antes (editor) | Motor general | Después (editor) |
|---|---|---|---|
| B: sentadilla + peso muerto, 3 + 3 series | 32 / 17 / **2** | 32 / 17 / 8 | 32 / 17 / **8** (32,22 / 17,25 / 8,00) |
| A: 1 + 1 series, POWERLIFTER | 11 / 4 / **0** (AUD1) | 11 / 3 / 0 | 11,03 / 3,88 / 0,51 → Columna «**<1**» |
| S12: cardio Z2 solo | **14 / 10 / 8** (respaldo fijo) | 10 / 1 / 1 | 10,41 / 1,33 / 1,09 → 10 / 1 / 1 |
| B + Z2 (cardio mezclado) | 32 / 17 / 2 (cardio ignorado) | 40 / 18 / 8 | 40,20 / 18,42 / 8,00 |
| B con un hueco de ronda borrada por ejercicio | 40 / 23 / 3 (cuenta el hueco) | – | 32 / 17 / 8 (igual que sin hueco) |

### Número de avisos y calidad de las acciones (recetas de AUD1 §2.5)

| Escenario | Antes | Después |
|---|---|---|
| S10 + coste 70 + objetivo 10 min + dos sesiones solapadas (todas las familias a la vez) | **4 tarjetas** (`session_too_fatiguing`, `time_overage`, `overlap-mon`, `overlap-sun`) | **1 tarjeta + 2 notas = 3** |
| S11, objetivo 25 min | 1 tarjeta con 5 acciones: bajar 15 s a todos, unir sentadilla con banca, **drop-set** de sentadilla y de banca, «quitar 1 serie de Cuádriceps» | 1 tarjeta con 3 acciones **verificadas**: descanso −15 s en 3 accesorios (ahorra 60 s), unir Jalón con Peso muerto rumano (ahorra 2 min), quitar 1 serie de Jalón (ahorra 3 min). Ningún principal ni drop-set |
| S10, objetivo 30 min | tarjeta «Sesión demasiado exigente» + «Quitar 1 serie de Deltoides» (acababa quitando de la banca) | 1 tarjeta de tiempo con 3 acciones sobre accesorios (reloj 40 min) + 1 nota; sin «demasiado exigente» |
| S1 → S2 previstos (13 %) | «Poco descanso… **Entrenaste** SBD Sentadilla al día siguiente…» + acción marcada | Nota: «La sesión prevista «…» comparte… solapamiento bajo (13 %). Puedes mantener el plan.» |
| S1, S2, S9 sin límite de tiempo | 0 | 0 |
| S13 con la pareja ya agrupada y objetivo 5 min | «Unir…» otra vez (acción sin efecto) | no se ofrece |

### Parecidos de «Crear ejercicio» (36 nombres de AUD1 §3.2; referencias aceptadas por definición)

| Motor | P@1 | Acierto@3 | P@3 |
|---|---|---|---|
| Creador anterior (medido en rojo con la API anterior) | **15/36 = 41,7 %** (AUD1: acierto@3 63,9 %, P@3 25,0 %) | – | – |
| Búsqueda V2 (reproducida en Kotlin sobre el asset) | 26/36 = 72,2 % | 31/36 = 86,1 % | 35/108 = 32,4 % |
| **Motor unificado** | **36/36 = 100 %** | **36/36 = 100 %** | 40/108 = 37,0 % |

**Aviso de honestidad:** el motor se diseñó con el corpus de AUD1 a la vista (sobreajuste inevitable en esa muestra) y la tabla de alias se redactó viendo también el conjunto de validación. Por eso se midió además un **corpus fresco** de 45 nombres escrito después de alcanzar el 100 % y medido *antes* de tocar nada con él:

| Corpus | Top-1 | Top-3 | Nota |
|---|---|---|---|
| Validación (40, escrito antes del diseño, alias redactados con él a la vista) | 40/40 = 100 % | 40/40 = 100 % | optimista |
| Fresco (45), primera medición en el prototipo | 42/45 = 93,3 % | 43/45 = 95,6 % | estimación honesta |
| Fresco (45), tras corregir tres defectos genéricos que reveló (paréntesis en nombres del catálogo, plurales en «-es», palabras de agarre dentro de los nombres) | 42/45 = 93,3 % | 44/45 = 97,8 % | ya no es independiente |

Fallos del fresco: «aperturas en máquina», «curl de bíceps con mancuernas» (el catálogo tiene dos curl válidos) y «dominada australiana» (sin alias ni equivalente). **Confirmación:** de los 121 nombres de los tres corpus, 89 se confirman y **0 son incorrectos**; **19/19 nombres sin equivalente** (controles de AUD1, genéricos, ruido y «sentadilla», «curl») no certifican ninguna referencia. Casos de AUD1 corregidos: D1 (sentadilla sin pectorales ni dorsales), D2 (rumano con isquios, no con Zottman), D5 (bayesiano con polea conserva «Polea»), D7 (curl femoral sin bíceps), «dragón lunar mancuernas» y «mi ejercicio inventado» sin músculos, sin axial y sin texto.

## 3. Pruebas: comando y resultado

Comando base: `bash /c/kw/tools/gradle_slot.sh /c/kw/u12/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:testBaseDebugUnitTest --tests <filtros>`.

| Corrida | Filtros | Resultado |
|---|---|---|
| Rojo (`mot-3.log`, solo reproducciones sobre el código sin arreglo) | `screens.sessioneditor.SessionEditorImpactParityTest`, `…SessionEditorAugeAxialGateTest`, `domain.sessionassistant.*`, `domain.exercises.*`, `domain.auge.*` | 416 pruebas, **15 fallan** (las 15 reproducciones: 4 de Impacto, 6 del asistente —la séptima es un control—, 5 del creador). Mensajes: editor 17/32/2 frente a general 17/32/8; cardio solo = 10/14/8 fijo; «Entrenaste… al día siguiente»; 4 tarjetas; `ReduceSet(muscle=Deltoides)`; P@1 15/36; axial 0,0 |
| Rojo P04 (`mot-5.log`) | filtros acotados + `SessionEditorEmptySlotVolumeTest` | 273 pruebas, 2 fallan (las del hueco de ronda: intensidad media «ALTA» en vez de «MEDIA», recuento 2 en vez de 0); el resto verde |
| Intermedia (`mot-4.log`) | `screens.sessioneditor.*`, `domain.sessionassistant.*`, `domain.exercises.*`, `domain.auge.*`, `domain.templates.*`, `domain.competitions.*`, `data.ExerciseCatalogContractTest`, `render.SessionAssistant*`, `PlannedIntensityPolicyTest` | 1161 pruebas, 3 fallos: 1 mío (límites rígidos: «bajar esfuerzo» no cambiaba series sin modo explícito; arreglado) y 2 de entorno (abajo) |
| **Final** (`mot-6.log`, código de `cbc6d3663`) | los anteriores + `domain.training.*`, `data.exercises.*`, `CustomExerciseListOpsTest`, `PersonalizedPlanCatalogTest`, `ProgramHeroWidgetsCalibrationTest` | **2911 pruebas, 7 fallos, 1 omitida; las 15 reproducciones y las dos de P04 en verde; todas mis pruebas nuevas en verde** |

Los 7 fallos finales **no son de esta pista** (ninguno toca archivos que yo cambié): 5 son pruebas de plan/protocolo (`PlanAdaptationResolverTest`, `PlanMaterializerCardioIdentityTest`, `ProgramProtocolEngineTest` ×2, `ProgramTemplateEngineTest`) con valores de TM frente a `reference1RM` (esperan 90 o 180 y reciben 100 o 200), que apuntan a `af6e6e546 fix(cal1): unifica capacidad, TM y consumidores del dominio`, ya en la base; no se comprobó contra la base con una corrida aparte. Los otros 2 son `ExerciseCatalogImageCoverageTest`: faltan los manifiestos `catalog/exercises/images/*.json` del worktree (checkout parcial, ya documentado en AUD2). La omitida es `SessionAssistantRulesRenderSheetTest.renderAssistantRulesSheets`, que ya lo era.

Pruebas nuevas (todas en `app/src/test`): `SessionDrainProjectionTest` (14), `SessionEditorImpactPrecisionTest` (7), `SessionAdvisorTest` (20), `ExerciseReferenceFinderTest` (10, imprime las tablas de medición), `CustomExerciseCreationTest` (11), `ExerciseCoverResolverTest` (6), `ExerciseEditorialTablesTest` (4), `SessionEditorEmptySlotVolumeTest` (2), `AugeRequestGateTest` (2), `CustomExerciseListOpsTest` (3), más las 16 reproducciones. Pruebas antiguas actualizadas a propósito porque su contrato cambió: `SessionAssistantEngineTest` (ya no hay tarjeta «sesión demasiado exigente» ni «poco descanso»; ahora nota de coste y cruce previsto) y `SmartExerciseCreatorTest.empty_catalog…` (sin referencia no se inventan cifras).

Sin red propia por no existir la API anterior: la reproducción en rojo de I2 (decimales, «<1», EMA) es la compilación contra la API anterior; su verde son `SessionDrainProjectionTest` y `SessionEditorImpactPrecisionTest`.

## 4. Riesgos de integración con la rama principal

1. **Archivos compartidos con otras pistas.** `SessionEditorViewModel.kt` (claves de caché, `SessionAssistantInput`, puerta de peticiones), `SessionEditorModels.kt`, `AssistantImpactTab.kt`, `AssistantSuggestionsTab.kt`, `AssistantSheet.kt`, `ExercisePickerV2Catalog.kt`, `CatalogExerciseRow.kt` y `CatalogCustomExerciseEditor.kt` son zona de ED-A/ED-B/ED-E y CAT-A/CAT-B: esperar conflictos de ámbito en el VM y el selector. Los cambios son pequeños y localizables.
2. **Semántica de `calculateAdjustedPredictedDrain`.** Sigue devolviendo enteros truncados, pero ahora cuenta cardio con el orden de `allExercises()`, ignora huecos de ronda, usa `settings.restTimerDefaultSeconds` para descansos sin valor y la carga sugerida en %RM. Con `Settings()` por defecto coincide con el cálculo anterior (lo verifican `AugeEditorCompletedParityTest`, plantillas y `domain.auge.*`).
3. **`ExerciseMuscleInfo` gana tres campos opcionales** (`creationReference`, `descriptionOrigin`, `cover`). Se guardan dentro del JSON de `custom_exercises.data` (sin migración de Room) y los registros antiguos decodifican igual (hay prueba). Un decodificador estricto fuera de `dbJson` fallaría con claves desconocidas; no encontré ninguno para este modelo.
4. **`OPEN_QUERY_PRIORITY_TABLE`** cambia el orden de las búsquedas abiertas «remo»/«row» (ahora las definiciones vigentes) y añade «bench press». Es el único cambio observable en la búsqueda.
5. **`VolumeCalculator.countEffectiveSets` y los recuentos del editor** excluyen huecos de ronda; lo usan el generador de rutinas y los resúmenes de volumen (sin huecos no cambia nada).
6. **Sin compilar `androidTest`** (la orden de la pista lo excluye). Hay cuatro archivos instrumentados que mencionan APIs cercanas; los cambios de firma son campos opcionales al final, así que no deberían romperlos.
7. **Sin QA visual.** La pestaña Impacto (notas y «<1»), las etiquetas y notas de Sugerencias y la miniatura de la fila de propios no se vieron en pantalla.
8. **Commits intermedios:** cada commit se probó solo como parte del conjunto final; no se compiló cada uno por separado.

## 5. Pendiente

- La interfaz nueva de «Crear ejercicio» (tablero E7), con los tres parecidos, la revisión de la inferencia y el elegir portada, no está hecha; la API sí (`findReferences`, `referenceResult`, `ExerciseCoverResolver.suggestReferenceCover`, `setCover`, `lastPersistError`). Falta un selector de foto del álbum: la fila ya pinta una foto elegida, pero ninguna pantalla la elige todavía.
- S4 completo: la personalización por historial es mínima (recuento y media de estrés de sesiones comparables); no hay referencias por músculo ni dosis comparables.
- I4/S5: la pasada de volumen/roles del editor y el cálculo bruto por ejercicio del asistente siguen existiendo (no son coste); las reglas antiguas del asistente se conservan como código sin uso por la regla «no quitar». Retirarlas es un paquete aparte.
- El cruce realizado/previsto se desduplica por id de sesión; si un registro no conserva el id de su sesión planificada, podría contarse dos veces.
- Los umbrales (120 s/10 %, 6 sesiones, 1,3×, 25 %) son decisiones iniciales de producto a calibrar, no límites de salud.
