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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import com.passvault.app.R
import com.passvault.app.data.VaultEntry

private data class Tab(val titleRes: Int, val icon: ImageVector)

@Composable
fun MainScreen(activity: FragmentActivity, startTab: Int = 0, startCreate: Boolean = false) {
    var selectedTab by remember { mutableIntStateOf(startTab) }
    var viewingEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var editingEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var creating by remember { mutableStateOf(startCreate) }
    var showingTrash by remember { mutableStateOf(false) }

    val tabs = listOf(
        Tab(R.string.tab_vault, Icons.Filled.Lock),
        Tab(R.string.tab_generator, Icons.Filled.Casino),
        Tab(R.string.tab_audit, Icons.Filled.VerifiedUser),
        Tab(R.string.tab_settings, Icons.Filled.Settings),
    )

    // Pantallas superpuestas: edición, detalle y papelera
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
        showingTrash -> {
            BackHandler { showingTrash = false }
            TrashScreen(onBack = { showingTrash = false })
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
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.titleRes)) },
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
            3 -> SettingsScreen(modifier, activity, onOpenTrash = { showingTrash = true })
        }
    }
}
