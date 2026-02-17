package io.aeternum.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import uniffi.aeternum.AeternumEngine
import uniffi.aeternum.DeviceInfo as UniFFIDeviceInfo
import uniffi.aeternum.VaultSession
import uniffi.aeternum.ProtocolState
import io.aeternum.data.VaultRepository
import io.aeternum.security.AndroidSecurityManager
import io.aeternum.security.BiometricAuthResult
import io.aeternum.security.IntegrityResult
import io.aeternum.ui.state.ActiveSubState
import io.aeternum.ui.state.AeternumUiState
import io.aeternum.ui.state.DegradedReason
import io.aeternum.ui.state.RekeyingStage
import io.aeternum.ui.state.RevokedReason
import io.aeternum.ui.state.UiError
import io.aeternum.ui.state.UiEvent
import io.aeternum.ui.state.UiState
import io.aeternum.ui.state.VaultSessionHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * 将 ByteArray 转换为十六进制字符串
 */
private fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }
}

/**
 * 将 ProtocolState 转换为显示字符串
 */
private fun ProtocolState.getStateString(): String {
    return when (this) {
        ProtocolState.IDLE -> "idle"
        ProtocolState.REKEYING -> "rekeying"
        ProtocolState.RECOVERY_INITIATED -> "recovery_initiated"
        ProtocolState.DEGRADED -> "degraded"
        ProtocolState.REVOKED -> "revoked"
    }
}

/**
 * Aeternum 主 ViewModel
 *
 * INVARIANT: 仅持有 Rust 实例句柄，不持有明文密钥
 * 参考：design.md §ViewModel 架构
 *
 * 安全特性：
 * - 自动后台锁定：应用后台 30 秒后自动锁定会话
 * - 生命周期感知：监听应用前后台切换
 * - FLAG_SECURE 支持：防止截屏录屏
 *
 * @property application 应用上下文
 */
class AeternumViewModel(
    application: Application,
) : AndroidViewModel(application), DefaultLifecycleObserver {
    // ========================================================================
    // 常量
    // ========================================================================

    companion object {
        /** 后台自动锁定超时时间（秒） */
        private const val BACKGROUND_LOCK_TIMEOUT_SECONDS = 30L
    }

    // ========================================================================
    // 依赖注入
    // ========================================================================

    private val vaultPath: File
        get() = File(getApplication<Application>().filesDir, "vault")

    private val repository: VaultRepository
        get() = VaultRepository()

    // AndroidSecurityManager 是 object 单例，直接访问

    // ========================================================================
    // UI 状态
    // ========================================================================

    private val _uiState = MutableStateFlow<AeternumUiState>(AeternumUiState.Uninitialized)
    val uiState: StateFlow<AeternumUiState> = _uiState.asStateFlow()

    // ========================================================================
    // 设备状态
    // ========================================================================

    private val _deviceListState = MutableStateFlow<UiState<List<DeviceInfo>>>(UiState.Idle)
    val deviceListState: StateFlow<UiState<List<DeviceInfo>>> = _deviceListState.asStateFlow()

    private val _deviceDetailState = MutableStateFlow<UiState<io.aeternum.ui.devices.DeviceDetailInfo>>(UiState.Idle)
    val deviceDetailState: StateFlow<UiState<io.aeternum.ui.devices.DeviceDetailInfo>> = _deviceDetailState.asStateFlow()

    private val _addDeviceState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val addDeviceState: StateFlow<UiState<String>> = _addDeviceState.asStateFlow()

    private val _currentEpoch = MutableStateFlow<UInt?>(null)
    val currentEpoch: StateFlow<UInt?> = _currentEpoch.asStateFlow()

    // ========================================================================
    // 会话自动锁定
    // ========================================================================

    /** 后台锁定任务 */
    private var backgroundLockJob: Job? = null

    /** 是否在后台 */
    private var isInBackground = false

    /**
     * 生命周期回调
     *
     * 监听应用前后台切换，实现自动锁定
     */
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        isInBackground = false
        // 取消后台锁定任务
        backgroundLockJob?.cancel()
        backgroundLockJob = null
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        isInBackground = true
        // 启动后台锁定计时器
        startBackgroundLockTimer()
    }

    /**
     * 启动后台锁定计时器
     *
     * 如果用户在 30 秒内没有回到应用，自动锁定会话
     */
    private fun startBackgroundLockTimer() {
        backgroundLockJob?.cancel()
        backgroundLockJob = viewModelScope.launch {
            delay(BACKGROUND_LOCK_TIMEOUT_SECONDS.seconds)
            // 仅当当前在 Decrypting 状态时才锁定
            val currentState = _uiState.value
            if (currentState is AeternumUiState.Active &&
                currentState.subState is ActiveSubState.Decrypting) {
                lockSession()
            }
        }
    }

    // ========================================================================
    // 初始化
    // ========================================================================

    init {
        initialize()
    }

    /**
     * 初始化应用状态
     *
     * 检查 Vault 是否存在，决定进入 Onboarding 还是主界面
     */
    private fun initialize() {
        viewModelScope.launch {
            _uiState.value = AeternumUiState.Uninitialized

            // 检查设备完整性
            when (val integrityResult = AndroidSecurityManager.checkDeviceIntegrity()) {
                is IntegrityResult.Success -> {
                    // 设备完整性验证通过
                }
                is IntegrityResult.Failed -> {
                    _uiState.value = AeternumUiState.Degraded(
                        DegradedReason.INTEGRITY_CHECK_FAILED
                    )
                    return@launch
                }
            }

            // 检查 Vault 是否存在
            if (vaultPath.exists()) {
                _uiState.value = AeternumUiState.Active(ActiveSubState.Idle)
            } else {
                _uiState.value = AeternumUiState.Onboarding
            }
        }
    }

    // ========================================================================
    // 初始化流程
    // ========================================================================

    /**
     * 完成欢迎页面，进入助记词备份
     */
    fun completeWelcome() {
        _uiState.value = AeternumUiState.Onboarding
    }

    /**
     * 完成助记词备份，进入设备注册
     */
    fun completeMnemonicBackup() {
        _uiState.value = AeternumUiState.Onboarding
    }

    /**
     * 启动设备注册流程
     *
     * 连接到 AndroidSecurityManager 生成硬件密钥并初始化 Vault
     *
     * INVARIANT: 设备注册必须在 Uninitialized → Initializing → Active 转换中完成
     */
    fun startDeviceRegistration() {
        viewModelScope.launch {
            try {
                // Step 1: 生成硬件密钥 (DK_hardware)
                AndroidSecurityManager.getHardwareKey()
                emitEvent(UiEvent.ShowSnackbar("硬件密钥生成成功"))

                // Step 2: 初始化 Vault
                // TODO: 重构接口 - Rust 端应通过 JNI 直接访问 KeyStore
                // 当前临时使用密钥别名作为标识符
                val keyAlias = "aeternum_dk_hardware".toByteArray()
                val result = repository.initializeVault(keyAlias)
                result.fold(
                    onSuccess = {
                        _uiState.value = AeternumUiState.Active(ActiveSubState.Idle)
                        emitEvent(UiEvent.ShowSnackbar("设备注册成功"))
                    },
                    onFailure = { error ->
                        _uiState.value = AeternumUiState.Error(
                            UiError.StorageError(error.message ?: "设备注册失败"),
                            recoverable = true,
                        )
                    },
                )
            } catch (e: Exception) {
                _uiState.value = AeternumUiState.Error(
                    UiError.UnknownError(
                        message = "设备注册失败",
                        originalError = e.message,
                    ),
                    recoverable = true,
                )
            }
        }
    }

    /**
     * 重试设备注册
     */
    fun retryDeviceRegistration() {
        startDeviceRegistration()
    }

    /**
     * 初始化 Vault
     *
     * @param hardwareKeyBlob 硬件密钥 Blob
     */
    fun initializeVault(hardwareKeyBlob: ByteArray) {
        viewModelScope.launch {
            _uiState.value = AeternumUiState.Active(ActiveSubState.Idle)

            val result = repository.initializeVault(hardwareKeyBlob)
            result.fold(
                onSuccess = {
                    _uiState.value = AeternumUiState.Active(ActiveSubState.Idle)
                    emitEvent(UiEvent.ShowSnackbar("Vault 初始化成功"))
                },
                onFailure = { error ->
                    _uiState.value = AeternumUiState.Error(
                        UiError.StorageError(error.message ?: "初始化失败"),
                        recoverable = true,
                    )
                },
            )
        }
    }

    // ========================================================================
    // 认证流程
    // ========================================================================

    /**
     * 请求生物识别解锁
     *
     * 需要传入 FragmentActivity 以显示生物识别对话框
     *
     * @param activity FragmentActivity 用于显示 BiometricPrompt
     */
    fun requestBiometricUnlock(activity: androidx.fragment.app.FragmentActivity) {
        viewModelScope.launch {
            emitEvent(UiEvent.RequestBiometric)

            val result = AndroidSecurityManager.authenticate(activity)

            when (result) {
                is BiometricAuthResult.Success -> {
                    // 生物识别成功，解锁 Vault
                    unlockVaultWithBiometric()
                }
                is BiometricAuthResult.Failed -> {
                    _uiState.value = AeternumUiState.Error(
                        UiError.AuthError(
                            result.reason,
                            requiresBiometric = true,
                        ),
                        recoverable = true,
                    )
                }
                is BiometricAuthResult.Cancelled -> {
                    // 用户取消，不做任何操作
                }
                is BiometricAuthResult.NotAvailable -> {
                    _uiState.value = AeternumUiState.Error(
                        UiError.AuthError(
                            result.reason,
                            requiresBiometric = true,
                        ),
                        recoverable = false,
                    )
                }
            }
        }
    }

    /**
     * 使用生物识别结果解锁 Vault
     */
    private suspend fun unlockVaultWithBiometric() {
        // 获取硬件密钥
        AndroidSecurityManager.getHardwareKey()

        // TODO: 重构接口 - Rust 端应通过 JNI 直接访问 KeyStore
        // 当前临时使用密钥别名作为标识符
        val keyAlias = "aeternum_dk_hardware".toByteArray()

        // 使用硬件密钥解锁 Vault
        val result = repository.unlockVault(keyAlias)
        result.fold(
            onSuccess = { session ->
                // 获取记录 ID 列表
                val recordIdsResult = repository.listRecordIds()
                recordIdsResult.fold(
                    onSuccess = { recordIds ->
                        // 更新 UI 状态为已解锁
                        _uiState.value = AeternumUiState.Active(
                            ActiveSubState.Decrypting(
                                session = VaultSessionHandle.Native(session),
                                recordIds = recordIds,
                            ),
                        )
                        emitEvent(UiEvent.ShowSnackbar("Vault 解锁成功"))
                    },
                    onFailure = { error ->
                        _uiState.value = AeternumUiState.Error(
                            UiError.DataError(
                                message = "获取记录列表失败: ${error.message}",
                            ),
                            recoverable = true,
                        )
                    },
                )
            },
            onFailure = { error ->
                _uiState.value = AeternumUiState.Error(
                    UiError.AuthError(
                        message = "Vault 解锁失败: ${error.message}",
                        requiresBiometric = true,
                    ),
                    recoverable = true,
                )
            },
        )
    }

    // ========================================================================
    // 主流程
    // ========================================================================

    /**
     * 解密字段
     *
     * INVARIANT: 解密在 Rust 端完成，Kotlin 仅接收脱敏数据
     *
     * @param recordId 记录 ID
     * @param fieldKey 字段键
     */
    suspend fun decryptField(recordId: String, fieldKey: String): Result<String> {
        return repository.retrieveEntry(recordId, fieldKey)
    }

    /**
     * 锁定会话
     *
     * 清除内存中的会话句柄
     */
    fun lockSession() {
        viewModelScope.launch {
            repository.lockVault()
            _uiState.value = AeternumUiState.Active(ActiveSubState.Idle)
            emitEvent(UiEvent.ShowSnackbar("会话已锁定"))
        }
    }

    /**
     * 启动密钥轮换
     */
    fun startRekeying() {
        viewModelScope.launch {
            val current = _currentEpoch.value ?: 0u
            val target = current + 1u

            _uiState.value = AeternumUiState.Active(
                ActiveSubState.Rekeying(
                    currentEpoch = current,
                    targetEpoch = target,
                    progress = 0f,
                    stage = RekeyingStage.PREPARING,
                ),
            )

            // 模拟轮换流程
            simulateRekeying(current, target)
        }
    }

    /**
     * 模拟密钥轮换流程
     *
     * TODO: 替换为实际的 Rust 调用
     */
    private suspend fun simulateRekeying(current: UInt, target: UInt) {
        val stages = listOf(
            RekeyingStage.PREPARING to 0.2f,
            RekeyingStage.ENCRYPTING to 0.5f,
            RekeyingStage.BROADCASTING to 0.7f,
            RekeyingStage.COMMITTING to 0.9f,
            RekeyingStage.FINALIZING to 1.0f,
        )

        for ((stage, progress) in stages) {
            _uiState.value = AeternumUiState.Active(
                ActiveSubState.Rekeying(
                    currentEpoch = current,
                    targetEpoch = target,
                    progress = progress,
                    stage = stage,
                ),
            )
            delay(1000) // 模拟处理时间
        }

        // 轮换完成
        _currentEpoch.value = target
        _uiState.value = AeternumUiState.Active(ActiveSubState.Idle)
        emitEvent(UiEvent.ShowSnackbar("密钥轮换完成，当前纪元: $target"))
    }

    // ========================================================================
    // 设备管理
    // ========================================================================

    /**
     * 加载设备列表
     */
    fun loadDeviceList() {
        viewModelScope.launch {
            _deviceListState.value = UiState.Loading

            val result = repository.getDeviceList()
            result.fold(
                onSuccess = { uniFFIDevices ->
                    // 转换为 UI 层 DeviceInfo（脱敏）
                    val devices = uniFFIDevices.map { sanitizeDeviceInfo(it) }
                    _deviceListState.value = UiState.Success(devices)
                },
                onFailure = { error ->
                    _deviceListState.value = UiState.Error(error.message ?: "获取设备列表失败")
                },
            )
        }
    }

    /**
     * 加载设备详情
     *
     * INVARIANT: 仅返回脱敏后的设备信息
     *
     * @param deviceId 设备 ID（字符串形式）
     */
    fun loadDeviceDetail(deviceId: String) {
        viewModelScope.launch {
            _deviceDetailState.value = UiState.Loading

            // TODO: 通过 bridge 获取实际设备详情
            delay(300)

            // 模拟从设备列表中查找设备
            val bridgeDevice = UniFFIDeviceInfo(
                deviceId = deviceId.toByteArray(),
                deviceName = when {
                    deviceId.contains("device_1") -> "本机"
                    deviceId.contains("device_2") -> "iPad Pro"
                    deviceId.contains("device_3") -> "Pixel 8"
                    else -> "未知设备"
                },
                epoch = 5u,
                state = when {
                    deviceId.contains("device_3") -> ProtocolState.DEGRADED
                    else -> ProtocolState.IDLE
                },
                lastSeenTimestamp = System.currentTimeMillis(),
                isThisDevice = deviceId.contains("device_1"),
            )

            // 转换为设备详情（脱敏）
            val deviceDetail = sanitizeDeviceDetail(bridgeDevice)

            _deviceDetailState.value = UiState.Success(deviceDetail)
        }
    }

    /**
     * 撤销设备
     *
     * @param deviceIdBytes 设备 ID（字节数组）
     */
    fun revokeDevice(deviceIdBytes: ByteArray) {
        viewModelScope.launch {
            val result = repository.revokeDevice(deviceIdBytes)
            result.fold(
                onSuccess = {
                    emitEvent(UiEvent.ShowSnackbar("设备撤销请求已发送"))
                    loadDeviceList() // 刷新列表
                },
                onFailure = { error ->
                    emitEvent(UiEvent.ShowSnackbar("设备撤销失败: ${error.message}"))
                },
            )
        }
    }

    /**
     * 发起添加设备流程
     *
     * 生成握手令牌和 QR 码，等待新设备扫描
     *
     * INVARIANT: 握手令牌由 Rust Core 生成，UI 层仅展示
     */
    fun initiateAddDevice() {
        viewModelScope.launch {
            _addDeviceState.value = UiState.Loading

            // TODO: 通过 bridge 调用 Rust 生成握手令牌
            delay(1000) // 模拟生成时间

            // 模拟生成握手令牌
            val handshakeToken = "aeternum_handshake_${System.currentTimeMillis()}"

            _addDeviceState.value = UiState.Success(handshakeToken)
        }
    }

    /**
     * 脱敏设备信息
     *
     * INVARIANT: 将 UniFFI DeviceInfo 转换为 UI 层 DeviceInfo，确保敏感信息不泄露
     * - ByteArray ID 转换为十六进制字符串
     * - 仅保留必要信息用于显示
     *
     * @param uniFFIDevice UniFFI 层设备信息
     * @return UI 层脱敏设备信息
     */
    private fun sanitizeDeviceInfo(uniFFIDevice: UniFFIDeviceInfo): DeviceInfo {
        return DeviceInfo(
            id = uniFFIDevice.deviceId.copyOfRange(0, minOf(8, uniFFIDevice.deviceId.size)).toHexString() +
                 uniFFIDevice.deviceId.copyOfRange(maxOf(0, uniFFIDevice.deviceId.size - 4), uniFFIDevice.deviceId.size).toHexString(),
            name = uniFFIDevice.deviceName,
            epoch = uniFFIDevice.epoch,
            state = uniFFIDevice.getStateString(),
            isThisDevice = uniFFIDevice.isThisDevice,
        )
    }

    /**
     * 脱敏设备详情
     *
     * INVARIANT: 将 UniFFI DeviceInfo 转换为 UI 层 DeviceDetailInfo，确保敏感信息不泄露
     *
     * @param uniFFIDevice UniFFI 层设备信息
     * @return UI 层脱敏设备详情
     */
    private fun sanitizeDeviceDetail(uniFFIDevice: UniFFIDeviceInfo): io.aeternum.ui.devices.DeviceDetailInfo {
        return io.aeternum.ui.devices.DeviceDetailInfo(
            id = uniFFIDevice.deviceId.toHexString(),
            name = uniFFIDevice.deviceName,
            epoch = uniFFIDevice.epoch,
            state = uniFFIDevice.getStateString(),
            isThisDevice = uniFFIDevice.isThisDevice,
            deviceType = when {
                uniFFIDevice.deviceName.contains("iPad", ignoreCase = true) -> "平板"
                uniFFIDevice.deviceName.contains("Pixel", ignoreCase = true) -> "手机"
                uniFFIDevice.deviceName.contains("Mac", ignoreCase = true) -> "桌面"
                else -> "未知"
            },
            osVersion = "Android 14", // TODO: 从 bridge 获取
            integrityStrong = uniFFIDevice.state != ProtocolState.DEGRADED,
            registeredAt = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L, // 30天前
            lastActiveAt = uniFFIDevice.lastSeenTimestamp, // 使用 UniFFI 提供的时间戳
        )
    }

    /**
     * ByteArray 转十六进制字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    // ========================================================================
    // 恢复流程
    // ========================================================================

    /**
     * 发起恢复
     *
     * @param mnemonic 助记词（将被派生为恢复密钥）
     */
    fun initiateRecovery(mnemonic: String) {
        viewModelScope.launch {
            // TODO: 从助记词派生恢复密钥
            // 目前使用占位符实现
            val result = repository.initiateRecovery()
            result.fold(
                onSuccess = { recoveryId ->
                    emitEvent(UiEvent.ShowSnackbar("恢复请求已发送，ID: $recoveryId"))
                },
                onFailure = { error ->
                    emitEvent(UiEvent.ShowSnackbar("恢复请求失败: ${error.message}"))
                },
            )
        }
    }

    /**
     * 提交否决
     *
     * @param recoveryId 恢复 ID
     */
    fun submitVeto(recoveryId: String) {
        viewModelScope.launch {
            val result = repository.submitVeto(recoveryId)
            result.fold(
                onSuccess = {
                    emitEvent(UiEvent.ShowSnackbar("否决已提交"))
                },
                onFailure = { error ->
                    emitEvent(UiEvent.ShowSnackbar("否决提交失败: ${error.message}"))
                },
            )
        }
    }

    // ========================================================================
    // 事件处理
    // ========================================================================

    private fun emitEvent(event: UiEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<UiEvent>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    val events: kotlinx.coroutines.flow.SharedFlow<UiEvent> = _events

    // ========================================================================
    // 生命周期
    // ========================================================================

    override fun onCleared() {
        super.onCleared()
        // 取消后台锁定任务
        backgroundLockJob?.cancel()
        // 关闭仓库
        repository.close()
    }
}
