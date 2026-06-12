package com.passvault.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.passvault.app.data.Settings
import com.passvault.app.data.VaultRepository
import com.passvault.app.ui.MainScreen
import com.passvault.app.ui.SetupScreen
import com.passvault.app.ui.UnlockScreen
import com.passvault.app.ui.theme.PassVaultTheme

class MainActivity : FragmentActivity() {

    companion object {
        const val ACTION_GENERATOR = "com.passvault.app.action.GENERATOR"
        const val ACTION_NEW_ENTRY = "com.passvault.app.action.NEW_ENTRY"
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        VaultRepository.lastInteractionAt = System.currentTimeMillis()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bloquea capturas de pantalla y vista previa en apps recientes
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val startTab = if (intent?.action == ACTION_GENERATOR) 1 else 0
        val startCreate = intent?.action == ACTION_NEW_ENTRY
        setContent {
            PassVaultTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(this, startTab, startCreate)
                }
            }
        }
    }
}

@Composable
private fun Root(activity: FragmentActivity, startTab: Int = 0, startCreate: Boolean = false) {
    val context = LocalContext.current
    val unlocked by VaultRepository.unlocked
    val revision by VaultRepository.vaultRevision

    AutoLockEffect()

    // El acceso a revision fuerza la recomposición al cambiar de bóveda
    key(revision) {
        when {
            !VaultRepository.vaultExists(context) && !unlocked -> SetupScreen()
            !unlocked -> UnlockScreen(activity)
            else -> MainScreen(activity, startTab, startCreate)
        }
    }
}

/**
 * Bloquea la bóveda cuando la app pasa a segundo plano más del tiempo
 * configurado, y también por inactividad aunque siga en pantalla.
 */
@Composable
private fun AutoLockEffect() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            val timeout = Settings.autoLockSeconds(context)
            if (timeout > 0 && VaultRepository.isUnlocked() &&
                System.currentTimeMillis() - VaultRepository.lastInteractionAt > timeout * 1000L
            ) {
                VaultRepository.lock()
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        var hiddenAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val timeout = Settings.autoLockSeconds(context)
                    if (timeout == 0) VaultRepository.lock() else hiddenAt = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    val timeout = Settings.autoLockSeconds(context)
                    if (timeout > 0 && hiddenAt > 0 &&
                        System.currentTimeMillis() - hiddenAt > timeout * 1000L
                    ) {
                        VaultRepository.lock()
                    }
                    hiddenAt = 0L
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
