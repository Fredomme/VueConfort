package fr.vueconfort.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import fr.vueconfort.app.magnifier.ScreenMagnifierService

object VueConfortCoreState {
    const val TAG = "VueConfortCore"
    @Volatile var serviceActive = false
    @Volatile var overlayActive = false
    @Volatile var magnificationActive = false
    @Volatile var extractionAvailable = false
    @Volatile var readerActive = false
    @Volatile var automaticProfilesActive = false

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any {
            it.contains(context.packageName) && it.contains("ScreenMagnifierService")
        }
    }

    fun log(area: String, message: String) {
        Log.i(TAG, "$area: $message")
    }

    fun error(area: String, throwable: Throwable) {
        Log.e(TAG, "$area: ${throwable.javaClass.simpleName}")
    }
}

class CoreActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ScreenMagnifierService.handleExternalAction(intent.action.orEmpty())
    }
}
