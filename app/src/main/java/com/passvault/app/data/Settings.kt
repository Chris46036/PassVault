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

    /** URI (árbol SAF) de la carpeta de auto-respaldo, o null si está desactivado. */
    fun autoBackupUri(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("autobackup_uri", null)

    fun setAutoBackupUri(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("autobackup_uri", uri).apply()
    }

    /** Copiar el código 2FA al portapapeles al autorrellenar (activado por defecto). */
    fun totpAutoCopy(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("totp_autocopy", true)

    fun setTotpAutoCopy(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("totp_autocopy", enabled).apply()
    }
}
