package com.passvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.passvault.app.data.VaultEntry

private data class Tab(val title: String, val icon: @Composable () -> Unit)

@Composable
fun MainScreen(activity: FragmentActivity) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var viewingEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var editingEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var creating by remember { mutableStateOf(false) }

    val tabs = listOf(
        Tab("Bóveda") { Icon(Icons.Filled.Lock, null) },
        Tab("Generador") { Icon(Icons.Filled.Casino, null) },
        Tab("Auditoría") { Icon(Icons.Filled.VerifiedUser, null) },
        Tab("Ajustes") { Icon(Icons.Filled.Settings, null) },
    )

    // Pantallas superpuestas: edición y detalle
    when {
        creating || editingEntry != null -> {
            BackHandler { creating = false; editingEntry = null }
            EntryEditScreen(
                entry = editingEntry,
                onDone = { saved ->
                    creating = false
                    editingEntry = null
                    if (saved != null && viewingEntry != null) viewingEntry = saved
                },
            )
            return
        }
        viewingEntry != null -> {
            BackHandler { viewingEntry = null }
            EntryDetailScreen(
                entry = viewingEntry!!,
                onBack = { viewingEntry = null },
                onEdit = { editingEntry = it },
                onDeleted = { viewingEntry = null },
            )
            return
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        icon = tab.icon,
                        label = { Text(tab.title) },
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = modifier,
                onOpenEntry = { viewingEntry = it },
                onCreate = { creating = true },
            )
            1 -> GeneratorScreen(modifier)
            2 -> AuditScreen(modifier, onOpenEntry = { viewingEntry = it })
            3 -> SettingsScreen(modifier, activity)
        }
    }
}
