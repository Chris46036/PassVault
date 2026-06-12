package com.passvault.app.passkeys

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import com.passvault.app.R
import com.passvault.app.data.VaultRepository
import com.passvault.app.security.BiometricHelper
import com.passvault.app.ui.PasswordField
import com.passvault.app.ui.theme.PassVaultTheme

/** Pantalla compartida: desbloquear la bóveda dentro de un flujo de passkeys. */
private fun FragmentActivity.showUnlockUi(message: Int, onUnlocked: () -> Unit) {
    if (BiometricHelper.isEnabled(this) && BiometricHelper.canUseBiometric(this)) {
        BiometricHelper.promptUnlock(this, onSuccess = { keyBytes ->
            if (VaultRepository.unlockWithKeyBytes(this, keyBytes)) onUnlocked()
        }, onFail = { /* puede usar la contraseña abajo */ })
    }
    setContent {
        PassVaultTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                var password by remember { mutableStateOf("") }
                var error by remember { mutableStateOf(false) }
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(message),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    PasswordField(
                        value = password,
                        onValueChange = { password = it; error = false },
                        label = stringResource(R.string.master_password),
                        isError = error,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    if (error) {
                        Text(
                            stringResource(R.string.wrong_password),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Button(
                        onClick = {
                            if (VaultRepository.unlock(this@showUnlockUi, password.toCharArray())) {
                                onUnlocked()
                            } else {
                                error = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) { Text(stringResource(R.string.unlock_button)) }
                    OutlinedButton(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

/** Acción de "desbloquear" del selector de passkeys; al cerrar, el sistema re-consulta. */
class UnlockForPasskeyActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (VaultRepository.isUnlocked()) {
            setResult(RESULT_OK)
            finish()
            return
        }
        showUnlockUi(R.string.unlock_title) {
            setResult(RESULT_OK)
            finish()
        }
    }
}

/** Crea una passkey nueva cuando un sitio o app lo solicita. */
@RequiresApi(34)
class CreatePasskeyActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val providerRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val callingRequest = providerRequest?.callingRequest as? CreatePublicKeyCredentialRequest
        if (providerRequest == null || callingRequest == null) {
            finish()
            return
        }

        fun doCreate() {
            try {
                val origin = providerRequest.callingAppInfo.origin
                    ?: PasskeyCrypto.appOrigin(providerRequest.callingAppInfo)
                val created = PasskeyCrypto.createPasskey(
                    requestJson = callingRequest.requestJson,
                    clientDataHash = callingRequest.clientDataHash,
                    origin = origin,
                    packageName = providerRequest.callingAppInfo.packageName,
                )
                VaultRepository.addEntry(this, created.entry)
                val result = Intent()
                PendingIntentHandler.setCreateCredentialResponse(
                    result,
                    androidx.credentials.CreatePublicKeyCredentialResponse(created.registrationResponseJson)
                )
                setResult(RESULT_OK, result)
                Toast.makeText(this, getString(R.string.passkey_created), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                setResult(RESULT_CANCELED)
            }
            finish()
        }

        if (VaultRepository.isUnlocked()) doCreate()
        else showUnlockUi(R.string.passkey_create_unlock, ::doCreate)
    }
}

/** Firma una autenticación con una passkey existente. */
@RequiresApi(34)
class GetPasskeyActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val providerRequest = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val option = providerRequest?.credentialOptions
            ?.filterIsInstance<GetPublicKeyCredentialOption>()?.firstOrNull()
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        if (providerRequest == null || option == null || entryId == null) {
            finish()
            return
        }

        fun doSign() {
            try {
                val entry = VaultRepository.entries.firstOrNull { it.id == entryId }
                    ?: throw IllegalStateException("passkey no encontrada")
                val origin = providerRequest.callingAppInfo.origin
                    ?: PasskeyCrypto.appOrigin(providerRequest.callingAppInfo)
                val responseJson = PasskeyCrypto.signAssertion(
                    entry = entry,
                    requestJson = option.requestJson,
                    clientDataHash = option.clientDataHash,
                    origin = origin,
                    packageName = providerRequest.callingAppInfo.packageName,
                )
                val result = Intent()
                PendingIntentHandler.setGetCredentialResponse(
                    result,
                    GetCredentialResponse(PublicKeyCredential(responseJson))
                )
                setResult(RESULT_OK, result)
            } catch (e: Exception) {
                setResult(RESULT_CANCELED)
            }
            finish()
        }

        if (VaultRepository.isUnlocked()) doSign()
        else showUnlockUi(R.string.unlock_title, ::doSign)
    }
}
