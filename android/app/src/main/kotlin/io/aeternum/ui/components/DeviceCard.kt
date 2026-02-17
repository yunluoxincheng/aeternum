package io.aeternum.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.CodeTextStyle
import io.aeternum.ui.theme.MachineStateColor
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnQuantumBlue
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.SurfaceColor
import io.aeternum.ui.theme.SurfaceVariantColor
import io.aeternum.ui.viewmodel.DeviceInfo

/**
 * 设备卡片组件
 *
 * 用于在设备列表中显示单个设备的信息。
 *
 * ## 设计理念
 * - **信息层次**: 设备名称 > 状态 > 纪元 > 详情
 * - **视觉区分**: 通过颜色和图标清晰区分设备状态
 * - **操作便捷**: 提供快速操作入口（查看详情、撤销等）
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示脱敏后的设备信息
 * - 设备 ID 等敏感信息以脱敏形式显示
 * - 所有操作通过 Rust Core 验证后执行
 *
 * @param device 设备信息
 * @param modifier 修饰符
 * @param onClick 点击回调
 * @param onRevoke 撤销回调（可选）
 * @param showRevokeButton 是否显示撤销按钮
 */
@Composable
fun DeviceCard(
    device: DeviceInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onRevoke: ((DeviceInfo) -> Unit)? = null,
    showRevokeButton: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (device.isThisDevice) {
            BorderStroke(2.dp, QuantumBlue)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = SurfaceColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // 顶部行：设备名称 + 徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 设备名称
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = getDeviceIcon(device),
                        contentDescription = null,
                        tint = getStateColor(device.state),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnDeepSpaceBackground,
                    )
                }

                // 徽章组
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 本机标识
                    if (device.isThisDevice) {
                        Surface(
                            color = QuantumBlue,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = "本机",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnQuantumBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 状态徽章
                    StatusBadge(
                        state = device.state,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 中部行：设备 ID（脱敏显示）
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ID: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
                Text(
                    text = device.id.take(8) + "..." + device.id.takeLast(4),
                    style = CodeTextStyle,
                    color = OnSurfaceVariantColor,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 底部行：纪元 + 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 纪元信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "纪元 ",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantColor,
                    )
                    Text(
                        text = "${device.epoch}",
                        style = CodeTextStyle,
                        color = OnSurfaceVariantColor,
                    )
                }

                // 撤销按钮（可选）
                if (showRevokeButton && onRevoke != null && !device.isThisDevice) {
                    ActionButton(
                        text = "撤销",
                        onClick = { onRevoke(device) },
                        type = ButtonType.Danger,
                        size = ButtonSize.Small,
                    )
                }
            }
        }
    }
}

/**
 * 状态徽章
 *
 * @param state 状态字符串
 */
@Composable
private fun StatusBadge(
    state: String,
) {
    val stateColor = when (state) {
        "Idle" -> MachineStateColor.Idle
        "Decrypting" -> MachineStateColor.Decrypting
        "Rekeying" -> MachineStateColor.Rekeying
        "Degraded" -> MachineStateColor.Degraded
        "Revoked" -> MachineStateColor.Revoked
        "active" -> MachineStateColor.Idle
        else -> MachineStateColor.Idle
    }

    val surfaceColor = when (state) {
        "Idle", "active" -> QuantumGreen
        "Decrypting" -> QuantumBlue
        "Rekeying" -> MachineStateColor.Rekeying.color
        "Degraded" -> MachineStateColor.Degraded.color
        "Revoked" -> MachineStateColor.Revoked.color
        else -> QuantumBlue
    }

    Surface(
        color = surfaceColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = surfaceColor,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.size(8.dp),
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stateColor.description,
                style = MaterialTheme.typography.labelSmall,
                color = surfaceColor,
            )
        }
    }
}

/**
 * 根据设备状态获取图标
 *
 * @param device 设备信息
 * @return 对应的图标
 */
@Composable
private fun getDeviceIcon(device: DeviceInfo): ImageVector {
    // TODO: 添加实际的设备图标资源
    return when (device.state) {
        "Idle", "active" -> ImageVector.vectorResource(android.R.drawable.ic_menu_info_details)
        "Decrypting" -> ImageVector.vectorResource(android.R.drawable.ic_menu_info_details)
        "Rekeying" -> ImageVector.vectorResource(android.R.drawable.ic_menu_rotate)
        "Degraded", "degraded" -> ImageVector.vectorResource(android.R.drawable.ic_menu_delete)
        "Revoked", "revoked" -> ImageVector.vectorResource(android.R.drawable.ic_menu_close_clear_cancel)
        else -> ImageVector.vectorResource(android.R.drawable.ic_menu_info_details)
    }
}

/**
 * 根据状态获取颜色
 *
 * @param state 状态字符串
 * @return 对应的颜色
 */
private fun getStateColor(state: String): androidx.compose.ui.graphics.Color {
    return when (state) {
        "Idle", "active" -> QuantumGreen
        "Decrypting" -> QuantumBlue
        "Rekeying" -> MachineStateColor.Rekeying.color
        "Degraded", "degraded" -> MachineStateColor.Degraded.color
        "Revoked", "revoked" -> MachineStateColor.Revoked.color
        else -> QuantumBlue
    }
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeviceCardPreview_ActiveDevice() {
    AeternumPreviewTheme {
        DeviceCard(
            device = DeviceInfo(
                id = "a1b2c3d4e5f6g7h8",
                name = "Google Pixel 8 Pro",
                epoch = 1u,
                state = "Idle",
                isThisDevice = true,
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeviceCardPreview_OtherDevice() {
    AeternumPreviewTheme {
        DeviceCard(
            device = DeviceInfo(
                id = "i9j0k1l2m3n4o5p6",
                name = "MacBook Pro M3",
                epoch = 1u,
                state = "Decrypting",
                isThisDevice = false,
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeviceCardPreview_RekeyingDevice() {
    AeternumPreviewTheme {
        DeviceCard(
            device = DeviceInfo(
                id = "q7r8s9t0u1v2w3x4",
                name = "iPad Pro",
                epoch = 2u,
                state = "Rekeying",
                isThisDevice = false,
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeviceCardPreview_DegradedDevice() {
    AeternumPreviewTheme {
        DeviceCard(
            device = DeviceInfo(
                id = "y5z6a7b8c9d0e1f2",
                name = "Unknown Device",
                epoch = 1u,
                state = "Degraded",
                isThisDevice = false,
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeviceCardPreview_WithRevokeButton() {
    AeternumPreviewTheme {
        DeviceCard(
            device = DeviceInfo(
                id = "g3h4j5k6l7m8n9o0",
                name = "Old Phone",
                epoch = 1u,
                state = "Idle",
                isThisDevice = false,
            ),
            showRevokeButton = true,
            onRevoke = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeviceCardPreview_RevokedDevice() {
    AeternumPreviewTheme {
        DeviceCard(
            device = DeviceInfo(
                id = "p1q2r3s4t5u6v7w8",
                name = "Lost Device",
                epoch = 1u,
                state = "Revoked",
                isThisDevice = false,
            ),
        )
    }
}
