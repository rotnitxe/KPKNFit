# SE3a · Editor de sesiones: módulos de parte, filas de ejercicio con dosis, superseries y arrastre (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con SE0, SE1 y SE2 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §2 (puntos 5, 6 y 8), §7, §8 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.2, §1.4, §1.5 y §6. Las líneas son de ANTES de SE0 y SE2: búscalas por nombre;
- lo que dejó SE2 en `screens/sessioneditor/design/` (cabecera, islas) y SE1 en `ui/design/` (`KpknSetTable`, `KpknNumberCell`).

Este paquete hace la LISTA de la sesión en plegado: cada parte es un módulo y cada ejercicio una fila de una línea con su dosis. El
desplegado de un ejercicio (la tabla de series y sus opciones) es de SE3b: hasta entonces, al desplegar se ve el contenido de hoy
(`ExerciseEditorCard` por dentro), aunque desentone.

## Qué haces
1. **Etiquetas de grupo** sobre el negro, sin caja: «CARDIO» y «FUERZA» en `KpknType.eyebrow` `textMuted`, en el orden que ya sigue
   `buildSessionListItems` (cardio al inicio o al final). Sustituyen a `CardioDivider` y `StrengthDivider`.
2. **Una parte = un módulo** (`screens/sessioneditor/design/SessionPartModule.kt` y piezas, sin estado):
   - **cabecera** (56 dp):
     - un punto de 10 dp con el color de la parte;
     - el nombre en `bodyStrong`;
     - «3 ejercicios · 10 series» en `caption`;
     - `KpknIconButton(More)` con un `KpknContextMenu`: «Renombrar», «Color», «Plegar» (o «Desplegar») y «Eliminar parte» en
       `danger`. Hacen lo mismo que hoy; eliminar pide confirmación con `KpknAlertDialog(destructive = true)`;
     - la pulsación larga en la cabecera arrastra la parte, con el mismo controlador de hoy;
   - **al pie**, `KpknTextAction` con `KpknLineIcons.Plus` DELANTE: «Añadir ejercicio a <parte>»;
   - los ejercicios sueltos (fuera de una parte) van en un módulo sin cabecera de parte.
3. **Una fila por ejercicio, plegada** (`SessionExerciseRow.kt`, 64 dp):
   - a la izquierda, la miniatura: la imagen del catálogo si existe (círculo de 44 dp; busca con Grep cómo la consigue hoy el
     catálogo, `ExerciseTechniqueImageLookup` o similar) o el glifo de su patrón (`ExercisePatterns`);
   - el nombre en `bodyStrong` (una línea con «…»);
   - la dosis en `caption` tabular: «4 × 6–8 · RIR 2 · 2:30». Las variantes: «RPE 8», «75 % 1RM», «4 × 0:45», «AMRAP», «hasta el
     fallo». Las series de aproximación no cuentan en «4 ×». Si las series no son iguales, se resume con el rango («3 × 6–10») o con
     «4 series» si no hay un rango común;
   - `ChevronDown` (que gira 180° al desplegar; sin giro con `wizardReducedMotion()`);
   - tocar la fila despliega y pliega, con el mismo estado de hoy si existe (Grep `collapsed` y `expanded` en el VM). Si no existe,
     un estado de pantalla (`rememberSaveable` por id);
   - la pulsación larga arrastra el ejercicio, con el mismo controlador y la misma geometría;
   - escribe la dosis con una función pura (`SessionExerciseDose.kt`) con su prueba unitaria.
4. **Superseries:** sus ejercicios van seguidos dentro del módulo, unidos por una barra vertical de 3 dp con el color de la parte, a
   la izquierda de las miniaturas. Encima, una línea en `caption`: «Superserie · 3 rondas · 1:30 entre rondas», con
   `KpknTextAction("Editar")`, que abre la gestión de superseries de hoy.
5. **Sesión vacía:** `KpknEmptyState` con «¿Qué quieres entrenar en esta sesión?» y dos `KpknOptionRow` grandes: «Fuerza» (con
   «Ejercicios, partes y superseries» debajo) y «Cardio» («Al inicio o al final de la sesión»). Hacen lo mismo que el
   `SessionEditorEmptyState` de hoy.
6. **Visuales del arrastre** (`SessionEditorVisuals.kt`), sin tocar el controlador ni la geometría:
   - al levantar: escala 1,02, sombra suave y un filete en tinta al 40 %;
   - el indicador de destino: una línea de 2 dp en tinta con los extremos redondos;
   - la zona no válida: un aviso compacto en `danger`. Conserva el tag `session_editor_invalid_drop_banner` y el texto «Zona no
     válida», porque `SessionEditorInvalidDropBannerUiTest` lo busca;
   - con `wizardReducedMotion()`, sin escala ni sombra animadas.
7. Lo que queda sin uso tras el cambio (`GroupEditorCard` en modo cabecera, `CardioDivider`, `StrengthDivider`, el estado vacío
   viejo…): bórralo con Grep en todo `app/src`. `ExerciseEditorCard` NO se borra: SE3b lo sustituye por dentro. Lo que use
   `screens/workout/` se queda.

## Pruebas
- **Siguen en verde:** las 33 clases de `src/test/.../screens/sessioneditor/`, sobre todo:
  - `SessionEditorDragControllerTest` (39);
  - `SessionListItemsTest` (15);
  - `SessionEditorManualOverrideTest` (30).
  - También las de `screens/workout/` que usan piezas del editor (`*Workout*`) y la compilación de `androidTest`.
- **Unitarias** `SessionExerciseDoseTest`:
  - series iguales con RIR y descanso;
  - RPE, %, tiempo y AMRAP;
  - series distintas (rango y «N series»);
  - sin las de aproximación.
- **Robolectric** `SessionPartModuleTest`:
  - el menú «⋯» de la parte llama a renombrar y a eliminar (con su confirmación);
  - tocar una fila llama a desplegar;
  - «Añadir ejercicio a <parte>» llama con la parte;
  - la sesión vacía llama a fuerza y a cardio.
- **Hoja de render** `render/SessionEditorListRenderSheetTest`, con las piezas montadas con datos de ejemplo:
  - una parte con 3 ejercicios (uno con imagen y dos con glifo);
  - una superserie de 2;
  - ejercicios sueltos;
  - las etiquetas «CARDIO» y «FUERZA»;
  - la sesión vacía;
  - el ejercicio levantado y la línea de destino;
  - el aviso de zona no válida;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se3a`.
  - Mira cada PNG con Read y critica con el listón del usuario: la sesión entera tiene que leerse sin scroll largo, mejor que la
    lista de MacroFactor Workouts y que las tablas de Hevy. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se3a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*SessionEditorList*" --tests "*SessionExerciseDose*" --tests "*SessionPartModule*" > /c/kw/logs/screens-se3a-<n>.log 2>&1`

## Commit
- `feat(sessioneditor): partes en módulos, ejercicios en una línea con su dosis, superseries y arrastre KPKN (SE3a)`.
- Si borras piezas viejas, aparte: `refactor(sessioneditor): quita las tarjetas de parte y los separadores viejos (SE3a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- El desplegado de un ejercicio por dentro (SE3b), el impacto y el asistente (SE4), el cardio y la preparación (SE5), el catálogo
  (SE6) y las hojas (SE7, en paralelo).
- `SessionEditorDragController`, `SessionEditorListGeometry` y la lógica del VM. Lo que falte, AL FINAL del VM.
- Las firmas de las piezas que usa `screens/workout/` (`ExerciseSetsCarousel`, `CompactModeSelector`, `UnilateralModeSelector`,
  `SideOrderChip`…).
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Pruebas: número y resultado.
- PNG y crítica.
