# Brief para Investigador — Inconsistencias Nutrición KPKN

**Para:** Agente investigador (Muse Spark 1.2 Free XHIGH o equivalente)
**Objetivo:** Investigar a fondo por qué el sistema de alimentación NO resuelve alimentos ni calorías aunque tiene una base gigante, y por qué pide selección manual redundante.
**No escribir código aún** — solo investigar, mapear flujo y dar diagnóstico con evidencia `file:line`.

---

## 1. Contexto Proyecto (obligatorio leer)

- App **KPKN Fit** — Android native `android-native/app/src/main/java/com/example/kpkn/`, Clean Architecture + MVVM, `domain/` puro Kotlin (sin `android.*`).
- Nutrición es flujo **local-first, offline**: `domain/nutrition/`, `data/food/`, `ExternalAiService` (fallback), catálogo importado `app-145022.jsonl` v8 con 5766 filas.
- Logs estructurados divididos por área: `app`, `workout`, `voice`, `nutrition` en `C:\Users\valen\CrossDevice\Z Flip5 de Matias (1)\storage\KPKN\KPKN\logs\` — formato JSONL `schemaVersion 2` con `sequence`, `timestamp`, `elapsedMs`, `event`, `area`, `sessionId`, `traceId`.
- Día principal auditado: **2026-08-22** (sesión completa). **2026-08-23** como contraste vacío.

---

## 2. Evidencia Concreta (ya verificada, re-verificar tú mismo)

### Archivos clave
- `nutrition/20260822/nutrition-145022.jsonl` — **26 líneas**, es el ÚNICO con análisis real. Resto `nutrition-193121,212542,001715,052333,053654.jsonl` solo tienen 1 línea `area_bootstrap` (vacíos).
- `app/20260822/app-145022.jsonl` — para health `logs_health_check` y `catalog_import`.

### Timeline exacto 2026-08-22 (traza `5172ac88`)
```
14:50:22.590 catalog_import_started datasetVersion 8
14:50:28.063 catalog_import_completed acceptedRows 5766 (5.47s) — OK
15:01:36.777 analysis_started descriptionLength 49 engine local
15:01:36.779 interpretation_started source manual
15:01:36.781 analysis_start
15:01:36.793 analysis_stage context_detect duration 2ms ok true
15:01:36.798 template_match 2ms ok
15:01:36.801 dataset_prepare 0ms ok
15:01:36.825 retrieval 20ms ok
15:01:36.971 parse 141ms ok
15:01:40.432 resolve_tags 3455ms ok  ← 94% del total
15:01:40.437 analysis_end items 2 tags 2 resolved 0 engine deterministic aiInferred 0 kcalRangeKnown false outcome completed duration 3659ms stageCount 6
15:01:40.438 analysis_finished tagCount 2 engine local
15:01:54.118 candidate_selected rank 0 manual
15:02:06.792 candidate_selected rank 0
15:02:17.359 candidate_selected rank 0
15:02:18.092 candidate_selected rank 0
15:02:18.960 candidate_selected rank 0  ← 5 selecciones en 36s, siempre rank 0
15:02:30.226 meal_saved foodCount 2 mealType BREAKFAST date 2026-08-22
15:02:30.227 save_log foodCount 2 tagCount 2 fromDescription true descriptionLength 49
```

### Qué esperaba el usuario vs qué pasó
- Usuario escribió explícitamente en `description` (49 chars) un alimento concreto, ej **"Queso Gauda"** (nota: escribe `Gauda` con typo vs `Gouda` real). Esperaba que el sistema **matchee solo** contra la DB gigante, sin preguntar.
- Realidad: `resolved 0` aunque `tags 2` y `items 2` → el resolver encontró etiquetas pero no las mapeó a filas del dataset. `kcalRangeKnown false` → ni calorías.
- Después le muestra tarjeta y le pide elegir 5 veces el mismo candidato `rank 0`, aunque ya estaba explícito en el texto. Guardado final ok (`meal_saved 2`) pero a costa de fricción manual.

### Otras pistas
- `analysis_stage` solo 6 stages; `resolve_tags` domina el tiempo (3455ms) sugiere scan sin índice o loop sobre 5766 filas sin cache.
- `engine deterministic` con `aiInferred 0` → no usó IA externa, fue 100% local y falló.
- 2026-08-23 vacío: 3 `nutrition_open` (14:59,15:00,16:28) pero solo 1 save el 22-08; el 23-08 sin análisis aunque app se abrió.

---

## 3. Qué NO es bug (correcciones del usuario)

- **Laterales 81kg NO es bug** — en máquina el peso es stack total, no mancuerna. No validar con `>50kg imposible`. Validación debe ser por implemento.
- **RPE 0↔10 NO es bug** — es decisión del usuario. El flip `10,10,0` en Press Hombros es intencional. El dual-log `rpe null` (23-08) vs `rpe 0` (22-08) es inconsistencia de schema, no de intensidad.
- **Cronómetro burst** (`rest_timer 4/1.5s` en `workout-d27e15ad-145022.jsonl:390`) es **carga batch**: usuario completó ejercicio sin registrar serie por serie y al volver cargó todo junto omitiendo rests. No es debounce aleatorio, es modo bulk que dispara timers innecesarios.
- **AUGE** debe informar **al final**, no en vivo. Muscular que quedó en 100 toda la sesión y solo bajó a 64 al final es el bug real (drenaje esperado 15-22% promedio, 60% pico según recalibración manual del usuario). No proponer alerta live.

Enfócate SOLO en nutrición.

---

## 4. Preguntas que debes responder (con evidencia file:line)

1.  **¿Por qué `resolved 0` si `tags 2`?** Mapea flujo `domain/nutrition/`: `context_detect → template_match → dataset_prepare → retrieval → parse → resolve_tags`. ¿Qué hace `ResolveTags`? ¿Qué condición hace que `resolved` quede 0 aunque haya tags? Busca umbral de score, normalización, filtrado por `kcalRangeKnown`.
2.  **¿Por qué 3455ms?** Revisa `data/food/` y DB Room `v23` (`app/schemas/`). ¿Hay índice sobre nombre/tag? ¿Se hace `LIKE %Gauda%` sobre 5766 filas sin trigram/FTS? ¿Se cachea `dataset_prepare`?
3.  **¿Por qué `kcalRangeKnown false`?** ¿De dónde sale `kcal`? ¿Dataset tiene kcal por 100g? ¿Se requiere `resolved` para calcular kcal? Traza `ExternalAiService`.
4.  **¿Por qué pide 5 veces `candidate_selected rank 0` si el texto ya es explícito?** Revisa `screens/nutrition/` y ranking: ¿Por qué siempre `rank 0`? ¿Hay normalización `Gauda→Gouda`? ¿Existe `levenshtein`/`collation` para typos? ¿La UI repide confirmación aunque `confidence > threshold`?
5.  **¿Qué pasa con alimentos explícitos vs implícitos?** Si el usuario escribe exactamente un alimento del dataset, ¿debería auto-resolver sin tarjeta? ¿Dónde está la regla `fromDescription true` en `save_log`?
6.  **¿Por qué 23-08 vacío?** ¿El análisis solo se dispara en `nutrition_open` manual? ¿Hay race con `catalog_import`?

---

## 5. Rutas autorizadas donde buscar (no inventar)

- `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/` — parsers, `AnalysisEngine`, `Interpretation`, `ResolveTags`, `Retrieval`
- `android-native/app/src/main/java/com/example/kpkn/data/food/` — `FoodRepository`, `FoodDao`, `FoodEntity`, índices, `catalogImport`
- `android-native/app/src/main/java/com/example/kpkn/data/db/` + `app/schemas/26.json` — schema v23, migraciones
- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/` — `NutritionViewModel`, `CandidateSelection`, UI tarjeta
- `android-native/app/src/main/java/com/example/kpkn/services/` — `ExternalAiService` si existe fallback
- `docs/ARCHITECTURE.md`, `.opencode/kpkn-map.md` — mapa del proyecto

---

## 6. Entregable esperado

- Lista de hallazgos con `file:line` y `event` exacto, severidad alta/media/baja.
- Diagrama de flujo real (no el ideal) del análisis nutrition con tiempos.
- Causa raíz de `resolved 0` + `kcal false` + `3455ms` + `5× rank 0`.
- Recomendación de fixes sin código (qué investigar más, qué flag activar: `nutrition`, `room` si toca migración).
- No edites, no propongas código aún, solo diagnóstico.

Usa `Read`, `Grep`, `Glob` o `bash` para verificar cada afirmación contra el repo y contra los JSONL reales en `C:\Users\valen\CrossDevice\...`. Si un dato no cuadra, dilo.
