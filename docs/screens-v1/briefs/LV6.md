# LV6 · El descanso con los RINGS (pantalla completa y TARJETA), minijuego, excedido y la última serie

Rama `screens/lv6`, worktree `C:\kw\u10` (ya compilado una vez), desde `feat/sessions-v1` `975431878`. Lee antes:
- `LV_COMUN.md`, `LV_COMUN2.md`;
- el mapa `docs/screens-v1/maps/MAPA_descanso_molestias_rir.md` (dónde está cada pieza y qué no se puede romper);
- los prototipos aprobados de `docs/screens-v1/proposals/sesion-en-vivo/`: `F1-DescansoCompleto`, `F3-DescansoTarjeta`,
  `F5-DescansoMinimizado`, `F6-DescansoExcedido`, `F8-MiniJuego` (fase A) y `F2-DescansoCompletoUltima`,
  `F4-DescansoTarjetaUltima`, `F7-Molestia` (fase B).

**Ley del usuario (16-oct, repetida):** «recuerda sobre el descanso, debe homologarse también a la TARJETA de descanso». Todo lo
que cambia en la pantalla completa cambia igual, adaptado, en la tarjeta (`RestLiveCard`): las formas de RINGS, el personaje, el
minijuego, el excedido y, en la fase B, la última serie. La tarjeta NUNCA cambia de tamaño (ley 1): su contenido se adapta a su
altura fija.

## Fase A · El descanso (F1, F3, F5, F6, F8)

### A1. El reloj son los RINGS (sin círculo)
- Sustituye el anillo Canvas de 204 dp (`testTag("lr1g-aro")`, en `NormalRestContent` y encogido en `FeedbackContent`) por un
  composable nuevo `RestRingsClock` (archivo nuevo, p. ej. `components/RestRingsClock.kt`) que se llena con el progreso del
  descanso `p = (total − restante) / total`.
- **Tres formas** (F1, mira su `<script>`: geometría exacta de cada una):
  - **Torre**: las tres elipses de los RINGS (abajo Columna, medio Músculos, arriba Energía) con el estilo luminoso de
    `LV_COMUN2.md`; se llenan de abajo arriba, un tercio del descanso cada una (`f(i) = clamp(3p − i)`).
  - **Pista**: un estadio (dos rectas y dos semicírculos) que se llena con un trazo luminoso.
  - **Espiral**: una espiral de seis semivueltas que se llena desde el centro.
- **Un personaje corre** sobre la forma: en la Torre, sobre el anillo que se está llenando; en Pista y Espiral, por el trazo. Figura
  de trazo fino con piernas que alternan (0,34 s), como F1.
- **La forma cambia en cada descanso**: rota Torre → Pista → Espiral entre descansos de la sesión. Unas pestañas «Torre · Pista ·
  Espiral» (segmentado pequeño, como F1) permiten elegir; lo elegido vale para el resto de la sesión (estado de UI con
  `rememberSaveable` a nivel de pantalla, sin persistir en el modelo).
- El tiempo grande va dentro o bajo la forma (`RestClockText`), con «de 4:09 · sugerido» cuando aplique. TalkBack: «Descanso:
  quedan N minutos y M segundos» (el mismo texto del prototipo).
- Fuente del tiempo: SIEMPRE `restante` del controlador (`RestTimerController.remaining`); la animación interpola entre ticks para
  que el llenado sea continuo, pero nunca cuenta por su cuenta.
- Con «reducir movimiento»: forma llena estática según `p`, sin personaje corriendo (de pie).

### A2. La pantalla completa (F1) sin perder nada de la app
Disposición de F1: título del ejercicio, «Serie 1 de 4 hecha», la línea del relator con brillo (ya existe), las pestañas de forma,
la forma con el tiempo, −15 s / «Saltar descanso» / +15 s y «Jugar mientras descansas».
**Se conserva todo lo de hoy** (ley 5), integrado con el aspecto de F1, sin botones gigantes:
- chevron «Minimizar»;
- «Ajustar tiempo» (junto a −15/+15, como píldora pequeña o pulsando el tiempo);
- `RestLastSetModule` (la última serie) y el módulo «PLAN DE SESIÓN» / «DESCANSO DINÁMICO» con «Descanso sugerido»
  (`onUseAdaptive`);
- «Saltar series restantes…» (`onSkipExercise`, solo cuando hoy aparece).
Todas las acciones siguen pasando por las mismas funciones del ViewModel (`addRestTime`, `stopRestTimer`,
`resolvePendingRestSuggestion`…): paridad con la tarjeta y la notificación (`RestLiveActionParity`).

### A3. La tarjeta (F3) y el minimizado (F5)
- `RestLiveCard` adopta F3: la forma de RINGS pequeña con su personaje (la misma forma que la pantalla completa), el tiempo
  «2:41 de 4:09», «Jugar», «Siguiente: Serie 2 · 95 kg × 4–6 · RIR 2» (de los datos reales de la siguiente serie), −15 s / Saltar /
  +15 s y expandir. Sin la barra lineal. Altura fija de la página del carrusel.
- F5: cuando la persona desliza a la tarjeta de la serie siguiente con el descanso en marcha, arriba se ve el chip compacto
  «Descanso 2:41 · +15 s» (es la píldora del roadmap `TimelineElement.RestPill` o una equivalente sobre la tarjeta: usa la que ya
  existe y dale el aspecto de F5). Tocarlo vuelve a la página del descanso.

### A4. El minijuego (F8)
- «Jugar mientras descansas» (pantalla completa) y «Jugar» (tarjeta) abren el minijuego DENTRO de la misma superficie (en la
  tarjeta, dentro de su altura): el corredor salta mancuernas que llegan desde la derecha; tocar = saltar; chocar reinicia la
  racha; «Saltos N · récord M»; arriba «El descanso sigue corriendo» con el tiempo restante; abajo «Después: Serie 2 · …» y
  «Volver al descanso».
- Juego en `Canvas` con `withFrameNanos`, a 60 fps, sin librerías; velocidad que sube poco a poco; física simple y justa (hitbox
  algo más pequeña que el dibujo).
- **El descanso manda:** al llegar a 0 el juego se cierra solo y ocurre exactamente lo de hoy (`handleNaturalFinish`: avanzar,
  feedback o cierre). Saltar descanso desde el juego también funciona. El juego no toca el estado del descanso.
- Récord: un entero local en `data/` (p. ej. `data/workout/RestGameRecordStore.kt` con SharedPreferences); nunca en Room ni en el
  log del entreno.
- Pausa automática si la app va a segundo plano; con «reducir movimiento», el juego sigue disponible pero sin efectos de
  partículas.

### A5. Descanso excedido (F6)
Hoy, al llegar a 0, el descanso se cierra y la sesión avanza sola (`handleNaturalFinish`), suena la alerta y se para el servicio.
Eso NO cambia. F6 se implementa como capa de interfaz encima:
- Si el descanso llegó a 0 de forma natural y lo siguiente es una serie normal (no feedback, no cierre), la superficie del descanso
  (pantalla completa o tarjeta, la que estuviera) pasa a «Descanso cumplido» con «TIEMPO EXTRA +0:24 sobre 4:09» contando hacia
  arriba desde el `endsAtMs` guardado, «Siguiente: Serie 2 · 95 kg × 4–6 · RIR 2» y «Empezar la serie 2» (cierra la capa y deja la
  tarjeta de la serie, que ya es la actual).
- «Necesito +30 s»: arranca un descanso nuevo de 30 s con el mismo `startRestTimer` (mismo ejercicio y tipo) solo si esa vía
  existe limpia en el ViewModel; si no, no lo añadas y anótalo.
- La capa no reprograma alarmas ni toca el servicio. Desaparece al registrar o tocar la serie, al cambiar de ejercicio o a los
  5 min.
- Estado de UI (no persistido): si el proceso muere, no vuelve.

## Fase B · La última serie (F2, F4, F7)
**Empieza la fase B solo cuando el orquestador te avise** de que está integrado el motor de molestias (paquete LV7a:
`domain/discomfort/DiscomfortCauseEngine` y `data/models/DiscomfortKnowledge`). Mientras, termina y comitea la fase A.

### B1. El panel (F2) dentro del descanso
`PostExerciseFeedbackPanel` (`WorkoutPostExerciseFeedbackHost.kt`) adopta F2:
- «Sentadilla trasera terminada · Descanso 2:38 · después, …» y la forma de RINGS pequeña (la misma de la fase A).
- **«¿Cómo te salió?» · CALIDAD TÉCNICA**: slider interactivo 1–10 por ejercicio (como hoy, por defecto 8) con color que va de
  rojo `#EF6F64` a ámbar `#F7CF73` y verde `#43D18C`, la palabra («limpia», «aceptable», «a revisar»…) y una figura que cambia de
  postura: tambalea con nota baja y destella con nota ≥ 8 (F2).
- **Sin «Qué tan intenso fue»**: se quita la pregunta (decisión del 16-oct; el RIR/RPE pasa a ser obligatorio en cada serie,
  paquete LV8). El modelo conserva `perceivedIntensityRpe`/`perceivedFailure` (lo escriben la voz y los logs viejos): no los
  borres; simplemente esta pantalla ya no los pide. `backfillCompletedSetIntensityFromPostExerciseFeedback` sigue funcionando con
  null.
- **¿ALGUNA MOLESTIA?**: buscador amable sobre `DISCOMFORT_CATALOG` (sinónimos y sin tildes: «rodila», «lumbar», «espalda baja»)
  y chips destacados: primero las reportadas antes (con «la reportaste el lunes», de `previousSessionDiscomforts` y las no
  resueltas) y luego «Frecuentes en <ejercicio>» (las más probables según `DiscomfortCauseEngine.rankLikely(...)` del paquete
  LV7a). Mantén las «Molestias previas relacionadas» con Sigue/Resuelta.
- **El aviso del motor**: una línea como F2 («Esta semana llevas un 35 % más de series de cuádriceps que tu media. Si la rodilla
  avisa, empieza por ahí.») que sale de `DiscomfortCauseEngine` (solo si hay evidencia real; si no, no se muestra).
- Tocar un chip de molestia abre la **ficha de la molestia (F7)**.
- «Saltar descanso» y «Guardar» (= el «Registrar feedback» de hoy, misma función).

### B2. La ficha de una molestia (F7)
Overlay propio: zona («RODILLA»), título, animación curada de la zona (la de la rodilla de F7 como modelo; una por sección del
catálogo: hombro y brazos, columna y cuello, cadera, rodilla, tobillo y pie, general), explicación y «CAUSAS COMUNES» (de
`DiscomfortKnowledge`), «EN TU CASO» con «Hoy» y «Esta semana» (de `DiscomfortCauseEngine`, solo líneas con evidencia), «QUÉ
PUEDES HACER» (siempre con «Si el dolor es fuerte o no mejora en una semana, consulta a un profesional») y «Reportar molestia»
(añade la molestia al feedback del ejercicio, igual que el chip). **Sin selector de gravedad**: la app no la tiene (ley 5).

### B3. La tarjeta tras la última serie (F4)
Hoy el feedback fuerza la pantalla completa (`forceShowForFeedback`). Con F4, si la persona minimiza, la tarjeta del descanso
muestra el mismo feedback compacto (técnica, molestias con chips, aviso del motor, Saltar/Guardar) dentro de su altura fija, y
«Guardar» guarda lo mismo que la pantalla completa. Al expandir se ve el panel completo con lo ya elegido (el estado vive en un
solo sitio).

## Lo que NO se puede romper (del mapa)
Una sola fuente del restante; `addTime` para ±15; `handleNaturalFinish` y su encadenado; alarmas, servicio en primer plano,
notificación y TTS; paridad overlay/tarjeta/notificación; `isRestMinimized = false` en cada inicio; la página REST no mueve el
cursor; `WorkoutRestModalState` serializable (campos nuevos con valor por defecto); restauración con `endsAtMs > now`; el orden
`nextSet` → `startRestTimer`; `technicalQuality` 1–10, `"none"`, ids del catálogo, `stillPresentDiscomfortIds`.
`WorkoutRestModalUiTest` (instrumentada) busca textos: si cambias alguno, actualízala aunque no la ejecutes.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.workout.*`, `com.example.kpkn.services.workout.*` y `com.example.kpkn.domain.*` que
  toques.
- Nuevas: el llenado de cada forma con `p` (puro), la rotación de forma por descanso, que ±15 y Saltar desde la pantalla completa,
  la tarjeta y el minijuego llaman a lo mismo, que el juego se cierra al llegar a 0 y se encadena lo de hoy, el excedido (cuenta
  desde `endsAtMs`, «Empezar» cierra sin tocar alarmas), y en la fase B: técnica guardada, sin pregunta de intensidad, chips
  destacados, Guardar desde la tarjeta = desde la pantalla completa.
- Hoja de render nueva `render/LiveRestRenderSheetTest` a 360 dp, 100 % y 130 %: las tres formas (dos momentos de llenado), la
  tarjeta, el minimizado, el minijuego (dos fotogramas), el excedido; fase B: la última serie en pantalla completa y en tarjeta
  con técnica baja y alta, y la ficha de la rodilla.

## Permitido y prohibido
- **Permitido:** `WorkoutRestOverlay.kt`, `RestLiveCard.kt`, `WorkoutOverlayHost.kt`, `WorkoutPostExerciseFeedbackHost.kt`,
  archivos nuevos del descanso, en `WorkoutV2Body.kt` solo la rama `LivePageType.REST` y la píldora del descanso, en
  `WorkoutSetPager.kt` solo `RestPill`, en `WorkoutViewModel`/`WorkoutRestTimerOrchestrator` solo lo mínimo para el excedido y
  «+30 s», `data/workout/RestGameRecordStore.kt`, pruebas.
- **Prohibido:** la tarjeta de la serie (`SetExecutionCard.kt`, `SetCard*Back.kt`: paquete LV8), el relator (LV2), la cabecera y el
  estado previo, la barra inferior y el cockpit, la hoja de cierre, `KpknTowerRings.kt` (paquete de anillos: dibuja tus formas en
  tu propio archivo con el estilo luminoso de `LV_COMUN2.md`), los servicios de `services/workout` (salvo que una prueba demuestre
  un fallo).

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lv6 bash /c/kw/tools/gradle_slot.sh /c/kw/u10/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.services.workout.*" --tests "*LiveRestRenderSheet*" > /c/kw/logs/lv6-<n>.log 2>&1`

Tope: 5 corridas en la fase A y 4 en la fase B. Mira tus PNG antes de cada corrida nueva.

## Commits
- `feat(workout): el descanso se cuenta con los RINGS en tres formas y un corredor, también en la tarjeta (LV6)`.
- `feat(workout): minijuego del descanso en la pantalla completa y en la tarjeta (LV6)`.
- `feat(workout): descanso cumplido con tiempo extra sin tocar las alarmas (LV6)`.
- Fase B: `feat(workout): la última serie con calidad técnica, molestias destacadas y el aviso del motor, también en la tarjeta (LV6)`
  y `feat(workout): ficha de cada molestia con sus causas y lo que ve el motor en tu caso (LV6)`.
