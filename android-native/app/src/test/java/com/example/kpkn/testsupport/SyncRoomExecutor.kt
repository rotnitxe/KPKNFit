package com.example.kpkn.testsupport

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor

/**
 * Ejecutor síncrono para las pruebas que abren Room en memoria. Room refresca su rastreador de invalidación en el hilo de disco:
 * con este ejecutor cada refresco termina dentro de la escritura que lo provoca, así que ninguno queda en vuelo cuando la prueba
 * cierra la base (si quedara, la excepción caería en la prueba siguiente). Es lo que hace `InstantTaskExecutorRule`, sin su
 * artefacto. Se instala en `@Before` y se retira en `@After`.
 */
object SyncRoomExecutor {

    fun install() {
        ArchTaskExecutor.getInstance().setDelegate(DirectTaskExecutor)
    }

    fun uninstall() {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    private object DirectTaskExecutor : TaskExecutor() {
        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
        override fun postToMainThread(runnable: Runnable) = runnable.run()
        override fun isMainThread(): Boolean = true
    }
}
