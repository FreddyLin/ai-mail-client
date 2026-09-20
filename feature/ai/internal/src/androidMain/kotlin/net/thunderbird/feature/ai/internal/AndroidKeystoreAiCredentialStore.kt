package net.thunderbird.feature.ai.internal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.thunderbird.feature.ai.api.AiCredential
import net.thunderbird.feature.ai.api.AiCredentialOperationResult
import net.thunderbird.feature.ai.api.AiCredentialStatus
import net.thunderbird.feature.ai.api.AiCredentialStore
import net.thunderbird.feature.ai.api.AiProviderId

internal class AndroidKeystoreAiCredentialStore(
    context: Context,
    private val credentialCipher: AiCredentialCipher = AndroidKeystoreAiCredentialCipher(),
) : AiCredentialStore {
    private val credentialDirectory = File(context.noBackupFilesDir, CREDENTIAL_DIRECTORY)
    private val lock = Any()

    override suspend fun read(providerId: AiProviderId): AiCredential? = synchronized(lock) {
        runCatching { readCredential(providerId) }.getOrNull()
    }

    override suspend fun status(providerId: AiProviderId): AiCredentialStatus = synchronized(lock) {
        val file = credentialFile(providerId)
        if (!file.isFile) return@synchronized AiCredentialStatus.Missing

        runCatching { readCredential(providerId) }
            .fold(
                onSuccess = { credential ->
                    if (credential != null) AiCredentialStatus.Available else AiCredentialStatus.Unavailable
                },
                onFailure = { AiCredentialStatus.Unavailable },
            )
    }

    override suspend fun write(
        providerId: AiProviderId,
        credential: AiCredential,
    ): AiCredentialOperationResult = synchronized(lock) {
        runCatching { writeCredential(providerId, credential) }
            .fold(
                onSuccess = { AiCredentialOperationResult.Success },
                onFailure = { AiCredentialOperationResult.Failure },
            )
    }

    override suspend fun delete(providerId: AiProviderId): AiCredentialOperationResult = synchronized(lock) {
        runCatching { deleteCredential(providerId) }
            .fold(
                onSuccess = { AiCredentialOperationResult.Success },
                onFailure = { AiCredentialOperationResult.Failure },
            )
    }

    private fun readCredential(providerId: AiProviderId): AiCredential? {
        val file = credentialFile(providerId)
        if (!file.isFile) return null

        val plaintext = credentialCipher.decrypt(file.readBytes()) ?: return null
        return AiCredential(String(plaintext, UTF_8))
    }

    private fun writeCredential(providerId: AiProviderId, credential: AiCredential) {
        val payload = credentialCipher.encrypt(credential.secret.toByteArray(UTF_8))

        check(credentialDirectory.mkdirs() || credentialDirectory.isDirectory)
        val target = credentialFile(providerId)
        val temporary = File(credentialDirectory, "${target.name}.tmp")
        try {
            temporary.writeBytes(payload)
            check(temporary.renameTo(target))
        } finally {
            temporary.delete()
        }
    }

    private fun deleteCredential(providerId: AiProviderId) {
        val file = credentialFile(providerId)
        check(!file.exists() || file.delete())
    }

    private fun credentialFile(providerId: AiProviderId): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(providerId.value.toByteArray(UTF_8))
        val filename = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP)
        return File(credentialDirectory, filename)
    }

    private companion object {
        const val CREDENTIAL_DIRECTORY = "ai-credentials"
    }
}

internal interface AiCredentialCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(payload: ByteArray): ByteArray?
}

private class AndroidKeystoreAiCredentialCipher : AiCredentialCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(payload: ByteArray): ByteArray? {
        if (payload.size <= IV_SIZE_BYTES) return null

        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val key = existingKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))

        return cipher.doFinal(ciphertext)
    }

    private fun existingKey(): SecretKey? {
        val keyStore = loadKeyStore()
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        runCatching {
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        }.getOrNull()?.let { return it }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "linus-mail-ai-credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
