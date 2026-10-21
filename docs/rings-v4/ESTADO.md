# Rings v4 · tablero de estado

Lo mantiene el orquestador. Última actualización: **2026-10-21 16:10**. Detalle de decisiones: `DECISIONES_AUTORRESPONDIDAS.md`. Informes por paquete: `reports/<ID>.md`. Convivencia de agentes: `BRIEF_PARALELO.md`.

## AVISO · Codex y Antigravity sin cuota
- **Codex** (21-oct 13:38): sin cuota hasta el **27 de octubre a las 12:38**.
- **Antigravity / Gemini** (21-oct 15:35): «Individual quota reached», cuota de toda la cuenta agotada por cinco agentes en paralelo; renovación estimada entre las 17:36 y las 18:10 (el plazo se mueve). Los cinco agentes Gemini murieron (2.5b, 3.1, 1.9v2, 1.10v2, 3.2b) y **al morir un agente se llevan por delante sus tareas de fondo** (la corrida Android de 1.9v2 en cola y la corrida completa del arnés de 3.1 se perdieron). Su trabajo quedó en cada worktree.
- Mientras tanto cierran con **OpenCode DeepSeek v4.1 Flash (gratis)** los pasos mecánicos de 2.5, 3.1 y 3.2b; el resto espera a Gemini (máx. 2 agentes a la vez cuando vuelva) o a una decisión tuya sobre ejecutores Claude. La máquina está además al límite de RAM (otras sesiones con Android): las colas de Gradle y del arnés se mueven despacio.

## Política de verificación (desde las 14:50)
La cola compartida de Gradle (otras 10 sesiones) lleva horas de espera, así que **el arnés JVM es la compuerta principal** y Android pasa a tandas mías:
- El arnés ya regenera el máster dorado (`-Pr4GoldenUpdate=true`: escribe solo en `android-native/app/build/auge-golden/`) y corre la simulación completa (`-Pr4SimFull=true`). Comprobado en `feat/rings-v4`: el archivo regenerado es **idéntico, byte a byte**, al que produjo Android.
- Los agentes **no lanzan corridas Android** salvo las que ya tenían en cola (3.1: la de Codex `r4-3-1-1.log`; 1.9v2: `r4-1-9-4.log`); terminan con arnés verde + informe y listan las clases que el arnés no cubre.
- Yo reviso el diff, fusiono en `feat/rings-v4` y lanzo UNA tanda Android (`r4-verify.sh`) con todo lo que el arnés no cubre (Robolectric, `screens/*`, `AugeSnapshotCoverageTest`, `AugeWriteMutexTest`, 1.11…). Los paquetes siguientes parten de lo ya fusionado, sin esperar esa tanda.
- Hallazgo de la revisión de 3.1 (14:55): el nuevo códec JSON del caché había perdido `coerceInputValues`, que sí tenía el decodificador original; se devuelve al agente para corregirlo antes de la corrida Android.

## Resumen
- **Plan en alcance: 29 paquetes** (32 del plan menos 3.6 opcional, 4.1 y 4.2 que dependen de tus datos). **Integrados: 16** (1.11 pendiente de verificar en Android). En curso: 2.5 y los carriles de abajo. Pendientes tras ellos: ver «Grafo».
- Aplazado por tu decisión: backtest (BT1). Fuera de esta entrega: AUGE Pro (13 decisiones tuyas pendientes), 3.6, 4.2, sugerencias S1–S14 salvo «Precisión del modelo» (en 3.5).

## Carriles en marcha
| Carril | Paquete | Agente / modelo | Worktree · rama | Estado |
|---|---|---|---|---|
| A | 2.5 anclas como estado + evidencia multiplicativa | DeepSeek v4.1 Flash cierra (antes Gemini; antes Codex) | `v3c` · `r4/2-5` | etapa A commiteada; etapa B escrita; máster regenerado con el arnés (solo cambia D04 a 6/12/24 h); DeepSeek copia el máster, commitea, fusiona `feat/rings-v4` y corre el arnés completo (en cola). El informe lo escribo yo |
| B | 3.1 caché v4, migración, anillo de observaciones | DeepSeek cierra (antes Gemini) | `sb` · `r4/3-1` | 8 commits; ajuste `coerceInputValues` hecho; fusión de `feat/rings-v4` hecha por DeepSeek (un conflicto, resuelto como pedí); falta el arnés completo; la corrida Android de Codex compiló TODA la producción y solo falló en 2 imports de una prueba (corregidos) |
| C | 1.9v2 textos y umbrales | Gemini (murió sin informe) | `u1` · `r4/1-9v2` | **fusionado en `feat/rings-v4` (`0c8a9a06c`)**; informe `reports/1.9.md` escrito por mí; su corrida Android se perdió: va en la tanda consolidada |
| D2 | 1.10v2 «sin datos» no es 100 % | Gemini (murió) | `r4n` · `r4/1-10v2` (apilada sobre 1.9v2) | 1 commit (estado sin datos en el snapshot y titular por canal limitante); espera a Gemini (trabajo de interfaz con compilación solo en Android) |
| F | 3.2b núcleo EKF + oráculo independiente | DeepSeek cierra (antes Gemini) | `r4l` · `r4/3-2` (apilada sobre `r4/3-1`) | `AugeLearner.kt` y pruebas escritos (revisados a ojo: siguen la especificación); falta correr los vectores del oráculo y el arnés completo (en cola) |
| D | 1.11 creador sin Core por defecto | Antigravity · Gemini 3.8 Flash High | `r4s` · `r4/1-11` | **integrado 13:30** (diff de 7 líneas + prueba nueva); su corrida Android murió con el agente: se verificará en la próxima tanda |
| E | DOC1 README de la auditoría y arquitectura (solo documentación) | OpenCode · DeepSeek v4.1 Flash (gratis) | `r4d` · `r4/5-3a` | **integrado 12:49** (README de la auditoría + arquitectura) |
| T | JVM1 arnés JVM para pruebas puras de AUGE (herramienta, fuera del repo) | Codex Sol 6.1 · máximo | `C:\kw\r4-jvm` | **listo 13:28**: 70 clases / 365 pruebas de AUGE, 0 discrepancias con Android; compilación en frío 202 s, caliente 1,5 s, completo 83 s, una clase 17 s; un arnés por worktree (`C:\kw\r4-jvm-<wt>`); no cubre `screens/`, `data/repository/`, Robolectric/Compose/Room ni 3 clases (`AugeEditorCompletedParityTest`, `AugeSnapshotComputerTest`, `BatteryRoundingTest`) |

Cola de lanzamiento (cuando se integre lo anterior): 3.2 (tras 3.1) → 3.3 → 3.4 → 3.5 en el carril del aprendiz; 2.6 y luego 2.4 (tras 2.5) en el carril del motor; 1.10v2 (tras 1.9v2) en el carril de interfaz; 5.1–5.3 al final.

## Grafo de lo que falta
- 2.5 → **2.6** (cambios de forma; regenera el máster dorado) → 2.4 (proyección; mide sobre la forma final).
- 3.1 → **3.2** (aprendiz EKF) → 3.3 (gesto manual) ∥ 3.4 (rendimiento) → **3.5** (habilidad, guardia, tarjeta «Precisión del modelo»).
- 1.9v2 → 1.10v2 (mismo carril: tocan los mismos archivos de Home/readiness).
- Cierre: 5.1 regresión amplia (incluye contraste con `master` de `RingsResultStepTest` y `VideoAnalysisRepositoryTest`) → 5.2 QA en el Flip → 5.3 documentación.

## Integrados en `feat/rings-v4`
Fase 0: 0.1, 0.2, 0.3 · Fase 1: 1.1, 1.2a, 1.2b, 1.3, 1.4, 1.5, 1.5b, 1.6, 1.7, 1.8, 1.12a, 1.12b, 1.12c · M0 (master fusionado) · Fase 2: 2.1, 2.2a, 2.2b1, 2.2b2, 2.3 · 1.11 (13:30, verificación pendiente) · DOC1 (índice de la auditoría y arquitectura, parte de 5.3).

## Verificaciones
- `r4-rev-23m` (árbol con 2.3): 513 clases / 4 833 pruebas / 2 fallos ajenos a AUGE (`VideoAnalysisRepositoryTest` solo falla en la corrida conjunta; `RingsResultStepTest` pendiente de contrastar con `master`).
