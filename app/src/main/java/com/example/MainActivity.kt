package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.calls.IncomingCallCoordinator
import com.example.data.model.CallSession
import com.example.data.model.CallTarget
import com.example.data.model.UiState
import com.example.data.model.buildDirectConversationId
import com.example.data.sync.UniversAppContainer
import com.example.notifications.FcmTokenManager
import com.example.notifications.InAppMessageBanner
import com.example.notifications.MessageNotificationCoordinator
import com.example.notifications.NotificationHelper
import com.example.notifications.RealtimeMessagingService
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.PhoneLoginSession
import com.example.ui.auth.PhoneSessionLoadState
import com.example.ui.auth.PhoneSessionStore
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOut
import com.example.ui.components.GlassmorphismCard
import com.example.ui.components.IosInAppNotificationBanner
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.rememberIosHaptics
import com.example.ui.screens.CallHostOverlay
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.NotificationHealthScreen
import com.example.ui.screens.PeopleScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.evaluateNotificationHealth
import com.example.ui.theme.PulseChatTheme
import com.example.ui.viewmodel.CallViewModel
import com.example.ui.viewmodel.ChatRoomViewModel
import com.example.ui.viewmodel.MainMessagingViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Routes {
    const val CHATS = "chats"
    const val PEOPLE = "people"
    const val PROFILE = "profile"
    const val NOTIFICATION_HEALTH = "notification_health"
    const val CHAT_ROOM = "chat_room/{conversationId}"

    fun chatRoom(conversationId: String): String = "chat_room/$conversationId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)
        handleNotificationDeepLink(intent)
        setContent {
            PulseChatTheme {
                PulseChatApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationDeepLink(intent)
    }

    private fun handleNotificationDeepLink(intent: Intent?) {
        val convId = intent?.getStringExtra(NotificationHelper.EXTRA_CONVERSATION_ID)
        if (!convId.isNullOrBlank()) {
            MessageNotificationCoordinator.emitDeepLinkConversation(convId)
        }
        val acceptCallId = intent?.getStringExtra(NotificationHelper.EXTRA_ACCEPT_CALL_ID)
        if (!acceptCallId.isNullOrBlank()) {
            IncomingCallCoordinator.emitNotificationAcceptCallId(acceptCallId)
        }
    }
}

@Composable
fun PulseChatApp() {
    val context = LocalContext.current
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val phoneSessionFlow = remember(context) { PhoneSessionStore.sessionStateFlow(context) }
    val phoneSessionState by phoneSessionFlow.collectAsStateWithLifecycle(initialValue = PhoneSessionLoadState.Loading)

    val firebaseUser = currentUser
    val savedPhoneSession = (phoneSessionState as? PhoneSessionLoadState.Ready)?.session

    when {
        firebaseUser == null && phoneSessionState is PhoneSessionLoadState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LiquidGlassBackdrop()
                CircularProgressIndicator()
            }
        }
        firebaseUser == null && savedPhoneSession == null -> {
            LaunchedEffect(Unit) {
                UniversAppContainer.get(context).syncCoordinator.stopSession()
                MessageNotificationCoordinator.stopObserving()
                RealtimeMessagingService.syncServiceState(context, shouldRun = false)
            }
            AuthScreen(onAuthSuccess = {})
        }
        else -> {
            val hasFirebase = firebaseUser != null
            val activeUserId = firebaseUser?.uid ?: savedPhoneSession!!.userId
            key(activeUserId, hasFirebase) {
                AuthenticatedShell(
                    currentUserId = activeUserId,
                    phoneSession = savedPhoneSession,
                    hasFirebaseSession = hasFirebase
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    phoneSession: PhoneLoginSession?,
    hasFirebaseSession: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val appContainer = remember(context) { UniversAppContainer.get(context) }

    val mainViewModel: MainMessagingViewModel = viewModel(
        key = "${currentUserId}_$hasFirebaseSession",
        factory = viewModelFactory {
            initializer {
                MainMessagingViewModel(
                    repository = appContainer.messagingRepository,
                    currentUserId = currentUserId,
                    dao = appContainer.dao,
                    syncCoordinator = appContainer.syncCoordinator,
                    connectivityMonitor = appContainer.connectivityMonitor,
                    presenceManager = appContainer.presenceManager,
                    callSignalingRepository = appContainer.callSignalingRepository,
                    phoneSession = phoneSession,
                    hasFirebaseAuth = hasFirebaseSession
                )
            }
        }
    )

    val callViewModel: CallViewModel = viewModel(
        key = "call_${currentUserId}_$hasFirebaseSession",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                CallViewModel(
                    appContext = app,
                    repository = appContainer.callSignalingRepository,
                    currentUserId = currentUserId,
                    hasFirebaseAuth = hasFirebaseSession
                )
            }
        }
    )

    val currentUserProfile by mainViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val privateUser by mainViewModel.privateUser.collectAsStateWithLifecycle()
    val blockedUserIds by mainViewModel.blockedUserIds.collectAsStateWithLifecycle()
    val callHistory by mainViewModel.callHistory.collectAsStateWithLifecycle()
    val conversationsState by mainViewModel.conversationsState.collectAsStateWithLifecycle()
    val peopleState by mainViewModel.peopleState.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val chatFilter by mainViewModel.chatFilter.collectAsStateWithLifecycle()
    val peopleSearchQuery by mainViewModel.peopleSearchQuery.collectAsStateWithLifecycle()
    val actionMessage by mainViewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by mainViewModel.isBusy.collectAsStateWithLifecycle()
    val isNetworkOnline by mainViewModel.isNetworkOnline.collectAsStateWithLifecycle()
    val pendingOutboxCount by mainViewModel.pendingOutboxCount.collectAsStateWithLifecycle()
    val incomingCall by callViewModel.incomingCall.collectAsStateWithLifecycle()
    val callState by callViewModel.callState.collectAsStateWithLifecycle()
    val callNotice by callViewModel.callNotice.collectAsStateWithLifecycle()

    LaunchedEffect(blockedUserIds) {
        callViewModel.updateBlockedUserIds(blockedUserIds)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationsEnabled = privateUser?.notificationsEnabled ?: true
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || NotificationHelper.hasNotificationPermission(context)
    }
    var requestedNotificationOnce by rememberSaveable(currentUserId) { mutableStateOf(false) }

    LaunchedEffect(currentUserId, hasFirebaseSession) {
        NotificationHelper.ensureChannels(context)
        if (hasFirebaseSession) {
            MessageNotificationCoordinator.startObservingForUser(context, currentUserId)
        } else {
            MessageNotificationCoordinator.stopObserving()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission &&
            !requestedNotificationOnce
        ) {
            requestedNotificationOnce = true
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(currentUserId, hasFirebaseSession, notificationsEnabled, hasNotificationPermission) {
        RealtimeMessagingService.syncServiceState(
            context = context,
            shouldRun = hasFirebaseSession && notificationsEnabled && hasNotificationPermission
        )
    }

    DisposableEffect(lifecycleOwner, mainViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mainViewModel.setForegroundPresence(true)
                    MessageNotificationCoordinator.setAppForegroundState(true)
                    hasNotificationPermission = NotificationHelper.hasNotificationPermission(context)
                }
                Lifecycle.Event.ON_STOP -> {
                    mainViewModel.setForegroundPresence(false)
                    MessageNotificationCoordinator.setAppForegroundState(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearActionMessage()
        }
    }

    LaunchedEffect(callNotice) {
        val notice = callNotice
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            callViewModel.clearCallNotice()
        }
    }

    var pendingCallAction by remember { mutableStateOf<PendingCallAction?>(null) }
    var callPermissionMessage by remember { mutableStateOf<String?>(null) }

    val executePendingCall: (PendingCallAction) -> Unit = { action ->
        when (action) {
            is PendingCallAction.Outgoing -> {
                val profile = currentUserProfile
                if (profile == null) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Syncing your profile, please try again in a moment.")
                    }
                } else {
                    callViewModel.startOutgoingCall(
                        callerProfile = profile,
                        target = action.target,
                        isVideoCall = action.isVideoCall
                    )
                }
            }
            is PendingCallAction.Accept -> {
                callViewModel.acceptIncomingCall(action.session)
            }
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val action = pendingCallAction ?: return@rememberLauncherForActivityResult
        pendingCallAction = null
        val allGranted = results.values.all { it }
        if (allGranted) {
            executePendingCall(action)
        } else {
            callPermissionMessage = if (action.needsCamera) {
                "Camera and Microphone permissions are required to place or answer HD Video calls."
            } else {
                "Microphone permission is required to place or answer HD Voice calls."
            }
        }
    }

    val launchWithCallPermissions: (PendingCallAction) -> Unit = { action ->
        val required = if (action.needsCamera) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val missing = required.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            executePendingCall(action)
        } else {
            pendingCallAction = action
            callPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    var activeBanner by remember { mutableStateOf<InAppMessageBanner?>(null) }
    LaunchedEffect(currentUserId) {
        MessageNotificationCoordinator.inAppBanners.collect { banner ->
            activeBanner = banner
        }
    }
    LaunchedEffect(activeBanner) {
        if (activeBanner != null) {
            delay(4500L)
            activeBanner = null
        }
    }

    val allConversations = (conversationsState as? UiState.Success)?.data.orEmpty()
    val totalUnread = remember(allConversations, currentUserId) {
        allConversations.sumOf { it.unreadCountFor(currentUserId) }
    }
    val allPeople = (peopleState as? UiState.Success)?.data.orEmpty()
    val readReceiptsEnabled = privateUser?.readReceiptsEnabled ?: true
    val viewerShowsOnline = privateUser?.onlineStatusVisible ?: true
    val pendingDeepLinkId by MessageNotificationCoordinator.pendingDeepLinkConversationId.collectAsStateWithLifecycle()
    val pendingAcceptCall by IncomingCallCoordinator.pendingAcceptCall.collectAsStateWithLifecycle()
    val pendingAcceptCallId by IncomingCallCoordinator.pendingAcceptCallId.collectAsStateWithLifecycle()
    val fcmDiagnosticState by FcmTokenManager.diagnosticState.collectAsStateWithLifecycle()
    val notificationHealthOk = remember(hasNotificationPermission, fcmDiagnosticState, hasFirebaseSession) {
        evaluateNotificationHealth(context, hasFirebaseSession, fcmDiagnosticState).isAllHealthy
    }

    LaunchedEffect(pendingAcceptCall, pendingAcceptCallId, hasFirebaseSession) {
        val readySession = IncomingCallCoordinator.consumePendingAcceptCall()
        if (readySession != null) {
            IncomingCallCoordinator.consumePendingAcceptCallId()
            launchWithCallPermissions(PendingCallAction.Accept(readySession))
            return@LaunchedEffect
        }
        val targetCallId = IncomingCallCoordinator.consumePendingAcceptCallId()
        if (!targetCallId.isNullOrBlank() && hasFirebaseSession) {
            val fetched = callViewModel.getRingingCallSessionById(targetCallId)
            if (fetched != null) {
                launchWithCallPermissions(PendingCallAction.Accept(fetched))
            } else {
                snackbarHostState.showSnackbar("Call is no longer active.")
            }
        }
    }

    val handleSignOut: () -> Unit = {
        coroutineScope.launch {
            callViewModel.hangUp()
            RealtimeMessagingService.syncServiceState(context, shouldRun = false)
            mainViewModel.prepareForSignOut(context)
            withContext(NonCancellable) {
                PhoneSessionStore.clearSession(context)
            }
            signOut(credentialManager, coroutineScope)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var expandedSelectedChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeTab by rememberSaveable { mutableStateOf(Routes.CHATS) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp
        val isMedium = maxWidth >= 600.dp && !isExpanded

        val currentVisibleConversationId = if (isExpanded) {
            if (activeTab == Routes.CHATS) expandedSelectedChatId else null
        } else if (currentRoute == Routes.CHAT_ROOM) {
            backStackEntry?.arguments?.getString("conversationId")
        } else {
            null
        }
        LaunchedEffect(currentVisibleConversationId) {
            MessageNotificationCoordinator.setActiveConversationId(currentVisibleConversationId)
            if (!currentVisibleConversationId.isNullOrBlank()) {
                NotificationHelper.cancelConversationNotification(context, currentVisibleConversationId)
            }
        }

        if (isExpanded) {
            BackHandler(enabled = activeTab == Routes.CHATS && expandedSelectedChatId != null) {
                expandedSelectedChatId = null
            }
            BackHandler(enabled = activeTab == Routes.NOTIFICATION_HEALTH) {
                activeTab = Routes.PROFILE
            }
            BackHandler(enabled = activeTab != Routes.CHATS && activeTab != Routes.NOTIFICATION_HEALTH) {
                activeTab = Routes.CHATS
            }
            LaunchedEffect(pendingDeepLinkId) {
                val targetId = pendingDeepLinkId
                if (!targetId.isNullOrBlank()) {
                    expandedSelectedChatId = targetId
                    activeTab = Routes.CHATS
                    MessageNotificationCoordinator.consumeDeepLinkConversation()
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    PulseNavigationRail(
                        selectedRoute = if (activeTab == Routes.NOTIFICATION_HEALTH) Routes.PROFILE else activeTab,
                        totalUnread = totalUnread,
                        onSelectRoute = { activeTab = it }
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (activeTab == Routes.CHATS) {
                        Box(modifier = Modifier.width(380.dp).fillMaxHeight()) {
                            ChatsScreen(
                                currentUserId = currentUserId,
                                currentUserProfile = currentUserProfile,
                                conversationsState = conversationsState,
                                peopleState = peopleState,
                                searchQuery = searchQuery,
                                selectedFilter = chatFilter,
                                selectedConversationId = expandedSelectedChatId,
                                onSearchQueryChange = mainViewModel::updateSearchQuery,
                                onSelectFilter = mainViewModel::selectChatFilter,
                                onOpenConversation = { expandedSelectedChatId = it },
                                onStartDirectChat = { person ->
                                    mainViewModel.startDirectChat(person) { expandedSelectedChatId = it }
                                },
                                onCreateGroupChat = { title, topic, members ->
                                    mainViewModel.createGroupChat(title, topic, members) { expandedSelectedChatId = it }
                                },
                                onDeleteConversation = { conversation, onResult ->
                                    if (expandedSelectedChatId == conversation.id) {
                                        expandedSelectedChatId = null
                                    }
                                    mainViewModel.deleteOrLeaveConversation(conversation, onResult)
                                },
                                onProfileClick = { activeTab = Routes.PROFILE },
                                blockedUserIds = blockedUserIds,
                                callHistory = callHistory,
                                onTogglePinConversation = mainViewModel::togglePinConversation,
                                onToggleMuteConversation = mainViewModel::toggleMuteConversation,
                                onToggleMarkUnread = mainViewModel::toggleMarkUnread,
                                onStartCall = { target, isVideo ->
                                    launchWithCallPermissions(PendingCallAction.Outgoing(target, isVideo))
                                },
                                cloudSyncPhoneNumber = if (!hasFirebaseSession) phoneSession?.phoneNumberE164 else null,
                                onLinkGoogleAccount = if (!hasFirebaseSession) {
                                    {
                                        onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onAuthSuccess = {},
                                            onAuthError = { msg ->
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            },
                                            scope = coroutineScope
                                        )
                                    }
                                } else null,
                                viewerShowsOnline = viewerShowsOnline,
                                readReceiptsEnabled = readReceiptsEnabled,
                                isNetworkOnline = isNetworkOnline,
                                pendingOutboxCount = pendingOutboxCount
                            )
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val selectedId = expandedSelectedChatId
                            if (selectedId != null) {
                                key(selectedId) {
                                    val chatRoomVm: ChatRoomViewModel = viewModel(
                                        key = "${currentUserId}_$selectedId",
                                        factory = viewModelFactory {
                                            initializer {
                                                ChatRoomViewModel(
                                                    repository = appContainer.messagingRepository,
                                                    conversationId = selectedId,
                                                    currentUserId = currentUserId,
                                                    dao = appContainer.dao,
                                                    syncCoordinator = appContainer.syncCoordinator,
                                                    connectivityMonitor = appContainer.connectivityMonitor,
                                                    hasFirebaseAuth = hasFirebaseSession
                                                )
                                            }
                                        }
                                    )
                                    ChatRoomScreen(
                                        viewModel = chatRoomVm,
                                        currentUserProfile = currentUserProfile,
                                        allPeople = allPeople,
                                        readReceiptsEnabled = readReceiptsEnabled,
                                        viewerShowsOnline = viewerShowsOnline,
                                        showBackButton = false,
                                        onBack = { expandedSelectedChatId = null },
                                        onStartCall = { target, isVideo ->
                                            launchWithCallPermissions(PendingCallAction.Outgoing(target, isVideo))
                                        },
                                        allConversations = allConversations,
                                        blockedUserIds = blockedUserIds,
                                        onTogglePinConversation = mainViewModel::togglePinConversation,
                                        onToggleMuteConversation = mainViewModel::toggleMuteConversation,
                                        onToggleBlockUser = mainViewModel::setUserBlocked
                                    )
                                }
                            } else {
                                EmptyExpandedChatPane()
                            }
                        }
                    } else if (activeTab == Routes.PEOPLE) {
                        PeopleScreen(
                            currentUserId = currentUserId,
                            currentUserProfile = currentUserProfile,
                            peopleState = peopleState,
                            searchQuery = peopleSearchQuery,
                            onSearchQueryChange = mainViewModel::updatePeopleSearchQuery,
                            onMessagePerson = { person ->
                                mainViewModel.startDirectChat(person) { convId ->
                                    expandedSelectedChatId = convId
                                    activeTab = Routes.CHATS
                                }
                            },
                            onCallPerson = { person, isVideo ->
                                launchWithCallPermissions(
                                    PendingCallAction.Outgoing(
                                        target = CallTarget(
                                            userId = person.userId,
                                            displayName = person.displayName,
                                            photoUrl = person.photoUrl,
                                            conversationId = buildDirectConversationId(currentUserId, person.userId)
                                        ),
                                        isVideoCall = isVideo
                                    )
                                )
                            },
                            blockedUserIds = blockedUserIds,
                            viewerShowsOnline = viewerShowsOnline
                        )
                    } else if (activeTab == Routes.NOTIFICATION_HEALTH) {
                        NotificationHealthScreen(
                            hasFirebaseAuth = hasFirebaseSession,
                            notificationsEnabled = notificationsEnabled,
                            notificationPreviewEnabled = privateUser?.notificationPreviewEnabled ?: true,
                            onToggleNotificationPreview = { previewEnabled ->
                                mainViewModel.updatePrivacyPreferences(
                                    readReceipts = readReceiptsEnabled,
                                    notifications = notificationsEnabled,
                                    notificationPreviewEnabled = previewEnabled
                                )
                            },
                            onResyncFcmToken = { mainViewModel.syncFcmTokenNow(context) },
                            onBack = { activeTab = Routes.PROFILE }
                        )
                    } else {
                        ProfileScreen(
                            userProfile = currentUserProfile,
                            privateUser = privateUser,
                            isBusy = isBusy,
                            onSaveProfile = { name, handle, bio, vibe, online, discoverable ->
                                mainViewModel.saveProfile(name, handle, bio, vibe, online, discoverable)
                                if (!hasFirebaseSession) {
                                    coroutineScope.launch {
                                        PhoneSessionStore.updateLocalProfile(
                                            context, name, handle, bio, vibe, online, discoverable
                                        )
                                    }
                                }
                            },
                            onUpdatePrivacy = { readReceipts, notifications, previewEnabled ->
                                mainViewModel.updatePrivacyPreferences(
                                    readReceipts = readReceipts,
                                    notifications = notifications,
                                    notificationPreviewEnabled = previewEnabled
                                )
                            },
                            onSignOut = handleSignOut,
                            hasPhoneNotificationPermission = hasNotificationPermission,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            notificationHealthOk = notificationHealthOk,
                            onOpenNotificationHealth = { activeTab = Routes.NOTIFICATION_HEALTH },
                            blockedUserIds = blockedUserIds,
                            allPeople = allPeople,
                            onUnblockUser = { targetId -> mainViewModel.setUserBlocked(targetId, false) }
                        )
                    }
                }
            }
        } else {
            LaunchedEffect(pendingDeepLinkId) {
                val targetId = pendingDeepLinkId
                if (!targetId.isNullOrBlank()) {
                    navController.navigate(Routes.chatRoom(targetId)) {
                        launchSingleTop = true
                    }
                    MessageNotificationCoordinator.consumeDeepLinkConversation()
                }
            }
            val showTopLevelNav = currentRoute in setOf(Routes.CHATS, Routes.PEOPLE, Routes.PROFILE)
            val navigateTopLevel: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(Routes.CHATS) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showTopLevelNav && !isMedium) {
                        PulseBottomBar(
                            selectedRoute = currentRoute,
                            totalUnread = totalUnread,
                            onSelectRoute = navigateTopLevel
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showTopLevelNav && isMedium) {
                        PulseNavigationRail(
                            selectedRoute = currentRoute,
                            totalUnread = totalUnread,
                            onSelectRoute = navigateTopLevel
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Routes.CHATS,
                        enterTransition = { fadeIn(tween(150)) },
                        exitTransition = { fadeOut(tween(120)) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        composable(Routes.CHATS) {
                            ChatsScreen(
                                currentUserId = currentUserId,
                                currentUserProfile = currentUserProfile,
                                conversationsState = conversationsState,
                                peopleState = peopleState,
                                searchQuery = searchQuery,
                                selectedFilter = chatFilter,
                                selectedConversationId = null,
                                onSearchQueryChange = mainViewModel::updateSearchQuery,
                                onSelectFilter = mainViewModel::selectChatFilter,
                                onOpenConversation = { convId ->
                                    navController.navigate(Routes.chatRoom(convId))
                                },
                                onStartDirectChat = { person ->
                                    mainViewModel.startDirectChat(person) { convId ->
                                        navController.navigate(Routes.chatRoom(convId))
                                    }
                                },
                                onCreateGroupChat = { title, topic, members ->
                                    mainViewModel.createGroupChat(title, topic, members) { convId ->
                                        navController.navigate(Routes.chatRoom(convId))
                                    }
                                },
                                onDeleteConversation = { conversation, onResult ->
                                    mainViewModel.deleteOrLeaveConversation(conversation, onResult)
                                },
                                onProfileClick = { navigateTopLevel(Routes.PROFILE) },
                                blockedUserIds = blockedUserIds,
                                callHistory = callHistory,
                                onTogglePinConversation = mainViewModel::togglePinConversation,
                                onToggleMuteConversation = mainViewModel::toggleMuteConversation,
                                onToggleMarkUnread = mainViewModel::toggleMarkUnread,
                                onStartCall = { target, isVideo ->
                                    launchWithCallPermissions(PendingCallAction.Outgoing(target, isVideo))
                                },
                                cloudSyncPhoneNumber = if (!hasFirebaseSession) phoneSession?.phoneNumberE164 else null,
                                onLinkGoogleAccount = if (!hasFirebaseSession) {
                                    {
                                        onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onAuthSuccess = {},
                                            onAuthError = { msg ->
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            },
                                            scope = coroutineScope
                                        )
                                    }
                                } else null,
                                viewerShowsOnline = viewerShowsOnline,
                                readReceiptsEnabled = readReceiptsEnabled,
                                isNetworkOnline = isNetworkOnline,
                                pendingOutboxCount = pendingOutboxCount
                            )
                        }

                        composable(Routes.PEOPLE) {
                            PeopleScreen(
                                currentUserId = currentUserId,
                                currentUserProfile = currentUserProfile,
                                peopleState = peopleState,
                                searchQuery = peopleSearchQuery,
                                onSearchQueryChange = mainViewModel::updatePeopleSearchQuery,
                                onMessagePerson = { person ->
                                    mainViewModel.startDirectChat(person) { convId ->
                                        navController.navigate(Routes.chatRoom(convId))
                                    }
                                },
                                onCallPerson = { person, isVideo ->
                                    launchWithCallPermissions(
                                        PendingCallAction.Outgoing(
                                            target = CallTarget(
                                                userId = person.userId,
                                                displayName = person.displayName,
                                                photoUrl = person.photoUrl,
                                                conversationId = buildDirectConversationId(currentUserId, person.userId)
                                            ),
                                            isVideoCall = isVideo
                                        )
                                    )
                                },
                                blockedUserIds = blockedUserIds,
                                viewerShowsOnline = viewerShowsOnline
                            )
                        }

                        composable(Routes.PROFILE) {
                            ProfileScreen(
                                userProfile = currentUserProfile,
                                privateUser = privateUser,
                                isBusy = isBusy,
                                onSaveProfile = { name, handle, bio, vibe, online, discoverable ->
                                    mainViewModel.saveProfile(name, handle, bio, vibe, online, discoverable)
                                    if (!hasFirebaseSession) {
                                        coroutineScope.launch {
                                            PhoneSessionStore.updateLocalProfile(
                                                context, name, handle, bio, vibe, online, discoverable
                                            )
                                        }
                                    }
                                },
                                onUpdatePrivacy = { readReceipts, notifications, previewEnabled ->
                                    mainViewModel.updatePrivacyPreferences(
                                        readReceipts = readReceipts,
                                        notifications = notifications,
                                        notificationPreviewEnabled = previewEnabled
                                    )
                                },
                                onSignOut = handleSignOut,
                                hasPhoneNotificationPermission = hasNotificationPermission,
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                notificationHealthOk = notificationHealthOk,
                                onOpenNotificationHealth = {
                                    navController.navigate(Routes.NOTIFICATION_HEALTH) {
                                        launchSingleTop = true
                                    }
                                },
                                blockedUserIds = blockedUserIds,
                                allPeople = allPeople,
                                onUnblockUser = { targetId -> mainViewModel.setUserBlocked(targetId, false) }
                            )
                        }

                        composable(
                            route = Routes.NOTIFICATION_HEALTH,
                            enterTransition = {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) + fadeIn(tween(180))
                            },
                            popExitTransition = {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) + fadeOut(tween(160))
                            }
                        ) {
                            NotificationHealthScreen(
                                hasFirebaseAuth = hasFirebaseSession,
                                notificationsEnabled = notificationsEnabled,
                                notificationPreviewEnabled = privateUser?.notificationPreviewEnabled ?: true,
                                onToggleNotificationPreview = { previewEnabled ->
                                    mainViewModel.updatePrivacyPreferences(
                                        readReceipts = readReceiptsEnabled,
                                        notifications = notificationsEnabled,
                                        notificationPreviewEnabled = previewEnabled
                                    )
                                },
                                onResyncFcmToken = { mainViewModel.syncFcmTokenNow(context) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Routes.CHAT_ROOM,
                            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
                            enterTransition = {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(240)) + fadeIn(tween(200))
                            },
                            popExitTransition = {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) + fadeOut(tween(180))
                            }
                        ) { entry ->
                            val conversationId = entry.arguments?.getString("conversationId").orEmpty()
                            val chatRoomVm: ChatRoomViewModel = viewModel(
                                key = "${currentUserId}_$conversationId",
                                factory = viewModelFactory {
                                    initializer {
                                        ChatRoomViewModel(
                                            repository = appContainer.messagingRepository,
                                            conversationId = conversationId,
                                            currentUserId = currentUserId,
                                            dao = appContainer.dao,
                                            syncCoordinator = appContainer.syncCoordinator,
                                            connectivityMonitor = appContainer.connectivityMonitor,
                                            hasFirebaseAuth = hasFirebaseSession
                                        )
                                    }
                                }
                            )
                            ChatRoomScreen(
                                viewModel = chatRoomVm,
                                currentUserProfile = currentUserProfile,
                                allPeople = allPeople,
                                readReceiptsEnabled = readReceiptsEnabled,
                                viewerShowsOnline = viewerShowsOnline,
                                showBackButton = true,
                                onBack = { navController.popBackStack() },
                                onStartCall = { target, isVideo ->
                                    launchWithCallPermissions(PendingCallAction.Outgoing(target, isVideo))
                                },
                                allConversations = allConversations,
                                blockedUserIds = blockedUserIds,
                                onTogglePinConversation = mainViewModel::togglePinConversation,
                                onToggleMuteConversation = mainViewModel::toggleMuteConversation,
                                onToggleBlockUser = mainViewModel::setUserBlocked
                            )
                        }
                    }
                }
            }
        }

        CallHostOverlay(
            currentUserId = currentUserId,
            incomingCall = incomingCall,
            callState = callState,
            onAcceptIncoming = { session ->
                launchWithCallPermissions(PendingCallAction.Accept(session))
            },
            onDeclineIncoming = callViewModel::declineIncomingCall,
            onHangUp = callViewModel::hangUp,
            onToggleMute = callViewModel::toggleMute,
            onToggleSpeaker = callViewModel::toggleSpeaker,
            onToggleCamera = callViewModel::toggleCamera,
            onSwitchCamera = callViewModel::switchCamera,
            onCycleQuality = callViewModel::cycleVideoQualityPreset,
            onSetMinimized = callViewModel::setMinimized,
            onAttachLocalRenderer = callViewModel::attachLocalRenderer,
            onDetachLocalRenderer = callViewModel::detachLocalRenderer,
            onAttachRemoteRenderer = callViewModel::attachRemoteRenderer,
            onDetachRemoteRenderer = callViewModel::detachRemoteRenderer
        )

        IosInAppNotificationBanner(
            banner = activeBanner,
            onOpenConversation = { convId ->
                activeBanner = null
                MessageNotificationCoordinator.emitDeepLinkConversation(convId)
            },
            onDismiss = { activeBanner = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        callPermissionMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { callPermissionMessage = null },
                title = { Text("Permission needed for HD Calls") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { callPermissionMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

private sealed interface PendingCallAction {
    val needsCamera: Boolean

    data class Outgoing(val target: CallTarget, val isVideoCall: Boolean) : PendingCallAction {
        override val needsCamera: Boolean get() = isVideoCall
    }

    data class Accept(val session: CallSession) : PendingCallAction {
        override val needsCamera: Boolean get() = session.isVideoCall
    }
}

@Composable
private fun EmptyExpandedChatPane() {
    Box(modifier = Modifier.fillMaxSize()) {
        LiquidGlassBackdrop()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphismCard(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Select a conversation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pick a thread from the left to jump right in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PulseBottomBar(
    selectedRoute: String?,
    totalUnread: Long,
    onSelectRoute: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassmorphismCard(
            shape = CircleShape,
            highlighted = false,
            tintAlpha = if (isDark) 0.86f else 0.90f,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosDockItem(
                    selected = selectedRoute == Routes.CHATS,
                    label = "Chats",
                    icon = if (selectedRoute == Routes.CHATS) Icons.AutoMirrored.Rounded.Chat else Icons.AutoMirrored.Outlined.Chat,
                    badgeCount = totalUnread,
                    onClick = { onSelectRoute(Routes.CHATS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_chats")
                )
                IosDockItem(
                    selected = selectedRoute == Routes.PEOPLE,
                    label = "Contacts",
                    icon = if (selectedRoute == Routes.PEOPLE) Icons.Rounded.Groups else Icons.Outlined.Groups,
                    badgeCount = 0L,
                    onClick = { onSelectRoute(Routes.PEOPLE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_people")
                )
                IosDockItem(
                    selected = selectedRoute == Routes.PROFILE,
                    label = "Settings",
                    icon = if (selectedRoute == Routes.PROFILE) Icons.Rounded.Person else Icons.Outlined.Person,
                    badgeCount = 0L,
                    onClick = { onSelectRoute(Routes.PROFILE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_profile")
                )
            }
        }
    }
}

@Composable
private fun IosDockItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberIosHaptics()
    val isDark = isSystemInDarkTheme()
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
        } else {
            Color.Transparent
        },
        label = "dock_pill"
    )
    val tintColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dock_tint"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(pillColor)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = if (isDark) 0.28f else 0.90f),
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable {
                if (!selected) haptics.selectionTick()
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0L) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White
                    ) {
                        Text(if (badgeCount > 99L) "99+" else badgeCount.toString())
                    }
                }
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tintColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tintColor
        )
    }
}

@Composable
private fun PulseNavigationRail(
    selectedRoute: String?,
    totalUnread: Long,
    onSelectRoute: (String) -> Unit
) {
    val haptics = rememberIosHaptics()
    val railItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        NavigationRailItem(
            selected = selectedRoute == Routes.CHATS,
            onClick = {
                if (selectedRoute != Routes.CHATS) haptics.selectionTick()
                onSelectRoute(Routes.CHATS)
            },
            colors = railItemColors,
            icon = {
                BadgedBox(
                    badge = {
                        if (totalUnread > 0L) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = Color.White
                            ) {
                                Text(if (totalUnread > 99L) "99+" else totalUnread.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedRoute == Routes.CHATS) Icons.AutoMirrored.Rounded.Chat else Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = "Chats"
                    )
                }
            },
            label = { Text("Chats", fontWeight = FontWeight.SemiBold) }
        )
        NavigationRailItem(
            selected = selectedRoute == Routes.PEOPLE,
            onClick = {
                if (selectedRoute != Routes.PEOPLE) haptics.selectionTick()
                onSelectRoute(Routes.PEOPLE)
            },
            colors = railItemColors,
            icon = {
                Icon(
                    imageVector = if (selectedRoute == Routes.PEOPLE) Icons.Rounded.Groups else Icons.Outlined.Groups,
                    contentDescription = "Contacts"
                )
            },
            label = { Text("Contacts", fontWeight = FontWeight.SemiBold) }
        )
        NavigationRailItem(
            selected = selectedRoute == Routes.PROFILE,
            onClick = {
                if (selectedRoute != Routes.PROFILE) haptics.selectionTick()
                onSelectRoute(Routes.PROFILE)
            },
            colors = railItemColors,
            icon = {
                Icon(
                    imageVector = if (selectedRoute == Routes.PROFILE) Icons.Rounded.Person else Icons.Outlined.Person,
                    contentDescription = "Settings"
                )
            },
            label = { Text("Settings", fontWeight = FontWeight.SemiBold) }
        )
    }
}
