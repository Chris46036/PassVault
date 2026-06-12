package com.passvault.app.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.passvault.app.R
import com.passvault.app.data.Vaults
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Desbloqueo biométrico: la clave maestra derivada se cifra con una clave AES
 * del Android Keystore que exige autenticación biométrica en cada uso.
 */
object BiometricHelper {

    private const val PREFS = "biometric_prefs"

    // Claves y blobs por bóveda; la bóveda "default" usa los nombres antiguos
    // para no invalidar la biometría de quien ya la tenía activada.
    private fun alias(context: Context): String {
        val id = Vaults.activeId(context)
        return if (id == Vaults.DEFAULT_ID) "passvault_biometric_key" else "passvault_biometric_key_$id"
    }

    private fun prefCt(context: Context): String {
        val id = Vaults.activeId(context)
        return if (id == Vaults.DEFAULT_ID) "wrapped_key_ct" else "wrapped_key_ct_$id"
    }

    private fun prefIv(context: Context): String {
        val id = Vaults.activeId(context)
        return if (id == Vaults.DEFAULT_ID) "wrapped_key_iv" else "wrapped_key_iv_$id"
    }

    fun canUseBiometric(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(prefCt(context))

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(prefCt(context)).remove(prefIv(context)).apply()
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.deleteEntry(alias(context))
        } catch (_: Exception) {
        }
    }

    private fun getOrCreateKeystoreKey(context: Context): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias(context), null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(
            alias(context),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(builder.build())
        return kg.generateKey()
    }

    /** Pide huella y, si es correcta, guarda la clave maestra cifrada. */
    fun promptEnable(
        activity: FragmentActivity,
        masterKeyBytes: ByteArray,
        onDone: (Boolean) -> Unit,
    ) {
        val cipher: Cipher
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey(activity))
        } catch (e: Exception) {
            disable(activity)
            onDone(false)
            return
        }
        prompt(activity, activity.getString(R.string.bio_enable_title), cipher, onResult = { c ->
            try {
                val ct = c.doFinal(masterKeyBytes)
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(prefCt(activity), Base64.encodeToString(ct, Base64.NO_WRAP))
                    .putString(prefIv(activity), Base64.encodeToString(c.iv, Base64.NO_WRAP))
                    .apply()
                onDone(true)
            } catch (e: Exception) {
                onDone(false)
            }
        }, onFail = { onDone(false) })
    }

    /** Pide huella y devuelve la clave maestra descifrada. */
    fun promptUnlock(
        activity: FragmentActivity,
        onSuccess: (ByteArray) -> Unit,
        onFail: (String) -> Unit,
    ) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ct = prefs.getString(prefCt(activity), null)
        val iv = prefs.getString(prefIv(activity), null)
        if (ct == null || iv == null) {
            onFail(activity.getString(R.string.bio_not_configured))
            return
        }
        val cipher: Cipher
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKeystoreKey(activity),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(activity)
            onFail(activity.getString(R.string.bio_changed))
            return
        } catch (e: Exception) {
            onFail(activity.getString(R.string.bio_unavailable))
            return
        }
        prompt(activity, activity.getString(R.string.unlock_title), cipher, onResult = { c ->
            try {
                onSuccess(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)))
            } catch (e: Exception) {
                onFail(activity.getString(R.string.bio_decrypt_failed))
            }
        }, onFail = { onFail(it) })
    }

    /**
     * Confirmación de identidad para acciones sensibles (ver CVV, exportar…).
     * Acepta biometría o el PIN/patrón del dispositivo; si no hay nada
     * configurado, deja pasar (no se puede exigir lo que no existe).
     */
    fun confirmAction(activity: FragmentActivity, title: String, onConfirmed: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val available = BiometricManager.from(activity)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        if (!available) {
            onConfirmed()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onConfirmed()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.bio_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        onResult: (Cipher) -> Unit,
        onFail: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onResult(c) else onFail(activity.getString(R.string.bio_cipher_error))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail(errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.bio_subtitle))
            .setNegativeButtonText(activity.getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
