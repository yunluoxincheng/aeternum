package io.aeternum.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.aeternum.ui.components.QuantumAnimation
import io.aeternum.ui.components.QuantumAnimationType
import io.aeternum.ui.state.ActiveSubState
import io.aeternum.ui.state.AeternumUiState
import io.aeternum.ui.state.RekeyingStage
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.MachineStateColor
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.QuantumYellow
import io.aeternum.ui.theme.SurfaceColor
import io.aeternum.ui.viewmodel.AeternumViewModel

/**
 * Aeternum 密钥轮换屏幕（Rekeying 状态）
 *
 * ## 设计理念
 * - **信息层次**: 进度 > 阶段 > 纪元信息
 * - **视觉反馈**: 旋转量子动画传达"轮换中"状态
 * - **进度透明**: 清晰显示当前阶段和进度百分比
 * - **安全感**: 新旧纪元对比，明确展示升级过程
 *
 * ## Rekeying 状态特性
 * - 正在执行 PQRR 密钥轮换协议
 * - 显示当前轮换阶段（PREPARING → ENCRYPTING → BROADCASTING → COMMITTING → FINALIZING）
 * - 显示从当前纪元到目标纪元的升级
 * - 进度条实时更新
 * - 用户不可中断（关键操作）
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示状态信息，不参与密钥轮换逻辑
 * - INVARIANT: 轮换由 Rust Core 驱动，Kotlin 仅响应状态更新
 * - INVARIANT: Rekeying 状态下禁止用户操作（防止中断关键流程）
 * - 不暴露密钥材料或敏感参数
 * - 纪元号可显示（非敏感信息）
 *
 * ## 状态机
 * ```
 * Active (Idle) → Active (Rekeying) → Active (Idle)
 *                 ↑           ↓
 *              显示本屏幕   禁止操作
 * ```
 *
 * @param viewModel Aeternum ViewModel
 * @param modifier 修饰符
 */
@Composable
fun RekeyingScreen(
    viewModel: AeternumViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    // 仅在 Rekeying 状态显示
    when (val state = uiState) {
        is AeternumUiState.Active -> {
            when (val subState = state.subState) {
                is ActiveSubState.Rekeying -> {
                    RekeyingContent(
                        currentEpoch = subState.currentEpoch,
                        targetEpoch = subState.targetEpoch,
                        progress = subState.progress,
                        stage = subState.stage,
                        modifier = modifier,
                    )
                }
                else -> {
                    // 其他状态不在此屏幕显示
                    InvalidStateContent(subState)
                }
            }
        }
        else -> {
            // 非 Active 状态不在此屏幕显示
            InvalidUiStateContent(state)
        }
    }
}

/**
 * 轮换主内容
 *
 * @param currentEpoch 当前纪元
 * @param targetEpoch 目标纪元
 * @param progress 进度 (0.0 - 1.0)
 * @param stage 当前阶段
 * @param modifier 修饰符
 */
@Composable
private fun RekeyingContent(
    currentEpoch: UInt,
    targetEpoch: UInt,
    progress: Float,
    stage: RekeyingStage,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = DeepSpaceBackground,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 旋转量子动画
            QuantumAnimation(
                modifier = Modifier.padding(bottom = 32.dp),
                type = QuantumAnimationType.Rotating(particleCount = 12),
                size = 120.dp,
                color = QuantumYellow,
                speed = 1.5f,
            )

            // 标题
            Text(
                text = "密钥轮换中",
                style = MaterialTheme.typography.headlineMedium,
                color = OnDeepSpaceBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 副标题
            Text(
                text = "正在执行后量子安全协议",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariantColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 进度卡片
            RekeyingProgressCard(
                currentEpoch = currentEpoch,
                targetEpoch = targetEpoch,
                progress = progress,
                stage = stage,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 阶段详情
            RekeyingStageDetails(
                stage = stage,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 警告提示
            RekeyingWarningBanner()
        }
    }
}

/**
 * 轮换进度卡片
 *
 * 显示纪元升级信息和进度条
 *
 * @param currentEpoch 当前纪元
 * @param targetEpoch 目标纪元
 * @param progress 进度 (0.0 - 1.0)
 * @param stage 当前阶段
 */
@Composable
private fun RekeyingProgressCard(
    currentEpoch: UInt,
    targetEpoch: UInt,
    progress: Float,
    stage: RekeyingStage,
) {
    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // 纪元对比标题
            Text(
                text = "纪元升级",
                style = MaterialTheme.typography.titleMedium,
                color = OnDeepSpaceBackground,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 新旧纪元对比
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 当前纪元
                EpochColumn(
                    label = "当前纪元",
                    epoch = currentEpoch,
                    color = OnSurfaceVariantColor,
                    modifier = Modifier.weight(1f),
                )

                // 箭头
                Text(
                    text = "→",
                    style = MaterialTheme.typography.headlineMedium,
                    color = QuantumYellow,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                // 目标纪元
                EpochColumn(
                    label = "新纪元",
                    epoch = targetEpoch,
                    color = QuantumGreen,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 进度百分比
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = QuantumYellow,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = QuantumYellow,
                trackColor = SurfaceVariantColor,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 当前阶段
            Text(
                text = getStageDisplayName(stage),
                style = MaterialTheme.typography.bodyMedium,
                color = OnDeepSpaceBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 纪元列
 *
 * @param label 标签
 * @param epoch 纪元号
 * @param color 颜色
 * @param modifier 修饰符
 */
@Composable
private fun EpochColumn(
    label: String,
    epoch: UInt,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariantColor,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            color = color.copy(alpha = 0.15f),
            shape = CircleShape,
        ) {
            Text(
                text = "v$epoch",
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 轮换阶段详情
 *
 * 显示当前阶段的详细说明
 *
 * @param stage 当前阶段
 */
@Composable
private fun RekeyingStageDetails(
    stage: RekeyingStage,
) {
    val (title, description) = when (stage) {
        RekeyingStage.PREPARING -> "准备中" to "生成新密钥材料"
        RekeyingStage.ENCRYPTING -> "加密中" to "使用新密钥重新加密数据"
        RekeyingStage.BROADCASTING -> "广播中" to "将新 Header 同步到其他设备"
        RekeyingStage.COMMITTING -> "提交中" to "原子提交新纪元"
        RekeyingStage.FINALIZING -> "完成中" to "清理旧密钥材料"
    }

    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 阶段指示器
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(QuantumBlue.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = getStageEmoji(stage),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            // 阶段信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = OnDeepSpaceBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }
        }
    }
}

/**
 * 轮换警告横幅
 *
 * 提醒用户不要中断轮换流程
 */
@Composable
private fun RekeyingWarningBanner() {
    Surface(
        color = QuantumYellow.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.titleMedium,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "请勿关闭应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDeepSpaceBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "密钥轮换期间请保持应用运行",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }
        }
    }
}

/**
 * 无效状态内容
 *
 * @param subState 活跃子状态
 */
@Composable
private fun InvalidStateContent(
    subState: ActiveSubState,
) {
    Surface(
        color = DeepSpaceBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "状态错误",
                style = MaterialTheme.typography.headlineMedium,
                color = OnDeepSpaceBackground,
            )
            Text(
                text = "当前状态: ${when (subState) {
                    is ActiveSubState.Idle -> "空闲"
                    is ActiveSubState.Decrypting -> "已解锁"
                    is ActiveSubState.Rekeying -> "密钥轮换中"
                }}",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantColor,
            )
        }
    }
}

/**
 * 无效 UI 状态内容
 *
 * @param uiState UI 状态
 */
@Composable
private fun InvalidUiStateContent(
    uiState: AeternumUiState,
) {
    Surface(
        color = DeepSpaceBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "无效的 UI 状态",
                style = MaterialTheme.typography.headlineMedium,
                color = OnDeepSpaceBackground,
            )
        }
    }
}

// ============================================================================
// 辅助函数
// ============================================================================

/**
 * 获取阶段显示名称
 */
private fun getStageDisplayName(stage: RekeyingStage): String {
    return when (stage) {
        RekeyingStage.PREPARING -> "准备阶段"
        RekeyingStage.ENCRYPTING -> "加密阶段"
        RekeyingStage.BROADCASTING -> "广播阶段"
        RekeyingStage.COMMITTING -> "提交阶段"
        RekeyingStage.FINALIZING -> "完成阶段"
    }
}

/**
 * 获取阶段 Emoji
 */
private fun getStageEmoji(stage: RekeyingStage): String {
    return when (stage) {
        RekeyingStage.PREPARING -> "🔧"
        RekeyingStage.ENCRYPTING -> "🔒"
        RekeyingStage.BROADCASTING -> "📡"
        RekeyingStage.COMMITTING -> "✅"
        RekeyingStage.FINALIZING -> "🎉"
    }
}

/**
 * 表面变体色（用于进度条轨道）
 */
private val SurfaceVariantColor = androidx.compose.ui.graphics.Color(0xFF2C2C2C)

// ============================================================================
// 预览
// ============================================================================

/**
 * RekeyingScreen 预览 - 准备阶段
 */
@Composable
private fun RekeyingScreenPreview_Preparing() {
    val mockState = AeternumUiState.Active(
        ActiveSubState.Rekeying(
            currentEpoch = 5u,
            targetEpoch = 6u,
            progress = 0.2f,
            stage = RekeyingStage.PREPARING,
        ),
    )

    // 预览需要在 AeternumPreviewTheme 中进行
    // 实际预览应在运行时或使用 Mock 数据
}

/**
 * RekeyingScreen 预览 - 加密阶段
 */
@Composable
private fun RekeyingScreenPreview_Encrypting() {
    val mockState = AeternumUiState.Active(
        ActiveSubState.Rekeying(
            currentEpoch = 5u,
            targetEpoch = 6u,
            progress = 0.5f,
            stage = RekeyingStage.ENCRYPTING,
        ),
    )
}

/**
 * RekeyingScreen 预览 - 完成阶段
 */
@Composable
private fun RekeyingScreenPreview_Finalizing() {
    val mockState = AeternumUiState.Active(
        ActiveSubState.Rekeying(
            currentEpoch = 5u,
            targetEpoch = 6u,
            progress = 0.95f,
            stage = RekeyingStage.FINALIZING,
        ),
    )
}
