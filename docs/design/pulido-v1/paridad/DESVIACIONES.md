# Desviaciones aceptadas respecto al lienzo

Una desviación es algo que la app NO hace exactamente como el lienzo aprobado (por límites de Android, de la librería de vidrio o por una decisión del usuario).
Cada una lleva un identificador `DESV-nn`, el ID de la fila de `paridad/<prefijo>.md` que cubre, qué cambia y cuánto, una captura **lado a lado** (lienzo | app | diferencias, hecha con
`tools/compare.cjs`) y, cuando exista, quién la aprobó. **Sin captura y sin aprobación, una desviación no vale: la fila sigue abierta.** No se declara «1:1» nada que tenga una desviación.

Formato (copiar y rellenar; el título tiene que ser `## DESV-nn` exacto porque `tools/paridad-check.cjs` lo busca):

```
## DESV-01
- Filas: lv-S06, lv-S16
- Qué cambia: la sombra `0 14px 34px #000c` se dibuja con capas porque `Modifier.shadow` no admite desenfoque ni extensión arbitrarios; diferencia máxima medida: 3 % de píxeles en la franja inferior.
- Captura: paridad/capturas/DESV-01.png
- Aprobada por: (usuario, fecha) / pendiente
```

**Rangos de numeración** (cada agente trabaja a la vez que otros; así no se pisan los números): profundidad (`dp`) 100–199 · movimiento (`mo`) 200–299 · `as` 300–399 · `ed` 400–499 · `ce` 500–599 · `lv` 600–699 · `lx` 700–799 · `cv` 800–899 · `ct` 900–999 · `rp` 1000–1099 · `tc` 1100–1199 · `nc` 1200–1299 · `sp` 1300–1399 · `aj` 1400–1499.
Los documentos (este archivo, `CALIBRACION_*.md`, `capturas/`) se escriben SOLO en la copia canónica `C:\kw\pulido\docs\design\pulido-v1`; las copias de los worktrees son de lectura.

(Todavía no hay desviaciones: se abren durante la implementación.)


---

## DESV-100
- Filas: vidrio (`backdrop-filter`) de todas las filas con preset `vidrio-isla`, `vidrio-hoja`, `vidrio-barra`, `vidrio-editor`, `velo-desenfoque`, `vidrio-interruptor` o `velo-suave` (`paridad/*.md`)
- Qué cambia: (1) el desenfoque real de Haze solo se pinta con `RenderEffect` en un teléfono con Android 12 o más; en la hoja de Robolectric (lienzo de software) sale el velo de respaldo, así que NO se pudo medir el vidrio real de Haze: se midió su equivalente por software (σ = B, saturate/brightness de CSS, borde espejo del navegador): 0,7–2,1/255 en el interior de las franjas, y el cálculo contra el lienzo confirma σ = 1,0 × B. (2) No se comprobó qué borde usa Haze en el recorte (Chrome: espejo): puede cambiar el color de los ~30 dp del borde de la franja. (3) Debajo de Android 12 no hay desenfoque (velo `KpknGlass.VeilFallback`; decisión aprobada). (4) Con hijos dentro del nodo del vidrio, la saturación y el brillo también les afectan: poner el vidrio en un nodo de fondo sin contenido.
- Captura: paridad/capturas/DESV-100.png
- Aprobada por: pendiente (se mide en el Flip5, puerta 4)

## DESV-101
- Filas: filtros `halo-desenfoque` y `resplandor-desenfoque` (`filter: blur(20px / 26px)`)
- Qué cambia: `RenderEffect` (Android 12+) no se pinta en el lienzo de software de la JVM; medido por emulación: 4,6/255 de media en el interior (el mejor modelo en Python sobre el propio lienzo da 6,4: el borde transparente de Chrome no se reproduce exacto). Debajo de Android 12 el desenfoque se ignora. Los filtros de color (`foto-apagada`, `video-apagado`, `miniatura-apagada`) sí se pintan en la JVM: 0,9–1,0/255.
- Captura: paridad/capturas/DESV-101.png
- Aprobada por: pendiente (Flip5)

## DESV-102
- Filas: toda sombra con desenfoque (caídas y resplandores) en Android 7–8 (API 24–27)
- Qué cambia: `BlurMaskFilter` no se acelera por hardware antes de la API 28: las capas con desenfoque caen a `Modifier.shadow` con elevación = desenfoque/2 (sombra de Material, sin extensión ni capas) y los anillos e interiores se siguen dibujando exactos. No se puede medir (la hoja se calibra en API 34).
- Captura: no aplica (no hay dispositivo ni hoja de API < 28)
- Aprobada por: pendiente

## DESV-200
- Filas: familia `revelado` (`tcvid`, hoja `cm-mov-trazos-1`)
- Qué cambia: `filter: blur(6px)` se aplica con `Modifier.blur(…, Unbounded)` (Android 12+); Robolectric no pinta `RenderEffect`, así que en la hoja de calibración el desenfoque no se ve y no se puede medir. Brillo (0,5 → 1) y escala (1,06 → 1) sí coinciden. Error del estadio: 13,4/255 en t = 0, 6,4 en 25 %, 1,8 en 50 %, 0,6 en 75 %, 0,4 en 100 %. En Android < 12 no hay desenfoque (queda el brillo).
- Captura: paridad/capturas/DESV-200.png (t = 0: lienzo | app | diferencias)
- Aprobada por: pendiente (comprobar en el Flip)

## DESV-201
- Filas: hoja `cm-mov-cuenta` (`KpknCountUp`)
- Qué cambia: el lienzo pinta las cifras en Inter 800 de 40 px; la app solo tiene Inter Regular, Medium y SemiBold, así que usa SemiBold, y la línea de 42 dp coloca los números unos 3–4 dp más abajo. Los valores (1.091 · 1.881 kcal · 72,2 kg · 16.118 kg · 144,2 g a los 550 ms) y la curva coinciden en los 5 instantes. Error de la hoja: 8,8/255 de media (oficial 8,4).
- Captura: paridad/capturas/DESV-201.png (t = 550 ms)
- Aprobada por: pendiente

## DESV-202
- Filas: todas las hojas `cm-mov-*` (texto de rótulos y de las chips)
- Qué cambia: Chrome y Skia rasterizan el texto distinto; el error de las hojas enteras es 1,2–2,8/255 aunque el círculo coincida a 0,04 dp y la pista a 0,2/255. Los estadios de las familias con texto (chips) quedan en 1,0–1,9/255.
- Captura: paridad/capturas/DESV-202.png (curvas-1, t = 120 ms)
- Aprobada por: pendiente

## DESV-203
- Filas: `KpknWaveField` estado «Sobre la zona» (`above`, aspereza 0,5)
- Qué cambia: con la misma fase y la misma intensidad quedan 5,6/255 en los trazos casi verticales; los otros cinco estados, entre 0,5 y 2,0. Causa probable: suavizado de bordes en trazos de 1 dp muy inclinados; el algoritmo coincide (15 valores de `cvWaveD` a 0,01).
- Captura: paridad/capturas/DESV-203.png (t = 2000 ms)
- Aprobada por: pendiente

## DESV-1000
- Filas: rp-S03
- Qué cambia: la hoja del lienzo (`.pl-sheet`: `0 -14px 44px #000c` + filete interior `0 1px #ffffff10`) la pinta `KpknSheet`, el componente compartido de hojas, que hoy no dibuja la sombra de caída hacia arriba (solo el velo `#000000a8` y su filete superior); las hojas del reparto (`RpSheet`: decisiones y resumen) la usan tal cual. Tocar `KpknSheet` afecta a todas las pantallas de la app y queda fuera de este paquete. En la captura se ve en la hoja de «Antes de aplicar» (a la izquierda el lienzo, en medio la app: sin halo sobre el borde de la hoja; en la hoja de render, además, la hoja se dibuja a mano y con otra altura).
- Captura: paridad/capturas/DESV-1000.png (`rp-decisiones`, 360 × 880)
- Aprobada por: pendiente

