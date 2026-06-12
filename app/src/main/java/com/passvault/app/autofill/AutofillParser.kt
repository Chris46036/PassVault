package com.passvault.app.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import com.passvault.app.data.EntryType
import com.passvault.app.data.VaultEntry
import com.passvault.app.util.DomainUtil

/** Campos de credenciales o de tarjeta detectados en la pantalla de otra app. */
data class ParsedStructure(
    val usernameId: AutofillId? = null,
    val passwordId: AutofillId? = null,
    val usernameValue: String = "",
    val passwordValue: String = "",
    val cardNumberId: AutofillId? = null,
    val cardCvvId: AutofillId? = null,
    val cardExpiryId: AutofillId? = null,
    val cardHolderId: AutofillId? = null,
    val webDomain: String = "",
    val packageName: String = "",
) {
    fun hasFields() = usernameId != null || passwordId != null
    fun hasCardFields() = cardNumberId != null || cardCvvId != null
}

object AutofillParser {

    fun parse(structure: AssistStructure): ParsedStructure {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameValue = ""
        var passwordValue = ""
        var cardNumberId: AutofillId? = null
        var cardCvvId: AutofillId? = null
        var cardExpiryId: AutofillId? = null
        var cardHolderId: AutofillId? = null
        var webDomain = ""
        val packageName = structure.activityComponent?.packageName ?: ""

        fun visit(node: AssistStructure.ViewNode) {
            node.webDomain?.let { if (it.isNotBlank()) webDomain = it }
            val id = node.autofillId
            if (id != null) {
                val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
                val idEntry = (node.idEntry ?: "").lowercase()
                val hintText = (node.hint ?: "").lowercase()
                val inputType = node.inputType

                val isPasswordInput =
                    inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD == InputType.TYPE_NUMBER_VARIATION_PASSWORD

                val looksPassword = View.AUTOFILL_HINT_PASSWORD in hints ||
                    isPasswordInput ||
                    idEntry.contains("pass") || hintText.contains("contraseña") || hintText.contains("password")

                val looksUsername = View.AUTOFILL_HINT_USERNAME in hints ||
                    View.AUTOFILL_HINT_EMAIL_ADDRESS in hints ||
                    idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("login") ||
                    hintText.contains("usuario") || hintText.contains("correo") || hintText.contains("email")

                val looksCardNumber = View.AUTOFILL_HINT_CREDIT_CARD_NUMBER in hints ||
                    idEntry.contains("card_number") || idEntry.contains("cardnumber") ||
                    idEntry.contains("cc-number") || idEntry.contains("cc_number") ||
                    hintText.contains("número de tarjeta") || hintText.contains("card number")

                val looksCvv = View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE in hints ||
                    idEntry.contains("cvv") || idEntry.contains("cvc") ||
                    idEntry.contains("security_code") || hintText.contains("cvv")

                val looksExpiry = View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE in hints ||
                    idEntry.contains("expir") || idEntry.contains("cc-exp") ||
                    hintText.contains("vencimiento") || hintText.contains("expiry")

                val looksHolder = idEntry.contains("cardholder") || idEntry.contains("card_holder") ||
                    idEntry.contains("cc-name") || hintText.contains("titular")

                when {
                    looksCardNumber && cardNumberId == null -> cardNumberId = id
                    looksCvv && cardCvvId == null -> cardCvvId = id
                    looksExpiry && cardExpiryId == null -> cardExpiryId = id
                    looksHolder && cardHolderId == null -> cardHolderId = id
                    looksPassword && passwordId == null -> {
                        passwordId = id
                        node.autofillValue?.let { if (it.isText) passwordValue = it.textValue.toString() }
                    }
                    looksUsername && usernameId == null -> {
                        usernameId = id
                        node.autofillValue?.let { if (it.isText) usernameValue = it.textValue.toString() }
                    }
                }
            }
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) {
            visit(structure.getWindowNodeAt(i).rootViewNode)
        }
        return ParsedStructure(
            usernameId, passwordId, usernameValue, passwordValue,
            cardNumberId, cardCvvId, cardExpiryId, cardHolderId,
            webDomain, packageName,
        )
    }

    /**
     * Entradas que encajan con la app/web detectada. En páginas web la
     * comparación es estricta por dominio registrable (anti-phishing): si no
     * hay coincidencia exacta, no se ofrece nada.
     */
    fun matchEntries(entries: List<VaultEntry>, parsed: ParsedStructure): List<VaultEntry> {
        val active = entries.filter { !it.isDeleted && it.password.isNotBlank() }
        val domain = parsed.webDomain.lowercase()

        if (domain.isNotBlank()) {
            return active.filter { DomainUtil.matches(it.url, domain) }
                .sortedByDescending { it.favorite }
                .take(8)
        }

        // App nativa: coincide por tokens del nombre de paquete contra la URL o el título
        val pkg = parsed.packageName.lowercase()
        val pkgTokens = pkg.split('.')
            .filter { it.length > 3 && it !in setOf("android", "google", "mobile", "free") }
        return active.filter { e ->
            val urlDomain = DomainUtil.registrableDomain(e.url)
            val title = e.title.lowercase()
            pkgTokens.any { t -> urlDomain.startsWith(t) || title == t || title.contains(t) }
        }.sortedByDescending { it.favorite }.take(8)
    }

    /** Tarjetas guardadas que se pueden ofrecer en un formulario de pago. */
    fun matchCards(entries: List<VaultEntry>): List<VaultEntry> =
        entries.filter {
            !it.isDeleted && it.type == EntryType.CARD && !it.extras["number"].isNullOrBlank()
        }.sortedByDescending { it.favorite }.take(8)
}
