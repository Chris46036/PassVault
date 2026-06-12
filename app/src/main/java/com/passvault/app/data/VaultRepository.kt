package com.passvault.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.passvault.app.crypto.CryptoManager
import java.io.File
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Bóveda en memoria + persistencia cifrada en filesDir/vault.enc, con dos
 * copias locales rotativas (vault.enc.bak1/.bak2) por si el archivo principal
 * se corrompe. La clave derivada solo vive en memoria mientras está desbloqueada.
 */
object VaultRepository {

    private const val TRASH_RETENTION_MS = 30L * 86_400_000L // 30 días

    val entries = mutableStateListOf<VaultEntry>()
    val unlocked = mutableStateOf(false)

    /** Cambia al observar la bóveda activa para que la UI se recomponga. */
    val vaultRevision = mutableStateOf(0)

    /** Bloquea y cambia la bóveda activa. */
    fun switchVault(context: Context, vaultId: String) {
        lock()
        Vaults.setActive(context, vaultId)
        vaultRevision.value++
    }

    @Volatile private var key: SecretKey? = null
    @Volatile private var salt: ByteArray? = null
    @Volatile private var kdf: CryptoManager.KdfParams = CryptoManager.KdfParams.current()

    fun isUnlocked(): Boolean = key != null

    /** Entradas activas (no en papelera). */
    fun activeEntries(): List<VaultEntry> = entries.filter { !it.isDeleted }

    /** Entradas en la papelera. */
    fun trashedEntries(): List<VaultEntry> = entries.filter { it.isDeleted }

    private fun fileName(context: Context) = Vaults.fileName(Vaults.activeId(context))
    private fun vaultFile(context: Context) = File(context.filesDir, fileName(context))
    private fun backupFiles(context: Context) = listOf(
        File(context.filesDir, "${fileName(context)}.bak1"),
        File(context.filesDir, "${fileName(context)}.bak2"),
    )

    fun vaultExists(context: Context): Boolean = vaultFile(context).exists()

    fun create(context: Context, masterPassword: CharArray) {
        val s = CryptoManager.randomSalt()
        val params = CryptoManager.KdfParams.current()
        val k = CryptoManager.deriveKey(masterPassword, s, params)
        key = k
        salt = s
        kdf = params
        entries.clear()
        persist(context)
        unlocked.value = true
    }

    /**
     * Intenta abrir el archivo principal y, si está corrupto, las copias de
     * respaldo. Devuelve true si el password es correcto.
     */
    fun unlock(context: Context, masterPassword: CharArray): Boolean {
        val candidates = listOf(vaultFile(context)) + backupFiles(context)
        for (file in candidates) {
            if (!file.exists()) continue
            val data = try {
                file.readBytes()
            } catch (e: Exception) {
                continue
            }
            val header = try {
                CryptoManager.parseVault(data)
            } catch (e: Exception) {
                continue // corrupto: prueba el siguiente respaldo
            }
            val k = CryptoManager.deriveKey(masterPassword, header.salt, header.kdf)
            val plain = CryptoManager.openVault(data, k) ?: return false // password incorrecto
            loadDecrypted(context, header, k, plain)
            // Migra las bóvedas v1 (PBKDF2) a Argon2id ahora que tenemos el password
            if (header.kdf != CryptoManager.KdfParams.current()) {
                val s = CryptoManager.randomSalt()
                val params = CryptoManager.KdfParams.current()
                key = CryptoManager.deriveKey(masterPassword, s, params)
                salt = s
                kdf = params
                persist(context)
            }
            return true
        }
        return false
    }

    /** Desbloqueo biométrico: la clave derivada viene descifrada del Keystore. */
    fun unlockWithKeyBytes(context: Context, keyBytes: ByteArray): Boolean {
        val candidates = listOf(vaultFile(context)) + backupFiles(context)
        for (file in candidates) {
            if (!file.exists()) continue
            val data = try {
                file.readBytes()
            } catch (e: Exception) {
                continue
            }
            val header = try {
                CryptoManager.parseVault(data)
            } catch (e: Exception) {
                continue
            }
            val k = SecretKeySpec(keyBytes, "AES")
            val plain = CryptoManager.openVault(data, k) ?: return false
            loadDecrypted(context, header, k, plain)
            return true
        }
        return false
    }

    private fun loadDecrypted(
        context: Context,
        header: CryptoManager.VaultHeader,
        k: SecretKey,
        plain: ByteArray,
    ) {
        key = k
        salt = header.salt
        kdf = header.kdf
        entries.clear()
        entries.addAll(VaultEntry.listFromJson(String(plain, Charsets.UTF_8)))
        purgeOldTrash(context)
        unlocked.value = true
    }

    /** Elimina definitivamente lo que lleva más de 30 días en la papelera. */
    private fun purgeOldTrash(context: Context) {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
        val purged = entries.removeAll { it.isDeleted && it.deletedAt < cutoff }
        if (purged) persist(context)
    }

    fun keyBytes(): ByteArray? = key?.encoded

    fun lock() {
        key = null
        salt = null
        entries.clear()
        unlocked.value = false
    }

    @Synchronized
    private fun persist(context: Context) {
        val k = key ?: return
        val s = salt ?: return
        val json = VaultEntry.listToJson(entries.toList()).toByteArray(Charsets.UTF_8)
        val sealed = CryptoManager.sealVaultWithKey(json, k, s, kdf)
        val f = vaultFile(context)
        // Rota las copias de respaldo antes de sobrescribir
        if (f.exists()) {
            val (bak1, bak2) = backupFiles(context)
            if (bak1.exists()) bak1.copyTo(bak2, overwrite = true)
            f.copyTo(bak1, overwrite = true)
        }
        val tmp = File(f.parentFile, "${fileName(context)}.tmp")
        tmp.writeBytes(sealed)
        if (f.exists()) f.delete()
        tmp.renameTo(f)
        AutoBackup.onVaultSaved(context, sealed)
    }

    fun addEntry(context: Context, entry: VaultEntry) {
        entries.add(entry)
        persist(context)
    }

    /** Actualiza la entrada; si cambió la contraseña, guarda la anterior en el historial. */
    fun updateEntry(context: Context, entry: VaultEntry) {
        val idx = entries.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            val old = entries[idx]
            val withHistory = if (
                old.password.isNotEmpty() && old.password != entry.password
            ) {
                entry.copy(
                    history = (listOf(PasswordHistoryItem(old.password, old.passwordChangedAt)) + entry.history)
                        .take(10)
                )
            } else entry
            entries[idx] = withHistory
        } else {
            entries.add(entry)
        }
        persist(context)
    }

    /** Mueve a la papelera (recuperable durante 30 días). */
    fun trashEntry(context: Context, id: String) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(deletedAt = System.currentTimeMillis())
            persist(context)
        }
    }

    fun restoreEntry(context: Context, id: String) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(deletedAt = 0L)
            persist(context)
        }
    }

    fun deleteForever(context: Context, id: String) {
        entries.removeAll { it.id == id }
        persist(context)
    }

    fun emptyTrash(context: Context) {
        entries.removeAll { it.isDeleted }
        persist(context)
    }

    fun changeMasterPassword(context: Context, current: CharArray, new: CharArray): Boolean {
        val data = vaultFile(context).readBytes()
        val header = CryptoManager.parseVault(data)
        val k = CryptoManager.deriveKey(current, header.salt, header.kdf)
        CryptoManager.openVault(data, k) ?: return false
        val s = CryptoManager.randomSalt()
        val params = CryptoManager.KdfParams.current()
        key = CryptoManager.deriveKey(new, s, params)
        salt = s
        kdf = params
        persist(context)
        return true
    }

    /** Copia de seguridad: el mismo archivo cifrado de la bóveda. */
    fun exportBytes(context: Context): ByteArray = vaultFile(context).readBytes()

    /**
     * Importa una copia cifrada (v1 o v2) con su password. Devuelve cuántas
     * entradas nuevas se añadieron, o -1 si el password es incorrecto.
     */
    fun importBackup(context: Context, data: ByteArray, password: CharArray): Int {
        val header = CryptoManager.parseVault(data)
        val k = CryptoManager.deriveKey(password, header.salt, header.kdf)
        val plain = CryptoManager.openVault(data, k) ?: return -1
        val imported = VaultEntry.listFromJson(String(plain, Charsets.UTF_8))
        return addAllNew(context, imported)
    }

    /** Añade entradas evitando duplicados exactos. Devuelve cuántas entraron. */
    fun addAllNew(context: Context, imported: List<VaultEntry>): Int {
        val existingIds = entries.map { it.id }.toSet()
        val existingSignatures = entries
            .map { Triple(it.title.lowercase(), it.username.lowercase(), it.password) }
            .toHashSet()
        val newOnes = imported.filter {
            it.id !in existingIds &&
                Triple(it.title.lowercase(), it.username.lowercase(), it.password) !in existingSignatures
        }
        entries.addAll(newOnes)
        if (newOnes.isNotEmpty()) persist(context)
        return newOnes.size
    }
}
