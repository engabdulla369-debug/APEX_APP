package com.example

import com.example.data.model.Flashcard
import com.example.data.model.StudySession
import com.example.utils.AnalyticsCalculator
import com.example.utils.ScientificCalculatorEngine
import com.example.utils.SpacedRepetitionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExampleUnitTest {
    @Test
    fun scientificCalculator_evaluatesEngineeringExpressions() {
        val trig = ScientificCalculatorEngine.evaluate("sin(30)+cos(60)", useDegrees = true)
        assertTrue(trig is ScientificCalculatorEngine.CalcResult.Value)
        assertEquals("1", (trig as ScientificCalculatorEngine.CalcResult.Value).formatted)

        val powerFact = ScientificCalculatorEngine.evaluate("2^3 + 4!", useDegrees = true)
        assertTrue(powerFact is ScientificCalculatorEngine.CalcResult.Value)
        assertEquals("32", (powerFact as ScientificCalculatorEngine.CalcResult.Value).formatted)

        val divZero = ScientificCalculatorEngine.evaluate("10/0", useDegrees = true)
        assertTrue(divZero is ScientificCalculatorEngine.CalcResult.Error)
    }

    @Test
    fun spacedRepetitionEngine_requeuesHardAndAdvancesEasyCards() {
        val now = 1_700_000_000_000L
        val card = Flashcard(
            id = "c1",
            deckName = "Math",
            frontText = "d/dx sin(x)",
            backText = "cos(x)"
        )

        val easy1 = SpacedRepetitionEngine.scheduleNext(card, SpacedRepetitionEngine.RATING_EASY, now)
        assertEquals(SpacedRepetitionEngine.RATING_EASY, easy1.difficultyRating)
        assertEquals(2, easy1.intervalDays)
        assertEquals(1, easy1.repetitions)
        assertEquals(265, easy1.easeFactorPercent)
        assertFalse(SpacedRepetitionEngine.isDueOrHard(easy1, now + 1000L))

        val easy2 = SpacedRepetitionEngine.scheduleNext(easy1, SpacedRepetitionEngine.RATING_EASY, now)
        assertEquals(6, easy2.intervalDays)
        assertEquals(2, easy2.repetitions)

        val hardReset = SpacedRepetitionEngine.scheduleNext(easy2, SpacedRepetitionEngine.RATING_HARD, now)
        assertEquals(SpacedRepetitionEngine.RATING_HARD, hardReset.difficultyRating)
        assertEquals(0, hardReset.intervalDays)
        assertEquals(0, hardReset.repetitions)
        assertTrue(SpacedRepetitionEngine.isDueOrHard(hardReset, now + 1000L))
    }

    @Test
    fun analyticsCalculator_computesDuolingoStreaksAndChartBuckets() {
        val dayMs = 24L * 3600L * 1000L
        val now = 1_700_050_000_000L
        val sessions = listOf(
            StudySession(id = "s1", durationSeconds = 1800L, completedAtMillis = now),
            StudySession(id = "s2", durationSeconds = 3600L, completedAtMillis = now - dayMs),
            StudySession(id = "s3", durationSeconds = 2400L, completedAtMillis = now - 2L * dayMs),
            StudySession(id = "s4", durationSeconds = 1200L, completedAtMillis = now - 5L * dayMs)
        )

        val streak = AnalyticsCalculator.computeStreaks(sessions, now)
        assertEquals(3, streak.currentStreakDays)
        assertEquals(3, streak.longestStreakDays)
        assertTrue(streak.studiedToday)
        assertEquals(7, streak.lastSevenDaysActive.size)
        assertTrue(streak.lastSevenDaysActive.last())

        val weekly = AnalyticsCalculator.weeklyBuckets(sessions, Locale.ENGLISH, now)
        assertEquals(7, weekly.size)
        assertEquals(30f, weekly.last().minutes, 0.01f)

        val monthly = AnalyticsCalculator.monthlyBuckets(sessions, "W", now)
        assertEquals(4, monthly.size)
        assertEquals(150f, monthly.last().minutes, 0.01f)
    }
}
