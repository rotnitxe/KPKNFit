# H3b · Importador: reconocer los nombres de gimnasio en inglés y español (2026-10-11)

Worktree `C:\kw\sb`, rama `v3/h3b` (sale de `feat/wizard-v3`, que ya trae H1 y el banco de pruebas H3a). Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y `docs/wizard-v3/briefs/H3a.md`.
Tareas Gradle `...Base...`. Español neutro.

## Por qué
El banco de pruebas pasó 118 hojas reales de la comunidad (en inglés) por el importador: solo el **7 %** de los nombres sale reconocido con confianza. Ejemplos del informe (`C:\kw\import-corpus-report\report.md`, sección «Nombres sin reconocer»):
- «Barbell Row» → Remo Convencional, con 0,33.
- «Squat» → Sentadilla en V‑Squat (¡mal!), con 0,71.
- «Barbell Bench Press» → Press de Banca con Pausa (variante equivocada), con 0,74.
- «Romanian Deadlift» → Rumano en déficit, con 0,31.
- «Seated Calf Raise», «Lat Pulldown», «Face Pulls», «EZ Bar Curl», «Hammer Curls»… sin nada confiable.

Causas en `ExerciseNameMatcher.kt`:
1. La confianza es un Levenshtein sobre la cadena entera contra el nombre en español o los `searchTerms`. Es sensible al orden de las palabras y a las palabras de más.
2. La palabra de material («Barbell», «Dumbbell», «DB») elige la configuración, pero **no se quita** al medir el parecido.
3. No hay traducción de inglés a español, y casi no hay alias en inglés.
4. Nada frena a una variante con calificativos que el nombre no dice («con pausa», «déficit», «V‑Squat»).

## 1. Tabla curada de alias (nuevo `domain/training/importer/ImportExerciseAliases.kt`, puro)
- **Qué es:** un mapa de frase normalizada → id de **definición** del catálogo v2, con un material opcional por defecto.
- **Normalización:** la del emparejador:
  - `ExerciseMatchLexicon.normalize` y raíces;
  - sin palabras vacías en español ni en inglés (`with`, `the`, `on`, `of`, `a`, `an`, `and`, `to`);
  - sin palabras de material, que siguen eligiendo la configuración.
- **Contenido:** al menos **200 frases**.
  - Los 60 nombres más frecuentes del informe que de verdad son ejercicios.
  - El vocabulario habitual de gimnasio en inglés y en español: sentadilla y variantes, banca y variantes, peso muerto y variantes, press militar, remos, dominadas, jalones, curls, tríceps, elevaciones, aperturas, piernas en máquina, gemelos, glúteos, core, halterofilia, strongman y peso corporal.
  - **Abreviaturas:** OHP, RDL, SLDL, SLD, GHR, CGBP, BB, DB, DL, SQ, BP, T-bar, EZ, DB RDL.
  - **Formas con guion y plurales:** «pull-up», «pullup», «pull ups», «chin-ups», «face pulls», «flyes» o «flys».
- **Reglas:**
  - Cada frase apunta a la variante BÁSICA que entiende un levantador. «squat» o «back squat» es la trasera con barra alta, «bench» o «bench press» es la banca plana, «deadlift» es el convencional y «press» u «OHP» es el press militar de pie.
  - Una variante solo si la frase la dice («pause squat», «deficit deadlift», «incline bench», «sumo deadlift»).
  - **Usa solo ids que existan:** búscalos en `src/main/assets/exercise_catalog_v2.json` (228 definiciones; cada una tiene `id`, `canonicalName`, `searchTerms` y `configurations`). Si un ejercicio no está en el catálogo, NO lo inventes: déjalo fuera y anótalo en el informe.
- **Pruebas** (`ImportExerciseAliasesTest`):
  - cada id existe en el catálogo real y tiene al menos una configuración;
  - no hay frases duplicadas tras normalizar;
  - una muestra de 30 frases conocidas da la definición esperada.

## 2. Emparejador (`ExerciseNameMatcher.kt`)
1. **Coincidencia exacta con un alias**, tras normalizar como arriba: un candidato con confianza **0,95**.
   - **Configuración:** la del material del nombre si la definición la tiene; si no, la del alias, la sugerida o la por defecto.
   - Si la definición no tiene ese material, se aplica la regla de hoy (la bajada de 0,15 y el tope). Ejemplo: «Dumbbell Hip Thrust» cuando no hay hip thrust con mancuernas.
   - Debajo, los candidatos de siempre.
2. **Fuera de los alias** (respaldo difuso):
   - **Antes de comparar:** quita del nombre las palabras de material y traduce palabra a palabra con un mapa inglés → español pequeño (squat→sentadilla, bench→banca, row→remo, raise→elevacion, calf→talon, fly→apertura, pulldown→jalon, pullup→dominada, dip→fondo, lunge→zancada, incline→inclinado, decline→declinado, seated→sentado, standing→pie, front→frontal, close→cerrado, grip→agarre, reverse→inverso, extension→extension, curl→curl…).
   - **Parecido:** el máximo entre el Levenshtein actual y el **parecido por palabras**. Este último es Dice sobre raíces, y dos palabras de 5 o más letras cuentan como iguales con distancia ≤ 1.
   - Se compara el nombre original y el traducido, y gana el mejor.
3. **Penalización de variante** (solo en el respaldo): −0,10 por cada calificativo que el candidato tenga y el nombre no diga, hasta −0,30. Calificativos: pausa, deficit, tempo, cadena, banda, bloque, pin, sumo, rumano, bulgara, frontal, hack, v, zercher, landmine, unilateral, inclinado, declinado, cerrado, drag, gorila, isometrico, sentado, pie, alta, baja.
4. Sin cambios: el contrato público (`match`, `matchAll`, `isConfident` con 0,8 y un margen de 0,15) y `ExerciseMatchLexicon`, que es compartido con la búsqueda de la app y NO se toca.
5. **Pruebas** en `ExerciseNameMatcherTest`, más casos nuevos:
   - las 9 actuales siguen en verde;
   - «Barbell Squat» → trasera con barra alta y barra, confiable;
   - «Incline Dumbbell Bench Press» → banca inclinada con mancuernas;
   - «Romanian Deadlift» → rumano básico, no el de déficit;
   - «Squat» no da V‑Squat;
   - «Barbell Bench Press» no da la banca con pausa;
   - «Pause Squat» sí da la variante con pausa (si existe);
   - un nombre basura («Warm-up 10 reps», «Set», «11 2») NO sale confiable.

## 3. Medición con el corpus (código de prueba)
- **Informe nuevo:** añade al `ImportCorpusReporter` la sección «Reconocidos con confianza (muestra)». Lista los 80 nombres más frecuentes con confianza, con el ejercicio y la configuración elegidos. Sirve para revisar los falsos positivos: un ejercicio confiable pero equivocado es peor que uno sin decidir.
- **Ejecución:** vuelve a correr el informe real con el comando de H3a, pero con `KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h3b`. Otro paquete escribe a la vez en otra carpeta; la de H3a queda como referencia de «antes».
- **Meta:** al menos un **60 %** de reconocimiento medio en los archivos con nombres (hoy es el 7 %), sin falsos positivos claros en la muestra. Si una frase de la tabla da un falso positivo, corrígela.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/sb/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ExerciseNameMatcher*" --tests "*ImportExerciseAliases*" --tests "*ImportCorpusReporter*" --tests "*ProgramImportParser*" --tests "*ImportedProgramBuilder*" --tests "*DomainHasNoAndroidImports*"`
- Y el informe real con las variables de H3a.

## Commit
- Por rutas, en `v3/h3b`, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada del corpus ni del informe en el repo.

## Prohibido
- `ExerciseMatchLexicon`, el catálogo (`exercise_catalog_v2.json`), el analizador y el lector.
- Copiar al repo textos del corpus más allá de nombres de ejercicio sueltos para los alias.
- `MainActivity.kt`.
- Push.

## Informe
- El número de frases de la tabla y los ejercicios que no encontraste en el catálogo.
- El reconocimiento medio antes y después.
- La muestra de 80 confiables, completa.
- Los 30 nombres más frecuentes que siguen sin reconocer.
