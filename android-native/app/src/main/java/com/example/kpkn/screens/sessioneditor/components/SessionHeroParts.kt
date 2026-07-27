package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.screens.sessioneditor.sessionBackgroundPresets
import com.example.kpkn.screens.sessioneditor.sessionGradients
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeSummary
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState

@Composable
internal fun SessionBackgroundLayer(background: SessionBackground?, blurDp: androidx.compose.ui.unit.Dp) {
    when {
        background == null || background.type == SessionBackgroundType.COLOR -> {
            val gradient = sessionBackgroundPresets.firstOrNull { it.id == background?.value } ?: sessionGradients.first()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradient.colors))
                    .blur(blurDp),
            )
        }
        else -> {
            AsyncImage(
                model = background.value,
                contentDescription = "Fondo de sesión",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurDp),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun HeroGlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    showUnsavedDot: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DarkEditorChip,
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            if (showUnsavedDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                )
            }
        }
    }
}

@Composable
internal fun HeroActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
        ),
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun TemplatesFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "Plantillas de sesión",
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun HeroGlassFab(
    summary: SessionEditorAugeSummary,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    onClick: () -> Unit,
) {
    // Same construction as the roadmap dock: Box + kpknGlass OVER a sibling hazeSource.
    // Do NOT wrap this in Scaffold's floatingActionButton (that nests inside hazeSource and kills blur).
    val glassModifier = if (hazeState != null) {
        Modifier.kpknGlass(hazeState, CircleShape)
    } else {
        Modifier.background(DarkEditorChip, CircleShape)
    }
    Box(
        modifier = modifier
            .size(56.dp)
            .then(glassModifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = "Abrir Asistente de sesión",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun SessionHeroActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = DarkEditorChip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
