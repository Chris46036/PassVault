package com.passvault.app.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast

object ClipboardUtil {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    /** Copia marcando el contenido como sensible y lo borra a los N segundos. */
    fun copySensitive(context: Context, label: String, text: String, clearAfterSeconds: Int) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= 24) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        cm.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
        }

        pendingClear?.let { handler.removeCallbacks(it) }
        if (clearAfterSeconds > 0) {
            val clear = Runnable {
                try {
                    val current = cm.primaryClip?.getItemAt(0)?.text?.toString()
                    if (current == text) {
                        if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                        else cm.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } catch (_: Exception) {
                }
            }
            pendingClear = clear
            handler.postDelayed(clear, clearAfterSeconds * 1000L)
        }
    }
}
