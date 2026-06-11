package com.passvault.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.passvault.app.crypto.CryptoManager
import java.io.File
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Bóveda en memoria + persistencia cifrada en filesDir/vault.enc.
 * La clave maestra derivada solo vive en memoria mientras la app está desbloqueada.
 */
object VaultRepository {

    private const val VAULT_FILE = "vault.enc"

    val entries = mutableStateListOf<VaultEntry>()
    val unlocked = mutableStateOf(false)

    @Volatile private var key: SecretKey? = null
    @Volatile private var salt: ByteArray? = null
    @Volatile private var iterations: Int = CryptoManager.DEFAULT_ITERATIONS

    fun isUnlocked(): Boolean = key != null

    private fun vaultFile(context: Context) = File(context.filesDir, VAULT_FILE)

    fun vaultExists(context: Context): Boolean = vaultFile(context).exists()

    /** Crea una bóveda nueva con el master password dado. */
    fun create(context: Context, masterPassword: CharArray) {
        val s = CryptoManager.randomSalt()
        val k = CryptoManager.deriveKey(masterPassword, s)
        key = k
        salt = s
        iterations = CryptoManager.DEFAULT_ITERATIONS
        entries.clear()
        persist(context)
        unlocked.value = true
    }

    /** Devuelve true si el password es correcto. */
    fun unlock(context: Context, masterPassword: CharArray): Boolean {
        val data = vaultFile(context).readBytes()
        val header = CryptoManager.parseVault(data)
        val k = CryptoManager.deriveKey(masterPassword, header.salt, header.iterations)
        val plain = CryptoManager.openVault(data, k) ?: return false
        key = k
        salt = header.salt
        iterations = header.iterations
        entries.clear()
        entries.addAll(VaultEntry.listFromJson(String(plain, Charsets.UTF_8)))
        unlocked.value = true
        return true
    }

    /** Desbloqueo biométrico: la clave derivada viene descifrada del Keystore. */
    fun unlockWithKeyBytes(context: Context, keyBytes: ByteArray): Boolean {
        val data = vaultFile(context).readBytes()
        val header = CryptoManager.parseVault(data)
        val k = SecretKeySpec(keyBytes, "AES")
        val plain = CryptoManager.openVault(data, k) ?: return false
        key = k
        salt = header.salt
        iterations = header.iterations
        entries.clear()
        entries.addAll(VaultEntry.listFromJson(String(plain, Charsets.UTF_8)))
        unlocked.value = true
        return true
    }

    fun keyBytes(): ByteArray? = key?.encoded

    fun lock() {
        key = null
        salt = null
        entries.clear()
        unlocked.value = false
    }

    private fun persist(context: Context) {
        val k = key ?: return
        val s = salt ?: return
        val json = VaultEntry.listToJson(entries.toList()).toByteArray(Charsets.UTF_8)
        val sealed = CryptoManager.sealVaultWithKey(json, k, s, iterations)
        val f = vaultFile(context)
        val tmp = File(f.parentFile, "$VAULT_FILE.tmp")
        tmp.writeBytes(sealed)
        if (f.exists()) f.delete()
        tmp.renameTo(f)
    }

    fun addEntry(context: Context, entry: VaultEntry) {
        entries.add(entry)
        persist(context)
    }

    fun updateEntry(context: Context, entry: VaultEntry) {
        val idx = entries.indexOfFirst { it.id == entry.id }
        if (idx >= 0) entries[idx] = entry else entries.add(entry)
        persist(context)
    }

    fun deleteEntry(context: Context, id: String) {
        entries.removeAll { it.id == id }
        persist(context)
    }

    fun changeMasterPassword(context: Context, current: CharArray, new: CharArray): Boolean {
        val data = vaultFile(context).readBytes()
        val header = CryptoManager.parseVault(data)
        val k = CryptoManager.deriveKey(current, header.salt, header.iterations)
        CryptoManager.openVault(data, k) ?: return false
        val s = CryptoManager.randomSalt()
        key = CryptoManager.deriveKey(new, s)
        salt = s
        iterations = CryptoManager.DEFAULT_ITERATIONS
        persist(context)
        return true
    }

    /** Copia de seguridad: el mismo archivo cifrado de la bóveda. */
    fun exportBytes(context: Context): ByteArray = vaultFile(context).readBytes()

    /**
     * Importa una copia cifrada con su password. Devuelve cuántas entradas
     * nuevas se añadieron, o -1 si el password es incorrecto.
     */
    fun importBackup(context: Context, data: ByteArray, password: CharArray): Int {
        val header = CryptoManager.parseVault(data)
        val k = CryptoManager.deriveKey(password, header.salt, header.iterations)
        val plain = CryptoManager.openVault(data, k) ?: return -1
        val imported = VaultEntry.listFromJson(String(plain, Charsets.UTF_8))
        val existingIds = entries.map { it.id }.toSet()
        val newOnes = imported.filter { it.id !in existingIds }
        entries.addAll(newOnes)
        persist(context)
        return newOnes.size
    }
}
