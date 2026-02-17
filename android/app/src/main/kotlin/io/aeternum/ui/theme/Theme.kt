package io.aeternum.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.view.accessibility.AccessibilityManager

/**
 * Aeternum 主题配置
 *
 * INVARIANT: 仅支持深色主题
 * 设计理念：深色主题 + 量子科技感，符合"后量子安全"产品气质
 */

/**
 * Aeternum 深色主题配色方案
 *
 * 使用自定义量子蓝配色，替代 Material 默认配色
 */
private val AeternumDarkColorScheme = darkColorScheme(
    // 主色调
    primary = QuantumBlue,
    onPrimary = OnQuantumBlue,
    primaryContainer = QuantumBlueContainer,
    onPrimaryContainer = OnQuantumBlueContainer,

    // 次要色调
    secondary = QuantumBlue,
    onSecondary = OnQuantumBlue,
    secondaryContainer = QuantumBlueContainer,
    onSecondaryContainer = OnQuantumBlueContainer,

    // 背景色系
    background = DeepSpaceBackground,
    onBackground = OnDeepSpaceBackground,
    surface = SurfaceColor,
    onSurface = OnSurfaceColor,
    surfaceVariant = SurfaceVariantColor,
    onSurfaceVariant = OnSurfaceVariantColor,

    // 错误色
    error = QuantumRed,
    onError = OnQuantumRed,

    // 其他
    outline = SurfaceVariantColor,
    outlineVariant = SurfaceVariantColor,
    scrim = SurfaceVariantColor,
)

/**
 * 高对比度配色方案
 *
 * 符合 WCAG AAA 标准（对比度 >= 7:1）
 */
private val AeternumHighContrastColorScheme = darkColorScheme(
    // 主色调
    primary = HighContrastQuantumBlue,
    onPrimary = HighContrastOnQuantumBlue,
    primaryContainer = HighContrastQuantumBlueContainer,
    onPrimaryContainer = HighContrastOnQuantumBlueContainer,

    // 次要色调
    secondary = HighContrastQuantumBlue,
    onSecondary = HighContrastOnQuantumBlue,
    secondaryContainer = HighContrastQuantumBlueContainer,
    onSecondaryContainer = HighContrastOnQuantumBlueContainer,

    // 背景色系
    background = HighContrastBackground,
    onBackground = HighContrastOnBackground,
    surface = HighContrastSurface,
    onSurface = HighContrastOnSurface,
    surfaceVariant = HighContrastSurfaceVariant,
    onSurfaceVariant = HighContrastOnSurfaceVariant,

    // 错误色
    error = HighContrastError,
    onError = HighContrastOnError,

    // 其他
    outline = HighContrastBorder,
    outlineVariant = HighContrastDivider,
    scrim = HighContrastSurfaceVariant,
)

/**
 * 检查系统高对比度模式是否启用
 */
@Composable
private fun isHighContrastEnabled(): Boolean {
    val context = LocalContext.current
    val accessibilityManager = remember {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    // 使用反射调用已弃用的 API
    return try {
        val method = accessibilityManager?.javaClass?.getMethod("isHighTextContrastEnabled")
        method?.invoke(accessibilityManager) as? Boolean ?: false
    } catch (e: Exception) {
        false
    }
}

/**
 * Aeternum 主题
 *
 * 强制使用深色主题，符合"后量子安全"产品气质
 * 自动适配高对比度模式
 *
 * @param darkTheme 是否使用深色主题（固定为 true）
 * @param dynamicColor 是否使用动态配色（Android 12+）
 * @param highContrast 是否强制启用高对比度（默认自动检测）
 * @param content 主题内容
 */
@Composable
fun AeternumTheme(
    darkTheme: Boolean = true, // INVARIANT: 固定为 true，仅支持深色主题
    dynamicColor: Boolean = false, // INVARIANT: 禁用动态配色，使用自定义量子蓝
    highContrast: Boolean = isHighContrastEnabled(), // 自动检测高对比度模式
    content: @Composable () -> Unit,
) {
    // INVARIANT: 仅支持深色主题，忽略 lightTheme 参数
    val colorScheme = when {
        highContrast -> AeternumHighContrastColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // 仅使用深色动态配色
            dynamicDarkColorScheme(context)
        }
        else -> AeternumDarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AeternumTypography,
        shapes = AeternumShapes,
        content = content,
    )
}

/**
 * Aeternum 预览主题
 *
 * 用于 Compose Preview，确保预览时使用深色主题
 */
@Composable
fun AeternumPreviewTheme(
    content: @Composable () -> Unit,
) {
    AeternumTheme(
        darkTheme = true,
        dynamicColor = false,
        highContrast = false,
        content = content,
    )
}

/**
 * Aeternum 高对比度预览主题
 *
 * 用于 Compose Preview，预览高对比度效果
 */
@Composable
fun AeternumHighContrastPreviewTheme(
    content: @Composable () -> Unit,
) {
    AeternumTheme(
        darkTheme = true,
        dynamicColor = false,
        highContrast = true,
        content = content,
    )
}

// ============================================================================
// 主题扩展函数
// ============================================================================

/**
 * MaterialTheme 扩展属性
 *
 * 提供便捷访问自定义颜色
 */
object AeternumTheme {
    /**
     * 量子蓝
     */
    val quantumBlue: androidx.compose.ui.graphics.Color
        @Composable get() = QuantumBlue

    /**
     * 量子红
     */
    val quantumRed: androidx.compose.ui.graphics.Color
        @Composable get() = QuantumRed

    /**
     * 量子绿
     */
    val quantumGreen: androidx.compose.ui.graphics.Color
        @Composable get() = QuantumGreen

    /**
     * 量子黄
     */
    val quantumYellow: androidx.compose.ui.graphics.Color
        @Composable get() = QuantumYellow

    /**
     * 量子信息蓝
     */
    val quantumInfo: androidx.compose.ui.graphics.Color
        @Composable get() = QuantumInfo

    /**
     * 深空背景色
     */
    val deepSpaceBackground: androidx.compose.ui.graphics.Color
        @Composable get() = DeepSpaceBackground

    /**
     * 表面色
     */
    val surfaceColor: androidx.compose.ui.graphics.Color
        @Composable get() = SurfaceColor

    /**
     * 获取状态机颜色
     *
     * @param state 状态字符串
     * @return 对应的颜色
     */
    @Composable
    fun getStateColor(state: String): androidx.compose.ui.graphics.Color {
        // 调用顶层函数，避免无限递归
        return io.aeternum.ui.theme.getStateColor(state)
    }
}

// ============================================================================
// 使用示例
// ============================================================================

/**
 * 示例：在 Composable 中使用主题
 *
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *    AeternumTheme {
 *        Scaffold(
 *            containerColor = MaterialTheme.colorScheme.background
 *        ) { padding ->
 *            Text(
 *                text = "Hello, Aeternum!",
 *                style = MaterialTheme.typography.headlineLarge,
 *                color = MaterialTheme.colorScheme.onBackground
 *            )
 *        }
 *    }
 * }
 * ```
 */

/**
 * 示例：使用自定义颜色
 *
 * ```kotlin
 * @Composable
 * fun StatusCard() {
 *    Card(
 *        colors = CardDefaults.cardColors(
 *            containerColor = QuantumGreen
 *        )
 *    ) {
 *        Text(
 *            text = "安全",
 *            style = StatusIndicatorTextStyle,
 *            color = OnQuantumGreen
 *        )
 *    }
 * }
 * ```
 */

/**
 * 示例：使用状态机颜色
 *
 * ```kotlin
 * @Composable
 * fun StateIndicator(state: String) {
 *    val color = getStateColor(state)
 *    Box(
 *        modifier = Modifier
 *            .background(color, StatusIndicatorShape)
 *            .size(12.dp)
 *    )
 * }
 * ```
 */
