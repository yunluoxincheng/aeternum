package io.aeternum.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.aeternum.ui.components.SecureScreen
import io.aeternum.ui.state.AeternumUiState
import io.aeternum.ui.state.ActiveSubState
import io.aeternum.ui.state.UiError
import io.aeternum.ui.viewmodel.AeternumViewModel

/**
 * 生物识别认证屏幕
 *
 * 显示生物识别提示对话框，用户通过指纹或面部识别解锁 Vault
 *
 * 安全特性：
 * - 使用系统 BiometricPrompt API（Class 3）
 * - 自动检测设备生物识别能力
 * - 处理成功/失败/取消事件
 * - 成功后自动导航到 Vault 屏幕
 *
 * INVARIANT: 生物识别认证必须通过 AndroidSecurityManager 执行，不存储生物特征数据
 *
 * @param viewModel Aeternum ViewModel
 * @param onAuthSuccess 认证成功回调
 * @param onAuthCancel 认证取消回调
 * @param onAuthFailed 认证失败回调
 */
@Composable
fun BiometricPromptScreen(
    viewModel: AeternumViewModel = viewModel(),
    onAuthSuccess: () -> Unit,
    onAuthCancel: () -> Unit,
    onAuthFailed: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // 监听认证状态变化
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AeternumUiState.Active -> {
                when (state.subState) {
                    is ActiveSubState.Decrypting -> {
                        // 认证成功，已进入 Decrypting 状态
                        onAuthSuccess()
                    }
                    else -> {
                        // 仍在 Idle 状态，显示生物识别提示
                        showPrompt = true
                    }
                }
            }
            is AeternumUiState.Error -> {
                val errorMessage = when (state.error) {
                    is UiError.EpochError -> (state.error as UiError.EpochError).message
                    is UiError.DataError -> (state.error as UiError.DataError).message
                    is UiError.AuthError -> (state.error as UiError.AuthError).message
                    is UiError.VetoError -> (state.error as UiError.VetoError).message
                    is UiError.StateError -> (state.error as UiError.StateError).message
                    is UiError.StorageError -> (state.error as UiError.StorageError).message
                    is UiError.NetworkError -> (state.error as UiError.NetworkError).message
                    is UiError.UnknownError -> (state.error as UiError.UnknownError).message
                }
                onAuthFailed(errorMessage)
            }
            else -> {
                // 其他状态，显示提示
                showPrompt = true
            }
        }
    }

    // 自动触发生物识别
    LaunchedEffect(showPrompt) {
        if (showPrompt && activity != null) {
            viewModel.requestBiometricUnlock(activity)
        }
    }

    // UI
    // INVARIANT: 生物识别屏幕必须启用 FLAG_SECURE，防止截屏
    SecureScreen {
        BiometricPromptContent(
            isProcessing = showPrompt,
            onCancel = {
                showPrompt = false
                onAuthCancel()
            }
        )
    }
}

/**
 * 生物识别提示内容
 *
 * 显示指纹图标、提示文本和加载动画
 *
 * @param isProcessing 是否正在处理
 * @param onCancel 取消回调
 */
@Composable
private fun BiometricPromptContent(
    isProcessing: Boolean,
    onCancel: () -> Unit,
) {
    var pulseScale by remember { mutableStateOf(1f) }
    val scale by animateFloatAsState(
        targetValue = pulseScale,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f,
        ),
        label = "pulse"
    )

    // 脉冲动画效果
    androidx.compose.animation.AnimatedVisibility(
        visible = isProcessing,
        enter = scaleIn(
            animationSpec = tween(durationMillis = 300),
        ),
        exit = scaleOut(
            animationSpec = tween(durationMillis = 200),
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 脉冲动画的指纹图标
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    // 外圈脉冲效果
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isProcessing,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(200))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(scale)
                                .alpha(1f - (scale - 1f) * 2f)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                        )
                    }

                    // 指纹图标
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "生物识别",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    // 加载指示器
                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(100.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // 标题文本
                Text(
                    text = "验证身份",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 提示文本
                Text(
                    text = "请使用指纹或面部识别解锁 Aeternum",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(48.dp))

                // 取消按钮
                androidx.compose.animation.AnimatedVisibility(
                    visible = isProcessing,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = onCancel,
                    ) {
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    // 触发脉冲动画
    androidx.compose.animation.AnimatedVisibility(
        visible = isProcessing,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        LaunchedEffect(Unit) {
            // 脉冲动画：1.0 -> 1.2 -> 1.0 循环
            while (isProcessing) {
                pulseScale = 1.2f
                kotlinx.coroutines.delay(1000)
                pulseScale = 1.0f
                kotlinx.coroutines.delay(1000)
            }
        }
    }
}

/**
 * 生物识别结果枚举
 *
 * 表示生物识别认证的结果
 */
sealed class BiometricResult {
    /**
     * 认证成功
     */
    data object Success : BiometricResult()

    /**
     * 认证失败
     *
     * @property reason 失败原因
     */
    data class Failed(val reason: String) : BiometricResult()

    /**
     * 用户取消
     */
    data object Cancelled : BiometricResult()

    /**
     * 不支持
     *
     * @property reason 不支持的原因
     */
    data class NotAvailable(val reason: String) : BiometricResult()
}

/**
 * 生物识别能力检测
 *
 * 检测设备的生物识别能力
 */
data class BiometricCapability(
    val hasHardware: Boolean = false,
    val hasFingerprint: Boolean = false,
    val hasFace: Boolean = false,
    val isClass3: Boolean = false,
)

/**
 * 检测生物识别能力
 *
 * @param context Android Context
 * @return 生物识别能力
 */
fun detectBiometricCapability(context: android.content.Context): BiometricCapability {
    val biometricManager = androidx.biometric.BiometricManager.from(context)

    val canAuthenticate = biometricManager.canAuthenticate(
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
    )

    val hasHardware = when (canAuthenticate) {
        androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS,
        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> true
        else -> false
    }

    val isClass3 = canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

    // 检测指纹
    val hasFingerprint = context.packageManager.hasSystemFeature(
        "android.hardware.fingerprint"
    )

    // 检测面部识别
    val hasFace = context.packageManager.hasSystemFeature(
        "android.hardware.face"
    )

    return BiometricCapability(
        hasHardware = hasHardware,
        hasFingerprint = hasFingerprint,
        hasFace = hasFace,
        isClass3 = isClass3,
    )
}

/**
 * 获取生物识别类型文本
 *
 * @param capability 生物识别能力
 * @return 类型描述文本
 */
fun getBiometricTypeText(capability: BiometricCapability): String {
    return when {
        capability.hasFace && capability.hasFingerprint -> "指纹或面部识别"
        capability.hasFace -> "面部识别"
        capability.hasFingerprint -> "指纹识别"
        else -> "生物识别"
    }
}
