package com.fsck.k9.ui.messageview

import com.fsck.k9.ui.messagelist.smartcategory.SmartCategoryAssignmentSource
import com.fsck.k9.ui.messagelist.smartcategory.SmartCategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategory
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategoryAssigner
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategoryAssignmentResult
import net.thunderbird.feature.mail.message.list.ui.state.SmartCategory

internal class LegacyMessageReaderAiCategoryAssigner(
    private val smartCategoryRepository: SmartCategoryRepository,
) : MessageReaderAiCategoryAssigner {
    override suspend fun assign(
        messageReference: String,
        categories: Set<MessageReaderAiCategory>,
    ): MessageReaderAiCategoryAssignmentResult = withContext(Dispatchers.IO) {
        val smartCategories = categories.map(::mapCategory).toSet()
        if (smartCategoryRepository.assignCategories(
                messageReference,
                smartCategories,
                SmartCategoryAssignmentSource.AI,
            )
        ) {
            MessageReaderAiCategoryAssignmentResult.Success
        } else {
            MessageReaderAiCategoryAssignmentResult.Failure
        }
    }

    private fun mapCategory(category: MessageReaderAiCategory) = when (category) {
        MessageReaderAiCategory.IMPORTANT -> SmartCategory.IMPORTANT
        MessageReaderAiCategory.ACTION -> SmartCategory.ACTION
        MessageReaderAiCategory.INVOICE -> SmartCategory.INVOICE
        MessageReaderAiCategory.ORDER -> SmartCategory.ORDER
        MessageReaderAiCategory.NEWSLETTER -> SmartCategory.NEWSLETTER
    }
}
