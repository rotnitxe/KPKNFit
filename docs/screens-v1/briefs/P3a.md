# P3a · Program Detail: Estructura, parte 1 (herramientas, calendario del programa y sus hojas) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con P0, P1, P2, P4, CU3 e I9 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/PROGRAM_DETAIL_V1.md`, «Pestaña Estructura»: TU especificación;
- el mapa `C:\kw\analysis\maps\programdetail.md` (§1 y la línea de MacrocycleEditor);
- lo que dejaron P1, P2 y P4 en `screens/programdetail/design/`, en especial `ProgramItemMenu.kt` y `ProgramDetailFormat.kt`;
- `ui/design/KpknMonthCalendar.kt` (lo dejó CU3).

La pestaña Estructura la pinta `components/editor/MacrocycleEditorLegacy.kt` (unas 2.940 líneas). Este paquete rediseña su PARTE DE
ARRIBA y las hojas de fechas:
- la barra `MacrocycleToolbar` (línea aprox. 877);
- el aviso «Revisa la estructura temporal» (aprox. 280, un `Surface` con `errorContainer`);
- la hoja «Calendario del programa» (`LegacyKeyDatesManagementSheet`, aprox. 1289, con `NativeDateField` y su diálogo);
- la hoja «Fecha clave» (`LegacyKeyDateDialog`, aprox. 1909, con fechas ISO escritas a mano);
- la hoja de calendarización simple (`LegacySimpleCalendarizationSheet`, aprox. 995).

Los bloques, mesociclos y semanas, `BlockRoadmap` en Estructura y los diálogos de borrado y conversión son de P3b. Las hojas de
ciclos (Loops), ruta avanzada, plantillas e historial son de P3c. La LÓGICA no cambia: `ProgramKeyDateEngine`,
`ProgramCalendarEngine`, `applyAdvancedCalendarSave`, el borrado de la competición con sesiones vinculadas y los callbacks del VM se
llaman igual que hoy. Cambia la presentación.

## Qué haces
1. **Módulo «Herramientas»** (`screens/programdetail/design/structure/ProgramToolsModule.kt`, sustituye a `MacrocycleToolbar`):
   - `KpknModule` con el eyebrow «HERRAMIENTAS» y, en la cabecera, a la derecha, el tipo del programa en `caption` `textMuted`:
     «Por bloques», «Cíclico» o «Calendarizado».
   - Debajo del eyebrow, una línea de cifras tabulares en `caption`: «4 bloques · 11 semanas · 44 sesiones» (de `ProgramStats`).
   - **Rejilla de baldosas**, 3 columnas con 8 dp de separación (2 columnas si hay exactamente 4 baldosas):
     - cada baldosa: superficie `KpknSurface.inset`, radio 16 dp, alto 76 dp, icono KPKN Line de 24 dp en `text` arriba a la
       izquierda y la etiqueta en `bodyStrong` abajo (una línea; al 130 % puede bajar a `caption` con `maxLines = 1` y elipsis);
     - una cifra opcional en `caption` `textMuted` a la derecha de la etiqueta («2», «3»);
     - tocable entera, `Role.Button`, 48 dp mínimo, con su `contentDescription` («Historial, 1 copia»).
   - **Qué baldosas** (solo las que aplican, en este orden):
     - programa por bloques: «Calendario» (`Calendar`), «Fechas clave» (`Flag`, con el número de fechas clave que no son la
       competición), «Ruta» (icono nuevo `Route`), «Plantillas» (icono nuevo `Layers`) e «Historial» (icono nuevo `History`);
     - programa simple cíclico: «Calendarizar» (`Calendar`), «Ciclos» (`Repeat`), «Plantillas» e «Historial»;
     - programa simple calendarizado: «Calendario» (`Calendar`), «Plantillas» e «Historial».
   - **Acciones:** las de hoy. «Calendario» y «Fechas clave» abren la hoja del punto 3 (la segunda con la lista de fechas clave a la
     vista); «Calendarizar» o «Calendario» de un simple abren la hoja del punto 5; «Ruta» llama a `onToggleRoadmap` (P3c la
     convierte en hoja); «Ciclos», «Plantillas» e «Historial» abren lo de hoy (P3c les cambia la piel).
   - **«Historial»** conserva el tag `macrocycle_history_button`. Sin copias, la baldosa va apagada (texto `textFaint`, sin clic).
     Con copias, la cifra es el número.
   - `MacrocycleToolbarUiTest` (androidTest) prueba la barra vieja: pásala a `ProgramToolsModule` con el MISMO contrato (a 320 dp el
     historial se ve, está habilitado, queda dentro del módulo, es más ancho que alto y el clic abre). Cambia «Historial (1)» por la
     búsqueda del texto nuevo. Solo se compila (`compileBaseDebugAndroidTestKotlin`).
2. **Módulo «Calendario del programa»** (`design/structure/ProgramCalendarModule.kt`), debajo de Herramientas:
   - Cabecera con el eyebrow «CALENDARIO» y `KpknTextAction("Editar")`, que abre la hoja del punto 3 (o la del 5 en un simple).
   - **Programa por bloques:** tres filas de 48 dp con la etiqueta en `caption` `textMuted` a la izquierda y el valor a la derecha en
     `bodyStrong` tabular:
     - «Inicio»: «lun 6 oct 2026», o «Sin fecha» en `textFaint`;
     - «Fin»: la fecha manual, o la proyectada con «≈» delante; «—» si no hay inicio;
     - «Competición»: la fecha y, debajo en `caption` con `SymbolPalette.energia`, la cuenta atrás («Faltan 41 días», «Mañana»,
       «Hoy», «Fue hace 3 días»). Sin competición, la fila no se pinta.
   - **Días de entreno:** una fila de 7 letras (L M X J V S D, semana desde lunes) en círculos de 28 dp: los días con sesión en la
     semana actual del programa en tinta con texto negro y el resto con anillo `divider`. Función pura nueva
     `programTrainingWeekdays(program, weekId)` en `ProgramDetailFormat.kt` (al final), con prueba unitaria.
   - **Simple cíclico:** «Semana que se repite · ciclo 5» en `body`, la fila de días y `KpknTextAction("Calendarizar")`.
   - **Simple calendarizado:** inicio, fin, «N semanas» y la fila de días.
   - Los mensajes de `buildCalendarPreview` que hoy se pierden (competición fuera del programa, fin manual antes del proyectado)
     se muestran debajo como una línea en `caption` con `KpknLineIcons.Info` en `energia`.
3. **Hoja «Calendario del programa»** (`design/structure/ProgramCalendarSheet.kt`, sustituye a `LegacyKeyDatesManagementSheet`):
   - `KpknSheet` (vidrio v2). Título «Calendario del programa» en `title` y una línea de ayuda en `caption`: «La competición marca su
     semana entera en el programa.»
   - **Filas de fecha** (`design/structure/ProgramDateRow.kt`, pieza reutilizable): etiqueta, valor formateado y `ChevronDown`.
     Tocar la fila DESPLIEGA debajo un `KpknMonthCalendar` (`AnimatedVisibility`, 220 ms; sin animación con movimiento reducido);
     solo una abierta a la vez. Elegir un día lo fija y pliega. `KpknTextAction("Quitar")` vacía la fecha cuando la hay.
     Nada de `OutlinedTextField`, `TextButton` ni fechas ISO a la vista.
     - «Inicio del programa» (sin límites);
     - «Día de la competición» (desde hoy);
     - «Fin del programa» (opcional; vacía = «≈ <proyectado>» como valor gris).
   - **Vista previa** (sustituye a `CalendarPreviewCard`): los bloques con su rango de fechas en filas de `caption` tabular («Bloque
     1 · 6 oct – 2 nov») y los mensajes del motor con `Info`.
   - **Otras fechas clave:** lista en filas de 56 dp: título en `bodyStrong`, rango en `caption` y el tipo como `KpknChip` pequeño;
     tocar edita (punto 4); `KpknIconButton(Trash, "Eliminar")` a la derecha (icono nuevo `Trash`). Debajo,
     `KpknTextAction("Añadir fecha clave")` (sin «exámenes, vacaciones, viaje…» en el botón: va como ayuda en `caption`).
   - **Guardar:** `KpknPrimaryPill("Guardar calendario")` a todo el ancho, con la misma condición de hoy. Si falta el inicio con
     competición, el texto de hoy en `caption` `danger`: «Para fijar una competición, elige primero el inicio del programa.»
4. **Hoja «Fecha clave»** (`design/structure/ProgramKeyDateSheet.kt`, sustituye a `LegacyKeyDateDialog`):
   - Título «Nueva fecha clave» o «Editar fecha clave».
   - `KpknTextField` «Nombre» (una línea) y `KpknTextField` «Notas» (opcional, 2-4 líneas).
   - Tipo con `KpknSegmentedControl(Compact)` o una fila de `KpknChip`: «Exámenes», «Vacaciones», «Viaje» y «Otra» (sin
     `entry.name` en inglés). La competición no se elige aquí: si la fecha es de competición, el texto de hoy, en español
     neutro y sin comillas rectas.
   - Fechas con `ProgramDateRow`: «Empieza» y «Termina (opcional)», con la regla «termina ≥ empieza».
   - Abajo: `KpknTextAction("Eliminar")` en `danger` a la izquierda (solo al editar) y `KpknPrimaryPill("Guardar")` a la derecha.
     Sin «Cancelar»: la hoja se cierra con el asa o atrás.
5. **Hoja de calendarización simple** (`design/structure/ProgramSimpleCalendarSheet.kt`, sustituye la PIEL de
   `LegacySimpleCalendarizationSheet`; sus callbacks y sus dos estados no cambian):
   - Sin calendarizar: título «Calendarizar el ciclo», el texto de hoy acortado (≤ 160 caracteres, sin «break»: «pausa»), las
     fechas con `ProgramDateRow`, el día de inicio de la semana como `KpknSegmentedControl(Compact)` de 7 letras, los días de
     entreno como 7 círculos tocables de 44 dp (como la fila del punto 2) y «N semanas» en `caption`. Las dos acciones de hoy como
     `KpknPrimaryPill` y `KpknSecondaryPill`.
   - Calendarizado: título «Ciclo calendarizado», el texto de hoy acortado y «Recuperar el ciclo guardado» (principal, apagado si
     no hay copia) y «Empezar un ciclo nuevo» (secundaria). Fuera los `Card`, `Button`, `OutlinedButton` y `Color.White` sueltos.
6. **Aviso «Revisa la estructura temporal»:** `KpknModule(accent = WizardColors.danger)` con el título en `bodyStrong` y hasta tres
   mensajes en `caption`.
7. **Raíz de Estructura:** el `Column` de `MacrocycleEditorLegacy` pasa a ritmo de módulos (12 dp entre módulos, 16 dp de margen)
   y los módulos nuevos entran con `kpknEntrance("programdetail.estructura", i)`. No toques lo de bloques (P3b).
8. **Iconos nuevos** al FINAL de `KpknLineIcons` (retícula 24, trazo 1,75, extremos redondos), en tu hoja de render:
   - `Route`: tres nodos (círculos r 2,25 en (5; 18), (12; 7) y (19; 15)) unidos por una línea quebrada;
   - `Layers`: dos rombos aplastados apilados (el de arriba completo, el de abajo solo su mitad inferior);
   - `History`: un reloj (círculo r 8 en (12; 12), agujas a las 12 y a las 3) con una flecha en sentido antihorario en su arco
     superior izquierdo;
   - `Trash`: cubo con tapa y dos líneas verticales.
   N3a añade `Search` al final en paralelo: si chocas al integrar, lo resuelvo yo.

## Formato y textos (puros, en `ProgramDetailFormat.kt`, al final)
- Fecha corta: «lun 6 oct 2026» (día de semana y mes en minúscula, sin punto). Rango: «6 oct – 2 nov».
- Cuenta atrás: mueve `formatDaysUntil` del Legacy a una función pura con los textos del punto 2, con prueba.
- Tipos de fecha clave: `keyDateTypeLabel(type)`.
- Usa `Locale("es")` explícito, nunca `Locale.getDefault()`. Las composables reciben `today: LocalDate` (en pantalla,
  `LocalDate.now()` UNA vez arriba), para que la hoja de render sea determinista.

## Pruebas
- **Siguen en verde:** `ProgramDetailViewModelTest`, `ProgramKeyDateEngine*`, `ProgramCalendarEngine*`, `*Competition*KeyDate*` y lo
  que encuentres con Grep sobre `MacrocycleEditor`, `KeyDates`, `Calendarization`.
- **Unitarias:** fecha corta y rango, cuenta atrás (hoy, mañana, 41 días, pasado), `programTrainingWeekdays`, `keyDateTypeLabel`.
- **Robolectric** `ProgramStructureToolsTest`: las baldosas según el tipo de programa; «Historial» apagado sin copias y con la
  cifra con copias; tocar «Calendario» llama a su lambda; en la hoja del calendario, tocar una fila despliega el calendario y elegir
  un día llama a `onTimelineStartDateChange` con la fecha ISO; «Quitar» la vacía.
- **Hoja de render** `render/ProgramStructureTopRenderSheetTest`:
  - herramientas de un programa por bloques, de un simple cíclico y de un calendarizado;
  - calendario con competición a 41 días, sin inicio y con avisos del motor;
  - la hoja del calendario con una fila desplegada y dos fechas clave;
  - la hoja de fecha clave nueva y al editar;
  - la calendarización simple en sus dos estados;
  - el aviso de estructura temporal;
  - los cuatro iconos nuevos a 48 dp;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/p3a`.
  - Mira cada PNG con Read y critica con el listón del usuario (Hevy y MacroFactor Workouts no tienen calendario de programa con
    competición: el nuestro tiene que verse como una herramienta profesional, limpia). Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/p3a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "*ProgramDetail*" --tests "*ProgramStructure*" --tests "*KeyDate*" --tests "*Calendar*" --tests "*KpknLineIcons*" > /c/kw/logs/screens-p3a-<n>.log 2>&1`

## Commit
- Por rutas: `feat(programdetail): Estructura con herramientas, calendario del programa y hojas de fechas con el calendario de mes (P3a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Bloques, mesociclos, semanas, `BlockRoadmap` y los diálogos de borrado y conversión (P3b).
- Las hojas de ciclos, ruta avanzada, plantillas e historial por dentro (P3c).
- La lógica de fechas, calendarización y competición.
- `ProgramDetailScreen.kt` salvo para pasar `today` o un callback nuevo (anótalo), y `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y código que queda sin uso en el Legacy (`NativeDateField`, `WeekStartDatePickerDialog`, `CalendarPreviewCard`…).
- Pruebas: número y resultado.
- PNG y crítica.
