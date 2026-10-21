# Calibrador v1 · AMRAP y AMRAP calibrador como funciones reales (especificación)

Fecha: 2026-10-20. Base:
- `audits/AUD3_amrap.md`: veredicto del código y propuesta.
- `audits/INV1_amrap_programas.md`: inventario de programas y puntos de enganche.
- `audits/RES1_calibrador_fuentes.md`: reglas publicadas y evidencia.

Decisiones del usuario:
- «El calibrador debe funcionar al 100 %».
- Debe estar potenciado para los programas que lo necesitan.
- **Sin propagación:** el AMRAP no cambia otros ejercicios, porque no hay evidencia de transferencia (decisión del 20-oct, por la tarde, que sustituye a la idea inicial de propagar por músculos y patrones).
- Se aplican todas las recomendaciones de las auditorías.

Marcas: **[F]** = con fuente (RES1), **[A]** = reutiliza lógica existente de la app, **[DP]** = decisión de producto de KPKN (sin umbral publicado; se puede cambiar).

## 0. Principios

1. **Dos propósitos distintos y comprobables.**
   - **AMRAP** documenta rendimiento y alimenta la regla del programa.
   - **Calibrador** produce una decisión de carga concreta, la muestra, la persona la aplica o la conserva, y queda guardada.
2. **Fidelidad a cada programa.** En un programa de autor manda su regla original (RES1 §12.6). El calibrador de KPKN no se superpone a ella. En sesiones libres, programas KPKN, generados o propios, manda el calibrador.
3. **Nada automático sin control.**
   - Toda propuesta muestra «antes → después» y su motivo.
   - Nunca se tocan las series ya hechas ni las cargas fijadas a mano.
   - En programas gestionados se respetan los modos OFF / PROPONER / AUTO.
4. **Una sola capacidad en toda la app.**
   - Híbrido de `Calculations.kt` sin el +2,5 % por etiqueta AMRAP **[A]**, versionado.
   - Historial, récords, editor, sugerencias y calibración dan el mismo número para la misma serie y el mismo contexto.

## 1. Modelo de datos

- **`SetPurpose`** en `ExerciseSet`: `NONE | AMRAP | CALIBRATOR`.
  - Sustituye a `isAmrap`, `isCalibrator` e `IntensityMode.AMRAP`.
  - Migración de lectura: `isCalibrator` → CALIBRATOR; `isAmrap` / `IntensityMode.AMRAP` → AMRAP.
  - `Exercise.isCalibratorAmrap` (muerto) se retira.
- **`amrapMinimumReps: Int?`**: entero > 0, o nulo = «Sin mínimo» explícito. Es el único mínimo efectivo para la interfaz, el registro y la progresión (arregla «mínimo del rango frente al máximo», AUD3 §9-1).
- **`calibrationScope`** (solo CALIBRATOR): `SESSION` («Resto del ejercicio hoy») | `NEXT_SESSIONS` («Próximas sesiones»).
- **`CompletedSet`** guarda:
  - el propósito realizado;
  - el mínimo efectivo;
  - las reps completas y las parciales (las reps con mala técnica o parciales no cuentan, como en APRE **[F]**);
  - la reserva declarada (RIR) o el fallo;
  - el tipo de carga;
  - la carga original y la normalizada;
  - el lado;
  - la clave de contexto (configuración, equipo, lateralidad).
- **Nueva tabla Room `calibration_decisions`** (KpknDatabase v29 → v30, con migración y prueba de migración):
  - **Identidad y origen:** `id`, `sourceSetId`, `sessionLogId`, `programId?`, `exerciseConfigId`, `contextKey`.
  - **Números:** `capacityKg` (C), `referenceKg?` (B), `referenceSource` (`ACCEPTED_CALIBRATION | HISTORY | DECLARED_1RM | DECLARED_PR | NONE`), `ratio?`, `factor`.
  - **Alcance y elegibilidad:** `scope`, `eligibility` (`OK | NO_EFFORT | RIR_TOO_HIGH | BODYWEIGHT | REPS_OUT_OF_RANGE | PARTIALS_ONLY | NO_EXTERNAL_LOAD`).
  - **Efectos:** `affected` (lista `setId, beforeKg, afterKg`), `propagated` (lista `exerciseConfigId, band, weight, factor`).
  - **Estado:** `status` (`PROPOSED | APPLIED | KEPT | REVERTED | SUPERSEDED`), `createdAt`, `resolvedAt`, `estimatorVersion`.
  - **Idempotencia:** aplicar dos veces o reintentar no duplica cambios. Revertir restaura los `beforeKg` de las series aún no hechas.
- **`reference1RM` deja de mezclar significados** (INV1 §6-9): se separa el TM en un campo propio (`referenceTrainingMax`) y se corrigen `explicitTrainingMaxLoad`, `computeSessionImprovementAdjustment` y el diálogo «Carga inteligente».

## 2. Motor del calibrador (dominio puro, `domain/calibration/`)

Entrada: la serie calibradora registrada, las series pendientes del ejercicio, la referencia B y el material. Salida: `CalibrationDecision`.

1. **Elegibilidad.** Si no es elegible, se registra igual y se explica el motivo dentro de la tarjeta.
   - Carga externa o lastre. Corporal y asistido: «se registra, no se calibra en kg» (sin evidencia, RES1 §9.3) **[DP]**.
   - Reps completas válidas = completas − parciales.
   - Reserva declarada **0–1 o fallo** **[F]** (precisión del RIR cerca del fallo; fallo técnico de APRE). Sin esfuerzo declarado → `NO_EFFORT`; RIR ≥ 2 → `RIR_TOO_HIGH`.
2. **Capacidad C** (e1RM con el estimador común sin bonus) **[A]**.
   - Rango fiable de **3 a 10 reps** **[F]** (Reynolds 2006, GZCLP; las fórmulas divergen más allá).
   - Con más de 10 reps se usa 10: estimación conservadora y aviso «Para calibrar mejor, usa más carga la próxima vez» **[DP]**.
   - Con 0–2 reps no se estima C: se propone una bajada fija del **−5 %** a las series pendientes, en la línea de la tabla APRE en kg (0–2 reps → −2,5 a −5 kg) **[F/DP]**.
   - Con RIR 1 se usan reps + 1 como reps al fallo **[F]** (MacroFactor y RIR de Helms).
3. **Referencia B**, en este orden **[DP]**:
   1. última calibración APLICADA del mismo contexto en los últimos 42 días;
   2. e1RM contextual del historial (`WorkoutPerformanceHomologationEngine`, EWMA) **[A]**;
   3. 1RM declarado o PR «kg × reps» del ejercicio;
   4. ninguna → **primera referencia**.
   - Nunca se compara volumen con e1RM ni se usa otro contexto (AUD3).
4. **Factor** con `r = C/B`, reutilizando `computeSessionImprovementAdjustment` **[A]**:
   - `r ≥ 1,025` → `min(r, 1,05)`;
   - `r ≤ 0,97` → `max(r, 0,92)`;
   - en medio, 1.
   - La zona muerta de ±2,5–3 % es coherente con la variación diaria del 1RM, un 4,2 % de mediana (Grgic 2020) **[F]**.
   - Tope de subida +5 %, dentro de APRE y SBS **[F]**. Tope de bajada −8 % en la sesión **[A]**. Los resets de TM del −10 % siguen siendo cosa de cada programa **[F]**.
5. **Cargas propuestas.**
   - Las series pendientes del ejercicio con carga externa, ni hechas ni fijadas a mano, pasan a `base × factor`, redondeadas al salto del material (2,5 kg barra; el de cada máquina o mancuerna) **[A]**.
   - Sin B (primera referencia): cargas desde C para las reps y la reserva objetivo de cada serie pendiente, con `calculateWeightFrom1RMAndIntensity` / `calculateWeightFrom1RM` **[A]**. No se muestra un % de mejora.
   - El redondeo puede dejar el cambio en 0: entonces «Mantener».
   - Usar la misma fórmula en un sentido y en el otro, en el mismo ejercicio y con reps parecidas, cancela casi todo su sesgo (RES1 §12.4). Por eso C se usa para cargas del **mismo** ejercicio.
6. **Alcance.**
   - `SESSION`: la decisión afecta a las series que quedan hoy.
   - `NEXT_SESSIONS`: no cambia nada hoy; guarda C como referencia aceptada y la próxima sesión de ese ejercicio parte de ella.
   - En programas gestionados con TM, `NEXT_SESSIONS` va por el canal de propuestas de `ProgramAutoregulationEngine` (OFF / PROPONER / AUTO), nunca por encima de la receta.
7. **Sin propagación a otros ejercicios** (decisión del usuario, 2026-10-20). No hay evidencia directa de transferencia (RES1 §10), así que una calibración solo cambia **ese ejercicio** en su mismo contexto (configuración, equipo y lateralidad): sus series pendientes de hoy o su referencia para próximas sesiones. Las herencias de TM que el autor puso en su programa (`SlotRecipe.lift.liftSlot`: T2 de GZCLP, inclinada de Madcow con el TM de banca…) siguen siendo regla del programa, no del calibrador.
8. **RINGS y AUGE.** Cuentan el trabajo y el esfuerzo reales. La etiqueta calibrador no rellena anillos por sí sola (AUD3 §8-8). La ola RINGS v4 sigue en pausa.

## 3. Sesión en vivo (diseño V7, tarjeta 312 × 425 con todo dentro)

- **Antes de registrar:**
  - Insignia «Mínimo N · Calibrador» o «· AMRAP».
  - Consigna «Haz al menos N. Cuenta todas las que completes.».
  - Reps, parciales, carga y esfuerzo con el deslizador aprobado (RIR o Fallo).
- **AMRAP (resultado):**
  - mínimo superado o no alcanzado;
  - «Registrado en tu historial y récords»;
  - el efecto real de la regla del programa: TM propuesto con Aceptar / Ahora no, TM aplicado, «sin regla de progresión», o la regla propia del programa (§5).
- **Calibrador (resultado):**
  - Panel con C, B y el cambio.
  - Lista «S2 60 → 62,5 kg», etc.
  - **Aplicar a S2–S4** · **Conservar**.
  - Los estados de la demo de V7: sube, mantiene, baja, primera referencia, no aplicada con su motivo y próximas sesiones.
- **Persistencia y retorno.** Las decisiones sobreviven a reiniciar la app. Las series ajustadas llevan la marca «calibrada». La ruta de la sesión (V1) lo refleja.
- **Resumen final de la sesión:** una línea por calibración, con su resolución.

## 4. Editor (diseño B3b)

- En el foco de la serie con modo AMRAP:
  - Propósito AMRAP / Calibrador, cada uno con su línea de efecto.
  - Mínimo con −/+ y «Sin mínimo».
  - Solo en Calibrador:
    - el alcance;
    - la vista previa «Ajustará S2–S4 · hoy 60 kg»;
    - el requisito para calibrar;
    - el aviso de corporal o asistido.
  - Hoja «¿Qué hace cada uno?».
- **Acción rápida nueva: «Bloque APRE»** (3, 6 o 10) **[F]**. Inserta en el ejercicio las cuatro series de Mann:
  1. 50 % del XRM;
  2. 75 % del XRM;
  3. AMRAP calibradora con XRM;
  4. carga ajustada por la serie 3.
  - El XRM sale del 1RM con la equivalencia de Mann: 10RM = 75 %, 6RM = 83 %, 3RM = 92 %.
  - Las reps de la serie 4 proponen el XRM de la próxima semana.
- **Preservar el AMRAP al guardar:** el guardado del editor ya no quita el propósito de series de recetas en modo RM (INV1 §6-10, AUD3 §9-1).
- **Importador:** «5+», «3x5+» y «AMRAP» marcan `SetPurpose.AMRAP` con el mínimo (INV1 §6-11).

## 5. Programas: cada AMRAP con su regla original (fidelidad)

| Programa | Qué hace hoy (INV1) | Qué hará (RES1) |
|---|---|---|
| **5/3/1 BBB / FSL** | Un AMRAP corto baja el TM un −2,5 % **y** congela la subida | La serie + registra récord de reps y e1RM (Epley, como Wendler) y **no mueve el TM**. El TM sube +2,5 kg (tren superior) / +5 kg (tren inferior) por ciclo si se cumplieron las reps mínimas con buena técnica [P]; si no, se congela sin −2,5 %. Reset −10 % tras dos ciclos seguidos congelados [S + DP]. Joker sets opcionales: fuera de v1 |
| **nSuns LP** | Tabla en kg; OHP y banca del día 1 sin AMRAP; el OHP nunca progresa | Tabla de RES1 §4. Revisar contra la fuente qué T1 llevan 1+ por día y corregir el OHP. Partir la tabla en tren superior / inferior solo si la fuente lo respalda; si no, como está |
| **Juggernaut 2.0** | Solo baja / congela; no aplica «reps sobre el estándar» | TM nuevo = TM + (reps del AMAP de realización − estándar) × 2,5 kg (tren inferior) / 1,25 kg (tren superior), con tope de 10 reps extra, tras cada realización [S]. Sin superar el estándar: TM igual, con aviso de TM quizá alto |
| **GZCLP** | Solo baja / congela | Etapas del T1: 3×5+ → 2×6+ → 1×10+ → 5RM nuevo × 85 %. El AMRAP del T1 no cambia carga y se topa en 10. T2 por etapas. El T3 sube carga cuando la última serie llega a 25 reps [P] |
| **SBS RTF (estilo)** | +0,5 %/rep; −1 %/rep con 2 o más de menos | Se mantiene (coincide con Lift Vault [S]). No se rotula «SBS» hasta confirmar la hoja oficial |
| **J&T 2.0** | `RepMaxAutoregulated` nunca se dispara | Arreglar `collectRepMaxHits` para que lea los RM del T1 con % |
| **Cube, Lilliebridge, Rippler** | El AMRAP no mueve nada | Revisar el propósito según la fuente; si es solo de registro, mostrarlo como AMRAP de registro, no como función vacía |
| **Plantillas KPKN `power-12-3`, `16-4`, `20-5`** | Banderas AMRAP muertas en las semanas de pico | Programas propios: sus AMRAP pasan a **Calibrador** (`NEXT_SESSIONS` por el canal de propuestas) y se retiran las banderas muertas |
| **Test de 1RM (bloques)** | Siempre pide S, B y D; no hay OHP; ignora las series «Test» | Pide solo los levantamientos del programa, añade OHP y precarga con las series «Test» registradas (con confirmación) |
| **Nuevo: APRE 3 / 6 / 10 (Mann)** | No existe | Protocolo del catálogo con la tabla de Mann [P] en % y redondeo al material: serie 3 → serie 4 en la sesión; serie 4 → XRM de la semana siguiente; reps con mala técnica descontadas |

- Cada programa muestra en vivo **su** resultado («nSuns: TM +5 kg la semana próxima», «Juggernaut: TM 107,5 kg tras esta ola», «5/3/1: récord de reps, el TM sube al cerrar el ciclo»). Lo calcula una vista previa pura de `ProgramAutoregulationEngine`.
- Las descripciones que prometen más de lo que hacen (Juggernaut, `JSS:180`) se corrigen.

## 5b. Training Max (TM): sacarle más partido (propuesta)

**Hoy** (INV1 §2):
- El TM solo existe para sentadilla, banca, peso muerto y press militar (`PowerliftingProfile`), y solo lo usan las recetas con carga `PERCENT_TM`.
- Se fija:
  - con la hoja «Máximo de entrenamiento» al crear o activar el programa, en Programas, en el detalle y en Inicio;
  - con el test de 1RM al cerrar un bloque;
  - con las propuestas de rendimiento.
- Se ve en una línea del detalle, «TM: sentadilla 180 · banca 120 · peso muerto 220 kg», que no incluye el press militar.

**Propuesta:**
1. **Una referencia por ejercicio, para todos los ejercicios principales**, no solo cuatro: 1RM (declarado, test, e1RM o calibración aceptada) y TM = 1RM × % del programa. Los cuatro de `PowerliftingProfile` pasan a ser casos de esta tabla, con migración. El calibrador (`NEXT_SESSIONS`) y el test escriben aquí, siempre con su origen.
2. **El TM se ve donde se usa:**
   - En la tarjeta en vivo: «85 % de tu TM (120) → 102,5 kg».
   - En el foco del editor y en el modo RM: la base es 1RM o TM, según el programa.
   - En el detalle del programa: el press militar y el historial de cada TM con su motivo (marcas del asistente, test, ciclo de 5/3/1, nSuns, Juggernaut, calibración).
3. **Cambiar un TM se nota al instante:** las semanas pendientes se rematerializan solas, conservando las cargas fijadas a mano; ya no hace falta pulsar «Re-materializar».
4. **Si falta un TM no hay sorpresas:** en la preparación de la sesión se pide solo el TM que esa sesión necesita («Falta tu TM de press militar»), con el valor estimado desde el historial si existe.
5. **Arreglos** (INV1 §6):
   - `reference1RM` deja de guardar el TM;
   - `PROMOTE_TM` compara con el 1RM, no con TM × 1,05;
   - el test pide solo los levantamientos del programa e incluye el press militar.
6. **Decisión del 21-oct (FIX-TM):** una serie %TM sin TM **no** se rellena sola con el 1RM al editar a mano.
   - Queda pendiente, con el aviso «Falta el TM».
   - La preparación de la sesión la pide, como dice el punto 4, y propone 1RM × % del programa como valor que la persona confirma.
   - Solo `PlanMaterializer` deriva el TM de la receta al materializar, y lo guarda en `referenceTrainingMax`.
   - Así se respetan las pruebas de CAL1 y D7, que prohíben rellenar cargas %TM con un 1RM inferido del PR o del historial.
   - CAL5b añade la fracción de TM por ejercicio y distingue el 1RM declarado del inferido.

## 6. Paquetes de implementación (Codex, uno a la vez en `C:\kw\u11`, después de AUD2)

| Paquete | Tamaño | Contenido | Pruebas mínimas |
|---|---|---|---|
| **CAL1 · Contrato** | M | `SetPurpose`, mínimo efectivo, alcance, migración de lectura, preservar AMRAP al editar y guardar, importador «+», separar `referenceTrainingMax`, estimador común sin bonus (versionado) | 5 reps con mínimo 4 nunca cuentan como cortas; una receta RM con su AMRAP sobrevive a editar otro campo; un AMRAP desactivado no reaparece; el importador marca «5+»; la misma serie da la misma capacidad en historial, récords y sugerencias |
| **CAL2 · Motor** | M | `domain/calibration/` puro: elegibilidad, C, B, factor, cargas, primera referencia, bajada de 0–2 reps | Tablas de casos con números (sube, mantiene, baja, topes, primera referencia, cada motivo de no aplicación), serie idéntica a la referencia → «mantener», y ninguna decisión toca otro ejercicio |
| **CAL3 · Persistencia y vivo** | L | Room v30 `calibration_decisions`; enganche tras registrar (`WorkoutSetRecorder` ≈ :983, sustituye `amrapCalibrationMessage`); estado de resultado en `SetExecutionCard` (V7); Aplicar, Conservar y Revertir idempotentes; consumo de la referencia aceptada en la sesión siguiente del mismo ejercicio; resumen final | Aplicar cambia solo las series pendientes no fijadas; reiniciar conserva la decisión; reintentar no multiplica el factor; la sesión siguiente parte de lo aceptado; prueba de migración v29 → v30 |
| **CAL4 · Editor** | M | Foco B3b (propósito, mínimo, alcance, vista previa, hoja), acción «Bloque APRE», textos | ViewModel y Robolectric del foco; «Bloque APRE» crea 4 series correctas desde un 1RM |
| **CAL5 · Programas** | L | Tabla §5: 5/3/1, nSuns, Juggernaut, GZCLP, J&T, Cube/Lilliebridge/Rippler, plantillas `power-*`, test de 1RM, vista previa por programa y descripciones | Una prueba por programa con la regla de la fuente; OFF / PROPONER / AUTO respetados |
| **CAL5b · TM** | M | §5b: referencia por ejercicio con origen e historial, TM visible en vivo, en el editor y en el detalle (con press militar), rematerialización automática, petición en la preparación de la sesión, arreglos de `PROMOTE_TM` y `reference1RM` | Migración de los cuatro TM; cambiar un TM recalcula solo las series no fijadas; la sesión pide solo el TM que necesita |
| **CAL6 · APRE** | M | Protocolo APRE 3/6/10 en el catálogo (ficha, editorial, receta, motor de la tabla) | La tabla de Mann en casos límite; la semana siguiente usa el XRM propuesto |
| **CAL7 · Punta a punta** | M | Recorridos completos: editor → vivo → resumen → sesión siguiente; programas gestionados; caracterización de RINGS (sin bonificación por etiqueta) | Robolectric o instrumentadas en el teléfono (`.dbg`) |

**Orden:** CAL1 → CAL2 → CAL3 → CAL4 → CAL5 → CAL6 → CAL7. CAL2 es dominio puro: puede ir en un worktree aparte mientras otro paquete compila, si hace falta.

## 7. Umbrales de producto que se pueden cambiar sin tocar la arquitectura

Reps de 3 a 10 · tope de 10 · RIR 0–1 · bajada de −5 % con 0–2 reps · zona muerta 0,97–1,025 · topes +5 % y −8 % · 42 días de vigencia de la referencia · reset de 5/3/1 tras dos ciclos congelados. Todos viven en un único objeto `CalibrationPolicy`, con pruebas.
