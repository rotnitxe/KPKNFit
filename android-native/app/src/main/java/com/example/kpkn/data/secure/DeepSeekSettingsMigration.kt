package com.example.kpkn.data.secure

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One-way migration from the legacy serialized Settings API keys. */
object DeepSeekSettingsMigration {
    suspend fun migrate(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        runCatching {
            val database = KpknDatabase.getInstance(appContext)
            val entity = database.settingsDao().get() ?: return@runCatching
            val settings = entity.toSettings()
            val legacyDeepSeek = settings.apiKeys.deepseek?.trim().orEmpty()
            val secureKey = DeepSeekCredentialStore.read(appContext)
            if (secureKey.isNullOrBlank() && legacyDeepSeek.isNotBlank()) {
                DeepSeekCredentialStore.write(appContext, legacyDeepSeek)
            }
            if (settings.apiProvider.name != "DEEPSEEK" || settings.apiKeys != com.example.kpkn.data.models.ApiKeys()) {
                database.settingsDao().upsert(
                    settings.copy(
                        apiProvider = com.example.kpkn.data.models.ApiProvider.DEEPSEEK,
                        apiKeys = com.example.kpkn.data.models.ApiKeys(),
                    ).toEntity(),
                )
            }
        }
    }
}


