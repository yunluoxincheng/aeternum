package io.aeternum.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import io.aeternum.ui.auth.BiometricPromptScreen
import io.aeternum.ui.degraded.DegradedModeScreen
import io.aeternum.ui.devices.AddDeviceScreen
import io.aeternum.ui.devices.DeviceDetailScreen
import io.aeternum.ui.devices.DeviceListScreen
import io.aeternum.ui.main.MainScreen
import io.aeternum.ui.main.VaultScreen
import io.aeternum.ui.onboarding.MnemonicBackupScreen
import io.aeternum.ui.onboarding.RegistrationScreen
import io.aeternum.ui.onboarding.WelcomeScreen
import io.aeternum.ui.recovery.RecoveryInitiateScreen
import io.aeternum.ui.recovery.VetoNotificationScreen
import io.aeternum.ui.revoked.RevokedScreen
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Aeternum 导航图
 *
 * INVARIANT: 单 Activity 架构 + 类型安全的导航
 * 组件层次：
 * MainActivity -> AeternumApp -> AeternumNavHost -> [各子图]
 */

// ============================================================================
// 占位符屏幕（待后续实现）
// ============================================================================

/**
 * 密钥轮换屏幕占位符
 */
@Composable
private fun RekeyingScreenPlaceholder(
    currentEpoch: UInt,
    targetEpoch: UInt,
) {
    // TODO: 实现 RekeyingScreen
}

// ============================================================================
// 初始化流程导航图 (Onboarding Graph)
// ============================================================================

/**
 * 初始化流程导航图
 *
 * 包含：欢迎 -> 助记词备份 -> 注册
 *
 * INVARIANT: 导航流程严格按顺序进行，确保用户完成所有初始化步骤
 * INVARIANT: 助记词由调用方提供（从 Rust Core 获取），UI 层不生成助记词
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    // TODO: 从 ViewModel 获取助记词
    mnemonicWords: List<String> = emptyList(),
) {
    navigation(
        route = "onboarding",
        startDestination = WelcomeRoute.route,
    ) {
        // 欢迎屏幕
        composable(WelcomeRoute.route) {
            WelcomeScreen(
                onGetStarted = {
                    // 导航到助记词备份屏幕
                    navController.navigate(MnemonicBackupRoute.route)
                }
            )
        }

        // 助记词备份屏幕
        composable(MnemonicBackupRoute.route) {
            MnemonicBackupScreen(
                mnemonicWords = mnemonicWords,
                onBack = {
                    // 返回欢迎屏幕
                    navController.popBackStack()
                },
                onConfirm = {
                    // 导航到设备注册屏幕
                    navController.navigate(RegistrationRoute.route)
                }
            )
        }

        // 设备注册屏幕
        composable(RegistrationRoute.route) {
            RegistrationScreen(
                onBack = {
                    navController.popBackStack()
                },
                onRegistrationComplete = {
                    // 注册完成后导航到主屏幕
                    navController.navigate(MainRoute.route) {
                        popUpTo("onboarding") {
                            inclusive = true
                        }
                    }
                },
                onRetry = {
                    // 重试注册
                    // TODO: 实现重试逻辑
                }
            )
        }
    }
}

// ============================================================================
// 认证流程导航图 (Auth Graph)
// ============================================================================

/**
 * 认证流程导航图
 *
 * 包含：生物识别认证
 *
 * INVARIANT: 生物识别认证必须使用 Class 3 生物识别（硬件-backed）
 */
fun NavGraphBuilder.authGraph(
    navController: NavHostController,
) {
    navigation(
        route = "auth",
        startDestination = BiometricPromptRoute.route,
    ) {
        // 生物识别屏幕
        composable(
            route = BiometricPromptRoute.route,
            enterTransition = { biometricSuccessEnterTransition },
            exitTransition = { biometricSuccessExitTransition },
        ) {
            BiometricPromptScreen(
                onAuthSuccess = {
                    // 认证成功，导航到 Vault 屏幕
                    navController.navigate(VaultRoute.route)
                },
                onAuthCancel = {
                    // 用户取消，返回上一页
                    navController.popBackStack()
                },
                onAuthFailed = { reason ->
                    // 认证失败，显示错误并返回
                    navController.popBackStack()
                }
            )
        }
    }
}

// ============================================================================
// 主流程导航图 (Main Graph)
// ============================================================================

/**
 * 主流程导航图
 *
 * 包含：主屏幕 (Idle) -> Vault (Decrypting) -> 轮换 (Rekeying)
 */
fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
) {
    navigation(
        route = "main",
        startDestination = MainRoute.route,
    ) {
        // 主屏幕 (Idle 状态)
        composable(MainRoute.route) {
            MainScreen(
                onUnlockRequest = {
                    // TODO: 触发生物识别认证
                    navController.navigate(BiometricPromptRoute.route)
                },
                onNavigateToScreen = { route ->
                    navController.navigate(route)
                }
            )
        }

        // Vault 屏幕 (Decrypting 状态)
        composable(VaultRoute.route) {
            VaultScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 密钥轮换屏幕 (Rekeying 状态)
        composable(
            route = "${RekeyingRoute.route}/{${RekeyingRoute.ARG_CURRENT_EPOCH}}/{${RekeyingRoute.ARG_TARGET_EPOCH}}",
            arguments = listOf(
                navArgument(RekeyingRoute.ARG_CURRENT_EPOCH) {
                    type = NavType.StringType
                },
                navArgument(RekeyingRoute.ARG_TARGET_EPOCH) {
                    type = NavType.StringType
                },
            ),
            enterTransition = { rekeyingEnterTransition },
            exitTransition = { rekeyingExitTransition },
        ) { backStackEntry ->
            val currentEpoch = backStackEntry.arguments?.getString(RekeyingRoute.ARG_CURRENT_EPOCH)?.toUInt() ?: 0u
            val targetEpoch = backStackEntry.arguments?.getString(RekeyingRoute.ARG_TARGET_EPOCH)?.toUInt() ?: 1u
            RekeyingScreenPlaceholder(currentEpoch, targetEpoch)
        }
    }
}

// ============================================================================
// 设备管理导航图 (Devices Graph)
// ============================================================================

/**
 * 设备管理导航图
 *
 * 包含：设备列表 -> 设备详情 -> 添加设备
 */
fun NavGraphBuilder.devicesGraph(
    navController: NavHostController,
) {
    navigation(
        route = "devices",
        startDestination = DeviceListRoute.route,
    ) {
        // 设备列表
        composable(DeviceListRoute.route) {
            DeviceListScreen(
                onNavigateToDetail = { deviceId ->
                    navController.navigate(DeviceDetailRoute.create(deviceId))
                },
                onNavigateToAdd = {
                    navController.navigate(AddDeviceRoute.route)
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 设备详情
        composable(
            route = "${DeviceDetailRoute.route}/{${DeviceDetailRoute.ARG_DEVICE_ID}}",
            arguments = listOf(
                navArgument(DeviceDetailRoute.ARG_DEVICE_ID) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString(DeviceDetailRoute.ARG_DEVICE_ID) ?: ""
            DeviceDetailScreen(
                deviceId = deviceId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 添加设备
        composable(AddDeviceRoute.route) {
            AddDeviceScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onDeviceAdded = {
                    // 设备添加成功，返回设备列表
                    navController.popBackStack()
                }
            )
        }
    }
}

// ============================================================================
// 恢复流程导航图 (Recovery Graph)
// ============================================================================

/**
 * 恢复流程导航图
 *
 * 包含：恢复发起 -> 否决通知
 */
fun NavGraphBuilder.recoveryGraph(
    navController: NavHostController,
    viewModel: io.aeternum.ui.viewmodel.AeternumViewModel,
) {
    navigation(
        route = "recovery",
        startDestination = RecoveryInitiateRoute.route,
    ) {
        // 恢复发起
        composable(RecoveryInitiateRoute.route) {
            RecoveryInitiateScreen(
                onBack = { navController.popBackStack() },
                onRecoveryInitiated = { recoveryId ->
                    navController.navigate(VetoNotificationRoute.create(recoveryId))
                },
                viewModel = viewModel,
            )
        }

        // 否决通知
        composable(
            route = "${VetoNotificationRoute.route}/{${VetoNotificationRoute.ARG_RECOVERY_ID}}",
            arguments = listOf(
                navArgument(VetoNotificationRoute.ARG_RECOVERY_ID) {
                    type = NavType.StringType
                },
            ),
            enterTransition = { vetoEnterTransition },
            exitTransition = { vetoExitTransition },
        ) { backStackEntry ->
            val recoveryId = backStackEntry.arguments?.getString(VetoNotificationRoute.ARG_RECOVERY_ID) ?: ""
            VetoNotificationScreen(
                recoveryId = recoveryId,
                onBack = { navController.popBackStack() },
                onVetoSubmitted = {
                    // 否决成功，返回主屏幕
                    navController.popBackStack(VetoNotificationRoute.route, inclusive = true)
                    navController.navigate(MainRoute.route)
                },
                onWindowClosed = {
                    // 窗口关闭，返回主屏幕
                    navController.popBackStack(VetoNotificationRoute.route, inclusive = true)
                    navController.navigate(MainRoute.route)
                },
            )
        }

        // 否决历史
        composable(VetoHistoryRoute.route) {
            // TODO: 实现 VetoHistoryScreen
        }
    }
}

// ============================================================================
// 异常状态导航图 (Error States Graph)
// ============================================================================

/**
 * 异常状态导航图
 *
 * 包含：降级模式、撤销状态
 *
 * INVARIANT: 这些是终态或警告状态，不允许常规导航返回
 * INVARIANT: UI 层仅显示状态信息，不执行敏感操作
 */
fun NavGraphBuilder.errorStatesGraph(
    navController: NavHostController,
) {
    // 降级模式
    composable(DegradedModeRoute.route) {
        // TODO: 从 ViewModel 获取降级原因
        DegradedModeScreen(
            reason = io.aeternum.ui.state.DegradedReason.INTEGRITY_CHECK_FAILED,
            onReverify = {
                // TODO: 触发重新验证流程
                // 通过 AndroidSecurityManager 重新验证设备完整性
            },
            onLearnMore = {
                // TODO: 打开帮助文档
            },
        )
    }

    // 撤销状态（终态，不允许返回）
    composable(RevokedRoute.route) {
        // TODO: 从 ViewModel 获取撤销原因
        RevokedScreen(
            reason = io.aeternum.ui.state.RevokedReason.REVOKED_BY_ANOTHER_DEVICE(),
            onLearnMore = {
                // TODO: 打开帮助文档
            },
        )
    }
}

// ============================================================================
// 主导航主机 (NavHost)
// ============================================================================

/**
 * Aeternum 导航主机
 *
 * INVARIANT: 监听路由变化并动态更新 FLAG_SECURE
 *
 * @param navController 导航控制器
 * @param startDestination 起始目标路由
 * @param viewModel AeternumViewModel
 * @param onRouteChanged 路由变化回调（用于更新 FLAG_SECURE）
 */
@Composable
fun AeternumNavHost(
    navController: NavHostController,
    startDestination: String = WelcomeRoute.route,
    viewModel: io.aeternum.ui.viewmodel.AeternumViewModel,
    onRouteChanged: (String?) -> Unit = {},
) {
    // 监听当前路由变化
    var currentRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow
            .onEach { backStackEntry ->
                val newRoute = backStackEntry.destination.route
                if (newRoute != currentRoute) {
                    currentRoute = newRoute
                    onRouteChanged(newRoute)
                }
            }
            .launchIn(this)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { standardEnterTransition },
        exitTransition = { standardExitTransition },
        popEnterTransition = { standardPopEnterTransition },
        popExitTransition = { standardPopExitTransition },
    ) {
        // 初始化流程
        onboardingGraph(navController)

        // 认证流程
        authGraph(navController)

        // 主流程
        mainGraph(navController)

        // 设备管理
        devicesGraph(navController)

        // 恢复流程
        recoveryGraph(navController, viewModel)

        // 异常状态（不作为嵌套图，直接在根级别）
        errorStatesGraph(navController)
    }
}
