package io.aeternum.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnQuantumBlue
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.SurfaceColor

/**
 * 快速操作按钮组件
 *
 * 用于主屏幕的快速操作入口，提供常用功能的快捷访问。
 *
 * ## 设计理念
 * - **图标优先**: 使用图标传达功能，文字作为辅助
 * - **网格布局**: 2x2 网格，平衡信息密度和可点击区域
 * - **视觉反馈**: 点击时有明确的视觉反馈
 *
 * ## 默认操作
 * - **查看密钥**: 进入 Vault 解密界面
 * - **设备管理**: 查看和管理已注册设备
 * - **密钥轮换**: 手动触发 PQRR 密钥轮换
 * - **恢复流程**: 发起助记词恢复（可选）
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅处理用户交互，不执行敏感操作
 * - 所有敏感操作通过 Rust Core 验证后执行
 * - 恢复流程需要额外的生物识别验证
 *
 * @param actions 快速操作列表
 * @param modifier 修饰符
 * @param onActionClick 操作点击回调
 */
@Composable
fun QuickActions(
    actions: List<QuickAction>,
    modifier: Modifier = Modifier,
    onActionClick: (QuickActionType) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题
        Text(
            text = "快速操作",
            style = MaterialTheme.typography.titleMedium,
            color = OnDeepSpaceBackground,
            fontWeight = FontWeight.SemiBold,
        )

        // 按钮网格（2x2）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 第一行
            actions.take(2).forEach { action ->
                QuickActionButton(
                    action = action,
                    onClick = { onActionClick(action.type) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (actions.size > 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 第二行
                actions.drop(2).take(2).forEach { action ->
                    QuickActionButton(
                        action = action,
                        onClick = { onActionClick(action.type) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // 如果只有 3 个按钮，添加占位符
                if (actions.size == 3) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 快速操作按钮
 *
 * @param action 操作信息
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun QuickActionButton(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 图标
            Surface(
                color = action.iconBackgroundColor,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.description,
                    tint = action.iconColor,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 标题
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium,
                color = OnDeepSpaceBackground,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            // 描述（如果有）
            if (action.subtitle != null) {
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDeepSpaceBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 快速操作信息
 *
 * @property type 操作类型
 * @property title 标题
 * @property subtitle 副标题（可选）
 * @property icon 图标
 * @property iconColor 图标颜色
 * @property iconBackgroundColor 图标背景色
 * @property description 操作描述
 */
data class QuickAction(
    val type: QuickActionType,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val iconColor: androidx.compose.ui.graphics.Color = QuantumBlue,
    val iconBackgroundColor: androidx.compose.ui.graphics.Color = QuantumBlue.copy(alpha = 0.15f),
    val description: String,
)

/**
 * 快速操作类型
 *
 * @property description 类型描述
 */
sealed class QuickActionType(val description: String) {
    /**
     * 查看密钥
     *
     * 进入 Vault 解密界面，需要生物识别认证
     */
    data object ViewVault : QuickActionType("查看密钥")

    /**
     * 设备管理
     *
     * 查看和管理已注册设备
     */
    data object DeviceManagement : QuickActionType("设备管理")

    /**
     * 密钥轮换
     *
     * 手动触发 PQRR 密钥轮换
     */
    data object KeyRotation : QuickActionType("密钥轮换")

    /**
     * 恢复流程
     *
     * 发起助记词恢复流程
     */
    data object Recovery : QuickActionType("恢复流程")

    /**
     * 自定义操作
     */
    data class Custom(val customDescription: String) : QuickActionType(customDescription)
}

// ============================================================================
// 默认快速操作
// ============================================================================

/**
 * 默认快速操作列表
 */
val defaultQuickActions: List<QuickAction>
    @Composable
    get() = listOf(
        QuickAction(
            type = QuickActionType.ViewVault,
            title = "查看密钥",
            subtitle = "需要认证",
            icon = ImageVector.vectorResource(android.R.drawable.ic_menu_view),
            iconColor = QuantumBlue,
            iconBackgroundColor = QuantumBlue.copy(alpha = 0.15f),
            description = "访问加密的 Vault 数据",
        ),
        QuickAction(
            type = QuickActionType.DeviceManagement,
            title = "设备管理",
            icon = ImageVector.vectorResource(android.R.drawable.ic_menu_agenda),
            iconColor = QuantumBlue,
            iconBackgroundColor = QuantumBlue.copy(alpha = 0.15f),
            description = "管理已注册设备",
        ),
        QuickAction(
            type = QuickActionType.KeyRotation,
            title = "密钥轮换",
            subtitle = "手动触发",
            icon = ImageVector.vectorResource(android.R.drawable.ic_menu_rotate),
            iconColor = QuantumBlue,
            iconBackgroundColor = QuantumBlue.copy(alpha = 0.15f),
            description = "手动触发 PQRR 密钥轮换",
        ),
        QuickAction(
            type = QuickActionType.Recovery,
            title = "恢复流程",
            subtitle = "助记词",
            icon = ImageVector.vectorResource(android.R.drawable.ic_menu_revert),
            iconColor = OnQuantumBlue,
            iconBackgroundColor = OnQuantumBlue.copy(alpha = 0.15f),
            description = "使用助记词恢复访问权限",
        ),
    )

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun QuickActionsPreview_Default() {
    AeternumPreviewTheme {
        QuickActions(
            actions = defaultQuickActions,
            onActionClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun QuickActionsPreview_TwoActions() {
    AeternumPreviewTheme {
        QuickActions(
            actions = defaultQuickActions.take(2),
            onActionClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun QuickActionsPreview_ThreeActions() {
    AeternumPreviewTheme {
        QuickActions(
            actions = defaultQuickActions.take(3),
            onActionClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun QuickActionsPreview_CustomActions() {
    AeternumPreviewTheme {
        val customActions = listOf(
            QuickAction(
                type = QuickActionType.Custom("设置"),
                title = "设置",
                icon = ImageVector.vectorResource(android.R.drawable.ic_menu_preferences),
                description = "应用设置",
            ),
            QuickAction(
                type = QuickActionType.Custom("帮助"),
                title = "帮助",
                icon = ImageVector.vectorResource(android.R.drawable.ic_menu_help),
                description = "获取帮助",
            ),
            QuickAction(
                type = QuickActionType.Custom("关于"),
                title = "关于",
                icon = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                description = "关于应用",
            ),
            QuickAction(
                type = QuickActionType.Custom("退出"),
                title = "退出",
                icon = ImageVector.vectorResource(android.R.drawable.ic_menu_close_clear_cancel),
                description = "退出应用",
            ),
        )

        QuickActions(
            actions = customActions,
            onActionClick = {},
        )
    }
}
