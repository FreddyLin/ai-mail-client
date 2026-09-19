package net.thunderbird.feature.ai.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.nio.charset.StandardCharsets.UTF_8
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.ai.api.AiCredential
import net.thunderbird.feature.ai.api.AiProviderId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class AndroidKeystoreAiCredentialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val providerId = AiProviderId("test-provider")
    private lateinit var testSubject: AndroidKeystoreAiCredentialStore

    @Before
    fun setUp() {
        context.noBackupFilesDir.deleteRecursively()
        testSubject = AndroidKeystoreAiCredentialStore(context)
    }

    @After
    fun tearDown() {
        context.noBackupFilesDir.deleteRecursively()
    }

    @Test
    fun `save and read returns credential`() = runTest {
        testSubject.write(providerId, AiCredential("secret-value"))

        assertEquals(AiCredential("secret-value").secret, testSubject.read(providerId)?.secret)
    }

    @Test
    fun `delete removes credential`() = runTest {
        testSubject.write(providerId, AiCredential("secret-value"))

        testSubject.delete(providerId)

        assertNull(testSubject.read(providerId))
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
}
