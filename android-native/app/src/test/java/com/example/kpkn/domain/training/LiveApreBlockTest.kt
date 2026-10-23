package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.SetPurpose
import org.junit.Assert.*
import org.junit.Test

class LiveApreBlockTest {
    private fun block(variant: ApreVariant = ApreVariant.APRE_6): Exercise {
        val sets = Apre.block(200.0, variant).map { item ->
            ExerciseSet(id = "s${item.ordinal}", weight = item.loadKg, targetReps = item.targetReps,
                purpose = item.purpose, amrapMinimumReps = item.minimumReps)
        }
        return Exercise(id = "press", name = "Press", sets = sets,
            apreBlock = ApreBlockReference(variant.xrmReps, sets.map { it.id }))
    }

    @Test fun lasTresVariantesMantienenS3YS4DelMarcador() {
        ApreVariant.entries.forEach { variant ->
            val result = resolveLiveApreBlock(block(variant))!!
            assertEquals(variant, result.variant)
            assertEquals(2, result.thirdIndex)
            assertEquals(3, result.fourthIndex)
            assertEquals(listOf("s1", "s2", "s3", "s4"), result.setIds)
        }
    }

    @Test fun unAmrapNormalNoEsUnBloquePorParecido() {
        val plain = block().copy(apreBlock = null)
        assertNull(resolveLiveApreBlock(plain))
        assertNull(resolveLiveApreBlock(plain, "otro-programa", true))
        assertNull(resolveLiveApreBlock(plain, "apre-mann", false))
        assertNotNull(resolveLiveApreBlock(plain, "apre-mann", true))
    }

    @Test fun marcadorInvalidoNoSeReparaConLaReceta() {
        val base = block()
        val invalid = base.copy(apreBlock = base.apreBlock!!.copy(setIds = listOf("s1", "s2", "s3", "borrada")))
        assertNull(resolveLiveApreBlock(invalid, "apre-mann", true))
        assertNull(resolveLiveApreBlock(base.copy(apreBlock = ApreBlockReference(5, base.apreBlock!!.setIds))))
    }

    @Test fun borrarReordenarODuplicarUnaIdentidadInvalidaElBloque() {
        val base = block()
        assertNull(resolveLiveApreBlock(base.copy(sets = base.sets.dropLast(1))))
        assertNull(resolveLiveApreBlock(base.copy(sets = listOf(base.sets[1], base.sets[0]) + base.sets.drop(2))))
        assertNull(resolveLiveApreBlock(base.copy(sets = base.sets + base.sets[2])))
        assertNull(resolveLiveApreBlock(base.copy(apreBlock = ApreBlockReference(6, listOf("s1", "s2", "s3", "s3")))))
    }

    @Test fun niCalibradorNiMinimoEditadoEntranEnMann() {
        val base = block()
        assertNull(resolveLiveApreBlock(base.copy(sets = base.sets.mapIndexed { i, s ->
            if (i == 2) s.copy(purpose = SetPurpose.CALIBRATOR) else s
        })))
        assertNull(resolveLiveApreBlock(base.copy(sets = base.sets.mapIndexed { i, s ->
            if (i == 3) s.copy(amrapMinimumReps = 10) else s
        })))
    }

    @Test fun unaSeriePosteriorNoSustituyeLaS4Marcada() {
        val base = block()
        val extra = ExerciseSet(id = "s5", purpose = SetPurpose.AMRAP, amrapMinimumReps = 6)
        assertEquals(3, resolveLiveApreBlock(base.copy(sets = base.sets + extra))!!.fourthIndex)
        // Sin marcador, una receta ya editada con cinco series no se adivina.
        assertNull(resolveLiveApreBlock(base.copy(sets = base.sets + extra, apreBlock = null), "apre-mann", true))
    }

    @Test fun noSeIntercalaOtraSerieNiSeAceptanHuecos() {
        val base = block()
        assertNull(resolveLiveApreBlock(base.copy(sets = base.sets.take(3) + ExerciseSet(id = "extra") + base.sets.last())))
        assertNull(resolveLiveApreBlock(base.copy(sets = base.sets.mapIndexed { i, s -> if (i == 3) s.copy(isEmptySlot = true) else s })))
    }
}
