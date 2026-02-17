package io.aeternum.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.DeepSpaceGradientEnd
import io.aeternum.ui.theme.DeepSpaceGradientStart
import io.aeternum.ui.theme.QuantumBlue

/**
 * Aeternum 欢迎屏幕
 *
 * INVARIANT: UI 层 - 仅负责展示和用户交互
 * 此屏幕用于新用户首次启动应用时展示欢迎信息
 *
 * 设计理念：
 * - 极简主义：减少视觉噪音
 * - 安全感：通过动画和色彩传达"安全"的感觉
 * - 后量子科技感：使用深色主题和科技感元素
 *
 * 动画效果：
 * - 背景渐变：缓慢流动的深空渐变
 * - 量子圆环：呼吸效果的量子环动画
 * - 标题淡入：流畅的淡入动画
 * - 按钮缩放：悬停时的缩放效果
 *
 * @param onGetStarted 点击"开始设置"按钮的回调
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
) {
    // 背景渐变动画状态
    val backgroundOffset = remember { Animatable(0f) }

    // 启动背景渐变动画
    LaunchedEffect(Unit) {
        backgroundOffset.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 10000,
                    easing = LinearOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // 背景渐变
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(
            DeepSpaceGradientStart,
            DeepSpaceGradientEnd
        ),
        start = Offset.Zero,
        end = Offset(
            x = backgroundOffset.value * 1000f,
            y = 1000f
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeepSpaceBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
        ) {
            WelcomeContent(
                onGetStarted = onGetStarted
            )
        }
    }
}

/**
 * 欢迎屏幕内容
 *
 * 包含：
 * - 量子圆环动画
 * - 标题和描述
 * - 开始按钮
 *
 * @param onGetStarted 点击"开始设置"按钮的回调
 */
@Composable
private fun WelcomeContent(
    onGetStarted: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 量子圆环动画
        QuantumRingAnimation(
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 标题和描述
        WelcomeTextSection()

        Spacer(modifier = Modifier.weight(1f))

        // 开始按钮
        GetStartedButton(
            onClick = onGetStarted
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 量子圆环动画
 *
 * 创建一个呼吸效果的量子环，传达"后量子安全"的感觉
 * 动画效果：
 * - 外环：缓慢缩放和淡入淡出
 * - 中环：相反相位的缩放和淡入淡出
 * - 内环：快速脉冲
 *
 * @param modifier 修饰符
 */
@Composable
private fun QuantumRingAnimation(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 外环动画
        val outerScale by animateFloatAsState(
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "outer_ring_scale"
        )
        val outerAlpha by animateFloatAsState(
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "outer_ring_alpha"
        )

        QuantumRing(
            modifier = Modifier
                .fillMaxSize()
                .scale(outerScale)
                .alpha(outerAlpha),
            color = QuantumBlue,
            radiusRatio = 1.0f,
            strokeWidth = 2.dp
        )

        // 中环动画
        val middleScale by animateFloatAsState(
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2500,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "middle_ring_scale"
        )
        val middleAlpha by animateFloatAsState(
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2500,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "middle_ring_alpha"
        )

        QuantumRing(
            modifier = Modifier
                .fillMaxSize(0.7f)
                .scale(middleScale)
                .alpha(middleAlpha),
            color = QuantumBlue,
            radiusRatio = 0.7f,
            strokeWidth = 3.dp
        )

        // 内环动画
        val innerScale by animateFloatAsState(
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "inner_ring_scale"
        )
        val innerAlpha by animateFloatAsState(
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "inner_ring_alpha"
        )

        QuantumRing(
            modifier = Modifier
                .fillMaxSize(0.4f)
                .scale(innerScale)
                .alpha(innerAlpha),
            color = QuantumBlue,
            radiusRatio = 0.4f,
            strokeWidth = 4.dp
        )

        // 中心圆
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = QuantumBlue,
                    shape = CircleShape
                )
        )
    }
}

/**
 * 量子圆环
 *
 * 使用 Canvas 绘制精确的圆形边框
 *
 * @param modifier 修饰符
 * @param color 圆环颜色
 * @param radiusRatio 半径比例（相对于父容器）
 * @param strokeWidth 边框宽度
 */
@Composable
private fun QuantumRing(
    modifier: Modifier = Modifier,
    color: Color = QuantumBlue,
    radiusRatio: Float = 1.0f,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp
) {
    Canvas(modifier = modifier) {
        val canvasSize = size
        val radius = (kotlin.math.min(canvasSize.width, canvasSize.height) / 2) * radiusRatio
        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}

/**
 * 欢迎文本部分
 *
 * 包含：
 * - 应用名称 "Aeternum"
 * - 副标题 "后量子安全密钥管理"
 * - 描述文本
 */
@Composable
private fun WelcomeTextSection() {
    // 标题淡入动画
    val titleAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "title_alpha"
    )

    // 副标题淡入动画（延迟）
    val subtitleAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "subtitle_alpha"
    )

    // 描述淡入动画（延迟）
    val descriptionAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "description_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = "Aeternum",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.alpha(titleAlpha),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 副标题
        Text(
            text = "后量子安全密钥管理",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = QuantumBlue,
            modifier = Modifier.alpha(subtitleAlpha),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 描述
        Text(
            text = "采用 ML-KEM (Kyber-1024) 加密技术\n保护您的数字资产安全",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(descriptionAlpha),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 开始按钮
 *
 * 大型主操作按钮，带有悬停缩放效果
 *
 * @param onClick 点击回调
 */
@Composable
private fun GetStartedButton(
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = QuantumBlue
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "开始设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }
}

// ============================================================================
// 预览
// ============================================================================

/**
 * 欢迎屏幕预览
 *
 * 用于在 Android Studio 中预览欢迎屏幕的外观
 */
@Preview(
    showBackground = true,
    name = "欢迎屏幕预览"
)
@Composable
fun WelcomeScreenPreview() {
    AeternumPreviewTheme {
        WelcomeScreen(
            onGetStarted = {
                // 预览中的点击回调
            }
        )
    }
}

/**
 * 欢迎屏幕内容预览
 */
@Preview(
    showBackground = true,
    name = "欢迎内容预览"
)
@Composable
fun WelcomeContentPreview() {
    AeternumPreviewTheme {
        WelcomeContent(
            onGetStarted = {
                // 预览中的点击回调
            }
        )
    }
}
