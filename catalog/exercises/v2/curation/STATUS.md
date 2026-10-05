# Estado de ejecución — catálogo de ejercicios

## Estado vigente — re-curaduría integral (desde 2026-10-01)

Todo lo que sigue a esta sección es el **registro histórico** de las curadurías
anteriores (v3 a v7.2). Nombra archivos y campos que ya no existen:
`editorial_briefs.json`, `muscleNotes`, `techniqueSummary`, `benefits`, etc. Lo
vigente es esto:

- Revisión del catálogo `v2-approved-2026-09-29-a` (sin cambio; los ids tampoco
  cambian, el resolver rechaza ejercicios guardados con otra revisión) y
  ontología `wikilab-v3-2026-08-08`.
- 96 familias, 206 definiciones, 523 configuraciones, 415 pares definición ×
  implemento (el 2026-10-02 se retiró `sissy_squat__barbell`; las selecciones
  guardadas se remapean a `sissy_squat__smith_machine`; el 2026-10-03 se dieron de
  alta las cinco especialidades M1-M5, ver más abajo; el 2026-10-04 se retiraron
  las cuatro configuraciones unilaterales del rumano sumo). SHA-256 canónico compartido
  `1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099`.
- Fuente única de autoría: `curation/fichas/<familyId>.json` (una por familia,
  96). Se copia con `scripts/catalog_v2_apply_fichas.py`; el flujo completo y las
  reglas están en `EDITORIAL_GUIDE.md`. El gate falla si `source/` difiere de lo
  que producen las fichas.
- Estado de las fichas: 102 definiciones `LEGACY` y **104 `CURATED`**. Piloto
  (2026-10-01): `seal_row`, `pull_up` y `glutes_clamshells_banda`. Lote 1, pecho
  (2026-10-02, 20 definiciones, aplicado): `floor_press`, las aperturas
  (`decline_chest_fly`, `flat_chest_fly`, `incline_chest_fly`, `reverse_pec_fly`),
  los presses (`bench_press`, `decline_bench_press`, `incline_bench_press`,
  `paused_bench_press`) y los empujes de `upper_horizontal_push`. Lote 2,
  sentadillas (2026-10-03, 25 definiciones, aplicado; informe en
  `curation/lotes/LOTE_02_SENTADILLAS.md`). Lote 3, rodilla aislada (2026-10-03, 10
  definiciones, aplicado; informe en `curation/lotes/LOTE_03_RODILLA.md`). Con el
  OK del usuario: balón, sliders y GHR pasan al patrón `knee_flexion` (bloque
  `pattern` de sus fichas); la regla `joint.knee-in-knee-patterns` deja los dos
  patrones que quedaron sin uso (`knee_hip_extension` y `knee_hip_flexion`, que
  también salen de la ontología: quedan 62) y cubre `biarticular_lengthened`; y
  `CompositionTaxonomy.kt` cuenta el nórdico inverso como extensión de rodilla y no
  como flexión de codo. Lote 4, tirones (2026-10-03, 20 definiciones, aplicado; informe
  en `curation/lotes/LOTE_04_TIRONES.md`). Con el OK del usuario: el Kelso pasa al patrón
  nuevo `scapular_retraction` (la ontología queda en 63 patrones), los Y-Raises a
  `shoulder_abduction_diagonal` y el Band Pull-Apart a `horizontal_abduction`; cambian los
  principales de 9 configuraciones protegidas (entra el trapecio en los remos con agarre
  amplio, las dominadas escapulares y los Y-Raises, y sale del jalón) y el remo en banda pasa
  a tener el dorsal como dominante; `EmphasisEngine.kt` deja de etiquetar como anterior el
  deltoides de los tirones. Altas M1-M5 (2026-10-03, paquete D de la curaduría de programas,
  ya `CURATED`): `close_grip_bench_press`, `paused_back_squat`, `deadlift_to_knees`,
  `close_grip_lat_pulldown` e `incline_biceps_curl`, cada una una especialidad de una
  sola configuración (`close_grip_bench_press__barbell`, `paused_back_squat__barbell`,
  `deadlift_to_knees__barbell`, `close_grip_lat_pulldown__cable`,
  `incline_biceps_curl__dumbbells`), sin eje nuevo ni cambio de revisión; el cableado en
  recetas, soportes y remaps es un paso posterior. El paso a `CURATED` se hace por
  lotes y queda registrado aquí. Lote 5, bisagras (2026-10-04, aprobado y
  aplicado): 21 definiciones / 59 configuraciones; cuatro retiros autorizados
  del rumano sumo unilateral. `good_morning_seated` sigue `LEGACY`; la cola de
  32 imágenes está aprobada y pendiente de generación. Informe en
  `curation/lotes/LOTE_05_BISAGRAS.md`. La captura nativa fresca propia aprobó
  11 suites / 67 tests y el gate compartido filtrado aprobó 50 suites / 402 tests,
  ambos sin fallos, errores ni omitidos. Base completa 1 queda como corrida
  histórica fallida (6327 tests, 5 fallos, 3 omitidos). Las ejecuciones completas
  posteriores se siguen en el chat de programas; aquí no se declara aprobación
  global de Base. APK, instalación, launch y restauración tienen evidencia
  propia aprobada. Las cuatro fichas/selector siguen sin ejecutar: Setup
  incompleto y plan candidato aún no seleccionado, sin defecto demostrado;
  Programas C.P14 los comprobará al final integral.
- `editorial_briefs.json` y su copia `.bak.2026-08-08` se eliminaron: sus 201
  definiciones eran idénticas al contenido de las fichas esqueleto.
- Los 8 generadores por plantilla (`build_catalog_v2_*`, `curaduria_v3` a `v6`,
  `seed_catalog_editorial_briefs`) viven en `scripts/legacy/` y no corren sin
  una confirmación explícita (`_legacy_guard.py`).
- `quality_lexicon.json`, `quality_allowlist.json`, `anatomy_rules.json` y
  `QUALITY_BASELINE.md` pertenecen a `scripts/catalog_v2_quality_audit.py`
  (auditoría de solo lectura; el baseline es la medición **previa** a retirar los
  campos, por eso sigue midiendo textos que el catálogo ya no contiene).

### Campos retirados del esquema y prueba de no uso (F1)

Retirar significa: no se escribe, no se guarda en `source/`, no se compila en el
asset de runtime y no lo lee Android, iOS ni el backend. La lista única es
`scripts/catalog_v2_retired_fields.py` (24 rutas); el compilador, el gate, el
backend (copia literal, sincronizada por prueba) y la prueba de Android
`ExerciseCatalogRetiredKnowledgeTest` rechazan cualquier carga que las traiga.

Método de la prueba de no uso: (1) las líneas eliminadas por F1 en código de
producción (`git diff HEAD` sobre `android-native/app/src/main`,
`ios-native/KPKNFit/KPKNFit` y `backend/`) se clasificaron en declaraciones del
modelo y lecturas; (2) se buscó cada nombre de campo en el código de producción
que queda. Resultado por grupo:

| Campos retirados | Lectores de producción antes de F1 | Efecto visible | Decisión |
| --- | --- | --- | --- |
| `family.description`, `*.evidence.rationale` | Ninguno (solo validación de formato) | Ninguno | Retirado |
| `profile.benefits`, `techniqueSummary`, `variantRationale` | Validación del loader de Android y métricas de `AprendeCatalogAudit`; armado de `richMetadata.editorial`; validación del backend. Ninguna pantalla los muestra | Ninguno | Retirado |
| `profile.commonMistakes`, `richMetadata.coaching` | Android: `ExerciseCatalogV2LegacyAdapter` los copiaba a `ExerciseMuscleInfo.commonMistakes`, que solo agrega `ExerciseMatchEngine` para sugerir un ejercicio personalizado nuevo y se guarda sin mostrarse. iOS: lo mismo en `ExerciseDatabase` y `ExerciseMatchEngine` | Ninguno: ninguna pantalla muestra «errores comunes» del catálogo | Retirado |
| `profile.muscleNotes`, `jointInvolvement[].note` (y su espejo en `richMetadata.anatomy`) | Solo validadores (loader de Android, backend, gate) | Ninguno | Retirado |
| `richMetadata.editorial`, `richMetadata.safety` | Armado en `toRichMetadata` y validadores | Ninguno | Retirado |
| `anatomy.targetRegions`, `muscleLengthBias`, `stabilizationDemand`; `biomechanics.rangeOfMotion`, `relevantTendons` | Validadores; iOS copiaba `rangeOfMotion` a `peakTensionPoint`, que ningún código lee | Ninguno | Retirado |
| `programming.suitableRepRanges`, `recoveryCost`, `setupTransitionCost` | Solo validadores | Ninguno | Retirado |
| `programming.objectives`, `programming.splitSuitability` | iOS: alimentaban `functionalTransfer` y `sportsRelevance`, que lee `inferTransferLabel` (texto de justificación de ejercicios sustitutos) | **iOS**: sin esos campos la frase cae en la frase por región (antes eran 514 frases de plantilla distintas) | Retirado; cambio visible escalado al dueño del producto |

Campos que **se conservan** porque tienen lector: `programming.indicativeRestSeconds`
(iOS: asistente de sesión y analítica), `programming.requiredEquipment` (Android:
compatibilidad de planes), `programming.role` y `biomechanics.stability` (iOS:
proyección) y `replacement.preservesIntent` (Android: similitud entre ejercicios;
ahora se deriva como `<patrón>:<músculo primario>`).

Campos **sin lector pero conservados** a la espera de decisión del dueño del
producto: `anatomy.volumeContribution`, `programming.fatigueCost` y
`replacement.compatibleEquipmentIds`. Los espejos `anatomy.jointActions` y
`biomechanics.relevantJoints` también se conservan, pero ya se derivan de la
ficha y no se escriben a mano.

Compatibilidad con datos ya guardados en el teléfono: las instantáneas de
historial y el `catalogRichMetadataJson` persistidos antes de F1 contienen
claves retiradas; los decodificadores de Android las ignoran
(`ignoreUnknownKeys`), y `ExerciseCatalogV2LegacyPersistedDataTest` lo fija.

### Piloto de re-curaduría (F2, 2026-10-01)

Tres definiciones pasaron de `LEGACY` a `CURATED`, con ficha completa (`public`,
`technique`, `anatomy`, `visual`, `sources`), aplicadas y compiladas:

| Definición | Qué cambió | Evidencia |
| --- | --- | --- |
| `seal_row` | Se retiran los erectores como músculo de trabajo (el torso apoyado sostiene la columna) y se añade el antebrazo como estabilizador del agarre. La descripción deja de atribuir el tope de la barra a la variante con mancuernas. | Regla `rows.chest-supported-spine` |
| `pull_up` | El bíceps sube de estabilizador a secundario en todos los agarres, también en el supino: no pasa a principal, porque el dorsal sigue por encima. Entran pectoral, erectores y antebrazo como estabilizadores. Salen los romboides (sin medición) y el reparto inventado por ancho de agarre. | Youdas 2010 (PMID 21068680), Dickie 2017 (PMID 28011412). Regla `vertical-pull.elbow-flexors` |
| `glutes_clamshells_banda` | El glúteo medio queda principal por su función abductora, no porque domine el EMG, y el glúteo mayor queda secundario. Entran los flexores de cadera como secundarios (54 % frente a 33 % y 34 %). Sale la sacroilíaca: ninguna fuente la midió en la almeja. | Distefano 2009 (PMID 19574661), McBeth 2012 (PMID 22488226), Willcox 2013 (PMID 23485733). Regla `hip.abductors-gluteus-medius` |

Las 17 fuentes citadas tienen prueba offline en `curation/sources_verified.json`.
El 2026-10-02 el usuario confirmó tres criterios: `rows.chest-supported-spine`
(Remo Seal), `hip.abductors-gluteus-medius` (Almejas) y `grip.hanging-forearm`.
El 2026-10-03 confirmó también el de la Dominada (`vertical-pull.elbow-flexors`, bíceps
secundario con cualquier agarre).

### Re-curaduría por lotes (plan aprobado el 2026-10-02)

**Continuación 2026-10-04 — lote 5, bisagras:** aprobado por el usuario y
aplicado; informe en `lotes/LOTE_05_BISAGRAS.md`. Se incorporaron 21 definiciones,
59 configuraciones y 43 pares. El catálogo vigente tiene 523 configuraciones,
104 definiciones `CURATED` y 102 `LEGACY`, con SHA-256 canónico
`1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099`.
Lint conjunto: 0 errores y 0 avisos; 186 citas de
39 URLs verificadas. `good_morning_seated` sigue LEGACY porque la evidencia
no resuelve la dominancia dinámica propuesta; conservar el dato heredado no
lo valida científicamente. Las cuatro variantes unilaterales del rumano sumo
se retiraron por decisión expresa del usuario (registro separado al final).
El usuario aclaró que el exceso aportado por ejercicios donde ESE músculo es
secundario o estabilizador se permite; el extra donde es principal debe ajustarse.
Se conserva la anatomía según evidencia y se separan ambos aportes en el informe.
Esta decisión está registrada en R7 y en el manual de autoría. La coordinación
de programas incorporó al árbol actual un contador separado de todos los
PRIMARY por grupo para el techo de W2 y el ajustador, preservando el volumen
indirecto en los informes. Esta entrada no declara aprobado el impacto semanal.
La cola de 32 imágenes está aprobada, pendiente y sin imágenes generadas.
Revisión visual: 46 pares, 35 PNG vistos, 14 PASA, 21 FALLA y 11 SIN_IMAGEN;
los tres pares del sentado apartado se distinguen en `lotes/COLA_IMAGENES.md`.
Pytest tras el retiro: 206 tests y 160 subtests aprobados (baseline: 197/158).
Pytest tras aplicar el lote: 206 tests y 160 subtests aprobados en 112.68 s;
JUnit confirma 0 fallos/errores. La captura Android fresca de catálogo aprobó
11 suites / 67 tests, incluida ExerciseCatalogAuditTest; el gate compartido
filtrado de 50 suites aprobó 402 tests, sin fallos, errores ni omitidos, con
BUILD SUCCESSFUL en 9m49s. Los resultados 10 suites / 58 tests y 48 suites /
797 tests se conservan como históricos; cobertura FULL: 25650 casos, 0 violaciones.
Base completa 1 permanece histórica FAILED: 718 suites / 6327 tests, 5 fallos,
0 errores y 3 omitidos. Las ejecuciones completas posteriores se siguen en el
chat de programas; este checkpoint no declara aprobación global de Base.
APK BaseDebug SHA-256
`6237d204e2e1f8dd8cfd2259189e7b708b7fd477052fcd764501d7b31721fc72`,
instalación Success y MainActivity top-resumed tienen evidencia propia; la
restauración verificó 207 archivos y WAL idéntico, con la app detenida. Las
cuatro fichas/selector quedan NOT_RUN: configuración incompleta y plan candidato
aún no seleccionado; Continuar deshabilitado es esperado en ese estado y no
demuestra un defecto. Programas C.P14 comprobará el selector al FINAL integral
con 104 CURATED / 102 LEGACY.
Android y el APK consumieron el árbol compartido con catálogo SHA1c y WIP de
programas excluido de los commits de catálogo. La base SHA31cc tiene ocho
suites / 42 tests previos y coherencia privada; no se reconstruyó ni probó un
checkout aislado del commit intermedio. Los gates posteriores SHA1c no se
atribuyen a SHA31cc. El checkpoint final de 39 rutas requiere integración con
los commits de programas para la app completa. El alcance exacto y la evidencia
Git se conservan en `artifacts/catalog-lote05-20261004/approved_land/commit_state`.
Los resultados y sus pruebas están en
`artifacts/catalog-lote05-20261004/native_evidence.json`.

Las 178 definiciones `LEGACY` pasan a `CURATED` en diez lotes ordenados por gravedad:
2 sentadillas, 3 rodilla aislada, 4 tirones, 5 bisagras, 6 unilaterales, 7 glúteo y
pierna baja, 8 hombro, 9 bíceps, 10 tríceps, 11 core, cuello y antebrazo.

- **Ciclo de cada lote.**
  - Los autores trabajan en copias privadas (`catalog_v2_splice_fichas.py snapshot`
    con `--fichas-dir`).
  - Un revisor que no participó comprueba la anatomía contra los resúmenes de las
    fuentes y las imágenes existentes contra la ficha nueva.
  - Quien coordina copia el trabajo con `splice` y corre el lint de integración.
  - El usuario recibe el informe (`catalog_v2_lote_report.py`). Se aplica y se hace
    commit solo con su OK.
- **Decisiones del usuario.**
  - Se retira `sissy_squat__barbell`.
  - Patrones nuevos, que entran en el lote 4:
    - Y-Raises pasa a `shoulder_abduction_diagonal`.
    - `scapular_retraction` para el Kelso.
    - `forearm_rotation` para pronaciones y supinaciones: pasa al lote 11, porque
      `AprendeCatalogAuditTest` exige que la ontología liste solo patrones en uso.
  - Quien coordina puede añadir `allowedDifferences` de herencia, con razón y fuente,
    listadas en el informe del lote.
- **Línea base antes del lote 2.** El árbol tiene trabajo del wizard sin commit.
  - pytest de scripts y backend: 3 fallos previos, corregidos en la preparación.
    - Dos tests del brief visual asumían que `bench_press` era LEGACY.
    - El test de `reverse_pec_fly` exigía «polea» en la descripción de la definición,
      que por regla no se ata a un implemento. Ahora lo comprueba en la configuración
      de polea.
  - Android `testBaseDebugUnitTest`: 4270 pruebas, con 1 fallo ajeno al catálogo
    (`WorkoutSnapshotCommitTest`).
- **Fuera de alcance, documentado para después.**
  - `bodyRegion` LOWER en cuello, muñeca y agarre.
  - Nombres de familia autogenerados.
  - `programming.role` que contradice `articulationType`.
  - `searchTerms` de press que apuntan a otros ángulos.
  - `CompositionTaxonomy` asigna a lateral el deltoides del empuje horizontal.
  - Peso 0.25 de los estabilizadores en `WorkoutContextComponents.kt`.
  - `articulationType` MULTIARTICULAR en el nórdico, el nórdico inverso y el GHR, que con
    la cadera inmóvil son de una sola articulación (lote 3; necesita un script estructural).
  - Nombres con doble nombre entre paréntesis: «Curl Nórdico (Nordic Hamstring Curl)» y
    «Curl Nórdico Inverso (Reverse Nordic Curl)» (regla R6).
  - Los tres Pull Over son extensión del hombro con el codo fijo; la ontología no tiene ese
    patrón y siguen como `vertical_pull` (lote 4).
  - Familia de dominadas: `pull_up` (piloto) no lista romboides ni deltoides y la dominada
    en rack sí (lote 4).
  - Variantes nuevas anotadas: Kelso Shrugs con mancuernas, la versión más habitual (lote 4).

---


Fecha de corte: 2026-08-10 (curaduría editorial v7.2 — copy humano editorial)
Revisión: `v2-approved-2026-08-10-c`
Hash canónico compartido: `20ecd23cb4766c341236e09d336bf1c3d3db3041ec6d8b3dd568de124acc0aa5`

> Actualización de inventario — 2026-09-15: se retiraron las configuraciones
> `decline_bench_press__machine`, `decline_bench_press__cable`,
> `decline_chest_fly__machine` y `decline_chest_fly__cable`. La fuente y los
> artefactos runtime actuales contienen 96 familias, 195 definiciones y 511
> configuraciones; su SHA-256
> de artefacto es `d229f99ad5779d881cbf2f22d1d307d10d489a8b3bd747e0342b9d182dd95d6e`.
> El resto de este documento conserva el registro histórico del corte v7.2.

> Corrección de copy — 2026-09-27 (T-041-r4): la configuración
> `tren_superior_press_banda_resistencia__default` es band-only declarada
> (`equipmentId=band`, `requiredEquipment=["band"]`, descripción de definición
> "sin banco ni pesas"), pero su `techniqueSummary`, `variantRationale` y
> `setupCues` describían bandas atadas a una barra. Se alineó ese copy y sus
> espejos exactos con la identidad band-only ya declarada; no se cambió
> implemento, roles, acciones, calificaciones, programación ni identidad, ni se
> añadió o retiró ningún ejercicio. Gate global: 2652 fallos, sin cambios.

## Curaduría v7.2 (2026-08-10): estructura editorial humana aprobada

- Las 196 descripciones de definición se reescribieron a mano con la estructura
  aprobada por el dueño del producto: (1) introducción con el nombre del
  ejercicio, tipo de movimiento, músculos y marco editorial; (2) ejecución
  contada en tono editorial con el implemento principal integrado; (3)
  mención opcional de 1-2 alternativas con su efecto real y transición
  natural (sin listas robóticas de implementos); (4) veredicto dedicado.
- La descripción de definición abre con el nombre del ejercicio (R11 v7.2);
  las configuraciones siguen sin repetirlo y pasan a ser líneas de matiz:
  esencia del movimiento (escrita por ejercicio) + efecto real de los chips.
- Se relajaron dos gates: nombre canónico permitido en definiciones y formas
  reflexivas/descriptivas como "se ejecuta" permitidas (imperativos fuera).
- Verificación anti-reciclaje: 0 frases de 6+ palabras compartidas entre
  definiciones; 0 implementos mencionados que el ejercicio no tenga; 0
  imperativos; aperturas únicas en definiciones y configuraciones.

## Curaduría v7 (2026-08-10): copy humano editorial — descripciones reescritas

- `editorial_briefs.json` reescrito con calidad humana: 196 descripciones de
  definición con estructura de 3 frases (qué es, qué trabaja + implementos
  disponibles, veredicto dedicado) y 518 líneas de matiz por configuración
  (variante corta y dedicada por implemento/agarre). Se elimina el relleno
  "una diferencia concreta para repartir el esfuerzo durante la serie", los
  benefits duplicados y los veredictos reciclados.
- Cada descripción menciona al ejercicio sin repetir el nombre canónico, respeta
  los implementos reales de cada definición (82 con múltiples implementos) y
  aporta un veredicto tipo "gran constructor de..." dedicado y no genérico.
- El gate comprueba cobertura exacta, igualdad con el perfil, ausencia de
  boilerplate y unicidad de la primera frase (196 + 518 aperturas únicas).

## Curaduría v6 (2026-08-08): briefs dedicados por ejercicio y configuración

- `editorial_briefs.json` es la fuente autoral de las 196 definiciones y 518
  configuraciones. Cada opción tiene descripción, beneficios, técnica y
  justificación propios; no se deriva texto visible desde un patrón global.
- Se eliminó del flujo editorial la apertura repetida de los remos y de los
  demás patrones. El gate comprueba cobertura exacta, igualdad con el perfil,
  ausencia de boilerplate y unicidad de la primera frase.
- La pasada solo reemplaza copy y cues editoriales; conserva las fichas
  `muscleNotes` y `jointInvolvement` ya validadas.

## Curaduría v5.1 (2026-08-08): lectura accesible y tarjeta ordenada

- Se reescribieron las 196 descripciones de definición y las 518 fichas de
  configuración con frases más directas, sin repetir el nombre canónico dentro
  del cuerpo y sin presentar un simple cambio de implemento como una variante.
- Se normalizó la capitalización de descripciones, beneficios, técnica,
  músculos, articulaciones, acciones y etiquetas compactas.
- La tarjeta expandida ahora coloca primero los chips de opciones. Descripción,
  Técnica, Involucramiento Muscular e Involucramiento Articular son secciones
  independientes, cerradas por defecto y abiertas solo al tocarlas.
- El gate estricto bloquea nombres repetidos, textos visibles que comienzan en
  minúscula y revisiones de perfil desincronizadas.

## Curaduría v5 (2026-08-08): ficha específica por variante e involucramiento articular

- Las 518 configuraciones tienen una descripción editorial propia que combina
  el movimiento, el implemento, la posición o agarre seleccionado, el beneficio
  de esa variante y una técnica breve; ya no se describe la opción como un mero
  cambio de implemento.
- Cada configuración incorpora `benefits`, `techniqueSummary` y
  `variantRationale`, además de cues de preparación, ejecución y errores
  frecuentes adaptados a sus ejes técnicos.
- `muscleNotes` se reescribió con el rol, la acción muscular y la consecuencia
  concreta de la variante. El nuevo `jointInvolvement` registra articulación,
  rol (principal, secundaria o estabilizadora), acciones y explicación
  biomecánica; usa los IDs canónicos de WikiLab y se replica en metadata rica.
- La revisión v6 queda protegida por compilador, gate editorial, backend y
  loader Android; el runtime Android y la copia de datos iOS comparten el hash
  canónico indicado arriba.

## Curaduría v4 (2026-08-03): descripciones amigables, involucramiento adaptativo y ejercicios nuevos

- **Descripciones reescritas para el usuario final** en las 196 definiciones y
  518 configuraciones: texto cercano, con carácter y que invita a probar el
  ejercicio. Fuera la jerga biomecánica (bisagra, patrón, cadena) y las
  plantillas genéricas. Regla L10.
- **Involucramiento muscular adaptativo real por chips**: remos con agarre
  amplio → trapecio/espalda alta, cerrado → dorsal/bíceps; dominadas supinas →
  bíceps protagonista, pronadas/neutras → estabilizador. Regla L11.
- **Ejercicios nuevos**: Curl Martillo y Curl Invertido (Barra H, Mancuernas,
  Polea, Máquina, Banda) y la familia Rotaciones de Antebrazo (Supinaciones y
  Pronaciones con Mancuerna y Polea). Implemento nuevo `h_bar` ("Barra H") con
  label en Android.
- Conteos nuevos: **96 familias / 196 definiciones / 518 configuraciones**.
- Gate: la palabra "todo" ya no se trata como placeholder (falso positivo del
  español en las descripciones nuevas).

## Correcciones posteriores al corte (-c, 2026-08-03)

- Eliminado "Curl de Bíceps Declinado" (duplicado funcional del Curl Bayesian).
- Title Case corregido: "Flexiones de Brazos", "Curl de Bíceps en TRX",
  "Tate Press".
- Peso Muerto Rumano y Peso Muerto Rumano Sumo: mismo set de implementos que el
  Convencional (se quitó `machine`; regla L9).
- UI picker v2: la descripción queda solo arriba de los chips (se eliminó la
  duplicada debajo) y cuando hay ≤7 chips en total, todos los ejes comparten una
  sola fila horizontal en vez de una fila vacía por eje.
- Conteos nuevos: 192 definiciones / 504 configuraciones (antes de la v4).

## Resultado del corte

El catálogo quedó generado desde fuente editorial determinista y es el único
catálogo que se empaqueta como runtime Android. El corte contiene:

- **96 familias, 196 definiciones y 518 configuraciones** enumeradas.
- Curaduría integral v2 aplicada: reestructura de bisagras (Convencional, Sumo,
  Rumano, Rumano Sumo, Piernas Rígidas, Buenos Días), remos (Convencional,
  Pendlay, Barra T, Gironda, Pecho Apoyado), curls de isquiosurales (Sentado,
  Tumbado, De Pie + sliders/balón/nórdico), presses de banca por ángulo,
  pullovers (de pie ≠ en banca), jalón con lateralidad, dominadas con agarre y
  amplitud, sentadillas (barra alta/baja, frontal, sumo, búlgara, sissy, hack),
  zancadas por dirección, presses de hombro por postura, bíceps por postura
  (Curl Araña, Bayesian, Concentrado, Sentado, Predicador, Crucifijo, Superman,
  Drag), aperturas por ángulo, Hip Thrust, Reverse Hyper único,
  Aducciones/Abducciones de Pierna.
- Curaduría v3 (revisión -c) añade y corrige: barra de seguridad (`safety_bar`)
  en Buenos Días y sentadillas traseras; **glúteo medio** (`gluteus_medius`)
  como músculo diferenciado (se agrupa con "Glúteos" en el cálculo de volumen);
  Cruce de Poleas con altura de polea; Aperturas Inversas con Máquina Pec Deck,
  Polea y Mancuernas; elevaciones laterales/posteriores/frontales reorganizadas;
  Super ROM unificada; Extensión de Tríceps (polea alta/máquina/banda);
  Extensiones/Flexiones de Cuello fusionadas; elevación de talones
  (máquina/barra/smith/polea × lateralidad); dominadas con perfiles musculares
  adaptativos por agarre y amplitud; eliminación de duplicados (curl inclinado,
  press de hombros de pie, plancha Copenhagen isométrica, hiperextensiones
  redundantes, Super ROM duplicada) y renombres Title Case sin relleno.
- `muscleNotes` y `jointInvolvement` completas en las 518 configuraciones: una
  explicación por músculo y articulación, sin huérfanos ni faltantes, validada
  por compilador, gate, backend y loader Android.
- Equivalencias fijas por rol: Principal 1.0 / Secundario 0.5 / Estabilizador
  0.4; no se guardan números en el JSON, UI y contadores derivan del rol.
- Eje condicional `pulley_height` soportado en compilador, gate, backend y
  loader Android (obligatorio donde `implement=cable`; prohibido en el resto;
  en definiciones de polea fija como Cruce de Poleas el eje `implement` es
  implícito y queda exento del chequeo de singleton).
- Chips limitados a ejes declarados por cada padre; solo aparece el siguiente
  nivel compatible. No se generan productos cartesianos ni se mezclan
  revisiones, definiciones o configuraciones.
- Variantes que cambian el patrón o la demanda (déficit, Zercher, métodos
  nombrados, isometrías) permanecen como especialidades separadas.
- Cada configuración materializada aporta su propia descripción contextual
  (≥40 chars, no instruccional, distinta entre configs del mismo padre).

## Artefactos y paridad

- Fuente agregada: `source/catalog_v2.json` (canónica, reconstruida por
  `scripts/merge_catalog_v2_families.py` desde `source/families/`).
- Guía editorial: `curation/EDITORIAL_GUIDE.md` (reglas R1-R11 y L1-L12).
- Runtime Android: `android-native/app/src/main/assets/exercise_catalog_v2.json`.
- Runtime iOS: `ios-native/KPKNFit/KPKNFit/exercise_catalog_v2.json` (copia de
  datos idéntica; la paridad de código iOS queda pendiente por falta de
  toolchain Apple en esta máquina).
- Backend: `backend/exercises_catalog_v2.py` valida revisión, hash, estructura,
  metadata, identidad exacta, `muscleNotes`, `jointInvolvement` y la ficha
  editorial.
- El compilador y el verificador comparan los tres artefactos mediante el mismo
  hash canónico; cualquier divergencia hace fallar el proceso.

## Gates ejecutados (corte curado)

- `python scripts/catalog_v2_gate.py --strict` → `status=READY`.
- `python scripts/compile_exercise_catalog_v2_cli.py --check` → 196 definiciones,
  518 configuraciones, hash canónico coincidente.
- `python scripts/compile_exercise_catalog_v2_cli.py --write` → asset Android
  regenerado (y copia idéntica a iOS).
- Backend: pruebas Python del catálogo → `OK`.
- Android: `testBaseDebugUnitTest` y `testHealthDebugUnitTest` → 0 failures.

## Retiro autorizado del rumano sumo unilateral (2026-10-04)

El usuario pidió eliminar este ejercicio. Se retiran exclusivamente
`romanian_sumo_deadlift__unilateral__barbell`,
`romanian_sumo_deadlift__unilateral__smith_machine`,
`romanian_sumo_deadlift__unilateral__dumbbells` y
`romanian_sumo_deadlift__unilateral__hex_bar`. Las cuatro configuraciones
bilaterales y el default bilateral con barra permanecen. El eje `stance`
desaparece porque ya no ofrece elección; `laterality` del perfil conserva
`BILATERAL`. No cambian patrones, revisión, músculos ni reglas anatómicas.

Las selecciones guardadas migran explícitamente a
`romanian_deadlift__unilateral__<mismo implemento>`, actualizando definición,
configuración y perfil. Las parejas de identidad incorrectas siguen siendo
inválidas. Las sesiones conservan series, cargas, ocurrencia y modificadores.

El corte resultante contiene 206 definiciones, 523 configuraciones y 415 pares
de definición e implemento. Se aplicó mediante
`scripts/catalog_v2_retire_romanian_sumo_unilateral.py` con hashes comprobados;
la segunda aplicación produjo cero cambios. La integración privada se
sincronizó con el mismo retiro y solo se rebasa su entrada
`romanian_sumo_deadlift` tras probar equivalencia exacta con la ficha shared
original menos las cuatro retiradas. Evidencia en
`artifacts/catalog-lote05-20261004/retirement/`.

Merge, gate estricto, compiler write/check y pin/check pasaron. Las 19 pruebas
Python dirigidas de backend y retiro pasaron. Las pruebas Kotlin nuevas de
remap y contratos quedan pendientes del gate Android de integración; este
corte no ejecutó Gradle ni ADB.

## Regla de mantenimiento

Toda modificación futura debe regenerar fuente, runtime y hash en un solo corte,
ejecutar el gate estricto, las pruebas Android/backend y la inspección del APK.
No se admite reintroducir v1, aliases globales, resolución por nombre o chips
implícitos. Una definición sin metadata completa, configuración por defecto,
`muscleNotes`, `jointInvolvement` y la ficha editorial completas o decisión
editorial explícita debe bloquear el build.
