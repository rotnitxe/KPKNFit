# H4e · Importador: celdas de varias líneas en tablas de OCR y días con número delante (2026-10-12)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`, con H4d integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`,
`docs/IMPORTADOR_PROGRAMAS.md` (sobre todo §1 y §2) y `docs/wizard-v3/briefs/H4d.md`. Tareas Gradle `...Base...`. Español neutro.

## De dónde sale
Segunda pasada de OCR en el teléfono (35 PDF): BIEN 9, POBRE 15 y NADA 11, con 477 ejercicios.
- **Filas:** `C:\kw\shots\v3\ocr-phone-2\ocr-report\*.rows.txt`, con `--- página N ---`. Son de terceros: no se copian al repo.
- **Banco JVM:** `OcrRowsCorpusReportTest` reproduce exactamente el resultado del teléfono. Informe de partida: `C:\kw\import-ocr-rows-report-fase2`.

## Qué falla
### 1. Celdas de varias líneas
En una tabla de PDF, un nombre largo ocupa 2 o 3 líneas dentro de su celda. `OcrLayout` deja la fila de datos en el centro y las líneas del
nombre en filas aparte, encima y debajo (`06-scraler…`, 6 veces):

```
EJERCICIO | SERIES | REPS | TEMPO |  | RPE | DESCANSO CARGA | NOTAS
Curl de bíceps con |  |  |  |  |  |  |
 | 2 | 10 a 12 | 2-0-1-0 |  |  | 60 s |
mancuernas |  |  |  |  |  |  |
Press inclinado con |  |  |  |  |  |  |  |
 | 3 | 8 a 10 | 3-0-1-0 |  | 8 | 2 min |  |
mancuernas |  |  |  |  |  |  |  |
Dominadas o jalón agarre |  |  |  |  |  |  |  |
 | 3 | 6 a 10 | 2-1-1-0 |  | 8 | 2 min |  |
cerrado |  |  |  |  |  |  |  |
Zancada andando con |  | 8 a 10 por |  |  |  |  |  |
 | 3 |  | 2-0-1-0 |  | 8 | 90 s |  |
mancuernas |  | pierna |  |  |  |  |  |
```

Más casos:
- `05-fullmusculo…`, unas 26 veces: «Fondos en Banco con» / « |  | 4 | 3 a 30 | 8.5 | 1min a 3min» / «Rodillas Flexionadas».
- `30-…chris-hemsworth…`: «DB Alternating Reverse Lunges» / « |  | 6-8 reps | 1-minute |  |  | 3» / «with Biceps Curls».

Hoy los trozos de nombre se quedan como líneas sueltas o días silenciosos, y la fila de datos da «falta el nombre del ejercicio».

### 2. Días con un número delante
`06-scraler…` trae «2 Día 2: Pierna A» y «4 Día 4: Pierna B»: el OCR lee un número de sección delante de la etiqueta. Como no se reconoce
como día, los ejercicios de «Pierna A» acaban en «Día 1: Torso A».

## Cambios (`ProgramImportParser`). Todo SOLO en hojas de OCR (`pageStarts` no vacío)
### 1. Unir celdas partidas
- **Fila de datos huérfana:**
  - la celda del nombre (la primera columna de la tabla) está vacía;
  - es una fila de datos de tabla: 2 o más celdas que empiezan por cifra (`isTableDataRow` de H4d);
  - hay una tabla activa (cabecera de columnas leída antes en la hoja).
- **Trozo de arriba:** la fila con texto inmediatamente anterior. Vale si cumple todo esto:
  - la celda del nombre tiene texto, al menos 3 letras y ninguna cifra;
  - no es prosa, ni día, ni semana, ni cabecera, ni notación, ni fila de datos de tabla;
  - en cualquier otra columna, solo tiene texto donde la fila de datos tiene la celda vacía (completa huecos: «8 a 10 por»);
  - no se usó ya como trozo de otra fila.
- **Trozo de abajo (opcional):** la fila con texto inmediatamente siguiente, con las mismas condiciones. No vale si la fila de después es,
  a su vez, una fila de datos huérfana: entonces ese texto es el trozo de arriba de la siguiente («mancuernas» va con «Press inclinado con»;
  «Dominadas o jalón agarre» va con su propia fila).
- **Unión:** columna a columna, arriba + datos + abajo, separados por un espacio. La fila resultante se lee como cualquier fila de tabla.
  Los trozos usados no cuentan como líneas de texto, ni días, ni avisos.
  - Ejemplo: «Zancada andando con mancuernas», 3 series y repeticiones «8 a 10 por pierna», con su descanso y su RPE.
- Una fila de datos huérfana sin trozo de arriba sigue como hoy («falta el nombre del ejercicio»).

### 2. Etiqueta de día con número delante
Si el texto de la fila es un número suelto de 1 o 2 cifras seguido de una etiqueta de día («2 Día 2: Pierna A»), es esa etiqueta sin el
número.

## Pruebas (escritas a mano, imitando la disposición)
- **Hoja de OCR** (`pageStarts = listOf(0)`) con la tabla de arriba, «Día 1: Torso A», «2 Día 2: Pierna A» y los cuatro casos partidos:
  - 2 días con sus ejercicios;
  - nombres unidos: «Curl de bíceps con mancuernas», «Press inclinado con mancuernas», «Dominadas o jalón agarre cerrado» y «Zancada
    andando con mancuernas»;
  - series y repeticiones correctas;
  - ningún aviso por los trozos.
- **Las mismas filas sin `pageStarts`** (hoja normal): el resultado es el de hoy, sin uniones. Compáralo con una aserción explícita.
- **Fila de datos huérfana sin trozo de arriba:** sigue avisando.
- Las pruebas que ya existían siguen en verde.

## Bancos
- **Filas del teléfono:** `KPKN_OCR_ROWS=C:/kw/shots/v3/ocr-phone-2/ocr-report KPKN_IMPORT_REPORT=C:/kw/import-ocr-rows-report-h4e`.
  Compáralo archivo por archivo con `C:/kw/import-ocr-rows-report-fase2`.
  - Esperado en `06-scraler…`: 4 días («Día 1: Torso A», «Día 2: Pierna A», «Día 3: Torso B», «Día 4: Pierna B») y unos 24 ejercicios.
  - Esperado en `05` y `30`: más ejercicios.
  - Ningún archivo empeora ni pierde ejercicios. Si cambia el número de días de algún archivo, explica por qué.
- **Corpus propio de hojas,** como control de fugas: `KPKN_IMPORT_CORPUS=C:/kw/import-corpus KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h4e`.
  - Debe ser idéntico a `C:/kw/import-corpus-report-h4d`: mismo resultado, mismos ejercicios y mismos días por archivo.
  - La colección del usuario no hace falta: las reglas solo actúan en OCR.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ProgramImportParser*" --tests "*OcrLayout*" --tests "*OcrPagesJoin*" --tests "*OcrRowsCorpus*" --tests "*ImportCorpusReporter*" --tests "*ImportedProgramBuilder*"`

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de las filas del teléfono ni de los corpus en el repo.

## Prohibido
- El emparejador, los lectores, `OcrLayout`, la interfaz y el reconocedor de plantillas.
- Cambiar los textos de los avisos que ya existían.
- Cualquier cambio que actúe fuera de las hojas de OCR.
- `MainActivity.kt` y push.

## Informe (corto)
- Las dos reglas, con un ejemplo de cada una.
- Pruebas: número y resultado.
- Filas del teléfono: BIEN, POBRE y NADA, antes y después; los archivos que cambian (ejercicios y días) y el detalle de `06`.
- Corpus propio: confirma que es idéntico.
