package com.example.kpkn.data.account

import com.example.kpkn.data.backup.BoundedInput
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

class AccountServiceException(val code: String) : Exception(code)

/** An immutable identity token prevents a delayed A request from being sent using B's SDK identity. */
interface AccountCalls {
    suspend fun call(uid: String, method: String, data: JsonObject = JsonObject(emptyMap()), freshAuthentication: Boolean = false): JsonObject
}
class AccountTransport(private val session: FirebaseSession) : AccountCalls {
    override suspend fun call(uid: String, method: String, data: JsonObject, freshAuthentication: Boolean): JsonObject = withContext(Dispatchers.IO) {
        require(method.matches(Regex("[A-Za-z][A-Za-z0-9]{1,64}")))
        val user = session.auth.currentUser?.takeIf { it.uid == uid } ?: throw AccountServiceException("UNAUTHENTICATED")
        val token = user.getIdToken(freshAuthentication).awaitResult().token ?: throw AccountServiceException("UNAUTHENTICATED")
        val appCheck = if (session.local) null else FirebaseAppCheck.getInstance(session.app).getAppCheckToken(false).awaitResult().token
        val encoded = buildJsonObject { put("data", data) }.toString().toByteArray(Charsets.UTF_8)
        require(encoded.size <= 4 * 1024 * 1024) { "Request is too large" }
        val connection = URL(session.functionsBase + method).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            appCheck?.let { connection.setRequestProperty("X-Firebase-AppCheck", it) }
            connection.setFixedLengthStreamingMode(encoded.size)
            connection.outputStream.use { it.write(encoded) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { BoundedInput.bytes(it, 5L * 1024 * 1024).toString(Charsets.UTF_8) }
                ?: throw AccountServiceException("UNAVAILABLE")
            val envelope = Json.parseToJsonElement(response).jsonObject
            envelope["error"]?.jsonObject?.let { error ->
                // User text, server messages and request URLs are intentionally not forwarded to diagnostics/UI.
                throw AccountServiceException(error["status"]?.jsonPrimitive?.content ?: "UNAVAILABLE")
            }
            (envelope["result"] ?: envelope["data"])?.jsonObject ?: throw AccountServiceException("INVALID_RESPONSE")
        } finally { connection.disconnect() }
    }
}
