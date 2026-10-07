# Lote OL-1 · altas de levantamientos olímpicos y acarreos de fuerza

Agente D6 · rama `ent/d6` (worktree `C:\kw\d6`; parte de `ent/d4` y está fusionada con `feat/wizard-entreno-v2`: BW-1 y el
resolutor único de material de los paquetes E y E2) · 2026-10-07. **Propuesto para el OK del usuario:** nada se aterrizó en el árbol
principal ni se empujó. `reviewStatus: APPROVED` en las 14 configuraciones es el estado propuesto para el corte (como en
`lote-bw1-report.md`), no una aprobación.

Commits de la rama, en orden (más la fusión `483b2d0ac` de `feat/wizard-entreno-v2` y el de esta documentación): `536d6595a` fichas
CURATED · `27e688cf3` estructura en las familias fuente · `e633c3ddd` catálogo compilado y pines (SHA `c815f8e0…`) · `c54071c21` correcciones de
la revisión limpia · `8540ccc62` recompilado y pines (SHA `8e709374…`) · `afa7fddfb` rack de enviones y sentadilla de arranque ·
`fba4acdb2` `OlympicLotEquipmentTest` · `66ee8c432` dos retoques de redacción.

## 1. Qué se añade

- **13 definiciones nuevas, ya `CURATED` (14 configuraciones)**, todas en familias que ya existían: las variantes olímpicas no son un
  implemento ni una variante de agarre de ningún ejercicio del catálogo, así que cada una merece definición propia; en cambio cada
  familia ya tenía hermanas, y no se crea ninguna familia nueva.

| Familia (hermanas que ya tiene) | Definiciones nuevas | Configuraciones |
|---|---|---|
| `lower_hip_hinge_explosive` (swing de kettlebell) | `power_clean` (Cargada de Potencia), `hang_power_clean` (Cargada de Potencia desde Colgado), `squat_clean` (Cargada Completa), `power_snatch` (Arranque de Potencia), `hang_power_snatch` (Arranque de Potencia desde Colgado), `squat_snatch` (Arranque Completo), `clean_pull` (Tirón de Cargada), `snatch_pull` (Tirón de Arranque) | 8, todas `__barbell` |
| `upper_vertical_push` (push press) | `push_jerk` (Envión de Empuje), `split_jerk` (Envión de Tijera) | 2, `__barbell` |
| `lower_knee_dominant` (sentadillas frontal, trasera y Zercher) | `overhead_squat` (Sentadilla de Arranque) | 1, `__barbell` |
| `lower_isometric_grip` (paseo del granjero) | `suitcase_carry` (Paseo del Maletín) | 2, `__dumbbells` y `__kettlebell` |
| `lower_spinal_extension` (hiperextensión Zercher) | `zercher_carry` (Paseo Zercher) | 1, `__barbell` |

- Cada una lleva ficha completa: descripciones propias, anatomía con rol razonado por músculo y articulación, técnica, descripción
  visual y de 9 a 28 fuentes por definición (de 3 a 11 estudios en PubMed y el resto capítulos de anatomía de StatPearls),
  verificadas con `catalog_v2_sources.py verify --definitions` (28 URLs nuevas en `sources_verified.json`). Las `executionCues` son
  de tres o cuatro frases cortas que el relator puede leer en voz alta y el lint pasa sin avisos. Tres revisores limpios (solo
  lectura, sin la historia de la redacción) leyeron las 13 definiciones contra sus fuentes y se aplicaron sus correcciones (sección 5).
- Catálogo: 96 familias; **215 / 539 / 430 → 228 / 553 / 444** (definiciones / configuraciones / pares definición×implemento);
  `CURATED` 133 → 146, `LEGACY` 82 sin cambio; revisión `v2-approved-2026-09-29-a` y ontología sin cambio; SHA `c30a5c2e…` →
  `8e70937474a217c8314eb53f17a94456fee0ec3d93f9587695ec4727dd8f33f9`.
- Aterrizaje de prueba en el worktree (`catalog_v2_land.py --definitions` con las 13 definiciones): gate estricto READY, auditoría de
  calidad 0 errores / 0 avisos, `compile --check` y pines movidos. `pytest scripts/tests backend/tests/test_exercises_catalog_v2.py`:
  214 aprobadas y 162 subtests, 0 fallos.
- **No cambia ningún perfil preexistente.** Las 13 definiciones y sus 14 configuraciones son todas nuevas, y las 215 definiciones y 539
  configuraciones anteriores salen idénticas del compilado (comparación campo a campo con el catálogo base de la rama: 0 diferencias
  en las 539 configuraciones, las 215 definiciones y las 96 familias, ni en la revisión y la ontología). Por eso el informe de
  `catalog_v2_lote_report.py` (sección 7) no tiene deltas y las altas se listan aparte.
- Lo que ya existía y por eso **no se añadió**: el brief decía que no había paseo del granjero, pero `forearms_paseo_del_granjero`
  (definición `LEGACY` de `lower_isometric_grip`) ya tiene cuatro configuraciones: mancuernas, kettlebell, disco y barra hexagonal.
  Cubre lo que pedía el apartado B de «paseo del granjero (mancuernas, kettlebells o barra hexagonal)».

## 2. Soportes y cableado Kotlin

- **Ninguna alta necesita un soporte nuevo** (la regla STOP no se activó). `supportRequirementsFor` ya conocía `rack`:
  `push_jerk__barbell`, `split_jerk__barbell` y `overhead_squat__barbell` lo piden, porque se hacen sacando la barra de un soporte (a
  los hombros en los enviones, con los brazos ya estirados en la sentadilla de arranque); sin él habría que subirla del suelo con una
  cargada o un arranque, que es otro ejercicio. Las otras 11 altas no piden ningún soporte: las ocho de cargada, arranque y tirón
  parten del suelo o de la posición colgada tras levantarla, y el maletín y el paseo Zercher se levantan del suelo. Los enviones y la
  sentadilla de arranque quedan así fuera de un gimnasio sin rack, como `front_squat__barbell`.
- Cambio mínimo en `EffectiveEquipmentCatalog.kt`: una rama de `supportRequirementsFor` (`RACK_OVERHEAD_CONFIGURATIONS`) y su
  párrafo de KDoc. No se tocó el resolutor, el filtro único ni `EquipmentSymbols`: el material de estas altas ya lo acreditan los
  símbolos «Barra y discos», «Mancuernas», «Kettlebell» y «Rack» con las llaves y categorías que dejaron E y E2.
- **Sin símbolo ni llave, y por tanto sin exigirse:** los discos de goma (*bumpers*) y la plataforma con los que se suelta la barra
  en cargadas, arranques y enviones, y el agarre en gancho o las muñequeras. El paso de material no los pregunta: quien marca «Barra y
  discos» recibe estas altas aunque su gimnasio no deje soltar la barra. Está en los riesgos (sección 5); si se quiere resolver hace
  falta un símbolo («Discos de goma y plataforma») y su llave, decisión de producto y del paquete E.
- Prueba nueva `OlympicLotEquipmentTest` (8 pruebas): solo enviones y sentadilla de arranque piden `rack`; una barra sola abre
  las cargadas, los arranques, los tirones y el paseo Zercher pero no los tres que piden rack; un rack sin barra no abre nada; el
  gimnasio completo abre las 14 y sin rack pierde solo esos tres; sin barra no se alcanza ninguna de las de barra; el maletín sigue
  al peso que se tiene (mancuernas o kettlebell); y las dificultades modeladas no superan el tope aprobado (7,0) y dejan las
  olímpicas por encima del tope de los novatos (5,2).
- No se tocó `NativeWorkoutProgressionRuntime` ni ninguna receta fija: las altas entran solo por la selección nativa, y el generador
  no las cita todavía (la sección 9 dice dónde encajan).

## 3. Qué se descartó y por qué

### 3.1 Halterofilia (apartado A)

- **Balance de arranque (*snatch balance*):** no hay un solo estudio indexado sobre él (la búsqueda en PubMed devolvió trabajos
  ajenos) y la regla pide 2 o más fuentes verificables. Fuera.
- **Cargada y envión como complejo, y las variantes desde bloques o desde colgado de las versiones completas** (*hang squat clean*,
  *hang squat snatch*): son combinaciones o posiciones de salida de ejercicios que ya están dados de alta, sin fuentes propias que
  los distingan, y una configuración no describe una secuencia. El generador tampoco programa complejos.
- **Tirón alto (*high pull*), *jump shrug*, tirón a media altura de muslo:** no estaban en el brief y quedan para un lote posterior
  (los tres se hacen con barra y discos, sin soporte).
- Dentro del tope de ~24 elementos (13 definiciones, 14 configuraciones) y sin recortar nada por falta de fuentes: las tres con menos
  (tirón de arranque, maletín y paseo Zercher) tienen 3 estudios cada una, además de sus capítulos de anatomía.

### 3.2 «Strongman» (apartado B)

- **Dadas de alta (con el material del wizard):** paseo del maletín (`suitcase_carry`, una mano, con mancuerna o kettlebell) y paseo
  Zercher (`zercher_carry`). El paseo del granjero ya existía (sección 1).
- **No dadas de alta, regla STOP:** el **press de tronco o de eje** y el **peso muerto de eje o de barra gruesa**. «Con barra» no
  vale: el tronco y el eje no son una barra olímpica (distinto grosor, agarre y recorrido; el grosor es lo que cambia el ejercicio) y
  ni el vocabulario de implementos (`barbell`, `hex_bar`, `safety_bar`, `ez_bar`, `t_bar`, `h_bar`, `plate`, `kettlebell`…) ni los
  símbolos del paso de material los distinguen. Etiquetarlos como `barbell` mentiría sobre el material que acredita el usuario.
- **Paseo «de abrazo»:** se hace con saco, piedra o tronco; sin el implemento no hay ejercicio. Mismo motivo.

**Implementos que faltan** (ninguno está en el vocabulario de implementos ni en los símbolos del paso de material; qué haría falta y
cuántas configuraciones abriría cada uno). Los recuentos son una estimación editorial de lo que de verdad se programa con cada
implemento, **sin buscar evidencia**: se cuentan para decidir si merece un lote, no se proponen como altas.

| Implemento (`equipmentId` propuesto) | Símbolo del paso de material que haría falta | Configuraciones que abriría | Cuáles |
|---|---|---|---|
| Yugo (`yoke`) | «Yugo» | 1 | paseo con yugo |
| Piedras de atlas (`atlas_stone`) | «Piedras de atlas» | 2 | cargar la piedra a una plataforma; acarreo de la piedra en abrazo |
| Tronco (`log`) | «Tronco» | 3 | cargada de tronco; press de tronco; cargada y press de tronco |
| Eje / barra gruesa (`axle`) | «Barra gruesa o eje» | 4 | peso muerto de eje; cargada de eje; press de eje; cargada y press de eje |
| Trineo (`sled`) | «Trineo» | 3 | empuje; arrastre hacia atrás; arrastre con arnés hacia delante |
| Saco de arena (`sandbag`) | «Saco de arena» | 5 | acarreo en abrazo; carga al hombro; cargada de saco; sentadilla de abrazo; zancada con saco |
| Neumático (`tire`) | «Neumático» | 1 | volteo de neumático (con mazo serían 2) |
| Barril (`barrel`) | «Barril» | 2 | carga de barril a una plataforma; acarreo de barril |
| **Total** | | **21** (22 con el mazo) | |

Cada implemento exige además: su `equipmentId` en `profile.equipmentId` y `compatibleEquipmentIds`, una entrada en
`EquipmentSymbols`, su llave y categoría en el resolutor (`SYMBOL_EQUIPMENT_KEYS`), el lugar donde se ofrece el símbolo (casi todos
solo en gimnasio) y su fila en `EquipmentReachTest`. Si el usuario prefiere un único símbolo «Material de strongman» que acredite
varios, el catálogo seguiría necesitando un `equipmentId` por implemento. Las entradas `missing` de `Disciplines.kt` (yugo, piedra de
atlas, press con tronco y eje, trineo, neumático, peso muerto con eje) siguen vigentes, y «acarreos de maleta pesada y de yugo» queda
reducida a la de yugo, porque este lote cubre la maleta; el saco de arena y el barril no figuran en esa lista.

- **Alcanzables con el material actual y no pedidos** (candidatos a un lote OL-2): además del *high pull* y el *jump shrug*, el
  acarreo en rack frontal y por encima de la cabeza con kettlebell o mancuernas.

## 4. Herencia de `efc/cnc/ssc/ttc` y dificultad (`fieldSemanticsGap`)

Los cuatro campos y `axialLoadFactor` se heredan sin cambio de perfiles aprobados porque su significado no está documentado en el
repositorio (`fieldSemanticsGap`); `technicalDifficulty` es una **puntuación editorial modelada**, no una medición, colocada entre
anclas aprobadas. `setupTimeSeconds` es 45 s en los levantamientos con barra (como el peso muerto y las sentadillas) y 20 s en el
maletín (como el paseo del granjero).

| Altas | `efc / cnc / ssc / ttc` (y `axialLoadFactor`) heredados de | `technicalDifficulty` (anclas) |
|---|---|---|
| `power_clean`, `hang_power_clean`, `squat_clean`, `power_snatch`, `hang_power_snatch`, `squat_snatch`, `overhead_squat` | `front_squat__barbell`: 2,8 / 2,2 / 0,5 / 2,0 (axial 0,7) | cargada de potencia 6,5; desde colgado 6,0; completa 7,0; arranque de potencia 6,8; desde colgado 6,5; completo 7,0; sentadilla de arranque 6,0 |
| `clean_pull`, `snatch_pull` | `deadlift_to_knees__barbell`: 2,8 / 2,2 / 1,0 / 2,0 (axial 1,0) | 6,0 los dos |
| `push_jerk`, `split_jerk` | `deltoides_push_press__default`: 2,5 / 1,8 / 0,3 / 1,6 (axial 0,0) | 6,0 y 6,5 |
| `suitcase_carry` (los dos implementos) | `forearms_paseo_del_granjero__dumbbells`: 2,0 / 1,5 / 0,2 / 1,5 (axial 0,3) | 4,5 (la del granjero) |
| `zercher_carry` | `efc / cnc / ttc` de `quads_sentadilla_zercher_barra_recta__default` (2,8 / 2,2 / 2,0, `fatigueTier` MEDIA: es el perfil multiarticular con la misma barra); `ssc` y `axialLoadFactor` de `back_hiperextension_45_zercher_espalda_baja__default` (0,4 / 0,4, el único perfil Zercher de su familia; la sentadilla trae 0,5 / 0,7) | 5,0 |

- **Criterio de las dificultades.** La escala del catálogo no ordena por dureza real (`bench_press__*` está en 7,0, el peso muerto y
  la sentadilla frontal en 4,2) y su tope aprobado es 7,0, así que los levantamientos olímpicos no pueden reflejar su curva de
  aprendizaje real: la cargada y el arranque completos quedan en el tope (7,0, a la par de la familia del press de banca), las
  versiones de potencia un escalón por debajo (la recepción es más fácil), las de colgado otro medio punto (no hay despegue), los
  tirones y la sentadilla de arranque en 6,0 (sin recepción, o con recepción pero sin tirón) y los enviones en 6,0 y 6,5 (la tijera
  añade un paso). Los valores son anclas ya aprobadas (4,5 del paseo del granjero, 5,0, 6,0 de `push_up__flat`, 6,8 del press Spoto y
  7,0 del press de banca) o el punto medio 6,5 entre 6,0 y 6,8, el mismo que BW-1 propone para `archer_push_up__default` (antes de BW-1
  no había ningún 6,5). Ampliar la escala por encima de 7,0 para separar mejor el arranque del press de banca sería una decisión de
  producto aparte (el contrato permite 1 a 10).
- **Novatos.** D1 y el planificador no arrancan a un novato por encima de 5,2 (`NOVICE_MAX_DIFFICULTY` de `ExerciseSelector`), con una
  excepción: los ejercicios que la reserva marca como `basic`. **Las 11 altas olímpicas (cargadas, arranques, tirones, enviones y
  sentadilla de arranque, de 6,0 a 7,0) quedan fuera de los planes de novatos solo si el generador no las marca `basic`:** que no se
  haga. El paseo Zercher (5,0) y el maletín (4,5) sí caben en un plan de novato; para el Zercher se recomienda `minLevel = INT`
  (un acarreo con la barra en los codos no es un primer ejercicio).
- `axialLoadFactor = 0,0` de los enviones viene del push press y del press militar (carga externa por encima de la cabeza modelada sin
  componente axial): subestima la carga de la columna con una barra pesada en lo alto. Es una elección de modelo heredada, no de este
  lote; si se corrige para el empuje vertical hay que hacerlo en todo el catálogo.

## 5. Riesgos y roles que requieren el criterio del usuario

- **Evidencia indirecta.** Hay electromiografía o cinética propias de la cargada, el arranque y sus tirones (Geisler 2023, Arauz,
  Kipp 2011 y 2019, Suchomel, Nagao e Ishii), de la sentadilla de arranque (Dinis 2021, Roth 2020, Ikeda 2025) y del paseo del
  maletín (Ellestad 2024, Graber 2021, McGill 2009), pero ninguna de cada variante: las versiones de potencia, desde colgado y
  completas comparten la lectura de los estudios de la cargada o del arranque. Los enviones se apoyan en una revisión (Soriano), en la
  electromiografía del press de hombros (Padovan) y en la cinética del envión de tijera (Nagao), **sin electromiografía del envión**; el
  paseo Zercher, en la actividad del erector al caminar con carga delante (Svenningsen 2017) y en los acarreos de strongman (McGill
  2009, Ellestad 2024), con el bíceps y el trapecio inferidos de la anatomía. Cada `why` dice qué es medición y qué es inferencia.
- **Sin imágenes.** Los 14 pares definición × implemento nuevos no tienen PNG; `catalog_v2_visual_brief.py` arma el brief de las 13
  definiciones, con `promptCore` y QA listos para la cola de imágenes.
- **Soltar la barra.** Cargadas, arranques y enviones se hacen soltando la barra desde los hombros o la cabeza, con discos de goma y una
  plataforma, y muchos gimnasios comerciales no lo permiten. El paso de material no tiene símbolo ni llave para eso (sección 2): el
  generador y el planificador los ofrecerán a quien marque «Barra y discos» y, cuando una reserva los cite, a un plan entero de
  halterofilia. Lo avisan el texto público de la cargada de potencia y las pistas o la técnica de la cargada completa, los tres
  arranques, los dos tirones, la sentadilla de arranque y el paseo Zercher; no lo dicen la cargada colgada ni los dos enviones. Si se
  quiere evitarlo, hace falta un símbolo («Discos de goma y plataforma») y su llave: decisión de producto.
- **`rack` en enviones y sentadilla de arranque** (sección 2): es mi lectura de cómo se hacen (desde un soporte); un envión también se
  puede hacer tras una cargada, sin rack. Si se prefiere ofrecerlos sin rack basta con quitar los tres ids de
  `RACK_OVERHEAD_CONFIGURATIONS` (y su prueba).
- **Solapes con ejercicios que ya existen.** `suitcase_carry` comparte familia y patrón (`isometric_grip`) con
  `forearms_paseo_del_granjero` (`LEGACY`, con cuatro configuraciones) y se distingue por ser de una sola mano y por la resistencia a la
  flexión lateral; `push_jerk` comparte familia con `deltoides_push_press__default` y se distingue por la segunda flexión de rodillas
  bajo la barra; `overhead_squat` es la sentadilla con la barra por encima del apoyo del tronco, como la frontal por delante.
  Cuando se recure el paseo del granjero (lote de antebrazo y agarre) conviene releerlos juntos.
- **Dificultades y herencias modeladas**, no medidas (sección 4), y el tope de la escala (7,0) impide que la cargada y el arranque
  completos se separen del press de banca. `reviewStatus: APPROVED` es propuesto.

**Roles propuestos que requieren el criterio del usuario** (cada uno está razonado en el `why` de la ficha; los resumo porque
mueven el volumen semanal que cuenta el generador: cada PRIMARY suma una serie completa, y el primero fija
`primaryMuscles.first()`, el músculo dominante de la configuración):

1. **Primer PRIMARY.** Glúteo mayor en las cargadas, los arranques de potencia y los tirones (la extensión de cadera acelera la
   barra); **cuádriceps en la cargada y el arranque completos y en la sentadilla de arranque** (el fondo de la sentadilla es lo que
   distingue las versiones completas de las de potencia: se apoya en Moolyk, Arauz y Dinis, que no ordenan glúteo y cuádriceps);
   deltoides en los enviones; **antebrazo en el maletín**; **erectores en el Zercher**. La cargada y el arranque completos conservan
   el patrón `hip_hinge_explosive` de su familia (que `CompositionTaxonomy` cuenta como bisagra) con el cuádriceps como dominante: la
   composición de sesión los verá como bisagra con cuádriceps dominante.
2. **Isquiosurales SECUNDARIOS** en todo lo olímpico y en los tirones (en los pesos muertos del catálogo son PRIMARY) y ausentes en la
   sentadilla de arranque, como en la frontal. Arauz midió más bíceps femoral en la cargada que en el arranque; ningún estudio los
   ordena frente al glúteo.
3. **Deltoides y tríceps SECUNDARIOS en los arranques** (bloqueo sobre la cabeza) y deltoides ESTABILIZADOR en las cargadas (sostén de
   la barra en el rack frontal): se infieren de los momentos de hombro y codo de Arauz y de la anatomía, sin electromiografía.
4. **Enviones: deltoides PRIMARY y piernas SECUNDARIAS.** Soriano califica el envión de «no técnicamente un movimiento de empuje»: la
   barra la acelera la extensión de cadera, rodillas y tobillos y el atleta se mete bajo ella. El deltoides PRIMARY (y el hombro
   PRIMARY) viene de la regla `press.overhead-synergists` del empuje vertical más que de la evidencia, y el cuádriceps y el glúteo
   mayor quedan SECUNDARIOS por debajo de lo que sugiere la propia fuente; subirlos a PRIMARY sumaría una serie completa de tren
   inferior a un ejercicio de hombro. El push press aprobado ya tiene el cuádriceps SECUNDARIO; el glúteo mayor y el trapecio
   SECUNDARIOS son míos. No hay electromiografía del envión (la única es de tronco: Eriksson Crommert, 2014).
5. **Maletín.** Abdominales PRIMARY junto al antebrazo: el oblicuo externo contralateral se activó como en la plancha con unos 25 kg
   (Ellestad) y el recto abdominal quedó muy por debajo; el catálogo no separa el oblicuo del resto de `abdominals` y el atlas Aprende
   enlaza ese músculo con el recto abdominal. **Erectores SECUNDARIOS**, aunque el longissimus llegó al 29 % de la contracción máxima
   frente al 33 % del oblicuo externo (la alternativa es PRIMARY: otra serie completa). Muñeca PRIMARY y columna lumbar SECUNDARIA por
   la regla `joint.grip-wrist`, aunque los estudios solo midieron tronco y cadera. Trapecio ESTABILIZADOR frente al SECUNDARIO que tiene
   el paseo del granjero heredado: conviene alinearlos cuando se recure.
6. **Zercher: erectores PRIMARY y ningún SECUNDARIO**; abdomen, trapecio y bíceps ESTABILIZADORES; cadera ESTABILIZADOR como única
   articulación del tren inferior. La única medición cercana es la de erectores al caminar con carga delante (Svenningsen, el 10 % de
   la masa corporal): quien lo programe como acarreo de cuerpo entero verá solo erectores en el volumen.
7. **Aductores SECUNDARIOS** en las completas y la sentadilla de arranque (Dinis para el aductor mayor; Kubo para la hipertrofia de la
   sentadilla profunda, que es otra sentadilla); el glúteo medio ESTABILIZADOR del arranque completo se apoya en los momentos
   abductores de Arauz, sin electromiografía.
8. **Bíceps ESTABILIZADOR en las cargadas.** Santos lo incluye en la sinergia de flexión del miembro superior y el codo es SECUNDARIO en
   esas fichas: SECUNDARIO (0,5 en vez de 0,4) también sería defendible.
9. **Rodilla PRIMARY y tobillo SECUNDARIO en cargadas y tirones.** Kipp (2011) midió, en general, pares de tobillo mayores o iguales que
   los de rodilla; el rol de la rodilla es una decisión editorial (empuja el suelo y frena la barra al recibirla), documentada en el
   `why` de cada ficha.

**Revisión limpia aplicada.** Tres revisores sin contexto (solo lectura) leyeron las 13 definiciones contra los resúmenes PubMed de
sus fuentes, y el texto completo de Santos, Geisler y Ellestad: cargadas y tirón de cargada; arranques, tirón de arranque y sentadilla
de arranque; enviones y acarreos. Ninguno halló una cita infiel ni un rol claramente erróneo. Sí hallaron, y está corregido (commit
`c54071c21`):

- El cuádriceps «se flexiona» al recibir la barra (frena la flexión; tres fichas) y el bíceps de las tres cargadas «sin
  electromiografía propia» (Santos sí lo registró dentro de la sinergia de flexión del miembro superior).
- Atribuciones que el resumen no hace: Meechan (tirón colgado al 140 %) para el agarre del tirón de arranque; Arauz y Suchomel para la
  mecánica de la rodilla y de los isquiosurales del tirón; Ikeda para la columna torácica; Khou como si «confirmara» roles; McGill por
  una cocontracción que es del yugo; los momentos de Arauz «durante la subida» cuando miden fases y ejes distintos. Los isquiosurales
  de la sentadilla de arranque salían de diferencias de grupo en personas con valgo: se quitaron.
- Seguridad de las pistas: tres pistas de arranque cerraban la repetición bajando la barra «con control hasta el suelo» desde encima de
  la cabeza, y el envión de empuje pedía una pausa en la flexión. Ahora dicen soltar la barra sobre discos bumper o bajarla con
  control, y «sin pausa».
- Profundidades que se contradecían («un cuarto de sentadilla» con «los muslos justo por encima de la horizontal»): ahora «sentadilla
  corta, con los muslos claramente por encima de la horizontal».
- Visual: el agarre ancho no se ve de perfil, así que los arranques pasan a un tres cuartos frontal (si no, la cargada colgada y el
  arranque colgado eran la misma imagen) y la sentadilla de arranque a perfil (si no, igual que el arranque completo); guardas
  inversas en la cargada colgada, el tirón de cargada, el envión de empuje y el Zercher.
- Coherencia entre hermanas: gemelos en el envión de tijera, columna torácica en los dos arranques de potencia, glúteo medio y
  abducción de cadera en el arranque completo, cadera en el Zercher; pies y agarre del envión de empuje unificados.

Sugerencias que **no** se aplicaron: el dorsal ancho como estabilizador en cargadas y arranques (ni el tirón de arranque ni los pesos
muertos del catálogo lo llevan y no hay medición: queda para la recuración de las bisagras), el serrato anterior (no está en la
ontología) y un soporte bajo para levantar el Zercher del suelo, que es lo más delicado del ejercicio (el vocabulario de soportes no
lo tiene).

## 6. Pruebas que cambian de cifras

Pines movidos de 215 / 539 / 430 y del SHA `c30a5c2e…` a 228 / 553 / 444 y `8e70937474a2…`: `AprendeCatalogAuditTest.kt` (228
definiciones y 553 configuraciones; 553 también en los recuentos de metadatos ricos, de cobertura editorial y articular, en el índice de
ejercicios en ejecución y en el índice inverso de patrones; y el SHA), `ExerciseCatalogContractTest.kt` (228 / 553),
`scripts/tests/test_catalog_v2_show.py` (215 → 228 filas) y `backend/tests/test_exercises_catalog_v2.py` (el SHA). Son recuentos del
catálogo, no cifras de planes. `ExerciseCatalogAuditTest` no cambia: ninguna definición con recuento fijado gana configuraciones. Prueba
nueva: `OlympicLotEquipmentTest` (8 pruebas: soportes de las altas, alcanzabilidad con el filtro único y dificultades modeladas).

**Tests de planes: no cambia ninguna cifra, no se re-baselinó nada.** Pasan sin tocar sus pines `PlanGenerationCoverageT006Test` (5),
`T006PersistenceAndUseIntegrationTest` (7), `PlanCoverageContractTest` (2), `SetupExecutableAvailabilityMatrixTest` (27),
`NativeProfileRecipeAndFitterTest` (36), `PersonalizedPlanCatalogTest` (21), `EffectiveEquipmentContractTest` (14),
`FixedRecipeEquipmentCompatibilityTest` (18) y `PlanRejectionPresenterTest` (28). Ninguna receta fija ni plantilla del código cita los
ids nuevos (solo los cita `EffectiveEquipmentCatalog`, para el rack): entran solo por la selección nativa.

**Kotlin Base, una corrida de `:app:testBaseDebugUnitTest` por el envoltorio de ranuras** sobre el estado final de la rama, filtrada por
`domain.training.*` (con `generator.*` y `split.*`), `domain.onboarding.*`, `domain.exercises.*`, `domain.templates.*`,
`data.protocols.*`, `data.programs.*`, `data.exercises.*`, `data.onboarding.*`, `ExerciseCatalogContractTest`,
`screens.sessioneditor.*`, `SetupExecutable*`, `PlanInfoModelTest` y `SetupFixedWarmupOptionsTest`: **244 suites y 2435 pruebas, 0
fallos, 0 errores, 0 omitidas.** Entre ellas `domain.training.generator.*` (7 suites, 49 pruebas: barridos, comportamiento, reservas y
`GeneratorEquipmentTest`), `GeneratorPoolsCatalogTest` (6), `EquipmentReachTest` (10), `ConfigurationEquipmentFilterTest` (10),
`EffectiveEquipmentResolverContractTest` (21), `EquipmentSymbolsTest` (29), `ExerciseTraitResolverTest` (11, recorre las 553
configuraciones), `CatalogAxialSscConsistencyTest` (1), `BodyweightLotEquipmentTest` (11) y `OlympicLotEquipmentTest` (8). Las altas no
rompen ningún barrido ni conteo del generador: la matriz que regenera `RoutineMatrixReportTest` es idéntica, línea a línea, a
`matrix-d1.txt` de E, y `sweep-problems.txt` sale vacío (las reservas citan ids concretos y ninguno es nuevo). `EquipmentReachTest` no
fija ninguna cifra del tamaño del catálogo: lo que cambia es el informe que escribe, en la sección 8. Una primera corrida con el
catálogo recién aterrizado (antes de la revisión limpia, SHA `c815f8e0…`) dio lo mismo: 244 suites y 2435 pruebas, 0 fallos; esta se
hizo después de aplicar las correcciones de los revisores y de mover los pines (el último commit, `66ee8c432`, solo toca el texto de una
ficha: el catálogo compilado y el SHA no cambian).

**Python:** `catalog_v2_gate --strict` READY, `compile --check` y pines al día, y `pytest scripts/tests
backend/tests/test_exercises_catalog_v2.py`: 214 aprobadas y 162 subtests, 0 fallos.

## 7. Informe de lote (formato de `catalog_v2_lote_report.py`)

Generado con las funciones de `catalog_v2_lote_report.py` (`render_markdown`, `budget_ledger`) sobre las fichas del lote. Como las 14
configuraciones son todas nuevas y el script solo sabe comparar configuraciones que ya existían, los deltas salen vacíos y las altas
se listan aparte, como en BW-1. Aparte, se comparó el catálogo compilado entero antes y después del lote (el de `ent/d6` justo antes de
aterrizar y el actual): **0 diferencias** en las 539 configuraciones, las 215 definiciones y las 96 familias (sin sus definiciones)
anteriores, y la revisión (`v2-approved-2026-09-29-a`) y la ontología (`wikilab-v3-2026-08-08`) no cambian.

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
| budget.mejor | 1 | 12 | suitcase_carry |
| budget.referencia | 1 | 8 |  |
| budget.equilibrio | 2 | 15 |  |
| budget.concentrar | 3 | 15 | hang_power_clean, suitcase_carry |
| budget.repartir | 3 | 5 | split_jerk |
| budget.guiado | 1 | 25 |  |
| budget.progresion | 1 | 20 |  |
| budget.medir | 3 | 8 | zercher_carry |
| budget.completo | 4 | 10 | power_clean, squat_clean, squat_snatch |
| budget.distinto | 2 | 8 |  |

Ningún tope se acerca a su máximo.

#### Configuraciones comparadas

Configuraciones que ya existían en el catálogo base y se compararon con `configuration_delta`: 0 (definiciones del lote: 13). Altas sin
«antes»: 14.

#### Altas del lote (sin comparación: no existían)

| configuración | definición | dominante | PRIMARY | patrón | implemento | dificultad |
|---|---|---|---|---|---|---|
| `clean_pull__barbell` | clean_pull | gluteus_maximus | gluteus_maximus, quadriceps | hip_hinge_explosive | barbell | 6.0 |
| `hang_power_clean__barbell` | hang_power_clean | gluteus_maximus | gluteus_maximus, quadriceps | hip_hinge_explosive | barbell | 6.0 |
| `hang_power_snatch__barbell` | hang_power_snatch | gluteus_maximus | gluteus_maximus, quadriceps | hip_hinge_explosive | barbell | 6.5 |
| `overhead_squat__barbell` | overhead_squat | quadriceps | quadriceps, gluteus_maximus | knee_dominant | barbell | 6.0 |
| `power_clean__barbell` | power_clean | gluteus_maximus | gluteus_maximus, quadriceps | hip_hinge_explosive | barbell | 6.5 |
| `power_snatch__barbell` | power_snatch | gluteus_maximus | gluteus_maximus, quadriceps | hip_hinge_explosive | barbell | 6.8 |
| `push_jerk__barbell` | push_jerk | deltoid | deltoid | vertical_push | barbell | 6.0 |
| `snatch_pull__barbell` | snatch_pull | gluteus_maximus | gluteus_maximus, quadriceps | hip_hinge_explosive | barbell | 6.0 |
| `split_jerk__barbell` | split_jerk | deltoid | deltoid | vertical_push | barbell | 6.5 |
| `squat_clean__barbell` | squat_clean | quadriceps | quadriceps, gluteus_maximus | hip_hinge_explosive | barbell | 7.0 |
| `squat_snatch__barbell` | squat_snatch | quadriceps | quadriceps, gluteus_maximus | hip_hinge_explosive | barbell | 7.0 |
| `suitcase_carry__dumbbells` | suitcase_carry | forearm | forearm, abdominals | isometric_grip | dumbbells | 4.5 |
| `suitcase_carry__kettlebell` | suitcase_carry | forearm | forearm, abdominals | isometric_grip | kettlebell | 4.5 |
| `zercher_carry__barbell` | zercher_carry | erector_spinae | erector_spinae | spinal_extension | barbell | 5.0 |

## 8. Alcanzabilidad del material: antes → después

Medida con las reglas de `EquipmentReachTest` (resolutor único y filtro único de E y E2; «alcanzable» = el planificador y el generador
la aprueban con el equipo que acreditan los símbolos). **Antes** = el catálogo de 539 configuraciones tras BW-1, con las cifras de
`lote-bw1-report.md` §9 (el `reach.txt` de `ent/d4`); **después** = el catálogo de 553 de esta rama, tal como lo escribe
`EquipmentReachTest` en `build/reports/equipment-reach/reach.txt`. Comparado fila a fila, solo cambian las que salen abajo.

Qué sube y por qué: las 9 altas que solo piden barra (las ocho de cargada, arranque y tirón y el paseo Zercher) las abre «Barra y
discos»; los tres que piden rack (los dos enviones y la sentadilla de arranque) necesitan además «Rack»; el maletín lo abren las
mancuernas o la kettlebell. Ninguna alta se alcanza con «solo cuerpo» ni con la semilla del parque (no hay barra ni pesas): los
materiales de peso corporal y las anillas no suben. Ninguna configuración deja de ser alcanzable, las 14 altas lo son desde algún
símbolo y lugar, y los inalcanzables siguen siendo 8 (ahora de 553): cuatro de la barra de seguridad, la barra H, el curl nórdico,
los sliders y el rodillo de muñeca. «Rack» solo no abre nada nuevo (todo lo que pide rack pide también barra), pero quitarlo del
gimnasio o de casa hace perder tres más.

**Materiales de referencia**

| material | antes (de 539) | después (de 553) |
|---|---|---|
| solo cuerpo | 34 | 34 |
| parque (semilla) | 53 | 53 |
| casa con mancuernas y banco | 127 | 128 |
| casa con anillas y cajón | 41 | 41 |
| gimnasio completo | 528 | 542 |
| gimnasio sin rack | 513 | 524 |

**Lugares** (solo cuerpo / con la semilla del lugar / con todo lo que ofrece)

| lugar | antes | después |
|---|---|---|
| Gimnasio | 34 / 528 / 531 | 34 / 542 / 545 |
| En casa | 34 / 34 / 505 | 34 / 34 / 519 |
| En espacios públicos | 34 / 53 / 197 | 34 / 53 / 199 |
| todos los lugares y todos los símbolos | 34 / – / 531 | 34 / – / 545 |

**Símbolos** (solo: lo que abre por sí solo sobre solo cuerpo / imprescindible: lo que se pierde al quitarlo). Solo las filas que
cambian; el resto (banco, poleas, máquinas, Smith, barra de dominadas, paralelas, anillas, bandas, balón, cuerda de saltar, cajón y
cardio) queda igual.

| símbolo | lugar | antes | después |
|---|---|---|---|
| Barra y discos | Gimnasio | 107 / 120 | 116 / 132 |
| Barra y discos | En casa | 87 / 100 | 96 / 112 |
| Rack | Gimnasio | 6 / 15 | 6 / 18 |
| Rack | En casa | 4 / 13 | 4 / 16 |
| Mancuernas | Gimnasio | 84 / 87 | 85 / 88 |
| Mancuernas | En casa | 84 / 88 | 85 / 89 |
| Mancuernas | En espacios públicos | 84 / 88 | 85 / 89 |
| Kettlebell | Gimnasio | 30 / 33 | 31 / 34 |
| Kettlebell | En casa | 30 / 33 | 31 / 34 |
| Kettlebell | En espacios públicos | 30 / 33 | 31 / 34 |

Quien no tenga barra no recibe ninguna de las ocho de cargada, arranque y tirón, ni el Zercher ni los enviones; sin rack tampoco los
enviones ni la sentadilla de arranque. «Solo cuerpo» y la semilla del parque no abren nada de este lote, y «En espacios públicos» solo
suma las dos del maletín, porque el parque no ofrece barra.

## 9. Tabla: patrón del generador → ids nuevos recomendados

Para que el paquete del generador (D1b) actualice las reservas de `MovementPools.kt` y `Disciplines.kt`. Los patrones son los de
`RoutinePattern`; entre paréntesis, la dificultad técnica (los ids no cambian ningún plan hasta que una reserva los cite: el
planificador SCP y las recetas fijas solo usan ids explícitos). Los niveles y tipos son sugerencias de reserva, no una decisión mía.

| Patrón del generador | Reserva donde encaja | Ids nuevos recomendados | Material / soporte | Sugerencia |
|---|---|---|---|---|
| `POWER` Potencia | `weightliftingPower` (base de halterofilia) y `power` general | `hang_power_clean__barbell` (6,0), `power_clean__barbell` (6,5), `hang_power_snatch__barbell` (6,5), `power_snatch__barbell` (6,8) | barra y discos; sin soporte | `kind = BALLISTIC`, de 2 a 3 repeticiones; `minLevel = INT` las de colgado y `ADV` las del suelo; nunca `basic` |
| `POWER` Potencia / `SQUAT` Sentadilla | `weightliftingPower` y `weightliftingSquat` | `squat_clean__barbell` (7,0), `squat_snatch__barbell` (7,0) | barra y discos; sin soporte | `ADV`; son los levantamientos de competición |
| `HINGE` Bisagra de cadera | `weightliftingHinge` (con `deadlift_to_knees__barbell`) | `clean_pull__barbell` (6,0), `snatch_pull__barbell` (6,0) | barra y discos; sin soporte | `kind = HEAVY`, `fitsRole = SECONDARY`, `INT` |
| `SQUAT` Sentadilla | `weightliftingSquat` | `overhead_squat__barbell` (6,0) | barra + `rack` | `INT`, accesorio de movilidad y recepción con carga ligera |
| `VERTICAL_PUSH` Empuje vertical | `weightliftingPower` y `strongmanVerticalPush` (con `deltoides_push_press__default`) | `push_jerk__barbell` (6,0), `split_jerk__barbell` (6,5) | barra + `rack` | `kind = BALLISTIC`; `INT` el de empuje y `ADV` el de tijera |
| `CARRY` Acarreo | `carry` general y strongman (hoy solo `forearms_paseo_del_granjero__dumbbells` y `__kettlebell`) | `suitcase_carry__dumbbells` y `suitcase_carry__kettlebell` (4,5), `zercher_carry__barbell` (5,0) | mancuernas, kettlebell o barra y discos; sin soporte | `kind = TIMED`; el Zercher con `INT` (con la misma barra el planificador lo daría a un novato: 5,0 < 5,2) |
| `GRIP` Agarre | `grip` | `suitcase_carry__dumbbells` y `__kettlebell` (antebrazo primer PRIMARY) | mancuernas o kettlebell | `TIMED` |
| `CORE_ROTATION` Core: rotación y lateral | `coreRotation` | `suitcase_carry__dumbbells` y `__kettlebell` (abdominales PRIMARY: resistir la flexión lateral) | mancuernas o kettlebell | `TIMED`, como alternativa lateral a la plancha |
| `BACK_EXTENSION` Extensión de espalda | `backExtension` | `zercher_carry__barbell` (erectores primer PRIMARY) | barra y discos | `INT`; el único de la reserva con carga externa |

Además, para `Disciplines.kt` (`missing`): con este lote dejan de faltar los tirones de arranque y de cargada, la sentadilla de
arranque, el envión (empuje y tijera), las variantes de potencia y desde colgado de cargada y arranque, y «acarreos de maleta pesada»
(la de yugo sigue sin material). Siguen faltando la cargada y el arranque desde bloques y la cargada con envión como complejo. Ningún
id nuevo tiene `LiftMark` propio (no existe marca de cargada ni de arranque): usarlos sin marca, como el swing de kettlebell, o
crear marcas es decisión del paquete del generador. Si una reserva los cita, `GeneratorPoolsCatalogTest` comprueba que existen y
están aprobados, y exige que la entrada de `push_jerk__barbell`, `split_jerk__barbell` y `overhead_squat__barbell` declare
`requires = "rack"` (el requisito compartido de `supportRequirementsFor`), como las sentadillas con rack.
