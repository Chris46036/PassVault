package com.passvault.app.passkeys

import android.util.Base64
import androidx.credentials.provider.CallingAppInfo
import com.passvault.app.data.EntryType
import com.passvault.app.data.VaultEntry
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Operaciones WebAuthn de un autenticador de plataforma: creación de
 * credenciales (registro) y firmas (autenticación), con claves EC P-256.
 * La clave privada se guarda cifrada dentro de la bóveda, como el resto
 * de los datos.
 */
object PasskeyCrypto {

    private val AAGUID = ByteArray(16) // sin identificar, como autenticador anónimo

    fun b64url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun b64urlDecode(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    /** Origen WebAuthn de una app Android: android:apk-key-hash:<sha256 de su firma>. */
    fun appOrigin(callingAppInfo: CallingAppInfo): String {
        val cert = callingAppInfo.signingInfo.apkContentsSigners[0].toByteArray()
        return "android:apk-key-hash:${b64url(sha256(cert))}"
    }

    fun clientDataJson(type: String, challengeB64url: String, origin: String, packageName: String): ByteArray =
        JSONObject()
            .put("type", type)
            .put("challenge", challengeB64url)
            .put("origin", origin)
            .put("androidPackageName", packageName)
            .toString().toByteArray(Charsets.UTF_8)

    data class CreatedPasskey(val entry: VaultEntry, val registrationResponseJson: String)

    /**
     * Registro: genera el par de claves, construye el attestationObject y la
     * entrada de bóveda que persiste la passkey.
     */
    fun createPasskey(
        requestJson: String,
        clientDataHash: ByteArray?,
        origin: String,
        packageName: String,
    ): CreatedPasskey {
        val request = JSONObject(requestJson)
        val rp = request.getJSONObject("rp")
        val rpId = rp.optString("id", rp.optString("name"))
        val user = request.getJSONObject("user")
        val userHandleB64 = user.getString("id")
        val userName = user.optString("name")
        val userDisplay = user.optString("displayName", userName)
        val challenge = request.getString("challenge")

        // Par de claves EC P-256 (ES256), generado en software para que viva
        // cifrado en la bóveda y sobreviva en las copias de seguridad
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        val credId = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val publicKey = keyPair.public as ECPublicKey
        val x = publicKey.w.affineX.toByteArray().let(::to32Bytes)
        val y = publicKey.w.affineY.toByteArray().let(::to32Bytes)
        val cosePublicKey = Cbor.encode(
            Cbor.CborMap(
                listOf(
                    1 to 2,    // kty: EC2
                    3 to -7,   // alg: ES256
                    -1 to 1,   // crv: P-256
                    -2 to x,
                    -3 to y,
                )
            )
        )

        // authenticatorData con datos de credencial adjuntos (flag AT)
        val flags = 0x5D // UP | UV | BE | BS | AT
        val authData = ByteBuffer.allocate(37 + 16 + 2 + credId.size + cosePublicKey.size)
            .put(sha256(rpId.toByteArray(Charsets.UTF_8)))
            .put(flags.toByte())
            .putInt(0) // signCount
            .put(AAGUID)
            .putShort(credId.size.toShort())
            .put(credId)
            .put(cosePublicKey)
            .array()

        val attestationObject = Cbor.encode(
            Cbor.CborMap(
                listOf(
                    "fmt" to "none",
                    "attStmt" to Cbor.CborMap(emptyList()),
                    "authData" to authData,
                )
            )
        )

        val clientDataJson = if (clientDataHash == null) {
            clientDataJson("webauthn.create", challenge, origin, packageName)
        } else {
            ByteArray(0) // el navegador privilegiado construye el suyo propio
        }

        val credIdB64 = b64url(credId)
        val response = JSONObject().apply {
            put("id", credIdB64)
            put("rawId", credIdB64)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put("clientExtensionResults", JSONObject())
            put("response", JSONObject().apply {
                put("clientDataJSON", b64url(clientDataJson))
                put("attestationObject", b64url(attestationObject))
                put("transports", org.json.JSONArray().put("internal"))
            })
        }

        val entry = VaultEntry(
            type = EntryType.PASSKEY,
            title = "$rpId · $userName",
            username = userName,
            url = rpId,
            extras = mapOf(
                "rpId" to rpId,
                "credId" to credIdB64,
                "privKey" to Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP),
                "userHandle" to userHandleB64,
                "userDisplayName" to userDisplay,
            ),
        )
        return CreatedPasskey(entry, response.toString())
    }

    /** Autenticación: firma el desafío con la clave privada de la passkey. */
    fun signAssertion(
        entry: VaultEntry,
        requestJson: String,
        clientDataHash: ByteArray?,
        origin: String,
        packageName: String,
    ): String {
        val request = JSONObject(requestJson)
        val rpId = request.optString("rpId", entry.extras["rpId"] ?: "")
        val challenge = request.getString("challenge")

        val flags = 0x1D // UP | UV | BE | BS
        val authData = ByteBuffer.allocate(37)
            .put(sha256(rpId.toByteArray(Charsets.UTF_8)))
            .put(flags.toByte())
            .putInt(0)
            .array()

        val clientDataJson = if (clientDataHash == null) {
            clientDataJson("webauthn.get", challenge, origin, packageName)
        } else {
            ByteArray(0)
        }
        val hash = clientDataHash ?: sha256(clientDataJson)

        val privKeyBytes = Base64.decode(entry.extras["privKey"], Base64.NO_WRAP)
        val privateKey: PrivateKey = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(privKeyBytes))
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(authData + hash)
            sign()
        }

        val credIdB64 = entry.extras["credId"] ?: ""
        return JSONObject().apply {
            put("id", credIdB64)
            put("rawId", credIdB64)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put("clientExtensionResults", JSONObject())
            put("response", JSONObject().apply {
                put("clientDataJSON", b64url(clientDataJson))
                put("authenticatorData", b64url(authData))
                put("signature", b64url(signature))
                put("userHandle", entry.extras["userHandle"] ?: "")
            })
        }.toString()
    }

    /** BigInteger.toByteArray puede traer un 0x00 inicial o venir corto; normaliza a 32 bytes. */
    private fun to32Bytes(raw: ByteArray): ByteArray {
        if (raw.size == 32) return raw
        val out = ByteArray(32)
        if (raw.size > 32) {
            System.arraycopy(raw, raw.size - 32, out, 0, 32)
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.size, raw.size)
        }
        return out
    }
}
