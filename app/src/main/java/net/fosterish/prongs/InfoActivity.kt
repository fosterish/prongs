package net.fosterish.prongs

import android.os.Bundle
import android.widget.TextView

/** Name, version and source link. The layout linkifies the URL, so that needs no wiring. */
class InfoActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.info)
        findViewById<TextView>(R.id.version).text =
            getString(R.string.version_format, BuildConfig.VERSION_NAME)
    }
}
