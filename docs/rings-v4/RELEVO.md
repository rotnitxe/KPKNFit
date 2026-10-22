# Rings v4 · relevo (2026-10-22, tras llegar la cuota semanal de Claude al 94 %)

Estado congelado por orden del usuario: al 94 % de la cuota semanal «Weekly · all models» se para todo. La cuota se renueva el **27-oct-2026 ~07:00 UTC**. Hasta entonces, un agente de relevo puede seguir SOLO con ejecutores gratuitos (Gemini 3.8 Flash por `agy`, OpenCode MiMo/GLM; DeepSeek v4.1 Flash free se cuelga a menudo) y con muy poca cuota de Claude; si usa subagentes de Claude, solo Haiku 5.5 esfuerzo máximo (Sonnet únicamente si es estrictamente necesario). Respuestas al usuario SIEMPRE en español.

## Reglas fijas (no reabrir)
- Nunca editar código del repo desde el orquestador: escriben los ejecutores; el orquestador redacta briefs, informes y docs, revisa diffs y fusiona.
- Merge `--no-ff` en `feat/rings-v4` (worktree `C:\kw\r4i`) y NUNCA a `master` sin consentimiento del usuario (master sigue en `8ac57c9d4`). No tocar el árbol principal `C:\Users\valen\Documents\KPKNFit`.
- Solo tareas Gradle del flavor Base. Room NO se toca (DB v29; cualquier migración futura ≥ 32 y nueva).
- Decisiones cerradas: sin cuestionario 24 h, sin sliders AUGE en Ajustes, sin Health Connect, sin iOS/backend, el sueño no se mide. Aplazados por el usuario: backtest BT1, AUGE Pro (13 decisiones), 3.6, 4.1, 4.2, sugerencias S1–S14 (salvo «Precisión del modelo» en 3.5).
- Si el usuario no está, no hacer preguntas bloqueantes: elegir lo recomendado y anotarlo en `docs/rings-v4/DECISIONES_AUTORRESPONDIDAS.md`.
- **Sesión VT** (`uds:\\.\pipe\LOCAL\cc-msg-0967e98244b41f5c4b790ab1e61cd283`): su APK con datos reales NO debe pasar de `0c8a9a06c` hasta que se les avise por SendMessage de que 3.3 y 3.4 están integrados y verificados.
- Al terminar TODO: avisar al usuario (PushNotification + chat) y pedir consentimiento para el merge a master.

## Cola de Gradle (leer `C:\kw\tools\GRADLE_GUIA.md`)
- Dominio puro (AUGE): `bash /c/kw/tools/r4-jvm.sh <worktree> [--tests FQN]… [-Pr4GoldenUpdate=true] [-Pr4SimFull=true]` o `kpkn_jvm.sh`. Siempre con `nohup … > /c/kw/logs/<log> 2>&1 &`.
- Android (1 ranura, 15–21 min por corrida, esperas de 1–3 h): `bash /c/kw/tools/gradle_solo.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests '<clase>'…`. Estado: `bash /c/kw/tools/gradle_cola.sh`. Los agentes NO lanzan Android; lo hace el orquestador. Lista de clases de la tanda integrada: `com.example.kpkn.domain.auge.*`, `screens.auge.*`, `ui.components.rings.*`, `domain.relator.*`, `domain.sessionassistant.*`, `domain.exercises.*`, `data.onboarding.*`, `data.repository.*`, `screens.home.*`, `screens.workout.*`, `screens.onboarding.rings.*`, `domain.onboarding.*`, `screens.sessioneditor.*`, `domain.training.BlockTransitionAutoDeloadFlagTest`.
- La cola identifica la corrida por carpeta: la corrida ya encolada de `C:\kw\r4n\android-native` compila lo que haya en ese árbol cuando le toque (se puede fusionar `feat/rings-v4` ahí antes de que empiece, nunca durante).
- Python en Windows: no usar `\r`/`\a` dentro de cadenas en heredoc; escribir scripts con la herramienta de ficheros y `python -I`; imprimir con `sys.stdout.reconfigure(encoding='utf-8')`.

## Estado de los paquetes
**Integrados en `feat/rings-v4`** (HEAD `e57836618` en `C:\kw\r4i`): fases 0, 1 (salvo 1.10v2), 2.1, 2.2a/b, 2.3, 2.5, **2.6 completo** (A=D5, B=D1/D2, C=D3, D=D8; informe `reports/2.6.md`), 3.1, 3.2b (aprendiz EKF), 3.2a-1 (lectura continua), 3.2c-1 (modelo de observación real, `energyShown`), y los re-anclajes de pruebas Android de Rings (rama `r4/verify`: textos de diales, editor por D3, espera del aviso en `AugeWriteMutexTest`). Arnés JVM de la punta: ~434 pruebas, 0 fallos. Tanda Android 2: 3.459 pruebas, 48 fallos (22 heredados M0 + 26 analizados; los de Rings ya están arreglados en la punta, falta confirmarlos en la siguiente tanda).

**Ramas con trabajo hecho, SIN fusionar:**
| Paquete | Rama · worktree | Estado |
|---|---|---|
| 1.10v2 («sin datos ≠ 100 %») | `r4/1-10v2` · `C:\kw\r4n` | Arnés 437 pruebas, 0 fallos; el revisor DeepSeek leyó el diff y corrigió 3 cosas (import `lookupMuscleValue`, paquete de `RecoveryBands` en `Rings110UiTest`, título del coach). **Tanda Android 3 corriendo/terminada desde las ~23:45 en `r4n`** (`/c/kw/logs/r4-batch-3.log`). Si pasa: revisar fallos nuevos vs lista heredada y fusionar en `feat/rings-v4`. |
| 3.3 (aprendizaje manual, 1 gesto = 1 observación, transacción bienestar+caché, retirada del EMA) | `r4/3-3` · `C:\kw\r4e` (commits `ab17eafb1`…`0bbf20c6e`) | PARCIAL: parte pura verificada (arnés 464 pruebas, 0 fallos); parte Android (AugeViewModel, AugeRepository `saveWellbeingAndCache`, FinishRingEdits, WorkoutScreen, pruebas Robolectric) escrita pero NO compilada. Falta: revisión de compilación a mano (el revisor MiMo/DeepSeek no llegó a correr) y tanda Android. El último commit `0bbf20c6e` es un puente de una línea en `SystemTauRoundTripTest`; 3.2c-2 lo sustituye. |
| 3.4 (rendimiento bilateral, mediana de 3 sesiones, contexto congelado) | `r4/3-4` · `C:\kw\u1` (parte de `r4/3-3`; commits `8a283e473`…`074c364b2`) | LISTO por Gemini: arnés 481 pruebas, 0 fallos; Android sin compilar (WorkoutFinishController, AugeViewModel, `AugePerformanceLearningIntegrationTest`). Falta: revisión de compilación a mano y tanda Android. Informe `reports/3.4.md` sin escribir (escribirlo el orquestador desde `3.4-tablas.md`). |
| 3.2c-2 (adaptador EKF al simulador sintético, criterios (a)(b)(g), comparación con EMA) | `r4/3-2` · `C:\kw\r4l` | EN CURSO, sin commit: `EkfSimLearner.kt`, `AugeLearnerSimulationTest.kt` (nuevos) y cambios en `AugeRingsAudit2026Test.kt`. Hay que terminarlo con Gemini/MiMo (brief `scratchpad/agy-r4-32c2-r.md`), verificar con el arnés, escribir `reports/3.2-tablas.md`/`3.2c.md`. |
| 2.4 (AugeProjection) | `r4/2-4` · `C:\kw\v3c` | EN CURSO, 8 ficheros sin commit (WIP de Gemini). Brief en `briefs/2.4.md` + `scratchpad/agy-r4-24-r.md`. |

**Pendiente de crear:** 3.5 (métricas de habilidad, guardia/rollback, tarjeta solo lectura «Precisión del modelo»; brief `briefs/3.5.md`), 5.1 (regresión amplia + contraste con master de las pruebas ajenas), 5.2 (QA en el Flip con `.dbg`, regla de 20 min), 5.3 (docs finales: README de la ola, ARCHITECTURE/ANDROID_ARCHITECTURE_MAP).

**Contraste con master limpio** (worktree `C:\kw\sb`, desacoplado en `8ac57c9d4`, log `/c/kw/logs/r4-master-contraste.log`, en cola): decide si los fallos de `RingsResultStepTest`, `RingsIntroStepTest`, `VideoAnalysisRepositoryTest`, `Live*`, `SetExecutionCardCompactTest`, `CatalogDetailContentTest`, `ExerciseCatalogImageCoverageTest` y `RingAdjustTest` son previos a Rings v4. No relanzar el worktree `sb` hasta que corra.

## Orden recomendado para el relevo
1. Leer: este archivo, `ESTADO.md`, `DECISIONES_AUTORRESPONDIDAS.md`, `BRIEF_COMUN.md`, `BRIEF_PARALELO.md`, el plan `C:\Users\valen\.claude\plans\drifting-frolicking-penguin.md`, la memoria `project_rings_v4_wave.md`.
2. Ver el resultado de `r4-batch-3.log` (`grep -E "FAILED$|tests completed|BUILD" …`; contrastar con `reports/M0-fallos-previos.txt`); si 1.10v2 pasa, `git merge --no-ff r4/1-10v2` en `C:\kw\r4i`.
3. Terminar 3.3: revisión de compilación a mano (Haiku max o MiMo/Gemini) de `AugeRepository.kt`, `AugeViewModel.kt`, `FinishRingEdits.kt`, `WorkoutScreen.kt`, `WorkoutSessionOverlaysHost.kt` y las pruebas `AugeManualLearning*Test`; fusionar `r4/3-3` y luego `r4/3-4` (también con revisión), resolviendo el puente de `SystemTauRoundTripTest` con lo de 3.2c-2.
4. Terminar 3.2c-2 y 2.4 (Gemini, máx. 2 agentes a la vez; cuota de Gemini se agota ~2 h después de 5 agentes; no sondearla).
5. Lanzar 3.5; después una tanda Android integrada (fusionar todo en `feat/rings-v4` y luego `git merge feat/rings-v4` en `C:\kw\r4n`, o encolar `r4i`), 5.1, 5.2 y 5.3.
6. Al fusionar 3.3 + 3.4 y verificar: avisar a la sesión VT. Al terminar todo: avisar al usuario y pedir consentimiento para master.

## ACTUALIZACIÓN 00:10 (manda sobre la tabla de arriba)
- **1.10v2 FUSIONADO** en `feat/rings-v4` (`e4c133cd8`). Tanda Android 3 (`r4n`, árbol con 1.10v2 pero SIN los re-anclajes de `r4/verify`): 3 472 pruebas, 49 fallos; los 49 = los 48 de la tanda 2 + `AlbumSheetContentTest` (ajeno a Rings, probable previo/inestable). `Rings110UiTest` y `Rings110IntegrationTest` pasan; no hay fallos nuevos de Rings. Los 26 no heredados de la tanda 2 que son de Rings (AugeWriteMutexTest, SessionEditorAugeCharacterizationTest, RingsUiCopyConsistencyTest, RingAdjustTest) ya tienen arreglo fusionado (`r4/verify`) pero NO se han reconfirmado en Android.
- Los agentes Gemini (3.2c-2 en `r4l`, 2.4 en `v3c`) y el revisor de 3.3 se pararon a las 23:45 por la cuota; quedan sus árboles sucios (`r4l`: 3 ficheros; `v3c`: 8 ficheros). Revisar `git status`/`git diff` antes de seguir; no rehacer desde cero.
- La corrida de contraste con master (`sb`) sigue en cola (`/c/kw/logs/r4-master-contraste.log`).
