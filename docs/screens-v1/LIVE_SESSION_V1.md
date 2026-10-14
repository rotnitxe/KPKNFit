# Sesión en vivo v1 · rediseño con el lenguaje KPKN v2 (2026-10-13)

> **2026-10-14: el usuario rechazó el rediseño y pidió volver a la sesión de la PR11.** Esta especificación queda archivada con el
> código en la etiqueta `archive/live-session-redesign-v1`. El refinado del aspecto de la original irá en un documento aparte.

## Encargo y fuentes
- **Encargo y listón:**
  - «Más adelante: rediseño del editor de sesiones y sesión en vivo».
  - «Nivel top mundial, que supere en diseño a Lyfta, MacroFactor Workouts, Hevy». Las decisiones las tomo yo.
- **Lenguaje común:** `docs/design/KPKN_UI_V2.md`.
- **Mapa del código actual:** `C:\kw\analysis\maps\workout.md`.
- **Editor de sesiones:** comparte piezas con esta pantalla. Está en `docs/screens-v1/SESSION_EDITOR_V1.md`.

Es LA pantalla de una app de entrenamiento: se usa con el pulso alto, entre series, con una mano y a veces con la vista cansada.

## 0. Por qué así, frente a la competencia
- **Hevy y Lyfta:**
  - Muestran la sesión entera como una lista de tablas, y cada serie se marca con un check.
  - Ventaja: lo ves todo.
  - Problemas: hay que hacer scroll, los números son pequeños y la serie de ahora no destaca.
- **MacroFactor Workouts:**
  - Un ejercicio a la vez, entradas grandes y progresión automática.
  - Es el mejor en foco, pero sobrio.
- **KPKN hará:**
  - **Foco:** la serie de ahora en grande, con kg y repeticiones en Syne de 40 sp.
  - **Registro en un toque:** «Registrar serie» de 56 dp.
  - **Descanso:** una pantalla con un anillo que se vacía y la serie siguiente a la vista.
  - **Contexto en una tira:** la tira de series y la barra de progreso de la sesión.
  - **Lo que nadie tiene:**
    - los RINGS que se gastan al registrar;
    - el relator;
    - la voz sin conexión;
    - las superseries por rondas.
- **Hoy, en cifras:**
  - 65.000 líneas;
  - 228 `Surface`;
  - 43 `OutlinedTextField`;
  - 35 `FilterChip`;
  - 260 colores fijos;
  - 184 iconos de Material y 0 KPKN;
  - la tarjeta de la serie tiene 2.775 líneas en una sola función;
  - no respeta el movimiento reducido.

## 1. Qué falla hoy
| Zona | Problema | Gravedad |
|---|---|---|
| Foco | La cabecera acumula cronómetro, chips, protocolo con emoji, ritmo, etiquetas, voz e historial: compite con la serie | 🔴 |
| Registro | kg en un campo de texto, repeticiones en una rueda horizontal, RIR en otra rueda y chips «Llegar al fallo»/«Reservar reps» con grises fijos | 🔴 |
| Descanso | Tres formas (tarjeta en el pager, overlay y pastilla) con etiquetas en MAYÚSCULAS | 🟡 |
| Navegación | El roadmap del dock y la tira de series: dos barras que piden atención a la vez | 🟡 |
| Identidad | Fondo `#0D1117` en la carga, `WorkoutGlassSurface` y botones propios fuera del sistema; Material por todas partes | 🔴 |
| Notificaciones | Todas abren la pestaña Entreno, no la sesión | 🟡 |
| Textos | «Reps», «ERROR», «Warm-up», voseo («podés»), 16 «...» en ASCII y errores de voz en inglés | 🟡 |
| Movimiento | Ignora el movimiento reducido | 🟡 |
| Deuda | Unas 3.500 líneas muertas y piezas de prueba dentro de producción | 🟢 |

## 2. La pantalla de una serie, de arriba abajo
1. **Fila superior** (sobre el negro, sin isla):
   - a la izquierda, `KpknIconButton(ChevronDown, "Minimizar")`: vuelve a la app y deja la sesión en curso (tarjeta flotante);
   - en el centro, el cronómetro de la sesión en `metricM` tabular («24:31»), y debajo, en `caption`, el ritmo si hay límite («Vas
     4 min por delante»);
   - a la derecha, el estado de la voz (un punto con «Voz») si está activa, y `KpknIconButton(More)` con:
     - «Sesión» (cockpit);
     - «Notas»;
     - «Fotos»;
     - «Etiquetas»;
     - «Modo de voz»;
     - «Terminar sesión».
2. **Barra de progreso de la sesión:**
   - una regla segmentada a todo el ancho: un tramo por ejercicio, los hechos en tinta, el actual en `musculo` y los futuros en
     blanco al 12 %. Es la misma regla de semanas de Program Detail;
   - tocarla abre la hoja **«Ruta de la sesión»** (§4).
3. **Cabecera del ejercicio:**
   - el eyebrow «EJERCICIO 3 DE 6 · TORSO A», o «SUPERSERIE A · RONDA 2 DE 3»;
   - el nombre en `display` (2 líneas como máximo, se encoge hasta 24 sp);
   - una fila de datos en `caption` tabular: «Objetivo 4 × 6–8 · RIR 2» y «Última vez 80 kg × 8»;
   - a la derecha, `KpknIconButton(Info, "Técnica")`, que abre la técnica y el vídeo o la foto, y
     `KpknIconButton(Repeat, "Cambiar ejercicio")`;
   - el protocolo del catálogo como `KpknChip` con icono KPKN, nunca con emoji.
4. **La serie** (la tarjeta protagonista, sin caja: sobre el negro):
   - el eyebrow «SERIE 2 DE 4» o «APROXIMACIÓN»;
   - **dos cifras grandes lado a lado:** «KG» y «REPS» como eyebrow encima, el valor en `metricXL` (Syne 40) tabular y, debajo,
     `KpknIconButton(Minus)` y `KpknIconButton(Plus)`:
     - el paso es el del material (2,5 kg barra, 2 kg mancuerna) y 1 repetición;
     - tocar la cifra abre el teclado numérico;
     - mantener pulsado ± repite;
   - **la intensidad:** una fila de `KpknChip` con «RIR 0 · 1 · 2 · 3 · 4+», o «RPE 6…10» según el modo; «Fallo» como chip aparte
     en `danger`;
   - **la sugerencia de carga:** «Sugerida 82,5 kg» en `caption` con `KpknTextAction("Usar")`. No se aplica sola si la persona ya
     escribió otra;
   - en peso corporal, lastre o asistencia, el modo como `KpknSegmentedControl(Compact)` sobre la cifra de kg;
   - en tiempo, la cifra es «0:45» con ±5 s.
5. **Acción principal:** `KpknPrimaryPill("Registrar serie")` a todo el ancho (56 dp), con la descripción «Registrar serie».
   - Al tocar:
     - háptico;
     - la píldora se convierte en un check (300 ms);
     - los RINGS gastados se ven 1,6 s en la tira de la torre;
     - empieza el descanso (§3).
6. **La tira de series** (sobre la isla inferior):
   - una fila de círculos de 44 dp, uno por serie: hechas con check y «80 × 8» debajo en `caption`, la actual con anillo de tinta y
     las futuras con anillo `divider`;
   - tocar una hecha la abre para corregirla;
   - «+» al final añade una serie.
7. **Isla inferior** (vidrio v2):
   - anterior y siguiente ejercicio (ChevronLeft y ChevronRight con el nombre corto del siguiente: «Sigue: Press inclinado»);
   - en el centro, el acceso a la ruta de la sesión.
   - Sustituye al dock del roadmap y al FAB.

Se conserva el deslizamiento horizontal entre ejercicios (el pager de hoy).

## 3. Descanso
- **Al registrar, la serie da paso al descanso** con un fundido cruzado de 220 ms:
  - un anillo grande (200 dp) que se vacía en `columna` con el tiempo restante en `metricXL` («1:45»);
  - debajo, «Siguiente: Serie 3 · 80 kg × 8» en `body`;
  - `KpknSecondaryPill("−15 s")` y `KpknSecondaryPill("+15 s")`;
  - `KpknTextAction("Saltar descanso")`;
  - el descanso adaptativo, si sugiere otro tiempo: «Descanso sugerido 2:30» con «Usar».
- **El feedback del ejercicio** (molestias y esfuerzo) se pide al acabar el ejercicio, dentro del descanso, como hoy, pero con
  `KpknChip` y la barra de molestia propia (sin `Slider` ni `Card` de Material).
- **Al terminar:** háptico y un sonido opcional; vuelve la serie siguiente.
- **Minimizar:** si la persona navega (ruta, otra pantalla), el descanso queda como una píldora de vidrio arriba («1:45 · Descanso»)
  que vuelve al tocarla.
- **Notificación:** muestra el tiempo con «−15 s», «+15 s», «Saltar» y «Hecho». **Al tocarla se abre la sesión**, no la pestaña
  Entreno (cambia el `path` de las notificaciones de la sesión).
- **Una sola pieza de descanso**, que hoy son tres: `RestLiveCard`, `RestTimerOverlay` y `RestTimerPill`.
- **Textos:** «Descanso», «Siguiente ronda» y «Entre lados», en tipo frase. Las pruebas instrumentadas buscan «DESCANSO»: si el
  texto pasa a eyebrow, se pinta en mayúsculas con el estilo y la cadena se conserva; si no, se actualizan las pruebas en el mismo
  paquete.

## 4. Ruta de la sesión (hoja)
- Se abre desde la barra de progreso y desde la isla inferior; sustituye al roadmap expandido.
- **Una fila por ejercicio:**
  - el estado (check, punto `musculo` si es el actual o anillo);
  - el nombre;
  - «2 de 4 series»;
  - el arrastre para reordenar.
- **Superseries:** agrupadas con la barra vertical, como en el editor.
- **Acciones:**
  - «Añadir ejercicio»;
  - «Crear superserie»;
  - «Saltar ejercicio»;
  - el modo dios: las acciones por lote del roadmap de hoy, con piel KPKN.

## 5. Otros momentos
- **Preparación** (antes de empezar):
  - hoja con la torre de los RINGS grande y ajustable (`KpknTowerRings`);
  - las molestias previas como `KpknChip`;
  - la voz con `KpknSwitch`;
  - `KpknPrimaryPill("Empezar")`. Hoy dice «Confirmar y entrenar»: se conserva si una prueba lo busca.
- **Calentamiento y movilidad:**
  - un módulo checklist con su temporizador, como una serie: cifra grande y «Hecho»;
  - «Saltar calentamiento» como `KpknTextAction`.
- **Cardio:**
  - el tiempo en `metricXL`;
  - los intervalos como línea de tiempo (la de hoy, en colores KPKN);
  - GPS (distancia y ritmo) en `metricL`;
  - pausa y reanudar como píldoras.
- **Sin ejercicios:** `KpknEmptyState` con «Esta sesión no tiene ejercicios» y «Añadir ejercicio». Hoy no hay nada.
- **Carga:** esqueletos sobre negro puro (fuera el `#0D1117`). «Cargando la sesión…» se ve solo tras 600 ms.
- **Cockpit** («Sesión»): una hoja con `KpknSegmentedControl`: «Resumen · Fotos · Herramientas · Notas».
- **Terminar** (página de resumen):
  - la duración, las series, el volumen y los récords, en `KpknMetric`;
  - la torre de los RINGS gastándose (animada una vez);
  - las series pendientes (conserva los tags `finish_pending_series_*`);
  - las notas;
  - `KpknPrimaryPill("Guardar entrenamiento")` y `KpknSecondaryPill("Compartir")`;
  - el cálculo, con su esqueleto y «Calculando la recuperación…».
- **Diálogos:** conflicto de sesión en curso, lectura corrupta, error al iniciar y error al cancelar. Usan `KpknAlertDialog` con los
  textos de hoy.

## 6. Honestidad, accesibilidad y movimiento
- **Datos:**
  - la sugerencia de carga siempre se presenta como sugerencia;
  - sin «Última vez» si no hay historial (nunca «—» suelto: la línea no se pinta);
  - las cifras de los RINGS solo con datos del motor.
- **Accesibilidad:**
  - todo tocable mide 48 dp como mínimo; ± de 56 dp en la serie, para tocar con guantes o con las manos sudadas;
  - TalkBack lee «Serie 2 de 4, 80 kilos, 8 repeticiones, RIR 2»;
  - contraste ≥ 7:1 en las cifras de la serie.
- **Movimiento:**
  - con `wizardReducedMotion()`, sin anillos animados, sin escalas y con fundidos de 120 ms;
  - el resto, con las curvas de KPKN v2.
- **Textos:**
  - español neutro y tipo frase;
  - «Reps» pasa a «Repeticiones» en lo largo y «REPS» solo como eyebrow de la cifra;
  - «Calentamiento», no «Warm-up» (las pruebas que buscan «Warm-up» se actualizan);
  - sin voseo;
  - «…» de un carácter;
  - errores de voz en español;
  - `Locale` español explícito en los relojes.

## 7. Arquitectura (sin cambiar la lógica)
- **Lógica intacta:** `WorkoutViewModel` y sus controladores (registro, descanso, persistencia, voz, sugerencia de carga, cierre).
- **Composición:**
  - Las funciones gigantes (`SetInputCardV2`, de 2.775 líneas, y `WorkoutV2Body`, de 2.390) se parten en piezas sin estado en
    `screens/workout/design/`, una por archivo, con datos simples y lambdas.
  - Así las pinta la hoja de render.
- **Piezas de prueba en producción** (`WorkoutAndroidTestSupport.kt`): se mueven a `src/androidTest` o se retiran con sus pruebas,
  en el paquete de limpieza.

## 8. Paquetes (ola LS, subagentes Haiku 5.5)
| Paquete | Qué | Depende de |
|---|---|---|
| LS0 | Limpieza:<br>- código muerto (unas 3.500 líneas, cada borrado confirmado con Grep);<br>- duplicados `ExerciseTagSheetContent`, `WorkoutExerciseSetupContent`, `ExerciseSetupSheetContent`;<br>- `shouldRenderRestLiveCard`;<br>- textos en inglés, voseo y «...»;<br>- `Locale.getDefault` | — |
| LS1 | Piezas de la serie, sin estado: cifra con ± (`KpknStepperMetric`), fila de intensidad, sugerencia de carga y tira de series. Con su hoja de render | D2 |
| LS2 | La pantalla de una serie (§2: fila superior, barra de progreso, cabecera del ejercicio, serie, «Registrar serie», tira e isla inferior) sobre el pager de hoy | LS1 |
| LS3 | Descanso único (§3), su píldora, el feedback del ejercicio con piezas KPKN y las notificaciones que abren la sesión | LS2 |
| LS4 | Ruta de la sesión (§4) con reordenar, superseries y modo dios | LS2 |
| LS5 | Preparación, calentamiento, movilidad y cardio (§5) | LS2 |
| LS6 | Resumen al terminar, cockpit «Sesión» y diálogos (§5) | LS2 |
| LS7 | Hojas de estructura y de contexto (reemplazar ejercicio, etiquetas, ajustes de serie, historial, RM, ultra rápido, técnica y medios) con piezas KPKN | LS2 |
| LS8 | Cierre: hojas de render de toda la sesión (serie, descanso, superserie, cardio, calentamiento, resumen; 360 dp, 100 % y 130 %), QA en el EMULADOR de una sesión entera con el arnés, accesibilidad y docs | todos |

Orden con el editor:
- **En paralelo:** SE0 y LS0, porque las limpiezas no se pisan. Después, SE1 y LS1, que son piezas nuevas.
- **Antes de tocar piezas compartidas:** los paquetes que revisten piezas del editor que usa la sesión en vivo (SE5) van antes que
  LS5.
