package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.PlanProvenanceClass

/** Sólo recetas propias sin protocolo de autor admiten propuestas KPKN confirmables. */
fun Program.acceptsKpknAdjustments(): Boolean = sourceRecipe != null && sourceProtocolId == null &&
    sourceRecipe.compositionProfile != RecipeCompositionProfile.AUTHORED_EXACT &&
    sourceRecipe.provenance?.category != PlanProvenanceClass.ORIGINAL &&
    planProvenance?.category != PlanProvenanceClass.ORIGINAL
