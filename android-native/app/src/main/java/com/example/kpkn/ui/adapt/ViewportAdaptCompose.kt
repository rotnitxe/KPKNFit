package com.example.kpkn.ui.adapt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

val LocalViewportAdapt = compositionLocalOf { ViewportAdapt.Identity }

@Composable
fun ViewportAdaptProvider(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthDp = when {
            maxWidth != Dp.Unspecified && maxWidth.value.isFinite() && maxWidth.value < 10_000f ->
                maxWidth.value
            else -> configuration.screenWidthDp.toFloat()
        }
        val heightDp = when {
            maxHeight != Dp.Unspecified && maxHeight.value.isFinite() && maxHeight.value < 10_000f ->
                maxHeight.value
            else -> configuration.screenHeightDp.toFloat()
        }
        val adapt = remember(widthDp, heightDp) {
            ViewportAdaptMath.compute(widthDp = widthDp, heightDp = heightDp)
        }
        CompositionLocalProvider(LocalViewportAdapt provides adapt) {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Composable
fun Dp.adapt(min: Dp = 0.dp): Dp {
    val scaled = this * LocalViewportAdapt.current.uniformScale
    return if (min > 0.dp && scaled < min) min else scaled
}

fun Dp.adapt(adapt: ViewportAdapt, min: Dp = 0.dp): Dp {
    val scaled = this * adapt.uniformScale
    return if (min > 0.dp && scaled < min) min else scaled
}

@Composable
fun TextStyle.adapt(): TextStyle {
    val scale = LocalViewportAdapt.current.uniformScale
    return copy(
        fontSize = fontSize.adaptSp(scale),
        lineHeight = lineHeight.adaptSp(scale, floor = false),
        letterSpacing = letterSpacing.adaptSp(scale, floor = false),
    )
}

fun TextUnit.adaptSp(scale: Float, floor: Boolean = true): TextUnit {
    if (!isSpecified || !isSp) return this
    val scaled = value * scale
    val floored = if (floor) scaled.coerceAtLeast(ViewportAdaptMath.MIN_LABEL_SP) else scaled
    return floored.sp
}
