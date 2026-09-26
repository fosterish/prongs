package net.fosterish.prongs

import android.content.Context

/**
 * Everything the app persists, in one place. These keys and encodings are on disk on people's
 * phones, so the theme ordinal means [ThemeMode] entries may only be appended to.
 */
class TunerPreferences(context: Context) {

    companion object {
        const val DEFAULT_OCTAVE = 4
        const val DEFAULT_REFERENCE_HZ = 440

        private const val NAME = "tuner"
        private const val KEY_TARGETS = "targets"
        private const val KEY_OCTAVE = "octave"
        private const val KEY_REFERENCE = "reference"
        private const val KEY_THEME = "theme"
    }

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** MIDI note numbers the tuner listens for. Empty means chromatic. */
    var targets: Set<Int>
        get() = prefs.getStringSet(KEY_TARGETS, emptySet())!!
            .mapNotNullTo(mutableSetOf(), String::toIntOrNull)
        set(value) = prefs.edit().putStringSet(KEY_TARGETS, value.map(Int::toString).toSet()).apply()

    /** Octave a keyboard tap applies to. */
    var activeOctave: Int
        get() = prefs.getInt(KEY_OCTAVE, DEFAULT_OCTAVE)
        set(value) = prefs.edit().putInt(KEY_OCTAVE, value).apply()

    var referenceHz: Int
        get() = prefs.getInt(KEY_REFERENCE, DEFAULT_REFERENCE_HZ)
        set(value) = prefs.edit().putInt(KEY_REFERENCE, value).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.entries.getOrNull(prefs.getInt(KEY_THEME, 0)) ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putInt(KEY_THEME, value.ordinal).apply()
}
