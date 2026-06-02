package com.aikeyboard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeyboard.app.data.AutocorrectLevel
import com.aikeyboard.app.data.KeyboardSize
import com.aikeyboard.app.data.KeyboardTheme
import com.aikeyboard.app.data.Settings
import com.aikeyboard.app.data.SettingsStore
import com.aikeyboard.app.data.UserDictionaryStore
import com.aikeyboard.app.ui.AIKeyboardTheme
import com.aikeyboard.app.ui.AppBackground
import com.aikeyboard.app.ui.AppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val state = MutableStateFlow(SetupState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIKeyboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    val app = applicationContext as KeyboardApp
                    SetupScreen(
                        stateFlow = state,
                        settingsStore = app.settingsStore,
                        userDictionaryStore = app.userDictionaryStore,
                        onOpenLanguageSettings = { openLanguageSettings() },
                        onPickInputMethod = { showInputMethodPicker() },
                        onSaveApiKey = { key ->
                            app.apiKeyStore.setApiKey(key)
                            refreshState()
                        },
                        onClearApiKey = {
                            app.apiKeyStore.clear()
                            refreshState()
                        },
                        onSaveGeminiKey = { key ->
                            app.apiKeyStore.setGeminiApiKey(key)
                            refreshState()
                        },
                        onClearGeminiKey = {
                            app.apiKeyStore.clearGemini()
                            refreshState()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val app = applicationContext as KeyboardApp
        state.value = SetupState(
            imeEnabled = isImeEnabled(),
            imeSelected = isImeSelected(),
            apiKeyPreview = app.apiKeyStore.maskedPreview(),
            geminiKeyPreview = app.apiKeyStore.maskedGeminiPreview()
        )
    }

    private fun openLanguageSettings() {
        startActivity(Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun showInputMethodPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val target = packageName + "/" +
            "com.aikeyboard.app.ime.AIKeyboardService"
        return imm.enabledInputMethodList.any { it.id == target }
    }

    private fun isImeSelected(): Boolean {
        val current = SystemSettings.Secure.getString(
            contentResolver,
            SystemSettings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        return current.startsWith(packageName)
    }
}

data class SetupState(
    val imeEnabled: Boolean = false,
    val imeSelected: Boolean = false,
    val apiKeyPreview: String? = null,
    val geminiKeyPreview: String? = null
)

@Composable
private fun SetupScreen(
    stateFlow: StateFlow<SetupState>,
    settingsStore: SettingsStore,
    userDictionaryStore: UserDictionaryStore,
    onOpenLanguageSettings: () -> Unit,
    onPickInputMethod: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit
) {
    val state by stateFlow.collectAsState()
    val settings by settingsStore.flow.collectAsState()
    val userWords by userDictionaryStore.flow.collectAsState()

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ProductHero(state)

            SectionLabel(stringResRemember(R.string.section_start))
            StartSection(
                state = state,
                onOpenLanguageSettings = onOpenLanguageSettings,
                onPickInputMethod = onPickInputMethod
            )

            SectionLabel(stringResRemember(R.string.section_ai))
            AiSection(
                state = state,
                onSaveApiKey = onSaveApiKey,
                onClearApiKey = onClearApiKey,
                onSaveGeminiKey = onSaveGeminiKey,
                onClearGeminiKey = onClearGeminiKey
            )

            SectionLabel(stringResRemember(R.string.section_keyboard))
            KeyboardSection(
                settings = settings,
                onSizeChange = { settingsStore.setKeyboardSize(it) },
                onThemeChange = { settingsStore.setKeyboardTheme(it) },
                onAutocorrectLevelChange = { settingsStore.setAutocorrectLevel(it) },
                onHapticToggle = { settingsStore.setHaptic(it) },
                onSoundToggle = { settingsStore.setSound(it) },
                onGestureToggle = { settingsStore.setGestureTyping(it) },
                onSuggestionsToggle = { settingsStore.setSuggestionsEnabled(it) }
            )

            SectionLabel(stringResRemember(R.string.section_personalization))
            UserDictionarySection(
                words = userWords,
                onAdd = { userDictionaryStore.add(it) },
                onRemove = { userDictionaryStore.remove(it) }
            )

            SectionLabel(stringResRemember(R.string.section_test))
            TestArea()

            PrivacyCard()
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ProductHero(state: SetupState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResRemember(R.string.app_name),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResRemember(R.string.hero_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        label = stringResRemember(
                            if (state.imeEnabled) R.string.hero_enabled else R.string.hero_disabled
                        ),
                        active = state.imeEnabled
                    )
                    StatusPill(
                        label = stringResRemember(
                            if (state.imeSelected) {
                                R.string.hero_selected
                            } else {
                                R.string.hero_not_selected
                            }
                        ),
                        active = state.imeSelected
                    )
                    StatusPill(
                        label = stringResRemember(
                            if (state.apiKeyPreview != null) {
                                R.string.hero_ai_ready
                            } else {
                                R.string.hero_ai_pending
                            }
                        ),
                        active = state.apiKeyPreview != null
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    val container = if (active) {
        AppColors.AccentSoft
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    }
    val content = if (active) {
        Color(0xFFE7E3FF)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .border(
                1.dp,
                if (active) AppColors.Accent.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                CircleShape
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) AppColors.Success else MaterialTheme.colorScheme.outline)
        )
        Text(label, color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StartSection(
    state: SetupState,
    onOpenLanguageSettings: () -> Unit,
    onPickInputMethod: () -> Unit
) {
    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SetupStepRow(
                number = 1,
                done = state.imeEnabled,
                title = stringResRemember(R.string.setup_step1_title),
                description = stringResRemember(R.string.setup_step1_desc),
                actionLabel = stringResRemember(
                    if (state.imeEnabled) {
                        R.string.setup_step1_done
                    } else {
                        R.string.setup_step1_action
                    }
                ),
                onAction = onOpenLanguageSettings
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            SetupStepRow(
                number = 2,
                done = state.imeSelected,
                title = stringResRemember(R.string.setup_step2_title),
                description = stringResRemember(R.string.setup_step2_desc),
                actionLabel = stringResRemember(
                    if (state.imeSelected) {
                        R.string.setup_step2_done
                    } else {
                        R.string.setup_step2_action
                    }
                ),
                onAction = onPickInputMethod
            )
        }
    }
}

@Composable
private fun SetupStepRow(
    number: Int,
    done: Boolean,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        StepBadge(number = number, done = done)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            if (done) {
                AssistChip(
                    onClick = onAction,
                    label = { Text(actionLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.Success
                        )
                    }
                )
            } else {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun StepBadge(number: Int, done: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                if (done) AppColors.AccentSoft else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (done) AppColors.Accent.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = AppColors.Success,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(number.toString(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AiSection(
    state: SetupState,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit
) {
    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoBanner(
                title = stringResRemember(R.string.ai_manual_title),
                body = stringResRemember(R.string.ai_manual_body)
            )
            ApiKeyEditor(
                title = stringResRemember(R.string.setup_step3_title),
                description = stringResRemember(R.string.setup_step3_desc),
                placeholder = stringResRemember(R.string.api_key_placeholder),
                preview = state.apiKeyPreview,
                onSave = onSaveApiKey,
                onClear = onClearApiKey
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ApiKeyEditor(
                title = stringResRemember(R.string.setup_step4_title),
                description = stringResRemember(R.string.setup_step4_desc),
                placeholder = stringResRemember(R.string.gemini_key_placeholder),
                preview = state.geminiKeyPreview,
                onSave = onSaveGeminiKey,
                onClear = onClearGeminiKey
            )
        }
    }
}

@Composable
private fun InfoBanner(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ApiKeyEditor(
    title: String,
    description: String,
    placeholder: String,
    preview: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var input by rememberSaveable(title) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(done = preview != null)
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        if (preview != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResWithArg(R.string.api_key_present, preview),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.trim() },
            singleLine = true,
            placeholder = { Text(placeholder) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        onSave(input)
                        input = ""
                        focusManager.clearFocus()
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResRemember(R.string.save_api_key)) }
            if (preview != null) {
                OutlinedButton(onClick = onClear) {
                    Text(stringResRemember(R.string.remove_action))
                }
            }
        }
    }
}

@Composable
private fun KeyboardSection(
    settings: Settings,
    onSizeChange: (KeyboardSize) -> Unit,
    onThemeChange: (KeyboardTheme) -> Unit,
    onAutocorrectLevelChange: (AutocorrectLevel) -> Unit,
    onHapticToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onGestureToggle: (Boolean) -> Unit,
    onSuggestionsToggle: (Boolean) -> Unit
) {
    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingGroup(
                icon = Icons.Outlined.Tune,
                title = stringResRemember(R.string.settings_size_title),
                description = stringResRemember(R.string.settings_size_desc)
            ) {
                SegmentedRow(
                    options = KeyboardSize.entries,
                    selected = settings.keyboardSize,
                    label = { stringResource(it.labelRes) },
                    onSelect = onSizeChange
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            SettingGroup(
                icon = Icons.Outlined.DarkMode,
                title = stringResRemember(R.string.settings_theme_title),
                description = stringResRemember(R.string.settings_theme_desc)
            ) {
                SegmentedRow(
                    options = KeyboardTheme.entries,
                    selected = settings.keyboardTheme,
                    label = { stringResource(it.labelRes) },
                    onSelect = onThemeChange
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            SettingGroup(
                icon = Icons.Outlined.AutoFixHigh,
                title = stringResRemember(R.string.settings_autocorrect_title),
                description = stringResRemember(R.string.settings_autocorrect_desc)
            ) {
                SegmentedRow(
                    options = AutocorrectLevel.entries,
                    selected = settings.autocorrectLevel,
                    label = { stringResource(it.labelRes) },
                    onSelect = onAutocorrectLevelChange
                )
                Text(
                    stringResRemember(settings.autocorrectLevel.descRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            ToggleRow(
                icon = Icons.Outlined.Vibration,
                title = stringResRemember(R.string.settings_haptic_title),
                description = stringResRemember(R.string.settings_haptic_desc),
                checked = settings.hapticEnabled,
                onCheckedChange = onHapticToggle
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            ToggleRow(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = stringResRemember(R.string.settings_sound_title),
                description = stringResRemember(R.string.settings_sound_desc),
                checked = settings.soundEnabled,
                onCheckedChange = onSoundToggle
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            ToggleRow(
                icon = Icons.Outlined.Gesture,
                title = stringResRemember(R.string.settings_gesture_title),
                description = stringResRemember(R.string.settings_gesture_desc),
                checked = settings.gestureTypingEnabled,
                onCheckedChange = onGestureToggle
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            ToggleRow(
                icon = Icons.Outlined.Lightbulb,
                title = stringResRemember(R.string.settings_suggestions_title),
                description = stringResRemember(R.string.settings_suggestions_desc),
                checked = settings.suggestionsEnabled,
                onCheckedChange = onSuggestionsToggle
            )
        }
    }
}

/** Cabeçalho (ícone + título + descrição) seguido de um controle. */
@Composable
private fun SettingGroup(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        content()
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        label(option),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun UserDictionarySection(
    words: Set<String>,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Boolean
) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResRemember(R.string.settings_userdict_title),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                stringResRemember(R.string.settings_userdict_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.trim() },
                    singleLine = true,
                    placeholder = { Text(stringResRemember(R.string.settings_userdict_placeholder)) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            onAdd(input)
                            input = ""
                            focusManager.clearFocus()
                        }
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                }
            }

            if (words.isEmpty()) {
                Text(
                    stringResRemember(R.string.settings_userdict_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    words.sorted().forEach { word ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(word, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemove(word) }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResRemember(R.string.remove_action),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestArea() {
    var value by rememberSaveable { mutableStateOf("") }
    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Keyboard, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResRemember(R.string.test_field_label),
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(stringResRemember(R.string.test_field_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp),
                maxLines = 4
            )
        }
    }
}

@Composable
private fun PrivacyCard() {
    PremiumCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResRemember(R.string.about_title),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResRemember(R.string.about_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun PremiumCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f))
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun StatusIcon(done: Boolean) {
    if (done) {
        Icon(Icons.Outlined.CheckCircle, null, tint = AppColors.Success)
    } else {
        Icon(
            Icons.Outlined.RadioButtonUnchecked,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun stringResRemember(resId: Int): String {
    val context = LocalContext.current
    return remember(resId) { context.getString(resId) }
}

@Composable
private fun stringResource(resId: Int): String = stringResRemember(resId)

@Composable
private fun stringResWithArg(resId: Int, arg: Any): String {
    val context = LocalContext.current
    return remember(resId, arg) { context.getString(resId, arg) }
}
