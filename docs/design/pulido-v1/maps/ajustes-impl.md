# Ajustes · implementación (paquete aj)
Archivos nuevos (screens/settings): AjIcons.kt, AjDesign.kt, AjScreens.kt; reescritos: SettingsScreen.kt, NutritionSourcesDialog.kt (ahora hoja), export/CsvExportSheet.kt.
Eliminados: TrainingSettingsBlock, equipment/* (4), components/SettingsListItems, ruta EquipmentInventory (Navigation, MainActivity), arneses debug de material y de entrenamiento, sus tests y hojas de render.
Descanso: constante domain/workout/RestDefaults.kt (90 s); el descanso al registrar siempre arranca.
Tests: AjustesScreensTest (7), AjustesRenderSheetTest (27 PNG), CsvExportSheetTest, WorkoutSetRecorderDurableRoomTest actualizado.
Comparación con el lienzo: ver paridad/DESVIACIONES.md (DESV-1400/1401/1402).
