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
- **Importador**: lee Excel, ODS, CSV, Word, texto, PDF y fotos (OCR en el teléfono). El usuario lo revisa y lo ajusta antes de
  guardarlo. Todo está en [`IMPORTADOR_PROGRAMAS.md`](IMPORTADOR_PROGRAMAS.md): lectores, reglas del analizador, emparejador de nombres,
  plantillas conocidas, editor con deshacer, bancos de pruebas y resultados. En Free, un importado de más de 1 semana o 5 sesiones pide Pro.

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

- Vídeos de la bienvenida: grabados el 2026-10-11 en el teléfono real (app `.dbg`, datos de demostración, Pro de depuración) e integrados en F3d.
  - Formato: 720 × 1488 (la proporción del lienzo de las escenas), H.264, sin audio y menos de 1 MB cada uno. Sin la barra de estado ni la del sistema.
  - RINGS: el deslizador solo existe en la hoja previa al entreno, así que el vídeo muestra una vista recortada de esa hoja, sin su título (ver abajo), y después el detalle del anillo en Inicio.
  - Si hay que volver a grabar: `phone_run.py --supervised` con `rec:`/`recwait:`/`pull:`, y el montaje con `C:\kw\tools\build_welcome_videos.py <crf>` (tomas en `C:\kw\shots\v3\videos\`).
- Semanas repetidas por `MeetRecipeFitter` (G2, decisión del usuario del 2026-10-11):
  - La n-ésima repetición sube un escalón de +2,5 %, con un tope de +10 %; las descargas no lo cuentan ni lo reinician.
  - Se escalan `weight`, redondeado a 0,5 kg, y `targetPercentageRM`, redondeado a 0,5 puntos y como mucho 100. Ninguno baja nunca del valor de origen.
  - No se tocan `plannedTargetV2` (es una meta de repeticiones, tiempo o distancia), las series de asistido, lastre o peso corporal, ni las descargas.
  - El nombre de la semana lo dice: «(repetida, +5 %)».
- La Columna del resultado está acotada por la mezcla de Inicio (`50 + (articular + guardianes)/4`), igual que en la app. Decisión del usuario (2026-10-11): se queda así, para que el alta e Inicio coincidan.
- Decisiones de contenido cerradas por el usuario el 2026-10-11:
  - Se quedan como estaban: los nombres que se encogen a 320 dp con letra al 130 %, la tabla de 30 nombres, el énfasis de armwrestling, la regla del 50–70 % de calistenia, «Importa tu programa», «Quiero ajustar mis músculos a mano», la revisión E5 sin la lista de la 1.ª semana y los pasos que F2 quitó del recorrido.
  - Cambia un nombre: «Camino al pico» pasa a «Camino a la gloria» (G1). «Pico» como nombre técnico de fase no se tocó.
- El detalle del anillo en Inicio es una hoja modal aparte: el recorrido no puede resaltarla.
- Marcas olímpicas (decisión del usuario, 2026-10-10: se acepta).
  - Qué pasa: con marcas, la serie de trabajo lleva `%1RM` y `ApproachPlanner.heaviestPercent()` la clasifica como muy pesada. Eso añade un *single* a la rampa, y el tiempo extra puede quitar el último elemento de una sesión. Hoy, en Halterofilia avanzada, es la caminata final de la 5.ª sesión.
  - Cómo lo vigila `OlympicMarksTest` (Z4): por sesión, como mucho falta el último elemento, y nunca un levantamiento con marca ni el primero.
- El interruptor de Pro de depuración (F3c) también lo ve la sincronización (`RoomSyncRepository`, `PersonalRecordCodec`). En la build de depuración no importa: usa los emuladores de Firebase y la grabación se hace sin iniciar sesión.
- Regresión amplia (2026-10-09): una clase deja vivo `ProgramRepository` con su `loadFromDb` esperando en un `StandardTestDispatcher`, y la siguiente que llama a `closeInstance()` se cuelga en `cancelAndJoin`. Ocurrió en `SetupWizardEntrenoPlanTest.setUp`.
  - No se encontró qué clase lo deja vivo: las que lo inician a mano lo cierran.
  - Si se repite, saca un `jstack` del ejecutor de pruebas, mata solo ese proceso y corre las clases restantes en una segunda pasada.
- Pruebas en el teléfono (2026-10-10, `.dbg` con el arnés: Powerlifting, Fuerza y masa y Calistenia, activación con Pro): todo correcto. Pulido opcional:
  - En la revisión, «Ver cómo funciona» y «Confirmo la activación» quedan bajo la píldora en reposo; se alcanzan al desplazar.
  - En «¿Compites?», los dos «Sí» usan el mismo dibujo.
  - La ✕ del detalle del programa tapa el «Cambiar» de la primera fila al desplazar.
- Inicio, el recorrido guiado y los vídeos no se pueden ver con el teléfono bloqueado: `MainActivity` no pide `showWhenLocked`.
- Visto al grabar los vídeos (2026-10-11). Arreglado en G1, por decisión del usuario:
  - La hoja previa al entreno se titula, para todo el mundo, «¿Cómo llegas a entrenar hoy?». Antes era «¿Qué tan preparado(a) te sientes?».
  - En su análisis: «y la recuperación de pantorrillas está al 99 %» y «La recuperación de X está al N %». Antes decía «tus pantorrillas vienen frescos» y fallaba en género y en número.
  - En el editor del plan de nutrición, todas las filas de chips usan `FlowRow` y ninguna palabra se parte. Antes «Superávit» y «Muy activo» se partían y «Profesional» quedaba fuera de la pantalla.
  - La cabecera de Inicio baja de 16 a 12 sp para que quepa el nombre entero. Si no cabe, usa «¡Hola, nombre!» y solo al final corta con «…».
  - Fichas de 6 semanas: «para recuperarte» en lugar de «para llegar fresco».
  - Tres «cuando estés listo» quedan neutros: el resumen del chat, la voz del entreno y el recorrido.
  - Sin probar en el teléfono: la cabecera y los chips solo se comprobaron con pruebas JVM y Robolectric.
- Visto en la prueba de la PR8 (2026-10-12). Arreglado en H5c y H5d, y comprobado en el teléfono:
  - **El reloj de descanso se partía en dos líneas** («02:5» / «7»): 46 sp con Syne en un anillo de 204 dp. Ahora `RestClockText` (en
    `WorkoutRestOverlay.kt`) lo pinta siempre en una línea y encoge la letra hasta 20 sp si no cabe; lo usan los dos anillos del
    descanso.
    - El vídeo de bienvenida de Entreno lo mostraba: se volvió a grabar con la misma toma y los mismos cortes; la imagen fija es del
      mismo instante.
    - Ojo en las pruebas: el `TextLayoutResult` que da la semántica de un `BasicText` lleva el estilo original, no el tamaño que
      elige `autoSize`. Para saber el tamaño real, mide el ancho de la línea.
  - **Tildes del entreno:** «Sesión principal», «Sesión activa», «Después de…», «Última serie», «transición» y «estación».
  - **El teclado no se cerraba al confirmar un paso del asistente** y tapaba el paso siguiente. Ahora `WizardDock` quita el foco y cierra
    el teclado antes de confirmar. En el paso 1, «Siguiente» del alias pasa a la fecha, y «Listo» de la fecha cierra el teclado sin
    confirmar el paso.
  - Pulido opcional, sin hacer:
    - con el teclado abierto, el check flota sobre el campo de fecha, y el tirador del cursor puede quedar encima del botón;
    - en el descanso, el texto de arriba se superpone al de la pantalla de debajo;
    - quedan «Terminar hasta acá» y «Aprovecha el timer».
- Sigue igual: la hoja del anillo en Inicio no tiene deslizador. El ajuste manual solo está en la hoja previa al entreno y al terminarlo.
- La persona `qa` del arnés no trae plan de nutrición. Para el vídeo se creó a mano en la `.dbg`.
- `src/debug/.../EntrenoPlanPreviewActivity.kt:185` (solo depuración) aún dice «para que llegues fresco».
- Fallos que no son de la ola:
  - `ProgramSnapshotStoreTest` ×2 y `ProgramDetailViewModelTest` ×2 (copias de seguridad del programa): venían de `master` 11aff1778. Ya pasan con `master` 6899fe9cb (comprobado el 2026-10-10).
  - `ExerciseDatabaseLoadTest` y `CanonicalJsonTest`: necesitan `catalog/` y `cloud-sync/`, que el checkout parcial no trae.
