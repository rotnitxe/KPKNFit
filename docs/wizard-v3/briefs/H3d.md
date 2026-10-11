# H3d · Importador: un lector de Excel que recorta en vez de rechazar (2026-10-11)

Worktree y rama: los que diga el mensaje de lanzamiento (sale de `feat/wizard-v3` con H3c ya integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H3a.md` y `docs/wizard-v3/briefs/H3c.md`.
Tareas Gradle `...Base...`. Español neutro.

## Por qué
En el corpus de la comunidad, el lector (`data/importer/XlsxReader.kt`) rechazó **31 de 118 libros enteros** («La hoja «X» tiene más de 40 columnas» ×28, «El libro descomprimido es demasiado grande» ×3). Se pierden también sus hojas buenas. Además:
- **Lee las hojas ocultas** (ayudas de cálculo que la persona nunca ve).
- **Ignora el formato de celda:** un 65 % guardado como 0,65 llega como «0.65», y un descanso de 2:00 llega como 0,00139 (fracción de día).
- **Mete en memoria todas las entradas del ZIP** (imágenes, `calcChain.xml`…) y analiza cada hoja con DOM. En el teléfono, una hoja grande puede agotar la memoria.

## Cambios
1. **`ImportSheet`** (dominio) gana `notes: List<String> = emptyList()`: lo que el lector recortó u omitió de esa hoja.
   - El analizador convierte cada nota en un aviso `SHEET_NOTE` («Hoja «X»: <nota>»). Es un único enganche en `parseSheets`.
2. **Recortar, no rechazar** (`XlsxReader` y `CsvReader`):
   - más de `MAX_COLUMNS` (40) columnas: se leen las primeras 40, con la nota «Solo se leyeron las primeras 40 columnas.»;
   - más de `MAX_ROWS` (2000) filas: se leen las primeras 2000, con la nota «Solo se leyeron las primeras 2000 filas.»;
   - más de `MAX_SHEETS` (20) hojas **visibles**: se leen las 20 primeras. La primera hoja devuelta lleva la nota «El libro tiene N hojas; se leyeron las 20 primeras.».
   - Siguen siendo rechazo: el archivo de más de 5 MB, lo que no es un xlsx válido y los topes contra bombas ZIP (punto 4).
3. **Hojas ocultas:** las que tienen `state="hidden"` o `"veryHidden"` en `workbook.xml` no se devuelven ni cuentan para el tope de hojas.
4. **ZIP y memoria:**
   - Solo se guardan las entradas necesarias: `xl/workbook.xml`, `xl/_rels/workbook.xml.rels`, `xl/sharedStrings.xml`, `xl/styles.xml` y las hojas referenciadas.
   - El resto se lee y se descarta sin guardarlo, con su propio tope de bytes descomprimidos (128 MB) contra bombas ZIP.
   - El tope de lo que sí se guarda pasa a 32 MB.
   - **Antes de fijar los topes, mide:** en el banco de pruebas, para cada RECHAZO de hoy, lista las 5 entradas más grandes con su tamaño descomprimido. Pon esa lista en tu informe.
5. **Análisis en streaming:**
   - Las hojas y las cadenas compartidas se leen con SAX (`javax.xml.parsers.SAXParserFactory`, que existe en Android y en la JVM), no con DOM. Mantén la configuración segura: sin DTD ni entidades externas.
   - Al pasar de 2000 filas, deja de guardar celdas. Puedes cortar el análisis con una excepción privada.
   - `workbook.xml` y los `.rels`, que son pequeños, pueden seguir con DOM.
6. **Formato de número** (`xl/styles.xml`):
   - **Cómo se lee:** de `numFmts` (id propio → `formatCode`) y `cellXfs` (índice → `numFmtId`). El atributo `s` de la celda numérica elige el formato. Las celdas de texto, de fórmula con texto (`t="str"`) y las booleanas no cambian.
   - **Porcentaje** (ids 9 y 10, o un `formatCode` con «%» fuera de comillas): se lee como valor × 100 sin ceros sobrantes y con «%». 0,65 → «65%», 0,725 → «72.5%».
   - **Hora o duración** (ids 18–21 y 45–47, o un `formatCode` con `h` o `s`, o «mm:ss», sin `y` ni `d`):
     - los segundos son `round(valor × 86400)`;
     - con 3600 o más, se lee «h:mm:ss»; si no, «m:ss». Ejemplo: 0,00138889 → «2:00».
   - **Fechas y lo demás:** como hoy.
7. **Sin cambios:** la API pública (`XlsxReader.read`) y los mensajes de error que siguen existiendo.
   - Las pruebas que fijaban los rechazos por columnas, filas u hojas cambian a «recorta con nota»; lista cuáles en el informe.

## Pruebas (`XlsxReaderTest`, `CsvReaderTest`; xlsx sintéticos construidos con `ZipOutputStream`)
- 45 columnas: quedan 40, con la nota.
- 2100 filas: quedan 2000, con la nota.
- 25 hojas visibles más 3 ocultas: quedan 20, sin las ocultas, con la nota.
- Una hoja oculta con datos no aparece.
- Porcentaje (id 9 y formato propio «0.0%») y hora (id 21 y formato propio «m:ss»).
- Una entrada `xl/media/image1.png` de 40 MB descomprimidos no impide leer el libro.
- Una bomba ZIP (una entrada muy comprimible de más de 128 MB) se rechaza sin agotar la memoria.
- Una hoja de 60 000 celdas se lee en menos de 3 s en la JVM.
- El enganche del analizador: una hoja con notas da avisos `SHEET_NOTE`.
- **Corpus real:** informe con `KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h3d` (comando de H3a). Pon en tu informe el antes y el después de los RECHAZO y de los resultados por archivo.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*XlsxReader*" --tests "*CsvReader*" --tests "*ProgramImportParser*" --tests "*ImportCorpusReporter*" --tests "*ImportProgramScreen*" --tests "*ProgramImportStep*" --tests "*DomainHasNoAndroidImports*"`

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada del corpus ni de los informes en el repo.

## Prohibido
- El emparejador, las reglas del analizador (salvo el enganche de las notas) y la interfaz.
- Dependencias nuevas.
- `MainActivity.kt`.
- Push.

## Informe
- Las entradas más grandes de los rechazos de hoy y los topes que fijaste.
- Pruebas: número y resultado.
- El antes y el después del corpus.
- Las pruebas existentes que cambiaste, y por qué.
