package com.example.kpkn.data.account

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.example.kpkn.BuildConfig
import com.example.kpkn.data.sync.PersonalRecordCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

data class SubscriptionOffer(val basePlanId: String, val formattedPrice: String, val currency: String,
    val billingPeriod: String, val trialDays: Int, internal val product: ProductDetails, internal val token: String)

/** Play supplies prices and eligibility. Purchases remain pending until the server confirms them. */
class PlaySubscriptionRepository(context: Context, private val scope: CoroutineScope, private val entitlement: () -> FirebaseEntitlementRepository?) {
    private val _offers = MutableStateFlow<List<SubscriptionOffer>>(emptyList())
    val offers = _offers.asStateFlow()
    private val connection = Mutex()
    private val client = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()
        .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            val owner = entitlement()
            scope.launch(Dispatchers.IO) { runCatching { verify(purchases.orEmpty(), owner) } }
        } }.build()
    private suspend fun ready() = withTimeout(30_000) { withContext(Dispatchers.Main) { connection.withLock {
        if (!client.isReady) suspendCancellableCoroutine<Unit> { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) { if (continuation.isActive) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                    else continuation.resumeWith(Result.failure(AccountServiceException("PLAY_UNAVAILABLE")))
                } }
                override fun onBillingServiceDisconnected() { }
            })
        }
    } } }
    suspend fun queryOffers() {
        ready()
        val parameters = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build())).build()
        withTimeout(30_000) { withContext(Dispatchers.Main) { suspendCancellableCoroutine<Unit> { continuation ->
            client.queryProductDetailsAsync(parameters) { result, products ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) _offers.value = products.productDetailsList.flatMap { product ->
                    product.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
                        val phases = offer.pricingPhases.pricingPhaseList
                        val recurring = phases.lastOrNull { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING } ?: return@mapNotNull null
                        if (recurring.billingPeriod !in setOf("P1M", "P1Y")) return@mapNotNull null
                        val free = phases.firstOrNull { it.priceAmountMicros == 0L }
                        val trial = free?.let { runCatching { java.time.Period.parse(it.billingPeriod).days }.getOrDefault(0) } ?: 0
                        if (free != null && trial != 14) return@mapNotNull null
                        SubscriptionOffer(offer.basePlanId, recurring.formattedPrice, recurring.priceCurrencyCode, recurring.billingPeriod, trial, product, offer.offerToken)
                    }
                }
                if (continuation.isActive) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                    else continuation.resumeWith(Result.failure(AccountServiceException("PLAY_UNAVAILABLE")))
                }
            }
        } } }
    }
    fun purchase(activity: Activity, uid: String, offer: SubscriptionOffer): BillingResult {
        check(BuildConfig.SALES_APPROVED)
        check(ProfileStorage.current().uid == uid && entitlement() != null)
        val details = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.product).setOfferToken(offer.token).build()
        return client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(details))
            .setObfuscatedAccountId(PersonalRecordCodec.sha256(uid.toByteArray())).build())
    }
    suspend fun restore() {
        val owner = entitlement() ?: throw AccountServiceException("UNAUTHENTICATED")
        ready()
        val purchases = withTimeout(30_000) { withContext(Dispatchers.Main) { suspendCancellableCoroutine<List<Purchase>> { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
                if (continuation.isActive) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(purchases)
                    else continuation.resumeWith(Result.failure(AccountServiceException("PLAY_UNAVAILABLE")))
                }
            }
        } } }
        withContext(Dispatchers.IO) { verify(purchases, owner); owner.refresh() }
    }
    private suspend fun verify(purchases: List<Purchase>, owner: FirebaseEntitlementRepository?) {
        requireNotNull(owner) { "Account required" }
        purchases.filter { PRODUCT in it.products }.forEach { purchase ->
            // Capture the original account before dispatch. A delayed callback never grants another UID.
            owner.verifyPurchase(purchase.purchaseToken)
    } }
    fun close() { client.endConnection() }
    companion object { const val PRODUCT = "kpkn_pro" }
}
