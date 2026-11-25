package com.aura.agent.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.agent.ui.theme.GeminiGradientColors

@Composable
fun SparkleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = false
) {
    val rotation = if (animate) {
        val transition = rememberInfiniteTransition(label = "sparkleSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sparkleAngle"
        )
        angle
    } else 0f

    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        val path = Path().apply {
            moveTo(cx, 0f)
            cubicTo(cx, cy * 0.72f, cx * 1.28f, cy, w, cy)
            cubicTo(cx * 1.28f, cy, cx, cy * 1.28f, cx, h)
            cubicTo(cx, cy * 1.28f, cx * 0.72f, cy, 0f, cy)
            cubicTo(cx * 0.72f, cy, cx, cy * 0.72f, cx, 0f)
            close()
        }

        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = GeminiGradientColors,
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
    }
}
