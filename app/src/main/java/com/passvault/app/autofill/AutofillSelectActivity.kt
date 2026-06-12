package com.passvault.app.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.Toast
import com.passvault.app.R
import com.passvault.app.data.Settings
import com.passvault.app.data.VaultRepository
import com.passvault.app.util.ClipboardUtil
import com.passvault.app.util.Totp

/**
 * Se ejecuta (sin interfaz) cuando el usuario elige una sugerencia de
 * autofill: devuelve el dataset con las credenciales reales y, si la entrada
 * tiene 2FA, copia el código actual al portapapeles.
 */
class AutofillSelectActivity : Activity() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_USERNAME_ID = "username_id"
        const val EXTRA_PASSWORD_ID = "password_id"
        const val EXTRA_CARD_NUMBER_ID = "card_number_id"
        const val EXTRA_CARD_CVV_ID = "card_cvv_id"
        const val EXTRA_CARD_EXPIRY_ID = "card_expiry_id"
        const val EXTRA_CARD_HOLDER_ID = "card_holder_id"
    }

    @Suppress("DEPRECATION")
    private fun id(name: String): AutofillId? = intent.getParcelableExtra(name)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        val entry = VaultRepository.entries.firstOrNull { it.id == entryId }
        if (entry == null || !VaultRepository.isUnlocked()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val parsed = ParsedStructure(
            usernameId = id(EXTRA_USERNAME_ID),
            passwordId = id(EXTRA_PASSWORD_ID),
            cardNumberId = id(EXTRA_CARD_NUMBER_ID),
            cardCvvId = id(EXTRA_CARD_CVV_ID),
            cardExpiryId = id(EXTRA_CARD_EXPIRY_ID),
            cardHolderId = id(EXTRA_CARD_HOLDER_ID),
        )
        val dataset = PassVaultAutofillService.buildDataset(this, entry, parsed)
        if (dataset == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        if (Settings.totpAutoCopy(this) && entry.totpSecret.isNotBlank()) {
            Totp.currentCode(entry.totpSecret)?.let { code ->
                ClipboardUtil.copySensitive(
                    this, "2fa", code, Settings.clipboardClearSeconds(this)
                )
                Toast.makeText(this, getString(R.string.totp_copied), Toast.LENGTH_LONG).show()
            }
        }

        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }
}
