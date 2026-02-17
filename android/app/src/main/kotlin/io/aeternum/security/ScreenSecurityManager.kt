package io.aeternum.security

import android.app.Activity
import android.view.WindowManager

/**
 * 屏幕安全管理器
 *
 * 负责动态管理 FLAG_SECURE，防止敏感屏幕被截屏或录屏
 *
 * INVARIANT: 仅在敏感屏幕启用 FLAG_SECURE，非敏感屏幕（如欢迎页、帮助页）允许截屏
 *
 * 敏感屏幕列表：
 * - 生物识别认证屏幕
 * - Vault 解密屏幕
 * - 设备管理屏幕
 * - 恢复流程屏幕
 * - 助记词备份屏幕
 *
 * 非敏感屏幕列表：
 * - 欢迎屏幕
 * - 关于/帮助屏幕
 */
object ScreenSecurityManager {

    /** 敏感路由集合（需要启用 FLAG_SECURE） */
    private val sensitiveRoutes = setOf(
        // 认证相关
        "biometric_prompt",
        // Vault 相关
        "vault",
        "main", // 主屏幕包含敏感信息
        // 设备管理相关
        "devices",
        "device_detail",
        "add_device",
        // 恢复相关
        "recovery_initiate",
        "veto_notification",
        "veto_history",
        // 初始化相关
        "mnemonic_backup",
        "registration",
    )

    /**
     * 为 Activity 设置防截屏标志
     *
     * @param activity 目标 Activity
     * @param enable 是否启用 FLAG_SECURE
     */
    fun setSecureFlag(activity: Activity, enable: Boolean) {
        if (enable) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * 根据路由判断是否需要启用 FLAG_SECURE
     *
     * @param route 当前路由
     * @return 是否为敏感路由
     */
    fun isSensitiveRoute(route: String?): Boolean {
        if (route == null) return false

        // 精确匹配
        if (route in sensitiveRoutes) return true

        // 前缀匹配（处理带参数的路由）
        return sensitiveRoutes.any { sensitiveRoute ->
            route.startsWith("$sensitiveRoute/")
        }
    }

    /**
     * 更新屏幕安全状态
     *
     * 根据当前路由自动启用或禁用 FLAG_SECURE
     *
     * @param activity 目标 Activity
     * @param currentRoute 当前路由
     */
    fun updateScreenSecurity(activity: Activity, currentRoute: String?) {
        val shouldBeSecure = isSensitiveRoute(currentRoute)
        setSecureFlag(activity, shouldBeSecure)
    }
}
