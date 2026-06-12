package com.passvault.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VaultInfo(val id: String, val name: String)

/** Registro de bóvedas (Personal, Trabajo…). Cada una es un archivo cifrado independiente. */
object Vaults {

    const val DEFAULT_ID = "default"
    private const val PREFS = "vaults"
    private const val KEY_REGISTRY = "registry"
    private const val KEY_ACTIVE = "active"

    fun list(context: Context): List<VaultInfo> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REGISTRY, null)
            ?: return listOf(VaultInfo(DEFAULT_ID, "Personal"))
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                VaultInfo(o.getString("id"), o.getString("name"))
            }.ifEmpty { listOf(VaultInfo(DEFAULT_ID, "Personal")) }
        } catch (e: Exception) {
            listOf(VaultInfo(DEFAULT_ID, "Personal"))
        }
    }

    private fun save(context: Context, vaults: List<VaultInfo>) {
        val arr = JSONArray()
        vaults.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_REGISTRY, arr.toString()).apply()
    }

    fun add(context: Context, name: String): VaultInfo {
        val info = VaultInfo(UUID.randomUUID().toString().take(8), name)
        save(context, list(context) + info)
        return info
    }

    fun activeId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, DEFAULT_ID) ?: DEFAULT_ID

    fun setActive(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE, id).apply()
    }

    fun fileName(id: String): String =
        if (id == DEFAULT_ID) "vault.enc" else "vault_$id.enc"
}
