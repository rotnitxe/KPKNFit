package com.example.kpkn.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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

class NutritionQuickActionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            QuickActionWidgetContent(context)
        }
    }
}

@Composable
private fun QuickActionWidgetContent(context: Context) {
    val logIntent = KpknDeepLinks.mainActivityIntent(context, "nutrition/action/openFoodLog")
    val searchIntent = KpknDeepLinks.mainActivityIntent(context, "nutrition/action/openSearch")

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0C7A6D), Color(0xFF0A5E55)))
            .padding(12.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_logo_kpkn),
            contentDescription = "Acciones de nutricion",
            modifier = GlanceModifier.size(24.dp),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Nutricion",
            style = TextStyle(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Box(
                modifier = GlanceModifier
                    .padding(horizontal = 4.dp)
                    .background(ColorProvider(Color(0x33FFFFFF), Color(0x26FFFFFF)))
                    .clickable(actionStartActivity(logIntent))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Log",
                    style = TextStyle(fontWeight = FontWeight.Medium),
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .padding(horizontal = 4.dp)
                    .background(ColorProvider(Color(0x33FFFFFF), Color(0x26FFFFFF)))
                    .clickable(actionStartActivity(searchIntent))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Buscar",
                    style = TextStyle(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

class NutritionQuickActionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NutritionQuickActionWidget()
}
