package com.passvault.app.util

import com.passvault.app.data.EntryType
import com.passvault.app.data.VaultEntry

/**
 * Exportación a CSV SIN CIFRAR (cabeceras compatibles con Chrome) para migrar
 * a otro gestor. El que llama es responsable de advertir al usuario.
 */
object CsvExporter {

    fun export(entries: List<VaultEntry>): String {
        val sb = StringBuilder("name,url,username,password,note,totp\n")
        entries.filter { !it.isDeleted && it.type == EntryType.LOGIN }.forEach { e ->
            sb.append(escape(e.title)).append(',')
                .append(escape(e.url)).append(',')
                .append(escape(e.username)).append(',')
                .append(escape(e.password)).append(',')
                .append(escape(e.notes)).append(',')
                .append(escape(e.totpSecret)).append('\n')
        }
        return sb.toString()
    }

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
