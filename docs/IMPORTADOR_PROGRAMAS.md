# Importador de programas (oct-2026)

Cómo KPKN lee un programa ajeno (Excel, CSV, Word, texto, PDF o foto) y cómo la persona lo deja perfecto antes de guardarlo. Se usa en el
alta (`PROGRAM_SOURCE` → «Importar mi programa», paso `PROGRAM_IMPORT`) y en Programas → «Nuevo» → «Importar»: las dos entradas
comparten la misma pantalla. Los briefs con el porqué de cada regla son `docs/wizard-v3/briefs/H1.md` a `H4d.md`.

## 1. De dónde sale el programa

| Fuente | Formatos | Lector (`data/importer/`) | Límites (`ImportLimits`) |
|---|---|---|---|
| «Elegir archivo» | `.xls` (BIFF8), `.xlsx`, `.xlsm`, `.ods`, `.csv`, `.docx`, `.txt`, `.md` y `.pdf` | `ImportFileReader` decide por la firma del archivo: `Biff8Reader`, `XlsxReader`, `OdsReader`, `DocxReader`, `CsvReader`, `TextProgramReader` y, para PDF, OCR. En `.xlsm` se leen las hojas; no se ejecutan ni conservan macros. | 5 MB por archivo (20 MB si es PDF); 32 MB descomprimidos; 20 hojas, 2000 filas y 40 columnas por hoja |
| «Pegar texto» | texto libre, tablas de Markdown y bloques de Liftoscript | `TextProgramReader` | — |
| «Foto de tu rutina» | cámara o galería | OCR (`data/importer/ocr/`) | 15 MB por imagen; 2000 px por lado |

- **Excel**: `.xlsx`/`.xlsm` se leen con SAX y `.xls` con BIFF8; no se conservan las hojas ocultas y se respetan los formatos de % y tiempo. Si recorta filas, columnas
  u hojas, la hoja lleva una nota («Solo se leyeron las primeras 2000 filas.»).
- **Formatos antiguos**: BIFF8 (`.xls`) se lee localmente. `.doc` y los libros `.xls` cifrados o incompatibles piden guardar una copia como `.xlsx`.
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
convierte en un `Program` con procedencia «Programa importado». En las importaciones web, el borrador durable y el programa guardado conservan
el título de la fuente y su origen HTTPS (esquema, host y puerto); se omiten ruta, query y fragmento para no retener tokens de enlaces.

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
- Día repetido sin semanas (H6a): si una hoja no tiene etiquetas de semana y una etiqueta de día vuelve a salir («DAY 1» tras «DAY 6»),
  empieza una semana nueva. Con semanas en la hoja, esta regla no se aplica.
- Calculadoras por semanas en columnas (H6c, `WeekBandBlocks`): bloques «N @ | carga | x R» bajo filas de semanas («Week 1:»,
  «Weeks 1 & 2»).
  - Cada fila es un día.
  - El levantamiento sale del título de la hoja; si no se reconoce, «Levantamiento principal» con una nota.
  - Unidad por la pista de redondeo («Round to 5» = lb, «2.5» = kg).
  - «Option 2» y siguientes se cortan, con una nota.
  - Las cargas salen del 1RM de ejemplo de la hoja, con la nota «las cargas salen de un 1RM de 200; cámbialas si el tuyo es otro.».
  - «x failure», «x AMRAP» o «x max» sin número: esa fila se omite con aviso.

**Columnas de la tabla (H6a)**
- **Esquema en una columna** («Sets x Reps», «Set Scheme», «Reps / Scheme», «Target», «Rx», «Prescription»…; lista cerrada):
  - «3 x 5» → 3 × 5;
  - «3-4 x 6-8» → 3 × 6-8 y la nota «Series: 3-4.»;
  - lo que va entre paréntesis pasa a nota.
- **Columnas de registro** («Reps Done», «Weight Used», «Fatigue %», «Est 1RM», «Log», «Done»…): no tienen papel. «Reps Done» ya no tapa a
  «Reps».
- **«Set n», «Set #», «Set Type» y «Top Set»:** sin papel. «Set» en singular queda sin papel si en la cabecera también hay «Sets». «Lift» y
  «Levantamiento», como cabecera exacta, son el nombre.
- **Cargas por serie:** en una tabla sin columna de series ni de esquema, si todas las cifras de las columnas «Set n» son ≥ 20, cuenta
  como series = número de celdas y carga = la mayor, con la nota «Cargas por serie: …».
- **«S · X · R» seguidas en la cabecera** (series, una «x» sin papel y repeticiones). Un día a la izquierda del nombre («MONDAY») abre ese
  día.
- **Listas en una celda:** «(load × reps)», donde «95×3, 95×3» = 2 × 3 con 95, y «(load x reps x sets)», donde «105x1x5» = 5 × 1 con 105.
- **Una fila por serie** (columna de índice «Set #», «Set n» o «Set», que sube de uno en uno):
  - las filas seguidas del mismo ejercicio forman UNO: series = filas, repeticiones iguales o en rango, y la carga mayor con la nota de
    cargas por serie;
  - el calentamiento no cuenta, con la nota «Se omitieron N series de calentamiento.».
- **Ojo:** el camino de «sesiones en columnas» sigue usando los papeles de columna anteriores (`legacyColumnRole`). Con los nuevos se
  rompían hojas que ya iban BIEN.

**Notación**
- Compacta: «4x8 @80kg», «3×10 RPE8», «5x5 @ 75%», «70%x3x2», «70/3/2».
- Larga, en inglés y en español: «3 sets of 8», «2 sets x 10 reps», «3 series de 10 repeticiones», también entre paréntesis tras el nombre.
- Delante del nombre: «4x8 Sentadilla».
- Nombre y prescripción en líneas distintas (texto pegado y OCR): «Sentadilla» / «4x8» = un ejercicio (busca el nombre hasta 3 filas atrás,
  saltando etiquetas de día).
- Solo en OCR, una fila sin encabezado puede llevar el día en la primera celda y el ejercicio/prescripción en las siguientes
  («DAY A | Back Squat | 3 x 5 reps | 70 kg»). Si el mismo día se repite en filas contiguas, sus ejercicios se agrupan; también se
  reconocen etiquetas «Day A»/«Día B».
- Una celda OCR «10 Push Ups» se lee como nombre + repeticiones. Si la fuente no indica series, se conserva con `INCOMPLETE_ROW` y el
  guardado queda bloqueado hasta completar las series; no se inventa una serie. Fuera de OCR, una fila sin series se omite.
- Tiempo objetivo: «Plank 3x40 s» y una columna `Duration (s/min)` producen modo por tiempo, sin objetivos de repeticiones; `rest 1 min`
  sigue siendo descanso. Si la fuente trae el tiempo pero no las series («Plank | 40 s»), la fila queda visible con `INCOMPLETE_ROW`
  y no se puede guardar hasta completar las series en el editor. No se inventa un número de series.
- Nombres sueltos sin prescripción (sin cifras): se cuentan como líneas de texto de la hoja y solo avisan uno a uno si la hoja no da
  ningún ejercicio. Una fila con cifras que no se entiende («Press 4/8») sí avisa, para poder «Añadir como ejercicio».
- Solo en PDF y fotos: una frase larga con cifras (8 palabras o más, o 60 caracteres o más, casi toda en minúscula; por ejemplo «Cómo
  leerlo: RPE 8 significa…») es una línea de texto, no un aviso.
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

`ImportWarningKind`: `SKIPPED_ROW` (fila que no se entendió), `IGNORED_VALUE` (valor fuera de rango), `INCOMPLETE_ROW` (falta completar
la prescripción), `EMPTY_DAY`, `NOTHING_FOUND` y `SHEET_NOTE` (notas del lector o de la hoja). La pantalla los agrupa, deja ignorarlos
(uno o todos) y convertir una fila saltada en ejercicio («Añadir como ejercicio»). `INCOMPLETE_EXERCISE` bloquea el guardado hasta que la
prescripción quede completa.

## 3. Nombres de ejercicio

`ExerciseNameMatcher` empareja cada nombre con el catálogo:
1. Alias exactos (`ImportExerciseAliases`, ~500 frases en inglés y en español; confianza 0,95).
2. Si no, parecido por palabras (Dice) con traducción inglés → español y penalización por variante o material que no cuadra.
- «Reconocido» = confianza ≥ 0,8 y al menos 0,15 por encima del segundo candidato. Si no, la pantalla ofrece 3 sugerencias y búsqueda.
- **Dos opciones** («Dominadas o jalón agarre cerrado», «Leg press or hack squat»):
  - si el nombre entero no se reconoce y la primera opción sí, se usa la primera;
  - el ejercicio guardado lleva la nota «Alternativa: jalón agarre cerrado.».
- **Alias que dicen el movimiento, no solo el material:** «remo sentado (en polea)» es el Remo Gironda, no el remo con barra en polea, que
  se hace de pie. Al añadir alias, comprobar las dos cosas en el catálogo.
- `ExerciseMatchLexicon` (el del resto de la app) no se tocó.
- **Nombres en inglés (H6b, `ImportNameNormalizer`):** antes de buscar, el nombre se normaliza.
  - **Qué se quita:** la notación pegada («Squat 3x5 @80%» → «Squat»).
  - **Qué se desarrolla:** las abreviaturas («DB», «BB», «KB», «OHP», «RDL», «1 arm»…).
  - **Qué se unifica:** plurales y compuestos («Pull-ups» = «pull up»).
  - **Qué se ignora:** las cabeceras que no son nombre.
  - **Si nada da:** una búsqueda relajada traducida.
  - **«X / Y»:** se prueban las dos.
  - **Umbral de sugerencias:** 0,3. Hay guardas para no tomar un material o una variante por otra.
- **Halterofilia:** «Clean» y «Cargada» a secas son la cargada completa (`squat_clean`); «Snatch» y «Arranque», el arranque completo
  (`squat_snatch`). «Power clean», «Hang clean» y demás siguen a su variante.

## 4. Programas que KPKN ya tiene

`KnownProgramRecognizer` busca en el título del archivo y en las celdas con forma de título de las 3 primeras filas un programa del catálogo
(GZCLP, Texas Method, 5/3/1, nSuns, PHUL, PHAT…). Si lo encuentra, la pantalla ofrece «Usar el de KPKN» (decisión del usuario): en el alta
salta a la recomendación con ese plan preseleccionado; en la app abre el plan en el asistente. Importar el archivo tal cual sigue disponible.

## 5. Revisión y apertura del programa

**Flujo visible actual (7 nov 2026):** al terminar de leer, el asistente abre directamente una sola pantalla de revisión de ejercicios y
distribución semanal → guardar y abrir el `ProgramDetail` existente. La pantalla reúne la confirmación de coincidencias y el calendario por
semana; no añade un resumen intermedio, pasos separados de Sesiones, editor de importación, checklist final ni éxito dentro del importador.

- Cada ejercicio debe quedar asociado a una variante del catálogo o marcado como propio. Las coincidencias automáticas también se pueden
  desplegar y comprobar junto con la prescripción original; cualquier nombre pendiente bloquea el guardado.
- El calendario conserva la semana y el día que se pudieron leer. La persona puede mover una sesión, sugerir un reparto o aplicarlo a las
  demás semanas. Los choques se muestran como información para corregir o confirmar; no se pierden al guardar.
- Los avisos de lectura también deben revisarse o descartarse. La distribución se guarda en el borrador y la ausencia de días de entrenamiento
  bloquea el guardado.
- Al guardar, `ImportedProgramBuilder` materializa el programa canónico una sola vez. `onSaved` abre directamente
  `KpknRoute.ProgramDetail.create(programId)`: ahí se ajustan las semanas y desde ahí se entra al `SessionEditorScreen` existente si hace falta
  cambiar series, repeticiones, carga, RPE/RIR o descanso.
- El título del programa también se puede cambiar en esa pantalla de revisión; los borradores antiguos que apuntan al resumen vuelven a la
  pantalla conjunta.
- Los borradores nuevos guardan el documento de origen, las decisiones de ejercicios, el título y la distribución; no guardan una segunda
  copia completa de `Program`. Los borradores editados de versiones anteriores conservan compatibilidad de lectura.
- La política Free sigue validando una semana y hasta cinco sesiones por semana. Si el programa supera el límite, el borrador durable se
  conserva y se explica el límite.
- El progreso de lectura presenta las etapas reales Documento, Estructura, Ejercicios, Catálogo y Días; en PDF muestra la página actual.
  Los contadores aparecen cuando los produce el lector, parser o matcher; no se simula progreso con temporizadores.
- La pantalla de importar oculta la barra flotante de la app, como el asistente y el editor de programas.

En Free, un programa importado de más de 1 semana o de más de 5 sesiones pide Pro (`ProgramAccessPolicy`).

## 6. Bancos de pruebas

Los corpus son de terceros: **nunca entran en el repositorio**. Solo se suben casos sintéticos escritos a mano.

| Banco | Cómo se corre | Qué escribe |
|---|---|---|
| Hojas (JVM) | `KPKN_IMPORT_CORPUS=<carpeta> KPKN_IMPORT_REPORT=<salida> … --tests "*ImportCorpusReportTest*"` | `report.md`, `report.json` y una vista por archivo en `sheets/`: resultado (BIEN, POBRE, NADA, RECHAZO o FALLO), nombres sin reconocer, plantilla reconocida, avisos y tiempos |
| Filas de OCR (JVM) | `KPKN_OCR_ROWS=<carpeta de .rows.txt> KPKN_IMPORT_REPORT=<salida> … --tests "*OcrRowsCorpusReportTest*"` | el mismo informe, con las filas que reconstruyó el teléfono (sin repetir el OCR) |
| OCR en el teléfono | APK `.dbg` + `ImportOcrHarnessActivity` (solo depuración), con el `phone_run`/`phone.lock` vigente: `phone_run.py --push-dbg <PDFs e imágenes .jpg/.jpeg/.png/.webp/.heic>=ocr-corpus --wait-file ocr-report/ocr-report.json --pull-dbg ocr-report=<salida>` | `ocr-report.json` (páginas, ms por página, resultado y conteos/mínimo/promedio de confianza del OCR) y `<archivo>.rows.txt` con `--- página N ---` entre páginas PDF. Una foto cuenta como una página. |

Para medir OCR contra una muestra etiquetada, coloca junto a cada entrada un sidecar UTF-8 llamado `<archivo completo>.expected.txt` (por ejemplo, `rutina.jpg.expected.txt`). Escribe una fila por línea y separa las celdas con ` | `; para PDF, usa los marcadores `--- página N ---` que produce el arnés. El JSON añade filas/celdas exactas, WER y CER sin copiar la transcripción de referencia al informe, además de los scores de confianza de ML Kit. Los scores no son exactitud calibrada; las métricas de transcripción requieren una referencia humana y no se infieren del corpus histórico sin etiquetas.

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

Resultados (2026-10-12, `feat/wizard-v3` con H5b):

| Corpus | Archivos | BIEN | POBRE | NADA | RECHAZO | Nombres reconocidos |
|---|---|---|---|---|---|---|
| Hojas de la comunidad (118 de LiftVault y 8 en español) | 126 | 22 | 61 | 43 | 0 | 493 nombres; 36,0 % de media por archivo; 34 plantillas de KPKN |
| Colección del usuario (`01_originales`) | 1057 | 106 | 207 | 149 | 595: PDF, porque la JVM no tiene OCR | 2033 nombres; 62 plantillas |
| PDF por OCR (filas del Galaxy Z Flip5, banco JVM) | 35 (483 páginas) | 9 | 13 | 13 | 0 fallos | 51,0 % de los nombres; 173 ms por página en el teléfono (mediana) |

Resultados tras la ola H6 (2026-10-13, `feat/wizard-v3` con H6a, H6b y H6c, frente a H5b):

| Corpus | Archivos | BIEN | POBRE | NADA | Empeoran | Nombres reconocidos |
|---|---|---|---|---|---|---|
| Hojas de la comunidad | 126 | 41 (22) | 52 (61) | 33 (43) | 0 | 50,5 % de media por archivo (36,0 %) |
| Colección del usuario | 1057 | 182 (106) | 170 (207) | 110 (149) | 0 | 55,8 % (39,7 %); 62 plantillas |
| PDF por OCR (filas del teléfono) | 35 | 9 | 13 | 13 | 0 | 239 de 441 nombres, 54,2 % (51,0 %) |

**PDF de la colección por OCR (emulador, 2026-10-13):** 562 PDF y 4676 páginas.
- **Cómo:** el OCR se hizo en el emulador con el arnés `.dbg` (mediana de 2,9 s por página: el emulador es mucho más lento que el
  teléfono, que tarda 173 ms). Después, las filas se analizaron en la JVM con el analizador integrado.
- **Resultado:** BIEN 67, POBRE 288, NADA 207 y FALLO 0. Con el analizador antiguo del APK eran BIEN 47, POBRE 308 y NADA 207.
- **Volumen:** 12 598 ejercicios y el 50,7 % de los nombres reconocidos con confianza.
- **Casi todos los NADA** son planes de carrera (5 km, 10 km, media maratón) y guías sin ejercicios de fuerza.
- **Las filas** se guardan en `C:\kw\ocr-emu\all-rows` (fuera del repo) para repetir el banco sin volver a pasar el OCR.

- **Pérdidas de ejercicios:** solo 3 en la colección, todas a propósito. Dos hojas de registro de Gript juntan sus 3 filas de serie en un
  ejercicio, y askold ya no lee como ejercicio la fila de cabecera «Week · Day · Lift · Set 1…».
- **Ningún archivo pasa a NADA.** Esta regla se exigió para integrar H6a.

- Los dos PDF de Vivifrail pasan de POBRE a NADA con H5b. Sus 6 «ejercicios» eran palabras sueltas de frases de instrucciones («Realiza»,
  «Haz»): no leer nada es más honesto.

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

- **Tablas de OCR sin columna de repeticiones** («Exercise | Activity | Rest | Sets»): el parser infiere la única columna libre que
  contiene etiquetas explícitas como «6-8 reps» o rangos repetidos como «6-8». Los números sueltos no bastan para evitar confundirlos
  con cargas; columnas de tempo o duración también quedan fuera. La suite `ProgramImportParserSchemeColumnsTest` comprueba estos casos.
- **Celdas partidas cuyo trozo de arriba lleva cifras** («Opción 2,- Flexiones en»): no se unen, y el trozo de abajo puede quedar como un
  día suelto.
- **Infografías de varias columnas** (algunos PDF de Templo del Fitness): el OCR mezcla las columnas en las mismas filas. Haría falta
  reconstruir bloques por página.
- **Liftoscript con plantillas** («...t1»): las referencias no se expanden.
- **Hojas de registro con una fila por sesión** (Dan John de 40 días) y libros con la semana en una pestaña aparte (Brazos Valley): su
  estructura no se reconoce.
- **Archivos de más de 5 MB** (15 en la colección): se rechazan por tamaño.
- **Pirámide sin series que no va en un solo sentido** («10-8-6-15»): no se lee como pirámide. Con series («4x 10-8-6-15») sí.
- **Errores del OCR dentro de una palabra** («Fondoso» en lugar de «Fondos o»): no se adivinan.
- **Dos opciones con una primera muy genérica** («Remo o jalón», «Sentadilla o prensa»): se usa la configuración por defecto de la primera.
- **RTS (106 de la colección):** la hoja «Main» la toma el camino de sesiones, que trata «Top Set» y «Set n» como series. La regla de
  carga por serie solo vale en tablas planas, así que sigue en NADA.
- **russian_squat:** la cabecera «(Weight x Reps x Sets)» cae en otra columna que sus filas y no se empareja.
- **PHAT del corpus (097):** la celda de carga trae «70%» (orden de columnas distinto), así que sale como porcentaje sin kg.
- **Swords (122):** las filas «Warm-up» de la columna «Set» salen como series.
- **Calculadoras de LiftVault con el levantamiento sin nombrar en el título:** 8 de 18 quedan en «Levantamiento principal» y la persona
  lo elige en la revisión.
- **El nombre del catálogo no dice el material en la vista previa** («Aperturas Planas» puede ser en polea): se ve al abrir el ejercicio.
