# Diagnóstico — task_0003: último descanso (UI + voz + molestias)

**Modo: solo diagnóstico. Cero ediciones de código.** Todo confirmado contra working tree actual + `git log -S / -G` sobre `android-native/`.

---

## 1) UI de último descanso (timer-shrink + panel calidad + molestias) — EXISTE, no fue eliminada

### Evidencia git

- Panel feedback embebido AGREGADO en `9dfd3228` (pickaxe `-S timerSize`, `-S FeedbackContent`, `-S postExerciseFeedbackContent` en `screens/workout/components/WorkoutRestOverlay.kt`).
- Retoques: `a88c3b62`, `5e451470` (F0-F5), `99e27fb6` (Liquid Glass Beta 8.3).
- `git log -S 'animateDpAsState'` (overlay): sin hits de shrink; el tamaño compacto siempre fue estático.
- `speakAskTechnicalQuality`, `speakAskDiscomfort`, `announceFeedbackSheetPrompt` entraron en `f268bb0b` (fix voz 1-10), tras la auditoría `docs/AUDITORIA_SISTEMA_VOZ_2026-08.md:177-186` (bug B5).
- Conclusión: ningún commit removió la UI. Nada que restaurar; sí verificar y reforzar.

### Timer que se encoge (parcial, estático)

Archivo: `screens/workout/components/WorkoutRestOverlay.kt`. Timer normal: 204.dp en `NormalRestContent` (306-307). Con panel activo, `FeedbackContent` (línea 123) baja a 152/132.dp según ejercicios (182-184): switch ESTÁTICO, sin animación; transición slide/fade del bloque (69-80, `AnimatedContent` + `SizeTransform`). Minimizado manual: `RestTimerPill` (737-810) vía `WorkoutOverlayHost.kt:67-87` (`isRestMinimized`).

### Panel calidad técnica + molestias

- `screens/workout/WorkoutPostExerciseFeedbackHost.kt:81` `WorkoutPostExerciseFeedbackContent`:
- Slider "Calidad técnica" 1-10 (169-204); slider "Qué tan intenso fue" + chip "Fallo" (206-253).
- Card "Molestias previas relacionadas" por articulación compartida, chips Sigue/Resuelta (270-340).
- Acordeón "¿Sientes alguna molestia?" (359) con buscador (368-389) + chips del `DISCOMFORT_CATALOG` + info (391-417).

### Cableado al último descanso

- `screens/workout/WorkoutStepNavigator.kt:329-345` — último set: `showPostExerciseSheet = shouldShowFeedback`, `pendingPostExerciseIdx = -2` (337).
- `screens/workout/WorkoutScreen.kt:729` `isShowingFeedback`; `:784-797` pasa `postExerciseFeedbackContent` + `forceShowForFeedback`.
- `screens/workout/WorkoutOverlayHost.kt:42-45` — overlay visible con panel aunque el timer esté detenido (caso último rest).
- `screens/workout/WorkoutRestTimerOrchestrator.kt:277-316` — al terminar/omitir el rest con `pending == -2`: reabre sheet u `openFinishSheet()`.

### Test vigente

`androidTest/java/com/example/kpkn/screens/workout/WorkoutRestModalUiTest.kt:96-120` — `rest_modal_can_embed_post_exercise_feedback_without_second_popup`.

### Qué falta (a)

1. Shrink animado real del timer (hoy salto 204 -> 152/132.dp).
2. Test del caso último-descanso (`pendingPostExerciseIdx = -2`, timer detenido + `forceShowForFeedback`).
3. `WorkoutViewModel.requestPostExerciseFeedback()` (2552-2556; delega a `WorkoutFeedbackController.kt:45-54`) sigue SIN call-sites: API muerta (auditoría B5).

---

## 2) Flujo de voz en último descanso — el prompt HABLA, pero el parse NO captura en voice-only

### Qué existe (fix `f268bb0b`, post-auditoría B5)

- TTS: `services/workout/WorkoutTtsManager.kt:241-243` `speakAskTechnicalQuality()` y `:245-247` `speakAskDiscomfort()`.
- Disparo: `screens/workout/WorkoutStepNavigator.kt:360-371` aparca `voicePendingFeedbackExerciseIds` (371) -> `screens/workout/WorkoutRestTimerOrchestrator.kt:102-107` las consume al iniciar rest -> `services/workout/WorkoutVoiceController.kt:449-461` `onVoicePendingFeedbackPrompt()`.
- Esa función: loguea `feedback_prompt_shown` (455-458), empuja gramática con vocabulario de feedback (459) y habla vía `announceFeedbackSheetPrompt(isFinal = false)` (460); la rama no-final habla ambas preguntas (442-445).
- Gramática: `WorkoutVoiceController.kt:3582` OR con `voiceFeedbackPromptActive` en `toVoiceCommandContext()` -> parser:226-236 inyecta "calidad, tecnica, ejecucion, molestia, dolor, tiron, hombro, rodilla, codo, muñeca..." (`WorkoutVoiceGrammarBuilder.kt:21-22`).
### ROTURA del parse (el fix de f268bb0b quedó a medias)

1. `WorkoutVoiceController.kt:1571` — `parseFeedbackCommand` solo corre si `exerciseInfo.showPostExerciseSheet == true`. El provider (`WorkoutViewModel.kt:628-683`, línea 676) pasa el flag crudo `s.showPostExerciseSheet` -> en el prompt vale false -> la respuesta NUNCA entra al parser.
2. Fallback `WorkoutVoiceController.kt:2016-2030`: llama a `WorkoutVoiceIntentMatcher` con `showPostExerciseSheet = false` HARDCODEADO (línea 2024) -> `WorkoutVoiceIntentMatcher.kt:28-30` salta el parse -> cae a `parseCommand` genérico -> Unknown (o peor: los números ya están en la gramática base, parser:237-238, y "ocho" puede absorberse como serie).
3. `voiceFeedbackPromptActive` (controller:104, set :453, reset :465/:477-478) se consulta SOLO para gramática (:3582), no para el gate. La ASR entiende la frase; el router la descarta.
4. Último rest: rama `announceFeedbackSheetPrompt(isFinal = true)` (controller:433-437 "¿Alguna molestia o nota final?...") SIN call-sites (solo :460 con false): rama muerta. El caso final `WorkoutStepNavigator.kt:329-345` NO aparca ids: enciende el sheet en silencio.
5. Parse final (`parseFinalFeedbackCommand`, parser:798-852) solo corre con `showFinishSheet == true` (controller:1562-1568) -> campos `voiceFinal*` (handler `WorkoutVoiceCommandHandler.kt:828-890`); no toca calidad técnica por ejercicio.

### Cuando el sheet SÍ está visible (camino que funciona)

- `parseFeedbackCommand` (parser:757-796): keywords "calidad/tecnica/ejecucion" (764); excelente=10, muy buena=9, buena=8, regular=6, mala=3 o número coerce 1-10 (765-772; `extractNumberFromText` :868-881; `VOICE_INTEGER_WORDS` :745-755); RPE (775-778); molestias (780-786); save keywords (760-761).
- Handler `WorkoutVoiceCommandHandler.kt:718-826`: default `technicalQuality = 8` (758); aplica calidad (796-799), RPE (801-804), discomfortIds (806-815); escribe `postExerciseFeedbackByExerciseId` (818-822); superserie por alias (`WorkoutVoiceExerciseAliasMatcher`, 733-751); con "guardar" persiste (`WorkoutFeedbackController.kt:60-79`), TTS `speakFeedbackSaved` (controller:696), log `feedback_registered` (781-788), `completeVoiceFeedbackPrompt` (controller:463-468).

### Inyección real al sistema (funciona cuando el valor llega)

- Modelo: `data/models/WorkoutLog.kt:159-*` `PostExerciseFeedback{technicalQuality, discomfortIds, perceivedIntensityRpe, perceivedFailure, stillPresentDiscomfortIds}`.
- Calidad -> estrés: `WorkoutFinishController.kt:190-199` promedia -> `technicalQuality10ToPenaltyScale` (`WorkoutFeedbackModels.kt:214-218`, 1-10 -> 1-5) -> `AugeFatigueEngine.calculateTechniquePenalty` (196-199) -> stressScore (205).
- Molestias -> resumen: `WorkoutScreen.kt:878-884` -> `DiscomfortAggregationEngine.computeSessionDiscomfortSummary` (`domain/auge/DiscomfortAggregationEngine.kt:20-85`) -> finish sheet.
- Por el bug 1571/2024, en voice-only el promedio cae al default 8.0 (`WorkoutFinishController.kt:193-194`): la inyección real nunca ocurre ahí.

### Alertas de descanso (intactas, ajenas al feedback)

- `services/workout/WorkoutRestAlertManager.kt:32-571` — canales (45-46), alarmas exactas, tonos PCM (~505-541), vibración (543-560), `RestTimerFinishedReceiver` (573-593).
- `services/workout/WorkoutRestAlertRules.kt:5-67` — pre-alerta -3s (`WORKOUT_REST_PREALERT_LEAD_SECONDS = 3`, 5; trigger 25-28), `workoutPrealertTonePlan` (39-43), `workoutCompletionTonePlan` D5/F5/A5 (45-49), vibración por intensidad (51-67).
- `services/workout/WorkoutRestForegroundService.kt:17-52` — notificación ongoing con `endAt`. Flags al overlay: `WorkoutRestTimerOrchestrator.kt:68` + `WorkoutRestModalState{notificationsEnabled, exactAlarmGranted, soundReady}`. git log: solo fixes, nada removido.

---

## 3) Molestias tipo "dolor de muñeca" + drill-down — existe pero débil

### Qué existe

- Matcher flat: `WorkoutVoiceCommandParser.kt:858-866` — tokens >=4 letras (excluye "dolor/molestia/tengo", 863) contra haystack `label+description+section.label`, `.take(3)` (865); "ninguna/sin molestia/todo bien" -> "none" (859-860).
- Catálogo: `data/models/DiscomfortCatalog.kt:6-168` — secciones (SHOULDERS_ARMS, SPINE_NECK, HIP_PELVIS, KNEE, ANKLE_FOOT, GENERAL); `wrist_hand` = "Muñeca / mano" (64-71); helper `discomfortLabel(id)` (173-174).
- Drill-down voz: multi-match -> `discomfortCandidates` (parser:784-785) -> handler:719-721 -> `requestDiscomfortSelection` (controller:659-674; `VoicePendingAction.DiscomfortSelection` en `WorkoutVoiceSessionState.kt:83-86`; gramática CONFIRM_WAIT) -> resolución por substring del label (controller:1672-1674) -> `LogFeedback(null, id, null)`; clear controller:2061-2063.

### Problemas

1. Falsos positivos por sección: "dolor de hombro" también matchea codo/muñeca (comparten "Hombro y brazos") -> `take(3)` mezcla zonas.
2. Sin drill-down por zona: falta flujo "zona (codo) -> sub-tipo (interna/externa)" con candidatos restringidos.
3. Label de confirmación obsoleto: mapa hardcodeado `WorkoutVoiceCommandHandler.kt:892-900` con ids viejos ("wrist", "hip") -> `wrist_hand` cae al genérico "articulación" en el TTS.

---

# Plan propuesto (pendiente de aprobación; NO implementado)

### (a) Verificar y completar la UI de último descanso

1. Shrink animado: `animateDpAsState(timerSize, label = "rest-timer-shrink")` + `animateFloatAsState` para fontSize/stroke en `WorkoutRestOverlay.kt:182-184` (204 -> 152/132.dp; la Column ya empuja el panel).
2. Test nuevo en `WorkoutRestModalUiTest.kt` (junto a línea 96): último descanso con `forceShowForFeedback = true` y timer detenido -> panel visible + timer compacto, sin "Saltar descanso" duplicado. `gradlew.bat connectedAndroidTest` targeted.
3. Confirmar pill vs panel: `WorkoutOverlayHost.kt:42-43` ya gatea con `!forceShowForFeedback` (correcto).
4. Higiene opcional: borrar o cablear `requestPostExerciseFeedback` (`WorkoutViewModel.kt:2552-2556`); si se cablea, resetear `announcedPostFeedbackPrompt` antes de anunciar para que vuelva a hablar.

### (b) Voz: calidad 1-10 con inyección real

1. Desbloquear parse: `WorkoutVoiceController.kt:1571` -> `showPostExerciseSheet == true || voiceFeedbackPromptActive`; y `:2024` -> pasar el mismo OR al IntentMatcher (así `IntentMatcher:28-29` rutea a `parseFeedbackCommand`).
2. Último rest habla: en `WorkoutRestTimerOrchestrator.start()` (tras línea 101) detectar rest final (`pendingPostExerciseIdx == -2` o sheet recién encendido sin ids) -> `announceFeedbackSheetPrompt(isFinal = true)` (hoy rama muerta controller:433-437). Alternativa: aparcar ids también en `WorkoutStepNavigator.kt:337`.
3. Número desnudo = calidad: con `voiceFeedbackPromptActive`, aceptar "ocho" sin keyword en `parseFeedbackCommand` (parser:763-773) vía `extractNumberFromText` coerce 1-10; la gramática ya tiene números (parser:237-238).
4. Checklist runtime: en logs `feedback_prompt_shown` (controller:455-458) seguido de `feedback_registered` (handler:781-788) con calidad != 8; y al cerrar sesión `WorkoutFinishController.kt:190-199` con avg != 8.0.

### (c) "Dolor de muñeca" robusto + drill-down por zona

1. Matcher por pesos en parser:858-866: score label (alto) > description (medio) > section.label (bajo; excluir el término genérico de sección, p.ej. "hombro"). Sinónimos: muñeca/muñecas/mano -> wrist_hand; codo -> elbow_*; rodilla -> knee_*; lumbar/espalda baja -> lower_back; aquiles -> achilles; cuello -> neck_cervical; ingle/aductores -> adductor_groin; isquios -> hamstring_proximal.
2. Drill-down por zona: si solo resuelve familia (`shoulder_*`, `elbow_*`, `knee_*`, `hip_*`), publicar `DiscomfortSelection` solo con entradas de esa zona + TTS con opciones ("¿interna o externa del codo?", "¿anterior o posterior del hombro?"); extender resolución (controller:1672-1674) para aceptar "interna/externa/anterior/posterior/lateral/medial".
3. Label correcto: borrar mapa de handler:892-900 y usar `discomfortLabel(id)` (DiscomfortCatalog.kt:173-174): arregla `wrist_hand` -> "Muñeca / mano" en TTS y evita desfasajes.
4. Tests en `WorkoutVoiceFeedbackParserTest.kt`: "dolor de muñeca" -> wrist_hand; "dolor de hombro" -> candidates solo shoulder_*; "rodilla por dentro" -> knee_medial; "molestia lumbar" -> lower_back; test de handler con label del catálogo.

### Validación (al implementar)

- `cd android-native; ./gradlew.bat test --tests "*WorkoutVoiceFeedbackParserTest*"` (b)(c).
- `./gradlew.bat connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.kpkn.screens.workout.WorkoutRestModalUiTest` (a).
- `./gradlew.bat assembleDebug` al final.

### Riesgos en (b)

- "guardar" sigue siendo keyword de finish (parser:801, `WorkoutVoiceIntentMatcher.kt:25-27`): no interceptar el cierre de sesión cuando aplique.
- Al activar `parseFeedbackCommand` en el rest, frases ambiguas tipo "8 por 12" deben seguir yendo a RegisterSet. Prioridad: keyword de feedback -> feedback; patrón de serie -> serie.

