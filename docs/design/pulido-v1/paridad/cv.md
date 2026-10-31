# Paridad de profundidad y movimiento · `cv` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 16 · vidrio, 8 · degradados 6 · animaciones 30 · transiciones 24 · transformaciones, 9 · movimiento 20 · escenas del paquete: 30

## Sombras (16)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| cv-S01 | `.cv-bar i::after` | box-shadow: 0 0 12px var(--zc) → preset resplandor-zona-12 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioNowContent.kt:113  |  [x]  |
| cv-S02 | `.cv-cnt .n` | text-shadow: rgb(0, 0, 0) 0px 0px 40px → preset texto-resplandor-negro-40 | cv-cuenta, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:477  |  [x]  |
| cv-S03 | `.cv-dot` | box-shadow: 0 0 10px color-mix(in srgb,var(--zc) 75%,transparent) → preset resplandor-zona-10 | cv-trans-in, cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona |  CardioReadyPulido.kt:59  |  [x]  |
| cv-S04 | `.cv-mod.warn` | box-shadow: 0 0 28px color-mix(in srgb,var(--wc,var(--zc)) 14%,transparent) → preset resplandor-zona-28 | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  CardioNowContent.kt:214  |  [x]  |
| cv-S05 | `.cv-prof .ps` | box-shadow: rgb(17, 17, 17) 0px 0px 0px 1px → preset corte-hoja | cv-trans-in, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:279  |  [x]  |
| cv-S06 | `.cv-r .rd i` | box-shadow: rgb(0, 0, 0) 0px 0px 0px 4px → preset anillo-negro-4 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveCard.kt:616  |  [x]  |
| cv-S07 | `.cv-r.d .rd i` | box-shadow: rgb(0, 0, 0) 0px 0px 0px 3px → preset anillo-negro-4 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveCard.kt:616  |  [x]  |
| cv-S08 | `.cv-r.n .rd i` | box-shadow: 0 0 0 4px #000,0 0 14px var(--c) → preset resplandor-zona-14-capas | cv-cuenta, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveCard.kt:616  |  [x]  |
| cv-S09 | `.cv-sw .kn` | box-shadow: rgba(0, 0, 0, 0.533) 0px 4px 18px → preset caida-4-18 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:697  |  [x]  |
| cv-S10 | `.cv-tape .nd` | box-shadow: rgb(17, 17, 17) 0px 0px 0px 2px → preset corte-hoja | cv-trans-in, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:247  |  [x]  |
| cv-S11 | `.cv-time` | text-shadow: rgb(0, 0, 0) 0px 2px 26px, rgba(0, 0, 0, 0.667) 0px 0px 6px → preset texto-2-26-capas | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  CardioNowContent.kt:98  |  [x]  |
| cv-S12 | `.cv-tsub` | text-shadow: rgb(0, 0, 0) 0px 1px 12px → preset texto-1-12 | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  CardioNowContent.kt:92  |  [x]  |
| cv-S13 | `.cv-wt .cap` | text-shadow: rgb(0, 0, 0) 0px 1px 8px → preset texto-1-12 | cv-ondas |  CardioNowContent.kt:89  |  [x]  |
| cv-S14 | `.pl-glass` (base) | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | cv-flujo, cv-descanso, cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta |  CardioLiveSpace.kt:351  |  [x]  |
| cv-S15 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  DESV-800  |  [~]  |
| cv-S16 | `.vbar .z` (base) | box-shadow: rgba(67, 209, 140, 0.333) 0px 0px 0px 1px inset → preset interior-hecho-1 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-pulso-manual |  DESV-800  |  [~]  |

## Vidrio, desenfoques y filtros (8)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| cv-V01 | `.cv-cnt p` | backdrop-filter: blur(6px) → preset velo-suave | cv-cuenta |  CardioLiveSpace.kt:478  |  [x]  |
| cv-V02 | `.cv-cue span` | backdrop-filter: blur(8px) → preset velo-desenfoque | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-gps |  CardioNowContent.kt:207  |  [x]  |
| cv-V03 | `.cv-halo` | filter: blur(20px) → preset halo-desenfoque | cv-listo, cv-listo-gps, cv-cuenta, cv-calentamiento, cv-trabajo, cv-sobre-zona |  DESV-101  |  [~]  |
| cv-V04 | `.cv-link` | backdrop-filter: blur(12px) → preset velo-desenfoque | cv-trans-in, cv-listo, cv-listo-gps, cv-terminado, cv-terminado-vacio, cv-terminado-error |  CardioReadyPulido.kt:95  |  [x]  |
| cv-V05 | `.cv-sheetwrap .pl-scrim` | backdrop-filter: blur(3px) → preset velo-suave | — |  CardioLiveCard.kt:507  |  [x]  |
| cv-V06 | `.cv-sw` | backdrop-filter: blur(16px) saturate(1.4) → preset vidrio-interruptor | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:687  |  [x]  |
| cv-V07 | `.cv-veil` | backdrop-filter: blur(10px) → preset velo-desenfoque | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveSpace.kt:456  |  [x]  |
| cv-V08 | `.pl-glass` (base) | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | cv-flujo, cv-descanso, cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta |  CardioLiveSpace.kt:351  |  [x]  |

## Degradados y resplandores (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| cv-G01 | `.cv-bg` | background-image: radial-gradient(120% 52% at 50% 24%,color-mix(in srgb,var(--zc) 14%,transparent),transparent 66%) → preset resplandor-ancho-zona | cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta, cv-calentamiento, cv-trabajo |  CardioLiveCard.kt:302  |  [x]  |
| cv-G02 | `.cv-halo` | background-image: radial-gradient(closest-side,var(--zc),transparent) → preset resplandor-zona | cv-listo, cv-listo-gps, cv-cuenta, cv-calentamiento, cv-trabajo, cv-sobre-zona |  CardioNowContent.kt:226  |  [x]  |
| cv-G03 | `.cv-route .ln i` | background-image: linear-gradient(90deg,#ffffff88,var(--zc)) → preset relleno-horizontal-blanco | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:546  |  [x]  |
| cv-G04 | `.cv-sw .slb` | background-image: linear-gradient(100deg, rgba(255, 255, 255, 0.65) 0px, rgba(255, 255, 255, 0.65) 38%, rgb(255, 255, 255) 50%, rgba(255, 255, 255, 0.65) 62%, rgba(255, 255, 255, 0.65) 100%) → preset brillo-esqueleto-2 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:691  |  [x]  |
| cv-G05 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  DESV-800  |  [~]  |
| cv-G06 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  DESV-800  |  [~]  |

## Animaciones (@keyframes) (30)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| cv-A01 | `.cv-big span` | animation: cv-rise · 0.6s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 600 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:549  |  [x]  |
| cv-A02 | `.cv-cmp i` | animation: cv-grow · 0.6s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo var(--d,0s) · repite 1 · normal · both → preset crece-y · curva estandar · dur 600 | cv-terminado, cv-terminado-vacio, cv-terminado-error |  CardioLiveCard.kt:752  |  [x]  |
| cv-A03 | `.cv-cnt .n` | animation: cv-cn · 0.9s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset asentar · curva estandar · dur 1000 | cv-cuenta, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:477  |  [x]  |
| cv-A04 | `.cv-cnt .rg` | animation: cv-rg · 0.9s · ease-out · retardo 0s · repite 1 · normal · both → preset onda · curva salida · dur 1000 | cv-cuenta |  CardioLiveSpace.kt:479  |  [x]  |
| cv-A05 | `.cv-halo` | animation: cv-beat · 1.1s · ease-out · retardo 0s · repite infinite · normal · none → preset latido · curva salida · dur 1000 | cv-listo, cv-listo-gps, cv-cuenta, cv-calentamiento, cv-trabajo, cv-sobre-zona |  CardioNowContent.kt:225  |  [x]  |
| cv-A06 | `.cv-hr svg` | animation: cv-heart · 1.1s · ease-out · retardo 0s · repite infinite · normal · none → preset latido · curva salida · dur 1000 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioNowContent.kt:122  |  [x]  |
| cv-A07 | `.cv-lead` | animation: cv-rise · 0.5s · ease · retardo 0.75s · repite 1 · normal · both → preset entrada-sube · curva suave · dur 520 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:551  |  [x]  |
| cv-A08 | `.cv-nxc` | animation: cv-rise · 0.6s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0.9s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 600 | cv-trans-out |  CardioLiveSpace.kt:630  |  [x]  |
| cv-A09 | `.cv-pg.cv-enter > *` | animation: cv-rise · 0.42s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 400 | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  CardioLiveCard.kt:425  |  [x]  |
| cv-A10 | `.cv-prof i` | animation: cv-grow · 0.6s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo var(--d,0s) · repite 1 · normal · both → preset crece-y · curva estandar · dur 600 | cv-trans-in, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:269  |  [x]  |
| cv-A11 | `.cv-rip` | animation: cv-rip · 0.55s · ease-out · retardo 0s · repite 1 · normal · forwards → preset onda · curva salida · dur 520 | — |  CardioLiveSpace.kt:387  |  [x]  |
| cv-A12 | `.cv-root.cv-in > *` | animation: cv-fadein · 0.32s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva estandar · dur 320 | cv-flujo, cv-descanso, cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta |  CardioLiveCard.kt:296  |  [x]  |
| cv-A13 | `.cv-root.locked .cv-lockcap` | animation: cv-pop · 0.35s · ease · retardo 0s · repite 1 · normal · both → preset rebote-aparece · curva suave · dur 320 | cv-flujo, cv-descanso, cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta |  CardioLiveCard.kt:458  |  [x]  |
| cv-A14 | `.cv-root.paused .cv-veil` | animation: cv-fadein · 0.3s · ease · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva suave · dur 280 | cv-flujo, cv-descanso, cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta |  CardioLiveSpace.kt:454  |  [x]  |
| cv-A15 | `.cv-route .ln i` | animation: cv-bar · 1s · cubic-bezier(0.5, 0, 0.2, 1) · retardo 0.25s · repite 1 · normal · both → preset crece-x · curva ruta · dur 1000 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:546  |  [x]  |
| cv-A16 | `.cv-route .nd.go` | animation: cv-ignite · 0.8s · ease · retardo 1.1s · repite 1 · normal · both → preset ignicion · curva suave · dur 800 | cv-trans-in, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:547  |  [x]  |
| cv-A17 | `.cv-sheetwrap .pl-scrim` | animation: cv-fadein · 0.3s · ease · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva suave · dur 280 | — |  CardioLiveCard.kt:507  |  [x]  |
| cv-A18 | `.cv-sheetwrap .pl-sheet` | animation: cv-sheet · 0.42s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 400 | — |  CardioLiveCard.kt:507  |  [x]  |
| cv-A19 | `.cv-sw` | animation: cv-rise · 0.5s · ease · retardo 1.25s · repite 1 · normal · both → preset entrada-sube · curva suave · dur 520 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:686  |  [x]  |
| cv-A20 | `.cv-sw .slb` | animation: cv-shim · 2.6s · linear · retardo 1.8s · repite infinite · normal · none → preset brillo-barrido · curva lineal · dur 2400 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:691  |  [x]  |
| cv-A21 | `.cv-sw.done` | animation: cv-swdone · 0.5s · ease · retardo 0s · repite 1 · normal · both → preset destello · curva suave · dur 520 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:700  |  [x]  |
| cv-A22 | `.cv-tc` | animation: cv-rise · 0.6s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0.95s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 600 | cv-trans-in |  CardioLiveSpace.kt:553  |  [x]  |
| cv-A23 | `.cv-tick` | animation: cv-pop · 0.5s · ease · retardo 0.15s · repite 1 · normal · both → preset rebote-aparece · curva suave · dur 520 | cv-registrado |  CardioLiveCard.kt:801  |  [x]  |
| cv-A24 | `.cv-time.flip` | animation: cv-rise · 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 520 | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  CardioNowContent.kt:98  |  [x]  |
| cv-A25 | `.cv-toast` | animation: cv-pop · 0.38s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset rebote-aparece · curva estandar · dur 400 | — |  CardioLiveCard.kt:488  |  [x]  |
| cv-A26 | `.cv-tz .z i` | animation: cv-bar · 0.8s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset crece-x · curva estandar · dur 800 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-pulso-manual |  CardioLiveCard.kt:670  |  [x]  |
| cv-A27 | `.draw.go .ln` (base) | animation: drawln · 1.6s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1600 | cv-trans-in, cv-trans-out |  DESV-800  |  [~]  |
| cv-A28 | `.go .burst .ln, .burst.go .ln` (base) | animation: drawln · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva estandar · dur 1000 | cv-trans-in, cv-trans-out |  DESV-800  |  [~]  |
| cv-A29 | `.plate .ln` (base) | animation: drawln · 1.4s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1400 | cv-trans-in, cv-trans-out |  DESV-800  |  [~]  |
| cv-A30 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  DESV-800  |  [~]  |

## Transiciones (24)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| cv-T01 | `.cv-bar i` | transition: width · 0.9s · linear · retardo 0s → preset curva lineal · dur 1000 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioNowContent.kt:110  |  [x]  |
| cv-T02 | `.cv-bg` | transition: background · 0.7s · ease · retardo 0s → preset curva suave · dur 700 | cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta, cv-calentamiento, cv-trabajo |  CardioLiveCard.kt:302  |  [x]  |
| cv-T03 | `.cv-c` | transition: background, opacity · 0.25s, 0.25s · ease, ease · retardo 0s, 0s → preset curva suave · dur 240 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveSpace.kt:396  |  [x]  |
| cv-T04 | `.cv-dock` | transition: transform · 0.35s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 320 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveSpace.kt:351  |  [x]  |
| cv-T05 | `.cv-dot` | transition: background, box-shadow · 0.5s, 0.5s · ease, ease · retardo 0s, 0s → preset curva suave · dur 520 | cv-trans-in, cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona |  CardioNowContent.kt:59  |  [x]  |
| cv-T06 | `.cv-halo` | transition: background · 0.6s · ease · retardo 0s → preset curva suave · dur 600 | cv-listo, cv-listo-gps, cv-cuenta, cv-calentamiento, cv-trabajo, cv-sobre-zona |  CardioNowContent.kt:225  |  [x]  |
| cv-T07 | `.cv-ind` | transition: left · 0.42s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 400 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveSpace.kt:356  |  [x]  |
| cv-T08 | `.cv-mod` | transition: border-color, box-shadow · 0.4s, 0.4s · ease, ease · retardo 0s, 0s → preset curva suave · dur 400 | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  CardioNowContent.kt:216  |  [x]  |
| cv-T09 | `.cv-pg` | transition: opacity · 0.3s · ease · retardo 0s → preset curva suave · dur 280 | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  CardioLiveCard.kt:390  |  [x]  |
| cv-T10 | `.cv-prof .ps` | transition: left · 0.9s · linear · retardo 0s → preset curva lineal · dur 1000 | cv-trans-in, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:279  |  [x]  |
| cv-T11 | `.cv-prof i` | transition: opacity · 0.4s · ease · retardo 0s → preset curva suave · dur 400 | cv-trans-in, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:269  |  [x]  |
| cv-T12 | `.cv-r` | transition: background · 0.3s · ease · retardo 0s → preset curva suave · dur 280 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveCard.kt:611  |  [x]  |
| cv-T13 | `.cv-r.n::after` | transition: width · 0.9s · linear · retardo 0s → preset curva lineal · dur 1000 | cv-cuenta, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveCard.kt:612  |  [x]  |
| cv-T14 | `.cv-root` | transition: opacity · 0.22s · ease · retardo 0s → preset curva suave · dur 200 | cv-flujo, cv-descanso, cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta |  CardioLiveCard.kt:296  |  [x]  |
| cv-T15 | `.cv-root.locked .cv-pages, .cv-root.locked .cv-head` | transition: opacity · 0.3s · ease · retardo 0s → preset curva suave · dur 280 | cv-flujo, cv-descanso, cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta |  CardioLiveCard.kt:458  |  [x]  |
| cv-T16 | `.cv-rpe button` | transition: background, color, transform · 0.25s, 0.25s, 0.25s · ease, ease, ease · retardo 0s, 0s, 0s → preset curva suave · dur 240 | cv-terminado, cv-terminado-vacio, cv-terminado-error |  CardioLiveSpace.kt:506  |  [x]  |
| cv-T17 | `.cv-sheetwrap.out .pl-scrim` | transition: opacity · 0.25s · ease · retardo 0s → preset curva suave · dur 240 | — |  CardioLiveCard.kt:507  |  [x]  |
| cv-T18 | `.cv-sheetwrap.out .pl-sheet` | transition: transform · 0.25s · ease-in · retardo 0s → preset curva acelera · dur 240 | — |  CardioLiveCard.kt:507  |  [x]  |
| cv-T19 | `.cv-sw.done .kn` | transition: left · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 280 | cv-trans-in, cv-trans-out |  CardioLiveSpace.kt:700  |  [x]  |
| cv-T20 | `.cv-tabs button` | transition: color · 0.25s · ease · retardo 0s → preset curva suave · dur 240 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveSpace.kt:362  |  [x]  |
| cv-T21 | `.cv-tape .bars i` | transition: height, opacity · 0.3s, 0.3s · ease, ease · retardo 0s, 0s → preset curva suave · dur 280 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioNowContent.kt:136  |  [x]  |
| cv-T22 | `.cv-tape .nd` | transition: left · 0.9s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 1000 | cv-trans-in, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion |  CardioLiveSpace.kt:247  |  [x]  |
| cv-T23 | `.cv-toast` | transition: opacity, transform · 0.35s, 0.35s · ease, ease · retardo 0s, 0s → preset curva suave · dur 320 | — |  CardioLiveCard.kt:488  |  [x]  |
| cv-T24 | `.cv-track` | transition: transform · 0.42s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 400 | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveCard.kt:414  |  [x]  |

## Transformaciones, máscaras y mezcla (9)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| cv-X01 | `.cv-finwave .cv-wave` | mask-image: linear-gradient(rgb(0, 0, 0), rgb(0, 0, 0) 60%, rgba(0, 0, 0, 0)) | cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta, cv-calentamiento, cv-trabajo |  KpknWaveField.kt:83  |  [x]  |
| cv-X02 | `.cv-lockcap` | transform: translateX(-50%) | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveCard.kt:458  |  [x]  |
| cv-X03 | `.cv-rip` | transform: scale(0) | — |  CardioLiveSpace.kt:387  |  [x]  |
| cv-X04 | `.cv-rpe button.on` | transform: translateY(-3px) | cv-listo, cv-listo-gps, cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona |  CardioLiveSpace.kt:506  |  [x]  |
| cv-X05 | `.cv-sheetwrap.out .pl-sheet` | transform: translateY(100%) | — |  CardioLiveCard.kt:507  |  [x]  |
| cv-X06 | `.cv-toast` | transform: translateX(-50%) | — |  CardioLiveCard.kt:488  |  [x]  |
| cv-X07 | `.cv-toast.out` | transform: translateX(-50%) translateY(8px) | — |  CardioLiveCard.kt:488  |  [x]  |
| cv-X08 | `.cv-track` | transform: translateX(calc(var(--pg,0)*-33.3333%)) | cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo |  CardioLiveCard.kt:414  |  [x]  |
| cv-X09 | `.cv-wave` | mask-image: linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0) 14%, rgb(0, 0, 0) 82%, rgba(0, 0, 0, 0)) | cv-trans-in, cv-listo, cv-listo-gps, cv-cuenta, cv-calentamiento, cv-trabajo |  KpknWaveField.kt:81  |  [x]  |

## Movimiento y estilos hechos en JavaScript (20)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| cv-J01 | `scenes-cv.js:114` | estilo en línea desde JS · a.setAttribute('stroke',zc);g.setAttribute('stroke',zc);a.setAttribute('stroke-width','2.2');a.setAttribute('opacity',cur.acc.toFixed(2));g.style.opac | — |  KpknWaveField.kt:85  |  [x]  |
| cv-J02 | `scenes-cv.js:128` | requestAnimationFrame · requestAnimationFrame(frame); | — |  KpknWaveField.kt:10  |  [x]  |
| cv-J03 | `scenes-cv.js:131` | requestAnimationFrame · else{Object.assign(cur,{...CVW[host.dataset.w\ | \ |  KpknWaveField.kt:43  |  [x]  | 'work'].amp});draw();requestAnimationFrame(frame)} | — |  | [ ] |
| cv-J04 | `scenes-cv.js:138` | setTimeout · const cvRipple=(btn,e)=>{const r=btn.getBoundingClientRect(),zoom=r.width/(btn.offsetWidth\ | \ |  CardioLiveSpace.kt:387  |  [x]  | 1,s=Math.max(r.width,r.height)*1.2;const sp=docu | — |  | [ ] |
| cv-J05 | `scenes-cv.js:141` | setInterval · const cvEvery=(root,fn,ms)=>{const sid=root.dataset.sid;const id=setInterval(()=>{if(!root.isConnected\ | \ |  CardioLiveCard.kt:233  |  [x]  |  | [ ] |
| cv-J06 | `scenes-cv.js:142` | setTimeout · const cvLater=(root,fn,ms)=>{const sid=root.dataset.sid;setTimeout(()=>{if(root.isConnected&&root.dataset.sid===sid)fn()},ms)}; | — |  CardioLiveCard.kt:235  |  [x]  |
| cv-J07 | `scenes-cv.js:147` | estilo en línea desde JS · const set=v=>{dx=Math.max(0,Math.min(max,v));kn.style.left=(5+dx)+'px';fill.style.width=(62+dx)+'px';const l=sw.querySelector('.slb');if(l)l.style.opa | — |  CardioLiveSpace.kt:702  |  [x]  |
| cv-J08 | `scenes-cv.js:148` | setTimeout · const finish=()=>{if(done)return;done=true;sw.classList.add('done');set(max);setTimeout(onDone,320)}; | — |  CardioLiveSpace.kt:700  |  [x]  |
| cv-J09 | `scenes-cv.js:151` | setTimeout · const up=()=>{if(x0==null\ | \ |  CardioLiveSpace.kt:701  |  [x]  |  | [ ] |
| cv-J10 | `scenes-cv.js:284` | setTimeout · setTimeout(()=>{ | — |  CardioLiveSpace.kt:541  |  [x]  |
| cv-J11 | `scenes-cv.js:291` | setTimeout · setTimeout(()=>root.classList.remove('cv-in'),420); | — |  CardioLiveCard.kt:296  |  [x]  |
| cv-J12 | `scenes-cv.js:304` | setTimeout · const cvToast=(scr,text)=>{const t=document.createElement('div');t.className='cv-toast pl-glass';t.textContent=text;scr.appendChild(t);setTimeout(()=> | — |  CardioLiveCard.kt:488  |  [x]  |
| cv-J13 | `scenes-cv.js:342` | estilo en línea desde JS · p0.querySelectorAll('.cv-prof i').forEach(i=>i.style.animation='none'); | — |  CardioLiveSpace.kt:269  |  [x]  |
| cv-J14 | `scenes-cv.js:352` | requestAnimationFrame · const step=()=>{if(t0==null)return;const p=Math.min(1,(performance.now()-t0)/1200);arc.setAttribute('stroke-dasharray',(p*C)+' '+C);if(p>=1){t0=null;s | — |  CardioLiveSpace.kt:412  |  [x]  |
| cv-J15 | `scenes-cv.js:353` | requestAnimationFrame · h.addEventListener('pointerdown',e=>{t0=performance.now();try{h.setPointerCapture(e.pointerId)}catch(_){}raf=requestAnimationFrame(step)}); | — |  CardioLiveSpace.kt:414  |  [x]  |
| cv-J16 | `scenes-cv.js:374` | setTimeout · const close=()=>{w.classList.add('out');setTimeout(()=>w.remove(),260)}; | — |  CardioLiveCard.kt:517  |  [x]  |
| cv-J17 | `scenes-cv.js:377` | setTimeout · paint();if(!o.sheet)setTimeout(()=>{try{inp.focus()}catch(_){}} ,300); | — |  CardioLiveCard.kt:507  |  [x]  |
| cv-J18 | `scenes-cv.js:427` | estilo en línea desde JS · if(drag){let off=-S.page*pages.offsetWidth+dx;const min=-2*pages.offsetWidth;if(off>0)off*=.35;if(off<min)off=min+(off-min)*.35;track.style.transform= | — |  CardioLiveCard.kt:414  |  [x]  |
| cv-J19 | `scenes-cv.js:431` | setTimeout · if(was){track.classList.remove('drag');track.style.transform='';root._noclick=true;setTimeout(()=>{root._noclick=false},60); | — |  CardioLiveCard.kt:414  |  [x]  |
| cv-J20 | `scenes-cv.js:498` | declaración CSS dentro del marcado · <div class="cv-rring"><svg viewBox="0 0 ${R} ${R}"><circle cx="${R/2}" cy="${R/2}" r="${rr}" fill="none" stroke="#ffffff0d" stroke-width="10"/><circle | — |  CardioLiveSpace.kt:280  |  [x]  |

## @keyframes usados (16)

```css
@keyframes drawln { 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes cv-fadein { 0% { opacity: 0; } 100% { opacity: 1; } }
```

```css
@keyframes cv-rise { 0% { opacity: 0; transform: translateY(14px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes cv-beat { 0% { opacity: 0.34; transform: scale(1.06); } 45% { opacity: 0.18; transform: scale(1); } 100% { opacity: 0.2; } }
```

```css
@keyframes cv-heart { 0% { transform: scale(1.22); } 40% { transform: scale(1); } 100% { transform: scale(1); } }
```

```css
@keyframes cv-grow { 0% { transform: scaleY(0.05); opacity: 0; } 100% { transform: scaleY(1); } }
```

```css
@keyframes cv-bar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes cv-rip { 100% { transform: scale(1); opacity: 0; } }
```

```css
@keyframes cv-pop { 0% { opacity: 0; transform: scale(0.86); } 60% { opacity: 1; transform: scale(1.03); } 100% { transform: scale(1); } }
```

```css
@keyframes cv-ignite { 0% { box-shadow: transparent 0px 0px 0px 0px; } 40% { box-shadow: 0 0 0 10px color-mix(in srgb,var(--zc) 30%,transparent); } 100% { box-shadow: 0 0 22px color-mix(in srgb,var(--zc) 40%,transparent); } }
```

```css
@keyframes cv-shim { 0% { background-position: 120% 0px; } 100% { background-position: -60% 0px; } }
```

```css
@keyframes cv-swdone { 100% { background: var(--ink); } }
```

```css
@keyframes cv-cn { 0% { opacity: 0; transform: scale(1.5); } 30% { opacity: 1; } 100% { opacity: 1; transform: scale(1); } }
```

```css
@keyframes cv-rg { 0% { transform: scale(0.5); opacity: 0.9; } 100% { transform: scale(3.4); opacity: 0; } }
```

```css
@keyframes cv-sheet { 0% { transform: translateY(100%); } 100% { transform: none; } }
```
