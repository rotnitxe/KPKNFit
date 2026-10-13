package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramHeaderStatus
import com.example.kpkn.screens.programdetail.design.ProgramVolumeAssociation
import com.example.kpkn.screens.programdetail.design.ProgramVolumeBreakdown
import com.example.kpkn.screens.programdetail.design.ProgramVolumeDiscomfort
import com.example.kpkn.screens.programdetail.design.ProgramVolumeLine
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.ProgramVolumeScope
import com.example.kpkn.screens.programdetail.design.ProgramVolumeSplit
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
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

    /** Zonas de ejemplo de un músculo: mínimo efectivo, máximo adaptativo y máximo recuperable (P4). */
    private fun zonesOf(min: Int, adaptive: Int, recoverable: Int): ProgramVolumeZones =
        ProgramVolumeZones(minEffective = min, maxAdaptive = adaptive, maxRecoverable = recoverable)

    /**
     * Una semana de diez músculos en todos los estados de volumen (P4): exceso por zonas y por la analítica, sobrecarga funcional,
     * alto tolerable, óptimo (dos rótulos) y subentrenado. Viene ordenada por series, como la pestaña.
     */
    val volumeWeek: List<ProgramVolumeMuscle> = listOf(
        ProgramVolumeMuscle("Cuádriceps", 26.0, zonesOf(8, 16, 22), real = 22.0, overtrained = false, overreaching = false, scaleMax = 26.0),
        ProgramVolumeMuscle("Dorsales", 18.0, zonesOf(10, 16, 22), real = 14.0, overtrained = false, overreaching = false, scaleMax = 22.0),
        ProgramVolumeMuscle("Pectorales", 16.0, zonesOf(8, 16, 22), real = 16.0, overtrained = false, overreaching = false, scaleMax = 22.0),
        ProgramVolumeMuscle("Trapecio", 12.0, zonesOf(6, 12, 18), real = null, overtrained = false, overreaching = true, scaleMax = 18.0),
        ProgramVolumeMuscle("Deltoides", 12.0, zonesOf(8, 14, 20), real = null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Pantorrillas", 10.0, zonesOf(6, 12, 18), real = null, overtrained = true, overreaching = false, scaleMax = 18.0),
        ProgramVolumeMuscle("Bíceps", 8.0, zonesOf(8, 12, 18), real = 6.0, overtrained = false, overreaching = false, scaleMax = 18.0),
        ProgramVolumeMuscle("Isquiosurales", 6.0, zonesOf(8, 14, 20), real = null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Antebrazo", 4.0, zonesOf(6, 16, 20), real = null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Abdomen", 2.0, zonesOf(4, 10, 16), real = null, overtrained = false, overreaching = false, scaleMax = 16.0),
    )

    /** La misma semana sin calibrar: sin zonas, todas las barras comparten la escala de la semana. */
    val volumeUncalibrated: List<ProgramVolumeMuscle> = volumeWeek.take(6).map { muscle ->
        muscle.copy(zones = null, scaleMax = 26.0, overtrained = false, overreaching = false)
    }

    /** El alcance de una semana, con sus tres tipos. */
    val volumeScopeWeek = ProgramVolumeScope(
        label = "S3 · Acumulación",
        detail = "Conteo exacto de esa semana.",
        kinds = listOf("Semana", "Bloque", "Programa"),
        selectedKind = 0,
        canPrevious = true,
        canNext = true,
    )

    /** El desglose de pectorales: tres ejercicios y sin cabezas. */
    val volumeBreakdownPectorales = ProgramVolumeBreakdown(
        exercises = listOf(
            ProgramVolumeLine("Press banca", 6.0),
            ProgramVolumeLine("Aperturas en polea", 4.0),
            ProgramVolumeLine("Fondos", 3.0),
        ),
        subMuscles = emptyList(),
    )

    /** El reparto de la semana de ejemplo, con empuje por delante. */
    val volumeSplit = ProgramVolumeSplit(push = 62.0, pull = 48.0, legs = 40.0)

    /** Molestias de ejemplo para el resumen. */
    val volumeDiscomforts = listOf(ProgramVolumeDiscomfort("Hombro derecho", 3), ProgramVolumeDiscomfort("Lumbar", 1))

    /** Molestias asociadas a un ejercicio de ejemplo. */
    val volumeAssociations = listOf(ProgramVolumeAssociation("Press militar", "Pinchazo en hombro", 2))
}
