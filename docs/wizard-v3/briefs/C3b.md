# C3b · Importador de programas (lectura y análisis, sin UI): Excel, CSV y texto pegado (punto 11)

Worktree indicado en tu encargo, rama `v3/c3b`. Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 11». Sin UI (la hace C3c).

## Contexto (verificado)
- No hay librería de Excel ni de CSV en el proyecto, ni IA. Hay un lector de líneas CSV propio para alimentos
  (`data/food/FoodImporter.kt:517`, `parseCsvLine`) y emparejadores de nombres de ejercicio: `domain/exercises/ExerciseMatchLexicon.kt`
  (normalización, sinónimos ES/EN, Levenshtein, `fuzzyMatch`, Jaccard), `domain/exercises/ExerciseMatchEngine.kt` (`findBestMatches`) y
  `domain/exercises/catalogv2/ExerciseCatalogV2Resolver.kt` (`search`, 46-152). El modelo `Program` es `@Serializable`
  (`data/models/Program.kt`: Macrocycle → Block → Mesocycle → ProgramWeek → Session → Exercise → ExerciseSet).
- Regla del proyecto: `domain/` sin `android.*`; la E/S de archivos va en `data/`.

## Qué hay que hacer
1. **`data/importer/XlsxReader.kt`**: lee un `.xlsx` desde un `InputStream` con `java.util.zip.ZipInputStream` y `javax.xml.parsers`
   (DOM; funciona en Android y en pruebas JVM): `xl/workbook.xml` (nombres y orden de hojas), `xl/_rels/workbook.xml.rels`,
   `xl/sharedStrings.xml` (cadenas compartidas, con texto enriquecido `<r><t>`), `xl/worksheets/sheetN.xml` (celdas `t="s"`, `t="inlineStr"`,
   `t="str"`, números y booleanos; referencia `r="B3"` → fila y columna; huecos vacíos). Devuelve `List<ImportSheet(name, rows: List<List<String>>)>`.
   Límites defensivos: 20 hojas, 2 000 filas y 40 columnas por hoja, 5 MB; si pasa, error claro.
2. **`data/importer/CsvReader.kt`**: CSV con detección de separador (`,` `;` tabulador) y comillas; reutiliza `parseCsvLine` si encaja (si no,
   uno propio pequeño). Devuelve una sola `ImportSheet`.
3. **`domain/training/importer/ProgramImportParser.kt`** (puro): de hojas o de texto libre a `ImportedProgramDraft`:
   - Modelo: `ImportedProgramDraft(title, weeks: List<ImportedWeek>)`, `ImportedWeek(index, days: List<ImportedDay>)`,
     `ImportedDay(name, exercises: List<ImportedExercise>)`,
     `ImportedExercise(rawName, sets, repsMin, repsMax, loadKg?, percent1Rm?, rpe?, rir?, restSeconds?, notes?)`, y `warnings: List<String>`.
   - Hojas: una hoja por día o por semana («Día 1», «Lunes», «Pierna», «Semana 2», «S2», «Week 2»…), o una sola hoja con filas‑encabezado de
     día o semana. Columnas por cabecera en español e inglés (Ejercicio/Exercise, Series/Sets, Reps/Repeticiones, Peso/Carga/Kg/Load, %, RPE,
     RIR, Descanso/Rest, Notas/Notes); sin cabecera, se intenta por contenido (texto + números).
   - Notación compacta en una celda o línea: «4x8», «4×8», «3x8-10», «5x5 @100kg», «3×10 RPE8», «4 series de 6», «3x12 @75%», «2' descanso».
   - Texto pegado: líneas de encabezado de día («Día 1 — Pierna», «Lunes:») y líneas de ejercicio («Sentadilla 4x6 @120kg»).
   - Lo que no se entienda va a `warnings` con la fila; nunca lanza excepción por contenido raro.
4. **`domain/training/importer/ExerciseNameMatcher.kt`**: para cada `rawName`, las 3 mejores configuraciones del catálogo v2 con una confianza
   0..1 (reutiliza los emparejadores existentes; normaliza mayúsculas y tildes). `isConfident` si la mejor ≥ 0,8 y saca ≥ 0,15 a la segunda.
5. **`domain/training/importer/ImportedProgramBuilder.kt`**: `build(draft, resolved: Map<rawName, configurationId?>, weekdays: List<Int>,
   programId: String): Program`.
   - Una semana por `ImportedWeek`: SIMPLE si hay una sola, COMPLEX con un bloque si hay varias.
   - Sesiones repartidas por los días de entreno dados.
   - Series con reps (o rango), kilos, %1RM, RPE/RIR y descanso.
   - Ejercicio sin resolver: se crea con su nombre libre, sin identidad de catálogo, como hace el editor con un ejercicio propio (búscalo).
   - Nombre del programa = título del archivo o «Mi programa importado»; marca su procedencia como importado (usa el campo de procedencia u
     origen que exista en `Program`; si no hay, `sourceDefinitionId = "imported"`) e indica en el informe qué campo usaste.

## Archivos permitidos
Nuevos en `data/importer/` y `domain/training/importer/`, y sus pruebas. No toques UI ni ViewModels.

## Pruebas (JVM, rápidas)
- Fixtures generados en la propia prueba (escribe un `.xlsx` mínimo con `ZipOutputStream`: workbook, rels, sharedStrings, dos hojas) y textos o
  CSV en `src/test/resources/import/`.
- `XlsxReaderTest`: cadenas compartidas, celdas vacías y números.
- `CsvReaderTest`: `;` y comillas.
- `ProgramImportParserTest`: hoja por día, una hoja con encabezados, texto pegado, notación compacta, avisos.
- `ExerciseNameMatcherTest`: «Sentadilla trasera» → sentadilla con barra; «Press banca» → `bench_press__barbell`; «Jalón al pecho» → jalón;
  una errata («sentadila») sigue encontrando.
- `ImportedProgramBuilderTest`: semanas, días, series y ejercicio sin resolver.
- Filtro: `*Xlsx*`, `*CsvReader*`, `*ProgramImport*`, `*ExerciseNameMatcher*`, `*ImportedProgramBuilder*`. Antes, `:app:compileBaseDebugKotlin`.
