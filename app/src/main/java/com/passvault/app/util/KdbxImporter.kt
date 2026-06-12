package com.passvault.app.util

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.Group
import com.passvault.app.data.Categories
import com.passvault.app.data.EntryType
import com.passvault.app.data.VaultEntry
import java.io.InputStream

/**
 * Importa bases de datos KeePass 2.x (.kdbx, formatos 3.1 y 4.x) usando kotpass.
 * Devuelve null si la contraseña es incorrecta o el archivo no es un kdbx.
 */
object KdbxImporter {

    fun import(stream: InputStream, password: String): List<VaultEntry>? {
        val db = try {
            KeePassDatabase.decode(
                stream,
                Credentials.from(EncryptedValue.fromString(password))
            )
        } catch (e: Exception) {
            return null
        }
        val out = ArrayList<VaultEntry>()
        collect(db.content.group, out)
        return out
    }

    private fun collect(group: Group, out: MutableList<VaultEntry>) {
        group.entries.forEach { out.add(toVaultEntry(it)) }
        group.groups.forEach { collect(it, out) }
    }

    private fun toVaultEntry(entry: Entry): VaultEntry {
        fun field(name: String): String = entry.fields[name]?.content ?: ""

        val otpRaw = field("otp").ifBlank { field("TOTP Seed") }
        return VaultEntry(
            type = EntryType.LOGIN,
            title = field("Title").ifBlank { field("UserName") },
            username = field("UserName"),
            password = field("Password"),
            url = field("URL"),
            notes = field("Notes"),
            totpSecret = CsvImporter.extractTotpSecret(otpRaw),
            category = Categories.LOGIN,
            tags = entry.tags,
        )
    }
}
