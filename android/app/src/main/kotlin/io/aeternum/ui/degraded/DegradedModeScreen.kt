package io.aeternum.ui.degraded

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.components.ActionButton
import io.aeternum.ui.components.ButtonType
import io.aeternum.ui.components.WarningBanner
import io.aeternum.ui.components.WarningBannerType
import io.aeternum.ui.state.DegradedReason
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.MachineStateColor
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.QuantumRed

/**
 * Aeternum 降级模式屏幕
 *
 * 当设备完整性验证失败时显示，表示应用已进入只读安全模式。
 *
 * ## 设计理念
 * - **安全优先**: 清晰传达安全风险，限制用户操作
 * - **可恢复**: 提供重新验证路径，引导用户解决安全问题
 * - **透明化**: 明确告知用户哪些功能被限制
 *
 * ## 状态机映射
 * - 对应状态：`AeternumUiState.Degraded`
 * - 进入条件：Play Integrity verdict ≠ STRONG
 * - 退出条件：重新获得 STRONG verdict
 *
 * ## 功能限制
 * 在降级模式下，以下操作被禁用：
 * - ❌ 解密完整数据
 * - ❌ 导出任何数据
 * - ❌ 发起恢复流程
 * - ✅ 查看脱敏字段（只读）
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示警告和限制说明，不执行敏感操作
 * - INVARIANT: 所有重新验证通过 AndroidSecurityManager 执行
 * - INVARIANT: 不持有任何明文密钥或敏感数据
 *
 * @param reason 降级原因
 * @param modifier 修饰符
 * @param onReverify 重新验证回调
 * @param onLearnMore 了解更多回调（可选）
 */
@Composable
fun DegradedModeScreen(
    reason: DegradedReason,
    modifier: Modifier = Modifier,
    onReverify: () -> Unit,
    onLearnMore: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 警告图标（脉冲动画）
            PulsingWarningIcon()

            Spacer(modifier = Modifier.height(32.dp))

            // 标题
            Text(
                text = "安全模式已激活",
                style = MaterialTheme.typography.headlineLarge,
                color = MachineStateColor.Degraded.color,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 错误描述
            Text(
                text = getReasonDescription(reason),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 功能限制警告横幅
            WarningBanner(
                message = "降级模式下，解密、导出和恢复功能已被禁用。您只能查看脱敏的只读数据。",
                type = WarningBannerType.Warning,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 重新验证按钮
            ActionButton(
                text = "重新验证",
                onClick = onReverify,
                type = ButtonType.Primary,
                modifier = Modifier.fillMaxWidth(),
            )

            // 了解更多链接
            if (onLearnMore != null) {
                Spacer(modifier = Modifier.height(16.dp))

                ActionButton(
                    text = "了解详情",
                    onClick = onLearnMore,
                    type = ButtonType.Text,
                )
            }
        }
    }
}

/**
 * 脉冲警告图标
 *
 * 使用无限循环的透明度动画创建脉冲效果
 */
@Composable
private fun PulsingWarningIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "warning_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Surface(
        modifier = Modifier.size(120.dp),
        color = QuantumRed.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "警告",
                tint = QuantumRed,
                modifier = Modifier
                    .size(64.dp)
                    .alpha(alpha),
            )
        }
    }
}

/**
 * 根据降级原因获取用户友好的描述
 *
 * @param reason 降级原因
 * @return 用户友好的描述文本
 */
private fun getReasonDescription(reason: DegradedReason): String {
    return when (reason) {
        is DegradedReason.INTEGRITY_CHECK_FAILED ->
            "设备完整性验证失败。设备可能已被 root 或安装了未经授权的应用。"

        is DegradedReason.NETWORK_UNAVAILABLE ->
            "网络连接不可用。无法验证设备完整性，已进入只读安全模式。"

        is DegradedReason.EPOCH_CONFLICT ->
            "检测到纪元冲突。您的设备状态可能已过期，请重新同步以避免数据丢失。"

        is DegradedReason.STORAGE_ERROR ->
            "存储访问失败。无法安全访问加密数据，已进入只读模式。"

        is DegradedReason.BIOMETRIC_UNAVAILABLE ->
            "生物识别不可用。无法安全验证用户身份，已进入只读模式。"

        is DegradedReason.OTHER ->
            if (reason.message.isNotEmpty()) {
                reason.message
            } else {
                "应用已进入降级模式。请检查设备状态并重新验证。"
            }
    }
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DegradedModeScreenPreview_IntegrityFailed() {
    AeternumPreviewTheme {
        DegradedModeScreen(
            reason = DegradedReason.INTEGRITY_CHECK_FAILED,
            onReverify = {},
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DegradedModeScreenPreview_NetworkUnavailable() {
    AeternumPreviewTheme {
        DegradedModeScreen(
            reason = DegradedReason.NETWORK_UNAVAILABLE,
            onReverify = {},
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DegradedModeScreenPreview_EpochConflict() {
    AeternumPreviewTheme {
        DegradedModeScreen(
            reason = DegradedReason.EPOCH_CONFLICT,
            onReverify = {},
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DegradedModeScreenPreview_StorageError() {
    AeternumPreviewTheme {
        DegradedModeScreen(
            reason = DegradedReason.STORAGE_ERROR,
            onReverify = {},
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DegradedModeScreenPreview_BiometricUnavailable() {
    AeternumPreviewTheme {
        DegradedModeScreen(
            reason = DegradedReason.BIOMETRIC_UNAVAILABLE,
            onReverify = {},
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DegradedModeScreenPreview_Other() {
    AeternumPreviewTheme {
        DegradedModeScreen(
            reason = DegradedReason.OTHER("自定义错误消息"),
            onReverify = {},
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DegradedModeScreenPreview_NoLearnMore() {
    AeternumPreviewTheme {
        DegradedModeScreen(
            reason = DegradedReason.INTEGRITY_CHECK_FAILED,
            onReverify = {},
            onLearnMore = null,
        )
    }
}
