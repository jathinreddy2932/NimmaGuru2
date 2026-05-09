package com.nimmaguru.app

import org.junit.Test
import org.junit.Assert.*

/**
 * NimmaGuru Logic Tests
 * Note: AppState depends on Firebase, so these tests primarily verify
 * data models and independent logic.
 */
class ExampleUnitTest {

    @Test
    fun session_model_isCorrect() {
        val session = Session(
            id = "test_1",
            guruName = "Dr. Rao",
            subject = "Science",
            time = "10:00 AM"
        )
        assertEquals("Dr. Rao", session.guruName)
        assertEquals("Science", session.subject)
    }

    @Test
    fun arithmetic_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}