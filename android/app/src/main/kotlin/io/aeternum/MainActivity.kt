package io.aeternum

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import io.aeternum.security.AndroidSecurityManager
import io.aeternum.security.ScreenSecurityManager
import io.aeternum.ui.navigation.AeternumNavHost
import io.aeternum.ui.navigation.WelcomeRoute
import io.aeternum.ui.theme.AeternumTheme
import io.aeternum.ui.viewmodel.AeternumViewModel
import kotlinx.coroutines.launch

/**
 * Aeternum 主 Activity
 *
 * 单 Activity 架构，所有导航通过 Compose Navigation 处理
 *
 * 安全特性：
 * - FLAG_SECURE：根据当前路由动态启用/禁用防截屏
 * - 生命周期观察：自动管理会话锁定
 * - 路由监听：敏感屏幕自动启用防截屏，非敏感屏幕允许截屏
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AeternumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化安全管理器
        AndroidSecurityManager.initialize(applicationContext)

        // 启用边到边显示
        enableEdgeToEdge()

        // 注册生命周期观察者（用于会话自动锁定）
        lifecycle.addObserver(viewModel)

        setContent {
            AeternumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // 创建导航控制器
                    val navController = androidx.navigation.compose.rememberNavController()

                    // 导航主机（带路由监听）
                    AeternumNavHost(
                        navController = navController,
                        startDestination = WelcomeRoute.route,
                        viewModel = viewModel,
                        onRouteChanged = { route ->
                            // 根据路由动态更新 FLAG_SECURE
                            ScreenSecurityManager.updateScreenSecurity(this@MainActivity, route)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除生命周期观察者
        lifecycle.removeObserver(viewModel)
    }
}
