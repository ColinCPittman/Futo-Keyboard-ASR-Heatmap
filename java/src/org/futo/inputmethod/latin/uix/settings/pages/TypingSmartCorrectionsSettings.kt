package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.ContextModels
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.TYPING_FIX_CHIP_ON_RIGHT
import org.futo.inputmethod.latin.uix.TYPING_SMART_CORRECTIONS
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.voiceinput.shared.modelpackage.ModelPackages

/** Whether a Smart corrections model is there for typing corrections to use. */
@Composable
private fun typingModelAvailable(): Boolean {
    val context = LocalContext.current
    val changes by ModelPackages.changes.collectAsState()
    return remember(changes) { ContextModels.deviceSupported && ContextModels.installed(context).isNotEmpty() }
}

/** The Text prediction row that opens [TypingSmartCorrectionsMenu]. */
val TypingSmartCorrectionsNavItem = UserSetting(
    name = R.string.typing_smart_corrections,
    subtitle = R.string.typing_smart_corrections_nav_subtitle,
    searchTags = R.string.typing_smart_corrections_tags,
) {
    val navController = LocalNavController.current
    val on = useDataStoreValue(TYPING_SMART_CORRECTIONS)
    SettingItem(
        title = stringResource(R.string.typing_smart_corrections),
        subtitle = if (!typingModelAvailable()) stringResource(R.string.voice_input_settings_context_no_model)
            else stringResource(
                if (on) R.string.smart_corrections_state_on else R.string.smart_corrections_state_off,
                stringResource(R.string.typing_smart_corrections_nav_subtitle)
            ),
        onClick = { navController?.navigate(TypingSmartCorrectionsMenu.navPath) }
    ) {
        Icon(Icons.Default.ArrowForward, contentDescription = null)
    }
}

val TypingSmartCorrectionsMenu = UserSettingsMenu(
    title = R.string.typing_smart_corrections,
    navPath = "typingSmartCorrections", registerNavPath = true,
    settings = listOf(
        UserSetting(name = R.string.typing_smart_corrections_enable, subtitle = R.string.typing_smart_corrections_subtitle) {
            val available = typingModelAvailable()
            val text = stringResource(if (available) R.string.typing_smart_corrections_subtitle else R.string.voice_input_settings_context_no_model)
            SettingToggleDataStore(
                title = stringResource(R.string.typing_smart_corrections_enable),
                subtitle = text,
                disabledSubtitle = text,
                setting = TYPING_SMART_CORRECTIONS,
                disabled = !available,
            )
        },
        UserSetting(name = R.string.typing_fix_chip_on_right, subtitle = R.string.typing_fix_chip_on_right_subtitle) {
            SettingToggleDataStore(
                title = stringResource(R.string.typing_fix_chip_on_right),
                subtitle = stringResource(R.string.typing_fix_chip_on_right_subtitle),
                disabledSubtitle = stringResource(R.string.typing_fix_chip_on_left_subtitle),
                setting = TYPING_FIX_CHIP_ON_RIGHT,
                disabled = !useDataStoreValue(TYPING_SMART_CORRECTIONS) || !typingModelAvailable(),
            )
        },
        TypingPairsNavItem,
    )
)
