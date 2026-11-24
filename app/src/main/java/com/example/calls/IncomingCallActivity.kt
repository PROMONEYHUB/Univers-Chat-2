package com.example.calls

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.R
import com.example.data.model.CallSession
import com.example.data.repository.CallSignalingRepository
import com.example.notifications.NotificationHelper
import com.example.ui.components.GlassmorphismCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.DynamicIslandDarkSurface
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosGreen
import com.example.ui.theme.IosRed
import com.example.ui.theme.PulseChatTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

/**
 * Full-screen incoming call Activity shown over the lock screen via full-screen intent
 * when a high-priority FCM call push arrives while the app is closed or the device is locked.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenBehavior()

        val targetCallId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID)

        setContent {
            PulseChatTheme(darkTheme = true) {
                val ringingCall by IncomingCallCoordinator.ringingCall.collectAsStateWithLifecycle()
                val remainingSeconds by IncomingCallCoordinator.remainingSeconds.collectAsStateWithLifecycle()

                LaunchedEffect(ringingCall, targetCallId) {
                    val current = ringingCall
                    if (current == null || (!targetCallId.isNullOrBlank() && current.id != targetCallId)) {
                        finish()
                    }
                }

                val session = ringingCall
                if (session != null) {
                    BackHandler {
                        handleDecline(session)
                    }
                    LockScreenIncomingCallStage(
                        callSession = session,
                        remainingSeconds = remainingSeconds,
                        onAccept = { handleAccept(session) },
                        onDecline = { handleDecline(session) }
                    )
                }
            }
        }
    }

    private fun configureLockScreenBehavior() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun handleAccept(session: CallSession) {
        IncomingCallCoordinator.acceptFromIncomingUi(applicationContext, session)

        val launchMain = {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationHelper.EXTRA_ACCEPT_CALL_ID, session.id)
            }
            startActivity(mainIntent)
            finish()
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager?.isKeyguardLocked == true) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        launchMain()
                    }

                    override fun onDismissError() {
                        launchMain()
                    }

                    override fun onDismissCancelled() {
                        launchMain()
                    }
                }
            )
        } else {
            launchMain()
        }
    }

    private fun handleDecline(session: CallSession) {
        IncomingCallCoordinator.declineFromIncomingUi(applicationContext, session.id)
        lifecycleScope.launch {
            if (runCatching { Firebase.auth.currentUser }.getOrNull() != null) {
                runCatching {
                    CallSignalingRepository(applicationContext).declineCall(session.id)
                }
            }
        }
        finish()
    }
}

@Composable
private fun LockScreenIncomingCallStage(
    callSession: CallSession,
    remainingSeconds: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val haptics = rememberIosHaptics()
    val transition = rememberInfiniteTransition(label = "lock_call_pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_pulse_ring"
    )
    val backdropBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                AuroraViolet.copy(alpha = 0.25f),
                DynamicIslandDarkSurface,
                Color.Black
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdropBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                GlassmorphismCard(
                    shape = CircleShape,
                    containerColor = DynamicIslandDarkSurface,
                    tintAlpha = 0.72f,
                    borderColor = AuroraCyan.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (callSession.isVideoCall) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = null,
                            tint = AuroraCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                if (callSession.isVideoCall) R.string.incoming_video_call
                                else R.string.incoming_voice_call
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = "Auto-timeout in ${remainingSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(188.dp)) {
                    Box(
                        modifier = Modifier
                            .size(152.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(AuroraCyan.copy(alpha = 0.14f))
                            .border(1.dp, AuroraCyan.copy(alpha = 0.36f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(IosBlue.copy(alpha = 0.24f))
                    )
                    UserAvatar(
                        name = callSession.callerName.ifBlank { "Member" },
                        photoUrl = callSession.callerPhotoUrl,
                        size = 112.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = callSession.callerName.ifBlank { "Member" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (callSession.isVideoCall) {
                        "Ringing · Encrypted HD Video"
                    } else {
                        "Ringing · Encrypted HD Voice"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.border(0.7.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hd,
                            contentDescription = null,
                            tint = AuroraCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = callSession.qualityEnum().label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            GlassmorphismCard(
                shape = RoundedCornerShape(32.dp),
                containerColor = DynamicIslandDarkSurface,
                tintAlpha = 0.82f,
                borderColor = Color.White.copy(alpha = 0.24f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = IosRed,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptics.impactMedium()
                                    onDecline()
                                }
                                .testTag("lockscreen_decline_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CallEnd,
                                    contentDescription = stringResource(R.string.decline_call),
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.decline_call),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = IosGreen,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptics.success()
                                    onAccept()
                                }
                                .testTag("lockscreen_accept_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (callSession.isVideoCall) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                    contentDescription = stringResource(R.string.accept_call),
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.accept_call),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
