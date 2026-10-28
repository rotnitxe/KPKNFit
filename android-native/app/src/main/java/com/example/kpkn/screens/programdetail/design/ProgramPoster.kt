package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverIdentity
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface

/**
 * Póster del programa (KPKN_UI_V2, P1): 88 × 116 dp, radio 16 dp y filete `moduleBorder` (la portada de Program Detail pasa las
 * medidas del lienzo v6: 84 × 104 dp y radio 18 dp). Pinta, por orden: la foto propia
 * ([photoUrl], con Coil), la portada empaquetada ([bundled]) y, si no hay ninguna, el arte de estudio de [identity]. Con [animate] y
 * sin movimiento reducido, el arte respira; fuera de pantalla no se mueve (lo decide `CoverStudioArt`).
 */
@Composable
internal fun ProgramPoster(
    identity: CoverIdentity?,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    bundled: ImageBitmap? = null,
    animate: Boolean = true,
    width: Dp = 88.dp,
    height: Dp = 116.dp,
    radius: Dp = 16.dp,
) {
    val reduced = wizardReducedMotion()
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(KpknSurface.inset, shape)
            .border(1.dp, KpknSurface.moduleBorder, shape)
            .semantics { contentDescription = "Portada del programa" },
    ) {
        when {
            photoUrl != null -> AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            bundled != null -> Image(
                bitmap = bundled,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            identity != null -> CoverStudioArt(
                identity = identity,
                modifier = Modifier.fillMaxSize(),
                animate = animate && !reduced,
                reduced = reduced,
            )
        }
    }
}
