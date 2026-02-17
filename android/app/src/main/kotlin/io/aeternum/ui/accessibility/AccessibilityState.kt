package io.aeternum.ui.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 无障碍服务状态检测
 *
 * 用于检测 TalkBack 等辅助功能是否启用
 */
class AccessibilityStateDetector(context: Context) {

    private val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    /**
     * 检查 TalkBack（触摸浏览）是否启用
     */
    fun isTalkBackEnabled(): Boolean {
        return accessibilityManager.isTouchExplorationEnabled
    }

    /**
     * 检查高对比度文本是否启用
     *
     * 注意: isHighTextContrastEnabled 在较新 API 中可能不可用，
     * 这里提供默认实现返回 false
     */
    fun isHighContrastTextEnabled(): Boolean {
        return try {
            // 尝试使用反射调用可能存在的方法
            val method = accessibilityManager.javaClass.getMethod("isHighTextContrastEnabled")
            method.invoke(accessibilityManager) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查是否有任何辅助功能服务启用
     */
    fun isAccessibilityEnabled(): Boolean {
        return accessibilityManager.isEnabled
    }

    /**
     * 获取推荐的 UI 配置
     */
    fun getRecommendedConfig(): AccessibilityConfig {
        return AccessibilityConfig(
            talkBackEnabled = isTalkBackEnabled(),
            highContrastEnabled = isHighContrastTextEnabled(),
            accessibilityEnabled = isAccessibilityEnabled(),
        )
    }
}

/**
 * 无障碍配置
 */
data class AccessibilityConfig(
    val talkBackEnabled: Boolean,
    val highContrastEnabled: Boolean,
    val accessibilityEnabled: Boolean,
) {
    /**
     * 是否需要增强无障碍支持
     */
    val needsEnhancedAccessibility: Boolean
        get() = talkBackEnabled || highContrastEnabled

    /**
     * 是否需要更明显的触摸目标
     */
    val needsLargerTouchTargets: Boolean
        get() = talkBackEnabled

    /**
     * 是否需要更清晰的视觉反馈
     */
    val needsClearerVisualFeedback: Boolean
        get() = highContrastEnabled
}

/**
 * 记住无障碍状态
 */
@Composable
fun rememberAccessibilityState(): AccessibilityConfig {
    val context = LocalContext.current
    val detector = remember { AccessibilityStateDetector(context) }
    var config by remember { mutableStateOf(detector.getRecommendedConfig()) }

    // 监听配置变化
    DisposableEffect(Unit) {
        // 实际应用中应注册 AccessibilityManager.AccessibilityStateChangeListener
        config = detector.getRecommendedConfig()
        onDispose {
            // 清理监听器
        }
    }

    return config
}

/**
 * 根据无障碍状态调整内容
 */
@Composable
fun AccessibilityAwareContent(
    content: @Composable AccessibilityConfig.() -> Unit,
) {
    val config = rememberAccessibilityState()
    content(config)
}

// ============================================================================
// 触摸目标尺寸
// ============================================================================

/**
 * 无障碍友好的最小触摸目标尺寸
 */
object AccessibilityTouchTargets {
    /**
     * 最小触摸目标尺寸（Material 推荐 48dp）
     */
    const val MIN_TOUCH_TARGET_DP = 48

    /**
     * 大触摸目标尺寸（适合 TalkBack 用户）
     */
    const val LARGE_TOUCH_TARGET_DP = 56

    /**
     * 列表项最小高度
     */
    const val MIN_LIST_ITEM_HEIGHT_DP = 56
}

// ============================================================================
// 无障碍测试工具
// ============================================================================

/**
 * 无障碍测试检查项
 */
object AccessibilityTestChecklist {

    /**
     * 检查按钮是否有内容描述
     */
    fun checkButtonHasContentDescription(hasDescription: Boolean): TestResult {
        return TestResult(
            passed = hasDescription,
            description = "按钮有内容描述",
            fix = if (!hasDescription) "添加 contentDescription 或使用语义修饰符" else null,
        )
    }

    /**
     * 检查触摸目标尺寸
     */
    fun checkTouchTargetSize(sizeDp: Int): TestResult {
        val minSize = AccessibilityTouchTargets.MIN_TOUCH_TARGET_DP
        return TestResult(
            passed = sizeDp >= minSize,
            description = "触摸目标尺寸 >= $minSize dp (当前: $sizeDp dp)",
            fix = if (sizeDp < minSize) "增大触摸目标尺寸至至少 $minSize dp" else null,
        )
    }

    /**
     * 检查颜色对比度
     */
    fun checkColorContrast(ratio: Float): TestResult {
        val minRatio = 4.5f // WCAG AA
        val minRatioAAA = 7.0f // WCAG AAA
        return TestResult(
            passed = ratio >= minRatio,
            description = "对比度 ${String.format("%.1f", ratio)}:1 (${if (ratio >= minRatioAAA) "AAA" else if (ratio >= minRatio) "AA" else "不达标"})",
            fix = if (ratio < minRatio) "提高前景/背景对比度至至少 4.5:1" else null,
        )
    }

    /**
     * 检查是否支持焦点导航
     */
    fun checkFocusNavigation(isFocusable: Boolean): TestResult {
        return TestResult(
            passed = isFocusable,
            description = "支持键盘/焦点导航",
            fix = if (!isFocusable) "确保元素可通过 focusable() 修饰符获得焦点" else null,
        )
    }
}

/**
 * 测试结果
 */
data class TestResult(
    val passed: Boolean,
    val description: String,
    val fix: String? = null,
)

/**
 * 无障碍测试报告
 */
data class AccessibilityTestReport(
    val componentName: String,
    val results: List<TestResult>,
) {
    val allPassed: Boolean
        get() = results.all { it.passed }

    val passedCount: Int
        get() = results.count { it.passed }

    val failedCount: Int
        get() = results.count { !it.passed }

    val summary: String
        get() = "$componentName: $passedCount/${results.size} 检查通过"
}
