# H4d · Importador: leer bien los PDF y fotos que pasan por OCR (2026-10-11)

Worktree y rama: los que diga el mensaje de lanzamiento (sale de `feat/wizard-v3`, con H3h integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H3a.md`, `H3c.md`, `H3g.md`, `H4b.md` y `H4c.md`.
Tareas Gradle `...Base...`. Español neutro.

## Qué pasó en el teléfono
El arnés de OCR (H4b) leyó en el Galaxy Z Flip5 35 PDF de la colección (23 en español):
- **Motor:** 483 páginas, mediana de 0,2 s por página, ningún fallo. Funciona.
- **Resultado:** 1 BIEN, 16 POBRE y 18 NADA.
- **Filas reconstruidas:** las de cada PDF (celdas separadas por « | ») están en `C:\kw\shots\v3\ocr-phone\ocr-report\*.rows.txt` y el resumen en `ocr-report.json`. Son de terceros: no se copian al repo.

Las tablas en inglés salen bien («Exercise |  | Sets | Reps» / «Overhead Press |  | 3 | 10-15»). Lo que falla es el **análisis** de lo leído. Ejemplo, los PDF visuales de Templo del Fitness (español, `13-…rows.txt`):

```
PARTE INFERIOR
Día 1
ADDUC TORES
ABDUCTOR
EXTENSIÓN DE CỦADRICEPS       ← lista de ejercicios del día, sin prescripción
…
AGR
4 TEMPLE OF FITNESS           ← cabecera o pie de marca, en cada página
ADDUCTORES                    ← una página por ejercicio:
DÍA 1                         ←   el día se repite
4x 15-12-10-8                 ←   la prescripción, sola, en otra línea (pirámide)
```

## Cambios
1. **Muebles de página** (`OcrImportSource`, en `data`):
   - Antes de concatenar las páginas, quita las filas que, normalizadas (minúsculas, sin cifras ni signos), se repiten en el 40 % de las páginas o más, con un mínimo de 3. Así salen «temple of fitness», «agr» y los pies con número de página.
   - El arnés de depuración escribe en `.rows.txt` una línea `--- página N ---` entre páginas, para poder estudiar las páginas en la JVM.
2. **Nombre y prescripción en líneas distintas** (`ProgramImportParser`, en texto y en filas de una celda):
   - Si una fila es **solo notación** («4x 15-12-10-8», «3x10», «4 series de 12»), busca su nombre hacia atrás, hasta 3 filas.
   - Toma la última fila de nombre: de 3 letras o más, sin notación, que no sea prosa, día ni etiqueta.
   - En el camino se saltan las etiquetas de día y las filas de 3 letras o menos.
   - Si lo encuentra, esa fila de nombre deja de contar como «línea de texto» o día candidato y se forma el ejercicio.
   - Vale también para el texto pegado («Sentadilla» / «4x8»).
3. **El mismo día repetido continúa el día:** una etiqueta de día igual (normalizada) al día abierto no abre otro. «DÍA 1» en cada página = un solo «Día 1».
4. **Lista de ejercicios sin prescripción** antes de las páginas de detalle:
   - Los nombres sueltos que no se emparejan con ninguna prescripción NO dan avisos uno a uno: suman a las «líneas de texto» de la hoja.
   - Solo dan SKIPPED_ROW con el mensaje de siempre si la hoja no tiene ningún ejercicio.
5. **Repeticiones en pirámide:** «4x 15-12-10-8», «15-12-10-8» o «12/10/8/6», con tantas cifras como series o 3 o más, dan el rango mín–máx (8–15) y la nota «Pirámide: 15-12-10-8.».
6. **Notación larga**, en inglés y en español:
   - «2 sets x 10 reps», «3 sets of 8», «4 sets of 8-12 reps»;
   - «3 series de 10 repeticiones», «3 series x 12», «4 x 10 reps».
   - También entre paréntesis tras un nombre: «Bent-Over Rows (2 sets x 10 reps)».
7. **Por lado:** «8/leg», «10 each side», «12 por pierna», «10 c/l» o «10 por lado» dan esas repeticiones con la nota «Por lado.».

## Banco de pruebas de filas de OCR (código de prueba)
- **Prueba nueva** `OcrRowsCorpusReportTest`: solo corre con `KPKN_OCR_ROWS` (la carpeta de `.rows.txt`).
  - Lee cada `.rows.txt`, parte las celdas por « | » y usa las líneas `--- página N ---` si las hay.
  - Arma una `ImportSheet` y pasa por `ProgramImportParser` y el emparejador, igual que el arnés del teléfono.
  - Escribe un informe como el de H3a en `KPKN_IMPORT_REPORT`.
- **Antes y después** en `C:\kw\import-ocr-rows-report-antes` y `-despues`, sobre `C:\kw\shots\v3\ocr-phone\ocr-report`.
  - Esas filas no tienen marcas de página, así que la regla 1 no se ve ahí; se prueba con datos sintéticos.

## Pruebas (escritas a mano, imitando la disposición)
- **Visual:** lista de ejercicios del día y, después, página por ejercicio con marca repetida, «DÍA 1» y «4x 15-12-10-8».
  - Resultado: un solo «Día 1» con sus ejercicios, rango 8–15 y la nota de pirámide, sin la marca.
- **Muebles de página:** 4 páginas sintéticas con la misma cabecera y pie numerado: desaparecen.
- **Notación larga y por lado:** un caso por forma.
- **Texto pegado:** «Sentadilla\n4x8\nPrensa\n3x10» da 2 ejercicios.
- Las pruebas del analizador que ya existían siguen en verde.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ProgramImportParser*" --tests "*OcrImportSource*" --tests "*OcrLayout*" --tests "*OcrRowsCorpus*" --tests "*ImportCorpusReporter*" --tests "*ImportedProgramBuilder*" --tests "*ProgramImportStep*"`

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de las filas del teléfono ni de los corpus en el repo.

## Prohibido
- El emparejador, el lector de Excel, la interfaz y el reconocedor de plantillas.
- Cambiar los textos de los avisos que ya existían.
- `MainActivity.kt`.
- Push.

## Informe
- Las reglas, con un ejemplo de cada una.
- Pruebas: número y resultado.
- El antes y el después de las filas del teléfono (por archivo y en total).
- Y de los dos corpus de hojas, para confirmar que no empeoran: `C:/kw/import-corpus-report-h4d` y `C:/kw/import-corpus-report-coleccion-h4d`.
