package io.aeternum.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue as animateValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.navigation.Easing
import io.aeternum.ui.navigation.AnimationDuration
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 否决信号脉冲动画组件
 *
 * 用于显示收到否决信号时的警告视觉效果。
 *
 * ## 设计理念
 * - **紧迫感**: 红色脉冲传达"需要立即处理"的感觉
 * - **持续性**: 1000ms 循环动画，持续吸引注意
 * - **层次感**: 多层脉冲波，增强视觉冲击力
 *
 * ## 动画规范
 * - 持续时间: 1000ms 循环
 * - 缓动曲线: FastOutSlowIn
 * - 参考: proposal.md §9.2 状态动画
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅提供视觉反馈，不执行任何协议操作
 * - 动画纯视觉效果，不涉及任何密钥材料或协议状态
 *
 * @param modifier 修饰符
 * @param size 动画尺寸
 * @param isUrgent 是否为紧急状态（影响动画速度）
 * @param pulseCount 脉冲波层数
 */
@Composable
fun VetoPulseAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    isUrgent: Boolean = true,
    pulseCount: Int = 3,
) {
    val speedMultiplier = if (isUrgent) 1.0f else 0.7f
    var pulseState by remember { mutableIntStateOf(0) }

    // 脉冲状态循环
    LaunchedEffect(Unit) {
        while (true) {
            pulseState = (pulseState + 1) % pulseCount
            delay((1000 / speedMultiplier).toLong())
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "veto_pulse")

    // 旋转动画
    val rotation by infiniteTransition.animateValue(
        initialValue = 0f,
        targetValue = 360f,
        typeConverter = Float.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (8000 / speedMultiplier).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // 缩放脉冲
    val scale by infiniteTransition.animateValue(
        initialValue = 1f,
        targetValue = 1.15f,
        typeConverter = Float.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                this.durationMillis = (2000 / speedMultiplier).toInt()
                1f at 0 with FastOutSlowInEasing
                1.15f at (durationMillis / 2) with FastOutSlowInEasing
                1f at durationMillis with FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "scale",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawVetoPulseAnimation(
                pulseState = pulseState,
                pulseCount = pulseCount,
                rotation = rotation,
                scale = scale,
                size = size,
            )
        }
    }
}

/**
 * 绘制否决脉冲动画
 */
private fun DrawScope.drawVetoPulseAnimation(
    pulseState: Int,
    pulseCount: Int,
    rotation: Float,
    scale: Float,
    size: Dp,
) {
    val center = Offset(size.toPx() / 2, size.toPx() / 2)
    val maxRadius = size.toPx() / 2 * 0.9f

    // 背景渐变
    val backgroundGradient = Brush.radialGradient(
        colors = listOf(
            QuantumRed.copy(alpha = 0.15f),
            QuantumRed.copy(alpha = 0.05f),
            Color.Transparent,
        ),
    )
    drawCircle(
        brush = backgroundGradient,
        radius = maxRadius * scale,
        center = center,
    )

    // 绘制多层脉冲波
    repeat(pulseCount) { i ->
        val layerProgress = ((pulseState - i + pulseCount) % pulseCount) / pulseCount.toFloat()
        val alpha = 1f - layerProgress
        val radius = maxRadius * (0.3f + layerProgress * 0.7f)
        val strokeWidth = (maxRadius * 0.05f * (1f - layerProgress)).coerceAtLeast(2.dp.toPx())

        // 脉冲环
        drawCircle(
            color = QuantumRed.copy(alpha = alpha * 0.6f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )

        // 第二层细环
        drawCircle(
            color = QuantumRed.copy(alpha = alpha * 0.3f),
            radius = radius * 0.85f,
            center = center,
            style = Stroke(width = strokeWidth * 0.5f),
        )
    }

    // 绘制旋转的警告图标
    drawWarningIcon(
        center = center,
        radius = maxRadius * 0.4f,
        rotation = rotation,
    )

    // 绘制中心核心
    drawCircle(
        color = QuantumRed,
        radius = maxRadius * 0.15f * scale,
        center = center,
    )

    // 绘制外圈装饰
    val decorationCount = 12
    val decorationAngle = 360f / decorationCount
    repeat(decorationCount) { i ->
        val angle = Math.toRadians((rotation + i * decorationAngle).toDouble())
        val distance = maxRadius * 0.75f * scale
        val x = center.x + cos(angle) * distance
        val y = center.y + sin(angle) * distance

        // 装饰点
        drawCircle(
            color = QuantumRed.copy(alpha = 0.6f),
            radius = 3.dp.toPx(),
            center = Offset(x.toFloat(), y.toFloat()),
        )
    }
}

/**
 * 绘制警告图标
 */
private fun DrawScope.drawWarningIcon(
    center: Offset,
    radius: Float,
    rotation: Float,
) {
    val iconRadius = radius * 0.6f

    // 绘制三角形背景
    val trianglePath = Path().apply {
        val angle1 = Math.toRadians((rotation - 90).toDouble())
        val angle2 = Math.toRadians((rotation + 30).toDouble())
        val angle3 = Math.toRadians((rotation + 150).toDouble())

        val x1 = center.x + cos(angle1) * iconRadius
        val y1 = center.y + sin(angle1) * iconRadius
        val x2 = center.x + cos(angle2) * iconRadius
        val y2 = center.y + sin(angle2) * iconRadius
        val x3 = center.x + cos(angle3) * iconRadius
        val y3 = center.y + sin(angle3) * iconRadius

        moveTo(x1.toFloat(), y1.toFloat())
        lineTo(x2.toFloat(), y2.toFloat())
        lineTo(x3.toFloat(), y3.toFloat())
        close()
    }

    drawPath(
        path = trianglePath,
        color = QuantumRed.copy(alpha = 0.8f),
        style = Stroke(width = 4.dp.toPx()),
    )

    // 绘制感叹号
    val exclamationPath = Path().apply {
        // 竖线
        val lineTop = center.y - radius * 0.2f
        val lineBottom = center.y + radius * 0.2f
        moveTo(center.x, lineTop)
        lineTo(center.x, lineBottom)

        // 点
        val dotCenter = center.y + radius * 0.35f
        val dotRadius = 4.dp.toPx()
        addArc(
            Rect(
                left = center.x - dotRadius,
                top = dotCenter - dotRadius,
                right = center.x + dotRadius,
                bottom = dotCenter + dotRadius
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 360f
        )
    }

    drawPath(
        path = exclamationPath,
        color = QuantumRed,
        style = Fill,
    )
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun VetoPulseAnimationPreview() {
    AeternumPreviewTheme {
        VetoPulseAnimation(
            size = 100.dp,
            isUrgent = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 150, heightDp = 150)
@Composable
private fun VetoPulseAnimationPreview_Large() {
    AeternumPreviewTheme {
        VetoPulseAnimation(
            size = 150.dp,
            isUrgent = true,
            pulseCount = 4,
        )
    }
}

@Preview(showBackground = true, widthDp = 80, heightDp = 80)
@Composable
private fun VetoPulseAnimationPreview_Small() {
    AeternumPreviewTheme {
        VetoPulseAnimation(
            size = 70.dp,
            isUrgent = false,
        )
    }
}
