package net.thunderbird.feature.ai.api

class AiCredential(val secret: String) {
    override fun toString(): String = "AiCredential(redacted)"
}

interface AiCredentialStore {
    suspend fun read(providerId: AiProviderId): AiCredential?

    suspend fun write(providerId: AiProviderId, credential: AiCredential)

    suspend fun delete(providerId: AiProviderId)
}
