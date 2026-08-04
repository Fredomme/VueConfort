package fr.vueconfort.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingAssessmentTest {
    @Test
    fun wordsPerMinute_usesElapsedMinutes() {
        val assessment = ReadingAssessment(
            startedAtMillis = 0,
            completedAtMillis = 120_000,
            wordCount = 300,
            readingDurationMillis = 120_000
        )
        assertEquals(150f, assessment.wordsPerMinute, 0.001f)
    }

    @Test
    fun wordsPerMinute_isZeroForInvalidDuration() {
        val assessment = ReadingAssessment(
            startedAtMillis = 0,
            completedAtMillis = 0,
            wordCount = 300,
            readingDurationMillis = 0
        )
        assertEquals(0f, assessment.wordsPerMinute)
    }
}
