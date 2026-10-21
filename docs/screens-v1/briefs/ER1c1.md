# ER1c1 · Editor original refinado: el ejercicio desplegado (preparación, opciones y el editor de series) (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con ER0 y la separación de ER1b integradas). Lee ENTEROS:
- `docs/screens-v1/SESSION_EDITOR_R1.md`, sobre todo §0, §1, §4.1, §4.2 y §4.3. Es tu contrato;
- el mapa del original, `C:\kw\analysis\maps\session_editor_original_map.md`: E9, E10, E11, E12 y E20 (la carga inteligente);
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel) y `docs/wizard-v3/BRIEF_COMUN.md`.

**Lo que dijo el usuario.** Aquí sí quiere «mejores formas»: las opciones de serie y sus modos, y añadir aproximación y movilidad a un
ejercicio. Le costaba encontrar dónde poner la aproximación o la movilidad. Pasar a unilateral le devolvía las tarjetas viejas. Pero
REFINAR: «abrazar la complejidad de KPKN», no recortarla.

**Regla de oro.** TODOS los controles de la tarjeta de serie del original (`InlineSetRow`, E10) siguen existiendo, uno por uno:
- el tipo de carga (Carga externa, Peso corporal, Lastre, Asistido);
- reps o rango («8-10»), reps mínimas en AMRAP, tiempo, distancia o unidad según el modo;
- la intensidad POR SERIE (RPE / RIR / Fallo y su valor);
- en modo RM, %RM o % del TM con su deslizador de 45 a 100 y «Reps est.»;
- en Solo RPE, «RPE obj.»;
- AMRAP y AMRAP calibrador;
- la caja de carga («X kg · N % RM» o «Usa carga inteligente…»);
- las técnicas Drop-set (1–3 drops) y Rest-pause (1–5 mini-series) con sus notas.
Y en el ejercicio:
- el descanso (con el de lados en unilateral);
- los 7 modos;
- unilateral, con su orden y lados iguales o aparte;
- la carga inteligente;
- la unidad personalizada;
- el panel de relación;
- la aproximación y la movilidad con todo lo suyo.

## Qué haces

### 1. Preparación del ejercicio, a la vista (R1 §4.1; E11 y E12)
- **Arriba del desplegado,** en el orden del original: Movilidad, Aproximación, Series.
- **Si no hay ninguna de las dos:** una fila con dos píldoras de borde punteado (filete blanco al 24 %, 40 dp), «+ Movilidad» y
  «+ Aproximación»:
  - «+ Movilidad» abre el catálogo de movilidad (`MobilityPickerSheet`, como «+ Agregar movilidad»);
  - «+ Aproximación» crea la primera, como «+ Añadir aproximación» (50 %, 8 reps, 60 s).
- **Con contenido, cada una es un bloque plegable** con su ceja y su contador («APROXIMACIÓN · 3»):
  - **Aproximación:** cada serie en una FILA (no en tarjeta de carrusel):
    - «1 · 50 % · 8 reps · 48 kg», con la carga sugerida;
    - al tocarla se edita en sitio el % de carga y las reps, con `KpknNumberCell`;
    - «Descanso entre series 1:00» en su fila, que abre el reloj como hoy;
    - «+ Añadir aproximación» (sube el 10 % hasta el 90 % y baja 2 reps, como el original);
    - los avisos de validación, en `danger`.
  - **Movilidad:** cada ejercicio en una FILA con su nombre, su tiempo o reps y su ×. «Tiempo global de movilidad» como fila de
    cabecera con su reloj, y «+ Agregar otra».
- Si hay una y no la otra, la que falta sigue como píldora punteada debajo, para que no haya que buscarla.

### 2. La barra de opciones del ejercicio (R1 §4.2; E9 punto 4)
- **Una fila de píldoras con rótulo**, a la vista, que pasa a una segunda línea si no cabe:
  - «Modo: Reps ▾» (`KpknChip`): una `KpknSheet` con los 7 modos del original (Reps, AMRAP, RM, Solo RPE, Tiempo, Distancia,
    Personalizado), cada uno con una línea que explica qué es («RM: la carga como % de tu máximo», «Solo RPE: sin carga fija, por
    esfuerzo»…). Elegir llama a lo mismo que `CompactModeSelector`;
  - «Descanso 1:30» (en unilateral, «Series 1:30 · Lados 0:15»): el mismo selector encadenado del original (`KpknRestPickerChain`);
  - «Bilateral | Unilateral»: un `KpknSegmentedControl` pequeño. Hace lo que hacía «⋮ → Cambiar a unilateral»
    (`toggledBilateralUnilateral`, con 15 s entre lados);
  - «⋯»: Medir ROM, Relacionar (o «Ancla: X»), Superserie, Unidad personalizada, Carga inteligente y Meta.
- **Debajo, en el orden del original:**
  - «Carga inteligente», como módulo de una línea (no en Solo RPE);
  - en unilateral, «Lados»: orden I/D (`SideOrderChip`), lados iguales o aparte, y el descanso entre lados, en UN solo módulo;
  - «Unidad personalizada» en su modo;
  - el panel de relación (tipo y «Notas de la relación»).

### 3. Las series: un solo editor, completo (R1 §4.3)
- **`ExerciseSetsCarousel` (`SessionEditorControls.kt`) cambia por dentro y conserva su nombre y su firma**, porque también la usa la
  sesión en vivo (`screens/workout/WorkoutStructureSheetsHost.kt`, cajón «Editar series»). El cajón recibe el editor nuevo sin
  tocarlo.
- **Lista vertical:** cada serie es una fila compacta de 56 dp con:
  - su número (o «I1»/«D1» en unilateral);
  - el tipo de carga si no es la externa;
  - la dosis en Syne tabular («80 kg × 6», «8–10», «45 s»);
  - la intensidad («RIR 2», «RPE 8», «Fallo», «80 %»);
  - la técnica si la hay («Drop × 2», «RP × 3»).
- **Tocar una fila la despliega en sitio** con TODOS los controles de la tarjeta del original (lista de arriba), con piezas KPKN:
  `KpknNumberCell` / `KpknTextField`, `KpknSegmentedControl` para RPE / RIR / Fallo, `KpknChip` para el tipo de carga y las
  técnicas, y el deslizador KPKN para el %RM. Reutiliza la lógica de `InlineSetRow` e `InlineSetRowTechniqueChips`: cambia la piel,
  no los cálculos. Solo una fila abierta a la vez.
- **Acciones de la fila abierta** (`KpknTextAction` en una fila):
  - «Aplicar a todas»: copia esta configuración al resto de series del ejercicio. Es nuevo; con confirmación si hay series distintas;
  - «Duplicar»;
  - «Subir» / «Bajar» (las acciones ya llegaban a la tarjeta, sin pintarse);
  - «Eliminar» (en `danger`). Queda al menos una; en una superserie deja el «hueco» de la ronda como el original.
- **Unilateral:** cada serie muestra I y D como dos sub-filas con su letra, sin el azul y el rojo del original. Si falta un lado,
  «Añadir I» o «Añadir D».
- **Al pie, «+ Añadir serie»** (copia la última; en RM baja el %, como el original). Vacío: «No hay series añadidas» con «+ Añadir
  serie».

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*` y `com.example.kpkn.screens.workout.*` (por el cajón «Editar
  series»).
- **Nuevas:**
  - con las dos vacías salen las dos píldoras, y cada una crea su bloque;
  - una fila de serie se abre y cierra, y abrir otra cierra la primera;
  - cada control de la tarjeta original existe en la fila abierta en los 7 modos (una prueba por modo);
  - «Aplicar a todas»;
  - «Duplicar»;
  - «Subir» y «Bajar»;
  - «Eliminar» deja al menos una;
  - unilateral con «Añadir I/D»;
  - el control Bilateral / Unilateral hace lo de `toggledBilateralUnilateral`;
  - la hoja de modos elige el modo.
- **Hoja de render** `render/SessionExerciseExpandedRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en
  `C:/kw/shots/screens/er1c1`:
  - un ejercicio bilateral RM con RIR (fila cerrada y abierta);
  - uno unilateral;
  - uno en AMRAP;
  - uno en Tiempo;
  - la preparación vacía (las dos píldoras), con 3 aproximaciones y con movilidad;
  - la hoja de modos;
  - el cajón «Editar series» de la sesión en vivo con el editor nuevo.

## Permitido y prohibido
- **Permitido:**
  - `components/ExerciseEditorExpanded.kt` (lo separó ER1b);
  - `SessionEditorControls.kt`;
  - `components/InlineSetRow.kt`, `components/InlineSetRowTechniqueChips.kt`, `components/SetEditorCards.kt` y
    `components/PreparationCarouselCards.kt`;
  - `SessionEditorFormFields.kt`;
  - un archivo NUEVO `SessionEditorViewModelSets.kt` para las acciones de serie («Aplicar a todas», subir y bajar), sin tocar los
    demás archivos del VM salvo lo justo para llamarlas;
  - archivos nuevos para las piezas.
- **Prohibido** (lo hacen otros paquetes a la vez):
  - las acciones rápidas, crear superserie y el editor de rondas (ER1c2);
  - la lista y el plegado (ER1b);
  - el cardio (ER1d);
  - el asistente, las reglas y los diálogos (ER1e);
  - el marco (ER1a);
  - el catálogo;
  - `screens/workout/`, que no se toca: el cajón recibe la pieza;
  - `ui/design` y `ui/components`, salvo usarlos;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/er1c1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.screens.workout.*" --tests "*SessionExerciseExpanded*" > /c/kw/logs/screens-er1c1-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida. Tope: 6 corridas (es el paquete más grande).
- Sin `.dbg`.

## Commit
- `style(sessioneditor): aproximación y movilidad a la vista dentro del ejercicio (ER1c1)`.
- `style(sessioneditor): la barra de opciones del ejercicio con modo, descanso y unilateral a la vista (ER1c1)`.
- `feat(sessioneditor): las series en lista con todos sus controles al desplegar, aplicar a todas, duplicar y mover (ER1c1)`.
- `test(sessioneditor): hoja de render del ejercicio desplegado (ER1c1)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto y dónde.
- La tabla de controles del original, cada uno con dónde está ahora.
- Los PNG, con tu comparación frente al original.
- Pruebas: número y resultado.
