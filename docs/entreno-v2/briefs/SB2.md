# S-B2 · Cabos sueltos del programa y de la semana, revisión final y retiro de la UI antigua

Lee antes `docs/entreno-v2/BRIEF_COMUN.md`, `docs/WIZARD_ENTRENO_V2.md`, `docs/entreno-v2/COPY.md`, `docs/WIZARD_PAGINA_LARGA.md` y `docs/entreno-v2/briefs/SB.md` (el contrato del paquete anterior, que sigue vigente).
Tu worktree parte de la rama de integración, donde ya aterrizaron S-B (PLAN, WEEK_LAYOUT y activación), W (el cableado visual de los pasos y el arnés `WizardHarnessActivity`), D3 (aproximación y movilidad) y D1b (el generador con el catálogo ampliado).
Eres el dueño de `SetupWizardViewModel.kt`, `SetupWeekLayouts.kt`, `SetupReviewStep.kt`, `SetupTrainingSteps.kt` y de `screens/onboarding/entreno/EntrenoPlan*.kt` / `EntrenoWeekLayoutStep.kt` en esta fase. No toques `domain/training/generator/**` (D1b) ni los componentes de `design/entreno/**` salvo defecto de accesibilidad que no se pueda resolver desde el paso: si lo necesitas, descríbelo en el informe.

## Por qué
S-B dejó el recorrido de punta a punta, pero con cabos que rompen la regla de oro («nada de UI de relleno; lo declarado cambia lo que se activa») o que dejan basura:
la semana armada puede contradecir el lugar de un día, la revisión final mide minutos con otro criterio, una preselección de la biblioteca produce un aviso alarmante sin motivo, y el código del flujo antiguo sigue en el árbol.

## Qué entregas
1. **Una sesión movida a un día de otro lugar** (`moveSession`, `SetupWeekLayouts.kt`): hoy la sesión conserva `Session.placeId` aunque el día destino tenga declarado otro lugar (`dayPlaces`), así que la ficha dice «Gimnasio» un día que la persona entrena «En casa».
   Invariante pedido: **el programa que se activa nunca trae una sesión cuyos ejercicios no se puedan hacer con el material declarado para su día sin que la persona haya sido avisada**. Reglas:
   - Compatible (el material del lugar del día destino cumple todos los ejercicios de la sesión según el filtro único `ConfigurationEquipmentFilter` vía `DayEquipment`/`EquipmentSymbols`, nunca otra comprobación): la sesión pasa a ese lugar (`placeId` nuevo) sin ruido, y el tablero lo refleja.
   - Incompatible: el movimiento se permite (la persona manda), la sesión conserva su lugar, y el tablero y la revisión final muestran un aviso honesto y persistente con el texto de COPY (añade la entrada si falta: «Esta sesión usa material de <lugar>; ese día entrenas en <lugar>.»). Deshacerlo es mover otra vez o «Restablecer».
   - Un solo punto de verdad: la lógica vive en una función pura junto a `applyLayout(draft, program, …)`, la usan la vista previa y la activación, y el reparto adaptado (`adaptToSplit`) sigue rechazándose con varios lugares (`SPLIT_MIXED_PLACES`).
2. **Minutos de la revisión con el estimador común**: hoy los minutos de una receta fija salen de una fórmula propia (`SetupWizardViewModel.estimateFixedSessionMinutes`: series × (45 s + descanso acotado a 30–300 s), sin calentamiento, aproximación ni movilidad) y solo para los planes que no son NATIVE; tres sitios los comparan con reglas distintas
   (`fixedRecipeDifference` del VM y la de `SetupReviewStep.kt` usan `> minutesPerSession` a secas; la puerta de activación del VM usa `timeBudgetWithTolerance`). Unifica: los minutos de la revisión se miden sobre el programa **ya armado** (semana, aproximación y movilidad incluidas) con `SessionDurationEstimator` (el mismo que ya usa el revelado en `SetupPlanReveals.kt`) y una única función de «¿cabe?» con las tolerancias del asistente (±1 min para los generados, 15 % para los de autor).
   Anota en el informe cuánto se desvía el estimador por su calentamiento fijo de 180 s cuando el programa ya trae aproximación y movilidad explícitas (no lo cambies: lo usa más código).
3. **Preselección desde la biblioteca** («Configurar este plan», `withPreselectedPlan` + `applyPreselection`): con `native:complete-athlete-v2` el objetivo pasa al perfil general «Fuerza y cardio», que solo ofrece su programa «a medida», así que el plan elegido cae y aparece «Tu plan elegido ya no encaja con tus respuestas» sin que nada haya cambiado. Arréglalo con un camino honesto: si el plan de la biblioteca ya no se ofrece desde un perfil general, el aviso dice qué pasó («Este programa de la biblioteca ahora se arma a medida en el asistente.») y deja el candidato «a medida» a un toque, sin alarma. Revisa los demás planes propios y de autor con la misma lente y deja una prueba por cada perfil.
4. **Paridad de avisos de PLAN** (`ownPlanNotice`, `droppedSelectionNotice`, `incompatibilityNotice` en `SetupTrainingSteps.kt`): confirma que cada motivo que explicaba la pantalla antigua del plan lo explica la nueva (`EntrenoPlanNotices.kt`) con las mismas reparaciones de un toque; si `ownPlanNotice` no tiene consumidor en la UI nueva, decide con una frase en el informe si se borra o se recupera.
5. **Marca de «pendiente de revisar» del reparto** (cosmético): cuando un cambio anterior (días, material, minutos, plan) invalida la semana armada, hoy `withoutWeekLayout()` la limpia en silencio. Comprueba que el paso WEEK_LAYOUT queda marcado como pendiente (no «hecho») y que la barra de progreso y el botón de la página lo reflejan; si ya es así, solo añade la prueba.
6. **Defectos de PLAN y WEEK_LAYOUT que halló W** (ver el anexo al final; lo que sea visual y ya lo corrigió W en `ent/w` no se repite).
7. **Retira la UI antigua**: borra de `SetupTrainingSteps.kt` y de sus vecinos todo lo que ya no tiene un camino alcanzable desde la ruta nueva (pantallas de `DAYS`, `STYLE`, `SPLIT`, `AUTOREGULATION`, `WARMUPS` con los preajustes 40/60/80, `TrainingPlanStep` antiguo, `FromScratchSessions`, `TrainingChoiceStep`, `BikePresenceConfirmation` y lo que cuelgue solo de ellas) y los ayudantes que solo usaban pruebas (`compatibleSplitTemplates`, `splitDisplayName`, `draftSplitLabel`, `warmupRecipesFromRaw`, `isSplitOfferedForGoal`) **con sus pruebas**, si D5 ya cubre su lógica en dominio. Se queda lo que sigue vivo (p. ej. los presentadores de rechazo que usa `EntrenoPlanNotices.kt`, `trainingMilestoneRows`): comprueba el alcance con `Grep` antes de borrar cada símbolo y deja el enum `SetupStepId` intacto (se lee de borradores viejos). Los textos de pasos retirados salen de `SetupStepDefinitions` solo si ninguna prueba de lectura de borradores los necesita.
8. **androidTest a la ruta nueva** (`SetupWizardFullJourneyUiTest`, `SetupWizardJourneyUiTest`, `WizardControlSemanticsUiTest` si cita pasos retirados): actualízalos para que compilen y describan el recorrido real (lugares → material → objetivo → … → PLAN → WEEK_LAYOUT). Solo compilan (`compileBaseDebugAndroidTestKotlin`); **nunca los ejecutes en el teléfono** (es del usuario). El comportamiento lo cubren las pruebas de ViewModel y de Robolectric.

## Pruebas (obligatorias)
- ViewModel con fakes: mover a un día de otro lugar, compatible e incompatible; vista previa = activado en ambos casos; «Restablecer» deshace el aviso.
- Revisión: el estimador común sobre un programa generado y sobre uno de autor, con y sin aproximación; la diferencia de minutos se explica en una prueba con aritmética independiente.
- Preselección de la biblioteca: un caso por cada perfil (propios y de autor) sin «selección caída» espuria.
- Marca de pendiente del reparto tras cambiar días / material / plan.
- Todo `com.example.kpkn.screens.onboarding.*` y `domain.onboarding.*` en verde; `domain.training.split.*` y `domain.training.approach.*` intactos; `assembleBaseDebug` y `compileBaseDebugAndroidTestKotlin` compilan.

## Teléfono
Comprueba en el teléfono (con `phone_run.py` y el arnés de W; lee `docs/entreno-v2/BRIEF_COMUN.md`) el aviso de la sesión movida y el aviso de la preselección. Capturas en `C:\kw\shots\sb2\phone\`. Una sesión corta y agrupada.

## Informe (≤ 400 palabras)
Qué cambió por punto, símbolos borrados (cuántas líneas y archivos salen), qué quedó sin consumidor o sin probar, desviación del estimador, y los cambios que necesites en archivos compartidos.

## Anexo (se completa con los hallazgos de W cuando entregue su informe)
*(pendiente)*
