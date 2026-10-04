package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.AuthoredSetRange
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.NativeProgressionSpec
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.PlanSlotChange
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.KpknOperationalDefault
import com.example.kpkn.data.protocols.KpknOperationalDefaultScope
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotLoadReferenceMetadata
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.rangeRirSets
import com.example.kpkn.data.protocols.weekRecipe

/**
 * Recetas normalizadas de los cuatro planes autorados de §10.1/§18.1 (paquete E):
 *
 * | id | procedencia |
 * |---|---|
 * | `original:phul-ms-2021-r1` | Original fiel PHUL · M&S 2021 |
 * | `original:phat-biolayne-2016-r1` | Original fiel PHAT · Biolayne 2016 |
 * | `adapted:phul-kpkn-r1` | Adaptación KPKN del PHUL anterior |
 * | `adapted:phat-kpkn-r1` | Adaptación KPKN del PHAT anterior |
 *
 * Contratos:
 * - Tablas §10.2/§10.3 literales: mismo orden de ejercicios, mínimo de cada
 *   rango de series y oráculos **18/16/21/18** (PHUL) y **21/17/24/28/28**
 *   (PHAT). Ningún set de PHUL lleva porcentaje; los SPEED de PHAT van al
 *   inicio de los tres días de hipertrofia con `percent = 65` y referencia
 *   `OBSERVED_WORKING_SET` de la MISMA configuración (§14.2), nunca TM.
 * - `liftSlots` vacío y `LiftRef.liftSlot = null` (§14.3: auditoría por
 *   configuración/slot, no mapa SBD genérico).
 * - `compositionProfile = AUTHORED_EXACT` con exenciones **por día y regla**
 *   justificadas por la tabla fuente; nunca ámbito `"*"` y nunca H11.
 * - Las adaptaciones comparten la tabla con su original (mismos días y
 *   dosis); solo cambian la identidad/procedencia y permiten sustitución por
 *   material vía `PlanAdaptationResolver` (§13.4).
 */
object AuthoredPhulPhatRecipes {
    const val PHUL_ORIGINAL_ID = "original:phul-ms-2021-r1"
    const val PHAT_ORIGINAL_ID = "original:phat-biolayne-2016-r1"
    const val PHUL_ADAPTED_ID = "adapted:phul-kpkn-r1"
    const val PHAT_ADAPTED_ID = "adapted:phat-kpkn-r1"

    /** Reps de RIR inicial publicado/operativo: 2 (ambas fuentes). */
    const val INITIAL_RIR = 2

    val phulOriginal: TrainingPlanRecipe by lazy { buildPhulOriginal() }
    val phatOriginal: TrainingPlanRecipe by lazy { buildPhatOriginal() }

    val phulAdapted: TrainingPlanRecipe by lazy {
        phulOriginal.copy(
            id = PHUL_ADAPTED_ID,
            // §10.2 solo prohibe sustituir los principales dentro de «Original»:
            // la adaptación habilita la sustitución curada de esos slots (p. ej.
            // banca con barra → mancuernas) sin cambiar días ni dosis.
            weeks = phulOriginal.weeks.map { week ->
                week.copy(days = week.days.map { day -> day.copy(slots = day.slots.map { it.copy(isCompetitionLift = false) }) })
            },
            provenance = adaptedProvenance(
                recipeId = PHUL_ADAPTED_ID,
                parent = phulOriginal,
                source = AuthoredSources.phul,
                note = "Adaptación KPKN del PHUL M&S 2021: mismos 4 días y dosis iniciales de §10.2; " +
                    "las sustituciones por material se aplican por slot con PlanAdaptationResolver (§13.4).",
            ),
        )
    }

    val phatAdapted: TrainingPlanRecipe by lazy {
        phatOriginal.copy(
            id = PHAT_ADAPTED_ID,
            provenance = adaptedProvenance(
                recipeId = PHAT_ADAPTED_ID,
                parent = phatOriginal,
                source = AuthoredSources.phat,
                note = "Adaptación KPKN del PHAT Biolayne 2016: mismos 5 días y dosis del ejemplo §10.3; " +
                    "las sustituciones por material se aplican por slot con PlanAdaptationResolver (§13.4). " +
                    "Si el presupuesto no admite el volumen se ofrece el plan propio de fuerza y músculo, " +
                    "nunca un «PHAT» recortado en silencio.",
            ),
        )
    }

    val all: List<TrainingPlanRecipe>
        get() = listOf(phulOriginal, phatOriginal, phulAdapted, phatAdapted)

    fun recipeFor(planId: String): TrainingPlanRecipe? = when (planId) {
        PHUL_ORIGINAL_ID -> phulOriginal
        PHAT_ORIGINAL_ID -> phatOriginal
        PHUL_ADAPTED_ID -> phulAdapted
        PHAT_ADAPTED_ID -> phatAdapted
        else -> null
    }

    // ─── PHUL §10.2 ──────────────────────────────────────────────────────────

    private fun buildPhulOriginal(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = PHUL_ORIGINAL_ID,
        weeks = (1..PHUL_WEEKS).map { week ->
            weekRecipe(
                weekNumber = week,
                blockIndex = 0,
                blockName = "PHUL",
                blockGoal = BlockGoal.ACCUMULATION,
                days = phulDays(),
                kind = WeekExecutionKind.TRAINING,
                weekName = "Semana $week",
            )
        },
        liftSlots = emptyMap(),
        exemptions = phulExemptions(),
        claimedDaysPerWeek = 4,
        claimedLevel = "intermedio",
        repeats = true,
        compositionProfile = RecipeCompositionProfile.AUTHORED_EXACT,
        provenance = AuthoredSources.phul.toOriginalProvenance(PHUL_ORIGINAL_ID),
        // R16 (decisión del dueño): PHUL, original y adaptado (copia), sube la carga con la doble
        // progresión KPKN. No toca series, repeticiones ni descansos. PHAT NO la lleva por ahora.
        nativeProgression = NativeProgressionSpec(
            strategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
            exposuresBeforeProposal = 2,
            note = "Recomendación KPKN §12.4: el PHUL publicado no fija la subida de carga. " +
                "Sube reps hasta el tope del rango y, tras dos exposiciones completas, propone " +
                "el menor incremento conocido del equipo.",
        ),
    )

    private fun phulDays(): List<DayRecipe> = listOf(
        DayRecipe(
            label = "Superior fuerza",
            id = "phul-upper-power",
            weekday = 1,
            slots = listOf(
                authoredSlot(
                    id = "bp", role = SlotRole.T1_MAIN, configurationId = CatalogIds.BP,
                    sets = rangeRirSets(3, 3, 5, INITIAL_RIR), restSeconds = 180,
                    intent = SlotIntent.F, authoredSetRange = AuthoredSetRange(3, 4),
                    isCompetitionLift = true,
                ),
                authoredSlot(
                    id = "inc-db", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.BP_INC_DB,
                    sets = rangeRirSets(3, 6, 10, INITIAL_RIR), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 4),
                    supplementalOf = "bp",
                ),
                authoredSlot(
                    id = "row", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.ROW,
                    sets = rangeRirSets(3, 3, 5, INITIAL_RIR), restSeconds = 180,
                    intent = SlotIntent.F, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "lat", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.LAT,
                    sets = rangeRirSets(3, 6, 10, INITIAL_RIR), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "ohp", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.OHP,
                    sets = rangeRirSets(2, 5, 8, INITIAL_RIR), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 3),
                    isCompetitionLift = true,
                ),
                authoredSlot(
                    id = "curl", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CURL,
                    sets = rangeRirSets(2, 6, 10, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 3),
                ),
                authoredSlot(
                    id = "skull", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.SKULLCRUSHER,
                    sets = rangeRirSets(2, 6, 10, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 3),
                ),
            ),
        ),
        DayRecipe(
            label = "Inferior fuerza",
            id = "phul-lower-power",
            weekday = 2,
            slots = listOf(
                authoredSlot(
                    id = "sq", role = SlotRole.T1_MAIN, configurationId = CatalogIds.SQ_HIGH,
                    sets = rangeRirSets(3, 3, 5, INITIAL_RIR), restSeconds = 180,
                    intent = SlotIntent.F, authoredSetRange = AuthoredSetRange(3, 4),
                    isCompetitionLift = true,
                ),
                authoredSlot(
                    id = "dl", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.DL,
                    sets = rangeRirSets(3, 3, 5, INITIAL_RIR), restSeconds = 180,
                    intent = SlotIntent.F, authoredSetRange = AuthoredSetRange(3, 4),
                    isCompetitionLift = true,
                ),
                authoredSlot(
                    id = "press", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.PRESS_LEG,
                    sets = rangeRirSets(3, 10, 15, INITIAL_RIR), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 5),
                ),
                authoredSlot(
                    id = "curl", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CURL_L,
                    sets = rangeRirSets(3, 6, 10, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "calf", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CALF_STANDING,
                    sets = rangeRirSets(4, 6, 10, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(4, 4),
                ),
            ),
        ),
        DayRecipe(
            label = "Superior hipertrofia",
            id = "phul-upper-hypertrophy",
            weekday = 4,
            slots = listOf(
                authoredSlot(
                    id = "bp-inc", role = SlotRole.T1_MAIN, configurationId = CatalogIds.BP_INC,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 180,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "fly", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.FLY,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "row-cable", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.ROW_CABLE,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "row-db", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.ROW_DB,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 4),
                    isUnilateral = true,
                ),
                authoredSlot(
                    id = "lat-raise", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.LATERAL,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                // C-10 (M5, B.S6): el curl inclinado de la tabla es una configuración propia del catálogo (antes se aproximaba con el
                // curl sentado en banco plano, que no es el ángulo inclinado del respaldo).
                authoredSlot(
                    id = "curl-inc", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CURL_INCLINE,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "tri-polea", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.PUSHDOWN,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                ),
            ),
        ),
        DayRecipe(
            label = "Inferior hipertrofia",
            id = "phul-lower-hypertrophy",
            weekday = 5,
            slots = listOf(
                authoredSlot(
                    id = "sq-front", role = SlotRole.T1_MAIN, configurationId = CatalogIds.SQ_FRONT,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 180,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 4),
                    isCompetitionLift = true,
                ),
                authoredSlot(
                    id = "lunge", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.LUNGE_W_BARBELL,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 4),
                    isUnilateral = true,
                ),
                authoredSlot(
                    id = "ext", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.LEG_EXT,
                    sets = rangeRirSets(3, 10, 15, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "curl", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CURL_H,
                    sets = rangeRirSets(3, 10, 15, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "calf-sit", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CALF_SEATED,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                ),
                authoredSlot(
                    id = "calf-press", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CALF_LEG_PRESS,
                    sets = rangeRirSets(3, 8, 12, INITIAL_RIR), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 4),
                    supplementalOf = "calf-sit",
                ),
            ),
        ),
    )

    private fun phulExemptions(): List<RecipeCompositionExemption> {
        val url = AuthoredSources.PHUL_URL
        // B.S6: la exención H5a de «Inferior fuerza» estaba muerta (PHUL no publica porcentajes, así que ninguna serie cuenta como
        // «pesada» en H5a: los dos axiales de la tabla caben sin exención) y se retira.
        return listOf(
            RecipeCompositionExemption(
                rule = "H1",
                scope = "w*/Superior hipertrofia",
                justification = "§10.2: la tabla publica las aperturas antes de los remos; el orden de la fuente se conserva.",
                sourceUrl = url,
            ),
            RecipeCompositionExemption(
                rule = "H3",
                scope = "w*/Inferior hipertrofia",
                justification = "§10.2: la tabla encadena sentadilla frontal, zancada y extensión de cuádriceps en el mismo día.",
                sourceUrl = url,
            ),
        )
    }

    // ─── PHAT §10.3 ──────────────────────────────────────────────────────────

    private fun buildPhatOriginal(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = PHAT_ORIGINAL_ID,
        weeks = (1..PHAT_WEEKS).map { week ->
            weekRecipe(
                weekNumber = week,
                blockIndex = 0,
                blockName = "PHAT",
                blockGoal = BlockGoal.ACCUMULATION,
                days = phatDays(rir = if (week <= 4) INITIAL_RIR else PHAT_LATE_RIR),
                kind = WeekExecutionKind.TRAINING,
                weekName = "Semana $week",
            )
        },
        liftSlots = emptyMap(),
        exemptions = phatExemptions(),
        claimedDaysPerWeek = 5,
        claimedLevel = "avanzado",
        // Ventana de 6 semanas con revisión al final (§10.3): sin semana 7
        // fabricada y sin descarga inventada; repetir la ventana es una acción
        // consciente del usuario, no un ciclo ciego.
        repeats = false,
        compositionProfile = RecipeCompositionProfile.AUTHORED_EXACT,
        provenance = AuthoredSources.phat.toOriginalProvenance(PHAT_ORIGINAL_ID),
    )

    private fun phatDays(rir: Int): List<DayRecipe> = listOf(
        DayRecipe(
            label = "Superior fuerza",
            id = "phat-power-upper",
            weekday = 1,
            slots = listOf(
                authoredSlot(
                    id = "row-pendlay", role = SlotRole.T1_MAIN, configurationId = CatalogIds.PENDLAY,
                    sets = rangeRirSets(3, 3, 5, rir), restSeconds = 240,
                    intent = SlotIntent.F, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "pullup", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.PULLUP,
                    sets = rangeRirSets(2, 6, 10, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "rack-chin", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.RACK_CHIN,
                    sets = rangeRirSets(2, 6, 10, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 2),
                    supplementalOf = "pullup",
                ),
                authoredSlot(
                    id = "bench-db", role = SlotRole.T1_MAIN, configurationId = CatalogIds.BP_DB,
                    sets = rangeRirSets(3, 3, 5, rir), restSeconds = 240,
                    intent = SlotIntent.F, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "dips", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.DIPS,
                    sets = rangeRirSets(2, 6, 10, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "press-db", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.SEATED_PRESS_DB,
                    sets = rangeRirSets(3, 6, 10, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "curl-ez", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CURL_EZ,
                    sets = rangeRirSets(3, 6, 10, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "skull", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.SKULLCRUSHER,
                    sets = rangeRirSets(3, 6, 10, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 3),
                ),
            ),
        ),
        DayRecipe(
            label = "Inferior fuerza",
            id = "phat-power-lower",
            weekday = 2,
            slots = listOf(
                authoredSlot(
                    id = "sq", role = SlotRole.T1_MAIN, configurationId = CatalogIds.SQ_HIGH,
                    sets = rangeRirSets(3, 3, 5, rir), restSeconds = 240,
                    intent = SlotIntent.F, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "hack", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.SQ_HACK,
                    sets = rangeRirSets(2, 6, 10, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "ext", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.LEG_EXT,
                    sets = rangeRirSets(2, 6, 10, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "sldl", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.SLDL,
                    sets = rangeRirSets(3, 5, 8, rir), restSeconds = 240,
                    intent = SlotIntent.F, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "curl-lying", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CURL_L,
                    sets = rangeRirSets(2, 6, 10, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "calf-stand", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CALF_STANDING,
                    sets = rangeRirSets(3, 6, 10, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "calf-sit", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CALF_SEATED,
                    sets = rangeRirSets(2, 6, 10, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                    supplementalOf = "calf-stand",
                ),
            ),
        ),
        DayRecipe(
            label = "Espalda/hombros hipertrofia",
            id = "phat-back-shoulder-hypertrophy",
            weekday = 4,
            priority = SlotPriority.SPEED,
            slots = listOf(
                speedSlot(
                    id = "speed-row", configurationId = CatalogIds.PENDLAY, sourceSlotId = "row-pendlay",
                ),
                authoredSlot(
                    id = "rack-chin", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.RACK_CHIN,
                    sets = rangeRirSets(3, 8, 12, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "row-cable", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.ROW_CABLE,
                    sets = rangeRirSets(3, 8, 12, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "row-cs", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CSR,
                    sets = rangeRirSets(2, 12, 15, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 2),
                    supplementalOf = "row-cable",
                ),
                // C-02 (M4, B.S6): el jalón con agarre cerrado es una configuración propia del catálogo, no `LAT` + `CLOSE_GRIP`.
                authoredSlot(
                    id = "lat-close", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.LAT_CLOSE_GRIP,
                    sets = rangeRirSets(2, 15, 20, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 2),
                    supplementalOf = "rack-chin",
                ),
                authoredSlot(
                    id = "press-db", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.SEATED_PRESS_DB,
                    sets = rangeRirSets(3, 8, 12, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "upright", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.UPRIGHT_ROW,
                    sets = rangeRirSets(2, 12, 15, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "lat-raise", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.LATERAL,
                    sets = rangeRirSets(3, 12, 20, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 3),
                ),
            ),
        ),
        DayRecipe(
            label = "Inferior hipertrofia",
            id = "phat-lower-hypertrophy",
            weekday = 5,
            priority = SlotPriority.SPEED,
            slots = listOf(
                speedSlot(
                    id = "speed-sq", configurationId = CatalogIds.SQ_HIGH, sourceSlotId = "sq",
                ),
                authoredSlot(
                    id = "hack", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.SQ_HACK,
                    sets = rangeRirSets(3, 8, 12, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "press", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.PRESS_LEG,
                    sets = rangeRirSets(2, 12, 15, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(2, 2),
                    supplementalOf = "hack",
                ),
                authoredSlot(
                    id = "ext", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.LEG_EXT,
                    sets = rangeRirSets(3, 15, 20, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "rdl", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.RDL,
                    sets = rangeRirSets(3, 8, 12, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "curl-lying", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CURL_L,
                    sets = rangeRirSets(2, 12, 15, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "curl-seated", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CURL_H,
                    sets = rangeRirSets(2, 15, 20, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                    supplementalOf = "curl-lying",
                ),
                authoredSlot(
                    id = "calf-donkey", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CALF_DONKEY,
                    sets = rangeRirSets(4, 10, 15, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(4, 4),
                ),
                authoredSlot(
                    id = "calf-sit", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CALF_SEATED,
                    sets = rangeRirSets(3, 15, 20, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 3),
                    supplementalOf = "calf-donkey",
                ),
            ),
        ),
        DayRecipe(
            label = "Pecho/brazos hipertrofia",
            id = "phat-chest-arms-hypertrophy",
            weekday = 6,
            priority = SlotPriority.SPEED,
            slots = listOf(
                speedSlot(
                    id = "speed-bp", configurationId = CatalogIds.BP_DB, sourceSlotId = "bench-db",
                ),
                authoredSlot(
                    id = "inc-db", role = SlotRole.T2_SUPPLEMENTAL, configurationId = CatalogIds.BP_INC_DB,
                    sets = rangeRirSets(3, 8, 12, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 3),
                    supplementalOf = "speed-bp",
                ),
                authoredSlot(
                    id = "hammer", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.HAMMER_CHEST_PRESS,
                    sets = rangeRirSets(3, 12, 15, rir), restSeconds = 120,
                    intent = SlotIntent.H, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "fly-inc", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.FLY_INC_CABLE,
                    sets = rangeRirSets(2, 15, 20, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "preacher", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.PREACHER_EZ,
                    sets = rangeRirSets(3, 8, 12, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "conc", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.CONCENTRATION_DB,
                    sets = rangeRirSets(2, 12, 15, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "spider", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.SPIDER_DB,
                    sets = rangeRirSets(2, 15, 20, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "overhead-ez", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.OVERHEAD_EZ,
                    sets = rangeRirSets(3, 8, 12, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(3, 3),
                ),
                authoredSlot(
                    id = "pushdown", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.PUSHDOWN,
                    sets = rangeRirSets(2, 12, 15, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                ),
                authoredSlot(
                    id = "kickback", role = SlotRole.T3_ACCESSORY, configurationId = CatalogIds.KICKBACK_CABLE,
                    sets = rangeRirSets(2, 15, 20, rir), restSeconds = 90,
                    intent = SlotIntent.I, authoredSetRange = AuthoredSetRange(2, 2),
                ),
            ),
        ),
    )

    private fun phatExemptions(): List<RecipeCompositionExemption> {
        val url = AuthoredSources.PHAT_URL
        fun scoped(rule: String, scope: String, justification: String) =
            RecipeCompositionExemption(rule = rule, scope = scope, justification = justification, sourceUrl = url)
        return listOf(
            scoped(
                "H6", "w*/Pecho/brazos hipertrofia",
                "§10.3: el ejemplo publicado agrupa 10 ejercicios en el día de pecho/brazos (oráculo 28 series).",
            ),
            scoped(
                "H1", "w*/Superior fuerza",
                "§10.3: la tabla ordena el remo pesado antes del press de banca pesado del mismo día.",
            ),
            scoped(
                "H1", "w*/Inferior fuerza",
                "§10.3: la tabla ordena la extensión de cuádriceps antes del peso muerto de piernas rígidas.",
            ),
            scoped(
                "H1", "w*/Inferior hipertrofia",
                "§10.3: la tabla ordena la extensión de cuádriceps antes del peso muerto rumano.",
            ),
            // B.S6: la H3 de «Superior fuerza» estaba muerta (el rack chin cuelga de la dominada con `supplementalOf`, así que
            // el par no cuenta como tres dorsales seguidos) y se retira.
            scoped(
                "H3", "w*/Inferior fuerza",
                "§10.3: sentadilla, hack y extensión de cuádriceps seguidos por la misma dominante.",
            ),
            scoped(
                "H3", "w*/Espalda/hombros hipertrofia",
                "§10.3: el día concentra cinco movimientos de espalda con la misma dominante.",
            ),
            scoped(
                "H3", "w*/Inferior hipertrofia",
                "§10.3: sentadilla rápida, hack, prensa y extensión con la misma dominante (cuádriceps).",
            ),
            scoped(
                "H3", "w*/Pecho/brazos hipertrofia",
                "§10.3: cuatro movimientos de pecho seguidos y tres de bíceps seguidos en la tabla.",
            ),
        )
    }

    // ─── Constructores comunes ───────────────────────────────────────────────

    private const val PHUL_WEEKS = 12
    private const val PHAT_WEEKS = 6

    /** RIR de semanas 5–6 de PHAT: rango publicado «1–2» (§10.3). */
    private const val PHAT_LATE_RIR = 1

    private fun authoredSlot(
        id: String,
        role: SlotRole,
        configurationId: String,
        sets: List<SetRecipe>,
        restSeconds: Int,
        intent: SlotIntent,
        authoredSetRange: AuthoredSetRange,
        supplementalOf: String? = null,
        technique: TechniqueModifier? = null,
        isUnilateral: Boolean = false,
        isCompetitionLift: Boolean = false,
        priority: SlotPriority = SlotPriority.NORMAL,
    ): SlotRecipe = SlotRecipe(
        id = id,
        role = role,
        lift = com.example.kpkn.data.protocols.LiftRef(configurationId = configurationId, liftSlot = null),
        sets = sets,
        restSeconds = restSeconds,
        technique = technique,
        supplementalOf = supplementalOf,
        isUnilateral = isUnilateral,
        isCompetitionLift = isCompetitionLift,
        priority = priority,
        source = com.example.kpkn.data.protocols.SlotSource.AUTHOR,
        intent = intent,
        authoredSetRange = authoredSetRange,
    )

    /**
     * Bloque SPEED de PHAT (§10.3/§14.2): 6×3 al **65 %** (rango 65–70 %) de la
     * carga de trabajo de 3–5 reps observada en el MISMO ejercicio del día
     * pesado enlazado. La referencia lleva la base visible; `loadBasis` nunca es
     * `PERCENT_TM`/`PERCENT_1RM`, porque 65 % del trabajo observado ≠ 65 % de
     * TM ni de 1RM. Sin referencia capturada la carga queda PENDIENTE
     * (null ≠ 0 kg) hasta registrar la serie pesada (§14.2).
     */
    private fun speedSlot(
        id: String,
        configurationId: String,
        sourceSlotId: String,
    ): SlotRecipe = SlotRecipe(
        id = id,
        role = SlotRole.SPEED,
        lift = com.example.kpkn.data.protocols.LiftRef(configurationId = configurationId, liftSlot = null),
        sets = List(6) {
            SetRecipe(
                reps = 3,
                percent = SPEED_PERCENT,
                loadBasis = LoadBasis.RPE,
                reference = PlanLoadReference(
                    kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
                    configurationId = configurationId,
                    sourceSlotId = sourceSlotId,
                    repMin = 3,
                    repMax = 5,
                ),
            )
        },
        restSeconds = 90,
        technique = TechniqueModifier.SPEED,
        priority = SlotPriority.SPEED,
        source = com.example.kpkn.data.protocols.SlotSource.AUTHOR,
        intent = SlotIntent.P,
        authoredSetRange = AuthoredSetRange(6, 6),
        explicitReference = SlotLoadReferenceMetadata(
            configurationId = configurationId,
            repMin = 3,
            repMax = 5,
            note = "65 % de la carga de trabajo de 3-5 reps del día pesado enlazado (§14.2).",
        ),
    )

    /** Magnitud publicada del SPEED de PHAT: 65 % dentro del rango 65–70 %. */
    const val SPEED_PERCENT = 65.0

    private fun adaptedProvenance(
        recipeId: String,
        parent: TrainingPlanRecipe,
        source: AuthoredSourceRecord,
        note: String,
    ): PlanProvenance {
        val base = parent.provenance ?: error("El original '${parent.id}' debe publicar procedencia")
        return base.copy(
            planId = recipeId,
            recipeId = recipeId,
            revision = 1,
            category = PlanProvenanceClass.ADAPTED,
            parentId = parent.id,
            parentRevision = 1,
            slotChanges = emptyList<PlanSlotChange>(),
            operationalDefaults = base.operationalDefaults + KpknOperationalDefault(
                scope = KpknOperationalDefaultScope.INITIAL_CHOICE,
                note = note,
            ),
            sourceEdition = source.editionWithConsult,
        )
    }
}
