# Sistema B — Módulos de coaching (check-ins) y onboarding

**Fuente:** `nu_libapp.so` (Nutrition 5.8.9, arm64). Digests: `re/digests/B_nu.txt` (391 clases), `B_nu_skel.txt`, `B_nu_fns.txt`.
**Prefijos analizados:** `package:macrofactor/src/coaching_modules/*`, `coaching_bases/*`, `coaching_definitions/*`, `enums/coaching_module*.dart`, `enums/checkin_state.dart`, `enums/onboarding_module.dart`, `models/coaching_module_status.dart`.

---

## B.0 Estructura (OBSERVADO)

| Capa | Clases |
|---|---|
| Enumeraciones | `CoachingModule {introduction, partialLogging, weighIn, fasting, loggingBreak, programUpdate}` (valor JSON = mismo nombre); `CheckinState {dismissed, future, now, none}`; `CoachingModulePriority {required=0, high=1, low=3}` (el índice 2 no sobrevivió al *tree shaker* ⇒ INFERIDO que existe un nivel intermedio sin uso); `OnboardingModule {basics → "0", goal → "2", program → "3"}` (el "1" no aparece). |
| Definiciones | `CoachingModuleDefinition` (base) y `PartialLoggingModuleDefinition`, `WeighInModuleDefinition`, `ProgramUpdateModuleDefinition`, `LoggingBreakModuleDefinition`, `FastingModuleDefinition` (`coaching_definitions/*`). |
| Estado persistido | `CoachingModuleStatus` (`models/coaching_module_status.dart`) con claves JSON `skip` y `lastSeen`. |
| Base de check-in | `CheckinBaseView`, `CheckinBaseViewModel`, `CheckinBaseViewState` (`coaching_bases/checkin_base/*`). |
| Módulos (vista + viewmodel + estado + argumentos + providers) | `IntroductionModuleViewModel`; `PartialLoggingModuleView/ViewModel/ViewState/ViewArguments(+Model)`; `WeighInModule…`; `FastingModule…`; `LoggingBreakModule…`; `ProgramUpdateModule…`. |
| Pasos (steps) | Fasting: `FastingDaysIntroductionStep`, `FastingDaysReviewStep`, `confirm_fasting_day_step`. Goal: `GoalTypeStep` (+`GoalTypeInput`), `GoalWeightRateStep`, `GoalSummaryStep`, `GoalEditSummaryStep`. Logging break: `ExpenditurePauseStep`, `ExpenditureResumeStep`. Program: `ProgramStyleStep` (+`ProgramStyleInput`), `ProgramUpdateStep`. Weigh-in: `WeighInOrNotStep` (+`WeighInOrNotInput`). Basics (onboarding): `weight_step` (`_WeightStepState.setKilograms/setPounds`, getter `weightKg`), `affiliate_step` (`AffiliateStepViewModel.setAffiliateCodeString`), `initial_expenditure_chat_tree` (enum `ExpenditureChatTreeKey {step1, step1More, step2, step2More, step3, step3More, step4}`). |
| Widgets | `GoalRatePicker` / `_GoalRatePickerState` / `GoalRateTextField`; `FastingWeekLog`; `PartialLoggingHint`; `expenditure_resume_graphic` (enum `DataSymbol {untrackedCircle, trackedCircle, noResultSquare, resultCircle}`). |

## B.1 Afirmaciones

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Hay seis módulos de coaching y tres módulos de onboarding (basics, goal, program). | OBSERVADO | Enums `CoachingModule` y `OnboardingModule` (instancias const). |
| Cada módulo tiene una definición con prioridad (`required` / `high` / `low`) y un estado por usuario con `skip` y `lastSeen`. | OBSERVADO (clases y claves JSON) / INFERIDO (semántica: «no volver a mostrar» y fecha del último check-in) | `CoachingModulePriority`; `CoachingModuleStatus` (pool con 'skip', 'lastSeen'). |
| Un check-in puede estar `now`, `future`, `dismissed` o `none`. | OBSERVADO | Enum `CheckinState`. INFERIDO: `future` = programado, `dismissed` = descartado por el usuario. |
| Todos los view-models de módulo comparten el ciclo `completeModule`, `onTapDoNotShow`, `saveData`, `setAllowDoneButtonAfterSpinner`. | OBSERVADO | Nombres de funciones derivadas (tear-offs/forwarders) en `FastingModuleViewModel`, `LoggingBreakModuleViewModel`, `PartialLoggingModuleViewModel`, `WeighInModuleViewModel`; `IntroductionModuleViewModel` sólo `completeModule`. |
| **Partial logging:** revisa días registrados parcialmente y recalcula calorías (`refreshCalorieData`); muestra una pista (`PartialLoggingHint`). | OBSERVADO (nombres) / INFERIDO (semántica) | `PartialLoggingModuleViewModel` tiene además `refreshCalorieData`; widget `PartialLoggingHint`. Qué cuenta como «parcial» (umbral de kcal/comidas): DESCONOCIDO. |
| **Weigh-in:** pregunta si pesarse o no y permite introducir el peso en el propio check-in. | OBSERVADO (nombres) / INFERIDO (flujo) | `WeighInOrNotStep`/`WeighInOrNotInput`; view-model con `onWeightChanged`, `optInEnterWeightYes`, `optInEnterWeightNo`. |
| **Logging break (parón de registro):** explica la pausa del gasto y su reanudación con un gráfico de símbolos por día (círculo no registrado / registrado, cuadrado sin resultado, círculo con resultado). | OBSERVADO (clases y enum) / INFERIDO (significado de los símbolos) | `ExpenditurePauseStep`, `ExpenditureResumeStep`, `DataSymbol`. Relación con `ExpenditureStrategy {updating, holding}` (sistema A): INFERIDO. |
| **Fasting:** detecta/confirma días de ayuno y los revisa en un registro semanal. | OBSERVADO (nombres) / INFERIDO (semántica) | `FastingDaysIntroductionStep`, `FastingDaysReviewStep`, `confirm_fasting_day_step`, `FastingWeekLog`. |
| **Program update:** check-in que propone actualizar el programa nutricional (`ProgramUpdateStep`), con argumentos propios. | OBSERVADO (nombres) | `ProgramUpdateModuleView*`, `ProgramUpdateModuleViewArguments(Model)`. Criterio de disparo: DESCONOCIDO. |
| **Goal (onboarding/edición):** tipo de objetivo (`GoalType {bulk "b", cut "c", maintenance "m"}`), tasa semanal elegida con un selector que tiene mínimo, máximo, máximo absoluto, recomendado y dos zonas «rojas». | OBSERVADO (campos) / INFERIDO (semántica de zonas) | `_GoalRatePickerState`: `selectedRate` (off 13), `minimumRate` (14), `maximumRate` (15), `maximumRateInit` (16), `maximumRateAbsolute` (17), `recommendedRate` (18), `dangerZoneRed` (28), `upperZoneRed` (29), `rateDescription` (30), `ratePerWeekController` (5). Valores de los límites: DESCONOCIDO (calculados en código sin nombre). |
| **Program style (onboarding):** elección de estilo de programa (`ProgramStyleStep`/`ProgramStyleInput`). | OBSERVADO (nombres) | — |
| **Basics (onboarding):** guarda actividad, opción «atleta», cumpleaños, % graso, experiencia de cardio y fuerza, ejercicio, altura, peso histórico (`LifetimeWeight`), sexo, cambio de peso reciente (`WeightChange`), unidades. | OBSERVADO | `BasicsModuleViewModel`: `saveActivity`, `saveAthleteOption`, `saveBirthday`, `saveBodyFat`, `saveCardioExperience`, `saveExercise`, `saveHeight`, `saveLifetimeWeight`, `saveSex`, `saveStrengthExperience`, `saveWeightChange`, `setBirthday`, `setBodyFat`, `setHeightInCm`, `setHeightUnit`, `setWeightInKgs`, `setWeightUnit`. |
| En el onboarding hay un «árbol de chat» de cuatro pasos (con variantes «más info») para explicar el gasto inicial. | OBSERVADO (enum) / INFERIDO (contenido) | `ExpenditureChatTreeKey`. Los textos no se reproducen. |
| Hay un paso de código de afiliado en el onboarding. | OBSERVADO | `AffiliateStepViewModel.setAffiliateCodeString`. |

## B.2 Lo que NO se pudo determinar

- Reglas de disparo de cada check-in (días sin registro, días sin pesaje, desviación respecto al objetivo) y el orden/frecuencia de presentación (`lastSeen` + prioridad): el código vive en métodos normales sin nombre.
- Valores numéricos del selector de tasa (mín/máx/recomendado por objetivo).
- Textos de los módulos (no se extraen por copyright; sólo existen como cadenas localizadas en/ja/de).

## B.3 Ronda 2 (desensamblado) — regla del check-in semanal y orden de módulos

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Estado del check-in (@0x1058a68, Nutrition): sin programa → `none`; bandera booleana del programa (w19) activa → `dismissed`; `ProgramStyle.manual` → `none`; si no, se calcula una fecha ancla a partir del día de la semana configurado (`user.w26 ?? WeekDay.monday`, @0x1058bbc) y, si `(ancla − fechaReferencia).inDays > 2` → `now`, en caso contrario → `future`. | OBSERVADO (umbral 2 días, `monday` por defecto) / INFERIDO (significado de ancla y referencia: último check-in programado frente a última actualización del programa) | `re\dis_nu\dis_1058a68.txt` |
| Cadencia y contadores del check-in (@0x1058784, 672 B; estado = objeto `this` con `w15` = usuario, `w27` = `CheckinState`, `w29/w31/w33` = enteros): `hoy = date(DateTime.now())` (@0x9641fc + @0x96425c); `D0 = date(0x1460aa4(servicio))` (fecha ancla del plan; esa función usa una `Duration` de 8 días y formatea fechas `en_US`); `B = 0x7daed4(hoy, 0x1058a24(this))` (INFERIDO: fecha del próximo check-in = hoy + k días). Si el estado es `now` o `none`: `w29 = (hoy − D0).inDays`, `w33 = 0`. Si es `future`/`dismissed`: `next = cond(usuario, hoy, B) ? hoy + 7 días : ((B − D0).inDays < 3 ? B + 7 días : B)` y `w29 = (hoy − D0).inDays`, `w33 = (next − hoy).inDays`, `w31 = (next − D0).inDays`. La `Duration` de 7 días es pool[22014] = 604 800 000 000 µs. @0x105867c calcula el estado con @0x1058a68, lo guarda en `this.w27` y llama a esta función si el estado es `now` o el usuario existe. | OBSERVADO (constantes 7 días, umbral `< 3`, campos escritos) / INFERIDO (significado de D0, B y `cond` @0x11f9518) | `dis_1058784.txt`, `dis_105867c.txt`, `--range 1058a24 11f9518 7daed4 1460aa4` |
| Orden fijo de evaluación de módulos: `[introduction, loggingBreak, fasting, partialLogging, weighIn, programUpdate]` | OBSERVADO (lista construida en @0xa94b50) / INFERIDO (que es prioridad de presentación) | `--range a94b50` |
| `setDoNotShow(module)` persiste la opción «no mostrar» (@0x13c2864, argumento con nombre `doNotShow`). | OBSERVADO | — |
| Umbrales de *partial logging* y *logging break* (días sin registrar, % de días). | DESCONOCIDO — tres enfoques agotados: (1) rangos que referencian los enums `CoachingModule.*`/`CheckinState.*` (sólo vistas y el check-in semanal), (2) barrido de la tabla de despacho buscando métodos virtuales comunes a las cinco `*ModuleDefinition` (cids 2415-2420) sin filtro y (3) con filtro anti-alineación: todos los candidatos resultaron ser funciones ajenas (streams, `ParentData`, gradientes). Pista INFERIDA: el motor de gasto define «día presente» como `0 < kcal ≤ 50 000` y exige ≥ 85 % de días presentes en la ventana (ver `A_formulas_gasto_tendencia.md`, A.F3); es probable que el módulo de registro parcial reutilice esa definición. | — | — |
