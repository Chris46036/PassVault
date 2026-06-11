package com.passvault.app

import com.passvault.app.util.Totp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpTest {

    /** Secreto de los vectores de prueba del RFC 6238 ("12345678901234567890" en base32). */
    private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `vectores RFC 6238 con SHA-1 truncados a 6 digitos`() {
        assertEquals("287082", Totp.currentCode(rfcSecret, 59_000L))
        assertEquals("081804", Totp.currentCode(rfcSecret, 1_111_111_109_000L))
        assertEquals("005924", Totp.currentCode(rfcSecret, 1_234_567_890_000L))
        assertEquals("279037", Totp.currentCode(rfcSecret, 2_000_000_000_000L))
    }

    @Test
    fun `acepta secretos con espacios minusculas y relleno`() {
        val code = Totp.currentCode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq==", 59_000L)
        assertEquals("287082", code)
    }

    @Test
    fun `secreto invalido devuelve null`() {
        assertNull(Totp.currentCode("!!no-es-base32!!", 59_000L))
        assertFalse(Totp.isValidSecret("!!no-es-base32!!"))
        assertTrue(Totp.isValidSecret(rfcSecret))
    }

    @Test
    fun `la cuenta atras esta entre 1 y 30`() {
        for (t in 0L..120L) {
            val remaining = Totp.secondsRemaining(t * 1000)
            assertTrue(remaining in 1..30)
        }
    }
}
