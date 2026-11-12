package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Course
import com.example.data.model.CourseMaterial
import com.example.data.model.GlobalReference
import com.example.data.model.MaterialCategory
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InAppDocumentViewerDialog
import com.example.ui.components.InAppVideoPlayerDialog
import com.example.ui.components.openOrDownloadMaterial
import com.example.ui.components.parseAccentColor
import com.example.ui.i18n.LocalAppStrings

@Composable
fun CoursesHubScreen(
    profile: UserProfile,
    courses: List<Course>,
    globalReferences: List<GlobalReference> = emptyList(),
    initialTabIndex: Int = 0,
    onSelectCourse: (String) -> Unit,
    onToggleEnroll: (String) -> Unit,
    onAddCourse: (title: String, code: String, year: Int, term: Int, instructor: String, dept: String, accent: String) -> Unit,
    onAddGlobalReference: (title: String, provider: String, subject: String, description: String, url: String, languageCode: String) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteGlobalReference: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var hubTab by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 1)) }
    LaunchedEffect(initialTabIndex) {
        hubTab = initialTabIndex.coerceIn(0, 1)
    }
    var selectedYear by remember(profile.academicYear) { mutableIntStateOf(profile.academicYear.coerceIn(1, 4)) }
    var selectedTerm by remember(profile.term) { mutableIntStateOf(profile.term.coerceIn(1, 2)) }
    var showAddCourseDialog by remember { mutableStateOf(false) }

    val filteredCourses = remember(courses, selectedYear, selectedTerm) {
        courses.filter { it.academicYear == selectedYear && it.term == selectedTerm }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = hubTab) {
            Tab(
                selected = hubTab == 0,
                onClick = { hubTab = 0 },
                text = { Text("${strings.tabBatchCourses} (${courses.size})") },
                modifier = Modifier.testTag("courses_tab_batch")
            )
            Tab(
                selected = hubTab == 1,
                onClick = { hubTab = 1 },
                text = { Text("${strings.tabGlobalRefs} (${globalReferences.size})") },
                modifier = Modifier.testTag("courses_tab_global_refs")
            )
        }

        if (hubTab == 1) {
            GlobalReferencesSection(
                profile = profile,
                references = globalReferences,
                onAddReference = onAddGlobalReference,
                onDeleteReference = onDeleteGlobalReference,
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = strings.coursesHubTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "اختر السنة الدراسية والفصل الدراسي للوصول للمحاضرات والملخصات والكتب",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                Spacer(Modifier.height(12.dp))

                Text("السنة الدراسية", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(1 to "الفرقة الأولى", 2 to "الفرقة الثانية", 3 to "الفرقة الثالثة", 4 to "الفرقة الرابعة")) { (yr, label) ->
                        FilterChip(
                            selected = selectedYear == yr,
                            onClick = { selectedYear = yr },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTerm == 1,
                        onClick = { selectedTerm = 1 },
                        label = { Text("الفصل الدراسي الأول") }
                    )
                    FilterChip(
                        selected = selectedTerm == 2,
                        onClick = { selectedTerm = 2 },
                        label = { Text("الفصل الدراسي الثاني") }
                    )
                }
            }

            if (filteredCourses.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "لا توجد مواد في هذا الفصل",
                        subtitle = "جرّب اختيار سنة أو ترم آخر، أو أضف مادة جديدة إذا كنت ممثل الدفعة."
                    )
                }
            } else {
                items(filteredCourses, key = { it.id }) { course ->
                    val accent = parseAccentColor(course.accentHex, MaterialTheme.colorScheme.primary)
                    val isEnrolled = profile.enrolledCourseIds.contains(course.id)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCourse(course.id) }
                            .testTag("course_card_${course.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(accent.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = accent.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = course.code,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = course.department,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = course.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = course.instructorName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onToggleEnroll(course.id) }) {
                                Icon(
                                    imageVector = if (isEnrolled) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isEnrolled) "إزالة من موادي" else "إضافة لموادي",
                                    tint = if (isEnrolled) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }

                if (profile.isBatchRep) {
                    FloatingActionButton(
                        onClick = { showAddCourseDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .testTag("add_course_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة مادة")
                    }
                }
            }
        }
    }

    if (showAddCourseDialog) {
        AddCourseDialog(
            defaultYear = selectedYear,
            defaultTerm = selectedTerm,
            defaultDept = profile.department,
            onDismiss = { showAddCourseDialog = false },
            onSave = { title, code, year, term, instructor, dept, accent ->
                onAddCourse(title, code, year, term, instructor, dept, accent)
                showAddCourseDialog = false
            }
        )
    }
}

@Composable
fun CourseDetailScreen(
    course: Course,
    profile: UserProfile,
    materials: List<CourseMaterial>,
    onBack: () -> Unit,
    onAddMaterial: (category: String, title: String, desc: String, week: Int, format: String, size: String, url: String) -> Unit,
    onDeleteMaterial: (String) -> Unit,
    onSummarizeMaterial: (CourseMaterial) -> Unit,
    onSummarizeCustomCourse: (courseTitle: String, summaryPrompt: String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddMaterialDialog by remember { mutableStateOf(false) }
    var previewVideoMaterial by remember { mutableStateOf<CourseMaterial?>(null) }
    var previewDocMaterial by remember { mutableStateOf<CourseMaterial?>(null) }

    val categories = MaterialCategory.entries
    val activeCategory = categories[selectedTab]
    val filteredMaterials = remember(materials, activeCategory) {
        materials.filter { it.category == activeCategory.key }
    }
    val canManage = profile.canManageCourse(course.id)
    val accent = parseAccentColor(course.accentHex, MaterialTheme.colorScheme.primary)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("course_detail_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${course.code} • ${course.instructorName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = course.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                val syllabusText = buildString {
                                    appendLine("ملخص شامل لمقرر ${course.title} (${course.code})")
                                    appendLine("المحاضر: ${course.instructorName}")
                                    if (materials.isNotEmpty()) {
                                        appendLine("الموضوعات والمحاضرات المرفوعة:")
                                        materials.take(10).forEach {
                                            appendLine("- الأسبوع ${it.weekNumber}: ${it.title} (${it.description})")
                                        }
                                    }
                                }
                                onSummarizeCustomCourse(course.title, syllabusText)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("course_ai_summary_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ملخص AI", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 12.dp
                    ) {
                        categories.forEachIndexed { index, cat ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(cat.labelAr, style = MaterialTheme.typography.labelLarge) },
                                modifier = Modifier.testTag("course_tab_${cat.key}")
                            )
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredMaterials.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "لا توجد عناصر في ${activeCategory.labelAr}",
                            subtitle = if (canManage) "اضغط على زر الإضافة لرفع محاضرة أو شرائح PowerPoint أو فيديو." else "سيتم رفع المحتوى هنا قريباً."
                        )
                    }
                } else {
                    items(filteredMaterials, key = { it.id }) { mat ->
                        val isVideo = mat.fileFormat.equals("VIDEO", ignoreCase = true) ||
                            mat.url.endsWith(".mp4", ignoreCase = true) ||
                            mat.url.contains("youtube", ignoreCase = true)
                        MaterialItemCard(
                            material = mat,
                            accent = accent,
                            canDelete = canManage,
                            isVideo = isVideo,
                            onPreviewOrWatch = {
                                if (isVideo) {
                                    previewVideoMaterial = mat
                                } else {
                                    previewDocMaterial = mat
                                }
                            },
                            onDownloadOrOpen = {
                                val msg = openOrDownloadMaterial(context, mat)
                                onShowMessage(msg)
                            },
                            onSummarize = { onSummarizeMaterial(mat) },
                            onDelete = { onDeleteMaterial(mat.id) }
                        )
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        if (canManage) {
            FloatingActionButton(
                onClick = { showAddMaterialDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_material_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "رفع ملف أو رابط")
            }
        }
    }

    if (showAddMaterialDialog) {
        AddMaterialDialog(
            initialCategory = activeCategory,
            onDismiss = { showAddMaterialDialog = false },
            onSave = { cat, title, desc, week, format, size, url ->
                onAddMaterial(cat.key, title, desc, week, format, size, url)
                showAddMaterialDialog = false
            }
        )
    }

    previewVideoMaterial?.let { vid ->
        InAppVideoPlayerDialog(
            material = vid,
            onDismiss = { previewVideoMaterial = null },
            onSummarizeClick = {
                previewVideoMaterial = null
                onSummarizeMaterial(vid)
            }
        )
    }

    previewDocMaterial?.let { doc ->
        InAppDocumentViewerDialog(
            material = doc,
            onDismiss = { previewDocMaterial = null },
            onDownloadClick = {
                val msg = openOrDownloadMaterial(context, doc)
                onShowMessage(msg)
            },
            onSummarizeClick = {
                previewDocMaterial = null
                onSummarizeMaterial(doc)
            }
        )
    }
}

@Composable
private fun MaterialItemCard(
    material: CourseMaterial,
    accent: Color,
    canDelete: Boolean,
    isVideo: Boolean,
    onPreviewOrWatch: () -> Unit,
    onDownloadOrOpen: () -> Unit,
    onSummarize: () -> Unit,
    onDelete: () -> Unit
) {
    val isSlides = material.fileFormat.equals("PPTX", true) || material.fileFormat.equals("PPT", true)
    val itemIcon = when {
        isVideo -> Icons.Default.PlayCircle
        isSlides -> Icons.Default.Slideshow
        material.category == "links" -> Icons.Default.Link
        else -> Icons.Default.PictureAsPdf
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = itemIcon,
                            contentDescription = null,
                            tint = accent
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "الأسبوع ${material.weekNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${material.fileFormat} • ${material.fileSize.ifBlank { "ملف" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = material.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الملف")
                    }
                }
            }
            if (material.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = material.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onSummarize,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("material_summarize_${material.id}")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تلخيص AI", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onPreviewOrWatch,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("material_preview_${material.id}")
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.PlayCircle else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isVideo) "مشاهدة مدمجة" else "معاينة", maxLines = 1)
                }
                Button(
                    onClick = onDownloadOrOpen,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("material_download_${material.id}")
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.AutoMirrored.Filled.OpenInNew else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isVideo) "خارجي" else "تحميل")
                }
            }
        }
    }
}

@Composable
private fun AddCourseDialog(
    defaultYear: Int,
    defaultTerm: Int,
    defaultDept: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var year by remember { mutableIntStateOf(defaultYear) }
    var term by remember { mutableIntStateOf(defaultTerm) }
    var instructor by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf(defaultDept.ifBlank { "هندسة" }) }
    var accent by remember { mutableStateOf("#1E3A8A") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مادة دراسية", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم المادة (مثال: فيزياء حديثة)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("كود المادة (مثال: PHYS 201)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it },
                    label = { Text("اسم الدكتور / المعيد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dept,
                    onValueChange = { dept = it },
                    label = { Text("القسم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { y ->
                        FilterChip(selected = year == y, onClick = { year = y }, label = { Text("سنة $y") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = term == 1, onClick = { term = 1 }, label = { Text("ترم 1") })
                    FilterChip(selected = term == 2, onClick = { term = 2 }, label = { Text("ترم 2") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#1E3A8A" to "أزرق", "#0D9488" to "أخضر", "#D97706" to "ذهبي", "#7C3AED" to "بنفسجي").forEach { (hex, label) ->
                        FilterChip(selected = accent == hex, onClick = { accent = hex }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, code, year, term, instructor, dept, accent) },
                enabled = title.trim().length >= 2 && code.trim().length >= 2 && instructor.trim().length >= 2
            ) {
                Text("إضافة")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddMaterialDialog(
    initialCategory: MaterialCategory,
    onDismiss: () -> Unit,
    onSave: (MaterialCategory, String, String, Int, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(initialCategory) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var weekNumber by remember { mutableIntStateOf(1) }
    var fileFormat by remember { mutableStateOf(if (initialCategory == MaterialCategory.LINKS) "VIDEO" else "PDF") }
    var fileSize by remember { mutableStateOf(if (initialCategory == MaterialCategory.LINKS) "رابط خارجي" else "1.5 MB") }
    var url by remember { mutableStateOf("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf") }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            url = uri.toString()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        val name = cursor.getString(nameIdx)
                        if (title.isBlank()) title = name.substringBeforeLast('.')
                        fileFormat = name.substringAfterLast('.', "PDF").uppercase().take(10)
                    }
                    if (sizeIdx >= 0) {
                        val bytes = cursor.getLong(sizeIdx)
                        val kb = (bytes / 1024.0).coerceAtLeast(1.0)
                        fileSize = if (kb >= 1024) String.format("%.1f MB", kb / 1024.0) else "${kb.toInt()} KB"
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة محتوى للمادة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MaterialCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (cat == MaterialCategory.LINKS) {
                                    fileFormat = "VIDEO"
                                    fileSize = "رابط خارجي"
                                }
                            },
                            label = { Text(cat.labelAr) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان (مثال: المحاضرة 2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف مختصر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weekNumber.toString(),
                        onValueChange = { weekNumber = it.toIntOrNull()?.coerceIn(1, 30) ?: 1 },
                        label = { Text("الأسبوع") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fileFormat,
                        onValueChange = { fileFormat = it },
                        label = { Text("الصيغة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fileSize,
                        onValueChange = { fileSize = it },
                        label = { Text("الحجم") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("رابط الملف / الفيديو") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (category != MaterialCategory.LINKS) {
                    OutlinedButton(
                        onClick = {
                            docPicker.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.ms-powerpoint",
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "text/plain",
                                    "video/*",
                                    "application/*",
                                    "image/*"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("اختيار PDF / PPTX / Word / فيديو من الجهاز")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, title, description, weekNumber, fileFormat, fileSize, url) },
                enabled = title.trim().length >= 2 && url.isNotBlank()
            ) {
                Text("حفظ ونشر")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
