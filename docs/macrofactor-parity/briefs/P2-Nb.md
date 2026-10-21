# P2-Nb · Estado de nutrientes, cobertura, contribuyentes y metas DRI

**Prioridad P2 · esfuerzo M–L.** Informe §4.4 (ítems D11 y D12 del informe; decisión D15 de la ola). Depende de **P2-Na** y **N3** (ya integrados). La vista diaria de micronutrientes se eliminó en el commit `0f649f72c`; hoy el día solo muestra kcal y macros, y cada alimento como mucho fibra, azúcar y sodio. Los faltantes del importador pasan a 0 (`data/food/FoodImporter.kt` ≈ l. 445-458), así que un total bajo no dice si falta el dato o es cero. **Sin cambiar el esquema de Room ni reimportar el catálogo**: este paquete resuelve lo que se puede resolver con los datos y la semántica de hoy, y lo dice con honestidad.

## Semántica de datos (decisión de diseño)
Para cada nutriente y cada alimento consumido hay tres estados:
- **Declarado:** el nutriente figura en `micronutrients` del alimento (cantidad ≥ 0; un 0 explícito es «cero declarado») o, para las columnas base de `LoggedFood` (fibra, azúcar, sodio, potasio, agua), su valor es > 0.
- **Sin dato:** no figura en `micronutrients` y la columna base es 0 (no se puede distinguir de un cero real: la etiqueta dice «sin dato (puede ser 0)»; no es un cero declarado).
- **Estimado:** el alimento viene de una estimación (`analysisSource`/banderas de calidad `INCOMPLETE`, `LOW_QUALITY`, o instantánea de entrada rápida/receta sin ese nutriente): cuenta como dato débil y se marca.
La **entrada rápida** (N4) y los alimentos sin nutrientes detallados aportan «sin dato» a los micronutrientes, nunca ceros.

## Cambio exacto
1. **Dominio puro `domain/nutrition/NutrientCoverage.kt`:** por día (o rango) y nutriente: total conocido, **cobertura** (proporción de las kcal del día de alimentos con dato declarado; y número de alimentos con/sin dato), estado (Completo ≥ 90 % · Parcial · Sin datos), y los **contribuyentes** principales (hasta 5, con su aporte). Puedes LEER como referencia `C:\kw\ref\mf-wip\untracked\android-native\app\src\main\java\com\example\kpkn\domain\nutrition\NutrientCoverage.kt` y su test `NutrientCoverageContractTest.kt` (mismo problema); porta a mano lo que encaje.
2. **Metas DRI (`domain/nutrition/DriTargets.kt`):** tabla pública (IOM/NAM, DRI: RDA/AI y límites superiores) para unos 20 nutrientes clave (fibra, sodio máx., potasio, calcio, hierro, magnesio, zinc, vitaminas A, C, D, E, K, B6, B12, folato, tiamina, riboflavina, niacina, yodo, selenio, según haya datos) por **sexo** y tramo de **edad** (19–30, 31–50, 51–70, > 70) y, si el perfil lo permite, embarazo/lactancia. Cita la fuente y la fecha en comentarios y en `docs/macrofactor-parity/P2-Nb_FUENTES.md`. Datos y fuentes SON públicos: no copies una tabla de ninguna app. Los campos de metas de `MacroGoals` que hoy están huérfanos se conectan o se documentan, sin borrarlos.
3. **UI:** una hoja «Nutrientes del día» accesible desde los totales del día en Nutrición (`screens/nutrition/NutritionScreen.kt`; refina, no rediseñes): lista por nutriente con total conocido, meta, barra, chip de estado y, al tocar, sus contribuyentes. **Nunca** muestra «0» para «sin dato». Pie honesto: «El total solo cuenta los alimentos con dato; puede ser mayor». Estilo KPKN v2, español, ≥ 48 dp, accesibilidad real. Hoja de render (PNG a 360 dp, 100 % y 130 %) y `NutrientsHarnessActivity` con `state` = `complete` | `partial` | `no_data` | `with_quick_entry`.
4. **No cambies** el importador, el catálogo ni los totales de kcal y macros.

## Archivos permitidos
- Nuevos: `domain/nutrition/NutrientCoverage.kt`, `domain/nutrition/DriTargets.kt`, `screens/nutrition/nutrients/*`, `app/src/debug/.../NutrientsHarnessActivity.kt` + manifiesto; tests; hoja de render; `docs/macrofactor-parity/P2-Nb_FUENTES.md`.
- `screens/nutrition/NutritionScreen.kt`, `NutritionViewModel.kt` (solo el acceso y el estado de la hoja), `data/models/NutritionModels.kt` (solo tipos nuevos con valor por defecto si hace falta).
- `docs/macrofactor-parity/CAMBIOS_VISIBLES.md`.

## Pruebas obligatorias
- Un alimento sin dato de sodio no aporta 0 al total ni cuenta como cobertura; el total declara su cobertura (p. ej. 2 de 5 alimentos).
- Cero declarado ≠ sin dato (una entrada `micronutrients` con 0 cuenta como declarada).
- Contribuyentes ordenados y sumando el total conocido; entrada rápida aporta «sin dato».
- Metas por sexo y edad (casos de borde: 30/31, 50/51, 70/71 años); sin perfil, no hay metas (no se inventan).
- La hoja con día vacío, día completo, parcial y con entrada rápida.
- Regresión de `domain.nutrition.*` y `screens.nutrition.*` sin cambiar expectativas ajenas.

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.*" --tests "com.example.kpkn.screens.nutrition.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/P2Nb` con `--tests "com.example.kpkn.render.NutrientsRenderSheetTest"`.

## Fuera de alcance
Separar «cero» de «desconocido» en el importador o en Room (requiere reimportar el catálogo: se propone aparte con permiso), FTS (ítem D8 del informe; decisión D16 de la ola: la búsqueda `LIKE` actual se mantiene y su medición va en el paquete P2-F), foto de etiqueta (ítem D10 del informe: no se implementa en esta ola).
