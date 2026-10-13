package com.example.kpkn.screens.body.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.body.BmiCategory
import com.example.kpkn.domain.body.bmiCategory
import com.example.kpkn.domain.body.roundBmi1
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType

/** Cifras de Cuerpo con hoja de explicación: cada una dice cómo se calcula y de dónde sale. */
enum class BodyInfoTopic { MUSCULO, FFMI, IMC, COMPOSICION }

/** Contenido de una hoja de explicación: título, párrafos y fuente. */
data class BodyInfo(
    val title: String,
    val paragraphs: List<String>,
    val source: String,
)

/**
 * Texto de la hoja de [topic]. Los consejos de grasa y de FFMI ya existían en «Consejos» y pasan aquí, sin afirmaciones de salud
 * nuevas. [ffmi] y [bodyFatPercent] eligen el consejo que corresponde a la cifra actual.
 */
internal fun bodyInfoFor(topic: BodyInfoTopic, ffmi: Double?, bodyFatPercent: Double?): BodyInfo = when (topic) {
    BodyInfoTopic.MUSCULO -> BodyInfo(
        title = "% de músculo",
        paragraphs = listOf("Es el porcentaje de tu peso que corresponde a masa muscular, según tu última medición con ese dato."),
        source = "Fuente: tu última medición con % de músculo.",
    )
    BodyInfoTopic.FFMI -> BodyInfo(
        title = "FFMI (índice de masa libre de grasa)",
        paragraphs = listOfNotNull(
            "El FFMI normaliza la masa muscular por altura. Un valor >25 sin sustancias es excepcional.",
            "Es la masa magra dividida entre la altura al cuadrado, normalizada a 1,80 m.",
            ffmi?.let { bodyFfmiTip(it) },
        ),
        source = "Fuente: Kouri et al. (1995). Usa la masa magra de la misma medición que el % de grasa.",
    )
    BodyInfoTopic.IMC -> BodyInfo(
        title = "IMC (índice de masa corporal)",
        paragraphs = listOf(
            "El IMC no distingue masa muscular de grasa. Si entrenas con pesas, el FFMI es una métrica más precisa.",
            "Es el peso en kilos dividido entre la altura en metros al cuadrado. Bajo peso: menos de 18,5. Normal: de 18,5 a 24,9. " +
                "Sobrepeso: de 25 a 29,9. Obesidad: 30 o más.",
        ),
        source = "Fuente: clasificación de la Organización Mundial de la Salud para adultos. Usa tu último pesaje y tu altura.",
    )
    BodyInfoTopic.COMPOSICION -> BodyInfo(
        title = "Composición corporal",
        paragraphs = listOfNotNull(
            "La masa magra es todo lo que no es grasa. Sale del peso y del % de grasa de una misma medición.",
            bodyFatTip(bodyFatPercent),
        ),
        source = "Fuente: tu última medición con peso y % de grasa en la misma sesión.",
    )
}

/** Consejo de grasa corporal, con los mismos rangos y textos que tenía «Consejos». Null si la cifra no tiene consejo. */
internal fun bodyFatTip(bodyFat: Double?): String? = when {
    bodyFat == null -> null
    bodyFat < 10 -> "Tu % de grasa es muy bajo. Mantener niveles < 10% es difícil a largo plazo y puede afectar hormonas."
    bodyFat in 10.0..15.0 -> "Excelente nivel de grasa corporal para rendimiento y estética."
    bodyFat in 15.0..20.0 -> "Buen rango de grasa corporal. Ideal para fases de volumen controlado."
    bodyFat > 25 -> "Considera una fase de déficit para mejorar salud metabólica y composición corporal."
    else -> null
}

/** Consejo de FFMI, con los rangos y textos que tenía «Consejos». Null si la cifra no tiene consejo. */
internal fun bodyFfmiTip(ffmi: Double): String? = when {
    ffmi < 18 -> "Tu FFMI sugiere potencial de crecimiento muscular significativo. Prioriza entrenamiento progresivo."
    ffmi in 20.0..23.0 -> "Muy buen desarrollo muscular. Estás en rango avanzado."
    ffmi > 25 -> "FFMI excepcional. Estás en el tope del potencial muscular natural."
    else -> null
}

/**
 * Categoría del FFMI con los límites que ya usaba la pantalla: Bajo, Promedio, Avanzado, Élite y Excepcional. Un valor entre dos
 * categorías cae en la inferior, como antes.
 */
internal fun bodyFfmiCategory(ffmi: Double): String = when {
    ffmi < 18.0 -> "Bajo"
    ffmi < 20.0 -> "Promedio"
    ffmi < 22.5 -> "Avanzado"
    ffmi < 25.0 -> "Élite"
    else -> "Excepcional"
}

/** Categoría del IMC sobre el valor que se muestra (un decimal), con las 4 categorías continuas del dominio. */
internal fun bodyBmiCategory(bmi: Double): String? = when (bmiCategory(roundBmi1(bmi))) {
    BmiCategory.UNDERWEIGHT -> "Bajo peso"
    BmiCategory.HEALTHY -> "Normal"
    BmiCategory.OVERWEIGHT -> "Sobrepeso"
    BmiCategory.OBESE -> "Obesidad"
    null -> null
}

/** Hoja de explicación de una cifra de Cuerpo. Sin estado: el texto lo escribe quien llama con [bodyInfoFor]. */
@Composable
fun BodyInfoSheet(
    info: BodyInfo,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        BodyInfoSheetContent(info = info, onDismiss = onDismiss)
    }
}

/** Contenido de [BodyInfoSheet] sin la hoja: título, párrafos, fuente y el cierre. */
@Composable
fun BodyInfoSheetContent(
    info: BodyInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Text(
            text = info.title,
            style = KpknType.title,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        info.paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Text(text = paragraph, style = KpknType.body, color = WizardColors.textMuted)
        }
        Spacer(Modifier.height(14.dp))
        Text(text = info.source, style = KpknType.caption, color = WizardColors.textFaint)
        Spacer(Modifier.height(20.dp))
        KpknSecondaryPill(text = "Cerrar", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
