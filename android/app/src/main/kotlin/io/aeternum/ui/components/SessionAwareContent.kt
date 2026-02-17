package io.aeternum.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.aeternum.security.SessionManager
import io.aeternum.security.SessionManager.detectUserActivity
import io.aeternum.ui.state.ActiveSubState
import io.aeternum.ui.state.AeternumUiState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * 会话感知内容包装器
 *
 * 为敏感屏幕提供：
 * - 用户活动检测
 * - 会话超时警告
 * - 自动锁定
 *
 * INVARIANT: 仅在 Decrypting 状态启用会话超时逻辑
 *
 * 使用示例：
 * ```kotlin
 * SessionAwareContent(
 *     uiState = uiState,
 *     onSessionTimeout = { viewModel.lockSession() },
 *     onWarning = { seconds -> showTimeoutWarning(seconds) }
 * ) {
 *     VaultContent()
 * }
 * ```
 *
 * @param uiState 当前 UI 状态
 * @param onSessionTimeout 会话超时回调
 * @param onWarning 警告回调（剩余秒数）
 * @param backgroundTimeoutSeconds 后台锁定超时（秒）
 * @param userActivityTimeoutSeconds 用户活动超时（秒）
 * @param showWarningOverlay 是否显示警告覆盖层
 * @param content 屏幕内容
 */
@Composable
fun SessionAwareContent(
    uiState: AeternumUiState,
    onSessionTimeout: () -> Unit,
    onWarning: (secondsRemaining: Long) -> Unit = {},
    backgroundTimeoutSeconds: Long = SessionManager.DEFAULT_BACKGROUND_LOCK_TIMEOUT_SECONDS,
    userActivityTimeoutSeconds: Long = SessionManager.DEFAULT_USER_ACTIVITY_TIMEOUT_SECONDS,
    showWarningOverlay: Boolean = true,
    content: @Composable () -> Unit,
) {
    // 是否在 Decrypting 状态
    val isDecrypting = uiState is AeternumUiState.Active &&
                       uiState.subState is ActiveSubState.Decrypting

    // 警告状态
    var warningSeconds by remember { mutableStateOf<Long?>(null) }

    // 会话状态
    val sessionState = SessionManager.rememberSessionState(
        backgroundTimeoutSeconds = backgroundTimeoutSeconds,
        userActivityTimeoutSeconds = userActivityTimeoutSeconds,
        onTimeout = {
            // 仅在 Decrypting 状态触发锁定
            if (isDecrypting) {
                onSessionTimeout()
            }
        },
        onWarning = { seconds ->
            warningSeconds = seconds
            onWarning(seconds)
        },
    )

    // 监听 Decrypting 状态变化
    LaunchedEffect(isDecrypting) {
        if (!isDecrypting) {
            // 离开 Decrypting 状态时清除警告
            warningSeconds = null
        }
    }

    // 自动清除警告（5秒后）
    LaunchedEffect(warningSeconds) {
        if (warningSeconds != null) {
            delay(5.seconds)
            warningSeconds = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容（带用户活动检测）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isDecrypting) {
                        Modifier.detectUserActivity {
                            sessionState.onUserActivity()
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            content()
        }

        // 超时警告覆盖层
        if (showWarningOverlay && warningSeconds != null && isDecrypting) {
            SessionTimeoutWarningOverlay(
                secondsRemaining = warningSeconds!!,
                onExtend = { warningSeconds = null },
                onLockNow = {
                    sessionState.lockNow()
                },
            )
        }
    }
}

/**
 * 会话超时警告覆盖层
 *
 * 显示倒计时警告，允许用户延长会话或立即锁定
 *
 * @param secondsRemaining 剩余秒数
 * @param onExtend 延长会话回调
 * @param onLockNow 立即锁定回调
 */
@Composable
private fun SessionTimeoutWarningOverlay(
    secondsRemaining: Long,
    onExtend: () -> Unit,
    onLockNow: () -> Unit,
) {
    // 使用自定义警告覆盖层
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error,
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "会话即将在 ${secondsRemaining} 秒后锁定",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 延长按钮
                TextButton(onClick = onExtend) {
                    Text("延长")
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 锁定按钮
                TextButton(onClick = onLockNow) {
                    Text("锁定")
                }
            }
        }
    }
}

/**
 * 简化版会话感知屏幕
 *
 * 用于不需要显示警告覆盖层的场景
 *
 * @param isActive 是否处于活跃状态
 * @param onTimeout 超时回调
 * @param content 屏幕内容
 */
@Composable
fun SimpleSessionAwareContent(
    isActive: Boolean,
    onTimeout: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sessionState = SessionManager.rememberSessionState(
        onTimeout = {
            if (isActive) {
                onTimeout()
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isActive) {
                    Modifier.detectUserActivity {
                        sessionState.onUserActivity()
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}
