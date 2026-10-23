# ED-B · Ejercicio y series en el editor (B1–B5, B3b–B3f con CAL4, B7, B8, B9)

**Worktree:** `C:\kw\u14`, rama `sv2/editor`, @ `db5aa08cf`. Ya lleva ED-A (marco), PROG (reglas de programas y `Apre`), FIX-TM (TM y 1RM separados en toda la app), CAL1–CAL3 y MOT. Trabaja **solo** ahí.

En paralelo trabajan:
- `u17`: ED-D, cardio y superseries (C2–C7).
- `u18`: ED-C, aproximación y movilidad en el editor (H1–H4, B6, D9). Su zona es el bloque de preparación dentro de `ExerciseEditorCard`.
- `u12`: ED-E, hojas del editor (D1–D8) y motores.
- `u15`: CAT-A, catálogo.
- `u16`: G, otros editores.
- `u11`: la sesión en vivo.

Si tocas algo de esas zonas, que sea mínimo y anótalo en «Riesgos de integración».

## Qué leer

- `C:\kw\u14\CLAUDE.md`.
- En `docs/screens-v2/`:
  - `PLAN_IMPLEMENTACION.md`: las leyes y el **esquema de Room congelado**.
  - `DECISIONES_PROTOTIPOS_2026-10-20.md`: la tabla de B1, B2, B3, B5 y B8, y lo aprobado (B4, B7, B9 y «confirmar antes de aplicar»).
  - `CALIBRADOR_V1.md`: §1, §4 (editor, diseño B3b) y §5b, incluido el punto 6, la decisión de FIX-TM.
  - `reports/`:
    - `ED-A.md` (marco y firmas nuevas);
    - `FIX-TM.md` (funciones `percentLoadKg`, `percentLoadReferenceKg`, `percentOfOneRm` y `missingPercentReferences`);
    - `PROG.md` (§ APRE y riesgo 9);
    - `CAL1.md` y `CAL3.md`.
  - `maps/MAPA_editor_catalogo.md` y `audits/AUD2_bugs_editor_catalogo.md`. Sus arreglos ya están: no los rompas.
- Tableros que mandan, en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`:
  - **B1-EjercicioAbierto**
  - **B2-FocoSerie**
  - **B3-FocoRM**
  - **B3b-FocoAMRAP**
  - **B3c-FocoSoloRPE**
  - **B3d-FocoTiempo**
  - **B3e-FocoDistancia**
  - **B3f-FocoPersonalizado**
  - **B4-Modos**
  - **B5-Unilateral**
  - **B7-Descanso**
  - **B8-OpcionesEjercicio**
  - **B9-Dialogos**

  Son de solo lectura, con la lógica en `<script type="text/x-dc">`.

## Qué construir (decisiones del usuario)

Por cada tablero, localiza primero la pantalla actual (archivo y línea) y refínala hasta el tablero. **Refinar, no quitar.**

1. **B1 · Ejercicio abierto.**
   - Bilateral por defecto.
   - La lateralidad pasa al menú ⋯ (B8). Si el ejercicio es unilateral, lleva una etiqueta discreta en su cabecera.
2. **B2 · Foco de serie.** Solo lleva lo de la serie: carga (de solo lectura donde lo diga el tablero), reps, intensidad, el descanso de esa serie y las acciones.
   - Lateralidad, modo y carga inteligente son del ejercicio y quedan fuera del foco.
   - Drop-set y rest-pause solo aparecen si la serie está programada a FALLO, detrás de «¿Vas más allá del fallo?», como mini tarjetas con su configuración.
3. **B3 · Modo RM.** Sin RIR ni RPE: solo porcentaje.
   - Deslizador de %RM con tres cifras enlazadas y editables: reps estimadas, % y peso. Al editar una se recalculan las otras.
   - Apartado de referencia: 1RM, o un PR en kg × reps.
   - **La base depende de la serie:** `PERCENT_TM` usa el TM y el resto el 1RM. Usa las funciones de FIX-TM. Si falta la referencia, muestra el aviso honesto que ya existe («Falta el TM» / «Falta el 1RM»): nunca se infiere.
4. **B3b · Foco AMRAP**, con **CAL4**, según CALIBRADOR_V1 §4:
   - Propósito AMRAP / Calibrador, cada uno con su línea de efecto.
   - Mínimo con −/+ y la opción «Sin mínimo».
   - Solo en Calibrador:
     - el alcance;
     - la vista previa («Ajustará S2–S4 · hoy 60 kg»);
     - el requisito para calibrar;
     - el aviso de peso corporal o asistida.
   - La hoja «¿Qué hace cada uno?».
   - El guardado del editor conserva el propósito de las series de receta en modo RM (INV1 §6-10).
   - El importador marca «5+», «3x5+» y «AMRAP» como `SetPurpose.AMRAP` con su mínimo, si aún no lo hace.
5. **Acción rápida «Bloque APRE» (3, 6 o 10)**, que usa `Apre.block` de PROG.
   - **Decisión del orquestador** (riesgo 9 de PROG): en un bloque APRE **manda la tabla de Mann** (`Apre.set4LoadKg`), igual que en la receta `apre-mann`, por fidelidad a la fuente.
   - Por tanto, la serie 3 del bloque es **AMRAP con mínimo = XRM, no `CALIBRATOR`**. Cambia `Apre.block` y su prueba.
   - Para que más adelante se pueda aplicar en vivo S3→S4 con Mann, el bloque tiene que poder **reconocerse como APRE**, con su variante. Si la receta ya tiene un marcador, reutilízalo. Si no, añade un campo con valor por defecto en el modelo serializado del ejercicio, y **solo si eso no cambia el esquema de Room**. Si lo cambiara, no lo hagas: anótalo en «Riesgos».
   - La aplicación en vivo no es de este paquete.
6. **B3c–B3f · Focos de Solo RPE, Tiempo, Distancia y Personalizado.**
7. **B4 · Modos**: cambiar de modo pide confirmar antes de aplicar.
8. **B5 · Unilateral**: L/R, nunca I/D.
9. **B7 · Descanso**: una sola hoja de descanso.
10. **B8 · Opciones del ejercicio**: recibe «Lateralidad».
11. **B9 · Diálogos**: los diálogos pasan a ser hojas.
12. **Retoque de ED-A:** en los resúmenes de serie, «RIR 2», «RPE 8» y «por lado» no se parten de línea (espacio no separable). Lo vio el orquestador en `eda_main.png`, donde se partía «RIR / 2».

**Leyes:**
- No inventes opciones ni números.
- Objetivos táctiles de al menos 48 dp.
- Paleta KPKN: fondo #000, módulos #111, tinta #F2EEE6; músculo #F49A6E, energía #F7CF73, columna #8FB2FF, mente #C9B8FF, ok #43D18C, peligro #EF6F64. Fuentes Syne e Inter.
- **Esquema de Room congelado.**

## Pruebas

- **Robolectric/Compose, por tablero:**
  - el foco solo con lo de la serie;
  - drop-set y rest-pause solo a FALLO;
  - RM con las tres cifras enlazadas, en ambas direcciones, y con base 1RM y TM, con números y en sus dos casos «falta»;
  - AMRAP y calibrador con el mínimo, el alcance y la vista previa;
  - el Bloque APRE 3, 6 y 10 desde un 1RM, con números de Mann;
  - modos con confirmación;
  - L/R;
  - la hoja de descanso;
  - la lateralidad en ⋯;
  - los diálogos como hojas;
  - el espacio no separable.
- **Renders de revisión** con el arnés existente (`*RenderSheetTest`, `KPKN_RENDER_DIR`): B1, B2, B3, B3b–B3f, B4, B5, B7, B8 y B9, a 360 × 880, con la fuente al 100 % y al 130 %. Van en `C:\kw\shots\edb\`.
- Adapta las pruebas existentes sin rebajar aserciones sin explicarlo.

## Gradle

- Solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u14/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`. La cola es global y compartida.
- Bash corta a los 10 min. Lanza en segundo plano con un log (`C:/kw/logs/edb-<n>.log`) y espera con bucles `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/edb-<n>.log; do sleep 20; done'`, repetidos. Nunca dos corridas tuyas a la vez.
- Tareas: `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest`, este con `--tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.domain.training.*" --tests "com.example.kpkn.domain.calculations.*"` más tus filtros. Júntalas en una corrida cuando puedas.
- **Primera corrida:** es además la base tras la fusión `db5aa08cf`. Anota sus fallos antes de tocar nada y clasifícalos: de la fusión, de antes o del entorno.
- No edites `build.gradle.kts` para filtrar pruebas.
- `ExerciseCatalogImageCoverageTest` falla por el checkout disperso: es del entorno.
- Nunca `--stop`, nunca matar java. Sin teléfono, sin adb, sin emulador y sin push.

## Commits e informe

- Commits por tablero, en español, terminados en `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Haz `git add` solo de tus rutas. Este brief va en tu primer commit.
- Informe en `docs/screens-v2/reports/ED-B.md`:
  - la base tras la fusión;
  - por tablero, de dónde se partía y qué cambió;
  - qué funciones se migraron y a dónde;
  - el marcador APRE elegido;
  - las pruebas, con su comando y su resultado;
  - los renders;
  - los riesgos de integración, sobre todo con ED-C, ED-D y ED-E.
