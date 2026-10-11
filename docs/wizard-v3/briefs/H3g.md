# H3g · Importador: sesiones y semanas en columnas, nombres numerados y filas de día dentro de tablas (2026-10-11)

Worktree `C:\kw\v3c`, rama `v3/h3g` (sale de `feat/wizard-v3`, que ya trae H3c y H3e). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H3a.md`, `H3c.md` y `H3e.md`.
Tareas Gradle `...Base...`. Español neutro.

## Por qué
Tras H3e, las dos hojas HST en español (`C:\kw\import-corpus\204-hst-basica`, `205-hst-mejorada`) dan 0 y 3 ejercicios. Su disposición es muy común en las hojas de progresión: las **sesiones van en columnas**. Vista de 204 (informe `C:\kw\import-corpus-report-h3e\sheets\204-*.md`):

```
fila 8 :                                     | Semana 1 |          |           |          |         |          | Semana 2 | …
fila 9 :                                     | LUNES    |          | MIÉRCOLES |          | VIERNES |          | LUNES    | …
fila 11: | 15RM actual | Incremento | …     | Sesión 1 |          | Sesión 2  |          | Sesión 3|          | Sesión 4 | …
fila 12:                                     | Series x reps | Peso (kg) | Series x reps | Peso (kg) | …
fila 13: 1. Sentadilla libre | 260 | 10 | 270 | 10 | 2 x 15 | 210 | 2 x 15 | 220 | 2 x 15 | 230 | …
…
fila 26:                                     | Semana 3 | …   (y otro bloque igual con 10RM)
```

El PPL de Reddit (`049`, una pestaña por día) tiene la misma idea:
- **Cabecera:** «Exercise | Sets | Reps / Scheme | Weight | AMRAP Reps | … | Weight | AMRAP Reps | …»;
- **Grupos:** «Session 1», «Session 2», «Session 3»… encima de cada grupo.
- **Hoy:** solo se lee la primera sesión.

Y dos casos en español del informe de la colección (`C:\kw\import-corpus-report-coleccion-h3e\sheets`):
- **Scraler:** filas «Día N: | Foco» DENTRO de la tabla no abren día, y sus 4 días se funden en uno.
- **Gymevo:** el segundo día («Entrenamiento…») no se lee. Mira su vista para ver la fila exacta.

## Cambios (`ProgramImportParser.kt`; una función propia para las sesiones en columnas)
1. **Sesiones en columnas.**
   - **Detección:** una **subcabecera** es una fila cuyas celdas con papel de columna forman un patrón de 2 o más papeles que se **repite** 2 veces o más hacia la derecha.
     - Los papeles son los de siempre (series, repeticiones, carga, RPE y RIR), más «Series x reps», «Sets x Reps», «Scheme» y «Esquema» como notación.
     - Ejemplos: [notación, carga] × 6 o [carga, AMRAP reps, …] × 6.
     - Cada repetición es un **grupo**, con el papel de cada columna.
   - **Etiquetas de grupo:** en las hasta 3 filas de encima de la subcabecera, cada grupo toma la celda no vacía más cercana en su primera columna o a su izquierda, sin pasar del grupo anterior. Se reconocen:
     - **semana:** «Semana N» o «Week N»;
     - **día:** días de la semana con abreviaturas, «Día N» o «Day N»;
     - **sesión:** «Sesión N» o «Session N».
   - **Reparto:**
     - con etiquetas de **semana y día** (HST): cada grupo es ese día de esa semana;
     - con etiquetas de **sesión** y la hoja ya es un día (su nombre es un día o hay un día abierto, como en el PPL): cada grupo es ese mismo día en la semana N, contando las sesiones en orden;
     - con **solo sesiones o solo días**: cada grupo es un día seguido de la semana abierta (o de la semana 1), con su etiqueta como nombre.
   - **Filas de datos** (debajo de la subcabecera):
     - **Nombre:** la primera celda de texto con 3 letras o más a la izquierda del primer grupo.
     - **Prescripción base:** si hay columnas de series o repeticiones a la izquierda de los grupos (PPL: «Sets», «Reps / Scheme»), dan la base.
     - **Ejercicio por grupo:** cada grupo da un ejercicio con su notación o la base, su carga y su RPE o RIR. Solo sale si tiene series y repeticiones.
     - Las columnas de cálculo entre el nombre y los grupos («15RM actual», «Incremento deseado»…) se ignoran.
   - **Fin:** una fila vacía seguida de otra cabecera, una nueva fila de etiquetas de semana, o el fin de la hoja. En HST viene después otro bloque («Semana 3/4»), que se detecta igual.
2. **Nombres numerados.**
   - Quita del nombre los enumeradores iniciales «1.», «1)», «1 -», «12.».
   - Con etiquetas de bloque «A1.», «A1)», «B2 -», quita la etiqueta y pon la nota «Bloque A1» (es información de superserie).
   - «1. Sentadilla libre» → «Sentadilla libre».
3. **Filas de día dentro de una tabla.** Una fila de tabla es un **día** y no un ejercicio sin series si:
   - su celda de nombre (o la primera celda) pasa `isDayLabel` («Día 1:», «Día 2 - Pierna», «Day 3»…), o es la forma de Gymevo que veas en su vista;
   - y no trae series ni repeticiones.
   - **Nombre del día:** la etiqueta limpia, más el foco si hay otra celda de texto en la fila («Día 1 — Pecho y tríceps»).

## Pruebas (casos escritos a mano, sin copiar el corpus)
- **Tipo HST:** 2 bloques; en cada uno, la fila de semanas (2 semanas), la de días (3 días por semana), «Sesión N» y la subcabecera [«Series x reps», «Peso (kg)»] × 6, con 3 ejercicios numerados y columnas de cálculo antes de los grupos.
  - Resultado: 4 semanas × 3 días × 3 ejercicios, con la carga de cada sesión y los nombres sin número.
- **Tipo PPL:** la hoja «Mon Pull» con la cabecera «Exercise | Sets | Reps / Scheme | Weight | AMRAP Reps | Weight | AMRAP Reps | Weight | AMRAP Reps» y «Session 1/2/3» encima de los grupos.
  - Resultado: 3 semanas con el día «Mon Pull», con la base de series y repeticiones y la carga de cada sesión.
- **Tipo Scraler:** una tabla con filas «Día 1: | Pecho» entre ejercicios → días separados con su foco.
- **Sin repetición:** una tabla normal no se trata como sesiones en columnas.
- Las pruebas de `ProgramImportParserTest`, `ProgramImportParserNoiseTest` y `ProgramImportParserLayoutTest` siguen en verde.
- **Corpus:** informes con `KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h3g` (corpus `C:/kw/import-corpus`) y `C:/kw/import-corpus-report-coleccion-h3g` (colección, solo lectura). Pon en tu informe:
  - el antes (los informes `-h3e`) y el después de los dos;
  - y, aparte, 204, 205, 049, el de Scraler y los de Gymevo.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/v3c/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ProgramImportParser*" --tests "*ImportedProgramBuilder*" --tests "*ImportDraft*" --tests "*ImportCorpusReporter*" --tests "*ImportActivation*" --tests "*ProgramImportStep*" --tests "*ImportProgramScreen*" --tests "*DomainHasNoAndroidImports*"`

## Commit
- Por rutas, en `v3/h3g`, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de los corpus ni de los informes en el repo.

## Prohibido
- El emparejador, el lector, el editor y la interfaz.
- Cambiar los textos de los avisos que ya existían.
- `MainActivity.kt`.
- Push.

## Informe
- Las reglas, con un ejemplo de cada una.
- Pruebas: número y resultado.
- El antes y el después de los dos corpus y de los archivos citados.
