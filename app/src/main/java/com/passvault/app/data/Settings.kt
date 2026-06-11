package com.passvault.app.data

import android.content.Context

object Settings {
    private const val PREFS = "settings"
    private const val KEY_AUTOLOCK = "autolock_seconds"
    private const val KEY_CLIPBOARD = "clipboard_clear_seconds"

    /** Segundos de inactividad antes de bloquear (0 = inmediato, -1 = nunca). */
    fun autoLockSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_AUTOLOCK, 60)

    fun setAutoLockSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_AUTOLOCK, seconds).apply()
    }

    /** Segundos hasta limpiar el portapapeles (0 = nunca). */
    fun clipboardClearSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CLIPBOARD, 30)

    fun setClipboardClearSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CLIPBOARD, seconds).apply()
    }
}
