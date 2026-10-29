package com.example.kpkn.screens.programs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluator
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.screens.onboarding.ProgramReadyChoice
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardDarkSystemBars
import com.example.kpkn.screens.onboarding.design.WizardDock
import com.example.kpkn.screens.onboarding.design.WizardDockClearance
import com.example.kpkn.screens.onboarding.design.WizardHeaderBlockHeight
import com.example.kpkn.screens.onboarding.design.WizardPageHeader
import com.example.kpkn.screens.onboarding.design.WizardProgressSegment
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardSummaryRow
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.ui.components.KpknPortal
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * «Listo para empezar» (Registros v4, cr-listo): el cierre único de los cuatro caminos de «Nuevo programa» (asistente,
 * catálogo, importar y desde cero). Está compuesto con piezas reales del asistente: la cabecera, las filas plegadas con
 * check y lápiz, las filas de opción con su radio y el botón redondo de confirmar. Nada se activa sin que la persona lo
 * decida: empezar ahora (pausa el actual), ponerlo en cola o guardarlo sin activar.
 */

/** El programa en curso (o en pausa) que el nuevo dejaría atrás. [weekIndex]/[weekCount] null si no se sabe la semana. */
data class ProgramReadyCurrent(
    val name: String,
    val weekIndex: Int? = null,
    val weekCount: Int? = null,
    val paused: Boolean = false,
)

/** Una fila plegada de la cabecera de «Listo para empezar»: etiqueta, valor y lápiz (null = sin lápiz). */
internal data class ProgramReadyRow(val label: String, val value: String, val onEdit: (() -> Unit)?)

/** Lo que el cierre cuenta del programa nuevo: nombre, semanas, días, minutos por sesión y día de inicio de la semana. */
internal data class ProgramReadyFacts(
    val name: String,
    val weeks: Int,
    val days: Int,
    val minutes: Int?,
    val startDay: Int,
)

/** Los textos de «Listo para empezar»: los del lienzo y, donde el lienzo no dibuja el caso, el mismo patrón. */
object ProgramReadyCopy {
    const val TITLE = "Listo para empezar"
    const val EYEBROW = "Último paso · Entreno"
    const val HEADER = "Nuevo programa"
    const val PROGRAM_LABEL = "Programa"
    const val WEEK_LABEL = "Tu semana"
    const val CONFIRM = "Confirmar"

    private val DAY_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    /** Con uno en curso, por defecto va en cola; sin ninguno, empieza ahora. */
    fun defaultChoice(current: ProgramReadyCurrent?): ProgramReadyChoice =
        if (current != null) ProgramReadyChoice.QUEUE else ProgramReadyChoice.START_NOW

    /** «Ya tienes «Motor y fuerza» en curso (semana 4 de 6). ¿Qué hacemos con el nuevo?» */
    fun body(current: ProgramReadyCurrent?): String {
        if (current == null) return "No tienes ningún programa en curso. ¿Qué hacemos con el nuevo?"
        val state = if (current.paused) "en pausa" else "en curso"
        val week = if (current.weekIndex != null && current.weekCount != null && current.weekCount > 1) {
            " (semana ${current.weekIndex} de ${current.weekCount})"
        } else {
            ""
        }
        return "Ya tienes «${current.name}» $state$week. ¿Qué hacemos con el nuevo?"
    }

    fun title(choice: ProgramReadyChoice): String = when (choice) {
        ProgramReadyChoice.START_NOW -> "Empezar ahora"
        ProgramReadyChoice.QUEUE -> "Ponerlo en cola"
        ProgramReadyChoice.SAVE -> "Guardarlo sin activar"
    }

    fun subtitle(choice: ProgramReadyChoice, current: ProgramReadyCurrent?): String = when (choice) {
        ProgramReadyChoice.START_NOW ->
            if (current != null) "Pausa «${current.name}»; podrás reanudarlo" else "Pasa a «En curso» desde hoy"
        ProgramReadyChoice.QUEUE ->
            if (current != null) "Empieza cuando termine el actual" else "Queda en «En cola»"
        ProgramReadyChoice.SAVE -> "Queda en «Tus programas»"
    }

    /** La línea sobre el botón: lo que pasa al confirmar. */
    fun note(choice: ProgramReadyChoice, current: ProgramReadyCurrent?): String = when (choice) {
        ProgramReadyChoice.START_NOW ->
            if (current != null) "Al confirmar: empieza hoy y «${current.name}» queda en pausa." else "Al confirmar: empieza hoy."
        ProgramReadyChoice.QUEUE ->
            if (current != null) "Al confirmar: guardado en cola, sin tocar el actual." else "Al confirmar: guardado en cola."
        ProgramReadyChoice.SAVE -> "Al confirmar: guardado sin activar."
    }

    /** «Fuerza y masa en bloques · 6 sem.» */
    fun programValue(name: String, weeks: Int): String =
        if (weeks > 0) "$name · $weeks sem." else name

    /** «5 días · ~60 min · Mié a Mar»: la semana va del día de inicio al anterior. */
    fun weekValue(days: Int, minutes: Int?, startDay: Int): String {
        val start = startDay.coerceIn(1, 7)
        val end = if (start == 1) 7 else start - 1
        return buildList {
            add(pluralDias(days))
            if (minutes != null && minutes > 0) add("~$minutes min")
            add("${DAY_SHORT[start - 1]} a ${DAY_SHORT[end - 1]}")
        }.joinToString(" · ")
    }
}

/** Nombre, semanas, días, minutos y día de inicio de [program]: lo que dicen las dos filas plegadas. */
internal fun programReadyFacts(program: Program): ProgramReadyFacts {
    val index = ProgramHierarchyIndex(program)
    val weeks = index.orderedWeeks().count { !it.week.isLoopWeek }
    val days = program.resolvedSchedulePlan().trainingDays.filter { it in 1..7 }.size
        .takeIf { it > 0 }
        ?: index.orderedWeeks().firstOrNull()?.week?.sessions?.size
        ?: 0
    return ProgramReadyFacts(
        name = program.name,
        weeks = weeks,
        days = days,
        minutes = PlanCandidateEvaluator.sessionMinutesOf(program).maxOrNull(),
        startDay = program.startDay?.takeIf { it in 1..7 } ?: 1,
    )
}

/** El programa en curso o en pausa de [state] (que no sea [exceptId]), con su semana si se sabe. */
internal fun programReadyCurrentOf(
    program: Program?,
    status: ProgramStatus?,
    currentWeekId: String?,
    exceptId: String? = null,
): ProgramReadyCurrent? {
    if (program == null || program.id == exceptId) return null
    if (status != ProgramStatus.ACTIVE && status != ProgramStatus.PAUSED) return null
    val index = ProgramHierarchyIndex(program)
    val weeks = index.orderedWeeks()
    val weekId = currentWeekId?.takeIf { it.isNotBlank() }
        ?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }
    val position = weekId?.let { id -> weeks.indexOfFirst { it.weekId == id } }?.takeIf { it >= 0 }
    return ProgramReadyCurrent(
        name = program.name,
        weekIndex = position?.plus(1),
        weekCount = if (position != null) weeks.size else null,
        paused = status == ProgramStatus.PAUSED,
    )
}

/** Lo que hace cada opción con el repositorio de programas. */
object ProgramReadyActions {
    /** El programa en curso o en pausa ahora mismo, salvo [exceptId]; null = ninguno. */
    fun currentProgram(exceptId: String? = null): ProgramReadyCurrent? {
        val repository = ProgramRepository.getInstance()
        val active = repository.activeProgramState.value ?: return null
        val program = repository.getProgramById(active.programId)
        return programReadyCurrentOf(program, active.status, active.currentWeekId, exceptId)
    }

    /**
     * Empezar ahora: el actual queda en pausa (se reanuda desde su detalle) y el nuevo pasa a «En curso». En cola: al final
     * de «En cola». Guardar: nada más (ya está en «Tus programas»).
     */
    fun apply(programId: String, choice: ProgramReadyChoice) {
        val repository = ProgramRepository.getInstance()
        when (choice) {
            ProgramReadyChoice.START_NOW -> {
                val active = repository.activeProgramState.value
                if (active != null && active.programId != programId && active.status == ProgramStatus.ACTIVE) {
                    repository.pauseProgram()
                }
                repository.removeProgramFromQueue(programId)
                repository.startProgram(programId)
            }
            ProgramReadyChoice.QUEUE -> repository.addProgramToQueue(programId)
            ProgramReadyChoice.SAVE -> Unit
        }
    }
}

/**
 * La pantalla «Listo para empezar». Sin estado propio: [choice] y [onChoice] los guarda quien la muestra (el asistente en
 * su borrador; los otros caminos en la pantalla). [headerLabel] y [segments] son los de la cabecera del asistente
 * («NUEVO PROGRAMA · 15/15»); [onConfirm] es el botón redondo.
 */
@Composable
internal fun ProgramReadyPage(
    headerLabel: String,
    segments: List<WizardProgressSegment>,
    rows: List<ProgramReadyRow>,
    current: ProgramReadyCurrent?,
    choice: ProgramReadyChoice,
    onChoice: (ProgramReadyChoice) -> Unit,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onBack: (() -> Unit)?,
    onExit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    WizardDarkSystemBars()
    val haze = remember { HazeState() }
    Box(modifier = modifier.fillMaxSize().background(WizardColors.background).testTag("program-ready")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = haze)
                .background(WizardColors.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(WizardHeaderBlockHeight))
            rows.forEach { row ->
                WizardSummaryRow(label = row.label, value = row.value, onClick = row.onEdit)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = WizardSpacing.gutter),
            ) {
                Spacer(Modifier.height(WizardSpacing.sectionPadTop))
                Text(
                    text = ProgramReadyCopy.EYEBROW.uppercase(),
                    style = WizardTypography.eyebrow,
                    color = WizardColors.textFaint,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ProgramReadyCopy.TITLE,
                    style = WizardTypography.stepTitle,
                    color = WizardColors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ProgramReadyCopy.body(current),
                    style = WizardTypography.stepSubtitle,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
                    ProgramReadyChoice.entries.forEach { option ->
                        Box(Modifier.testTag("program-ready-${option.name}")) {
                            WizardChoiceCard(
                                title = ProgramReadyCopy.title(option),
                                subtitle = ProgramReadyCopy.subtitle(option, current),
                                selected = option == choice,
                                onClick = { onChoice(option) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(WizardDockClearance + 64.dp))
            }
        }
        WizardPageHeader(
            haze = haze,
            label = headerLabel.uppercase(),
            segments = segments,
            onBack = onBack,
            onExit = onExit,
            exitLabel = "Salir",
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )
        WizardDock(
            enabled = confirmEnabled,
            label = ProgramReadyCopy.CONFIRM,
            note = ProgramReadyCopy.note(choice, current),
            onClick = onConfirm,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * «Listo para empezar» de los caminos que no pasan por el asistente (catálogo con máximos, importar y desde cero): el
 * programa ya está guardado en «Tus programas»; confirmar aplica la opción y [onDone] lo abre. Atrás ([onBack]) no
 * activa nada: el programa queda guardado.
 */
@Composable
internal fun ProgramReadyOverlay(
    programId: String,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    onEditProgram: ((String) -> Unit)? = null,
) {
    val repository = remember { ProgramRepository.getInstance() }
    val program = remember(programId) { repository.getProgramById(programId) }
    val current = remember(programId) { ProgramReadyActions.currentProgram(exceptId = programId) }
    var choice by rememberSaveable(programId) { mutableStateOf(ProgramReadyCopy.defaultChoice(current)) }
    BackHandler(onBack = onBack)
    val facts = remember(program) { program?.let(::programReadyFacts) }
    val edit = onEditProgram?.let { open -> { open(programId) } }
    val rows = facts?.let {
        listOf(
            ProgramReadyRow(ProgramReadyCopy.PROGRAM_LABEL, ProgramReadyCopy.programValue(it.name, it.weeks), edit),
            ProgramReadyRow(ProgramReadyCopy.WEEK_LABEL, ProgramReadyCopy.weekValue(it.days, it.minutes, it.startDay), edit),
        )
    }.orEmpty()
    // Por encima de todo (también de la barra de pestañas), como la página del catálogo.
    KpknPortal {
        ProgramReadyPage(
            headerLabel = ProgramReadyCopy.HEADER,
            segments = emptyList(),
            rows = rows,
            current = current,
            choice = choice,
            onChoice = { choice = it },
            confirmEnabled = program != null,
            onConfirm = {
                ProgramReadyActions.apply(programId, choice)
                onDone(programId)
            },
            onBack = onBack,
            onExit = onBack,
        )
    }
}
