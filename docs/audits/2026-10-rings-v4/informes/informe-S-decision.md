# Informe del agente S · decision (auditoría RINGS/AUGE, 2026-10-12)

> Entregado por un subagente Haiku 5.5 (tipo Explore, solo lectura) y copiado **sin editar**. La verificación, las correcciones de severidad y las comprobaciones propias están en el anexo A del plan (`PLAN.md` de esta carpeta). Las referencias `archivo:línea` son de `master` @ `00431a27d`.

## Auditoría de motores de decisión RINGS/AUGE (solo lectura)

Referencia: HEAD `00431a27d`. El alcance no tiene cambios locales (`git status` sobre `domain/`, `screens/` y `test/`). No ejecuté Gradle ni tests. Los cálculos son réplicas en Python de una línea; la réplica de readiness reproduce el 74 que fija el test B15. Recuento: 4 P0, 12 P1, 4 P2.

## A. Mapa de constantes

| Motor | Constante | Valor | archivo:línea | Unidad | Propósito |
|---|---|---|---|---|---|
| Readiness | ADJUSTMENT_THRESHOLD | 70 | ExerciseReadinessEngine.kt:17 (=RecoveryBands.kt:17) | score 0–100 | umbral de ajuste |
| Readiness | MIN/MAX_REDUCTION, CAP | 0.20 / 0.80 / 0.30 | :19–21 | fracción | severidad y tope de recorte |
| Readiness | pesos | wMusc=1/T, wCNS=cnc/5/T, wSpine=axial/T, wArt=ttc/5/T | :110–122 | adimensional | mezcla de componentes |
| Readiness | setsPenalty | max(0.85, 1−0.01·(series−3)) | :131–133 | factor | series extra |
| Readiness | intensityPenalty | 0.98 si RPE≥9.5; 0.97 si ≥10 | :136–140 | factor | intensidad |
| Readiness | ermPenalty | 0.95 si planificado/eRM>0.85 | :142–155 | factor | proximidad a 1RM (S-02) |
| Readiness | ttcHardCap | 25+(1−ttcF)·20 … 100 si articular<60 | :160–167 | puntos | techo articular |
| Bandas | HIGH/NORMAL/MODERATE/LOW | 85/70/50/35 | RecoveryBands.kt:11–14 | score | bandas |
| Overlay | color verde/ámbar | 75/50 | SetAdjustmentOverlay.kt:65–69 | score | color (no coincide con 70) |
| Auto-regulación carga | umbrales y factores | 70/50; 0.70/0.85/1.05 | WorkoutFeedbackModels.kt:435–439 | score; factor | factor de peso |
| Relator | ajuste de carga | <60 → −5 %; <45 → −10 % | ReadinessObserver.kt:20, 28 | % | copy de carga |
| Coach | buckets | 80/60/40; null→HIGH | WorkoutCoachMessages.kt:158–161 | score | tono |
| Avisos | WATCH/ADJUST/UNLOAD; HYSTERESIS | 1.2 / 1.4 / 1.6; 0.10 | LoadAdvisoryEngine.kt:23–26 | ACWR | niveles |
| Avisos | ADJUST por frecuencia | ≥4 días axiales en 6 | :122 | días | Columna |
| Monitores | ventanas; mínimos | aguda 7 d; crónica 28 d ÷4; ≥4 sesiones; ≥14 d | AxialLoadMonitor.kt:23–25, 40–44; SystemicLoadMonitor.kt:16–17, 39–44 | días | ACWR |
| Monitores | AXIAL_MIN | 0.5 | AxialLoadMonitor.kt:23 | factor axial | exclusión (22 configs en [0.4,0.5)) |
| Clasificadores | zonas ACWR; factor muscular | 1.3 / 1.5; ×1.06 / ×1.12 | AugeClassifiers.kt:37–42, 57–61 | ACWR | anillo Músculos |
| Clasificadores | multiplicador RPE | 0.6 (<8); 1.0 (≥8); 1.2 (≥10) | AugeClassifiers.kt:20–24 | factor | volumen efectivo |
| Sobrecarga | factores; umbral | 5; ≥3; fatiga≥8; DOMS≥3.5; fuerza≤5 | OvertrainingDetector.kt:67, 88, 123 | — | detector |
| Bloques | EMA estrés | ≥75 | BlockTransitionEngine.kt:142 | unidades estrés | DELOAD de bloque |
| Autorregulación | autoDeload | readiness<40 y fatiga>75 | ProgramAutoregulationEngine.kt:933 | — | INSERT_DELOAD |
| AUGE | flag auto-deload | augeAutoDeload | AugeFatigueEngine.kt:1058 | — | compuerta (parcial, S-04) |
| TTC | escala | 0–5; reglas 1.0–3.0 | AugeTtcEngine.kt:25, 116–161 | 0–5 | coste tendinoso |
| Articular | decay; plateau | k=2/48 ó 2/60 h; 0.05·h <24 h | AugeTtcEngine.kt:312–313, 322–326 | h | recuperación |
| Articular | molestias | 10 (post); 15 (sin resolver, decae 168 h) | :31–33 | puntos | estrés |
| Interferencia | ventana; umbrales | 96 h; drenaje≥0.08; uso≥0.25; residual≥0.03 | InterferenceEngine.kt:40–44, 454, 460 | h; fracción | solape |
| Interferencia | semividas | 24 / 48 / 72 / 96 h | :26–38 | h | decay |
| Nutrición (off) | déficit; superávit | <0.9 → 1+1.2·(1−r); >1.1 | NutritionRecoveryEngine.kt:99–103, 108 | ratio | τ |
| RingBudget | proyección | 0.72·v − 0.0022·v² | RingBudgetPolicy.kt:34–38 | puntos CNS | semana |
| Molestias | penalización | 0.95 (1 solape); 0.90 (≥2) | ExerciseReadinessEngine.kt:401–405 | factor | readiness |

## Respuestas a las preguntas obligatorias

| Q | Respuesta breve | Ver |
|---|---|---|
| 1 Readiness | Articular contada dos o tres veces. Escalones: umbral 70; suelo 0.85 a partir de 18 series; RPE 9.5/10; eRM 0.85; techo articular salta ~2 puntos al pasar de 59 a 60. Redondeo a 2.5 kg sin inventario. `targetPercentageRM` tratado como fracción (bug). NaN solo con el ejercicio tibial. Resto de consumidores con guarda. | S-01, S-02, S-06, S-10 |
| 2 Monitores | ACWR = carga 7 d / (28 d ÷ 4): acoplado y denominador fijo. Simulación: (a) ADJUST hoy (1.60); (b) ADJUST días 15–21 tras la vuelta (1.71→1.33), WATCH día 24, NONE día 27; (c) NONE (1.0); (d) ADJUST de Columna por frecuencia con ACWR 1.0. Histéresis correcta. Días en UTC: una sesión a las 01:00 en UTC+2 cae en el día anterior. | S-03, S-09, S-15, S-17 |
| 3 Sobrecarga | 3 factores vivos (volumen, dolor, progresión); 2 muertos. `fatigueLevel` se infiere (1–5) y `clarityRating` es técnica con defecto 8. Usos: tarjeta Home, diálogo, DELOAD de bloque y un duplicado en ProgramDetail. | S-04, S-14, S-16 |
| 4 Autorregulación | INSERT_DELOAD, SCALE_WEEK_INTENSITY (−2.5/−5 %), SCALE_WEEK_VOLUME (×0.8), DELAY_PEAK; PROMOTE_TM inalcanzable. Propuestas con aceptación explícita; excepción: DELOAD de bloque automático. | S-04, S-05 |
| 5 TTC | Decay exponencial con k=2/48 o 60 h (según drenaje); plateau lineal en 24 h. Drenaje y TTC en escalas distintas. 21 etiquetas del catálogo, todas mapeadas salvo «Flexores Cadera». `eq ==` coincide con 5 etiquetas, no con 62 configs. Varias funciones sin uso. | S-11, S-18, S-19, S-20 |
| 6 Interferencia | Porcentaje con denominador parcial; planificado usa máximo, histórico suma; mensaje en pasado para sesiones futuras. Usa logs de 96 h, no anillos. | S-12 |
| 7 Otros | Resumen de molestias correcto; buscador con AND (doc dice OR); nutrición apagada con saltos; presupuesto semanal no monótono; relator con umbrales 60/45 y suprime con ≥75. | S-13, S-17, S-19, S-08 |
| 8 Previos | Ver B. | B |

## B. Estado de hallazgos previos

| ID | Estado | Evidencia |
|---|---|---|
| H9 | PARCIAL | Ya no aplica: `dashboard.headline` sí se pinta (HomeRingsSection.kt:121–129); `InterferenceEngine` sí tiene caller (SessionAssistantEngine.kt:790); el colágeno ya no es texto de UI (solo comentario, AugeTtcEngine.kt:321). Sigue vivo: `calculateSleepRecommendations` sin callers (AugeRecoveryEngine.kt:1531); `getTendonCompensationSuggestions` solo tests. |
| H16 | CERRADO en su forma original | Sin comidas o sin metas → 1.0 (NutritionRecoveryEngine.kt:51–57, 72–80); flag false (Settings.kt:294; AugeRecoveryEngine.kt:1571). Quedan saltos (S-17). |
| H33 | PARCIAL | `weeksCount` por span (OvertrainingDetector.kt:34–39). DOMS/fuerza siguen muertos: no hay escritor de `PostSessionFeedback` (WorkoutFeedbackModels.kt:309 y AugeViewModel.kt:463 sin callers). El duplicado de ProgramDetail sigue con `logs/3` (S-14). |
| H34 | VIGENTE, ampliado | Solo reduce en `calculateSetAdjustment` (ExerciseReadinessEngine.kt:311; tope L21). Pero la auto-regulación de carga sube +5 % con readiness ≥90 (WorkoutFeedbackModels.kt:473) (S-07). |
| H35 | CORREGIR | La compuerta `augeAutoDeload` cubre solo una rama (AugeFatigueEngine.kt:1058). Las ramas EMA y sobrecarga disparan DELOAD sin flag (S-04). |
| H36 | CONFIRMADO y peor | Sin overrides, `getReadinessScore` devuelve null y el coach asume HIGH (WorkoutCoachMessages.kt:158; WorkoutViewModel.kt:7208) (S-08). |
| B15 | VIGENTE | ExerciseReadinessBlendPinTest.kt:69 fija 74 con el doble conteo (S-10). |
| B17 | APLICADO en Home | `weeksCount` por span y copy «Posible exceso de volumen» (HomeScreen.kt:645). No aplicado al duplicado (S-14). |

## C. Hallazgos nuevos

### P0

**S-01 · P0 · bug (cierre condicional)**
- Ruta: ExerciseReadinessEngine.kt:259–262 → WorkoutFeedbackController.kt:202 → WorkoutScreen.kt:376 (`LaunchedEffect` sin try/catch).
- Cita: `uniqueMuscles.mapNotNull { perMuscle[it]?.recoveryScore }.average().roundToInt()`
- Mecanismo: `involvedMuscleIds` son ids «display» (cabeza de deltoides, EmphasisEngine.kt:64–76). `perMuscle` solo tiene pilares (AugeRecoveryEngine.kt:1330; AugeViewModel.kt:257). Si ningún id del patrón está en `perMuscle`, `average()` da NaN y `roundToInt()` lanza `IllegalArgumentException` (semántica de la stdlib; no ejecutado).
- Alcance (553 configuraciones): solo `calves_tibial_anterior__default` (patrón `ankle_dorsiflexion`, músculo «Tibial Anterior») cumple la condición. Su plantilla `tpl-calves-focus` lo mete en `parts` (SessionTemplates.kt:1889), así que no entra. Los generadores PANTORRILLAS y FULL_BODY (:4126, :4283) sí son candidatos; no verificado cómo se materializan (E1).
- Arreglo: `takeIf { it.isNotEmpty() }` antes de `average()` y fallback a pilar en el id display; test con «Tibial Anterior».
- Estado: HIPÓTESIS con traza completa; no reproducida.

**S-02 · P0 · matemática (unidades)**
- Ruta: ExerciseReadinessEngine.kt:146–147.
- Cita: `averageErm * it.targetPercentageRM`
- Unidad: el resto del código divide entre 100: SessionAssistantEngine.kt:260, AugeFatigueEngine.kt:896, MuscularSessionImpactEngine.kt:99, SetExecutionCard.kt:843.
- Escenario: serie al 60 % sin kg, eRM 100 kg → ratio 60 > 0.85 → ×0.95 en todo ejercicio %RM. Base 73 → 69 (python): cae bajo 70 y propone bajar carga. Correcto: 73, sin ajuste.
- Arreglo: `averageErm * pct / 100.0`; test con 60 %.

**S-03 · P0 · matemática (ACWR) → aviso falso**
- Ruta: AxialLoadMonitor.kt:42–48; SystemicLoadMonitor.kt:41–44. `chronicWeekly = recent.sumOf{…} / 4.0` con 28 días fijos, aunque el historial sea más corto.
- Escenario (python, cargas iguales): 5 sesiones en 15 días (≈2/sem, en −15, −12, −8, −5, −1). Son calculables (≥4 sesiones y ≥14 d). Aguda 2; crónica 5/4 = 1.25; ACWR 1.60 → ADJUST en Columna y Energía hoy. Con semanas reales (15/7): 2/(5/2.14) = 0.86 → SAFE.
- Texto visible: «Hoy baja 10–15 % en sentadilla o peso muerto» (LoadAdvisoryEngine.kt:239); «Deja 1–2 repeticiones en reserva» (:245).
- Arreglo: denominador = min(4, semanas con datos) o EWMA; no emitir ACWR con menos de 28 días.

**S-04 · P0 · decisión sobre el programa sin flag ni confirmación**
- Ruta: BlockTransitionEngine.kt:138–142: `suggestDeload = (hasAugeSnapshot && shouldSuggestAutoDeload(…)) || context.mesocycleStressEma >= 75.0 || context.overtrainedMuscles.isNotEmpty()`. Solo la primera rama pasa por `augeAutoDeload`. Aplica a programas COMPLEX al final de bloque: ProgramProgressEngine.kt:943–950 (`var working = decision.updatedProgram ?: program`).
- Entrada: `overtrainedMuscles` = OvertrainingDetector (ProgramRepository.kt:1097). En producción solo se activan 3 de 5 factores: el sistémico (OvertrainingDetector.kt:67, `>= 8`, con valores 1–5) y el local (L87–91, sin escritor) están muertos. Quedan volumen (L55), dolor por texto (L57–65) y progresión (L101–114, cualquier bajada de peso entre el log más viejo y el más nuevo).
- Realimentación (hipótesis): las bajadas de carga por readiness (S-07) quedan registradas y activan el factor de progresión.
- Efecto: el programa gana un bloque de descarga sin pedirlo y sin el flag.
- Arreglo: aplicar `augeAutoDeload` a las tres ramas; la inserción debe pedir confirmación; no contar factores muertos en el umbral «3 de 5».

### P1

**S-05 · P1 · matemática (fatiga y autorregulación)**
- Ruta: ProgramRepository.kt:1215 `cumFatigue.takeIf{…} ?: batteries.cnc.toDouble()`; ProgramAutoregulationEngine.kt:933.
- Escenario: 14 días sin sesiones → fatiga := batería CNS 90 (fresca) → readiness 35 → autoDeload → INSERT_DELOAD («AUGE propone una descarga táctica»). Una batería alta se lee como fatiga alta.
- Además: L943 escribe «ACWR UNLOAD» aunque `unload` viene de tendencias del anillo (LoadAdvisoryEngine.kt:266–269). ProgramRepository.kt:1219–1220 fijan `overtrained = emptyList()` y `consecutiveHighE1rm = 0`: las ramas de sobrecarga (L938) y PROMOTE_TM (L964) no se activan en esta ruta.
- Efecto: propuesta falsa, aunque requiere aceptación (ProgramDetailViewModel.kt:1318).
- Arreglo: fatiga = carga real de 14 días sin fallback de batería; corregir etiqueta; alimentar o retirar ramas muertas.

**S-06 · P1 · bug (sugerencia de carga)**
- Rutas: ExerciseReadinessEngine.kt:316–358; SetAdjustmentOverlay.kt:91, 236, 243, 245, 365–366.
- (a) No-op: LOAD 5 kg, 10 % → 4.5 → redondeo 2.5 → **5.0 kg** (L352–358). La UI muestra «Aplicar 5kg» con «−10 %».
- (b) Casi nulo: score 69, severidad 0.5 → «−1 %» con 5.0 kg.
- (c) Truncado frente a redondeo: ASSISTED +2.5 kg → texto del motor «+2kg» (L348, `toInt`); overlay «+3kg asist.» (SetAdjustmentOverlay.kt:243, `roundToInt`) y botón «Aplicar +12.5kg Asist.» (:365). LASTRE (L327) y BODYWEIGHT (L338) también truncan.
- (d) Equipo y unidad: «kg» fijo (SetAdjustmentOverlay.kt:91, 365–366). El paso de 2.5 kg no consulta el inventario: `snapLoad` (Settings.kt:363) y `resolveDumbbell` (:430) solo se usan en calentamiento (WarmupFeasibility.kt:143–145).
- Efecto: «−10 %» sin cambio de peso y cargas que el equipo no tiene (mancuernas de 2 kg, máquinas de 5 kg).
- Arreglo: calcular con `snapLoad`/`resolveDumbbell`; no mostrar porcentaje si el peso no cambia; un solo formato numérico.

**S-07 · P1 · inconsistencia (dos motores de carga)**
- Rutas: WorkoutLoadSuggestionController.kt:364–368, 385–387; WorkoutFeedbackModels.kt:441–476. `min` de SNC, columna y músculo; L473 `readiness >= 90 -> 1.05`.
- Efecto: (1) con readiness ≥90 sube la carga un 5 %, mientras ExerciseReadinessEngine solo reduce (L311). (2) SNC y columna son globales: columna 45 → factor 0.835 (−16.5 %) también en curl de bíceps (axial 0), mientras ExerciseReadiness pondera la columna por el axial del ejercicio (L114).
- Arreglo: un único factor de carga ponderado por la demanda del ejercicio; no subir cargas desde readiness.

**S-08 · P1 · inconsistencia de umbrales y H36**
- Umbrales vigentes: ajuste 70 (RecoveryBands.kt:17); overlay 75/50 (SetAdjustmentOverlay.kt:65–69); relator 60/45 (ReadinessObserver.kt:20, 28); coach 80/60/40 (WorkoutCoachMessages.kt:158–161); bandas 85/70/50/35 (RecoveryBands.kt:11–14).
- Escenario: score 72 → overlay ámbar, etiqueta «Bueno» y «No requiere ajuste».
- H36 confirmado: sin overrides `getReadinessScore` devuelve null (WorkoutCoachMessages.kt:172–178; llamada WorkoutViewModel.kt:7208) y `null → HIGH` (L158). El coach dice «tu readiness es alta» sin dato.
- Arreglo: un solo conjunto de umbrales (RecoveryBands); `null` → sin mensaje de readiness.

**S-09 · P1 · copy**
- «Hoy baja 15-20 % las cargas» (ExerciseReadinessEngine.kt:535, rama roja <50) frente al motor: 5 / 12.5 / 20 % con severidad 0 / 0.5 / 1 (python). El slider arranca en 0.5 (SetAdjustmentOverlay.kt:40); el 15 % solo se alcanza con severidad ≥0.67.
- «el plan de ejercicios encaja…» (L505): 539 de 553 configuraciones caen en «ejercicios» (friendlyPatternPlural, L572–581), porque `movementLabel` (ExerciseCatalogV2LegacyAdapter.kt:221–234) no produce «Empuje», «Sentadilla», «Tirón» ni «Extensión». El titular espinal (L540) solo se da con «bisagra» (14 configs).
- «Puedes empujar volumen o un PR» (RecoveryBands.kt:47) frente a «NUNCA sugiere PRs» (ExerciseReadinessEngine.kt:427).
- «lleva semanas al alza» (LoadAdvisoryEngine.kt:242) y «Tres semanas de carga neural alta» (:248): el disparador es 3 inicios <50 (:266) o una tendencia (:269); no hay semanas medidas.
- Badge «hoy −10 %» (:100) frente a «10–15 %» en el cuerpo (:73, :239).

**S-10 · P1 · matemática (articular contada 2–3 veces)**
- ExerciseReadinessEngine.kt:98 `structuralComponent = minOf(muscular, articular)`; :124–128 `articularComponent * wArtic`; :161–171 el techo vuelve a usar la articular.
- Python (cnc 4, axial 0.5, ttc 2.5; muscular 80, SNC 80, columna 80): articular 40 → base 58.6; articular 70 → 74.6. Peso efectivo de la articular 0.536 (0.357+0.179) frente a 0.179 si se contara una vez. Con articular por debajo de muscular, el peso muscular pasa a 0.
- B15 fija el resultado: ExerciseReadinessBlendPinTest.kt:69 `assertEquals(74, …)`; réplica: 73.8 → 74.
- Arreglo: articular como límite o como peso, no ambos; actualizar el pin.

**S-11 · P1 · inconsistencia de TTC (dos fuentes)**
- Catálogo: `profile.ttc` en las 553 configuraciones (rango 1.5–2.2; ExerciseCatalogV2LegacyAdapter.kt:89). Lo leen TrainingEnergyEngine.kt:289 y :447, y ProgramAnalyticsEngine.kt:366.
- Motor AUGE: recalcula por nombre (AugeTtcEngine.kt:116–161). Lo usa en readiness (ExerciseReadinessEngine.kt:107) y en el drenaje tendinoso (AugeTtcEngine.kt:290).
- Ejemplo: «Press de Banca Plano» 2.2 en catálogo y 1.0 en motor (la regla es «press banca», L131, sin «de»). Las 23 configuraciones «Press de Banca…» dan 1.0; en total 348 de 553 dan 1.0.
- Modificador de equipo nunca aplicado a 62 configuraciones (`smith_machine` 37, `ez_bar` 9, `hex_bar` 8, `safety_bar` 4, `t_bar` 3, `h_bar` 1; L144–146). Etiquetas crudas («hex_bar») por `equipmentLabel` (ExerciseCatalogV2LegacyAdapter.kt:194).
- Efecto: demanda articular del press 0.20 en vez de 0.44; drenaje tendinoso a la mitad.
- Arreglo: usar `info.ttc`; el regex solo como respaldo.

**S-12 · P1 · matemática y copy (interferencia)**
- El porcentaje es la media solo de músculos compartidos (InterferenceEngine.kt:481–482). Un solape de pectorales con residual 0.757 (drenaje 0.9, 12 h, semivida 48 h) da 75 % «Alta interferencia» (:508), aunque el resto de la sesión B no se solape.
- Planificado frente a histórico: `max` por ejercicio (:385) frente a suma (:278, :309). Tres ejercicios de pecho (efc 2.5, 4 series): planificado 0.36 → 25 %; histórico 1.0 → 70 % a 24 h.
- SessionAssistantEngine.kt:813 «Entrenaste $from $whenLabel…» también para sesiones planificadas (`isFromHistory = false`; «al día siguiente», L806).
- Arreglo: denominador = Σ uso de B; misma regla de suma; mensaje en futuro para planificadas.

**S-13 · P1 · matemática (proyección semanal no monótona)**
- RingBudgetPolicy.kt:34–38: `value*0.72 − value²*0.0022`; máximo en ~164.
- Python: 3×60 CNS (raw 180) → 58; 4×60 (raw 240) → 46; 2×80 → 59; 3×80 → 46.
- Consumidores: SessionTemplateSuggestionEngine.kt:205–206, 303–304. Añadir una sesión baja la proyección.
- Arreglo: suma lineal con tope; garantizar monotonía.

**S-14 · P1 · dos detectores de sobrecarga**
- OvertrainingDetector.kt:34–39 usa span. ProgramDetailViewModel.kt:363–365 usa `weeksCount = (logs.size / 3)` y reimplementa los factores (393–451).
- Ejemplo (MRV 20, hipotético): 80 series en 12 registros de 27 días. Home: 3 semanas → 26.7 > 20 (factor activo). ProgramDetail: 12/3 = 4 → 20.0 (inactivo).
- Arreglo: un único detector y eliminar el duplicado.

**S-15 · P1 · inconsistencia de umbrales de ACWR**
- AugeClassifiers.kt:37–42 (SAFE ≤1.3, CAUTION ≤1.5, DANGER >1.5) → anillo Músculos ×1.06 / ×1.12 (:57–61; AugeRecoveryEngine.kt:367–372).
- LoadAdvisoryEngine.kt:23–25 (WATCH ≥1.2, ADJUST >1.4, UNLOAD con tendencia) → avisos de Columna y Energía.
- ACWR 1.35: el anillo muscular dice «Zona de riesgo» y Columna/Energía dicen WATCH.
- Arreglo: un solo mapa de umbrales.

**S-16 · P1 · dato inferido presentado como preguntado**
- `fatigueLevel` es `inferredFatigue` (1–5) desde el RPE efectivo medio (WorkoutFinishHost.kt:333–340, :387; WorkoutViewModel.kt:6454–6460, :6490). No hay pregunta de fatiga general al cierre.
- Se muestra «Fatiga X/10» (HistoryView.kt:305, :552): un 3 de 5 se lee como 3/10.
- `clarityRating` = media de `technicalQuality` (técnica, no claridad), con defecto 8.0 sin valoraciones (WorkoutFinishHost.kt:342–348, :400; WorkoutFinishController.kt:327–331). Cadena: 8 → factor 0.96 (WorkoutFinishController.kt:337–341) → `sessionStressScore` → EMA de meso (ProgramRepository.kt:1081) → umbral 75 de S-04.
- `factorSystemic` (OvertrainingDetector.kt:67) es inalcanzable.
- Arreglo: fatiga preguntada o escala 1–10 explícita; `clarityRating` solo con valoración; etiquetar lo inferido.

### P2

**S-17 · P2 · rendimiento y robustez**
- AxialLoadMonitor.kt:33–39 y SystemicLoadMonitor.kt:27–35 recalculan drenajes de todo el historial en cada evaluación (AugeViewModel.kt:299–307; ProgramRepository.kt:1208–1210).
- `System.currentTimeMillis()` en AugeTtcEngine.kt:211, InterferenceEngine.kt:58 y :194 (default), NutritionRecoveryEngine.kt:35. AugeClock.kt:3–6 dice que es el origen único.
- Nutrición (flag off): salto en 0.9 (ratio 0.899 → 1.121; 0.901 → 1.0) y en 1.1 (1.099 → 1.0; 1.101 → 0.86). La rama «<8 %» (L132) es inalcanzable en SURPLUS. La rama de proteína baja no es monótona (L124–128: 0.92 / 0.88 / 0.92).

**S-18 · P2 · matemática latente (sin lector de producción)**
- AugeTtcEngine.kt:309–313: `avgTtc` es drenaje medio por batería comparado con 3.0 (escala TTC). 1 serie de sentadilla (5 reps, RPE 8) → 48 h; 4 series de curl (12 reps, RPE 9) → 60 h. Invertido respecto al coste tendinoso.
- L367–370 `hoursToRecovery` ignora el plateau: sesión de hace 2 h, acc 40 → 38.6 h frente a 59.5 h del modelo (python). Exacto solo con sesiones de ≥24 h.
- Sin lectores de producción (AugeModels.kt:268; solo tests).

**S-19 · P2 · código muerto, docs y contratos**
- Sin callers: InterferenceEngine.kt:52 y :100; AugeTtcEngine.kt:431 (`getTendonImbalanceAlerts`); AugeClassifiers.kt:63–76, 89–108, 117–125, 130–131; AugeRecoveryEngine.kt:1531.
- Solo tests: AugeTtcEngine.kt:464 (`getTendonCompensationSuggestions`) y RingBudgetPolicy.kt:18 (`sessionHardCaps`, que ignora `isPowerlifting`).
- DiscomfortSuggestionEngine.kt:32 documenta «cualquiera de los términos»; el código (:41, `terms.all`) exige todos.

**S-20 · P2 · defaults silenciosos**
- Readiness: muscular y articular = 100 sin datos (ExerciseReadinessEngine.kt:78, :93–95). Python: sin datos musculares/articulares, SNC 40 y columna 40 → readiness 73 «sin ajuste».
- Drenaje estimado: `set.weight ?: 60.0` (SessionAssistantEngine.kt:262), también para peso corporal.
- AugeTtcEngine.kt:395 `?: 100` para baterías ausentes. «Flexores Cadera» no tiene entrada en `MUSCLE_TO_ARTICULAR` (:43–80; 13 apariciones en el catálogo).

## D. Lo sano (no tocar)

- NutritionRecoveryEngine: sin comidas o sin metas → neutro, sin déficit inventado (L51–57, L72–80). Cierra H16.
- LoadAdvisoryEngine: escalada inmediata, bajada gradual con histéresis y un paso por día (L183–204); descarte por semana (L150).
- ExerciseReadinessEngine: el ajuste solo reduce y tiene tope de 30 % (L21, L311). Umbral 70 = NORMAL_MIN (RecoveryBands.kt:17). Penalización por molestias acotada entre 0.90 y 1.0 (L401–405).
- Guardas de datos insuficientes de los monitores (≥4 sesiones, ≥14 días; AxialLoadMonitor.kt:42, SystemicLoadMonitor.kt:41). Son correctas; falla solo el denominador (S-03).
- Propuestas de autorregulación pendientes con aceptación explícita (ProgramDetailViewModel.kt:1318).
- Contrato pilar/display (AugeMuscleNormalization.kt:1–11). El uso de pilares en InterferenceEngine (L307, L341, L383, L419) es consistente.
- Plateau continuo en 24 h (AugeTtcEngine.kt:322–326: 0.05·24 = 1.2).
- Convención de porcentaje correcta en SessionAssistantEngine.kt:260.

## E. Preguntas abiertas / no verificado

- E1. Si la fuerza de una sesión está dentro de `parts`, `computeExerciseReadiness` queda vacío sin aviso (WorkoutFeedbackController.kt:175 frente a `Session.allExercises()`, Session.kt:90). SessionTemplates usa `parts` 67 veces; SessionAssembler (generador) mete la fuerza en `exercises` (SessionAssembler.kt:698–699). No verificado cómo se materializan las plantillas.
- E2. S-01 y S-02 no están reproducidos con test (sin Gradle).
- E3. Unidades: no encontré conversión lb↔kg en el flujo de cargas. `weightUnit` solo llega a AugeFatigueEngine como parámetro (:422, :600) y a Perfil/Onboarding. No verificado cómo se guardan los pesos con LBS.
- E4. Escala de `fatigueLevel` en logs anteriores a la inferencia: no verificado.
- E5. Cardio en InterferenceEngine: `CardioRingDrainEngine.muscleDrains` usa claves propias sin pasar por pilar (InterferenceEngine.kt:276–278). No verificado si coinciden.
- E6. WorkoutAdaptiveRest: el RPE entra dos veces (rpeFactor L85–95 y el drenaje, AugeFatigueEngine.kt:427–446). Posible doble conteo; no cuantificado.
- E7. DiscomfortAggregationEngine.kt:40–41 no filtra contribución 0 ni NEUTRALIZER (sí lo hace ExerciseReadinessEngine.kt:51–57 en el fallback). Sin efecto hoy: el catálogo nunca genera NEUTRALIZER (ExerciseCatalogV2LegacyAdapter.kt:62–64).
- E8. `estimatedHoursToRecovery` no tiene lector; corregir antes de exponerlo (S-18).
- E9. Fuera de mi alcance: ACWR muscular (AugeRecoveryEngine.kt:367–372) y `cumulativeFatigue` (ya asignados a otro agente). Drenaje y recuperación (AugeRecoveryEngine.kt:666–670) no revisados.
- E10. Las referencias de línea de los H-docs están casi todas desfasadas; conviene reindexar.

## F. Cobertura real

- Leído completo: ExerciseReadinessEngine (593), OvertrainingDetector (130), InterferenceEngine (530), DiscomfortAggregationEngine (86), DiscomfortSuggestionEngine (44), NutritionRecoveryEngine (157), AxialLoadMonitor (155), SystemicLoadMonitor (54), LoadAdvisoryEngine (281), AugeTtcEngine (507), RingBudgetPolicy (46), AugeClassifiers (132), RecoveryBands (152), AugeMuscleNormalization (197), ReadinessObserver (62), AugeClock (10). ExerciseCatalogV2LegacyAdapter: casi completo (líneas 37–234).
- Tramos leídos: SessionAssistantEngine (196–345, 596–626, 756–830, 1000–1026, 1078–1082); ProgramAutoregulationEngine (922–993 y greps); BlockTransitionEngine (30–200, 336–351); ProgramProgressEngine (930–975); ProgramRepository (1076–1100, 1146–1222); AugeRecoveryEngine (greps y 40–80, 1269–1300, 1566–1600, 1836–1925); AugeViewModel (226–257, 340–343 y greps); AugeFatigueEngine (412–450 parcial, 809–830, 1053–1067); WorkoutFeedbackController (150–259); WorkoutLoadSuggestionController (356–392 y greps); WorkoutFeedbackModels (283–320, 435–476); WorkoutFinishHost (326–352, 380–412); WorkoutFinishController (296–322, 322–395 parcial); WorkoutViewModel (6405–6502, 7200–7216, 6185–6230, 1815–1835); SetAdjustmentOverlay (30–100, 222–322 y greps); WorkoutAdaptiveRest (1–100); WorkoutCoachMessages (140–185 parcial); HomeViewModel (172–214); HomeScreen (340–360, 640–656); HomeRingsSection (100–140); RelatorContextBuilder (greps); TrainingEnergyEngine (greps); Settings (greps y 294, 326–376, 430).
- Sin lectura detallada: TrainingEnergyEngine (solo greps), RelatorContextBuilder (solo greps), BlockTransitionEngine 200–335, ProgramAutoregulationEngine fuera de 922–993, SessionAssistantEngine y AugeFatigueEngine (solo tramos).
- Python: réplicas de readiness (validada con B15), recuento del catálogo (553 configuraciones, 228 definiciones) y simulación de ACWR (4 escenarios; supuestos: cargas iguales por sesión y ejercicio axial en todas).
- No escribí archivos y no ejecuté Gradle ni tests.

## G. Observaciones de sueño

- ReadinessObserver.kt:15–16 y RelatorContextBuilder.kt:106 siguen leyendo `sleepQuality` para copy de carga («Sueño flojo… baja un punto»). Si la UI ya no pide sueño, es dato residual.
- AugeRecoveryEngine.kt:1531 (`calculateSleepRecommendations`) no tiene callers: no hay salida de sueño.
- H4 (sleepMult con 7.5 h, AugeRecoveryEngine.kt:215–228) no re-verificado: fuera de mi alcance.
