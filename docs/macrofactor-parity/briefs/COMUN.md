# Brief común · Ola MFP (paridad con lo mejor de MacroFactor)

Aplica a TODOS los paquetes de la ola. Cada paquete tiene su brief propio en `docs/macrofactor-parity/briefs/<ID>.md`: **si choca con este documento, manda el del paquete**.

La ola aplica el informe `artifacts/macrofactor-reverse-engineering-20261020/INFORME_FINAL.md` (en tu worktree). Las decisiones ya tomadas están en `docs/macrofactor-parity/README.md`: no las reabras.

## Dónde trabajas
- **Worktree:** `C:\kw\mfp`, rama `feat/macrofactor-parity`, checkout parcial. Trabaja SOLO ahí y no cambies de rama.
- **Fuera de `C:\kw\mfp` solo** escribes logs en `C:\kw\logs\` y capturas en `C:\kw\shots\mfp\`.
- Hay otros worktrees de otras sesiones en marcha (`C:\kw\*`, `C:\Users\valen\Documents\KPKNFit` y su `.claude\worktrees`, `C:\Users\valen\.codex\worktrees`): **no los toques**. En particular `C:\Users\valen\.codex\worktrees\mf-improvements\KPKNFit` es trabajo previo del usuario SIN integrar: no lo modifiques jamás. Hay una copia de solo lectura en `C:\kw\ref\mf-wip` (ver «Referencia no integrada»).
- Los comandos de los briefs son de Git Bash. Si tu terminal es PowerShell, ejecútalos con `& 'C:\Program Files\Git\bin\bash.exe' -c '…'`.

## Lee primero (en este orden)
1. `CLAUDE.md`. Su cifra de Room puede estar atrasada: la base de datos es v29 (`data/db/KpknDatabase.kt`).
2. `AGENTS.md`: sus comandos de Gradle (`.opencode/scripts/...`) NO aplican aquí; usa los de este brief.
3. Tu brief `docs/macrofactor-parity/briefs/<ID>.md`.
4. Del informe, SOLO las secciones que tu brief cita (§4 son tablas de contraste con archivo:línea del HEAD de partida; algunas líneas ya se habrán movido: busca por nombre).
5. Para paquetes con pantallas: `docs/design/KPKN_UI_V2.md` y `docs/screens-v1/briefs/S_COMUN.md` (lenguaje visual KPKN v2).
6. El código que vas a tocar y sus vecinos, ANTES de editar. Imita el estilo, el nombre y la densidad de comentarios del código cercano.

## Reglas que no se rompen
- **Gradle:** SOLO por `gradle_solo.sh` (espera a que no haya otra compilación Android viva en la máquina). Formato, con la salida a un log:
  `bash /c/kw/tools/gradle_solo.sh /c/kw/mfp/android-native <tareas…> > /c/kw/logs/mfp-<ID>-<n>.log 2>&1`
  - Flavor **base** únicamente: `:app:compileBaseDebugKotlin`, `:app:testBaseDebugUnitTest --tests "<filtro>"`, `:app:assembleBaseDebug`.
  - Dale a la herramienta de shell un `timeout` ≥ 3 600 000 ms, en primer plano. La primera compilación de este worktree puede tardar 20–40 min (frío + cola); las siguientes, menos.
  - Agrupa tus ediciones antes de compilar; corre solo los tests de lo que tocas (`--tests`), no la suite entera, salvo que tu brief lo pida.
  - Gradle no imprime el total de pruebas cuando todo pasa: cuéntalas en los XML de `android-native/app/build/test-results/testBaseDebugUnitTest/`.
  - **Prohibido:** `./gradlew` o `gradle` directos, `--stop`, `clean`, un `GRADLE_USER_HOME` paralelo, `JAVA_TOOL_OPTIONS`, matar procesos `java`. Si un candado de Gradle es ajeno y no se libera, PARA y cuéntalo en el informe.
- **Git:**
  - Commits pequeños y en español (`feat(nutricion): …`, `fix(entreno): …`, `test(…): …`), cada uno con la última línea `Co-Authored-By: Codex <noreply@openai.com>`.
  - `git add` solo por rutas explícitas. Prohibidos `git add -A`, `git add .`, `stash`, `reset --hard`, `rebase`, `merge`, `push` y cambiar de rama.
  - Termina con `git status` limpio de lo tuyo (los briefs del orquestador pueden aparecer como cambios ajenos: no los toques).
- **Archivos:** solo los de la lista de tu brief. Si necesitas cambiar otro, NO lo hagas: descríbelo en el informe (qué, dónde y por qué) y sigue con lo que no dependa de él.
- **Arquitectura (CLAUDE.md):** `domain/` es Kotlin puro (sin `android.*`); Room, archivos y red van en `data/`; los ViewModels exponen `StateFlow` de solo lectura (`asStateFlow()`); lo pesado en `Dispatchers.IO`, la UI en Main; composables sin estado cuando se pueda; `val` y `data class` inmutables.
- **Texto de la app, comentarios y KDoc en español**, tono breve. Nada de nombres ni marcas de MacroFactor en el código, los textos ni los commits.
- **Refinar, no quitar:** ningún paquete elimina ni esconde una función existente. Lo que quede huérfano se conecta o se documenta en el informe; no se borra sin permiso.
- **Datos del usuario:** nunca una migración destructiva, nunca reescribir registros históricos. Cualquier cambio de esquema Room lleva su migración, el JSON de esquema exportado por KSP (`app/schemas/…`, no se edita a mano), y un test de migración.
- **Sin descargas ni librerías nuevas**, salvo que tu brief las nombre.
- **Tú no usas el teléfono ni el emulador** (prohibido `adb`): la QA con dispositivo la hace el orquestador con una variante propia (`com.example.kpkn.mfp`), sin rozar otras pruebas. Para pantallas nuevas, genera la hoja de render (PNG) como las existentes en `src/test/.../render/` (`KPKN_RENDER_DIR=C:/kw/shots/mfp/<ID>`) y critícala tú mismo contra el listón de `KPKN_UI_V2.md`.
- **Vista previa en dispositivo (solo paquetes con pantallas nuevas):** además de la hoja de render, añade en `app/src/debug/` una actividad de vista previa que aloje las pantallas nuevas con DATOS DE PRUEBA EN MEMORIA (nada se escribe en la base real salvo que el brief lo diga):
  - `app/src/debug/java/com/example/kpkn/debug/<Nombre>HarnessActivity.kt` + un bloque `<activity …/>` nuevo en `app/src/debug/AndroidManifest.xml` (solo añade; mira `WizardHarnessActivity` y las `Entreno*PreviewActivity` como patrón).
  - Debe pedir `setShowWhenLocked(true)` y `setTurnScreenOn(true)` (el teléfono suele estar bloqueado) y aceptar extras: `state` (las variantes que tu brief enumera: vacío, con datos, error…), `fontScale`, `reducedMotion` y `widthDp`.
  - Va en un commit aparte (`debug(mfp): …`) y su informe lista el nombre de la actividad y sus extras, para que el orquestador la lance con `--actions "launch:<Nombre>HarnessActivity|--es|state|full"`.
  - Código solo de depuración: nada de esto entra en `main/`.
- **Disco y procesos:** no borres nada fuera de tu worktree; si `C:` baja de 15 GB libres, para y dilo; no dejes procesos tuyos vivos.
- **Tope de intentos:** si algo sigue fallando tras 3 intentos, PARA y descríbelo en el informe con el log. No des vueltas.

## Clean-room (obligatorio)
El informe describe el comportamiento de un producto de terceros. Úsalo para entender el problema y los criterios de aceptación, NO como fuente de coeficientes:
- **Prohibido** introducir en el código, los tests o los comentarios los coeficientes ajustados que aparecen en el informe y en la evidencia: `0,965`, `0,975`, `0,582`, `0,327`, `0,5619`, `1,718`, `48,1`, `0,783`, `598`, `0,0008`, `0,0025`, `0,007`, `0,01` como constantes de ruido, `4400` (asimetría de densidad energética), `0,33`/`0,66` como rampa, `[0,4; 0,6; 0,8; 0,95]` ni las constantes de su Kalman.
- Permitido: métodos públicos y estándar (balance energético, 7700 kcal/kg ya presente en KPKN, Cunningham, EER DRI, modelos de espacio de estados estándar, regresión robusta, factor de consistencia de la MAD 1,4826, curvas de %1RM de la literatura, las que KPKN ya tiene).
- Los parámetros de tu paquete salen de TU validación (simulación con semillas fijas, tests), y los documentas con su razón. No leas `artifacts/**/evidencia/` ni nada de la carpeta temporal de la ingeniería inversa (tampoco existen en tu worktree).
- El orquestador revisa tu diff buscando esos valores.

## Referencia no integrada (solo lectura)
`C:\kw\ref\mf-wip\` contiene una COPIA del trabajo previo (18-oct, sin commits, sobre una base de hace ~800 commits y con 11 tests rojos) hecho con el informe anterior:
- `tracked-android.patch` / `untracked\...` (archivos nuevos como `TrainingVolumeWindow.kt`, `MealReuseEngine.kt`, `FoodBarcode.kt`, `NutrientCoverage.kt`, `PerformanceMetricProjection.kt`, `AugeRecoveryMapProjection.kt`) y sus tests.
- Puedes LEERLA y portar a mano una pieza pura cuando tu brief lo diga y cumpla su contrato. NO la apliques como parche, no copies lo que contradiga tu brief, y revisa cada pieza contra el código actual (la base cambió mucho: Room, `VolumeView`, `NutritionScreen`).

## Pruebas
- Espeja los paquetes de `main` en `android-native/app/src/test/java/com/example/kpkn/…` (JUnit4, Robolectric 4.12.2, `kotlinx-coroutines-test`; mira un test vecino como patrón).
- Cada comportamiento nuevo lleva un test que fallaría sin el cambio. Los criterios de aceptación del brief se convierten en tests con nombre legible.
- Una compilación verde no cierra nada: cuenta los tests ejecutados y que pasaron.

## Terminado =
Los criterios del brief en tests verdes (con su cuenta), compilación `:app:compileBaseDebugKotlin` verde, commits en `feat/macrofactor-parity` solo con tus archivos, y el informe.

## Informe final (≤ 400 palabras, español, sin pegar código)
1. Commits (hash y mensaje).
2. Archivos tocados y firmas públicas nuevas.
3. Cada corrida de Gradle: comando, log en `C:\kw\logs\mfp-<ID>-<n>.log`, línea de resultado y número de tests (de los XML).
4. Decisiones que tomaste dentro de tu margen, con su razón (parámetros y su validación incluidos).
5. Cambios que necesitas fuera de tu lista.
6. Lo que NO pudiste verificar y los riesgos.
