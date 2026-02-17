package io.aeternum.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.concurrent.Executor
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android 硬件安全模块管理器
 *
 * 负责：
 * - StrongBox/KeyStore 集成
 * - 生物识别验证
 * - DK_hardware 密钥管理
 * - Play Integrity 验证
 */
object AndroidSecurityManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val HARDWARE_KEY_ALIAS = "aeternum_dk_hardware"

    private lateinit var context: Context
    private lateinit var masterKey: MasterKey

    /**
     * 初始化安全管理器
     */
    fun initialize(ctx: Context) {
        context = ctx.applicationContext
        masterKey = createMasterKey()
    }

    /**
     * 获取上下文（用于非初始化场景）
     */
    fun getContext(): Context {
        if (!::context.isInitialized) {
            throw IllegalStateException("AndroidSecurityManager 未初始化")
        }
        return context
    }

    /**
     * 创建或获取主密钥 (存储在 KeyStore/StrongBox)
     * 这是 DK_hardware - 硬件绑定密钥
     */
    fun getHardwareKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val key = keyStore.getEntry(HARDWARE_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry

        return key?.secretKey ?: createHardwareKey()
    }

    /**
     * 创建新的硬件绑定密钥
     */
    private fun createHardwareKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            HARDWARE_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(30)
            .setRandomizedEncryptionRequired(true)
            .apply {
                // 尝试使用 StrongBox (如果设备支持)
                if (isStrongBoxAvailable()) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * 创建主密钥用于 EncryptedSharedPreferences
     */
    private fun createMasterKey(): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * 获取加密的 SharedPreferences
     */
    fun getEncryptedPrefs(name: String): EncryptedSharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    /**
     * 检查设备是否支持 StrongBox
     */
    fun isStrongBoxAvailable(): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.strongbox")
    }

    // ========================================================================
    // 生物识别认证
    // ========================================================================

    /**
     * 执行生物识别认证
     *
     * 使用系统 BiometricPrompt API 进行 Class 3 生物识别认证
     *
     * INVARIANT: 仅使用 Class 3（BIOMETRIC_STRONG）生物识别
     * INVARIANT: 生物识别数据由系统管理，应用不存储任何生物特征数据
     *
     * @param activity FragmentActivity 用于显示生物识别对话框
     * @return 认证结果（Flow）
     */
    suspend fun authenticate(activity: FragmentActivity): BiometricAuthResult = withContext(Dispatchers.Main) {
        // 首先检查生物识别能力
        val capability = checkBiometricCapability()
        if (!capability.isClass3) {
            return@withContext BiometricAuthResult.NotAvailable(
                reason = when {
                    !capability.hasHardware -> "设备不支持生物识别硬件"
                    capability.canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "未注册生物识别"
                    capability.canAuthenticate == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "生物识别硬件不可用"
                    else -> "生物识别不可用"
                }
            )
        }

        // 创建执行器
        val executor = ContextCompat.getMainExecutor(context)

        // 使用 callbackFlow 创建挂起函数
        callbackFlow {
            // 创建 BiometricPrompt
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        trySend(BiometricAuthResult.Success)
                        close()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        // 认证失败但不关闭，允许重试
                        // 不发送事件，让用户重试
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                BiometricAuthResult.Cancelled
                            }
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                                BiometricAuthResult.Failed("生物识别已锁定，请使用其他方式解锁")
                            }
                            else -> {
                                BiometricAuthResult.Failed(errString.toString())
                            }
                        }
                        trySend(result)
                        close()
                    }
                }
            )

            // 构建提示信息
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("验证身份")
                .setSubtitle("请使用指纹或面部识别解锁 Aeternum")
                .setNegativeButtonText("取消")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            // 显示生物识别对话框
            biometricPrompt.authenticate(promptInfo)

            awaitClose {
                // 清理资源
            }
        }.firstOrNull() ?: BiometricAuthResult.Failed("认证超时")
    }

    /**
     * 检查生物识别能力
     *
     * @return 生物识别能力
     */
    fun checkBiometricCapability(): BiometricCapability {
        val biometricManager = BiometricManager.from(context)

        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )

        val hasHardware = when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true
            else -> false
        }

        val isClass3 = canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS

        // 检测指纹
        val hasFingerprint = context.packageManager.hasSystemFeature(
            "android.hardware.fingerprint"
        )

        // 检测面部识别
        val hasFace = context.packageManager.hasSystemFeature(
            "android.hardware.biometrics"
        ) || context.packageManager.hasSystemFeature(
            "android.hardware.face"
        )

        return BiometricCapability(
            hasHardware = hasHardware,
            hasFingerprint = hasFingerprint,
            hasFace = hasFace,
            isClass3 = isClass3,
            canAuthenticate = canAuthenticate,
        )
    }

    /**
     * 检查生物识别是否可用（简化版本）
     *
     * @return 是否可用
     */
    fun isBiometricAvailable(): Boolean {
        val capability = checkBiometricCapability()
        return capability.isClass3
    }

    // ========================================================================
    // 设备完整性检查
    // ========================================================================

    /**
     * 检查设备完整性
     *
     * @return 完整性检查结果
     */
    suspend fun checkDeviceIntegrity(): IntegrityResult = withContext(Dispatchers.IO) {
        // TODO: 实现 Play Integrity API 集成
        // 目前返回占位符成功结果
        IntegrityResult.Success
    }

    /**
     * 检查设备是否已 root
     *
     * @return 是否已 root
     */
    fun isDeviceRooted(): Boolean {
        // 基础 root 检测
        return checkSuExists() || checkDangerousProps() || checkRootApps()
    }

    private fun checkSuExists(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
        )
        return paths.any { java.io.File(it).exists() }
    }

    private fun checkDangerousProps(): Boolean {
        val tags = android.os.Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun checkRootApps(): Boolean {
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.topjohnwu.magisk",
        )
        return rootApps.any { isPackageInstalled(it) }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 完整性检查结果
 */
sealed class IntegrityResult {
    /** 成功 */
    data object Success : IntegrityResult()

    /** 失败 */
    data class Failed(val reason: String) : IntegrityResult()
}

/**
 * 生物识别认证结果
 *
 * 表示生物识别认证的结果
 */
sealed class BiometricAuthResult {
    /**
     * 认证成功
     */
    data object Success : BiometricAuthResult()

    /**
     * 认证失败
     *
     * @property reason 失败原因
     */
    data class Failed(val reason: String) : BiometricAuthResult()

    /**
     * 用户取消
     */
    data object Cancelled : BiometricAuthResult()

    /**
     * 不支持
     *
     * @property reason 不支持的原因
     */
    data class NotAvailable(val reason: String) : BiometricAuthResult()
}

/**
 * 生物识别能力
 *
 * 表示设备的生物识别能力
 */
data class BiometricCapability(
    /** 是否有生物识别硬件 */
    val hasHardware: Boolean = false,

    /** 是否支持指纹识别 */
    val hasFingerprint: Boolean = false,

    /** 是否支持面部识别 */
    val hasFace: Boolean = false,

    /** 是否支持 Class 3（BIOMETRIC_STRONG）生物识别 */
    val isClass3: Boolean = false,

    /** BiometricManager.canAuthenticate 的结果 */
    val canAuthenticate: Int = 0,
) {
    /**
     * 获取生物识别类型描述
     */
    fun getBiometricTypeText(): String {
        return when {
            hasFace && hasFingerprint -> "指纹或面部识别"
            hasFace -> "面部识别"
            hasFingerprint -> "指纹识别"
            else -> "生物识别"
        }
    }
}
