# Desviaciones respecto al plan de planes adaptativos del wizard

Plan normativo: `C:\Users\valen\.opencode\plan\KPKNFit\2026-09-28-planes-adaptativos-wizard.md` (r2, citado como «r2 §x»).
Procedimiento aplicado: r2 §18.1 (si un hallazgo contradice la especificación: contraejemplo mínimo, corregir contrato y test, subir revisión si cambia producto o dosis).

El plan r2 no se modifica desde este repositorio. Cada desviación queda aquí con su ID, el texto que desvía, la aritmética que la justifica, lo implementado, las alternativas descartadas y su estado.

## Registro de estado (cierre del 2026-10-02)

El dueño del producto resolvió el 2026-10-02 lo que este documento tenía «pendiente de confirmación»: «apliquemos todo lo que me dices y recomiendas» y, sobre glúteos, «Si en gluteos se pasa de series, no hay problemas, siempre y cuando no sea tanta la diferencia».

Gobierno del plan: r2 sigue con `user_approval: PENDING` y `approved_revision: null` en su cabecera (líneas 16-17) y vive fuera del repositorio, así que formalmente sigue sin aprobar hasta que exista un anexo firmado o una r3. Su definición de terminado (§18.3, línea 813) pide «reglas de §§10–17 implementadas»: las filas de abajo son las excepciones aceptadas a esa definición, para que el plan no quede incumplido en silencio. Las etiquetas R13, R14 y R16 las puso el informe de auditoría; no están en el plan.

| ID | Qué se aparta de r2 | Estado | Lo actualiza |
|---|---|---|---|
| DEV-r2-01 | Variantes corporales sin tirón (Músculo 5 y 6 días, Atleta 6 días): puente por flexión para respetar el MRV de glúteos (16) | **ACEPTADA** (2026-10-02) | cerrada con este documento |
| DEV-r2-02 | Tolerancia blanda de glúteos: hasta 17,5 series por semana (límite 16), solo como último recurso y solo en planes propios | **APROBADA** e **IMPLEMENTADA** (B-02; ver `artifacts/consolidation-20261001/closeout/notes/G-glutes-tolerance.md`) | cerrada con B-02 |
| DEV-r2-03 | §12.4: el cardio sube de minutos tras dos sesiones cómodas (R13); no subir a la vez duración, intensidad y volumen (R14) | **DIFERIDA** por decisión del dueño; R14 absorbida en R13 | cerrada con este documento (detalle técnico en DEC-w1-07) |
| DEV-r2-04 | §12.4: regla de progresión de carga de los originales PHUL/PHAT y de sus adaptaciones (R16) | **PARCIAL**: PHUL sí (ítem R16 del cierre, pendiente); PHAT no por ahora | quien cierre R16 (detalle técnico en DEC-w1-08) |
| DEV-r2-05 | §12.1: al terminar el bloque se «ofrece» continuar; el programa continúa solo con las últimas cargas y se avisa una vez (H-CICLO) | **APROBADA**; aviso pendiente (ítem H-CICLO del cierre) | quien cierre H-CICLO |
| DEV-r2-06 | §13.3: «desconocidas no se usan» (máquinas). Los planes propios usan variantes de máquina por categoría cuando solo se confirmaron soportes: el modo «configuración exacta» exige presencia declarada de una máquina o polea (2026-10-03) | **IMPLEMENTADA** (paso A.B3 del plan de curaduría de programas, aprobado el 2026-10-03; detalle al final de «Decisiones de la ola 2») | cerrada con A.B3 |

---

## DEV-r2-01 — Variantes corporales sin tirón: el MRV de glúteos (16) prevalece sobre el calendario literal

**Estado:** **ACEPTADA por el dueño del producto el 2026-10-02.** Su criterio, textual: «Si en gluteos se pasa de series, no hay problemas, siempre y cuando no sea tanta la diferencia»; y sobre la recomendación de conservar el plan implementado: «apliquemos todo lo que me dices y recomiendas» (2026-10-02). Decisión: para quien entrena sin ningún material **se conserva el plan implementado** (puente 1 vez por semana en Músculo y 2 en Atleta; glúteos 15–16 series, 16 como máximo) y **no se vuelve al calendario literal de r2**. El plan r2 externo no se edita desde este repositorio y sigue con `user_approval: PENDING`: esta aceptación y el anexo fechado de DEV-r2-02 (más abajo) son el registro hasta que el dueño apruebe una r3 o incorpore un anexo al plan (con entrada en §9 y §18).

**Ámbito:**
- `native:muscle-foundation-v2` (Músculo) sin soporte de tirón, 5 y 6 días.
- `native:complete-athlete-v2` (Atleta completo) sin soporte de tirón, 6 días.

No cambia Músculo de 1–4 días, Atleta de 1–5 días, ningún perfil con tirón disponible (banda, mancuernas, barra, polea, barra de dominadas) ni Fuerza ni Fuerza y músculo. La referencia de «sin tirón» es `pullAvailable == false` en `SimpleCyclePersonalizer.personalizeNative`.

**Actualización 2026-10-03:** desde DEC-w2-01 (B3) el tirón se decide resolviendo una configuración real de remo o jalón; los principiantes con solo barra de dominadas (E13) y los usuarios en modo exacto con máquinas sin remo ni jalón pasan a los calendarios sin tirón; ver DEC-w2-01.

**Alcance en la matriz de pruebas Q2** (2.304 filas = 4 perfiles × 4 respuestas de experiencia × 6 días × 8 fixtures de material × 3 tiempos de 20, 60 y 100 min). Las recetas de esta desviación solo se usan en el fixture E0 (sin material, el único sin tirón): **36 filas de 2.304 (1,6 %)**, a saber Músculo 5 y 6 días (2 × 4 × 3 = 24) y Atleta 6 días (1 × 4 × 3 = 12). De ellas **24 son viables** (las de 60 y 100 min); las 12 de 20 min terminan en `TIME_BUDGET` legítimo [I: Músculo tiene su piso en 21 min según `SetupExecutableAvailabilityMatrixTest`; Atleta de 6 días a 20 min es la misma conclusión por lectura, no medida]. Q2 de g24 no tiene ningún rechazo `COMPOSITION` (`rejectedByReason={APPARATUS_ABSENT=360, TIME_BUDGET=504, PROFILE_MISMATCH=216}`). *Corrección:* una cifra anterior, «32 de 2.304 (1,4 %)», salía de una descomposición **hipotética** del análisis Q2 (`research/q2/informe-q2.md`, hallazgo F11: «24 + 8») que no contaba bien los tiempos de 20 min; vale 36 filas afectadas y 24 viables. Es una proporción de la matriz de pruebas, **no** de usuarios reales.

### Qué dice r2

- §11.3 (especialización corporal de Músculo): `BL=[S,H; puente,H; U,H; G,I]` en las tres apariciones de BL con 5 días (BL/BU/BL/BU/BL) y con 6 días (BU/BL/BU/BL/BU/BL).
- §11.4 (Atleta corporal sin tirón): `XH=[S,H; B,H; puente,H; U,H; C,C]` y, en el día 6, `[U,H; B,H; puente,H; C,C]`; `XA` y `XB` con puente como `H`/`Fv`.
- §14.3: el chequeo de MRV superior de `VolumeLandmarks.byGroup` se mantiene con contabilidad real incluso con `liftSlots` vacío y es **HARD**; «no cambiar MEV/MRV globales de AUGE» y los HARD conservados «no se degradan a SOFT para hacer pasar la matriz». (Única excepción aprobada: Anexo A1 de [DEV-r2-02](#dev-r2-02--tolerancia-blanda-de-glúteos-hasta-175-series-por-semana-solo-como-último-recurso-y-solo-en-planes-propios), 16 → 17,5 series, solo glúteos y solo planes propios, aprobada por el dueño el 2026-10-02.)
- §17.2 #3 y #8: Músculo E0 6d/30 y Atleta E0/E2 6d/60 deben materializar las seis semanas.

### Por qué el literal es insatisfacible (aritmética)

Contabilidad real (`VolumeCalculator.calculateRoleSeparatedMuscleVolume`, techo `VolumeLandmarks.GLUTES` MRV = 16): en el catálogo aprobado la sentadilla sin carga (`quads_sentadilla_sin_carga__default`), la zancada inversa corporal (`reverse_lunge__bodyweight`) y el puente (`glutes_puente_gluteos__bilateral__bodyweight`) cuentan 1,0 serie de glúteo directa por serie; el superman (`back_superman_suelo__default`) cuenta 0,5 indirecta. La flexión de rodillas no suma glúteos. La dosis mínima de principiante es 2 series por slot H/Fv/P.

**Músculo 5/6 días, principiante (el mínimo de dosis):**

| | Literal r2 | Implementado |
|---|---|---|
| Slots de glúteo directo (S, BG, U por BL) | 3 BL × 3 = 9 | 3 BL × (S, U) + 1 puente = 7 |
| Series directas × 2 | 18 | 14 |
| Superman (0,5/serie, uno por BU) | + 1,0 (5d) / + 1,5 (6d) | + 1,0 (5d) / + 1,5 (6d) |
| Total | **19,0 / 19,5 > 16** | **15,0 / 15,5 ≤ 16** |

El fitter de r2 §12.3 no puede corregirlo: el superman no se retira en principiante porque `BU` quedaría en 3 series (< 4, `DayMinimumDose`), y H ya está en 2. En intermedio/avanzado (H = 3) el literal parte de 27 + 2,0/3,0 y el suelo alcanzable del fitter (retirar superman, H 3→2) es 18 > 16 («Glúteos excede 2,0»).

**Atleta 6 días corporal sin tirón, principiante:**

| Día | Slots con glúteo directo (literal) | Series |
|---|---|---|
| XA | P(S), Fv(S), puente H | 6 |
| XH | S, puente H, U | 6 |
| XB | U (F), puente (Fv) | 4 |
| D6 | U, puente H | 4 |
| **Semana** | | **20 > 16** |

Con el fitter (Fv 2→1 en S de XA y en el puente de XB, ambas «práctica adicional», que es lo único reducible) el suelo es 18 > 16 (principiante) y 19 > 16 (intermedio/avanzado, semanas 3–5, P = 3). El literal exige entonces ≥18 series de glúteo directo con un MRV HARD de 16 y, a la vez, programa real en §17.2 #8: no existe una receta que cumpla las tres condiciones.

**Resumen: series de glúteo por semana, calendario literal de r2 frente al aceptado** (E0, sin presión de tiempo; límite 16):

| Caso | Literal r2, antes de ajustar | Literal r2, tras el mejor ajuste posible | Aceptado, tras ajustar |
|---|---|---|---|
| Músculo 5 días, principiante | 19,0 | 19,0 (no hay palanca) | 15,0 |
| Músculo 5 días, intermedio/avanzado | 29,0 | 18 | 16,0 |
| Músculo 6 días, principiante | 19,5 | 19,5 (no hay palanca) | 15,5 |
| Músculo 6 días, intermedio/avanzado | 30,0 | 18 | 16,0 |
| Atleta 6 días, principiante | 20 | 18 | 16 |
| Atleta 6 días, intermedio/avanzado (semanas 3–5) | 27 | 19 | 16 |

En una línea: Músculo 5d, 19,0 literal frente a 15–16 aceptado; 6d, 19,5 frente a 15,5–16; Atleta 6d, 20 antes del ajuste frente a 16. El exceso del literal sobre el límite llega a 3,5 series (19,5 − 16, ≈ 22 %). Origen de las cifras: modelo estático `research/q2/q2sim.js`, reejecutado el 2026-10-02 con el catálogo actual (salida idéntica a `research/q2/q2sim-output.txt`) [M]; solo dos están fijadas por pruebas en verde (g24): 18 series directas en Músculo y 20 con suelo 18 en Atleta.

Contraejemplos fijados en tests (`NativeProfileRecipeAndFitterTest`):
- `no_pull_muscle_calendar_has_a_real_glute_mrv_minimum_counterexample` (Músculo: 3 BL × 3 slots × 2 series = 18 > 16).
- `no_pull_athlete_six_day_calendar_has_a_real_glute_mrv_minimum_counterexample` (Atleta: XA 6 + XH 6 + XB 4 + D6 4 = 20 > 16; suelo del fitter 18).

### Qué se implementó

- **Músculo 5/6 días:** en 2 de los 3 BL se sustituye `puente,H` por `flexión (B),H` (`BL_MRV_ADJUSTED = [S,H; B,H; U,H; G,I]`; 5d: BL, BU, BL_MRV, BU, BL_MRV; 6d: BU, BL, BU, BL_MRV, BU, BL_MRV).
- **Atleta 6 días:** en `XH` (`XH_NP_SIX_DAY = [S,H; B,H; B,H; U,H; C,C]`) y en el día 6 (`X_D6_NO_PULL = [U,H; B,H; B,H; C,C]`) el puente `H` se cambia por una segunda flexión `H` de la misma configuración, que `mergeNativeDaySlots` fusiona en un solo slot de hasta 4 series (§13.3). `XA` y `XB` no cambian.
- Una nota en la descripción del programa lo menciona («dos días BL cambian puente H por flexión H…», «XH y D6 sustituyen puente H por una segunda flexión H…»), **pero casi no se ve** (corrige la redacción anterior «la descripción lo declara al usuario»): usa jerga interna (BL, puente H, MRV); se agrega al final del texto de `description` (`SimpleCyclePersonalizer.kt`, donde arma `description = (entry.description + "\n" + notes...)`), y el detalle del programa muestra esa descripción en un campo editable de dos líneas (`CompactHeroBanner.kt`: `InlineHeroTextField(... singleLine = false)` con `maxLines = 2`, ~l.320-335 y 488). **B-03 (implementado):** la nota se reescribió en lenguaje llano («Hacemos el puente de glúteo 1 vez por semana para no pasar de 16 series de glúteo…», constantes `GLUTE_BRIDGE_ONCE_NOTE` y `ATHLETE_SIX_DAY_BRIDGE_NOTE`) y la revisión del asistente la muestra bajo el plan (`PersonalizationReport.planNotes`), junto con el aviso de volumen alto de DEV-r2-02.

**Se conserva:** número de días y orden de arquetipos; tres exposiciones inferiores por semana; S y U en cada día inferior (frecuencia muscular de glúteos 3/semana); al menos un puente `H` semanal en Músculo y el puente `H`/`Fv` de `XA` y `XB` en Atleta; dosis base de §11.2 y suelos diarios/semanales de §12.3 y §14.3; MEV/MRV globales; severidad HARD de W2-MRV (salvo la franja de glúteos 16 → 17,5 del Anexo A1 de DEV-r2-02, solo planes propios); potencia (2 exposiciones), cardio y fuerza relativa de Atleta.

**Cambia:** la frecuencia del puente baja (Músculo 3→1 por semana; Atleta 4→2); la frecuencia de empuje corporal sube (Músculo 5d: 2→4 días con flexión, 6d: 3→5); en Atleta la flexión de rodillas de `XH` y `D6` puede llegar a 4 series (principiante).

**Resultado esperado (modelo estático; ver «Evidencia»):** glúteos por semana 15,0 (5d) / 15,5 (6d) en Músculo principiante y 16,0/16 en intermedio/avanzado tras el fitter; Atleta 6d principiante 16,0/16 e intermedio/avanzado 16,0/16 en semanas 3–5. **Margen MRV cero** en esos casos: cualquier serie glúteo-directa adicional o cambio de catálogo los devolvería a COMPOSITION.

### Alternativas descartadas

- **ALT-E — quitar el puente de 2 BL (y de XH/D6) sin reemplazo** (`BL₂=[S,H; U,H; G,I]`): da la misma aritmética de glúteos (15,0/16,0 en 5d y 15,5/16,0 en 6d) con menos cambio de contenido y sin añadir empuje en un plan declarado sin tirón; deja días de 3 ejercicios (admitido por §12.3, «no forzar cinco configuraciones»). Se descartó por ahora porque r2 §13.3 pide redistribuir las series «únicamente a movimientos corporales ya presentes» y la flexión conserva el volumen de resistencia diario; habría sido la alternativa a preferir si el usuario no quería más empuje. El dueño aceptó el plan implementado el 2026-10-02 (arriba), así que queda descartada.
- **ALT-C — puente de `H` a `I` en los tres BL:** conserva el puente 3×/semana pero cambia su clase de dosis (1 serie en principiante) y depende de que el fitter retire un puente `I` en 6d para llegar a 15,5 (16,0 en 5d); más frágil y con peor justificación de dosis.
- **Un solo reemplazo en vez de dos (puente 2 veces por semana)** (ítem B-04 del plan de cierre): con el límite en 16 queda en 17,0 (Músculo 5d, principiante) y 17,5 (6d, principiante) por el superman obligatorio; en intermedio/avanzado queda en 16 en Músculo, y en Atleta 6d principiante en 16 e intermedio/avanzado en 17 (semanas 3–5) [M; reproducido el 2026-10-02 con el modelo estático ampliado a esos calendarios]. Habría cabido en la franja de DEV-r2-02, pero cuesta 1,5-2 h más sobre B-02 (4-6 h en total), toca la receta y 4-5 clases de pruebas, por una ganancia marginal. **Descartada por decisión del dueño (2026-10-02): el puente se queda en 1 vez por semana en Músculo (y en 2 en Atleta).**
- **Volver al calendario literal de r2, puente en los tres días de pierna** (ítem B-05): necesita +3,5 series sobre el límite (19,5 frente a 16, ≈ 22 %), que no es «poca diferencia»; afecta justo a los principiantes y, con una tolerancia de +2 o de 17,5, el literal sigue sin caber en Músculo principiante, así que incumpliría el positivo obligatorio §17.2 #8. Cuesta 6-8 h con riesgo alto. **Descartada por decisión del dueño (2026-10-02).**
- **Subir el tope de glúteos a 18 para todo el generador** (ítem B-06): mueve filas hoy viables y los bordes de tiempo (obligaría a reverificar Q3 y la matriz de 20/21 min) y r2 §14.3 prohíbe cambiar los MEV/MRV globales. Descartada por riesgo técnico; la franja de último recurso de DEV-r2-02 da el seguro sin mover ninguna fila viable.
- **Subir el MRV de glúteos, degradar W2-MRV a SOFT con carácter general o reclasificar músculos en el catálogo:** prohibido por r2 §14.3 (la única degradación permitida es la del Anexo A1 de DEV-r2-02: 16 → 17,5, solo glúteos y solo planes propios, aprobada por el dueño el 2026-10-02) (líneas «No cambiar MEV/MRV globales de AUGE» y «los HARD conservados no se degradan») o cambia AUGE/catálogo globalmente.
- **Extender la tolerancia a otros músculos o a los originales PHUL/PHAT** (ítem B-07): en los calendarios corporales solo glúteos supera su MRV; los originales PHUL/PHAT no pasan por esa regla (`liftSlots` vacío en `AuthoredPhulPhat.kt`, ~l.125 y 339, y el cálculo de `mrvApplies` en `SessionCompositionPolicy.kt`), aunque en el papel excedan (según el plan de cierre: glúteos 16,5 en PHUL y 21,5 en PHAT [M]). Sin tolerancia para ellos ni para otro músculo (decisión del 2026-10-02); un aviso informativo para PHUL/PHAT quedó fuera del cierre.

### Hallazgo: el MRV global de glúteos (16) es menor que el piso de la calibración personal (18)

[C] `VolumeLandmarks.byGroup` fija GLUTES en (MEV 0, MAV 8, MRV 16) (`VolumeLandmarks.kt:39`), pero el piso de la calibración personal para «Glúteos» es `Bounds(9, 14, 18)` (`VolumeCalibrationEngine.kt:93`, `floorFor`): el máximo recuperable personal **nunca baja de 18**. Es el caso más claro de un MRV global por debajo del piso personal (en la taxonomía de la política de composición, el deltoides anterior, 16, y el posterior, 20, también quedan por debajo del piso de «Deltoides», 22, pero hoy no se manifiesta). Consecuencias:

- El generador se limita siempre a 16: `budgets()` toma `min(personal, global)` (`SimpleCyclePersonalizer.kt`) y el personal es ≥ 18. La calibración no puede bajar el techo de glúteos.
- El asistente de sesión muestra un aviso **bloqueante** («… en o sobre MRV semanal») cuando las series semanales llegan al MRV personal (`SessionAssistantEngine.kt:644-651`, condición `weeklySets >= weeklyMrv`; `weeklyMrv` sale del `maxRecoverableVolume` del programa, `buildVolumeThresholds`): con el piso en 18, quien esté justo en 18,0 recibiría ese bloqueo. Por eso la tolerancia de DEV-r2-02 se fija en **17,5** y no en 18.

### Reservas conocidas (no bloqueantes)

1. La sustitución por flexión añade estímulo de empuje (flexión 4–5 días/semana en Músculo; hasta 4 series en XH/D6) en un plan sin tirón, agravando el desequilibrio empuje:tirón que r2 §13.3 ya reconoce.
2. `B:H` resuelve siempre a `knee_push_up__default` para cualquier nivel (la tabla de candidatos no distingue capacidad; r2 §13.3 dice «según capacidad»).
3. El fitter MRV no está dirigido por músculo: para llegar a 16 retira todos los `I`/`C` opcionales y baja `H` ajenos (en intermedio/avanzado se pierden gemelo, superman y, en Atleta 6d, el core). Es preexistente (Músculo 3d/4d, Atleta 4d/5d) y está **fuera de alcance** de esta desviación; queda como nota en `artifacts/consolidation-20261001/notes/wizgen.md`.
4. Las recetas nunca se ejecutaron antes de esta consolidación; desde g24 sus pruebas están en verde (ver «Evidencia»), pero las cifras de glúteos por nivel siguen siendo de un modelo estático (`artifacts/consolidation-20261001/research/q2/q2sim.js`); solo 18 (Músculo) y 20 con suelo 18 (Atleta) están fijadas por pruebas.
5. El margen frente al MRV es cero en varios casos (16,0 de 16): cualquier serie de glúteo directa extra o un cambio de catálogo los llevaría a `COMPOSITION`. La franja de DEV-r2-02 (hasta 17,5, último recurso) es el seguro frente a ese riesgo.

### Evidencia exigida antes de dar la desviación por cerrada

`NativeProfileSpecCatalogTest`; `NativeProfileRecipeAndFitterTest` (contraejemplos de Músculo y de Atleta, 5/6 días × tres niveles, `section_17_2_*`, matriz de glúteos); `PlanGenerationCoverageT006Test` (`Q2_real_generation_coverage_2304_inputs` sin COMPOSITION y `Q2_required_positives`); Q5 `muscleE0BeginnerSixDaysThirtyMinutes…` y su gemelo de Atleta. Reportar glúteos por semana y dosis finales tras el fitter.

**Estado de la evidencia (2026-10-02):** en la corrida completa g24 (`artifacts/consolidation-20261001/test-evidence/g24-FREEZE-full-base`) están en verde `NativeProfileSpecCatalogTest` (7), `NativeProfileRecipeAndFitterTest` (29), `PlanGenerationCoverageT006Test` (4), `T006PersistenceAndUseIntegrationTest` (7) y `SessionCompositionPolicyTest` (16). Q2: `totalInputs=2304 … viableCandidates=1224 rejectedByReason={APPARATUS_ABSENT=360, TIME_BUDGET=504, PROFILE_MISMATCH=216}` (ningún `COMPOSITION`); `Q2_required_positives`: 58 filas aparte, todas listas (`rowsByWitness` = `readyByWitness`). Falta solo lo que no está fijado por pruebas: las series de glúteos por semana y nivel tras el fitter siguen siendo del modelo estático (reejecutado el 2026-10-02 con el catálogo actual, salida idéntica a la guardada).

### Compatibilidad

No existen programas generados con el calendario literal (nunca materializó) ni migración que hacer. Con el calendario aceptado ya pueden existir programas activados: si algún día se cambia la receta aceptada (ALT-E, un solo reemplazo u otra), incrementar `contentVersion` de la receta (r2 §13.4: IDs o revisiones nuevos para versiones incompatibles). La tolerancia de DEV-r2-02 no cambia ninguna receta y no necesita migración.

---

## DEV-r2-02 — Tolerancia blanda de glúteos: hasta 17,5 series por semana, solo como último recurso y solo en planes propios

**Estado:** **APROBADA por el dueño el 2026-10-02** e **IMPLEMENTADA** en el cierre (B-02): ver la nota del paquete G (`artifacts/consolidation-20261001/closeout/notes/G-glutes-tolerance.md`), que da las cifras antes/después y la API del aviso (`PersonalizationReport.highVolume`). La revisión del asistente muestra el aviso «Volumen alto en Glúteos…» y las notas del plan en lenguaje llano (B-03).

**Decisión del dueño (2026-10-02):** «Si en gluteos se pasa de series, no hay problemas, siempre y cuando no sea tanta la diferencia». En concreto:

- El límite de glúteos sigue siendo **16 series por semana** (`VolumeLandmarks.byGroup`, GLUTES MRV 16): **no se toca**, igual que ningún MEV/MRV global de AUGE.
- De 16 a **17,5** series (+1,5, ≈ 9 %) es «volumen alto»: se permite y se avisa. Por encima de 17,5 se rechaza como hoy (`COMPOSITION`).
- Solo en **planes propios KPKN** (los cuatro nativos: Fuerza, Fuerza y músculo, Músculo y Atleta completo).
- Solo como **último recurso**: el ajustador sigue apuntando a 16 y usa la franja únicamente cuando ya no queda ninguna otra palanca dentro de los mínimos de §12.3 y §14.3.
- **Sin tolerancia** para ningún otro músculo ni para los originales PHUL/PHAT y sus adaptaciones.

**Qué texto de r2 relaja:** §14.3 (línea 597): «Mantener chequeo de MRV superior de `VolumeLandmarks.byGroup` con contabilidad real aun con `liftSlots` vacío … Si se excede, el fitter reduce accesorios dentro de mínimos; si no puede, devuelve COMPOSITION. No cambiar MEV/MRV globales de AUGE.», y la línea 601 («Los HARD conservados no se degradan a SOFT para hacer pasar la matriz.»). No se cambia ningún MEV/MRV global. Lo que cambia es que, **solo para glúteos en planes propios**, el exceso entre 16 y 17,5 deja de ser HARD y pasa a ser un hallazgo SOFT con aviso; por encima de 17,5 sigue siendo HARD. No se hace para que pase una prueba concreta: es una política declarada del producto, con su techo, su alcance y su aviso.

**Por qué 17,5 y no 18:** ver el hallazgo de DEV-r2-01 (MRV global 16 frente a piso personal 18). Con el techo en 18, la franja llegaría justo al punto donde el asistente de sesión bloquea a quien está en el piso personal.

**Efecto esperado hoy [C, solo sin calibración]:** ninguno. Q2 de g24 no tiene rechazos por composición en 2.304 entradas, es decir, 0 de las 1.224 filas viables cambian. Eso vale solo sin calibración (Q2 corre con `volumeRecommendations` vacío mientras el asistente real sí las pasa: `SetupWizardViewModel.kt`, donde entrega `volumeRecommendations = draft.volumeRecommendations` al personalizador): la pasada de Q2 calibrada y el contador de «filas en banda = 0» son parte de B-02. La franja queda como seguro ante cambios de catálogo o de dosis.

**Anexo a r2 §14.3 (texto fechado para incorporar al plan o a una r3):**

> **Anexo A1 a r2 §14.3, fecha 2026-10-02.** Autorizado por el dueño del producto el 2026-10-02: «Si en gluteos se pasa de series, no hay problemas, siempre y cuando no sea tanta la diferencia». Se mantiene el chequeo de MRV superior de `VolumeLandmarks.byGroup` con contabilidad real y no cambia ningún MEV/MRV global de AUGE (GLUTES MRV = 16). Excepción única: en los programas propios KPKN, el exceso de glúteos entre 16 y 17,5 series semanales es un hallazgo SOFT («volumen alto», con aviso) y no impide materializar; por encima de 17,5 el hallazgo sigue siendo HARD y el fitter devuelve COMPOSITION. El fitter sigue apuntando a 16 o menos y solo acepta la franja cuando no queda otra palanca dentro de los mínimos de §12.3 y §14.3. Sin tolerancia para otros músculos ni para los originales PHUL/PHAT y sus adaptaciones (`AUTHORED_EXACT`). Este anexo no cambia el resto de los HARD ni la prohibición de degradarlos a SOFT.

**Compatibilidad:** no cambia recetas ni programas ya activados; no requiere migración (Room sigue en v28). El aviso visible de «volumen alto» en la revisión del asistente es el ítem B-03 del cierre.

---

## DEV-r2-03 — Progresión de cardio de §12.4 (R13) diferida; regla de no subir todo a la vez (R14) absorbida

**Estado:** **DIFERIDA por decisión del dueño (2026-10-02)**. En el Atleta completo el cardio **mantiene los minutos del plan durante todo el programa**; si el atleta quiere más, los cambia a mano en el campo de duración del editor. No hay propuesta de subida 10→15→20→30. El detalle técnico de lo que falta está en DEC-w1-07.

**Texto de r2 que no se cumple:** §12.4 (línea 433): «Cardio: tras dos exposiciones completas a intensidad conversacional, proponer el **siguiente valor ofertado** …» y «No elevar simultáneamente duración, intensidad y volumen de fuerza».

**Por qué no hay una promesa incumplida en pantalla [C]:** nada sube el cardio por sí solo y la intensidad es fija (`NativeCardioDefaults.details`, intensidad `MEDIA` por defecto, `NativeProfileSpec.kt` ~l.603-611). La escalera existe como funciones sueltas, `NativeCardioEscalation.nextOffered` (`NativeProfileSpec.kt` ~l.725-746) y `NativeWorkoutProgressionRuntime.nextCardioDurationAfterTwoConversationalExposures`, que solo llaman pruebas.

**R14 (no subir a la vez duración, intensidad y volumen):** es una restricción sobre R13, no una función aparte, y hoy se cumple por construcción. Queda **absorbida en R13**: si algún día se hace R13, añadir una guarda de una línea (sin propuesta de cardio mientras haya una propuesta de volumen de AUGE pendiente) y una prueba.

**Si más adelante se quiere (según el plan de cierre):** versión mínima de 5-7 h sin datos nuevos: cuenta como exposición un bloque con al menos el 90 % de los minutos previstos fuera de descarga, y la propuesta hace la pregunta del habla («Si pudiste hablar en frases cortas durante todo el bloque, súbelo a 15 min»). No la versión completa con un campo nuevo de intensidad (9-13 h): descartada. Se revisa con uso real del Atleta completo. Ojo: el botón de progresión del editor de cardio (`CardioEditorCard.kt` → `CardioProgressionEngine.suggest`) sube un 10 % a partir del esfuerzo planificado (10 min pasan a 11), un valor fuera de la escalera ofertada que §12.4 prohíbe; no es un respaldo limpio de R13.

---

## DEV-r2-04 — Progresión de carga de los originales PHUL/PHAT y de sus adaptaciones (R16): parcial

**Estado:** **PARCIAL, por decisión del dueño (2026-10-02)**.

- **PHUL original y adaptado: sí** usan la doble progresión KPKN, rotulada «Recomendación KPKN §12.4» (el plan lo respalda en su línea 262: «La regla de progreso inicial es recomendación KPKN por ejercicio de §12.4, identificada como tal»). Implementación **pendiente** (ítem R16 del cierre; depende de H-IDENT). PHUL dura 12 semanas con `repeats = true` (`AuthoredPhulPhat.kt`, `PHUL_WEEKS = 12`), así que el cierre de ciclo no puede decir siempre «6 semanas».
- **PHAT: no por ahora.** El plan no lo respalda para PHAT, su reserva cambia de 2 a 1 desde la semana 5 y su ventana es de 6 semanas con `repeats = false`, con acciones propias de fin de ventana.
- **Adaptaciones:** heredan lo de su original.

**Texto de r2 que no se cumple (en PHUL hasta que se implemente; en PHAT, mientras no se retome):** §12.4 (línea 435): «Adaptaciones de originales usan la regla del original mientras sea semánticamente compatible; cuando la sustitución rompe la base de carga, adoptar esta doble progresión únicamente para esos slots y registrar el cambio». Hoy las recetas de autor y las adaptadas llevan `nativeProgression = null`. Detalle técnico del hueco actual: DEC-w1-08. La opción estricta (solo los slots sustituidos) cuesta 6-9 h y deja planes a medias: no se recomienda.

---

## DEV-r2-05 — Fin de bloque: el programa continúa solo con un aviso, sin pantalla de oferta (H-CICLO)

**Estado:** **APROBADA por el dueño (2026-10-02)**; el aviso único está **pendiente** (ítem H-CICLO del cierre).

**Texto de r2 que se aparta:** §12.1 (línea 399): «Fin de bloque: ofrecer continuar otras seis semanas manteniendo configuraciones y últimas referencias específicas; iniciar de nuevo semana base, sin duplicar semana 6 ni reiniciar historial».

**Qué hace el código hoy [C]:** `ProgramProgressEngine.completeCycle` abre el ciclo siguiente sin pedir confirmación (~l.947-1020) y `registerNativeContinuationOnce` arrastra las últimas cargas y registra la continuación una sola vez (~l.1037-1070). Las propuestas pendientes se caducan con un aviso visible en el detalle del programa; lo que no se avisa es la continuación.

**Decisión:** mantener la continuación automática con las últimas cargas y añadir **un único aviso** al entrar en el ciclo 2 («Empiezas un nuevo bloque con tus últimas cargas»), que sustituya —no se sume— al aviso de caducidad y tome la duración del programa (PHUL dura 12 semanas, no 6). No se construye una pantalla de oferta (4-6 h).

---

## Decisiones de la ola 1 de consolidación (2026-10-01)

Convención: `DEV-r2-NN` (arriba) es una desviación del texto de r2. `DEC-w1-NN` es una decisión de la ola 1 que cierra un hueco o un conflicto sin contradecir r2, o que registra algo **NO ENTREGADO**. Al escribir estas notas no se ejecutó Gradle: la evidencia son las corridas `artifacts/consolidation-20261001/test-evidence/g02-baseline-integrated-base` y `g04-wave1-targeted` (XML JUnit) y la aritmética con el estimador real (`SessionDurationEstimator` + `calculateSessionTimeBreakdown`). Cada decisión nombra el test que la confirma o la refuta; lo marcado «reconstrucción» es aritmética sobre el código, no una ejecución.

### DEC-w1-01 — El fitter no recorta cardio ni descansos: a 45 min el cardio por defecto de 15 min da TIME_BUDGET

**Estado:** implementada; restaura la conformidad con r2 (no es una desviación). Solo queda por confirmar la consecuencia de producto del último párrafo.

**Qué ocurría.** `SimpleCyclePersonalizer.personalizeNative` aplicaba, además de los pasos 1–5 de r2 §12.3, dos palancas que r2 no contiene: un «paso 4b» (comentado como `§12.3.4b`, que no existe en r2) que acortaba bloques de cardio hasta caber, y un ajuste final de descansos (−15 s en todo slot con descanso > 75 s, con suelo de 60 s) cuando el exceso era ≤ 3 min.

- Atleta corporal 1 día, principiante (X1_NP = `P(S); B,F; S,Fv; puente,H; U,H` + cardio): con cardio de 10 min el día mide 41 min (2460 s; `cardio_defaults_use_session_time_boundaries_44_and_45` lo aserta a SESSION_TIME = 44). A SESSION_TIME = 45 el default es 15 min (§11.4: «44 min selecciona default10 y 45 min default15») y el día mide 2460 + 5 · 60 = 2760 s = 46,0 → **46 min > 45**. Los pasos 2–4 no tienen palanca en ese día (sin I/C; H ya está en 2 series en principiante; no hay segunda exposición de Fv; con un solo día no hay a dónde mover). El generador devolvía entonces un programa con «Cardio Día 1: 15→10 min por presupuesto (§12.3.4b)» y `maxSessionMinutes = 41`: el cardio entregado (10) no era el default seleccionado por SESSION_TIME (15). Es el fallo idéntico en la línea base y tras la ola 1 de `NativeProfileRecipeAndFitterTest.cardio_defaults_use_session_time_boundaries_44_and_45` y de `PlanGenerationCoverageT006Test.Q3_fitter_viability_neighbors_and_cardio_default_boundaries` («default cardio a 45 min expected:<15> but was:<10>»): misma raíz.
- El 4b tampoco respetaba el contrato de valores. Reconstrucción por lectura: con 10 min no hay escalón ofertado menor y la rama de respaldo (`actual − 1`, mínimo 5) daba 9…5 min (con 40 min de presupuesto y cardio explícito de 10, el día de 41 min se entregaba con un bloque de 9), contra «cardio ≥ 10 min por bloque» (§12.3) y «no inventar 12/14 min fuera de este contrato» (§12.4). Además recortaba los minutos explícitos del usuario.
- El ajuste de descansos: a 45 min el 1 min de diferencia caía dentro de su umbral y solo lo detenía el suelo H9 del T1 (180 s), por casualidad; en planes sin T1/T2 (Músculo) podía entregar descansos de 105 s en lugar de los 120 s de la dosis.

**Qué dice r2.** §11.4: «Elegir minutos explícitos reemplaza el default por bloque […] se valida completo; no se recorta silenciosamente» y «Después se valida la duración total; no recalcular el default usando tiempo restante ni entrar en un bucle». §12.2: «Las recetas mínimas no se hacen caber bajando descansos». §12.3: lista cerrada de palancas (1 dosis completa; 2 retirar I y luego C; 3 reducir H, I, F, Fv; 4 mover un accesorio; 5 variante de split o `TIME_BUDGET` con los minutos mínimos calculados, «no devolver éxito parcial») y suelo «cardio ≥ 10 min por bloque». §12.4: si se eligieron 20/30 min «conservar esos minutos, sin reducirlos». §17.3: «cardio 44/45 min de SESSION_TIME elige 10/15 de default».

**Qué se hizo.** Se eliminaron el paso 4b y el ajuste de descansos y no se sustituyen por nada. A 45 min, Atleta 1 día principiante devuelve `TIME_BUDGET` con `maxSessionMinutes = 46` (= 41 + 5, el coste exacto del escalón 10→15). Cuando hay programa, el estimador común mide ≤ presupuesto y el cardio es el default por SESSION_TIME o los minutos explícitos tal cual. El oráculo de ambos tests ya era correcto (no se tocó).

**Tests.** `NativeProfileRecipeAndFitterTest.cardio_defaults_use_session_time_boundaries_44_and_45` (44 → programa de 41 min con cardio 10; 45 → `null`, `TIME_BUDGET`, 46 − 41 = 5); `PlanGenerationCoverageT006Test.Q3_fitter_viability_neighbors_and_cardio_default_boundaries` (45 → rechazo temporal con `defaultCardio=15m`; 30 → 10; 59 → 15; 60 → 20); nuevo `athlete_cardio_minutes_are_validated_whole_and_never_trimmed_to_fit` (10/15/20/30 min explícitos con mínimos 41/46/51/61; un minuto menos → `TIME_BUDGET`, nunca un cardio más corto).

**Consecuencia de producto (a confirmar).** Un usuario de Atleta 1 día principiante con SESSION_TIME = 45 min que no elija minutos de cardio verá «el mínimo real es de 46 min» (con 46–59 min el default de 15 min cabe); con 10 min explícitos el día mide 41 y cabe. Bajar el default cuando no cabe contradice el texto citado de §11.4; si el producto lo quisiera, es un cambio de contrato (r3, con registro visible del cambio y tests nuevos), no un recorte silencioso del fitter. **Riesgo:** las filas que solo cabían gracias a esas dos palancas pasan a `TIME_BUDGET` con mínimo ≤ presupuesto + 3. Los testigos de §17.2 (Atleta 3d/45, 5d/45, 2d/60, 1d/60, 6d/60) no dependen de ellas según la aritmética estática (cardio dedicado o presupuesto ≥ 60); si alguno cae, es un hallazgo real.

### DEC-w1-02 — Músculo corporal de 3 días: el fitter retira los accesorios «de la tabla» y el mínimo real es 21 min, no 25

**Estado:** implementada (producción) y oráculo de la matriz recalculado (DEC-w1-03).

**Qué ocurría.** `T019_A_negativo_…` informaba `requiredMinutes = 25` en 3 días frente a 21 en 1, 5 y 6. Aritmética con el estimador (reconstrucción): BFA sin ajustar = 180 + 3 · 300 (H) + 120 (superman, I) + 108 (core, C) + 3 · 90 (aproximaciones S, B y superman: SM pertenece a la familia bisagra) = 1578 s → 27 min (coincide con las sesiones de 27 de `A-d1-m30`); BFB = 180 + 900 + 120 (G) + 108 + 2 · 90 = 1488 s → 25 min. Con I y C retirados ambos son 3 H + 2 aproximaciones = 1260 s = 21,0 min (confirmado en 1 día: `A-d1-m21` mide 21). El fitter se atascaba por dos causas encadenadas: (a) `isFittable` comparaba, por posición de día, el máximo de todas las semanas, y en FA/FB/FA ↔ FB/FA/FB (semanas pares: planes de día `[1], [0], [1]`) una misma posición la ocupan dos planes de día, así que retirar un accesorio de uno solo no baja ese máximo mientras el otro empate; (b) W5 (mismo conjunto de accesorios de la semana en todas las semanas del bloque) rechaza retirar el superman de la primera copia de BFA mientras la tercera lo conserva. Final atascado: BFB sin ajustar (25) y BFA sin core pero con superman (1470 s → 25) → «mínimo real 25», aunque r2 §12.3 paso 2 permite una receta de 21 min; el informe contradecía «mostrar el mínimo real necesario». Y `A-d3-m21` «pasaba» solo porque otro candidato histórico (`native:full-body`) cabía, no el plan propio.

**Qué se hizo.** (1) `isFittable` compara cada sesión materializada (`allSessionMinutes`) en lugar del máximo por posición de día; en los calendarios sin alternancia es equivalente. (2) Solo en el calendario que alterna (Músculo 3 días; helper `alternatesEvenWeeks`, que comparte con el ensamblado de la receta) el paso 2 se repite mientras acepte retiradas, así el accesorio sale primero de la copia que W5 permite y después de la otra («por orden inverso de la tabla»). Traza esperada (reconstrucción): pasada 1 retira G de BFB, superman de la tercera BFA, core de la primera y de la tercera BFA; pasada 2 retira el superman de la primera BFA y el core de BFB → 3 H por día, 21 min.

**Tests.** Nuevo `NativeProfileRecipeAndFitterTest.muscle_bodyweight_three_day_alternation_reports_and_reaches_its_real_floor` (20 min → `TIME_BUDGET` con 21; 21 min → programa con tres H por día, alternancia intacta, reglas duras limpias, las dos BFA con los mismos ejercicios) y `SetupExecutableAvailabilityMatrixTest.T019_A_negativo_…` / `T019_A_suficiencia_…` (DEC-w1-03).

### DEC-w1-03 — Matriz grupo A: piso independiente por fila, suficiencia con el plan propio y metadatos de composición en el arnés

**Estado:** implementada (solo tests).

- `independentBodyweightMuscleFloorMinutes(days)` calcula el piso de cada fila desde el calendario y los arquetipos de r2 §11.3 (con DEV-r2-01 en 5/6 días) y las constantes del estimador, aplicando el paso 2 de §12.3 con el suelo diario (≥ 2 configuraciones y ≥ 4 series). Por día: BFA, BFB y BL_MRV = 1260 s (21 min); BL = 1170 s (19,5 → 20); BU = 888 s (14,8 → 15; no puede perder superman ni core porque quedaría con 3 series). Son exactamente las sesiones que mide el motor en `A-d5-m21` (20, 15, 21, 15, 21) y `A-d6-m21` (15, 20, 15, 21, 15, 21). Pisos: 1 d = 21, 3 d = 21, 5 d = 21, 6 d = 21 (el día más largo es siempre 3 H + 2 aproximaciones; lo que cambia entre filas es la composición de los demás días, no el máximo).
- Las filas de suficiencia (`A-dN-m21`, id calculado desde el piso de su fila) exigen que el testigo sea `native:muscle-foundation-v2` y que su sesión más larga mida **exactamente** el piso. Antes valía cualquier candidato.
- Las filas negativas de 5 y 6 días **no** fallaban por el mínimo (el plan propio ya informaba 21): `template:body-20-5` / `template:body-16-4` se rechazaban con `IllegalStateException: No hay ExerciseCompositionMetadataProvider` (INTERNAL_MATERIALIZATION). Las plantillas resuelven los metadatos por `CompositionMetadataHolder`, que en producción rellena `initializeExerciseDatabase` y este arnés no inicializa. Las filas negativas (las únicas que juzgan los rechazos de todos los candidatos) instalan el mismo proveedor, derivado del asset verificado por identidad, y restauran el valor anterior. No se relajó ninguna aserción; no se instala en las demás filas porque `T001_03` compara el conjunto de rechazos con los ID publicados.
- **Pendiente de producción (no es de este paquete):** `SetupWizardViewModel` debería pasar metadatos explícitos a `ProgramTemplateEngine.applyTemplate`/`PlanMaterializer.materialize` en lugar de depender del holder global; hoy un wizard abierto antes de que `initializeExerciseDatabase` termine rechazaría todas las plantillas como error interno.

### DEC-w1-04 — Los planes de autor no se filtran por nivel (wizvm)

**Estado:** implementada; decisión del implementador, revisable por el usuario.

`AuthoredPlanMaterializer` llama a `PlanAdaptationResolver.adapt` con `level = null` y no hay rechazo `LEVEL_UNSUITABLE` para el original. Razón: r2 §15.3 («ordenar primero propios […], luego originales/adaptados […] según preferencias/nivel, **sin ocultar los originales**») y §17.2 #1 («no exigir PHAT a principiante») piden ordenar y no obligan a ocultar; todos los protocolos heredados son visibles para todos los niveles, y un original visible con su adaptación oculta sería incoherente. Efecto deliberado: las sustituciones usan el RIR 2 inicial del autor, no el RIR 3 de principiante de los planes propios (§12.1 solo define los cuatro propios nuevos). Alternativa: pasar `draft.experience.toCatalogLevel()` a `adapt` y añadir el mismo chequeo al original, con `LEVEL_UNSUITABLE` (motivo cerrado de §15.2); implica decidir si PHUL/PHAT se ofrecen a principiantes.

### DEC-w1-05 — Un slot SPEED sin material es NotViable, no se sustituye (wizvm)

**Estado:** implementada; conforme a r2 §10.3, §13.4 y §14.2.

Antes `PlanAdaptationResolver.applySubstitution` reescribía el SPEED de PHAT (6×3 al 65 % de «su carga habitual de 3–5 reps») con otro ejercicio en series de RIR 8–12, lo que rompía la base «misma configuración técnica y unidad que la carga de referencia» (§10.3) y «no estimar un 3–5RM a partir de una marca distinta». Ahora un SPEED con material ausente devuelve `NotViable(NO_VALID_SUBSTITUTION, slotId = speed-…)` («si quedan slots esenciales sin alternativa, esa adaptación no es viable», §13.4): la adaptación no se publica y el original conserva su rechazo tipado por material.

### DEC-w1-06 — Las adaptaciones van tras los propios y los originales (wizvm)

**Estado:** implementada; interpretación de r2 §15.3, revisable.

`SetupWizardViewModel.collectViable` ordena de forma estable las entradas con procedencia ADAPTED detrás del resto (que conserva el ranking del planificador): el desempate por id habría puesto «adapted:» delante de «native:». r2 §15.3 pide propios primero y después originales/adaptados «según preferencias/nivel»; aquí los originales van antes que las adaptaciones. Además una adaptación de una adaptación conserva a su ORIGINAL como `parentId/parentRevision`, y los cambios de un mismo día de receta se registran una sola vez (deduplicados por slot, origen y destino).

### DEC-w1-07 — NO ENTREGADO: progresión de cardio de r2 §12.4 (R13/R14)

**Estado:** no entregado; sin consumidor. Ningún test ni UI lo oculta: `RecipeCardioBlock.progression` se escribe y nadie lo lee. **Desde el 2026-10-02 es una desviación aceptada (DEV-r2-03): el dueño difiere R13 —el cardio del Atleta mantiene los minutos del plan— y R14 queda absorbida en R13.** Lo que sigue es el análisis técnico de lo que faltaría si algún día se retoma.

r2 §12.4 pide, tras dos exposiciones completas a intensidad conversacional, proponer el siguiente valor ofertado (tras resistencia 10→15 y parar en 15; día dedicado 10→15→20→30; 20/30 elegidos se conservan). Faltan exactamente estos datos y puntos de llamada:
1. **La señal «conversacional» no se registra.** `CompletedSet` no guarda intensidad conversacional; el registro de cardio de `WorkoutViewModel` (~l. 5585) escribe `rpe = details.resolvedRpe()`, que es la intensidad **planificada** del bloque, no el esfuerzo reportado. `avgHeartRate` es opcional y no hay zonas ni edad con las que decidir «conversacional» sin inventar un umbral.
2. **No hay pregunta** al terminar el bloque (`CardioLiveCard`) que la capture.
3. **No hay punto de llamada**: falta un hook de cardio en `ProgramRepository.finalizeWorkout` que cuente las dos exposiciones y proponga.
`NativeCardioEscalation.nextOffered` y `nextCardioDurationAfterTwoConversationalExposures` (en `NativeWorkoutProgressionRuntime`) existen como funciones puras ejercitadas solo por tests.

### DEC-w1-08 — NO ENTREGADO: cláusula de adaptaciones de originales de r2 §12.4 (R16)

**Estado:** no entregado; sin consumidor. **Desde el 2026-10-02 es una desviación aceptada y parcial (DEV-r2-04): PHUL (original y adaptado) sí usará la doble progresión KPKN —ítem R16 del cierre, pendiente—; PHAT no por ahora; las adaptaciones heredan de su original.** Lo que sigue es el análisis técnico del hueco actual.

r2 §12.4: las adaptaciones de originales usan la regla del original mientras sea compatible y, cuando la sustitución rompe la base de carga, adoptan la doble progresión propia solo para esos slots, registrando el cambio. Hoy las recetas de autor y las adaptadas llevan `nativeProgression = null`, así que `NativeWorkoutProgressionRuntime` retorna de inmediato. Los datos para detectarlo existen (`PlanSlotChange.loadReferenceKept == false` en `PlanProvenance.slotChanges`, que `PlanAdaptationResolver` ya escribe), pero activarlo exige (1) que `PlanAdaptationResolver` marque en la receta adaptada los slots cuya base de carga se perdió, (2) decidir qué recetas optan y con qué `NativeProgressionSpec`, y (3) que el runtime lea esa marca por slot. No se inventó ninguno de los tres.

---

## Decisiones de la ola 2 de la curaduría de programas (2026-10-03)

Convención: `DEC-w2-NN` es una decisión del plan de curaduría de programas (`docs/audits/2026-10-programs/00-PLAN-curaduria-programas-2026-10-03.md`) que cierra un hueco o un conflicto sin contradecir r2; si lo contradijera sería un `DEV-r2-NN`. Las entradas `DEC-w2-01` a `DEC-w2-06` las registra el paquete A. Cada decisión nombra el test que la confirma o la refuta; las cifras «antes → después» salen de corridas por filtro de `testBaseDebugUnitTest` (XML JUnit) del 2026-10-03, y lo marcado «reconstrucción» es aritmética o lectura de código, no una ejecución.

### DEC-w2-07 — Los cinco nativos históricos dejan de listarse (D2, 2026-10-03)

**Estado:** implementada (paso C.P2b del paquete C); decisión del dueño del producto (D2 del plan, §3). No contradice r2. Los programas ya activados, los ids antiguos y la ruta histórica del personalizador no cambian.

**Qué dice r2.** §11.1, al cerrar la tabla de los cuatro perfiles: «Los ocho nativos de hipertrofia y `native:strength-cardio` anteriores se conservan para IDs/programas históricos y como material reutilizable. Los que cubren exactamente la misma combinación pueden agruparse en una tarjeta con variantes, conservando IDs en detalle; no duplicar contenido para inflar cobertura. `native:strength-cardio` no se convierte en Atleta completo por renombrarlo.» r2 exige conservar ids y programas, no seguir ofreciéndolos como candidatos nuevos; ocultar una entrada de las listas sin borrarla cumple ese texto.

**Decisión del dueño (D2, 2026-10-03).** Ocultar ya, en la Fase 1, `native:full-body`, `native:gym-muscle`, `native:one-day`, `native:return-training` y `native:home-training`; mantener `native:machine-muscle` y `native:bodyweight` relegados al final del orden con texto honesto (rank 600 y 610); dejar `native:strength-cardio` solo como legado, fuera del objetivo Músculo (`references = ∅`). Alternativas descartadas: relegar ahora y ocultar en la Fase 2 (era la recomendación del diseño editorial §2.2, D1, porque ocultar obliga a re-baselinear `PlanGenerationCoverageT006Test` y la matriz T-019) y mantener los ocho. Argumento de cobertura: los cuatro planes propios cubren de 1 a 6 días en sus cuatro objetivos, así que ningún histórico cubre una celda objetivo × frecuencia que el propio no cubra; lo confirma `SetupTrainingPlannerAvailabilitySweepTest` tras el cambio (8 tandas × 55 296 filas, 0 fallos y 0 filas registradas como hueco).

**Qué se hizo.**
- `PlanEditorialTable`: `listed = false` en las cinco fichas; `references = emptySet()` en `native:strength-cardio`; `references = {POWERLIFTING, POWERBUILDING}` en `protocol:wendler-531-bbb` (diseño editorial §2.4 y D6: Boring But Big aparece también en «Fuerza y músculo»; First Set Last sigue solo en powerlifting). Ninguna otra ficha toca esos dos campos (lo fija `PlanCatalogEditorialContractTest`).
- `PersonalizedPlanCatalog`: cada rama de `entries()` pasa `references = editorial.references ?: <el cálculo de siempre>` y hay un `listedEntries()` nuevo (`entries().filter { it.listed }`). `entries()` y `find()` siguen devolviendo las 55 entradas y los 12 nativos; `lookup`, `findForProgram`, los borradores y el snapshot de `PlanCandidateEvaluator` del wizard (que sigue recibiendo `entries()` completo) resuelven un histórico oculto igual que antes.
- `SetupTrainingPlanner.candidates` y la lista de `CreateProgramTemplateSheet` leen `listedEntries()` (con el filtro `PUBLISHED` de siempre).
- El modo mixto no cambia. `schedulesCardio` se decide por `source == NATIVE && sourceId == "strength-cardio"` y no por las referencias; con `mixedTraining = true` el planner salta el filtro de referencia y el evaluador solo rechaza por disciplina cuando `references` no está vacío. `candidates(mixedTraining = true)` sigue devolviendo exactamente `native:strength-cardio` con 1 a 6 días.

**Tests: cifras antes → después.**

| Test u oráculo | Antes | Después | Por qué |
|---|---|---|---|
| `PlanGenerationCoverageT006Test.Q1` (`publishedCandidates`, 62 208 entradas del planner) | 497 664 (evidencia `g24-FREEZE-full-base`; no se remidió en esta sesión) | **422 496**, `emptyPublishedRows = 0` | −62 208 los cinco ocultos (12 pares plan-frecuencia, en Músculo y en Atleta completo, × 2 592 filas); −15 552 `strength-cardio` sale de Músculo (6 frecuencias × 2 592); +2 592 BBB entra en Fuerza y músculo a 4 días. 497 664 − 62 208 − 15 552 + 2 592 = 422 496, exactamente lo medido |
| `PlanGenerationCoverageT006Test.Q2` (2 304 filas) | 1 224 viables; `{APPARATUS_ABSENT=360, TIME_BUDGET=504, PROFILE_MISMATCH=216}` | idéntico; la pasada calibrada (`sin-calibrar`, `piso`, `techo`) también da 1 224 viables y los mismos rechazos | Q2 solo evalúa el plan propio de cada objetivo, cuya ficha no cambia |
| `PersonalizedPlanCatalogTest` | 18 tests; 12 nativos en `entries()` | 20 tests; 12 en `entries()`, 7 en `listedEntries()` y la lista exacta de los 5 ocultos; nuevo: el planner no devuelve ninguno de los 5 en 1 008 combinaciones de referencia × frecuencia (1..6 y sin ella) × nivel × material × mixto × solo protocolos, BBB aparece en Fuerza y músculo a 4 días (solo a 4), `strength-cardio` no aparece en Músculo y es el único candidato mixto | decisión D2 |
| `PlanCatalogEditorialContractTest` (regla 11, antes `listed_and_the_references_override_are_inert_in_this_step`) | 21 tests; `listed` y `references` inertes | 22 tests; `listed = false` exactamente en los 5 ids, override de `references` exactamente en `native:strength-cardio` (∅) y `protocol:wendler-531-bbb` (PL + PB), 55 entradas en `entries()` y 50 en `listedEntries()` | los dos campos dejan de ser inertes |
| `NativeProfileSpecCatalogTest.historical_native_families_are_untouched` | 12 nativos y sus rangos | 12 nativos y rangos; además `listed` por familia (5 ocultas, 3 listadas), `references` (∅ en `strength-cardio`, hipertrofia en el resto), 7 nativos listados | la familia sigue entera; cambia solo cómo se ofrece |
| `SetupTrainingPlannerTest` | 9 tests; `one-day` se excluía solo porque se pedían 7 días | 11 tests; `one-day` con 1 día y los otros cuatro ocultos con cada una de sus frecuencias no son candidatos; `strength-cardio` no es candidato de Músculo en 1..6 días y sigue siéndolo del modo mixto | el test anterior no ejercía la exclusión |
| `SetupTrainingPlannerAvailabilitySweepTest` | cobertura derivada de `entries()` | cobertura derivada de `listedEntries()`; 8 tandas × 55 296 filas, 0 fallos, 0 filas registradas | un histórico oculto no puede tapar un hueco |
| `SetupExecutableAvailabilityMatrixTest`, GRUPO F | F-m20 negativa y F-m21 positiva con testigo `native:full-body` (piso 21 min) | F-m20 negativa y F-m28 positiva con testigo `native:muscle-foundation-v2` (piso 28 min) | ver el párrafo siguiente |

**GRUPO F de T-019 (testigo `native:full-body` → `native:muscle-foundation-v2`).** En la fila F (Músculo, principiante, 11 categorías declaradas, 3 días) el planner publica ahora tres candidatos: `native:machine-muscle`, `native:muscle-foundation-v2` y `native:bodyweight`. A 20 min los tres rechazan con `TIME_BUDGET` tipado (mínimos de 21, 28 y 21 min, medidos con el motor), así que la fila sigue siendo un negativo limpio. El piso del plan propio no es el 21 del generador histórico: con material, el calendario FA/FB/FA de r2 §11.3 deja, tras retirar I y C (r2 §12.3, paso 2), cuatro H por día de cuatro familias de patrón distintas, y el estimador común mide 180 + 4 × (60 + 2 × 48 + 120) + 4 × 90 = 1644 s = 27,4 → **28 min**. Medido: de 20 a 27 min el plan informa `TIME_BUDGET` con 28; con 28 min llega a un programa de seis semanas cuya sesión más larga mide 28 (las seis sesiones de las dos primeras semanas dan 1644 s: preparación 240, ejecución 384, descansos 480, calentamiento y aproximaciones 540); con 30 min recupera el core y el gemelo. La corrida de `T019_F` con el ViewModel real lo confirma: F-m20 es un negativo limpio (publica `native:machine-muscle`, `native:muscle-foundation-v2` y `native:bodyweight`, sin tarjetas) y F-m28 llega al programa con `native:muscle-foundation-v2` como testigo, 15 sesiones de 28 min y las 3 de la semana de descarga de 17 min. Se aplicó el caso «piso mayor que 20»: F-m20 queda negativa con `requiredMinutes == 28` y F-m28 (antes F-m21) es la positiva en el piso, con una función independiente nueva, `independentOwnMusclePlanFloorMinutes()`, derivada de la aritmética del estimador como `independentBodyweightMuscleFloorMinutes` (sustituye a `independentHistoricalFullBodyFloorMinutes`; la constante `HISTORICAL_FULL_BODY_ID` se eliminó). La función no nombra ningún ejercicio, solo la estructura de FA/FB: no depende del desempate del catálogo, pero sí de que cada día retenga cuatro familias de patrón; si el paquete B cambia los arquetipos o las dosis, el oráculo debe cambiar con ellos. Los contadores de la matriz no cambian (25 filas originales: A12, B6, C2, D2, E1 y F2).

**Pendiente y riesgos.**
- En esta decisión solo se ejecutó el GRUPO F de la matriz T-019. Los demás grupos (A, A20, A21, B, C, D, E, T027, T001 y T006-Q4) comparten las aserciones de siempre, pero ocultar los históricos cambia qué candidato queda primero en el orden del planner (reconstrucción: `native:full-body` y `native:home-training` pasan a `native:machine-muscle`, `native:bodyweight` o el plan propio, según el material), y por tanto qué plan hace de testigo en las filas que no exigen uno concreto. Conviene una corrida completa de la matriz antes del commit.
- Un borrador guardado con un histórico oculto seleccionado conserva esa selección: el wizard pasa `find(id) != null` como `planExists` de `SetupDraftCompatibility.applyCatalogRevision`, que sigue siendo verdadero, y el evaluador lo sigue evaluando por id; lo que cambia es que ya no aparece en la lista de candidatos que se calcula para ese borrador. Si se quiere marcar esa selección como revisable, es un cambio de C.P6 (preselección) o del paquete A.
- En la biblioteca, el filtro «Músculo» deja de listar `native:strength-cardio` (cae en «Otros») y «Fuerza» y «Fuerza y músculo» listan BBB, por el cambio de `references`.
- El orden del planner sigue siendo por nivel, tamaño del material pedido e id: para un principiante con máquinas, `native:machine-muscle` (relegado, rank 600) precede a `native:muscle-foundation-v2` (así sale la lista de la fila F). Lo corrige el comparador por rank editorial de C.P3; esta decisión no toca el orden.
- Efecto colateral sin consecuencias de producto: `PlanCandidateEvaluator.coverageOf` deriva `hasHypertrophy` de `references`, así que `native:strength-cardio` pasa a `hasHypertrophy = false`; esa cobertura solo se usa en la puerta de Atleta completo, donde `strength-cardio` ya no era apto (sin fuerza ni potencia).
- El KDoc de `PlanEditorial.references` y `PlanEditorial.listed` (`PlanEditorial.kt`) sigue diciendo «En este paso SIEMPRE null» y «SIEMPRE true»; este paso no lo toca por estar fuera de su alcance y queda para el siguiente cambio de C.
- Ninguna prueba de `androidTest` usa un histórico oculto como testigo: `SetupWizardFullJourneyUiTest` espera `native:powerbuilding-foundation-v2` y `native:muscle-foundation-v2`.

### DEC-w2-01 — Contrato de cobertura centrado en el plan propio, con reparación de un toque (2026-10-03)

**Estado:** el contrato (`PlanCoverageContractTest`, paso A2) está commiteado desde 5849c32f0 con los techos del baseline; los pasos A.B1, A.B2, A.B3 y A.B7 están implementados (este registro) y bajan esos techos. El resto del paquete A (B4–B6, C1–C4, D1–D4, E1–E2) sigue pendiente: el contrato **no** está en 0. No contradice r2: §15.2 pide motivos cerrados (`APPARATUS_UNKNOWN` distinto de `APPARATUS_ABSENT`) y que «Falta confirmar hack» lleve al panel preciso; §13.2 dice que el usuario «puede continuar con desconocidos»; §11.1 exige para Fuerza barra/carga, rack y banco **confirmados** y define Fuerza y músculo como «Barra o mancuernas».

**Qué dice el contrato.** Para todo input alcanzable (4 objetivos × 3 niveles, con «nuevo» ≡ «retomando» × 1–6 días × {20, 30, 45, 60, 90} min × 19 fixtures de material E0–E18; Atleta además × cardio {caminar, correr, bici} × {10, 15, 20, 30} min: 25 650 filas) el plan propio del objetivo queda `Ready` o es un rechazo **honesto** y existe una reparación de un toque que lo deja `Ready`. Honesto = `APPARATUS_UNKNOWN` cuyos requisitos resuelven todos una llave confirmable del panel (C2); `APPARATUS_ABSENT` con evidencia explícita (la barra como categoría sin marcar, o rack y banco negados en todas las llaves que los acreditan); `PROFILE_MISMATCH`; `TIME_BUDGET` con `requiredMinutes` en (minutos, 100]. `COMPOSITION`, `INTERNAL_MATERIALIZATION`, `CATALOG_NOT_READY` y `UNRESOLVED_CONFIGURATION` son violación siempre. Las reparaciones que el test simula son `ConfirmApparatus`, `SetMinutes`, `SetCardioMinutes` y `SwitchGoal`, con un `SetMinutes` encadenado cuando el destino solo falla por tiempo; `ClearSplit` espera a A.E2. C3 (UI y test comparten `PlanRejectionPresenter.primary`) y C4 (el pase «a peso corporal» solo si todos los rechazos son de aparatos) esperan a A.C4 y A.D4. C5: con material utilizable (kettlebell, Smith y banco, banda y barra de dominadas, máquinas) todo `Ready` de Músculo y Atleta usa al menos una configuración de ese material (hoy falla en kettlebell y Smith: paso B5).

**Tiers y ratchet.** `KPKN_COVERAGE_TIER=smoke` (por defecto, 1/16 de las filas), `ci` (un shard de 4 por hash, `KPKN_COVERAGE_SHARD`) o `full` (todas; ≈ 8 min con 4 hilos). Los techos `VIOLATION_CEILING` (smoke, ci/0–ci/3 y full) solo bajan: la suite está siempre en verde y solo falla si una corrida supera su techo; cuando una corrida da menos, imprime «techo bajable a N». El informe queda en `android-native/app/build/reports/coverage-contract/<tier>.txt`.

**Qué se hizo (cada cambio de semántica con su test).**
- **B1, plomería de requisitos.** `PlanMaterializationException`, `PlanCandidateEvaluation.Rejected`, `PersonalizationReport` y `SetupCandidateRejection` ganan `missingRequirements: List<String>` (al final, con lista vacía por defecto). `NativePlanFailureMapper.typedFailure` gana la rama `APPARATUS_UNKNOWN` (etapa MATERIAL) y las dos ramas de aparatos pasan la lista. `SetupApparatusPanel.keyForToken` y `categoriesFor` convierten un token en la llave curada del panel y en las categorías que esa llave necesita. El ViewModel saca la llave confirmable de `missingRequirements` y solo lee el texto del mensaje cuando la lista viene vacía (ruta heredada de `SetupCandidateFailureException`). Tests: `NativePlanFailureMapperTest`, `PlanCandidateEvaluatorTest`, `NativeMaterialEvidenceTest` y `SetupWizardActivationGateTest.apparatusRejectionsTakeTheConfirmableKeyFromMissingRequirementsInsteadOfTheMessage`.
- **B2, gates con evidencia.** Fuerza y Fuerza y músculo calculan la evidencia (`PRESENT`, `ABSENT` o `UNKNOWN`) de `barbell`, `rack`, `bench` y `dumbbells` con el mismo criterio que `PlanAdaptationResolver.evidenceOfKind` aplica a las recetas fijas: los soportes del vocabulario del panel usan `requirements`; una categoría sin marcar con la disponibilidad confirmada es una ausencia declarada; y sin disponibilidad (ruta legacy) lo que no consta no está, de modo que `general_gym` a secas sigue sin acreditar barra, rack ni banco. **Fuerza:** algún requisito `ABSENT` → `APPARATUS_ABSENT` y la lista trae solo lo negado («Falta barra y carga, rack.»); si no, algún `UNKNOWN` → `APPARATUS_UNKNOWN` («Falta confirmar si tienes rack y banco.»). **Fuerza y músculo:** barra con rack y banco acreditados → ruta barra; mancuernas → ruta mancuernas, sin preguntar; solo cuando no hay ninguna de las dos se distingue `APPARATUS_UNKNOWN` («Tienes barra, pero falta confirmar si tienes rack y banco para los principales.»: hay barra y nadie negó rack ni banco; si solo uno de los dos está sin responder, el texto nombra solo ese) de `PROFILE_MISMATCH` (sin resistencia externa, o con rack o banco negados). Los mensajes de aparatos (`APPARATUS_ABSENT` y `APPARATUS_UNKNOWN`) no llevan «:»: la ruta heredada (`SetupWizardViewModel.missingEquipmentTokens`) lee lo que sigue al primer «:» como lista de tokens, y `NativeMaterialEvidenceTest` lo comprueba en cada rechazo de aparatos. Tests: `NativeMaterialEvidenceTest`.
- **B7, evidencia ANY → ALL.** Un soporte es `ABSENT` solo si TODAS las llaves que lo acreditan están `ABSENT`. Solo cambia `bench` (lo acreditan el banco plano y el regulable): «banco plano = No» con el regulable sin responder deja `bench` en `UNKNOWN`, no en `ABSENT`; `rack`, `dip_bars`, `pull_up_bar` y `low_bar_support` tienen una sola llave y no cambian. Es la misma regla que ya aplicaba la categoría («con TODAS sus claves ausentes pierde su token»). Tests: `EffectiveEquipmentResolverContractTest.requirements_are_absent_only_when_every_attesting_key_is_denied`, `NativeMaterialEvidenceTest` y `SetupWizardActivationGateTest` (re-baseline en la tabla de abajo).
- **B3, máquinas y tirón.** `hasExplicitMachinePresence` (solo llaves de MÁQUINAS o POLEAS) sustituye a `hasExplicitPresence` en las dos líneas de `requireExactMachineConfiguration`: es la desviación DEV-r2-06 (entrada siguiente). `pullAvailable` deja de ser una lista de tokens y pasa a ser «existe una configuración resoluble de remo o de jalón para este material y este nivel» (`resolveConfiguration(R, H)` o `(V, H)`); por eso `resolveConfiguration` y lo que necesita se declaran antes de elegir el calendario. Consecuencia: un principiante con SOLO barra de dominadas (E13) ya no tiene tirón (r2 §13.3: no recibe dominadas por defecto, y sin barra baja no hay remo), así que usa los calendarios «sin tirón» de DEV-r2-01 en vez de un calendario con remos imposibles de resolver (antes terminaba en `COMPOSITION`); amplía el ámbito de DEV-r2-01, que listaba la barra de dominadas entre los materiales con tirón (la misma regla deja sin tirón a quien declara máquinas concretas sin remo ni jalón; ver la actualización del 2026-10-03 de esa desviación). Se conserva el tirón con banda (E1), con barra de dominadas y barra baja (E7) y, en nivel intermedio o avanzado, con solo barra de dominadas (E13); sin ningún material (E0) el calendario «sin tirón» es el mismo de siempre. Tests: `NativeMaterialEvidenceTest`.

**Decisión de producto dentro de B2: con mancuernas, Fuerza y músculo no se bloquea por rack y banco sin confirmar.** El brief del paso ordenaba preguntar por rack y banco en cuanto hubiera barra y ninguno negado, también cuando hay mancuernas (fixture E17: barra, mancuernas y bancos). Se descartó: la misma regla convierte en `APPARATUS_UNKNOWN` al gimnasio sin confirmar (E8), y el caso de bloqueo T-001 de la matriz (gimnasio completo + Fuerza y músculo + 5 días, 60 min) tiene como único candidato viable el plan propio (los PHAT quedan rechazados por «bench»; evidencia `g24-FREEZE-full-base`), así que lo dejaría sin ningún plan. Además r2 §11.1 define Fuerza y músculo como «Barra o mancuernas» y §13.2 permite continuar con desconocidos y recibir planes propios. La pregunta por rack y banco solo se hace cuando la ruta de mancuernas no existe (barra sola, E16).

**Cifras antes → después (rejilla de 25 650 filas, corrida `full` del 2026-10-03; 4 hilos, 421 s).**

| Clase de violación | Antes | Después | Qué cambió |
|---|---|---|---|
| `NOT_HONEST` (`COMPOSITION`) | 195 | **120** | −75: principiantes con solo barra de dominadas (E13), Músculo de 4 a 6 días (15 filas) y Atleta de 1 día (60): pasan al calendario «sin tirón» (B3). Quedan las 120 de Atleta de 1 día, intermedio y avanzado, con E13 (ver «Pendiente») |
| `DISHONEST_ABSENT` | 270 | **0** | Fuerza con E8 (gimnasio sin confirmar), E16 (barra sola) y E17 (barra, mancuernas y bancos, sin rack) deja de ser «declaraste ausente» y pasa a `APPARATUS_UNKNOWN` (B2) |
| `TIME_BUDGET_OUT_OF_RANGE` | 0 | 0 | |
| `TIME_BUDGET_INEXACT` | 1 578 | 1 578 | sin cambio (paso A.C2) |
| `NO_REPAIR` | 30 | **0** | eran Fuerza y Fuerza y músculo con E13 principiante (15 + 15): su destino «Músculo» ya no cae en `COMPOSITION` |
| `MATERIAL_UNUSED` | 1 464 | 1 464 | sin cambio (paso A.B5: kettlebell y Smith) |
| `EXCEPTION` | 0 | 0 | |
| **Total** | **3 537** | **3 162** (−375) | |

Techos nuevos (`VIOLATION_CEILING`): smoke 226 → 200; ci/0 887 → 790; ci/1 885 → 787; ci/2 886 → 797; ci/3 879 → 788; full 3 537 → 3 162. Detalle de la corrida: `Ready` 13 590 → 13 624 (+34); rechazos honestos antes de reparar 11 595 → 11 906 (`APPARATUS_ABSENT` 1 080 → 1 080, `APPARATUS_UNKNOWN` 0 → 360, `PROFILE_MISMATCH` 900 → 810, `TIME_BUDGET` 9 615 → 9 656); primera reparación que logra `Ready`: `SetMinutes` 9 615 → 9 656, `SwitchGoal` 1 581 → 1 532, `SwitchGoal+SetMinutes` 369 → 358, `ConfirmApparatus` 0 → 315 y `ConfirmApparatus+SetMinutes` 0 → 45. Las 75 filas de E13 principiante repartidas así: 34 pasan a `Ready` y 41 a `TIME_BUDGET` honesto; los totales cuadran con que solo cambian esas. `PlanGenerationCoverageT006Test` Q2 queda idéntico (2 304 filas, 1 224 viables, `{APPARATUS_ABSENT=360, TIME_BUDGET=504, PROFILE_MISMATCH=216}`, y también en las tres pasadas calibradas): sus ocho fixtures E0–E7 no cruzan ninguna de las reglas nuevas.

**Re-baselines.**

| Test | Aserción antes | Después | Por qué |
|---|---|---|---|
| `EffectiveEquipmentResolverContractTest.requirements_distinguish_absent_from_unknown` | solo `bench_flat = ABSENT` → `requirements["bench"] == ABSENT` y `"bench" in missingRequirements` | se niegan `bench_flat` y `bench_adjustable` para obtener `ABSENT`; el caso de un solo banco negado pasa al test nuevo `requirements_are_absent_only_when_every_attesting_key_is_denied` (→ `UNKNOWN`) | B7: ANY → ALL |
| `SetupWizardActivationGateTest.apparatusRejectionReportsDefinitiveAbsenceWhenOtherRequirementsAreUnknown` | solo `bench_flat = ABSENT` → `apparatusReason("… bench, machine")` = `APPARATUS_ABSENT` | se niegan los dos bancos para `APPARATUS_ABSENT`; con solo el plano negado la aserción nueva espera `APPARATUS_UNKNOWN` | B7: el regulable sin responder deja el banco por confirmar |
| `PlanCoverageContractTest` | `APPARATUS_UNKNOWN` honesto sin condición; `ConfirmApparatus` fijo a rack + banco; `APPARATUS_ABSENT` honesto por cualquier llave negada o por la categoría exigida por el objetivo | `APPARATUS_UNKNOWN` honesto solo con `missingRequirements` que resuelven una llave del panel; `ConfirmApparatus` confirma exactamente esas llaves (+ sus categorías) y encadena un `SetMinutes` si solo falla por tiempo; `APPARATUS_ABSENT` honesto solo con evidencia explícita por requisito; techos nuevos | B1, B2 y B7 |

Ninguna fila de `SetupExecutableAvailabilityMatrixTest` cambió: la clase completa da 17 pruebas verdes y 43 filas (37 positivas, 6 negativas) sin problemas, T-001 incluida.

**Pendiente.** B4–B6 y C1–C4: el `PlanRepairAdvisor` (C1) debe reutilizar `keyForToken` y `categoriesFor` para `ConfirmApparatus` y encadenar `SetMinutes` cuando el plan, ya con el material confirmado, solo falle por tiempo (hoy lo hace la simulación del test); las recetas fijas (`AuthoredPlanMaterializer`) aún no llenan `missingRequirements`, así que su llave sigue saliendo del texto del mensaje hasta B6 (que también decide `needsApparatusConfirmation = UNKNOWN && apparatusKey != null`; hoy un rechazo `APPARATUS_ABSENT` por la barra ofrece «Confirmar material» sin llave porque la barra es una categoría); `evaluateSelectedCandidate` no copia `missingRequirements` al relanzar un rechazo guardado, pero solo se usa su mensaje. Atleta de 1 día, intermedio o avanzado, con solo barra de dominadas (E13): 120 filas siguen en `COMPOSITION` («Atleta: 3 series H en la semana < 4»). `pullAvailable` es verdadero por el jalón con dominada (V), pero el calendario de 1 día (X1) solo lleva remo (R), que con esa barra no se resuelve, y el día queda con 3 series H; ya fallaba antes (180 filas, todos los niveles). Lo resuelven las tablas de candidatos (B5) o un calendario con V, no este paso.

### DEV-r2-06 — Máquinas por categoría en los planes propios cuando solo se confirmaron soportes (A.B3, 2026-10-03)

**Estado:** implementada (paso A.B3 del paquete A). **Desviación de r2** registrada por el plan de curaduría de programas (§6, «Documentar en `docs/WIZARD_PLAN_DEVIATIONS.md`»), aprobado el 2026-10-03.

**Nota de numeración:** el plan 00 (§3 D4 y §6) usa DEV-r2-06 para dos decisiones; la onda de RIR de D4 (Fase 2) se registrará como DEV-r2-07. El plan aprobado no se edita desde el repo.

**Qué texto de r2 relaja.** §13.3, al cerrar el tratamiento de las once categorías: «Máquinas confirmadas se asignan a sus slots exactos; desconocidas no se usan.»; y §13.1 (consumidores obligatorios): «El nativo deja de aceptar máquina genérica donde el fijo exigiría precisión.» Se conserva la regla 5 de §13.1 («Emitir `machine_config:<id>` solo desde mapeo curado o inventario exacto, jamás porque se marcó `MACHINES`»), y los planes fijos y adaptados (PHUL, PHAT) siguen exigiendo la configuración exacta.

**Qué hacía el código y qué hace ahora.** `SimpleCyclePersonalizer.equipmentAllows` tiene un modo «configuración exacta» (`requireExactMachineConfiguration`): con él, una máquina (equipmentId `machine`) solo entra si su token `machine_config:<id>` está acreditado por una llave `PRESENT` del panel; sin él vale la categoría `machine` marcada, sin afirmar ninguna configuración concreta. Antes el modo se activaba con CUALQUIER presencia declarada (`EquipmentAvailability.hasExplicitPresence`: máquinas, poleas, bancos, rack, barra de dominadas, paralelas e incluso la bici exterior). Ahora solo se activa con la presencia (Sí o No) de una llave de MÁQUINAS o POLEAS (`hasExplicitMachinePresence`, en `EffectiveEquipmentCatalog.kt`); confirmar un banco, el rack, la barra de dominadas o la bici ya no apaga las máquinas por categoría. No cambia nada con disponibilidad puramente categórica (que ya usaba las máquinas por categoría) ni con inventario declarado sin disponibilidad nueva.

**Por qué.** Con la regla anterior, contestar una sola pregunta de soportes empeoraba el plan: un gimnasio con rack y banco confirmados y las máquinas sin responder (fixture E5 del contrato de cobertura) quedaba en modo exacto sin ninguna máquina acreditada, mientras que el mismo gimnasio sin contestar nada usaba las máquinas por categoría. Y quien solo tiene máquinas y un banco veía declarado imposible el tirón aunque su gimnasio tuviera remo y jalón en máquina. r2 se contradice a sí mismo en este punto: el mismo §13.3 pide «No ignorar equipo utilizable reduciendo todos esos casos a un plan corporal genérico» para KETTLEBELL, SMITH, CABLE, MACHINES y BALL sin barra ni mancuernas. Se resuelve a favor de usar la categoría que la persona sí confirmó.

**Efecto medido.** `NativeMaterialEvidenceTest.machines_by_category_feed_the_pull_of_own_plans_unless_a_concrete_machine_is_declared`: Músculo con las categorías MÁQUINAS y SOPORTES más un banco plano (3 días, 90 min, intermedio) incluye el remo o el jalón en máquina y no lleva la nota «Sin banda o barra de apoyo»; si además se declara la prensa (una máquina concreta, modo exacto), ese remo y ese jalón no entran y el plan queda sin tirón. En la rejilla del contrato (E0–E18), E5 y E15 (todas las categorías con soportes confirmados o negados y ninguna máquina declarada) son los únicos fixtures con la categoría MÁQUINAS marcada que pasan del modo exacto al categórico. Sus resultados en Fuerza, Músculo y Fuerza y músculo no cambian en conteo (E5: 81 `Ready` y 9 `TIME_BUDGET`, 75 y 15, 72 y 18, igual que antes), pero el plan puede cambiar en un punto: el gemelo. El slot G lista `calf_raise__bilateral__machine` antes que la reserva corporal en `NativeCandidateTable.candidatesFor(G)` (`NativeProfileSpec.kt`, ~l.304), así que con soportes contestados y máquinas sin contestar el gemelo pasa de la reserva corporal a la máquina por categoría; el resto de máquinas van detrás de barra, mancuernas y polea en las tablas, así que no entran. El cambio solo se ve donde el plan conserva el gemelo, que es un accesorio opcional (`G:I`): el fitter MRV no distingue por músculo (reserva 3 de DEV-r2-01) y retira los accesorios opcionales cuando algún músculo pasa su MRV. Medido el 2026-10-03: Músculo con E5 (3 días, 90 min, intermedio) no lleva ningún gemelo, ni la máquina ni la reserva corporal; que sea el fitter quien lo retira es una inferencia por lectura del código, no una medición. Lo fija `NativeMaterialEvidenceTest.calf_moves_to_the_machine_by_category_when_only_supports_were_confirmed`, que recorre Músculo con 2 a 4 días, principiante e intermedio, a 90 min: con E5 y con E15 el gemelo, donde sobrevive, es siempre `calf_raise__bilateral__machine` y nunca la reserva corporal; con E18 (solo la prensa, que no acredita el gemelo de pie, modo exacto) es siempre `calf_raise__bilateral__bodyweight`. La prueba exige además que en alguna celda de cada uno de los tres fixtures el gemelo sobreviva, para que esas exclusiones no sean vacuas; pasó el 2026-10-03 (22 de 22 tests de la clase en verde). Fuerza y Atleta completo (con tirón) no llevan gemelo en su calendario, y Fuerza y músculo lo lleva desde 4 días por el mismo mecanismo (por lectura de las tablas, sin prueba propia). E3, E4, E7, E11, E12, E13 y E17 también cambian de modo (confirman soportes: bancos, rack, barra de dominadas o barra baja), pero sin efecto: no marcan la categoría MÁQUINAS, así que ninguna máquina podía entrar en ninguno de los dos modos. Fuera de ese gemelo, el efecto está en quien confirma soportes y no tiene barra ni mancuernas, como en la prueba de arriba.

**Alternativas descartadas.** (1) Mantener el modo exacto ante cualquier presencia, la lectura literal de §13.1 y §13.3: castiga a quien contesta la pregunta de soportes y no cumple el «no ignorar equipo utilizable». (2) No usar nunca máquinas por categoría, también literal: quitaría una ruta que la disponibilidad categórica pura ya tenía y dejaría sin tirón a quien solo marcó MÁQUINAS.

**Compatibilidad.** Sin migración: no cambia recetas ni programas ya activados, solo lo que el generador puede elegir al crear un plan propio nuevo con esa disponibilidad. Los ocho nativos históricos usan el mismo `hasExplicitMachinePresence` (`SimpleCyclePersonalizer.kt`, ~l.271), así que `native:machine-muscle` (listado, relegado) pasa a ser candidato viable en E5 y E15: antes, con los soportes confirmados, el modo exacto no le dejaba ninguna máquina (reconstrucción por lectura del código, no una ejecución).

**Qué lo confirma.** `NativeMaterialEvidenceTest.explicit_machine_presence_counts_only_machine_and_cable_keys`, `machines_by_category_feed_the_pull_of_own_plans_unless_a_concrete_machine_is_declared` y `calf_moves_to_the_machine_by_category_when_only_supports_were_confirmed`.

### DEC-w2-06 — Ranking editorial del planner y matcher de objetivo compartido (A.D1 + C.P3, 2026-10-03)

**Estado:** implementada (paso A.D1 del paquete A y paso C.P3 del paquete C); decisión del plan de curaduría de programas (§6, «Documentar en `docs/WIZARD_PLAN_DEVIATIONS.md`»). No contradice r2: concreta su regla de ordenación. No cambia lo que se ofrece por nivel ni por clase de plan (DEC-w1-04), el material real sigue decidiéndose en la materialización y los cinco históricos ocultos de DEC-w2-07 siguen sin ofrecerse.

**Qué dice r2.** §15.3: «ordenar primero propios […], luego originales/adaptados […] según preferencias/nivel, sin ocultar los originales». r2 pide ordenar por nivel y preferencias; no fija el comparador ni dice que un plan de otra disciplina sea candidato del objetivo.

**Qué hacía el código.**
- `SetupTrainingPlanner.candidates` ordenaba por (disciplina pedida, `level == input.level`, tamaño de `requiredEquipment`, id). `level` es una sola etiqueta (el menor de los niveles de la ficha), el tamaño del material no dice nada del plan y el id desempata por alfabeto. Con el catálogo de producción: para Músculo, principiante y 3 días, `native:machine-muscle` (rank 600) precedía a `native:muscle-foundation-v2` (fila F de la matriz, DEC-w2-07); con intermedio, `native:bodyweight` iba el primero (por eso `SetupWizardFullJourneyTest` elegía un plan que pide barra de dominadas y apoyo); en Fuerza y músculo, intermedio y 4 días, el plan propio quedaba detrás de los cuatro métodos de su nivel (PHUL original, adaptado y versión anterior, y BBB); y en Fuerza el complemento de Coan y las especializaciones de Smolov se mezclaban con los planes completos por orden alfabético.
- Atleta completo (referencia `null`) evaluaba todo el catálogo y cada plan de otra disciplina devolvía un `PROFILE_MISMATCH` trivial que encabezaba la lista de rechazos.
- La biblioteca (`CreateProgramTemplateSheet.profileMatches`) decidía por «capacidad O referencia»: «Fuerza» listaba `native:powerbuilding-foundation-v2` solo porque declara la capacidad de fuerza, y «Músculo» y «Fuerza y músculo» hacían lo mismo con la suya.

**Qué se hizo.**
- *Comparador editorial* (`SetupTrainingPlanner.candidates`): (1) la entrada declara la disciplina pedida; (2) `input.level in editorial.levels`; (3) clase de plan, PLAN antes que ESPECIALIZACION, COMPLEMENTO y ESTRUCTURA (posición explícita con un `when` exhaustivo, no el `ordinal` del enum); (4) `rank`; (5) id como último desempate. Desaparecen `level == input.level` y `requiredEquipment.size`. Nada se oculta: el nivel y la clase solo ordenan. La clase pesa menos que el nivel (es el orden del plan 00, A.D1): para una persona avanzada Smolov, Smolov Jr y Coan cierran su grupo de nivel, delante de los planes de otro nivel; para el resto cierran la lista.
- *Prefiltro de capacidades*: `SetupTrainingPlannerInput.requiredCapabilities` (por defecto vacío) y filtro `capabilities ⊇ requiredCapabilities`. El wizard pasa `PlanGoalMatcher.requiredCapabilities(goalProfileOf(draft))`: solo Atleta completo exige capacidades (fuerza, hipertrofia, potencia y cardio) y deja como único candidato `native:complete-athlete-v2`, la única entrada que las declara. La puerta de Atleta del evaluador (`coverage.completeAthlete`, calculada sobre la receta) no cambia: el prefiltro solo evita evaluar lo que no puede pasarla.
- *Exención de enfoque*: ya no esconde las siete plantillas con receta fija (la semana está escrita en la receta; el enfoque no la cambia). Las tres plantillas simples siguen dependiendo del enfoque; los protocolos ya estaban exentos.
- *`PlanGoalMatcher`* (nuevo, puro): STRENGTH ⇔ `POWERLIFTING ∈ references`; MUSCLE ⇔ `HYPERTROPHY`; STRENGTH_MUSCLE ⇔ `POWERBUILDING`; COMPLETE_ATHLETE ⇔ las cuatro capacidades; LEGACY_MIXED ⇔ `schedulesCardio`; LEGACY_HEALTH ⇔ siempre (el wizard no le da disciplina propia: su referencia sale del estilo de la calibración). La biblioteca lo usa en `profileMatches` y «Otros» pasa a ser «ninguno de los cuatro objetivos». Reconstrucción por lectura del código: «Fuerza» pasa de 31 a 29 entradas (salen `native:powerbuilding-foundation-v2` y `native:complete-athlete-v2`), «Músculo» de 14 a 12, «Fuerza y músculo» de 10 a 9, y «Atleta completo» (1) y «Otros» (`native:strength-cardio` y las tres estructuras) no cambian.
- Texto de cabecera de la biblioteca: «Elige un plan para ver cómo funciona. Antes de activarlo confirmamos tu material, tus días y tu tiempo.»

**Tests: cifras antes → después** (corridas por filtro de `testBaseDebugUnitTest`, 2026-10-03).

| Test u oráculo | Antes | Después | Por qué |
|---|---|---|---|
| `PlanGenerationCoverageT006Test.Q1` (`publishedCandidates`, 62 208 entradas del planner) | 422 496 (DEC-w2-07) | **194 400**, `emptyPublishedRows = 0` | Atleta completo pasa de 94 candidatos (suma de las seis frecuencias: 9, 10, 16, 35, 14, 10) a 1 por frecuencia: −2 592 × (94 − 6) = −228 096, y 422 496 − 228 096 = 194 400, exactamente lo medido. Los otros tres objetivos no cambian (69 pares plan-frecuencia) y el testigo propio de cada perfil sigue publicado. Q1 solo imprime el contador: ningún oráculo cambió |
| `PlanGenerationCoverageT006Test` Q2, Q2 positivos obligatorios, Q2 calibrada y Q3 | 1 224 viables de 2 304 con `{APPARATUS_ABSENT=360, TIME_BUDGET=504, PROFILE_MISMATCH=216}`; 58 positivos; Q3 de 328 filas con 310 viables | idéntico | solo evalúan el plan propio de cada objetivo, cuya ficha y receta no cambian |
| `SetupTrainingPlannerTest` | 11 tests | 14 tests: capacidades requeridas vacías = sin filtro, solo pasan las entradas con todas las requeridas (y la disciplina se aplica encima) y la plantilla con receta fija sobrevive a un enfoque | casos nuevos de A.D1; ninguna aserción previa dependía del orden |
| `SetupTrainingPlannerRankingTest` (nuevo) | — | 14 tests: Fuerza principiante y avanzado, el plan propio primero en cada disciplina, Músculo sin ocultos, Fuerza y músculo (intermedio a 4 días y avanzado a 5), Atleta con las cuatro capacidades en 1..6 días, el nivel solo ordena, exención de enfoque y la propiedad de orden editorial sobre 672 listas (con un oráculo propio de la prueba) | fija el comparador |
| `PlanGoalMatcherTest` (nuevo) | — | 14 tests: los cuatro propios casan solo con su objetivo; `powerbuilding-foundation` no casa con Fuerza; BBB casa con Fuerza y con Fuerza y músculo; `strength-cardio` solo con el mixto heredado; reparto de las 55 entradas (solo `strength-cardio` y las tres estructuras no casan con ningún objetivo) y el wizard y la biblioteca ofrecen lo mismo para cada objetivo y cada frecuencia | fija la regla compartida |
| `SetupExecutableAvailabilityMatrixTest`, GRUPO F (F-m20 y F-m28) | el planner publicaba `native:machine-muscle`, `native:muscle-foundation-v2`, `native:bodyweight` (DEC-w2-07) | `native:muscle-foundation-v2`, `native:machine-muscle`, `native:bodyweight` (rank 110, 600 y 610); F-m20 sigue siendo un negativo limpio (`TIME_BUDGET`, mínimo 28 min) y F-m28 llega al programa con el propio de testigo | solo cambia el orden; ningún oráculo de la matriz se tocó |
| `SetupExecutableAvailabilityMatrixTest`, fila T001 (Fuerza y músculo, intermedio, 5 días, gimnasio completo) | `adapted:phat-kpkn-r1`, `native:powerbuilding-foundation-v2`, `original:phat-biolayne-2016-r1`, `protocol:phat-verified` (reconstrucción por id) | `native:powerbuilding-foundation-v2`, `original:phat-biolayne-2016-r1`, `adapted:phat-kpkn-r1`, `protocol:phat-verified` (rank 120, 210, 230 y 991); el único viable sigue siendo el propio | ranks editoriales |
| `SetupExecutableAvailabilityMatrixTest` (17 tests: A, A20, A21, B, C, D, E, F, T027, T001, T001-03 y T006-Q4) | verde | 17 de 17 | el único cambio del archivo: `publishedEntryIdSet` pasa el mismo prefiltro de Atleta que el ViewModel, así la evidencia de «publicadas» es la lista que el ViewModel evalúa de verdad (antes era un superconjunto) |
| `SetupTrainingPlannerAvailabilitySweepTest` | 8 tandas, 0 fallos | 8 tandas, 0 fallos | la membresía de candidatos no cambia: el nivel y la clase solo ordenan |
| `PersonalizedPlanCatalogTest` (20), `PlanCatalogEditorialContractTest` (22), `NativeMixedFrequencyContractTest` (10), `PlanCoverageContractTest` (2), `SetupWizardActivationGateTest` (3), `SetupWizardAuthoredPlansTest` (4) y `T004FirstRejectionDiagnosticTest` (1) | verdes | verdes, sin tocarlos | comprueban membresía, no orden |
| `SetupWizardFullJourneyTest` (2 tests) | `fullJourneySelfDefinedNutritionCommitsExecutableProgramAndDurableTargets` rojo: el primer nativo viable era `native:bodyweight` y la reanudación se quedaba en EQUIPMENT | viables `[native:muscle-foundation-v2, native:machine-muscle, native:bodyweight]`: elige el plan propio, reanuda, recorre nutrición y Rings y activa el programa (con el rollback y el reintento del commit). **Sigue rojo, pero más adelante y por otra causa**: `assertCommittedFullJourney`, «la bolsa se aplicó al generar» (plan=`native:muscle-foundation-v2`, bolsa aplicada `{}`, `NOT_APPLIED_BAG_MISMATCH`: «El programa no registra ninguna bolsa de orden aplicada al generarlo.») | los planes propios todavía no persisten `planOrderPriorities` (`SimpleCyclePersonalizer.nativeSkeleton`; paso A.E1, pendiente), mientras que los históricos sí lo hacen. El ranking no puede arreglarlo; elegir explícitamente `native:muscle-foundation-v2` tampoco, porque ya es el elegido. El otro test de la clase (solo seguimiento) pasa |

**Relación con otras decisiones.**
- DEC-w1-04 (los planes de autor no se filtran por nivel): se conserva. El nivel solo reordena; `SetupTrainingPlannerRankingTest.theLevelOnlyOrdersAndNeverChangesTheMembership` lo comprueba para las cuatro referencias, las siete frecuencias y dos enfoques.
- DEC-w1-06 (las adaptaciones van tras los propios y los originales): ahora lo garantiza el `rank` (propios 100–130, originales 200–210, adaptaciones 220–230). El orden estable de `SetupWizardViewModel.collectViable` (`sortedBy { ADAPTED → 1 }`) pasa a ser redundante, pero no del todo inocuo: sigue empujando las adaptaciones por detrás de BBB y de las versiones anteriores de PHUL y PHAT (rank 990 y 991), que por su ficha deberían ir después de ellas. Queda fuera de este paso, que solo toca el punto donde el ViewModel arma `SetupTrainingPlannerInput`; el arreglo es borrar ese `sortedBy`.
- DEC-w2-07 (cinco históricos ocultos): resuelve su pendiente sobre el orden del planner («sigue siendo por nivel, tamaño del material pedido e id») y la reserva sobre qué candidato queda primero en las filas de la matriz que no exigen un testigo concreto.

**Pendiente y riesgos.**
- **`SetupWizardFullJourneyTest` queda rojo hasta A.E1** (ver la tabla). Opciones: esperar a A.E1 (el test pasa sin tocar su oráculo) o relajar el oráculo de la bolsa para los planes propios hasta entonces. Fijar provisionalmente un nativo histórico NO sirve: se probó `native:machine-muscle` (el segundo viable) y la reanudación sobre Room vuelve a quedarse en EQUIPMENT («Timeout esperando reanudación sobre Room en EQUIPMENT»), igual que con `native:bodyweight`; solo el plan propio recorre el viaje entero. En el test solo se añadió una traza (`[C.P3][FullJourney]`) y el plan y los motivos en el mensaje del aserto de la bolsa.
- **Clase de plan frente a nivel (decisión de producto).** El comparador pide nivel antes que clase (plan 00, A.D1). Una persona avanzada ve Smolov, Smolov Jr y Coan delante de los planes completos de otro nivel (por ejemplo `protocol:kpkn-native-sbd-4`, intermedio). Si se quiere que las especializaciones y los complementos cierren SIEMPRE la lista, basta invertir los criterios 2 y 3 en `SetupTrainingPlanner`.
- Los métodos de otro nivel quedan al final sin explicación: la tarjeta no dice «Es más exigente que tu nivel actual» (D7 del diseño editorial). Es A.C4 / C.P11.
- Con el prefiltro, Atleta completo evalúa un solo plan: `candidateCounts` y la lista de rechazos describen únicamente al propio (antes eran decenas de `PROFILE_MISMATCH` triviales), y en un borrador heredado con ruta de protocolos la lista de Atleta queda vacía y sin rechazos. El texto «N planes revisados» (C.P5, `candidateCountsText`) debe tolerar N = 1.
- La biblioteca sigue mostrando `listedEntries()` en el orden natural del catálogo, no por rank; C.P5 puede reutilizar el comparador. `PlanGoalMatcher` solo gobierna el prefiltro de capacidades y los filtros de la biblioteca: el planner sigue filtrando por `TrainingReference` y `PlanCandidateEvaluator.coverageOf` deriva su cobertura de la receta (C.P5, si el wizard gana otro filtro por objetivo).
- KDoc obsoleto fuera del alcance de este paso: `CatalogEntry.level` (`PersonalizedPlanCatalog.kt`) dice «El planner lo usa hoy para ordenar» y `PlanEditorial.references` y `PlanEditorial.listed` (`PlanEditorial.kt`) siguen diciendo «SIEMPRE null» y «SIEMPRE true».
- Un cambio de `rank`, nivel o clase en `PlanEditorialTable` reordena la lista del wizard sin tocar ningún programa ya activado: solo cambia qué se ofrece primero a quien configura un plan nuevo.

**Qué lo confirma.** `SetupTrainingPlannerRankingTest`, `PlanGoalMatcherTest`, `SetupTrainingPlannerTest` (capacidades requeridas y plantillas con receta fija), `SetupExecutableAvailabilityMatrixTest` (grupos F y T001) y `PlanGenerationCoverageT006Test.Q1`.

### DEC-w2-08 — La bolsa de prioridades de orden se aplica y se persiste en los planes propios (A.E1, D6, 2026-10-03)

**Estado:** implementada (paso A.E1 del paquete A); decisión D6 del plan de curaduría de programas («prioridades y split aplicados a los propios», §3 y §6). Esta entrada cubre solo la mitad de **prioridades**: el split de los propios lo cierra A.E2 (DEC-w2-04, parte 2) y con él D6 queda completa. No contradice r2: lo cumple también en los cuatro planes propios. No cambia ejercicios, series, repeticiones, RIR, volumen por músculo ni la viabilidad de ningún plan.

**Qué dice r2.** §12.1 (último párrafo): las prioridades de orden existentes conservan su contrato (máximo 2 puntos por músculo y 5 en total), «solo ordenan slots de igual prioridad sin cambiar series/frecuencia», y SPEED y los principales siguen primero. r2 vive fuera del repositorio; es la única mención de las prioridades de orden en r2.

**Qué hacía el código.**
- La ruta histórica (`native:machine-muscle`, `native:bodyweight`…) ya aplicaba la bolsa (`prioritizeExerciseOrder`) y la registraba en `Program.planOrderPriorities`.
- La ruta propia (`SimpleCyclePersonalizer.personalizeNative`) calculaba la bolsa con `orderPoints`, validaba su contrato y la descartaba: ningún slot se ordenaba con ella y el programa no la registraba. Efecto visible: `OrderPrioritiesContract.capabilitiesOf` respondía `NOT_APPLIED_BAG_MISMATCH` («El programa no registra ninguna bolsa de orden aplicada al generarlo») para cualquier plan propio con bolsa, aunque la persona la hubiera elegido en PRIORITIES, y `SetupWizardFullJourneyTest` (plan propio de Músculo con Pectorales 2, Dorsales 2 y Tríceps 1) quedaba rojo desde DEC-w2-06.
- Las tarjetas de plan del wizard no decían qué pasa con la bolsa en cada plan.

**Qué se hizo.**
- *Se aplica al terminar el fitter, no al armar cada día.* El plan 00 proponía ordenar con `sortWith(h1Rank, puntos)` en los dos sitios donde `personalizeNative` ordena (`SimpleCyclePersonalizer`, al armar el día y tras mover un accesorio). El fitter retira, recorta y mueve accesorios recorriendo los slots en el orden del día (los accesorios I y luego C en orden, las reducciones H 3→2 en orden, los movimientos del último al primero): con la bolsa aplicada antes, el accesorio priorizado iría primero y sería el primero en retirarse o recortarse (con Tríceps 2, el pushdown pasaría delante del curl y se retiraría antes que él), y un orden distinto podía además cambiar qué plan cabe o fallar H1 o H3 (`COMPOSITION`). Eso contradice «solo ordena» (razonamiento por lectura del código: la variante del plan 00 no se implementó para medirlo). Por eso `personalizeNative` aplica la bolsa DESPUÉS de que el fitter fije dosis, accesorios y días (justo antes de componer las notas y el programa), con el mismo comparador que proponía el plan 00.
- *Comparador:* (rango H1 ascendente, puntos descendentes) con ordenación estable. El rango H1 manda (SPEED, T1, T2, T3 compuesto, aislamiento, core y gemelo: el orden entre rangos no cambia) y la bolsa solo desempata dentro de un rango; a igual puntuación se conserva el orden recomendado. Puntos de un ejercicio = el máximo de puntos de la bolsa entre los músculos en los que trabaja de forma directa, con los mismos grupos y el mismo cálculo que la ruta histórica (series directas del volumen separado por rol sobre la tabla de músculos del catálogo, con una serie de prueba). La aproximación (`warmup`) sigue al primer compuesto de cada patrón en el orden nuevo, con el mismo número de aproximaciones por patrón.
- *Revalidación y reversión.* El orden con bolsa se valida con el mismo intento del fitter (reglas duras de composición, tiempo y techo de volumen). Si no pasara, el plan sale con el orden recomendado, con el aviso «No pudimos ordenar los ejercicios según tus prioridades…» y SIN registrar la bolsa, para que el contrato nunca afirme una bolsa que no se aplicó. No se ha observado nunca: la ordenación solo permuta ejercicios del mismo rango (sin cambiar series, minutos ni volumen), y en la matriz de la prueba hay 276 comparaciones con 0 reversiones.
- *Persistencia:* `Program.planOrderPriorities` = exactamente la bolsa normalizada por `orderPointsFromBag` (los sinónimos «pecho» o «espalda» se registran como `Pectorales` y `Dorsales`), que es la que compara `OrderPrioritiesContract`; `null` si no se pidió ninguna. Una bolsa válida de un músculo que el plan no trabaja se registra igual y no mueve nada. Las prioridades heredadas (`PersonalizerInput.priorityMuscles`, 1 punto por músculo) también se aplican a los propios, como ya hacía la ruta histórica. Una bolsa fuera de contrato se rechaza con el mismo mensaje de siempre (`limitations` con «máximo 2 puntos por músculo…»).
- *Tarjetas del wizard* (`SetupWizardViewModel`, `planOrderPriorityReason`), solo cuando la bolsa trae algún punto: los planes KPKN (nativos) dicen «Tus prioridades ordenan los ejercicios de cada día»; los métodos de autor y las plantillas con receta fija dicen «Conserva el orden del método» (`OrderOwnership.RECIPE_FIXED`: la bolsa no los toca); las plantillas sin receta no dicen nada (no hay orden de método que conservar ni plan generado que se ordene). Las razones que ya existían se conservan.

**Alternativas descartadas.** (1) Aplicar la bolsa al armar cada día, como proponía el plan 00 (ver arriba): cambia las decisiones del fitter y puede invertir lo pedido. (2) Dejar que la bolsa también proteja al accesorio priorizado de las retiradas del fitter: sería un efecto sobre las series y los ejercicios, fuera del contrato «solo ordena» de r2 §12.1. (3) Solo aclarar el texto («los planes KPKN no aplican las prioridades»): descartada por D6.

**Tests: cifras antes → después** (corridas por filtro de `testBaseDebugUnitTest`, 2026-10-03).

| Test u oráculo | Antes | Después | Por qué |
|---|---|---|---|
| `SetupWizardFullJourneyTest` (2 tests) | `fullJourneySelfDefinedNutritionCommitsExecutableProgramAndDurableTargets` rojo en `assertCommittedFullJourney` (plan=`native:muscle-foundation-v2`, bolsa aplicada `{}`, `NOT_APPLIED_BAG_MISMATCH`) | 2 de 2 verdes, sin relajar el oráculo de la bolsa (aplicada = Pectorales 2, Dorsales 2, Tríceps 1) | los planes propios ya registran la bolsa. Solo cambia el archivo: se quita la traza `[C.P3][FullJourney]` y se afirma que la tarjeta del plan propio explica la bolsa |
| `OwnPlanPrioritiesAndSplitTest` (nuevo) | — | 11 tests: persistencia y contrato `APPLIED` (Músculo, intermedio, 3 días, 90 min, gimnasio completo), sinónimos, prioridades heredadas, el orden solo desempata dentro del rango H1 y no cambia la secuencia de rangos (dos bolsas), bolsa vacía o de un músculo ausente = el mismo programa, bolsa inválida en los cuatro planes, los cuatro planes persisten la bolsa (el Atleta con cardio de 20 min), matriz de 144 escenarios (cuatro planes × tres niveles × 1..6 días × 90 y 45 min) con 276 comparaciones, barrido de minutos con la bolsa sobre el accesorio que se queda y tarjetas del wizard | fija la decisión. La matriz compara con y sin bolsa la viabilidad, el motivo de los rechazos, la prescripción de cada sesión (ejercicio, series, repeticiones y RIR como multiconjunto), el volumen por músculo, las reglas duras, el tiempo y las notas §12.3 del fitter: 6 escenarios rechazados con el mismo motivo, 126 programas reordenados, 66 con ajustes del fitter idénticos, 0 reversiones. El barrido de minutos (Músculo y Fuerza y músculo, 4 a 6 días, de 20 a 62 min): 4 planes (Fuerza y músculo de 5 días con 26 a 29 min por sesión) en los que el fitter retira algo de un día que conserva otro accesorio de un músculo distinto; con la bolsa puesta sobre el que se queda decide exactamente lo mismo y la prescripción no cambia (por lectura del código, ordenar la bolsa antes del fitter lo habría invertido: no se implementó esa variante para medirlo) |
| `RecipeOrderPrioritiesContractTest` | 5 tests (solo la ruta histórica y las recetas de autor) | 8 tests: +3 del plan propio (`APPLIED` solo si la bolsa pedida es la registrada, sin bolsa no se registra nada y pedirla después da `NOT_APPLIED_BAG_MISMATCH`, bolsa inválida) | mismo contrato que `native:machine-muscle` |
| `PriorityOrderOnlyTest` (ruta histórica) | 5 verdes | 5 verdes, sin tocarlo | la ruta histórica no cambia |
| `PlanGenerationCoverageT006Test` Q2, Q2 positivos, Q2 calibrada y Q3 | 1 224 viables de 2 304 con `{APPARATUS_ABSENT=360, TIME_BUDGET=504, PROFILE_MISMATCH=216}`; 58 positivos; Q3 de 328 filas con 310 viables; Q1 194 400 | idéntico: las tres calibraciones dan 1 224 → 1 224 con 0 filas que cambian | la matriz no pide bolsa, y con la bolsa vacía el programa es el mismo (el bloque nuevo ni se ejecuta) |
| `SetupExecutableAvailabilityMatrixTest` | 17 tests verdes | 17 de 17 | sin bolsa no cambia ninguna fila |
| `NativeProfileRecipeAndFitterTest` (32), `NativeMaterialEvidenceTest` (22), `PlanCoverageContractTest` (2), `SetupWizardAuthoredPlansTest` (4), `SetupWizardActivationGateTest` (3), `SetupWizardOneDayCopyTest` (1), `NativeRirWarmupAndIntegrationTest` (9), `OnboardingSplitSelectionTest` (4), `PersonalizedPlanCatalogTest` (20), `LegacyNativeDurationConsistencyTest` (4), `NativeProgressionRealPlanTest` (6, uno omitido por `assumeTrue`, como antes) y `PlanMaterializerDurationSealTest` (5) | verdes | verdes, sin tocarlos | no pedían bolsa o usan la ruta histórica |

**Relación con otras decisiones.**
- DEC-w2-06: resuelve su pendiente («`SetupWizardFullJourneyTest` queda rojo hasta A.E1»); el test pasa sin tocar su oráculo.
- DEC-w2-04, parte 2 (A.E2, split de los propios): completa D6. Reutilizará esta clase de pruebas (`OwnPlanPrioritiesAndSplitTest`, que lleva «Split» en el nombre para ese fin). El bloque de la bolsa está en `personalizeNative` justo antes de `notes += adjustments`: cualquier cambio de orden que haga A.E2 debe quedar antes del fitter o respetar que la bolsa se aplica al final.
- r2 §12.1 y DEC-w1-01: el fitter no cambia, así que cardio, descansos y dosis siguen como estaban.

**Pendiente y riesgos.**
- **Los programas propios ya activados no tienen `planOrderPriorities`** (se generaron con el generador anterior): si se les pide una bolsa, el contrato seguirá diciendo `NOT_APPLIED_BAG_MISMATCH` («no registra ninguna bolsa»), que es la verdad. Sin migración: para tener sus prioridades hay que generar un plan nuevo con el wizard.
- `SetupDependencyRules` sigue declarando `PRIORITIES` como `reorderOnly` (no marca ningún preview como obsoleto). Cambiar las prioridades tras llegar a PLAN sí regenera candidatos y preview (la clave de candidatos incluye `trainingOptions`, y por tanto la bolsa), así que el resultado es correcto, pero el grafo de dependencias no lo anota como «por revisar». Si se quiere que figure, `PRIORITIES` debería declarar `RECIPE` y `EXERCISES` obsoletos; no se ha tocado `SetupStepGraph` en este paso.
- El aviso de reversión y su rama no tienen prueba directa: no se ha encontrado ninguna entrada que la active (hacerlo exigiría inyectar un fallo en el fitter).
- `PersonalizedPlanCatalog.REVISION` (`native-cycle-1`) no se sube en este paso: el plan lo sube al cierre del paquete A (`native-cycle-2`). La caché de candidatos de la sesión ya distingue las bolsas, así que no hay riesgo de reutilizar un plan generado con otra.

**Qué lo confirma.** `OwnPlanPrioritiesAndSplitTest`, `RecipeOrderPrioritiesContractTest` (casos `own_plan_*`), `SetupWizardFullJourneyTest` (`assertCommittedFullJourney` y la tarjeta del plan propio), `PriorityOrderOnlyTest` (la ruta histórica sin cambios) y `PlanGenerationCoverageT006Test` (Q2 idéntico).

### DEC-w2-04 (parte 1) — Soportes reales de sentadillas y press, y tablas de candidatos para kettlebell, Smith y banda (A.B4/B5, 2026-10-03)

**Estado:** implementada (pasos A.B4 y A.B5 del paquete A). La parte 2 de DEC-w2-04 (tabla split↔calendario, A.E2) sigue pendiente y el contrato de cobertura no está en 0: quedan `TIME_BUDGET_INEXACT` (A.C2) y las 120 filas de Atleta de 1 día con solo barra de dominadas. No contradice r2: implementa su §13.2 y su §13.3; sí corrige un test que fijaba lo contrario para la sentadilla (ver «Una lectura corregida»).

**Qué dice r2.**
- §13.2, tabla de claves: «`squat_rack` … S/B si también hay banco; no prueba cajón, soportes de Nordic o paralelas». Y en el párrafo de `supportDependencyFor`: «Banca barra exige barra+banco+rack; banca DB exige DB+banco; … Rumano/remo de pie no requieren rack por ser barra si se inician desde suelo. No imponer rack a todos los ejercicios con barra por coincidencia de implemento.»
- §13.3, tabla de selección: la fila S lleva `high_bar_back_squat__barbell` con «(rack)»; la B, `bench_press__barbell` con «(banco+rack)» y «`floor_press__dumbbells` sin banco». «Las once categorías tienen tratamiento definido»: la kettlebell «se aprovecha en front squat/sumo squat, remo convencional, press militar, zancada y banca (si hay banco)», la Smith «usa sus variantes canónicas de sentadilla/banca/rumano, exigiendo banco en press», y «No ignorar equipo utilizable reduciendo todos esos casos a un plan corporal genérico».
- §13.3, cierre: si V y R terminan en la misma configuración de remo se fusionan H con H hasta 4 series y se «registra ajuste».
- Los huecos de soporte del rack chin, las dominadas escapulares, la suspensión, el jalón y el hip thrust con banda y el curl inclinado no están en r2: los añade el plan de curaduría (§6, B4) con el mismo criterio de §13.2 («fondos entre bancos exigen sus apoyos; dominadas barra; remo invertido barra baja»): cada configuración con SU apoyo.

**Una lectura corregida.** `FixedRecipeEquipmentCompatibilityTest` fijaba `supportRequirementsFor("high_bar_back_squat__barbell") = ∅` con el comentario «§13.2: nada de rack por coincidencia de implemento». Era una lectura demasiado amplia de la última frase de §13.2: lo que r2 excluye es imponer rack al rumano y al remo (barra que parte del suelo), no a la sentadilla, cuya fila de §13.3 dice «(rack)» y que se descarga de un soporte. B4 corrige esa lectura; r2 queda como estaba y no hay desviación.

**Qué se hizo.**
- **B4, `supportRequirementsFor`** (`EffectiveEquipmentCatalog.kt`, antes → después):

| Configuración | Antes | Después |
|---|---|---|
| `high_bar_back_squat__barbell`, `low_bar_back_squat__barbell`, `front_squat__barbell`, `paused_back_squat__barbell`, `high_bar_back_squat__safety_bar`, `quads_sentadilla_cajon__default`, `quads_sentadilla_anderson__default` | ∅ | {rack} |
| `paused_bench_press__barbell`, `close_grip_bench_press__barbell`, `tren_superior_press_spoto_barra__default`, `tren_superior_press_banca_cadenas__default` | ∅ (el prefijo `bench_press__` no los cubría) | {bench, rack} |
| `incline_biceps_curl__dumbbells` | ∅ | {bench, bench_incline} |
| `rack_chin__default` | ∅ | {low_bar_support} |
| `back_dominadas_escapulares__default`, `forearms_suspension_isometrica_barra_fija__default` | ∅ | {pull_up_bar} |
| `lat_pulldown__bilateral__band`, `lat_pulldown__unilateral__band` | ∅ | {pull_up_bar} |
| `hip_thrust__bilateral__band`, `hip_thrust__unilateral__band` | ∅ | {bench} |
| `close_grip_lat_pulldown__cable` | fuera de la llave `cable_high_low` | entra en `CABLE_HIGH_LOW_CONFIGURATIONS` (sin esto PHAT dejaría de ser viable con polea al migrar `lat-close` a esta configuración, B.S6) |

  Sin cambio a propósito: Smith, kettlebell, mancuerna y peso corporal; el peso muerto (también «hasta la rodilla»), el rumano, el press militar (Smith incluido), `floor_press__*` y las zancadas. El banco plano exigido por `bench_press__*` (también en Smith, kettlebell y cable) no cambia. La variante de banda del hip thrust y del jalón se cubre por ambas lateralidades (bilateral y unilateral).
- **B5, `NativeCandidateTable`** (`NativeProfileSpec.kt`): 16 ids nuevos, todos APPROVED en los dos assets (verificados antes de escribirlos; ninguno faltaba):

| Slot | Altas (equipmentId verificado) |
|---|---|
| S (F, Fv y H) | `high_bar_back_squat__smith_machine` (smith_machine), `front_squat__kettlebell` (kettlebell) |
| B y PB | `floor_press__dumbbells` (dumbbells), `bench_press__smith_machine` (smith_machine) |
| D | `romanian_deadlift__bilateral__smith_machine` (smith_machine), `hip_thrust__bilateral__band` (band) |
| R | `conventional_row__smith_machine` (smith_machine), `conventional_row__kettlebell` (kettlebell) |
| V | `lat_pulldown__bilateral__band` (band); como respaldo, los remos Smith y kettlebell |
| O | `military_press__smith_machine` (smith_machine), `military_press__kettlebell` (kettlebell) |
| U | `walking_lunge__kettlebell` (kettlebell) |
| L | `standing_lateral_raise__kettlebell` (kettlebell) |
| A | `hammer_curl__kettlebell` (kettlebell) |
| T | `overhead_triceps__dumbbells` (dumbbells), `triceps_press_frances__kettlebell` (kettlebell) |

  Orden (KDoc de `NativeCandidateTable`): cada alta va detrás de las variantes con barra, mancuerna, polea y máquina que su lista ya tenía y delante de la banda y de la reserva de peso corporal (§13.3), primero la mancuerna, luego la Smith y luego la kettlebell. Excepciones por lógica de prioridad: en T y A las variantes de banda ya iban delante de las de máquina y las altas van tras ellas; en V el jalón con banda (tirón vertical) va detrás de las dominadas y delante de los remos de respaldo; en D el hip thrust con banda va delante del puente corporal. Efecto: quien ya resolvía una variante con barra, mancuerna, polea o máquina conserva su elección (salvo el caso de V); cambian quienes solo tenían banda, kettlebell, Smith, mancuernas sin banco o peso corporal.
- **Nota llana de remo compartido** (`SHARED_ROW_FOR_PULL_NOTE`, `SimpleCyclePersonalizer.kt`): si V y R resuelven a la MISMA configuración de remo y el calendario lleva los dos slots, el plan avisa «Tus días de espalda usan el mismo remo para el tirón horizontal y el vertical porque con tu material y tu nivel no hay jalón ni dominadas.» (en `planNotes`, que la revisión del wizard muestra, y en la descripción del programa). El texto dice «con tu material y tu nivel» y no solo «tu material» porque el principiante con barra de dominadas tampoco recibe dominadas por defecto (r2 §13.3). Se detecta con las mismas dos resoluciones que ya decidían `pullAvailable`; el aviso sale también con solo banda, mancuernas o barra, que ya fusionaban R y V sin decirlo.

**Efecto por material (Músculo, kettlebell y Smith medidos en `NativeCandidateTableSlotsTest`; el resto por lectura de la tabla).**
- **E10, solo kettlebell:** antes no tenía ningún remo ni jalón, así que Músculo y Atleta usaban el calendario «sin tirón» solo con peso corporal. Ahora resuelve S → `front_squat__kettlebell`, R y V → `conventional_row__kettlebell`, O → `military_press__kettlebell`, U → `walking_lunge__kettlebell`, L, A y T con kettlebell; B sigue en la flexión de rodillas y D en el puente. Con tirón disponible, el calendario pasa al general (UA/UB, FA/FB…).
- **E11, Smith y banco:** S, B (con el banco plano confirmado), D, R, V y O en Smith; U, L, A y T siguen sin variante de Smith en la tabla. Sin banco, B vuelve a la flexión de rodillas (`bench_press__*` exige banco).
- **E12, banda y barra de dominadas:** el principiante (sin dominadas por defecto) recibe `lat_pulldown__bilateral__band` como tirón vertical en lugar de repetir el remo con banda; intermedio y avanzado conservan las dominadas.
- **E2 y E3 (mancuernas):** E2 sin banco hace `floor_press__dumbbells` en lugar de la flexión de rodillas; E2 y E3 hacen `overhead_triceps__dumbbells` en lugar de las flexiones esfinge.
- **E8, E15, E16 y E17 (barra sin rack acreditado: gimnasio sin confirmar, rack negado, barra sola, barra con mancuernas y bancos):** la sentadilla de barra deja de prescribirse sin rack acreditado (un rack «sin responder» no cuenta como presente, igual que ya pasaba con la banca de barra): E16 cae a la sentadilla sin carga y E8, E15 y E17 (con mancuernas en el material) a la sentadilla copa; Fuerza y músculo con E8, E15 y E17 (ruta de mancuernas) usa la copa como principal en lugar de la de barra sin dónde cargarla. El recuento de filas `Ready` de Músculo no cambia en E8, E15 y E17; en E16 sí (−3, ver abajo). Quien tiene un gimnasio completo y se salta el panel de soportes recibe ahora la copa en lugar de la sentadilla de barra hasta que confirme el rack.
- **Sin cambio:** E0, E1 (la banda sola no tiene dónde anclar el jalón ni banco para el hip thrust), E4, E5, E6 y E18 (rack confirmado), E7, E9, E13 y E14.

**Cifras antes → después** (rejilla de 25 650 filas del contrato, corrida `full` del 2026-10-03; 4 hilos, 363 s).

| Clase de violación | Antes | Después | Qué cambió |
|---|---|---|---|
| `NOT_HONEST` (`COMPOSITION`) | 120 | **120** | sin cambio: Atleta de 1 día con solo barra de dominadas (E13), intermedio y avanzado |
| `DISHONEST_ABSENT`, `TIME_BUDGET_OUT_OF_RANGE`, `NO_REPAIR`, `EXCEPTION` | 0 | **0** | |
| `TIME_BUDGET_INEXACT` | 1 578 | **1 584** | +6, con causa (abajo); sigue siendo A.C2 |
| `MATERIAL_UNUSED` | 1 464 | **0** | −1 464: Músculo y Atleta con kettlebell (E10) o Smith y banco (E11) usan su material |
| **Total** | **3 162** | **1 704** (−1 458) | |

Techos nuevos (`VIOLATION_CEILING`): smoke 200 → 110; ci/0 790 → 432; ci/1 787 → 420; ci/2 797 → 426; ci/3 788 → 426; full 3 162 → 1 704. Detalle de la corrida: `Ready` 13 624 → 13 471 (−153); rechazos honestos antes de reparar 11 906 → 12 059 (`TIME_BUDGET` 9 656 → 9 809; `APPARATUS_ABSENT` 1 080, `APPARATUS_UNKNOWN` 360 y `PROFILE_MISMATCH` 810 sin cambio); primera reparación que logra `Ready`: `SetMinutes` 9 656 → 9 809, `SwitchGoal` 1 532 → 1 520, `SwitchGoal+SetMinutes` 358 → 370, `ConfirmApparatus` 315 y `ConfirmApparatus+SetMinutes` 45 sin cambio.

**Por qué sube `TIME_BUDGET_INEXACT` en 6 y bajan 153 `Ready`.** Atleta con solo kettlebell (E10) o solo Smith (E11) tenía 657 filas `Ready` con el calendario «sin tirón» (corto, solo peso corporal). Con remo resoluble usa el calendario con tirón, más largo, y se comporta como el resto de fixtures con tirón (585 `Ready`, 495 rechazos y 84 filas de `TIME_BUDGET_INEXACT`): −72 `Ready` por fixture y 81 → 84 filas inexactas, +6 en total. Músculo con E10, E11 y E16 pierde 3 `Ready` cada uno (75 → 72). En E10 y E11 es el mismo cambio de calendario; en E16 la única diferencia con E8, E15 y E17 (que no pierden ninguna fila) es que la sentadilla cae a la variante sin carga, de 8 a 15 repeticiones, y no a la copa: es una inferencia, no una medición fila a fila. Las 153 filas son rechazos honestos `TIME_BUDGET` con reparación `SetMinutes` (`NO_REPAIR` sigue en 0); las 12 filas que pasan de `SwitchGoal` a `SwitchGoal+SetMinutes` son Fuerza y Fuerza y músculo con E10 y E11, cuyo destino «Músculo» ahora necesita además subir los minutos. El contrato no sube ninguna clase sin causa: A.C2 hace exactas las 1 584 filas inexactas. `PlanGenerationCoverageT006Test` queda idéntico: Q1 (62 208 filas), Q2 (2 304 filas, 1 224 viables, `{APPARATUS_ABSENT=360, TIME_BUDGET=504, PROFILE_MISMATCH=216}`), Q2 positivos obligatorios (58 de 58 `Ready`) y Q3 (328 filas, 310 viables, 18 `TIME_BUDGET`, tres bordes en `MUSCLE/E2/3d@28`, `POWERBUILDING/E2/3d@23` y `COMPLETE_ATHLETE/E0/3d@25`); E2 y E3 cambian de contenido (floor press, tríceps sobre la cabeza) pero no de viabilidad. `SetupExecutableAvailabilityMatrixTest` completa: 17 pruebas verdes sin tocar ninguna fila.

**Re-baselines.**

| Test | Aserción antes | Después | Por qué |
|---|---|---|---|
| `FixedRecipeEquipmentCompatibilityTest.support_requirements_are_a_set_of_dependencies_not_a_single_string` | `supportRequirementsFor("high_bar_back_squat__barbell") = ∅` | `{rack}`; el rumano con barra y las desconocidas siguen en ∅ | B4 (r2 §13.3 fila S) |
| `PlanAdaptationAuthoredRecipesTest.aSubstitutionIsRecordedOncePerSlotAcrossTheTwelveWeeksAndKeepsTheOriginalAsParent` | PHUL adaptado sin rack: cambios `[bp, bp-inc]` a mancuernas | `[bp, bp-inc, sq, sq-front]`; `sq` → `high_bar_back_squat__smith_machine` y `sq-front` → `front_squat__smith_machine` (misma definición, tier 1; el gimnasio completo trae Smith) | sin rack tampoco hay sentadilla de barra |
| `AuthoredPlanMaterializerTest.missingRackSubstitutesTheBarbellBenchAndSquatSlotsAndLeavesTheOriginalUntouched` (antes `…OnlyTheBarbellBenchSlots…`) | 2 cambios de procedencia | 4, más aserciones de que no queda sentadilla de barra y de que `sq` es la Smith en las 12 semanas | ídem |
| `AuthoredPlanMaterializerTest.adaptedProgramSurvivesTheRoomJsonRoundTripWithItsProvenance` y `AuthoredPlansActivationParityTest.theReadyAdaptationKeepsItsSlotChangesThroughActivationAndReopen` | `slotChanges.size == 2` | 4 | ídem |
| `PlanCoverageContractTest` | techos 200 / 790 / 787 / 797 / 788 / 3 162 | 110 / 432 / 420 / 426 / 426 / 1 704 | B4 y B5 |

**Riesgos y compatibilidad.**
- Recetas fijas visibles con sentadilla de barra: 28. Veintiséis ya pedían rack por la banca de barra; **Smolov y Smolov Jr** (sentadilla sin banca) lo piden ahora por primera vez (`FixedRecipeEquipmentCompatibilityTest.every_visible_fixed_recipe_with_a_rack_squat_reports_the_rack_when_it_is_missing`). Madcow, Texas y el resto no cambian de requisitos.
- **PHAT** (original y adaptado) pide ahora `low_bar_support` por el rack chin, que antes no pedía ningún soporte. Con la llave sin responder, la adaptación devuelve `APPARATUS_UNKNOWN` con `low_bar_support` (confirmable, «Barra baja estable»); con la llave negada devuelve `NO_VALID_SUBSTITUTION` en el slot `rack-chin`, porque la tabla curada de `PlanAdaptationResolver` no cubre esa definición (`FixedRecipeEquipmentCompatibilityTest.phat_now_asks_for_the_low_bar_support_that_its_rack_chin_always_needed`). Decidir si se añade un sustituto es del paso A.B6.
- PHUL adaptado sin rack: las dos sentadillas de barra pasan a la misma definición en Smith (o a la copa o la sentadilla sin carga si no hay Smith), como ya hacían las bancas.
- La sección «Material» de la hoja «Cómo funciona» (`PlanInfoModel.material`) usa `supportRequirementsFor`: ahora lista «Rack de sentadilla» en cualquier plan con sentadilla de barra y «Barra baja estable» en PHAT (lectura de código; `PlanInfoModelTest` sigue verde).
- Los programas ya activados no cambian: `supportRequirementsFor` y la tabla de candidatos solo gobiernan lo que se genera y se comprueba de ahora en adelante.

**Qué lo confirma.** `NativeCandidateTableSlotsTest` (10 pruebas: ids reales y APPROVED, sufijo de material coherente con el catálogo, orden de las altas, y el generador real con E1, E2, E10, E11 y E12), `CatalogIdsExistInCatalogTest` (por reflexión: `CatalogIds`, `NativeCandidateTable` y `AuthoredExerciseBindings.all` en ambos assets), `FixedRecipeEquipmentCompatibilityTest` (reglas de B4, Smolov y PHAT), `EffectiveEquipmentResolverContractTest` (evidencia `UNKNOWN`/`ABSENT` de rack, banco, barra de dominadas y barra baja, y `cable_high_low` con el jalón cerrado), `EffectiveEquipmentContractTest.barbell_squats_need_the_rack_and_without_it_the_squat_falls_back_to_the_loadless_squat` y `PlanCoverageContractTest`.

**Pendiente.**
- B6 (evidencia unificada) y la tabla de sustitución de `rack_chin` en `PlanAdaptationResolver`.
- Banca con kettlebell: r2 §13.3 la cita «si hay banco» y `bench_press__kettlebell` existe APPROVED, pero el plan de curaduría no la lista en B5; no se añadió. Decisión del dueño.
- Otras sentadillas de barra del catálogo (Anderson frontal, Zercher, hack con barra, sumo, bazuca, somersault) y otros ejercicios que se descargan de un soporte (hip thrust con barra, Smith o máquina; JM press) no entran en B4: ninguna receta ni tabla los usa hoy.
- Atleta de 1 día, intermedio o avanzado, con solo barra de dominadas (E13): las 120 filas de `COMPOSITION` siguen; B5 no añade remo ni jalón para ese material.
- `TIME_BUDGET_INEXACT` (1 584 filas) es de A.C2.

### DEC-w2-02 — Redirección honesta con un toque, sin «fuerza relativa» (A.C1 y A.C4 parte pura, 2026-10-03)

**Estado:** implementada en el dominio (`PlanRepair`, `PlanRepairAdvisor` y `PlanRejectionPresenter`, commit `e275d0bbc`); falta el cableado en el wizard (A.C3, `applyRepair` en el ViewModel, y C.P11, la tabla de rechazos de la UI): hoy ningún código de producción llama al asesor ni al presentador. Aplica la decisión D5 del plan de curaduría (§3), que se ejecuta con su recomendación por defecto salvo indicación contraria del dueño: redirección honesta con un botón de un toque, sin variantes de «fuerza relativa». No contradice r2: concreta §15.2 (motivos cerrados, cada uno con su acción) y deja intactas §11.1 y DEC-w1-01.

**Qué dice r2.**
- §15.2: la UI presenta tres estados distintos —calculando, planes listos e «incompatibilidad con acciones precisas»— y cada rechazo conserva su motivo cerrado (la lista de 14 motivos); «Este plan necesita X min; elegiste Y» ofrece otros planes o editar el tiempo.
- §11.1: sin barra, rack y banco, Fuerza explica qué falta; con solo bandas o cuerpo, Fuerza y músculo «explica el requisito de resistencia externa para sus principales y ofrece explícitamente Músculo/Atleta completo». Son restricciones de la disciplina acordada, no filtros para esconder un fallo del catálogo. La fuerza relativa con peso corporal solo la reserva para Atleta completo (tabla de §11.1: «Fuerza puede ser relativa con peso corporal; no se promete powerlifting»). r2 no pide variantes de fuerza relativa dentro de Fuerza ni de Fuerza y músculo.

**Qué hacía el código.**
- Los rechazos de Fuerza y de Fuerza y músculo sin material (P-01 y P-02 del plan, D5) se resumían en `CandidateIncompatibility` (`SetupTrainingSteps.kt`) con el primer rechazo de la lista (`candidateRejections.firstOrNull()`, a menudo el rechazo trivial de un plan que la persona ni pidió), un `when` local por motivo y, debajo, el texto crudo del motor (`rejection.reason`). Los botones eran «Confirmar material», «Editar tiempo» y «Reintentar»: ninguno llevaba a otro objetivo ni confirmaba material con un toque.
- Fuera del contrato de cobertura no existía ninguna función de reparación. Las reparaciones de un toque que el contrato exigía (`ConfirmApparatus`, `SetMinutes`, `SetCardioMinutes`, `SwitchGoal`) eran simulaciones dentro de `PlanCoverageContractTest` (DEC-w2-01): el contrato daba por buena una reparación que el producto todavía no podía ofrecer.

**Qué decide.** Fuerza y Fuerza y músculo sin material no ganan variantes de «fuerza relativa» dentro del plan propio: serían ≈ 15 h de contenido editorial nuevo y contradicen r2 §11.1, que define esos perfiles por su resistencia externa. Reciben una redirección honesta con un botón de un toque hacia un objetivo cuyo plan propio ya existe y ya cabe, y no hay un motivo de rechazo nuevo: se usan los cerrados de §15.2.

**Qué se hizo.**
- `PlanRepair` (interfaz sellada, `PlanRepair.kt`): `SetMinutes(minutes)`, `SetCardioMinutes(minutes)`, `ConfirmApparatus(keys, categories)` con `applyTo(availability)` (la escritura pura sobre la disponibilidad, que usarán por igual el asesor y `applyRepair`), `SwitchGoal(goal, alsoMinutes)` y `ClearSplit`. Solo dice QUÉ cambiar; no cambia nada por sí misma.
- `PlanRepairAdvisor.suggest(request, rejected, availability, evaluate)` (`PlanRepairAdvisor.kt`) devuelve la lista ORDENADA de reparaciones de un toque (vacía = no hay ninguna) y prueba cada candidata con el evaluador que recibe (`PlanRepairEvaluator`: en producción `PlanCandidateEvaluator.evaluate` con el plan propio del objetivo; en las pruebas, un doble): nunca adivina si una reparación funciona. Hace como mucho cuatro evaluaciones por rechazo (el peor caso es un `TIME_BUDGET` de Atleta con cardio de 30 min: una de minutos y tres de cardio). Tabla:

| Motivo del rechazo | Reparación | Solo se propone si |
|---|---|---|
| `TIME_BUDGET` | `SetMinutes(requiredMinutes)` | los minutos son un presupuesto que el wizard admite (por encima del elegido y hasta 100) y con ellos el plan queda `Ready`. Si no, y solo en Atleta completo con cardio explícito: `SetCardioMinutes` con el mayor valor de {30, 20, 15, 10} menor que el actual con el que el plan queda `Ready` (el cardio solo baja, y solo por decisión de la persona: DEC-w1-01) |
| `APPARATUS_UNKNOWN` | `ConfirmApparatus` con las llaves que resuelven los `missingRequirements` del rechazo (`SetupApparatusPanel.keyForToken`) y sus categorías (`categoriesFor`) | con el material confirmado el plan queda `Ready`; si con él solo falla por tiempo, se encadena un único `SetMinutes` con los minutos exactos |
| `APPARATUS_ABSENT` y `PROFILE_MISMATCH` | `SwitchGoal(destino)`: Fuerza → Fuerza y músculo si la categoría de mancuernas está marcada y, si no, Músculo; Fuerza y músculo → Músculo; Músculo y Atleta completo no tienen destino; nunca Atleta completo | el destino, evaluado con su propia referencia, sin cardio y sin reparto elegido, queda `Ready`, o falla solo por tiempo y un `SetMinutes` lo arregla (`alsoMinutes`) |
| `SPLIT` | `ClearSplit` | sin el reparto elegido el plan queda `Ready` (hoy ningún plan propio rechaza por SPLIT: lo activa A.E2) |
| Catálogo, receta no disponible, nivel, frecuencia, configuración sin resolver, sin sustitución válida, base de carga, composición e interno | ninguna | — |

- `PlanRejectionPresenter` (`PlanRejectionPresenter.kt`; el presentador ÚNICO, dominio puro, sin `screens/` ni Android): `primary(rejections, ownPlanId)` elige el rechazo más accionable (el del plan propio del perfil; luego `APPARATUS_UNKNOWN` con llave confirmable; luego `TIME_BUDGET` con menos minutos requeridos; luego `PROFILE_MISMATCH` o `APPARATUS_ABSENT`; si no, el primero de la lista) y `present(rejection, context)` escribe un texto llano con como mucho dos botones según la tabla de C.P11 («Reintentar», «Cambiar objetivo», «Ver alternativas», «Cambiar días», «Cambiar reparto», «Confirmar material» y «Ajustar a N min»). `RejectionView`, la proyección de un rechazo que recibe, no trae el texto crudo del motor, así que ni ids ni tokens llegan a la persona.
- El contrato de cobertura deja de simular: `PlanCoverageContractTest` llama al asesor real con un `probe` que traduce cada candidata a una fila de la rejilla y comprueba que el asesor arma el pedido del destino como lo arma el wizard (referencia propia de cada objetivo; cardio solo en Atleta completo).

**Alternativas descartadas.** (1) Variantes de «fuerza relativa» con mancuernas o peso corporal dentro de Fuerza y de Fuerza y músculo (≈ 15 h de contenido y contradice §11.1). (2) Proponer `SwitchGoal` sin probar el destino: podría llevar a un plan que tampoco cabe; por eso solo se propone si el destino queda `Ready` o si un `SetMinutes` lo arregla. (3) `SwitchGoal` a Atleta completo en un toque: exige cardio y minutos de cardio que la persona no ha elegido. r2 §11.1 pide además ofrecer Atleta completo para Fuerza y músculo sin resistencia: queda en el botón «Cambiar objetivo» del presentador (abrirá el paso de objetivo cuando se cablee), no en el de un toque.

**Tests: cifras antes → después.**

| Test u oráculo | Antes | Después | Por qué |
|---|---|---|---|
| `PlanRepairAdvisorTest` | — | 25 tests (nuevo): la tabla de arriba regla por regla (los minutos exactos arreglan o no, el cardio solo baja y se prefieren los minutos, nunca Atleta, los destinos de `SwitchGoal` se evalúan con sus propios términos, `ConfirmApparatus` con sus llaves y categorías y un único `SetMinutes` encadenado, `ClearSplit`, los motivos sin reparación no evalúan nada, cada sonda lleva su propia clave de entrada) | fija el asesor |
| `PlanRejectionPresenterTest` | — | 24 tests (nuevo): `primary` (propio, UNKNOWN con llave, menos minutos, el resto) y `present` motivo por motivo con plurales y etiquetas del panel; nunca ids, tokens ni texto del motor | fija la tabla de C.P11 |
| `PlanCoverageContractTest` (`full`, 25 650 filas) | reparaciones simuladas en el propio test: `SetMinutes` 9 809, `SwitchGoal` 1 520, `SwitchGoal+SetMinutes` 370, `ConfirmApparatus` 315, `ConfirmApparatus+SetMinutes` 45; `NO_REPAIR` 0 | idénticas con el asesor del dominio; `NO_REPAIR` 0 (informe `full`: 12 059 rechazos honestos antes de reparar y 13 471 `Ready`) | el asesor reproduce la semántica de las simulaciones que sustituye |

**Relación con otras decisiones.**
- DEC-w2-01: resuelve su pendiente de C1 (el asesor reutiliza `keyForToken` y `categoriesFor` y encadena `SetMinutes`) y lleva al dominio las reparaciones que el contrato simulaba. Su decisión de B2 (con mancuernas, Fuerza y músculo no se bloquea por rack y banco sin confirmar) existe porque faltaba el botón de reparación: conviene revisarla cuando A.C3 y C.P11 estén cableados (ver «Decisiones pendientes del dueño» en el README de la curaduría).
- DEC-w2-03: `SetMinutes` y `alsoMinutes` consumen el `requiredMinutes` exacto de A.C2; con el esfuerzo del fitter la reparación habría ofrecido minutos de más.
- DEC-w2-05 (A.D4): los rechazos que leerán el asesor y el presentador son siempre los del pase pedido, no los del pase a peso corporal.
- DEC-w1-01: el cardio nunca se recorta dentro del generador; `SetCardioMinutes` es una elección de la persona.
- DEC-w2-04, parte 2 (A.E2): `ClearSplit` espera a que los planes propios rechacen por SPLIT.

**Pendiente y riesgos.**
- **Cableado (A.C3 y C.P11).** `applyRepair(repair)` en el ViewModel con la API que ya existe (`updateStep`, `setStepNumber`, `setStepChoice`, `editStep`), la conversión de `SetupCandidateRejection` en `RejectionView` y la sustitución de la tabla local de `SetupTrainingSteps.kt` (`CandidateIncompatibility` y el aviso de la selección caída, `droppedSelectionNotice`) por el presentador. Ese archivo y `SetupWizardViewModel.kt` los serializan los pasos de A y de C (plan 00 §4b). Las evaluaciones del asesor cuestan 25–60 ms cada una en el equipo de desarrollo (KDoc del contrato de cobertura): hay que lanzarlas fuera de Main (r2 §15.3).
- **Textos que habrá que cambiar.** `SetupWizardCandidateGateTest` fija hoy el texto antiguo «Este plan necesita 75 min por sesión; elegiste 60 min.», que también escriben `CandidateIncompatibility` y `droppedSelectionNotice`; el presentador dice «Con las series mínimas este plan necesita N min por sesión y elegiste M.».
- **Botones de un toque sin etiqueta.** D5 nombra «Cambiar a Músculo», «Cambiar a Fuerza y músculo» y «Sí, tengo rack y banco»; el presentador solo trae botones de navegación («Cambiar objetivo», «Confirmar material») y «Ajustar a N min». Las etiquetas de `SwitchGoal` y `ConfirmApparatus` las fija A.C3.
- **`PROFILE_MISMATCH` es genérico.** El presentador usa la frase de la tabla de C.P11 («Este plan es de {disciplina}; tu objetivo es {objetivo}»). Para el plan propio de Fuerza y músculo sin resistencia externa (o con rack y banco negados y sin mancuernas), r2 §11.1 y el diseño editorial (11 §4) piden explicar el requisito de resistencia; la frase genérica sobre el plan del propio objetivo diría algo como «Este plan es de powerbuilding; tu objetivo es fuerza y músculo». Al cablear hay que añadir ese caso al presentador o no dar disciplina al plan propio (`disciplineLabelOf`).
- **Desviación de copy:** `APPARATUS_ABSENT` dice «Este plan necesita X, que dijiste que no tienes.» y no «…, que marcaste como que no tienes» (diseño editorial, 11 §4) ni una forma con pronombre («… no lo tienes»): el pronombre no concuerda con etiquetas como «rack de sentadilla», «mancuernas» o «barra y carga» (comentario del código). Decisión de copy pendiente del dueño.
- `ClearSplit` no se usa en producción hasta A.E2.

**Qué lo confirma.** `PlanRepairAdvisorTest`, `PlanRejectionPresenterTest` y `PlanCoverageContractTest` (con el asesor real: `NO_REPAIR` = 0).

### DEC-w2-03 — `requiredMinutes` exacto con el cardio intacto (A.C2, 2026-10-03)

**Estado:** implementada (paso A.C2 del paquete A, commit `e275d0bbc`). No contradice r2 y deja intacta DEC-w1-01: no añade ninguna palanca al fitter, solo cambia qué minutos se informan cuando el plan propio no cabe. Cierra la clase `TIME_BUDGET_INEXACT` del contrato de cobertura (que DEC-w2-01 y DEC-w2-04, parte 1, dejaban pendiente de A.C2) y el texto de tiempo de P-02.

**Qué dice r2.** §12.3, paso 5: sin variante que quepa, «devolver `TIME_BUDGET` con minutos mínimos calculados y acciones «más tiempo»/«otro plan», conservando respuestas. No devolver éxito parcial». §15.2: «Este plan necesita X min; elegiste Y» ofrece otros planes o editar el tiempo, y el rechazo lleva `requiredMinutes`. r2 pide el mínimo calculado; no dice cómo calcularlo.

**Qué ocurría.**
- Cuando el fitter agotaba sus palancas con `fit.maxMinutes > budget`, el rechazo informaba `fit.maxMinutes`, el mayor tiempo de sesión que midió el mejor esfuerzo del fitter, y el mensaje imprimía el presupuesto de la persona como si fuera el mínimo: «Este plan necesita $budget min por sesión y no cabe con las dosis mínimas; el mínimo real es de X min…».
- Ese esfuerzo no es una cota inferior. Caso medido: Atleta de 3 días a 20 min con 10 min de cardio da un esfuerzo de 31 min y, sin embargo, con 30 min ya hay programa (el paso 4 solo mueve un accesorio si los dos días caben en el presupuesto, así que con un presupuesto pequeño el esfuerzo queda por encima del mínimo real). En la rejilla del contrato, 1 584 filas, todas de Atleta con cardio, tenían programa con al menos un minuto menos que el informado (`TIME_BUDGET_INEXACT`).
- Efecto: «Ajustar a N min» habría ofrecido minutos de más y la UI habría dicho «necesita N min» con una N mayor que el mínimo.

**Qué se hizo.**
- **Ruta propia** (`SimpleCyclePersonalizer.personalizeNative`): `requiredMinutes` es el mínimo EXACTO. El generador prueba la MISMA entrada (mismo cardio, descansos, dosis mínimas, slots esenciales, días y split) con otros presupuestos, hasta 100 min, e informa el primero que produce programa. `exactMinimumMinutes(budget, hint, viableAt)` prueba la conjetura (el esfuerzo del fitter) y su vecino de abajo, que casi siempre basta; si el vecino también es viable, o la conjetura no lo era, bisecciona entre el presupuesto elegido (que se da por no viable sin probarlo) y el menor viable conocido (el techo de 100 en el segundo caso). Cota: 2 + ⌈log₂(100 − presupuesto)⌉ generaciones, como mucho 10 (el barrido lineal de `firstViableMinutes` llegaba a unas 80). Los sondeos no se anidan: `isViableAt` vuelve a `personalizeAt(…, probeMinimumMinutes = false)`, cuenta `IllegalArgumentException` e `IllegalStateException` como «no viable» y deja pasar `CancellationException`.
- **Atleta sin minutos de cardio explícitos:** se barre minuto a minuto (`minimumViableMinutes`), porque su cardio por defecto sube con el presupuesto (10, 15 y 20 min, r2 §11.4) y la viabilidad no es monótona. En el wizard el Atleta siempre manda sus minutos de cardio: ese caso solo lo alcanzan las llamadas directas.
- **Si ni 100 min bastan:** `TIME_BUDGET` sin minutos (`maxSessionMinutes = null`) y el mensaje «Con las series mínimas este plan no cabe ni con 100 min por sesión y elegiste M.»; no existe reparación `SetMinutes` y la UI no promete un ajuste que no funciona.
- **Mensaje:** «Con las series mínimas este plan necesita N min por sesión y elegiste M.». El diagnóstico de composición de algún intento intermedio del fitter va tras un salto de línea («Diagnóstico de composición (solo para el registro): …») para que el texto de la persona no lo incluya; la UI nueva armará su frase con el presentador a partir de `requiredMinutes`, nunca de este mensaje.
- **No cambia (DEC-w1-01):** cardio, descansos, dosis mínimas, slots esenciales, días y split. La ruta histórica (los ocho nativos) ya era exacta (barría minuto a minuto con `minimumViableMinutes`) y tampoco cambia.

**Tests: cifras antes → después** (contrato `full`: 25 650 filas, 4 hilos).

| Test u oráculo | Antes | Después | Por qué |
|---|---|---|---|
| `PlanCoverageContractTest` (`full`) | 1 704 violaciones: `TIME_BUDGET_INEXACT` 1 584 y `NOT_HONEST` 120 | **120**: `TIME_BUDGET_INEXACT` 0; quedan las 120 `NOT_HONEST` (`COMPOSITION`) de Atleta de 1 día, intermedio y avanzado, con solo barra de dominadas (E13) | con `requiredMinutes` hay plan y con `requiredMinutes − 1` sigue siendo `TIME_BUDGET` (cláusula `time_budget_minimum_is_exact`) |
| Techos `VIOLATION_CEILING` | smoke 110; ci 432 / 420 / 426 / 426; full 1 704 | smoke **8**; ci **30 / 30 / 30 / 30**; full **120** | ratchet: solo bajan |
| `NativeProfileRecipeAndFitterTest` | 32 tests | 33: nuevo `time_budget_reports_the_exact_first_viable_minute`, oráculo por barrido en 10 filas (al menos 8 son rechazos de tiempo): con el mínimo hay programa, ningún minuto entre el presupuesto y el mínimo lo tiene y la primera línea del mensaje es la nueva | fija el oráculo con el generador real |
| `NativePlanFailureMapperTest` | 15 tests | 21: +6 de `exactMinimumMinutes` (todos los umbrales con como mucho 10 sondeos, conjetura ya mínima, conjetura equivocada, techo no viable, borde exacto sin monotonía y propagación de la cancelación y de cualquier otra excepción) | fija el algoritmo |
| `PlanGenerationCoverageT006Test` (5 tests) y `SetupExecutableAvailabilityMatrixTest` (17) | verdes | verdes, sin tocarlos (mensaje del commit: T006 5/5 y matriz 17/17) | los mínimos que ya eran exactos (el piso de 21 min de Músculo en 1, 3, 5 y 6 días, F-m28) no cambian |

**Coste** (medido en el equipo de desarrollo; en el teléfono no se midió). El contrato `full` pasa de 376 a 638 s (+70 %; el informe de la corrida da 638,37 s con 4 hilos y 35 934 evaluaciones reales). Q2 de T006 pasa de 53,6 a 97,8 s en un benchmark del agente del paso (+82 %; cifras no archivadas), unos 88 ms más por rechazo de tiempo (44,2 s entre las 504 filas `TIME_BUDGET` de Q2). En el teléfono se estima 0,3–0,6 s por plan rechazado por tiempo (estimación, sin medir). La palanca para bajarlo es el coste fijo por llamada: cada evaluación real cuesta 25–60 ms porque el generador reconstruye su tabla de catálogo y el `legacyLookup` (`toLegacyConfigurationLookup`) en cada llamada, y un rechazo de tiempo paga hasta 9 o 10 llamadas más.

**Límite.** La bisección supone que la viabilidad crece con el presupuesto, que es lo que hace el fitter con minutos de cardio explícitos. Si no lo hiciera, el resultado sigue siendo un borde exacto (viable con él y no viable con uno menos), aunque no necesariamente el primero; lo cubre la prueba del borde sin monotonía.

**Relación con otras decisiones.**
- DEC-w1-01: intacta. Se prueban otros presupuestos con las mismas palancas; ningún cardio ni descanso se recorta.
- DEC-w2-01 y DEC-w2-04 (parte 1): cierra `TIME_BUDGET_INEXACT` (1 578, luego 1 584, ahora 0).
- DEC-w2-02: `SetMinutes` y `alsoMinutes` consumen este `requiredMinutes`.
- DEC-w2-07 y DEC-w1-03: los pisos que la matriz T-019 calcula por su cuenta (21 min en Músculo corporal, 28 min con material) coinciden con el mínimo informado: los oráculos no se movieron.

**Pendiente y riesgos.**
- Si el coste pesa en el teléfono, la palanca es cachear la tabla de catálogo y el `legacyLookup` entre sondeos, o limitar los sondeos en el wizard; no hay medición en dispositivo.
- La UI sigue diciendo «Este plan necesita N min por sesión; elegiste M min» (`CandidateIncompatibility` y el aviso de la selección caída) hasta C.P11: el número ya es exacto, la frase todavía no es la del presentador.
- Atleta de 1 día, intermedio o avanzado, con solo barra de dominadas (E13): las 120 filas de `COMPOSITION` siguen (B5 no añade remo ni jalón para ese material).

**Qué lo confirma.** `NativeProfileRecipeAndFitterTest.time_budget_reports_the_exact_first_viable_minute`, las 6 pruebas de `exactMinimumMinutes` de `NativePlanFailureMapperTest` y la cláusula `time_budget_minimum_is_exact` de `PlanCoverageContractTest`.

### DEC-w2-05 — El pase a peso corporal solo sigue a rechazos de material y publica siempre los rechazos del pase pedido (A.D4, B-07, 2026-10-03)

**Estado:** implementada (A.D4, junto con A.D2 y A.D3, commit `0b50bda5e`; hallazgo B-07; cláusula C4 del contrato de cobertura del plan 00 §6). No contradice r2: concreta su invariante. No cambia planes, recetas, dosis ni viabilidad: solo decide cuándo el wizard sustituye la lista pedida por la del material mínimo y qué explica.

**Qué dice r2.**
- §3, invariantes: «No usar el segundo pase de peso corporal para cambiar de perfil silenciosamente ni para devolver un plan de disciplina distinta.»
- §15.2: el ViewModel «no implementa un segundo filtro de material ni presume validez porque haya tarjeta publicada»; cada rechazo conserva su motivo cerrado; la UI distingue calculando, planes listos e incompatibilidad con acciones precisas. §17.2 (negativos indispensables): «error interno/catalog loading no se reporta como falta de material». §15.3: un resultado de material viejo no sobrescribe el nuevo.
- r2 no dice cuándo procede el segundo pase: lo cierra la cláusula C4 del plan de curaduría.

**Qué hacía el código.**
- `adaptedPass = ruta != PROTOCOL && requested.viable.isEmpty() && equipmentIds != {bodyweight}`: el pase a peso corporal se intentaba siempre que el pase pedido no dejara nada viable, fuera cual fuera la causa. Si el pase corporal daba plan, se mostraba esa lista con la razón «Plan KPKN adaptado a peso corporal: tu material no tenía una receta ejecutable».
- Los rechazos y los conteos que se publicaban eran los del pase corporal (`outcome.scan.rejections`), no los del pedido: el motivo real desaparecía. Un `TIME_BUDGET` del plan con tu material podía acabar en un plan de peso corporal presentado como falta de material, y un fallo interno o un catálogo sin cargar también disparaban el pase.

**Qué se hizo.**
- `bodyweightPassAllowed(requestedViableCount, rejections)` (`SetupWizardViewModel.kt`, función pura): el pase solo se intenta si el pase pedido no dejó viables, hay al menos un rechazo y TODOS son `APPARATUS_UNKNOWN` o `APPARATUS_ABSENT`. Lo impiden `TIME_BUDGET`, `PROFILE_MISMATCH`, `COMPOSITION`, `NO_VALID_SUBSTITUTION`, `UNRESOLVED_CONFIGURATION`, `FREQUENCY`, `LEVEL_UNSUITABLE`, `INTERNAL_MATERIALIZATION`, `CATALOG_NOT_READY` y un rechazo heredado sin código. La ruta PROTOCOL y la petición de solo peso corporal siguen sin segundo pase (D-005/E-015).
- `CandidateOutcome(requested, scan, useAdapted)`: las tarjetas del pase corporal se muestran solo si dio viables (`planAdaptedToBodyweight = true`, con la razón de siempre); `candidateRejections`, `candidateCounts` y el texto de conteo son SIEMPRE los del pase pedido (con el pase usado: 0 viables y todos los evaluados no viables).
- En el mismo paso (A.D2 y A.D3):
  - se borra el `sortedBy { ADAPTED → 1 }` de `collectViable` (DEC-w2-06): el orden es el editorial del planner;
  - la puerta de la lista es `candidateListGate` (B-01): el error del preview de la selección ya no esconde la lista (sale como aviso encima, con «Reintentar»); una selección que deja de ser viable pasa a `SetupDroppedSelection` con el aviso «Tu plan elegido ya no encaja con tus respuestas…» y una acción por motivo; con PLAN sin confirmar se limpia la selección y con PLAN confirmado se conserva y el paso queda pendiente de revisión; `submitCurrentStep(PLAN)` exige una selección viable;
  - la caché de la sesión no guarda fallos transitorios (`INTERNAL_MATERIALIZATION` ni `CATALOG_NOT_READY`; B-05);
  - `ensureCatalogLoaded` solo marca el catálogo como cargado si queda `Ready` (bajo `Mutex`) y, si no, lanza `CATALOG_NOT_READY`: la lista muestra «No pudimos cargar el catálogo de ejercicios. Reintenta.» y «Reintentar» vuelve a cargarlo (B-06).

**Tests: cifras antes → después.**

| Test u oráculo | Antes | Después | Por qué |
|---|---|---|---|
| `SetupWizardCandidateGateTest` | — | 20 tests (nuevo): B-01 (puerta de la lista, selección caída con PLAN confirmado y sin confirmar, `planSelectionGate`, orden del planner), B-05 y B-06 (catálogo que no carga y reintento tras un fallo transitorio) y B-07: `bodyweightPassAllowed` y tres pruebas con el ViewModel real (un `TIME_BUDGET` nunca dispara el pase y se publican sus rechazos; una mezcla de material y tiempo tampoco; solo los rechazos de material lo disparan y lo publicado es el pase pedido: 0 viables y todos no viables) | fija la regla |
| `PlanCandidateSessionCacheTest` | 5 tests | 9 | B-05: la caché no guarda fallos transitorios |
| `SetupProtocolSourceGuardTest` | el id forzado de otro plan entraba sin confirmar PLAN | el id forzado entra con PLAN ya confirmado | con PLAN sin confirmar la selección caída se limpia (B-01) y la guardia de activación ya no tendría nada que rechazar |
| `SetupExecutableAvailabilityMatrixTest` | 17 verdes | 17 de 17, sin tocar | mensaje del commit: matriz idéntica. Por lectura: A20 y A21 son solo peso corporal (`categories = emptySet()`) y nunca tuvieron segundo pase (la condición `equipmentIds != {bodyweight}` los excluía); en F-m20 todos los rechazos son `TIME_BUDGET` (DEC-w2-07), así que ya no entra al pase corporal, que a 20 min tampoco daba plan, y publica lo mismo (reconstrucción, no ejecución) |
| `T004FirstRejectionDiagnosticTest` | 1 test | 1 test, sin tocarlo | es una sonda del planificador y del generador, no del ViewModel |

Corrida final del paso (mensaje del commit): 38 suites, 321 tests, 0 fallos, con `SetupWizardFullJourneyTest` 2 de 2.

**Relación con otras decisiones.**
- DEC-w2-06: resuelve su pendiente del `sortedBy` de `collectViable` (borrado).
- D-005/E-015: bajo PROTOCOL sigue sin haber segundo pase.
- DEC-w2-01: la cláusula C4 queda cumplida en el ViewModel. `PlanCoverageContractTest` no modela el ViewModel y conserva un comentario `TODO A.D4 (C4)` que quedó obsoleto: el pase lo cubre `SetupWizardCandidateGateTest`.
- DEC-w2-02 (A.C3 y A.C4): el asesor y el presentador leerán los rechazos del pase pedido, que ahora son siempre los publicados.

**Pendiente y riesgos.**
- `NO_VALID_SUBSTITUTION` cuenta como «no es material» (regla literal: todos `APPARATUS_*`): un perfil con material parcial cuyo único rechazo que no es de material es una adaptación de autor sin sustituto (por ejemplo, PHAT adaptado con la barra baja negada, DEC-w2-04 parte 1) ya no recibe el pase. Es una línea de `bodyweightPassAllowed` si el dueño lo quiere distinto (decisión pendiente).
- Como el pase exige que TODOS los rechazos sean de material, solo aparece cuando todo lo candidato se rechaza por aparatos, y un solo rechazo de otra clase entre los candidatos lo impide. En la rejilla del contrato (informe `full`) el plan propio de Músculo y el de Atleta completo no se rechazan por aparatos, el de Fuerza y músculo solo lo hace con barra sola (el resto son `PROFILE_MISMATCH`, DEC-w2-01) y el de Fuerza sí. Es inferencia por lectura de la regla y de ese informe; no se midió la frecuencia del pase en el producto.
- El conteo «N planes evaluados · 0 viables · N no viables» convive con tarjetas del pase corporal: C.P5 debe tolerarlo.
- El aviso de la selección caída usa una tabla local de motivos que A.C4 sustituirá por `PlanRejectionPresenter`.

**Qué lo confirma.** `SetupWizardCandidateGateTest` (`theBodyweightPassOnlyFollowsRejectionsThatAreAllAboutMaterial` y las tres pruebas de B-07 con el ViewModel real), `PlanCandidateSessionCacheTest`, `SetupProtocolSourceGuardTest` y `SetupExecutableAvailabilityMatrixTest` (17 de 17).

---

## Decisiones de la ola 3 de la curaduría de programas (2026-10-03 y 2026-10-04)

Convención: `DEC-w3-NN` es una decisión del paquete B (recetas y progresión) del plan de curaduría de programas (`docs/audits/2026-10-programs/00-PLAN-curaduria-programas-2026-10-03.md`) que cierra un hueco o un conflicto sin contradecir r2; si lo contradijera sería un `DEV-r2-NN`. Las de la Fase 1 son `DEC-w3-01`, `DEC-w3-02`, `DEC-w3-03` y `DEC-w3-07` (plan 00 §6, paquete B). Cada decisión nombra el test que la confirma o la refuta. Al escribir estas notas no se ejecutó Gradle: las cifras «antes → después» salen de los mensajes de los commits (que citan sus corridas), de la lectura del código y de los tests, y los conteos de pruebas son de anotaciones `@Test` contadas en cada commit, no de una ejecución.

### DEC-w3-03 — La progresión del método se aplica siempre y la de rendimiento viaja como propuesta de TM (B.S3, B.S4 y B.S5, 2026-10-03/04)

**Estado:** implementada en el motor y en el detalle del programa: B.S3 (`5312decc2`, el método), B.S4 (`b18597085`, el rendimiento) y B.S5 (`2d411c58f`, el TM en pantalla). Faltan los datos de las recetas (B.S6, que decide qué regla lleva cada una) y la interfaz de la Fase 2 (B.S13). No contradice r2. El principio es el del plan 00 (§6, B.S3–S5) y separa dos clases de progresión:
- **La del método** (`CycleIncrement`, `WeeklyKg`) es contenido de la receta y se aplica siempre, con la autorregulación en OFF, PROPOSE o AUTO.
- **La de rendimiento** (`AmrapDrivenTm`, `RepTargetDrivenTm`, `TopSetPr`, `RepMaxAutoregulated`) sale como propuesta `ADJUST_TM` y respeta OFF (nada), PROPOSE (queda pendiente) y AUTO (se aplica con auditoría).

Registro de consumidores (`ProgressionConsumers`, en `AuthoredProgressionEngine.kt`): reglas de autor = {`CycleIncrement`, `WeeklyKg`}; autorregulación = {`AmrapDrivenTm`, `RepTargetDrivenTm`, `TopSetPr`, `RepMaxAutoregulated`}. `WeeklyPercent` sigue sin consumidor en `2d411c58f` y solo la declara `madcow-5x5`: B.S6 pasa Madcow a `CycleIncrement` y la saca de las recetas. En el contrato de receta (regla C6) los hallazgos pasan de 16 a 7 (B.S3) y a 2 (B.S4); el inventario, de 466 a 457 y a 452, con el techo `CEILING` en 452. Los dos C6 que quedan son `WeeklyPercent` de `madcow-5x5` y la exigencia de AMRAP de `kpkn-rts-style` (`RepTargetDrivenTm` sin series AMRAP).

**Qué dice r2.**
- §12.4 (l.435): «La progresión propia no modifica la regla original de otros protocolos ni el algoritmo AUGE global», y las adaptaciones de originales «usan la regla del original mientras sea semánticamente compatible». r2 no describe ningún motor para las recetas fijas: pide que su regla se respete. Hasta B.S3 la regla de varias recetas estaba declarada y no hacía nada.
- §12.4 (l.431): las propuestas se confirman con el flujo existente y no cambian kilos registrados retroactivamente. Eso rige la progresión por rendimiento, que es una propuesta, y se cumple: el flujo es el de OFF, PROPOSE y AUTO de siempre y cada registro conserva su carga. La del método no es una propuesta sino contenido de la receta (el 5/3/1 prescribe su subida por ciclo): no pide confirmación y tampoco toca kilos registrados; lo que se recalcula es la prescripción de lo que viene (ver «Pendiente y riesgos» para las sesiones que el ciclo siguiente reutiliza).
- DEV-r2-05 (r2 §12.1, l.399): al terminar el bloque el programa continúa solo y se avisa una vez, con un único aviso que sustituye al de caducidad.

**Qué hacía el código** (hallazgos L-03, L-04, L-16, R-02, R-11, R-17 y R-23; el plan 00 no separa cuál de R-02, R-11 y R-17 describe cada punto).
1. `tmDeltaForAmrap` devolvía `(kg ÷ 100) × 2,5`: un porcentaje calculado a partir de una tabla en kilos, es decir, de 0,06 a 0,19 % del TM en lugar de 2,5 a 7,5 kg (L-04). Un AMRAP corto con 2 o más repeticiones caía en la misma tabla y devolvía un delta positivo: error de signo.
2. El camino positivo de `RepTargetDrivenTm` (SBS, +0,5 % por repetición) era inalcanzable: `buildProposals`, fuera del caso corto, solo miraba `AmrapDrivenTm`.
3. `liftSlotFor` buscaba `contains("deadlift")` y `contains("squat")` en el id de la configuración: el rumano, la sentadilla hack y la frontal contaban como peso muerto o sentadilla. Una serie sin levantamiento (las dominadas de Texas, L-16) generaba `ADJUST_TM` con `liftSlot = null` y `applyTmDelta(null)` escalaba los cuatro TM.
4. `collectAmrapHits` filtraba la semana por `weekId` sin ciclo (mezclaba ciclos) y tomaba la última serie de todas las coincidentes, sin distinguir sesión ni serie: como el historial llega del más reciente al más antiguo, leía el log más antiguo y, con un T1 y un T2 de la misma configuración, la serie de cualquiera de los dos.
5. `TopSetPr` y `RepMaxAutoregulated` no tenían consumidor (L-03), y `PlanObserver` prometía «≥5 reps y la TM sube 2,5 kg» a toda receta con `AmrapDrivenTm`, con independencia de su tabla (que da 5 kg con 4 o 5 repeticiones y 2,5 kg con 2 o 3).
6. (B.S3) `CycleIncrement` y `WeeklyKg` no tenían consumidor (L-03): el 5/3/1 no subía el TM al cerrar el ciclo y Smolov y Smolov Jr no sumaban sus kilos por semana. Además `rematerializeWeek` reconstruía con `startDay = 1` y sin los días del split, así que rotaba los días (R-23).

**Qué se hizo.**

*B.S3, el método* (`AuthoredProgressionEngine`, dominio puro; `PlanMaterializer`; `ProgramProgressEngine`):
- `CycleIncrement(upperKg, lowerKg, scope)` sube el TM de cada levantamiento de `recipe.liftSlots`: `upperKg` en banca y press militar, `lowerKg` en sentadilla y peso muerto. El TM sale del perfil guardado o, sin él, de `1RM × trainingMaxPercent`. El resultado se redondea al paso del inventario (el doble del disco más pequeño; 0,5 kg sin discos declarados), sin que el paso supere nunca el incremento del método y sin que el TM baje.
- Enganche por ciclo (`scope = CYCLE`): `ProgramProgressEngine.completeCycle`, DESPUÉS de avanzar `cycleNumber` (si no, `weekRecipeSourceFor` arrastraría las semanas escaladas del ciclo cerrado). Enganche por bloque (`scope = BLOCK`, Juggernaut por ola): `advanceComplexAfterSessionComplete`, al entrar en el bloque siguiente; entrar en el primer bloque no es un cierre, ni lo es entrar en una «Descarga (auto)» de AUGE. Con progresión nativa activa (`recipe.nativeProgression` distinta de `NONE`) el motor de autor no actúa.
- Reconstrucción: solo las semanas de los bloques que vienen de la receta (`Block.sourceDefinitionId == recipe.id`, sin semanas de loop). Al cerrar el ciclo se rematerializan con `executedWeekIds = ∅` (en el ciclo nuevo no hay nada entrenado); al entrar en un bloque, las sesiones con registros del run no se reconstruyen. Las sesiones con ajustes manuales (`manualSessionOverrides`) se conservan y el aviso dice cuántas; los días que el atleta movió arrastrando una sesión se restauran.
- Idempotente por marca (`AppliedRecipeProposal` con `proposalId` `author-cycle-c<N>`, con N el ciclo nuevo, o `author-block-b<N>`, con N el índice del bloque al que se entra) y con un aviso único del mismo id, con coma decimal y solo los levantamientos que cambian: «Nuevo ciclo: TM sentadilla 180 → 185 kg, banca 108 → 110,5 kg, peso muerto 198 → 203 kg.».
- Respaldo: sin metadatos del catálogo, o si la reconstrucción falla, el TM sube igual, las cargas quedan como estaban, los bloques quedan con `materializationPending` (botón RE-MATERIALIZAR) y la causa técnica va al resumen de la marca, no al aviso. Sin perfil de cargas o sin TM que subir no se hace nada.
- `WeeklyKg`: `PlanMaterializer.materializeSet` suma los kilos de la semana a la carga resuelta de la serie principal (T1 con `liftSlot`) y deja `targetPercentageRM` coherente con ese kilo; sin base de carga el peso queda `null` y el porcentaje es el de la receta.
- R-23: `rematerializeWeek` y `materialize` comparten el calendario (`resolveWeekSchedule`: `startDay` y días del split); reconstruir una semana ya no rota los días.
- `ProgramRepository` entrega al avance de sesión el inventario de discos del atleta, y `executedTrainingEvidence` filtra por ciclo también en los programas Simples cíclicos (antes, tras el ciclo 1 de un 5/3/1, RE-MATERIALIZAR no recalculaba nada).
- Modelo y JSON: `IncrementScope { CYCLE, BLOCK }` (por defecto `CYCLE`), `CycleIncrement(upperKg, lowerKg, scope)` y `TopSetPr(upperKg = 1,25; lowerKg = 2,5)` como `data class` con `@SerialName("top_set_pr")`. El JSON anterior decodifica y no hay migración de Room.

*B.S4, el rendimiento* (`ProgramAutoregulationEngine`):
- `AutoregulationProposal.kgDelta` (por defecto `null`) manda sobre `percentDelta` en `ADJUST_TM` y `PROMOTE_TM`; `applyTmKgDelta` redondea a 0,5 kg y, sin TM guardado, parte del 1RM por el porcentaje de la receta. El JSON anterior decodifica con `null`.
- AMRAP (`amrapTmChange`): lo corto se evalúa primero y nunca sube. Es corto el AMRAP con menos repeticiones que el objetivo, o con 1 o menos desde el 90 % del TM aunque el objetivo fuera 1+ (`SHORT_AMRAP_SINGLE_MIN_PERCENT`, cláusula heredada). Un AMRAP corto baja 2,5 % con `AmrapDrivenTm` (su tabla en kilos no define bajadas), 1 % por repetición que falta con `RepTargetDrivenTm` (solo si faltan 2 o más) y 2,5 % con cualquier otra regla si faltaron repeticiones. Si no es corto, `AmrapDrivenTm` propone los kilos de su tabla (0, 2,5, 5 y 7,5 kg con 0-1, 2-3, 4-5 y 6 o más repeticiones; 0 kg es nada) y solo desde el 85 % del TM (`AMRAP_TM_MIN_PERCENT`); `RepTargetDrivenTm` propone +0,5 % por repetición sobre el objetivo, sin umbral de intensidad. Una serie hecha con menos del 97,5 % de la carga prescrita (`LIGHTER_LOAD_RATIO`) no sube el TM; sí puede bajarlo.
- Lectura de los registros: `collectAmrapHits` filtra por ciclo y run (`logsForInstance`), toma el log MÁS RECIENTE de cada sesión y empareja por la serie marcada `amrapPerformed` (sin marca, por posición y solo si el registro trae todas las series del plan). El levantamiento de cada serie sale del slot de la receta (`recipeDayId` + `recipeSlotId`; si no, configuración y rol); el texto del id queda como último recurso y excluye rumano, hack, frontal, goblet, zancadas y otras variantes. Sin levantamiento no hay propuesta: las dominadas de Texas ya no escalan los cuatro TM. `applyMutations` ignora las propuestas de TM sin levantamiento y las pendientes antiguas caducan con el motivo «la propuesta no indica a qué levantamiento corresponde».
- `TopSetPr` (`collectTopSetHits`, `topSetTmChange`): con las repeticiones del objetivo sube el incremento del levantamiento (`upperKg` o `lowerKg`); con 2 o más sobre el objetivo (`TOP_SET_REPS_MARGIN`), el doble; con 2 o más por debajo, baja 2,5 %; con una de menos no cambia nada; con menos carga que la prescrita no sube.
- `RepMaxAutoregulated` (`collectRepMaxHits`, `repMaxTmChange`; mínimo y conservador): compara el e1RM de las series al máximo (base `REP_MAX`, o top set sin porcentaje, de 1 a 10 repeticiones) del levantamiento de competición de la receta con el 1RM que implica el TM (TM ÷ `trainingMaxPercent`): desde ×1,025 (`REP_MAX_RAISE_RATIO`) sube el TM 2,5 % (`TM_UP_PERCENT`) y hasta ×0,95 (`REP_MAX_DROP_RATIO`) lo baja 2,5 %. Las variantes no cuentan.
- «Un lift con AMRAP corto no sube»: los enganches de ciclo y de bloque calculan `shortAmrapLifts` del ciclo o bloque que se cierra y excluyen esos levantamientos del incremento del método; el aviso único lo dice: «…; banca se mantiene en 108 kg (AMRAP corto).». Si el AMRAP corto congela todas las subidas, el ciclo se cierra sin cambios de TM ni reconstrucción y deja igualmente su marca y su aviso. El AMRAP corto de un ciclo no congela el siguiente.
- `PlanObserver.amrapLine` lee la tabla real de la receta (o el porcentaje de `RepTargetDrivenTm`) y `ProgramRepository.buildWeeklyAutoregulationSignals` pasa ciclo, programa, run y receta a `collectAmrapHits`.

*B.S5, el TM en pantalla* (R-03, R-04, R-19, H13, H14):
- «Guardar TM» (R-04): `TrainingMaxMerge.merge(old, new, trainingMaxPercent)` fusiona el perfil que devuelve el asistente de TM, que solo edita los cuatro 1RM, con el del programa, por levantamiento: 1RM igual → conserva el TM del programa (puede traer un ajuste de una propuesta o del método); 1RM distinto → TM = 1RM × porcentaje de la receta (90 % sin receta); 1RM en blanco → no cambia nada. Las variantes, la modalidad y los estimados son siempre los del perfil viejo. `ProgramDetailViewModel.updatePowerliftingProfile` guarda el perfil fusionado y, si hay receta, reconstruye en la MISMA mutación durable las semanas de los bloques de la receta con la evidencia real de entrenamiento (`executedTrainingEvidence`; lo entrenado y lo editado a mano se conserva) y avisa «TM actualizado: 3 semanas recalculadas, 1 entrenada intacta» (con plurales). Si la reconstrucción falla, el TM se guarda, los bloques quedan pendientes y el aviso remite a RE-MATERIALIZAR; un error de escritura sale en rojo.
- Test de 1RM (R-19): `resolvePendingOneRmTest`, con un resultado registrado, fusiona los 1RM probados con el mismo `TrainingMaxMerge` y marca `materializationPending` en los bloques de la receta; el ViewModel los reconstruye con la evidencia y avisa «1RM registrado. TM actualizado: …». «Omitir» no toca el perfil.
- R-03: «VER PROPUESTA» solo navega a la pestaña Semana, donde APLICAR, ACEPTAR TODO y RECHAZAR siguen en la tarjeta; ya no llama a `acceptAutoregulation()`.
- H13: `PlanMaterializer.materialize` retira de `effectiveWeekRecipes` y de `nativeProgressionAudit` las marcas y avisos de autor (`author-cycle-c`, `author-block-b`) de un run anterior; un cierre de ciclo posterior vuelve a subir el TM. Las marcas `native-progression-c<N>` no se limpian (ver «Pendiente y riesgos»).
- H14: la tarjeta de progresión titula «Nuevo ciclo» (`author-cycle-c`) y «Nuevo bloque» (`author-block-b` y `native-cycle-c`); cualquier otro aviso, «Progresión de carga».
- `usesTrainingMax` (decide si la tarjeta de propuestas muestra y deja editar el TM) cubre ahora todas las reglas que mueven el TM y las series de trabajo `PERCENT_TM` de un levantamiento.

**Tests: cifras antes → después.** Primero el comportamiento (oráculos del plan 00 §10.4 y casos de B.S4), todo medido por los tests que se citan al final:

| Caso | Antes | Después |
|---|---|---|
| 5/3/1 BBB al cerrar el ciclo, 1RM 200 / 120 / 220 (TM 180 / 108 / 198) | el TM no cambiaba | 185 / 110,5 / 203 kg (`CycleIncrement(2,5; 5,0)`: +5, +2,5 y +5) |
| El mismo cierre con discos de 1,25 kg (paso de 2,5 kg) | — | 185 / 110 / 202,5 kg |
| Con discos de 2,5 kg (paso de 5 kg), partiendo de TM 180 / 110 / 200 | — | 185 / 112,5 / 205 kg: el paso nunca supera el incremento, así que la banca sube 2,5 y no 5 |
| El primer cierre con un AMRAP corto de banca (3 repeticiones donde pedía 5) | — | 185 / 108 / 203 kg y el aviso «…; banca se mantiene en 108 kg (AMRAP corto).» |
| Smolov Jr, primera sesión de cada semana (6×6 al 70 %), 1RM 200, semanas 1 a 3 | 140 / 140 / 140 kg | 140 / 145 / 150 kg |
| `AmrapDrivenTm`, AMRAP al 95 % (1+) con 5 repeticiones | +0,125 % del TM (≈ +0,2 kg con TM 180) | +5 kg |
| `AmrapDrivenTm`, AMRAP corto (3 repeticiones de 5) | +0,06 % (subía) | −2,5 % |
| `AmrapDrivenTm`, AMRAP al 70 % con 12 repeticiones | ≈ +0,3 kg con TM 180 | nada (no llega al 85 %) |
| `RepTargetDrivenTm`, 9 repeticiones sobre 6 | nunca se ejecutaba | +1,5 % |
| `TopSetPr`, sentadilla con 5 y con 7 repeticiones (objetivo 5) | sin consumidor | +2,5 kg y +5 kg |
| `TopSetPr`, 3 repeticiones (objetivo 5) | sin consumidor | −2,5 % |
| `RepMaxAutoregulated`, e1RM de 216 y de 174,9 kg frente a 200 kg de 1RM implícito | sin consumidor | +2,5 % y −2,5 % |
| Contrato de receta: hallazgos C6 / inventario / `CEILING` | 16 / 466 / 466 | 7 / 457 / 457 tras B.S3 y 2 / 452 / 452 tras B.S4 |

Y los conteos de pruebas, contados como `@Test` al final de cada commit (la columna «B.S5» es también `2d411c58f`, el HEAD al escribir esta nota):

| Clase de pruebas | Antes de B.S3 | B.S3 | B.S4 | B.S5 |
|---|---|---|---|---|
| `AuthoredProgressionEngineTest` | — | 15 (nuevo) | 18 | 18 |
| `ProgressionConsumerCoverageTest` | — | 6 (nuevo) | 6 | 6 |
| `ProgramProgressCycleCloseTest` | 6 | 26 | 31 | 37 |
| `TopSetProgressionTest` | — | — | 17 (nuevo) | 17 |
| `ProgramAutoregulationEngineTest` | 7 | 7 | 24 | 24 |
| `ProgramAutoregulationResolutionTest` | 5 | 5 | 11 | 11 |
| `PlanMaterializerTest` | 12 | 18 | 18 | 20 |
| `TrainingMaxMergeTest` | — | — | — | 13 (nuevo) |
| `ProgramDetailViewModelTest` | 58 | 58 | 58 | 74 |
| `NativeProgressionCardModelTest` | 13 | 13 | 13 | 15 |
| `ProgramRepositoryConsolidationTest` | 12 | 15 | 15 | 15 |
| `TrainingPlanRecipeJsonCompatTest` | 11 | 16 | 16 | 16 |
| `ProgramProgressEngineTest` | 9 | 10 | 10 | 10 |
| `RelatorPlanAwareTest` | 6 | 6 | 10 | 10 |
| `RecipeContractPolicyTest` | 27 | 27 | 27 | 27 |
| `RecipeContractInventoryTest`, techo `CEILING` | 466 | 457 | 452 | 452 |

Los mensajes de B.S3 y B.S4 traen conteos desfasados. B.S3 dice «`ProgramProgressCycleCloseTest` 18 → 26» (real: 6 → 26), «`ProgramRepositoryConsolidationTest` (+2)» (real: +3) y «`TrainingPlanRecipeJsonCompatTest` (+2)» (real: +5), y no cuenta el `@Test` nuevo de `ProgramProgressEngineTest`. B.S4 dice «`ProgramAutoregulationEngineTest` 6 → 23» (real: 7 → 24). Los de B.S5 coinciden con los `@Test` reales. Corridas que citan los mensajes (no repetidas al escribir esta nota): B.S3, 590 tests y 0 fallos; B.S4, dos corridas y 0 fallos; B.S5, 406 casos y 0 fallos.

**Relación con otras decisiones.**
- DEV-r2-05 (aviso único al terminar el bloque): sigue siendo uno solo. El cierre de ciclo de una receta de autor deja un aviso (`author-cycle-c<N>`) y la entrada a un bloque nuevo, otro (`author-block-b<N>`); ambos son idempotentes por su marca. Con progresión nativa activa el motor de autor no actúa, así que su aviso y el de la continuación nativa (`native-cycle-c<N>`, «Empiezas un nuevo bloque … con tus últimas cargas.») no se suman. Desde B.S4 el aviso dice también lo que se mantiene por un AMRAP corto y, desde B.S5, la tarjeta lo titula «Nuevo ciclo» o «Nuevo bloque».
- DEC-w3-02 (base 5RM, por escribir) y D7: el motor sube el TM del perfil (el guardado o, sin él, `1RM × trainingMaxPercent`). Con D7 (Texas y Madcow a 0,87, `4aa1bbb10`) ese TM es aproximadamente el 5RM; cuando B.S6 pase Madcow a `CycleIncrement` (plan 00 §6), la subida actuará sobre él. DEC-w3-02 fijará la base 5RM de esos planes.
- DEC-w3-01 (contrato C1–C10, por escribir): la regla C6 lee el registro de consumidores de esta decisión.

**Pendiente y riesgos.**
- **Propuestas en % anteriores a B.S4.** Las que estaban pendientes se siguen aplicando con `percentDelta`; las que no traen levantamiento (`liftSlot = null`) ya no se aplican y caducan con motivo (`outcomeEntries`).
- **La cláusula heredada «1 repetición o menos desde el 90 % del TM es un AMRAP corto»** también congela el levantamiento en la semana 3 del 5/3/1 (1+ al 95 %) cuando solo sale una repetición, que es el mínimo que pide el «1+».
- **La última semana de cada ciclo no pasa por la autorregulación semanal:** `completeCycle` no la evalúa, así que sus AMRAP y top sets no generan propuesta de TM. Sus AMRAP sí cuentan para «un lift con AMRAP corto no sube», porque `shortAmrapLifts` lee todas las semanas del ciclo.
- **Se reescribe la prescripción mostrada del ciclo cerrado** (plan 00 §11, «Programas ya activos»): las sesiones se reutilizan entre ciclos y el cierre rematerializa sus semanas con el TM nuevo; los registros del ciclo cerrado conservan sus kilos. Afecta a los programas ya activados con una receta que ejecute `CycleIncrement` (hoy, los dos 5/3/1). Con la autorregulación en OFF la subida del método se aplica igual: es contenido, no una propuesta.
- **Marcas `native-progression-c<N>` sin limpiar al re-materializar** (H13 limpió solo las de autor): defecto latente anterior a B.S5. Por la lectura de `registerNativeContinuationOnce`, que usa esa marca como guarda, tras re-materializar el cierre de un ciclo con el mismo número no volvería a registrar la continuación nativa, y el aviso `native-cycle-c<N>` tampoco se limpia. Es una inferencia de código, sin prueba. Pendiente en B.S11 o B.S13 (sección 7 del README de la curaduría).
- **Datos de recetas pendientes de B.S6** (el motor ya los ejecuta; los datos todavía no son los buenos):
  - nSuns: el AMRAP está en la última serie, al 65 %, y con el umbral del 85 % nunca sube el TM; hay que moverlo a la serie 1+ al 95 % de los lower (a verificar contra la hoja de nSuns).
  - Lilliebridge declara `TopSetPr`: cada top set cumplido propondría una subida de TM (2,5 kg en sentadilla y peso muerto, 1,25 kg en banca) hasta que B.S6 la pase a `None`.
  - Texas: el chin con `amrap` no tiene levantamiento y hoy no propone nada; B.S6 le quita el `amrap`.
  - `kpkn-rts-style` declara `RepTargetDrivenTm` sin series AMRAP (el C6 restante junto a `WeeklyPercent` de Madcow).
  - `RepMaxAutoregulated` no produce propuestas con los datos actuales: las series al máximo de Westside son de variantes (sentadilla al cajón, buenos días, press con cadenas…), que no cuentan, y `gzcl-jt-2` prescribe por porcentaje.
  - Juggernaut declara `CycleIncrement` por ciclo en una receta que no se repite, así que el enganche de ciclo nunca corre en ella (`scopePending = {juggernaut-2}` en `ProgressionConsumerCoverageTest`); B.S6 la pasa a `IncrementScope.BLOCK`.
  - Comentarios desfasados: `TrainingPlanRecipe.kt` (~l.384, «Su consumidor llega con B.S4») y `RecipeContractPolicy.kt` (~l.366) siguen diciendo que los consumidores de `TopSetPr` y `RepMaxAutoregulated` están por llegar.
- **B.S6, parte 2** (antes de activar `BLOCK` en Juggernaut): H7, porque `resolvePendingDeload(reject)` y `advanceAfterPendingAction` entran al bloque siguiente sin pasar por el enganche de bloque y no subirían el TM; y H10, porque `WeeklyKg` suma sus kilos sin ningún tope.
- **B.S13 (Fase 2):** aviso «no aplicable» al aceptar una propuesta (R-13), selector de modo con confirmación para AUTO (R-15) y tarjeta «Programa terminado → Repetir con TM actualizado» (la parte de R-19 que B.S5 no cubre).

**Qué lo confirma.** `AuthoredProgressionEngineTest` (18); `ProgressionConsumerCoverageTest` (6: el registro, el alcance de cada `CycleIncrement` frente a la estructura de la receta y las listas de pendientes); `ProgramProgressCycleCloseTest` (37: el cierre del 5/3/1 de 180 / 108 / 198 a 185 / 110,5 / 203, los discos, los ajustes manuales, los días movidos, la descarga de AUGE, el respaldo sin metadatos, los AMRAP cortos, las olas por bloque y el test de 1RM); `TopSetProgressionTest` (17, con el recorrido de todas las recetas publicadas resolviendo el levantamiento de cada AMRAP y top set, y de punta a punta en AUTO); `ProgramAutoregulationEngineTest` (24); `ProgramAutoregulationResolutionTest` (11); `PlanMaterializerTest` (20: `weekly_kg_offsets_squat_sets_by_week`, R-23 y H13); `TrainingMaxMergeTest` (13); `ProgramDetailViewModelTest` (74: «Guardar TM», test de 1RM, RE-MATERIALIZAR y `verPropuesta_only_navigates_to_the_week_tab_and_never_accepts_the_proposal`); `NativeProgressionCardModelTest` (15); `ProgramRepositoryConsolidationTest` (15; evidencia por ciclo); `TrainingPlanRecipeJsonCompatTest` (16; JSON compatible); `RelatorPlanAwareTest` (10); `RecipeContractPolicyTest` (27) y `RecipeContractInventoryTest` (`CEILING` 452).
