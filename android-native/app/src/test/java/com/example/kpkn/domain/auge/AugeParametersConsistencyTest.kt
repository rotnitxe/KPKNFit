package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.math.exp

/**
 * WP 2.1 (F-61): cada parámetro numérico del modelo de recuperación (τ por perfil y por pilar, C por canal, τ por defecto de
 * Energía y Columna, K de 95 %, límites de τ) tiene UNA sola definición, `AugeParameters`, y el resto del código la lee.
 *
 * Las tablas de aquí están escritas a mano a propósito: son la especificación independiente del código. Sin reloj real
 * (`nowOverride` en cada llamada) y sin aleatoriedad.
 */
class AugeParametersConsistencyTest {

    // ─── (a) τ por músculo: lo que usa el motor ───────────────────────────────

    @Test
    fun elMotorUsaElTauDelPerfilEnLosCuarentaMusculosConPerfil() {
        // A 24 h el warp muscular deja 12 h efectivas: con ESTRES = 750 los cuatro τ posibles dan 93 · 86 · 82 · 80,
        // así que la batería mostrada identifica el τ del motor sin ambigüedad.
        val candidatos = TAU_DEL_PERFIL_ANTES.values.toList()
        val fallos = mutableListOf<String>()
        for ((musculo, perfil) in PERFIL_DEL_MOTOR_ANTES) {
            val esperado = TAU_DEL_PERFIL_ANTES.getValue(perfil)
            val mostrado = puntuacionMusculo(musculo, horas = 24)
            val compatibles = candidatos.filter { tau ->
                AugeUtils.batteryPercent(bateriaRealMusculo(ESTRES, 24.0, tau)) == mostrado
            }
            if (compatibles != listOf(esperado)) {
                fallos += "$musculo ($perfil): el motor da $mostrado a 24 h; τ compatibles $compatibles; esperado $esperado"
            }
        }
        assertTrue("τ del motor distinto del perfil (${fallos.size}): $fallos", fallos.isEmpty())
    }

    @Test
    fun elMotorUsaElTauDelPlanEnLosTrecePilares() {
        // 24 h: Bíceps, Tríceps, Deltoides, Pantorrillas, Abdomen · 48 h: Pectorales, Dorsales, Trapecio, Core ·
        // 72 h: Cuádriceps, Glúteos · 96 h: Isquiosurales, Erectores Espinales.
        val candidatos = listOf(24.0, 48.0, 72.0, 96.0)
        val fallos = mutableListOf<String>()
        for ((pilar, esperado) in TAU_ESPERADO_POR_PILAR) {
            val mostrado = puntuacionMusculo(pilar, horas = 24)
            val compatibles = candidatos.filter { tau ->
                AugeUtils.batteryPercent(bateriaRealMusculo(ESTRES, 24.0, tau)) == mostrado
            }
            if (compatibles != listOf(esperado)) {
                fallos += "$pilar: el motor da $mostrado a 24 h; τ compatibles $compatibles; esperado $esperado"
            }
        }
        assertTrue("τ del motor distinto de la tabla del plan (${fallos.size}): $fallos", fallos.isEmpty())
    }

    // ─── (c) guarda estructural: ningún parámetro del modelo fuera del registro ─

    @Test
    fun ningunParametroDelModeloViveFueraDelRegistro() {
        val hallazgos = archivosEscaneados(raizDeFuentes()).flatMap { hallazgosEn(it.name, it.readText(Charsets.UTF_8)) }
        val sinPermiso = hallazgos.filterNot { h -> PERMITIDOS.any { it.cubre(h) } }
        assertTrue(
            "Quedan ${sinPermiso.size} copias de parámetros del modelo fuera de AugeParameters.kt " +
                "(archivo:línea [regla] texto):\n" + sinPermiso.joinToString("\n") { " - $it" },
            sinPermiso.isEmpty(),
        )
        // Una excepción que ya no tiene hallazgo es una excepción vieja: se borra para que la lista no mienta.
        val sinUso = PERMITIDOS.filter { p -> hallazgos.none { p.cubre(it) } }
        assertTrue(
            "Excepciones sin uso, bórralas: " + sinUso.joinToString { "${it.archivo} [${it.regla}] «${it.contiene}»" },
            sinUso.isEmpty(),
        )
    }

    @Test
    fun lasReglasDelEscaneoDetectanLasFormasQueBuscan() {
        fun reglas(texto: String): Set<String> = hallazgosEn("Muestra.kt", texto).map { it.regla }.toSet()

        assertTrue("mapa-de-tau" in reglas("    private val MUSCLE_PROFILE_MAP = mapOf("))
        assertTrue("mapa-de-tau" in reglas("    private val RECOVERY_PROFILES = mapOf("))
        assertTrue("mapa-de-tau" in reglas("    val PILLAR_TAU_HOURS: Map<String, Double> = mapOf("))
        assertTrue("mapa-de-tau" in reglas("    private val MUSCLE_HALF_LIFE: Map<String, Double> = mapOf("))
        assertTrue("tau-por-clave" in reglas("        \"fast\"   to 24.0,"))
        assertTrue("tau-por-clave" in reglas("            \"biceps\" -> 24.0"))
        assertTrue("tau-por-clave" in reglas("        \"Pectorales\" to 48.0, \"Dorsales\" to 48.0,"))
        assertTrue("constante-k95" in reglas("        val impliedTau = 2.9957 / k"))
        assertTrue("constante-k95" in reglas("        val k = ln(20.0) / tau"))
        assertTrue("c-en-el-exponente" in reglas("        val p = clamp(100.0 * (1.0 - safeExp(-rawFatiguePct / 90.0)), 0.0, 100.0)"))
        assertTrue("c-en-el-exponente" in reglas("        val drop = 100.0 * (1.0 - exp(-rawPct / 70.0))"))
        assertTrue("c-en-el-exponente" in reglas("        val n = 100.0 * (1.0 - safeExp(-rawGymPct / 75.0))"))
        assertTrue("c-en-la-inversa" in reglas("            val impliedRawGymPct = -75.0 * ln(1.0 - penalty / 100.0)"))
        assertTrue("c-en-la-inversa" in reglas("            val target = -90.0 * ln(0.9) * capacity / 100.0"))
        assertTrue("tau-por-defecto" in reglas("        val baseTau = adaptiveCache.cnsRecoveryHours ?: 36.0"))
        assertTrue("tau-por-defecto" in reglas("        val tauHours = adaptiveCache.spinalRecoveryHours ?: 52.0"))
        assertTrue("tau-por-defecto" in reglas("                ?: 48.0"))
        assertTrue("limites-de-tau" in reglas("        val t = clamp(adaptiveHours ?: perfil ?: 48.0, 18.0, 144.0)"))
        assertTrue("limites-de-tau" in reglas("        return current + (muscleKey to clamp(newTau, 12.0, 144.0))"))
        assertTrue(
            "lista-de-pilares" in reglas(
                "    private val PILLAR_MUSCLES = listOf(\n        \"Pectorales\", \"Dorsales\", \"Deltoides\", \"Bíceps\", \"Tríceps\",\n    )",
            ),
        )
        assertTrue("lista-de-pilares" in reglas("    internal val SPINE_GUARD_MUSCLES = listOf(\"Erectores Espinales\", \"Dorsales\", \"Trapecio\")"))

        // Lo que NO debe saltar: el registro, los comentarios y las cadenas con «//».
        assertTrue(reglas("        val c = AugeParameters.C_MUSCLE * AugeParameters.TAU_K_95").isEmpty())
        assertTrue(reglas("        val t = clamp(x, AugeParameters.TAU_MIN_H, AugeParameters.TAU_MAX_H)").isEmpty())
        assertTrue(reglas("    val PILLAR_TAU_HOURS: Map<String, Double> = AugeParameters.PILLAR_TAU_HOURS").isEmpty())
        assertTrue(reglas("        // tau = 2.9957 / k, exp(-rawPct / 70.0), ?: 36.0").isEmpty())
        assertTrue(reglas("    /** C de la columna: `exp(-rawPct / 70.0)`, `-90.0 * ln(0.9)` y `?: 52.0`. */").isEmpty())
        assertTrue(reglas("    /*\n     * RECOVERY_PROFILES = mapOf(\n     * 2.9957 y ?: 48.0\n     */\n    val x = 1").isEmpty())
        assertTrue(reglas("        val u = \"https://ejemplo.org/a\" // 2.9957 ?: 36.0").isEmpty())
    }

    @Test
    fun elEscaneoLeeLasFuentesDeAugeYNoElRegistro() {
        val nombres = archivosEscaneados(raizDeFuentes()).map { it.name }
        assertTrue("El escaneo leyó pocos archivos (${nombres.size})", nombres.size >= 25)
        for (esperado in listOf(
            "AugeRecoveryEngine.kt", "AugeAdaptiveEngine.kt", "AugeUtils.kt", "InitialRecoveryEvidencePolicy.kt",
            "InterferenceEngine.kt", "AugeViewModel.kt", "InitialRecoveryEvidence.kt",
        )) {
            assertTrue("El escaneo no incluye $esperado", esperado in nombres)
        }
        assertTrue("El registro no debe escanearse a sí mismo", "AugeParameters.kt" !in nombres)
    }

    // ─── Fixtures y fórmulas a mano ───────────────────────────────────────────

    /** Sesión con impacto V2 congelado en [ORIGEN]: el músculo recibe [estres] y la capacidad al cierre es [CAPACIDAD]. */
    private fun sesionV2(musculo: String, estres: Double): WorkoutLog {
        val iso = Instant.ofEpochMilli(ORIGEN).toString()
        return WorkoutLog(
            id = "v2-$musculo", programId = "p", sessionId = "s", sessionName = "Referencia",
            date = iso, durationMinutes = 60,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 30,
                perMuscle = mapOf(
                    musculo to MuscleSessionImpactV2(
                        stressUnits = estres, capacityAtCompletion = CAPACIDAD, immediateDrainPct = estres / 10.0,
                        directStressUnits = estres, indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf(musculo),
                setInputHash = "h", contextHash = "c",
            ),
        )
    }

    /** Batería mostrada del músculo [horas] h después de la sesión V2, sin ancla y sin τ aprendido (caché vacío). */
    private fun puntuacionMusculo(musculo: String, horas: Int): Int =
        AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = musculo,
            history = listOf(sesionV2(musculo, ESTRES)),
            wellbeing = null,
            settings = Settings(),
            precomputedCapacity = CAPACIDAD,
            nowOverride = ORIGEN + horas * HORA_MS,
        ).recoveryScore

    /** Batería REAL (Double) del músculo con la fórmula del motor: `100·exp(−R/C)`, R = % de fatiga restante (como `BatteryRoundingTest`). */
    private fun bateriaRealMusculo(estres: Double, horas: Double, tau: Double, c: Double = C_MUSCULO_ESPERADA): Double {
        val k = AugeUtils.TAU_K_95 / tau
        val restante = estres * exp(-k * AugeUtils.getSigmoidalHours(horas))
        // Con ≤ 0,1 unidades de carga residual el motor fija 100 exacto.
        if (restante <= 0.1) return 100.0
        val crudoPct = restante / CAPACIDAD * 100.0
        return 100.0 * exp(-crudoPct / c)
    }

    // ─── Escaneo de fuentes ───────────────────────────────────────────────────

    private class Regla(val id: String, val descripcion: String, val patron: Regex)

    private class Hallazgo(val archivo: String, val linea: Int, val regla: String, val texto: String) {
        override fun toString() = "$archivo:$linea [$regla] $texto"
    }

    /** Excepción documentada del escaneo: la línea de [archivo] que contiene [contiene] puede incumplir [regla]; [motivo] dice por qué. */
    private class Permitido(val archivo: String, val regla: String, val contiene: String, val motivo: String) {
        fun cubre(h: Hallazgo) = h.archivo == archivo && h.regla == regla && contiene in h.texto
    }

    private fun raizDeFuentes(): File {
        val relativo = "src/main/java/com/example/kpkn"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, relativo), File(dir, "app/$relativo")).firstOrNull { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("No encuentro $relativo subiendo desde ${File("").absolutePath}")
    }

    /** Los archivos .kt de `domain/auge` salvo el registro, más los dos de fuera que también tenían copias. */
    private fun archivosEscaneados(raiz: File): List<File> {
        val auge = File(raiz, "domain/auge").listFiles { f -> f.isFile && f.extension == "kt" }
            .orEmpty().filter { it.name != "AugeParameters.kt" }.sortedBy { it.name }
        val extras = ARCHIVOS_EXTRA.map { File(raiz, it) }
        extras.forEach { check(it.isFile) { "El escaneo espera encontrar $it: si se movió, actualiza ARCHIVOS_EXTRA" } }
        return auge + extras
    }

    private fun hallazgosEn(nombre: String, texto: String): List<Hallazgo> {
        val limpio = sinComentarios(texto)
        val lineas = texto.split('\n')
        return REGLAS.flatMap { regla ->
            regla.patron.findAll(limpio).map { m ->
                val linea = limpio.substring(0, m.range.first).count { it == '\n' } + 1
                Hallazgo(nombre, linea, regla.id, lineas.getOrNull(linea - 1)?.trim().orEmpty())
            }.toList()
        }.distinctBy { Triple(it.archivo, it.linea, it.regla) }
    }

    /**
     * Quita comentarios de línea y de bloque (KDoc incluido) respetando cadenas, cadenas crudas y literales de carácter.
     * Los comentarios se sustituyen por espacios y los saltos de línea se conservan: los números de línea no se mueven.
     */
    private fun sinComentarios(texto: String): String {
        val salida = StringBuilder(texto.length)
        val n = texto.length
        var i = 0
        while (i < n) {
            val c = texto[i]
            val d = if (i + 1 < n) texto[i + 1] else '\u0000'
            when {
                c == '/' && d == '/' -> {
                    while (i < n && texto[i] != '\n') { salida.append(' '); i++ }
                }
                c == '/' && d == '*' -> {
                    var profundidad = 0
                    while (i < n) {
                        val a = texto[i]
                        val b = if (i + 1 < n) texto[i + 1] else '\u0000'
                        if (a == '/' && b == '*') { profundidad++; salida.append("  "); i += 2 }
                        else if (a == '*' && b == '/') { profundidad--; salida.append("  "); i += 2; if (profundidad == 0) break }
                        else { salida.append(if (a == '\n') '\n' else ' '); i++ }
                    }
                }
                c == '"' && texto.startsWith("\"\"\"", i) -> {
                    val cierre = texto.indexOf("\"\"\"", i + 3)
                    val fin = if (cierre < 0) n else cierre + 3
                    salida.append(texto, i, fin)
                    i = fin
                }
                c == '"' -> {
                    salida.append(c); i++
                    while (i < n && texto[i] != '"' && texto[i] != '\n') {
                        if (texto[i] == '\\' && i + 1 < n) { salida.append(texto[i]); i++ }
                        salida.append(texto[i]); i++
                    }
                    if (i < n && texto[i] == '"') { salida.append('"'); i++ }
                }
                c == '\'' -> {
                    val cierre = texto.indexOf('\'', if (d == '\\') i + 3 else i + 2)
                    val fin = if (cierre in 0..(i + 8)) cierre + 1 else i + 1
                    salida.append(texto, i, fin)
                    i = fin
                }
                else -> { salida.append(c); i++ }
            }
        }
        return salida.toString()
    }

    private companion object {
        val ORIGEN: Long = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
        const val HORA_MS = 3_600_000L
        const val CAPACIDAD = 2_600.0
        const val ESTRES = 750.0

        /** C del músculo (90), escrita a mano: la especificación contra la que se compara el motor. */
        const val C_MUSCULO_ESPERADA = 90.0

        /** τ base de cada pilar según el plan, escrito a mano a propósito: no se calcula de ningún mapa del código. */
        val TAU_ESPERADO_POR_PILAR: Map<String, Double> = linkedMapOf(
            "Pectorales" to 48.0, "Dorsales" to 48.0, "Deltoides" to 24.0, "Bíceps" to 24.0, "Tríceps" to 24.0,
            "Cuádriceps" to 72.0, "Isquiosurales" to 96.0, "Glúteos" to 72.0, "Pantorrillas" to 24.0,
            "Abdomen" to 24.0, "Trapecio" to 48.0, "Erectores Espinales" to 96.0, "Core" to 48.0,
        )

        /** τ de cada perfil tal como estaba en `AugeRecoveryEngine.RECOVERY_PROFILES` antes de 2.1. */
        val TAU_DEL_PERFIL_ANTES: Map<String, Double> =
            linkedMapOf("fast" to 24.0, "medium" to 48.0, "slow" to 72.0, "heavy" to 96.0)

        /** Músculo → perfil, copia del `MUSCLE_PROFILE_MAP` del motor antes de 2.1 (mismo orden). */
        val PERFIL_DEL_MOTOR_ANTES: List<Pair<String, String>> = listOf(
            "Bíceps" to "fast", "Tríceps" to "fast", "Deltoides" to "fast",
            "Deltoides Anterior" to "fast", "Deltoides Lateral" to "fast", "Deltoides Posterior" to "fast",
            "Pantorrillas" to "fast", "Abdomen" to "fast", "Antebrazo" to "fast",
            "Gemelos" to "fast", "Sóleo" to "fast", "Oblicuos" to "fast", "Costales" to "fast",
            "Pectorales" to "medium", "Dorsales" to "medium", "Hombros" to "medium", "Trapecio" to "medium",
            "Romboide" to "medium", "Redondo Menor" to "medium", "Redondo Mayor" to "medium",
            "Infraespinoso" to "medium", "Supraespinoso" to "medium", "Subescapular" to "medium",
            "Serrato Anterior" to "medium",
            "Cuádriceps" to "slow", "Glúteos" to "slow",
            "Glúteo Mayor" to "slow", "Glúteo Medio" to "slow", "Glúteo Menor" to "slow",
            "Aductores" to "medium", "Tensor Fascia Lata" to "medium",
            "Isquiosurales" to "heavy", "Bíceps Femoral" to "heavy", "Semitendinoso" to "heavy",
            "Semimembranoso" to "heavy", "Erectores Espinales" to "heavy", "Core" to "medium",
            "Cuello" to "medium", "Psoas" to "medium", "Lumbar" to "heavy",
        )

        /** Archivos de fuera de `domain/auge` que también tenían copias de τ por defecto o de la lista de pilares. */
        val ARCHIVOS_EXTRA = listOf("screens/auge/AugeViewModel.kt", "data/models/InitialRecoveryEvidence.kt")

        val REGLAS = listOf(
            Regla(
                "mapa-de-tau", "mapa de τ por perfil o por músculo",
                Regex("""\b(?:RECOVERY_PROFILES|MUSCLE_PROFILE_MAP|PILLAR_TAU_HOURS|MUSCLE_HALF_LIFE)\b[^=\n]*=\s*mapOf\s*\("""),
            ),
            Regla(
                "tau-por-clave", "texto → 24 · 48 · 72 · 96 h (τ de perfil escrito a mano)",
                Regex(""""[^"\n]+"\s*(?:to|->)\s*(?:24|48|72|96)\.0\b"""),
            ),
            Regla(
                "constante-k95", "2,9957 o ln(20) escritos a mano",
                Regex("""\b2\.9957\b|\bln\(\s*20(?:\.0)?\s*\)"""),
            ),
            Regla(
                "c-en-el-exponente", "C del canal dentro de exp(−x / 90 · 75 · 70)",
                Regex("""\b(?:safeExp|exp)\(\s*-\s*[A-Za-z_]\w*\s*/\s*(?:90|75|70)(?:\.0)?\s*\)"""),
            ),
            Regla(
                "c-en-la-inversa", "C del canal en −90 · 75 · 70 · ln(…)",
                Regex("""-\s*(?:90|75|70)\.0\s*\*\s*(?:kotlin\.math\.)?ln\("""),
            ),
            Regla(
                "tau-por-defecto", "τ por defecto de un canal escrito tras «?:» (36 · 48 · 52 h)",
                Regex("""\?:\s*(?:36|48|52)\.0\b"""),
            ),
            Regla(
                "limites-de-tau", "límites de τ (12 o 18 … 144 h) escritos en un clamp",
                Regex("""\bclamp\([^()]*,\s*(?:12|18)\.0\s*,\s*144\.0\s*\)"""),
            ),
            Regla(
                "lista-de-pilares", "lista de los 13 pilares o de las guardas de Columna escrita a mano",
                Regex(
                    """listOf\(\s*"Pectorales"\s*,\s*"Dorsales"\s*,\s*"Deltoides"\s*,""" +
                        """|listOf\(\s*"Erectores Espinales"\s*,\s*"Dorsales"\s*,\s*"Trapecio"\s*\)""",
                ),
            ),
        )

        /**
         * Lo que se decide dejar fuera del registro. Cada entrada nombra archivo, regla y un trozo de la línea, y da el motivo.
         * Los tres clamps son del aprendiz actual (EMA de `AugeAdaptiveEngine`), que la fase 3 reemplaza: sus límites no son los del
         * motor (el inferior de músculo y Energía es 12 h, no 18 h), así que 2.1 no los unifica.
         */
        val PERMITIDOS = listOf(
            Permitido(
                "AugeAdaptiveEngine.kt", "limites-de-tau", "clamp(newTau, 12.0, 144.0)",
                "límites propios del aprendiz de músculos; lo reemplaza la fase 3",
            ),
            Permitido(
                "AugeAdaptiveEngine.kt", "limites-de-tau", "clamp(it, 12.0, 144.0)",
                "límites propios del aprendiz de Energía; lo reemplaza la fase 3",
            ),
            Permitido(
                "AugeAdaptiveEngine.kt", "limites-de-tau", "clamp(it, 18.0, 144.0)",
                "límites propios del aprendiz de Columna; coinciden con el motor pero son del aprendiz actual",
            ),
        )
    }
}
