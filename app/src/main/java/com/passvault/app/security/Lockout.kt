package com.passvault.app.security

import android.content.Context
import com.passvault.app.data.Vaults
import java.io.File

/**
 * Freno anti fuerza bruta del desbloqueo: retrasos crecientes tras fallos
 * y, si el usuario lo activa, borrado de la bóveda tras N intentos.
 */
object Lockout {

    private const val PREFS = "lockout"

    /** Intentos fallidos a partir de los cuales se aplica retraso. */
    private const val FREE_ATTEMPTS = 3

    private fun key(context: Context, name: String) =
        "${name}_${Vaults.activeId(context)}"

    fun failedAttempts(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(key(context, "fails"), 0)

    /** Milisegundos que faltan para poder intentarlo de nuevo (0 = ya). */
    fun remainingCooldownMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fails = prefs.getInt(key(context, "fails"), 0)
        if (fails < FREE_ATTEMPTS) return 0
        val lastFail = prefs.getLong(key(context, "last_fail"), 0)
        val cooldown = when {
            fails < 5 -> 5_000L
            fails < 7 -> 30_000L
            fails < 10 -> 60_000L
            else -> 300_000L
        }
        return (lastFail + cooldown - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /**
     * Registra un fallo. Devuelve true si se alcanzó el límite de borrado
     * y la bóveda activa fue eliminada.
     */
    fun recordFailure(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fails = prefs.getInt(key(context, "fails"), 0) + 1
        prefs.edit()
            .putInt(key(context, "fails"), fails)
            .putLong(key(context, "last_fail"), System.currentTimeMillis())
            .apply()

        val wipeAfter = wipeAfterAttempts(context)
        if (wipeAfter in 1..fails) {
            wipeActiveVault(context)
            return true
        }
        return false
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(context, "fails"))
            .remove(key(context, "last_fail"))
            .apply()
    }

    /** 0 = desactivado. */
    fun wipeAfterAttempts(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("wipe_after", 0)

    fun setWipeAfterAttempts(context: Context, attempts: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("wipe_after", attempts).apply()
    }

    private fun wipeActiveVault(context: Context) {
        val name = Vaults.fileName(Vaults.activeId(context))
        listOf(name, "$name.bak1", "$name.bak2", "$name.tmp").forEach {
            File(context.filesDir, it).delete()
        }
        BiometricHelper.disable(context)
        reset(context)
    }
}
