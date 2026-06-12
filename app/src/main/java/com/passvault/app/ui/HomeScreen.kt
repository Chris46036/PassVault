package com.passvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passvault.app.R
import com.passvault.app.data.Categories
import com.passvault.app.data.EntryType
import com.passvault.app.data.VaultEntry
import com.passvault.app.data.VaultRepository

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenEntry: (VaultEntry) -> Unit,
    onCreate: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }

    val all = VaultRepository.entries.filter { !it.isDeleted }
    val allTags = all.flatMap { it.tags }.distinct().sorted()
    val filtered = all.filter { e ->
        (query.isBlank() ||
            e.title.contains(query, true) ||
            e.username.contains(query, true) ||
            e.url.contains(query, true) ||
            e.tags.any { it.contains(query, true) }) &&
            (categoryFilter == null || e.category == categoryFilter) &&
            (tagFilter == null || tagFilter in e.tags) &&
            (!onlyFavorites || e.favorite)
    }.sortedWith(compareByDescending<VaultEntry> { it.favorite }.thenBy { it.title.lowercase() })

    val context = androidx.compose.ui.platform.LocalContext.current
    val vaultName = remember {
        com.passvault.app.data.Vaults.list(context)
            .firstOrNull { it.id == com.passvault.app.data.Vaults.activeId(context) }?.name ?: "Personal"
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(vaultName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.items_count, all.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { VaultRepository.lock() }) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.set_lock_now),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { onlyFavorites = !onlyFavorites },
                        label = { Text(stringResource(R.string.favorites)) },
                        leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) },
                    )
                }
                items(Categories.ALL) { cat ->
                    FilterChip(
                        selected = categoryFilter == cat,
                        onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                        label = { Text(categoryLabel(cat)) },
                    )
                }
                items(allTags) { tag ->
                    FilterChip(
                        selected = tagFilter == tag,
                        onClick = { tagFilter = if (tagFilter == tag) null else tag },
                        label = { Text("#$tag") },
                    )
                }
            }
            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(if (all.isEmpty()) R.string.empty_vault else R.string.no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        EntryCard(entry, onClick = { onOpenEntry(entry) })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
        }
    }
}

@Composable
private fun EntryCard(entry: VaultEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (entry.type) {
                        EntryType.CARD -> Icon(
                            Icons.Filled.CreditCard, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        EntryType.NOTE -> Icon(
                            Icons.Filled.Description, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        EntryType.IDENTITY -> Icon(
                            Icons.Filled.Badge, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        EntryType.PASSKEY -> Icon(
                            Icons.Filled.Key, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        else -> Text(
                            entry.title.take(1).uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (entry.type) {
                        EntryType.CARD -> maskCardNumber(entry.extras["number"] ?: "")
                        EntryType.IDENTITY -> entry.extras["fullName"] ?: ""
                        EntryType.NOTE -> typeLabel(entry.type)
                        EntryType.PASSKEY -> typeLabel(entry.type) + " · " + entry.url
                        else -> entry.username.ifBlank { entry.url }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.favorite) {
                Icon(
                    Icons.Filled.Star, contentDescription = stringResource(R.string.favorite),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

fun maskCardNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length >= 4) "•••• ${digits.takeLast(4)}" else number
}
