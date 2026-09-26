package com.example.kpkn.screens.nutrition

/**
 * El DTO del borrador vive en `domain/nutrition` porque el dominio del alta
 * ([com.example.kpkn.domain.onboarding.SetupNutritionPreparation]) lo necesita y
 * `domain/` no puede importar `screens/`. Este typealias conserva el nombre y el
 * paquete históricos: hay UNA sola clase serializable y el JSON de los
 * borradores guardados sigue leyéndose igual.
 */
typealias NutritionWizardDraft = com.example.kpkn.domain.nutrition.NutritionWizardDraft
