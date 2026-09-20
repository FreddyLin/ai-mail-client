package net.thunderbird.feature.ai.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.ai.api.AiCredential
import net.thunderbird.feature.ai.api.AiCredentialOperationResult
import net.thunderbird.feature.ai.api.AiCredentialStatus
import net.thunderbird.feature.ai.api.AiProviderId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class AndroidKeystoreAiCredentialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val providerId = AiProviderId("test-provider")
    private lateinit var testSubject: AndroidKeystoreAiCredentialStore

    @Before
    fun setUp() {
        context.noBackupFilesDir.deleteRecursively()
        testSubject = AndroidKeystoreAiCredentialStore(context, TestAiCredentialCipher())
    }

    @After
    fun tearDown() {
        context.noBackupFilesDir.deleteRecursively()
    }

    @Test
    fun `save and read returns credential`() = runTest {
        val result = testSubject.write(providerId, AiCredential("secret-value"))

        assertIs<AiCredentialOperationResult.Success>(result)
        assertEquals(AiCredential("secret-value").secret, testSubject.read(providerId)?.secret)
    }

    @Test
    fun `valid credential status is available`() = runTest {
        testSubject.write(providerId, AiCredential("secret-value"))

        assertIs<AiCredentialStatus.Available>(testSubject.status(providerId))
    }

    @Test
    fun `delete removes credential`() = runTest {
        testSubject.write(providerId, AiCredential("secret-value"))

        val result = testSubject.delete(providerId)

        assertIs<AiCredentialOperationResult.Success>(result)
        assertNull(testSubject.read(providerId))
    }

    @Test
    fun `delete missing credential is idempotent`() = runTest {
        assertIs<AiCredentialOperationResult.Success>(testSubject.delete(providerId))
    }

    @Test
    fun `different provider ids do not collide`() = runTest {
        testSubject.write(AiProviderId("provider-a"), AiCredential("secret-a"))
        testSubject.write(AiProviderId("provider-b"), AiCredential("secret-b"))

        assertEquals("secret-a", testSubject.read(AiProviderId("provider-a"))?.secret)
        assertEquals("secret-b", testSubject.read(AiProviderId("provider-b"))?.secret)
    }

    @Test
    fun `missing credential returns null`() = runTest {
        assertNull(testSubject.read(providerId))
    }

    @Test
    fun `missing credential status is missing`() = runTest {
        assertIs<AiCredentialStatus.Missing>(testSubject.status(providerId))
    }

    @Test
    fun `persisted data does not contain plaintext credential`() = runTest {
        val secret = "secret-value"
        testSubject.write(providerId, AiCredential(secret))

        val persisted = context.noBackupFilesDir
            .resolve("ai-credentials")
            .walkTopDown()
            .filter { it.isFile }
            .flatMap { it.readBytes().asIterable() }
            .toList()
            .toByteArray()

        assertFalse(String(persisted, UTF_8).contains(secret))
    }

    @Test
    fun `corrupted data returns null`() = runTest {
        testSubject.write(providerId, AiCredential("secret-value"))
        context.noBackupFilesDir
            .resolve("ai-credentials")
            .listFiles()!!
            .single()
            .writeBytes(byteArrayOf(1, 2, 3))

        assertNull(testSubject.read(providerId))
    }

    @Test
    fun `corrupted credential status is unavailable`() = runTest {
        testSubject.write(providerId, AiCredential("secret-value"))
        context.noBackupFilesDir
            .resolve("ai-credentials")
            .listFiles()!!
            .single()
            .writeBytes(byteArrayOf(1, 2, 3))

        assertIs<AiCredentialStatus.Unavailable>(testSubject.status(providerId))
    }

    @Test
    fun `failed write is reported without exposing credential`() = runTest {
        val secret = "secret-value"
        context.noBackupFilesDir.resolve("ai-credentials").writeText("not-a-directory")

        val result = testSubject.write(providerId, AiCredential(secret))

        assertIs<AiCredentialOperationResult.Failure>(result)
        assertFalse(result.toString().contains(secret))
    }

    @Test
    fun `credential status never exposes credential`() = runTest {
        val secret = "secret-value"
        testSubject.write(providerId, AiCredential(secret))

        val status = testSubject.status(providerId)

        assertFalse(status.toString().contains(secret))
    }
}

private class TestAiCredentialCipher : AiCredentialCipher {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply {
        init(256)
    }.generateKey()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(payload: ByteArray): ByteArray? {
        if (payload.size <= IV_SIZE_BYTES) return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_SIZE_BITS, payload.copyOfRange(0, IV_SIZE_BYTES)),
        )
        return cipher.doFinal(payload.copyOfRange(IV_SIZE_BYTES, payload.size))
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
