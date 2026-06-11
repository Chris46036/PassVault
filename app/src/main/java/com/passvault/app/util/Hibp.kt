package com.passvault.app.util

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Comprobación de filtraciones con Have I Been Pwned usando k-anonimato:
 * solo se envían los primeros 5 caracteres del hash SHA-1, nunca la contraseña.
 */
object Hibp {

    /** Número de filtraciones conocidas, 0 si no aparece, -1 si falla la red. */
    fun breachCount(password: String): Int {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
                .digest(password.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02X".format(it) }
            val prefix = hex.substring(0, 5)
            val suffix = hex.substring(5)

            val conn = URL("https://api.pwnedpasswords.com/range/$prefix")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Add-Padding", "true")
            conn.setRequestProperty("User-Agent", "PassVault-Android")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            for (line in body.lineSequence()) {
                val parts = line.trim().split(":")
                if (parts.size == 2 && parts[0].equals(suffix, ignoreCase = true)) {
                    return parts[1].trim().toIntOrNull() ?: 0
                }
            }
            0
        } catch (e: Exception) {
            -1
        }
    }
}
