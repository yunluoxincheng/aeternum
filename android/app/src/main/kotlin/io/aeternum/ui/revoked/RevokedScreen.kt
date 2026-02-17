package io.aeternum.ui.revoked

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.components.ActionButton
import io.aeternum.ui.components.ButtonType
import io.aeternum.ui.state.RevokedReason
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.MachineStateColor

/**
 * Aeternum 撤销状态屏幕
 *
 * 设备已被撤销的终态提示，表示所有密钥和数据已被清除。
 *
 * ## 设计理念
 * - **终态清晰**: 明确传达这是不可逆的终态
 * - **透明化**: 清晰告知用户发生了什么
 * - **引导恢复**: 提供重新注册的路径
 *
 * ## 状态机映射
 * - 对应状态：`AeternumUiState.Revoked`
 * - 终态：不可逆，无法回到其他状态
 * - 进入条件：
 *   - PQRR 撤销
 *   - Root Rotation 吊销
 *   - 本地完整性锁定
 *
 * ## 执行操作
 * 设备撤销时会执行：
 * - delete StrongBox key
 * - wipe SQLCipher
 * - wipe cache
 * - zeroize memory
 *
 * ## 架构约束
 * - INVARIANT: 终态屏幕，不允许返回操作
 * - INVARIANT: UI 层仅显示状态信息
 * - INVARIANT: 数据清除由 AndroidSecurityManager 和 Rust Core 执行
 * - INVARIANT: 不持有任何明文密钥或敏感数据
 *
 * ## 重新注册
 * 如需重新使用 Aeternum，用户必须：
 * 1. 在其他已信任设备上操作
 * 2. 重新扫描注册 QR 码
 * 3. 完成设备注册流程
 *
 * @param reason 撤销原因
 * @param modifier 修饰符
 * @param onLearnMore 了解原因回调（可选）
 */
@Composable
fun RevokedScreen(
    reason: RevokedReason,
    modifier: Modifier = Modifier,
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
            // 撤销图标（静态显示）
            RevokedIcon()

            Spacer(modifier = Modifier.height(32.dp))

            // 标题
            Text(
                text = "此设备已被撤销",
                style = MaterialTheme.typography.headlineLarge,
                color = MachineStateColor.Revoked.color,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 主要消息
            Text(
                text = "所有密钥和数据已从设备上清除",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 撤销原因说明
            RevokedReasonCard(reason)

            Spacer(modifier = Modifier.height(32.dp))

            // 重新注册说明
            Text(
                text = buildReRegistrationMessage(reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 了解原因链接
            if (onLearnMore != null) {
                ActionButton(
                    text = "了解原因",
                    onClick = onLearnMore,
                    type = ButtonType.Text,
                )
            }
        }
    }
}

/**
 * 撤销图标
 *
 * 使用静态图标表示终态，不添加动画以强调状态的永久性
 */
@Composable
private fun RevokedIcon() {
    Surface(
        modifier = Modifier.size(120.dp),
        color = MachineStateColor.Revoked.color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = "撤销",
                tint = MachineStateColor.Revoked.color,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

/**
 * 撤销原因卡片
 *
 * 显示详细的撤销原因信息
 */
@Composable
private fun RevokedReasonCard(reason: RevokedReason) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            // 原因标签
            Text(
                text = "撤销原因",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 原因描述
            Text(
                text = getRevokedReasonDescription(reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 根据撤销原因获取用户友好的描述
 *
 * @param reason 撤销原因
 * @return 用户友好的描述文本
 */
private fun getRevokedReasonDescription(reason: RevokedReason): String {
    return when (reason) {
        is RevokedReason.REVOKED_BY_ANOTHER_DEVICE -> {
            if (reason.deviceId != null) {
                "被设备「${reason.deviceId}」撤销"
            } else {
                "被您信任的其他设备撤销"
            }
        }

        is RevokedReason.EPOCH_ROLLBACK_DETECTED ->
            "检测到纪元回滚攻击。为保护您的数据安全，设备已被自动撤销。"

        is RevokedReason.VETO_TIMEOUT ->
            "否决窗口超时。恢复流程已自动完成，设备已退出信任网络。"

        is RevokedReason.KEY_COMPROMISED ->
            "密钥可能已泄露。为保护您的数据安全，设备已被立即撤销。"

        is RevokedReason.USER_INITIATED ->
            "您主动撤销了此设备。"

        is RevokedReason.OTHER ->
            if (reason.message.isNotEmpty()) {
                reason.message
            } else {
                "未知原因"
            }
    }
}

/**
 * 构建重新注册消息
 *
 * @param reason 撤销原因
 * @return 重新注册引导消息
 */
private fun buildReRegistrationMessage(reason: RevokedReason): String {
    return when (reason) {
        is RevokedReason.REVOKED_BY_ANOTHER_DEVICE ->
            "如需重新使用，请前往其他已信任设备，在设备管理中重新注册此设备。"

        is RevokedReason.EPOCH_ROLLBACK_DETECTED,
        is RevokedReason.KEY_COMPROMISED ->
            "出于安全考虑，此设备无法直接重新注册。请联系支持团队获取帮助。"

        is RevokedReason.VETO_TIMEOUT ->
            "如需重新使用，请在其他已信任设备上重新注册此设备。"

        is RevokedReason.USER_INITIATED ->
            "如需重新使用，请在其他已信任设备上重新注册此设备。"

        is RevokedReason.OTHER ->
            "如需重新使用，请使用助记词在其他设备上重新注册。"
    }
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun RevokedScreenPreview_RevokedByAnotherDevice() {
    AeternumPreviewTheme {
        RevokedScreen(
            reason = RevokedReason.REVOKED_BY_ANOTHER_DEVICE("Pixel 9 Pro"),
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun RevokedScreenPreview_EpochRollback() {
    AeternumPreviewTheme {
        RevokedScreen(
            reason = RevokedReason.EPOCH_ROLLBACK_DETECTED,
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun RevokedScreenPreview_VetoTimeout() {
    AeternumPreviewTheme {
        RevokedScreen(
            reason = RevokedReason.VETO_TIMEOUT,
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun RevokedScreenPreview_KeyCompromised() {
    AeternumPreviewTheme {
        RevokedScreen(
            reason = RevokedReason.KEY_COMPROMISED,
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun RevokedScreenPreview_UserInitiated() {
    AeternumPreviewTheme {
        RevokedScreen(
            reason = RevokedReason.USER_INITIATED,
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun RevokedScreenPreview_Other() {
    AeternumPreviewTheme {
        RevokedScreen(
            reason = RevokedReason.OTHER("系统异常导致撤销"),
            onLearnMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun RevokedScreenPreview_NoLearnMore() {
    AeternumPreviewTheme {
        RevokedScreen(
            reason = RevokedReason.REVOKED_BY_ANOTHER_DEVICE(),
            onLearnMore = null,
        )
    }
}
