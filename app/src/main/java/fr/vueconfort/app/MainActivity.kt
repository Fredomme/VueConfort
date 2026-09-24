package fr.vueconfort.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import fr.vueconfort.app.navigation.VueConfortApp
import fr.vueconfort.app.prescription.LocalDocumentReader
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            try {
                LocalDocumentReader.clearAbandonedTemporaryCopies(applicationContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The document reader retries cleanup and surfaces a readable error before importing.
            }
        }

        setContent {
            VueConfortApp(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
