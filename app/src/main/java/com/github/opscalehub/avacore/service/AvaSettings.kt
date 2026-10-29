package com.github.opscalehub.avacore.service

import android.content.Context

/** AvaCore's own user settings (shown on its settings screen, read by the service). */
object AvaSettings {
    private const val PREFS = "avacore_settings"
    private const val KEY_GOFTARI = "goftari_by_default"

    /** Read Persian in spoken (Goftari) form — on unless the user turned it off. */
    fun goftariByDefault(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_GOFTARI, true)

    fun setGoftariByDefault(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_GOFTARI, on).apply()
    }
}
