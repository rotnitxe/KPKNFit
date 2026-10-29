# Paridad 1:1 con los lienzos (orden del 28-oct-2026, ~16:00)

## Orden del usuario (literal, resumida sin cambiar el sentido)

> DEBES APLICAR LAS COSAS AL PIE DE LA LETRA. SI NO VA A QUEDAR 99,999 % IGUAL, NO ME INTERESA TU ENTREGA.
> Program Detail y Progreso (rediseño anterior de este chat) NO cumplen: siguen heredando elementos antiguos que no deberían estar.
> SI APARECEN GRÁFICOS E INFORMACIÓN EN LA ORIGINAL QUE EL NUEVO DISEÑO NO CONTEMPLA, SE ELIMINA. Si no queda claro qué eliminar, PREGUNTAR.
> Ejemplos que hay que eliminar: la tabla gigante de relación empuje/tirón («tabla de mierda fea»); los avisos de la vista de
> Semana sobre procedencia del plan, progreso del plan, temporada y próxima semana («puro relleno»).
> Nutrición: lo del gráfico que se mostrará en Nutrición (la huella del día) aplica SOLO a ese elemento individual; el resto de
> elementos aprobados (registro, etc.) mandan en cómo se ve esa pantalla.

Antes (mismo turno): «Ok» a implementar Registros (lienzo v4, https://claude.ai/artifact/AdMLuhhAnJPpt1iH4kK2xj).

## Lo que ya se sabe

- Los avisos «Temporada» (`ProgramSeasonModule`), «Próxima semana» (`ProgramPlannedLoadModule`), procedencia
  (`ProgramAboutModule`) y progreso del plan (`ProgramProgressModule`) estaban en la Semana del master ANTERIOR
  (164058d4f / de18c9f3c, el APK «KPKN Nutricion v2» que tiene el usuario). En la versión integrada hoy (df3a190b5) ya no se
  llaman; sus archivos siguen en `screens/programdetail/design/` → borrarlos si no los usa nadie.
- La tabla empuje/tirón es `VolumeAnalyticsCard` (`components/ProgramAnalyticsCards.kt`), que se pinta en
  `components/editor/MacrocycleEditorLegacy.kt:505` dentro de Estructura («Revisión del plan»). SE ELIMINA.
- La pantalla real sigue montada sobre piezas viejas: `DayView` (Semana), `MacrocycleEditor` + `MacrocycleEditorLegacy`
  (Estructura), `VolumeView` (volumen de Progreso). Cada una puede arrastrar elementos que el lienzo no tiene.

- OJO: el lienzo v6 SÍ tiene la escena `e-review` «Revisión del plan» (Estructura): avisos con semáforo + «Proporciones
  semanales» (Empuje/Tirón, Empuje H/Tirón H, Cuádriceps/Posterior, Tren sup./inf. como barras) + «Cobertura» (4 filas). La app
  pinta en su lugar la tabla vieja `VolumeAnalyticsCard`. DUDA para el usuario: ¿fuera todo, o la versión compacta del lienzo?
- Hojas de render obsoletas que usan los módulos viejos: `test/.../render/ProgramDetailClosureRenderSheetTest.kt`,
  `ProgramDetailHeaderRenderSheetTest.kt`; prueba `design/ProgramPlannedLoadModuleTest.kt`. Inventario en curso por un agente
  (Opus) → `INVENTARIO_PARIDAD.md`.

## Método

1. Inventario por pestaña (Semana, Progreso, Estructura, cabecera, hojas, Programas, historias, resumen del mes): lo que pinta
   la app frente a lo que tiene cada escena del lienzo (`docs/design/program-detail-v1/src/scenes*.js`).
2. Lo que la app pinta y el lienzo no tiene → se elimina (preguntar solo si no está claro).
3. Lo que el lienzo tiene distinto o falta → se rehace igual (textos, orden, tamaños, colores, iconos).
4. Verificación visual lado a lado: render de la app (Robolectric o emulador) junto a la captura de la escena del lienzo.
5. Después: Registros v4 1:1 (competencias, crear programa, registro de comida, buscar, vitaminas y minerales, historias).
6. Integrar en master y push al terminar.

## Estado

- [x] Inventario → `INVENTARIO_PARIDAD.md` (59 sobra, 101 falta/difiere, 24 dudosos). Dudosos de «quitar o no» preguntados al
  usuario el 28-oct ~16:30; el resto se resuelve con el patrón de la escena más cercana sin perder funciones.
  Master avanzó (35cc86392, Nutrición v2 QA): fusionar antes de integrar.
- [ ] Eliminaciones
- [ ] Correcciones
- [ ] Verificación lado a lado
- [ ] Registros v4

## Respuestas del usuario a los dudosos (28-oct ~16:40)

- E-D1 «Revisión del plan»: **QUITARLA ENTERA** (ni la tabla vieja ni la versión compacta del lienzo; Estructura sin revisión).
- Semana (W-D1…W-D4): «en la vista de semana quita todo lo que no salga en el prototipo, usaremos EL PROTOTIPO como fuente de
  verdad». → nombre/nota de semana bajo el navegador, casilla «Confirmar sesión opcional» en la agenda y días «Fuera de
  programa» salen de la agenda (las funciones que el lienzo ponga en un ⋯ se quedan ahí; lo demás se va).
- Progreso (G-D1, G-D3): «Ambos rediseñados (sin prototipo, para ahorrar tiempo) sin tirar hacia abajo la información
  importante». → los estados vacíos del volumen y la hoja «Calibrar» SE QUEDAN, rehechos con el lenguaje del lienzo.
- Estructura (E-D2…E-D5): «Nada de esto» → mesociclos, aviso «Revisa la estructura temporal» y hojas sin escena SE QUEDAN,
  rehechos con el patrón de e-block / e-dates.
- Resto de dudosos (C-D*, G-D2, H-D*, P-D*): patrón de la escena más cercana del lienzo, sin perder funciones; decisión anotada
  en el informe de cada paquete.

## Ejecución (paquetes en worktrees aislados, subagentes Opus/Sonnet effort high)

Ola 1 en paralelo, cada uno SOLO en su zona y, dentro de `ProgramDetailScreen.kt`, solo en su región:
- PA Cabecera/barra + Semana (región de cabecera ~350-760 y rama SEMANA; DayView.kt; design/ de cabecera y semana).
- PB Progreso (rama VOLUMEN; VolumeView.kt; design/progress/*; hoja Calibrar rediseñada).
- PC Estructura (rama MACROCICLO; MacrocycleEditor.kt, editor/*, design/structure/*; borrar ProgramAnalyticsCards.kt y la
  revisión del plan).
- PE Programas (screens/programs/*).
Ola 2: PD Hojas y menús, tras fusionar la ola 1. Después: borrar módulos muertos y hojas de render obsoletas, fusionar master,
verificación lado a lado, integrar y push. Luego Registros v4.

## Mensaje del usuario (28-oct, durante la ola 1)

> «Como puedes ver en la vista del programa, hay como 4 bloques de tarjetas de progreso del programa, temporada, próxima
> semana y sobre este programa que son relleno visual y empujan LO QUE SÍ IMPORTA HACIA ABAJO. ESTO JAMÁS DEBE PASAR.»

La captura era del APK «KPKN Nutricion v2» (20477bae7, 13:03), que NO trae el rediseño (df3a190b5). En el código actual no
se llaman, pero la regla queda: entre la portada y la semana no hay NADA salvo lo del lienzo (acción del día + campana;
Competición compacta solo en p-comp). Encargado a PA: borrar ProgramAbout/Progress/Season/PlannedLoadModule (+ prueba y
hojas de render), una sola línea de alcance en la portada, avisos solo en la campana.

Encargo de la ola 2 (PD) redactado y listo: scratchpad de la sesión `encargo_PD.md` (decisiones H-D1 confirmación en hoja corta, H-D2 m-split entra, H-D3 una fila por tipo con «y N más").

### Ola 1 — PE Programas (28-oct)
Hecho en `worktree-agent-a43fe949e6aee9390` 7c7847ac5 (compila; 247 pruebas de programs.* en verde). Pedidos 3 ajustes: meta del catálogo «D días · N semanas · Objetivo» (sin nivel), quitar «Elegir otra forma», KpknChip con altura opcional (30 dp en Programas). Mínimo 13 sp de KpknType se mantiene (regla vigente): única diferencia consciente con las líneas de 11-12 px del lienzo.
PE fusionado en la rama (fast-forward a bce3d60a6, ajustes hechos: meta catálogo, sin «Elegir otra forma», chips 30 dp).

Integración: por TURNO de master (C:\kw\tools\INTEGRACION_GUIA.md, coordina «Mejoras sistema búsqueda y nutrientes Nutrición»); al final borrar la rama y los worktrees agent-* y avisarle.

### Ola 1 — PA Cabecera y Semana (28-oct)
Hecho en `worktree-agent-aef4b1cd1636ff1fa` 47e7b955f, fusionado en la rama (347d74eaf). Compila; 11 clases de prueba en
verde. Borrados ProgramAbout/Progress/Season/PlannedLoadModule + ProgramWeekRuler, sus pruebas y las hojas de render
Header/Closure; fuera seasonState y plannedLoadProjection del ViewModel. Decisiones: W-D2 la casilla de confirmar la
opcional SALE (toggleOptionalSessionConfirmation queda sin interfaz; afecta a la previsión de nutrición) — decirlo en el
informe final; W-D3 días fuera de programa salen salvo los que tienen sesión; C-D1 rutina sin cinta en la portada.
Pendiente de verificación visual lado a lado.

### Ola 1 — PC Estructura (28-oct)
Hecho en `worktree-agent-aa26247470af2d5d6` efcdc6c10; fusionado (13dc446b6; conflictos solo en pruebas: hoja Closure queda
borrada, imports de ProgramDetailSamples). Compila en su rama; 10 clases en verde. Borrados ProgramAnalyticsCards
(revisión del plan), RouteSection, KeyDatesSection, ProgramRoute*, ProgramCalendarModule, ProgramToolsModule, etc.
Decisiones: «Ruta» → ⋯ del bloque; fechas clave y calendario → hoja «Fechas»; biblioteca aplica al tocar (interruptor
«Sustituir semanas con sesiones»); rutina con módulo de acciones al pie (mesociclos, añadir semana, calendarizar, pasar a
bloques) para no perder funciones. structurePhaseColor ahora usa ProgramPhase.color (eran iguales).
Limpieza pendiente (tras PB): imports sin uso en ProgramDetailScreen (ProgramAnalyticsEngine, exerciseCatalogSnapshot),
setMacrocycleRoadmapExpanded y borradores de timeline del VM, exerciseTrainingMaxSummary/Missing.

### 28-oct (noche) — PB colgado y relanzado; PD en paralelo
El primer PB se quedó 5 h colgado abriendo el navegador (artifact.html) sin tocar ningún archivo: detenido, su worktree
vacío y su rama borrados. Relanzados PB (Progreso) y PD (Hojas y menús) EN PARALELO desde 1f45107f3, sin navegador; PB no
toca hojas salvo Calibrar, PD no toca Progreso ni Calibrar. Encargos en el scratchpad de la sesión (`encargo_PB.md`,
`encargo_PD.md`). En paralelo, inventario de Registros v4 (solo lectura, contra origin/master) →
`docs/design/registros-v1/INVENTARIO_PARIDAD.md`. Comida/búsqueda/micro chocan con la sesión de Nutrición: implementar
tras integrar Program Detail y coordinando con ella.
Limpieza lista para hacer tras fusionar: import ProgramAnalyticsEngine (ProgramDetailScreen.kt:34),
setMacrocycleRoadmapExpanded + macrocycleRoadmapExpanded (VM:949/101), exerciseTrainingMaxSummary/Missing
(ExerciseTrainingMaxPanel.kt:34/42); exerciseCatalogSnapshot sí se usa en data/ (no tocar).

### 28-oct (noche) — Registros v4 en marcha
Inventario hecho (750a7a9c6: 60 sobra, 88 falta/difiere, 35 dudosos). Dudosos resueltos con la regla aprobada (patrón de la
escena más cercana sin perder funciones; contradicciones: manda el dibujo salvo nota explícita); decisiones en los encargos.
En paralelo desde 750a7a9c6: R-FS (comida + buscar; huella como hueco `footprint`), R-CR (crear programa), R-C
(competencias; NO toca programdetail: la píldora «Planificar intentos» → c-plan la cableo yo). Reglas comunes:
scratchpad `encargo_R_comun.md`. En cola por disco (2,6 GB por worktree + build): R-M (vitaminas y minerales, tras R-FS
o en paralelo sin tocar el registro) y R-H (historias; tras R-C). H-D1 por defecto: la hoja nueva solo para las historias
nuevas; programa y mes siguen con la hoja del lienzo v6 ya aprobada.

### Registros — R-CR Crear programa (fusionado, 7340c4e1c; código de 34b3b163c)
Sin compilar aún: la base no compilaba (ProgramHistorySheet del menú sin today/onSaveCopy, suma de la ola 1) → arreglado en
f164a2549 y avisado a R-C y R-FS (cherry-pick). Pruebas a correr con la suma: PostDischargeRoutingTest, DeepLinkRouterTest,
ProgramsViewModelTest, ProgramReadyCopyTest, LibraryVisibleEntriesTest, LibraryCardModelTest, NewProgramSheetsTest,
LibraryReachabilityTest, NewProgramInstanceCopyTest, SetupStepGraphNewProgramTest, SetupStepGraphTest,
SetupWizardEntrenoPlanTest. Decisiones para el informe: modo aparte NEW_PROGRAM; «Lo que ya sabemos» fila fija; en la ruta del
catálogo no hay paso PLAN; máximos antes de la semana (orden funcional, el lienzo pone la semana primero); atrás en «Listo»
al importar deja el programa guardado sin activar; «Desde cero» solo pasa por «Listo» si hay uno en curso; crear desde
plantilla ya no activa. Pendiente fuera de zona: comentario de PlanGoalMatcher.kt que nombra la hoja vieja.
Lanzados R-M (vitaminas; entrega NutrientFootprint para los huecos de R-FS) y R-H (historias; entrega
CompetitionStoryShareSheet para el onShare de R-C) desde 7340c4e1c. Disco: 244 GB libres.

### Ola 1 — PB Progreso (fusionado, de63aca2b; código 8fcb097d6)
Compila en su rama; 10 clases / 58 pruebas en verde. Orden: alcance → resumen → Fuerza · e1RM → series duras → trabajo
acumulado → series por músculo → cumplimiento → esfuerzo → molestias → decisiones → resúmenes mensuales (citados por el
lienzo). Módulo sin datos no se pinta; g-empty solo resumen y fuerza. Volumen sigue los chips (sale el selector propio),
indirectas ½ fijas, 9 músculos del lienzo siempre. «Calibrar» → «Revisar mis respuestas». No hecho: ejercicios por rol (no
existen: los 5 con más sesiones), franja de rangos solo con perfil calibrado, «Semana 5» en chips, artículo en la frase.
Limpieza: VM programDiscomforts, exerciseDiscomfortAssociations, calibrationAnswers sin uso (tras PD).

### Ola 2 — PD Hojas y menús (fusionado, bc411d6bc; código 6534e1724)
Compila en su rama; 14 clases / 151 pruebas en verde. Kit común design/ProgramSheetKit.kt; Pendiente abre cada hoja final
(sesiones sin registrar → ProgramMissedSessionsSheet / ProgramScheduleReviewSheet nuevas); menús de semana y sesión como
hojas (ProgramWeekMenus.kt); m-split rehecho. Borrados ProgramProposalsModule, ProgramScheduleModule,
ProgramTransitionModule, ProgramPendingDetailSheet, SplitCurrentModule/TopIsland/TagRow y el banner de transición.
Distinto al lienzo (para el informe): «Reemplazar» de m-paste apagado por seguridad; «Mover a otro día» sigue como menú
anclado; menú de semana de Estructura sin tocar; «Seleccionar varios» del reparto como acción de texto; colores de m-split
del lienzo; varias propuestas → «Aplicar» la primera, «Mantener carga» rechaza todas; «Fin del programa pronto» sin acción.
Conflictos resueltos: copia desde Versiones guardadas se abre (como Estructura); Pr11PolishRenderSheetTest sin los casos de
volumen y propuestas (borrados por PB y PD). Limpieza hecha (6a05fe25b). Program Detail COMPLETO en la rama; falta compilar
la suma (se hará una vez con Registros).

### Registros — R-H Historias (fusionado, d2a8db74e; código 723f63411)
Compila en su rama; 18 pruebas en verde. Plantillas de competencia (Total, Intentos, Foto, Próxima, Tiempo, Tarima, Cuadro +
vídeo MP4 6 s), cuerpo (Cambio, Medidas) y nutrición (Semana, Macros, Racha); hoja nueva solo para estas. Conectado por mí
(4d0b00068): Cuerpo comparte desde el módulo de peso (shareHistory) y Nutrición desde la semana con macros medios
(nutritionStoryMacrosOf + 2 pruebas) y racha de «Comidas» (HabitStreaks.daily, la misma de Hábitos). Falta: onShare de
Competencias → CompetitionStoryShareSheet(competition, allCompetitions, onDismiss[, startWithNext]) cuando llegue R-C.
Decisiones para el informe: medalla solo 1-3; «Guardar · Más apps» guarda y abre el selector; culturismo sin foto → sin
historia; en objetivo = 85-110 %; Medidas sin «con la misma fuerza» (no hay dato).

### Registros — R-M Vitaminas y minerales (fusionado, 9f938cf3d; código 5e1e885a9)
Compila en su rama; NutrientFootprintModelTest 18, NutrientsSheetTest 10, NutrientGroups/Coverage/CustomFoodForm en verde.
Huella de 24 en tres bandas, módulo del día bajo NutritionDayModule, hoja del día con «A vigilar», hoja por nutriente con
«De dónde sale» y referencias. Piezas para los huecos de R-FS (paquete screens.nutrition.nutrients): NutrientFootprints.food /
fromPer100g / fromFoods / meal / savedLine; NutrientFootprintSection (ficha y ajuste, height Edit), NutrientFoodListContent,
NutrientMealModule + NutrientMealListSheet (registro), NutrientSavedLine (guardado), NutrientHomeLine (Inicio); perfil de
DriTargets.profileFor(settings, plan), energyKcal = kcal objetivo del día. Pendiente: «Completar con una ficha propia» →
customfood. Decisiones para el informe: cifras con fmtN del lienzo; cobertura sin artículo; notas a mano convertidas en
reglas; unidades cortas («µg»); 13 sp mínimo.

### Registros — R-C Competencias (fusionado, 0a2e61325; código edca306be)
Compila en su rama; 93 pruebas (3 omitidas) en verde. Hub, nueva (28 disciplinas), próxima, plan, hoy, resultado, puesto,
recuerdos, celebración, ficha, menú, marcas, comparar y plantillas por disciplina; borrado el asistente viejo, CompetitionScreen
y el archivo de Perfil. Conectado por mí (commit siguiente): onShare → CompetitionStoryShareHost (historias) en ficha y
recorridos; en Program Detail «Planificar intentos» → c-plan y «Modo competición» → c-today de la competencia enlazada.
Decisiones para el informe: barra de pasos solo donde el lienzo la dibuja; hero de Inicio solo «Modo competición»; hora de
carrera en hoja h/m/s (no rueda); «Comparar» solo en carga/reloj con historial; Room intacto (JSON con valores por defecto).
