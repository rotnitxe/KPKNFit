package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.util.Locale

/**
 * Render canónico del máster dorado: función pura (mismas entradas ⇒ mismo texto).
 *
 * Formato. UTF-8, `\n`, una línea por (caso, instante). No aparece ninguna fecha absoluta: las horas son
 * relativas al origen del caso, así que el cuerpo del texto no depende del instante base (solo la cabecera).
 * - Cabecera: `# AUGE golden master v1`, `# zona=UTC base=<instante>`, `# casos=<n>`.
 * - Por caso: `## <id> · <título>`, seguido de:
 *   - Sesión descrita (casos con sesión): `<id> | session#<k> | drain=cns:<n>,spinal:<n>,muscular:<n> | pm=<Músculo>:<n>;… |
 *     v2global=<n> | v2=<Músculo>:stress=<x>,imm=<x>,cap=<x>,dir=<x>,ind=<x>;…`. El drenaje sale de
 *     `calculateCompletedSessionDrainBreakdown` (`pm` = `perMuscleMuscular`); el V2 es el congelado de la sesión.
 *   - Un renglón por instante: `<id> | t=<h>h | muscles=<Músculo>:<score>/<horasARecuperar>;… |
 *     rings=<muscular>,<cnc>,<spinal> | energy=bat:<a>,gym:<b>,life:<c> | spinal=<n> | acwr=<x|null> |
 *     dash=overall:<n>;conf:<etiqueta>;<canal>:<score>/<banda>/<confianza>;…`.
 *     `rings` son las `GlobalBatteries`; `energy` es `calculateSystemicFatigue` (batería SNC, carga de gimnasio y
 *     carga de vida, que el motor deja fija a 0); `acwr` se calcula con el instante del caso, nunca con el reloj real.
 *   - Casos E (vista previa): `<id> | preview | neural=<n> spinal=<n> muscular=<n> | drains=cns:<n>,spinal:<n>,muscular:<n> |
 *     raw=<n> | tomorrow=cns:<n|null>,spinal:<n|null> | pre=muscular:<n|null>,neural:<n|null>,spinal:<n|null> |
 *     muscles=<Músculo>:<score>/<horas>;…`.
 * Enteros tal cual; Double con `%.3f` (Locale.ROOT). Zona UTC y locale ROOT se fijan en la prueba.
 *
 * Congelación consciente: `applyMuscularAcwr` llama a `muscularAcwrFor(history)` sin instante, o sea con el reloj real.
 * Con la base de 2025-03-03 todas las ventanas de 28 días de ese reloj quedan vacías, así que el anillo muscular
 * global y el dashboard congelan «ACWR no disponible» (por el reloj real). Eso es un cambio que se re-ancla de forma
 * declarada cuando 1.4/1.7 inyecten el reloj. El `acwr=` de cada renglón sí usa el instante del caso.
 */
internal object AugeGoldenRender {

    /** Texto canónico y valores que pasan por el render, para las comprobaciones de cordura de la prueba. */
    internal class Resultado(
        val texto: String,
        val casos: Int,
        val baterias: List<Int>,
        val reales: List<Double>,
    )

    fun render(baseMs: Long, casos: List<GoldenCase>): Resultado {
        val recogida = Recogida()
        val lineas = mutableListOf(
            "# AUGE golden master v1",
            "# zona=UTC base=${Instant.ofEpochMilli(baseMs)}",
            "# casos=${casos.size}",
        )
        for (caso in casos) {
            lineas += "## ${caso.id} · ${caso.title}"
            caso.preview?.let { lineas += lineaVista(caso, it, recogida) }
            caso.described?.let { lineas += lineaSesion(caso, it, recogida) }
            for (instante in caso.instants) lineas += lineaInstante(caso, instante, recogida)
        }
        return Resultado(
            texto = lineas.joinToString(separator = "\n", postfix = "\n"),
            casos = casos.size,
            baterias = recogida.baterias.toList(),
            reales = recogida.reales.toList(),
        )
    }

    private fun lineaSesion(caso: GoldenCase, sesion: WorkoutLog, r: Recogida): String {
        val indice = caso.history.indexOfFirst { it.id == sesion.id }
        val numero = if (indice < 0) 1 else indice + 1
        val drenaje = AugeFatigueEngine.calculateCompletedSessionDrainBreakdown(
            completedExercises = sesion.completedExercises,
            exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
            settings = caso.settings,
            adaptiveCache = AugeAdaptiveCache(),
        )
        val impacto = requireNotNull(sesion.muscularImpactV2) { "${caso.id}: sesión sin muscularImpactV2" }
        val global = drenaje.global
        val pm = drenaje.perMuscleMuscular.toSortedMap().entries.joinToString(";") { "${it.key}:${it.value}" }
        val v2 = impacto.perMuscle.toSortedMap().entries.joinToString(";") { (musculo, v) ->
            "$musculo:stress=${r.dbl(v.stressUnits)},imm=${r.dbl(v.immediateDrainPct)}," +
                "cap=${r.dbl(v.capacityAtCompletion)},dir=${r.dbl(v.directStressUnits)}," +
                "ind=${r.dbl(v.indirectStressUnits)}"
        }
        return "${caso.id} | session#$numero | drain=cns:${r.pct(global.cns)},spinal:${r.pct(global.spinal)}," +
            "muscular:${r.pct(global.muscular)} | pm=$pm | v2global=${r.pct(impacto.globalMuscularDrain)} | v2=$v2"
    }

    private fun lineaInstante(caso: GoldenCase, instante: Long, r: Recogida): String {
        val historia = caso.history
        val exerciseDb = AugeGoldenCorpus.EXERCISE_DB
        val porMusculo = AugeRecoveryEngine.getPerMuscleBatteries(
            history = historia,
            wellbeing = caso.wellbeing,
            settings = caso.settings,
            exerciseDb = exerciseDb,
            nowOverrideMs = instante,
            zone = AugeGoldenCorpus.ZONE,
        )
        val globales = AugeRecoveryEngine.calculateGlobalBatteries(
            history = historia,
            wellbeing = caso.wellbeing,
            settings = caso.settings,
            exerciseDb = exerciseDb,
            precomputedMuscles = porMusculo,
            nowOverrideMs = instante,
        )
        val (energia, gimnasio, vida) = AugeRecoveryEngine.calculateSystemicFatigue(
            history = historia,
            wellbeing = caso.wellbeing,
            settings = caso.settings,
            exerciseDb = exerciseDb,
            nowOverride = instante,
        )
        val espinal = AugeRecoveryEngine.calculateSpinalBattery(
            history = historia,
            wellbeing = caso.wellbeing,
            settings = caso.settings,
            exerciseDb = exerciseDb,
            nowOverride = instante,
        )
        val acwr = AugeRecoveryEngine.muscularAcwrFor(historia, instante)
        val panel = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries = globales,
            perMuscle = porMusculo,
            articularBatteries = emptyMap(),
            wellbeing = caso.wellbeing,
            recentSessionCount = AugeRecoveryEngine.recentSessionCount(historia, instante),
        )
        val t = (instante - caso.origin) / AugeGoldenCorpus.HORA_MS
        val musculos = porMusculo.toSortedMap().entries.joinToString(";") { (nombre, e) ->
            "$nombre:${r.pct(e.recoveryScore)}/${e.hoursToRecovery}"
        }
        val canales = panel.channels.sortedBy { it.id.name }.joinToString(";") { c ->
            "${c.id.name}:${r.pct(c.score)}/${c.band.name}/${r.pct(c.confidence)}"
        }
        return "${caso.id} | t=${t}h | muscles=$musculos" +
            " | rings=${r.pct(globales.muscular)},${r.pct(globales.cnc)},${r.pct(globales.spinal)}" +
            " | energy=bat:${r.pct(energia)},gym:${r.pct(gimnasio)},life:$vida" +
            " | spinal=${r.pct(espinal)}" +
            " | acwr=${acwr?.let { r.dbl(it) } ?: "null"}" +
            " | dash=overall:${r.pct(panel.overallScore)};conf:${panel.confidenceLabel};$canales"
    }

    private fun lineaVista(caso: GoldenCase, vista: GoldenPreview, r: Recogida): String {
        val res = AugeRecoveryEngine.previewPostSessionBatteries(
            baseHistory = vista.baseHistory,
            previewLog = vista.previewLog,
            wellbeing = caso.wellbeing,
            settings = caso.settings,
            exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
            nowOverrideMs = vista.finishMs,
            finishOperationId = vista.operationId,
            completionInstantIso = vista.previewLog.date,
            inputHash = vista.inputHash,
            automaticImpact = vista.previewLog.muscularImpactV2,
        )
        fun opcional(v: Int?): String = v?.let { r.pct(it) } ?: "null"
        val musculos = res.perMuscle.toSortedMap().entries.joinToString(";") { (nombre, e) ->
            "$nombre:${r.pct(e.recoveryScore)}/${e.hoursToRecovery}"
        }
        return "${caso.id} | preview | neural=${r.pct(res.neural)} spinal=${r.pct(res.spinal)}" +
            " muscular=${r.pct(res.muscular)}" +
            " | drains=cns:${r.pct(res.globalCnsDrain)},spinal:${r.pct(res.globalSpinalDrain)}," +
            "muscular:${r.pct(res.globalMuscularDrain)}" +
            " | raw=${r.pct(res.spinalRaw)}" +
            " | tomorrow=cns:${opcional(res.tomorrowNeural)},spinal:${opcional(res.tomorrowSpinal)}" +
            " | pre=muscular:${opcional(res.preSessionMuscular)},neural:${opcional(res.preSessionNeural)}," +
            "spinal:${opcional(res.preSessionSpinal)}" +
            " | muscles=$musculos"
    }

    /** Acumula los valores que pasan por el render para las comprobaciones de cordura. */
    private class Recogida {
        val baterias = mutableListOf<Int>()
        val reales = mutableListOf<Double>()

        fun pct(v: Int): String {
            baterias += v
            return v.toString()
        }

        fun dbl(v: Double): String {
            reales += v
            return String.format(Locale.ROOT, "%.3f", v)
        }
    }
}
