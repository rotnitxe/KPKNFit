# Mapa · Cimientos (Fase 0.1, prefijo `cm`, pestaña `cimientos`)

Catálogo finito de profundidad y movimiento del lienzo aprobado, agrupado en presets, y las hojas de calibración que la app debe igualar. Todo sale de lo que el lienzo ya usa: no hay valores inventados. No toca Kotlin ni otras escenas.

## 1. Flujo y archivos
1. `node build.cjs --out=shots/cm` y `node tools/inventory.cjs --build=shots/cm` (genera `paridad/_inventory.json`).
2. `node tools/tokens-group.cjs` escribe `paridad/cimientos.json` (máquina), `paridad/CIMIENTOS_GRUPOS.md` (persona) y regenera el bloque `CM` de `src/scenes-cm.js` (entre `/*CM-DATOS-INICIO*/` y `/*CM-DATOS-FIN*/`; no se edita a mano).
3. Compilar e inventariar otra vez: cada fila de `paridad/*.md` lleva ahora « → preset <nombre>» (sombras, vidrio, degradados, curvas, duraciones y familias de `@keyframes`). `previous()` ignora el sufijo: no se pierde ningún `[x]`. El paquete `cm` queda fuera del inventario (no es una pantalla).
- `tools/tokens-core.cjs` recoge y agrupa (tolerancias); `tools/tokens-group.cjs` nombra, calcula la geometría y escribe; `tools/cssvars.cjs` resuelve `var()` y atajos.
- `src/scenes-cm.js`, `src/cm.css`: 36 escenas (33 hojas y 3 documentos; los documentos van al final para que el selector «Estado» siga visible).

## 2. Hallazgo en el inventario (corregido)
Los atajos `animation:`, `transition:` y `background:` con `var()` (todo `as` y `ed`) quedaban vacíos en el CSS calculado y el inventario no los veía: +32 animaciones, +25 transiciones, +5 degradados y el muelle `cubic-bezier(.3,1.5,.5,1)`. `src/boot.js` guarda ahora su texto y las variables; `tools/inventory.cjs` los resuelve. Las filas existentes no cambian de clave.

## 3. Resultado (valores distintos → presets)
Sombras 97 → 65 (+ texto 4 → 3) · vidrio 11 → 7 · filtros 8 → 5 · curvas 15 → 14 · duraciones 54 → 23 · retardos 20 → 11 · degradados 46 → 43 · `@keyframes` 110 → 33 familias.
Tolerancia máxima realmente aplicada: sombras y ±2 dp, desenfoque ±4, extensión ±1, alfa ±0,07, canal ±12; vidrio ±4 dp; curvas ±0,02; duraciones 11,1 %; degradados posición ±8.

## 4. Criterios
- Canónico = el miembro más usado, tal cual; el resto está dentro de la tolerancia de ÉL (sin cadenas ni promedios). Empate: el que aparece antes en el CSS.
- Sombras: capas iguales e `inset` igual; color dinámico (`--c`, `--zc`, `--wc`) solo equivale a otro dinámico (hojas con `--c` #8fb2ff y `--zc` #87C9A5). Nombre por papel (`hoja`, `isla-vidrio`, `flotante`, `modal`, `dialogo-N`, `menu`, `aviso`, `popover`…) o por estructura (`caida-<y>-<desenfoque>`, `anillo-<color>-<extensión>`…).
- Duraciones: escala 90, 120, 160, 200, 240, 280, 320, 400, 520, 700, 1000, 1600, 2400 ms (el 90 es añadido) con ajuste ≤ 12 %; exactas: 140, 460, 600, 800, 1200, 1300, 1400, 6000, 15000.
- Curvas: las palabras clave se comparan por su bezier (`ease-in` se une a `cubic-bezier(.4,0,1,1)`).

## 5. Geometría de las hojas (360 × 880 sin marco, barras ni velo; origen arriba a la izquierda; fondo #0B0B0C)
- Sombras: muestra 140 × 84, radio 24, #1B1B1D. Alcance ≤ 14 dp: rejilla x 24 / 196, y 72 + 150·fila (10 por hoja), etiqueta debajo. Mayor: columna x 110, etiqueta (34 dp) encima, y según lo que sobresale la sombra. Posiciones exactas por muestra: nota de cada escena y `CIMIENTOS_GRUPOS.md`. Hojas: `cm-som-{caida,anillo,interior,resplandor,nula,texto}-N`.
- Vidrio `cm-vid-1`: franjas 312 × 72 (x 24, y 72 + 108·fila, 7 por hoja) sobre 8 barras de color y texto de 13 px fijos, con aro interior blanco 18 %. Filtros `cm-vid-filtros-1` y degradados `cm-deg-1…5`: muestras 140 × 84, x 24 / 196, y 72 + 150·fila.
- Movimiento: círculo de 40 que recorre 200 (x 60 → 260), filas cada 64 desde y 64, espera 600 ms en cada extremo, misma curva a la vuelta; `cm-mov-curvas-1…2` (cada curva con sus 2 duraciones más usadas) y `cm-mov-duraciones-1…2` (escala con «estandar»). Capturar en 0, 25, 50 y 100 % de la duración con `--t=<ms>`.
- Familias: `cm-mov-{entradas,pulsaciones,bucles,trazos-1,trazos-2}`, celdas de 152 × 120 (x 16 / 192, y 64 + 128·fila) que animan el `@keyframes` REAL más usado de la familia con su duración, curva y repetición más usadas. `cm-mov-cuenta`: el contador `[data-count]` del lienzo (1100 ms, salida cúbica 1−(1−t)³).
- Documentos: `cm-doc-resumen`, `cm-doc-sombras`, `cm-doc-resto`.

## 6. Incoherencias entre paquetes (anotadas, no arregladas)
`paridad/CIMIENTOS_GRUPOS.md` lista 20 pares «casi iguales» que no se unieron por poco. Los más claros: `flotante` (0 8 28, 67 %) y `caida-8-28` (mismo con 80 %, `.tc-loupe`); `menu` (0 14 40), `aviso` (0 12 34) y `popover` (0 18 44) son tres versiones de un mismo menú flotante; `modal` (0 20 60) y `caida-16-60` (`.nc-os`); `borde-interior` 8 % / 16,5 % / 25 %; `resplandor-crema` 8, 10, 12, 18; `dialogo-1` y `dialogo-2` (capa interior distinta).

## 7. Dudas
- ¿Unificar los pares casi iguales del apartado 6? (Reduciría presets; hay que cambiar el lienzo de otros paquetes.)
- Las hojas de caída ocupan 8 pantallas por el alcance de las sombras grandes; si molesta se pueden compactar a costa de que se solapen halos.
- Sombras sobre #0B0B0C casi no se ven a simple vista: la comparación es numérica (`tools/compare.cjs`), no ocular.
- Android < 12 no tiene desenfoque de fondo: `cm-vid-1` solo calibra Android 12+.
- Las duraciones de 6000 y 15000 ms son bucles decorativos; solo se calibra su primera vuelta.
