package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class PlateLoadLabelTest {
    private val kind=NativeLoadConventions.StockKind.BARBELL
    private val convention=LoadQuantityConvention.TOTAL_EXTERNAL
    private val inventory=EquipmentInventory(20.0,listOf(PlateStock(25.0,1),PlateStock(20.0,1),PlateStock(15.0,0),PlateStock(10.0,1),PlateStock(5.0,1),PlateStock(2.5,1),PlateStock(1.25,1)))
    @Test fun cienY62coma5CuadranConBarraYStock() {
        val hundred=PlateLoadLabels.build(100.0,convention,kind,inventory)!!
        assertEquals(listOf(25.0,10.0,5.0),hundred.platesPerSide);assertTrue(hundred.exact)
        assertEquals("por lado: 25 + 10 + 5",hundred.text)
        val smaller=PlateLoadLabels.build(62.5,convention,kind,inventory)!!
        assertEquals(listOf(20.0,1.25),smaller.platesPerSide)
        listOf(hundred,smaller).forEach { assertEquals(it.displayedKg,it.barKg+2*it.platesPerSide.sum(),.0001) }
    }
    @Test fun masCercanaMontablePuedeEstarArribaYPrefiereSueloEnEmpate() {
        val limited=EquipmentInventory(20.0,listOf(PlateStock(20.0,1),PlateStock(1.25,1)))
        val above=PlateLoadLabels.build(59.0,convention,kind,limited)!!
        assertEquals(60.0,above.displayedKg,0.0);assertFalse(above.exact);assertTrue(above.text.contains("más cercana montable"))
        val tie=PlateLoadLabels.build(61.25,convention,kind,limited)!!
        assertEquals(60.0,tie.displayedKg,0.0)
    }
    @Test fun inventarioBarraCantidadOConvencionDesconocidosNoProducenEtiqueta() {
        assertNull(PlateLoadLabels.build(100.0,convention,kind,null))
        assertNull(PlateLoadLabels.build(100.0,convention,kind,inventory.copy(barbellWeightKg=null)))
        assertNull(PlateLoadLabels.build(100.0,convention,kind,inventory.copy(plates=listOf(PlateStock(20.0,null)))))
        NativeLoadConventions.StockKind.entries.filter { it!=kind }.forEach { assertNull(PlateLoadLabels.build(100.0,convention,it,inventory)) }
        assertNull(PlateLoadLabels.build(100.0,LoadQuantityConvention.PER_IMPLEMENT,kind,inventory))
    }
    @Test fun todaEtiquetaRespetaCantidadesFinitaYSumaExacta() {
        (20..250 step 5).forEach { target ->
            val label=PlateLoadLabels.build(target.toDouble(),convention,kind,inventory)!!
            assertEquals(label.displayedKg,20.0+2*label.platesPerSide.sum(),.001)
            label.platesPerSide.groupingBy { it }.eachCount().forEach { (weight,count) -> assertTrue(count<=inventory.plates.first { it.weightKg==weight }.countPerSide!!) }
        }
    }
}
