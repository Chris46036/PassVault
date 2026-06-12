package com.passvault.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passvault.app.R
import com.passvault.app.data.Settings
import com.passvault.app.data.VaultRepository
import com.passvault.app.security.BiometricHelper
import com.passvault.app.util.CsvImporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    activity: FragmentActivity,
    onOpenTrash: () -> Unit,
) {
    val context = LocalContext.current
    var bioEnabled by remember { mutableStateOf(BiometricHelper.isEnabled(context)) }
    var autoLock by remember { mutableIntStateOf(Settings.autoLockSeconds(context)) }
    var clipClear by remember { mutableIntStateOf(Settings.clipboardClearSeconds(context)) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showClipDialog by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var importPasswordUri by remember { mutableStateOf<Uri?>(null) }
    var kdbxUri by remember { mutableStateOf<Uri?>(null) }
    var totpCopy by remember { mutableStateOf(Settings.totpAutoCopy(context)) }
    var autoBackup by remember { mutableStateOf(Settings.autoBackupUri(context)) }
    var showDisableBackup by remember { mutableStateOf(false) }
    var showNewVault by remember { mutableStateOf(false) }
    var showSwitchVault by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exportDoneMsg = stringResource(R.string.export_done)
    val importReadErrorMsg = stringResource(R.string.import_read_error)
    val importCsvInvalidMsg = stringResource(R.string.import_csv_invalid)
    val bioEnabledMsg = stringResource(R.string.set_bio_enabled)
    val autofillErrorMsg = stringResource(R.string.autofill_settings_error)
    val browserErrorMsg = stringResource(R.string.browser_error)
    val changePwdDoneMsg = stringResource(R.string.change_pwd_done)
    val importWrongPwdMsg = stringResource(R.string.import_wrong_password)
    val importInvalidMsg = stringResource(R.string.import_invalid)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(VaultRepository.exportBytes(context))
                }
                toast(exportDoneMsg)
            } catch (e: Exception) {
                toast(context.getString(R.string.export_error, e.message ?: ""))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            importPasswordUri = uri
        }
    }

    val kdbxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            kdbxUri = uri
        }
    }

    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Settings.setAutoBackupUri(context, uri.toString())
                autoBackup = uri.toString()
            } catch (_: Exception) {
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (text == null) {
                    toast(importReadErrorMsg)
                } else {
                    val parsed = CsvImporter.parse(text)
                    if (parsed == null) {
                        toast(importCsvInvalidMsg)
                    } else {
                        val added = VaultRepository.addAllNew(context, parsed)
                        toast(context.getString(R.string.import_done, added))
                    }
                }
            } catch (e: Exception) {
                toast(importCsvInvalidMsg)
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        SectionTitle(stringResource(R.string.sec_section))
        Card(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_bio))
                        Text(
                            stringResource(
                                if (BiometricHelper.canUseBiometric(context)) R.string.set_bio_sub
                                else R.string.set_bio_unavailable
                            ),
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
                                        if (ok) toast(bioEnabledMsg)
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
                    stringResource(R.string.set_autolock),
                    autoLockLabel(autoLock),
                ) { showAutoLockDialog = true }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.set_clipboard),
                    clipLabel(clipClear),
                ) { showClipDialog = true }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.set_change_master),
                    stringResource(R.string.set_change_master_sub),
                ) { showChangePassword = true }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.set_lock_now),
                    stringResource(R.string.set_lock_now_sub),
                ) { VaultRepository.lock() }
            }
        }

        if (Build.VERSION.SDK_INT >= 26) {
            SectionTitle(stringResource(R.string.autofill_section))
            Card(Modifier.fillMaxWidth()) {
                Column {
                    val afm = remember { context.getSystemService(AutofillManager::class.java) }
                    val isService = afm?.hasEnabledAutofillServices() == true
                    SettingRow(
                        stringResource(if (isService) R.string.set_autofill_active else R.string.set_autofill),
                        stringResource(R.string.set_autofill_sub),
                    ) {
                        try {
                            val intent = Intent(AndroidSettings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            activity.startActivity(intent)
                        } catch (e: Exception) {
                            toast(autofillErrorMsg)
                        }
                    }
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.set_totp_autocopy))
                            Text(
                                stringResource(R.string.set_totp_autocopy_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = totpCopy,
                            onCheckedChange = {
                                totpCopy = it
                                Settings.setTotpAutoCopy(context, it)
                            },
                        )
                    }
                    if (Build.VERSION.SDK_INT >= 34) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.set_passkeys),
                            stringResource(R.string.set_passkeys_sub),
                        ) {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_CREDENTIAL_PROVIDER).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                activity.startActivity(intent)
                            } catch (e: Exception) {
                                toast(autofillErrorMsg)
                            }
                        }
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.vaults_section))
        Card(Modifier.fillMaxWidth()) {
            Column {
                val vaults = com.passvault.app.data.Vaults.list(context)
                val activeName = vaults.firstOrNull {
                    it.id == com.passvault.app.data.Vaults.activeId(context)
                }?.name ?: "Personal"
                if (vaults.size > 1) {
                    SettingRow(
                        stringResource(R.string.set_switch_vault),
                        stringResource(R.string.vault_label, activeName),
                    ) { showSwitchVault = true }
                    HorizontalDivider()
                }
                SettingRow(
                    stringResource(R.string.set_new_vault),
                    stringResource(R.string.set_new_vault_sub),
                ) { showNewVault = true }
            }
        }

        SectionTitle(stringResource(R.string.backup_section))
        Card(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    stringResource(R.string.set_export),
                    stringResource(R.string.set_export_sub),
                ) {
                    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, "passvault-backup-$date.pvlt")
                    }
                    exportLauncher.launch(intent)
                }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.set_import),
                    stringResource(R.string.set_import_sub),
                ) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    importLauncher.launch(intent)
                }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.set_import_csv),
                    stringResource(R.string.set_import_csv_sub),
                ) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    importCsvLauncher.launch(intent)
                }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.set_import_kdbx),
                    stringResource(R.string.set_import_kdbx_sub),
                ) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    kdbxLauncher.launch(intent)
                }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.set_autobackup),
                    stringResource(if (autoBackup == null) R.string.set_autobackup_off else R.string.set_autobackup_on),
                ) {
                    if (autoBackup == null) {
                        backupFolderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                    } else {
                        showDisableBackup = true
                    }
                }
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.set_trash, VaultRepository.trashedEntries().size),
                    stringResource(R.string.set_trash_sub),
                ) { onOpenTrash() }
            }
        }

        SectionTitle(stringResource(R.string.support_section))
        Card(Modifier.fillMaxWidth()) {
            SettingRow(
                stringResource(R.string.kofi_title),
                stringResource(R.string.kofi_sub),
            ) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/chris46036"))
                    )
                } catch (e: Exception) {
                    toast(browserErrorMsg)
                }
            }
        }

        SectionTitle(stringResource(R.string.about_section))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val versionName = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                }
                Text(stringResource(R.string.about_title, versionName), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAutoLockDialog) {
        OptionsDialog(
            title = stringResource(R.string.set_autolock),
            options = listOf(
                0 to stringResource(R.string.autolock_immediately),
                60 to stringResource(R.string.autolock_1m),
                300 to stringResource(R.string.autolock_5m),
                900 to stringResource(R.string.autolock_15m),
                -1 to stringResource(R.string.never),
            ),
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
            title = stringResource(R.string.set_clipboard),
            options = listOf(
                15 to stringResource(R.string.clip_15),
                30 to stringResource(R.string.clip_30),
                60 to stringResource(R.string.clip_60),
                0 to stringResource(R.string.never),
            ),
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
                toast(changePwdDoneMsg)
            },
        )
    }
    if (showDisableBackup) {
        AlertDialog(
            onDismissRequest = { showDisableBackup = false },
            title = { Text(stringResource(R.string.autobackup_disable_q)) },
            confirmButton = {
                TextButton(onClick = {
                    Settings.setAutoBackupUri(context, null)
                    autoBackup = null
                    showDisableBackup = false
                }) { Text(stringResource(R.string.disable), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDisableBackup = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showNewVault) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewVault = false },
            title = { Text(stringResource(R.string.set_new_vault)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_vault_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        val info = com.passvault.app.data.Vaults.add(context, name.trim())
                        showNewVault = false
                        VaultRepository.switchVault(context, info.id)
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewVault = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showSwitchVault) {
        val vaults = com.passvault.app.data.Vaults.list(context)
        val activeId = com.passvault.app.data.Vaults.activeId(context)
        AlertDialog(
            onDismissRequest = { showSwitchVault = false },
            title = { Text(stringResource(R.string.set_switch_vault)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    vaults.forEach { vault ->
                        Text(
                            (if (vault.id == activeId) "●  " else "○  ") + vault.name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showSwitchVault = false
                                if (vault.id != activeId) {
                                    VaultRepository.switchVault(context, vault.id)
                                }
                            }.padding(8.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSwitchVault = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
    kdbxUri?.let { uri ->
        var kdbxPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { kdbxUri = null },
            title = { Text(stringResource(R.string.kdbx_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.kdbx_password_body))
                    PasswordField(kdbxPassword, { kdbxPassword = it }, stringResource(R.string.master_password), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val entries = context.contentResolver.openInputStream(uri)?.use {
                            com.passvault.app.util.KdbxImporter.import(it, kdbxPassword)
                        }
                        if (entries == null) {
                            toast(context.getString(R.string.kdbx_invalid))
                        } else {
                            val added = VaultRepository.addAllNew(context, entries)
                            toast(context.getString(R.string.import_done, added))
                        }
                    } catch (e: Exception) {
                        toast(context.getString(R.string.kdbx_invalid))
                    }
                    kdbxUri = null
                }) { Text(stringResource(R.string.import_button)) }
            },
            dismissButton = {
                TextButton(onClick = { kdbxUri = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    importPasswordUri?.let { uri ->
        ImportDialog(
            onDismiss = { importPasswordUri = null },
            onImport = { password ->
                try {
                    val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (data == null) {
                        toast(importReadErrorMsg)
                    } else {
                        val added = VaultRepository.importBackup(context, data, password.toCharArray())
                        if (added < 0) toast(importWrongPwdMsg)
                        else toast(context.getString(R.string.import_done, added))
                    }
                } catch (e: Exception) {
                    toast(importInvalidMsg)
                }
                importPasswordUri = null
            },
        )
    }
}

@Composable
private fun autoLockLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.autolock_immediately)
    -1 -> stringResource(R.string.never)
    60 -> stringResource(R.string.autolock_1m)
    300 -> stringResource(R.string.autolock_5m)
    else -> stringResource(R.string.autolock_15m)
}

@Composable
private fun clipLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.never)
    15 -> stringResource(R.string.clip_15)
    60 -> stringResource(R.string.clip_60)
    else -> stringResource(R.string.clip_30)
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_change_master)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it; errorRes = null }, stringResource(R.string.change_pwd_current), modifier = Modifier.fillMaxWidth())
                PasswordField(new, { new = it; errorRes = null }, stringResource(R.string.change_pwd_new), modifier = Modifier.fillMaxWidth())
                if (new.isNotEmpty()) StrengthBar(new, Modifier.fillMaxWidth())
                PasswordField(confirm, { confirm = it; errorRes = null }, stringResource(R.string.change_pwd_repeat), modifier = Modifier.fillMaxWidth())
                errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    new.length < 8 -> errorRes = R.string.setup_min_chars
                    new != confirm -> errorRes = R.string.setup_no_match
                    else -> {
                        val ok = VaultRepository.changeMasterPassword(
                            context, current.toCharArray(), new.toCharArray()
                        )
                        if (ok) onChanged() else errorRes = R.string.change_pwd_wrong
                    }
                }
            }) { Text(stringResource(R.string.change_pwd_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_dialog_body))
                PasswordField(password, { password = it }, stringResource(R.string.import_dialog_label), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(password) }) { Text(stringResource(R.string.import_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
