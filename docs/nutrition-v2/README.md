# Nutrición v2 — ola de comprensión, catálogo en español y micronutrientes

Plan aprobado el 2026-10-27. Rama `claude/nutrition-search-nutrients-improvements-46b6dc` (desde master
`c4491bc83`). Este README es el documento vivo: estado, decisiones y registro de corridas del examen.

## Qué se hace y en qué orden

**F0 → F1a → F4 → F1b → F2 → F3 → F5.** Cada fase se acepta solo con el examen (`EXAMEN.md`).

**Resultado final: [RESULTADO_V2.md](RESULTADO_V2.md)** (TEST sellado: M3 20,8 % → 49,1 %, M4 53,1 % → 24,6 %,
ACEPTA). Decisiones de la noche del 28-oct: [DECISIONES_NOCHE.md](DECISIONES_NOCHE.md).

| Fase | Qué | Estado |
|---|---|---|
| F0 | Examen sellado + arnés A/B; línea base de master; veredicto master vs. Codex | hecho (`3126937de`, [BASELINE.md](BASELINE.md)): Codex empata con master |
| F1a | Paquete de catálogo v0 (SQLite de solo lectura) con vector completo de nutrientes | hecho: paquete (`e0e53e842`), lector y publicador en Android (F4-B `a401eafc7`); el APK lo lleva sin `.gz` (AGP descomprime los assets) |
| F4 | Micronutrientes de punta a punta (sin cambio de esquema Room) | hecho: 33 nutrientes, «sin dato» ≠ 0, DRI de embarazo y lactancia, topes de guía (F4-A1/A2), instantánea al guardar (F4-B), hoja agrupada (F4-C) |
| F1b | Conceptos y alias en español por lotes («IA en la fábrica») | hecho: 1.652 conceptos, 22.425 alias (es/en/faltas), 2.068 fichas; rondas de datos R1-D (`b4cc06fe6`) y R2-D (`f6de40a13`) |
| F2 | Parser v2 por léxico + gramática de enlaces, en sombra | hecho (`fc6d7c94b` + ronda 1 `fa14c0558`, `c5edc22d9`) |
| F3 | Cantidad, estado y presentación (reconstitución) explícitos | hecho en el resolutor (fracciones, envases, reconstitución, estado y forma por defecto, preguntas materiales); los chips nuevos en la tarjeta quedan pendientes |
| F5 | Integración, rendimiento, teléfono, fuentes y licencias | hecho: adaptador con la misma compuerta (`c5348ee10`), Fuentes y licencias, assets; la QA en el emulador encontró que el paquete no se publicaba en el dispositivo (arreglado, N33); QA en el Flip5 con arreglos N35–N44; desde el 28-oct v2 es el sistema, sin interruptor (N45), y «Buscar» usa v2 y el paquete (N46–N48, sin compilar todavía) |

## Decisiones

| # | Decisión |
|---|---|
| D1 | IA solo offline (al construir catálogo y examen), nunca en tiempo de ejecución. |
| D2 | Catálogo como SQLite de solo lectura fuera de Room; puntero en `nutrition_catalog_publication` (Room 36, ya en master); una sola generación viva. |
| D3 | USDA Branded fuera del APK salvo evidencia del examen. |
| D4 | Parser v2 en sombra; v1 de respaldo; cambio de predeterminado solo con la regla A/B y visto bueno del usuario. **Cumplida el 28-oct:** v2 ganó en TEST y el usuario decidió que es el sistema (N45); v1 queda solo como respaldo interno. |
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
| Puntuador + bootstrap | `scripts/nutrition_v2/exam/score.py` | hecho (`793660b0d`) |
| Curación, oro y acuerdo | `scripts/nutrition_v2/exam/curate.py`, `gold_build.py`, `agree.py` | hecho (`dcc1d445c`); curación real: 1.000 casos (DEV 400 / TEST 600), 146 de reserva, 8 duplicados |
| Módulo B (pedido del 27-oct) | 6 agentes Sonnet → `raw\B1…B6.jsonl` (`spec\GENERACION_B.md`); curación en anexo (`caf997baf`) | hecho: 408 generados → 350 casos E1001–E1350 (DEV 140 / TEST 210; 92 en inglés, 18 spanglish); los 1.000 anteriores intactos |
| Etiquetado | agentes Sonnet con `spec\ETIQUETADO.md` (criterios unificados v1.1) y `spec\TAREA_ETIQUETADOR.md`; lotes `work\batches\D01–D16`, `T01–T24`, `DB01–DB06`, `TB01–TB09` | TEST: L1 y L2 completos salvo TB07–TB09; DEV (solo L1): D01–D16 y DB01–DB02 hechos, DB03–DB06 en curso. 0 errores de validación |
| Acuerdo v2 | `scripts/nutrition_v2/exam/agree.py`: tolerancia de la clase sobre el promedio, plato ≡ componentes, oro = promedio | hecho; sobre TEST A + TB01–TB06: 70 % de casos y 77 % de menciones concuerdan (antes 24–28 % de casos con ±15 % de kcal) |
| Adjudicación | `spec\ADJUDICACION.md` (convenciones v1.2: 29 decisiones sobre las dudas de los etiquetadores; la otra lectura razonable va a `equivalents`) → `labels\ADJ\` | hecho: 9 agentes Sonnet, 262 casos, 0 errores de validación |
| Oro sellado | `sealed\gold-test.jsonl` (+ `MANIFEST.txt` con sha256) y `gold\dev.jsonl` | TEST: 809 casos (A 599 / B 210; 1 descartado), 1.303 menciones (211 con ambigüedad, 207 con equivalentes), 547 por acuerdo + 262 adjudicados. DEV: 539 casos, 855 menciones (un etiquetador). Límite conocido: un plato sin ficha (`refId: null`) no puede llevar lectura equivalente por componente |
| Predicciones de master | `android-native/app/src/test/java/com/example/kpkn/domain/nutrition/exam/` | código listo; la verificación en Gradle Android falló por memoria del compilador (4 GiB) y un comentario sin cerrar: se repite con `-Xmx6g` |

## Piezas de F1a

| Pieza | Ruta | Estado |
|---|---|---|
| Contrato del paquete | `docs/nutrition-v2/F1A_PAQUETE.md` | hecho (`da6574cf4`) |
| Constructor del paquete | `scripts/nutrition_v2/catalog/build_pack.py` | hecho (`e0e53e842`): 31,5 MB, 38 nutrientes, FTS4 sobre alias normalizados |

## Registro de corridas de TEST

| Fecha | Sistema | Commit | M3 | M4 | M7 | Nota |
|---|---|---|---|---|---|---|
| 2026-10-28 02:20 | master | `c4491bc83` | 20,8 % | 53,1 % | 34,4 % | línea base (BASELINE.md) |
| 2026-10-28 02:27 | Codex + trabajo sin commitear | `16cf3c293` | 20,8 % | 53,3 % | 34,1 % | NO ACEPTA (empate) |
| 2026-10-28 05:46 | **v2** (vocabulario `b43cc853…`) | `f6de40a13` | **49,1 %** | **24,6 %** | 49,1 % | **ACEPTA**: ΔM3 +28,3 pp [+25,4; +31,6], ΔM4 −28,6 pp [−32,8; −24,2]; única corrida de v2 en TEST |
