package com.passvault.app.passkeys

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.passvault.app.R
import com.passvault.app.data.EntryType
import com.passvault.app.data.VaultRepository
import org.json.JSONObject

/**
 * Proveedor de passkeys del sistema (Credential Manager, Android 14+).
 * Las passkeys se guardan cifradas en la bóveda como cualquier otro secreto.
 */
@RequiresApi(34)
class PassVaultCredentialService : CredentialProviderService() {

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        if (!VaultRepository.isUnlocked()) {
            // Bóveda bloqueada: ofrece desbloquear; el sistema vuelve a consultar después
            val intent = Intent(this, UnlockForPasskeyActivity::class.java)
            val pending = PendingIntent.getActivity(
                this, 3001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            callback.onResult(
                BeginGetCredentialResponse(
                    authenticationActions = listOf(
                        AuthenticationAction(getString(R.string.unlock_title), pending)
                    )
                )
            )
            return
        }

        val entries = mutableListOf<PublicKeyCredentialEntry>()
        var requestCode = 3100
        for (option in request.beginGetCredentialOptions) {
            if (option !is BeginGetPublicKeyCredentialOption) continue
            val rpId = try {
                JSONObject(option.requestJson).optString("rpId")
            } catch (e: Exception) {
                ""
            }
            val passkeys = VaultRepository.activeEntries().filter {
                it.type == EntryType.PASSKEY && it.extras["rpId"] == rpId
            }
            for (passkey in passkeys) {
                val intent = Intent(this, GetPasskeyActivity::class.java)
                    .putExtra(GetPasskeyActivity.EXTRA_ENTRY_ID, passkey.id)
                val pending = PendingIntent.getActivity(
                    this, requestCode++, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                entries.add(
                    PublicKeyCredentialEntry.Builder(
                        this,
                        passkey.username.ifBlank { passkey.title },
                        pending,
                        option,
                    )
                        .setDisplayName(passkey.extras["userDisplayName"])
                        .build()
                )
            }
        }
        callback.onResult(BeginGetCredentialResponse(credentialEntries = entries))
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onResult(BeginCreateCredentialResponse(createEntries = emptyList()))
            return
        }
        val intent = Intent(this, CreatePasskeyActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 3002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        callback.onResult(
            BeginCreateCredentialResponse(
                createEntries = listOf(
                    CreateEntry(getString(R.string.app_name), pending)
                )
            )
        )
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }

    companion object {
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 34
    }
}
