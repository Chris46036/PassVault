package com.passvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passvault.app.R
import com.passvault.app.data.VaultRepository
import com.passvault.app.security.BiometricHelper
import com.passvault.app.util.PasswordStrength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(R.string.show_hide)
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun StrengthBar(password: String, modifier: Modifier = Modifier) {
    val strength = PasswordStrength.evaluate(password)
    Column(modifier) {
        LinearProgressIndicator(
            progress = { (strength.score + 1) / 5f },
            color = strength.color,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.strength_bits, stringResource(strength.labelRes), strength.entropyBits),
            style = MaterialTheme.typography.labelSmall,
            color = strength.color,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Primera ejecución: crear la contraseña maestra. */
@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Shield, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(stringResource(R.string.setup_welcome), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.setup_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        PasswordField(password, { password = it; errorRes = null }, stringResource(R.string.master_password), modifier = Modifier.fillMaxWidth())
        if (password.isNotEmpty()) {
            StrengthBar(password, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        PasswordField(confirm, { confirm = it; errorRes = null }, stringResource(R.string.setup_repeat), modifier = Modifier.fillMaxWidth())
        errorRes?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                when {
                    password.length < 8 -> errorRes = R.string.setup_min_chars
                    PasswordStrength.evaluate(password).score < 1 -> errorRes = R.string.setup_too_weak
                    password != confirm -> errorRes = R.string.setup_no_match
                    else -> {
                        working = true
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                VaultRepository.create(context, password.toCharArray())
                            }
                        }
                    }
                }
            },
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (working) CircularProgressIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.setup_create))
        }
    }
}

/** Pantalla de desbloqueo con contraseña o huella. */
@Composable
fun UnlockScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bioInvalidMsg = stringResource(R.string.unlock_bio_invalid)
    val wrongPasswordMsg = stringResource(R.string.wrong_password)
    val bioAvailable = remember { BiometricHelper.isEnabled(context) && BiometricHelper.canUseBiometric(context) }

    fun unlockWithBiometric() {
        BiometricHelper.promptUnlock(activity, onSuccess = { keyBytes ->
            if (!VaultRepository.unlockWithKeyBytes(context, keyBytes)) {
                error = bioInvalidMsg
                BiometricHelper.disable(context)
            }
        }, onFail = { msg -> error = msg })
    }

    LaunchedEffect(Unit) {
        if (bioAvailable) unlockWithBiometric()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Shield, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.unlock_locked),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        PasswordField(password, { password = it; error = null }, stringResource(R.string.master_password), isError = error != null, modifier = Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                working = true
                error = null
                scope.launch {
                    val ok = withContext(Dispatchers.Default) {
                        VaultRepository.unlock(context, password.toCharArray())
                    }
                    working = false
                    if (!ok) error = wrongPasswordMsg
                }
            },
            enabled = !working && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (working) CircularProgressIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.unlock_button))
        }
        if (bioAvailable) {
            OutlinedButton(
                onClick = { unlockWithBiometric() },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.unlock_use_fingerprint))
            }
        }
    }
}
