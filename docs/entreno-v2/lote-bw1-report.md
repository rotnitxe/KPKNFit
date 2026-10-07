# Lote BW-1 · altas de ejercicios de peso corporal

Agente D4 · rama `ent/d4` (worktree `C:\kw\d4`) · 2026-10-07. **Propuesto para el OK del usuario:** nada se aterrizó en el
árbol principal ni se empujó. `reviewStatus: APPROVED` en las 18 configuraciones es el estado propuesto para el corte (como en
`WIZARD_BODYWEIGHT_20260926.md`), no una aprobación.

## 1. Qué se añade

- **9 definiciones nuevas, ya `CURATED` (10 configuraciones):** `pike_push_up` (`flat` y `feet_elevated`), `negative_pull_up`,
  `diamond_push_up`, `archer_push_up`, `hollow_body_hold`, `dead_bug`, `side_plank`, `bird_dog` y `wall_sit`. Entran en familias
  que ya existían; no hay familia nueva.
- **8 configuraciones `__bodyweight` en definiciones ya `CURATED`:** `forward_lunge`, `walking_lunge`, `step_up`,
  `bulgarian_split_squat`, `sumo_squat`, `sissy_squat`, `romanian_deadlift` (`unilateral`, a una pierna) y `good_morning`.
- Cada una lleva ficha completa (descripciones propias, anatomía con rol razonado por músculo y articulación, técnica, descripción
  visual y de 6 a 10 fuentes PubMed/StatPearls verificadas con `catalog_v2_sources.py verify --definitions`) y el lint pasa sin
  avisos. Además, tres revisores limpios (solo lectura, sin la historia de la redacción) leyeron las 17 definiciones contra sus
  fuentes: ningún bloqueante, y se aplicaron sus correcciones (sección 6).
- Catálogo: 96 familias; **206 / 521 / 413 → 215 / 539 / 430** (definiciones / configuraciones / pares definición×implemento);
  `CURATED` 124 → 133, `LEGACY` 82 sin cambio; revisión `v2-approved-2026-09-29-a` y ontología sin cambio; SHA
  `c67eeb8f…` → `c30a5c2e61b9d2f7f7bae3d517104b93f5851e772339872badc025c6bc67f831`.
- Aterrizaje de prueba en el worktree (`catalog_v2_land.py --definitions` con las 17 definiciones): gate estricto READY, auditoría
  de calidad 0 errores / 0 avisos, `compile --check` y pines movidos. `pytest scripts/tests backend/tests/test_exercises_catalog_v2.py`:
  214 aprobadas y 162 subtests, 0 fallos.
- **No cambia ningún perfil preexistente:** de las 60 configuraciones de las 17 definiciones, 42 ya existían y se compararon con el
  catálogo base (sección 8): 0 cambios de rol muscular, articular o de patrón, y ninguna configuración existente cambia de texto.
  Sí se retocó, solo donde daba por hecha una carga en las manos, la descripción pública de `walking_lunge`, `romanian_deadlift` y
  `good_morning`, y texto interno (técnica, base visual y QA del brief de imagen) de las ocho definiciones con una configuración
  sin carga nueva.

## 2. Tabla: patrón del generador → ids nuevos recomendados

Los patrones son los de `RoutinePattern` de D1. «Tramo» es la escalera de `BodyweightLadders` donde encajan (sugerencia: la decide el
paquete del generador). La dificultad técnica va entre paréntesis; D1 no arranca a un novato por encima de 5,2.

| Patrón del generador | Huecos de la matriz (solo cuerpo / parque) | Ids nuevos | Material | Tramo sugerido |
|---|---|---|---|---|
| `VERTICAL_PUSH` Empuje vertical | «siempre» en ambos perfiles | `pike_push_up__flat` (4,8), `pike_push_up__feet_elevated` (5,0) | plana: ninguno; elevada: `support` | nueva escalera: plana, luego pies elevados |
| `VERTICAL_PULL` Tracción vertical | solo cuerpo: «siempre»; parque ya tenía dominadas | `negative_pull_up__default` (4,0) | `pull_up_bar` | `pullUp.easy` (antes de `pull_up__*`) |
| `HORIZONTAL_PUSH` Empuje horizontal | cubierto | `diamond_push_up__default` (6,0), `archer_push_up__default` (6,5) | ninguno | `pushUp.hard`, tras `push_up__flat` |
| `TRICEPS` Tríceps | cubierto por las flexiones | `diamond_push_up__default` (primer PRIMARY: tríceps) | ninguno | complemento de la anterior |
| `SINGLE_LEG` Zancada y una pierna | cubierto por `reverse_lunge__bodyweight` | `forward_lunge__bodyweight`, `walking_lunge__bodyweight`, `step_up__bodyweight`, `bulgarian_split_squat__bodyweight` (todas 4,2) | step-up y búlgara: `support`; las otras: ninguno | `singleLeg.easy`/`standard` (la búlgara, `standard`) |
| `HINGE` Bisagra de cadera | nota 672: «sin carga la bisagra son puentes de glúteos» | `good_morning__bilateral__bodyweight`, `romanian_deadlift__unilateral__bodyweight` (4,2) | ninguno | `hinge.standard` y `hinge.hard` |
| `SQUAT` Sentadilla | una sola configuración sin carga | `sumo_squat__bodyweight` (4,2), `wall_sit__default` (3,5, isométrica) | ninguno | `squat.standard`; la pared, por tiempo |
| `QUAD_ISOLATION` Cuádriceps aislado | «a veces» | `sissy_squat__bodyweight` (5,2), `wall_sit__default` | ninguno | `squat.hard` (la sissy no es para novatos) |
| `CORE_STABILITY` Core: flexión y estabilidad | cubierto | `dead_bug__default` (4,0), `hollow_body_hold__default` (4,0, isométrica) | ninguno | `core.easy` y `core.standard` |
| `CORE_ROTATION` Core: rotación y lateral | «a veces» en ambos | `side_plank__default` (4,0, isométrica; flexión lateral), `bird_dog__default` (3,5; antirrotación) | ninguno | `core` por tiempo/series |
| `BACK_EXTENSION` Extensión de espalda | cubierto por `back_superman_suelo__default` | `bird_dog__default` (primer PRIMARY: erectores) | ninguno | alternativa más suave al superman |
| `BICEPS` Bíceps | «siempre» | ninguno nuevo; `biceps_curl_trx__supinated` pasa a ser alcanzable con el símbolo «Anillas o TRX» | `trx` | nueva escalera con anillas |
| `HORIZONTAL_PULL` Tracción horizontal | «siempre» en solo cuerpo | **ninguno** (ver sección 4) | — | `back_remo_invertido__default` ya existe |
| `SHOULDER_LATERAL`, `REAR_DELT`, `HAMSTRING_CURL` | «siempre» o «a veces» | **ninguno**: no hay ejercicio honesto de peso corporal sin material | — | el límite honesto del generador sigue en pie |

Los tres isométricos (`wall_sit`, `hollow_body_hold`, `side_plank`) se prescriben por tiempo, como `core_plancha__default`.
De las lagunas que D1 anota en `BodyweightLadders`, este lote cierra el empuje vertical (pica) y deja una regresión de dominada
(la negativa); siguen abiertos el remo en anillas, el puente a una pierna sin carga y el salto al cajón.

## 3. Soportes y cableado Kotlin

- Ninguna alta necesita un soporte nuevo (la regla STOP no se activó). `supportRequirementsFor`: `negative_pull_up__*` pide
  `pull_up_bar`; `pike_push_up__feet_elevated`, `step_up__bodyweight` y `bulgarian_split_squat__bodyweight` piden `support`
  («Apoyo elevado»: banco, cajón, escalón o sofá). Como su implemento es `bodyweight` (siempre acreditado), sin ese requisito se
  ofrecerían a quien no tiene dónde apoyarse. Las otras 14 altas no piden nada.
- Resolutor: tras fusionar con los paquetes E y E2 solo queda la vía de E (`SYMBOL_EQUIPMENT_KEYS` en `resolveWithAvailability`: las
  anillas acreditan `trx` y `rings`, el cajón `plyo_box`, con la categoría confirmada y la llave `PRESENT`; el generador y el
  planificador deciden con el mismo `ConfigurationEquipmentFilter`). De mi cableado queda una línea: la entrada `plyo_box` acredita
  además `support` (un apoyo elevado). Las anillas no lo acreditan. Antes de E el motor ignoraba esas llaves y las 3
  configuraciones TRX (`biceps_curl_trx__supinated`, `quads_sentadilla_pistola_asistida_trx__default`, `triceps_extension__default`)
  no se alcanzaban con ningún material; hoy las abren las anillas.
- La cabecera de `EquipmentSymbols.kt` ya la había corregido E; solo se añadió que el cajón es además un apoyo elevado.
- No se tocó `NativeWorkoutProgressionRuntime`, cuya escalera en vivo solo conoce la flexión (rodillas → estándar → pies
  elevados). Que la pica plana pase a la elevada, o el diamante y el arquero entren tras la flexión estándar, es una decisión de
  entrenador y producto aparte; el lote deja los ids y sus soportes listos.

## 4. Qué se descartó y por qué

- **Remo invertido accesible:** `back_remo_invertido__default` ya modela el ejercicio con los ángulos del cuerpo y pide
  `low_bar_support`. La variante «bajo una mesa robusta» necesita un soporte que el vocabulario y el paso de material no pueden
  acreditar (regla STOP: se reporta, no se inventa) y «pies elevados» es la versión difícil, no la accesible. Si se quiere cerrar la
  tracción horizontal sin material hace falta decidir un soporte nuevo (p. ej. «Mesa o barra baja») o un remo en anillas.
- **Seis conversiones que no se hicieron:** `glutes_puente_gluteos` (unilateral), `calf_raise` (unilateral), `hip_thrust`,
  `hip_abduction` (tumbado de lado), `glutes_patada_gluteo` y `glutes_patada_gluteo_lateral`. Sus definiciones son `LEGACY` (solo
  copy heredado) y una ficha `CURATED` exige técnica, anatomía, visual y fuentes de la definición completa, con anatomía que
  sustituiría a la aprobada de 3 a 12 configuraciones existentes por definición. Eso es el lote 7 de la re-curaduría («Glúteo y
  pierna baja», con su criterio de glúteo medio y su OK propio): añadirlas aquí por una vía parcial daría fichas incompletas, que es
  justo lo que el encargo prohíbe. Recomendación: incluirlas en el lote 7 (el puente y la pantorrilla unilaterales son las dos que
  la escalera de D1 echa en falta).
- No se tocó la búsqueda de `core_crunch_suelo_peso_corporal`, que conserva el término heredado «dead bug / bicho muerto» y
  solapa con la definición nueva `dead_bug`: queda para el lote de core.

## 5. Herencia de `efc/cnc/ssc/ttc` y dificultad (`fieldSemanticsGap`)

Los cuatro campos se heredan sin cambio del perfil aprobado más cercano porque su significado no está documentado en el
repositorio; `technicalDifficulty` es una **puntuación editorial modelada**, no una medición, colocada entre anclas aprobadas.
`axialLoadFactor = 0.0` es una elección de modelo (cero carga axial **externa**).

| Altas | `efc / cnc / ssc / ttc` heredados de | `technicalDifficulty` (anclas) |
|---|---|---|
| `dead_bug`, `hollow_body_hold`, `side_plank`, `bird_dog` | `core_plancha__default`, `core_elevacion_piernas__default`: 2,0 / 1,5 / 0,2 / 1,5 | 4,0 / 4,0 / 4,0 / 3,5: entre la plancha (3,5) y el dragon flag (4,5) |
| `wall_sit` | `quads_sentadilla_sin_carga__default`: 2,8 / 2,2 / 0,5 / 2,0 | 3,5: por debajo de la sentadilla sin carga (4,0), estática y sin coordinación |
| `pike_push_up` | `military_press__barbell` (familia de empuje vertical): 2,5 / 1,8 / 0,3 / 1,6 | 4,8 plana y 5,0 elevada: las de `push_up__feet_elevated` y `push_up__hands_elevated`. El catálogo no ordena la dificultad por dureza (`push_up__flat` está en 6,0): la pica queda bajo el tope de 5,2 de los novatos de D1 aunque carga más las manos; no debería ofrecerse a novatos ni la elevada sin la plana |
| `diamond_push_up`, `archer_push_up` | `push_up__flat`: 3,5 / 3,0 / 0,35 / 1,8 | 6,0 (igual que `push_up__flat`) y 6,5 (entre 6,0 y el 6,8 de Spoto) |
| `negative_pull_up` | `pull_up__pronated__medium`: 2,5 / 2,0 / 0,4 / 1,8 | 4,0, la de la dominada |
| zancadas, step-up, búlgara, sumo, sissy | la configuración hermana con mancuernas (idénticas a `reverse_lunge__bodyweight`): 2,8 / 2,2 / 0,5 / 2,0 | 4,2 y 5,2 (sissy), las de sus hermanas |
| rumano a una pierna y buenos días sin carga | la hermana con mancuernas o barra, con `ssc` 1,0 → 0,5 (el test de coherencia prohíbe `axial = 0` con `ssc ≥ 0,9`) | 4,2, la de sus hermanas |

## 6. Riesgos

- **Evidencia indirecta.** La pica, la flexión arquero, la sentadilla en pared, la dominada negativa, los buenos días y el rumano
  sin carga no se apoyan en electromiografía de ese mismo ejercicio: los roles se extrapolan de ejercicios vecinos (press militar y
  flexiones, sentadilla isométrica, dominada completa, versiones cargadas) y el `why` de cada músculo lo dice. Dead bug, bird dog y
  plancha lateral sí tienen EMG propio (Souza 2001, García 2012, Ekstrom 2007, McGill 2009, Kim 2016); el hollow body se apoya en
  el EMG del descenso de piernas.
- **Sin imágenes.** Los 17 pares definición × implemento nuevos no tienen PNG (las dos configuraciones de la pica comparten
  par); `catalog_v2_visual_brief.py` arma el brief de las 17 definiciones, con `promptCore` y QA listos para la cola de imágenes.
- **`support` es genérico.** Lo acreditan el banco, el cajón, el rack y las paralelas (la semántica que ya tenía
  `push_up__feet_elevated`): quien solo marcó paralelas recibiría la búlgara, que en la práctica pide banco o cajón. Quien tiene
  sofá pero marcó «solo peso corporal» no recibe la pica elevada, el step-up ni la búlgara (conservador a propósito). Un requisito
  más fino o un símbolo «Silla o sofá» sería una decisión de producto aparte. Además, `step_up__dumbbells` y
  `bulgarian_split_squat__dumbbells` siguen sin pedir `support` (preexistente): no se tocaron para no mover cifras de planes.
- **Movilidad.** `MobilityExerciseCatalog` ya trae `mob_wall_sit` (sentadilla isométrica en pared) y `mob_dead_bug_reach`: es otro
  registro, con prefijo `mob_`, sin colisión de ids; se anota por si algún buscador los mezcla con `wall_sit` y `dead_bug`.
- **Contrato compartido.** El resolutor es de todos los planes. Tras la fusión con E y E2 el cableado de BW-1 en él es un solo
  token (`support` en la entrada `plyo_box` de `SYMBOL_EQUIPMENT_KEYS`), con pruebas propias; el resto es de E.
- **Revisión limpia aplicada.** Tres revisores sin contexto (solo lectura) leyeron las 17 definiciones contra sus fuentes: ningún
  bloqueante. Se corrigieron, entre otros, la dominada negativa (las acciones del hombro estaban invertidas: en la bajada el dorsal
  frena la flexión y la abducción), el diamante (ya no se afirma que el tríceps sea «el que más trabaja»: lo sostiene un solo estudio
  que no normaliza a una contracción máxima), la sentadilla en pared (el EMG citado es de una sentadilla trasera isométrica con
  barra: ahora se dice y se marca la extrapolación), el arquero (se quitó un «casi no usa el brazo estirado» sin fuente), la
  coherencia sin carga de la base visual, la QA y las cues de las ocho definiciones aprobadas con una configuración nueva, y las
  cámaras del diamante (de perfil no se ve el rombo) y del arquero (desde los pies era una vista trasera).
- **Roles que decidió la revisión y quedan para el OK.** `dead_bug`: flexores de cadera SECUNDARIOS (su `why` decía que frenan el
  descenso). `side_plank`: glúteo medio PRINCIPAL junto al abdomen (Collings 2023 lo sitúa en el primer grupo de fuerza con el
  peso corporal, 338 a 483 N, y Ekstrom 2007 lo señala con el oblicuo externo). `bird_dog`: primer PRIMARY erectores (EMG de Souza
  y García), que `CompositionTaxonomy` cuenta como ERECTORS aunque el patrón es CORE. `diamond_push_up`: primer PRIMARY tríceps
  (fija `primaryMuscles.first()`). `wall_sit`: glúteo mayor SECUNDARIO frente a PRIMARY en `quads_sentadilla_sin_carga` (isométrica,
  tronco apoyado y sin recorrido). `negative_pull_up` lista deltoides y escápula SECUNDARIOS, que la dominada aprobada no tiene
  (sin deltoides; escápula PRINCIPAL): los `why` lo explican, y la que puede estar incompleta es la dominada (Rabello 2024 apoya el
  deltoides en las dominadas).
- **Dificultades y herencias modeladas**, no medidas (sección 5). `reviewStatus: APPROVED` es propuesto.

## 7. Pruebas que cambian de cifras

Pines movidos de 206 / 521 y del SHA `c67eeb8f…` a 215 / 539 y `c30a5c2e…`: `AprendeCatalogAuditTest.kt` (incluido el índice inverso
de patrones, que suma 539), `ExerciseCatalogContractTest.kt`, `ExerciseCatalogAuditTest.kt` (el rumano pasa de 8 a 9
configuraciones), `scripts/tests/test_catalog_v2_show.py` (206 → 215 filas) y `backend/tests/test_exercises_catalog_v2.py` (SHA y
`romanian_deadlift` de 8 a 9 configuraciones). Son recuentos del catálogo, no cifras de planes. Prueba nueva: `BodyweightLotEquipmentTest`
(11 pruebas: soportes de las altas y alcanzabilidad con el filtro único; el cajón es además un apoyo elevado y las anillas no).

**Tests de planes: no cambia ninguna cifra, no se re-baselinó nada.** Pasan sin tocar sus pines `PlanGenerationCoverageT006Test` (5),
`T006PersistenceAndUseIntegrationTest` (7), `PlanCoverageContractTest` (2), `SetupExecutableAvailabilityMatrixTest` (27),
`NativeProfileRecipeAndFitterTest`, `PersonalizedPlanCatalogTest`, `EffectiveEquipmentContractTest`, `FixedRecipeEquipmentCompatibilityTest`,
`PlanRejectionPresenterTest` y `SetupWizardActivationGateTest`. Ninguna receta fija ni plantilla del código cita los ids nuevos: entran
solo por la selección nativa.

**Fusión con `feat/wizard-entreno-v2` (paquetes E y E2).** Kotlin Base, una corrida de `:app:testBaseDebugUnitTest` por el envoltorio de
ranuras, filtrada por `domain.training.*` (con `generator.*` y `split.*`), `domain.onboarding.*`, `domain.exercises.*`,
`domain.templates.*`, `data.protocols.*`, `data.programs.*`, `data.exercises.*`, `data.onboarding.*`, `EquipmentAvailabilityTest`,
`ExerciseCatalogContractTest`, `SetupExecutable*`, los `SetupWizard*` que usan el catálogo, `SetupFixedWarmupOptionsTest`,
`PlanInfoModelTest` y `ExerciseCatalogV2*` del editor: **221 suites y 2278 pruebas, 0 fallos, 0 errores, 0 omitidas.** Entre ellas,
`domain.training.generator.*` (7 suites, 49 pruebas: barridos, comportamiento, reservas y `GeneratorEquipmentTest`),
`EquipmentReachTest` (10), `ConfigurationEquipmentFilterTest` (10), `EffectiveEquipmentResolverContractTest` (21),
`EquipmentSymbolsTest` (29) y `ExerciseTraitResolverTest` (11, recorre las 539 configuraciones). Las 18 altas no rompen ningún
barrido ni conteo del generador: la matriz que regenera `RoutineMatrixReportTest` es idéntica, línea a línea, a `matrix-d1.txt` de E
(las reservas citan ids concretos y ninguno es nuevo). `EquipmentReachTest` no fija ninguna cifra del tamaño del catálogo (solo las de
máquinas, discos, hexagonal/T, GHD y rueda, que no cambian): lo que cambia es el informe que escribe, en la sección 9.

**Python (con la fusión):** `catalog_v2_gate --strict` READY, `compile --check` y pines ok, y
`pytest scripts/tests backend/tests/test_exercises_catalog_v2.py`: 214 aprobadas y 162 subtests, 0 fallos.

## 8. Informe de lote (formato de `catalog_v2_lote_report.py`)

Generado con las funciones del script (`configuration_delta`, `budget_ledger`, `render_markdown`) contra el catálogo **base** de la
rama (`d6ea70290`): el lote ya está aterrizado en el catálogo compilado de la rama, y el script, tal cual, solo sabe comparar
configuraciones que existían, así que las 18 altas se listan aparte en la última tabla.

### Informe de lote: delta anatómico

#### Escalar antes de aplicar (principal cambiado en configuraciones protegidas)

Ninguna.

#### Cambios de rol muscular

Sin cambios.

#### Cambios articulares

Sin cambios.

#### Topes de palabras (léxico): definiciones CURATED que usan cada palabra

| tope | usadas | máximo | en este lote |
|---|---|---|---|
| budget.claridad | 1 | 3 |  |
| budget.referencia | 1 | 8 |  |
| budget.equilibrio | 2 | 15 | romanian_deadlift, walking_lunge |
| budget.concentrar | 1 | 15 |  |
| budget.repartir | 2 | 5 |  |
| budget.guiado | 1 | 25 |  |
| budget.progresion | 1 | 20 | pike_push_up |
| budget.medir | 2 | 8 |  |
| budget.completo | 1 | 10 | negative_pull_up |
| budget.distinto | 2 | 8 |  |

#### Configuraciones comparadas

Configuraciones que ya existian en el catalogo base y se compararon con `configuration_delta`: 42 (definiciones del lote: 17). Altas sin "antes": 18.

#### Altas del lote (sin comparacion: no existian)

| configuracion | definicion | dominante | PRIMARY | patron | implemento | dificultad |
|---|---|---|---|---|---|---|
| `hollow_body_hold__default` | hollow_body_hold | abdominals | abdominals | anti_extension_isometric | bodyweight | 4.0 |
| `dead_bug__default` | dead_bug | abdominals | abdominals | anti_extension_pelvic_control | bodyweight | 4.0 |
| `bird_dog__default` | bird_dog | erector_spinae | erector_spinae, abdominals | anti_rotation_trunk | bodyweight | 3.5 |
| `side_plank__default` | side_plank | abdominals | abdominals, gluteus_medius | lateral_trunk_flexion | bodyweight | 4.0 |
| `good_morning__bilateral__bodyweight` | good_morning | hamstrings | hamstrings, gluteus_maximus | hip_hinge | bodyweight | 4.2 |
| `romanian_deadlift__unilateral__bodyweight` | romanian_deadlift | hamstrings | hamstrings, gluteus_maximus | romanian_deadlift | bodyweight | 4.2 |
| `forward_lunge__bodyweight` | forward_lunge | quadriceps | quadriceps, gluteus_maximus | unilateral_knee_dominant | bodyweight | 4.2 |
| `sumo_squat__bodyweight` | sumo_squat | quadriceps | quadriceps, gluteus_maximus | knee_hip_dominant | bodyweight | 4.2 |
| `wall_sit__default` | wall_sit | quadriceps | quadriceps | knee_dominant | bodyweight | 3.5 |
| `sissy_squat__bodyweight` | sissy_squat | quadriceps | quadriceps | knee_dominant_lengthened | bodyweight | 5.2 |
| `step_up__bodyweight` | step_up | quadriceps | quadriceps, gluteus_maximus | unilateral_knee_dominant | bodyweight | 4.2 |
| `walking_lunge__bodyweight` | walking_lunge | quadriceps | quadriceps, gluteus_maximus | unilateral_knee_dominant | bodyweight | 4.2 |
| `bulgarian_split_squat__bodyweight` | bulgarian_split_squat | quadriceps | quadriceps, gluteus_maximus | unilateral_knee_dominant | bodyweight | 4.2 |
| `diamond_push_up__default` | diamond_push_up | triceps | triceps, pectoralis | horizontal_push | bodyweight | 6.0 |
| `archer_push_up__default` | archer_push_up | pectoralis | pectoralis | horizontal_push | bodyweight | 6.5 |
| `negative_pull_up__default` | negative_pull_up | latissimus_dorsi | latissimus_dorsi | vertical_pull | bodyweight | 4.0 |
| `pike_push_up__flat` | pike_push_up | deltoid | deltoid | vertical_push | bodyweight | 4.8 |
| `pike_push_up__feet_elevated` | pike_push_up | deltoid | deltoid | vertical_push | bodyweight | 5.0 |

## 9. Alcanzabilidad del material: antes → después

Medida con las reglas de `EquipmentReachTest` (resolutor único y filtro único de E y E2; «alcanzable» = el planificador y el generador la
aprueban con el equipo que acreditan los símbolos). **Antes** = el catálogo base de 521 configuraciones con el resolutor de E, que
reproduce sus cifras publicadas (8 inalcanzables de 521). **Después** = el catálogo de 539 con la fusión; coincide fila a fila con lo que
escribe `EquipmentReachTest` en `build/reports/equipment-reach/reach.txt` (51 filas comparadas, 0 diferencias).

Qué sube y por qué: las 14 altas sin soporte suben todos los materiales (incluido «solo cuerpo»); cualquier símbolo de soporte (rack,
banco, paralelas, cajón) suma las 3 que piden un apoyo elevado; la barra de dominadas suma la dominada negativa; el cajón suma además
`push_up__feet_elevated`, la única configuración existente que cambia de alcance (antes solo la abría la categoría de soportes). Las
anillas siguen abriendo las mismas 3 configuraciones TRX. Ninguna configuración deja de ser alcanzable, y las 18 altas lo son desde
algún símbolo y lugar: los inalcanzables siguen siendo 8 (los cuatro implementos raros y el curl nórdico), ahora de 539.

**Materiales de referencia**

| material | antes | después |
|---|---|---|
| solo cuerpo | 20 | 34 |
| parque (semilla) | 35 | 53 |
| casa con mancuernas y banco | 110 | 127 |
| casa con anillas y cajón | 23 | 41 |
| gimnasio completo | 510 | 528 |
| gimnasio sin rack | 495 | 513 |

**Lugares** (solo cuerpo / con la semilla del lugar / con todo lo que ofrece)

| lugar | antes | después |
|---|---|---|
| Gimnasio | 20 / 510 / 513 | 34 / 528 / 531 |
| En casa | 20 / 20 / 487 | 34 / 34 / 505 |
| En espacios públicos | 20 / 35 / 179 | 34 / 53 / 197 |
| todos los lugares y todos los símbolos | 20 / – / 513 | 34 / – / 531 |

**Símbolos** (solo: lo que abre por sí solo sobre solo cuerpo / imprescindible: lo que se pierde al quitarlo). Solo las filas que cambian;
el resto (barra y discos, mancuernas, kettlebell, poleas, máquinas, Smith, bandas, balón, cuerda de saltar y cardio) queda igual.

| símbolo | lugar | antes | después |
|---|---|---|---|
| Rack | Gimnasio | 3 / 15 | 6 / 15 |
| Rack | En casa | 1 / 13 | 4 / 13 |
| Banco | Gimnasio | 5 / 26 | 8 / 26 |
| Banco | En casa | 2 / 24 | 5 / 24 |
| Banco | En espacios públicos | 2 / 10 | 5 / 10 |
| Barra de dominadas | Gimnasio | 11 / 13 | 12 / 14 |
| Barra de dominadas | En casa | 11 / 13 | 12 / 14 |
| Barra de dominadas | En espacios públicos | 13 / 15 | 14 / 16 |
| Paralelas | Gimnasio, casa y espacios públicos | 2 / 1 | 5 / 1 |
| Anillas o TRX | En espacios públicos | 4 / 3 | 7 / 3 |
| Cajón o step | Gimnasio | 0 / 0 | 4 / 0 |
| Cajón o step | En casa | 0 / 0 | 4 / 0 |
| Cajón o step | En espacios públicos | 1 / 0 | 4 / 0 |

Una rareza que BW-1 no crea pero hereda: en espacios públicos la llave del rack no se escribe (queda sin responder), así que cualquier
símbolo de soporte, también las anillas solas, acredita `support` por la categoría; por eso las anillas suman ahí las 3 altas con apoyo
elevado (4 → 7) y el cajón ya abría `push_up__feet_elevated` en el parque (1 → 4). En casa y en el gimnasio las anillas no acreditan
`support`. Si se quiere que en el parque un símbolo de soporte sin banco ni cajón no ofrezca esos ejercicios, hay que escribir las
llaves de soporte ausentes también en ese lugar: es una decisión de E, no de este lote.
