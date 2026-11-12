package com.example.ui.components

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Announcement
import com.example.data.model.CourseMaterial
import com.example.data.model.UserRole
import kotlinx.coroutines.delay

fun parseAccentColor(hex: String, fallback: Color = Color(0xFF1E3A8A)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

fun formatCountdownArabic(dueAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Pair<String, Boolean> {
    val diff = dueAtMillis - nowMillis
    if (diff <= 0L) return "انتهى الموعد" to true
    val totalMinutes = diff / 60_000L
    val days = totalMinutes / (60L * 24L)
    val hours = (totalMinutes % (60L * 24L)) / 60L
    val mins = totalMinutes % 60L
    val label = when {
        days > 0 -> "باقي $days يوم و $hours ساعة"
        hours > 0 -> "باقي $hours ساعة و $mins دقيقة"
        else -> "باقي $mins دقيقة"
    }
    return label to false
}

fun openOrDownloadMaterial(context: Context, material: CourseMaterial): String {
    val rawUrl = material.url.trim()
    if (rawUrl.isBlank()) {
        return "لا يوجد رابط مرفق لهذا العنصر"
    }
    return try {
        val uri = Uri.parse(rawUrl)
        val isHttp = rawUrl.startsWith("http://") || rawUrl.startsWith("https://")
        val isDirectDoc = isHttp && (
            material.fileFormat.equals("PDF", ignoreCase = true) ||
                material.fileFormat.equals("DOCX", ignoreCase = true) ||
                material.fileFormat.equals("PPTX", ignoreCase = true) ||
                rawUrl.endsWith(".pdf", ignoreCase = true)
            )

        if (isDirectDoc && material.category != "links") {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                val ext = material.fileFormat.lowercase().ifBlank { "pdf" }
                val safeName = material.title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").take(40).ifBlank { "material" }
                val request = DownloadManager.Request(uri)
                    .setTitle(material.title)
                    .setDescription("جارٍ التحميل من منصة الدفعة")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$safeName.$ext")
                dm.enqueue(request)
                return "بدأ تحميل: ${material.title}"
            }
        }
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (rawUrl.startsWith("content://")) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(viewIntent)
        "تم فتح: ${material.title}"
    } catch (_: Exception) {
        "تعذر فتح الرابط على هذا الجهاز"
    }
}

@Composable
fun RoleBadge(roleKey: String, modifier: Modifier = Modifier) {
    val role = UserRole.fromKey(roleKey)
    val (bg, fg) = when (role) {
        UserRole.DEAN -> Color(0xFF4C1D95) to Color(0xFFFEF3C7)
        UserRole.BATCH_REP -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        UserRole.TA -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        UserRole.STUDENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = role.labelAr,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CountdownChip(dueAtMillis: Long, modifier: Modifier = Modifier) {
    val (text, isOverdue) = remember(dueAtMillis) { formatCountdownArabic(dueAtMillis) }
    val diffHours = (dueAtMillis - System.currentTimeMillis()) / 3_600_000L
    val (bg, fg) = when {
        isOverdue -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        diffHours <= 48L -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AnnouncementsTickerBanner(
    announcements: List<Announcement>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (announcements.isEmpty()) return

    var currentIndex by remember(announcements.size) { mutableIntStateOf(0) }
    LaunchedEffect(announcements) {
        while (announcements.size > 1) {
            delay(4000L)
            currentIndex = (currentIndex + 1) % announcements.size
        }
    }
    val active = announcements[currentIndex.coerceIn(0, announcements.lastIndex)]
    val isCircular = active.priority == "circular"
    val isUrgent = active.priority == "urgent"

    val containerColor = when {
        isCircular -> Color(0xFFEDE9FE)
        isUrgent -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val iconColor = when {
        isCircular -> Color(0xFF6D28D9)
        isUrgent -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("announcements_ticker_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconColor)
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "إعلان عاجل",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            AnimatedContent(
                targetState = active,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "ticker_anim"
            ) { item ->
                Column {
                    Text(
                        text = when (item.priority) {
                            "circular" -> "تعميم رسمي من عمادة الكلية"
                            "urgent" -> "تنبيه عاجل للدفعة"
                            else -> "إعلان أكاديمي"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = iconColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "عرض كل الإعلانات",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AcademicBannerCard(
    title: String,
    subtitle: String,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_dashboard_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xCC0F172A),
                                Color(0x991E3A8A)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_state),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(14.dp))
                action()
            }
        }
    }
}
