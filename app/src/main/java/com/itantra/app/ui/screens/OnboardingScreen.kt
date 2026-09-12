package com.itantra.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.model.SupportedLanguage
import com.itantra.app.ui.theme.AccentBlue
import com.itantra.app.ui.theme.AccentBlueContainer
import com.itantra.app.ui.theme.BadgeIndigoContainer
import com.itantra.app.ui.theme.BadgeIndigoText
import com.itantra.app.ui.theme.BadgeMintContainer
import com.itantra.app.ui.theme.BadgeMintText
import com.itantra.app.ui.theme.minimalColors
import com.itantra.app.viewmodel.MissionControlViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: MissionControlViewModel,
    onContinue: () -> Unit
) {
    val colors = MaterialTheme.minimalColors
    val scrollState = rememberScrollState()

    // Form States
    var name by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Male") }
    var selectedLanguageCodes by remember { mutableStateOf(setOf("hi", "en")) }
    var selectedRelation by remember { mutableStateOf("Parent") }
    var relativePhone by remember { mutableStateOf("") }

    // UI state dropdown toggles
    var isGenderDropdownOpen by remember { mutableStateOf(false) }
    var isRelationDropdownOpen by remember { mutableStateOf(false) }
    var isLanguageSheetOpen by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Validation
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val genderOptions = listOf("Male", "Female", "Other", "Prefer not to say")
    val relationOptions = listOf("Parent", "Father", "Mother", "Spouse", "Sibling", "Child", "Guardian", "Friend", "Other")

    val buttonInteraction = remember { MutableInteractionSource() }
    val isPressed by buttonInteraction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label = "buttonScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // =======================================================
            // 1. HERO TACTICAL BRANDING & BADGE
            // =======================================================
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentBlue.copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
                    .border(1.5.dp, AccentBlue.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "iTantra Shield",
                    tint = AccentBlue,
                    modifier = Modifier.size(34.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BadgeIndigoContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SOVEREIGN DISASTER MESH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = BadgeIndigoText
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Disaster Identity Setup",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Configure your off-grid beacon profile so rescue squads and nearby teams can identify and assist you in an emergency.",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =======================================================
            // 2. REQUIRED SECTION
            // =======================================================
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFDCFCE7))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "REQUIRED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            color = Color(0xFF15803D)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.outline.copy(alpha = 0.5f))
                    )
                }

                // Languages & Gender/Age Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Column (55%): Language Multiple Dropdown
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x0A000000))
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
                            .clickable { isLanguageSheetOpen = true }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "LANGUAGES",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.6.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Open Language Dropdown",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Selected language chips
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                selectedLanguageCodes.take(3).forEach { code ->
                                    val lang = SupportedLanguage.fromCode(code)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(BadgeMintContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = lang.nativeName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BadgeMintText
                                        )
                                    }
                                }
                                if (selectedLanguageCodes.size > 3) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.cardSecondaryBg)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "+${selectedLanguageCodes.size - 3}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Tap to add / change dialects",
                                fontSize = 10.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Column (45%): Gender & Age
                    Box(
                        modifier = Modifier
                            .weight(0.95f)
                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x0A000000))
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Gender Selector
                            Column {
                                Text(
                                    text = "GENDER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.cardSecondaryBg)
                                            .clickable { isGenderDropdownOpen = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedGender,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = isGenderDropdownOpen,
                                        onDismissRequest = { isGenderDropdownOpen = false },
                                        modifier = Modifier.background(colors.surface)
                                    ) {
                                        genderOptions.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt, fontSize = 13.sp, color = colors.textPrimary) },
                                                onClick = {
                                                    selectedGender = opt
                                                    isGenderDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Age Field
                            Column {
                                Text(
                                    text = "AGE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = ageText,
                                    onValueChange = { input ->
                                        if (input.length <= 3 && input.all { it.isDigit() }) {
                                            ageText = input
                                        }
                                    },
                                    placeholder = { Text("24", fontSize = 13.sp, color = colors.textSecondary.copy(alpha = 0.5f)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentBlue,
                                        unfocusedBorderColor = colors.outline,
                                        focusedContainerColor = colors.cardSecondaryBg,
                                        unfocusedContainerColor = colors.cardSecondaryBg,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =======================================================
            // 3. OPTIONAL SECTION
            // =======================================================
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BadgeIndigoContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "OPTIONAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            color = BadgeIndigoText
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.outline.copy(alpha = 0.5f))
                    )
                }

                // Full Name / Callsign Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x0A000000))
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "FULL NAME / CALLSIGN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                color = colors.textSecondary
                            )
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                if (showError) showError = false
                            },
                            placeholder = { Text("e.g. Rahul Sharma / Alpha-01", color = colors.textSecondary.copy(alpha = 0.5f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = colors.outline,
                                focusedContainerColor = colors.cardSecondaryBg,
                                unfocusedContainerColor = colors.cardSecondaryBg,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Attached to emergency beacons so rescuers can call you by name over intercom.",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                // Priority Emergency Contact Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x0A000000))
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.ContactPhone,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "EMERGENCY CONTACT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BadgeIndigoContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SOS TELEMETRY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BadgeIndigoText
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Relation Dropdown
                            Box(modifier = Modifier.weight(0.42f)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.cardSecondaryBg)
                                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                                        .clickable { isRelationDropdownOpen = true }
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedRelation,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = isRelationDropdownOpen,
                                    onDismissRequest = { isRelationDropdownOpen = false },
                                    modifier = Modifier.background(colors.surface)
                                ) {
                                    relationOptions.forEach { rel ->
                                        DropdownMenuItem(
                                            text = { Text(rel, fontSize = 13.sp, color = colors.textPrimary) },
                                            onClick = {
                                                selectedRelation = rel
                                                isRelationDropdownOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Phone Number
                            OutlinedTextField(
                                value = relativePhone,
                                onValueChange = { input ->
                                    if (input.length <= 15 && input.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
                                        relativePhone = input
                                    }
                                },
                                placeholder = { Text("+91 98765 43210", fontSize = 13.sp, color = colors.textSecondary.copy(alpha = 0.5f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentBlue,
                                    unfocusedBorderColor = colors.outline,
                                    focusedContainerColor = colors.cardSecondaryBg,
                                    unfocusedContainerColor = colors.cardSecondaryBg,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.58f)
                                    .height(56.dp)
                            )
                        }

                        Text(
                            text = "When SOS is triggered, rescue teams will be given this relative number to inform your family immediately upon detection.",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Inline validation error if any
            AnimatedVisibility(visible = showError) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEE2E2))
                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB91C1C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =======================================================
            // 5. CONTINUE ACTION BUTTON
            // =======================================================
            Button(
                onClick = {
                    if (selectedLanguageCodes.isEmpty()) {
                        errorMessage = "Please select at least one language dialect."
                        showError = true
                        return@Button
                    }
                    if (ageText.isBlank()) {
                        errorMessage = "Please enter your age to continue."
                        showError = true
                        return@Button
                    }

                    val parsedAge = ageText.toIntOrNull()
                    viewModel.completeOnboarding(
                        name = name.trim(),
                        age = parsedAge,
                        gender = selectedGender,
                        languages = selectedLanguageCodes,
                        relation = selectedRelation,
                        phone = relativePhone.trim()
                    )
                    onContinue()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .scale(buttonScale),
                interactionSource = buttonInteraction,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONTINUE TO TACTICAL MESH",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "▶", fontSize = 12.sp)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Text(
                    text = "100% On-Device Sovereign • Zero Cloud Stored • Editable in Settings",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    // =======================================================
    // 6. MULTI-SELECT LANGUAGE MODAL BOTTOM SHEET
    // =======================================================
    if (isLanguageSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isLanguageSheetOpen = false },
            sheetState = sheetState,
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Spoken Languages",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Choose dialects you speak or understand",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    IconButton(onClick = { isLanguageSheetOpen = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = languageSearchQuery,
                    onValueChange = { languageSearchQuery = it },
                    placeholder = { Text("Search 10 regional dialects...", fontSize = 13.sp, color = colors.textSecondary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = colors.outline,
                        focusedContainerColor = colors.cardSecondaryBg,
                        unfocusedContainerColor = colors.cardSecondaryBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // List of Languages
                val filtered = SupportedLanguage.entries.filter {
                    it.englishName.contains(languageSearchQuery, ignoreCase = true) ||
                    it.nativeName.contains(languageSearchQuery, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { lang ->
                        val isSelected = selectedLanguageCodes.contains(lang.code)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AccentBlueContainer else colors.cardSecondaryBg)
                                .clickable {
                                    selectedLanguageCodes = if (isSelected) {
                                        if (selectedLanguageCodes.size > 1) {
                                            selectedLanguageCodes - lang.code
                                        } else {
                                            selectedLanguageCodes // Keep at least 1
                                        }
                                    } else {
                                        selectedLanguageCodes + lang.code
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) AccentBlue else colors.outline),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.nativeInitial,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else colors.textPrimary
                                    )
                                }

                                Column {
                                    Text(
                                        text = lang.nativeName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = lang.englishName,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedLanguageCodes = if (checked) {
                                        selectedLanguageCodes + lang.code
                                    } else {
                                        if (selectedLanguageCodes.size > 1) {
                                            selectedLanguageCodes - lang.code
                                        } else {
                                            selectedLanguageCodes
                                        }
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentBlue,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = { isLanguageSheetOpen = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text(
                        text = "Apply Selection (${selectedLanguageCodes.size} selected)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
