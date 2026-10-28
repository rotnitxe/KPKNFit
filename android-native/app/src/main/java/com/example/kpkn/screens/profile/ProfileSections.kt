package com.example.kpkn.screens.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.domain.training.ExerciseProgressChoice
import com.example.kpkn.domain.training.NutritionGoalProgress
import com.example.kpkn.domain.training.StarredExerciseProgress
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.technique.hub.design.TechniqueRecentStrip
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknSurface
import java.util.Locale

/** Tokens del perfil: los del prototipo aprobado (`docs/design/profile-concepts-v1/src/styles.css`). */
internal object ProfileTokens {
    val Ink = WizardColors.text
    val Ink2 = Color(0xFFDCD7CE)
    val Muted = Color(0xFFB9B5AE)
    val Faint = WizardColors.textFaint
    val Dim = Color(0xFF6F6D69)
    val Module = KpknSurface.module
    val Inset = KpknSurface.inset
    val Line = Color(0x12FFFFFF)
    val Track = Color(0xFF262626)
    val Star = Color(0xFFE2C27A)
    val Danger = Color(0xFFFF9B92)
    val ModuleShape = RoundedCornerShape(22.dp)
    val GlassBrush = Brush.linearGradient(listOf(Color(0xCC2A2A2A), Color(0xC0161616)))
}

internal object ProfileType {
    val eyebrow = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp)
    val rowTitle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp)
    val rowSub = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp, lineHeight = 18.sp)
    val body = TextStyle(fontFamily = WizardFonts.body, fontSize = 14.sp, lineHeight = 20.sp)
    val action = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    fun syne(size: Int, weight: FontWeight = FontWeight.Bold) = TextStyle(
        fontFamily = WizardFonts.display, fontWeight = weight, fontSize = size.sp, lineHeight = (size + 3).sp,
        letterSpacing = (-0.03 * size).sp, fontFeatureSettings = "'lnum' 1, 'tnum' 1",
    )
}

/** Botón redondo de vidrio de 44 dp (atrás, Ajustes), sobre el desenfoque de la app. */
@Composable
internal fun ProfileGlassButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(44.dp)
            .kpknGlassOrFallback(LocalHazeState.current, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = ProfileTokens.Ink)
    }
}

/** Sección de la página: rótulo pequeño en mayúsculas (con icono opcional) y su contenido. */
@Composable
internal fun ProfileSection(label: String, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
        Row(Modifier.padding(start = 2.dp, bottom = 10.dp).semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, Modifier.size(15.dp), tint = ProfileTokens.Faint)
                Spacer(Modifier.width(8.dp))
            }
            Text(label.uppercase(), style = ProfileType.eyebrow, color = ProfileTokens.Faint)
        }
        content()
    }
}

/** Módulo carbón con filete, como `.module` del prototipo. */
@Composable
internal fun ProfileModule(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ProfileTokens.ModuleShape)
            .background(ProfileTokens.Module)
            .border(1.dp, ProfileTokens.Line, ProfileTokens.ModuleShape),
        content = content,
    )
}

private fun Modifier.profileRowLine(show: Boolean): Modifier = if (!show) this else drawBehind {
    drawLine(ProfileTokens.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

/** Fila tocable de un módulo: elemento inicial, título, texto y, si se pide, chevrón. */
@Composable
internal fun ProfileRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { ProfileChevron() },
) {
    Row(
        modifier
            .fillMaxWidth()
            .profileRowLine(divider)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = ProfileType.rowTitle, color = ProfileTokens.Ink)
            subtitle?.let { Text(it, Modifier.padding(top = 3.dp), style = ProfileType.rowSub, color = ProfileTokens.Muted) }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun ProfileChevron() = Icon(KpknLineIcons.ChevronRight, null, Modifier.size(18.dp), tint = ProfileTokens.Dim)

/** Círculo tonal de 38 dp con un icono de línea. */
@Composable
internal fun ProfileIconBubble(icon: ImageVector, tint: Color = ProfileTokens.Ink2, size: Int = 38) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(ProfileTokens.Inset), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.47f).dp), tint = tint)
    }
}

// ─── Héroe ───────────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ProfileHero(settings: Settings, avatar: Bitmap?, onAvatar: () -> Unit, onEdit: () -> Unit) {
    val name = profileDisplayName(settings)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(112.dp)
                .clickable(role = Role.Button, onClick = onAvatar)
                .semantics { contentDescription = if (avatar != null) "Foto de perfil: cambiar o quitar" else "Elegir foto de perfil" }
                .testTag("profile-avatar"),
        ) {
            Box(
                Modifier.fillMaxSize().border(1.5.dp, ProfileTokens.Ink.copy(alpha = 0.25f), CircleShape).padding(5.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF5B554C), Color(0xFF2B2825), Color(0xFF1A1917)))),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    Image(avatar.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else if (name != null) {
                    Text(profileInitials(settings.username), style = ProfileType.syne(34, FontWeight.ExtraBold), color = ProfileTokens.Ink)
                } else {
                    // Sin nombre todavía: una silueta, no la «U» de «Usuario».
                    Icon(KpknLineIcons.Person, null, Modifier.size(42.dp), tint = ProfileTokens.Ink.copy(alpha = 0.8f))
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).offset((-2).dp, (-4).dp).size(34.dp)
                    .border(3.dp, Color.Black, CircleShape).padding(2.dp).clip(CircleShape).background(ProfileTokens.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Camera, null, Modifier.size(16.dp), tint = Color(0xFF0B0B0B))
            }
        }
        Text(
            name ?: "Añade tu nombre",
            Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
            style = ProfileType.syne(29, FontWeight.ExtraBold).copy(lineHeight = 32.sp),
            color = ProfileTokens.Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            if (avatar == null) "Personaliza tu foto y tus datos" else "Tu perfil personal",
            style = ProfileType.rowSub, color = ProfileTokens.Faint, textAlign = TextAlign.Center,
        )
        ProfilePill("Editar perfil", KpknLineIcons.Edit, onEdit, Modifier.padding(top = 14.dp).testTag("profile-edit"))
        ProfileBodyFicha(settings, onEdit, Modifier.padding(top = 22.dp))
    }
}

/** Píldora secundaria con filete, como `.secondary` del prototipo. */
@Composable
internal fun ProfilePill(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, null, Modifier.size(15.dp), tint = ProfileTokens.Ink)
            Spacer(Modifier.width(7.dp))
        }
        // Sin TextAlign.Center: con ancho al contenido, Compose maqueta el párrafo al ancho máximo y desplaza los glifos fuera de su caja.
        Text(text, style = ProfileType.action, color = ProfileTokens.Ink)
    }
}

/** Ficha de vidrio con estatura, peso y sexo. Cada celda abre el editor; si falta el dato, ofrece «Añadir». */
@Composable
private fun ProfileBodyFicha(settings: Settings, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val vitals = settings.userVitals
    val unit = settings.weightUnit.label()
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(ProfileTokens.ModuleShape)
            .background(ProfileTokens.GlassBrush)
            .border(1.dp, Color(0x1CFFFFFF), ProfileTokens.ModuleShape)
            .testTag("profile-body-ficha"),
    ) {
        FichaCell("Estatura", vitals.height?.let { it.formatDisplay() }, "cm", onEdit, divider = false)
        FichaCell("Peso", vitals.weight?.let { displayWeight(it, settings.weightUnit).formatDisplay() }, unit, onEdit, divider = true)
        FichaCell("Sexo", vitals.gender?.label(), null, onEdit, divider = true, word = true)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FichaCell(
    label: String,
    value: String?,
    unit: String?,
    onClick: () -> Unit,
    divider: Boolean,
    word: Boolean = false,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .drawBehind {
                if (divider) drawLine(ProfileTokens.Line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
            }
            .clickable(role = Role.Button, onClickLabel = if (value == null) "Añadir $label" else "Editar $label", onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(horizontal = 6.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Text(label.uppercase(), style = ProfileType.eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.4.sp), color = ProfileTokens.Faint, textAlign = TextAlign.Center)
        when {
            value == null -> Text(
                "Añadir", style = ProfileType.action.copy(textDecoration = TextDecoration.Underline), color = ProfileTokens.Ink,
                textAlign = TextAlign.Center,
            )
            word -> Text(value, style = ProfileType.rowTitle.copy(fontSize = 14.sp), color = ProfileTokens.Ink, textAlign = TextAlign.Center)
            else -> Text(
                buildAnnotatedString {
                    append(value)
                    unit?.let {
                        withStyle(SpanStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = ProfileTokens.Muted, letterSpacing = 0.sp)) {
                            append(" $it")
                        }
                    }
                },
                style = ProfileType.syne(21), color = ProfileTokens.Ink, textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Ejercicios estrella ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun StarredExercisesModule(exercises: List<StarredExerciseProgress>, onOpen: (String?) -> Unit) {
    ProfileModule {
        if (exercises.isEmpty()) {
            ProfileHint("Marca ejercicios con estrella desde tus sesiones para seguir aquí su RM y objetivo.")
        } else {
            exercises.forEachIndexed { index, exercise ->
                val fraction = exercise.progressFraction
                val percent = fraction?.let { (it * 100).toInt() }
                val goal = exercise.goal1RM?.let { "Meta ${it.formatDisplay()} kg" } ?: "Sin meta RM"
                val detail = when {
                    percent != null -> "$goal · $percent % hacia la meta"
                    exercise.bestEstimated1RM == null -> "$goal · Sin registros todavía"
                    else -> "$goal · Registra una marca y define una meta para ver el avance."
                }
                ProfileRow(
                    title = exercise.name,
                    subtitle = detail,
                    onClick = { onOpen(exercise.key) },
                    divider = index > 0,
                    leading = {
                        if (fraction != null) ProgressRing(fraction) else ProfileIconBubble(KpknLineIcons.Star, ProfileTokens.Star)
                    },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(exercise.bestEstimated1RM?.formatDisplay() ?: "—", style = ProfileType.syne(19), color = ProfileTokens.Ink)
                            Text("kg eRM", Modifier.padding(top = 3.dp), style = ProfileType.rowSub.copy(fontSize = 11.sp), color = ProfileTokens.Faint)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float) {
    Box(Modifier.size(46.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2 + 1.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(ProfileTokens.Track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(ProfileTokens.Ink, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${(fraction * 100).toInt()}%", style = ProfileType.action.copy(fontSize = 11.sp), color = ProfileTokens.Ink)
    }
}

// ─── Meta corporal ───────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun NutritionGoalModule(progress: NutritionGoalProgress?) {
    ProfileModule {
        when {
            progress == null -> ProfileHint("Crea una meta corporal para empezar a seguir tu avance.")
            progress.currentValue == null -> ProfileHint("Aún no hay una medición reciente para esta meta.")
            progress.targetValue == null -> ProfileHint("Tu plan todavía no tiene un objetivo corporal definido.")
            else -> Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(progress.metric.label(), style = ProfileType.syne(18), color = ProfileTokens.Ink)
                        Text("Desde tu plan de nutrición activo", Modifier.padding(top = 4.dp), style = ProfileType.rowSub, color = ProfileTokens.Muted)
                    }
                    progress.percent?.let { percent ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                buildAnnotatedString {
                                    append("$percent")
                                    withStyle(SpanStyle(fontSize = 19.sp)) { append("%") }
                                },
                                style = ProfileType.syne(34, FontWeight.ExtraBold), color = ProfileTokens.Ink,
                            )
                            Text("completado", style = ProfileType.rowSub.copy(fontSize = 11.sp), color = ProfileTokens.Faint)
                        }
                    }
                }
                progress.percent?.let { GoalTrack(it / 100f, Modifier.padding(top = 22.dp, start = 8.dp, end = 8.dp)) }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GoalValue("Inicio", progress.startValue, progress.metric, Alignment.Start)
                    GoalValue("Ahora", progress.currentValue, progress.metric, Alignment.CenterHorizontally)
                    GoalValue("Meta", progress.targetValue, progress.metric, Alignment.End)
                }
                if (progress.percent == null) {
                    Text(
                        "Registra un valor inicial para calcular el porcentaje.", Modifier.padding(top = 12.dp),
                        style = ProfileType.rowSub, color = ProfileTokens.Muted,
                    )
                }
            }
        }
    }
}

/** Pista Inicio → Ahora → Meta: punto de inicio gris, anillo de la meta y el punto actual en tinta con halo. */
@Composable
private fun GoalTrack(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(18.dp).clearAndSetSemantics {}) {
        val y = size.height / 2
        val h = 6.dp.toPx()
        drawRoundRect(ProfileTokens.Track, Offset(0f, y - h / 2), androidx.compose.ui.geometry.Size(size.width, h),
            androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawRoundRect(
            Brush.horizontalGradient(listOf(ProfileTokens.Ink.copy(alpha = 0.33f), ProfileTokens.Ink), 0f, size.width * f.coerceAtLeast(0.01f)),
            Offset(0f, y - h / 2), androidx.compose.ui.geometry.Size(size.width * f, h), androidx.compose.ui.geometry.CornerRadius(h / 2),
        )
        drawCircle(Color(0xFF555555), 4.dp.toPx(), Offset(0f, y))
        drawCircle(ProfileTokens.Ink, 5.dp.toPx(), Offset(size.width, y), style = Stroke(2.dp.toPx()))
        drawCircle(ProfileTokens.Ink.copy(alpha = 0.12f), 13.dp.toPx(), Offset(size.width * f, y))
        drawCircle(ProfileTokens.Ink, 8.dp.toPx(), Offset(size.width * f, y))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.GoalValue(label: String, value: Double?, metric: GoalMetric, align: Alignment.Horizontal) {
    Column(Modifier.weight(1f), horizontalAlignment = align) {
        Text(label.uppercase(), style = ProfileType.eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.2.sp), color = ProfileTokens.Faint)
        Text(
            value?.let { "${it.formatDisplay()} ${metric.unitLabel()}" } ?: "—",
            Modifier.padding(top = 4.dp), style = ProfileType.syne(16), color = ProfileTokens.Ink,
        )
    }
}

// ─── Progreso ────────────────────────────────────────────────────────────────────────────────────────────────────────

/** Bloque «Progreso»: entradas a las vistas de avance y los ejercicios más entrenados. Las vistas nuevas se añaden al final. */
@Composable
internal fun ProfileProgressModule(
    onOpenMuscleVolume: () -> Unit,
    onOpenExerciseProgress: (String?) -> Unit,
    exercises: List<ExerciseProgressChoice>,
) {
    ProfileModule {
        ProfileRow(
            "Volumen por músculo",
            "Series registradas por músculo en 1, 4 o 12 semanas, con los ejercicios que las explican.",
            onOpenMuscleVolume,
            leading = { ProfileIconBubble(KpknLineIcons.Layers) },
        )
        ProfileRow(
            "Progreso por ejercicio",
            "Métricas, récords y sesiones de tus ejercicios más entrenados.",
            { onOpenExerciseProgress(null) },
            divider = true,
            leading = { ProfileIconBubble(KpknLineIcons.TrendUp) },
        )
        if (exercises.isNotEmpty()) {
            Text(
                "TUS MÁS ENTRENADOS",
                Modifier.fillMaxWidth().profileRowLine(true).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 2.dp),
                style = ProfileType.eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.4.sp), color = ProfileTokens.Faint,
            )
            exercises.forEachIndexed { index, exercise ->
                ProfileRow(
                    exercise.name,
                    "${exercise.sessions} sesiones · ${exercise.configuration}",
                    { onOpenExerciseProgress(exercise.id) },
                    divider = index > 0,
                    leading = {
                        Text("${index + 1}", Modifier.width(38.dp), style = ProfileType.syne(16), color = ProfileTokens.Faint, textAlign = TextAlign.Center)
                    },
                )
            }
        }
    }
}

// ─── Técnica en vídeo ────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ProfileTechniqueCard(
    state: ProfileTechniqueUiState,
    onOpenTechnique: () -> Unit,
    onOpenTechniqueAnalysis: (String) -> Unit = {},
) {
    ProfileModule {
        if (state.recentVideos.isEmpty() && state.analyzedVideoCount == 0) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                Text("Analiza tus series y sigue las tendencias de tu técnica.", style = ProfileType.body, color = ProfileTokens.Ink)
                Text(
                    "Graba una serie desde el Modo visual de la sesión o analiza un vídeo de tu galería",
                    Modifier.padding(top = 6.dp), style = ProfileType.rowSub, color = ProfileTokens.Muted,
                )
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
                // Tres miniaturas a todo el ancho (cada una reserva 8 dp para su insignia y hay 4 dp entre ellas).
                val thumb = ((maxWidth - 8.dp - 24.dp) / 3).coerceIn(56.dp, 112.dp)
                TechniqueRecentStrip(state.recentVideos.take(3), thumb, onOpenTechniqueAnalysis)
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                Text(state.summary, Modifier.testTag("profile-technique-summary"), style = ProfileType.rowSub, color = ProfileTokens.Muted)
                state.conclusion?.let { conclusion ->
                    Text(conclusion, Modifier.padding(top = 6.dp).testTag("profile-technique-conclusion"), style = ProfileType.body, color = ProfileTokens.Ink)
                }
            }
        }
        ProfilePill(
            "Abrir técnica en vídeo", KpknLineIcons.Camera, onOpenTechnique,
            Modifier.fillMaxWidth().padding(14.dp).heightIn(min = 48.dp).testTag("profile-technique-open")
                .semantics { contentDescription = "Abrir técnica en vídeo" },
        )
    }
}

// ─── Archivo ─────────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ProfileArchiveTiles(onOpenAlbums: () -> Unit, onOpenHabits: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProfileAlbumsCard(onOpenAlbums, Modifier.weight(1f).fillMaxHeight())
        ProfileTile(
            "Hábitos", "Rachas de comidas, pesaje y entrenos, con su calendario del último año.", "Ver hábitos",
            KpknLineIcons.Calendar, onOpenHabits, Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
internal fun ProfileAlbumsCard(onOpenAlbums: () -> Unit, modifier: Modifier = Modifier) {
    ProfileTile(
        "Álbumes de entrenamiento", "Fotos y vídeos de tus series, agrupados por sesión.", "Ver álbumes",
        KpknLineIcons.Camera, onOpenAlbums, modifier,
    )
}

@Composable
private fun ProfileTile(title: String, description: String, action: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(ProfileTokens.ModuleShape)
            .background(ProfileTokens.Module)
            .border(1.dp, ProfileTokens.Line, ProfileTokens.ModuleShape)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
            .heightIn(min = 140.dp)
            .padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 16.dp),
        // SpaceBetween y no un Spacer con peso: con altura fija (pareja de baldosas) la acción baja al fondo; suelta, la
        // baldosa mide su contenido en vez de estirarse hasta el alto máximo disponible.
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileIconBubble(icon)
            Text(title, style = ProfileType.syne(16), color = ProfileTokens.Ink)
            Text(description, style = ProfileType.rowSub.copy(fontSize = 12.sp, lineHeight = 17.sp), color = ProfileTokens.Muted)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(action, style = ProfileType.action, color = ProfileTokens.Ink)
            Icon(KpknLineIcons.ChevronRight, null, Modifier.padding(start = 4.dp).size(14.dp), tint = ProfileTokens.Ink)
        }
    }
}

@Composable
internal fun ProfileHint(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(16.dp), style = ProfileType.body, color = ProfileTokens.Muted)
}

// ─── Formato ─────────────────────────────────────────────────────────────────────────────────────────────────────────

internal fun profileInitials(username: String): String =
    username.trim().takeUnless { it == "Usuario" }.orEmpty().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "U" }

internal fun Gender.label(): String = when (this) {
    Gender.MALE -> "Masculino"
    Gender.FEMALE -> "Femenino"
    Gender.OTHER -> "Otro"
}

internal fun WeightUnit.label(): String = when (this) {
    WeightUnit.KG -> "kg"
    WeightUnit.LBS -> "lb"
}

private fun GoalMetric.label(): String = when (this) {
    GoalMetric.WEIGHT -> "Meta de peso"
    GoalMetric.BODY_FAT -> "Meta de grasa corporal"
    GoalMetric.MUSCLE_MASS -> "Meta de masa muscular"
}

private fun GoalMetric.unitLabel(): String = when (this) {
    GoalMetric.WEIGHT -> "kg"
    GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "%"
}

internal fun displayWeight(weightKg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.LBS) weightKg * 2.2046226218 else weightKg

/** Para mostrar: coma decimal y una cifra decimal solo si hace falta («82,5», «178»). */
internal fun Double.formatDisplay(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale("es", "ES"), "%.1f", this)

/** Para los campos de edición: punto decimal, como antes (el guardado acepta coma o punto). */
internal fun Double.formatInput(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)
