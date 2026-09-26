package net.fosterish.prongs

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Applies the stored [ThemeMode] by claiming a night setting in the activity's configuration.
 *
 * Overriding in [attachBaseContext] makes every `getColor` resolve the right `values-night`
 * palette with no theme-attribute plumbing, but it is per-activity, hence the shared base.
 */
abstract class ThemedActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        val night = when (TunerPreferences(newBase).themeMode) {
            ThemeMode.SYSTEM -> {
                super.attachBaseContext(newBase)
                return
            }

            ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
        }
        val config = Configuration(newBase.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    /** The restart is what gives [attachBaseContext] a chance to apply [mode]. */
    protected fun applyThemeMode(mode: ThemeMode) {
        TunerPreferences(this).themeMode = mode
        recreate()
    }
}
