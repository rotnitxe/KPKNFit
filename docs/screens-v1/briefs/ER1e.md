# ER1e · Editor original refinado: Asistente, Reglas y el resto de hojas y diálogos (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con ER0 integrado: el editor de la PR11). Lee ENTEROS:
- `docs/screens-v1/SESSION_EDITOR_R1.md`, sobre todo §0, §1 y §6. Es tu contrato;
- el mapa del original, `C:\kw\analysis\maps\session_editor_original_map.md`: E20, E22, E23, E25 y la sección 3 (Impacto);
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel) y `docs/wizard-v3/BRIEF_COMUN.md`.

**Lo que dijo el usuario.** El Asistente de sesión y las Reglas «no pueden quedar tan en segundo plano». El impacto de la sesión va en
el asistente. ER1a les da dos de los tres botones de la barra inferior. Tú haces que, al abrirlos, estén a la altura.

**Regla de oro.** Todo lo que hacen hoy el asistente, las reglas y las hojas se sigue pudiendo hacer:
- las tres pestañas del asistente;
- aplicar sugerencias seleccionadas;
- las plantillas con «Reemplazar» o «Añadir al final»;
- el alcance de las reglas (sesión, grupo, o compuestos y aislados), sus valores, los descansos, «Aplicar a nuevos elementos», las
  plantillas de reglas y «Aplicar»;
- la pestaña de tiempo con su reloj, sus sugerencias y sus tiempos por grupo y por ejercicio;
- Versiones con restaurar;
- Relacionar;
- Meta, Unidad personalizada y Carga inteligente.
Cambia CÓMO se ven.

**Piezas que puedes reaprovechar** del rediseño archivado (solo la piel; la estructura y las funciones son las del original). Míralas
con `git show archive/session-editor-redesign-v1:android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/design/<archivo>`:
- `SessionAssistantSheet.kt`, `SessionAssistantImpactTab.kt`, `SessionAssistantSuggestionsTab.kt` y `SessionAssistantTemplatesTab.kt`;
- `SessionAssistantLoading.kt`;
- `SessionImpactFigures.kt`, `SessionImpactModels.kt` y `SessionImpactMuscleRow.kt`.

## Qué haces (R1 §6)

### 1. Asistente (`components/sheets/AssistantSheet.kt`)
- **Una hoja alta** con el título «Asistente de sesión» en Syne y un `KpknSegmentedControl`: «Impacto · Sugerencias · Plantillas».
  «MÉTRICAS» pasa a llamarse «Impacto».
- **Impacto:**
  - la torre de RINGS (`KpknTowerRings`) con Músculos, Energía y Columna, y sus cifras en Syne;
  - la duración de la sesión (la misma fuente única);
  - el volumen por músculo, en filas con barra, plegable como hoy;
  - las calorías estimadas.
- **Sugerencias:**
  - cada una en un módulo con su casilla (`KpknCheckRow`), qué cambia y por qué en una línea;
  - «Aplicar selección» fijo abajo (`KpknPrimaryPill`);
  - si no hay, un estado vacío que lo diga.
- **Plantillas:** el buscador (`KpknTextField` con lupa) y las tarjetas de `TemplateCatalogBrowser` / `TemplateCatalogCards` con la
  piel nueva; elegir una pregunta «Reemplazar» o «Añadir al final» en `KpknAlertDialog`.
- Mientras calcula, el esqueleto (`KpknSkeleton`), no un spinner de Material.

### 2. Reglas y tiempo (`components/sheets/RulesSheet.kt`)
- Título «Reglas y tiempo» y un `KpknSegmentedControl`: «Reglas · Tiempo».
- **Reglas:**
  - el alcance como `KpknSegmentedControl`: Sesión, Grupo, o Compuestos y aislados;
  - los valores (intensidad RPE / RIR / Fallo, series y reps) con `KpknNumberCell`;
  - los descansos (Normal, Lados, Entre ejercicios, Rondas) con el selector de descanso de la app;
  - «Aplicar a nuevos elementos» como `KpknSwitch`;
  - las plantillas de reglas como chips;
  - la barra fija «Aplicar» (`KpknPrimaryPill`).
- **Tiempo:**
  - el reloj de sesión en grande: el anillo de la barra inferior (estimado frente al límite, con los umbrales de ER1a) y, al lado,
    «Estimado», «Límite» y «Diferencia» en Syne;
  - las sugerencias para entrar en el límite, como módulos;
  - el tiempo de sesión;
  - los tiempos por grupo y por ejercicio, en filas.
- **Arreglo de lógica** (commit aparte, con prueba que falle sin él): `SessionEditorRulesEngine.normalizeSet` (privado) sigue
  borrando el rango, el RIR y el RPE de las series en modo RM al aplicar reglas. Con el mismo criterio que SE11 (ver
  `normalizedForEditorSave`), aplicar reglas solo cambia lo que la regla fija.

### 3. El resto de hojas y diálogos, solo con la piel
- **`components/ExerciseEditorDialogs.kt`** (Carga inteligente, Meta y Unidad personalizada):
  - piezas KPKN;
  - y que el teclado NO tape el campo ni el botón: relleno de teclado y desplazamiento, como hizo SE11 en el rediseño
    (`cec9fb93c`, míralo con `git show`). Era un fallo también del original.
- **`components/sheets/HistorySheet.kt`** (Versiones): cada versión como fila de módulo con la fecha legible y «Restaurar», que pide
  confirmación como hoy.
- **`components/sheets/RelationshipPickerSheet.kt`** (Relacionar): el buscador y la lista con la piel nueva.
- **`components/TemplateCatalogBrowser.kt`** y **`components/TemplateCatalogCards.kt`**: las tarjetas de plantilla con la piel nueva.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*`.
- **Nuevas:**
  - el control segmentado del asistente cambia de pestaña;
  - «Aplicar selección» aplica solo lo marcado;
  - la pestaña Tiempo pinta el anillo con su color por umbral;
  - el arreglo de `SessionEditorRulesEngine`;
  - la Meta con el teclado simulado deja el campo a la vista.
- **Hoja de render** `render/SessionAssistantRulesRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en `C:/kw/shots/screens/er1e`:
  - el asistente en sus tres pestañas;
  - Reglas y Tiempo (dentro y fuera del límite);
  - Versiones;
  - Meta;
  - Carga inteligente.

## Permitido y prohibido
- **Permitido:**
  - `components/sheets/AssistantSheet.kt`, `RulesSheet.kt`, `HistorySheet.kt` y `RelationshipPickerSheet.kt`;
  - `components/ExerciseEditorDialogs.kt`, `components/TemplateCatalogBrowser.kt` y `components/TemplateCatalogCards.kt`;
  - `SessionEditorRulesEngine.kt`, solo para el arreglo;
  - archivos nuevos para las piezas.
- **Prohibido** (lo hacen otros paquetes a la vez):
  - `components/sheets/SessionEditorSheets.kt` y `components/sheets/ClonerSaveWarmupSheets.kt` (ER1a);
  - `components/sheets/TemplatesAndQuickActionSheets.kt` y `SupersetCreatorAndManagerSheets.kt` (ER1c);
  - la lista (ER1b);
  - el cardio (ER1d);
  - el marco (ER1a);
  - el catálogo;
  - `ui/design` y `ui/components`, salvo usarlos;
  - `screens/workout/`;
  - push;
  - el teléfono y el emulador.
- Si para abrir una hoja hace falta tocar `SessionEditorSheets.kt`, dímelo en vez de tocarlo.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/er1e bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*SessionAssistantRules*" > /c/kw/logs/screens-er1e-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida. Tope: 5 corridas.
- Sin `.dbg`.

## Commit
- `style(sessioneditor): el asistente con Impacto, Sugerencias y Plantillas en piezas KPKN (ER1e)`.
- `style(sessioneditor): Reglas y tiempo con el reloj de sesión a la vista (ER1e)`.
- `fix(sessioneditor): aplicar reglas no borra el rango ni el RIR de los básicos (ER1e)`.
- `style(sessioneditor): versiones, relacionar y los diálogos del ejercicio con la piel nueva y sin tapar el teclado (ER1e)`.
- `test(sessioneditor): hoja de render del asistente y las reglas (ER1e)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto y dónde.
- Los PNG, con tu comparación frente al original.
- Pruebas: número y resultado.
