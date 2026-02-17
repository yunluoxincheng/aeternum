package io.aeternum.ui.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import io.aeternum.security.ScreenSecurityManager

/**
 * 安全屏幕包装器
 *
 * 为敏感屏幕自动设置 FLAG_SECURE，防止截屏和录屏
 *
 * INVARIANT: 仅在敏感屏幕启用 FLAG_SECURE，非敏感屏幕允许截屏
 *
 * 使用示例：
 * ```kotlin
 * SecureScreen {
 *     VaultContent()
 * }
 * ```
 *
 * @param enable 是否启用 FLAG_SECURE（默认 true）
 * @param content 屏幕内容
 */
@Composable
fun SecureScreen(
    enable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current as? Activity

    DisposableEffect(enable) {
        // 进入屏幕时设置 FLAG_SECURE
        if (enable && activity != null) {
            ScreenSecurityManager.setSecureFlag(activity, true)
        }

        onDispose {
            // 离开屏幕时清除 FLAG_SECURE
            if (enable && activity != null) {
                ScreenSecurityManager.setSecureFlag(activity, false)
            }
        }
    }

    content()
}

/**
 * 路由感知的安全屏幕包装器
 *
 * 根据路由自动判断是否需要启用 FLAG_SECURE
 *
 * INVARIANT: 通过 ScreenSecurityManager.isSensitiveRoute 判断路由敏感性
 *
 * 使用示例：
 * ```kotlin
 * RouteAwareSecureScreen(currentRoute = "vault") {
 *     VaultContent()
 * }
 * ```
 *
 * @param currentRoute 当前路由
 * @param content 屏幕内容
 */
@Composable
fun RouteAwareSecureScreen(
    currentRoute: String?,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    val isSensitive = ScreenSecurityManager.isSensitiveRoute(currentRoute)

    DisposableEffect(isSensitive) {
        // 根据路由敏感性设置 FLAG_SECURE
        if (activity != null) {
            ScreenSecurityManager.updateScreenSecurity(activity, currentRoute)
        }

        onDispose {
            // 离开屏幕时清除 FLAG_SECURE
            if (activity != null) {
                ScreenSecurityManager.setSecureFlag(activity, false)
            }
        }
    }

    content()
}
