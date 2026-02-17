package io.aeternum.data

import uniffi.aeternum.AeternumEngine
import uniffi.aeternum.DeviceInfo as UniFFIDeviceInfo
import uniffi.aeternum.PqrrException
import uniffi.aeternum.VaultSession
import uniffi.aeternum.ProtocolState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Vault 仓库
 *
 * 管理 Vault 的状态和操作
 *
 * INVARIANT: 所有密码学操作在 Rust 端完成
 * INVARIANT: Kotlin 层仅持有 Rust 实例句柄
 */
class VaultRepository {
    // UniFFI 生成的引擎实例（延迟创建）
    private var _engine: AeternumEngine? = null

    // 获取引擎实例，如果不存在则创建
    private val engine: AeternumEngine
        get() = _engine ?: createEngine()

    // 当前活跃的会话
    private var activeSession: VaultSession? = null

    /**
     * 创建引擎实例
     */
    private fun createEngine(): AeternumEngine {
        val newEngine = AeternumEngine(noHandle = uniffi.aeternum.NoHandle)
        _engine = newEngine
        return newEngine
    }

    /**
     * 初始化新 Vault
     *
     * 使用硬件密钥初始化 Vault（首次使用）
     *
     * @param hardwareKeyBlob 硬件密钥 Blob（从 StrongBox 获取）
     */
    suspend fun initializeVault(hardwareKeyBlob: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            engine.initializeVault(hardwareKeyBlob)
            Result.success(Unit)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解锁 Vault
     *
     * 使用硬件密钥解锁 Vault，返回会话句柄
     *
     * @param hardwareKeyBlob 硬件密钥 Blob（从 StrongBox 获取）
     */
    suspend fun unlockVault(hardwareKeyBlob: ByteArray): Result<VaultSession> = withContext(Dispatchers.IO) {
        try {
            val session = engine.unlock(hardwareKeyBlob)
            activeSession = session
            Result.success(session)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 锁定 Vault
     *
     * 关闭当前会话并清除内存中的密钥
     */
    fun lockVault() {
        activeSession?.lock()
        activeSession = null
    }

    /**
     * 存储条目
     *
     * 加密并存储条目到 Vault
     *
     * @param recordId 记录 ID
     * @param fieldKey 字段键
     * @param plaintextValue 明文值（将在 Rust 端加密）
     */
    suspend fun storeEntry(
        recordId: String,
        fieldKey: String,
        plaintextValue: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = activeSession
                ?: return@withContext Result.failure(
                    IllegalStateException("Vault is locked. No active session.")
                )
            session.storeEntry(recordId, fieldKey, plaintextValue)
            Result.success(Unit)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 检索条目
     *
     * 从 Vault 中检索并解密条目
     *
     * @param recordId 记录 ID
     * @param fieldKey 字段键
     */
    suspend fun retrieveEntry(
        recordId: String,
        fieldKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val session = activeSession
                ?: return@withContext Result.failure(
                    IllegalStateException("Vault is locked. No active session.")
                )
            val value = session.retrieveEntry(recordId, fieldKey)
            Result.success(value)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 列出记录 ID
     *
     * 获取 Vault 中所有记录 ID（脱敏）
     */
    suspend fun listRecordIds(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val session = activeSession
                ?: return@withContext Result.failure(
                    IllegalStateException("Vault is locked. No active session.")
                )
            val ids = session.listRecordIds()
            Result.success(ids)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取设备列表
     *
     * 获取所有已注册设备（脱敏）
     */
    suspend fun getDeviceList(): Result<List<UniFFIDeviceInfo>> = withContext(Dispatchers.IO) {
        try {
            val devices = engine.getDeviceList()
            Result.success(devices)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 撤销设备
     *
     * @param deviceId 设备 ID（16 字节）
     */
    suspend fun revokeDevice(deviceId: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            engine.revokeDevice(deviceId)
            Result.success(Unit)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 发起恢复
     *
     * 启动 48 小时否决窗口
     */
    suspend fun initiateRecovery(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val recoveryId = engine.initiateRecovery()
            Result.success(recoveryId)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 提交否决
     *
     * 对恢复请求提交否决
     *
     * @param recoveryId 恢复请求 ID
     */
    suspend fun submitVeto(recoveryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            engine.submitVeto(recoveryId)
            Result.success(Unit)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 验证 Vault 完整性
     *
     * @param vaultBlob Vault 数据 Blob
     */
    suspend fun verifyVaultIntegrity(vaultBlob: ByteArray): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val isValid = engine.verifyVaultIntegrity(vaultBlob)
            Result.success(isValid)
        } catch (e: PqrrException) {
            Result.failure(mapPqrrError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 关闭引擎
     *
     * 清理所有资源
     */
    fun close() {
        lockVault()
        engine.shutdown()
    }

    /**
     * 映射 PqrrError 到通用 Exception
     *
     * 将 Rust 端的错误转换为 Kotlin 可理解的错误
     */
    private fun mapPqrrError(error: PqrrException): Exception {
        return when (error) {
            is PqrrException.EpochRegression -> {
                IllegalStateException(
                    "纪元回滚检测: 当前=${error.current}, 尝试=${error.attempted}"
                )
            }
            is PqrrException.HeaderIncomplete -> {
                IllegalStateException("Header 不完整: ${error.reason}")
            }
            is PqrrException.InsufficientPrivileges -> {
                SecurityException("权限不足: ${error.role} 无法执行 ${error.operation}")
            }
            is PqrrException.PermissionDenied -> {
                SecurityException("权限被拒绝: ${error.role} 无法执行 ${error.operation}")
            }
            is PqrrException.Vetoed -> {
                IllegalStateException("恢复被否决: ${error.requestId}, 否决数=${error.vetoCount}")
            }
            is PqrrException.InvalidStateTransition -> {
                IllegalStateException("无效状态转换: ${error.from} -> ${error.to}: ${error.reason}")
            }
            is PqrrException.StorageException -> {
                java.io.IOException("存储错误: ${error.storageMsg}")
            }
            else -> {
                RuntimeException("未知错误: ${error.message}")
            }
        }
    }

    /**
     * 获取默认 Vault 路径
     */
    private fun getDefaultVaultPath(): String {
        // TODO: 使用应用私有目录
        return "/data/data/io.aeternum/files/vault.db"
    }

    /**
     * 检查会话是否有效
     */
    fun isSessionValid(): Boolean {
        return activeSession?.isValid() == true
    }
}

/**
 * Vault 状态
 */
sealed class VaultState {
    /** 已锁定 - 需要密码/生物识别 */
    data object Locked : VaultState()

    /** 已解锁 - 可以访问数据 */
    data class Unlocked(val session: VaultSession) : VaultState()

    /** 不存在 - 需要初始化 */
    data object NotInitialized : VaultState()
}

/**
 * UniFFI DeviceInfo 扩展函数
 *
 * 将 ProtocolState 转换为字符串用于 UI 显示
 */
fun UniFFIDeviceInfo.getStateString(): String {
    return when (state) {
        ProtocolState.IDLE -> "idle"
        ProtocolState.REKEYING -> "rekeying"
        ProtocolState.RECOVERY_INITIATED -> "recovery_initiated"
        ProtocolState.DEGRADED -> "degraded"
        ProtocolState.REVOKED -> "revoked"
    }
}
