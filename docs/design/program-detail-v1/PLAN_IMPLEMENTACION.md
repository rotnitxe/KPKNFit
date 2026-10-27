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
- **Ejercicios sin series con carga.** `LiftTrendEngine` los devuelve con 0 sesiones; la pantalla filtra `sessions > 0`.
- **P2 dividido.** P2a (piezas sin cablear: barra flotante, cinta, campana, hoja Pendiente, cabecera) y P2b (cableado en
  ProgramDetailScreen, que es un archivo de 1.373 líneas).

## Estado

| Paquete | Estado | Commit |
|---|---|---|
| P1a modelo de portadas | en curso | |
| P1b dibujo de portadas | pendiente de P1a | |
| D1a fuerza y trabajo | hecho · 19 pruebas JVM | 0f64af9aa |
| D1b informe mensual | hecho · 33 pruebas JVM en el paquete | c51c2ce6c |
| P2a piezas de cabecera | en curso | |
| P2b cableado | pendiente de P2a | |
