# Rings v4 · tablero de estado

Lo mantiene el orquestador. Última actualización: **2026-10-21 13:48**. Detalle de decisiones: `DECISIONES_AUTORRESPONDIDAS.md`. Informes por paquete: `reports/<ID>.md`. Convivencia de agentes: `BRIEF_PARALELO.md`.

## AVISO · Codex sin cuota (21-oct 13:38)
La cuenta de Codex llegó a su límite de uso y no vuelve hasta el **27 de octubre a las 12:38**. Los tres agentes Codex en curso (2.5, 3.1, 1.9v2) se detuvieron a mitad con su trabajo guardado en cada worktree; los continúa Antigravity Gemini 3.8 Flash High (vía autorizada por el usuario), con el arnés JVM para iterar y UNA corrida Android oficial al final. Para lo más delicado (aprendiz EKF 3.2, 2.6, 2.4, 3.3–3.5) queda pendiente la decisión del usuario sobre el ejecutor; mientras tanto se sigue con Gemini/DeepSeek y revisión estricta.

## Resumen
- **Plan en alcance: 29 paquetes** (32 del plan menos 3.6 opcional, 4.1 y 4.2 que dependen de tus datos). **Integrados: 16** (1.11 pendiente de verificar en Android). En curso: 2.5 y los carriles de abajo. Pendientes tras ellos: ver «Grafo».
- Aplazado por tu decisión: backtest (BT1). Fuera de esta entrega: AUGE Pro (13 decisiones tuyas pendientes), 3.6, 4.2, sugerencias S1–S14 salvo «Precisión del modelo» (en 3.5).

## Carriles en marcha
| Carril | Paquete | Agente / modelo | Worktree · rama | Estado |
|---|---|---|---|---|
| A | 2.5 anclas como estado + evidencia multiplicativa | Gemini 3.8 Flash High (continúa lo de Codex Sol) | `v3c` · `r4/2-5` | etapa A commiteada y validada; etapa B escrita, sin commit: en verificación (relevo 13:46) |
| B | 3.1 caché v4, migración, anillo de observaciones | Gemini 3.8 Flash High (continúa lo de Codex Sol) | `sb` · `r4/3-1` | producción commiteada, pruebas sin commit: en verificación (relevo 13:46) |
| C | 1.9v2 textos y umbrales | Gemini 3.8 Flash High (continúa lo de Codex Luna) | `u1` · `r4/1-9v2` | 17 archivos de producción sin commit: en revisión (relevo 13:46) |
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
