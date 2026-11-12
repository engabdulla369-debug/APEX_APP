package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.Assignment
import com.example.data.model.AssignmentProgress
import com.example.data.model.Course
import com.example.data.model.UserProfile
import com.example.ui.components.CountdownChip
import com.example.ui.components.EmptyStateCard

@Composable
fun AssignmentsScreen(
    profile: UserProfile,
    courses: List<Course>,
    assignments: List<Assignment>,
    progressMap: Map<String, AssignmentProgress>,
    onToggleCompleted: (assignmentId: String, completed: Boolean, existing: AssignmentProgress?) -> Unit,
    onSubmitSolution: (assignmentId: String, fileName: String, fileType: String, note: String, uri: String, existing: AssignmentProgress?) -> Unit,
    onAddAssignment: (course: Course, title: String, description: String, dueAtMillis: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterTab by remember { mutableIntStateOf(0) } // 0=النشطة, 1=المنجزة, 2=الكل
    var submittingAssignment by remember { mutableStateOf<Assignment?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(assignments, progressMap, filterTab) {
        when (filterTab) {
            0 -> assignments.filter { progressMap[it.id]?.completed != true }
            1 -> assignments.filter { progressMap[it.id]?.completed == true }
            else -> assignments
        }
    }

    val manageableCourses = remember(courses, profile) {
        if (profile.isBatchRep) courses
        else courses.filter { profile.canManageCourse(it.id) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "متابعة الواجبات والتكليفات",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "علّم على المهام المنجزة أو ارفع حلولك بصيغة PDF أو صورة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TabRow(selectedTabIndex = filterTab) {
                Tab(
                    selected = filterTab == 0,
                    onClick = { filterTab = 0 },
                    text = { Text("النشطة (${assignments.count { progressMap[it.id]?.completed != true }})") },
                    modifier = Modifier.testTag("assignments_tab_active")
                )
                Tab(
                    selected = filterTab == 1,
                    onClick = { filterTab = 1 },
                    text = { Text("المنجزة (${assignments.count { progressMap[it.id]?.completed == true }})") },
                    modifier = Modifier.testTag("assignments_tab_completed")
                )
                Tab(
                    selected = filterTab == 2,
                    onClick = { filterTab = 2 },
                    text = { Text("الكل (${assignments.size})") },
                    modifier = Modifier.testTag("assignments_tab_all")
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = if (filterTab == 0) "أنجزت جميع واجباتك!" else "لا توجد واجبات في هذه القائمة",
                            subtitle = if (filterTab == 0) "عمل رائع، يمكنك مراجعة المنجزة أو التدرب في قسم الاختبارات." else "ستظهر الواجبات المضافة هنا فور نشرها."
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { assignment ->
                        val progress = progressMap[assignment.id]
                        val isDone = progress?.completed == true
                        AssignmentCard(
                            assignment = assignment,
                            progress = progress,
                            isDone = isDone,
                            onCheckedChange = { checked ->
                                onToggleCompleted(assignment.id, checked, progress)
                            },
                            onOpenSubmit = { submittingAssignment = assignment }
                        )
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        if (manageableCourses.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_assignment_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة واجب")
            }
        }
    }

    submittingAssignment?.let { assignment ->
        SubmitSolutionDialog(
            assignment = assignment,
            onDismiss = { submittingAssignment = null },
            onSubmit = { fileName, fileType, note, uri ->
                onSubmitSolution(assignment.id, fileName, fileType, note, uri, progressMap[assignment.id])
                submittingAssignment = null
            }
        )
    }

    if (showAddDialog && manageableCourses.isNotEmpty()) {
        AddAssignmentDialog(
            courses = manageableCourses,
            onDismiss = { showAddDialog = false },
            onSave = { course, title, desc, dueMillis ->
                onAddAssignment(course, title, desc, dueMillis)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AssignmentCard(
    assignment: Assignment,
    progress: AssignmentProgress?,
    isDone: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSubmit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("assignment_card_${assignment.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag("assignment_checkbox_${assignment.id}")
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assignment.courseTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = assignment.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
                CountdownChip(dueAtMillis = assignment.dueAtMillis)
            }

            if (assignment.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = assignment.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!progress?.submissionFileName.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "تم تسليم الحل: ${progress?.submissionFileName} (${progress?.submissionFileType})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!progress?.submissionNote.isNullOrBlank()) {
                                Text(
                                    text = progress?.submissionNote ?: "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onOpenSubmit,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("submit_assignment_btn_${assignment.id}")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (progress?.submissionFileName.isNullOrBlank()) "تسليم الواجب" else "تحديث الحل")
                }
            }
        }
    }
}

@Composable
private fun SubmitSolutionDialog(
    assignment: Assignment,
    onDismiss: () -> Unit,
    onSubmit: (fileName: String, fileType: String, note: String, uri: String) -> Unit
) {
    val context = LocalContext.current
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileType by remember { mutableStateOf("PDF") }
    var selectedUri by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    fun extractMeta(uri: Uri, fallbackType: String) {
        selectedUri = uri.toString()
        selectedFileType = fallbackType
        var resolvedName = "solution_${System.currentTimeMillis().toString().takeLast(4)}.${fallbackType.lowercase()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) {
                        resolvedName = name
                        selectedFileType = name.substringAfterLast('.', fallbackType).uppercase().take(10)
                    }
                }
            }
        }
        selectedFileName = resolvedName
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) extractMeta(uri, "IMAGE")
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) extractMeta(uri, "PDF")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسليم حل الواجب", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = assignment.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("صورة")
                    }
                    OutlinedButton(
                        onClick = {
                            docPicker.launch(arrayOf("application/pdf", "application/*", "image/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ملف PDF")
                    }
                }
                if (selectedFileName.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(selectedFileName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة للمصحح (اختياري)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        selectedFileName.ifBlank { "حل_الواجب.pdf" },
                        selectedFileType,
                        note,
                        selectedUri.ifBlank { "local://submitted" }
                    )
                },
                enabled = selectedFileName.isNotBlank() || note.isNotBlank()
            ) {
                Text("إرسال التسليم")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddAssignmentDialog(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onSave: (Course, String, String, Long) -> Unit
) {
    var selectedCourse by remember { mutableStateOf(courses.first()) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var daysUntilDue by remember { mutableIntStateOf(3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة واجب جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    label = { Text("عنوان الواجب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف المطلوب") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("مهلة التسليم (بالأيام من الآن):", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 7).forEach { days ->
                        FilterChip(
                            selected = daysUntilDue == days,
                            onClick = { daysUntilDue = days },
                            label = { Text("$days يوم") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dueMillis = System.currentTimeMillis() + daysUntilDue * 24L * 3600L * 1000L
                    onSave(selectedCourse, title, description, dueMillis)
                },
                enabled = title.trim().length >= 2
            ) {
                Text("نشر الواجب")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
