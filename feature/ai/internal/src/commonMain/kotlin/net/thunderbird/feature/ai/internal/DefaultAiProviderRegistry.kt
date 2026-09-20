package net.thunderbird.feature.ai.internal

import net.thunderbird.feature.ai.api.AiCapability
import net.thunderbird.feature.ai.api.AiProvider
import net.thunderbird.feature.ai.api.AiProviderAvailability
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiProviderRegistry

internal class DefaultAiProviderRegistry(
    private val providers: List<AiProvider>,
) : AiProviderRegistry {
    override val availability = AiProviderAvailability(
        enabled = providers.isNotEmpty(),
        providerId = providers.singleOrNull()?.id,
        capabilities = providers.flatMap { it.capabilities }.toSet(),
    )

    override fun providerFor(capability: AiCapability): AiProvider? =
        providers.firstOrNull { it.supports(capability) }

    override fun providerFor(providerId: AiProviderId): AiProvider? =
        providers.firstOrNull { it.id == providerId }
}
