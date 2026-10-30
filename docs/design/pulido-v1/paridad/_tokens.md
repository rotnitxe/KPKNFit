# Tokens de profundidad y movimiento del lienzo (catálogo finito para los cimientos)

Los valores distintos que usa el lienzo aprobado. Los **cimientos** (`ui/design/depth` y `ui/design/motion`) implementan cada uno una vez; los paquetes solo los usan.

## Sombras (101 valores distintos)

| Usos | Paquetes | Valor |
|---|---|---|
| 11 | aj as ce ed lv lx nc rp sp tc | `rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset` |
| 10 | as ce ct cv ed lv nc rp sp tc | `rgb(244, 154, 110) 0px -2px inset` |
| 8 | as ce ct cv lx nc rp | `rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset` |
| 6 | as lv nc rp sp tc | `rgba(67, 209, 140, 0.533) 0px -2px inset` |
| 5 | aj ct nc rp sp | `rgba(0, 0, 0, 0.6) 0px 10px 30px, rgba(255, 255, 255, 0.07) 0px 1px inset` |
| 5 | aj ct nc rp sp | `rgba(0, 0, 0, 0.467) 0px 8px 24px, rgba(255, 255, 255, 0.11) 0px 1px inset` |
| 4 | lx rp sp | `0 0 0 2px var(--ink)` |
| 3 | ce lx | `rgba(0, 0, 0, 0.667) 0px 8px 28px` |
| 3 | lv | `rgba(242, 238, 230, 0.333) 0px 0px 0px 2px` |
| 2 | aj lx | `rgba(0, 0, 0, 0.8) 0px 20px 60px` |
| 2 | as ct | `rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` |
| 2 | ce lv | `rgba(242, 238, 230, 0.08) 0px 0px 0px 4px` |
| 2 | ce ed | `rgb(27, 27, 29) 0px 0px 0px 2px` |
| 2 | ce ed | `rgba(0, 0, 0, 0.667) 0px 10px 30px, rgba(255, 255, 255, 0.094) 0px 1px inset` |
| 2 | ce nc | `rgba(242, 238, 230, 0.333) 0px 0px 12px` |
| 2 | ct | `inset 0 0 0 1.5px var(--c)` |
| 2 | cv lv | `rgb(17, 17, 17) 0px 0px 0px 2px` |
| 2 | cv sp | `rgba(67, 209, 140, 0.333) 0px 0px 0px 1px inset` |
| 2 | lv | `rgba(255, 255, 255, 0.07) 0px 0px 0px 1px inset` |
| 2 | lv tc | `rgba(255, 255, 255, 0.11) 0px 0px 0px 1px` |
| 2 | lx rp | `rgba(0, 0, 0, 0.533) 0px 4px 12px` |
| 2 | lx | `rgba(255, 255, 255, 0.11) 0px 0px 0px 1px inset` |
| 2 | rp sp | `rgb(0, 0, 0) 0px 0px 0px 2px, rgba(242, 238, 230, 0.667) 0px 0px 0px 3px` |
| 1 | as | `rgba(0, 0, 0, 0.733) 0px -10px 30px` |
| 1 | as | `rgba(255, 255, 255, 0.15) 0px 0px 0px 1.5px inset` |
| 1 | as | `rgba(0, 0, 0, 0.8) 0px 14px 40px` |
| 1 | as | `0 0 0 2px var(--as-mod)` |
| 1 | as | `rgba(0, 0, 0, 0.8) 0px 12px 34px` |
| 1 | ce | `inset 0 0 0 1px var(--ink)` |
| 1 | ct | `rgba(255, 255, 255, 0.165) 0px 0px 0px 1px inset` |
| 1 | ct | `rgba(255, 255, 255, 0.09) 0px 0px 0px 1px inset` |
| 1 | ct | `rgba(0, 0, 0, 0.933) 0px 24px 70px, rgba(255, 255, 255, 0.08) 0px 1px inset` |
| 1 | ct | `rgba(242, 238, 230, 0.533) 0px 0px 0px 1.5px inset` |
| 1 | ct | `rgba(255, 255, 255, 0.18) 0px 0px 0px 1px inset` |
| 1 | ct | `rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.07) 0px 1px inset` |
| 1 | ct | `rgba(255, 255, 255, 0.25) 0px 0px 0px 1px inset` |
| 1 | cv | `0 0 12px var(--zc)` |
| 1 | cv | `rgb(0, 0, 0) 0px 0px 40px` |
| 1 | cv | `0 0 10px color-mix(in srgb,var(--zc) 75%,transparent)` |
| 1 | cv | `0 0 28px color-mix(in srgb,var(--wc,var(--zc)) 14%,transparent)` |
| 1 | cv | `rgb(17, 17, 17) 0px 0px 0px 1px` |
| 1 | cv | `rgb(0, 0, 0) 0px 0px 0px 4px` |
| 1 | cv | `rgb(0, 0, 0) 0px 0px 0px 3px` |
| 1 | cv | `0 0 0 4px #000,0 0 14px var(--c)` |
| 1 | cv | `rgba(0, 0, 0, 0.533) 0px 4px 18px` |
| 1 | cv | `rgb(0, 0, 0) 0px 2px 26px, rgba(0, 0, 0, 0.667) 0px 0px 6px` |
| 1 | cv | `rgb(0, 0, 0) 0px 1px 12px` |
| 1 | cv | `rgb(0, 0, 0) 0px 1px 8px` |
| 1 | ed | `rgba(0, 0, 0, 0.867) 0px 24px 48px, rgba(255, 255, 255, 0.15) 0px 0px 0px 1px` |
| 1 | ed | `rgba(0, 0, 0, 0) 0px 0px 0px 0px` |
| 1 | ed | `rgba(0, 0, 0, 0.867) 0px 18px 44px` |
| 1 | ed | `rgba(0, 0, 0, 0.667) 0px 2px 8px` |
| 1 | ed | `0 0 0 1.5px var(--ene)` |
| 1 | lv | `rgba(0, 0, 0, 0.6) 0px 6px 16px, rgba(255, 255, 255, 0.133) 0px 0px 0px 1px` |
| 1 | lv | `rgba(0, 0, 0, 0.6) 0px 6px 16px, rgba(255, 255, 255, 0.133) 0px 0px 0px 1px, rgb(244, 154, 110) 0px 2px 0px` |
| 1 | lv | `rgba(255, 155, 146, 0.4) 0px 0px 0px 1px inset` |
| 1 | lv | `rgba(0, 0, 0, 0.8) 0px 14px 34px` |
| 1 | lv | `rgba(0, 0, 0, 0.667) 0px 10px 22px` |
| 1 | lv | `rgba(0, 0, 0, 0.667) 0px 10px 28px` |
| 1 | lv | `rgb(255, 255, 255) 0px 0px 0px 1.5px inset` |

## Vidrio, desenfoques y filtros (19 valores distintos)

| Usos | Paquetes | Valor |
|---|---|---|
| 9 | as ce ct cv ed lx nc rp | `backdrop-filter: blur(22px) saturate(1.5)` |
| 5 | aj ct nc rp sp | `backdrop-filter: blur(26px) saturate(1.3)` |
| 5 | aj ct nc rp sp | `backdrop-filter: blur(18px) saturate(1.8) brightness(0.9)` |
| 2 | ce ed | `backdrop-filter: blur(24px) saturate(1.7)` |
| 2 | ct cv | `backdrop-filter: blur(12px)` |
| 1 | cv | `backdrop-filter: blur(6px)` |
| 1 | cv | `backdrop-filter: blur(8px)` |
| 1 | cv | `filter: blur(20px)` |
| 1 | cv | `backdrop-filter: blur(3px)` |
| 1 | cv | `backdrop-filter: blur(16px) saturate(1.4)` |
| 1 | cv | `backdrop-filter: blur(10px)` |
| 1 | lx | `filter: blur(26px)` |
| 1 | lx | `filter: saturate(0.8)` |
| 1 | tc | `filter: brightness(0.85)` |
| 1 | tc | `backdrop-filter: blur(24px) saturate(1.5)` |
| 1 | tc | `filter: brightness(0.9)` |
| 1 | tc | `filter: brightness(0.55) saturate(0.7)` |
| 1 | tc | `filter: brightness(0.8)` |
| 1 | tc | `filter: brightness(0.62) saturate(0.8)` |

## Curvas de animación y transición (25 distintas)

| Usos | Paquetes | Curva |
|---|---|---|
| 118 | aj as ce ct cv ed lv lx nc rp sp tc | `cubic-bezier(0.2, 0.8, 0.2, 1)` |
| 63 | aj as ce ct cv ed lv lx nc rp sp tc | `ease` |
| 38 | as ce cv ed lv lx nc rp sp tc | `ease, ease` |
| 30 | aj as ce ct cv ed lv lx nc rp sp tc | `linear` |
| 13 | as ct cv lv lx nc rp | `cubic-bezier(0.3, 0.7, 0.2, 1)` |
| 9 | aj ce cv ed nc | `ease-out` |
| 7 | aj ed lv tc | `cubic-bezier(0.2, 0.8, 0.2, 1), ease` |
| 7 | as ed nc tc | `ease, cubic-bezier(0.2, 0.8, 0.2, 1)` |
| 6 | ed | `cubic-bezier(0.3, 1.5, 0.5, 1)` |
| 6 | lv lx nc tc | `ease-in-out` |
| 4 | as cv tc | `ease, ease, ease` |
| 2 | as lv | `cubic-bezier(0.2, 0.8, 0.2, 1), cubic-bezier(0.2, 0.8, 0.2, 1)` |
| 2 | ce nc | `steps(1)` |
| 2 | ed | `cubic-bezier(0.5, 0, 0.9, 0.5)` |
| 2 | lv rp | `cubic-bezier(0.2, 0.8, 0.2, 1), ease, ease` |
| 2 | lv | `cubic-bezier(0.4, 0, 1, 1)` |
| 2 | sp | `cubic-bezier(0.3, 1.6, 0.5, 1)` |
| 1 | cv | `cubic-bezier(0.5, 0, 0.2, 1)` |
| 1 | cv | `ease-in` |
| 1 | ed | `cubic-bezier(0.3, 1.5, 0.5, 1), ease, ease` |
| 1 | ed | `ease, ease, cubic-bezier(0.3, 1.5, 0.5, 1)` |
| 1 | lv | `cubic-bezier(0.22, 1.12, 0.36, 1)` |
| 1 | lv | `cubic-bezier(0.2, 0.8, 0.2, 1), cubic-bezier(0.2, 0.8, 0.2, 1), cubic-bezier(0.2, 0.8, 0.2, 1)` |
| 1 | lx | `cubic-bezier(0.4, 0, 0.2, 1), cubic-bezier(0.4, 0, 0.2, 1), cubic-bezier(0.4, 0, 0.2, 1), cubic-bezier(0.4, 0, 0.2, 1), cubic-bezier(0.4, 0, 0.2, 1)` |
| 1 | lx | `cubic-bezier(0.4, 0, 0.2, 1)` |

## Duraciones y retardos (101 distintos)

| Usos | Paquetes | Duración |
|---|---|---|
| 26 | aj as ce ed lv lx nc rp sp tc | `0.2s` |
| 18 | aj as cv ed rp | `0.22s` |
| 15 | as ce ed lx nc rp sp tc | `0.2s, 0.2s / retardo 0s, 0s` |
| 14 | aj as cv ed lx nc rp | `0.3s` |
| 12 | as ce ed lv rp | `0.26s` |
| 12 | as ct cv lv lx nc rp tc | `1.1s` |
| 12 | ed lv sp tc | `0.16s, 0.16s / retardo 0s, 0s` |
| 11 | aj ed lx sp | `0.16s` |
| 10 | as ct cv lx nc rp tc | `1.4s` |
| 10 | as ce ct cv ed lv nc rp sp tc | `6s` |
| 9 | cv ed rp sp tc | `0.5s` |
| 8 | as ce ct cv lv lx nc rp | `1.6s` |
| 8 | as ct cv lx nc rp | `0.32s` |
| 7 | cv nc | `0.9s` |
| 7 | ed lv lx tc | `0.34s` |
| 6 | aj ct cv lx rp | `0.8s` |
| 6 | ce ct ed lv lx | `0.24s` |
| 5 | aj as ct ed | `0.18s` |
| 5 | as cv ed sp | `0.6s` |
| 5 | as cv | `0.42s` |
| 5 | ce cv lv | `0.25s` |
| 4 | as cv lv sp | `0.7s` |
| 4 | as ed lv | `0.18s, 0.18s / retardo 0s, 0s` |
| 4 | ce lv lx tc | `0.15s` |
| 3 | aj as ed | `0.36s` |
| 3 | as cv | `0.35s` |
| 3 | cv lv lx | `0.55s` |
| 3 | lv | `420ms` |
| 3 | lv | `220ms` |
| 2 | aj tc | `1.2s` |
| 2 | as ed | `0.18s, 0.18s, 0.18s / retardo 0s, 0s, 0s` |
| 2 | ce nc | `1s` |
| 2 | cv | `0.6s / retardo var(--d,0s)` |
| 2 | cv lv | `0.38s` |
| 2 | cv tc | `0.25s, 0.25s / retardo 0s, 0s` |
| 2 | cv tc | `0.5s, 0.5s / retardo 0s, 0s` |
| 2 | ed lv | `0.26s, 0.2s / retardo 0s, 0s` |
| 2 | lv | `260ms` |
| 2 | lv | `90ms` |
| 2 | tc | `0.3s, 0.25s, 0.3s / retardo 0s, 0s, 0s` |

## Degradados (46 distintos; los 30 más usados)

| Usos | Paquetes | Valor |
|---|---|---|
| 11 | as ce ct cv ed lv lx nc rp sp tc | `linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8))` |
| 10 | as ce ct cv ed lv nc rp sp tc | `linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191))` |
| 5 | aj ct nc rp sp | `linear-gradient(140deg, rgba(44, 44, 44, 0.72), rgba(22, 22, 22, 0.69))` |
| 5 | aj ct nc rp sp | `linear-gradient(150deg, rgba(58, 58, 58, 0.4), rgba(20, 20, 20, 0.35))` |
| 4 | as ce lx sp | `linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0))` |
| 2 | rp sp | `linear-gradient(160deg, rgba(244, 154, 110, 0.08), rgb(17, 17, 17) 60%)` |
| 1 | aj | `linear-gradient(170deg, rgba(247, 207, 115, 0.05), rgb(15, 15, 15) 55%)` |
| 1 | as | `linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 30%)` |
| 1 | as | `linear-gradient(90deg, rgba(255, 255, 255, 0.04) 20%, rgba(255, 255, 255, 0.11) 50%, rgba(255, 255, 255, 0.04) 80%)` |
| 1 | ce | `radial-gradient(120% 90% at 50% 55%, rgb(27, 27, 29) 0%, rgb(10, 10, 10) 62%, rgb(0, 0, 0) 100%)` |
| 1 | ct | `linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 28%)` |
| 1 | ct | `linear-gradient(rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.85) 72%)` |
| 1 | ct | `linear-gradient(rgba(27, 27, 29, 0), rgba(27, 27, 29, 0.8) 42%, rgb(27, 27, 29) 82%)` |
| 1 | ct | `linear-gradient(rgb(27, 27, 29) 62%, rgba(27, 27, 29, 0))` |
| 1 | cv | `radial-gradient(120% 52% at 50% 24%,color-mix(in srgb,var(--zc) 14%,transparent),transparent 66%)` |
| 1 | cv | `radial-gradient(closest-side,var(--zc),transparent)` |
| 1 | cv | `linear-gradient(90deg,#ffffff88,var(--zc))` |
| 1 | cv | `linear-gradient(100deg, rgba(255, 255, 255, 0.65) 0px, rgba(255, 255, 255, 0.65) 38%, rgb(255, 255, 255) 50%, rgba(255, 255, 255, 0.65) 62%, rgba(255, 255, 255, 0.65) 100%)` |
| 1 | ed | `linear-gradient(90deg,var(--ink) var(--p,50%),#ffffff1f var(--p,50%))` |
| 1 | ed | `linear-gradient(rgb(0, 0, 0) 20%, rgba(0, 0, 0, 0))` |
| 1 | lv | `linear-gradient(90deg, rgba(242, 238, 230, 0.753), rgb(242, 238, 230))` |
| 1 | lx | `radial-gradient(120% 70% at 28% 18%, rgb(48, 52, 58), transparent 62%), radial-gradient(90% 60% at 80% 90%, rgb(35, 32, 28), transparent 70%), linear-gradient(rgb(20, 22, 26), rgb(10, 11, 12))` |
| 1 | lx | `radial-gradient(closest-side, rgba(242, 238, 230, 0.165), transparent)` |
| 1 | lx | `linear-gradient(rgba(242, 238, 230, 0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(242, 238, 230, 0.05) 1px, transparent 1px)` |
| 1 | lx | `linear-gradient(rgba(10, 10, 10, 0), rgb(10, 10, 10))` |
| 1 | lx | `radial-gradient(60% 40% at 30% 30%, rgb(90, 74, 60), transparent 70%), radial-gradient(50% 35% at 75% 55%, rgb(58, 63, 71), transparent 70%), radial-gradient(70% 50% at 50% 85%, rgb(42, 36, 32), trans` |
| 1 | lx | `linear-gradient(rgba(0, 0, 0, 0) 35%, rgba(0, 0, 0, 0.85) 75%)` |
| 1 | lx | `linear-gradient(rgba(0, 0, 0, 0) 30%, rgba(0, 0, 0, 0.933) 68%)` |
| 1 | nc | `linear-gradient(100deg, rgb(15, 15, 15) 30%, rgb(26, 26, 26) 50%, rgb(15, 15, 15) 70%)` |
| 1 | nc | `linear-gradient(rgba(242, 238, 230, 0.35), rgba(242, 238, 230, 0.35))` |
