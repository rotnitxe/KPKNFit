package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.domain.biomechanics.profiles.ProfileTier
import com.example.kpkn.domain.biomechanics.profiles.StartPhase
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Banco reproducible: solo trazas derivadas, etiquetas y atribución; nunca abre vídeos ni el JSON del arnés Android. */
internal object GoldenReport {
    const val COMPATIBLE_COUNT = 97
    const val EXTERNAL_COUNT = 416
    const val MAX_FIXTURE_BYTES = 15L * 1024 * 1024
    const val PREEXISTING_SMOKE = "x14-commons-squat"
    val TUNING_SOURCES = setOf("pexels", "mixkit", "wger")
    val HOLDOUT_SOURCES = setOf("pixabay", "commons", "YMove")
    val EXTERNAL_SOURCES = setOf("Kaggle_WorkoutFitness", "UCF101")
    val PILOT_IDS = setOf("g25-pexels-8456990", "r14-pexels-4921641", "wger_0002")
    val RELIABLE_TIMING_PREFIXES = setOf("r02", "r03", "r08", "r09", "r10", "r15", "r16", "r17", "r18", "r19", "x05", "x06", "x07", "x08", "x12", "x13", "x14")
    const val TIMING_TOLERANCE_US = 250_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val safeId = Regex("[A-Za-z0-9_-]+")
    private val finalHoldoutCache = mutableMapOf<String, List<GoldenResult>>()

    fun compatibleRoot(): File {
        val explicit = System.getProperty("kpkn.golden.root") ?: System.getenv("KPKN_GOLDEN_DIR")
        if (!explicit.isNullOrBlank()) return File(explicit).canonicalFile
        val appRoot = System.getProperty("APP_ROOT") ?: System.getenv("APP_ROOT")
        return File(appRoot?.let(::File) ?: File("."), "src/test/resources/golden").canonicalFile
    }

    fun load(root: File = compatibleRoot(), external: Boolean = false): GoldenCorpus {
        val manifest = File(root, "manifest.json")
        require(manifest.isFile) { "Falta ${manifest.absolutePath}; APP_ROOT debe apuntar al módulo app o KPKN_GOLDEN_DIR a golden/." }
        val obj = readObject(manifest)
        val outputHashFile = File(root, "trace-hashes.json")
        val outputHashes = if (external && outputHashFile.isFile) {
            val hashes = readObject(outputHashFile)
            require(hashes.stringValue("manifestSha256") == sha256(manifest.readBytes())) { "trace-hashes.json pertenece a otro manifiesto externo." }
            val hashRows = hashes.getValue("rows").jsonArray.map { it.jsonObject }
            require(hashRows.map { it.stringValue("id") }.toSet().size == hashRows.size) { "IDs repetidos en trace-hashes.json" }
            hashRows.associateBy { it.stringValue("id") }
        } else emptyMap()
        val expected = obj.intValue("expectedCount")
        require(expected == if (external) EXTERNAL_COUNT else COMPATIBLE_COUNT) { "El manifiesto debe conservar el universo congelado completo, no solo los clips extraídos: $expected." }
        require(obj.boolValue("partitionFrozenBeforeExtraction")) { "Reparto sin congelar." }
        val rows = obj.getValue("rows").jsonArray.map { entry ->
            val row = entry.jsonObject
            val id = row.stringValue("id")
            require(safeId.matches(id)) { "ID inválido: $id" }
            GoldenFixture(
                id = id,
                source = row.stringValue("source"),
                subset = row.stringValue("subset"),
                profileId = AnalysisProfileId.valueOf(row.stringValue("profile")),
                equipmentId = row.optionalString("equipmentId"),
                labelSha256 = row.stringValue("labelSha256"),
                poseSha256 = row.optionalString("poseSha256") ?: outputHashes[id]?.optionalString("poseSha256"),
                barSha256 = row.optionalString("barSha256") ?: outputHashes[id]?.optionalString("barSha256"),
                preexistingSmoke = row.boolValue("preexistingSmoke", false),
                sourceGroup = row.optionalString("sourceGroup"),
                labelPath = row.optionalString("labelPath"),
                posePath = row.optionalString("posePath"),
                barPath = row.optionalString("barPath"),
            )
        }
        require(rows.size == expected && rows.map { it.id }.toSet().size == expected) { "Manifiesto incompleto o con IDs repetidos." }
        require(outputHashes.keys.all { id -> rows.any { it.id == id } }) { "trace-hashes.json contiene un clip fuera del manifiesto." }
        for (row in rows) {
            require(row.source in if (external) EXTERNAL_SOURCES else TUNING_SOURCES + HOLDOUT_SOURCES) { "Fuente no permitida: ${row.source}" }
            if (!external) {
                val expectedSubset = if (row.source in TUNING_SOURCES) "tuning" else "holdout"
                require(row.subset == expectedSubset) { "Reparto alterado: ${row.id}" }
                require(row.preexistingSmoke == (row.id == PREEXISTING_SMOKE)) { "El humo previo x14 debe excluirse de la aceptación ciega." }
            } else {
                require(row.source != "Kaggle_WorkoutFitness" || row.subset == "isolated") { "Kaggle debe conservar el banco isolated, sin reparto 60/40." }
                require(row.source != "UCF101" || (row.subset == "grouped" && !row.sourceGroup.isNullOrBlank() && row.sourceGroup != "unknown")) { "UCF101 requiere subset grouped y sourceGroup real." }
            }
            checkHash(fileAt(root, row.labelPath, "labels/${row.id}.json", external), row.labelSha256)
        }
        if (!external) {
            require(rows.count { it.subset == "tuning" } == 59 && rows.count { it.subset == "holdout" } == 38) { "El reparto congelado es 59/38." }
            require(root.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= MAX_FIXTURE_BYTES) { "golden/ supera 15 MiB." }
        }
        return GoldenCorpus(root, rows, obj.stringValue("frozenManifestSha256"), external)
    }

    /** Nunca lee resultados de la reserva al producir el diagnóstico de ajuste. */
    fun tuningFixtures(corpus: GoldenCorpus): List<GoldenFixture> = corpus.fixtures.filter { it.subset == "tuning" }

    fun missingTraces(corpus: GoldenCorpus): List<String> = corpus.fixtures.filterNot {
        isExtracted(corpus, it) && (it.barSha256 == null || barFile(corpus, it).isFile)
    }.map { it.id }

    /** Conteo y tiempos comparten la misma pasada final dentro del JVM; nunca se usa desde las pruebas de ajuste. */
    @Synchronized
    fun finalHoldout(corpus: GoldenCorpus): List<GoldenResult> {
        require(!corpus.external && missingTraces(corpus).isEmpty()) { "La reserva exige las 97 extracciones compatibles." }
        integrityPreflight(corpus)
        val key = corpus.root.canonicalPath + ":" + corpus.manifestSha256
        return finalHoldoutCache.getOrPut(key) {
            evaluateAvailable(corpus, corpus.fixtures.filter { it.subset == "holdout" && !it.preexistingSmoke })
        }
    }

    fun isExtracted(corpus: GoldenCorpus, fixture: GoldenFixture): Boolean =
        fixture.poseSha256 != null && poseFile(corpus, fixture).isFile

    fun evaluateAvailable(corpus: GoldenCorpus, fixtures: List<GoldenFixture>): List<GoldenResult> =
        fixtures.filter { isExtracted(corpus, it) }.map { evaluate(corpus, it) }

    fun evaluate(corpus: GoldenCorpus, fixture: GoldenFixture): GoldenResult {
        val label = label(corpus, fixture)
        val profile = profile(fixture, label)
        val exclusions = exclusions(fixture, label, profile)
        val (pose, bar) = readTracks(corpus, fixture)
        val start = label.activeWindowUs?.first ?: pose.frames.firstOrNull()?.tUs ?: 0L
        val end = label.activeWindowUs?.second ?: pose.frames.lastOrNull()?.tUs ?: start
        val croppedPose = pose.copy(frames = pose.frames.filter { it.tUs in start..end })
        val croppedBar = bar?.copy(samples = bar.samples.filter { it.tUs in start..end })
        require(croppedPose.frames.isNotEmpty()) { "Ventana sin fotogramas: ${fixture.id}" }
        val report = TechniqueAnalysisEngine.analyze(
            TechniqueAnalysisInput(croppedPose, croppedBar, profile, null, null, emptySet()),
        )
        require(report.repCount == report.reps.count { it.kind == RepKind.FULL }) { "FULL incoherentes: ${fixture.id}" }
        require(report.partialReps == report.reps.count { it.kind == RepKind.PARTIAL }) { "PARTIAL incoherentes: ${fixture.id}" }
        require(report.failedReps == report.reps.count { it.kind == RepKind.FAILED }) { "FAILED incoherentes: ${fixture.id}" }
        return GoldenResult(fixture, label, profile, report, exclusions)
    }

    /** Comprueba TODAS las fixtures, incluido ajuste y humo previo, sin ejecutar el motor ni observar métricas de reserva. */
    fun integrityPreflight(corpus: GoldenCorpus) {
        require(missingTraces(corpus).isEmpty()) { "Preflight incompleto: ${missingTraces(corpus).joinToString()}" }
        for (fixture in corpus.fixtures) {
            checkHash(fileAt(corpus.root, fixture.labelPath, "labels/${fixture.id}.json", corpus.external), fixture.labelSha256)
            profile(fixture, label(corpus, fixture))
            readTracks(corpus, fixture)
        }
    }

    private fun readTracks(corpus: GoldenCorpus, fixture: GoldenFixture): Pair<PoseTrackV2, BarTrack?> {
        val poseFile = poseFile(corpus, fixture)
        checkHash(poseFile, requireNotNull(fixture.poseSha256) { "Falta extracción: ${fixture.id}" })
        val pose = requireNotNull(PoseTrackCodec.decode(poseFile.readBytes())) { "Pose v2 inválida: ${fixture.id}" }
        require(pose.version == 2 && pose.frames.isNotEmpty() && pose.mediaId == fixture.id && pose.engine.model == "full") { "Traza de otro clip/modelo o vacía: ${fixture.id} (${pose.mediaId}/${pose.engine.model})." }
        val barFile = barFile(corpus, fixture)
        val bar = fixture.barSha256?.let { hash ->
            checkHash(barFile, hash)
            requireNotNull(BarTrackCodec.decode(barFile.readBytes())) { "BarTrack inválida: ${fixture.id}" }
                .also { require(it.version == 1 && it.mediaId == fixture.id && it.frameWidth == pose.frameWidth && it.frameHeight == pose.frameHeight) { "Barra de otro clip/tamaño: ${fixture.id}" } }
        }
        require(fixture.barSha256 != null || !barFile.exists()) { "Disco presente sin hash en manifiesto: ${fixture.id}" }
        return pose to bar
    }

    fun label(corpus: GoldenCorpus, fixture: GoldenFixture): GoldenLabel {
        val obj = readObject(fileAt(corpus.root, fixture.labelPath, "labels/${fixture.id}.json", corpus.external))
        require(obj.stringValue("id") == fixture.id && obj.stringValue("suggestedProfile") == fixture.profileId.name) { "Perfil/ID de etiqueta distinto del manifiesto: ${fixture.id}" }
        val reps = obj.getValue("reps").jsonObject
        val range = reps.getValue("acceptableRange").jsonArray.map { it.jsonPrimitive.int }
        require(range.size == 2 && range[0] >= 0 && range[0] <= range[1]) { "Rango inválido: ${fixture.id}" }
        val window = obj["activeWindowS"]?.takeUnless { it is JsonNull }?.jsonArray?.map { secondsUs(it.jsonPrimitive.double) }
        val events = obj["repTimesS"]?.jsonArray.orEmpty().map { e ->
            val t = e.jsonObject
            val pause = t["windowS"]?.takeUnless { it is JsonNull }?.jsonArray?.map { secondsUs(it.jsonPrimitive.double) }
            GoldenTurn(secondsUs(t.getValue("turnaroundS").jsonPrimitive.double), normalizedSide(t.optionalString("side")), pause?.let { it[0] to it[1] })
        }
        val edges = obj["edgeEvents"]?.jsonArray.orEmpty().map { e ->
            val edge = e.jsonObject
            GoldenEdge(secondsUs(edge.getValue("timeS").jsonPrimitive.double), edge.stringValue("kind"))
        }
        return GoldenLabel(
            id = fixture.id, full = reps.intValue("full"), partial = reps.intValue("partial"), failed = reps.intValue("failed"),
            uncertain = reps.boolValue("uncertain"), rangeMin = range[0], rangeMax = range[1], usable = obj.boolValue("usable"),
            view = obj.optionalString("view"), exercise = obj.stringValue("exercise"), turnaroundKind = obj.stringValue("turnaroundKind"),
            activeWindowUs = window?.let { require(it.size == 2 && it[0] <= it[1]); it[0] to it[1] },
            turns = events, edges = edges, warnings = obj["softWarnings"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
        )
    }

    private fun profile(fixture: GoldenFixture, label: GoldenLabel): AnalysisProfile {
        val resolved = AnalysisProfileResolver.resolve(
            ExerciseRef(null, null, null, fixture.equipmentId, "BILATERAL", label.exercise, true, fixture.profileId),
        )
        require(resolved is ProfileResolution.Supported) { "Perfil no resuelto: ${fixture.id}" }
        return resolved.profile
    }

    private fun exclusions(fixture: GoldenFixture, label: GoldenLabel, profile: AnalysisProfile): Set<String> = buildSet {
        if (!label.usable) add("not_usable")
        if (label.uncertain) add("uncertain")
        if (label.rangeMin == 0) add("zero_range")
        if (fixture.preexistingSmoke) add("preexisting_smoke")
        if (fixture.source == "Kaggle_WorkoutFitness") add("isolated_repetition")
        if (fixture.id.startsWith("ucf_v_WallPushups_") || label.warnings.any { "WALL_PUSH_UP" in it }) add("wall_push_up")
        if (fixture.id.startsWith("ucf_v_BenchPress_")) add("ucf_bench_homogeneous")
        if (label.view == "BACK" && ViewKind.BACK !in profile.countViews) add("negative_back_view")
    }

    /** La exactitud usa solo las salidas reales; la ausencia de REPS se publica como cobertura, nunca como cero correcto. */
    fun counts(results: List<GoldenResult>): GoldenCounts {
        val output = results.filter { it.hasOutput }
        val high = output.filter { it.report.confidence == CountConfidence.ALTA }
        return GoldenCounts(
            n = results.size, output = output.size, exactFull = output.count { it.report.repCount == it.label.full },
            withinOneFull = output.count { abs(it.report.repCount - it.label.full) <= 1 },
            inRange = output.count { it.rangeError == 0 }, withinOne = output.count { it.rangeError <= 1 },
            maxRangeError = output.maxOfOrNull { it.rangeError },
            partialExact = output.count { it.report.partialReps == it.label.partial },
            failedExact = output.count { it.report.failedReps == it.label.failed },
            high = high.size, highCorrect = high.count { it.rangeError == 0 },
        )
    }

    /** Historical range regression only; NOT the original acceptance contract. See OriginalContractAcceptanceTest. */
    fun countGateErrors(results: List<GoldenResult>): List<String> = buildList {
        val accepted = results.filter { it.exclusions.isEmpty() }
        val groups = accepted.groupBy { it.profile.id }.filterValues { it.size >= 3 }
        if (groups.isEmpty()) add("Ningún perfil tiene n >= 3 en la reserva elegible; no hay base para aceptación.")
        for ((profileId, rows) in groups) {
            val c = counts(rows)
            val tier = rows.first().profile.tier
            if (tier == ProfileTier.C) continue
            if (c.output < 3) { add("$profileId: solo ${c.output}/${c.n} salidas; precisión sin conclusión."); continue }
            val required = if (tier == ProfileTier.A) 0.95 else 0.90
            if (c.inRange.toDouble() / c.output < required) add("$profileId: ${c.inRange}/${c.output} dentro del rango; requiere ${formatPercent(required)}.")
        }
        val measured = accepted.filter { it.hasOutput }
        for (row in measured.filter { it.rangeError > 1 }) add("${row.fixture.id}: error ${row.rangeError} contra el extremo más cercano del rango.")
        val high = measured.filter { it.report.confidence == CountConfidence.ALTA }
        if (high.isEmpty()) add("Sin salidas ALTA: precisión ALTA no validada (cobertura 0).")
        else if (high.count { it.rangeError == 0 }.toDouble() / high.size < 0.98) add("ALTA: ${high.count { it.rangeError == 0 }}/${high.size} correctas; requiere 98 %.")
        for (row in results.filter { "negative_back_view" in it.exclusions && it.hasOutput && it.report.confidence == CountConfidence.ALTA }) {
            add("${row.fixture.id}: vista BACK no admitida y conteo ALTA.")
        }
    }

    /** La incertidumbre del N no invalida los giros fiables expresamente incluidos por VT10 (r03/r17). */
    fun timingEligible(result: GoldenResult): Boolean =
        result.fixture.id.substringBefore('-') in RELIABLE_TIMING_PREFIXES && result.label.usable &&
            !result.fixture.preexistingSmoke && "negative_back_view" !in result.exclusions

    fun timing(result: GoldenResult): GoldenTiming? {
        if (result.fixture.id.substringBefore('-') !in RELIABLE_TIMING_PREFIXES) return null
        val window = result.label.activeWindowUs ?: return null
        val expectedKind = if (result.profile.startPhase == StartPhase.ECCENTRIC_FIRST) "BOTTOM" else "TOP"
        require(result.label.turnaroundKind == expectedKind) { "${result.fixture.id}: giro de etiqueta ${result.label.turnaroundKind}, perfil $expectedKind (D30)." }
        fun inside(t: Long) = t in window.first..window.second
        fun edge(t: Long) = result.label.edges.any { it.kind in setOf("TRUNCATED_START", "TRUNCATED_END") && abs(it.timeUs - t) <= TIMING_TOLERANCE_US }
        val expected = result.label.turns.filter { it.pauseWindowUs == null && inside(it.timeUs) && !edge(it.timeUs) }.sortedBy { it.timeUs }
        val pauses = result.label.turns.mapNotNull { it.pauseWindowUs }
        val detected = result.report.reps.filter { rep ->
            val p = rep.phases
            val turn = (p.turnStartUs + p.turnEndUs) / 2
            p.departUs >= window.first && p.arriveUs <= window.second && inside(turn) && !edge(turn) &&
                pauses.none { turn in (it.first - TIMING_TOLERANCE_US)..(it.second + TIMING_TOLERANCE_US) }
        }.map { GoldenTurn((it.phases.turnStartUs + it.phases.turnEndUs) / 2, normalizedSide(it.side), null) }.sortedBy { it.timeUs }
        val pairs = matchTurns(expected, detected)
        return GoldenTiming(result.fixture.id, expected.size, detected.size, pairs.map { abs(expected[it.first].timeUs - detected[it.second].timeUs) }, expected.size - pairs.size, detected.size - pairs.size)
    }

    /** Alineamiento monótono, uno a uno y por lado. Maximiza pares y después minimiza su distancia; no reutiliza una rep. */
    fun matchTurns(expected: List<GoldenTurn>, detected: List<GoldenTurn>): List<Pair<Int, Int>> {
        val n = expected.size; val m = detected.size
        val matches = Array(n + 1) { IntArray(m + 1) }
        val costs = Array(n + 1) { LongArray(m + 1) }
        val choice = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) choice[i][0] = 1
        for (j in 1..m) choice[0][j] = 2
        for (i in 1..n) for (j in 1..m) {
            var count = matches[i - 1][j]; var cost = costs[i - 1][j]; var selected = 1
            fun prefer(c: Int, d: Long, how: Int) { if (c > count || (c == count && d < cost)) { count = c; cost = d; selected = how } }
            prefer(matches[i][j - 1], costs[i][j - 1], 2)
            val a = expected[i - 1]; val b = detected[j - 1]; val delta = abs(a.timeUs - b.timeUs)
            if ((a.side == null || a.side == b.side) && delta <= 2_000_000L) prefer(matches[i - 1][j - 1] + 1, costs[i - 1][j - 1] + delta, 3)
            matches[i][j] = count; costs[i][j] = cost; choice[i][j] = selected
        }
        val result = mutableListOf<Pair<Int, Int>>()
        var i = n; var j = m
        while (i > 0 || j > 0) when (choice[i][j]) {
            1 -> i--
            2 -> j--
            3 -> { result += (i - 1) to (j - 1); i--; j-- }
            else -> error("Alineamiento inválido")
        }
        return result.asReversed()
    }

    fun render(corpus: GoldenCorpus, results: List<GoldenResult>, title: String, finalGate: Boolean = false, holdoutEvaluation: Boolean = finalGate): String = buildString {
        appendLine("# $title")
        appendLine()
        appendLine("Motor ${TechniqueAnalysisEngine.ENGINE_VERSION}; manifiesto congelado SHA-256 `${corpus.manifestSha256}`.")
        val status = when {
            finalGate -> "Regresión histórica de reserva con acceptableRange; NO acredita aceptación del contrato original."
            holdoutEvaluation -> "Regresión temporal histórica de 250 ms; NO acredita MAE 50 ms ni ICC. Los conteos son descriptivos."
            corpus.external -> "DIAGNÓSTICO EXTERNO: no constituye aceptación del banco compatible ni mezcla sus porcentajes."
            else -> "DIAGNÓSTICO PARCIAL: no constituye aceptación ni evalúa la reserva ciega."
        }
        appendLine("Extracción: ${corpus.fixtures.count { isExtracted(corpus, it) }}/${corpus.fixtures.size}. Evaluados en esta corrida: ${results.size}. $status")
        appendLine("Exacto FULL compara el punto de la etiqueta; correcto compara acceptableRange; ±1 usa el extremo más cercano. Parciales y fallidas se informan por separado. Sin MetricKey.REPS = sin salida, nunca acierto.")
        appendLine()
        appendLine("| Perfil | Nivel | n | Salidas/cobertura | FULL exacto | FULL ±1 | Rango | Rango ±1 | Error máx. | Parciales exactas | Fallidas exactas | ALTA correctas/ALTA | Conclusión |")
        appendLine("|---|---|---:|---|---|---|---|---|---|---|---|---|---|")
        val accepted = results.filter { it.exclusions.isEmpty() }
        for ((id, rows) in accepted.groupBy { it.profile.id }.toSortedMap(compareBy { it.name })) {
            val c = counts(rows)
            appendLine("| $id | ${rows.first().profile.tier} | ${c.n} | ${c.output}/${c.n} (${ratio(c.output, c.n)}) | ${fraction(c.exactFull, c.output)} | ${fraction(c.withinOneFull, c.output)} | ${fraction(c.inRange, c.output)} | ${fraction(c.withinOne, c.output)} | ${c.maxRangeError ?: "—"} | ${fraction(c.partialExact, c.output)} | ${fraction(c.failedExact, c.output)} | ${fraction(c.highCorrect, c.high)} | ${if (c.n < 3 || c.output < 3) "n < 3: sin conclusión" else if (rows.first().profile.tier == ProfileTier.C) "C: descriptivo" else if (!finalGate) "diagnóstico" else "ver gate"} |")
        }
        appendLine()
        appendLine("Perfiles sin n suficiente no entran en el porcentaje de su nivel.")
        for (tier in ProfileTier.entries) {
            val tierRows = accepted.groupBy { it.profile.id }.values.filter { it.size >= 3 && it.first().profile.tier == tier }.flatten()
            val c = counts(tierRows)
            appendLine("- Nivel $tier, perfiles con n >= 3: ${c.n} etiquetas; ${c.output} salidas; rango ${fraction(c.inRange, c.output)}; cobertura ${ratio(c.output, c.n)}; ALTA ${fraction(c.highCorrect, c.high)} (cobertura ${ratio(c.high, c.n)}).")
        }
        if (corpus.external) {
            appendLine()
            appendLine("Kaggle contiene fragmentos de repeticiones aisladas y casi duplicados; sus IDs no prueban independencia. UCF101 conserva los grupos de grabación; un grupo tampoco prueba identidad de persona ni independencia entre fuentes.")
            appendLine("| Fuente externa | Clips evaluados | Grupos conocidos |")
            appendLine("|---|---:|---:|")
            for ((source, rows) in results.groupBy { it.fixture.source }.toSortedMap()) {
                val groups = rows.mapNotNull { it.fixture.sourceGroup?.takeUnless { group -> group == "unknown" } }.toSet().size
                appendLine("| $source | ${rows.size} | $groups |")
            }
        }
        appendLine()
        appendLine("| Subconjunto separado | n | Salidas | FULL exacto | Rango | ±1 | ALTA correctas/ALTA |")
        appendLine("|---|---:|---:|---|---|---|---|")
        for (reason in results.flatMap { it.exclusions }.toSortedSet()) {
            val c = counts(results.filter { reason in it.exclusions })
            appendLine("| $reason | ${c.n} | ${c.output} | ${fraction(c.exactFull, c.output)} | ${fraction(c.inRange, c.output)} | ${fraction(c.withinOne, c.output)} | ${fraction(c.highCorrect, c.high)} |")
        }
        val warnings = counts(results.filter { it.label.warnings.isNotEmpty() })
        appendLine("| softWarnings (no excluyen por sí solos) | ${warnings.n} | ${warnings.output} | ${fraction(warnings.exactFull, warnings.output)} | ${fraction(warnings.inRange, warnings.output)} | ${fraction(warnings.withinOne, warnings.output)} | ${fraction(warnings.highCorrect, warnings.high)} |")
        val cropped = counts(results.filter { row -> row.label.warnings.any { warning -> "cortad" in warning.lowercase(Locale.ROOT) || "fuera de plano" in warning.lowercase(Locale.ROOT) } })
        appendLine("| corte mencionado en softWarnings (no excluye por sí solo) | ${cropped.n} | ${cropped.output} | ${fraction(cropped.exactFull, cropped.output)} | ${fraction(cropped.inRange, cropped.output)} | ${fraction(cropped.withinOne, cropped.output)} | ${fraction(cropped.highCorrect, cropped.high)} |")
        appendLine()
        appendLine("| Vista etiqueta → motor | n | Salidas |")
        appendLine("|---|---:|---:|")
        for ((views, rows) in results.groupBy { "${it.label.view ?: "sin etiqueta"} → ${it.report.view}" }.toSortedMap()) appendLine("| $views | ${rows.size} | ${rows.count { it.hasOutput }} |")
        appendLine()
        appendLine("| Clip | Reparto | Perfil | Etiqueta F/P/f | Motor F/P/f | Disponible REPS | Confianza | Vista motor | Exclusión |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        for (r in results.sortedBy { it.fixture.id }) appendLine("| ${r.fixture.id} | ${r.fixture.subset} | ${r.profile.id} | ${r.label.full}/${r.label.partial}/${r.label.failed} | ${r.report.repCount}/${r.report.partialReps}/${r.report.failedReps} | ${r.hasOutput} | ${r.report.confidence} | ${r.report.view} | ${r.exclusions.joinToString()} |")
        appendLine()
        appendLine("| Giro fiable lote 1 | Etiquetas | Detectados | Pares | MAE ms | Máx ms | Sin pareja etiqueta/motor |")
        appendLine("|---|---:|---:|---:|---|---|---|")
        for (t in results.mapNotNull(::timing)) appendLine("| ${t.id} | ${t.expected} | ${t.detected} | ${t.errorsUs.size} | ${t.errorsUs.takeIf { it.isNotEmpty() }?.average()?.div(1000)?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"} | ${t.errorsUs.maxOrNull()?.div(1000.0) ?: "—"} | ${t.unmatchedExpected}/${t.unmatchedDetected} |")
        appendLine()
        appendLine("Sin etiquetas de fases a precisión de fotograma: MAE 50 ms e ICC CON/EXC no validados. Profundidad κ y error del círculo/disco no validados: faltan etiquetas. m/s, fps reales, calor y latencia en teléfono pendientes. El diámetro del banco es supuesto, por lo que PLATE_SCALE_MISMATCH no permite medir falsos positivos con diámetro verificado.")
        appendLine("La pérdida de velocidad publicada no acredita fatiga real; durationError y la ausencia de fatiga visible necesitan una medida/etiqueta adicional. No se ajustaron el dominio ni los umbrales.")
        if (finalGate) {
            val errors = countGateErrors(results)
            appendLine()
            appendLine(if (errors.isEmpty()) "Regresión histórica de rango: PASS; aceptación original NOT_VALIDATED. Ver OriginalContractAcceptanceTest." else "Regresión histórica de rango: FAIL; sin veredicto de aceptación original.")
            errors.forEach { appendLine("- $it") }
        }
    }

    fun publish(name: String, text: String) {
        println(text)
        val out = System.getProperty("kpkn.golden.reportDir") ?: System.getenv("KPKN_GOLDEN_REPORT_DIR")
        if (!out.isNullOrBlank()) File(out).apply { mkdirs() }.resolve(name).writeText(text, Charsets.UTF_8)
    }

    private fun readObject(file: File): JsonObject = json.parseToJsonElement(file.readText(Charsets.UTF_8).removePrefix("\uFEFF")).jsonObject
    private fun poseFile(corpus: GoldenCorpus, fixture: GoldenFixture): File = fileAt(corpus.root, fixture.posePath, "${fixture.id}.pose2.json.gz", corpus.external)
    private fun barFile(corpus: GoldenCorpus, fixture: GoldenFixture): File = fileAt(corpus.root, fixture.barPath, "${fixture.id}.bar.json.gz", corpus.external)
    private fun fileAt(root: File, explicit: String?, fallback: String, external: Boolean): File {
        val path = File(explicit ?: fallback)
        val resolved = (if (path.isAbsolute) path else File(root, path.path)).canonicalFile
        require(external || resolved.toPath().startsWith(root.canonicalFile.toPath())) { "Fixture compatible fuera de golden/: $resolved" }
        return resolved
    }
    private fun JsonObject.stringValue(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.optionalString(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.intValue(key: String): Int = getValue(key).jsonPrimitive.int
    private fun JsonObject.boolValue(key: String, default: Boolean? = null): Boolean = get(key)?.jsonPrimitive?.boolean ?: requireNotNull(default) { "Falta $key" }
    private fun secondsUs(value: Double): Long = (value * 1_000_000).roundToLong()
    private fun normalizedSide(value: String?): String? = when (value?.lowercase(Locale.ROOT)) { "l", "left" -> "left"; "r", "right" -> "right"; null -> null; else -> error("Lado desconocido: $value") }
    private fun checkHash(file: File, expected: String) {
        require(file.isFile) { "Falta ${file.absolutePath}" }
        require(sha256(file.readBytes()) == expected) { "Hash cambiado: ${file.absolutePath}" }
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun fraction(n: Int, d: Int): String = "$n/$d (${ratio(n, d)})"
    private fun ratio(n: Int, d: Int): String = if (d == 0) "sin salida" else formatPercent(n.toDouble() / d)
    private fun formatPercent(p: Double): String = String.format(Locale.ROOT, "%.1f %%", p * 100)
}

internal data class GoldenCorpus(val root: File, val fixtures: List<GoldenFixture>, val manifestSha256: String, val external: Boolean)
internal data class GoldenFixture(val id: String, val source: String, val subset: String, val profileId: AnalysisProfileId, val equipmentId: String?, val labelSha256: String, val poseSha256: String?, val barSha256: String?, val preexistingSmoke: Boolean, val sourceGroup: String?, val labelPath: String?, val posePath: String?, val barPath: String?)
internal data class GoldenTurn(val timeUs: Long, val side: String?, val pauseWindowUs: Pair<Long, Long>?)
internal data class GoldenEdge(val timeUs: Long, val kind: String)
internal data class GoldenLabel(val id: String, val full: Int, val partial: Int, val failed: Int, val uncertain: Boolean, val rangeMin: Int, val rangeMax: Int, val usable: Boolean, val view: String?, val exercise: String, val turnaroundKind: String, val activeWindowUs: Pair<Long, Long>?, val turns: List<GoldenTurn>, val edges: List<GoldenEdge>, val warnings: List<String>)
internal data class GoldenResult(val fixture: GoldenFixture, val label: GoldenLabel, val profile: AnalysisProfile, val report: SetTechniqueReport, val exclusions: Set<String>) {
    val hasOutput: Boolean get() = MetricKey.REPS in report.available
    val rangeError: Int get() = when { report.repCount < label.rangeMin -> label.rangeMin - report.repCount; report.repCount > label.rangeMax -> report.repCount - label.rangeMax; else -> 0 }
}
internal data class GoldenCounts(val n: Int, val output: Int, val exactFull: Int, val withinOneFull: Int, val inRange: Int, val withinOne: Int, val maxRangeError: Int?, val partialExact: Int, val failedExact: Int, val high: Int, val highCorrect: Int)
internal data class GoldenTiming(val id: String, val expected: Int, val detected: Int, val errorsUs: List<Long>, val unmatchedExpected: Int, val unmatchedDetected: Int)
