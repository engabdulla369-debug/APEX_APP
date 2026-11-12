package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiSummaryState
import com.example.data.model.Announcement
import com.example.data.model.AppNotificationItem
import com.example.data.model.Assignment
import com.example.data.model.AssignmentProgress
import com.example.data.model.BatchConstants
import com.example.data.model.ChatChannel
import com.example.data.model.ChatMessage
import com.example.data.model.Course
import com.example.data.model.CourseMaterial
import com.example.data.model.DynamicModule
import com.example.data.model.Flashcard
import com.example.data.model.GlobalReference
import com.example.data.model.JoinRequest
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MeetingMessage
import com.example.data.model.MeetingRoom
import com.example.data.model.PersonalTask
import com.example.data.model.Quiz
import com.example.data.model.QuizAttempt
import com.example.data.model.QuizQuestion
import com.example.data.model.ScheduleEntry
import com.example.data.model.StudySession
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.unwrapFirestoreThrowable
import com.example.data.repository.AiSummaryRepository
import com.example.data.repository.BatchRepository
import com.example.utils.BatchNotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class BatchViewModel(
    private val repository: BatchRepository,
    val currentUserId: String,
    private val appContext: Context? = null,
    private val aiSummaryRepository: AiSummaryRepository = AiSummaryRepository()
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _isActionBusy = MutableStateFlow(false)
    val isActionBusy: StateFlow<Boolean> = _isActionBusy.asStateFlow()

    private var hasAttemptedAutoSeed = false

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { profile ->
            if (profile != null && profile.isApproved && profile.isBatchRep && !hasAttemptedAutoSeed) {
                hasAttemptedAutoSeed = true
                viewModelScope.launch {
                    val name = profile.displayName.ifBlank { "ممثل الدفعة" }
                    val seeded = repository.seedStarterAcademicDataIfEmpty(name).getOrDefault(false)
                    if (!seeded) {
                        repository.seedDynamicAndMeetingStarters(currentUserId, name)
                        repository.seedGlobalReferencesIfEmpty(currentUserId)
                    }
                }
            }
            UiState.Success(profile)
        }
        .catch { error ->
            val root = unwrapFirestoreThrowable(error)
            if (root == error && error is CancellationException) throw error
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.message ?: "تعذر تحميل بيانات الحساب"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val approvedBatchFlow: Flow<String?> = userProfileState
        .map { state ->
            val profile = (state as? UiState.Success)?.data
            if (profile != null && profile.isApproved && profile.batchId.isNotBlank()) profile.batchId else null
        }
        .distinctUntilChanged()

    private fun <T> observeWhenApproved(
        fallback: T,
        streamFactory: (batchId: String) -> Flow<T>
    ): StateFlow<UiState<T>> = approvedBatchFlow
        .flatMapLatest { batchId ->
            if (batchId == null) {
                flowOf<UiState<T>>(UiState.Success(fallback))
            } else {
                streamFactory(batchId)
                    .map<T, UiState<T>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        if (root == error && error is CancellationException) throw error
                        Log.w(TAG, "Stream error", root)
                        emit(UiState.Error(root.message ?: "حدث خطأ في مزامنة البيانات"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val seenAnnouncementIds = mutableSetOf<String>()
    private var initialAnnouncementsLoaded = false
    private val _notifications = MutableStateFlow<List<AppNotificationItem>>(emptyList())
    val notifications: StateFlow<List<AppNotificationItem>> = _notifications.asStateFlow()

    val announcementsState: StateFlow<UiState<List<Announcement>>> =
        observeWhenApproved(emptyList()) { batchId ->
            repository.observeAnnouncements(batchId).map { list ->
                syncAnnouncementNotifications(list)
                list
            }
        }

    private fun syncAnnouncementNotifications(list: List<Announcement>) {
        if (!initialAnnouncementsLoaded) {
            initialAnnouncementsLoaded = true
            list.forEach { seenAnnouncementIds.add(it.id) }
            _notifications.value = list.take(15).map { ann ->
                AppNotificationItem(
                    id = "ann_${ann.id}",
                    title = when (ann.priority) {
                        "circular" -> "تعميم رسمي: ${ann.title}"
                        "urgent" -> "تنبيه عاجل: ${ann.title}"
                        else -> ann.title
                    },
                    body = ann.body.ifBlank { "بواسطة ${ann.authorName}" },
                    category = ann.priority,
                    timestampMillis = ann.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                    isUnread = false
                )
            }
            return
        }

        val newlyAdded = list.filter { !seenAnnouncementIds.contains(it.id) }
        if (newlyAdded.isNotEmpty()) {
            newlyAdded.forEach { ann ->
                seenAnnouncementIds.add(ann.id)
                val notifTitle = when (ann.priority) {
                    "circular" -> "تعميم رسمي جديد من الإدارة"
                    "urgent" -> "إعلان عاجل للدفعة"
                    else -> "تنبيه أكاديمي جديد"
                }
                val item = AppNotificationItem(
                    id = "ann_${ann.id}",
                    title = ann.title,
                    body = ann.body.ifBlank { "بواسطة ${ann.authorName}" },
                    category = ann.priority,
                    timestampMillis = System.currentTimeMillis(),
                    isUnread = true
                )
                _notifications.value = (listOf(item) + _notifications.value).distinctBy { it.id }.take(30)
                val prefs = (userProfileState.value as? UiState.Success)?.data
                if (appContext != null && (ann.priority == "circular" || prefs?.notifyLectures != false)) {
                    BatchNotificationHelper.postAlert(
                        context = appContext,
                        notificationId = ann.id.hashCode(),
                        title = notifTitle,
                        body = ann.title
                    )
                }
            }
        }
    }

    fun markAllNotificationsRead() {
        _notifications.value = _notifications.value.map { it.copy(isUnread = false) }
    }

    val coursesState: StateFlow<UiState<List<Course>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeCourses(batchId) }

    val scheduleState: StateFlow<UiState<List<ScheduleEntry>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeScheduleEntries(batchId) }

    val assignmentsState: StateFlow<UiState<List<Assignment>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeAssignments(batchId) }

    val assignmentProgressMap: StateFlow<Map<String, AssignmentProgress>> = approvedBatchFlow
        .flatMapLatest { batchId ->
            if (batchId == null) flowOf(emptyMap())
            else repository.observeAssignmentProgress(currentUserId)
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (root == error && error is CancellationException) throw error
                    Log.w(TAG, "Assignment progress error", root)
                    emit(emptyMap())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    val quizzesState: StateFlow<UiState<List<Quiz>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeQuizzes(batchId) }

    val quizAttempts: StateFlow<List<QuizAttempt>> = approvedBatchFlow
        .flatMapLatest { batchId ->
            if (batchId == null) flowOf(emptyList())
            else repository.observeQuizAttempts(currentUserId)
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (root == error && error is CancellationException) throw error
                    Log.w(TAG, "Quiz attempts error", root)
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val chatChannelsState: StateFlow<UiState<List<ChatChannel>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeChatChannels(currentUserId, batchId) }

    val studySessions: StateFlow<List<StudySession>> = approvedBatchFlow
        .flatMapLatest { batchId ->
            if (batchId == null) flowOf(emptyList())
            else repository.observeStudySessions(currentUserId)
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (root == error && error is CancellationException) throw error
                    Log.w(TAG, "Study sessions error", root)
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeLeaderboard(batchId) }

    val meetingRoomsState: StateFlow<UiState<List<MeetingRoom>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeMeetingRooms(batchId) }

    val dynamicModulesState: StateFlow<UiState<List<DynamicModule>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeDynamicModules(batchId) }

    val flashcards: StateFlow<List<Flashcard>> = approvedBatchFlow
        .flatMapLatest { batchId ->
            if (batchId == null) flowOf(emptyList())
            else repository.observeFlashcards(currentUserId)
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (root == error && error is CancellationException) throw error
                    Log.w(TAG, "Flashcards error", root)
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val personalTasks: StateFlow<List<PersonalTask>> = approvedBatchFlow
        .flatMapLatest { batchId ->
            if (batchId == null) flowOf(emptyList())
            else repository.observePersonalTasks(currentUserId)
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (root == error && error is CancellationException) throw error
                    Log.w(TAG, "Personal tasks error", root)
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val globalReferencesState: StateFlow<UiState<List<GlobalReference>>> =
        observeWhenApproved(emptyList()) { batchId -> repository.observeGlobalReferences(batchId) }

    val joinRequests: StateFlow<List<JoinRequest>> = userProfileState
        .map { state ->
            val profile = (state as? UiState.Success)?.data
            profile != null && profile.isApproved && profile.isBatchRep
        }
        .distinctUntilChanged()
        .flatMapLatest { isRep ->
            if (!isRep) flowOf(emptyList())
            else repository.observeJoinRequests()
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (root == error && error is CancellationException) throw error
                    Log.w(TAG, "Join requests error", root)
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    private val _selectedCourseId = MutableStateFlow<String?>(null)
    val selectedCourseMaterials: StateFlow<UiState<List<CourseMaterial>>> = _selectedCourseId
        .flatMapLatest { courseId ->
            if (courseId.isNullOrBlank()) {
                flowOf<UiState<List<CourseMaterial>>>(UiState.Success(emptyList()))
            } else {
                repository.observeMaterialsForCourse(courseId, BatchConstants.DEFAULT_BATCH_ID)
                    .map<List<CourseMaterial>, UiState<List<CourseMaterial>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        if (root == error && error is CancellationException) throw error
                        emit(UiState.Error(root.message ?: "تعذر تحميل ملفات المادة"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelMessages: StateFlow<UiState<List<ChatMessage>>> = _selectedChannelId
        .flatMapLatest { channelId ->
            if (channelId.isNullOrBlank()) {
                flowOf<UiState<List<ChatMessage>>>(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(channelId, BatchConstants.DEFAULT_BATCH_ID)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        if (root == error && error is CancellationException) throw error
                        emit(UiState.Error(root.message ?: "تعذر تحميل الرسائل"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _selectedMeetingRoomId = MutableStateFlow<String?>(null)
    val selectedMeetingMessages: StateFlow<UiState<List<MeetingMessage>>> = _selectedMeetingRoomId
        .flatMapLatest { roomId ->
            if (roomId.isNullOrBlank()) {
                flowOf<UiState<List<MeetingMessage>>>(UiState.Success(emptyList()))
            } else {
                repository.observeMeetingMessages(roomId, BatchConstants.DEFAULT_BATCH_ID)
                    .map<List<MeetingMessage>, UiState<List<MeetingMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        if (root == error && error is CancellationException) throw error
                        emit(UiState.Error(root.message ?: "تعذر تحميل دردشة الغرفة"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _aiSummaryState = MutableStateFlow<AiSummaryState>(AiSummaryState.Idle)
    val aiSummaryState: StateFlow<AiSummaryState> = _aiSummaryState.asStateFlow()
    private var aiSummaryJob: Job? = null

    fun summarizeWithAi(
        sourceTitle: String,
        contentText: String,
        courseTitle: String = "",
        extraInstructions: String = ""
    ) {
        aiSummaryJob?.cancel()
        aiSummaryJob = viewModelScope.launch {
            aiSummaryRepository.summarizeContentStream(
                sourceTitle = sourceTitle,
                contentText = contentText,
                courseTitle = courseTitle,
                extraInstructions = extraInstructions
            ).collect { state ->
                _aiSummaryState.value = state
            }
        }
    }

    fun summarizeMaterialWithAi(material: CourseMaterial) {
        val localText = if (appContext != null && material.url.startsWith("content://")) {
            aiSummaryRepository.readReadableTextFromUri(appContext, material.url)
        } else ""
        val combinedText = buildString {
            appendLine("عنوان المحاضرة / الملف: ${material.title}")
            appendLine("الأسبوع الدراسي: ${material.weekNumber}")
            appendLine("نوع الملف: ${material.fileFormat}")
            if (material.description.isNotBlank()) {
                appendLine("ملخص/وصف المحتوى: ${material.description}")
            }
            if (localText.isNotBlank()) {
                appendLine("نص الملف:")
                appendLine(localText)
            } else {
                appendLine("مطلوب: قدّم شرحاً مركزاً ومراجعة أكاديمية شاملة لموضوع '${material.title}' في مادة '${material.courseTitle}' مع القوانين والأفكار الرئيسية.")
            }
        }
        summarizeWithAi(
            sourceTitle = material.title,
            contentText = combinedText,
            courseTitle = material.courseTitle
        )
    }

    fun clearAiSummary() {
        aiSummaryJob?.cancel()
        _aiSummaryState.value = AiSummaryState.Idle
    }

    fun selectCourseForMaterials(courseId: String?) {
        _selectedCourseId.value = courseId
    }

    fun selectChannelForMessages(channelId: String?) {
        _selectedChannelId.value = channelId
    }

    fun selectMeetingRoom(roomId: String?) {
        _selectedMeetingRoomId.value = roomId
    }

    fun registerProfile(
        displayName: String,
        email: String,
        studentRecordNumber: String,
        department: String,
        academicYear: Int,
        term: Int,
        joinCode: String
    ) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = repository.registerUserProfile(
                displayName = displayName,
                email = email,
                studentRecordNumber = studentRecordNumber,
                department = department,
                academicYear = academicYear,
                term = term,
                joinCodeInput = joinCode
            )
            _isActionBusy.value = false
            result.onSuccess { profile ->
                if (profile.isApproved) {
                    _events.emit("تم ربط حسابك بالدفعة بنجاح!")
                } else {
                    _events.emit("تم إرسال طلب الانضمام لممثل الدفعة")
                }
            }.onFailure { err ->
                _events.emit("تعذر حفظ البيانات: ${err.localizedMessage ?: "تأكد من صحة رمز الدفعة"}")
            }
        }
    }

    fun submitJoinCodeForPendingAccount(joinCode: String) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = repository.verifyJoinCodeAndActivate(joinCode)
            _isActionBusy.value = false
            result.onSuccess {
                _events.emit("تم تفعيل الحساب بنجاح!")
            }.onFailure {
                _events.emit("رمز الدفعة غير صحيح أو لم يتم التحقق بعد")
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUserSettings(darkMode = enabled)
        }
    }

    fun setPreferredLanguage(languageCode: String) {
        viewModelScope.launch {
            repository.updateUserSettings(preferredLanguage = languageCode)
                .onSuccess {
                    val msg = when (languageCode) {
                        "en" -> "Language switched to English (LTR)"
                        "fr" -> "Langue changée en Français (LTR)"
                        "es" -> "Idioma cambiado a Español (LTR)"
                        else -> "تم تحويل لغة التطبيق إلى العربية (RTL)"
                    }
                    _events.emit(msg)
                }
        }
    }

    fun updateNotificationPrefs(notifyLectures: Boolean? = null, notifyAssignments: Boolean? = null) {
        viewModelScope.launch {
            repository.updateUserSettings(
                notifyLectures = notifyLectures,
                notifyAssignments = notifyAssignments
            )
        }
    }

    fun toggleCourseEnrollment(courseId: String, currentEnrolled: List<String>) {
        viewModelScope.launch {
            val updated = if (currentEnrolled.contains(courseId)) {
                currentEnrolled - courseId
            } else {
                (currentEnrolled + courseId).take(20)
            }
            repository.updateUserSettings(enrolledCourseIds = updated)
        }
    }

    fun updateAcademicSelection(academicYear: Int, term: Int) {
        viewModelScope.launch {
            repository.updateUserSettings(academicYear = academicYear, term = term)
        }
    }

    fun approveOrUpdateMember(
        targetUserId: String,
        role: String,
        approvalStatus: String,
        assignedCourseIds: List<String>
    ) {
        viewModelScope.launch {
            val result = repository.updateMemberRoleAndApproval(targetUserId, role, approvalStatus, assignedCourseIds)
            result.onSuccess {
                _events.emit("تم تحديث صلاحية العضو بنجاح")
            }.onFailure {
                _events.emit("تعذر تحديث الصلاحية")
            }
        }
    }

    fun addAnnouncement(title: String, body: String, priority: String, authorName: String) {
        viewModelScope.launch {
            val result = repository.addAnnouncement(title, body, priority, authorName)
            result.onSuccess {
                val label = if (priority == "circular") "تم إصدار التعميم الرسمي للدفعة" else "تم نشر الإعلان للدفعة"
                _events.emit(label)
            }.onFailure { _events.emit("تعذر نشر الإعلان") }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        viewModelScope.launch {
            repository.deleteAnnouncement(announcementId)
        }
    }

    fun addCourse(
        title: String,
        code: String,
        academicYear: Int,
        term: Int,
        instructorName: String,
        department: String,
        accentHex: String
    ) {
        viewModelScope.launch {
            val result = repository.addCourse(title, code, academicYear, term, instructorName, department, accentHex)
            result.onSuccess { _events.emit("تمت إضافة المادة وقناة النقاش الخاصة بها") }
                .onFailure { _events.emit("تعذر إضافة المادة") }
        }
    }

    fun addScheduleEntry(
        course: Course,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        location: String,
        instructorName: String,
        sessionType: String
    ) {
        viewModelScope.launch {
            val result = repository.addScheduleEntry(
                courseId = course.id,
                courseTitle = course.title,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                location = location,
                instructorName = instructorName.ifBlank { course.instructorName },
                sessionType = sessionType,
                academicYear = course.academicYear,
                term = course.term
            )
            result.onSuccess { _events.emit("تمت إضافة الموعد إلى الجدول") }
                .onFailure { _events.emit("تعذر إضافة الموعد") }
        }
    }

    fun deleteScheduleEntry(entryId: String) {
        viewModelScope.launch {
            repository.deleteScheduleEntry(entryId)
        }
    }

    fun addMaterial(
        course: Course,
        category: String,
        title: String,
        description: String,
        weekNumber: Int,
        fileFormat: String,
        fileSize: String,
        url: String,
        uploaderName: String
    ) {
        viewModelScope.launch {
            val result = repository.addMaterial(
                courseId = course.id,
                courseTitle = course.title,
                category = category,
                title = title,
                description = description,
                weekNumber = weekNumber,
                fileFormat = fileFormat,
                fileSize = fileSize,
                url = url,
                uploaderName = uploaderName
            )
            result.onSuccess { _events.emit("تم رفع الملف بنجاح") }
                .onFailure { _events.emit("تعذر رفع الملف — تحقق من الصلاحيات") }
        }
    }

    fun deleteMaterial(materialId: String) {
        viewModelScope.launch {
            repository.deleteMaterial(materialId)
        }
    }

    fun addAssignment(course: Course, title: String, description: String, dueAtMillis: Long) {
        viewModelScope.launch {
            val result = repository.addAssignment(course.id, course.title, title, description, dueAtMillis)
            result.onSuccess { _events.emit("تمت إضافة الواجب بنجاح") }
                .onFailure { _events.emit("تعذر إضافة الواجب") }
        }
    }

    fun toggleAssignmentDone(assignmentId: String, completed: Boolean, existing: AssignmentProgress?) {
        viewModelScope.launch {
            repository.setAssignmentCompleted(assignmentId, completed, existing)
                .onSuccess {
                    _events.emit(if (completed) "تم نقل الواجب إلى قائمة المنجزة" else "تمت إعادة الواجب إلى القائمة النشطة")
                }
                .onFailure { _events.emit("تعذر تحديث حالة الواجب") }
        }
    }

    fun submitAssignmentSolution(
        assignmentId: String,
        fileName: String,
        fileType: String,
        note: String,
        fileUri: String,
        existing: AssignmentProgress?
    ) {
        viewModelScope.launch {
            repository.submitAssignmentSolution(assignmentId, fileName, fileType, note, fileUri, existing)
                .onSuccess { _events.emit("تم تسليم الحل ووضع علامة الإنجاز بنجاح!") }
                .onFailure { _events.emit("تعذر تسليم الحل") }
        }
    }

    fun addQuiz(
        course: Course,
        title: String,
        description: String,
        durationMinutes: Int,
        questions: List<QuizQuestion>
    ) {
        viewModelScope.launch {
            val result = repository.addQuiz(course.id, course.title, title, description, durationMinutes, questions)
            result.onSuccess { _events.emit("تم نشر الاختبار التجريبي بنجاح") }
                .onFailure { _events.emit("تعذر نشر الاختبار") }
        }
    }

    fun saveQuizAttempt(quiz: Quiz, score: Int, durationSecondsUsed: Int, selectedAnswers: List<Int>) {
        viewModelScope.launch {
            repository.saveQuizAttempt(quiz, score, durationSecondsUsed, selectedAnswers)
                .onFailure { _events.emit("تعذر حفظ نتيجة الاختبار") }
        }
    }

    fun createProjectTeam(name: String, description: String, creatorName: String) {
        viewModelScope.launch {
            repository.createProjectTeam(name, description, creatorName)
                .onSuccess { _events.emit("تم إنشاء غرفة فريق العمل بنجاح") }
                .onFailure { _events.emit("تعذر إنشاء غرفة المشروع") }
        }
    }

    fun addMemberToTeam(channel: ChatChannel, memberUid: String) {
        viewModelScope.launch {
            repository.addMemberToTeam(channel.id, memberUid, channel.memberUids)
                .onSuccess { _events.emit("تمت إضافة العضو للفريق") }
                .onFailure { _events.emit("تعذر إضافة العضو") }
        }
    }

    fun sendMessage(
        channelId: String,
        senderName: String,
        senderRole: String,
        text: String,
        attachmentName: String? = null,
        attachmentUrl: String? = null
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendChatMessage(channelId, senderName, senderRole, text, attachmentName, attachmentUrl)
                .onFailure { _events.emit("تعذر إرسال الرسالة") }
        }
    }

    // --- STUDY & LEADERBOARD ACTIONS ---

    fun recordStudySession(
        profile: UserProfile,
        mode: String,
        courseTitle: String,
        durationSeconds: Long,
        pointsEarned: Int,
        isPomodoroCompletion: Boolean
    ) {
        if (durationSeconds < 1L) return
        viewModelScope.launch {
            repository.recordStudySessionAndLeaderboard(
                profile = profile,
                mode = mode,
                courseTitle = courseTitle,
                durationSeconds = durationSeconds,
                pointsEarned = pointsEarned,
                isPomodoroCompletion = isPomodoroCompletion
            ).onSuccess {
                _events.emit("تم تسجيل الجلسة (+$pointsEarned نقطة تركيز)")
            }.onFailure {
                _events.emit("تعذر حفظ جلسة المذاكرة")
            }
        }
    }

    // --- MINI MEETING ROOMS ACTIONS ---

    fun createMeetingRoom(
        title: String,
        topic: String,
        courseTitle: String,
        hostName: String,
        meetingUrl: String = ""
    ) {
        viewModelScope.launch {
            repository.createMeetingRoom(title, topic, courseTitle, hostName, meetingUrl)
                .onSuccess { roomId ->
                    _selectedMeetingRoomId.value = roomId
                    _events.emit("تم فتح غرفة الاجتماع المصغرة")
                }
                .onFailure { _events.emit("تعذر إنشاء غرفة الاجتماع") }
        }
    }

    fun joinOrLeaveMeetingRoom(room: MeetingRoom, userName: String, join: Boolean) {
        viewModelScope.launch {
            repository.joinOrLeaveMeetingRoom(room, userName, join)
                .onFailure { _events.emit("تعذر تحديث الحضور في الغرفة") }
        }
    }

    fun toggleRaiseHandInMeeting(room: MeetingRoom) {
        viewModelScope.launch {
            repository.toggleRaiseHandInMeeting(room)
        }
    }

    fun updateMeetingAgenda(roomId: String, agendaNotes: String) {
        viewModelScope.launch {
            repository.updateMeetingAgenda(roomId, agendaNotes)
                .onSuccess { _events.emit("تم تحديث ملاحظات وأجندة الاجتماع") }
                .onFailure { _events.emit("تعذر حفظ الأجندة") }
        }
    }

    fun endMeetingRoom(roomId: String) {
        viewModelScope.launch {
            repository.endMeetingRoom(roomId)
                .onSuccess { _events.emit("تم إنهاء الاجتماع") }
                .onFailure { _events.emit("تعذر إنهاء الاجتماع") }
        }
    }

    fun sendMeetingMessage(roomId: String, senderName: String, senderRole: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMeetingMessage(roomId, senderName, senderRole, text)
                .onFailure { _events.emit("تعذر إرسال الرسالة في الغرفة") }
        }
    }

    // --- DYNAMIC APP BUILDER ACTIONS ---

    fun saveDynamicModule(
        existingId: String?,
        title: String,
        subtitle: String,
        moduleType: String,
        iconKey: String,
        accentHex: String,
        actionType: String,
        actionPayload: String,
        sortOrder: Int,
        enabled: Boolean,
        creatorName: String
    ) {
        viewModelScope.launch {
            repository.saveDynamicModule(
                existingId = existingId,
                title = title,
                subtitle = subtitle,
                moduleType = moduleType,
                iconKey = iconKey,
                accentHex = accentHex,
                actionType = actionType,
                actionPayload = actionPayload,
                sortOrder = sortOrder,
                enabled = enabled,
                creatorName = creatorName
            ).onSuccess {
                _events.emit("تم حفظ وتحديث الوحدة الديناميكية فوراً لجميع الطلاب")
            }.onFailure {
                _events.emit("تعذر حفظ الوحدة الديناميكية")
            }
        }
    }

    fun deleteDynamicModule(moduleId: String) {
        viewModelScope.launch {
            repository.deleteDynamicModule(moduleId)
                .onSuccess { _events.emit("تم حذف الوحدة الديناميكية") }
        }
    }

    fun seedStarterData(userName: String) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = repository.seedStarterAcademicDataIfEmpty(userName)
            _isActionBusy.value = false
            result.onSuccess { seeded ->
                if (seeded) _events.emit("تمت تهيئة المواد والجداول والاختبارات الأولية بنجاح!")
                else _events.emit("البيانات الأكاديمية موجودة بالفعل")
            }.onFailure {
                _events.emit("تعذر تهيئة البيانات")
            }
        }
    }

    // --- FLASHCARDS ACTIONS ---

    fun createFlashcard(
        deckName: String,
        frontText: String,
        backText: String,
        courseId: String = "",
        courseTitle: String = ""
    ) {
        viewModelScope.launch {
            repository.createFlashcard(deckName, frontText, backText, courseId, courseTitle)
                .onSuccess { _events.emit("تمت إضافة البطاقة إلى مجموعة $deckName") }
                .onFailure { _events.emit("تعذر إضافة البطاقة") }
        }
    }

    fun reviewFlashcard(card: Flashcard, rating: String) {
        viewModelScope.launch {
            repository.reviewFlashcard(card, rating)
                .onFailure { _events.emit("تعذر حفظ تقييم البطاقة") }
        }
    }

    fun deleteFlashcard(cardId: String) {
        viewModelScope.launch {
            repository.deleteFlashcard(cardId)
        }
    }

    fun seedStarterFlashcards() {
        viewModelScope.launch {
            repository.seedStarterFlashcards()
                .onSuccess { count ->
                    if (count > 0) _events.emit("تم استيراد $count بطاقات تعليمية جاهزة!")
                    else _events.emit("البطاقات التمهيدية مضافة مسبقاً")
                }
                .onFailure { _events.emit("تعذر استيراد البطاقات") }
        }
    }

    // --- PERSONAL TO-DO TASKS ACTIONS ---

    fun addPersonalTask(
        title: String,
        notes: String,
        category: String,
        priority: String,
        dueAtMillis: Long,
        courseTitle: String = ""
    ) {
        viewModelScope.launch {
            repository.addPersonalTask(title, notes, category, priority, dueAtMillis, courseTitle)
                .onSuccess { _events.emit("تمت إضافة المهمة اليومية") }
                .onFailure { _events.emit("تعذر إضافة المهمة") }
        }
    }

    fun togglePersonalTask(task: PersonalTask, completed: Boolean, currentCompletedCount: Int) {
        viewModelScope.launch {
            repository.togglePersonalTask(task, completed, currentCompletedCount)
                .onFailure { _events.emit("تعذر تحديث حالة المهمة") }
        }
    }

    fun deletePersonalTask(taskId: String) {
        viewModelScope.launch {
            repository.deletePersonalTask(taskId)
        }
    }

    // --- GLOBAL REFERENCES ACTIONS ---

    fun addGlobalReference(
        title: String,
        provider: String,
        subject: String,
        description: String,
        url: String,
        languageCode: String
    ) {
        viewModelScope.launch {
            repository.addGlobalReference(title, provider, subject, description, url, languageCode)
                .onSuccess { _events.emit("تمت إضافة المرجع العالمي للدفعة") }
                .onFailure { _events.emit("تعذر إضافة المرجع") }
        }
    }

    fun deleteGlobalReference(refId: String) {
        viewModelScope.launch {
            repository.deleteGlobalReference(refId)
        }
    }

    private companion object {
        const val TAG = "BatchViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
