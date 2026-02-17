package io.aeternum.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.aeternum.ui.components.ActionButton
import androidx.compose.ui.graphics.vector.ImageVector
import io.aeternum.ui.theme.CodeTextStyle
import io.aeternum.ui.theme.MachineStateColor
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnQuantumBlue
import io.aeternum.ui.theme.OnQuantumGreen
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.SurfaceVariantColor
import io.aeternum.ui.viewmodel.AeternumViewModel
import io.aeternum.ui.state.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备详情屏幕
 *
 * 用于显示单个设备的详细信息，包括设备状态、纪元信息、完整性状态等。
 *
 * ## 设计理念
 * - **信息透明**: 清晰展示设备的所有关键信息
 * - **安全第一**: 脱敏显示敏感信息，撤销操作需要确认
 * - **操作便捷**: 提供快速操作入口（撤销设备等）
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示脱敏后的设备信息
 * - INVARIANT: 所有操作通过 ViewModel 执行，由 Rust Core 验证
 * - 设备 ID 等敏感信息以脱敏形式显示
 *
 * @param deviceId 设备 ID
 * @param viewModel Aeternum ViewModel
 * @param onNavigateBack 返回上一页的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    viewModel: AeternumViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
) {
    // 收集设备详情状态
    val deviceDetailState by viewModel.deviceDetailState.collectAsState()

    // 撤销确认对话框状态
    var showRevokeDialog by remember { mutableStateOf(false) }

    // 加载设备详情
    androidx.compose.runtime.LaunchedEffect(deviceId) {
        viewModel.loadDeviceDetail(deviceId)
    }

    Scaffold(
        topBar = {
            DeviceDetailTopBar(
                onNavigateBack = onNavigateBack,
                onRevokeClick = {
                    if (deviceDetailState is UiState.Success) {
                        val device = (deviceDetailState as UiState.Success).data
                        // 仅非本机且非撤销状态的设备可以撤销
                        if (!device.isThisDevice && device.state != "revoked" && device.state != "Revoked") {
                            showRevokeDialog = true
                        }
                    }
                },
                deviceState = if (deviceDetailState is UiState.Success) {
                    (deviceDetailState as UiState.Success).data.state
                } else {
                    null
                },
                isThisDevice = if (deviceDetailState is UiState.Success) {
                    (deviceDetailState as UiState.Success).data.isThisDevice
                } else {
                    false
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val state = deviceDetailState) {
                is UiState.Idle -> {
                    // 初始状态，显示加载指示器
                    LoadingState()
                }

                is UiState.Loading -> {
                    // 加载状态
                    LoadingState()
                }

                is UiState.Success -> {
                    // 成功状态，显示设备详情
                    DeviceDetailContent(
                        device = state.data,
                        onRevokeClick = { showRevokeDialog = true },
                    )
                }

                is UiState.Error -> {
                    // 错误状态
                    ErrorState(
                        error = state.error,
                        recoverable = state.recoverable,
                        onRetry = { viewModel.loadDeviceDetail(deviceId) },
                        onBack = onNavigateBack,
                    )
                }
            }
        }
    }

    // 撤销确认对话框
    if (showRevokeDialog) {
        RevokeConfirmDialog(
            deviceName = if (deviceDetailState is UiState.Success) {
                (deviceDetailState as UiState.Success).data.name
            } else {
                "未知设备"
            },
            onConfirm = {
                showRevokeDialog = false
                viewModel.revokeDevice(deviceId.hexStringToByteArray())
                // 撤销后返回设备列表
                onNavigateBack()
            },
            onDismiss = { showRevokeDialog = false },
        )
    }
}

/**
 * 设备详情顶部栏
 *
 * @param onNavigateBack 返回回调
 * @param onRevokeClick 撤销按钮回调
 * @param deviceState 设备状态
 * @param isThisDevice 是否为本机
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailTopBar(
    onNavigateBack: () -> Unit,
    onRevokeClick: () -> Unit,
    deviceState: String?,
    isThisDevice: Boolean,
) {
    // 判断是否显示撤销按钮
    val showRevokeButton = deviceState != null &&
            !isThisDevice &&
            deviceState != "revoked" &&
            deviceState != "Revoked"

    TopAppBar(
        title = {
            Text(
                text = "设备详情",
                color = OnDeepSpaceBackground,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = OnDeepSpaceBackground,
                )
            }
        },
        actions = {
            if (showRevokeButton) {
                TextButton(
                    onClick = onRevokeClick,
                ) {
                    Text(
                        text = "撤销设备",
                        color = QuantumRed,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * 设备详情内容
 *
 * @param device 设备信息
 * @param onRevokeClick 撤销按钮回调
 */
@Composable
private fun DeviceDetailContent(
    device: DeviceDetailInfo,
    onRevokeClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 设备名称卡片
        DeviceNameCard(device = device)

        // 设备状态卡片
        DeviceStateCard(device = device)

        // 设备信息卡片
        DeviceInfoCard(device = device)

        // 安全信息卡片
        SecurityInfoCard(device = device)

        // 活跃信息卡片
        ActivityInfoCard(device = device)

        // 危险操作区域（仅非本机且非撤销状态显示）
        if (!device.isThisDevice && device.state != "revoked" && device.state != "Revoked") {
            DangerActionsCard(
                deviceName = device.name,
                onRevokeClick = onRevokeClick,
            )
        }

        // 底部间距
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 设备名称卡片
 *
 * @param device 设备信息
 */
@Composable
private fun DeviceNameCard(device: DeviceDetailInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariantColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // 设备名称
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnDeepSpaceBackground,
                    )

                    if (device.isThisDevice) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = QuantumBlue,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = "当前设备",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnQuantumBlue,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // 状态图标
                Icon(
                    imageVector = getStateIcon(device.state),
                    contentDescription = "设备状态",
                    tint = getStateColor(device.state),
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

/**
 * 设备状态卡片
 *
 * @param device 设备信息
 */
@Composable
private fun DeviceStateCard(device: DeviceDetailInfo) {
    val stateColor = getStateColor(device.state)
    val stateInfo = getStateInfo(device.state)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = stateColor.copy(alpha = 0.1f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = stateColor.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // 状态标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = stateInfo.icon,
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "设备状态",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDeepSpaceBackground,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 状态显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stateInfo.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = stateColor,
                )

                // 状态指示点
                Surface(
                    color = stateColor,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Box(
                        modifier = Modifier.size(16.dp),
                    ) {
                        // 脉冲动画效果（使用 LinearProgressIndicator）
                        LinearProgressIndicator(
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 状态描述
            Text(
                text = stateInfo.detail,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantColor,
            )
        }
    }
}

/**
 * 设备信息卡片
 *
 * @param device 设备信息
 */
@Composable
private fun DeviceInfoCard(device: DeviceDetailInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariantColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
            Text(
                text = "设备信息",
                style = MaterialTheme.typography.titleMedium,
                color = OnDeepSpaceBackground,
            )

            // 设备 ID
            InfoRow(
                label = "设备 ID",
                value = device.id.take(8) + "..." + device.id.takeLast(4),
                isCode = true,
            )

            // 当前纪元
            InfoRow(
                label = "当前纪元",
                value = "Epoch ${device.epoch}",
                isCode = true,
            )

            // 设备类型
            InfoRow(
                label = "设备类型",
                value = device.deviceType,
            )

            // 操作系统
            InfoRow(
                label = "操作系统",
                value = device.osVersion,
            )
        }
    }
}

/**
 * 安全信息卡片
 *
 * @param device 设备信息
 */
@Composable
private fun SecurityInfoCard(device: DeviceDetailInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariantColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = QuantumGreen,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "安全状态",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDeepSpaceBackground,
                )
            }

            // 完整性状态
            IntegrityStatusRow(
                isStrong = device.integrityStrong,
            )

            // 加密状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "数据加密",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariantColor,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已加密",
                        tint = QuantumGreen,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "已加密",
                        style = MaterialTheme.typography.bodySmall,
                        color = QuantumGreen,
                    )
                }
            }
        }
    }
}

/**
 * 活跃信息卡片
 *
 * @param device 设备信息
 */
@Composable
private fun ActivityInfoCard(device: DeviceDetailInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariantColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = QuantumBlue,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "活跃信息",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDeepSpaceBackground,
                )
            }

            // 首次注册时间
            InfoRow(
                label = "注册时间",
                value = formatTimestamp(device.registeredAt),
            )

            // 最后活跃时间
            InfoRow(
                label = "最后活跃",
                value = formatTimestamp(device.lastActiveAt),
            )

            // 活跃时长
            val activeDuration = calculateActiveDuration(device.registeredAt)
            InfoRow(
                label = "活跃时长",
                value = activeDuration,
            )
        }
    }
}

/**
 * 危险操作卡片
 *
 * @param deviceName 设备名称
 * @param onRevokeClick 撤销按钮回调
 */
@Composable
private fun DangerActionsCard(
    deviceName: String,
    onRevokeClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = QuantumRed.copy(alpha = 0.1f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = QuantumRed.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = QuantumRed,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "危险操作",
                    style = MaterialTheme.typography.titleMedium,
                    color = QuantumRed,
                )
            }

            // 警告说明
            Text(
                text = "撤销设备将立即移除其对 Vault 的访问权限，此操作不可撤销。",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantColor,
            )

            // 撤销按钮
            ActionButton(
                text = "撤销此设备",
                onClick = onRevokeClick,
                type = io.aeternum.ui.components.ButtonType.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 信息行
 *
 * @param label 标签
 * @param value 值
 * @param isCode 是否为代码样式
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    isCode: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantColor,
        )

        Text(
            text = value,
            style = if (isCode) CodeTextStyle else MaterialTheme.typography.bodyMedium,
            color = if (isCode) OnDeepSpaceBackground else OnDeepSpaceBackground,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * 完整性状态行
 *
 * @param isStrong 是否为强完整性
 */
@Composable
private fun IntegrityStatusRow(isStrong: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "完整性验证",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantColor,
        )

        if (isStrong) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "强完整性",
                    tint = QuantumGreen,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "强完整性",
                    style = MaterialTheme.typography.bodySmall,
                    color = QuantumGreen,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "完整性降级",
                    tint = QuantumRed,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "完整性降级",
                    style = MaterialTheme.typography.bodySmall,
                    color = QuantumRed,
                )
            }
        }
    }
}

/**
 * 加载状态
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = QuantumBlue)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "加载设备详情...",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantColor,
            )
        }
    }
}

/**
 * 错误状态
 *
 * @param error 错误信息
 * @param recoverable 是否可恢复
 * @param onRetry 重试回调
 * @param onBack 返回回调
 */
@Composable
private fun ErrorState(
    error: String,
    recoverable: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = QuantumRed,
                modifier = Modifier.size(64.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleLarge,
                color = OnDeepSpaceBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (recoverable) {
                androidx.compose.material3.Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重试")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.material3.OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("返回")
            }
        }
    }
}

/**
 * 撤销确认对话框
 *
 * @param deviceName 设备名称
 * @param onConfirm 确认回调
 * @param onDismiss 取消回调
 */
@Composable
private fun RevokeConfirmDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = QuantumRed,
            )
        },
        title = {
            Text(
                text = "确认撤销设备",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "您确定要撤销以下设备吗？",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = QuantumRed,
                    fontFamily = FontFamily.Monospace,
                )

                Text(
                    text = "撤销后，该设备将无法访问您的 Vault 数据。此操作不可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(
                    text = "确认撤销",
                    color = QuantumRed,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("取消")
            }
        },
    )
}

// ============================================================================
// 辅助函数
// ============================================================================

/**
 * 根据状态获取图标
 *
 * @param state 状态字符串
 * @return 对应的图标
 */
@Composable
private fun getStateIcon(state: String): ImageVector {
    return when (state.lowercase()) {
        "idle", "active" -> Icons.Default.CheckCircle
        "decrypting" -> Icons.Default.Block
        "rekeying" -> Icons.Default.Warning
        "degraded" -> Icons.Default.ErrorOutline
        "revoked" -> Icons.Default.Block
        else -> Icons.Default.Info
    }
}

/**
 * 根据状态获取颜色
 *
 * @param state 状态字符串
 * @return 对应的颜色
 */
private fun getStateColor(state: String): androidx.compose.ui.graphics.Color {
    return when (state.lowercase()) {
        "idle", "active" -> QuantumGreen
        "decrypting" -> QuantumBlue
        "rekeying" -> MachineStateColor.Rekeying.color
        "degraded" -> MachineStateColor.Degraded.color
        "revoked" -> MachineStateColor.Revoked.color
        else -> QuantumBlue
    }
}

/**
 * 状态信息
 *
 * @param description 状态描述
 * @param detail 详细说明
 * @param icon 状态图标
 */
private data class StateInfo(
    val description: String,
    val detail: String,
    val icon: ImageVector,
)

/**
 * 根据状态获取状态信息
 *
 * @param state 状态字符串
 * @return 状态信息
 */
@Composable
private fun getStateInfo(state: String): StateInfo {
    return when (state.lowercase()) {
        "idle", "active" -> StateInfo(
            description = "活跃",
            detail = "设备正常，可以安全使用",
            icon = Icons.Default.CheckCircle,
        )
        "decrypting" -> StateInfo(
            description = "解密中",
            detail = "设备正在访问加密数据",
            icon = Icons.Default.Block,
        )
        "rekeying" -> StateInfo(
            description = "轮换中",
            detail = "设备正在执行密钥轮换操作",
            icon = Icons.Default.Warning,
        )
        "degraded" -> StateInfo(
            description = "降级模式",
            detail = "设备完整性验证失败，功能受限",
            icon = Icons.Default.ErrorOutline,
        )
        "revoked" -> StateInfo(
            description = "已撤销",
            detail = "设备已被撤销，无法访问 Vault 数据",
            icon = Icons.Default.Block,
        )
        else -> StateInfo(
            description = "未知状态",
            detail = "设备状态未知",
            icon = Icons.Default.Info,
        )
    }
}

/**
 * 格式化时间戳
 *
 * @param timestamp 时间戳（毫秒）
 * @return 格式化后的时间字符串
 */
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "未知"

    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 计算活跃时长
 *
 * @param registeredAt 注册时间戳（毫秒）
 * @return 活跃时长描述
 */
private fun calculateActiveDuration(registeredAt: Long): String {
    if (registeredAt == 0L) return "未知"

    val now = System.currentTimeMillis()
    val diff = now - registeredAt

    val days = diff / (1000 * 60 * 60 * 24)
    val hours = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)

    return when {
        days > 365 -> "${days / 365} 年"
        days > 30 -> "${days / 30} 个月"
        days > 0 -> "$days 天"
        hours > 0 -> "$hours 小时"
        else -> "不到 1 小时"
    }
}

// ============================================================================
// 数据模型
// ============================================================================

/**
 * 设备详情信息
 *
 * INVARIANT: 仅包含脱敏后的设备信息，不包含任何敏感密钥材料
 */
data class DeviceDetailInfo(
    val id: String,
    val name: String,
    val epoch: UInt,
    val state: String,
    val isThisDevice: Boolean,
    val deviceType: String,
    val osVersion: String,
    val integrityStrong: Boolean,
    val registeredAt: Long,
    val lastActiveAt: Long,
)

// ============================================================================
// 扩展函数
// ============================================================================

/**
 * 十六进制字符串转 ByteArray
 *
 * 将十六进制编码的字符串转换回字节数组
 *
 * @return 字节数组
 */
private fun String.hexStringToByteArray(): ByteArray {
    val len = this.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4)
                + Character.digit(this[i + 1], 16)).toByte()
        i += 2
    }
    return data
}
