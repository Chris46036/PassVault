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

    private const val KEYSTORE_ALIAS = "passvault_biometric_key"
    private const val PREFS = "biometric_prefs"
    private const val PREF_CT = "wrapped_key_ct"
    private const val PREF_IV = "wrapped_key_iv"

    fun canUseBiometric(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(PREF_CT)

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.deleteEntry(KEYSTORE_ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
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
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        } catch (e: Exception) {
            disable(activity)
            onDone(false)
            return
        }
        prompt(activity, "Activar desbloqueo biométrico", cipher, onResult = { c ->
            try {
                val ct = c.doFinal(masterKeyBytes)
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PREF_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
                    .putString(PREF_IV, Base64.encodeToString(c.iv, Base64.NO_WRAP))
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
        val ct = prefs.getString(PREF_CT, null)
        val iv = prefs.getString(PREF_IV, null)
        if (ct == null || iv == null) {
            onFail("Biometría no configurada")
            return
        }
        val cipher: Cipher
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKeystoreKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(activity)
            onFail("Las huellas del dispositivo cambiaron. Usa tu contraseña maestra y vuelve a activar la biometría.")
            return
        } catch (e: Exception) {
            onFail("Biometría no disponible")
            return
        }
        prompt(activity, "Desbloquear PassVault", cipher, onResult = { c ->
            try {
                onSuccess(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)))
            } catch (e: Exception) {
                onFail("No se pudo descifrar la clave")
            }
        }, onFail = { onFail(it) })
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
                if (c != null) onResult(c) else onFail("Error de cifrado")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail(errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Confirma tu identidad")
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
