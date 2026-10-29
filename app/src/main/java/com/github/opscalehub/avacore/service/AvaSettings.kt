package com.github.opscalehub.avacore.service

import android.content.Context

/** AvaCore's own user settings (shown on its settings screen, read by the service). */
object AvaSettings {
    private const val PREFS = "avacore_settings"
    private const val KEY_GOFTARI = "goftari_by_default"

    /** Read every app's Persian in spoken (Goftari) form, not just requests for the Goftari voice. */
    fun goftariByDefault(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_GOFTARI, false)

    fun setGoftariByDefault(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_GOFTARI, on).apply()
    }
}
