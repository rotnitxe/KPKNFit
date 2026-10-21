# H6c · Importador: calculadoras con bloques «3 @ · 121 · x 6» por semanas en columnas (2026-10-12)

Worktree `C:\kw\u3`, rama `v3/h6c` (sale de `feat/wizard-v3`, versión 42). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`,
`docs/IMPORTADOR_PROGRAMAS.md` (§2, §3 y §6) y `docs/wizard-v3/briefs/H6a.md` (va a la vez y toca el mismo archivo). Tareas Gradle
`...Base...`. Español neutro.

## De dónde sale
Análisis de los 149 archivos de la colección que hoy dan NADA (`C:\kw\analysis\H6\nada.md`, familia A). Son 18 calculadoras de
liftvault («The WeighTrainer»): el programa está entero, pero en bloques de 3 o 4 celdas bajo filas de semanas puestas en columnas.
- **Hoy:** ningún patrón casa, la fila se toma como prosa o «no se entiende» y el archivo sale sin ejercicios.
- **Vistas de cada archivo:** `C:\kw\import-corpus-report-coleccion-h5b\sheets\liftvault_xlsx_<nombre>.md`, en «Vistas de hoja».
- **Archivos:** 004, 006, 016, 018, 022, 037, 048, 049, 053, 055, 076, 077, 107, 123, 124, 125, 129 y 130 de
  `C:\kw\analysis\H6\familias_nada.csv`.

Ejemplo (004, «12 Week Bench Press Peaking Program»):

| Fila | A | B | C | D | E | F | G | H | I | J | K |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 4 | | | | | | 12 Week Bench Press Peaking Program | | | | | |
| 7 | Enter your current 1RM: | | | 200 | | | | | | | |
| 12 | | Week 1: | | | | Week 2: | | | | Week 3: | |
| 13 | 3 @ | 121 | x 6 | | 4 @ | 121 | x 8 | | 5 @ | 132 | x 8 |
| 15 | | Week 4: | | | | Week 5: | | | | Week 6: | |
| 16 | 5 @ | 143 | x 7 | | 5 @ | 154 | x 6 | | 5 @ | 165 | x 5-6 |

Variantes que hay en la colección:
- días por fila: «Monday: · 4 @ · 300 · x 5 · …» (124 y 125), o «Heavy day:» y «Light day:» (053, 076 y 077);
- **bloque con dos cargas** (018): «1 @ · 45 · 45 · x 8»; la segunda es la redondeada;
- **sufijo en la etiqueta de semana:**
  - «Week 1: A · Week 1: B» (016) y «Week 1: Light Day · Week 1: Heavy Day» (018) dan días;
  - «Week 4: unload» (130) es una nota de la semana;
- **varias semanas por banda:** «Weeks 1 & 2 · Weeks 3 & 4» (006);
- **varias filas de bloques** bajo una fila de semanas (006, 016, 055, 076): son varias series del mismo día;
- **series en rango:** «1-4 @», «5-6 @» (124);
- **unidad:**
  - «Rounding: 5», «Round to: · 5» o «Enter 2.5 for KG or 5 for LB · 5»: 5 es libras y 2,5 es kilos;
  - sin esa fila, la unidad la decide el borrador como hoy;
- **dos opciones** en la hoja (124): «Option 1:» y, más abajo, «Option 2:» con otra tabla desde la semana 1;
- el levantamiento solo aparece en el título («Bench Press», «Deadlift», «Squat»); en 8 archivos el título no lo dice
  («Classic Powerlifting Peaking Program», «Ed Coan Peaking Program»…).

## Diseño
### 1. Archivo nuevo `domain/training/importer/WeekBandBlocks.kt` (puro, `internal`)
- **Qué hace:** recibe las filas de una hoja (`List<List<String>>`) y devuelve un plan, o `null` si la hoja no es de este tipo. El
  contrato exacto del plan lo eliges tú.
- **El plan incluye:**
  - las filas de semanas;
  - los bloques de cada fila, con su semana o semanas, día, series, carga, repeticiones y notas;
  - el levantamiento;
  - la pista de unidad;
  - si se cortó en una segunda opción;
  - el 1RM de la hoja.
- **Cuándo hay plan:** hay al menos una fila de semanas con 2 o más etiquetas en celdas distintas Y al menos 2 bloques debajo.
- **Fila de semanas:** celdas que EMPIEZAN por semana y número.
  - Formatos: «Week 1:», «Week 1: A», «Week 1: Light Day», «Week 4: unload», «Weeks 1 & 2», «Weeks 1-2», «Semana 1:».
  - Sin distinguir mayúsculas, con o sin «:».
- **Bloque:** tres o cuatro celdas SEGUIDAS de la misma fila.
  1. «N @» o «N-M @» (series o rango de series).
  2. Una o dos celdas con un número (la carga). Con dos, vale la segunda.
  3. «x R», «x R-S» o «x R+» (repeticiones, rango o AMRAP), también con «×» y sin espacio.
  - Una celda con todo junto («3 @ 121 x 6» en una sola celda) NO es bloque: así no cambia ninguna hoja que ya funciona.
- **Semana de cada bloque:**
  - Se usa la fila de semanas más cercana por encima.
  - Dentro de ella, la etiqueta cuya columna cae dentro de las columnas del bloque; si ninguna cae dentro, la más cercana por la
    izquierda; si no hay ninguna a la izquierda, la primera.
  - «Weeks 1 & 2» da las dos semanas: el mismo bloque se crea en ambas.
- **Día de cada bloque:**
  1. la etiqueta de la fila, a la izquierda del primer bloque, sin «:» («Monday», «Weds», «Heavy day»);
  2. si la fila no tiene etiqueta, la de la fila de bloques anterior bajo la misma fila de semanas;
  3. si no hay ninguna, el sufijo de la etiqueta de semana cuando es una sola letra o contiene «day» o «día» («A», «Light Day»);
  4. si no, «Día 1».
  - Otro sufijo («unload») va como nota a los ejercicios de esa banda: «unload.».
- **Levantamiento:** recorre las filas de una sola celda por encima de la primera fila de semanas. En cada una busca la frase más
  larga de 1 a 4 palabras que reconoce `ImportExerciseAliases.lookup`. Gana la primera fila que la tenga.
  - Ejemplos: «12 Week Bench Press Peaking Program» → «Bench Press»; «Gene Bell Jr.'s Squat Peaking Program» → «Squat».
  - El nombre del ejercicio es la frase como está escrita en el título.
  - Si no hay ninguna: «Levantamiento principal». Comprueba con una prueba que `ExerciseNameMatcher` NO lo reconoce con confianza: debe
    quedar para «Elegir».
- **Pista de unidad:** una fila con «rounding», «round to» o «for kg or … for lb» y un número 5 (libras) o 2,5 (kilos).
- **1RM de la hoja:** «Enter your current 1RM: · 200» o «… 2RM …» → el número y si es 1RM o 2RM.
- **Segunda opción:** una fila que empieza por «Option 2», «Option 3», «Opción 2»… corta el plan: lo que va debajo no da bloques.

### 2. Enganche en `ProgramImportParser.kt` (mínimo; H6a toca otras funciones del mismo archivo a la vez)
- **En `processRows`:**
  - calcula el plan una vez, junto a `planSessions(rows)`;
  - en el bucle, justo después del bloque de `sessions[index]`, una rama para las filas del plan:
    - **fila de semanas:** se consume sin aviso;
    - **fila con bloques:** un ejercicio por bloque y semana en `builder.sessionDay(semana, día, context)` + `builder.addToDay(...)`;
    - **valores:** pasan por `validate(...)`, para usar los mismos rangos y avisos;
    - **carga:** cuenta con `context.noteLoad(null)`.
  - Con plan, las filas sin bloque que van por encima de la primera fila de semanas cuentan como etiqueta (`Noise.LABEL`), sin aviso:
    título, «To make your own copy», «The WeighTrainer», «Enter your current 1RM: · 200», «Projected new 1RM», «Rounding: · 5»,
    «Option 1:».
  - Las filas de debajo que no son del plan siguen el camino de siempre: «NOTE: Do warm-up sets…» es prosa, como hoy.
  - Lo que va desde «Option 2» cuenta como etiqueta.
- **En `DraftBuilder`:**
  - **`noteUnitHint(unit)`:** suma como una cabecera de carga en esa unidad (`kgMarks` o `lbHeaderMarks`).
  - **Opciones:** una marca como `cyclesStopped`, con su nota al cerrar la hoja: «Hoja «1974»: trae varias opciones; se leyó la primera.».
  - **Nota del 1RM** (solo si la hoja lo trae): «Hoja «12 Week»: las cargas salen de un 1RM de 200; cámbialas si el tuyo es otro.». Con
    2RM, «de un 2RM de 245».
  - **Nota sin levantamiento:** «Hoja «power2»: no dice qué levantamiento es; elige el ejercicio.».
- **Notas del ejercicio:**
  - rango de series «1-4 @» → 1 serie (el mínimo) y «Series: 1-4.»;
  - AMRAP con la nota de siempre (`amrapNote`).

## Resultados esperados (pruebas y banco)
| Archivo | Esperado |
|---|---|
| 004 | 12 semanas; cada una con «Día 1» y «Bench Press». Semana 1: 3×6 con 121. Semana 6: 5×5-6 con 165. Sin avisos SKIPPED_ROW. |
| 124 | 3 semanas con Monday, Weds y Friday; «Squat». Semana 1 Monday 4×5 con 300; semana 2 Monday 3×5 con 320. Nota de opciones. |
| 018 | carga 45, la segunda celda; unidad del archivo en libras («Round to: 5»). |
| 016 | semana 1 con los días «A» y «B», cada uno con sus filas en orden. |
| 006 | «Weeks 1 & 2»: los mismos ejercicios en las semanas 1 y 2; «Levantamiento principal» y la nota sin levantamiento. |

## Pruebas (escritas a mano)
- **Clase nueva:** `ProgramImportParserWeekBandsTest`, con hojas `ImportSheet` construidas en la prueba que copian los ejemplos.
- **Los cinco casos de la tabla.**
- **Casos negativos:**
  - una tabla «Exercise · Sets · Reps» normal da lo mismo que hoy;
  - una celda «Squat 3 @ 100 x 5» da lo mismo que hoy;
  - una fila «Week 1 · Week 2» sin bloques debajo da lo mismo que hoy.
- **Prueba del nombre:** «Levantamiento principal» no queda reconocido con confianza.
- Las pruebas que ya existían de `*ProgramImportParser*` siguen en verde.

## Bancos (compara archivo por archivo con `python -I -X utf8 C:/kw/tools/compare_import_reports.py <antes> <después>`)
- **Colección:**
  - comando: `KPKN_IMPORT_CORPUS=C:/Users/valen/Desktop/KPKN_Programas_Recoleccion_2026-10-11/01_originales KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-coleccion-h6c`;
  - antes: `C:/kw/import-corpus-report-coleccion-h5b`;
  - esperado: los 18 archivos salen de NADA (unos 11 BIEN y 7 POBRE) y **ningún otro archivo cambia**. La ruta es nueva: si cambia
    otro, es un fallo.
- **Corpus propio:**
  - comando: `KPKN_IMPORT_CORPUS=C:/kw/import-corpus KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h6c`;
  - antes: `C:/kw/import-corpus-report-h5b`;
  - esperado: sin cambios.
- **Filas del teléfono:**
  - comando: `KPKN_OCR_ROWS=C:/kw/shots/v3/ocr-phone-2/ocr-report KPKN_IMPORT_REPORT=C:/kw/import-ocr-rows-report-h6c`;
  - antes: `C:/kw/import-ocr-rows-report-h5b`;
  - esperado: sin cambios.
- **Tope:** 3 rondas de ajuste.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/u3/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ProgramImportParser*" --tests "*ImportCorpusReport*" --tests "*OcrRowsCorpus*" --tests "*ImportedProgramBuilder*" > /c/kw/logs/v3-h6c-<n>.log 2>&1`
- Con las variables del banco delante, un banco por ejecución. Sin variables, las pruebas de banco se saltan solas.

## Commit
- Por rutas, con mensaje en español: `feat(importador): calculadoras con bloques por semanas en columnas (H6c)`.
- La línea Co-Authored-By del BRIEF_COMUN.
- Nada de los corpus ni de los informes en el repo.

## Prohibido
- `columnRole`, `detectColumns`, `tableRow`, `nonExerciseKind`, `classify`, las listas de palabras de cabecera y la rama
  `header != null` de `processRows`: H6a va a la vez.
- Cambiar la visibilidad de funciones del analizador que ya existían: lo que necesites, cópialo en el archivo nuevo.
- `ExerciseNameMatcher.kt`, `ImportExerciseAliases.kt` y `KnownProgramRecognizer.kt`: H6b va a la vez.
- La interfaz, los lectores, `OcrLayout` y `MainActivity.kt`.
- Cambiar textos de avisos que ya existían.
- Push.

## Informe
- Qué hiciste, archivo por archivo, con el contrato del plan.
- Pruebas: número y resultado.
- Los tres bancos: totales antes y después, y los archivos que cambian, con lo que sale ahora en cada uno de los 18.
- Lo que no se pudo hacer y por qué.
