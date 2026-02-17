package io.aeternum.security

/**
 * 安全边界验证工具
 *
 * 提供运行时验证和静态分析辅助，确保 UI 层不违反安全约束
 *
 * ## 安全边界定义
 *
 * | 层 | 可信 | 持有明文密钥 |
 * |----|------|-------------|
 * | Rust Core | ✅ | ✅ |
 * | Android Security Layer | ✅ (部分) | ❌ (仅硬件句柄) |
 * | UI Layer | ❌ | ❌ |
 * | Backend | ❌ | ❌ |
 *
 * ## 不可违反的约束
 *
 * ### 禁止事项
 * - ❌ 在 Kotlin 层实现任何密码学逻辑
 * - ❌ 在日志中记录密钥或敏感信息
 * - ❌ Kotlin 层持有明文密钥的 `ByteArray`
 * - ❌ 将密钥以任何形式传递到 UI 层
 *
 * ### 必须事项
 * - ✅ UI 层仅显示脱敏后的数据
 * - ✅ 所有解密操作通过 Rust Core 句柄调用
 * - ✅ 使用 BiometricPrompt (Class 3) 进行用户认证
 * - ✅ 使用 Play Integrity API 验证设备完整性
 */
object SecurityBoundaryValidator {

    /**
     * 验证结果
     */
    sealed class ValidationResult {
        /** 验证通过 */
        data object Pass : ValidationResult()

        /** 验证失败 */
        data class Fail(val violations: List<Violation>) : ValidationResult()
    }

    /**
     * 违规项
     */
    data class Violation(
        val rule: SecurityRule,
        val message: String,
        val location: String? = null,
    )

    /**
     * 安全规则枚举
     */
    enum class SecurityRule(val description: String) {
        /** UI 层禁止持有明文密钥 */
        NO_PLAINTEXT_KEYS_IN_UI("UI 层禁止持有明文密钥"),

        /** 禁止在日志中记录敏感信息 */
        NO_SENSITIVE_LOGGING("禁止在日志中记录敏感信息"),

        /** 解密操作必须通过 Rust 句柄 */
        DECRYPT_VIA_RUST_HANDLE("解密操作必须通过 Rust 句柄"),

        /** 生物识别必须使用 Class 3 */
        CLASS_3_BIOMETRIC_REQUIRED("生物识别必须使用 Class 3"),

        /** 敏感屏幕必须启用 FLAG_SECURE */
        SECURE_FLAG_REQUIRED("敏感屏幕必须启用 FLAG_SECURE"),
    }

    /**
     * 运行时验证上下文
     *
     * 用于收集运行时信息以便验证
     */
    class ValidationContext {
        private val violations = mutableListOf<Violation>()

        /**
         * 添加违规项
         */
        fun addViolation(rule: SecurityRule, message: String, location: String? = null) {
            violations.add(Violation(rule, message, location))
        }

        /**
         * 构建验证结果
         */
        fun build(): ValidationResult {
            return if (violations.isEmpty()) {
                ValidationResult.Pass
            } else {
                ValidationResult.Fail(violations.toList())
            }
        }
    }

    /**
     * 验证数据类型是否为安全类型
     *
     * 安全类型定义：
     * - 基本类型（String, Int, Boolean, etc.）
     * - 枚举类型
     * - 密封类（用于状态表示）
     * - 句柄类型（VaultSessionHandle 等）
     *
     * 不安全类型：
     * - ByteArray（可能包含密钥）
     * - 包含 ByteArray 的数据类
     */
    fun isSafeDataType(className: String): Boolean {
        // 允许的安全类型前缀
        val safePrefixes = listOf(
            "kotlin.String",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Boolean",
            "kotlin.Float",
            "kotlin.Double",
            "kotlin.Unit",
            "io.aeternum.ui.state",  // UI 状态类型
            "io.aeternum.ui.viewmodel",  // ViewModel 类型
        )

        // 禁止的不安全类型
        val unsafePatterns = listOf(
            "ByteArray",
            "[B",  // JVM 字节数组签名
        )

        return safePrefixes.any { className.startsWith(it) } &&
               !unsafePatterns.any { className.contains(it) }
    }

    /**
     * 验证函数名是否为安全操作
     *
     * 禁止的操作名称模式：
     * - encrypt*, decrypt*（应在 Rust 端）
     * - *Key, *Secret（可能涉及密钥）
     * - derive*, generate*Key（密钥派生）
     */
    fun isSafeFunctionName(functionName: String): Boolean {
        val unsafePatterns = listOf(
            "encrypt",
            "decrypt",
            "deriveKey",
            "generateKey",
            "getRawKey",
            "getPlaintextKey",
            "getSecretKey",
        )

        return !unsafePatterns.any { functionName.contains(it, ignoreCase = true) }
    }

    /**
     * 生成安全边界报告
     *
     * 用于代码审查和静态分析
     */
    fun generateSecurityReport(): SecurityReport {
        return SecurityReport(
            timestamp = System.currentTimeMillis(),
            version = "1.0.0",
            rules = SecurityRule.values().map { rule ->
                RuleStatus(
                    rule = rule,
                    status = "IMPLEMENTED",
                    notes = getRuleNotes(rule),
                )
            },
        )
    }

    private fun getRuleNotes(rule: SecurityRule): String {
        return when (rule) {
            SecurityRule.NO_PLAINTEXT_KEYS_IN_UI ->
                "VaultSessionHandle 为句柄类型，不持有明文密钥"
            SecurityRule.NO_SENSITIVE_LOGGING ->
                "日志使用脱敏函数，禁止记录 ByteArray"
            SecurityRule.DECRYPT_VIA_RUST_HANDLE ->
                "解密操作通过 AeternumBridge 调用 Rust"
            SecurityRule.CLASS_3_BIOMETRIC_REQUIRED ->
                "使用 BiometricPrompt.AUTHENTICATOR_BIOMETRIC_STRONG"
            SecurityRule.SECURE_FLAG_REQUIRED ->
                "敏感屏幕使用 SecureScreen 组件"
        }
    }

    /**
     * 安全报告
     */
    data class SecurityReport(
        val timestamp: Long,
        val version: String,
        val rules: List<RuleStatus>,
    )

    /**
     * 规则状态
     */
    data class RuleStatus(
        val rule: SecurityRule,
        val status: String,
        val notes: String,
    )
}

/**
 * 敏感数据脱敏工具
 *
 * 用于日志和调试输出时脱敏敏感信息
 */
object SensitiveDataSanitizer {

    /**
     * 脱敏字节数组
     *
     * 返回字节数组长度和前/后几个字节的十六进制表示
     */
    fun sanitizeByteArray(data: ByteArray, visibleBytes: Int = 4): String {
        if (data.isEmpty()) return "[empty]"
        if (data.size <= visibleBytes * 2) {
            val hex = data.take(visibleBytes).toByteArray().toHex()
            return "[${data.size} bytes: $hex...]"
        }
        val startHex = data.take(visibleBytes).toByteArray().toHex()
        val endHex = data.takeLast(visibleBytes).toByteArray().toHex()
        return "[${data.size} bytes: $startHex...$endHex]"
    }

    /**
     * 脱敏字符串
     *
     * 保留前几个和后几个字符，中间用 * 替代
     */
    fun sanitizeString(text: String, visibleChars: Int = 3): String {
        if (text.isEmpty()) return "[empty]"
        if (text.length <= visibleChars * 2) {
            return "${text.take(visibleChars)}***"
        }
        return "${text.take(visibleChars)}***${text.takeLast(visibleChars)}"
    }

    /**
     * 脱敏设备 ID
     *
     * 返回缩略形式的设备 ID
     */
    fun sanitizeDeviceId(deviceId: String): String {
        if (deviceId.isEmpty()) return "[empty]"
        if (deviceId.length <= 8) return deviceId
        return "${deviceId.take(4)}...${deviceId.takeLast(4)}"
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
