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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.passvault.app.data.Settings
import com.passvault.app.util.ClipboardUtil
import com.passvault.app.util.PasswordGenerator
import kotlin.math.roundToInt

@Composable
fun GeneratorScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Generador", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
        GeneratorPanel(onUse = null)
    }
}

@Composable
fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var current by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generar contraseña") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GeneratorPanel(onUse = null, onGenerated = { current = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (current.isNotEmpty()) onUse(current) }) { Text("Usar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
                    generated.ifBlank { "Selecciona al menos un tipo de carácter" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { seed++ }) { Icon(Icons.Filled.Refresh, "Regenerar") }
                IconButton(onClick = {
                    if (generated.isNotBlank()) {
                        ClipboardUtil.copySensitive(
                            context, "Contraseña", generated,
                            Settings.clipboardClearSeconds(context)
                        )
                    }
                }) { Icon(Icons.Filled.ContentCopy, "Copiar") }
            }
        }
        if (generated.isNotBlank()) StrengthBar(generated, Modifier.fillMaxWidth())

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == 0,
                onClick = { mode = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Caracteres") }
            SegmentedButton(
                selected = mode == 1,
                onClick = { mode = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Frase de paso") }
        }

        if (mode == 0) {
            Text("Longitud: ${length.roundToInt()}")
            Slider(value = length, onValueChange = { length = it }, valueRange = 8f..64f)
            SwitchRow("Mayúsculas (A-Z)", useUpper) { useUpper = it }
            SwitchRow("Minúsculas (a-z)", useLower) { useLower = it }
            SwitchRow("Números (0-9)", useDigits) { useDigits = it }
            SwitchRow("Símbolos (!@#…)", useSymbols) { useSymbols = it }
            SwitchRow("Evitar ambiguos (l, 1, O, 0)", excludeAmbiguous) { excludeAmbiguous = it }
        } else {
            Text("Palabras: ${words.roundToInt()}")
            Slider(value = words, onValueChange = { words = it }, valueRange = 3f..10f)
        }

        if (onUse != null) {
            TextButton(onClick = { if (generated.isNotBlank()) onUse(generated) }) { Text("Usar esta contraseña") }
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
