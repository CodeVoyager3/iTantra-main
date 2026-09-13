package com.itantra.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.model.SupportedLanguage
import com.itantra.app.model.VoiceStatus
import com.itantra.app.modelhub.ModelDownloadState
import com.itantra.app.ui.theme.AccentBlue
import com.itantra.app.ui.theme.AccentBlueContainer
import com.itantra.app.ui.theme.BadgeIndigoContainer
import com.itantra.app.ui.theme.BadgeIndigoText
import com.itantra.app.ui.components.DigitalAudioVisualizer
import com.itantra.app.ui.theme.BadgeMintContainer
import com.itantra.app.ui.theme.BadgeMintText
import com.itantra.app.ui.theme.MeshGreen
import com.itantra.app.ui.theme.MeshGreenText
import com.itantra.app.ui.theme.RescueAmber
import com.itantra.app.ui.theme.RescueAmberContainer
import com.itantra.app.ui.theme.RescueAmberText
import com.itantra.app.ui.theme.minimalColors
import com.itantra.app.ui.theme.SosRed
import com.itantra.app.ui.theme.SosRedDark
import com.itantra.app.viewmodel.MissionControlViewModel
import kotlinx.coroutines.launch

/**
 * World-Class Modern, Simplistic Emergency SOS Screen:
 * - High-End Mission Telemetry Header: Brand identity + Live dynamic readiness status
 * - Hero Industrial Tactile SOS Dome: Multi-layer concentric radar guides, realistic specular radial dome,
 *   subtle ambient breathing aura, and responsive Surface touch target
 * - Instant Reassurance & Abort Pill: Clear instructions on standby, high-visibility cancel controls on SOS
 * - Floating Neural Voice Engine Card + 1-Tap Dialect Switcher: Instant quick pills for top Indic dialects + modal sheet
 * - Rescuer Radar & Live Audio Intercom: Real-time visualizer waveform and proximity telemetry on SOS
 * - Grouped "Off-Grid Radios" Hardware Card: Muted standby with auto-engage on emergency distress
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosDistressScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.minimalColors
    val uiState by viewModel.uiState.collectAsState()
    val isSosBroadcasting by viewModel.isSosBroadcasting.collectAsState()
    val wifiDirectEnabled by viewModel.wifiDirectEnabled.collectAsState()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsState()
    val nearbyRescuers by viewModel.nearbyRescuers.collectAsState()
    val connectedRescuer by viewModel.connectedRescuer.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val modelWarning by viewModel.modelWarningMessage.collectAsState()
    val selectedLanguage = uiState.selectedLanguage
    val modelPacks by viewModel.modelPacks.collectAsState()
    val selectedPack = modelPacks.firstOrNull { it.iso == selectedLanguage.code }
    val selectedPackState = if (selectedPack?.isInstalled == true) ModelDownloadState.Installed
        else (selectedPack?.downloadState ?: ModelDownloadState.Idle)
    val messageLogs by viewModel.messageLogs.collectAsState()
    val currentTranscript = uiState.currentTranscript
    val voiceStatus = uiState.voiceStatus
    val isVadSpeaking by viewModel.isVadSpeaking.collectAsState()
    val isPttActive by viewModel.isPttActive.collectAsState()
    // A rescuer streaming a strictly one-way announcement: this device is a
    // receive-only endpoint, so it must not present talk affordances.
    val isReceivingOneWayBroadcast by viewModel.isReceivingOneWayBroadcast.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isSpeakerphoneOn by viewModel.isSpeakerphoneOn.collectAsState()

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.sendBroadcastTextMessage(spokenText)
            }
        }
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var showLanguageSheet by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Smooth breathing transition for button aura
    val infiniteTransition = rememberInfiniteTransition(label = "sosPulse")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSosBroadcasting) 1.22f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSosBroadcasting) 750 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = if (isSosBroadcasting) 0.45f else 0.15f,
        targetValue = if (isSosBroadcasting) 0.85f else 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSosBroadcasting) 750 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    // Animated waveform bars for live intercom audio
    val waveBar1 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "waveBar1"
    )
    val waveBar2 by infiniteTransition.animateFloat(
        initialValue = 16f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "waveBar2"
    )
    val waveBar3 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "waveBar3"
    )
    val waveBar4 by infiniteTransition.animateFloat(
        initialValue = 20f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(460, easing = LinearEasing), RepeatMode.Reverse),
        label = "waveBar4"
    )

    // Button press interaction source for tactile depression
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
                            .background(if (isSosBroadcasting) SosRed else AccentBlue)
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
                        text = "OFF-GRID",
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
                    .background(
                        if (isSosBroadcasting) colors.errorContainer else colors.surface
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSosBroadcasting) colors.error else colors.outline,
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
                            .scale(if (isSosBroadcasting) ringScale else 1f)
                            .clip(CircleShape)
                            .background(if (isSosBroadcasting) SosRed else MeshGreen)
                    )
                    Text(
                        text = if (isSosBroadcasting) "DISTRESS ACTIVE" else "STANDBY READY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = if (isSosBroadcasting) colors.error else colors.textSecondary
                    )
                }
            }
        }

        // =======================================================
        // 2. HERO INDUSTRIAL TACTILE EMERGENCY SOS BUTTON
        // =======================================================
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
                        if (isSosBroadcasting) {
                            Brush.radialGradient(
                                colors = listOf(Color(0x40EF4444), Color(0x10EF4444), Color.Transparent)
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(Color(0x18EF4444), Color(0x08EF4444), Color.Transparent)
                            )
                        }
                    )
            )

            // Middle radar reference ring with cardinal markers
            Box(
                modifier = Modifier
                    .size(212.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = if (isSosBroadcasting) Color(0x40EF4444) else Color(0x1CE2E8F0),
                        shape = CircleShape
                    )
            )

            // 4 Cardinal Micro-Ticks for tactile instrument aesthetic
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .size(width = 2.dp, height = 6.dp)
                    .background(Color(0xFFCBD5E1), RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .size(width = 2.dp, height = 6.dp)
                    .background(Color(0xFFCBD5E1), RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp)
                    .size(width = 6.dp, height = 2.dp)
                    .background(Color(0xFFCBD5E1), RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .size(width = 6.dp, height = 2.dp)
                    .background(Color(0xFFCBD5E1), RoundedCornerShape(1.dp))
            )

            // Central Tactile Dome Button (Using Surface to guarantee 100% reliable click registration)
            Surface(
                onClick = {
                    if (isSosBroadcasting) viewModel.stopSos() else viewModel.startSos()
                },
                shape = CircleShape,
                color = Color.Transparent,
                interactionSource = buttonInteractionSource,
                shadowElevation = if (isSosBroadcasting) 16.dp else 8.dp,
                modifier = Modifier
                    .size(178.dp)
                    .scale(buttonPressScale)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = if (isSosBroadcasting) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFF5252),
                                        Color(0xFFE53935),
                                        Color(0xFFB71C1C),
                                        Color(0xFF7F1D1D)
                                    )
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFF4D4D),
                                        Color(0xFFEF4444),
                                        Color(0xFFDC2626),
                                        Color(0xFF991B1B)
                                    )
                                )
                            }
                        )
                        .border(
                            width = 3.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0x99FFFFFF),
                                    Color(0x25FFFFFF)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Emergency Upper Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.95f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EMERGENCY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }

                        // Hero SOS Headline
                        Text(
                            text = "SOS",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = Color.White
                        )

                        // Action Micro-Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                                .padding(horizontal = 9.dp, vertical = 2.5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSosBroadcasting) Icons.Default.NotificationsActive else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSosBroadcasting) "DISTRESS ACTIVE" else "TAP TO START",
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

        // =======================================================
        // 3. REASSURING CONTEXT & STOP SOS CONTROLS
        // =======================================================
        if (!isSosBroadcasting) {
            // Calm, reassuring message on Standby
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap SOS to alert all rescue nodes in 250m mesh radius",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Emergency active alert banner + Cancel button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🚨 Emergency Distress Beacon Broadcasting.\nNearby rescuers' phones are vibrating to locate you.",
                    fontSize = 13.sp,
                    color = SosRedDark,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Button(
                    onClick = { viewModel.stopSos() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFEE2E2),
                        contentColor = SosRedDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(44.dp)
                ) {
                    Text(
                        text = "⏹ Stop Distress SOS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // =======================================================
        // 4. FLOATING NEURAL LANGUAGE SELECTOR & 1-TAP DIALECT BAR
        // =======================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x0A000000))
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Top Row: Selected Language summary & Change trigger
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showLanguageSheet = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Native Glyph Avatar Squircle
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp), spotColor = AccentBlue.copy(alpha = 0.25f))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)))
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedLanguage.nativeInitial,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "VOICE ENGINE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.cardSecondaryBg)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "OFFLINE AI",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "${selectedLanguage.englishName} (${selectedLanguage.nativeName})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = when (selectedPackState) {
                                    is ModelDownloadState.Installed -> "Installed • On-device STT + TTS"
                                    is ModelDownloadState.Downloading ->
                                        "Downloading • ${(selectedPackState.progress * 100).toInt()}%"
                                    is ModelDownloadState.Paused ->
                                        "Paused • ${(selectedPackState.progress * 100).toInt()}%"
                                    is ModelDownloadState.Verifying -> "Verifying • On-device STT + TTS"
                                    is ModelDownloadState.Extracting -> "Extracting • On-device STT + TTS"
                                    is ModelDownloadState.Error -> "Download failed • Tap Retry"
                                    else -> "Not downloaded • ${formatSizeMb(selectedPack?.sizeMb ?: selectedLanguage.downloadSizeMb.toDouble())} MB"
                                },
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Apple-style modern pill dropdown trigger
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.badgeBlueContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Change",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Change Language",
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.outline, thickness = 1.dp)

                // Quick 1-Tap Dialect Switcher Bar (Instant zero-friction switching)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickLangs = listOf(
                        SupportedLanguage.HINDI,
                        SupportedLanguage.ENGLISH,
                        SupportedLanguage.BENGALI,
                        SupportedLanguage.MARATHI
                    )

                    quickLangs.forEach { lang ->
                        val isLangActive = selectedLanguage == lang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isLangActive) colors.accent else colors.cardSecondaryBg)
                                .border(
                                    width = 1.dp,
                                    color = if (isLangActive) colors.accent else colors.outline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setSelectedLanguage(lang) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lang.nativeName,
                                fontSize = 11.sp,
                                fontWeight = if (isLangActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLangActive) Color.White else colors.textSecondary
                            )
                        }
                    }

                    // "+More" Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.cardSecondaryBg)
                            .clickable { showLanguageSheet = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+6",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary
                        )
                    }
                }

                // Digital Gray-Line Audio Visualizer (Live Microphone Activity)
                if (isSosBroadcasting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    DigitalAudioVisualizer(
                        audioLevel = audioLevel,
                        isActive = isSosBroadcasting,
                        label = when {
                            isReceivingOneWayBroadcast -> "RECEIVE-ONLY RESCUER BROADCAST"
                            connectedRescuer != null -> "LIVE 2-WAY INTERCOM"
                            else -> "HANDS-FREE EMERGENCY MIC"
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // =======================================================
        // 5. RESCUER RADAR & LIVE INTERCOM (EXPANDS ON SOS)
        // =======================================================
        AnimatedVisibility(
            visible = isSosBroadcasting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Model Download Warning Banner (if model pack is missing for voice transcription)
                if (modelWarning != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7))
                            .border(1.dp, Color(0xFFD97706), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = modelWarning ?: "",
                                    fontSize = 11.sp,
                                    color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E),
                                    lineHeight = 15.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissModelWarning() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // =====================================================
                // LIVE RESCUER CALL / 1-WAY BROADCAST CONSOLE CARD
                // =====================================================
                if (connectedRescuer != null || isReceivingOneWayBroadcast) {
                    val rescuer = connectedRescuer
                    val isBroadcast = isReceivingOneWayBroadcast

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 3.dp,
                                shape = RoundedCornerShape(22.dp),
                                spotColor = if (isBroadcast) RescueAmber.copy(alpha = 0.25f) else MeshGreen.copy(alpha = 0.25f)
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .then(
                                if (isBroadcast) {
                                    Modifier.background(
                                        if (colors.isDark) Color(0xFF451A03).copy(alpha = 0.35f)
                                        else Color(0xFFFFFBEB)
                                    )
                                } else {
                                    if (colors.isDark) {
                                        Modifier.background(Color(0xFF064E3B).copy(alpha = 0.35f))
                                    } else {
                                        Modifier.background(
                                            Brush.linearGradient(listOf(Color(0xFFECFDF5), Color(0xFFF0FDF4)))
                                        )
                                    }
                                }
                            )
                            .border(
                                1.5.dp,
                                if (isBroadcast) RescueAmber else MeshGreen,
                                RoundedCornerShape(22.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Header Row: Rescuer Info / Broadcast Title + Status Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(if (isBroadcast) RescueAmber else MeshGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isBroadcast) Icons.Default.Campaign else Icons.Default.HeadsetMic,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (isBroadcast) "EMERGENCY BROADCAST" else "RESCUER CONNECTED",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isBroadcast) RescueAmberText else MeshGreenText,
                                            letterSpacing = 0.8.sp
                                        )
                                        Text(
                                            text = rescuer?.callsign ?: "Rescuer Megaphone",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = if (isBroadcast) {
                                                "1-Way Announcement • Listen Only"
                                            } else {
                                                "${rescuer?.role ?: "iTantra Rescuer"} • ~${rescuer?.distanceMeters ?: 1}m away"
                                            },
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                // Status Badge
                                if (isBroadcast) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(RescueAmber)
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "ON AIR",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MeshGreen)
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Box(modifier = Modifier.width(3.dp).height(waveBar1.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                        Box(modifier = Modifier.width(3.dp).height(waveBar2.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                        Box(modifier = Modifier.width(3.dp).height(waveBar3.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                        Box(modifier = Modifier.width(3.dp).height(waveBar4.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "LIVE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // 1-Way Broadcast Information Banner (if receiving broadcast)
                            if (isBroadcast) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7))
                                        .border(1.dp, RescueAmber.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MicOff,
                                            contentDescription = null,
                                            tint = RescueAmberText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "1-WAY RESCUER BROADCAST — REPLIES DISABLED",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                                            )
                                            Text(
                                                text = "Rescuer is broadcasting megaphone announcements to all victims. Your mic is locked & muted.",
                                                fontSize = 11.sp,
                                                color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Digital Gray-Line Audio Visualizer
                            DigitalAudioVisualizer(
                                audioLevel = audioLevel,
                                isActive = true,
                                label = if (isBroadcast) {
                                    "RESCUER 1-WAY BROADCAST (RECEIVE ONLY)"
                                } else {
                                    "RESCUER 2-WAY AUDIO LINK"
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        if (isBroadcast) RescueAmber.copy(alpha = 0.3f)
                                        else MeshGreen.copy(alpha = 0.3f)
                                    )
                            )

                            // Hands-Free Audio Controls Row (Big Mic, Big Speaker, Big Disconnect)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Big Mic Mute / Unmute Button (58dp)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            if (!isBroadcast) {
                                                viewModel.toggleMicMute()
                                            }
                                        },
                                        enabled = !isBroadcast,
                                        shape = CircleShape,
                                        color = when {
                                            isBroadcast -> if (colors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                                            isMicMuted -> if (colors.isDark) Color(0xFF450A0A) else Color(0xFFFEE2E2)
                                            else -> colors.surface
                                        },
                                        shadowElevation = if (isBroadcast) 0.dp else 3.dp,
                                        modifier = Modifier.size(58.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isBroadcast || isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                                contentDescription = "Mute Mic",
                                                tint = when {
                                                    isBroadcast -> colors.textSecondary
                                                    isMicMuted -> SosRedDark
                                                    else -> colors.textPrimary
                                                },
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = when {
                                            isBroadcast -> "Mic Locked"
                                            isMicMuted -> "Unmute"
                                            else -> "Mute Mic"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            isBroadcast -> colors.textSecondary
                                            isMicMuted -> SosRedDark
                                            else -> colors.textSecondary
                                        }
                                    )
                                }

                                // 2. Big Speakerphone Toggle Button (58dp)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        onClick = { viewModel.toggleSpeakerphone() },
                                        shape = CircleShape,
                                        color = if (isSpeakerphoneOn) AccentBlueContainer else colors.surface,
                                        shadowElevation = 3.dp,
                                        modifier = Modifier.size(58.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Hearing,
                                                contentDescription = "Speaker",
                                                tint = if (isSpeakerphoneOn) AccentBlue else colors.textPrimary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isSpeakerphoneOn) "Speaker" else "Earpiece",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSpeakerphoneOn) AccentBlue else colors.textSecondary
                                    )
                                }

                                // 3. Big Disconnect Button (58dp)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            if (!isBroadcast) {
                                                viewModel.disconnectConnectedRescuer()
                                            }
                                        },
                                        enabled = !isBroadcast,
                                        shape = CircleShape,
                                        color = if (isBroadcast) (if (colors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0)) else SosRed,
                                        shadowElevation = if (isBroadcast) 0.dp else 4.dp,
                                        modifier = Modifier.size(58.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isBroadcast) Icons.Default.Hearing else Icons.Default.CallEnd,
                                                contentDescription = if (isBroadcast) "Listen Only" else "Disconnect",
                                                tint = if (isBroadcast) colors.textSecondary else Color.White,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isBroadcast) "Listen Only" else "End Call",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBroadcast) colors.textSecondary else SosRedDark
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // LIVE TRANSCRIPTION CARD (Shows sent + received text)
                // =====================================================
                if (isSosBroadcasting || connectedRescuer != null || messageLogs.isNotEmpty()) {
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "LIVE TRANSCRIPTION",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = colors.textSecondary
                                    )
                                    // Model status indicator badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (selectedPack?.isInstalled == true) BadgeMintContainer
                                                else Color(0xFFFEF3C7)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (selectedPack?.isInstalled == true) "✓ AI STT ACTIVE" else "⚠️ PACK REQUIRED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedPack?.isInstalled == true) BadgeMintText else Color(0xFF92400E)
                                        )
                                    }
                                }

                                if (messageLogs.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(BadgeMintContainer)
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${messageLogs.size} MSG",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BadgeMintText
                                        )
                                    }
                                }
                            }

                            // Warning banner if neural model pack is missing
                            if (modelWarning != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7))
                                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = modelWarning ?: "",
                                            fontSize = 11.sp,
                                            color = Color(0xFF92400E),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            // Voice Controls Row: Hold to Talk (PTT) + Instant Voice Dictate
                            if (isReceivingOneWayBroadcast) {
                                // Receive-only participant of a 1-way rescuer broadcast:
                                // no talk/mic affordance is shown, because nothing sent
                                // from here is heard on the other side.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (colors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                                        .padding(vertical = 12.dp, horizontal = 14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Hearing,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "RECEIVE-ONLY EMERGENCY BROADCAST",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "Rescuer is transmitting one-way. Mic and dictation are disabled.",
                                                fontSize = 11.sp,
                                                color = colors.textSecondary,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Hold to Talk (PTT) Button
                                    Box(
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isPttActive || isVadSpeaking) Color(0xFFDC2626)
                                                else if (colors.isDark) Color(0xFF1E293B)
                                                else Color(0xFFF1F5F9)
                                            )
                                            .border(
                                                1.dp,
                                                if (isPttActive || isVadSpeaking) Color(0xFFB91C1C)
                                                else colors.outline,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        viewModel.startPtt()
                                                        tryAwaitRelease()
                                                        viewModel.stopPtt()
                                                    }
                                                )
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Hold to talk",
                                                tint = if (isPttActive || isVadSpeaking) Color.White else SosRedDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (isPttActive || isVadSpeaking) "RECORDING..." else "HOLD TO TALK",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPttActive || isVadSpeaking) Color.White else colors.textPrimary
                                            )
                                        }
                                    }

                                    // 2. Voice Dictation Button (Android SpeechRecognizer in selected language)
                                    Box(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentBlue.copy(alpha = 0.12f))
                                            .border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.languageTag)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage.languageTag)
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${selectedLanguage.nativeName}...")
                                                }
                                                try {
                                                    speechLauncher.launch(intent)
                                                } catch (_: Exception) {}
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "🗣️ DICTATE",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentBlue
                                            )
                                        }
                                    }
                                }
                            }

                            // Active speaking recording pulse banner
                            if (!isReceivingOneWayBroadcast && (isVadSpeaking || isPttActive)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFDC2626).copy(alpha = 0.12f))
                                        .border(0.5.dp, Color(0xFFDC2626).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFDC2626))
                                        )
                                        Text(
                                            text = "RECORDING SPEECH... (Release button or pause to send)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }

                            // Live transcript / processing status banner
                            if (currentTranscript.isNotBlank()) {
                                val isListening = voiceStatus == VoiceStatus.LISTENING || voiceStatus == VoiceStatus.LISTENING_PTT
                                val isTranscribing = voiceStatus == VoiceStatus.TRANSCRIBING
                                val isWarning = voiceStatus == VoiceStatus.UNCLEAR

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                isListening -> SosRed.copy(alpha = 0.15f)
                                                isTranscribing -> BadgeMintContainer.copy(alpha = 0.6f)
                                                isWarning -> if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
                                                colors.isDark -> Color(0xFF1E3A5F).copy(alpha = 0.6f)
                                                else -> Color(0xFFEFF6FF)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isListening -> SosRed.copy(alpha = 0.6f)
                                                isTranscribing -> Color(0xFF059669).copy(alpha = 0.5f)
                                                isWarning -> Color(0xFFF59E0B)
                                                else -> AccentBlue.copy(alpha = 0.4f)
                                            },
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isListening || isTranscribing || isWarning) currentTranscript
                                                   else "\"$currentTranscript\"",
                                            fontSize = 13.sp,
                                            fontWeight = if (isListening || isTranscribing) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isListening -> SosRedDark
                                                isTranscribing -> Color(0xFF065F46)
                                                isWarning -> if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                                                colors.isDark -> Color(0xFFBFDBFE)
                                                else -> Color(0xFF1E40AF)
                                            },
                                            lineHeight = 18.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!isListening && !isTranscribing && !isWarning) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(BadgeMintContainer)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "✓ SENT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BadgeMintText
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (messageLogs.isEmpty()) {
                                // Standby waiting state
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.cardSecondaryBg)
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "🎙️ Voice Transceiver Standby",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = if (selectedPack?.isInstalled == true)
                                                "Speak clearly into mic or hold button. Neural AI STT will transcribe and broadcast text over mesh for TTS playback."
                                            else
                                                "Neural STT pack is not downloaded. Use HOLD TO TALK or DICTATE, or tap Quick Emergency Phrases below.",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            // Localized Quick Emergency Transmit Chips
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "QUICK EMERGENCY PHRASES (${selectedLanguage.nativeName.uppercase()})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = colors.textSecondary
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    selectedLanguage.quickSosPhrases.forEach { phrase ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (colors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                                .border(0.5.dp, colors.outline, RoundedCornerShape(8.dp))
                                                .clickable { viewModel.sendBroadcastTextMessage(phrase) }
                                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = phrase,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // Message log (last 5 messages)
                            messageLogs.take(5).forEach { msg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.cardSecondaryBg)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Sent/Received indicator
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (msg.isLocal) AccentBlue.copy(alpha = 0.15f)
                                                else MeshGreen.copy(alpha = 0.15f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (msg.isLocal) "↑" else "↓",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isLocal) AccentBlue else MeshGreen
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = msg.text,
                                            fontSize = 12.sp,
                                            color = colors.textPrimary,
                                            lineHeight = 16.sp,
                                            maxLines = 3
                                        )
                                        Text(
                                            text = if (msg.isLocal) "You • ${msg.senderCallsign}" else "Rescuer • ${msg.senderCallsign}",
                                            fontSize = 10.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Nearby Rescuers Detected Card
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
                                text = "NEARBY RESCUERS IN RANGE (${nearbyRescuers.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = colors.textSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MeshGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Scanning 250m",
                                    fontSize = 11.sp,
                                    color = colors.badgeMintText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        nearbyRescuers.forEach { rescuer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.cardSecondaryBg)
                                    .border(0.5.dp, colors.outline, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.badgeBlueContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HeadsetMic,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = rescuer.callsign,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "${rescuer.role} • ${rescuer.distanceMeters}m away",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (rescuer.isConnected) colors.badgeMintContainer else colors.badgeBlueContainer)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (rescuer.isConnected) "LINKED" else "READY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rescuer.isConnected) colors.badgeMintText else colors.badgeBlueText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // =======================================================
        // 6. UNIFIED "OFF-GRID RADIOS" HARDWARE CARD (STANDBY vs ACTIVE)
        // =======================================================
        val radioAlpha by animateFloatAsState(
            targetValue = if (isSosBroadcasting) 1f else 0.88f,
            animationSpec = tween(300),
            label = "radioAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(radioAlpha)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x0A000000))
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OFF-GRID RADIOS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.textSecondary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSosBroadcasting) colors.badgeMintContainer else colors.cardSecondaryBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isSosBroadcasting) "Broadcasting Full Power" else "Auto-Starts on SOS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSosBroadcasting) colors.badgeMintText else colors.textSecondary
                        )
                    }
                }

                // Row 1: Wi-Fi Direct P2P Mesh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSosBroadcasting) AccentBlueContainer else Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wi-Fi Direct",
                                tint = if (isSosBroadcasting) AccentBlue else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Wi-Fi Direct P2P",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSosBroadcasting) BadgeMintContainer else Color(0xFFF1F5F9))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (isSosBroadcasting) "P2P Active" else "Locked",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSosBroadcasting) BadgeMintText else Color(0xFF64748B)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isSosBroadcasting) {
                                    "Broadcasting on UDP port 8889 • direct device link"
                                } else {
                                    "High-speed local audio & mesh network"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Switch(
                        checked = wifiDirectEnabled,
                        onCheckedChange = { if (isSosBroadcasting) viewModel.toggleWifiDirect(it) },
                        enabled = isSosBroadcasting,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                // Row 2: Bluetooth BLE Beacon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSosBroadcasting) BadgeIndigoContainer else Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth",
                                tint = if (isSosBroadcasting) BadgeIndigoText else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Bluetooth BLE Mesh",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSosBroadcasting) BadgeIndigoContainer else Color(0xFFF1F5F9))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (isSosBroadcasting) "BLE Active" else "Locked",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSosBroadcasting) BadgeIndigoText else Color(0xFF64748B)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isSosBroadcasting) "Broadcasting emergency beacon" else "Continuous low-power emergency beacon",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Switch(
                        checked = bluetoothEnabled,
                        onCheckedChange = { if (isSosBroadcasting) viewModel.toggleBluetooth(it) },
                        enabled = isSosBroadcasting,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }

    // =======================================================
    // 7. HIGH-END 10-LANGUAGE MODAL BOTTOM SHEET WITH SEARCH
    // =======================================================
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            sheetState = sheetState,
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.outline)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Distress Voice Model",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "10 Indic neural AI bundles run 100% on-device",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showLanguageSheet = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }

                // Instant Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardSecondaryBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = languageSearchQuery,
                            onValueChange = { languageSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            decorationBox = { innerTextField ->
                                if (languageSearchQuery.isEmpty()) {
                                    Text(
                                        text = "Search language or dialect...",
                                        fontSize = 14.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                HorizontalDivider(color = colors.outline, thickness = 1.dp)

                // Filtered List of Supported Languages
                val filteredLanguages = remember(languageSearchQuery) {
                    if (languageSearchQuery.isBlank()) {
                        SupportedLanguage.entries
                    } else {
                        val q = languageSearchQuery.trim().lowercase()
                        SupportedLanguage.entries.filter {
                            it.englishName.lowercase().contains(q) ||
                            it.nativeName.lowercase().contains(q)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLanguages) { lang ->
                        val isSelected = selectedLanguage == lang
                        val pack = modelPacks.firstOrNull { it.iso == lang.code }
                        val state = if (pack?.isInstalled == true) ModelDownloadState.Installed
                            else (pack?.downloadState ?: ModelDownloadState.Idle)
                        val sizeMb = pack?.sizeMb ?: lang.downloadSizeMb.toDouble()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) colors.badgeBlueContainer else colors.cardSecondaryBg)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) colors.accent else colors.outline,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    // Row main click always selects the language + dismisses.
                                    viewModel.setSelectedLanguage(lang)
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        showLanguageSheet = false
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Native Glyph Avatar
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when (state) {
                                                        is ModelDownloadState.Installed -> colors.accent
                                                        is ModelDownloadState.Downloading,
                                                        is ModelDownloadState.Paused,
                                                        is ModelDownloadState.Verifying,
                                                        is ModelDownloadState.Extracting -> BadgeIndigoContainer
                                                        else -> colors.outline
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = lang.nativeInitial,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected || state is ModelDownloadState.Installed) Color.White else colors.textSecondary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = lang.englishName,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.accent else colors.textPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(${lang.nativeName})",
                                                    fontSize = 14.sp,
                                                    color = if (isSelected) colors.accent else colors.textSecondary
                                                )
                                            }

                                            Text(
                                                text = "On-Device Size: ${formatSizeMb(sizeMb)} MB (STT + TTS)",
                                                fontSize = 12.sp,
                                                color = if (isSelected) colors.accent.copy(alpha = 0.85f) else colors.textSecondary
                                            )
                                        }
                                    }

                                    // Trailing action, driven by the live download state.
                                    when {
                                        state is ModelDownloadState.Installed && isSelected -> {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colors.accent)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "ACTIVE",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        state is ModelDownloadState.Installed -> {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colors.badgeMintContainer)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "INSTALLED",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.badgeMintText
                                                )
                                            }
                                        }

                                        state is ModelDownloadState.Downloading -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(
                                                    onClick = { viewModel.pauseModelDownload(lang.languageTag) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Pause,
                                                        contentDescription = "Pause ${lang.englishName}",
                                                        tint = colors.textSecondary,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.cancelModelDownload(lang.languageTag) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Cancel,
                                                        contentDescription = "Cancel ${lang.englishName}",
                                                        tint = SosRedDark,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }

                                        state is ModelDownloadState.Paused -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(
                                                    onClick = { viewModel.downloadModel(lang.languageTag) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Resume ${lang.englishName}",
                                                        tint = colors.accent,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.cancelModelDownload(lang.languageTag) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Cancel,
                                                        contentDescription = "Cancel ${lang.englishName}",
                                                        tint = SosRedDark,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }

                                        state is ModelDownloadState.Verifying || state is ModelDownloadState.Extracting -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (state is ModelDownloadState.Verifying) "Verifying…" else "Extracting…",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.accent
                                                )
                                                IconButton(
                                                    onClick = { viewModel.cancelModelDownload(lang.languageTag) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Cancel,
                                                        contentDescription = "Cancel ${lang.englishName}",
                                                        tint = SosRedDark,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }

                                        state is ModelDownloadState.Error -> {
                                            Text(
                                                text = "Retry",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.error,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colors.errorContainer)
                                                    .clickable { viewModel.downloadModel(lang.languageTag) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }

                                        else -> {
                                            // Idle or Cancelled — offer the download.
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colors.accent)
                                                    .clickable { viewModel.downloadModel(lang.languageTag) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Download • ${formatSizeMb(sizeMb)} MB",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                // Progress / status line below the row.
                                when (state) {
                                    is ModelDownloadState.Downloading -> {
                                        LinearProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(CircleShape),
                                            color = colors.accent,
                                            trackColor = colors.outline
                                        )
                                        Text(
                                            text = "${(state.progress * 100).toInt()}% • ${formatSizeMb(state.progressBytes / (1024.0 * 1024.0))} / ${formatSizeMb(state.totalBytes / (1024.0 * 1024.0))} MB",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                    is ModelDownloadState.Paused -> {
                                        LinearProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(CircleShape),
                                            color = colors.badgePurpleText,
                                            trackColor = colors.outline
                                        )
                                        Text(
                                            text = "PAUSED • ${(state.progress * 100).toInt()}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.badgePurpleText
                                        )
                                    }
                                    is ModelDownloadState.Error -> {
                                        Text(
                                            text = state.message,
                                            fontSize = 11.sp,
                                            color = SosRedDark
                                        )
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** Formats a size in MiB to one decimal place for compact UI labels. */
private fun formatSizeMb(sizeMb: Double): String =
    String.format(java.util.Locale.US, "%.1f", sizeMb)
