package io.aeternum.bridge

import aeternum.AeternumSession
import aeternum.AeternumErrorCode
import aeternum.AeternumException
import io.aeternum.security.AndroidSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Aeternum 核心桥接层
 *
 * 封装 UniFFI 生成的 Rust FFI 接口，提供 Kotlin 友好的 API
 */
class AeternumBridge(private val vaultPath: File) {

    private var session: AeternumSession? = null

    /**
     * 初始化新的 Vault
     */
    suspend fun initializeVault(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = AeternumSession(vaultPath.absolutePath)
            session.initializeVault(password)
            this@AeternumBridge.session = session
            Result.success(Unit)
        } catch (e: AeternumException) {
            Result.failure(handleAeternumError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解锁现有 Vault
     */
    suspend fun unlockVault(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = AeternumSession(vaultPath.absolutePath)
            val success = session.unlockVault(password)
            if (success) {
                this@AeternumBridge.session = session
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("密码错误或 Vault 不存在"))
            }
        } catch (e: AeternumException) {
            Result.failure(handleAeternumError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 存储条目
     */
    suspend fun storeEntry(key: String, value: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            session?.storeEntry(key, value.toList())
            Result.success(Unit)
        } catch (e: AeternumException) {
            Result.failure(handleAeternumError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 检索条目
     */
    suspend fun retrieveEntry(key: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val value = session?.retrieveEntry(key)
            value?.let { Result.success(it.toByteArray()) }
                ?: Result.failure(NoSuchElementException("条目不存在: $key"))
        } catch (e: AeternumException) {
            Result.failure(handleAeternumError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 关闭会话
     */
    fun close() {
        session?.close()
        session = null
    }

    /**
     * 处理 Aeternum 异常
     */
    private fun handleAeternumError(e: AeternumException): Exception {
        return when (e.errorCode) {
            AeternumErrorCode.CryptoError -> SecurityException("加密操作失败: ${e.message}")
            AeternumErrorCode.StorageError -> IOException("存储操作失败: ${e.message}")
            AeternumErrorCode.SyncError -> IOException("同步操作失败: ${e.message}")
            AeternumErrorCode.InvalidInput -> IllegalArgumentException("无效输入: ${e.message}")
        }
    }
}
