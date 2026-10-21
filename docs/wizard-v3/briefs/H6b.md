# H6b · Importador: nombres en inglés con notación, plurales y abreviaturas (propuesta v6) (2026-10-12)

Worktree `C:\kw\u8`, rama `v3/h6b` (sale de `feat/wizard-v3`). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`,
`docs/IMPORTADOR_PROGRAMAS.md` (§2 y §3), `docs/wizard-v3/briefs/H5b.md` (alias y material) y, ENTERO, el análisis
`C:\kw\analysis\H6\nombres.md`. Tareas Gradle `...Base...`.

## De dónde sale
El análisis de los nombres que el importador no reconoce (`C:\kw\analysis\H6\nombres.md`) encontró estas causas:
- **Búsqueda con AND:** el buscador exige que cada palabra exista en el catálogo en español, así que «up», «to», «db» o «sitting» lo
  rompen. 156 de 200 nombres no tienen ningún candidato.
- **Plurales ingleses:** «raises» → «rais», y «rows» o «dips» no se tocan.
- **Notación pegada:** «Barbell Curl 25», «[1-4]», «2ct», «ACC1».
- **Abreviaturas:** DB, BB, CG, SSB, Ext.
- **Cabeceras y prosa** tomadas por ejercicios.

La propuesta v6 se midió con un emulador en Python del emparejador real, que reproduce 280 de 280 candidatos:
- reconoce 337 nombres nuevos, sin ningún falso positivo entre los 95 que no deben reconocerse;
- no cambia ninguno de los 539 nombres que hoy se reconocen;
- el reconocimiento medio estimado de la colección pasa de 39,7 % a ≈54,7 %.

La referencia exacta de cada regla está en Python en `C:\kw\analysis\H6\nombres\` (`props.py`, `props4.py`, `props5.py`, `props6.py`,
`emu.py`, `evaluate.py`). Los resultados por configuración están en `eval_v6.json`, los nombres nuevos en `v6_newly.json` y la tabla de
200 en `annex_v6.txt`.

## Cambios
### 1. Las reglas v6 (§3 del análisis) en el emparejador del importador
- **Dónde:** en `domain/training/importer/ExerciseNameMatcher.kt`, o en un archivo nuevo del mismo paquete (p. ej.
  `ImportNameNormalizer.kt`) que el emparejador llama.
- **Lo que NO se toca:** el léxico compartido `ExerciseMatchLexicon` ni `ExerciseCatalogV2Resolver`.
- **Orden de las reglas:**
  - **0. Lo actual primero:** si el emparejador de hoy reconoce el nombre con confianza, su resultado no cambia. Las reglas solo actúan
    sobre lo que hoy queda sin reconocer.
  - **1. Notación:** se quitan cargas, series, repeticiones, rangos, %, RPE, «rm», AMRAP y EMOM, «[1-4]», SS1–3, códigos de bloque
    (ACC1 ACC, A1:, PD, PBA, PPS, PDS, main:) y restos de hoja (#REF!, #VALUE!). «2ct» o «3 ct» pasan a «pausa». Las palabras repetidas
    se colapsan.
  - **2. Abreviaturas:**
    - cg → agarre cerrado; wg → agarre ancho; dbs, bbs y kbs → db, bb y kb;
    - fuera «comp», «competition», «speed», «heavy», «light» y «heavier».
  - **3. Compuestos:** pushups, pullups, chinups y pull downs → push up, pull up, chin up y pulldown.
  - **4. Plurales, solo en las claves del importador:**
    - «-ises» y «-ges» pierden la «s»;
    - las palabras de 4 letras también (rows, dips, legs);
    - flyes y calves, como irregulares;
    - las reglas del español no cambian.
  - **5. Cabeceras y prosa sin candidatos:** días y semanas («3 day (Wednesday)», «1 Fri Heavy»), «tier», calentamientos, «used: none»,
    EMOM y AMRAP, RPE, nombres de programa y nombres de 9 palabras o más.
  - **6. Búsqueda traducida y con AND relajado:**
    - las palabras inglesas se traducen, también «sitting» → «sentado»;
    - las que el catálogo no conoce se quitan;
    - SOLO amplía candidatos.
  - **7. «X / Y»:** se acepta la primera opción solo si las dos se reconocen por separado. Con más de un «/», ninguna.
- **Guardas G1 y G3**, con la lista de palabras neutras del análisis:
  - con una guarda, no hay reconocimiento automático: la sugerencia se muestra con confianza ≤ 0,79;
  - los alias curados no pasan por las guardas.
- **Umbral de sugerencias:** no se muestran sugerencias con confianza < 0,3 (p. ej. «Pause Deadlift» → «monster walk», 0,09).

### 2. Alias nuevos (`ImportExerciseAliases.kt`)
- Los 29 del Anexo B del análisis, SIN «pull over» y CON «1 arm db row» → remo a una mano con mancuernas.
- El id y la configuración de cada uno se comprueban contra el catálogo real (`ExerciseCatalogV2`), como en H5b. Si alguno no existe,
  no se pone y se dice en el informe.

### 3. Decisiones ya tomadas (no las cambies)
- **«Pause/Paused Deadlift»:** sin reconocimiento automático (no hay variante con pausa). El déficit sí existe.
- **«Weighted …»:** solo sugerencia.
- **Grupos musculares** («Biceps», «Chest», «Back», «Abs», «Delts», «Calves» solo): no se reconocen. «Calves» puede quedar como
  sugerencia de elevación de talones.
- **Que el analizador no cree ejercicios con cabeceras** NO es de este paquete: el analizador lo tocan H6a y H6c a la vez.

### 4. Informe del banco con todos los nombres (`src/test/.../data/importer/ImportCorpusReportTest.kt` y su generador de informe)
- `unknownNames` vuelca TODOS los nombres no reconocidos, con su conteo, ordenados por conteo. Hoy solo van los 60 primeros.
- Si hace falta tocar una clase de informe fuera de las pruebas, dilo en el informe antes de hacerlo.

## Pruebas (escritas a mano, sin contenido del corpus)
- **Clase nueva:** `ExerciseNameMatcherEnglishTest`, con los casos de §6 del análisis:
  - plurales, notación, abreviaturas y códigos;
  - cabeceras sin sugerencias;
  - los negativos: «Pin Squat», «Pause Deadlift», «BTN Push-Press», «Seated Leg Press», «Biceps», «Chest», «Back», «Pulldowns / Pullups /
    Chinups», «Weighted Chin-ups», y que «Standing Tricep Extensions with Barbell» no sugiera la versión tumbada.
- **Alias:** un caso por cada alias nuevo.
- Las pruebas que ya existían siguen en verde: `ExerciseNameMatcherTest`, `ImportExerciseAliasesTest`, `ExerciseMatchLexiconTest`,
  `KnownProgramRecognizerTest` y `*ProgramImportParser*`.

## Bancos (compara archivo por archivo con `python -I -X utf8 C:/kw/tools/compare_import_reports.py <antes> <después>`)
- **Colección:**
  - comando: `KPKN_IMPORT_CORPUS=C:/Users/valen/Desktop/KPKN_Programas_Recoleccion_2026-10-11/01_originales KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-coleccion-h6b`;
  - antes: `C:/kw/import-corpus-report-coleccion-h5b`;
  - esperado: el reconocimiento medio sube unos 15 puntos.
- **Corpus propio:**
  - comando: `KPKN_IMPORT_CORPUS=C:/kw/import-corpus KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h6b`;
  - antes: `C:/kw/import-corpus-report-h5b`.
- **Filas del teléfono:**
  - comando: `KPKN_OCR_ROWS=C:/kw/shots/v3/ocr-phone-2/ocr-report KPKN_IMPORT_REPORT=C:/kw/import-ocr-rows-report-h6b`;
  - antes: `C:/kw/import-ocr-rows-report-h5b`.
- **En los tres:**
  - ningún nombre que antes se reconocía deja de reconocerse ni cambia de ejercicio;
  - ningún archivo empeora de resultado;
  - días y ejercicios iguales: este paquete no cambia el analizador.
  - Lista los nombres nuevos reconocidos con su ejercicio, para que el orquestador los revise, y marca los de confianza media.
- **Tope:** 3 rondas de ajuste. Si una regla da un falso positivo que no se arregla, deja la regla más estrecha.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/u8/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ExerciseNameMatcher*" --tests "*ImportExerciseAliases*" --tests "*ExerciseMatchLexicon*" --tests "*KnownProgramRecognizer*" --tests "*ProgramImportParser*" --tests "*ImportCorpusReport*" --tests "*OcrRowsCorpus*" > /c/kw/logs/v3-h6b-<n>.log 2>&1`
- Para los bancos, las variables delante; un banco por ejecución.

## Commit
- Por rutas: `feat(importador): nombres en inglés con notación, plurales y abreviaturas (H6b)`.
- La línea Co-Authored-By del BRIEF_COMUN.
- Nada de los corpus ni de los informes en el repo.

## Prohibido
- `ProgramImportParser.kt` y `WeekBandBlocks.kt`: H6a y H6c van a la vez.
- `ExerciseMatchLexicon` y `ExerciseCatalogV2Resolver` (los usa el resto de la app).
- La interfaz, los lectores y `MainActivity.kt`.
- Push.

## Informe
- Cada regla, con un ejemplo real y su resultado.
- Pruebas: número y resultado.
- Los tres bancos: antes y después (reconocimiento medio, BIEN/POBRE/NADA) y la lista de nombres nuevos reconocidos.
- Lo que no se pudo hacer y por qué.
