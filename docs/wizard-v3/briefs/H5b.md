# H5b · Importador: nombres en español que no se reconocen, nombres con «o» y prosa de los PDF (2026-10-12)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`, versión 40). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`,
`docs/IMPORTADOR_PROGRAMAS.md` (§2 y §3) y `docs/wizard-v3/briefs/H3b.md`, `H4d.md` y `H4e.md`. Tareas Gradle `...Base...`. Español neutro.

## De dónde sale
Revisión visual en el teléfono con el PDF «Plan de entrenamiento, 4 días» de Scraler (OCR, 22 ejercicios):
- **Sin reconocer** (piden «Elegir»): «Remo con barra inclinado», «Gemelos de pie», «Gemelos sentado», «Zancada andando con mancuernas»,
  «Dominadas o jalón agarre cerrado», «Peso muerto con barra hexagonal o hip thrust», «Aductores o abductores en máquina» y
  «Fondoso extensión de tríceps sobre la cabeza» (el OCR leyó «Fondos o» como «Fondoso»).
- **Mal reconocido:**
  - «Remo sentado en polea» → «Remo Convencional», que es con barra;
  - con texto pegado, «Cable fly» → «Aperturas Planas», que es con mancuernas: se pierde el material.
- **Avisos de prosa:** 47 avisos, casi todos frases con cifras («Cómo leerlo: RPE 8 significa que al final de la serie…», «Regla de
  progresión: …»), que dan «sin series; se omite» o «no se entiende».

## Cambios
### 1. Alias (`ImportExerciseAliases`)
- **Frases que hay que añadir** (comprueba antes cuáles faltan):

  | Familia | Frases | Ejercicio |
  |---|---|---|
  | Gemelos de pie | «gemelos de pie», «elevación de talones de pie», «standing calf raise» | elevación de talones de pie |
  | Gemelos sentado | «gemelos sentado», «gemelos sentado en máquina», «seated calf raise» | elevación de talones sentado |
  | Zancada andando | «zancada andando», «zancadas andando», «zancadas caminando», «walking lunge(s)» | zancada caminando; si dice «con mancuernas», con mancuernas |
  | Remo con barra | «remo con barra inclinado», «remo inclinado con barra», «bent over row», «barbell row» | remo con barra inclinado |
  | Remo en polea | «remo sentado en polea», «remo en polea sentado», «seated cable row», «remo gironda» | remo sentado en polea |
  | Aperturas en polea | «aperturas en polea», «cruce de poleas», «cable fly», «cable crossover» | aperturas o cruce en polea, NUNCA mancuernas |
  | Tríceps sobre la cabeza | «extensión de tríceps sobre la cabeza», «overhead triceps extension» | extensión de tríceps por encima de la cabeza |
  | Peso muerto hexagonal | «peso muerto con barra hexagonal», «trap bar deadlift», «hex bar deadlift» | peso muerto con barra hexagonal |
  | Aductores | «aductores en máquina», «adductor machine» | aductores en máquina |
  | Abductores | «abductores en máquina», «abductor machine» | abductores en máquina |
  | Jalón cerrado | «jalón agarre cerrado», «close grip pulldown» | jalón al pecho con agarre cerrado |

- **Cómo:** el id de configuración se busca en el catálogo real (`ExerciseCatalogV2`).
  - Si el catálogo no tiene la variante, usa la más cercana del mismo patrón Y del mismo material.
  - Si tampoco hay ninguna así, no pongas alias y explícalo en el informe.

### 2. Material que no cuadra
- **Por qué «Remo sentado en polea» → «Remo Convencional»:** encuentra la causa.
- **Arreglo:** que una palabra de material del nombre («polea», «cable», «mancuernas», «barra», «máquina», «banda», «kettlebell») que el
  candidato no tiene baje su confianza por debajo de «reconocido».
  - Ya existe `MISSING_MATERIAL_PENALTY`: comprueba por qué no actúa aquí y amplíalo si hace falta.
- **Prueba:** «Cable fly» no se reconoce como un ejercicio con mancuernas.

### 3. Nombres con dos opciones
- **Regla:** «X o Y» / «X or Y» / «X u Y».
  - Si el nombre completo no se reconoce y X sí (con confianza), se reconoce X y el ejercicio lleva la nota «Alternativa: Y.».
  - Si X no se reconoce, todo sigue como hoy.
- **Ejemplos:**
  - «Dominadas o jalón agarre cerrado» → Dominadas, «Alternativa: jalón agarre cerrado.»;
  - «Peso muerto con barra hexagonal o hip thrust» → peso muerto con barra hexagonal, «Alternativa: hip thrust.».
- **Errores del OCR:** «Fondoso extensión…», con «o» pegada, no se arregla: no adivines palabras.
- **Dónde:** en el emparejador (`ExerciseNameMatcher`), o en el analizador si la nota tiene que ir al ejercicio. Elige lo más simple y
  explícalo.

### 4. Prosa con cifras en hojas de OCR (`ProgramImportParser`, solo con `pageStarts` no vacío)
- **Línea de texto:** cuenta como línea de texto (`Noise.TEXT`), no como aviso, una fila de una sola celda que cumple las tres cosas:
  - es una frase larga: 8 palabras o más, o 60 caracteres o más;
  - la mayoría de sus palabras van en minúscula;
  - no empieza por notación ni por un nombre reconocido.
- Ejemplos: «Cómo leerlo: RPE 8 significa que…», «Regla de progresión: cuando el cliente llega…», «Plantilla gratuita de Scraler…».
- Fuera de OCR no cambia nada.

## Pruebas (escritas a mano)
- **Alias:** un caso por familia de la tabla, con su id esperado; «Cable fly» no acaba en mancuernas.
- **Nombres con dos opciones:** reconocido con la nota; y no reconocido, como hoy.
- **Prosa:** hoja de OCR con prosa con cifras, sin avisos; la misma fila en una hoja normal avisa como hoy.
- Las pruebas que ya existían del emparejador y del analizador siguen en verde.

## Bancos (compara archivo por archivo: resultado, ejercicios y días)
- **Filas del teléfono:**
  - comando: `KPKN_OCR_ROWS=C:/kw/shots/v3/ocr-phone-2/ocr-report KPKN_IMPORT_REPORT=C:/kw/import-ocr-rows-report-h5b`;
  - comparar con `C:/kw/import-ocr-rows-report-h4e`;
  - esperado: menos SKIPPED_ROW y más nombres reconocidos en `06-scraler…`.
- **Corpus propio:**
  - comando: `KPKN_IMPORT_CORPUS=C:/kw/import-corpus KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h5b`;
  - comparar con `C:/kw/import-corpus-report-h4e`.
- **Colección:**
  - comando: `KPKN_IMPORT_CORPUS=C:/Users/valen/Desktop/KPKN_Programas_Recoleccion_2026-10-11/01_originales KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-coleccion-h5b`;
  - comparar con `C:/kw/import-corpus-report-coleccion-h4d`.
- **En los tres:**
  - ningún archivo empeora de resultado, pierde ejercicios ni cambia de número de días sin explicación;
  - el reconocimiento medio sube o se queda igual.
  - Lista los nombres que pasan de «sin reconocer» a «reconocido» con su ejercicio, para que yo revise que son correctos.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ExerciseNameMatcher*" --tests "*ImportExerciseAliases*" --tests "*ProgramImportParser*" --tests "*ImportedProgramBuilder*" --tests "*ImportCorpusReporter*" --tests "*OcrRowsCorpus*" --tests "*KnownProgramRecognizer*"`

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de los corpus en el repo.

## Prohibido
- La interfaz (`screens/`), los lectores, `OcrLayout` y `MainActivity.kt`: H5a va a la vez.
- `ExerciseMatchLexicon` (el del resto de la app).
- Cambiar los textos de los avisos que ya existían.
- Push.

## Informe
- Cada cambio, con un ejemplo.
- La causa de «Remo sentado en polea» → «Remo Convencional».
- Pruebas: número y resultado.
- Los tres bancos: antes y después, archivos que cambian y lista de nombres reconocidos nuevos.
