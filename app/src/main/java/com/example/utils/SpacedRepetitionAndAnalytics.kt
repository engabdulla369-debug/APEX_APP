package com.example.utils

import com.example.data.model.Flashcard
import com.example.data.model.StudySession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object SpacedRepetitionEngine {
    const val RATING_NEW = "new"
    const val RATING_HARD = "hard"
    const val RATING_MEDIUM = "medium"
    const val RATING_EASY = "easy"

    private const val DAY_MS = 24L * 3600L * 1000L
    private const val HARD_REPEAT_MS = 60L * 1000L // 1 minute -> immediately available in Hard / Due queue

    fun scheduleNext(
        card: Flashcard,
        rating: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Flashcard {
        val currentEase = card.easeFactorPercent.coerceIn(130, 350)
        return when (rating) {
            RATING_HARD -> {
                val nextEase = (currentEase - 20).coerceAtLeast(130)
                card.copy(
                    difficultyRating = RATING_HARD,
                    easeFactorPercent = nextEase,
                    intervalDays = 0,
                    repetitions = 0,
                    reviewCount = card.reviewCount + 1,
                    nextReviewAtMillis = nowMillis + HARD_REPEAT_MS,
                    lastReviewedAtMillis = nowMillis
                )
            }
            RATING_MEDIUM -> {
                val nextReps = card.repetitions + 1
                val nextInterval = when (nextReps) {
                    1 -> 1
                    2 -> 3
                    else -> ((card.intervalDays.coerceAtLeast(1) * currentEase) / 100.0).roundToInt().coerceIn(1, 3650)
                }
                val nextEase = (currentEase - 5).coerceAtLeast(130)
                card.copy(
                    difficultyRating = RATING_MEDIUM,
                    easeFactorPercent = nextEase,
                    intervalDays = nextInterval,
                    repetitions = nextReps,
                    reviewCount = card.reviewCount + 1,
                    nextReviewAtMillis = nowMillis + nextInterval * DAY_MS,
                    lastReviewedAtMillis = nowMillis
                )
            }
            RATING_EASY -> {
                val nextReps = card.repetitions + 1
                val nextInterval = when (nextReps) {
                    1 -> 2
                    2 -> 6
                    else -> (((card.intervalDays.coerceAtLeast(2) * currentEase) / 100.0) * 1.3)
                        .roundToInt()
                        .coerceIn(2, 3650)
                }
                val nextEase = (currentEase + 15).coerceAtMost(350)
                card.copy(
                    difficultyRating = RATING_EASY,
                    easeFactorPercent = nextEase,
                    intervalDays = nextInterval,
                    repetitions = nextReps,
                    reviewCount = card.reviewCount + 1,
                    nextReviewAtMillis = nowMillis + nextInterval * DAY_MS,
                    lastReviewedAtMillis = nowMillis
                )
            }
            else -> card
        }
    }

    fun isDueOrHard(card: Flashcard, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return card.difficultyRating == RATING_NEW ||
            card.difficultyRating == RATING_HARD ||
            card.nextReviewAtMillis <= nowMillis
    }
}

data class ChartBucket(
    val label: String,
    val minutes: Float,
    val isCurrent: Boolean = false
)

data class StreakSummary(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val studiedToday: Boolean,
    val lastSevenDaysActive: List<Boolean>
)

object AnalyticsCalculator {
    private fun dayKeyFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun shortDayFormatter(locale: Locale): SimpleDateFormat =
        SimpleDateFormat("EEE", locale)

    fun dayKey(millis: Long): String = dayKeyFormatter().format(Date(millis))

    fun computeStreaks(
        sessions: List<StudySession>,
        nowMillis: Long = System.currentTimeMillis()
    ): StreakSummary {
        val activeDays = sessions
            .filter { it.completedAtMillis > 0L && it.durationSeconds >= 10L }
            .map { dayKey(it.completedAtMillis) }
            .toSortedSet()

        val todayStr = dayKey(nowMillis)
        val studiedToday = activeDays.contains(todayStr)

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val last7 = (6 downTo 0).map { offset ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            activeDays.contains(dayKey(c.timeInMillis))
        }

        // Current streak counts consecutive days backward starting from today (or yesterday if not studied today yet)
        var currentStreak = 0
        val cursor = (cal.clone() as Calendar)
        if (!studiedToday) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        while (activeDays.contains(dayKey(cursor.timeInMillis))) {
            currentStreak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }

        // Longest consecutive streak across all recorded days
        var longest = 0
        if (activeDays.isNotEmpty()) {
            val fmt = dayKeyFormatter()
            var run = 0
            var prevMillis: Long? = null
            for (k in activeDays) {
                val d = fmt.parse(k)?.time ?: continue
                if (prevMillis == null) {
                    run = 1
                } else {
                    val diffDays = ((d - prevMillis) / (24L * 3600L * 1000L)).toInt()
                    run = if (diffDays == 1) run + 1 else 1
                }
                if (run > longest) longest = run
                prevMillis = d
            }
        }
        if (currentStreak > longest) longest = currentStreak

        return StreakSummary(
            currentStreakDays = currentStreak,
            longestStreakDays = longest,
            studiedToday = studiedToday,
            lastSevenDaysActive = last7
        )
    }

    fun weeklyBuckets(
        sessions: List<StudySession>,
        locale: Locale = Locale("ar"),
        nowMillis: Long = System.currentTimeMillis()
    ): List<ChartBucket> {
        val byDay = sessions.groupBy { dayKey(it.completedAtMillis) }
        val dayFmt = shortDayFormatter(locale)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return (6 downTo 0).map { offset ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val key = dayKey(c.timeInMillis)
            val mins = (byDay[key]?.sumOf { it.durationSeconds } ?: 0L) / 60f
            ChartBucket(
                label = dayFmt.format(c.time),
                minutes = mins,
                isCurrent = offset == 0
            )
        }
    }

    fun monthlyBuckets(
        sessions: List<StudySession>,
        weekPrefix: String = "W",
        nowMillis: Long = System.currentTimeMillis()
    ): List<ChartBucket> {
        val dayMs = 24L * 3600L * 1000L
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val endToday = cal.timeInMillis
        return (3 downTo 0).map { weekOffset ->
            val bucketEnd = endToday - weekOffset * 7L * dayMs
            val bucketStart = bucketEnd - 7L * dayMs
            val totalSec = sessions
                .filter { it.completedAtMillis in (bucketStart + 1)..bucketEnd }
                .sumOf { it.durationSeconds }
            val weekNum = 4 - weekOffset
            ChartBucket(
                label = "$weekPrefix$weekNum",
                minutes = totalSec / 60f,
                isCurrent = weekOffset == 0
            )
        }
    }
}
