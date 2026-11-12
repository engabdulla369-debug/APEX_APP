package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.AppNotificationItem
import com.example.data.model.Assignment
import com.example.data.model.AssignmentProgress
import com.example.data.model.Course
import com.example.data.model.DynamicModule
import com.example.data.model.LeaderboardEntry
import com.example.data.model.ScheduleEntry
import com.example.data.model.UserProfile
import com.example.ui.components.AcademicBannerCard
import com.example.ui.components.AnnouncementsTickerBanner
import com.example.ui.components.CountdownChip
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.parseAccentColor
import com.example.ui.i18n.LocalAppStrings
import java.util.Calendar

private val ACADEMIC_DAYS = listOf(
    1 to "السبت",
    2 to "الأحد",
    3 to "الاثنين",
    4 to "الثلاثاء",
    5 to "الأربعاء",
    6 to "الخميس",
    7 to "الجمعة"
)

private fun currentAcademicDayOfWeek(): Int {
    return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> 1
        Calendar.SUNDAY -> 2
        Calendar.MONDAY -> 3
        Calendar.TUESDAY -> 4
        Calendar.WEDNESDAY -> 5
        Calendar.THURSDAY -> 6
        else -> 7
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    profile: UserProfile,
    announcements: List<Announcement>,
    courses: List<Course>,
    scheduleEntries: List<ScheduleEntry>,
    assignments: List<Assignment>,
    assignmentProgress: Map<String, AssignmentProgress>,
    dynamicModules: List<DynamicModule>,
    notifications: List<AppNotificationItem>,
    leaderboard: List<LeaderboardEntry> = emptyList(),
    flashcardsCount: Int = 0,
    pendingTodoCount: Int = 0,
    onMarkNotificationsRead: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onNavigateAssignments: () -> Unit,
    onOpenAnalytics: () -> Unit = {},
    onOpenFlashcards: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onOpenGlobalReferences: () -> Unit = {},
    onOpenCalculator: () -> Unit,
    onOpenAiSummarizer: () -> Unit,
    onExecuteDynamicAction: (actionType: String, payload: String) -> Unit,
    onOpenDynamicBuilder: () -> Unit,
    onAddAnnouncement: (title: String, body: String, priority: String) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onAddScheduleEntry: (course: Course, dayOfWeek: Int, startTime: String, endTime: String, location: String, instructor: String, sessionType: String) -> Unit,
    onDeleteScheduleEntry: (String) -> Unit,
    onSeedStarterData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var selectedDay by remember { mutableIntStateOf(currentAcademicDayOfWeek()) }
    var showAnnouncementsSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showAddAnnouncementDialog by remember { mutableStateOf(false) }
    var showAddScheduleDialog by remember { mutableStateOf(false) }

    val unreadCount = remember(notifications) { notifications.count { it.isUnread } }
    val enabledModules = remember(dynamicModules) { dynamicModules.filter { it.enabled } }
    val latestCircular = remember(announcements) { announcements.firstOrNull { it.priority == "circular" } }

    val termCourses = remember(courses, profile.academicYear, profile.term, profile.enrolledCourseIds) {
        val enrolled = courses.filter { profile.enrolledCourseIds.contains(it.id) }
        if (enrolled.isNotEmpty()) enrolled
        else courses.filter { it.academicYear == profile.academicYear && it.term == profile.term }
            .ifEmpty { courses }
    }

    val pendingAssignments = remember(assignments, assignmentProgress) {
        assignments.filter { assignmentProgress[it.id]?.completed != true }
            .sortedBy { it.dueAtMillis }
            .take(4)
    }

    val daySchedule = remember(scheduleEntries, selectedDay) {
        scheduleEntries.filter { it.dayOfWeek == selectedDay }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 0. Modern Action Bar Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${strings.focusPointsLabel}: ${profile.focusPoints} • ${strings.studyHoursLabel}: ${formatHoursMinutesAr(profile.totalStudySeconds)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalIconButton(
                            onClick = onOpenAiSummarizer,
                            modifier = Modifier.testTag("home_ai_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "ملخص الذكاء الاصطناعي")
                        }
                        FilledTonalIconButton(
                            onClick = onOpenCalculator,
                            modifier = Modifier.testTag("home_calculator_button")
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = "الآلة الحاسبة العلمية")
                        }
                        FilledTonalIconButton(
                            onClick = {
                                onMarkNotificationsRead()
                                showNotificationsSheet = true
                            },
                            modifier = Modifier.testTag("home_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(unreadCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "الإشعارات")
                            }
                        }
                    }
                }
            }

            // 1. Urgent Announcements Ticker
            item {
                if (announcements.isNotEmpty()) {
                    AnnouncementsTickerBanner(
                        announcements = announcements,
                        onClick = { showAnnouncementsSheet = true }
                    )
                } else if (profile.isBatchRep) {
                    OutlinedButton(
                        onClick = { showAddAnnouncementDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("نشر أول إعلان أو تعميم للدفعة")
                    }
                }
            }

            // 1b. Official Dean Circular Highlight (if present)
            if (latestCircular != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4C1D95)),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAnnouncementsSheet = true }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "تعميم رسمي من عمادة الكلية • ${latestCircular.authorName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFEF3C7),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = latestCircular.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            if (latestCircular.body.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = latestCircular.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.88f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 2. Hero Welcome Banner
            item {
                AcademicBannerCard(
                    title = "${strings.welcomeBack} ${profile.displayName}",
                    subtitle = "${profile.department} • ${profile.academicYear} • ${pendingTodoCount} ${strings.tabDailyTodo}",
                    badgeText = "${pendingAssignments.size} ${strings.tabAssignments} • ${daySchedule.size} ${strings.dailyScheduleTitle}"
                )
            }

            // 2a. Global Learning Hub Quick Strip (Analytics + Streak + Flashcards + Global Refs)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = Color(0xFFFFEDD5),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFEA580C))
                                    }
                                }
                                Column {
                                    Text(strings.analyticsBannerTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${strings.currentStreakLabel}: ${profile.currentStreakDays} ${strings.daysUnit} • ${strings.analyticsBannerSubtitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilledTonalButton(
                                    onClick = onOpenAnalytics,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("home_open_analytics_button")
                                ) {
                                    Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.tabAnalytics)
                                }
                            }
                            item {
                                FilledTonalButton(
                                    onClick = onOpenFlashcards,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("home_open_flashcards_button")
                                ) {
                                    Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${strings.tabFlashcards} ($flashcardsCount)")
                                }
                            }
                            item {
                                FilledTonalButton(
                                    onClick = onOpenGlobalReferences,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("home_open_global_refs_button")
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.globalRefsTitle)
                                }
                            }
                        }
                    }
                }
            }

            // 2a-bis. Top Batch Students Preview Card
            if (leaderboard.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                                    Text(strings.topBatchStudentsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = onOpenLeaderboard, modifier = Modifier.testTag("home_leaderboard_view_all")) {
                                    Text(strings.viewAll)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            leaderboard.take(3).forEachIndexed { idx, entry ->
                                val rankColor = when (idx) {
                                    0 -> Color(0xFFF59E0B)
                                    1 -> Color(0xFF64748B)
                                    else -> Color(0xFFB45309)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                        Surface(color = rankColor.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                            Text("#${idx + 1}", color = rankColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                        Text(
                                            text = if (entry.userId == profile.userId) "${entry.displayName} *" else entry.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${entry.focusPoints} pt • 🔥${entry.currentStreakDays}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2b. Dynamic App Builder Modules
            if (enabledModules.isNotEmpty() || profile.isBatchRep) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.smartToolsTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (profile.isBatchRep) {
                            TextButton(onClick = onOpenDynamicBuilder) {
                                Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.customizeUi)
                            }
                        }
                    }
                    if (enabledModules.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(enabledModules, key = { it.id }) { mod ->
                                val modAccent = parseAccentColor(mod.accentHex, MaterialTheme.colorScheme.primary)
                                val modIcon = when (mod.iconKey) {
                                    "calculator", "open_calculator" -> Icons.Default.Calculate
                                    "meeting", "open_meetings" -> Icons.Default.VideoCall
                                    "study", "navigate_study" -> Icons.Default.Timer
                                    else -> Icons.Default.Star
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .width(190.dp)
                                        .clickable {
                                            if (mod.actionType == "external_url" && mod.actionPayload.isNotBlank()) {
                                                try {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(mod.actionPayload))
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                } catch (_: Exception) {
                                                }
                                            } else {
                                                onExecuteDynamicAction(mod.actionType, mod.actionPayload)
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Surface(
                                            color = modAccent.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(modIcon, contentDescription = null, tint = modAccent, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(mod.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(mod.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Seed quick action for Batch Rep if curriculum empty
            if (courses.isEmpty() && profile.isBatchRep) {
                item {
                    EmptyStateCard(
                        title = "المنصة جاهزة للتهيئة",
                        subtitle = "بصفتك ممثل الدفعة، يمكنك تهيئة المواد الأولية والجدول والاختبارات التجريبية بضغطة واحدة.",
                        action = {
                            Button(
                                onClick = onSeedStarterData,
                                modifier = Modifier.testTag("seed_starter_data_button")
                            ) {
                                Text("تهيئة مواد الدفعة الآن")
                            }
                        }
                    )
                }
            }

            // 3. Quick Access: Courses
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.quickCoursesTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${termCourses.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (termCourses.isEmpty()) {
                    Text(
                        text = "ستظهر موادك الدراسية هنا بمجرد إضافتها من ممثل الدفعة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(termCourses, key = { it.id }) { course ->
                            val accent = parseAccentColor(course.accentHex, MaterialTheme.colorScheme.primary)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium,
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier
                                    .width(176.dp)
                                    .clickable { onOpenCourse(course.id) }
                                    .testTag("quick_course_${course.id}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accent)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = course.code,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = course.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = course.instructorName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Quick Access: Imminent Assignments
            if (pendingAssignments.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.upcomingAssignmentsTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigateAssignments) {
                            Text(strings.viewAll)
                        }
                    }
                }
                items(pendingAssignments, key = { it.id }) { assignment ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateAssignments)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Assignment,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = assignment.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = assignment.courseTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            CountdownChip(dueAtMillis = assignment.dueAtMillis)
                        }
                    }
                }
            }

            // 5. Daily Academic Schedule
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.dailyScheduleTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (profile.isBatchRep && courses.isNotEmpty()) {
                        TextButton(onClick = { showAddScheduleDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.addSession)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ACADEMIC_DAYS) { (dayInt, dayName) ->
                        FilterChip(
                            selected = selectedDay == dayInt,
                            onClick = { selectedDay = dayInt },
                            label = { Text(dayName) }
                        )
                    }
                }
            }

            if (daySchedule.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "لا توجد محاضرات أو سكاشن مسجلة في هذا اليوم.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(daySchedule, key = { it.id }) { entry ->
                    ScheduleEntryCard(
                        entry = entry,
                        canDelete = profile.isBatchRep,
                        onDelete = { onDeleteScheduleEntry(entry.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }

        if (profile.isBatchRep) {
            FloatingActionButton(
                onClick = { showAddAnnouncementDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_announcement_fab")
            ) {
                Icon(Icons.Default.Campaign, contentDescription = "نشر إعلان عاجل")
            }
        }
    }

    if (showAnnouncementsSheet) {
        ModalBottomSheet(onDismissRequest = { showAnnouncementsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "إعلانات الدفعة (${announcements.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (profile.isBatchRep) {
                        TextButton(onClick = {
                            showAnnouncementsSheet = false
                            showAddAnnouncementDialog = true
                        }) {
                            Text("إعلان جديد")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                announcements.forEach { ann ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ann.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (profile.isBatchRep) {
                                    IconButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الإعلان")
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(text = ann.body, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "بواسطة: ${ann.authorName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(onDismissRequest = { showNotificationsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "صندوق الإشعارات الفورية (${notifications.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                if (notifications.isEmpty()) {
                    Text(
                        text = "لا توجد إشعارات جديدة حالياً.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    notifications.forEach { n ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAddAnnouncementDialog) {
        AddAnnouncementDialog(
            canSendCircular = profile.isDean,
            onDismiss = { showAddAnnouncementDialog = false },
            onSave = { title, body, priority ->
                onAddAnnouncement(title, body, priority)
                showAddAnnouncementDialog = false
            }
        )
    }

    if (showAddScheduleDialog && courses.isNotEmpty()) {
        AddScheduleEntryDialog(
            courses = courses,
            defaultDay = selectedDay,
            onDismiss = { showAddScheduleDialog = false },
            onSave = { course, day, start, end, location, instructor, type ->
                onAddScheduleEntry(course, day, start, end, location, instructor, type)
                showAddScheduleDialog = false
            }
        )
    }
}

@Composable
private fun ScheduleEntryCard(
    entry: ScheduleEntry,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val typeLabel = when (entry.sessionType) {
        "section" -> "سكشن"
        "lab" -> "معمل"
        else -> "محاضرة"
    }
    val typeBg = when (entry.sessionType) {
        "section" -> MaterialTheme.colorScheme.secondaryContainer
        "lab" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val typeFg = when (entry.sessionType) {
        "section" -> MaterialTheme.colorScheme.onSecondaryContainer
        "lab" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(74.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(2.dp))
                Text(entry.startTime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(entry.endTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = typeBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = typeFg,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.courseTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(entry.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(entry.instructorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف من الجدول")
                }
            }
        }
    }
}

@Composable
private fun AddAnnouncementDialog(
    canSendCircular: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String, priority: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("urgent") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نشر إعلان للدفعة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الإعلان (يظهر في الشريط العلوي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("التفاصيل") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = priority == "urgent",
                        onClick = { priority = "urgent" },
                        label = { Text("عاجل") }
                    )
                    FilterChip(
                        selected = priority == "normal",
                        onClick = { priority = "normal" },
                        label = { Text("عادي") }
                    )
                    if (canSendCircular) {
                        FilterChip(
                            selected = priority == "circular",
                            onClick = { priority = "circular" },
                            label = { Text("تعميم عميد") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, body.ifBlank { title }, priority) },
                enabled = title.trim().length >= 2
            ) {
                Text("نشر")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun AddScheduleEntryDialog(
    courses: List<Course>,
    defaultDay: Int,
    onDismiss: () -> Unit,
    onSave: (Course, Int, String, String, String, String, String) -> Unit
) {
    var selectedCourse by remember { mutableStateOf(courses.first()) }
    var dayOfWeek by remember { mutableIntStateOf(defaultDay) }
    var startTime by remember { mutableStateOf("10:00 ص") }
    var endTime by remember { mutableStateOf("11:30 ص") }
    var location by remember { mutableStateOf("مدرج أ") }
    var sessionType by remember { mutableStateOf("lecture") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حصة للجدول", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(courses) { course ->
                        FilterChip(
                            selected = selectedCourse.id == course.id,
                            onClick = { selectedCourse = course },
                            label = { Text(course.title) }
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ACADEMIC_DAYS) { (d, label) ->
                        FilterChip(
                            selected = dayOfWeek == d,
                            onClick = { dayOfWeek = d },
                            label = { Text(label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("من") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("إلى") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("القاعة / المدرج") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("lecture" to "محاضرة", "section" to "سكشن", "lab" to "معمل").forEach { (key, label) ->
                        FilterChip(
                            selected = sessionType == key,
                            onClick = { sessionType = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedCourse, dayOfWeek, startTime, endTime, location, selectedCourse.instructorName, sessionType)
                },
                enabled = location.isNotBlank() && startTime.isNotBlank()
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
