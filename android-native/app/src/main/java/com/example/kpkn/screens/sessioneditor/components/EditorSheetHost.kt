package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetSpec
import com.example.kpkn.ui.components.LocalKpknSheetDismiss
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.motion.KpknMotion

/**
 * Cierra la hoja del editor que lo contiene con su salida animada (`closeLayer(cb)` del lienzo) y, al desmontarla, llama a `then`.
 * Fuera de una hoja ejecuta `then` al instante.
 */
internal val LocalEdSheetClose = staticCompositionLocalOf<((() -> Unit)?) -> Unit> { { then -> then?.invoke() } }

/**
 * `.ed-sheet` (Pulido v1 · EDITOR): los valores exactos de sus filas de `ed.md` para `KpknSheet(lienzo = …)`: sombra `hoja` (ed-S07), velo
 * 0,22 s `--ed-ease` (ed-A11) y 0,2 s `ease` al salir (ed-A12), la hoja sube 0,34 s `--ed-ease` (ed-A13) y baja 0,24 s con la curva `cae`
 * (ed-A14). El contenido no se anima aparte (la hoja entera sube) y la altura no cambia.
 */
internal val EdSheetSpec = KpknSheetSpec(
    shadow = KpknShadow.Hoja.spec,
    scrimInMs = 220,
    scrimEasing = KpknMotion.Estandar,
    sheetInMs = 340,
    sheetEasing = KpknMotion.Estandar,
    contentDelayMs = 0,
    contentInMs = 1,
    contentEasing = KpknMotion.Lineal,
    contentRise = 0.dp,
    outMs = 240,
    outEasing = KpknMotion.Cae,
    outTravel = 1f,
    scrimOutMs = 200,
    heightMs = 1,
    heightEasing = KpknMotion.Lineal,
    scrimOutEasing = KpknMotion.Suave,
)

/**
 * `.ed-sheet`: la hoja del editor (#1B1B1D plana, radio 28) sobre `KpknSheet` con [EdSheetSpec], con 20 dp a los lados y 14 de separación.
 * Los botones de dentro cierran con [LocalEdSheetClose] (la hoja baja y después corre lo que confirman).
 */
@Composable
internal fun EdSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    // pulido:ed-S07 .ed-sheet sombra · ed-A11/A12 velo · ed-A13 entrada · ed-A14 salida · ed-J05 closeLayer: la hoja baja y después corre el aviso
    KpknSheet(
        onDismissRequest = {
            afterClose?.invoke()
            onDismiss()
        },
        surfaceColor = EdColors.sheet,
        maxHeightFraction = 0.88f,
        lienzo = EdSheetSpec,
    ) {
        val animatedDismiss = LocalKpknSheetDismiss.current
        CompositionLocalProvider(
            LocalEdSheetClose provides { then ->
                afterClose = then
                if (animatedDismiss != null) animatedDismiss() else {
                    then?.invoke()
                    onDismiss()
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}
