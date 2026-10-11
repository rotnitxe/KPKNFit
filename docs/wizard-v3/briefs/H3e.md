# H3e · Importador: disposiciones en español, grupos de series, libras y fusión de grupos (2026-10-11)

Worktree y rama: los que diga el mensaje de lanzamiento (sale de `feat/wizard-v3` con H3c ya integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H1.md`, `docs/wizard-v3/briefs/H3a.md` y `docs/wizard-v3/briefs/H3c.md`.
Tareas Gradle `...Base...`. Español neutro.

## Por qué
El banco de pruebas pasó también la colección del usuario (391 hojas, informe en `C:\kw\import-corpus-report-coleccion`). Las plantillas en español (Clubbi, Trainer Studio, Scraler, Gymevo) enseñan cómo las rellenan los usuarios de KPKN, y hay dos disposiciones que el analizador no entiende:
- **Columna de día** (Trainer Studio semanal): «Dia | Grupo Muscular | Ejercicio | Series | Reps | Peso | Descanso | Notas». El día va solo en la primera fila de cada bloque («Lunes») y las filas siguientes dejan esa celda vacía. Hoy todo cae en un único día.
- **Días en columnas** (Trainer Studio gimnasio): la fila de cabecera es «| Lunes | Martes | Miercoles | Jueves | Viernes | Sabado», y debajo vienen bloques de tres filas: «Ejercicio 1 / Series x Reps / Peso (kg)», con el valor de cada día en su columna.

Y en las hojas de fuerza:
- **Grupos por porcentaje** (Sheiko, Smolov, 5/3/1): «Sentadilla 50%x5, 60%x4, 70%x3x2, 75%x3x5» o «50/5 60/4 70/3/2». Hoy no se leen.
- **Libras:** muchas hojas en inglés usan lb. Hoy «135» se lee como 135 kg.
- **Grupos seguidos del mismo ejercicio:** salen como varios ejercicios en la sesión de la app («Sentadilla» 4 veces). Deberían ser un solo ejercicio con todas sus series.

## Cambios
1. **Columna de día** (`ProgramImportParser`):
   - Nuevo papel de columna `DAY`: día, dia, day, sesión, sesion, session o jornada.
   - En una fila de tabla, si la celda `DAY` tiene texto, abre un día con ese nombre y la fila sigue leyéndose como ejercicio si trae nombre.
   - Una celda `DAY` vacía continúa el día abierto.
   - Una columna «Grupo muscular» (grupo, grupo muscular, muscle o muscle group) no es nombre: va a notas solo si no hay otra nota.
2. **Días en columnas** (`ProgramImportParser`, una función propia):
   - **Cuándo entra:** una fila con 3 o más celdas que sean días («Lunes», «Lun», «Monday», «Mon», «Día 1», «Day 2»…), en columnas distintas y sin papeles de tabla. Esa fila abre una **cuadrícula**: cada columna con día es un día, con el nombre de la cabecera.
   - **Filas de la cuadrícula:** cada fila tiene una etiqueta en la primera celda no vacía antes de la primera columna de día.
     - «Ejercicio N», «Exercise N», «Ejercicio» o una etiqueta vacía con nombres en las columnas: cada celda con texto es un ejercicio nuevo de ese día.
     - «Series x Reps», «Sets x Reps», «Series», «Reps» o «Repeticiones»: notación o números del último ejercicio de ese día.
     - «Peso», «Carga», «Weight» o «kg»: carga.
     - «Descanso» o «Rest»: descanso.
     - «Notas» o «Notes»: notas.
   - **Ejercicios sin series ni repeticiones:** al cerrar la cuadrícula se omiten, con el SKIPPED_ROW de siempre.
   - **Fin de la cuadrícula:** una nueva cabecera de tabla o de cuadrícula, o el fin de la hoja.
3. **Grupos por porcentaje** (`ProgramImportParser`):
   - **Formas** (en una celda, o en el texto tras el nombre):
     - `p%xr`, `p% x r x s`, `p%×r×s`;
     - con barras, `p/r` o `p/r/s`, donde p es el % (30–110), r las repeticiones (1–12) y s las series (1–12).
   - **Barras:** solo cuentan si hay **2 o más** grupos en el texto, para no confundir «4/8» ni una fecha.
   - **Con «%»:** basta un grupo.
   - **Resultado:** un ejercicio por grupo, con el mismo nombre, en orden, con sus series, repeticiones y porcentaje.
   - **Prioridad:** si la fila también tiene «4x8» sin «%», gana la notación de siempre y los grupos no se aplican.
4. **Libras** (modelo, analizador y constructor):
   - **Modelo:** `ImportedProgramDraft.loadUnit: ImportLoadUnit = KG`, serializable y con valor por defecto, para que los borradores guardados sigan leyéndose. `enum class ImportLoadUnit { KG, LB }`.
   - **Detección en el analizador:**
     - es `LB` si la cabecera de carga dice lb, lbs, pounds, libras o «(lb)», o si al menos la mitad de las celdas de carga con número llevan «lb» o «lbs»;
     - una cabecera o celda con «kg» manda sobre todo lo demás y deja `KG`.
   - **Valores:** se guardan tal como vienen, en la unidad del archivo. Las celdas «135 lb», «60kg» o «60 kg» dan el número.
   - **Constructor:** con `LB`, `weight = round(valor × 0,45359237 × 2) / 2` (al 0,5 kg más cercano). Con `KG`, como hoy.
   - **Carga cero:** «0», «0lb» o «0kg» es «sin carga» (peso corporal): `loadKg = null` y sin aviso. Hoy da un IGNORED_VALUE por estar fuera de rango.
5. **Fusión de grupos seguidos** (`ImportedProgramBuilder`):
   - Los ejercicios SEGUIDOS de un mismo día con el mismo `rawName` se convierten en UN ejercicio de la app.
   - Sus series van concatenadas en orden, y cada serie con su propio objetivo (repeticiones, %, carga, RPE y RIR).
   - El descanso es el primero que no sea nulo. Las notas no repetidas se unen en `executionCues`.
   - Si el mismo nombre aparece separado por otro ejercicio, NO se funde.
   - El borrador no cambia: el editor sigue viendo una fila por grupo.

## Pruebas (casos escritos a mano, sin copiar el corpus)
- **Columna de día:** una tabla de 3 días con «Lunes», «Miércoles» y «Viernes» en la columna de día y celdas vacías debajo → 3 días con sus ejercicios.
- **Cuadrícula:** 3 días en columnas con 2 bloques «Ejercicio N / Series x Reps / Peso (kg)» → 3 días × 2 ejercicios con sus valores. Un bloque sin «Series x Reps» → SKIPPED_ROW.
- **Grupos:** «Sentadilla 50%x5, 60%x4, 70%x3x2» → 3 ejercicios (1×5 al 50 %, 1×4 al 60 %, 2×3 al 70 %). «50/5 60/4 70/3/2» igual. «Press 4/8» no es un grupo.
- **Libras:** una cabecera «Weight (lb)» → `LB`; el constructor convierte 135 → 61 kg. «kg» en la cabecera → `KG`. Borrador antiguo sin `loadUnit` → `KG`.
- **Fusión:** 3 grupos seguidos de «Sentadilla» → 1 ejercicio con 4 series y sus porcentajes. Sentadilla, Prensa, Sentadilla → 3 ejercicios.
- **Corpus:** informes con `KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-h3e` (corpus `C:/kw/import-corpus`) y `C:/kw/import-corpus-report-coleccion-h3e` (corpus `C:/Users/valen/Desktop/KPKN_Programas_Recoleccion_2026-10-11/01_originales`, solo lectura). Pon en tu informe el antes y el después de los dos.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ProgramImportParser*" --tests "*ImportedProgramBuilder*" --tests "*ImportDraft*" --tests "*ImportCorpusReporter*" --tests "*ImportActivation*" --tests "*ProgramImportStep*" --tests "*ImportProgramScreen*" --tests "*DomainHasNoAndroidImports*"`

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de los corpus ni de los informes en el repo.

## Prohibido
- La interfaz: mostrar la unidad y el conmutador kg/lb los hace H2b.
- El emparejador, el lector y el editor.
- Cambiar los textos de los avisos que ya existían.
- `MainActivity.kt`.
- Push.

## Informe
- Las reglas, con un ejemplo de cada una.
- Pruebas: número y resultado.
- El antes y el después de los dos corpus.
- Lo que cambiaste de pruebas que ya existían, y por qué.
