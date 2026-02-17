package io.aeternum.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isPressed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * 会话超时管理器
 *
 * 负责管理会话超时逻辑，包括：
 * - 后台自动锁定（默认 30 秒，可配置）
 * - 用户活动检测（触摸、滑动）
 * - 可配置的超时时间
 * - 会话倒计时提示
 *
 * INVARIANT: 会话超时仅影响 Decrypting 状态，Idle 状态不受影响
 *
 * 使用示例：
 * ```kotlin
 * // 检测用户活动
 * Modifier.detectUserActivity { onUserActivity() }
 *
 * // 生命周期感知
 * val sessionState = rememberSessionState(
 *     onTimeout = { lockSession() },
 *     onWarning = { secondsLeft -> showWarning(secondsLeft) }
 * )
 * ```
 */
object SessionManager {

    /** 默认后台锁定超时时间（秒） */
    const val DEFAULT_BACKGROUND_LOCK_TIMEOUT_SECONDS = 30L

    /** 默认用户活动超时时间（秒） */
    const val DEFAULT_USER_ACTIVITY_TIMEOUT_SECONDS = 300L // 5 分钟

    /** 默认显示警告的时间（秒） */
    const val WARNING_THRESHOLD_SECONDS = 10L

    /**
     * 创建用户活动检测修饰符
     *
     * 应用于根布局或主要屏幕以检测用户活动（触摸、点击、滑动）
     *
     * INVARIANT: 仅在 Decrypting 状态启用活动检测
     *
     * @param onActivity 用户活动回调
     */
    @Composable
    fun Modifier.detectUserActivity(
        onActivity: () -> Unit,
    ): Modifier = composed {
        val scope = rememberCoroutineScope()
        var debounceJob: Job? = null

        // 简化版：使用 pointerInput 检测触摸事件
        Modifier.pointerInput(Unit) {
            // 检测任何触摸事件作为用户活动
            // 当用户触摸屏幕时，pointerInput 会触发
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(500) // 500ms 防抖
                onActivity()
            }
        }
    }

    /**
     * 会话状态 Holder
     *
     * 管理会话超时计时器，支持后台和活动超时
     */
    @Composable
    fun rememberSessionState(
        backgroundTimeoutSeconds: Long = DEFAULT_BACKGROUND_LOCK_TIMEOUT_SECONDS,
        userActivityTimeoutSeconds: Long = DEFAULT_USER_ACTIVITY_TIMEOUT_SECONDS,
        onTimeout: () -> Unit,
        onWarning: (secondsRemaining: Long) -> Unit = {},
    ): SessionState {
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()

        val state = rememberSessionStateHolder(
            backgroundTimeoutSeconds = backgroundTimeoutSeconds,
            userActivityTimeoutSeconds = userActivityTimeoutSeconds,
            onTimeout = onTimeout,
            onWarning = onWarning,
        )

        // 监听生命周期变化（前台/后台切换）
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        state.onAppBackgrounded()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        state.onAppForegrounded()
                    }
                    else -> {}
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                state.cleanup()
            }
        }

        return state
    }

    /**
     * 会话状态
     *
     * 提供会话超时管理接口
     */
    interface SessionState {
        /** 用户活动事件（触摸、滑动等） */
        fun onUserActivity()

        /** 应用进入后台 */
        fun onAppBackgrounded()

        /** 应用进入前台 */
        fun onAppForegrounded()

        /** 手动锁定会话 */
        fun lockNow()

        /** 清理资源 */
        fun cleanup()
    }

    /**
     * 记住会话状态 Holder
     */
    @Composable
    private fun rememberSessionStateHolder(
        backgroundTimeoutSeconds: Long,
        userActivityTimeoutSeconds: Long,
        onTimeout: () -> Unit,
        onWarning: (secondsRemaining: Long) -> Unit,
    ): SessionState {
        val scope = rememberCoroutineScope()

        return androidx.compose.runtime.remember {
            SessionStateImpl(
                scope = scope,
                backgroundTimeoutSeconds = backgroundTimeoutSeconds,
                userActivityTimeoutSeconds = userActivityTimeoutSeconds,
                onTimeout = onTimeout,
                onWarning = onWarning,
            )
        }
    }

    /**
     * 会话状态实现
     */
    private class SessionStateImpl(
        private val scope: kotlinx.coroutines.CoroutineScope,
        private val backgroundTimeoutSeconds: Long,
        private val userActivityTimeoutSeconds: Long,
        private val onTimeout: () -> Unit,
        private val onWarning: (secondsRemaining: Long) -> Unit,
    ) : SessionState {

        private var userActivityJob: Job? = null
        private var backgroundJob: Job? = null
        private var warningJob: Job? = null

        override fun onUserActivity() {
            resetUserActivityTimer()
        }

        override fun onAppBackgrounded() {
            // 应用进入后台，启动后台计时器
            startBackgroundTimer()
            // 取消用户活动计时器（后台不需要检测活动）
            userActivityJob?.cancel()
        }

        override fun onAppForegrounded() {
            // 应用进入前台，取消后台计时器
            backgroundJob?.cancel()
            // 启动用户活动计时器
            resetUserActivityTimer()
        }

        override fun lockNow() {
            cleanup()
            onTimeout()
        }

        override fun cleanup() {
            userActivityJob?.cancel()
            backgroundJob?.cancel()
            warningJob?.cancel()
        }

        private fun startBackgroundTimer() {
            backgroundJob?.cancel()
            warningJob?.cancel()

            backgroundJob = scope.launch {
                delay(backgroundTimeoutSeconds.seconds)
                onTimeout()
            }

            // 警告计时器
            if (backgroundTimeoutSeconds > WARNING_THRESHOLD_SECONDS) {
                warningJob = scope.launch {
                    delay((backgroundTimeoutSeconds - WARNING_THRESHOLD_SECONDS).seconds)
                    onWarning(WARNING_THRESHOLD_SECONDS)
                }
            }
        }

        private fun resetUserActivityTimer() {
            userActivityJob?.cancel()
            warningJob?.cancel()

            userActivityJob = scope.launch {
                delay(userActivityTimeoutSeconds.seconds)
                onTimeout()
            }

            // 警告计时器
            if (userActivityTimeoutSeconds > WARNING_THRESHOLD_SECONDS) {
                warningJob = scope.launch {
                    delay((userActivityTimeoutSeconds - WARNING_THRESHOLD_SECONDS).seconds)
                    onWarning(WARNING_THRESHOLD_SECONDS)
                }
            }
        }
    }
}
