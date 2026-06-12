package com.passvault.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Auto-respaldo a una carpeta elegida por el usuario (SAF). El archivo va
 * cifrado igual que la bóveda: la carpeta puede ser una SD o una carpeta
 * sincronizada con Drive sin comprometer el modelo zero-knowledge.
 * Conserva los últimos 7 días por bóveda.
 */
object AutoBackup {

    private const val KEEP_FILES = 7

    /** Llamado tras cada guardado de la bóveda; falla en silencio (mejor esfuerzo). */
    fun onVaultSaved(context: Context, sealedBytes: ByteArray) {
        val uriStr = Settings.autoBackupUri(context) ?: return
        try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return
            if (!tree.canWrite()) return
            val vaultId = Vaults.activeId(context)
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val name = "passvault-$vaultId-$date.pvlt"

            tree.findFile(name)?.delete()
            val file = tree.createFile("application/octet-stream", name) ?: return
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(sealedBytes) }

            prune(tree, vaultId)
        } catch (_: Exception) {
        }
    }

    private fun prune(tree: DocumentFile, vaultId: String) {
        val prefix = "passvault-$vaultId-"
        val backups = tree.listFiles()
            .filter { (it.name ?: "").startsWith(prefix) && (it.name ?: "").endsWith(".pvlt") }
            .sortedByDescending { it.name }
        backups.drop(KEEP_FILES).forEach { it.delete() }
    }
}
