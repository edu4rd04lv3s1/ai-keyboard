package com.aikeyboard.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tamanho do teclado (multiplicador aplicado às alturas das teclas e paddings).
 */
enum class KeyboardSize(val scale: Float, val labelRes: Int) {
    SMALL(0.85f, com.aikeyboard.app.R.string.size_small),
    MEDIUM(1.0f, com.aikeyboard.app.R.string.size_medium),
    LARGE(1.15f, com.aikeyboard.app.R.string.size_large),
    XLARGE(1.30f, com.aikeyboard.app.R.string.size_xlarge);

    companion object {
        fun fromName(name: String?): KeyboardSize =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * Aparência do teclado. SYSTEM segue o tema claro/escuro do aparelho.
 */
enum class KeyboardTheme(val labelRes: Int) {
    SYSTEM(com.aikeyboard.app.R.string.theme_system),
    DARK(com.aikeyboard.app.R.string.theme_dark),
    LIGHT(com.aikeyboard.app.R.string.theme_light);

    companion object {
        fun fromName(name: String?): KeyboardTheme =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Nível de agressividade da autocorreção aplicada ao fechar a palavra (espaço).
 * O usuário escolhe nos ajustes. `labelRes`/`descRes` alimentam a UI; o mapa de
 * thresholds por nível vive no TypingEngine (camada de digitação).
 */
enum class AutocorrectLevel(val labelRes: Int, val descRes: Int) {
    OFF(com.aikeyboard.app.R.string.autocorrect_off, com.aikeyboard.app.R.string.autocorrect_off_desc),
    LIGHT(com.aikeyboard.app.R.string.autocorrect_light, com.aikeyboard.app.R.string.autocorrect_light_desc),
    MEDIUM(com.aikeyboard.app.R.string.autocorrect_medium, com.aikeyboard.app.R.string.autocorrect_medium_desc),
    AGGRESSIVE(com.aikeyboard.app.R.string.autocorrect_strong, com.aikeyboard.app.R.string.autocorrect_strong_desc),
    ULTIMATE(com.aikeyboard.app.R.string.autocorrect_ultimate, com.aikeyboard.app.R.string.autocorrect_ultimate_desc);

    companion object {
        fun fromName(name: String?): AutocorrectLevel =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * Snapshot imutável das preferências do usuário.
 */
data class Settings(
    val keyboardSize: KeyboardSize = KeyboardSize.MEDIUM,
    val keyboardTheme: KeyboardTheme = KeyboardTheme.SYSTEM,
    /** Feedback tátil (vibração curta) a cada tecla. Padrão: ligado. */
    val hapticEnabled: Boolean = true,
    /** Som de clique do sistema a cada tecla. Padrão: desligado. */
    val soundEnabled: Boolean = false,
    /** Digitação por gesto (deslizar o dedo pelas letras). Padrão: ligado. */
    val gestureTypingEnabled: Boolean = true,
    /** Mostra barra de sugestões/correções após uma pausa curta na digitação. */
    val suggestionsEnabled: Boolean = true,
    /**
     * Nível da autocorreção automática ao dar espaço. Padrão: MEDIUM — corrige
     * typos claros sem "inventar" palavras fora do léxico (o que tornava ULTIMATE
     * impreciso, sobretudo em apps de chat) e é bem mais leve na UI thread.
     */
    val autocorrectLevel: AutocorrectLevel = AutocorrectLevel.MEDIUM
)

/**
 * Persistência simples de [Settings] em SharedPreferences (não criptografado, são preferências de UI).
 * Expõe um [StateFlow] que é atualizado a cada mudança.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Settings> = _flow

    val current: Settings get() = _flow.value

    private fun load(): Settings {
        runMigrationsOnce()
        return Settings(
            keyboardSize = KeyboardSize.fromName(prefs.getString(KEY_SIZE, null)),
            keyboardTheme = KeyboardTheme.fromName(prefs.getString(KEY_THEME, null)),
            hapticEnabled = prefs.getBoolean(KEY_HAPTIC, true),
            soundEnabled = prefs.getBoolean(KEY_SOUND, false),
            gestureTypingEnabled = prefs.getBoolean(KEY_GESTURE, true),
            suggestionsEnabled = prefs.getBoolean(KEY_SUGGESTIONS, true),
            autocorrectLevel = AutocorrectLevel.fromName(prefs.getString(KEY_AUTOCORRECT_LEVEL, null))
        )
    }

    fun setKeyboardSize(size: KeyboardSize) {
        prefs.edit().putString(KEY_SIZE, size.name).apply()
        _flow.value = current.copy(keyboardSize = size)
    }

    fun setKeyboardTheme(theme: KeyboardTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _flow.value = current.copy(keyboardTheme = theme)
    }

    fun setHaptic(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
        _flow.value = current.copy(hapticEnabled = enabled)
    }

    fun setSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        _flow.value = current.copy(soundEnabled = enabled)
    }

    fun setGestureTyping(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURE, enabled).apply()
        _flow.value = current.copy(gestureTypingEnabled = enabled)
    }

    fun setSuggestionsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUGGESTIONS, enabled).apply()
        _flow.value = current.copy(suggestionsEnabled = enabled)
    }

    fun setAutocorrectLevel(level: AutocorrectLevel) {
        prefs.edit().putString(KEY_AUTOCORRECT_LEVEL, level.name).apply()
        _flow.value = current.copy(autocorrectLevel = level)
    }

    /**
     * Migrações idempotentes de preferências entre versões. Cada uma roda no
     * máximo uma vez (guardada por uma flag) e nunca sobrescreve uma escolha
     * posterior do usuário.
     */
    private fun runMigrationsOnce() {
        // Versões antigas, em nome de "baixa latência", forçaram a vibração
        // SEMPRE desligada — inclusive quebrando o switch da UI. A pipeline de
        // digitação hoje é assíncrona e a vibração usa HapticFeedbackConstants
        // .KEYBOARD_TAP (barata, fora da UI thread), então o feedback tátil
        // volta ligado por padrão. Depois disso, respeita a escolha manual.
        if (!prefs.getBoolean(KEY_HAPTIC_RESTORED, false)) {
            prefs.edit()
                .putBoolean(KEY_HAPTIC, true)
                .putBoolean(KEY_HAPTIC_RESTORED, true)
                .apply()
        }
        // A barra de sugestões deixou de ser risco de latência (Flow +
        // collectLatest + debounce); liga de volta UMA vez.
        if (!prefs.getBoolean(KEY_SUGGESTIONS_ON_DEFAULT_APPLIED, false)) {
            prefs.edit()
                .putBoolean(KEY_SUGGESTIONS, true)
                .putBoolean(KEY_SUGGESTIONS_ON_DEFAULT_APPLIED, true)
                .apply()
        }
        // Autocorreção "ULTIMATE" (modelo de teclado): promove UMA vez quem
        // estava num nível anterior; depois respeita a escolha manual.
        if (!prefs.getBoolean(KEY_ULTIMATE_DEFAULT_APPLIED, false)) {
            prefs.edit()
                .putString(KEY_AUTOCORRECT_LEVEL, AutocorrectLevel.ULTIMATE.name)
                .putBoolean(KEY_ULTIMATE_DEFAULT_APPLIED, true)
                .apply()
        }
        // CORREÇÃO DE PRECISÃO: o ULTIMATE forçado acima "inventava" palavras
        // fora do léxico (impreciso em chat) e travava o espaço (cálculo pesado
        // na UI). Rebaixa UMA vez para MEDIUM — corrige typo claro sem destruir
        // o que o usuário escreveu. Depois respeita a escolha manual.
        if (!prefs.getBoolean(KEY_BALANCED_DEFAULT_APPLIED, false)) {
            prefs.edit()
                .putString(KEY_AUTOCORRECT_LEVEL, AutocorrectLevel.MEDIUM.name)
                .putBoolean(KEY_BALANCED_DEFAULT_APPLIED, true)
                .apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "ai_keyboard_settings"
        private const val KEY_SIZE = "keyboard_size"
        private const val KEY_THEME = "keyboard_theme"
        private const val KEY_AUTOCORRECT_LEVEL = "autocorrect_level"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_GESTURE = "gesture_typing_enabled"
        private const val KEY_SUGGESTIONS = "suggestions_enabled"
        private const val KEY_HAPTIC_RESTORED = "haptic_restored_v2"
        private const val KEY_SUGGESTIONS_ON_DEFAULT_APPLIED = "suggestions_on_default_applied"
        private const val KEY_ULTIMATE_DEFAULT_APPLIED = "ultimate_autocorrect_applied"
        private const val KEY_BALANCED_DEFAULT_APPLIED = "balanced_autocorrect_applied"
    }
}
