package com.example.kpkn.data.food.catalog

import android.database.sqlite.SQLiteDatabase
import com.example.kpkn.domain.nutrition.nutrients.CatalogPortion
import com.example.kpkn.domain.nutrition.nutrients.CatalogRecord
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
import com.example.kpkn.domain.nutrition.nutrients.NutritionCatalogPort
import java.io.Closeable
import java.io.File

/**
 * Lector de solo lectura del paquete de catálogo SQLite (F1a §2). Todas las consultas son parametrizadas;
 * ausente = sin fila, 0 = cero declarado por la fuente.
 */
class SqliteNutritionCatalogReader private constructor(
    private val db: SQLiteDatabase,
    override val generation: String,
) : NutritionCatalogPort, Closeable {

    override fun record(id: String): CatalogRecord? =
        db.rawQuery("$RECORD_SELECT WHERE id = ?", arrayOf(id)).use { c -> if (c.moveToFirst()) c.toRecord() else null }

    override fun recordFor(source: String, sourceRecordId: String): CatalogRecord? {
        val upper = source.uppercase()
        val prefix = when {
            "USDA" in upper -> "usda_"
            "OFF" in upper -> "off_"
            else -> null
        }
        if (prefix != null) {
            // Camino rápido por clave primaria: así se nombran las fichas de USDA y OFF.
            record(prefix + sourceRecordId)?.takeIf { it.source.startsWith(prefix.uppercase()) }?.let { return it }
            return db.rawQuery(
                "$RECORD_SELECT WHERE source LIKE ? AND source_record_id = ? LIMIT 1",
                arrayOf(prefix.uppercase() + "%", sourceRecordId),
            ).use { c -> if (c.moveToFirst()) c.toRecord() else null }
        }
        return db.rawQuery(
            "$RECORD_SELECT WHERE source = ? AND source_record_id = ? LIMIT 1",
            arrayOf(source, sourceRecordId),
        ).use { c -> if (c.moveToFirst()) c.toRecord() else null }
    }

    override fun byGtin(gtin: String): CatalogRecord? {
        val id = db.rawQuery("SELECT record_id FROM product WHERE gtin = ?", arrayOf(gtin))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return null
        return record(id)
    }

    override fun nutrientsPer100(id: String): Map<NutrientId, Double> {
        val out = LinkedHashMap<NutrientId, Double>()
        db.rawQuery("SELECT nutrient_id, amount FROM nutrient_value WHERE record_id = ?", arrayOf(id)).use { c ->
            while (c.moveToNext()) {
                val nutrient = NutrientId.fromId(c.getInt(0)) ?: continue
                out[nutrient] = c.getDouble(1)
            }
        }
        return out
    }

    override fun portions(id: String): List<CatalogPortion> =
        db.rawQuery(
            "SELECT seq, label, label_es, grams, ml FROM portion WHERE record_id = ? ORDER BY seq",
            arrayOf(id),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CatalogPortion(
                            seq = c.getInt(0),
                            label = c.getString(1),
                            labelEs = if (c.isNull(2)) null else c.getString(2),
                            grams = c.getDouble(3),
                            ml = if (c.isNull(4)) null else c.getDouble(4),
                        ),
                    )
                }
            }
        }

    override fun search(queryNorm: String, limit: Int): List<CatalogRecord> {
        val tokens = TOKEN.findAll(queryNorm).map { it.value }.take(MAX_TOKENS).toList()
        if (tokens.isEmpty() || limit <= 0) return emptyList()
        val match = tokens.joinToString(" ") { "$it*" }
        val ids = db.rawQuery(
            // El límite es un Int ya acotado, no texto del usuario.
            "SELECT DISTINCT record_id FROM alias_fts WHERE alias_norm MATCH ? LIMIT ${limit.coerceAtMost(MAX_LIMIT)}",
            arrayOf(match),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return ids.mapNotNull { record(it) }
    }

    override fun close() {
        db.close()
    }

    private fun android.database.Cursor.toRecord() = CatalogRecord(
        id = getString(0),
        source = getString(1),
        sourceRecordId = getString(2),
        name = getString(3),
        nameEs = if (isNull(4)) null else getString(4),
        brand = if (isNull(5)) null else getString(5),
        category = if (isNull(6)) null else getString(6),
        preparation = getString(7),
        form = getString(8),
        basisAmount = getDouble(9),
        basisUnit = getString(10),
        densityGPerMl = if (isNull(11)) null else getDouble(11),
    )

    companion object {
        private const val RECORD_SELECT =
            "SELECT id, source, source_record_id, name, name_es, brand, category, preparation, form, " +
                "basis_amount, basis_unit, density_g_per_ml FROM record"
        private val TOKEN = Regex("[a-z0-9]+")
        private const val MAX_TOKENS = 6
        private const val MAX_LIMIT = 500

        /** Abre [file] con OPEN_READONLY y lee `metadata.generation`; lanza si el archivo no es un paquete válido. */
        fun open(file: File): SqliteNutritionCatalogReader {
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val generation = db.rawQuery("SELECT value FROM metadata WHERE key = 'generation'", null)
                    .use { c -> if (c.moveToFirst()) c.getString(0) else null }
                check(!generation.isNullOrBlank()) { "El paquete de catálogo no declara metadata.generation" }
                return SqliteNutritionCatalogReader(db, generation)
            } catch (error: Throwable) {
                db.close()
                throw error
            }
        }
    }
}
