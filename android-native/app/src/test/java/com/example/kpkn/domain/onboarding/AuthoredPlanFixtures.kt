package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.EffectiveEquipmentResult
import com.example.kpkn.domain.training.EquipmentKeys
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.resolveEffectiveEquipment

/**
 * Fixtures compartidas por las pruebas de planes de autor (paquete wizvm).
 * Los materiales siguen los nombres de §17.1 (E0..E6) y son los mismos que
 * usa `PlanGenerationCoverageT006Test`; el catálogo es el real aprobado.
 */
internal object AuthoredPlanFixtures {

    /** Material declarado + equipo efectivo REAL que resuelve el producto. */
    class Gear(val label: String, val availability: EquipmentAvailability) {
        val options: TrainingOptions = TrainingOptions(availability = availability)
        val equipment: EffectiveEquipmentResult by lazy { options.resolveEffectiveEquipment(emptySet()) }
    }

    private val confirmedSupports: Map<String, ApparatusPresence> = mapOf(
        EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT,
        EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.PRESENT,
        EquipmentKeys.SQUAT_RACK to ApparatusPresence.PRESENT,
        EquipmentKeys.PREACHER_BENCH to ApparatusPresence.PRESENT,
        EquipmentKeys.PULLUP_BAR to ApparatusPresence.PRESENT,
        EquipmentKeys.DIP_BARS to ApparatusPresence.PRESENT,
        EquipmentKeys.LOW_BAR_SUPPORT to ApparatusPresence.PRESENT,
        EquipmentKeys.EZ_BAR to ApparatusPresence.PRESENT,
    )

    private val confirmedStations: Map<String, ApparatusPresence> = EFFECTIVE_EQUIPMENT_KEYS
        .filter { it.category in setOf(EquipmentCategory.MACHINES, EquipmentCategory.CABLE, EquipmentCategory.SMITH_MACHINE) }
        .associate { it.key to ApparatusPresence.PRESENT }

    /** E6: gimnasio con todos los aparatos de §10 confirmados. */
    val fullGym = Gear(
        "E6-gimnasio-completo",
        EquipmentAvailability(
            categories = EquipmentCategory.entries.toSet(),
            apparatus = confirmedStations,
            supports = confirmedSupports,
        ),
    )

    /** E5: categorías de gimnasio, soportes confirmados y máquinas SIN confirmar (UNKNOWN). */
    val gymWithUnknownMachines = Gear(
        "E5-gimnasio-maquinas-sin-confirmar",
        EquipmentAvailability(
            categories = EquipmentCategory.entries.toSet(),
            supports = confirmedSupports,
        ),
    )

    /** E3: solo mancuernas y banco regulable. */
    val dumbbellsAndBench = Gear(
        "E3-mancuernas-y-banco",
        EquipmentAvailability(
            categories = setOf(EquipmentCategory.DUMBBELLS, EquipmentCategory.SUPPORT),
            supports = mapOf(
                EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT,
                EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.PRESENT,
            ),
        ),
    )

    /** E0: solo peso corporal confirmado (categorías vacías). */
    val bodyweightOnly = Gear("E0-solo-cuerpo", EquipmentAvailability())

    /** Gimnasio completo pero con el rack NEGADO explícitamente (la banca de barra deja de ser posible). */
    val gymWithoutRack = Gear(
        "E6-sin-rack",
        EquipmentAvailability(
            categories = EquipmentCategory.entries.toSet(),
            apparatus = confirmedStations,
            supports = confirmedSupports + (EquipmentKeys.SQUAT_RACK to ApparatusPresence.ABSENT),
        ),
    )

    /** Gimnasio completo sin la categoría barra: la potencia ligada a un ejercicio de barra no tiene sustituto. */
    val gymWithoutBarbell = Gear(
        "E6-sin-barra",
        EquipmentAvailability(
            categories = EquipmentCategory.entries.toSet() - EquipmentCategory.BARBELL,
            apparatus = confirmedStations,
            supports = confirmedSupports,
        ),
    )

    fun entry(planId: String): CatalogEntry =
        requireNotNull(PersonalizedPlanCatalog.find(planId)) { "El plan '$planId' no está en el catálogo" }

    /** Programa base como el del wizard: id estable del borrador y lunes como inicio. */
    fun baseProgram(id: String = "authored-test"): Program = Program(id = id, name = "Plan de prueba", startDay = 1)

    fun request(
        entry: CatalogEntry,
        gear: Gear,
        idProvider: IdProvider = com.example.kpkn.domain.training.UuidIdProvider,
        expectedDaysPerWeek: Int? = entry.supportedFrequencies.first,
        programId: String = "authored-test",
    ): AuthoredPlanRequest = AuthoredPlanRequest(
        entry = entry,
        baseProgram = baseProgram(programId),
        equipment = gear.equipment,
        availability = gear.availability,
        catalog = CatalogCompositionTestSupport.catalog,
        metadata = CatalogCompositionTestSupport.metadata,
        options = gear.options,
        expectedDaysPerWeek = expectedDaysPerWeek,
        idProvider = idProvider,
    )

    fun prepare(planId: String, gear: Gear, idProvider: IdProvider = com.example.kpkn.domain.training.UuidIdProvider): Program =
        AuthoredPlanMaterializer.prepare(request(entry(planId), gear, idProvider))

    /** Ids deterministas para comparar dos materializaciones independientes. */
    class SeqIds(private val prefix: String) : IdProvider {
        private var next = 0
        override fun newId(): String = "${prefix}_${++next}"
    }

    // ─── Lectura del programa materializado ──────────────────────────────────

    fun weeksOf(program: Program): List<ProgramWeek> = program.macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }

    fun sessionsOf(program: Program): List<Session> = weeksOf(program).flatMap { it.sessions }

    fun exercisesOf(program: Program): List<Exercise> = sessionsOf(program).flatMap { it.allExercises() }

    /** Series de trabajo por sesión de la PRIMERA semana (oráculos 18/16/21/18 y 21/17/24/28/28). */
    fun firstWeekSetCounts(program: Program): List<Int> = weeksOf(program).first().sessions
        .map { session -> session.allExercises().sumOf { exercise -> exercise.sets.size } }

    fun firstWeekDays(program: Program): Set<Int> = weeksOf(program).first().sessions
        .mapNotNull { it.dayOfWeek }.toSet()
}
