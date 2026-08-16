---
flags: [nutrition, room]
---

# Plan ejecutable: precisión nutricional, calibración y auditoría de cálculos (V2)

Fecha: 2026-08-16. Derivado del plan maestro aprobado por el usuario. Android solamente; iOS/backend reciben contratos y golden cases sin código.

## Rutas

- Dominio nutricional: `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/`
- Datos e importación: `android-native/app/src/main/java/com/example/kpkn/data/food/`
- Room y DAOs: `android-native/app/src/main/java/com/example/kpkn/data/db/` (Entities.kt, Daos.kt, KpknDatabase.kt)
- Repositorios: `android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt`
- Modelos: `android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt`
- UI nutricional: `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/`
- Esquemas Room: `android-native/app/schemas/`
- Tests unitarios: `android-native/app/src/test/java/com/example/kpkn/`
- Tests instrumentados: `android-native/app/src/androidTest/java/com/example/kpkn/`
- Contratos: `docs/contracts/nutrition_interpretation_v1.schema.json`, `docs/contracts/nutrition_interpretation_v1_golden.json`
- Auditoría: `docs/audits/2026-08-nutrition-precision/README.md`

Cambios preexistentes a preservar (no tocar, sin reset/stash/checkout): 18 archivos modificados en workout, session-editor, Navigation.kt, MainActivity.kt y Settings.kt (trabajo concurrente). El staging se limita a nutrición, Room y documentación asociada.

## Impacto

- **Fase 0 (tests primero):** regresiones del flujo completo para `200 g pechuga de pollo {cocida|cocinada|a la plancha|al horno|cruda}` que comprueban identidad, fila fuente, base de peso, gramos visibles, transformaciones y macros finales. Deben fallar por la razón esperada antes de tocar producción.
- **Fase 1 (corrección inmediata):** `FoodParser`/`TagResolution`/`SmartFoodResolver` conservan el estado crudo/cocido para la resolución; `CookingStateResolver.methodSearchSuffixes` reconoce género/plural y raw/cooked; `MacroCalculator.scaleFoodByPortion` no aplica yield ni factor de cocción cuando ficha y peso comparten base; eliminación de ajuste de proteína por contexto; último fallback manual `RESOLVED`→`NEEDS_REVIEW`. Compuerta: 200 g cocidos ≈ 64,2 g proteína (FDC 331960, 32,1 g/100 g) y ninguna ruta válida a 78–91 g.
- **Fase 2 (catálogo v2):** generador extendido (`food_catalog_v2.csv.gz`, `food_portions_v2.csv.gz`, manifiesto con SHA-256/licencia/filas); `GlobalFoodEntity` ampliado con procedencia/estado/base/versión/categoría/porción/calidad; importación atómica con rollback; sin cache negativo permanente. Migración Room v20→v21 no destructiva + `NutritionCalibrationProfileEntity` singleton + campos en `LearnedResolutionEntity`; exportar `app/schemas/.../21.json`; tests de migración v20 con datos representativos.
- **Fase 3 (resolver trazable):** pipeline inmutable identidad → estado/base → porción → preparación/aceite → macros, con evidencia por etapa, orden de candidatos por precedencia (barcode > identidad exacta > estado > marca > calidad > aprendido > fuzzy), reglas matemáticas de conversión (yield+retención solo si falta ficha), aceite escalable por 100 g con rango, porciones absolutas idempotentes.
- **Fase 4 (Food Logger V2):** ViewModel con StateFlow readonly y eventos unidireccionales; preguntas visibles encima de resultados; `Pequeña/Habitual/Grande · N g` + `Ingresar gramos` + `No estoy seguro; usar estimación`; bloqueo de Guardar ante dudas materiales (confianza <0,80, margen <0,15, ≥50 kcal o ≥5 g P/G entre alternativas); `≈` en estimaciones; chips de fuente/estado; edición manual si el resolver falla.
- **Fase 5 (calibración):** ruta `nutrition/calibration` con wizard de 6 pasos omisible/reanudable/versionado; madurez tras 3 confirmaciones ±15%; `Usé tu habitual: N g · Cambiar`; restablecer desde Ajustes > Nutrición.
- **Fase 6 (planes/energía/histórico):** `metricType` preservado y `startValue` desde medición correspondiente; dirección explícita de déficit/superávit; eliminar plan activo limpia referencia y valores derivados (sin autoactivar el último); progreso = ingesta/meta alimentaria con entrenamiento en tarjeta separada (sin doble conteo TDEE); `DailyGoalSnapshot` con días históricos inmutables; día sin registro = hueco; promedios con cobertura `N de M`; validación de mediciones corporales (NaN/infinito/negativos/0–100%).
- **Fase 7 (telemetría/docs/paridad):** eventos locales sanitizados (interpretation_started, candidate_selected, clarification_requested/answered, interpretation_finalized, manual_correction, calibration_updated, catalog_import_*); contrato JSON + golden + README de auditoría. Sin texto libre ni credenciales.

## Pruebas

Línea base a no degradar: 24 suites nutricionales, 313 tests, 0 fallos, compilación BaseDebug exitosa.

Obligatorias nuevas:
1. Regresiones pollo (Fase 0): 5 variantes de cocción + contexto post-entreno no altera proteína por 100 g. Fallan primero por doble conversión.
2. Fase 1: `MacroCalculatorTest`, `CookingPortionPrecisionTest`, `ResolutionGoldenCorpusTest`, `CookingFactorsTest` verdes + 200 g cocidos ≈ 64,2 g.
3. Parser de estados/métodos; matching exacto/fuzzy y precedencia; raw/cooked sin doble conversión; rendimiento/retención solo cuando falta ficha; energía de fuente vs diagnóstico Atwater; aceite explícito/proporcional/cero; porciones absolutas idempotentes; umbrales de aclaración; aprendizaje confirmado excluye Unsure; planes/TDEE/objetivos/snapshots; series con huecos; validación corporal.
4. Datos/Room: checksum del catálogo, filas malformadas, importación abortada con rollback, FTS coherente, migración v20→v21 con datos reales, instalación v21 limpia, logs antiguos sin evidencia/snapshot, reimportación sin borrar custom/aprendizaje.
5. UI Compose: pregunta por porción visible, tres tamaños con gramos, guardar bloqueado, "No estoy seguro" con rango, cambio repetido de tamaño idempotente, wizard completo/omitido/reanudado/restablecido, plan grasa/músculo, gráficos con huecos y bandas, errores de mediciones.

Comandos (desde `android-native/`):
- Tests dirigidos: `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.CookingPortionPrecisionTest'"`
- Suite nutricional completa + `compileBaseDebugKotlin` + `assembleBaseDebug`.
- Fallback: `gradlew.bat --no-daemon --console=plain --warning-mode=summary <task>` con timeout 300000/600000.

Compuerta por fase: tests verdes + BUILD SUCCESSFUL antes de pasar a la siguiente. QA de emulador solo con autorización expresa.

## Riesgos

- **Migración Room v20→v21:** mitigar con fixture v20 realista, patrón recreate-table existente, exportar 21.json, tests de migración. No borrar logs/planes/mediciones/custom foods al reconstruir catálogo global.
- **Tamaño del asset (~70 MB):** medir tamaño/memoria/importación tras extender el generador; no crecer sin cobertura útil justificada.
- **Falsa confianza:** ninguna heurística queda marcada como verificada sin respuesta explícita; "No estoy seguro" conserva rango y no entrena aprendizaje.
- **Regresión de rendimiento:** parsing/ranking/opciones fuera del hilo principal.
- **Drift USDA/OFF:** fijar releases y SHA-256 en manifiesto.
- **Reintroducción por fallback:** el pipeline V1 no debe ejecutarse silenciosamente si V2 falla; el fallback manual debe ser `NEEDS_REVIEW`.
- **Contaminación de trabajo concurrente:** diffs limitados a nutrición/Room/docs; los 18 archivos de workout/session-editor quedan intactos.
- Tope de auditoría: 5 rondas por plan según pipeline; no avanzar a instalación/UI si dominio, migración o suite fallan.

## Definición de terminado

Pollo 200 g cocidos ≈ 64,2 g proteína; sin doble conversión raw/cooked; porción vaga pregunta o muestra rango; Guardar no oculta dudas materiales; aceite y cocción escalan coherentes; USDA/OFF conservan procedencia/estado/porciones; calibración aprende y se restablece; planes/energía/metas históricas/gráficos cumplen semántica; Room 20→21 sin pérdida; suite + build + instalación verdes; Auditor acepta sin P0/P1; la entrega enumera lo probado y lo no probado físicamente.
