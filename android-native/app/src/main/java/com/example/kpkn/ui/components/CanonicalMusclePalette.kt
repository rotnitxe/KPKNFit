package com.example.kpkn.ui.components

import androidx.compose.ui.graphics.Color

/** Shared display palette for canonical muscle labels. */
fun canonicalMuscleColor(name: String): Color = when (name) {
    "Pectorales" -> Color(0xFFE53935)
    "Dorsales" -> Color(0xFF1E88E5)
    "Trapecio" -> Color(0xFF1976D2)
    "Deltoides" -> Color(0xFFFF8F00)
    "Tríceps" -> Color(0xFF7B1FA2)
    "Bíceps" -> Color(0xFF8E24AA)
    "Antebrazo" -> Color(0xFF795548)
    "Abdomen" -> Color(0xFF00897B)
    "Cuádriceps" -> Color(0xFF43A047)
    "Isquiosurales" -> Color(0xFF2E7D32)
    "Glúteos", "Glúteo Medio" -> Color(0xFF558B2F)
    "Aductores" -> Color(0xFF7CB342)
    "Pantorrillas" -> Color(0xFF33691E)
    "Core" -> Color(0xFF00695C)
    "Erectores Espinales" -> Color(0xFF1565C0)
    "Cuello" -> Color(0xFF6D4C41)
    else -> Color(0xFF757575)
}

val CONCEPTS_DIVIDER: Color = Color(0xFF2A2A2A)
val CONCEPTS_LINK_COLOR: Color = Color(0xFF9DB6C9)
