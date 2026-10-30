# Fase 0.1 · Catálogo de cimientos y su pestaña del lienzo (prefijo `cm`, pestaña `cimientos`)

Lee antes `BRIEF_COMUN.md` (reglas del lienzo y herramientas) y `PROTOCOLO_1A1.md` (por qué existe esto). **Contexto**: el usuario aprobó el lienzo y, desconfiando de que los agentes de código apliquen las
sombras y las animaciones, exigió un 1:1 comprobable. Antes de implementar pantallas se construyen los **cimientos** en la app (`ui/design/depth` y `ui/design/motion`): un catálogo finito de sombras, vidrio,
resplandores, curvas y duraciones que todas las pantallas usan. El lienzo tiene hoy **101 sombras distintas, 19 filtros, 22 curvas, 91 duraciones, 41 degradados y 110 `@keyframes`**
(`paridad/_tokens.md`, generado por `tools/inventory.cjs`). Tu trabajo es agruparlos en presets con criterio y construir las **hojas de calibración** en el lienzo que la app tendrá que igualar. Esto es
solo HTML/JS/CSS del lienzo y herramientas de `tools/`: no toques el Kotlin ni ejecutes Gradle.

## Entregas

1. **Datos de partida**: `node build.cjs --out=shots/cm` y `node tools/inventory.cjs --build=shots/cm` (genera `paridad/_inventory.json` con todas las reglas CSS que ponen sombras, vidrio, degradados, animaciones,
   transiciones y transformaciones, con su selector, propiedades y las escenas que las usan, más los `@keyframes` completos). Léelos; mira también `src/*.css` para entender el contexto de cada valor.
2. **`tools/tokens-group.cjs`** (determinista, sin dependencias): lee `paridad/_inventory.json` y escribe `paridad/cimientos.json` (máquina) y `paridad/CIMIENTOS_GRUPOS.md` (persona). Reglas de agrupación:
   - **Sombras** (`box-shadow`, `text-shadow`): se parsean en capas (inset, x, y, desenfoque, extensión, color rgba). Dos valores son equivalentes si tienen las mismas capas con x e y ±2 dp, desenfoque ±4 dp,
     extensión ±1 dp, alfa ±0,08, canales de color ±12 e igual `inset`. Agrupa de mayor a menor uso; el **valor canónico es el del miembro más usado**, tal cual (nada de promediar). Nombra cada preset por su
     papel visto en los selectores (`flotante`, `hoja`, `tarjeta`, `anillo-1`, `anillo-2`, `foco`, `realce-interior`, `resplandor-<color>`…), con números si hay variantes (`flotante-1`, `flotante-2`).
   - **Vidrio y filtros** (`backdrop-filter`, `filter`): agrupa por desenfoque ±4 y saturación/brillo ±0,15.
   - **Curvas**: `cubic-bezier` con cada coordenada ±0,05 y las palabras clave (`ease`, `linear`, `ease-out`…). **Duraciones y retardos**: ajusta a una escala (…120, 160, 200, 240, 280, 320, 400, 520, 700, 1000,
     1600, 2400 ms…) cuando la diferencia sea ≤ 12 %; lo que no encaje queda como token exacto.
   - **Degradados**: clasifica (relleno lineal, velo, resplandor radial de borde transparente…) y agrupa los de las mismas paradas; los resplandores llevan nombre `resplandor-*`.
   - **`@keyframes`**: por familia y estructura (pulso, latido, brillo que barre, dibujo de línea, entrada, cuenta, onda…): una entrada por familia con sus parámetros (duración, curva, repetición) y los nombres que agrupa.
   `CIMIENTOS_GRUPOS.md` lleva, por preset: nombre, valor canónico exacto, miembros que agrupa, número de filas del inventario que cubre y la **mayor distancia** de un miembro al canónico (para que se vea la
   tolerancia aplicada). Primero un resumen (cuántos valores distintos → cuántos presets) y las decisiones de criterio que hayas tomado.
3. **Marcar el inventario**: haz que `tools/inventory.cjs` añada a la celda «Valor exacto» de cada fila el sufijo ` → preset <nombre>` (leyendo `paridad/cimientos.json` si existe), de forma que `previous()` ignore
   ese sufijo al fusionar estados (no se pueden perder los `[x]` ya marcados). Regenera `paridad/*.md`.
4. **Pestaña `cimientos` del lienzo** (`src/scenes-cm.js`, `src/cm.css`; el tab ya está en `tabs.js`): **hojas de calibración** deterministas, a 360 × 880, que la app dibujará igual:
   - Sombras: una escena por familia de presets con una rejilla de muestras de tamaño fijo (por ejemplo 140 × 84 dp, radio 24 dp) sobre `#0B0B0C`, a posiciones exactas y documentadas (en las notas y en
     `CIMIENTOS_GRUPOS.md`), cada una con su nombre y valor. Incluye las sombras que son anillos y realces (`0 0 0 1px`, `inset`).
   - Vidrio: muestras de cada preset sobre un fondo con detalle (barras de colores y texto) para que el desenfoque se vea.
   - Resplandores y degradados canónicos.
   - Movimiento: una escena por familia de curvas/duraciones con **demostraciones CSS deterministas** (un círculo de 40 dp que recorre 200 dp con cada par curva-duración; entradas y salidas estándar;
     pulsaciones; muelles; pulsos infinitos; cuenta de cifras). Deben poder capturarse en un instante con `node tools/shots.cjs shot <id> --t=<ms>` (ya soporta CSS y `requestAnimationFrame`).
   - Una escena-documento con la tabla de agrupación para que el usuario la mire.
5. `node build.cjs --out=shots/cm`, `node tools/shots.cjs check --build=shots/cm --prefix=cm`, y mira tus PNG (también `--t=0`, `--t=150`, `--t=300`…). Deja `maps/cimientos.md` (≤ 80 líneas): criterios,
   geometría de las hojas, qué preset es cada cosa y las dudas.

## Reglas
- Cada preset tiene UN valor canónico exacto; ningún preset inventado: todo sale de lo que el lienzo ya usa. Si una sombra del lienzo no encaja en ningún grupo, es su propio preset.
- No cambies las escenas de otros paquetes. Si descubres una incoherencia visible entre paquetes (p. ej. dos sombras casi iguales que deberían ser una), anótala en `maps/cimientos.md`, no la arregles.
- Informe final ≤ 250 palabras: cuántos valores → cuántos presets por familia, la tolerancia máxima real aplicada, escenas creadas y lo que dejas dudoso.
