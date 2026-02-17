package io.aeternum.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import io.aeternum.security.AndroidSecurityManager
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.DeepSpaceGradientEnd
import io.aeternum.ui.theme.DeepSpaceGradientStart
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.QuantumYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Aeternum 设备注册屏幕
 *
 * INVARIANT: UI 层 - 仅负责展示和用户交互
 * 此屏幕用于新用户完成助记词备份后的设备注册流程
 *
 * 功能：
 * - 连接到 AndroidSecurityManager 生成硬件密钥
 * - 显示注册进度（多阶段流程）
 * - 完成后导航到主屏幕
 *
 * 状态机转换：Uninitialized → Initializing → Active
 *
 * 设计理念：
 * - 科技感：使用量子动画传达"正在配置高科技安全"
 * - 透明度：清晰展示每个步骤的进度
 * - 安全感：通过动画和色彩传达"安全"的感觉
 *
 * @param onBack 点击返回按钮的回调
 * @param onRegistrationComplete 注册完成的回调
 * @param onRetry 重试注册的回调（失败时）
 */
@Composable
fun RegistrationScreen(
    onBack: () -> Unit,
    onRegistrationComplete: () -> Unit,
    onRetry: () -> Unit,
) {
    // 注册状态
    var registrationState by remember { mutableStateOf<RegistrationState>(RegistrationState.Idle) }

    // 启动注册流程
    LaunchedEffect(Unit) {
        delay(500) // 短暂延迟，让用户看到界面
        registrationState = RegistrationState.InProgress(RegistrationStep.GENERATING_KEYS, 0f)
    }

    // 模拟注册流程进度
    LaunchedEffect(registrationState) {
        if (registrationState is RegistrationState.InProgress) {
            simulateRegistrationFlow { step, progress ->
                registrationState = RegistrationState.InProgress(step, progress)
            }
            registrationState = RegistrationState.Success
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeepSpaceBackground
    ) {
        RegistrationContent(
            registrationState = registrationState,
            onBack = onBack,
            onRetry = {
                registrationState = RegistrationState.Idle
                onRetry()
            },
            onComplete = onRegistrationComplete,
        )
    }
}

/**
 * 注册状态
 *
 * 表示设备注册的当前状态
 */
sealed class RegistrationState {
    /**
     * 空闲状态
     */
    data object Idle : RegistrationState()

    /**
     * 进行中
     *
     * @property currentStep 当前步骤
     * @property progress 进度 (0.0 - 1.0)
     */
    data class InProgress(
        val currentStep: RegistrationStep,
        val progress: Float,
    ) : RegistrationState()

    /**
     * 成功
     */
    data object Success : RegistrationState()

    /**
     * 失败
     *
     * @property error 错误信息
     */
    data class Failed(val error: String) : RegistrationState()
}

/**
 * 注册步骤
 *
 * 设备注册的具体步骤
 */
enum class RegistrationStep(
    val displayName: String,
    val description: String,
    val stepNumber: Int,
) {
    /**
     * 生成硬件密钥
     *
     * 使用 Android KeyStore/StrongBox 生成 DK_hardware
     */
    GENERATING_KEYS(
        displayName = "生成硬件密钥",
        description = "正在使用设备硬件安全模块生成加密密钥...",
        stepNumber = 1,
    ),

    /**
     * 初始化 Vault
     *
     * 创建加密的 Vault 存储并初始化密钥层级
     */
    INITIALIZING_VAULT(
        displayName = "初始化加密存储",
        description = "正在创建加密 Vault 并初始化密钥层级...",
        stepNumber = 2,
    ),

    /**
     * 配置生物识别
     *
     * 设置生物识别认证（指纹/面部）
     */
    CONFIGURING_BIOMETRIC(
        displayName = "配置生物识别",
        description = "正在设置生物识别安全认证...",
        stepNumber = 3,
    ),

    /**
     * 验证完整性
     *
     * 运行设备完整性检查
     */
    VERIFYING_INTEGRITY(
        displayName = "验证设备完整性",
        description = "正在验证设备安全性和完整性...",
        stepNumber = 4,
    ),

    /**
     * 完成
     */
    COMPLETED(
        displayName = "完成",
        description = "设备注册完成！",
        stepNumber = 5,
    ),
}

/**
 * 模拟注册流程
 *
 * TODO: 替换为实际的 Rust Core 调用
 *
 * @param onProgress 进度回调
 */
private suspend fun simulateRegistrationFlow(
    onProgress: (RegistrationStep, Float) -> Unit,
) {
    val steps = listOf(
        RegistrationStep.GENERATING_KEYS to 0.25f,
        RegistrationStep.INITIALIZING_VAULT to 0.50f,
        RegistrationStep.CONFIGURING_BIOMETRIC to 0.75f,
        RegistrationStep.VERIFYING_INTEGRITY to 0.95f,
        RegistrationStep.COMPLETED to 1.0f,
    )

    for ((step, targetProgress) in steps) {
        onProgress(step, targetProgress)
        delay(1500) // 模拟处理时间
    }
}

/**
 * 设备注册屏幕内容
 *
 * @param registrationState 注册状态
 * @param onBack 点击返回按钮的回调
 * @param onRetry 重试回调
 * @param onComplete 完成回调
 */
@Composable
private fun RegistrationContent(
    registrationState: RegistrationState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部导航栏
        RegistrationTopBar(
            onBack = onBack,
            showBackButton = registrationState !is RegistrationState.InProgress
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 主要内容区域
        when (registrationState) {
            is RegistrationState.Idle -> {
                // 空闲状态（通常不会显示）
            }
            is RegistrationState.InProgress -> {
                RegistrationInProgressContent(
                    currentStep = registrationState.currentStep,
                    progress = registrationState.progress,
                )
            }
            is RegistrationState.Success -> {
                RegistrationSuccessContent(
                    onComplete = onComplete
                )
            }
            is RegistrationState.Failed -> {
                RegistrationFailedContent(
                    error = registrationState.error,
                    onRetry = onRetry,
                )
            }
        }
    }
}

/**
 * 顶部导航栏
 *
 * @param onBack 点击返回按钮的回调
 * @param showBackButton 是否显示返回按钮
 */
@Composable
private fun RegistrationTopBar(
    onBack: () -> Unit,
    showBackButton: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBackButton) {
            // 返回按钮
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        // 标题
        Text(
            text = "注册设备",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * 注册进行中内容
 *
 * 显示：
 * - 旋转的量子动画
 * - 当前步骤信息
 * - 进度条
 *
 * @param currentStep 当前步骤
 * @param progress 进度 (0.0 - 1.0)
 */
@Composable
private fun RegistrationInProgressContent(
    currentStep: RegistrationStep,
    progress: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 旋转动画
        Spacer(modifier = Modifier.weight(1f))

        RotatingQuantumAnimation(
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 步骤信息卡片
        StepInfoCard(
            currentStep = currentStep,
            progress = progress,
        )

        Spacer(modifier = Modifier.weight(1f))

        // 提示文本
        Text(
            text = "请勿关闭应用，正在配置您的安全环境...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 旋转量子动画
 *
 * 创建一个旋转的量子圆环，传达"正在配置高科技安全"
 *
 * @param modifier 修饰符
 */
@Composable
private fun RotatingQuantumAnimation(
    modifier: Modifier = Modifier
) {
    // 旋转角度
    val rotationAngle = remember { Animatable(0f) }

    // 启动旋转动画
    LaunchedEffect(Unit) {
        rotationAngle.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000,
                    easing = LinearOutSlowInEasing
                ),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 外环（慢速旋转）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size
            val radius = kotlin.math.min(canvasSize.width, canvasSize.height) / 2 * 0.9f
            val center = Offset(canvasSize.width / 2, canvasSize.height / 2)

            drawCircle(
                color = QuantumBlue.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // 中环（快速旋转）
        val middleRotation by animateFloatAsState(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500,
                    easing = LinearOutSlowInEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "middle_rotation"
        )

        Canvas(modifier = Modifier.fillMaxSize(0.7f)) {
            val canvasSize = size
            val radius = kotlin.math.min(canvasSize.width, canvasSize.height) / 2 * 0.8f
            val center = Offset(canvasSize.width / 2, canvasSize.height / 2)

            drawCircle(
                color = QuantumBlue.copy(alpha = 0.5f),
                radius = radius,
                center = center,
                style = Stroke(width = 6.dp.toPx())
            )
        }

        // 内环（脉冲）
        val innerScale by animateFloatAsState(
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 800,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "inner_pulse"
        )

        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(innerScale)
                .background(
                    color = QuantumBlue.copy(alpha = 0.8f),
                    shape = CircleShape
                )
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
 * 步骤信息卡片
 *
 * 显示当前步骤的名称、描述和进度
 *
 * @param currentStep 当前步骤
 * @param progress 进度 (0.0 - 1.0)
 */
@Composable
private fun StepInfoCard(
    currentStep: RegistrationStep,
    progress: Float,
) {
    // 入场动画
    val cardAlpha = remember { Animatable(0f) }

    LaunchedEffect(currentStep) {
        cardAlpha.snapTo(0f)
        cardAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha.value),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // 步骤序号
            Text(
                text = "步骤 ${currentStep.stepNumber}/4",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 步骤名称
            Text(
                text = currentStep.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 步骤描述
            Text(
                text = currentStep.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = QuantumBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 进度百分比
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * 注册成功内容
 *
 * 显示：
 * - 成功图标
 * - 成功消息
 * - 完成按钮
 *
 * @param onComplete 完成回调
 */
@Composable
private fun RegistrationSuccessContent(
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // 成功动画
        SuccessAnimation(
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 成功消息
        Text(
            text = "设备注册完成！",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = QuantumGreen,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "您的设备已成功配置\nAeternum 后量子安全保护",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // 完成按钮
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = QuantumGreen
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "开始使用",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 成功动画
 *
 * 显示一个带勾选标记的圆圈，传达"成功"的感觉
 *
 * @param modifier 修饰符
 */
@Composable
private fun SuccessAnimation(
    modifier: Modifier = Modifier
) {
    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "success_scale"
    )

    // 透明度动画
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "success_alpha"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        // 外圈圆环
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = QuantumGreen.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        )

        // 内圈圆环
        Box(
            modifier = Modifier
                .fillMaxSize(0.7f)
                .background(
                    color = QuantumGreen.copy(alpha = 0.4f),
                    shape = CircleShape
                )
        )

        // 中心圆
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = QuantumGreen,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 勾选标记（使用文本）
            Text(
                text = "✓",
                style = MaterialTheme.typography.displayLarge,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 注册失败内容
 *
 * 显示：
 * - 错误图标
 * - 错误消息
 * - 重试按钮
 *
 * @param error 错误信息
 * @param onRetry 重试回调
 */
@Composable
private fun RegistrationFailedContent(
    error: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // 错误图标
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 错误消息
        Text(
            text = "注册失败",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // 重试按钮
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = QuantumYellow
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "重试",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ============================================================================
// 预览
// ============================================================================

/**
 * 注册进行中预览
 */
@Preview(
    showBackground = true,
    name = "注册进行中预览"
)
@Composable
fun RegistrationInProgressPreview() {
    AeternumPreviewTheme {
        Surface(color = DeepSpaceBackground) {
            RegistrationInProgressContent(
                currentStep = RegistrationStep.GENERATING_KEYS,
                progress = 0.25f,
            )
        }
    }
}

/**
 * 注册成功预览
 */
@Preview(
    showBackground = true,
    name = "注册成功预览"
)
@Composable
fun RegistrationSuccessPreview() {
    AeternumPreviewTheme {
        Surface(color = DeepSpaceBackground) {
            RegistrationSuccessContent(
                onComplete = {}
            )
        }
    }
}

/**
 * 注册失败预览
 */
@Preview(
    showBackground = true,
    name = "注册失败预览"
)
@Composable
fun RegistrationFailedPreview() {
    AeternumPreviewTheme {
        Surface(color = DeepSpaceBackground) {
            RegistrationFailedContent(
                error = "设备不支持 StrongBox 硬件安全模块",
                onRetry = {}
            )
        }
    }
}
