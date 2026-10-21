package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/** Original single-line exercise carousel; step selection lives in the row above the FABs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveExerciseDockPill(name: String, completed: Int, total: Int, selected: Boolean,
    done: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Surface(
        Modifier.height(32.dp).widthIn(max = 260.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = CircleShape,
        color = if (selected) WizardColors.text else androidx.compose.ui.graphics.Color(0xFF161616),
        border = BorderStroke(1.dp, WizardColors.text.copy(alpha = if (selected) 1f else .1f)),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text("${if (done) "✓" else "$completed/$total"} · $name",
                style = KpknType.chip.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                color = if (selected) WizardColors.ctaContent else WizardColors.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
