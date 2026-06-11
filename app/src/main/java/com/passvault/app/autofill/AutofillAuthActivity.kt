package com.passvault.app.autofill

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Bundle
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passvault.app.data.VaultRepository
import com.passvault.app.security.BiometricHelper
import com.passvault.app.ui.theme.PassVaultTheme

/**
 * Pantalla de desbloqueo lanzada por el sistema de Autofill cuando la bóveda
 * está bloqueada. Al desbloquear, devuelve los datasets que encajan.
 */
class AutofillAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        if (BiometricHelper.isEnabled(this) && BiometricHelper.canUseBiometric(this)) {
            BiometricHelper.promptUnlock(this, onSuccess = { keyBytes ->
                if (VaultRepository.unlockWithKeyBytes(this, keyBytes)) finishWithDatasets()
            }, onFail = { /* el usuario puede usar la contraseña abajo */ })
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
                        Text("Desbloquear PassVault", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = false },
                            label = { Text("Contraseña maestra") },
                            visualTransformation = PasswordVisualTransformation(),
                            isError = error,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        )
                        if (error) {
                            Text(
                                "Contraseña incorrecta",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Button(
                            onClick = {
                                if (VaultRepository.unlock(this@AutofillAuthActivity, password.toCharArray())) {
                                    finishWithDatasets()
                                } else {
                                    error = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text("Desbloquear y rellenar") }
                    }
                }
            }
        }
    }

    private fun finishWithDatasets() {
        @Suppress("DEPRECATION")
        val structure: AssistStructure? =
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)
        if (structure == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val parsed = AutofillParser.parse(structure)
        val matches = AutofillParser.matchEntries(VaultRepository.entries.toList(), parsed)
        if (matches.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val builder = FillResponse.Builder()
        matches.forEach { entry ->
            PassVaultAutofillService.buildDataset(entry, parsed)?.let { builder.addDataset(it) }
        }
        val reply = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, builder.build())
        setResult(RESULT_OK, reply)
        finish()
    }
}
