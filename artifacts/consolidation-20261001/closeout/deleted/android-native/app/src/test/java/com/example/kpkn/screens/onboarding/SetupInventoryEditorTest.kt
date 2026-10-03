package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.domain.onboarding.SetupInventoryGroup
import com.example.kpkn.domain.onboarding.SetupStepId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda inventory: la edición de filas vive en el borrador
 * (`stepEditors[step]`: fila, fase y crudos) para reanudar exacta tras
 * Atrás/Guardar y salir; «No tengo este material» es un token explícito por
 * grupo que solo retira ese grupo; y las máquinas reparten sus cinco datos en
 * subfases de máximo dos campos relacionados. Contratos puros, sin Compose ni
 * ViewModel y sin ejecución en esta rama (el build central manda).
 */
class SetupInventoryEditorTest {

    @Test
    fun `la edicion persiste fila fase y crudos para reanudar`() {
        val opened = openStepEditor(itemIndex = 2, values = mapOf("weight" to "20"))
        assertTrue(opened.editing)
        assertEquals(2, opened.itemIndex)
        assertEquals(0, opened.phase)

        val typed = withEditorValue(opened, "count", "4")
        assertTrue(typed.editing)
        assertEquals("el crudo anterior se conserva", "20", typed.values["weight"])
        assertEquals("4", typed.values["count"])

        val moved = withEditorPhase(typed, 1)
        assertEquals(1, moved.phase)
        assertEquals(2, moved.itemIndex)
        assertEquals("cambiar de fase no toca los crudos", typed.values, moved.values)
        assertTrue(moved.editing)
    }

    @Test
    fun `maquinas reparte cinco datos en subfases de maximo dos campos`() {
        assertEquals(listOf("name"), machineEditorKeys(0))
        assertEquals(listOf("min", "max"), machineEditorKeys(1))
        assertEquals(listOf("inc", "base"), machineEditorKeys(2))
        (0 until MACHINE_EDITOR_PHASES).forEach { phase ->
            assertTrue("la fase $phase supera dos campos", machineEditorKeys(phase).size <= 2)
        }
        assertEquals(
            setOf("name", "min", "max", "inc", "base"),
            (0 until MACHINE_EDITOR_PHASES).flatMap { machineEditorKeys(it) }.toSet(),
        )
    }

    @Test
    fun `retirar un grupo solo vacia ese grupo y crea inventario vacio si no existe`() {
        val source = EquipmentInventory(
            barbellWeightKg = 15.0,
            plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)),
            dumbbells = listOf(DumbbellPairStock(weightPerUnitKg = 8.0)),
        )
        val cleared = clearInventoryGroup(source, SetupInventoryGroup.PLATES)
        assertEquals(emptyList<PlateStock>(), cleared.plates)
        assertEquals(15.0, cleared.barbellWeightKg!!, 0.0)
        assertEquals(1, cleared.dumbbells.size)

        // Sin inventario aún: se crea vacío, sin asumir material (nada de 20 kg).
        assertEquals(EquipmentInventory(), clearInventoryGroup(null, SetupInventoryGroup.BARBELL))
    }

    @Test
    fun `agregar quita el token none solo del paso que guarda`() {
        val selections = mapOf(
            SetupStepId.INVENTORY_PLATES to listOf(INVENTORY_NONE),
            SetupStepId.INVENTORY_DUMBBELLS to listOf(INVENTORY_NONE, "legacy"),
        )

        val afterPlates = withoutNoneToken(selections, SetupStepId.INVENTORY_PLATES)
        // Solo tenía none → la clave del paso sale del mapa.
        assertFalse(afterPlates.containsKey(SetupStepId.INVENTORY_PLATES))
        // El resto de grupos queda intacto.
        assertEquals(listOf(INVENTORY_NONE, "legacy"), afterPlates[SetupStepId.INVENTORY_DUMBBELLS])

        // none junto a otro token → se retira únicamente none.
        assertEquals(
            listOf("legacy"),
            withoutNoneToken(selections, SetupStepId.INVENTORY_DUMBBELLS)[SetupStepId.INVENTORY_DUMBBELLS],
        )

        // Un paso sin none no cambia.
        assertEquals(selections, withoutNoneToken(selections, SetupStepId.INVENTORY_BARBELL))
    }

    @Test
    fun `validacion de maquinas exige tipo tope y configuracion generica`() {
        val ok = mapOf(
            "name" to "Prensa",
            "min" to "0",
            "max" to "200",
            "inc" to "5",
            "base" to "10",
            "kind" to "machine",
            "config" to "leg_press__machine",
        )
        assertTrue(machineValuesValid(ok))
        // Máquina genérica sin configuración real: no se guarda (el nombre no basta).
        assertFalse("genérica sin configuración", machineValuesValid(ok - "config"))
        // Estación multi: cable/Smith sólo exigen tipo + rango válido.
        assertTrue(machineValuesValid((ok - "config") + ("kind" to "cable")))
        assertTrue(machineValuesValid((ok - "config") + ("kind" to "smith_machine")))
        // Tipo obligatorio en toda declaración nueva; nunca deducido del nombre.
        assertFalse("tipo sin declarar", machineValuesValid(ok - "kind"))
        // Tope máximo obligatorio: nada de máquinas ilimitadas.
        assertFalse("máquina sin máximo = ilimitada", machineValuesValid(ok - "max"))
        assertFalse("máquina sin máximo = ilimitada", machineValuesValid(ok + ("max" to "")))
        assertFalse(machineValuesValid(ok + ("min" to "300")))
        assertFalse(machineValuesValid(ok + ("inc" to "0")))
        assertFalse(machineValuesValid(ok - "base"))
        assertFalse(machineValuesValid(ok + ("name" to "   ")))
    }

    @Test
    fun `el none de la barra conserva los soportes declarados a mano`() {
        val source = EquipmentInventory(
            barbellWeightKg = 20.0,
            supportEquipment = setOf("support", "pull_up_bar"),
        )
        val cleared = clearInventoryGroup(source, SetupInventoryGroup.BARBELL)
        assertNull("none de barra no debe borrar soportes", cleared.barbellWeightKg)
        assertEquals(setOf("support", "pull_up_bar"), cleared.supportEquipment)
        // Los demás grupos quedan intactos.
        assertEquals(emptySet<String>(), clearInventoryGroup(null, SetupInventoryGroup.PLATES).supportEquipment)
    }

    @Test
    fun `soportes crudos admiten solo ids canonicos y rack o banco se acreditan como support`() {
        assertEquals(setOf("support", "ball"), supportEquipmentFromRaw("support, ball"))
        assertEquals(setOf("support"), supportEquipmentFromRaw("rack, bench"))
        assertEquals(setOf("support", "band"), supportEquipmentFromRaw("support, band, id_inventado"))
        assertEquals(emptySet<String>(), supportEquipmentFromRaw(""))
        assertEquals("support,pull_up_bar", rawFromSupportEquipment(setOf("pull_up_bar", "support")))
        assertEquals(setOf("band"), supportEquipmentFromRaw(rawFromSupportEquipment(setOf("band"))))
    }

    @Test
    fun `el tipo de maquina se declara explicitamente y el nombre nunca lo deduce`() {
        assertEquals("cable", machineKindOrNull("cable"))
        assertEquals("smith_machine", machineKindOrNull("smith_machine"))
        assertNull(machineKindOrNull(""))
        assertNull(machineKindOrNull("Prensa"))
    }

    @Test
    fun `peso y cantidad de discos exigen valores explicitos finitos`() {
        assertTrue(positiveWeightValid("20"))
        assertTrue(positiveWeightValid("1.25"))
        assertFalse(positiveWeightValid(""))
        assertFalse(positiveWeightValid("-5"))
        assertFalse(positiveWeightValid("abc"))

        assertTrue(plateCountValid("2"))
        assertTrue(plateCountValid("0"))
        assertFalse(plateCountValid(""))
        assertFalse(plateCountValid("1.5"))
        assertFalse(plateCountValid("-1"))
    }
}
