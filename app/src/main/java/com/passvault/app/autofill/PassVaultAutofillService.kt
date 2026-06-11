package com.passvault.app.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.passvault.app.data.Categories
import com.passvault.app.data.VaultEntry
import com.passvault.app.data.VaultRepository

class PassVaultAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        // No autorrellenar nuestra propia app
        if (structure.activityComponent?.packageName == packageName) {
            callback.onSuccess(null)
            return
        }
        val parsed = AutofillParser.parse(structure)
        if (!parsed.hasFields()) {
            callback.onSuccess(null)
            return
        }

        val response = if (VaultRepository.isUnlocked()) {
            buildUnlockedResponse(parsed)
        } else {
            buildLockedResponse(parsed)
        }
        callback.onSuccess(response)
    }

    private fun buildUnlockedResponse(parsed: ParsedStructure): FillResponse? {
        val matches = AutofillParser.matchEntries(VaultRepository.entries.toList(), parsed)
        if (matches.isEmpty()) return null
        val builder = FillResponse.Builder()
        matches.forEach { entry ->
            builder.addDataset(buildDataset(entry, parsed) ?: return@forEach)
        }
        addSaveInfo(builder, parsed)
        return builder.build()
    }

    private fun buildLockedResponse(parsed: ParsedStructure): FillResponse {
        val intent = Intent(this, AutofillAuthActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "🔒 Desbloquear PassVault")
        }
        val ids = listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()
        val builder = FillResponse.Builder()
            .setAuthentication(ids, pending.intentSender, presentation)
        addSaveInfo(builder, parsed)
        return builder.build()
    }

    private fun addSaveInfo(builder: FillResponse.Builder, parsed: ParsedStructure) {
        val ids = listOfNotNull(parsed.usernameId, parsed.passwordId)
        if (parsed.passwordId != null) {
            builder.setSaveInfo(
                SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                    ids.toTypedArray()
                ).build()
            )
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        if (!VaultRepository.isUnlocked()) {
            callback.onFailure("Desbloquea PassVault para guardar")
            return
        }
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onFailure("Sin datos")
            return
        }
        val parsed = AutofillParser.parse(structure)
        if (parsed.passwordValue.isBlank()) {
            callback.onFailure("Sin contraseña que guardar")
            return
        }
        val name = parsed.webDomain.ifBlank {
            parsed.packageName.split('.').lastOrNull { it.length > 2 } ?: parsed.packageName
        }
        VaultRepository.addEntry(
            this,
            VaultEntry(
                title = name.replaceFirstChar { it.uppercase() },
                username = parsed.usernameValue,
                password = parsed.passwordValue,
                url = parsed.webDomain,
                category = Categories.LOGIN,
            )
        )
        callback.onSuccess()
    }

    companion object {
        fun buildDataset(entry: VaultEntry, parsed: ParsedStructure): Dataset? {
            if (!parsed.hasFields()) return null
            val builder = Dataset.Builder()
            val label = entry.title.ifBlank { entry.username.ifBlank { "Sin nombre" } }
            val subtitle = entry.username
            parsed.usernameId?.let { id ->
                val rv = presentation("$label  ·  $subtitle")
                builder.setValue(id, AutofillValue.forText(entry.username), rv)
            }
            parsed.passwordId?.let { id ->
                val rv = presentation("$label  ·  $subtitle")
                builder.setValue(id, AutofillValue.forText(entry.password), rv)
            }
            return builder.build()
        }

        private fun presentation(text: String): RemoteViews =
            RemoteViews("com.passvault.app", android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "🔑 $text")
            }
    }
}
