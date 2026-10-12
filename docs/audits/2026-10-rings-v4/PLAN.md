> Copia del plan aprobado por el usuario el 2026-10-12 (original: `C:\Users\valen\.claude\plans\drifting-frolicking-penguin.md`). Los informes de los 7 agentes están en `informes/`. Las referencias `archivo:línea` del plan son de `master` @ `00431a27d` (verificadas también sobre `7815e45b6`).

# Plan RINGS/AUGE v4 — fiabilidad, aprendizaje y potencial

> Estado: auditoría COMPLETA y verificada. Plan listo para aprobación. Nada del repo se ha tocado.
> Decisiones que ya tomaste (AskUserQuestion, 2026-10-12): **alcance = fiabilidad completa** · **números = directo (sin modo sombra)** · **ejecutores = mixto** (Sonnet 5.5 en matemática y aprendizaje; Haiku 5.5 máx. en limpieza, pruebas y textos).
> Rutas: `K/` = `android-native/app/src/main/java/com/example/kpkn/` · `T/` = `android-native/app/src/test/java/com/example/kpkn/`. Base: `master` @ `7815e45b6` (la auditoría se hizo sobre `00431a27d`; las 80 commits posteriores son del importador de la ola Wizard v3 y la PR7, y ninguna toca archivos citados aquí, salvo un `AndroidManifest.xml` ajeno; todas las referencias siguen válidas).

## 1. Contexto

Pediste auditar RINGS/AUGE para que los cálculos, los modelos de **drenaje** y de **recuperación** y el **aprendizaje del usuario** sean más fiables, sin bugs ni inconsistencias, y que te sugiera cómo ampliarlo. El sueño no cuenta como problema (Energía es su proxy); tampoco se reabre el cuestionario de 24 h, los sliders en Ajustes, Health Connect ni iOS/backend.

Ya había 4 auditorías (29-ago H1–H36, 03-sep B9–B17, τ-rendimiento, 12-sep F0–F6). El código cambió mucho desde entonces, así que **todo se re-verificó contra el código actual**.

**Método.** 7 subagentes Haiku 5.5 máx. (tipo Explore, solo lectura) en 3 olas: drenaje, recuperación, aprendizaje · persistencia/concurrencia, superficies de UI, motores de decisión · mapa de pruebas. Yo orquesté, leí en paralelo ~6 000 líneas del motor, recalculé con Python las cifras clave y **verifiqué cada informe antes de aceptarlo** (anexo A). Todas las citas de este plan pasan un verificador automático de rutas y líneas.

## 2. Veredicto

1. El lazo está vivo: **10 de los 36 hallazgos de agosto están arreglados y 15 más cambiaron de forma** (triple aprendizaje, piso de 0,5 h, sleepMult 7,5 h, sexo/edad, déficit nutricional asumido, copy del «día siguiente», segundo loop de bias).
2. **P0-A — pérdida silenciosa de calibraciones.** `recompute` vuelve a pedir un mutex no reentrante que sus 5 llamadores ya sostienen: tras el primer recálculo del día UTC o un cambio de nivel de aviso, readiness, cierre y descarte de avisos **dejan de guardarse sin error** hasta reiniciar la app. Dos agentes lo hallaron por separado y yo confirmé la estructura (F-01).
3. **P0-B — el aprendizaje de τ está sesgado por construcción.** Con la sensación = predicción exacta del propio modelo, τ aprendido sale ×1,2–3,5 en músculos, ×0,4–0,8 en Energía (a 8–24 h) y ×1,0–1,8 en Columna. En simulación el τ deriva +24 h aun con el τ correcto y ~57 % de las actualizaciones van en sentido contrario (F-10). Las pruebas no lo ven: su ida y vuelta es lineal (F-63).
4. **Los números no coinciden entre pantallas**: la vista previa del cierre, el editor, Home y el snapshot del programa usan ≥8 copias de la misma receta de agregación y 2 pipelines (F-23, F-31, F-60).
5. **El modelo tiene escalas arbitrarias**: el tanque se divide dos veces, el SNC acumula por músculo (10 curls ≈ sentadilla+banca), Energía casi no distingue 1 de 5 días duros, y el drenaje muscular satura (10 series valen 7,6) (F-30…F-33).
6. **Bugs concretos y baratos**: `%RM` sin ÷100, baterías truncadas (se quedan en «99 %» días), ACWR con denominador fijo (avisos falsos a quien lleva <4 semanas), ancla manual que cae a las 18 h, «horas hasta normal» 20–27 % cortas, ediciones por músculo que se pierden al plegar el Flip, cierre por voz que descarta los anillos dictados.
7. **Decisiones sobre tu programa sin pedirlo**: un bloque «Descarga (auto)» se inserta por EMA de estrés ≥75 o músculos «sobrecargados», sin pasar por `augeAutoDeload` (F-43).
8. Hay **datos valiosos ya capturados y sin aprovechar** (inicio de anillos por sesión, `automaticBatteryAtAnchor`, calidad técnica y molestias por ejercicio, FC de cardio, `ttc` del catálogo) → sección 8.
9. **Qué propongo:** 6 fases y 32 paquetes con briefs cerrados; primero salvar datos y arreglar bugs baratos, luego unificar el motor, luego rehacer el aprendizaje con un actualizador bayesiano verificado con usuarios sintéticos.

## 3. Estado de las auditorías previas (resumen)

| Estado | IDs |
|---|---|
| Arreglados | H1 (copy), H4, H16, H17, H19 (forma original), H20, H22, H23, H28, H30 (por eliminación) · B9, B11, B12, B13 · F0–F6 confirmados |
| Cambiaron de forma / parciales | H2, H5, H9 (el titular **sí** se pinta, `InterferenceEngine` **sí** tiene llamador), H10, H11, H12, H15 (motor arreglado, **creador de ejercicios regresa**), H24, H26, H27, H32, H33, H34, H35 (el flag gatea solo una rama), H36 (peor: `null→HIGH`) · B14 (contrato invertido), B16 |
| Siguen abiertos | H3 (intencional), H6, H7, H13, H14, H21 (warp correcto, **fracción incorrecta**), H25, H31 |
| Obsoletos / fuera | H8, H29 (backend), H18 (la tabla de pines sigue vigente) |

## 4. Hallazgos verificados

Severidad: P0 = pierde datos o muestra números incorrectos en silencio · P1 = inconsistencia visible o sesgo claro · P2 = deuda o caso borde. Fuente: D/R/L/P/U/S/T = agente; «yo» = verificado por mí. WP = paquete de la sección 7.

### A · Datos y concurrencia
| ID | Sev | Hallazgo | Evidencia | WP |
|---|---|---|---|---|
| F-01 | P0 | Bloqueo del `augeWriteMutex`: `recompute` lo pide en :395-403 y se llama con el mutex tomado desde `saveWellbeing` (:412→:455), `applyManualBatteries` (:611→:697), `dismissAdvisory` (:1060→:1066), `clearManualBatteryOverrides` y `resetAdaptiveCache`. Se activa si `cacheToPersist≠null` (primer recálculo del día UTC o cambio de nivel de aviso). Después los guardados se cuelgan sin error. | `K/screens/auge/AugeViewModel.kt:395-403`; `K/domain/auge/LoadAdvisoryEngine.kt:114,157` (L-01, P-01, yo) | 1.1 |
| F-02 | P1 | Cierre **por voz** descarta los anillos dictados: `applyManualBatteries` solo se llama desde `WorkoutScreen`; `finalizeVoiceSession` arma `finalNeuralBattery/finalSpinalBattery` que nadie lee (el anillo automático sí se actualiza por el historial). | `K/screens/workout/WorkoutScreen.kt:1747`; `K/screens/workout/WorkoutViewModel.kt:6448-6502` (P-02, yo) | 1.2 |
| F-03 | P1 | Las ediciones **por músculo** de readiness se pierden al recrear la Activity (plegar el Z Flip, girar, muerte de proceso): `userEditedMuscles` es `remember`, el resto es guardable, y el `else` reescribe las semillas. El cierre usa `remember(seed)` y pierde todo. `MainActivity` no declara `configChanges`. | `K/screens/workout/components/WorkoutReadinessSheet.kt:144,166-178`; `K/screens/workout/WorkoutFinishHost.kt:226-230`; `android-native/app/src/main/AndroidManifest.xml:56-61` (U-03, U-04, yo) | 1.6 |
| F-04 | P1 | Bienestar: `applyManualBatteries` reconstruye la fila sin `source`/`capturedFields`; readiness reenvía copias de hoy como ajustes nuevos (reaprende y reancla); `produceState` lee la fila una sola vez y tras medianoche reescribe la de ayer; el motor usa la fila de ayer entera o la de hoy (todo-o-nada). | `K/screens/auge/AugeViewModel.kt:192-205,439-452,639-663`; `K/screens/workout/WorkoutSessionOverlaysHost.kt:87-90`; `K/screens/workout/WorkoutScreen.kt:352-354` (L-05, U-05, P-03/04/05) | 1.2 |
| F-05 | P2 | `recompute` sin `try/catch` (una excepción mata el colector o la app); `learnFromLatestFinishedLog` traga `CancellationException`; restauración sin mutex; claves de día UTC vs local; decodificación silenciosa a defaults. | `AugeViewModel.kt:188-405,864-943`; `K/data/settings/SettingsJsonBackup.kt:229-234`; `K/data/db/Entities.kt:224-238` (P-09/11/12/15/16) | 1.1 |
| F-06 | P2 | Cierre en dos fases: el log se guarda atómico en Room, los anillos editados después; si el proceso muere entre fases, la edición se pierde. | `K/data/repository/ProgramRepository.kt:1026-1046`; `K/screens/workout/WorkoutFinishController.kt:555-557` (P) | 1.2 |

### B · Aprendizaje
| ID | Sev | Hallazgo | Evidencia | WP |
|---|---|---|---|---|
| F-10 | P0 | Inversión de τ sesgada por construcción: la fracción restante usa depleción/estrés, pero el modelo es `b(t)=b0^x`; Energía usa el drenaje del **resumen** (≈2,4× la caída real); `immediateDrainPct` usa `e^{-s/c}` y el anillo `e^{-1,11·s/c}`. Ratios τ aprendido/τ real (sin ruido): músculo ×1,2–3,5, Energía ×0,41–0,54 a 8–12 h, Columna ×1,0–1,8. | `K/domain/auge/AugeAdaptiveEngine.kt:27-48`; `K/domain/auge/AugeFatigueEngine.kt:649`; `K/domain/auge/AugeRecoveryEngine.kt:653,883-885`; `K/domain/auge/MuscularSessionImpactEngine.kt:229` (L-02/03, R-01, yo: Python) | 3.2-3.3 |
| F-11 | P1 | Actualizador: α depende del contador **global** (la 1.ª observación de un músculo tras ≥29 eventos aprende al 5 %); sin zona muerta (1 pt dispara hasta −25 %); sin prior ni incertidumbre; ignora molestias/DOMS/pisos; clamps [12,144] vs [18,144]. | `AugeAdaptiveEngine.kt:76-80,98,130,174,227`; `AugeRecoveryEngine.kt:487` (L-04/08/09/10) | 3.2 |
| F-12 | P1 | Aprendizaje por rendimiento: baseline con **orden invertido** (historial DESC, pesa más lo viejo); solo sorpresas negativas → trinquete hacia τ lento; umbral de 8 pts ≈ 2 % de e1RM (1 rep = 12 pts); sin excluir lesión/deload; predicción de inicio con reloj actual. | `K/domain/performance/PerformanceRangeCalculator.kt:84`; `K/data/db/Daos.kt:69`; `K/domain/auge/PerformanceTauLearner.kt:315-324,431-437`; `K/domain/auge/PerformanceImpliedBattery.kt:16` (L-07/11/12, yo) | 3.4 |
| F-13 | P1 | Estado zombi: `muscleDeltas`, `cns/spinalLearningDelta`, `cns/spinalDrainMultiplier`, `muscleDrainMultipliers` se **leen** en el modelo y nadie los **escribe**; la migración v2→v3 no limpia deltas/multiplicadores musculares. La escala de drenaje por usuario no se aprende. | `AugeRecoveryEngine.kt:603,675-677,1203-1207`; `AugeFatigueEngine.kt:681,847`; `K/domain/auge/AugeAdaptiveCacheMigration.kt:23-32` (L-06) | 3.1 |
| F-14 | P2 | No hay registro predicho-vs-reportado de Energía/Columna, ni métricas de habilidad del modelo, ni tope de seguridad si el aprendizaje empeora. `automaticBatteryAtAnchor` (útil) existe solo para músculos V2 y nadie lo lee. | `K/data/models/AugeModels.kt:113-118`; `AugeViewModel.kt:630` (P-17) | 3.5 |

### C · Recuperación y anillos
| ID | Sev | Hallazgo | Evidencia | WP |
|---|---|---|---|---|
| F-20 | P1 | ACWR con denominador fijo `/4`: 5 sesiones en 15 días (≈2/sem) dan ACWR 1,60 → aviso ADJUST «Hoy baja 10–15 % en sentadilla o peso muerto» (con semanas reales: 0,86). El factor muscular salta al cumplirse 14 d (60→55 sin entrenar). Umbrales distintos: 1,3/1,5 vs 1,2/1,4/1,6. | `AugeRecoveryEngine.kt:352-374`; `K/domain/auge/AxialLoadMonitor.kt:42-48`; `K/domain/auge/SystemicLoadMonitor.kt:41-44`; `K/domain/auge/AugeClassifiers.kt:37-42` (R-03, S-03, S-15) | 1.4 |
| F-21 | P1 | Ancla manual descartada a las 18 h: escalón de hasta −19 pts sin entrenar. | `K/data/repository/AugeRepository.kt:55,65-66` (R-04) | 2.5 |
| F-22 | P1 | «Horas hasta normal» 20–27 % cortas: usan horas efectivas del warp y depleción exponencial pura; la UI usa τ por defecto 36/52/48 y mezcla batería cruda/mostrada. | `AugeRecoveryEngine.kt:666-672`; `K/domain/auge/RecoveryBands.kt:136-149`; `AugeViewModel.kt:321-331` (R-05, yo) | 2.4 |
| F-23 | P1 | Vista previa del cierre ≠ Home (hasta ~10 pts): resta lineal por músculo, log previo sin V2, articulares calculadas sin la sesión y con reloj actual. | `AugeRecoveryEngine.kt:1853-1879`; `AugeViewModel.kt:492-500,555-557`; `K/domain/auge/AugeTtcEngine.kt:211` (R-06, yo) | 2.3 |
| F-24 | P1 | Evidencia inicial se compone **aditiva** (−9…−16 pts vs la ley del motor) y duplica τ (24/48/72/96, 36, 52, 48). | `AugeRecoveryEngine.kt:1239-1240,1356`; `K/domain/auge/InitialRecoveryEvidencePolicy.kt:252-269` (R-07, L-17, yo) | 2.5 |
| F-25 | P1 | Truncación `toInt()`: con τ=48 h el músculo marca 99 de 72 a 168 h; la torre trunca Float (53→52, 59→58). | `AugeRecoveryEngine.kt:693,888,1096,1172`; `K/screens/home/HomeRingsSection.kt:263`; `K/screens/home/HomeSessionSection.kt:435` (el promedio de la sesión también trunca) (yo: Python; U-02, T) | 1.5 |
| F-26 | P1 | Blend articular solo en el global de Músculos (los chips no lo llevan); la descripción dice «promedio». | `AugeRecoveryEngine.kt:1136-1147,1330-1346`; `RecoveryBands.kt:94` (R-02) | D1 |
| F-27 | P2 | Evaluaciones no causales/no deterministas: el bienestar de hoy se aplica a evaluaciones pasadas, logs con fecha futura cuentan en Energía/Columna, ACWR/articular y la capacidad de respaldo (`calculateUserWorkCapacity`, `:387`) con reloj real (20 usos de `nowMs()`/`currentTimeMillis` en 8 archivos del dominio), `AugeClock` no inyectable. | `AugeRecoveryEngine.kt:387,800,957,994-1015`; `K/domain/auge/AugeClock.kt:8-10` (R-09/10/11, T, yo) | 1.7 |
| F-28 | P1 | «Sin datos» se presenta como 100 % afirmativo: titular «Listo para empujar», líneas de readiness, relator «Salta aprox» (score ≥80), detalle «100 % · Alta»; chips de músculo en 100 %; el titular ponderado oculta el canal más bajo (Energía 40 → «Buen estado»). | `HomeRingsSection.kt:121-129`; `K/domain/relator/WarmupObserver.kt:20,35`; `AugeRecoveryEngine.kt:1484-1489` (U-01/06/12) | 1.10 |

### D · Drenaje
| ID | Sev | Hallazgo | Evidencia | WP |
|---|---|---|---|---|
| F-30 | P1 | Doble normalización por tanque y escalas arbitrarias por canal: el drenaje por serie ya es % del tanque y el anillo divide otra vez por `1,15·tanque` / `0,02·tanque`; resumen 40 → caída real de Energía 16,4; con POWERLIFTER Columna cae −61 %. Los pines 80/93 dependen de ello. | `AugeRecoveryEngine.kt:883-885,1093-1095`; `AugeFatigueEngine.kt:533-535` (D-01, R-15, yo) | 2.1, D5 |
| F-31 | P1 | Editor ≠ finish ≠ Home: Columna sin reescalar en el editor, cardio fuera del global y fallback inventado 10/14/8, términos solo en Home (singles +15 %, duración, SPF). | `K/screens/sessioneditor/SessionEditorAugeComputation.kt:525-537,709-732`; `AugeRecoveryEngine.kt:866-878,1078` (D-02, D-07, yo) | 2.2 |
| F-32 | P1 | El SNC acumula por músculo primario: 10 curls RPE 9 drenan Energía ≈ sentadilla+banca. Energía casi plana ante la frecuencia: pin 1×=87 / 5×=85; 1→7 días duros seguidos ≈94 con un solo exponencial. | `AugeFatigueEngine.kt:450,608-609`; `T/domain/auge/AugeRingCharacterizationTest.kt:93-95` (D-04, yo) | 2.6, D3/D4 |
| F-33 | P1 | Forma saturante del drenaje muscular (progresivo 1,1→0,3 en la serie 10: 10 series = 7,6; 20 = 10,6); V2 reinicia por ejercicio y legacy por músculo; capacidad ×1,8 del promedio semanal (1 sesión/sem 25 % → 5/sem 10,5 %) con dos motores de capacidad. | `AugeFatigueEngine.kt:325-332`; `MuscularSessionImpactEngine.kt:193-201`; `K/domain/auge/AugeMuscleCapacityEngine.kt:47-50` (D-08, D-11, yo) | 2.6, 4.2 |
| F-34 | P1 | El creador de ejercicios persiste **Core** como primario cuando no hay coincidencias (regresión de H15). | `K/domain/exercises/ExerciseAugeInference.kt:137-141` (D-09, yo) | 1.11 |
| F-35 | P2 | Dobles conteos y acantilados: descanso corto de superseries contado dos veces (+17 % sesión), dropset triple (SNC ×2,31), suelo de 6 series duras (columna 0→8 con ejercicio sin ficha), series de tiempo como fuerza, RPE<6 ignorado con salto 0→100 %, dependencia del orden, asistidas ×(1+0,2n) sin tope. | `AugeFatigueEngine.kt:264-282,364,462-470,518-519,757-807` (D-03/05/06/11/12/13/19) | 2.6 |
| F-36 | P2 | Cardio sin FC/distancia; en logs V2 se pierde el cardio muscular de músculos presentes en el V2. | `K/domain/auge/CardioRingDrainEngine.kt:25-89`; `AugeRecoveryEngine.kt:550-569` (yo) | backlog |

### E · Motores de decisión
| ID | Sev | Hallazgo | Evidencia | WP |
|---|---|---|---|---|
| F-40 | P1 | `targetPercentageRM` sin ÷100 → ×0,95 en todo ejercicio por %RM sin kg (readiness 73→69 y propone bajar carga). Todo el resto del código divide entre 100. | `K/domain/auge/ExerciseReadinessEngine.kt:146-147`; `AugeFatigueEngine.kt:896` (V10, S-02, yo) | 1.3 |
| F-41 | P1 | Sugerencia de carga: redondeo a 2,5 kg sin inventario (5 kg −10 % = 5 kg «Aplicar 5kg»), textos truncan y el overlay redondea; dos motores de carga (readiness ≥90 **sube** +5 %; SNC/columna globales recortan también un curl). | `ExerciseReadinessEngine.kt:316-358`; `K/screens/workout/WorkoutFeedbackModels.kt:441-476`; `K/screens/workout/components/SetAdjustmentOverlay.kt:243,365` (S-06, S-07, yo) | 1.12 |
| F-42 | P1 | Lo articular se cuenta 2–3 veces en readiness (min + peso + techo) y el TTC se calcula por regex ignorando `profile.ttc` del catálogo (348/553 configs dan 1,0; el modificador de equipo no aplica a 62). | `ExerciseReadinessEngine.kt:98,124-128,160-171`; `AugeTtcEngine.kt:116-161`; `K/data/exercises/catalogv2/ExerciseCatalogV2LegacyAdapter.kt:89` (S-10, S-11) | 2.6, D1 |
| F-43 | P1 | Descarga automática de bloque sin flag ni confirmación (COMPLEX, al cerrar bloque): se inserta «Descarga (auto)» si el EMA de estrés ≥75 o hay músculos «sobrecargados»; `augeAutoDeload` gatea una sola rama. El detector exige 3 de 5 factores pero 2 están muertos (`fatigueLevel` 1–5 nunca ≥8; sin `PostSessionFeedback`), así que dispara cuando coinciden volumen>MRV, dolor por texto y una «bajada» de carga medida entre la primera y la última vez que se hizo el ejercicio en **todo** el historial (`OvertrainingDetector.kt:105-114`). | `K/domain/training/BlockTransitionEngine.kt:138-142,181-194`; `K/domain/training/ProgramProgressEngine.kt:943-950`; `K/domain/auge/OvertrainingDetector.kt:67` (S-04, yo) | 1.12 |
| F-44 | P2 | `cumulativeFatigue`: suma de 14 días sin normalizar contra 75; fallback usa la batería CNS como «fatiga»; snapshot propio en `ProgramRepository` (índice de catálogo distinto, constantes fijas, `runCatching` mudo); dos detectores de sobrecarga. Autorregulación apagada por defecto. | `ProgramRepository.kt:1154-1226`; `K/domain/training/ProgramAutoregulationEngine.kt:933` (V14, P-06/07, S-05/14) | 2.3 |
| F-45 | P1 | Datos **inferidos** presentados como medidos: `fatigueLevel` (1–5 desde RPE) se muestra «Fatiga X/10»; `clarityRating` = técnica media (defecto 8) y modifica `sessionStressScore` (×0,96). | `WorkoutFinishHost.kt:333-348,400`; `WorkoutFinishController.kt:327-341`; `K/screens/programdetail/components/HistoryView.kt:305` (S-16) | 1.12 |
| F-46 | P2 | Umbrales dispersos (70/75/60/45/80/40, coach `null→HIGH`), interferencia con denominador parcial y mensaje en pasado para sesiones futuras, proyección semanal no monótona, patrones del catálogo («Empuje Horizontal») que el coaching no reconoce (539/553 → «ejercicios»). | `K/screens/workout/WorkoutCoachMessages.kt:158-161`; `K/domain/auge/InterferenceEngine.kt:481-508`; `K/domain/templates/RingBudgetPolicy.kt:34-38` (S-08/09/12/13) | 1.9 |
| F-47 | P2 | Latente: `average().roundToInt()` sobre lista vacía lanza `IllegalArgumentException`; alcanzable solo con ids «display» fuera de `perMuscle` (hoy solo «Tibial Anterior»). | `ExerciseReadinessEngine.kt:259-262`; `K/domain/auge/AugeMuscleNormalization.kt:95-102` (V11, S-01) | 1.3 |

### F · Textos, UI y accesibilidad
| ID | Sev | Hallazgo | Evidencia | WP |
|---|---|---|---|---|
| F-50 | P1 | Textos que prometen más de lo que hace el código (25 revisados): diálogo «estado de tu cuerpo»/«puedes corregir desde Home»; «Tu Energía ya va alta» con significado invertido; «Desgaste final por Músculo»; «Modelo actualizado» en instalaciones nuevas; voz «por ciento de drenaje» vs batería. | `HomeRingsSection.kt:511-535`; `SessionEditorAugeComputation.kt:192-197`; `WorkoutFinishHost.kt:641`; `K/screens/workout/WorkoutVoiceCommandHandler.kt:1925` (U-07/08/10/13/14) | 1.9 |
| F-51 | P1 | Tres cifras de «Músculos» en la hoja de readiness; slider por músculo sin semántica de accesibilidad y contraste 2,11:1. | `WorkoutReadinessSheet.kt:138-141,443`; `K/screens/workout/components/MinimalMuscleSlider.kt:33-107` (U-09, U-11) | 1.10 |
| F-52 | P2 | Estados límite: placeholders de 50 en readiness, sin estado de error. | `AugeViewModel.kt:74-112`; `WorkoutReadinessSheet.kt:252-258` (U-15) | 1.10 |

### G · Arquitectura y deuda
| ID | Sev | Hallazgo | Evidencia | WP |
|---|---|---|---|---|
| F-60 | P1 | ≥8 implementaciones de la receta «conservación 0,85 × 1/(1+0,65·acc/100) × tope blando» (resumen, ring legacy ×3, editor, V2 distinto, snapshot del programa). Origen de F-23, F-31, F-44. | `AugeFatigueEngine.kt:588-635`; `AugeRecoveryEngine.kt:571-644,812-878,1024-1090`; `SessionEditorAugeComputation.kt:649-658`; `MuscularSessionImpactEngine.kt:193-210` (yo) | 2.2 |
| F-61 | P1 | Constantes duplicadas en ≥4 sitios (τ por pilar y canal) y `AugeRecoveryEngine` importa `NutritionRepository` (dominio→datos). | `AugeRecoveryEngine.kt:5,37-59,779,936`; `AugeAdaptiveEngine.kt:246-273`; `InitialRecoveryEvidencePolicy.kt:252-269` (yo) | 2.1 |
| F-62 | P2 | Código muerto verificado sin llamadores en `src/main`: `updateMuscleDeltas/updateDrainMultipliers/updateSystemLearningDeltas`, `learnRecoveryRate`, `calculatePersonalRecoveryStats` (fabrica «horas de recuperación»), `calculateSessionDrainRankings`, `calculateExerciseDrainRankings`, `calculateSleepRecommendations`, `mapWorkoutToPostSessionFeedback` (⇒ ramas de feedback muertas), `AugeSheetParts.kt` entero, `MuscleSliderChip`, `Settings.augePredictionBias`. **No borrar** `automaticBatteryAtAnchor` (es el único dato supervisado). | ver evidencia en cada archivo (yo, U, S) | 1.8 |
| F-63 | P2 | Pruebas que no protegen (matriz de 25 huecos en el anexo C): ida y vuelta lineal con `predictedBattery = actual` (`AugeRingsAudit2026Test.kt:46-69`), pines con impactos V2 escritos a mano (`AugeRingCharacterizationTest.kt:104-116`), pecho con capacidad legacy (`AugeRingDrainRealismTest.kt`), 3 de 4 pruebas de `WorkoutFinishCalibrationTest.kt` no llaman a producción (`:28,33-37,40-48`), aserción condicional vacua (`AugeTtcRecoveryHoursTest.kt:128-130`), `AxialLoadMonitorTest.kt:64` acepta WATCH o ADJUST, 8 archivos con reloj real. **Cero pruebas** de: mutex, `%RM`/lista vacía, redondeo en pantalla, ancla caducada, vista previa con historia larga, `applyManualBatteries`, aprendizaje idempotente o apilado. | `T/domain/auge/` (L-15, D, R-13, T, yo) | 0.2, 0.3 |

## 5. Lo que está sano — no tocar

Pines F0 (Energía 80/92/97, Columna 93/96/99, Pecho 72/80/85/97) reproducibles a mano; inversión de Columna exacta (`AugeRecoveryEngine.kt:304-315`); ley de ancla igual en motor y evidencia; V2 independiente de la capacidad actual (`:551-557`); soft-cap invariante de escala; sueño fuera de τ (`:169-172`); cierre atómico en Room con identidad determinista y reintento congelado; `mergeWellbeingForSave`; guardas de generación; histéresis de avisos; ajuste de carga que solo reduce con tope 30 %; nutrición neutra sin comidas; contrato pilar/display; migraciones Room de `auge_*` correctas; monotonía del drenaje con series, RPE y carga.

## 6. Diseño de la mejora

**Principios.** (1) Una sola fuente de verdad por concepto. (2) El aprendizaje evalúa el **mismo** modelo que pinta el anillo. (3) Tipos `Double` hasta el borde de la UI. (4) Todo cambio visible lleva tabla antes/después. (5) Sin migración Room: el caché es JSON (`AugeAdaptiveCacheEntity`), no se toca la versión de la base de datos (otras sesiones comparten esquema).

**6.1 Registro de parámetros (`AugeParameters`).** Un objeto versionado con τ por pilar/canal, constantes C (90/75/70), warps, pisos, capacidades por tipo de atleta, soft-cap, conservación, umbrales de bandas y de ACWR. Lo leen el motor, la evidencia inicial, el aprendiz y la UI. Prueba de consistencia: ninguna constante numérica de τ fuera del registro. **Calibrado para reproducir los valores actuales de ENTHUSIAST**; solo se vuelven explícitas las escalas por canal (F-30).

**6.2 Impacto canónico de sesión (`SessionImpactEngine`).** Un único agregador (serie → ejercicio → sesión) que devuelve `SessionImpact { perMuscle(stressUnits, directo, indirecto, capacidad), cnsLoad, spinalLoad, articular, bonos/duración, modelVersion }`. Se persiste al cierre como `WorkoutLog.sessionImpactV3` (superconjunto del V2, que se sigue escribiendo). El modo «planificado» usa la misma función con series sintéticas → editor = cierre por construcción. Logs sin V3 se calculan una vez con el mismo motor y se memoizan en memoria (sin reescribir historial). Los anillos se calculan **solo** desde impactos, no desde series.

**6.3 Pipeline único (`AugeSnapshotComputer`).** Una función pura `(history, wellbeing, settings, cache, nowMs) → AugeSnapshot` que usan `AugeViewModel`, `ProgramRepository`, el preview del cierre y la demo del wizard. El preview añade el impacto de la sesión actual a la historia y evalúa en el instante congelado (preview ≡ Home). Articular y ACWR reciben `nowMs`.

**6.4 Proyección (`AugeProjection`).** `timeToThreshold(canal/músculo, umbral)` por simulación hacia delante con bisección sobre el propio motor; sustituye `hoursToRecovery`/`hoursUntilNormal` y habilita «listo para…».

**6.5 Anclas y evidencia como estado.** La corrección manual es una **innovación** que ajusta el estado y decae con la dinámica del modelo; no se descarta a las 18 h. La evidencia inicial entra como pseudo-sesiones (`dayLoads`) en el mismo flujo → composición multiplicativa, caducidad y supresión por historial reales.

**6.6 Aprendizaje v4 (`AugeLearner`).** Estado por parámetro `θ_p = ln τ_p` con media, varianza y n (prior: `ln τ0` del registro, sd 35 %). Observación `y` = valor sentido (σ≈5 pts), rendimiento e1RM (σ≈12) o ΔRPE (σ≈10). Modelo de observación `h(θ)` = **la propia batería del motor** evaluada en el instante de la observación (incluye sesiones apiladas, pisos, desaceleración, bloques). Actualización de Kalman extendido con Jacobiano por diferencias finitas (±2 % en τ):

`ν = y − h(θ̂)` · `H = ∂h/∂θ` · `K = P·H / (H²P + σ²)` · `θ̂ ← θ̂ + K·clip(ν, ±2,5·√S)` · `P ← (1−K·H)·P + q·Δdías`

Reglas: τ solo con 8 h ≤ Δt ≤ 14 d desde la última sesión relevante; se censuran lecturas saturadas (≥99,5, en piso, |H| pequeña) y diferencias <3 pts; id de observación `(canal, ancla, valor, logId)` para idempotencia; el rendimiento es **bilateral** (las sorpresas positivas pesan ×0,5 por progresión) y usa mediana de 3 sesiones; se excluyen sesiones con molestia reportada o deload. **Escala de drenaje** `ln s_p` (opcional, WP 3.6): se identifica con la lectura del cierre (t≈0, `∂h/∂τ≈0`) y τ con la lectura del día siguiente; así el gesto del cierre vuelve a enseñar sin los tres parámetros a la vez de agosto.
**Observabilidad y seguridad:** anillo de 200 observaciones `(canal, y, predicho, Δt, fuente, τ usado)` dentro del caché JSON (reproducible y auditable); EWMA de |ν| y sesgo por canal («Precisión del modelo»); guardia: si tras ≥8 observaciones el error del modelo adaptado supera al del prior en >15 %, se congela y vuelve al prior (con evento de diagnóstico).
**Migración:** `schemaVersion 4`, τ aprendidos v3 **se descartan** (nacieron de la inversión sesgada) y se conserva todo lo demás; se reutiliza `modelUpdateNoticeShown` para avisar.

**Evidencia de que funciona (Python, 300 usuarios sintéticos, 4 sesiones/sem, σ=4 pts, zona muerta 2):**

| Escenario | Aprendiz actual (MAE de τ tras 30 obs) | Kalman (MAE) |
|---|---|---|
| τ real = por defecto (48 h) | 24,3 h, sesgo +24,3 | 2,6 h, sesgo −0,4 |
| τ real 60 h (más lento) | 30,2 h | 3,4 h |
| τ real 36 h (más rápido) | 16,9 h | 1,7 h |
| usuario ruidoso σ=7 | 18,5 h | 4,9 h |

Compartir información entre músculos (factor personal de velocidad) mejora solo ≈17 % la primera semana → **opcional**.

**6.7 Cambios de forma del modelo (gated por tus decisiones D1–D5).** Cada uno entra como parámetro del registro con tabla antes/después: SNC con progresivo sobre las series duras de **toda la sesión** (no por músculo); articular solo en Columna y readiness (o también en chips); agregador global de Músculos sensible a lo localizado (5 pts menos tras un día de pecho); capacidad con tope de crecimiento; Energía con compartimento lento (τ≈10 d, coherente con fitness-fatiga): 1→7 días duros baja de 95→94 (plano) a 92→82 y se recupera en 7–10 días.

## 7. Paquetes de trabajo

Ejecutor: **S** = Sonnet 5.5 · **H** = Haiku 5.5 máx. Máx. 3 agentes en paralelo, siempre en paquetes distintos; archivos calientes se serializan: `AugeViewModel.kt` (1.1→1.2→1.7→2.3→3.3), `AugeRecoveryEngine.kt` (1.4→1.5→2.1→2.2→2.3→2.4/2.5), `AugeFatigueEngine.kt` (1.8→2.1→2.2→2.6). Tope: 3 iteraciones por paquete antes de escalar.

| ID | Paquete | Ej. | Depende | Aceptación |
|---|---|---|---|---|
| **Fase 0 — Red de seguridad** | | | | |
| 0.1 | Línea base: correr los lotes E1–E5 y E6 (anexo C-E) por separado; confirmar la tarea Gradle con `:app:tasks`; documentar fallos previos y tiempos | H | — | `docs/audits/2026-10-rings-v4/baseline.md` con lote, tiempo y fallos preexistentes |
| 0.2 | «Máster dorado» (anexo C-D): corpus de ~30 sesiones con semilla fija y **fichas heterogéneas** (compuesto/aislamiento, axial 0/1: el fixture actual fija efc/cnc/ssc iguales), baterías por músculo y canal a 0/6/12/24/48/72 h, globales, dashboard y vista previa; valor crudo `Double` + entero mostrado | H | 0.1 | `AugeGoldenMasterTest` + `corpus`/`expected` en `src/test/resources/auge/golden/`; regenerar con `-Dauge.golden.update=true` (escribe en `build/`, nunca en `src`); diff por fila; tolerancia 0 |
| 0.3 | Arnés de usuarios sintéticos con el motor **real** (anexo C-D) y pruebas que documentan el sesgo actual del aprendiz | S | 0.1 | reproduce la tabla de 6.6; invariantes (rango, suelos, sin NaN, monotonía); misma semilla ⇒ mismo hash |
| **Fase 1 — Salvar datos y bugs baratos** | | | | |
| 1.1 | Mutex: recompute sin re-lock (variante «con candado ya tomado»); `try/catch` con diagnóstico; no tragar cancelación; `refresh` coalescido; seam mínimo para sembrar los repositorios singleton en pruebas | S | 0.1 | `AugeWriteMutexTest` (Robolectric, anexo C-D) con un caso por llamador bloqueado (5), afirmado **por efecto** (tras `advanceUntilIdle()`, una segunda operación que toma el mutex debe haber persistido): rojo hoy, verde tras el arreglo; 3 `saveWellbeing` seguidos con `cacheToPersist≠null` terminan |
| 1.2 | Bienestar: usar `mergeWellbeingForSave` en `applyManualBatteries`; no reaprender copias; leer fila al guardar y no reusar `id` de otro día; anclas por canal; la voz aplica los anillos dictados | H | 1.1 | pruebas de procedencia, medianoche, copia sin cambios y voz |
| 1.3 | Readiness: ÷100 de %RM; guarda de lista vacía; revisar otros `average().roundToInt()` | H | 0.1 | 60 %RM sin kg ⇒ factor 1,0; sesión de «Tibial Anterior» no lanza |
| 1.4 | ACWR: denominador = min(4, semanas con datos); factor continuo; `nowMs`; un solo mapa de umbrales; monitores sin falsos avisos | S | 0.2 | nuevo (5 ses/15 d) ⇒ sin ADJUST; estable 3×/sem ⇒ NONE; sin escalón >2 pts; pruebas con 14–27 días de historia (hueco 5); re-anclar los pines de ACWR (1,12 y >1,5 de `AugeLanguageConsistencyTest`) |
| 1.5 | Redondeo: `roundToInt`, ≥99,5⇒100, Float de la torre (`HomeRingsSection.kt:263`) y promedio de sesión (`HomeSessionSection.kt:435`), bandas coherentes | H | 1.4 | τ=48 h lee 100 desde ≤72 h; torre 53 muestra 53; primera prueba de redondeo en pantalla (huecos 4 y 21) |
| 1.6 | UI a prueba de recreación: ediciones por músculo y sliders del cierre guardables | H | 0.1 | Robolectric: recrear Activity conserva ediciones |
| 1.7 | `AugeClock` inyectable; `nowMs` en articular/ACWR/monitores/VM y en la capacidad de respaldo (`AugeRecoveryEngine.kt:387`); sin `System.currentTimeMillis()` en motores; las pruebas con reloj real (8 archivos) pasan a `nowOverride` | H | 1.2 | pruebas deterministas con reloj fijo; log con fecha futura no cuenta (hueco 12) |
| 1.8 | Código muerto y flags (F-62), conservando JSON compatible; retirar las pruebas de las funciones muertas (`AugeAdaptiveEngineTest`: 6 de 11; `AdaptiveCalibrationAppliesTest`: bloque de `updateDrainMultipliers`) y ajustar las que dependan de ellas | H | 1.3,1.5 | compila; grep sin llamadores huérfanos; el recuento de `@Test` baja solo por las retiradas declaradas |
| 1.9 | Textos: tabla de 25 textos (U) + umbrales y mensajes (S-08/09/12) | H | 1.6 | `AugeLanguageConsistencyTest` ampliada |
| 1.10 | «Sin datos» ≠ 100 %: titular/readiness/relator/chips; titular por canal más bajo; accesibilidad de sliders; placeholders | H | 1.6 | sin historial ⇒ sin titular ni «Salta aprox» |
| 1.11 | Creador de ejercicios: sin Core por defecto, marcar «sin datos» | H | 0.1 | ejercicio sin coincidencias ⇒ sin músculo inventado |
| 1.12 | Descarga automática bajo `augeAutoDeload` + confirmación; etiquetar datos inferidos; sugerencia de carga con el inventario (reutilizar `snapLoad`/`resolveDumbbell`, `Settings.kt:363,430`) y sin +5 % | H | 1.3 | sin flag no se inserta bloque; 5 kg −10 % no muestra «−10 %» |
| **Fase 2 — Unificación del motor** | | | | |
| 2.1 | `AugeParameters` + sustituir constantes duplicadas; el dominio deja de importar datos | S | 1.5,1.8 | prueba de consistencia; máster dorado sin diferencias |
| 2.2 | `SessionImpactEngine` + `sessionImpactV3`; migrar editor, resumen, cierre y anillos; borrar copias | S | 2.1 | máster dorado ±1 pt salvo cambios declarados; editor = cierre |
| 2.3 | `AugeSnapshotComputer` único (VM, programa, preview, wizard) con `nowMs`; preview ≡ Home | S | 2.2,1.7 | preview vs Home ≤1 pt con historia larga |
| 2.4 | `AugeProjection` y sustitución de «horas hasta normal» | S | 2.3 | error ≤0,5 h frente a simulación directa |
| 2.5 | Anclas como estado + evidencia como pseudo-sesiones | S | 2.3 | sin escalón a 18 h; composición multiplicativa |
| 2.6 | Cambios de forma D1–D5 (cada uno con tabla antes/después y punto de control contigo) | S | 2.3,tus decisiones | tablas aprobadas |
| **Fase 3 — Aprendizaje v4** | | | | |
| 3.1 | Caché v4 + migración + anillo de observaciones | S | 2.1 | v1/v2/v3→v4 conservan avisos/flags y descartan τ; ida y vuelta JSON del caché (huecos 19–20: hoy solo se prueba v2→v3 en memoria) |
| 3.2 | `AugeLearner` (Kalman) + pruebas de simulación | S | 0.3,2.3 | criterios de la tabla de abajo |
| 3.3 | Enganche manual (readiness/cierre), idempotencia, guardado atómico; borrar inversión y α global | S | 3.2,1.2 | un gesto = una observación; sin re-aprender copias |
| 3.4 | Enganche de rendimiento (bilateral, mediana, orden del historial, reloj, exclusiones) | S | 3.2 | prueba con historia DESC y con deload |
| 3.5 | Habilidad del modelo, guardia/rollback, eventos de diagnóstico (+ UI de solo lectura si lo apruebas) | S | 3.3 | congela y vuelve al prior en simulación adversa |
| 3.6 | (opc.) Escala de drenaje `s_p` aprendida del cierre | S | 3.3 | identifica `s` y τ con dos lecturas |
| **Fase 4 — Calibración con datos (opcional)** | | | | |
| 4.1 | Herramienta de backtest sobre un respaldo tuyo | H | 3.5 | informe predicho-vs-reportado |
| 4.2 | Elegir formas (Energía de dos escalas, capacidad, agregador) por habilidad medida | S | 4.1 | tablas + tu visto bueno |
| **Fase 5 — Cierre** | | | | |
| 5.1 | Regresión amplia (solo antes de `master`) | H | todo | verde |
| 5.2 | QA en el Flip (`.dbg`, regla de 20 min): 3 ediciones seguidas guardan, plegar no pierde, preview = Home | H | 5.1 | capturas |
| 5.3 | Docs: `docs/audits/2026-10-rings-v4/README.md`, ARCHITECTURE/ANDROID_ARCHITECTURE_MAP, anexos | H | 5.1 | docs al día |

**Tamaño relativo.** Fase 0: 3 paquetes pequeños. Fase 1: 12 paquetes pequeños/medianos, casi todos Haiku, 1–3 iteraciones cada uno. Fase 2: 6 paquetes grandes y **serializados** (Sonnet). Fase 3: 6 paquetes medianos con pruebas de simulación (Sonnet). Fases 4–5: opcional y cierre.

**Arranque tras la aprobación (lo hago yo, sin escribir código del repo):**
1. Crear `feat/rings-v4` desde `master` @ `7815e45b6` en un worktree nuevo (`C:/kw/tools/mkwt.sh`; hecho: `C:\kw\r4i`) y ramas `r4/<id>` por paquete, reutilizando worktrees ociosos de la ola anterior (`u1`, `sb`, `v3c`) porque el disco está al 97 %; **no se toca** el árbol principal (tiene ~1 300 entradas sin commitear de otra sesión).
2. Escribir `docs/rings-v4/BRIEF_COMUN.md` (adaptado de `docs/wizard-v3/BRIEF_COMUN.md`: entorno, Gradle con candado, tareas `Base`, estilo, informe ≤400 palabras) y los briefs cerrados de las fases 0 y 1 (archivos permitidos, cambio exacto, pruebas, comando).
3. Lanzar 0.1 y 0.2 (Haiku) y 0.3 (Sonnet) en paralelo; revisar diffs, correr pruebas filtradas e integrar por rutas explícitas.
4. Volcar a `docs/audits/2026-10-rings-v4/` los informes completos de los 7 agentes como anexos. Se recuperan de la transcripción de esta sesión (`C:\Users\valen\.claude\projects\C--Users-valen-Documents-KPKNFit\e5bf5841-3c4a-4327-a787-51d01257f542.jsonl`, entradas `<task-notification>`), porque algunos archivos de salida de tareas quedaron vacíos.
5. Guardar en memoria del proyecto el plan, tus decisiones y cómo retomar.

**Criterios del aprendiz (3.2), con el motor real:** (a) sin deriva: τ real = prior ⇒ |sesgo| ≤5 % tras 30 obs; (b) convergencia: |ln(τ̂/τ)| ≤0,15 tras 8 obs (σ=5); (c) signo: una lectura **peor** que la predicha nunca acorta τ; (d) idempotencia: mismo id, mismo estado; (e) lectura saturada o en piso ⇒ sin cambio; (f) un músculo nuevo tras 40 observaciones globales aprende como el primero; (g) sesiones apiladas (2–3 a 24 h) sin sesgo >10 %.

## 8. Sugerencias para ampliar y aprovechar el potencial

| # | Idea | Por qué (dato o hallazgo) | Impacto | Esfuerzo | Depende |
|---|---|---|---|---|---|
| S1 | **«Listo para…»**: hora estimada de ≥70/≥85 por anillo/músculo y para la próxima sesión planificada | simulación hacia delante (6.4) + agenda del programa | Alto | M | 2.4 |
| S2 | **Explicabilidad**: «por qué Pecho está en 71 %» (sesiones que más pesan, horas a 90 %) | `SessionImpact` por sesión | Alto | M | 2.2 |
| S3 | **Precisión del modelo y tu perfil de recuperación** (solo lectura): τ aprendido ± intervalo vs población, error medio de tus correcciones | estado del aprendiz v4; respeta «sin sliders» | Medio-Alto | S | 3.5 |
| S4 | **Resistencia a la fatiga intra-sesión**: caída de reps/e1RM o subida de RPE serie a serie calibra la forma del drenaje por ejercicio, sin preguntar | medida objetiva ya registrada | Alto | M | 2.2 |
| S5 | **Bucle cerrado de ajustes de carga**: registrar si aceptas la sugerencia y el RPE resultante; aprender el mapa readiness→recorte | hoy no hay realimentación | Alto | M | 1.12 |
| S6 | **Deload multi-señal**: tendencia de anillos al inicio de sesión + estancamiento de e1RM + RPE al alza, en lugar de «suma 14 d >75» | `ringStartSnapshot` ya está en cada log | Alto | M | 2.3 |
| S7 | **Planificador consciente de anillos**: llevar `InterferenceEngine` al editor/calendario («A y B comparten 62 % a 24 h») | ya existe en el asistente | Medio | M | 1.9 |
| S8 | **Cardio por FC y sRPE** (TRIMP-like) y distancia | `avgHeartRate`, `distanceKm` se capturan y no se usan | Medio | S-M | 2.2 |
| S9 | **Catálogo**: `ttc` ya está en 1 106 entradas (rango 1,5–2,2) pero el motor lo ignora; añadir demanda articular por articulación | F-42 | Medio | M | 2.6 |
| S10 | **Energía de dos escalas** (aguda + crónica) para que la frecuencia importe | pines 87/86/85; simulación | Alto (si el backtest lo confirma) | M | 4.2 |
| S11 | **Backtest y reproducción** del historial: herramienta de depuración con predicho-vs-reportado por observación | anillo de observaciones | Alto (mantenimiento) | M | 3.1 |
| S12 | **Widget/notificación «listo para entrenar»** | no existe widget de anillos | Medio | M | S1 |
| S13 | **Nutrición opt-in con datos suficientes** como modulador de confianza (no de τ) | sistema de alimentos ya auditado | Bajo-Medio | M | 4.2 |
| S14 | Aprendizaje por ejercicio con `technicalQuality`, molestias y `perceivedFailure` | datos del cierre que hoy no alimentan nada | Medio | M | 3.3 |

## 9. Verificación

- **Por paquete:** `:app:compileBaseDebugKotlin` + pruebas filtradas por clase con `:app:testBaseDebugUnitTest --tests …` (nombre estándar de AGP para el flavor `base`; `flavorDimensions`/`base`/`health` confirmados en `android-native/app/build.gradle.kts:29-36`; 0.1 lo comprueba con `:app:tasks`), siempre por el envoltorio con candado (`C:/kw/tools/gradle_slot.sh` o `.opencode/scripts/run-gradle-locked.ps1`), en segundo plano, nunca la suite entera.
- **Filtros por clase, no por archivo:** `AugeRingsAudit2026Test.kt` define 13 clases, `AugeRingsGuideAndMonitorTest.kt` 6 y `AxialLoadMonitorTest.kt` 2 (nombres en el anexo C-E); un `--tests` con el nombre del archivo no las selecciona y el lote pasaría en vacío.
- **Antes/después:** el máster dorado (0.2) se vuelve a correr en cada paquete numérico y se adjunta la tabla de diferencias; los cambios declarados se re-anclan con motivo.
- **Simulación:** las pruebas del aprendiz (3.2) usan el motor real y semillas fijas; ninguna prueba nueva usa reloj real.
- **Antes de `master`:** una regresión amplia (5.1) con vigilante de tiempo (el repo ya tuvo un cuelgue de 7 h en una clase de onboarding).
- **Teléfono (5.2):** `.dbg` con datos de demostración, regla de 20 min y sin tocar tus datos reales: tres ediciones seguidas de anillos, plegar/desplegar con la hoja abierta, cierre por voz, vista previa = Home.
- **Mapa de pruebas** (anexo C): compuerta rápida de cada paquete numérico = lotes E1+E3+E5 (JVM pura) + el máster dorado; E2/E4 según el área; E6 (Robolectric/Room, pesado) solo antes de integrar. Qué pruebas se re-anclan o retiran en cada paquete: tabla C-F.

## 10. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Cambiar números visibles sin querer | máster dorado + tabla antes/después por paquete; unificar primero **sin** cambiar números |
| Regresión silenciosa en el aprendiz | simulación con motor real y 7 criterios; guardia que vuelve al prior |
| Choque con otras sesiones (Codex, esquema de BD compartido) | sin migración Room; worktrees separados; archivos calientes serializados |
| Memoria/Gradle escasos | máx. 3 agentes y 2 ranuras; sin builds en paralelo en el mismo worktree |
| Reabrir decisiones de producto | sueño, 24 h, sliders, HC e iOS siguen cerrados; la UI nueva (S3) es solo lectura |
| Borrar el único dato supervisado | `automaticBatteryAtAnchor` se conserva y se amplía a Energía/Columna |

## 11. Decisiones tuyas (defecto recomendado — puedes responder al aprobar o en cada punto de control)

- **D1 · Lo articular**: A) solo en Columna y readiness, fuera del multiplicador de los 13 pilares *(recomendado)* · B) también en los chips.
- **D2 · Músculos global**: mantener 0,85·media+0,15·cuartil *(recomendado hasta el backtest)* · cambiar a 0,5/0,5 con los 3 peores (≈ −5 pts tras un día de pecho).
- **D3 · SNC**: progresivo sobre las series duras de la **sesión** *(recomendado, con tabla)* · mantener por músculo.
- **D4 · Energía con compartimento lento**: posponer a la fase 4 *(recomendado)* · hacerlo ya.
- **D5 · Escalas por canal**: conservar los valores actuales de ENTHUSIAST y hacerlos explícitos *(recomendado)* · recalibrar pines 80/93.
- **D6 · Backtest con tu historial real**: exportas un respaldo y lo analizo en local, solo lectura *(recomendado)* · solo sintéticos.
- **D7 · UI de solo lectura «Precisión del modelo / tu recuperación»**: sí *(recomendado)* · no.

## Anexo A — Control de calidad de los agentes Haiku

| Agente | Alcance | Cobertura | Comprobaciones mías | Veredicto |
|---|---|---|---|---|
| D · drenaje | AugeFatigueEngine, MuscularSessionImpact, capacidad, cardio | 100 % del alcance | editor (`SessionEditorAugeComputation.kt:525-537,709-732`), Core del creador, suelo de sesión; reprodujo los pines 80/90/93 | Aceptado; D-01/D-02 bajan de P0 a P1 |
| R · recuperación | AugeRecoveryEngine 1-1935, RecoveryBands | 100 % | ACWR, ancla 18 h, horas a normal, overlay, evidencia aditiva, fechas futuras (todo leído por mí); curvas coinciden con mi simulación | Aceptado |
| L · aprendizaje | motor adaptativo, rendimiento, VM, evidencia | 100 % | ida y vuelta Energía/músculo/Columna **coincide con la mía**; orden DESC del historial; migración; copias de readiness | Aceptado; L-01 sube a prioridad máxima |
| P · persistencia | VM, repos, Room, respaldo, cierre | ~85 % (resto fuera de AUGE) | confirmó F-01 por su cuenta; `applyManualBatteries` con un único llamador; `produceState` una vez | Aceptado; P-07/P-08 bajan a P2 |
| U · superficies | Home, readiness, cierre, editor, wizard, textos | ~75 % (resto por grep) | float32 53→52 y 59→58 exactos; `remember` no guardable; titular sin mirar cobertura; `AugeSheetParts.kt` 0 referencias | Aceptado; U-02 baja a P2, U-03 a P1 |
| S · decisión | readiness, avisos, TTC, programa | 100 % de motores | ÷100 reproducido; descarga automática verificada en `BlockTransitionEngine.kt:181-194`; patrones del catálogo; hizo su propio análisis de alcance del NaN | Aceptado; S-01 baja a P2, S-02..S-04 a P1 |
| T · pruebas | `T/domain/auge/` (39 archivos con pruebas + 1 fixture) y 8 dependientes | 100 % de `domain/auge`; parcial en onboarding y cierre; no ejecutó Gradle | recuento 39 archivos / 215 `@Test` (grep propio); releí `AugeRingsAudit2026Test.kt:46-69`, `WorkoutFinishCalibrationTest.kt` entero, `AugeTtcRecoveryHoursTest.kt:112-135`, `AxialLoadMonitorTest.kt:44-75`, `AugeLanguageConsistencyTest.kt:96-127`, `AugeRecoveryEngine.kt:376-397` (`nowMs()` en `:387`), `HomeSessionSection.kt:435`; dependencias de prueba en `build.gradle.kts:241-246`; `AugeSheetParts.kt`: sus 9 declaraciones solo se usan dentro del propio archivo | Aceptado con 4 correcciones: (1) «oráculo vacío» de `WorkoutFinishCalibrationTest` es 3 de 4 pruebas, no todas; (2) sus filtros por archivo omitirían las clases de `AugeRingsAudit2026Test.kt` (13), `AugeRingsGuideAndMonitorTest.kt` (6) y `AxialLoadMonitorTest.kt` (2): corregido en C-E; (3) su esqueleto del mutex usaba `withTimeout` sobre métodos que no son `suspend` (lanzan con `viewModelScope.launch`): corregido en C-D para afirmar por efecto; (4) el nombre de la tarea Gradle queda por confirmar en 0.1 |

**Lo que Haiku no halló y sí yo:** planicie «99 %», horas efectivas en `hoursToRecovery`, segundo snapshot de `ProgramRepository`, ÷100 de %RM, ≥8 copias de la receta, trinquete del aprendizaje por rendimiento, Energía plana ante la frecuencia, constantes duplicadas en la evidencia. **Lo que yo no había visto:** F-01, reaprendizaje de copias, migración con deltas v2, orden DESC del historial, escalón del ACWR, escalón de 18 h, SNC por músculo, doble conteo de descanso en superseries, Core del creador, descarga automática sin flag, ediciones perdidas al plegar, cierre por voz.

## Anexo B — Evidencia numérica propia (Python con constantes reales)

- **Ida y vuelta sin ruido (inversión actual):** músculo (τ=48) ×1,44–3,45 a 8 h, ×1,22–2,46 a 12 h, ×1,16–1,89 a 24 h; Energía (τ=36) ×0,41–0,47 a 8 h, ×0,50–0,54 a 12 h, ×0,67–0,79 a 24 h; Columna (τ=52) ×1,0–1,8 a 8 h, ×1,1–1,3 a 24 h. Arreglar solo el `90 vs 100` deja el músculo en ×1,06–1,5.
- **Modelo hacia delante muscular (τ=48 h, sesión 3 000/5 000):** batería 74,4 a 6 h; 95,3 a 48 h; 99,26 a 72 h → muestra 99 hasta ≈168 h.
- **Drenaje:** progresivo muscular por serie 1,0·1,05·1,10·1,0·0,9·0,8·0,62·0,49·0,38·0,30; acumulado 10 series = 7,6, 20 = 10,6; SNC 1,0→2,6 (tope en la 10.ª). Energía: resumen 10/20/40/60 → caída 4,4/8,6/16,4/23,6.
- **Energía vs frecuencia (un solo exponencial):** 1 sesión 95,0; 2 → 94,3; ≥3 → 94,2 (plano). Con compartimento lento (30 % de la carga, τ≈10 d): 92,3 → 88,8 → 86,6 → 85,0 → 83,9 → 83,1 → 82,5; recuperación tras 5 días: 87→96,5 a los 5 d.
- **Músculos global tras un día de pecho (pilares 72/85/88, resto 100):** actual 93,7; soft-min 93,8; 0,5·media+0,5·peores3 = 88,7.
- **Pooling entre músculos:** error de ln τ en la 1.ª semana 0,115 (con pooling) vs 0,139 (sin) → mejora ≈17 %; se diluye hacia la semana 6.

## Anexo C — Mapa de pruebas (agente T; muestra verificada por mí, ver anexo A)

### C-A · Inventario y veredictos
`T/domain/auge/` tiene 40 `.kt`: 39 con **215 `@Test`** (recuento propio) y el fixture `AugeRealSessionFixtures.kt`. Fuera de esa carpeta, 8 archivos llaman a motores AUGE: `AugeSnapshotCoverageTest` (`T/screens/auge/`), `SetupRingsMergeTest`, `SetupRingsHomeCoverageTest` y `RingsChannelCoverageTest` (`T/domain/onboarding/`), `RingsIntroStepTest` y `RingsResultV2Test` (`T/screens/onboarding/rings/`), `RingsIntroRenderSheetTest` (`T/render/`) y `PlannedIntensityPolicyTest` (`T/screens/workout/`). Stack: JUnit 4, Robolectric 4.12.2, coroutines-test 1.8.1 (`build.gradle.kts:241-246`); **sin mockk ni turbine** y sin bloque `test {}` (el estado estático se filtra entre clases). `domain/auge` es JVM pura; `AugeViewModel` solo se instancia con Robolectric (patrón en `SessionEditorViewModelRulesTest`; el reinicio de `AugeRepository.INSTANCE` se hace por reflexión en `WorkoutFinishCancellationTest`).

| Veredicto | Archivos (n.º de pruebas) |
|---|---|
| **Mantener** | `AugeMuscleNormalizationTest` (6) · `AugeMuscleAttributionTest` (3) · `AugeUnilateralImpactTest` (1) · `AugeSupersetDensityTest` (1) · `AugeFatigueEngineEffectiveSetTest` (4, duplica `AugeUnitsConsistencyTest`) · `CardioRingDrainBoundsTest` (2) · `WellbeingSaveMergeTest` (10) · `DiscomfortSuggestionEngineTest` (9) · `ExerciseReadinessEngineTest` (17) · `ExerciseReadinessEngineArticularTest` (6) · `NutritionRecoveryEngineTest` (7) · `OvernightRecoverySensationTest` (2) · `PerformanceImpliedBatteryTest` (2) · `SleepLogExtendedToSleepLogTest` (1); fusionar `AugeMuscleProfileTest` (1) en `AugeMuscleNormalizationTest` |
| **Re-anclar si cambian números** | `AugeRingCharacterizationTest` (2; todos los pines) · `AugeRingDrainRealismTest` (6; ruta legacy, su cabecera ya dice «DEBEN FALLAR tras la Fase 1») · `InitialRecoveryEvidencePolicyTest` (13; 76/85/82) · `AugePerMuscleInitialEvidenceTest` (6; 88/77) · `InitialRecoveryEvidenceV2Test` (8; 88 y revisión de contrato) · `PerformanceTauObservationTest` (10; τ exactos 70→60) · `AugeLanguageConsistencyTest` (6; 78, 1,12, ACWR>1,5) · `AugeTtcRecoveryHoursTest` (2; fórmula con `toInt()`) · `ExerciseReadinessBlendPinTest` (1; 74) · `RingsIntroSimulatorTest` (14; (84,89,77) a 0 h y (99,96,99) a 36 h) y, con él, `RingsIntroStepTest`, `RingsIntroRenderSheetTest`, `RingsResultV2Test` · `AugeSnapshotCoverageTest` (4; caducidad v1) |
| **Obsoletas** | `AugeAdaptiveEngineTest` (6 de 11) y el bloque de `updateDrainMultipliers` en `AdaptiveCalibrationAppliesTest`: solo ellas llaman a `updateSystemLearningDeltas`/`updateMuscleDeltas`/`updateDrainMultipliers` (verificado; WP 1.8) |
| **Engañosas o frágiles** | ver C-B; además `AugeRecoveryEngineRingTest.kt:99-104` tautológica, `:54-108` con `perMuscle` vacío no entra en la rama lineal; `SessionDrainBoundsTest` (`predictedAndCompleted_…` no compara la predicción); `AugeUnitsConsistencyTest` (`completionMs > 0` tautológico); `AugePostSessionPreviewImmediateTest` (no comprueba horas; usa las 03:00 del 6-sep-2026, fuera del hueco horario de Santiago); `WorkoutLogAugeImpactSerializationTest` (solo `setInputHash`); `OvertrainingDetectorTest` (sin caso negativo) |

### C-B · Oráculos
- **Autoconfirmantes:** `AugeRingsAudit2026Test.kt:46-69` (modelo directo con el mismo `TAU_K_95` y tiempo lineal, `predictedBattery = actual`, tolerancia 5 % que absorbe el sesgo; **no prueba** las ramas sigmoidal ni espinal de la inversión) · `AugeTtcRecoveryHoursTest` (réplica de la fórmula) · `AugeRecoveryEngineManualOverrideTest` (`min(97, …)`: no discrimina pesos) · evidencias con `sensationPenalty` de producción (consistencia, no magnitud) · `WorkoutFinishCalibrationTest.kt` (3 de 4 pruebas sin producción) · `AugeEditorCompletedParityTest` (plan construido desde lo completado, ±2).
- **Referencia independiente (conservar):** `InitialRecoveryEvidenceV2Test` (cálculo a mano), `ExerciseReadinessEngineArticularTest` (1/3,5), `AugeMuscleNormalizationTest`, `PerformanceImpliedBatteryTest`, `OvernightRecoverySensationTest`.
- **Contratos que quedarían sin protección al unificar la receta:** (1) τ en ramas sigmoidal y espinal con sus clamps y el caso `actualDepletion ≥ sessionStress`; (2) drenaje y capacidad legacy frente a los públicos (ninguna prueba los compara); (3) mezcla estructural 0,6/0,4; (4) ACWR `/4`; (5) preview↔Home con base <100; (6) magnitud de la penalización por sensación; (7) horas de recuperación. → el máster dorado (0.2) los cubre.

### C-C · Matriz de huecos (25) y paquete que los cierra
| # | Ítem | Estado | WP |
|---|---|---|---|
| 1 | τ invierte el modelo (sesgo) | NO | 0.3 documenta · 3.2 cierra |
| 2 | `recompute` bloquea (mutex) | NO | 1.1 |
| 3 | `%RM` sin ÷100 / lista vacía | NO | 1.3 |
| 4 | Meseta «99 %» por `toInt()` | NO | 1.5 |
| 5 | ACWR `/4` (14–27 días) | PARCIAL | 1.4 |
| 6 | Ancla manual caducada a 18 h | NO | 2.5 |
| 7 | Preview ≠ Home con historia (resta lineal) | NO | 2.3 |
| 8 | Editor ≠ cierre ≠ Home (solo hay prueba del motor) | NO | 2.2 |
| 9 | `applyManualBatteries` pierde `source`/`capturedFields` | NO | 1.2 |
| 10 | Evidencia inicial aditiva | PARCIAL | 2.5 |
| 11 | «Horas hasta normal» cortas | NO | 2.4 |
| 12 | Logs con fecha futura en Energía/Columna | NO | 1.7 |
| 13 | Código muerto (solo 3 funciones tienen pruebas) | PARCIAL | 1.8 |
| 14 | Determinismo con `nowMs` fijo | PARCIAL | 1.7 |
| 15 | Idempotencia del aprendizaje por `logId` | NO | 3.3 |
| 16 | Aprendizaje con sesiones apiladas | NO | 3.2 (g) |
| 17 | Ley del ancla entre canales tras el decaimiento | PARCIAL | 2.5 |
| 18 | Guardado de caché concurrente | NO | 1.1 |
| 19 | Ida y vuelta JSON de `AugeAdaptiveCache` | NO | 3.1 |
| 20 | Migraciones (solo v2→v3 en memoria) | PARCIAL | 3.1 |
| 21 | Redondeo en pantalla | NO | 1.5 |
| 22 | Más series ⇒ más drenaje | PARCIAL | 0.3 |
| 23 | Sin NaN con entradas raras | PARCIAL | 1.3 |
| 24 | Planificado ≡ completado | PARCIAL | 2.2 |
| 25 | Paridad preview↔Home con historia larga | NO | 2.3 |

Cubierto y sólido: monotonía temporal de la recuperación, secundario = 0,5 en todo el código, fechas rotas en nutrición, suelos y topes del drenaje, evidencia sin doble conteo.

### C-D · Esqueletos de pruebas nuevas (en `T/domain/auge/` salvo indicación)
- **Arnés de usuarios sintéticos** (`AugeSyntheticUserHarness` + `AugeSyntheticUserInvariantsTest`, WP 0.3): `SyntheticUser(id, atleta, sesionesPorSemana, intensidad, semanas, semilla)`; historiales con `kotlin.random.Random(semilla)` sobre plantillas del fixture (con fichas heterogéneas) y `muscularImpactV2` generado por `MuscularSessionImpactEngine.evaluate` real; muestreo cada 6 h por la ruta de Home (`getPerMuscleBatteries` → `calculateGlobalBatteries` → `calculateRecoveryDashboard` → `computeSnapshotCoverage`). Invariantes: 0–100, ≥ `physiologicalFloor`, sin NaN, recuperación monótona entre sesiones, ACWR nulo con <14 días, preview↔Home con presupuesto de divergencia medido, **misma semilla ⇒ mismo SHA-256** (delata relojes ocultos). 10 usuarios × 90 días ≈ 3 600 cadenas: filtro propio. El mismo arnés genera las «sensaciones» para 3.2 (motor real con un τ verdadero distinto + ruido).
- **Máster dorado** (`AugeGoldenMasterTest`, WP 0.2): corpus y esperado en `src/test/resources/auge/golden/` (carpeta nueva; `src/test/resources` ya existe); tabla canónica con claves ordenadas: drenaje por sesión (`calculateCompletedSessionDrainBreakdown`), baterías por músculo a 0/6/12/24/48/72 h, globales, dashboard y preview, en crudo y entero mostrado. Una sola tabla cubre las ≥8 copias de la receta: un cambio intencional produce un diff revisable en vez de re-anclar 40 pruebas.
- **Mutex** (`T/screens/auge/AugeWriteMutexTest`, WP 1.1): Robolectric (`@Config(sdk=[34])`) como `AugeSnapshotCoverageTest`; `Dispatchers.setMain(StandardTestDispatcher())`, reinicio de `AugeRepository.INSTANCE`, historial y ajustes sembrados (hace falta el seam: `ProgramRepository` y `NutritionRepository` son singletons sin constructor). Un caso por llamador con candado previo: `saveWellbeing`, `applyManualBatteries`, `dismissAdvisory`, `clearManualBatteryOverrides`, `resetAdaptiveCache`. **Corrección a T:** esos métodos son `fun` + `viewModelScope.launch` (no `suspend`), así que `withTimeout` no sirve; se afirma **por efecto**: tras `advanceUntilIdle()`, una segunda operación que toma el mutex (p. ej. `markModelUpdateNoticeShown`, `AugeViewModel.kt:1073`) debe haber persistido, o una sonda `internal` de `isLocked` debe estar libre. Hoy falla porque el `Mutex` no es reentrante, sin depender del planificador.

### C-E · Lotes para compuertas rápidas
Tarea `:app:testBaseDebugUnitTest` (por confirmar en 0.1); cada `--tests` en su propio argumento; **por nombre de clase** (ver sección 9). Cada lote debe informar cuántas pruebas ejecutó (0 ⇒ filtro inválido). Prefijo `com.example.kpkn.domain.auge.` salvo E6.

| Lote | Clases | Coste |
|---|---|---|
| E1 Drenaje | `AugeMuscle*` · `*DrainBounds*` · `AugeUnilateralImpactTest` · `AugeSupersetDensityTest` · `AugeFatigueEngineEffectiveSetTest` · `PerformanceImpliedBatteryTest` · `AugeEditorCompletedParityTest` · `AugePostSessionPreviewImmediateTest` · `WorkoutLogAugeImpactSerializationTest` · `SleepLogExtendedToSleepLogTest` | ligero |
| E2 Recuperación y anillos | `AugeRecovery*` · `AugeRingCharacterizationTest` · `AugeRingDrainRealismTest` · `OvernightRecoverySensationTest` · `AugeTtcRecoveryHoursTest` · `NutritionRecoveryEngineTest` · de `AugeRingsAudit2026Test.kt`: `SpinalWarpContinuityTest`, `SpinalAxialGateTest`, `HardSessionSpinalFloorTest`, `CardioSystemicDrainTest`, `SpineRingSingleDefinitionTest`, `ManualSpinalOverrideSticksTest`, `MuscleColdStartParityTest`, `ReadinessSheetHomeParityTest` | medio |
| E3 Aprendizaje | `AugeAdaptiveEngineTest` · `AdaptiveCalibrationAppliesTest` · `PerformanceTauObservationTest` · de `AugeRingsAudit2026Test.kt`: `SystemTauRoundTripTest`, `TauObservationCapTest`, `PerformanceTauProgressionBiasTest`, `ManualSpinalLearningAxialGateTest`, `AdaptiveCacheSchema3MigrationTest` | ligero |
| E4 Evidencia inicial y arranque | `InitialRecovery*` · `AugePerMuscleInitialEvidenceTest` · `RingsIntroSimulatorTest` · `WellbeingSaveMergeTest` | ligero-medio |
| E5 Readiness, avisos y carga | `ExerciseReadiness*` · `AxialLoadMonitorTest` y `SystemicLoadMonitorTest` (ambas en `AxialLoadMonitorTest.kt`) · `OvertrainingDetectorTest` · `DiscomfortSuggestionEngineTest` · `AugeUnitsConsistencyTest` · `AugeLanguageConsistencyTest` · de `AugeRingsGuideAndMonitorTest.kt`: `RecoveryBandsSingleSourceTest`, `RingProjectionTest`, `PersonalBaselineTest`, `LoadAdvisoryHysteresisTest`, `AdvisoryDismissalTest`, `LoadAdvisorySessionContextTest` | ligero |
| E6 Fuera de dominio (pesado, Robolectric/Room) | `com.example.kpkn.screens.auge.AugeSnapshotCoverageTest` · `com.example.kpkn.domain.onboarding.*Rings*` · `com.example.kpkn.screens.onboarding.rings.*` · `com.example.kpkn.screens.onboarding.SetupRingsAnswersV2Test` · `com.example.kpkn.render.Rings*` · `com.example.kpkn.data.onboarding.SetupRingsPreviewPayloadTest` · `com.example.kpkn.screens.workout.WorkoutFinish*` · `com.example.kpkn.screens.workout.PlannedIntensityPolicyTest` | pesado; solo antes de integrar |
| E7 Compuertas | rápida = E1+E3+E5 · completa AUGE = `com.example.kpkn.domain.auge.*` | ligero / medio |

### C-F · Qué se re-ancla, retira o crea por paquete
| WP | Pruebas |
|---|---|
| 0.2, 0.3 | crear máster dorado y arnés; endurecer `AugeRealSessionFixtures` (hoy fija efc 3,0 / cnc 3,0 / ssc 0,7 para todos los ejercicios) |
| 1.1 | crear `AugeWriteMutexTest` (5 casos) |
| 1.3 | crear: %RM sin kg y lista vacía; revisar `ExerciseReadinessBlendPinTest` (74) |
| 1.4 | re-anclar `AugeLanguageConsistencyTest` (1,12; ACWR>1,5); endurecer `AxialLoadMonitorTest.kt:64` (acepta WATCH o ADJUST); crear 14–27 días |
| 1.5 | re-anclar `AugeTtcRecoveryHoursTest`; crear redondeo/meseta y redondeo en pantalla |
| 1.7 | mover a `nowOverride`: `AugeRecoveryEngineRingTest`, `AugeRingDrainRealismTest`, `NutritionRecoveryEngineTest`, `AugeRecoveryEngineManualOverrideTest`, `AugeRecoveryEnginePreWorkoutDiscomfortTest`, `SessionDrainBoundsTest`, `AugeTtcRecoveryHoursTest`, `AugeSnapshotCoverageTest`; crear fecha futura |
| 1.8 | retirar las obsoletas (C-A); fusionar `AugeMuscleProfileTest` |
| 2.1, 2.2, 2.6 | re-anclar con tabla antes/después: `AugeRingCharacterizationTest`, `AugeRingDrainRealismTest`, `SessionDrainBoundsTest` (topes 32/32/35 si cambian), `AugeEditorCompletedParityTest` (±2 → exacto), `RingsIntroSimulatorTest` y dependientes |
| 2.3, 2.4 | crear preview≡Home con historia larga; fijar cifras en `RingProjectionTest` (hoy solo el signo) |
| 2.5 | re-anclar `InitialRecoveryEvidencePolicyTest`, `AugePerMuscleInitialEvidenceTest`, `InitialRecoveryEvidenceV2Test`, `AugeSnapshotCoverageTest`, `ManualSpinalOverrideSticksTest`; crear caducidad de ancla |
| 3.1–3.4 | re-anclar `AdaptiveCacheSchema3MigrationTest`, `PerformanceTauObservationTest`; sustituir `SystemTauRoundTripTest` por la simulación con motor real; revisar `TauObservationCapTest` y `PerformanceTauProgressionBiasTest` |
