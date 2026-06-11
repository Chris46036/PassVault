package com.passvault.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passvault.app.data.Settings
import com.passvault.app.data.VaultRepository
import com.passvault.app.security.BiometricHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, activity: FragmentActivity) {
    val context = LocalContext.current
    var bioEnabled by remember { mutableStateOf(BiometricHelper.isEnabled(context)) }
    var autoLock by remember { mutableIntStateOf(Settings.autoLockSeconds(context)) }
    var clipClear by remember { mutableIntStateOf(Settings.clipboardClearSeconds(context)) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showClipDialog by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf<Uri?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(VaultRepository.exportBytes(context))
                }
                toast("Copia de seguridad exportada (cifrada con tu contraseña maestra)")
            } catch (e: Exception) {
                toast("No se pudo exportar: ${e.message}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            importPassword = uri
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))

        SectionTitle("Seguridad")
        Card(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Desbloqueo con huella")
                        Text(
                            if (BiometricHelper.canUseBiometric(context)) "Usa tu biometría para abrir la bóveda"
                            else "Tu dispositivo no tiene biometría configurada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bioEnabled,
                        enabled = BiometricHelper.canUseBiometric(context),
                        onCheckedChange = { wanted ->
                            if (wanted) {
                                val keyBytes = VaultRepository.keyBytes()
                                if (keyBytes != null) {
                                    BiometricHelper.promptEnable(activity, keyBytes) { ok ->
                                        bioEnabled = ok
                                        if (ok) toast("Desbloqueo biométrico activado")
                                    }
                                }
                            } else {
                                BiometricHelper.disable(context)
                                bioEnabled = false
                            }
                        },
                    )
                }
                HorizontalDivider()
                SettingRow(
                    "Bloqueo automático",
                    autoLockLabel(autoLock),
                ) { showAutoLockDialog = true }
                HorizontalDivider()
                SettingRow(
                    "Limpiar portapapeles",
                    clipLabel(clipClear),
                ) { showClipDialog = true }
                HorizontalDivider()
                SettingRow("Cambiar contraseña maestra", "Re-cifra toda la bóveda") {
                    showChangePassword = true
                }
                HorizontalDivider()
                SettingRow("Bloquear ahora", "Cierra la bóveda inmediatamente") {
                    VaultRepository.lock()
                }
            }
        }

        SectionTitle("Autorrellenar")
        Card(Modifier.fillMaxWidth()) {
            val afm = remember { context.getSystemService(AutofillManager::class.java) }
            val isService = afm?.hasEnabledAutofillServices() == true
            SettingRow(
                if (isService) "PassVault es tu servicio de autofill ✓" else "Activar autorrellenado",
                "Rellena usuario y contraseña en otras apps y navegadores",
            ) {
                try {
                    val intent = Intent(AndroidSettings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    toast("No se pudo abrir la configuración de autofill")
                }
            }
        }

        SectionTitle("Copia de seguridad")
        Card(Modifier.fillMaxWidth()) {
            Column {
                SettingRow("Exportar bóveda cifrada", "Archivo .pvlt protegido con tu contraseña maestra") {
                    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, "passvault-backup-$date.pvlt")
                    }
                    exportLauncher.launch(intent)
                }
                HorizontalDivider()
                SettingRow("Importar copia de seguridad", "Añade las entradas de un archivo .pvlt") {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    importLauncher.launch(intent)
                }
            }
        }

        SectionTitle("Apoya el proyecto")
        Card(Modifier.fillMaxWidth()) {
            SettingRow("☕ Invítame un café en Ko-fi", "Si PassVault te resulta útil, ¡apóyame!") {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/chris46036"))
                    )
                } catch (e: Exception) {
                    toast("No se pudo abrir el navegador")
                }
            }
        }

        SectionTitle("Acerca de")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("PassVault 1.0", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Cifrado AES-256-GCM · PBKDF2 (250.000 iteraciones) · " +
                        "Todo se guarda solo en tu dispositivo. Nadie más, ni siquiera esta app, " +
                        "puede recuperar tu contraseña maestra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAutoLockDialog) {
        OptionsDialog(
            title = "Bloqueo automático",
            options = listOf(0 to "Al salir de la app", 60 to "Tras 1 minuto", 300 to "Tras 5 minutos", 900 to "Tras 15 minutos", -1 to "Nunca"),
            selected = autoLock,
            onSelect = {
                autoLock = it
                Settings.setAutoLockSeconds(context, it)
                showAutoLockDialog = false
            },
            onDismiss = { showAutoLockDialog = false },
        )
    }
    if (showClipDialog) {
        OptionsDialog(
            title = "Limpiar portapapeles",
            options = listOf(15 to "A los 15 segundos", 30 to "A los 30 segundos", 60 to "Al minuto", 0 to "Nunca"),
            selected = clipClear,
            onSelect = {
                clipClear = it
                Settings.setClipboardClearSeconds(context, it)
                showClipDialog = false
            },
            onDismiss = { showClipDialog = false },
        )
    }
    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onChanged = {
                showChangePassword = false
                BiometricHelper.disable(context)
                bioEnabled = false
                toast("Contraseña maestra actualizada. Vuelve a activar la biometría si la usabas.")
            },
        )
    }
    importPassword?.let { uri ->
        ImportDialog(
            onDismiss = { importPassword = null },
            onImport = { password ->
                try {
                    val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (data == null) {
                        toast("No se pudo leer el archivo")
                    } else {
                        val added = VaultRepository.importBackup(context, data, password.toCharArray())
                        if (added < 0) toast("Contraseña incorrecta para esa copia")
                        else toast("Importadas $added entradas nuevas")
                    }
                } catch (e: Exception) {
                    toast("Archivo de copia no válido")
                }
                importPassword = null
            },
        )
    }
}

private fun autoLockLabel(seconds: Int) = when (seconds) {
    0 -> "Al salir de la app"
    -1 -> "Nunca"
    60 -> "Tras 1 minuto"
    else -> "Tras ${seconds / 60} minutos"
}

private fun clipLabel(seconds: Int) = when (seconds) {
    0 -> "Nunca"
    60 -> "Al minuto"
    else -> "A los $seconds segundos"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
    ) {
        Text(title)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionsDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (value, label) ->
                    Text(
                        (if (value == selected) "●  " else "○  ") + label,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña maestra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it; error = null }, "Contraseña actual", modifier = Modifier.fillMaxWidth())
                PasswordField(new, { new = it; error = null }, "Nueva contraseña", modifier = Modifier.fillMaxWidth())
                if (new.isNotEmpty()) StrengthBar(new, Modifier.fillMaxWidth())
                PasswordField(confirm, { confirm = it; error = null }, "Repite la nueva", modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    new.length < 8 -> error = "Usa al menos 8 caracteres"
                    new != confirm -> error = "Las contraseñas no coinciden"
                    else -> {
                        val ok = VaultRepository.changeMasterPassword(
                            context, current.toCharArray(), new.toCharArray()
                        )
                        if (ok) onChanged() else error = "La contraseña actual no es correcta"
                    }
                }
            }) { Text("Cambiar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar copia de seguridad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Introduce la contraseña maestra con la que se creó esa copia.")
                PasswordField(password, { password = it }, "Contraseña de la copia", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(password) }) { Text("Importar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
