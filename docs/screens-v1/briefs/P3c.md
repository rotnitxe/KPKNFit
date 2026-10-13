# P3c · Program Detail: Estructura, parte 3 (hojas de ciclos, ruta, plantillas e historial) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con P0, P1, P2, P3a, P4, CU3, I9 y D1 integrados). Lee
antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/PROGRAM_DETAIL_V1.md`, «Pestaña Estructura» (hojas e idioma): TU especificación;
- el mapa `C:\kw\analysis\maps\programdetail.md`;
- lo que dejó P3a en `screens/programdetail/design/structure/` (la rejilla «Herramientas» abre tus hojas; `ProgramDateRow`).

Las cuatro herramientas que abre la rejilla de P3a siguen con su piel vieja:
- **Ciclos:** `components/LoopsView.kt` (unas 565 líneas), dentro de una `KpknSheet` del Legacy. Usa emojis («⚡», «🧘»…), «Loops»,
  «Templates», «Agregar», voseo («Posponé o cancelá») y colores fijos (`0xFF92400E`). Las plantillas de ciclos con su emoji viven en
  el propio archivo (`LoopTemplate`) y en `domain/training/LoopEngine.kt`.
- **Ruta:** `MacrocycleRoadmapSection` → `LegacyAdvancedRoadmapSection` y sus ayudantes (`KeyDateFitSection`, `KeyDateTrackCard`,
  `RoadmapWeekDot`, `CompetitionWeekCard`, `KeyDateTypeBadge`) en `components/editor/MacrocycleEditorLegacy.kt` (aprox. 1600-1907).
  Hoy es una sección que se despliega dentro de la página.
- **Plantillas:** `LibrarySection` → `LegacyLibrarySection`, el diálogo de confirmar plantilla (con `Checkbox`) y
  `screens/programs/ProtocolDetailSheet.kt` para los protocolos.
- **Historial:** un `KpknAlertDialog` con `TextButton` por copia («Restaurar») en el Legacy.

La LÓGICA no cambia: `LoopEngine`, `ProgramKeyDateEngine` (ajustar semanas de un bloque), `ProgramTemplateEngine`, aplicar protocolo,
restaurar copias.

## Qué haces
1. **Hoja «Ciclos»** (`design/structure/ProgramCyclesSheet.kt` y piezas; `LoopsView` queda como orquestador o se sustituye):
   - título «Ciclos» en `title` y la línea «2 ciclos activos · vas por el ciclo 5» en `caption`;
   - `KpknIconButton(Info, "Para qué sirven los ciclos")` abre la explicación de hoy como `KpknAlertDialog`, con «ciclos» en lugar de
     «loops»;
   - **los ciclos activos:** una fila por ciclo con un icono KPKN Line según su tipo (descarga = `Moon`, test = `Flag`, evento =
     `Calendar`, otro = `Repeat`: función pura `loopTypeIcon`), el nombre en `bodyStrong` y «cada 4 ciclos» en `caption`;
   - **las semanas de ciclo:** filas con la semana, «Semana especial completa» si toca, y las acciones de hoy («Ver semana», «Posponer»,
     «Cancelar») como `KpknTextAction` (cancelar en `danger`);
   - **las plantillas de ciclo:** módulos pequeños con su icono KPKN (fuera los emojis de `LoopTemplate`: el campo puede quedarse por
     compatibilidad, pero no se pinta), nombre y descripción, y «Añadir» como `KpknSecondaryPill`;
   - el aviso de eventos antiguos por migrar: `KpknModule(accent = SymbolPalette.energia)` con «Hay 2 eventos antiguos. Pásalos a
     ciclos para usar el sistema nuevo.» y `KpknTextAction("Pasar a ciclos")`;
   - el aviso de choque: «Pospón o cancela uno para que no coincidan.» (sin voseo).
2. **Hoja «Ruta del programa»** (`design/structure/ProgramRouteSheet.kt`): la herramienta «Ruta» de P3a deja de desplegar una sección
   y abre esta hoja casi a pantalla completa:
   - **la línea de tiempo** (Canvas propio): los bloques como tramos horizontales de su color de fase, con su nombre debajo; las
     semanas como puntos; las fechas clave como banderas (`Flag`) con su tipo; la semana de competición destacada en `danger`; «Hoy»
     como una marca vertical en tinta. Se dibuja de izquierda a derecha la primera vez (600 ms; sin animación con movimiento reducido).
     Si no cabe a 360 dp, se desplaza en horizontal con la semana actual a la vista;
   - **el ajuste a la competición** (lo de `KeyDateFitSection`): si la competición no cae en el pico, una línea clara («Tu competición
     cae 2 semanas después del final del programa») y, por bloque, `KpknIconButton(Minus)`/`KpknIconButton(Plus)` para quitar o añadir
     semanas (la acción de hoy, `onAdjustBlockWeeks`), con «3 semanas» en medio;
   - **el inicio de cada bloque:** filas «Bloque 2 · empieza el lun 3 nov» en `caption` tabular.
3. **Hoja «Plantillas»** (`design/structure/ProgramTemplatesSheet.kt`, sustituye a la piel de `LegacyLibrarySection`):
   - dos grupos con su eyebrow: «PLANTILLAS» y «PROTOCOLOS»; un módulo por opción con el nombre, la descripción (2 líneas con «…») y
     «3 bloques · 12 semanas» en `caption`; tocar abre la confirmación;
   - **confirmar plantilla** (sustituye al diálogo con `Checkbox`): `KpknSheet` con el nombre, la descripción, «Las semanas se rellenan
     con sesiones sugeridas según el reparto.» y, si el programa tiene sesiones, dos `KpknRadioRow`: «Reemplazar el programa (se
     guarda una copia en Historial)» y «Crear una copia en borrador». Principal: «Reemplazar todo» o «Aplicar plantilla», como hoy;
   - los protocolos siguen abriendo `ProtocolDetailSheet` (no lo rediseñes aquí: anota su estado).
4. **Hoja «Historial del programa»** (`design/structure/ProgramHistorySheet.kt`, sustituye al diálogo):
   - una fila por copia (las 10 más recientes, como hoy): la fecha en `bodyStrong` («lun 13 oct · 10:42», con `Locale` español
     explícito) y el motivo en `caption`; `KpknTextAction("Restaurar")` a la derecha;
   - **restaurar** pide confirmación (`KpknAlertDialog`: «¿Restaurar esta copia? El programa actual se guarda antes en el
     historial.» si así lo hace hoy la lógica; si no, sin la segunda frase);
   - vacío: `KpknEmptyState` con «Aún no hay copias» y «Se guarda una cada vez que reemplazas el programa con una plantilla o un
     protocolo.».
5. **Idioma y colores:** español neutro y tipo frase; «Ciclos», «Plantillas», «Pausa» (nunca «Loops», «Templates», «break»); sin
   emojis; fuera `Card`, `OutlinedButton`, `TextButton`, `Checkbox`, `Icons.*` y `Color(0x…)` de estas piezas.

## Pruebas
- **Siguen en verde:** `ProgramDetailViewModelTest`, lo que encuentres con Grep sobre `LoopsView`, `LoopEngine`, `LoopTemplate`,
  `ProgramTemplateEngine`, `ProgramSnapshot` y `MacrocycleRoadmap`, lo que dejó P3a y la compilación de `androidTest`
  (`MacrocycleToolbarUiTest` lo adaptó P3a: no lo rompas).
- **Unitarias:** `loopTypeIcon` y los textos nuevos con número (ciclos activos, semanas que faltan o sobran para la competición).
- **Robolectric** `ProgramStructureSheetsTest`: «Restaurar» pide confirmación y llama con el id; en Plantillas, elegir «Crear una
  copia» aplica sin reemplazar; en la ruta, «+» de un bloque llama a ajustar con +1.
- **Hoja de render** `render/ProgramStructureSheetsRenderSheetTest`:
  - Ciclos con dos ciclos activos, una semana especial y el aviso de eventos antiguos;
  - la ruta con 4 bloques, dos fechas clave y la competición desajustada;
  - Plantillas y su confirmación con sesiones;
  - Historial con copias y vacío;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/p3c`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/p3c bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "*ProgramDetail*" --tests "*ProgramStructure*" --tests "*Loop*" --tests "*ProgramTemplate*" --tests "*Snapshot*" > /c/kw/logs/screens-p3c-<n>.log 2>&1`

## Commit
- Por rutas: `feat(programdetail): hojas de ciclos, ruta del programa, plantillas e historial en piezas KPKN (P3c)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Bloques, mesociclos, semanas y sus hojas (P3b, en paralelo) y lo de P3a.
- La lógica de ciclos, fechas clave, plantillas, protocolos y copias.
- `ProtocolDetailSheet.kt` por dentro, `ProgramDetailScreen.kt` y `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y código que queda sin uso en el Legacy y en `LoopsView.kt`.
- Pruebas: número y resultado.
- PNG y crítica.
