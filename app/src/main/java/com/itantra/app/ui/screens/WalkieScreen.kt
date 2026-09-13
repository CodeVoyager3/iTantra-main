package com.itantra.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.model.PeerDevice
import com.itantra.app.model.RadioChannelState
import com.itantra.app.model.SupportedLanguage
import com.itantra.app.model.TransportProtocol
import com.itantra.app.ui.components.BatteryIndicator
import com.itantra.app.ui.theme.AccentBlue
import com.itantra.app.ui.theme.AccentBlueContainer
import com.itantra.app.ui.theme.BadgeMintContainer
import com.itantra.app.ui.theme.BadgeMintText
import com.itantra.app.ui.theme.MeshGreen
import com.itantra.app.ui.theme.MeshGreenContainer
import com.itantra.app.ui.theme.MeshGreenText
import com.itantra.app.ui.theme.SosRed
import com.itantra.app.ui.theme.SosRedDark
import com.itantra.app.ui.theme.minimalColors
import com.itantra.app.viewmodel.MissionControlViewModel

/**
 * World-Class Modern, Minimalistic Walkie-Talkie Screen:
 * - High-End Mission Telemetry Header matching the SOS / Rescue pages
 * - Language Selector Card with 1-Tap Dialect Chips and Full Dialect Sheet
 * - Warning & Language Mismatch Banner with 1-Tap Peer Sync
 * - Standby Mode: Prominent, highlighted hero transceiver dome
 * - Active Mode: Transforms into a high-end call & intercom console with:
 *     - Interactive Central Disc: Push-to-Talk (PTT hold) + Auto-Voice VAD
 *     - Live Equalizer audio spectrum visualizer
 *     - Clean 3-Button Controls: Mic Mute, Speakerphone, Disconnect (Dictate removed)
 * - Revealed Controls on Activation:
 *     - Paired Team Radios with signal, battery & unpair
 *     - Available Nearby Nodes with interactive, animated Refresh/Rescan button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.minimalColors
    val isWalkieActive by viewModel.isWalkieActive.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isTransmitting by viewModel.isTransmitting.collectAsState()
    val isPttActive by viewModel.isPttActive.collectAsState()
    val isRefreshingNodes by viewModel.isRefreshingNodes.collectAsState()
    val isSpeakerphoneOn by viewModel.isSpeakerphoneOn.collectAsState()
    val pairedDevices by viewModel.pairedWalkieDevices.collectAsState()
    val discoveredDevices by viewModel.discoveredWalkieDevices.collectAsState()
    val incomingPairRequest by viewModel.incomingPairRequest.collectAsState()
    val pendingPairingTargetNodeId by viewModel.pendingPairingTargetNodeId.collectAsState()
    val isVadSpeaking by viewModel.isVadSpeaking.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val isWalkieLinkActive by viewModel.isWalkieLinkActive.collectAsState()
    val isReceivingAudio by viewModel.isReceivingAudio.collectAsState()
    val remoteAudioLevel by viewModel.remoteAudioLevel.collectAsState()
    val liveAudioLevel = if (isReceivingAudio) remoteAudioLevel else audioLevel
    val connectedPairedCount = pairedDevices.count { it.isConnected }
    val uiState by viewModel.uiState.collectAsState()
    val messageLogs by viewModel.messageLogs.collectAsState()
    val modelPacks by viewModel.modelPacks.collectAsState()
    val modelWarningMessage by viewModel.modelWarningMessage.collectAsState()
    val activePeerLanguage by viewModel.activePeerLanguage.collectAsState()

    var showLanguageSheet by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }
    val selectedLanguage = uiState.selectedLanguage

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
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
        // INCOMING PAIRING REQUEST APPROVAL BANNER
        // =======================================================
        AnimatedVisibility(
            visible = incomingPairRequest != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val req = incomingPairRequest
            if (req != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = AccentBlue.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E293B)
                                )
                            )
                        )
                        .border(1.5.dp, AccentBlue, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "🤝", fontSize = 18.sp)
                                Text(
                                    text = "PAIRING REQUEST",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentBlue,
                                    letterSpacing = 1.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentBlueContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "APPROVAL REQUIRED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentBlue
                                )
                            }
                        }

                        Text(
                            text = "${req.fromCallsign} wants to pair with your radio to start sharing Walkie-Talkie voice.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.rejectPairRequest(req.fromNodeId) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF334155),
                                    contentColor = Color(0xFFCBD5E1)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Decline", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.acceptPairRequest(req.fromNodeId) },
                                modifier = Modifier.weight(1.3f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeshGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Accept & Pair", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // =======================================================
        // WARNING / STATUS BANNER (Language Mismatch, Model Packs)
        // =======================================================
        AnimatedVisibility(
            visible = modelWarningMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val warning = modelWarningMessage
            if (warning != null) {
                val isMismatch = warning.contains("Language Mismatch", ignoreCase = true)
                val peerLangCode = activePeerLanguage
                val peerLangName = peerLangCode?.let { SupportedLanguage.fromCode(it).englishName }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isMismatch) Color(0xFFFEF3C7) else Color(0xFFEFF6FF))
                        .border(
                            width = 1.dp,
                            color = if (isMismatch) Color(0xFFF59E0B) else Color(0xFF3B82F6),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isMismatch) Color(0xFFB45309) else Color(0xFF1D4ED8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isMismatch) "LANGUAGE MISMATCH" else "RADIO NOTICE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = if (isMismatch) Color(0xFFB45309) else Color(0xFF1D4ED8)
                            )
                        }

                        Text(
                            text = warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isMismatch) Color(0xFF78350F) else Color(0xFF1E3A8A)
                        )

                        // If it's a language mismatch, provide a 1-tap button to sync language with the peer!
                        if (isMismatch && peerLangCode != null && peerLangName != null) {
                            Button(
                                onClick = {
                                    viewModel.setSelectedLanguage(SupportedLanguage.fromCode(peerLangCode))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD97706),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "Switch to $peerLangName ($peerLangCode) to Match Peer",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // =======================================================
        // 2. LANGUAGE SELECTOR CARD
        // =======================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x0A000000))
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedLanguage.nativeInitial,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "RADIO DIALECT: ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "${selectedLanguage.englishName} (${selectedLanguage.code.uppercase()})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            val isInstalled = modelPacks.firstOrNull {
                                it.iso == selectedLanguage.code || it.languageTag.startsWith(selectedLanguage.code)
                            }?.isInstalled == true
                            Text(
                                text = if (isInstalled) "✓ Neural Pack Ready" else "⚠ Pack Not Installed (${selectedLanguage.downloadSizeMb} MB)",
                                fontSize = 11.sp,
                                fontWeight = if (isInstalled) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isInstalled) Color(0xFF059669) else Color(0xFFD97706)
                            )
                        }
                    }

                    Surface(
                        onClick = { showLanguageSheet = true },
                        shape = RoundedCornerShape(8.dp),
                        color = colors.cardSecondaryBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SWITCH",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Switch Language",
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.outline.copy(alpha = 0.5f), thickness = 1.dp)

                // 1-Tap Dialect Chips: Hindi, English, Bengali, Marathi
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
                        val isInstalled = modelPacks.firstOrNull {
                            it.iso == lang.code || it.languageTag.startsWith(lang.code)
                        }?.isInstalled == true

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
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = lang.englishName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isLangActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isLangActive) Color.White else colors.textPrimary
                                )
                                if (isInstalled) {
                                    Text(
                                        text = "READY",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLangActive) Color(0xFFA7F3D0) else Color(0xFF059669)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =======================================================
        // 3. STANDBY HERO (When Walkie is Inactive)
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
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "JOIN WALKIE",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.1.sp,
                                color = Color.White
                            )
                            Text(
                                text = "OFF-GRID TEAM VOICE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Trust Badges underneath the standby hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("100% Offline", "Direct Mesh Voice", "Zero Mobile Data").forEach { feature ->
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
        // 4. ACTIVE MODE: CALL-STYLE CONSOLE & CONTROLS
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
                    // Top Room Info Row
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

                    // Voice Equalizer / PTT Central Disc (Supports HOLD TO TALK via pointerInput)
                    val isLiveTx = (isTransmitting || isVadSpeaking || isPttActive) && !isMicMuted
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

                        // Central Disc: Push-to-Talk touch gesture handler
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
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            viewModel.startPtt()
                                            try {
                                                tryAwaitRelease()
                                            } finally {
                                                viewModel.stopPtt()
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isMicMuted -> Icons.Default.MicOff
                                    isLiveTx -> Icons.Default.GraphicEq
                                    isReceivingAudio -> Icons.AutoMirrored.Filled.VolumeUp
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = "Push to Talk",
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
                                isLiveTx -> if (isPttActive) "TRANSMITTING LIVE VOICE (PTT HELD)" else "TRANSMITTING LIVE VOICE..."
                                else -> "HOLD DISC TO TALK • OR SPEAK FREELY"
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

                    // Live 24-Bar Equalizer Audio Spectrum Visualizer
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
                    // CLEAN 3-BUTTON HANDS-FREE CONTROLS
                    // (1. Mute/Unmute Mic, 2. Speaker/Earpiece, 3. Disconnect)
                    // Note: Dictate button removed as requested.
                    // =======================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Mic Mute / Unmute Button
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
                                        imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Hearing,
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
            // LIVE TRANSCRIPTION & VOICE COMMS CARD
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
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BadgeMintContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (uiState.channelState == RadioChannelState.RECEIVING) "⚡ RECEIVING VOICE"
                                    else if (isTransmitting || isVadSpeaking || isPttActive) "🎙️ TRANSMITTING"
                                    else "● STANDBY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.channelState == RadioChannelState.RECEIVING) MeshGreenText
                                    else if (isTransmitting || isVadSpeaking || isPttActive) AccentBlue
                                    else colors.textSecondary
                                )
                            }
                        }

                        if (messageLogs.isNotEmpty()) {
                            Text(
                                text = "${messageLogs.size} logs",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Current Live Transcript Bar
                    val transcript = uiState.currentTranscript
                    if (transcript.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (uiState.channelState == RadioChannelState.RECEIVING) MeshGreenContainer.copy(alpha = 0.5f)
                                    else colors.cardSecondaryBg
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (uiState.channelState == RadioChannelState.RECEIVING) MeshGreen.copy(alpha = 0.5f)
                                    else colors.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (uiState.channelState == RadioChannelState.RECEIVING) "Incoming Speech:" else "Live Caption:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.channelState == RadioChannelState.RECEIVING) MeshGreenText else colors.textSecondary
                                )
                                Text(
                                    text = transcript,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    } else if (messageLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.cardSecondaryBg)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Hold central disc or speak to transmit. Transcriptions sync across all radios automatically.",
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Recent Message Log (Last 3 messages)
                    if (messageLogs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            messageLogs.take(3).forEach { msg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (msg.isLocal) AccentBlueContainer.copy(alpha = 0.35f) else MeshGreenContainer.copy(alpha = 0.35f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (msg.isLocal) "You (${msg.senderCallsign})" else msg.senderCallsign,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isLocal) AccentBlue else MeshGreenText
                                        )
                                        Text(
                                            text = msg.text,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =======================================================
            // 5. PAIRED TEAM RADIOS (Revealed on Activation)
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
            // 6. AVAILABLE NODES NEARBY (With Refresh Button)
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
                        val peerNodeId = peer.id.removePrefix("node-").removePrefix("ble-").removePrefix("p2p-").toLongOrNull()
                        val isPending = pendingPairingTargetNodeId != null && pendingPairingTargetNodeId == peerNodeId

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
                                    text = "Radio Mesh • $signalDesc",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Button(
                                onClick = { viewModel.sendPairRequest(peer) },
                                enabled = !isPending,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPending) colors.outline else colors.accent,
                                    contentColor = Color.White,
                                    disabledContainerColor = colors.cardSecondaryBg,
                                    disabledContentColor = colors.textSecondary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                if (isPending) {
                                    Text("Requesting...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
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
        }

        Spacer(modifier = Modifier.height(14.dp))
    }

    // =======================================================
    // FULL DIALECT SELECTION BOTTOM SHEET
    // =======================================================
    if (showLanguageSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            sheetState = sheetState,
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Walkie Dialect",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    IconButton(onClick = { showLanguageSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                OutlinedTextField(
                    value = languageSearchQuery,
                    onValueChange = { languageSearchQuery = it },
                    placeholder = { Text("Search language or dialect...", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                val filtered = remember(languageSearchQuery) {
                    if (languageSearchQuery.isBlank()) SupportedLanguage.entries
                    else {
                        val q = languageSearchQuery.trim().lowercase()
                        SupportedLanguage.entries.filter {
                            it.englishName.lowercase().contains(q) ||
                            it.nativeName.lowercase().contains(q) ||
                            it.code.lowercase().contains(q)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { lang ->
                        val isSelected = selectedLanguage == lang
                        val isInstalled = modelPacks.firstOrNull {
                            it.iso == lang.code || it.languageTag.startsWith(lang.code)
                        }?.isInstalled == true

                        Surface(
                            onClick = {
                                viewModel.setSelectedLanguage(lang)
                                showLanguageSheet = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.cardSecondaryBg,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) colors.accent else colors.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${lang.englishName} (${lang.nativeName})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) colors.accent else colors.textPrimary
                                    )
                                    Text(
                                        text = if (isInstalled) "✓ Installed & Ready" else "Neural Pack: ${lang.downloadSizeMb} MB",
                                        fontSize = 11.sp,
                                        color = if (isInstalled) Color(0xFF059669) else colors.textSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
