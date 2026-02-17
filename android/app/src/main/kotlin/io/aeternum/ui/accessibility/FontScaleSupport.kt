package io.aeternum.ui.accessibility

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * 字体缩放配置
 *
 * 提供适应系统字体缩放设置的 UI 调整
 *
 * ## 设计理念
 * - **系统适配**: 遵循用户系统字体缩放设置
 * - **布局弹性**: 组件间距随字体大小调整
 * - **可读性优先**: 大字体时确保内容不被截断
 */
object FontScaleConfig {

    /**
     * 正常字体缩放阈值
     */
    const val NORMAL_SCALE = 1.0f

    /**
     * 大字体缩放阈值
     */
    const val LARGE_SCALE = 1.15f

    /**
     * 特大字体缩放阈值
     */
    const val EXTRA_LARGE_SCALE = 1.3f

    /**
     * 最大支持的字体缩放
     */
    const val MAX_SCALE = 2.0f
}

/**
 * 字体缩放配置数据
 */
data class FontScaleSettings(
    val fontScale: Float,
    val isLargeFont: Boolean,
    val isExtraLargeFont: Boolean,
    val adjustedLineHeightMultiplier: Float,
    val adjustedPaddingMultiplier: Float,
) {
    companion object {
        val Default = FontScaleSettings(
            fontScale = 1.0f,
            isLargeFont = false,
            isExtraLargeFont = false,
            adjustedLineHeightMultiplier = 1.0f,
            adjustedPaddingMultiplier = 1.0f,
        )
    }
}

/**
 * 本地字体缩放配置
 */
val LocalFontScaleSettings = staticCompositionLocalOf { FontScaleSettings.Default }

/**
 * 获取当前字体缩放配置
 */
@Composable
fun rememberFontScaleSettings(): FontScaleSettings {
    val configuration = LocalConfiguration.current
    val fontScale = configuration.fontScale

    val isLargeFont = fontScale >= FontScaleConfig.LARGE_SCALE
    val isExtraLargeFont = fontScale >= FontScaleConfig.EXTRA_LARGE_SCALE

    // 大字体时增加行高，防止文字重叠
    val lineHeightMultiplier = when {
        isExtraLargeFont -> 1.3f
        isLargeFont -> 1.2f
        else -> 1.0f
    }

    // 大字体时增加内边距，确保触摸目标足够大
    val paddingMultiplier = when {
        isExtraLargeFont -> 1.25f
        isLargeFont -> 1.15f
        else -> 1.0f
    }

    return FontScaleSettings(
        fontScale = fontScale.coerceIn(0.85f, FontScaleConfig.MAX_SCALE),
        isLargeFont = isLargeFont,
        isExtraLargeFont = isExtraLargeFont,
        adjustedLineHeightMultiplier = lineHeightMultiplier,
        adjustedPaddingMultiplier = paddingMultiplier,
    )
}

/**
 * 提供字体缩放配置的 Composable
 */
@Composable
fun FontScaleAwareContent(
    content: @Composable FontScaleSettings.() -> Unit,
) {
    val settings = rememberFontScaleSettings()
    CompositionLocalProvider(LocalFontScaleSettings provides settings) {
        content(settings)
    }
}

/**
 * 适应字体缩放的文本样式
 *
 * @param baseStyle 基础样式
 * @return 调整后的样式
 */
@Composable
fun scaledTextStyle(baseStyle: TextStyle): TextStyle {
    val settings = LocalFontScaleSettings.current

    // 如果字体特别大，可能需要调整行高
    return if (settings.isLargeFont) {
        baseStyle.copy(
            lineHeight = (baseStyle.fontSize.value * settings.adjustedLineHeightMultiplier).sp,
        )
    } else {
        baseStyle
    }
}

// ============================================================================
// 大字体布局调整工具
// ============================================================================

/**
 * 大字体布局调整
 */
object LargeFontLayoutAdjustments {

    /**
     * 根据字体缩放调整最小高度
     *
     * @param baseHeightDp 基础高度（dp）
     * @param settings 字体缩放设置
     * @return 调整后的高度
     */
    fun adjustMinHeight(baseHeightDp: Int, settings: FontScaleSettings): Int {
        return (baseHeightDp * settings.adjustedPaddingMultiplier).toInt()
    }

    /**
     * 根据字体缩放调整内边距
     *
     * @param basePaddingDp 基础内边距（dp）
     * @param settings 字体缩放设置
     * @return 调整后的内边距
     */
    fun adjustPadding(basePaddingDp: Int, settings: FontScaleSettings): Int {
        // 大字体时不增加太多内边距，避免 UI 过于稀疏
        val factor = if (settings.isExtraLargeFont) 1.1f else 1.0f
        return (basePaddingDp * factor).toInt()
    }

    /**
     * 判断是否应该使用紧凑布局
     *
     * 大字体时禁用紧凑布局
     */
    fun shouldUseCompactLayout(settings: FontScaleSettings): Boolean {
        return !settings.isLargeFont
    }

    /**
     * 获取建议的列数
     *
     * 大字体时减少列数，确保内容可读
     */
    fun getSuggestedColumnCount(
        baseColumns: Int,
        settings: FontScaleSettings,
    ): Int {
        return when {
            settings.isExtraLargeFont -> maxOf(1, baseColumns - 1)
            settings.isLargeFont -> maxOf(1, baseColumns - 1)
            else -> baseColumns
        }
    }
}

// ============================================================================
// 使用示例
// ============================================================================

/**
 * 示例：使用字体缩放配置
 *
 * ```kotlin
 * @Composable
 * fun MyText(text: String) {
 *     val settings = LocalFontScaleSettings.current
 *     val style = scaledTextStyle(MaterialTheme.typography.bodyLarge)
 *
 *     Text(
 *         text = text,
 *         style = style,
 *         modifier = Modifier.padding(
 *             vertical = (8 * settings.adjustedPaddingMultiplier).dp
 *         )
 *     )
 * }
 * ```
 */

/**
 * 示例：使用大字体布局调整
 *
 * ```kotlin
 * @Composable
 * fun MyList() {
 *     val settings = LocalFontScaleSettings.current
 *     val columns = LargeFontLayoutAdjustments.getSuggestedColumnCount(3, settings)
 *
 *     LazyVerticalGrid(
 *         columns = GridCells.Fixed(columns),
 *         contentPadding = PaddingValues(
 *             vertical = LargeFontLayoutAdjustments.adjustPadding(16, settings).dp
 *         )
 *     ) {
 *         // items...
 *     }
 * }
 * ```
 */
