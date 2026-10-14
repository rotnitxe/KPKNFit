package com.example.kpkn.widgets

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks

// Glance no tiene tokens de Compose: estos tres colores salen de los de KPKN y se fijan aquí.

/** Negro del widget, igual en día y noche: el tinte `#0A0A0A` de las islas de vidrio (KPKN_UI_V2 §2). */
private val WidgetBlack = Color(0xFF0A0A0A)

/** Tinta cálida `#F2EEE6`: `WizardColors.text`. */
private val WidgetInk = Color(0xFFF2EEE6)

/** Blanco al 12 %: el fondo de la píldora «Buscar», la acción secundaria. */
private val WidgetInkSoft = Color(0x1FFFFFFF)

class NutritionQuickActionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            QuickActionWidgetContent(context)
        }
    }
}

/** Radio de esquina del sistema para widgets (Android 12 en adelante); antes de eso, 16 dp. */
private fun widgetCornerModifier(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.cornerRadius(16.dp)
    }

@Composable
private fun QuickActionWidgetContent(context: Context) {
    val logIntent = KpknDeepLinks.mainActivityIntent(context, "nutrition/action/openFoodLog")
    val searchIntent = KpknDeepLinks.mainActivityIntent(context, "nutrition/action/openSearch")

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(widgetCornerModifier())
            .background(ColorProvider(WidgetBlack, WidgetBlack))
            .padding(16.dp),
        horizontalAlignment = Alignment.Horizontal.Start,
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        // Arriba: el símbolo de KPKN y el nombre del módulo, en tinta.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.kpkn_simbolo),
                contentDescription = null,
                modifier = GlanceModifier.width(28.dp).height(18.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "Nutrición",
                style = TextStyle(color = ColorProvider(WidgetInk, WidgetInk), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(modifier = GlanceModifier.height(14.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetPill(
                text = "Registrar",
                icon = R.drawable.ic_kpkn_plus,
                background = WidgetInk,
                foreground = WidgetBlack,
                onClick = actionStartActivity(logIntent),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            WidgetPill(
                text = "Buscar",
                icon = R.drawable.ic_kpkn_search,
                background = WidgetInkSoft,
                foreground = WidgetInk,
                onClick = actionStartActivity(searchIntent),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** Píldora de acción: icono KPKN Line y texto, de 48 dp de alto para tocar con comodidad. */
@Composable
private fun WidgetPill(
    text: String,
    icon: Int,
    background: Color,
    foreground: Color,
    onClick: Action,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .cornerRadius(24.dp)
            .background(ColorProvider(background, background))
            .clickable(onClick)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(foreground, foreground), fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

class NutritionQuickActionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NutritionQuickActionWidget()
}
