# Cimientos · grupos de presets de profundidad y movimiento

Generado por `node tools/tokens-group.cjs` desde `paridad/_inventory.json` (el CSS real del lienzo aprobado). **No se inventó ningún valor**: cada preset tiene UN valor canónico exacto, el del miembro más usado, tal cual; los demás miembros se parecen a él dentro de la tolerancia. «Filas» = filas del inventario de paridad (`paridad/<prefijo>.md`) que cubre el preset. La hoja de calibración donde se dibuja cada preset está en la columna «Hoja» (escena del lienzo y posición de la muestra en dp desde la esquina superior izquierda de la hoja de 360 × 880).

## Resumen

| Familia | Valores distintos | Presets |
|---|---|---|
| Sombras (box-shadow) | 97 | 65 |
| Sombras de texto | 4 | 3 |
| Vidrio (backdrop-filter) | 11 | 7 |
| Filtros (filter) | 8 | 5 |
| Curvas | 15 | 14 |
| Duraciones | 54 | 23 |
| Retardos | 20 | 11 |
| Degradados | 46 | 43 |
| @keyframes | 110 | 33 |

## Tolerancia máxima realmente aplicada

- Sombras: x ±0 dp, y ±2 dp, desenfoque ±4 dp, extensión ±1 dp, alfa ±0.07, canal ±12 (tope: 2 / 2 / 4 / 1 / 0,08 / 12).
- Vidrio: desenfoque ±4 dp, saturación ±0, brillo ±0 (tope: 4 / 0,15 / 0,15). Filtros: desenfoque ±0 dp, saturación ±0.1, brillo ±0.1.
- Curvas: coordenada ±0.02 (tope 0,05).
- Duraciones: hasta 11.1 % (tope 12 %); retardos: hasta 11.1 %.
- Degradados: ángulo ±0°, posición ±8 puntos, alfa ±0, canal ±10 (tope 5 / 8 / 0,08 / 12).

## Reglas y decisiones de criterio

- **Canónico = el más usado, tal cual.** Se ordenan los valores distintos de más a menos usados (empate: el que aparece antes en el CSS); el primero sin grupo es el canónico del grupo y absorbe a todo valor sin grupo que esté dentro de la tolerancia *de él*. No hay cadenas (A≈B≈C) ni promedios.
- **Sombras:** mismas capas (mismo orden e igual `inset`). El color dinámico del módulo (`var(--c)`, `var(--zc)`, `var(--wc)`: lo fija cada escena) solo equivale a otro dinámico; su hoja usa un color de muestra (`--c` cardio `#8fb2ff`, `--zc` `#87C9A5`). Una sombra con alfa 0 en todas sus capas es la sombra «ninguna» (destino de transiciones). `text-shadow` va aparte (`texto-*`).
- **Nombres:** por papel visto en los selectores (`hoja`, `isla-vidrio`, `flotante`, `modal`, `dialogo`, `menu`, `aviso`…) y, si el papel no es claro, por estructura (`caida-<y>-<desenfoque>`, `anillo-<color>-<extensión>`, `interior-<color>-…`, `resplandor-<color>-<desenfoque>`). Si dos presets comparten nombre se numeran por uso (`dialogo-1`, `dialogo-2`).
- **Curvas:** las palabras clave se comparan por su `cubic-bezier` (`ease-in` ≈ `cubic-bezier(.4,0,1,1)`, p. ej.); `steps(1)` es suyo. El nombre y la descripción de cada curva salen de dónde se usa.
- **Duraciones:** escala `90 · 120 · 160 · 200 · 240 · 280 · 320 · 400 · 520 · 700 · 1000 · 1600 · 2400` ms (el 90 se añadió a la lista del encargo para cubrir 0,09 s). Lo que no encaja a ≤ 12 % queda exacto: 80, 140, 460, 600, 800, 1200, 1300, 1400, 6000, 15000 ms. Un retardo `0s` no es preset.
- **Degradados:** se clasifican por papel (`relleno-*`, `velo-*` = de transparente a color, `resplandor-*` = radial de borde transparente, `vidrio-relleno-*`, `brillo-esqueleto`, `franja-*`, `rayas-*`, `rejilla`, `compuesto`). Los de varias capas (`background-image` con comas) solo agrupan si son idénticos.
- **@keyframes:** una entrada por familia estructural; el canónico es el `@keyframes` más usado de la familia y la demostración de la hoja usa su combinación de duración y curva más usada. Los `@keyframes` de las hojas de calibración (`cm-*`) no cuentan.
- **Inventario ampliado:** los atajos `animation:`, `transition:` y `background:` escritos con `var()` dejaban vacíos sus valores en el CSS calculado y el inventario los perdía (+32 animaciones, +25 transiciones y +5 degradados; entre ellos el muelle `cubic-bezier(.3,1.5,.5,1)`). `src/boot.js` ahora guarda su texto y `tools/inventory.cjs` los resuelve con las variables del CSS.

## Sombras · caídas (28)

| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros que agrupa (filas) | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|---|
| **hoja** | caida | `rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset` | 11 | `rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset` (10)<br>`rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.07) 0px 1px inset` (1) | alfa ±0.007 | cm-som-caida-2 · x 110, y 132 · 140×84 |
| **isla-vidrio** | caida | `rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset` | 15 | `rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset` (7)<br>`rgba(0, 0, 0, 0.6) 0px 10px 30px, rgba(255, 255, 255, 0.07) 0px 1px inset` (5)<br>`rgba(0, 0, 0, 0.667) 0px 10px 30px, rgba(255, 255, 255, 0.094) 0px 1px inset` (2)<br>`rgba(0, 0, 0, 0.667) 0px 10px 30px, rgba(255, 255, 255, 0.08) 0px 1px inset` (1) | y ±2 dp, desenfoque ±2 dp, alfa ±0.067 | cm-som-caida-2 · x 110, y 288 · 140×84 |
| **barra-vidrio** | caida | `rgba(0, 0, 0, 0.467) 0px 8px 24px, rgba(255, 255, 255, 0.11) 0px 1px inset` | 5 | (solo él) | idéntico | cm-som-caida-2 · x 110, y 450 · 140×84 |
| **nota** | caida | `rgba(0, 0, 0, 0.533) 0px 4px 12px` | 2 | (solo él) | idéntico | cm-som-caida-2 · x 110, y 608 · 140×84 |
| **flotante** | caida | `rgba(0, 0, 0, 0.667) 0px 8px 28px` | 5 | `rgba(0, 0, 0, 0.667) 0px 8px 28px` (2)<br>`rgba(0, 0, 0, 0.667) 0px 10px 28px` (1)<br>`rgba(0, 0, 0, 0.733) 0px 10px 28px` (1)<br>`rgba(0, 0, 0, 0.667) 0px 10px 30px` (1) | y ±2 dp, desenfoque ±2 dp, alfa ±0.066 | cm-som-caida-3 · x 110, y 94 · 140×84 |
| **modal** | caida | `rgba(0, 0, 0, 0.8) 0px 20px 60px` | 3 | `rgba(0, 0, 0, 0.8) 0px 20px 60px` (2)<br>`rgba(0, 0, 0, 0.867) 0px 20px 60px` (1) | alfa ±0.067 | cm-som-caida-3 · x 110, y 276 · 140×84 |
| **historia** | caida | `rgba(0, 0, 0, 0.8) 0px 20px 50px, rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` | 2 | `rgba(0, 0, 0, 0.8) 0px 20px 50px, rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` (1)<br>`rgba(0, 0, 0, 0.8) 0px 18px 50px, rgba(255, 255, 255, 0.11) 0px 0px 0px 1px inset` (1) | y ±2 dp, alfa ±0.03 | cm-som-caida-3 · x 110, y 492 · 140×84 |
| **pie-sube** | caida | `rgba(0, 0, 0, 0.733) 0px -10px 30px` | 1 | (solo él) | idéntico | cm-som-caida-3 · x 110, y 708 · 140×84 |
| **menu** | caida | `rgba(0, 0, 0, 0.8) 0px 14px 40px` | 2 | `rgba(0, 0, 0, 0.8) 0px 14px 40px` (1)<br>`rgba(0, 0, 0, 0.867) 0px 14px 40px` (1) | alfa ±0.067 | cm-som-caida-4 · x 110, y 100 · 140×84 |
| **aviso** | caida | `rgba(0, 0, 0, 0.8) 0px 12px 34px` | 3 | `rgba(0, 0, 0, 0.8) 0px 12px 34px` (1)<br>`rgba(0, 0, 0, 0.8) 0px 14px 34px` (1)<br>`rgba(0, 0, 0, 0.8) 0px 10px 30px` (1) | y ±2 dp, desenfoque ±4 dp | cm-som-caida-4 · x 110, y 282 · 140×84 |
| **dialogo-1** | caida | `rgba(0, 0, 0, 0.933) 0px 24px 70px, rgba(255, 255, 255, 0.08) 0px 1px inset` | 1 | (solo él) | idéntico | cm-som-caida-4 · x 110, y 480 · 140×84 |
| **caida-4-18** | caida | `rgba(0, 0, 0, 0.533) 0px 4px 18px` | 1 | (solo él) | idéntico | cm-som-caida-4 · x 110, y 700 · 140×84 |
| **levantado** | caida | `rgba(0, 0, 0, 0.867) 0px 24px 48px, rgba(255, 255, 255, 0.15) 0px 0px 0px 1px` | 1 | (solo él) | idéntico | cm-som-caida-5 · x 110, y 98 · 140×84 |
| **popover** | caida | `rgba(0, 0, 0, 0.867) 0px 18px 44px` | 2 | `rgba(0, 0, 0, 0.867) 0px 18px 44px` (1)<br>`rgba(0, 0, 0, 0.8) 0px 18px 40px` (1) | desenfoque ±4 dp, alfa ±0.067 | cm-som-caida-5 · x 110, y 302 · 140×84 |
| **asa-deslizador** | caida | `rgba(0, 0, 0, 0.667) 0px 2px 8px` | 1 | (solo él) | idéntico | cm-som-caida-1 · x 24, y 72 · 140×84 |
| **caida-10-22** | caida | `rgba(0, 0, 0, 0.667) 0px 10px 22px` | 1 | (solo él) | idéntico | cm-som-caida-5 · x 110, y 490 · 140×84 |
| **caida-6-16-capas-1** | caida | `rgba(0, 0, 0, 0.6) 0px 6px 16px, rgba(255, 255, 255, 0.133) 0px 0px 0px 1px` | 1 | (solo él) | idéntico | cm-som-caida-5 · x 110, y 648 · 140×84 |
| **caida-6-16-capas-2** | caida | `rgba(0, 0, 0, 0.6) 0px 6px 16px, rgba(255, 255, 255, 0.133) 0px 0px 0px 1px, rgb(244, 154, 110) 0px 2px 0px` | 1 | (solo él) | idéntico | cm-som-caida-6 · x 110, y 94 · 140×84 |
| **caida-2-8-capas** | caida | `rgba(0, 0, 0, 0.667) 0px 2px 8px, rgb(17, 17, 17) 0px 0px 0px 4px` | 1 | (solo él) | idéntico | cm-som-caida-1 · x 196, y 72 · 140×84 |
| **caida-8-20-capas** | caida | `rgba(0, 0, 0, 0.8) 0px 8px 20px, rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` | 1 | (solo él) | idéntico | cm-som-caida-6 · x 110, y 242 · 140×84 |
| **caida-10-28-capas** | caida | `rgba(0, 0, 0, 0.7) 0px 10px 28px, rgba(0, 0, 0, 0.5) 0px 2px 6px` | 1 | (solo él) | idéntico | cm-som-caida-6 · x 110, y 396 · 140×84 |
| **caida-4-14** | caida | `rgba(0, 0, 0, 0.35) 0px 4px 14px` | 1 | (solo él) | idéntico | cm-som-caida-6 · x 110, y 560 · 140×84 |
| **caida-16-60** | caida | `rgba(0, 0, 0, 0.867) 0px 16px 60px` | 1 | (solo él) | idéntico | cm-som-caida-7 · x 110, y 118 · 140×84 |
| **dialogo-2** | caida | `rgba(0, 0, 0, 0.867) 0px 24px 60px, rgba(255, 255, 255, 0.063) 0px 1px inset` | 1 | (solo él) | idéntico | cm-som-caida-7 · x 110, y 336 · 140×84 |
| **caida-18-50** | caida | `rgba(0, 0, 0, 0.867) 0px 18px 50px` | 1 | (solo él) | idéntico | cm-som-caida-7 · x 110, y 558 · 140×84 |
| **dialogo-vidrio** | caida | `rgba(255, 255, 255, 0.11) 0px 0px 0px 1px, rgb(0, 0, 0) 0px 20px 60px` | 1 | (solo él) | idéntico | cm-som-caida-8 · x 110, y 114 · 140×84 |
| **caida-8-28** | caida | `rgba(0, 0, 0, 0.8) 0px 8px 28px` | 1 | (solo él) | idéntico | cm-som-caida-8 · x 110, y 320 · 140×84 |
| **caida-24-70** | caida | `rgba(0, 0, 0, 0.933) 0px 24px 70px` | 1 | (solo él) | idéntico | cm-som-caida-8 · x 110, y 508 · 140×84 |

## Sombras · anillos (13)

| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros que agrupa (filas) | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|---|
| **anillo-actual** | anillo | `0 0 0 2px var(--ink)` | 4 | `0 0 0 2px var(--ink)` (3)<br>`0 0 0 1.5px var(--ink)` (1) | extensión ±0.5 dp | cm-som-anillo-1 · x 24, y 72 · 140×84 |
| **anillo-doble-actual** | anillo | `rgb(0, 0, 0) 0px 0px 0px 2px, rgba(242, 238, 230, 0.667) 0px 0px 0px 3px` | 2 | (solo él) | idéntico | cm-som-anillo-1 · x 196, y 72 · 140×84 |
| **aureola-foco** | anillo | `rgba(242, 238, 230, 0.08) 0px 0px 0px 4px` | 3 | `rgba(242, 238, 230, 0.08) 0px 0px 0px 4px` (2)<br>`rgba(242, 238, 230, 0.047) 0px 0px 0px 4px` (1) | alfa ±0.033 | cm-som-anillo-1 · x 24, y 222 · 140×84 |
| **corte-hoja** | anillo | `rgb(27, 27, 29) 0px 0px 0px 2px` | 6 | `rgb(27, 27, 29) 0px 0px 0px 2px` (2)<br>`rgb(17, 17, 17) 0px 0px 0px 2px` (2)<br>`0 0 0 2px var(--as-mod)` (1)<br>`rgb(17, 17, 17) 0px 0px 0px 1px` (1) | extensión ±1 dp, canal ±12 | cm-som-anillo-1 · x 196, y 222 · 140×84 |
| **borde-exterior** | anillo | `rgba(255, 255, 255, 0.11) 0px 0px 0px 1px` | 3 | `rgba(255, 255, 255, 0.11) 0px 0px 0px 1px` (2)<br>`rgba(255, 255, 255, 0.15) 0px 0px 0px 1px` (1) | alfa ±0.04 | cm-som-anillo-1 · x 24, y 372 · 140×84 |
| **anillo-negro-4** | anillo | `rgb(0, 0, 0) 0px 0px 0px 4px` | 2 | `rgb(0, 0, 0) 0px 0px 0px 4px` (1)<br>`rgb(0, 0, 0) 0px 0px 0px 3px` (1) | extensión ±1 dp | cm-som-anillo-1 · x 196, y 372 · 140×84 |
| **anillo-energia-1p5** | anillo | `0 0 0 1.5px var(--ene)` | 1 | (solo él) | idéntico | cm-som-anillo-1 · x 24, y 522 · 140×84 |
| **anillo-foco** | anillo | `rgba(242, 238, 230, 0.333) 0px 0px 0px 2px` | 2 | `rgba(242, 238, 230, 0.333) 0px 0px 0px 2px` (1)<br>`rgba(242, 238, 230, 0.333) 0px 0px 0px 1.5px` (1) | extensión ±0.5 dp | cm-som-anillo-1 · x 196, y 522 · 140×84 |
| **anillo-blanco-3-suave** | anillo | `rgba(255, 255, 255, 0.08) 0px 0px 0px 3px` | 1 | (solo él) | idéntico | cm-som-anillo-1 · x 24, y 672 · 140×84 |
| **anillo-aviso** | anillo | `rgba(247, 207, 115, 0.15) 0px 0px 0px 3px` | 2 | `rgba(247, 207, 115, 0.15) 0px 0px 0px 3px` (1)<br>`rgba(247, 207, 115, 0.19) 0px 0px 0px 3px` (1) | alfa ±0.04 | cm-som-anillo-1 · x 196, y 672 · 140×84 |
| **anillo-hecho** | anillo | `rgba(67, 209, 140, 0.15) 0px 0px 0px 3px` | 1 | (solo él) | idéntico | cm-som-anillo-2 · x 24, y 72 · 140×84 |
| **anillo-error** | anillo | `rgba(255, 155, 146, 0.15) 0px 0px 0px 3px` | 1 | (solo él) | idéntico | cm-som-anillo-2 · x 196, y 72 · 140×84 |
| **anillo-crema-4-suave** | anillo | `rgba(242, 238, 230, 0.2) 0px 0px 0px 4px` | 1 | (solo él) | idéntico | cm-som-anillo-2 · x 24, y 222 · 140×84 |

## Sombras · realces y bordes interiores (13)

| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros que agrupa (filas) | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|---|
| **subrayado-musculo** | interior | `rgb(244, 154, 110) 0px -2px inset` | 10 | (solo él) | idéntico | cm-som-interior-1 · x 24, y 72 · 140×84 |
| **subrayado-hecho** | interior | `rgba(67, 209, 140, 0.533) 0px -2px inset` | 6 | (solo él) | idéntico | cm-som-interior-1 · x 196, y 72 · 140×84 |
| **interior-hecho-1** | interior | `rgba(67, 209, 140, 0.333) 0px 0px 0px 1px inset` | 2 | (solo él) | idéntico | cm-som-interior-1 · x 24, y 222 · 140×84 |
| **borde-interior** | interior | `rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` | 9 | `rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` (2)<br>`rgba(255, 255, 255, 0.15) 0px 0px 0px 1.5px inset` (1)<br>`rgba(255, 255, 255, 0.09) 0px 0px 0px 1px inset` (1)<br>`rgba(255, 255, 255, 0.11) 0px 0px 0px 1.5px inset` (1)<br>`rgba(255, 255, 255, 0.06) 0px 0px 0px 1px inset` (1)<br>`rgba(255, 255, 255, 0.07) 0px 0px 0px 1px inset` (1)<br>`rgba(255, 255, 255, 0.11) 0px 0px 0px 1px inset` (1)<br>`rgba(255, 255, 255, 0.12) 0px 0px 0px 1px inset` (1) | extensión ±0.5 dp, alfa ±0.07 | cm-som-interior-1 · x 196, y 222 · 140×84 |
| **interior-crema-1** | interior | `inset 0 0 0 1px var(--ink)` | 1 | (solo él) | idéntico | cm-som-interior-1 · x 24, y 372 · 140×84 |
| **interior-crema-1p5-1** | interior | `rgba(242, 238, 230, 0.533) 0px 0px 0px 1.5px inset` | 1 | (solo él) | idéntico | cm-som-interior-1 · x 196, y 372 · 140×84 |
| **interior-blanco-1-tenue** | interior | `rgba(255, 255, 255, 0.165) 0px 0px 0px 1px inset` | 3 | `rgba(255, 255, 255, 0.165) 0px 0px 0px 1px inset` (1)<br>`rgba(255, 255, 255, 0.18) 0px 0px 0px 1px inset` (1)<br>`rgba(255, 255, 255, 0.173) 0px 0px 0px 1.5px inset` (1) | extensión ±0.5 dp, alfa ±0.015 | cm-som-interior-1 · x 24, y 522 · 140×84 |
| **interior-zona-1p5** | interior · dinámico | `inset 0 0 0 1.5px var(--c)` | 1 | (solo él) | idéntico | cm-som-interior-1 · x 196, y 522 · 140×84 |
| **borde-interior-fuerte** | interior | `rgba(255, 255, 255, 0.25) 0px 0px 0px 1px inset` | 1 | (solo él) | idéntico | cm-som-interior-1 · x 24, y 672 · 140×84 |
| **interior-peligro-1** | interior | `rgba(255, 155, 146, 0.4) 0px 0px 0px 1px inset` | 1 | (solo él) | idéntico | cm-som-interior-1 · x 196, y 672 · 140×84 |
| **interior-blanco-1p5** | interior | `rgb(255, 255, 255) 0px 0px 0px 1.5px inset` | 1 | (solo él) | idéntico | cm-som-interior-2 · x 24, y 72 · 140×84 |
| **interior-musculo-1** | interior | `rgba(244, 154, 110, 0.333) 0px 0px 0px 1px inset` | 1 | (solo él) | idéntico | cm-som-interior-2 · x 196, y 72 · 140×84 |
| **interior-crema-1p5-2** | interior | `rgba(242, 238, 230, 0.3) 0px 0px 0px 1.5px inset` | 1 | (solo él) | idéntico | cm-som-interior-2 · x 24, y 222 · 140×84 |

## Sombras · resplandores (10)

| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros que agrupa (filas) | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|---|
| **resplandor-crema-12** | resplandor | `rgba(242, 238, 230, 0.333) 0px 0px 12px` | 2 | (solo él) | idéntico | cm-som-resplandor-1 · x 24, y 72 · 140×84 |
| **resplandor-zona-10** | resplandor · dinámico | `0 0 10px color-mix(in srgb,var(--zc) 75%,transparent)` | 1 | (solo él) | idéntico | cm-som-resplandor-1 · x 196, y 72 · 140×84 |
| **resplandor-zona-12** | resplandor · dinámico | `0 0 12px var(--zc)` | 1 | (solo él) | idéntico | cm-som-resplandor-1 · x 24, y 222 · 140×84 |
| **resplandor-zona-28** | resplandor · dinámico | `0 0 28px color-mix(in srgb,var(--wc,var(--zc)) 14%,transparent)` | 1 | (solo él) | idéntico | cm-som-resplandor-2 · x 110, y 102 · 140×84 |
| **resplandor-zona-14-capas** | resplandor · dinámico | `0 0 0 4px #000,0 0 14px var(--c)` | 1 | (solo él) | idéntico | cm-som-resplandor-1 · x 196, y 222 · 140×84 |
| **resplandor-crema-10** | resplandor | `rgba(242, 238, 230, 0.22) 0px 0px 10px` | 1 | (solo él) | idéntico | cm-som-resplandor-1 · x 24, y 372 · 140×84 |
| **resplandor-crema-12-capas** | resplandor | `rgb(242, 238, 230) 0px 0px 0px 1.5px inset, rgba(242, 238, 230, 0.3) 0px 0px 12px` | 1 | (solo él) | idéntico | cm-som-resplandor-1 · x 196, y 372 · 140×84 |
| **resplandor-crema-8** | resplandor | `rgba(242, 238, 230, 0.6) 0px 0px 8px` | 1 | (solo él) | idéntico | cm-som-resplandor-1 · x 24, y 522 · 140×84 |
| **resplandor-crema-18** | resplandor | `rgba(242, 238, 230, 0.333) 0px 0px 18px 2px` | 1 | (solo él) | idéntico | cm-som-resplandor-2 · x 110, y 256 · 140×84 |
| **resplandor-energia-22-capas** | resplandor | `rgba(0, 0, 0, 0.667) 0px 0px 0px 1px, rgba(247, 207, 115, 0.333) 0px 0px 22px, rgba(0, 0, 0, 0.467) 0px 0px 0px 1px inset` | 1 | (solo él) | idéntico | cm-som-resplandor-2 · x 110, y 404 · 140×84 |

## Sombras · sin sombra (1)

| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros que agrupa (filas) | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|---|
| **ninguna** | nula | `rgba(0, 0, 0, 0) 0px 0px 0px 0px` | 2 | `rgba(0, 0, 0, 0) 0px 0px 0px 0px` (1)<br>`transparent 0px 1.5px 0px 0px` (1) | y ±1.5 dp | cm-som-nula-1 · x 24, y 72 · 140×84 |

## Sombras de texto (3)

| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros que agrupa (filas) | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|---|
| **texto-2-26-capas** | caida | `rgb(0, 0, 0) 0px 2px 26px, rgba(0, 0, 0, 0.667) 0px 0px 6px` | 1 | (solo él) | idéntico | cm-som-texto-1 · x 110, y 98 · 140×84 |
| **texto-1-12** | caida | `rgb(0, 0, 0) 0px 1px 12px` | 2 | `rgb(0, 0, 0) 0px 1px 12px` (1)<br>`rgb(0, 0, 0) 0px 1px 8px` (1) | desenfoque ±4 dp | cm-som-texto-1 · x 110, y 252 · 140×84 |
| **texto-resplandor-negro-40** | resplandor | `rgb(0, 0, 0) 0px 0px 40px` | 1 | (solo él) | idéntico | cm-som-texto-1 · x 110, y 411 · 140×84 |

## Vidrio · backdrop-filter (7)

| Preset | Valor canónico (exacto) | Filas | Miembros | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|
| **vidrio-isla** | `blur(22px) saturate(1.5)` | 9 | `blur(22px) saturate(1.5)` (8)<br>`blur(24px) saturate(1.5)` (1) | desenfoque ±2 dp | cm-vid-1 · x 24, y 72 · 312×72 |
| **vidrio-hoja** | `blur(26px) saturate(1.3)` | 5 | (solo él) | idéntico | cm-vid-1 · x 24, y 180 · 312×72 |
| **vidrio-barra** | `blur(18px) saturate(1.8) brightness(0.9)` | 5 | (solo él) | idéntico | cm-vid-1 · x 24, y 288 · 312×72 |
| **vidrio-editor** | `blur(24px) saturate(1.7)` | 2 | (solo él) | idéntico | cm-vid-1 · x 24, y 396 · 312×72 |
| **velo-desenfoque** | `blur(12px)` | 4 | `blur(12px)` (2)<br>`blur(8px)` (1)<br>`blur(10px)` (1) | desenfoque ±4 dp | cm-vid-1 · x 24, y 504 · 312×72 |
| **vidrio-interruptor** | `blur(16px) saturate(1.4)` | 1 | (solo él) | idéntico | cm-vid-1 · x 24, y 612 · 312×72 |
| **velo-suave** | `blur(6px)` | 2 | `blur(6px)` (1)<br>`blur(3px)` (1) | desenfoque ±3 dp | cm-vid-1 · x 24, y 720 · 312×72 |

## Filtros · filter (5)

| Preset | Valor canónico (exacto) | Filas | Miembros | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|
| **foto-apagada** | `saturate(0.8)` | 1 | (solo él) | idéntico | cm-vid-filtros-1 · x 24, y 72 · 140×84 |
| **halo-desenfoque** | `blur(20px)` | 1 | (solo él) | idéntico | cm-vid-filtros-1 · x 196, y 72 · 140×84 |
| **resplandor-desenfoque** | `blur(26px)` | 1 | (solo él) | idéntico | cm-vid-filtros-1 · x 24, y 222 · 140×84 |
| **video-apagado** | `brightness(0.62) saturate(0.8)` | 2 | `brightness(0.62) saturate(0.8)` (1)<br>`brightness(0.55) saturate(0.7)` (1) | saturación ±0.1, brillo ±0.07 | cm-vid-filtros-1 · x 196, y 222 · 140×84 |
| **miniatura-apagada** | `brightness(0.9)` | 3 | `brightness(0.9)` (1)<br>`brightness(0.85)` (1)<br>`brightness(0.8)` (1) | brillo ±0.1 | cm-vid-filtros-1 · x 24, y 372 · 140×84 |

## Curvas (14)

| Preset | Valor canónico | Filas | Para qué | Pares curva-duración más usados | Miembros | Mayor distancia |
|---|---|---|---|---|---|---|
| **estandar** | `cubic-bezier(0.2, 0.8, 0.2, 1)` | 120 | La de casi todo: sale rápido y se asienta despacio. | 200 ms (21), 240 ms (19) | (solo él) | idéntico |
| **suave** | `ease` | 105 | Estándar del navegador para color, fondo y opacidad. | 200 ms (41), 160 ms (29) | (solo él) | idéntico |
| **lineal** | `linear` | 28 | Barras de progreso, giros continuos y barridos. | 6000 ms (10), 800 ms (4) | (solo él) | idéntico |
| **trazo** | `cubic-bezier(0.3, 0.7, 0.2, 1)` | 13 | Dibujo de líneas y anillos (stroke-dashoffset). | 1600 ms (7), 1400 ms (5) | (solo él) | idéntico |
| **salida** | `ease-out` | 9 | Pulsaciones y retornos cortos. | 1000 ms (3), 200 ms (2) | (solo él) | idéntico |
| **resorte** | `cubic-bezier(0.3, 1.5, 0.5, 1)` | 8 | Muelle del editor: rebasa un 15 % y vuelve. | 320 ms (4), 160 ms (2) | (solo él) | idéntico |
| **vaiven** | `ease-in-out` | 6 | Giros y trazos que van y vienen. | 2400 ms (2), 700 ms (1) | (solo él) | idéntico |
| **parpadeo** | `steps(1)` | 2 | Parpadeo del cursor (todo o nada). | 1000 ms (2) | (solo él) | idéntico |
| **cae** | `cubic-bezier(0.5, 0, 0.9, 0.5)` | 2 | Salidas del editor: arranca lento y acelera. | 200 ms (1), 240 ms (1) | (solo él) | idéntico |
| **acelera** | `cubic-bezier(0.4, 0, 1, 1)` | 3 | Cierre de la hoja de la ruta y del velo. | 240 ms (2), 200 ms (1) | `cubic-bezier(0.4, 0, 1, 1)` (2)<br>`ease-in` (1) | posición ±0.02 |
| **material** | `cubic-bezier(0.4, 0, 0.2, 1)` | 2 | Estándar de Material (entrada y salida de los fondos). | 320 ms (2) | (solo él) | idéntico |
| **resorte-fuerte** | `cubic-bezier(0.3, 1.6, 0.5, 1)` | 2 | Muelle de suplementos: rebasa un 20 %. | 600 ms (1), 700 ms (1) | (solo él) | idéntico |
| **ruta** | `cubic-bezier(0.5, 0, 0.2, 1)` | 1 | Trazado de la ruta del cardio. | 1000 ms (1) | (solo él) | idéntico |
| **resorte-suave** | `cubic-bezier(0.22, 1.12, 0.36, 1)` | 1 | Apertura de la hoja de la ruta: rebasa un 5 %. | 400 ms (1) | (solo él) | idéntico |

## Duraciones (23)

| Preset | Ms | Filas | Valores que agrupa (filas) | Mayor distancia |
|---|---|---|---|---|
| **80** (exacto) | 80 | 1 | `80ms` (1) | 0 % |
| **90** | 90 | 2 | `0.09s` (1), `90ms` (1) | 0 % |
| **120** | 120 | 1 | `0.12s` (1) | 0 % |
| **140** (exacto) | 140 | 1 | `0.14s` (1) | 0 % |
| **160** | 160 | 33 | `0.16s` (15), `0.18s` (12), `0.15s` (5), `160ms` (1) | 11.1 % |
| **200** | 200 | 64 | `0.2s` (42), `0.22s` (17), `220ms` (4), `200ms` (1) | 9.1 % |
| **240** | 240 | 32 | `0.26s` (13), `0.25s` (9), `0.24s` (6), `260ms` (2), `250ms` (1), `240ms` (1) | 7.7 % |
| **280** | 280 | 21 | `0.3s` (19), `0.28s` (1), `280ms` (1) | 6.7 % |
| **320** | 320 | 26 | `0.32s` (10), `0.34s` (8), `0.35s` (4), `0.36s` (3), `360ms` (1) | 11.1 % |
| **400** | 400 | 13 | `0.42s` (6), `420ms` (3), `0.4s` (2), `0.38s` (2) | 5.3 % |
| **460** (exacto) | 460 | 2 | `0.46s` (2) | 0 % |
| **520** | 520 | 20 | `0.5s` (16), `0.55s` (3), `550ms` (1) | 5.5 % |
| **600** (exacto) | 600 | 9 | `0.6s` (9) | 0 % |
| **700** | 700 | 7 | `0.7s` (5), `700ms` (1), `780ms` (1) | 10.3 % |
| **800** (exacto) | 800 | 7 | `0.8s` (7) | 0 % |
| **1000** | 1000 | 22 | `1.1s` (13), `0.9s` (6), `1s` (3) | 11.1 % |
| **1200** (exacto) | 1200 | 2 | `1.2s` (2) | 0 % |
| **1300** (exacto) | 1300 | 1 | `1.3s` (1) | 0 % |
| **1400** (exacto) | 1400 | 10 | `1.4s` (10) | 0 % |
| **1600** | 1600 | 9 | `1.6s` (8), `1.8s` (1) | 11.1 % |
| **2400** | 2400 | 4 | `2.6s` (2), `2.2s` (1), `2.4s` (1) | 9.1 % |
| **6000** (exacto) | 6000 | 10 | `6s` (10) | 0 % |
| **15000** (exacto) | 15000 | 1 | `15s` (1) | 0 % |

## Retardos (11)

| Preset | Ms | Filas | Valores que agrupa (filas) | Mayor distancia |
|---|---|---|---|---|
| **r60** (exacto) | 60 | 2 | `0.06s` (1), `60ms` (1) | 0 % |
| **r90** | 90 | 1 | `0.1s` (1) | 10 % |
| **r120** | 120 | 1 | `0.12s` (1) | 0 % |
| **r160** | 160 | 2 | `0.16s` (1), `0.15s` (1) | 6.7 % |
| **r240** | 240 | 2 | `0.25s` (2) | 4 % |
| **r600** (exacto) | 600 | 1 | `0.6s` (1) | 0 % |
| **r700** | 700 | 3 | `0.7s` (2), `0.75s` (1) | 6.7 % |
| **r1000** | 1000 | 4 | `1.1s` (1), `0.95s` (1), `0.9s` (1), `1s` (1) | 11.1 % |
| **r1250** (exacto) | 1250 | 1 | `1.25s` (1) | 0 % |
| **r1300** (exacto) | 1300 | 1 | `1.3s` (1) | 0 % |
| **r1600** | 1600 | 1 | `1.8s` (1) | 11.1 % |

## Degradados (43)

| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros | Mayor distancia | Hoja y posición |
|---|---|---|---|---|---|---|
| **relleno-crema-suave** | linear | `linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8))` | 11 | (solo él) | idéntico | cm-deg-1 · x 24, y 72 · 140×84 |
| **relleno-crema** | linear | `linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191))` | 10 | (solo él) | idéntico | cm-deg-1 · x 196, y 72 · 140×84 |
| **tinte-hacia-modulo-1** | linear | `linear-gradient(160deg, rgba(244, 154, 110, 0.08), rgb(17, 17, 17) 60%)` | 2 | (solo él) | idéntico | cm-deg-1 · x 24, y 222 · 140×84 |
| **tinte-hacia-modulo-2** | linear | `linear-gradient(170deg, rgba(247, 207, 115, 0.05), rgb(15, 15, 15) 55%)` | 1 | (solo él) | idéntico | cm-deg-1 · x 196, y 222 · 140×84 |
| **relleno-horizontal-blanco** | linear · dinámico | `linear-gradient(90deg,#ffffff88,var(--zc))` | 1 | (solo él) | idéntico | cm-deg-1 · x 24, y 372 · 140×84 |
| **relleno-horizontal-crema-1** | linear · dinámico | `linear-gradient(90deg,var(--ink) var(--p,50%),#ffffff1f var(--p,50%))` | 1 | (solo él) | idéntico | cm-deg-1 · x 196, y 372 · 140×84 |
| **relleno-horizontal-crema-2** | linear | `linear-gradient(90deg, rgba(242, 238, 230, 0.753), rgb(242, 238, 230))` | 1 | (solo él) | idéntico | cm-deg-1 · x 24, y 522 · 140×84 |
| **tinte-crema-35** | linear | `linear-gradient(rgba(242, 238, 230, 0.35), rgba(242, 238, 230, 0.35))` | 1 | (solo él) | idéntico | cm-deg-1 · x 196, y 522 · 140×84 |
| **tinte-crema-70** | linear | `linear-gradient(rgba(242, 238, 230, 0.7), rgba(242, 238, 230, 0.7))` | 1 | (solo él) | idéntico | cm-deg-1 · x 24, y 672 · 140×84 |
| **relleno-modulo** | linear | `linear-gradient(rgb(12, 12, 12), rgb(0, 0, 0))` | 1 | (solo él) | idéntico | cm-deg-1 · x 196, y 672 · 140×84 |
| **relleno-cardio** | linear | `linear-gradient(rgb(143, 178, 255), rgb(110, 143, 208))` | 1 | (solo él) | idéntico | cm-deg-2 · x 24, y 72 · 140×84 |
| **relleno-horizontal-cardio** | linear · dinámico | `linear-gradient(90deg,var(--col),var(--ene))` | 1 | (solo él) | idéntico | cm-deg-2 · x 196, y 72 · 140×84 |
| **velo-negro-1** | linear | `linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0))` | 5 | `linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0))` (4)<br>`linear-gradient(rgba(10, 10, 10, 0), rgb(10, 10, 10))` (1) | canal ±10 | cm-deg-2 · x 24, y 222 · 140×84 |
| **velo-hoja** | linear | `linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 30%)` | 3 | `linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 30%)` (1)<br>`linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 28%)` (1)<br>`linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 22%)` (1) | posición ±8 | cm-deg-2 · x 196, y 222 · 140×84 |
| **velo-negro-2** | linear | `linear-gradient(rgba(0, 0, 0, 0) 35%, rgba(0, 0, 0, 0.85) 75%)` | 1 | (solo él) | idéntico | cm-deg-2 · x 24, y 372 · 140×84 |
| **velo-negro-3** | linear | `linear-gradient(rgba(0, 0, 0, 0) 30%, rgba(0, 0, 0, 0.933) 68%)` | 1 | (solo él) | idéntico | cm-deg-2 · x 196, y 372 · 140×84 |
| **velo-negro-4** | linear | `linear-gradient(rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.85) 72%)` | 1 | (solo él) | idéntico | cm-deg-2 · x 24, y 522 · 140×84 |
| **velo-hoja-largo** | linear | `linear-gradient(rgba(27, 27, 29, 0), rgba(27, 27, 29, 0.8) 42%, rgb(27, 27, 29) 82%)` | 1 | (solo él) | idéntico | cm-deg-2 · x 196, y 522 · 140×84 |
| **velo-hoja-sale** | linear | `linear-gradient(rgb(27, 27, 29) 62%, rgba(27, 27, 29, 0))` | 1 | (solo él) | idéntico | cm-deg-2 · x 24, y 672 · 140×84 |
| **velo-negro-sale-1** | linear | `linear-gradient(rgb(0, 0, 0) 20%, rgba(0, 0, 0, 0))` | 1 | (solo él) | idéntico | cm-deg-2 · x 196, y 672 · 140×84 |
| **velo-negro-sale-2** | linear | `linear-gradient(rgb(0, 0, 0) 52%, rgba(0, 0, 0, 0))` | 1 | (solo él) | idéntico | cm-deg-3 · x 24, y 72 · 140×84 |
| **velo-negro-sale-3** | linear | `linear-gradient(to top, rgb(0, 0, 0) 58%, rgba(0, 0, 0, 0))` | 1 | (solo él) | idéntico | cm-deg-3 · x 196, y 72 · 140×84 |
| **velo-crema-largo** | linear | `linear-gradient(transparent, rgba(242, 238, 230, 0.2) 70%, rgba(242, 238, 230, 0.5))` | 1 | (solo él) | idéntico | cm-deg-3 · x 24, y 222 · 140×84 |
| **vineta** | radial | `radial-gradient(120% 90% at 50% 55%, rgb(27, 27, 29) 0%, rgb(10, 10, 10) 62%, rgb(0, 0, 0) 100%)` | 1 | (solo él) | idéntico | cm-deg-3 · x 196, y 222 · 140×84 |
| **resplandor-ancho-zona** | radial · dinámico | `radial-gradient(120% 52% at 50% 24%,color-mix(in srgb,var(--zc) 14%,transparent),transparent 66%)` | 1 | (solo él) | idéntico | cm-deg-3 · x 24, y 372 · 140×84 |
| **resplandor-zona** | radial · dinámico | `radial-gradient(closest-side,var(--zc),transparent)` | 1 | (solo él) | idéntico | cm-deg-3 · x 196, y 372 · 140×84 |
| **resplandor-crema** | radial | `radial-gradient(closest-side, rgba(242, 238, 230, 0.165), transparent)` | 1 | (solo él) | idéntico | cm-deg-3 · x 24, y 522 · 140×84 |
| **vidrio-relleno-hoja** | linear | `linear-gradient(140deg, rgba(44, 44, 44, 0.72), rgba(22, 22, 22, 0.69))` | 5 | (solo él) | idéntico | cm-deg-3 · x 196, y 522 · 140×84 |
| **vidrio-relleno-barra** | linear | `linear-gradient(150deg, rgba(58, 58, 58, 0.4), rgba(20, 20, 20, 0.35))` | 5 | (solo él) | idéntico | cm-deg-3 · x 24, y 672 · 140×84 |
| **vidrio-relleno-tecnica** | linear | `linear-gradient(160deg, rgba(42, 42, 45, 0.77), rgba(20, 20, 22, 0.84))` | 1 | (solo él) | idéntico | cm-deg-3 · x 196, y 672 · 140×84 |
| **aviso-relleno-error** | linear | `linear-gradient(160deg, rgba(58, 36, 35, 0.847), rgba(28, 18, 18, 0.88))` | 1 | (solo él) | idéntico | cm-deg-4 · x 24, y 72 · 140×84 |
| **brillo-esqueleto-1** | linear | `linear-gradient(90deg, rgba(255, 255, 255, 0.04) 20%, rgba(255, 255, 255, 0.11) 50%, rgba(255, 255, 255, 0.04) 80%)` | 1 | (solo él) | idéntico | cm-deg-4 · x 196, y 72 · 140×84 |
| **brillo-esqueleto-2** | linear | `linear-gradient(100deg, rgba(255, 255, 255, 0.65) 0px, rgba(255, 255, 255, 0.65) 38%, rgb(255, 255, 255) 50%, rgba(255, 255, 255, 0.65) 62%, rgba(255, 255, 255, 0.65) 100%)` | 1 | (solo él) | idéntico | cm-deg-4 · x 24, y 222 · 140×84 |
| **brillo-esqueleto-3** | linear | `linear-gradient(100deg, rgb(15, 15, 15) 30%, rgb(26, 26, 26) 50%, rgb(15, 15, 15) 70%)` | 1 | (solo él) | idéntico | cm-deg-4 · x 196, y 222 · 140×84 |
| **brillo-esqueleto-4** | linear | `linear-gradient(90deg, rgb(22, 22, 22), rgb(34, 34, 34) 50%, rgb(22, 22, 22))` | 1 | (solo él) | idéntico | cm-deg-4 · x 24, y 372 · 140×84 |
| **franja-crema** | linear | `linear-gradient(transparent, rgb(242, 238, 230), transparent)` | 1 | (solo él) | idéntico | cm-deg-4 · x 196, y 372 · 140×84 |
| **brillo-esqueleto-5** | linear | `linear-gradient(100deg, rgba(255, 255, 255, 0.05) 30%, rgba(255, 255, 255, 0.11) 50%, rgba(255, 255, 255, 0.05) 70%)` | 1 | (solo él) | idéntico | cm-deg-4 · x 24, y 522 · 140×84 |
| **fondo-compuesto-1** | compuesto | `radial-gradient(60% 40% at 30% 30%, rgb(90, 74, 60), transparent 70%), radial-gradient(50% 35% at 75% 55%, rgb(58, 63, 71), transparent 70%), radial-gradient(70% 50% at 50% 85%, rgb(42, 36, 32), transparent 70%), linear-gradien…` | 1 | (solo él) | idéntico | cm-deg-4 · x 196, y 522 · 140×84 |
| **fondo-compuesto-2** | compuesto | `radial-gradient(120% 70% at 28% 18%, rgb(48, 52, 58), transparent 62%), radial-gradient(90% 60% at 80% 90%, rgb(35, 32, 28), transparent 70%), linear-gradient(rgb(20, 22, 26), rgb(10, 11, 12))` | 1 | (solo él) | idéntico | cm-deg-4 · x 24, y 672 · 140×84 |
| **rejilla** | compuesto | `linear-gradient(rgba(242, 238, 230, 0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(242, 238, 230, 0.05) 1px, transparent 1px)` | 1 | (solo él) | idéntico | cm-deg-4 · x 196, y 672 · 140×84 |
| **rayas-blanco** | repeating-linear | `repeating-linear-gradient(135deg, rgba(255, 255, 255, 0.19) 0px, rgba(255, 255, 255, 0.19) 3px, rgba(255, 255, 255, 0.063) 3px, rgba(255, 255, 255, 0.063) 6px)` | 1 | (solo él) | idéntico | cm-deg-5 · x 24, y 72 · 140×84 |
| **rayas-crema** | repeating-linear | `repeating-linear-gradient(135deg, rgb(242, 238, 230) 0px, rgb(242, 238, 230) 3px, rgba(242, 238, 230, 0.667) 3px, rgba(242, 238, 230, 0.667) 6px)` | 1 | (solo él) | idéntico | cm-deg-5 · x 196, y 72 · 140×84 |
| **rayas-energia** | repeating-linear | `repeating-linear-gradient(135deg, rgb(247, 207, 115) 0px, rgb(247, 207, 115) 3px, rgba(247, 207, 115, 0.333) 3px, rgba(247, 207, 115, 0.333) 6px)` | 1 | (solo él) | idéntico | cm-deg-5 · x 24, y 222 · 140×84 |

## @keyframes por familia (33 familias de 110 @keyframes)

| Familia | Qué hace | Canónico | Agrupa | Duraciones (filas) | Curvas | Repite | Relleno |
|---|---|---|---|---|---|---|---|
| **entrada-sube** | Aparece subiendo | `cv-rise` | cv-rise, rp-in, ce-in, ct-rise, nc-up, aj-sheet, aj-toast, asContent, ed-in, lv-swap, lv2-roll, lv2-su, tcpop | 240 (6), 320 (5), 520 (4), 280 (4), 600 (3) | estandar (22), suave (3) | 1 (25) | none (13), both (12) |
| **dibujo-trazo** | Dibujo de línea o anillo | `drawln` | drawln, lv-ck, lv2-arc, lv2-ck, lxring, nc-ring | 1000 (8), 1600 (7), 1400 (5), 460 (1), 700 (1) | trazo (13), estandar (10), lineal (1) | 1 (24) | forwards (20), backwards (2), both (2) |
| **crece-x** | Crece en horizontal | `wrbar` | wrbar, cv-bar, asGrow, lxgrow | 6000 (10), 800 (2), 1000 (1), 700 (1) | lineal (10), estandar (3), ruta (1) | 1 (14) | forwards (10), both (4) |
| **entrada-derecha** | Aparece desde la derecha | `asIn` | asIn, aj-push, ct-in, ed-lbl-r, lv2-sr | 200 (6), 240 (3), 280 (2), 160 (1) | estandar (12) | 1 (12) | both (9), none (3) |
| **fundido-entrada** | Fundido de entrada | `asScrim` | asScrim, cv-fadein, ed-fadein, lxfade, aj-fade | 200 (6), 280 (3), 320 (1), 600 (1) | suave (7), estandar (3), salida (1) | 1 (11) | both (7), none (2), forwards (2) |
| **giro** | Giro continuo | `lxspin` | lxspin, aj-spin, asSpin, ct-sp, nc-spin, rp-rot, orb | 800 (5), 1000 (2) | lineal (7) | infinite (7) | none (7) |
| **hoja-sube** | Hoja que sube | `asRise` | asRise, cv-sheet, ed-up, lxsheet, tcsheet | 320 (3), 400 (2), 280 (1), 700 (1) | estandar (7) | 1 (7) | both (6), none (1) |
| **rebote** | Rebote (pulsación con rebasado) | `lv-pop` | lv-pop, ed-lift, ed-pop, lv-snap, spbump, sppop, bump | 200 (2), 240 (2), 320 (1), 700 (1), 600 (1) | estandar (3), resorte (2), resorte-fuerte (2) | 1 (7) | none (5), forwards (1), both (1) |
| **pulso** | Pulso de opacidad o grosor (bucle) | `blink` | blink, ce-blink, lv-breathe, lxpulse, nc-pulse, tcq | 1000 (3), 2400 (2), 1400 (1) | vaiven (4), parpadeo (2) | infinite (5), 2 (1) | none (6) |
| **aparece-escala** | Aparece creciendo | `ed-bump` | ed-bump, lv-pin, rp-pop | 320 (2), 240 (1), 400 (1), 520 (1) | estandar (3), resorte (2) | 1 (5) | none (5) |
| **brillo-barrido** | Brillo que barre (esqueleto) | `asSh` | asSh, cv-shim, nc-sh, rp-sk, tcsk, sk | 1400 (4), 2400 (1) | lineal (5) | infinite (5) | none (5) |
| **destello** | Destello de fondo o color | `asFlash` | asFlash, ce-fresh, cv-swdone, ed-flash, nc-flash | 600 (2), 1600 (2), 520 (1) | suave (2), salida (2), estandar (1) | 1 (5) | none (3), both (1), forwards (1) |
| **entrada-baja** | Aparece bajando | `lxrise` | lxrise, ed-popin, tcfade | 240 (1), 320 (1), 200 (1), 520 (1) | estandar (3), suave (1) | 1 (4) | both (3), none (1) |
| **barrido-ancho** | Barrido de ancho | `lv-wipe` | lv-wipe, lv2-fill, lv2-wipe | 520 (2), 400 (1) | estandar (3) | 1 (3) | none (2), both (1) |
| **crece-y** | Crece en vertical | `cv-grow` | cv-grow, nc-bar, grow, hgrow | 600 (2), 700 (1) | estandar (2), vaiven (1) | 1 (2), infinite (1) | both (2), none (1) |
| **entrada-implicita** | Entrada con el estado inicial en la regla | `lxrise2` | lxrise2, lv2-chip, pop, rise | 520 (2), 200 (1) | estandar (3) | 1 (3) | forwards (3) |
| **entrada-izquierda** | Aparece desde la izquierda | `aj-pop` | aj-pop, ed-lbl-l, lv2-sl | 280 (1), 0 (1), 200 (1) | estandar (2), suave (1) | 1 (3) | none (2), both (1) |
| **rebote-aparece** | Rebote al aparecer | `cv-pop` | cv-pop | 320 (1), 520 (1), 400 (1) | suave (2), estandar (1) | 1 (3) | both (3) |
| **asentar** | Se asienta (parte de más grande) | `cv-cn` | cv-cn, lv2-tick | 1000 (1), 80 (1) | estandar (2) | 1 (2) | both (1), none (1) |
| **latido** | Latido | `cv-beat` | cv-beat, cv-heart | 1000 (2) | salida (2) | infinite (2) | none (2) |
| **onda** | Onda que se expande | `cv-rg` | cv-rg, cv-rip | 1000 (1), 520 (1) | salida (2) | 1 (2) | both (1), forwards (1) |
| **aviso** | Aviso: entra, espera y sale | `ed-delta` | ed-delta | 1300 (1) | estandar (1) | 1 (1) | forwards (1) |
| **barrido-linea** | Línea que barre | `tcsweep` | tcsweep, las | 2400 (1) | lineal (1) | infinite (1) | none (1) |
| **despliegue-fila** | Despliegue de fila | `ed-fresh` | ed-fresh | 520 (1) | estandar (1) | 1 (1) | none (1) |
| **fundido-salida** | Fundido de salida | `ed-fadeout` | ed-fadeout | 200 (1) | suave (1) | 1 (1) | forwards (1) |
| **hoja-baja** | Hoja que baja | `ed-down` | ed-down | 240 (1) | cae (1) | 1 (1) | forwards (1) |
| **ignicion** | Ignición (anillo y resplandor) | `cv-ignite` | cv-ignite | 800 (1) | suave (1) | 1 (1) | both (1) |
| **onda-anillo** | Onda de anillo | `ed-ring` | ed-ring | 460 (1) | salida (1) | 1 (1) | forwards (1) |
| **pulso-anillo** | Pulso de anillo (bucle) | `tcpulse` | tcpulse | 1200 (1) | vaiven (1) | infinite (1) | none (1) |
| **revelado** | Revelado de vídeo (brillo y desenfoque) | `tcvid` | tcvid | 1000 (1) | estandar (1) | 1 (1) | both (1) |
| **caida** | Caída con giro | `fall` | fall |  |  |  |  |
| **flotar** | Flota arriba y abajo | `fl` | fl |  |  |  |  |
| **giro-entrada** | Entrada con giro | `popin` | popin, spinm |  |  |  |  |

### Texto exacto del `@keyframes` canónico de cada familia

- **entrada-sube** · `@keyframes cv-rise { 0% { opacity: 0; transform: translateY(14px); } 100% { opacity: 1; transform: none; } }`
- **dibujo-trazo** · `@keyframes drawln { 100% { stroke-dashoffset: 0; } }`
- **crece-x** · `@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }`
- **entrada-derecha** · `@keyframes asIn { 0% { opacity: 0; transform: translateX(var(--dx,10px)); } 100% { opacity: 1; transform: none; } }`
- **fundido-entrada** · `@keyframes asScrim { 0% { opacity: 0; } 100% { opacity: 1; } }`
- **giro** · `@keyframes lxspin { 100% { transform: rotate(360deg); } }`
- **hoja-sube** · `@keyframes asRise { 0% { transform: translateY(100%); } 100% { transform: none; } }`
- **rebote** · `@keyframes lv-pop { 0% { transform: scale(0.88); } 60% { transform: scale(1.14); } 100% { transform: scale(1); } }`
- **pulso** · `@keyframes blink { 50% { opacity: 0; } }`
- **aparece-escala** · `@keyframes ed-bump { 0% { transform: scale(0.86); } 100% { transform: none; } }`
- **brillo-barrido** · `@keyframes asSh { 0% { background-position: 120% 0px; } 100% { background-position: -120% 0px; } }`
- **destello** · `@keyframes asFlash { 0% { color: var(--mus); } 100% { color: inherit; } }`
- **entrada-baja** · `@keyframes lxrise { 0% { opacity: 0; transform: translateY(-8px); } 100% { opacity: 1; transform: none; } }`
- **barrido-ancho** · `@keyframes lv-wipe { 0% { width: 0px; opacity: 0.9; } 70% { width: 100%; opacity: 0.9; } 100% { width: 100%; opacity: 0; } }`
- **crece-y** · `@keyframes cv-grow { 0% { transform: scaleY(0.05); opacity: 0; } 100% { transform: scaleY(1); } }`
- **entrada-implicita** · `@keyframes lxrise2 { 100% { opacity: 1; transform: none; } }`
- **entrada-izquierda** · `@keyframes aj-pop { 0% { transform: translateX(-24px); opacity: 0; } 100% { transform: none; opacity: 1; } }`
- **rebote-aparece** · `@keyframes cv-pop { 0% { opacity: 0; transform: scale(0.86); } 60% { opacity: 1; transform: scale(1.03); } 100% { transform: scale(1); } }`
- **asentar** · `@keyframes cv-cn { 0% { opacity: 0; transform: scale(1.5); } 30% { opacity: 1; } 100% { opacity: 1; transform: scale(1); } }`
- **latido** · `@keyframes cv-beat { 0% { opacity: 0.34; transform: scale(1.06); } 45% { opacity: 0.18; transform: scale(1); } 100% { opacity: 0.2; } }`
- **onda** · `@keyframes cv-rg { 0% { transform: scale(0.5); opacity: 0.9; } 100% { transform: scale(3.4); opacity: 0; } }`
- **aviso** · `@keyframes ed-delta { 0% { opacity: 0; transform: translateY(8px); } 18% { opacity: 1; transform: none; } 75% { opacity: 1; } 100% { opacity: 0; transform: translateY(-8px); } }`
- **barrido-linea** · `@keyframes tcsweep { 0% { transform: translateY(0px); } 100% { transform: translateY(calc(var(--vfh,640px) + 46px)); } }`
- **despliegue-fila** · `@keyframes ed-fresh { 0% { background: rgba(242, 238, 230, 0.12); max-height: 0px; opacity: 0; } 30% { max-height: 140px; } 100% { background: var(--module); max-height: 200px; opacity: 1; } }`
- **fundido-salida** · `@keyframes ed-fadeout { 100% { opacity: 0; } }`
- **hoja-baja** · `@keyframes ed-down { 100% { transform: translateY(100%); } }`
- **ignicion** · `@keyframes cv-ignite { 0% { box-shadow: transparent 0px 0px 0px 0px; } 40% { box-shadow: 0 0 0 10px color-mix(in srgb,var(--zc) 30%,transparent); } 100% { box-shadow: 0 0 22px color-mix(in srgb,var(--zc) 40%,transparent); } }`
- **onda-anillo** · `@keyframes ed-ring { 0% { box-shadow: rgba(242, 238, 230, 0.467) 0px 0px 0px 0px; } 100% { box-shadow: rgba(242, 238, 230, 0) 0px 0px 0px 14px; } }`
- **pulso-anillo** · `@keyframes tcpulse { 0%, 100% { box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 0px 0px; } 50% { box-shadow: rgba(242, 238, 230, 0) 0px 0px 0px 5px; } }`
- **revelado** · `@keyframes tcvid { 0% { filter: brightness(0.5) blur(6px); transform: translate(var(--vx),var(--vy)) scale(calc(var(--vs)*1.06)); } 100% { filter: none; } }`
- **caida** · `@keyframes fall { 0% { transform: translateY(0px) rotate(0deg); } 100% { transform: translateY(900px) rotate(540deg); opacity: 0; } }`
- **flotar** · `@keyframes fl { 0%, 100% { transform: translateY(0px); } 50% { transform: translateY(-8px); } }`
- **giro-entrada** · `@keyframes popin { 0% { transform: scale(0.2) rotate(-40deg); opacity: 0; } 100% { transform: none; opacity: 1; } }`

## Casi iguales: pares que NO se unieron por poco (20)

Dentro del doble de la tolerancia pero fuera de ella. **No se arreglan aquí**: son candidatos a unificar si el usuario lo decide (cambiar un valor del lienzo y volver a generar).

| Preset A | Preset B | Valor A | Valor B | Diferencia |
|---|---|---|---|---|
| isla-vidrio | caida-8-20-capas | `rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset` | `rgba(0, 0, 0, 0.8) 0px 8px 20px, rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` | y ±1 dp, desenfoque ±8 dp, extensión ±1 dp, alfa ±0.133 |
| nota | caida-4-18 | `rgba(0, 0, 0, 0.533) 0px 4px 12px` | `rgba(0, 0, 0, 0.533) 0px 4px 18px` | desenfoque ±6 dp |
| nota | asa-deslizador | `rgba(0, 0, 0, 0.533) 0px 4px 12px` | `rgba(0, 0, 0, 0.667) 0px 2px 8px` | y ±2 dp, desenfoque ±4 dp, alfa ±0.134 |
| aureola-foco | anillo-crema-4-suave | `rgba(242, 238, 230, 0.08) 0px 0px 0px 4px` | `rgba(242, 238, 230, 0.2) 0px 0px 0px 4px` | alfa ±0.12 |
| flotante | aviso | `rgba(0, 0, 0, 0.667) 0px 8px 28px` | `rgba(0, 0, 0, 0.8) 0px 12px 34px` | y ±4 dp, desenfoque ±6 dp, alfa ±0.133 |
| flotante | caida-10-22 | `rgba(0, 0, 0, 0.667) 0px 8px 28px` | `rgba(0, 0, 0, 0.667) 0px 10px 22px` | y ±2 dp, desenfoque ±6 dp |
| flotante | caida-8-28 | `rgba(0, 0, 0, 0.667) 0px 8px 28px` | `rgba(0, 0, 0, 0.8) 0px 8px 28px` | alfa ±0.133 |
| modal | caida-16-60 | `rgba(0, 0, 0, 0.8) 0px 20px 60px` | `rgba(0, 0, 0, 0.867) 0px 16px 60px` | y ±4 dp, alfa ±0.067 |
| borde-interior | interior-blanco-1-tenue | `rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` | `rgba(255, 255, 255, 0.165) 0px 0px 0px 1px inset` | alfa ±0.085 |
| resplandor-crema-12 | resplandor-crema-10 | `rgba(242, 238, 230, 0.333) 0px 0px 12px` | `rgba(242, 238, 230, 0.22) 0px 0px 10px` | desenfoque ±2 dp, alfa ±0.113 |
| resplandor-crema-12 | resplandor-crema-18 | `rgba(242, 238, 230, 0.333) 0px 0px 12px` | `rgba(242, 238, 230, 0.333) 0px 0px 18px 2px` | desenfoque ±6 dp, extensión ±2 dp |
| borde-exterior | anillo-blanco-3-suave | `rgba(255, 255, 255, 0.11) 0px 0px 0px 1px` | `rgba(255, 255, 255, 0.08) 0px 0px 0px 3px` | extensión ±2 dp, alfa ±0.03 |
| menu | aviso | `rgba(0, 0, 0, 0.8) 0px 14px 40px` | `rgba(0, 0, 0, 0.8) 0px 12px 34px` | y ±2 dp, desenfoque ±6 dp |
| menu | popover | `rgba(0, 0, 0, 0.8) 0px 14px 40px` | `rgba(0, 0, 0, 0.867) 0px 18px 44px` | y ±4 dp, desenfoque ±4 dp, alfa ±0.067 |
| aviso | caida-8-28 | `rgba(0, 0, 0, 0.8) 0px 12px 34px` | `rgba(0, 0, 0, 0.8) 0px 8px 28px` | y ±4 dp, desenfoque ±6 dp |
| interior-blanco-1-tenue | borde-interior-fuerte | `rgba(255, 255, 255, 0.165) 0px 0px 0px 1px inset` | `rgba(255, 255, 255, 0.25) 0px 0px 0px 1px inset` | alfa ±0.085 |
| popover | caida-18-50 | `rgba(0, 0, 0, 0.867) 0px 18px 44px` | `rgba(0, 0, 0, 0.867) 0px 18px 50px` | desenfoque ±6 dp |
| caida-10-22 | caida-8-28 | `rgba(0, 0, 0, 0.667) 0px 10px 22px` | `rgba(0, 0, 0, 0.8) 0px 8px 28px` | y ±2 dp, desenfoque ±6 dp, alfa ±0.133 |
| anillo-foco | anillo-crema-4-suave | `rgba(242, 238, 230, 0.333) 0px 0px 0px 2px` | `rgba(242, 238, 230, 0.2) 0px 0px 0px 4px` | extensión ±2 dp, alfa ±0.133 |
| resplandor-crema-10 | resplandor-crema-18 | `rgba(242, 238, 230, 0.22) 0px 0px 10px` | `rgba(242, 238, 230, 0.333) 0px 0px 18px 2px` | desenfoque ±8 dp, extensión ±2 dp, alfa ±0.113 |
