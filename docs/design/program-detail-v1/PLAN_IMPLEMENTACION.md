# Plan de implementación · Programas + Program Detail v1 (aprobado el 27-oct-2026)

Fuente de verdad visual y de producto: el lienzo v6 (`prototipos.html`, fuentes en `src/`). Cada paquete cita las escenas que
implementa. Lo que no esté en el lienzo se decide con el criterio del lienzo y se anota en «Decisiones de implementación».

Rama: `claude/program-detail-progress-redesign-b0a1e5` (worktree `.claude/worktrees/program-detail-progress-redesign-b0a1e5`).
Sin push. Commit por paquete con PASS.

## Reglas

- Escriben subagentes Haiku 5.5 MAX con encargo cerrado (skill `kpkn-haiku-brief`); el orquestador revisa diffs, verifica y
  commitea. Sonnet solo si Haiku falla dos veces por juicio.
- Gradle SOLO por la cola: motor → `bash /c/kw/tools/kpkn_jvm.sh <worktree> "-PtestInclude=<glob>" --tests <FQN>`; pantallas →
  `bash /c/kw/tools/gradle_slot.sh <worktree>/android-native :app:compileBaseDebugKotlin` o
  `:app:testBaseDebugUnitTest --tests <FQN>`. Nunca `./gradlew` directo. Solo flavor `base`.
- Room CONGELADO: nada nuevo en la base de datos. Lo persistente nuevo (resumen del mes visto) va en SharedPreferences.
- `domain/` sin `android.*`. StateFlow expuesto con `asStateFlow()`. Textos en español.
- Refinar, no quitar: toda función actual sigue existiendo (en su nuevo sitio). Lo que sale está aprobado en la pestaña Decisiones.
- Las historias del programa (Semana, Bloques, Progreso) se pintan ASÍ TAL CUAL (ver `historias/01-03`).

## Paquetes (en orden; los marcados ∥ pueden ir en paralelo)

| # | Paquete | Escenas del lienzo | Depende de |
|---|---|---|---|
| P1 ∥ | Portadas de líneas: motor Compose `LineCoverArt` (8 estilos + `SESION`, 5 fondos), esquema `kpkn-lines://ESTILO/FONDO`, valor por defecto (Perfil con bloques, Ondas en rutinas), dibujo animado | Portadas › galería | — |
| D1 ∥ | Motores de progreso (dominio puro + pruebas JVM): estado de fuerza, trabajo por semana, RIR frente al plan, informe mensual | Progreso, Resumen del mes | — |
| P2 | Barra flotante de vidrio (atrás · pestañas · ⋯), portada nueva, campana + hoja «Pendiente», botón compartir, pestañas Semana · Progreso · Estructura | Portada (todas) | P1 |
| P3 | Semana en agenda: navegador, tres cifras, días con varias sesiones, sesión desplegada, hoja de semanas, menús de semana/día/sesión unificados, «rutina» | Semana, Menús (semana, día, sesión, fecha, empezar fuera de fecha) | P2 |
| P4 | Progreso (sustituye a Volumen): resumen, fuerza, trabajo, volumen hecho/plan, cumplimiento, esfuerzo, molestias, decisiones; hoja de ejercicio; hoja «Rangos»; sale el cuestionario «Calibrar» y su aviso; modalidad a «Editar programa» | Progreso, Menús › aviso calibrar | D1, P2 |
| P5 | Estructura: mapa, bloques, «Del programa» (TM, plantillas, versiones), revisión del plan, hoja «Fechas», ciclos de rutina | Estructura | P2 |
| P6 ∥ | Programas: lista nueva, ⋯ de programa, hoja «Nuevo», catálogo como página, «Desde cero» con nombre, archivados, vacío, arreglos | Programas | P1 |
| P7 | Historias: renderizador Canvas 1080×1920 para programa (3), entreno (3, sustituye al actual conservando opciones) y mes (4); hojas de compartir | Compartir | P1, D1 |
| P8 | Resumen del mes: aviso en Inicio, pantalla completa animada (10 pantallas), Progreso › Resúmenes mensuales, visto en SharedPreferences | Resumen del mes | D1, P1, P7 |
| P9 | Hojas restantes con piel KPKN: check-in, test 1RM, revisión de bloque, continuación, competición, cambiar ejercicio, decisiones, editar programa, sobre el programa + lista de fallos de Decisiones | Menús | P2 |

## Decisiones de implementación

(se rellena mientras avanza; cada una con su razón)

- **Portadas de líneas detrás de `CoverStudioArt`.** Esquema nuevo `kpkn-lines://ESTILO/FONDO`; `CoverIdentity.lines` decide el
  motor. Así no cambia ninguna llamada (lista, Inicio, detalle, editor). Las portadas `kpkn-studio://` ya guardadas se siguen
  pintando como antes; sin portada (o con `gradient://`) se usa la de líneas por defecto: Perfil con varias semanas, Ondas en rutinas.
- **Pestañas sin reordenar el enum.** `StructureSubTab` se queda (SEMANA, MACROCICLO, VOLUMEN); la barra las muestra como
  Semana · Progreso (VOLUMEN) · Estructura (MACROCICLO). Evita migrar estado guardado.
- **Coordinación con la sesión en vivo (27-oct).** Esa sesión trabaja en master (screens/workout/**). De su zona solo toco
  `WorkoutStoryRenderer.kt` y `WorkoutStorySheet.kt` en P7, después de fusionar su master, y conservo en esa KpknSheet
  `backdropScrim = LiveSheetTokens.Veil, additionalGlassScrim = LiveSheetTokens.Panel`. La entrada a «Historia» vive ahora en su
  menú «Tu sesión» (tag `workout-story-header`). No hay APK común: cada sesión entrega el suyo.
- **D1 dividido.** D1a (tendencia de fuerza y trabajo semanal) y D1b (informe mensual, que reutiliza D1a).
- **Arnés JVM (fuera del repo).** `WorkoutLog` guarda `WorkoutVariantProgress` (de screens/workout) desde el 27-oct; añadí
  `class WorkoutVariantProgress()` vacía a `C:\kw\kpkn-jvm\stubs\...\screens\workout\HarnessWorkoutStubs.kt` (copia previa en el
  scratchpad). Sin eso no compilaba ninguna corrida JVM de worktrees con el master actual.
- **Heap de 8 GB en las corridas Android de este worktree.** Con los 4 GB por defecto de la ranura,
  `:app:compileBaseDebugKotlin` (compilación en proceso) se queda sin memoria. Las corridas pasan
  `"-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"` a `gradle_slot.sh`.
- **Contrato de portada por defecto.** `ProgramCoverStudioTest.aMissingOrInvalidStudioValueGivesTheIdentityOfToday` esperaba la
  identidad vieja con null y `gradient://`; se ajusta en P1b al contrato nuevo (identidad con `lines`).
- **«Pendiente» reutiliza los avisos de hoy.** Cada fila abre una hoja con el MISMO composable y callbacks que antes
  (ProgramScheduleModule, ProgramTransitionModule, AutoregulationProposalsCard). La piel nueva de esas hojas llega en P9; así no se
  pierde lógica en el traslado.
- **Una sola paleta de fases.** La cinta usa `ProgramPhase.color` de la app y no los colores del prototipo, para que la portada,
  Estructura y el resto coincidan.
- **Icono de compartir local.** `KpknLineIcons` no tiene «compartir» y ese archivo lo edita la sesión en vivo; el icono vive en
  `programdetail/design/ProgramShareIcon.kt`.
- **Sin entrada hasta su paquete (temporal, a cerrar).** Tras P2b, «Carga planificada» (ProgramPlannedLoadSheet) y el resumen de
  progreso no tienen entrada: van a Progreso en P4. La temporada sale (aprobado). El editor de TM se abre desde la tarjeta de
  propuestas y desde el panel de TM de la Semana; en P5 pasa a Estructura › «Cargas de referencia».
- **Etiqueta de rutina también en el editor.** `programProgressLabel` lo usa `MacrocycleEditorLegacy` (repeatingWeekLabel): ahora
  dice «Semana N», coherente con «rutina».
- **Ejercicios sin series con carga.** `LiftTrendEngine` los devuelve con 0 sesiones; la pantalla filtra `sessions > 0`.
- **P3 en tres.** P3a (agenda en DayView/ProgramDayModule, menús de día y de sesión), P3b (cabecera y hojas de semana en
  ProgramDetailScreen, tras P2b) y P3c (diálogos de fecha y de «fuera de fecha»). P3a no toca los archivos de P2b ni de P6a.
- **Sesión desplegada en su sitio, estado local.** La agenda despliega sesiones, no días: el estado vive en DayView
  (`rememberSaveable` por semana). Los parámetros `expandedDayIds` de DayView y su guardado en el ViewModel quedan sin uso hasta
  limpiarlos en P3b.
- **Puntos de foco.** Salen del músculo primario del catálogo (`VolumeCalculator.normalizeCanonicalMuscleGroup`): empuje, tirón,
  pierna (incluye erectores), core y cardio; máximo tres por sesión, repartidos por frecuencia.
- **Menús de día y de sesión anclados (vidrio).** Se reutiliza `ProgramItemMenu` en vez de la hoja con subtítulos del lienzo: mismo
  contenido y orden; la piel de hoja, si hace falta, llega en P9.
- **Historias con DrawScope de Compose.** `screens/stories/`: lienzo 1080 × 1920 pintado con `CanvasDrawScope` y `TextMeasurer`
  a densidad 1 (1 sp = 1 px), que reutiliza `drawLineCover`. La misma función pinta la vista previa escalada de la hoja. Compartir y
  guardar reutilizan `WorkoutShareService.shareBitmap` / `saveImage` (solo lectura de la zona de la sesión en vivo).
- **Lista de programas (P6a).** «Terminado · d MMM» usa el último entreno registrado del programa (el programa no guarda fecha de
  cierre). La cola se reordena con flechas (no hay arrastre en el código). «Compartir en historias» del ⋯ llega con P7b.
- **Catálogo como capa, no como ruta.** La página «Catálogo» se pinta a pantalla completa sobre Programas (portal + atrás del
  sistema), sin tocar Navigation.kt. La hoja vieja `CreateProgramTemplateSheet` se queda para Inicio, que la sigue usando.
- **Máximos: cerrar no crea.** En el paso de TM desde Programas, deslizar para cerrar ya no crea el programa; «Crear sin TM» sí.
- **Pendiente tras P3a.** `PostSetupCalendarUiTest.expandDay` (androidTest, no se ejecuta aquí) toca la cabecera del día para
  desplegarlo y eso ya no despliega: adaptar en P3b. La cápsula de propuesta («+2,5 kg») de la tarjeta de sesión aún no se
  alimenta: se conecta en P3b desde las propuestas de autorregulación. Un tramo de descansos que incluye hoy queda plegado (se ve
  «HOY» en su fecha solo si es el primer día del tramo): revisar en P3b.
- **Historias del mes, detalles a cerrar en P8.** Con menos de 60 min la meta de Constancia diría «0 h 45 min»: usar «45 min». La racha de 1 dice «1 sesión» (bien).
- **P2 dividido.** P2a (piezas sin cablear: barra flotante, cinta, campana, hoja Pendiente, cabecera) y P2b (cableado en
  ProgramDetailScreen, que es un archivo de 1.373 líneas).

## Estado

| Paquete | Estado | Commit |
|---|---|---|
| P1a modelo de portadas | hecho · 34 pruebas del paquete cover | 92310c9ac |
| P1b dibujo de portadas | hecho y verificado visualmente (render-android/lineas_contacto.png) | 92310c9ac, ec38cc20f |
| D1a fuerza y trabajo | hecho · 19 pruebas JVM | 0f64af9aa |
| D1b informe mensual | hecho · 33 pruebas JVM en el paquete | c51c2ce6c |
| P2a piezas de cabecera | hecho · 141 pruebas de programdetail.design | 0bbcd74be |
| P2b cableado | hecho · 369 pruebas de programdetail | 8af533b98 |
| P6a lista de programas | hecho · 232 pruebas de screens.programs | ecabe59cb |
| P6b Nuevo, catálogo, desde cero | hecho · 239 pruebas de screens.programs | dec33a576 |
| P3a agenda de días y menús de día y sesión | hecho · 369 pruebas de programdetail | 4834cb63c |
| P3b cabecera de semana, hoja de semanas, ⋯ de semana, vacía, rutina | hecho · 390 pruebas de programdetail | bfa6d9f22 |
| P3c fecha con calendario y aviso «fuera de fecha» con título del caso | hecho · 18 pruebas; androidTest sin compilar por fallos previos de master (RINGS_*) | b8d6d6a8b |
| P7a dibujo de las historias del programa (1080 × 1920) | hecho · 6 pruebas; PNG en render-android/historia_*.png, ±3 px del lienzo | d13f09c6f |
| P7b hoja de compartir del programa y datos desde el programa | hecho · 8 pruebas; conectada en portada, ⋯ y lista (351 pruebas) | be6a2ea83, 6659a370f |
| P7c historias del mes (Resumen, Constancia, Fuerza, Músculos) | hecho · 10 pruebas; PNG en render-android/mes_*.png | 65a36828a |
| P7d historias del entreno (Sesión, Ejercicio, Fotos) | pendiente de fusionar master (zona de la sesión en vivo) | |
| P4a modelo y piezas de Progreso (resumen, fuerza, trabajo por semana) | hecho · 20 pruebas; sin cablear | 6919ac012 |
| P4b cablear resumen, fuerza y trabajo en la pestaña Progreso (con alcance) | hecho · 413 pruebas de programdetail | 94d0ea039 |
| P4c volumen por músculo, Rangos y salida de «Calibrar» | pendiente de P4b | |
