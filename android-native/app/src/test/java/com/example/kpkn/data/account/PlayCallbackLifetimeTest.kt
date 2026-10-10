package com.example.kpkn.data.account

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.LocalSubscriptionFixture
import com.android.billingclient.api.QueryProductDetailsResult
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class PlayCallbackLifetimeTest {
    private lateinit var scope: CoroutineScope
    private lateinit var context: Context
    private lateinit var sdk: DeferredBillingClient
    private var ownerReads = 0
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        ProfileStorage.initialize(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    private fun actor() = PlaySubscriptionRepository(context, scope, { ownerReads++; null }) { _, listener ->
        DeferredBillingClient(listener).also { sdk = it }
    }
    @After fun cleanup(): Unit = runBlocking {
        AccountGraph.stop()
        scope.coroutineContext[Job]!!.cancelAndJoin()
        com.example.kpkn.data.backup.DataSessionWriters.closeAdmission()
        com.example.kpkn.data.backup.DataSessionWriters.awaitStopped()
        com.example.kpkn.data.backup.DataSessionWriters.reopenAdmission()
        Dispatchers.resetMain()
    }
    @Test fun graphStop(): Unit = runBlocking {
        val billing = AccountGraph.subscriptions { actor() }
        AccountGraph.stop()
        assertTrue("Maintenance must close the previous SDK connection", sdk.ended)
        sdk.deliverPurchase()
        assertEquals("An old callback cannot resolve the next profile's entitlement", 0, ownerReads)
    }
    @Test fun latePurchase(): Unit = runBlocking {
        val billing = actor()
        billing.close()
        sdk.deliverPurchase()
        assertEquals("An already queued callback must stop before looking up a new owner", 0, ownerReads)
    }
    @Test fun lateOffers(): Unit = runBlocking {
        val billing = actor()
        val query = scope.async { billing.queryOffers() }
        withTimeout(10_000) { while (sdk.products == null) delay(5) }
        billing.close()
        sdk.products.onProductDetailsResponse(DeferredBillingClient.ok(),
            QueryProductDetailsResult.create(listOf(LocalSubscriptionFixture.product()), emptyList()))
        withTimeout(10_000) { query.join() }
        assertTrue("A late response must not republish offers on a closed graph", billing.offers.value.isEmpty())
    }
}
