# Rings v4 · tablero de estado

Lo mantiene el orquestador. Última actualización: **2026-10-21 12:50**. Detalle de decisiones: `DECISIONES_AUTORRESPONDIDAS.md`. Informes por paquete: `reports/<ID>.md`. Convivencia de agentes: `BRIEF_PARALELO.md`.

## Resumen
- **Plan en alcance: 29 paquetes** (32 del plan menos 3.6 opcional, 4.1 y 4.2 que dependen de tus datos). **Integrados: 15.** En curso: 2.5 y los carriles de abajo. Pendientes tras ellos: ver «Grafo».
- Aplazado por tu decisión: backtest (BT1). Fuera de esta entrega: AUGE Pro (13 decisiones tuyas pendientes), 3.6, 4.2, sugerencias S1–S14 salvo «Precisión del modelo» (en 3.5).

## Carriles en marcha
| Carril | Paquete | Agente / modelo | Worktree · rama | Estado |
|---|---|---|---|---|
| A | 2.5 anclas como estado + evidencia multiplicativa | Codex Sol 6.1 · esfuerzo máximo | `v3c` · `r4/2-5` | etapa A en validación Android; etapa B pendiente |
| B | 3.1 caché v4, migración, anillo de observaciones | Codex Sol 6.1 · medio | `sb` · `r4/3-1` | por lanzar |
| C | 1.9v2 textos y umbrales | Codex Luna · máximo | `u1` · `r4/1-9v2` | por lanzar |
| D | 1.11 creador sin Core por defecto | Antigravity · Gemini 3.8 Flash High | `r4s` · `r4/1-11` | por lanzar |
| T | JVM1 arnés JVM para pruebas puras de AUGE (herramienta, fuera del repo) | Codex Sol 6.1 · máximo | `C:\kw\r4-jvm` | validando contra el oráculo Android |

## Grafo de lo que falta
- 2.5 → **2.6** (cambios de forma; regenera el máster dorado) → 2.4 (proyección; mide sobre la forma final).
- 3.1 → **3.2** (aprendiz EKF) → 3.3 (gesto manual) ∥ 3.4 (rendimiento) → **3.5** (habilidad, guardia, tarjeta «Precisión del modelo»).
- 1.9v2 → 1.10v2 (mismo carril: tocan los mismos archivos de Home/readiness).
- Cierre: 5.1 regresión amplia (incluye contraste con `master` de `RingsResultStepTest` y `VideoAnalysisRepositoryTest`) → 5.2 QA en el Flip → 5.3 documentación.

## Integrados en `feat/rings-v4`
Fase 0: 0.1, 0.2, 0.3 · Fase 1: 1.1, 1.2a, 1.2b, 1.3, 1.4, 1.5, 1.5b, 1.6, 1.7, 1.8, 1.12a, 1.12b, 1.12c · M0 (master fusionado) · Fase 2: 2.1, 2.2a, 2.2b1, 2.2b2, 2.3.

## Verificaciones
- `r4-rev-23m` (árbol con 2.3): 513 clases / 4 833 pruebas / 2 fallos ajenos a AUGE (`VideoAnalysisRepositoryTest` solo falla en la corrida conjunta; `RingsResultStepTest` pendiente de contrastar con `master`).
