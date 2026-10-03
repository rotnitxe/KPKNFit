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

---

## DEV-r2-01 — Variantes corporales sin tirón: el MRV de glúteos (16) prevalece sobre el calendario literal

**Estado:** **ACEPTADA por el dueño del producto el 2026-10-02.** Su criterio, textual: «Si en gluteos se pasa de series, no hay problemas, siempre y cuando no sea tanta la diferencia»; y sobre la recomendación de conservar el plan implementado: «apliquemos todo lo que me dices y recomiendas» (2026-10-02). Decisión: para quien entrena sin ningún material **se conserva el plan implementado** (puente 1 vez por semana en Músculo y 2 en Atleta; glúteos 15–16 series, 16 como máximo) y **no se vuelve al calendario literal de r2**. El plan r2 externo no se edita desde este repositorio y sigue con `user_approval: PENDING`: esta aceptación y el anexo fechado de DEV-r2-02 (más abajo) son el registro hasta que el dueño apruebe una r3 o incorpore un anexo al plan (con entrada en §9 y §18).

**Ámbito:**
- `native:muscle-foundation-v2` (Músculo) sin soporte de tirón, 5 y 6 días.
- `native:complete-athlete-v2` (Atleta completo) sin soporte de tirón, 6 días.

No cambia Músculo de 1–4 días, Atleta de 1–5 días, ningún perfil con tirón disponible (banda, mancuernas, barra, polea, barra de dominadas) ni Fuerza ni Fuerza y músculo. La referencia de «sin tirón» es `pullAvailable == false` en `SimpleCyclePersonalizer.personalizeNative`.

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
