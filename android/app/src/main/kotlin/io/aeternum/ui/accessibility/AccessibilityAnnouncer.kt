package io.aeternum.ui.accessibility

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

/**
 * 无障碍语音播报服务
 *
 * 提供状态变化的语音提示，配合 TalkBack 使用。
 *
 * ## 设计理念
 * - **智能播报**: 仅在 TalkBack 启用时播报
 * - **优先级控制**: 重要消息优先播报
 * - **去重机制**: 避免重复播报
 *
 * ## 架构约束
 * - INVARIANT: 不播报敏感信息（密钥、助记词等）
 * - 仅播报脱敏后的状态信息
 */
class AccessibilityAnnouncer(private val context: Context) {

    private val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _announcements = MutableSharedFlow<Announcement>(
        extraBufferCapacity = 10,
        replay = 0,
    )
    val announcements = _announcements.asSharedFlow()

    /**
     * 初始化 TTS 引擎
     */
    fun initialize() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                textToSpeech?.language = Locale.CHINESE
            }
        }
    }

    /**
     * 释放 TTS 资源
     */
    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
    }

    /**
     * 检查 TalkBack 是否启用
     */
    fun isTalkBackEnabled(): Boolean {
        return accessibilityManager.isTouchExplorationEnabled
    }

    /**
     * 播报消息
     *
     * @param message 消息内容
     * @param priority 优先级（高优先级会打断当前播报）
     */
    fun announce(message: String, priority: AnnouncementPriority = AnnouncementPriority.NORMAL) {
        if (!isTalkBackEnabled()) return

        val announcement = Announcement(message, priority)
        _announcements.tryEmit(announcement)

        // 通过 AccessibilityManager 播报
        // 注意: ACCESSIBILITY_ANNOUNCEMENT 常量已被废弃，直接使用事件类型
        accessibilityManager.sendAccessibilityEvent(
            android.view.accessibility.AccessibilityEvent.obtain().apply {
                eventType = android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT
                text.add(message)
            },
        )

        // 同时使用 TTS 作为备选
        if (isTtsReady && priority == AnnouncementPriority.HIGH) {
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                null,
            )
        } else if (isTtsReady) {
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_ADD,
                null,
                null,
            )
        }
    }

    /**
     * 播报状态变化
     */
    fun announceStateChange(
        fromState: String,
        toState: String,
        reason: String? = null,
    ) {
        val message = generateStateChangeAnnouncement(fromState, toState, reason)
        announce(message, AnnouncementPriority.HIGH)
    }

    /**
     * 播报操作结果
     */
    fun announceActionResult(
        action: String,
        success: Boolean,
        errorMessage: String? = null,
    ) {
        val message = if (success) {
            "$action 成功"
        } else {
            "$action 失败${errorMessage?.let { "：$it" } ?: ""}"
        }
        announce(message, if (success) AnnouncementPriority.NORMAL else AnnouncementPriority.HIGH)
    }

    /**
     * 播报倒计时提醒
     */
    fun announceCountdownReminder(
        context: String,
        remainingSeconds: Int,
    ) {
        val timeText = formatAccessibleTime(remainingSeconds)
        announce("$context 剩余 $timeText", AnnouncementPriority.LOW)
    }

    /**
     * 播报警告
     */
    fun announceWarning(message: String) {
        announce("警告：$message", AnnouncementPriority.HIGH)
    }

    /**
     * 播报导航变化
     */
    fun announceNavigation(screenName: String) {
        announce("已进入 $screenName", AnnouncementPriority.NORMAL)
    }

    companion object {
        @Volatile
        private var instance: AccessibilityAnnouncer? = null

        fun getInstance(context: Context): AccessibilityAnnouncer {
            return instance ?: synchronized(this) {
                instance ?: AccessibilityAnnouncer(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

/**
 * 播报消息
 */
data class Announcement(
    val message: String,
    val priority: AnnouncementPriority,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * 播报优先级
 */
enum class AnnouncementPriority {
    LOW,    // 低优先级，加入队列
    NORMAL, // 正常优先级
    HIGH,   // 高优先级，打断当前播报
}

// ============================================================================
// Composable 辅助函数
// ============================================================================

/**
 * 获取 AccessibilityAnnouncer 实例
 */
@Composable
fun rememberAccessibilityAnnouncer(): AccessibilityAnnouncer {
    val context = LocalContext.current
    val announcer = remember { AccessibilityAnnouncer.getInstance(context) }

    DisposableEffect(announcer) {
        announcer.initialize()
        onDispose {
            // 不在这里释放，因为是单例
        }
    }

    return announcer
}

/**
 * 状态变化时自动播报
 */
@Composable
fun AnnounceStateChange(
    state: String,
    previousState: String?,
    reason: String? = null,
) {
    val announcer = rememberAccessibilityAnnouncer()

    LaunchedEffect(state, previousState) {
        if (previousState != null && state != previousState) {
            announcer.announceStateChange(previousState, state, reason)
        }
    }
}

/**
 * 操作结果播报
 */
@Composable
fun AnnounceActionResult(
    action: String,
    result: ActionResult?,
) {
    val announcer = rememberAccessibilityAnnouncer()

    LaunchedEffect(result) {
        result?.let {
            announcer.announceActionResult(action, it.success, it.errorMessage)
        }
    }
}

/**
 * 操作结果
 */
data class ActionResult(
    val success: Boolean,
    val errorMessage: String? = null,
)

// ============================================================================
// 状态变化播报文本定义
// ============================================================================

/**
 * 状态变化的中文描述映射
 */
object StateAnnouncements {

    // 设备状态
    const val DEVICE_IDLE = "空闲"
    const val DEVICE_ACTIVE = "活跃"
    const val DEVICE_DECRYPTING = "正在解密"
    const val DEVICE_REKEYING = "正在轮换密钥"
    const val DEVICE_DEGRADED = "安全降级模式"
    const val DEVICE_REVOKED = "已撤销"

    // 操作结果
    const val AUTH_SUCCESS = "身份验证成功"
    const val AUTH_FAILED = "身份验证失败"
    const val UNLOCK_SUCCESS = "保险库已解锁"
    const val LOCK_SUCCESS = "保险库已锁定"
    const val REKEY_STARTED = "密钥轮换已开始"
    const val REKEY_COMPLETED = "密钥轮换已完成"
    const val RECOVERY_STARTED = "恢复流程已启动"
    const val RECOVERY_CANCELLED = "恢复流程已取消"
    const val VETO_SUBMITTED = "已提交否决"
    const val DEVICE_REVOKED_SUCCESS = "设备已撤销"

    // 警告消息
    const val WARNING_INTEGRITY_FAILED = "设备完整性验证失败，功能受限"
    const val WARNING_SESSION_TIMEOUT = "会话超时，已自动锁定"
    const val WARNING_RECOVERY_PENDING = "存在待处理的恢复请求"
    const val WARNING_VETO_WINDOW_CLOSING = "否决窗口即将关闭"

    // 导航
    const val SCREEN_WELCOME = "欢迎页面"
    const val SCREEN_MAIN = "主页面"
    const val SCREEN_DEVICES = "设备管理"
    const val SCREEN_RECOVERY = "恢复流程"
    const val SCREEN_SETTINGS = "设置"
    const val SCREEN_VAULT = "保险库"

    /**
     * 获取状态的中文描述
     */
    fun getStateDescription(state: String): String {
        return when (state.lowercase()) {
            "idle" -> DEVICE_IDLE
            "active" -> DEVICE_ACTIVE
            "decrypting" -> DEVICE_DECRYPTING
            "rekeying" -> DEVICE_REKEYING
            "degraded" -> DEVICE_DEGRADED
            "revoked" -> DEVICE_REVOKED
            else -> state
        }
    }
}
