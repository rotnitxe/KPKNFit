# Protocolo 1:1 · cómo se garantiza que la app sea igual que el lienzo aprobado (29-oct-2026)

Origen: el usuario aprobó el lienzo (menos el roadmap, que se rehace) y dijo que no confía en recibir un 1:1 porque «algunas animaciones y sombras nunca las aplican los agentes».
Tiene razón y esto es lo que lo explica y lo que cambia. Nada de lo de abajo es una promesa: cada mecanismo es una herramienta o una puerta que se puede comprobar.

## Por qué se perdían sombras y animaciones

1. **Solo estaba escrito lo que se ve en una captura.** Las animaciones, los estados de pulsación y las sombras sutiles no salen en un PNG, así que no figuraban en ninguna lista de comprobación.
2. **La matriz de paridad se hacía a mano.** Quien implementaba decidía qué contaba como «elemento»; lo que no recordaba no existía.
3. **Cada pantalla improvisaba su propia sombra, curva y duración.** El lienzo aprobado tiene 101 sombras distintas, 22 curvas y 91 duraciones: sin un catálogo común, cada agente aproximaba.
4. **La verificación era estática, tardía y declarativa** («tests verdes», «paridad hecha») en vez de una comparación con el lienzo.

## Qué cambia

| # | Mecanismo | Herramienta (ya hecha salvo lo marcado) |
|---|---|---|
| 1 | **Inventario automático** de cada sombra, desenfoque, degradado, resplandor, animación (@keyframes), transición, transformación y movimiento en JavaScript del lienzo, sacado del CSS real. Cada fila exige estado: no se borra. | `node tools/inventory.cjs` → `paridad/<prefijo>.md` (763 filas hoy) y `paridad/_tokens.md` |
| 2 | **Cimientos antes de las pantallas** (Fase 0): `ui/design/depth` (sombras en capas, vidrio con Haze, anillos, resplandores) y `ui/design/motion` (curvas, duraciones, entradas, muelles, pulsos, campo de ondas) con los valores del catálogo; una hoja de render de los tokens se compara con la misma hoja del lienzo hasta calibrarla. Los paquetes solo usan esos tokens. | por hacer (Fase 0); catálogo en `paridad/_tokens.md` |
| 3 | **Pasadas separadas con agentes distintos** por paquete: A estructura → B profundidad (solo filas S, V, G) → C movimiento (solo filas A, T, X, J) → D auditoría independiente contra la matriz. Quien hace una pasada no audita la suya. | briefs por pasada (por escribir) |
| 4 | **Cuatro puertas de cierre** por paquete (ver abajo). Sin las cuatro no se declara terminado. | `tools/paridad-check.cjs`, `tools/compare.cjs`, hojas de render, Flip |
| 5 | **Desviaciones registradas**: lo que Android no puede hacer igual se lista con captura lado a lado y se te enseña. No se llama «1:1» a nada que tenga una. | `paridad/DESVIACIONES.md` |
| 6 | **El lienzo manda sobre el código**: si la app se ve distinta, se cambia la app; lo que el lienzo no contempla se elimina (regla del 28-oct). | — |

## Las cuatro puertas de cierre de un paquete

1. **Matriz completa**: `node tools/paridad-check.cjs <prefijo>` sale en verde: toda fila de `paridad/<prefijo>.md` está en `[x]` con `archivo.kt:línea` que existe, o en `[~]` con una `DESV-nn` registrada.
2. **Render estático**: una hoja de render (Robolectric, como las `*RenderSheetTest` que ya existen) con TODAS las escenas del paquete a 360×880, y 360×740 y 130 % en las densas; cada una se compara con la captura del lienzo con `node tools/compare.cjs`
   (porcentaje de píxeles distintos, error medio, zona más lejana e imagen lienzo | app | diferencias). El umbral lo fija la calibración de la Fase 0; lo que lo supere se explica o se arregla.
3. **Fotogramas de movimiento**: cada animación y transición de la matriz se renderiza con el reloj de pruebas en cuatro instantes (0, 25, 50 y 100 % de su duración) y se compara con el lienzo en los mismos instantes. Las animaciones continuas (pulsos, ondas) se comparan por periodo.
4. **Flip**: capturas y vídeo de cada estado con el APK de depuración al lado del lienzo (regla de 20 minutos, o sesión supervisada si me cedes el teléfono). Lo reviso yo, no el agente que lo implementó.

## Límites reales (se tratan de frente, no se esconden)

- `box-shadow` de CSS (desenfoque y extensión arbitrarios, varias capas) no existe en Android: `Modifier.shadow` usa elevación. Se dibuja en capas con `drawBehind` y la calibración de la Fase 0 mide la diferencia; el resultado se acerca mucho pero no es matemáticamente idéntico.
- `backdrop-filter: blur()` → Haze: radio y tinte equivalentes, no iguales; en Android < 12 (el flavor base llega a 24) cae a un velo; en el Flip (Android 16) sí desenfoca.
- Las 101 sombras se agrupan en presets por equivalencia visual (desenfoque ±4 dp, alfa ±0,08); la tabla de agrupación se te enseña antes de implementar y cada preset lleva el valor exacto de su miembro más usado.
- Las animaciones CSS se pasan con las mismas duraciones y curvas (`CubicBezierEasing`, `tween`, `keyframes`, `infiniteRepeatable`); lo que CSS hace y Compose no (`steps()`, ciertas máscaras) se emula y se registra como desviación si se nota.

## Orden de trabajo

- **Fase 0 · cimientos** (un paquete, un agente de profundidad y otro de movimiento, con su hoja de render calibrada).
- **Fase 1 · paquetes en oleadas** (las pasadas A→B→C→D de cada uno; los que tocan los mismos archivos van seguidos, no a la vez):
  1. Editor + cardio del editor + asistente (comparten barra inferior, isla superior y hojas).
  2. Sesión en vivo: tarjeta (incluido el roadmap nuevo), resto en vivo y cardio en vivo (comparten `WorkoutScreen`, `WorkoutV2Body` y la tarjeta).
  3. Catálogo, reparto y técnica (con motor y tests de dominio por el carril JVM).
  4. Comida y cuerpo, suplementos y ajustes.
- **Fase 2 · integración** por turnos (`turno.sh`), un paquete cada vez, con su matriz verde.

El cuello de botella es la cola de Gradle (15–21 minutos por corrida de pantallas y una sola ranura efectiva). Por eso las pasadas B y C de un paquete van juntas en una corrida cuando se puede.

## Lo que hace falta de ti

1. **Luz verde para Gradle** (compilar, hojas de render y tests de las clases tocadas, siempre por la cola). Sin eso no existen las puertas 2 y 3, y yo no puedo prometer un 1:1.
2. **El teléfono** para la puerta 4 al cerrar cada oleada.
3. **Elegir el roadmap** (propuestas A y B del lienzo).
