package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Size

// PAPER TEXTURE — reduced from 9000 to max 800 specks, removed fibrous lines
fun Modifier.paperTexture(alpha: Float = 0.06f): Modifier = this.drawWithContent {
    drawContent()
    val w = size.width
    val h = size.height
    if (w > 0f && h > 0f) {
        val random = java.util.Random(42)
        val numSpecks = (w * h / 200f).toInt().coerceIn(20, 800)
        for (i in 0 until numSpecks) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            val r = 0.3f + random.nextFloat() * 0.7f
            val a = (0.04f + random.nextFloat() * 0.06f) * alpha
            drawCircle(color = Color.White.copy(alpha = a), radius = r, center = Offset(x, y))
        }
    }
}

// GLASS BLUR — FIX: CLAMP instead of DECAL, API guard, no-op fallback below API 31
fun Modifier.glassBlur(radius: Float = 20f): Modifier = this.then(
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                radius, radius, android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        Modifier // API < 31 fallback: liquidGlass handles visual frosting instead
    }
)

// DEPTH 3D BEVEL
fun Modifier.depth3D(cornerRadius: Dp = 16.dp, isDark: Boolean = true): Modifier = this.drawWithContent {
    drawContent()
    val sw = 1.2f.dp.toPx()
    val r = cornerRadius.toPx()
    drawLine(Color.White.copy(0.28f), Offset(r, sw / 2), Offset(size.width - r, sw / 2), sw)
    drawLine(Color.White.copy(0.14f), Offset(sw / 2, r), Offset(sw / 2, size.height - r), sw)
    drawLine(Color.Black.copy(0.55f), Offset(r, size.height - sw / 2), Offset(size.width - r, size.height - sw / 2), sw)
    drawLine(Color.Black.copy(0.40f), Offset(size.width - sw / 2, r), Offset(size.width - sw / 2, size.height - r), sw)
}

enum class GlassIntensity { LOW, MEDIUM, ULTRA }

// LIQUID GLASS — ALL BUGS FIXED:
// 1. bgColor alpha reduced so frosted effect reads as translucent not opaque
// 2. Grain count 45 → 200 for visible frosted texture
// 3. Border now includes purple mid-stop (borderGlow parameter was previously ignored)
// 4. Shadow colors switched to purple-tinted — black shadows invisible on dark bg
// 5. Elevation doubled: LOW 4→8, MEDIUM 8→16, ULTRA 16→28
fun Modifier.liquidGlass(
    intensity: GlassIntensity = GlassIntensity.MEDIUM,
    cornerRadius: Dp = 20.dp,
    borderGlow: Color = Color(0xFF8B5CF6),
    isDarkTheme: Boolean? = null
): Modifier = composed {

    val bgColor = when (intensity) {
        GlassIntensity.LOW    -> Color(0xFF0D1020).copy(alpha = 0.45f)
        GlassIntensity.MEDIUM -> Color(0xFF0A0D1A).copy(alpha = 0.70f)
        GlassIntensity.ULTRA  -> Color(0xFF06080F).copy(alpha = 0.88f)
    }

    val shadowElevation = when (intensity) {
        GlassIntensity.LOW    -> 8.dp
        GlassIntensity.MEDIUM -> 16.dp
        GlassIntensity.ULTRA  -> 28.dp
    }

    // FIX: Purple-tinted shadows — black shadows were invisible on dark background
    val shadowAmbient = Color(0x1A8B5CF6)
    val shadowSpot    = Color(0x258B5CF6)

    val infiniteTransition = rememberInfiniteTransition(label = "glass_sheen")
    val shineProgress by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_progress"
    )

    // FIX: Border now uses borderGlow color — was previously ignored, always white
    val bevelBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.35f),
            borderGlow.copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.08f),
            Color.Black.copy(alpha = 0.40f)
        )
    )

    this
        .shadow(
            elevation = shadowElevation,
            shape = RoundedCornerShape(cornerRadius),
            clip = false,
            ambientColor = shadowAmbient,
            spotColor = shadowSpot
        )
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind {
            // 1. Dark glass base
            drawRect(color = bgColor)

            // 2. Subtle purple radial inner tint for depth
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.2f),
                    radius = size.maxDimension * 0.7f
                )
            )

            // 3. Diagonal specular shine sweep
            val startX = size.width * shineProgress
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.04f),
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    start = Offset(startX, -50f),
                    end   = Offset(startX + size.height, size.height + 50f)
                )
            )

            // 4. FIX: Frosted micro-grain — increased from 45 to 200, now visible
            val random = java.util.Random(77)
            for (i in 0 until 200) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val r = 0.5f + random.nextFloat() * 0.9f
                val a = 0.04f + random.nextFloat() * 0.06f
                drawCircle(
                    color = if (random.nextFloat() > 0.4f)
                        Color.White.copy(alpha = a)
                    else
                        Color(0xFF8B5CF6).copy(alpha = a * 0.5f),
                    radius = r,
                    center = Offset(x, y)
                )
            }
        }
        .border(
            width = 1.dp,
            brush = bevelBorderBrush,
            shape = RoundedCornerShape(cornerRadius)
        )
}

// GLOWING AMBIENT ORBS — purple theme, removed rainbow cyan/green
fun Modifier.glowingAmbientOrbs(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "ambient_orbs")
    val orb1Offset by transition.animateFloat(
        initialValue = -100f, targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb1"
    )
    val orb2Offset by transition.animateFloat(
        initialValue = 100f, targetValue = -100f,
        animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb2"
    )

    this.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x3D8B5CF6), Color(0x0D8B5CF6), Color.Transparent),
                center = Offset(size.width * 0.15f + orb1Offset, size.height * 0.25f),
                radius = size.width * 0.90f
            ),
            center = Offset(size.width * 0.15f + orb1Offset, size.height * 0.25f),
            radius = size.width * 0.90f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x2D6366F1), Color(0x086366F1), Color.Transparent),
                center = Offset(size.width * 0.85f + orb2Offset, size.height * 0.80f),
                radius = size.width * 1.0f
            ),
            center = Offset(size.width * 0.85f + orb2Offset, size.height * 0.80f),
            radius = size.width * 1.0f
        )
    }
}

// BOUNCE CLICK
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press   -> isPressed = true
                is androidx.compose.foundation.interaction.PressInteraction.Release -> isPressed = false
                is androidx.compose.foundation.interaction.PressInteraction.Cancel  -> isPressed = false
            }
        }
    }
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
}

// PREMIUM SHINE
fun Modifier.premiumShineEffect(showShine: Boolean = true, durationMillis: Int = 4500): Modifier = composed {
    if (!showShine) return@composed this
    val transition = rememberInfiniteTransition(label = "shine")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing, delayMillis = 2000),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_translate"
    )
    this.drawWithContent {
        drawContent()
        val diagonal = kotlin.math.sqrt(size.width * size.width + size.height * size.height)
        val t = (translateAnim / 1000f) * (diagonal * 2f) - diagonal
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                start = Offset(t - 40f, t - 40f),
                end   = Offset(t + 40f, t + 40f)
            )
        )
    }
}

// BEVEL GLOW — purple
fun Modifier.bevelGlow(glowColor: Color = Color(0xFF8B5CF6), cornerRadius: Dp = 24.dp): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_scale"
    )
    this
        .drawBehind {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.18f * glowScale), Color.Transparent),
                    center = center,
                    radius = size.maxDimension * 0.65f
                )
            )
        }
        .border(
            width = 1.5.dp,
            brush = Brush.linearGradient(listOf(glowColor.copy(0.80f), glowColor.copy(0.20f), glowColor.copy(0.80f))),
            shape = RoundedCornerShape(cornerRadius)
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(0.38f), Color.White.copy(0.04f), Color.Black.copy(0.38f))),
            shape = RoundedCornerShape(cornerRadius)
        )
}
