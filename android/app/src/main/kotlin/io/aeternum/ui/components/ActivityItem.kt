package io.aeternum.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
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

/**
 * 活动列表项组件
 *
 * 用于显示最近的系统活动，如密钥轮换、设备撤销、否决操作等。
 *
 * ## 设计理念
 * - **时间线式**: 左侧图标 + 时间线连线，形成清晰的时间线
 * - **信息层次**: 活动类型 > 时间 > 详情
 * - **状态传达**: 通过颜色和图标传达活动的重要性和结果
 *
 * ## 支持的活动类型
 * - **密钥轮换** (KeyRotation): 纪元升级，黄色警告
 * - **设备撤销** (DeviceRevoked): 设备被撤销，红色危险
 * - **设备添加** (DeviceAdded): 新设备加入，绿色成功
 * - **否决操作** (Veto): 否决恢复请求，红色警告
 * - **恢复请求** (RecoveryRequest): 发起恢复，黄色关注
 * - **生物识别** (BiometricAuth): 生物识别认证，蓝色正常
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示脱敏后的活动信息
 * - 不记录敏感操作的具体内容
 * - 所有时间戳使用相对时间显示
 *
 * @param activity 活动信息
 * @param modifier 修饰符
 * @param onClick 点击回调（可选）
 */
@Composable
fun ActivityItem(
    activity: ActivityInfo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧：活动图标
        ActivityIcon(
            type = activity.type,
            modifier = Modifier.padding(end = 16.dp),
        )

        // 右侧：活动信息
        Column(
            modifier = Modifier.weight(1f),
        ) {
            // 活动标题
            Text(
                text = activity.title,
                style = MaterialTheme.typography.bodyLarge,
                color = OnDeepSpaceBackground,
            )

            // 活动详情和时间
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 详情（如果有）
                if (activity.description != null) {
                    Text(
                        text = activity.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantColor,
                        modifier = Modifier.weight(1f),
                    )
                }

                // 时间
                Text(
                    text = activity.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }
        }
    }
}

/**
 * 活动图标
 *
 * @param type 活动类型
 * @param modifier 修饰符
 */
@Composable
private fun ActivityIcon(
    type: ActivityType,
    modifier: Modifier = Modifier,
) {
    val (icon, color, backgroundColor) = when (type) {
        ActivityType.KeyRotation -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_rotate),
            QuantumYellow,
            QuantumYellow.copy(alpha = 0.15f),
        )
        ActivityType.DeviceRevoked -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_close_clear_cancel),
            QuantumRed,
            QuantumRed.copy(alpha = 0.15f),
        )
        ActivityType.DeviceAdded -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_add),
            QuantumGreen,
            QuantumGreen.copy(alpha = 0.15f),
        )
        ActivityType.Veto -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_call),
            QuantumRed,
            QuantumRed.copy(alpha = 0.15f),
        )
        ActivityType.RecoveryRequest -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_view),
            QuantumYellow,
            QuantumYellow.copy(alpha = 0.15f),
        )
        ActivityType.BiometricAuth -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
            QuantumBlue,
            QuantumBlue.copy(alpha = 0.15f),
        )
        is ActivityType.Custom -> Triple(
            ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
            QuantumBlue,
            QuantumBlue.copy(alpha = 0.15f),
        )
    }

    Surface(
        color = backgroundColor,
        shape = CircleShape,
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.description,
            tint = color,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/**
 * 活动信息
 *
 * @property type 活动类型
 * @property title 活动标题
 * @property description 活动描述（可选）
 * @property timestamp 时间戳（相对时间）
 */
data class ActivityInfo(
    val type: ActivityType,
    val title: String,
    val description: String? = null,
    val timestamp: String,
)

/**
 * 活动类型
 *
 * @property description 类型描述
 */
sealed class ActivityType(val description: String) {
    /** 密钥轮换 */
    data object KeyRotation : ActivityType("密钥轮换")

    /** 设备撤销 */
    data object DeviceRevoked : ActivityType("设备撤销")

    /** 设备添加 */
    data object DeviceAdded : ActivityType("设备添加")

    /** 否决操作 */
    data object Veto : ActivityType("否决操作")

    /** 恢复请求 */
    data object RecoveryRequest : ActivityType("恢复请求")

    /** 生物识别认证 */
    data object BiometricAuth : ActivityType("生物识别")

    /** 自定义活动 */
    data class Custom(val customDescription: String) : ActivityType(customDescription)
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActivityItemPreview_KeyRotation() {
    AeternumPreviewTheme {
        ActivityItem(
            activity = ActivityInfo(
                type = ActivityType.KeyRotation,
                title = "密钥已轮换",
                description = "纪元升级至 v2",
                timestamp = "2 分钟前",
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActivityItemPreview_DeviceRevoked() {
    AeternumPreviewTheme {
        ActivityItem(
            activity = ActivityInfo(
                type = ActivityType.DeviceRevoked,
                title = "设备已撤销",
                description = "iPad Pro",
                timestamp = "1 小时前",
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActivityItemPreview_DeviceAdded() {
    AeternumPreviewTheme {
        ActivityItem(
            activity = ActivityInfo(
                type = ActivityType.DeviceAdded,
                title = "新设备已添加",
                description = "MacBook Pro M3",
                timestamp = "昨天",
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActivityItemPreview_Veto() {
    AeternumPreviewTheme {
        ActivityItem(
            activity = ActivityInfo(
                type = ActivityType.Veto,
                title = "否决了恢复请求",
                description = "来自未知设备",
                timestamp = "2 天前",
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActivityItemPreview_RecoveryRequest() {
    AeternumPreviewTheme {
        ActivityItem(
            activity = ActivityInfo(
                type = ActivityType.RecoveryRequest,
                title = "发起了恢复请求",
                timestamp = "3 天前",
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActivityItemPreview_BiometricAuth() {
    AeternumPreviewTheme {
        ActivityItem(
            activity = ActivityInfo(
                type = ActivityType.BiometricAuth,
                title = "生物识别认证成功",
                timestamp = "刚刚",
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActivityItemPreview_WithoutDescription() {
    AeternumPreviewTheme {
        ActivityItem(
            activity = ActivityInfo(
                type = ActivityType.KeyRotation,
                title = "自动密钥轮换",
                timestamp = "1 周前",
            ),
        )
    }
}
