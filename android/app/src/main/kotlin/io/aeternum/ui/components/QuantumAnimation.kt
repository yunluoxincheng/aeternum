package io.aeternum.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.QuantumYellow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aeternum 量子动画组件
 *
 * 用于展示"后量子安全"的视觉效果，增强用户对产品安全特性的感知。
 *
 * ## 设计理念
 * - **科技感**: 使用旋转、脉冲、渐变效果传达"量子"概念
 * - **状态关联**: 不同动画状态对应不同的系统状态
 * - **性能优化**: 使用高效动画 API，避免过度绘制
 *
 * ## 动画类型
 * - **Rotating**: 旋转量子环（用于密钥轮换）
 * - **Pulsing**: 脉冲警告（用于否决通知/降级状态）
 * - **Fading**: 淡入淡出过渡（用于状态切换）
 *
 * ## 架构约束
 * - INVARIANT: UI 层动画，不涉及任何密钥材料
 * - 所有动画均为视觉反馈，不携带敏感信息
 *
 * @param modifier 修饰符
 * @param type 动画类型
 * @param size 动画尺寸
 * @param color 动画主色调
 * @param speed 动画速度倍数
 */
@Composable
fun QuantumAnimation(
    modifier: Modifier = Modifier,
    type: QuantumAnimationType,
    size: Dp = 48.dp,
    color: Color = QuantumBlue,
    speed: Float = 1.0f,
) {
    when (type) {
        is QuantumAnimationType.Rotating -> {
            RotatingQuantumAnimation(
                modifier = modifier,
                size = size,
                color = color,
                speed = speed,
                particleCount = type.particleCount,
            )
        }
        is QuantumAnimationType.Pulsing -> {
            PulsingQuantumAnimation(
                modifier = modifier,
                size = size,
                color = color,
                speed = speed,
            )
        }
        is QuantumAnimationType.Fading -> {
            FadingQuantumAnimation(
                modifier = modifier,
                size = size,
                color = color,
                speed = speed,
            )
        }
    }
}

/**
 * 旋转量子动画
 *
 * 显示围绕中心旋转的量子粒子，用于密钥轮换状态。
 *
 * 性能优化：
 * - 使用 remember 缓存渐变对象，避免每帧重新创建
 * - 预计算粒子角度步长，减少重复计算
 */
@Composable
private fun RotatingQuantumAnimation(
    modifier: Modifier = Modifier,
    size: Dp,
    color: Color,
    speed: Float,
    particleCount: Int = 8,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotating_quantum")

    // 旋转角度 - 使用 animateValue 与 Float.VectorConverter
    val rotation by infiniteTransition.animateValue(
        initialValue = 0f,
        targetValue = 360f,
        typeConverter = Float.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (3000 / speed).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // 脉冲缩放 - 使用 animateValue 与 Float.VectorConverter
    val scale by infiniteTransition.animateValue(
        initialValue = 1f,
        targetValue = 1.1f,
        typeConverter = Float.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                this.durationMillis = (2000 / speed).toInt()
                1f at 0 with FastOutSlowInEasing
                1.1f at (durationMillis / 2) with FastOutSlowInEasing
                1f at durationMillis with FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_scale",
    )

    // 缓存渐变对象，避免每帧重新创建
    val gradient = remember(color) {
        Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.6f),
                color.copy(alpha = 0.2f),
                Color.Transparent,
            ),
        )
    }

    // 预计算角度步长（性能优化）
    val angleStep = remember(particleCount) { 360f / particleCount }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .scale(scale),
        ) {
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            val radius = size.toPx() / 2 * 0.8f

            // 绘制中心光晕
            drawCircle(
                brush = gradient,
                radius = radius * 0.3f,
                center = center,
            )

            // 绘制旋转粒子
            repeat(particleCount) { i ->
                val angle = Math.toRadians((rotation + i * angleStep).toDouble())
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()

                // 粒子轨迹
                drawCircle(
                    color = color.copy(alpha = 0.1f),
                    radius = radius * 0.15f,
                    center = Offset(x, y),
                )

                // 粒子核心
                drawCircle(
                    color = color,
                    radius = radius * 0.06f,
                    center = Offset(x, y),
                )
            }

            // 绘制外环
            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/**
 * 脉冲量子动画
 *
 * 显示从中心向外扩散的脉冲波，用于警告/降级状态。
 *
 * 性能优化：
 * - 使用 rememberInfiniteTransition 替代 LaunchedEffect + delay
 * - 使用 remember 缓存渐变对象
 */
@Composable
private fun PulsingQuantumAnimation(
    modifier: Modifier = Modifier,
    size: Dp,
    color: Color,
    speed: Float,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_quantum")

    // 使用无限过渡动画替代 LaunchedEffect + delay
    val pulseProgress by infiniteTransition.animateValue(
        initialValue = 0f,
        targetValue = 1f,
        typeConverter = Float.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1000 / speed).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_progress",
    )

    // 缓存渐变对象，避免每帧重新创建
    val gradient = remember(color) {
        Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.8f),
                color.copy(alpha = 0.3f),
                color.copy(alpha = 0.1f),
                Color.Transparent,
            ),
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            val maxRadius = size.toPx() / 2

            // 绘制多层脉冲波（优化：使用 pulseProgress 计算）
            repeat(3) { i ->
                val waveProgress = ((pulseProgress + i * 0.33f) % 1f)
                val alpha = (1f - waveProgress).coerceIn(0f, 1f)
                val radius = maxRadius * (0.2f + waveProgress * 0.8f)

                drawCircle(
                    brush = gradient,
                    radius = radius,
                    center = center,
                    alpha = alpha * 0.5f,
                )
            }

            // 绘制中心核心
            drawCircle(
                color = color,
                radius = maxRadius * 0.2f,
                center = center,
            )
        }
    }
}

/**
 * 淡入淡出量子动画
 *
 * 显示呼吸效果的渐变动画，用于状态切换过渡。
 *
 * 性能优化：
 * - 使用 remember 缓存渐变对象
 * - 使用 keyframes 动画规格获得更平滑的呼吸效果
 */
@Composable
private fun FadingQuantumAnimation(
    modifier: Modifier = Modifier,
    size: Dp,
    color: Color,
    speed: Float,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fading_quantum")

    // 淡入淡出 alpha
    val alpha by infiniteTransition.animateValue(
        initialValue = 0.3f,
        targetValue = 1f,
        typeConverter = Float.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                this.durationMillis = (2000 / speed).toInt()
                0.3f at 0 with FastOutSlowInEasing
                1f at (durationMillis / 2) with FastOutSlowInEasing
                0.3f at durationMillis with FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "fade_alpha",
    )

    // 旋转渐变
    val rotation by infiniteTransition.animateValue(
        initialValue = 0f,
        targetValue = 360f,
        typeConverter = Float.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (4000 / speed).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fade_rotation",
    )

    // 缓存渐变对象，避免每帧重新创建
    val gradient = remember(color) {
        Brush.sweepGradient(
            colors = listOf(
                color.copy(alpha = 0.2f),
                color.copy(alpha = 0.6f),
                color.copy(alpha = 0.2f),
            ),
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            val radius = size.toPx() / 2 * 0.8f

            // 绘制旋转渐变背景
            drawCircle(
                brush = gradient,
                radius = radius,
                center = center,
                alpha = alpha * 0.5f,
            )

            // 绘制中心圆
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius * 0.3f,
                center = center,
            )
        }
    }
}

/**
 * 量子动画类型
 *
 * 使用 @Stable 注解帮助 Compose 编译器进行稳定性推断，
 * 减少不必要的重组。
 */
@Stable
sealed class QuantumAnimationType {
    /**
     * 旋转动画
     *
     * 用于密钥轮换状态，显示围绕中心旋转的量子粒子
     *
     * @property particleCount 粒子数量
     */
    @Immutable
    data class Rotating(val particleCount: Int = 8) : QuantumAnimationType()

    /**
     * 脉冲动画
     *
     * 用于警告/降级状态，显示从中心向外扩散的脉冲波
     */
    data object Pulsing : QuantumAnimationType()

    /**
     * 淡入淡出动画
     *
     * 用于状态切换过渡，显示呼吸效果的渐变
     */
    data object Fading : QuantumAnimationType()
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun QuantumAnimationPreview_Rotating() {
    AeternumPreviewTheme {
        QuantumAnimation(
            type = QuantumAnimationType.Rotating(),
            size = 64.dp,
            color = QuantumBlue,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun QuantumAnimationPreview_Pulsing() {
    AeternumPreviewTheme {
        QuantumAnimation(
            type = QuantumAnimationType.Pulsing,
            size = 64.dp,
            color = QuantumRed,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun QuantumAnimationPreview_Fading() {
    AeternumPreviewTheme {
        QuantumAnimation(
            type = QuantumAnimationType.Fading,
            size = 64.dp,
            color = QuantumGreen,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun QuantumAnimationPreview_AllTypes() {
    AeternumPreviewTheme {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            QuantumAnimation(
                type = QuantumAnimationType.Rotating(),
                size = 48.dp,
                color = QuantumBlue,
            )
            QuantumAnimation(
                type = QuantumAnimationType.Pulsing,
                size = 48.dp,
                color = QuantumYellow,
            )
            QuantumAnimation(
                type = QuantumAnimationType.Fading,
                size = 48.dp,
                color = QuantumGreen,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuantumAnimationPreview_CustomSpeed() {
    AeternumPreviewTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            QuantumAnimation(
                type = QuantumAnimationType.Rotating(),
                size = 48.dp,
                color = QuantumBlue,
                speed = 0.5f,
            )
            QuantumAnimation(
                type = QuantumAnimationType.Rotating(),
                size = 48.dp,
                color = QuantumBlue,
                speed = 1.0f,
            )
            QuantumAnimation(
                type = QuantumAnimationType.Rotating(),
                size = 48.dp,
                color = QuantumBlue,
                speed = 2.0f,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuantumAnimationPreview_AllColors() {
    AeternumPreviewTheme {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            QuantumAnimation(
                type = QuantumAnimationType.Rotating(),
                size = 48.dp,
                color = QuantumBlue,
            )
            QuantumAnimation(
                type = QuantumAnimationType.Rotating(),
                size = 48.dp,
                color = QuantumGreen,
            )
            QuantumAnimation(
                type = QuantumAnimationType.Rotating(),
                size = 48.dp,
                color = QuantumYellow,
            )
            QuantumAnimation(
                type = QuantumAnimationType.Rotating(),
                size = 48.dp,
                color = QuantumRed,
            )
        }
    }
}
