package com.example.kpkn.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S9 (B6): `usda_es_aliases.csv` da nombre y sinónimos en español a los alimentos USDA (que vienen en inglés).
 * La tabla se genera con `android-native/scripts/build_usda_es_aliases.py` y se cura a mano; estas pruebas cuidan que
 * siga alineada con food.csv, que conserve el formato del generador y que ningún nombre o alias sea ambiguo.
 */
class UsdaAliasTableTest {

    private class Row(val fdcId: Int, val english: String, val spanish: String, val aliasesRaw: String) {
        val aliases: List<String> get() = if (aliasesRaw.isEmpty()) emptyList() else aliasesRaw.split('|')
    }

    private fun norm(value: String) = FoodImporter.normalizeSearch(value)

    /** Alimentos cotidianos (leche, huevo, pollo, arroz, pan, aceites, frutas, verduras...) que SIEMPRE llevan nombre. */
    private val everyday = mapOf(
        746782 to "leche entera", 746778 to "leche semidescremada 2%", 746776 to "leche descremada", 748967 to "huevo entero crudo",
        2646170 to "pechuga de pollo cruda", 331960 to "pechuga de pollo cocida", 2646171 to "muslo de pollo",
        2512381 to "arroz blanco", 2512380 to "arroz integral", 325871 to "pan de molde blanco",
        335240 to "pan de molde integral", 330458 to "aceite de coco", 748608 to "aceite de oliva",
        748278 to "aceite de canola", 1750349 to "aceite de maravilla", 1750341 to "manzana gala",
        1105314 to "plátano", 2346401 to "papa", 2258586 to "zanahoria", 1999634 to "tomate",
        790646 to "cebolla", 2710824 to "palta", 746771 to "naranja", 2346409 to "frutilla",
        328637 to "queso cheddar", 329370 to "queso mozzarella", 2259793 to "yogur natural", 789828 to "mantequilla",
        2262072 to "mantequilla de maní", 2346396 to "avena", 2644283 to "lentejas", 2644282 to "garbanzos",
        2644285 to "porotos negros", 2684441 to "salmón", 334194 to "atún", 2684443 to "camarón",
        2514744 to "carne molida de vacuno", 2646168 to "lomo de cerdo", 789890 to "harina de trigo", 746784 to "azúcar",
        747447 to "brócoli", 1999633 to "espinaca", 2346389 to "lechuga", 2685573 to "coliflor",
        2346404 to "camote", 2346393 to "almendras", 2346394 to "nueces", 2710826 to "choclo",
        2346398 to "piña", 2685568 to "zapallo italiano",
    )

    // ─── Alineación con food.csv ─────────────────────────────────────────────────────────────────

    @Test
    fun `the table has exactly one row per foundation food of food csv in fdc_id order`() {
        val tableIds = rows.map { it.fdcId }
        val foundationIds = UsdaTestAssets.foundation.map { it.fdcId }
        assertEquals("fdc_id repetidos", tableIds.size, tableIds.toSet().size)
        assertEquals("la tabla debe tener todos los foundation_food y solo ellos", foundationIds.toSet(), tableIds.toSet())
        assertEquals("filas fuera de orden por fdc_id", tableIds.sorted(), tableIds)
    }

    @Test
    fun `the English description is the one in food csv`() {
        // Se compara normalizado: el lector mínimo de las pruebas descarta las comillas de las pulgadas (0" fat).
        val english = UsdaTestAssets.foundation.associate { it.fdcId to norm(it.description) }
        rows.forEach { assertEquals("fdc ${it.fdcId}", english[it.fdcId], norm(it.english)) }
        rows.forEach { assertEquals("fdc ${it.fdcId}: espacios", it.english.trim().split(Regex("""\s+""")).joinToString(" "), it.english) }
        rows.forEach { assertFalse("fdc ${it.fdcId}: espacio duro", it.english.contains(Char(0x00A0))) }
    }

    @Test
    fun `the file is exactly what the generator script writes`() {
        val text = UsdaTestAssets.text("usda_es_aliases.csv")
        assertFalse("sin BOM", text.startsWith(Char(0xFEFF).toString()))
        assertEquals(listOf("fdc_id", "en_description", "es_name", "aliases"), parseCsv(text).first())
        // csv.writer de Python: comillas solo si hace falta, LF, sin espacios sobrantes.
        assertEquals(text.replace("\r\n", "\n"), renderCsv(parseCsv(text)))
    }

    // ─── Cobertura ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `fifty everyday foods have a clean Spanish name`() {
        assertEquals(50, everyday.size)
        val byId = rows.associateBy { it.fdcId }
        everyday.forEach { (fdcId, label) ->
            val row = requireNotNull(byId[fdcId]) { "fdc $fdcId ($label) no está en la tabla" }
            assertTrue("fdc $fdcId ($label) sin es_name", row.spanish.isNotBlank())
            assertFalse("fdc $fdcId ($label) lleva el calificador de duplicado: ${row.spanish}", row.spanish.contains("(FDC "))
            assertTrue("fdc $fdcId: '${row.spanish}' no menciona '$label'", norm(row.spanish).contains(norm(label)))
        }
    }

    @Test
    fun `almost every row has a Spanish name and the rest keep the English one`() {
        val unnamed = rows.filter { it.spanish.isBlank() }
        assertTrue("sin es_name: ${unnamed.map { it.fdcId }}", unnamed.size <= rows.size / 20)
        rows.forEach { assertFalse("fdc ${it.fdcId}: es_name igual al inglés", it.spanish.isNotBlank() && norm(it.spanish) == norm(it.english)) }
    }

    // ─── Sin ambigüedades ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `no two rows share the same normalized Spanish name`() {
        val owners = rows.filter { it.spanish.isNotBlank() }.groupBy { norm(it.spanish) }
        val clashes = owners.filterValues { it.size > 1 }.mapValues { (_, v) -> v.map { it.fdcId } }
        assertTrue("es_name repetidos: $clashes", clashes.isEmpty())
    }

    @Test
    fun `aliases are unique across the table and never repeat a name or the English description`() {
        val names = rows.filter { it.spanish.isNotBlank() }.associate { norm(it.spanish) to it.fdcId }
        val aliasOwner = HashMap<String, Int>()
        val problems = mutableListOf<String>()
        rows.forEach { row ->
            val own = setOf(norm(row.spanish), norm(row.english))
            val seenInRow = HashSet<String>()
            row.aliases.forEach { alias ->
                val key = norm(alias)
                if (key.isEmpty()) problems.add("fdc ${row.fdcId}: alias vacío '$alias'")
                if (key in own) problems.add("fdc ${row.fdcId}: alias '$alias' repite su propio nombre")
                if (!seenInRow.add(key)) problems.add("fdc ${row.fdcId}: alias '$alias' repetido en la fila")
                names[key]?.takeIf { it != row.fdcId }?.let { problems.add("fdc ${row.fdcId}: alias '$alias' es el nombre de $it") }
                aliasOwner.put(key, row.fdcId)?.takeIf { it != row.fdcId }?.let { problems.add("fdc ${row.fdcId}: alias '$alias' también en $it") }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `names and aliases are tidy`() {
        rows.forEach { row ->
            val id = row.fdcId
            assertEquals("fdc $id: es_name con espacios sobrantes", row.spanish.trim(), row.spanish)
            assertFalse("fdc $id: '|' dentro de es_name", row.spanish.contains('|'))
            assertEquals("fdc $id: alias con espacios sobrantes o vacíos", row.aliasesRaw.trim(), row.aliasesRaw)
            row.aliases.forEach { alias ->
                assertTrue("fdc $id: alias vacío en '${row.aliasesRaw}'", alias.isNotEmpty() && alias == alias.trim())
            }
            assertTrue("fdc $id: más de 4 alias", row.aliases.size <= 4)
            assertTrue("fdc $id: alias sin es_name", row.spanish.isNotBlank() || row.aliases.isEmpty())
        }
    }

    @Test
    fun `identical English descriptions keep one clean name and the older rows carry their FDC id`() {
        // food.csv publica varias veces el mismo alimento (2019-04 y 2019-12, o lotes de abril y octubre): misma
        // descripción, valores casi idénticos. El más nuevo (mayor fdc_id) conserva el nombre limpio y los demás se
        // distinguen con "(FDC <id>)" para que dos filas nunca se vean iguales en la lista de búsqueda.
        val includes = Regex("""\s*\(Includes[^)]*\)""")
        rows.groupBy { norm(includes.replace(it.english, "")) }.values.filter { it.size > 1 }.forEach { group ->
            val newest = group.maxOf { it.fdcId }
            group.forEach { row ->
                val qualifier = " (FDC ${row.fdcId})"
                if (row.fdcId == newest) {
                    assertFalse("fdc ${row.fdcId}: el más nuevo no lleva calificador", row.spanish.contains("(FDC "))
                } else {
                    assertTrue("fdc ${row.fdcId}: debe terminar en '$qualifier' y es '${row.spanish}'", row.spanish.endsWith(qualifier))
                    assertTrue("fdc ${row.fdcId}: los duplicados antiguos no llevan alias", row.aliases.isEmpty())
                }
            }
        }
    }

    // ─── Lo que lee el importador ───────────────────────────────────────────────────────────────────

    @Test
    fun `the importer reads every named row with its aliases`() {
        val table = FoodImporter.parseUsdaAliases(UsdaTestAssets.dataLines("usda_es_aliases.csv").asSequence())
        assertEquals(rows.count { it.spanish.isNotBlank() }, table.size)
        rows.filter { it.spanish.isNotBlank() }.forEach { row ->
            assertEquals("fdc ${row.fdcId}", FoodImporter.UsdaAlias(row.spanish, row.aliases), table[row.fdcId])
        }
        assertTrue(table.getValue(746782).aliases.contains("leche entera 3,25%"))
        // Nada de la tabla se pierde al normalizar: el nombre en español encabeza los alias de búsqueda.
        val milk = FoodImporter.usdaSearchAliases(rows.first { it.fdcId == 746782 }.english, table[746782])
        assertEquals("leche entera", milk.first())
        assertTrue(milk.contains("milk whole 3 25 milkfat with added vitamin d"))
        assertTrue(milk.contains("leche completa"))
    }

    @Test
    fun `everyday Spanish queries find exactly the intended food`() {
        val exact = HashMap<String, Int>()
        rows.forEach { row ->
            if (row.spanish.isNotBlank()) exact[norm(row.spanish)] = row.fdcId
            row.aliases.forEach { exact[norm(it)] = row.fdcId }
        }
        mapOf(
            "palta" to 2710824, "aguacate" to 2710824, "fresa" to 2346409, "frutillas" to 2346409, "choclos" to 2710826,
            "elote crudo" to 2710826, "batata" to 2346404, "remolacha" to 2685576, "calabacín" to 2685568,
            "garbanzos enlatados" to 2644288, "yogurt natural entero" to 2259793, "pechuga cocida" to 331960,
            "leche desnatada" to 746776, "leche completa" to 746782, "tahini" to 2262073, "brécol" to 747447, "ananás" to 2346398,
        ).forEach { (query, fdcId) -> assertEquals("consulta '$query'", fdcId, exact[norm(query)]) }
    }

    private companion object {
        // ─── CSV: lector y escritor mínimos con las reglas de csv.writer de Python ───────────────────────

        fun parseCsv(text: String): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            var fields = mutableListOf<String>()
            val field = StringBuilder()
            var quoted = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> quoted = !quoted
                    !quoted && c == ',' -> {
                        fields.add(field.toString())
                        field.clear()
                    }
                    !quoted && c == '\n' -> {
                        fields.add(field.toString())
                        field.clear()
                        rows.add(fields)
                        fields = mutableListOf()
                    }
                    !quoted && c == '\r' -> Unit
                    else -> field.append(c)
                }
                i++
            }
            if (field.isNotEmpty() || fields.isNotEmpty()) {
                fields.add(field.toString())
                rows.add(fields)
            }
            return rows
        }

        fun renderCsv(table: List<List<String>>): String = buildString {
            table.forEach { row ->
                append(
                    row.joinToString(",") { field ->
                        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
                    },
                )
                append('\n')
            }
        }

        val rows: List<Row> by lazy {
            parseCsv(UsdaTestAssets.text("usda_es_aliases.csv")).drop(1).map { Row(it[0].toInt(), it[1], it[2], it[3]) }
        }
    }
}
