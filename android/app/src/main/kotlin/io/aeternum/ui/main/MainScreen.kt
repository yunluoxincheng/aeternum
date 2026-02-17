package io.aeternum.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.aeternum.ui.components.ActivityInfo
import io.aeternum.ui.components.QuickActions
import io.aeternum.ui.components.QuickActionType
import io.aeternum.ui.components.SecurityStatus
import io.aeternum.ui.components.StatusCard
import io.aeternum.ui.state.ActiveSubState
import io.aeternum.ui.state.AeternumUiState
import io.aeternum.ui.state.UiState
import io.aeternum.ui.state.getDisplayName
import io.aeternum.ui.theme.AeternumTheme
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.viewmodel.AeternumViewModel

/**
 * Aeternum 主屏幕
 *
 * 应用主界面，在 Idle 状态下显示，包含状态卡片、快速操作和最近活动。
 *
 * ## 设计理念
 * - **信息层次**: 状态 > 操作 > 历史
 * - **安全优先**: 状态卡片清晰传达设备安全状态
 * - **快速访问**: 常用操作一键触达
 * - **透明度**: 最近活动让用户了解系统变化
 *
 * ## Idle 状态特性
 * - Vault 已锁定，需要生物识别解锁
 * - 显示当前纪元和设备统计
 * - 允许查看设备列表和触发操作
 * - 显示最近的系统活动
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示脱敏数据，不持有明文密钥
 * - 所有敏感操作（如查看密钥）需要生物识别认证
 * - 不暴露设备 ID 或敏感标识符
 * - 纪元号和活动时间可显示（非敏感信息）
 *
 * @param viewModel Aeternum ViewModel
 * @param modifier 修饰符
 * @param onUnlockRequest 请求生物识别解锁
 * @param onNavigateToScreen 导航到其他屏幕
 */
@Composable
fun MainScreen(
    viewModel: AeternumViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onUnlockRequest: () -> Unit = {},
    onNavigateToScreen: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    // 仅在 Idle 状态显示主屏幕
    when (val state = uiState) {
        is AeternumUiState.Active -> {
            when (state.subState) {
                is ActiveSubState.Idle -> {
                    IdleMainContent(
                        viewModel = viewModel,
                        modifier = modifier,
                        onUnlockRequest = onUnlockRequest,
                        onNavigateToScreen = onNavigateToScreen,
                    )
                }
                else -> {
                    // 其他状态由其他屏幕处理
                    OtherStateContent(state.subState)
                }
            }
        }
        else -> {
            // 其他状态由导航处理
            OtherUiStateContent(state)
        }
    }
}

/**
 * Idle 状态主内容
 *
 * 性能优化：
 * - 使用 remember 缓存静态数据，避免每次重组重新创建
 * - 提取状态到局部变量，减少多次读取
 * - 使用 key() 确保 LazyColumn 项正确更新
 *
 * @param viewModel Aeternum ViewModel
 * @param modifier 修饰符
 * @param onUnlockRequest 请求生物识别解锁
 * @param onNavigateToScreen 导航到其他屏幕
 */
@Composable
private fun IdleMainContent(
    viewModel: AeternumViewModel,
    modifier: Modifier = Modifier,
    onUnlockRequest: () -> Unit = {},
    onNavigateToScreen: (String) -> Unit = {},
) {
    // 提取状态到局部变量，避免多次读取 StateFlow
    val currentEpoch by viewModel.currentEpoch.collectAsState()
    val deviceListState by viewModel.deviceListState.collectAsState()

    // 缓存示例活动数据，避免每次重组重新创建
    // TODO: 替换为从 ViewModel 或 Rust Core 获取实际活动数据
    val recentActivities = remember { getSampleActivities() }

    // 默认快速操作
    val quickActions = io.aeternum.ui.components.defaultQuickActions

    // 计算设备数量和安全状态（提取到局部变量）
    val (deviceCount, securityStatus) = remember(deviceListState) {
        val count = when (val state = deviceListState) {
            is UiState.Success -> state.data.size
            else -> 1
        }
        val status = when (val s = deviceListState) {
            is UiState.Success -> {
                val hasDegraded = s.data.any { it.state == "degraded" }
                val hasRevoked = s.data.any { it.state == "revoked" }
                when {
                    hasRevoked -> SecurityStatus.Danger
                    hasDegraded -> SecurityStatus.Warning
                    else -> SecurityStatus.Secure
                }
            }
            else -> SecurityStatus.Secure
        }
        count to status
    }

    Surface(
        color = DeepSpaceBackground,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 状态卡片
            item {
                StatusCard(
                    status = securityStatus,
                    epoch = currentEpoch ?: 1u,
                    deviceCount = deviceCount,
                )
            }

            // 快速操作
            item {
                QuickActions(
                    actions = quickActions,
                    onActionClick = { actionType ->
                        when (actionType) {
                            is QuickActionType.ViewVault -> {
                                onUnlockRequest()
                            }
                            is QuickActionType.DeviceManagement -> {
                                onNavigateToScreen("devices")
                            }
                            is QuickActionType.KeyRotation -> {
                                viewModel.startRekeying()
                            }
                            is QuickActionType.Recovery -> {
                                onNavigateToScreen("recovery")
                            }
                            else -> {
                                onNavigateToScreen(actionType.description)
                            }
                        }
                    },
                )
            }

            // 最近活动
            item {
                Column {
                    Text(
                        text = "最近活动",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnDeepSpaceBackground,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    if (recentActivities.isEmpty()) {
                        Text(
                            text = "暂无最近活动",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariantColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        )
                    } else {
                        recentActivities.forEach { activity ->
                            io.aeternum.ui.components.ActivityItem(
                                activity = activity,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // 底部间距
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 其他状态内容占位符
 *
 * 对于 Rekeying 状态，显示专门的轮换屏幕
 * 对于其他状态，显示占位符
 */
@Composable
private fun OtherStateContent(
    subState: ActiveSubState,
) {
    when (subState) {
        is ActiveSubState.Rekeying -> {
            // 显示密钥轮换屏幕
            RekeyingScreen()
        }
        else -> {
            // 其他状态的占位符
            Surface(
                color = DeepSpaceBackground,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = when (subState) {
                            is ActiveSubState.Idle -> "空闲"
                            is ActiveSubState.Decrypting -> "已解锁"
                            is ActiveSubState.Rekeying -> "密钥轮换中"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = OnDeepSpaceBackground,
                    )
                }
            }
        }
    }
}

/**
 * 其他 UI 状态内容占位符
 */
@Composable
private fun OtherUiStateContent(
    uiState: AeternumUiState,
) {
    Surface(
        color = DeepSpaceBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = uiState.getDisplayName(),
                style = MaterialTheme.typography.headlineMedium,
                color = OnDeepSpaceBackground,
            )
        }
    }
}

/**
 * 获取示例活动数据
 *
 * TODO: 替换为从 ViewModel 或 Rust Core 获取实际活动数据
 *
 * @return 示例活动列表
 */
private fun getSampleActivities(): List<ActivityInfo> {
    return listOf(
        ActivityInfo(
            type = io.aeternum.ui.components.ActivityType.KeyRotation,
            title = "密钥已轮换",
            description = "纪元升级至 v5",
            timestamp = "2 分钟前",
        ),
        ActivityInfo(
            type = io.aeternum.ui.components.ActivityType.DeviceAdded,
            title = "新设备已添加",
            description = "iPad Pro",
            timestamp = "1 小时前",
        ),
        ActivityInfo(
            type = io.aeternum.ui.components.ActivityType.BiometricAuth,
            title = "生物识别认证成功",
            timestamp = "昨天",
        ),
    )
}

// ============================================================================
// 预览
// ============================================================================

// Preview 需要 Fake ViewModel，暂不提供预览
// 实际预览应在运行时或使用 Mock 数据
