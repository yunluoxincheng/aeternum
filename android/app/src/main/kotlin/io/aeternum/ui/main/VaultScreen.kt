package io.aeternum.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.aeternum.security.SessionManager
import io.aeternum.security.SessionManager.detectUserActivity
import io.aeternum.ui.components.ActionButton
import kotlinx.coroutines.launch
import io.aeternum.ui.components.ButtonType
import io.aeternum.ui.components.SecureScreen
import io.aeternum.ui.state.ActiveSubState
import io.aeternum.ui.state.AeternumUiState
import io.aeternum.ui.state.VaultSessionHandle
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.MachineStateColor
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.SurfaceColor
import io.aeternum.ui.viewmodel.AeternumViewModel

/**
 * Aeternum Vault 屏幕（Decrypting 状态）
 *
 * ## 设计理念
 * - **信息层次**: 字段列表 > 操作按钮 > 状态信息
 * - **安全优先**: 每个字段独立解密，明文仅在 Rust 内存中存在
 * - **快速锁定**: 一键锁定会话，清除内存中的密钥
 * - **视觉反馈**: 清晰的字段状态指示（已加密/已解密）
 *
 * ## Decrypting 状态特性
 * - Vault 已通过生物识别解锁
 * - DEK/VK 仅存在 Rust 内存中（mlock + zeroize）
 * - 支持按需解密字段（点击才解密）
 * - 支持会话锁定（返回 Idle 状态）
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示脱敏数据，不持有明文密钥
 * - INVARIANT: 所有解密操作通过 Rust 句柄调用，明文仅在 Rust 内存中
 * - INVARIANT: 锁定会话触发 Rust zeroize，清除内存密钥
 * - 不暴露设备 ID 或敏感标识符
 * - 记录 ID 可显示（非敏感信息）
 *
 * @param viewModel Aeternum ViewModel
 * @param modifier 修饰符
 * @param onNavigateBack 导航返回
 */
@Composable
fun VaultScreen(
    viewModel: AeternumViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    // INVARIANT: Vault 屏幕必须启用 FLAG_SECURE，防止截屏
    SecureScreen {
        // 仅在 Decrypting 状态显示
        when (val state = uiState) {
            is AeternumUiState.Active -> {
                when (val subState = state.subState) {
                    is ActiveSubState.Decrypting -> {
                        VaultContent(
                            session = subState.session,
                            recordIds = subState.recordIds,
                            viewModel = viewModel,
                            modifier = modifier,
                            onNavigateBack = onNavigateBack,
                        )
                    }
                    else -> {
                        // 其他状态不在此屏幕显示
                        InvalidStateContent(subState)
                    }
                }
            }
            else -> {
                // 非 Active 状态不在此屏幕显示
                InvalidUiStateContent(state)
            }
        }
    }
}

/**
 * Vault 主内容
 *
 * ## 安全特性
 * - 集成 SessionManager 管理会话超时
 * - 用户活动检测（触摸、滑动）
 * - 会话超时警告（倒计时提示）
 * - 自动锁定（后台 30 秒 / 前台无活动 5 分钟）
 *
 * @param session Vault 会话句柄
 * @param recordIds 记录 ID 列表
 * @param viewModel Aeternum ViewModel
 * @param modifier 修饰符
 * @param onNavigateBack 导航返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultContent(
    session: VaultSessionHandle,
    recordIds: List<String>,
    viewModel: AeternumViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    // 会话超时警告显示状态
    var showTimeoutWarning by remember { mutableStateOf(false) }
    var timeoutWarningSeconds by remember { mutableStateOf(0L) }

    // 会话超时管理
    val sessionState = SessionManager.rememberSessionState(
        backgroundTimeoutSeconds = SessionManager.DEFAULT_BACKGROUND_LOCK_TIMEOUT_SECONDS,
        userActivityTimeoutSeconds = SessionManager.DEFAULT_USER_ACTIVITY_TIMEOUT_SECONDS,
        onTimeout = {
            // 会话超时，自动锁定
            viewModel.lockSession()
            onNavigateBack()
        },
        onWarning = { secondsRemaining ->
            // 显示超时警告
            showTimeoutWarning = true
            timeoutWarningSeconds = secondsRemaining
        }
    )

    // 用户活动处理
    val handleUserActivity = {
        sessionState.onUserActivity()
        // 隐藏警告（如果有）
        showTimeoutWarning = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopBar(
                onNavigateBack = onNavigateBack,
                onLockSession = {
                    viewModel.lockSession()
                    onNavigateBack()
                },
            )
        },
        containerColor = DeepSpaceBackground,
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    // 刷新记录列表
                    isRefreshing = false
                },
                state = pullToRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    // 检测用户活动（触摸、滑动）
                    .then(Modifier.detectUserActivity { handleUserActivity() }),
            ) {
                if (recordIds.isEmpty()) {
                    EmptyVaultContent(
                        onLockSession = {
                            viewModel.lockSession()
                            onNavigateBack()
                        },
                    )
                } else {
                    VaultFieldsList(
                        recordIds = recordIds,
                        onDecryptField = { recordId, fieldKey ->
                            handleUserActivity()
                            viewModel.decryptField(recordId, fieldKey)
                        },
                    )
                }
            }

            // 会话超时警告横幅
            AnimatedVisibility(
                visible = showTimeoutWarning,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                SessionTimeoutWarning(
                    secondsRemaining = timeoutWarningSeconds,
                    onLockNow = {
                        sessionState.lockNow()
                    },
                )
            }
        }
    }
}

/**
 * 会话超时警告横幅
 *
 * 在会话即将超时时显示警告提示
 *
 * @param secondsRemaining 剩余秒数
 * @param onLockNow 立即锁定回调
 */
@Composable
private fun SessionTimeoutWarning(
    secondsRemaining: Long,
    onLockNow: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 警告图标
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )

            // 警告文本
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "会话即将超时",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "将在 $secondsRemaining 秒后自动锁定",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            // 立即锁定按钮
            ActionButton(
                text = "立即锁定",
                onClick = onLockNow,
                type = ButtonType.Primary,
            )
        }
    }
}

/**
 * Vault 顶部栏
 *
 * @param onNavigateBack 导航返回
 * @param onLockSession 锁定会话
 */
@Composable
private fun VaultTopBar(
    onNavigateBack: () -> Unit = {},
    onLockSession: () -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "保险库",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "已解锁",
                    style = MaterialTheme.typography.bodySmall,
                    color = MachineStateColor.Decrypting.color,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = ImageVector.vectorResource(android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = "关闭",
                    tint = OnDeepSpaceBackground,
                )
            }
        },
        actions = {
            // 锁定按钮
            IconButton(
                onClick = onLockSession,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "锁定会话",
                    tint = QuantumBlue,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SurfaceColor,
            titleContentColor = OnDeepSpaceBackground,
            navigationIconContentColor = OnDeepSpaceBackground,
        ),
    )
}

/**
 * Vault 字段列表
 *
 * @param recordIds 记录 ID 列表
 * @param onDecryptField 解密字段回调
 */
@Composable
private fun VaultFieldsList(
    recordIds: List<String>,
    onDecryptField: suspend (String, String) -> Result<String>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题
        item {
            Column {
                Text(
                    text = "存储的记录",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDeepSpaceBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${recordIds.size} 条记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }
        }

        // 记录列表
        items(recordIds) { recordId ->
            VaultFieldCard(
                recordId = recordId,
                onDecryptField = onDecryptField,
            )
        }

        // 底部间距
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Vault 字段卡片
 *
 * 显示单个记录的字段，支持点击解密。
 *
 * ## 架构约束
 * - INVARIANT: 解密操作通过 Rust 句柄完成
 * - INVARIANT: 明文仅在 Rust 内存中，不传递到 Kotlin 层
 * - 支持显示/隐藏切换（在 UI 层）
 *
 * @param recordId 记录 ID
 * @param onDecryptField 解密字段回调
 */
@Composable
private fun VaultFieldCard(
    recordId: String,
    onDecryptField: suspend (String, String) -> Result<String>,
) {
    var isDecrypted by remember { mutableStateOf(false) }
    var isContentVisible by remember { mutableStateOf(false) }
    var fieldKey by remember { mutableStateOf("password") }
    var decryptedValue by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 记录 ID
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recordId,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnDeepSpaceBackground,
                        fontWeight = FontWeight.Medium,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                    Text(
                        text = if (isDecrypted) "已解密" else "已加密",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDecrypted) {
                            MachineStateColor.Decrypting.color
                        } else {
                            OnSurfaceVariantColor
                        },
                    )
                }

                // 解密/显示按钮
                if (isDecrypted) {
                    IconButton(
                        onClick = {
                            isContentVisible = !isContentVisible
                        },
                    ) {
                        Icon(
                            imageVector = if (isContentVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = if (isContentVisible) "隐藏" else "显示",
                            tint = QuantumBlue,
                        )
                    }
                }
            }

            // 解密内容区域
            AnimatedVisibility(
                visible = isDecrypted,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 字段内容
                    Surface(
                        color = DeepSpaceBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isContentVisible && decryptedValue != null) {
                            Text(
                                text = decryptedValue ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnDeepSpaceBackground,
                                modifier = Modifier.padding(12.dp),
                            )
                        } else if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "解密失败",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "•".repeat(20),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariantColor,
                                    letterSpacing = 0.1.sp,
                                )
                            }
                        }
                    }
                }
            }

            // 解密按钮
            if (!isDecrypted) {
                Spacer(modifier = Modifier.height(12.dp))

                ActionButton(
                    text = "解密",
                    onClick = {
                        coroutineScope.launch {
                            val result = onDecryptField(recordId, fieldKey)
                            result.onSuccess { value ->
                                decryptedValue = value
                                isDecrypted = true
                                isContentVisible = true
                                errorMessage = null
                            }.onFailure { error ->
                                errorMessage = error.message ?: "解密失败"
                                isDecrypted = false
                            }
                        }
                    },
                    type = ButtonType.Primary,
                    fullWidth = true,
                )
            }
        }
    }
}

/**
 * 空 Vault 内容
 *
 * @param onLockSession 锁定会话
 */
@Composable
private fun EmptyVaultContent(
    onLockSession: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 空状态图标
        Surface(
            color = QuantumBlue.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(120.dp),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(android.R.drawable.ic_menu_info_details),
                contentDescription = null,
                tint = QuantumBlue,
                modifier = Modifier
                    .size(60.dp)
                    .padding(16.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "保险库为空",
            style = MaterialTheme.typography.titleLarge,
            color = OnDeepSpaceBackground,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "您还没有添加任何记录",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantColor,
        )

        Spacer(modifier = Modifier.height(24.dp))

        ActionButton(
            text = "锁定会话",
            onClick = onLockSession,
            type = ButtonType.Secondary,
        )
    }
}

/**
 * 无效状态内容
 *
 * @param subState 活跃子状态
 */
@Composable
private fun InvalidStateContent(
    subState: ActiveSubState,
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
                text = "状态错误",
                style = MaterialTheme.typography.headlineMedium,
                color = OnDeepSpaceBackground,
            )
            Text(
                text = "当前状态: ${when (subState) {
                    is ActiveSubState.Idle -> "空闲"
                    is ActiveSubState.Decrypting -> "已解锁"
                    is ActiveSubState.Rekeying -> "密钥轮换中"
                }}",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantColor,
            )
        }
    }
}

/**
 * 无效 UI 状态内容
 *
 * @param uiState UI 状态
 */
@Composable
private fun InvalidUiStateContent(
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
                text = "无效的 UI 状态",
                style = MaterialTheme.typography.headlineMedium,
                color = OnDeepSpaceBackground,
            )
        }
    }
}

// ============================================================================
// 预览
// ============================================================================

/**
 * VaultFieldCard 预览
 */
@Composable
private fun VaultFieldCardPreview() {
    val sampleDecrypt: suspend (String, String) -> Result<String> = { _, _ -> Result.success("example_password_123") }
    VaultFieldCard(
        recordId = "Gmail 账户",
        onDecryptField = sampleDecrypt,
    )
}
