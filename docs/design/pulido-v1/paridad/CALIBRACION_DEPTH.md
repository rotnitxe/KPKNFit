# Calibración de la profundidad (Fase 0.2 · `ui/design/depth`) · 30-oct-2026

Hojas de render de la app (`DepthSheetRenderTest`, Robolectric, 360 × 880 dp a xxhdpi = 1080 × 2640 px) comparadas con las mismas hojas del lienzo (`cm-som-*`, `cm-vid-1`, `cm-vid-filtros-1`, `cm-deg-1…5`).
Dos corridas de calibración de la app (la 2.ª con los arreglos de abajo). **Resultado: todas las sombras, anillos, interiores, resplandores y degradados quedan por debajo del objetivo (≤ 2,5/255 en la zona de cada muestra); el vidrio real de Haze y el desenfoque `RenderEffect` NO se pueden medir en la JVM** (ver DESV-100 y DESV-101).

## Cómo se midió
- Lienzo: `node build.cjs --out=shots/dp` y capturas con `--force-device-scale-factor=3` (la misma densidad de la app; con 1× los bordes duros de Chrome sin anti-alias inflaban rayas y anillos, p. ej. rayas-energia 11 → 0,5), reducidas a 360 × 880 promediando bloques de 3 × 3 (`PIL.BOX`). La app hace la misma reducción (`<id>_1x.png`). Dos versiones del lienzo: la hoja entera, y otra sin rótulos ni cabecera (`.cm-l,.cm-h,.cm-gt{display:none}`) con la app `showLabels = false` (`<id>_nolabel`).
- Hoja entera: `node tools/compare.cjs` (error medio de TODA la hoja, incluye el texto de rótulos y cabecera: la tipografía anti-alias del navegador y la de Android no coinciden bit a bit).
- Por muestra: `node tools/depth-zones.cjs <hoja> <lienzo-sin-rotulos> <app-sin-rotulos>` (nuevo). Error = máx(|ΔR|,|ΔG|,|ΔB|) por píxel (0–255). «fuera» = el rectángulo de la muestra ampliado en el alcance de la sombra, SIN el interior redondeado (donde está la sombra); «dentro» = el interior (realces `inset`, degradados, vidrio). Resumen de todas las hojas: `node tools/depth-report.cjs --app=<carpeta de PNG> --canvas=shots/dp/cm1x-full --canvas-nl=shots/dp/cm1x-nl`.
- Los PNG no se versionan: se regeneran con `node tools/depth-report.cjs`. Los catálogos de Kotlin se generan con `node tools/depth-kotlin-gen.cjs --out=<ui/design/depth>` desde `paridad/cimientos.json`.

## Constantes CSS → Android que salieron
| Familia | Método en Android | Equivalencia calibrada |
|---|---|---|
| `box-shadow` caída / resplandor / anillo | `drawBehind` + `Canvas.drawPath` con `BlurMaskFilter`, recortada FUERA de la forma (`ClipOp.Difference`) | CSS: σ = B/2. Skia: σ = 0,57735·radio + 0,5 (px de pantalla) → `radio = (σpx − 0,5)/0,57735` con `σpx = B/2 · densidad` (`KpknDepthCalibration.ShadowSigmaPerBlur = 0,5`). Ajustando la σ de un perfil real: app/teoría = 1,01 (σ 6,08 vs 6,0; 5,08 vs 5,0; 4,01 vs 4,0); Chrome sale 0,89–1,04 × la teórica (su gaussiana es una aproximación): la app usa la exacta y la diferencia queda en 1–2 niveles de gris. No se retocó el 0,5. |
| extensión (`spread`) | se infla el rectángulo redondeado (radio + extensión; radio 0 no se redondea) | exacta (anillos: 0,1–0,7/255 salvo 1,2–1,6 en los claros de 1,5–3 dp) |
| `inset` | marco `EvenOdd` (rectángulo grande − agujero desplazado y reducido por la extensión), desenfoque y recorte DENTRO de la forma | exacta (0,0–0,05/255 dentro) |
| orden de pintado | capas exteriores (la última del CSS primero), fondo, capas `inset` | igual al CSS |
| `text-shadow` | `Shadow(blurRadius = radio Skia)`; dos capas = dos copias del texto apiladas | σ = B/2 como en la caída (fuera 0,0–0,25/255) |
| degradados | `LinearGradient`/`RadialGradient` (elipse con matriz local), ángulo CSS (línea = \|W·sen\| + \|H·cos\|), `repeating` con `TileMode.REPEAT` | cada tramo se subdivide en 8 con interpolación **premultiplicada** (la de CSS) |
| `backdrop-filter: blur(B)` | Haze 1.7.2: `HazeStyle.blurRadius = B.dp` | **factor 1,0**: Haze pasa `blurRadius.toPx()` tal cual a `RenderEffect.createBlurEffect(σ, σ)` (bytecode de `RenderEffect_androidKt.createRenderEffect`). Contra el lienzo (Python sobre las capturas): σ = 1,0 × B con borde espejo da 1,5–1,9/255 en el centro de la franja; 0,9 × da 2,4–3,7 y 1,1 × da 2,1–4,4. |
| `saturate(s)` / `brightness(b)` | `saveLayer` con `ColorMatrix` (pesos 0,213/0,715/0,072 de CSS) | si `s > 1` y `b ≠ 1` (vidrio-barra) van **dos capas** (saturación dentro, brillo fuera): CSS recorta entre funciones, una sola matriz recortaba el azul tarde (azul 255 vs 230 del lienzo). Filtros de color medidos sobre píxeles donde el fondo coincide: 0,86–1,01/255 |
| `filter: blur()` | `graphicsLayer { renderEffect = BlurEffect(σ, σ, TileMode.Decal) }` | σ = B (Android 12+) |

## Errores por preset (sin rótulos, 3× reducido a 1×; fuera/dentro = error medio y máximo en 0–255)
### Sombras de caída

| Preset | fuera: medio | fuera: máx | dentro: medio | dentro: máx |
|---|---|---|---|---|
| `asa-deslizador` | 0.69 | 1 | 0.00 | 1 |
| `caida-2-8-capas` | 0.62 | 2 | 0.01 | 2 |
| `hoja` | 0.82 | 2 | 0.02 | 2 |
| `isla-vidrio` | 0.83 | 2 | 0.02 | 2 |
| `barra-vidrio` | 0.75 | 3 | 0.02 | 2 |
| `nota` | 0.77 | 2 | 0.00 | 1 |
| `flotante` | 0.82 | 2 | 0.00 | 1 |
| `modal` | 0.68 | 2 | 0.00 | 1 |
| `historia` | 0.79 | 3 | 0.04 | 2 |
| `pie-sube` | 0.81 | 1 | 0.00 | 1 |
| `menu` | 0.81 | 2 | 0.00 | 1 |
| `aviso` | 0.81 | 2 | 0.00 | 1 |
| `dialogo-1` | 0.73 | 2 | 0.02 | 2 |
| `caida-4-18` | 0.83 | 2 | 0.00 | 1 |
| `levantado` | 0.74 | 3 | 0.01 | 2 |
| `popover` | 0.79 | 2 | 0.00 | 1 |
| `caida-10-22` | 0.77 | 2 | 0.00 | 1 |
| `caida-6-16-capas-1` | 0.77 | 2 | 0.01 | 2 |
| `caida-6-16-capas-2` | 0.78 | 9 | 0.02 | 8 |
| `caida-8-20-capas` | 0.78 | 2 | 0.04 | 2 |
| `caida-10-28-capas` | 0.92 | 2 | 0.00 | 1 |
| `caida-4-14` | 0.70 | 1 | 0.00 | 1 |
| `caida-16-60` | 0.75 | 1 | 0.00 | 1 |
| `dialogo-2` | 0.75 | 3 | 0.02 | 2 |
| `caida-18-50` | 0.79 | 2 | 0.00 | 1 |
| `dialogo-vidrio` | 0.73 | 2 | 0.00 | 2 |
| `caida-8-28` | 0.83 | 1 | 0.00 | 1 |
| `caida-24-70` | 0.69 | 2 | 0.00 | 1 |

### Anillos

| Preset | fuera: medio | fuera: máx | dentro: medio | dentro: máx |
|---|---|---|---|---|
| `anillo-actual` | 0.10 | 9 | 0.01 | 7 |
| `anillo-doble-actual` | 0.37 | 11 | 0.05 | 10 |
| `aureola-foco` | 0.52 | 2 | 0.00 | 1 |
| `corte-hoja` | 0.06 | 1 | 0.01 | 2 |
| `borde-exterior` | 0.23 | 2 | 0.00 | 1 |
| `anillo-negro-4` | 0.04 | 1 | 0.01 | 2 |
| `anillo-energia-1p5` | 0.10 | 8 | 0.01 | 8 |
| `anillo-foco` | 0.36 | 3 | 0.00 | 3 |
| `anillo-blanco-3-suave` | 0.44 | 2 | 0.00 | 1 |
| `anillo-aviso` | 0.46 | 2 | 0.01 | 1 |
| `anillo-hecho` | 0.46 | 2 | 0.01 | 2 |
| `anillo-error` | 0.46 | 2 | 0.00 | 2 |
| `anillo-crema-4-suave` | 0.51 | 2 | 0.00 | 1 |

### Realces y bordes interiores

| Preset | fuera: medio | fuera: máx | dentro: medio | dentro: máx |
|---|---|---|---|---|
| `subrayado-musculo` | 0.02 | 1 | 0.01 | 1 |
| `subrayado-hecho` | 0.03 | 2 | 0.03 | 2 |
| `interior-hecho-1` | 0.05 | 2 | 0.04 | 2 |
| `borde-interior` | 0.05 | 2 | 0.04 | 2 |
| `interior-crema-1` | 0.03 | 1 | 0.01 | 2 |
| `interior-crema-1p5-1` | 0.04 | 2 | 0.07 | 2 |
| `interior-blanco-1-tenue` | 0.05 | 2 | 0.04 | 2 |
| `interior-zona-1p5` | 0.04 | 8 | 0.02 | 10 |
| `borde-interior-fuerte` | 0.03 | 2 | 0.04 | 2 |
| `interior-peligro-1` | 0.05 | 3 | 0.04 | 2 |
| `interior-blanco-1p5` | 0.02 | 6 | 0.02 | 10 |
| `interior-musculo-1` | 0.05 | 2 | 0.04 | 3 |
| `interior-crema-1p5-2` | 0.05 | 2 | 0.07 | 2 |

### Resplandores

| Preset | fuera: medio | fuera: máx | dentro: medio | dentro: máx |
|---|---|---|---|---|
| `resplandor-crema-12` | 1.33 | 2 | 0.00 | 2 |
| `resplandor-zona-10` | 1.20 | 2 | 0.00 | 3 |
| `resplandor-zona-12` | 0.75 | 2 | 0.01 | 1 |
| `resplandor-zona-14-capas` | 0.68 | 7 | 0.03 | 7 |
| `resplandor-crema-10` | 1.32 | 2 | 0.00 | 2 |
| `resplandor-crema-12-capas` | 1.23 | 2 | 0.01 | 1 |
| `resplandor-crema-8` | 0.90 | 2 | 0.00 | 3 |
| `resplandor-zona-28` | 1.57 | 3 | 0.01 | 2 |
| `resplandor-crema-18` | 1.36 | 2 | 0.00 | 1 |
| `resplandor-energia-22-capas` | 1.55 | 3 | 0.04 | 2 |

### Sin sombra

| Preset | fuera: medio | fuera: máx | dentro: medio | dentro: máx |
|---|---|---|---|---|
| `ninguna` | 0.02 | 1 | 0.00 | 1 |

### Sombras de texto

| Preset | fuera: medio | fuera: máx | dentro: medio | dentro: máx |
|---|---|---|---|---|
| `texto-2-26-capas` | 0.05 | 4 | 2.13 | 66 |
| `texto-1-12` | 0.00 | 1 | 1.86 | 67 |
| `texto-resplandor-negro-40` | 0.25 | 4 | 1.53 | 60 |

### Degradados y resplandores (radiales/lineales)

| Preset | fuera: medio | fuera: máx | dentro: medio | dentro: máx |
|---|---|---|---|---|
| `relleno-crema-suave` | 0.16 | 6 | 0.71 | 10 |
| `relleno-crema` | 0.16 | 6 | 0.70 | 9 |
| `tinte-hacia-modulo-1` | 0.10 | 1 | 0.45 | 2 |
| `tinte-hacia-modulo-2` | 0.09 | 1 | 0.42 | 2 |
| `relleno-horizontal-blanco` | 0.11 | 1 | 0.76 | 2 |
| `relleno-horizontal-crema-1` | 0.09 | 1 | 0.38 | 1 |
| `relleno-horizontal-crema-2` | 0.16 | 8 | 0.69 | 8 |
| `tinte-crema-35` | 0.13 | 3 | 0.75 | 4 |
| `tinte-crema-70` | 0.17 | 6 | 0.26 | 2 |
| `relleno-modulo` | 0.09 | 1 | 0.35 | 2 |
| `velo-negro-1` | 0.08 | 1 | 0.29 | 1 |
| `velo-negro-2` | 0.05 | 1 | 0.14 | 1 |
| `velo-negro-3` | 0.05 | 1 | 0.15 | 1 |
| `velo-hoja` | 0.06 | 1 | 0.20 | 1 |
| `velo-negro-4` | 0.10 | 1 | 0.18 | 1 |
| `velo-hoja-largo` | 0.05 | 1 | 0.52 | 1 |
| `velo-hoja-sale` | 0.08 | 1 | 0.28 | 1 |
| `velo-negro-sale-1` | 0.09 | 1 | 0.20 | 1 |
| `relleno-cardio` | 0.15 | 6 | 0.72 | 10 |
| `relleno-horizontal-cardio` | 0.17 | 7 | 0.80 | 10 |
| `vidrio-relleno-hoja` | 0.10 | 1 | 0.71 | 2 |
| `vidrio-relleno-barra` | 0.08 | 1 | 0.78 | 1 |
| `vineta` | 0.09 | 1 | 0.56 | 2 |
| `resplandor-ancho-zona` | 0.06 | 1 | 0.31 | 2 |
| `resplandor-zona` | 0.00 | 0 | 0.60 | 2 |
| `resplandor-crema` | 0.00 | 0 | 0.63 | 2 |
| `velo-negro-sale-2` | 0.08 | 1 | 0.16 | 1 |
| `velo-negro-sale-3` | 0.08 | 1 | 0.14 | 1 |
| `vidrio-relleno-tecnica` | 0.10 | 1 | 0.61 | 2 |
| `velo-crema-largo` | 0.09 | 1 | 0.65 | 1 |
| `fondo-compuesto-1` | 0.09 | 1 | 0.68 | 2 |
| `brillo-esqueleto-1` | 0.08 | 1 | 0.50 | 1 |
| `brillo-esqueleto-2` | 0.11 | 1 | 0.81 | 2 |
| `fondo-compuesto-2` | 0.09 | 1 | 0.73 | 2 |
| `rejilla` | 0.01 | 1 | 0.06 | 1 |
| `brillo-esqueleto-3` | 0.06 | 1 | 0.19 | 2 |
| `brillo-esqueleto-4` | 0.10 | 1 | 0.26 | 1 |
| `aviso-relleno-error` | 0.12 | 2 | 0.87 | 2 |
| `franja-crema` | 0.09 | 1 | 0.68 | 1 |
| `brillo-esqueleto-5` | 0.06 | 1 | 0.19 | 1 |
| `rayas-blanco` | 0.06 | 2 | 0.49 | 1 |
| `rayas-crema` | 0.16 | 6 | 0.41 | 7 |
| `rayas-energia` | 0.12 | 1 | 0.50 | 1 |

### Vidrio (`cm-vid-1`) y filtros (`cm-vid-filtros-1`)
Haze real en Robolectric = el velo de respaldo oscuro (la hoja entera da 94/255): **no es una medida del vidrio**. Se mide con el fondo de la app desenfocado por software con las constantes de arriba (`*_emul_nolabel.png`):

| Preset | dentro: medio | dentro: máx | nota |
|---|---|---|---|
| `vidrio-isla` (blur 22, sat 1,5) | 2,07 | 27 | |
| `vidrio-hoja` (26, 1,3) | 1,86 | 53 | |
| `vidrio-barra` (18, 1,8, bri 0,9) | 2,11 | 27 | tras el arreglo de las dos capas |
| `vidrio-editor` (24, 1,7) | 2,00 | 53 | |
| `velo-desenfoque` (12) | 1,91 | 26 | |
| `vidrio-interruptor` (16, 1,4) | 1,98 | 53 | |
| `velo-suave` (6) | 0,66 | 7 | |
| `foto-apagada` (sat 0,8) | 0,86 en píxeles de fondo coincidente | 12 | la media de toda la muestra (9,1) es el texto del fondo (glifos distintos) |
| `video-apagado` (bri 0,62, sat 0,8) | 1,01 en fondo coincidente | 8 | media de la muestra 6,0 |
| `miniatura-apagada` (bri 0,9) | 0,93 en fondo coincidente | 12 | media de la muestra 8,9 |
| `halo-desenfoque` (blur 20, borde transparente) | 4,6 | 23 | emulado; por Python sobre las capturas del lienzo el mejor modelo da 6,4 |
| `resplandor-desenfoque` (blur 26) | 4,7 | 24 | emulado; idem 6,5 |

La columna «fuera» de las hojas de vidrio y filtros no significa nada (son las esquinas del rectángulo con el texto de fondo, que difiere por glifos). El texto del fondo (`cm-txt`, Inter 700 de 13 px con corte en cualquier carácter) da 1,9/255 de media en la hoja de filtros; las líneas coinciden salvo 1–2 cortes distintos.

### Hoja entera (con rótulos y cabecera; error medio de `compare.cjs`, lienzo 3× → 1×)
caída 1,2–1,65 · anillos 0,76–1,83 · interiores 1,1–2,1 · resplandores 1,5–2,1 · nula 0,56 · texto 1,32 · degradados 2,2–5,7 (los rótulos largos de los degradados parten distinto) · filtros 8,7 · vidrio 94 (Haze sin desenfoque en la JVM: DESV-100).

## Decisiones y trampas
- `Modifier.shadow` (elevación) NO se usa salvo en Android < 9: no admite desenfoque ni extensión arbitrarios y un lienzo de software no lo pinta. `BlurMaskFilter` se pinta igual en hardware y en software.
- API: `kpknShadow(preset | spec, shape, color)`, `kpknRing`, `kpknInset`, `drawKpknShadow(spec, shape)` (dibujos propios), `KpknShadowedText`, `kpknGradient(preset | spec, shape, args)`, `kpknGlassPreset` / `kpknGlassOrFallback(preset = …)`, `kpknFilter`, `kpknBackdropAdjust`. El VALOR EXACTO de una fila: `preset.spec.copyLayer(i) { it.copy(...) }` o `KpknShadowSpec.of(...)`.
- Los presets NO se unifican (decisión del orquestador): `menu`/`aviso`/`popover`, `flotante`/`caida-8-28`, `dialogo-1`/`dialogo-2`… son casos distintos.
- Ciclos usados: 2 de 5 (la primera corrida ya bajaba del objetivo; la segunda arregló el vidrio-barra y midió a 1×).
- Comparaciones para enseñar al usuario (`paridad/capturas/`): `DEPTH-hoja.png`, `DEPTH-isla-vidrio.png`, `DEPTH-modal.png` (niveles ×7 para verlas; las sombras sobre #0B0B0C difieren 1–2 niveles), `DESV-100.png` (vidrio), `DESV-101.png` (filtros).
