package net.thunderbird.feature.ai.internal

import net.thunderbird.feature.ai.api.AiCapability
import net.thunderbird.feature.ai.api.AiProvider
import net.thunderbird.feature.ai.api.AiProviderAvailability
import net.thunderbird.feature.ai.api.AiProviderRegistry

internal class UnconfiguredAiProviderRegistry : AiProviderRegistry {
    override val availability = AiProviderAvailability(
        enabled = false,
        providerId = null,
        capabilities = emptySet(),
    )

    override fun providerFor(capability: AiCapability): AiProvider? = null
}
