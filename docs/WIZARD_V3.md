# Wizard v3 (oct-2026): las 24 correcciones del alta

Referencia de lo que cambió en la ola «Wizard v3» (rama `feat/wizard-v3`, plan aprobado el 2026-10-08). Complementa a
[`WIZARD_PAGINA_LARGA.md`](WIZARD_PAGINA_LARGA.md) (cómo funciona la página larga) y a [`WIZARD_ENTRENO_V2.md`](WIZARD_ENTRENO_V2.md)
(el bloque Entreno). Los briefs de cada paquete están en `docs/wizard-v3/briefs/` y explican el porqué de cada decisión.

## 1. La ruta nueva

La ruta sigue saliendo SOLO del borrador (`SetupStepGraph.nodes`). Entre corchetes, lo condicional.

**Entreno**: `EXPERIENCE, EQUIPMENT, AVAILABILITY, GOAL, [COMPETITION], FRESH_DAY, WEEKDAYS, SESSION_TIME, [CARDIO_TYPE, CARDIO_TIME,
[CARDIO_BASE]], [VOLUME_TECHNIQUE], VOLUME_CONSISTENCY, VOLUME_STRENGTH, VOLUME_MOBILITY, [PROGRAM_SOURCE]`, y después según la fuente del
programa:
- «Que KPKN me recomiende uno» (o sin la pregunta): `[CAPABILITIES], PRIORITIES, [AVOIDED_EXERCISES], [ARM_STYLE], [TRAINING_VARIANTS],
  [TRAINING_MAX], PLAN, [WEEK_LAYOUT], MILESTONE_TRAINING`.
- «Importar mi programa»: `PROGRAM_IMPORT, [WEEK_LAYOUT], MILESTONE_TRAINING`.
- «Lo armaré yo más adelante»: `MILESTONE_TRAINING` (alta sin programa).

**RINGS**: `RINGS_INTRO, RINGS_SESSION_DAYS, [RINGS_MAIN_LIFTS, [RINGS_GROUP_SETS], RINGS_INTENSITY], RINGS_FEELINGS, RINGS_DISCOMFORT,
RINGS_RESULT, MILESTONE_RINGS`. Las preguntas de ejercicios e intensidad solo salen si hubo días entrenados; las series por grupo, si alguno
cae en las últimas 72 h. Los pasos retirados (`RINGS_RECENT`, `RINGS_SESSIONS`, `RINGS_RECENCY`, `RINGS_ACTIVITY`, `RINGS_AXIAL` y las tres
sensaciones sueltas) siguen en el enum para los borradores viejos, que conservan su ley v1.

Cuándo sale cada paso condicional:

| Paso | Condición |
|---|---|
| `COMPETITION` | Powerlifting, Halterofilia, Strongman, Culturismo, Armwrestling o Calistenia, con experiencia intermedia o avanzada |
| `PROGRAM_SOURCE` | experiencia «Tengo experiencia» |
| `AVOIDED_EXERCISES` | Culturismo |
| `ARM_STYLE` | Armwrestling |
| `TRAINING_VARIANTS` | Powerlifting o Powerbuilding, intermedio o avanzado |
| `TRAINING_MAX` | marcas solo desde «Ya entreno con constancia» (Fuerza y masa con barra, Powerlifting, Powerbuilding, Strongman, Halterofilia) |
| `CARDIO_BASE` | objetivo con cardio |
| `CAPABILITIES` | peso corporal o Calistenia: repeticiones seguidas (dominadas obligatorias si hay barra) |

`SetupStepGraph.REVISION` = 5 (lo subió C1; no hace falta subirlo por pasos nuevos con condición).

## 2. Qué se hizo por punto

| # | Corrección | Dónde vive |
|---|---|---|
| 1 | Bienvenida con vídeo real | `welcome/WelcomeVideoPlayer.kt`: usa `res/raw/welcome_{entreno,nutricion,rings}.mp4` y sus pósteres si existen; si no, las escenas Canvas de siempre |
| 2 | Prólogo «Para desbloquear todo KPKN…» | `design/WizardPrologueOverlay.kt` (antes del overlay de arranque) |
| 3 | «Por qué lo preguntamos» en vidrio | `design/WizardGlassNote.kt` (`LocalWizardGlassNote`, capa hermana del desenfoque) |
| 4 | Símbolos de material | `design/entreno/EquipmentSymbolArt*.kt` |
| 5 | Volver sin «deshacer» | `SetupStepGraph.nextNeedingAttention` + `closeMilestonesBehind`: al confirmar una edición se vuelve al primer paso pendiente o a donde se estaba |
| 6–7 | Símbolos de objetivo y disciplina | `design/entreno/plan/GoalProfileArt*.kt`, `DisciplinePoses.kt` |
| 8 | Día con más energía preseleccionado | `withFreshestDay` y `effectiveWeekStart()` (la semana empieza en el día fuerte en todas las rutas) |
| 9 | Músculos sin Trapecio y legibles | `design/entreno/MuscleArt*.kt`, `MuscleSymbols.kt` (encuadre por músculo, contracción al elegir); Espalda cubre dorsales y trapecio |
| 10 | Punto de partida por perfil | pasos `TRAINING_VARIANTS`, `AVOIDED_EXERCISES`, `CARDIO_BASE`, `ARM_STYLE`; marcas con «1RM / Mi mejor serie» y press militar; el generador los lee (`RoutineRequest`, `VariantPools`, `AvoidGroups`) |
| 11 | Avanzados: cómo tener su programa + importar | `PROGRAM_SOURCE`, `PROGRAM_IMPORT`; lector propio de Excel/CSV/texto (`domain/training/importer/`) y la misma vista previa en Programas → «Importar» |
| 12 | Oferta de programas | 30 programas KPKN (10 perfiles × simple, en bloques, periodizado), PHUL/PHAT en una sola ficha, sin «versión anterior», nombres sin «KPKN» ni «a medida» |
| 13 | ¿Compites? | `COMPETITION` con calendario propio y línea de bloques; el programa termina en la semana de la competición; al activar, fecha clave, registro PLANNED, recordatorios y permiso de notificaciones |
| 14 | Portadas propias | `assets/program_covers/cover_<id_saneado>.webp` (`ProgramCoverRepository`, `kpkn-cover://<entryId>`); prompts en `docs/programas/PROMPTS_PORTADAS.md` |
| 15 | Página del programa | semana desde el día de inicio; tocar un día muestra sus ejercicios y «Cambiar» (`ExerciseSwapOptions`, `draft.exerciseOverrides`) |
| 16 | Motor de repartos | `domain/training/split/` v2: un pesado por patrón y día, principales primero, recortes declarados (`DayTimeFit`) |
| 17–19 | Nutrición | subtextos del objetivo, regla de peso objetivo con diferencia y estimación, «Hasta tu objetivo» en el plan, celebración en el hito, texto de pesajes |
| 20 | Intro de RINGS y torre del logo | `ui/components/rings/KpknTowerRings.kt` en toda la app; `RINGS_INTRO` con el simulador del motor (`RingsIntroSimulator`) |
| 21 | Preguntas de RINGS | `screens/onboarding/rings/`; evidencia v2 (`InitialRecoveryEvidenceFactory.fromInputsV2`) |
| 22 | «Tus RINGS» interactivo | `rings/RingsResultStep.kt`: torre con los valores de Inicio, deslizadores 0–100 y músculos a mano; sin los tres botones |
| 23 | Revisión en carrusel | `SetupReviewStep.kt` con `ui/components/PagerDots.kt`; botón «Activar y entrar a KPKN» |
| 24 | Recorrido de la app | motor `ui/tour/` (F1) y contenido `ui/tour/TourScripts.kt` (F2): `tour-home`, `tour-training`, `tour-first-workout`, `tour-nutrition`, `tour-body`, `tour-profile`; «Repetir el recorrido» en Ajustes |

## 3. Motores nuevos

- **Periodizador** (`domain/training/periodization/`): `RoutinePeriodizer.build(request, base, variant, horizon)` materializa todas las semanas
  (ids `…-w<n>`); `PeriodLayout.phases(variant, horizon, heavy)` da las fases. `Horizon.Weeks(n)` o `Horizon.ToMeet(ancla, fecha, inicio)`.
  Variantes del catálogo: `generated:<perfil>` (simple), `:blocks` (6 sem) y `:periodized` (12 sem).
- **Competición en planes de autor**: `MeetRecipeFitter.fit(program, meet, anchor, weekStartDay)`: cola fija, recorta o repite acumulación,
  descarga cada 6 semanas. Aptos: `kpkn-native-sbd-4`, `candito-6`, `cube-method`, `calgary-16`, `tsa-9`, `gzcl-uhf-9`. Procedencia
  `ADAPTED`. Quien compite con fecha no ve la versión simple de los programas propios.
- **Evidencia v2 de RINGS**: días entrenados, ejercicios principales y series por grupo con las curvas reales del motor; sin días no hay
  evidencia; sin datos de columna, «Sin calibrar» (nunca 100 %). Las sensaciones solo penalizan; nunca son ancla.
- **Importador**: `XlsxReader` (ZIP + XmlPullParser), `CsvReader`, `ProgramImportParser`, `ExerciseNameMatcher`, `ImportedProgramBuilder`
  (procedencia «Programa importado»). En Free, un importado de más de 1 semana o 5 sesiones pide Pro.

## 4. Reglas nuevas que no hay que romper

1. **La huella (`SetupInputFootprint`) solo lleva lo que debe marcar pasos por revisar.** No entran: el nombre del evento, los ajustes 0–100
   de RINGS (`manualRings`, `manualMusclesV2`) ni las sustituciones de ejercicios (`exerciseOverrides`). Tampoco entran en `trainingKey`
   las sustituciones (se aplican después de generar, en `applyLayout`).
2. **La competición se lee SOLO con `activeCompetition()`, `activeMeetDate()` y `activeMeetName()`**: los campos crudos pueden quedar en el
   borrador aunque el paso haya salido de la ruta.
3. **Lo que se ve es lo que se activa**: la vista previa de RINGS usa la misma función que Inicio (incluida la Columna mezclada y su
   inversión al guardar); la del programa ya lleva las sustituciones.
4. **Setters nuevos al final de `SetupWizardViewModel`** y campos nuevos del borrador junto a los de su tema, no al final: los paquetes en
   paralelo chocaban ahí.
5. **Textos en español neutro sin género gramatical** («llegas con el cuerpo descansado», «Quiero ajustar mis músculos a mano»).

## 5. Cómo probar

- **Hojas de render** (Robolectric, sin teléfono): `KPKN_RENDER_DIR=<carpeta> … --tests "<Clase>RenderSheetTest"`. Hay hojas de símbolos
  (`SymbolRenderSheet`), músculos (`MuscleFamilyRenderSheetTest`), RINGS (`RingsQuestionRenderSheetTest`, `RingsResultRenderSheetTest`),
  competición, revisión (`ReviewCarouselRenderSheetTest`), detalle del programa y recorrido.
- **Arnés de depuración**: `WizardHarnessActivity` (`persona=qa…`, `activate=<ms>`) responde todos los pasos nuevos (`answeredAs`).
- **Datos de demostración** (solo debug): `DemoSeedActivity` tras activar la persona QA con el arnés:
  `adb shell am start -n com.example.kpkn.dbg/com.example.kpkn.debug.DemoSeedActivity`.
  Con Pro (solo debug y en memoria; al morir el proceso vuelve a Free): `adb shell am start -n com.example.kpkn.dbg/com.example.kpkn.debug.DemoSeedActivity --ez pro true`.

## 6. Pendientes conocidos

- Vídeos de la bienvenida: falta grabarlos (el código cae a las escenas Canvas mientras no existan).
- Semanas repetidas por `MeetRecipeFitter`: copian la carga de su semana de origen (sin escalón propio).
- La Columna del resultado está acotada por la mezcla de Inicio (`50 + (articular + guardianes)/4`), igual que en la app.
- El detalle del anillo en Inicio es una hoja modal aparte: el recorrido no puede resaltarla.
- `OlympicMarksTest` (Halterofilia avanzada) sigue en rojo, a decidir.
  - Qué pasa: con marcas, la serie de trabajo lleva `%1RM` y `ApproachPlanner.heaviestPercent()` la clasifica como muy pesada. Eso añade un *single* a la rampa, y el minuto extra tira el último ejercicio de la sesión.
  - Sin marcas no hay porcentaje y la rampa es más corta. Lo destapó el press militar que ahora entra en Halterofilia.
- Regresión amplia (2026-10-09): una clase deja vivo `ProgramRepository` con su `loadFromDb` esperando en un `StandardTestDispatcher`, y la siguiente que llama a `closeInstance()` se cuelga en `cancelAndJoin`. Ocurrió en `SetupWizardEntrenoPlanTest.setUp`.
  - No se encontró qué clase lo deja vivo: las que lo inician a mano lo cierran.
  - Si se repite, saca un `jstack` del ejecutor de pruebas, mata solo ese proceso y corre las clases restantes en una segunda pasada.
- Fallos que no son de la ola:
  - `ProgramSnapshotStoreTest` ×2 y `ProgramDetailViewModelTest` ×2 (copias de seguridad del programa): vienen de `master` 11aff1778.
  - `ExerciseDatabaseLoadTest` y `CanonicalJsonTest`: necesitan `catalog/` y `cloud-sync/`, que el checkout parcial no trae.
