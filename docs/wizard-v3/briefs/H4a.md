# H4a · Importador: abrir también ods, docx, md y txt (2026-10-11)

Worktree y rama: los que diga el mensaje de lanzamiento (sale de `feat/wizard-v3`). Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y `docs/wizard-v3/briefs/H3a.md`.
Tareas Gradle `...Base...`. Español neutro. **Sin dependencias nuevas.**

## Por qué
La persona usuaria reunió una colección de 1.057 programas reales en `C:\Users\valen\Desktop\KPKN_Programas_Recoleccion_2026-10-11\01_originales` (solo lectura). Además de xlsx y csv, trae:
- **60 programas de Liftosaur en Markdown** (`liftosaur_texto/md`): el programa va en un bloque de código cercado con prosa alrededor.

```text
# Week 1
## Chest & Back 1
Bench Press / 4x8 / 135lb / 120s / progress: dp(5lb, 8, 12)
Incline Bench Press, Dumbbell / 3x10 / 30lb / 90s / progress: dp(5lb, 10, 12)
```

  En el archivo real, la marca de apertura es «```liftoscript».
- **9 docx** (`vertex42`, `joe_gallina`), **3 ods** (`vertex42`) y **2 doc** antiguos.
- **592 PDF.** Los hará H4b con OCR; aquí solo se detectan.

Hoy `ProgramImportSource.readSheets` trata un ZIP como xlsx y todo lo demás como CSV. Un docx, un ods o un PDF dan errores confusos.

## Cambios (`data/importer/`, sin Android salvo el punto 1)
1. **Lector puro de archivos** (`ImportFileReader.read(bytes: ByteArray, fileName: String, mime: String?): List<ImportSheet>`, sin Android):
   - Elige por la firma y, si no basta, por la extensión y el MIME:
     - **ZIP (PK):** si tiene `xl/workbook.xml` es **xlsx**; si su `mimetype` es `application/vnd.oasis.opendocument.spreadsheet` (o tiene `content.xml` con hojas de cálculo), **ods**; si tiene `word/document.xml`, **docx**. Cualquier otro ZIP: `ImportFileException("No es un archivo que sepamos leer. Usa .xlsx, .ods, .csv, .docx o texto.")`.
     - **OLE** (`D0 CF 11 E0 A1 B1 1A E1`): .xls o .doc antiguos. `ImportFileException("Es un formato antiguo de Office. Guárdalo como .xlsx o .docx y vuelve a intentarlo.")`.
     - **`%PDF`:** `ImportFileException("Para importar un PDF, copia su texto y usa «Pegar texto».")`. H4b lo cambiará.
     - **Texto:** con extensión .md, .markdown o .txt, el **lector de texto** (punto 4); con .csv o .tsv, o sin extensión conocida, `CsvReader`, como hoy.
   - `readSheets` (Android) pasa a llamar a `ImportFileReader`; el título y el tope de 5 MB siguen igual.
   - El selector (`PROGRAM_IMPORT_MIME_TYPES`) añade el ods (`application/vnd.oasis.opendocument.spreadsheet`), el docx (`application/vnd.openxmlformats-officedocument.wordprocessingml.document`), `text/markdown` y `application/pdf`. El PDF ya se elige para que salga su mensaje; H4b lo leerá.
2. **`OdsReader`** (ZIP más `content.xml`; SAX o DOM seguro, con las mismas protecciones que `XlsxReader`):
   - Cada `table:table` es una hoja con su `table:name`; las filas son `table:table-row` y las celdas `table:table-cell`.
   - **Repeticiones:** respeta `table:number-rows-repeated` y `table:number-columns-repeated`, pero solo hasta los topes de `ImportLimits`. Un «repetir 16 384 columnas vacías» no se expande.
   - **Valores:**
     - `office:value-type="percentage"` → valor × 100 + «%»;
     - `time` («PT0H2M0S») → «m:ss» o «h:mm:ss»;
     - `float` → `office:value` sin ceros sobrantes;
     - texto → los `text:p` unidos con espacio.
   - **Topes:** columnas, filas y hojas como en `XlsxReader`. Si H3d ya está integrado en tu rama, recorta con notas como él; si no, rechaza como hoy.
3. **`DocxReader`** (ZIP más `word/document.xml`):
   - Recorre el cuerpo en orden: cada párrafo `w:p` con texto es una fila de una celda (los `w:t` unidos); cada tabla `w:tbl` da sus filas `w:tr`, con una celda por `w:tc` (los párrafos de la celda unidos con espacio).
   - Todo va en una sola hoja con el nombre del archivo.
   - Las celdas combinadas en horizontal (`w:gridSpan`) ocupan una celda; las verticales (`w:vMerge`) dejan la celda vacía.
   - Topes de filas y columnas como en xlsx.
4. **Lector de texto** (`TextProgramReader`, para .md y .txt):
   - **Bloques de código:** si el texto tiene bloques cercados (```…```) y alguno contiene notación de series × repeticiones, SOLO se leen esos bloques. Si no, el texto entero.
   - **Tablas Markdown:** «| a | b |» → una fila con sus celdas. Las líneas separadoras («|---|:--:|») se ignoran.
   - **Encabezados:** «#», «##» o «###» → el texto sin almohadillas («# Week 1» → «Week 1»).
   - **Listas:** «- », «* », «+ » y «1. » se quitan al inicio.
   - **Énfasis:** se quita «**», «__», «*», «_» y «`» del texto.
   - **Barras:** en líneas que no son tabla, « / » separa celdas («Bench Press / 4x8 / 135lb» → tres celdas).
   - Cada línea no vacía es una fila. Va todo en una sola hoja con el nombre del archivo.
5. **Banco de pruebas** (`ImportCorpusReporter`, código de prueba):
   - Usa `ImportFileReader` en lugar de elegir el lector por su cuenta.
   - Lee también .ods, .docx, .md, .markdown, .txt y .pdf. El PDF sale como RECHAZO con su mensaje hasta H4b.
   - Los .doc y .xls antiguos salen como RECHAZO con su mensaje.

## Pruebas (archivos sintéticos construidos en la prueba con `ZipOutputStream` o texto; sin copiar nada de la colección)
- **ods:** dos hojas; celdas con repetición de filas y columnas; porcentaje y hora; y una fila con 16 384 columnas vacías repetidas, que no se expande.
- **docx:** párrafos «Día 1 — Pierna» y una tabla «Ejercicio | Series | Reps» con dos filas → la hoja tiene esas filas en orden.
- **md:** prosa con un bloque cercado de estilo Liftoscript (semana, dos días y tres líneas con « / ») → solo las filas del bloque; una tabla Markdown sin bloque → sus filas.
- **Firmas:** OLE → mensaje de formato antiguo; `%PDF` → mensaje de PDF; un ZIP cualquiera → mensaje de formato desconocido.
- **De extremo a extremo** (`ImportFileReader` y luego `ProgramImportParser.parseSheets`): un docx y un md dan los ejercicios esperados.
- **Colección real:** informe con `KPKN_IMPORT_CORPUS=C:/Users/valen/Desktop/KPKN_Programas_Recoleccion_2026-10-11/01_originales KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-coleccion-h4a` (comando de H3a). En tu informe pon los resultados de los md de Liftosaur, los docx y los ods.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ImportFileReader*" --tests "*OdsReader*" --tests "*DocxReader*" --tests "*TextProgramReader*" --tests "*XlsxReader*" --tests "*CsvReader*" --tests "*ImportCorpusReporter*" --tests "*ProgramImportParser*" --tests "*ImportProgramScreen*" --tests "*ProgramImportStep*"`

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de la colección ni de los informes en el repo.

## Prohibido
- Dependencias nuevas.
- El analizador (`ProgramImportParser`), el emparejador y el editor.
- La interfaz, salvo la lista de tipos del selector.
- `MainActivity.kt`.
- Push.

## Informe
- Los lectores nuevos y cómo elige `ImportFileReader`.
- Pruebas: número y resultado.
- Los resultados de la colección por formato (md, docx, ods, pdf y doc).
