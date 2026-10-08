package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/*
 * «¿Qué ejercicios ya te salen?»: una figura de palitos por ejercicio (dominada, flexión, fondo, sentadilla a una
 * pierna) que hace el movimiento en bucle con el RITMO y la CANTIDAD de repeticiones de su nivel: «Aún no» es la figura
 * quieta con el ejercicio intentado a medias; «Algunas», tres repeticiones y una pausa; «Varias», repeticiones fluidas
 * y continuas. Debajo, tres segmentos de nivel: tocar el símbolo avanza al siguiente (con vuelta al primero) y tocar un
 * segmento lo fija.
 */

private val MuscleAccent = Color(0xFFF49A6E)

/** Alto de la figura en dp y alto lógico de referencia (el del escenario más alto): misma escala en los cuatro. */
private val FigureBoxHeight = 104.dp
private const val STAGE_REF_HEIGHT = 76f

/** Marca de prueba del símbolo de un ejercicio y de uno de sus segmentos. */
internal fun capabilityTag(skill: CapabilitySkill) = "setup-capability-${skill.name}"
internal fun capabilityTag(skill: CapabilitySkill, level: CapabilityLevel) = "setup-capability-${skill.name}-${level.name}"

/**
 * Selector de capacidades. [skills] son los ejercicios por los que se pregunta y [levels] lo respondido de cada uno
 * (sin entrada = sin responder: la figura se ve como «Aún no» pero sin segmentos encendidos, y el primer toque en el
 * símbolo elige «Algunas»). [onLevel] recibe el ejercicio y su nuevo nivel, sea por el toque en el símbolo o en un
 * segmento. Con «reducir movimiento» las figuras quedan quietas en un cuadro representativo de su nivel.
 */
@Composable
fun CapabilitySymbols(
    skills: List<CapabilitySkill>,
    levels: Map<CapabilitySkill, CapabilityLevel>,
    onLevel: (CapabilitySkill, CapabilityLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val anyMoving = skills.any { CapabilityMotion.isMoving(levels[it]) }
    val clock = rememberFigClock(active = !reduced && anyMoving)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Las filas se componen repartidas en cuadros (la primera de golpe y una más por cuadro): ver `rememberProgressiveCount`.
        val rows = remember(skills) { skills.chunked(2) }
        val shownRows = rememberProgressiveCount(total = rows.size, first = 1)
        rows.take(shownRows).forEach { rowSkills ->
            // Si algún nombre de la fila ocupa dos líneas, todos reservan dos: los segmentos quedan alineados.
            val nameLines = if (rowSkills.any { it.label.length > 14 }) 2 else 1
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rowSkills.size == 1) Spacer(Modifier.weight(0.5f))
                rowSkills.forEach { skill ->
                    CapabilityCell(
                        skill = skill,
                        level = levels[skill],
                        clock = clock,
                        reducedMotion = reduced,
                        nameLines = nameLines,
                        onLevel = { onLevel(skill, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowSkills.size == 1) Spacer(Modifier.weight(0.5f))
            }
        }
    }
}

@Composable
private fun CapabilityCell(
    skill: CapabilitySkill,
    level: CapabilityLevel?,
    clock: State<Float>,
    reducedMotion: Boolean,
    nameLines: Int,
    onLevel: (CapabilityLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val levelText = level?.label ?: "sin responder"
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(capabilityTag(skill))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Cambiar el nivel de ${skill.label}",
                ) { onLevel(CapabilityLevels.next(level)) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "${skill.label}, $levelText"
                    stateDescription = levelText
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CapabilityFigure(
                skill = skill,
                level = level,
                clock = clock,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxWidth().height(FigureBoxHeight),
            )
            Text(
                text = skill.label,
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                minLines = nameLines,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        LevelSegments(skill = skill, level = level, reducedMotion = reducedMotion, onLevel = onLevel)
        // El nombre del nivel: con color solo cuando se respondió; sin responder ocupa su sitio, vacío.
        Text(
            text = level?.label ?: " ",
            style = WizardTypography.note,
            color = if (level != null) MuscleAccent else WizardColors.textFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- segmentos de nivel

/** Tres segmentos: 1, 2 o 3 encendidos según el nivel; tocar uno fija ese nivel. Cada objetivo mide 48 × 48 dp. */
@Composable
private fun LevelSegments(
    skill: CapabilitySkill,
    level: CapabilityLevel?,
    reducedMotion: Boolean,
    onLevel: (CapabilityLevel) -> Unit,
) {
    val lit = CapabilityLevels.litSegments(level)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CapabilityLevel.entries.forEach { lvl ->
            val on = lvl.ordinal < lit
            val color by animateColorAsState(
                if (on) MuscleAccent else WizardColors.text.copy(alpha = 0.22f),
                tween(if (reducedMotion) 0 else 220),
                label = "capability-segment",
            )
            Box(
                modifier = Modifier
                    .testTag(capabilityTag(skill, lvl))
                    .semantics {
                        role = Role.RadioButton
                        selected = lvl == level
                        contentDescription = "${skill.label}: ${lvl.label}"
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onLevel(lvl) }
                    .size(width = 48.dp, height = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(width = 32.dp, height = 4.dp)) {
                    drawRoundRect(color, Offset.Zero, Size(size.width, size.height), CornerRadius(size.height / 2f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- la figura

@Composable
private fun CapabilityFigure(
    skill: CapabilitySkill,
    level: CapabilityLevel?,
    clock: State<Float>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val pose = remember { FigPose() }
    val front = remember { FigFrontPose() }
    val pen = remember { FigPen() }
    val moving = !reducedMotion && CapabilityMotion.isMoving(level)
    Canvas(modifier) {
        val depth = if (moving) CapabilityMotion.depthAt(level, clock.value) else CapabilityMotion.restDepth(level)
        val stage = CapabilityPoses.stageOf(skill)
        // La misma escala en los cuatro ejercicios (la figura mide lo mismo), con el pie del escenario sobre el borde de abajo.
        val s = size.height / STAGE_REF_HEIGHT
        val ink = FigStyle.ink
        withTransform({
            translate(size.width / 2f - (stage.left + stage.width / 2f) * s, size.height - (stage.top + stage.height) * s)
            scale(s, s, Offset.Zero)
        }) {
            pen.begin(this)
            if (skill == CapabilitySkill.PULL_UP) {
                CapabilityPoses.pullUp(depth, front)
                drawPullUp(pen, front, ink)
            } else {
                CapabilityPoses.solveSide(skill, depth, pose)
                drawSideStage(pen, skill, stage, ink)
                pen.figure(pose, ink)
            }
        }
    }
}

private fun DrawScope.drawPullUp(pen: FigPen, p: FigFrontPose, ink: Color) {
    val w = FigStyle.BODY
    val soft = ink.copy(alpha = FigStyle.SOFT)
    // Barra con dos remates; la figura cuelga de ella.
    pen.line(Offset(CapabilityPoses.PULL_CX - CapabilityPoses.PULL_BAR_HALF, CapabilityPoses.PULL_BAR_Y),
        Offset(CapabilityPoses.PULL_CX + CapabilityPoses.PULL_BAR_HALF, CapabilityPoses.PULL_BAR_Y), ink.copy(alpha = 0.92f), FigStyle.BAR)
    pen.line(Offset(CapabilityPoses.PULL_CX - CapabilityPoses.PULL_BAR_HALF, CapabilityPoses.PULL_BAR_Y),
        Offset(CapabilityPoses.PULL_CX - CapabilityPoses.PULL_BAR_HALF, CapabilityPoses.PULL_BAR_Y + 6f), soft, FigStyle.FINE)
    pen.line(Offset(CapabilityPoses.PULL_CX + CapabilityPoses.PULL_BAR_HALF, CapabilityPoses.PULL_BAR_Y),
        Offset(CapabilityPoses.PULL_CX + CapabilityPoses.PULL_BAR_HALF, CapabilityPoses.PULL_BAR_Y + 6f), soft, FigStyle.FINE)
    pen.poly(p.shoulderL, p.elbowL, p.handL, ink, w)
    pen.poly(p.shoulderR, p.elbowR, p.handR, ink, w)
    pen.line(p.shoulderL, p.shoulderR, ink, w)
    pen.line(Offset(p.hip.x, (p.shoulderL.y)), p.hip, ink, w)
    pen.poly(p.hip, p.kneeL, p.footL, ink, w)
    pen.poly(p.hip, p.kneeR, p.footR, ink, w)
    pen.dot(p.head, FigGeo.HEAD_R + w * 0.4f, Color.Black)
    pen.ring(p.head, FigGeo.HEAD_R, ink, w * 0.85f)
}

private fun DrawScope.drawSideStage(pen: FigPen, skill: CapabilitySkill, stage: CapabilityStage, ink: Color) {
    val soft = ink.copy(alpha = FigStyle.SOFT)
    if (!stage.groundY.isNaN()) {
        pen.line(Offset(stage.left + 2f, stage.groundY), Offset(stage.left + stage.width - 2f, stage.groundY), soft, FigStyle.FINE)
    }
    if (skill == CapabilitySkill.DIP) {
        val y = CapabilityPoses.DIP_BAR_Y
        pen.line(Offset(CapabilityPoses.DIP_BAR_LEFT, y), Offset(CapabilityPoses.DIP_BAR_RIGHT, y), ink.copy(alpha = 0.92f), FigStyle.BAR)
        pen.line(Offset(CapabilityPoses.DIP_BAR_LEFT + 3f, y), Offset(CapabilityPoses.DIP_BAR_LEFT + 3f, CapabilityPoses.DIP_GROUND + 1.5f), soft, FigStyle.FINE)
        pen.line(Offset(CapabilityPoses.DIP_BAR_RIGHT - 3f, y), Offset(CapabilityPoses.DIP_BAR_RIGHT - 3f, CapabilityPoses.DIP_GROUND + 1.5f), soft, FigStyle.FINE)
    }
}
