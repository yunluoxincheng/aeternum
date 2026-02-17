package io.aeternum.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.OnQuantumBlue
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.OnQuantumYellow
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.QuantumYellow
import io.aeternum.ui.theme.WarningBannerShape
import io.aeternum.ui.theme.WarningBannerTextStyle

/**
 * Aeternum 警告横幅组件
 *
 * 用于显示重要的警告、错误或信息提示，确保用户不会错过关键信息。
 *
 * ## 设计理念
 * - **视觉醒目**: 使用颜色和图标快速传达消息级别
 * - **操作引导**: 可提供操作按钮引导用户采取行动
 * - **可关闭**: 允许用户关闭非关键提示，提升体验
 *
 * ## 横幅类型
 * - **Error**: 错误提示（如设备撤销、纪元冲突）
 * - **Warning**: 警告提示（如密钥轮换中、降级模式）
 * - **Info**: 信息提示（如新设备加入、备份完成）
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示警告消息，不执行敏感操作
 * - 所有敏感操作（如确认撤销）通过 Rust Core 验证后执行
 *
 * @param message 警告消息
 * @param modifier 修饰符
 * @param type 横幅类型
 * @param icon 自定义图标（null 使用默认图标）
 * @param dismissible 是否可关闭
 * @param onDismiss 关闭回调
 * @param actionText 操作按钮文本（null 不显示按钮）
 * @param onAction 操作按钮回调
 */
@Composable
fun WarningBanner(
    message: String,
    modifier: Modifier = Modifier,
    type: WarningBannerType = WarningBannerType.Info,
    icon: ImageVector? = null,
    dismissible: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (containerColor, contentColor, defaultIcon) = when (type) {
        is WarningBannerType.Error -> {
            Triple(
                QuantumRed.copy(alpha = 0.1f),
                QuantumRed,
                Icons.Filled.Error,
            )
        }
        is WarningBannerType.Warning -> {
            Triple(
                QuantumYellow.copy(alpha = 0.15f),
                OnQuantumYellow,
                Icons.Filled.Warning,
            )
        }
        is WarningBannerType.Info -> {
            Triple(
                QuantumBlue.copy(alpha = 0.1f),
                QuantumBlue,
                Icons.Filled.Info,
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = WarningBannerShape,
        border = BorderStroke(1.dp, containerColor),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标
            Icon(
                imageVector = icon ?: defaultIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 消息内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = WarningBannerTextStyle,
                    color = contentColor,
                    maxLines = if (actionText != null) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )

                // 操作按钮
                if (actionText != null && onAction != null) {
                    ActionButton(
                        text = actionText,
                        onClick = onAction,
                        type = when (type) {
                            is WarningBannerType.Error -> ButtonType.Danger
                            is WarningBannerType.Warning -> ButtonType.Primary
                            is WarningBannerType.Info -> ButtonType.Secondary
                        },
                        size = ButtonSize.Small,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // 关闭按钮
            if (dismissible && onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = "关闭",
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * 警告横幅类型
 */
sealed class WarningBannerType {
    /**
     * 错误类型
     *
     * 用于严重错误，如设备撤销、纪元冲突、密钥泄漏
     */
    data object Error : WarningBannerType()

    /**
     * 警告类型
     *
     * 用于警告提示，如密钥轮换中、降级模式、网络不稳定
     */
    data object Warning : WarningBannerType()

    /**
     * 信息类型
     *
     * 用于一般信息，如新设备加入、备份完成、同步成功
     */
    data object Info : WarningBannerType()
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_Error() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "设备已被远程撤销，所有密钥和数据已清除。请联系支持团队了解更多信息。",
            type = WarningBannerType.Error,
            dismissible = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_Warning() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "密钥轮换进行中，请保持应用开启。此过程可能需要几分钟时间。",
            type = WarningBannerType.Warning,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_Info() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "新设备已成功加入您的信任网络。",
            type = WarningBannerType.Info,
            dismissible = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_WithAction() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "检测到设备完整性验证失败，应用已进入只读安全模式。",
            type = WarningBannerType.Warning,
            actionText = "重新验证",
            onAction = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_VetoWarning() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "检测到恢复请求，您可以在剩余时间内否决此操作。如果这是您发起的请求，请忽略此提示。",
            type = WarningBannerType.Error,
            actionText = "否决恢复",
            onAction = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_EpochConflict() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "检测到纪元冲突，您的设备可能已过期。请立即同步最新状态以避免数据丢失。",
            type = WarningBannerType.Error,
            actionText = "立即同步",
            onAction = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_NetworkWarning() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "网络连接不稳定，部分功能可能受限。应用将自动重试连接。",
            type = WarningBannerType.Warning,
            dismissible = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_MnemonicBackup() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "请务必妥善备份您的助记词。助记词是恢复账户的唯一方式，丢失后将无法找回。",
            type = WarningBannerType.Warning,
            actionText = "立即备份",
            onAction = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 350)
@Composable
private fun WarningBannerPreview_ShortMessage() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "操作成功",
            type = WarningBannerType.Info,
            dismissible = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WarningBannerPreview_LongMessage() {
    AeternumPreviewTheme {
        WarningBanner(
            message = "这是一条很长的错误消息，用于测试横幅在处理长文本时的显示效果。横幅应该能够正确地截断文本并显示省略号，同时保持布局的稳定性和美观性。",
            type = WarningBannerType.Error,
            dismissible = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 350)
@Composable
private fun WarningBannerPreview_AllTypes() {
    AeternumPreviewTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            WarningBanner(
                message = "错误消息示例",
                type = WarningBannerType.Error,
            )
            Spacer(modifier = Modifier.size(8.dp))
            WarningBanner(
                message = "警告消息示例",
                type = WarningBannerType.Warning,
            )
            Spacer(modifier = Modifier.size(8.dp))
            WarningBanner(
                message = "信息消息示例",
                type = WarningBannerType.Info,
            )
        }
    }
}
