package com.itantra.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.model.AiModelCategory
import com.itantra.app.model.DownloadedAiModel
import com.itantra.app.ui.theme.AccentBlue
import com.itantra.app.ui.theme.AccentBlueContainer
import com.itantra.app.ui.theme.BadgeMintContainer
import com.itantra.app.ui.theme.BadgeMintText
import com.itantra.app.ui.theme.MeshGreen
import com.itantra.app.ui.theme.MeshGreenText
import com.itantra.app.ui.theme.MinimalColorsInstance
import com.itantra.app.ui.theme.SosRed
import com.itantra.app.ui.theme.SosRedContainer
import com.itantra.app.ui.theme.SosRedDark
import com.itantra.app.ui.theme.minimalColors
import com.itantra.app.viewmodel.MissionControlViewModel

/**
 * World-Class Tactical Settings Screen:
 * 1. Device Callsign & Identity Card
 * 2. Appearance: 3-Way Theme Switcher (Light Air • Dark Stealth • System Auto)
 * 3. On-Device AI Neural Models Management (List, Storage Bar, Deletion with Confirmation Dialog)
 * 4. Radio & Disaster Mesh Tuning (TX Power, Beacon Frequency, Hop Limit)
 * 5. Voice & Audio Tuning (VAD Sensitivity, Noise Suppression, SOS Override)
 * 6. Tactical Privacy, Map Cache & Sensor Health Diagnostics
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.minimalColors
    val uiState by viewModel.uiState.collectAsState()
    val callsign by viewModel.callsign.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val txPower by viewModel.txPower.collectAsState()
    val beaconInterval by viewModel.beaconInterval.collectAsState()
    val meshHopLimit by viewModel.meshHopLimit.collectAsState()
    val vadSensitivity by viewModel.vadSensitivity.collectAsState()
    val noiseSuppressionEnabled by viewModel.noiseSuppressionEnabled.collectAsState()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val zeroLogPrivacy by viewModel.zeroLogPrivacy.collectAsState()
    val mapCacheSizeMb by viewModel.mapCacheSizeMb.collectAsState()

    val totalModelStorageMb = remember(downloadedModels) {
        downloadedModels.sumOf { it.sizeMb }
    }

    // State for delete confirmation dialog
    var modelToDelete by remember { mutableStateOf<DownloadedAiModel?>(null) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. DEVICE IDENTITY & CALLSIGN
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.badgeBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "RADIO CALLSIGN & NODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = callsign,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "MAC: 00:1B:44:11:3A:B7 • Full-Duplex",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.badgeMintContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Transceiver",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.badgeMintText
                    )
                }
            }
        }

        // ==========================================
        // 2. APPEARANCE & THEME SWITCHER
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "APPEARANCE & THEME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.textSecondary
                    )
                }

                // 3-Way Segmented Theme Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionButton(
                        title = "Light Air",
                        icon = Icons.Default.LightMode,
                        isSelected = uiState.themeMode == "light",
                        onClick = { viewModel.setThemeMode("light") },
                        modifier = Modifier.weight(1f),
                        colors = colors
                    )

                    ThemeOptionButton(
                        title = "Dark Stealth",
                        icon = Icons.Default.DarkMode,
                        isSelected = uiState.themeMode == "dark",
                        onClick = { viewModel.setThemeMode("dark") },
                        modifier = Modifier.weight(1f),
                        colors = colors
                    )

                    ThemeOptionButton(
                        title = "System Auto",
                        icon = Icons.Default.AutoAwesome,
                        isSelected = uiState.themeMode == "system",
                        onClick = { viewModel.setThemeMode("system") },
                        modifier = Modifier.weight(1f),
                        colors = colors
                    )
                }

                HorizontalDivider(color = colors.outline, thickness = 1.dp)

                // Screen Awake Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Screen Awake during Mission",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Prevents device standby while SOS or Walkie is active",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Switch(
                        checked = keepScreenAwake,
                        onCheckedChange = { viewModel.setKeepScreenAwake(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent
                        )
                    )
                }
            }
        }

        // ==========================================
        // 3. DOWNLOADED ON-DEVICE AI MODELS
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header with reload button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.badgePurpleContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = colors.badgePurpleText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ON-DEVICE AI MODELS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "100% Offline • Zero Internet Required",
                                fontSize = 11.sp,
                                color = colors.badgeMintText
                            )
                        }
                    }

                    if (downloadedModels.size < 7) {
                        TextButton(
                            onClick = { viewModel.restoreDefaultModels() }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Restore All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent
                                )
                            }
                        }
                    }
                }

                // Storage usage breakdown bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardSecondaryBg)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Model Storage:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$totalModelStorageMb MB",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = "${downloadedModels.size} Engines Installed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.badgeMintText
                            )
                        }

                        // Progress representation (relative to simulated 1024 MB quota)
                        LinearProgressIndicator(
                            progress = { (totalModelStorageMb / 1024f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = colors.accent,
                            trackColor = colors.outline,
                        )
                    }
                }

                HorizontalDivider(color = colors.outline, thickness = 1.dp)

                // Models List
                if (downloadedModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No on-device models installed",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.restoreDefaultModels() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Re-download Standard Pack", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        downloadedModels.forEach { model ->
                            ModelItemCard(
                                model = model,
                                colors = colors,
                                onDeleteClick = { modelToDelete = model }
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. DISASTER MESH & RADIO TUNING (DUMMY OPTIONS)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "DISASTER MESH & RADIO TUNING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.textSecondary
                    )
                }

                // Setting 1: Radio Transmission Range
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Radio TX Power & Range",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = txPower,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Low (100m)", "Balanced (500m)", "Max (1.5km)").forEach { option ->
                            val isSel = txPower.startsWith(option.substringBefore(" "))
                            SegmentedOptionChip(
                                label = option,
                                isSelected = isSel,
                                onClick = { viewModel.setTxPower(option) },
                                modifier = Modifier.weight(1f),
                                colors = colors
                            )
                        }
                    }
                }

                // Setting 2: Beacon Ping Frequency
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Beacon Broadcast Frequency",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Every ${beaconInterval}s",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15 to "15s (Rapid)", 30 to "30s (Default)", 60 to "60s (Saver)").forEach { (sec, label) ->
                            val isSel = beaconInterval == sec
                            SegmentedOptionChip(
                                label = label,
                                isSelected = isSel,
                                onClick = { viewModel.setBeaconInterval(sec) },
                                modifier = Modifier.weight(1f),
                                colors = colors
                            )
                        }
                    }
                }

                // Setting 3: Mesh Hop Limit
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mesh Relay Multi-Hop Limit",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "$meshHopLimit Hops",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(3 to "3 Hops", 5 to "5 Hops (Rec)", 7 to "7 Hops (Deep)").forEach { (hops, label) ->
                            val isSel = meshHopLimit == hops
                            SegmentedOptionChip(
                                label = label,
                                isSelected = isSel,
                                onClick = { viewModel.setMeshHopLimit(hops) },
                                modifier = Modifier.weight(1f),
                                colors = colors
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. VOICE & SENSOR AUDIO TUNING
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "VOICE & SENSOR AUDIO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.textSecondary
                    )
                }

                // VAD Sensitivity
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hands-Free VAD Sensitivity",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = vadSensitivity,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Low Noise", "Balanced", "High Sensitivity").forEach { level ->
                            val isSel = vadSensitivity.startsWith(level.substringBefore(" "))
                            SegmentedOptionChip(
                                label = level,
                                isSelected = isSel,
                                onClick = { viewModel.setVadSensitivity(level) },
                                modifier = Modifier.weight(1f),
                                colors = colors
                            )
                        }
                    }
                }

                // Noise Suppression Filter Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Noise Suppression Filter",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Filters heavy wind, rain, and rubble acoustic noise",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Switch(
                        checked = noiseSuppressionEnabled,
                        onCheckedChange = { viewModel.setNoiseSuppressionEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent
                        )
                    )
                }

                // Force Max Volume Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Force Max Volume on SOS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Overrides device silent mode during emergency intercom",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Switch(
                        checked = uiState.forceMaxVolumeAlerts,
                        onCheckedChange = { viewModel.setForceMaxVolumeAlerts(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent
                        )
                    )
                }

                // Battery Saver Duty-Cycle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Battery Saver Duty-Cycling",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Paces BLE radio scanning when stationary to extend battery",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Switch(
                        checked = uiState.isLowPowerListeningEnabled,
                        onCheckedChange = { viewModel.setLowPowerListeningEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent
                        )
                    )
                }
            }
        }

        // ==========================================
        // 6. TACTICAL PRIVACY, STORAGE & SENSORS
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TACTICAL PRIVACY & SENSORS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.textSecondary
                    )
                }

                // Zero-Log Privacy Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zero-Log Tactical Privacy",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Keeps voice and message buffers in volatile RAM only",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Switch(
                        checked = zeroLogPrivacy,
                        onCheckedChange = { viewModel.setZeroLogPrivacy(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent
                        )
                    )
                }

                // Offline Map Tile Cache Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline Radar Map Cache",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (mapCacheSizeMb > 0) "$mapCacheSizeMb MB cached pre-disaster tiles" else "Cache cleared",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearMapCache() },
                        enabled = mapCacheSizeMb > 0,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = colors.outline, thickness = 1.dp)

                // Hardware Sensor Diagnostics
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Hardware Sensor Health",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SensorStatusBadge("GPS 3D Fix", "Active", colors.badgeMintContainer, colors.badgeMintText)
                        SensorStatusBadge("Compass / Gyro", "Calibrated", colors.badgeMintContainer, colors.badgeMintText)
                        SensorStatusBadge("BLE Mesh", "Advertising", colors.badgeMintContainer, colors.badgeMintText)
                        SensorStatusBadge("Wi-Fi Direct", "Ready", colors.badgeBlueContainer, colors.badgeBlueText)
                    }
                }

                // Emergency Wipe Action
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.errorContainer)
                        .clickable { showWipeConfirmDialog = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emergency Local Data Wipe",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.error
                        )
                    }
                }
            }
        }

        // App Version Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "iTantra Tactical Mesh • Version 2.4.0",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Text(
                    text = "Zero-Cloud Sovereign Neural Architecture",
                    fontSize = 10.sp,
                    color = colors.textSecondary.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // =========================================================================
    // DIALOG 1: DELETE MODEL CONFIRMATION POPUP
    // =========================================================================
    if (modelToDelete != null) {
        val target = modelToDelete!!
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Delete AI Model?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to remove \"${target.name}\" from offline storage?",
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "This will immediately free up ${target.sizeMb} MB. Offline ${target.category.label.lowercase()} for ${target.language} will be disabled until re-downloaded.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteModel(target.id)
                        modelToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete Model", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { modelToDelete = null }
                ) {
                    Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                }
            },
            modifier = Modifier
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp)),
            containerColor = if (colors.isDark) Color(0xFF131A29) else Color.White,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // =========================================================================
    // DIALOG 2: EMERGENCY WIPE CONFIRMATION
    // =========================================================================
    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Tactical Wipe?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This will immediately clear all local message history, cached map tiles, and restore default tactical identity parameters.",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLogs()
                        viewModel.clearMapCache()
                        showWipeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Wipe Local Data", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWipeConfirmDialog = false }
                ) {
                    Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                }
            },
            modifier = Modifier
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp)),
            containerColor = if (colors.isDark) Color(0xFF131A29) else Color.White,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * 3-Way Theme Switcher Option Pill
 */
@Composable
private fun ThemeOptionButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: com.itantra.app.ui.theme.MinimalColors
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.badgeBlueContainer else colors.cardSecondaryBg)
            .border(
                width = 1.2.dp,
                color = if (isSelected) colors.accent else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.accent else colors.textSecondary
            )
        }
    }
}

/**
 * Single Model Card in the Downloaded AI Models list
 */
@Composable
private fun ModelItemCard(
    model: DownloadedAiModel,
    colors: com.itantra.app.ui.theme.MinimalColors,
    onDeleteClick: () -> Unit
) {
    val categoryBadgeBg = when (model.category) {
        AiModelCategory.STT -> colors.badgeBlueContainer
        AiModelCategory.TTS -> colors.badgeMintContainer
        AiModelCategory.TRANSLATION -> colors.badgePurpleContainer
    }
    val categoryBadgeText = when (model.category) {
        AiModelCategory.STT -> colors.badgeBlueText
        AiModelCategory.TTS -> colors.badgeMintText
        AiModelCategory.TRANSLATION -> colors.badgePurpleText
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardSecondaryBg)
            .border(0.5.dp, colors.outline, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(categoryBadgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = model.category.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryBadgeText
                        )
                    }

                    // Language tag
                    Text(
                        text = model.language,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = model.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                Text(
                    text = "${model.description} • ${model.sizeMb} MB",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            // Trash action button
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete ${model.name}",
                    tint = colors.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Segmented Option Chip
 */
@Composable
private fun SegmentedOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: com.itantra.app.ui.theme.MinimalColors
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) colors.badgeBlueContainer else colors.cardSecondaryBg)
            .border(
                width = 1.dp,
                color = if (isSelected) colors.accent else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) colors.accent else colors.textSecondary,
            maxLines = 1
        )
    }
}

/**
 * Diagnostic Sensor Status Pill
 */
@Composable
private fun SensorStatusBadge(
    sensorName: String,
    status: String,
    bgColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "$sensorName: $status",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
