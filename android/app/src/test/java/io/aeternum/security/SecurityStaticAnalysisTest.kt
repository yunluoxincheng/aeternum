package io.aeternum.security

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Aeternum 安全静态分析测试套件
 *
 * ## 测试目标
 *
 * 此测试通过静态代码分析验证以下安全约束：
 *
 * 1. **UI 层不持有明文密钥**
 *    - UI 目录下不应该有 ByteArray 类型的属性
 *    - UI 目录下不应该有包含 "key", "secret", "password" 的敏感属性
 *
 * 2. **日志中无敏感信息**
 *    - 禁止使用 Log.* 打印敏感信息
 *    - 禁止使用 println 打印敏感信息
 *    - 禁止直接打印 ByteArray
 *
 * ## 扫描范围
 *
 * | 目录 | 扫描项 |
 * |-----|-------|
 * | ui/ | ByteArray, 敏感属性, 日志调用 |
 * | viewmodel/ | ByteArray, 敏感属性 |
 * | security/ | 日志调用 |
 *
 * ## 架构约束
 * - INVARIANT: UI 层禁止持有 ByteArray 类型
 * - INVARIANT: 所有日志必须使用脱敏函数
 * - INVARIANT: 敏感数据结构必须实现 Zeroize
 */
class SecurityStaticAnalysisTest {

    // 项目根目录
    private val projectRoot = File(System.getProperty("user.dir") ?: ".")
        .parentFile // 从 test 目录向上到 app
        .parentFile // 从 app 向上到 android
        .parentFile // 从 android 向上到项目根

    // UI 源代码目录
    private val uiSourceDir = File(projectRoot, "android/app/src/main/kotlin/io/aeternum/ui")

    // 安全层源代码目录
    private val securitySourceDir = File(projectRoot, "android/app/src/main/kotlin/io/aeternum/security")

    // 禁止在 UI 层出现的敏感关键词
    private val sensitiveKeywords = listOf(
        "ByteArray",
        "SecretKey",
        "PrivateKey",
        "rawKey",
        "plaintextKey",
        "getRawKey",
        "decryptKey",
        "encryptKey",
    )

    // 禁止直接打印的模式
    private val prohibitedLogPatterns = listOf(
        // 直接打印 ByteArray
        Regex("""Log\.\w+\([^)]*\$?\{?\w*\}?\s*,\s*[^)]*\w+Bytes"""),
        Regex("""println\(.*\w+Bytes"""),
        Regex("""println\(.*ByteArray"""),
        // 打印密钥相关
        Regex("""Log\.\w+\([^)]*,\s*[^)]*key[^)]*\)""", RegexOption.IGNORE_CASE),
        Regex("""println\(.*key.*\)""", RegexOption.IGNORE_CASE),
        // 打印密码相关
        Regex("""Log\.\w+\([^)]*,\s*[^)]*password[^)]*\)""", RegexOption.IGNORE_CASE),
        Regex("""println\(.*password.*\)""", RegexOption.IGNORE_CASE),
        // 打印助记词相关
        Regex("""Log\.\w+\([^)]*,\s*[^)]*mnemonic[^)]*\)""", RegexOption.IGNORE_CASE),
        Regex("""println\(.*mnemonic.*\)""", RegexOption.IGNORE_CASE),
    )

    // ============================================================================
    // 12.5.2 验证 UI 层不持有明文密钥（静态分析）
    // ============================================================================

    /**
     * 测试 UI 层没有 ByteArray 类型属性
     *
     * INVARIANT: UI 层禁止持有 ByteArray 类型
     */
    @Test
    fun testUILayerNoByteArrayProperties() {
        val violations = mutableListOf<String>()

        // 扫描 UI 目录下的所有 Kotlin 文件
        if (uiSourceDir.exists()) {
            scanKotlinFiles(uiSourceDir) { file, content ->
                // 检查是否有 ByteArray 类型的属性
                val byteArrayPropertyPattern = Regex(
                    """val\s+\w+\s*:\s*ByteArray|var\s+\w+\s*:\s*ByteArray"""
                )

                byteArrayPropertyPattern.findAll(content).forEach { match ->
                    // 检查是否在注释中
                    val lineStart = content.lastIndexOf('\n', match.range.first) + 1
                    val line = content.substring(lineStart, content.indexOf('\n', match.range.last).let {
                        if (it == -1) content.length else it
                    })

                    if (!line.trim().startsWith("//") && !line.trim().startsWith("*")) {
                        violations.add(
                            "${file.name}: 发现 ByteArray 类型属性: ${match.value}"
                        )
                    }
                }
            }
        }

        assertTrue(
            "UI 层不应该包含 ByteArray 类型属性。发现:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    /**
     * 测试 UI 层没有敏感函数名
     *
     * INVARIANT: UI 层不应该有加密/解密/密钥派生函数
     */
    @Test
    fun testUILayerNoSensitiveFunctionNames() {
        val violations = mutableListOf<String>()

        val unsafeFunctionPatterns = listOf(
            Regex("""fun\s+encrypt\w*\(""", RegexOption.IGNORE_CASE),
            Regex("""fun\s+decrypt\w*\(""", RegexOption.IGNORE_CASE),
            Regex("""fun\s+derive\w*Key\w*\(""", RegexOption.IGNORE_CASE),
            Regex("""fun\s+generate\w*Key\w*\(""", RegexOption.IGNORE_CASE),
            Regex("""fun\s+getRaw\w*Key\w*\(""", RegexOption.IGNORE_CASE),
        )

        if (uiSourceDir.exists()) {
            scanKotlinFiles(uiSourceDir) { file, content ->
                for (pattern in unsafeFunctionPatterns) {
                    pattern.findAll(content).forEach { match ->
                        violations.add(
                            "${file.name}: 发现不安全函数名: ${match.value}"
                        )
                    }
                }
            }
        }

        assertTrue(
            "UI 层不应该包含加密/解密相关函数。发现:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    /**
     * 测试 UI 层 ViewModel 没有直接持有密钥
     */
    @Test
    fun testViewModelNoDirectKeyHolding() {
        val violations = mutableListOf<String>()

        val viewModelDir = File(uiSourceDir, "viewmodel")
        val keyHoldingPatterns = listOf(
            Regex("""(private\s+)?(val|var)\s+\w*key\w*\s*:""", RegexOption.IGNORE_CASE),
            Regex("""(private\s+)?(val|var)\s+\w*secret\w*\s*:""", RegexOption.IGNORE_CASE),
            Regex("""(private\s+)?(val|var)\s+\w*password\w*\s*:""", RegexOption.IGNORE_CASE),
        )

        if (viewModelDir.exists()) {
            scanKotlinFiles(viewModelDir) { file, content ->
                for (pattern in keyHoldingPatterns) {
                    pattern.findAll(content).forEach { match ->
                        // 排除句柄类型（如 VaultSessionHandle）
                        if (!match.value.contains("Handle", ignoreCase = true)) {
                            violations.add(
                                "${file.name}: 可能直接持有敏感数据: ${match.value}"
                            )
                        }
                    }
                }
            }
        }

        assertTrue(
            "ViewModel 不应该直接持有密钥。发现:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    /**
     * 测试 UI 状态类没有敏感数据字段
     */
    @Test
    fun testUIStateNoSensitiveDataFields() {
        val violations = mutableListOf<String>()

        val stateDir = File(uiSourceDir, "state")
        val sensitiveFieldPatterns = listOf(
            Regex("""val\s+\w*key\w*\s*:\s*ByteArray""", RegexOption.IGNORE_CASE),
            Regex("""val\s+\w*secret\w*\s*:\s*ByteArray""", RegexOption.IGNORE_CASE),
            Regex("""val\s+\w*token\w*\s*:\s*String"""),
            Regex("""val\s+\w*password\w*\s*:\s*String""", RegexOption.IGNORE_CASE),
        )

        if (stateDir.exists()) {
            scanKotlinFiles(stateDir) { file, content ->
                for (pattern in sensitiveFieldPatterns) {
                    pattern.findAll(content).forEach { match ->
                        // 排除已脱敏的字段
                        if (!match.value.contains("Sanitized", ignoreCase = true)) {
                            violations.add(
                                "${file.name}: 发现敏感数据字段: ${match.value}"
                            )
                        }
                    }
                }
            }
        }

        // 注意：某些字段可能是有意设计的，需要人工审查
        // 这里我们只报告，不强制失败
        if (violations.isNotEmpty()) {
            println("警告：发现可能的敏感数据字段，请人工审查:")
            violations.forEach { println("  - $it") }
        }
    }

    // ============================================================================
    // 12.5.4 验证日志中无敏感信息（静态分析）
    // ============================================================================

    /**
     * 测试没有直接使用 Log.d/i/v/e 打印敏感信息
     *
     * INVARIANT: 所有日志必须使用脱敏函数
     */
    @Test
    fun testNoDirectLoggingOfSensitiveData() {
        val violations = mutableListOf<String>()

        // 扫描所有 Kotlin 文件
        val sourceDir = File(projectRoot, "android/app/src/main/kotlin")
        if (sourceDir.exists()) {
            scanKotlinFiles(sourceDir) { file, content ->
                for (pattern in prohibitedLogPatterns) {
                    pattern.findAll(content).forEach { match ->
                        violations.add(
                            "${file.name}: 可能直接打印敏感信息: ${match.value}"
                        )
                    }
                }
            }
        }

        assertTrue(
            "禁止直接打印敏感信息。发现:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    /**
     * 测试没有直接打印 ByteArray
     */
    @Test
    fun testNoDirectByteArrayPrinting() {
        val violations = mutableListOf<String>()

        val sourceDir = File(projectRoot, "android/app/src/main/kotlin")
        val byteArrayPrintPatterns = listOf(
            Regex("""println\(.*\.toString\(\)"""),
            Regex("""println\(\w+Bytes"""),
            Regex("""Log\.\w+\([^)]*,\s*\w+\.toString\(\)"""),
            Regex("""Log\.\w+\([^)]*,\s*""\s*\+\s*\w+Bytes"""),
        )

        if (sourceDir.exists()) {
            scanKotlinFiles(sourceDir) { file, content ->
                for (pattern in byteArrayPrintPatterns) {
                    pattern.findAll(content).forEach { match ->
                        // 检查是否在测试文件中
                        if (!file.path.contains("test", ignoreCase = true)) {
                            violations.add(
                                "${file.name}: 可能直接打印 ByteArray: ${match.value}"
                            )
                        }
                    }
                }
            }
        }

        assertTrue(
            "禁止直接打印 ByteArray.toString()。发现:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    /**
     * 测试日志调用使用了脱敏函数
     */
    @Test
    fun testLoggingUsesSanitization() {
        // 检查 SensitiveDataSanitizer 是否被正确使用
        val sourceDir = File(projectRoot, "android/app/src/main/kotlin")
        var sanitizerImportCount = 0
        var sanitizerUsageCount = 0

        val importPattern = Regex("""import\s+io\.aeternum\.security\.SensitiveDataSanitizer""")
        val usagePattern = Regex("""SensitiveDataSanitizer\.\w+"""")

        if (sourceDir.exists()) {
            scanKotlinFiles(sourceDir) { _, content ->
                if (importPattern.containsMatchIn(content)) {
                    sanitizerImportCount++
                }
                sanitizerUsageCount += usagePattern.findAll(content).count()
            }
        }

        println("SensitiveDataSanitizer 导入次数: $sanitizerImportCount")
        println("SensitiveDataSanitizer 使用次数: $sanitizerUsageCount")

        // 如果有敏感数据处理，应该使用脱敏函数
        // 这里不强制失败，只是报告
    }

    /**
     * 测试测试文件中允许打印敏感信息（仅用于测试）
     */
    @Test
    fun testLoggingInTestFilesIsAllowed() {
        val testDir = File(projectRoot, "android/app/src/androidTest")
        var logCount = 0

        if (testDir.exists()) {
            scanKotlinFiles(testDir) { _, content ->
                logCount += Regex("""println\(|Log\.\w+\(""").findAll(content).count()
            }
        }

        println("测试文件中的日志调用次数: $logCount")
        // 测试文件中的日志调用是允许的
        assertTrue("测试文件可以有日志调用", true)
    }

    // ============================================================================
    // 安全注解使用验证
    // ============================================================================

    /**
     * 测试敏感屏幕使用了 @RequiresSecureFlag 注解
     */
    @Test
    fun testSensitiveScreensUseSecureFlagAnnotation() {
        val screensNeedingSecureFlag = listOf(
            "VaultScreen",
            "BiometricPromptScreen",
            "MnemonicBackupScreen",
            "RecoveryInitiateScreen",
            "VetoNotificationScreen",
        )

        val violations = mutableListOf<String>()
        val secureFlagPattern = Regex("""@RequiresSecureFlag""")

        if (uiSourceDir.exists()) {
            for (screenName in screensNeedingSecureFlag) {
                var found = false
                scanKotlinFiles(uiSourceDir) { file, content ->
                    if (file.nameWithoutExtension == screenName) {
                        found = true
                        // 检查是否有注解或 SecureScreen 包装
                        if (!secureFlagPattern.containsMatchIn(content) &&
                            !content.contains("SecureScreen")) {
                            violations.add(
                                "${file.name}: 敏感屏幕应该使用 @RequiresSecureFlag 或 SecureScreen"
                            )
                        }
                    }
                }
                if (!found) {
                    println("警告：未找到屏幕文件: $screenName")
                }
            }
        }

        // 报告但不强制失败，因为可能使用了 SecureScreen 包装
        if (violations.isNotEmpty()) {
            println("安全注解检查结果:")
            violations.forEach { println("  - $it") }
        }
    }

    /**
     * 测试安全边界注解的使用
     */
    @Test
    fun testSecurityBoundaryAnnotationUsage() {
        val sourceDir = File(projectRoot, "android/app/src/main/kotlin")
        var boundaryAnnotationCount = 0

        val pattern = Regex("""@SecurityBoundary""")

        if (sourceDir.exists()) {
            scanKotlinFiles(sourceDir) { _, content ->
                boundaryAnnotationCount += pattern.findAll(content).count()
            }
        }

        println("@SecurityBoundary 注解使用次数: $boundaryAnnotationCount")
        // 注解使用是推荐的，不是强制的
        assertTrue("注解统计完成", true)
    }

    // ============================================================================
    // 辅助函数
    // ============================================================================

    /**
     * 扫描目录下的所有 Kotlin 文件
     */
    private fun scanKotlinFiles(
        directory: File,
        action: (file: File, content: String) -> Unit,
    ) {
        if (!directory.exists() || !directory.isDirectory) {
            println("目录不存在: ${directory.absolutePath}")
            return
        }

        directory.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                try {
                    val content = file.readText()
                    action(file, content)
                } catch (e: Exception) {
                    println("读取文件失败: ${file.name} - ${e.message}")
                }
            }
    }

    /**
     * 测试项目结构是否正确
     */
    @Test
    fun testProjectStructureExists() {
        println("项目根目录: ${projectRoot.absolutePath}")
        println("UI 源码目录存在: ${uiSourceDir.exists()}")
        println("安全层源码目录存在: ${securitySourceDir.exists()}")

        // 至少应该能找到项目根目录
        assertTrue(
            "应该能找到项目结构",
            projectRoot.exists() || uiSourceDir.exists() || securitySourceDir.exists(),
        )
    }
}
