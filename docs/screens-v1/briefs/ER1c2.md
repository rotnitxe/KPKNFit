# ER1c2 · Editor original refinado: acciones rápidas, crear superserie y el editor de rondas (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con ER0 integrado). Lee ENTEROS:
- `docs/screens-v1/SESSION_EDITOR_R1.md`, sobre todo §0, §1, §4.4 y §4.5. Es tu contrato;
- el mapa del original, `C:\kw\analysis\maps\session_editor_original_map.md`: E12 (el catálogo de movilidad), E13, E15, E16 y E19;
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel) y `docs/wizard-v3/BRIEF_COMUN.md`.

**Lo que dijo el usuario.** Le costaba muchísimo encontrar «dónde creo una superserie a partir de ejercicios existentes». Quiere
mejores formas para convertir en superserie, sin recortar nada.

**Regla de oro.** Todo lo del original sigue:
- crear una superserie desde «⋮» o desde el catálogo con la selección múltiple;
- el máximo de 4;
- los miembros con quitar y reordenar;
- tocar un miembro para su configuración;
- las rondas con su descanso entre ejercicios y al final de la ronda;
- «Añadir ronda»;
- «Convertir en Superset opcional»;
- «Disolver»;
- quitar un miembro con sus tres opciones;
- «Gestionar superserie»;
- las plantillas de sesión;
- el catálogo de movilidad.

## Qué haces

### 1. Acciones rápidas (R1 §4.4; E15)
- La hoja de la pulsación larga en el nombre (`ExerciseQuickActionsSheet`, `components/sheets/TemplatesAndQuickActionSheets.kt`)
  recibía calentamiento, movilidad, borrar y superserie, pero solo pintaba «Cambiar».
- Ahora pinta TODAS, como filas de opción KPKN (icono en círculo `inset` de 36 dp y texto Inter 15), con la información del
  ejercicio arriba como hoy:
  - «Ficha»;
  - «Cambiar»;
  - «+ Aproximación»;
  - «+ Movilidad»;
  - «Crear superserie» (abre el punto 2);
  - «Duplicar ejercicio» (nuevo: copia el ejercicio con sus series justo debajo, con un `occurrenceId` nuevo si es del catálogo);
  - «Eliminar» en `danger`. Usa el «Deshacer» de ER1b si ya existe en el VM; si no, pide confirmación con `KpknAlertDialog`.

### 2. Crear superserie con ejercicios que ya están (R1 §4.5; E13)
- `SupersetMemberPickerSheet` (`components/sheets/SupersetCreatorAndManagerSheets.kt`) pasa de «toca otro y se crea» a una lista con
  casillas:
  - todos los ejercicios libres de la sesión, por grupo, con su miniatura y su dosis;
  - el actual va marcado;
  - se marcan de 2 a 4; al llegar a 4 el resto se apaga con la nota «Máximo 4»;
  - el botón fijo abajo: «Crear superserie con 3» (`KpknPrimaryPill`), apagado con menos de 2;
  - «Abrir catálogo», que sigue como `KpknTextAction`.
- Los descansos de partida son los del original (60 / 120 s).
- La creación usa la función del VM que ya existe. Si solo admite dos, añade la de varios en un archivo NUEVO
  `SessionEditorViewModelQuickActions.kt`, junto con «Duplicar ejercicio».

### 3. El editor de rondas, solo con la piel (E13; `components/SupersetRoundsAndConfig.kt` y `SupersetManagerSheet`)
- **Las rondas:** en vez del carrusel de tarjetas de 320 dp, una lista vertical de rondas (como las series de ER1c1). Cada ronda,
  una fila con sus miembros y su dosis; al tocarla se despliega con las tarjetas de cada miembro.
- «Añadir ronda» al pie.
- **El botón de descansos** «Entre 1:00 · Ronda 2:00», como píldora, con el mismo selector encadenado.
- **«Configuración del ejercicio» de un miembro** (`SupersetExerciseConfigOverlay`), los diálogos de carga inteligente y de meta, y
  el «hueco vacío» de una ronda: la piel nueva. Los campos de número ya usan `rememberEditableNumber` (ER0): no lo rompas.
- **`SupersetManagerSheet`:** los descansos en el formato de la app (m:ss), con el mismo selector.

### 4. Plantillas de sesión y catálogo de movilidad, solo con la piel
En el mismo archivo `TemplatesAndQuickActionSheets.kt`: `TemplatesSheet` y `MobilityPickerSheet` con piezas KPKN (buscador
`KpknTextField`, filas de opción y botones píldora).

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*`.
- **Nuevas:**
  - la hoja de acciones rápidas pinta las 7 acciones y cada una llama a lo suyo;
  - «Duplicar ejercicio» lo pone debajo con un `occurrenceId` nuevo;
  - crear superserie con 3 marcados crea una de 3;
  - con 4 marcados, el quinto no se puede marcar;
  - con 1, el botón está apagado;
  - las rondas en lista se abren y cierran;
  - «Añadir ronda».
- **Hoja de render** `render/SessionSupersetQuickActionsRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en
  `C:/kw/shots/screens/er1c2`:
  - las acciones rápidas;
  - crear superserie con 0, 2 y 4 marcados;
  - el editor de rondas cerrado y con una ronda abierta;
  - la configuración de un miembro;
  - `SupersetManagerSheet`;
  - las plantillas;
  - el catálogo de movilidad.

## Permitido y prohibido
- **Permitido:**
  - `components/sheets/TemplatesAndQuickActionSheets.kt` y `components/sheets/SupersetCreatorAndManagerSheets.kt`;
  - `components/SupersetRoundsAndConfig.kt`;
  - un archivo NUEVO `SessionEditorViewModelQuickActions.kt`, sin tocar los demás archivos del VM salvo lo justo para llamarlo;
  - archivos nuevos para las piezas.
- **Prohibido** (lo hacen otros paquetes a la vez):
  - el desplegado del ejercicio y el editor de series (ER1c1);
  - la lista y la cabecera de la superserie (ER1b);
  - el cardio (ER1d);
  - el asistente, las reglas y los diálogos (ER1e);
  - el marco (ER1a);
  - el catálogo;
  - `ui/design` y `ui/components`, salvo usarlos;
  - `screens/workout/`;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/er1c2 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*SessionSupersetQuickActions*" > /c/kw/logs/screens-er1c2-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida. Tope: 5 corridas.
- Sin `.dbg`.

## Commit
- `style(sessioneditor): las acciones rápidas pintan todas sus acciones, y duplicar ejercicio (ER1c2)`.
- `feat(sessioneditor): crear superserie marcando de 2 a 4 ejercicios de la sesión (ER1c2)`.
- `style(sessioneditor): el editor de rondas en lista y la configuración de miembros con la piel nueva (ER1c2)`.
- `style(sessioneditor): plantillas de sesión y catálogo de movilidad con la piel nueva (ER1c2)`.
- `test(sessioneditor): hoja de render de superseries y acciones rápidas (ER1c2)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto y dónde.
- Los PNG, con tu comparación frente al original.
- Pruebas: número y resultado.
