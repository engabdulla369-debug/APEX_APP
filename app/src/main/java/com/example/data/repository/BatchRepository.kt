package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Announcement
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
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.utils.AnalyticsCalculator
import com.example.utils.SpacedRepetitionEngine
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class BatchRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun handleFlowError(error: Throwable, operationType: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- USER PROFILE & MEMBERSHIP ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> handleFlowError(error, OperationType.GET, path, userId) }
        )
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> = runCatching {
        val path = "users/$userId"
        try {
            val snap = db.collection("users").document(userId).get().await()
            if (!snap.exists()) null
            else snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    suspend fun registerUserProfile(
        displayName: String,
        email: String,
        studentRecordNumber: String,
        department: String,
        academicYear: Int,
        term: Int,
        joinCodeInput: String
    ): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val isBootstrappedAdmin = email.equals(BatchConstants.BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
        val trimmedCode = joinCodeInput.trim()
        val hasCode = trimmedCode.isNotEmpty()
        val role = if (isBootstrappedAdmin) "dean" else "student"
        val approvalStatus = if (isBootstrappedAdmin || hasCode) "approved" else "pending"
        val batchId = if (approvalStatus == "approved") BatchConstants.DEFAULT_BATCH_ID else ""

        val profilePayload = mutableMapOf<String, Any>(
            "userId" to uid,
            "displayName" to displayName.trim(),
            "email" to email.trim(),
            "studentRecordNumber" to studentRecordNumber.trim(),
            "department" to department.trim(),
            "academicYear" to academicYear,
            "term" to term,
            "role" to role,
            "approvalStatus" to approvalStatus,
            "batchId" to batchId,
            "assignedCourseIds" to emptyList<String>(),
            "enrolledCourseIds" to emptyList<String>(),
            "darkMode" to false,
            "notifyLectures" to true,
            "notifyAssignments" to true,
            "focusPoints" to 0,
            "totalStudySeconds" to 0L,
            "pomodoroSessionsCompleted" to 0,
            "longestSessionSeconds" to 0L,
            "preferredLanguage" to "ar",
            "currentStreakDays" to 0,
            "longestStreakDays" to 0,
            "lastStudyDayKey" to "",
            "completedPersonalTasksCount" to 0,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!isBootstrappedAdmin && hasCode) {
            profilePayload["joinCode"] = trimmedCode
        }

        val userRef = db.collection("users").document(uid)
        val requestRef = db.collection("join_requests").document(uid)
        val requestPayload = mapOf(
            "userId" to uid,
            "displayName" to displayName.trim(),
            "studentRecordNumber" to studentRecordNumber.trim(),
            "department" to department.trim(),
            "academicYear" to academicYear,
            "role" to role,
            "approvalStatus" to approvalStatus,
            "batchId" to if (approvalStatus == "approved") BatchConstants.DEFAULT_BATCH_ID else "pending_queue",
            "assignedCourseIds" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val existingUserSnap = userRef.get().await()
            if (!existingUserSnap.exists()) {
                userRef.set(profilePayload).await()
            } else {
                val existingStatus = existingUserSnap.getString("approvalStatus") ?: "pending"
                if (existingStatus == "pending" && hasCode) {
                    userRef.update(
                        mapOf(
                            "approvalStatus" to "approved",
                            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                            "joinCode" to trimmedCode,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val existingReqSnap = requestRef.get().await()
            if (!existingReqSnap.exists()) {
                requestRef.set(requestPayload).await()
            }

            userRef.get().await().toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = displayName.trim(),
                    email = email.trim(),
                    studentRecordNumber = studentRecordNumber.trim(),
                    department = department.trim(),
                    academicYear = academicYear,
                    term = term,
                    role = role,
                    approvalStatus = approvalStatus,
                    batchId = batchId
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, userRef.path)
            throw e
        }
    }

    suspend fun verifyJoinCodeAndActivate(joinCodeInput: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val trimmedCode = joinCodeInput.trim()
        val userRef = db.collection("users").document(uid)
        val reqRef = db.collection("join_requests").document(uid)
        try {
            userRef.update(
                mapOf(
                    "approvalStatus" to "approved",
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "joinCode" to trimmedCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            reqRef.update(
                mapOf(
                    "approvalStatus" to "approved",
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun updateUserSettings(
        displayName: String? = null,
        department: String? = null,
        academicYear: Int? = null,
        term: Int? = null,
        enrolledCourseIds: List<String>? = null,
        darkMode: Boolean? = null,
        notifyLectures: Boolean? = null,
        notifyAssignments: Boolean? = null,
        preferredLanguage: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        displayName?.let { updates["displayName"] = it.trim() }
        department?.let { updates["department"] = it.trim() }
        academicYear?.let { updates["academicYear"] = it }
        term?.let { updates["term"] = it }
        enrolledCourseIds?.let { updates["enrolledCourseIds"] = it }
        darkMode?.let { updates["darkMode"] = it }
        notifyLectures?.let { updates["notifyLectures"] = it }
        notifyAssignments?.let { updates["notifyAssignments"] = it }
        preferredLanguage?.let { updates["preferredLanguage"] = it }

        try {
            userRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    fun observeJoinRequests(): Flow<List<JoinRequest>> = flow {
        val uid = requireUserId()
        val path = "join_requests"
        val approvedFlow = db.collection(path)
            .whereEqualTo("batchId", BatchConstants.DEFAULT_BATCH_ID)
            .snapshots()
            .map { it.toObjects(JoinRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val pendingFlow = db.collection(path)
            .whereEqualTo("batchId", "pending_queue")
            .snapshots()
            .map { it.toObjects(JoinRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(pendingFlow, approvedFlow) { pending, approved ->
                (pending + approved)
                    .distinctBy { it.userId }
                    .sortedWith(compareBy<JoinRequest> { it.approvalStatus != "pending" }.thenBy { it.displayName })
            }.catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun updateMemberRoleAndApproval(
        targetUserId: String,
        role: String,
        approvalStatus: String,
        assignedCourseIds: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        val batchId = if (approvalStatus == "approved") BatchConstants.DEFAULT_BATCH_ID else "pending_queue"
        val userBatchId = if (approvalStatus == "approved") BatchConstants.DEFAULT_BATCH_ID else ""
        val userRef = db.collection("users").document(targetUserId)
        val reqRef = db.collection("join_requests").document(targetUserId)
        try {
            userRef.update(
                mapOf(
                    "role" to role,
                    "approvalStatus" to approvalStatus,
                    "batchId" to userBatchId,
                    "assignedCourseIds" to assignedCourseIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            reqRef.update(
                mapOf(
                    "role" to role,
                    "approvalStatus" to approvalStatus,
                    "batchId" to batchId,
                    "assignedCourseIds" to assignedCourseIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    // --- ANNOUNCEMENTS ---

    fun observeAnnouncements(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        val path = "announcements"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun addAnnouncement(
        title: String,
        body: String,
        priority: String,
        authorName: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("announcements").document()
        val payload = mapOf(
            "title" to title.trim(),
            "body" to body.trim(),
            "priority" to priority,
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "authorId" to uid,
            "authorName" to authorName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("announcements").document(announcementId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- COURSES ---

    fun observeCourses(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<Course>> = flow {
        val uid = requireUserId()
        val path = "courses"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Course::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<Course> { it.academicYear }.thenBy { it.term }.thenBy { it.title })
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun addCourse(
        title: String,
        code: String,
        academicYear: Int,
        term: Int,
        instructorName: String,
        department: String,
        accentHex: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("courses").document()
        val payload = mapOf(
            "title" to title.trim(),
            "code" to code.trim().uppercase(),
            "academicYear" to academicYear,
            "term" to term,
            "instructorName" to instructorName.trim(),
            "department" to department.trim(),
            "accentHex" to accentHex,
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            // Also create a corresponding course chat channel automatically
            val channelRef = db.collection("chat_channels").document("course_${docRef.id}")
            channelRef.set(
                mapOf(
                    "name" to "قناة ${title.trim()}",
                    "type" to "course",
                    "courseId" to docRef.id,
                    "description" to "نقاشات وأسئلة مادة ${title.trim()}",
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "memberUids" to emptyList<String>(),
                    "createdBy" to uid,
                    "createdByName" to "ممثل الدفعة",
                    "lastMessage" to "مرحباً بكم في قناة المادة",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- SCHEDULE ---

    fun observeScheduleEntries(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<ScheduleEntry>> = flow {
        val uid = requireUserId()
        val path = "schedule_entries"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ScheduleEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<ScheduleEntry> { it.dayOfWeek }.thenBy { it.startTime })
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun addScheduleEntry(
        courseId: String,
        courseTitle: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        location: String,
        instructorName: String,
        sessionType: String,
        academicYear: Int,
        term: Int
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("schedule_entries").document()
        val payload = mapOf(
            "courseId" to courseId,
            "courseTitle" to courseTitle,
            "dayOfWeek" to dayOfWeek,
            "startTime" to startTime.trim(),
            "endTime" to endTime.trim(),
            "location" to location.trim(),
            "instructorName" to instructorName.trim(),
            "sessionType" to sessionType,
            "academicYear" to academicYear,
            "term" to term,
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteScheduleEntry(entryId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("schedule_entries").document(entryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- MATERIALS ---

    fun observeMaterialsForCourse(
        courseId: String,
        batchId: String = BatchConstants.DEFAULT_BATCH_ID
    ): Flow<List<CourseMaterial>> = flow {
        val uid = requireUserId()
        val path = "materials"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .whereEqualTo("courseId", courseId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CourseMaterial::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<CourseMaterial> { it.weekNumber }.thenByDescending { it.createdAt ?: Timestamp(0, 0) })
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun addMaterial(
        courseId: String,
        courseTitle: String,
        category: String,
        title: String,
        description: String,
        weekNumber: Int,
        fileFormat: String,
        fileSize: String,
        url: String,
        uploaderName: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("materials").document()
        val payload = mapOf(
            "courseId" to courseId,
            "courseTitle" to courseTitle,
            "category" to category,
            "title" to title.trim(),
            "description" to description.trim(),
            "weekNumber" to weekNumber,
            "fileFormat" to fileFormat.trim().uppercase(),
            "fileSize" to fileSize.trim(),
            "url" to url.trim(),
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "uploaderId" to uid,
            "uploaderName" to uploaderName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMaterial(materialId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("materials").document(materialId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- ASSIGNMENTS & PROGRESS ---

    fun observeAssignments(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<Assignment>> = flow {
        val uid = requireUserId()
        val path = "assignments"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Assignment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.dueAtMillis }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeAssignmentProgress(userId: String): Flow<Map<String, AssignmentProgress>> = flow {
        val path = "users/$userId/assignment_progress"
        emitAll(
            db.collection("users").document(userId).collection("assignment_progress")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AssignmentProgress::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .associateBy { it.assignmentId }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun addAssignment(
        courseId: String,
        courseTitle: String,
        title: String,
        description: String,
        dueAtMillis: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("assignments").document()
        val payload = mapOf(
            "courseId" to courseId,
            "courseTitle" to courseTitle,
            "title" to title.trim(),
            "description" to description.trim(),
            "dueAtMillis" to dueAtMillis,
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun setAssignmentCompleted(
        assignmentId: String,
        completed: Boolean,
        existingProgress: AssignmentProgress?
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("assignment_progress").document(assignmentId)
        try {
            if (existingProgress == null || existingProgress.createdAt == null) {
                val payload = mapOf(
                    "userId" to uid,
                    "assignmentId" to assignmentId,
                    "completed" to completed,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun submitAssignmentSolution(
        assignmentId: String,
        fileName: String,
        fileType: String,
        note: String,
        fileUri: String,
        existingProgress: AssignmentProgress?
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("assignment_progress").document(assignmentId)
        val nowMillis = System.currentTimeMillis()
        try {
            if (existingProgress == null || existingProgress.createdAt == null) {
                val payload = mapOf(
                    "userId" to uid,
                    "assignmentId" to assignmentId,
                    "completed" to true,
                    "submissionFileName" to fileName.trim(),
                    "submissionFileType" to fileType.trim(),
                    "submissionNote" to note.trim(),
                    "submissionUri" to fileUri,
                    "submittedAtMillis" to nowMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "completed" to true,
                        "submissionFileName" to fileName.trim(),
                        "submissionFileType" to fileType.trim(),
                        "submissionNote" to note.trim(),
                        "submissionUri" to fileUri,
                        "submittedAtMillis" to nowMillis,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- QUIZZES & ATTEMPTS ---

    fun observeQuizzes(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<Quiz>> = flow {
        val uid = requireUserId()
        val path = "quizzes"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Quiz::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeQuizAttempts(userId: String): Flow<List<QuizAttempt>> = flow {
        val path = "users/$userId/quiz_attempts"
        emitAll(
            db.collection("users").document(userId).collection("quiz_attempts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(QuizAttempt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun addQuiz(
        courseId: String,
        courseTitle: String,
        title: String,
        description: String,
        durationMinutes: Int,
        questions: List<QuizQuestion>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("quizzes").document()
        val questionMaps = questions.map { q ->
            mapOf(
                "questionText" to q.questionText,
                "questionType" to q.questionType,
                "options" to q.options,
                "correctIndex" to q.correctIndex,
                "explanation" to q.explanation
            )
        }
        val payload = mapOf(
            "courseId" to courseId,
            "courseTitle" to courseTitle,
            "title" to title.trim(),
            "description" to description.trim(),
            "durationMinutes" to durationMinutes,
            "questions" to questionMaps,
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun saveQuizAttempt(
        quiz: Quiz,
        score: Int,
        durationSecondsUsed: Int,
        selectedAnswers: List<Int>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("quiz_attempts").document()
        val payload = mapOf(
            "userId" to uid,
            "quizId" to quiz.id,
            "quizTitle" to quiz.title,
            "courseTitle" to quiz.courseTitle,
            "score" to score,
            "totalQuestions" to quiz.questions.size,
            "durationSecondsUsed" to durationSecondsUsed,
            "selectedAnswers" to selectedAnswers,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- COMMUNITY CHAT & PROJECT TEAMS ---

    fun observeChatChannels(
        userId: String,
        batchId: String = BatchConstants.DEFAULT_BATCH_ID
    ): Flow<List<ChatChannel>> = flow {
        val path = "chat_channels"
        val publicFlow = db.collection(path)
            .whereEqualTo("batchId", batchId)
            .whereIn("type", listOf("general", "course"))
            .snapshots()
            .map { it.toObjects(ChatChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        val teamFlow = db.collection(path)
            .whereEqualTo("type", "team")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { it.toObjects(ChatChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(publicFlow, teamFlow) { pub, teams ->
                (pub + teams).distinctBy { it.id }
                    .sortedWith(
                        compareBy<ChatChannel> {
                            when (it.type) {
                                "general" -> 0
                                "course" -> 1
                                else -> 2
                            }
                        }.thenByDescending { it.updatedAt ?: Timestamp(0, 0) }
                    )
            }.catch { error -> handleFlowError(error, OperationType.LIST, path, userId) }
        )
    }

    fun observeMessages(
        channelId: String,
        batchId: String = BatchConstants.DEFAULT_BATCH_ID
    ): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "chat_channels/$channelId/messages"
        emitAll(
            db.collection("chat_channels").document(channelId).collection("messages")
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp.now() }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun createProjectTeam(
        name: String,
        description: String,
        creatorName: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("chat_channels").document()
        val payload = mapOf(
            "name" to name.trim(),
            "type" to "team",
            "courseId" to "",
            "description" to description.trim(),
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "memberUids" to listOf(uid),
            "createdBy" to uid,
            "createdByName" to creatorName,
            "lastMessage" to "تم إنشاء مجموعة المشروع",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun addMemberToTeam(channelId: String, memberUid: String, currentMembers: List<String>): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("chat_channels").document(channelId)
        val updated = (currentMembers + memberUid.trim()).distinct().take(25)
        try {
            docRef.update(
                mapOf(
                    "memberUids" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun sendChatMessage(
        channelId: String,
        senderName: String,
        senderRole: String,
        text: String,
        attachmentName: String? = null,
        attachmentUrl: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val msgRef = db.collection("chat_channels").document(channelId).collection("messages").document()
        val channelRef = db.collection("chat_channels").document(channelId)
        val payload = mutableMapOf<String, Any>(
            "channelId" to channelId,
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "senderId" to uid,
            "senderName" to senderName,
            "senderRole" to senderRole,
            "text" to text.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!attachmentName.isNullOrBlank()) payload["attachmentName"] = attachmentName.trim()
        if (!attachmentUrl.isNullOrBlank()) payload["attachmentUrl"] = attachmentUrl.trim()

        try {
            msgRef.set(payload).await()
            channelRef.update(
                mapOf(
                    "lastMessage" to "${senderName}: ${text.trim().take(80)}",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    // --- STARTER CURRICULUM SEEDING (BATCH REP / ADMIN ONLY) ---

    suspend fun seedStarterAcademicDataIfEmpty(userName: String): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val existingCourses = db.collection("courses")
            .whereEqualTo("batchId", BatchConstants.DEFAULT_BATCH_ID)
            .limit(1)
            .get()
            .await()

        if (!existingCourses.isEmpty) {
            return@runCatching false
        }

        // Batch config
        db.collection("batch_config").document("main").set(
            mapOf(
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "batchName" to BatchConstants.DEFAULT_BATCH_NAME,
                "joinCode" to BatchConstants.DEFAULT_JOIN_CODE,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        val mathId = "course_math_201"
        val mechId = "course_mech_202"
        val drawId = "course_draw_103"
        val chemId = "course_chem_104"

        val starterCourses = listOf(
            Triple(mathId, "الرياضيات الهندسية", mapOf(
                "title" to "الرياضيات الهندسية",
                "code" to "MATH 201",
                "academicYear" to 2,
                "term" to 1,
                "instructorName" to "د. أحمد شوقي",
                "department" to "أساسية",
                "accentHex" to "#1E3A8A"
            )),
            Triple(mechId, "الميكانيكا الهندسية", mapOf(
                "title" to "الميكانيكا الهندسية",
                "code" to "MECH 202",
                "academicYear" to 2,
                "term" to 1,
                "instructorName" to "د. مصطفى كمال",
                "department" to "ميكانيكا",
                "accentHex" to "#0D9488"
            )),
            Triple(drawId, "الرسم الهندسي", mapOf(
                "title" to "الرسم الهندسي",
                "code" to "DRAW 103",
                "academicYear" to 1,
                "term" to 1,
                "instructorName" to "د. سلمى إبراهيم",
                "department" to "مدني",
                "accentHex" to "#D97706"
            )),
            Triple(chemId, "الكيمياء العامة", mapOf(
                "title" to "الكيمياء العامة",
                "code" to "CHEM 104",
                "academicYear" to 1,
                "term" to 2,
                "instructorName" to "د. طارق المنصور",
                "department" to "كيمياء",
                "accentHex" to "#7C3AED"
            ))
        )

        starterCourses.forEach { (id, title, fields) ->
            db.collection("courses").document(id).set(
                fields + mapOf(
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("chat_channels").document("channel_$id").set(
                mapOf(
                    "name" to "قناة $title",
                    "type" to "course",
                    "courseId" to id,
                    "description" to "نقاشات وأسئلة طلاب $title",
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "memberUids" to emptyList<String>(),
                    "createdBy" to uid,
                    "createdByName" to userName,
                    "lastMessage" to "اسأل هنا عن المحاضرات والشيتات",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // General batch chat channel
        db.collection("chat_channels").document("channel_batch_general").set(
            mapOf(
                "name" to "شات الدفعة العام",
                "type" to "general",
                "courseId" to "",
                "description" to "غرفة التواصل الرئيسية لجميع طلاب الدفعة",
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "memberUids" to emptyList<String>(),
                "createdBy" to uid,
                "createdByName" to userName,
                "lastMessage" to "أهلاً بكم في منصة الدفعة المنظمة!",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Announcements
        db.collection("announcements").document("ann_1").set(
            mapOf(
                "title" to "تأجيل محاضرة الميكانيكا اليوم إلى الساعة 12:30 ظهراً في مدرج ب",
                "body" to "تم التنسيق مع د. مصطفى كمال لتأجيل المحاضرة ساعة واحدة نظراً لصيانة القاعة.",
                "priority" to "urgent",
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "authorId" to uid,
                "authorName" to userName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("announcements").document("ann_2").set(
            mapOf(
                "title" to "موعد تسليم شيت الرياضيات الهندسية الأول خلال 48 ساعة",
                "body" to "يرجى رفع الحل بصيغة PDF أو صورة واضحة من قسم الواجبات قبل الموعد النهائي.",
                "priority" to "urgent",
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "authorId" to uid,
                "authorName" to userName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Schedule entries for all week days so Today always shows sessions
        for (day in 1..7) {
            db.collection("schedule_entries").document("sched_math_$day").set(
                mapOf(
                    "courseId" to mathId,
                    "courseTitle" to "الرياضيات الهندسية",
                    "dayOfWeek" to day,
                    "startTime" to "09:00 ص",
                    "endTime" to "10:45 ص",
                    "location" to "مدرج أ - الدور الثاني",
                    "instructorName" to "د. أحمد شوقي",
                    "sessionType" to if (day % 2 == 0) "section" else "lecture",
                    "academicYear" to 2,
                    "term" to 1,
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("schedule_entries").document("sched_mech_$day").set(
                mapOf(
                    "courseId" to mechId,
                    "courseTitle" to "الميكانيكا الهندسية",
                    "dayOfWeek" to day,
                    "startTime" to "12:30 م",
                    "endTime" to "02:00 م",
                    "location" to "معمل الميكانيكا 3",
                    "instructorName" to "م. طارق سعد",
                    "sessionType" to if (day % 2 == 0) "lecture" else "lab",
                    "academicYear" to 2,
                    "term" to 1,
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // Materials across all 4 categories
        val starterMaterials = listOf(
            mapOf(
                "id" to "mat_math_lec1",
                "courseId" to mathId,
                "courseTitle" to "الرياضيات الهندسية",
                "category" to "lectures",
                "title" to "المحاضرة 1: المعادلات التفاضلية من الرتبة الأولى",
                "description" to "شرح كامل لطرق فصل المتغيرات والمعادلات المتجانسة مع أمثلة محلولة.",
                "weekNumber" to 1,
                "fileFormat" to "PDF",
                "fileSize" to "2.4 MB",
                "url" to "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
            ),
            mapOf(
                "id" to "mat_math_sum1",
                "courseId" to mathId,
                "courseTitle" to "الرياضيات الهندسية",
                "category" to "summaries",
                "title" to "شيت 1 + ملخص القوانين المختصرة",
                "description" to "ملخص منسق من زمايلكم يجمع قوانين التكامل والتمارين المطلوبة.",
                "weekNumber" to 1,
                "fileFormat" to "PDF",
                "fileSize" to "1.1 MB",
                "url" to "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
            ),
            mapOf(
                "id" to "mat_math_ref1",
                "courseId" to mathId,
                "courseTitle" to "الرياضيات الهندسية",
                "category" to "references",
                "title" to "مرجع Erwin Kreyszig للرياضيات الهندسية المتقدمة",
                "description" to "الفصول 1 إلى 4 مع جداول التحويلات الأساسية.",
                "weekNumber" to 1,
                "fileFormat" to "PDF",
                "fileSize" to "8.5 MB",
                "url" to "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
            ),
            mapOf(
                "id" to "mat_math_slides1",
                "courseId" to mathId,
                "courseTitle" to "الرياضيات الهندسية",
                "category" to "lectures",
                "title" to "عرض تقديمي (PowerPoint): طرق الحل والتحويلات",
                "description" to "شرائح عرض تقديمية تفاعلية للمحاضرة الثانية جاهزة للمعاينة والتحميل.",
                "weekNumber" to 2,
                "fileFormat" to "PPTX",
                "fileSize" to "1.8 MB",
                "url" to "https://scholar.harvard.edu/files/torman_personal/files/samplepptx.pptx"
            ),
            mapOf(
                "id" to "mat_math_link1",
                "courseId" to mathId,
                "courseTitle" to "الرياضيات الهندسية",
                "category" to "links",
                "title" to "فيديو شرح تطبيقي مدمج — المعادلات الهندسية",
                "description" to "مشاهدة مباشرة داخل مشغل الفيديو المدمج في التطبيق.",
                "weekNumber" to 1,
                "fileFormat" to "VIDEO",
                "fileSize" to "MP4 • بث مباشر",
                "url" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
            ),
            mapOf(
                "id" to "mat_mech_lec1",
                "courseId" to mechId,
                "courseTitle" to "الميكانيكا الهندسية",
                "category" to "lectures",
                "title" to "المحاضرة 1: تحليل القوى والعزوم في الفراغ",
                "description" to "مقدمة الاتزان الإستاتيكي وتمثيل المتجهات الثلاثية.",
                "weekNumber" to 1,
                "fileFormat" to "PDF",
                "fileSize" to "3.0 MB",
                "url" to "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
            )
        )

        starterMaterials.forEach { m ->
            val docId = m["id"] as String
            val data = m.filterKeys { it != "id" } + mapOf(
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "uploaderId" to uid,
                "uploaderName" to userName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("materials").document(docId).set(data).await()
        }

        // Assignments
        val now = System.currentTimeMillis()
        db.collection("assignments").document("assign_math_1").set(
            mapOf(
                "courseId" to mathId,
                "courseTitle" to "الرياضيات الهندسية",
                "title" to "شيت 1: المعادلات التفاضلية",
                "description" to "حل المسائل من 1 إلى 10 بخط واضح ورفع الحل بصيغة PDF أو صورة.",
                "dueAtMillis" to now + 38L * 3600L * 1000L,
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("assignments").document("assign_mech_1").set(
            mapOf(
                "courseId" to mechId,
                "courseTitle" to "الميكانيكا الهندسية",
                "title" to "تكليف تحليل الجملونات (Trusses)",
                "description" to "تطبيق طريقة العقد وطريقة القطع على المسألتين بالملف المرفق.",
                "dueAtMillis" to now + 96L * 3600L * 1000L,
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Sample Quizzes
        db.collection("quizzes").document("quiz_math_1").set(
            mapOf(
                "courseId" to mathId,
                "courseTitle" to "الرياضيات الهندسية",
                "title" to "اختبار تجريبي 1: أساسيات المعادلات التفاضلية",
                "description" to "اختبار سريع من 4 أسئلة لقياس الاستيعاب قبل الكويز الرسمي.",
                "durationMinutes" to 5,
                "questions" to listOf(
                    mapOf(
                        "questionText" to "ما رتبة المعادلة التفاضلية: y'' + 3y' - 2y = 0 ؟",
                        "questionType" to "mcq",
                        "options" to listOf("الرتبة الأولى", "الرتبة الثانية", "الرتبة الثالثة", "رتبة صفرية"),
                        "correctIndex" to 1,
                        "explanation" to "رتبة المعادلة التفاضلية تتحدد بأعلى مشتقة موجودة فيها، وهي هنا المشتقة الثانية y''."
                    ),
                    mapOf(
                        "questionText" to "يمكن حل المعادلة dy/dx = x·y بطريقة فصل المتغيرات مباشرة.",
                        "questionType" to "true_false",
                        "options" to listOf("صح", "خطأ"),
                        "correctIndex" to 0,
                        "explanation" to "نعم، بقسمة الطرفين على y وضربهما في dx نحصل على dy/y = x dx وهي مفصولة المتغيرات."
                    ),
                    mapOf(
                        "questionText" to "ما قيمة التكامل ∫ (1/x) dx (لـ x > 0) ؟",
                        "questionType" to "mcq",
                        "options" to listOf("x² / 2 + C", "ln(x) + C", "e^x + C", "-1/x² + C"),
                        "correctIndex" to 1,
                        "explanation" to "مشتقة اللوغاريتم الطبيعي ln(x) هي 1/x، وبالتالي تكامل 1/x هو ln(x) + C."
                    ),
                    mapOf(
                        "questionText" to "شرط كون المعادلة M dx + N dy = 0 تامة (Exact) هو ∂M/∂y = ∂N/∂x.",
                        "questionType" to "true_false",
                        "options" to listOf("صح", "خطأ"),
                        "correctIndex" to 0,
                        "explanation" to "تكون المعادلة تامة إذا تساوى المشتق الجزئي لـ M بالنسبة لـ y مع المشتق الجزئي لـ N بالنسبة لـ x."
                    )
                ),
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("quizzes").document("quiz_mech_1").set(
            mapOf(
                "courseId" to mechId,
                "courseTitle" to "الميكانيكا الهندسية",
                "title" to "اختبار تجريبي: اتزان القوى في المستوى",
                "description" to "تدريب سريع على شروط الاتزان وحساب العزوم.",
                "durationMinutes" to 5,
                "questions" to listOf(
                    mapOf(
                        "questionText" to "عدد معادلات الاتزان المستقلة لجسم متماسك في المستوى (2D) هو:",
                        "questionType" to "mcq",
                        "options" to listOf("معادلتان", "3 معادلات", "4 معادلات", "6 معادلات"),
                        "correctIndex" to 1,
                        "explanation" to "في المستوى لدينا ثلاث معادلات: ΣFx = 0 ، ΣFy = 0 ، و ΣM = 0."
                    ),
                    mapOf(
                        "questionText" to "عزم قوة تمر مباشرة بنقطة الدوران يساوي صفراً.",
                        "questionType" to "true_false",
                        "options" to listOf("صح", "خطأ"),
                        "correctIndex" to 0,
                        "explanation" to "ذراع العزم العمودي يساوي صفراً عندما يمر خط عمل القوة بنقطة الدوران، لذا العزم = 0."
                    )
                ),
                "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Seed starter Dynamic Modules & Meeting Room & Global References
        seedDynamicAndMeetingStarters(uid, userName)
        seedGlobalReferencesIfEmpty(uid)

        true
    }

    suspend fun seedDynamicAndMeetingStarters(uid: String, userName: String) {
        val dynamicStarter = listOf(
            mapOf(
                "id" to "mod_study_sprint",
                "title" to "سباق التركيز الأسبوعي",
                "subtitle" to "شغّل البومودورو أو وضع عدم التشتيت واكسب نقاط الشرف",
                "moduleType" to "quick_tool",
                "iconKey" to "study",
                "accentHex" to "#0D9488",
                "actionType" to "navigate_study",
                "actionPayload" to "",
                "sortOrder" to 1,
                "enabled" to true
            ),
            mapOf(
                "id" to "mod_sci_calc",
                "title" to "الآلة الحاسبة العلمية",
                "subtitle" to "حسابات هندسية ومعادلات مثلثية فورية أثناء المذاكرة",
                "moduleType" to "quick_tool",
                "iconKey" to "calculator",
                "accentHex" to "#1E3A8A",
                "actionType" to "open_calculator",
                "actionPayload" to "",
                "sortOrder" to 2,
                "enabled" to true
            ),
            mapOf(
                "id" to "mod_study_room",
                "title" to "غرف المذاكرة التفاعلية",
                "subtitle" to "انضم لزملائك في غرف النقاش والمراجعة المصغرة",
                "moduleType" to "home_card",
                "iconKey" to "meeting",
                "accentHex" to "#7C3AED",
                "actionType" to "open_meetings",
                "actionPayload" to "",
                "sortOrder" to 3,
                "enabled" to true
            )
        )
        dynamicStarter.forEach { mod ->
            val id = mod["id"] as String
            val ref = db.collection("dynamic_modules").document(id)
            if (!ref.get().await().exists()) {
                ref.set(
                    mod.filterKeys { it != "id" } + mapOf(
                        "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                        "createdBy" to uid,
                        "createdByName" to userName.ifBlank { "إدارة المنصة" },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        val roomRef = db.collection("meeting_rooms").document("room_math_review")
        if (!roomRef.get().await().exists()) {
            roomRef.set(
                mapOf(
                    "title" to "غرفة مراجعة الرياضيات الهندسية",
                    "topic" to "حل مسائل المعادلات التفاضلية وتبادل الملخصات قبل الكويز",
                    "courseTitle" to "الرياضيات الهندسية",
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "hostId" to uid,
                    "hostName" to userName.ifBlank { "منسق الدفعة" },
                    "status" to "active",
                    "participantUids" to listOf(uid),
                    "participantNames" to listOf(userName.ifBlank { "منسق الدفعة" }),
                    "raisedHandUids" to emptyList<String>(),
                    "agendaNotes" to "1. مراجعة فصل المتغيرات\n2. حل السؤال 4 و 7 من الشيت\n3. أسئلة مفتوحة",
                    "meetingUrl" to "",
                    "startedAtMillis" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    // --- STUDY SESSIONS & LEADERBOARD ---

    fun observeStudySessions(userId: String): Flow<List<StudySession>> = flow {
        val path = "users/$userId/study_sessions"
        emitAll(
            db.collection("users").document(userId).collection("study_sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StudySession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.completedAtMillis }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, userId) }
        )
    }

    fun observeLeaderboard(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<LeaderboardEntry>> = flow {
        val uid = requireUserId()
        val path = "study_leaderboard"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LeaderboardEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<LeaderboardEntry> { it.focusPoints }
                                .thenByDescending { it.totalStudySeconds }
                        )
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun recordStudySessionAndLeaderboard(
        profile: UserProfile,
        mode: String,
        courseTitle: String,
        durationSeconds: Long,
        pointsEarned: Int,
        isPomodoroCompletion: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeDuration = durationSeconds.coerceIn(1L, 86_400L)
        val safePoints = pointsEarned.coerceIn(0, 5_000)

        val sessionRef = db.collection("users").document(uid).collection("study_sessions").document()
        val userRef = db.collection("users").document(uid)
        val leaderboardRef = db.collection("study_leaderboard").document(uid)

        val newFocusPoints = (profile.focusPoints + safePoints).coerceAtMost(10_000_000)
        val newTotalSeconds = (profile.totalStudySeconds + safeDuration).coerceAtMost(100_000_000L)
        val newPomodoros = profile.pomodoroSessionsCompleted + (if (isPomodoroCompletion) 1 else 0)
        val newLongest = maxOf(profile.longestSessionSeconds, safeDuration).coerceAtMost(86_400L)

        val todayKey = AnalyticsCalculator.dayKey(nowMillis)
        val yesterdayKey = AnalyticsCalculator.dayKey(nowMillis - 24L * 3600L * 1000L)
        val nextStreak = when (profile.lastStudyDayKey) {
            todayKey -> profile.currentStreakDays.coerceAtLeast(1)
            yesterdayKey -> (profile.currentStreakDays + 1).coerceAtLeast(1)
            else -> 1
        }.coerceIn(1, 3650)
        val bestStreak = maxOf(profile.longestStreakDays, nextStreak).coerceIn(1, 3650)

        try {
            val sessionPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "mode" to mode,
                "durationSeconds" to safeDuration,
                "pointsEarned" to safePoints,
                "completedAtMillis" to nowMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (courseTitle.isNotBlank()) {
                sessionPayload["courseTitle"] = courseTitle.trim().take(120)
            }
            sessionRef.set(sessionPayload).await()

            userRef.update(
                mapOf(
                    "focusPoints" to newFocusPoints,
                    "totalStudySeconds" to newTotalSeconds,
                    "pomodoroSessionsCompleted" to newPomodoros,
                    "longestSessionSeconds" to newLongest,
                    "currentStreakDays" to nextStreak,
                    "longestStreakDays" to bestStreak,
                    "lastStudyDayKey" to todayKey,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val lbSnap = leaderboardRef.get().await()
            if (!lbSnap.exists()) {
                leaderboardRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to profile.displayName.ifBlank { "طالب" },
                        "department" to profile.department.ifBlank { "الهندسة" },
                        "academicYear" to profile.academicYear.coerceIn(1, 5),
                        "role" to profile.role,
                        "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                        "focusPoints" to newFocusPoints,
                        "totalStudySeconds" to newTotalSeconds,
                        "pomodoroSessionsCompleted" to newPomodoros,
                        "longestSessionSeconds" to newLongest,
                        "currentStreakDays" to nextStreak,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                leaderboardRef.update(
                    mapOf(
                        "displayName" to profile.displayName.ifBlank { "طالب" },
                        "department" to profile.department.ifBlank { "الهندسة" },
                        "academicYear" to profile.academicYear.coerceIn(1, 5),
                        "role" to profile.role,
                        "focusPoints" to newFocusPoints,
                        "totalStudySeconds" to newTotalSeconds,
                        "pomodoroSessionsCompleted" to newPomodoros,
                        "longestSessionSeconds" to newLongest,
                        "currentStreakDays" to nextStreak,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, sessionRef.path)
            throw e
        }
    }

    // --- MINI MEETING ROOMS ---

    fun observeMeetingRooms(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<MeetingRoom>> = flow {
        val uid = requireUserId()
        val path = "meeting_rooms"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MeetingRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<MeetingRoom> { it.status != "active" }
                                .thenByDescending { it.startedAtMillis }
                        )
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeMeetingMessages(roomId: String, batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<MeetingMessage>> = flow {
        val uid = requireUserId()
        val path = "meeting_rooms/$roomId/messages"
        emitAll(
            db.collection("meeting_rooms").document(roomId).collection("messages")
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MeetingMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp.now() }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun createMeetingRoom(
        title: String,
        topic: String,
        courseTitle: String,
        hostName: String,
        meetingUrl: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("meeting_rooms").document()
        val payload = mutableMapOf<String, Any>(
            "title" to title.trim(),
            "topic" to topic.trim(),
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "hostId" to uid,
            "hostName" to hostName.ifBlank { "طالب" },
            "status" to "active",
            "participantUids" to listOf(uid),
            "participantNames" to listOf(hostName.ifBlank { "طالب" }),
            "raisedHandUids" to emptyList<String>(),
            "agendaNotes" to "جدول أعمال الاجتماع:\n• ",
            "startedAtMillis" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (courseTitle.isNotBlank()) payload["courseTitle"] = courseTitle.trim()
        if (meetingUrl.isNotBlank()) payload["meetingUrl"] = meetingUrl.trim()

        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun joinOrLeaveMeetingRoom(
        room: MeetingRoom,
        userName: String,
        join: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("meeting_rooms").document(room.id)
        val pairs = room.participantUids.zip(room.participantNames).toMutableList()
        pairs.removeAll { it.first == uid }
        if (join) {
            pairs.add(uid to userName.ifBlank { "طالب" })
        }
        val clipped = pairs.take(50)
        val raised = if (join) room.raisedHandUids else room.raisedHandUids - uid
        try {
            docRef.update(
                mapOf(
                    "participantUids" to clipped.map { it.first },
                    "participantNames" to clipped.map { it.second },
                    "raisedHandUids" to raised,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleRaiseHandInMeeting(room: MeetingRoom): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("meeting_rooms").document(room.id)
        val updated = if (room.raisedHandUids.contains(uid)) {
            room.raisedHandUids - uid
        } else {
            (room.raisedHandUids + uid).take(50)
        }
        try {
            docRef.update(
                mapOf(
                    "raisedHandUids" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateMeetingAgenda(roomId: String, agendaNotes: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("meeting_rooms").document(roomId)
        try {
            docRef.update(
                mapOf(
                    "agendaNotes" to agendaNotes.take(3000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun endMeetingRoom(roomId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("meeting_rooms").document(roomId)
        try {
            docRef.update(
                mapOf(
                    "status" to "ended",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun sendMeetingMessage(
        roomId: String,
        senderName: String,
        senderRole: String,
        text: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val msgRef = db.collection("meeting_rooms").document(roomId).collection("messages").document()
        val payload = mapOf(
            "channelId" to roomId,
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "senderId" to uid,
            "senderName" to senderName.ifBlank { "طالب" },
            "senderRole" to senderRole,
            "text" to text.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            msgRef.set(payload).await()
            msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    // --- DYNAMIC IN-APP APP BUILDER ---

    fun observeDynamicModules(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<DynamicModule>> = flow {
        val uid = requireUserId()
        val path = "dynamic_modules"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DynamicModule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.sortOrder }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun saveDynamicModule(
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
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = if (existingId.isNullOrBlank()) {
            db.collection("dynamic_modules").document()
        } else {
            db.collection("dynamic_modules").document(existingId)
        }
        try {
            if (existingId.isNullOrBlank()) {
                docRef.set(
                    mapOf(
                        "title" to title.trim(),
                        "subtitle" to subtitle.trim(),
                        "moduleType" to moduleType,
                        "iconKey" to iconKey,
                        "accentHex" to accentHex,
                        "actionType" to actionType,
                        "actionPayload" to actionPayload.trim(),
                        "sortOrder" to sortOrder.coerceIn(0, 1000),
                        "enabled" to enabled,
                        "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                        "createdBy" to uid,
                        "createdByName" to creatorName.ifBlank { "العميد" },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to title.trim(),
                        "subtitle" to subtitle.trim(),
                        "moduleType" to moduleType,
                        "iconKey" to iconKey,
                        "accentHex" to accentHex,
                        "actionType" to actionType,
                        "actionPayload" to actionPayload.trim(),
                        "sortOrder" to sortOrder.coerceIn(0, 1000),
                        "enabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDynamicModule(moduleId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("dynamic_modules").document(moduleId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- INTERACTIVE FLASHCARDS (SPACED REPETITION) ---

    fun observeFlashcards(userId: String): Flow<List<Flashcard>> = flow {
        val path = "users/$userId/flashcards"
        emitAll(
            db.collection("users").document(userId).collection("flashcards")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Flashcard::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<Flashcard> { it.difficultyRating != "hard" }
                                .thenBy { it.nextReviewAtMillis }
                        )
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun createFlashcard(
        deckName: String,
        frontText: String,
        backText: String,
        courseId: String = "",
        courseTitle: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("flashcards").document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "deckName" to deckName.trim().ifBlank { "مراجعة عامة" }.take(100),
            "frontText" to frontText.trim().take(1500),
            "backText" to backText.trim().take(2000),
            "difficultyRating" to SpacedRepetitionEngine.RATING_NEW,
            "easeFactorPercent" to 250,
            "intervalDays" to 0,
            "repetitions" to 0,
            "reviewCount" to 0,
            "nextReviewAtMillis" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (courseId.isNotBlank()) payload["courseId"] = courseId.trim()
        if (courseTitle.isNotBlank()) payload["courseTitle"] = courseTitle.trim().take(120)
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun reviewFlashcard(card: Flashcard, rating: String): Result<Flashcard> = runCatching {
        val uid = requireUserId()
        val updated = SpacedRepetitionEngine.scheduleNext(card, rating)
        val docRef = db.collection("users").document(uid).collection("flashcards").document(card.id)
        try {
            docRef.update(
                mapOf(
                    "difficultyRating" to updated.difficultyRating,
                    "easeFactorPercent" to updated.easeFactorPercent,
                    "intervalDays" to updated.intervalDays,
                    "repetitions" to updated.repetitions,
                    "reviewCount" to updated.reviewCount,
                    "nextReviewAtMillis" to updated.nextReviewAtMillis,
                    "lastReviewedAtMillis" to updated.lastReviewedAtMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            updated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFlashcard(cardId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("flashcards").document(cardId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun seedStarterFlashcards(): Result<Int> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("flashcards")
        val now = System.currentTimeMillis()
        val starter = listOf(
            Triple("الرياضيات الهندسية", "ما هي معادلة أويلر الأساسية في التحليل المركب؟", "e^(iθ) = cos(θ) + i·sin(θ)، وتُستخدم لتحويل الدوال الأسية المركبة إلى صور مثلثية."),
            Triple("الرياضيات الهندسية", "متى تكون المعادلة التفاضلية M dx + N dy = 0 تامة (Exact)؟", "عندما يتحقق الشرط ∂M/∂y = ∂N/∂x في مجال متصل."),
            Triple("الميكانيكا الهندسية", "ما هي شروط الاتزان الاستاتيكي الثلاثة لجسم في المستوى (2D)؟", "ΣFx = 0 ، ΣFy = 0 ، و ΣMz = 0 حول أي نقطة في المستوى."),
            Triple("هندسة البرمجيات", "ما الفرق بين Stack و Queue في هياكل البيانات؟", "Stack يعمل بمبدأ LIFO (الأخير يدخل أولاً يخرج)، بينما Queue يعمل بمبدأ FIFO (الأول يدخل أولاً يخرج).")
        )
        var added = 0
        starter.forEachIndexed { idx, (deck, front, back) ->
            val docId = "starter_fc_${idx + 1}"
            val docRef = colRef.document(docId)
            if (!docRef.get().await().exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "courseTitle" to deck,
                        "deckName" to deck,
                        "frontText" to front,
                        "backText" to back,
                        "difficultyRating" to SpacedRepetitionEngine.RATING_NEW,
                        "easeFactorPercent" to 250,
                        "intervalDays" to 0,
                        "repetitions" to 0,
                        "reviewCount" to 0,
                        "nextReviewAtMillis" to now,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                added++
            }
        }
        added
    }

    // --- DAILY PERSONAL TASK MANAGER (NOTION-STYLE TO-DO LIST) ---

    fun observePersonalTasks(userId: String): Flow<List<PersonalTask>> = flow {
        val path = "users/$userId/personal_tasks"
        emitAll(
            db.collection("users").document(userId).collection("personal_tasks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PersonalTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<PersonalTask> { it.completed }
                                .thenBy {
                                    when (it.priority) {
                                        "high" -> 0
                                        "medium" -> 1
                                        else -> 2
                                    }
                                }
                                .thenBy { it.dueAtMillis }
                        )
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun addPersonalTask(
        title: String,
        notes: String,
        category: String,
        priority: String,
        dueAtMillis: Long,
        courseTitle: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("personal_tasks").document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "title" to title.trim().take(200),
            "category" to category.trim().ifBlank { "مذاكرة" }.take(60),
            "priority" to if (priority in listOf("high", "medium", "low")) priority else "medium",
            "dueAtMillis" to dueAtMillis.coerceAtLeast(0L),
            "completed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (notes.isNotBlank()) payload["notes"] = notes.trim().take(1500)
        if (courseTitle.isNotBlank()) payload["courseTitle"] = courseTitle.trim().take(120)
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun togglePersonalTask(
        task: PersonalTask,
        completed: Boolean,
        currentCompletedCount: Int
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("personal_tasks").document(task.id)
        val userRef = db.collection("users").document(uid)
        val nextCount = if (completed) {
            currentCompletedCount + 1
        } else {
            (currentCompletedCount - 1).coerceAtLeast(0)
        }
        try {
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "completedAtMillis" to if (completed) System.currentTimeMillis() else 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            userRef.update(
                mapOf(
                    "completedPersonalTasksCount" to nextCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deletePersonalTask(taskId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("personal_tasks").document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- GLOBAL ACADEMIC REFERENCES ---

    fun observeGlobalReferences(batchId: String = BatchConstants.DEFAULT_BATCH_ID): Flow<List<GlobalReference>> = flow {
        val uid = requireUserId()
        val path = "global_references"
        emitAll(
            db.collection(path)
                .whereEqualTo("batchId", batchId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(GlobalReference::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.title }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun addGlobalReference(
        title: String,
        provider: String,
        subject: String,
        description: String,
        url: String,
        languageCode: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("global_references").document()
        val payload = mapOf(
            "title" to title.trim().take(160),
            "provider" to provider.trim().take(80),
            "subject" to subject.trim().take(80),
            "description" to description.trim().take(600),
            "url" to url.trim().take(800),
            "languageCode" to languageCode.trim().ifBlank { "en" }.take(16),
            "batchId" to BatchConstants.DEFAULT_BATCH_ID,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteGlobalReference(refId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("global_references").document(refId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun seedGlobalReferencesIfEmpty(uid: String) {
        val colRef = db.collection("global_references")
        val existing = colRef.whereEqualTo("batchId", BatchConstants.DEFAULT_BATCH_ID).limit(1).get().await()
        if (!existing.isEmpty) return

        val starters = listOf(
            mapOf(
                "id" to "ref_mit_ocw",
                "title" to "MIT OpenCourseWare — Engineering & Calculus",
                "provider" to "MIT",
                "subject" to "رياضيات وهندسة",
                "description" to "محاضرات، امتحانات سابقة، وكتب مجانية من معهد ماساتشوستس للتكنولوجيا.",
                "url" to "https://ocw.mit.edu/search/?d=Electrical%20Engineering%20and%20Computer%20Science",
                "languageCode" to "en"
            ),
            mapOf(
                "id" to "ref_khan_academy",
                "title" to "Khan Academy — Differential Equations & Physics",
                "provider" to "Khan Academy",
                "subject" to "رياضيات وفيزياء",
                "description" to "شروحات تفاعلية وتمارين متدرجة في التفاضل والتكامل والميكانيكا والفيزياء.",
                "url" to "https://www.khanacademy.org/math/differential-equations",
                "languageCode" to "ar/en"
            ),
            mapOf(
                "id" to "ref_wolfram_math",
                "title" to "Wolfram MathWorld & Engineering Reference",
                "provider" to "Wolfram",
                "subject" to "رياضيات تطبيقية",
                "description" to "موسوعة المعادلات والنظريات الرياضية والهندسية الدقيقة.",
                "url" to "https://mathworld.wolfram.com/",
                "languageCode" to "en"
            ),
            mapOf(
                "id" to "ref_ieee_xplore",
                "title" to "IEEE Xplore Digital Library",
                "provider" to "IEEE",
                "subject" to "أبحاث وهندسة",
                "description" to "أوراق بحثية ومعايير هندسية عالمية في الإلكترونيات والحاسبات والطاقة.",
                "url" to "https://ieeexplore.ieee.org/",
                "languageCode" to "en"
            ),
            mapOf(
                "id" to "ref_openstax",
                "title" to "OpenStax Free Peer-Reviewed Textbooks",
                "provider" to "Rice University",
                "subject" to "كتب جامعية",
                "description" to "مراجع جامعية مجانية ومحكمة في الفيزياء والكيمياء والرياضيات بصيغة PDF.",
                "url" to "https://openstax.org/subjects",
                "languageCode" to "en"
            ),
            mapOf(
                "id" to "ref_hyperphysics",
                "title" to "HyperPhysics Interactive Concept Maps",
                "provider" to "Georgia State",
                "subject" to "ميكانيكا وفيزياء",
                "description" to "خرائط مفاهيم تفاعلية لقوانين الديناميكا والكهرباء والحرارة.",
                "url" to "http://hyperphysics.phy-astr.gsu.edu/",
                "languageCode" to "en"
            )
        )
        starters.forEach { item ->
            val id = item["id"] as String
            colRef.document(id).set(
                item.filterKeys { it != "id" } + mapOf(
                    "batchId" to BatchConstants.DEFAULT_BATCH_ID,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
