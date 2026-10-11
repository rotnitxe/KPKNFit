# H3c · Importador: menos ruido y la notación que usa la comunidad (2026-10-11)

Worktree `C:\kw\v3c`, rama `v3/h3c` (sale de `feat/wizard-v3`, que ya trae H1 y el banco de pruebas H3a). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H1.md` y `docs/wizard-v3/briefs/H3a.md`.
Tareas Gradle `...Base...`. Español neutro, sin género gramatical.

## Por qué
Con 118 hojas reales (informe en `C:\kw\import-corpus-report\report.md` y `sheets/*.md`), ningún archivo sale BIEN: 28 dan NADA y 59 POBRE. Lo que se ve en las vistas de hoja:
1. **Repeticiones con notación.** El PPL de Reddit (049) tiene una tabla perfecta: «Exercise | Sets | Reps / Scheme | Weight» con «Deadlift | 1 | 1x5+ | 135».
   - `tableRow` solo acepta un número o un rango en la columna de repeticiones. «1x5+» o «3x8-12» dejan la fila «sin repeticiones», y todas se omiten.
2. **Prosa convertida en día.**
   - Cualquier texto sin cifras pasa a ser un día: 003 tiene 223 avisos EMPTY_DAY.
   - En 111, una frase de instrucciones («Set Priority (yellow) to Yes for at most one or two muscles…») es el nombre de un día.
3. **Hojas de instrucciones y cálculo** («Read Me», «Setup», «Calculators», «1RM Input»): llenan de avisos SKIPPED_ROW de prosa.
4. **Filas que no son ejercicios** entran como ejercicios:
   - calentamientos («Warm-up 12 reps @ 50%», en TLS 059);
   - etiquetas («Set», «Biceps SxR»);
   - números sueltos («11 2», «5 2RM»);
   - bloques («Block 1 Meso A 1 10RM»).
5. **Notación antes del nombre:** «5x5 Squat» deja el nombre vacío.
6. **AMRAP «5+»:** el «+» de la última serie se pierde o rompe la lectura.

## Cambios (`domain/training/importer/ProgramImportParser.kt`; modelo `ImportedProgramDraft.kt` solo para el tipo nuevo)
- **Tipo de aviso nuevo `ImportWarningKind.SHEET_NOTE`:** aviso informativo de una hoja entera o agregado. Lleva `location = "Hoja «X»"`, sin `dayId` ni `exerciseId`. Añádelo al final del enum.
1. **Hojas que se omiten por su nombre.**
   - Se omiten si, una vez normalizado, el nombre:
     - contiene readme, «read me», leeme, instructions, instrucciones, instruction, faq, setup, configuracion, calculator, calculators, calculadora, calculadoras, calcs, glossary, glosario, changelog o tutorial;
     - o es exactamente inputs, input, maxes, max, 1rm, «1rm input», notes, notas, info, log, registro, summary, resumen, stats, dashboard, history o historial.
   - Cada una deja un aviso SHEET_NOTE: «Se omitió la hoja «Read Me»: parece de instrucciones o de cálculo.».
2. **Prosa.**
   - Una fila sin notación es prosa si su texto (las celdas unidas):
     - tiene más de 8 palabras o más de 60 caracteres;
     - o contiene «http»;
     - o termina en «.» con 5 palabras o más.
   - La prosa no es día ni aviso suelto. Se cuenta por hoja y al final de la hoja sale un SHEET_NOTE: «Hoja «X»: se omitieron N líneas de texto.» (con N = 1, «1 línea de texto»).
3. **Días.**
   - `isDayLabel` reconoce, en inglés y español:
     - «Day N», «Día N»/«Dia N», «Session N»/«Sesión N», «Workout A/B/C/1–9», «Entreno A/B»;
     - los días de la semana con sus abreviaturas (Mon, Tue/Tues, Wed, Thu/Thur/Thurs, Fri, Sat, Sun; lun, mar, mié/mie, jue, vie, sáb/sab, dom), seguidos o no de un foco («Mon Pull», «Monday – Pull», «Lunes: Pierna»);
     - y focos solos o combinados con «&», «/», «y», «+» o «and»: upper, lower, push, pull, legs, leg, full body, fullbody, torso, pierna, piernas, empuje, tirón, arms, brazos, chest, pecho, back, espalda, shoulders, hombros, glutes, glúteos, butt, calves, gemelos, core, abs, abdomen.
   - Un texto corto que no es prosa sigue pudiendo ser un día (≤ 5 palabras y ≤ 40 caracteres, sin cifras salvo un número final).
   - Si ese día candidato se queda sin ejercicios:
     - con `isDayLabel`, da EMPTY_DAY, como hoy;
     - si no, se descarta en silencio y suma a las «líneas de texto» de la hoja.
4. **Nombres que no son ejercicios.**
   - **Calentamiento:** un nombre que empieza por warm up, warmup, warm-up, calentamiento, aproximación, approach o ramp no es un ejercicio.
     - Se cuenta por hoja y sale un SHEET_NOTE: «Hoja «X»: se omitieron N series de calentamiento.».
   - **Cálculo o etiqueta:** también se cuentan juntos, con un SHEET_NOTE por hoja: «Hoja «X»: se omitieron N filas de cálculo o de etiquetas.». Lo son:
     - un nombre con menos de 3 letras;
     - o uno que, quitando cifras, «%» y signos, sea solo palabras de este conjunto: set, sets, serie, series, rep, reps, sxr, week, semana, day, dia, total, volume, volumen, tonnage, tonelaje, e1rm, rm, max, tm, training, notes, notas, load, weight, peso, carga, status, estado, amrap, rest, descanso, tempo, rpe, rir, date, fecha, cycle, ciclo, block, bloque, meso, phase, fase, scheme.
   - Las palabras de grupo muscular con «SxR» («Biceps SxR», «Tricep SxR», «Abs SxR») también son etiquetas.
5. **Repeticiones con notación.**
   - Si la celda de repeticiones no es un número ni un rango, se lee con la notación compacta: «1x5+», «3x8-12», «5x5», «3 x 10».
   - De ahí salen las repeticiones, y también las series cuando la columna de series está vacía. Si la columna de series tiene un valor, manda la columna.
6. **AMRAP.**
   - Un número de repeticiones seguido de «+» («5+», «1x5+», «3x8+») son esas repeticiones, más la nota «AMRAP: última serie, N o más repeticiones.». Se une a otras notas con un espacio.
   - Si las repeticiones son «AMRAP», «MR», «max reps», «máx.» o «al fallo» sin número, la fila se omite con un SKIPPED_ROW: ««X»: AMRAP sin número de repeticiones; se omite».
   - «10-15+» es el rango 10–15, con la misma nota.
7. **Notación antes del nombre.** Si no hay texto antes del primer tramo de notación, el nombre es el texto que queda detrás («5x5 Squat» → «Squat»), sin separadores sueltos («-–—:·|,»).
8. **Rango doble.** «4-6 / 8-10» toma el primer rango, como hoy, y deja «8-10» en las notas.
- **Sin cambios:** lo demás (textos de los avisos que ya existían, ids y `sourceText`). Las pruebas que ya existían siguen en verde. Si alguna fijaba lo permisivo de antes (p. ej. una prosa que pasaba a ser un día), cámbiala solo si la regla nueva lo exige, y lístalo en el informe.

## Pruebas (`ProgramImportParserTest` o un archivo nuevo `ProgramImportParserNoiseTest`)
- Hojas sintéticas para cada regla del 1 al 8, en hojas y en texto pegado cuando aplique.
- Un caso tipo PPL de Reddit, escrito a mano con nombres inventados y no copiado del corpus:
  - una hoja «Read Me» de prosa;
  - una «Setup»;
  - dos hojas de día con la cabecera «Exercise | Sets | Reps / Scheme | Weight» y filas «1x5+» y «3x8-12».
  - Resultado esperado: 2 días con todos sus ejercicios, la nota AMRAP y solo SHEET_NOTE como avisos.
- **Corpus real:** vuelve a correr el informe con `KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h3c` (comando de H3a). En el informe pon:
  - el antes y el después: archivos por resultado, ejercicios totales y avisos por tipo;
  - las muestras de 049, 059 y 111 después del cambio.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/v3c/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ProgramImportParser*" --tests "*ImportedProgramBuilder*" --tests "*ImportDraft*" --tests "*ImportCorpusReporter*" --tests "*ProgramImportStep*" --tests "*ImportProgramScreen*" --tests "*DomainHasNoAndroidImports*"`

## Commit
- Por rutas, en `v3/h3c`, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada del corpus ni de los informes en el repo.

## Prohibido
- El emparejador (`ExerciseNameMatcher`), en el que trabaja otro paquete a la vez.
- El lector (`data/importer/*`).
- El constructor, el editor y la interfaz.
- Cambiar los textos de los avisos que ya existían.
- `MainActivity.kt`.
- Push.

## Informe
- Las reglas, con un ejemplo de cada una.
- Pruebas: número y resultado.
- El antes y el después del corpus.
- Lo que cambiaste de pruebas que ya existían, y por qué.
