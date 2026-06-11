package com.passvault.app.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/** Generador TOTP (RFC 6238): códigos 2FA de 6 dígitos cada 30 segundos. */
object Totp {

    private const val PERIOD_SECONDS = 30
    private const val DIGITS = 6

    fun isValidSecret(secret: String): Boolean = try {
        base32Decode(secret).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /** Devuelve el código actual, o null si el secreto no es base32 válido. */
    fun currentCode(secret: String, timeMillis: Long = System.currentTimeMillis()): String? {
        return try {
            val key = base32Decode(secret)
            if (key.isEmpty()) return null
            val counter = timeMillis / 1000 / PERIOD_SECONDS
            val data = ByteArray(8)
            var c = counter
            for (i in 7 downTo 0) {
                data[i] = (c and 0xFF).toByte()
                c = c shr 8
            }
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key, "HmacSHA1"))
            val hash = mac.doFinal(data)
            val offset = (hash[hash.size - 1] and 0x0F).toInt()
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
            var pow = 1
            repeat(DIGITS) { pow *= 10 }
            (binary % pow).toString().padStart(DIGITS, '0')
        } catch (e: Exception) {
            null
        }
    }

    /** Segundos que faltan para que cambie el código. */
    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis()): Int =
        PERIOD_SECONDS - ((timeMillis / 1000) % PERIOD_SECONDS).toInt()

    private const val B32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32Decode(input: String): ByteArray {
        val clean = input.uppercase().replace(" ", "").replace("-", "").trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)
        var bits = 0
        var value = 0
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        for (ch in clean) {
            val idx = B32_ALPHABET.indexOf(ch)
            require(idx >= 0) { "Carácter base32 inválido: $ch" }
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                out.add(((value shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
