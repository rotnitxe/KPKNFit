package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeClassifiers
import org.junit.Assert.*
import org.junit.Test

class VolumeAccountingContractTest {
    private val f = TrainingVolumeFixtures
    private val heads = listOf(
        InvolvedMuscle("Deltoides", MuscleRole.PRIMARY, 1.0, "anterior"),
        InvolvedMuscle("Deltoides", MuscleRole.SECONDARY, 0.5, "medio"),
        InvolvedMuscle("Deltoides", MuscleRole.STABILIZER, 0.4, "posterior"),
    )

    @Test
    fun `tres mas tres series del mismo ejercicio suman seis en el padre y en la porcion`() {
        val session = Session("sesion", "Sesión", exercises = listOf(f.exercise(count = 3, muscles = heads)))
        val result = VolumeCalculator.calculateVolume(listOf(session, session), emptyMap()).muscles.getValue("Deltoides")
        assertEquals(6.0, result.sets, 1e-9)
        assertEquals(6.0, result.portions.single { it.name == "Deltoides Anterior" }.sets, 1e-9)
        assertEquals(6.0, result.exercises.single().sets, 1e-9)
        assertEquals(3.0, result.portions.single { it.role == MuscleRole.SECONDARY }.sets, 1e-9)
        assertEquals(2.4, result.portions.single { it.role == MuscleRole.STABILIZER }.sets, 1e-9)
    }

    @Test
    fun `la suma del desglose iguala la barra en los tres modos con y sin intensidad`() {
        val exercises = listOf(
            f.exercise("directo", 3, heads).copy(exerciseDbId = "directo"),
            f.exercise("secundario", 2, listOf(heads[1])).copy(exerciseDbId = "secundario"),
            f.exercise("estabilizador", 1, listOf(heads[2])).copy(exerciseDbId = "estabilizador"),
        )
        VolumeCountingMode.entries.forEach { mode ->
            listOf(false, true).forEach { intensity ->
                val result = VolumeCalculator.calculateVolume(
                    listOf(Session("s", "Sesión", exercises = exercises)), emptyMap(), divisor = 3.0,
                    mode = mode, adjustByIntensity = intensity,
                ).muscles.getValue("Deltoides")
                val sets = when (mode) {
                    VolumeCountingMode.DIRECT -> 3.0
                    VolumeCountingMode.SECONDARY -> 4.0
                    VolumeCountingMode.STABILIZER -> 4.4
                }
                val multiplier = if (intensity) AugeClassifiers.getEffectiveVolumeMultiplier(7.0) else 1.0
                assertEquals("$mode / intensidad=$intensity", sets * multiplier / 3.0, result.sets, 1e-9)
                assertEquals(result.sets, result.exercises.sumOf { it.sets }, 1e-9)
                assertEquals(result.sets, result.roles.totalSets, 1e-9)
                result.portions.forEach { portion ->
                    assertTrue(mode.includes(portion.role))
                    assertEquals(portion.sets, portion.exercises.sumOf { it.sets }, 1e-9)
                }
            }
        }
    }

    @Test
    fun `la API canonica colapsa los roles del mismo grupo al maximo sin sumar cabezas`() {
        val session = Session("s", "Sesión", exercises = listOf(f.exercise(count = 3, muscles = heads)))
        val result = VolumeCalculator.calculateVolume(listOf(session), emptyMap()).muscles.getValue("Deltoides").roles
        assertEquals(3.0, result.totalSets, 1e-9)
        assertEquals(3.0, result.directSets, 1e-9)
        assertEquals(0.0, result.indirectSets, 1e-9)
    }

    @Test
    fun `gana el aporte mayor aunque su rol sea secundario y el empate prioriza directo`() {
        val rows = listOf(
            InvolvedMuscle("Glúteos", MuscleRole.PRIMARY, 0.2, "mayor"),
            InvolvedMuscle("Glúteos", MuscleRole.SECONDARY, 0.5, "medio"),
        )
        fun calculate(muscles: List<InvolvedMuscle>, mode: VolumeCountingMode) = VolumeCalculator.calculateVolume(
            listOf(Session("s", "Sesión", exercises = listOf(f.exercise(count = 2, muscles = muscles)))), emptyMap(), mode = mode,
        ).muscles.getValue("Glúteos")
        assertEquals(0.4, calculate(rows, VolumeCountingMode.DIRECT).sets, 1e-9)
        val secondary = calculate(rows.reversed(), VolumeCountingMode.SECONDARY)
        assertEquals(1.0, secondary.sets, 1e-9)
        assertEquals(1.0, secondary.roles.secondarySets, 1e-9)
        val tied = rows.map { it.copy(volumeContribution = 0.5) }
        assertEquals(1.0, calculate(tied, VolumeCountingMode.STABILIZER).roles.directSets, 1e-9)
        assertEquals(1.0, calculate(tied.reversed(), VolumeCountingMode.STABILIZER).roles.directSets, 1e-9)
    }

    @Test
    fun `una porcion conserva sus exposiciones por rol sin duplicar filas repetidas de una ocurrencia`() {
        val rows = listOf(heads[0], heads[0], heads[1].copy(emphasis = "anterior"))
        val session = Session("s", "Sesión", exercises = listOf(f.exercise(count = 3, muscles = rows)))
        val result = VolumeCalculator.calculateVolume(listOf(session), emptyMap()).muscles.getValue("Deltoides")
        assertEquals(3.0, result.sets, 1e-9)
        assertEquals(2, result.portions.size)
        assertEquals(3.0, result.portions.single { it.role == MuscleRole.PRIMARY }.sets, 1e-9)
        assertEquals(1.5, result.portions.single { it.role == MuscleRole.SECONDARY }.sets, 1e-9)
    }

    @Test
    fun `intensidad ajusta una sola vez y excluye las series inefectivas`() {
        val exercise = f.exercise(count = 1, muscles = listOf(heads[1])).let {
            it.copy(sets = it.sets + it.sets.single().copy(id = "excluida", targetRPE = 10.0, isIneffective = true))
        }
        val result = VolumeCalculator.calculateVolume(
            listOf(Session("s", "Sesión", exercises = listOf(exercise))), emptyMap(), divisor = 2.0, adjustByIntensity = true,
        ).muscles.getValue("Deltoides")
        val expected = AugeClassifiers.getEffectiveVolumeMultiplier(7.0) * 0.5 / 2.0
        assertEquals(expected, result.sets, 1e-9)
        assertEquals(expected, result.exercises.single().sets, 1e-9)
        assertEquals(expected, result.portions.single().sets, 1e-9)
    }

    @Test
    fun `real conserva configuracion v2 y aplica RPE registrado antes del divisor`() {
        val config = ExerciseMuscleInfo("press-v2", "Press", involvedMuscles = heads)
        val log = f.log("log", sets = 2).copy(completedExercises = listOf(
            f.log("base", sets = 2).completedExercises.single().copy(
                exerciseDbId = null, catalogConfigurationId = "press-v2",
                sets = listOf(CompletedSet("buena", reps = 8, weight = 60.0, rpe = 7.0), CompletedSet("calentamiento", reps = 8, isWarmup = true)),
            ),
        ))
        val result = VolumeCalculator.calculateCompletedVolume(
            listOf(log), mapOf("press-v2" to config), TrainingWindow.last28Days(f.zone, f.today),
            mode = VolumeCountingMode.DIRECT, adjustByIntensity = true,
        ).muscles.getValue("Deltoides")
        assertEquals(AugeClassifiers.getEffectiveVolumeMultiplier(7.0) / 4.0, result.sets, 1e-9)
        assertEquals(result.sets, result.portions.single().sets, 1e-9)
    }

    @Test
    fun `ejercicios distintos con el mismo nombre conservan contribuyentes distintos`() {
        val first = f.exercise("primero", 3, heads).copy(exerciseDbId = "primero")
        val second = f.exercise("segundo", 3, heads).copy(exerciseDbId = "segundo")
        val result = VolumeCalculator.calculateVolume(
            listOf(Session("s", "Sesión", exercises = listOf(first, second))), emptyMap(),
        ).muscles.getValue("Deltoides")
        assertEquals(2, result.exercises.size)
        assertEquals(6.0, result.portions.single { it.role == MuscleRole.PRIMARY }.sets, 1e-9)
    }

    @Test
    fun `RPE y RIR registrados en el modo de intensidad ajustan Real una sola vez`() {
        val log = f.log("intensidad").copy(completedExercises = listOf(f.log("base").completedExercises.single().copy(sets = listOf(
            CompletedSet("rpe", reps = 8, weight = 60.0, actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 7.0),
            CompletedSet("rir", reps = 8, weight = 60.0, actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 3.0),
        ))))
        val result = VolumeCalculator.calculateCompletedVolume(
            listOf(log), f.catalog, TrainingWindow.last28Days(f.zone, f.today), adjustByIntensity = true,
        )
        assertEquals(2.0 * AugeClassifiers.getEffectiveVolumeMultiplier(7.0) / 4.0, result.muscles.getValue("Pectorales").sets, 1e-9)
    }
}
