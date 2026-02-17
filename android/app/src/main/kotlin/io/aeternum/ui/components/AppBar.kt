package io.aeternum.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.SurfaceColor
import io.aeternum.ui.theme.SurfaceVariantColor

/**
 * Aeternum 应用栏组件
 *
 * 用于屏幕顶部导航和标题显示，提供一致的用户体验。
 *
 * ## 设计理念
 * - **层次清晰**: 标题、导航、操作按钮布局清晰
 * - **上下文感知**: 根据当前屏幕显示合适的导航图标
 * - **视觉统一**: 与整体主题保持一致的颜色和样式
 *
 * ## 应用栏类型
 * - **Small**: 标准高度 (64dp)，用于大多数屏幕
 * - **Medium**: 中等高度 (112dp)，用于重要屏幕
 * - **Large**: 大高度 (152dp)，用于欢迎/设置屏幕
 *
 * ## 导航图标
 * - **Back**: 返回图标（用于子屏幕）
 * - **Close**: 关闭图标（用于模态对话框）
 * - **Menu**: 菜单图标（用于主屏幕）
 * - **None**: 无导航图标
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅处理导航，不执行敏感操作
 * - 所有敏感操作（如设置）通过 Rust Core 验证后执行
 *
 * @param title 标题
 * @param modifier 修饰符
 * @param navigationIcon 导航图标类型
 * @param onNavigationClick 导航图标点击回调
 * @param actions 操作按钮（可组合）
 * @param subtitle 副标题（可选）
 * @param height 应用栏高度
 */
@Composable
fun AeternumAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: AppBarNavigationIcon = AppBarNavigationIcon.None,
    onNavigationClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    subtitle: String? = null,
    height: AppBarHeight = AppBarHeight.Small,
) {
    val (icon, iconDescription) = when (navigationIcon) {
        is AppBarNavigationIcon.Back -> Icons.AutoMirrored.Filled.ArrowBack to "返回"
        is AppBarNavigationIcon.Close -> Icons.Default.Close to "关闭"
        is AppBarNavigationIcon.Menu -> Icons.Default.Menu to "菜单"
        is AppBarNavigationIcon.None -> null to null
    }

    val appBarHeight = when (height) {
        is AppBarHeight.Small -> 64.dp
        is AppBarHeight.Medium -> 112.dp
        is AppBarHeight.Large -> 152.dp
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (height is AppBarHeight.Small) 0.dp else 2.dp,
    ) {
        if (subtitle != null) {
            // 带副标题的应用栏
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(appBarHeight)
                    .padding(horizontal = 4.dp),
            ) {
                // 顶部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 导航图标
                    if (icon != null && onNavigationClick != null) {
                        IconButton(onClick = onNavigationClick) {
                            Icon(
                                imageVector = icon,
                                contentDescription = iconDescription,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 操作按钮
                    if (actions != null) {
                        Row {
                            actions()
                        }
                    }
                }

                // 标题和副标题
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = title,
                        style = if (height is AppBarHeight.Large) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            // 标准应用栏
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = if (height is AppBarHeight.Large) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (icon != null && onNavigationClick != null) {
                        IconButton(onClick = onNavigationClick) {
                            Icon(
                                imageVector = icon,
                                contentDescription = iconDescription,
                            )
                        }
                    }
                },
                actions = {
                    if (actions != null) {
                        actions()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.height(appBarHeight),
            )
        }
    }
}

/**
 * 应用栏导航图标类型
 */
sealed class AppBarNavigationIcon {
    /** 返回图标 */
    data object Back : AppBarNavigationIcon()

    /** 关闭图标 */
    data object Close : AppBarNavigationIcon()

    /** 菜单图标 */
    data object Menu : AppBarNavigationIcon()

    /** 无导航图标 */
    data object None : AppBarNavigationIcon()
}

/**
 * 应用栏高度
 */
sealed class AppBarHeight {
    /** 标准高度 - 64dp */
    data object Small : AppBarHeight()

    /** 中等高度 - 112dp */
    data object Medium : AppBarHeight()

    /** 大高度 - 152dp */
    data object Large : AppBarHeight()
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun AeternumAppBarPreview_Simple() {
    AeternumPreviewTheme {
        AeternumAppBar(
            title = "Aeternum",
            height = AppBarHeight.Small,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AeternumAppBarPreview_WithBack() {
    AeternumPreviewTheme {
        AeternumAppBar(
            title = "设置",
            navigationIcon = AppBarNavigationIcon.Back,
            onNavigationClick = {},
            height = AppBarHeight.Small,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AeternumAppBarPreview_WithActions() {
    AeternumPreviewTheme {
        AeternumAppBar(
            title = "设备管理",
            navigationIcon = AppBarNavigationIcon.Back,
            onNavigationClick = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "更多")
                }
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                }
            },
            height = AppBarHeight.Small,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AeternumAppBarPreview_WithSubtitle() {
    AeternumPreviewTheme {
        AeternumAppBar(
            title = "密钥轮换",
            subtitle = "正在升级到纪元 2",
            navigationIcon = AppBarNavigationIcon.Close,
            onNavigationClick = {},
            height = AppBarHeight.Medium,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumAppBarPreview_LongTitle() {
    AeternumPreviewTheme {
        AeternumAppBar(
            title = "这是一个非常长的标题，用于测试文本溢出处理效果",
            navigationIcon = AppBarNavigationIcon.Back,
            onNavigationClick = {},
            height = AppBarHeight.Small,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumAppBarPreview_Large() {
    AeternumPreviewTheme {
        AeternumAppBar(
            title = "欢迎使用 Aeternum",
            subtitle = "后量子安全的密钥管理系统",
            navigationIcon = AppBarNavigationIcon.Menu,
            onNavigationClick = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                }
            },
            height = AppBarHeight.Large,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumAppBarPreview_Medium() {
    AeternumPreviewTheme {
        AeternumAppBar(
            title = "设备详情",
            subtitle = "Pixel 8 Pro - 活跃",
            navigationIcon = AppBarNavigationIcon.Back,
            onNavigationClick = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "更多")
                }
            },
            height = AppBarHeight.Medium,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun AeternumAppBarPreview_AllVariants() {
    AeternumPreviewTheme {
        Column {
            AeternumAppBar(
                title = "主屏幕",
                navigationIcon = AppBarNavigationIcon.Menu,
                onNavigationClick = {},
            )
            AeternumAppBar(
                title = "子屏幕",
                navigationIcon = AppBarNavigationIcon.Back,
                onNavigationClick = {},
            )
            AeternumAppBar(
                title = "对话框",
                navigationIcon = AppBarNavigationIcon.Close,
                onNavigationClick = {},
            )
        }
    }
}
