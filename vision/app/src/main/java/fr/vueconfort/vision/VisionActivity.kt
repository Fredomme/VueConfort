package fr.vueconfort.vision

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Entry point for the independent, experimental image-adjustment loupe. */
class VisionActivity : Activity() {
    private lateinit var permissionButton: Button
    private lateinit var message: TextView
    private var consentPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consentPending = savedInstanceState?.getBoolean(STATE_CONSENT_PENDING) ?: false
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        scroll.addView(content, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()

        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_vision)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(16) })
        content.addView(label(getString(R.string.app_name), 30f, PURPLE, true))
        content.addView(label(getString(R.string.vision_intro), 18f).apply {
            setPadding(0, dp(12), 0, dp(20))
        })
        content.addView(label(getString(R.string.vision_controls), 17f).apply {
            setPadding(0, 0, 0, dp(12))
        })
        content.addView(label(getString(R.string.vision_one_loupe), 16f).apply {
            setPadding(0, 0, 0, dp(20))
        })

        permissionButton = button(getString(R.string.vision_permission), primary = false) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        content.addView(permissionButton)
        content.addView(button(getString(R.string.vision_start), primary = true) {
            requestProjection()
        })
        content.addView(button("Essai optique guidé", primary = false) {
            stopService(Intent(this, VisionService::class.java))
            OpticalSnapshot.clear()
            startActivity(Intent(this, OpticalActivity::class.java)
                .putExtra(EXTRA_TECHNICAL_EVIDENCE, intent.getBooleanExtra(EXTRA_TECHNICAL_EVIDENCE, false)))
        })
        content.addView(button(getString(R.string.vision_stop), primary = false) {
            stopService(Intent(this, VisionService::class.java))
            message.text = getString(R.string.vision_stopped)
        })

        message = label("", 16f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(0, dp(12), 0, dp(12))
        }
        content.addView(message)
        content.addView(label(getString(R.string.vision_experimental), 14f, MUTED).apply {
            setPadding(0, dp(8), 0, 0)
        })
    }

    override fun onResume() {
        super.onResume()
        if (::permissionButton.isInitialized) {
            val allowed = Settings.canDrawOverlays(this)
            permissionButton.isEnabled = !allowed
            permissionButton.text = getString(if (allowed)
                R.string.vision_permission_ready else R.string.vision_permission)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_CONSENT_PENDING, consentPending)
        super.onSaveInstanceState(outState)
    }

    private fun requestProjection() {
        if (consentPending) return
        if (!Settings.canDrawOverlays(this)) {
            message.text = getString(R.string.vision_permission_needed)
            return
        }
        stopService(Intent(this, VisionService::class.java))
        val manager = getSystemService(MediaProjectionManager::class.java)
        consentPending = true
        @Suppress("DEPRECATION")
        startActivityForResult(
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),
            REQUEST_CAPTURE
        )
    }

    @Deprecated("Android activity result callback retained for this native Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        consentPending = false
        if (resultCode != RESULT_OK || data == null) {
            message.text = getString(R.string.vision_cancelled)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            message.text = getString(R.string.vision_permission_needed)
            return
        }
        try {
            startForegroundService(Intent(this, VisionService::class.java).apply {
                putExtra(EXTRA_CONSENT, data)
                putExtra(EXTRA_TECHNICAL_EVIDENCE,
                    intent.getBooleanExtra(EXTRA_TECHNICAL_EVIDENCE, false))
            })
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            finish()
        } catch (_: RuntimeException) {
            message.text = getString(R.string.vision_start_failed)
        }
    }

    private fun label(text: String, size: Float, color: Int = TEXT, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

    private fun button(text: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 18f
        isAllCaps = false
        minHeight = dp(60)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(MUTED, if (primary) Color.WHITE else PURPLE)
        ))
        backgroundTintList = ColorStateList.valueOf(if (primary) PURPLE else PALE_PURPLE)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        const val EXTRA_CONSENT = "consent"
        const val EXTRA_TECHNICAL_EVIDENCE = "technical_evidence"
        private const val REQUEST_CAPTURE = 2412
        private const val STATE_CONSENT_PENDING = "consent_pending"
        private val BACKGROUND = Color.rgb(248, 246, 252)
        private val PURPLE = Color.rgb(101, 52, 177)
        private val PALE_PURPLE = Color.rgb(232, 221, 248)
        private val TEXT = Color.rgb(37, 30, 48)
        private val MUTED = Color.rgb(95, 87, 107)
    }
}
