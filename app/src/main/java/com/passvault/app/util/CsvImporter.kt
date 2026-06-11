package com.passvault.app.util

import com.passvault.app.data.Categories
import com.passvault.app.data.EntryType
import com.passvault.app.data.VaultEntry

/**
 * Importa CSV exportados por Chrome, Bitwarden, LastPass y similares.
 * Detecta las columnas por el nombre de la cabecera.
 */
object CsvImporter {

    private val TITLE_COLS = listOf("name", "title", "nombre", "account")
    private val URL_COLS = listOf("url", "login_uri", "web site", "website", "sitio")
    private val USER_COLS = listOf("username", "login_username", "user name", "usuario", "login", "email")
    private val PASS_COLS = listOf("password", "login_password", "contraseña", "contrasena", "pass")
    private val NOTES_COLS = listOf("note", "notes", "extra", "notas", "comments")
    private val TOTP_COLS = listOf("totp", "login_totp", "otp")

    /** Devuelve las entradas leídas, o null si el archivo no parece un CSV de contraseñas. */
    fun parse(text: String): List<VaultEntry>? {
        val rows = parseCsv(text)
        if (rows.size < 2) return null
        val header = rows.first().map { it.trim().lowercase() }

        fun col(candidates: List<String>): Int =
            header.indexOfFirst { h -> candidates.any { h == it } }

        val titleIdx = col(TITLE_COLS)
        val urlIdx = col(URL_COLS)
        val userIdx = col(USER_COLS)
        val passIdx = col(PASS_COLS)
        val notesIdx = col(NOTES_COLS)
        val totpIdx = col(TOTP_COLS)

        if (passIdx < 0 && userIdx < 0) return null // no es un CSV de credenciales

        fun cell(row: List<String>, idx: Int): String =
            if (idx in row.indices) row[idx].trim() else ""

        return rows.drop(1).mapNotNull { row ->
            val title = cell(row, titleIdx)
            val url = cell(row, urlIdx)
            val username = cell(row, userIdx)
            val password = cell(row, passIdx)
            if (title.isBlank() && username.isBlank() && password.isBlank()) return@mapNotNull null
            VaultEntry(
                type = EntryType.LOGIN,
                title = title.ifBlank { DomainUtil.registrableDomain(url).ifBlank { username } },
                username = username,
                password = password,
                url = url,
                notes = cell(row, notesIdx),
                totpSecret = extractTotpSecret(cell(row, totpIdx)),
                category = Categories.LOGIN,
            )
        }
    }

    /** Acepta secretos en claro o URIs otpauth://. */
    fun extractTotpSecret(value: String): String {
        val v = value.trim()
        if (!v.startsWith("otpauth://", ignoreCase = true)) return v
        val query = v.substringAfter('?', "")
        for (param in query.split('&')) {
            val (k, value2) = param.split('=', limit = 2).let {
                it[0] to it.getOrElse(1) { "" }
            }
            if (k.equals("secret", ignoreCase = true)) return value2
        }
        return ""
    }

    /** Parser CSV (RFC 4180): comillas, comas y saltos de línea dentro de campos. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var field = StringBuilder()
        var row = ArrayList<String>()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> {
                        field.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    row.add(field.toString()); field = StringBuilder()
                }
                c == '\r' -> { /* ignora */ }
                c == '\n' -> {
                    row.add(field.toString()); field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }
}
