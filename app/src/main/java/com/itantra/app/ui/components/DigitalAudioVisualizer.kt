package com.itantra.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.ui.theme.minimalColors
import kotlin.math.sin

/**
 * Tactical Digital Audio Visualizer (clean gray digital bars).
 *
 * Displays a sleek row of digital equalizer bars that dynamically dance
 * in response to live microphone or incoming playback audio levels.
 */
@Composable
fun DigitalAudioVisualizer(
    audioLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    label: String = if (isActive) "DIGITAL AUDIO LINK" else "STANDBY",
    barCount: Int = 22,
    baseColor: Color = Color(0xFF64748B), // Slate gray
    activeColor: Color = Color(0xFF94A3B8)
) {
    val colors = MaterialTheme.minimalColors
    val infiniteTransition = rememberInfiniteTransition(label = "digitalVisualizer")

    // Phase animation for rhythmic wave motion
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val smoothLevel by animateFloatAsState(
        targetValue = if (isActive) audioLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "smoothLevel"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (colors.isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5F9))
            .border(1.dp, if (colors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label & Live indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isActive && smoothLevel > 0.05f) Color(0xFF10B981) else Color(0xFF94A3B8))
                )
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = if (colors.isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }

            // Digital Gray Equalizer Bars
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                modifier = Modifier.height(26.dp)
            ) {
                for (i in 0 until barCount) {
                    val waveFactor = sin(phase + (i.toFloat() * 0.45f)).coerceAtLeast(0.1f)
                    val baseHeight = 4f
                    val maxHeight = 24f
                    val dynamicHeight = if (isActive) {
                        baseHeight + (smoothLevel * (maxHeight - baseHeight) * (0.4f + 0.6f * waveFactor))
                    } else {
                        baseHeight
                    }

                    val isHigh = smoothLevel > 0.4f && waveFactor > 0.7f
                    val barTint = when {
                        !isActive -> Color(0xFF94A3B8).copy(alpha = 0.4f)
                        isHigh -> activeColor
                        else -> baseColor
                    }

                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(dynamicHeight.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(barTint)
                    )
                }
            }
        }
    }
}
