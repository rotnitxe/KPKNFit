# Fase 0.3 · Cimientos de movimiento en Compose (`ui/design/motion`)

Lee antes, en este orden: `briefs/impl-COMUN-v2.md` (reglas firmes: **Gradle SOLO por la cola**, sin commit, informe), `PROTOCOLO_1A1.md`, `DECISIONES_APROBADAS.md` (incluida la sección «Decisiones de implementación», sobre todo «Curvas por tramo»),
`maps/cimientos.md` y `paridad/CIMIENTOS_GRUPOS.md` (el catálogo YA está cerrado: 14 curvas, 23 duraciones, 11 retardos y 33 familias de `@keyframes` con su texto exacto; versión para máquina: `paridad/cimientos.json`).
Trabajas en el worktree **`C:\kw\pu-mo`** (rama `pulido/mo`, creada desde `feat/pulido-v1`); las herramientas del lienzo (`build.cjs`, `tools/shots.cjs`, `tools/compare.cjs`) se ejecutan en `C:\kw\pulido\docs\design\pulido-v1` con salida propia
(`node build.cjs --out=shots/mo`); los PNG no se versionan. Otro agente construye a la vez la profundidad (`ui/design/depth`) en `C:\kw\pu-dp`: **no dependas de su código ni lo toques**. Las familias que animan una sombra (`ignicion`, `onda-anillo`, `pulso-anillo`)
entrégalas como *valores animados* (extensión, alfa, radio como `Float`/`Dp` por fotograma) más un dibujo mínimo con `drawBehind` para la hoja de calibración; la pasada B/C las conecta luego con los presets de profundidad.

## Qué hay que construir
En `android-native/app/src/main/java/com/example/kpkn/ui/design/motion/`, una sola vez, el catálogo de **curvas, duraciones, entradas, salidas, pulsaciones, muelles, pulsos y ondas**:
- `KpknMotion.kt`: objeto con las 14 curvas (`CubicBezierEasing` con las coordenadas EXACTAS del lienzo; las palabras clave de CSS `ease`, `ease-in`, `ease-out`, `ease-in-out` con su `cubic-bezier` real, `linear` y `steps(1)`), la escala de duraciones (23 + 11 retardos) con sus nombres del catálogo,
  `fun <T> kpknTween(duration: KpknDuration, easing: KpknEasing, delayMillis: Int = 0)` y `kpknSpring`-equivalentes (los «muelles» del lienzo son curvas con rebasado: `resorte`, `resorte-fuerte`, `resorte-suave`; úsalos tal cual, no los cambies por `spring()`).
- **El motor de fotogramas clave** (lo más importante): `KpknKeyframes`/`Modifier.kpknAnimate(...)` que reproduce la semántica de CSS `animation` con los 33 `@keyframes` canónicos como especificaciones con nombre (`entrada-sube`, `rebote`, `latido`…): fotogramas con `offset`, propiedades (`alpha`, `translationX/Y`, `scaleX/Y`, `rotation`, y las que el catálogo use),
  `duration`, `easing` aplicada **a cada intervalo entre fotogramas** (semántica de CSS; en Compose `keyframes { v at t using easing }` rige el tramo que EMPIEZA en ese fotograma: compruébalo con fotogramas), `delay`, `iterations` (1, 2 o infinito), `direction` y `fill-mode` (`none`, `forwards`, `backwards`, `both`), y la acepción de movimiento reducido
  (sin animación: estado final). También la versión de **transición** (`transition`: de valor actual a nuevo con duración, curva y retardo).
- Componentes: `kpknEnter()`/`kpknExit()` (los desplazamientos + fundidos estándar del lienzo), `Modifier.kpknPress(...)` (escala y opacidad de pulsación), `rememberKpknPulse(...)` (pulsos y latidos infinitos con su periodo exacto), `KpknCountUp` (cifras que cuentan: el contador `[data-count]` del lienzo, 1100 ms, salida cúbica 1−(1−t)³).
- `KpknWaveField.kt`: el **campo de ondas** abstracto del cardio y de las portadas, como `Canvas` que reproduce **el mismo algoritmo** que el lienzo (lee `docs/design/program-detail-v1/src/covers.js` y las ondas de `docs/design/pulido-v1/src/scenes-cv.js`: semilla `rng`, número de líneas, amplitud, velocidad,
  color por zona, latido con el pulso) para que a un instante dado produzca el mismo dibujo, con versión estática para movimiento reducido.
- `KpknMotionSheet.kt`: `internal @Composable fun KpknMotionSheet(sheet: String)` que dibuja **exactamente** las hojas `cm-mov-*` del lienzo (círculos de 40 dp que recorren 200 dp con cada curva y duración, familias de `@keyframes`, cuenta de cifras), con la geometría y los tiempos de `src/scenes-cm.js` y `src/cm.css` (espera 600 ms en cada extremo, misma curva a la vuelta).

## Calibración con fotogramas (la parte que importa)
1. Test `android-native/app/src/test/java/com/example/kpkn/render/MotionFrameRenderTest.kt`: con `composeTestRule.mainClock.autoAdvance = false` y `advanceTimeBy(...)`, renderiza cada demostración en **t = 0, 25, 50, 75 y 100 %** de su duración (y, para las infinitas, en cinco instantes de su periodo) y guarda los PNG en `KPKN_RENDER_DIR`.
2. Captura el lienzo en los mismos instantes: `node tools/shots.cjs shot <id-cm> --build=shots/mo --t=<ms>` (congela las animaciones CSS y el reloj de `requestAnimationFrame`) y compara con `node tools/compare.cjs <lienzo.png> <app.png>`.
3. Itera hasta que cada fotograma baje del umbral (error medio ≤ 2/255; la posición de cada círculo, ±1 dp). Escribe en `paridad/CALIBRACION_MOTION.md` el error por curva, duración, familia y componente. Lo que no cierre → desviación en `paridad/DESVIACIONES.md` (`DESV-nn`) con la imagen lado a lado.
4. Tope: 5 ciclos por familia; documenta el mejor resultado y la causa si no cierra.
5. Deja en el KDoc de cada componente un ejemplo de 3 líneas.

## Gradle (autorizado por el usuario, solo por la cola)
`KPKN_RENDER_DIR=<carpeta> bash /c/kw/tools/gradle_slot.sh /c/kw/pu-mo/android-native :app:testBaseDebugUnitTest --tests "com.example.kpkn.render.MotionFrameRenderTest"`, en SEGUNDO PLANO (`run_in_background`) y esperando la notificación. La primera compilación de un worktree nuevo es en frío (unos 7 minutos)
y una corrida de pantallas tarda 15–21 min. **No mates procesos java ni abras otro Gradle**; sin `./gradlew` directo; guía completa en `/c/kw/tools/GRADLE_GUIA.md`. Si la cola está ocupada, espera: no la saltes. Agrupa los cambios para gastar pocas corridas. No hagas commit.

## Entrega
Archivos nuevos y tocados (rutas relativas), tabla de errores por componente (`paridad/CALIBRACION_MOTION.md`), desviaciones abiertas y un ejemplo de 3 líneas por componente en el KDoc. Aparte, guarda tres tiras de fotogramas lado a lado (p. ej. `estandar` con 240 ms, `rebote` y `latido`) y dime sus rutas,
para enseñárselas al usuario. Informe final ≤ 250 palabras, con franqueza sobre lo que no pudiste igualar.
