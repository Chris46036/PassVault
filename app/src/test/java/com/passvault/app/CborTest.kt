package com.passvault.app

import com.passvault.app.passkeys.Cbor
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Vectores de https://www.rfc-editor.org/rfc/rfc8949 (apéndice A). */
class CborTest {

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `enteros pequenos y grandes`() {
        assertArrayEquals(hex("00"), Cbor.encode(0))
        assertArrayEquals(hex("0a"), Cbor.encode(10))
        assertArrayEquals(hex("17"), Cbor.encode(23))
        assertArrayEquals(hex("1818"), Cbor.encode(24))
        assertArrayEquals(hex("1903e8"), Cbor.encode(1000))
        assertArrayEquals(hex("1a000f4240"), Cbor.encode(1_000_000))
    }

    @Test
    fun `enteros negativos`() {
        assertArrayEquals(hex("20"), Cbor.encode(-1))
        assertArrayEquals(hex("26"), Cbor.encode(-7)) // alg ES256 de COSE
        assertArrayEquals(hex("3863"), Cbor.encode(-100))
    }

    @Test
    fun `cadenas de texto y bytes`() {
        assertArrayEquals(hex("6449455446"), Cbor.encode("IETF"))
        assertArrayEquals(hex("4401020304"), Cbor.encode(byteArrayOf(1, 2, 3, 4)))
        assertArrayEquals(hex("60"), Cbor.encode(""))
    }

    @Test
    fun `mapas con orden preservado`() {
        // {1: 2, 3: 4} → a201020304
        assertArrayEquals(
            hex("a201020304"),
            Cbor.encode(Cbor.CborMap(listOf(1 to 2, 3 to 4)))
        )
        // {"fmt": "none", "attStmt": {}} como en WebAuthn
        assertArrayEquals(
            hex("a263666d74646e6f6e656761747453746d74a0"),
            Cbor.encode(
                Cbor.CborMap(
                    listOf("fmt" to "none", "attStmt" to Cbor.CborMap(emptyList()))
                )
            )
        )
    }
}
