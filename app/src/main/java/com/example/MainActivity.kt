package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.model.AiSummaryState
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.BatchRepository
import com.example.firebase.configureAppCheck
import com.example.ui.components.AiSummaryDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ScientificCalculatorDialog
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LanguagePrefs
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.CommunityChatScreen
import com.example.ui.screens.CourseDetailScreen
import com.example.ui.screens.CoursesHubScreen
import com.example.ui.screens.DynamicAppBuilderScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.PendingApprovalScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StudyFocusHubScreen
import com.example.ui.screens.TasksHubScreen
import com.example.ui.theme.BatchHubTheme
import com.example.ui.viewmodel.BatchViewModel
import com.example.utils.BatchNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        BatchNotificationHelper.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            BatchHubAppRoot()
        }
    }
}

private enum class TopDestination(
    val route: String,
    val labelSelector: (AppStrings) -> String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", { it.navHome }, Icons.Filled.Home, Icons.Outlined.Home),
    COURSES("courses", { it.navCourses }, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    STUDY("study", { it.navStudy }, Icons.Filled.Timer, Icons.Outlined.Timer),
    TASKS("tasks", { it.navTasks }, Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
    CHAT("chat", { it.navChat }, Icons.Filled.Forum, Icons.Outlined.Forum),
    PROFILE("profile", { it.navProfile }, Icons.Filled.Person, Icons.Outlined.Person)
}

private const val ROUTE_COURSE_DETAIL = "course_detail/{courseId}"
private const val ROUTE_DYNAMIC_BUILDER = "dynamic_builder"

@Composable
fun BatchHubAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val initialSavedLanguage = remember(context) { LanguagePrefs.getSavedLanguage(context) }

    var autoSignInAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoSignInAttempted) {
            autoSignInAttempted = true
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        BatchHubTheme(darkTheme = false, language = initialSavedLanguage) {
            SignInScreen(onAuthSuccess = {})
        }
    } else {
        key(user.uid) {
            AuthenticatedRoot(
                firebaseUser = user,
                initialLanguage = initialSavedLanguage,
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedRoot(
    firebaseUser: FirebaseUser,
    initialLanguage: AppLanguage,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: BatchViewModel = viewModel(
        key = firebaseUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BatchViewModel(
                    repository = BatchRepository(db, Firebase.auth),
                    currentUserId = firebaseUser.uid,
                    appContext = app.applicationContext
                )
            }
        }
    )

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isActionBusy.collectAsStateWithLifecycle()
    val profileData = (profileState as? UiState.Success)?.data
    val darkTheme = profileData?.darkMode ?: false

    var activeLanguage by remember { mutableStateOf(initialLanguage) }
    LaunchedEffect(profileData?.preferredLanguage) {
        val code = profileData?.preferredLanguage
        if (!code.isNullOrBlank()) {
            val remoteLang = AppLanguage.fromCode(code)
            if (remoteLang != activeLanguage) {
                activeLanguage = remoteLang
                LanguagePrefs.saveLanguage(context, remoteLang)
            }
        }
    }

    BatchHubTheme(darkTheme = darkTheme, language = activeLanguage) {
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(viewModel) {
            viewModel.events.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }

        when (val state = profileState) {
            UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        title = "تعذر الاتصال بالمنصة",
                        subtitle = state.message,
                        action = {
                            Button(onClick = onSignOut) {
                                Text("تسجيل الخروج وإعادة المحاولة")
                            }
                        }
                    )
                }
            }
            is UiState.Success -> {
                val profile = state.data
                if (profile == null) {
                    ProfileSetupScreenWithSnackbar(
                        snackbarHostState = snackbarHostState,
                        initialName = firebaseUser.displayName ?: "",
                        email = firebaseUser.email ?: "",
                        isBusy = isBusy,
                        onSubmit = { name, record, dept, year, term, joinCode ->
                            viewModel.registerProfile(
                                displayName = name,
                                email = firebaseUser.email ?: "",
                                studentRecordNumber = record,
                                department = dept,
                                academicYear = year,
                                term = term,
                                joinCode = joinCode
                            )
                        },
                        onSignOut = onSignOut
                    )
                } else if (!profile.isApproved) {
                    PendingApprovalScreen(
                        profile = profile,
                        isBusy = isBusy,
                        onSubmitJoinCode = viewModel::submitJoinCodeForPendingAccount,
                        onSignOut = onSignOut
                    )
                } else {
                    MainBatchShell(
                        profile = profile,
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        activeLanguage = activeLanguage,
                        onChangeLanguage = { newLang ->
                            activeLanguage = newLang
                            LanguagePrefs.saveLanguage(context, newLang)
                            viewModel.setPreferredLanguage(newLang.code)
                        },
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSetupScreenWithSnackbar(
    snackbarHostState: SnackbarHostState,
    initialName: String,
    email: String,
    isBusy: Boolean,
    onSubmit: (String, String, String, Int, Int, String) -> Unit,
    onSignOut: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ProfileSetupScreen(
            initialName = initialName,
            email = email,
            isBusy = isBusy,
            onSubmit = onSubmit,
            onSignOut = onSignOut
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun MainBatchShell(
    profile: UserProfile,
    viewModel: BatchViewModel,
    snackbarHostState: SnackbarHostState,
    activeLanguage: AppLanguage,
    onChangeLanguage: (AppLanguage) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(profile.userId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !BatchNotificationHelper.hasNotificationPermission(context)
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val announcementsState by viewModel.announcementsState.collectAsStateWithLifecycle()
    val coursesState by viewModel.coursesState.collectAsStateWithLifecycle()
    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val assignmentsState by viewModel.assignmentsState.collectAsStateWithLifecycle()
    val progressMap by viewModel.assignmentProgressMap.collectAsStateWithLifecycle()
    val quizzesState by viewModel.quizzesState.collectAsStateWithLifecycle()
    val quizAttempts by viewModel.quizAttempts.collectAsStateWithLifecycle()
    val channelsState by viewModel.chatChannelsState.collectAsStateWithLifecycle()
    val studySessions by viewModel.studySessions.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val flashcards by viewModel.flashcards.collectAsStateWithLifecycle()
    val personalTasks by viewModel.personalTasks.collectAsStateWithLifecycle()
    val globalReferencesState by viewModel.globalReferencesState.collectAsStateWithLifecycle()
    val meetingRoomsState by viewModel.meetingRoomsState.collectAsStateWithLifecycle()
    val meetingMessagesState by viewModel.selectedMeetingMessages.collectAsStateWithLifecycle()
    val dynamicModulesState by viewModel.dynamicModulesState.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val aiSummaryState by viewModel.aiSummaryState.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    val courseMaterialsState by viewModel.selectedCourseMaterials.collectAsStateWithLifecycle()
    val chatMessagesState by viewModel.selectedChannelMessages.collectAsStateWithLifecycle()

    var activeChannelId by remember { mutableStateOf<String?>(null) }
    var activeMeetingRoomId by remember { mutableStateOf<String?>(null) }
    var chatInitialTab by remember { mutableIntStateOf(0) }
    var studyInitialTab by remember { mutableIntStateOf(0) }
    var coursesInitialTab by remember { mutableIntStateOf(0) }
    var tasksInitialTab by remember { mutableIntStateOf(0) }
    var showCalculatorDialog by remember { mutableStateOf(false) }
    var showAiSummaryDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val announcements = (announcementsState as? UiState.Success)?.data ?: emptyList()
    val courses = (coursesState as? UiState.Success)?.data ?: emptyList()
    val scheduleEntries = (scheduleState as? UiState.Success)?.data ?: emptyList()
    val assignments = (assignmentsState as? UiState.Success)?.data ?: emptyList()
    val quizzes = (quizzesState as? UiState.Success)?.data ?: emptyList()
    val channels = (channelsState as? UiState.Success)?.data ?: emptyList()
    val leaderboard = (leaderboardState as? UiState.Success)?.data ?: emptyList()
    val globalReferences = (globalReferencesState as? UiState.Success)?.data ?: emptyList()
    val meetingRooms = (meetingRoomsState as? UiState.Success)?.data ?: emptyList()
    val meetingMessages = (meetingMessagesState as? UiState.Success)?.data ?: emptyList()
    val dynamicModules = (dynamicModulesState as? UiState.Success)?.data ?: emptyList()
    val materials = (courseMaterialsState as? UiState.Success)?.data ?: emptyList()
    val messages = (chatMessagesState as? UiState.Success)?.data ?: emptyList()

    val isTopLevelRoute = TopDestination.entries.any { it.route == currentRoute }

    fun navigateTop(route: String) {
        navController.navigate(route) {
            popUpTo(TopDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && isTopLevelRoute) {
                    NavigationBar {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val destLabel = dest.labelSelector(strings)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTop(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = destLabel
                                    )
                                },
                                label = {
                                    Text(
                                        text = destLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && isTopLevelRoute) {
                    NavigationRail {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val destLabel = dest.labelSelector(strings)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTop(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = destLabel
                                    )
                                },
                                label = { Text(destLabel) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = TopDestination.HOME.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(TopDestination.HOME.route) {
                        HomeDashboardScreen(
                            profile = profile,
                            announcements = announcements,
                            courses = courses,
                            scheduleEntries = scheduleEntries,
                            assignments = assignments,
                            assignmentProgress = progressMap,
                            dynamicModules = dynamicModules,
                            notifications = notifications,
                            leaderboard = leaderboard,
                            flashcardsCount = flashcards.size,
                            pendingTodoCount = personalTasks.count { !it.completed },
                            onMarkNotificationsRead = viewModel::markAllNotificationsRead,
                            onOpenCourse = { courseId ->
                                viewModel.selectCourseForMaterials(courseId)
                                navController.navigate("course_detail/$courseId")
                            },
                            onNavigateAssignments = {
                                tasksInitialTab = 1
                                navigateTop(TopDestination.TASKS.route)
                            },
                            onOpenAnalytics = {
                                studyInitialTab = 4
                                navigateTop(TopDestination.STUDY.route)
                            },
                            onOpenFlashcards = {
                                studyInitialTab = 3
                                navigateTop(TopDestination.STUDY.route)
                            },
                            onOpenLeaderboard = {
                                studyInitialTab = 5
                                navigateTop(TopDestination.STUDY.route)
                            },
                            onOpenGlobalReferences = {
                                coursesInitialTab = 1
                                navigateTop(TopDestination.COURSES.route)
                            },
                            onOpenCalculator = { showCalculatorDialog = true },
                            onOpenAiSummarizer = {
                                viewModel.clearAiSummary()
                                showAiSummaryDialog = true
                            },
                            onExecuteDynamicAction = { actionType, _ ->
                                when (actionType) {
                                    "navigate_courses" -> {
                                        coursesInitialTab = 0
                                        navigateTop(TopDestination.COURSES.route)
                                    }
                                    "navigate_study" -> {
                                        studyInitialTab = 0
                                        navigateTop(TopDestination.STUDY.route)
                                    }
                                    "navigate_tasks" -> {
                                        tasksInitialTab = 0
                                        navigateTop(TopDestination.TASKS.route)
                                    }
                                    "navigate_chat" -> {
                                        chatInitialTab = 0
                                        navigateTop(TopDestination.CHAT.route)
                                    }
                                    "open_meetings" -> {
                                        chatInitialTab = 3
                                        navigateTop(TopDestination.CHAT.route)
                                    }
                                    "open_calculator" -> showCalculatorDialog = true
                                }
                            },
                            onOpenDynamicBuilder = { navController.navigate(ROUTE_DYNAMIC_BUILDER) },
                            onAddAnnouncement = { title, body, priority ->
                                viewModel.addAnnouncement(title, body, priority, profile.displayName)
                            },
                            onDeleteAnnouncement = viewModel::deleteAnnouncement,
                            onAddScheduleEntry = viewModel::addScheduleEntry,
                            onDeleteScheduleEntry = viewModel::deleteScheduleEntry,
                            onSeedStarterData = { viewModel.seedStarterData(profile.displayName) }
                        )
                    }

                    composable(TopDestination.COURSES.route) {
                        CoursesHubScreen(
                            profile = profile,
                            courses = courses,
                            globalReferences = globalReferences,
                            initialTabIndex = coursesInitialTab,
                            onSelectCourse = { courseId ->
                                viewModel.selectCourseForMaterials(courseId)
                                navController.navigate("course_detail/$courseId")
                            },
                            onToggleEnroll = { courseId ->
                                viewModel.toggleCourseEnrollment(courseId, profile.enrolledCourseIds)
                            },
                            onAddCourse = viewModel::addCourse,
                            onAddGlobalReference = viewModel::addGlobalReference,
                            onDeleteGlobalReference = viewModel::deleteGlobalReference
                        )
                    }

                    composable(ROUTE_COURSE_DETAIL) { entry ->
                        val courseId = entry.arguments?.getString("courseId") ?: ""
                        LaunchedEffect(courseId) {
                            viewModel.selectCourseForMaterials(courseId)
                        }
                        val course = courses.firstOrNull { it.id == courseId }
                        if (course == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            CourseDetailScreen(
                                course = course,
                                profile = profile,
                                materials = materials,
                                onBack = { navController.popBackStack() },
                                onAddMaterial = { cat, title, desc, week, format, size, url ->
                                    viewModel.addMaterial(course, cat, title, desc, week, format, size, url, profile.displayName)
                                },
                                onDeleteMaterial = viewModel::deleteMaterial,
                                onSummarizeMaterial = { mat ->
                                    viewModel.summarizeMaterialWithAi(mat)
                                    showAiSummaryDialog = true
                                },
                                onSummarizeCustomCourse = { courseTitle, promptText ->
                                    viewModel.summarizeWithAi("ملخص مادة $courseTitle", promptText, courseTitle)
                                    showAiSummaryDialog = true
                                },
                                onShowMessage = { msg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }

                    composable(TopDestination.STUDY.route) {
                        StudyFocusHubScreen(
                            profile = profile,
                            courses = courses,
                            sessions = studySessions,
                            leaderboard = leaderboard,
                            flashcards = flashcards,
                            assignments = assignments,
                            assignmentProgress = progressMap,
                            personalTasks = personalTasks,
                            initialTabIndex = studyInitialTab,
                            onRecordSession = { mode, courseTitle, durationSec, points, isPomodoro ->
                                viewModel.recordStudySession(
                                    profile = profile,
                                    mode = mode,
                                    courseTitle = courseTitle,
                                    durationSeconds = durationSec,
                                    pointsEarned = points,
                                    isPomodoroCompletion = isPomodoro
                                )
                            },
                            onCreateFlashcard = viewModel::createFlashcard,
                            onReviewFlashcard = viewModel::reviewFlashcard,
                            onDeleteFlashcard = viewModel::deleteFlashcard,
                            onSeedFlashcards = viewModel::seedStarterFlashcards
                        )
                    }

                    composable(TopDestination.TASKS.route) {
                        TasksHubScreen(
                            profile = profile,
                            courses = courses,
                            assignments = assignments,
                            progressMap = progressMap,
                            quizzes = quizzes,
                            attempts = quizAttempts,
                            personalTasks = personalTasks,
                            initialTabIndex = tasksInitialTab,
                            onAddPersonalTask = viewModel::addPersonalTask,
                            onTogglePersonalTask = viewModel::togglePersonalTask,
                            onDeletePersonalTask = viewModel::deletePersonalTask,
                            onToggleCompleted = viewModel::toggleAssignmentDone,
                            onSubmitSolution = viewModel::submitAssignmentSolution,
                            onAddAssignment = viewModel::addAssignment,
                            onSaveQuizAttempt = viewModel::saveQuizAttempt,
                            onAddQuiz = viewModel::addQuiz
                        )
                    }

                    composable(TopDestination.CHAT.route) {
                        CommunityChatScreen(
                            profile = profile,
                            channels = channels,
                            activeChannelId = activeChannelId,
                            messages = messages,
                            meetingRooms = meetingRooms,
                            activeMeetingRoomId = activeMeetingRoomId,
                            meetingMessages = meetingMessages,
                            initialTabIndex = chatInitialTab,
                            onSelectChannel = { id ->
                                activeChannelId = id
                                viewModel.selectChannelForMessages(id)
                            },
                            onCreateTeam = { name, desc ->
                                viewModel.createProjectTeam(name, desc, profile.displayName)
                            },
                            onAddMemberToTeam = viewModel::addMemberToTeam,
                            onSendMessage = { channelId, text, attName, attUrl ->
                                viewModel.sendMessage(channelId, profile.displayName, profile.role, text, attName, attUrl)
                            },
                            onSelectMeetingRoom = { roomId ->
                                activeMeetingRoomId = roomId
                                viewModel.selectMeetingRoom(roomId)
                            },
                            onCreateMeetingRoom = { title, topic, courseTitle, url ->
                                viewModel.createMeetingRoom(title, topic, courseTitle, profile.displayName, url)
                            },
                            onJoinOrLeaveMeeting = { room, join ->
                                viewModel.joinOrLeaveMeetingRoom(room, profile.displayName, join)
                            },
                            onToggleRaiseHand = viewModel::toggleRaiseHandInMeeting,
                            onUpdateMeetingAgenda = viewModel::updateMeetingAgenda,
                            onEndMeetingRoom = viewModel::endMeetingRoom,
                            onSendMeetingMessage = { roomId, text ->
                                viewModel.sendMeetingMessage(roomId, profile.displayName, profile.role, text)
                            }
                        )
                    }

                    composable(TopDestination.PROFILE.route) {
                        ProfileSettingsScreen(
                            profile = profile,
                            courses = courses,
                            joinRequests = joinRequests,
                            activeLanguage = activeLanguage,
                            onChangeLanguage = onChangeLanguage,
                            onToggleDarkMode = viewModel::toggleDarkMode,
                            onUpdateNotifications = viewModel::updateNotificationPrefs,
                            onToggleCourseEnrollment = { courseId ->
                                viewModel.toggleCourseEnrollment(courseId, profile.enrolledCourseIds)
                            },
                            onUpdateMemberRole = viewModel::approveOrUpdateMember,
                            onOpenDynamicBuilder = { navController.navigate(ROUTE_DYNAMIC_BUILDER) },
                            onSeedStarterData = { viewModel.seedStarterData(profile.displayName) },
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            onSignOut = onSignOut
                        )
                    }

                    composable(ROUTE_DYNAMIC_BUILDER) {
                        DynamicAppBuilderScreen(
                            profile = profile,
                            modules = dynamicModules,
                            onBack = { navController.popBackStack() },
                            onSaveModule = { id, title, subtitle, type, icon, accent, action, payload, order, enabled ->
                                viewModel.saveDynamicModule(
                                    existingId = id,
                                    title = title,
                                    subtitle = subtitle,
                                    moduleType = type,
                                    iconKey = icon,
                                    accentHex = accent,
                                    actionType = action,
                                    actionPayload = payload,
                                    sortOrder = order,
                                    enabled = enabled,
                                    creatorName = profile.displayName
                                )
                            },
                            onDeleteModule = viewModel::deleteDynamicModule
                        )
                    }
                }
            }
        }
    }

    if (showCalculatorDialog) {
        ScientificCalculatorDialog(onDismiss = { showCalculatorDialog = false })
    }

    if (showAiSummaryDialog) {
        AiSummaryDialog(
            state = aiSummaryState,
            onSummarizeCustomText = { title, text ->
                viewModel.summarizeWithAi(title, text)
            },
            onDismiss = {
                viewModel.clearAiSummary()
                showAiSummaryDialog = false
            }
        )
    }
}
