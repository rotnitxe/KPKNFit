package com.example.kpkn.data.account

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.kpkn.domain.entitlements.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Only authenticated server results can create a cache; portable backups never import it. */
class FirebaseEntitlementRepository(private val context: Context, private val uid: String, private val transport: AccountTransport) : EntitlementRepository {
    @Serializable private data class Receipt(val ownerUid: String, val confirmed: ConfirmedEntitlement, val elapsedMs: Long, val boot: Int, val serverEpochMs: Long)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(EntitlementState())
    override val state = _state.asStateFlow()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val preferences by lazy { context.getSharedPreferences("kpkn_entitlement", 0) }
    private var receipt: Receipt? = null
    suspend fun load() = withContext(Dispatchers.IO) {
        receipt = runCatching {
            val encoded = preferences.getString("receipt", null) ?: return@runCatching null
            val signature = preferences.getString("signature", null) ?: return@runCatching null
            require(encoded.toByteArray().size < 4096 && java.security.MessageDigest.isEqual(signature.toByteArray(), sign(encoded).toByteArray()))
            json.decodeFromString(Receipt.serializer(), encoded).also { require(it.ownerUid == uid) }
        }.getOrNull()
        updateClock()
    }
    suspend fun updateClock() {
        val value = receipt
        val now = if (value != null && value.boot == boot() && SystemClock.elapsedRealtime() >= value.elapsedMs)
            value.serverEpochMs + SystemClock.elapsedRealtime() - value.elapsedMs else null
        withContext(Dispatchers.Main) { _state.value = _state.value.copy(confirmed = value?.confirmed ?: ConfirmedEntitlement(), effectiveLevel = value?.let { EntitlementPolicy.level(it.confirmed, now) } ?: PlanLevel.FREE) }
    }
    override suspend fun refresh() = verify("readEntitlement", JsonObject(emptyMap()))
    override suspend fun verifyPurchase(purchaseToken: String) = verify("validatePlayPurchase", buildJsonObject { put("purchaseToken", purchaseToken) })
    private suspend fun verify(method: String, data: JsonObject) = withContext(Dispatchers.IO) { mutex.withLock {
        withContext(Dispatchers.Main) { _state.value = _state.value.copy(checking = true, errorCode = null) }
        try {
            val response = transport.call(uid, method, data)
            val confirmed = json.decodeFromJsonElement(ConfirmedEntitlement.serializer(), response)
            require(confirmed.confirmedAtEpochMs > 0 && confirmed.validUntilEpochMs >= 0)
            val value = Receipt(uid, confirmed, SystemClock.elapsedRealtime(), boot(), response.getValue("serverTimeEpochMs").jsonPrimitive.long)
            val encoded = json.encodeToString(Receipt.serializer(), value)
            check(preferences.edit().putString("receipt", encoded).putString("signature", sign(encoded)).commit())
            receipt = value
        } catch (error: Exception) {
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(errorCode = (error as? AccountServiceException)?.code ?: "UNAVAILABLE") }
            throw error
        } finally {
            updateClock(); withContext(Dispatchers.Main) { _state.value = _state.value.copy(checking = false) }
        }
    } }
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun sign(value: String): String = synchronized(FirebaseEntitlementRepository::class.java) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "kpkn-entitlement-cache-v1"
        val key = (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).setDigests(KeyProperties.DIGEST_SHA256).build()); generateKey()
        }
        Mac.getInstance("HmacSHA256").run { init(key); doFinal(value.toByteArray()).joinToString("") { "%02x".format(it) } }
    }
}
