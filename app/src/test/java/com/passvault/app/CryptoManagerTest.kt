package com.passvault.app

import com.passvault.app.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Prueba el formato de archivo y el cifrado AES-GCM. La derivación se prueba
 * con PBKDF2 (v1) porque Argon2 usa una librería nativa de Android no
 * disponible en la JVM local; el camino Argon2 se ejercita en el dispositivo.
 */
class CryptoManagerTest {

    private val pbkdf2 = CryptoManager.KdfParams(version = 1, iterations = 1000)

    @Test
    fun `cifrar y descifrar devuelve el contenido original`() {
        val salt = CryptoManager.randomSalt()
        val key = CryptoManager.deriveKey("correcthorse".toCharArray(), salt, pbkdf2)
        val plaintext = """{"entries":[{"title":"correo","password":"s3creta"}]}""".toByteArray()

        val sealed = CryptoManager.sealVaultWithKey(plaintext, key, salt, pbkdf2)
        val opened = CryptoManager.openVault(sealed, key)

        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun `clave incorrecta devuelve null`() {
        val salt = CryptoManager.randomSalt()
        val key = CryptoManager.deriveKey("correcta".toCharArray(), salt, pbkdf2)
        val wrong = CryptoManager.deriveKey("incorrecta".toCharArray(), salt, pbkdf2)
        val sealed = CryptoManager.sealVaultWithKey("hola".toByteArray(), key, salt, pbkdf2)

        assertNull(CryptoManager.openVault(sealed, wrong))
    }

    @Test
    fun `archivo manipulado devuelve null`() {
        val salt = CryptoManager.randomSalt()
        val key = CryptoManager.deriveKey("clave".toCharArray(), salt, pbkdf2)
        val sealed = CryptoManager.sealVaultWithKey("hola".toByteArray(), key, salt, pbkdf2)

        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        assertNull(CryptoManager.openVault(sealed, key))
    }

    @Test
    fun `la cabecera conserva los parametros de derivacion`() {
        val salt = CryptoManager.randomSalt()
        val key = CryptoManager.deriveKey("clave".toCharArray(), salt, pbkdf2)
        val sealed = CryptoManager.sealVaultWithKey("hola".toByteArray(), key, salt, pbkdf2)

        val header = CryptoManager.parseVault(sealed)
        assertEquals(pbkdf2, header.kdf) // conserva la versión y parámetros de la clave
        assertArrayEquals(salt, header.salt)
    }

    @Test
    fun `misma contrasena y salt derivan la misma clave`() {
        val salt = CryptoManager.randomSalt()
        val k1 = CryptoManager.deriveKey("clave".toCharArray(), salt, pbkdf2)
        val k2 = CryptoManager.deriveKey("clave".toCharArray(), salt, pbkdf2)
        assertArrayEquals(k1.encoded, k2.encoded)
    }
}
