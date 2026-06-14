package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.paperTexture(alpha: Float = 0.05f): Modifier = this.drawBehind {
    val sizeWidth = size.width
    val sizeHeight = size.height
    if (sizeWidth > 0f && sizeHeight > 0f) {
        val random = java.util.Random(42) // Consistent seed
        val numDots = (sizeWidth * sizeHeight / 300f).toInt().coerceIn(10, 500)
        for (i in 0 until numDots) {
            val x = random.nextFloat() * sizeWidth
            val y = random.nextFloat() * sizeHeight
            val radius = 0.5f + random.nextFloat() * 1.0f
            val isDark = random.nextFloat() > 0.72f
            val dotAlpha = (0.2f + random.nextFloat() * 0.8f) * alpha
            if (isDark) {
                drawCircle(
                    color = Color.Black.copy(alpha = dotAlpha * 0.45f),
                    radius = radius * 0.9f,
                    center = Offset(x, y)
                )
            } else {
                drawCircle(
                    color = Color.White.copy(alpha = dotAlpha * 0.85f),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }
    }
}

fun Modifier.depth3D(cornerRadius: Dp = 12.dp, isDark: Boolean = true): Modifier = this.drawBehind {
    val strokeWidth = 1.dp.toPx()
    // 3D Bevel highlight at the top edge
    drawLine(
        color = if (isDark) Color.White.copy(0.12f) else Color.White.copy(0.4f),
        start = Offset(cornerRadius.toPx(), strokeWidth / 2),
        end = Offset(size.width - cornerRadius.toPx(), strokeWidth / 2),
        strokeWidth = strokeWidth
    )
    // 3D Bevel shadow at the bottom edge
    drawLine(
        color = Color.Black.copy(0.25f),
        start = Offset(cornerRadius.toPx(), size.height - strokeWidth / 2),
        end = Offset(size.width - cornerRadius.toPx(), size.height - strokeWidth / 2),
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
