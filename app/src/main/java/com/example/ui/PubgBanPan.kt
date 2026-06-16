package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun PubgBanPan(
    modifier: Modifier = Modifier,
    panColor: Color = Color(0xFF1E293B) // classic cast-iron/slate gray
) {
    Canvas(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val width = size.width
        val height = size.height
        
        // Circular body calculations
        val minDim = minOf(width, height)
        val radius = minDim * 0.28f
        val centerX = width / 2
        // Shift up slightly to fit the long bottom handle beautifully
        val centerY = height / 2 - (radius * 0.15f)
        
        // 1. Helper loop handle at the top (curved loop helper handle opposite the main handle)
        val helperHandlePath = Path().apply {
            val loopW = radius * 0.32f
            val loopH = radius * 0.22f
            val topCircleY = centerY - radius
            moveTo(centerX - loopW / 2, topCircleY)
            cubicTo(
                centerX - loopW, topCircleY - loopH,
                centerX + loopW, topCircleY - loopH,
                centerX + loopW / 2, topCircleY
            )
        }
        
        // Draw the top loop helper handle with a stroke
        drawPath(
            path = helperHandlePath,
            color = panColor,
            style = Stroke(width = radius * 0.10f, cap = StrokeCap.Round)
        )
        
        // 2. Main handle at the bottom (vertical handle tapering to a rounded point)
        val mainHandlePath = Path().apply {
            val baseW = radius * 0.18f
            val tipW = radius * 0.11f
            val handleLen = radius * 1.15f
            val baseY = centerY + radius - 4f // slightly overlap with body
            val tipY = baseY + handleLen
            
            moveTo(centerX - baseW / 2, baseY)
            lineTo(centerX - tipW / 2, tipY - tipW)
            // Rounded point tip
            quadraticTo(
                centerX, tipY,
                centerX + tipW / 2, tipY - tipW
            )
            lineTo(centerX + baseW / 2, baseY)
            close()
        }
        
        drawPath(
            path = mainHandlePath,
            color = panColor
        )
        
        // 3. Symmetrical triangular pour spouts protruding from left/right sides
        val bodyPath = Path().apply {
            val spoutHalfW = radius * 0.20f
            val spoutLen = radius * 0.14f
            
            // Left spout
            moveTo(centerX - radius + 2f, centerY - spoutHalfW)
            lineTo(centerX - radius - spoutLen, centerY)
            lineTo(centerX - radius + 2f, centerY + spoutHalfW)
            
            // Circular body outline
            addOval(androidx.compose.ui.geometry.Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius))
            
            // Right spout
            moveTo(centerX + radius - 2f, centerY - spoutHalfW)
            lineTo(centerX + radius + spoutLen, centerY)
            lineTo(centerX + radius - 2f, centerY + spoutHalfW)
        }
        
        drawPath(
            path = bodyPath,
            color = panColor
        )
        
        // 4. Double rim/shading highlights inside the classic frying pan design
        drawCircle(
            color = Color.White.copy(alpha = 0.09f),
            radius = radius * 0.94f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = radius * 0.04f)
        )
        
        drawCircle(
            color = Color.Black.copy(alpha = 0.16f),
            radius = radius * 0.90f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )
        
        // 5. Masked text cut-out: "RECLAIM" exactly across the center
        val paint = Paint().apply {
            color = android.graphics.Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // Eats through drawing for negative-space
            typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.BOLD), Typeface.BOLD)
            textSize = radius * 0.44f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        
        val textY = centerY - ((paint.descent() + paint.ascent()) / 2)
        drawContext.canvas.nativeCanvas.drawText("RECLAIM", centerX, textY, paint)
    }
}
