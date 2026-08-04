package fr.vueconfort.app.core

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fr.vueconfort.app.R
import fr.vueconfort.app.magnifier.ScreenMagnifierService

class VueConfortTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (VueConfortCoreState.serviceActive) {
            ScreenMagnifierService.handleExternalAction(ScreenMagnifierService.ACTION_TOGGLE)
        } else {
            openAccessibilitySettings()
        }
        refresh()
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (VueConfortCoreState.serviceActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(if (VueConfortCoreState.serviceActive) R.string.active else R.string.inactive)
            }
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
