package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import com.example.kpkn.domain.calculations.PlateCalculator
import org.junit.Assert.*
import org.junit.Test

class MfpWarmupInventoryBankTest {
    private data class Stock(val name: String,val inventory: EquipmentInventory,val kind: String,val machine: MachineLoadRange?=null)
    @Test fun bancoDe20A250KgNuncaSuperaTrabajoYLoEmitidoEsMontable() {
        val commercial=EquipmentInventory(20.0,listOf(25.0,20.0,15.0,10.0,5.0,2.5,1.25).map { PlateStock(it,6) })
        val home=EquipmentInventory(20.0,listOf(PlateStock(10.0,2),PlateStock(2.5,1)))
        val dumbbells=EquipmentInventory(dumbbells=(2..50).map { DumbbellPairStock(it*2.5,true) })
        val machine=MachineLoadRange("Prensa",0.0,300.0,5.0,0.0,"machine","machine-fixture")
        val stocks=listOf(Stock("comercial",commercial,"barbell"),Stock("casa",home,"barbell"),Stock("mancuernas",dumbbells,"dumbbells"),Stock("maquina",EquipmentInventory(machines=listOf(machine)),"machine",machine))
        var checked=0
        val errors=mutableListOf<String>()
        for(work in 20..250 step 5) for(level in ApproachLevel.entries) for(heavy in listOf(false,true)) for(stock in stocks) {
            val warmups=ApproachRamp.definitions("fixture",ApproachRamp.long(level,heavy))
            val result=PlanMaterializer.realizeWarmupLoads(warmups,work.toDouble(),stock.inventory,machine=stock.machine,
                configurationId=stock.machine?.configurationId,equipmentKind=stock.kind)
            result.entries.filter { it.status==WarmupLoadStatus.READY }.forEach { entry ->
                val kg=requireNotNull(entry.realizedKg);checked++
                val clue="${stock.name}/$work/${level.name}/$heavy/$kg"
                if(kg>work+.001) errors+="$clue supera trabajo"
                if(kg>=work-.001 && !(stock.kind=="barbell" && work.toDouble()==stock.inventory.barbellWeightKg)) errors+="$clue iguala trabajo sin regla de barra minima"
                val mountable=when(stock.kind) {
                    "barbell" -> PlateCalculator.calculatePlates(kg,stock.inventory).isExact
                    "dumbbells" -> stock.inventory.dumbbells.any { it.pairAvailable && kotlin.math.abs(it.weightPerUnitKg-kg)<.001 }
                    else -> kotlin.math.abs(kg/5.0-kotlin.math.round(kg/5.0))<.001 && kg in 0.0..300.0
                }
                if(!mountable) errors+="$clue no montable"
            }
        }
        assertTrue("El banco debe probar cargas reales",checked>1000)
        assertTrue(errors.take(30).joinToString("\n"),errors.isEmpty())
    }
    @Test fun inventarioVacioNoInventaKilosYLasRampasTienenTopeFinito() {
        for(level in ApproachLevel.entries) for(heavy in listOf(false,true)) {
            val long=ApproachRamp.long(level,heavy);val short=ApproachRamp.short(level,heavy)
            assertTrue(long.size<=4);assertTrue(short.size<=2)
            assertTrue(long.sumOf { it.restSeconds }<=195);assertTrue(short.sumOf { it.restSeconds }<=105)
            val result=PlanMaterializer.realizeWarmupLoads(ApproachRamp.definitions("none",long),100.0,EquipmentInventory(),equipmentKind="barbell")
            assertTrue(result.entries.all { it.realizedKg==null })
        }
    }
}
