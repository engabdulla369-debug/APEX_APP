package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatChannel
import com.example.data.model.ChatMessage
import com.example.data.model.MeetingMessage
import com.example.data.model.MeetingRoom
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoleBadge
import kotlinx.coroutines.delay

@Composable
fun CommunityChatScreen(
    profile: UserProfile,
    channels: List<ChatChannel>,
    activeChannelId: String?,
    messages: List<ChatMessage>,
    meetingRooms: List<MeetingRoom>,
    activeMeetingRoomId: String?,
    meetingMessages: List<MeetingMessage>,
    initialTabIndex: Int = 0,
    onSelectChannel: (String?) -> Unit,
    onCreateTeam: (name: String, description: String) -> Unit,
    onAddMemberToTeam: (channel: ChatChannel, memberUid: String) -> Unit,
    onSendMessage: (channelId: String, text: String, attachmentName: String?, attachmentUrl: String?) -> Unit,
    onSelectMeetingRoom: (String?) -> Unit,
    onCreateMeetingRoom: (title: String, topic: String, courseTitle: String, meetingUrl: String) -> Unit,
    onJoinOrLeaveMeeting: (room: MeetingRoom, join: Boolean) -> Unit,
    onToggleRaiseHand: (room: MeetingRoom) -> Unit,
    onUpdateMeetingAgenda: (roomId: String, notes: String) -> Unit,
    onEndMeetingRoom: (roomId: String) -> Unit,
    onSendMeetingMessage: (roomId: String, text: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex.coerceIn(0, 3)) } // 0=عام, 1=المواد, 2=فرق المشاريع, 3=غرف الاجتماعات
    var showCreateTeamDialog by remember { mutableStateOf(false) }
    var showCreateMeetingDialog by remember { mutableStateOf(false) }

    val activeChannel = remember(channels, activeChannelId) {
        channels.firstOrNull { it.id == activeChannelId }
    }
    val activeRoom = remember(meetingRooms, activeMeetingRoomId) {
        meetingRooms.firstOrNull { it.id == activeMeetingRoomId }
    }

    if (activeChannel != null) {
        ChatRoomView(
            channel = activeChannel,
            profile = profile,
            messages = messages,
            onBack = { onSelectChannel(null) },
            onAddMember = { uid -> onAddMemberToTeam(activeChannel, uid) },
            onSend = { text, attName, attUrl ->
                onSendMessage(activeChannel.id, text, attName, attUrl)
            },
            modifier = modifier
        )
        return
    }

    if (activeRoom != null) {
        LiveMeetingRoomView(
            room = activeRoom,
            profile = profile,
            messages = meetingMessages,
            onBack = { onSelectMeetingRoom(null) },
            onJoinOrLeave = { join -> onJoinOrLeaveMeeting(activeRoom, join) },
            onToggleRaiseHand = { onToggleRaiseHand(activeRoom) },
            onSaveAgenda = { notes -> onUpdateMeetingAgenda(activeRoom.id, notes) },
            onEndRoom = { onEndMeetingRoom(activeRoom.id) },
            onSendMessage = { text -> onSendMeetingMessage(activeRoom.id, text) },
            modifier = modifier
        )
        return
    }

    val filteredChannels = remember(channels, selectedTab) {
        when (selectedTab) {
            0 -> channels.filter { it.type == "general" }
            1 -> channels.filter { it.type == "course" }
            else -> channels.filter { it.type == "team" }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "التواصل وغرف الاجتماعات",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "شات الدفعة، قنوات المواد، فرق المشاريع، وغرف المراجعة التفاعلية",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("الشات العام") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("قنوات المواد") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("فرق المشاريع") })
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("غرف الاجتماعات (${meetingRooms.count { it.status == "active" }})") },
                    modifier = Modifier.testTag("chat_tab_meetings")
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (selectedTab == 3) {
                    if (meetingRooms.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "لا توجد غرف اجتماعات حالياً",
                                subtitle = "أنشئ غرفة اجتماع مصغرة للمذاكرة مع زملائك أو مناقشة مشروع التخرج.",
                                action = {
                                    Button(onClick = { showCreateMeetingDialog = true }) {
                                        Text("فتح غرفة اجتماع الآن")
                                    }
                                }
                            )
                        }
                    } else {
                        items(meetingRooms, key = { it.id }) { room ->
                            MeetingRoomCard(
                                room = room,
                                onEnter = {
                                    if (!room.participantUids.contains(profile.userId) && room.status == "active") {
                                        onJoinOrLeaveMeeting(room, true)
                                    }
                                    onSelectMeetingRoom(room.id)
                                }
                            )
                        }
                    }
                } else {
                    if (filteredChannels.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = if (selectedTab == 2) "لا توجد فرق مشاريع بعد" else "لا توجد قنوات متاحة",
                                subtitle = if (selectedTab == 2) "أنشئ غرفة خاصة لفريق مشروعك لرفع الملفات والتنسيق بعيداً عن زحمة الدفعة." else "ستظهر القنوات بمجرد تهيئة الدفعة.",
                                action = if (selectedTab == 2) {
                                    {
                                        Button(onClick = { showCreateTeamDialog = true }) {
                                            Text("إنشاء فريق مشروع")
                                        }
                                    }
                                } else null
                            )
                        }
                    } else {
                        items(filteredChannels, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                onClick = { onSelectChannel(channel.id) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        FloatingActionButton(
            onClick = {
                if (selectedTab == 3) showCreateMeetingDialog = true else showCreateTeamDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag(if (selectedTab == 3) "create_meeting_room_fab" else "create_project_team_fab")
        ) {
            Icon(
                imageVector = if (selectedTab == 3) Icons.Default.VideoCall else Icons.Default.Groups,
                contentDescription = if (selectedTab == 3) "إنشاء غرفة اجتماع" else "إنشاء فريق مشروع"
            )
        }
    }

    if (showCreateTeamDialog) {
        CreateTeamDialog(
            onDismiss = { showCreateTeamDialog = false },
            onCreate = { name, desc ->
                onCreateTeam(name, desc)
                selectedTab = 2
                showCreateTeamDialog = false
            }
        )
    }

    if (showCreateMeetingDialog) {
        CreateMeetingRoomDialog(
            onDismiss = { showCreateMeetingDialog = false },
            onCreate = { title, topic, courseTitle, url ->
                onCreateMeetingRoom(title, topic, courseTitle, url)
                selectedTab = 3
                showCreateMeetingDialog = false
            }
        )
    }
}

@Composable
private fun MeetingRoomCard(
    room: MeetingRoom,
    onEnter: () -> Unit
) {
    val isActive = room.status == "active"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnter)
            .testTag("meeting_room_card_${room.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (isActive) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (isActive) "● مباشر الآن" else "منتهي",
                            color = if (isActive) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (room.courseTitle.isNotBlank()) {
                        Text(room.courseTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = "${room.participantUids.size} مشارك",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(room.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (room.topic.isNotBlank()) {
                Text(
                    text = room.topic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("بإدارة: ${room.hostName}", style = MaterialTheme.typography.labelSmall)
                FilledTonalButton(
                    onClick = onEnter,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isActive) "دخول الغرفة" else "عرض الملخص")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveMeetingRoomView(
    room: MeetingRoom,
    profile: UserProfile,
    messages: List<MeetingMessage>,
    onBack: () -> Unit,
    onJoinOrLeave: (Boolean) -> Unit,
    onToggleRaiseHand: () -> Unit,
    onSaveAgenda: (String) -> Unit,
    onEndRoom: () -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val isParticipant = room.participantUids.contains(profile.userId)
    val isHandRaised = room.raisedHandUids.contains(profile.userId)
    val isHostOrRep = room.hostId == profile.userId || profile.isBatchRep
    val isActive = room.status == "active"

    var agendaText by remember(room.id, room.agendaNotes) { mutableStateOf(room.agendaNotes) }
    var showAgendaEditor by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var elapsedSec by remember { mutableLongStateOf(((System.currentTimeMillis() - room.startedAtMillis) / 1000L).coerceAtLeast(0L)) }

    LaunchedEffect(room.id, isActive) {
        while (isActive) {
            elapsedSec = ((System.currentTimeMillis() - room.startedAtMillis) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع للغرف")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(room.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (isActive) "مدة الاجتماع: ${formatDurationHms(elapsedSec)}" else "الاجتماع منتهٍ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isHostOrRep && isActive) {
                        IconButton(onClick = onEndRoom) {
                            Icon(Icons.Default.StopCircle, contentDescription = "إنهاء الاجتماع", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Controls Bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                ) {
                    if (isActive) {
                        FilledTonalButton(
                            onClick = { onJoinOrLeave(!isParticipant) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isParticipant) "مغادرة الحضور" else "تسجيل حضور")
                        }
                        FilledTonalButton(
                            onClick = onToggleRaiseHand,
                            colors = if (isHandRaised) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(Icons.Default.PanTool, contentDescription = "رفع اليد", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isHandRaised) "خفض اليد" else "رفع اليد")
                        }
                    }
                    OutlinedButton(onClick = { showAgendaEditor = !showAgendaEditor }) {
                        Icon(Icons.Default.EditNote, contentDescription = "الأجندة", modifier = Modifier.size(18.dp))
                    }
                    if (room.meetingUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(room.meetingUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "رابط الفيديو", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Participant chips
                if (room.participantNames.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        room.participantUids.zip(room.participantNames).forEach { (uid, name) ->
                            val raised = room.raisedHandUids.contains(uid)
                            Surface(
                                color = if (raised) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    if (raised) {
                                        Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                if (showAgendaEditor) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            OutlinedTextField(
                                value = agendaText,
                                onValueChange = { agendaText = it },
                                label = { Text("ملاحظات وأجندة الاجتماع المشتركة") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    onSaveAgenda(agendaText)
                                    showAgendaEditor = false
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("حفظ الأجندة للجميع")
                            }
                        }
                    }
                }
            }
        }

        // Messages
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "مرحباً بك في غرفة الاجتماع",
                        subtitle = room.agendaNotes.ifBlank { "شارك أفكارك وملخصاتك مع المشاركين في النقاش المباشر." }
                    )
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == profile.userId
                    Column(
                        horizontalAlignment = if (isMine) Alignment.Start else Alignment.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(msg.senderName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            RoleBadge(roleKey = msg.senderRole)
                        }
                        Spacer(Modifier.height(3.dp))
                        Surface(
                            color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("اكتب في دردشة الاجتماع...") },
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val txt = chatInput.trim()
                        if (txt.isNotEmpty()) {
                            onSendMessage(txt)
                            chatInput = ""
                        }
                    },
                    enabled = chatInput.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال")
                }
            }
        }
    }
}

@Composable
private fun CreateMeetingRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, topic: String, courseTitle: String, meetingUrl: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var courseTitle by remember { mutableStateOf("") }
    var meetingUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فتح غرفة اجتماع مصغرة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم الغرفة (مثال: مراجعة الفيزياء)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("محور النقاش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = courseTitle,
                    onValueChange = { courseTitle = it },
                    label = { Text("المادة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = meetingUrl,
                    onValueChange = { meetingUrl = it },
                    label = { Text("رابط صوت/فيديو خارجي مثل Meet (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, topic, courseTitle, meetingUrl) },
                enabled = title.trim().length >= 2
            ) {
                Text("إنشاء وبدء")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ChannelCard(
    channel: ChatChannel,
    onClick: () -> Unit
) {
    val icon = when (channel.type) {
        "general" -> Icons.Default.Forum
        "course" -> Icons.AutoMirrored.Filled.MenuBook
        else -> Icons.Default.Groups
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_channel_${channel.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = channel.lastMessage.ifBlank { channel.description },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatRoomView(
    channel: ChatChannel,
    profile: UserProfile,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onAddMember: (String) -> Unit,
    onSend: (text: String, attachmentName: String?, attachmentUrl: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var attachedName by remember { mutableStateOf<String?>(null) }
    var attachedUrl by remember { mutableStateOf<String?>(null) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedUrl = uri.toString()
            var name = "ملف_مرفق"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: name
                }
            }
            attachedName = name
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع للقنوات")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (channel.type == "team" && (channel.createdBy == profile.userId || profile.isBatchRep)) {
                    IconButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عضو للفريق")
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "ابدأ المحادثة",
                        subtitle = "اكتب سؤالك أو شارك ملفاً مع زملائك في ${channel.name}"
                    )
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == profile.userId
                    Column(
                        horizontalAlignment = if (isMine) Alignment.Start else Alignment.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = msg.senderName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            RoleBadge(roleKey = msg.senderRole)
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (msg.attachmentName.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(msg.attachmentName, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (attachedName != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("مرفق: $attachedName", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { attachedName = null; attachedUrl = null }) {
                        Text("إزالة")
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "إرفاق ملف")
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("اكتب رسالتك...") },
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val content = inputText.trim()
                        if (content.isNotEmpty()) {
                            onSend(content, attachedName, attachedUrl)
                            inputText = ""
                            attachedName = null
                            attachedUrl = null
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier.testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAddMemberDialog) {
        var memberUid by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("إضافة زميل لفريق المشروع", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = memberUid,
                    onValueChange = { memberUid = it },
                    label = { Text("معرف الطالب (UID من الملف الشخصي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddMember(memberUid)
                        showAddMemberDialog = false
                    },
                    enabled = memberUid.trim().length >= 4
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = { TextButton(onClick = { showAddMemberDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun CreateTeamDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء فريق مشروع جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الفريق / المشروع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف المشروع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description.ifBlank { "غرفة عمل المشروع" }) },
                enabled = name.trim().length >= 2
            ) {
                Text("إنشاء")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
