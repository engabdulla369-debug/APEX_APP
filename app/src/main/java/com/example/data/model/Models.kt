package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiSummaryState {
    data object Idle : AiSummaryState
    data object Loading : AiSummaryState
    data class Streaming(val text: String, val sourceTitle: String) : AiSummaryState
    data class Success(val text: String, val sourceTitle: String) : AiSummaryState
    data class Error(val message: String, val sourceTitle: String = "") : AiSummaryState
}

object BatchConstants {
    const val DEFAULT_BATCH_ID = "eng_2026"
    const val DEFAULT_BATCH_NAME = "دفعة الهندسة 2026"
    const val DEFAULT_JOIN_CODE = "ENG-2026"
    const val BOOTSTRAPPED_ADMIN_EMAIL = "engabdulla369@gmail.com"
}

enum class UserRole(val key: String, val labelAr: String) {
    STUDENT("student", "طالب"),
    TA("ta", "مساعد مادة / دكتور"),
    BATCH_REP("batch_rep", "ممثل الدفعة / مسؤول"),
    DEAN("dean", "العميد / المسؤول الأعلى");

    companion object {
        fun fromKey(key: String?): UserRole = entries.firstOrNull { it.key == key } ?: STUDENT
    }
}

enum class MaterialCategory(val key: String, val labelAr: String) {
    LECTURES("lectures", "محاضرات وسكاشن"),
    SUMMARIES("summaries", "الملخصات والشيتات"),
    REFERENCES("references", "بنك المراجع والكتب"),
    LINKS("links", "تسجيلات وروابط");

    companion object {
        fun fromKey(key: String?): MaterialCategory = entries.firstOrNull { it.key == key } ?: LECTURES
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val studentRecordNumber: String = "",
    val department: String = "",
    val academicYear: Int = 1,
    val term: Int = 1,
    val role: String = "student",
    val approvalStatus: String = "pending",
    val batchId: String = "",
    val joinCode: String = "",
    val assignedCourseIds: List<String> = emptyList(),
    val enrolledCourseIds: List<String> = emptyList(),
    val darkMode: Boolean = false,
    val notifyLectures: Boolean = true,
    val notifyAssignments: Boolean = true,
    val focusPoints: Int = 0,
    val totalStudySeconds: Long = 0L,
    val longestSessionSeconds: Long = 0L,
    val pomodoroSessionsCompleted: Int = 0,
    val preferredLanguage: String = "ar",
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val lastStudyDayKey: String = "",
    val completedPersonalTasksCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val userRole: UserRole get() = UserRole.fromKey(role)
    val isApproved: Boolean get() = approvalStatus == "approved"
    val isDean: Boolean get() = userRole == UserRole.DEAN
    val isBatchRep: Boolean get() = userRole == UserRole.BATCH_REP || userRole == UserRole.DEAN
    fun canManageCourse(courseId: String): Boolean {
        return isBatchRep || (userRole == UserRole.TA && assignedCourseIds.contains(courseId))
    }
}

@IgnoreExtraProperties
data class JoinRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val studentRecordNumber: String = "",
    val department: String = "",
    val academicYear: Int = 1,
    val role: String = "student",
    val approvalStatus: String = "pending",
    val batchId: String = "",
    val assignedCourseIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val priority: String = "normal", // normal | urgent | circular
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val authorId: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Course(
    @DocumentId val id: String = "",
    val title: String = "",
    val code: String = "",
    val academicYear: Int = 1,
    val term: Int = 1,
    val instructorName: String = "",
    val department: String = "",
    val accentHex: String = "#1E3A8A",
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ScheduleEntry(
    @DocumentId val id: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val dayOfWeek: Int = 1,
    val startTime: String = "",
    val endTime: String = "",
    val location: String = "",
    val instructorName: String = "",
    val sessionType: String = "lecture",
    val academicYear: Int = 1,
    val term: Int = 1,
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CourseMaterial(
    @DocumentId val id: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val category: String = "lectures",
    val title: String = "",
    val description: String = "",
    val weekNumber: Int = 1,
    val fileFormat: String = "PDF",
    val fileSize: String = "",
    val url: String = "",
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val uploaderId: String = "",
    val uploaderName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Assignment(
    @DocumentId val id: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val title: String = "",
    val description: String = "",
    val dueAtMillis: Long = 0L,
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AssignmentProgress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val assignmentId: String = "",
    val completed: Boolean = false,
    val submissionFileName: String = "",
    val submissionFileType: String = "",
    val submissionNote: String = "",
    val submissionUri: String = "",
    val submittedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class QuizQuestion(
    val questionText: String = "",
    val questionType: String = "mcq",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val explanation: String = ""
)

@IgnoreExtraProperties
data class Quiz(
    @DocumentId val id: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val title: String = "",
    val description: String = "",
    val durationMinutes: Int = 10,
    val questions: List<QuizQuestion> = emptyList(),
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class QuizAttempt(
    @DocumentId val id: String = "",
    val userId: String = "",
    val quizId: String = "",
    val quizTitle: String = "",
    val courseTitle: String = "",
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val durationSecondsUsed: Int = 0,
    val selectedAnswers: List<Int> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatChannel(
    @DocumentId val id: String = "",
    val name: String = "",
    val type: String = "general",
    val courseId: String = "",
    val description: String = "",
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val createdByName: String = "",
    val lastMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val channelId: String = "",
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "student",
    val text: String = "",
    val attachmentName: String = "",
    val attachmentUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = "pomodoro", // pomodoro | long_timer | focus_zone
    val courseTitle: String = "",
    val durationSeconds: Long = 0L,
    val pointsEarned: Int = 0,
    val completedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val department: String = "",
    val academicYear: Int = 1,
    val role: String = "student",
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val focusPoints: Int = 0,
    val totalStudySeconds: Long = 0L,
    val pomodoroSessionsCompleted: Int = 0,
    val longestSessionSeconds: Long = 0L,
    val currentStreakDays: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MeetingRoom(
    @DocumentId val id: String = "",
    val title: String = "",
    val topic: String = "",
    val courseTitle: String = "",
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val hostId: String = "",
    val hostName: String = "",
    val status: String = "active", // active | ended
    val participantUids: List<String> = emptyList(),
    val participantNames: List<String> = emptyList(),
    val raisedHandUids: List<String> = emptyList(),
    val agendaNotes: String = "",
    val meetingUrl: String = "",
    val startedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MeetingMessage(
    @DocumentId val id: String = "",
    val channelId: String = "",
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "student",
    val text: String = "",
    val attachmentName: String = "",
    val attachmentUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DynamicModule(
    @DocumentId val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val moduleType: String = "home_card", // home_card | quick_tool | official_banner
    val iconKey: String = "star", // star | study | calculator | meeting | link | circular
    val accentHex: String = "#1E3A8A",
    val actionType: String = "navigate_study", // navigate_courses | navigate_study | navigate_tasks | navigate_chat | open_calculator | open_meetings | external_url
    val actionPayload: String = "",
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val createdBy: String = "",
    val createdByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AppNotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val category: String, // circular | urgent | announcement | assignment | quiz
    val timestampMillis: Long = System.currentTimeMillis(),
    val isUnread: Boolean = true
)

@IgnoreExtraProperties
data class Flashcard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val deckName: String = "",
    val frontText: String = "",
    val backText: String = "",
    val difficultyRating: String = "new", // new | easy | medium | hard
    val easeFactorPercent: Int = 250,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val reviewCount: Int = 0,
    val nextReviewAtMillis: Long = 0L,
    val lastReviewedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PersonalTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val category: String = "مذاكرة",
    val courseTitle: String = "",
    val priority: String = "medium", // high | medium | low
    val dueAtMillis: Long = 0L,
    val completed: Boolean = false,
    val completedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GlobalReference(
    @DocumentId val id: String = "",
    val title: String = "",
    val provider: String = "",
    val subject: String = "",
    val description: String = "",
    val url: String = "",
    val languageCode: String = "en",
    val batchId: String = BatchConstants.DEFAULT_BATCH_ID,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

