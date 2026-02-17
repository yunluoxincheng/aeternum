package io.aeternum.ui.accessibility

import io.aeternum.ui.theme.HighContrastIdle
import io.aeternum.ui.theme.HighContrastDecrypting
import io.aeternum.ui.theme.HighContrastRekeying
import io.aeternum.ui.theme.HighContrastDegraded
import io.aeternum.ui.theme.HighContrastRevoked
import io.aeternum.ui.theme.HighContrastQuantumBlue
import io.aeternum.ui.theme.getHighContrastStateColor
import org.junit.Assert.*
import org.junit.Test

/**
 * 无障碍功能单元测试
 *
 * 测试无障碍扩展、语音播报和字体缩放功能
 */
class AccessibilityTest {

    // ============================================================================
    // 语义描述测试
    // ============================================================================

    @Test
    fun `formatAccessibleTime should format hours correctly`() {
        val seconds = 3661 // 1 hour, 1 minute, 1 second
        val result = formatAccessibleTime(seconds)
        assertTrue(result.contains("1 小时"))
        assertTrue(result.contains("1 分钟"))
        assertTrue(result.contains("1 秒"))
    }

    @Test
    fun `formatAccessibleTime should format minutes correctly`() {
        val seconds = 125 // 2 minutes, 5 seconds
        val result = formatAccessibleTime(seconds)
        assertTrue(result.contains("2 分钟"))
        assertTrue(result.contains("5 秒"))
        assertFalse(result.contains("小时"))
    }

    @Test
    fun `formatAccessibleTime should format seconds only`() {
        val seconds = 45
        val result = formatAccessibleTime(seconds)
        assertTrue(result.contains("45 秒"))
        assertFalse(result.contains("分钟"))
        assertFalse(result.contains("小时"))
    }

    @Test
    fun `formatAccessibleTime should handle zero`() {
        val seconds = 0
        val result = formatAccessibleTime(seconds)
        assertTrue(result.contains("0 秒"))
    }

    // ============================================================================
    // 状态变化播报测试
    // ============================================================================

    @Test
    fun `generateStateChangeAnnouncement should generate correct message`() {
        val result = generateStateChangeAnnouncement(
            fromState = "空闲",
            toState = "已解锁",
        )
        assertEquals("状态已从 空闲 变更为 已解锁", result)
    }

    @Test
    fun `generateStateChangeAnnouncement should include reason`() {
        val result = generateStateChangeAnnouncement(
            fromState = "空闲",
            toState = "降级",
            reason = "设备完整性验证失败",
        )
        assertEquals("状态已从 空闲 变更为 降级。原因：设备完整性验证失败", result)
    }

    // ============================================================================
    // 高对比度颜色测试
    // ============================================================================

    @Test
    fun `getHighContrastStateColor returns correct colors for all states`() {
        assertEquals(HighContrastIdle, getHighContrastStateColor("idle"))
        assertEquals(HighContrastDecrypting, getHighContrastStateColor("decrypting"))
        assertEquals(HighContrastRekeying, getHighContrastStateColor("rekeying"))
        assertEquals(HighContrastDegraded, getHighContrastStateColor("degraded"))
        assertEquals(HighContrastRevoked, getHighContrastStateColor("revoked"))
    }

    @Test
    fun `getHighContrastStateColor is case insensitive`() {
        assertEquals(HighContrastIdle, getHighContrastStateColor("IDLE"))
        assertEquals(HighContrastIdle, getHighContrastStateColor("Idle"))
        assertEquals(HighContrastIdle, getHighContrastStateColor("IDle"))
    }

    @Test
    fun `getHighContrastStateColor returns default for unknown state`() {
        assertEquals(HighContrastQuantumBlue, getHighContrastStateColor("unknown"))
    }

    // ============================================================================
    // 无障碍测试清单测试
    // ============================================================================

    @Test
    fun `AccessibilityTestChecklist checkButtonHasContentDescription passes when has description`() {
        val result = AccessibilityTestChecklist.checkButtonHasContentDescription(true)
        assertTrue(result.passed)
        assertNull(result.fix)
    }

    @Test
    fun `AccessibilityTestChecklist checkButtonHasContentDescription fails when no description`() {
        val result = AccessibilityTestChecklist.checkButtonHasContentDescription(false)
        assertFalse(result.passed)
        assertNotNull(result.fix)
    }

    @Test
    fun `AccessibilityTestChecklist checkTouchTargetSize passes for valid sizes`() {
        val result = AccessibilityTestChecklist.checkTouchTargetSize(48)
        assertTrue(result.passed)
    }

    @Test
    fun `AccessibilityTestChecklist checkTouchTargetSize fails for small sizes`() {
        val result = AccessibilityTestChecklist.checkTouchTargetSize(40)
        assertFalse(result.passed)
        assertTrue(result.fix?.contains("增大") == true)
    }

    @Test
    fun `AccessibilityTestChecklist checkColorContrast passes for AAA ratio`() {
        val result = AccessibilityTestChecklist.checkColorContrast(7.5f)
        assertTrue(result.passed)
        assertTrue(result.description.contains("AAA"))
    }

    @Test
    fun `AccessibilityTestChecklist checkColorContrast passes for AA ratio`() {
        val result = AccessibilityTestChecklist.checkColorContrast(4.5f)
        assertTrue(result.passed)
        assertTrue(result.description.contains("AA"))
    }

    @Test
    fun `AccessibilityTestChecklist checkColorContrast fails for low ratio`() {
        val result = AccessibilityTestChecklist.checkColorContrast(3.0f)
        assertFalse(result.passed)
    }

    // ============================================================================
    // 无障碍测试报告测试
    // ============================================================================

    @Test
    fun `AccessibilityTestReport calculates summary correctly`() {
        val report = AccessibilityTestReport(
            componentName = "测试组件",
            results = listOf(
                TestResult(passed = true, description = "测试1"),
                TestResult(passed = true, description = "测试2"),
                TestResult(passed = false, description = "测试3", fix = "修复"),
            ),
        )
        assertEquals(2, report.passedCount)
        assertEquals(1, report.failedCount)
        assertFalse(report.allPassed)
        assertTrue(report.summary.contains("2/3"))
    }

    @Test
    fun `AccessibilityTestReport allPassed when all tests pass`() {
        val report = AccessibilityTestReport(
            componentName = "测试组件",
            results = listOf(
                TestResult(passed = true, description = "测试1"),
                TestResult(passed = true, description = "测试2"),
            ),
        )
        assertTrue(report.allPassed)
        assertEquals(2, report.passedCount)
        assertEquals(0, report.failedCount)
    }

    // ============================================================================
    // 字体缩放配置测试
    // ============================================================================

    @Test
    fun `FontScaleSettings detects large font correctly`() {
        val settings = FontScaleSettings(
            fontScale = 1.2f,
            isLargeFont = true,
            isExtraLargeFont = false,
            adjustedLineHeightMultiplier = 1.2f,
            adjustedPaddingMultiplier = 1.15f,
        )
        assertTrue(settings.isLargeFont)
        assertFalse(settings.isExtraLargeFont)
    }

    @Test
    fun `FontScaleSettings detects extra large font correctly`() {
        val settings = FontScaleSettings(
            fontScale = 1.5f,
            isLargeFont = true,
            isExtraLargeFont = true,
            adjustedLineHeightMultiplier = 1.3f,
            adjustedPaddingMultiplier = 1.25f,
        )
        assertTrue(settings.isLargeFont)
        assertTrue(settings.isExtraLargeFont)
    }

    // ============================================================================
    // 大字体布局调整测试
    // ============================================================================

    @Test
    fun `LargeFontLayoutAdjustments adjusts min height for extra large font`() {
        val settings = FontScaleSettings(
            fontScale = 1.5f,
            isLargeFont = true,
            isExtraLargeFont = true,
            adjustedLineHeightMultiplier = 1.3f,
            adjustedPaddingMultiplier = 1.25f,
        )
        val adjustedHeight = LargeFontLayoutAdjustments.adjustMinHeight(48, settings)
        assertTrue(adjustedHeight >= 48) // 应该大于等于原始值
    }

    @Test
    fun `LargeFontLayoutAdjustments reduces column count for large font`() {
        val normalSettings = FontScaleSettings.Default
        val largeFontSettings = FontScaleSettings(
            fontScale = 1.2f,
            isLargeFont = true,
            isExtraLargeFont = false,
            adjustedLineHeightMultiplier = 1.2f,
            adjustedPaddingMultiplier = 1.15f,
        )

        val normalColumns = LargeFontLayoutAdjustments.getSuggestedColumnCount(3, normalSettings)
        val largeColumns = LargeFontLayoutAdjustments.getSuggestedColumnCount(3, largeFontSettings)

        assertEquals(3, normalColumns)
        assertTrue(largeColumns <= normalColumns)
    }

    @Test
    fun `LargeFontLayoutAdjustments disables compact layout for large font`() {
        val normalSettings = FontScaleSettings.Default
        val largeFontSettings = FontScaleSettings(
            fontScale = 1.2f,
            isLargeFont = true,
            isExtraLargeFont = false,
            adjustedLineHeightMultiplier = 1.2f,
            adjustedPaddingMultiplier = 1.15f,
        )

        assertTrue(LargeFontLayoutAdjustments.shouldUseCompactLayout(normalSettings))
        assertFalse(LargeFontLayoutAdjustments.shouldUseCompactLayout(largeFontSettings))
    }

    // ============================================================================
    // 无障碍配置测试
    // ============================================================================

    @Test
    fun `AccessibilityConfig needsEnhancedAccessibility when TalkBack enabled`() {
        val config = AccessibilityConfig(
            talkBackEnabled = true,
            highContrastEnabled = false,
            accessibilityEnabled = true,
        )
        assertTrue(config.needsEnhancedAccessibility)
        assertTrue(config.needsLargerTouchTargets)
    }

    @Test
    fun `AccessibilityConfig needsEnhancedAccessibility when high contrast enabled`() {
        val config = AccessibilityConfig(
            talkBackEnabled = false,
            highContrastEnabled = true,
            accessibilityEnabled = true,
        )
        assertTrue(config.needsEnhancedAccessibility)
        assertTrue(config.needsClearerVisualFeedback)
    }

    @Test
    fun `AccessibilityConfig no enhanced needs when nothing enabled`() {
        val config = AccessibilityConfig(
            talkBackEnabled = false,
            highContrastEnabled = false,
            accessibilityEnabled = false,
        )
        assertFalse(config.needsEnhancedAccessibility)
        assertFalse(config.needsLargerTouchTargets)
        assertFalse(config.needsClearerVisualFeedback)
    }

    // ============================================================================
    // 状态播报文本测试
    // ============================================================================

    @Test
    fun `StateAnnouncements provides correct Chinese descriptions`() {
        assertEquals("空闲", StateAnnouncements.getStateDescription("idle"))
        assertEquals("活跃", StateAnnouncements.getStateDescription("active"))
        assertEquals("正在解密", StateAnnouncements.getStateDescription("decrypting"))
        assertEquals("正在轮换密钥", StateAnnouncements.getStateDescription("rekeying"))
        assertEquals("安全降级模式", StateAnnouncements.getStateDescription("degraded"))
        assertEquals("已撤销", StateAnnouncements.getStateDescription("revoked"))
    }

    @Test
    fun `StateAnnouncements returns original for unknown states`() {
        assertEquals("unknown", StateAnnouncements.getStateDescription("unknown"))
    }

    // ============================================================================
    // 触摸目标尺寸测试
    // ============================================================================

    @Test
    fun `AccessibilityTouchTargets defines correct minimum sizes`() {
        assertEquals(48, AccessibilityTouchTargets.MIN_TOUCH_TARGET_DP)
        assertEquals(56, AccessibilityTouchTargets.LARGE_TOUCH_TARGET_DP)
        assertEquals(56, AccessibilityTouchTargets.MIN_LIST_ITEM_HEIGHT_DP)
    }
}
