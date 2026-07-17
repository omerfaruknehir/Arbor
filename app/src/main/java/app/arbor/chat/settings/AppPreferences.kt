package app.arbor.chat.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ColorPalette { ARBOR, SYSTEM, GRAPHITE }

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("arbor_app_settings", Context.MODE_PRIVATE)
    private val _amoled = MutableStateFlow(preferences.getBoolean(KEY_AMOLED, false))
    private val _palette = MutableStateFlow(
        runCatching { ColorPalette.valueOf(preferences.getString(KEY_PALETTE, null) ?: ColorPalette.ARBOR.name) }
            .getOrDefault(ColorPalette.ARBOR),
    )

    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()
    val palette: StateFlow<ColorPalette> = _palette.asStateFlow()

    fun setAmoled(enabled: Boolean) {
        _amoled.value = enabled
        preferences.edit { putBoolean(KEY_AMOLED, enabled) }
    }

    fun setPalette(value: ColorPalette) {
        _palette.value = value
        preferences.edit { putString(KEY_PALETTE, value.name) }
    }

    private companion object {
        const val KEY_AMOLED = "amoled_black"
        const val KEY_PALETTE = "color_palette"
    }
}
