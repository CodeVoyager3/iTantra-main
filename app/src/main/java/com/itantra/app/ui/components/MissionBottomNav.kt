package com.itantra.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.ui.theme.AccentBlue
import com.itantra.app.ui.theme.AccentBlueContainer
import com.itantra.app.ui.theme.MinimalColorsInstance
import com.itantra.app.ui.theme.SosRed

enum class MissionDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SOS("sos", "SOS", Icons.Default.WarningAmber, Icons.Outlined.WarningAmber),
    WALKIE("walkie", "Walkie", Icons.Default.Mic, Icons.Outlined.Mic),
    RESCUE("rescue", "Rescue", Icons.Default.NotificationsActive, Icons.Outlined.CellTower),
    SETTINGS("settings", "Settings", Icons.Default.Settings, Icons.Outlined.Settings)
}

/**
 * 4-Tab Bottom Navigation Bar (SOS • Walkie • Rescue • Settings).
 * Clean bright surface with soft border and high-contrast active indicator pills.
 */
@Composable
fun MissionBottomNav(
    currentDestination: MissionDestination,
    onDestinationSelected: (MissionDestination) -> Unit,
    alertCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        HorizontalDivider(
            color = colors.outline,
            thickness = 1.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MissionDestination.entries.forEach { destination ->
                val isSelected = currentDestination == destination

                val iconColor by animateColorAsState(
                    targetValue = when {
                        isSelected && destination == MissionDestination.SOS -> colors.error
                        isSelected -> colors.accent
                        else -> colors.textSecondary
                    },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "navIconColor"
                )

                val labelColor by animateColorAsState(
                    targetValue = when {
                        isSelected && destination == MissionDestination.SOS -> colors.error
                        isSelected -> colors.accent
                        else -> colors.textSecondary
                    },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "navTextColor"
                )

                val pillColor by animateColorAsState(
                    targetValue = when {
                        isSelected && destination == MissionDestination.SOS -> colors.errorContainer
                        isSelected -> colors.accentContainer
                        else -> Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "pillColor"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDestinationSelected(destination) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(pillColor)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (destination == MissionDestination.RESCUE && alertCount > 0) {
                                        Badge(
                                            containerColor = SosRed,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (alertCount > 9) "9+" else "$alertCount",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = destination.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.2.sp,
                            color = labelColor
                        )
                    }
                }
            }
        }
    }
}
