# H3a · Importador: banco de pruebas con el corpus de la comunidad (2026-10-11)

Worktree `C:\kw\sb`, rama `v3/h3a` (sale de `feat/wizard-v3` con H1 ya integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`.
El build tiene flavors `base`/`health`: las tareas son `...Base...`. Español neutro, sin género gramatical.

## Objetivo
La persona descargó 118 hojas de programas de la comunidad (exportaciones xlsx de Google Sheets enlazadas desde LiftVault) en `C:\kw\import-corpus\<nnn-nombre>\<nnn-nombre>.xlsx`.
- **No son nuestras** (derechos de autor): no se copian al repositorio, ni enteras ni en trozos. Los fixtures del repo siguen siendo sintéticos.
- **Son datos no confiables:** solo los lee el código del importador (`XlsxReader`, que ya es defensivo); nunca se ejecutan ni se abren con otra herramienta.
- **Qué hace H3a:** un banco de pruebas que lee el corpus con el importador real y escribe un informe fuera del repo. Con ese informe se decidirán las mejoras del lector y del analizador (H3b, otro brief). H3a **no cambia** el importador.

## 1. La prueba (`src/test/java/com/example/kpkn/data/importer/ImportCorpusReportTest.kt`)
- **Activación:**
  - Solo corre si existe la variable de entorno `KPKN_IMPORT_CORPUS`. Sin ella, `Assume.assumeTrue(false)` y se salta: las ejecuciones normales y la integración no la ven.
  - `KPKN_IMPORT_REPORT` es la carpeta de salida y es obligatoria si hay corpus.
- **Recorrido:** todos los `*.xlsx` y `*.csv` bajo el corpus, recursivo y ordenado por ruta.
- **Para cada archivo:**
  1. **Lectura:** con `XlsxReader.read(FileInputStream)` o el lector CSV que ya usa la app, igual que en producción.
     - Un `ImportFileException` es un rechazo esperado (p. ej. «más de 20 hojas»): se anota su mensaje.
     - Cualquier otra excepción es un fallo: se anota la clase y el mensaje.
  2. **Análisis:** `ProgramImportParser.parseSheets(sheets, <nombre del archivo sin extensión>)`. Si lanza algo, es un fallo: el analizador promete no lanzar nunca.
  3. **Emparejado:** `ExerciseNameMatcher(catálogo).matchAll(nombres distintos)`.
     - Catálogo real con `ExerciseCatalogV2Loader.decodeApproved(File("src/main/assets/exercise_catalog_v2.json").readText())`, como `ExerciseNameMatcherTest`.
     - Se cuenta cuántos nombres salen `isConfident`.
  4. **Tiempos:** lectura y análisis, en ms.
- **Métricas por archivo:**
  - hojas (número y nombres) y filas totales;
  - semanas, días, ejercicios, nombres distintos y nombres reconocidos con confianza (número y %);
  - avisos por tipo (`SKIPPED_ROW`, `IGNORED_VALUE`, `EMPTY_DAY`, `NOTHING_FOUND`).
- **Resultado** (uno por archivo, en este orden de gravedad):
  - `FALLO`: excepción no esperada;
  - `RECHAZO`: `ImportFileException`;
  - `NADA`: 0 ejercicios;
  - `POBRE`: menos de 50 % de nombres reconocidos o más avisos `SKIPPED_ROW` que ejercicios;
  - `BIEN`: el resto.
- **Muestras** (para diseñar H3b sin abrir los archivos):
  - los primeros 15 ejercicios: semana, día, nombre, series × reps, kg, %, RPE, RIR, descanso y `sourceText`;
  - los primeros 15 avisos: tipo, `location`, `message` y `sourceText`;
  - **vista de cada hoja:** las primeras 25 filas no vacías × 16 columnas, como tabla Markdown. Cada celda va recortada a 24 caracteres, con `|` y saltos de línea escapados.

## 2. Salida (en `KPKN_IMPORT_REPORT`, nunca dentro del repo)
- `report.json`: todo lo anterior, legible por máquina.
- `report.md`:
  - **Resumen:** archivos por resultado, % medio de reconocimiento y los mensajes de rechazo agrupados con su número.
  - **Tabla:** una fila por archivo, ordenada por gravedad y luego por nombre.
  - **Nombres sin reconocer:** los 60 más frecuentes en todo el corpus, con cuántos archivos los usan. Es la lista para ampliar sinónimos.
- `sheets/<archivo>.md`: las vistas de hoja, las muestras y los avisos de ese archivo.
- **Al final:** si hubo algún `FALLO`, la prueba falla con la lista de archivos y excepciones, después de escribir el informe. Los rechazos y los resultados pobres no la hacen fallar.

## 3. Pruebas propias de H3a
Un corpus sintético mínimo **creado en la propia prueba** (en una carpeta temporal de JUnit `TemporaryFolder`). Lleva:
- un xlsx válido construido con `ZipOutputStream`, como hace `XlsxReaderTest`;
- un CSV;
- un archivo que no es xlsx;
- uno con 21 hojas.

Comprueba que:
- se escriben `report.json` y `report.md`;
- cada archivo sale con el resultado esperado;
- el recorte y el escapado de celdas funcionan.

La lógica del informe va en una clase interna en `src/test` (p. ej. `ImportCorpusReporter`), para poder probarla con el corpus sintético.

## Comandos
- **Pruebas normales:** `bash /c/kw/tools/gradle_slot.sh /c/kw/sb/android-native :app:testBaseDebugUnitTest --tests "*ImportCorpusReport*" --tests "*XlsxReader*" --tests "*CsvReader*"`. La prueba del corpus real se salta.
- **Informe real:** `KPKN_IMPORT_CORPUS=C:/kw/import-corpus KPKN_IMPORT_REPORT=C:/kw/import-corpus-report bash /c/kw/tools/gradle_slot.sh /c/kw/sb/android-native :app:testBaseDebugUnitTest --tests "*ImportCorpusReportTest*" --rerun`.
  - Comprueba que la variable llega a la JVM de pruebas. Si no llega, dilo en el informe, no toques el `build.gradle.kts`.
  - Ejecútalo una vez y deja el informe en `C:\kw\import-corpus-report`.

## Commit
- Por rutas, en `v3/h3a`, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Solo código de prueba: nada del corpus ni del informe.

## Prohibido
- Cambiar el importador (`data/importer/*`, `domain/training/importer/*`).
- Copiar al repositorio nada de `C:\kw\import-corpus`.
- Abrir los archivos con otra cosa que el importador.
- `MainActivity.kt`.
- Push.

## Informe
- Lo que añadiste y cómo se ejecuta.
- El resumen de `report.md`: archivos por resultado, reconocimiento medio y rechazos agrupados.
- Los 10 archivos peores, con una línea sobre la causa probable que ves en sus vistas de hoja.
