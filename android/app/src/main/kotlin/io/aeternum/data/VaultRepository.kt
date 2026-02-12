package io.aeternum.data

import io.aeternum.bridge.AeternumBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Vault 仓库
 *
 * 管理 Vault 的状态和操作
 */
class VaultRepository(
    private val bridgeFactory: (String) -> AeternumBridge
) {
    private val bridge: AeternumBridge by lazy {
        bridgeFactory(getDefaultVaultPath())
    }

    private val _vaultState = MutableStateFlow<VaultState>(VaultState.Locked)
    val vaultState: Flow<VaultState> = _vaultState.asStateFlow()

    /**
     * 获取默认 Vault 路径
     */
    private fun getDefaultVaultPath(): String {
        // TODO: 使用应用私有目录
        return "/data/data/io.aeternum/files/vault.db"
    }

    /**
     * 初始化新 Vault
     */
    suspend fun initializeVault(password: String): Result<Unit> {
        return bridge.initializeVault(password).also {
            if (it.isSuccess) {
                _vaultState.value = VaultState.Unlocked
            }
        }
    }

    /**
     * 解锁 Vault
     */
    suspend fun unlockVault(password: String): Result<Unit> {
        return bridge.unlockVault(password).also {
            if (it.isSuccess) {
                _vaultState.value = VaultState.Unlocked
            }
        }
    }

    /**
     * 锁定 Vault
     */
    fun lockVault() {
        bridge.close()
        _vaultState.value = VaultState.Locked
    }

    /**
     * 存储条目
     */
    suspend fun storeEntry(key: String, value: ByteArray): Result<Unit> {
        return bridge.storeEntry(key, value)
    }

    /**
     * 检索条目
     */
    suspend fun retrieveEntry(key: String): Result<ByteArray> {
        return bridge.retrieveEntry(key)
    }
}

/**
 * Vault 状态
 */
sealed class VaultState {
    /** 已锁定 - 需要密码/生物识别 */
    data object Locked : VaultState()

    /** 已解锁 - 可以访问数据 */
    data object Unlocked : VaultState()

    /** 不存在 - 需要初始化 */
    data object NotInitialized : VaultState()
}
