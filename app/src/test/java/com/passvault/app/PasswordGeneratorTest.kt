package com.passvault.app

import com.passvault.app.util.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `respeta la longitud pedida`() {
        for (len in listOf(8, 16, 32, 64)) {
            assertEquals(len, PasswordGenerator.generate(len).length)
        }
    }

    @Test
    fun `incluye al menos un caracter de cada clase seleccionada`() {
        repeat(50) {
            val p = PasswordGenerator.generate(
                12, useLower = true, useUpper = true, useDigits = true, useSymbols = true
            )
            assertTrue("sin minúscula: $p", p.any { it.isLowerCase() })
            assertTrue("sin mayúscula: $p", p.any { it.isUpperCase() })
            assertTrue("sin dígito: $p", p.any { it.isDigit() })
            assertTrue("sin símbolo: $p", p.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `solo usa las clases seleccionadas`() {
        repeat(20) {
            val p = PasswordGenerator.generate(
                20, useLower = true, useUpper = false, useDigits = true, useSymbols = false
            )
            assertTrue(p.all { it.isLowerCase() || it.isDigit() })
        }
    }

    @Test
    fun `excluir ambiguos elimina caracteres confusos`() {
        repeat(20) {
            val p = PasswordGenerator.generate(40, excludeAmbiguous = true)
            assertTrue(p.none { it in "Il1O0o" })
        }
    }

    @Test
    fun `sin clases seleccionadas devuelve vacio`() {
        assertEquals(
            "",
            PasswordGenerator.generate(16, useLower = false, useUpper = false, useDigits = false, useSymbols = false)
        )
    }

    @Test
    fun `la frase de paso tiene el numero de palabras pedido`() {
        val phrase = PasswordGenerator.generatePassphrase(5, separator = "-", addNumber = false)
        assertEquals(5, phrase.split("-").size)
    }
}
