package com.example.kpkn.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.screens.onboarding.placeConflictDayName

/*
 * SOLO DEPURACIÓN (no se integra): lo que dejó la activación del asistente, leído del REPOSITORIO de programas (el que usa la app,
 * publicado por el coordinador de altas tras guardar en Room) y no del estado de la pantalla del asistente. Sirve para comprobar en
 * el teléfono, con una captura, que lo declarado llegó al programa: nombre, días, minutos, lugar y ejercicios de cada sesión, sesión
 * principal y los Ajustes que escribe el alta (tipo de atleta, material, unidad).
 */

/** Una fila del resumen: el título en tinta y, debajo, un detalle atenuado. */
internal data class SummaryRow(val title: String, val detail: String? = null, val strong: Boolean = false)

private fun firstWeekOf(program: Program): List<Session> =
    program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.firstOrNull()?.sessions.orEmpty()

private fun dayLabel(day: Int?): String = day?.let { placeConflictDayName(it).replaceFirstChar { c -> c.titlecase() } } ?: "Sin día"

private fun placeLabel(placeId: String?): String = when (placeId) {
    "GYM" -> "gimnasio"
    "HOME" -> "casa"
    "PUBLIC" -> "espacios públicos"
    null -> "sin lugar"
    else -> placeId.lowercase()
}

/**
 * Las filas del resumen de [program] y [settings] (puro, para poder probarlo): primero el programa y su semana, después una fila
 * por sesión (día, nombre, lugar, número de ejercicios y minutos; con [withExercises], también los nombres) y al final lo que el
 * alta escribió en Ajustes y en el programa (tipo de atleta, material, prioridades, marcas).
 */
internal fun activationSummaryRows(program: Program?, settings: Settings, withExercises: Boolean): List<SummaryRow> = buildList {
    if (program == null) {
        add(SummaryRow("Sin programa activo en el repositorio", "El alta no dejó ningún programa activo.", strong = true))
        return@buildList
    }
    val plan = program.resolvedSchedulePlan()
    val sessions = firstWeekOf(program).sortedBy { it.dayOfWeek ?: 0 }
    val days = sessions.mapNotNull { it.dayOfWeek }.sorted()
    add(SummaryRow(program.name, "modo ${program.mode.name.lowercase()} · autorregulación ${program.autoregulationMode.name}", strong = true))
    add(
        SummaryRow(
            "${days.size} ${if (days.size == 1) "día" else "días"}: ${days.joinToString(", ") { placeConflictDayName(it).take(3) }}",
            "la semana empieza el ${placeConflictDayName(plan.weekStartDay ?: program.startDay ?: 1)} · días de entreno ${plan.trainingDays.sorted()}",
        ),
    )
    val main = sessions.firstOrNull { it.isMainSession }
    add(SummaryRow("Sesión principal: " + (main?.let { "${dayLabel(it.dayOfWeek)} · ${it.name}" } ?: "ninguna"), null))
    sessions.forEach { session ->
        val estimate = SessionDurationEstimator.estimate(session)
        val exercises = session.allExercises()
        val head = "${dayLabel(session.dayOfWeek)} · ${session.name}${if (session.isMainSession) "  (principal)" else ""}"
        val facts = "${placeLabel(session.placeId)} · ${exercises.size} ejercicios · ${estimate.totalMinutes} min"
        val names = if (withExercises) exercises.joinToString(" · ") { exercise ->
            val sets = exercise.sets.size
            val extras = buildList {
                if (exercise.warmupSets.isNotEmpty()) add("aprox.")
                if (exercise.mobilitySeries.isNotEmpty()) add("mov.")
            }.joinToString("+")
            "${exercise.name} ${sets}x${if (extras.isEmpty()) "" else " $extras"}"
        } else null
        add(SummaryRow(head, listOfNotNull(facts, names).joinToString("\n")))
    }
    val material = EquipmentSymbols.selectedFrom(settings.equipmentAvailability).joinToString(", ") { it.label }.ifEmpty { "ninguno" }
    add(SummaryRow("Ajustes que escribió el alta", "tipo de atleta ${settings.athleteType} · unidad ${settings.weightUnit}\nmaterial: $material"))
    program.planOrderPriorities?.takeIf { it.isNotEmpty() }?.let { bag ->
        add(SummaryRow("Prioridades del programa", bag.entries.sortedBy { it.key }.joinToString(" · ") { "${it.key} ${it.value}" }))
    }
    program.powerliftingProfile?.let { profile ->
        add(SummaryRow("Marcas que viajan al programa", "sentadilla ${profile.squat1RM} · banca ${profile.bench1RM} · peso muerto ${profile.deadlift1RM}"))
    }
}

/**
 * La pantalla del resumen: lee el programa activo y los Ajustes de [ProgramRepository] (las mismas fuentes que la app tras activar).
 * Con [withExercises] lista también los ejercicios de cada sesión (con `aprox.` y `mov.` cuando llevan aproximación o movilidad).
 */
@Composable
internal fun HarnessActivationSummary(withExercises: Boolean) {
    val repository = ProgramRepository.getInstance()
    val active by repository.activeProgramState.collectAsState()
    val programs by repository.programs.collectAsState()
    val settings by repository.settings.collectAsState()
    val program = active?.programId?.let { id -> programs.firstOrNull { it.id == id } }
    val rows = activationSummaryRows(program, settings, withExercises)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("ACTIVADO · leído del repositorio", color = INK_MUTED, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        rows.forEach { row ->
            Text(
                row.title,
                color = INK,
                fontSize = if (row.strong) 18.sp else 15.sp,
                fontWeight = if (row.strong) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            row.detail?.let { detail -> Text(detail, color = INK_MUTED, fontSize = 13.sp, lineHeight = 17.sp) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val INK = Color(0xFFF2EEE6)
private val INK_MUTED = Color(0xFFB8B2A6)
