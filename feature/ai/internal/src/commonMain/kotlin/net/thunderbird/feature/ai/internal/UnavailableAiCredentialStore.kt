package net.thunderbird.feature.ai.internal

import net.thunderbird.feature.ai.api.AiCredential
import net.thunderbird.feature.ai.api.AiCredentialStore
import net.thunderbird.feature.ai.api.AiProviderId

internal class UnavailableAiCredentialStore : AiCredentialStore {
    override suspend fun read(providerId: AiProviderId): AiCredential? = null

    override suspend fun write(providerId: AiProviderId, credential: AiCredential) = Unit

    override suspend fun delete(providerId: AiProviderId) = Unit
}
