package com.example.kpkn.testsupport

import com.example.kpkn.data.repository.SessionTemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Regla común para las pruebas que abren Room (en memoria o en archivo) con repositorios. Instala [SyncRoomExecutor] antes de
 * `@Before` y lo retira después de `@After`: los refrescos del rastreador de invalidación corren dentro de la escritura que
 * los provoca y ninguno queda en el hilo de disco cuando la prueba cierra la base. No cambia lo que la prueba comprueba.
 */
class RoomTestRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            SyncRoomExecutor.install()
            try {
                closeTemplateFixture()
                base.evaluate()
            } finally {
                try {
                    closeTemplateFixture()
                } finally {
                    SyncRoomExecutor.uninstall()
                }
            }
        }
    }

    /** Join the IO reader before Robolectric or the next fixture closes its Room database. */
    private fun closeTemplateFixture() {
        val instanceField = SessionTemplateRepository::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        val repository = instanceField.get(null) ?: return
        val scopeField = SessionTemplateRepository::class.java.getDeclaredField("scope").apply { isAccessible = true }
        val job = (scopeField.get(repository) as CoroutineScope).coroutineContext[Job]
        SessionTemplateRepository.resetForTests()
        runBlocking { withTimeout(10_000) { job?.join() } }
    }
}
