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

    @Test
    fun `ai confirmation clears suppression only for selected category`() = runBlocking {
        val messageReference = "account:folder:3"
        preferences.edit().clear().commit()
        val testSubject = SharedPreferencesSmartCategoryRepository(context)

        testSubject.applyRuleCategories(messageReference, setOf(SmartCategory.NEWSLETTER, SmartCategory.INVOICE))
        testSubject.removeCategory(messageReference, SmartCategory.NEWSLETTER)
        testSubject.removeCategory(messageReference, SmartCategory.INVOICE)
        testSubject.assignCategories(
            messageReference,
            setOf(SmartCategory.NEWSLETTER),
            SmartCategoryAssignmentSource.AI,
        )

        val assignment = testSubject.observeAssignments().first().getValue(messageReference)
        assertEquals(SmartCategoryAssignmentSource.AI, assignment.assigned[SmartCategory.NEWSLETTER])
        assertTrue(SmartCategory.NEWSLETTER !in assignment.suppressed)
        assertTrue(SmartCategory.INVOICE in assignment.suppressed)
    }

    @Test
    fun `ai assignment is protected from later rule updates`() = runBlocking {
        val messageReference = "account:folder:4"
        preferences.edit().clear().commit()
        val testSubject = SharedPreferencesSmartCategoryRepository(context)

        testSubject.assignCategories(
            messageReference,
            setOf(SmartCategory.NEWSLETTER),
            SmartCategoryAssignmentSource.AI,
        )
        testSubject.applyRuleCategories(messageReference, setOf(SmartCategory.INVOICE))

        val assignment = testSubject.observeAssignments().first().getValue(messageReference)
        assertEquals(SmartCategoryAssignmentSource.AI, assignment.assigned[SmartCategory.NEWSLETTER])
        assertTrue(SmartCategory.INVOICE !in assignment.assigned)
    }

    @Test
    fun `ai assignment does not replace user or rule assignment`() = runBlocking {
        val messageReference = "account:folder:5"
        preferences.edit().clear().commit()
        val testSubject = SharedPreferencesSmartCategoryRepository(context)

        testSubject.assignCategory(messageReference, SmartCategory.IMPORTANT)
        testSubject.applyRuleCategories(messageReference, setOf(SmartCategory.INVOICE))
        testSubject.assignCategories(
            messageReference,
            setOf(SmartCategory.IMPORTANT, SmartCategory.INVOICE, SmartCategory.ORDER),
            SmartCategoryAssignmentSource.AI,
        )

        val assignment = testSubject.observeAssignments().first().getValue(messageReference)
        assertEquals(SmartCategoryAssignmentSource.USER, assignment.assigned[SmartCategory.IMPORTANT])
        assertEquals(SmartCategoryAssignmentSource.RULE, assignment.assigned[SmartCategory.INVOICE])
        assertEquals(SmartCategoryAssignmentSource.AI, assignment.assigned[SmartCategory.ORDER])
    }

    @Test
    fun `all is never persisted as an ai category`() = runBlocking {
        val messageReference = "account:folder:6"
        preferences.edit().clear().commit()
        val testSubject = SharedPreferencesSmartCategoryRepository(context)

        assertTrue(
            testSubject.assignCategories(
                messageReference,
                setOf(SmartCategory.ALL),
                SmartCategoryAssignmentSource.AI,
            ),
        )

        assertTrue(testSubject.observeAssignments().first().isEmpty())
    }

    private companion object {
        const val PREFERENCES_NAME = "linus_mail_smart_categories"
    }
}
