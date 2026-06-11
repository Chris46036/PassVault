package com.passvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.passvault.app.R
import com.passvault.app.data.Settings
import com.passvault.app.util.ClipboardUtil
import com.passvault.app.util.PasswordGenerator
import kotlin.math.roundToInt

@Composable
fun GeneratorScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            stringResource(R.string.tab_generator),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        GeneratorPanel(onUse = null)
    }
}

@Composable
fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var current by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gen_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GeneratorPanel(onUse = null, onGenerated = { current = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (current.isNotEmpty()) onUse(current) }) {
                Text(stringResource(R.string.gen_use))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorPanel(onUse: ((String) -> Unit)?, onGenerated: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0 = caracteres, 1 = frase de paso
    var length by remember { mutableFloatStateOf(20f) }
    var words by remember { mutableFloatStateOf(5f) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var excludeAmbiguous by remember { mutableStateOf(false) }
    var seed by remember { mutableIntStateOf(0) }

    val generated = remember(mode, length, words, useUpper, useLower, useDigits, useSymbols, excludeAmbiguous, seed) {
        val result = if (mode == 0) {
            PasswordGenerator.generate(
                length.roundToInt(), useLower, useUpper, useDigits, useSymbols, excludeAmbiguous
            )
        } else {
            PasswordGenerator.generatePassphrase(words.roundToInt())
        }
        onGenerated?.invoke(result)
        result
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    generated.ifBlank { stringResource(R.string.gen_select_type) },
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { seed++ }) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.regenerate))
                }
                IconButton(onClick = {
                    if (generated.isNotBlank()) {
                        ClipboardUtil.copySensitive(
                            context, "password", generated,
                            Settings.clipboardClearSeconds(context)
                        )
                    }
                }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy)) }
            }
        }
        if (generated.isNotBlank()) StrengthBar(generated, Modifier.fillMaxWidth())

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == 0,
                onClick = { mode = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.gen_mode_chars)) }
            SegmentedButton(
                selected = mode == 1,
                onClick = { mode = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.gen_mode_passphrase)) }
        }

        if (mode == 0) {
            Text(stringResource(R.string.gen_length, length.roundToInt()))
            Slider(value = length, onValueChange = { length = it }, valueRange = 8f..64f)
            SwitchRow(stringResource(R.string.gen_upper), useUpper) { useUpper = it }
            SwitchRow(stringResource(R.string.gen_lower), useLower) { useLower = it }
            SwitchRow(stringResource(R.string.gen_digits), useDigits) { useDigits = it }
            SwitchRow(stringResource(R.string.gen_symbols), useSymbols) { useSymbols = it }
            SwitchRow(stringResource(R.string.gen_ambiguous), excludeAmbiguous) { excludeAmbiguous = it }
        } else {
            Text(stringResource(R.string.gen_words, words.roundToInt()))
            Slider(value = words, onValueChange = { words = it }, valueRange = 3f..10f)
        }

        if (onUse != null) {
            TextButton(onClick = { if (generated.isNotBlank()) onUse(generated) }) {
                Text(stringResource(R.string.gen_use_this))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
