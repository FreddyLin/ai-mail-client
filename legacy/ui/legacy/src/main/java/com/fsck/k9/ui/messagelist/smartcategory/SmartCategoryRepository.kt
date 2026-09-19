package com.fsck.k9.ui.messagelist.smartcategory

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.feature.mail.message.list.ui.state.SmartCategory

interface SmartCategoryRepository {
    fun observeAssignments(): Flow<Map<String, SmartCategoryAssignments>>

    fun assignCategory(messageReference: String, category: SmartCategory)

    fun removeCategory(messageReference: String, category: SmartCategory)

    fun applyRuleCategories(messageReference: String, categories: Set<SmartCategory>)
}

data class SmartCategoryAssignments(
    val assigned: Map<SmartCategory, SmartCategoryAssignmentSource> = emptyMap(),
    val suppressed: Set<SmartCategory> = emptySet(),
)

enum class SmartCategoryAssignmentSource {
    USER,
    RULE,
}

class SharedPreferencesSmartCategoryRepository(
    context: Context,
) : SmartCategoryRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val assignments = MutableStateFlow(readAssignments())
    private val lock = Any()

    override fun observeAssignments(): Flow<Map<String, SmartCategoryAssignments>> = assignments

    override fun assignCategory(messageReference: String, category: SmartCategory) {
        if (category == SmartCategory.ALL) return

        synchronized(lock) {
            preferences.edit()
                .putString(keyFor(messageReference, category), SmartCategoryAssignmentSource.USER.name)
                .remove(suppressedKeyFor(messageReference, category))
                .apply()
            assignments.value = readAssignments()
        }
    }

    override fun removeCategory(messageReference: String, category: SmartCategory) {
        if (category == SmartCategory.ALL) return

        synchronized(lock) {
            preferences.edit()
                .remove(keyFor(messageReference, category))
                .putString(suppressedKeyFor(messageReference, category), SmartCategoryAssignmentSource.USER.name)
                .apply()
            assignments.value = readAssignments()
        }
    }

    override fun applyRuleCategories(messageReference: String, categories: Set<SmartCategory>) {
        val ruleCategories = categories - SmartCategory.ALL
        synchronized(lock) {
            val current = assignments.value[messageReference] ?: SmartCategoryAssignments()
            val userCategories = current.assigned
                .filterValues { it == SmartCategoryAssignmentSource.USER }
                .keys
            val nextRuleCategories = ruleCategories - current.suppressed - userCategories
            val currentRuleCategories = current.assigned
                .filterValues { it == SmartCategoryAssignmentSource.RULE }
                .keys

            preferences.edit().apply {
                (currentRuleCategories - nextRuleCategories).forEach { category ->
                    remove(keyFor(messageReference, category))
                }
                (nextRuleCategories - currentRuleCategories).forEach { category ->
                    putString(keyFor(messageReference, category), SmartCategoryAssignmentSource.RULE.name)
                }
                apply()
            }
            assignments.value = readAssignments()
        }
    }

    private fun readAssignments(): Map<String, SmartCategoryAssignments> {
        val assignmentMap = mutableMapOf<String, MutableMap<SmartCategory, SmartCategoryAssignmentSource>>()
        val suppressedMap = mutableMapOf<String, MutableSet<SmartCategory>>()

        preferences.all.keys.forEach { key ->
            val prefix = when {
                key.startsWith(ASSIGNMENT_PREFIX) -> ASSIGNMENT_PREFIX
                key.startsWith(SUPPRESSED_PREFIX) -> SUPPRESSED_PREFIX
                else -> return@forEach
            }
            val assignmentKey = key.removePrefix(prefix)
            val separatorIndex = assignmentKey.lastIndexOf(KEY_SEPARATOR)
            if (separatorIndex <= 0) return@forEach

            val messageReference = assignmentKey.substring(0, separatorIndex)
            val categoryName = assignmentKey.substring(separatorIndex + KEY_SEPARATOR.length)
            val category = SmartCategory.entries.firstOrNull { it.name == categoryName }
                ?: return@forEach
            if (category == SmartCategory.ALL) return@forEach

            if (prefix == ASSIGNMENT_PREFIX) {
                val source = preferences.getString(key, SmartCategoryAssignmentSource.USER.name)
                    ?.let { value -> SmartCategoryAssignmentSource.entries.firstOrNull { it.name == value } }
                    ?: SmartCategoryAssignmentSource.USER
                assignmentMap.getOrPut(messageReference) { mutableMapOf() }[category] = source
            } else {
                suppressedMap.getOrPut(messageReference) { mutableSetOf() }.add(category)
            }
        }

        return (assignmentMap.keys + suppressedMap.keys).associateWith { messageReference ->
            SmartCategoryAssignments(
                assigned = assignmentMap[messageReference].orEmpty(),
                suppressed = suppressedMap[messageReference].orEmpty(),
            )
        }
    }

    private fun keyFor(messageReference: String, category: SmartCategory): String =
        ASSIGNMENT_PREFIX + messageReference + KEY_SEPARATOR + category.name

    private fun suppressedKeyFor(messageReference: String, category: SmartCategory): String =
        SUPPRESSED_PREFIX + messageReference + KEY_SEPARATOR + category.name

    private companion object {
        const val PREFERENCES_NAME = "linus_mail_smart_categories"
        const val ASSIGNMENT_PREFIX = "message."
        const val SUPPRESSED_PREFIX = "suppressed."
        const val KEY_SEPARATOR = "."
    }
}
