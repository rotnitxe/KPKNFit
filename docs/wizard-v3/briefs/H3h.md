# H3h · Importador: hojas de una serie por fila (5/3/1), tope de libras y regresiones de H3g (2026-10-11)

Worktree `C:\kw\v3c`, rama `v3/h3h` (sale de `feat/wizard-v3`, con todo el importador integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H3a.md`, `H3c.md`, `H3e.md` y `H3g.md`.
Tareas Gradle `...Base...`. Español neutro.

## 1. Tope de carga según la unidad
- Hoy el analizador descarta cualquier carga fuera de 0,5–1000 aunque el borrador esté en libras.
- Con `loadUnit = LB`, el rango válido del valor leído es 1–2200 (lb). Con KG, sigue 0,5–1000.
- **Prueba:** «Deadlift 1x5 1100 lb» en un archivo en libras conserva la carga.

## 2. Hojas de una serie por fila (familia 5/3/1)
- **La disposición** (informe `C:\kw\import-corpus-report-h3g\sheets\020-*.md`, pestañas «Press», «Deadlift»…):
  ```
  fila 1:  Press – 5/3/1 BBB
  fila 2:           | Week 1 (3x5) |      |       | Week 2 (3x3) | …     | Week 3 (5/3/1) | … | Week 4 Deload
  fila 3:  Set      | % of TM | Load | AMRAP Reps | % of TM | Load | AMRAP Reps | …
  fila 4:  Cycle 1 (add 5 lb to TM…)
  fila 5:  Set 1    | 65%     | 80   |            | 70%     | 85   | …
  fila 7:  Set 3 (AMRAP) | 85% | 100 | …
  fila 8:  Boring But Big (5x10 @ 50%)
  fila 9:  BBB Set 1 (10 reps) | 0.5 | 60 | …
  fila 15: Cycle 2 …
  ```
  Cada **fila es una serie** del levantamiento de la pestaña (o del título de la fila 1), no un ejercicio.
- **Regla:**
  - **Cuándo aplica:** dentro de unas sesiones en columnas (H3g), los nombres de fila parecen etiquetas de serie: «Set N», «Serie N», «Set N (AMRAP)», «BBB Set N (10 reps)», «Top set», «Back-off set» o «Warm-up set».
  - **Agrupación:** las filas de serie seguidas forman UN ejercicio por grupo (semana). Su nombre es:
    - el título de la hoja sin lo que va tras «–», «-» o «:» («Press – 5/3/1 BBB» → «Press»);
    - si no hay título, el nombre de la pestaña;
    - las filas «BBB …» forman un segundo ejercicio, «<levantamiento> BBB».
  - **Repeticiones de cada serie,** en este orden:
    1. las de la etiqueta de la fila («(10 reps)»);
    2. las de la etiqueta de la semana: «(3x5)» → 5; «(3x3)» → 3; «(5/3/1)» → 5, 3 y 1 por orden de serie; «Deload» o «Descarga» → 5;
    3. si no hay ninguna, la serie no sale y da un aviso SKIPPED_ROW como siempre.
  - **AMRAP:** «(AMRAP)» o «+» en la etiqueta añade la nota AMRAP de H3c.
  - **% y carga:** de las columnas del grupo. Un valor ≤ 1,5 en una columna de % es una fracción (0,5 → 50 %).
  - **Ciclos:** solo se lee el **primer ciclo**, de «Cycle 1»/«Ciclo 1» hasta «Cycle 2». La hoja lleva la nota «Hoja «X»: se leyó el primer ciclo.».
  - **Día:** cada pestaña de levantamiento es un día con el nombre del levantamiento («Press»). Cada grupo de semana va a su semana.
- **Resultado esperado en 020:**
  - 4 semanas, cada una con los días Press, Deadlift, Bench y Squat;
  - en cada día, el levantamiento con 3 series (con su %, su carga y sus repeticiones) más «<levantamiento> BBB» con 5 × 10 al 50 %;
  - la fusión de H3e junta las series seguidas.

## 3. Regresiones de H3g
H3g mejoró mucho el total, pero bajó estos archivos de la colección (compara las vistas de `C:\kw\import-corpus-report-coleccion-h3e\sheets` y `C:\kw\import-corpus-report-coleccion-h3g\sheets`):
- HLM: BIEN 36 → POBRE 9;
- Greg Nuckols: 14 → 0;
- 8-week powerbuilding: 30 → 22;
- Sheikburn: 6 → 3;
- 10-week Everett: 1 → 0;
- 5/3/1 Vanilla (`026`): 8 → 0, que quizá se arregla con el punto 2.

Para cada uno:
- encuentra la causa (lo más probable es que una subcabecera se tome por sesiones en columnas sin serlo);
- corrígela sin perder lo que H3g ganó en 204, 205 y 049.
- **Si una disposición no tiene arreglo general,** explica por qué en el informe y no la fuerces.

## Pruebas (casos escritos a mano, sin copiar el corpus)
- **Tipo 5/3/1:** pestañas «Press» y «Squat» con 2 semanas («Week 1 (3x5)», «Week 2 (5/3/1)»), filas Set 1–3 (AMRAP en la 3), BBB con «(10 reps)» y fracción 0,5, y un «Cycle 2» que no se lee.
  - Resultado esperado: 2 semanas × 2 días, las repeticiones correctas, el 50 % y la nota de ciclo.
- Un caso que reproduzca cada regresión corregida.
- **Libras:** el tope del punto 1.
- Las pruebas del analizador que ya existían siguen en verde.
- **Corpus:** informes en `C:/kw/import-corpus-report-h3h` y `C:/kw/import-corpus-report-coleccion-h3h`, con el antes (los `-h3g`) y el después de los dos, y aparte los archivos citados.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/v3c/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ProgramImportParser*" --tests "*ImportedProgramBuilder*" --tests "*ImportDraft*" --tests "*ImportCorpusReporter*" --tests "*ImportActivation*" --tests "*ProgramImportStep*" --tests "*DomainHasNoAndroidImports*"`

## Commit
- Por rutas, en `v3/h3h`, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de los corpus ni de los informes en el repo.

## Prohibido
- El emparejador, el lector, el editor, la interfaz y el reconocedor de plantillas (H3f va a la vez).
- Cambiar los textos de los avisos que ya existían.
- `MainActivity.kt`.
- Push.

## Informe
- Las reglas, con un ejemplo de cada una.
- La causa de cada regresión y su arreglo, o por qué no lo tiene.
- Pruebas: número y resultado.
- El antes y el después de los dos corpus.
