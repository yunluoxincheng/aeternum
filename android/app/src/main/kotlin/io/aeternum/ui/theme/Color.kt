package io.aeternum.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Aeternum 主题色彩系统
 *
 * 设计理念：深色主题 + 量子科技感
 * 参考：Material Design 3 Dark Theme + 自定义功能色
 */

// ============================================================================
// 主色调 (Primary Colors) - 量子蓝
// ============================================================================

/**
 * 主色 - 量子蓝
 *
 * 传达科技与安全的感觉，用于主要交互元素
 */
val QuantumBlue = Color(0xFF00BCD4)

/**
 * 主色上的内容色 - 黑色
 *
 * 确保在量子蓝背景上的文字可读性
 */
val OnQuantumBlue = Color(0xFF000000)

/**
 * 主色容器 - 深量子蓝
 *
 * 用于强调容器背景
 */
val QuantumBlueContainer = Color(0xFF008B9D)

/**
 * 主色容器上的内容色 - 白色
 *
 * 确保在深量子蓝背景上的文字可读性
 */
val OnQuantumBlueContainer = Color(0xFFFFFFFF)

// ============================================================================
// 背景色系 (Background Colors) - 深空灰
// ============================================================================

/**
 * 主背景色 - 深空灰
 *
 * Material Design 3 标准深色背景
 */
val DeepSpaceBackground = Color(0xFF121212)

/**
 * 主背景上的内容色 - 浅灰
 *
 * 确保在深色背景上的文字可读性
 */
val OnDeepSpaceBackground = Color(0xFFE0E0E0)

/**
 * 表面色 - 稍浅的深灰
 *
 * 用于卡片、对话框等表面元素
 */
val SurfaceColor = Color(0xFF1E1E1E)

/**
 * 表面上的内容色 - 浅灰
 *
 * 确保在表面上的文字可读性
 */
val OnSurfaceColor = Color(0xFFE0E0E0)

/**
 * 表面变体色 - 中灰
 *
 * 用于次要表面元素
 */
val SurfaceVariantColor = Color(0xFF2C2C2C)

/**
 * 表面变体上的内容色
 */
val OnSurfaceVariantColor = Color(0xFFB0B0B0)

// ============================================================================
// 功能色调 (Functional Colors)
// ============================================================================

/**
 * 错误色 - 量子红
 *
 * 用于错误、危险操作、撤销状态
 */
val QuantumRed = Color(0xFFFF5252)

/**
 * 错误色上的内容色 - 白色
 */
val OnQuantumRed = Color(0xFFFFFFFF)

/**
 * 成功色 - 量子绿
 *
 * 用于成功状态、安全确认
 */
val QuantumGreen = Color(0xFF69F0AE)

/**
 * 成功色上的内容色 - 黑色
 */
val OnQuantumGreen = Color(0xFF000000)

/**
 * 警告色 - 量子黄
 *
 * 用于警告提示、轮换中状态
 */
val QuantumYellow = Color(0xFFFFD740)

/**
 * 警告色上的内容色 - 黑色
 */
val OnQuantumYellow = Color(0xFF000000)

/**
 * 信息色 - 量子天蓝
 *
 * 用于信息提示、帮助文本
 */
val QuantumInfo = Color(0xFF40C4FF)

/**
 * 信息色上的内容色 - 黑色
 */
val OnQuantumInfo = Color(0xFF000000)

// ============================================================================
// 状态机色彩映射 (State Machine Colors)
// ============================================================================

/**
 * 状态机颜色映射
 *
 * 根据设备状态动态选择颜色
 */
sealed class MachineStateColor(val color: Color, val description: String) {
    /**
     * Idle 状态 - 安全绿色
     */
    data object Idle : MachineStateColor(
        color = QuantumGreen,
        description = "安全"
    )

    /**
     * Decrypting 状态 - 量子蓝色
     */
    data object Decrypting : MachineStateColor(
        color = QuantumBlue,
        description = "解密中"
    )

    /**
     * Rekeying 状态 - 警告黄色
     */
    data object Rekeying : MachineStateColor(
        color = QuantumYellow,
        description = "轮换中"
    )

    /**
     * Degraded 状态 - 错误红色
     */
    data object Degraded : MachineStateColor(
        color = QuantumRed,
        description = "降级"
    )

    /**
     * Revoked 状态 - 深红色
     */
    data object Revoked : MachineStateColor(
        color = Color(0xFFB00020),
        description = "已撤销"
    )
}

/**
 * 根据状态字符串获取颜色
 *
 * @param state 状态字符串
 * @return 对应的颜色
 */
fun getStateColor(state: String): Color {
    return when (state.lowercase()) {
        "idle" -> MachineStateColor.Idle.color
        "decrypting" -> MachineStateColor.Decrypting.color
        "rekeying" -> MachineStateColor.Rekeying.color
        "degraded" -> MachineStateColor.Degraded.color
        "revoked" -> MachineStateColor.Revoked.color
        else -> QuantumBlue
    }
}

// ============================================================================
// 透明度变体 (Opacity Variants)
// ============================================================================

/**
 * 带透明度的量子蓝
 *
 * @param alpha 透明度 (0.0 - 1.0)
 */
fun quantumBlueAlpha(alpha: Float): Color = QuantumBlue.copy(alpha = alpha)

/**
 * 带透明度的表面色
 *
 * @param alpha 透明度 (0.0 - 1.0)
 */
fun surfaceColorAlpha(alpha: Float): Color = SurfaceColor.copy(alpha = alpha)

/**
 * 带透明度的背景色
 *
 * @param alpha 透明度 (0.0 - 1.0)
 */
fun backgroundColorAlpha(alpha: Float): Color = DeepSpaceBackground.copy(alpha = alpha)

// ============================================================================
// 渐变色 (Gradient Colors)
// ============================================================================

/**
 * 量子蓝渐变起始色
 */
val QuantumBlueGradientStart = Color(0xFF00BCD4)

/**
 * 量子蓝渐变结束色
 */
val QuantumBlueGradientEnd = Color(0xFF0097A7)

/**
 * 深空渐变起始色
 */
val DeepSpaceGradientStart = Color(0xFF121212)

/**
 * 深空渐变结束色
 */
val DeepSpaceGradientEnd = Color(0xFF1E1E1E)
