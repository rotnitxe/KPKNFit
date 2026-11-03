# Paridad de profundidad y movimiento · `lx` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 13 · vidrio, 4 · degradados 9 · animaciones 15 · transiciones 9 · transformaciones, 12 · movimiento 7 · escenas del paquete: 29

## Sombras (13)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lx-S01 | `.ann` (base) | box-shadow: rgba(0, 0, 0, 0.533) 0px 4px 12px → preset nota | lx-l-medidas | DESV-706 | [~] |
| lx-S02 | `.lx-banner` | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | lx-t-coexiste | FreeClockHeader.kt:113 | [x] |
| lx-S03 | `.lx-cap` | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px → preset flotante | lx-t-sesion, lx-t-cron-cero, lx-t-cron-vueltas, lx-t-cron-pausa, lx-t-temp-config, lx-t-temp-corriendo | WorkoutHeaderBar.kt:488 | [x] |
| lx-S04 | `.lx-cap.lx-glass` | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | lx-t-sesion, lx-t-cron-cero, lx-t-cron-vueltas, lx-t-cron-pausa, lx-t-temp-config, lx-t-temp-corriendo | WorkoutHeaderBar.kt:483 | [x] |
| lx-S05 | `.lx-dock` | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px → preset flotante | lx-t-sesion, lx-t-cron-cero, lx-t-cron-vueltas, lx-t-cron-pausa, lx-t-temp-config, lx-t-temp-corriendo | DESV-708 | [~] |
| lx-S06 | `.lx-dock .lx-steps i.lx-on` | box-shadow: rgba(255, 255, 255, 0.08) 0px 0px 0px 3px → preset anillo-blanco-3-suave | lx-t-sesion, lx-t-cron-cero, lx-t-cron-vueltas, lx-t-cron-pausa, lx-t-temp-config, lx-t-temp-corriendo | DESV-708 | [~] |
| lx-S07 | `.lx-ov` | box-shadow: rgba(0, 0, 0, 0.8) 0px 20px 60px → preset modal | lx-v-tarjeta, lx-v-expandido, lx-v-sin-camara, lx-v-lista | SetCardExerciseMediaBack.kt:1291 | [x] |
| lx-S08 | `.lx-spv .story` | box-shadow: rgba(0, 0, 0, 0.8) 0px 18px 50px, rgba(255, 255, 255, 0.11) 0px 0px 0px 1px inset → preset historia | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | WorkoutStorySheet.kt:137 | [x] |
| lx-S09 | `.lx-sth .story` | box-shadow: rgba(255, 255, 255, 0.11) 0px 0px 0px 1px inset → preset borde-interior | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | FinishSheetUi.kt:615 | [x] |
| lx-S10 | `.lx-tpl button.lx-on .story` | box-shadow: 0 0 0 2px var(--ink) → preset anillo-actual | lx-t-sesion, lx-t-cron-cero, lx-t-cron-vueltas, lx-t-cron-pausa, lx-t-temp-config, lx-t-temp-corriendo | WorkoutStorySheet.kt:164 | [x] |
| lx-S11 | `.lx-tpt .story` | box-shadow: rgba(255, 255, 255, 0.11) 0px 0px 0px 1px inset → preset borde-interior | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | WorkoutStorySheet.kt:164 | [x] |
| lx-S12 | `.pl-sheet` (base) | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | lx-t-sesion, lx-t-cron-cero, lx-t-cron-vueltas, lx-t-cron-pausa, lx-t-temp-config, lx-t-temp-corriendo | DESV-700 | [~] |
| lx-S13 | `.story` (base) | box-shadow: rgba(0, 0, 0, 0.8) 0px 20px 50px, rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset → preset historia | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |

## Vidrio, desenfoques y filtros (4)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lx-V01 | `.lx-banner` | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | lx-t-coexiste | FreeClockHeader.kt:114 | [x] |
| lx-V02 | `.lx-cap.lx-glass` | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | lx-t-sesion, lx-t-cron-cero, lx-t-cron-vueltas, lx-t-cron-pausa, lx-t-temp-config, lx-t-temp-corriendo | WorkoutHeaderBar.kt:484 | [x] |
| lx-V03 | `.lx-glow` | filter: blur(26px) → preset resplandor-desenfoque | lx-f-historia, lx-f-historia-ej, lx-f-historia-fotos, lx-f-historia-error | WorkoutStorySheet.kt:132 | [x] |
| lx-V04 | `.story .photo` (base) | filter: saturate(0.8) → preset foto-apagada | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |

## Degradados y resplandores (9)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lx-G01 | `.lx-cam` | background-image: radial-gradient(120% 70% at 28% 18%, rgb(48, 52, 58), transparent 62%), radial-gradient(90% 60% at 80% 90%, rgb(35, 32, 28), transparent 70%), linear-gradient(rgb(20, 22, 26), rgb(10, 11, 12)) → preset fondo-compuesto-2 | lx-v-tarjeta, lx-v-expandido, lx-v-sin-camara, lx-v-lista | DESV-705 | [~] |
| lx-G02 | `.lx-glow` | background-image: radial-gradient(closest-side, rgba(242, 238, 230, 0.165), transparent) → preset resplandor-crema | lx-f-historia, lx-f-historia-ej, lx-f-historia-fotos, lx-f-historia-error | WorkoutStorySheet.kt:133 | [x] |
| lx-G03 | `.lx-grid` | background-image: linear-gradient(rgba(242, 238, 230, 0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(242, 238, 230, 0.05) 1px, transparent 1px) → preset rejilla | lx-v-tarjeta, lx-v-expandido, lx-v-sin-camara, lx-v-lista | DESV-705 | [~] |
| lx-G04 | `.story .bgart::after` (base) | background-image: linear-gradient(rgba(10, 10, 10, 0), rgb(10, 10, 10)) → preset velo-negro-1 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |
| lx-G05 | `.story .bgart.over::after` (base) | background-image: linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0)) → preset velo-negro-1 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |
| lx-G06 | `.story .photo` (base) | background-image: radial-gradient(60% 40% at 30% 30%, rgb(90, 74, 60), transparent 70%), radial-gradient(50% 35% at 75% 55%, rgb(58, 63, 71), transparent 70%), radial-gradient(70% 50% at 50% 85%, rgb(42, 36, 32), transparent 70%), linear-gradient(160deg, rgb(43, 38, 33), rgb(18, 17, 16)) → preset fondo-compuesto-1 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |
| lx-G07 | `.story .photo::after` (base) | background-image: linear-gradient(rgba(0, 0, 0, 0) 35%, rgba(0, 0, 0, 0.85) 75%) → preset velo-negro-2 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |
| lx-G08 | `.story .photo::after` (base) | background-image: linear-gradient(rgba(0, 0, 0, 0) 30%, rgba(0, 0, 0, 0.933) 68%) → preset velo-negro-3 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |
| lx-G09 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |

## Animaciones (@keyframes) (15)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lx-A01 | `.draw.go .ln` (base) | animation: drawln · 1.6s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1600 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FinishSheetUi.kt:176 | [x] |
| lx-A02 | `.go .burst .ln, .burst.go .ln` (base) | animation: drawln · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva estandar · dur 1000 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | FinishSheetUi.kt:494 | [x] |
| lx-A03 | `.lx-banner` | animation: lxrise · 0.32s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-baja · curva estandar · dur 320 | lx-t-coexiste | FreeClockHeader.kt:100 | [x] |
| lx-A04 | `.lx-fin` | animation: lxsheet · 0.34s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 320 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | DESV-702 | [~] |
| lx-A05 | `.lx-mu` | animation: lxrise2 · 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0.7s · repite 1 · normal · forwards → preset entrada-implicita · curva estandar · dur 520 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | FinishSheetUi.kt:155 | [x] |
| lx-A06 | `.lx-mu i u, .lx-mu i s` | animation: lxgrow · 0.8s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0.7s · repite 1 · normal · both → preset crece-x · curva estandar · dur 800 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | FinishSheetUi.kt:167 | [x] |
| lx-A07 | `.lx-pend` | animation: lxrise · 0.24s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-baja · curva estandar · dur 240 | — | SessionTimeSheet.kt:84 | [x] |
| lx-A08 | `.lx-rgp` | animation: lxring · 1.1s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0.6s · repite 1 · normal · both → preset dibujo-trazo · curva trazo · dur 1000 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FinishSheetUi.kt:446 | [x] |
| lx-A09 | `.lx-rise` | animation: lxrise2 · 0.55s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset entrada-implicita · curva estandar · dur 520 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FinishSheetUi.kt:142 | [x] |
| lx-A10 | `.lx-sparea` | animation: lxfade · 0.6s · ease · retardo 1s · repite 1 · normal · forwards → preset fundido-entrada · curva suave · dur 600 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-guardando, lx-f-error, lx-f-notas | FinishSheetUi.kt:412 | [x] |
| lx-A11 | `.lx-spdot` | animation: lxfade · 0.3s · ease · retardo 1.3s · repite 1 · normal · forwards → preset fundido-entrada · curva suave · dur 280 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-guardando, lx-f-error, lx-f-notas | FinishSheetUi.kt:413 | [x] |
| lx-A12 | `.lx-spin` | animation: lxspin · 0.8s · linear · retardo 0s · repite infinite · normal · none → preset giro · curva lineal · dur 800 | lx-f-guardando | FinishSheetUi.kt:194 | [x] |
| lx-A13 | `.lx-spn` | animation: lxspin · 0.8s · linear · retardo 0s · repite infinite · normal · none → preset giro · curva lineal · dur 800 | — | WorkoutStorySheet.kt:109 | [x] |
| lx-A14 | `.lx-tring.lx-done [data-tarc]` | animation: lxpulse · 1.4s · ease-in-out · retardo 0s · repite 2 · normal · none → preset pulso · curva vaiven · dur 1400 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FreeClockUi.kt:341 | [x] |
| lx-A15 | `.plate .ln` (base) | animation: drawln · 1.4s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1400 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |

## Transiciones (9)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lx-T01 | `.lx-np` | transition: transform · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 280 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FinishSheetUi.kt:719 | [x] |
| lx-T02 | `.lx-ov` | transition: left, top, width, height, border-radius · 0.32s, 0.32s, 0.32s, 0.32s, 0.32s · cubic-bezier(0.4, 0, 0.2, 1), cubic-bezier(0.4, 0, 0.2, 1), cubic-bezier(0.4, 0, 0.2, 1), cubic-bezier(0.4, 0, 0.2, 1), cubic-bezier(0.4, 0, 0.2, 1) · retardo 0s, 0s, 0s, 0s, 0s → preset curva material · dur 320 | lx-v-tarjeta, lx-v-expandido, lx-v-sin-camara, lx-v-lista | SetCardExerciseMediaBack.kt:1242 | [x] |
| lx-T03 | `.lx-ovin` | transition: opacity · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | lx-v-tarjeta, lx-v-expandido, lx-v-sin-camara, lx-v-lista | SetCardExerciseMediaBack.kt:1300 | [x] |
| lx-T04 | `.lx-ovv` | transition: opacity · 0.32s · cubic-bezier(0.4, 0, 0.2, 1) · retardo 0s → preset curva material · dur 320 | lx-v-tarjeta, lx-v-expandido, lx-v-sin-camara, lx-v-lista | SetCardExerciseMediaBack.kt:1280 | [x] |
| lx-T05 | `.lx-ovw.lx-open .lx-ovin` | transition: opacity · 0.2s · ease · retardo 0.12s → preset curva suave · dur 200 | lx-v-tarjeta, lx-v-expandido, lx-v-sin-camara, lx-v-lista | SetCardExerciseMediaBack.kt:1300 | [x] |
| lx-T06 | `.lx-scale i` | transition: background · 0.15s · ease · retardo 0s → preset curva suave · dur 160 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FinishSheetUi.kt:820 | [x] |
| lx-T07 | `.lx-sw` | transition: background · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | FinishSheetUi.kt:640 | [x] |
| lx-T08 | `.lx-sw u` | transition: transform · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | FinishSheetUi.kt:642 | [x] |
| lx-T09 | `.lx-tpt .story` | transition: opacity, box-shadow · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | WorkoutStorySheet.kt:162 | [x] |

## Transformaciones, máscaras y mezcla (12)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lx-X01 | `.lx-art` | mask-image: radial-gradient(80% 100% at 100% 0%, rgb(0, 0, 0) 20%, transparent 75%) | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FinishSheetUi.kt:328 | [x] |
| lx-X02 | `.lx-camsg` | transform: translateY(-50%) | lx-v-sin-camara | SetCardExerciseMediaBack.kt:823 | [x] |
| lx-X03 | `.lx-dl2 svg` | transform: rotate(180deg) | lx-f-historia, lx-f-historia-ej, lx-f-historia-fotos, lx-f-historia-error | FreeClockIcons.kt:45 | [x] |
| lx-X04 | `.lx-fin::before` | transform: translateX(-50%) | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | RecordedSessionFinishSheet.kt:145 | [x] |
| lx-X05 | `.lx-laps` | mask-image: linear-gradient(rgb(0, 0, 0) calc(100% - 22px), transparent) | — | FreeClockUi.kt:410 | [x] |
| lx-X06 | `.lx-list` | mask-image: linear-gradient(transparent 0px, rgb(0, 0, 0) 24px, rgb(0, 0, 0) calc(100% - 28px), transparent 100%) | lx-l-reposo, lx-l-scroll, lx-l-medidas, lx-v-lista | LiveListEdgeMask.kt:22 | [x] |
| lx-X07 | `.lx-np` | transform: translateX(104%) | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FinishSheetUi.kt:723 | [x] |
| lx-X08 | `.lx-rise` | transform: translateY(14px) | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-vacia, lx-f-guardando | FinishSheetUi.kt:145 | [x] |
| lx-X09 | `.lx-sw.lx-on u` | transform: translateX(18px) | lx-t-sesion, lx-t-cron-cero, lx-t-cron-vueltas, lx-t-cron-pausa, lx-t-temp-config, lx-t-temp-corriendo | FinishSheetUi.kt:642 | [x] |
| lx-X10 | `.story .bgart.over` (base) | mix-blend-mode: screen | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |
| lx-X11 | `.story .st-medal` (base) | transform: translate(-50%, -50%) | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |
| lx-X12 | `.story .st-week span` (base) | transform: rotate(180deg) | lx-f-normal, lx-f-record, lx-f-cardio, lx-f-corta, lx-f-guardando, lx-f-error | DESV-703 | [~] |

## Movimiento y estilos hechos en JavaScript (7)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lx-J01 | `scenes-lx.js:173` | requestAnimationFrame · const loop=()=>{if(!root.isConnected)return;tick(false);requestAnimationFrame(loop)}; | — | FreeClockUi.kt:140 | [x] |
| lx-J02 | `scenes-lx.js:192` | requestAnimationFrame · paint();requestAnimationFrame(loop); | — | FreeClockUi.kt:140 | [x] |
| lx-J03 | `scenes-lx.js:272` | estilo en línea desde JS · ov.style.transition='none';put(rel(face),25);w.classList.add('lx-shown');ov.getBoundingClientRect();ov.style.transition=''; | — | SetCardExerciseMediaBack.kt:1237 | [x] |
| lx-J04 | `scenes-lx.js:273` | requestAnimationFrame · if(anim)requestAnimationFrame(()=>{put(tgt(),30);w.classList.add('lx-open')}); | — | SetCardExerciseMediaBack.kt:1242 | [x] |
| lx-J05 | `scenes-lx.js:274` | estilo en línea desde JS · else{ov.style.transition='none';put(tgt(),30);w.classList.add('lx-open');ov.getBoundingClientRect();ov.style.transition=''} | — | SetCardExerciseMediaBack.kt:1237 | [x] |
| lx-J06 | `scenes-lx.js:276` | setTimeout · const fold=()=>{put(rel(face),25);w.classList.remove('lx-open');setTimeout(()=>w.classList.remove('lx-shown'),340)}; | — | SetCardExerciseMediaBack.kt:1245 | [x] |
| lx-J07 | `scenes-lx.js:546` | setTimeout · const draw=(delay)=>{prev.innerHTML='<span class="lx-spn"></span>';clearTimeout(tm);tm=setTimeout(()=>{prev.innerHTML=lxStory(S.tpl,'100%',S);animateI | — | WorkoutStorySheet.kt:92 | [x] |

## @keyframes usados (9)

```css
@keyframes drawln { 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes lxpulse { 0%, 100% { stroke-width: 7; opacity: 1; } 50% { stroke-width: 11; opacity: 0.7; } }
```

```css
@keyframes lxrise { 0% { opacity: 0; transform: translateY(-8px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes lxsheet { 0% { transform: translateY(100%); } 100% { transform: none; } }
```

```css
@keyframes lxfade { 0% { opacity: 0; } 100% { opacity: 1; } }
```

```css
@keyframes lxring { 0% { stroke-dasharray: 0 var(--len); } }
```

```css
@keyframes lxrise2 { 100% { opacity: 1; transform: none; } }
```

```css
@keyframes lxgrow { 0% { transform: scaleX(0); } 100% { transform: none; } }
```

```css
@keyframes lxspin { 100% { transform: rotate(360deg); } }
```
