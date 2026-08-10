package fr.vueconfort.app.custommagnifier

import android.content.Context

internal class MagnifierSettings(context: Context) {
    private val preferences = context.getSharedPreferences("custom_magnifier", Context.MODE_PRIVATE)

    var zoom: Float
        get() = MagnifierGeometry.clampZoom(preferences.getFloat("zoom", 2f))
        set(value) { preferences.edit().putFloat("zoom", MagnifierGeometry.clampZoom(value)).apply() }

    var consented: Boolean
        get() = preferences.getBoolean("consented", false)
        set(value) { preferences.edit().putBoolean("consented", value).apply() }
}
