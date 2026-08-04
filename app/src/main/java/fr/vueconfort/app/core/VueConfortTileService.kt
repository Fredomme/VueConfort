package fr.vueconfort.app.core

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fr.vueconfort.app.MainActivity
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
            startActivityAndCollapse(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        refresh()
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (VueConfortCoreState.serviceActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            subtitle = getString(if (VueConfortCoreState.serviceActive) R.string.active else R.string.inactive)
            updateTile()
        }
    }
}
