package net.fosterish.prongs

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView

class MainActivity : ThemedActivity() {

    companion object {
        private const val REQUEST_MICROPHONE = 1

        /** Well above 1, so near-square windows like an unfolded foldable stay upright. */
        private const val WIDE_ASPECT = 1.5f

        /** A semitone either side of A440, covering baroque through sharp orchestral pitch. */
        private const val MIN_REFERENCE_HZ = 415
        private const val MAX_REFERENCE_HZ = 466

        private const val REPEAT_DELAY_MS = 70L
    }

    private lateinit var prefs: TunerPreferences
    private lateinit var meter: TuningMeterView
    private lateinit var keyboard: PianoKeyboardView
    private lateinit var octaveStrip: OctaveStripView
    private lateinit var modeLabel: TextView
    private lateinit var pitchValue: TextView
    private lateinit var permissionPanel: View

    private lateinit var engine: TunerEngine
    private val tone = ToneGenerator()
    private val handler = Handler(Looper.getMainLooper())

    private var targets: Set<Int> = emptySet()
    private var referenceHz = TunerPreferences.DEFAULT_REFERENCE_HZ
    private var soundingMidi: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wide = isWideWindow()
        setContentView(if (wide) R.layout.main_wide else R.layout.main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = TunerPreferences(this)
        meter = findViewById(R.id.meter)
        keyboard = findViewById(R.id.keyboard)
        octaveStrip = findViewById(R.id.octaveStrip)
        modeLabel = findViewById(R.id.modeLabel)
        pitchValue = findViewById(R.id.pitchValue)
        permissionPanel = findViewById(R.id.permissionPanel)

        engine = TunerEngine(applicationContext) { meter.update(it) }

        keyboard.isVertical = wide
        octaveStrip.isVertical = wide

        targets = prefs.targets
        val octave = prefs.activeOctave
        octaveStrip.activeOctave = octave
        keyboard.activeOctave = octave

        keyboard.onToggle = ::toggleTarget
        keyboard.onSoundTone = ::startTone
        keyboard.onStopTone = ::stopTone

        octaveStrip.onOctaveSelected = { selected ->
            octaveStrip.activeOctave = selected
            keyboard.activeOctave = selected
            prefs.activeOctave = selected
        }

        modeLabel.setOnClickListener { if (targets.isNotEmpty()) applyTargets(emptySet()) }
        findViewById<Button>(R.id.grantButton).setOnClickListener { requestMicrophone() }
        findViewById<ImageButton>(R.id.infoButton).setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java))
        }

        findViewById<Button>(R.id.pitchDown).autoRepeat { applyReference(referenceHz - 1) }
        findViewById<Button>(R.id.pitchUp).autoRepeat { applyReference(referenceHz + 1) }

        bindThemeButtons()
        applyTargets(targets)
        applyReference(prefs.referenceHz)
    }

    private fun isWideWindow(): Boolean {
        val config = resources.configuration
        if (config.screenHeightDp <= 0) return false
        return config.screenWidthDp.toFloat() / config.screenHeightDp >= WIDE_ASPECT
    }

    private fun bindThemeButtons() {
        val current = prefs.themeMode
        val buttons = mapOf(
            ThemeMode.LIGHT to findViewById<ImageButton>(R.id.themeLight),
            ThemeMode.DARK to findViewById<ImageButton>(R.id.themeDark),
            ThemeMode.SYSTEM to findViewById<ImageButton>(R.id.themeSystem),
        )
        buttons.forEach { (mode, button) ->
            val ink = if (mode == current) R.color.accent else R.color.text_disabled
            button.imageTintList = ColorStateList.valueOf(getColor(ink))
            button.setOnClickListener {
                if (mode != current) applyThemeMode(mode)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasMicrophonePermission()) {
            permissionPanel.visibility = View.GONE
            engine.start()
        } else {
            permissionPanel.visibility = View.VISIBLE
            requestMicrophone()
        }
    }

    override fun onPause() {
        super.onPause()
        stopTone()
        engine.stop()
    }

    /** Fires once on tap, then repeats for as long as the button stays held. */
    private fun View.autoRepeat(onStep: () -> Unit) {
        val repeat = object : Runnable {
            override fun run() {
                if (!isPressed) return
                onStep()
                handler.postDelayed(this, REPEAT_DELAY_MS)
            }
        }
        setOnClickListener { onStep() }
        setOnLongClickListener {
            handler.removeCallbacks(repeat)
            handler.post(repeat)
            true
        }
    }

    private fun applyReference(hz: Int) {
        referenceHz = hz.coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ)
        engine.a4Hz = referenceHz.toDouble()
        pitchValue.text = getString(R.string.reference_value, referenceHz)
        prefs.referenceHz = referenceHz
        soundingMidi?.let {
            tone.play(Notes.frequency(it, referenceHz.toDouble()))
            meter.pin(toneReading(it))
        }
    }

    private fun toggleTarget(midi: Int) {
        applyTargets(if (midi in targets) targets - midi else targets + midi)
    }

    private fun applyTargets(updated: Set<Int>) {
        targets = updated
        engine.targets = updated
        keyboard.selection = updated
        octaveStrip.selection = updated
        modeLabel.text = if (updated.isEmpty()) getString(R.string.mode_chromatic) else clearPrompt(updated.size)
        prefs.targets = updated
    }

    /** "4 targets - tap here to clear", with the tappable half picked out in white. */
    private fun clearPrompt(count: Int): CharSequence {
        val summary = resources.getQuantityString(R.plurals.mode_targets, count, count)
        val action = getString(R.string.clear_action)
        return SpannableStringBuilder("$summary \u00B7 $action").apply {
            setSpan(
                ForegroundColorSpan(getColor(R.color.text_primary)),
                length - action.length,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    /** The tone would dominate the microphone and peg the meter to itself, so listening pauses. */
    private fun startTone(midi: Int) {
        engine.stop()
        soundingMidi = midi
        tone.play(Notes.frequency(midi, referenceHz.toDouble()))
        keyboard.tonePlaying = true
        // Better than prompting for a note while the tuner is deliberately not listening.
        meter.pin(toneReading(midi))
    }

    private fun stopTone() {
        if (!keyboard.tonePlaying) return
        tone.stop()
        soundingMidi = null
        keyboard.tonePlaying = false
        meter.pin(null)
        if (hasMicrophonePermission() && hasWindowFocus()) engine.start()
    }

    /** The sounding tone dressed up as a dead-on reading. */
    private fun toneReading(midi: Int): TuningReading {
        val hz = Notes.frequency(midi, referenceHz.toDouble())
        return TuningReading(targetMidi = midi, targetHz = hz, detectedHz = hz, cents = 0.0)
    }

    private fun hasMicrophonePermission() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicrophone() =
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode != REQUEST_MICROPHONE) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        permissionPanel.visibility = if (granted) View.GONE else View.VISIBLE
        if (granted) engine.start()
    }
}
