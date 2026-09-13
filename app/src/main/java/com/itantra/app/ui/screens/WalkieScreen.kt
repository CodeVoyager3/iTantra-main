package com.itantra.app.ui.screens

import com.itantra.app.ui.components.BatteryIndicator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.model.PeerDevice
import com.itantra.app.model.TransportProtocol
import com.itantra.app.ui.theme.AccentBlue
import com.itantra.app.ui.theme.AccentBlueContainer
import com.itantra.app.ui.theme.BadgeMintContainer
import com.itantra.app.ui.theme.BadgeMintText
import com.itantra.app.ui.theme.LightOutline
import com.itantra.app.ui.theme.MeshGreen
import com.itantra.app.ui.theme.MeshGreenContainer
import com.itantra.app.ui.theme.MeshGreenText
import com.itantra.app.ui.theme.MinimalColorsInstance
import com.itantra.app.ui.theme.SosRed
import com.itantra.app.ui.theme.SosRedContainer
import com.itantra.app.ui.theme.SosRedDark
import com.itantra.app.ui.theme.minimalColors
import com.itantra.app.viewmodel.MissionControlViewModel

/**
 * World-Class Modern, Minimalistic Walkie-Talkie Screen:
 * - High-End Mission Telemetry Header matching the SOS page
 * - Standby Mode: Prominent, highlighted hero transceiver dome with channel selection & clean layout
 * - Active Mode: Transforms into a high-end call & intercom console with:
 *     - Mic Mute / Unmute button with visual badge
 *     - PTT / Voice Transmit Trigger with live equalizer audio spectrum
 *     - Speakerphone / Audio output toggle
 *     - Call-Like Red "End / Disconnect" button (replaces basic toggle)
 * - Revealed Controls on Activation:
 *     - Paired Team Radios with signal, battery & unpair
 *     - Available Nearby Nodes with interactive, animated Refresh/Rescan button
 */
@Composable
fun WalkieScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.minimalColors
    val isWalkieActive by viewModel.isWalkieActive.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isTransmitting by viewModel.isTransmitting.collectAsState()
    val isRefreshingNodes by viewModel.isRefreshingNodes.collectAsState()
    val isSpeakerphoneOn by viewModel.isSpeakerphoneOn.collectAsState()
    val pairedDevices by viewModel.pairedWalkieDevices.collectAsState()
    val discoveredDevices by viewModel.discoveredWalkieDevices.collectAsState()
    val isVadSpeaking by viewModel.isVadSpeaking.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val isWalkieLinkActive by viewModel.isWalkieLinkActive.collectAsState()
    val isReceivingAudio by viewModel.isReceivingAudio.collectAsState()
    val remoteAudioLevel by viewModel.remoteAudioLevel.collectAsState()

    // Real audio levels only: local mic while transmitting, received frame
    // RMS while a peer is talking. Nothing is synthesised for display.
    val liveAudioLevel = if (isReceivingAudio) {
        remoteAudioLevel
    } else if (isTransmitting || isVadSpeaking) {
        audioLevel
    } else {
        0f
    }
    val connectedPairedCount = pairedDevices.count { it.isConnected }

    val scrollState = rememberScrollState()

    // Breathing radar aura transition
    val infiniteTransition = rememberInfiniteTransition(label = "walkiePulse")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isWalkieActive) 1.25f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWalkieActive) 800 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = if (isWalkieActive) 0.35f else 0.12f,
        targetValue = if (isWalkieActive) 0.85f else 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWalkieActive) 800 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    // Animated rotation for refresh button
    val refreshRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshSpin"
    )

    // Tactile button depression
    val buttonInteractionSource = remember { MutableInteractionSource() }
    val isPressed by buttonInteractionSource.collectIsPressedAsState()
    val buttonPressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "buttonPressScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =======================================================
        // 1. HIGH-END MISSION TELEMETRY HEADER
        // =======================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Brand & Mode Identity
            Box(
                modifier = Modifier
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), spotColor = Color(0x08000000))
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isWalkieActive) MeshGreen else AccentBlue)
                    )
                    Text(
                        text = "iTANTRA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = colors.textPrimary
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(10.dp)
                            .background(colors.outline)
                    )
                    Text(
                        text = "WALKIE MESH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // Right: Dynamic System Status Capsule
            Box(
                modifier = Modifier
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), spotColor = Color(0x08000000))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isWalkieActive) colors.badgeMintContainer else colors.surface)
                    .border(
                        width = 1.dp,
                        color = if (isWalkieActive) colors.badgeMintText else colors.outline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(if (isWalkieActive) ringScale else 1f)
                            .clip(CircleShape)
                            .background(if (isWalkieActive) MeshGreen else colors.textSecondary)
                    )
                    Text(
                        text = if (isWalkieActive) "LIVE TEAM COMMS" else "RADIO STANDBY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = if (isWalkieActive) colors.badgeMintText else colors.textSecondary
                    )
                }
            }
        }

        // =======================================================
        // 2. STANDBY HERO (When Walkie is Inactive)
        // =======================================================
        if (!isWalkieActive) {
            Box(
                modifier = Modifier
                    .size(246.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outermost soft breathing pulse halo
                Box(
                    modifier = Modifier
                        .size(244.dp)
                        .scale(ringScale)
                        .alpha(ringAlpha)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0x3038BDF8), Color(0x100284C7), Color.Transparent)
                            )
                        )
                )

                // Middle radar reference ring with cardinal markers
                Box(
                    modifier = Modifier
                        .size(212.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0x2838BDF8), CircleShape)
                )

                // 4 Cardinal Micro-Ticks
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(width = 2.dp, height = 6.dp).background(Color(0xFF94A3B8), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(width = 2.dp, height = 6.dp).background(Color(0xFF94A3B8), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(width = 6.dp, height = 2.dp).background(Color(0xFF94A3B8), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(width = 6.dp, height = 2.dp).background(Color(0xFF94A3B8), RoundedCornerShape(1.dp)))

                // Highlighted Hero Tactile Button (Major Action)
                Surface(
                    onClick = { viewModel.toggleWalkieMaster(true) },
                    shape = CircleShape,
                    color = Color.Transparent,
                    interactionSource = buttonInteractionSource,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .size(178.dp)
                        .scale(buttonPressScale)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF38BDF8),
                                        Color(0xFF2563EB),
                                        Color(0xFF1D4ED8),
                                        Color(0xFF0F172A)
                                    )
                                )
                            )
                            .border(
                                width = 3.dp,
                                brush = Brush.verticalGradient(
                                    listOf(Color(0x99FFFFFF), Color(0x25FFFFFF))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.95f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "TRANSCEIVER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.6.sp,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "WALKIE",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "TAP TO ACTIVATE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reassurance & Instructions Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.cardSecondaryBg)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap to join off-grid team mesh • Automatically reconnects to remembered radios in range",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Quick Capabilities Row (Human-friendly, zero jargon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("100% Offline", "No Internet Needed", "Hands-Free Auto Voice").forEach { feature ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.cardSecondaryBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = feature,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        // =======================================================
        // 3. ACTIVE MODE: CALL-STYLE CONSOLE & CONTROLS
        // =======================================================
        if (isWalkieActive) {
            // Hero Voice Comms Stage Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0x0C000000))
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top Room Info Row (No technical jargon like 5.180 GHz or AES-256)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentBlueContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "TEAM VOICE ROOM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentBlue
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isWalkieLinkActive) BadgeMintContainer else colors.cardSecondaryBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isWalkieLinkActive) Color(0xFF059669) else colors.textSecondary)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isWalkieLinkActive) "DIRECT LINK ACTIVE" else "SEARCHING FOR TEAM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWalkieLinkActive) BadgeMintText else colors.textSecondary
                                )
                            }
                        }
                    }

                    // Voice Equalizer / PTT Central Disc
                    val isLiveTx = (isTransmitting || isVadSpeaking) && !isMicMuted
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Concentric expanding soundwave rings when speaking/transmitting
                        if (isLiveTx || isReceivingAudio) {
                            Box(
                                modifier = Modifier
                                    .size(136.dp)
                                    .scale(ringScale)
                                    .alpha(ringAlpha)
                                    .clip(CircleShape)
                                    .background(
                                        if (isReceivingAudio) AccentBlue.copy(alpha = 0.22f)
                                        else MeshGreen.copy(alpha = 0.25f)
                                    )
                            )
                        }

                        // Central Disc
                        Box(
                            modifier = Modifier
                                .size(108.dp)
                                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = AccentBlue.copy(alpha = 0.3f))
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isMicMuted -> Brush.radialGradient(listOf(Color(0xFFFEF2F2), Color(0xFFFCA5A5)))
                                        isLiveTx -> Brush.radialGradient(listOf(MeshGreen, Color(0xFF047857)))
                                        isReceivingAudio -> Brush.radialGradient(listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8)))
                                        else -> Brush.radialGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)))
                                    }
                                )
                                .border(
                                    width = 2.5.dp,
                                    color = when {
                                        isMicMuted -> SosRed
                                        isLiveTx -> Color(0xFFA7F3D0)
                                        isReceivingAudio -> Color(0xFFBAE6FD)
                                        else -> Color.White
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isMicMuted -> Icons.Default.MicOff
                                    isLiveTx -> Icons.Default.GraphicEq
                                    isReceivingAudio -> Icons.AutoMirrored.Filled.VolumeUp
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = if (isMicMuted) SosRedDark else Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Dynamic Transmission Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isMicMuted -> Color(0xFFFEE2E2)
                                    isReceivingAudio -> Color(0xFFEFF6FF)
                                    isLiveTx -> Color(0xFFECFDF5)
                                    else -> Color(0xFFEFF6FF)
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = when {
                                isMicMuted -> "MIC MUTED • TAP UNMUTE TO SPEAK"
                                isReceivingAudio -> "RECEIVING LIVE VOICE..."
                                isLiveTx -> "TRANSMITTING LIVE VOICE + TEXT"
                                else -> "AUTO-VOICE ACTIVE • SPEAK FREELY"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isMicMuted -> SosRedDark
                                isReceivingAudio -> AccentBlue
                                isLiveTx -> MeshGreenText
                                else -> AccentBlue
                            }
                        )
                    }

                    // Live 24-Bar Equalizer Audio Spectrum Visualizer.
                    // Bar heights are driven purely by the measured audio level
                    // (mic while transmitting, received frame RMS while a peer
                    // speaks) — there is no decorative animation.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(24) { index ->
                            val centreDistance = kotlin.math.abs(index - 11.5f) / 11.5f
                            val weight = 1f - centreDistance * 0.7f
                            val heightFraction = when {
                                isMicMuted -> 0.08f
                                isReceivingAudio || isLiveTx -> (0.06f + 0.94f * liveAudioLevel * weight).coerceIn(0.06f, 1f)
                                else -> 0.08f
                            }

                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height((26.dp * heightFraction).coerceAtLeast(3.dp))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            isMicMuted -> Color(0xFFE2E8F0)
                                            isReceivingAudio -> AccentBlue
                                            isLiveTx -> MeshGreen
                                            else -> AccentBlue.copy(alpha = 0.35f)
                                        }
                                    )
                            )
                        }
                    }

                    HorizontalDivider(color = colors.outline, thickness = 1.dp)

                    // =======================================================
                    // CLEAN HANDS-FREE CALL CONTROLS
                    // (1. Mute/Unmute Mic, 2. Speaker/Earpiece, 3. Disconnect)
                    // Voice is automatically detected by VAD and sent as text!
                    // =======================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Mic Mute / Unmute Button (Primary control for hands-free VAD)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { viewModel.toggleMicMute() },
                                shape = CircleShape,
                                color = if (isMicMuted) colors.errorContainer else colors.cardSecondaryBg,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(58.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Mute Mic",
                                        tint = if (isMicMuted) colors.error else colors.textPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isMicMuted) "Unmute" else "Mute Mic",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMicMuted) colors.error else colors.textSecondary
                            )
                        }

                        // 2. Speakerphone Toggle Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { viewModel.toggleSpeakerphone() },
                                shape = CircleShape,
                                color = if (isSpeakerphoneOn) colors.badgeBlueContainer else colors.cardSecondaryBg,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(58.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSpeakerphoneOn) Icons.Default.VolumeUp else Icons.Default.Hearing,
                                        contentDescription = "Speaker",
                                        tint = if (isSpeakerphoneOn) colors.accent else colors.textPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isSpeakerphoneOn) "Speaker" else "Earpiece",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                        }

                        // 3. Call-Like Red "End / Disconnect" Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { viewModel.toggleWalkieMaster(false) },
                                shape = CircleShape,
                                color = Color.Transparent,
                                shadowElevation = 8.dp,
                                modifier = Modifier.size(58.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = "Disconnect Walkie",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Disconnect",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.error
                            )
                        }
                    }
                }
            }

            // =======================================================
            // 4. PAIRED TEAM RADIOS (Revealed on Activation)
            // =======================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x0A000000))
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PAIRED TEAM RADIOS ($connectedPairedCount OF ${pairedDevices.size} CONNECTED)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = colors.textSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.badgeMintContainer)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Auto-Mesh Link",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.badgeMintText
                            )
                        }
                    }

                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "No paired radios yet. Pair a discovered node below — it reconnects automatically.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    pairedDevices.forEach { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.cardSecondaryBg)
                                .border(0.5.dp, colors.outline, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.badgeBlueContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (peer.protocol) {
                                            TransportProtocol.WIFI_DIRECT -> Icons.Default.Wifi
                                            else -> Icons.Default.Bluetooth
                                        },
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = peer.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val signalDesc = when {
                                            peer.signalStrengthDbm > -65 -> "Strong Signal"
                                            peer.signalStrengthDbm > -80 -> "Good Signal"
                                            else -> "Fair Signal"
                                        }
                                        Text(
                                            text = if (peer.isConnected) {
                                                "Connected • $signalDesc"
                                            } else {
                                                "Paired • Out of range"
                                            },
                                            fontSize = 11.sp,
                                            color = if (peer.isConnected) colors.badgeMintText else colors.textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        BatteryIndicator(
                                            batteryPercent = peer.batteryPercent,
                                            heightDp = 10.dp
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.unpairDevice(peer) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Unpair",
                                    tint = colors.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =======================================================
            // 5. AVAILABLE NODES NEARBY (With Refresh Button)
            // =======================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x0A000000))
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVAILABLE NODES NEARBY (${discoveredDevices.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = colors.textSecondary
                        )

                        // Interactive Refresh Button with animated spin during scanning
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.cardSecondaryBg)
                                .clickable { viewModel.refreshDiscoveredNodes() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = colors.accent,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .rotate(if (isRefreshingNodes) refreshRotation else 0f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isRefreshingNodes) "Scanning..." else "Rescan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent
                                )
                            }
                        }
                    }

                    if (discoveredDevices.isEmpty()) {
                        Text(
                            text = if (isRefreshingNodes) {
                                "Scanning for BLE + Wi-Fi Direct nodes in range..."
                            } else {
                                "No nodes in range yet. Both phones must have Walkie Mesh active."
                            },
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    discoveredDevices.forEach { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.cardSecondaryBg)
                                .border(0.5.dp, colors.outline, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = peer.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                val signalDesc = when {
                                    peer.signalStrengthDbm > -65 -> "Strong Signal"
                                    peer.signalStrengthDbm > -80 -> "Good Signal"
                                    else -> "Fair Signal"
                                }
                                Text(
                                    text = "${if (peer.protocol == TransportProtocol.BLE) "BLE" else "Wi-Fi Direct"} • $signalDesc",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Button(
                                onClick = { viewModel.pairDevice(peer) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pair", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}
