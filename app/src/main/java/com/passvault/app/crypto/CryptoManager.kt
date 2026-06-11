package com.passvault.app.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado de la bóveda: AES-256-GCM con clave derivada del master password
 * mediante PBKDF2-HMAC-SHA256. Formato de archivo:
 *   magic "PVLT" (4) | version (1) | iterations (4) | salt (16) | iv (12) | ciphertext
 */
object CryptoManager {

    const val DEFAULT_ITERATIONS = 250_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private val MAGIC = byteArrayOf(0x50, 0x56, 0x4C, 0x54) // "PVLT"
    private const val VERSION: Byte = 1

    private val random = SecureRandom()

    fun randomSalt(): ByteArray = ByteArray(SALT_LEN).also { random.nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Serializa y cifra el contenido de la bóveda en el formato de archivo. */
    fun sealVault(plaintext: ByteArray, password: CharArray): ByteArray {
        val salt = randomSalt()
        val key = deriveKey(password, salt)
        return sealVaultWithKey(plaintext, key, salt, DEFAULT_ITERATIONS)
    }

    fun sealVaultWithKey(plaintext: ByteArray, key: SecretKey, salt: ByteArray, iterations: Int): ByteArray {
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(MAGIC.size + 1 + 4 + SALT_LEN + IV_LEN + ct.size)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv).put(ct)
            .array()
    }

    data class VaultHeader(val iterations: Int, val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray)

    fun parseVault(data: ByteArray): VaultHeader {
        require(data.size > MAGIC.size + 1 + 4 + SALT_LEN + IV_LEN) { "Archivo de bóveda inválido" }
        val buf = ByteBuffer.wrap(data)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        require(magic.contentEquals(MAGIC)) { "No es un archivo PassVault" }
        val version = buf.get()
        require(version == VERSION) { "Versión de bóveda no soportada" }
        val iterations = buf.int
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iv = ByteArray(IV_LEN).also { buf.get(it) }
        val ct = ByteArray(buf.remaining()).also { buf.get(it) }
        return VaultHeader(iterations, salt, iv, ct)
    }

    /** Devuelve null si la clave es incorrecta o el archivo fue manipulado. */
    fun openVault(data: ByteArray, key: SecretKey): ByteArray? {
        return try {
            val h = parseVault(data)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, h.iv))
            cipher.doFinal(h.ciphertext)
        } catch (e: Exception) {
            null
        }
    }
}
