package com.example.kpkn.screens.wikilab.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.kpkn.R
import kotlinx.coroutines.delay

enum class SquatVariant {
    HIGH_BAR,
    LOW_BAR
}

@Composable
fun CaupolicanSquatInteractiveViewer(
    modifier: Modifier = Modifier,
    initialVariant: SquatVariant = SquatVariant.HIGH_BAR
) {
    // Bucle automático de falso GIF (alterna cada 1.2 segundos sin controles ni interfaz extra)
    var showBottom by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200L)
            showBottom = !showBottom
        }
    }

    val topImage = if (initialVariant == SquatVariant.HIGH_BAR) {
        R.drawable.ic_caupolican_high_bar_top_art
    } else {
        R.drawable.ic_caupolican_low_bar_top_art
    }

    val bottomImage = if (initialVariant == SquatVariant.HIGH_BAR) {
        R.drawable.ic_caupolican_high_bar_bottom_art
    } else {
        R.drawable.ic_caupolican_low_bar_bottom_art
    }

    val currentImage = if (showBottom) bottomImage else topImage

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0A0E)),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = currentImage,
            animationSpec = tween(durationMillis = 300),
            label = "FalseGifCrossfade"
        ) { targetDrawable ->
            Image(
                painter = painterResource(id = targetDrawable),
                contentDescription = "Demostración de ejercicio",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
