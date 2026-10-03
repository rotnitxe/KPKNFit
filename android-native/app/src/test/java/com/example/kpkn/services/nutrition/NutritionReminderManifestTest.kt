package com.example.kpkn.services.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * WP-U14 / C10: el cableado del manifest que mantiene vivos los recordatorios de comida. Sin el receiver de arranque
 * las alarmas quedaban muertas hasta abrir la app; un nombre mal escrito aquí solo se descubriría en un reinicio real.
 * Puro JVM: lee `src/main/AndroidManifest.xml`.
 */
class NutritionReminderManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val bootReceiver = ".services.nutrition.NutritionReminderBootReceiver"
    private val alarmReceiver = ".services.nutrition.NutritionAlertReceiver"

    private fun manifestFile(): File {
        val relative = "src/main/AndroidManifest.xml"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, relative), File(dir, "app/$relative")).firstOrNull { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        error("No encuentro $relative subiendo desde ${File("").absolutePath}")
    }

    private val manifest: Document by lazy {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifestFile())
    }

    private fun Element.androidAttr(name: String): String = getAttributeNS(androidNs, name)

    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun receiver(name: String): Element =
        elements("receiver").firstOrNull { it.androidAttr("name") == name } ?: error("El manifest no registra $name")

    private fun actionsOf(receiver: Element): Set<String> {
        val actions = receiver.getElementsByTagName("action")
        return (0 until actions.length).map { (actions.item(it) as Element).androidAttr("name") }.toSet()
    }

    @Test
    fun theBootReceiverListensToBootUpdateAndTimezoneChanges() {
        val boot = receiver(bootReceiver)
        assertEquals("true", boot.androidAttr("exported"))
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIMEZONE_CHANGED",
            ),
            actionsOf(boot),
        )
    }

    @Test
    fun theAlarmReceiverStaysPrivate() {
        val alarm = receiver(alarmReceiver)
        assertEquals("false", alarm.androidAttr("exported"))
        assertTrue(actionsOf(alarm).isEmpty())
    }

    @Test
    fun bothReceiversPointAtRealClasses() {
        listOf(bootReceiver, alarmReceiver).forEach { name ->
            val className = "com.example.kpkn$name"
            Class.forName(className, false, javaClass.classLoader)
        }
    }

    @Test
    fun exactAlarmAndBootPermissionsAreDeclared() {
        val declared = elements("uses-permission").map { it.androidAttr("name") }.toSet()
        assertTrue("SCHEDULE_EXACT_ALARM", "android.permission.SCHEDULE_EXACT_ALARM" in declared)
        assertTrue("RECEIVE_BOOT_COMPLETED", "android.permission.RECEIVE_BOOT_COMPLETED" in declared)
    }
}
