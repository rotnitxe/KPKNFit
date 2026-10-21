# ER1d · Editor original refinado: el cardio, profesional (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con ER0 integrado: el editor de la PR11). Lee ENTEROS:
- `docs/screens-v1/SESSION_EDITOR_R1.md`, sobre todo §0, §1 y §5. Es tu contrato;
- el mapa del original, `C:\kw\analysis\maps\session_editor_original_map.md`: E14 y los archivos de cardio de la sección 1;
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel) y `docs/wizard-v3/BRIEF_COMUN.md`.

**Lo que dijo el usuario.** El cardio está «mucho menos trabajado». Hay que refinarlo «sin cometer errores previos» y que se vea
«totalmente profesional».

**Regla de oro.** Todo lo que hace hoy el editor de cardio se sigue pudiendo hacer:
- las tres formas (continuo; series de repeticiones con calentamiento, N ×, esfuerzo, pausa, enfriamiento y HIIT/SIT; intervalos
  irregulares);
- tiempo, distancia y GPS;
- «Añadir series»;
- el RPE;
- «Avanzado»;
- el historial y las plantillas destacadas;
- «Al inicio» / «Al final»;
- el editor de bloques con «Duplicar bloque»;
- las plantillas HIIT.
Cambia CÓMO se ve y se lee, no lo que hace.

**Pieza compartida.** `CardioEditorCard` también lo usa la sesión en vivo, en el cajón «Editar series» de un cardio
(`screens/workout/WorkoutStructureSheetsHost.kt`). Su firma NO cambia: el refinado llega a los dos sitios. `CardioIntervalChart`
(`screens/workout/`) es de la sesión en vivo: úsalo y no lo cambies.

Mira también, para ideas de piel (no de estructura), cómo quedó el cardio en el rediseño archivado:
- `git show archive/session-editor-redesign-v1:android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/CardioEditorCard.kt`;
- y `.../design/SessionCardioModule.kt`.

## Qué haces (R1 §5)

### 1. El editor de un cardio (`components/CardioEditorCard.kt`)
En el orden del original, con estos cambios:
- **Arriba, la prescripción en grande:**
  - la frase actual pasa a una línea de dosis en Syne: «20 min · Z2», «8 × 30 s / 30 s · RPE 9», «5 km · 5:30/km»;
  - debajo, la gráfica a todo el ancho (`CardioIntervalChart`, con los colores que ya usa la sesión en vivo: trabajo en el acento y
    recuperación en Columna);
  - el chip «Al inicio / Al final», como `KpknChip`.
- **La forma, como `KpknSegmentedControl`:** «Continuo · Series · Intervalos», con la misma lógica que hoy elige la forma.
  - **Continuo:** Tiempo, Distancia y GPS, cada magnitud en una celda `inset` con la cifra en Syne tabular y su unidad. Al tocarla,
    el selector de `CardioMagnitudePickers` («ruedas»), pasado a una hoja KPKN si hoy es un diálogo de Material.
  - **Series:** calentamiento, «N ×», esfuerzo, pausa, enfriamiento y HIIT/SIT, como filas de módulo con su cifra a la derecha y un
    stepper (`KpknStepperMetric`) donde hoy hay más y menos.
  - **Intervalos:** el editor de bloques (punto 2).
- **La intensidad:** el RPE 1–10 como deslizador KPKN (pista blanca al 8 %, progreso y pulgar en tinta), con la cifra en Syne y la
  zona en palabras («Z2 · cómodo», «Z4 · duro»; usa las etiquetas que ya existan).
- **El historial y las plantillas destacadas:** módulos con su mini gráfica y su dosis, tocables como hoy.
- **«Avanzado»:** un módulo plegado con su flecha.
- Fuera los colores sueltos (el verde degradado, #10B981, #34D399…) y los componentes de Material: a tokens y piezas de
  `ui/design`.

### 2. El editor de bloques (`components/CardioIntervalsEditor.kt`)
- **Cada bloque, una fila de módulo:**
  - su tipo (esfuerzo, pausa, calentamiento, enfriamiento) con un punto del color de la gráfica;
  - la duración o la distancia en Syne;
  - la intensidad;
  - las repeticiones.
- «Duplicar bloque» y quitar, en su «⋯».
- Los pasos de ronda (`CompactRoundStepper`) pasan a `KpknStepperMetric`.
- La gráfica de arriba se actualiza en vivo.

### 3. Plantillas HIIT (`components/HiitTemplatePickerDialog.kt`)
Una `KpknSheet` con cada plantilla en un módulo: su nombre, su dosis y su mini gráfica, y «Usar».

### 4. Textos sueltos
- En `components/CardioCatalogSheet.kt` (catálogo), «Sprint / potencia» pasa a «Sprint y potencia». Solo ese texto.
- En `screens/programdetail/components/SessionPrescriptionSummary.kt`, la línea de cardio dice «SPEED»: tradúcelo («Velocidad») y
  no toques nada más.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*`, `com.example.kpkn.screens.workout.*` (por la pieza compartida) y
  `com.example.kpkn.screens.programdetail.*`.
- **Nuevas:**
  - la línea de dosis de cardio para las tres formas;
  - cambiar de forma en el control segmentado conserva lo que la lógica de hoy conserva;
  - «Duplicar bloque» duplica.
- **Hoja de render** `render/SessionCardioEditorRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en `C:/kw/shots/screens/er1d`:
  - continuo (cinta 20 min Z2);
  - series 8 × 30/30;
  - intervalos irregulares con 5 bloques;
  - el historial y las plantillas;
  - las plantillas HIIT;
  - y el editor de hoy, para comparar.

## Permitido y prohibido
- **Permitido:**
  - `components/CardioEditorCard.kt`, `CardioIntervalsEditor.kt`, `CardioMagnitudePickers.kt`, `CardioHiitEditor.kt` y
    `HiitTemplatePickerDialog.kt`;
  - `SessionEditorViewModelCardio.kt`, solo si algo de la vista lo necesita, sin cambiar la lógica;
  - los dos textos del punto 4;
  - archivos nuevos para las piezas.
- **Prohibido** (lo hacen otros paquetes a la vez):
  - la fila del cardio en la lista (ER1b);
  - el marco (ER1a);
  - el desplegado de fuerza (ER1c);
  - el asistente y las reglas (ER1e);
  - `CardioIntervalChart` y el resto de `screens/workout/`;
  - `ui/design` y `ui/components`, salvo usarlos;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/er1d bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.screens.programdetail.*" --tests "*SessionCardioEditor*" > /c/kw/logs/screens-er1d-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida. Tope: 5 corridas.
- Sin `.dbg`.

## Commit
- `style(sessioneditor): el editor de cardio con su dosis grande, la forma segmentada y magnitudes KPKN (ER1d)`.
- `style(sessioneditor): el editor de bloques y las plantillas HIIT con la piel nueva (ER1d)`.
- `fix(sessioneditor): textos de cardio en español (ER1d)`.
- `test(sessioneditor): hoja de render del cardio (ER1d)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto y dónde.
- Los PNG, con tu comparación frente al de hoy.
- Pruebas: número y resultado.
