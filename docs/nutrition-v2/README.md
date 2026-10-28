# Nutrición v2 — ola de comprensión, catálogo en español y micronutrientes

Plan aprobado el 2026-10-27. Rama `claude/nutrition-search-nutrients-improvements-46b6dc` (desde master
`c4491bc83`). Este README es el documento vivo: estado, decisiones y registro de corridas del examen.

## Qué se hace y en qué orden

**F0 → F1a → F4 → F1b → F2 → F3 → F5.** Cada fase se acepta solo con el examen (`EXAMEN.md`).

| Fase | Qué | Estado |
|---|---|---|
| F0 | Examen sellado + arnés A/B; línea base de master; veredicto master vs. Codex | en curso |
| F1a | Paquete de catálogo v0 (SQLite de solo lectura) con vector completo de nutrientes | pendiente |
| F4 | Micronutrientes de punta a punta (sin cambio de esquema Room) | pendiente |
| F1b | Conceptos y alias en español por lotes («IA en la fábrica») | pendiente |
| F2 | Parser v2 por léxico + gramática de enlaces, en sombra | pendiente |
| F3 | Cantidad, estado y presentación (reconstitución) explícitos | pendiente |
| F5 | Integración, rendimiento, teléfono, fuentes y licencias | pendiente |

## Decisiones

| # | Decisión |
|---|---|
| D1 | IA solo offline (al construir catálogo y examen), nunca en tiempo de ejecución. |
| D2 | Catálogo como SQLite de solo lectura fuera de Room; puntero en `nutrition_catalog_publication` (Room 36, ya en master); una sola generación viva. |
| D3 | USDA Branded fuera del APK salvo evidencia del examen. |
| D4 | Parser v2 en sombra; v1 de respaldo; cambio de predeterminado solo con la regla A/B y visto bueno del usuario. |
| D5 | es-CL como región principal; sinónimos AR/MX/PE/CO/ES. |
| D6 | Del trabajo de Codex (`codex/nutrition-local-ai-20261024`, sin commitear) se copian piezas con atribución; su worktree no se toca. |
| D7 | El historial nunca se recalcula (contrato v2). |
| D8 | Solo subagentes de Claude Code, Sonnet con esfuerzo medio/alto (orden del usuario del 2026-10-27): escriben el código **y** generan/etiquetan el examen. Nada externo (OpenCode, agy, Codex). Independencia del oro: los agentes que generan y etiquetan no ven código ni catálogo; TEST lleva doble etiquetado ciego con encuadres distintos + adjudicación. |
| D9 | Predicciones de master en el carril Android de tests unitarios (el importador real depende de Android); puntuación en Python sin Gradle. |

## Piezas de F0

| Pieza | Ruta | Estado |
|---|---|---|
| Contrato del examen | `docs/nutrition-v2/EXAMEN.md` | hecho |
| Lectores de fuentes (copiados de Codex) | `scripts/nutrition_v2/sources/` | hecho (`a6dd982fc`) |
| Índice de referencias (16.541 fichas) | `scripts/nutrition_v2/exam/refindex.py` | hecho (`3214b91a5`) |
| Generación de textos | 8 agentes Sonnet → `C:\kw\nutri-examen\raw\G1…G8.jsonl` (guía en `spec\GENERACION.md`) | hecho: 1.154 textos; por fenómeno exacto; estilo `telefono` 47 % (meta 40 %), SNACK 22 % (meta 15 %) |
| Puntuador + bootstrap | `scripts/nutrition_v2/exam/score.py` | en curso |
| Curación, oro y acuerdo | `scripts/nutrition_v2/exam/curate.py`, `gold_build.py`, `agree.py` | en curso |
| Etiquetado | agentes Sonnet con `spec\ETIQUETADO.md` (convenciones de porciones) | pendiente |
| Predicciones de master | `android-native/app/src/test/java/com/example/kpkn/domain/nutrition/exam/` | en curso |

## Registro de corridas de TEST

| Fecha | Sistema | Commit | M3 | M4 | M7 | Nota |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |
