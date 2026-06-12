package com.passvault.app.passkeys

import java.io.ByteArrayOutputStream

/**
 * Escritor CBOR mínimo (RFC 8949) para construir los objetos de WebAuthn:
 * enteros, byte strings, text strings y mapas con orden explícito.
 */
object Cbor {

    /** Un mapa CBOR conservando el orden dado (WebAuthn exige orden canónico). */
    class CborMap(val pairs: List<Pair<Any, Any>>)

    fun encode(item: Any): ByteArray {
        val out = ByteArrayOutputStream()
        writeItem(item, out)
        return out.toByteArray()
    }

    private fun writeItem(item: Any, out: ByteArrayOutputStream) {
        when (item) {
            is Int -> writeInt(item.toLong(), out)
            is Long -> writeInt(item, out)
            is ByteArray -> {
                writeHeader(2, item.size.toLong(), out)
                out.write(item)
            }
            is String -> {
                val bytes = item.toByteArray(Charsets.UTF_8)
                writeHeader(3, bytes.size.toLong(), out)
                out.write(bytes)
            }
            is CborMap -> {
                writeHeader(5, item.pairs.size.toLong(), out)
                item.pairs.forEach { (k, v) ->
                    writeItem(k, out)
                    writeItem(v, out)
                }
            }
            else -> throw IllegalArgumentException("Tipo CBOR no soportado: ${item::class}")
        }
    }

    private fun writeInt(value: Long, out: ByteArrayOutputStream) {
        if (value >= 0) writeHeader(0, value, out)
        else writeHeader(1, -1L - value, out)
    }

    private fun writeHeader(major: Int, value: Long, out: ByteArrayOutputStream) {
        val m = major shl 5
        when {
            value < 24 -> out.write(m or value.toInt())
            value < 0x100 -> {
                out.write(m or 24)
                out.write(value.toInt())
            }
            value < 0x10000 -> {
                out.write(m or 25)
                out.write((value shr 8).toInt() and 0xFF)
                out.write(value.toInt() and 0xFF)
            }
            else -> {
                out.write(m or 26)
                for (shift in 24 downTo 0 step 8) {
                    out.write((value shr shift).toInt() and 0xFF)
                }
            }
        }
    }
}
