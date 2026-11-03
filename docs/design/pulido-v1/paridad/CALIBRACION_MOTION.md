# Calibración del movimiento (Fase 0.3 · `ui/design/motion`)

Pasada del 30-oct-2026, rama `pulido/mo` (worktree `C:\kw\pu-mo`). Todo lo de abajo son números medidos fotograma a fotograma contra el lienzo; no hay ningún «1:1» declarado: lo que no baja del umbral está en `DESVIACIONES.md` (DESV-200 a DESV-203).

## Cómo se midió
- **App**: `MotionFrameRenderTest` (Robolectric, 360 × 880 dp a xxhdpi = 1080 × 2640 px). La hoja `KpknMotionSheet(id, timeMs)` se dibuja en el instante fijado a mano (reloj de cuadros parado con `mainClock.autoAdvance = false`) y la prueba guarda un PNG por hoja e instante: **274 fotogramas** más 5 de las ondas. Los instantes son el 0, 25, 50, 75 y 100 % de la duración de cada elemento (`kpknMotionSheetInstants`): de la ida en las rodadas (más la mitad de la vuelta), desde su retardo en las familias, del periodo en los bucles y de 1100 ms en la cuenta. Una segunda prueba comprueba que el reloj de cuadros real sigue a `advanceTimeBy` (±1 cuadro).
- **Lienzo**: la misma hoja con `?solo=1&scene=<id>&t=<ms>` (pausa todas las animaciones CSS en `currentTime = t` y mueve el reloj virtual de `requestAnimationFrame`), capturada con Chrome headless a ×3 (1080 × 2640) en los mismos 274 instantes.
- **Números**: (1) error medio por región (0–255, máximo de canal, la misma métrica de `compare.cjs`) a 1080 × 2640; (2) posición del círculo de las rodadas (centroide del píxel crema) en dp; (3) `node tools/compare.cjs <lienzo> <app>` sobre las 274 parejas (las dos imágenes se escalan a 360 × 880); las imágenes lienzo | app | diferencias quedaron en `C:\kw\render-mo\cmpout\`. Umbral del encargo: error medio ≤ 2/255 y círculo ±1 dp.
- **Aviso sobre `tools/shots.cjs --t`**: en 19 de las 274 capturas de las hojas de rodadas (t = 0 de duraciones-1, curvas-2 y duraciones-2, y t ≥ 900 ms de curvas-1 y duraciones-1) varias filas NO quedan congeladas (el círculo sale en una fase cualquiera) y compararlas da errores de hasta 23/255 que son del lienzo, no de la app. Por eso los números de `compare.cjs` de este documento usan las capturas con Chrome ×3 (mismo mecanismo `?t=` de `boot.js`) que, además, coinciden con la fórmula: el círculo de la app y el del lienzo difieren menos de 0,04 dp. Conviene revisar `shots.cjs` antes de la puerta 3 de los demás paquetes.
- Dos trampas de la medida, ya resueltas en la prueba: con el reloj parado, lo que se lee al componer (anchos, color, sombras) necesita varios cuadros alternados con `waitForIdle` para llegar al dibujo (sin eso, un fotograma sale con el estado del anterior); y una capa con opacidad y escala mayor que 1 se recorta en Robolectric al tamaño sin escalar (la hoja de `onda` usa una capa de 90 dp; no afecta a la app).

## Resultado en una línea
- **Rodadas (curvas y duraciones)**: pista ≤ 0,20/255; el círculo coincide a **≤ 0,04 dp** en las 24 + 23 filas, ida, espera y vuelta, con los tres muelles, `steps(1)` y las curvas con rebasado.
- **Familias**: 32 de 33 con el estadio ≤ 1,94/255 (las de capa y transformación, ≤ 0,4). Abierta: `revelado` (DESV-200).
- **Cuenta de cifras**: los valores coinciden en los 5 instantes; la hoja entera queda en 8,8/255 por el texto (DESV-201).
- **Campo de ondas**: 5 de 6 estados ≤ 2,0/255 en 5 instantes; «Sobre la zona» 5,6 (DESV-203).
- **Texto**: el suelo de las hojas enteras es 1,2–2,8/255 por la rasterización del texto (Chrome frente a Skia), no por el movimiento (DESV-202).
- Pruebas puras (`KpknMotionEngineTest`, 13, todas verdes): curvas contra un resolutor bezier independiente, curva en CADA intervalo (`lv-pop` al 30 % y 80 %), curva propia de un fotograma, retardo, relleno, repeticiones, dirección, movimiento reducido, periodo exacto de los bucles, formato de la cuenta y 15 valores del campo de ondas sacados con Node de `cvWaveD`.

## Tablas

### Curvas y duraciones (hojas `cm-mov-curvas-*`, `cm-mov-duraciones-*`)

Error de la pista = error medio (0–255, máximo de canal) de la franja x 56–304, 44 dp de alto de cada fila; posición = diferencia máxima del centro del círculo (dp) entre lienzo y app en todos los instantes medidos de esa fila.

| Hoja | Fila | Curva | Duración | Instantes | Pista máx. | Pista media | Posición máx. (dp) |
|---|---|---|---|---|---|---|---|
| curvas-1 | 1 | estandar | 200 ms | 38 | 0.19 | 0.09 | 0.03 |
| curvas-1 | 2 | estandar | 240 ms | 38 | 0.19 | 0.11 | 0.03 |
| curvas-1 | 3 | suave | 200 ms | 38 | 0.20 | 0.10 | 0.03 |
| curvas-1 | 4 | suave | 160 ms | 38 | 0.20 | 0.10 | 0.03 |
| curvas-1 | 5 | lineal | 6000 ms | 38 | 0.09 | 0.08 | 0.00 |
| curvas-1 | 6 | lineal | 800 ms | 38 | 0.20 | 0.09 | 0.03 |
| curvas-1 | 7 | trazo | 1600 ms | 38 | 0.20 | 0.14 | 0.04 |
| curvas-1 | 8 | trazo | 1400 ms | 38 | 0.20 | 0.14 | 0.04 |
| curvas-1 | 9 | salida | 1000 ms | 38 | 0.20 | 0.13 | 0.04 |
| curvas-1 | 10 | salida | 200 ms | 38 | 0.20 | 0.09 | 0.03 |
| curvas-1 | 11 | resorte | 320 ms | 38 | 0.20 | 0.10 | 0.03 |
| curvas-1 | 12 | resorte | 160 ms | 38 | 0.17 | 0.09 | 0.02 |
| curvas-2 | 1 | vaiven | 2400 ms | 34 | 0.20 | 0.16 | 0.03 |
| curvas-2 | 2 | vaiven | 700 ms | 34 | 0.20 | 0.13 | 0.03 |
| curvas-2 | 3 | parpadeo | 1000 ms | 34 | 0.08 | 0.08 | 0.00 |
| curvas-2 | 4 | cae | 200 ms | 34 | 0.19 | 0.10 | 0.02 |
| curvas-2 | 5 | cae | 240 ms | 34 | 0.20 | 0.11 | 0.02 |
| curvas-2 | 6 | acelera | 240 ms | 34 | 0.20 | 0.10 | 0.04 |
| curvas-2 | 7 | acelera | 200 ms | 34 | 0.20 | 0.09 | 0.02 |
| curvas-2 | 8 | material | 320 ms | 34 | 0.19 | 0.11 | 0.03 |
| curvas-2 | 9 | resorte-fuerte | 600 ms | 34 | 0.20 | 0.11 | 0.03 |
| curvas-2 | 10 | resorte-fuerte | 700 ms | 34 | 0.20 | 0.12 | 0.03 |
| curvas-2 | 11 | ruta | 1000 ms | 34 | 0.20 | 0.14 | 0.03 |
| curvas-2 | 12 | resorte-suave | 400 ms | 34 | 0.19 | 0.11 | 0.03 |
| duraciones-1 | 1 | estandar | 80 ms | 47 | 0.20 | 0.09 | 0.04 |
| duraciones-1 | 2 | estandar | 90 ms | 47 | 0.19 | 0.08 | 0.03 |
| duraciones-1 | 3 | estandar | 120 ms | 47 | 0.19 | 0.10 | 0.03 |
| duraciones-1 | 4 | estandar | 140 ms | 47 | 0.19 | 0.10 | 0.03 |
| duraciones-1 | 5 | estandar | 160 ms | 47 | 0.19 | 0.10 | 0.03 |
| duraciones-1 | 6 | estandar | 200 ms | 47 | 0.19 | 0.11 | 0.03 |
| duraciones-1 | 7 | estandar | 240 ms | 47 | 0.20 | 0.11 | 0.03 |
| duraciones-1 | 8 | estandar | 280 ms | 47 | 0.20 | 0.12 | 0.03 |
| duraciones-1 | 9 | estandar | 320 ms | 47 | 0.19 | 0.12 | 0.03 |
| duraciones-1 | 10 | estandar | 400 ms | 47 | 0.20 | 0.13 | 0.03 |
| duraciones-1 | 11 | estandar | 460 ms | 47 | 0.20 | 0.13 | 0.04 |
| duraciones-1 | 12 | estandar | 520 ms | 47 | 0.20 | 0.13 | 0.03 |
| duraciones-2 | 1 | estandar | 600 ms | 42 | 0.19 | 0.11 | 0.03 |
| duraciones-2 | 2 | estandar | 700 ms | 42 | 0.20 | 0.12 | 0.03 |
| duraciones-2 | 3 | estandar | 800 ms | 42 | 0.20 | 0.11 | 0.02 |
| duraciones-2 | 4 | estandar | 1000 ms | 42 | 0.19 | 0.12 | 0.03 |
| duraciones-2 | 5 | estandar | 1200 ms | 42 | 0.20 | 0.13 | 0.03 |
| duraciones-2 | 6 | estandar | 1300 ms | 42 | 0.20 | 0.14 | 0.03 |
| duraciones-2 | 7 | estandar | 1400 ms | 42 | 0.20 | 0.13 | 0.03 |
| duraciones-2 | 8 | estandar | 1600 ms | 42 | 0.20 | 0.13 | 0.03 |
| duraciones-2 | 9 | estandar | 2400 ms | 42 | 0.20 | 0.13 | 0.03 |
| duraciones-2 | 10 | estandar | 6000 ms | 42 | 0.20 | 0.15 | 0.03 |
| duraciones-2 | 11 | estandar | 15000 ms | 42 | 0.20 | 0.16 | 0.03 |

Peor pista: **0.20/255**; peor posición del círculo: **0.04 dp** (umbral ±1 dp).

### Familias de `@keyframes` (estadio de cada celda, 152 × 64 dp)

Instantes: 0, 25, 50, 75 y 100 % de la duración de la demostración (desde su retardo; en los bucles, del periodo). Error = error medio 0–255 del estadio de la celda (sin el rótulo). «Celda» incluye el rótulo.

| Familia | @keyframes | Duración · curva | Instantes (ms) | Estadio máx. | Estadio medio | Celda máx. |
|---|---|---|---|---|---|---|
| entrada-sube | cv-rise | 420 ms | 0, 105, 210, 315, 420 | 1.72 | 1.28 | 3.49 |
| entrada-derecha | asIn | 220 ms | 0, 55, 110, 165, 220 | 1.71 | 1.19 | 4.73 |
| fundido-entrada | asScrim | 200 ms | 0, 50, 100, 150, 200 | 1.68 | 1.03 | 4.08 |
| hoja-sube | asRise | 420 ms | 0, 105, 210, 315, 420 | 0.13 | 0.06 | 2.03 |
| entrada-baja | lxrise | 240 ms | 0, 60, 120, 180, 240 | 1.69 | 1.24 | 3.59 |
| entrada-implicita | lxrise2 | 500 ms | 0, 700, 825, 950, 1075, 1200 | 1.00 | 0.61 | 4.54 |
| entrada-izquierda | aj-pop | 300 ms | 0, 75, 150, 225, 300 | 1.76 | 1.30 | 5.04 |
| aviso | ed-delta | 1300 ms | 0, 325, 650, 975, 1300 | 1.42 | 0.85 | 2.18 |
| fundido-salida | ed-fadeout | 200 ms | 0, 50, 100, 150, 200 | 1.56 | 0.61 | 3.83 |
| hoja-baja | ed-down | 240 ms | 0, 60, 120, 180, 240 | 0.10 | 0.05 | 2.04 |
| rebote | lv-pop | 200 ms | 0, 50, 100, 150, 200 | 0.18 | 0.13 | 1.56 |
| aparece-escala | ed-bump | 240 ms | 0, 60, 120, 180, 240 | 0.16 | 0.11 | 3.26 |
| rebote-aparece | cv-pop | 350 ms | 0, 88, 175, 263, 350 | 0.21 | 0.10 | 3.24 |
| asentar | cv-cn | 900 ms | 0, 225, 450, 675, 900 | 1.23 | 0.27 | 2.35 |
| giro | lxspin | 800 ms ∞ | 0, 200, 400, 600, 800 | 0.08 | 0.06 | 1.16 |
| pulso | blink | 1000 ms ∞ | 0, 250, 500, 750, 1000 | 0.00 | 0.00 | 1.45 |
| brillo-barrido | asSh | 1400 ms ∞ | 0, 350, 700, 1050, 1400 | 0.03 | 0.02 | 3.02 |
| latido | cv-beat | 1100 ms ∞ | 0, 275, 550, 825, 1100 | 0.23 | 0.16 | 1.77 |
| pulso-anillo | tcpulse | 1200 ms ∞ | 0, 300, 600, 900, 1200 | 0.04 | 0.03 | 2.36 |
| flotar | fl | 400 ms | 0, 100, 200, 300, 400 | 0.08 | 0.03 | 1.35 |
| dibujo-trazo | drawln | 1600 ms | 0, 400, 800, 1200, 1600 | 0.23 | 0.09 | 3.06 |
| crece-x | wrbar | 6000 ms | 0, 1500, 3000 | 0.11 | 0.09 | 1.90 |
| destello | asFlash | 600 ms | 0, 150, 300, 450, 600 | 1.72 | 1.42 | 2.98 |
| barrido-ancho | lv-wipe | 550 ms | 0, 138, 275, 413, 550 | 0.31 | 0.23 | 3.03 |
| crece-y | cv-grow | 600 ms | 0, 150, 300, 450, 600 | 0.27 | 0.16 | 2.26 |
| onda | cv-rg | 900 ms | 0, 225, 450, 675, 900 | 0.37 | 0.21 | 1.33 |
| barrido-linea | tcsweep | 2600 ms ∞ | 0, 650, 1300, 1950, 2600 | 0.02 | 0.01 | 2.78 |
| despliegue-fila | ed-fresh | 500 ms | 0, 125, 250, 375, 500 | 1.94 | 1.40 | 5.57 |
| ignicion | cv-ignite | 800 ms | 0, 1100, 1300, 1500, 1700, 1900 | 0.12 | 0.03 | 1.70 |
| onda-anillo | ed-ring | 460 ms | 0, 115, 230, 345, 460 | 0.17 | 0.08 | 2.51 |
| revelado | tcvid | 1100 ms | 0, 275, 550, 825, 1100 | 13.35 | 4.52 | 8.84 |
| caida | fall | 400 ms | 0, 100, 200, 300, 400 | 0.01 | 0.01 | 1.35 |
| giro-entrada | popin | 400 ms | 0, 100, 200, 300, 400 | 1.46 | 0.88 | 3.16 |

### Hojas enteras (360 × 880 dp)

«Propio» = error medio a 1080 × 2640 con la métrica de `compare.cjs`; «oficial» = `node tools/compare.cjs <captura 1x de shots.cjs --t> <app>` (360 × 880): % de píxeles distintos y error medio. Todo el texto es de otra rasterización (Chrome frente a Skia/Android), de ahí el suelo de 1–3.

| Hoja | Fotogramas | Propio medio | Propio máx. | Oficial: % px (media / máx.) | Oficial: error medio (media / máx.) |
|---|---|---|---|---|---|
| cm-mov-bucles | 21 | 1.50 | 1.53 | 1.34 / 1.34 | 1.27 / 1.30 |
| cm-mov-cuenta | 5 | 8.77 | 9.86 | 5.66 / 6.22 | 8.39 / 9.48 |
| cm-mov-curvas-1 | 38 | 2.32 | 2.34 | 2.38 / 2.38 | 1.94 / 1.95 |
| cm-mov-curvas-2 | 34 | 2.81 | 2.83 | 2.86 / 2.86 | 2.33 / 2.34 |
| cm-mov-duraciones-1 | 47 | 2.39 | 2.42 | 2.45 / 2.45 | 1.99 / 2.00 |
| cm-mov-duraciones-2 | 42 | 2.15 | 2.16 | 2.19 / 2.19 | 1.80 / 1.80 |
| cm-mov-entradas | 29 | 2.82 | 2.85 | 2.54 / 2.59 | 2.42 / 2.45 |
| cm-mov-pulsaciones | 17 | 1.46 | 1.56 | 1.20 / 1.28 | 1.23 / 1.33 |
| cm-mov-trazos-1 | 36 | 2.76 | 3.01 | 2.48 / 2.90 | 2.33 / 2.56 |
| cm-mov-trazos-2 | 5 | 1.10 | 1.12 | 0.93 / 0.96 | 0.91 / 0.93 |

### Campo de ondas (`cv-ondas`, tarjetas de 328 × 112 dp, 12 líneas)

Error medio 0–255 de la tarjeta (sin las esquinas ni el rótulo; la captura del lienzo se alinea 1 dp porque el marco del teléfono tiene 1 dp de borde).

| Instante (ms) | Reposo | Calentamiento | Trabajo | Recuperación | Calma | Sobre la zona |
|---|---|---|---|---|---|---|
| 0 | 0.71 | 0.78 | 1.89 | 0.80 | 0.53 | 5.74 |
| 500 | 0.71 | 0.78 | 1.99 | 0.82 | 0.53 | 5.53 |
| 1000 | 0.71 | 0.78 | 1.70 | 0.82 | 0.53 | 5.62 |
| 2000 | 0.71 | 0.77 | 1.67 | 0.79 | 0.52 | 5.68 |
| 3000 | 0.71 | 0.79 | 1.80 | 0.74 | 0.48 | 5.74 |

## Componentes entregados (`ui/design/motion`)
| Pieza | Qué es | Cómo se comprobó |
|---|---|---|
| `KpknMotion` | 14 curvas (coordenadas exactas, palabras clave de CSS con su bezier), 23 duraciones, 11 retardos, `kpknTween`, `kpknSpringTween` | pruebas puras + las 47 filas de rodadas |
| `KpknKeyframes` / `KpknAnimation` | motor con la semántica de CSS: curva en cada intervalo, curva por fotograma, retardo, relleno, repeticiones, dirección, fotogramas implícitos 0 %/100 % | pruebas puras + 33 familias en fotogramas |
| `KpknFamilies` | los 33 `@keyframes` canónicos con su demostración | hojas `cm-mov-entradas/pulsaciones/bucles/trazos-*` |
| `Modifier.kpknAnimate` / `kpknPose` / `rememberKpknPose` / `rememberKpknPulse` | reproducción con el reloj de cuadros (o con un instante a mano), bucles con periodo exacto | `animationClockFollowsTheTestClock` + hojas |
| `animateKpknFloatAsState` / `animateKpknColorAsState` | `transition` (el color se mezcla como CSS) | solo compila; sin hoja propia |
| `kpknEnter()` / `kpknExit()` | entradas y salidas estándar (`AnimatedVisibility`) | solo compila; las familias que usan están medidas |
| `Modifier.kpknPress` | escala de pulsación `:active` (0,97 en 90 ms, vuelta en 160 ms; variante suave 0,96) | solo compila; sin hoja propia |
| `KpknCountUp` | `[data-count]`: 1100 ms, `1 − (1 − t)³`, coma decimal y punto de millar | hoja `cm-mov-cuenta` |
| `KpknWaveField` | campo de ondas del cardio y de las portadas (mismo algoritmo que `cvWaveD`) | hoja `cv-ondas` y 15 valores de Node |
| `KpknMotionSheet` | las hojas `cm-mov-*` | todo lo anterior |

Pendiente de la pasada B/C: conectar `ShadowAlpha`, `ShadowSpreadDp` y `ShadowBlurDp` (ignición, onda y pulso de anillo) con los presets de `ui/design/depth`; en la hoja se dibujan con un círculo (`drawBehind`). `kpknPress`, `kpknEnter/kpknExit` y las transiciones no tienen hoja de fotogramas propia: las mide el paquete que las usa. Las descripciones de `resorte-suave` («rebasa un 5 %») y `resorte` («15 %») vienen del catálogo; la curva real rebasa un 0,4 % y un 8 % (y `resorte-fuerte` un 11 %).


## Desviaciones abiertas
- **DESV-200** `revelado` (`tcvid`): el desenfoque no sale en Robolectric (no pinta `RenderEffect`); brillo y escala sí. Error 13,4/255 en t = 0 y 0,4 en el final.
- **DESV-201** cuenta de cifras: Inter ExtraBold (800) no está en la app (solo Regular/Medium/SemiBold) y los números caen unos 3–4 dp más abajo; los valores y los tiempos coinciden.
- **DESV-202** texto de las hojas: rasterización distinta; suelo de 1,2–2,8/255 en hojas enteras (la pista del círculo y los estadios sin texto, ≤ 0,2).
- **DESV-203** ondas «Sobre la zona» (aspereza 0,5): 5,6/255 con las fases iguales (la suma de píxeles es 1,006 veces la del lienzo); son los trazos casi verticales.
