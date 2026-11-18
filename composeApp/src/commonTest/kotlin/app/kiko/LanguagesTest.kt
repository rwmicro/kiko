package app.kiko

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguagesTest {
    @Test
    fun frenchAndIndonesianSpeakersFormAnExchange() {
        assertTrue(
            isReciprocalExchange(
                myNative = setOf("fr"), myLearning = setOf("id"),
                theirNative = setOf("id"), theirLearning = setOf("fr"),
            )
        )
    }

    @Test
    fun twoLearnersOfTheSameLanguageDoNotMatch() {
        assertFalse(
            isReciprocalExchange(
                myNative = setOf("fr"), myLearning = setOf("id"),
                theirNative = setOf("fr"), theirLearning = setOf("id"),
            )
        )
    }

    @Test
    fun betaOffersFrenchAndIndonesian() {
        assertEquals(listOf("fr", "id"), Languages.beta.map { it.code })
    }
}
