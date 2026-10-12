# Importador de programas (oct-2026)

Cómo KPKN lee un programa ajeno (Excel, CSV, Word, texto, PDF o foto) y cómo la persona lo deja perfecto antes de guardarlo. Se usa en el
alta (`PROGRAM_SOURCE` → «Importar mi programa», paso `PROGRAM_IMPORT`) y en Programas → «Nuevo» → «Importar»: las dos entradas
comparten la misma pantalla. Los briefs con el porqué de cada regla son `docs/wizard-v3/briefs/H1.md` a `H4d.md`.

## 1. De dónde sale el programa

| Fuente | Formatos | Lector (`data/importer/`) | Límites (`ImportLimits`) |
|---|---|---|---|
| «Elegir archivo» | `.xlsx`, `.ods`, `.csv`, `.docx`, `.txt`, `.md` y `.pdf` | `ImportFileReader` decide por la firma del archivo, no por la extensión: `XlsxReader`, `OdsReader`, `DocxReader`, `CsvReader`, `TextProgramReader` y, para PDF, OCR | 5 MB por archivo (20 MB si es PDF); 32 MB descomprimidos; 20 hojas, 2000 filas y 40 columnas por hoja |
| «Pegar texto» | texto libre, tablas de Markdown y bloques de Liftoscript | `TextProgramReader` | — |
| «Foto de tu rutina» | cámara o galería | OCR (`data/importer/ocr/`) | 15 MB por imagen; 2000 px por lado |

- **Excel**: lectura SAX (sin cargar el libro entero), sin las hojas ocultas, con los formatos de % y de tiempo. Si recorta filas, columnas
  u hojas, la hoja lleva una nota («Solo se leyeron las primeras 2000 filas.»).
- **Formatos antiguos** (`.xls`, `.doc`, OLE): se rechazan con un mensaje que dice cómo guardarlos en un formato nuevo.
- **OCR**: ML Kit `text-recognition` empaquetado en el APK (decisión del usuario: funciona sin conexión y sin descargas; unos +20 MB en
  release).
  - El PDF se pinta con `PdfRenderer` a 1600 px por página, hasta 30 páginas, con el progreso «Leyendo la página 3 de 12…».
  - `OcrLayout.reconstruct` rehace la tabla: filas por el centro vertical de cada línea, celdas por los huecos y columnas alineadas con las
    de las filas vecinas.
  - Antes de unir las páginas se quitan los «muebles» (`OcrLayout.joinPages`): cabeceras de marca y pies con número de página.
    - Un mueble es una de las 2 primeras o de las 2 últimas filas de su página que, sin cifras ni signos, se repite en el 40 % de las
      páginas o más (mínimo 3).
    - Nunca lo es una fila con contenido (`ProgramImportParser.pageContentRows`): semana, día o cabecera de columnas, notación o %,
      datos de tabla, el nombre de una prescripción suelta o un título justo encima de ejercicios. Un programa semanal con las mismas
      filas en cada página no pierde nada.
  - La hoja guarda dónde empieza cada página (`ImportSheet.pageStarts`; lista no vacía = hoja de OCR).
  - Todo lo leído por OCR lleva la nota «Lo leímos de una imagen: revisa los números antes de guardar.».

Cada lector entrega `ImportSheet(name, rows, notes)`: filas de celdas de texto, sin interpretar.

## 2. Del texto al programa

`ProgramImportParser` (puro, `domain/training/importer/`) convierte las hojas en un `ImportedProgramDraft` (semanas → días → ejercicios,
cada uno con id estable `w<s>-d<n>-e<m>` y su texto de origen) más una lista de avisos (`ImportWarning`). Después `ImportedProgramBuilder` lo
convierte en un `Program` con procedencia «Programa importado».

### Reglas del analizador (un ejemplo de cada una)

**Estructura**
- Días por etiqueta, en español y en inglés: «Día 1», «Lunes», «Pierna», «Day A», «Upper», «Push».
- Solo en PDF y fotos (hojas con `pageStarts`):
  - la misma etiqueta de día en la cabecera de una página (sus 3 primeras filas) continúa el día abierto: «DÍA 1» en cada página es un
    solo día;
  - un texto corto que podría ser un día no cambia un día con nombre ya abierto: cuenta como línea de texto.
  - Fuera de OCR no se aplican: en una hoja, un día repetido abre otro (así no se mezclan semanas en una sesión).
- Semanas: por hoja («Semana 2», «Week 3»), por fila o por grupos de columnas («Week 1 | | | Week 2 | …»).
- Sesiones en columnas (tablas tipo HST o PPL de Reddit) y cuadrícula de días de la semana.
- Columna DÍA en una tabla plana: cada valor distinto abre un día.
- Una serie por fila (familia 5/3/1): «Set 1 | 65% | 80», «Set 3 (AMRAP)», «BBB Set 1 (10 reps)» forman un ejercicio por semana con el nombre
  del levantamiento de la pestaña; solo se lee el primer ciclo, con la nota «se leyó el primer ciclo».
- Hojas de instrucciones («Instructions», «Léeme», «How to use»…) y prosa: no dan ejercicios; la prosa se resume en un aviso.
- Calentamiento («Warm-up», «Calentamiento») y etiquetas de tabla: fuera.

**Notación**
- Compacta: «4x8 @80kg», «3×10 RPE8», «5x5 @ 75%», «70%x3x2», «70/3/2».
- Larga, en inglés y en español: «3 sets of 8», «2 sets x 10 reps», «3 series de 10 repeticiones», también entre paréntesis tras el nombre.
- Delante del nombre: «4x8 Sentadilla».
- Nombre y prescripción en líneas distintas (texto pegado y OCR): «Sentadilla» / «4x8» = un ejercicio (busca el nombre hasta 3 filas atrás,
  saltando etiquetas de día).
- Nombres sueltos sin prescripción (sin cifras): se cuentan como líneas de texto de la hoja y solo avisan uno a uno si la hoja no da
  ningún ejercicio. Una fila con cifras que no se entiende («Press 4/8») sí avisa, para poder «Añadir como ejercicio».
- AMRAP: «5+», «AMRAP» → la última serie lleva la nota AMRAP.
- Pirámide: «4x 15-12-10-8» o «12/10/8/6» → rango 8–15 y la nota «Pirámide: 15-12-10-8.».
  - Hacen falta 3 cifras o más («2x10-12» es un rango) y ninguna con cero delante.
  - Sin series, la lista tiene que ir en un solo sentido: así una fecha como «06/03/15» no es una pirámide.
- Por lado: «8/leg», «10 each side», «12 por pierna», «10 c/l» → 8 repeticiones y la nota «Por lado.».

**Cargas**
- Unidad del archivo (`loadUnit`): libras si el archivo lo dice («lb», «lbs»). Rango válido: 0,5–1000 kg o 1–2200 lb; fuera de él la carga
  se descarta con un aviso. Al guardar, las libras pasan a kg (× 0,45359237, redondeado a 0,5).
- Una carga 0 se queda vacía (peso corporal o por decidir).

### Avisos

`ImportWarningKind`: `SKIPPED_ROW` (fila que no se entendió), `IGNORED_VALUE` (valor fuera de rango), `EMPTY_DAY`, `NOTHING_FOUND` y
`SHEET_NOTE` (notas del lector o de la hoja). La pantalla los agrupa, deja ignorarlos (uno o todos) y convertir una fila saltada en
ejercicio («Añadir como ejercicio»).

## 3. Nombres de ejercicio

`ExerciseNameMatcher` empareja cada nombre con el catálogo:
1. Alias exactos (`ImportExerciseAliases`, ~500 frases en inglés y en español; confianza 0,95).
2. Si no, parecido por palabras (Dice) con traducción inglés → español y penalización por variante o material que no cuadra.
- «Reconocido» = confianza ≥ 0,8 y al menos 0,15 por encima del segundo candidato. Si no, la pantalla ofrece 3 sugerencias y búsqueda.
- `ExerciseMatchLexicon` (el del resto de la app) no se tocó.

## 4. Programas que KPKN ya tiene

`KnownProgramRecognizer` busca en el título del archivo y en las celdas con forma de título de las 3 primeras filas un programa del catálogo
(GZCLP, Texas Method, 5/3/1, nSuns, PHUL, PHAT…). Si lo encuentra, la pantalla ofrece «Usar el de KPKN» (decisión del usuario): en el alta
salta a la recomendación con ese plan preseleccionado; en la app abre el plan en el asistente. Importar el archivo tal cual sigue disponible.

## 5. Revisión y ajuste antes de guardar

Todo es puro y se puede deshacer:
- `ImportDraftEditor`: editar series, repeticiones, carga, RPE/RIR y descanso; renombrar (en una semana o en todas); duplicar, mover y
  borrar ejercicios; renombrar, mover, unir y añadir días; duplicar o borrar semanas; cambiar la unidad (kg/lb).
- `ImportEdits` (pantalla): cada cambio es una operación (`ImportEditOp`) con su inversa; deshacer hasta 30 pasos (`ImportUndoStack`).
- Cada nombre necesita una decisión: «Elegir» (un ejercicio del catálogo) o «Propio» (se guarda con su nombre).
- Revisión final «Falta revisar» (`ImportDraftValidation.check`, pura): semanas o días vacíos, nombres sin decidir y avisos sin revisar,
  cada uno con su acción («Elegir», «Quitar el día», «Quitar la semana», «Ver avisos», «Ignorar todos»).
- El borrador del alta guarda el programa importado; los borradores anteriores a H1 se reparan al leerlos (`withIds`).

En Free, un programa importado de más de 1 semana o de más de 5 sesiones pide Pro (`ProgramAccessPolicy`).

## 6. Bancos de pruebas

Los corpus son de terceros: **nunca entran en el repositorio**. Solo se suben casos sintéticos escritos a mano.

| Banco | Cómo se corre | Qué escribe |
|---|---|---|
| Hojas (JVM) | `KPKN_IMPORT_CORPUS=<carpeta> KPKN_IMPORT_REPORT=<salida> … --tests "*ImportCorpusReportTest*"` | `report.md`, `report.json` y una vista por archivo en `sheets/`: resultado (BIEN, POBRE, NADA, RECHAZO o FALLO), nombres sin reconocer, plantilla reconocida, avisos y tiempos |
| Filas de OCR (JVM) | `KPKN_OCR_ROWS=<carpeta de .rows.txt> KPKN_IMPORT_REPORT=<salida> … --tests "*OcrRowsCorpusReportTest*"` | el mismo informe, con las filas que reconstruyó el teléfono (sin repetir el OCR) |
| OCR en el teléfono | APK `.dbg` + `ImportOcrHarnessActivity` (solo depuración): `phone_run.py --clear-dbg --push-dbg <pdfs>=ocr-corpus --wait-file ocr-report/ocr-report.json --pull-dbg ocr-report=<salida>` | `ocr-report.json` (páginas, ms por página, resultado) y `<archivo>.rows.txt` con `--- página N ---` entre páginas |

- El banco de filas de OCR reproduce exactamente lo que da el teléfono: se puede mejorar el analizador en la JVM y pasar por el teléfono
  solo para confirmar.
- Al revisar un cambio del analizador se compara archivo por archivo (`report.json`): resultado, ejercicios y **número de días**. Una
  regla que junta días puede mejorar el total y mezclar semanas en una sesión.

Qué significa cada resultado:
- **BIEN:** hay ejercicios, se reconoce al menos la mitad de los nombres y no hay más filas saltadas que ejercicios.
- **POBRE:** hay ejercicios, pero se reconocen menos de la mitad de los nombres o sobran filas saltadas. Se puede importar: lo que falta
  se decide en la revisión.
- **NADA:** ningún ejercicio.
- **RECHAZO:** el lector no acepta el archivo.

Resultados (2026-10-12, `feat/wizard-v3` con H4d):

| Corpus | Archivos | BIEN | POBRE | NADA | RECHAZO | Nombres reconocidos |
|---|---|---|---|---|---|---|
| Hojas de la comunidad (118 de LiftVault y 8 en español) | 126 | 22 | 61 | 43 | 0 | 36,0 % de media por archivo; 34 plantillas de KPKN |
| Colección del usuario (`01_originales`) | 1057 | 105 | 208 | 149 | 595: PDF, porque la JVM no tiene OCR | 39,3 %; 62 plantillas |
| PDF por OCR en el Galaxy Z Flip5 | 35 (483 páginas) | 9 | 15 | 11 | 0 fallos | 47,9 % de los nombres; 173 ms por página (mediana) |

Evolución en el teléfono:
- 1.ª pasada (H4b): BIEN 1, POBRE 16 y NADA 18, con 317 ejercicios.
- 2.ª pasada (H4d): BIEN 9, POBRE 15 y NADA 11, con 477 ejercicios. Ningún archivo empeoró.
- Con H4e, medido con el banco JVM sobre las filas de la 2.ª pasada: 486 ejercicios.
  - El plan de 4 días de Scraler pasa de 2 a 4 días y de 15 a 22 ejercicios.
  - Ningún archivo empeora y las hojas de cálculo no cambian.
- Qué hace H4e en PDF y fotos:
  - en una tabla, los trozos del nombre que quedan encima y debajo de su fila de datos se unen a ella («Curl de bíceps con» / datos /
    «mancuernas» → «Curl de bíceps con mancuernas»);
  - se quita el número que va delante de una etiqueta de día («2 Día 2: Pierna A»).

## 7. Pendientes conocidos

- **Tablas de OCR sin columna de repeticiones** («Exercise | Activity | Rest | Sets»): «6-8 reps» cae en otra columna y el ejercicio se
  rechaza. Haría falta deducir las columnas por su contenido.
- **Celdas partidas cuyo trozo de arriba lleva cifras** («Opción 2,- Flexiones en»): no se unen, y el trozo de abajo puede quedar como un
  día suelto.
- **Infografías de varias columnas** (algunos PDF de Templo del Fitness): el OCR mezcla las columnas en las mismas filas. Haría falta
  reconstruir bloques por página.
- **Liftoscript con plantillas** («...t1»): las referencias no se expanden.
- **Hojas de registro con una fila por sesión** (Dan John de 40 días) y libros con la semana en una pestaña aparte (Brazos Valley): su
  estructura no se reconoce.
- **Archivos de más de 5 MB** (15 en la colección): se rechazan por tamaño.
- **Pirámide sin series que no va en un solo sentido** («10-8-6-15»): no se lee como pirámide. Con series («4x 10-8-6-15») sí.
