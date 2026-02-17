package io.aeternum.ui.accessibility

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 字体缩放感知的间距组件
 *
 * 提供根据字体缩放自动调整的间距，确保大字体模式下布局不拥挤。
 *
 * ## 设计理念
 * - **自适应**: 间距随字体大小自动调整
 * - **一致性**: 统一的间距规范
 * - **可读性**: 确保大字体时内容有足够呼吸空间
 */

// ============================================================================
// 标准间距定义
// ============================================================================

/**
 * 标准间距尺寸
 */
object SpacingTokens {
    /** 无间距 */
    val None = 0.dp

    /** 极小间距 - 2dp */
    val ExtraSmall = 2.dp

    /** 小间距 - 4dp */
    val Small = 4.dp

    /** 中小间距 - 8dp */
    val MediumSmall = 8.dp

    /** 中等间距 - 12dp */
    val Medium = 12.dp

    /** 中大间距 - 16dp */
    val MediumLarge = 16.dp

    /** 大间距 - 24dp */
    val Large = 24.dp

    /** 超大间距 - 32dp */
    val ExtraLarge = 32.dp

    /** 巨大间距 - 48dp */
    val Huge = 48.dp

    // 特殊用途间距

    /** 列表项内边距 - 垂直 */
    val ListItemVertical = 12.dp

    /** 列表项内边距 - 水平 */
    val ListItemHorizontal = 16.dp

    /** 卡片内边距 */
    val CardPadding = 16.dp

    /** 屏幕边距 */
    val ScreenPadding = 16.dp

    /** 按钮内边距 - 水平 */
    val ButtonHorizontal = 24.dp

    /** 按钮内边距 - 垂直 */
    val ButtonVertical = 12.dp
}

// ============================================================================
// 字体缩放感知间距计算
// ============================================================================

/**
 * 计算字体缩放后的间距
 *
 * @param baseSpacing 基础间距
 * @param settings 字体缩放设置
 * @param minMultiplier 最小乘数（防止间距过小）
 * @param maxMultiplier 最大乘数（防止间距过大）
 * @return 调整后的间距
 */
fun scaledSpacing(
    baseSpacing: Dp,
    settings: FontScaleSettings,
    minMultiplier: Float = 1.0f,
    maxMultiplier: Float = 1.5f,
): Dp {
    val multiplier = settings.adjustedPaddingMultiplier.coerceIn(minMultiplier, maxMultiplier)
    return (baseSpacing.value * multiplier).dp
}

/**
 * 字体缩放感知的内边距
 */
@Composable
fun scaledPaddingValues(
    baseVertical: Dp,
    baseHorizontal: Dp,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
): PaddingValues {
    return PaddingValues(
        vertical = scaledSpacing(baseVertical, settings),
        horizontal = scaledSpacing(baseHorizontal, settings),
    )
}

/**
 * 字体缩放感知的内边距（四个方向相同）
 */
@Composable
fun scaledPaddingValues(
    basePadding: Dp,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
): PaddingValues {
    return PaddingValues(scaledSpacing(basePadding, settings))
}

/**
 * 字体缩放感知的内边距（四个方向独立）
 */
@Composable
fun scaledPaddingValues(
    baseTop: Dp,
    baseBottom: Dp,
    baseStart: Dp,
    baseEnd: Dp,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
): PaddingValues {
    return PaddingValues(
        top = scaledSpacing(baseTop, settings),
        bottom = scaledSpacing(baseBottom, settings),
        start = scaledSpacing(baseStart, settings),
        end = scaledSpacing(baseEnd, settings),
    )
}

// ============================================================================
// 修饰符扩展
// ============================================================================

/**
 * 添加字体缩放感知的内边距
 */
@Composable
fun Modifier.scaledPadding(
    basePadding: Dp,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
): Modifier {
    return this.padding(scaledSpacing(basePadding, settings))
}

/**
 * 添加字体缩放感知的内边距（水平和垂直）
 */
@Composable
fun Modifier.scaledPadding(
    baseVertical: Dp,
    baseHorizontal: Dp,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
): Modifier {
    return this.padding(
        vertical = scaledSpacing(baseVertical, settings),
        horizontal = scaledSpacing(baseHorizontal, settings),
    )
}

// ============================================================================
// 字体缩放感知的 Spacer
// ============================================================================

/**
 * 字体缩放感知的垂直间距
 */
@Composable
fun ScaledVerticalSpacer(
    baseHeight: Dp,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
) {
    Spacer(modifier = Modifier.height(scaledSpacing(baseHeight, settings)))
}

/**
 * 字体缩放感知的水平间距
 */
@Composable
fun ScaledHorizontalSpacer(
    baseWidth: Dp,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
) {
    Spacer(modifier = Modifier.width(scaledSpacing(baseWidth, settings)))
}

// ============================================================================
// 预设间距组件
// ============================================================================

/**
 * 预设垂直间距
 */
@Composable
fun VerticalSpacing(
    size: SpacingSize = SpacingSize.Medium,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
) {
    val baseHeight = when (size) {
        SpacingSize.ExtraSmall -> SpacingTokens.ExtraSmall
        SpacingSize.Small -> SpacingTokens.Small
        SpacingSize.MediumSmall -> SpacingTokens.MediumSmall
        SpacingSize.Medium -> SpacingTokens.Medium
        SpacingSize.MediumLarge -> SpacingTokens.MediumLarge
        SpacingSize.Large -> SpacingTokens.Large
        SpacingSize.ExtraLarge -> SpacingTokens.ExtraLarge
    }
    ScaledVerticalSpacer(baseHeight, settings)
}

/**
 * 预设水平间距
 */
@Composable
fun HorizontalSpacing(
    size: SpacingSize = SpacingSize.Medium,
    settings: FontScaleSettings = LocalFontScaleSettings.current,
) {
    val baseWidth = when (size) {
        SpacingSize.ExtraSmall -> SpacingTokens.ExtraSmall
        SpacingSize.Small -> SpacingTokens.Small
        SpacingSize.MediumSmall -> SpacingTokens.MediumSmall
        SpacingSize.Medium -> SpacingTokens.Medium
        SpacingSize.MediumLarge -> SpacingTokens.MediumLarge
        SpacingSize.Large -> SpacingTokens.Large
        SpacingSize.ExtraLarge -> SpacingTokens.ExtraLarge
    }
    ScaledHorizontalSpacer(baseWidth, settings)
}

/**
 * 间距尺寸枚举
 */
enum class SpacingSize {
    ExtraSmall,
    Small,
    MediumSmall,
    Medium,
    MediumLarge,
    Large,
    ExtraLarge,
}

// ============================================================================
// 列表和卡片专用间距
// ============================================================================

/**
 * 列表项内边距（自动适应字体缩放）
 */
@Composable
fun listItemPadding(
    settings: FontScaleSettings = LocalFontScaleSettings.current,
): PaddingValues {
    // 列表项在大字体时保持相对紧凑，避免过度拉伸
    val factor = if (settings.isExtraLargeFont) 1.1f else 1.0f
    return PaddingValues(
        vertical = (SpacingTokens.ListItemVertical.value * factor).dp,
        horizontal = (SpacingTokens.ListItemHorizontal.value * factor).dp,
    )
}

/**
 * 卡片内边距（自动适应字体缩放）
 */
@Composable
fun cardPadding(
    settings: FontScaleSettings = LocalFontScaleSettings.current,
): PaddingValues {
    return scaledPaddingValues(
        baseVertical = SpacingTokens.CardPadding,
        baseHorizontal = SpacingTokens.CardPadding,
        settings = settings,
    )
}

/**
 * 屏幕边距（自动适应字体缩放）
 */
@Composable
fun screenPadding(
    settings: FontScaleSettings = LocalFontScaleSettings.current,
): PaddingValues {
    return scaledPaddingValues(
        baseVertical = SpacingTokens.ScreenPadding,
        baseHorizontal = SpacingTokens.ScreenPadding,
        settings = settings,
    )
}
