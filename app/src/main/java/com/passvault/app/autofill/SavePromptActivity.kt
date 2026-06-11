package com.passvault.app.autofill

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
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
import androidx.fragment.app.FragmentActivity
import com.passvault.app.R
import com.passvault.app.data.VaultRepository
import com.passvault.app.security.BiometricHelper
import com.passvault.app.ui.PasswordField
import com.passvault.app.ui.theme.PassVaultTheme

/**
 * Se abre cuando el sistema detecta credenciales nuevas pero la bóveda está
 * bloqueada: pide desbloquear y las guarda.
 */
class SavePromptActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val credTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val credUser = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        val credPass = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val credUrl = intent.getStringExtra(EXTRA_URL) ?: ""

        fun saveAndFinish() {
            val parsed = ParsedStructure(
                usernameValue = credUser,
                passwordValue = credPass,
                webDomain = credUrl,
            )
            PassVaultAutofillService.saveCredential(this, parsed)
            Toast.makeText(this, getString(R.string.save_prompt_saved), Toast.LENGTH_LONG).show()
            finish()
        }

        if (VaultRepository.isUnlocked()) {
            saveAndFinish()
            return
        }

        if (BiometricHelper.isEnabled(this) && BiometricHelper.canUseBiometric(this)) {
            BiometricHelper.promptUnlock(this, onSuccess = { keyBytes ->
                if (VaultRepository.unlockWithKeyBytes(this, keyBytes)) saveAndFinish()
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
                            stringResource(R.string.save_prompt_title),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.save_prompt_body, credTitle.ifBlank { credUrl }, credUser),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        PasswordField(
                            value = password,
                            onValueChange = { password = it; error = false },
                            label = stringResource(R.string.master_password),
                            isError = error,
                            modifier = Modifier.fillMaxWidth(),
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
                                if (VaultRepository.unlock(this@SavePromptActivity, password.toCharArray())) {
                                    saveAndFinish()
                                } else {
                                    error = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text(stringResource(R.string.save_prompt_unlock_and_save)) }
                        OutlinedButton(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }
}
