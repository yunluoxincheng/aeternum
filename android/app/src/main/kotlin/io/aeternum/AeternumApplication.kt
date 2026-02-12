package io.aeternum

import android.app.Application
import io.aeternum.security.AndroidSecurityManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Aeternum 应用程序入口
 */
class AeternumApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化依赖注入
        startKoin {
            androidContext(this@AeternumApplication)
            modules(appModule)
        }

        // 初始化安全管理器
        AndroidSecurityManager.initialize(this)
    }
}
