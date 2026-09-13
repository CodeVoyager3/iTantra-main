package com.itantra.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import com.itantra.app.model.SupportedLanguage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.DialogProperties
import com.itantra.app.model.DistressVictim
import com.itantra.app.model.RescueConnectionMode
import com.itantra.app.model.VoiceStatus
import com.itantra.app.ui.components.BatteryIndicator
import com.itantra.app.ui.components.DigitalAudioVisualizer
import com.itantra.app.ui.theme.AccentBlue
import com.itantra.app.ui.theme.AccentBlueContainer
import com.itantra.app.ui.theme.BadgeMintContainer
import com.itantra.app.ui.theme.BadgeMintText
import com.itantra.app.ui.theme.RescueAmber
import com.itantra.app.ui.theme.RescueAmberContainer
import com.itantra.app.ui.theme.RescueAmberText
import com.itantra.app.ui.theme.SosRed
import com.itantra.app.ui.theme.SosRedContainer
import com.itantra.app.ui.theme.SosRedDark
import com.itantra.app.ui.theme.minimalColors
import com.itantra.app.viewmodel.MissionControlViewModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * World-Class Mission Rescue Screen:
 * 1. Pre-Boot Standby Mode:
 *    - Prominent, highlighted "BOOT RESCUE SYSTEM" hero dome with tactical radar aesthetics.
 *    - Off-grid search capabilities summary with zero technical jargon.
 * 2. Active Rescue Mode:
 *    - Top mission telemetry with real-time victim locator count.
 *    - Tactical Connection Control Console:
 *        - Real-time connection mode badge: STANDBY / 1-TO-1 DIRECT VOICE / BROADCASTING TO ALL.
 *        - One-Way Emergency Broadcast Trigger: Streams voice message to all nearby SOS victims simultaneously.
 *        - 1-to-1 Voice Link Card: Direct hands-free translated conversation with selected victim.
 * 3. 100% Offline Relative Tactical Compass Radar Map:
 *    - Dead-center positioning of the Rescuer (game-style tactical minimap).
 *    - Rotates dynamically by compass heading so the Rescuer's forward heading is ALWAYS facing UP.
 *    - Concentric metric distance rings (25m, 50m, 75m, 100m).
 *    - Cardinal direction markers (N, E, S, W) that rotate with the compass.
 *    - Animated sweep beam and glowing victim blips with distance labels.
 * 4. Fullscreen Tactical Map Mode:
 *    - Expandable via "Expand Map" button.
 *    - High-contrast close ("X") button at top right.
 *    - Range selection filters and victim selector drawer.
 * 5. Distress Victims Queue:
 *    - Rich victim cards with relative distances, friendly signal indicators, hazard tags,
 *      quoted distress messages in native language, and direct 1-to-1 connect buttons.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RescueScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.minimalColors
    val isRescueActive by viewModel.isRescueActive.collectAsState()
    val connectionMode by viewModel.rescueConnectionMode.collectAsState()
    val isBroadcastingToAll by viewModel.isBroadcastingToAll.collectAsState()
    val compassHeading by viewModel.compassHeading.collectAsState()
    val activeVictims by viewModel.activeDistressVictims.collectAsState()
    val connectedVictim by viewModel.connectedVictimIntercom.collectAsState()
    val selectedVictim by viewModel.selectedVictim.collectAsState()
    val isMapExpanded by viewModel.isMapExpanded.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isSpeakerphoneOn by viewModel.isSpeakerphoneOn.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val modelWarning by viewModel.modelWarningMessage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedLanguage = uiState.selectedLanguage
    val modelPacks by viewModel.modelPacks.collectAsState()
    val messageLogs by viewModel.messageLogs.collectAsState()
    val currentTranscript = uiState.currentTranscript
    val voiceStatus = uiState.voiceStatus
    val isVadSpeaking by viewModel.isVadSpeaking.collectAsState()
    val isPttActive by viewModel.isPttActive.collectAsState()
    val isModelInstalled = modelPacks.firstOrNull { it.iso == selectedLanguage.code || it.languageTag.startsWith(selectedLanguage.code) }?.isInstalled == true

    var showLanguageSheet by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()



    // Standby Button Press State Physics
    val bootButtonSource = remember { MutableInteractionSource() }
    val isBootPressed by bootButtonSource.collectIsPressedAsState()
    val bootButtonScale by animateFloatAsState(
        targetValue = if (isBootPressed) 0.94f else 1.0f,
        animationSpec = tween(120),
        label = "bootButtonScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // TOP TELEMETRY PILLS (Consistent with SOS & Walkie pages)
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRescueActive) RescueAmber else colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "iTANTRA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = " | RESCUE RADAR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when {
                            !isRescueActive -> colors.cardSecondaryBg
                            connectionMode == RescueConnectionMode.BROADCAST_ALL -> RescueAmberContainer
                            connectionMode == RescueConnectionMode.ONE_TO_ONE -> SosRedContainer
                            else -> BadgeMintContainer
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !isRescueActive -> colors.textSecondary
                                    connectionMode == RescueConnectionMode.BROADCAST_ALL -> RescueAmber
                                    connectionMode == RescueConnectionMode.ONE_TO_ONE -> SosRedDark
                                    else -> Color(0xFF059669)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !isRescueActive -> "RADAR STANDBY"
                            connectionMode == RescueConnectionMode.BROADCAST_ALL -> "BROADCAST ACTIVE"
                            connectionMode == RescueConnectionMode.ONE_TO_ONE -> "1-TO-1 CALL ACTIVE"
                            else -> "${activeVictims.size} VICTIMS IN RANGE"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !isRescueActive -> colors.textSecondary
                            connectionMode == RescueConnectionMode.BROADCAST_ALL -> RescueAmberText
                            connectionMode == RescueConnectionMode.ONE_TO_ONE -> SosRedDark
                            else -> BadgeMintText
                        }
                    )
                }
            }
        }

        // =========================================================================
        // RESCUE OPERATING LANGUAGE SELECTOR & 1-TAP DIALECT BAR
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Top Row: Selected Language summary & Switch trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showLanguageSheet = true },
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
                                    text = "RESCUE DIALECT: ",
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
                            val isInstalled = modelPacks.firstOrNull { it.iso == selectedLanguage.code || it.languageTag.startsWith(selectedLanguage.code) }?.isInstalled == true
                            Text(
                                text = if (isInstalled) "✓ Neural Pack Ready" else "⚠ Pack Not Installed (${selectedLanguage.downloadSizeMb} MB)",
                                fontSize = 11.sp,
                                fontWeight = if (isInstalled) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isInstalled) Color(0xFF059669) else RescueAmberText
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

                // 1-Tap Dialect Chips: Hindi, English, Bengali, Marathi, etc.
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
                        val isInstalled = modelPacks.firstOrNull { it.iso == lang.code || it.languageTag.startsWith(lang.code) }?.isInstalled == true
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
                                        color = if (isLangActive) Color(0xFFD1FAE5) else Color(0xFF059669)
                                    )
                                }
                            }
                        }
                    }

                    // "+More" Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.cardSecondaryBg)
                            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                            .clickable { showLanguageSheet = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
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
            }
        }

        // =========================================================================
        // VIEW SWITCHER: STANDBY (PRE-BOOT) vs ACTIVE RESCUE CONSOLE
        // =========================================================================
        if (!isRescueActive) {
            // =====================================================================
            // STANDBY MODE: PROMINENT TACTICAL BOOT-UP DOME
            // =====================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer subtle radar rings
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.outline, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.outline.copy(alpha = 0.5f), CircleShape)
                )

                // 4 Cardinal Micro-Ticks
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(width = 2.dp, height = 6.dp).background(colors.textSecondary.copy(alpha = 0.5f), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(width = 2.dp, height = 6.dp).background(colors.textSecondary.copy(alpha = 0.5f), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(width = 6.dp, height = 2.dp).background(colors.textSecondary.copy(alpha = 0.5f), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(width = 6.dp, height = 2.dp).background(colors.textSecondary.copy(alpha = 0.5f), RoundedCornerShape(1.dp)))

                // Highlighted Hero Tactile Button (Major Action to Boot Rescue System)
                Surface(
                    onClick = { viewModel.bootRescueSystem(true) },
                    shape = CircleShape,
                    color = Color.Transparent,
                    interactionSource = bootButtonSource,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .size(178.dp)
                        .scale(bootButtonScale)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFBBF24),
                                        Color(0xFFF59E0B),
                                        Color(0xFFD97706),
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
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.95f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SEARCH & RESCUE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "RESCUE",
                                fontSize = 36.sp,
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
                                        imageVector = Icons.Default.CellTower,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "BOOT SYSTEM",
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

            // Reassuring Info Banner (No jargon)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RescueAmberContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = RescueAmberText,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Automatic Distress Beacon Detection",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Booting activates offline radar scanning for nearby SOS victims. Phone vibrates when a beacon is found.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Quick Capabilities Row (Clean, zero technical jargon)
            // Quick Capabilities (Clean, 2x2 grid, zero technical jargon)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("100% Offline Minimap", "Compass Oriented").forEach { feature ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.cardSecondaryBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = feature,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1-Way All Broadcast", "1-to-1 Voice Link").forEach { feature ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.cardSecondaryBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = feature,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        } else {
            // =====================================================================
            // ACTIVE RESCUE CONSOLE (Booted Up)
            // =====================================================================

            // 1. TACTICAL CONNECTION & BROADCAST CONTROL CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x12000000))
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header with Active Status & Shutdown button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (connectionMode) {
                                            RescueConnectionMode.BROADCAST_ALL -> RescueAmberContainer
                                            RescueConnectionMode.ONE_TO_ONE -> SosRedContainer
                                            else -> AccentBlueContainer
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = when (connectionMode) {
                                        RescueConnectionMode.BROADCAST_ALL -> "📢 BROADCAST TO ALL"
                                        RescueConnectionMode.ONE_TO_ONE -> "● 1-TO-1 VOICE LINK"
                                        else -> "● SCANNING VICINITY"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (connectionMode) {
                                        RescueConnectionMode.BROADCAST_ALL -> RescueAmberText
                                        RescueConnectionMode.ONE_TO_ONE -> SosRedDark
                                        else -> AccentBlue
                                    }
                                )
                            }
                        }

                        // Highlighted "Leave Rescue" Button (High visibility, prominent)
                        Surface(
                            onClick = { viewModel.bootRescueSystem(false) },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEE2E2),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Leave Rescue",
                                    tint = SosRedDark,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "LEAVE RESCUE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = SosRedDark
                                )
                            }
                        }
                    }

                    // 1-Way Emergency Broadcast Trigger Button
                    Surface(
                        onClick = { viewModel.toggleBroadcastToAll() },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isBroadcastingToAll) RescueAmberContainer else if (colors.isDark) Color(0xFF261E14) else Color(0xFFFFFBEB),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isBroadcastingToAll) RescueAmber else if (colors.isDark) Color(0xFF78350F) else Color(0xFFFCD34D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isBroadcastingToAll) RescueAmber else if (colors.isDark) Color(0xFF451A03) else Color(0xFFFDE68A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = "Broadcast",
                                        tint = if (isBroadcastingToAll) Color.White else RescueAmberText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.padding(end = 8.dp)) {
                                    Text(
                                        text = if (isBroadcastingToAll) "BROADCASTING TO ALL" else "Broadcast to All (1-Way)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RescueAmberText,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isBroadcastingToAll) "Live transmitting to ${activeVictims.size} targets..." else "Stream announcement to all SOS phones",
                                        fontSize = 11.sp,
                                        color = if (colors.isDark) Color(0xFFFCD34D) else Color(0xFF92400E),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isBroadcastingToAll) RescueAmber else colors.surface)
                                    .border(1.dp, if (isBroadcastingToAll) Color.Transparent else if (colors.isDark) Color(0xFF78350F) else Color(0xFFFCD34D), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isBroadcastingToAll) "STOP" else "BROADCAST",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBroadcastingToAll) Color.White else RescueAmberText,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // =======================================================
                    // RESCUE CALL CONSOLE CARD (Similar to Walkie-Talkie Mode)
                    // (Features Big Mic, Big Speaker, and Big Disconnect Button)
                    // =======================================================
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (connectedVictim != null) (if (colors.isDark) Color(0xFF3B1219) else Color(0xFFFEF2F2))
                                else if (isBroadcastingToAll) (if (colors.isDark) Color(0xFF2E1C0C) else Color(0xFFFFFBEB))
                                else colors.cardSecondaryBg
                            )
                            .border(
                                width = 1.dp,
                                color = if (connectedVictim != null) (if (colors.isDark) Color(0xFF991B1B) else Color(0xFFFCA5A5))
                                else if (isBroadcastingToAll) (if (colors.isDark) Color(0xFF78350F) else Color(0xFFFCD34D))
                                else colors.outline,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(16.dp)
                    ) {
                        val activeVictimLink = connectedVictim
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Room / Call Info Header (Zero distress quote jargon)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = when {
                                            activeVictimLink != null -> "CONNECTED: ${activeVictimLink.callsign}"
                                            isBroadcastingToAll -> "TRANSMITTING TO ALL VICTIMS"
                                            else -> "RESCUE AUDIO CONSOLE"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            activeVictimLink != null -> if (colors.isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                                            isBroadcastingToAll -> RescueAmberText
                                            else -> colors.textPrimary
                                        },
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when {
                                            activeVictimLink != null -> "~${activeVictimLink.distanceMeters}m away • Direct 2-Way Voice"
                                            isBroadcastingToAll -> "1-Way Rescuer Announcement Channel"
                                            else -> "Hands-Free VAD Voice • Select victim to link"
                                        },
                                        fontSize = 11.sp,
                                        color = when {
                                            activeVictimLink != null -> if (colors.isDark) Color(0xFFF87171) else Color(0xFFB91C1C)
                                            isBroadcastingToAll -> if (colors.isDark) Color(0xFFFCD34D) else Color(0xFF92400E)
                                            else -> colors.textSecondary
                                        },
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                connectedVictim != null -> SosRed
                                                isBroadcastingToAll -> RescueAmber
                                                else -> BadgeMintContainer
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when {
                                            connectedVictim != null -> "LIVE CALL"
                                            isBroadcastingToAll -> "ON AIR"
                                            else -> "READY"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            connectedVictim != null -> Color.White
                                            isBroadcastingToAll -> Color.White
                                            else -> BadgeMintText
                                        }
                                    )
                                }
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        if (connectedVictim != null) (if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFECACA))
                                        else if (isBroadcastingToAll) (if (colors.isDark) Color(0xFF78350F) else Color(0xFFFDE68A))
                                        else colors.outline
                                    )
                            )

                            // Model Download Warning Banner (if model pack is missing)
                            if (modelWarning != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7))
                                        .border(1.dp, RescueAmber.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
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
                                                imageVector = Icons.Default.WarningAmber,
                                                contentDescription = null,
                                                tint = RescueAmber,
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
                                                tint = RescueAmber,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Digital Gray-Line Audio Visualizer
                            DigitalAudioVisualizer(
                                audioLevel = audioLevel,
                                isActive = connectedVictim != null || isBroadcastingToAll,
                                label = when {
                                    connectedVictim != null -> "RESCUER 2-WAY INTERCOM"
                                    isBroadcastingToAll -> "RESCUE BROADCAST ON AIR"
                                    else -> "AUDIO STANDBY"
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Big Hands-Free Audio Controls Row (Big Mic, Big Speaker, Big Disconnect)
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
                                        onClick = { viewModel.toggleMicMute() },
                                        shape = CircleShape,
                                        color = if (isMicMuted) (if (colors.isDark) Color(0xFF450A0A) else Color(0xFFFEE2E2)) else colors.surface,
                                        shadowElevation = 3.dp,
                                        modifier = Modifier.size(58.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                                contentDescription = "Mute Mic",
                                                tint = if (isMicMuted) SosRedDark else colors.textPrimary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isMicMuted) "Unmute" else "Mute Mic",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isMicMuted) SosRedDark else colors.textSecondary
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
                                            when {
                                                connectedVictim != null -> viewModel.disconnectVictimIntercom()
                                                isBroadcastingToAll -> viewModel.toggleBroadcastToAll()
                                                else -> viewModel.bootRescueSystem(false)
                                            }
                                        },
                                        shape = CircleShape,
                                        color = SosRed,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.size(58.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CallEnd,
                                                contentDescription = "Disconnect",
                                                tint = Color.White,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = when {
                                            connectedVictim != null -> "End Call"
                                            isBroadcastingToAll -> "Stop"
                                            else -> "Standby"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SosRedDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =====================================================
            // LIVE TRANSCRIPTION CARD (Rescuer side)
            // =====================================================
            if (connectedVictim != null || isBroadcastingToAll || isRescueActive || messageLogs.isNotEmpty()) {
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
                                            if (isModelInstalled) BadgeMintContainer
                                            else Color(0xFFFEF3C7)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isModelInstalled) "✓ AI STT ACTIVE" else "⚠️ PACK REQUIRED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isModelInstalled) BadgeMintText else Color(0xFF92400E)
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

                        // Active speaking recording pulse banner
                        if (isVadSpeaking || isPttActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RescueAmber.copy(alpha = 0.15f))
                                    .border(0.5.dp, RescueAmber.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
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
                                            .background(RescueAmber)
                                    )
                                    Text(
                                        text = "RECORDING SPEECH... (Release button or pause to send)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RescueAmberText
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
                                            isListening -> RescueAmber.copy(alpha = 0.15f)
                                            isTranscribing -> BadgeMintContainer.copy(alpha = 0.6f)
                                            isWarning -> if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
                                            colors.isDark -> Color(0xFF1E3A5F).copy(alpha = 0.6f)
                                            else -> Color(0xFFEFF6FF)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isListening -> RescueAmber.copy(alpha = 0.6f)
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
                                            isListening -> RescueAmberText
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
                                        text = "🎙️ Rescue Intercom Channel Ready",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (isModelInstalled)
                                            "Hands-free voice active. Neural AI STT will transcribe and broadcast your speech to victims over mesh for instant TTS playback."
                                        else
                                            "Neural STT pack is not downloaded. Voice-to-text requires the offline language model in Model Hub.",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                        lineHeight = 15.sp
                                    )
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
                                            else RescueAmber.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (msg.isLocal) "↑" else "↓",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.isLocal) AccentBlue else RescueAmber
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
                                        text = if (msg.isLocal) "You • ${msg.senderCallsign}" else "Victim • ${msg.senderCallsign}",
                                        fontSize = 10.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // =====================================================================
            // 2. MINIMAL BRIGHT-THEME TACTICAL MAP CARD
            // =====================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x0A000000))
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
                    .clickable { viewModel.toggleMapExpanded(true) } // Clicking ANYWHERE on the map expands it!
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Map Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TACTICAL RESCUE MAP",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = "Real-time compass orientation • Tap anywhere to expand",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }

                        // Expand Map Badge / Trigger
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.cardSecondaryBg)
                                .clickable { viewModel.toggleMapExpanded(true) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Expand",
                                    tint = colors.accent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Expand",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Minimal Bright Vector Map Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (colors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                            .clickable { viewModel.toggleMapExpanded(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        MinimalBrightMapCanvas(
                            compassHeading = compassHeading,
                            victims = activeVictims,
                            selectedVictim = selectedVictim,
                            connectedVictim = connectedVictim,
                            onVictimSelected = {
                                viewModel.selectVictim(it)
                                viewModel.toggleMapExpanded(true)
                            },
                            onMapTapped = { viewModel.toggleMapExpanded(true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Map Readout Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HEADING: ${compassHeading.toInt()}° • ALL VICTIMS VISIBLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        Text(
                            text = "Tap map to zoom & explore",
                            fontSize = 10.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // =====================================================================
            // 3. DISCOVERED SOS VICTIMS QUEUE (Streamlined Modern Cards)
            // =====================================================================
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISTRESS BEACONS IN RANGE (${activeVictims.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.textSecondary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SosRedContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Vibrating on Alert",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SosRedDark
                        )
                    }
                }

                activeVictims.forEach { victim ->
                    val isThisVictimConnected = connectedVictim?.id == victim.id
                    val isThisVictimSelected = selectedVictim?.id == victim.id
                    val someoneElseConnected = connectedVictim != null && !isThisVictimConnected

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x08000000))
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surface)
                            .border(
                                width = if (isThisVictimConnected) 2.dp else if (isThisVictimSelected) 1.5.dp else 1.dp,
                                color = if (isThisVictimConnected) SosRed else if (isThisVictimSelected) AccentBlue else colors.outline,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { viewModel.selectVictim(victim) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isThisVictimConnected) SosRedContainer else if (colors.isDark) Color(0xFF3B1219) else Color(0xFFFEE2E2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = SosRedDark,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = victim.callsign,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "~${victim.distanceMeters}m away" +
                                                (victim.identityLabel?.let { " • $it" } ?: ""),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.accent
                                        )
                                        Text(
                                            text = "•",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                        BatteryIndicator(
                                            batteryPercent = victim.batteryPercent,
                                            heightDp = 10.dp
                                        )
                                    }
                                }
                            }

                            // Action Button: Connect vs Switch vs End
                            if (isThisVictimConnected) {
                                Button(
                                    onClick = { viewModel.disconnectVictimIntercom() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SosRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneDisabled,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "End", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (someoneElseConnected) {
                                Button(
                                    onClick = { viewModel.switchVictimIntercom(victim) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (colors.isDark) Color(0xFF2E1C0C) else Color(0xFFFFFBEB),
                                        contentColor = if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFB45309)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (colors.isDark) Color(0xFF78350F) else Color(0xFFFCD34D)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Switch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.connectVictimIntercom(victim) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }

    // =============================================================================
    // FULLSCREEN INTERACTIVE BRIGHT TACTICAL MAP (Pinch-to-zoom, Pan, Compass)
    // =============================================================================
    if (isMapExpanded) {
        Dialog(
            onDismissRequest = { viewModel.toggleMapExpanded(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            var zoomScale by remember { mutableFloatStateOf(1.2f) }
            var panOffset by remember { mutableStateOf(Offset.Zero) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (colors.isDark) Color(0xFF0B0F19) else Color(0xFFF8FAFC))
            ) {
                // 1. Solid Top App Bar covering the status bar and header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent)
                                    )
                                    Text(
                                        text = "TACTICAL RESCUE MAP",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp,
                                        color = colors.textPrimary
                                    )
                                }
                                Text(
                                    text = "Heading: ${compassHeading.toInt()}° • Pinch to Zoom / Drag to Pan",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Close (X) Button (Clean, high-contrast)
                            Surface(
                                onClick = { viewModel.toggleMapExpanded(false) },
                                shape = CircleShape,
                                color = colors.cardSecondaryBg,
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(1.dp, colors.outline, CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Map",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Map Viewport Box (Strictly fills the space between top bar and bottom bar)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    // Minimal Bright Vector Map Canvas
                    MinimalBrightMapCanvas(
                        compassHeading = compassHeading,
                        victims = activeVictims,
                        selectedVictim = selectedVictim,
                        connectedVictim = connectedVictim,
                        onVictimSelected = { viewModel.selectVictim(it) },
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        showCardLabels = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(0.6f, 4.0f)
                                    panOffset += pan
                                }
                            }
                    )

                    // Floating Zoom & Recenter Controls (Right Side)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Zoom In (+)
                        Surface(
                            onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4.0f) },
                            shape = CircleShape,
                            color = colors.surface,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.dp, colors.outline, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoom In",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Zoom Out (-)
                        Surface(
                            onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.6f) },
                            shape = CircleShape,
                            color = colors.surface,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.dp, colors.outline, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Zoom Out",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Recenter
                        Surface(
                            onClick = {
                                zoomScale = 1.2f
                                panOffset = Offset.Zero
                            },
                            shape = CircleShape,
                            color = colors.surface,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.dp, colors.outline, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Recenter",
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Bottom Floating Selected Victim Card
                    val victimToInspect = selectedVictim ?: activeVictims.firstOrNull()
                    if (victimToInspect != null) {
                        val isConnected = connectedVictim?.id == victimToInspect.id
                        val someoneElseConnected = connectedVictim != null && !isConnected

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                                .align(Alignment.BottomCenter)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x15000000))
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isConnected) SosRedContainer else if (colors.isDark) Color(0xFF3B1219) else Color(0xFFFEE2E2)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = SosRedDark,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = victimToInspect.callsign,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "~${victimToInspect.distanceMeters}m away" +
                                                    (victimToInspect.identityLabel?.let { " • $it" } ?: ""),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.accent
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 11.sp,
                                                color = colors.textSecondary
                                            )
                                            BatteryIndicator(
                                                batteryPercent = victimToInspect.batteryPercent,
                                                heightDp = 10.dp
                                            )
                                        }
                                    }
                                }

                                if (isConnected) {
                                    Button(
                                        onClick = { viewModel.disconnectVictimIntercom() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SosRed,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneDisabled,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("End", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else if (someoneElseConnected) {
                                    Button(
                                        onClick = { viewModel.switchVictimIntercom(victimToInspect) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (colors.isDark) Color(0xFF2E1C0C) else Color(0xFFFFFBEB),
                                            contentColor = if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFB45309)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (colors.isDark) Color(0xFF78350F) else Color(0xFFFCD34D)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = null,
                                            tint = if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Switch", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.connectVictimIntercom(victimToInspect) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.accent,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Connect", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Solid Bottom Scrim covering the system navigation buttons area
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shadowElevation = 4.dp
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet for All 10 Supported Indian Dialects
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = colors.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Rescuer Language",
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
                        val isInstalled = modelPacks.firstOrNull { it.iso == lang.code || it.languageTag.startsWith(lang.code) }?.isInstalled == true
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

/**
 * Minimal Bright-Theme Map Canvas:
 * - Crisp, bright off-white cartographic surface (no submarine theme!).
 * - Subtle road networks and city block geometry in light tones.
 * - Rotates dynamically with phone's real compass heading so rotating phone turns the map.
 * - Rescuer blue location puck at center with directional vision cone and subtle pulsing halo.
 * - All victims rendered directly as sleek modern pin markers with small floating cards ("SECTOR-4B • 14m").
 * - Interactive tap selection for victim pins.
 */
@Composable
fun MinimalBrightMapCanvas(
    compassHeading: Float,
    victims: List<DistressVictim>,
    selectedVictim: DistressVictim?,
    connectedVictim: DistressVictim?,
    onVictimSelected: (DistressVictim) -> Unit,
    modifier: Modifier = Modifier,
    zoomScale: Float = 1.0f,
    panOffset: Offset = Offset.Zero,
    showCardLabels: Boolean = true,
    onMapTapped: (() -> Unit)? = null
) {
    val colors = MaterialTheme.minimalColors
    val isDark = colors.isDark

    val infiniteTransition = rememberInfiniteTransition(label = "rescuerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val currentCompassHeading by rememberUpdatedState(compassHeading)
    val currentVictims by rememberUpdatedState(victims)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentPanOffset by rememberUpdatedState(panOffset)
    val currentOnVictimSelected by rememberUpdatedState(onVictimSelected)
    val currentOnMapTapped by rememberUpdatedState(onMapTapped)

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
            val density = this
            detectTapGestures { tapOffset ->
                val cx = size.width / 2f + currentPanOffset.x
                val cy = size.height / 2f + currentPanOffset.y
                val maxRadius = minOf(size.width, size.height) * 0.42f * currentZoomScale

                var hitVictim: DistressVictim? = null
                val pinHitRadiusPx = with(density) { 42.dp.toPx() }
                val badgeHPx = with(density) { 26.dp.toPx() }
                val badgeWPx = with(density) { 150.dp.toPx() }
                val badgePadPx = with(density) { 16.dp.toPx() }

                currentVictims.forEach { victim ->
                    val angleRad = Math.toRadians(victim.relativeBearingDegrees.toDouble() - 90.0)
                    val normDist = (victim.distanceMeters / 90f).coerceIn(0.15f, 0.92f)
                    val r = normDist * maxRadius
                    val vx = cx + (r * cos(angleRad)).toFloat()
                    val vy = cy + (r * sin(angleRad)).toFloat()

                    val d2 = (tapOffset.x - vx) * (tapOffset.x - vx) + (tapOffset.y - vy) * (tapOffset.y - vy)
                    val cardY = vy - with(density) { 24.dp.toPx() }
                    val inBadge = tapOffset.x >= (vx - badgeWPx / 2f - badgePadPx) &&
                                  tapOffset.x <= (vx + badgeWPx / 2f + badgePadPx) &&
                                  tapOffset.y >= (cardY - badgePadPx) &&
                                  tapOffset.y <= (vy + badgePadPx)

                    if (d2 <= pinHitRadiusPx * pinHitRadiusPx || inBadge) {
                        hitVictim = victim
                    }
                }

                if (hitVictim != null) {
                    currentOnVictimSelected(hitVictim)
                } else {
                    currentOnMapTapped?.invoke()
                }
            }
        }
    ) {
        clipRect {
            val cx = size.width / 2f + panOffset.x
            val cy = size.height / 2f + panOffset.y
            val maxRadius = minOf(size.width, size.height) * 0.42f * zoomScale

            // 1. Bright / Dark Minimal Cartographic Background
            drawRect(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))

            // 2. Subtle Cartographic Road / Street Grid Network (Rotates with compass)
            withTransform({
                rotate(-compassHeading, pivot = Offset(cx, cy))
            }) {
            val blockSize = 55.dp.toPx() * zoomScale
            val blockColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
            for (ix in -4..4) {
                for (iy in -4..4) {
                    if ((ix + iy) % 2 == 0) {
                        val bx = cx + ix * (blockSize + 16.dp.toPx() * zoomScale)
                        val by = cy + iy * (blockSize + 16.dp.toPx() * zoomScale)
                        drawRoundRect(
                            color = blockColor,
                            topLeft = Offset(bx, by),
                            size = Size(blockSize, blockSize),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }
            }

            val roadWidth = 10.dp.toPx() * zoomScale
            val roadOutlineWidth = 12.dp.toPx() * zoomScale
            val roadColor = if (isDark) Color(0xFF131D2E) else Color(0xFFFFFFFF)
            val roadBorder = if (isDark) Color(0xFF2E3D52) else Color(0xFFE2E8F0)

            for (i in -3..3) {
                val offsetVal = i * (blockSize + 16.dp.toPx() * zoomScale)
                drawLine(
                    color = roadBorder,
                    start = Offset(cx - size.width * 2, cy + offsetVal),
                    end = Offset(cx + size.width * 2, cy + offsetVal),
                    strokeWidth = roadOutlineWidth
                )
                drawLine(
                    color = roadColor,
                    start = Offset(cx - size.width * 2, cy + offsetVal),
                    end = Offset(cx + size.width * 2, cy + offsetVal),
                    strokeWidth = roadWidth
                )

                drawLine(
                    color = roadBorder,
                    start = Offset(cx + offsetVal, cy - size.height * 2),
                    end = Offset(cx + offsetVal, cy + size.height * 2),
                    strokeWidth = roadOutlineWidth
                )
                drawLine(
                    color = roadColor,
                    start = Offset(cx + offsetVal, cy - size.height * 2),
                    end = Offset(cx + offsetVal, cy + size.height * 2),
                    strokeWidth = roadWidth
                )
            }
        }

        // 3. Subtle Distance Reference Rings
        val distanceSteps = listOf(0.33f to "25m", 0.66f to "50m", 1.0f to "100m")
        distanceSteps.forEach { (step, _) ->
            val r = maxRadius * step
            drawCircle(
                color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // 4. Cardinal Compass Indicators on Map Boundary (N, E, S, W)
        val cardinalPaint = Paint().apply {
            color = if (isDark) android.graphics.Color.parseColor("#94A3B8") else android.graphics.Color.parseColor("#64748B")
            textSize = 10.sp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val northPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#EF4444")
            textSize = 11.sp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val cardinalDirections = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
        cardinalDirections.forEach { (label, bearing) ->
            val angleRad = Math.toRadians(bearing.toDouble() - 90.0)
            val labelR = maxRadius * 1.06f
            val lx = cx + (labelR * cos(angleRad)).toFloat()
            val ly = cy + (labelR * sin(angleRad)).toFloat() + 3.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText(
                label,
                lx,
                ly,
                if (label == "N") northPaint else cardinalPaint
            )
        }

        // 5. Victim Map Pins & Small Floating Cards (True relative coordinates)
        val cardTextPaint = Paint().apply {
            color = if (isDark) android.graphics.Color.parseColor("#F8FAFC") else android.graphics.Color.parseColor("#0F172A")
            textSize = 10.sp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cardDistPaint = Paint().apply {
            color = if (isDark) android.graphics.Color.parseColor("#60A5FA") else android.graphics.Color.parseColor("#2563EB")
            textSize = 9.sp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        victims.forEach { victim ->
            val angleRad = Math.toRadians(victim.relativeBearingDegrees.toDouble() - 90.0)
            val normDist = (victim.distanceMeters / 90f).coerceIn(0.15f, 0.92f)
            val r = normDist * maxRadius
            val vx = cx + (r * cos(angleRad)).toFloat()
            val vy = cy + (r * sin(angleRad)).toFloat()

            val isConnected = connectedVictim?.id == victim.id
            val isSelected = selectedVictim?.id == victim.id

            // Selection/Connection outer glow
            if (isSelected || isConnected) {
                drawCircle(
                    color = if (isConnected) Color(0x35EF4444) else Color(0x352563EB),
                    radius = 16.dp.toPx(),
                    center = Offset(vx, vy)
                )
            }

            // Pin marker (border adapts to theme)
            drawCircle(
                color = if (isDark) Color(0xFF0F172A) else Color.White,
                radius = 8.dp.toPx(),
                center = Offset(vx, vy)
            )
            drawCircle(
                color = if (isConnected) Color(0xFFEF4444) else if (isSelected) Color(0xFF0284C7) else Color(0xFFF43F5E),
                radius = 6.dp.toPx(),
                center = Offset(vx, vy)
            )

            // Small Floating Card above pin
            if (showCardLabels) {
                val shortName = victim.callsign.replace("VICTIM-", "")
                val labelText = "$shortName • ${victim.distanceMeters}m"
                val textWidth = cardTextPaint.measureText(labelText)
                val cardW = textWidth + 14.dp.toPx()
                val cardH = 18.dp.toPx()
                val cardX = vx - cardW / 2f
                val cardY = vy - 24.dp.toPx()

                drawRoundRect(
                    color = if (isDark) Color(0xFF1E293B) else Color.White,
                    topLeft = Offset(cardX, cardY),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
                drawRoundRect(
                    color = if (isConnected) Color(0xFFEF4444) else if (isSelected) (if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)) else (if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)),
                    topLeft = Offset(cardX, cardY),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    style = Stroke(width = if (isConnected || isSelected) 1.5.dp.toPx() else 1.dp.toPx())
                )

                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    vx,
                    cardY + cardH * 0.72f,
                    if (isSelected) cardDistPaint else cardTextPaint
                )
            }
        }

        // 6. Rescuer GPS Location Puck at Center with rotating vision cone & chevron
        withTransform({
            rotate(compassHeading, pivot = Offset(cx, cy))
        }) {
            val conePath = Path().apply {
                moveTo(cx, cy)
                lineTo(cx - 24.dp.toPx(), cy - 55.dp.toPx())
                lineTo(cx + 24.dp.toPx(), cy - 55.dp.toPx())
                close()
            }
            drawPath(
                path = conePath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x3338BDF8), Color(0x0038BDF8)),
                    startY = cy - 50.dp.toPx(),
                    endY = cy
                )
            )
            // Forward Chevron Arrow
            drawLine(
                color = Color.White,
                start = Offset(cx, cy - 10.dp.toPx()),
                end = Offset(cx - 4.dp.toPx(), cy - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.White,
                start = Offset(cx, cy - 10.dp.toPx()),
                end = Offset(cx + 4.dp.toPx(), cy - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Center Rescuer Marker
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.3f),
            radius = 12.dp.toPx(),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFF0284C7),
            radius = 6.dp.toPx(),
            center = Offset(cx, cy)
        )
        }
    }
}
