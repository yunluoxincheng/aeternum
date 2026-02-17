package io.aeternum.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Aeternum 排版系统
 *
 * 设计理念：清晰、现代、易读
 * 参考：Material Design 3 Typography Scale
 */

/**
 * Aeternum 排版规范
 *
 * 使用系统默认字体，确保各平台一致性
 */
val AeternumTypography = Typography(
    // ========================================================================
    // Display Styles - 大标题，用于非常强调的文本
    // ========================================================================

    /**
     * Display Large - 超大标题
     *
     * 用途：欢迎屏幕、特殊强调场景
     */
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),

    /**
     * Display Medium - 大标题
     *
     * 用途：主屏幕标题
     */
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),

    /**
     * Display Small - 中等标题
     *
     * 用途：次要屏幕标题
     */
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),

    // ========================================================================
    // Headline Styles - 标题，用于强调文本
    // ========================================================================

    /**
     * Headline Large - 大标题
     *
     * 用途：屏幕主标题
     */
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),

    /**
     * Headline Medium - 中等标题
     *
     * 用途：卡片标题、区块标题
     */
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),

    /**
     * Headline Small - 小标题
     *
     * 用途：列表项标题
     */
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),

    // ========================================================================
    // Title Styles - 标题，用于较短的强调文本
    // ========================================================================

    /**
     * Title Large - 大标题
     *
     * 用途：对话框标题、重要操作
     */
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),

    /**
     * Title Medium - 中等标题
     *
     * 用途：卡片标题
     */
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),

    /**
     * Title Small - 小标题
     *
     * 用途：小卡片标题
     */
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // ========================================================================
    // Body Styles - 正文，用于主要文本内容
    // ========================================================================

    /**
     * Body Large - 大正文
     *
     * 用途：主要文本内容
     */
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),

    /**
     * Body Medium - 中等正文
     *
     * 用途：次要文本内容
     */
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),

    /**
     * Body Small - 小正文
     *
     * 用途：辅助文本、说明文字
     */
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),

    // ========================================================================
    // Label Styles - 标签，用于按钮、标签等小文本
    // ========================================================================

    /**
     * Label Large - 大标签
     *
     * 用途：按钮文本
     */
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    /**
     * Label Medium - 中等标签
     *
     * 用途：小按钮文本
     */
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),

    /**
     * Label Small - 小标签
     *
     * 用途：标签、徽章文本
     */
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ============================================================================
// 自定义文本样式扩展
// ============================================================================

/**
 * 纪元徽章文本样式
 */
val EpochBadgeTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.W600,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
)

/**
 * 状态指示器文本样式
 */
val StatusIndicatorTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.W500,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp,
)

/**
 * 警告横幅文本样式
 */
val WarningBannerTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.W400,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp,
)

/**
 * 代码/等宽文本样式
 *
 * 用途：显示设备 ID、纪元号等
 */
val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.W400,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
)
