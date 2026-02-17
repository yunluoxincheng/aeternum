package io.aeternum.ui.recovery

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.SurfaceColor
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 否决通知屏幕
 *
 * 显示恢复请求的 48 小时否决窗口，用户可以选择否决恢复操作
 *
 * INVARIANT: UI 层 - 仅处理显示和用户交互，否决操作通过 Rust Core
 * 参考：design.md §恢复流程设计
 * 参考：Cold-Anchor-Recovery.md §3 否决机制
 *
 * @param recoveryId 恢复 ID
 * @param onBack 返回回调
 * @param onVetoSubmitted 否决提交成功回调
 * @param onWindowClosed 否决窗口关闭回调（48h 结束）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetoNotificationScreen(
    recoveryId: String,
    onBack: () -> Unit,
    onVetoSubmitted: () -> Unit,
    onWindowClosed: () -> Unit,
) {
    var remainingTime by remember { mutableStateOf(48.hours) }
    var isSubmitting by remember { mutableStateOf(false) }

    // 倒计时逻辑
    LaunchedEffect(Unit) {
        while (remainingTime > Duration.ZERO) {
            delay(1.seconds)
            remainingTime = remainingTime - 1.seconds
        }
        // 倒计时结束
        onWindowClosed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("否决恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = OnDeepSpaceBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceColor,
                    titleContentColor = OnDeepSpaceBackground,
                ),
            )
        },
        containerColor = DeepSpaceBackground,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 脉冲警告图标
            VetoPulseWarningIcon()

            Spacer(modifier = Modifier.height(24.dp))

            // 标题
            Text(
                text = "检测到恢复请求",
                style = MaterialTheme.typography.headlineMedium,
                color = OnDeepSpaceBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 说明
            Text(
                text = "如果这不是您本人操作，请立即否决此次恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 倒计时显示
            CountdownDisplay(remainingTime = remainingTime)

            Spacer(modifier = Modifier.height(32.dp))

            // 否决原因选择（可选）
            VetoReasonSection()

            Spacer(modifier = Modifier.height(24.dp))

            // 否决按钮
            Button(
                onClick = {
                    isSubmitting = true
                    // TODO: 调用 ViewModel 提交否决
                    // viewModel.submitVeto(recoveryId, selectedReason)
                    onVetoSubmitted()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuantumRed,
                    contentColor = Color.White,
                    disabledContainerColor = QuantumRed.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (isSubmitting) "提交中..." else "否决恢复",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 安全提示
            SecurityHintBanner()
        }
    }
}

/**
 * 脉冲警告图标
 *
 * 否决信号的红色脉冲动画（1000ms 循环）
 * 参考：design.md §动画规范 - 否决信号脉冲动画
 */
@Composable
private fun VetoPulseWarningIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "veto_pulse")

    // 脉冲缩放动画
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_scale",
    )

    // 脉冲透明度动画
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_alpha",
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 外圈脉冲效果
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha * 0.3f)
                .background(
                    color = QuantumRed.copy(alpha = 0.2f),
                    shape = CircleShape,
                ),
        )

        // 中圈脉冲效果
        Box(
            modifier = Modifier
                .size(90.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha * 0.5f)
                .background(
                    color = QuantumRed.copy(alpha = 0.3f),
                    shape = CircleShape,
                ),
        )

        // 内圈图标
        Box(
            modifier = Modifier
                .size(70.dp)
                .background(
                    color = QuantumRed,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "警告",
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * 倒计时显示
 *
 * 显示剩余的 48 小时倒计时
 * 格式：天:小时:分钟:秒
 *
 * @param remainingTime 剩余时间
 */
@Composable
private fun CountdownDisplay(remainingTime: Duration) {
    val days = remainingTime.inWholeDays
    val hours = (remainingTime - days.days).inWholeHours
    val minutes = (remainingTime - days.days - hours.hours).inWholeMinutes
    val seconds = (remainingTime - days.days - hours.hours - minutes.minutes).inWholeSeconds

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = QuantumRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 2.dp,
                color = QuantumRed,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "否决窗口倒计时",
            style = MaterialTheme.typography.labelLarge,
            color = QuantumRed,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 时间单位显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TimeUnitDisplay(value = days.toInt(), unit = "天")
            TimeUnitDisplay(value = hours.toInt(), unit = "时")
            TimeUnitDisplay(value = minutes.toInt(), unit = "分")
            TimeUnitDisplay(value = seconds.toInt(), unit = "秒")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 警告提示
        Text(
            text = if (remainingTime > 24.hours) {
                "请在倒计时结束前做出决定"
            } else {
                "⚠️ 否决窗口即将关闭"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (remainingTime > 24.hours) OnSurfaceVariantColor else QuantumRed,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 时间单位显示
 *
 * @param value 数值
 * @param unit 单位
 */
@Composable
private fun TimeUnitDisplay(value: Int, unit: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 数值
        Text(
            text = value.toString().padStart(2, '0'),
            style = MaterialTheme.typography.headlineLarge,
            color = QuantumRed,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 单位
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariantColor,
        )
    }
}

/**
 * 否决原因选择（可选）
 *
 * 允许用户选择否决原因，帮助系统了解情况
 */
@Composable
private fun VetoReasonSection() {
    var selectedReason by remember { mutableStateOf<VetoReason?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "否决原因（可选）",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariantColor,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        VetoReason.values().forEach { reason ->
            VetoReasonOption(
                reason = reason,
                isSelected = selectedReason == reason,
                onClick = { selectedReason = reason },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 否决原因选项
 *
 * @param reason 原因
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun VetoReasonOption(
    reason: VetoReason,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) {
                    QuantumBlue.copy(alpha = 0.1f)
                } else {
                    SurfaceColor
                },
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) QuantumBlue else SurfaceColor,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选择指示器
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = if (isSelected) QuantumBlue else Color.Transparent,
                    shape = CircleShape,
                )
                .border(
                    width = 2.dp,
                    color = if (isSelected) QuantumBlue else OnSurfaceVariantColor,
                    shape = CircleShape,
                ),
        )

        Spacer(modifier = Modifier.size(12.dp))

        // 原因信息
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = reason.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = OnDeepSpaceBackground,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = reason.description,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantColor,
            )
        }
    }
}

/**
 * 安全提示横幅
 */
@Composable
private fun SecurityHintBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceColor,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = "安全提示",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariantColor,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "• 如果不是您本人操作，请立即否决\n" +
                    "• 否决后，恢复请求将被永久取消\n" +
                    "• 48 小时内任何活跃设备都可以否决\n" +
                    "• 否决有助于保护您的账户安全",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantColor,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        )
    }
}
