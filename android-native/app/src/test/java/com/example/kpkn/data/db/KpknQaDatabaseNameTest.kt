package com.example.kpkn.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class KpknQaDatabaseNameTest {

    @Test
    fun exact_vt_debug_application_id_uses_isolated_room_database() {
        assertEquals(
            "kpkn-vt-qa-v29.db",
            KpknDatabase.databaseNameForApplicationId("com.example.kpkn.dbg"),
        )
        assertEquals(
            "kpkn-vt-qa-v29_snapshot_",
            DatabaseBackupHelper.snapshotFilePrefixForApplicationId("com.example.kpkn.dbg"),
        )
    }

    @Test
    fun official_application_id_keeps_existing_room_database_name() {
        assertEquals(
            "kpkn.db",
            KpknDatabase.databaseNameForApplicationId("com.example.kpkn"),
        )
        assertEquals(
            "kpkn_snapshot_",
            DatabaseBackupHelper.snapshotFilePrefixForApplicationId("com.example.kpkn"),
        )
    }

    @Test
    fun similar_or_unknown_application_ids_keep_existing_room_database_name() {
        assertEquals(
            "kpkn.db",
            KpknDatabase.databaseNameForApplicationId("com.example.kpkn.debug"),
        )
        assertEquals(
            "kpkn_snapshot_",
            DatabaseBackupHelper.snapshotFilePrefixForApplicationId("com.example.kpkn.debug"),
        )
        assertEquals(
            "kpkn.db",
            KpknDatabase.databaseNameForApplicationId("com.example.kpkn.dbg2"),
        )
        assertEquals(
            "kpkn.db",
            KpknDatabase.databaseNameForApplicationId("com.example.other"),
        )
        assertEquals(
            "kpkn_snapshot_",
            DatabaseBackupHelper.snapshotFilePrefixForApplicationId("com.example.kpkn.dbg2"),
        )
    }
}
