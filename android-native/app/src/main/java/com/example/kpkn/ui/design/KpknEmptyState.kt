package com.example.kpkn.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Estado vacío (KPKN_UI_V2): dentro de un módulo y centrado, con el icono en un círculo `inset` de 56 dp, el título en `bodyStrong`, el
 * texto en `body` y `textMuted` y, si la hay, la [action] debajo. Si [body] es nulo, no se pinta.
 */
@Composable
fun KpknEmptyState(
    icon: ImageVector,
    title: String,
    body: String?,
    action: (@Composable () -> Unit)? = null,
) {
    KpknModule(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(KpknSurface.inset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = WizardColors.textMuted,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = body,
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}
