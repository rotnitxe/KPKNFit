# ER2a · Editor: el guardado no cambia las cargas y el modo RM enseña su rango y su RIR (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `cad9f1c16`). Lee antes:
- `docs/screens-v1/SESSION_EDITOR_R1.md` (§0 y §4);
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel);
- `docs/wizard-v3/BRIEF_COMUN.md`.

## Lo que vimos en el teléfono (prueba del 15-oct, 11:14–11:21)
Programa generado «Base sólida», jueves «Pierna A». Antes de abrir el editor, Inicio decía:
- Sentadilla Trasera con Barra Alta: `4 × 4–6 · 95 kg`;
- Peso Muerto Piernas Rígidas: `3 × 6–10 · 70 kg`.

Abrimos el editor y creamos una superserie (sin tocar ninguna carga). Al salir, el guardado automático dejó en Inicio `96 kg` y
`70,8 kg`. **El editor reescribió las cargas de los básicos.** Es la misma familia de fallo que el usuario ya denunció («los básicos
reescritos por el editor») y no puede salir en la PR12.

### Causa (verificada en el código)
1. **`normalizeSet`** (`SessionEditorSessionHelpers.kt`, final de la función, ~L644-645) termina con
   `normalized.copy(weight = … autoWeight ?: normalized.weight)`. En modo RM, `autoWeight = calculateSuggestedLoad(...)` vale
   siempre `capacidad × %RM`, así que **cada pasada de normalización pisa la carga guardada**.
2. **Dos redondeos distintos:**
   - el generador escribe `Prescriber.roundLoad(oneRm × % / 100)`, a pasos de 2,5 kg (`domain/training/generator/Prescriptions.kt:115`): 95 y 70;
   - el editor usa `calculateSuggestedLoad` → `roundSuggestedLoad`, a pasos de 0,25 kg (`domain/calculations/Calculations.kt:139`): 96 y 70,75.
3. **La fila muestra la carga calculada, no la guardada:** `setRowSummary` (`components/ExerciseSetList.kt` ~L372) usa
   `predictedWeight ?: set.weight`.

### Segundo problema: el modo RM esconde la prescripción
- Los básicos del generador son RM con rango y RIR: `4 × 4–6 · 80 % 1RM · RIR 2`, como se ve en la dosis plegada.
- Pero la fila de la serie dice `96 kg × 7`, donde 7 son las «Reps est.» (las que salen al 80 %).
- La serie abierta solo tiene «%RM» y «Reps est.». El rango 4–6 y el RIR 2 no se ven ni se pueden editar.
- La persona lee «haz 7» cuando el plan dice 4–6 a RIR 2. La sesión en vivo, en cambio, propone 6 reps a RIR 2.

## Qué haces

### 1. La carga guardada manda
- **`normalizeSet` no pisa una carga guardada positiva** en modo RM. Solo la rellena si está vacía (0 o nula), y entonces la redondea
  como el generador.
- **La carga se recalcula SOLO por una acción explícita** de la persona en el editor, y siempre redondeada con la misma función
  que el generador (`Prescriber.roundLoad`, o una función común que ambos usen):
  - cambiar el %RM de una serie (campo, deslizador o «Aplicar a todas»);
  - cambiar la referencia de 1RM o la carga inteligente del ejercicio.
  - Encuentra TODOS esos caminos (busca `targetPercentageRM` en `screens/sessioneditor/` y lo que cambia la referencia) y haz que
    cada uno fije la carga.
- **No cambies `calculateSuggestedLoad` de `domain/calculations`**: también lo usa la sesión en vivo. El redondeo nuevo vive en el
  editor.
- **La fila y la dosis plegada enseñan la carga guardada.** La calculada solo aparece si no hay guardada.

### 2. El modo RM con rango y RIR (solo si la serie los trae)
- **Fila cerrada:** `95 kg × 4–6 · RIR 2`, con el `80 %` a la derecha como ahora.
- **Serie abierta:**
  - dos campos más junto a «%RM»: «Reps» (rango, el mismo control que el modo Reps) y «RIR» (el mismo control que ya usa la app);
  - «Reps est.» deja de ser un campo y pasa a una línea de ayuda debajo: «≈ 7 reps máx. al 80 %».
- **Una serie RM pura** (sin rango ni RIR) se queda como hoy.
- **Al editar:**
  - el rango y el RIR se guardan en `targetRepsRange` y `targetRIR`, que SE11 ya conserva en la normalización;
  - no toques cómo se calculan el %RM ni las «Reps est.».

### 3. El tipo de carga en rejilla
- `LoadTypeRow` (`components/InlineSetRow.kt` ~L436) promete «una rejilla 2 × 2 de celdas iguales» cuando el segmentado no cabe.
- En el teléfono, con la letra de la persona, salieron chips de anchos distintos: cada `KpknChip` envuelve su texto dentro de su
  celda.
- Que cada chip llene su celda, para que las cuatro midan lo mismo.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*` y `com.example.kpkn.domain.calculations.*`.
- **Nuevas (que fallen sin el arreglo):**
  - una sesión del generador (sentadilla al 80 % de 120 → 95 kg) pasa por `normalizeSet` y por el guardado del editor y sigue en 95;
  - crear una superserie no cambia ninguna carga del programa;
  - cambiar el %RM a 85 deja 102,5 kg (redondeo de 2,5);
  - cambiar la referencia de 1RM recalcula las series RM del ejercicio, redondeadas;
  - la fila RM con rango y RIR dice `95 kg × 4–6 · RIR 2`;
  - la serie abierta tiene «Reps» y «RIR», y editar el RIR lo guarda;
  - una serie RM pura se ve como antes.
- **Hoja de render:** escenas nuevas en `render/SessionExerciseExpandedRenderSheetTest`, al 100 % y al 130 %:
  - la fila RM con rango y RIR, cerrada y abierta;
  - la rejilla 2 × 2 al 130 %.

## Permitido y prohibido
- **Permitido:**
  - `screens/sessioneditor/`: `SessionEditorSessionHelpers.kt`, `SessionEditorViewModelSets.kt`, `components/ExerciseSetList.kt`,
    `components/InlineSetRow.kt`, `SessionExerciseDose.kt` y los archivos del VM que cambian el %RM o la referencia;
  - archivos nuevos en `screens/sessioneditor/`;
  - pruebas.
- **Prohibido:**
  - `domain/calculations/Calculations.kt` y `domain/training/` (solo puedes llamar a `Prescriber.roundLoad`);
  - `screens/workout/`;
  - los grupos, el asistente, las reglas, el cardio y el marco (los toca ER2b a la vez);
  - `ui/design` y `ui/components`, salvo usarlos;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/er2a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.domain.calculations.*" --tests "*SessionExerciseExpandedRenderSheet*" > /c/kw/logs/screens-er2a-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida.
- Tope: 5 corridas.
- Sin `.dbg`.

## Commit
- `fix(sessioneditor): el guardado no cambia las cargas del programa; el %RM las recalcula redondeadas como el generador (ER2a)`.
- `feat(sessioneditor): las series RM enseñan y editan su rango y su RIR (ER2a)`.
- `style(sessioneditor): el tipo de carga en rejilla de celdas iguales (ER2a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Por punto: qué cambió y dónde, y la lista de caminos que recalculan la carga.
- Los PNG.
- Pruebas: número y resultado.
