package io.aeternum.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android 硬件安全模块管理器
 *
 * 负责：
 * - StrongBox/KeyStore 集成
 * - 生物识别验证
 * - DK_hardware 密钥管理
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
}
