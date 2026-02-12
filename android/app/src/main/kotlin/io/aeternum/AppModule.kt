package io.aeternum

import io.aeternum.bridge.AeternumBridge
import io.aeternum.data.VaultRepository
import org.koin.dsl.module

/**
 * Koin 依赖注入模块
 */
val appModule = module {
    // Vault Repository
    single { VaultRepository(get()) }

    // Bridge Factory
    factory { (vaultPath: String) -> AeternumBridge(java.io.File(vaultPath)) }
}
