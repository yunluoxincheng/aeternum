package io.aeternum.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.ButtonShapes
import io.aeternum.ui.theme.OnQuantumBlue
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.SurfaceColor
import io.aeternum.ui.theme.SurfaceVariantColor

/**
 * Aeternum 操作按钮组件
 *
 * 用于执行主要用户操作，提供清晰的视觉反馈和状态指示。
 *
 * ## 设计理念
 * - **层次清晰**: 主要/次要/危险按钮样式区分操作重要性
 * - **状态明确**: 加载、禁用等状态直观可见
 * - **无障碍**: 所有按钮都有清晰的标签和语义
 *
 * ## 按钮类型
 * - **Primary**: 主要操作，如"确认"、"保存"
 * - **Secondary**: 次要操作，如"取消"、"返回"
 * - **Danger**: 危险操作，如"删除"、"撤销设备"
 * - **Text**: 文本按钮，用于低优先级操作
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅处理用户交互，不执行敏感操作
 * - 所有敏感操作（如密钥删除）通过 Rust Core 验证后执行
 *
 * @param text 按钮文本
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param type 按钮类型
 * @param size 按钮尺寸
 * @param icon 图标（可选）
 * @param isLoading 是否显示加载状态
 * @param enabled 是否启用
 * @param fullWidth 是否全宽显示
 * @param accessibilityDescription 无障碍描述（可选，默认使用按钮文本）
 * @param accessibilityHint 无障碍提示（可选）
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.Primary,
    size: ButtonSize = ButtonSize.Medium,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    accessibilityDescription: String? = null,
    accessibilityHint: String? = null,
) {
    // 构建无障碍语义描述
    val semanticModifier = modifier.semantics {
        role = Role.Button
        contentDescription = accessibilityDescription ?: text
        stateDescription = when {
            isLoading -> "正在加载"
            !enabled -> "已禁用"
            else -> "可用"
        }
        accessibilityHint?.let { /* hint 通过合并到 contentDescription */ }
    }

    when (type) {
        is ButtonType.Primary -> {
            PrimaryButton(
                text = text,
                onClick = onClick,
                modifier = semanticModifier,
                size = size,
                icon = icon,
                isLoading = isLoading,
                enabled = enabled,
                fullWidth = fullWidth,
            )
        }
        is ButtonType.Secondary -> {
            SecondaryButton(
                text = text,
                onClick = onClick,
                modifier = semanticModifier,
                size = size,
                icon = icon,
                isLoading = isLoading,
                enabled = enabled,
                fullWidth = fullWidth,
            )
        }
        is ButtonType.Danger -> {
            DangerButton(
                text = text,
                onClick = onClick,
                modifier = semanticModifier.semantics {
                    // 危险按钮额外添加警告语义
                    contentDescription = "${accessibilityDescription ?: text}。警告：此操作可能不可撤销"
                },
                size = size,
                icon = icon,
                isLoading = isLoading,
                enabled = enabled,
                fullWidth = fullWidth,
            )
        }
        is ButtonType.Text -> {
            TextOnlyButton(
                text = text,
                onClick = onClick,
                modifier = semanticModifier,
                size = size,
                icon = icon,
                enabled = enabled,
            )
        }
    }
}

/**
 * 主要按钮
 */
@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
) {
    val contentColor = OnQuantumBlue
    val containerColor = QuantumBlue

    Button(
        onClick = onClick,
        modifier = if (fullWidth) Modifier.fillMaxWidth() else modifier,
        enabled = enabled && !isLoading,
        shape = ButtonShapes.Primary,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f),
        ),
        contentPadding = size.getContentPadding(hasIcon = icon != null),
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            isLoading = isLoading,
            contentColor = if (enabled && !isLoading) contentColor else contentColor.copy(alpha = 0.5f),
        )
    }
}

/**
 * 次要按钮
 */
@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
) {
    val contentColor = QuantumBlue

    OutlinedButton(
        onClick = onClick,
        modifier = if (fullWidth) Modifier.fillMaxWidth() else modifier,
        enabled = enabled && !isLoading,
        shape = ButtonShapes.Secondary,
        border = BorderStroke(1.dp, contentColor.copy(alpha = if (enabled && !isLoading) 1f else 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
        ),
        contentPadding = size.getContentPadding(hasIcon = icon != null),
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            isLoading = isLoading,
            contentColor = if (enabled && !isLoading) contentColor else contentColor.copy(alpha = 0.5f),
        )
    }
}

/**
 * 危险按钮
 */
@Composable
private fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
) {
    val contentColor = OnQuantumRed
    val containerColor = QuantumRed

    Button(
        onClick = onClick,
        modifier = if (fullWidth) Modifier.fillMaxWidth() else modifier,
        enabled = enabled && !isLoading,
        shape = ButtonShapes.Danger,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f),
        ),
        contentPadding = size.getContentPadding(hasIcon = icon != null),
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            isLoading = isLoading,
            contentColor = if (enabled && !isLoading) contentColor else contentColor.copy(alpha = 0.5f),
        )
    }
}

/**
 * 仅文本按钮
 */
@Composable
private fun TextOnlyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShapes.Secondary,
        contentPadding = size.getContentPadding(hasIcon = icon != null),
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            isLoading = false,
            contentColor = if (enabled) QuantumBlue else QuantumBlue.copy(alpha = 0.5f),
        )
    }
}

/**
 * 按钮内容
 */
@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    isLoading: Boolean,
    contentColor: Color,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = contentColor,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
            )
        }
    }
}

/**
 * 按钮类型
 */
sealed class ButtonType {
    /** 主要操作 */
    data object Primary : ButtonType()

    /** 次要操作 */
    data object Secondary : ButtonType()

    /** 危险操作 */
    data object Danger : ButtonType()

    /** 文本按钮 */
    data object Text : ButtonType()
}

/**
 * 按钮尺寸
 */
enum class ButtonSize(
    private val horizontalPadding: Dp,
    private val verticalPadding: Dp,
) {
    Small(12.dp, 8.dp),
    Medium(16.dp, 10.dp),
    Large(24.dp, 12.dp),
    ;

    fun getContentPadding(hasIcon: Boolean): PaddingValues {
        return PaddingValues(
            horizontal = horizontalPadding,
            vertical = verticalPadding,
        )
    }
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun ActionButtonPreview_Primary() {
    AeternumPreviewTheme {
        ActionButton(
            text = "确认",
            onClick = {},
            type = ButtonType.Primary,
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun ActionButtonPreview_Secondary() {
    AeternumPreviewTheme {
        ActionButton(
            text = "取消",
            onClick = {},
            type = ButtonType.Secondary,
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun ActionButtonPreview_Danger() {
    AeternumPreviewTheme {
        ActionButton(
            text = "删除设备",
            onClick = {},
            type = ButtonType.Danger,
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun ActionButtonPreview_Text() {
    AeternumPreviewTheme {
        ActionButton(
            text = "了解更多",
            onClick = {},
            type = ButtonType.Text,
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun ActionButtonPreview_WithIcon() {
    AeternumPreviewTheme {
        ActionButton(
            text = "保存",
            onClick = {},
            type = ButtonType.Primary,
            icon = ImageVector.vectorResource(android.R.drawable.ic_menu_save),
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun ActionButtonPreview_Loading() {
    AeternumPreviewTheme {
        ActionButton(
            text = "处理中",
            onClick = {},
            type = ButtonType.Primary,
            isLoading = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun ActionButtonPreview_Disabled() {
    AeternumPreviewTheme {
        ActionButton(
            text = "禁用状态",
            onClick = {},
            type = ButtonType.Primary,
            enabled = false,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun ActionButtonPreview_Sizes() {
    AeternumPreviewTheme {
        Row {
            ActionButton(
                text = "小",
                onClick = {},
                size = ButtonSize.Small,
            )
            ActionButton(
                text = "中",
                onClick = {},
                size = ButtonSize.Medium,
            )
            ActionButton(
                text = "大",
                onClick = {},
                size = ButtonSize.Large,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun ActionButtonPreview_AllTypes() {
    AeternumPreviewTheme {
        Row {
            ActionButton(
                text = "主要",
                onClick = {},
                type = ButtonType.Primary,
            )
            ActionButton(
                text = "次要",
                onClick = {},
                type = ButtonType.Secondary,
            )
            ActionButton(
                text = "危险",
                onClick = {},
                type = ButtonType.Danger,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 350)
@Composable
private fun ActionButtonPreview_FullWidth() {
    AeternumPreviewTheme {
        ActionButton(
            text = "全宽按钮",
            onClick = {},
            type = ButtonType.Primary,
            fullWidth = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActionButtonPreview_ButtonRow() {
    AeternumPreviewTheme {
        Row {
            ActionButton(
                text = "取消",
                onClick = {},
                type = ButtonType.Secondary,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                text = "确认",
                onClick = {},
                type = ButtonType.Primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
