package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.Assignment
import com.example.data.model.AssignmentProgress
import com.example.data.model.Course
import com.example.data.model.PersonalTask
import com.example.data.model.Quiz
import com.example.data.model.QuizAttempt
import com.example.data.model.QuizQuestion
import com.example.data.model.UserProfile
import com.example.ui.i18n.LocalAppStrings

@Composable
fun TasksHubScreen(
    profile: UserProfile,
    courses: List<Course>,
    assignments: List<Assignment>,
    progressMap: Map<String, AssignmentProgress>,
    quizzes: List<Quiz>,
    attempts: List<QuizAttempt>,
    personalTasks: List<PersonalTask> = emptyList(),
    initialTabIndex: Int = 0,
    onAddPersonalTask: (title: String, notes: String, category: String, priority: String, dueAtMillis: Long, courseTitle: String) -> Unit = { _, _, _, _, _, _ -> },
    onTogglePersonalTask: (PersonalTask, Boolean, Int) -> Unit = { _, _, _ -> },
    onDeletePersonalTask: (String) -> Unit = {},
    onToggleCompleted: (assignmentId: String, completed: Boolean, existing: AssignmentProgress?) -> Unit,
    onSubmitSolution: (assignmentId: String, fileName: String, fileType: String, note: String, fileUri: String, existing: AssignmentProgress?) -> Unit,
    onAddAssignment: (course: Course, title: String, desc: String, dueAtMillis: Long) -> Unit,
    onSaveQuizAttempt: (quiz: Quiz, score: Int, durationSec: Int, selectedAnswers: List<Int>) -> Unit,
    onAddQuiz: (course: Course, title: String, desc: String, durationMinutes: Int, questions: List<QuizQuestion>) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedSubTab by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 2)) } // 0=Daily To-Do, 1=Assignments, 2=Quizzes
    LaunchedEffect(initialTabIndex) {
        selectedSubTab = initialTabIndex.coerceIn(0, 2)
    }
    val activeTodoCount = remember(personalTasks) { personalTasks.count { !it.completed } }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSubTab) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("${strings.tabDailyTodo} ($activeTodoCount)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("tasks_tab_todo")
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("${strings.tabAssignments} (${assignments.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("tasks_tab_assignments")
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                text = { Text("${strings.tabQuizzes} (${quizzes.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("tasks_tab_quizzes")
            )
        }

        when (selectedSubTab) {
            0 -> DailyTodoSection(
                tasks = personalTasks,
                courses = courses,
                completedCountTotal = maxOf(profile.completedPersonalTasksCount, personalTasks.count { it.completed }),
                onAddTask = onAddPersonalTask,
                onToggleTask = onTogglePersonalTask,
                onDeleteTask = onDeletePersonalTask,
                modifier = Modifier.weight(1f)
            )
            1 -> AssignmentsScreen(
                profile = profile,
                courses = courses,
                assignments = assignments,
                progressMap = progressMap,
                onToggleCompleted = onToggleCompleted,
                onSubmitSolution = onSubmitSolution,
                onAddAssignment = onAddAssignment,
                modifier = Modifier.weight(1f)
            )
            else -> QuizzesHubScreen(
                profile = profile,
                courses = courses,
                quizzes = quizzes,
                attempts = attempts,
                onSaveAttempt = onSaveQuizAttempt,
                onAddQuiz = onAddQuiz,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
