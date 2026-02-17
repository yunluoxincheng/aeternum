package io.aeternum

import io.aeternum.data.VaultRepository
import org.koin.dsl.module

/**
 * Koin 依赖注入模块
 */
val appModule = module {
    // Vault Repository - 使用无参构造函数
    single { VaultRepository() }

    // 注意: AeternumEngine 现在由 UniFFI 生成
    // VaultRepository 内部会根据需要创建引擎实例
}
