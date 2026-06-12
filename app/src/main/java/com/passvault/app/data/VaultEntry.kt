package com.passvault.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Tipos de elemento de la bóveda. */
object EntryType {
    const val LOGIN = "login"
    const val CARD = "card"
    const val NOTE = "note"
    const val IDENTITY = "identity"

    /** Passkey WebAuthn; extras: rpId, credId, privateKey (PKCS8 b64url), userHandle, userName. */
    const val PASSKEY = "passkey"

    /** Tipos que el usuario puede crear a mano (las passkeys las crea el sistema). */
    val ALL = listOf(LOGIN, CARD, NOTE, IDENTITY)
}

data class PasswordHistoryItem(val password: String, val changedAt: Long)

/** Archivo adjunto cifrado dentro de la bóveda (contenido en base64). */
data class Attachment(val name: String, val dataB64: String) {
    val sizeBytes: Int get() = dataB64.length / 4 * 3
}

data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: String = EntryType.LOGIN,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val category: String = Categories.OTHER,
    val favorite: Boolean = false,
    val totpSecret: String = "",
    /** Campos según el tipo: tarjeta (number, holder, expiry, cvv), identidad (fullName, phone, address, document). */
    val extras: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val history: List<PasswordHistoryItem> = emptyList(),
    /** 0 = activa; >0 = en la papelera desde ese instante. */
    val deletedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val passwordChangedAt: Long = System.currentTimeMillis(),
) {
    val isDeleted: Boolean get() = deletedAt > 0L

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("title", title)
        put("username", username)
        put("password", password)
        put("url", url)
        put("notes", notes)
        put("category", category)
        put("favorite", favorite)
        put("totpSecret", totpSecret)
        put("extras", JSONObject(extras as Map<*, *>))
        put("tags", JSONArray(tags))
        put("attachments", JSONArray().apply {
            attachments.forEach {
                put(JSONObject().put("n", it.name).put("d", it.dataB64))
            }
        })
        put("history", JSONArray().apply {
            history.forEach {
                put(JSONObject().put("p", it.password).put("t", it.changedAt))
            }
        })
        put("deletedAt", deletedAt)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("passwordChangedAt", passwordChangedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): VaultEntry {
            val extrasObj = o.optJSONObject("extras")
            val extras = buildMap {
                extrasObj?.keys()?.forEach { k -> put(k, extrasObj.optString(k)) }
            }
            val tagsArr = o.optJSONArray("tags")
            val tags = buildList {
                if (tagsArr != null) for (i in 0 until tagsArr.length()) add(tagsArr.getString(i))
            }
            val attachArr = o.optJSONArray("attachments")
            val attachments = buildList {
                if (attachArr != null) {
                    for (i in 0 until attachArr.length()) {
                        val a = attachArr.getJSONObject(i)
                        add(Attachment(a.optString("n"), a.optString("d")))
                    }
                }
            }
            val historyArr = o.optJSONArray("history")
            val history = buildList {
                if (historyArr != null) {
                    for (i in 0 until historyArr.length()) {
                        val h = historyArr.getJSONObject(i)
                        add(PasswordHistoryItem(h.optString("p"), h.optLong("t")))
                    }
                }
            }
            return VaultEntry(
                id = o.optString("id", UUID.randomUUID().toString()),
                type = o.optString("type", EntryType.LOGIN),
                title = o.optString("title"),
                username = o.optString("username"),
                password = o.optString("password"),
                url = o.optString("url"),
                notes = o.optString("notes"),
                category = Categories.normalize(o.optString("category", Categories.OTHER)),
                favorite = o.optBoolean("favorite", false),
                totpSecret = o.optString("totpSecret"),
                extras = extras,
                tags = tags,
                attachments = attachments,
                history = history,
                deletedAt = o.optLong("deletedAt", 0L),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                passwordChangedAt = o.optLong("passwordChangedAt", System.currentTimeMillis()),
            )
        }

        fun listToJson(entries: List<VaultEntry>): String {
            val arr = JSONArray()
            entries.forEach { arr.put(it.toJson()) }
            return JSONObject().put("entries", arr).toString()
        }

        fun listFromJson(json: String): List<VaultEntry> {
            val arr = JSONObject(json).getJSONArray("entries")
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    }
}

/** Claves internas de categoría; el nombre visible se localiza en la UI. */
object Categories {
    const val LOGIN = "accounts"
    const val SOCIAL = "social"
    const val BANK = "bank"
    const val EMAIL = "email"
    const val WORK = "work"
    const val WIFI = "wifi"
    const val OTHER = "other"
    val ALL = listOf(LOGIN, SOCIAL, BANK, EMAIL, WORK, WIFI, OTHER)

    /** Migra los nombres en español guardados por la versión 1.0. */
    fun normalize(value: String): String = when (value) {
        in ALL -> value
        "Cuentas" -> LOGIN
        "Redes sociales" -> SOCIAL
        "Bancos y finanzas" -> BANK
        "Correo" -> EMAIL
        "Trabajo" -> WORK
        "Wi-Fi" -> WIFI
        else -> OTHER
    }
}
