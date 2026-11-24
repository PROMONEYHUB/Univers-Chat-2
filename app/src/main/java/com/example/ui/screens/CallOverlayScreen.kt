package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.CallQualityPreset
import com.example.data.model.CallSession
import com.example.ui.components.GlassmorphismCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.rememberIosHaptics
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.DynamicIslandDarkBorder
import com.example.ui.theme.DynamicIslandDarkSurface
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosGreen
import com.example.ui.theme.IosRed
import com.example.ui.viewmodel.ActiveCallUiState
import com.example.ui.viewmodel.CallConnectionPhase
import org.webrtc.SurfaceViewRenderer
import java.util.Locale

@Composable
fun CallHostOverlay(
    currentUserId: String,
    incomingCall: CallSession?,
    callState: ActiveCallUiState,
    onAcceptIncoming: (CallSession) -> Unit,
    onDeclineIncoming: (CallSession) -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onCycleQuality: () -> Unit,
    onSetMinimized: (Boolean) -> Unit,
    onAttachLocalRenderer: (SurfaceViewRenderer) -> Unit,
    onDetachLocalRenderer: (SurfaceViewRenderer) -> Unit,
    onAttachRemoteRenderer: (SurfaceViewRenderer) -> Unit,
    onDetachRemoteRenderer: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSession = callState.session

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Fullscreen HD Video / Voice Call Stage
        AnimatedVisibility(
            visible = activeSession != null && !callState.isMinimized,
            enter = fadeIn(tween(220)) + slideInVertically { it / 8 },
            exit = fadeOut(tween(180)) + slideOutVertically { it / 8 }
        ) {
            if (activeSession != null) {
                BackHandler {
                    onSetMinimized(true)
                }
                ActiveCallFullScreen(
                    currentUserId = currentUserId,
                    session = activeSession,
                    callState = callState,
                    onHangUp = onHangUp,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onToggleCamera = onToggleCamera,
                    onSwitchCamera = onSwitchCamera,
                    onCycleQuality = onCycleQuality,
                    onMinimize = { onSetMinimized(true) },
                    onAttachLocalRenderer = onAttachLocalRenderer,
                    onDetachLocalRenderer = onDetachLocalRenderer,
                    onAttachRemoteRenderer = onAttachRemoteRenderer,
                    onDetachRemoteRenderer = onDetachRemoteRenderer
                )
            }
        }

        // 2. Minimized Floating Dynamic Island Pill
        AnimatedVisibility(
            visible = activeSession != null && callState.isMinimized,
            enter = fadeIn(tween(180)) + slideInVertically { -it },
            exit = fadeOut(tween(150)) + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            if (activeSession != null) {
                MinimizedCallIslandBar(
                    currentUserId = currentUserId,
                    session = activeSession,
                    callState = callState,
                    onExpand = { onSetMinimized(false) },
                    onHangUp = onHangUp
                )
            }
        }

        // 3. Incoming Ringing Call Glass Sheet
        AnimatedVisibility(
            visible = incomingCall != null && activeSession == null,
            enter = fadeIn(tween(220)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically { -it / 2 }
        ) {
            if (incomingCall != null) {
                IncomingCallFullOverlay(
                    callSession = incomingCall,
                    onAccept = { onAcceptIncoming(incomingCall) },
                    onDecline = { onDeclineIncoming(incomingCall) }
                )
            }
        }
    }
}

@Composable
private fun ActiveCallFullScreen(
    currentUserId: String,
    session: CallSession,
    callState: ActiveCallUiState,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onCycleQuality: () -> Unit,
    onMinimize: () -> Unit,
    onAttachLocalRenderer: (SurfaceViewRenderer) -> Unit,
    onDetachLocalRenderer: (SurfaceViewRenderer) -> Unit,
    onAttachRemoteRenderer: (SurfaceViewRenderer) -> Unit,
    onDetachRemoteRenderer: (SurfaceViewRenderer) -> Unit
) {
    val haptics = rememberIosHaptics()
    val peerName = session.peerNameFor(currentUserId)
    val peerPhoto = session.peerPhotoUrlFor(currentUserId)
    val isVideo = session.isVideoCall

    val stageGradient = remember {
        Brush.radialGradient(
            colors = listOf(
                AuroraViolet.copy(alpha = 0.28f),
                DynamicIslandDarkSurface,
                Color.Black
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Remote Stream or HD Voice Stage
        if (isVideo) {
            RemoteVideoRendererSurface(
                onAttach = onAttachRemoteRenderer,
                onDetach = onDetachRemoteRenderer,
                modifier = Modifier.fillMaxSize()
            )
            if (!callState.hasRemoteVideo || callState.phase != CallConnectionPhase.CONNECTED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(stageGradient),
                    contentAlignment = Alignment.Center
                ) {
                    PulsingAvatarCallStage(
                        name = peerName,
                        photoUrl = peerPhoto,
                        statusLine = callState.phase.label,
                        qualityLabel = callState.qualityPreset.label
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(stageGradient),
                contentAlignment = Alignment.Center
            ) {
                PulsingAvatarCallStage(
                    name = peerName,
                    photoUrl = peerPhoto,
                    statusLine = if (callState.phase == CallConnectionPhase.CONNECTED) {
                        formatCallDuration(callState.durationSeconds)
                    } else {
                        callState.phase.label
                    },
                    qualityLabel = CallQualityPreset.HD_VOICE.label
                )
            }
        }

        // Top Frosted Call HUD
        GlassmorphismCard(
            shape = RoundedCornerShape(24.dp),
            containerColor = DynamicIslandDarkSurface,
            tintAlpha = 0.72f,
            borderColor = Color.White.copy(alpha = 0.24f),
            blurRadius = 18.dp,
            highlighted = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptics.lightTap()
                        onMinimize()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .testTag("call_minimize_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = "Minimize call",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (callState.phase == CallConnectionPhase.CONNECTED) IosGreen
                                    else AuroraCyan
                                )
                        )
                        Text(
                            text = if (callState.phase == CallConnectionPhase.CONNECTED) {
                                "${callState.phase.label} · ${formatCallDuration(callState.durationSeconds)}"
                            } else {
                                callState.phase.label
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                // HD Quality Badge / Switcher (720p HD <-> 1080p FHD on video, HD Opus on voice)
                Surface(
                    shape = CircleShape,
                    color = IosBlue.copy(alpha = 0.28f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(0.8.dp, AuroraCyan.copy(alpha = 0.6f), CircleShape)
                        .then(
                            if (isVideo) {
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable {
                                        haptics.selectionTick()
                                        onCycleQuality()
                                    }
                            } else Modifier
                        )
                        .testTag("call_quality_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hd,
                            contentDescription = "HD quality stream",
                            tint = AuroraCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isVideo) callState.qualityPreset.shortBadge else CallQualityPreset.HD_VOICE.shortBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Local Self-View PIP Card (Video Calls)
        if (isVideo && callState.isCameraEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 128.dp)
                    .size(width = 114.dp, height = 162.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DynamicIslandDarkSurface)
                    .border(
                        width = 1.2.dp,
                        color = Color.White.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                LocalVideoRendererSurface(
                    onAttach = onAttachLocalRenderer,
                    onDetach = onDetachLocalRenderer,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptics.selectionTick()
                            onSwitchCamera()
                        }
                        .testTag("call_pip_flip_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = "Flip camera",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Frosted Control Capsule
        GlassmorphismCard(
            shape = RoundedCornerShape(32.dp),
            containerColor = DynamicIslandDarkSurface,
            tintAlpha = 0.78f,
            borderColor = Color.White.copy(alpha = 0.24f),
            blurRadius = 22.dp,
            highlighted = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlCircleButton(
                    icon = if (callState.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (callState.isMuted) "Unmute" else "Mute",
                    active = callState.isMuted,
                    onClick = {
                        haptics.selectionTick()
                        onToggleMute()
                    },
                    testTag = "call_mute_button"
                )

                CallControlCircleButton(
                    icon = if (callState.isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    label = if (callState.isSpeakerOn) "Speaker" else "Earpiece",
                    active = callState.isSpeakerOn,
                    onClick = {
                        haptics.selectionTick()
                        onToggleSpeaker()
                    },
                    testTag = "call_speaker_button"
                )

                if (isVideo) {
                    CallControlCircleButton(
                        icon = if (callState.isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        label = if (callState.isCameraEnabled) "Cam On" else "Cam Off",
                        active = !callState.isCameraEnabled,
                        onClick = {
                            haptics.selectionTick()
                            onToggleCamera()
                        },
                        testTag = "call_camera_button"
                    )

                    CallControlCircleButton(
                        icon = Icons.Rounded.Cameraswitch,
                        label = "Flip",
                        active = false,
                        onClick = {
                            haptics.selectionTick()
                            onSwitchCamera()
                        },
                        testTag = "call_flip_camera_button"
                    )
                }

                CallControlCircleButton(
                    icon = Icons.Rounded.CallEnd,
                    label = "End",
                    active = false,
                    containerColor = IosRed,
                    iconTint = Color.White,
                    onClick = {
                        haptics.impactMedium()
                        onHangUp()
                    },
                    testTag = "call_end_button"
                )
            }
        }
    }
}

@Composable
private fun IncomingCallFullOverlay(
    callSession: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val haptics = rememberIosHaptics()
    val backdropBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
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
            // Top badge
            GlassmorphismCard(
                shape = CircleShape,
                containerColor = DynamicIslandDarkSurface,
                tintAlpha = 0.7f,
                borderColor = AuroraCyan.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 12.dp)
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

            // Center pulsing caller card
            PulsingAvatarCallStage(
                name = callSession.callerName.ifBlank { "Member" },
                photoUrl = callSession.callerPhotoUrl,
                statusLine = if (callSession.isVideoCall) "Ringing · Encrypted HD Video" else "Ringing · Encrypted HD Voice",
                qualityLabel = callSession.qualityEnum().label
            )

            // Bottom Accept / Decline dock
            GlassmorphismCard(
                shape = RoundedCornerShape(32.dp),
                containerColor = DynamicIslandDarkSurface,
                tintAlpha = 0.78f,
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
                                .testTag("incoming_call_decline_button")
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
                                .testTag("incoming_call_accept_button")
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

@Composable
private fun MinimizedCallIslandBar(
    currentUserId: String,
    session: CallSession,
    callState: ActiveCallUiState,
    onExpand: () -> Unit,
    onHangUp: () -> Unit
) {
    val haptics = rememberIosHaptics()
    GlassmorphismCard(
        shape = CircleShape,
        containerColor = DynamicIslandDarkSurface,
        tintAlpha = 0.88f,
        borderColor = DynamicIslandDarkBorder,
        highlighted = true,
        onClick = {
            haptics.lightTap()
            onExpand()
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("minimized_call_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = session.peerNameFor(currentUserId),
                photoUrl = session.peerPhotoUrlFor(currentUserId),
                size = 38.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.peerNameFor(currentUserId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (callState.phase == CallConnectionPhase.CONNECTED) {
                        "${callState.qualityPreset.shortBadge} · ${formatCallDuration(callState.durationSeconds)} · Tap to return"
                    } else {
                        "${callState.phase.label} · Tap to expand"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = CircleShape,
                color = IosRed,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptics.impactMedium()
                        onHangUp()
                    }
                    .testTag("minimized_call_hangup_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.end_call),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingAvatarCallStage(
    name: String,
    photoUrl: String?,
    statusLine: String,
    qualityLabel: String
) {
    val transition = rememberInfiniteTransition(label = "call_pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_ring"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(AuroraCyan.copy(alpha = 0.14f))
                    .border(1.dp, AuroraCyan.copy(alpha = 0.35f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(122.dp)
                    .clip(CircleShape)
                    .background(IosBlue.copy(alpha = 0.22f))
            )
            UserAvatar(
                name = name,
                photoUrl = photoUrl,
                size = 104.dp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = statusLine,
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
                    text = qualityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CallControlCircleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    containerColor: Color = if (active) Color.White else Color.White.copy(alpha = 0.16f),
    iconTint: Color = if (active) DynamicIslandDarkSurface else Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(0.8.dp, Color.White.copy(alpha = 0.26f), CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1
        )
    }
}

@Composable
private fun RemoteVideoRendererSurface(
    onAttach: (SurfaceViewRenderer) -> Unit,
    onDetach: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context) }

    DisposableEffect(renderer) {
        onAttach(renderer)
        onDispose {
            onDetach(renderer)
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

@Composable
private fun LocalVideoRendererSurface(
    onAttach: (SurfaceViewRenderer) -> Unit,
    onDetach: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context) }

    DisposableEffect(renderer) {
        onAttach(renderer)
        onDispose {
            onDetach(renderer)
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

fun formatCallDuration(totalSeconds: Long): String {
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
