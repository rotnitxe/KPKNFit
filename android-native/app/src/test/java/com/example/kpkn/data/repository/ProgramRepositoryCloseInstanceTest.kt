package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * `closeInstance()` vuelve aunque un hijo del arranque quede esperando en `Dispatchers.Main`. El arranque publica su estado en Main;
 * aquí Main es un despachador que nadie avanza, como el hilo principal de Robolectric cuando `runBlocking` lo bloquea al cerrar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramRepositoryCloseInstanceTest {

    /** Cuenta los envíos a Main: el arranque deja uno pendiente que nadie drena. */
    private class EnviosAMain(private val destino: CoroutineDispatcher) : CoroutineDispatcher() {
        val enviados = AtomicInteger(0)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            enviados.incrementAndGet()
            destino.dispatch(context, block)
        }
    }

    private lateinit var main: EnviosAMain

    @Before
    fun setUp() {
        SyncRoomExecutor.install()
        main = EnviosAMain(StandardTestDispatcher())
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
        SyncRoomExecutor.uninstall()
    }

    @Test(timeout = 20_000)
    fun closeInstanceVuelveAunqueUnArranqueEsperaEnMain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        val db = repository.databaseForTests()
        val limite = System.currentTimeMillis() + 10_000
        while (main.enviados.get() == 0 && System.currentTimeMillis() < limite) Thread.sleep(10)
        assertTrue("el arranque debía dejar un envío pendiente a Main", main.enviados.get() > 0)

        ProgramRepository.closeInstance()

        assertFalse("closeInstance debe cerrar la base que posee el repositorio", db.isOpen)
    }
}
