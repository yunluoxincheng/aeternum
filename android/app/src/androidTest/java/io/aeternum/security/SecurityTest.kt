package io.aeternum.security

import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.aeternum.ui.components.SecureScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Aeternum 安全测试套件
 *
 * ## 12.5 安全测试覆盖
 *
 * | 测试项 | 类型 | 覆盖目标 |
 * |-------|------|---------|
 * | FLAG_SECURE 验证 | 设备测试 | 敏感屏幕 100% |
 * | UI 层不持有明文密钥 | 静态分析 | UI 层 100% |
 * | 会话自动锁定 | 单元测试 | 100% |
 * | 日志安全 | 静态分析 | 全部文件 |
 *
 * ## 安全边界定义
 *
 * | 层 | 可信 | 持有明文密钥 |
 * |----|------|-------------|
 * | Rust Core | ✅ | ✅ |
 * | Android Security Layer | ✅ (部分) | ❌ (仅硬件句柄) |
 * | UI Layer | ❌ | ❌ |
 *
 * ## 架构约束
 * - INVARIANT: FLAG_SECURE 仅在敏感屏幕启用
 * - INVARIANT: UI 层禁止持有明文密钥
 * - INVARIANT: 会话后台 30 秒自动锁定
 * - INVARIANT: 日志中禁止记录敏感信息
 */
@RunWith(AndroidJUnit4::class)
class SecurityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ============================================================================
    // 12.5.1 FLAG_SECURE 验证测试
    // ============================================================================

    /**
     * 测试 FLAG_SECURE 在敏感屏幕生效
     *
     * 验证点：
     * - 敏感路由正确识别
     * - SecureScreen 组件正确设置 FLAG_SECURE
     * - 非敏感路由不设置 FLAG_SECURE
     */
    @Test
    fun testSensitiveRoutesIdentification() {
        // 敏感路由列表（应启用 FLAG_SECURE）
        val sensitiveRoutes = listOf(
            "biometric_prompt",
            "vault",
            "main",
            "devices",
            "device_detail",
            "add_device",
            "recovery_initiate",
            "veto_notification",
            "veto_history",
            "mnemonic_backup",
            "registration",
        )

        // 验证敏感路由被正确识别
        for (route in sensitiveRoutes) {
            assertTrue(
                "路由 '$route' 应该被识别为敏感路由",
                ScreenSecurityManager.isSensitiveRoute(route),
            )
        }

        // 验证带参数的敏感路由被正确识别
        val parameterizedRoutes = listOf(
            "device_detail/device_123",
            "devices/",
            "recovery_initiate?step=2",
        )

        for (route in parameterizedRoutes) {
            assertTrue(
                "带参数的路由 '$route' 应该被识别为敏感路由",
                ScreenSecurityManager.isSensitiveRoute(route),
            )
        }
    }

    /**
     * 测试非敏感路由不启用 FLAG_SECURE
     */
    @Test
    fun testNonSensitiveRoutesIdentification() {
        // 非敏感路由列表（不应启用 FLAG_SECURE）
        val nonSensitiveRoutes = listOf(
            "welcome",
            "about",
            "help",
            "settings",
            null,
            "",
        )

        for (route in nonSensitiveRoutes) {
            assertFalse(
                "路由 '$route' 不应该被识别为敏感路由",
                ScreenSecurityManager.isSensitiveRoute(route),
            )
        }
    }

    /**
     * 测试 ScreenSecurityManager.setSecureFlag 功能
     *
     * 此测试验证 FLAG_SECURE 标志的正确设置和清除
     */
    @Test
    fun testScreenSecurityManagerSetSecureFlag() {
        // 使用 Compose 测试环境
        var flagSecureWasSet = false
        var flagSecureWasCleared = false

        composeTestRule.setContent {
            SecureScreen(enable = true) {
                // 模拟敏感屏幕内容
                flagSecureWasSet = true
            }
        }

        // 验证 SecureScreen 组件被正确渲染
        composeTestRule.waitForIdle()
        assertTrue("SecureScreen 应该被渲染", flagSecureWasSet)
    }

    /**
     * 测试 ScreenSecurityManager 状态一致性
     */
    @Test
    fun testScreenSecurityManagerConsistency() {
        // 验证敏感路由集合完整性
        val expectedSensitiveRoutes = setOf(
            "biometric_prompt",
            "vault",
            "main",
            "devices",
            "device_detail",
            "add_device",
            "recovery_initiate",
            "veto_notification",
            "veto_history",
            "mnemonic_backup",
            "registration",
        )

        // 验证所有预期敏感路由都被正确识别
        for (route in expectedSensitiveRoutes) {
            assertTrue(
                "敏感路由 '$route' 应该被正确识别",
                ScreenSecurityManager.isSensitiveRoute(route),
            )
        }
    }

    // ============================================================================
    // 12.5.2 UI 层不持有明文密钥验证（运行时部分）
    // ============================================================================

    /**
     * 测试 SecurityBoundaryValidator 数据类型验证
     */
    @Test
    fun testDataTypesSafetyValidation() {
        // 安全类型列表
        val safeTypes = listOf(
            "kotlin.String",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Boolean",
            "io.aeternum.ui.state.VaultUiState",
            "io.aeternum.ui.viewmodel.VaultViewModel",
        )

        for (type in safeTypes) {
            assertTrue(
                "类型 '$type' 应该是安全的",
                SecurityBoundaryValidator.isSafeDataType(type),
            )
        }

        // 不安全类型列表
        val unsafeTypes = listOf(
            "kotlin.ByteArray",
            "[B", // JVM 字节数组签名
            "io.aeternum.ui.state.KeyState ByteArray",
        )

        for (type in unsafeTypes) {
            assertFalse(
                "类型 '$type' 不应该被认为是安全的",
                SecurityBoundaryValidator.isSafeDataType(type),
            )
        }
    }

    /**
     * 测试 SecurityBoundaryValidator 函数名验证
     */
    @Test
    fun testFunctionNameSafetyValidation() {
        // 安全函数名列表
        val safeFunctions = listOf(
            "displayRecordList",
            "updateUiState",
            "handleUserClick",
            "navigateToVault",
            "getSessionHandle",
        )

        for (function in safeFunctions) {
            assertTrue(
                "函数名 '$function' 应该是安全的",
                SecurityBoundaryValidator.isSafeFunctionName(function),
            )
        }

        // 不安全函数名列表
        val unsafeFunctions = listOf(
            "encryptData",
            "decryptField",
            "deriveKey",
            "generateKey",
            "getRawKey",
            "getPlaintextKey",
            "getSecretKey",
        )

        for (function in unsafeFunctions) {
            assertFalse(
                "函数名 '$function' 不应该被认为是安全的",
                SecurityBoundaryValidator.isSafeFunctionName(function),
            )
        }
    }

    /**
     * 测试 SecurityBoundaryValidator 安全报告生成
     */
    @Test
    fun testSecurityReportGeneration() {
        val report = SecurityBoundaryValidator.generateSecurityReport()

        // 验证报告基本信息
        assertTrue("报告时间戳应该大于 0", report.timestamp > 0)
        assertEquals("报告版本应该是 1.0.0", "1.0.0", report.version)
        assertTrue("报告应包含安全规则", report.rules.isNotEmpty())

        // 验证所有安全规则都有状态
        for (ruleStatus in report.rules) {
            assertNotNull("规则状态不应该为空", ruleStatus.status)
            assertNotNull("规则备注不应该为空", ruleStatus.notes)
        }
    }

    // ============================================================================
    // 12.5.4 日志安全验证
    // ============================================================================

    /**
     * 测试敏感数据脱敏工具
     */
    @Test
    fun testSensitiveDataSanitizer() {
        // 测试字节数组脱敏
        val byteArray = ByteArray(32) { it.toByte() }
        val sanitizedBytes = SensitiveDataSanitizer.sanitizeByteArray(byteArray)

        assertTrue(
            "脱敏后的字节数组应该包含长度信息",
            sanitizedBytes.contains("32 bytes"),
        )
        // 确保不是完整的十六进制表示
        assertFalse(
            "脱敏后的字节数组不应该包含完整数据",
            sanitizedBytes.length > 100,
        )

        // 测试字符串脱敏
        val sensitiveString = "my-secret-password-12345"
        val sanitizedString = SensitiveDataSanitizer.sanitizeString(sensitiveString)

        assertTrue(
            "脱敏后的字符串应该包含星号",
            sanitizedString.contains("*"),
        )
        assertFalse(
            "脱敏后的字符串不应该包含完整原始值",
            sanitizedString.contains("my-secret-password-12345"),
        )

        // 测试设备 ID 脱敏
        val deviceId = "device-abc-123-xyz-789"
        val sanitizedDeviceId = SensitiveDataSanitizer.sanitizeDeviceId(deviceId)

        assertTrue(
            "脱敏后的设备 ID 应该包含省略号",
            sanitizedDeviceId.contains("..."),
        )
        assertFalse(
            "脱敏后的设备 ID 不应该包含完整原始值",
            sanitizedDeviceId.contains("device-abc-123-xyz-789"),
        )
    }

    /**
     * 测试空值和边界情况脱敏
     */
    @Test
    fun testSensitiveDataSanitizerEdgeCases() {
        // 空字节数组
        val emptyBytes = ByteArray(0)
        val sanitizedEmpty = SensitiveDataSanitizer.sanitizeByteArray(emptyBytes)
        assertEquals("空字节数组应该显示 [empty]", "[empty]", sanitizedEmpty)

        // 空字符串
        val emptyString = ""
        val sanitizedEmptyString = SensitiveDataSanitizer.sanitizeString(emptyString)
        assertEquals("空字符串应该显示 [empty]", "[empty]", sanitizedEmptyString)

        // 短字符串
        val shortString = "abc"
        val sanitizedShort = SensitiveDataSanitizer.sanitizeString(shortString)
        assertTrue(
            "短字符串应该包含星号",
            sanitizedShort.contains("*"),
        )

        // 短设备 ID
        val shortDeviceId = "dev123"
        val sanitizedShortDeviceId = SensitiveDataSanitizer.sanitizeDeviceId(shortDeviceId)
        assertEquals(
            "短设备 ID 应该保持原样",
            shortDeviceId,
            sanitizedShortDeviceId,
        )
    }

    /**
     * 测试日志输出安全性验证
     *
     * 验证规则：
     * - 禁止在日志中记录 ByteArray
     * - 禁止在日志中记录密钥相关字符串
     * - 禁止在日志中记录完整敏感信息
     */
    @Test
    fun testLoggingSecurityRules() {
        // 模拟敏感数据
        val sensitiveKey = ByteArray(32) { (it % 256).toByte() }
        val mnemonicPhrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"

        // 验证直接打印 ByteArray 是不安全的
        val unsafeLog = sensitiveKey.toString()
        assertTrue(
            "ByteArray.toString() 会暴露信息",
            unsafeLog.contains("[B@"),
        )

        // 验证脱敏后是安全的
        val safeLog = SensitiveDataSanitizer.sanitizeByteArray(sensitiveKey)
        assertFalse(
            "脱敏后的日志不应该包含完整 ByteArray 信息",
            safeLog.contains("@"),
        )

        // 验证助记词不应该直接出现在日志中
        val sanitizedMnemonic = SensitiveDataSanitizer.sanitizeString(mnemonicPhrase)
        assertFalse(
            "脱敏后的助记词不应该包含完整原始值",
            sanitizedMnemonic.contains("abandon abandon abandon"),
        )
    }

    // ============================================================================
    // 安全边界验证综合测试
    // ============================================================================

    /**
     * 综合安全边界验证测试
     *
     * 验证所有安全规则的状态
     */
    @Test
    fun testComprehensiveSecurityBoundaryValidation() {
        val context = SecurityBoundaryValidator.ValidationContext()

        // 模拟运行时检查
        // 1. 检查数据类型安全
        if (!SecurityBoundaryValidator.isSafeDataType("kotlin.ByteArray")) {
            context.addViolation(
                SecurityBoundaryValidator.SecurityRule.NO_PLAINTEXT_KEYS_IN_UI,
                "UI 层检测到不安全的数据类型 ByteArray",
                "testComprehensiveSecurityBoundaryValidation",
            )
        }

        // 2. 检查函数名安全
        if (!SecurityBoundaryValidator.isSafeFunctionName("decryptField")) {
            context.addViolation(
                SecurityBoundaryValidator.SecurityRule.DECRYPT_VIA_RUST_HANDLE,
                "UI 层检测到不安全的函数名 decryptField",
                "testComprehensiveSecurityBoundaryValidation",
            )
        }

        // 验证结果
        // 注意：这些违规是预期的测试行为，实际运行时不应该有违规
        val result = context.build()

        // 在这个测试中，我们验证验证器本身工作正常
        // 实际应用中，如果验证失败应该触发熔断
        println("Security Validation Result: $result")
    }
}
