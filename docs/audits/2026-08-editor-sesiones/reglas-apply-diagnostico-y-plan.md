# Diagnóstico y plan — "Aplicar" de Reglas de Sesión no aplica nada
## Investigación A (traza estática) — redactada para cruzar con una auditoría paralela

- **Repo:** KPKNFit (`android-native/`) — rama `master`, con cambios SIN COMMIT en el working tree (ver §3)
- **Método:** traza estática completa UI → ViewModel → engine → modelo → persistencia → vivo. No se ejecutó la app ni tests. Todo lo marcado CONFIRMADO tiene evidencia `archivo:línea` en el working tree actual.
- **Síntoma reportado:** "Pongo los valores, presiono aplicar pero NADA se aplica. Las tarjetas de sesión simplemente lo ignoran."

---

## 1. Cadena verificada (el camino feliz SÍ funciona)

1. `RulesSheet.kt:736-741` — botón **Aplicar** → `onApplyRules(scopePartId)`.
2. `SessionEditorScreen.kt:769-779` — `onApplyRules` → `viewModel.applyRuleDefaultsToSession(partId)` + snackbar.
3. `SessionEditorViewModelStructure.kt:594-621` — lee `getRuleDefaultsForPart(partId)` (`:177-180`) y llama `updateSession { SessionEditorRulesEngine.applyDefaults(session, defaults, partId, exerciseIndex) }`.
4. `SessionEditorViewModel.kt:550-587` — `updateSession`: si `transformed == current` → **return sin hacer nada**; si difiere → actualiza `_uiState`, marca `hasUnsavedChanges`, `scheduleAugeRecalc()` + `scheduleAutoSave()` (autosave default ON, `SessionEditorModels.kt:182`, persiste a Room a los 2 s vía `persistRecoverableSession` `:251-264`, solo si `weekId` no está en blanco).
5. Campos de la sheet commitean correctamente: `SheetMiniField` commitea por tecla (`RulesSheet.kt:104-110`), diálogos de descanso al confirmar (`:226-271`), chips de intensidad directo. Wiring de parámetros correcto en `SessionEditorScreen.kt:826-839`.
6. Las tarjetas recomponen desde `uiState` (`SessionEditorScreen.kt:151`: `session = uiState.activeVariantSession ?: uiState.session`); filas de serie re-sincronizan su estado local cuando cambia el valor y no tienen foco (`InlineSetRow.kt:626-632`); descanso por tarjeta re-sincroniza vía `LaunchedEffect(exercise.id, exercise.restTime)` (`ExerciseEditorCard.kt:183`).

**Conclusión parcial:** en variante A + scope válido + defaults distintos de los valores actuales, Aplicar SÍ materializa cambios en el modelo y persiste. El fallo está en los casos donde eso no se cumple y en el feedback.

---

## 2. Hallazgos (defectos)

### H1 — CONFIRMADO: no-op silencioso + snackbar de éxito falso
- Desde `47ab319f` la sheet **siembra sus valores desde la sesión** (mediana de rest/sets/reps/RPE, `SessionEditorViewModel.kt:407-430`). Al abrir REGLAS, los valores mostrados **ya coinciden con las tarjetas**.
- Si Aplicar produce una sesión igual, `updateSession` no-op (`:556`) — por diseño.
- Pero `SessionEditorScreen.kt:771-778` muestra **siempre** "Defaults aplicados a la sesión" (SUCCESS), **incondicionalmente**. Éxito falso + cero cambios visibles = el síntoma exacto del usuario.

### H2 — CONFIRMADO (estático) / PROBABLE CAUSA RAÍZ: scope de grupo inexistente en la sesión transformada → no-op
- Chips de grupo de la sheet: `uiState.session?.parts` (`RulesSheet.kt:344`) = **siempre variante A**. `updateSession` transforma la **variante activa** (`SessionEditorViewModel.kt:552,566`).
- Editando variante B/C/D con scope a un grupo de A: `applyDefaults` no encuentra el `partId` en la sesión transformada → `scopedExerciseIds` vacío → devuelve sesión idéntica (`SessionEditorRulesEngine.kt:96-116`) → no-op silencioso. Mismo resultado si el grupo existe pero está vacío. Idem tab TIEMPO (`RulesSheet.kt:743` lee `uiState.session`).
- Evidencia de reproducción previa: el working tree contiene logs `RulesDebug` y un hack "fallback global" agregados a mano en `applyRuleDefaultsToSession` (ver §3) — alguien ya observó `result == session` con scope.

### H3 — CONFIRMADO: regresión en working tree — Aplicar BORRA el descanso entre lados
`SessionEditorRulesEngine.kt:92` (working tree) revierte el fix F1 commiteado:
- Commiteado (correcto): `restBetweenSidesSeconds = if (safeSideRest > 0) safeSideRest else this.restBetweenSidesSeconds`
- Working tree (roto): `restBetweenSidesSeconds = safeSideRest` → con la regla "Lados" en 0 (default), **cada Aplicar borra el descanso entre lados configurado por tarjeta**.

### H4 — CONFIRMADO: código de debug en producción (working tree)
`SessionEditorViewModelStructure.kt:596-619`: tres `Log.d("RulesDebug", ...)` + fallback que, si el apply con scope no cambió nada, **aplica globalmente sin consentimiento** (pisa personalizaciones de otras partes). Debe eliminarse junto con H3 (el commit `7c9068f6` ya tuvo que limpiar debug similar una vez).

### H5 — CONFIRMADO (por diseño, sin aviso): en superseries el descanso individual no manda
En vivo, `WorkoutSetRecorder.kt:445-464` da prioridad al grupo (`roundRest* > restBetweenExercises/restAfterSuperset > restTime`). Aplicar "Descanso Normal" no tiene efecto visible en ejercicios en superset. El editor lo advierte solo en texto pequeño (`ExerciseEditorCard.kt:610-616`).

### H6 — CONFIRMADO (por diseño): modos de entrenamiento que ignoran parte de la regla
`normalizeSet` (`SessionEditorRulesEngine.kt:252-279`): en `TrainingMode.SOLO_RPE` fuerza RPE y **borra `targetReps`**; en `RM` fuerza modo LOAD. Para esos ejercicios, las reglas Reps/RIR nunca se materializan.

### Contexto sistémico (auditoría previa `reglas-y-tiempo.md`, misma carpeta)
Las "reglas" son defaults de UI que solo viven en `ruleDefaults/partRuleDefaults` hasta que Aplicar los materializa; vivo y previews leen SOLO el modelo. Subsistemas zombie: `SessionEditorRuleLimits`, `Exercise.timeStrategy`, `ExerciseSet.restBetweenSides`, ajuste global de intensidad (callback suprimido `RulesSheet.kt:192-197`).

---

## 3. Estado del working tree (SIN COMMIT — relevante para la otra auditoría)

`git status` muestra modificados: `SessionEditorRulesEngine.kt` (regresión H3), `SessionEditorViewModelStructure.kt` (logs + fallback H4), `SessionEditorScreen.kt`, `components/SessionContextNavigator.kt`, `components/SessionHero.kt` (roadmap/acentos del hero, sin relación), `gradlew`.
**Ojo:** cualquier conclusión de la otra auditoría debe distinguir código commiteado vs. estos cambios locales.

---

## 4. Confirmado vs. hipótesis

| Afirmación | Estado |
|---|---|
| Camino feliz de Aplicar funciona (variante A, scope válido, valores distintos) | CONFIRMADO estático |
| Snackbar de éxito se muestra aunque no se aplique nada | CONFIRMADO (`SessionEditorScreen.kt:771-778`) |
| Scope a parte inexistente/variante distinta → no-op | CONFIRMADO estático; falta confirmar en runtime qué caso golpeó al usuario |
| Regresión betweenSides en working tree | CONFIRMADO (diff) |
| El usuario estaba en variante B/C/D o con scope de grupo vacío | HIPÓTESIS (preguntar o reproducir con logs) |
| Supersets / SOLO_RPE explican "algunos valores sí y otros no" | HIPÓTESIS plausible según la sesión del usuario |

---

## 5. Plan de solución (propuesto, pendiente de aprobación)

**Fase 1 — Reparar regresión y limpiar debug**
- Restaurar F1 en `SessionEditorRulesEngine.kt:92`.
- Quitar `Log.d RulesDebug` y el fallback global de `SessionEditorViewModelStructure.kt:594-621`.

**Fase 2 — Feedback honesto (corazón del reporte)**
- `applyRuleDefaultsToSession` calcula el resultado ANTES de tocar estado y devuelve tipo sellado: `Applied(exercisesChanged)` / `NoChanges` / `ScopeNotFound(partId)`. Lógica de decisión en función pura testeable.
- Snackbar según resultado real: "Defaults aplicados a N ejercicios" / "Sin cambios: las tarjetas ya tienen estos valores" / "Ese grupo no existe en esta variante". Nunca más éxito falso.

**Fase 3 — Scope consciente de variante**
- Chips de grupo y lecturas del tab TIEMPO desde `uiState.activeVariantSession` (no `uiState.session`) en `RulesSheet.kt`.

**Fase 4 — Tests** (`app/src/test/.../SessionEditorRulesEngineTest.kt`)
- Anti-regresión F1 (lados preservados con regla 0); no-op con defaults idénticos; scope inexistente → ScopeNotFound; apply con cambios → Applied(n).

**Fase 5 — Validación**
- Desde `android-native/`: `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests ''*.SessionEditorRulesEngineTest''"` y luego `assembleDebug`.

**Fuera de alcance deliberado** (documentar, no cambiar sin OK del usuario): Aplicar es destructivo por diseño (reescribe todas las series y pisa pesos/reps personalizados); en superseries el descanso individual seguirá cediendo ante el grupo; subsistemas zombie (límites, timeStrategy) siguen muertos.

---

## 6. Preguntas abiertas para cruzar con la otra auditoría

1. ¿El usuario usa variantes de semana (B/C/D) o scope por grupo al aplicar? (decide si H2 es la causa raíz)
2. ¿El síntoma es "nada cambia en el editor" o "cambia en el editor pero no en vivo/tarjetas del programa"? (si es lo segundo, mirar persistencia: `weekId` en blanco salta el upsert a Room en `persistRecoverableSession` — `SessionEditorViewModel.kt:254-261`)
3. ¿La otra auditoría reproduce el no-op en variante A con scope "Toda la sesión" y valores claramente distintos? Si sí, hay otro fallo no detectado aquí y este diagnóstico está incompleto.
4. ¿Mantener el comportamiento destructivo de Aplicar o convertirlo en merge no destructivo? (decisión de producto)
