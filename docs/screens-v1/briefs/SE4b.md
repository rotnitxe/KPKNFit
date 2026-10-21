# SE4b · Editor de sesiones: las plantillas del asistente en piezas KPKN (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con SE4 y SE7 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §5 («Plantillas»), §8 y §9;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §2.1 («Plantillas (tarjetas y navegador)»), §6 y §7. Las líneas son de ANTES:
  búscalas por nombre;
- lo que dejó SE4 (`design/SessionAssistantSheet.kt` y `design/SessionAssistantTemplatesTab.kt`) y lo que dejó SE7 en
  `components/TemplateCatalogBrowser.kt` (los diálogos ya en `KpknAlertDialog`).

SE4 rehízo el asistente, pero su pestaña «Plantillas» sigue pintando `TemplateCatalogBrowser` (unas 1.000 líneas) con piel de
Material: un botón lila «Guardar sesión», desplegables de filtro, una rejilla de grupos y tarjetas viejas. Es la ÚNICA vía para
guardar, aplicar, editar, archivar, restaurar y borrar plantillas, así que no se puede perder nada. La lógica NO cambia: mismos datos,
mismas llamadas y mismos filtros.

## Qué haces
1. **La pestaña «Plantillas»** (`TemplateCatalogBrowser` y `TemplateCatalogCards`), revestida por dentro con piezas KPKN:
   - arriba, el buscador `KpknTextField` con `Search` y la placeholder «Buscar plantilla…» (hoy dice «...» en ASCII);
   - **«Mis plantillas»:** el eyebrow y, a la derecha, `KpknTextAction("Guardar esta sesión", leadingIcon = Plus)` en lugar del
     botón lila. Cada plantilla propia en una fila de módulo: nombre en `bodyStrong`, «6 ejercicios · 22 series · ≈ 55 min» en
     `caption`, la fatiga en mini barras con los colores de `KpknRingColors` (sustituye a `CompactFatigueMeters` y
     `CompactFatigueChip`), `KpknTextAction("Aplicar")` y un `KpknIconButton(More)` con `KpknContextMenu`: «Editar datos»,
     «Archivar» o «Restaurar» y «Eliminar» en `danger` (con su `KpknAlertDialog(destructive = true)`);
   - sin plantillas propias: una línea en `caption`, «Todavía no tienes plantillas guardadas.», sin caja;
   - **los filtros** («Agrupar», «Nivel», «Duración» y los grupos «Piernas · Torso · Brazos · Cuerpo completo»): una fila de
     `KpknChip` desplazable. Un filtro activo se ve seleccionado con su valor. Abre el mismo selector de hoy, en un
     `KpknContextMenu` o una `KpknSheet` pequeña. Fuera `FilterDropdownRow`, `CatalogDropdownButton` y `SessionTypeChipsRow` si
     quedan sin uso;
   - **el catálogo** (las plantillas de KPKN) en módulos por grupo con la misma fila, sin el menú de gestión;
   - desplegar una plantilla (`TemplateExpandedDetails`) muestra sus ejercicios en `caption` dentro del módulo;
   - vacío del catálogo: `KpknEmptyState` con el texto de hoy, en lenguaje llano;
   - «Full body» se escribe «Cuerpo completo» en lo que se ve (los ids no cambian).
2. **Diálogos de metadatos y de guardar** (`UserTemplateMetadataDialog`, `SaveUserTemplateDialog`, `TemplateMetadataDropdown`): en
   `KpknSheet` con `KpknTextField` para el nombre, `KpknChip` para grupo, nivel y duración, y `KpknPrimaryPill("Guardar")`. El pie
   «6 ejercicios · 22 series» si quien lo abre puede pasar la sesión (añade un parámetro opcional al final; si no puede, sin pie).
3. **Índice del desplazamiento automático** (`SessionEditorScreen.kt`): `EditorLeadingItems` vale 3 (fila superior, franja y
   cabecera), pero SE4 añadió el módulo de impacto delante de la lista cuando hay datos. Pasa a contarlo
   (`EditorLeadingItems + if (impact != null) 1 else 0`), con una prueba de `lazyColumnIndexForExercise` si no la hay. Solo esa
   línea de la pantalla.
4. **Lo que queda sin uso** tras el cambio: bórralo con Grep en todo `app/src`.
5. **Textos:** tipo frase, «…» de un carácter, sin MAYÚSCULAS escritas a mano, sin anglicismos en lo que se ve («Cuerpo completo»).
   Antes de cambiar un texto, Grep en `src/androidTest`.

## Pruebas
- **Siguen en verde:**
  - las clases de `src/test/.../screens/sessioneditor/`, sobre todo las de plantillas y `SessionAssistantContentTest`;
  - `*Workout*`;
  - la compilación de `androidTest`.
- **Robolectric** `SessionTemplatesContentTest`, con el cuerpo montado suelto:
  - «Aplicar» llama con el id y pide confirmación como hoy;
  - el menú «⋯» llama a archivar y a eliminar (con su confirmación);
  - un filtro activo filtra la lista;
  - «Guardar esta sesión» abre el guardado.
- **Hoja de render** `render/SessionTemplatesRenderSheetTest`:
  - la pestaña con 2 plantillas propias y el catálogo;
  - sin plantillas propias;
  - un filtro activo;
  - una plantilla desplegada;
  - el menú «⋯» abierto;
  - guardar plantilla;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se4b`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se4b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*SessionTemplates*" --tests "*SessionAssistant*" --tests "*SessionListItems*" > /c/kw/logs/screens-se4b-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(sessioneditor): plantillas del asistente en piezas KPKN, con gestión, filtros y guardado (SE4b)`.
- `fix(sessioneditor): el desplazamiento automático cuenta el módulo de impacto (SE4b)`.
- Si borras piezas viejas, aparte: `refactor(sessioneditor): quita las piezas viejas del catálogo de plantillas (SE4b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica de las plantillas (aplicar, guardar, archivar, restaurar, borrar, filtros) y del VM. Lo que falte, AL FINAL del VM.
- Las otras pestañas del asistente y el módulo de impacto (SE4), la lista (SE3a), el desplegado (SE3b), el cardio (SE5), el catálogo
  de ejercicios (SE6) y las hojas de SE7.
- `SessionEditorScreen.kt` fuera de la línea del punto 3.
- `MainActivity.kt` y `screens/workout/`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Pruebas: número y resultado.
- PNG y crítica.
