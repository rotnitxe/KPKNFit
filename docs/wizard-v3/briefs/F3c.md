# F3c · Pro solo en la build de depuración, para grabar los vídeos de la bienvenida

Worktree `C:\kw\v3c`, rama `v3/f3c` (sale de `feat/wizard-v3`). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`.

**Decisión del usuario (2026-10-10): «Sí, solo en debug».** Para grabar los vídeos (punto 1) con comidas y los tres anillos hace
falta Pro en la app de depuración. Esto es **sensible**: la versión publicada no debe poder activar Pro por ningún camino. Sigue el
diseño al pie de la letra. Si algo no encaja, para y descríbelo en el informe.

## Estado actual (verificado)
- La fuente única de Pro de la app es `AccountGraph.entitlement` (`data/account/AccountGraph.kt`): un `StateFlow<EntitlementState>`
  público sobre `_entitlement`.
- `_entitlement` se escribe en dos sitios: `stop()` (~46, lo vuelve a `EntitlementState()`) y el colector de `attach()` (~68, el estado
  confirmado por el servidor).
- La sincronización observa `entitlements.state` (el repositorio de Firebase), NO `_entitlement`: el override no debe tocar eso.
- Solo hay dos tipos de build, `debug` y `release` (`app/build.gradle.kts`). Ya existe `src/debug` (con `DemoSeedActivity` y
  `DemoDataSeeder`). No existe `src/release`.

## Diseño (obligatorio)
1. **Mismo objeto con dos implementaciones por tipo de build**, misma firma pública:
   - `src/release/java/com/example/kpkn/data/account/BuildEntitlementOverride.kt`:
     `internal object BuildEntitlementOverride { fun apply(state: EntitlementState): EntitlementState = state }`. Nada más: ni
     setter, ni preferencias, ni extras.
   - `src/debug/java/com/example/kpkn/data/account/BuildEntitlementOverride.kt`: el mismo `apply`, que devuelve
     `state.copy(effectiveLevel = PlanLevel.PRO)` cuando el interruptor en memoria está encendido.
     - Añade `fun setForcedPro(enabled: Boolean)` con un `@Volatile` privado.
     - En memoria y **sin persistir**: al morir el proceso vuelve a Free.
2. **`AccountGraph` (cambio mínimo en `src/main`)**:
   - Guarda el último estado sin tocar (`private var rawEntitlement`) y publica siempre por una función privada
     `publish(value)`, que hace `rawEntitlement = value; _entitlement.value = BuildEntitlementOverride.apply(value)`.
   - Usa `publish` en los dos sitios que hoy escriben `_entitlement`.
   - Añade `internal fun republishEntitlement()`, que vuelve a publicar `rawEntitlement`; en release es la identidad.
   - Respeta el hilo actual: hoy se escribe con `withContext(Dispatchers.Main)`.
   - No cambies el tipo ni la naturaleza síncrona de `entitlement` (nada de `combine`/`stateIn`: hay lectores que leen `.value` al momento).
3. **`DemoSeedActivity` (`src/debug`)**: con el extra `--ez pro true`, antes de sembrar llama
   `BuildEntitlementOverride.setForcedPro(true)` y `AccountGraph.republishEntitlement()`, en el hilo principal. Sin el extra no cambia nada.
4. **Comprueba los lectores**: busca con Grep quién decide Pro (`effectiveLevel`, `PlanLevel.PRO`, `entitlement`). Confirma que
   comidas, los tres anillos y `ProgramAccessPolicy` leen de `AccountGraph.entitlement` (directa o indirectamente). Si alguno lee el
   repositorio de Firebase directamente, NO lo cambies: dilo en el informe.
5. **Docs**: en `docs/WIZARD_V3.md` §5, una línea bajo «Datos de demostración»:
   `adb shell am start -n com.example.kpkn.dbg/com.example.kpkn.debug.DemoSeedActivity --ez pro true` (Pro solo en debug y en memoria).

## Archivos permitidos
- Los dos `BuildEntitlementOverride.kt` nuevos.
- `data/account/AccountGraph.kt`: solo lo del punto 2.
- `src/debug/.../debug/DemoSeedActivity.kt`.
- Una prueba nueva en `src/testDebug/java/com/example/kpkn/data/account/`.
- `docs/WIZARD_V3.md`.

Nada más. Prohibido: `FirebaseEntitlementRepository`, `PlaySubscriptionRepository`, `MainActivity`, `ProgramRepository`, cualquier
otra cosa de `src/main`.

## Pruebas
1. Prueba JVM en `src/testDebug` (pura, sobre `BuildEntitlementOverride.apply`):
   - con el interruptor apagado devuelve el estado igual;
   - encendido devuelve PRO y conserva el resto de campos;
   - al apagarlo vuelve a FREE.
   Si puedes probar `publish`/`republishEntitlement` sin Firebase, mejor; si no, no fuerces.
2. `:app:compileDebugKotlin`, `:app:testDebugUnitTest --tests "*BuildEntitlementOverride*" --tests "*DemoDataPlan*"`.
3. **`:app:compileReleaseKotlin` una vez al final**: demuestra que la variante publicada compila con la implementación vacía.

Informe del `BRIEF_COMUN` (≤ 400 palabras). Incluye la lista de lectores de Pro que comprobaste.
