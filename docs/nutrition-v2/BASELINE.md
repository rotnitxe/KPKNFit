# Línea base del examen y veredicto master vs. Codex (F0, 28-oct-2026)

Primer entregable del plan: medir antes de cambiar. Examen sellado descrito en [EXAMEN.md](EXAMEN.md);
puntuador `scripts/nutrition_v2/exam/score.py` (bootstrap pareado por descripción, IC 95 %). De TEST solo
se publican agregados.

## Qué se midió

| | Casos | Menciones de oro |
|---|---|---|
| DEV (visible, para iterar) | 539 | 852 |
| TEST (sellado) | 809 | 1.299 |

- **A = master `c4491bc83`**, en un worktree limpio (decisión N16), con el catálogo de sus assets.
  Corredor: `NutritionExamPredictionRunnerTest` + `MasterExamPipeline` (Robolectric, cola Android);
  predicciones `master@c4491bc83-20261028-0220.jsonl`.
- **B = Codex `16cf3c293` + su trabajo sin commitear** (instantánea en un worktree aparte, sin tocar el
  suyo). Corredor: `CodexExamPredictionRunnerTest` + `CodexExamPipeline` (copias en
  [runners/codex/](runners/codex/)); predicciones `codex@16cf3c293+wip-20261028-0227.jsonl`.
- **Lo que se mide de Codex es su análisis automático tal como corre en la app.** Su catálogo grande
  (`catalog.sqlite`, 390 mil identidades) y sus conceptos no participan en ese camino: la app solo los usa en
  la pestaña Buscar y en «Buscar por concepto», que devuelve propuestas para revisar y nunca autoaprueba
  (`CatalogFoodRetrieval.kt`, `FoodLoggerDrawer.performSearch`). No se inventó una política para medirlo
  (decisión N20).

## Resultado de master (línea base)

| Métrica | DEV | TEST | IC 95 % TEST |
|---|---|---|---|
| **M3 Mención correcta** (principal) | 21,0 % | **20,8 %** | [18,4; 23,1] |
| M1 Identidad | 24,4 % | 25,3 % | [22,9; 27,8] |
| M2 Gramos (dada la identidad) | 65,7 % | 70,1 % | [65,1; 75,1] |
| **M4 Error silencioso** | 51,0 % | **53,1 %** | [49,8; 56,6] |
| M5 Cobertura | 49,2 % | 48,6 % | [45,2; 51,7] |
| M6 Preguntas: precisión / recobrado | 12,6 % / 52,9 % | 16,5 % / 51,2 % | |
| M7 Comida dentro de ±20 % kcal | 32,5 % | 34,4 % | [31,0; 37,6] |
| M8 Micronutrientes (núcleo completo) | 0 % | 0 % | |
| Extras por comida | 1,24 | 1,29 | |
| Preguntas por comida | 1,62 | 1,63 | |
| Latencia p50 / p95 (PC, JVM) | 133 / 746 ms | 128 / 975 ms | |

TEST por fenómeno (M3 y M5 de master):

| Fenómeno | Menciones | M3 | M5 |
|---|---|---|---|
| F01 Alimento suelto | 66 | 27,5 % | 75,8 % |
| F02 Masa o volumen explícito | 143 | 22,1 % | 51,7 % |
| F03 Conteo y tamaño | 416 | 25,9 % | 49,8 % |
| F04 Medida casera | 289 | 22,5 % | 51,9 % |
| F05 Envase y fracción | 110 | 17,5 % | 38,2 % |
| F06 Cocción / estado | 288 | 19,2 % | 49,7 % |
| F07 Presentación / forma | 129 | 19,1 % | 51,9 % |
| F08 Plato regional | 374 | 14,3 % | 36,1 % |
| F09 Composición «X con Y» | 766 | 21,3 % | 54,6 % |
| F10 Exclusión frente a atributo | 67 | 19,5 % | 37,3 % |
| F11 Marca o producto | 81 | 14,8 % | 27,2 % |
| F12 Corrección o referencia | 44 | 18,4 % | 56,8 % |
| F13 Faltas de tipeo o dictado | 143 | 20,3 % | 45,5 % |
| F14 Regionalismo | 213 | 20,7 % | 52,1 % |
| F15 Bebida o alcohol | 306 | 21,9 % | 41,5 % |
| F16 Suplemento | 33 | 27,3 % | 36,4 % |
| F17 Texto extenso o con ruido | 123 | 15,4 % | 22,0 % |
| F18 Inglés o mezcla de idiomas | 132 | 11,2 % | 32,6 % |

Por idioma (TEST): español 22,0 %, inglés 5,6 %, mezcla 28,6 % de M3.

### Qué dice la línea base

1. **El cuello de botella es la identidad, no los gramos.** Solo 1 de cada 4 menciones llega a una ficha
   aceptable (M1 25 %); cuando llega, los gramos están en tolerancia el 70 % de las veces. Confirma el
   diagnóstico del plan: el techo lo pone el vocabulario.
2. **La peor falla es frecuente:** el 53 % de las menciones que el sistema acepta sin preguntar están mal
   (identidad equivocada o gramos fuera de ±50 %). Es lo primero que v2 tiene que bajar.
3. **Inglés casi no existe** (M3 5,6 %) y los platos regionales, las marcas y los textos largos son los
   fenómenos más flojos (14–15 %).
4. **Micronutrientes: 0 %.** Ninguna mención trae el núcleo completo (fibra, azúcar, sodio y potasio).
5. Latencia medida en el PC con Robolectric: sirve para comparar sistemas, no como cifra del teléfono.

## Veredicto master vs. Codex (TEST)

**No acepta.** El análisis automático de Codex no es mejor que master: es igual.

| Métrica | master | Codex | Δ (Codex − master) | IC 95 % Δ |
|---|---|---|---|---|
| M3 Mención correcta | 20,8 % | 20,8 % | −0,0 pp | [−0,4; +0,4] |
| M4 Error silencioso | 53,1 % | 53,3 % | +0,2 pp | [−0,2; +0,6] |
| M1 Identidad | 25,3 % | 25,5 % | +0,2 pp | [−0,1; +0,6] |
| M5 Cobertura | 48,6 % | 48,9 % | +0,3 pp | [−0,4; +1,0] |
| M7 Comida ±20 % kcal | 34,4 % | 34,1 % | −0,2 pp | [−1,0; +0,5] |
| Latencia p50 / p95 | 128 / 975 ms | 92 / 383 ms | | |

- Regla de aceptación: ΔM3 con IC inferior > 0 (no: −0,4 pp) y ΔM4 con IC superior ≤ +0,5 pp
  (no: +0,6 pp). Ningún fenómeno con ≥ 30 menciones cae más de 3 pp.
- En DEV da lo mismo: ΔM3 +0,0 pp [−0,3; +0,4], ΔM4 +0,3 pp [−0,4; +0,9].
- Lo único que Codex mejora con claridad es la **velocidad** (p95 2,5 veces menor).
- **Conclusión:** los cambios de parser y resolutor de Codex no cambian lo que la app entiende; su catálogo
  grande no entra en el análisis automático. Por eso se rescatan piezas (fuentes, paquete, avisos de licencia)
  y no se fusiona la rama (decisión D6 del plan).

## Cómo reproducir

```bash
python -I scripts/nutrition_v2/exam/score.py --cases C:/kw/nutri-examen/cases/test.jsonl \
  --gold C:/kw/nutri-examen/sealed/gold-test.jsonl \
  --pred "C:/kw/nutri-examen/predictions/master@c4491bc83-20261028-0220.jsonl" \
  --pred-b "C:/kw/nutri-examen/predictions/codex@16cf3c293+wip-20261028-0227.jsonl" \
  --split test --out-dir C:/kw/nutri-examen/reports/master-vs-codex-test
```

Para DEV: `--cases .../cases/dev.jsonl --gold .../gold/dev.jsonl --split dev`. Los corredores de cada
sistema se ejecutan por la cola Android con `KPKN_EXAM_SYSTEM`, `KPKN_EXAM_CASES` y `KPKN_EXAM_OUT`
(ver los comentarios de cada corredor).
