package com.example.kpkn.screens.competitions.wizard

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.competitions.PowerliftingFederation
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.ui.components.KpknSheetTokens

val WizardFieldShape = RoundedCornerShape(14.dp)
val WizardCardShape = RoundedCornerShape(22.dp)
val WizardInk = Color.White.copy(alpha = 0.92f)
val WizardMuted = Color.White.copy(alpha = 0.55f)
val GoldMetal = Color(0xFFC6A35A)
val SilverMetal = Color(0xFFC5C7CC)
val BronzeMetal = Color(0xFFB08A5B)

@Composable
fun WizardPillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(WizardFieldShape)
            .background(KpknSheetTokens.ControlFill)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = KpknSheetTokens.ControlPlaceholder, fontWeight = FontWeight.Medium)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = KpknSheetTokens.ControlLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
            cursorBrush = SolidColor(KpknSheetTokens.ControlLabel),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun WizardPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(WizardCardShape)
            .background(Color.White.copy(alpha = 0.06f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
fun WizardChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = WizardInk,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun WizardStepper(
    valueLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepperButton("–", onMinus)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(WizardFieldShape)
                .background(KpknSheetTokens.ControlFill),
            contentAlignment = Alignment.Center,
        ) {
            Text(valueLabel, color = KpknSheetTokens.ControlLabel, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        StepperButton("+", onPlus)
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(WizardFieldShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = WizardInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FederationMark(
    federation: PowerliftingFederation?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    customLabel: String? = null,
) {
    val context = LocalContext.current
    val color = remember(federation?.colorHex) { parseFederationColor(federation?.colorHex) }
    val bitmap = remember(federation?.logoFile) { loadFederationLogo(context, federation?.logoFile) }
    val letters = federation?.shortName ?: customLabel ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = federation?.name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.matchParentSize().background(color), contentAlignment = Alignment.Center) {
                Text(
                    letters,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = when {
                        letters.length <= 3 -> 15.sp
                        letters.length == 4 -> 12.sp
                        else -> 9.sp
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

fun parseFederationColor(hex: String?): Color {
    val clean = hex.orEmpty().removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return Color(0xFF6B6B6B)
    return Color(0xFF000000 or value)
}

private fun loadFederationLogo(context: Context, fileName: String?) =
    fileName?.let { name ->
        runCatching {
            context.assets.open("${PowerliftingFederationCatalog.LOGO_ASSET_DIR}/$name").use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
