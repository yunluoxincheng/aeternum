package io.aeternum.ui.state

import uniffi.aeternum.VaultSession
import io.aeternum.security.NoPlaintextKey
import io.aeternum.security.SecurityBoundary
import io.aeternum.security.Boundary

/**
 * Aeternum UI 状态定义
 *
 * INVARIANT: 类型安全的状态表示，编译时穷举检查
 * 参考：design.md §状态管理架构
 */

/**
 * Vault 会话句柄
 *
 * INVARIANT: 此句柄不持有明文密钥，仅持有 Rust 端实例的引用
 * 所有解密操作通过句柄委托到 Rust 端执行
 */
@SecurityBoundary(
    boundary = Boundary.BRIDGE_LAYER,
    constraints = [
        "句柄不持有明文密钥",
        "解密操作委托到 Rust 端",
        "lock() 触发 Rust 端 zeroize",
    ]
)
sealed class VaultSessionHandle {
    /**
     * 原生 UniFFI 会话句柄
     *
     * INVARIANT: 包装 UniFFI 生成的 VaultSession，提供类型安全的句柄访问
     * 不持有明文密钥，仅持有 Rust 端实例的引用
     */
    data class Native(val session: VaultSession) : VaultSessionHandle()

    /**
     * 占位符实现（用于测试和开发）
     *
     * INVARIANT: 不持有明文密钥
     */
    data object Placeholder : VaultSessionHandle()
}

// ============================================================================
// 主 UI 状态
// ============================================================================

/**
 * Aeternum 主 UI 状态
 *
 * 表示应用的整体状态，映射到底层状态机
 */
sealed class AeternumUiState {
    /**
     * 未初始化状态
     *
     * 应用首次启动，尚未检测到 Vault
     */
    data object Uninitialized : AeternumUiState()

    /**
     * 初始化流程中
     *
     * 用户正在完成欢迎、助记词备份、设备注册流程
     */
    data object Onboarding : AeternumUiState()

    /**
     * 活跃状态
     *
     * 设备已注册且处于正常状态，包含子状态
     *
     * @property subState 活跃子状态（Idle/Decrypting/Rekeying）
     */
    data class Active(val subState: ActiveSubState) : AeternumUiState()

    /**
     * 降级状态
     *
     * 设备完整性验证失败，处于只读安全模式
     *
     * @property reason 降级原因
     */
    data class Degraded(val reason: DegradedReason) : AeternumUiState()

    /**
     * 撤销状态
     *
     * 设备已被撤销，所有密钥和数据已清除
     *
     * @property reason 撤销原因
     */
    data class Revoked(val reason: RevokedReason) : AeternumUiState()

    /**
     * 错误状态
     *
     * 发生不可恢复的错误
     *
     * @property error 错误信息
     * @property recoverable 是否可恢复
     */
    data class Error(val error: UiError, val recoverable: Boolean = false) : AeternumUiState()
}

// ============================================================================
// 活跃子状态
// ============================================================================

/**
 * 活跃子状态
 *
 * 表示 Active 状态下的具体子状态
 */
sealed class ActiveSubState {
    /**
     * 空闲状态
     *
     * Vault 已锁定，等待用户解锁
     */
    data object Idle : ActiveSubState()

    /**
     * 解密状态
     *
     * Vault 已解锁，可以访问加密数据
     *
     * INVARIANT: session 是 Rust 句柄，不持有明文密钥
     *
     * @property session Vault 会话句柄
     * @property recordIds 可用的记录 ID 列表（脱敏）
     */
    data class Decrypting(
        val session: VaultSessionHandle,
        val recordIds: List<String>,
    ) : ActiveSubState()

    /**
     * 轮换状态
     *
     * 正在执行 PQRR 密钥轮换
     *
     * @property currentEpoch 当前纪元
     * @property targetEpoch 目标纪元
     * @property progress 进度 (0.0 - 1.0)
     * @property stage 当前阶段
     */
    data class Rekeying(
        val currentEpoch: UInt,
        val targetEpoch: UInt,
        val progress: Float,
        val stage: RekeyingStage,
    ) : ActiveSubState()
}

// ============================================================================
// 轮换阶段
// ============================================================================

/**
 * 密钥轮换阶段
 *
 * 表示 PQRR 轮换的具体阶段
 */
enum class RekeyingStage {
    /**
     * 准备阶段
     *
     * 生成新密钥材料
     */
    PREPARING,

    /**
     * 加密阶段
     *
     * 使用新密钥重新加密数据
     */
    ENCRYPTING,

    /**
     * 广播阶段
     *
     * 将新 Header 广播到其他设备
     */
    BROADCASTING,

    /**
     * 提交阶段
     *
     * 原子提交新纪元
     */
    COMMITTING,

    /**
     * 完成阶段
     *
     * 清理旧密钥材料
     */
    FINALIZING,
}

// ============================================================================
// 降级原因
// ============================================================================

/**
 * 降级原因
 *
 * 设备进入降级模式的原因
 */
sealed class DegradedReason {
    /** Play Integrity 验证失败 */
    data object INTEGRITY_CHECK_FAILED : DegradedReason()

    /** 网络连接问题 */
    data object NETWORK_UNAVAILABLE : DegradedReason()

    /** 纪元冲突 */
    data object EPOCH_CONFLICT : DegradedReason()

    /** 存储错误 */
    data object STORAGE_ERROR : DegradedReason()

    /** 生物识别不可用 */
    data object BIOMETRIC_UNAVAILABLE : DegradedReason()

    /** 其他原因 */
    data class OTHER(val message: String = "未知原因") : DegradedReason()
}

// ============================================================================
// 撤销原因
// ============================================================================

/**
 * 撤销原因
 *
 * 设备被撤销的原因
 */
sealed class RevokedReason {
    /**
     * 被其他设备撤销
     *
     * @property deviceId 发起撤销的设备 ID
     */
    data class REVOKED_BY_ANOTHER_DEVICE(val deviceId: String? = null) : RevokedReason()

    /** 纪元回滚检测 */
    data object EPOCH_ROLLBACK_DETECTED : RevokedReason()

    /** 否决权超时 */
    data object VETO_TIMEOUT : RevokedReason()

    /** 密钥泄漏 */
    data object KEY_COMPROMISED : RevokedReason()

    /** 用户主动请求 */
    data object USER_INITIATED : RevokedReason()

    /** 其他原因 */
    data class OTHER(val message: String = "未知原因") : RevokedReason()
}

// ============================================================================
// UI 错误定义
// ============================================================================

/**
 * UI 错误定义
 *
 * 表示 UI 层可感知的错误
 */
sealed class UiError {
    /**
     * 纪元错误
     *
     * 纪元版本冲突
     *
     * @property message 错误消息
     * @property currentEpoch 当前纪元
     * @property expectedEpoch 期望纪元
     */
    data class EpochError(
        val message: String,
        val currentEpoch: UInt,
        val expectedEpoch: UInt,
    ) : UiError()

    /**
     * 数据错误
     *
     * 数据不完整或损坏
     *
     * @property message 错误消息
     * @property missingFields 缺失的字段列表
     */
    data class DataError(
        val message: String,
        val missingFields: List<String> = emptyList(),
    ) : UiError()

    /**
     * 认证错误
     *
     * 生物识别或设备认证失败
     *
     * @property message 错误消息
     * @property requiresBiometric 是否需要生物识别
     */
    data class AuthError(
        val message: String,
        val requiresBiometric: Boolean = true,
    ) : UiError()

    /**
     * 否决错误
     *
     * 操作被其他设备否决
     *
     * @property message 错误消息
     * @property vetoingDevice 否决设备名称
     * @property remainingWindow 剩余否决窗口
     */
    data class VetoError(
        val message: String,
        val vetoingDevice: String,
        val remainingWindow: kotlin.time.Duration,
    ) : UiError()

    /**
     * 状态错误
     *
     * 无效的状态转换
     *
     * @property message 错误消息
     * @property currentState 当前状态
     * @property attemptedTransition 尝试的转换
     */
    data class StateError(
        val message: String,
        val currentState: String,
        val attemptedTransition: String,
    ) : UiError()

    /**
     * 存储错误
     *
     * 存储操作失败
     *
     * @property message 错误消息
     * @property availableSpace 可用空间（字节）
     */
    data class StorageError(
        val message: String,
        val availableSpace: Long? = null,
    ) : UiError()

    /**
     * 网络错误
     *
     * 网络连接问题
     *
     * @property message 错误消息
     * @property isOffline 是否离线
     */
    data class NetworkError(
        val message: String,
        val isOffline: Boolean = true,
    ) : UiError()

    /**
     * 未知错误
     *
     * 未分类的错误
     *
     * @property message 错误消息
     * @property originalError 原始错误信息
     */
    data class UnknownError(
        val message: String,
        val originalError: String? = null,
    ) : UiError()
}

// ============================================================================
// UI 状态辅助函数
// ============================================================================

/**
 * 获取状态的显示名称
 */
fun AeternumUiState.getDisplayName(): String {
    return when (this) {
        is AeternumUiState.Uninitialized -> "未初始化"
        is AeternumUiState.Onboarding -> "初始化中"
        is AeternumUiState.Active -> when (subState) {
            is ActiveSubState.Idle -> "空闲"
            is ActiveSubState.Decrypting -> "已解锁"
            is ActiveSubState.Rekeying -> "密钥轮换中"
        }
        is AeternumUiState.Degraded -> "降级模式"
        is AeternumUiState.Revoked -> "已撤销"
        is AeternumUiState.Error -> "错误"
    }
}

/**
 * 检查状态是否允许用户操作
 */
fun AeternumUiState.allowUserActions(): Boolean {
    return when (this) {
        is AeternumUiState.Active -> true
        is AeternumUiState.Onboarding -> true
        is AeternumUiState.Degraded -> false
        is AeternumUiState.Revoked -> false
        is AeternumUiState.Error -> recoverable
        is AeternumUiState.Uninitialized -> false
    }
}

/**
 * 检查是否需要显示警告
 */
fun AeternumUiState.needsWarning(): Boolean {
    return when (this) {
        is AeternumUiState.Degraded -> true
        is AeternumUiState.Revoked -> true
        is AeternumUiState.Error -> true
        is AeternumUiState.Active -> subState is ActiveSubState.Rekeying
        else -> false
    }
}
