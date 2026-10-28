# Plan de la noche (28-oct) — qué falta y cómo retomarlo

Estado vivo: memoria `project_nutrition_v2_wave.md` (bloque «RETOMAR AQUÍ») y `DECISIONES_NOCHE.md`.

## Pasos que siguen, en orden

1. **F1b, cierre**: cuando terminen los agentes de etapa 3 (C02a/b, C05, C08, C09, C10, C11, C13, C16a/b, C17),
   escribir `C:\kw\nutri-build\concepts\curation.json` (drop C15 `arroz_con_leche` → C06
   `arroz_con_leche_postre`; dropVariants de C12 con forma LIQUID que significa «con azúcar»: revisar con
   `notes`) y correr:
   `python -I scripts/nutrition_v2/concepts/merge.py --in-dir C:/kw/nutri-build/concepts/stage3 --out C:/kw/nutri-build/concepts/concepts.jsonl --ambiguous C:/kw/nutri-build/concepts/ambiguous.jsonl --report C:/kw/nutri-build/concepts/report.json --curation C:/kw/nutri-build/concepts/curation.json`
   y `export.py` (completo → `C:/kw/nutri-build/concepts_export.json`; `--macros-only --gzip` →
   `concepts_app.json.gz`). Revisión cruzada del 10 % por lote: un agente Sonnet con una muestra.
2. **F2-d** (cuando F2-b y F2-c informen): un agente Sonnet high escribe `NutritionParserV2` (fachada:
   `NormalizerV2.tokenize` → `buildLexiconV2(catalog).tag` → `GrammarV2.parse` → `ResolverV2.resolve`),
   `NutritionParserV2Test` y el corredor JVM `domain/nutrition/v2/exam/NutritionExamV2RunnerTest.kt` que
   reutiliza `domain/nutrition/exam/ExamPredictionModels.kt` (incluirlo en `-PtestInclude`; el resto de
   `exam/` no compila en JVM). Ajustes: `KPKN_EXAM_CASES` (por defecto un recurso de prueba),
   `KPKN_V2_EXPORT` (`C:/kw/nutri-build/concepts_export.json`), `KPKN_EXAM_OUT`, `KPKN_EXAM_SYSTEM`
   (`v2@<generation>`). Mapeo a `ExamMention`: foodName = nombre del concepto, foodId = ficha, source por
   prefijo (usda/ciqual/off/kpkn), heuristic = sin ficha, state = nombre o UNKNOWN, questions = dimensiones,
   nutrientsPresent con los nombres de `score.py` (`fiber`, `sugar`, `sodium`, `potassium`).
   Iteración SOLO con `C:/kw/nutri-examen/cases/dev.jsonl` y `gold/dev.jsonl`; prohibido `cases/test.jsonl`,
   `cases/all.jsonl` y `sealed/`. Puntuar: `python -I scripts/nutrition_v2/exam/score.py` (ver su ayuda).
3. **F4**: recibir F4-A2 (pedirle la regla F4-9) y F4-B; luego F4-C (hoja de nutrientes agrupada).
4. **F0**: S1 (master) termina su corrida de muestra → corrida completa (DEV+TEST) con
   `KPKN_EXAM_SYSTEM=master@c4491bc83`; Codex en cola (`nv2-codex-run1.log`); `BASELINE.md` y veredicto.
5. **F5**: asset `concepts_app.json.gz` + paquete en el APK (una vez), modo sombra v2 en el logger con
   interruptor en ajustes (apagado por defecto), pantalla «Fuentes y licencias», APK de QA con sufijo
   propio en EMULADOR.
6. **Final**: TEST de v2 (yo, solo agregados), informe con IC, merge a master, commit y push.
