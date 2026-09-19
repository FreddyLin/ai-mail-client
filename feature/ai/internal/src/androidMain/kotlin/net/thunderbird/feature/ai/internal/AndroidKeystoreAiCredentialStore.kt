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
import net.thunderbird.feature.ai.api.AiCredentialStore
import net.thunderbird.feature.ai.api.AiProviderId

internal class AndroidKeystoreAiCredentialStore(
    context: Context,
) : AiCredentialStore {
    private val credentialDirectory = File(context.noBackupFilesDir, CREDENTIAL_DIRECTORY)
    private val lock = Any()

    override suspend fun read(providerId: AiProviderId): AiCredential? = synchronized(lock) {
        runCatching { readCredential(providerId) }.getOrNull()
    }

    override suspend fun write(providerId: AiProviderId, credential: AiCredential) {
        synchronized(lock) {
            runCatching { writeCredential(providerId, credential) }
        }
    }

    override suspend fun delete(providerId: AiProviderId) {
        synchronized(lock) {
            runCatching { credentialFile(providerId).delete() }
        }
    }

    private fun readCredential(providerId: AiProviderId): AiCredential? {
        val file = credentialFile(providerId)
        if (!file.isFile) return null

        val payload = file.readBytes()
        if (payload.size <= IV_SIZE_BYTES) return null

        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val key = existingKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))

        return AiCredential(String(cipher.doFinal(ciphertext), UTF_8))
    }

    private fun writeCredential(providerId: AiProviderId, credential: AiCredential) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val encrypted = cipher.doFinal(credential.secret.toByteArray(UTF_8))
        val payload = cipher.iv + encrypted

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

    private fun credentialFile(providerId: AiProviderId): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(providerId.value.toByteArray(UTF_8))
        val filename = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP)
        return File(credentialDirectory, filename)
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
        const val CREDENTIAL_DIRECTORY = "ai-credentials"
        const val KEY_ALIAS = "linus-mail-ai-credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
