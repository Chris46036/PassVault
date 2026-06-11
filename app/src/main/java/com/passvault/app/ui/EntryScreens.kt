package com.passvault.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.passvault.app.data.Categories
import com.passvault.app.data.Settings
import com.passvault.app.data.VaultEntry
import com.passvault.app.data.VaultRepository
import com.passvault.app.util.ClipboardUtil
import com.passvault.app.util.Totp
import kotlinx.coroutines.delay

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
    // Lee la versión viva por si se editó
    val live = VaultRepository.entries.firstOrNull { it.id == entry.id } ?: entry

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(live.title.ifBlank { "Sin nombre" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    IconButton(onClick = {
                        VaultRepository.updateEntry(context, live.copy(favorite = !live.favorite))
                    }) {
                        Icon(
                            if (live.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (live.favorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(live) }) { Icon(Icons.Filled.Edit, "Editar") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (live.username.isNotBlank()) {
                DetailField("Usuario", live.username, onCopy = {
                    ClipboardUtil.copySensitive(context, "Usuario", live.username, clipSeconds)
                })
            }
            if (live.password.isNotBlank()) {
                DetailField(
                    "Contraseña",
                    if (showPassword) live.password else "•".repeat(live.password.length.coerceAtMost(16)),
                    mono = true,
                    onCopy = { ClipboardUtil.copySensitive(context, "Contraseña", live.password, clipSeconds) },
                    extraAction = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Mostrar",
                            )
                        }
                    },
                )
            }
            if (live.totpSecret.isNotBlank()) {
                TotpCard(live.totpSecret) { code ->
                    ClipboardUtil.copySensitive(context, "Código 2FA", code, clipSeconds)
                }
            }
            if (live.url.isNotBlank()) {
                DetailField("Sitio web", live.url, onCopy = {
                    ClipboardUtil.copySensitive(context, "URL", live.url, clipSeconds)
                }, extraAction = {
                    IconButton(onClick = {
                        val url = if (live.url.startsWith("http")) live.url else "https://${live.url}"
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Abrir") }
                })
            }
            if (live.notes.isNotBlank()) {
                DetailField("Notas", live.notes, onCopy = null)
            }
            Text(
                "Categoría: ${live.category}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val days = (System.currentTimeMillis() - live.passwordChangedAt) / 86_400_000L
            Text(
                "Contraseña actualizada hace $days días",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar entrada?") },
            text = { Text("\"${live.title}\" se eliminará de forma permanente.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    VaultRepository.deleteEntry(context, live.id)
                    onDeleted()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
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
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Copiar") }
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
                Text("Código 2FA (TOTP)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    code?.chunked(3)?.joinToString(" ") ?: "Secreto inválido",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (remaining <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            CircularProgressIndicator(
                progress = { remaining / 30f },
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("$remaining s", style = MaterialTheme.typography.labelSmall)
            if (code != null) {
                IconButton(onClick = { onCopy(code) }) { Icon(Icons.Filled.ContentCopy, "Copiar código") }
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
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var username by remember { mutableStateOf(entry?.username ?: "") }
    var password by remember { mutableStateOf(entry?.password ?: "") }
    var url by remember { mutableStateOf(entry?.url ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var category by remember { mutableStateOf(entry?.category ?: Categories.LOGIN) }
    var totpSecret by remember { mutableStateOf(entry?.totpSecret ?: "") }
    var showPassword by remember { mutableStateOf(entry == null) }
    var showGenerator by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entry == null) "Nueva entrada" else "Editar entrada") },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it; error = null },
                label = { Text("Nombre *") }, singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Usuario o correo") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Contraseña") }, singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Mostrar",
                            )
                        }
                        IconButton(onClick = { showGenerator = true }) {
                            Icon(Icons.Filled.Casino, contentDescription = "Generar")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (password.isNotEmpty()) StrengthBar(password, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Sitio web (para autorrellenar)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = category, onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    Categories.ALL.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = { category = cat; categoryExpanded = false },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = totpSecret, onValueChange = { totpSecret = it },
                label = { Text("Secreto 2FA / TOTP (opcional)") }, singleLine = true,
                supportingText = {
                    if (totpSecret.isNotBlank() && !Totp.isValidSecret(totpSecret)) {
                        Text("No parece un secreto base32 válido", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("El código que te da el sitio al activar la verificación en dos pasos")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notas") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (title.isBlank()) {
                        error = "El nombre es obligatorio"
                        return@Button
                    }
                    val now = System.currentTimeMillis()
                    val saved = (entry ?: VaultEntry()).let { base ->
                        base.copy(
                            title = title.trim(),
                            username = username.trim(),
                            password = password,
                            url = url.trim(),
                            notes = notes,
                            category = category,
                            totpSecret = totpSecret.trim(),
                            updatedAt = now,
                            passwordChangedAt = if (base.password != password) now else base.passwordChangedAt,
                        )
                    }
                    VaultRepository.updateEntry(context, saved)
                    onDone(saved)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
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
