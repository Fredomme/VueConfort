package fr.vueconfort.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import fr.vueconfort.app.navigation.VueConfortApp

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            VueConfortApp(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
