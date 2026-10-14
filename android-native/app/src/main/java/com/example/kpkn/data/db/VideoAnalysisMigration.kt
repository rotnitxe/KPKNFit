package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room v29: la tabla `video_analysis` (caché del análisis de técnica en vídeo, §3.9), los ids de catálogo en
 * `workout_media` y el índice por `sessionKey`.
 *
 * Solo AÑADE: dos columnas nulables, un índice y una tabla nueva. No copia, mueve ni borra ninguna fila existente, así que
 * los medios, las series y los programas de los usuarios pasan intactos. El DDL es el de `schemas/…/29.json` (lo genera KSP),
 * sin ningún `DEFAULT` que Room no espere: si se cambia [VideoAnalysisEntity] hay que copiar aquí el nuevo `createSql`
 * (lo vigila `VideoAnalysisMigrationValidateTest`).
 *
 * Es idempotente (`IF NOT EXISTS` y columnas solo si faltan): aplicada sobre una base que ya tiene el esquema nuevo no hace
 * nada, y las pruebas pueden rebobinar un archivo v29 para ejercitar `onUpgrade`.
 *
 * [from] permite renumerarla si otra rama toma el 29: `videoAnalysisMigration(28)` es la 28→29.
 */
fun videoAnalysisMigration(from: Int): Migration = object : Migration(from, from + 1) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing(table = "workout_media", column = "catalogDefinitionId", type = "TEXT")
        db.addColumnIfMissing(table = "workout_media", column = "catalogConfigurationId", type = "TEXT")
        db.execSQL(WORKOUT_MEDIA_SESSION_KEY_INDEX_SQL)
        db.execSQL(VIDEO_ANALYSIS_CREATE_SQL)
        VIDEO_ANALYSIS_INDEX_SQL.forEach { db.execSQL(it) }
    }
}

/** `ALTER TABLE … ADD COLUMN` no tiene `IF NOT EXISTS`: se mira antes. */
private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, column: String, type: String) {
    val exists = query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        var found = false
        while (!found && cursor.moveToNext()) found = cursor.getString(nameIndex) == column
        found
    }
    if (!exists) execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $type")
}

internal const val WORKOUT_MEDIA_SESSION_KEY_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS `index_workout_media_sessionKey` ON `workout_media` (`sessionKey`)"

/** El `createSql` de `video_analysis` en `29.json`, con el nombre de la tabla puesto. */
internal const val VIDEO_ANALYSIS_CREATE_SQL = """
CREATE TABLE IF NOT EXISTS `video_analysis` (
`mediaId` TEXT NOT NULL, `status` TEXT NOT NULL, `progressPct` INTEGER NOT NULL, `errorCode` TEXT,
`engineVersion` INTEGER NOT NULL, `poseVersion` INTEGER NOT NULL, `poseEngine` TEXT, `profileId` TEXT,
`exerciseKey` TEXT NOT NULL, `configurationKey` TEXT, `exerciseName` TEXT, `setKey` TEXT, `loadKg` REAL,
`recordedAtMs` INTEGER NOT NULL, `sourceFps` REAL, `analyzedFps` REAL, `viewKind` TEXT, `confidence` TEXT,
`repCount` INTEGER, `partialReps` INTEGER, `failedReps` INTEGER,
`tutMs` INTEGER, `tempoEccMs` INTEGER, `tempoPauseAfterEccMs` INTEGER, `tempoConMs` INTEGER, `tempoPauseAfterConMs` INTEGER,
`romMeanDeg` REAL, `romCvPct` REAL,
`velocityUnit` TEXT, `velocityLossPct` REAL, `mcvFirst` REAL, `mcvBest` REAL, `mcvLast` REAL, `peakBest` REAL,
`scaleSource` TEXT, `plateDiameterMm` INTEGER, `barDeviation` REAL, `barDeviationUnit` TEXT, `trunkMeanDeg` REAL,
`flagsJson` TEXT NOT NULL, `reportPath` TEXT, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL,
PRIMARY KEY(`mediaId`),
FOREIGN KEY(`mediaId`) REFERENCES `workout_media`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
)
"""

/** Los índices de `video_analysis` en `29.json`: por estado y por ejercicio y fecha. */
internal val VIDEO_ANALYSIS_INDEX_SQL: List<String> = listOf(
    "CREATE INDEX IF NOT EXISTS `index_video_analysis_status` ON `video_analysis` (`status`)",
    "CREATE INDEX IF NOT EXISTS `index_video_analysis_exerciseKey_recordedAtMs` " +
        "ON `video_analysis` (`exerciseKey`, `recordedAtMs`)",
)
