package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Assignment
import com.example.data.model.AssignmentProgress
import com.example.data.model.Course
import com.example.data.model.Flashcard
import com.example.data.model.PersonalTask
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.utils.AnalyticsCalculator
import com.example.utils.ChartBucket
import com.example.utils.SpacedRepetitionEngine
import java.util.Locale
import kotlin.math.max

@Composable
fun FlashcardsSection(
    flashcards: List<Flashcard>,
    courses: List<Course>,
    onCreateCard: (deckName: String, front: String, back: String, courseId: String, courseTitle: String) -> Unit,
    onReviewCard: (Flashcard, String) -> Unit,
    onDeleteCard: (String) -> Unit,
    onSeedStarterDeck: () -> Unit
) {
    val strings = LocalAppStrings.current
    var filterMode by remember { mutableIntStateOf(0) } // 0=Due, 1=Hard Only, 2=All
    var selectedDeck by remember { mutableStateOf("الكل") }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val decks = remember(flashcards) {
        listOf("الكل") + flashcards.map { it.deckName }.filter { it.isNotBlank() }.distinct()
    }
    val now = System.currentTimeMillis()
    val filteredCards = remember(flashcards, filterMode, selectedDeck, now) {
        flashcards.filter { card ->
            val matchesDeck = selectedDeck == "الكل" || card.deckName == selectedDeck
            val matchesMode = when (filterMode) {
                0 -> SpacedRepetitionEngine.isDueOrHard(card, now)
                1 -> card.difficultyRating == SpacedRepetitionEngine.RATING_HARD
                else -> true
            }
            matchesDeck && matchesMode
        }
    }

    val safeIndex = if (filteredCards.isEmpty()) 0 else currentIndex.coerceIn(0, filteredCards.lastIndex)
    val activeCard = filteredCards.getOrNull(safeIndex)
    val hardCount = remember(flashcards) { flashcards.count { it.difficultyRating == SpacedRepetitionEngine.RATING_HARD } }
    val dueCount = remember(flashcards, now) { flashcards.count { SpacedRepetitionEngine.isDueOrHard(it, now) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.flashcardsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(strings.flashcardsSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("add_flashcard_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.newFlashcard)
                    }
                }

                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filterMode == 0,
                            onClick = { filterMode = 0; currentIndex = 0; isFlipped = false },
                            label = { Text("${strings.filterDue} ($dueCount)") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == 1,
                            onClick = { filterMode = 1; currentIndex = 0; isFlipped = false },
                            label = { Text("${strings.filterHardOnly} ($hardCount)") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == 2,
                            onClick = { filterMode = 2; currentIndex = 0; isFlipped = false },
                            label = { Text("${strings.filterAll} (${flashcards.size})") }
                        )
                    }
                }

                if (decks.size > 2) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(decks) { deck ->
                            FilterChip(
                                selected = selectedDeck == deck,
                                onClick = { selectedDeck = deck; currentIndex = 0; isFlipped = false },
                                label = { Text(deck) }
                            )
                        }
                    }
                }
            }
        }

        if (flashcards.isEmpty()) {
            EmptyStateCard(
                title = strings.flashcardsTitle,
                subtitle = strings.noCardsYet,
                action = {
                    Button(
                        onClick = onSeedStarterDeck,
                        modifier = Modifier.testTag("seed_flashcards_button")
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(strings.loadStarterDeck)
                    }
                }
            )
        } else if (activeCard == null) {
            EmptyStateCard(
                title = strings.noCardsDue,
                subtitle = "${flashcards.size} cards total",
                action = {
                    OutlinedButton(onClick = { filterMode = 2; currentIndex = 0; isFlipped = false }) {
                        Text(strings.filterAll)
                    }
                }
            )
        } else {
            // 3D Flip Flashcard
            val density = LocalDensity.current.density
            val rotation by animateFloatAsState(
                targetValue = if (isFlipped) 180f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "flashcard_flip"
            )
            val isBackVisible = rotation > 90f

            val diffColor = when (activeCard.difficultyRating) {
                SpacedRepetitionEngine.RATING_HARD -> Color(0xFFDC2626)
                SpacedRepetitionEngine.RATING_MEDIUM -> Color(0xFFD97706)
                SpacedRepetitionEngine.RATING_EASY -> Color(0xFF059669)
                else -> MaterialTheme.colorScheme.primary
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isBackVisible) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 14f * density
                    }
                    .clickable { isFlipped = !isFlipped }
                    .testTag("flashcard_flip_surface")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isBackVisible) rotationY = 180f
                        }
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    ) {
                        Surface(
                            color = diffColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${activeCard.deckName} • ${safeIndex + 1}/${filteredCards.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = diffColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(onClick = { onDeleteCard(activeCard.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = strings.deleteLabel, modifier = Modifier.size(18.dp))
                        }
                    }

                    Text(
                        text = if (isBackVisible) activeCard.backText else activeCard.frontText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        Icon(Icons.Default.Flip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isBackVisible) "Reviews: ${activeCard.reviewCount} • Interval: ${activeCard.intervalDays}d" else strings.tapToFlip,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isFlipped) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            onReviewCard(activeCard, SpacedRepetitionEngine.RATING_HARD)
                            isFlipped = false
                            currentIndex = (safeIndex + 1) % max(1, filteredCards.size)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("flashcard_rate_hard")
                    ) {
                        Text(strings.rateHard, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = {
                            onReviewCard(activeCard, SpacedRepetitionEngine.RATING_MEDIUM)
                            isFlipped = false
                            currentIndex = (safeIndex + 1) % max(1, filteredCards.size)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("flashcard_rate_medium")
                    ) {
                        Text(strings.rateMedium, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = {
                            onReviewCard(activeCard, SpacedRepetitionEngine.RATING_EASY)
                            isFlipped = false
                            currentIndex = (safeIndex + 1) % max(1, filteredCards.size)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("flashcard_rate_easy")
                    ) {
                        Text(strings.rateEasy, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFlashcardDialog(
            courses = courses,
            onDismiss = { showCreateDialog = false },
            onSave = { deck, front, back, course ->
                onCreateCard(deck, front, back, course?.id ?: "", course?.title ?: "")
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreateFlashcardDialog(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onSave: (deckName: String, front: String, back: String, course: Course?) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedCourse by remember { mutableStateOf(courses.firstOrNull()) }
    var deckName by remember { mutableStateOf(courses.firstOrNull()?.title ?: "مراجعة عامة") }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.newFlashcard, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (courses.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(courses) { c ->
                            FilterChip(
                                selected = selectedCourse?.id == c.id,
                                onClick = {
                                    selectedCourse = c
                                    deckName = c.title
                                },
                                label = { Text(c.title) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = deckName,
                    onValueChange = { deckName = it },
                    label = { Text(strings.deckNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text(strings.frontQuestionLabel) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("flashcard_front_input")
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text(strings.backAnswerLabel) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("flashcard_back_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(deckName, front, back, selectedCourse) },
                enabled = front.isNotBlank() && back.isNotBlank(),
                modifier = Modifier.testTag("flashcard_save_button")
            ) {
                Text(strings.createCard)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun StudentAnalyticsDashboardSection(
    profile: UserProfile,
    courses: List<Course>,
    sessions: List<StudySession>,
    assignments: List<Assignment>,
    assignmentProgress: Map<String, AssignmentProgress>,
    personalTasks: List<PersonalTask>,
    flashcards: List<Flashcard>
) {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val locale = remember(language) {
        when (language) {
            AppLanguage.AR -> Locale("ar")
            AppLanguage.EN -> Locale.ENGLISH
            AppLanguage.FR -> Locale.FRENCH
            AppLanguage.ES -> Locale("es")
        }
    }
    var chartMode by remember { mutableIntStateOf(0) } // 0=Weekly, 1=Monthly

    val computedStreak = remember(sessions) { AnalyticsCalculator.computeStreaks(sessions) }
    val activeStreak = maxOf(profile.currentStreakDays, computedStreak.currentStreakDays)
    val bestStreak = maxOf(profile.longestStreakDays, computedStreak.longestStreakDays, activeStreak)

    val completedAssignmentsCount = remember(assignments, assignmentProgress) {
        assignments.count { assignmentProgress[it.id]?.completed == true }
    }
    val completedPersonalTasksCount = remember(personalTasks, profile.completedPersonalTasksCount) {
        maxOf(personalTasks.count { it.completed }, profile.completedPersonalTasksCount)
    }
    val totalDoneTasks = completedAssignmentsCount + completedPersonalTasksCount
    val totalTasksDenom = (assignments.size + personalTasks.size).coerceAtLeast(1)
    val completionPercent = ((totalDoneTasks * 100f) / totalTasksDenom).toInt().coerceIn(0, 100)
    val masteredCardsCount = remember(flashcards) {
        flashcards.count { it.difficultyRating == SpacedRepetitionEngine.RATING_EASY || it.repetitions >= 2 }
    }

    val chartBuckets = remember(sessions, chartMode, locale) {
        if (chartMode == 0) AnalyticsCalculator.weeklyBuckets(sessions, locale)
        else AnalyticsCalculator.monthlyBuckets(sessions, weekPrefix = if (language == AppLanguage.AR) "أ" else "W")
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Duolingo-inspired Study Streak Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().testTag("analytics_streak_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFF97316).copy(alpha = 0.22f),
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFFB923C),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.streakTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFFED7AA)
                            )
                            Text(
                                text = "$activeStreak ${strings.daysUnit}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(strings.bestStreakLabel, style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                            Text("$bestStreak ${strings.daysUnit}", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val weeklyBuckets = remember(sessions, locale) { AnalyticsCalculator.weeklyBuckets(sessions, locale) }
                    weeklyBuckets.forEachIndexed { idx, bucket ->
                        val active = computedStreak.lastSevenDaysActive.getOrElse(idx) { false } || bucket.minutes > 0f
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) Color(0xFFF97316) else Color.White.copy(alpha = 0.1f)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (active) Icons.Default.LocalFireDepartment else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (active) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = bucket.label.take(3),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }

        // 2x2 KPI Cards
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AnalyticsKpiCard(
                title = strings.totalStudyTimeLabel,
                value = formatHoursMinutesAr(profile.totalStudySeconds),
                icon = Icons.Default.Timer,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            AnalyticsKpiCard(
                title = strings.completedTasksLabel,
                value = "$totalDoneTasks",
                icon = Icons.Default.TaskAlt,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AnalyticsKpiCard(
                title = strings.courseCompletionLabel,
                value = "$completionPercent%",
                icon = Icons.Default.School,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            AnalyticsKpiCard(
                title = strings.masteredCardsLabel,
                value = "$masteredCardsCount / ${flashcards.size}",
                icon = Icons.Default.AutoAwesome,
                accent = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f)
            )
        }

        // Productivity Chart Card (Weekly & Monthly)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(strings.productivityChartTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chartMode == 0,
                        onClick = { chartMode = 0 },
                        label = { Text(strings.weeklyChartTab) }
                    )
                    FilterChip(
                        selected = chartMode == 1,
                        onClick = { chartMode = 1 },
                        label = { Text(strings.monthlyChartTab) }
                    )
                }
                Spacer(Modifier.height(14.dp))
                ProductivityBarAndLineChart(
                    buckets = chartBuckets,
                    minutesLabel = strings.minutesUnit
                )
            }
        }

        // Per-Course Progress Card
        if (courses.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.courseCompletionLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    courses.take(6).forEach { course ->
                        val courseAssigns = assignments.filter { it.courseId == course.id }
                        val doneAssigns = courseAssigns.count { assignmentProgress[it.id]?.completed == true }
                        val courseStudySec = sessions.filter { it.courseTitle == course.title }.sumOf { it.durationSeconds }
                        val ratio = if (courseAssigns.isNotEmpty()) {
                            (doneAssigns.toFloat() / courseAssigns.size.toFloat()).coerceIn(0f, 1f)
                        } else if (courseStudySec > 0L) {
                            (courseStudySec / 3600f).coerceIn(0.15f, 1f)
                        } else 0.1f

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(course.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${(ratio * 100).toInt()}% • ${formatHoursMinutesAr(courseStudySec)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsKpiCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProductivityBarAndLineChart(
    buckets: List<ChartBucket>,
    minutesLabel: String
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val maxVal = max(10f, buckets.maxOfOrNull { it.minutes } ?: 10f)

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .padding(horizontal = 4.dp)
        ) {
            val count = buckets.size.coerceAtLeast(1)
            val slotW = size.width / count
            val barW = (slotW * 0.48f).coerceAtMost(38.dp.toPx())
            val chartH = size.height - 16.dp.toPx()

            // Subtle horizontal grid lines
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                val y = chartH * (1f - frac) + 8.dp.toPx()
                drawLine(
                    color = track,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val linePath = Path()
            buckets.forEachIndexed { idx, bucket ->
                val ratio = (bucket.minutes / maxVal).coerceIn(0f, 1f)
                val barH = max(4.dp.toPx(), ratio * chartH)
                val cx = slotW * idx + slotW / 2f
                val left = cx - barW / 2f
                val top = chartH - barH + 8.dp.toPx()

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (bucket.isCurrent) listOf(secondary, primary)
                        else listOf(primary.copy(alpha = 0.85f), primary.copy(alpha = 0.35f))
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                val point = Offset(cx, top)
                if (idx == 0) linePath.moveTo(point.x, point.y) else linePath.lineTo(point.x, point.y)
            }

            drawPath(
                path = linePath,
                color = secondary,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            buckets.forEachIndexed { idx, bucket ->
                val ratio = (bucket.minutes / maxVal).coerceIn(0f, 1f)
                val barH = max(4.dp.toPx(), ratio * chartH)
                val cx = slotW * idx + slotW / 2f
                val top = chartH - barH + 8.dp.toPx()
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(cx, top))
                drawCircle(color = secondary, radius = 2.5.dp.toPx(), center = Offset(cx, top))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            buckets.forEach { b ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${b.minutes.toInt()}$minutesLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Text(
                        text = b.label.take(4),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
