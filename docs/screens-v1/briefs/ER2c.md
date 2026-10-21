# ER2c · Aplicar reglas tampoco reescribe las cargas (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, con ER2a integrado). Lee antes:
- `docs/screens-v1/briefs/ER2a.md` (la regla: la carga guardada manda);
- `screens/sessioneditor/SessionEditorRmLoads.kt` (de ER2a);
- `docs/wizard-v3/BRIEF_COMUN.md`.

## El hueco
ER2a hizo que normalizar y guardar no cambien la carga de una serie RM. Hay un camino que no pasa por ahí:
- **El camino:** `SessionEditorRulesEngine.applyDefaults` (~L109) llama a su `normalizeSet` PRIVADO (~L381). Ese termina con
  `normalized.copy(weight = autoWeight ?: normalized.weight)` (~L446-447), con `calculateSuggestedLoad`, que redondea a 0,25 kg.
- **Desde dónde llega:** `evaluateApply` (~L166) y `SessionEditorViewModelStructure.kt:580`.
- **El efecto:** al aplicar reglas, un básico al 80 % de 120 vuelve a 96 kg en vez de 95.

## Qué haces
1. **Quita la copia.** El `normalizeSet` privado del motor de reglas usa la misma regla que el de `SessionEditorSessionHelpers`:
   - si la serie RM (carga externa, sin TM) tiene %RM y carga > 0, se conserva;
   - si falta, `rmSetLoadKg`.
   - Mejor aún, si las dos funciones hacen lo mismo, deja una sola y que el motor la llame.
2. **Si aplicar una regla cambia el %RM de una serie**, la carga se recalcula, redondeada con `rmSetLoadKg`. Comprueba si ya ocurre
   porque el camino pasa por `withExplicitRmLoads` en `updateSession`; si no, que pase.
3. **No cambies qué fija cada regla** (series, rangos, RIR o descansos): solo cómo se trata la carga.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*`.
- **Nuevas, que fallen sin el arreglo:**
  - aplicar reglas a una sesión del generador (sentadilla al 80 % de 120 → 95 kg) deja 95 kg;
  - una regla que cambia el %RM a 85 deja 102,5 kg.

## Permitido y prohibido
- **Permitido:** `screens/sessioneditor/SessionEditorRulesEngine.kt`, `SessionEditorSessionHelpers.kt`, `SessionEditorRmLoads.kt`,
  `SessionEditorViewModelStructure.kt` (solo si hace falta para el punto 2) y pruebas.
- **Prohibido:**
  - `domain/`;
  - `screens/workout/`;
  - la UI;
  - push;
  - el teléfono y el emulador.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" > /c/kw/logs/screens-er2c-<n>.log 2>&1`

Tope: 3 corridas.

## Commit
- `fix(sessioneditor): aplicar reglas conserva las cargas RM guardadas (ER2c)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió y dónde.
- Pruebas: número y resultado.
