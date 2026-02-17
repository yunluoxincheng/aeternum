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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.aeternum.ui.components.DeviceCard
import io.aeternum.ui.components.LoadingOverlay
import io.aeternum.ui.navigation.DeviceDetailRoute
import io.aeternum.ui.state.UiState
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.viewmodel.AeternumViewModel
import io.aeternum.ui.viewmodel.DeviceFilter
import io.aeternum.ui.viewmodel.DeviceInfo

/**
 * 设备列表屏幕
 *
 * 用于显示和管理所有已注册的设备。
 *
 * ## 设计理念
 * - **信息透明**: 清晰展示所有设备状态和安全信息
 * - **操作便捷**: 提供快速过滤和导航到设备详情
 * - **安全第一**: 脱敏显示设备信息，撤销操作需要确认
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示脱敏后的设备信息
 * - INVARIANT: 所有操作通过 ViewModel 执行，由 Rust Core 验证
 * - 设备 ID 以脱敏形式显示（前 8 位十六进制）
 *
 * @param viewModel Aeternum ViewModel
 * @param onNavigateToDetail 导航到设备详情的回调
 * @param onNavigateToAdd 导航到添加设备的回调
 * @param onNavigateBack 返回上一页的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    viewModel: AeternumViewModel = viewModel(),
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToAdd: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    // 收集设备列表状态
    val deviceListState by viewModel.deviceListState.collectAsState()

    // 当前过滤器
    var selectedFilter by remember { mutableStateOf(DeviceFilter.ALL) }

    // 刷新状态
    var isRefreshing by remember { mutableStateOf(false) }

    // 刷新设备列表
    fun refreshDeviceList() {
        isRefreshing = true
        viewModel.loadDeviceList()
        // TODO: 监听加载完成后设置 isRefreshing = false
    }

    // 初次加载
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (deviceListState is UiState.Idle) {
            viewModel.loadDeviceList()
        }
    }

    Scaffold(
        topBar = {
            DeviceListTopBar(
                onNavigateBack = onNavigateBack,
                onFilterChange = { selectedFilter = it },
                selectedFilter = selectedFilter,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = QuantumBlue,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加设备",
                    tint = OnDeepSpaceBackground,
                )
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val state = deviceListState) {
                is UiState.Idle -> {
                    // 初始状态，显示加载指示器
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = QuantumBlue)
                    }
                }

                is UiState.Loading -> {
                    // 加载状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = QuantumBlue)
                    }
                }

                is UiState.Success -> {
                    // 成功状态，显示设备列表
                    val allDevices = state.data
                    val filteredDevices = filterDevices(allDevices, selectedFilter)

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = ::refreshDeviceList,
                    ) {
                        if (filteredDevices.isEmpty()) {
                            EmptyDeviceList(
                                filter = selectedFilter,
                                onFilterChange = { selectedFilter = it },
                            )
                        } else {
                            DeviceListContent(
                                devices = filteredDevices,
                                onDeviceClick = { device ->
                                    onNavigateToDetail(device.id)
                                },
                                onDeviceRevoke = { device ->
                                    // TODO: 显示确认对话框
                                    viewModel.revokeDevice(device.id.hexStringToByteArray())
                                },
                            )
                        }
                    }
                }

                is UiState.Error -> {
                    // 错误状态
                    ErrorDeviceList(
                        error = state.error,
                        recoverable = state.recoverable,
                        onRetry = { viewModel.loadDeviceList() },
                        onBack = onNavigateBack,
                    )
                }
            }
        }
    }
}

/**
 * 设备列表顶部栏
 *
 * @param onNavigateBack 返回回调
 * @param onFilterChange 过滤器变更回调
 * @param selectedFilter 当前选中的过滤器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceListTopBar(
    onNavigateBack: () -> Unit,
    onFilterChange: (DeviceFilter) -> Unit,
    selectedFilter: DeviceFilter,
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    text = "设备管理",
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
                // 过滤器菜单
                DeviceFilterMenu(
                    selectedFilter = selectedFilter,
                    onFilterChange = onFilterChange,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // 过滤器芯片行
        DeviceFilterChips(
            selectedFilter = selectedFilter,
            onFilterChange = onFilterChange,
        )
    }
}

/**
 * 设备过滤器芯片行
 *
 * @param selectedFilter 当前选中的过滤器
 * @param onFilterChange 过滤器变更回调
 */
@Composable
private fun DeviceFilterChips(
    selectedFilter: DeviceFilter,
    onFilterChange: (DeviceFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeviceFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        text = filter.displayName,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 设备过滤器菜单（桌面端使用）
 *
 * @param selectedFilter 当前选中的过滤器
 * @param onFilterChange 过滤器变更回调
 */
@Composable
private fun DeviceFilterMenu(
    selectedFilter: DeviceFilter,
    onFilterChange: (DeviceFilter) -> Unit,
) {
    // TODO: 实现过滤器下拉菜单（适用于桌面端）
    // 移动端使用 FilterChips，桌面端可以使用 DropdownMenu
}

/**
 * 设备列表内容
 *
 * @param devices 设备列表
 * @param onDeviceClick 设备点击回调
 * @param onDeviceRevoke 设备撤销回调
 */
@Composable
private fun DeviceListContent(
    devices: List<DeviceInfo>,
    onDeviceClick: (DeviceInfo) -> Unit,
    onDeviceRevoke: (DeviceInfo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题
        item {
            Text(
                text = "我的设备 (${devices.size})",
                style = MaterialTheme.typography.titleMedium,
                color = OnDeepSpaceBackground,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // 设备列表
        items(devices, key = { it.id }) { device ->
            DeviceCard(
                device = device,
                onClick = { onDeviceClick(device) },
                onRevoke = onDeviceRevoke,
                showRevokeButton = !device.isThisDevice && device.state != "revoked",
            )
        }

        // 底部间距
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * 空设备列表
 *
 * @param filter 当前过滤器
 * @param onFilterChange 过滤器变更回调
 */
@Composable
private fun EmptyDeviceList(
    filter: DeviceFilter,
    onFilterChange: (DeviceFilter) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = OnSurfaceVariantColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (filter) {
                    DeviceFilter.ALL -> "暂无设备"
                    DeviceFilter.ACTIVE -> "暂无活跃设备"
                    DeviceFilter.DEGRADED -> "暂无降级设备"
                    DeviceFilter.REVOKED -> "暂无撤销设备"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariantColor,
            )

            if (filter != DeviceFilter.ALL) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "尝试切换到其他过滤器",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }
        }
    }
}

/**
 * 错误设备列表
 *
 * @param error 错误信息
 * @param recoverable 是否可恢复
 * @param onRetry 重试回调
 * @param onBack 返回回调
 */
@Composable
private fun ErrorDeviceList(
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
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
 * 根据过滤器过滤设备列表
 *
 * @param devices 所有设备列表
 * @param filter 过滤器
 * @return 过滤后的设备列表
 */
private fun filterDevices(
    devices: List<DeviceInfo>,
    filter: DeviceFilter,
): List<DeviceInfo> {
    return when (filter) {
        DeviceFilter.ALL -> devices
        DeviceFilter.ACTIVE -> devices.filter { it.state == "active" || it.state == "Idle" || it.state == "Decrypting" }
        DeviceFilter.DEGRADED -> devices.filter { it.state == "degraded" || it.state == "Degraded" }
        DeviceFilter.REVOKED -> devices.filter { it.state == "revoked" || it.state == "Revoked" }
    }
}

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
