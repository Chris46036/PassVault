package com.passvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.passvault.app.data.VaultEntry
import com.passvault.app.data.VaultRepository
import com.passvault.app.util.Hibp
import com.passvault.app.util.PasswordStrength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuditScreen(modifier: Modifier = Modifier, onOpenEntry: (VaultEntry) -> Unit) {
    val scope = rememberCoroutineScope()
    val entries = VaultRepository.entries.filter { it.password.isNotBlank() }

    val weak = entries.filter { PasswordStrength.evaluate(it.password).score < 2 }
    val reusedGroups = entries.groupBy { it.password }.filter { it.value.size > 1 }
    val reused = reusedGroups.values.flatten()
    val oldOnes = entries.filter {
        System.currentTimeMillis() - it.passwordChangedAt > 365L * 86_400_000L
    }

    var breached by remember { mutableStateOf<List<Pair<VaultEntry, Int>>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf(false) }

    val total = entries.size.coerceAtLeast(1)
    val issues = (weak.map { it.id } + reused.map { it.id } + oldOnes.map { it.id } +
        (breached?.map { it.first.id } ?: emptyList())).toSet().size
    val score = ((1f - issues.toFloat() / total) * 100).toInt().coerceIn(0, 100)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Auditoría de seguridad", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$score",
                        style = MaterialTheme.typography.displayMedium,
                        color = when {
                            score >= 80 -> Color(0xFF3FB950)
                            score >= 50 -> Color(0xFFD29922)
                            else -> Color(0xFFF85149)
                        },
                    )
                    Text("Puntuación de seguridad", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${entries.size} contraseñas analizadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    checking = true
                    checkError = false
                    scope.launch {
                        val results = withContext(Dispatchers.IO) {
                            entries.distinctBy { it.password }.mapNotNull { e ->
                                val count = Hibp.breachCount(e.password)
                                if (count < 0) return@withContext null
                                if (count > 0) e.password to count else null
                            }
                        }
                        checking = false
                        if (results == null) {
                            checkError = true
                        } else {
                            val byPassword = results.toMap()
                            breached = entries
                                .filter { it.password in byPassword }
                                .map { it to (byPassword[it.password] ?: 0) }
                        }
                    }
                },
                enabled = !checking && entries.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Comprobando filtraciones…")
                } else {
                    Text("Comprobar filtraciones (Have I Been Pwned)")
                }
            }
        }
        if (checkError) {
            item {
                IssueHeader(Icons.Filled.CloudOff, "Sin conexión: no se pudo comprobar", Color(0xFF8B949E))
            }
        }
        breached?.let { list ->
            item {
                IssueHeader(
                    Icons.Filled.Error,
                    if (list.isEmpty()) "Ninguna contraseña aparece en filtraciones conocidas 🎉"
                    else "Filtradas en brechas de datos (${list.size}) — cámbialas ya",
                    if (list.isEmpty()) Color(0xFF3FB950) else Color(0xFFF85149),
                )
            }
            items(list, key = { "b" + it.first.id }) { (entry, count) ->
                IssueCard(entry, "Vista en $count filtraciones", onOpenEntry)
            }
        }

        if (weak.isNotEmpty()) {
            item { IssueHeader(Icons.Filled.Warning, "Contraseñas débiles (${weak.size})", Color(0xFFF0883E)) }
            items(weak, key = { "w" + it.id }) { IssueCard(it, PasswordStrength.evaluate(it.password).label, onOpenEntry) }
        }
        if (reused.isNotEmpty()) {
            item { IssueHeader(Icons.Filled.Repeat, "Contraseñas reutilizadas (${reused.size})", Color(0xFFD29922)) }
            items(reused, key = { "r" + it.id }) { entry ->
                val times = reusedGroups[entry.password]?.size ?: 0
                IssueCard(entry, "Usada en $times entradas", onOpenEntry)
            }
        }
        if (oldOnes.isNotEmpty()) {
            item { IssueHeader(Icons.Filled.History, "Sin cambiar hace más de un año (${oldOnes.size})", Color(0xFF8B949E)) }
            items(oldOnes, key = { "o" + it.id }) { entry ->
                val days = (System.currentTimeMillis() - entry.passwordChangedAt) / 86_400_000L
                IssueCard(entry, "Hace $days días", onOpenEntry)
            }
        }

        if (entries.isNotEmpty() && weak.isEmpty() && reused.isEmpty() && oldOnes.isEmpty() && breached?.isEmpty() != false) {
            item {
                Text(
                    "Todo en orden. Tus contraseñas son fuertes y únicas. 💪",
                    color = Color(0xFF3FB950),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "Añade contraseñas a tu bóveda para analizarlas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun IssueHeader(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun IssueCard(entry: VaultEntry, detail: String, onOpenEntry: (VaultEntry) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.title.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.TextButton(onClick = { onOpenEntry(entry) }) { Text("Ver") }
        }
    }
}
