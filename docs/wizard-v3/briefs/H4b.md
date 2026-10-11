# H4b · Importador: leer PDF y fotos con OCR en el teléfono, el motor (2026-10-11)

Worktree y rama: los que diga el mensaje de lanzamiento (sale de `feat/wizard-v3` con H4a ya integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H4a.md` y `docs/wizard-v3/briefs/H3a.md`.
Tareas Gradle `...Base...`. Español neutro.

## Decisión de la persona usuaria
**«OCR en el teléfono».** KPKN dibuja cada página del PDF y lee el texto con ML Kit **sin conexión**, reconstruyendo las tablas por la posición del texto. Sirve también para fotos de rutinas impresas. Se acepta que la APK crezca unos 4 MB.
- La colección tiene 592 PDF (34 en español) en `C:\Users\valen\Desktop\KPKN_Programas_Recoleccion_2026-10-11\01_originales` (solo lectura).
- H4b hace el **motor**. La interfaz (botones de foto, progreso) es H4c.

## 1. Dependencia (`app/build.gradle.kts`; única línea que puedes añadir)
- `implementation("com.google.mlkit:text-recognition:16.0.1")`: modelo latino **empaquetado** en la APK, sin descargas ni red.
- **Si no resuelve:** prueba la versión estable anterior más cercana, y si tampoco, para y dilo en el informe.
- **Tamaño:** compara el tamaño de `app-base-debug.apk` antes y después (`:app:assembleBaseDebug`, una sola vez cada uno) y ponlo en el informe.

## 2. Reconstrucción de la tabla (nuevo `domain/training/importer/OcrLayout.kt`, puro y sin Android)
- **Entrada:** `OcrPage(width: Int, height: Int, words: List<OcrWord>)` con `OcrWord(text, left, top, right, bottom)`, es decir, los «elements» de ML Kit con su caja.
- **Salida:** `List<List<String>>`, filas con sus celdas, de todas las páginas en orden.
- **Filas:**
  - ordena las palabras por el centro vertical;
  - una palabra entra en la fila abierta si su centro está a menos de 0,5 × (altura mediana de palabra) del centro de la fila; si no, abre otra;
  - dentro de la fila, las palabras van de izquierda a derecha.
- **Celdas:** dentro de una fila, un hueco mayor que max(1,2 × altura mediana, 2,5 × ancho medio de carácter) abre una celda nueva. Las palabras de una celda se unen con espacio.
- **Columnas:**
  - junta los bordes izquierdos de las celdas de todas las filas de la página;
  - agrúpalos con una tolerancia del 2 % del ancho de página para sacar anclas de columna, contando solo las anclas que aparecen en al menos 3 filas;
  - pon cada celda en el ancla más cercana por la izquierda, con celdas vacías en medio. Así una tabla «Ejercicio | Series | Reps» sale alineada y el analizador encuentra la cabecera.
- **Páginas:** se concatenan sin fila vacía extra.
- **Ruido:** una palabra de un solo carácter que no sea letra ni cifra (p. ej. «|», «•» o «-») se descarta.
- **Pruebas** (`OcrLayoutTest`):
  - una tabla de 3 columnas con cabecera y 4 filas, con temblor vertical de ±3 px;
  - una celda de varias palabras («Press de banca inclinado»);
  - una fila con una celda vacía en medio;
  - dos páginas;
  - líneas sueltas de un día («Día 1 — Pierna») encima de la tabla;
  - los bordes de las anclas con temblor de ±1 % del ancho.

## 3. Fuentes OCR (nuevo `data/importer/ocr/`)
- **`TextRecognizerPort`** (interfaz): `suspend fun recognize(bitmap: Bitmap): List<OcrWord>`.
  - La implementación real usa `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`. Envuelve la `Task` con `suspendCancellableCoroutine`, sin la librería de coroutines de Play Services. Cierra el cliente al terminar.
  - Las pruebas inyectan una falsa.
- **`OcrImportSource.readPdf(contentResolver, uri, title, onProgress: (page: Int, pages: Int) -> Unit): List<ImportSheet>`:**
  - `PdfRenderer` con `openFileDescriptor(uri, "r")`. Si el proveedor no lo da, copia el archivo a `cacheDir` y bórralo después.
  - Dibuja cada página a 1600 px de ancho en ARGB_8888 sobre fondo blanco, una a la vez, y la recicla.
  - Pasa por `TextRecognizerPort` y luego por `OcrLayout`. Devuelve una sola `ImportSheet` con el título.
  - Hasta 30 páginas. Con más, se leen las 30 primeras y la hoja lleva la nota «Solo se leyeron las primeras 30 páginas.».
  - La hoja lleva siempre la nota «Lo leímos de una imagen: revisa los números antes de guardar.». Si H3d ya está en tu rama, las notas de hoja dan avisos `SHEET_NOTE` y la revisión final obliga a mirarlos. Si no, añade tú el enganche mínimo en `parseSheets`.
  - Un PDF protegido con contraseña o dañado → `ImportFileException("No pudimos abrir este PDF.")`.
- **`OcrImportSource.readImage(context, uri, title)`:** `InputImage`/bitmap desde la uri, respetando la rotación EXIF, con el lado largo escalado a como mucho 2000 px. Mismo `OcrLayout` y las mismas notas.
- **Todo en `Dispatchers.Default`/`IO`**, cancelable entre páginas.

## 4. Elección del lector (`ProgramImportSource.readSheets`, Android)
- **Topes por tipo:** se leen hasta 20 MB y se aplica el tope de cada tipo: PDF, 20 MB; imagen (JPEG, PNG, WebP o HEIC, por firma o por `image/*`), 15 MB; lo demás, 5 MB, como hoy, con su mensaje.
  - Hoy 14 PDF de la colección se rechazan por superar 5 MB antes de mirar el tipo.
- **`%PDF`** → `OcrImportSource.readPdf`. **Imagen** → `readImage`.
- `ImportFileReader` (puro) sigue diciendo que el PDF no se lee sin Android. El banco de pruebas JVM no puede hacer OCR.
- `PROGRAM_IMPORT_MIME_TYPES` añade `image/*`. El resto de la interfaz es H4c.
- `readSheets` gana un parámetro opcional `onProgress`, que se pasa al PDF. Los dos ViewModels de importación pueden seguir sin usarlo hasta H4c.

## 5. Arnés en el teléfono (solo `src/debug`)
- **`ImportOcrHarnessActivity`** (exportada solo en debug, como `WizardHarnessActivity`):
  - lee todos los `.pdf`, `.jpg`, `.jpeg` y `.png` de `getExternalFilesDir("ocr-corpus")`;
  - los pasa por el mismo camino que la app: `readPdf` o `readImage`, luego `ProgramImportParser.parseSheets` y luego el emparejador con el catálogo;
  - escribe en `getExternalFilesDir("ocr-report")`:
    - un `ocr-report.json` con, por archivo: páginas, ms de OCR por página, filas, semanas, días, ejercicios, nombres reconocidos y avisos por tipo;
    - un `<archivo>.rows.txt` con las filas reconstruidas, con celdas separadas por « | ».
- Muestra en pantalla «Listo: N archivos» y escribe en logcat con la etiqueta `KpknOcrHarness`.
- Lánzala con `adb shell am start -n com.example.kpkn.dbg/<paquete>.ImportOcrHarnessActivity`. La ejecuta el orquestador en el teléfono, no tú.

## Pruebas
- `OcrLayoutTest` (punto 2).
- **`OcrImportSourceTest`** (Robolectric si hace falta), con un `TextRecognizerPort` falso que devuelve palabras con cajas:
  - un PDF sintético de 2 páginas generado en la prueba con `android.graphics.pdf.PdfDocument`; si Robolectric no lo permite, prueba solo la imagen;
  - las notas «Lo leímos…» y «Solo se leyeron…» (con un falso de 31 páginas, si es viable);
  - la cancelación entre páginas.
- **Tamaños:** un PDF de 6 MB ya no se rechaza por tamaño; uno de 21 MB, sí.
- **Compilación de debug:** `:app:compileBaseDebugKotlin` incluye el arnés de `src/debug`.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*OcrLayout*" --tests "*OcrImportSource*" --tests "*ImportFileReader*" --tests "*ProgramImportParser*" --tests "*ImportProgramScreen*" --tests "*ProgramImportStep*"`
- Y `:app:assembleBaseDebug` antes y después de añadir la dependencia, para medir el tamaño.

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de la colección en el repo.

## Prohibido
- Otras dependencias. Tampoco la librería de coroutines de Play Services.
- Red: el OCR es solo local.
- La interfaz de selección y progreso (H4c).
- El analizador, salvo el enganche mínimo de notas si hace falta.
- `MainActivity.kt`.
- Push.

## Informe
- Firmas nuevas.
- Pruebas: número y resultado.
- El tamaño de la APK antes y después.
- Cómo lanzar el arnés.
- Los riesgos que veas, como memoria o rotación.
