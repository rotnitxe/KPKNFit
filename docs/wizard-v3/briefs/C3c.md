# C3c · Importar un programa: pantalla de vista previa, emparejado y entradas en el alta y en Programas (punto 11, segunda parte)

Worktree indicado en tu encargo, rama `v3/c3c` (sale de `feat/wizard-v3` con C3a y C3b integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, del plan «Punto 11», y los briefs `C3a.md` (ruta y paso) y `C3b.md` (lector, analizador, emparejador y
constructor ya existen: `XlsxReader`, `CsvReader`, `ProgramImportParser`, `ExerciseNameMatcher`, `ImportedProgramBuilder`).

**API real que dejó C3a** (integrado): `ProgramSource { RECOMMEND, LATER, IMPORT }` e `internal object ProgramSourceOptions { const val
IMPORT_ENABLED = false }` en `EntrenoContracts.kt`; `SetupStepId.PROGRAM_SOURCE` y `PROGRAM_IMPORT`; `SetupStepContext.programImportEnabled`
(por defecto `IMPORT_ENABLED`; las pruebas lo pueden encender); `draft.programSource`; `withProgramSource(source?)` y `withProgramDeferred()` en
`SetupStepAnswers.kt`; `vm.setProgramSource(source)`; arte `programSourceArtOf(source, reduced)` en `design/entreno/ProgramSourceArts.kt`.
`PROGRAM_IMPORT` aún no tiene composable (rama `else -> Unit`) y su validación está preparada en `false`.

**Lo que dejó C3b** (integrado): `XlsxReader.read(input)`, `CsvReader.read(input, name)`/`parse(text, name)`, `ImportFileException`,
`ImportLimits`, `ImportSheet`, `ProgramImportParser.parseSheets(sheets, title)`/`parseText(text, title)` → `ImportedProgramDraft(title, weeks,
warnings)`, `ExerciseNameMatcher(catalog).match(rawName)`/`matchAll(names)` → `ExerciseNameMatch(rawName, candidates)` con `best` e
`isConfident`, `ImportedProgramBuilder.build(draft, resolved, weekdays, programId, catalog)`. Además, en este paquete:
- **Procedencia «importado»** (autorizado `data/models/` donde vive `PlanProvenanceClass` y donde se pinta su etiqueta, p. ej.
  `PlanDetailsSummary`): añade `PlanProvenanceClass.IMPORTED` y su etiqueta «Programa importado»; el constructor de C3b hoy usa `LEGACY`
  (se leería «Programa creado a mano»). Cámbialo a `IMPORTED`.
- **Días**: si el importado tiene más sesiones por semana que días de entreno elegidos, la vista previa lo dice («Tu programa tiene 5 sesiones
  por semana y elegiste 4 días») y ofrece «Cambiar días» (`vm.editStep(WEEKDAYS)`); no se activa con dos sesiones el mismo día.

## 1. Lectura de archivo (capa `data/`)
- `data/importer/ProgramImportSource.kt`: `suspend fun readSheets(contentResolver, uri, mime): Result<List<ImportSheet>>` en `Dispatchers.IO`
  (xlsx → `XlsxReader`, csv/texto → `CsvReader`; tope 5 MB; errores con texto claro: «El archivo es demasiado grande (máx. 5 MB).», «No pudimos
  leer este archivo. Prueba a exportarlo como .xlsx o .csv.»).
- Selector: `ActivityResultContracts.OpenDocument()` con los tipos `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
  `text/csv`, `text/comma-separated-values`, `text/plain` (patrón de uso en `screens/settings/SettingsScreen.kt:119`).

## 2. Estado de la importación (borrador del alta)
- En el borrador: `importedProgram: ImportedProgramDraft? = null`, `importResolutions: Map<String, String?> = emptyMap()` (nombre crudo →
  `configId`; null = dejarlo como ejercicio propio) y `importTitle: String = ""`.
- Setters al FINAL del ViewModel: `importFromSheets(sheets, title)`, `importFromText(text)`, `resolveImportedExercise(rawName, configId?)`,
  `clearImport()`. El análisis y el emparejado corren fuera del hilo principal (como el resto de trabajos del ViewModel).
- Validación de `PROGRAM_IMPORT` (la regla que C3a dejó en `false`): hay programa importado con al menos un día y un ejercicio, y ningún ejercicio
  «con dudas» sin decidir (los confiados se aceptan solos; los dudosos piden elegir o «Dejarlo como está»).
- Al activar con `programSource == IMPORT`: el programa sale de `ImportedProgramBuilder.build(...)` con los días de entreno del alta y el id del
  alta; la semana armada (`WEEK_LAYOUT`) reparte sus sesiones como hoy. Busca dónde se elige el programa a activar según la ruta y añade este caso.
- Enciende `ProgramSourceOptions.IMPORT_ENABLED = true`.

## 3. UI del paso `PROGRAM_IMPORT` (`entreno/EntrenoProgramImportStep.kt` + rama en `SetupTrainingSteps.kt`)
- Sin importar todavía: dos acciones grandes en símbolo (sin tarjetas): «Elegir archivo» (hoja de cálculo entrando al teléfono, el arte de C3a) y
  «Pegar texto» (portapapeles). «Pegar texto» despliega un campo multilínea (estilo de los campos del wizard) con ejemplo en gris:
  «Día 1 — Pierna / Sentadilla 4x6 @120kg / Prensa 3x10».
- Con un programa leído: título editable; por semana («Semana 1»…) y por día, la lista de ejercicios: `PatternGlyph` + nombre reconocido + «4 × 6
  · 120 kg»; a la derecha, un ✓ (reconocido) o la acción «Elegir» (dudoso). «Elegir» abre un panel de vidrio con las 3 sugerencias del
  emparejador, un campo de búsqueda en el catálogo (`ExerciseCatalogV2Resolver.search`) y «Dejarlo como está (ejercicio propio)».
- Avisos del analizador (`warnings`) en una lista corta y plegable: «3 filas no se entendieron» → al desplegar, cada fila.
- «Cambiar archivo» y «Empezar de nuevo» como acciones de texto. Movimiento reducido: sin despliegues animados.

## 4. Desde la app: Programas → «Nuevo programa» → «Importar»
- En `screens/programs/CreateProgramTemplateSheet.kt`, junto a «Desde cero», una entrada «Importar (Excel, CSV o texto)» que abre una pantalla
  nueva `screens/programs/import/ImportProgramScreen.kt` con la MISMA vista previa (extrae los composables comunes a
  `screens/programs/import/ImportPreview.kt` y úsalos también en el alta) y un botón «Guardar programa».
- Guardar usa el repositorio de programas de siempre (busca cómo guarda el editor un programa nuevo) y respeta los límites Free:
  `ProgramAccessPolicy.rejection(nivel, programa)`; si lo rechaza, el aviso «Los programas de más de una semana o de más de 5 sesiones son de KPKN
  Pro.» y no se guarda. Añade la ruta nueva en `navigation/Navigation.kt` siguiendo el patrón de las demás.

## Archivos permitidos
Nuevos `data/importer/ProgramImportSource.kt`, `entreno/EntrenoProgramImportStep.kt`, `screens/programs/import/*`; `SetupTrainingSteps.kt` (rama),
`SetupWizardModels.kt` (campos y validación), `SetupWizardViewModel.kt` (setters al final y el caso IMPORT de la activación),
`design/entreno/ProgramSourceArts.kt` (solo el interruptor), `CreateProgramTemplateSheet.kt` (solo la entrada), `navigation/Navigation.kt` (solo
la ruta), y pruebas.

## Pruebas
- Nueva `ProgramImportStepTest` (Compose): pegar texto muestra la vista previa con días y ejercicios; un dudoso bloquea el check hasta elegir;
  «Dejarlo como está» lo acepta; el título se edita.
- Nueva `ImportActivationTest` (Robolectric con base en memoria, como las de activación del alta): con un `.xlsx` de prueba (genéralo como en
  `XlsxReaderTest`), el programa activado tiene sus semanas, días y series, y los ejercicios propios conservan su nombre.
- Nueva `ImportProgramScreenTest`: en Free, un importado de 2 semanas no se guarda y muestra el aviso; en Pro, se guarda.
- Filtro: `*ProgramImport*`, `*ImportActivation*`, `*ImportProgramScreen*`, `*ProgramSource*`. Antes, `:app:compileDebugKotlin`.
