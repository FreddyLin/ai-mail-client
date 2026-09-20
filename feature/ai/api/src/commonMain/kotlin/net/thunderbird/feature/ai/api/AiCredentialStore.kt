package net.thunderbird.feature.ai.api

class AiCredential(val secret: String) {
    override fun toString(): String = "AiCredential(redacted)"
}

sealed interface AiCredentialStatus {
    data object Missing : AiCredentialStatus
    data object Available : AiCredentialStatus
    data object Unavailable : AiCredentialStatus
}

sealed interface AiCredentialOperationResult {
    data object Success : AiCredentialOperationResult
    data object Failure : AiCredentialOperationResult
}

interface AiCredentialStore {
    suspend fun read(providerId: AiProviderId): AiCredential?

    suspend fun status(providerId: AiProviderId): AiCredentialStatus

    suspend fun write(providerId: AiProviderId, credential: AiCredential): AiCredentialOperationResult

    suspend fun delete(providerId: AiProviderId): AiCredentialOperationResult
}
