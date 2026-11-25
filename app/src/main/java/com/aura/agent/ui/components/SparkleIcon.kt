package com.aura.agent.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SparkleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = false
) {
    val tint = MaterialTheme.colorScheme.primary
    val scale = if (animate) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val s by transition.animateFloat(
            initialValue = 0.86f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        s
    } else 1f

    Canvas(
        modifier = modifier
            .size(size)
            .scale(scale)
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val path = Path().apply {
            moveTo(cx, 0f)
            cubicTo(cx, cy * 0.74f, cx * 1.26f, cy, w, cy)
            cubicTo(cx * 1.26f, cy, cx, cy * 1.26f, cx, h)
            cubicTo(cx, cy * 1.26f, cx * 0.74f, cy, 0f, cy)
            cubicTo(cx * 0.74f, cy, cx, cy * 0.74f, cx, 0f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}
