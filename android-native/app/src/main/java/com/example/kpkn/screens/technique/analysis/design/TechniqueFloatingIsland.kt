package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.technique.analysis.TECHNIQUE_ANALYSIS_MENU_TAG
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import dev.chrisbanes.haze.HazeState

const val TECHNIQUE_ISLAND_TAG = "technique-island"
const val TECHNIQUE_ISLAND_BACK_TAG = "technique-island-back"

private val IslandCream = Color(0xFFF2EEE6)
private val IslandShape = RoundedCornerShape(29.dp)

internal fun analysisTopTitle(exerciseName: String, setInfo: String?): String =
    listOfNotNull(
        exerciseName.takeIf { it.isNotBlank() },
        setInfo?.substringBefore(" · ")?.takeIf { it.startsWith("Serie") }?.lowercase(),
    ).joinToString(" · ").ifBlank { "Técnica" }

internal fun analysisTopSubtitle(setInfo: String?): String? {
    if (setInfo.isNullOrBlank()) return null
    val first = setInfo.substringBefore(" · ")
    return if (first.startsWith("Serie")) {
        setInfo.substringAfter(" · ", "").takeIf { it.isNotBlank() }
    } else {
        setInfo
    }
}

/**
 * Isla flotante superior `.tc-isl` (`height >= 58dp`, `radius = 29dp`) compartida entre la pantalla de resultado
 * y los estados estáticos `.tc-still`.
 */
@Composable
fun TechniqueFloatingIsland(
    exerciseName: String,
    setInfo: String?,
    onBack: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    showMenu: Boolean = false,
    onOpenMenu: () -> Unit = {},
    onMenuAnchorChanged: (Rect) -> Unit = {},
) {
    val title = analysisTopTitle(exerciseName, setInfo)
    val subtitle = analysisTopSubtitle(setInfo)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .kpknGlassPreset(
                hazeState = hazeState,
                preset = KpknGlassPreset.VidrioIsla,
                shape = IslandShape,
                withBorder = true,
                additionalScrim = Color(0x730E0E12),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(TECHNIQUE_ISLAND_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IslandCircleButton(
            icon = KpknLineIcons.ChevronLeft,
            contentDescription = "Volver",
            onClick = onBack,
            modifier = Modifier.testTag(TECHNIQUE_ISLAND_BACK_TAG),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = WizardFonts.display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    lineHeight = 19.sp,
                ),
                color = IslandCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = Color(0x8CFFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showMenu) {
            Spacer(Modifier.width(4.dp))
            IslandCircleButton(
                icon = KpknLineIcons.More,
                contentDescription = "Más opciones",
                onClick = onOpenMenu,
                modifier = Modifier
                    .testTag(TECHNIQUE_ANALYSIS_MENU_TAG)
                    .onGloballyPositioned { onMenuAnchorChanged(it.boundsInWindow()) },
            )
        }
    }
}

@Composable
private fun IslandCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0x16FFFFFF))
                .border(1.dp, Color(0x24FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = IslandCream, modifier = Modifier.size(18.dp))
        }
    }
}
