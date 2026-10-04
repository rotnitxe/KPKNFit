# Curaduría integral de programas — evidencia e índice

> **Estado al redactar** (cierre del 2026-10-03, HEAD `b18597085`): hechos F0.1, F0.3, A1–A2, A.B1–A.B5 y A.B7, A.D1–A.D4, A.E1, B.S1, B.S3, B.S4, C.P1–C.P3, C.P7–C.P10, C.P12–C.P13 y D.M1–M5; con el dominio listo y el cableado pendiente, A.C1, A.C2 y A.C4 (ningún código del producto usa todavía el asesor de reparaciones ni el presentador de rechazos); parciales B.S2 (inventario SOFT), C.P4 y el cableado Kotlin del paquete D; en curso C.P5 (cambios sin commit en la biblioteca y en el wizard); todo lo demás está pendiente (A3, A.B6, A.C3, A.E2, B.S5 a B.S13, C.P6, C.P11 y C.P14, y las Fases 2 y 3). La fuente es la matriz de la sección 4; este encabezado solo la resume.

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
| E-02 | Orden de tarjetas: el nivel nunca filtra y los 4 planes propios son BEGINNER fijo | 01 §2.2, l.346 | C.P1 (`levels`, `rank`); A.D1 y C.P3 (comparador del planner) | **parcial** · `c802b7e78` (modelo) y A.D1/C.P3 `2e3123245` (comparador editorial); falta decir «más exigente que tu nivel» en los métodos de otro nivel (A.C4, C.P11) y la razón «Su nivel coincide con tu experiencia» sigue leyendo `entry.level` (BEGINNER en los propios) |
| E-03 | Una descripción única, con afirmaciones no verificables, para 29 protocolos | 01 §2.2, l.362 | C.P2 | **hecho** · `c802b7e78` |
| E-04 | `friendlyMethods`: 3 claves muertas y títulos incoherentes | 01 §2.2, l.375 | C.P1, C.P2 | **hecho** · `c802b7e78` (mapa eliminado) |
| E-05 | «N días» duplicado en el subtítulo de 4 protocolos | 01 §2.2, l.387 | C.P1 (subtítulo = duración y nivel) | **hecho** · `c802b7e78` |
| E-06 | Smolov Jr: título, descripción y fuente (es solo sentadilla) | 01 §2.2, l.393 | C.P2 (texto, kind ESPECIALIZACION); B.S6 (receta); E-30 (URL heredada) | **parcial** · `c802b7e78` (texto); receta pendiente (B.S6); URL en Fase 3 |
| E-07 | Coan y Philippi: complemento de un día ofrecido como plan completo | 01 §2.2, l.406 | C.P2 (kind COMPLEMENTO, rank 920); C.P12 (autor en la definición) | **parcial** · `c802b7e78` (ficha); definición pendiente (C.P12) |
| E-08 | Cube: rotación inexistente | 01 §2.2, l.417 | C.P2 (descripción honesta, D3); B.S6 (Cube); B.S7 (rotación) | **parcial** · `c802b7e78` (texto); recetas pendientes; rotación en Fase 2 |
| E-09 | RP-style, PPL KPKN y RTS-style prometen lo que la receta no hace | 01 §2.2, l.421 | C.P2 (texto honesto, D3); B.S6 (PPL y RP sin RIR 0); B.S7 (rampa RP, RTS por top sets) | **parcial** · `c802b7e78` (texto); B.S6 pendiente; resto en Fase 2 |
| E-10 | Frecuencia declarada distinta de los días reales (Smolov, Candito, Lilliebridge) | 01 §2.2, l.429 | B.S6 (Candito semana 1 a 4 días; Smolov semanas 9 a 13); C.P11 (rechazo FREQUENCY); A.F1–A.F3 (A-01). El plan no asigna paso a los weekdays de Lilliebridge | **pendiente** · A.F1 en Fase 2 |
| E-11 | 4 protocolos KPKN con «No afiliado a KPKN Fit» y URL `kpkn.fit` sin sentido | 01 §2.2, l.436 | C.P2 (`attributionLine`); C.P4 (`ProtocolAuditTest:84-85`); C.P12 | **hecho** · C.P2 `c802b7e78` (`attributionLine`) y C.P12 `328752b13` (los 5 protocolos KPKN sin URL ni «No afiliado a KPKN Fit»; `ProtocolAuditTest` exige URL y disclaimer solo a los verificados) |
| E-12 | Atribución y detalle del método sin superficie de lectura | 01 §2.2, l.447 | C.P8–C.P9 (`PlanInfoSheet`) | **parcial** · C.P8–C.P9 `e5e428e54` (la hoja «Cómo funciona», con fuente, atribución y los plegables de las autoradas) y C.P7 `aa3ccb40f` (se abre desde el detalle del programa y la revisión del wizard); falta abrirla desde la biblioteca, que sigue con su `AlertDialog`, y desde las tarjetas del wizard (C.P5) |
| E-13 | Biblioteca: nivel en inglés | 01 §2.2, l.455 | C.P1 (`CatalogLevel.label`); C.P5 (biblioteca) | **parcial** · `c802b7e78` (etiqueta); al redactar la biblioteca aún imprime `level.name` (C.P5 pendiente) |
| E-14 | `ProtocolDetailSheet`: códigos internos y jerga | 01 §2.2, l.459 | C.P8–C.P9 (notas llanas de la tabla) | **hecho** · C.P8–C.P9 `e5e428e54` (`ProtocolDetailSheet` delega en la hoja nueva: sin requisitos crudos, sin «Notas KPKN» con códigos, con el resumen y las «Notas del método» de la tabla `c802b7e78`) |
| E-15 | `ProtocolDetailSheet`: calentamiento presentado como prescripción | 01 §2.2, l.470 | C.P8–C.P9 (`PlanInfoModel`) | **hecho** · C.P8–C.P9 `e5e428e54` (la semana tipo se arma sin calentamientos y con las series agrupadas) |
| E-16 | Paso PLAN del wizard: solo título, subtítulo técnico y motivos | 01 §2.2, l.476 | C.P5 («Ver cómo funciona»); C.P8–C.P9 | **pendiente** (C.P5) · la hoja ya existe (`e5e428e54`) y la revisión del wizard ya la abre (`aa3ccb40f`), pero las tarjetas del paso PLAN no |
| E-17 | Programa activado: «Procedencia no declarada» e identificadores crudos | 01 §2.2, l.482 | C.P7 (`findForProgram`) | **hecho** · C.P7 `aa3ccb40f` (procedencia y nombre desde `findForProgram`; fin de «Procedencia no declarada» y de «Método anterior · {id}») |
| E-18 | «Planes», configurar: se descarta el plan elegido | 01 §2.2, l.489 | C.P6 (`preselectedPlanId`) | **pendiente** |
| E-19 | PHUL y PHAT: tres versiones conviviendo y orígenes mal etiquetados | 01 §2.2, l.493 | C.P2 (origen, rank 990, «versión anterior»); C.P5 (badge y filtros); B.S7 (ocultar heredados). 11 §0, punto 4: no se renombra el filtro «Versión KPKN» | **parcial** · `c802b7e78` (ficha); superficie pendiente; ocultar en Fase 2 |
| E-20 | Plantillas: jerga y lenguaje de contrato interno | 01 §2.2, l.502 | C.P2 (nombres y resúmenes); nombres de bloque coordinados con B | **parcial** · `c802b7e78` (textos); nombres de bloque pendientes |
| E-21 | Hipertrofia: nombres de plan y de bloque que no corresponden al contenido | 01 §2.2, l.511 | C.P2 (ídem) | **parcial** · `c802b7e78` (textos); nombres de bloque pendientes |
| E-22 | Plantillas simples: títulos amigables sobre estructuras vacías | 01 §2.3, l.522 | C.P2 (kind ESTRUCTURA); C.P6 (nombre del programa creado) | **parcial** · `c802b7e78` (ficha); nombre del programa pendiente |
| E-23 | Nativos históricos: descripciones contradictorias o ambiguas | 01 §2.3, l.529 | C.P2, C.P2b | **hecho** · `c802b7e78` y `d91a0ae2e` (5 ocultos; `strength-cardio` sin referencias) |
| E-24 | Perfiles propios: fugas de razonamiento interno en el texto | 01 §2.3, l.536 | C.P2; A.E1–A.E2 (prioridades y split) | **parcial** · `c802b7e78` (textos) y A.E1 `4f9845f81` (la bolsa de prioridades se aplica y se persiste); A.E2 (split) pendiente, y el foco sigue siendo solo una etiqueta en los propios (el plan no le asigna paso) |
| E-25 | Jerga sin explicar (TM, 1RM, AMRAP, T1/T2/T3, MEV/MRV…) | 01 §2.3, l.542 | C.P10 (glosario); C.P8–C.P9 | **parcial** · C.P10 `75c15deb0` (`PlanGlossary` y cuatro conceptos nuevos en Conceptos clave) y C.P8–C.P9 `e5e428e54` (la hoja muestra el glosario del plan); falta el enlace «Ver en Conceptos clave» (la hoja lo admite, nadie le pasa el callback) y las superficies que aún no usan la hoja (C.P5) |
| E-26 | Copy de PHUL y PHAT autoradas | 01 §2.3, l.547 | C.P2; C.P8–C.P9 (fuente en letra pequeña); C.P12 (`AuthoredSourceRecord:71`) | **parcial** · `c802b7e78` (fichas), C.P12 `328752b13` (`AuthoredSourceRecord` sin «§12.4») y C.P8–C.P9 `e5e428e54` (fuente y plegables «Lo que publica el autor» y «Configuración inicial KPKN» en la hoja); la línea de autor repetida de la biblioteca espera a C.P5 |
| E-27 | Duración y ciclo: los ciclos que se repiten salen como «Ciclo finito» | 01 §2.3, l.555 | C.P1 (`REPEATING_CYCLE`) | **hecho** · `c802b7e78` |
| E-28 | `references`, `capabilities` y `focuses` inconsistentes | 01 §2.3, l.560 | C.P2b (referencias editoriales); C.P3 (`PlanGoalMatcher`); A.D1 (exención de foco) | **parcial** · `d91a0ae2e` (BBB en Fuerza y músculo; `strength-cardio` fuera de Músculo) y C.P3/A.D1 `2e3123245` (`PlanGoalMatcher` compartido con la biblioteca, prefiltro de capacidades de Atleta y exención de foco para las plantillas con receta); quedan el PHUL heredado sin «Músculo» y las capacidades vacías de PHUL y PHAT autoradas, que el matcher nuevo no usa (solo Atleta completo lee las capacidades) |
| E-29 | Nombre y modo del programa creado | 01 §2.3, l.567 | C.P6 (`programNameFor`, `programModeFor`) | **pendiente** |
| E-30 | Fuentes y URLs de terceros | 01 §2.3, l.577 | plan 00 §8 (fuente de verdad de URLs) | **Fase 3** |
| E-31 | Niveles de protocolos discutibles (GZCLP, nSuns, PHAT adaptado) | 01 §2.3, l.583 | plan 00 §8 (niveles contra fuentes) | **Fase 3** |
| E-32 | Autor frente a disclaimer | 01 §2.3, l.587 | C.P12 (`attributed()` con autores completos) | **hecho** · C.P2 `c802b7e78` (`attributionLine` con autores completos) y C.P12 `328752b13` («Mark Rippetoe y Glenn Pendlay», «Andy Baker y Mark Rippetoe», «Madcow (a partir de Bill Starr)», «Ed Coan y Mark Philippi»; el disclaimer repite el autor completo) |
| E-33 | Textos del wizard (`SSD:284`, `WCC:143`, `TrainingMaxWizard`, «AUTO» y «PROPOSE») | 01 §2.3, l.591 | C.P12–C.P13 | **parcial** · C.P12–C.P13 `328752b13` (los cuatro textos que el plan asigna al paso: «Recomiéndame un plan», WizChat sin «tal cual es, sin recortar su receta», «Máximo de entrenamiento (TM)» y «Mantener el ajuste automático» y «Volver a proponer cambios» en lugar de «AUTO» y «PROPOSE»); sin paso del plan: `WCG:36` (objetivos heredados) y que cerrar la hoja de marcas cree el programa de todos modos |
| E-34 | Detalles de `ProtocolDetailSheet` (semana tipo, leyenda, `toInt()`) | 01 §2.3, l.598 | C.P8–C.P9 | **hecho** · C.P8–C.P9 `e5e428e54` (sin la semana tipo duplicada ni la leyenda «[KPKN]»; cifras con coma decimal sin truncar, 62,5 y no 62) |
| E-35 | Biblioteca: orden y campos que no se muestran | 01 §2.3, l.604 | C.P1 (`rank`); C.P5 | **parcial** · A.D1/C.P3 `2e3123245` (cabecera nueva de la biblioteca: «Elige un plan para ver cómo funciona…»); el orden por `rank` y los campos de las tarjetas esperan a C.P5 (la biblioteca sigue en el orden natural del catálogo) |
| E-36 | Idioma y mayúsculas en etiquetas de día y de bloque | 01 §2.4, l.611 | plan 00 §8 | **Fase 3** |
| E-37 | Restos y campos huérfanos (emoji de BBB, `audienceLabel`…) | 01 §2.4, l.615 | C.P7 solo para el emoji (chip sin emoji, 11 §5) | **parcial** · C.P7 `aa3ccb40f` (el chip del detalle muestra el nombre del catálogo sin emoji, así que ya no sale la «5» suelta de BBB; el dato `emoji` sigue mal formado); `audienceLabel`, ids de plantilla y el parámetro `adapted` sin uso: **sin paso** (BAJA) |
| E-38 | Tono y gramática de títulos | 01 §2.4, l.621 | ninguno propio; la tabla de C.P2 unifica los títulos | **parcial** · `c802b7e78` (títulos de la tabla) |
| E-39 | Nombres de bloque crípticos (F1–F4, B1–B3, «Cubo», «Switching»…) | 01 §2.4, l.626 | ninguno en el plan | **sin paso** (BAJA) |

### 3.2 Prefijo L- (lógica de recetas, 35 hallazgos)

Origen: [02](02-auditoria-logica-recetas-fijas.md) §2.5. L-01 a L-07 son ALTA; L-08 a L-29 y L-32 MEDIA (L-20 BAJA/MEDIA); L-30, L-31 y L-33 a L-35 BAJA. No hay ids con prefijo M- en ningún documento; M1–M5 son las altas del catálogo (sección 3.10). L-36 se cita en 02, l.169, pero no tiene hallazgo.

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| L-01 | Madcow: doble escalado de las rampas, con cargas por encima del 1RM | 02 §2.5, l.521 | B.S1 (`PercentResolver`); B.S6 (resto de Madcow) | **hecho** (cargas) · `4aa1bbb10`; resto de Madcow en B.S6 pendiente |
| L-02 | Texas 3d/4d y Madcow: la base de carga es el 1RM | 02 §2.5, l.535 | D7; B.S1; B.S6 | **parcial** · `4aa1bbb10` (Texas 3d y Madcow a 0,87); Texas 4d pendiente (B.S6) |
| L-03 | Progresiones declaradas pero inertes (5/3/1, Juggernaut, Smolov, Smolov Jr, Madcow, Texas, Lilliebridge) | 02 §2.5, l.543 | B.S3–B.S5 (motor de autor); B.S6 | **parcial** · B.S3 `5312decc2` (`CycleIncrement` por ciclo y por bloque y `WeeklyKg`: 5/3/1 sube el TM al cerrar el ciclo y Smolov y Smolov Jr suman sus kilos por semana) y B.S4 `b18597085` (`TopSetPr` y `RepMaxAutoregulated` como propuestas de TM); faltan los datos en B.S6: Madcow (`WeeklyPercent`, la única regla sin consumidor), Juggernaut por ola (`BLOCK`) y Lilliebridge a `None` |
| L-04 | `tmDeltaForAmrap`: unidades, signo y serie AMRAP (nSuns, GZCLP) | 02 §2.5, l.552 | B.S3–B.S5 (AMRAP); B.S6 (nSuns) | **parcial** · B.S4 `b18597085` (la propuesta lleva `kgDelta`; el AMRAP corto se evalúa primero y nunca sube; la tabla en kilos solo vale desde el 85 % del TM); falta mover el AMRAP de nSuns a la serie 1+ al 95 % de los lower (B.S6) |
| L-05 | Porcentajes en %TM presentados y validados como %1RM | 02 §2.5, l.558 | B.S1–B.S2 (regla C7); B.S6 (datos de TSA, Calgary, KPKN SBD-4) | **parcial** · `4aa1bbb10` (`PercentBasis`, H11 y H11b) y B.S2 `2f5e0bd69` (C7 en el inventario SOFT: 12 hallazgos al entrar); conversión en H5a, H8, H9, W3, W4 y BLOCK, y datos, pendientes |
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
| L-16 | Texas 3d: el chin AMRAP sin `liftSlot` escala los cuatro TM | 02 §2.5, l.632 | B.S6 (chin sin `amrap`); B.S3–B.S5 (`liftSlotFor`) | **parcial** · B.S4 `b18597085` (un AMRAP cuyo slot no declara lift ya no propone nada: el chin de Texas deja de escalar los cuatro TM); quitar el `amrap` del chin está pendiente (B.S6) |
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
| L-32 | Validadores: W1, W2 y S6 saltados, %TM crudo, H11 permisivo, `supplementalOf`, exenciones por `contains`, `daysPerWeek` tautológico, `goalRank` | 02 §2.5, l.710 | B.S1–B.S2 (contrato de receta) | **parcial** · `4aa1bbb10` (H11 y H11b sobre kg resueltos, exenciones con glob anclado) y B.S2 `2f5e0bd69` (`goalRank` con goals en inglés y en español, fidelidad contra los días reales, `supplementalOf` incoherente detectado por C8 en el inventario SOFT); pendientes W1, W2 y S6 con `liftSlots` vacío, el %TM crudo en BLOCK, H8, H5 y W3, y los datos (B.S6) |
| L-33 | Series sueltas de 1 (`SHRUG`, `PUSHDOWN`) en unas 15 recetas | 02 §2.5, l.720 | B.S6 (series sueltas) | **pendiente** |
| L-34 | `PersonalizedPlanCatalog`: `friendlyMethods` con claves muertas y plantillas siempre ADVANCED | 02 §2.5, l.724 | C.P1, C.P2 | **hecho** · `c802b7e78` |
| L-35 | Smolov Jr con los 4 días llamados «Sesión»; `CatalogIds` con constantes sin uso | 02 §2.5, l.728 | B.S6 (etiquetas Jr S1 a S4) | **pendiente** |

### 3.3 Prefijo C- (catálogo de ejercicios que usan las recetas, 12 hallazgos)

Origen: [02](02-auditoria-logica-recetas-fijas.md) §3. Las altas M1–M5 de la Fase 1 retiran los parches C-01 a C-04 y C-10 (11 §7.1).

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| C-01 | `CLOSE_GRIP` sobre `BP` (5/3/1, nSuns) | 02 §3, l.740 | D.M1 (`close_grip_bench_press__barbell`) | **parcial** · alta `d68e91872` y soportes (banco y rack) en `supportRequirementsFor` `d604ef579`; cableado y retirada de `technique` pendientes |
| C-02 | `CLOSE_GRIP` sobre `LAT` (PHAT `lat-close`) | 02 §3, l.745 | D.M4 (`close_grip_lat_pulldown__cable`) | **parcial** · alta `d68e91872` y `CABLE_HIGH_LOW_CONFIGURATIONS` `d604ef579`; cableado en `lat-close` pendiente |
| C-03 | `PAUSE_2S` sobre sentadilla (Calgary, power-20-5) | 02 §3, l.749 | D.M2 (`paused_back_squat__barbell`) | **parcial** · alta `d68e91872` y soporte (rack) en `supportRequirementsFor` `d604ef579`; cableado pendiente |
| C-04 | `TO_KNEES` sobre `DL` (Sheiko) | 02 §3, l.753 | D.M3 (`deadlift_to_knees__barbell`) | **parcial** · alta `d68e91872`; cableado pendiente |
| C-05 | `DEFICIT` redundante o parche | 02 §3, l.755 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-06 | `BOX` y `PIN` redundantes | 02 §3, l.760 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-07 | `CHAINS_BANDS` sobre `BP_FLOOR` | 02 §3, l.762 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-08 | `SPEED` sobre configuraciones normales (informativo) | 02 §3, l.764 | ninguno | **sin paso** |
| C-09 | Clasificación de `UPRIGHT_ROW` como tirón vertical | 02 §3, l.770 | ninguno | **sin paso** |
| C-10 | «Curl inclinado» del PHUL resuelto con curl sentado en banco plano | 02 §3, l.772 | D.M5 (`incline_biceps_curl__dumbbells`) | **parcial** · alta `d68e91872` y soportes (banco y banco regulable) en `supportRequirementsFor` `d604ef579`; binding pendiente |
| C-11 | `ROW_DB` marcado unilateral con lateralidad BILATERAL | 02 §3, l.776 | ninguno | **sin paso** |
| C-12 | Ids de slot confusos (`pull`, `lat`, `bp`, `tate`) | 02 §3, l.778 | ninguno | **sin paso** |

### 3.4 Prefijo P- (informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| P-01 | «Fuerza» y «Fuerza y músculo» sin material: el plan los cita juntos con P-02 y no dice cuál es cuál | plan 00 §3 D5, l.37; contexto en §1, l.17 | A.C1 (`SwitchGoal`); A.C4 y C.P11 (presentador de rechazos) | **parcial** · el motivo ya es honesto (`APPARATUS_UNKNOWN` en lugar de «declaraste ausente», A.B2 `4d63e9dd1`) y la redirección está lista en el dominio (`SwitchGoal`, `ConfirmApparatus` y el presentador, A.C1 y A.C4 `e275d0bbc`, DEC-w2-02); cableado pendiente (A.C3, C.P11); D5 se aplica por defecto |
| P-02 | Ídem. Además 11 §4 (l.344) usa P-02 para el texto de TIME_BUDGET de `SimpleCyclePersonalizer` (:1216-1219), que imprime el tiempo del usuario | plan 00 §3 D5, l.37; 11 §4 | A.C2 (`requiredMinutes` exacto y mensaje nuevo); A.C1, A.C4 | **parcial** · A.C2 `e275d0bbc` (el generador informa el mínimo exacto con el mensaje «Con las series mínimas este plan necesita N min por sesión y elegiste M.»; ya no imprime el tiempo del usuario como mínimo; DEC-w2-03); la frase y el botón «Ajustar a N min» de la UI esperan al cableado (A.C3, C.P11) |

### 3.5 Prefijo A- (informe no archivado; no confundir con las fichas A-01 a A-04 de 02 §2.2)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| A-01 | Protocolos de otra frecuencia, visibles y plegados con «Cambiar mis días a N» | plan 00 §7, l.202 | A.F1–A.F3 | **Fase 2** |
| A-03 | Recetas fijas con `userWeekdays` en vez de rotar | plan 00 §7, l.202 | A.F1–A.F3 | **Fase 2** |

### 3.6 Prefijo B- (informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| B-01 | Una selección obsoleta oculta la lista de planes | plan 00 §6 A.D2, l.106; §1, l.17 | A.D2 | **hecho** · A.D2 `0b50bda5e` (`candidateListGate`: el error del preview ya no esconde la lista; selección caída con aviso) |
| B-03 | Gates de Fuerza y de Fuerza y músculo sin evidencia de material («gimnasio completo» sin confirmar se reporta como ausente) | plan 00 §6 A.B2, l.96; §1, l.17 | A.B2 | **hecho** · A.B2 `4d63e9dd1` (evidencia por requisito; `DISHONEST_ABSENT` 270 → 0) |
| B-05 | «Reintentar» reproduce rechazos cacheados | plan 00 §6 A.D3, l.107 | A.D3 | **hecho** · A.D3 `0b50bda5e` (la caché no guarda `INTERNAL_MATERIALIZATION` ni `CATALOG_NOT_READY`) |
| B-06 | `ensureCatalogLoaded` marca el catálogo como cargado sin estar listo | plan 00 §6 A.D3, l.107 | A.D3 | **hecho** · A.D3 `0b50bda5e` (solo se marca cargado con estado Ready; si no, `CATALOG_NOT_READY` visible con «Reintentar») |
| B-07 | Pase a peso corporal tras rechazos que no son de material | plan 00 §6 A.D4, l.108 | A.D4 | **hecho** · A.D4 `0b50bda5e` (`bodyweightPassAllowed`; los rechazos publicados son los del pase pedido; DEC-w2-05) |
| B-12 | «Fuerza y músculo» sin resistencia: rechazo sin botón | 11 §4, l.329 | A.C4 y C.P11 (tabla de rechazos) | **parcial** · dominio listo (`SwitchGoal` a Músculo y el presentador con «Cambiar objetivo», `e275d0bbc`); sin cablear (A.C3, C.P11); 11 lo da por cerrado con el botón «Cambiar objetivo» |

Nota: el B-03 de 11 §0 (punto 8) y §4 es otro: el aviso de volumen alto y las notas del plan en la revisión, del cierre de la consolidación. Allí figura como hecho (`artifacts/consolidation-20261001/TODO.md`, l.32).

### 3.7 Prefijo N- (informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| N-01 | Fitter dirigido por músculo (separar la fase de volumen de la de tiempo) | plan 00 §7, l.197; §11, l.239 | B.S9–B.S10 | **Fase 2** |
| N-03 | Ver plan 00 §7 (B.S9–B.S10) | plan 00 §7, l.197 | B.S9–B.S10 | **Fase 2** |
| N-04 | Ver plan 00 §7 (B.S9–B.S10) | plan 00 §7, l.197 | B.S9–B.S10 | **Fase 2** |
| N-05 | `supportRequirementsFor`: falta `rack` en las sentadillas y huecos de banco, barra y prefijo `bench_press__` | plan 00 §6 A.B4, l.98 | A.B4 | **hecho** · A.B4 `d604ef579` (rack en las sentadillas con barra; banco y rack en el press con pausa, agarre cerrado, Spoto y cadenas; `low_bar_support` en el rack chin, `pull_up_bar` en las dominadas escapulares, la suspensión y el jalón con banda, y banco en el hip thrust con banda) |
| N-14 | Ver plan 00 §7 (B.S9–B.S10) | plan 00 §7, l.197 | B.S9–B.S10 | **Fase 2** |
| N-16 | Ver plan 00 §7 (B.S9–B.S10) | plan 00 §7, l.197 | B.S9–B.S10 | **Fase 2** |

### 3.8 Prefijo R- (runtime de progresión; informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| R-02 | AMRAP: ver plan 00 §6 (B.S3–B.S5, viñeta AMRAP; el plan no separa cuál de R-02, R-11 y R-17 describe cada punto) | plan 00 §6, l.140 | B.S3–B.S5 | **hecho** (motor) · B.S4 `b18597085` (AMRAP en kilos con `kgDelta`, el log más reciente de cada ciclo, el lift sale del slot de la receta y `PlanObserver` lee la tabla real); los datos de las recetas siguen en B.S6 |
| R-03 | «VER PROPUESTA» acepta la propuesta en vez de solo navegar (error visible, 1 h) | plan 00 §6, l.142 | B.S3–B.S5 | **pendiente** |
| R-04 | Guardar TM: conservar variantes y TMs ajustados y re-materializar semanas pendientes | plan 00 §6, l.141 | B.S3–B.S5 | **pendiente** |
| R-11 | AMRAP: ver plan 00 §6 (viñeta AMRAP) | plan 00 §6, l.140 | B.S3–B.S5 | **hecho** (motor) · B.S4 `b18597085` (mismo cambio que R-02: el plan no separa los tres); los datos de las recetas siguen en B.S6 |
| R-13 | Aviso «no aplicable» al aceptar una propuesta | plan 00 §7, l.200 | B.S13 | **Fase 2** |
| R-15 | Selector de modo en Detalle con confirmación para AUTO | plan 00 §7, l.200 | B.S13 | **Fase 2** |
| R-16 | AUGE: umbral de fatiga normalizado | plan 00 §7, l.199 | B.S12 | **Fase 2** |
| R-17 | AMRAP: ver plan 00 §6 (viñeta AMRAP) | plan 00 §6, l.140 | B.S3–B.S5 | **hecho** (motor) · B.S4 `b18597085` (mismo cambio que R-02: el plan no separa los tres); los datos de las recetas siguen en B.S6 |
| R-19 | Test de 1RM: también actualiza `powerliftingProfile` (l.141); tarjeta «Programa terminado, repetir con TM actualizado» (l.200) | plan 00 §6, l.141; §7, l.200 | B.S3–B.S5; B.S13 | **pendiente** · tarjeta en Fase 2 |
| R-23 | `rematerializeWeek` no resuelve `startDay` ni `trainingDays` como `materialize` (rota los días) | plan 00 §6, l.141 | B.S3–B.S5 | **hecho** · B.S3 `5312decc2` (`rematerializeWeek` y `materialize` comparten `resolveWeekSchedule`; prueba con `startDay` 3) |
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
| F0.3 | Archivar la evidencia | **hecho** en lo archivable · `d41ba0524` (plan 00, 01, 02 y 11) y `6152603dc` (este README) | Solo hay tres informes archivables; los demás no existen como archivo (sección 2). |
| F0.4 | Baseline de la suite completa Base | sin commit | El baseline que cita el plan es la corrida X-full-2 del cierre de la consolidación: `testBaseDebugUnitTest`, 4 270 tests, 0 fallos, 0 errores, 2 omitidos, 615 clases (`artifacts/consolidation-20261001/test-evidence/X-full-2/summary.json`, sin versionar). La carpeta `artifacts/programs-curation/baseline/` que pedía el plan no existe. |

### Fase 1, paquete A: cobertura y adaptación (plan 00 §6)

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| A1 | `CoverageFixtures` compartidos (E0 a E18) y T006 con `NativePlanFailureMapper.typedFailure` | **hecho** · `5849c32f0` | Q1, Q2, Q2 calibrada, positivos y Q3 idénticos: Q2 con 2 304 filas, 1 224 viables, APPARATUS_ABSENT 360, TIME_BUDGET 504 y PROFILE_MISMATCH 216; positivos 58/58. |
| A2 | `PlanCoverageContractTest` con ratchet de violaciones | **hecho** · `5849c32f0` | Baseline `full` del 2026-10-03: 25 650 filas, 13 590 Ready y 3 537 violaciones (COMPOSITION 195, DISHONEST_ABSENT 270, TIME_BUDGET_INEXACT 1 578, NO_REPAIR 30, MATERIAL_UNUSED 1 464). La suite queda en verde y cada paso baja los techos hasta 0. |
| A3 | Filas `T020_*` de paridad con el ViewModel | pendiente | |
| A.B1, A.B2, A.B3, A.B7 | Plomería `missingRequirements`; gates con evidencia (B-03); `hasExplicitMachinePresence` y `pullAvailable` resuelto; `requirementEvidence` de ANY a ALL | **hecho** · `4d63e9dd1` | Contrato `full` 3 537 → 3 162 (DISHONEST_ABSENT 270 → 0, NO_REPAIR 30 → 0, NOT_HONEST 195 → 120). `NativeMaterialEvidenceTest` (22) nuevo; matriz T-019 completa verde (43 filas). Registra DEC-w2-01 y DEV-r2-06. |
| A.B4, A.B5 | Soportes de `supportRequirementsFor` (N-05); tablas de candidatos con Smith, kettlebell y banda | **hecho** · `d604ef579` | Contrato `full` 3 162 → 1 704 (MATERIAL_UNUSED 1 464 → 0). `NativeCandidateTableSlotsTest` (10) y `CatalogIdsExistInCatalogTest` (3) nuevos. Deja pendiente `bench_press__kettlebell` (decisión del dueño, sección 7). Registra DEC-w2-04 parte 1. |
| A.B6 | Evidencia unificada (`evidenceOfKind` compartido con el ViewModel; tokens fuera del panel sin botón) | pendiente | Incluye el sustituto de `rack_chin` en `PlanAdaptationResolver` (DEC-w2-04 parte 1). |
| A.C1, A.C2, A.C4 (parte pura) | `PlanRepair` y `PlanRepairAdvisor`; `requiredMinutes` exacto; `PlanRejectionPresenter` | **parcial** · dominio listo en `e275d0bbc`; cableado pendiente | Contrato `full` 1 704 → 120 (TIME_BUDGET_INEXACT 1 584 → 0; techos smoke 8, ci 30 y full 120). `PlanRepairAdvisorTest` (25) y `PlanRejectionPresenterTest` (24) nuevos. Ningún código del producto usa todavía el asesor ni el presentador. Documentan el paso DEC-w2-02 y DEC-w2-03. |
| A.C3 y la parte de UI de A.C4 | `applyRepair` en el ViewModel; sustituir `CandidateIncompatibility` y el aviso de selección caída por el presentador | pendiente | Va con C.P11. |
| A.D1 (con C.P3) | Ranking editorial del planner y prefiltro de capacidades de Atleta | **hecho** · `2e3123245` | Q1 de T006 422 496 → 194 400 candidatos publicados; Q2 idéntico. Registra DEC-w2-06. |
| A.D2, A.D3, A.D4 | Lista y selección caída (B-01); caché y catálogo (B-05, B-06); pase corporal solo por material (B-07) | **hecho** · `0b50bda5e` | `SetupWizardCandidateGateTest` (20) nuevo; `PlanCandidateSessionCacheTest` 5 → 9; matriz 17/17 idéntica. Registra DEC-w2-05. |
| A.E1 | Prioridades en los 4 planes propios (D6) | **hecho** · `4f9845f81` | `OwnPlanPrioritiesAndSplitTest` (11) nuevo; `SetupWizardFullJourneyTest` vuelve a verde sin tocar su oráculo. Registra DEC-w2-08. |
| A.E2 | Split en los 4 planes propios (D6) | pendiente | Completa D6 y DEC-w2-04 parte 2. |
| A, documentación | DEC-w2-01 a DEC-w2-08, DEV-r2-06 y `REVISION` a `native-cycle-2` | **parcial** | Escritas: DEC-w2-01 y DEV-r2-06 (`4d63e9dd1`), DEC-w2-06 (`2e3123245`), DEC-w2-04 parte 1 (`d604ef579`), DEC-w2-08 (`4f9845f81`), DEC-w2-07 (`d91a0ae2e`) y, en el commit de documentación de la ola, DEC-w2-02, DEC-w2-03 y DEC-w2-05. Faltan DEC-w2-04 parte 2 (A.E2) y subir `PersonalizedPlanCatalog.REVISION` a `native-cycle-2` al cerrar el paquete. |

### Fase 1, paquete B: recetas y progresión

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| B.S1 | `PercentResolver` y `PercentBasis`; H11 sobre kg resueltos; H11b (Epley) no exentable; exenciones con glob anclado; Texas 3d y Madcow a TM 0,87 (D7); Madcow a `PERCENT_TM`; Smolov semana 9 a 2 repeticiones | **hecho** · `4aa1bbb10` | 26 clases por filtro, 0 fallos. Inventario permanente `every_published_recipe_is_free_of_h11_and_h11b`: 0 hallazgos en 40 recetas (antes del cambio de datos, 81). Con 1RM 200: Texas viernes 174 kg y Madcow lunes de la semana 4 174 kg. Pendientes anotados en el commit para C.P8, B.S2 y B.S12. |
| B.S2 | `RecipeContractPolicy` (reglas C1 a C10): inventario SOFT y luego HARD | **parcial** · `2f5e0bd69` (inventario SOFT); el paso a HARD sigue pendiente, tras B.S6 | 466 hallazgos en 38 de 40 recetas al entrar (C1 280, C9 80, C8 37, C10 20, C6 16, C5 15, C7 12, C4 6); hoy 452, porque los consumidores de B.S3 y B.S4 bajan C6 a 2. `RecipeContractPolicyTest` y `RecipeContractInventoryTest` nuevos; `ProtocolRecipeFidelityTest` compara con los días reales. Pendientes anotados en el commit para B.S6: prueba diferencial de C7, ratchet por regla, C4 por unión de días. |
| B.S3 | Motor de progresión de autor, parte 1: `CycleIncrement` por ciclo y por bloque, `WeeklyKg` en la materialización, R-23 y registro de consumidores | **hecho** · `5312decc2` | `AuthoredProgressionEngineTest` (15 al entrar), `ProgressionConsumerCoverageTest` (6) y `ProgramProgressCycleCloseTest` 6 → 26; 590 tests, 0 fallos (el mensaje del commit dice «18 → 26»; el conteo de `@Test` da 6 antes). Con TM 180, 108 y 198 el 5/3/1 BBB cierra el ciclo con 185, 110,5 y 203; Smolov Jr con 1RM 200 sube de 140 a 145 y 150 kg. Pendientes anotados en el commit: H13, H14 y el resto de B.S5. |
| B.S4 | Progresión por rendimiento como propuesta de TM: AMRAP en kilos, `TopSetPr`, `RepMaxAutoregulated` y «un lift con AMRAP corto no sube» | **hecho** · `b18597085` | `TopSetProgressionTest` (17) nuevo; `ProgramAutoregulationEngineTest` 7 → 24 (el mensaje del commit dice «6 → 23»), `ProgramAutoregulationResolutionTest` 5 → 11 y `ProgramProgressCycleCloseTest` 26 → 31; inventario de B.S2 457 → 452. Pendientes anotados en el commit: nSuns, Lilliebridge a `None`, el chin de Texas sin `amrap` y kpkn-rts-style sin AMRAP (B.S6). |
| B.S5 | Guardar TM (R-04), test de 1RM (R-19), «VER PROPUESTA» sin aceptar (R-03), tarjeta «Nuevo ciclo» y H13 | pendiente | R-23 ya lo cubrió B.S3. |
| B.S6 | Correcciones ALTA receta a receta | **parcial** · `4aa1bbb10` | Hecho: Texas 3d, Madcow (TM y `PERCENT_TM`) y Smolov semana 9. Pendiente: resto de Madcow (`CycleIncrement`, `supplementalOf`, `ProtocolBlock`), Texas 4d, KPKN SBD-4, Lilliebridge, Smolov y Smolov Jr, 5/3/1, Juggernaut, nSuns, PPL, RP y body-12-3, series sueltas, Candito, UHF-9, TSA y Calgary, GZCLP, Cube, RTS y Westside. Los consumidores de progresión ya existen (B.S3, B.S4): faltan los datos de Madcow (`CycleIncrement`), Juggernaut (`BLOCK`) y Lilliebridge (`None`). |
| B, documentación | DEC-w3-01, DEC-w3-02, DEC-w3-03 y DEC-w3-07 | pendiente | |

### Fase 1, paquete C: editorial y UX

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| C.P1, C.P2 | `PlanEditorial`, `PlanLabels`, `PlanEditorialTable` (55 fichas), `findForProgram` y `REPEATING_CYCLE` | **hecho** · `c802b7e78` | `PlanCatalogEditorialContractTest` (21) y `PlanLabelsTest` (13) nuevos; por filtro, 14 clases y 148 tests, 0 fallos. Fuera de este commit: planner y pantallas (C.P3, C.P5) y nombres de bloque de las plantillas. |
| C.P2b | Ocultar los 5 nativos históricos (D2), referencias editoriales y re-baseline | **hecho** · `d91a0ae2e` | Q1 de T006 pasa de 497 664 a 422 496 candidatos publicados; Q2 idéntico (2 304 filas, 1 224 viables); T-019 grupo F con el testigo `native:muscle-foundation-v2` (piso de 28 min). Registra DEC-w2-07. En este paso solo se corrió el grupo F de T-019; el resto de la matriz no se corrió. |
| C.P3 (con A.D1) | Planner por ficha editorial y `PlanGoalMatcher` compartido con la biblioteca | **hecho** · `2e3123245` | `SetupTrainingPlannerRankingTest` (14) y `PlanGoalMatcherTest` (14) nuevos. Por lectura de código, «Fuerza» en la biblioteca pasa de 31 a 29 entradas (DEC-w2-06). |
| C.P4 | `PlanCatalogEditorialContractTest` y tests asociados | **parcial** · `c802b7e78`, `d91a0ae2e`, `328752b13`, `2e3123245` | Hechos: el contrato y los tests de catálogo, `ProtocolAuditTest` (URL y disclaimer solo a los verificados, `328752b13`) y el test de orden del planner (`SetupTrainingPlannerRankingTest`). Falta `ProgramTemplateClaimsTest` (guardas por `blockGoalSemantics`). |
| C.P5 | Biblioteca y wizard: `displayName`, procedencia y resumen en las tarjetas; «Ver cómo funciona» en el paso PLAN; conteos «N planes revisados» | **en curso** | Sin commit al redactar: hay cambios en `CreateProgramTemplateSheet`, `WizardChoiceCard`, `SetupTrainingSteps`, `SetupWizardViewModel`, `SetupReviewStep` y `PlanInfoModel`. |
| C.P6 | Nombre del programa creado, modo y preselección (`preselectedPlanId`) | pendiente | Toca `Navigation.kt`, `MainActivity.kt` y `HomeScreen.kt`: avisar antes a la sesión de nutrición. |
| C.P7 | Procedencia y nombre del plan en el detalle del programa y en la revisión del wizard, con «Ver cómo funciona» | **hecho** · `aa3ccb40f` | `ProgramPlanDisplaySummaryTest` (27) nuevo; `PlanInfoModelTest`, `ProgramDetailViewModelTest` y `SetupWizardFullJourneyTest` verdes. Fuera de su alcance, anotado en el commit: el id del split en «Reparto semanal», el patrón emoji + nombre en el editor de sesión, el vocabulario viejo del foco en `ProgramHeroWidgets` y «1 días/semana». |
| C.P8, C.P9 | `PlanInfoModel` (puro) y `PlanInfoSheet` («Cómo funciona»); `ProtocolDetailSheet` delega en ella | **hecho** · `e5e428e54` | `PlanInfoModelTest` (42 al entrar). La hoja se abre desde `ProtocolDetailSheet` y, desde `aa3ccb40f`, desde el detalle del programa y la revisión; falta la biblioteca y las tarjetas del wizard (C.P5) y nadie le pasa el callback a Conceptos clave. |
| C.P10 | Glosario en dos niveles: `PlanGlossary` y cuatro conceptos en Conceptos clave | **hecho** · `75c15deb0` | `PlanGlossaryTest` (11) nuevo; el índice de Conceptos clave pasa de 27 a 31 conceptos (`ConceptosClaveIndexTest`). |
| C.P11 | Rechazos: tabla de textos y botones en la UI con el presentador único | **parcial** · dominio listo en `e275d0bbc` (A.C4) | Falta el cableado: la tabla de rechazos de la UI, los botones de un toque (A.C3) y los banners. Ver DEC-w2-02. |
| C.P12, C.P13 | Atribución con autores completos, planes KPKN sin URL ni «No afiliado a KPKN Fit», y copy menor | **hecho** · `328752b13` | 27 clases, 277 tests, 0 fallos (mensaje del commit). `ProtocolAttributionTest` +5 y `TrainingMaxWizardCopyTest` (3) nuevo. Cierra E-11 y E-32; E-26 y E-33 quedan parciales. |
| C.P14 | QA manual en emulador (Base) | pendiente | |

### Fase 1, paquete D: catálogo de ejercicios

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| D.M1 a D.M5 | Cinco altas SPECIALTY por el pipeline v2: `close_grip_bench_press__barbell`, `paused_back_squat__barbell`, `deadlift_to_knees__barbell`, `close_grip_lat_pulldown__cable` e `incline_biceps_curl__dumbbells` | **hecho** · `d68e91872` | Catálogo 96/206/527 con `catalogRevision` intacta (`v2-approved-2026-09-29-a`); SHA canónico `b2a652bb4f654f32e2925593858b3110e6c06a95637e7a2a4bad19d20cc6734a`; 63 definiciones CURATED. Verificación: 187 pruebas de `scripts/tests`, 10 de backend y 72 de Gradle por filtro, 0 fallos. |
| D, cableado Kotlin | Soportes en `supportRequirementsFor`, remaps del reconciliador, retirar `technique` de las recetas, bindings y `CABLE_HIGH_LOW_CONFIGURATIONS` | **parcial** · `d604ef579` (soportes de M1, M2 y M5 y `close_grip_lat_pulldown__cable` en `CABLE_HIGH_LOW_CONFIGURATIONS`) | Pendientes: remaps del reconciliador, retirar `technique` de las recetas y bindings. `CatalogIds` tiene las cinco constantes y ningún consumidor de recetas las usa todavía. |
| D, Q-07 y Q-08 | Etiquetas de opciones y notas obsoletas de `CATALOG_GAP_NOTES` | pendiente | |
| D, `CatalogIdsExistInCatalogTest` | Regla «nunca inventar ids» como test | **hecho** · `d604ef579` | 3 pruebas por reflexión sobre `CatalogIds`, `NativeCandidateTable` y `AuthoredExerciseBindings.all` contra los dos assets del catálogo. |

### Fases 2 y 3, y cierre

| Paso | Qué es | Estado |
|---|---|---|
| Fase 2 (plan 00 §7) | B.S9–B.S10, B.S11, B.S12, B.S13, B.S7, A.F1–A.F3 y las altas D1–D4 del catálogo con el resto de C/D | pendiente |
| Fase 3 (plan 00 §8) | URLs de terceros (E-30), niveles (E-31), etiquetas de día en español (E-36), paridad iOS, Q-02, G-07 y G-08 | pendiente |
| Documentación final (plan 00 §10.6) | Este README; DEV y DEC en `WIZARD_PLAN_DEVIATIONS.md`; actualizar `docs/WIZARD_BIENVENIDA_EXECUTION_STATE.md` | **parcial**: este README al día hasta `b18597085`; escritas DEV-r2-06 y DEC-w2-01 a DEC-w2-08, salvo la parte 2 de DEC-w2-04 (A.E2); DEC-w3-* y `WIZARD_BIENVENIDA_EXECUTION_STATE.md` pendientes |

## 5. Decisiones registradas

### 5.1 Decisiones del plan (plan 00 §3, l.29)

D1 a D4 las decidió el dueño el 2026-10-03; D5 a D7 se aplican con la recomendación indicada salvo que el dueño diga otra cosa.

| # | Decisión | Estado |
|---|---|---|
| D1 | Primera entrega «publicable» = Fase 1 (cobertura, editorial, recetas y progresión, 5 altas de catálogo y ocultar históricos); Fases 2 y 3 después | Decidida |
| D2 | Ocultar ya (`listed = false`) `full-body`, `gym-muscle`, `one-day`, `return-training` y `home-training`; mantener `machine-muscle` y `bodyweight` relegados; `strength-cardio` solo legado | Decidida; aplicada en `d91a0ae2e` (DEC-w2-07) |
| D3 | Recetas cuya descripción miente: Fase 1 con descripción honesta, PPL y RP sin RIR 0, PHUL y PHAT heredados relegados con «Versión anterior»; Fase 2 con las correcciones de fondo | Decidida; textos en `c802b7e78`; PPL y RP sin RIR 0 pendiente (B.S6) |
| D4 | Onda de RIR intra-ciclo en los 4 propios (principiante `3,3,3,2,2`; intermedio y avanzado `3,2,2,1,1`; descarga RIR 4), en Fase 2 | Decidida; se registrará como DEV-r2-07, porque DEV-r2-06 quedó para las máquinas por categoría (nota de numeración en `WIZARD_PLAN_DEVIATIONS.md`) |
| D5 | Fuerza y Fuerza y músculo sin material: redirección con botón de un toque, sin variantes de fuerza relativa | Por defecto; dominio listo en `e275d0bbc` (A.C1 y A.C4 parte pura, DEC-w2-02); cableado pendiente (A.C3, C.P11) |
| D6 | Prioridades y split en los 4 propios: prioridades ordenan empates, split por tabla con rechazo SPLIT reparable | Por defecto; prioridades aplicadas en `4f9845f81` (A.E1, DEC-w2-08); split pendiente (A.E2) |
| D7 | Texas y Madcow con `trainingMaxPercent = 0,87` y nota visible en el wizard de marcas | Por defecto; aplicada a Texas 3d y Madcow en `4aa1bbb10` y la nota del wizard de marcas en `328752b13`; Texas 4d pendiente |

### 5.2 Decisiones previas que el plan respeta (plan 00 §2, l.23)

Glúteos hasta 17,5 como banda blanda solo en propios (DEV-r2-02); puente una vez por semana (DEV-r2-01); el cardio del Atleta nunca se recorta (DEC-w1-01, DEV-r2-03); PHUL con doble progresión y PHAT sin ella (DEV-r2-04); al terminar un bloque el programa continúa con un solo aviso (DEV-r2-05); los planes de autor no se filtran por nivel (DEC-w1-04); un slot SPEED sin material es NotViable (DEC-w1-05); las adaptaciones van al final (DEC-w1-06).

### 5.3 Registradas en `docs/WIZARD_PLAN_DEVIATIONS.md`

Las líneas son las de [WIZARD_PLAN_DEVIATIONS.md](../../WIZARD_PLAN_DEVIATIONS.md).

| Id | Qué decide | Línea |
|---|---|---|
| DEV-r2-01 | Variantes corporales sin tirón: el MRV de glúteos (16) prevalece sobre el calendario literal. Aceptada el 2026-10-02 | 25 |
| DEV-r2-02 | Tolerancia blanda de glúteos hasta 17,5 series por semana, como último recurso y solo en planes propios. Aprobada e implementada | 139 |
| DEV-r2-03 | Progresión de cardio (R13) diferida; R14 absorbida en R13 | 165 |
| DEV-r2-04 | Progresión de carga de los originales PHUL y PHAT y de sus adaptaciones (R16): parcial, PHUL sí y PHAT no por ahora | 179 |
| DEV-r2-05 | Fin de bloque: el programa continúa solo con un aviso, sin pantalla de oferta (H-CICLO) | 191 |
| DEC-w1-01 | El fitter no recorta cardio ni descansos: a 45 min el cardio por defecto de 15 min da TIME_BUDGET | 207 |
| DEC-w1-02 | Músculo corporal de 3 días: el fitter retira los accesorios «de la tabla» y el mínimo real es 21 min | 225 |
| DEC-w1-03 | Matriz grupo A: piso independiente por fila y metadatos de composición en el arnés | 235 |
| DEC-w1-04 | Los planes de autor no se filtran por nivel | 244 |
| DEC-w1-05 | Un slot SPEED sin material es NotViable, no se sustituye | 250 |
| DEC-w1-06 | Las adaptaciones van tras los propios y los originales | 256 |
| DEC-w1-07 | No entregado: progresión de cardio de r2 §12.4; hoy es la desviación DEV-r2-03 | 262 |
| DEC-w1-08 | No entregado: cláusula de adaptaciones de originales de r2 §12.4; hoy es la desviación DEV-r2-04 | 272 |
| DEC-w2-07 | Los cinco nativos históricos dejan de listarse (D2, 2026-10-03) | 284 |
| DEC-w2-01 | Contrato de cobertura centrado en el plan propio, con reparación de un toque (A.B1, A.B2, A.B3 y A.B7) | 322 |
| DEV-r2-06 | Máquinas por categoría en los planes propios cuando solo se confirmaron soportes (A.B3). Desviación de r2 §13.1 y §13.3 | 365 |
| DEC-w2-06 | Ranking editorial del planner y matcher de objetivo compartido (A.D1 y C.P3) | 385 |
| DEC-w2-08 | La bolsa de prioridades de orden se aplica y se persiste en los planes propios (A.E1, D6) | 435 |
| DEC-w2-04 (parte 1) | Soportes reales de sentadillas y press, y tablas de candidatos para kettlebell, Smith y banda (A.B4 y A.B5) | 480 |
| DEC-w2-02 | Redirección honesta con un toque, sin «fuerza relativa» (A.C1 y A.C4 parte pura; D5) | 573 |
| DEC-w2-03 | `requiredMinutes` exacto con el cardio intacto (A.C2) | 629 |
| DEC-w2-05 | El pase a peso corporal solo sigue a rechazos de material y publica siempre los rechazos del pase pedido (A.D4, B-07) | 674 |

Previstas por el plan y todavía no escritas: la parte 2 de DEC-w2-04 (tabla split y calendario, A.E2) y, del paquete B, DEC-w3-01, DEC-w3-02, DEC-w3-03 y DEC-w3-07. La numeración quedó así: DEV-r2-06 es la de las máquinas por categoría (el plan la usaba también para la onda de RIR de §3 D4 y §7, que se registrará como DEV-r2-07) y DEC-w2-08 no estaba en el plan: la creó A.E1 para las prioridades de orden.

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
| D10 | Glosario en dos niveles | Aplicada en `75c15deb0` (C.P10) |
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

**Tests que ya existen y cubren lo entregado:** [PlanCoverageContractTest](../../../android-native/app/src/test/java/com/example/kpkn/domain/training/PlanCoverageContractTest.kt), `PlanRepairAdvisorTest`, `PlanRejectionPresenterTest`, `NativeMaterialEvidenceTest`, `NativeCandidateTableSlotsTest`, `CatalogIdsExistInCatalogTest`, `SetupTrainingPlannerRankingTest`, `PlanGoalMatcherTest`, `SetupWizardCandidateGateTest`, `OwnPlanPrioritiesAndSplitTest`, `RecipeContractPolicyTest`, `PercentResolverTest`, `ExemptionScopeMigrationTest`, `AuthoredProgressionEngineTest`, `ProgressionConsumerCoverageTest`, `TopSetProgressionTest`, `PlanCatalogEditorialContractTest`, `PlanLabelsTest`, `PlanInfoModelTest`, `PlanGlossaryTest`, `ProgramPlanDisplaySummaryTest` y los actualizados `PersonalizedPlanCatalogTest`, `NativeProfileSpecCatalogTest`, `WizardPluralCopyTest`, `SetupTrainingPlannerTest` y `AprendeCatalogAuditTest`. De los que lista el plan 00 §10.1 solo falta `ProgramNamingTest` (C.P6).

**Contrato de cobertura.** `PlanCoverageContractTest` elige el tier con `KPKN_COVERAGE_TIER` (`smoke`, que es el valor por defecto, `ci` o `full`). En `ci` se elige el shard 0 a 3 con `KPKN_COVERAGE_SHARD`, y `KPKN_COVERAGE_WORKERS` fija los hilos. La suite siempre queda en verde: solo falla si una corrida supera el techo de su tier. Techos actuales, tras A.C2 (`e275d0bbc`): smoke 8, ci 30/30/30/30 y full 120; los del baseline de `5849c32f0` eran smoke 226, ci 887/885/886/879 y full 3 537. La corrida `full` tarda unos 11 minutos con 4 hilos (638 s en `e275d0bbc`). Los informes quedan en `android-native/app/build/reports/coverage-contract/<tier>[-shard].txt` (por ejemplo `smoke.txt`, `full.txt`, `ci-0.txt`); esa carpeta es salida de compilación y no se versiona.

**Oráculos de aceptación (plan 00 §10.4).** Comprobados hasta ahora: Madcow con 1RM 200 nunca prescribe más de 174 kg (`4aa1bbb10`); 5/3/1 cierra el ciclo con TM +2,5 y +5 y Smolov Jr semana 2 sube 5 kg (`5312decc2`); T006 Q2 sin COMPOSITION y positivos 58/58 (idénticos en cada paso del paquete A); ningún `displayName` ni `summary` con jerga, ids o inglés (lo vigila `PlanCatalogEditorialContractTest`). Pendientes: que toda fila de la rejilla de cobertura termine Ready o con una reparación verificada (quedan 120 violaciones, de Atleta de 1 día con solo barra de dominadas) y que «VER PROPUESTA» no acepte la propuesta (R-03, B.S5).

## 7. Decisiones pendientes del dueño (2026-10-03, noche)

Se suman a las de la sección 5.5. Cada una tiene su detalle en la entrada que se cita de `docs/WIZARD_PLAN_DEVIATIONS.md` y un valor por defecto ya aplicado.

- **Clase de plan frente a nivel en el orden del planner (DEC-w2-06).** Hoy el nivel pesa más que la clase de plan (el orden del plan 00, A.D1): una persona avanzada ve Smolov, Smolov Jr y Coan delante de planes completos de otro nivel. Si se quiere que las especializaciones y los complementos cierren SIEMPRE la lista, basta invertir los criterios 2 y 3 del comparador de `SetupTrainingPlanner`.
- **`bench_press__kettlebell` como candidato (DEC-w2-04 parte 1).** r2 §13.3 la cita («si hay banco») y existe APPROVED en el catálogo; el plan 00 no la lista en B5 y no se añadió.
- **`NO_VALID_SUBSTITUTION` y el pase a peso corporal (DEC-w2-05).** Con la regla literal (todos los rechazos `APPARATUS_*`), una adaptación de autor sin sustituto impide el pase; si el dueño quiere que no lo impida, es una línea de `bodyweightPassAllowed`.
- **Programas Madcow ya activados (pendiente que dejó B.S1, `4aa1bbb10`).** Conservan la receta persistida anterior a los cambios de datos de B.S1 (base de carga al 100 % del 1RM y porcentajes relativos al top set; L-01 y L-02). No está decidido si se dejan como están, se avisa a quien los tenga o se migran.
- **Anatomía de las altas D.M1 a D.M5 (sección 5.5).** `deadlift_to_knees` (cuádriceps y rodilla como PRIMARY: suma 0,5 serie de cuádriceps frente al peso muerto convencional cuando Sheiko migre a esa configuración), `close_grip_lat_pulldown` (trapecio como SECONDARY, igual que `pull_up`), `close_grip_bench_press` (tríceps como SECONDARY) y el solape del alias «curl inclinado» de `incline_biceps_curl` con el curl Bayesian.
- **Fuerza y músculo con mancuernas antes que `APPARATUS_UNKNOWN` (DEC-w2-01, B2).** Con mancuernas, el plan no se bloquea por rack y banco sin confirmar; se eligió para no dejar sin plan al gimnasio sin confirmar (E8) mientras no existiera el botón de reparación. Conviene revisarlo cuando A.C3 y C.P11 lo cableen: se revierte moviendo un bloque del gate.
- **Copy de `APPARATUS_ABSENT` (DEC-w2-02).** El presentador dice «Este plan necesita X, que dijiste que no tienes.»; el diseño editorial (11 §4) decía «…, que marcaste como que no tienes.». Se evitó la forma con pronombre («… no lo tienes») porque no concuerda con «rack de sentadilla», «mancuernas» o «barra y carga».
