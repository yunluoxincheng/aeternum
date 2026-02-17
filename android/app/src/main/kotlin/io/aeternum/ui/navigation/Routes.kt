package io.aeternum.ui.navigation

/**
 * Aeternum 导航路由定义
 *
 * 定义所有目标路由和参数键
 * INVARIANT: 类型安全的路由，使用普通对象确保编译时检查
 */

// ============================================================================
// 主路由定义
// ============================================================================

/**
 * 导航路由接口
 */
interface AeternumRoute {
    val route: String
}

// ============================================================================
// 初始化流程路由 (Onboarding)
// ============================================================================

/**
 * 欢迎屏幕
 */
data object WelcomeRoute : AeternumRoute {
    override val route: String = "welcome"
}

/**
 * 助记词备份屏幕
 */
data object MnemonicBackupRoute : AeternumRoute {
    override val route: String = "mnemonic_backup"
}

/**
 * 设备注册屏幕
 */
data object RegistrationRoute : AeternumRoute {
    override val route: String = "registration"
}

// ============================================================================
// 认证流程路由 (Auth)
// ============================================================================

/**
 * 生物识别认证屏幕
 */
data object BiometricPromptRoute : AeternumRoute {
    override val route: String = "biometric_prompt"
}

// ============================================================================
// 主流程路由 (Main)
// ============================================================================

/**
 * 主屏幕 (Idle 状态)
 */
data object MainRoute : AeternumRoute {
    override val route: String = "main"
}

/**
 * Vault 屏幕 (Decrypting 状态)
 */
data object VaultRoute : AeternumRoute {
    override val route: String = "vault"
}

/**
 * 密钥轮换屏幕 (Rekeying 状态)
 */
data object RekeyingRoute : AeternumRoute {
    override val route: String = "rekeying"

    const val ARG_CURRENT_EPOCH = "current_epoch"
    const val ARG_TARGET_EPOCH = "target_epoch"

    fun create(currentEpoch: UInt, targetEpoch: UInt): String {
        return "rekeying/$currentEpoch/$targetEpoch"
    }
}

// ============================================================================
// 设备管理路由 (Devices)
// ============================================================================

/**
 * 设备列表屏幕
 */
data object DeviceListRoute : AeternumRoute {
    override val route: String = "devices"
}

/**
 * 设备详情屏幕
 */
data object DeviceDetailRoute : AeternumRoute {
    override val route: String = "device_detail"

    const val ARG_DEVICE_ID = "device_id"

    fun create(deviceId: String): String {
        return "device_detail/$deviceId"
    }
}

/**
 * 添加设备屏幕
 */
data object AddDeviceRoute : AeternumRoute {
    override val route: String = "add_device"
}

// ============================================================================
// 恢复流程路由 (Recovery)
// ============================================================================

/**
 * 恢复发起屏幕
 */
data object RecoveryInitiateRoute : AeternumRoute {
    override val route: String = "recovery_initiate"
}

/**
 * 否决通知屏幕
 */
data object VetoNotificationRoute : AeternumRoute {
    override val route: String = "veto_notification"

    const val ARG_RECOVERY_ID = "recovery_id"

    fun create(recoveryId: String): String {
        return "veto_notification/$recoveryId"
    }
}

/**
 * 否决历史屏幕
 */
data object VetoHistoryRoute : AeternumRoute {
    override val route: String = "veto_history"
}

// ============================================================================
// 异常状态路由 (Error States)
// ============================================================================

/**
 * 降级模式屏幕
 */
data object DegradedModeRoute : AeternumRoute {
    override val route: String = "degraded_mode"
}

/**
 * 撤销状态屏幕
 */
data object RevokedRoute : AeternumRoute {
    override val route: String = "revoked"
}

// ============================================================================
// 路由扩展函数
// ============================================================================

/**
 * 导航到目标路由
 *
 * @param navController NavController
 * @param route 目标路由
 */
fun navigateToRoute(
    navController: androidx.navigation.NavController,
    route: AeternumRoute,
) {
    navController.navigate(route.route)
}

/**
 * 导航到带参数的路由
 *
 * @param navController NavController
 * @param route 完整路由路径
 */
fun navigateToRoute(
    navController: androidx.navigation.NavController,
    route: String,
) {
    navController.navigate(route)
}

/**
 * 返回上一页
 *
 * @param navController NavController
 */
fun navigateBack(navController: androidx.navigation.NavController) {
    navController.popBackStack()
}

/**
 * 返回到指定路由
 *
 * @param navController NavController
 * @param route 目标路由
 * @param inclusive 是否包含目标路由
 */
fun navigateBackTo(
    navController: androidx.navigation.NavController,
    route: AeternumRoute,
    inclusive: Boolean = false,
) {
    navController.popBackStack(route.route, inclusive)
}
