package com.example.kpkn.data.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * La telemetría de MediaPipe (tasks-core) y de ML Kit se corta en el manifiesto (D27, decisión U9 del usuario): sin el servicio
 * y el receptor con los que datatransport programa la subida, sus métricas de uso no llegan a `firebaselogging.googleapis.com`.
 * Se comprobó en el emulador que la pose y el OCR funcionan igual y que no sale ni un byte. Esta prueba impide que un cambio
 * del manifiesto, o una versión de la librería con otros nombres, lo deshaga sin que nadie se entere. JVM puro.
 */
class DatatransportManifestTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun manifest(): Document {
        val file = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).firstOrNull { it.isFile }
        assertNotNull("No se encontró src/main/AndroidManifest.xml desde ${File(".").canonicalPath}", file)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun declared(tag: String, className: String): Element? {
        val nodes = manifest().getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }.firstOrNull { it.getAttributeNS(androidNs, "name") == className }
    }

    @Test
    fun the_job_scheduler_service_is_declared_disabled_in_the_app_manifest() {
        val service = declared("service", JOB_SERVICE)

        assertNotNull("el manifiesto de la app debe declarar $JOB_SERVICE", service)
        assertEquals("false", service!!.getAttributeNS(androidNs, "enabled"))
    }

    @Test
    fun the_alarm_receiver_is_declared_disabled_in_the_app_manifest() {
        val receiver = declared("receiver", ALARM_RECEIVER)

        assertNotNull("el manifiesto de la app debe declarar $ALARM_RECEIVER", receiver)
        assertEquals("false", receiver!!.getAttributeNS(androidNs, "enabled"))
    }

    @Test
    fun only_the_two_schedulers_are_switched_off_and_not_the_backend_discovery_or_the_rest_of_the_app() {
        val document = manifest()
        val disabled = listOf("service", "receiver", "activity", "provider").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as Element }
        }.filter { it.getAttributeNS(androidNs, "enabled") == "false" }.map { it.getAttributeNS(androidNs, "name") }

        assertEquals(setOf(JOB_SERVICE, ALARM_RECEIVER), disabled.toSet())
        assertFalse(
            "el descubrimiento del backend no se toca",
            declared("service", "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery") != null,
        )
    }

    @Test
    fun the_names_are_real_classes_of_the_library_so_a_rename_in_an_update_is_noticed() {
        for (name in listOf(JOB_SERVICE, ALARM_RECEIVER)) {
            // Sin inicializar: basta con que la clase exista (si falta una de sus superclases de Android, `LinkageError`, existe igual).
            val found = try {
                Class.forName(name, false, javaClass.classLoader)
                true
            } catch (_: ClassNotFoundException) {
                false
            } catch (_: LinkageError) {
                true
            }
            assertTrue("datatransport ya no tiene la clase $name: hay que actualizar el manifiesto", found)
        }
    }

    private companion object {
        const val JOB_SERVICE = "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService"
        const val ALARM_RECEIVER =
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver"
    }
}
