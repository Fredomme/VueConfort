package fr.vueconfort.app.mediaprojection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MediaProjectionConsentActivity : Activity() {
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val padding = (24 * resources.displayMetrics.density).toInt()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(context).apply {
                text = "Loupe VueConfort fluide"
                textSize = 26f
            })
            addView(TextView(context).apply {
                text = "Pour créer une loupe personnalisée et fluide, VueConfort doit recevoir temporairement l’image finale affichée par Android. Cette image sert uniquement à produire la zone agrandie visible à l’écran.\n\nAucune image n’est enregistrée, transmise, analysée par un serveur ou utilisée pour reconnaître le contenu. La session reste visible grâce à une notification permanente et peut être arrêtée à tout moment.\n\nLes contenus protégés par Android ne sont pas capturés.\n\nFlux visuel temporaire traité localement."
                textSize = 18f
                setPadding(0, padding, 0, padding)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(Button(context).apply {
                text = "Continuer vers l’autorisation Android"
                setOnClickListener { startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE) }
            })
            addView(Button(context).apply {
                text = "Annuler"
                setOnClickListener { finish() }
            })
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, MediaProjectionMagnifierService::class.java)
                    .setAction(MediaProjectionMagnifierService.ACTION_START)
                    .putExtra(MediaProjectionMagnifierService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(MediaProjectionMagnifierService.EXTRA_RESULT_DATA, data)
            )
        }
        finish()
    }

    companion object { private const val REQUEST_CAPTURE = 4801 }
}
