# Curaduría integral de programas — evidencia e índice

## Estado vigente — 2026-10-05

**Fase 1 abierta.** El índice F0.3 y la consolidación de evidencia se entregan en este commit documental. Código entregado: recetas y metadatos, 32 rutas en `f56cd6e76`; A39 `54668bcb8`; Q-07 `ede1123d4`; tests editoriales `6fcd47e08`; consumidor TM `7c0c27e5b`. Catálogo `8eb3432a2`/`844c3d1d9`, SHA `1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099`.

**Consumidor TM entregado y probado.** Cuatro rutas comparten la resolución de RM+PERCENT_TM, conservan los kg y las repeticiones prescritas y dejan pendiente un TM ausente, sin inferirlo de PR o historial. El filtro excepcional de la sesión 73134 pasó exit 0/BUILD SUCCESSFUL en 2 min 42 s: nueve suites, 143 tests sin fallos, errores ni omitidos, incluidos los 13 nuevos. C verificó los hashes de los XML archivados y las cuatro fuentes. La autorización humana `call_FPTcKi9Z0wPLyDWEltPGNO3K` de una ejecución adicional quedó consumida. Se preserva el intento 3 fallido por dos oráculos literales Double; la reparación comparó exactamente el porcentaje original con el posterior, sin cambiar tolerancias, kg ni producción. El caso publicado de 174/178,35 kg pasó antes y después de esa reparación.

| Evidencia actual | Resultado | Alcance |
|---|---|---|
| Contrato FULL final | Exit 0/BUILD SUCCESSFUL, 14 min 49 s; 2 tests sin fallos, errores ni omitidos; 25.650 filas, 13.498 Ready y 12.152 rechazos honestos, todos reparados; cero violaciones de las siete clases | Generación C1–C5, `native-cycle-2`, seis ratchets en cero, Temurin C2 normal y 4 hilos. No sustituye la suite Base global ni los tests del consumidor |
| Editorial actual | Library 19/0 y PlanInfo 52/0, total 71/0; BUILD SUCCESSFUL, 52 s; exit estructurado NO_OBSERVABLE | XML, log y fuentes archivados; dos rutas en `6fcd47e08`; no acredita QA visual |
| Diagnóstico E16 | Nutrition 5/0; p95 mediana 26,2 ms frente a 50 ms; BUILD SUCCESSFUL, 79 s; exit estructurado NO_OBSERVABLE | C2 normal; no se extrapola a la suite Base completa |
| Base global | Full1 rechazada; full2 OOM e incompleta; full3 interrumpida por C2; full4 C1 con timeout, exit 124 a los 3.600 s | Sin suite Base actual aceptada; otra ejecución con JBR21 sigue sin autorización |
| Consumidor TM actual | Filtro excepcional 73134: exit 0/BUILD SUCCESSFUL, 2 min 42 s; nueve suites y 143 tests sin fallos, errores ni omitidos; 13 nuevos/0 | Commit `7c0c27e5b`; fuentes y XML archivados. No se extrapola a Base completa ni UI |
| APK nuevo e instalación | Assemble-only 51687: exit 0/BUILD SUCCESSFUL, 44 s; SHA `09ae806279ad93688cab49ff7f720fa9868a0191cdb1e99135f0fe9e52e6add8`, 553.916.698 bytes, UTC 03:37:07.4030450. Instalación Success a las 03:52:17 UTC | Arranque adicional con Status ok, COLD de 4.883 ms, MainActivity al frente y Welcome observado en XML/PNG a las 03:52:49, sin modal del sistema |
| QA pública | El FULL temporal llegó a Experiencia de entrenamiento y quedó aparcado antes de configurar o activar un plan | Los 28 casos públicos siguen NOT_RUN; no se aceptó una fixture FULL. El arranque PASS se registra por separado |
| Conservación | Restore ORIGINAL posterior: exit 0/PASS a las 04:05:22.5294762 UTC; 207 archivos y WAL con bytes equivalentes, app detenida | Backup original privado `35f20af…3bdf` y parcial de QA, 259 miembros/SHA `4fe4e207…3f2e9`, preservados. C no leyó tar, DB ni preferencias |

**Recuperación acotada.** El dueño respondió «Autorizar recuperación y un intento adicional» a `call_gCvy5XfLG2s1qLUaYCUHz3Pw`. La recuperación de `emulator-5554` usó RAM temporal de 4.096 MB, frente a los 6.144 MB previos, y mantuvo CPU6, los flags `-no-snapshot` y la configuración persistente. El backing `ram.img` cambió entre boots: 6.442.516.480→4.295.032.832 bytes, SHA D12DE041…6424FA→BBA36E59…263607. La igualdad de bytes y metadata se verificó dentro de cada stop, no entre los dos boots; no se prueba el estado del disco completo ni Quick Boot, y no hay copia del backing RAM anterior disponible para restaurarlo. El boot pasó en menos de 45 s; `5556` quedó intacto. El único arranque adicional mostró Welcome sin ANR. Root aparcó el flujo temporal, conservó su backup parcial y restauró ORIGINAL. El QEMU nuevo, PID47564, se cerró con exit 0; la configuración y el backing RAM conservaron bytes y metadata equivalentes únicamente dentro de ese cierre, entre las 04:09:18 y 04:09:27 UTC. Puertos y candado quedaron libres; Gradle/ADB5554 se cedieron a alimentos a las 04:10:10 UTC. Las excepciones de arranque y de prueba quedaron consumidas; no autorizan lanzamientos o tests automáticos adicionales, otra suite Base ni JBR21, y no constituyen una pausa pedida por el dueño.

**Pendientes de aceptación.** Los 28 casos públicos de QA, incluida la observación del consumidor, y la decisión sobre otra suite Base completa. QEMU47564 ya está cerrado; la configuración permanece igual y el backing RAM es equivalente solo antes/después del último stop y alimentos recibió la prioridad de Gradle/ADB5554. Madcow/material y programas activos/custom/legacy mantienen sus reservas específicas; C10 permanece SOFT y la paridad iOS/backend sigue diferida a sus fases. Se conservan el cardio del Atleta, glúteos hasta 17,5 solo en planes propios, PHUL con progresión KPKN/PHAT sin ella y el oráculo Madcow de 174/178,35 kg.

## 1. Qué es esta carpeta

Aquí está la evidencia de la curaduría integral de programas de KPKN Fit (editorial, lógica de recetas, progreso y cobertura), aprobada el 2026-10-03: el plan, dos auditorías y un diseño. Las auditorías son lectura estática del código con referencias archivo:línea. No se ejecutó nada para escribirlas, así que los efectos numéricos que citan salen de leer código, no de medirlo. La carpeta sirve para trazar cada cambio: un hallazgo (sección 3) apunta al paso del plan que lo resuelve, y ese paso al commit que lo entregó (sección 4). Para seguir un hallazgo, busca su id en la sección 3, lee su paso y su estado, y abre el commit con `git show <hash>`.

## 2. Índice de documentos

| Archivo | Qué contiene | Secciones clave (número de línea del archivo) |
|---|---|---|
| [00-PLAN-curaduria-programas-2026-10-03.md](00-PLAN-curaduria-programas-2026-10-03.md) (243 líneas, sin salto final) | Plan aprobado: contexto, decisiones, paquetes A a D, Fase 0, pasos de la Fase 1, Fases 2 y 3, orden, verificación y riesgos. Es el sitio principal donde quedan resumidos los hallazgos P-, A-, B-, N-, R- y Q- | §1 Contexto (l.10) · §2 decisiones previas del dueño (l.23) · §3 decisiones D1–D7 (l.29) · §4 paquetes A–D (l.45) · §4b modo de ejecución (l.60) · §5 Fase 0, F0.0 a F0.4 (l.70) · §6 Fase 1: paquete A (l.82), B (l.115), C (l.163), D (l.179) · §7 Fase 2 (l.195) · §8 Fase 3 (l.205) · §9 orden y dependencias (l.211) · §10 verificación (l.223) · §11 riesgos (l.234) |
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
| E-06 | Smolov Jr: título, descripción y fuente (es solo sentadilla) | 01 §2.2, l.393 | C.P2 (texto, kind ESPECIALIZACION); B.S6 (receta); E-30 (URL heredada) | **parcial** · textos `c802b7e78`, etiquetas y receta `8a3adefd4`, ficha actualizada `28a8a6f5e`; URL pendiente de Fase 3 y origen en banca pendiente de contraste externo (§7) |
| E-07 | Coan y Philippi: complemento de un día ofrecido como plan completo | 01 §2.2, l.406 | C.P2 (kind COMPLEMENTO, rank 920); C.P12 (autor en la definición) | **hecho** · ficha `c802b7e78` y autores completos en la definición `328752b13` |
| E-08 | Cube: rotación inexistente | 01 §2.2, l.417 | C.P2 (descripción honesta, D3); B.S6 (Cube); B.S7 (rotación) | **parcial** · datos de Fase 1 `8a3adefd4` y descripción honesta `28a8a6f5e`; rotación real diferida a B.S7 |
| E-09 | RP-style, PPL KPKN y RTS-style prometen lo que la receta no hace | 01 §2.2, l.421 | C.P2 (texto honesto, D3); B.S6 (PPL y RP sin RIR 0); B.S7 (rampa RP, RTS por top sets) | **parcial** · PPL y RP sin RIR 0 y con descargas `8a3adefd4`, textos honestos `28a8a6f5e`; rampa RP y RTS por top sets en Fase 2 |
| E-10 | Frecuencia declarada distinta de los días reales (Smolov, Candito, Lilliebridge) | 01 §2.2, l.429 | B.S6 (Candito semana 1 a 4 días; Smolov semanas 9 a 13); C.P11 (rechazo FREQUENCY); A.F1–A.F3 (A-01). El plan no asigna paso a los weekdays de Lilliebridge | **parcial** · Candito a 4 días, Lilliebridge con weekdays coherentes y exenciones explícitas de Smolov `8a3adefd4`; contrato editorial sin excepciones de frecuencia `28a8a6f5e`; rechazo de la UI `76950161e`; protocolos de otra frecuencia plegados en Fase 2 |
| E-11 | 4 protocolos KPKN con «No afiliado a KPKN Fit» y URL `kpkn.fit` sin sentido | 01 §2.2, l.436 | C.P2 (`attributionLine`); C.P4 (`ProtocolAuditTest:84-85`); C.P12 | **hecho** · C.P2 `c802b7e78` (`attributionLine`) y C.P12 `328752b13` (los 5 protocolos KPKN sin URL ni «No afiliado a KPKN Fit»; `ProtocolAuditTest` exige URL y disclaimer solo a los verificados) |
| E-12 | Atribución y detalle del método sin superficie de lectura | 01 §2.2, l.447 | C.P8–C.P9 (`PlanInfoSheet`); C.P5 (biblioteca y tarjetas del wizard) | **hecho** · C.P8–C.P9 `e5e428e54` (la hoja «Cómo funciona», con fuente, atribución y los plegables de las autoradas), C.P7 `aa3ccb40f` (detalle del programa y revisión del wizard) y C.P5 `1bbe7a6fe` (el tap de la biblioteca abre la hoja y desaparece el `AlertDialog`; las tarjetas del wizard llevan «Ver cómo funciona»; los métodos de la biblioteca siguen por `ProtocolDetailSheet`, que delega en la misma hoja) |
| E-13 | Biblioteca: nivel en inglés | 01 §2.2, l.455 | C.P1 (`CatalogLevel.label`); C.P5 (biblioteca) | **hecho** · `c802b7e78` (`CatalogLevel.label`) y C.P5 `1bbe7a6fe` (la tarjeta imprime `PlanLabels.metaLine`, «4 días por semana · Semana que se repite · Intermedio»; `LibraryCardModelTest` vigila que ningún texto lleve un nivel en inglés) |
| E-14 | `ProtocolDetailSheet`: códigos internos y jerga | 01 §2.2, l.459 | C.P8–C.P9 (notas llanas de la tabla) | **hecho** · C.P8–C.P9 `e5e428e54` (`ProtocolDetailSheet` delega en la hoja nueva: sin requisitos crudos, sin «Notas KPKN» con códigos, con el resumen y las «Notas del método» de la tabla `c802b7e78`) |
| E-15 | `ProtocolDetailSheet`: calentamiento presentado como prescripción | 01 §2.2, l.470 | C.P8–C.P9 (`PlanInfoModel`) | **hecho** · C.P8–C.P9 `e5e428e54` (la semana tipo se arma sin calentamientos y con las series agrupadas) |
| E-16 | Paso PLAN del wizard: solo título, subtítulo técnico y motivos | 01 §2.2, l.476 | C.P5 («Ver cómo funciona»); C.P8–C.P9 | **hecho** · C.P5 `1bbe7a6fe` («Ver cómo funciona» al pie de cada tarjeta del paso PLAN, fuera de la zona que elige: abre la hoja de `e5e428e54` con la primera semana real de la evaluación en caché y «Elegir este plan»); la revisión del wizard ya la abría (`aa3ccb40f`). Los motivos de la tarjeta («Se ejecuta con el equipo que has elegido») siguen igual: la corrección propuesta era el botón |
| E-17 | Programa activado: «Procedencia no declarada» e identificadores crudos | 01 §2.2, l.482 | C.P7 (`findForProgram`) | **hecho** · C.P7 `aa3ccb40f` (procedencia y nombre desde `findForProgram`; fin de «Procedencia no declarada» y de «Método anterior · {id}») |
| E-18 | «Planes», configurar: se descarta el plan elegido | 01 §2.2, l.489 | C.P6 (`preselectedPlanId`) | **hecho** · `76950161e` transmite `planId` desde la biblioteca como intención preseleccionada del wizard; la corrección de callejones sin salida y persistencia de esa intención está entregada en WIZFIX `54668bcb8`; QA pendiente |
| E-19 | PHUL y PHAT: tres versiones conviviendo y orígenes mal etiquetados | 01 §2.2, l.493 | C.P2 (origen, rank 990, «versión anterior»); C.P5 (badge y filtros); B.S7 (ocultar heredados). 11 §0, punto 4: no se renombra el filtro «Versión KPKN» | **parcial** · `c802b7e78` (ficha) y C.P5 `1bbe7a6fe` (la tarjeta de la biblioteca dice «Versión anterior» y los filtros de procedencia se deciden por `origin`: los 5 planes KPKN de receta fija ya no caen en «Versión KPKN»; la biblioteca ordena por `rank`, así que las versiones anteriores quedan al final); ocultar los heredados en Fase 2 (B.S7) |
| E-20 | Plantillas: jerga y lenguaje de contrato interno | 01 §2.2, l.502 | C.P2 (nombres y resúmenes); nombres de bloque coordinados con B | **parcial** · `c802b7e78` (textos); mapa C/B commiteado junto en `f56cd6e76`, con espejo/semántica Claims2/2 en48/50; aceptación global/QA pendientes |
| E-21 | Hipertrofia: nombres de plan y de bloque que no corresponden al contenido | 01 §2.2, l.511 | C.P2 (ídem) | **parcial** · `c802b7e78` (textos); mapa C/B commiteado junto en `f56cd6e76`, con espejo/semántica Claims2/2 en48/50; aceptación global/QA pendientes |
| E-22 | Plantillas simples: títulos amigables sobre estructuras vacías | 01 §2.3, l.522 | C.P2 (kind ESTRUCTURA); C.P6 (nombre del programa creado) | **parcial** · ficha ESTRUCTURA `c802b7e78` y `ProgramNaming` `76950161e`; estructuras fuera de candidatos del planner. WIZFIX entregó la ruta limitada de creación; guardas verdes y commit `54668bcb8`; QA pendiente (§7) |
| E-23 | Nativos históricos: descripciones contradictorias o ambiguas | 01 §2.3, l.529 | C.P2, C.P2b | **hecho** · `c802b7e78` y `d91a0ae2e` (5 ocultos; `strength-cardio` sin referencias) |
| E-24 | Perfiles propios: fugas de razonamiento interno en el texto | 01 §2.3, l.536 | C.P2; A.E1–A.E2 (prioridades y split) | **parcial** · textos `c802b7e78`, prioridades `4f9845f81` y reparto testigo `a94539866`; D6 implementada. El foco sigue como etiqueta en los propios y no tiene paso asignado |
| E-25 | Jerga sin explicar (TM, 1RM, AMRAP, T1/T2/T3, MEV/MRV…) | 01 §2.3, l.542 | C.P10 (glosario); C.P8–C.P9 | **hecho** para el alcance del plan · glosario `75c15deb0`, hoja `e5e428e54`, accesos `1bbe7a6fe` y enlace a Conceptos clave desde biblioteca/wizard/revisión `76950161e`; QA visual pendiente |
| E-26 | Copy de PHUL y PHAT autoradas | 01 §2.3, l.547 | C.P2; C.P8–C.P9 (fuente en letra pequeña); C.P12 (`AuthoredSourceRecord:71`) | **parcial** · `c802b7e78` (fichas), C.P12 `328752b13` (`AuthoredSourceRecord` sin «§12.4»), C.P8–C.P9 `e5e428e54` (fuente y plegables «Lo que publica el autor» y «Configuración inicial KPKN» en la hoja) y C.P5 `1bbe7a6fe` (la tarjeta de la biblioteca ya no imprime «autor · edición consultada»: lleva la procedencia y la línea de atribución de la ficha); queda que el autor sale en las dos, en la procedencia («Original fiel · {autor}») y en la atribución («Original fiel de {autor} (…). No afiliado a {autor}.»), a decidir al verlo en C.P14 |
| E-27 | Duración y ciclo: los ciclos que se repiten salen como «Ciclo finito» | 01 §2.3, l.555 | C.P1 (`REPEATING_CYCLE`) | **hecho** · `c802b7e78` |
| E-28 | `references`, `capabilities` y `focuses` inconsistentes | 01 §2.3, l.560 | C.P2b (referencias editoriales); C.P3 (`PlanGoalMatcher`); A.D1 (exención de foco) | **parcial** · `d91a0ae2e` (BBB en Fuerza y músculo; `strength-cardio` fuera de Músculo) y C.P3/A.D1 `2e3123245` (`PlanGoalMatcher` compartido con la biblioteca, prefiltro de capacidades de Atleta y exención de foco para las plantillas con receta); quedan el PHUL heredado sin «Músculo» y las capacidades vacías de PHUL y PHAT autoradas, que el matcher nuevo no usa (solo Atleta completo lee las capacidades) |
| E-29 | Nombre y modo del programa creado | 01 §2.3, l.567 | C.P6 (`programNameFor`, `programModeFor`) | **hecho** · `76950161e`: `ProgramNaming` deriva nombre y modo de la ficha en el wizard, personalizador y ProgramsViewModel; `ProgramNamingTest` (9) |
| E-30 | Fuentes y URLs de terceros | 01 §2.3, l.577 | plan 00 §8 (fuente de verdad de URLs) | **Fase 3** |
| E-31 | Niveles de protocolos discutibles (GZCLP, nSuns, PHAT adaptado) | 01 §2.3, l.583 | plan 00 §8 (niveles contra fuentes) | **Fase 3** |
| E-32 | Autor frente a disclaimer | 01 §2.3, l.587 | C.P12 (`attributed()` con autores completos) | **hecho** · C.P2 `c802b7e78` (`attributionLine` con autores completos) y C.P12 `328752b13` («Mark Rippetoe y Glenn Pendlay», «Andy Baker y Mark Rippetoe», «Madcow (a partir de Bill Starr)», «Ed Coan y Mark Philippi»; el disclaimer repite el autor completo) |
| E-33 | Textos del wizard (`SSD:284`, `WCC:143`, `TrainingMaxWizard`, «AUTO» y «PROPOSE») | 01 §2.3, l.591 | C.P12–C.P13 | **parcial** · C.P12–C.P13 `328752b13` (los cuatro textos que el plan asigna al paso: «Recomiéndame un plan», WizChat sin «tal cual es, sin recortar su receta», «Máximo de entrenamiento (TM)» y «Mantener el ajuste automático» y «Volver a proponer cambios» en lugar de «AUTO» y «PROPOSE»); sin paso del plan: `WCG:36` (objetivos heredados) y que cerrar la hoja de marcas cree el programa de todos modos |
| E-34 | Detalles de `ProtocolDetailSheet` (semana tipo, leyenda, `toInt()`) | 01 §2.3, l.598 | C.P8–C.P9 | **hecho** · C.P8–C.P9 `e5e428e54` (sin la semana tipo duplicada ni la leyenda «[KPKN]»; cifras con coma decimal sin truncar, 62,5 y no 62) |
| E-35 | Biblioteca: orden y campos que no se muestran | 01 §2.3, l.604 | C.P1 (`rank`); C.P5 | **hecho** · A.D1/C.P3 `2e3123245` (cabecera nueva de la biblioteca: «Elige un plan para ver cómo funciona…») y C.P5 `1bbe7a6fe` (orden editorial por `rank` y nombre; la tarjeta lee la ficha: nombre, procedencia, resumen, metadatos y línea de atribución con el «No afiliado a …»; el material y la URL de la fuente están en la hoja que abre el tap) |
| E-36 | Idioma y mayúsculas en etiquetas de día y de bloque | 01 §2.4, l.611 | plan 00 §8 | **Fase 3** |
| E-37 | Restos y campos huérfanos (emoji de BBB, `audienceLabel`…) | 01 §2.4, l.615 | C.P7 solo para el emoji (chip sin emoji, 11 §5) | **parcial** · C.P7 `aa3ccb40f` (el chip del detalle muestra el nombre del catálogo sin emoji, así que ya no sale la «5» suelta de BBB; el dato `emoji` sigue mal formado); `audienceLabel`, ids de plantilla y el parámetro `adapted` sin uso: **sin paso** (BAJA) |
| E-38 | Tono y gramática de títulos | 01 §2.4, l.621 | ninguno propio; la tabla de C.P2 unifica los títulos | **parcial** · `c802b7e78` (títulos de la tabla) |
| E-39 | Nombres de bloque crípticos (F1–F4, B1–B3, «Cubo», «Switching»…) | 01 §2.4, l.626 | ninguno en el plan | **sin paso** (BAJA) |

### 3.2 Prefijo L- (lógica de recetas, 35 hallazgos)

Origen: [02](02-auditoria-logica-recetas-fijas.md) §2.5. L-01 a L-07 son ALTA; L-08 a L-29 y L-32 MEDIA (L-20 BAJA/MEDIA); L-30, L-31 y L-33 a L-35 BAJA. No hay ids con prefijo M- en ningún documento; M1–M5 son las altas del catálogo (sección 3.10). L-36 se cita en 02, l.169, pero no tiene hallazgo.

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| L-01 | Madcow: doble escalado de las rampas, con cargas por encima del 1RM | 02 §2.5, l.521 | B.S1 (`PercentResolver`); B.S6 (resto de Madcow) | **parcial** · resolutor `4aa1bbb10`, datos/CycleIncrement `8a3adefd4`; DEC-MADCOW-20261004 aprobada directamente: conservar lunes174/viernes178,35 con1RM200 y corregir dos oráculos de §10. QA/aceptación global finales pendientes |
| L-02 | Texas 3d/4d y Madcow: la base de carga es el 1RM | 02 §2.5, l.535 | D7; B.S1; B.S6 | **hecho** · Texas3d/Madcow `4aa1bbb10`; Texas4d TM0,87/top100/volumen90 en `8a3adefd4`; base5RM documentada en DEC-w3-02 y oráculos Madcow corregidos por decisión directa DEC-MADCOW-20261004 |
| L-03 | Progresiones declaradas pero inertes (5/3/1, Juggernaut, Smolov, Smolov Jr, Madcow, Texas, Lilliebridge) | 02 §2.5, l.543 | B.S3–B.S5 (motor de autor); B.S6 | **parcial** · consumidores B.S3–S5, Madcow a `CycleIncrement` y Lilliebridge a `None` en `8a3adefd4`; rutas de bloque cubiertas en `27e2d4938` y `3d7c67474`; datos BLOCK/descargas de Juggernaut en `f56cd6e76`, filtros verdes; aceptación global/QA pendientes |
| L-04 | `tmDeltaForAmrap`: unidades, signo y serie AMRAP (nSuns, GZCLP) | 02 §2.5, l.552 | B.S3–B.S5 (AMRAP); B.S6 (nSuns) | **hecho** en lo commiteado · unidades/signo en `b18597085` y AMRAP de nSuns a la serie 1+ al 95 % en `8a3adefd4`; interpretación del mínimo de 1+ bajo revisión provisional (§7) |
| L-05 | Porcentajes en %TM presentados y validados como %1RM | 02 §2.5, l.558 | B.S1–B.S2 (regla C7); B.S6 (datos de TSA, Calgary, KPKN SBD-4) | **parcial** · PercentBasis/H11/H11b `4aa1bbb10`, inventario C7 `2f5e0bd69`, TSA/Calgary a TM 1,0 y picos SBD-4 `8a3adefd4`; conversión de BLOCK/H8/checkTaperLastHeavy y demás límites pesados a %1RM efectivo commiteada en `f56cd6e76`; filtros verdes y aceptación global/QA pendientes |
| L-06 | KPKN SBD-4: el peso muerto nunca se entrena pesado | 02 §2.5, l.571 | B.S6 (KPKN SBD-4) | **hecho** · `8a3adefd4`: PM con base, intensidad y pico pesado; calendario SQ lun, BV mar, PM jue, BH vie |
| L-07 | Lilliebridge: ninguna semana con peso muerto pesado | 02 §2.5, l.575 | B.S6 (Lilliebridge) | **hecho** · `8a3adefd4`: PM pesado en semanas pares y sentadilla pesada en impares; progression `None` |
| L-08 | nSuns: segunda banca repite tabla de volumen | 02 §2.5, l.582 | B.S6 | **hecho en datos** · `f56cd6e76`: T1 volumen nueve series65/75/85/85/85/80/75/70/65, T1 pesado diferente con AMRAP1+95. Guardas Fidelity:nsuns_t1_bench_has_nine_sets(:47)/nsuns_amrap_is_on_the_heavy_single(:515). Filtros previos verdes; aceptación Base/UI separada |
| L-09 | GZCLP, J&T y Rippler: solo etapa 1, T2 sin `liftSlot`, `repsMax` perdido | 02 §2.5, l.586 | B.S6 (GZCLP: descripción, `CycleIncrement`, `rpeSets`); B.S7 (etapas y reset) | **parcial** · `8a3adefd4`: GZCLP con CycleIncrement y T3 15–20 con repsMin; texto honesto `28a8a6f5e`. Etapas/reset y resto del hallazgo en Fase 2 |
| L-10 | UHF-9: semana 5 con esquema de volumen en el bloque «Intensidad»; sábado con déficit | 02 §2.5, l.595 | B.S6 (UHF-9) | **hecho** · `8a3adefd4`: bloques 5+4 y sábado sin dl-def ni bp-pause |
| L-11 | Juggernaut: descargas sin kindDELOAD | 02 §2.5, l.599 | B.S6 | **hecho en datos** · `f56cd6e76`, JuggernautSheikoSmolov.kt:112–150: semanas4/8/12/16 DELOAD/blockGoalDELOAD, T1 del autor y T2/T3 a2seriesRPE6. FidelityTest:1066–1100 fija semanas/kind/dosis/CycleIncrementBLOCK. Fuente/filtros entregados; Base global/UI pendientes |
| L-12 | 5/3/1 BBB y FSL: la descarga mantiene el suplementario | 02 §2.5, l.606 | B.S6 (5/3/1) | **hecho** · `8a3adefd4`: semana 4 DELOAD sin suplementario; BBB al 50 % y subida por ciclo |
| L-13 | Cube: sin rotación pesado, explosivo y repeticiones | 02 §2.5, l.613 | B.S6 (Cube); B.S7 (rotación) | **parcial** · `8a3adefd4`: PM por repeticiones con AMRAP y enlace supplemental incoherente retirado; descripción honesta `28a8a6f5e`; rotación pesada/rápida/repeticiones en Fase 2 |
| L-14 | Westside: variantes ME inalcanzables y sin resistencia acomodada | 02 §2.5, l.620 | B.S6 (Westside) | **parcial** · `8a3adefd4`: variantes ME alcanzables y progression None; la resistencia acomodada no queda acreditada y la especialización restante sigue diferida |
| L-15 | Calgary y Sheiko: offsets derivados, relleno KPKN y parches de técnica | 02 §2.5, l.626 | B.S6 (Calgary: `trainingMaxPercent` y `sq-tech`); B.S7 (Sheiko con tirón) | **parcial** · `8a3adefd4`: Calgary TM 1,0 y sentadilla con pausa curada; Sheiko usa DL_TO_KNEES. Sheiko con tirón y demás contenido de Fase 2 pendiente |
| L-16 | Texas 3d: el chin AMRAP sin `liftSlot` escala los cuatro TM | 02 §2.5, l.632 | B.S6 (chin sin `amrap`); B.S3–B.S5 (`liftSlotFor`) | **hecho** · `b18597085` impide TM sin lift y `8a3adefd4` retira el AMRAP de las dominadas de Texas |
| L-17 | Texas3d: OHP sin top set y DL de relleno | 02 §2.5, l.637 | sin paso explícito; datos dentro B.S6 | **parcial** · `f56cd6e76`, TexasWendlerProtocols.kt:48–75: DL lunes1×5 al90%TM/subordinadoSQ, OHP recuperación3×5 al80%TM. No se afirma introducir un top set OHP ni cerrar todo el hallazgo; oráculo editorial Texas pasó52/0 dentro exact2 |
| L-18 | Texas 4d: referencias SQ_HIGH y SQ_LOW distintas, accesorios idénticos | 02 §2.5, l.640 | B.S6 (Texas 4d re-basado) | **parcial** · `8a3adefd4` rebasa Texas 4d a TM 0,87; no se acredita cierre de los accesorios idénticos del hallazgo |
| L-19 | Smolov semana 9: 5 repeticiones al 90 % del 1RM | 02 §2.5, l.644 | B.S1 (`n(1,5,90)` pasa a `n(1,2,90)`; H11b); B.S1–B.S2 y B.S6 (rótulo y días) | **parcial** · `4aa1bbb10` corrige a 2@90 y añade H11b; `8a3adefd4` conserva 3 días con exenciones C4 w9–w13 justificadas. El rótulo Movilidad no se da por corregido |
| L-20 | Candito: semana 1 con 5 días frente a un claim de 4; pico sin máximo | 02 §2.5, l.648 | B.S6 (Candito semana 1 a 4 días) | **parcial** · `8a3adefd4` corrige semana 1 a 4 días y `28a8a6f5e` explica la variante; no se acredita cierre del pico sin máximo |
| L-21 | TSA: RPE solo SQ; descarga sin recorteT2 | 02 §2.5, l.652 | B.S6 TSA/Calgary | **parcial** · `f56cd6e76`, ClassicPlProtocols.kt:470–485: RPE aplicado aSBD, semana5 kindDELOAD/T1al60 y RIR mínimo4. El recorte de series T2 no se acredita por ese mapa (withMinRir cambia esfuerzo); esa parte conserva reserva de implementación, sin convertir filtros en cierre total |
| L-22 | PHUL heredado: potencia a 82 %TM, sin tirón vertical, duplica al autorado | 02 §2.5, l.656 | C.P2 y D3 (relegado con «versión anterior»); B.S7 (ocultar) | **parcial** · `c802b7e78` (ficha, rank 990); ocultar en Fase 2 |
| L-23 | PHAT heredado: SPEED mal ubicado y weekdays distintos de la fuente | 02 §2.5, l.662 | C.P2 y D3; B.S7 (ocultar) | **parcial** · `c802b7e78` (ficha, rank 991); ocultar en Fase 2 |
| L-24 | kpkn-ppl-6: RIR 0 en compuestos, descarga sin recorte y sin core | 02 §2.5, l.668 | B.S6 (PPL: `secondRir`, descargas) | **parcial** · `8a3adefd4`: fin del RIR 0 y descargas con recorte en PPL; no se acredita añadir core para cerrar todo el hallazgo |
| L-25 | kpkn-rp-style: MEV a MRV no implementado y «Torso B» es de empuje | 02 §2.5, l.673 | B.S6 (RP: sin RIR 0, descargas); B.S7 (rampa y «Torso B» real) | **parcial** · `8a3adefd4`: RP sin RIR 0 y descarga real; `28a8a6f5e` describe que no aumenta series. Rampa y Torso B real en Fase 2 |
| L-26 | kpkn-rts-style: `RepTargetDrivenTm` sin AMRAP y RPE solo en sentadilla | 02 §2.5, l.679 | C.P2 (descripción honesta, D3); B.S7 (RTS por top sets) | **parcial** · RTS pasa a None en `8a3adefd4` y texto honesto en `28a8a6f5e`; top sets y esfuerzo del resto de lifts en Fase 2 |
| L-27 | power-16-4 y power-20-5: técnicas redundantes y parches | 02 §2.5, l.684 | D.M2 (alta); B.S1–B.S2 (regla C9); B.S7 | **parcial** · alta M2 `d68e91872`, SQ_PAUSED en power-20-5 `8a3adefd4`; parches C9 corregidos en `f56cd6e76`; aceptación global/QA pendientes |
| L-28 | power-12-3: principiante con accesorios avanzados | 02 §2.5, l.693 | B.S7 | **Fase 2** |
| L-29 | powerbuild16/body16–20: bloques similares y sin descarga real | 02 §2.5, l.697 | B.S6 descarga; B.S7 diferenciación | **parcial** · descargas reales ya en `f56cd6e76`: powerbuild16w4/8/12, body16w4/8/12/16, body20w6/12/18; KpknAdvancedProgramRecipes.kt:220/281/348 y FidelityTest:1146–1178 fijanDELOAD/recorte/effort. Diferenciación restante de bloques sigue Fase2; Base global/UI no cerrada |
| L-30 | Autorados: `isCompetitionLift` sobrecargado; PHAT sin progresión | 02 §2.5, l.702 | ninguno (PHAT sin progresión es decisión de producto, DEV-r2-04) | **sin paso** |
| L-31 | Coan: SPEED antes del pesado | 02 §2.5, l.708 | ninguno | **sin paso** |
| L-32 | Validadores/gates/%TM/enlaces/exenciones/días/goalRank | 02 §2.5, l.710 | B.S1–B.S2/B.S6 | **parcial** · `4aa1bbb10` H11/H11b/glob; `f56cd6e76` HARD C1–C9/C10SOFT, PercentBasis compartida enBLOCK/H5a/H8/H9/W3/W4 y W2 porPRIMARY sin desactivarlo porliftSlots vacío en propios. SessionCompositionPolicy.kt:825–849 confirma límite propio; escapes genéricos PL/SBD/especialización/MEV0legacy y S6informativo mantienen su alcance, no se proclama cierre total. Fuente/filtros actuales separados de Base global/UI |
| L-33 | Series sueltas de 1 (`SHRUG`, `PUSHDOWN`) en unas 15 recetas | 02 §2.5, l.720 | B.S6 (series sueltas) | **hecho** · `8a3adefd4`: SHRUG 2×8 y PUSHDOWN 2×12–15 |
| L-34 | `PersonalizedPlanCatalog`: `friendlyMethods` con claves muertas y plantillas siempre ADVANCED | 02 §2.5, l.724 | C.P1, C.P2 | **hecho** · `c802b7e78` |
| L-35 | Smolov Jr con los 4 días llamados «Sesión»; `CatalogIds` con constantes sin uso | 02 §2.5, l.728 | B.S6 (etiquetas Jr S1 a S4) | **parcial** · etiquetas Smolov Jr S1–S4 y uso de las cinco altas `8a3adefd4`; no se afirma eliminación de todas las constantes sin uso |

### 3.3 Prefijo C- (catálogo de ejercicios que usan las recetas, 12 hallazgos)

Origen: [02](02-auditoria-logica-recetas-fijas.md) §3. Las altas M1–M5 de la Fase 1 retiran los parches C-01 a C-04 y C-10 (11 §7.1).

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| C-01 | `CLOSE_GRIP` sobre `BP` (5/3/1, nSuns) | 02 §3, l.740 | D.M1 (`close_grip_bench_press__barbell`) | **hecho** · alta `d68e91872`, soportes `d604ef579` y BP_CLOSE_GRIP en nSuns/Wendler sin parche CLOSE_GRIP `8a3adefd4` |
| C-02 | `CLOSE_GRIP` sobre `LAT` (PHAT `lat-close`) | 02 §3, l.745 | D.M4 (`close_grip_lat_pulldown__cable`) | **hecho** · alta `d68e91872`, llaves `d604ef579` y LAT_CLOSE_GRIP en PHAT con binding actualizado `8a3adefd4` |
| C-03 | `PAUSE_2S` sobre sentadilla (Calgary, power-20-5) | 02 §3, l.749 | D.M2 (`paused_back_squat__barbell`) | **hecho** · alta `d68e91872`, rack `d604ef579` y SQ_PAUSED en Calgary/power-20-5 sin PAUSE_2S `8a3adefd4` |
| C-04 | `TO_KNEES` sobre `DL` (Sheiko) | 02 §3, l.753 | D.M3 (`deadlift_to_knees__barbell`) | **hecho** · alta `d68e91872` y DL_TO_KNEES en Sheiko sin TO_KNEES `8a3adefd4` |
| C-05 | `DEFICIT` redundante o parche | 02 §3, l.755 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-06 | `BOX` y `PIN` redundantes | 02 §3, l.760 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-07 | `CHAINS_BANDS` sobre `BP_FLOOR` | 02 §3, l.762 | B.S1–B.S2 (regla C9); B.S7 | **Fase 2** |
| C-08 | `SPEED` sobre configuraciones normales (informativo) | 02 §3, l.764 | ninguno | **sin paso** |
| C-09 | Clasificación de `UPRIGHT_ROW` como tirón vertical | 02 §3, l.770 | ninguno | **sin paso** |
| C-10 | «Curl inclinado» del PHUL resuelto con curl sentado en banco plano | 02 §3, l.772 | D.M5 (`incline_biceps_curl__dumbbells`) | **hecho** · alta `d68e91872`, soportes `d604ef579` y CURL_INCLINE/binding de PHUL `8a3adefd4` |
| C-11 | `ROW_DB` marcado unilateral con lateralidad BILATERAL | 02 §3, l.776 | ninguno | **sin paso** |
| C-12 | Ids de slot confusos (`pull`, `lat`, `bp`, `tate`) | 02 §3, l.778 | ninguno | **sin paso** |

### 3.4 Prefijo P- (informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| P-01 | «Fuerza» y «Fuerza y músculo» sin material: el plan los cita juntos con P-02 y no dice cuál es cuál | plan 00 §3 D5, l.37; contexto en §1, l.17 | A.C1 (`SwitchGoal`); A.C4 y C.P11 (presentador de rechazos) | **hecho** · dominio `e275d0bbc` y botones con reparación evaluada en UI `76950161e`; QA visual C.P14 pendiente |
| P-02 | Ídem. Además 11 §4 (l.344) usa P-02 para el texto de TIME_BUDGET de `SimpleCyclePersonalizer` (:1216-1219), que imprime el tiempo del usuario | plan 00 §3 D5, l.37; 11 §4 | A.C2 (`requiredMinutes` exacto y mensaje nuevo); A.C1, A.C4 | **hecho** · mínimo exacto/cardio intacto `e275d0bbc` y botón Ajustar a N min `76950161e` |

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
| B-12 | «Fuerza y músculo» sin resistencia: rechazo sin botón | 11 §4, l.329 | A.C4 y C.P11 (tabla de rechazos) | **hecho** · `e275d0bbc` y `76950161e`: propio Fuerza y músculo sin pesas con reparación a Músculo; copy final WIZFIX en `54668bcb8`; QA pendiente |

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
| R-03 | «VER PROPUESTA» acepta la propuesta en vez de solo navegar (error visible, 1 h) | plan 00 §6, l.142 | B.S3–B.S5 | **hecho** · B.S5 `2d411c58f` («VER PROPUESTA» solo navega a la pestaña Semana y ya no llama a `acceptAutoregulation()`; lo fija `ProgramDetailViewModelTest.verPropuesta_only_navigates_to_the_week_tab_and_never_accepts_the_proposal`) |
| R-04 | Guardar TM: conservar variantes y TMs ajustados y re-materializar semanas pendientes | plan 00 §6, l.141 | B.S3–B.S5 | **hecho** · B.S5 `2d411c58f` (`TrainingMaxMerge` conserva las variantes y los TM ajustados de los levantamientos cuyo 1RM no cambió; «Guardar TM» recalcula en la misma mutación las semanas pendientes con la evidencia real y avisa cuántas recalculó y cuántas dejó intactas) |
| R-11 | AMRAP: ver plan 00 §6 (viñeta AMRAP) | plan 00 §6, l.140 | B.S3–B.S5 | **hecho** (motor) · B.S4 `b18597085` (mismo cambio que R-02: el plan no separa los tres); los datos de las recetas siguen en B.S6 |
| R-13 | Aviso «no aplicable» al aceptar una propuesta | plan 00 §7, l.200 | B.S13 | **Fase 2** |
| R-15 | Selector de modo en Detalle con confirmación para AUTO | plan 00 §7, l.200 | B.S13 | **Fase 2** |
| R-16 | AUGE: umbral de fatiga normalizado | plan 00 §7, l.199 | B.S12 | **Fase 2** |
| R-17 | AMRAP: ver plan 00 §6 (viñeta AMRAP) | plan 00 §6, l.140 | B.S3–B.S5 | **hecho** (motor) · B.S4 `b18597085` (mismo cambio que R-02: el plan no separa los tres); los datos de las recetas siguen en B.S6 |
| R-19 | Test de 1RM: también actualiza `powerliftingProfile` (l.141); tarjeta «Programa terminado, repetir con TM actualizado» (l.200) | plan 00 §6, l.141; §7, l.200 | B.S3–B.S5; B.S13 | **parcial** · B.S5 `2d411c58f` (el test de 1RM registrado actualiza `powerliftingProfile` con `TrainingMaxMerge` y recalcula las semanas pendientes); la tarjeta «Programa terminado, repetir con TM actualizado» queda en Fase 2 (B.S13) |
| R-23 | `rematerializeWeek` no resuelve `startDay` ni `trainingDays` como `materialize` (rota los días) | plan 00 §6, l.141 | B.S3–B.S5 | **hecho** · B.S3 `5312decc2` (`rematerializeWeek` y `materialize` comparten `resolveWeekSchedule`; prueba con `startDay` 3) |
| R-24 | RIR derivado de Epley para porcentajes sin TM | plan 00 §7, l.201 | B.S7 | **Fase 2** |

### 3.9 Prefijo Q- (catálogo v2; informe no archivado)

| id | Título corto | Dónde está | Paso | Estado |
|---|---|---|---|---|
| Q-02 | Llaves para las 6 máquinas de las tablas propias | plan 00 §8, l.207 | plan 00 §8 | **Fase 3** |
| Q-07 | Etiquetas donkey_machine/leg_press_machine/hands_elevated | plan00:191; diseño11:464 | paquete D | **parcial** · `ede1123d4`, tres casos reales Labels1/1 en50; QA/aceptación Base pendientes |
| Q-08 | Tres notas obsoletas de CATALOG_GAP_NOTES | plan00:191; diseño11:465 | paquete D | **parcial** · `54668bcb8`; fuentes y guardas entregadas, aceptación Base/QA pendientes |

### 3.10 Prefijo G- (brechas del catálogo)

Origen: [11](11-diseno-editorial-y-catalogo.md) §7.1 (l.417). Las altas M1–M4 existen en el catálogo y se consumen en las recetas desde `8a3adefd4` (sección 4). M5 se corresponde con C-10, no con un G-.

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
| F0.0 | Entorno de edición | proceso, sin commit | La memoria de la sesión original registra Write/Edit funcionando al iniciar la ola 1. En esta continuación Sonnet no está disponible por cuota; el dueño autorizó subagentes Codex. No se toma esa autorización como aprobación de cambios de producto fuera del plan. |
| F0.1 | Commit del árbol consolidado | **hecho** · `5c281a02c` | Con `466ef4bd4` (herramientas y guías), `6914fb6f8` (iOS) y `bcda7d1a5` (backend). `5c281a02c` toca 465 archivos y su mensaje declara 4 270 tests JVM en verde. Sin push. |
| F0.2 | Candado de Gradle y coordinación | proceso; wrapper corregido en `416412542` | El script [run-gradle-locked.ps1](../../../.opencode/scripts/run-gradle-locked.ps1) existe en el árbol (commit `97c464ad0`, de nutrición); se usa con `-Owner "curaduria-programas"`. La reparación de run-gradle.ps1 conserva exit real y limita el timeout al árbol propio; fixture de esta continuación devuelve 124 sin terminar otros Gradle. |
| F0.3 | Archivar la evidencia | **hecho** · `d41ba0524` (plan 00, 01, 02 y 11), `6152603dc` (índice inicial) y consolidación actual en este commit documental | Solo hay tres informes archivables; los demás no existen como archivo (sección 2). |
| F0.4 | Baseline de la suite completa Base | sin commit | El baseline que cita el plan es la corrida X-full-2 del cierre de la consolidación: `testBaseDebugUnitTest`, 4 270 tests, 0 fallos, 0 errores, 2 omitidos, 615 clases (`artifacts/consolidation-20261001/test-evidence/X-full-2/summary.json`, sin versionar). La carpeta `artifacts/programs-curation/baseline/` que pedía el plan no existe. |

### Fase 1, paquete A: cobertura y adaptación (plan 00 §6)

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| A1 | `CoverageFixtures` compartidos (E0 a E18) y T006 con `NativePlanFailureMapper.typedFailure` | **hecho** · `5849c32f0` | Q1, Q2, Q2 calibrada, positivos y Q3 idénticos: Q2 con 2 304 filas, 1 224 viables, APPARATUS_ABSENT 360, TIME_BUDGET 504 y PROFILE_MISMATCH 216; positivos 58/58. |
| A2 | `PlanCoverageContractTest` con ratchet de violaciones | **hecho** · `5849c32f0` | Baseline `full` del 2026-10-03: 25 650 filas, 13 590 Ready y 3 537 violaciones (COMPOSITION 195, DISHONEST_ABSENT 270, TIME_BUDGET_INEXACT 1 578, NO_REPAIR 30, MATERIAL_UNUSED 1 464). La suite queda en verde y cada paso baja los techos hasta 0. |
| A3 | Filas `T020_*` de paridad con el ViewModel | **hecho** · `76950161e` | Filas T020 a–f con VM y motor reales; matriz 17 → 24. A.E2 añade T021 y deja 25 en `a94539866`. Filtros actuales pasan en48/50; aceptación Base/QA pendientes. |
| A.B1, A.B2, A.B3, A.B7 | Plomería `missingRequirements`; gates con evidencia (B-03); `hasExplicitMachinePresence` y `pullAvailable` resuelto; `requirementEvidence` de ANY a ALL | **hecho** · `4d63e9dd1` | Contrato `full` 3 537 → 3 162 (DISHONEST_ABSENT 270 → 0, NO_REPAIR 30 → 0, NOT_HONEST 195 → 120). `NativeMaterialEvidenceTest` (22) nuevo; matriz T-019 completa verde (43 filas). Registra DEC-w2-01 y DEV-r2-06. |
| A.B4, A.B5 | Soportes de `supportRequirementsFor` (N-05); tablas de candidatos con Smith, kettlebell y banda | **hecho** · `d604ef579` | Contrato `full` 3 162 → 1 704 (MATERIAL_UNUSED 1 464 → 0). `NativeCandidateTableSlotsTest` (10) y `CatalogIdsExistInCatalogTest` (3) nuevos. Deja pendiente `bench_press__kettlebell` (decisión del dueño, sección 7). Registra DEC-w2-04 parte 1. |
| A.B6 | Evidencia unificada VM/materializador autor/adaptación y UNKNOWN con llave real | **parcial** · fuente commiteada `54668bcb8` | Consumidores y copy compartidos; filtros48/50 verdes. Gate47 histórico fallido conservado. Aceptación Base y QA C.P14 pendientes |
| A.C1, A.C2, A.C4 (parte pura) | `PlanRepair` y `PlanRepairAdvisor`; `requiredMinutes` exacto; `PlanRejectionPresenter` | **hecho** · `e275d0bbc`; UI `76950161e` | Asesor, mínimo exacto y presentador consumidos por el producto desde `76950161e`. Contrato full 1 704 → 120; suite verde con ratchet no equivale a cero violaciones. |
| A.C3 y la parte de UI de A.C4 | `applyRepair` en el ViewModel; sustituir `CandidateIncompatibility` y el aviso de selección caída por el presentador | **hecho** · `76950161e` | applyRepair/applyRepairs y repairs del propio; CandidateIncompatibility, selección caída y aviso del propio usan el presentador. SetupWizardRepairTest (26 al entrar). Correcciones WIZFIX entregadas en `54668bcb8`; QA pública pendiente. |
| A.D1 (con C.P3) | Ranking editorial del planner y prefiltro de capacidades de Atleta | **hecho** · `2e3123245` | Q1 de T006 422 496 → 194 400 candidatos publicados; Q2 idéntico. Registra DEC-w2-06. |
| A.D2, A.D3, A.D4 | Lista y selección caída (B-01); caché y catálogo (B-05, B-06); pase corporal solo por material (B-07) | **hecho** · `0b50bda5e` | `SetupWizardCandidateGateTest` (20) nuevo; `PlanCandidateSessionCacheTest` 5 → 9; matriz 17/17 idéntica. Registra DEC-w2-05. |
| A.E1 | Prioridades en los 4 planes propios (D6) | **hecho** · `4f9845f81` | `OwnPlanPrioritiesAndSplitTest` (11) nuevo; `SetupWizardFullJourneyTest` vuelve a verde sin tocar su oráculo. Registra DEC-w2-08. |
| A.E2 | Split en los 4 planes propios (D6) | **hecho** · `a94539866` | NativeProfileSplitWitness, SPLIT reparable, días nombrados y selectedSplitId tras materializar; 36 testigos y barrido de 1 140 generaciones según el commit. DEC-w2-04 parte 2 registrada; opciones sin testigo pendientes (§7). |
| A, documentación | DEC-w2-01–08/DEV-r2-06 y REVISION native-cycle-2 | **entregado en este commit** · fuente `54668bcb8` | Revisión y seis ratchets en cero commiteados; JSONCompat conserva el payload legacy native-cycle-1. FULL definitivo 25.650/0 medido; índice F0.3 consolidado, aceptación Base/QA abierta. |

### Fase 1, paquete B: recetas y progresión

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| B.S1 | `PercentResolver` y `PercentBasis`; H11 sobre kg resueltos; H11b (Epley) no exentable; exenciones con glob anclado; Texas 3d y Madcow a TM 0,87 (D7); Madcow a `PERCENT_TM`; Smolov semana 9 a 2 repeticiones | **hecho** · `4aa1bbb10` | 26 clases por filtro, 0 fallos. Inventario permanente `every_published_recipe_is_free_of_h11_and_h11b`: 0 hallazgos en 40 recetas (antes del cambio de datos, 81). Con 1RM 200: Texas viernes 174 kg y Madcow lunes de la semana 4 174 kg. Pendientes anotados en el commit para C.P8, B.S2 y B.S12. |
| B.S2 | RecipeContractPolicy C1–C10, SOFT→HARD | **parcial** · SOFT `2f5e0bd69`, HARD `f56cd6e76` | C1–C9 HARD/C10 SOFT entregados; filtros48/50 verdes. Inventario40 recetas/100 crudos/18 tras exenciones, soloC10SOFT/C7=0. Gate47 fallido histórico; aceptación Base/QA pendientes |
| B.S3 | Motor de progresión de autor, parte 1: `CycleIncrement` por ciclo y por bloque, `WeeklyKg` en la materialización, R-23 y registro de consumidores | **hecho** · `5312decc2` | `AuthoredProgressionEngineTest` (15 al entrar), `ProgressionConsumerCoverageTest` (6) y `ProgramProgressCycleCloseTest` 6 → 26; 590 tests, 0 fallos (el mensaje del commit dice «18 → 26»; el conteo de `@Test` da 6 antes). Con TM 180, 108 y 198 el 5/3/1 BBB cierra el ciclo con 185, 110,5 y 203; Smolov Jr con 1RM 200 sube de 140 a 145 y 150 kg. Pendientes anotados en el commit: H13, H14 y el resto de B.S5 (cerrados en `2d411c58f`). |
| B.S4 | Progresión por rendimiento como propuesta de TM: AMRAP en kilos, `TopSetPr`, `RepMaxAutoregulated` y «un lift con AMRAP corto no sube» | **hecho** · `b18597085` | `TopSetProgressionTest` (17) nuevo; `ProgramAutoregulationEngineTest` 7 → 24 (el mensaje del commit dice «6 → 23»), `ProgramAutoregulationResolutionTest` 5 → 11 y `ProgramProgressCycleCloseTest` 26 → 31; inventario de B.S2 457 → 452. Pendientes anotados en el commit: nSuns, Lilliebridge a `None`, el chin de Texas sin `amrap` y kpkn-rts-style sin AMRAP (B.S6). |
| B.S5 | Guardar TM (R-04), test de 1RM (R-19), «VER PROPUESTA» sin aceptar (R-03), tarjeta «Nuevo ciclo» y H13 | **hecho** · `2d411c58f` | `TrainingMaxMergeTest` (13) nuevo; `ProgramDetailViewModelTest` 58 → 74, `ProgramProgressCycleCloseTest` 31 → 37, `PlanMaterializerTest` 18 → 20 y `NativeProgressionCardModelTest` 13 → 15 (los conteos del mensaje coinciden con los de `@Test`); corrida del commit: 406 casos, 0 fallos. «Guardar TM» fusiona el perfil (conserva variantes y TM ajustados) y recalcula las semanas pendientes con la evidencia real; el test de 1RM registrado actualiza el perfil; «VER PROPUESTA» solo navega; `PlanMaterializer.materialize` limpia las marcas de autor (H13); la tarjeta titula «Nuevo ciclo» y «Nuevo bloque» (H14). R-23 ya lo cubrió B.S3. Pendientes anotados en el commit (B.S13, Fase 2): R-13, R-15 y la tarjeta «Repetir con TM actualizado»; las marcas `native-progression-c<N>` siguen sin limpiarse (sección 7). Registra DEC-w3-03, junto con B.S3 y B.S4. |
| B.S6 | Correcciones ALTA receta a receta | **parcial** · `8a3adefd4`/`27e2d4938`/`3d7c67474`; cierre `f56cd6e76` | Partes2b/2c, Juggernaut/descargas/enlaces/exenciones/%1RM/HARD y metadata editorial entregadas juntas. Filtros48/50 verdes; aceptación Base/QA pendientes. Evidencia histórica de partes anteriores se conserva en commits e informes |
| B, documentación | DEC-w3-01, DEC-w3-02, DEC-w3-03 y DEC-w3-07 | **entregado en este commit** | Decisiones y evidencia actual registradas, con checkpoints históricos explícitos. Recetas+metadatos `f56cd6e76` y consumidor `7c0c27e5b` entregados; 143 tests actuales sin fallos. Base global y 28 casos públicos de QA abiertos. |

### Fase 1, paquete C: editorial y UX

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| C.P1, C.P2 | PlanEditorial/Labels/Table55/findForProgram/REPEATING_CYCLE | **parcial** · `c802b7e78` y cierre `f56cd6e76`; duración `54668bcb8` | Mapa17 literales por lado/espejo/semántica con Claims2/2 en48/50. Ciclo repetible implementado/commiteado; UI/aceptación global pendientes (plan165; diseño11:157–164/494) |
| C.P2b | Ocultar los 5 nativos históricos (D2), referencias editoriales y re-baseline | **hecho** · `d91a0ae2e` | Q1 de T006 pasa de 497 664 a 422 496 candidatos publicados; Q2 idéntico (2 304 filas, 1 224 viables); T-019 grupo F con el testigo `native:muscle-foundation-v2` (piso de 28 min). Registra DEC-w2-07. En este paso solo se corrió el grupo F de T-019; el resto de la matriz no se corrió. |
| C.P3 (con A.D1) | Planner por ficha editorial y `PlanGoalMatcher` compartido con la biblioteca | **hecho** · `2e3123245` | `SetupTrainingPlannerRankingTest` (14) y `PlanGoalMatcherTest` (14) nuevos. Por lectura de código, «Fuerza» en la biblioteca pasa de 31 a 29 entradas (DEC-w2-06). |
| C.P4 | PlanCatalogEditorialContractTest y tests asociados | **parcial** · `28a8a6f5e`/`f56cd6e76` | Editorial24/24 y Claims2/2 en48/50; Q-07 Labels1/1 en50, commiteado aparte. Full1 rechazada y full2–4 incompletas no acreditan aceptación Base. QA pendiente |
| C.P5 | Biblioteca y wizard: `displayName`, procedencia y resumen en las tarjetas; «Ver cómo funciona» en el paso PLAN; conteos «N planes revisados» | **hecho** · `1bbe7a6fe` | `LibraryCardModelTest` (17), `SetupWizardReadyWeekTest` (5) y `WizardSplitLabelTest` (7) nuevos; `PlanInfoModelTest` 42 → 52 y `WizardPluralCopyTest` 13 → 15 (los conteos del mensaje coinciden con los de `@Test`); corrida `onboarding.*` + `programs.*` + `data.programs.*` del commit: 472 tests en 47 clases, 0 fallos propios y 1 rojo ajeno (`PersonalizedPlanCatalogTest.everyNativeFamilyBuildsExecutableCanonicalSessions`, por el lote 4 del catálogo sin commit; sección 7). La biblioteca lee la ficha editorial, se ordena por `rank` y su tap abre «Cómo funciona» (desaparece el `AlertDialog`); las tarjetas del wizard llevan «Ver cómo funciona» con la primera semana real de la evaluación en caché; el conteo dice «12 planes revisados · 7 encajan con tus respuestas»; la revisión muestra el nombre del reparto y no su id. Pendientes históricos C.P6 cerrados en `76950161e`: preselección desde Home/MainActivity, onOpenConcept y readyWeek de revisión. El filtro de Ciclo repetible ya fue implementado por A y pasa el gate50; UI todavía pendiente. Otras reservas del punto de control: 22 tarjetas muestran «Plan propio de KPKN.» bajo «Plan KPKN»; el copy del conteo con el pase corporal (DEC-w2-05); la verificación visual (C.P14). |
| C.P6 | Nombre del programa creado, modo y preselección (`preselectedPlanId`) | **hecho** · `76950161e` | ProgramNaming, nombre/modo por ficha, planId/preselectedPlanId en ruta y enlace a Conceptos clave. ProgramNamingTest (9). WIZFIX `54668bcb8`: TRAINING_ONLY después del alta es fix dentro de C.P6; creación en blanco es excepción limitada registrada en DEC-wiz-20261004, no aceptación de receta incompatible. PostDischargeRoutingTest y LibraryReachabilityTest 6/6 cada uno en gate47; QA de conservación/preselección pendiente. Nutrición avisada antes del commit original. |
| C.P7 | Procedencia y nombre del plan en el detalle del programa y en la revisión del wizard, con «Ver cómo funciona» | **hecho** · `aa3ccb40f` | `ProgramPlanDisplaySummaryTest` (27) nuevo; `PlanInfoModelTest`, `ProgramDetailViewModelTest` y `SetupWizardFullJourneyTest` verdes. Fuera de su alcance, anotado en el commit: el id del split en «Reparto semanal» (lo cerró C.P5 `1bbe7a6fe`: la revisión muestra el nombre del reparto), el patrón emoji + nombre en el editor de sesión, el vocabulario viejo del foco en `ProgramHeroWidgets` y «1 días/semana». |
| C.P8, C.P9 | `PlanInfoModel` (puro) y `PlanInfoSheet` («Cómo funciona»); `ProtocolDetailSheet` delega en ella | **hecho** · `e5e428e54` | `PlanInfoModelTest` (42 al entrar). La hoja se abre desde ProtocolDetailSheet, detalle/revisión (`aa3ccb40f`), biblioteca/tarjetas (`1bbe7a6fe`). Desde `76950161e` recibe enlaces a Conceptos clave y la revisión recibe la semana real. |
| C.P10 | Glosario en dos niveles: `PlanGlossary` y cuatro conceptos en Conceptos clave | **hecho** · `75c15deb0` | `PlanGlossaryTest` (11) nuevo; el índice de Conceptos clave pasa de 27 a 31 conceptos (`ConceptosClaveIndexTest`). |
| C.P11 | Rechazos: tabla de textos y botones en la UI con el presentador único | **hecho** · dominio `e275d0bbc`, cableado `76950161e` | Presentador único en incompatibilidad, selección caída y aviso del propio; reparación principal y acción secundaria. QA de botones en C.P14 pendiente. |
| C.P12, C.P13 | Atribución con autores completos, planes KPKN sin URL ni «No afiliado a KPKN Fit», y copy menor | **hecho** · `328752b13` | 27 clases, 277 tests, 0 fallos (mensaje del commit). `ProtocolAttributionTest` +5 y `TrainingMaxWizardCopyTest` (3) nuevo. Cierra E-11 y E-32; E-26 y E-33 quedan parciales. |
| C.P14 | QA manual Base | **pendiente** · arranque recuperado PASS; 28 casos públicos NOT_RUN | APK09AE instalado, MainActivity/Welcome sin modal y COLD de 4.883 ms. FULL temporal aparcado en Experiencia antes de configuración/activación; backup parcial preservado y ORIGINAL restaurado:207 archivos/WAL equivalentes. QEMU cerrado y prioridad Gradle/ADB5554 cedida a alimentos. Otra suite Base/JBR21 sigue sin autorización. |

### Fase 1, paquete D: catálogo de ejercicios

| Paso | Qué es | Estado · commit | Notas |
|---|---|---|---|
| D.M1 a D.M5 | Cinco altas SPECIALTY por el pipeline v2: `close_grip_bench_press__barbell`, `paused_back_squat__barbell`, `deadlift_to_knees__barbell`, `close_grip_lat_pulldown__cable` e `incline_biceps_curl__dumbbells` | **hecho** · `d68e91872` | Catálogo 96/206/527 con `catalogRevision` intacta (`v2-approved-2026-09-29-a`); SHA canónico `b2a652bb4f654f32e2925593858b3110e6c06a95637e7a2a4bad19d20cc6734a`; 63 definiciones CURATED. Verificación: 187 pruebas de `scripts/tests`, 10 de backend y 72 de Gradle por filtro, 0 fallos. |
| D, cableado Kotlin | Soportes/remaps/bindings/recetas | **parcial** · `d604ef579`/`8a3adefd4`/`f56cd6e76`/`54668bcb8` | Configuraciones curadas y remaps legacy preservados; Q-07/Q-08 entregados, filtros verdes. Aceptación Base/QA pendientes |
| D, Q-07 y Q-08 | Etiquetas y tres notas obsoletas | **parcial** · Q-07 `ede1123d4`, Q-08 `54668bcb8` | Tres ramas/casos de C y tres notas calf_raise/reverse_lunge/push_up retiradas por A; sin altas/dosis nuevas. Labels1/1 en50. Fuente commiteada, aceptación Base/QA pendientes |
| D, `CatalogIdsExistInCatalogTest` | Regla «nunca inventar ids» como test | **hecho** · `d604ef579` | 3 pruebas por reflexión sobre `CatalogIds`, `NativeCandidateTable` y `AuthoredExerciseBindings.all` contra los dos assets del catálogo. |

### Fases 2 y 3, y cierre

| Paso | Qué es | Estado |
|---|---|---|
| Fase 2 (plan 00 §7) | B.S9–B.S10, B.S11, B.S12, B.S13, B.S7, A.F1–A.F3 y las altas D1–D4 del catálogo con el resto de C/D | pendiente |
| Fase 3 (plan 00 §8) | URLs de terceros (E-30), niveles (E-31), etiquetas de día en español (E-36), paridad iOS, Q-02, G-07 y G-08 | pendiente |
| Documentación final (plan 00 §10.6) | Este README; DEV/DEC; estado del wizard y mapa de arquitectura | **entregado en este commit** | Índice F0.3 y matriz actual consolidados, permisos humanos y dos oráculos Madcow registrados. Fuente, pruebas, APK, instalación, arranque, QA parcial y restore se distinguen; la documentación no cierra Fase 1: quedan Base global y 28 casos públicos. |

## 5. Decisiones registradas

### 5.1 Decisiones del plan (plan 00 §3, l.29)

D1 a D4 las decidió el dueño el 2026-10-03; D5 a D7 se aplican con la recomendación indicada salvo que el dueño diga otra cosa.

| # | Decisión | Estado |
|---|---|---|
| D1 | Primera entrega «publicable» = Fase 1 (cobertura, editorial, recetas y progresión, 5 altas de catálogo y ocultar históricos); Fases 2 y 3 después | Decidida |
| D2 | Ocultar ya (`listed = false`) `full-body`, `gym-muscle`, `one-day`, `return-training` y `home-training`; mantener `machine-muscle` y `bodyweight` relegados; `strength-cardio` solo legado | Decidida; aplicada en `d91a0ae2e` (DEC-w2-07) |
| D3 | Recetas cuya descripción miente: Fase 1 con descripción honesta, PPL y RP sin RIR 0, PHUL y PHAT heredados relegados con «Versión anterior»; Fase 2 con las correcciones de fondo | Decidida; textos c802b7e78/28a8a6f5e y PPL/RP sin RIR 0 en 8a3adefd4; correcciones de fondo en Fase 2 |
| D4 | Onda de RIR intra-ciclo en los 4 propios (principiante `3,3,3,2,2`; intermedio y avanzado `3,2,2,1,1`; descarga RIR 4), en Fase 2 | Decidida; se registrará como DEV-r2-07, porque DEV-r2-06 quedó para las máquinas por categoría (nota de numeración en `WIZARD_PLAN_DEVIATIONS.md`) |
| D5 | Fuerza y Fuerza y músculo sin material: redirección con botón de un toque, sin variantes de fuerza relativa | Por defecto del plan; dominio e275d0bbc y UI 76950161e; QA pendiente |
| D6 | Prioridades y split en los 4 propios: prioridades ordenan empates, split por tabla con rechazo SPLIT reparable | Por defecto del plan; prioridades 4f9845f81 y reparto a94539866; D6 implementada, opciones sin testigo pendientes de revisión |
| D7 | Texas y Madcow con `trainingMaxPercent = 0,87` y nota visible en el wizard de marcas | Por defecto del plan; Texas 3d/Madcow 4aa1bbb10, Texas 4d 8a3adefd4 y nota de marcas 328752b13 |

### 5.2 Decisiones previas que el plan respeta (plan 00 §2, l.23)

Glúteos hasta 17,5 como banda blanda solo en propios (DEV-r2-02); puente una vez por semana (DEV-r2-01); el cardio del Atleta nunca se recorta (DEC-w1-01, DEV-r2-03); PHUL con doble progresión y PHAT sin ella (DEV-r2-04); al terminar un bloque el programa continúa con un solo aviso (DEV-r2-05); los planes de autor no se filtran por nivel (DEC-w1-04); un slot SPEED sin material es NotViable (DEC-w1-05); las adaptaciones van al final (DEC-w1-06).

### 5.3 Registradas en `docs/WIZARD_PLAN_DEVIATIONS.md`

Las líneas son las de [WIZARD_PLAN_DEVIATIONS.md](../../WIZARD_PLAN_DEVIATIONS.md).

| Id | Qué decide | Línea |
|---|---|---|
| DEV-r2-01 | Variantes corporales sin tirón: el MRV de glúteos (16) prevalece sobre el calendario literal. Aceptada el 2026-10-02 | 48 |
| DEV-r2-02 | Tolerancia blanda de glúteos hasta 17,5 series por semana, como último recurso y solo en planes propios. Aprobada e implementada | 162 |
| DEV-r2-03 | Progresión de cardio (R13) diferida; R14 absorbida en R13 | 188 |
| DEV-r2-04 | Progresión de carga de los originales PHUL y PHAT y de sus adaptaciones (R16): parcial, PHUL sí y PHAT no por ahora | 202 |
| DEV-r2-05 | Fin de bloque: el programa continúa solo con un aviso, sin pantalla de oferta (H-CICLO) | 214 |
| DEC-w1-01 | El fitter no recorta cardio ni descansos: a 45 min el cardio por defecto de 15 min da TIME_BUDGET | 230 |
| DEC-w1-02 | Músculo corporal de 3 días: el fitter retira los accesorios «de la tabla» y el mínimo real es 21 min, no 25 | 248 |
| DEC-w1-03 | Matriz grupo A: piso independiente por fila, suficiencia con el plan propio y metadatos de composición en el arnés | 258 |
| DEC-w1-04 | Los planes de autor no se filtran por nivel (wizvm) | 267 |
| DEC-w1-05 | Un slot SPEED sin material es NotViable, no se sustituye (wizvm) | 273 |
| DEC-w1-06 | Las adaptaciones van tras los propios y los originales (wizvm) | 279 |
| DEC-w1-07 | NO ENTREGADO: progresión de cardio de r2 §12.4 (R13/R14) | 285 |
| DEC-w1-08 | NO ENTREGADO: cláusula de adaptaciones de originales de r2 §12.4 (R16) | 295 |
| DEC-w2-07 | Los cinco nativos históricos dejan de listarse (D2, 2026-10-03) | 307 |
| DEC-w2-01 | Contrato de cobertura centrado en el plan propio, con reparación de un toque (2026-10-03) | 345 |
| DEV-r2-06 | Máquinas por categoría en los planes propios cuando solo se confirmaron soportes (A.B3, 2026-10-03) | 388 |
| DEC-w2-06 | Ranking editorial del planner y matcher de objetivo compartido (A.D1 + C.P3, 2026-10-03) | 408 |
| DEC-w2-08 | La bolsa de prioridades de orden se aplica y se persiste en los planes propios (A.E1, D6, 2026-10-03) | 458 |
| DEC-w2-04 (parte 1) | Soportes reales de sentadillas y press, y tablas de candidatos para kettlebell, Smith y banda (A.B4/B5, 2026-10-03) | 503 |
| DEC-w2-02 | Redirección honesta con un toque, sin «fuerza relativa» (A.C1 y A.C4 parte pura, 2026-10-03) | 596 |
| DEC-w2-03 | `requiredMinutes` exacto con el cardio intacto (A.C2, 2026-10-03) | 652 |
| DEC-w2-05 | El pase a peso corporal solo sigue a rechazos de material y publica siempre los rechazos del pase pedido (A.D4, B-07, 2026-10-03) | 697 |
| DEC-w3-03 | La progresión del método se aplica siempre y la de rendimiento viaja como propuesta de TM (B.S3, B.S4 y B.S5, 2026-10-03/04) | 812 |
| DEC-w3-07 | Retirada de la propuesta de variante técnica (B.S6 parte 2a, 2026-10-04) | 924 |
| DEC-w2-04 (parte 2) | Reparto testigo de los planes propios y rechazo SPLIT reparable (A.E2, D6, 2026-10-04) | 747 |
| DEC-w3-01 | Un contrato de receta con intensidad efectiva y gates separados (B.S2/B.S6, 2026-10-04) | 776 |
| DEC-w3-02 | Texas y Madcow usan una base aproximada de 5RM (D7, 2026-10-04) | 794 |
| DEC-MADCOW-20261004 | Aprobación directa: conservar lunes174/viernes178,35 y corregir oráculos del plan, sin cambios de receta ni migración | 804 |
| DEC-EXECUTOR-20261004 | Autorización humana actual: subagentes Codex editan tras agotar cuota Sonnet; orquestador revisa/filtra/commitea | 958 |
| DEC-RECOVERY-5554-20261005 | Respuesta humana: una recuperación5554 RAM temporal4096 y un intentoQA adicional; sin autorización JBR21 | 966 |
| DEC-CONSUMER-GATE4-20261005 | Excepción humana: una ejecución de los nueve filtros del consumidor; sin autorización de otra suite Base | 974 |
| DEC-cat-20261004 | El exceso debido a músculos secundarios o estabilizadores no bloquea por sí solo | 939 |
| DEC-wiz-20261004 | Intención de plan en biblioteca, TRAINING_ONLY dentro del alcance y excepción limitada de estructura vacía | 948 |

DEC-w3-01 (contrato final), DEC-w3-02 (base de carga), DEC-w2-04 parte 2 y DEC-w3-07 se recuperaron en esta actualización; DEC-cat-20261004 registra la decisión humana nueva sobre el volumen indirecto. La numeración quedó así: DEV-r2-06 es la de las máquinas por categoría (el plan la usaba también para la onda de RIR de §3 D4 y §7, que se registrará como DEV-r2-07) y DEC-w2-08 no estaba en el plan: la creó A.E1 para las prioridades de orden.

### 5.4 Decisiones del diseño editorial (11 §9, l.558)

Son recomendaciones del diseño; no coinciden con las D1–D7 del plan.

| # | Recomendación del diseño | Resultado |
|---|---|---|
| D1 | Relegar los históricos ahora y ocultarlos después | Superada por la D2 del plan (ocultar ya; DEC-w2-07) |
| D2 | `PlanKind` con cuatro valores | Aplicada en `c802b7e78` |
| D3 | Etiquetar los 24 métodos de tercero como «Versión KPKN basada en…» | Reflejada en la tabla editorial (`c802b7e78`) |
| D4 | El programa creado se llama como el `displayName` | Aplicada en 76950161e; preferredName explícito conserva precedencia |
| D5 | PHUL y PHAT heredados quedan en el wizard con rank 990 y badge «Versión anterior» | Fichas c802b7e78 y badge 1bbe7a6fe; ocultar en Fase 2 |
| D6 | BBB con referencias de powerlifting y powerbuilding; FSL solo powerlifting | Aplicada en `d91a0ae2e` |
| D7 | Protocolos avanzados para principiantes: mostrarlos al final con un motivo, sin ocultarlos | Sin paso nombrado en el plan |
| D8 | Plantillas y protocolos de la biblioteca van al wizard; el camino directo queda solo en el editor | Commiteada en 76950161e; TRAINING_ONLY es fix de C.P6, excepción limitada de estructura vacía registrada en DEC-wiz-20261004, WIZFIX `54668bcb8`; QA pendiente |
| D9 | Renombrar bloques en las recetas, coordinado con el paquete de recetas | Mapa de17 literales C/B en mismo commit `f56cd6e76`; Claims2/2 en48/50, aceptación Base/QA pendientes (C.P2 y B.S6) |
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
- Smolov semanas 9 a 13: `8a3adefd4` conserva 3 días y declara exenciones C4 por semana con justificación; no es una nueva aprobación humana.
- nSuns: `8a3adefd4` mueve el AMRAP a la serie 1+@95 de sentadilla, PM y banca pesada. La corrección de la interpretación de 1+ se distingue en §7.1; no es una aprobación humana nueva.

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

**Tests que ya existen y cubren lo entregado:** [PlanCoverageContractTest](../../../android-native/app/src/test/java/com/example/kpkn/domain/training/PlanCoverageContractTest.kt), `PlanRepairAdvisorTest`, `PlanRejectionPresenterTest`, `NativeMaterialEvidenceTest`, `NativeCandidateTableSlotsTest`, `CatalogIdsExistInCatalogTest`, `SetupTrainingPlannerRankingTest`, `PlanGoalMatcherTest`, `SetupWizardCandidateGateTest`, `OwnPlanPrioritiesAndSplitTest`, `RecipeContractPolicyTest`, `PercentResolverTest`, `ExemptionScopeMigrationTest`, `AuthoredProgressionEngineTest`, `ProgressionConsumerCoverageTest`, `TopSetProgressionTest`, `PlanCatalogEditorialContractTest`, `PlanLabelsTest`, `PlanInfoModelTest`, `PlanGlossaryTest`, `ProgramPlanDisplaySummaryTest`, `LibraryCardModelTest`, `SetupWizardReadyWeekTest`, `WizardSplitLabelTest`, `TrainingMaxMergeTest` y los actualizados `PersonalizedPlanCatalogTest`, `NativeProfileSpecCatalogTest`, `WizardPluralCopyTest`, `SetupTrainingPlannerTest` y `AprendeCatalogAuditTest`. También existen `ProgramNamingTest` (9 al entrar), `SetupWizardRepairTest` (26 al entrar), `NativeProfileSplitWitnessTest` (9) y `WeeklyKgCapTest` (9).

**Contrato de cobertura.** `PlanCoverageContractTest` elige el tier con `KPKN_COVERAGE_TIER` (`smoke`, que es el valor por defecto, `ci` o `full`). En `ci` se elige el shard 0 a 3 con `KPKN_COVERAGE_SHARD`, y `KPKN_COVERAGE_WORKERS` fija los hilos. La suite queda en verde mientras no supere el techo de su tier; ese ratchet no acredita que se cumpla el contrato de producto. Techos históricos commiteados, tras A.C2 (`e275d0bbc`): smoke 8, ci 30/30/30/30 y full 120; los del baseline de `5849c32f0` eran smoke 226, ci 887/885/886/879 y full 3 537. La corrida `full` tarda unos 11 minutos con 4 hilos (638 s en `e275d0bbc`). Tras el full 48 medido sin violaciones, A3 puso los seis techos a0 y los commiteó en54668bcb8 (:58–63), sin relajar contrato; FULL definitivo ya pasó25.650/0; falta suite Base aceptada/QA. Los informes quedan en `android-native/app/build/reports/coverage-contract/<tier>[-shard].txt` (por ejemplo `smoke.txt`, `full.txt`, `ci-0.txt`); esa carpeta es salida de compilación y no se versiona.

**Oráculos de aceptación (plan 00 §10.4).** DEC-MADCOW-20261004 fue aprobada directamente por el dueño: con 1RM inicial 200/TM 174, top del lunes de semana 4 de 174 kg y triple del viernes 178,35 kg (102,5 %TM; ambos <1RM). Se corrigieron únicamente los items4/5 de§10 en la copia del repo; los antiguos «nunca>174»/«kg≤TM» contradictorios quedan como antecedente histórico, no como reserva pendiente del dueño. QA sigue pendiente. Ya acreditados por los commits: 5/3/1 cierra ciclo con TM +2,5 y +5 y Smolov Jr semana 2 sube 5 kg (`5312decc2`); T006 Q2 sin COMPOSITION y positivos 58/58 en las corridas históricas; el lote concurrente dejó rojos históricos, después corregidos y verificados por filtros; ningún `displayName` ni `summary` con jerga, ids o inglés (lo vigila `PlanCatalogEditorialContractTest`); «VER PROPUESTA» ya no acepta la propuesta (`2d411c58f`, R-03). El gate48 ya comprobó que toda fila de la rejilla full termina Ready o con reparación verificada. El último full commiteado tenía 120 violaciones de Atleta de 1 día; gate47 midió smoke 1 582/0; gate48 posterior sí midió full 25 650/0 sobre SHA1c267f40. Ratchets y revisión están commiteados y el FULL definitivo pasó. El consumidor TM también está entregado en `7c0c27e5b` con 143 tests sin fallos; quedan la suite Base global y los 28 casos públicos de QA.

## 7. Decisiones pendientes del dueño (2026-10-03, noche, y 2026-10-04)

Se suman a §5.5. D1–D7 y las decisiones previas del dueño conservan su alcance. Los valores implementados por el orquestador se identifican como provisionales: un brief, una memoria de modelo o un commit no prueba una nueva aprobación humana. No se reabren las decisiones ya adoptadas en el plan.

- **Clase de plan frente a nivel en el orden del planner (DEC-w2-06).** Hoy el nivel pesa más que la clase de plan (el orden del plan 00, A.D1): una persona avanzada ve Smolov, Smolov Jr y Coan delante de planes completos de otro nivel. Si se quiere que las especializaciones y los complementos cierren SIEMPRE la lista, basta invertir los criterios 2 y 3 del comparador de `SetupTrainingPlanner`.
- **`bench_press__kettlebell` como candidato (DEC-w2-04 parte 1).** r2 §13.3 la cita («si hay banco») y existe APPROVED en el catálogo; el plan 00 no la lista en B5 y no se añadió.
- **`NO_VALID_SUBSTITUTION` y el pase a peso corporal (DEC-w2-05).** Con la regla literal (todos los rechazos `APPARATUS_*`), una adaptación de autor sin sustituto impide el pase; si el dueño quiere que no lo impida, es una línea de `bodyweightPassAllowed`.
- **Programas Madcow ya activados (pendiente que dejó B.S1, `4aa1bbb10`).** Conservan la receta persistida anterior a los cambios de datos de B.S1 (base de carga al 100 % del 1RM y porcentajes relativos al top set; L-01 y L-02). No está decidido si se dejan como están, se avisa a quien los tenga o se migran.
- **DEC-MADCOW-20261004: decisión directa recibida.** Respuesta del dueño a async `n9Th9hE3Kr8mqZkfX5LwCk6q`: «Sí, conservar 174/178,35 kg y corregir el oráculo». Autoriza corregir los dos oráculos contradictorios de§10 en la copia del repo y conservar la prescripción específica. El original externo, recetas/tests/fases y la política de programas activos no cambian. Esta aprobación no acredita una corrida ni QA.
- **Anatomía de las altas D.M1 a D.M5 (sección 5.5).** `deadlift_to_knees` (cuádriceps y rodilla como PRIMARY: suma 0,5 serie de cuádriceps frente al peso muerto convencional cuando Sheiko migre a esa configuración), `close_grip_lat_pulldown` (trapecio como SECONDARY, igual que `pull_up`), `close_grip_bench_press` (tríceps como SECONDARY) y el solape del alias «curl inclinado» de `incline_biceps_curl` con el curl Bayesian.
- **Fuerza y músculo con mancuernas antes que `APPARATUS_UNKNOWN` (DEC-w2-01, B2).** Con mancuernas, el plan no se bloquea por rack y banco sin confirmar; se eligió para no dejar sin plan al gimnasio sin confirmar (E8) mientras no existiera el botón de reparación. Conviene revisarlo cuando A.C3 y C.P11 lo cableen: se revierte moviendo un bloque del gate.
- **Copy de `APPARATUS_ABSENT` (DEC-w2-02).** El presentador dice «Este plan necesita X, que dijiste que no tienes.»; el diseño editorial (11 §4) decía «…, que marcaste como que no tienes.». Se evitó la forma con pronombre («… no lo tienes») porque no concuerda con «rack de sentadilla», «mancuernas» o «barra y carga».
- **Volumen indirecto del lote de tirones: decisión humana ya recibida (2026-10-04).** El dueño permite el extra si lo aporta un ejercicio donde ese músculo es SECONDARY/STABILIZER; el exceso debido a PRIMARY sigue siendo el problema. Origen verificado: chat de catálogo `01a1076b-1953-79c1-b1a9-561be5ff06a6`, mensaje humano `01a1076c-ab2f-79e3-991c-8792f5c8e048`; catálogo la registra en AUTHORING_FICHA §3.2, EDITORIAL_GUIDE R7 y STATUS. Los pesos 1/0,5/0,4 y el volumen real no cambian. B entregó el gate que distingue la fuente del exceso. T006 pre-lote5 pasó y gate48 posterior sobre el lote5 pasó 797/0 con full 25 650/0. T006 no formó parte de48; su clase pasó en full1 como parcial. Fuentes ya commiteadas, aceptación Base/QA pendientes. Los cuatro rojos históricos son `PersonalizedPlanCatalogTest.everyNativeFamilyBuildsExecutableCanonicalSessions`, `OwnPlanPrioritiesAndSplitTest.the_fitter_retires_the_same_accessories_when_the_bag_prioritizes_the_one_that_stays`, `PlanGenerationCoverageT006Test.Q2_calibrated_pass_changes_no_viable_row` y «Q2 B-02: filas con aviso de volumen alto» (0 esperado frente a 24). No se borran aportes anatómicos ni se re-baselinean esos oráculos para esconder el efecto. Véase DEC-cat-20261004.

- **Marcas `native-progression-c<N>` sin limpiar al re-materializar (DEC-w3-03, H13).** `PlanMaterializer.materialize` limpia las marcas y los avisos de autor (`author-cycle-c`, `author-block-b`), pero no las `native-progression-c<N>` ni los avisos `native-cycle-c<N>` de la continuación nativa. Por la lectura de `registerNativeContinuationOnce`, tras re-materializar el cierre de un ciclo con el mismo número no volvería a registrar la continuación. Es un defecto latente anterior a B.S5, sin prueba. Propuesta: limpiarlas junto con las de autor en B.S11 (runtime nativo) o B.S13. Sin valor por defecto aplicado.


### 7.1 Implementaciones provisionales del orquestador, para revisión

- **AMRAP 1+.** B.S6 parte 2b implementa que corto signifique menos repeticiones que el objetivo: una repetición cumple 1+; también impide propuestas TM para `progression = None` sin gancho `AMRAP_TM` y retira la cláusula heredada del 90 %. Es una corrección provisional del orquestador, no una decisión nueva del dueño; sus filtros actuales están verdes y su fuente está en f56cd6e76; aceptación Base/QA pendientes.
- **Datos de recetas.** Taper SBD-4 al80%, Madcow en ola de4semanas y Candito con4sesiones desde semana1 son elecciones de implementación. Madcow/Candito tienen datos commiteados en `8a3adefd4`; el taper está en f56cd6e76 con filtros actuales verdes; aceptación Base/QA pendientes. Sus diferencias con las fuentes se mantienen visibles. DEC-MADCOW-20261004 resuelve expresamente el oráculo contradictorio, sin aprobar una nueva migración ni alterar datos.
- **Ruta de biblioteca.** TRAINING_ONLY después del alta cumple C.P6: plan 00 l.171/r2 l.693 exigen intención y mismo evaluador, sin imponer FULL. La estructura en blanco directa es una excepción de implementación al literal de plan 00 l.171, registrada/revisada en DEC-wiz-20261004: r2 conserva crear desde cero y la estructura no tiene receta candidata. Un plan con receta sigue evaluándose; strength-cardio conserva lectura sin configuración. No se atribuye aprobación humana nueva ni se inventa un permiso adicional. Las guardas unitarias pasaron, pero fuente en54668bcb8; falta QA de preselección/conservación (C.P14 L03/L04/W06).
- **Fuerza y músculo con mancuernas.** Se conserva la decisión de implementación de DEC-w2-01/B2 y se propone avisar que los principales serán versiones con mancuernas. No cambia D5 ni convierte la variante en preparación para competir.

### 7.2 Opciones que siguen abiertas y reservas de aceptación

- **Repartos sin testigo.** Qué ofrecer en SPLIT para Atleta, Fuerza fuera de 3 días y Músculo sin tirón de 5 días; si se añade un id estable para el último calendario. El hecho entregado es rechazar el reparto incompatible y ofrecer ClearSplit, no aceptar cualquier reparto del mismo número de días.
- **Programas activos.** `Program.sourceRecipe` conserva la receta guardada. Texas 4d, Lilliebridge, nSuns, SBD-4, GZCLP, Madcow, TSA/Calgary y PPL/RP/body-12-3 pueden seguir con datos anteriores aunque su ficha describa la receta nueva. Opciones: mantener, avisar al abrir o migrar únicamente sin semanas entrenadas. `contentVersion` permite identificar versiones, pero no constituye una migración. No se decidió una política nueva del dueño en esta continuación.
- **Taper y AMRAP: reserva de custom/legacy, sin fallo publicado demostrado.** La lectura B/F1-acceptance-audit.md distingue CYCLE (Wendler lee el ciclo completo) del único BLOCK publicado, Juggernaut, cuya descarga conserva la misma ola y el AMRAP anterior. No se afirma que todos los tapers lean solo sus propias semanas ni que pierdan el AMRAP. Una receta guardada/custom BLOCK con un bloque TAPER separado podría excluir el pico previo: es inferencia estática sin fixture ejecutada ni política humana nueva. La frontera se mantiene visible hasta decidir/cubrir ese caso; no se inventa un fallo publicado de F1 ni una migración de activos.
- **Fuentes editoriales.** Las cinco sesiones iniciales del Candito original, el origen de Smolov Jr en banca y la autoría detallada de Lilliebridge figuran en el informe/commit editorial, pero no se verifican con el repositorio. No se elevan a evidencia primaria ni a decisión del dueño.
- **Aceptación pendiente.** FULL definitivo 25.650/0, editorial 71/0 y consumidor 143/0 medidos; fuentes y ratchets commiteados. APK09AE instalado y arranque recuperado PASS, flujo FULL temporal aparcado antes de configurar/activar, ORIGINAL restaurado y QEMU cerrado. Quedan Base global/decisión JBR21 y los 28 casos públicos de QA; las reservas de material Madcow y programas activos mantienen su alcance. No se suman filtros como suite global ni se adelantan Fases 2/3.


### 7.3 Evidencia histórica de esta continuación (checkpoint previo al cierre actual)

El paquete C actualiza siete fichas, dos guardas editoriales y la prioridad de blockGoalSemantics. Los 17 nombres/espejo pasan en48/50 y están en f56cd6e76; Q-07 también pasa en 50. La opción de duración ya fue implementada por A. C.P4 está commiteado en f56cd6e76 y espera aceptación global/QA; el taper conserva90→95→100 %1RM real. Source C permanece congelado.

B entregó C1–C9 HARD, C10 SOFT e intensidad efectiva compartida. El inventario fresco del gate47 mide 40 recetas, 100 crudos/18 tras exenciones, todos los remanentes C10 SOFT y C7=0; el SOFT 112/29 previo es antiguo. A corrigió E13 con tirón vertical cuando falta horizontal y existe vertical (gate OR A.B3, plan 00 l.97), sin recortar cardio ni mínimos. Smoke del47 1 582/0; full del48 25 650/0, con outputs separados. Ratchet/revisión y suite Base final aún pendientes.

| Evidencia de la continuación | Resultado comprobado | Límite |
|---|---|---|
| `scripts/tests`, antes del retiro de 4 configuraciones | 187/187, 90,4 s, verificado por el orquestador | Corrida anterior al catálogo actual; no se usa como resultado de la repetición |
| Backend catálogo, antes del retiro | 10 casos / 24 subtests, verde | Evidencia previa, separada del resultado actual |
| `catalog_v2_pin_sha.py --check`, antes del retiro | Verde sobre SHA prefijo `49273e8e` | Evidencia previa, sin commit de este paquete |
| Catálogo del gate47, tras retiro aprobado y antes de lote5 | 3 pins OK; SHA canónico `31ccbddd4943362bd4258679900ed21549516484985ff01d613360e30c04c562`; 206 definiciones/523 configuraciones/415 pares | Fuente/assets/resources/iOS idénticos al correr 47; evidencia pre-lote5. C no stagea catálogo ni pins |
| Backend catálogo actual | 11 passed + 24 subtests, 2,67 s, verificado por el orquestador | Evidencia actual, separada de JVM |
| `scripts/tests` actual, tras retiro | 195 tests OK, 64,250 s: `python -m unittest discover -s scripts/tests` ejecutado por el orquestador | No acredita suite JVM ni cobertura cero |
| Primer intento Kotlin Base | Daemon perdido en `compileBaseDebugKotlin`, Xmx8GB; ningún test ejecutado | El exit 0 del wrapper no acredita éxito |
| Segundo gate de 32 filtros | Principal Base compilado; fallo en `compileBaseDebugUnitTestKotlin` por import de ProgressionRule incorrecto, corregido en C | Ningún filtro ejecutado; repetición pendiente. Log archivado en artifacts/programs-curation/resume-20261004/C |
| Wrapper `416412542` | Código de salida real y timeout acotado; fixture propia exit 124, sin terminar otros Gradle | No acredita tests JVM; turno cedido a alimentos antes de otra corrida |
| Gate47 Base terminado | Exit 1, BUILD FAILED, 24 min 20 s; 47 suites/786 tests/11 fallos/0 errores/0 omitidos; XML frescos mtime 13:36:16 | Log y 47 XML archivados con SHA/comando en artifacts/C/gate47; pre-lote5. Reparaciones y mapa editorial posteriores requieren nueva corrida |
| T006 fresco en gate47 | 5 tests/0 fallos, 642,44 s. Q1: 62 208/194 400; Q2: 2 304/1 224 viables (360 ABSENT,216 PROFILE,504 TIME); positivos 58/58; 3×calibradas 1 224/1 224, 0 changed/rescued/band/highVolume; Q3: 328/310 viables/18 TIME,3 fronteras | Parcial aprobado dentro de un gate conjunto fallido; pre-lote5 |
| Contrato de cobertura smoke fresco | PlanCoverageContractTest 2/0;1 582 filas,0 violaciones | No acredita full 0; informe archivado pre-lote5 |
| Inventario de receta fresco | 40 recetas (29 protocolos/7 plantillas/4 autoradas), 100 crudos/18 tras exenciones; C7=0 y remanentes solo C10 SOFT | No acredita runtime completo ni convierte47 fallido en verde |
| Catálogo después del splice lote5 | Root comunica SHA `1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099`, 3 pins OK | Aprobación humana verificada por root (mensaje01a107bb-a8dc-7aa1-87da-12483974404b); scripts 195 OK79,522s y backend 11/24subtests2,23s actuales. JVM/full/build/QA pendientes;47 corresponde a31cc |
| Gate48 full terminado, sobre SHA1c267f40 | Exit0, BUILD SUCCESSFUL 26 min 10 s; 48 suites/797 tests/0 fallos/0 errores/0 omitidos, XML frescos mtime14:18:05 | Todos los outputs y diez XML catálogo archivados con SHA en artifacts/C/gate48. Q-07/Q-08/ratchet/revisión posteriores no cubiertos; no es suite Base completa ni QA |
| Cobertura full del48 | 25 650 evaluadas; 13 498 Ready y 12 152 rechazos honestos (1080 ABSENT/360 UNKNOWN/810 PROFILE/9902 TIME), todos reparados; 0 violaciones en siete clases. Progreso final741s/4hilos | Cumplimiento medido del full actual. Ratchet/revisión/commit y revalidación final de cambios posteriores pendientes |
| Inventario del48 | 40 recetas/100 crudos/18 tras exenciones; solo C10 SOFT, C7=0 | Inventario fresco sobre1c267f40; C1–C9 HARD filtrado verde. Suite Base/QA/commit pendientes |
| Fuentes de cierre posteriores al48 | Q-07 Labels 1/1 en full 1 y50; A entregó Q-08, native-cycle-2, ratchet0 y filtro Ciclo repetible; código congelado | Catálogo commiteado en 8eb3432a2/844c3d1d9; FULL de revisión final, suite global, commits propios y QA pendientes. C no stagea |
| Gate50 de reparaciones | Exit 0, BUILD SUCCESSFUL 9 min 49 s; 50 suites/402 tests/0 fallos/0 errores/0 omitidos; catálogo 11 suites/67 tests/0 | XML íntegros, system-out, hashes, log y once XML de catálogo en C/gate50. Filtro de duración implementado; no es suite completa ni QA |
| Base completa 1 | Exit 1, 718 suites/6 327 tests/5 fallos/0 errores/3 omitidos, 31 min 28 s | Rechazada como conjunto; reparaciones verificadas en 50. APK anterior conservado como BUILT_BUT_GATE_FAILED |
| Base completa 2 | Incompleta por agotamiento de commit global: pagefile disponible 71 MB y malloc nativo fallido; sin exit final observable | Log/owner/dump privado en C/fullbase2; XML antiguos del50 excluidos y sin conteo de suite. No prueba fallo de aserción ni aceptación |
| Base completa 3 | Exit 1, BUILD FAILED 27 min 5 s por EXCEPTION_ACCESS_VIOLATION en C2CompilerThread7/Room prepare; 479 suites/4 344 tests parciales/0 fallos/0 errores y un AUGE interrumpido | Archivo completo de evidencia parcial A/fullbase3-safety, formalizado byte a byte en C/fullbase3. El caso interrumpido no es omisión condicional; no es PASS global |
| Base completa 4 | Sesión13035: exit124 por timeout3600s; INCOMPLETE_C1_TIMEOUT. Wrapper liberó candado y terminó solo el árbol propio. Gradle2GB/max-workers1, worker512MB/C1 solo Base, JDK21.0.8 | Sin nuevo crash reportado; E16 NutritionMetricsContractTest p95<50ms fallido publicado, valor no observable sin XML final. Archivo A verificado:0 XML/0 APK frescos, nueve copias idénticas y PIDs47580/101544 muertos; no se reutilizan479/718 ni APK14:41. Sin PASS global/APK aceptado/QA |
| Smoke fresco dentro de Base4, no suite global | Reporte mtime 22:08:28 local: 1582 evaluadas/834 Ready/748 rechazos honestos, todos reparados; cero violaciones en siete clases. Progreso 206s/4 hilos sobre native-cycle-2/ratchet0 | Solo muestra smoke. No reemplaza FULL 25 650, no acredita PASS global ni se reutilizan los83s históricos del full3. A ya archivó Base4; C verificó recibo/nueve hashes sin mezclar salida antigua |
