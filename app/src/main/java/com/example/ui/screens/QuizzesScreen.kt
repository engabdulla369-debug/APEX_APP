package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Course
import com.example.data.model.Quiz
import com.example.data.model.QuizAttempt
import com.example.data.model.QuizQuestion
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import kotlinx.coroutines.delay

@Composable
fun QuizzesHubScreen(
    profile: UserProfile,
    courses: List<Course>,
    quizzes: List<Quiz>,
    attempts: List<QuizAttempt>,
    onSaveAttempt: (quiz: Quiz, score: Int, secondsUsed: Int, selectedAnswers: List<Int>) -> Unit,
    onAddQuiz: (course: Course, title: String, desc: String, durationMinutes: Int, questions: List<QuizQuestion>) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeQuiz by remember { mutableStateOf<Quiz?>(null) }
    var reviewResult by remember { mutableStateOf<Triple<Quiz, List<Int>, Int>?>(null) }
    var selectedCourseFilter by remember { mutableStateOf<String?>(null) }
    var showCreateQuizDialog by remember { mutableStateOf(false) }

    val manageableCourses = remember(courses, profile) {
        if (profile.isBatchRep) courses else courses.filter { profile.canManageCourse(it.id) }
    }

    val currentReview = reviewResult
    val currentQuiz = activeQuiz

    if (currentQuiz != null) {
        QuizPlayerScreen(
            quiz = currentQuiz,
            onExit = { activeQuiz = null },
            onFinished = { answers, secondsUsed ->
                val score = currentQuiz.questions.indices.count { i ->
                    answers.getOrNull(i) == currentQuiz.questions[i].correctIndex
                }
                onSaveAttempt(currentQuiz, score, secondsUsed, answers)
                activeQuiz = null
                reviewResult = Triple(currentQuiz, answers, secondsUsed)
            }
        )
        return
    }

    if (currentReview != null) {
        QuizResultReviewScreen(
            quiz = currentReview.first,
            selectedAnswers = currentReview.second,
            secondsUsed = currentReview.third,
            onBackToHub = { reviewResult = null },
            onRetake = {
                val q = currentReview.first
                reviewResult = null
                activeQuiz = q
            }
        )
        return
    }

    val filteredQuizzes = remember(quizzes, selectedCourseFilter) {
        if (selectedCourseFilter == null) quizzes
        else quizzes.filter { it.courseId == selectedCourseFilter }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "الاختبارات التجريبية والكويزات",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "تدرّب بمؤقت زمني حقيقي واحصل على التصحيح الفوري مع نموذج الإجابة والشرح",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCourseFilter == null,
                            onClick = { selectedCourseFilter = null },
                            label = { Text("كل المواد") }
                        )
                    }
                    items(courses, key = { it.id }) { course ->
                        FilterChip(
                            selected = selectedCourseFilter == course.id,
                            onClick = { selectedCourseFilter = course.id },
                            label = { Text(course.title) }
                        )
                    }
                }
            }

            if (filteredQuizzes.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "لا توجد اختبارات متاحة حالياً",
                        subtitle = "سيقوم ممثل الدفعة أو معيد المادة بنشر اختبارات تجريبية قريباً."
                    )
                }
            } else {
                items(filteredQuizzes, key = { it.id }) { quiz ->
                    val bestAttempt = attempts.filter { it.quizId == quiz.id }.maxByOrNull { it.score }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quiz_card_${quiz.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = quiz.courseTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (bestAttempt != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.EmojiEvents,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "أفضل درجة: ${bestAttempt.score}/${bestAttempt.totalQuestions}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = quiz.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (quiz.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = quiz.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                        Text("${quiz.questions.size} أسئلة", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                        Text("${quiz.durationMinutes} دقائق", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Button(
                                    onClick = { activeQuiz = quiz },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("start_quiz_${quiz.id}")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ابدأ الآن")
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }

        if (manageableCourses.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showCreateQuizDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_quiz_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إنشاء اختبار تجريبي")
            }
        }
    }

    if (showCreateQuizDialog && manageableCourses.isNotEmpty()) {
        CreateQuizDialog(
            courses = manageableCourses,
            onDismiss = { showCreateQuizDialog = false },
            onSave = { course, title, desc, minutes, questions ->
                onAddQuiz(course, title, desc, minutes, questions)
                showCreateQuizDialog = false
            }
        )
    }
}

@Composable
private fun QuizPlayerScreen(
    quiz: Quiz,
    onExit: () -> Unit,
    onFinished: (selectedAnswers: List<Int>, secondsUsed: Int) -> Unit
) {
    BackHandler(onBack = onExit)

    val totalSeconds = (quiz.durationMinutes * 60).coerceAtLeast(60)
    var remainingSeconds by remember(quiz.id) { mutableIntStateOf(totalSeconds) }
    var currentIndex by remember(quiz.id) { mutableIntStateOf(0) }
    val selectedAnswers = remember(quiz.id) {
        mutableStateListOf<Int>().apply { repeat(quiz.questions.size) { add(-1) } }
    }

    LaunchedEffect(quiz.id) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
        onFinished(selectedAnswers.toList(), totalSeconds)
    }

    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timeString = String.format("%02d:%02d", mins, secs)
    val question = quiz.questions[currentIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "خروج من الاختبار")
            }
            Text(
                text = quiz.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (remainingSeconds <= 30) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("quiz_countdown_timer")
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / quiz.questions.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "السؤال ${currentIndex + 1} من ${quiz.questions.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = question.questionText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(18.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            question.options.forEachIndexed { optIdx, optionText ->
                val isSelected = selectedAnswers[currentIndex] == optIdx
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedAnswers[currentIndex] = optIdx }
                        .testTag("quiz_option_$optIdx")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedAnswers[currentIndex] = optIdx }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (currentIndex > 0) {
                OutlinedButton(
                    onClick = { currentIndex -= 1 },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("السابق")
                }
            }
            Button(
                onClick = {
                    if (currentIndex < quiz.questions.lastIndex) {
                        currentIndex += 1
                    } else {
                        onFinished(selectedAnswers.toList(), totalSeconds - remainingSeconds)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("quiz_next_or_submit_button")
            ) {
                Text(if (currentIndex < quiz.questions.lastIndex) "التالي" else "تسليم وعرض النتيجة")
            }
        }
    }
}

@Composable
private fun QuizResultReviewScreen(
    quiz: Quiz,
    selectedAnswers: List<Int>,
    secondsUsed: Int,
    onBackToHub: () -> Unit,
    onRetake: () -> Unit
) {
    BackHandler(onBack = onBackToHub)

    val score = quiz.questions.indices.count { i -> selectedAnswers.getOrNull(i) == quiz.questions[i].correctIndex }
    val percent = if (quiz.questions.isNotEmpty()) (score * 100) / quiz.questions.size else 0

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "نتيجتك: $score من ${quiz.questions.size} ($percent%)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("quiz_result_score_text")
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "الوقت المستغرق: ${secondsUsed / 60} دقيقة و ${secondsUsed % 60} ثانية",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onRetake) { Text("إعادة المحاولة") }
                        Button(onClick = onBackToHub) { Text("العودة للاختبارات") }
                    }
                }
            }
        }

        item {
            Text(
                text = "نموذج الإجابة والشرح",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        itemsIndexed(quiz.questions) { index, q ->
            val chosen = selectedAnswers.getOrNull(index) ?: -1
            val isCorrect = chosen == q.correctIndex

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isCorrect) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${index + 1}. ${q.questionText}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!isCorrect) {
                        Text(
                            text = "إجابتك: ${q.options.getOrNull(chosen) ?: "لم يتم الاختيار"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = "الإجابة الصحيحة: ${q.options.getOrNull(q.correctIndex) ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    if (q.explanation.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "الشرح: ${q.explanation}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateQuizDialog(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onSave: (Course, String, String, Int, List<QuizQuestion>) -> Unit
) {
    var selectedCourse by remember { mutableStateOf(courses.first()) }
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(5) }
    var qText by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("") }
    var opt2 by remember { mutableStateOf("") }
    var opt3 by remember { mutableStateOf("") }
    var correctIdx by remember { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf("") }
    val addedQuestions = remember { mutableStateListOf<QuizQuestion>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء اختبار تجريبي", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(courses) { c ->
                        FilterChip(
                            selected = selectedCourse.id == c.id,
                            onClick = { selectedCourse = c },
                            label = { Text(c.title) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الاختبار") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 20).forEach { m ->
                        FilterChip(selected = duration == m, onClick = { duration = m }, label = { Text("$m د") })
                    }
                }
                Text("إضافة سؤال (${addedQuestions.size} مضاف):", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = qText,
                    onValueChange = { qText = it },
                    label = { Text("نص السؤال") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = opt1, onValueChange = { opt1 = it }, label = { Text("الخيار 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = opt2, onValueChange = { opt2 = it }, label = { Text("الخيار 2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = opt3, onValueChange = { opt3 = it }, label = { Text("الخيار 3 (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "الأول صح", 1 to "الثاني صح", 2 to "الثالث صح").forEach { (idx, lbl) ->
                        FilterChip(selected = correctIdx == idx, onClick = { correctIdx = idx }, label = { Text(lbl) })
                    }
                }
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("شرح الإجابة الصحيحة") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val opts = listOf(opt1, opt2, opt3).map { it.trim() }.filter { it.isNotEmpty() }
                        if (qText.isNotBlank() && opts.size >= 2) {
                            addedQuestions.add(
                                QuizQuestion(
                                    questionText = qText.trim(),
                                    questionType = if (opts.size == 2) "true_false" else "mcq",
                                    options = opts,
                                    correctIndex = correctIdx.coerceIn(0, opts.lastIndex),
                                    explanation = explanation.trim()
                                )
                            )
                            qText = ""; opt1 = ""; opt2 = ""; opt3 = ""; explanation = ""; correctIdx = 0
                        }
                    },
                    enabled = qText.isNotBlank() && opt1.isNotBlank() && opt2.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ إضافة هذا السؤال للقائمة")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalQuestions = addedQuestions.toList()
                    onSave(selectedCourse, title, "اختبار تدريبي تفاعلي", duration, finalQuestions)
                },
                enabled = title.trim().length >= 2 && addedQuestions.isNotEmpty()
            ) {
                Text("نشر الاختبار")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
