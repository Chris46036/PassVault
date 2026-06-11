package com.passvault.app.util

import androidx.compose.ui.graphics.Color
import com.passvault.app.R
import kotlin.math.log2

data class StrengthResult(val score: Int, val labelRes: Int, val color: Color, val entropyBits: Int)

object PasswordStrength {

    private val COMMON = setOf(
        "123456", "password", "12345678", "qwerty", "123456789", "12345", "1234",
        "111111", "1234567", "dragon", "123123", "abc123", "admin", "letmein",
        "welcome", "monkey", "password1", "qwerty123", "iloveyou", "000000",
        "contraseña", "contrasena", "hola123", "tequiero", "america", "futbol",
    )

    fun evaluate(password: String): StrengthResult {
        if (password.isEmpty()) return StrengthResult(0, R.string.strength_empty, Color(0xFF6E7681), 0)
        if (password.lowercase() in COMMON) {
            return StrengthResult(0, R.string.strength_common, Color(0xFFF85149), 0)
        }

        var pool = 0
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 28

        var effectiveLength = password.length.toDouble()
        // Penaliza repeticiones y secuencias simples
        for (i in 1 until password.length) {
            val c = password[i]
            val p = password[i - 1]
            if (c == p || c.code == p.code + 1 || c.code == p.code - 1) effectiveLength -= 0.5
        }
        val bits = (effectiveLength * log2(pool.toDouble().coerceAtLeast(2.0))).toInt()

        return when {
            bits < 28 -> StrengthResult(0, R.string.strength_very_weak, Color(0xFFF85149), bits)
            bits < 36 -> StrengthResult(1, R.string.strength_weak, Color(0xFFF0883E), bits)
            bits < 60 -> StrengthResult(2, R.string.strength_fair, Color(0xFFD29922), bits)
            bits < 90 -> StrengthResult(3, R.string.strength_strong, Color(0xFF3FB950), bits)
            else -> StrengthResult(4, R.string.strength_very_strong, Color(0xFF2EA043), bits)
        }
    }
}
