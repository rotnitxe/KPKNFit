package com.example.kpkn.domain.biomechanics.profiles

import java.io.File
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura del catálogo real (`exercise_catalog_v2.json`): cada configuración resuelve a `Supported` o a un
 * `Unsupported` explícito, ningún patrón queda fuera de la tabla y el ángulo de ROM del perfil es una articulación que
 * el catálogo da como principal o secundaria. Lee el JSON directamente, sin el cargador del catálogo (aislamiento).
 */
class AnalysisProfileResolverCoverageTest {

    private class CatalogConfiguration(
        val definitionId: String,
        val configurationId: String,
        val patternId: String,
        val equipmentId: String,
        val laterality: String,
        val name: String,
        /** Articulaciones PRIMARY o SECONDARY (ids en español: rodilla, cadera, codo, glenohumeral…). */
        val movers: Set<String>,
    ) {
        fun toRef() = ExerciseRef(
            definitionId = definitionId, configurationId = configurationId, movementPatternId = patternId,
            equipmentId = equipmentId, laterality = laterality, name = name, isCustom = false,
        )
    }

    private companion object {
        val catalog: List<CatalogConfiguration> by lazy {
            val file = listOf(
                File("src/main/assets/exercise_catalog_v2.json"),
                File("app/src/main/assets/exercise_catalog_v2.json"),
            ).first { it.exists() }
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            root.getValue("families").jsonArray.flatMap { family ->
                family.jsonObject.getValue("definitions").jsonArray.flatMap { definitionElement ->
                    val definition = definitionElement.jsonObject
                    val definitionId = definition.getValue("id").jsonPrimitive.content
                    val name = definition["canonicalName"]?.jsonPrimitive?.content ?: definitionId
                    definition.getValue("configurations").jsonArray.map { configurationElement ->
                        val configuration = configurationElement.jsonObject
                        val profile = configuration.getValue("profile").jsonObject
                        val movers = profile.getValue("jointInvolvement").jsonArray
                            .map { it.jsonObject }
                            .filter { it.getValue("role").jsonPrimitive.content in setOf("PRIMARY", "SECONDARY") }
                            .map { it.getValue("jointId").jsonPrimitive.content }
                            .toSet()
                        CatalogConfiguration(
                            definitionId = definitionId,
                            configurationId = configuration.getValue("id").jsonPrimitive.content,
                            patternId = profile.getValue("movementPatternId").jsonPrimitive.content,
                            equipmentId = profile.getValue("equipmentId").jsonPrimitive.content,
                            laterality = profile.getValue("laterality").jsonPrimitive.content,
                            name = name,
                            movers = movers,
                        )
                    }
                }
            }
        }

        /** Articulaciones del catálogo (ids en español) que valen para cada ángulo de ROM. */
        val jointsOf = mapOf(
            RomAngle.KNEE to setOf("rodilla"),
            RomAngle.HIP to setOf("cadera"),
            RomAngle.ELBOW to setOf("codo"),
            RomAngle.ARM_ELEVATION to setOf("glenohumeral"),
            RomAngle.TRUNK to setOf("cadera", "columna-lumbar"),
        )

        /**
         * Lista blanca de excepciones de articulación: configuraciones cuya articulación principal NO es la del ángulo de
         * ROM del perfil, pero donde el ángulo sí mide el movimiento.
         *
         * Las dos son extensiones de espalda con la lumbar como articulación principal y la cadera como estabilizadora
         * (la pelvis queda fija en el apoyo). El perfil BACK_EXTENSION mide 180−θ(S,H,K), el ángulo tronco–muslo: es lo
         * único observable, porque MediaPipe no tiene puntos de columna. El tronco se mueve sobre la pelvis fija y ese
         * ángulo cambia igual, así que el ROM sirve como estimación del recorrido del tronco.
         */
        val jointWhitelist = setOf(
            "back_extension_lumbar__default",
            "back_hiperextension_45_zercher_espalda_baja__default",
        )

        fun percent(part: Int, whole: Int) = String.format(Locale.ROOT, "%.1f %%", 100.0 * part / whole)
    }

    private fun resolve(configuration: CatalogConfiguration) = AnalysisProfileResolver.resolve(configuration.toRef())

    @Test
    fun theCatalogHasBeenReadFromTheRealAsset() {
        // 553 configuraciones y 63 patrones el 13-oct-2026 (ExerciseCatalogContractTest fija el conteo exacto).
        assertTrue("Catálogo vacío o incompleto: ${catalog.size}", catalog.size >= 500)
        println("[VT2a] Catálogo: ${catalog.size} configuraciones, ${catalog.map { it.patternId }.toSet().size} patrones.")
    }

    @Test
    fun everyConfigurationResolvesToSupportedOrAnExplicitUnsupported() {
        val problems = mutableListOf<String>()
        for (configuration in catalog) {
            when (val resolution = resolve(configuration)) {
                is ProfileResolution.Supported -> Unit
                is ProfileResolution.Unsupported ->
                    if (resolution.reason == UnsupportedReasons.UNKNOWN_PATTERN) {
                        problems += "${configuration.configurationId}: patrón «${configuration.patternId}» sin entrada en la tabla"
                    }
                is ProfileResolution.NeedsUserChoice -> problems += "${configuration.configurationId}: NeedsUserChoice"
            }
        }
        assertTrue("Configuraciones sin resolver:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun noCatalogPatternIsMissingFromTheTable() {
        val missing = catalog.map { it.patternId }.toSortedSet().filter { pattern ->
            val ref = ExerciseRef(null, null, pattern, null, null, pattern, isCustom = false)
            AnalysisProfileResolver.resolve(ref) == ProfileResolution.Unsupported(UnsupportedReasons.UNKNOWN_PATTERN)
        }
        assertTrue(
            "Patrones nuevos del catálogo sin entrada en AnalysisProfileResolver (PATTERN_RULES): $missing",
            missing.isEmpty(),
        )
        // La tabla del brief cubre exactamente los patrones del catálogo, ni uno más.
        assertEquals(catalog.map { it.patternId }.toSortedSet(), ProfileExpectations.byPattern.keys.toSortedSet())
    }

    @Test
    fun supportedShareIsAtLeastSeventyFivePercentAndPerProfileCountsArePrinted() {
        val supported = mutableListOf<AnalysisProfile>()
        val reasons = sortedMapOf<String, Int>()
        for (configuration in catalog) {
            when (val resolution = resolve(configuration)) {
                is ProfileResolution.Supported -> supported += resolution.profile
                is ProfileResolution.Unsupported -> reasons.merge(resolution.reason, 1) { a, b -> a + b }
                is ProfileResolution.NeedsUserChoice -> Unit
            }
        }
        val perProfile = AnalysisProfileId.entries.associateWith { id -> supported.count { it.id == id } }
        println("[VT2a] Supported: ${supported.size}/${catalog.size} (${percent(supported.size, catalog.size)}).")
        println("[VT2a] Por perfil: " + perProfile.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
        println("[VT2a] Unsupported por motivo: " + reasons.entries.joinToString { "${it.key}=${it.value}" })
        assertTrue(
            "Supported ${supported.size}/${catalog.size} (${percent(supported.size, catalog.size)}) < 75 %",
            supported.size * 100 >= catalog.size * 75,
        )
        // Cada perfil sale al menos una vez en el catálogo: ninguno de los 23 es letra muerta.
        val unused = perProfile.filterValues { it == 0 }.keys
        assertTrue("Perfiles sin ninguna configuración del catálogo: $unused", unused.isEmpty())
    }

    @Test
    fun everyUnsupportedReasonIsAKnownCodeWithItsSpanishText() {
        for (configuration in catalog) {
            val resolution = resolve(configuration) as? ProfileResolution.Unsupported ?: continue
            assertTrue("${configuration.configurationId}: motivo ${resolution.reason}", resolution.reason in UnsupportedReasons.texts)
            assertTrue(resolution.text.isNotBlank())
        }
    }

    @Test
    fun resolvesEveryConfigurationExactlyAsTheBriefTable() {
        // Cada id de excepción del brief existe en el catálogo (un id mal escrito no se enteraría nunca).
        val definitions = catalog.map { it.definitionId }.toSet()
        val absent = ProfileExpectations.byDefinition.keys.filter { it !in definitions }
        assertTrue("Excepciones del brief con una definición que no está en el catálogo: $absent", absent.isEmpty())

        val mismatches = mutableListOf<String>()
        for (configuration in catalog) {
            val expected = ProfileExpectations.byDefinition[configuration.definitionId]
                ?: ProfileExpectations.byPattern[configuration.patternId]
            if (expected == null) {
                mismatches += "${configuration.configurationId}: el patrón «${configuration.patternId}» no está en la tabla del brief"
                continue
            }
            val actual = resolve(configuration)
            val ok = when (actual) {
                is ProfileResolution.Supported ->
                    expected.profile == actual.profile.id &&
                        (expected.startPhase == null || expected.startPhase == actual.profile.startPhase)
                is ProfileResolution.Unsupported -> expected.profile == null
                is ProfileResolution.NeedsUserChoice -> false
            }
            if (!ok) mismatches += "${configuration.configurationId}: esperado ${expected.profile ?: "UNSUPPORTED"}, resultó $actual"
        }
        assertTrue("Discrepancias con la tabla del brief:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun romAngleJointIsAmongThePrimaryOrSecondaryJointsOfTheCatalog() {
        val exceptions = mutableListOf<String>()
        var checked = 0
        for (configuration in catalog) {
            val profile = (resolve(configuration) as? ProfileResolution.Supported)?.profile ?: continue
            val angle = profile.romAngle ?: continue
            checked++
            val accepted = jointsOf.getValue(angle)
            if (accepted.none { it in configuration.movers }) {
                exceptions += "${configuration.configurationId} (${profile.id}, $angle → ${accepted.joinToString("/")}): " +
                    "articulaciones principales o secundarias = ${configuration.movers.sorted()}"
            }
        }
        val unexplained = exceptions.filter { line -> jointWhitelist.none { line.startsWith("$it ") } }
        val whitelisted = exceptions.size - unexplained.size
        println("[VT2a] Articulación del ROM: $checked configuraciones con ROM, ${exceptions.size} excepciones ($whitelisted en lista blanca).")
        exceptions.forEach { println("[VT2a]   excepción: $it") }
        val stale = jointWhitelist.filter { id -> exceptions.none { it.startsWith("$id ") } }
        if (stale.isNotEmpty()) println("[VT2a]   lista blanca que ya no hace falta: $stale")
        assertTrue("Excepciones de articulación sin justificar:\n" + unexplained.joinToString("\n"), unexplained.isEmpty())
    }

    @Test
    fun usesPlateFollowsTheEquipmentAndOnlyTheFourFramingProfilesAskForThePlate() {
        val plateEquipment = setOf("barbell", "smith_machine", "hex_bar", "safety_bar", "t_bar")
        val framingProfiles = setOf(AnalysisProfileId.SQUAT, AnalysisProfileId.DEADLIFT, AnalysisProfileId.BENCH, AnalysisProfileId.OHP)
        var withPlate = 0
        for (configuration in catalog) {
            val profile = (resolve(configuration) as? ProfileResolution.Supported)?.profile ?: continue
            val expected = profile.plateSignal != null && configuration.equipmentId in plateEquipment
            assertEquals(configuration.configurationId, expected, profile.usesPlate)
            assertEquals(configuration.configurationId, profile.id in framingProfiles && expected, profile.framing.needsPlate)
            if (profile.usesPlate) {
                withPlate++
                assertNotNull(configuration.configurationId, profile.plateMinAmplitude)
            }
        }
        println("[VT2a] Configuraciones con el disco como señal principal: $withPlate.")
        assertTrue(withPlate > 0)
    }

    @Test
    fun mvtAppearsOnlyOnTheListedDefinitionsWithTheirEquipment() {
        val expected = HashMap<Pair<String, String>, Double>()
        for (definition in listOf("high_bar_back_squat", "low_bar_back_squat", "paused_back_squat")) {
            for (equipment in listOf("barbell", "smith_machine")) expected[definition to equipment] = 0.30
        }
        for (definition in listOf("bench_press", "paused_bench_press")) {
            for (equipment in listOf("barbell", "smith_machine")) expected[definition to equipment] = 0.17
        }
        expected["conventional_deadlift" to "barbell"] = 0.15
        expected["sumo_deadlift" to "barbell"] = 0.15
        expected["military_press" to "barbell"] = 0.19
        expected["seal_row" to "barbell"] = 0.50

        var withMvt = 0
        for (configuration in catalog) {
            val profile = (resolve(configuration) as? ProfileResolution.Supported)?.profile ?: continue
            assertEquals(configuration.configurationId, expected[configuration.definitionId to configuration.equipmentId], profile.mvtMps)
            if (profile.mvtMps != null) withMvt++
        }
        println("[VT2a] Configuraciones con MVT: $withMvt.")
        assertEquals(catalog.count { (it.definitionId to it.equipmentId) in expected }, withMvt)
    }
}
