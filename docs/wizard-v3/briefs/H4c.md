# H4c · Importador: foto de la rutina, progreso del OCR y cargas en kg o lb (2026-10-11)

Worktree y rama: los que diga el mensaje de lanzamiento (sale de `feat/wizard-v3`, que ya trae H2a–H2c, H3b–H3e, H4a y H4b). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H2a.md`, `H2c.md`, `H3e.md` (la unidad de carga `loadUnit`) y `H4b.md` (el motor OCR).
Tareas Gradle `...Base...`. Español neutro, sin género gramatical. «Símbolos, no tarjetas».

## Objetivo
- H4b dejó el motor que lee PDF y fotos con OCR en el teléfono. La persona decidió el modelo empaquetado.
- H3e dejó la unidad de carga (`ImportedProgramDraft.loadUnit`, KG o LB); el constructor convierte las libras a kg al guardar.

Falta la interfaz:
- **elegir una foto o hacerla**;
- **ver el progreso** mientras se lee un PDF, con la opción de cancelar;
- **ver y cambiar la unidad de las cargas**.

## 1. Elegir cómo traer el programa (`ImportSourceChoices.kt`)
- **«Elegir archivo»:** la descripción pasa a «Elegir un archivo de Excel, CSV, Word, texto o PDF».
- **Tercer símbolo: «Foto de tu rutina»** (`import-source-photo`).
  - **Dibujo:** una cámara simple delante de una hoja con renglones. Usa el patrón de `ImportClipboardArt` (estático, lienzo de 64 × 64, `SymbolPen` y `SymbolStroke`) con el acento `SymbolPalette.columna`.
  - Al tocarlo, aparecen debajo dos acciones de texto (`ImportTextAction`):
    - **«Hacer una foto»** (`import-photo-camera`): `ActivityResultContracts.TakePicture` sobre un archivo de `cacheDir/import_photos/`. Añade a `res/xml/provider_paths.xml` un `cache-path` con `name="import_photos"` y `path="import_photos/"`. La foto se borra al terminar de leerla.
    - **«Elegir de la galería»** (`import-photo-gallery`): `ActivityResultContracts.PickVisualMedia(ImageOnly)`.
  - Sigue el patrón de las pantallas que ya usan estos contratos (`CompetitionWizardAlbumStep.kt`, `WorkoutSessionCockpit.kt`).
- **`readPickedFile`** pasa `cacheDir = context.cacheDir` y `onProgress` a `readSheets` (lo pidió H4b: sin `cacheDir`, un PDF que el proveedor no abre directamente falla).

## 2. Progreso y cancelación (los dos ViewModels de importación)
- **Estado:** `importProgress: String?` en `SetupWizardState` y `progress: String?` en `ImportProgramState`.
- **Textos:**
  - mientras se lee un PDF, «Leyendo la página N de M…»;
  - una foto, «Leyendo la foto…»;
  - el resto, «Leyendo el programa…», como hoy.
- **Interfaz:** el texto sustituye a la línea «Leyendo el programa…» de hoy, con la acción **«Cancelar»** (`import-cancel`). Cancelar corta el trabajo (`importJob?.cancel()`) y deja la pantalla como estaba, sin error.
- Los avisos de la lectura por OCR («Lo leímos de una imagen…») ya llegan como `SHEET_NOTE` y la revisión final obliga a mirarlos. No hagas nada más con ellos.

## 3. Unidad de las cargas
- **Operación:** `ImportEditOp.SetLoadUnit(unit)`, con el texto de deshacer «Cambiaste la unidad de las cargas a kg.» o «… a lb.». Solo cambia `loadUnit`, no los números.
- **Conmutador** en la vista previa, encima de las semanas, solo si algún ejercicio tiene carga:
  - el título «Las cargas de tu archivo están en», con `ImportSegmented` «kg | lb» (`import-load-unit`);
  - con lb, debajo y en `note`: «Se guardarán en kg (p. ej. 135 lb = 61 kg).», con el primer ejercicio con carga del borrador y la misma conversión que el constructor.
- **Mostrar la unidad:**
  - `importPrescriptionOf` escribe «135 lb» o «120 kg» según `loadUnit`;
  - en el panel de ajuste, el campo de carga se llama «Kilos» o «Libras»;
  - con lb, el rango del campo es de 1 a 2200, con la pista «Entre 1 y 2200 lb.».
- **El parche** sigue guardando el número tal cual, en la unidad del archivo. El constructor ya convierte.

## Pruebas
- **Robolectric:**
  - los tres símbolos, y las dos acciones de la foto al tocar «Foto de tu rutina»;
  - el progreso «Leyendo la página 2 de 5…» y «Cancelar» con un `readSheets` falso que avisa del progreso;
  - el conmutador kg/lb: aparece solo con cargas; cambiar a lb da «135 lb» en la fila y la nota de conversión; «Deshacer» vuelve a kg;
  - el panel de ajuste en lb: con 2300, la pista y «Guardar» desactivado.
- **Hoja de render** (`KPKN_RENDER_DIR=C:/kw/shots/v3/h4c`, a 411 dp y a 320 dp con letra al 130 %):
  - (a) los tres símbolos con la foto desplegada;
  - (b) la vista previa con el conmutador en lb y su nota;
  - (c) la línea de progreso con «Cancelar».
  - Máximo 3 rondas.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ImportSource*" --tests "*ImportEdits*" --tests "*ImportExerciseEditor*" --tests "*ImportWarnings*" --tests "*ImportStructure*" --tests "*ImportProgramScreen*" --tests "*ProgramImportStep*" --tests "*ImportActivation*" --tests "*OcrImportSource*"`
- Las hojas de render, aparte, con `KPKN_RENDER_DIR`.

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- El motor OCR, el analizador, el lector y el emparejador.
- Dependencias nuevas.
- `WizardUnitToggle` y `EntrenoTextAction`.
- `MainActivity.kt`.
- Push.

## Informe
- Firmas nuevas.
- Pruebas: número y resultado.
- Las rutas de los PNG y lo que corregiste en cada ronda.
