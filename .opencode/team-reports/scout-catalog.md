# Informe: Catálogo/Picker de Ejercicios V2 — KPKN Fit (solo lectura)

Raíz investigada: `android-native/app/src/main/java/com/example/kpkn/`. Nota general de estructura: los archivos del picker viven en `screens/sessioneditor/components/` (no directo en `sessioneditor/`), salvo `CatalogSelectionWizard.kt` que está en `screens/sessioneditor/`.

Dato estructural clave que afecta a varios puntos: **`ExercisePickerSheet.kt` delega el 100% de la superficie al catálogo V2 y hace `return` en la línea 133** (`ExercisePickerSheet.kt:118-133`). Todo el código V1 posterior (líneas 134-687: orden legacy, `CatalogSelectionWizard`, `ExercisePickerDetailedCard`, `ExercisePickerSelectionDock`) es **código muerto inalcanzable**.

---

## 1. BUG DE VARIANTES IGNORADAS ("Aperturas declinadas" + POLEA → aparece "mancuernas")

### Flujo completo verificado

**a) Selección en el picker (V2):**
- Entry point real: `ExercisePickerV2Catalog()` (`components/ExercisePickerV2Catalog.kt:126`), renderizado por `CatalogReadyContent()` (`:265`).
- El borrador de opciones por definición vive en `draftByDefinition` (`mutableStateOf`, `:351`); el initial draft se siembra desde `initialCatalogDefinitionId/initialCatalogConfigurationId` (`:336-350`).
- Al tocar un chip de eje: `selectOption` (`:704-712`) → `draftAfterAxisSelection()` (`:1712-1745`), que repara opciones incompatibles eliminando ejes que ninguna configuración materializada soporta.
- La configuración exacta se obtiene de `repository.compatibility(definitionId, selectedOptions).exactConfigurationId` (`:497-500`; implementación en `domain/exercises/catalogv2/ExerciseCatalogV2Repository.kt:86-158`, `exactConfigurationId = matching.singleOrNull()?.id` en `:156`).
- Fallback: `resolvedConfigurationId = selectedConfigurationId ?: bestMatchingConfigurationId(...)` (`:509-510`; función en `:1683-1697`): elige la configuración compatible con **menos desviaciones del default**, respetando los valores elegidos por el usuario (no debería descartar "cable").

**b) Confirmación:**
- Botón "Seleccionar ejercicio"/"Usar este ejercicio" (`:812-834`): `exactInfo(catalog, definition, selectedConfigurationId o resolvedConfigurationId)`.
- Click en tarjeta colapsada sin opciones o pre-configurada por búsqueda: agrega al instante con `resolvedConfigurationId` (`:558-565`).
- `exactInfo()` (`:1698-1710`) → `ExerciseCatalogV2.toLegacySelection(ExerciseSelectionV2)` (`data/exercises/catalogv2/ExerciseCatalogV2LegacyAdapter.kt:111-127`) → `toLegacyInfo()` (`:38-108`):
  - `id = definition.id` (id del PADRE, `:122/:65`), `name = canonicalName` del padre (`:66`)
  - `equipment = equipmentLabel(profile.equipmentId)` de la config elegida (`:74`)
  - `catalogDefinitionId = id`, `catalogConfigurationId = configuration.id` (`:94-95`)
  - `catalogVariantChips = optionAxes → selectedOptions → optionLabel` (`:101-103`; "cable" → "Polea" según `ExerciseCatalogV2Labels.kt:46`).
