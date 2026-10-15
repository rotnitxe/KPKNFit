package com.example.kpkn.testsupport

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
                base.evaluate()
            } finally {
                SyncRoomExecutor.uninstall()
            }
        }
    }
}
