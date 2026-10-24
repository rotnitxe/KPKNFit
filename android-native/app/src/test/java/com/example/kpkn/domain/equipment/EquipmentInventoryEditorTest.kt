package com.example.kpkn.domain.equipment

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.WarmupFeasibilityStatus
import com.example.kpkn.domain.training.WarmupLoadStatus
import org.junit.Assert.*
import org.junit.Test

class EquipmentInventoryEditorTest {
    private val editor = EquipmentInventoryEditor
    private fun commercial() = editor.template(EquipmentInventoryTemplate.COMMERCIAL)

    @Test fun lasTresPlantillasProducenInventariosValidosYEditables() {
        EquipmentInventoryTemplate.entries.forEach { template ->
            val draft = editor.template(template)
            assertTrue(template.label, editor.validate(draft).isValid)
            assertEquals(draft, editor.fromInventory(editor.validate(draft).inventory!!))
        }
        assertEquals(EquipmentInventoryDraft(supportEquipment = setOf("pull_up_bar", "band")), editor.template(EquipmentInventoryTemplate.BODYWEIGHT))
    }

    @Test fun losPesosDuplicadosSeRechazanAunqueUsenComaOPunto() {
        val draft = EquipmentInventoryDraft(plates = listOf(PlateDraft("1,25"), PlateDraft("1.25")),
            dumbbells = listOf(DumbbellDraft("10"), DumbbellDraft("10.0")), kettlebells = listOf("8", "8,0"))
        val result = editor.validate(draft)
        assertFalse(result.isValid)
        listOf("plates.1.weight", "dumbbells.1.weight", "kettlebells.1.weight").forEach { assertTrue(result.errors.containsKey(it)) }
    }

    @Test fun pesosNegativosCeroNoFinitosYFueraDeLimiteNoSeGuardan() {
        listOf("-5", "0", "NaN", "Infinity", "-Infinity", "251").forEach { weight ->
            assertFalse(weight, editor.validate(EquipmentInventoryDraft(kettlebells = listOf(weight))).isValid)
        }
        assertFalse(editor.validate(EquipmentInventoryDraft(barbellWeightKg = "101")).isValid)
        assertTrue(editor.validate(EquipmentInventoryDraft(barbellWeightKg = "")).isValid)
    }

    @Test fun cantidadesPorLadoAceptanCeroOSinLimitePeroNoNegativosNiFracciones() {
        listOf("-1", "1,5", "101", "").forEach { count ->
            assertFalse(editor.validate(EquipmentInventoryDraft(plates = listOf(PlateDraft("5", count)))).isValid)
        }
        assertEquals(0, editor.validate(EquipmentInventoryDraft(plates = listOf(PlateDraft("5", "0")))).inventory!!.plates.single().countPerSide)
        assertNull(editor.validate(EquipmentInventoryDraft(plates = listOf(PlateDraft("5", unlimited = true)))).inventory!!.plates.single().countPerSide)
    }

    @Test fun maquinaExigeTopeSuperiorPasoPositivoYMaximoNoMenorQueMinimo() {
        val row = MachineDraft("Polea", "cable", "10", "50", "5", "0")
        assertTrue(editor.validate(EquipmentInventoryDraft(machines = listOf(row))).isValid)
        listOf(row.copy(maxLoadKg = ""), row.copy(maxLoadKg = "9"), row.copy(incrementKg = "0"),
            row.copy(incrementKg = "-2"), row.copy(baseLoadKg = "51"), row.copy(maxLoadKg = "2001")).forEach {
            assertFalse(editor.validate(EquipmentInventoryDraft(machines = listOf(it))).isValid)
        }
    }

    @Test fun elNombreDeMaquinaNuncaDeterminaSuTipoYLaConfiguracionEsOpcional() {
        val row = MachineDraft("Polea Smith", "machine", "0", "100", "5", "0")
        val inventory = editor.validate(EquipmentInventoryDraft(machines = listOf(row))).inventory!!
        assertEquals("machine", inventory.machines.single().equipmentKind)
        assertNull(inventory.machines.single().configurationId)
        assertFalse(editor.validate(EquipmentInventoryDraft(machines = listOf(row.copy(equipmentKind = "")))).isValid)
    }

    @Test fun maquinasYConfiguracionesDuplicadasSeRechazan() {
        val row = MachineDraft("Prensa", "machine", "0", "100", "5", "0", "leg_press__machine")
        assertFalse(editor.validate(EquipmentInventoryDraft(machines = listOf(row, row.copy(name = " PRENSA ")))).isValid)
        assertFalse(editor.validate(EquipmentInventoryDraft(machines = listOf(row, row.copy(name = "Otra")))).isValid)
    }

    @Test fun rangoDeMancuernasEsInclusivoYConservaLaDisponibilidadDePesosExistentes() {
        val draft = EquipmentInventoryDraft(dumbbells = listOf(DumbbellDraft("4", false)))
        val result = editor.addDumbbellRange(draft, DumbbellRangeDraft("2", "10", "2"))
        assertNull(result.error)
        assertEquals(listOf("2", "4", "6", "8", "10"), result.draft.dumbbells.map { it.weightKg })
        assertFalse(result.draft.dumbbells[1].pairAvailable)
        assertTrue(editor.validate(result.draft).isValid)
    }

    @Test fun rangoDecimalNoAcumulaErrorDePuntoFlotante() {
        val result = editor.addDumbbellRange(EquipmentInventoryDraft(), DumbbellRangeDraft("1,25", "5", "1,25"))
        assertEquals(listOf("1,25", "2,5", "3,75", "5"), result.draft.dumbbells.map { it.weightKg })
    }

    @Test fun rangoInvalidoOExcesivoNoModificaElBorrador() {
        val draft = commercial()
        listOf(DumbbellRangeDraft("10", "2", "2"), DumbbellRangeDraft("2", "10", "0"),
            DumbbellRangeDraft("-2", "10", "2"), DumbbellRangeDraft("1", "250", "0,001")).forEach { range ->
            val result = editor.addDumbbellRange(draft, range)
            assertNotNull(result.error)
            assertEquals(draft, result.draft)
        }
    }

    @Test fun idaYVueltaPreservaTodasLasCantidadesTiposYConfiguraciones() {
        val inventory = EquipmentInventory(15.0, listOf(PlateStock(1.25, null), PlateStock(20.0, 2)),
            listOf(DumbbellPairStock(12.5, false)), listOf(KettlebellStock(16.0)),
            listOf(MachineLoadRange("Prensa", 10.0, 180.0, 5.0, 10.0, "machine", "leg_press__machine")), setOf("support", "band"))
        assertEquals(inventory, editor.validate(editor.fromInventory(inventory)).inventory)
    }

    @Test fun resumenComercialCompruebaElRangoYLosSaltosConElResolutorReal() {
        val summary = editor.summarize(editor.validate(commercial()).inventory!!)
        assertEquals(20.0, summary.minKg!!, 0.0)
        assertEquals(335.0, summary.maxKg!!, 0.0)
        assertEquals(2.5, summary.uniformStepKg!!, 0.0)
        assertFalse(summary.boundedPreview)
    }

    @Test fun resumenDeclaraHuecosYSinBarraNoInventaUna() {
        val summary = editor.summarize(EquipmentInventory(20.0, listOf(PlateStock(5.0, 1), PlateStock(20.0, 1))))
        assertNull(summary.uniformStepKg)
        assertTrue(summary.warnings.any { it.contains("inalcanzables") })
        val noBar = editor.summarize(EquipmentInventory(plates = listOf(PlateStock(20.0, 1))))
        assertNull(noBar.minKg)
        assertNull(noBar.maxKg)
        assertTrue(noBar.warnings.isNotEmpty())
    }

    @Test fun resumenDeStockIlimitadoAcotaLaVistaSinInventarUnTope() {
        val summary = editor.summarize(EquipmentInventory(20.0, listOf(PlateStock(1.25))))
        assertTrue(summary.boundedPreview)
        assertEquals(EquipmentInventoryEditor.SUMMARY_LIMIT_KG, summary.maxKg!!, 0.0)
    }

    @Test fun conBarraYDiscosLosCalentamientosDanKgYConInventarioVacioSiguenPendientes() {
        val warmups = listOf(WarmupSetDefinition("a", 50.0, 8), WarmupSetDefinition("b", 75.0, 5), WarmupSetDefinition("c", 100.0, 3))
        val plan = PlanMaterializer.realizeWarmupLoads(warmups = warmups, workingLoadKg = 60.0,
            inventory = editor.validate(commercial()).inventory!!, equipmentKind = "barbell", deduplicate = false)
        // P2-Tb: el 100 % ya es trabajo, aunque los discos permitan montarlo.
        assertEquals(listOf(30.0, 45.0, null), plan.entries.map { it.realizedKg })
        assertTrue(plan.entries.take(2).all { it.status == WarmupLoadStatus.READY })
        assertEquals(WarmupLoadStatus.PENDING_PERCENT, plan.entries.last().status)
        assertNull(plan.entries.last().realizedKg)
        assertTrue(plan.validationMessages.any { it.contains("85%") })
        assertEquals(WarmupFeasibilityStatus.REALIZABLE, plan.feasibility.status)
        val empty = PlanMaterializer.realizeWarmupLoads(warmups = warmups, workingLoadKg = 60.0,
            inventory = EquipmentInventory(), equipmentKind = "barbell", deduplicate = false)
        assertTrue(empty.entries.all { it.status == WarmupLoadStatus.PENDING_PERCENT && it.realizedKg == null })
    }
}
