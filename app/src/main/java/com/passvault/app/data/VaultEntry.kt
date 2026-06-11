package com.passvault.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val category: String = Categories.OTHER,
    val favorite: Boolean = false,
    val totpSecret: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val passwordChangedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("username", username)
        put("password", password)
        put("url", url)
        put("notes", notes)
        put("category", category)
        put("favorite", favorite)
        put("totpSecret", totpSecret)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("passwordChangedAt", passwordChangedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = VaultEntry(
            id = o.optString("id", UUID.randomUUID().toString()),
            title = o.optString("title"),
            username = o.optString("username"),
            password = o.optString("password"),
            url = o.optString("url"),
            notes = o.optString("notes"),
            category = o.optString("category", Categories.OTHER),
            favorite = o.optBoolean("favorite", false),
            totpSecret = o.optString("totpSecret"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            passwordChangedAt = o.optLong("passwordChangedAt", System.currentTimeMillis()),
        )

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

object Categories {
    const val LOGIN = "Cuentas"
    const val SOCIAL = "Redes sociales"
    const val BANK = "Bancos y finanzas"
    const val EMAIL = "Correo"
    const val WORK = "Trabajo"
    const val WIFI = "Wi-Fi"
    const val OTHER = "Otros"
    val ALL = listOf(LOGIN, SOCIAL, BANK, EMAIL, WORK, WIFI, OTHER)
}
