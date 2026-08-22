package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.db.JointEntity
import com.example.kpkn.data.db.MuscleGroupEntity
import com.example.kpkn.data.db.TendonEntity

internal data class WikiLabPatternInsight(
    val summary: String,
    val setupCues: List<String>,
    val commonErrors: List<String>,
    val mobilityDemands: List<String>,
)

internal data class WikiLabVisualGuide(
    val title: String,
    val summary: String,
    val bullets: List<String>,
    val accent: Color,
    val icon: ImageVector,
)

private val patternInsights = mapOf(
    "horizontal-push" to WikiLabPatternInsight(
        summary = "El vector principal viaja por delante del torso, así que la estabilidad escapular y el control del húmero deciden cuánto empuje útil llega al implemento.",
        setupCues = listOf(
            "Fija escápulas antes de iniciar y deja que el pecho reciba la carga.",
            "Mantén antebrazos casi verticales para no regalar brazo de momento en muñeca y codo.",
            "Aprieta el suelo con pies y glúteos para que el tronco no pierda rigidez.",
        ),
        commonErrors = listOf(
            "Codos muy abiertos que desplazan tensión al hombro anterior.",
            "Perder retracción o apoyo torácico en mitad del recorrido.",
            "Rebote sin control en el punto de máxima elongación.",
        ),
        mobilityDemands = listOf(
            "Extensión torácica funcional.",
            "Rotación externa y control anterior del hombro.",
            "Estabilidad escapular bajo fatiga.",
        ),
    ),
    "horizontal-pull" to WikiLabPatternInsight(
        summary = "Aquí la palanca útil nace en la escápula y se completa con codo y hombro. Si la cintura escapular no lidera, el tirón se vuelve un simple gesto de brazo.",
        setupCues = listOf(
            "Inicia el gesto con depresión y retracción escapular suave.",
            "Mantén costillas abajo para que el tirón no se convierta en extensión lumbar.",
            "Lleva el codo hacia atrás o hacia la cadera según el ángulo del remo.",
        ),
        commonErrors = listOf(
            "Encoger trapecio superior y perder dorsales.",
            "Compensar con balanceo del torso en cada repetición.",
            "Cerrar el rango final antes de completar la escápula.",
        ),
        mobilityDemands = listOf(
            "Control escapulotorácico.",
            "Bisagra o apoyo estable del tronco.",
            "Rotación humeral libre de pinzamiento.",
        ),
    ),
    "vertical-push" to WikiLabPatternInsight(
        summary = "Empujar arriba exige alinear muñeca, codo, hombro y tronco debajo de la carga. Cuanto más limpia la columna, menor fuga de fuerza.",
        setupCues = listOf(
            "Apila costillas sobre pelvis antes de despegar el peso.",
            "Deja que la cabeza pase bajo la carga sin hiperextender la lumbar.",
            "Empuja en línea recta y termina con hombro elevado y escápula rotando hacia arriba.",
        ),
        commonErrors = listOf(
            "Arqueo lumbar para compensar falta de flexión de hombro.",
            "Barra demasiado adelantada, lejos del centro de masa.",
            "Bloqueo final sin rotación escapular suficiente.",
        ),
        mobilityDemands = listOf(
            "Flexión completa de hombro.",
            "Extensión torácica usable.",
            "Capacidad de brace y control glúteo.",
        ),
    ),
    "vertical-pull" to WikiLabPatternInsight(
        summary = "En el tirón vertical la carga tiende a separar el húmero del tronco. La dominada buena es una secuencia de depresión escapular, aducción del brazo y estabilidad del core.",
        setupCues = listOf(
            "Crea tensión desde el agarre antes de despegar.",
            "Baja escápulas y después flexiona codos.",
            "Mantén pelvis estable para no convertir el gesto en un columpio.",
        ),
        commonErrors = listOf(
            "Tirar solo con bíceps desde el inicio.",
            "Perder control excéntrico y caer en cada repetición.",
            "Encoger hombros en la parte alta.",
        ),
        mobilityDemands = listOf(
            "Flexión de hombro sin dolor.",
            "Control escapular en depresión y rotación inferior.",
            "Rigidez del tronco para evitar balanceos.",
        ),
    ),
    "squat" to WikiLabPatternInsight(
        summary = "La sentadilla reparte demanda entre tobillo, rodilla, cadera y tronco. El patrón cambia mucho con antropometría, barra y base, por eso la técnica útil no siempre se ve idéntica.",
        setupCues = listOf(
            "Encuentra una base que te permita profundidad sin colapsar pies ni pelvis.",
            "Respira y bracea antes del descenso.",
            "Desciende manteniendo la barra sobre el mediopié.",
        ),
        commonErrors = listOf(
            "Talones o arco del pie inestables durante el descenso.",
            "Rodillas que colapsan hacia dentro cuando sube la demanda.",
            "Perder rigidez torácica y dejar que la barra se adelante.",
        ),
        mobilityDemands = listOf(
            "Dorsiflexión de tobillo.",
            "Rotación externa de cadera y control pélvico.",
            "Extensión torácica según variante de barra.",
        ),
    ),
    "hinge" to WikiLabPatternInsight(
        summary = "La bisagra separa cadera y columna: la cadera se mueve, la espalda transmite. Cuando ese reparto se pierde, el patrón deja de cargar al posterior y sube el coste lumbar.",
        setupCues = listOf(
            "Lleva la cadera atrás sin abandonar la presión del pie completo.",
            "Mantén el implemento cerca del cuerpo para acortar el brazo de momento.",
            "Piensa en cerrar costillas y pelvis antes de iniciar.",
        ),
        commonErrors = listOf(
            "Flexionar lumbar al buscar más rango.",
            "Alejar la carga del cuerpo durante la fase dura.",
            "Iniciar con rodilla o espalda en vez de con cadera.",
        ),
        mobilityDemands = listOf(
            "Longitud funcional de isquiosurales.",
            "Control de columna neutra bajo tensión.",
            "Tolerancia de agarre y dorsal para fijar la carga.",
        ),
    ),
    "lunge" to WikiLabPatternInsight(
        summary = "La estocada desafía control frontal y sagital a la vez. Es un patrón excelente para repartir carga entre piernas, pero castiga rápido la pérdida de equilibrio y alineación.",
        setupCues = listOf(
            "Crea una zancada que te permita bajar vertical y estable.",
            "Mantén pelvis cuadrada durante el descenso.",
            "Empuja el suelo con la pierna adelantada para volver.",
        ),
        commonErrors = listOf(
            "Paso demasiado corto que amontona carga en la rodilla delantera.",
            "Torso colapsado o inclinado sin intención.",
            "Inestabilidad frontal del pie y la cadera.",
        ),
        mobilityDemands = listOf(
            "Flexión de cadera unilateral.",
            "Extensión de cadera de la pierna retrasada.",
            "Estabilidad de pie, rodilla y glúteo medio.",
        ),
    ),
    "extension" to WikiLabPatternInsight(
        summary = "Los patrones de extensión generan fuerza alejando segmentos desde flexión previa. La clave es abrir donde toca sin convertirlo en extensión lumbar indiscriminada.",
        setupCues = listOf(
            "Define la articulación que quieres extender antes de iniciar.",
            "Mantén el tronco como base si la extensión es periférica.",
            "Busca recorrido activo, no solo velocidad.",
        ),
        commonErrors = listOf(
            "Compensar extensión de cadera con arco lumbar.",
            "Bloquear la repetición con rebote pasivo.",
            "Perder tensión en la fase excéntrica.",
        ),
        mobilityDemands = listOf(
            "Capacidad de extender sin dolor la articulación objetivo.",
            "Control del core para que la columna no robe movimiento.",
            "Tolerancia tendinosa al punto final del gesto.",
        ),
    ),
    "anti-extension" to WikiLabPatternInsight(
        summary = "Anti-extensión no significa inmovilidad absoluta; significa resistir que la caja torácica se abra y la pelvis se desordene cuando la carga intenta arquearte.",
        setupCues = listOf(
            "Apila costillas sobre pelvis antes de empezar.",
            "Respira sin perder presión abdominal circumferencial.",
            "Piensa en alargar el cuerpo mientras resistes la carga.",
        ),
        commonErrors = listOf(
            "Confundir brace con apnea rígida y perder control fino.",
            "Ceder la pelvis en anteversión cuando aumenta el brazo de palanca.",
            "Reducir el ejercicio a hombros o flexores de cadera.",
        ),
        mobilityDemands = listOf(
            "Control lumbopélvico.",
            "Capacidad de hombro si el patrón es por encima de la cabeza.",
            "Tolerancia del core a tensión sostenida.",
        ),
    ),
    "anti-rotation" to WikiLabPatternInsight(
        summary = "La carga intenta girarte; tu tarea es dejar pasar fuerza sin que el tronco se retuerza. Cuanto mejor se organizan pies, cadera y parrilla costal, más limpio sale el patrón.",
        setupCues = listOf(
            "Enraíza pies y glúteos antes de recibir la tensión lateral.",
            "Mantén esternón y pelvis mirando al frente.",
            "Respira corto y controlado para no perder el cilindro.",
        ),
        commonErrors = listOf(
            "Rotar hombros aunque la pelvis siga quieta.",
            "Buscar tensión solo con brazos y no con el tronco.",
            "Compensar con desplazamientos laterales del cuerpo.",
        ),
        mobilityDemands = listOf(
            "Estabilidad frontal de cadera.",
            "Control oblicuo y serrato.",
            "Alineación torácica sin rigidez excesiva.",
        ),
    ),
    "rotation" to WikiLabPatternInsight(
        summary = "Rotar bien es repartir giro entre pies, caderas, columna torácica y hombros. Cuando una región se queda atrás, otra suele excederse.",
        setupCues = listOf(
            "Define desde dónde quieres producir la rotación principal.",
            "Permite al pie y a la cadera acompañar si el gesto es atlético.",
            "Controla la desaceleración igual que la aceleración.",
        ),
        commonErrors = listOf(
            "Rotar lumbar en exceso por falta de cadera o tórax.",
            "Iniciar demasiado rápido y perder línea de fuerza.",
            "Bloquear pies por completo en patrones que piden transferencia.",
        ),
        mobilityDemands = listOf(
            "Rotación torácica.",
            "Rotación interna y externa de cadera.",
            "Capacidad de desaceleración del core.",
        ),
    ),
    "carry" to WikiLabPatternInsight(
        summary = "Las cargas caminadas son una prueba de transmisión de fuerza. El valor está en sostener postura, respiración y simetría mientras la base cambia paso a paso.",
        setupCues = listOf(
            "Agarra y apila antes de dar el primer paso.",
            "Camina con pasos cortos y silenciosos.",
            "Mantén costillas y pelvis estables mientras la carga te intenta inclinar.",
        ),
        commonErrors = listOf(
            "Marchar demasiado rápido y perder control del tronco.",
            "Llevar hombro elevado o escápula inestable bajo carga.",
            "Compensar con inclinación lateral marcada.",
        ),
        mobilityDemands = listOf(
            "Grip y estabilidad escapular.",
            "Resistencia postural del core.",
            "Control unilateral de cadera y pie.",
        ),
    ),
    "jump" to WikiLabPatternInsight(
        summary = "El salto combina producción rápida de fuerza y amortiguación. No basta con despegar alto; importa también cómo recibes y redistribuyes la carga al caer.",
        setupCues = listOf(
            "Carga el patrón desde pies, cadera y brazos si la variante lo permite.",
            "Despega proyectando fuerza al suelo, no solo elevando rodillas.",
            "Amortigua con tobillo, rodilla y cadera al aterrizar.",
        ),
        commonErrors = listOf(
            "Aterrizajes ruidosos y rígidos.",
            "Valgo dinámico o colapso del pie en la recepción.",
            "Usar solo rodilla para frenar sin ayuda de cadera.",
        ),
        mobilityDemands = listOf(
            "Elasticidad de tobillo y pie.",
            "Capacidad de absorber fuerza en rodilla y cadera.",
            "Rigidez reactiva del tendón de Aquiles y complejo posterior.",
        ),
    ),
)

@Composable
internal fun WikiLabInsightCard(
    title: String,
    accent: Color,
    icon: ImageVector,
    summary: String,
    bullets: List<String> = emptyList(),
    footer: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(APRENDE_MUTED_FILL)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = accent)
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = Color.White.copy(alpha = 0.8f),
                ),
            )
        }

        Text(
            summary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 18.sp,
            ),
        )

        bullets.forEach { bullet ->
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(7.dp),
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.72f),
                ) {}
                Spacer(Modifier.width(10.dp))
                Text(
                    bullet,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 18.sp,
                    ),
                )
            }
        }

        footer?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

internal fun patternInsightFor(patternId: String): WikiLabPatternInsight? = patternInsights[patternId]

internal fun buildMuscleGuide(muscle: MuscleGroupEntity): WikiLabVisualGuide {
    val accent = wikiLabBodyPartAccent(muscle.bodyPart)
    return when {
        muscle.id.startsWith("pectoral") -> WikiLabVisualGuide(
            title = "Qué Mirar",
            summary = "En el pectoral importa ver si el hombro sigue una trayectoria limpia y si la caja torácica le da una base estable para empujar o aproximar el brazo.",
            bullets = listOf(
                "Busca si el húmero se acerca al tronco sin hombros adelantados en exceso.",
                "Diferencia si el trabajo viene del pectoral superior, medio o inferior según el ángulo.",
                "Si el codo domina demasiado, el gesto suele migrar a tríceps y deltoides.",
            ),
            accent = accent,
            icon = Icons.Default.Visibility,
        )

        muscle.id.contains("trapecio") || muscle.id.contains("romboides") || muscle.id.contains("dorsal") || muscle.id == "espalda" -> WikiLabVisualGuide(
            title = "Lectura Visual",
            summary = "La espalda rara vez se entiende por un solo plano. Lo útil es mirar si la escápula se mueve con intención y si el tronco sostiene la trayectoria sin balanceos.",
            bullets = listOf(
                "Depresión y retracción no son lo mismo; observa cuál de las dos falla primero.",
                "Un dorsal dominante suele llevar el codo hacia la cadera, no solo hacia atrás.",
                "Cuando el tronco se mueve de más, la espalda deja de ser el motor principal.",
            ),
            accent = accent,
            icon = Icons.Default.Insights,
        )

        muscle.id.contains("deltoides") || muscle.id == "hombros" -> WikiLabVisualGuide(
            title = "Lectura Visual",
            summary = "El hombro se ve mejor por trayectorias que por volumen. Observa si la escápula acompaña y si cada porción del deltoides recibe tensión donde corresponde.",
            bullets = listOf(
                "Deltoides anterior: control del brazo por delante del torso.",
                "Deltoides lateral: separación limpia del brazo sin encoger trapecio.",
                "Deltoides posterior: extensión y abducción horizontal con escápula estable.",
            ),
            accent = accent,
            icon = Icons.Default.Visibility,
        )

        muscle.id.contains("bíceps") || muscle.id == "brazos" || muscle.id.contains("tríceps") || muscle.id.contains("antebrazo") -> WikiLabVisualGuide(
            title = "Aplicación Práctica",
            summary = "En brazos conviene distinguir si el músculo mueve, asiste o solo estabiliza. Esa diferencia cambia mucho la elección del ejercicio y el volumen útil.",
            bullets = listOf(
                "El bíceps gana cuando el codo flexiona con hombro estable y supinación real.",
                "El tríceps destaca cuando el codo extiende sin que el torso robe la tarea.",
                "El antebrazo suele limitar por agarre antes que por fatiga visible.",
            ),
            accent = accent,
            icon = Icons.Default.AutoAwesome,
        )

        muscle.id.contains("glúte") || muscle.id.contains("isquio") || muscle.id.contains("cuádr") || muscle.id.contains("aductor") || muscle.id == "piernas" || muscle.id.contains("pantorr") -> WikiLabVisualGuide(
            title = "Qué Mirar",
            summary = "En tren inferior conviene leer el reparto de carga entre pie, rodilla y cadera. Si una zona pierde alineación, otra suele absorber el coste mecánico.",
            bullets = listOf(
                "Cuádriceps: cuánto avanza la rodilla y cuánta estabilidad mantiene el pie.",
                "Glúteos e isquios: si la cadera lidera o solo acompaña al movimiento.",
                "Pantorrillas: calidad del apoyo y rigidez reactiva en el tobillo.",
            ),
            accent = accent,
            icon = Icons.Default.Insights,
        )

        muscle.id.contains("abdomen") || muscle.id == "core" || muscle.id.contains("erectores") -> WikiLabVisualGuide(
            title = "Aplicación Práctica",
            summary = "El core no siempre se ve por movimiento, sino por ausencia de movimiento no deseado. Lo más útil es mirar si costillas, pelvis y presión interna se mantienen coordinadas.",
            bullets = listOf(
                "Anti-extensión: evita abrir costillas o exagerar la lordosis.",
                "Anti-rotación: mira si hombros y pelvis siguen la misma dirección.",
                "Erectores: sostienen y transfieren, pero no deberían reemplazar a la cadera.",
            ),
            accent = accent,
            icon = Icons.Default.Bolt,
        )

        else -> WikiLabVisualGuide(
            title = "Aplicación Práctica",
            summary = "La mejor lectura visual es comprobar qué articulaciones mueve este músculo y dónde debería sentirse la carga cuando la ejecución es estable.",
            bullets = listOf(
                "Observa si el movimiento sucede en las articulaciones que el músculo cruza.",
                "Compara lado derecho e izquierdo cuando el gesto es unilateral.",
                "Si la tensión se va a otra región, la técnica o la selección probablemente no encaja.",
            ),
            accent = accent,
            icon = Icons.Default.Visibility,
        )
    }
}

internal fun buildJointGuide(joint: JointEntity): WikiLabVisualGuide {
    return when (joint.type) {
        "ball-socket" -> WikiLabVisualGuide(
            title = "Lectura Articular",
            summary = "Las articulaciones esferoideas ganan libertad a costa de control. Lo importante no es solo cuánto se mueven, sino cómo centran la cabeza articular durante la carga.",
            bullets = listOf(
                "Busca si hay rotación y traslación limpias, no solo rango.",
                "Escápula y caja torácica suelen decidir el hombro más que el húmero solo.",
                "Cuando falla el control, aparecen pinzamientos o compensaciones rápidas.",
            ),
            accent = APRENDE_LINK_COLOR,
            icon = Icons.Default.Visibility,
        )

        "hinge" -> WikiLabVisualGuide(
            title = "Lectura Articular",
            summary = "Las bisagras viven mejor cuando la fuerza entra alineada. Tolera mucha carga, pero castiga pronto el colapso frontal o la pérdida del eje principal.",
            bullets = listOf(
                "Revisa si la línea pie-rodilla-cadera sigue siendo clara bajo fatiga.",
                "Demasiada traslación o valgo suele indicar que otra región dejó de ayudar.",
                "La carga protectora suele venir de progresar control y tolerancia, no de inmovilizar.",
            ),
            accent = APRENDE_LINK_COLOR,
            icon = Icons.Default.Insights,
        )

        else -> WikiLabVisualGuide(
            title = "Lectura Articular",
            summary = "Esta articulación suele ser un punto de transferencia. Lo útil es observar si deja pasar movimiento y fuerza sin convertirse en el cuello de botella del patrón.",
            bullets = listOf(
                "Pregunta si está guiando el gesto o solo adaptándose a la región vecina.",
                "Compara movilidad disponible con estabilidad bajo carga.",
                "Las molestias repetidas suelen venir de exceso o defecto de movimiento relativo.",
            ),
            accent = APRENDE_LINK_COLOR,
            icon = Icons.Default.Visibility,
        )
    }
}

internal fun buildTendonGuide(tendon: TendonEntity): WikiLabVisualGuide {
    return WikiLabVisualGuide(
        title = "Manejo de Carga",
        summary = "Los tendones responden mejor a progresiones consistentes que a picos heroicos. Más que buscar sensaciones, conviene leer tolerancia a carga, irritabilidad al día siguiente y calidad del patrón.",
        bullets = listOf(
            "Dolor estable y tolerable durante la sesión suele ser más manejable que dolor creciente.",
            "La señal útil es cómo responde 24 a 48 horas después, no solo al terminar.",
            "Isométricos, tempo y rango parcial suelen servir como escalones antes de volver al gesto completo.",
        ),
        accent = APRENDE_LINK_COLOR,
        icon = Icons.Default.Healing,
    )
}

internal fun recommendedExercisesForMuscle(
    muscle: MuscleGroupEntity,
    limit: Int = 6,
): List<WikiLabExerciseLink> = catalogExercisesForMuscle(muscle.id, limit)

private fun wikiLabBodyPartAccent(bodyPart: String?): Color = when (bodyPart) {
    "upper", "lower", "core", "spine" -> APRENDE_LINK_COLOR
    else -> Color(0xFF7F8D96)
}
