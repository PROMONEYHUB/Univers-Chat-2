package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notifications.FcmTokenDiagnosticState
import com.example.notifications.FcmTokenManager
import com.example.notifications.NotificationHelper
import com.example.ui.components.GlassmorphismCard
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosGreen
import com.example.ui.theme.IosIndigo
import com.example.ui.theme.IosOrange
import com.example.ui.theme.IosRed

data class NotificationHealthSnapshot(
    val hasNotificationPermission: Boolean,
    val messagesChannelEnabled: Boolean,
    val callsChannelEnabled: Boolean,
    val canUseFullScreenIntent: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val hasFcmToken: Boolean,
    val fcmSyncedWithBackend: Boolean,
    val fcmError: String?,
    val hasFirebaseAuth: Boolean
) {
    val issueCount: Int
        get() {
            var count = 0
            if (!hasNotificationPermission) count++
            if (!messagesChannelEnabled || !callsChannelEnabled) count++
            if (!canUseFullScreenIntent) count++
            if (!ignoringBatteryOptimizations) count++
            if (hasFirebaseAuth && !hasFcmToken) count++
            return count
        }

    val isAllHealthy: Boolean get() = issueCount == 0
}

fun evaluateNotificationHealth(
    context: Context,
    hasFirebaseAuth: Boolean,
    fcmState: FcmTokenDiagnosticState
): NotificationHealthSnapshot {
    val hasPermission = NotificationHelper.hasNotificationPermission(context)
    val messagesChannelOk = NotificationHelper.isChannelEnabled(context, NotificationHelper.CHANNEL_MESSAGES)
    val callsChannelOk = NotificationHelper.isChannelEnabled(context, NotificationHelper.CHANNEL_CALLS)
    val fullScreenOk = NotificationHelper.canUseFullScreenIntentCompat(context)
    val batteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else {
        true
    }
    val cachedToken = fcmState.currentToken ?: FcmTokenManager.getCachedToken(context)
    return NotificationHealthSnapshot(
        hasNotificationPermission = hasPermission,
        messagesChannelEnabled = messagesChannelOk,
        callsChannelEnabled = callsChannelOk,
        canUseFullScreenIntent = fullScreenOk,
        ignoringBatteryOptimizations = batteryUnrestricted,
        hasFcmToken = !cachedToken.isNullOrBlank(),
        fcmSyncedWithBackend = !fcmState.lastSyncedUserId.isNullOrBlank(),
        fcmError = fcmState.lastError,
        hasFirebaseAuth = hasFirebaseAuth
    )
}

@Composable
fun NotificationHealthScreen(
    hasFirebaseAuth: Boolean,
    notificationsEnabled: Boolean,
    notificationPreviewEnabled: Boolean,
    onToggleNotificationPreview: (Boolean) -> Unit,
    onResyncFcmToken: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberIosHaptics()
    val fcmState by FcmTokenManager.diagnosticState.collectAsStateWithLifecycle()

    var refreshTick by remember { mutableStateOf(0) }
    var testNotificationStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshTick += 1
    }

    val snapshot = remember(refreshTick, fcmState, hasFirebaseAuth) {
        evaluateNotificationHealth(context, hasFirebaseAuth, fcmState)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LiquidGlassBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        haptics.lightTap()
                        onBack()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("notification_health_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Settings"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification Health Check",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Diagnostics for background & lock-screen alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Overall Status Banner
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (snapshot.isAllHealthy) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notification_health_summary_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (snapshot.isAllHealthy) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (snapshot.isAllHealthy) IosGreen else IosOrange,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (snapshot.isAllHealthy) {
                                "Ready for Background Delivery"
                            } else {
                                "${snapshot.issueCount} check${if (snapshot.issueCount == 1) "" else "s"} need attention"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!notificationsEnabled) {
                                "Note: In-app Real-Time Phone Alerts toggle is currently turned off in Settings."
                            } else if (snapshot.isAllHealthy) {
                                "Permissions, channels, full-screen calls, battery & device token look good."
                            } else {
                                "Fix the highlighted items below so messages & calls wake your phone reliably."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. POST_NOTIFICATIONS Permission
            HealthCheckCard(
                icon = Icons.Rounded.NotificationsActive,
                badgeColor = IosBlue,
                title = "Notification Permission",
                statusLabel = if (snapshot.hasNotificationPermission) "Allowed" else "Blocked",
                isHealthy = snapshot.hasNotificationPermission,
                description = if (snapshot.hasNotificationPermission) {
                    "Univers Chat can post message banners and incoming call alerts."
                } else {
                    "Android is blocking all notifications. Grant permission or open system settings."
                },
                actionLabel = if (snapshot.hasNotificationPermission) "App Notification Settings" else "Allow Notifications",
                actionTestTag = "health_fix_permission_button",
                onAction = {
                    if (!snapshot.hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings(context)
                    }
                }
            )

            // 2. Notification Channels
            val channelsHealthy = snapshot.messagesChannelEnabled && snapshot.callsChannelEnabled
            HealthCheckCard(
                icon = Icons.Rounded.NotificationsNone,
                badgeColor = IosIndigo,
                title = "Message & Call Channels",
                statusLabel = if (channelsHealthy) "Enabled" else "Silenced",
                isHealthy = channelsHealthy,
                description = "Messages: ${if (snapshot.messagesChannelEnabled) "High priority" else "Off"} · " +
                    "Calls: ${if (snapshot.callsChannelEnabled) "High priority" else "Off"}",
                actionLabel = "Configure Channels",
                actionTestTag = "health_fix_channels_button",
                onAction = {
                    NotificationHelper.ensureChannels(context)
                    openAppNotificationSettings(context)
                }
            )

            // 3. Full-Screen Incoming Call Intent (Android 14+)
            HealthCheckCard(
                icon = Icons.Rounded.Call,
                badgeColor = IosGreen,
                title = "Lock-Screen Full-Screen Calls",
                statusLabel = if (snapshot.canUseFullScreenIntent) "Enabled" else "Restricted",
                isHealthy = snapshot.canUseFullScreenIntent,
                description = if (snapshot.canUseFullScreenIntent) {
                    "Incoming calls can wake the screen and show full-screen Accept/Decline controls."
                } else {
                    "Android 14+ revoked Full-Screen Intent permission. Enable it so calls ring over the lock screen."
                },
                actionLabel = "Full-Screen Call Settings",
                actionTestTag = "health_fix_fullscreen_button",
                onAction = { openFullScreenIntentSettings(context) }
            )

            // 4. Battery Optimization
            HealthCheckCard(
                icon = Icons.Rounded.BatteryChargingFull,
                badgeColor = IosOrange,
                title = "Battery Optimization",
                statusLabel = if (snapshot.ignoringBatteryOptimizations) "Unrestricted" else "Optimized (May Delay)",
                isHealthy = snapshot.ignoringBatteryOptimizations,
                description = if (snapshot.ignoringBatteryOptimizations) {
                    "Excluded from aggressive Doze throttling for timely background calls & messages."
                } else {
                    "OEM battery savers can delay or suppress killed-state pushes. Set Univers Chat to Unrestricted."
                },
                actionLabel = "Battery Settings",
                actionTestTag = "health_fix_battery_button",
                onAction = { openBatteryOptimizationSettings(context) }
            )

            // 5. FCM Token & Cloud Push Backend Status
            val tokenHealthy = !hasFirebaseAuth || (snapshot.hasFcmToken && snapshot.fcmSyncedWithBackend)
            val tokenPreview = (fcmState.currentToken ?: FcmTokenManager.getCachedToken(context))
                ?.let { tok -> if (tok.length > 18) "${tok.take(10)}…${tok.takeLast(6)}" else tok }
            HealthCheckCard(
                icon = Icons.Rounded.CloudDone,
                badgeColor = if (tokenHealthy) IosGreen else IosRed,
                title = "Device Push Token (FCM)",
                statusLabel = when {
                    !hasFirebaseAuth -> "Cloud Sign-In Needed"
                    snapshot.fcmSyncedWithBackend && snapshot.hasFcmToken -> "Synced"
                    snapshot.hasFcmToken -> "Cached Locally"
                    else -> "Missing"
                },
                isHealthy = tokenHealthy,
                description = buildString {
                    if (!hasFirebaseAuth) {
                        append("Link Google Sign-In so Firestore & FCM can register this device token.")
                    } else {
                        append("Device ID: ${fcmState.deviceId.ifBlank { FcmTokenManager.getOrCreateDeviceId(context) }}")
                        if (tokenPreview != null) append(" · Token: $tokenPreview")
                        if (!snapshot.fcmError.isNullOrBlank()) append("\nError: ${snapshot.fcmError}")
                    }
                },
                actionLabel = "Re-sync Push Token",
                actionTestTag = "health_resync_token_button",
                onAction = {
                    onResyncFcmToken()
                    refreshTick += 1
                }
            )

            // Privacy Preview Toggle & Local Test Trigger
            GlassmorphismCard(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IosIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Show Message Previews",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "When off, notifications show \"New message\" without text",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationPreviewEnabled,
                            onCheckedChange = {
                                haptics.selectionTick()
                                onToggleNotificationPreview(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IosGreen
                            ),
                            modifier = Modifier.testTag("health_preview_toggle")
                        )
                    }

                    Button(
                        onClick = {
                            haptics.success()
                            NotificationHelper.ensureChannels(context)
                            if (!NotificationHelper.hasNotificationPermission(context)) {
                                testNotificationStatus = "Grant Notification Permission first."
                            } else {
                                val sampleBody = if (notificationPreviewEnabled) {
                                    "Test alert: Background notifications & inline reply are working."
                                } else {
                                    "New message"
                                }
                                NotificationHelper.showIncomingMessageNotificationFromPush(
                                    context = context,
                                    conversationId = "health_check_test_thread",
                                    isGroup = false,
                                    conversationTitle = "Univers Chat Test",
                                    senderName = "Univers Chat",
                                    messageText = sampleBody,
                                    timestampMillis = System.currentTimeMillis()
                                )
                                testNotificationStatus = "Test notification posted! Check your shade."
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("health_test_notification_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Test Notification", fontWeight = FontWeight.Bold)
                    }

                    testNotificationStatus?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Honest Backend & OEM Platform Note
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Killed-state & Doze push delivery requires Cloud Functions (functions/index.js) " +
                            "to be deployed on your Firebase project (Blaze plan) and Google Play Services on the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = { refreshTick += 1 },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("health_refresh_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh Diagnostics", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }
}

@Composable
private fun HealthCheckCard(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    statusLabel: String,
    isHealthy: Boolean,
    description: String,
    actionLabel: String,
    actionTestTag: String,
    onAction: () -> Unit
) {
    GlassmorphismCard(
        shape = RoundedCornerShape(20.dp),
        highlighted = !isHealthy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = if (isHealthy) IosGreen.copy(alpha = 0.16f) else IosOrange.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHealthy) IosGreen else IosOrange,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onAction,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(actionTestTag)
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openFullScreenIntentSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { openAppNotificationSettings(context) }
}

private fun openBatteryOptimizationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { openAppNotificationSettings(context) }
}
