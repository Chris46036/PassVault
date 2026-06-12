package com.passvault.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.passvault.app.R
import com.passvault.app.data.Categories
import com.passvault.app.data.EntryType
import com.passvault.app.data.Settings
import com.passvault.app.data.VaultEntry
import com.passvault.app.data.VaultRepository
import com.passvault.app.util.ClipboardUtil
import com.passvault.app.util.CsvImporter
import com.passvault.app.util.Totp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry: VaultEntry,
    onBack: () -> Unit,
    onEdit: (VaultEntry) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val clipSeconds = remember { Settings.clipboardClearSeconds(context) }
    var showPassword by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    // Lee la versión viva por si se editó
    val live = VaultRepository.entries.firstOrNull { it.id == entry.id } ?: entry

    fun copy(label: String, value: String) =
        ClipboardUtil.copySensitive(context, label, value, clipSeconds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(live.title.ifBlank { stringResource(R.string.untitled) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        VaultRepository.updateEntry(context, live.copy(favorite = !live.favorite))
                    }) {
                        Icon(
                            if (live.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (live.favorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(live) }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.edit))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (live.type) {
                EntryType.CARD -> CardDetail(live, showPassword, { showPassword = it }, ::copy)
                EntryType.IDENTITY -> IdentityDetail(live, ::copy)
                EntryType.NOTE -> {}
                EntryType.PASSKEY -> {
                    if (live.username.isNotBlank()) {
                        DetailField(stringResource(R.string.field_username), live.username, onCopy = null)
                    }
                    DetailField(stringResource(R.string.field_website), live.url, onCopy = null)
                    DetailField(typeLabel(live.type), live.extras["userDisplayName"] ?: "", onCopy = null)
                }
                else -> LoginDetail(live, showPassword, { showPassword = it }, ::copy)
            }

            if (live.attachments.isNotEmpty()) {
                AttachmentsCard(live)
            }
            if (live.tags.isNotEmpty()) {
                Text(
                    live.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (live.notes.isNotBlank()) {
                DetailField(stringResource(R.string.field_notes), live.notes, onCopy = null)
            }

            if (live.history.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { historyExpanded = !historyExpanded }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.history_title, live.history.size),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (historyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                            )
                        }
                        if (historyExpanded) {
                            live.history.forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.password,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        )
                                        Text(
                                            DateFormat.getDateInstance().format(Date(item.changedAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { copy("password", item.password) }) {
                                        Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.detail_category, categoryLabel(live.category)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (live.type == EntryType.LOGIN && live.password.isNotBlank()) {
                val days = (System.currentTimeMillis() - live.passwordChangedAt) / 86_400_000L
                Text(
                    stringResource(R.string.detail_pwd_age, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body, live.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    VaultRepository.trashEntry(context, live.id)
                    onDeleted()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LoginDetail(
    live: VaultEntry,
    showPassword: Boolean,
    onToggleShow: (Boolean) -> Unit,
    copy: (String, String) -> Unit,
) {
    val context = LocalContext.current
    if (live.username.isNotBlank()) {
        DetailField(stringResource(R.string.field_username), live.username, onCopy = { copy("user", live.username) })
    }
    if (live.password.isNotBlank()) {
        DetailField(
            stringResource(R.string.field_password),
            if (showPassword) live.password else "•".repeat(live.password.length.coerceAtMost(16)),
            mono = true,
            onCopy = { copy("password", live.password) },
            extraAction = {
                IconButton(onClick = { onToggleShow(!showPassword) }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.show_hide),
                    )
                }
            },
        )
    }
    if (live.totpSecret.isNotBlank()) {
        TotpCard(live.totpSecret) { code -> copy("2fa", code) }
    }
    if (live.url.isNotBlank()) {
        DetailField(stringResource(R.string.field_website), live.url, onCopy = { copy("url", live.url) }, extraAction = {
            IconButton(onClick = {
                val url = if (live.url.startsWith("http")) live.url else "https://${live.url}"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open)) }
        })
    }
}

@Composable
private fun CardDetail(
    live: VaultEntry,
    showSecret: Boolean,
    onToggleShow: (Boolean) -> Unit,
    copy: (String, String) -> Unit,
) {
    val number = live.extras["number"] ?: ""
    val holder = live.extras["holder"] ?: ""
    val expiry = live.extras["expiry"] ?: ""
    val cvv = live.extras["cvv"] ?: ""
    if (number.isNotBlank()) {
        DetailField(
            stringResource(R.string.card_number),
            if (showSecret) number else maskCardNumber(number),
            mono = true,
            onCopy = { copy("card", number.filter { it.isDigit() }) },
            extraAction = {
                IconButton(onClick = { onToggleShow(!showSecret) }) {
                    Icon(
                        if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.show_hide),
                    )
                }
            },
        )
    }
    if (holder.isNotBlank()) {
        DetailField(stringResource(R.string.card_holder), holder, onCopy = { copy("holder", holder) })
    }
    if (expiry.isNotBlank()) {
        DetailField(stringResource(R.string.card_expiry), expiry, onCopy = { copy("expiry", expiry) })
    }
    if (cvv.isNotBlank()) {
        DetailField(
            stringResource(R.string.card_cvv),
            if (showSecret) cvv else "•••",
            mono = true,
            onCopy = { copy("cvv", cvv) },
        )
    }
}

@Composable
private fun IdentityDetail(live: VaultEntry, copy: (String, String) -> Unit) {
    listOf(
        R.string.id_fullname to "fullName",
        R.string.id_document to "document",
        R.string.id_phone to "phone",
        R.string.id_address to "address",
    ).forEach { (labelRes, key) ->
        val value = live.extras[key] ?: ""
        if (value.isNotBlank()) {
            DetailField(stringResource(labelRes), value, onCopy = { copy(key, value) })
        }
    }
}

/** Lista de adjuntos cifrados con exportación vía SAF. */
@Composable
private fun AttachmentsCard(entry: com.passvault.app.data.VaultEntry) {
    val context = LocalContext.current
    var exporting by remember { mutableStateOf<com.passvault.app.data.Attachment?>(null) }
    val exportedMsg = stringResource(R.string.attachment_exported)
    val exportLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val att = exporting
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null && att != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(android.util.Base64.decode(att.dataB64, android.util.Base64.NO_WRAP))
                }
                Toast.makeText(context, exportedMsg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
        exporting = null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.attachments_title, entry.attachments.size),
                style = MaterialTheme.typography.titleSmall,
            )
            entry.attachments.forEach { att ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AttachFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(att.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "%.1f KB".format(att.sizeBytes / 1024f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        exporting = att
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_TITLE, att.name)
                        }
                        exportLauncher.launch(intent)
                    }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    mono: Boolean = false,
    onCopy: (() -> Unit)?,
    extraAction: (@Composable () -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyLarge,
                )
            }
            extraAction?.invoke()
            if (onCopy != null) {
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy)) }
            }
        }
    }
}

/** Código 2FA en vivo con cuenta atrás. */
@Composable
private fun TotpCard(secret: String, onCopy: (String) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(secret) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val code = Totp.currentCode(secret, now)
    val remaining = Totp.secondsRemaining(now)

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.field_totp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    code?.chunked(3)?.joinToString(" ") ?: stringResource(R.string.totp_invalid),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (remaining <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            CircularProgressIndicator(
                progress = { remaining / 30f },
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.seconds_short, remaining), style = MaterialTheme.typography.labelSmall)
            if (code != null) {
                IconButton(onClick = { onCopy(code) }) {
                    Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_code))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    entry: VaultEntry?,
    onDone: (VaultEntry?) -> Unit,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(entry?.type ?: EntryType.LOGIN) }
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var username by remember { mutableStateOf(entry?.username ?: "") }
    var password by remember { mutableStateOf(entry?.password ?: "") }
    var url by remember { mutableStateOf(entry?.url ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var category by remember { mutableStateOf(entry?.category ?: Categories.LOGIN) }
    var totpSecret by remember { mutableStateOf(entry?.totpSecret ?: "") }
    var cardNumber by remember { mutableStateOf(entry?.extras?.get("number") ?: "") }
    var cardHolder by remember { mutableStateOf(entry?.extras?.get("holder") ?: "") }
    var cardExpiry by remember { mutableStateOf(entry?.extras?.get("expiry") ?: "") }
    var cardCvv by remember { mutableStateOf(entry?.extras?.get("cvv") ?: "") }
    var idFullName by remember { mutableStateOf(entry?.extras?.get("fullName") ?: "") }
    var idDocument by remember { mutableStateOf(entry?.extras?.get("document") ?: "") }
    var idPhone by remember { mutableStateOf(entry?.extras?.get("phone") ?: "") }
    var idAddress by remember { mutableStateOf(entry?.extras?.get("address") ?: "") }
    var tagsText by remember { mutableStateOf(entry?.tags?.joinToString(", ") ?: "") }
    var attachments by remember { mutableStateOf(entry?.attachments ?: emptyList()) }
    var showPassword by remember { mutableStateOf(entry == null) }
    var showGenerator by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val qrNoSecretMsg = stringResource(R.string.qr_no_secret)
    val scanQrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val content = result.contents
        if (content != null) {
            val secret = CsvImporter.extractTotpSecret(content)
            if (secret.isNotBlank() && Totp.isValidSecret(secret)) {
                totpSecret = secret
            } else {
                Toast.makeText(context, qrNoSecretMsg, Toast.LENGTH_LONG).show()
            }
        }
    }
    val scanPrompt = stringResource(R.string.scan_qr_prompt)

    val tooBigMsg = stringResource(R.string.attachment_too_big)
    val attachLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.size > 4 * 1024 * 1024) {
                    Toast.makeText(context, tooBigMsg, Toast.LENGTH_LONG).show()
                } else {
                    var name = "adjunto"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
                    }
                    attachments = attachments + com.passvault.app.data.Attachment(
                        name, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (entry == null) R.string.entry_new else R.string.entry_edit)) },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entry == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryType.ALL.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (t == EntryType.CARD && category == Categories.LOGIN) category = Categories.BANK
                            },
                            label = { Text(typeLabel(t)) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it; showError = false },
                label = { Text(stringResource(R.string.entry_name_label)) }, singleLine = true,
                isError = showError,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showError) {
                Text(
                    stringResource(R.string.entry_name_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            when (type) {
                EntryType.CARD -> {
                    OutlinedTextField(
                        value = cardNumber, onValueChange = { cardNumber = it },
                        label = { Text(stringResource(R.string.card_number)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cardHolder, onValueChange = { cardHolder = it },
                        label = { Text(stringResource(R.string.card_holder)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = cardExpiry, onValueChange = { cardExpiry = it },
                            label = { Text(stringResource(R.string.card_expiry)) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = cardCvv, onValueChange = { cardCvv = it },
                            label = { Text(stringResource(R.string.card_cvv)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                EntryType.IDENTITY -> {
                    OutlinedTextField(
                        value = idFullName, onValueChange = { idFullName = it },
                        label = { Text(stringResource(R.string.id_fullname)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = idDocument, onValueChange = { idDocument = it },
                        label = { Text(stringResource(R.string.id_document)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = idPhone, onValueChange = { idPhone = it },
                        label = { Text(stringResource(R.string.id_phone)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = idAddress, onValueChange = { idAddress = it },
                        label = { Text(stringResource(R.string.id_address)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                EntryType.NOTE -> { /* solo nombre + notas */ }
                EntryType.PASSKEY -> { /* la clave la gestiona el sistema; solo nombre, etiquetas y notas */ }
                else -> {
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text(stringResource(R.string.entry_user_label)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(stringResource(R.string.field_password)) }, singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(R.string.show_hide),
                                    )
                                }
                                IconButton(onClick = { showGenerator = true }) {
                                    Icon(Icons.Filled.Casino, contentDescription = stringResource(R.string.generate))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (password.isNotEmpty()) StrengthBar(password, Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text(stringResource(R.string.entry_url_label)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = totpSecret, onValueChange = { totpSecret = it },
                        label = { Text(stringResource(R.string.entry_totp_label)) }, singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                scanQrLauncher.launch(
                                    ScanOptions()
                                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt(scanPrompt)
                                        .setBeepEnabled(false)
                                        .setOrientationLocked(true)
                                )
                            }) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.scan_qr))
                            }
                        },
                        supportingText = {
                            if (totpSecret.isNotBlank() && !Totp.isValidSecret(totpSecret)) {
                                Text(stringResource(R.string.entry_totp_invalid), color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(stringResource(R.string.entry_totp_help))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = categoryLabel(category), onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.entry_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    Categories.ALL.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(categoryLabel(cat)) },
                            onClick = { category = cat; categoryExpanded = false },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = tagsText, onValueChange = { tagsText = it },
                label = { Text(stringResource(R.string.tags_label)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(stringResource(R.string.field_notes)) },
                minLines = if (type == EntryType.NOTE) 8 else 3,
                modifier = Modifier.fillMaxWidth(),
            )

            attachments.forEach { att ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AttachFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(att.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { attachments = attachments - att }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.delete))
                    }
                }
            }
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                attachLauncher.launch(intent)
            }) {
                Icon(Icons.Filled.AttachFile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_attachment))
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (title.isBlank()) {
                        showError = true
                        return@Button
                    }
                    val now = System.currentTimeMillis()
                    val extras = when (type) {
                        EntryType.CARD -> mapOf(
                            "number" to cardNumber.trim(),
                            "holder" to cardHolder.trim(),
                            "expiry" to cardExpiry.trim(),
                            "cvv" to cardCvv.trim(),
                        ).filterValues { it.isNotBlank() }
                        EntryType.IDENTITY -> mapOf(
                            "fullName" to idFullName.trim(),
                            "document" to idDocument.trim(),
                            "phone" to idPhone.trim(),
                            "address" to idAddress.trim(),
                        ).filterValues { it.isNotBlank() }
                        // Las passkeys conservan sus extras: ahí vive la clave privada
                        EntryType.PASSKEY -> entry?.extras ?: emptyMap()
                        else -> emptyMap()
                    }
                    val keepsLoginFields = type == EntryType.LOGIN || type == EntryType.PASSKEY
                    val saved = (entry ?: VaultEntry(type = type)).let { base ->
                        base.copy(
                            type = type,
                            title = title.trim(),
                            username = if (keepsLoginFields) username.trim() else "",
                            password = if (type == EntryType.LOGIN) password else base.password,
                            url = if (keepsLoginFields) url.trim() else "",
                            notes = notes,
                            category = category,
                            totpSecret = if (type == EntryType.LOGIN) totpSecret.trim() else "",
                            extras = extras,
                            tags = tagsText.split(',').map { it.trim() }.filter { it.isNotBlank() },
                            attachments = attachments,
                            updatedAt = now,
                            passwordChangedAt = if (base.password != password) now else base.passwordChangedAt,
                        )
                    }
                    VaultRepository.updateEntry(context, saved)
                    onDone(saved)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (showGenerator) {
        GeneratorDialog(
            onDismiss = { showGenerator = false },
            onUse = { generated ->
                password = generated
                showPassword = true
                showGenerator = false
            },
        )
    }
}
