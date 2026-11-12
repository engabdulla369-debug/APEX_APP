package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BatchConstants
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BatchRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun observeCourses_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = BatchRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCourses().first()
            }
        } catch (e: Exception) {
            caught = e
        }

        assertNotNull("Expected unauthenticated observeCourses to fail", caught)
        val firestoreException = caught?.unwrapFirestoreException()
        if (firestoreException != null) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        } else {
            assertTrue(caught is IllegalStateException)
        }
    }

    @Test
    fun registerUserProfile_withValidJoinCode_approvesStudent() = runBlocking {
        val aliceEmail = uniqueEmail("alice_reg")
        val uid = signInTestUser(aliceEmail)
        val repository = BatchRepository(firestore, auth)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUserProfile(
                displayName = "سارة أحمد",
                email = aliceEmail,
                studentRecordNumber = "20261042",
                department = "هندسة الحاسبات",
                academicYear = 2,
                term = 1,
                joinCodeInput = BatchConstants.DEFAULT_JOIN_CODE
            )
        }

        assertTrue("Expected profile registration to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(uid, profile.userId)
        assertEquals("approved", profile.approvalStatus)
        assertEquals("student", profile.role)
        assertEquals(BatchConstants.DEFAULT_BATCH_ID, profile.batchId)
    }

    @Test
    fun getUserProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceEmail = uniqueEmail("alice_pii")
        val bobEmail = uniqueEmail("bob_pii")
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = BatchRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.registerUserProfile(
                displayName = "سارة أحمد",
                email = aliceEmail,
                studentRecordNumber = "20261042",
                department = "هندسة الحاسبات",
                academicYear = 2,
                term = 1,
                joinCodeInput = BatchConstants.DEFAULT_JOIN_CODE
            ).getOrThrow()
        }

        signInTestUser(bobEmail)
        val bobRepo = BatchRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getUserProfile(aliceUid)
        }

        assertTrue("Cross-user PII read must fail", result.isFailure)
        val firestoreError = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError!!.code)
    }

    @Test
    fun addCourse_asStudent_failsWithPermissionDenied() = runBlocking {
        val aliceEmail = uniqueEmail("alice_course")
        signInTestUser(aliceEmail)
        val repository = BatchRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUserProfile(
                displayName = "سارة أحمد",
                email = aliceEmail,
                studentRecordNumber = "20261042",
                department = "هندسة الحاسبات",
                academicYear = 2,
                term = 1,
                joinCodeInput = BatchConstants.DEFAULT_JOIN_CODE
            ).getOrThrow()
        }

        val courseResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addCourse(
                title = "مادة تجريبية",
                code = "TEST101",
                academicYear = 1,
                term = 1,
                instructorName = "د. أحمد",
                department = "هندسة",
                accentHex = "#1E3A8A"
            )
        }

        assertTrue("Student should not be permitted to create courses", courseResult.isFailure)
        val firestoreError = courseResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError!!.code)
    }

    @Test
    fun assignmentProgress_and_projectTeamChat_workForApprovedStudent() = runBlocking {
        val aliceEmail = uniqueEmail("alice_work")
        val aliceUid = signInTestUser(aliceEmail)
        val repository = BatchRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUserProfile(
                displayName = "سارة أحمد",
                email = aliceEmail,
                studentRecordNumber = "20261042",
                department = "هندسة الحاسبات",
                academicYear = 2,
                term = 1,
                joinCodeInput = BatchConstants.DEFAULT_JOIN_CODE
            ).getOrThrow()
        }

        val assignmentId = "assign_${UUID.randomUUID().toString().take(8)}"
        val submitResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.submitAssignmentSolution(
                assignmentId = assignmentId,
                fileName = "solution.pdf",
                fileType = "PDF",
                note = "الحل الكامل",
                fileUri = "content://sample/solution.pdf",
                existingProgress = null
            )
        }
        assertTrue("Assignment submission should succeed: ${submitResult.exceptionOrNull()}", submitResult.isSuccess)

        val progressMap = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAssignmentProgress(aliceUid).first { it.containsKey(assignmentId) }
        }
        assertTrue(progressMap[assignmentId]?.completed == true)
        assertEquals("solution.pdf", progressMap[assignmentId]?.submissionFileName)

        val teamId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProjectTeam(
                name = "مشروع الميكانيكا - فريق 1",
                description = "فريق التصميم",
                creatorName = "سارة أحمد"
            ).getOrThrow()
        }

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.sendChatMessage(
                channelId = teamId,
                senderName = "سارة أحمد",
                senderRole = "student",
                text = "أهلاً بفريق المشروع!"
            )
        }
        assertTrue("Sending team message should succeed: ${msgResult.exceptionOrNull()}", msgResult.isSuccess)
    }

    @Test
    fun studySession_leaderboard_and_meetingRoom_workForApprovedStudent_whileDynamicModuleIsDenied() = runBlocking {
        val studentEmail = uniqueEmail("stud_focus")
        val uid = signInTestUser(studentEmail)
        val repository = BatchRepository(firestore, auth)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUserProfile(
                displayName = "يوسف علي",
                email = studentEmail,
                studentRecordNumber = "20263311",
                department = "كهرباء",
                academicYear = 2,
                term = 1,
                joinCodeInput = BatchConstants.DEFAULT_JOIN_CODE
            ).getOrThrow()
        }

        val sessionRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordStudySessionAndLeaderboard(
                profile = profile,
                mode = "long_timer",
                courseTitle = "الرياضيات الهندسية",
                durationSeconds = 3600L,
                pointsEarned = 60,
                isPomodoroCompletion = false
            )
        }
        assertTrue("Expected study session & leaderboard write to succeed: ${sessionRes.exceptionOrNull()}", sessionRes.isSuccess)

        val leaderboard = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLeaderboard().first { list -> list.any { it.userId == uid } }
        }
        val entry = leaderboard.first { it.userId == uid }
        assertEquals(60, entry.focusPoints)
        assertEquals(3600L, entry.totalStudySeconds)

        val roomId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createMeetingRoom(
                title = "غرفة مذاكرة الكهرباء",
                topic = "حل مسائل الدوائر",
                courseTitle = "دوائر كهربائية",
                hostName = "يوسف علي"
            ).getOrThrow()
        }
        val roomMsgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.sendMeetingMessage(
                roomId = roomId,
                senderName = "يوسف علي",
                senderRole = "student",
                text = "بدأنا مراجعة الفصل الثالث"
            )
        }
        assertTrue("Meeting room message should succeed: ${roomMsgRes.exceptionOrNull()}", roomMsgRes.isSuccess)

        // Student must be blocked from writing dynamic_modules
        val modRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveDynamicModule(
                existingId = null,
                title = "اختراق",
                subtitle = "غير مصرح",
                moduleType = "home_card",
                iconKey = "star",
                accentHex = "#1E3A8A",
                actionType = "navigate_study",
                actionPayload = "",
                sortOrder = 1,
                enabled = true,
                creatorName = "يوسف علي"
            )
        }
        assertTrue("Student should be denied creating dynamic_modules", modRes.isFailure)
        val firestoreErr = modRes.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr!!.code)
    }

    @Test
    fun flashcards_personalTasks_globalReferences_and_languageStreakSync_workAgainstRules() = runBlocking {
        val studentEmail = uniqueEmail("global_upgrades")
        val uid = signInTestUser(studentEmail)
        val repository = BatchRepository(firestore, auth)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUserProfile(
                displayName = "مازن طارق",
                email = studentEmail,
                studentRecordNumber = "20265519",
                department = "حاسبات",
                academicYear = 3,
                term = 1,
                joinCodeInput = BatchConstants.DEFAULT_JOIN_CODE
            ).getOrThrow()
        }

        // 1. Update preferredLanguage (RTL <-> LTR switch persistence)
        val langRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateUserSettings(preferredLanguage = "en")
        }
        assertTrue("Language update should succeed: ${langRes.exceptionOrNull()}", langRes.isSuccess)
        val updatedProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserProfile(uid).getOrThrow()
        }
        assertEquals("en", updatedProfile?.preferredLanguage)

        // 2. Flashcards CRUD + Spaced Repetition review
        val cardId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createFlashcard(
                deckName = "Signals",
                frontText = "Fourier Transform of delta(t)",
                backText = "1"
            ).getOrThrow()
        }
        val cards = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFlashcards(uid).first { list -> list.any { it.id == cardId } }
        }
        val createdCard = cards.first { it.id == cardId }
        val reviewed = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.reviewFlashcard(createdCard, "hard").getOrThrow()
        }
        assertEquals("hard", reviewed.difficultyRating)
        assertEquals(0, reviewed.intervalDays)

        // 3. Personal Daily Tasks CRUD + completion counter sync
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addPersonalTask(
                title = "Solve 10 Calculus problems",
                notes = "Chapter 4",
                category = "Study",
                priority = "high",
                dueAtMillis = System.currentTimeMillis() + 3600_000L
            ).getOrThrow()
        }
        val tasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePersonalTasks(uid).first { list -> list.any { it.id == taskId } }
        }
        val createdTask = tasks.first { it.id == taskId }
        val toggleRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.togglePersonalTask(createdTask, completed = true, currentCompletedCount = 0)
        }
        assertTrue("Personal task toggle should succeed: ${toggleRes.exceptionOrNull()}", toggleRes.isSuccess)

        // 4. Global Reference creation
        val refId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addGlobalReference(
                title = "MIT OCW Linear Algebra",
                provider = "MIT",
                subject = "Math",
                description = "Gilbert Strang lectures",
                url = "https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/",
                languageCode = "en"
            ).getOrThrow()
        }
        val refs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeGlobalReferences().first { list -> list.any { it.id == refId } }
        }
        assertEquals("MIT", refs.first { it.id == refId }.provider)

        // 5. Study session updates streak and leaderboard currentStreakDays
        val sessRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordStudySessionAndLeaderboard(
                profile = profile,
                mode = "focus_zone",
                courseTitle = "Math",
                durationSeconds = 1800L,
                pointsEarned = 45,
                isPomodoroCompletion = false
            )
        }
        assertTrue("Study session should succeed: ${sessRes.exceptionOrNull()}", sessRes.isSuccess)
        val lb = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLeaderboard().first { list -> list.any { it.userId == uid } }
        }
        assertTrue(lb.first { it.userId == uid }.currentStreakDays >= 1)
    }

    private fun uniqueEmail(prefix: String): String {
        return "${prefix}_${UUID.randomUUID().toString().take(8)}@test.com"
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
