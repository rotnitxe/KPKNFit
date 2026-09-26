# T-004 candidate delta against captured WIP

This ledger describes only the T-004 edits relative to each path's pre-edit working-tree contents. It is intentionally not a HEAD diff: `Settings.kt`, `SetupSettingsPatch.kt`, the onboarding files, and training/test files already contained protected WIP. The exact pre-edit and candidate SHA-256 values are in `T-004-report.md`. New-file bases were absent.

The hunks below are an abbreviated, candidate-specific diff ledger rather than a patch-apply artifact; unchanged surrounding code is omitted and marked in prose. This preserves the WIP-relative changes without representing pre-existing dirty content as T-004 work.

## Production source hunks (pre-edit WIP → T-004)

```diff
--- android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt
+++ android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt
@@ Settings constructor tail
     val relatorMemoryJson: String? = null,
+    /** Categorical equipment availability; numeric stock remains in [equipmentInventory]. */
+    val equipmentAvailability: EquipmentAvailability? = null,
@@ resolvedEquipmentInventory
-        val configured = equipmentInventory
-            ?: return EquipmentInventory(
-                barbellWeightKg = barbellWeight.takeIf { it.isFinite() && it > 0.0 },
-                plates = availablePlates.mapNotNull { weightKg ->
-                    weightKg.takeIf { it.isFinite() && it > 0.0 }
-                        ?.let { PlateStock(weightKg = it, countPerSide = null) }
-                },
-            )
-        return configured.copy(barbellWeightKg = configured.barbellWeightKg?.takeIf { it.isFinite() && it > 0.0 })
+        val configured = equipmentInventory
+        if (configured != null) return configured.copy(
+            barbellWeightKg = configured.barbellWeightKg?.takeIf { it.isFinite() && it > 0.0 },
+        )
+        if (equipmentAvailability != null) return EquipmentInventory()
+        return EquipmentInventory(legacy barbell/plate fallback unchanged)
```

```diff
--- android-native/app/src/main/java/com/example/kpkn/domain/training/TrainingOptions.kt
+++ android-native/app/src/main/java/com/example/kpkn/domain/training/TrainingOptions.kt
@@ constructor / validation
     val warmup: List<SetRecipe>? = null,
+    val availability: EquipmentAvailability? = null,
 )
-fun validate(): TrainingValidation { ... inventoryHonestyReasons(inventory) ... }
+fun validate(): TrainingValidation = validate(includeInventoryHonesty = true)
+internal fun validateForSelection() = validate(includeInventoryHonesty = availability == null)
+private fun validate(includeInventoryHonesty: Boolean) { ... }
@@ effectiveEquipment
 fun TrainingOptions.effectiveEquipment(legacyEquipment: Set<String>): Set<String> {
+    availability?.let { declaredAvailability ->
+        return buildSet {
+            add("bodyweight")
+            EquipmentCategory.entries.forEach { category ->
+                if (category in declaredAvailability.categories) add(category.canonicalToken())
+            }
+        }
+    }
     // Existing nullable-inventory legacy and numeric-inventory behavior follows unchanged.
 }
+// Exhaustive private mapping: the eleven approved canonical tokens only.
```

```diff
--- android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt
+++ android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt
-when (val config = options.validate()) {
+when (val config = options.validateForSelection()) {
@@ native machine filtering
+val requireExactMachineConfiguration = options.availability == null && options.inventory != null
-if (!equipmentAllows(configuration, equipment, entry.sourceId, options.inventory != null)) ...
+if (!equipmentAllows(configuration, equipment, entry.sourceId, requireExactMachineConfiguration)) ...
```

```diff
--- android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupSettingsPatch.kt
+++ android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupSettingsPatch.kt
@@ SetupSettingsPatch tail (pre-existing inventory/nutrition fields retained)
     val nutritionTrackingOnly: SetupPatchField<Boolean> = SetupPatchField.Unchanged,
+    val equipmentAvailability: SetupPatchField<EquipmentAvailability?> = SetupPatchField.Unchanged,
@@ applyTo(base)
         nutritionTrackingOnly = nutritionTrackingOnly.resolve(base.nutritionTrackingOnly),
+        equipmentAvailability = equipmentAvailability.resolve(base.equipmentAvailability),
```

```diff
--- android-native/app/src/main/java/com/example/kpkn/domain/onboarding/SetupStepGraph.kt
+++ android-native/app/src/main/java/com/example/kpkn/domain/onboarding/SetupStepGraph.kt
     val inventory: Set<String> = emptySet(),
+    /** Null = unanswered/legacy; empty = explicitly no declared categories. */
+    val equipmentAvailability: Set<String>? = null,
@@ SetupChangeDetector.sourcesFor
-old.inventory != new.inventory
+old.inventory != new.inventory || old.equipmentAvailability != new.equipmentAvailability
```

```diff
--- android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardModels.kt
+++ android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardModels.kt
     inventory = trainingOptions.inventory?.toString()?.let { setOf(it) }.orEmpty(),
+    equipmentAvailability = trainingOptions.availability?.categories?.mapTo(linkedSetOf()) { it.name },
```

```diff
--- android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardViewModel.kt
+++ android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardViewModel.kt
@@ buildSettingsPatch
             nutritionTrackingOnly = setIf(true, trackingOnly),
+            equipmentAvailability = if (draft.isStepDeclared(SetupStepId.HOME_EQUIPMENT)) {
+                setOrKeep(draft.trainingOptions.availability)
+            } else SetupPatchField.Unchanged,
@@ newDraft
+trainingOptions = SetupTrainingOptions(availability = settings.equipmentAvailability)
```

## Production delta

- `data/models/EquipmentAvailability.kt` **(new):** serializable `EquipmentAvailability(categories = emptySet())`; closed serializable enum with exactly `BARBELL`, `DUMBBELLS`, `KETTLEBELL`, `MACHINES`, `CABLE`, `SMITH_MACHINE`, `BAND`, `SUPPORT`, `PULL_UP_BAR`, `BALL`, `CARDIO`.
- `data/models/Settings.kt`: appended nullable `equipmentAvailability`; resolver precedence is explicit sanitized stock → empty unknown inventory for non-null categorical availability → unchanged legacy bar/plate fallback.
- `domain/training/TrainingOptions.kt`: appended nullable availability; selection projection short-circuits both inventory and old equipment flags, adds bodyweight, maps only the closed categories, and cannot emit `general_gym`, `free_weights`, or `machine_config:*`. `validate()` retains inventory honesty; internal `validateForSelection()` skips only stock-honesty reasons when categorical availability is explicit.
- `domain/training/SimpleCyclePersonalizer.kt`: switched its production selection validation to `validateForSelection()` and exact machine-config gating now applies only when availability is null and legacy inventory is present. Native categorical machines remain `equipmentId == machine`; approved pools, recipe data, and fixed-recipe guard are unchanged.
- `data/onboarding/SetupSettingsPatch.kt`: appended a three-state availability patch independent from `equipmentInventory`; normal `applyTo(latestSettings)` semantics preserve untouched/latest fields and numeric stock.
- `screens/onboarding/SetupWizardViewModel.kt`: `buildSettingsPatch` persists only a declared `HOME_EQUIPMENT` availability value (including explicit empty); null or an untouched Settings suggestion yields `Unchanged`. `newDraft` seeds stored Settings availability into training options without declaring a step. Restored drafts still take the existing restore branch.
- `domain/onboarding/SetupStepGraph.kt` and `screens/onboarding/SetupWizardModels.kt`: footprint has nullable category names so null differs from explicit empty; availability changes join the existing EQUIPMENT impact path, which stales exercise/load/warm-up previews and marks PLAN for review. Existing `trainingKey` already contains `trainingOptions`, so candidate/program keys include the new field without another edit.

## Regression delta

- `EffectiveEquipmentContractTest`: exhaustive explicit precedence cases plus production `SimpleCyclePersonalizer` generation with machine availability, null stock, and unrelated invalid stock.
- `FixedRecipeEquipmentCompatibilityTest`: broad machine availability still fails an authored exact machine requirement and leaves the authored plan intact.
- `SettingsEquipmentInventoryResolutionTest`: category-only Settings lead the real warm-up load materializer to pending/unknown loads with legacy raw fields unchanged; explicit numeric stock still wins.
- `SetupActivationContractTest`: coordinator applies explicit empty availability to the latest Settings row without changing unrelated Settings or stock; Unchanged preserves existing availability.
- `SettingsJsonBackupTest`: older `{}` Settings decode to null; explicit empty survives backup payload encode/decode and import.
- `EquipmentAvailabilityTest` **(new):** exhaustive closed token projection, forbidden token checks, and null-vs-empty Settings JSON distinction.
- `SetupAvailabilitySeedTest` **(new):** real ViewModel new-draft seed and restored-null behavior; builder patch semantics; draft serialization and footprint/change-impact distinction.

No production edit was made to `SettingsJsonBackup.kt`: its Settings JSON serializer already propagates serializable fields. No UI, route, inventory-question, catalog, authored recipe, Room schema, iOS, backend, or asset path was edited.
