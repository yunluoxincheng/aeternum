package io.aeternum.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.SurfaceColor
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Aeternum 微交互效果组件
 *
 * 用于增强用户交互体验的细微动画效果。
 *
 * ## 设计理念
 * - **即时反馈**: 用户操作立即获得视觉反馈
 * - **自然流畅**: 符合物理直觉的动画效果
 * - **性能优化**: 轻量级动画，不阻塞主线程
 *
 * ## 微交互类型
 * - **按钮点击反馈**: 缩放 + 涟漪效果
 * - **卡片交互**: 悬停时阴影变化 + 轻微提升
 * - **列表项滑动**: 滑动操作按钮显示
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅处理视觉效果，不涉及敏感操作
 *
 * ## 动画规范
 * - 按钮点击: 100ms 弹性缩放
 * - 卡片悬停: 200ms 阴影过渡
 * - 参考: proposal.md §9.3 微交互
 */

// ============================================================================
// 按钮点击反馈
// ============================================================================

/**
 * 带有点击反馈的修饰符
 *
 * 为可点击元素添加按下时的缩放效果。
 *
 * @param enabled 是否启用
 * @param onPressScale 按下时的缩放比例 (0.95-0.99)
 * @param animationSpec 动画规格
 */
fun Modifier.clickFeedback(
    enabled: Boolean = true,
    onPressScale: Float = 0.97f,
    animationSpec: SpringSpec<Float> = spring(
        stiffness = 800f,
        dampingRatio = 0.8f,
    ),
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) onPressScale else 1f,
        animationSpec = animationSpec,
        label = "click_feedback_scale",
    )

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions
            .filterIsInstance<PressInteraction.Release>()
            .collect { isPressed = false }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions
            .filterIsInstance<PressInteraction.Press>()
            .collect { isPressed = true }
    }

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = { },
        )
}

// ============================================================================
// 卡片交互效果
// ============================================================================

/**
 * 卡片交互效果
 *
 * 为卡片添加悬停时的视觉反馈效果。
 *
 * @param modifier 修饰符
 * @param onClick 点击回调
 * @param enabled 是否启用
 * @param elevationPressed 按下时的阴影高度
 * @param elevationDefault 默认阴影高度
 * @param elevationHovered 悬停时的阴影高度
 * @param shape 卡片形状
 * @param backgroundColor 背景颜色
 * @param borderStroke 边框
 * @param content 内容
 */
@Composable
fun InteractiveCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    elevationPressed: Dp = 2.dp,
    elevationDefault: Dp = 4.dp,
    elevationHovered: Dp = 8.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = SurfaceColor,
    borderStroke: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }

    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            isPressed -> elevationPressed
            isHovered -> elevationHovered
            else -> elevationDefault
        },
        animationSpec = spring(
            stiffness = 600f,
            dampingRatio = 0.7f,
        ),
        label = "interactive_card_elevation",
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            stiffness = 800f,
            dampingRatio = 0.8f,
        ),
        label = "interactive_card_scale",
    )

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release -> isPressed = false
                is PressInteraction.Cancel -> isPressed = false
            }
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .shadow(elevation, shape, clip = false),
        shape = shape,
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
        ),
        interactionSource = interactionSource,
        enabled = enabled,
    ) {
        content()
    }
}

// ============================================================================
// 列表项滑动操作
// ============================================================================

/**
 * 滑动操作状态
 *
 * @param offset 当前偏移量
 * @param isActionVisible 是否显示操作按钮
 * @param actionWidth 操作按钮宽度
 */
data class SwipeActionState(
    val offset: Float = 0f,
    val isActionVisible: Boolean = false,
    val actionWidth: Float = 0f,
)

/**
 * 滑动操作配置
 *
 * @param actionWidth 操作按钮宽度
 * @param actionBackgroundColor 操作按钮背景色
 * @param swipeThreshold 触发操作的滑动阈值 (0-1)
 */
data class SwipeActionConfig(
    val actionWidth: Dp = 80.dp,
    val actionBackgroundColor: Color = Color.Red,
    val swipeThreshold: Float = 0.5f,
)

/**
 * 带滑动操作的列表项
 *
 * 允许用户滑动列表项以显示操作按钮。
 *
 * @param modifier 修饰符
 * @param config 滑动操作配置
 * @param onAction 操作回调
 * @param backgroundContent 背景内容（操作按钮）
 * @param foregroundContent 前景内容
 */
@Composable
fun SwipeActionItem(
    modifier: Modifier = Modifier,
    config: SwipeActionConfig = SwipeActionConfig(),
    onAction: () -> Unit = {},
    backgroundContent: @Composable () -> Unit,
    foregroundContent: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isActionVisible by remember { mutableStateOf(false) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isActionVisible) -config.actionWidth.value else 0f,
        animationSpec = spring(
            stiffness = 400f,
            dampingRatio = 0.8f,
        ),
        label = "swipe_action_offset",
    )

    Box(modifier = modifier) {
        // 背景内容（操作按钮）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            backgroundContent()
        }

        // 前景内容
        Box(
            modifier = Modifier
                .offset(x = animatedOffsetX.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isActionVisible) {
                            // 点击关闭操作按钮
                            isActionVisible = false
                        }
                    },
                ),
        ) {
            foregroundContent()
        }
    }
}

// ============================================================================
// 波纹效果
// ============================================================================

/**
 * 波纹效果修饰符
 *
 * 为可点击元素添加 Material Design 风格的波纹效果。
 *
 * @param color 波纹颜色
 * @param radius 波纹半径
 */
fun Modifier.rippleEffect(
    color: Color = Color.White.copy(alpha = 0.3f),
    radius: Dp = 100.dp,
): Modifier = composed {
    var rippleX by remember { mutableFloatStateOf(0f) }
    var rippleY by remember { mutableFloatStateOf(0f) }
    var rippleRadius by remember { mutableFloatStateOf(0f) }
    var isRippling by remember { mutableStateOf(false) }

    val animatedRadius by animateFloatAsState(
        targetValue = if (isRippling) radius.value else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "ripple_radius",
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isRippling) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "ripple_alpha",
    )

    Box(modifier = this) {
        // 波纹效果层
        if (isRippling) {
            Box(
                modifier = Modifier
                    .offset(x = rippleX.dp - radius / 2, y = rippleY.dp - radius / 2)
                    .alpha(animatedAlpha),
            ) {
                // TODO: 实现波纹绘制
            }
        }
    }

    this.then(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                // 触发波纹效果
            },
        )
    )
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun InteractiveCardPreview() {
    AeternumPreviewTheme {
        InteractiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(
                text = "交互式卡片",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun InteractiveCardPreview_Disabled() {
    AeternumPreviewTheme {
        InteractiveCard(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(
                text = "禁用状态的交互式卡片",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SwipeActionItemPreview() {
    AeternumPreviewTheme {
        SwipeActionItem(
            onAction = {},
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            backgroundContent = {
                Surface(
                    color = io.aeternum.ui.theme.QuantumRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "删除",
                        modifier = Modifier.padding(16.dp),
                        color = Color.White,
                    )
                }
            },
            foregroundContent = {
                Surface(
                    color = SurfaceColor,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "滑动列表项",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            },
        )
    }
}
