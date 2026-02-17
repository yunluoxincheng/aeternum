package io.aeternum.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.BottomNavShape
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.SurfaceColor

/**
 * Aeternum 底部导航栏组件
 *
 * 用于主要功能模块切换，提供清晰的导航体验。
 *
 * ## 设计理念
 * - **固定入口**: 主要功能始终可访问，无需返回
 * - **视觉清晰**: 当前选中项高亮显示
 * - **徽章通知**: 支持显示未读/警告数量
 *
 * ## 导航项要求
 * - 最少 3 个，最多 5 个导航项
 * - 每个导航项必须有图标和标签
 * - 标签建议使用 2-4 个中文字符
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅处理导航，不执行敏感操作
 * - 导航切换应先保存当前状态，再切换到新屏幕
 *
 * @param items 导航项列表
 * @param selectedItem 当前选中项索引
 * @param onItemSelected 选中项变化回调
 * @param modifier 修饰符
 * @param showLabels 是否显示文字标签
 */
@Composable
fun AeternumBottomNavBar(
    items: List<BottomNavItem>,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    require(items.size in 3..5) {
        "底部导航栏必须有 3-5 个导航项，当前有 ${items.size} 个"
    }

    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        shape = BottomNavShape,
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            items.forEachIndexed { index, item ->
                val selected = selectedItem == index

                NavigationBarItem(
                    icon = {
                        if (item.badgeCount != null && item.badgeCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(
                                            text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString()
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.icon,
                                    contentDescription = item.label,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                            )
                        }
                    },
                    label = if (showLabels) {
                        {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else null,
                    selected = selected,
                    onClick = { onItemSelected(index) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = QuantumBlue,
                        selectedTextColor = QuantumBlue,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        indicatorColor = QuantumBlue.copy(alpha = 0.12f),
                    ),
                )
            }
        }
    }
}

/**
 * 底部导航项数据类
 *
 * @property icon 未选中时的图标
 * @property selectedIcon 选中时的图标
 * @property label 导航项标签
 * @property badgeCount 徽章数量（null 表示无徽章）
 */
data class BottomNavItem(
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val label: String,
    val badgeCount: Int? = null,
)

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumBottomNavBarPreview_Standard() {
    AeternumPreviewTheme {
        var selectedItem by rememberSaveable { mutableIntStateOf(0) }

        AeternumBottomNavBar(
            items = listOf(
                BottomNavItem(
                    icon = Icons.Default.Home,
                    selectedIcon = Icons.Default.Home,
                    label = "主页",
                ),
                BottomNavItem(
                    icon = Icons.Default.Lock,
                    selectedIcon = Icons.Default.Lock,
                    label = "密钥",
                ),
                BottomNavItem(
                    icon = Icons.Default.Settings,
                    selectedIcon = Icons.Default.Settings,
                    label = "设置",
                ),
            ),
            selectedItem = selectedItem,
            onItemSelected = { selectedItem = it },
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumBottomNavBarPreview_WithBadges() {
    AeternumPreviewTheme {
        var selectedItem by rememberSaveable { mutableIntStateOf(0) }

        AeternumBottomNavBar(
            items = listOf(
                BottomNavItem(
                    icon = Icons.Default.Home,
                    selectedIcon = Icons.Default.Home,
                    label = "主页",
                    badgeCount = 0,
                ),
                BottomNavItem(
                    icon = Icons.Default.Lock,
                    selectedIcon = Icons.Default.Lock,
                    label = "密钥",
                    badgeCount = 3,
                ),
                BottomNavItem(
                    icon = Icons.Default.Settings,
                    selectedIcon = Icons.Default.Settings,
                    label = "设置",
                    badgeCount = 1,
                ),
            ),
            selectedItem = selectedItem,
            onItemSelected = { selectedItem = it },
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumBottomNavBarPreview_FiveItems() {
    AeternumPreviewTheme {
        var selectedItem by rememberSaveable { mutableIntStateOf(2) }

        AeternumBottomNavBar(
            items = listOf(
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "主页",
                ),
                BottomNavItem(
                    icon = Icons.Default.Lock,
                    label = "密钥",
                ),
                BottomNavItem(
                    icon = Icons.Default.Settings,
                    label = "设置",
                ),
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "设备",
                ),
                BottomNavItem(
                    icon = Icons.Default.Lock,
                    label = "恢复",
                ),
            ),
            selectedItem = selectedItem,
            onItemSelected = { selectedItem = it },
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumBottomNavBarPreview_IconsOnly() {
    AeternumPreviewTheme {
        var selectedItem by rememberSaveable { mutableIntStateOf(0) }

        AeternumBottomNavBar(
            items = listOf(
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "主页",
                ),
                BottomNavItem(
                    icon = Icons.Default.Lock,
                    label = "密钥",
                ),
                BottomNavItem(
                    icon = Icons.Default.Settings,
                    label = "设置",
                ),
            ),
            selectedItem = selectedItem,
            onItemSelected = { selectedItem = it },
            showLabels = false,
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
private fun AeternumBottomNavBarPreview_FullScreen() {
    AeternumPreviewTheme {
        var selectedItem by rememberSaveable { mutableIntStateOf(0) }

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 主内容区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "当前选中: ${listOf("主页", "密钥", "设置")[selectedItem]}",
                    modifier = Modifier.padding(16.dp),
                )
            }

            // 底部导航栏
            AeternumBottomNavBar(
                items = listOf(
                    BottomNavItem(
                        icon = Icons.Default.Home,
                        label = "主页",
                    ),
                    BottomNavItem(
                        icon = Icons.Default.Lock,
                        label = "密钥",
                    ),
                    BottomNavItem(
                        icon = Icons.Default.Settings,
                        label = "设置",
                    ),
                ),
                selectedItem = selectedItem,
                onItemSelected = { selectedItem = it },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumBottomNavBarPreview_WithLargeBadge() {
    AeternumPreviewTheme {
        var selectedItem by rememberSaveable { mutableIntStateOf(0) }

        AeternumBottomNavBar(
            items = listOf(
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "主页",
                    badgeCount = 150,
                ),
                BottomNavItem(
                    icon = Icons.Default.Lock,
                    label = "密钥",
                ),
                BottomNavItem(
                    icon = Icons.Default.Settings,
                    label = "设置",
                ),
            ),
            selectedItem = selectedItem,
            onItemSelected = { selectedItem = it },
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AeternumBottomNavBarPreview_AllItemsSelected() {
    AeternumPreviewTheme {
        Column {
            for (i in 0..2) {
                AeternumBottomNavBar(
                    items = listOf(
                        BottomNavItem(
                            icon = Icons.Default.Home,
                            label = "主页",
                        ),
                        BottomNavItem(
                            icon = Icons.Default.Lock,
                            label = "密钥",
                        ),
                        BottomNavItem(
                            icon = Icons.Default.Settings,
                            label = "设置",
                        ),
                    ),
                    selectedItem = i,
                    onItemSelected = {},
                )
            }
        }
    }
}
