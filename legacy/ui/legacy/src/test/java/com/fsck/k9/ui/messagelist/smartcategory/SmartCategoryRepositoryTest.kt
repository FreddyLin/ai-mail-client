package com.fsck.k9.ui.messagelist.smartcategory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.thunderbird.feature.mail.message.list.ui.state.SmartCategory

class SmartCategoryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Test
    fun `existing v01 assignment is treated as user assignment`() = runBlocking {
        val messageReference = "account:folder:1"
        preferences.edit().clear().putString("message.$messageReference.NEWSLETTER", "USER").commit()
        val testSubject = SharedPreferencesSmartCategoryRepository(context)

        val assignment = testSubject.observeAssignments().first().getValue(messageReference)

        assertEquals(SmartCategoryAssignmentSource.USER, assignment.assigned[SmartCategory.NEWSLETTER])
    }

    @Test
    fun `user removal suppresses a later rule assignment`() = runBlocking {
        val messageReference = "account:folder:2"
        preferences.edit().clear().commit()
        val testSubject = SharedPreferencesSmartCategoryRepository(context)

        testSubject.applyRuleCategories(messageReference, setOf(SmartCategory.NEWSLETTER))
        testSubject.removeCategory(messageReference, SmartCategory.NEWSLETTER)
        testSubject.applyRuleCategories(messageReference, setOf(SmartCategory.NEWSLETTER))

        val assignment = testSubject.observeAssignments().first().getValue(messageReference)
        assertTrue(SmartCategory.NEWSLETTER !in assignment.assigned)
        assertTrue(SmartCategory.NEWSLETTER in assignment.suppressed)
    }

    private companion object {
        const val PREFERENCES_NAME = "linus_mail_smart_categories"
    }
}
