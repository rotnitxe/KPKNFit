# prefs: preferencias globales del editor de sesiones (nota de implementacion)

Solo edicion; no se ejecuto Gradle/adb/tests. Base de comparacion: `snapshot-pre-wave1`. Solo se tocaron 6 archivos, todos dentro de `screens/sessioneditor/` y su carpeta de tests.

## Que cambia

Los diez extras globales (scope, intensityType, 4 compound*, 4 isolation*) ya no se pierden al cruzar la frontera Room. Room sigue siendo la autoridad de los ocho campos core (`SessionPersistedRuleDefaults`, sin cambios, Room v28 intacto); los extras viajan en el MISMO registro de preferencias por sesion que ya llevaba partes y limites, asi que un solo `commit()` los cubre.

- `SessionEditorModels.kt`: tipo PUBLICO `SessionEditorGlobalRuleExtras` (10 campos + `version`), `extras()`, `withExtras()`, `sameCoreAs()`, `atCurrentVersion()` (internal), y `SessionEditorUiState.savedRuleExtras`.
- `SessionEditorDraftPersistence.kt`:
  - `SessionEditorRulePreferences.globalRuleExtras: ...? = null` (null = registro v1; todo registro escrito por esta build lleva no-null).
  - `SessionEditorCommittedRuleBaseline` (draft): `ruleDefaults` COMPLETO + `version`.
  - `resolveGlobalRuleDefaults(roomSession, stored, draft)` pura: overlay SOLO de extras sobre el core actual de Room; fusion de 3 vias del core para drafts con baseline (`mergeCoreThreeWay`), "el draft gana" para drafts legacy.
  - `hasMeaningfulSessionChanges()` compara solo el core contra `committedRuleDefaults()` (core de `originalSession` + `savedRuleExtras`); `hasMeaningfulDraftChanges()` suma los extras. Una edicion solo de extras cae en la ruta pref-only: sin override, sin `lastModifiedAtMs`, sin `mutateProgramNow`.
  - `RulePreferencesRead` (Absent / Unreadable / Present) y `readRulePreferencesResult`; `readRulePreferences` delega. Codec del registro con `coerceInputValues` (`sessionEditorStoreJson`): un enum desconocido degrada a default en vez de volver ilegible todo el registro.
  - `discardMustWriteRulePreferences` (F9) y dos hooks de test (`writeRaw/readRawRulePreferencesForTests`).
- `SessionEditorViewModel.kt`: `committedRuleBaseline` en `PersistedSessionEditorDraft` (se calcula en `createPersistedDraft`, nunca durante el encode en IO); `loadEditorRulePreferencesFor(..., roomSession)` devuelve tambien `global`; migracion del registro SOLO si estaba ausente (F8); `preserveEditorRuleBaselineForDiscard` y `propagateGlobalRuleExtrasToClones`; `loadSessionInternal` calcula `originalSession` antes y usa `global.current`.
- `SessionEditorViewModelNavigation.kt`: `saveSession` (S5 con `originalSession = draft`, S6 con `state.toRulePreferences()`, ack con `savedRuleExtras`, fan-out MESOCYCLE), `saveEditorRulePreferencesOnly`, `switchToSession`, `completeCreateSessionForDay` (reinicia `savedRuleExtras`), `discardAndSwitchPendingSessionAndAwait`.

## Decisiones

- Secuencia durable intacta: Room -> draft con transferencia consumida (ahora con baseline = core nuevo + extras VIEJOS) -> preferencias -> borrar draft. El ack deja `originalSession = draft`, asi que el reintento tras un fallo de preferencias es siempre pref-only (nunca otro APPEND). Revisiones de preferencias se reclaman antes del primer await, como antes.
- Descartar (F9): primero se LEE el registro (sin reclamar revision); solo si difiere del baseline se reclama revision y se reescribe. Un registro ilegible solo se reemplaza si el baseline tiene algo no-default que preservar; en el resto de casos no se toca.
- F8: registro ilegible = no se migra encima al cargar; la UI usa el baseline del draft; solo un guardado explicito lo reemplaza.
- F6 (decidido: SI propagar): `applySessionToMesocycle` ya copia el core dentro de la `Session` clonada; los extras se copian best-effort a los registros de `templateOverrideIds` DESPUES de que el registro propio es durable. Solo se reemplazan los extras (partes/limites del clon se conservan), no se reescribe un registro que ya coincide, un registro ilegible se omite y cualquier fallo se ignora. No hay reintento (el reintento pref-only no lleva alcance).
- D1 (draft de S con baseline de partes pero sin baseline global): sus extras quedan PENDIENTES. D0 (draft previo a S) sin registro: sus extras migran como confirmados, igual que partes/limites.
- Sin migracion Room, sin tocar `Session.kt`, `data/`, `SessionEditorScreen.kt`, `RulesSheet.kt`.

## Cambio a un test existente (justificado)

`SessionEditorManualOverrideTest.legacyDraftSettingsMigrateAsCommittedBaselineAndSurviveDiscard`: la migracion legacy ahora escribe un registro v2, asi que la expectativa pasa de `SessionEditorRulePreferences(parts, limits)` a `SessionEditorRulePreferences(parts, limits, SessionEditorGlobalRuleExtras())`. Es mas estricta, no mas debil (distingue v2 de v1).

## No hecho

- H1 (`contentDirty` en el draft / `resolveNewestSession`): riesgo conocido, menor (las rutas del editor SI suben `lastModifiedAtMs`).
- H3 (`RulesSheet.selectScope` para recordar PER_GROUP en el global): archivo de UI, fuera de este paquete de logica.
- F11 (limpieza de registros de preferencias al borrar sesion/programa), F10 (`addSet` ignora `intensityType`).
- Un guardado solo-extras con alcance MESOCYCLE no propaga a los clones (la propagacion vive en el guardado completo).
- Drafts: el `Json` de lectura del draft (`draftJson` del ViewModel) no usa `coerceInputValues`; solo el registro de preferencias.

## Riesgos

- Nada se ejecuto. Supuestos a confirmar al correr tests: (1) `coerceInputValues` degrada tambien un enum desconocido en propiedades `Enum?` con default null; (2) el fixture COMPLEX de `mesocycleProgram` es aceptado por `ProgramRepository.addProgramNow` y el guardado MESOCYCLE funciona en Robolectric; (3) los tests con VM reabierta tras un guardado completo no compiten con el `switchToSession` asincrono del VM anterior (se asierta sobre VMs nuevos).
- Los tests usan `setRoomCore` con `lastModifiedAtMs = ahora + 1 s` para que la copia de Room sea siempre la mas nueva frente al draft (evita empates de milisegundo).
