package io.aeternum.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.SurfaceColor

/**
 * 通用列表项组件
 *
 * 提供可复用的列表项样式，支持多种配置。
 *
 * ## 设计理念
 * - **灵活性**: 支持图标、标题、副标题、尾部元素等多种组合
 * - **一致性**: 遵循 Material Design 3 列表项规范
 * - **易用性**: 提供合理的默认值和便捷的配置选项
 *
 * ## 支持的配置
 * - **头部元素**: 图标、头像、自定义 Composable
 * - **文本内容**: 标题（必需）、副标题（可选）、 trailing 文本（可选）
 * - **尾部元素**: 图标、开关、按钮、自定义 Composable
 * - **交互**: 点击、长按、开关切换等
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅处理用户交互
 * - 不包含业务逻辑，所有回调由调用者实现
 * - 保持组件纯粹性，不直接访问状态或桥接层
 *
 * @param title 标题文本
 * @param modifier 修饰符
 * @param subtitle 副标题文本（可选）
 * @param leadingIcon 头部图标（可选）
 * @param leadingAvatar 头部头像（可选）
 * @param leadingContent 头部自定义内容（可选）
 * @param trailingIcon 尾部图标（可选）
 * @param trailingText 尾部文本（可选）
 * @param trailingContent 尾部自定义内容（可选）
 * @param trailingSwitch 尾部开关（可选）
 * @param trailingAction 尾部操作按钮文本（可选）
 * @param onTrailingActionClick 尾部操作按钮点击回调（可选）
 * @param onClick 点击回调（可选）
 * @param enabled 是否启用
 * @param showDivider 是否显示分隔线
 */
@Composable
fun ListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingAvatar: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    trailingSwitch: Pair<Boolean, ((Boolean) -> Unit)?>? = null,
    trailingAction: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showDivider: Boolean = false,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick, enabled = enabled)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // 主内容
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头部元素（三选一）
            when {
                leadingContent != null -> {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        leadingContent()
                    }
                }
                leadingAvatar != null -> {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        leadingAvatar()
                    }
                }
                leadingIcon != null -> {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (enabled) QuantumBlue else QuantumBlue.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                else -> {
                    // 占位空间，保持对齐
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }

            // 文本内容
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) OnDeepSpaceBackground else OnDeepSpaceBackground.copy(alpha = 0.5f),
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantColor,
                    )
                }
            }

            // 尾部元素
            when {
                trailingContent != null -> {
                    trailingContent()
                }
                trailingSwitch != null -> {
                    var checked by remember { mutableStateOf(trailingSwitch.first) }
                    Switch(
                        checked = checked,
                        onCheckedChange = { newChecked ->
                            checked = newChecked
                            trailingSwitch.second?.invoke(newChecked)
                        },
                        enabled = enabled,
                        colors = SwitchColors(
                            checkedThumbColor = QuantumBlue,
                            checkedTrackColor = QuantumBlue.copy(alpha = 0.5f),
                            uncheckedThumbColor = OnSurfaceVariantColor,
                            uncheckedTrackColor = OnSurfaceVariantColor.copy(alpha = 0.5f),
                            checkedBorderColor = QuantumBlue,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            checkedIconColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedIconColor = MaterialTheme.colorScheme.outline,
                            disabledCheckedThumbColor = MaterialTheme.colorScheme.surface,
                            disabledUncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            disabledCheckedTrackColor = MaterialTheme.colorScheme.surface,
                            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surface,
                            disabledCheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            disabledUncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            disabledCheckedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            disabledUncheckedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        ),
                    )
                }
                trailingAction != null -> {
                    TextButton(
                        onClick = { onTrailingActionClick?.invoke() },
                        enabled = enabled,
                    ) {
                        Text(
                            text = trailingAction,
                            style = MaterialTheme.typography.labelLarge,
                            color = QuantumBlue,
                        )
                    }
                }
                trailingText != null -> {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariantColor,
                    )
                }
                trailingIcon != null -> {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = OnSurfaceVariantColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // 分隔线
        if (showDivider) {
            Spacer(modifier = Modifier.height(8.dp))
            Divider(
                color = OnSurfaceVariantColor.copy(alpha = 0.2f),
                modifier = Modifier.padding(start = 72.dp),
            )
        }
    }
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_Basic() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "基本列表项",
                subtitle = "这是一个简单的列表项",
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_WithIcon() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "带图标的列表项",
                subtitle = "左侧有一个图标",
                leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_WithTrailingIcon() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "可点击的列表项",
                leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                trailingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_more),
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_WithSwitch() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "自动密钥轮换",
                subtitle = "定期自动升级密钥纪元",
                leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_rotate),
                trailingSwitch = Pair(true) { },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_WithAction() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "生物识别设置",
                subtitle = "配置指纹和面部识别",
                leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                trailingAction = "设置",
                onTrailingActionClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_WithAvatar() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "设备管理",
                subtitle = "管理已连接的设备",
                leadingAvatar = {
                    // 模拟头像
                    Surface(
                        color = QuantumBlue,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "D",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnDeepSpaceBackground,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                },
                trailingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_more),
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_WithTrailingText() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "当前纪元",
                subtitle = "密钥加密纪元版本",
                leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                trailingText = "v2",
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_WithCustomLeading() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "安全状态",
                subtitle = "当前系统安全状态",
                leadingContent = {
                    StatusIndicator(
                        state = "安全",
                        color = QuantumBlue,
                    )
                },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_Disabled() {
    AeternumPreviewTheme {
        Surface(color = SurfaceColor) {
            ListItem(
                title = "已禁用的选项",
                subtitle = "该选项当前不可用",
                leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                enabled = false,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ListItemPreview_WithDivider() {
    AeternumPreviewTheme {
        Column {
            Surface(color = SurfaceColor) {
                ListItem(
                    title = "列表项 1",
                    subtitle = "第一项",
                    leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                    showDivider = true,
                )
            }
            Surface(color = SurfaceColor) {
                ListItem(
                    title = "列表项 2",
                    subtitle = "第二项",
                    leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                    showDivider = true,
                )
            }
            Surface(color = SurfaceColor) {
                ListItem(
                    title = "列表项 3",
                    subtitle = "第三项",
                    leadingIcon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                )
            }
        }
    }
}

/**
 * 简单的状态指示器
 *
 * 用于预览，实际使用应该导入现有的 StatusIndicator 组件
 */
@Composable
private fun StatusIndicator(
    state: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = CircleShape,
        modifier = Modifier.size(40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = color,
                shape = CircleShape,
                modifier = Modifier.size(8.dp),
            ) {}
        }
    }
}
