package com.example.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.Assignment
import com.example.data.model.AssignmentProgress
import com.example.data.model.Course
import com.example.data.model.Flashcard
import com.example.data.model.LeaderboardEntry
import com.example.data.model.PersonalTask
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoleBadge
import com.example.ui.components.ScientificCalculatorPanel
import com.example.ui.i18n.LocalAppStrings
import kotlinx.coroutines.delay

fun formatDurationHms(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = s % 60L
    return String.format("%02d:%02d:%02d", h, m, sec)
}

fun formatHoursMinutesAr(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600L
    val mins = (totalSeconds % 3600L) / 60L
    return when {
        hrs > 0 -> "$hrs س ${mins} د"
        mins > 0 -> "$mins دقيقة"
        else -> "$totalSeconds ث"
    }
}

@Composable
fun StudyFocusHubScreen(
    profile: UserProfile,
    courses: List<Course>,
    sessions: List<StudySession>,
    leaderboard: List<LeaderboardEntry>,
    flashcards: List<Flashcard> = emptyList(),
    assignments: List<Assignment> = emptyList(),
    assignmentProgress: Map<String, AssignmentProgress> = emptyMap(),
    personalTasks: List<PersonalTask> = emptyList(),
    initialTabIndex: Int = 0,
    onRecordSession: (mode: String, courseTitle: String, durationSeconds: Long, pointsEarned: Int, isPomodoro: Boolean) -> Unit,
    onCreateFlashcard: (deckName: String, front: String, back: String, courseId: String, courseTitle: String) -> Unit = { _, _, _, _, _ -> },
    onReviewFlashcard: (Flashcard, String) -> Unit = { _, _ -> },
    onDeleteFlashcard: (String) -> Unit = {},
    onSeedFlashcards: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 6)) } // 0=Pomodoro, 1=LongTimer, 2=FocusZone, 3=Flashcards, 4=Analytics, 5=Leaderboard, 6=SciCalc
    LaunchedEffect(initialTabIndex) {
        selectedTab = initialTabIndex.coerceIn(0, 6)
    }
    var selectedCourseTitle by remember(courses) { mutableStateOf(courses.firstOrNull()?.title ?: "مذاكرة عامة") }
    var showFocusOverlay by remember { mutableStateOf(false) }
    var leaderboardSortMode by remember { mutableIntStateOf(0) } // 0=Points, 1=Hours, 2=Streak

    val sortedLeaderboard = remember(leaderboard, leaderboardSortMode) {
        when (leaderboardSortMode) {
            1 -> leaderboard.sortedByDescending { it.totalStudySeconds }
            2 -> leaderboard.sortedByDescending { it.currentStreakDays }
            else -> leaderboard.sortedByDescending { it.focusPoints }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Sleek Stats Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = strings.studyLabTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniStatPill(
                        label = strings.focusPointsLabel,
                        value = "${profile.focusPoints}",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatPill(
                        label = strings.studyHoursLabel,
                        value = formatHoursMinutesAr(profile.totalStudySeconds),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatPill(
                        label = strings.currentStreakLabel,
                        value = "🔥 ${profile.currentStreakDays}",
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.tabPomodoro) }, modifier = Modifier.testTag("study_tab_pomodoro"))
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(strings.tabLongTimer) }, modifier = Modifier.testTag("study_tab_long"))
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(strings.tabFocusZone) }, modifier = Modifier.testTag("study_tab_focus"))
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("${strings.tabFlashcards} (${flashcards.size})") }, modifier = Modifier.testTag("study_tab_flashcards"))
            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text(strings.tabAnalytics) }, modifier = Modifier.testTag("study_tab_analytics"))
            Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text(strings.tabLeaderboard) }, modifier = Modifier.testTag("study_tab_leaderboard"))
            Tab(selected = selectedTab == 6, onClick = { selectedTab = 6 }, text = { Text(strings.tabCalculator) }, modifier = Modifier.testTag("study_tab_calc"))
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (courses.isNotEmpty() && selectedTab <= 2) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCourseTitle == "مذاكرة عامة",
                                onClick = { selectedCourseTitle = "مذاكرة عامة" },
                                label = { Text("مذاكرة عامة") }
                            )
                        }
                        items(courses, key = { it.id }) { c ->
                            FilterChip(
                                selected = selectedCourseTitle == c.title,
                                onClick = { selectedCourseTitle = c.title },
                                label = { Text(c.title) }
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    item {
                        PomodoroSectionCard(
                            courseTitle = selectedCourseTitle,
                            onSaveSession = { durSec, pts, completed ->
                                onRecordSession("pomodoro", selectedCourseTitle, durSec, pts, completed)
                            }
                        )
                    }
                    if (sessions.isNotEmpty()) {
                        item {
                            RecentSessionsCard(sessions = sessions.take(6))
                        }
                    }
                }

                1 -> {
                    item {
                        MarathonStopwatchCard(
                            courseTitle = selectedCourseTitle,
                            longestRecordedSec = profile.longestSessionSeconds,
                            onSaveSession = { durSec, pts ->
                                onRecordSession("long_timer", selectedCourseTitle, durSec, pts, false)
                            }
                        )
                    }
                    if (sessions.isNotEmpty()) {
                        item {
                            RecentSessionsCard(sessions = sessions.filter { it.mode == "long_timer" }.take(6))
                        }
                    }
                }

                2 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(22.dp)
                            ) {
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = CircleShape,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(34.dp))
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "منطقة التركيز العميق (وضع Flip)",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "تُغلق المشتتات وتتابع بقاءك داخل شاشة المذاكرة بدون خروج من التطبيق. مغادرة الشاشة توقف المؤقت وتنبهك، وإكمال الجلسة يمنحك +50% نقاط إضافية في لوحة الشرف!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(18.dp))
                                Button(
                                    onClick = { showFocusOverlay = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("start_flip_focus_button")
                                ) {
                                    Icon(Icons.Default.SelfImprovement, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("ابدأ جلسة عدم التشتيت الآن")
                                }
                            }
                        }
                    }
                }

                3 -> {
                    item {
                        FlashcardsSection(
                            flashcards = flashcards,
                            courses = courses,
                            onCreateCard = onCreateFlashcard,
                            onReviewCard = onReviewFlashcard,
                            onDeleteCard = onDeleteFlashcard,
                            onSeedStarterDeck = onSeedFlashcards
                        )
                    }
                }

                4 -> {
                    item {
                        StudentAnalyticsDashboardSection(
                            profile = profile,
                            courses = courses,
                            sessions = sessions,
                            assignments = assignments,
                            assignmentProgress = assignmentProgress,
                            personalTasks = personalTasks,
                            flashcards = flashcards
                        )
                    }
                }

                5 -> {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = leaderboardSortMode == 0,
                                    onClick = { leaderboardSortMode = 0 },
                                    label = { Text(strings.leaderboardSortPoints) },
                                    modifier = Modifier.testTag("leaderboard_sort_points")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = leaderboardSortMode == 1,
                                    onClick = { leaderboardSortMode = 1 },
                                    label = { Text(strings.leaderboardSortHours) },
                                    modifier = Modifier.testTag("leaderboard_sort_hours")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = leaderboardSortMode == 2,
                                    onClick = { leaderboardSortMode = 2 },
                                    label = { Text(strings.leaderboardSortStreak) },
                                    modifier = Modifier.testTag("leaderboard_sort_streak")
                                )
                            }
                        }
                    }

                    if (sortedLeaderboard.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "لوحة الشرف جاهزة لأول بطل!",
                                subtitle = "أكمل أي جلسة بومودورو أو تايمر مفتوح لتسجيل اسمك ونقاطك في قائمة الدفعة."
                            )
                        }
                    } else {
                        if (sortedLeaderboard.size >= 2) {
                            item {
                                OlympicPodiumCard(
                                    topThree = sortedLeaderboard.take(3),
                                    sortMode = leaderboardSortMode,
                                    currentUserId = profile.userId
                                )
                            }
                        }
                        itemsIndexed(sortedLeaderboard, key = { _, e -> e.userId }) { idx, entry ->
                            LeaderboardRankCard(
                                rank = idx + 1,
                                entry = entry,
                                isCurrentUser = entry.userId == profile.userId
                            )
                        }
                    }
                }

                6 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.tabCalculator, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(10.dp))
                                ScientificCalculatorPanel()
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showFocusOverlay) {
        FlipFocusZoneOverlay(
            courseTitle = selectedCourseTitle,
            onDismiss = { showFocusOverlay = false },
            onFinishAndSave = { durSec, pts ->
                onRecordSession("focus_zone", selectedCourseTitle, durSec, pts, false)
                showFocusOverlay = false
            }
        )
    }
}

@Composable
private fun MiniStatPill(
    label: String,
    value: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(color = color, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

@Composable
private fun PomodoroSectionCard(
    courseTitle: String,
    onSaveSession: (durationSeconds: Long, points: Int, completed: Boolean) -> Unit
) {
    var focusMinutesPreset by remember { mutableIntStateOf(25) } // 25/5 or 50/10
    var isBreakPhase by remember { mutableStateOf(false) }
    val targetTotalSeconds = remember(focusMinutesPreset, isBreakPhase) {
        if (isBreakPhase) (if (focusMinutesPreset == 50) 10 else 5) * 60L else focusMinutesPreset * 60L
    }
    var remainingSeconds by remember(targetTotalSeconds) { mutableLongStateOf(targetTotalSeconds) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, targetTotalSeconds) {
        while (isRunning && remainingSeconds > 0L) {
            delay(1000L)
            remainingSeconds = (remainingSeconds - 1L).coerceAtLeast(0L)
            if (remainingSeconds == 0L) {
                isRunning = false
                if (!isBreakPhase) {
                    val pts = if (focusMinutesPreset == 50) 55 else 25
                    onSaveSession(targetTotalSeconds, pts, true)
                    isBreakPhase = true
                } else {
                    isBreakPhase = false
                }
            }
        }
    }

    val progress = if (targetTotalSeconds > 0L) {
        (targetTotalSeconds - remainingSeconds).toFloat() / targetTotalSeconds.toFloat()
    } else 0f
    val elapsedFocusSec = (targetTotalSeconds - remainingSeconds).coerceAtLeast(0L)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isBreakPhase && focusMinutesPreset == 25,
                    onClick = { if (!isRunning) { isBreakPhase = false; focusMinutesPreset = 25 } },
                    label = { Text("25 د تركيز / 5 راحة") }
                )
                FilterChip(
                    selected = !isBreakPhase && focusMinutesPreset == 50,
                    onClick = { if (!isRunning) { isBreakPhase = false; focusMinutesPreset = 50 } },
                    label = { Text("50 د عميق / 10 راحة") }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isBreakPhase) "استراحة قصيرة — $courseTitle" else "جلسة تركيز — $courseTitle",
                style = MaterialTheme.typography.labelLarge,
                color = if (isBreakPhase) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val ringColor = if (isBreakPhase) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = stroke
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val m = remainingSeconds / 60L
                    val s = remainingSeconds % 60L
                    Text(
                        text = String.format("%02d:%02d", m, s),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isBreakPhase) "وقت الراحة" else "متبقي",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { isRunning = !isRunning },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("pomodoro_toggle_button")
                ) {
                    Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRunning) "إيقاف مؤقت" else "تشغيل")
                }
                OutlinedButton(
                    onClick = {
                        isRunning = false
                        remainingSeconds = targetTotalSeconds
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("إعادة")
                }
            }

            if (!isBreakPhase && elapsedFocusSec >= 60L) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        isRunning = false
                        val pts = ((elapsedFocusSec / 60L).toInt()).coerceAtLeast(1)
                        onSaveSession(elapsedFocusSec, pts, false)
                        remainingSeconds = targetTotalSeconds
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ الوقت المنجز الآن (+${(elapsedFocusSec / 60L).coerceAtLeast(1)} نقطة)")
                }
            }
        }
    }
}

@Composable
private fun MarathonStopwatchCard(
    courseTitle: String,
    longestRecordedSec: Long,
    onSaveSession: (durationSeconds: Long, points: Int) -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }
    var accumulatedMs by remember { mutableLongStateOf(0L) }
    var runStartRealtimeMs by remember { mutableLongStateOf(0L) }
    var displaySeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            runStartRealtimeMs = SystemClock.elapsedRealtime()
            while (isRunning) {
                val totalMs = accumulatedMs + (SystemClock.elapsedRealtime() - runStartRealtimeMs)
                displaySeconds = totalMs / 1000L
                delay(500L)
            }
        }
    }

    val hrs = displaySeconds / 3600L
    val milestoneLabel = when {
        hrs >= 15 -> "أسطورة التحصيل (15+ ساعة متواصلة!)"
        hrs >= 10 -> "ماراثون 10 ساعات أكاديمية!"
        hrs >= 5 -> "إنجاز 5 ساعات مذاكرة متصلة!"
        hrs >= 1 -> "تجاوزت الساعة الأولى — استمر!"
        else -> "مؤقت دقيق لجلسات المذاكرة الممتدة حتى 15+ ساعة"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text("التايمر المفتوح ($courseTitle)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = milestoneLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 26.dp)
                ) {
                    Text(
                        text = formatDurationHms(displaySeconds),
                        style = MaterialTheme.typography.displayMedium,
                        color = Color(0xFF2DD4BF),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "أطول جلسة سابقة لك: ${formatHoursMinutesAr(longestRecordedSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (isRunning) {
                            accumulatedMs += (SystemClock.elapsedRealtime() - runStartRealtimeMs)
                            displaySeconds = accumulatedMs / 1000L
                            isRunning = false
                        } else {
                            isRunning = true
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("long_timer_toggle_button")
                ) {
                    Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRunning) "إيقاف مؤقت" else if (displaySeconds > 0L) "استئناف" else "بدء الجلسة")
                }

                if (displaySeconds >= 10L) {
                    Button(
                        onClick = {
                            if (isRunning) {
                                accumulatedMs += (SystemClock.elapsedRealtime() - runStartRealtimeMs)
                                displaySeconds = accumulatedMs / 1000L
                                isRunning = false
                            }
                            val savedSec = displaySeconds.coerceAtLeast(10L)
                            val pts = ((savedSec / 60L).toInt()).coerceIn(5, 5000)
                            onSaveSession(savedSec, pts)
                            accumulatedMs = 0L
                            displaySeconds = 0L
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("إنهاء وحفظ")
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipFocusZoneOverlay(
    courseTitle: String,
    onDismiss: () -> Unit,
    onFinishAndSave: (durationSeconds: Long, pointsEarned: Int) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var isFocused by remember { mutableStateOf(true) }
    var distractionsCount by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                isFocused = false
                distractionsCount++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isFocused) {
        while (isFocused) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF090E1A), Color(0xFF0F172A), Color(0xFF132238))
                    )
                )
                .padding(24.dp)
                .testTag("flip_focus_overlay")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    color = Color(0xFF2DD4BF).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "وضع عدم التشتيت نشط • $courseTitle",
                        color = Color(0xFF2DD4BF),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    text = formatDurationHms(elapsedSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "اترك الهاتف الآن وركّز في المذاكرة (+50% نقاط تركيز إضافية)",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (!isFocused) {
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        color = Color(0xFF7F1D1D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFCA5A5))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "تم رصد مغادرة الشاشة ($distractionsCount)!",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { isFocused = true }) {
                                Text("العودة للتركيز واستكمال العداد")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(44.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("خروج بدون حفظ")
                    }
                    Button(
                        onClick = {
                            val sec = elapsedSeconds.coerceAtLeast(1L)
                            val basePts = ((sec / 40L).toInt()).coerceAtLeast(5)
                            val bonusPts = (basePts * 1.5).toInt().coerceIn(5, 5000)
                            onFinishAndSave(sec, bonusPts)
                        },
                        enabled = elapsedSeconds >= 5L,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إنهاء وحفظ النقاط")
                    }
                }
            }
        }
    }
}

@Composable
private fun OlympicPodiumCard(
    topThree: List<LeaderboardEntry>,
    sortMode: Int,
    currentUserId: String
) {
    val first = topThree.getOrNull(0)
    val second = topThree.getOrNull(1)
    val third = topThree.getOrNull(2)

    fun metricText(e: LeaderboardEntry): String = when (sortMode) {
        1 -> formatHoursMinutesAr(e.totalStudySeconds)
        2 -> "${e.currentStreakDays} 🔥"
        else -> "${e.focusPoints} pt"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("leaderboard_podium_card")
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp)
        ) {
            if (second != null) {
                PodiumSlot(
                    rank = 2,
                    entry = second,
                    metric = metricText(second),
                    medalColor = Color(0xFFCBD5E1),
                    pedestalHeight = 56,
                    isCurrentUser = second.userId == currentUserId,
                    modifier = Modifier.weight(1f)
                )
            }
            if (first != null) {
                PodiumSlot(
                    rank = 1,
                    entry = first,
                    metric = metricText(first),
                    medalColor = Color(0xFFFBBF24),
                    pedestalHeight = 76,
                    isCurrentUser = first.userId == currentUserId,
                    modifier = Modifier.weight(1.1f)
                )
            }
            if (third != null) {
                PodiumSlot(
                    rank = 3,
                    entry = third,
                    metric = metricText(third),
                    medalColor = Color(0xFFD97706),
                    pedestalHeight = 44,
                    isCurrentUser = third.userId == currentUserId,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PodiumSlot(
    rank: Int,
    entry: LeaderboardEntry,
    metric: String,
    medalColor: Color,
    pedestalHeight: Int,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Surface(
            color = medalColor,
            shape = CircleShape,
            modifier = Modifier.size(if (rank == 1) 46.dp else 38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "#$rank",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(if (rank == 1) 24.dp else 18.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isCurrentUser) "${entry.displayName} *" else entry.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = metric,
            style = MaterialTheme.typography.labelSmall,
            color = medalColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(medalColor.copy(alpha = 0.22f))
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = medalColor
            )
        }
    }
}

@Composable
private fun LeaderboardRankCard(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean
) {
    val badgeBg = when (rank) {
        1 -> Color(0xFFFBBF24)
        2 -> Color(0xFFCBD5E1)
        3 -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeBg)
            ) {
                if (rank <= 3) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = "المركز $rank", tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp))
                } else {
                    Text("#$rank", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isCurrentUser) "${entry.displayName} (أنت)" else entry.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    RoleBadge(roleKey = entry.role)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${entry.department} • ${formatHoursMinutesAr(entry.totalStudySeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.currentStreakDays > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(14.dp))
                            Text("${entry.currentStreakDays}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEA580C), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "${entry.focusPoints} نقطة",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentSessionsCard(sessions: List<StudySession>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("أحدث جلساتك المسجلة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            sessions.forEach { s ->
                val modeAr = when (s.mode) {
                    "pomodoro" -> "بومودورو"
                    "long_timer" -> "تايمر مفتوح"
                    else -> "وضع التركيز"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("$modeAr • ${s.courseTitle.ifBlank { "مذاكرة عامة" }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("المدة: ${formatHoursMinutesAr(s.durationSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("+${s.pointsEarned} نقطة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
