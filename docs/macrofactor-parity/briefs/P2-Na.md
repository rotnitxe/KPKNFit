# P2-Na · Recetas con rendimiento, favoritos y hora de la comida, y copia de seguridad de preferencias

**Prioridad P2 · esfuerzo M–L.** Informe §4.4 (ítems D5 y D13 del informe), §4.9 (H-c) y el hallazgo de H1 sobre la exportación. Depende de **N3** (alimentos propios y `saveCustomFood` durable) y **N4** (plantillas, copiar y entrada rápida), ya integrados. Sin cambios de esquema de Room: todo va como campos con valor por defecto en JSON y en preferencias.

## 1. Recetas con rendimiento (ítem D5 del informe)
Hoy no hay recetas de usuario: solo estimaciones de platos del catálogo. Una receta separa tres conceptos: **ingredientes**, **peso final cocinado** y **ración registrada**. Cocinar pierde agua, no nutrientes: el peso final cambia la densidad, **nunca** los totales del lote.
- **Modelo (en `data/models/NutritionModels.kt`, valores por defecto):** `RecipeData(version: Int, ingredients: List<RecipeIngredient>, servings: Int, finalWeightGrams: Double?, notes: String?, updatedAt)`, donde `RecipeIngredient` guarda el nombre, el id del alimento (si existe) y **la instantánea de cantidad y nutrientes** al guardarla. Se guarda como un alimento propio (`FoodItem.recipe: RecipeData? = null`) con `source = "recipe"`: así entra en la búsqueda y en el registro por el camino de siempre y sin migración.
- **Dominio puro (`domain/nutrition/RecipeCalculator.kt`):** totales del lote = suma de ingredientes; por ración = total / raciones; por 100 g de peso final = total / peso final × 100 (si hay peso final). Sin peso final solo se puede registrar por ración. Validaciones: raciones ≥ 1, peso final > 0 y razonable frente a la suma de ingredientes (aviso, no bloqueo), ingredientes ≥ 1, cantidades finitas > 0.
- **Versionado e inmutabilidad:** editar una receta sube `version` y reemplaza su definición; **los registros pasados no cambian** (cada comida ya guarda su instantánea `LoggedFood`; escribe el test que lo demuestra).
- **UI (junto a las pantallas de alimentos propios de N3, `screens/nutrition/customfood/*` y el cajón):** «Crear receta» (nombre, buscador de ingredientes con el buscador existente, cantidad por ingrediente, raciones, peso final opcional, vista previa viva «por ración» y «por 100 g»), editar, duplicar y borrar; en el cajón, acceso «Recetas» con las guardadas. Registrar una receta: por **raciones** o por **gramos** de peso final. Guardado durable con `saveCustomFood`.
- **Criterios:** un lote de 800 kcal en 4 raciones → 200 kcal por ración. Con peso final de 600 g, 150 g → 200 kcal. Editar la receta no cambia registros anteriores. Una receta cuyos ingredientes ya no existen en el catálogo sigue funcionando por su instantánea.

## 2. Favoritos visibles y hora de la comida (ítem D13 del informe)
- **Favoritos:** una estrella en cada alimento (resultados, recientes, plantillas/recetas) y una sección «Favoritos» arriba del cajón. Persisten en un conjunto de ids en preferencias (`nutrition_food_catalog` u otra, la que ya use el repositorio para preferencias de alimentos), **incluido en la copia de seguridad** (punto 3). No cambies el ranking por uso existente.
- **Hora de la comida (opcional):** el registrador guarda siempre las 12:00 (`T12:00:00.000Z` en `duplicateLog` y al crear). Permite elegir la hora: por defecto, la hora actual si el día es hoy y las 12:00 en cualquier otro; editable en la hoja de fecha del registrador (`screens/nutrition/design/logger/LoggerDateSheet.kt`). Las filas del diario muestran la hora en una etiqueta discreta y se ordenan por hora dentro de su comida. No cambia a qué día pertenece una comida (usa el día local, sin saltos por zona horaria; cuidado con los registros guardados en UTC).

## 3. Copia de seguridad de preferencias (H-c)
`nutrition_utensils` (volúmenes de utensilios) y `food_query_learning_v2` (memoria de selección de alimentos) más los **favoritos** nuevos quedan fuera de `data/settings/SettingsJsonBackup.kt`. Inclúyelos en la copia y en la restauración (con la versión de esquema de la copia si hace falta subirla; conserva la lectura de copias antiguas). Test de ida y vuelta con datos reales de cada preferencia.

## 4. Exportación robusta (hallazgo de H1)
`screens/settings/SettingsViewModel.kt` (≈ l. 200-225) exporta con `.map { it.toMealTemplate() }` y otras decodificaciones en bloque: una fila ilegible rompe toda la exportación. Aplica el patrón de cuarentena de H1 (`NutritionRepository.decodeUserRows`: excluye la fila ilegible, la registra sin volcar su contenido y sigue). Revisa también los demás `getAll…().map { … }` de la exportación. Test: una plantilla ilegible no impide exportar el resto.

## Archivos permitidos
- `data/models/NutritionModels.kt` (campos con valor por defecto), nuevo `domain/nutrition/RecipeCalculator.kt`, `data/repository/NutritionRepository.kt` (favoritos, hora, recetas sobre `saveCustomFood`), `data/settings/SettingsJsonBackup.kt`, `screens/settings/SettingsViewModel.kt` (solo la exportación).
- `screens/nutrition/customfood/*`, nuevos `screens/nutrition/recipes/*`, `screens/nutrition/components/FoodLoggerDrawer.kt`, `screens/nutrition/design/logger/*`, `screens/nutrition/NutritionScreen.kt` y `NutritionViewModel.kt` (hora y orden en el diario).
- Tests, hoja de render y `app/src/debug/.../RecipeHarnessActivity.kt` (`state` = `empty` | `editing` | `preview_by_weight` | `error` | `logged`) + bloque de manifiesto; `docs/macrofactor-parity/CAMBIOS_VISIBLES.md`.

## Pruebas obligatorias
- `RecipeCalculator`: los dos criterios de aceptación; sin peso final solo por ración; el peso final no cambia los nutrientes totales; validaciones.
- Receta guardada y editada: `version` sube; registros anteriores idénticos; búsqueda y registro de la receta por raciones y por gramos; guardado durable (un fallo de Room no publica).
- Favoritos: marcar/desmarcar persiste, aparece en «Favoritos» y viaja en la copia.
- Hora: por defecto hoy = ahora, otro día = 12:00; el día de la comida no cambia por la zona horaria; orden por hora; registros antiguos (12:00) siguen mostrándose bien.
- Copia: utensilios, aprendizaje de selección y favoritos hacen ida y vuelta; copias antiguas se leen.
- Exportación con una fila ilegible.
- Regresión de `data.repository.Nutrition*` y `screens.nutrition.*` sin cambiar expectativas ajenas.

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.*" --tests "com.example.kpkn.data.repository.Nutrition*" --tests "com.example.kpkn.screens.nutrition.*" --tests "com.example.kpkn.data.settings.*" --tests "com.example.kpkn.screens.settings.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/P2Na` con `--tests "com.example.kpkn.render.RecipeRenderSheetTest"`.

## Fuera de alcance
Importar recetas por URL (decisión D6: no se implementa); estado de nutrientes y DRI (P2-Nb); cambiar el esquema de Room.
