package com.example.kpkn.data.account

import com.example.kpkn.domain.entitlements.ConfirmedEntitlement
import com.example.kpkn.domain.entitlements.EntitlementState
import com.example.kpkn.domain.entitlements.PlanLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Interruptor de Pro de depuración: la parte pura y su publicación en `AccountGraph`, sin Firebase. */
@OptIn(ExperimentalCoroutinesApi::class)
class BuildEntitlementOverrideTest {

    @Before fun hiloPrincipal() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After fun apagarYLimpiar() {
        BuildEntitlementOverride.setForcedPro(false)
        Dispatchers.resetMain()
    }

    @Test
    fun `con el interruptor apagado devuelve el mismo estado`() {
        val state = estadoFree()
        assertEquals(state, BuildEntitlementOverride.apply(state))
    }

    @Test
    fun `encendido devuelve PRO y conserva el resto de campos`() {
        BuildEntitlementOverride.setForcedPro(true)
        val state = estadoFree()
        val result = BuildEntitlementOverride.apply(state)
        assertEquals(PlanLevel.PRO, result.effectiveLevel)
        assertEquals(state.copy(effectiveLevel = PlanLevel.PRO), result)
    }

    @Test
    fun `al apagarlo vuelve a FREE`() {
        BuildEntitlementOverride.setForcedPro(true)
        BuildEntitlementOverride.setForcedPro(false)
        assertEquals(PlanLevel.FREE, BuildEntitlementOverride.apply(estadoFree()).effectiveLevel)
    }

    @Test
    fun `republicar en AccountGraph aplica el interruptor y lo quita al apagarlo`() = runTest {
        AccountGraph.stop()
        BuildEntitlementOverride.setForcedPro(true)
        AccountGraph.republishEntitlement()
        assertEquals(PlanLevel.PRO, AccountGraph.entitlement.value.effectiveLevel)
        BuildEntitlementOverride.setForcedPro(false)
        AccountGraph.republishEntitlement()
        assertEquals(PlanLevel.FREE, AccountGraph.entitlement.value.effectiveLevel)
    }

    private fun estadoFree() = EntitlementState(
        confirmed = ConfirmedEntitlement(state = "NONE", validUntilEpochMs = 123L, confirmedAtEpochMs = 456L),
        effectiveLevel = PlanLevel.FREE,
        checking = true,
        errorCode = "NETWORK",
    )
}
