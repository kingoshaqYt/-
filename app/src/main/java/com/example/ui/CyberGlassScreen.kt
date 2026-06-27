package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CyberGlassScreen represents a beautiful, futuristic security audit & unban dashboard.
 * Designed purely with:
 * 1. Deep Midnight Slate background (#05070F)
 * 2. Vivid Cyan and Electric Purple back-glowing radial gradients
 * 3. Neon glow Typography with custom rendering shadows
 * 4. Genuine reflective Glassmorphism visual card container with 28.dp rounded corners
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyberGlassScreen(
    modifier: Modifier = Modifier,
    onBackTrigger: (() -> Unit)? = null
) {
    // Interactive states to show true craftsmanship
    var auditCount by remember { mutableStateOf(1) }
    var neuralSyncProgress by remember { mutableStateOf(0.78f) }
    var terminalStatus by remember { mutableStateOf("ENCRYPTION SECURE") }
    var isAuditing by remember { mutableStateOf(false) }
    
    // Pulse animation for simulated connection ping
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_signal")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Animated colors based on status
    val neonAccentColor by animateColorAsState(
        targetValue = if (isAuditing) Color(0xFF6B11FF) else Color(0xFF00F2FE),
        animationSpec = tween(500),
        label = "accent_color"
    )

    // Main background box featuring deep midnight slate and customized neon ambient glows
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // 1. Solid Primary Midnight Background Selection
                drawRect(color = Color(0xFF05070F))

                // 2. Neon Ambient Glowing Orbs behind the layouts (Top-Start Glow & Bottom-End Glow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00F2FE).copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(0f, 0f),
                        radius = 1200f
                    ),
                    center = Offset(0f, 0f),
                    radius = 1200f
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6B11FF).copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(size.width, size.height),
                        radius = 1300f
                    ),
                    center = Offset(size.width, size.height),
                    radius = 1300f
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CORE TELEMETRY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F2FE).copy(alpha = 0.7f),
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "NODE SECURE PROTOCOL v8.12",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                }

                // Close / Back Trigger handler safely wrapped
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .clickable { onBackTrigger?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Popping Title with deep outer Neon Glow Shadow rendering effect!
            Text(
                text = "CYBER CORE GLASS",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(
                        color = neonAccentColor,
                        offset = Offset(0f, 0f),
                        blurRadius = 25f
                    )
                )
            )

            Text(
                text = "Hyper-dimensional predictive hardware auditing suite bypass terminal.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            // True Representative Glassmorphism Card Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF0F1528).copy(alpha = 0.50f))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header inside Glassmorphic Screen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF34C759).copy(alpha = pulseAlpha))
                            )
                            Text(
                                text = "AUDIT PANEL #0${auditCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00F2FE).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = terminalStatus,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00F2FE)
                            )
                        }
                    }

                    // Simulated Radial Signal Indicator using custom Canvas draw in the central glass
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .drawBehind {
                                val radius = size.minDimension / 2
                                // Decorative ambient concentric circles indicating bypass signals
                                drawCircle(
                                    color = Color(0xFF00F2FE).copy(alpha = 0.04f),
                                    radius = radius,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF6B11FF).copy(alpha = 0.06f),
                                    radius = radius * 0.75f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF00F2FE).copy(alpha = 0.10f),
                                    radius = radius * 0.5f,
                                    center = center
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(neuralSyncProgress * 100).toInt()}%",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color(0xFF00F2FE).copy(alpha = 0.7f),
                                        blurRadius = 15f
                                    )
                                )
                            )
                            Text(
                                text = "NEURAL SYNC",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White.copy(alpha = 0.4f),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Key telemetry meters inside the card
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TelemetryGlassItem(
                            label = "VIRTUAL MACHINE ADAPTER",
                            value = "HYPER-V V82",
                            info = "ACTIVE",
                            color = Color(0xFF00F2FE)
                        )
                        TelemetryGlassItem(
                            label = "RESTRICTION PARALLEL BYPASS",
                            value = "INJECTED SECURE",
                            info = "OPTIMAL",
                            color = Color(0xFF34C759)
                        )
                        TelemetryGlassItem(
                            label = "DEVICE STATE ATTESTATION",
                            value = "PASSED",
                            info = "VERIFIED",
                            color = Color(0xFFFF9500)
                        )
                    }

                    // Dynamic informative prompt
                    Text(
                        text = "Audit safeguards block potential system limits dynamically on Day One to ensure 100% security attestation status.",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Glass-reflective Action trigger Button
                    Button(
                        onClick = {
                            isAuditing = true
                            terminalStatus = "CALIBRATING ATTACK PATHS..."
                            // Simple interactive updates
                            neuralSyncProgress = 0.99f
                            auditCount++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = neonAccentColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 12.dp
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Trigger neural bypass sync reset",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isAuditing) "AUDITING PROTOCOLS..." else "RUN REAL-TIME ATTESTATION CYCLES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Secondary informational card detailing status parameters securely
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1528).copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Security alert icon",
                        tint = Color(0xFF00F2FE),
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ANTI-DETECTION SIGNATURE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "This environment passes state attestation checks automatically, maintaining fully untampered device indices.",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryGlassItem(
    label: String,
    value: String,
    info: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = info,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CyberGlassScreenPreview() {
    CyberGlassScreen()
}
