package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Course
import com.example.data.model.PersonalTask
import com.example.ui.components.CountdownChip
import com.example.ui.components.EmptyStateCard
import com.example.ui.i18n.LocalAppStrings
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DailyTodoSection(
    tasks: List<PersonalTask>,
    courses: List<Course>,
    completedCountTotal: Int,
    onAddTask: (title: String, notes: String, category: String, priority: String, dueAtMillis: Long, courseTitle: String) -> Unit,
    onToggleTask: (PersonalTask, Boolean, Int) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var filterMode by remember { mutableIntStateOf(0) } // 0=Active, 1=High Priority, 2=Completed, 3=All
    var showAddDialog by remember { mutableStateOf(false) }
    var celebrateTrigger by remember { mutableIntStateOf(0) }

    val filteredTasks = remember(tasks, filterMode) {
        when (filterMode) {
            0 -> tasks.filter { !it.completed }
            1 -> tasks.filter { !it.completed && it.priority == "high" }
            2 -> tasks.filter { it.completed }
            else -> tasks
        }
    }

    val activeDone = tasks.count { it.completed }
    val completionRatio = if (tasks.isEmpty()) 0f else activeDone.toFloat() / tasks.size.toFloat()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
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
                            Text(strings.dailyTodoTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "$activeDone / ${tasks.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { completionRatio },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = filterMode == 0, onClick = { filterMode = 0 }, label = { Text(strings.filterPending) }) }
                            item { FilterChip(selected = filterMode == 1, onClick = { filterMode = 1 }, label = { Text(strings.priorityHigh) }) }
                            item { FilterChip(selected = filterMode == 2, onClick = { filterMode = 2 }, label = { Text(strings.filterCompleted) }) }
                            item { FilterChip(selected = filterMode == 3, onClick = { filterMode = 3 }, label = { Text(strings.filterAll) }) }
                        }
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = strings.dailyTodoTitle,
                        subtitle = if (tasks.isEmpty()) "نظّم مهامك اليومية بأولويات ومواعيد تسليم واضحة." else "لا توجد مهام ضمن هذا التصنيف.",
                        action = {
                            Button(onClick = { showAddDialog = true }) {
                                Text(strings.addTodoTask)
                            }
                        }
                    )
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    PersonalTodoCard(
                        task = task,
                        onToggle = { nowDone ->
                            if (nowDone) celebrateTrigger++
                            onToggleTask(task, nowDone, completedCountTotal)
                        },
                        onDelete = { onDeleteTask(task.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(76.dp)) }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_personal_task_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = strings.addTodoTask)
        }

        if (celebrateTrigger > 0) {
            CelebrationBurstBanner(
                triggerKey = celebrateTrigger,
                message = strings.taskDoneToast,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            )
        }
    }

    if (showAddDialog) {
        AddPersonalTaskDialog(
            courses = courses,
            onDismiss = { showAddDialog = false },
            onSave = { title, notes, category, priority, dueAt, courseTitle ->
                onAddTask(title, notes, category, priority, dueAt, courseTitle)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun PersonalTodoCard(
    task: PersonalTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    val priorityColor = when (task.priority) {
        "high" -> Color(0xFFDC2626)
        "medium" -> Color(0xFFD97706)
        else -> Color(0xFF0284C7)
    }
    val priorityLabel = when (task.priority) {
        "high" -> strings.priorityHigh
        "medium" -> strings.priorityMedium
        else -> strings.priorityLow
    }

    val checkScale by animateFloatAsState(
        targetValue = if (task.completed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "todo_check_scale"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
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
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (task.completed) Color(0xFF059669) else priorityColor)
            )
            Spacer(Modifier.width(12.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .scale(checkScale)
                    .clip(CircleShape)
                    .background(
                        if (task.completed) Color(0xFF059669)
                        else priorityColor.copy(alpha = 0.12f)
                    )
                    .clickable { onToggle(!task.completed) }
                    .testTag("todo_toggle_${task.id}")
            ) {
                if (task.completed) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.alpha(if (task.completed) 0.65f else 1f)
                )
                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = priorityColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = priorityColor, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(priorityLabel, style = MaterialTheme.typography.labelSmall, color = priorityColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (!task.completed && task.dueAtMillis > 0L) {
                        CountdownChip(dueAtMillis = task.dueAtMillis)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = strings.deleteLabel, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CelebrationBurstBanner(
    triggerKey: Int,
    message: String,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    var visible by remember(triggerKey) { mutableStateOf(true) }

    LaunchedEffect(triggerKey) {
        visible = true
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing))
        visible = false
    }

    AnimatedVisibility(visible = visible, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(180.dp, 64.dp)) {
                val p = progress.value
                val colors = listOf(Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF3B82F6), Color(0xFFEC4899), Color(0xFF8B5CF6))
                val cx = size.width / 2f
                val cy = size.height / 2f
                for (i in 0 until 14) {
                    val angle = (i * (360f / 14f)) * (Math.PI / 180.0)
                    val radius = (12f + p * 72f)
                    val x = cx + (cos(angle) * radius).toFloat()
                    val y = cy + (sin(angle) * radius * 0.45f).toFloat()
                    drawCircle(
                        color = colors[i % colors.size].copy(alpha = (1f - p).coerceIn(0f, 1f)),
                        radius = (5.dp.toPx() * (1f - p * 0.6f)).coerceAtLeast(1f),
                        center = Offset(x, y)
                    )
                }
            }
            Surface(
                color = Color(0xFF065F46),
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Celebration, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(message, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AddPersonalTaskDialog(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, category: String, priority: String, dueAtMillis: Long, courseTitle: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("مذاكرة") }
    var priority by remember { mutableStateOf("high") }
    var daysUntilDue by remember { mutableIntStateOf(1) }
    var selectedCourseTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addTodoTask, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.taskTitleHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("personal_task_title_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(strings.taskNotesHint) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(strings.taskCategoryHint, style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("مذاكرة", "مشروع", "مراجعة", "شخصي")) { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = priority == "high", onClick = { priority = "high" }, label = { Text(strings.priorityHigh) })
                    FilterChip(selected = priority == "medium", onClick = { priority = "medium" }, label = { Text(strings.priorityMedium) })
                    FilterChip(selected = priority == "low", onClick = { priority = "low" }, label = { Text(strings.priorityLow) })
                }
                Text(strings.deadlineLabel, style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(0 to "اليوم (+6س)", 1 to "غداً", 3 to "3 أيام", 7 to "أسبوع")) { (d, lbl) ->
                        FilterChip(selected = daysUntilDue == d, onClick = { daysUntilDue = d }, label = { Text(lbl) })
                    }
                }
                if (courses.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(courses.take(5)) { c ->
                            FilterChip(
                                selected = selectedCourseTitle == c.title,
                                onClick = { selectedCourseTitle = if (selectedCourseTitle == c.title) "" else c.title },
                                label = { Text(c.title) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val due = System.currentTimeMillis() + if (daysUntilDue == 0) 6L * 3600L * 1000L else daysUntilDue * 24L * 3600L * 1000L
                    onSave(title, notes, category, priority, due, selectedCourseTitle)
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_personal_task_button")
            ) {
                Text(strings.saveTask)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
