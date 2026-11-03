# Reparto (rp) · punto 23 · mapa del paquete

Pestaña `reparto`, 27 escenas, `src/scenes-rp.js` + `src/rp.css`. Contrato del motor: `maps/reparto-motor.md`.
Todo en `android-native/app/src/main/java/com/example/kpkn/`. Datos de ejemplo sintéticos (programa «Motor y fuerza», semana 5).

## 1. Qué hay hoy (leído del código)
- Entrada: ⋯ de la semana → «Cambiar reparto · Qué entrena cada día» (`screens/programdetail/design/ProgramWeekMenus.kt`, solo en la semana abierta) → `SplitView.kt` (estado) → `SplitCatalogScreen.kt`.
- Catálogo: 35 repartos publicados (`data/splits/SplitTemplates.kt`: 23 generales + 12 de powerlifting; `KPKN_NATIVE_SPLIT_IDS`) + el propio. Cada tarjeta (`SplitCatalogModule.kt`): radio, nombre, «N días», barras de color. **Sin descripción**; el texto, las ventajas y las precauciones solo salen tras tocar (`SplitApplySheet.kt`).
- Hoja «Aplicar reparto»: diagrama (`SplitWeekDiagram`, `SplitRoleLegend`), inicio de semana, alcance (Semana/Varias/Bloque/Programa), programa por bloques, contenido (`SessionMigrationMode`: MIGRATE «Conservar y reubicar», CLEAN «Sesiones vacías», PREBUILT «Con ejercicios de plantilla»), impacto + aceptación, confirmación si > 1 semana o > 4 sesiones.
- **«Conservar y reubicar» hoy** = `SplitApplicationEngine.buildSessionsForSplit`: cada sesión entera va al día que más se parece (`bestTrainingDayForSession`). No reparte ejercicios; con más sesiones que días las amontona, con menos deja días en blanco; no mira sesiones hechas, ni deload, ni volumen; sin vista previa ni deshacer.
- **Ya existe un motor bueno, pero solo lo usa el asistente**: `domain/training/split/SplitRedistributor` (+ `DayAssigner`, `WeekUnits`, `WeekBuilder`, `DayTimeFit`, `SplitDayFocus`, `SplitCatalogRules`, `WeekAssignment`) con su tablero `screens/onboarding/design/entreno/layout/WeekLayoutBoard.kt` + `SplitPicker.kt`. Mueve ejercicios enteros, conserva el volumen, mantiene superseries y devuelve informe (`RedistributionReport`: movimientos, volumen antes/después, recortes).

## 2. Escena → pieza de la app
| Escena | Pieza que sustituye o mantiene |
|---|---|
| rp-hoy, rp-hoy-hoja | `SplitCatalogScreen`/`SplitCatalogModule` y `SplitApplySheet` tal como son (anotadas) |
| rp-catalogo, rp-buscar | `SplitCatalogScreen`, `SplitCatalogModule`, `SplitCatalogFilter` (`filterSplitCatalog`, `splitTagLabel`) |
| rp-seleccion, rp-comparar | `SplitSelectionBar`, `SplitCompareSheet` (+ tabla de frecuencias) |
| rp-varios | `SplitMultiApplySheet` |
| rp-propio-vacio, rp-propio | `SplitCustomCatalogModule`, `SplitCustomEditorSheet` |
| rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia | **Nueva** `SplitPreviewScreen` (reutiliza `WeekLayoutBoard`/`WeekLayoutDrag`/`SplitPicker` del asistente); sustituye a la hoja como destino de tocar una tarjeta |
| rp-revision, rp-revision-musculo, rp-decisiones | **Nueva** `SplitReviewScreen` (+ hoja de decisiones); datos de `RedistributionReport` |
| rp-opciones, -varias, -bloques, rp-sustituir, rp-confirmar | `SplitApplySheet` reducida (`SplitStartDayControl`, `SplitWeekCircles`, `SplitBlockRow`, `SplitModeRows`, `SplitImpactModule`, `SplitConfirmDialog`) |
| rp-aplicado, rp-deshecho | **Nuevos**: hoja de resumen + deshacer (hoy `onUpdateProgram` solo cierra la página) |
| rp-rechazo, rp-autor, rp-calculando | `RedistributionResult.reason`, `AuthoredPlans.hasFixedRecipe`, estado de carga (`SplitErrorDialog` se mantiene) |

## 3. Propuestas y decisiones (cada una con su razón)
- `new` **Descripción en cada tarjeta**: una frase por reparto (35, redactadas con solo el patrón, ventajas y precauciones; tabla en `scenes-rp.js`, `RP_SPLITS.d`). Hoy los textos son frases sueltas («Simple y brutal.»). Hay que aprobarlas.
- `new` **Veces por semana por músculo** calculada del foco de cada día (`DayFocusParser`, mismo mapa que el motor); mediana de 9 grupos; no se muestra en powerlifting (sus días se definen por levantamiento).
- `new` **Nivel** (campo `Difficulty`, hoy oculto). `new` **Filtro por días** y agrupación «Encajan con tus N días / Cambian tus días». `new` «Ahora» arriba (el radio de «actual» no sirve: tu reparto es Personalizado).
- `move` **Nombres llanos** (`SplitCatalogRules.displayName`) también en el catálogo; hoy salen «Upper / Lower x4».
- `move` **Colores unificados** (empuje amarillo, tirón azul, pierna coral, torso lila, cuerpo completo crema): hoy las barras del catálogo y el diagrama de la hoja los intercambian. Los días «raros» (Pecho, Hombro/Brazo, Glúteo…) toman el color de su grupo en vez de crema. Iniciales de día **L M Mi J V S D** (como el asistente), no «X».
- `new` **Vista previa interactiva** como destino de tocar una tarjeta (no una hoja): semana de 7 días con las sesiones calculadas con tus ejercicios, Antes/Después, arrastrar y «Mover a», avisos, volumen/frecuencia antes→después, selector «Repartos para tu semana». Los días del reparto se proponen solos si el número no cuadra (`ask`).
- `new` **Revisión** antes de aplicar: veredicto, mapa de dónde sale cada sesión, ejercicios movidos resaltados («de Empuje»), superseries juntas, tabla por músculo. «Aplicar» solo ahí.
- `new` **Decisiones con propuesta** (cardio, core, tiempo por sesión): nunca bloquean aplicar.
- `keep` Alcance, inicio de semana, bloques, modos de contenido, impacto, confirmación, comparar, varios, propio, buscar, error: misma lógica. `move`: pasan a la hoja «Cómo aplicar» (botón de la barra) y salen del camino principal.
- `new` «Conservar y reubicar» viene elegido si la semana tiene sesiones; en semana vacía, «Con ejercicios de plantilla» (`ask`).
- `new` **Deshacer** (resumen tras aplicar + ⋯ de la semana). `out` el texto «no se puede deshacer automáticamente».
- Semana en curso: lo hecho no se toca (check, sin asa), cuenta en el volumen y ocupa el día del reparto que más se le parece. Semanas hechas no se eligen en «Varias».
- Rechazos con su frase (competición, variantes B/C/D, sin ejercicios, menos ejercicios que días, falta un levantamiento): «Sesiones vacías» sigue disponible.
- Plan de autor: usa el aviso del asistente («Este programa trae su reparto de autor…», «Adaptar» / «Mantener mi reparto»).
- Descartado: radios en las tarjetas (el reparto se elige en la vista previa), barras sueltas de 6 px (la tira de 7 casillas ya lleva letra, color e inicial).

## 4. Qué exige implementarlo
- **Motor**: ver `reparto-motor.md`. En corto: (a) MIGRATE llama a `SplitRedistributor` con `weekdays` y `propagateToOtherWeeks`; (b) fachada pura `SplitMigrationPlanner.plan()` que devuelve el plan (días, movimientos, volumen/frecuencia, avisos, decisiones) y es lo que luego aplica `apply`; (c) bloqueo de sesiones hechas (hoy el motor no sabe qué está hecho); (d) término de estabilidad y día preferido por ejercicio entre semanas; (e) decisiones y recorte por tiempo opcional; (f) instantánea para deshacer.
- **Datos**: `SplitTemplate.summary` (descripción nueva) o tabla aparte; `Program.lastSplitChange` (instantánea nullable, JSON, sin migración de Room, Room está congelado). Frecuencia y nivel salen de datos existentes.
- **UI**: `SplitPreviewScreen`, `SplitReviewScreen`, hojas de decisiones y resumen; tarjeta nueva del catálogo con tira de días; filtros por días. Se reutilizan el tablero del asistente y `SplitWeekCircles` etc.
- **Rendimiento**: el cálculo va en `Dispatchers.Default`; `DayAssigner` ya limita los intercambios a 60 unidades.
- **Textos nuevos**: ver `scenes-rp.js`; los del tablero del asistente se reutilizan tal cual.

## 5. Dudas que de verdad hay que decidir
1. ¿Apruebas las 35 descripciones nuevas? (están en `RP_SPLITS` y en el informe).
2. ¿El reparto puede cambiar tus días de entreno cuando el número no cuadra (con aviso y arrastre)? Es lo que ya hace hoy; la alternativa es pedirte los días antes.
3. «Deshacer»: ¿hasta que edites la semana (propuesto, la copia viaja con el programa) o solo mientras la hoja está abierta?
4. ¿«Conservar y reubicar» preseleccionado, y «con plantilla» en semanas vacías?
5. En la semana en curso, ¿cada sesión hecha ocupa el día del reparto que más se le parece (propuesto) o prefieres que las semanas en curso no se puedan cambiar?
6. ¿Recorte por tiempo por sesión (quita series de accesorios) disponible aquí, siempre como decisión explícita y apagada por defecto?
