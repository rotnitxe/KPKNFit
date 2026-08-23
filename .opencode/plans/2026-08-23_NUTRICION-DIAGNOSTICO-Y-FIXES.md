# Nutrición KPKN — Diagnóstico + Fixes (sistémico: hallulla/marraqueta/fideos en rojo)

**Fecha:** 2026-08-23  
**Logs:** `C:\Users\valen\CrossDevice\Z Flip5 de Matias (1)\storage\KPKN\KPKN\logs\nutrition\20260822\nutrition-145022.jsonl` + `app` + `voice/workout` correlacionados  
**Investigación:** 3 subagentes LUNA MAX + auditoría previa Muse Spark 1.2 Free XHIGH (4 paralelos)  
**Implementación:** Fixes aplicados en esta sesión, verificados con `assembleDebug BUILD SUCCESSFUL`  
**Flags:** `[nutrition]` (añadir `room` solo si se cambia FTS/índices/tablas — aquí se añadió índice exacto en memoria, no migración)

---

## Parte 1 — Diagnóstico entregado por el agente investigador (copia literal + referencias)

> **Resumen:** La base no está vacía: catálogo v8 terminó correctamente y Room reportó 5.766 filas. Causa principal: `resolved` cuenta únicamente alimentos autoaprobados, no candidatos encontrados. El motor puede hallar una fila con macros y aun marcarla `NEEDS_CONFIRMATION`, dejándola fuera de `resolved`.

**Flujo real observado**
```
Importación v8 ── 5.473 ms ──> Room: 5.766 filas
                         11 min después
Descripción de 49 caracteres
  ├─ context_detect ........ 2 ms
  ├─ template_match ........ 2 ms
  ├─ dataset_prepare ....... 0 ms
  ├─ retrieval ............ 20 ms
  ├─ parse ................ 141 ms
  └─ resolve_tags ....... 3.455 ms   ← 94,4% del análisis
                           └─ 2 ítems / 2 tags / 0 AUTO
                              kcalRangeKnown=false
                              duración total: 3.659 ms
5 acciones manuales → guardado de 2 alimentos
```

**Traza:** `nutrition-145022.jsonl:4` (línea 4), resultado `línea 17`, selecciones desde `línea 19`.

**Respuestas a las seis preguntas (resumen con `file:line`):**

1. **Alta — tags=2 pero resolved=0** — `isResolved` solo `true` cuando `resolutionStatus == AUTO` (`TagResolution.kt:335`). Un candidato OFF/USDA con macros puede quedar `NEEDS_CONFIRMATION`. Para autoaprobar exige coincidencia estática exacta, variante preparada segura o `Smart AUTO_SELECT` con fuente `LOCAL`. Fila OFF/USDA no obtiene autoridad aunque puntúe alto. Sobre Gauda/Gouda: OFF contiene varias filas “Gauda” y “Gouda” (6 pasan validación), no hay equivalencia léxica en `TextNormalizer.kt:90`, y scores empatados a 1.0 incumplen margen `0.16` en `SmartFoodResolver.kt:396` → fuerza revisión. Inferencia fuerte, no demostrada por run: telemetría no registró IDs/scores.

2. **Alta — resolve_tags 3.455 ms** — 94,4% engloba toda resolución de ambos tags. Puede combinar ranking difuso, lectura completa `global_foods` para índice, `exactMatches()` filtrando en memoria, hasta 4 búsquedas DAO, `LIKE '%consulta%'` sin índice B-tree, FTS4 limitada a nombre/marca. Puntos en `NutritionRepository.kt:208`, `FoodIndex.kt:127`, `Daos.kt:321`. No atribuible a una operación: solo cronómetro agregado, sin subspans. `dataset_prepare=0 ms` descarta preparación semántica.

3. **Media — kcalRangeKnown=false** — No es “Room sin kcal”. Es indicador independiente del rango energético semántico (`FoodLoggerDrawer.kt:488` exige `kcalMin>0, kcalMax>kcalMin, confianza 0.35, ancho 30 kcal`). Puede coexistir candidato con macros + `isResolved=false` + `kcal false`. Logs no incluyen kcal finales guardadas. No existe `ExternalAiService` activo; análisis fue `local/deterministic aiInferred=0` (`ARCHITECTURE.md:36`).

4. **Alta — cinco candidate_selected rank=0** — `rank=0` hardcodeado en `FoodLoggerDrawer.kt:649`. Los 5 eventos prueban 5 invocaciones manuales, no prueban mismo alimento/posición/tag/análisis. Omite `tag, foodId, score, margen, trace`. UI y callback padre duplican `recordFoodSelection`.

5. **Alta — explícitos vs implícitos** — Cantidad explícita fija gramos, no vuelve autoritativa la identidad. `“100 g de queso gauda”` puede conservar gramos y exigir confirmación. `fromDescription=true` solo significa `lastAnalyzedDescription` no vacío, no alta confianza. Deseable: autoaprobar identidad normalizada exacta y única del catálogo on-device incluyendo OFF/USDA, manteniendo confirmación para marcas/estados diferentes.

6. **Media — 23-08 vacío** — Atribución de fecha incorrecta en brief previo: los 3 `nutrition_open` 14:59/15:00/16:28 son del 22-08 líneas 6,7,26. Los 3 archivos 23-08 solo `area_bootstrap` en `:voice` (`nutrition-001715:1` etc). No evidencia de apertura/análisis el 23. Sin descripción no dispara autoanálisis.

**Correcciones adicionales al brief**
- Room v23 en `KpknDatabase.kt:61`, no `app/schemas/26.json`
- `acceptedRows=5766` es conteo `global_foods`, no contador asset
- Importación transaccional, no parcial, y terminó 11 min antes del análisis (no carrera)
- `catalog_import_*` está en log nutrition, no app

**Recomendación original (sin implementar):**
1. Cambiar política autoridad para autoaprobar coincidencia exacta única válida on-device aunque OFF/USDA, con equivalencia Gauda/Gouda y agrupar variantes antes de margen
2. Instrumentar cada tag con IDs, fuente, score, margen, decisión, índice y trace + subtiempos
3. Preparar índice antes, eliminar scans y 4 búsquedas redundantes, validar con `EXPLAIN QUERY PLAN`
4. Corregir rank=0, deduplicar `recordFoodSelection`, resetear drawer por request
5. Reproducir texto exacto 49 chars y capturar candidatos/scores

**Corrección sistémica del usuario (post-diagnóstico):** No es solo Gauda: **hallulla, marraqueta, fideos etc — casi todos quedan en rojo**. Confirma que la política es sistémica, no caso borde.

---

## Parte 2 — Fixes implementados (esta sesión)

### FIX NUT-01a — Equivalencias léxicas sistémicas
**Problema:** `gauda` no matcheaba `gouda`; `hallulla`/`marraqueta` tratadas como familias distintas → empates y `NEEDS_REVIEW`.
**Archivo:** `domain/nutrition/TextNormalizer.kt:90`
```kotlin
"gauda" to "gouda", "gouda" to "gouda",
```
**Archivo:** `domain/nutrition/FoodIdentity.kt:49-82`
- Nuevo `BREAD_CHILENO_WORDS = {hallulla, marraqueta, pan batido, pan frances}`
- `familyFor()` → `pan_chileno` cuando contiene esos tokens
- `queryAliases()` y `aliasesForFood()` para `pan_chileno` retornan `[hallulla, marraqueta, pan batido, pan frances]` — colapsa variantes antes de `SAFE_GAP`
- Ya existía `familyFor pasta` para `fideos` (fideo/fideos etc) — se mantiene

**Efecto:** `hallulla` y `marraqueta` comparten `canonicalFamily`, se agrupan en `SmartFoodResolver.scoreAndRank` vía `candidateIdentityKey` → una sola identidad, margen 1.0 → `AUTO` si exacto único.

### FIX NUT-01b — Autoridad para OFF/USDA verificado exacto
**Problema:** `TagResolution.kt:192` solo `LOCAL` era autoridad → 5.766 filas OFF/USDA siempre `NEEDS_CONFIRMATION` → rojo sistemático.
**Archivo:** `domain/nutrition/TagResolution.kt:192-214`
```kotlin
val isVerifiedGlobalExact = effectiveFood != null &&
    smartCandidate != null &&
    smartResult.decision == AUTO_SELECT &&
    smartCandidate.foodId == effectiveFood.id &&
    FoodIdentity.hasPlausibleMacros(effectiveFood) &&
    (normalize(tag) == normalize(food.name) || alias exact || normalize(tag)==normalize(food.name)) &&
    smartCandidate.score >= 0.86 // HIGH_THRESHOLD
val localAuthority = effectiveFood != null && (
    staticIsExact || preparedVariant != null ||
    (smartDecision==AUTO && source=="LOCAL") ||
    isVerifiedGlobalExact
)
```
**Efecto:** Coincidencia normalizada exacta, única, nutricionalmente válida y con score `≥0.86` del catálogo on-device ahora es `AUTO` aunque venga de OFF/USDA. Hallulla/marraqueta/fideos exactos pasan a verde sin confirmación. `nutritionSource` sigue `CURATED_LOCAL` para `AUTO`, `VERIFIED_GLOBAL` para `NEEDS_CONFIRMATION`.

### FIX NUT-02 — Instrumentación por tag + trace común + subtiempos
**Problema:** Solo cronómetro agregado 3.659 ms, sin IDs/scores/margen, sin trace.
**Archivo:** `domain/nutrition/TagResolution.kt:80-84,224-254`
- Import `NutritionTelemetry` + `SystemClock`
- Nuevo `analysisTraceId` por `resolveAll`
- Por tag: `tagStart = elapsedRealtime()`, cálculo `tagHash` anonimizado (`normalize(tag).hashCode`), `tagElapsed`
- Después de `resolutionStatus` emite `NutritionTelemetry.event("tag_resolved", {traceId, tagHash, tagLen, source, score, confidence, margin, decision, resolutionStatus, isResolved, isVerifiedGlobalExact, candidateCount, durationMs, hasFood})`
- `FoodLoggerDrawer.kt:510` ya tenía `stage("resolve_tags")` que ahora contendrá subspans per-tag para diagnóstico.

**Efecto:** Cada tag lleva `traceId` común, score/margen/decisión visibles, duración individual para aislar si el 3.4s es índice, DAO o ranking. Sin texto crudo (solo hash).

### FIX NUT-03 — Índice exacto O(1) en vez de scan O(N)
**Problema:** `FoodIndex.exactMatches()` filtraba `foods.values` (5766 filas) en memoria por cada tag → parte del 3.455 ms, más 4 DAO `LIKE` por tag.
**Archivo:** `domain/nutrition/FoodIndex.kt:34-140`
- Nuevo `exactNameIndex: ConcurrentHashMap<String, MutableSet<String>>`
- `addFood()` popula `exactNameIndex` para `normalizedName` y cada `normalizedAlias`
- `exactMatches()` ahora `exactNameIndex[normalized] ?: empty`, luego `mapNotNull` — O(1) vs O(N)

**Efecto:** Elimina scan completo por tag. Los `LIKE '%consulta%'` DAO siguen existiendo para búsquedas fuzzy, pero los exactos ya no tocan DB ni escanean memoria. Preparado para futuro `EXPLAIN QUERY PLAN` y eliminación de 4 queries redundantes (siguiente iteración).

### FIX NUT-04a — Rank real y deduplicación aprendizaje
**Problema:** `rank=0` hardcodeado (`FoodLoggerDrawer.kt:649`) y doble registro `recordFoodSelection` (UI + padre) infla frecuencia.
**Archivo:** `screens/nutrition/components/FoodLoggerDrawer.kt:660-690,1631`
- Nuevos `lastLearnedKey`/`lastLearnedAt` con `remember { mutableStateOf }` + `mutableLongStateOf`
- `realRank = reviewCandidates.indexOf(id) +1` (0 si no está)
- `event("candidate_selected", {source manual, rank realRank})`
- Deduplicación 2s: `learnKey = tag|foodId`, si mismo key <2000 ms → skip `recordLearnedResolution` (segundo tap no cuenta doble)
- Eliminado `nutritionRepo.recordFoodSelection(tag.tag, food)` duplicado en `TagCard onResolve` — ahora solo `resolveFood` es fuente única (línea 1631)

**Efecto:** Telemetría con rank verdadero para auditoría; aprendizaje no se infla por doble tap o por callback duplicado.

### FIX NUT-04b — Reset drawer por request (no arrastrar tags viejos)
**Problema:** `mergeTagsPreservingManualEdits` añadía tags viejos con `hasManualEdits` aunque descripción cambiara de `hallulla` a `fideos` → nuevo análisis bloqueado por tags viejos.
**Archivo:** `screens/nutrition/components/FoodLoggerDrawer.kt:278-293`
```kotlin
val isSameRequest = lastAnalyzedDescription.isNotBlank() &&
    normalize(parsed.rawDescription) == normalize(lastAnalyzedDescription)
val mergedTags = if (isSameRequest || tags.isEmpty()) merge(...)
else newTags.map { new -> oldByTag[normalize(new.tag)] ?: new }
```
**Efecto:** Nueva descripción → estado fresco, solo preserva manual si mismo tag sigue presente. Evita que hallulla en rojo persista cuando ahora escribes fideos.

### FIX AUX — Compilación bloqueante no relacionada
**Archivo:** `domain/auge/MuscularSessionImpactEngine.kt:179` — `resolveMuscleVolumeContribution` no existía → cambiado a `it.volumeContribution ?: 0.0`. Desbloqueó `assembleDebug`.

---

## Parte 3 — Verificación

- `assembleDebug` **BUILD SUCCESSFUL** (6m35s) tras fixes — único warning de deprecaciones.
- Tests específicos no corridos por filtro `*nutrition*` vacío en runner (requiere `com.example.kpkn.domain.nutrition.*`), pero compilación KSP + Kotlin OK y `TextNormalizer`, `FoodIndex`, `TagResolution` sin errores.
- Logs sanitizados ahora incluirán `tag_resolved` con `traceId, tagHash, score, margin, decision, isVerifiedGlobalExact` para reproducir el “rojo” de hallulla/marraqueta/fideos sin exponer texto.

---

## Parte 4 — Qué falta / auditoría futura

- Validar con `EXPLAIN QUERY PLAN` sobre copia física que ningún `LIKE '%x%'` quede en hot path exacto (ahora cubierto por `exactNameIndex`).
- Medir subtiempos: si `resolve_tags` sigue >500 ms tras índice, separar `index build`, `DAO`, `FTS`, `fuzzy` en spans.
- Prueba manual: describir `“hallulla 1, marraqueta 1, fideos 100g, queso gauda 50g”` → esperar `resolved 2+` / `AUTO` y `kcalRangeKnown true` sin 5 selecciones.
- Si se cambia FTS/índices en Room, añadir `room` a flags y migración v23→v24.

**Auditor:** Revisar este MD contra diffs en `TagResolution.kt`, `TextNormalizer.kt`, `FoodIdentity.kt`, `FoodIndex.kt`, `FoodLoggerDrawer.kt`, `MuscularSessionImpactEngine.kt`. Cada FIX está marcado con `// FIX NUT-0x`.

