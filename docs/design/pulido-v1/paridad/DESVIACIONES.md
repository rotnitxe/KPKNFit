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
