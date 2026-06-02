package com.aikeyboard.app

import android.app.Application
import com.aikeyboard.app.ai.GeminiClient
import com.aikeyboard.app.ai.GroqClient
import com.aikeyboard.app.data.ApiKeyStore
import com.aikeyboard.app.data.PersonalizationStore
import com.aikeyboard.app.data.SettingsStore
import com.aikeyboard.app.data.UserDictionaryStore

/**
 * Application class. Inicializa singletons usados pelo IME e pela MainActivity.
 */
class KeyboardApp : Application() {

    val apiKeyStore: ApiKeyStore by lazy { ApiKeyStore(this) }
    val groqClient: GroqClient by lazy { GroqClient(apiKeyStore) }
    val geminiClient: GeminiClient by lazy { GeminiClient(apiKeyStore) }
    val geminiSecondaryClient: GeminiClient by lazy {
        GeminiClient(apiKeyStore, apiKeyProvider = { apiKeyStore.getGeminiSecondaryApiKey() })
    }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val userDictionaryStore: UserDictionaryStore by lazy { UserDictionaryStore(this) }
    val personalizationStore: PersonalizationStore by lazy { PersonalizationStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile private var instance: KeyboardApp? = null
        fun get(): KeyboardApp =
            instance ?: error("KeyboardApp não inicializado")
    }
}
