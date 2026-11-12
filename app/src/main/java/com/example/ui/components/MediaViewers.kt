package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.model.AiSummaryState
import com.example.data.model.CourseMaterial
import com.example.utils.ScientificCalculatorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

fun isDirectMediaStreamUrl(material: CourseMaterial): Boolean {
    val u = material.url.lowercase()
    return u.endsWith(".mp4") || u.endsWith(".m3u8") || u.endsWith(".webm") || u.endsWith(".mkv") ||
        (u.startsWith("content://") && material.fileFormat.equals("VIDEO", ignoreCase = true))
}

@Composable
fun InAppVideoPlayerDialog(
    material: CourseMaterial,
    onDismiss: () -> Unit,
    onSummarizeClick: () -> Unit
) {
    val context = LocalContext.current
    val isDirectStream = remember(material.url) { isDirectMediaStreamUrl(material) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("in_app_video_player_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = material.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = material.courseTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق المشغل")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    if (isDirectStream) {
                        val exoPlayer = remember(material.url) {
                            ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(Uri.parse(material.url)))
                                prepare()
                                playWhenReady = true
                            }
                        }
                        DisposableEffect(exoPlayer) {
                            onDispose {
                                exoPlayer.release()
                            }
                        }
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        EmbeddedWebViewer(
                            url = material.url,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSummarizeClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تلخيص المحاضرة بالذكاء الاصطناعي")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(material.url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "فتح خارجي")
                    }
                }
            }
        }
    }
}

@Composable
fun InAppDocumentViewerDialog(
    material: CourseMaterial,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit,
    onSummarizeClick: () -> Unit
) {
    val context = LocalContext.current
    val isLocalContentPdf = material.url.startsWith("content://") &&
        material.fileFormat.equals("PDF", ignoreCase = true)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("in_app_document_viewer_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = material.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${material.fileFormat} • ${material.fileSize} • ${material.courseTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق المعاينة")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    if (isLocalContentPdf) {
                        LocalPdfPageViewer(uriString = material.url, modifier = Modifier.fillMaxSize())
                    } else if (material.url.startsWith("http://") || material.url.startsWith("https://")) {
                        val viewerUrl = remember(material.url) {
                            val encoded = URLEncoder.encode(material.url, "UTF-8")
                            "https://docs.google.com/gview?embedded=true&url=$encoded"
                        }
                        EmbeddedWebViewer(url = viewerUrl, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "ملف ${material.fileFormat} جاهز للفتح أو التحميل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = material.description.ifBlank { "يمكنك فتح الملف عبر تطبيق العروض التقديمية أو المستندات المثبت على جهازك." },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onDownloadClick) {
                                Text("فتح / تحميل الملف")
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSummarizeClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تلخيص AI", maxLines = 1)
                    }
                    Button(
                        onClick = onDownloadClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تنزيل الملف", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(material.url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "فتح خارجياً")
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPdfPageViewer(
    uriString: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uriString, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                if (pfd != null) {
                    pfd.use {
                        PdfRenderer(it).use { renderer ->
                            pageCount = renderer.pageCount
                            if (renderer.pageCount > 0) {
                                val idx = pageIndex.coerceIn(0, renderer.pageCount - 1)
                                renderer.openPage(idx).use { page ->
                                    val bmp = Bitmap.createBitmap(
                                        (page.width * 2).coerceAtMost(1400),
                                        (page.height * 2).coerceAtMost(2000),
                                        Bitmap.Config.ARGB_8888
                                    )
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmap = bmp
                                }
                            }
                        }
                    }
                } else {
                    loadError = "تعذر فتح ملف الـ PDF"
                }
            } catch (_: Exception) {
                loadError = "تعذر عرض صفحات الملف محلياً"
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(10.dp)
    ) {
        if (loadError != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(loadError!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (bitmap == null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "صفحة ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            )
        }
        if (pageCount > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { if (pageIndex > 0) pageIndex-- },
                    enabled = pageIndex > 0
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "الصفحة السابقة")
                }
                Text("صفحة ${pageIndex + 1} من $pageCount", fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                    enabled = pageIndex < pageCount - 1
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "الصفحة التالية")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedWebViewer(
    url: String,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun AiSummaryDialog(
    state: AiSummaryState,
    onSummarizeCustomText: (title: String, text: String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var customTitle by remember { mutableStateOf("تلخيص محاضرة / ملاحظات") }
    var customInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 320.dp, max = 660.dp)
                .testTag("ai_summary_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("المُلخّص الأكاديمي الذكي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("تلخيص النصوص والمحاضرات بنقاط مراجعة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (state) {
                        AiSummaryState.Idle -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = customTitle,
                                    onValueChange = { customTitle = it },
                                    label = { Text("عنوان الموضوع / المحاضرة") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = customInput,
                                    onValueChange = { customInput = it },
                                    label = { Text("الصق نص المحاضرة أو النقاط المطلوب تلخيصها") },
                                    minLines = 5,
                                    maxLines = 10,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_custom_input")
                                )
                                Button(
                                    onClick = { onSummarizeCustomText(customTitle, customInput) },
                                    enabled = customInput.trim().length >= 8,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("تلخيص فوري بالذكاء الاصطناعي")
                                }
                            }
                        }

                        AiSummaryState.Loading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp)
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("جارٍ تحليل المحتوى وصياغة أهم النقاط...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        is AiSummaryState.Streaming -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = state.sourceTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = state.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        is AiSummaryState.Success -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = state.sourceTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }

                        is AiSummaryState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                if (state is AiSummaryState.Success) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(state.text)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("نسخ الملخص")
                        }
                        Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("تم")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScientificCalculatorDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .testTag("scientific_calculator_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("الآلة الحاسبة العلمية الهندسية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }
                Spacer(Modifier.height(8.dp))
                ScientificCalculatorPanel()
            }
        }
    }
}

@Composable
fun ScientificCalculatorPanel(modifier: Modifier = Modifier) {
    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var useDegrees by remember { mutableStateOf(true) }
    var ansValue by remember { mutableDoubleStateOf(0.0) }
    val history = remember { mutableStateListOf<Pair<String, String>>() }

    fun evaluateNow() {
        when (val res = ScientificCalculatorEngine.evaluate(expression, useDegrees, ansValue)) {
            is ScientificCalculatorEngine.CalcResult.Value -> {
                resultText = res.formatted
                ansValue = res.raw
                errorText = null
                if (expression.isNotBlank()) {
                    history.add(0, expression to res.formatted)
                    if (history.size > 6) history.removeAt(history.lastIndex)
                }
            }
            is ScientificCalculatorEngine.CalcResult.Error -> {
                errorText = res.message
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        // History strip
        if (history.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(history) { (expr, ans) ->
                    FilterChip(
                        selected = false,
                        onClick = { expression = expr; errorText = null },
                        label = { Text("$expr = $ans", maxLines = 1) }
                    )
                }
            }
        }

        // Display Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = Color(0xFF1E3A8A),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        TextButton(
                            onClick = { useDegrees = !useDegrees },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (useDegrees) "DEG" else "RAD",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = expression.ifBlank { "0" },
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorText ?: resultText,
                    color = if (errorText != null) Color(0xFFFCA5A5) else Color.White,
                    style = if (errorText != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Scientific function rows
        val sciRows = listOf(
            listOf("sin(" to "sin", "cos(" to "cos", "tan(" to "tan", "√(" to "√", "^" to "x^y"),
            listOf("asin(" to "sin⁻¹", "acos(" to "cos⁻¹", "ln(" to "ln", "log(" to "log", "!" to "n!"),
            listOf("(" to "(", ")" to ")", "π" to "π", "e" to "e", "ANS" to "ANS")
        )
        sciRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (token, label) ->
                    FilledTonalButton(
                        onClick = {
                            expression += token
                            errorText = null
                        },
                        contentPadding = PaddingValues(vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }

        // Standard keypad
        val numRows = listOf(
            listOf("7", "8", "9", "÷", "C"),
            listOf("4", "5", "6", "×", "⌫"),
            listOf("1", "2", "3", "−", "%"),
            listOf("0", ".", "^2", "+", "=")
        )
        numRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isEquals = key == "="
                    val isOp = key in listOf("÷", "×", "−", "+", "C", "⌫", "%")
                    Button(
                        onClick = {
                            when (key) {
                                "C" -> {
                                    expression = ""
                                    resultText = "0"
                                    errorText = null
                                }
                                "⌫" -> {
                                    if (expression.isNotEmpty()) expression = expression.dropLast(1)
                                    errorText = null
                                }
                                "=" -> evaluateNow()
                                else -> {
                                    expression += key
                                    errorText = null
                                }
                            }
                        },
                        colors = when {
                            isEquals -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            isOp -> ButtonDefaults.filledTonalButtonColors()
                            else -> ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        contentPadding = PaddingValues(vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "مسح", modifier = Modifier.size(18.dp))
                        } else {
                            Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
