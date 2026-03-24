package com.example.kpkn.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.R
import com.example.kpkn.ui.components.icons.CaupolicanIcon

@Composable
fun HomeHeaderSection(
    modifier: Modifier = Modifier,
    greeting: String,
    userName: String,
    ringsViewMode: HomeViewModel.RingsViewMode,
    onThemeToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onRingsViewChange: (HomeViewModel.RingsViewMode) -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 20.dp)) {
        // Solo el saludo — "Tus RINGS" está en HomeRingsSection
        Text(
            "$greeting,\n$userName!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            lineHeight = 40.sp,
            letterSpacing = (-1).sp,
        )
    }
}

// ─── Header Icons ──────────────────────────────────────────────────────────

@Composable
private fun ThreeRingsHeaderIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val size = this.size
        val r = size.minDimension / 6f
        val s = r * 1.6f
        val cy = size.height / 2f
        val cx = size.width / 2f

        val colors = listOf(Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFFFFD740))
        val positions = listOf(
            Offset(cx - s, cy),
            Offset(cx, cy),
            Offset(cx + s, cy),
        )

        positions.forEachIndexed { i, center ->
            drawCircle(
                colors[i],
                r,
                center,
                style = Stroke(1.2f),
            )
        }
    }
}

@Composable
private fun SingleRingHeaderIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val size = this.size
        val r = size.minDimension / 2.5f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            Color(0xFF448AFF),
            r,
            center,
            style = Stroke(1.2f),
        )
    }
}
