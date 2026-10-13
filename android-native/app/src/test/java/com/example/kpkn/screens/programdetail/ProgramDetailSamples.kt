package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramHeaderStatus
import com.example.kpkn.screens.programdetail.design.programMetaLine

/** Datos de ejemplo de Program Detail para la hoja de render de la cabecera (P1). Sin datos reales ni inventados fuera de ejemplo. */
internal object ProgramDetailSamples {

    /** Un programa de fuerza de 12 semanas, con portada de estudio en negro y noche. */
    val active: Program = Program(
        id = "generated:strength-muscle",
        name = "Fuerza de base",
        description = "Para empezar",
        coverImage = "kpkn-studio://PLATES/NOCHE",
        mode = ProgramMode.POWERLIFTING,
        weekDays = 4,
    )

    /** El mismo programa con un nombre largo, para ver cómo se encoge el título. */
    val longName: Program = active.copy(name = "Fuerza y masa muscular con acumulación e intensificación de doce semanas")

    /** Un programa simple de una semana que se repite. */
    val simple: Program = active.copy(
        id = "template:simple-1",
        name = "Semana de tres días",
        structure = ProgramStructure.SIMPLE,
        coverImage = "kpkn-studio://WAVES/PAPEL",
        mode = ProgramMode.HYPERTROPHY,
        weekDays = 3,
    )

    /** El estado de la cabecera que pinta [program], con sus textos y su progreso. */
    fun headerState(
        program: Program,
        status: ProgramHeaderStatus,
        primary: ProgramHeaderPrimary,
        weekCount: Int,
        currentWeek: Int?,
        progressLabel: String?,
        repeating: Boolean = false,
    ): ProgramHeaderState = ProgramHeaderState(
        name = program.name,
        status = status,
        meta = programMetaLine(weekCount, repeating, program.weekDays, program.mode),
        identity = coverIdentityForProgram(program),
        photoUrl = null,
        bundled = null,
        weekCount = weekCount,
        currentWeekIndex = currentWeek,
        progressLabel = progressLabel,
        primary = primary,
    )
}
