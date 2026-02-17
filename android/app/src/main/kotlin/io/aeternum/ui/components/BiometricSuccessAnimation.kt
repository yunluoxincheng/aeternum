package io.aeternum.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.navigation.Easing
import io.aeternum.ui.navigation.AnimationDuration
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 生物识别成功动画组件
 *
 * 用于显示生物识别认证成功后的视觉效果。
 *
 * ## 设计理念
 * - **安全感**: 通过绿色传达"安全"的感觉
 * - **流畅性**: 使用 EmphasizedDecelerate 缓动曲线
 * - **科技感**: 环形扩展效果 + 检查标记动画
 *
 * ## 动画规范
 * - 持续时间: 300ms
 * - 缓动曲线: EmphasizedDecelerate (0.05, 0.7, 0.1, 1.0)
 * - 参考: proposal.md §9.2 状态动画
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅提供视觉反馈，不执行任何安全操作
 * - 动画纯视觉效果，不涉及任何密钥材料
 *
 * @param modifier 修饰符
 * @param size 动画尺寸
 * @param onAnimationEnd 动画结束回调
 */
@Composable
fun BiometricSuccessAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    onAnimationEnd: (() -> Unit)? = null,
) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    // 动画序列
    LaunchedEffect(Unit) {
        // 阶段 1: 圆环扩展 (0-300ms)
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = AnimationDuration.NORMAL,
                    easing = Easing.EmphasizedDecelerate,
                ),
            )
        }

        // 阶段 2: 淡入 (0-200ms)
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 200,
                    easing = Easing.EmphasizedDecelerate,
                ),
            )
        }

        // 阶段 3: 检查标记旋转 (100-300ms)
        launch {
            rotation.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    this.durationMillis = AnimationDuration.NORMAL
                    0f at 100 with FastOutSlowInEasing
                    1f at AnimationDuration.NORMAL with FastOutSlowInEasing
                },
            )
        }

        // 动画结束回调
        launch {
            kotlinx.coroutines.delay(300L)
            onAnimationEnd?.invoke()
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawBiometricSuccessAnimation(
                scale = scale.value,
                alpha = alpha.value,
                progress = rotation.value,
                size = size,
            )
        }
    }
}

/**
 * 绘制生物识别成功动画
 */
private fun DrawScope.drawBiometricSuccessAnimation(
    scale: Float,
    alpha: Float,
    progress: Float,
    size: Dp,
) {
    val center = Offset(size.toPx() / 2, size.toPx() / 2)
    val maxRadius = size.toPx() / 2 * 0.85f

    // 绘制外环（渐变光晕）
    val outerGradient = Brush.radialGradient(
        colors = listOf(
            QuantumGreen.copy(alpha = 0.3f * alpha),
            QuantumGreen.copy(alpha = 0.1f * alpha),
            Color.Transparent,
        ),
    )
    drawCircle(
        brush = outerGradient,
        radius = maxRadius * scale,
        center = center,
    )

    // 绘制主环
    drawCircle(
        color = QuantumGreen.copy(alpha = alpha),
        radius = maxRadius * 0.7f * scale,
        center = center,
        style = Stroke(width = 4.dp.toPx()),
    )

    // 绘制内环（虚线效果）
    val dashCount = 12
    val dashAngle = 360f / dashCount
    repeat(dashCount) { i ->
        val angle = Math.toRadians((i * dashAngle).toDouble())
        val startRadius = maxRadius * 0.55f * scale
        val endRadius = maxRadius * 0.65f * scale
        val x = center.x + cos(angle) * startRadius
        val y = center.y + sin(angle) * startRadius
        val endX = center.x + cos(angle) * endRadius
        val endY = center.y + sin(angle) * endRadius

        drawLine(
            color = QuantumGreen.copy(alpha = alpha * 0.8f),
            start = Offset(x.toFloat(), y.toFloat()),
            end = Offset(endX.toFloat(), endY.toFloat()),
            strokeWidth = 2.dp.toPx(),
        )
    }

    // 绘制检查标记
    if (progress > 0) {
        drawCheckMark(
            center = center,
            radius = maxRadius * 0.35f * scale,
            progress = progress,
            color = QuantumGreen.copy(alpha = alpha),
        )
    }

    // 绘制粒子效果
    if (scale > 0.5f) {
        val particleCount = 8
        val particleProgress = (scale - 0.5f) * 2f
        repeat(particleCount) { i ->
            val angle = Math.toRadians((i * 360f / particleCount).toDouble())
            val distance = maxRadius * particleProgress
            val x = center.x + cos(angle) * distance
            val y = center.y + sin(angle) * distance
            val particleAlpha = (1f - particleProgress) * alpha

            drawCircle(
                color = QuantumGreen.copy(alpha = particleAlpha),
                radius = 3.dp.toPx(),
                center = Offset(x.toFloat(), y.toFloat()),
            )
        }
    }
}

/**
 * 绘制检查标记
 */
private fun DrawScope.drawCheckMark(
    center: Offset,
    radius: Float,
    progress: Float,
    color: Color,
) {
    val checkMarkPath = Path().apply {
        val startX = center.x - radius * 0.4f
        val startY = center.y
        val midX = center.x - radius * 0.1f
        val midY = center.y + radius * 0.4f
        val endX = center.x + radius * 0.5f
        val endY = center.y - radius * 0.3f

        if (progress < 0.5f) {
            // 第一段：从起点到中间
            val segmentProgress = progress * 2f
            val currentX = startX + (midX - startX) * segmentProgress
            val currentY = startY + (midY - startY) * segmentProgress
            moveTo(startX, startY)
            lineTo(currentX, currentY)
        } else {
            // 第二段：从中间到终点
            val segmentProgress = (progress - 0.5f) * 2f
            val currentX = midX + (endX - midX) * segmentProgress
            val currentY = midY + (endY - midY) * segmentProgress
            moveTo(startX, startY)
            lineTo(midX, midY)
            lineTo(currentX, currentY)
        }
    }

    drawPath(
        path = checkMarkPath,
        color = color,
        style = Stroke(width = 6.dp.toPx()),
    )
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun BiometricSuccessAnimationPreview() {
    AeternumPreviewTheme {
        BiometricSuccessAnimation(
            size = 120.dp,
        )
    }
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun BiometricSuccessAnimationPreview_Small() {
    AeternumPreviewTheme {
        BiometricSuccessAnimation(
            size = 80.dp,
        )
    }
}

@Preview(showBackground = true, widthDp = 300, heightDp = 300)
@Composable
private fun BiometricSuccessAnimationPreview_Large() {
    AeternumPreviewTheme {
        BiometricSuccessAnimation(
            size = 200.dp,
        )
    }
}
