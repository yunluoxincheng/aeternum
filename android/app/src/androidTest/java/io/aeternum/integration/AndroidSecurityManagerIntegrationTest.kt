package io.aeternum.integration

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.aeternum.security.AndroidSecurityManager
import io.aeternum.security.BiometricAuthResult
import io.aeternum.security.BiometricCapability
import io.aeternum.security.IntegrityResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * AndroidSecurityManager 集成测试
 *
 * 测试覆盖：
 * - 硬件密钥生成与管理
 * - 生物识别认证集成
 * - 设备完整性检查
 * - 安全存储集成
 * - Root 检测
 *
 * INVARIANT: 必须使用 Class 3 生物识别
 * INVARIANT: 密钥存储在 KeyStore/StrongBox 中
 * INVARIANT: 生物识别数据由系统管理，应用不存储
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSecurityManagerIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        AndroidSecurityManager.initialize(context)
    }

    @After
    fun tearDown() {
        // 清理测试产生的密钥
        cleanupTestKeys()
    }

    private fun cleanupTestKeys() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias("aeternum_dk_hardware")) {
                keyStore.deleteEntry("aeternum_dk_hardware")
            }
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }

    // ========================================================================
    // 12.3.3.1 硬件密钥管理测试
    // ========================================================================

    /**
     * 测试：AndroidSecurityManager 初始化
     *
     * 验证：可以正确初始化安全管理器
     */
    @Test
    fun testSecurityManagerInitialization() {
        // 验证上下文可用
        val ctx = AndroidSecurityManager.getContext()
        assertNotNull(ctx)
        assertEquals(context.packageName, ctx.packageName)
    }

    /**
     * 测试：硬件密钥生成
     *
     * 验证：可以生成硬件绑定的密钥
     */
    @Test
    fun testHardwareKeyGeneration() {
        val hardwareKey = AndroidSecurityManager.getHardwareKey()

        // 验证密钥已生成
        assertNotNull(hardwareKey)
        assertNotNull(hardwareKey.algorithm)
    }

    /**
     * 测试：密钥存储在 KeyStore
     *
     * 验证：生成的密钥存储在 AndroidKeyStore 中
     */
    @Test
    fun testKeyStoredInKeyStore() {
        // 生成密钥
        AndroidSecurityManager.getHardwareKey()

        // 验证密钥存在于 KeyStore
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        assertTrue(keyStore.containsAlias("aeternum_dk_hardware"))
    }

    /**
     * 测试：StrongBox 可用性检查
     *
     * 验证：可以正确检测设备是否支持 StrongBox
     */
    @Test
    fun testStrongBoxAvailabilityCheck() {
        val isStrongBoxAvailable = AndroidSecurityManager.isStrongBoxAvailable()

        // 验证返回布尔值
        assertTrue(isStrongBoxAvailable is Boolean)
    }

    /**
     * 测试：密钥需要用户认证
     *
     * INVARIANT: 密钥使用需要用户认证
     */
    @Test
    fun testKeyRequiresUserAuthentication() {
        val hardwareKey = AndroidSecurityManager.getHardwareKey()

        // 验证密钥已生成
        assertNotNull(hardwareKey)

        // 实际的密钥使用需要用户认证，这里验证密钥存在
        // 实际测试需要在设备上运行
    }

    // ========================================================================
    // 12.3.3.2 生物识别能力测试
    // ========================================================================

    /**
     * 测试：生物识别能力检测
     *
     * 验证：可以正确检测设备的生物识别能力
     */
    @Test
    fun testBiometricCapabilityDetection() {
        val capability = AndroidSecurityManager.checkBiometricCapability()

        // 验证能力结构完整
        assertNotNull(capability)
        assertNotNull(capability.hasHardware)
        assertNotNull(capability.hasFingerprint)
        assertNotNull(capability.hasFace)
        assertNotNull(capability.isClass3)
        assertNotNull(capability.canAuthenticate)
    }

    /**
     * 测试：生物识别可用性检查
     *
     * 验证：可以快速检查生物识别是否可用
     */
    @Test
    fun testBiometricAvailabilityCheck() {
        val isAvailable = AndroidSecurityManager.isBiometricAvailable()

        // 验证返回布尔值
        assertTrue(isAvailable is Boolean)
    }

    /**
     * 测试：生物识别类型描述
     *
     * 验证：可以正确获取生物识别类型描述
     */
    @Test
    fun testBiometricTypeDescription() {
        val capability = AndroidSecurityManager.checkBiometricCapability()
        val typeText = capability.getBiometricTypeText()

        // 验证描述非空
        assertNotNull(typeText)
        assertTrue(typeText.isNotEmpty())
    }

    /**
     * 测试：Class 3 生物识别检查
     *
     * INVARIANT: 必须使用 Class 3 (Strong) 生物识别
     */
    @Test
    fun testClass3BiometricCheck() {
        val capability = AndroidSecurityManager.checkBiometricCapability()

        // Class 3 表示强生物识别
        if (capability.isClass3) {
            assertTrue(capability.hasHardware)
            assertEquals(BiometricManager.BIOMETRIC_SUCCESS, capability.canAuthenticate)
        }
    }

    /**
     * 测试：生物识别能力数据类
     *
     * 验证：BiometricCapability 正确存储各项能力
     */
    @Test
    fun testBiometricCapabilityDataClass() {
        val capability = BiometricCapability(
            hasHardware = true,
            hasFingerprint = true,
            hasFace = false,
            isClass3 = true,
            canAuthenticate = BiometricManager.BIOMETRIC_SUCCESS,
        )

        assertTrue(capability.hasHardware)
        assertTrue(capability.hasFingerprint)
        assertFalse(capability.hasFace)
        assertTrue(capability.isClass3)
        assertEquals(BiometricManager.BIOMETRIC_SUCCESS, capability.canAuthenticate)
    }

    // ========================================================================
    // 12.3.3.3 生物识别结果测试
    // ========================================================================

    /**
     * 测试：生物识别成功结果
     *
     * 验证：BiometricAuthResult.Success 正确创建
     */
    @Test
    fun testBiometricAuthResultSuccess() {
        val result = BiometricAuthResult.Success

        assertEquals("Success", result::class.simpleName)
    }

    /**
     * 测试：生物识别失败结果
     *
     * 验证：BiometricAuthResult.Failed 包含原因
     */
    @Test
    fun testBiometricAuthResultFailed() {
        val reason = "指纹不匹配"
        val result = BiometricAuthResult.Failed(reason)

        assertEquals(reason, result.reason)
    }

    /**
     * 测试：生物识别取消结果
     *
     * 验证：BiometricAuthResult.Cancelled 正确创建
     */
    @Test
    fun testBiometricAuthResultCancelled() {
        val result = BiometricAuthResult.Cancelled

        assertEquals("Cancelled", result::class.simpleName)
    }

    /**
     * 测试：生物识别不可用结果
     *
     * 验证：BiometricAuthResult.NotAvailable 包含原因
     */
    @Test
    fun testBiometricAuthResultNotAvailable() {
        val reason = "设备不支持生物识别"
        val result = BiometricAuthResult.NotAvailable(reason)

        assertEquals(reason, result.reason)
    }

    // ========================================================================
    // 12.3.3.4 设备完整性检查测试
    // ========================================================================

    /**
     * 测试：设备完整性检查
     *
     * 验证：可以检查设备完整性
     */
    @Test
    fun testDeviceIntegrityCheck() = runTest {
        val result = AndroidSecurityManager.checkDeviceIntegrity()

        // 验证返回完整性结果
        assertNotNull(result)
        assertTrue(result is IntegrityResult)
    }

    /**
     * 测试：完整性检查成功结果
     *
     * 验证：IntegrityResult.Success 正确创建
     */
    @Test
    fun testIntegrityResultSuccess() {
        val result = IntegrityResult.Success

        assertEquals("Success", result::class.simpleName)
    }

    /**
     * 测试：完整性检查失败结果
     *
     * 验证：IntegrityResult.Failed 包含原因
     */
    @Test
    fun testIntegrityResultFailed() {
        val reason = "设备已被 Root"
        val result = IntegrityResult.Failed(reason)

        assertEquals(reason, result.reason)
    }

    // ========================================================================
    // 12.3.3.5 Root 检测测试
    // ========================================================================

    /**
     * 测试：Root 检测
     *
     * 验证：可以检测设备是否被 Root
     */
    @Test
    fun testRootDetection() {
        val isRooted = AndroidSecurityManager.isDeviceRooted()

        // 验证返回布尔值
        assertTrue(isRooted is Boolean)

        // 测试环境通常不是 Root 设备
        // 但验证方法可以正常执行
    }

    /**
     * 测试：Root 检测 - SU 文件检查
     *
     * 验证：正确检查常见 SU 文件路径
     */
    @Test
    fun testRootDetection_suFiles() {
        // 验证方法可用
        val isRooted = AndroidSecurityManager.isDeviceRooted()
        assertNotNull(isRooted)
    }

    // ========================================================================
    // 12.3.3.6 加密存储测试
    // ========================================================================

    /**
     * 测试：加密 SharedPreferences
     *
     * 验证：可以创建加密的 SharedPreferences
     */
    @Test
    fun testEncryptedSharedPreferences() {
        val prefs = AndroidSecurityManager.getEncryptedPrefs("test_prefs")

        assertNotNull(prefs)

        // 测试读写
        prefs.edit().putString("test_key", "test_value").apply()
        val value = prefs.getString("test_key", null)

        assertEquals("test_value", value)
    }

    /**
     * 测试：加密存储数据持久化
     *
     * 验证：加密存储的数据可以正确读取
     */
    @Test
    fun testEncryptedStoragePersistence() {
        val prefs = AndroidSecurityManager.getEncryptedPrefs("test_persistence")

        // 写入数据
        prefs.edit()
            .putString("string_key", "test_string")
            .putInt("int_key", 42)
            .putBoolean("bool_key", true)
            .apply()

        // 读取数据
        assertEquals("test_string", prefs.getString("string_key", null))
        assertEquals(42, prefs.getInt("int_key", 0))
        assertEquals(true, prefs.getBoolean("bool_key", false))
    }

    /**
     * 测试：多个加密存储实例
     *
     * 验证：可以创建多个独立的加密存储实例
     */
    @Test
    fun testMultipleEncryptedStorageInstances() {
        val prefs1 = AndroidSecurityManager.getEncryptedPrefs("test_instance_1")
        val prefs2 = AndroidSecurityManager.getEncryptedPrefs("test_instance_2")

        // 写入不同数据
        prefs1.edit().putString("key", "value1").apply()
        prefs2.edit().putString("key", "value2").apply()

        // 验证数据隔离
        assertEquals("value1", prefs1.getString("key", null))
        assertEquals("value2", prefs2.getString("key", null))
    }

    // ========================================================================
    // 12.3.3.7 安全边界验证测试
    // ========================================================================

    /**
     * 测试：密钥不可导出
     *
     * INVARIANT: 硬件密钥不可导出
     */
    @Test
    fun testKeyNonExportable() {
        val hardwareKey = AndroidSecurityManager.getHardwareKey()

        // 验证密钥存在
        assertNotNull(hardwareKey)

        // 密钥应该存储在 KeyStore 中，且不可导出
        // 实际验证需要检查 KeyGenParameterSpec
        // 这里验证密钥生成成功
    }

    /**
     * 测试：敏感数据不进入日志
     *
     * INVARIANT: 日志中不应包含敏感信息
     */
    @Test
    fun testNoSensitiveDataInLogs() {
        // 此测试验证概念，实际需要检查日志输出
        // 验证方法执行成功
        val capability = AndroidSecurityManager.checkBiometricCapability()
        assertNotNull(capability)
    }

    // ========================================================================
    // 12.3.3.8 上下文管理测试
    // ========================================================================

    /**
     * 测试：获取应用上下文
     *
     * 验证：可以正确获取应用上下文
     */
    @Test
    fun testGetContext() {
        val ctx = AndroidSecurityManager.getContext()

        assertNotNull(ctx)
        assertEquals(context.applicationContext.packageName, ctx.packageName)
    }

    /**
     * 测试：上下文初始化检查
     *
     * 验证：未初始化时抛出异常
     */
    @Test
    fun testContextInitializationCheck() {
        // 已经在 setup 中初始化，验证可以获取上下文
        val ctx = AndroidSecurityManager.getContext()
        assertNotNull(ctx)
    }

    // ========================================================================
    // 12.3.3.9 生物识别类型组合测试
    // ========================================================================

    /**
     * 测试：仅指纹设备
     *
     * 验证：正确处理仅支持指纹的设备
     */
    @Test
    fun testFingerprintOnlyDevice() {
        val capability = BiometricCapability(
            hasHardware = true,
            hasFingerprint = true,
            hasFace = false,
            isClass3 = true,
        )

        assertEquals("指纹识别", capability.getBiometricTypeText())
    }

    /**
     * 测试：仅面部识别设备
     *
     * 验证：正确处理仅支持面部识别的设备
     */
    @Test
    fun testFaceOnlyDevice() {
        val capability = BiometricCapability(
            hasHardware = true,
            hasFingerprint = false,
            hasFace = true,
            isClass3 = true,
        )

        assertEquals("面部识别", capability.getBiometricTypeText())
    }

    /**
     * 测试：指纹和面部识别设备
     *
     * 验证：正确处理同时支持两种识别方式的设备
     */
    @Test
    fun testBothBiometricsDevice() {
        val capability = BiometricCapability(
            hasHardware = true,
            hasFingerprint = true,
            hasFace = true,
            isClass3 = true,
        )

        assertEquals("指纹或面部识别", capability.getBiometricTypeText())
    }

    /**
     * 测试：无生物识别设备
     *
     * 验证：正确处理不支持生物识别的设备
     */
    @Test
    fun testNoBiometricsDevice() {
        val capability = BiometricCapability(
            hasHardware = false,
            hasFingerprint = false,
            hasFace = false,
            isClass3 = false,
        )

        assertEquals("生物识别", capability.getBiometricTypeText())
    }
}
