package com.example.kpkn.domain.auge

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Máster dorado del motor de anillos (WP 0.2): congela las cifras de AUGE sobre [AugeGoldenCorpus].
 *
 * Regenerar: con `AUGE_GOLDEN_UPDATE=1` la prueba escribe `build/auge-golden/expected.txt` y pasa (nunca escribe en
 * `src`); el humano lo revisa y lo copia a `src/test/resources/auge/golden/expected.txt`. Si la tabla difiere, se
 * vuelca lo obtenido a `build/auge-golden/actual.txt` y la prueba lista las diferencias.
 */
class AugeGoldenMasterTest {
    private var zonaOriginal: TimeZone? = null
    private var localeOriginal: Locale? = null

    @Before
    fun fijarZonaYLocale() {
        zonaOriginal = TimeZone.getDefault()
        localeOriginal = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.ROOT)
    }

    @After
    fun restaurarZonaYLocale() {
        zonaOriginal?.let { TimeZone.setDefault(it) }
        localeOriginal?.let { Locale.setDefault(it) }
    }

    @Test
    fun goldenMasterMatches() {
        val obtenido = render(AugeGoldenCorpus.BASE_MS).texto
        if (System.getenv("AUGE_GOLDEN_UPDATE") == "1") {
            escribir("build/auge-golden/expected.txt", obtenido)
            return
        }
        val esperado = javaClass.getResourceAsStream(RECURSO)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw AssertionError("Falta el recurso $RECURSO")
        val diferencias = diferencias(esperado.replace("\r\n", "\n"), obtenido)
        if (diferencias.isNotEmpty()) {
            escribir("build/auge-golden/actual.txt", obtenido)
            throw AssertionError(
                "Máster dorado: ${diferencias.size} diferencias (caso | instante | campo: esperado → obtenido). " +
                    "Primeras ${minOf(40, diferencias.size)}:\n" + diferencias.take(40).joinToString("\n"),
            )
        }
    }

    @Test
    fun renderIsDeterministic() {
        val primera = render(AugeGoldenCorpus.BASE_MS).texto
        val segunda = render(AugeGoldenCorpus.BASE_MS).texto
        assertTrue("Renderizar dos veces dio textos distintos", primera == segunda)
    }

    @Test
    fun renderIsInvariantToAbsoluteTime() {
        val antes = cuerpo(render(AugeGoldenCorpus.BASE_MS).texto)
        val despues = cuerpo(render(AugeGoldenCorpus.BASE_MS + AugeGoldenCorpus.DESPLAZAMIENTO_MS).texto)
        assertTrue("El cuerpo cambia al desplazar el corpus 52 semanas (${antes.size} frente a ${despues.size} líneas)",
            antes.size == despues.size)
        val primera = antes.indices.firstOrNull { antes[it] != despues[it] }
        assertTrue(
            "El texto depende del instante absoluto (línea ${primera?.plus(1)}): " +
                "«${primera?.let { antes[it] }}» frente a «${primera?.let { despues[it] }}»",
            primera == null,
        )
    }

    @Test
    fun tableIsSane() {
        val r = render(AugeGoldenCorpus.BASE_MS)
        assertTrue("Menos de 30 casos: ${r.casos}", r.casos >= 30)
        assertTrue("Hay NaN o infinitos en el texto", !r.texto.contains("NaN") && !r.texto.contains("Infinity"))
        assertTrue(
            "Valores de batería fuera de 0..100: ${r.baterias.filterNot { it in 0..100 }.take(5)}",
            r.baterias.all { it in 0..100 },
        )
        assertTrue("Hay valores reales no finitos", r.reales.all { it.isFinite() })
    }

    private fun render(baseMs: Long) = AugeGoldenRender.render(baseMs, AugeGoldenCorpus.build(baseMs))

    /** Cuerpo del texto: todo menos la cabecera de tres líneas (`# …`). */
    private fun cuerpo(texto: String): List<String> = texto.split("\n").filterNot { it.startsWith("# ") }

    /** Diferencias línea a línea; en renglones con campos (` | `) se nombra el caso, el instante y el campo. */
    private fun diferencias(esperado: String, obtenido: String): List<String> {
        val e = esperado.split("\n")
        val o = obtenido.split("\n")
        val salida = mutableListOf<String>()
        for (i in 0 until maxOf(e.size, o.size)) {
            val le = e.getOrNull(i)
            val lo = o.getOrNull(i)
            if (le == lo) continue
            val ce = le?.split(" | ") ?: emptyList()
            val co = lo?.split(" | ") ?: emptyList()
            if (ce.size <= 1 && co.size <= 1) {
                salida += "línea ${i + 1}: «${le ?: "∅"}» → «${lo ?: "∅"}»"
                continue
            }
            val caso = (co.firstOrNull() ?: ce.firstOrNull()).orEmpty()
            val instante = (co.getOrNull(1) ?: ce.getOrNull(1)).orEmpty()
            for (k in 0 until maxOf(ce.size, co.size)) {
                val fe = ce.getOrNull(k)
                val fo = co.getOrNull(k)
                if (fe != fo) {
                    val campo = (fo ?: fe).orEmpty().substringBefore('=')
                    salida += "$caso | $instante | $campo: ${fe ?: "∅"} → ${fo ?: "∅"}"
                }
            }
        }
        return salida
    }

    private fun escribir(ruta: String, texto: String) {
        val archivo = File(ruta)
        archivo.parentFile?.mkdirs()
        archivo.writeText(texto, Charsets.UTF_8)
    }

    private companion object {
        const val RECURSO = "/auge/golden/expected.txt"
    }
}
