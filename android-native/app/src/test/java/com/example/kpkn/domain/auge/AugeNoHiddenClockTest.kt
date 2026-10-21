package com.example.kpkn.domain.auge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guarda estructural de relojes escondidos (WP 1.7, hallazgo F-27).
 *
 * Escanea los `.kt` de `domain/auge/` y falla si aparece una lectura del reloj (`System.currentTimeMillis`,
 * `Instant.now(`, `LocalDate.now(` y parientes, o una llamada `nowMs()`) fuera de la LISTA BLANCA de abajo.
 *
 * Por qué hace falta: con fechas lejanas, como las del máster dorado, una prueba de invariancia no ve el reloj
 * real, porque las ventanas de 10, 28 y 35 días quedan vacías. Esta guarda es la que impide que un reloj escondido
 * vuelva a colarse.
 *
 * Cómo se lee la lista: cada entrada nombra el archivo, la función que contiene la línea (`null` = cualquiera),
 * un fragmento de la línea y cuántas veces puede aparecer. Una lectura nueva obliga a decidir: o recibe el instante
 * por parámetro, o entra aquí con su motivo. Se permiten «como mucho» las veces declaradas, nunca más.
 */
class AugeNoHiddenClockTest {

    private class Permitido(
        val archivo: String,
        val funcion: String?,
        val fragmento: String,
        val veces: Int,
        val motivo: String,
    )

    private class Lectura(val archivo: String, val linea: Int, val funcion: String?, val texto: String) {
        override fun toString() = "$archivo:$linea (${funcion ?: "fuera de funciones"}): $texto"
    }

    private val permitidos = listOf(
        Permitido(
            "AugeClock.kt", "nowMs", "System.currentTimeMillis()", 1,
            "origen único del reloj: lo demás recibe el instante o cae aquí por el valor por defecto de un parámetro",
        ),
        Permitido(
            "AugeFatigueEngine.kt", null, "computedAtMs = System.currentTimeMillis()", 2,
            "metadato de cuándo se calculó el EMA de estrés del mesociclo; no entra en ninguna cifra",
        ),
        Permitido(
            "AugeTtcEngine.kt", "calculateArticularBatteries", "nowMs: Long = AugeClock.nowMs()", 1,
            "valor por defecto del parámetro de instante",
        ),
        Permitido(
            "NutritionRecoveryEngine.kt", "computeNutritionRecoveryMultiplier", "nowMs: Long = AugeClock.nowMs()", 1,
            "valor por defecto del parámetro de instante",
        ),
        Permitido(
            "InterferenceEngine.kt", "calculateHistoricalInterferences", "nowMs: Long = AugeClock.nowMs()", 1,
            "valor por defecto del parámetro de instante",
        ),
        Permitido(
            "InterferenceEngine.kt", "analyzeUpcomingSession", "nowMs: Long = AugeClock.nowMs()", 1,
            "valor por defecto del parámetro de instante",
        ),
        Permitido(
            "AxialLoadMonitor.kt", "evaluate", "nowMs: Long = AugeClock.nowMs()", 1,
            "valor por defecto del parámetro de instante; los llamadores de producción pasan el suyo",
        ),
        Permitido(
            "SystemicLoadMonitor.kt", "evaluate", "nowMs: Long = AugeClock.nowMs()", 1,
            "valor por defecto del parámetro de instante; los llamadores de producción pasan el suyo",
        ),
        Permitido(
            "LoadAdvisoryEngine.kt", "evaluate", "nowMs: Long = AugeClock.nowMs()", 1,
            "valor por defecto del parámetro de instante; los llamadores de producción pasan el suyo",
        ),
    )

    private val lecturaDeReloj = Regex(
        """System\.currentTimeMillis|System\.nanoTime|Instant\.now\(|LocalDate\.now\(|LocalDateTime\.now\(|""" +
            """ZonedDateTime\.now\(|OffsetDateTime\.now\(|Calendar\.getInstance|\bnowMs\(\)""",
    )

    private val declaracionDeFuncion = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(""")

    /** La línea sin su comentario: una mención en KDoc o tras `//` no es una lectura del reloj. */
    private fun codigoDe(linea: String): String {
        val recortada = linea.trim()
        if (recortada.startsWith("*") || recortada.startsWith("/*") || recortada.startsWith("//")) return ""
        return linea.substringBefore("//")
    }

    /** Función más cercana por encima de la línea (la propia línea cuenta), o null si no hay ninguna. */
    private fun funcionDe(lineas: List<String>, indice: Int): String? {
        for (i in indice downTo 0) {
            val coincidencia = declaracionDeFuncion.find(codigoDe(lineas[i]))
            if (coincidencia != null) return coincidencia.groupValues[1]
        }
        return null
    }

    private fun lecturasDe(archivo: String, lineas: List<String>): List<Lectura> =
        lineas.indices.mapNotNull { i ->
            val codigo = codigoDe(lineas[i])
            if (lecturaDeReloj.containsMatchIn(codigo)) {
                Lectura(archivo, i + 1, funcionDe(lineas, i), codigo.trim())
            } else {
                null
            }
        }

    /** Las lecturas que la lista blanca no cubre (o que exceden las veces declaradas), una línea cada una. */
    private fun fugasDe(lecturas: List<Lectura>): List<String> {
        val usos = HashMap<Permitido, Int>()
        val fugas = mutableListOf<String>()
        for (lectura in lecturas) {
            val regla = permitidos.firstOrNull { p ->
                p.archivo == lectura.archivo &&
                    (p.funcion == null || p.funcion == lectura.funcion) &&
                    lectura.texto.contains(p.fragmento) &&
                    (usos[p] ?: 0) < p.veces
            }
            if (regla == null) {
                fugas += lectura.toString()
            } else {
                usos[regla] = (usos[regla] ?: 0) + 1
            }
        }
        return fugas
    }

    private fun carpetaDelDominio(): File {
        val relativa = "src/main/java/com/example/kpkn/domain/auge"
        val candidatas = listOf(File(relativa), File("app/$relativa"), File("android-native/app/$relativa"))
        return candidatas.firstOrNull { it.isDirectory }
            ?: throw AssertionError("No encuentro domain/auge desde ${File(".").absoluteFile}")
    }

    @Test
    fun snapshotAndRecoveryDoNotImportRepositoriesOrAndroid() {
        val domain = carpetaDelDominio()
        val forbiddenImport = Regex("""(?m)^\s*import\s+(android\.|com\.example\.kpkn\.data\.repository\.)""")
        listOf("AugeSnapshotComputer.kt", "AugeRecoveryEngine.kt").forEach { name ->
            val source = File(domain, name).readText(Charsets.UTF_8)
            assertTrue("$name vuelve a depender de repositorios o Android", !forbiddenImport.containsMatchIn(source))
        }
    }

    @Test
    fun noClockReadsOutsideTheWhitelist() {
        val archivos = carpetaDelDominio().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.name }
            .toList()
        assertTrue(
            "Solo ${archivos.size} archivos .kt: la ruta del escaneo está mal y la guarda pasaría en vacío",
            archivos.size >= 20,
        )
        val lecturas = archivos.flatMap { lecturasDe(it.name, it.readLines(Charsets.UTF_8)) }
        val fugas = fugasDe(lecturas)
        assertTrue(
            "Relojes escondidos en domain/auge (${fugas.size}). Pasa el instante por parámetro o, si es " +
                "legítimo, añádelo a la lista blanca con su motivo:\n" + fugas.joinToString("\n"),
            fugas.isEmpty(),
        )
    }

    @Test
    fun theScannerSeesClockReadsAndIgnoresComments() {
        val muestra = listOf(
            "fun calcula(): Long {",
            "    val ahora = System.currentTimeMillis()",
            "    val dia = LocalDate.now()",
            "    val t = nowMs()",
            "    // nowMs() mencionado en un comentario",
            "     * System.currentTimeMillis() mencionado en un KDoc",
            "    val sinParentesis = nowMs",
            "    val caeAlReloj = nowOverride ?: nowMs()   // cae al reloj",
        )
        val lecturas = lecturasDe("Muestra.kt", muestra)
        assertEquals(listOf(2, 3, 4, 8), lecturas.map { it.linea })
        assertTrue(lecturas.all { it.funcion == "calcula" })
    }

    @Test
    fun theWhitelistAcceptsTheDeclaredReadsAndOnlyThose() {
        val origen = Lectura("AugeClock.kt", 9, "nowMs", "fun nowMs(): Long = System.currentTimeMillis()")
        val metadato = Lectura("AugeFatigueEngine.kt", 982, "x", "computedAtMs = System.currentTimeMillis(),")
        assertTrue(fugasDe(listOf(origen, metadato, metadato)).isEmpty())
        // La tercera lectura del metadato excede las dos declaradas.
        assertEquals(1, fugasDe(listOf(origen, metadato, metadato, metadato)).size)
        // Un reloj en un motor que no está en la lista es una fuga, aunque sea el mismo texto que el origen.
        val escondido = Lectura("AugeTtcEngine.kt", 212, "calculateArticularBatteries", "val now = System.currentTimeMillis()")
        assertEquals(1, fugasDe(listOf(escondido)).size)
        // Un `nowMs()` en la función equivocada tampoco pasa.
        val enOtraFuncion = Lectura("AugeRecoveryEngine.kt", 393, "calculateUserWorkCapacity", "val now = nowMs()")
        assertEquals(1, fugasDe(listOf(enOtraFuncion)).size)
    }
}
