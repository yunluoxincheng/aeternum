package io.aeternum.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// 导入 Compose 的 collectAsState
import androidx.compose.runtime.collectAsState

// ============================================================================
// StateFlow 扩展函数
// ============================================================================

/**
 * 将 StateFlow 转换为 Compose State
 *
 * @param stateFlow 源 StateFlow
 * @return Compose State
 */
@Composable
fun <T> rememberStateFlowState(stateFlow: StateFlow<T>): T {
    return stateFlow.collectAsState().value
}

/**
 * 收集 StateFlow 并在生命周期变化时处理
 *
 * @param stateFlow 源 StateFlow
 * @param lifecycle 目标生命周期
 * @param onValue 值变化回调
 */
@Composable
fun <T> CollectStateFlowWithLifecycle(
    stateFlow: StateFlow<T>,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    onValue: (T) -> Unit,
) {
    val currentLifecycle = lifecycle

    LaunchedEffect(stateFlow, currentLifecycle) {
        // 简化版本，不使用 repeatOnLifecycle
        stateFlow.collect { value ->
            onValue(value)
        }
    }
}

// ============================================================================
// Flow 扩展函数
// ============================================================================

/**
 * 将 Flow 转换为 StateFlow
 *
 * @param scope 协程作用域
 * @param initialValue 初始值
 * @param started 共享策略
 * @return StateFlow
 */
fun <T> Flow<T>.asStateFlowIn(
    scope: kotlinx.coroutines.CoroutineScope,
    initialValue: T,
    started: SharingStarted = SharingStarted.Eagerly,
): StateFlow<T> {
    return this.stateIn(
        scope = scope,
        started = started,
        initialValue = initialValue,
    )
}

/**
 * 将 Flow 转换为 SharedFlow
 *
 * @param scope 协程作用域
 * @param replay 重放数量
 * @param onBufferOverflow 缓冲区溢出策略
 * @return SharedFlow
 */
fun <T> Flow<T>.asSharedFlowIn(
    scope: kotlinx.coroutines.CoroutineScope,
    replay: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
): SharedFlow<T> {
    val sharedFlow = MutableSharedFlow<T>(
        replay = replay,
        onBufferOverflow = onBufferOverflow,
    )

    scope.launch {
        this@asSharedFlowIn.collect { value ->
            sharedFlow.emit(value)
        }
    }

    return sharedFlow
}

// ============================================================================
// UiState 封装
// ============================================================================

/**
 * 通用 UI 状态封装
 *
 * 用于表示异步操作的状态
 */
sealed class UiState<out T> {
    /**
     * 空闲状态
     */
    data object Idle : UiState<Nothing>()

    /**
     * 加载状态
     */
    data object Loading : UiState<Nothing>()

    /**
     * 成功状态
     *
     * @property data 成功数据
     */
    data class Success<T>(val data: T) : UiState<T>()

    /**
     * 错误状态
     *
     * @property error 错误信息
     * @property recoverable 是否可恢复
     */
    data class Error(val error: String, val recoverable: Boolean = false) : UiState<Nothing>()

    /**
     * 检查是否成功
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * 检查是否加载中
     */
    val isLoading: Boolean
        get() = this is Loading

    /**
     * 检查是否错误
     */
    val isError: Boolean
        get() = this is Error

    /**
     * 获取数据
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    /**
     * 获取数据或默认值
     */
    fun getOrElse(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> defaultValue
    }

    /**
     * 映射数据
     */
    fun <R> map(transform: (T) -> R): UiState<R> = when (this) {
        is Success -> Success(transform(data))
        is Idle -> Idle
        is Loading -> Loading
        is Error -> Error(error, recoverable)
    }

    companion object {
        /**
         * 从可空值创建 UiState
         */
        fun <T> fromNullable(value: T?): UiState<T> = if (value != null) {
            Success(value)
        } else {
            Error("值为空", recoverable = false)
        }
    }
}

// ============================================================================
// 事件流
// ============================================================================

/**
 * 单次事件
 *
 * 用于表示只应消费一次的事件（如导航、提示消息）
 */
sealed class UiEvent {
    /**
     * 导航事件
     *
     * @property route 目标路由
     * @property args 路由参数
     */
    data class Navigate(val route: String, val args: Map<String, String> = emptyMap()) : UiEvent()

    /**
     * 返回事件
     */
    data object NavigateBack : UiEvent()

    /**
     * 提示消息事件
     *
     * @property message 消息内容
     * @property duration 显示时长
     */
    data class ShowSnackbar(
        val message: String,
        val duration: SnackbarDuration = SnackbarDuration.Short,
    ) : UiEvent()

    /**
     * 对话框事件
     *
     * @property title 标题
     * @property message 消息
     * @property confirmText 确认文本
     * @property onConfirm 确认回调
     */
    data class ShowDialog(
        val title: String,
        val message: String,
        val confirmText: String = "确认",
        val onConfirm: () -> Unit,
    ) : UiEvent()

    /**
     * 生物识别请求事件
     */
    data object RequestBiometric : UiEvent()

    /**
     * 会话锁定事件
     */
    data object LockSession : UiEvent()

    /**
     * 清除事件
     */
    data object Clear : UiEvent()
}

/**
 * Snackbar 显示时长
 */
enum class SnackbarDuration {
    /**
     * 短时间（约 2 秒）
     */
    Short,

    /**
     * 长时间（约 4 秒）
     */
    Long,

    /**
     * 永久（直到用户手动关闭）
     */
    Indefinite,
}

// ============================================================================
// ViewModel 辅助类
// ============================================================================

/**
 * ViewModel 状态管理辅助类
 *
 * 提供常用状态管理功能
 */
abstract class AeternumViewModel : ViewModel() {
    /**
     * 处理 UI 事件
     */
    protected fun handleEvent(event: UiEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    /**
     * 导航到指定路由
     */
    protected fun navigate(route: String, args: Map<String, String> = emptyMap()) {
        handleEvent(UiEvent.Navigate(route, args))
    }

    /**
     * 返回上一页
     */
    protected fun navigateBack() {
        handleEvent(UiEvent.NavigateBack)
    }

    /**
     * 显示提示消息
     */
    protected fun showSnackbar(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        handleEvent(UiEvent.ShowSnackbar(message, duration))
    }

    /**
     * 请求生物识别
     */
    protected fun requestBiometric() {
        handleEvent(UiEvent.RequestBiometric)
    }

    /**
     * 锁定会话
     */
    protected fun lockSession() {
        handleEvent(UiEvent.LockSession)
    }

    /**
     * 事件流
     */
    private val _events = MutableSharedFlow<UiEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * 事件流（只读）
     */
    val events: SharedFlow<UiEvent> = _events

    /**
     * 清除事件
     */
    fun clearEvents() {
        viewModelScope.launch {
            _events.emit(UiEvent.Clear)
        }
    }
}

// ============================================================================
// 会话超时管理
// ============================================================================

/**
 * 会话超时管理器
 *
 * 管理解密会话的超时逻辑
 */
class SessionTimeoutManager(
    private val timeout: Duration = 30.seconds,
    private val onTimeout: () -> Unit,
) {
    private var lastActivityTime = System.currentTimeMillis()
    private var isMonitoring = false

    /**
     * 检查会话是否超时
     */
    fun checkTimeout() {
        val elapsed = System.currentTimeMillis() - lastActivityTime
        if (elapsed > timeout.inWholeMilliseconds) {
            onTimeout()
        }
    }

    /**
     * 更新活动时间
     */
    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    /**
     * 重置超时计时器
     */
    fun reset() {
        lastActivityTime = System.currentTimeMillis()
    }

    /**
     * 停止监控
     */
    fun stopMonitoring() {
        isMonitoring = false
    }

    /**
     * 开始监控
     */
    fun startMonitoring() {
        isMonitoring = true
    }
}

// ============================================================================
// 使用示例
// ============================================================================

/**
 * 使用示例：
 *
 * ```kotlin
 * class MyViewModel : AeternumViewModel() {
 *     private val _uiState = MutableStateFlow<UiState<Data>>(UiState.Idle)
 *     val uiState: StateFlow<UiState<Data>> = _uiState.asStateFlow()
 *
 *     fun loadData() {
 *         viewModelScope.launch {
 *             _uiState.value = UiState.Loading
 *             try {
 *                 val data = repository.fetchData()
 *                 _uiState.value = UiState.Success(data)
 *             } catch (e: Exception) {
 *                 _uiState.value = UiState.Error(e.message ?: "未知错误")
 *             }
 *         }
 *     }
 * }
 *
 * @Composable
 * fun MyScreen(viewModel: MyViewModel = viewModel()) {
 *     val uiState by viewModel.uiState.collectAsState()
 *
 *     when (uiState) {
 *         is UiState.Loading -> LoadingIndicator()
 *         is UiState.Success -> DataContent((uiState as UiState.Success<Data>).data)
 *         is UiState.Error -> ErrorMessage((uiState as UiState.Error).message)
 *         is UiState.Idle -> Unit
 *     }
 * }
 * ```
 */
