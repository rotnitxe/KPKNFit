# Auditoría y mejora de RINGS/AUGE · ola Rings v4

Índice de la auditoría de fiabilidad del motor de anillos y del plan que la ejecuta. Plan completo: [`PLAN.md`](PLAN.md). Estado vivo: [`ESTADO.md`](../../rings-v4/ESTADO.md).

## 1. Qué se hizo y por qué

Se auditó RINGS/AUGE para que los cálculos, los modelos de drenaje y recuperación y el aprendizaje del usuario fueran fiables, sin bugs ni inconsistencias, y para proponer una ampliación. El sueño no se trató como problema (Energía es su proxy); tampoco se reabrieron el cuestionario de 24 h, los sliders de Ajustes, Health Connect ni iOS/backend.

La auditoría re-verificó contra el código actual lo que ya habían visto cuatro auditorías previas (29-ago, 03-sep, τ-rendimiento y 12-sep). Se hizo con siete subagentes de solo lectura en tres olas (drenaje, recuperación y aprendizaje; persistencia y concurrencia; superficies de UI y motores de decisión; mapa de pruebas), con recálculo propio en Python y verificación de cada informe antes de aceptarlo.

El veredicto fue que el lazo seguía vivo —10 de los 36 hallazgos de agosto estaban arreglados y 15 más habían cambiado de forma—, pero quedaban dos P0: una pérdida silenciosa de calibraciones por un mutex no reentrante (F-01) y un aprendizaje de τ sesgado por construcción (F-10). Además, las pantallas no coincidían porque repetían hasta ocho veces la misma receta de agregación (F-23, F-31, F-60), y el modelo usaba escalas arbitrarias (F-30 a F-33). La propuesta fue un plan de seis fases y 32 paquetes: primero salvar datos y arreglar bugs baratos, después unificar el motor y, por último, rehacer el aprendizaje con un actualizador bayesiano verificado con usuarios sintéticos.

## 2. Hallazgos, paquete y estado

Estados tomados de [`ESTADO.md`](../../rings-v4/ESTADO.md) (última actualización 2026-10-21 12:50) y del campo WP de las tablas de la sección 4 del plan. El estado es el del paquete, no una revalidación individual de cada hallazgo. «Pendiente» incluye los carriles marcados «por lanzar».

| ID | Sev | Hallazgo | Paquete (WP) | Estado |
|---|---|---|---|---|
| F-01 | P0 | Mutex no reentrante: los guardados dejan de persistir sin error | 1.1 | integrado |
| F-02 | P1 | El cierre por voz descarta los anillos dictados | 1.2a/1.2b | integrado |
| F-03 | P1 | Las ediciones por músculo se pierden al recrear la Activity | 1.6 | integrado |
| F-04 | P1 | Bienestar: procedencia, medianoche y copias reenviadas | 1.2a/1.2b | integrado |
| F-05 | P2 | `recompute` sin `try/catch`; cancelación y claves UTC/local | 1.1 | integrado |
| F-06 | P2 | Cierre en dos fases: el log y los anillos no son atómicos | 1.2a/1.2b; 3.3 (límite) | parcial: la edición ya no se pierde por recreación, pero las dos fases siguen sin ser una transacción |
| F-10 | P0 | Inversión de τ sesgada por construcción | 3.2–3.3 | pendiente |
| F-11 | P1 | Actualizador con α global, sin zona muerta ni prior | 3.2 | pendiente |
| F-12 | P1 | Aprendizaje por rendimiento con orden invertido y trinquete | 3.4 | pendiente |
| F-13 | P1 | Estado zombi y migración que no limpia deltas | 3.1 | pendiente |
| F-14 | P2 | Sin registro predicho-vs-reportado ni métricas de habilidad | 3.5 | pendiente |
| F-20 | P1 | ACWR con denominador fijo y umbrales distintos | 1.4 | integrado |
| F-21 | P1 | Ancla manual descartada a las 18 h | 2.5 | en curso |
| F-22 | P1 | «Horas hasta normal» 20–27 % cortas | 2.4 | pendiente |
| F-23 | P1 | Vista previa del cierre distinta de Home | 2.3 | integrado |
| F-24 | P1 | Evidencia inicial compuesta de forma aditiva | 2.5 | en curso |
| F-25 | P1 | Truncación `toInt()` y baterías atascadas en 99 % | 1.5/1.5b | integrado |
| F-26 | P1 | Blend articular solo en el global de Músculos | D1 → 2.6 | pendiente |
| F-27 | P2 | Evaluaciones no causales y reloj real en el dominio | 1.7 | integrado |
| F-28 | P1 | «Sin datos» se presenta como 100 % afirmativo | 1.10/1.10v2 | pendiente |
| F-30 | P1 | Doble normalización por tanque y escalas arbitrarias | 2.1; D5 → 2.6 | parcial: registro integrado (2.1); escalas explícitas en 2.6 |
| F-31 | P1 | Editor ≠ finish ≠ Home | 2.2a–2.2b2 | integrado |
| F-32 | P1 | El SNC acumula por músculo; Energía casi plana | 2.6 (D3/D4) | pendiente |
| F-33 | P1 | Drenaje muscular saturante y dos motores de capacidad | 2.6; 4.2 | pendiente |
| F-34 | P1 | El creador persiste Core cuando no hay coincidencias | 1.11 | pendiente |
| F-35 | P2 | Dobles conteos y acantilados del drenaje | 2.6 | pendiente |
| F-36 | P2 | Cardio sin FC ni distancia | backlog | pendiente |
| F-40 | P1 | `targetPercentageRM` sin ÷100 | 1.3 | integrado |
| F-41 | P1 | Sugerencia de carga sin inventario y con dos motores | 1.12a–1.12c | integrado |
| F-42 | P1 | Articular contada 2–3 veces; TTC por regex | 2.6; D1 | pendiente |
| F-43 | P1 | Descarga automática de bloque sin flag | 1.12a–1.12c | integrado |
| F-44 | P2 | `cumulativeFatigue` sin normalizar y dos detectores | 2.3 | integrado |
| F-45 | P1 | Datos inferidos presentados como medidos | 1.12a–1.12c | integrado |
| F-46 | P2 | Umbrales dispersos y patrones del catálogo no reconocidos | 1.9/1.9v2 | pendiente |
| F-47 | P2 | `average()` sobre lista vacía | 1.3 | integrado |
| F-50 | P1 | Textos que prometen más de lo que hace el código | 1.9/1.9v2 | pendiente |
| F-51 | P1 | Tres cifras de «Músculos» y accesibilidad del slider | 1.10/1.10v2 | pendiente |
| F-52 | P2 | Placeholders de 50 y ausencia de estado de error | 1.10/1.10v2 | pendiente |
| F-60 | P1 | ≥8 copias de la receta de agregación | 2.2a–2.2b2 | integrado |
| F-61 | P1 | Constantes duplicadas; dominio importa datos | 2.1 | integrado |
| F-62 | P2 | Código muerto verificado sin llamadores | 1.8 | integrado |
| F-63 | P2 | Pruebas que no protegen el comportamiento real | 0.2/0.3 | integrado |

Notas de estado: 2.5 está en curso (etapa A en validación y etapa B pendiente); 3.1, 1.9v2 y 1.11 figuran como «por lanzar» y por eso se cuentan como pendientes. En F-30, el paquete 2.1 está integrado, pero la decisión D5 se cierra en 2.6, aún pendiente.

## 3. Informes

| Archivo | Contenido |
|---|---|
| [`1.7.md`](../../rings-v4/reports/1.7.md) | Paquete 1.7 completado: Energía y Columna no cuentan sesiones futuras y el reloj pasa a ser explícito. |
| [`2.1.md`](../../rings-v4/reports/2.1.md) | Paquete 2.1 completado: registro `AugeParameters` y sustitución de constantes duplicadas sin cambiar cifras. |
| [`2.2a.md`](../../rings-v4/reports/2.2a.md) | Paquete 2.2a: cierre, predictor y editor comparten iteración y núcleo, sin cambios numéricos. |
| [`2.2b1-tablas.md`](../../rings-v4/reports/2.2b1-tablas.md) | Tablas del bloque 2 de 2.2b1: regla anterior frente a regla común y número fijado por prueba. |
| [`2.2b1.md`](../../rings-v4/reports/2.2b1.md) | Paquete 2.2b1 completado: doce ejemplos de paridad exacta editor = predictor = cierre. |
| [`2.2b1-aprendiz-antes.json`](../../rings-v4/reports/2.2b1-aprendiz-antes.json) | Líneas crudas del simulador del aprendiz anteriores al paquete, con la base y el sesgo medidos. |
| [`2.2b1-cambios.json`](../../rings-v4/reports/2.2b1-cambios.json) | Cambios del máster y de las caracterizaciones en 2.2b1, con casos, campos y deltas. |
| [`2.2b1-evidencia.json`](../../rings-v4/reports/2.2b1-evidencia.json) | Evidencia diagnóstica de 2.2b1: sin cambios en el máster y dos casos con delta máximo 1. |
| [`2.2b2-tablas.md`](../../rings-v4/reports/2.2b2-tablas.md) | Tablas completas de 2.2b2: 248 campos y 371 números distintos comparados antes y después. |
| [`2.2b2-evidencia.json`](../../rings-v4/reports/2.2b2-evidencia.json) | Evidencia reproducible de 2.2b2 por etapas, con recuentos de pruebas y capturas del motor. |
| [`2.2b2.md`](../../rings-v4/reports/2.2b2.md) | Paquete 2.2b2 completado: acumulados por canal, puerta axial previa y mitades unilaterales comunes. |
| [`2.3-tablas.md`](../../rings-v4/reports/2.3-tablas.md) | Inventario y evidencia de 2.3: llamadores migrados, firmas, archivos y decisiones ante discrepancias. |
| [`2.3-evidencia.json`](../../rings-v4/reports/2.3-evidencia.json) | Ejecuciones de 2.3 con recuentos, clases, tiempos y rutas de los registros de Gradle. |
| [`2.3.md`](../../rings-v4/reports/2.3.md) | Paquete 2.3 completado: instantánea compartida y vista previa igual a Home, con paridad de cero puntos. |
| [`M0.md`](../../rings-v4/reports/M0.md) | M0 integrado: fusión de `master` con 830 commits; compila y conserva 22 fallos previos de interfaz. |
| [`M0-fallos-previos.txt`](../../rings-v4/reports/M0-fallos-previos.txt) | Tres corridas y lista contrastada de los 22 fallos heredados de interfaz, con sus registros. |
| [`reaudit-1.9-1.10.md`](../../rings-v4/reports/reaudit-1.9-1.10.md) | Reauditoría de solo lectura de los briefs 1.9 y 1.10, con veredictos por ítem y defectos vigentes. |

## 4. Cómo reproducir las verificaciones

Desde `android-native/`, con el envoltorio de Gradle que use el worktree:

```bash
./gradlew :app:testBaseDebugUnitTest \
  --tests 'com.example.kpkn.domain.auge.AugeGoldenMasterTest' \
  --tests 'com.example.kpkn.domain.auge.AugeSyntheticUserInvariantsTest'
```

- **Máster dorado.** `AugeGoldenMasterTest` congela las cifras del corpus en `app/src/test/resources/auge/golden/expected.txt`. Para regenerarlo, ejecutar la misma tarea con `AUGE_GOLDEN_UPDATE=1`: escribe `build/auge-golden/expected.txt` y pasa; hay que revisar el archivo y copiarlo a mano a `src` (nunca lo escribe la prueba). Sin esa variable, cualquier diferencia falla y deja `build/auge-golden/actual.txt`.
- **Usuarios sintéticos.** `AugeSyntheticUserInvariantsTest` ejecuta el motor real con instantes explícitos y comprueba rango, suelos, ausencia de NaN, monotonía, ACWR, drenaje y paridad vista previa/Home. Por defecto usa el modo ligero (3 usuarios × 28 días); con `AUGE_SIM_FULL=1` corre el modo completo.
- **Recuento.** Desde la raíz del worktree, `python -I docs/rings-v4/tools/count_tests.py --clases` lee el XML de la última ejecución de `testBaseDebugUnitTest` y muestra clases, pruebas, fallos, errores, omitidas y tiempo. Sin `--clases` da solo los totales.

## 5. Referencias

- Decisiones tomadas sin consultar al usuario durante la ola: [`DECISIONES_AUTORRESPONDIDAS.md`](../../rings-v4/DECISIONES_AUTORRESPONDIDAS.md).
- Tablero de paquetes integrados, carriles en marcha y grafo de lo que falta: [`ESTADO.md`](../../rings-v4/ESTADO.md).
