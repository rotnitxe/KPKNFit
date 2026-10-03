# Curaduría integral de programas — evidencia e índice

> **Estado al redactar** (2026-10-03, HEAD `4aa1bbb10`): hechos F0.1, A1–A2, C.P1–C.P2, C.P2b, D.M1–M5 y B.S1; F0.3 queda completo en lo archivable con este archivo; en curso A.B1, A.B2, A.B3, A.B7 y B.S2; todo lo demás está pendiente. La fuente es la matriz de la sección 4; este encabezado solo la resume.

| Campo | Valor |
|---|---|
| Fecha | 2026-10-03 |
| Rama | `consolidation/2026-10-02-wizard-session-repair` |
| Flavor | Base (el flavor Health no se usa) |
| Plan aprobado | [00-PLAN-curaduria-programas-2026-10-03.md](00-PLAN-curaduria-programas-2026-10-03.md) |
| Baseline de pruebas | X-full-2: 4 270 tests JVM en verde (plan 00, línea 3) |
| Registro de decisiones | [docs/WIZARD_PLAN_DEVIATIONS.md](../../WIZARD_PLAN_DEVIATIONS.md) |
| Estado vigente del catálogo de ejercicios | [catalog/exercises/v2/curation/STATUS.md](../../../catalog/exercises/v2/curation/STATUS.md) (96 familias, 206 definiciones, 527 configuraciones) |

## 1. Qué es esta carpeta

Aquí está la evidencia de la curaduría integral de programas de KPKN Fit (editorial, lógica de recetas, progreso y cobertura), aprobada el 2026-10-03: el plan, dos auditorías y un diseño. Las auditorías son lectura estática del código con referencias archivo:línea. No se ejecutó nada para escribirlas, así que los efectos numéricos que citan salen de leer código, no de medirlo. La carpeta sirve para trazar cada cambio: un hallazgo (sección 3) apunta al paso del plan que lo resuelve, y ese paso al commit que lo entregó (sección 4). Para seguir un hallazgo, busca su id en la sección 3, lee su paso y su estado, y abre el commit con `git show <hash>`.

## 2. Índice de documentos

| Archivo | Qué contiene | Secciones clave (número de línea del archivo) |
|---|---|---|
| [00-PLAN-curaduria-programas-2026-10-03.md](00-PLAN-curaduria-programas-2026-10-03.md) (242 líneas) | Plan aprobado: contexto, decisiones, paquetes A a D, Fase 0, pasos de la Fase 1, Fases 2 y 3, orden, verificación y riesgos. Es el sitio principal donde quedan resumidos los hallazgos P-, A-, B-, N-, R- y Q- | §1 Contexto (l.10) · §2 decisiones previas del dueño (l.23) · §3 decisiones D1–D7 (l.29) · §4 paquetes A–D (l.45) · §4b modo de ejecución (l.60) · §5 Fase 0, F0.0 a F0.4 (l.70) · §6 Fase 1: paquete A (l.82), B (l.115), C (l.163), D (l.179) · §7 Fase 2 (l.195) · §8 Fase 3 (l.205) · §9 orden y dependencias (l.211) · §10 verificación (l.223) · §11 riesgos (l.234) |
| [01-auditoria-editorial-catalogo-planes.md](01-auditoria-editorial-catalogo-planes.md) (723 líneas; en el plan: `informe_editorial.md`) | Auditoría editorial de las 55 entradas del catálogo de planes: inventario, veredicto por entrada, hallazgos E-01 a E-39, mapa de superficies de UI y tests que fijan el texto | §1 inventario: conteos (l.45), NATIVE (l.84), TEMPLATE (l.103), PROTOCOL (l.120), descripciones íntegras (l.187), entradas ocultas (l.250) · §2.1 veredicto por entrada (l.277) · §2.2 ALTA, E-01 a E-21 (l.334) · §2.3 MEDIA, E-22 a E-35 (l.520) · §2.4 BAJA, E-36 a E-39 (l.609) · §2.5 prioridades (l.629) · §3 mapa de superficies de UI (l.636) · §4 tests que fijan el editorial (l.679) · §5 lo que no se verificó (l.711) |
| [02-auditoria-logica-recetas-fijas.md](02-auditoria-logica-recetas-fijas.md) (818 líneas; en el plan: `informe_recetas.md`) | Auditoría de la lógica de las recetas fijas: modelo y reglas del validador, ficha por receta, progresión declarada frente a ejecutada, hallazgos L-01 a L-35 y hallazgos C-01 a C-12 del catálogo de ejercicios que usan las recetas | §1 modelo y validación (l.10; reglas l.30; lo que no se valida l.72) · §2.0 inventario (l.97) · §2.1 arquetipos (l.108) · §2.2 fichas R-01 a R-29, A-01 a A-04 y T-01 a T-07 (l.151) · §2.3 progresión declarada frente a ejecutada (l.466) · §2.4 equilibrio semanal (l.484) · §2.5 hallazgos L-: ALTA (l.519), MEDIA (l.580), BAJA y validadores (l.706) · §3 catálogo referenciado, C-01 a C-12 (l.734) · §4 tests a actualizar (l.782) · §5 lo que no se verificó (l.808) |
| [11-diseno-editorial-y-catalogo.md](11-diseno-editorial-y-catalogo.md) (578 líneas; en el plan: `plan_editorial.md`) | Diseño del modelo editorial único, tabla de las 55 entradas con sus textos finales, hoja «Cómo funciona», mensajes de rechazo, glosario, altas M1–M5 y D1–D4 del catálogo, tests y decisiones D1–D13 del diseño | §0 diez hallazgos nuevos que cambian el diseño (l.6) · §1 modelo editorial (l.19) · §2 tabla de las 55 (l.104): propios (l.114), históricos (l.125), plantillas (l.142), métodos de tercero (l.166), KPKN con receta fija (l.206), autoradas (l.216), atribución (l.229), textos alternativos (l.244), mapa de correcciones (l.255) · §3 hoja «Cómo funciona» (l.269) · §4 rechazos y botones (l.315) · §5 detalle del programa (l.349) · §6 glosario (l.373) · §7 altas del catálogo: lista (l.417) y procedimiento (l.441) · §8 tests (l.485) · §9 orden, riesgos (l.549) y decisiones D1–D13 (l.558) |

**Cómo leer los números de línea.** En 01, 02 y 11 la línea 1 es la cabecera de traspaso del subagente que produjo el informe (no es contenido de la auditoría) y todas las demás van con dos espacios de sangría. Los números de esta tabla y de la sección 3 son los de esos archivos tal como están.

**Lo que no está en esta carpeta.** El plan habla de 6 auditorías y 3 diseños (plan 00, l.5-6) y aquí solo hay tres informes: 01, 02 y 11. Los informes de pipeline, generador nativo, runtime de progresión y catálogo v2, y el resto de los diseños, no se archivaron como archivos: quedaron en el transcript de la sesión y solo se conoce de ellos lo que resume el plan 00 (su §1 Contexto y las tablas de su §6). Sus hallazgos (prefijos P-, A-, B-, N-, R-, Q- y parte de G-) se indexan en la sección 3 con el texto que da el plan, sin completarlo.

### Convenciones de identificadores

Varios identificadores se parecen y significan cosas distintas. Esta tabla evita cruzarlos.

| Forma | Qué es | Dónde |
|---|---|---|
| `E-nn` | Hallazgo editorial | 01 §2.2 a §2.4 |
| `L-nn` | Hallazgo de lógica de receta | 02 §2.5 |
| `C-nn` | Hallazgo del catálogo de ejercicios que usan las recetas (parches de técnica) | 02 §3 |
| `P-`, `A-`, `B-`, `N-`, `R-`, `Q-`, `G-` con guion | Hallazgos de informes no archivados; los `G-` de las altas también están en 11 §7.1 | plan 00, 11 |
| `R-01` a `R-29`, `A-01` a `A-04`, `T-01` a `T-07` en 02 §2.2 | **No son hallazgos**: numeran las recetas (29 protocolos visibles), los 4 autorados y las 7 plantillas. No se confunden con los `R-` y `A-` del plan 00 | 02 §2.2 |
| `L-36` | Aparece solo como referencia en la ficha R-01 (02, l.169); no existe un hallazgo con ese id | 02 |
| `B-03` en 11 §0 y §4 | Es el B-03 del cierre de la consolidación (aviso de volumen alto y notas del plan en la revisión, `artifacts/consolidation-20261001/ROADMAP_CIERRE.md`, l.207), distinto del B-03 del plan 00 (gates con evidencia, paso A.B2) | 11, plan 00 |
| `R13`, `R14`, `R16` sin guion | Etiquetas del informe de auditoría del cierre de la consolidación (progresión de cardio y de carga de PHUL y PHAT); no están en r2 ni son los `R-13`, `R-14`, `R-16` del plan 00 | `WIZARD_PLAN_DEVIATIONS.md` |
| `Q1` a `Q4` sin guion | Nombres de pruebas de T006 (`PlanGenerationCoverageT006Test` y `SetupExecutableAvailabilityMatrixTest`); no son hallazgos `Q-nn` | tests |
| `C1` a `C5` | Cláusulas del contrato de cobertura | plan 00 §6, paquete A |
| `C1_SET_RANGE` a `C10_IDENTICAL_WEEKS`, `H11b` | Reglas del contrato de receta válida | plan 00 §6, B.S1–S2 |
| `A1`–`A3`, `A.B1`–`A.E2`, `B.S1`–`B.S13`, `C.P1`–`C.P14`, `D.M1`–`D.M5` | Pasos del plan, nombrados como en su §9 | plan 00 |
| `D1`–`D7` | Decisiones del plan (§3). Aparte, `D1`–`D13` en 11 §9 son decisiones del diseño, y las «altas D1–D4» son altas de catálogo de la Fase 2 | plan 00, 11 |
| `DEV-r2-nn`, `DEC-w1-nn`, `DEC-w2-nn`, `DEC-w3-nn` | Desviaciones y decisiones registradas (o por registrar) | `WIZARD_PLAN_DEVIATIONS.md` |

## 3. Índice de hallazgos por prefijo

**Columnas.** «Dónde está» da el archivo, la sección y la línea. «Paso» usa los nombres del plan 00 §6 (paquete A: A1–A3 y A.B1–A.E2; B: B.S1–B.S13; C: C.P1–C.P14; D: D.M1–D.M5). «Estado» usa estas palabras:

- **hecho**: corregido y commiteado (con el hash).
- **parcial**: una parte está hecha (con el hash) y otra pendiente (con el paso).
- **en curso**: trabajo sin commit al redactar.
- **pendiente**: Fase 1, sin empezar.
- **Fase 2** y **Fase 3**: diferido por el plan (§7 y §8).
- **sin paso**: el plan 00 no le asigna ningún paso.

### 3.1 Prefijo E- (editorial, 39 hallazgos)

Origen: [01](01-auditoria-editorial-catalogo-planes.md). Los pasos C.P salen del plan 00 §6 y de 11 §2.9 (mapa de correcciones, l.255). En el cuerpo de 01, E-01 a E-21 son ALTA, E-22 a E-35 MEDIA y E-36 a E-39 BAJA.

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| E-01 | Nivel de las 7 plantillas complejas derivado del tipo y no de la receta | 01 §2.2, l.336 | C.P1, C.P2 | **hecho** · `c802b7e78` (nivel desde `claimedLevel`) |
| E-02 | Orden de tarjetas: el nivel nunca filtra y los 4 planes propios son BEGINNER fijo | 01 §2.2, l.346 | C.P1 (`levels`, `rank`); A.D1 y C.P3 (comparador del planner) | **parcial** · `c802b7e78` (modelo); comparador pendiente |
| E-03 | Una descripción única, con afirmaciones no verificables, para 29 protocolos | 01 §2.2, l.362 | C.P2 | **hecho** · `c802b7e78` |
| E-04 | `friendlyMethods`: 3 claves muertas y títulos incoherentes | 01 §2.2, l.375 | C.P1, C.P2 | **hecho** · `c802b7e78` (mapa eliminado) |
| E-05 | «N días» duplicado en el subtítulo de 4 protocolos | 01 §2.2, l.387 | C.P1 (subtítulo = duración y nivel) | **hecho** · `c802b7e78` |
| E-06 | Smolov Jr: título, descripción y fuente (es solo sentadilla) | 01 §2.2, l.393 | C.P2 (texto, kind ESPECIALIZACION); B.S6 (receta); E-30 (URL heredada) | **parcial** · `c802b7e78` (texto); receta pendiente (B.S6); URL en Fase 3 |
| E-07 | Coan y Philippi: complemento de un día ofrecido como plan completo | 01 §2.2, l.406 | C.P2 (kind COMPLEMENTO, rank 920); C.P12 (autor en la definición) | **parcial** · `c802b7e78` (ficha); definición pendiente (C.P12) |
| E-08 | Cube: rotación inexistente | 01 §2.2, l.417 | C.P2 (descripción honesta, D3); B.S6 (Cube); B.S7 (rotación) | **parcial** · `c802b7e78` (texto); recetas pendientes; rotación en Fase 2 |
| E-09 | RP-style, PPL KPKN y RTS-style prometen lo que la receta no hace | 01 §2.2, l.421 | C.P2 (texto honesto, D3); B.S6 (PPL y RP sin RIR 0); B.S7 (rampa RP, RTS por top sets) | **parcial** · `c802b7e78` (texto); B.S6 pendiente; resto en Fase 2 |
| E-10 | Frecuencia declarada distinta de los días reales (Smolov, Candito, Lilliebridge) | 01 §2.2, l.429 | B.S6 (Candito semana 1 a 4 días; Smolov semanas 9 a 13); C.P11 (rechazo FREQUENCY); A.F1–A.F3 (A-01). El plan no asigna paso a los weekdays de Lilliebridge | **pendiente** · A.F1 en Fase 2 |
| E-11 | 4 protocolos KPKN con «No afiliado a KPKN Fit» y URL `kpkn.fit` sin sentido | 01 §2.2, l.436 | C.P2 (`attributionLine`); C.P4 (`ProtocolAuditTest:84-85`); C.P12 | **parcial** · `c802b7e78` (`attributionLine`); definiciones y test pendientes |
| E-12 | Atribución y detalle del método sin superficie de lectura | 01 §2.2, l.447 | C.P8–C.P9 (`PlanInfoSheet`) | **pendiente** |
| E-13 | Biblioteca: nivel en inglés | 01 §2.2, l.455 | C.P1 (`CatalogLevel.label`); C.P5 (biblioteca) | **parcial** · `c802b7e78` (etiqueta); al redactar la biblioteca aún imprime `level.name` (C.P5 pendiente) |
| E-14 | `ProtocolDetailSheet`: códigos internos y jerga | 01 §2.2, l.459 | C.P8–C.P9 (notas llanas de la tabla) | **pendiente** (las notas ya están en la tabla, `c802b7e78`) |
| E-15 | `ProtocolDetailSheet`: calentamiento presentado como prescripción | 01 §2.2, l.470 | C.P8–C.P9 (`PlanInfoModel`) | **pendiente** |
| E-16 | Paso PLAN del wizard: solo título, subtítulo técnico y motivos | 01 §2.2, l.476 | C.P5 («Ver cómo funciona»); C.P8–C.P9 | **pendiente** |
| E-17 | Programa activado: «Procedencia no declarada» e identificadores crudos | 01 §2.2, l.482 | C.P7 (`findForProgram`) | **pendiente** (`findForProgram` ya existe, `c802b7e78`) |
| E-18 | «Planes», configurar: se descarta el plan elegido | 01 §2.2, l.489 | C.P6 (`preselectedPlanId`) | **pendiente** |
| E-19 | PHUL y PHAT: tres versiones conviviendo y orígenes mal etiquetados | 01 §2.2, l.493 | C.P2 (origen, rank 990, «versión anterior»); C.P5 (badge y filtros); B.S7 (ocultar heredados). 11 §0, punto 4: no se renombra el filtro «Versión KPKN» | **parcial** · `c802b7e78` (ficha); superficie pendiente; ocultar en Fase 2 |
| E-20 | Plantillas: jerga y lenguaje de contrato interno | 01 §2.2, l.502 | C.P2 (nombres y resúmenes); nombres de bloque coordinados con B | **parcial** · `c802b7e78` (textos); nombres de bloque pendientes |
| E-21 | Hipertrofia: nombres de plan y de bloque que no corresponden al contenido | 01 §2.2, l.511 | C.P2 (ídem) | **parcial** · `c802b7e78` (textos); nombres de bloque pendientes |
| E-22 | Plantillas simples: títulos amigables sobre estructuras vacías | 01 §2.3, l.522 | C.P2 (kind ESTRUCTURA); C.P6 (nombre del programa creado) | **parcial** · `c802b7e78` (ficha); nombre del programa pendiente |
| E-23 | Nativos históricos: descripciones contradictorias o ambiguas | 01 §2.3, l.529 | C.P2, C.P2b | **hecho** · `c802b7e78` y `d91a0ae2e` (5 ocultos; `strength-cardio` sin referencias) |
| E-24 | Perfiles propios: fugas de razonamiento interno en el texto | 01 §2.3, l.536 | C.P2; A.E1–A.E2 (prioridades y split) | **parcial** · `c802b7e78` (textos); A.E pendiente |
| E-25 | Jerga sin explicar (TM, 1RM, AMRAP, T1/T2/T3, MEV/MRV…) | 01 §2.3, l.542 | C.P10 (glosario); C.P8–C.P9 | **pendiente** (`PlanTerm` ya existe, `c802b7e78`) |
| E-26 | Copy de PHUL y PHAT autoradas | 01 §2.3, l.547 | C.P2; C.P8–C.P9 (fuente en letra pequeña); C.P12 (`AuthoredSourceRecord:71`) | **parcial** · `c802b7e78` (fichas); C.P12 pendiente |
| E-27 | Duración y ciclo: los ciclos que se repiten salen como «Ciclo finito» | 01 §2.3, l.555 | C.P1 (`REPEATING_CYCLE`) | **hecho** · `c802b7e78` |
| E-28 | `references`, `capabilities` y `focuses` inconsistentes | 01 §2.3, l.560 | C.P2b (referencias editoriales); C.P3 (`PlanGoalMatcher`); A.D1 (exención de foco) | **parcial** · `d91a0ae2e` (BBB en Fuerza y músculo; `strength-cardio` fuera de Músculo); C.P3 y A.D1 pendientes |
| E-29 | Nombre y modo del programa creado | 01 §2.3, l.567 | C.P6 (`programNameFor`, `programModeFor`) | **pendiente** |
| E-30 | Fuentes y URLs de terceros | 01 §2.3, l.577 | plan 00 §8 (fuente de verdad de URLs) | **Fase 3** |
| E-31 | Niveles de protocolos discutibles (GZCLP, nSuns, PHAT adaptado) | 01 §2.3, l.583 | plan 00 §8 (niveles contra fuentes) | **Fase 3** |
| E-32 | Autor frente a disclaimer | 01 §2.3, l.587 | C.P12 (`attributed()` con autores completos) | **parcial** · `c802b7e78` (`attributionLine` con autores completos); definiciones pendientes |
| E-33 | Textos del wizard (`SSD:284`, `WCC:143`, `TrainingMaxWizard`, «AUTO» y «PROPOSE») | 01 §2.3, l.591 | C.P12–C.P13 | **pendiente** |
| E-34 | Detalles de `ProtocolDetailSheet` (semana tipo, leyenda, `toInt()`) | 01 §2.3, l.598 | C.P8–C.P9 | **pendiente** |
| E-35 | Biblioteca: orden y campos que no se muestran | 01 §2.3, l.604 | C.P1 (`rank`); C.P5 | **pendiente** (`rank` ya existe, `c802b7e78`) |
| E-36 | Idioma y mayúsculas en etiquetas de día y de bloque | 01 §2.4, l.611 | plan 00 §8 | **Fase 3** |
| E-37 | Restos y campos huérfanos (emoji de BBB, `audienceLabel`…) | 01 §2.4, l.615 | C.P7 solo para el emoji (chip sin emoji, 11 §5) | **sin paso** (BAJA); emoji de BBB: C.P7 pendiente |
| E-38 | Tono y gramática de títulos | 01 §2.4, l.621 | ninguno propio; la tabla de C.P2 unifica los títulos | **parcial** · `c802b7e78` (títulos de la tabla) |
| E-39 | Nombres de bloque crípticos (F1–F4, B1–B3, «Cubo», «Switching»…) | 01 §2.4, l.626 | ninguno en el plan | **sin paso** (BAJA) |

### 3.2 Prefijo L- (lógica de recetas, 35 hallazgos)

Origen: [02](02-auditoria-logica-recetas-fijas.md) §2.5. L-01 a L-07 son ALTA; L-08 a L-29 y L-32 MEDIA (L-20 BAJA/MEDIA); L-30, L-31 y L-33 a L-35 BAJA. No hay ids con prefijo M- en ningún documento; M1–M5 son las altas del catálogo (sección 3.10). L-36 se cita en 02, l.169, pero no tiene hallazgo.

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| L-01 | Madcow: doble escalado de las rampas, con cargas por encima del 1RM | 02 §2.5, l.521 | B.S1 (`PercentResolver`); B.S6 (resto de Madcow) | **hecho** (cargas) · `4aa1bbb10`; resto de Madcow en B.S6 pendiente |
| L-02 | Texas 3d/4d y Madcow: la base de carga es el 1RM | 02 §2.5, l.535 | D7; B.S1; B.S6 | **parcial** · `4aa1bbb10` (Texas 3d y Madcow a 0,87); Texas 4d pendiente (B.S6) |
| L-03 | Progresiones declaradas pero inertes (5/3/1, Juggernaut, Smolov, Smolov Jr, Madcow, Texas, Lilliebridge) | 02 §2.5, l.543 | B.S3–B.S5 (motor de autor); B.S6 | **pendiente** |
| L-04 | `tmDeltaForAmrap`: unidades, signo y serie AMRAP (nSuns, GZCLP) | 02 §2.5, l.552 | B.S3–B.S5 (AMRAP); B.S6 (nSuns) | **pendiente** |
| L-05 | Porcentajes en %TM presentados y validados como %1RM | 02 §2.5, l.558 | B.S1–B.S2 (regla C7); B.S6 (datos de TSA, Calgary, KPKN SBD-4) | **parcial** · `4aa1bbb10` (`PercentBasis`, H11 y H11b); conversión en H5a, H8, H9, W3, W4 y BLOCK, y datos, pendientes |
| L-06 | KPKN SBD-4: el peso muerto nunca se entrena pesado | 02 §2.5, l.571 | B.S6 (KPKN SBD-4) | **pendiente** |
| L-07 | Lilliebridge: ninguna semana con peso muerto pesado | 02 §2.5, l.575 | B.S6 (Lilliebridge) | **pendiente** |
| L-08 | nSuns: la segunda jornada de banca repite la tabla de volumen | 02 §2.5, l.582 | B.S6 (nSuns) | **pendiente** |
| L-09 | GZCLP, J&T y Rippler: solo etapa 1, T2 sin `liftSlot`, `repsMax` perdido | 02 §2.5, l.586 | B.S6 (GZCLP: descripción, `CycleIncrement`, `rpeSets`); B.S7 (etapas y reset) | **pendiente** · etapas en Fase 2 |
| L-10 | UHF-9: semana 5 con esquema de volumen en el bloque «Intensidad»; sábado con déficit | 02 §2.5, l.595 | B.S6 (UHF-9) | **pendiente** |
| L-11 | Juggernaut: descargas sin `kind = DELOAD` | 02 §2.5, l.599 | B.S6 (Juggernaut) | **pendiente** |
| L-12 | 5/3/1 BBB y FSL: la descarga mantiene el suplementario | 02 §2.5, l.606 | B.S6 (5/3/1) | **pendiente** |
| L-13 | Cube: sin rotación pesado, explosivo y repeticiones | 02 §2.5, l.613 | B.S6 (Cube); B.S7 (rotación) | **pendiente** · rotación en Fase 2 |
| L-14 | Westside: variantes ME inalcanzables y sin resistencia acomodada | 02 §2.5, l.620 | B.S6 (Westside) | **pendiente** |
| L-15 | Calgary y Sheiko: offsets derivados, relleno KPKN y parches de técnica | 02 §2.5, l.626 | B.S6 (Calgary: `trainingMaxPercent` y `sq-tech`); B.S7 (Sheiko con tirón) | **pendiente** · Sheiko en Fase 2 |
| L-16 | Texas 3d: el chin AMRAP sin `liftSlot` escala los cuatro TM | 02 §2.5, l.632 | B.S6 (chin sin `amrap`); B.S3–B.S5 (`liftSlotFor`) | **pendiente** |
| L-17 | Texas 3d: OHP sin top set y DL de relleno | 02 §2.5, l.637 | ninguno explícito (Texas está en B.S6) | **sin paso** |
| L-18 | Texas 4d: referencias SQ_HIGH y SQ_LOW distintas, accesorios idénticos | 02 §2.5, l.640 | B.S6 (Texas 4d re-basado) | **pendiente** |
| L-19 | Smolov semana 9: 5 repeticiones al 90 % del 1RM | 02 §2.5, l.644 | B.S1 (`n(1,5,90)` pasa a `n(1,2,90)`; H11b); B.S1–B.S2 y B.S6 (rótulo y días) | **parcial** · `4aa1bbb10` (repeticiones y H11b); rótulo «Movilidad» y 3 frente a 4 días pendientes |
| L-20 | Candito: semana 1 con 5 días frente a un claim de 4; pico sin máximo | 02 §2.5, l.648 | B.S6 (Candito semana 1 a 4 días) | **pendiente** |
| L-21 | TSA: RPE solo en sentadilla; la descarga no recorta T2 | 02 §2.5, l.652 | B.S6 (TSA y Calgary) | **pendiente** |
| L-22 | PHUL heredado: potencia a 82 %TM, sin tirón vertical, duplica al autorado | 02 §2.5, l.656 | C.P2 y D3 (relegado con «versión anterior»); B.S7 (ocultar) | **parcial** · `c802b7e78` (ficha, rank 990); ocultar en Fase 2 |
| L-23 | PHAT heredado: SPEED mal ubicado y weekdays distintos de la fuente | 02 §2.5, l.662 | C.P2 y D3; B.S7 (ocultar) | **parcial** · `c802b7e78` (ficha, rank 991); ocultar en Fase 2 |
| L-24 | kpkn-ppl-6: RIR 0 en compuestos, descarga sin recorte y sin core | 02 §2.5, l.668 | B.S6 (PPL: `secondRir`, descargas) | **pendiente** |
| L-25 | kpkn-rp-style: MEV a MRV no implementado y «Torso B» es de empuje | 02 §2.5, l.673 | B.S6 (RP: sin RIR 0, descargas); B.S7 (rampa y «Torso B» real) | **pendiente** · rampa en Fase 2 |
| L-26 | kpkn-rts-style: `RepTargetDrivenTm` sin AMRAP y RPE solo en sentadilla | 02 §2.5, l.679 | C.P2 (descripción honesta, D3); B.S7 (RTS por top sets) | **parcial** · `c802b7e78` (texto); receta en Fase 2 |
| L-27 | power-16-4 y power-20-5: técnicas redundantes y parches | 02 §2.5, l.684 | D.M2 (alta); B.S1–B.S2 (regla C9); B.S7 | **parcial** · alta `d68e91872` (M2); recetas en Fase 2 |
| L-28 | power-12-3: principiante con accesorios avanzados | 02 §2.5, l.693 | B.S7 | **Fase 2** |
| L-29 | powerbuild-16-4 y body-16-4/20-5: bloques casi iguales y sin descargas reales | 02 §2.5, l.697 | B.S7 | **Fase 2** |
| L-30 | Autorados: `isCompetitionLift` sobrecargado; PHAT sin progresión | 02 §2.5, l.702 | ninguno (PHAT sin progresión es decisión de producto, DEV-r2-04) | **sin paso** |
| L-31 | Coan: SPEED antes del pesado | 02 §2.5, l.708 | ninguno | **sin paso** |
| L-32 | Validadores: W1, W2 y S6 saltados, %TM crudo, H11 permisivo, `supplementalOf`, exenciones por `contains`, `daysPerWeek` tautológico, `goalRank` | 02 §2.5, l.710 | B.S1–B.S2 (contrato de receta) | **parcial** · `4aa1bbb10` (H11 y H11b sobre kg resueltos, exenciones con glob anclado); resto pendiente |
| L-33 | Series sueltas de 1 (`SHRUG`, `PUSHDOWN`) en unas 15 recetas | 02 §2.5, l.720 | B.S6 (series sueltas) | **pendiente** |
| L-34 | `PersonalizedPlanCatalog`: `friendlyMethods` con claves muertas y plantillas siempre ADVANCED | 02 §2.5, l.724 | C.P1, C.P2 | **hecho** · `c802b7e78` |
| L-35 | Smolov Jr con los 4 días llamados «Sesión»; `CatalogIds` con constantes sin uso | 02 §2.5, l.728 | B.S6 (etiquetas Jr S1 a S4) | **pendiente** |

### 3.3 Prefijo C- (catálogo de ejercicios que usan las recetas, 12 hallazgos)

Origen: [02](02-auditoria-logica-recetas-fijas.md) §3. Las altas M1–M5 de la Fase 1 retiran los parches C-01 a C-04 y C-10 (11 §7.1).

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| C-01 | `CLOSE_GRIP` sobre `BP` (5/3/1, nSuns) | 02 §3, l.740 | D.M1 (`close_grip_bench_press__barbell`) | **parcial** · alta `d68e91872`; cableado y retirada de `technique` pendientes |
| C-02 | `CLOSE_GRIP` sobre `LAT` (PHAT `lat-close`) | 02 §3, l.745 | D.M4 (`close_grip_lat_pulldown__cable`) | **parcial** · alta `d68e91872`; cableado pendiente (incluye `CABLE_HIGH_LOW_CONFIGURATIONS`) |
| C-03 | `PAUSE_2S` sobre sentadilla (Calgary, power-20-5) | 02 §3, l.749 | D.M2 (`paused_back_squat__barbell`) | **parcial** · alta `d68e91872`; cableado pendiente |
| C-04 | `TO_KNEES` sobre `DL` (Sheiko) | 02 §3, l.753 | D.M3 (`deadlift_to_knees__barbell`) | **parcial** · alta `d68e91872`; cableado pendiente |
| C-05 | `DEFICIT` redundante o parche | 02 §3, l.755 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-06 | `BOX` y `PIN` redundantes | 02 §3, l.760 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-07 | `CHAINS_BANDS` sobre `BP_FLOOR` | 02 §3, l.762 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-08 | `SPEED` sobre configuraciones normales (informativo) | 02 §3, l.764 | ninguno | **sin paso** |
| C-09 | Clasificación de `UPRIGHT_ROW` como tirón vertical | 02 §3, l.770 | ninguno | **sin paso** |
| C-10 | «Curl inclinado» del PHUL resuelto con curl sentado en banco plano | 02 §3, l.772 | D.M5 (`incline_biceps_curl__dumbbells`) | **parcial** · alta `d68e91872`; binding pendiente |
| C-11 | `ROW_DB` marcado unilateral con lateralidad BILATERAL | 02 §3, l.776 | ninguno | **sin paso** |
| C-12 | Ids de slot confusos (`pull`, `lat`, `bp`, `tate`) | 02 §3, l.778 | ninguno | **sin paso** |

### 3.4 Prefijo P- (informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| P-01 | «Fuerza» y «Fuerza y músculo» sin material: el plan los cita juntos con P-02 y no dice cuál es cuál | plan 00 §3 D5, l.37; contexto en §1, l.17 | A.C1 (`SwitchGoal`); A.C4 y C.P11 (presentador de rechazos) | **pendiente** (D5 se aplica por defecto) |
| P-02 | Ídem. Además 11 §4 (l.344) usa P-02 para el texto de TIME_BUDGET de `SimpleCyclePersonalizer` (:1216-1219), que imprime el tiempo del usuario | plan 00 §3 D5, l.37; 11 §4 | A.C2 (`requiredMinutes` exacto y mensaje nuevo); A.C1, A.C4 | **pendiente** |

### 3.5 Prefijo A- (informe no archivado; no confundir con las fichas A-01 a A-04 de 02 §2.2)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| A-01 | Protocolos de otra frecuencia, visibles y plegados con «Cambiar mis días a N» | plan 00 §7, l.202 | A.F1–A.F3 | **Fase 2** |
| A-03 | Recetas fijas con `userWeekdays` en vez de rotar | plan 00 §7, l.202 | A.F1–A.F3 | **Fase 2** |

### 3.6 Prefijo B- (informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| B-01 | Una selección obsoleta oculta la lista de planes | plan 00 §6 A.D2, l.106; §1, l.17 | A.D2 | **pendiente** |
| B-03 | Gates de Fuerza y de Fuerza y músculo sin evidencia de material («gimnasio completo» sin confirmar se reporta como ausente) | plan 00 §6 A.B2, l.96; §1, l.17 | A.B2 | **en curso** |
| B-05 | «Reintentar» reproduce rechazos cacheados | plan 00 §6 A.D3, l.107 | A.D3 | **pendiente** |
| B-06 | `ensureCatalogLoaded` marca el catálogo como cargado sin estar listo | plan 00 §6 A.D3, l.107 | A.D3 | **pendiente** |
| B-07 | Pase a peso corporal tras rechazos que no son de material | plan 00 §6 A.D4, l.108 | A.D4 | **pendiente** |
| B-12 | «Fuerza y músculo» sin resistencia: rechazo sin botón | 11 §4, l.329 | A.C4 y C.P11 (tabla de rechazos) | **pendiente** (11 lo da por cerrado con el botón «Cambiar objetivo») |

Nota: el B-03 de 11 §0 (punto 8) y §4 es otro: el aviso de volumen alto y las notas del plan en la revisión, del cierre de la consolidación. Allí figura como hecho (`artifacts/consolidation-20261001/TODO.md`, l.32).

### 3.7 Prefijo N- (informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| N-01 | Fitter dirigido por músculo (separar la fase de volumen de la de tiempo) | plan 00 §7, l.197; §11, l.239 | B.S9–B.S10 | **Fase 2** |
| N-03 | Ver plan 00 §7 (B.S9–B.S10) | plan 00 §7, l.197 | B.S9–B.S10 | **Fase 2** |
| N-04 | Ver plan 00 §7 (B.S9–B.S10) | plan 00 §7, l.197 | B.S9–B.S10 | **Fase 2** |
| N-05 | `supportRequirementsFor`: falta `rack` en las sentadillas y huecos de banco, barra y prefijo `bench_press__` | plan 00 §6 A.B4, l.98 | A.B4 | **pendiente** |
| N-14 | Ver plan 00 §7 (B.S9–B.S10) | plan 00 §7, l.197 | B.S9–B.S10 | **Fase 2** |
| N-16 | Ver plan 00 §7 (B.S9–B.S10) | plan 00 §7, l.197 | B.S9–B.S10 | **Fase 2** |

### 3.8 Prefijo R- (runtime de progresión; informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| R-02 | AMRAP: ver plan 00 §6 (B.S3–B.S5, viñeta AMRAP; el plan no separa cuál de R-02, R-11 y R-17 describe cada punto) | plan 00 §6, l.140 | B.S3–B.S5 | **pendiente** |
| R-03 | «VER PROPUESTA» acepta la propuesta en vez de solo navegar (error visible, 1 h) | plan 00 §6, l.142 | B.S3–B.S5 | **pendiente** |
| R-04 | Guardar TM: conservar variantes y TMs ajustados y re-materializar semanas pendientes | plan 00 §6, l.141 | B.S3–B.S5 | **pendiente** |
| R-11 | AMRAP: ver plan 00 §6 (viñeta AMRAP) | plan 00 §6, l.140 | B.S3–B.S5 | **pendiente** |
| R-13 | Aviso «no aplicable» al aceptar una propuesta | plan 00 §7, l.200 | B.S13 | **Fase 2** |
| R-15 | Selector de modo en Detalle con confirmación para AUTO | plan 00 §7, l.200 | B.S13 | **Fase 2** |
| R-16 | AUGE: umbral de fatiga normalizado | plan 00 §7, l.199 | B.S12 | **Fase 2** |
| R-17 | AMRAP: ver plan 00 §6 (viñeta AMRAP) | plan 00 §6, l.140 | B.S3–B.S5 | **pendiente** |
| R-19 | Test de 1RM: también actualiza `powerliftingProfile` (l.141); tarjeta «Programa terminado, repetir con TM actualizado» (l.200) | plan 00 §6, l.141; §7, l.200 | B.S3–B.S5; B.S13 | **pendiente** · tarjeta en Fase 2 |
| R-23 | `rematerializeWeek` no resuelve `startDay` ni `trainingDays` como `materialize` (rota los días) | plan 00 §6, l.141 | B.S3–B.S5 | **pendiente** |
| R-24 | RIR derivado de Epley para porcentajes sin TM | plan 00 §7, l.201 | B.S7 | **Fase 2** |

### 3.9 Prefijo Q- (catálogo v2; informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| Q-02 | Llaves para las 6 máquinas de las tablas propias | plan 00 §8, l.207 | plan 00 §8 | **Fase 3** |
| Q-07 | Etiquetas `donkey_machine`, `leg_press_machine` y `hands_elevated` en `ExerciseCatalogV2Labels.kt` | plan 00 §6, l.191; 11 §7.2, l.464 | paquete D (PR de catálogo) | **pendiente** |
| Q-08 | Notas obsoletas de `CATALOG_GAP_NOTES` en `PlanAdaptationResolver.kt:274-283` | plan 00 §6, l.191; 11 §7.2, l.465 | paquete D (PR de catálogo) | **pendiente** |

### 3.10 Prefijo G- (brechas del catálogo)

Origen: [11](11-diseno-editorial-y-catalogo.md) §7.1 (l.417). Las altas M1–M4 ya existen en el catálogo; su cableado en Kotlin sigue pendiente (sección 4). M5 se corresponde con C-10, no con un G-.

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| G-01 | Alta M1: press de banca con agarre cerrado (`close_grip_bench_press__barbell`); misma brecha que C-01 | 11 §7.1, l.423 | D.M1 | **hecho** (alta) · `d68e91872` |
| G-02 | Alta M2: sentadilla trasera con pausa (`paused_back_squat__barbell`) | 11 §7.1, l.424 | D.M2 | **hecho** (alta) · `d68e91872` |
| G-03 | Alta M3: peso muerto hasta la rodilla (`deadlift_to_knees__barbell`) | 11 §7.1, l.425 | D.M3 | **hecho** (alta) · `d68e91872` |
| G-17 | Alta M4: jalón al pecho con agarre cerrado (`close_grip_lat_pulldown__cable`) | 11 §7.1, l.426 | D.M4 | **hecho** (alta) · `d68e91872` |
| G-15 | Alta D1: elevación lateral con banda (`standing_lateral_raise__band`) | 11 §7.1, l.428 | alta D1 del catálogo | **Fase 2** |
| G-12 | Alta D2: flexiones pike (`pike_push_up__bodyweight`) | 11 §7.1, l.429 | alta D2 del catálogo | **Fase 2** (11 §7.1: D2, D3 y D4 entran solo con aprobación del dueño) |
| G-13 | Alta D3: peso muerto rumano unilateral corporal (`romanian_deadlift__unilateral__bodyweight`) | 11 §7.1, l.430 | alta D3 del catálogo | **Fase 2** (ídem) |
| G-14 | Alta D4: sentadilla copa con kettlebell (`goblet_squat_kettlebell__kettlebell`) | 11 §7.1, l.431 | alta D4 del catálogo | **Fase 2** (ídem) |
| G-07 | Cargada de potencia (power clean): exige un patrón nuevo en la ontología | 11 §0, l.10 y §7.1, l.435; plan 00 §8 | diferido (D13 del diseño) | **Fase 3** |
| G-08 | Sentadilla con salto: exige un patrón nuevo en la ontología | 11 §0, l.10 y §7.1, l.435; plan 00 §8 | diferido (D13 del diseño) | **Fase 3** |

### 3.11 Recuento de ids indexados

| Prefijo | Ids | Observación |
|---|---|---|
| E- | 39 | E-01 a E-39 |
| L- | 35 | L-01 a L-35; L-36 solo como referencia colgante |
| C- | 12 | C-01 a C-12 |
| P- | 2 | P-01, P-02 |
| A- | 2 | A-01, A-03 |
| B- | 6 | B-01, B-03, B-05, B-06, B-07 y B-12 (más el B-03 homónimo del cierre, no contado) |
| N- | 6 | N-01, N-03, N-04, N-05, N-14, N-16 |
| R- | 11 | R-02, R-03, R-04, R-11, R-13, R-15, R-16, R-17, R-19, R-23, R-24 |
| Q- | 3 | Q-02, Q-07, Q-08 |
| G- | 10 | G-01, G-02, G-03, G-07, G-08, G-12, G-13, G-14, G-15, G-17 |
| **Total** | **126** | |

## 4. Matriz paso del plan a commit

Cada fila lleva el commit que entregó el paso, o su estado si todavía no lo hay. Las cifras de verificación salen del mensaje del commit citado. Al cerrar un paso hay que actualizar esta tabla y la columna de estado de la sección 3.

### Fase 0 (plan 00 §5)

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| F0.0 | Entorno de edición: Write y Edit bloqueados por hooks de un plugin, según el plan | sin commit | Es del entorno de la sesión y no se puede comprobar desde el repositorio (plan 00, l.72). |
| F0.1 | Commit del árbol consolidado | **hecho** · `5c281a02c` | Con `466ef4bd4` (herramientas y guías), `6914fb6f8` (iOS) y `bcda7d1a5` (backend). `5c281a02c` toca 465 archivos y su mensaje declara 4 270 tests JVM en verde. Sin push. |
| F0.2 | Candado de Gradle y coordinación | proceso, sin commit propio | El script [run-gradle-locked.ps1](../../../.opencode/scripts/run-gradle-locked.ps1) existe en el árbol (commit `97c464ad0`, de la sesión de nutrición); se usa con `-Owner "curaduria-programas"`. |
| F0.3 | Archivar la evidencia | **hecho** en lo archivable · `d41ba0524` (plan 00, 01, 02 y 11) y este README, sin commit al redactar | Solo hay tres informes archivables; los demás no existen como archivo (sección 2). |
| F0.4 | Baseline de la suite completa Base | sin commit | El baseline que cita el plan es la corrida X-full-2 del cierre de la consolidación: `testBaseDebugUnitTest`, 4 270 tests, 0 fallos, 0 errores, 2 omitidos, 615 clases (`artifacts/consolidation-20261001/test-evidence/X-full-2/summary.json`, sin versionar). La carpeta `artifacts/programs-curation/baseline/` que pedía el plan no existe. |

### Fase 1, paquete A: cobertura y adaptación (plan 00 §6)

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| A1 | `CoverageFixtures` compartidos (E0 a E18) y T006 con `NativePlanFailureMapper.typedFailure` | **hecho** · `5849c32f0` | Q1, Q2, Q2 calibrada, positivos y Q3 idénticos: Q2 con 2 304 filas, 1 224 viables, APPARATUS_ABSENT 360, TIME_BUDGET 504 y PROFILE_MISMATCH 216; positivos 58/58. |
| A2 | `PlanCoverageContractTest` con ratchet de violaciones | **hecho** · `5849c32f0` | Baseline `full` del 2026-10-03: 25 650 filas, 13 590 Ready y 3 537 violaciones (COMPOSITION 195, DISHONEST_ABSENT 270, TIME_BUDGET_INEXACT 1 578, NO_REPAIR 30, MATERIAL_UNUSED 1 464). La suite queda en verde y cada paso baja los techos hasta 0. |
| A3 | Filas `T020_*` de paridad con el ViewModel | pendiente | |
| A.B1, A.B2, A.B3, A.B7 | Plomería `missingRequirements`; gates con evidencia (B-03); `hasExplicitMachinePresence` y `pullAvailable` resuelto; `requirementEvidence` de ANY a ALL | **en curso** | Sin commit al redactar: hay cambios en el árbol de trabajo y `NativeMaterialEvidenceTest` aparece sin rastrear. |
| A.B4, A.B5, A.B6 | Soportes de `supportRequirementsFor` (N-05); tablas de candidatos; evidencia unificada | pendiente | |
| A.C1 a A.C4 | `PlanRepair`; `requiredMinutes` exacto; `applyRepair`; `PlanRejectionPresenter` | pendiente | |
| A.D1 a A.D4 | Ranking editorial; lista y selección caída (B-01); caché y catálogo (B-05, B-06); pase corporal (B-07) | pendiente | |
| A.E1, A.E2 | Prioridades y split en los 4 planes propios (D6) | pendiente | |
| A, documentación | DEC-w2-01 a DEC-w2-06, DEV-r2-06 y `REVISION` a `native-cycle-2` | pendiente | Solo DEC-w2-07 está escrita (la registró C.P2b). |

### Fase 1, paquete B: recetas y progresión

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| B.S1 | `PercentResolver` y `PercentBasis`; H11 sobre kg resueltos; H11b (Epley) no exentable; exenciones con glob anclado; Texas 3d y Madcow a TM 0,87 (D7); Madcow a `PERCENT_TM`; Smolov semana 9 a 2 repeticiones | **hecho** · `4aa1bbb10` | 26 clases por filtro, 0 fallos. Inventario permanente `every_published_recipe_is_free_of_h11_and_h11b`: 0 hallazgos en 40 recetas (antes del cambio de datos, 81). Con 1RM 200: Texas viernes 174 kg y Madcow lunes de la semana 4 174 kg. Pendientes anotados en el commit para C.P8, B.S2 y B.S12. |
| B.S2 | `RecipeContractPolicy` (reglas C1 a C10): inventario SOFT y luego HARD | **en curso** | Sin commit al redactar: `RecipeContractPolicy.kt`, `RecipeContractPolicyTest.kt` y `RecipeContractInventoryTest.kt` aparecen sin rastrear y `SessionCompositionPolicy.kt` modificado. |
| B.S3 a B.S5 | Motor de progresión de autor, AMRAP, guardar TM, test de 1RM, R-23 y R-03 | pendiente | |
| B.S6 | Correcciones ALTA receta a receta | **parcial** · `4aa1bbb10` | Hecho: Texas 3d, Madcow (TM y `PERCENT_TM`) y Smolov semana 9. Pendiente: resto de Madcow (`CycleIncrement`, `supplementalOf`, `ProtocolBlock`), Texas 4d, KPKN SBD-4, Lilliebridge, Smolov y Smolov Jr, 5/3/1, Juggernaut, nSuns, PPL, RP y body-12-3, series sueltas, Candito, UHF-9, TSA y Calgary, GZCLP, Cube, RTS y Westside. |
| B, documentación | DEC-w3-01, DEC-w3-02, DEC-w3-03 y DEC-w3-07 | pendiente | |

### Fase 1, paquete C: editorial y UX

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| C.P1, C.P2 | `PlanEditorial`, `PlanLabels`, `PlanEditorialTable` (55 fichas), `findForProgram` y `REPEATING_CYCLE` | **hecho** · `c802b7e78` | `PlanCatalogEditorialContractTest` (21) y `PlanLabelsTest` (13) nuevos; por filtro, 14 clases y 148 tests, 0 fallos. Fuera de este commit: planner y pantallas (C.P3, C.P5) y nombres de bloque de las plantillas. |
| C.P2b | Ocultar los 5 nativos históricos (D2), referencias editoriales y re-baseline | **hecho** · `d91a0ae2e` | Q1 de T006 pasa de 497 664 a 422 496 candidatos publicados; Q2 idéntico (2 304 filas, 1 224 viables); T-019 grupo F con el testigo `native:muscle-foundation-v2` (piso de 28 min). Registra DEC-w2-07. En este paso solo se corrió el grupo F de T-019; el resto de la matriz no se corrió. |
| C.P3 | Planner y `PlanGoalMatcher` | pendiente | |
| C.P4 | `PlanCatalogEditorialContractTest` y tests asociados | **parcial** · `c802b7e78`, `d91a0ae2e` | Contrato y tests de catálogo hechos. Faltan `ProtocolAuditTest:84-85`, `ProgramTemplateClaimsTest` y el test de orden del planner. |
| C.P5 a C.P14 | Biblioteca y wizard; nombre, modo y preselección; revisión y detalle; `PlanInfoSheet`; glosario; rechazos; atribución y copy; QA en emulador | pendiente | |

### Fase 1, paquete D: catálogo de ejercicios

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| D.M1 a D.M5 | Cinco altas SPECIALTY por el pipeline v2: `close_grip_bench_press__barbell`, `paused_back_squat__barbell`, `deadlift_to_knees__barbell`, `close_grip_lat_pulldown__cable` e `incline_biceps_curl__dumbbells` | **hecho** · `d68e91872` | Catálogo 96/206/527 con `catalogRevision` intacta (`v2-approved-2026-09-29-a`); SHA canónico `b2a652bb4f654f32e2925593858b3110e6c06a95637e7a2a4bad19d20cc6734a`; 63 definiciones CURATED. Verificación: 187 pruebas de `scripts/tests`, 10 de backend y 72 de Gradle por filtro, 0 fallos. |
| D, cableado Kotlin | Soportes en `supportRequirementsFor`, remaps del reconciliador, retirar `technique` de las recetas, bindings y `CABLE_HIGH_LOW_CONFIGURATIONS` | pendiente | `CatalogIds` ya tiene las cinco constantes, sin consumidores. |
| D, Q-07 y Q-08 | Etiquetas de opciones y notas obsoletas de `CATALOG_GAP_NOTES` | pendiente | |
| D, `CatalogIdsExistInCatalogTest` | Regla «nunca inventar ids» como test | pendiente | |

### Fases 2 y 3, y cierre

| Paso | Qué es | Estado |
|---|---|---|
| Fase 2 (plan 00 §7) | B.S9–B.S10, B.S11, B.S12, B.S13, B.S7, A.F1–A.F3 y las altas D1–D4 del catálogo con el resto de C/D | pendiente |
| Fase 3 (plan 00 §8) | URLs de terceros (E-30), niveles (E-31), etiquetas de día en español (E-36), paridad iOS, Q-02, G-07 y G-08 | pendiente |
| Documentación final (plan 00 §10.6) | Este README; DEV y DEC en `WIZARD_PLAN_DEVIATIONS.md`; actualizar `docs/WIZARD_BIENVENIDA_EXECUTION_STATE.md` | **parcial**: README hecho (sin commit), DEC-w2-07 hecha, el resto pendiente |

## 5. Decisiones registradas

### 5.1 Decisiones del plan (plan 00 §3, l.29)

D1 a D4 las decidió el dueño el 2026-10-03; D5 a D7 se aplican con la recomendación indicada salvo que el dueño diga otra cosa.

| # | Decisión | Estado |
|---|---|---|
| D1 | Primera entrega «publicable» = Fase 1 (cobertura, editorial, recetas y progresión, 5 altas de catálogo y ocultar históricos); Fases 2 y 3 después | Decidida |
| D2 | Ocultar ya (`listed = false`) `full-body`, `gym-muscle`, `one-day`, `return-training` y `home-training`; mantener `machine-muscle` y `bodyweight` relegados; `strength-cardio` solo legado | Decidida; aplicada en `d91a0ae2e` (DEC-w2-07) |
| D3 | Recetas cuya descripción miente: Fase 1 con descripción honesta, PPL y RP sin RIR 0, PHUL y PHAT heredados relegados con «Versión anterior»; Fase 2 con las correcciones de fondo | Decidida; textos en `c802b7e78`; PPL y RP sin RIR 0 pendiente (B.S6) |
| D4 | Onda de RIR intra-ciclo en los 4 propios (principiante `3,3,3,2,2`; intermedio y avanzado `3,2,2,1,1`; descarga RIR 4), en Fase 2 | Decidida; DEV-r2-06 por registrar |
| D5 | Fuerza y Fuerza y músculo sin material: redirección con botón de un toque, sin variantes de fuerza relativa | Por defecto; pendiente (A.C1, A.C4, C.P11) |
| D6 | Prioridades y split en los 4 propios: prioridades ordenan empates, split por tabla con rechazo SPLIT reparable | Por defecto; pendiente (A.E1, A.E2) |
| D7 | Texas y Madcow con `trainingMaxPercent = 0,87` y nota visible en el wizard de marcas | Por defecto; aplicada a Texas 3d y Madcow en `4aa1bbb10`; Texas 4d y la nota del wizard pendientes |

### 5.2 Decisiones previas que el plan respeta (plan 00 §2, l.23)

Glúteos hasta 17,5 como banda blanda solo en propios (DEV-r2-02); puente una vez por semana (DEV-r2-01); el cardio del Atleta nunca se recorta (DEC-w1-01, DEV-r2-03); PHUL con doble progresión y PHAT sin ella (DEV-r2-04); al terminar un bloque el programa continúa con un solo aviso (DEV-r2-05); los planes de autor no se filtran por nivel (DEC-w1-04); un slot SPEED sin material es NotViable (DEC-w1-05); las adaptaciones van al final (DEC-w1-06).

### 5.3 Registradas en `docs/WIZARD_PLAN_DEVIATIONS.md`

Las líneas son las de [WIZARD_PLAN_DEVIATIONS.md](../../WIZARD_PLAN_DEVIATIONS.md).

| Id | Qué decide | Línea |
|---|---|---|
| DEV-r2-01 | Variantes corporales sin tirón: el MRV de glúteos (16) prevalece sobre el calendario literal. Aceptada el 2026-10-02 | 24 |
| DEV-r2-02 | Tolerancia blanda de glúteos hasta 17,5 series por semana, como último recurso y solo en planes propios. Aprobada e implementada | 136 |
| DEV-r2-03 | Progresión de cardio (R13) diferida; R14 absorbida en R13 | 162 |
| DEV-r2-04 | Progresión de carga de los originales PHUL y PHAT y de sus adaptaciones (R16): parcial, PHUL sí y PHAT no por ahora | 176 |
| DEV-r2-05 | Fin de bloque: el programa continúa solo con un aviso, sin pantalla de oferta (H-CICLO) | 188 |
| DEC-w1-01 | El fitter no recorta cardio ni descansos: a 45 min el cardio por defecto de 15 min da TIME_BUDGET | 204 |
| DEC-w1-02 | Músculo corporal de 3 días: el fitter retira los accesorios «de la tabla» y el mínimo real es 21 min | 222 |
| DEC-w1-03 | Matriz grupo A: piso independiente por fila y metadatos de composición en el arnés | 232 |
| DEC-w1-04 | Los planes de autor no se filtran por nivel | 241 |
| DEC-w1-05 | Un slot SPEED sin material es NotViable, no se sustituye | 247 |
| DEC-w1-06 | Las adaptaciones van tras los propios y los originales | 253 |
| DEC-w1-07 | No entregado: progresión de cardio de r2 §12.4; hoy es la desviación DEV-r2-03 | 259 |
| DEC-w1-08 | No entregado: cláusula de adaptaciones de originales de r2 §12.4; hoy es la desviación DEV-r2-04 | 269 |
| DEC-w2-07 | Los cinco nativos históricos dejan de listarse (D2, 2026-10-03) | 281 |

Previstas por el plan y todavía no escritas: DEC-w2-01 a DEC-w2-06 y DEV-r2-06 (paquete A) y DEC-w3-01, DEC-w3-02, DEC-w3-03 y DEC-w3-07 (paquete B). El plan usa DEV-r2-06 para dos decisiones distintas (la onda de RIR en §3 D4 y §7, y las máquinas por categoría sin llave en el paquete A de §6); hay que renumerar una antes de registrarlas.

### 5.4 Decisiones del diseño editorial (11 §9, l.558)

Son recomendaciones del diseño; no coinciden con las D1–D7 del plan.

| # | Recomendación del diseño | Resultado |
|---|---|---|
| D1 | Relegar los históricos ahora y ocultarlos después | Superada por la D2 del plan (ocultar ya; DEC-w2-07) |
| D2 | `PlanKind` con cuatro valores | Aplicada en `c802b7e78` |
| D3 | Etiquetar los 24 métodos de tercero como «Versión KPKN basada en…» | Reflejada en la tabla editorial (`c802b7e78`) |
| D4 | El programa creado se llama como el `displayName` | Pendiente (C.P6) |
| D5 | PHUL y PHAT heredados quedan en el wizard con rank 990 y badge «Versión anterior» | Fichas en `c802b7e78`; badge pendiente; ocultar en Fase 2 |
| D6 | BBB con referencias de powerlifting y powerbuilding; FSL solo powerlifting | Aplicada en `d91a0ae2e` |
| D7 | Protocolos avanzados para principiantes: mostrarlos al final con un motivo, sin ocultarlos | Sin paso nombrado en el plan |
| D8 | Plantillas y protocolos de la biblioteca van al wizard; el camino directo queda solo en el editor | Recogida en C.P6; pendiente |
| D9 | Renombrar bloques en las recetas, coordinado con el paquete de recetas | Pendiente (C.P2 y B.S6) |
| D10 | Glosario en dos niveles | Pendiente (C.P10) |
| D11 | Fichas CURATED para M1–M5 y LEGACY para D1–D4 | M1–M5 hechas en `d68e91872` |
| D12 | `paused_back_squat` con una sola configuración | Aplicada en `d68e91872` |
| D13 | G-07 y G-08 diferidos hasta decidir la ontología | Fase 3 (plan 00 §8) |

### 5.5 Pendientes de decisión del dueño

Las cuatro primeras las dejó abiertas el commit `d68e91872` (reportadas, no decididas):

- **Pendiente de decisión del dueño:** anatomía de `deadlift_to_knees`, con cuádriceps y rodilla como PRIMARY (suma 0,5 serie de cuádriceps frente al peso muerto convencional cuando Sheiko migre a esa configuración).
- **Pendiente de decisión del dueño:** anatomía de `close_grip_lat_pulldown`, con trapecio como SECONDARY (igual que `pull_up`).
- **Pendiente de decisión del dueño:** anatomía de `close_grip_bench_press`, con tríceps como SECONDARY.
- **Pendiente de decisión del dueño:** el solape de `incline_biceps_curl` con el alias «curl inclinado» del curl Bayesian.
- Las altas D2, D3 y D4 del catálogo (G-12, G-13, G-14) tocan `NativeCandidateTable` y el oráculo de glúteos, y entran solo con aprobación del dueño (11 §7.1).
- Smolov semanas 9 a 13: conservar con una exención justificada o alinear a 4 días (plan 00, B.S1–S2 regla C4 y B.S6).
- nSuns: mover el AMRAP a la serie 1+@95 de los lower, a verificar contra la hoja de nSuns (plan 00, B.S6).

## 6. Cómo verificar

Todo Gradle va con el candado y solo para el flavor Base (plan 00 §10 y §5 F0.2). Desde la raíz del repositorio, en PowerShell:

```powershell
# Una clase por filtro
.opencode/scripts/run-gradle-locked.ps1 -Tasks "testBaseDebugUnitTest --tests '*.PlanCatalogEditorialContractTest'" -Owner "curaduria-programas"

# Suite completa Base (al cerrar cada paquete; baseline: 4 270 tests, 0 fallos)
.opencode/scripts/run-gradle-locked.ps1 -Tasks "testBaseDebugUnitTest" -Owner "curaduria-programas"
```

Catálogo de ejercicios (plan 00 §10.3):

```powershell
python scripts/catalog_v2_pin_sha.py --check
python -m unittest discover -s scripts/tests
```

Y el test de backend `backend/tests/test_exercises_catalog_v2.py`, que el commit `d68e91872` corrió con pytest (10 pruebas en verde).

**Tests que ya existen y cubren lo entregado:** [PlanCoverageContractTest](../../../android-native/app/src/test/java/com/example/kpkn/domain/training/PlanCoverageContractTest.kt), `PlanCatalogEditorialContractTest`, `PlanLabelsTest`, `PercentResolverTest`, `ExemptionScopeMigrationTest` y los actualizados `PersonalizedPlanCatalogTest`, `NativeProfileSpecCatalogTest`, `WizardPluralCopyTest`, `SetupTrainingPlannerTest` y `AprendeCatalogAuditTest`. Del plan 00 §10.1 todavía no existen `PlanRepairAdvisorTest`, `PlanRejectionPresenterTest`, `NativeCandidateTableSlotsTest`, `SetupTrainingPlannerRankingTest`, `OwnPlanPrioritiesAndSplitTest`, `ProgressionConsumerCoverageTest`, `AuthoredProgressionEngineTest`, `PlanInfoModelTest`, `ProgramNamingTest` ni `CatalogIdsExistInCatalogTest`. `NativeMaterialEvidenceTest` y `RecipeContractPolicyTest` existen sin commit, como parte de los pasos en curso.

**Contrato de cobertura.** `PlanCoverageContractTest` elige el tier con `KPKN_COVERAGE_TIER` (`smoke`, que es el valor por defecto, `ci` o `full`). En `ci` se elige el shard 0 a 3 con `KPKN_COVERAGE_SHARD`, y `KPKN_COVERAGE_WORKERS` fija los hilos. La suite siempre queda en verde: solo falla si una corrida supera el techo de su tier (techos del baseline de `5849c32f0`: smoke 226, ci 887/885/886/879 y full 3 537). Los informes quedan en `android-native/app/build/reports/coverage-contract/<tier>[-shard].txt` (por ejemplo `smoke.txt`, `full.txt`, `ci-0.txt`); esa carpeta es salida de compilación y no se versiona.

**Oráculos de aceptación (plan 00 §10.4).** Madcow con 1RM 200 nunca prescribe más de 174 kg (comprobado en `4aa1bbb10`); 5/3/1 cierra el ciclo con TM +2,5 y +5; Smolov Jr semana 2 sube 5 kg; T006 Q2 sin COMPOSITION y positivos 58/58; toda fila de la rejilla de cobertura termina Ready o con una reparación verificada; ningún `displayName` ni `summary` con jerga, ids o inglés; «VER PROPUESTA» no acepta la propuesta.
