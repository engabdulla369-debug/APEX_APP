package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DynamicModule
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.parseAccentColor

@Composable
fun DynamicAppBuilderScreen(
    profile: UserProfile,
    modules: List<DynamicModule>,
    onBack: () -> Unit,
    onSaveModule: (
        existingId: String?,
        title: String,
        subtitle: String,
        moduleType: String,
        iconKey: String,
        accentHex: String,
        actionType: String,
        actionPayload: String,
        sortOrder: Int,
        enabled: Boolean
    ) -> Unit,
    onDeleteModule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    var editingModule by remember { mutableStateOf<DynamicModule?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("منشئ الواجهات الديناميكي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "أضف أو عدّل البطاقات والأدوات بالصفحة الرئيسية لجميع الطلاب فوراً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (modules.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "لا توجد وحدات مخصصة بعد",
                            subtitle = "استخدم زر الإضافة لإنشاء أداة أو إعلان مخصص يظهر للطلاب مباشرة في الرئيسية."
                        )
                    }
                } else {
                    items(modules, key = { it.id }) { mod ->
                        val accent = parseAccentColor(mod.accentHex, MaterialTheme.colorScheme.primary)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(color = accent.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
                                            Text(
                                                text = when (mod.moduleType) {
                                                    "quick_tool" -> "أداة سريعة"
                                                    "official_banner" -> "بانر رسمي"
                                                    else -> "بطاقة رئيسية"
                                                },
                                                color = accent,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text("#${mod.sortOrder}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(mod.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(mod.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = mod.enabled,
                                    onCheckedChange = { newVal ->
                                        onSaveModule(
                                            mod.id,
                                            mod.title,
                                            mod.subtitle,
                                            mod.moduleType,
                                            mod.iconKey,
                                            mod.accentHex,
                                            mod.actionType,
                                            mod.actionPayload,
                                            mod.sortOrder,
                                            newVal
                                        )
                                    }
                                )
                                IconButton(onClick = { editingModule = mod }) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل")
                                }
                                IconButton(onClick = { onDeleteModule(mod.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        if (profile.isBatchRep) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_dynamic_module_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة وحدة ديناميكية")
            }
        }
    }

    if (showCreateDialog || editingModule != null) {
        val current = editingModule
        DynamicModuleEditorDialog(
            initial = current,
            nextOrder = (modules.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            onDismiss = {
                showCreateDialog = false
                editingModule = null
            },
            onSave = { title, subtitle, type, icon, accent, action, payload, order ->
                onSaveModule(
                    current?.id,
                    title,
                    subtitle,
                    type,
                    icon,
                    accent,
                    action,
                    payload,
                    order,
                    current?.enabled ?: true
                )
                showCreateDialog = false
                editingModule = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DynamicModuleEditorDialog(
    initial: DynamicModule?,
    nextOrder: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var subtitle by remember { mutableStateOf(initial?.subtitle ?: "") }
    var moduleType by remember { mutableStateOf(initial?.moduleType ?: "home_card") }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: "study") }
    var accentHex by remember { mutableStateOf(initial?.accentHex ?: "#1E3A8A") }
    var actionType by remember { mutableStateOf(initial?.actionType ?: "navigate_study") }
    var actionPayload by remember { mutableStateOf(initial?.actionPayload ?: "") }
    var sortOrder by remember { mutableIntStateOf(initial?.sortOrder ?: nextOrder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة وحدة تفاعلية جديدة" else "تعديل الوحدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("الوصف المختصر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("النوع", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("home_card" to "بطاقة", "quick_tool" to "أداة سريعة", "official_banner" to "بانر رسمي").forEach { (k, label) ->
                        FilterChip(selected = moduleType == k, onClick = { moduleType = k }, label = { Text(label) })
                    }
                }
                Text("الإجراء عند الضغط", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "navigate_study" to "المذاكرة",
                        "open_calculator" to "الحاسبة",
                        "open_meetings" to "الاجتماعات",
                        "navigate_courses" to "المواد",
                        "navigate_tasks" to "المهام",
                        "external_url" to "رابط خارجي"
                    ).forEach { (k, label) ->
                        FilterChip(selected = actionType == k, onClick = { actionType = k; iconKey = k }, label = { Text(label) })
                    }
                }
                if (actionType == "external_url") {
                    OutlinedTextField(
                        value = actionPayload,
                        onValueChange = { actionPayload = it },
                        label = { Text("الرابط الخارجي (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("#1E3A8A" to "أزرق", "#0D9488" to "أخضر", "#7C3AED" to "ملكي", "#D97706" to "ذهبي").forEach { (hex, label) ->
                        FilterChip(selected = accentHex == hex, onClick = { accentHex = hex }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, subtitle, moduleType, iconKey, accentHex, actionType, actionPayload, sortOrder) },
                enabled = title.trim().length >= 2
            ) {
                Text("حفظ ونشر")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
