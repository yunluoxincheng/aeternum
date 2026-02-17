package io.aeternum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.MachineStateColor
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.QuantumYellow
import io.aeternum.ui.theme.SurfaceColor

/**
 * 状态卡片组件
 *
 * 用于显示设备当前的安全状态，包括状态指示器、纪元信息和设备统计。
 *
 * ## 设计理念
 * - **视觉层次**: 状态图标 > 状态文字 > 详细信息
 * - **颜色编码**: 绿色(安全) > 蓝色(正常) > 黄色(警告) > 红色(危险)
 * - **信息密度**: 紧凑但清晰，一屏展示核心信息
 *
 * ## 支持的状态
 * - **Secure (安全)**: 所有设备正常，无警告
 * - **Warning (警告)**: 有设备降级或即将轮换
 * - **Danger (危险)**: 有设备撤销或纪元冲突
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示脱敏后的状态信息
 * - 不暴露设备 ID 或敏感标识符
 * - 纪元号可显示（非敏感信息）
 *
 * @param status 状态信息
 * @param epoch 当前纪元
 * @param deviceCount 设备数量
 * @param modifier 修饰符
 */
@Composable
fun StatusCard(
    status: SecurityStatus,
    epoch: UInt,
    deviceCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：状态指示器
            StatusIcon(
                status = status,
                modifier = Modifier.padding(end = 16.dp),
            )

            // 右侧：状态信息
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // 状态文字和纪元徽章
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = OnDeepSpaceBackground,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 纪元徽章
                    EpochBadge(
                        epoch = epoch,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 设备统计
                Text(
                    text = "$deviceCount 台设备已连接",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariantColor,
                )
            }
        }
    }
}

/**
 * 状态图标
 *
 * @param status 安全状态
 * @param modifier 修饰符
 */
@Composable
private fun StatusIcon(
    status: SecurityStatus,
    modifier: Modifier = Modifier,
) {
    val (icon, backgroundColor, iconColor) = when (status) {
        is SecurityStatus.Secure -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
            QuantumGreen.copy(alpha = 0.15f),
            QuantumGreen,
        )
        is SecurityStatus.Warning -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_dialog_alert),
            QuantumYellow.copy(alpha = 0.15f),
            QuantumYellow,
        )
        is SecurityStatus.Danger -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_dialog_alert),
            QuantumRed.copy(alpha = 0.15f),
            QuantumRed,
        )
        is SecurityStatus.Custom -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
            QuantumBlue.copy(alpha = 0.15f),
            QuantumBlue,
        )
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = status.description,
            tint = iconColor,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * 纪元徽章
 *
 * @param epoch 纪元号
 */
@Composable
private fun EpochBadge(
    epoch: UInt,
) {
    Surface(
        color = QuantumBlue.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = "Epoch $epoch",
            style = MaterialTheme.typography.labelMedium,
            color = QuantumBlue,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 安全状态
 *
 * @property displayName 显示名称
 * @property description 状态描述
 */
sealed class SecurityStatus(val displayName: String, val description: String) {
    /**
     * 安全状态
     *
     * 所有设备正常，无警告
     */
    data object Secure : SecurityStatus(
        displayName = "安全",
        description = "所有设备运行正常",
    )

    /**
     * 警告状态
     *
     * 有设备降级或即将需要密钥轮换
     */
    data object Warning : SecurityStatus(
        displayName = "警告",
        description = "需要注意设备状态",
    )

    /**
     * 危险状态
     *
     * 有设备撤销或检测到异常
     */
    data object Danger : SecurityStatus(
        displayName = "危险",
        description = "检测到安全风险",
    )

    /**
     * 自定义状态
     */
    data class Custom(
        val customDisplayName: String,
        val customDescription: String,
    ) : SecurityStatus(customDisplayName, customDescription)
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StatusCardPreview_Secure() {
    AeternumPreviewTheme {
        StatusCard(
            status = SecurityStatus.Secure,
            epoch = 5u,
            deviceCount = 2,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StatusCardPreview_Warning() {
    AeternumPreviewTheme {
        StatusCard(
            status = SecurityStatus.Warning,
            epoch = 5u,
            deviceCount = 3,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StatusCardPreview_Danger() {
    AeternumPreviewTheme {
        StatusCard(
            status = SecurityStatus.Danger,
            epoch = 5u,
            deviceCount = 1,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StatusCardPreview_SingleDevice() {
    AeternumPreviewTheme {
        StatusCard(
            status = SecurityStatus.Secure,
            epoch = 1u,
            deviceCount = 1,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StatusCardPreview_MultipleDevices() {
    AeternumPreviewTheme {
        StatusCard(
            status = SecurityStatus.Secure,
            epoch = 10u,
            deviceCount = 5,
        )
    }
}
