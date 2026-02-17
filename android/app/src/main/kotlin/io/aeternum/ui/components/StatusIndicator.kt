package io.aeternum.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aeternum.ui.state.AeternumUiState
import io.aeternum.ui.state.ActiveSubState
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.CodeTextStyle
import io.aeternum.ui.theme.MachineStateColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.QuantumYellow
import io.aeternum.ui.theme.StatusIndicatorTextStyle

/**
 * Aeternum 状态指示器组件
 *
 * 用于显示设备的当前安全状态，支持脉冲动画和多种尺寸变体。
 *
 * ## 设计理念
 * - **视觉清晰**: 使用颜色和大小直观传达状态
 * - **动画引导**: 脉冲效果引导用户关注重要状态变化
 * - **无障碍**: 始终提供文字标签说明状态含义
 *
 * ## 状态映射
 * - Idle → 绿色（安全）
 * - Decrypting → 蓝色（活动）
 * - Rekeying → 黄色（警告+脉冲动画）
 * - Degraded → 红色（降级）
 * - Revoked → 深红色（撤销）
 *
 * @param modifier 修饰符
 * @param state UI 状态
 * @param showLabel 是否显示文字标签
 * @param size 指示器尺寸
 */
@Composable
fun StatusIndicator(
    state: AeternumUiState,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    size: StatusIndicatorSize = StatusIndicatorSize.Medium,
) {
    val (color, label) = when (state) {
        is AeternumUiState.Uninitialized -> MachineStateColor.Idle to "未初始化"
        is AeternumUiState.Onboarding -> MachineStateColor.Idle to "初始化中"
        is AeternumUiState.Active -> when (state.subState) {
            is ActiveSubState.Idle -> MachineStateColor.Idle to "空闲"
            is ActiveSubState.Decrypting -> MachineStateColor.Decrypting to "已解锁"
            is ActiveSubState.Rekeying -> MachineStateColor.Rekeying to "轮换中"
        }
        is AeternumUiState.Degraded -> MachineStateColor.Degraded to "降级"
        is AeternumUiState.Revoked -> MachineStateColor.Revoked to "已撤销"
        is AeternumUiState.Error -> MachineStateColor.Degraded to "错误"
    }

    // Rekeying 状态使用脉冲动画
    val isPulsing = state is AeternumUiState.Active &&
            state.subState is ActiveSubState.Rekeying

    StatusIndicator(
        color = color.color,
        label = label,
        modifier = modifier,
        showLabel = showLabel,
        size = size,
        isPulsing = isPulsing,
    )
}

/**
 * 基础状态指示器
 *
 * @param color 状态颜色
 * @param label 状态文字标签
 * @param modifier 修饰符
 * @param showLabel 是否显示标签
 * @param size 指示器尺寸
 * @param isPulsing 是否启用脉冲动画
 * @param accessibilityDescription 自定义无障碍描述（可选）
 */
@Composable
fun StatusIndicator(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    size: StatusIndicatorSize = StatusIndicatorSize.Medium,
    isPulsing: Boolean = false,
    accessibilityDescription: String? = null,
) {
    val indicatorSize = size.indicatorSize
    val scale = if (isPulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
        val animScale by infiniteTransition.animatePulse(
            initialValue = 1f,
            targetValue = 1.3f,
            durationMillis = 1000,
        )
        animScale
    } else {
        1f
    }

    // 无障碍描述
    val semanticDescription = accessibilityDescription ?: "状态：$label${if (isPulsing) "，正在处理中" else ""}"

    if (showLabel) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.semantics {
                role = Role.Image
                contentDescription = semanticDescription
                stateDescription = label
            },
        ) {
            IndicatorDot(
                color = color,
                size = indicatorSize,
                scale = scale,
            )
            Spacer(modifier = Modifier.width(size.labelSpacing))
            Text(
                text = label,
                style = StatusIndicatorTextStyle,
                color = color,
            )
        }
    } else {
        IndicatorDot(
            color = color,
            size = indicatorSize,
            scale = scale,
            modifier = modifier.semantics {
                role = Role.Image
                contentDescription = semanticDescription
                stateDescription = label
            },
        )
    }
}

/**
 * 状态指示点
 *
 * @param color 颜色
 * @param size 尺寸
 * @param scale 缩放比例
 * @param modifier 修饰符
 */
@Composable
private fun IndicatorDot(
    color: Color,
    size: Dp,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        // 主圆点
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape),
        )
        // 脉冲光晕（当 scale > 1 时显示）
        if (scale > 1f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .alpha(0.3f)
                    .background(color, CircleShape),
            )
        }
    }
}

/**
 * 状态指示器尺寸
 */
enum class StatusIndicatorSize(
    /** 指示器圆点尺寸 */
    val indicatorSize: Dp,

    /** 标签间距（当显示标签时） */
    val labelSpacing: Dp,
) {
    /** 小尺寸 - 用于列表项 */
    Small(8.dp, 6.dp),

    /** 中等尺寸 - 标准尺寸 */
    Medium(12.dp, 8.dp),

    /** 大尺寸 - 用于卡片标题 */
    Large(16.dp, 10.dp),
}

// ============================================================================
// 脉冲动画扩展
// ============================================================================

/**
 * 脉冲动画扩展函数
 */
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animatePulse(
    initialValue: Float,
    targetValue: Float,
    durationMillis: Int,
): androidx.compose.runtime.State<Float> = animateValue(
    initialValue = initialValue,
    targetValue = targetValue,
    typeConverter = Float.VectorConverter,
    animationSpec = infiniteRepeatable(
        animation = tween(
            durationMillis = durationMillis,
            easing = FastOutSlowInEasing,
        ),
        repeatMode = RepeatMode.Restart,
    ),
    label = "pulse",
)

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun StatusIndicatorPreview_Idle() {
    AeternumPreviewTheme {
        StatusIndicator(
            color = QuantumGreen,
            label = "空闲",
            showLabel = true,
            size = StatusIndicatorSize.Medium,
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun StatusIndicatorPreview_Decrypting() {
    AeternumPreviewTheme {
        StatusIndicator(
            color = QuantumBlue,
            label = "已解锁",
            showLabel = true,
            size = StatusIndicatorSize.Medium,
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun StatusIndicatorPreview_Rekeying() {
    AeternumPreviewTheme {
        StatusIndicator(
            color = QuantumYellow,
            label = "轮换中",
            showLabel = true,
            size = StatusIndicatorSize.Medium,
            isPulsing = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun StatusIndicatorPreview_Degraded() {
    AeternumPreviewTheme {
        StatusIndicator(
            color = QuantumRed,
            label = "降级",
            showLabel = true,
            size = StatusIndicatorSize.Medium,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun StatusIndicatorPreview_Sizes() {
    AeternumPreviewTheme {
        Row {
            StatusIndicator(
                color = QuantumGreen,
                label = "小",
                size = StatusIndicatorSize.Small,
            )
            StatusIndicator(
                color = QuantumBlue,
                label = "中",
                size = StatusIndicatorSize.Medium,
            )
            StatusIndicator(
                color = QuantumYellow,
                label = "大",
                size = StatusIndicatorSize.Large,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun StatusIndicatorPreview_DotOnly() {
    AeternumPreviewTheme {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIndicator(
                color = QuantumGreen,
                label = "点",
                showLabel = false,
                size = StatusIndicatorSize.Small,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("状态", style = CodeTextStyle)
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StatusIndicatorPreview_AllStates() {
    AeternumPreviewTheme {
        Row {
            StatusIndicator(
                color = QuantumGreen,
                label = "安全",
                size = StatusIndicatorSize.Small,
            )
            StatusIndicator(
                color = QuantumBlue,
                label = "活动",
                size = StatusIndicatorSize.Small,
            )
            StatusIndicator(
                color = QuantumYellow,
                label = "轮换",
                size = StatusIndicatorSize.Small,
            )
            StatusIndicator(
                color = QuantumRed,
                label = "降级",
                size = StatusIndicatorSize.Small,
            )
        }
    }
}
