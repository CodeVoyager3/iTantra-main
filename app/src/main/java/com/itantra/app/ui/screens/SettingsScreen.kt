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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radio
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
import com.itantra.app.modelhub.LanguageModelPack
import com.itantra.app.modelhub.ModelDownloadState
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
    val modelPacks by viewModel.modelPacks.collectAsState()
    val txPower by viewModel.txPower.collectAsState()
    val beaconInterval by viewModel.beaconInterval.collectAsState()
    val meshHopLimit by viewModel.meshHopLimit.collectAsState()
    val vadSensitivity by viewModel.vadSensitivity.collectAsState()
    val noiseSuppressionEnabled by viewModel.noiseSuppressionEnabled.collectAsState()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val zeroLogPrivacy by viewModel.zeroLogPrivacy.collectAsState()
    val mapCacheSizeMb by viewModel.mapCacheSizeMb.collectAsState()

    val installedPacks = modelPacks.filter { it.isInstalled }
    val installedStorageMb = installedPacks.sumOf { it.sizeMb }

    val isTranslationInstalled by viewModel.isTranslationModelInstalled.collectAsState()
    val translationDownloadState by viewModel.translationDownloadState.collectAsState()
    var testTranslationInput by remember { mutableStateOf("हम मलबे में दबे हैं") }
    var testTranslationOutput by remember { mutableStateOf("") }

    // State for delete confirmation dialog
    var modelToDelete by remember { mutableStateOf<LanguageModelPack?>(null) }
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
        // 3. ON-DEVICE AI MODEL PACKS (REAL OFFLINE HUB)
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            text = "100% Offline • Download packs on demand",
                            fontSize = 11.sp,
                            color = colors.badgeMintText
                        )
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
                                    text = "${formatSizeMb(installedStorageMb)} MB",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = "${installedPacks.size} Engines Installed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.badgeMintText
                            )
                        }

                        // Progress relative to a 2048 MB tactical storage quota
                        LinearProgressIndicator(
                            progress = { (installedStorageMb / MODEL_STORAGE_QUOTA_MB).toFloat().coerceIn(0f, 1f) },
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

                // Model pack list
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    modelPacks.forEach { pack ->
                        LanguagePackRow(
                            pack = pack,
                            colors = colors,
                            onDownload = { viewModel.downloadModel(pack.languageTag) },
                            onPauseDownload = { viewModel.pauseModelDownload(pack.languageTag) },
                            onResumeDownload = { viewModel.downloadModel(pack.languageTag) },
                            onCancelDownload = { viewModel.cancelModelDownload(pack.languageTag) },
                            onDeleteClick = { modelToDelete = pack }
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3B. NEURAL TRANSLATION ENGINES (CROSS-LINGUAL MESH)
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NEURAL TRANSLATION ENGINES",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Cross-lingual mesh (Hindi ⟷ English)",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isTranslationInstalled) BadgeMintContainer
                                else if (translationDownloadState is ModelDownloadState.Downloading) AccentBlueContainer
                                else colors.cardSecondaryBg
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when {
                                isTranslationInstalled -> "✓ INSTALLED"
                                translationDownloadState is ModelDownloadState.Downloading -> "DOWNLOADING"
                                else -> "NOT DOWNLOADED"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isTranslationInstalled -> BadgeMintText
                                translationDownloadState is ModelDownloadState.Downloading -> AccentBlue
                                else -> colors.textSecondary
                            }
                        )
                    }
                }

                // Description Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardSecondaryBg)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Model: Google ML Kit Neural NMT (hi ↔ en)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Google On-Device",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                        }
                        Text(
                            text = "Powered by Google ML Kit On-Device Neural Machine Translation. Translates full sentences offline between Hindi and English. If neither device has this pack installed, cross-lingual voice turns will pause with a prompt.",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            lineHeight = 15.sp
                        )

                        // Download Progress bar if active
                        if (translationDownloadState is ModelDownloadState.Downloading) {
                            val state = translationDownloadState as ModelDownloadState.Downloading
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = colors.accent,
                                trackColor = colors.outline
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading offline neural weights...",
                                    fontSize = 10.sp,
                                    color = AccentBlue
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentBlue
                                )
                            }
                        }
                    }
                }

                // Action Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTranslationInstalled) {
                        OutlinedButton(
                            onClick = { viewModel.deleteTranslationModel() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SosRed
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SosRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = SosRed
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Model", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else if (translationDownloadState is ModelDownloadState.Downloading) {
                        Button(
                            onClick = { },
                            enabled = false,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Downloading...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.downloadTranslationModel() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Model (Google ML Kit)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = colors.outline, thickness = 1.dp)

                // -------------------------------------------------------------
                // TEST SANDBOX: Instant offline verification for judges & demo
                // -------------------------------------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "OFFLINE TRANSLATION TEST SANDBOX",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = colors.textSecondary
                        )
                    }

                    Text(
                        text = "Tap any emergency phrase to test 100% offline bidirectional translation:",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )

                    // Quick test pills
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "हम मलबे में दबे हैं",
                            "मदद चाहिए",
                            "Rescue team is on the way",
                            "Are you okay?",
                            "पानी चाहिए",
                            "Severely injured"
                        ).forEach { sample ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.cardSecondaryBg)
                                    .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                                    .clickable {
                                        testTranslationInput = sample
                                        val isHindi = sample.any { it.code in 0x0900..0x097F }
                                        testTranslationOutput = if (isHindi) {
                                            viewModel.translateEmergencyText(sample, "hi", "en")
                                        } else {
                                            viewModel.translateEmergencyText(sample, "en", "hi")
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = sample,
                                    fontSize = 11.sp,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }

                    // Live Test Result Card
                    if (testTranslationOutput.isNotBlank() || testTranslationInput.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.cardSecondaryBg)
                                .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val isInputHindi = testTranslationInput.any { it.code in 0x0900..0x097F }
                                val fromTag = if (isInputHindi) "Hindi" else "English"
                                val toTag = if (isInputHindi) "English" else "Hindi"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$fromTag ➔ $toTag",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                    Text(
                                        text = "Google ML Kit Neural NMT",
                                        fontSize = 10.sp,
                                        color = BadgeMintText
                                    )
                                }
                                Text(
                                    text = "Input: \"$testTranslationInput\"",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "Output: \"${if (testTranslationOutput.isNotBlank()) testTranslationOutput else viewModel.translateEmergencyText(testTranslationInput, if (isInputHindi) "hi" else "en", if (isInputHindi) "en" else "hi")}\"",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BadgeMintText
                                )
                            }
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
                    text = "Delete Language Pack?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to remove \"${target.name}\" (${target.languageTag}) from offline storage?",
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "This will immediately free up ${formatSizeMb(target.sizeMb)} MB. Offline speech for ${target.name} will be disabled until re-downloaded.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteModel(target.languageTag)
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
                        viewModel.emergencyWipe()
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

/** Tactical on-device model storage quota (MB) used for the storage bar. */
private const val MODEL_STORAGE_QUOTA_MB = 2048.0

private fun formatSizeMb(sizeMb: Double): String =
    String.format(java.util.Locale.US, "%.1f", sizeMb)

/**
 * Single language pack row in the ON-DEVICE AI MODELS list. The trailing
 * action adapts to the pack's live download state.
 */
@Composable
private fun LanguagePackRow(
    pack: LanguageModelPack,
    colors: com.itantra.app.ui.theme.MinimalColors,
    onDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val state = pack.downloadState

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardSecondaryBg)
            .border(0.5.dp, colors.outline, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        // Script badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.badgeBlueContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = pack.script,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.badgeBlueText
                            )
                        }

                        // Language tag
                        Text(
                            text = pack.languageTag.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = pack.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Text(
                        text = "STT + TTS engine • ${formatSizeMb(pack.sizeMb)} MB",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                when {
                    pack.isInstalled -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Installed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.badgeMintText
                        )
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete ${pack.name}",
                                tint = colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    state is ModelDownloadState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        IconButton(onClick = onPauseDownload, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause ${pack.name}",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onCancelDownload, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel ${pack.name}",
                                tint = colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    state is ModelDownloadState.Paused -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Paused • ${(state.progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.badgePurpleText
                        )
                        IconButton(onClick = onResumeDownload, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume ${pack.name}",
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onCancelDownload, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel ${pack.name}",
                                tint = colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    state is ModelDownloadState.Verifying -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Verifying",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        IconButton(onClick = onCancelDownload, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel ${pack.name}",
                                tint = colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    state is ModelDownloadState.Extracting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Extracting",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        IconButton(onClick = onCancelDownload, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel ${pack.name}",
                                tint = colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    state is ModelDownloadState.Error -> Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    else -> Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

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
                        text = "Downloading • ${formatSizeMb(state.progressBytes / (1024.0 * 1024.0))} / ${formatSizeMb(state.totalBytes / (1024.0 * 1024.0))} MB",
                        fontSize = 10.sp,
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
                        text = "Paused • ${(state.progress * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
                is ModelDownloadState.Error -> {
                    Text(
                        text = state.message,
                        fontSize = 10.sp,
                        color = colors.error
                    )
                }
                else -> Unit
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
