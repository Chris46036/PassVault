package com.passvault.app.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado de la bóveda: AES-256-GCM con clave derivada del master password.
 *
 * Formato v2 (actual, Argon2id):
 *   "PVLT" (4) | version=2 (1) | memKiB (4) | iterations (4) | parallelism (4) | salt (16) | iv (12) | ciphertext
 * Formato v1 (legado, PBKDF2-HMAC-SHA256; se sigue leyendo para bóvedas y copias antiguas):
 *   "PVLT" (4) | version=1 (1) | iterations (4) | salt (16) | iv (12) | ciphertext
 */
object CryptoManager {

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BYTES = 32
    private val MAGIC = byteArrayOf(0x50, 0x56, 0x4C, 0x54) // "PVLT"

    private const val VERSION_PBKDF2: Byte = 1
    private const val VERSION_ARGON2: Byte = 2

    // Parámetros Argon2id (mismos valores por defecto que usa Bitwarden)
    const val ARGON2_MEM_KIB = 65536 // 64 MiB
    const val ARGON2_ITERATIONS = 3
    const val ARGON2_PARALLELISM = 4

    private val random = SecureRandom()

    fun randomSalt(): ByteArray = ByteArray(SALT_LEN).also { random.nextBytes(it) }

    /** Parámetros de derivación leídos de la cabecera de una bóveda. */
    data class KdfParams(
        val version: Byte,
        val iterations: Int,
        val memKiB: Int = 0,
        val parallelism: Int = 0,
    ) {
        companion object {
            fun current() = KdfParams(
                VERSION_ARGON2, ARGON2_ITERATIONS, ARGON2_MEM_KIB, ARGON2_PARALLELISM
            )
        }
    }

    /**
     * Calibra Argon2id para que la derivación tarde ~1 segundo en este
     * dispositivo: más coste en gama alta, sin castigar a la gama baja.
     */
    fun calibrateKdf(): KdfParams {
        return try {
            val trialMemKiB = 16 * 1024 // prueba con 16 MiB y 2 iteraciones
            val trialIters = 2
            val start = System.nanoTime()
            Argon2Kt().hash(
                mode = Argon2Mode.ARGON2_ID,
                password = "calibracion".toByteArray(Charsets.UTF_8),
                salt = randomSalt(),
                tCostInIterations = trialIters,
                mCostInKibibyte = trialMemKiB,
                parallelism = ARGON2_PARALLELISM,
                hashLengthInBytes = KEY_BYTES,
            )
            val trialMs = (System.nanoTime() - start) / 1_000_000.0
            // El coste crece ≈ linealmente con memoria × iteraciones
            val targetCost = (trialMemKiB.toLong() * trialIters * (1000.0 / trialMs.coerceAtLeast(1.0))).toLong()

            var memKiB = ARGON2_MEM_KIB
            var iterations = (targetCost / memKiB).toInt().coerceIn(2, 6)
            if (targetCost < memKiB.toLong() * 2) {
                // Dispositivo modesto: reduce memoria antes que bajar de 2 iteraciones
                memKiB = (targetCost / 2).toInt().coerceIn(32 * 1024, ARGON2_MEM_KIB)
                iterations = 2
            }
            KdfParams(VERSION_ARGON2, iterations, memKiB, ARGON2_PARALLELISM)
        } catch (e: Exception) {
            KdfParams.current()
        }
    }

    data class VaultHeader(
        val kdf: KdfParams,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    fun deriveKey(password: CharArray, salt: ByteArray, kdf: KdfParams): SecretKey {
        val keyBytes = when (kdf.version) {
            VERSION_ARGON2 -> {
                Argon2Kt().hash(
                    mode = Argon2Mode.ARGON2_ID,
                    password = String(password).toByteArray(Charsets.UTF_8),
                    salt = salt,
                    tCostInIterations = kdf.iterations,
                    mCostInKibibyte = kdf.memKiB,
                    parallelism = kdf.parallelism,
                    hashLengthInBytes = KEY_BYTES,
                ).rawHashAsByteArray()
            }
            VERSION_PBKDF2 -> {
                val spec = PBEKeySpec(password, salt, kdf.iterations, KEY_BYTES * 8)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val bytes = factory.generateSecret(spec).encoded
                spec.clearPassword()
                bytes
            }
            else -> throw IllegalArgumentException("Versión de bóveda no soportada")
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Cifra el contenido escribiendo en la cabecera los mismos parámetros KDF
     * con los que se derivó la clave (si no, la bóveda sería indescifrable).
     */
    fun sealVaultWithKey(plaintext: ByteArray, key: SecretKey, salt: ByteArray, kdf: KdfParams): ByteArray {
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)
        return when (kdf.version) {
            VERSION_ARGON2 -> ByteBuffer.allocate(MAGIC.size + 1 + 12 + SALT_LEN + IV_LEN + ct.size)
                .put(MAGIC).put(VERSION_ARGON2)
                .putInt(kdf.memKiB).putInt(kdf.iterations).putInt(kdf.parallelism)
                .put(salt).put(iv).put(ct)
                .array()
            VERSION_PBKDF2 -> ByteBuffer.allocate(MAGIC.size + 1 + 4 + SALT_LEN + IV_LEN + ct.size)
                .put(MAGIC).put(VERSION_PBKDF2)
                .putInt(kdf.iterations)
                .put(salt).put(iv).put(ct)
                .array()
            else -> throw IllegalArgumentException("Versión de bóveda no soportada")
        }
    }

    fun parseVault(data: ByteArray): VaultHeader {
        require(data.size > MAGIC.size + 1 + 4 + SALT_LEN + IV_LEN) { "Archivo de bóveda inválido" }
        val buf = ByteBuffer.wrap(data)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        require(magic.contentEquals(MAGIC)) { "No es un archivo PassVault" }
        val kdf = when (val version = buf.get()) {
            VERSION_PBKDF2 -> KdfParams(VERSION_PBKDF2, buf.int)
            VERSION_ARGON2 -> {
                val mem = buf.int
                val iter = buf.int
                val par = buf.int
                KdfParams(VERSION_ARGON2, iter, mem, par)
            }
            else -> throw IllegalArgumentException("Versión de bóveda no soportada: $version")
        }
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iv = ByteArray(IV_LEN).also { buf.get(it) }
        val ct = ByteArray(buf.remaining()).also { buf.get(it) }
        return VaultHeader(kdf, salt, iv, ct)
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
