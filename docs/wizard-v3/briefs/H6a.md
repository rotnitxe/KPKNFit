# H6a · Importador: columnas de esquema, «Set n», «S · X · R» y listas de carga × repeticiones (2026-10-12)

Worktree `C:\kw\u2`, rama `v3/h6a` (sale de `feat/wizard-v3`, versión 42). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`,
`docs/IMPORTADOR_PROGRAMAS.md` (§2, §3 y §6) y `docs/wizard-v3/briefs/H5b.md`. Tareas Gradle `...Base...`. Español neutro.

## De dónde sale
Análisis de los 149 archivos de la colección que hoy dan NADA (`C:\kw\analysis\H6\nada.md`, con `familias_nada.csv`). Este paquete
cubre las familias B, C, E y H: la tabla tiene el programa, pero el analizador no lee las series o las repeticiones.
- **B (16):** la cabecera «Sets x Reps» se toma como columna de series. `tableRow` (`ProgramImportParser.kt:417`) lee «3 x 5» con
  `leadingInt` → 3 series y sin repeticiones → «sin repeticiones; se omite».
- **C (5):** `columnRole` (`:667`) da SETS a «Set 1», «Set Type» o «Fatigue Sets». Con «Set 1» = 270 sale «tiene 270 series».
- **E (3):** cabecera «S · X · R»: ni «S» ni «R» son roles.
- **H (3):** listas en una celda («95×3, 95×3, 100×3» o «105x1x5, 90x2x3») bajo una cabecera «Work-up (load × reps)» o
  «Prescription (load x reps x sets)». Hoy «95×3» se lee como 95 series.

Las vistas de cada archivo están en `C:\kw\import-corpus-report-coleccion-h5b\sheets\<ruta con _>.md` (sección «Vistas de hoja»).

## Cambios (todo en `ProgramImportParser.kt`)
### 1. Columna de esquema (rol nuevo `ColumnRole.SCHEME`)
- **Cabeceras** (compara normalizadas con `ExerciseMatchLexicon.normalize`, sin paréntesis): «Sets x Reps», «Sets×Reps», «Sets*Reps»,
  «Set x Rep», «Sets x Rep», «Sets/Reps», «Sets / Reps», «Series x Reps», «Series x Repeticiones», «Set Scheme», «Rep Scheme»,
  «Scheme», «Esquema», «Reps / Scheme», «Sets x Reps / How», «Sets x», «Rx», «Prescription», «Prescripción», «Target».
  - Mira cómo normaliza `normalize` la «×», el «*» y la «/» antes de escribir la lista; prueba cada forma.
  - Es una lista cerrada: «Last Set Rep Count», «Set 1 Reps» o «First Set RPE» NO son esquema.
- **Datos** (en `tableRow`):
  - La celda de esquema se lee con `parseNotation`: series, repeticiones (rango), AMRAP «+» y, si vienen dentro, carga, %, RPE y RIR.
  - Manda la columna de series o de repeticiones si existe y trae número, igual que hoy. El esquema rellena lo que falta.
  - **Rango de series** «3-4 x 6-8»: 3 series (el mínimo), repeticiones 6-8 y la nota «Series: 3-4.». Hoy la regex leería 4.
  - El texto que sigue a la notación va a notas: «5 x 5 (heavy, squeeze)» → nota «(heavy, squeeze)».
  - Si la celda no tiene notación («sets to 25 total reps», «Work up to a 3-5 rep max», «n/a»), la fila sigue como hoy.
- **Ejemplos** (archivo de la colección → resultado):

  | Archivo | Fila | Resultado |
  |---|---|---|
  | 029 brad_gillingham_raw_bench | «Bench - Top Single · 1 x 1 · 90% · 250» | 1×1, carga 250 |
  | 050 fierce_5 | «Squat · 3×5» | 3×5 |
  | 079 lyle_mcdonald…hypertrophy | «Squat · 3-4 x 6-8 · 185» | 3×6-8 y «Series: 3-4.» |
  | 044 dan_john_mass_made_simple | «Back Squat · 3 x 10 (build to bodywe…» | 3×10 con la nota |
  | 097 phat_workout_log | «Bench Press · Power Pushing · 3 sets of 3-5 · 70% · 70» | 3×3-5, 70 %, carga 70 |
  | 034 candito_linear | «Squat · 3 sets of 6 · 77.5% · 80» | 3×6 |

### 2. Columnas de registro (sin rol)
- Una cabecera con «done», «used», «actual», «log», «logged», «hecho», «hechas», «realizado», «realizadas», «usado», «registro», «fatigue»,
  «fatiga», «expected», «estimated» o «est» no tiene rol. Se comprueba ANTES que los demás roles.
- Ejemplos: «Weight Used», «Reps Done», «Done Wt», «Wt. Used», «ACTUAL», «Log / notes», «Fatigue %», «Fatigue Sets», «Est 1RM».
- Motivo: son columnas que rellena quien entrena. Hoy «Reps Done» se lleva el rol de repeticiones y tapa el esquema.

### 3. «Set n» y parecidos no son la columna de series
- **Sin rol:** la palabra de serie seguida de un número («Set 1», «Serie 2», «Set 1 Reps»), «Set #», «Set Type», «Top Set», «First Set …»
  y «Last Set …».
- **Singular y plural en la misma cabecera** («Lift · Set · Sets · Reps», archivo 122): «Sets» o «Series» es la columna de series y
  «Set» o «Serie» queda sin rol.
- **Lo demás sigue igual:** «Sets», «Series», «Series totales», «Wk 1 Sets» o «Sets:».
- **Nombre:** «Lift» y «Levantamiento» pasan a ser cabecera de nombre (`NAME_WORDS`), como «Exercise».

### 4. Cargas por serie en columnas «Set n» (archivo 106, RTS)
- **Cuándo:** la tabla no tiene columna de series y la fila tiene números en columnas «Set 1…Set k».
  - Si TODOS esos números son ≥ 20, son cargas.
  - Si alguno es < 20 (repeticiones o RIR), esta regla no se aplica y la fila sigue como hoy.
- **Resultado:**
  - series = cuántas celdas «Set n» traen número;
  - carga = la de la columna de carga, si la hay; si no, la mayor de esas celdas;
  - nota «Cargas por serie: 270, 285, 300.».
- **Ejemplo:** 106, cabecera «Exercise · Modifier · Reps · RPE · Fatigue % · Method · Top Set · Set 1 · Set 2 · Set 3 · …», fila
  «Squat · · 4 · 9 · 0% · · 300 · 270 · 285 · 300» → Squat 3×4, RPE 9, carga 300 y la nota.

### 5. Cabecera «S · X · R» y día delante de la cabecera (archivos 005, 014 y 021)
- **Roles:** en una fila con columna de nombre, tres celdas seguidas «S», «X» y «R» (una letra cada una, sin distinguir mayúsculas):
  - «S» es la columna de series;
  - «R» es la columna de repeticiones;
  - «X» queda sin rol.
- **Día delante de la cabecera:** si una cabecera de columnas trae una etiqueta de día (`isDayLabel`) a la izquierda de la columna de
  nombre («MONDAY · Exercise · · S · X · R · LOAD · %1RM»), esa etiqueta abre el día.
  - Es el ÚNICO cambio que se permite en `processRows`: dentro de la rama `header != null` (unas líneas 316-321), sin tocar nada más
    de esa función.
- **Ejemplo:** 005 → día «MONDAY» y «Competition Squat · 3 · x · 5 · 70 · 68%» → 3×5, carga 70, 68 %.

### 6. Listas de carga × repeticiones en una celda (archivos 084, 085 y russian_squat_routine)
- **Cabecera:** el texto entre paréntesis dice el orden. Mira la cabecera ORIGINAL: `columnRole` quita los paréntesis.
  - «(load × reps)», «(load x reps)», «(weight x reps)», «(wt x reps)», «(kg x reps)» o «(peso x reps)»: columna de lista
    «carga × repeticiones».
  - Si acaba en «x sets» («(load x reps x sets)», «(Weight x Reps x Sets)»): lista «carga × repeticiones × series».
  - La columna de lista cuenta como segundo rol, así que la fila es cabecera aunque no haya otro: «Exercise · Work-up (load × reps) · Log».
- **Celda:** elementos separados por comas.
  - «95×3, 95×3, 100×3, 100×3» (carga × reps): cada elemento es una serie. Los elementos seguidos e iguales se agrupan, y sale un
    ejercicio por grupo con el mismo nombre: 2×3 con 95 y 2×3 con 100.
  - «105x1x5, 90x2x3, 90x3x2» (carga × reps × series): un ejercicio por elemento: 5×1 con 105, 3×2 con 90 y 2×3 con 90.
  - Mismo convenio que los grupos de porcentaje: imita `percentGroups`, `groupLine` y `addGroup`.
- **Sin cambio:** una celda que no sigue la lista («reps: 10, 10, 10», texto) sigue como hoy.

## Fuera de este paquete (no lo toques)
- «Week N» con datos y los códigos «1-A» (familia K).
- Cabeceras de serie con su prescripción dentro («Set 1\n10 reps @ 50%», archivo 127).
- Tablas sin columna de nombre, semanas en columnas y pares peso · reps sin cabecera (familias D, F, G, I y J): van en otro paquete.
- Las calculadoras «3 @ · 121 · x 6» (familia A): las hace H6c a la vez, con un archivo nuevo y un enganche en `processRows`.

## Pruebas (escritas a mano)
- **Clase nueva:** `ProgramImportParserSchemeColumnsTest`, con hojas `ImportSheet` construidas en la prueba.
- **Un caso por regla,** con las filas de ejemplo de arriba y sus resultados.
- **Casos negativos:**
  - «Last Set Rep Count» no es series ni esquema;
  - «Series totales» sigue siendo series;
  - «Exercise · Sets · Reps» da lo mismo que hoy;
  - «Set 1 · Set 2» con 8 y 6 (repeticiones) da lo mismo que hoy;
  - una celda «reps: 10, 10, 10» bajo una cabecera de lista no inventa series.
- Las pruebas que ya existían de `*ProgramImportParser*` siguen en verde. Si alguna cambia porque la regla era la contraria, explícalo
  en el informe antes de tocarla.

## Bancos (compara archivo por archivo con `python -I -X utf8 C:/kw/tools/compare_import_reports.py <antes> <después>`)
- **Colección:**
  - comando: `KPKN_IMPORT_CORPUS=C:/Users/valen/Desktop/KPKN_Programas_Recoleccion_2026-10-11/01_originales KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-coleccion-h6a`;
  - antes: `C:/kw/import-corpus-report-coleccion-h5b`;
  - esperado: unos 25 archivos de B, C, E y H salen de NADA.
- **Corpus propio:**
  - comando: `KPKN_IMPORT_CORPUS=C:/kw/import-corpus KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h6a`;
  - antes: `C:/kw/import-corpus-report-h5b`.
- **Filas del teléfono:**
  - comando: `KPKN_OCR_ROWS=C:/kw/shots/v3/ocr-phone-2/ocr-report KPKN_IMPORT_REPORT=C:/kw/import-ocr-rows-report-h6a`;
  - antes: `C:/kw/import-ocr-rows-report-h5b`.
- **En los tres:**
  - ningún archivo BIEN empeora;
  - ningún archivo pierde ejercicios ni cambia de número de días sin una razón que expliques;
  - los riesgos ya señalados están en `C:\kw\analysis\H6\riesgo_bien.csv` (filas del paquete A).
- **Tope:** 3 rondas de ajuste. Si un archivo BIEN empeora y no lo resuelves en 3 rondas, deja la regla más estrecha y cuéntalo.

## Comando
`KPKN_IMPORT_CORPUS=… KPKN_IMPORT_REPORT=… bash /c/kw/tools/gradle_slot.sh /c/kw/u2/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ProgramImportParser*" --tests "*ImportCorpusReport*" --tests "*OcrRowsCorpus*" --tests "*KnownProgramRecognizer*" --tests "*ImportedProgramBuilder*" > /c/kw/logs/v3-h6a-<n>.log 2>&1`
- Un banco por ejecución, cambiando las variables. Sin variables, las pruebas de banco se saltan solas.
- Para la primera compilación y las pruebas unitarias, deja fuera los bancos.

## Commit
- Por rutas, con mensaje en español: `feat(importador): columnas de esquema, «Set n», «S · X · R» y listas de carga (H6a)`.
- La línea Co-Authored-By del BRIEF_COMUN.
- Nada de los corpus ni de los informes en el repo.

## Prohibido
- En `processRows`, todo salvo la rama `header != null` del punto 5. `parseSheets` y la parte de `DraftBuilder` de días y semanas:
  H6c engancha ahí a la vez.
- `ExerciseNameMatcher.kt`, `ImportExerciseAliases.kt` y `KnownProgramRecognizer.kt`: H6b va a la vez.
- La interfaz (`screens/`), los lectores (`data/importer/`), `OcrLayout` y `MainActivity.kt`.
- Cambiar los textos de los avisos que ya existían.
- Push.

## Informe
- Cada regla, con un ejemplo real y su resultado.
- Pruebas: número y resultado.
- Los tres bancos: totales antes y después, y la lista de archivos que cambian de resultado, de ejercicios o de días, cada uno con su
  motivo.
- Lo que no se pudo hacer y por qué.
