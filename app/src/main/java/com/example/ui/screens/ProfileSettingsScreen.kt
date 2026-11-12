package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Course
import com.example.data.model.JoinRequest
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.RoleBadge
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsScreen(
    profile: UserProfile,
    courses: List<Course>,
    joinRequests: List<JoinRequest>,
    activeLanguage: AppLanguage = LocalAppLanguage.current,
    onChangeLanguage: (AppLanguage) -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit,
    onUpdateNotifications: (notifyLectures: Boolean?, notifyAssignments: Boolean?) -> Unit,
    onToggleCourseEnrollment: (String) -> Unit,
    onUpdateMemberRole: (targetUid: String, role: String, status: String, assignedCourses: List<String>) -> Unit,
    onOpenDynamicBuilder: () -> Unit,
    onSeedStarterData: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    var editingMember by remember { mutableStateOf<JoinRequest?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Student Info Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                RoleBadge(roleKey = profile.role)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    InfoRow(icon = Icons.Default.School, label = "القسم والفرقة", value = "${profile.department} • الفرقة ${profile.academicYear}")
                    Spacer(Modifier.height(8.dp))
                    InfoRow(icon = Icons.Default.Badge, label = "رقم السجل", value = profile.studentRecordNumber)
                    Spacer(Modifier.height(8.dp))
                    InfoRow(icon = Icons.Default.Email, label = "البريد الإلكتروني", value = profile.email)
                    Spacer(Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "معرفك للانضمام لفرق المشاريع: ${profile.userId.take(10)}...",
                                style = MaterialTheme.typography.labelSmall
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(profile.userId))
                                onShowMessage("تم نسخ معرف الطالب")
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ المعرف", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Study & Focus Achievements Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("إنجازاتك في المذاكرة والتركيز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "نقاط التركيز: ${profile.focusPoints} • إجمالي الوقت: ${formatHoursMinutesAr(profile.totalStudySeconds)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "جلسات بومودورو المكتملة: ${profile.pomodoroSessionsCompleted} • أطول جلسة متصلة: ${formatHoursMinutesAr(profile.longestSessionSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Multi-Language & RTL/LTR Switcher Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag("language_switcher_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.languageSectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.languageSectionSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val isSelected = activeLanguage == lang
                            FilterChip(
                                selected = isSelected,
                                onClick = { onChangeLanguage(lang) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text("${lang.nativeLabel} (${lang.badge})") },
                                modifier = Modifier.testTag("lang_chip_${lang.code}")
                            )
                        }
                    }
                }
            }
        }

        // Enrolled Courses
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.enrolledCoursesTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.enrolledCoursesSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    if (courses.isEmpty()) {
                        Text("لا توجد مواد مسجلة بعد", style = MaterialTheme.typography.bodySmall)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            courses.forEach { course ->
                                val selected = profile.enrolledCourseIds.contains(course.id)
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleCourseEnrollment(course.id) },
                                    label = { Text(course.title) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Appearance & Notifications
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.appearanceSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(strings.darkModeTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(strings.darkModeSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = profile.darkMode,
                            onCheckedChange = onToggleDarkMode,
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Text(strings.lectureAlertsLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = profile.notifyLectures,
                            onCheckedChange = { onUpdateNotifications(it, null) }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(10.dp))
                            Text(strings.assignmentAlertsLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = profile.notifyAssignments,
                            onCheckedChange = { onUpdateNotifications(null, it) }
                        )
                    }
                }
            }
        }

        // Batch Rep / Admin Panel
        if (profile.isBatchRep) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.adminPanelTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "رمز الانضمام المباشر للدفعة: ENG-2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))

                        FilledTonalButton(
                            onClick = onOpenDynamicBuilder,
                            modifier = Modifier.fillMaxWidth().testTag("open_dynamic_builder_button")
                        ) {
                            Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("فتح منشئ الواجهات والأدوات الديناميكي")
                        }
                        Spacer(Modifier.height(10.dp))

                        if (courses.isEmpty()) {
                            OutlinedButton(onClick = onSeedStarterData, modifier = Modifier.fillMaxWidth()) {
                                Text("تهيئة المواد والجداول الأولية")
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        val pending = joinRequests.filter { it.approvalStatus == "pending" }
                        val approvedMembers = joinRequests.filter { it.approvalStatus == "approved" && it.userId != profile.userId }

                        if (pending.isNotEmpty()) {
                            Text("طلبات انضمام بانتظار الموافقة (${pending.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(6.dp))
                            pending.forEach { req ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(req.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("${req.department} • سجل ${req.studentRecordNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row {
                                        IconButton(onClick = { onUpdateMemberRole(req.userId, "student", "approved", emptyList()) }) {
                                            Icon(Icons.Default.Check, contentDescription = "قبول", tint = MaterialTheme.colorScheme.secondary)
                                        }
                                        IconButton(onClick = { onUpdateMemberRole(req.userId, "student", "rejected", emptyList()) }) {
                                            Icon(Icons.Default.Close, contentDescription = "رفض", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        if (approvedMembers.isNotEmpty()) {
                            Text("أعضاء الدفعة المسجلون (${approvedMembers.size})", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(6.dp))
                            approvedMembers.forEach { member ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(UserRole.fromKey(member.role).labelAr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    TextButton(onClick = { editingMember = member }) {
                                        Text("تعديل الصلاحية")
                                    }
                                }
                            }
                        } else if (pending.isEmpty()) {
                            Text(
                                text = "لا توجد طلبات معلقة حالياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.signOutButton)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editingMember?.let { member ->
        EditMemberRoleDialog(
            member = member,
            courses = courses,
            canAssignDean = profile.isDean,
            onDismiss = { editingMember = null },
            onSave = { role, assignedCourses ->
                onUpdateMemberRole(member.userId, role, "approved", assignedCourses)
                editingMember = null
            }
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditMemberRoleDialog(
    member: JoinRequest,
    courses: List<Course>,
    canAssignDean: Boolean,
    onDismiss: () -> Unit,
    onSave: (role: String, assignedCourseIds: List<String>) -> Unit
) {
    var role by remember { mutableStateOf(member.role) }
    val selectedCourses = remember {
        mutableStateListOf<String>().apply { addAll(member.assignedCourseIds) }
    }
    val assignableRoles = remember(canAssignDean) {
        if (canAssignDean) UserRole.entries else UserRole.entries.filter { it != UserRole.DEAN }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل صلاحية ${member.displayName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    assignableRoles.forEach { r ->
                        FilterChip(
                            selected = role == r.key,
                            onClick = { role = r.key },
                            label = { Text(r.labelAr) }
                        )
                    }
                }
                if (role == "ta" && courses.isNotEmpty()) {
                    Text("المواد المصرح له برفع محاضراتها:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        courses.forEach { c ->
                            val checked = selectedCourses.contains(c.id)
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    if (checked) selectedCourses.remove(c.id) else selectedCourses.add(c.id)
                                },
                                label = { Text(c.title) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(role, if (role == "ta") selectedCourses.toList() else emptyList())
            }) {
                Text("حفظ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
