package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.SetupPlanReveal
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCarousel
import com.example.kpkn.ui.design.KpknCompactPill

/** Una sola oferta, dos grupos visibles y elección conservada; no elige automáticamente. */
@Composable
internal fun CatalogOfferSection(offer: SplitPlanOffer,recommendedId: String?,selectedId: String?,
    onSelect: (String) -> Unit,onOpen: (String) -> Unit,onExpandCatalog: () -> Unit) {
    if (offer.tailored.isNotEmpty()) {
        Text("Tu programa",style = WizardTypography.controlLabel,color = WizardColors.text)
        PlanCarousel(cards = cardModelsOf(offer.tailored,recommendedId),selectedId = shownSelectedIdOf(selectedId,offer.tailored),
            onSelect = onSelect,onOpen = onOpen,initialId = recommendedId)
    }
    CatalogOnlySection(offer,selectedId,onSelect,onOpen,onExpandCatalog)
}

@Composable
internal fun CatalogOnlySection(offer: SplitPlanOffer,selectedId: String?,onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,onExpandCatalog: () -> Unit) {
    if (offer.catalogHidden > 0) KpknCompactPill(text = "Ver programas del catálogo (${offer.catalogHidden})",onClick = onExpandCatalog,
        modifier = Modifier.testTag(MORE_PROGRAMS_TAG), filled = false, fullWidth = true)
    if (offer.catalog.isNotEmpty()) {
        Text("Del catálogo",style = WizardTypography.controlLabel,color = WizardColors.text)
        PlanCarousel(cards = cardModelsOf(offer.catalog),selectedId = shownSelectedIdOf(selectedId,offer.catalog),onSelect = onSelect,onOpen = onOpen,
            initialId = shownSelectedIdOf(selectedId,offer.catalog))
    }
}
