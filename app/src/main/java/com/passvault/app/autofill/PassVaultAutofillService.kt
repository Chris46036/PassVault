package com.passvault.app.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.passvault.app.R
import com.passvault.app.data.Categories
import com.passvault.app.data.EntryType
import com.passvault.app.data.VaultEntry
import com.passvault.app.data.VaultRepository
import com.passvault.app.util.DomainUtil

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
        if (!parsed.hasFields() && !parsed.hasCardFields()) {
            callback.onSuccess(null)
            return
        }

        val inlineSpecs: List<InlinePresentationSpec> =
            if (Build.VERSION.SDK_INT >= 30) {
                request.inlineSuggestionsRequest?.inlinePresentationSpecs ?: emptyList()
            } else {
                emptyList()
            }

        val response = if (VaultRepository.isUnlocked()) {
            buildUnlockedResponse(parsed, inlineSpecs)
        } else {
            buildLockedResponse(parsed, inlineSpecs)
        }
        callback.onSuccess(response)
    }

    private fun buildUnlockedResponse(
        parsed: ParsedStructure,
        inlineSpecs: List<InlinePresentationSpec>,
    ): FillResponse? {
        val matches = if (parsed.hasFields()) {
            AutofillParser.matchEntries(VaultRepository.entries.toList(), parsed)
        } else {
            AutofillParser.matchCards(VaultRepository.entries.toList())
        }
        val builder = FillResponse.Builder()
        var hasContent = false
        matches.forEachIndexed { i, entry ->
            val dataset = buildStubDataset(
                this, entry, parsed, inlineSpecs.getOrNull(i)
            ) ?: return@forEachIndexed
            builder.addDataset(dataset)
            hasContent = true
        }
        val saveAdded = addSaveInfo(builder, parsed)
        if (!hasContent && !saveAdded) return null
        return builder.build()
    }

    private fun buildLockedResponse(
        parsed: ParsedStructure,
        inlineSpecs: List<InlinePresentationSpec>,
    ): FillResponse {
        val intent = Intent(this, AutofillAuthActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val text = getString(R.string.autofill_unlock_to_fill)
        val presentation = simplePresentation(this, text)
        val ids = listOfNotNull(
            parsed.usernameId, parsed.passwordId,
            parsed.cardNumberId, parsed.cardCvvId, parsed.cardExpiryId, parsed.cardHolderId,
        ).toTypedArray()
        val builder = FillResponse.Builder()
        if (Build.VERSION.SDK_INT >= 30 && inlineSpecs.isNotEmpty()) {
            val inline = inlinePresentation(this, text, inlineSpecs.first())
            if (inline != null) {
                builder.setAuthentication(ids, pending.intentSender, presentation, inline)
            } else {
                builder.setAuthentication(ids, pending.intentSender, presentation)
            }
        } else {
            builder.setAuthentication(ids, pending.intentSender, presentation)
        }
        addSaveInfo(builder, parsed)
        return builder.build()
    }

    /** Pide al sistema que ofrezca guardar credenciales nuevas. */
    private fun addSaveInfo(builder: FillResponse.Builder, parsed: ParsedStructure): Boolean {
        if (parsed.passwordId == null) return false
        val ids = listOfNotNull(parsed.usernameId, parsed.passwordId)
        val saveBuilder = SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
            ids.toTypedArray()
        )
        // En navegadores el formulario puede desaparecer sin "commit" explícito
        if (parsed.webDomain.isNotBlank()) {
            saveBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        }
        builder.setSaveInfo(saveBuilder.build())
        return true
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onFailure(getString(R.string.autofill_no_data))
            return
        }
        val parsed = AutofillParser.parse(structure)
        if (parsed.passwordValue.isBlank()) {
            callback.onFailure(getString(R.string.autofill_nothing_to_save))
            return
        }

        if (VaultRepository.isUnlocked()) {
            saveCredential(this, parsed)
            callback.onSuccess()
        } else {
            // Bóveda bloqueada: abre una pantalla para desbloquear y guardar
            val intent = Intent(this, SavePromptActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(SavePromptActivity.EXTRA_TITLE, suggestedTitle(parsed))
                putExtra(SavePromptActivity.EXTRA_USERNAME, parsed.usernameValue)
                putExtra(SavePromptActivity.EXTRA_PASSWORD, parsed.passwordValue)
                putExtra(SavePromptActivity.EXTRA_URL, parsed.webDomain)
            }
            startActivity(intent)
            callback.onSuccess()
        }
    }

    companion object {

        fun suggestedTitle(parsed: ParsedStructure): String {
            val name = DomainUtil.registrableDomain(parsed.webDomain).ifBlank {
                parsed.packageName.split('.').lastOrNull { it.length > 2 } ?: parsed.packageName
            }
            return name.substringBefore('.').replaceFirstChar { it.uppercase() }
        }

        fun saveCredential(context: Context, parsed: ParsedStructure) {
            // Si ya existe la misma credencial, actualiza la contraseña en vez de duplicar
            val existing = VaultRepository.activeEntries().firstOrNull {
                it.username.equals(parsed.usernameValue, ignoreCase = true) &&
                    parsed.webDomain.isNotBlank() &&
                    DomainUtil.matches(it.url, parsed.webDomain)
            }
            if (existing != null) {
                if (existing.password != parsed.passwordValue) {
                    VaultRepository.updateEntry(
                        context,
                        existing.copy(
                            password = parsed.passwordValue,
                            updatedAt = System.currentTimeMillis(),
                            passwordChangedAt = System.currentTimeMillis(),
                        )
                    )
                }
            } else {
                VaultRepository.addEntry(
                    context,
                    VaultEntry(
                        type = EntryType.LOGIN,
                        title = suggestedTitle(parsed),
                        username = parsed.usernameValue,
                        password = parsed.passwordValue,
                        url = parsed.webDomain,
                        category = Categories.LOGIN,
                    )
                )
            }
        }

        fun simplePresentation(context: Context, text: String): RemoteViews =
            RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, text)
            }

        /** Sugerencia para mostrar dentro del teclado (Android 11+). */
        fun inlinePresentation(
            context: Context,
            text: String,
            spec: InlinePresentationSpec?,
        ): android.service.autofill.InlinePresentation? {
            if (Build.VERSION.SDK_INT < 30 || spec == null) return null
            return try {
                val attribution = PendingIntent.getActivity(
                    context, 2001,
                    Intent(context, com.passvault.app.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val content = InlineSuggestionUi.newContentBuilder(attribution)
                    .setTitle(text)
                    .build()
                android.service.autofill.InlinePresentation(content.slice, spec, false)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Dataset autenticado "hueco": al elegirlo se lanza AutofillSelectActivity,
         * que devuelve las credenciales reales y copia el código 2FA si lo hay.
         */
        private fun entryLabel(context: Context, entry: VaultEntry): String {
            val label = entry.title.ifBlank { entry.username.ifBlank { "—" } }
            return if (entry.type == EntryType.CARD) {
                val digits = (entry.extras["number"] ?: "").filter { it.isDigit() }
                "💳 $label · ····${digits.takeLast(4)}"
            } else if (entry.username.isBlank()) {
                "🔑 $label"
            } else {
                "🔑 $label · ${entry.username}"
            }
        }

        fun buildStubDataset(
            context: Context,
            entry: VaultEntry,
            parsed: ParsedStructure,
            inlineSpec: InlinePresentationSpec? = null,
        ): Dataset? {
            if (!parsed.hasFields() && !parsed.hasCardFields()) return null
            val builder = Dataset.Builder()
            val display = entryLabel(context, entry)
            val rv = simplePresentation(context, display)
            val inline = inlinePresentation(context, entry.title.ifBlank { "—" }, inlineSpec)

            val intent = Intent(context, AutofillSelectActivity::class.java)
                .putExtra(AutofillSelectActivity.EXTRA_ENTRY_ID, entry.id)
                .putExtra(AutofillSelectActivity.EXTRA_USERNAME_ID, parsed.usernameId)
                .putExtra(AutofillSelectActivity.EXTRA_PASSWORD_ID, parsed.passwordId)
                .putExtra(AutofillSelectActivity.EXTRA_CARD_NUMBER_ID, parsed.cardNumberId)
                .putExtra(AutofillSelectActivity.EXTRA_CARD_CVV_ID, parsed.cardCvvId)
                .putExtra(AutofillSelectActivity.EXTRA_CARD_EXPIRY_ID, parsed.cardExpiryId)
                .putExtra(AutofillSelectActivity.EXTRA_CARD_HOLDER_ID, parsed.cardHolderId)
            val pending = PendingIntent.getActivity(
                context, entry.id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            fun setPlaceholder(id: android.view.autofill.AutofillId) {
                if (Build.VERSION.SDK_INT >= 30 && inline != null) {
                    builder.setValue(id, null, rv, inline)
                } else {
                    builder.setValue(id, null, rv)
                }
            }
            val ids = if (entry.type == EntryType.CARD) {
                listOfNotNull(parsed.cardNumberId, parsed.cardCvvId, parsed.cardExpiryId, parsed.cardHolderId)
            } else {
                listOfNotNull(parsed.usernameId, parsed.passwordId)
            }
            if (ids.isEmpty()) return null
            ids.forEach { setPlaceholder(it) }
            builder.setAuthentication(pending.intentSender)
            return builder.build()
        }

        fun buildDataset(
            context: Context,
            entry: VaultEntry,
            parsed: ParsedStructure,
            inlineSpec: InlinePresentationSpec? = null,
        ): Dataset? {
            if (!parsed.hasFields() && !parsed.hasCardFields()) return null
            val builder = Dataset.Builder()
            val display = entryLabel(context, entry)
            val rv = simplePresentation(context, display)
            val inline = inlinePresentation(context, entry.title.ifBlank { "—" }, inlineSpec)

            var filledAny = false
            fun set(id: android.view.autofill.AutofillId?, value: String) {
                if (id == null || value.isBlank()) return
                filledAny = true
                if (Build.VERSION.SDK_INT >= 30 && inline != null) {
                    builder.setValue(id, AutofillValue.forText(value), rv, inline)
                } else {
                    builder.setValue(id, AutofillValue.forText(value), rv)
                }
            }
            if (entry.type == EntryType.CARD) {
                set(parsed.cardNumberId, (entry.extras["number"] ?: "").filter { it.isDigit() })
                set(parsed.cardCvvId, entry.extras["cvv"] ?: "")
                set(parsed.cardExpiryId, entry.extras["expiry"] ?: "")
                set(parsed.cardHolderId, entry.extras["holder"] ?: "")
            } else {
                set(parsed.usernameId, entry.username)
                set(parsed.passwordId, entry.password)
            }
            return if (filledAny) builder.build() else null
        }
    }
}
