package com.fsck.k9.ui.messagelist.smartcategory

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.feature.mail.message.list.ui.state.SmartCategory

interface SmartCategoryRepository {
    fun observeAssignments(): Flow<Map<String, Set<SmartCategory>>>

    fun assignCategory(messageReference: String, category: SmartCategory)

    fun removeCategory(messageReference: String, category: SmartCategory)
}

class SharedPreferencesSmartCategoryRepository(
    context: Context,
) : SmartCategoryRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val assignments = MutableStateFlow(readAssignments())
    private val lock = Any()

    override fun observeAssignments(): Flow<Map<String, Set<SmartCategory>>> = assignments

    override fun assignCategory(messageReference: String, category: SmartCategory) {
        if (category == SmartCategory.ALL) return

        synchronized(lock) {
            preferences.edit()
                .putString(keyFor(messageReference, category), CATEGORY_SOURCE_USER)
                .apply()
            assignments.value = readAssignments()
        }
    }

    override fun removeCategory(messageReference: String, category: SmartCategory) {
        synchronized(lock) {
            preferences.edit().remove(keyFor(messageReference, category)).apply()
            assignments.value = readAssignments()
        }
    }

    private fun readAssignments(): Map<String, Set<SmartCategory>> = preferences.all.keys
        .mapNotNull { key ->
            if (!key.startsWith(KEY_PREFIX)) return@mapNotNull null
            val assignmentKey = key.removePrefix(KEY_PREFIX)
            val separatorIndex = assignmentKey.lastIndexOf(KEY_SEPARATOR)
            if (separatorIndex <= 0) return@mapNotNull null

            val messageReference = assignmentKey.substring(0, separatorIndex)
            val categoryName = assignmentKey.substring(separatorIndex + KEY_SEPARATOR.length)
            val category = SmartCategory.entries.firstOrNull { it.name == categoryName }
                ?: return@mapNotNull null
            if (category == SmartCategory.ALL) return@mapNotNull null
            messageReference to category
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, categories) -> categories.toSet() }

    private fun keyFor(messageReference: String, category: SmartCategory): String =
        KEY_PREFIX + messageReference + KEY_SEPARATOR + category.name

    private companion object {
        const val PREFERENCES_NAME = "linus_mail_smart_categories"
        const val KEY_PREFIX = "message."
        const val KEY_SEPARATOR = "."
        const val CATEGORY_SOURCE_USER = "USER"
    }
}
