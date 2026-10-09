package com.example.kpkn.data.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.Program
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Holds the real generated DAO inside its SQLite mutation, beyond cancellation of the caller. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
class RoomWriterDrainTest {
    @Test fun admittedRoomMutationMustFinishBeforeMaintenanceCanComplete(): Unit = runBlocking {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val framework = FrameworkSQLiteOpenHelperFactory()
        val factory = SupportSQLiteOpenHelper.Factory { configuration ->
            val helper = framework.create(configuration)
            fun wrap(database: SupportSQLiteDatabase) = object : SupportSQLiteDatabase by database {
                override fun compileStatement(sql: String): SupportSQLiteStatement {
                    val statement = database.compileStatement(sql)
                    return object : SupportSQLiteStatement by statement {
                        private fun awaitInsertGate() {
                            if (sql.contains("INSERT", ignoreCase = true) && sql.contains("programs", ignoreCase = true)) {
                                entered.countDown()
                                check(finish.await(10, TimeUnit.SECONDS)) { "SQLite gate was not released" }
                            }
                        }
                        override fun execute() { awaitInsertGate(); statement.execute() }
                        override fun executeInsert(): Long {
                            awaitInsertGate()
                            return statement.executeInsert()
                        }
                    }
                }
            }
            object : SupportSQLiteOpenHelper by helper {
                override val writableDatabase: SupportSQLiteDatabase get() = wrap(helper.writableDatabase)
                override val readableDatabase: SupportSQLiteDatabase get() = wrap(helper.readableDatabase)
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, KpknDatabase::class.java).openHelperFactory(factory).build()
        val writer = DataSessionWriter("blocked-real-room-mutation")
        val caller = launch(Dispatchers.Default) { writer.write { database.programDao().upsert(Program("qa-room-drain", "Accepted").toEntity()) } }
        try {
            assertTrue("The generated DAO must reach the real SQLite insert", withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            caller.cancel()
            DataSessionWriters.closeAdmission()
            val drain = async { DataSessionWriters.awaitStopped() }
            delay(100)
            assertFalse("Cancellation must not report stopped while SQLite still writes", drain.isCompleted)
            finish.countDown()
            withTimeout(5_000) { drain.await(); caller.join() }
            assertNotNull(database.programDao().getById("qa-room-drain"))
        } finally {
            finish.countDown()
            caller.cancelAndJoin()
            DataSessionWriters.closeAdmission()
            DataSessionWriters.awaitStopped()
            DataSessionWriters.reopenAdmission()
            database.close()
        }
    }
}
