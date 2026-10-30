# Pasada A+B+C unificada · estructura, profundidad y movimiento en una sola entrega (desde el 30-oct-2026)

Se usa para los paquetes que arrancan DESPUÉS de que los cimientos (`ui/design/depth`, `ui/design/motion`) estén en `feat/pulido-v1`: tu worktree sale de esa rama y ya los trae. Sigue `briefs/impl-COMUN-v2.md` (reglas firmes) y `PROTOCOLO_1A1.md` (las cuatro puertas). El cuello de botella es la cola de Gradle (una ranura, 15–21 min por corrida):
**agrupa los cambios y gasta pocas corridas** (la primera para ver que compila y las hojas salen; la segunda para confirmar, no más de tres).

## Qué haces, en este orden
1. **Estructura, estados y lógica** (pasada A de `impl-pasadas.md`): layout con las medidas EXACTAS del lienzo, tipografía, colores, textos, iconos, orden y TODOS los estados de cada escena; la lógica y los datos que pide `maps/<paquete>.md`; **eliminas** lo que la pantalla vieja tiene y el lienzo no.
2. **Profundidad** (filas S, V, G de `paridad/<prefijo>.md`): usa los presets de `ui/design/depth` (`kpknShadow`, `kpknRing`, `kpknInset…`, `KpknGlassPresets`, `KpknGradients`, `KpknTextShadows`). Lee primero sus KDoc. Cada fila trae «Valor exacto»: si el preset canónico difiere del valor de la fila,
   pasa el valor exacto (la API admite `preset.spec.copy(...)` o un spec propio). Prohibido `Modifier.shadow`/`elevation` suelto, `BlurMaskFilter` suelto o un degradado escrito a mano en la pantalla.
3. **Movimiento** (filas A, T, X, J): usa `ui/design/motion` (`KpknMotion` curvas y duraciones, `kpknAnimate` con las familias de `@keyframes` por nombre, `kpknEnter/kpknExit/kpknPress`, `rememberKpknPulse`, `KpknCountUp`, `KpknWaveField`). Duración, curva, retardo, fill-mode y disparador de la fila, no «parecidos».
   Animaciones infinitas con su periodo exacto y versión estática con movimiento reducido. Los gestos (umbrales, posiciones) son lógica; su respuesta visual es la ficha de movimiento de la escena.
4. **Matriz**: cada fila de `paridad/<prefijo>.md` (copia canónica en `C:\kw\pulido\docs\design\pulido-v1\paridad\`; es el ÚNICO documento que editas, solo tu archivo de paquete) acaba `[x]` con `archivo.kt:línea` real, o `[~]` con una `DESV-nn` de tu rango en `paridad/DESVIACIONES.md`
   y la captura lado a lado en `paridad/capturas/`. No se borran filas. `node tools/paridad-check.cjs <prefijo> --repo=<tu worktree>` debe salir en verde.

## Verificación (puertas 2 y 3, lo que hace falta de verdad)
- **Hoja de render** `render/<Paquete>RenderSheetTest.kt` que dibuje CADA escena del paquete con los datos de muestra del lienzo a 360 × 880 (y 360 × 740 y 130 % en las densas) → PNG en `KPKN_RENDER_DIR`. Compáralas con `node tools/compare.cjs <lienzo.png> <app.png> --out=<lado-a-lado.png>`
   (lienzo: `node build.cjs --out=shots/<prefijo>` y `node tools/shots.cjs shot <id> --build=shots/<prefijo>`). Objetivo: error medio ≤ 2,5/255 por escena y casi 0 % de píxeles > 28/255 fuera de bordes de suavizado; lo que no baje se explica o se arregla. Con profundidad ya presente, esta es la comparación DEFINITIVA.
- **Fotogramas**: las familias de `@keyframes` del catálogo ya están calibradas por fotogramas en los cimientos; en tu paquete compruebas el **cableado** (nombre de la familia, duración, retardo, fill-mode, disparador) y renderizas fotogramas (reloj de pruebas `mainClock`, instantes 0, 25, 50, 75 y 100 % con `node tools/shots.cjs shot <id> --t=<ms>`)
  de TODA animación propia del paquete que no esté en el catálogo, y de las animaciones compuestas más visibles (entradas de hoja, píldoras, resplandor de esfuerzo, etc.).
- Tests de dominio por el carril JVM cuando sea lógica pura; tests de pantalla/ViewModel por la cola con `--tests "<clase concreta>"`. No dejes tests rotos y actualiza los que tu cambio invalida.

## Informe final (≤ 300 palabras)
Filas cerradas (IDs), filas `[~]` con su `DESV`, archivos tocados y nuevos (rutas relativas), números de comparación por escena (error medio y % de píxeles) y por animación propia, lo abierto y los riesgos, y con franqueza lo que NO pudiste comprobar. Cita las rutas de TRES comparaciones lado a lado
(las más visibles del paquete) para enseñárselas al usuario. No hagas commit.
