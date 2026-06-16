package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

fun Modifier.paperTexture(alpha: Float = 0.08f): Modifier = this.drawWithContent {
    drawContent()
    val sizeWidth = size.width
    val sizeHeight = size.height
    if (sizeWidth > 0f && sizeHeight > 0f) {
        val random = java.util.Random(42) // Consistent seed
        // Draw specks
        val numSpecks = (sizeWidth * sizeHeight / 120f).toInt().coerceIn(30, 2000)
        for (i in 0 until numSpecks) {
            val x = random.nextFloat() * sizeWidth
            val y = random.nextFloat() * sizeHeight
            val sizeFactor = random.nextFloat()
            val radius = 0.4f + sizeFactor * 0.8f
            val isDark = random.nextFloat() > 0.65f
            val speckAlpha = (0.3f + random.nextFloat() * 0.7f) * alpha
            if (isDark) {
                drawCircle(
                    color = Color.Black.copy(alpha = speckAlpha * 0.35f),
                    radius = radius,
                    center = Offset(x, y)
                )
            } else {
                drawCircle(
                    color = Color.White.copy(alpha = speckAlpha * 0.55f),
                    radius = radius * 1.2f,
                    center = Offset(x, y)
                )
            }
        }
        // Draw physical fibrous lines for paper touch
        val numFibers = (sizeWidth * sizeHeight / 600f).toInt().coerceIn(10, 500)
        for (i in 0 until numFibers) {
            val x1 = random.nextFloat() * sizeWidth
            val y1 = random.nextFloat() * sizeHeight
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val length = 3f + random.nextFloat() * 8f
            val x2 = x1 + length * Math.cos(angle.toDouble()).toFloat()
            val y2 = y1 + length * Math.sin(angle.toDouble()).toFloat()
            val fiberAlpha = (0.1f + random.nextFloat() * 0.4f) * alpha
            val isDark = random.nextFloat() > 0.5f
            drawLine(
                color = if (isDark) Color.Black.copy(alpha = fiberAlpha * 0.22f) else Color.White.copy(alpha = fiberAlpha * 0.35f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 0.6f + random.nextFloat() * 0.6f
            )
        }
    }
}

fun Modifier.depth3D(cornerRadius: Dp = 12.dp, isDark: Boolean = true): Modifier = this.drawWithContent {
    drawContent()
    val strokeWidth = 1.25f.dp.toPx()
    val rPx = cornerRadius.toPx()
    // Top highlight bevel (light source from top-left)
    drawLine(
        color = if (isDark) Color.White.copy(0.22f) else Color.White.copy(0.60f),
        start = Offset(rPx, strokeWidth / 2),
        end = Offset(size.width - rPx, strokeWidth / 2),
        strokeWidth = strokeWidth
    )
    // Left edge highlight
    drawLine(
        color = if (isDark) Color.White.copy(0.14f) else Color.White.copy(0.50f),
        start = Offset(strokeWidth / 2, rPx),
        end = Offset(strokeWidth / 2, size.height - rPx),
        strokeWidth = strokeWidth
    )
    // Bottom shadow bevel
    drawLine(
        color = Color.Black.copy(0.46f),
        start = Offset(rPx, size.height - strokeWidth / 2),
        end = Offset(size.width - rPx, size.height - strokeWidth / 2),
        strokeWidth = strokeWidth
    )
    // Right edge shadow bevel
    drawLine(
        color = Color.Black.copy(0.36f),
        start = Offset(size.width - strokeWidth / 2, rPx),
        end = Offset(size.width - strokeWidth / 2, size.height - rPx),
        strokeWidth = strokeWidth
    )
}

enum class GlassIntensity {
    LOW, MEDIUM, ULTRA
}

fun Modifier.liquidGlass(
    intensity: GlassIntensity = GlassIntensity.MEDIUM,
    cornerRadius: Dp = 16.dp,
    borderGlow: Color = Color(0x6600E5FF),
    isDarkTheme: Boolean? = null
): Modifier = composed {
    val dark = isDarkTheme ?: (androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f)
    
    val alphaBackground = when (intensity) {
        GlassIntensity.LOW -> 0.08f
        GlassIntensity.MEDIUM -> 0.04f
        GlassIntensity.ULTRA -> 0.02f
    }
    
    val strokeWidth = when (intensity) {
        GlassIntensity.LOW -> 0.8.dp
        GlassIntensity.MEDIUM -> 1.0.dp
        GlassIntensity.ULTRA -> 1.5.dp
    }

    val shadowElevation = when (intensity) {
        GlassIntensity.LOW -> 2.dp
        GlassIntensity.MEDIUM -> 4.dp
        GlassIntensity.ULTRA -> 8.dp
    }

    val bgBrush = if (dark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alphaBackground),
                Color.White.copy(alpha = alphaBackground * 0.5f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alphaBackground + 0.65f),
                Color.White.copy(alpha = alphaBackground + 0.45f)
            )
        )
    }

    val borderBrush = if (dark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = 0.12f),
                borderGlow.copy(alpha = 0.08f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F172A).copy(alpha = 0.10f),
                Color(0xFF0F172A).copy(alpha = 0.04f),
                Color(0xFF0F172A).copy(alpha = 0.10f),
                borderGlow.copy(alpha = 0.18f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

    val shadowColor = if (dark) Color(0x1F000000) else Color(0x0F000000)
    val shadowSpotColor = if (dark) Color(0x0D00E5FF) else Color(0x080582FF)

    this
        .shadow(
            elevation = shadowElevation,
            shape = RoundedCornerShape(cornerRadius),
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowSpotColor
        )
        .background(
            brush = bgBrush,
            shape = RoundedCornerShape(cornerRadius)
        )
        .border(
            width = strokeWidth,
            brush = borderBrush,
            shape = RoundedCornerShape(cornerRadius)
        )
        .clip(RoundedCornerShape(cornerRadius))
        .paperTexture(alpha = if (intensity == GlassIntensity.ULTRA) 0.03f else 0.05f)
        .depth3D(cornerRadius = cornerRadius, isDark = dark)
}

fun Modifier.glowingAmbientOrbs(): Modifier {
    return this.drawBehind {
        // Draw top center cyan-gradient: radial-gradient at (50%, -20%) with cyan glow Color(0x2606B6D4)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x2606B6D4), Color.Transparent),
                center = Offset(size.width * 0.5f, -size.height * 0.1f),
                radius = size.height * 0.6f
            ),
            center = Offset(size.width * 0.5f, -size.height * 0.1f),
            radius = size.height * 0.6f
        )
        
        // Draw bottom left cyan-gradient: Center bottom-left with cyan-glow Color(0x1A0891B2)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1A0891B2), Color.Transparent),
                center = Offset(-size.width * 0.1f, size.height * 1.1f),
                radius = size.width * 0.8f
            ),
            center = Offset(-size.width * 0.1f, size.height * 1.1f),
            radius = size.width * 0.8f
        )
    }
}

/**
 * Modern Touch Feedback Modifier (Scale + Ripple)
 */
fun Modifier.bounceClick(
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce"
    )
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> isPressed = true
                is androidx.compose.foundation.interaction.PressInteraction.Release -> isPressed = false
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> isPressed = false
            }
        }
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick
        )
}
