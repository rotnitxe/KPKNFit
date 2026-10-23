# Mapa · editor de sesiones, catálogo de ejercicios y sus instancias (2026-10-20, master 8ac57c9d4)

Explorador de solo lectura sobre `C:\kw\u11` (= master). `SE/` = `screens/sessioneditor/`, `WO/` = `screens/workout/`.
Complementa `artifacts/editor-catalog-refinement-20261018/research/{editor,catalog,design-baseline}.md` del checkout principal.

## 1. Editor de sesiones
- Ruta `KpknRoute.SessionEditor` (`session-editor/{programId}/{sessionId}?weekId&macroIndex&mesoIndex&dayOfWeek`), montada en
  `MainActivity`. Lo abren: «Editar» del héroe de Inicio, «Editar» de Program Detail, crear sesión en un día vacío, y el propio
  editor (hoja Semana). No lo abren la sesión en vivo ni el wizard.
- `SE/SessionEditorScreen.kt` (guardado automático 1,2 s, catálogo de página completa con `CatalogLaunchRequest`, carga/error con
  botones M3, fila superior + aviso + `LazyColumn`, barra inferior, hoja Semana, arrastre, Asistente, hojas, ficha).
- Marco: `SE/frame/SessionEditorFrameTopBar.kt` (Atrás, día anterior/siguiente, abrir semana, «Más opciones» → Transferir,
  Versiones), `SessionEditorFrameHeader.kt` (nombre y descripción editables, protocolo y resumen, deslizar cambia de día),
  `SessionEditorFrameBottomBar.kt` (isla de vidrio con anillo de tiempo y contador: Añadir · Asistente · Reglas; «Reglas» abre la
  pestaña Tiempo), `SessionEditorFrameWeekSheet.kt` (días, principal, variantes, semanas, crear sesión), `SessionEditorFrameBlockNotice.kt`.
- ViewModel `SE/SessionEditorViewModel*.kt`; borrador `SessionEditorDraftPersistence`; versiones `TrainedSessionVersionStore`;
  reglas `SessionEditorRulesEngine`; impacto `SessionEditorAugeComputation`; arrastre `SessionEditorDragController`. Deshacer solo
  para borrar ejercicio (`SessionExerciseUndoNotice`).
- Lista: `SessionListItems`, `SessionEditorScrollRenderer` («Añadir ejercicio», «Nuevo grupo», «Crear espacio de cardio», «Añadir
  cardio», rótulos CARDIO/FUERZA/SIN GRUPO, «Mover al inicio/final»), `GroupEditorCard` (borrar deslizando), `SessionEditorEmptyState`
  («¿Qué quieres entrenar en esta sesión?» Fuerza/Cardio), arrastre en `SessionEditorVisuals`.
- Ejercicio: `ExerciseEditorCard` (plegado/desplegado, «Meta del ejercicio», «MOVIMIENTO DE COMPETICIÓN», asa), `ExerciseEditorExpanded`
  (APROXIMACIÓN, «+ Movilidad», «+ Aproximación», SERIES EFECTIVAS, relación), `PreparationCarouselCards`, `ExerciseOptionsBar`
  (modo ▾ con 7 modos, descanso, Bilateral/Unilateral, menú: ROM, Relacionar, Superserie, Unidad personalizada, Carga inteligente,
  Meta), `ExerciseSetList` («+ Añadir serie», Aplicar a todas, Duplicar, Subir/Bajar, Eliminar), `InlineSetRow`
  (+ `InlineSetRowTechniqueChips`: Drop-set, Rest-pause), `SessionEditorControls` (`ExerciseSetsCarousel` compartido con la sesión en
  vivo, selectores de modo y unilateral), diálogos `ExerciseSmartLoadDialog`, `ExerciseGoalDialog`, `ExerciseCustomUnitDialog`.
  Descanso con `KpknNativeTimePickerDialog` (TimePicker de reloj 24 h para minutos:segundos; trunca > 23 min).
- Superseries: `SupersetGroupEditorCard` («Rondas y descansos», «Convertir en opcional», «Disolver», «Eliminar superserie»),
  `SupersetRoundsAndConfig` (overlay de config, rondas, carga inteligente, meta, descanso).
- Cardio: `CardioEditorCard` (Continuo · Series · Intervalos, bloques, Avanzado, historial, plantillas), `CardioIntervalsEditor`,
  `HiitTemplatePickerDialog`, ruedas `NumberPicker` nativas.
- Hojas (`SE/components/sheets/SessionEditorSheets.kt`): Asistente (Impacto · Sugerencias · Plantillas con `TemplateCatalogBrowser`),
  Reglas y tiempo (`RulesSheet` + `RulesTimeTab`), Versiones (`HistorySheet`), Transferir (`SessionClonerSheet`), Acciones rápidas
  (7 filas: Ficha, Cambiar, + Aproximación, + Movilidad, Crear/Gestionar superserie, Duplicar, Eliminar, más mini ficha), Aproximación
  (`WarmupSheet`, piel M3), Catálogo de movilidad, Selector de cardio (`CardioCatalogSheet`), Crear superserie (2–4), Gestionar
  superserie, Relacionar ejercicio. Sin uso: BACKGROUND (`CoverSheet`), SAVE (`SaveSheet`), CARDIO_PLACEMENT, `TemplatesSheet`.
- Ficha: `SessionExerciseDetailSheet` → `ExerciseFicha` (contexto SESSION, sin pie).

## 2. Otros editores
- Sesión en vivo (`WO/WorkoutStructureSheetsHost.kt`): «Editar series / Editar cardio» (`WorkoutEditSeriesSheet` en `WorkoutDrawer`,
  reusa selectores del editor, «Descanso entre lados (s)» como campo de texto, carga inteligente, `ExerciseSetsCarousel`, «Confirmar
  cambios»; cardio con `CardioEditorCard`), crear superserie en vivo (`WorkoutSupersetCreatorSheet`, diálogo con campos «(s)»),
  rondas y descansos (diálogo), opciones C1/C2, reordenar C3, etiquetas, setup, RM, diálogos de alcance (nombres distintos para el
  mismo alcance), reemplazo (cardio → `CardioCatalogSheet`).
- Importador: `screens/programs/import/ImportProgramScreen.kt`, `ImportExerciseEditor.kt` (series, reps, carga, esfuerzo, descanso,
  deshacer propio), `ImportAddExercisePanel.kt` (búsqueda propia); también en el wizard (`EntrenoProgramImportStep`).
- Wizard: panel «Cambiar» (`PlanDetailOverlay`, alternativas `SwapOption`) y su ficha (`ExerciseInfoOverlay` → `ExerciseFicha`, WIZARD).
- Programas (siguiente ronda, Program Detail): `ProgramEditorScreen`, `MacrocycleEditorLegacy`, `ProgramCycleEditorSheet`,
  `ProgramTemplatesSheet`, `SplitCustomEditorSheet`, `TrainingMaxWizard`, `CreateProgramTemplateSheet`.

## 3. Catálogo
- Contrato `SE/CatalogContracts.kt`: orígenes SESSION_EDITOR, LIVE_SESSION, REPLACEMENT, SUPERSET, MOBILITY (sin uso), TECHNIQUE;
  modos SINGLE, MULTIPLE, REPLACEMENT, SUPERSET. Ruta `KpknRoute.ExerciseCatalog`; la abren el editor, la sesión en vivo y Técnica
  («Buscar en el catálogo», pie «Elegir»). Aprende/wikilab no lo abren.
- `SE/components/ExerciseCatalogScreen.kt` (página opaca, contexto H1) → `ExercisePickerV2Catalog.kt` (único selector real): cabecera,
  «Todos los ejercicios» / «Resultados para «q»» / «Primero los más parecidos al ejercicio actual», «Ejercicios personalizados», fila,
  chips de variante, ficha, «Crear ejercicio», barra de reemplazo, isla de selección, configurador de superserie, borrar propio.
- `SE/design/`: `CatalogListHeader` («Añadir ejercicios»/«Reemplazar», buscador, filtros Todos · Tren superior · Tren inferior ·
  Músculo; al reemplazar Mismo músculo · Todos), `CatalogExerciseRow` («Ver ficha»), `CatalogVariantChips`, `CatalogRowThumbnail`,
  `CatalogListStates` (esqueleto, «No encontramos “q”» con «Crear ejercicio», error con «Reintentar»), `CatalogSelectionIsland`
  («Añadir N», «Superserie»), `CatalogSupersetSheet` («Configurar superserie»), `CatalogReplacement` (REEMPLAZANDO y «Reemplazar por
  X»), `CatalogCustomExerciseEditor` («Editar ejercicio», solo para propios; «Crear ejercicio» crea automático sin formulario).
- Muerto: `ExercisePickerSheet.kt` tras el `return` (catálogo antiguo), `CatalogSelectionWizard`, `ExercisePickerCards`,
  `ExerciseAspectChips`, `ExercisePickerCatalogLogic`.
- Ficha única `ExerciseFicha` (contextos CATALOG, REPLACEMENT, SESSION, WIZARD; pie Añadir/Quitar/Elegir). Huecos: los cues
  (`FichaCues`) no se muestran; medios solo de vídeo no dan entrada al álbum; barra de título sólida en vez de vidrio.
- Otros catálogos sueltos: cardio, movilidad, relación, miembros de superserie, importador, swap del wizard. Favoritos no existen.

## 4. Pruebas y hojas de render
`SessionEditorFrame/List/ExerciseExpanded/SupersetQuickActions/CardioEditor/AssistantRules`, `EditarSeries`, `CatalogList`,
`CatalogDetail`, `CatalogReplace`, `LiveSessionOptions`, `ExerciseInfo`, `KpknSetTable`, `ImportEditorRenderTest`. No hay render de la
pantalla entera del editor ni de la ficha a mitad de desplazamiento.

## 5. Problemas visibles
1. Código muerto grande (selector antiguo, hojas sin abrir, imports y parámetros sin uso).
2. Duplicados: cuatro creadores de superserie, tres entradas de descanso, carga inteligente y meta dobles, dos reordenadores, mini
   ficha en acciones rápidas, aproximación por dos caminos, editor y búsqueda propios del importador, «Cambiar» propio del wizard.
3. Flujo: «Cambiar» del editor abre el catálogo con la búsqueda rellena y sin contexto H1; la cabecera dice «Añadir ejercicios» al
   elegir para Técnica o superserie; «Reglas» abre Tiempo; alcances con nombres distintos; «Añadir/Agregar/Agrega»; mayúsculas a mano;
   «Unidad Personalizada».
4. Aspecto antiguo (M3, `DarkEditor*`, `NumberPicker`) en Transferir, Aproximación, selector de cardio, campos del editor, estado de
   error; acciones rápidas en lista frente a las losas de la sesión en vivo.
5. Acoplamiento: la sesión en vivo reusa piezas del editor (`CardioEditorCard`, `ExerciseSetsCarousel`, selectores, contratos,
   `SessionExerciseDetailSheet`).
