package com.example.kpkn.screens.settings.equipment

import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.domain.equipment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentInventoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<EquipmentInventoryViewModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private class Store : EquipmentInventoryStore {
        val writes = mutableListOf<EquipmentInventory>()
        var fail = false
        var loadFail = false
        override suspend fun load(): EquipmentInventory {
            if (loadFail) error("Fallo de lectura simulado")
            return EquipmentInventory()
        }
        override suspend fun save(inventory: EquipmentInventory) {
            if (fail) error("Fallo simulado")
            writes += inventory
        }
    }
    private fun model(store: Store) = EquipmentInventoryViewModel(store, { emptyList() }, dispatcher).also { models += it }

    @Test fun guardarEscribeExactamenteElInventarioEditado() {
        val store = Store()
        val model = model(store)
        model.useTemplate(EquipmentInventoryTemplate.HOME)
        val expected = EquipmentInventoryEditor.validate(model.state.value.draft).inventory
        assertTrue(store.writes.isEmpty())
        model.save()
        assertEquals(listOf(expected), store.writes)
        assertFalse(model.state.value.dirty)
        assertTrue(model.state.value.exitRequested)
    }

    @Test fun plantillaYDescartarNoEscriben() {
        val store = Store()
        val model = model(store)
        model.useTemplate(EquipmentInventoryTemplate.BODYWEIGHT)
        model.discard()
        assertTrue(store.writes.isEmpty())
        assertEquals(EquipmentInventoryDraft(), model.state.value.draft)
        assertTrue(model.state.value.exitRequested)
    }

    @Test fun elegirUnaPlantillaDesdeLaHojaLaCierraSinGuardar() {
        val store = Store()
        val model = model(store)
        model.openEditor(EquipmentSection.TEMPLATES)
        model.useTemplate(EquipmentInventoryTemplate.HOME)
        assertNull(model.state.value.editing)
        assertTrue(model.state.value.dirty)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun salirConCambiosAvisaYPermiteSeguirEditando() {
        val model = model(Store())
        model.edit(model.state.value.draft.copy(barbellWeightKg = "15"))
        model.requestExit()
        assertTrue(model.state.value.showDiscardDialog)
        assertFalse(model.state.value.exitRequested)
        model.keepEditing()
        assertFalse(model.state.value.showDiscardDialog)
        assertEquals("15", model.state.value.draft.barbellWeightKg)
    }

    @Test fun salirSinCambiosNoPideDescartar() {
        val model = model(Store())
        model.requestExit()
        assertTrue(model.state.value.exitRequested)
        assertFalse(model.state.value.showDiscardDialog)
    }

    @Test fun datosInvalidosNuncaLleganAlRepositorio() {
        val store = Store()
        val model = model(store)
        model.edit(model.state.value.draft.copy(barbellWeightKg = "-20"))
        model.save()
        assertTrue(store.writes.isEmpty())
        assertTrue(model.state.value.errors.containsKey("barbell"))
        assertFalse(model.state.value.exitRequested)
    }

    @Test fun falloDeGuardadoConservaElBorradorYSePuedeReintentar() {
        val store = Store().apply { fail = true }
        val model = model(store)
        model.edit(model.state.value.draft.copy(barbellWeightKg = "15"))
        model.save()
        assertNotNull(model.state.value.saveError)
        assertTrue(model.state.value.dirty)
        assertFalse(model.state.value.exitRequested)
        store.fail = false
        model.save()
        assertEquals(15.0, store.writes.single().barbellWeightKg!!, 0.0)
    }

    @Test fun agregarYQuitarFilasSoloEditaElBorrador() {
        val store = Store()
        val model = model(store)
        listOf(EquipmentSection.PLATES, EquipmentSection.DUMBBELLS, EquipmentSection.KETTLEBELLS, EquipmentSection.MACHINES).forEach { section ->
            model.openEditor(section)
            assertEquals(section, model.state.value.editing?.section)
            model.removeEditingRow()
            assertEquals(EquipmentInventoryDraft(), model.state.value.draft)
        }
        assertTrue(store.writes.isEmpty())
    }

    @Test fun elAsistenteDeRangoSeValidaSinGuardar() {
        val store = Store()
        val model = model(store)
        model.openEditor(EquipmentSection.DUMBBELL_RANGE)
        model.editRange(DumbbellRangeDraft("2", "8", "0"))
        model.addRange()
        assertNotNull(model.state.value.rangeError)
        model.editRange(DumbbellRangeDraft("2", "8", "2"))
        model.addRange()
        assertEquals(4, model.state.value.draft.dumbbells.size)
        assertNull(model.state.value.editing)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun siFallaLaLecturaNoSeSobrescribeElMaterialConUnBorradorVacio() {
        val store = Store().apply { loadFail = true }
        val model = model(store)
        assertNotNull(model.state.value.loadError)
        model.useTemplate(EquipmentInventoryTemplate.COMMERCIAL)
        model.save()
        assertTrue(store.writes.isEmpty())
        assertFalse(model.state.value.dirty)
    }
}
