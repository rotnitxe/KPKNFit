# ED-A · Marco del editor de sesiones (Main/A1, A2, A2b, A3, A4, A5, A6, C1)

**Worktree:** `C:\kw\u14`, rama `sv2/editor` (de `feat/screens-v2` @ `373729ae0`). Todo el trabajo está dentro de ese worktree; no se ha tocado `u11`, `u12`, `u13`, el checkout principal ni ningún otro. Sin push. Sin teléfono, `adb` ni emuladores: la verificación visual es con renders de Robolectric y la funcional con pruebas de Compose.

## Resumen

Los ocho tableros están implementados sobre el código existente, refinando lo que había (nada se ha quitado; lo que cambia de sitio se migra y se prueba). No se han reescrito los motores (Impacto, sugerencias, creador) ni se han roto los arreglos de AUD2: todas las pruebas de AUD2 del editor siguen en verde.

## Commits (rama `sv2/editor`, sin push)

| Tablero | Commit |
|---|---|
| A1 | `fca2110a4` marco como Main, sin RIR global, isla inferior |
| A2, A2b | `5bea77c37` sesión vacía y solo cardio |
| A5, A6 | `892718046` semana abierta y estados con un solo aviso |
| C1 | `3d550c955` superserie dentro de su grupo |
| A3 | `fa4c2707c` arrastre libre con señal por destino |
| A4 | `6de36723d` aviso de borrar con cuenta atrás |
| Renders | hoja de renders `SessionEditorEdaRenderSheetTest` (commit «test(editor): hoja de renders…») |
| Informe | este documento |

Los archivos compartidos (`SessionEditorScreen.kt`, `SessionEditorScrollRenderer.kt`) se partieron por trozos para que cada commit lleve solo lo de su tablero; solo el estado final de la rama se compiló y se probó (los commits intermedios no se han compilado uno a uno).

## Qué cambió por tablero

### A1 · Main (marco del editor)
- **Sin RIR global**: la cabecera ya no lleva el chip «Fuerza · RIR 2». El resumen pasa a fichas (`ejercicios · series`, `≈ N min`); si la sesión es solo de cardio, la ficha de series no aparece (`sessionFrameSummaryChips(..., showSets)`).
- Barra superior con «Día · Semana N»: la semana solo se nombra si el programa las distingue (varias semanas, o una simple con bucles).
- Isla inferior (64 dp): anillo de tiempo (botón propio, abre **Tiempo**), «+ Añadir», «Asistente» con su contador y «Reglas» (abre **Reglas**).
- Pie de cada grupo: botón de trazos «Añadir a *Grupo*» (antes «Añadir ejercicio a *Grupo*»). Rótulos `CARDIO · AL FINAL/AL INICIO` con acento de columna y línea.
- Archivos: `frame/SessionEditorFrameTopBar.kt`, `SessionEditorFrameHeader.kt`, `SessionEditorFrameBottomBar.kt`, `SessionEditorFrameLogic.kt`, `design/SessionGroupLabel.kt`, `design/SessionEditorDashedAddButton.kt` (nuevo), y las llamadas en `SessionEditorScreen.kt`/`SessionEditorScrollRenderer.kt`.

### A2 · Sesión vacía / A2b · Solo cardio
- Sesión vacía: solo **Fuerza** y **Cardio**, cada una con su símbolo animado (pesa y pulso). Al tocar una aparece su apartado y la otra queda abajo como opción pendiente («AÑADIR A LA SESIÓN»), grande si todavía no hay ejercicios y como píldora («Crear espacio de cardio» / «Añadir fuerza») cuando ya hay contenido.
- «Al inicio / Al final» solo existe cuando hay **fuerza y cardio**: `Session.hasStrengthContent()` gobierna tanto el control de colocación de la lista (`SessionListItem.CardioPlacement`) como el chip de la tarjeta de cardio desplegada.
- Sesión solo de cardio (A2b): sin divisor de fuerza, sin colocación, la opción de Fuerza queda debajo, pendiente.
- Estado nuevo `cardioSpaceCommitted` en el ViewModel (se compromete el espacio de cardio vacío sin crear ejercicio; se reinicia al cambiar de sesión).
- Archivos: `components/SessionEditorEmptyState.kt`, `SessionListItems.kt`, `SessionEditorModels.kt`, `SessionEditorViewModelCardio.kt` (`commitCardioSpace`, `setCardioPlacement`), `SessionEditorViewModelNavigation.kt`.

### A3 · Arrastrar
- Se mueve libre y de forma estable **entre grupos, fuera de un grupo, dentro de un grupo y a una superserie ya formada** (máx. 4 miembros). Cada destino lleva su señal: módulo de destino con filete, línea de inserción, zona «Sin grupo» (recuadro de trazos opaco sobre el borde del primer grupo cuando no hay sueltos), marco de la superserie en `mente` (2 dp) y un aviso con el efecto del soltado («Al soltar pasará de «X» a «Y».», «…saldrá de «X» y quedará sin grupo.», «…entrará como A3 en la superserie…», «La superserie ya tiene 4 ejercicios.», «Zona no válida»).
- `planExerciseDrop(...)` (nuevo, puro) calcula ese destino (REORDER, BETWEEN_GROUPS, OUT_OF_GROUP, INTO_GROUP, INTO_SUPERSET, OUT_OF_SUPERSET, INVALID, NONE) y lo comparten la señal y el soltado real; el motor de movimiento (`SessionEditorMoveEngine`) no se ha tocado.
- La cabecera de una superserie mueve el bloque entero; un ejercicio miembro mueve solo a ese miembro. Los huecos de desplazamiento de la lista quedan fijos (cero) mientras se arrastra: la lista no salta.
- AUD2-03 conservado: si el destino desaparece, no se pierde el ejercicio (cubierto por las pruebas existentes y por `SessionEditorDragDestinationsTest`).
- Archivos: `SessionEditorDropPlan.kt` (nuevo), `design/SessionEditorDropSignals.kt` (nuevo), `SessionEditorDragController.kt`, `design/SessionModuleSlot.kt`, `design/SessionModuleSurface.kt`, y el paso de parámetros en `SessionEditorScrollRenderer.kt`/`SessionEditorScreen.kt`.

### A4 · Borrar con deshacer
- El aviso «Eliminado · Deshacer» conserva la ventana de 5 s de AUD2-01/02 y ahora lleva su cuenta atrás (`session_undo_countdown`) y se reinicia con cada borrado. Archivo: `design/SessionExerciseUndoNotice.kt`.

### A5 · Semana y variantes
- La hoja de semana ya reunía lo que pide el tablero (días L M X J V S D, sesiones del día con «principal», variantes A/B/C/D —activa, crear, borrar fuera de la original—, semanas del programa y «crear sesión» en un día vacío); se conserva entera. El único cambio de código es que el rótulo «Semana 3 · Acumulación» ahora es el de la semana **abierta** (antes salía el de la primera de la lista). El resto de A5 es la cabecera «Día · Semana N» de A1 que abre esta hoja, y su cobertura de pruebas, que no existía. Archivo: `frame/SessionEditorFrameWeekSheet.kt`.

### A6 · Estados
- Cargando y error de carga con una sola vista cada uno (`frame/SessionEditorStates.kt`: `SessionEditorLoadingState`, `SessionEditorLoadErrorState`) y **guardado bloqueado con un solo aviso**: si el aviso fijo de «guardado bloqueado» ya dice lo mismo, el snackbar redundante se suprime (`isRedundantWhileSaveBlocked`).

### C1 · Superserie dentro de su grupo
- La superserie va dentro de su grupo, en un marco interior (fondo `#0E0E0E`, filete en `mente`, 8 dp de margen y esquinas de 20 dp) con cabecera: asa, título «SUPERSERIE · N ejercicios», «⋯» y debajo, en su propia fila, las tres fichas (`N rondas`, `1:00 entre ejercicios`, `2:00 tras la ronda`). Los miembros llevan insignia A1, A2, A3 unidos por el raíl.
- Archivos: `components/SupersetGroupEditorCard.kt`, `components/ExerciseEditorCard.kt` (`memberBadge`), `SessionEditorScrollRenderer.kt`.

## Funciones migradas (refinar, no quitar)

| Antes | Ahora |
|---|---|
| «Reglas» abría la hoja con la pestaña de **Tiempo** | El anillo de tiempo abre **Tiempo** y «Reglas» abre **Reglas** (`openRulesSheet(initialTab = 1/0)`) |
| «Crear espacio de cardio» (acción de texto en el pie) | Opción pendiente grande (sesión vacía) o píldora «Crear espacio de cardio» (con ejercicios) |
| «Añadir ejercicio a *X*» en el pie del grupo | Botón de trazos «Añadir a *X*» (misma acción) |
| Superserie suelta con su propio panel | Marco interior de su grupo; «Rondas y descansos», miembros, opcional, disolver y eliminar siguen en el «⋯» y en el panel |
| Chip «Fuerza · RIR 2» en la cabecera | Retirado por decisión del usuario; cada ejercicio y serie llevan su RIR/RPE |
| «Al inicio / Al final» siempre visible en el cardio | Solo si hay fuerza y cardio |
| Cabecera «Día» | «Día · Semana N» (la semana solo si el programa las distingue) |

## Pruebas

Comando (siempre por la cola, sin `--stop` ni matar java):

```
KPKN_RENDER_DIR=C:/kw/shots/eda/despues bash /c/kw/tools/gradle_slot.sh /c/kw/u14/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:testBaseDebugUnitTest
```

Para no gastar turnos de la cola, el filtro se dio por un bloque **temporal** en `app/build.gradle.kts` (más `eda-tests.txt`) con `com.example.kpkn.screens.sessioneditor.*` y los tres `*RenderSheetTest` del editor; la misma corrida compiló también `:app:compileBaseDebugAndroidTestKotlin`. Ese bloque no está commiteado.

**Resultado de la corrida final (`/c/kw/logs/eda-4.log`, 15 min 44 s en ejecución tras ~1 h de cola):** 639 pruebas ejecutadas, **637 en verde y 2 fallidas, las dos ajenas a ED-A** (`ExerciseCatalogImageCoverageTest`, ver «Pendiente»). Todas las pruebas de AUD2 del editor, las 62 nuevas y las adaptadas pasan. `:app:compileBaseDebugAndroidTestKotlin` compila. Los renders se regeneraron en esa misma corrida.

Corridas anteriores (mismo filtro): eda-1/eda-2 con errores de compilación/filtro ya corregidos; eda-3 con 639 pruebas y 4 fallos (los 2 de entorno y 2 de `SessionEditorWeekSheetComposeTest` por un diálogo fuera de pantalla en el arnés, corregido envolviendo el contenido en un `Box(fillMaxSize)`).


### Pruebas nuevas (62)
- `SessionEditorEmptySpacesTest` (12): sesión vacía con solo Fuerza y Cardio; elegir Fuerza y elegir Cardio (el otro queda pendiente); la colocación solo existe con los dos espacios y sin ejercicios, y decide cuál va primero; solo cardio (sin colocación ni espacio de fuerza); la colocación del cardio desplegado solo con fuerza; la superserie de un grupo va dentro del módulo de ese grupo y la suelta es módulo propio; en el ViewModel, elegir cardio no abre el selector ni crea nada, el primer cardio crea la parte conservando la colocación y la colocación cambia sin abrir el selector.
- `SessionEditorEmptyStateComposeTest` (4): solo Fuerza y Cardio, cada opción mide más de 44 dp, la opción pendiente es la misma pieza que la de la sesión vacía, la colocación ofrece Al inicio y Al final.
- `SessionEditorDragDestinationsTest` (14): arrastre a los cuatro destinos (entre grupos, fuera de un grupo con y sin sueltos, dentro de un grupo, a una superserie: tercer miembro, entre miembros, en la raya), soltar donde estaba, superserie llena (aviso y no se pierde nada), sacar un miembro de su superserie, destino que desaparece antes de soltar (AUD2-03), fuera de las zonas válidas y la superserie entera como bloque.
- `SessionEditorDropSignalsComposeTest` (4): el aviso dice cada destino, la zona «Sin grupo» solo se ve cuando es el destino, la señal y el borde del destino no mueven la lista y el botón «Añadir a *Grupo*».
- `SessionEditorUndoNoticeComposeTest` (3): el aviso de borrar con «Deshacer» y su cuenta atrás de 5 s, con movimiento reducido la cuenta queda quieta y ocupa el sitio de la fila de 64 dp. La lógica de borrar/deshacer/confirmar en el ViewModel (ventana de 5 s, AUD2-01/02) sigue cubierta por `SessionEditorUndoViewModelTest`, `SessionEditorAuditRegressionTest` y `SessionEditorNavigationRaceAuditTest`, que no se han tocado.
- `SessionEditorFrameA1ComposeTest` (7): **la cabecera no lleva chip de RIR global**, con protocolo solo dice el protocolo, la píldora dice día y semana, la fila superior mide más de 44 dp, el resumen no cuenta series en una sesión solo de cardio, la isla inferior (anillo, Añadir, Asistente, Reglas) y el anillo dice minutos y límite.
- `SessionEditorWeekSheetComposeTest` (8): días, sesiones del día, variantes y semanas; tocar un día con sesión o vacío; cambiar la principal; elegir/crear/borrar variantes solo fuera de la original; eliminar con variante activa; abrir otra semana; con las cuatro variantes ya no se ofrece crear otra; letra al 130 %.
- `SessionEditorStatesComposeTest` (6): cargando (una sola franja, sin barra de progreso), error de carga (dos salidas y motivo concreto), guardado bloqueado (un solo aviso con su acceso, sin acceso si no hay ejercicio) y la snackbar no repite lo que ya dice el aviso fijo.
- `SessionEditorSupersetFrameComposeTest` (3): superserie enmarcada en su grupo con A1 y A2, el marco queda 24 dp dentro del margen del módulo y el destino del arrastre cambia solo el borde.
- `SessionEditorEdaRenderSheetTest` (1): hoja de renders de ED-A.

### Pruebas existentes adaptadas (y por qué)
- `SessionListItemsTest`: el caso «cardio al inicio» asumía divisor de fuerza en una sesión solo de cardio. Ahora (A2b) comprueba que, sea cual sea la preferencia, una sesión solo de cardio no tiene divisor de fuerza ni colocación, el cardio va arriba y «Añadir fuerza» queda debajo. No se rebaja nada: la colocación con ambas sigue cubierta en las pruebas de arriba.
- `SessionListBehaviorTest`: `onNodeWithText("Superserie", substring = true)` pasa a `"SUPERSERIE"` + `"3 rondas"` (el rótulo ahora va en mayúsculas en `mente` y lleva sus fichas); la aserción es más estricta, no menos.
- `SessionEditorFrameComposeTest`: «54 min» ya no es texto suelto sino el anillo (descripción «Tiempo estimado 54 de 60 minutos»); se comprueba el anillo y que toca `onTime`, distinto de `onRules`. Se añade el parámetro `onTime`.
- `SessionEditorFrameRenderSheetTest`: firma nueva (`summaryChips`, `onTime`); sin cambio de aserciones.

## Renders (360 × 880, densidad 3; el arnés `KPKN_RENDER_DIR`)

Carpeta: `C:\kw\shots\eda\despues\` (cada uno también con sufijo `_s130`, letra al 130 %).

- Main: `eda_c1_superserie.png`, `anadir_cardio_100.png`, `cardio_air_bike_100.png` (+ los de la hoja existente del marco: `crear_superserie_100.png`, `configurar_miembro_de_superserie_100.png`)
- A2: `eda_a2_vacia.png`, `eda_a2_fuerza.png`, `eda_a2_cardio.png`, `eda_a2_ambas.png`
- A2b: `eda_a2b_solo_cardio.png`
- A3 (destinos): `eda_a3_entre_grupos.png`, `eda_a3_fuera_de_grupo.png`, `eda_a3_fuera_sin_sueltos.png`, `eda_a3_dentro_de_grupo.png`, `eda_a3_a_superserie.png`, `eda_a3_superserie_llena.png`, `arrastre_y_zona_no_valida_100.png`
- A4: `eda_a4_borrar_deshacer.png`, `aviso_deshacer_100.png`
- A5: `eda_a5_semana.png`
- A6: `eda_a6_cargando.png`, `eda_a6_error.png`, `eda_a6_bloqueado.png`
- C1: `eda_c1_superserie.png`

## Riesgos de integración

- **Firmas cambiadas**: `SessionEditorFrameBottomBar` (nuevo `onTime`), `SessionEditorFrameHeader` (`summaryChips` en lugar de `summary`), `buildSessionListItems` (nuevo `cardioSpaceCommitted`; nuevo ítem `SessionListItem.CardioPlacement`), `SupersetGroupEditorCard` (`isDropTarget`), `ExerciseEditorCard` (`memberBadge`), `SessionEditorScrollRenderer` (`exerciseDropTargetGroupId`, `draggingExercisePartId`, `dropPlan`). Quien llame a esos composables desde otra rama debe adaptarse.
- **Solapes con otras ramas** en `SessionEditorScreen.kt`, `SessionEditorScrollRenderer.kt`, `SessionEditorDragController.kt` y `SessionEditorViewModelCardio.kt` (u11/u12/u13 pueden tocarlos): al fusionar, resolver los conflictos conservando las llamadas de A1/A2/A3 de este informe. Los commits por tablero cortan los archivos compartidos por trozos, así que cada commit es revisable por separado; solo se ha compilado y probado el estado final de la rama (no cada commit intermedio).
- `Session.hasStrengthContent()` es nuevo y `internal`: cualquier otro lugar que decida «¿ofrecer Al inicio/Al final?» debería usarlo.
- La señal de arrastre usa `statusBarsPadding()`; en dispositivos con barra de estado muy alta el aviso baja con ella (no se ha podido ver en teléfono).

## Pendiente / limitaciones

- **Sin QA en teléfono** (no se ha usado `adb`): el arrastre real con el dedo, la sensación del deshacer y el vidrio solo se han validado con Robolectric y renders.
- A2: no hay selección previa de forma de cardio antes de crear el espacio (se mantiene el flujo existente).
- A5: la hoja de semana no lleva todavía las insignias A/B/C de color del tablero; las variantes se distinguen por texto.
- A 130 % de letra la píldora superior de la barra se elide y la isla inferior se aprieta (legible, no desborda; ver `*_s130.png`).
- Dos pruebas de `ExerciseCatalogImageCoverageTest` (`screens/sessioneditor/components/`) fallan por entorno: leen `catalog/exercises/images/*.json` en la raíz del repositorio, que no existe en este worktree; no es parte de ED-A y no se ha tocado.
