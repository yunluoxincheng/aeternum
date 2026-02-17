package io.aeternum.ui.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 设备性能等级枚举
 *
 * 用于自适应动画配置，根据设备性能调整动画复杂度。
 *
 * ## 性能等级定义
 * - **LOW**: 旧设备，关闭复杂动画
 * - **MEDIUM**: 主流设备，简化动画
 * - **HIGH**: 高端设备，完整动画
 * - **ULTRA**: 旗舰设备，所有特效
 *
 * ## 检测依据
 * - CPU 核心数
 * - 内存大小
 * - 是否为低内存设备
 * - API 级别
 */
enum class DevicePerformanceTier {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA,
}

/**
 * 动画配置
 *
 * 根据设备性能等级生成的动画配置参数。
 *
 * @property enableComplexAnimations 是否启用复杂动画
 * @property targetFrameRate 目标帧率
 * @property easing 缓动曲线
 * @property durationScale 时长缩放因子
 */
data class AnimationConfig(
    val enableComplexAnimations: Boolean,
    val targetFrameRate: Int,
    val easing: Easing,
    val durationScale: Float = 1.0f,
) {
    companion object {
        /**
         * 根据性能等级创建动画配置
         */
        fun forTier(tier: DevicePerformanceTier): AnimationConfig = when (tier) {
            DevicePerformanceTier.LOW -> AnimationConfig(
                enableComplexAnimations = false,
                targetFrameRate = 30,
                easing = LinearEasing,
                durationScale = 0.5f,
            )

            DevicePerformanceTier.MEDIUM -> AnimationConfig(
                enableComplexAnimations = true,
                targetFrameRate = 30,
                easing = FastOutSlowInEasing,
                durationScale = 0.75f,
            )

            DevicePerformanceTier.HIGH -> AnimationConfig(
                enableComplexAnimations = true,
                targetFrameRate = 60,
                easing = FastOutSlowInEasing,
                durationScale = 1.0f,
            )

            DevicePerformanceTier.ULTRA -> AnimationConfig(
                enableComplexAnimations = true,
                targetFrameRate = 120,
                easing = FastOutSlowInEasing,
                durationScale = 1.0f,
            )
        }
    }

    /**
     * 创建 TweenSpec
     */
    fun <T> tweenSpec(durationMillis: Int): AnimationSpec<T> {
        return tween(
            durationMillis = (durationMillis * durationScale).toInt(),
            easing = easing,
        )
    }

    /**
     * 创建 SpringSpec
     */
    fun <T> springSpec(): AnimationSpec<T> {
        return spring(
            dampingRatio = if (enableComplexAnimations) 0.8f else 1.0f,
            stiffness = if (enableComplexAnimations) 400f else 1000f,
        )
    }
}

/**
 * 性能检测器
 *
 * 用于检测设备性能等级，提供自适应动画配置。
 */
object PerformanceDetector {

    /**
     * 检测设备性能等级
     *
     * @param context Android Context
     * @return 设备性能等级
     */
    fun detectTier(context: Context): DevicePerformanceTier {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRam = activityManager.isLowRamDevice
        val cores = Runtime.getRuntime().availableProcessors()
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemory = memoryInfo.totalMem

        return when {
            isLowRam || cores < 4 -> DevicePerformanceTier.LOW
            cores >= 8 && totalMemory > 8_000_000_000L -> DevicePerformanceTier.HIGH
            cores >= 6 && totalMemory > 6_000_000_000L -> DevicePerformanceTier.MEDIUM
            cores >= 4 && totalMemory > 4_000_000_000L -> DevicePerformanceTier.MEDIUM
            else -> DevicePerformanceTier.LOW
        }
    }

    /**
     * 获取设备性能信息字符串
     */
    fun getPerformanceInfo(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val cores = Runtime.getRuntime().availableProcessors()
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
        val availableMemoryMB = memoryInfo.availMem / (1024 * 1024)
        val isLowRam = activityManager.isLowRamDevice
        val tier = detectTier(context)

        return buildString {
            appendLine("Device Performance Info:")
            appendLine("  Tier: $tier")
            appendLine("  CPU Cores: $cores")
            appendLine("  Total Memory: ${totalMemoryMB}MB")
            appendLine("  Available Memory: ${availableMemoryMB}MB")
            appendLine("  Is Low RAM: $isLowRam")
            appendLine("  API Level: ${Build.VERSION.SDK_INT}")
        }
    }
}

/**
 * 动画性能监控器
 *
 * 用于监控动画帧率，自动降级动画配置。
 *
 * ## 使用场景
 * - 检测动画卡顿
 * - 自动降低动画复杂度
 * - 生成性能报告
 */
class AnimationPerformanceMonitor {
    private val frameTimes = ArrayDeque<Long>()
    private var consecutiveSlowFrames = 0

    /**
     * 记录帧时间
     */
    fun recordFrame(frameTimeNanos: Long) {
        if (frameTimes.size >= 120) {
            frameTimes.removeFirst()
        }
        frameTimes.addLast(frameTimeNanos)

        // 检测慢帧 (超过 33ms)
        if (frameTimeNanos > 33_333_333L) {
            consecutiveSlowFrames++
        } else {
            consecutiveSlowFrames = 0
        }
    }

    /**
     * 获取平均帧率
     */
    fun getAverageFps(): Double {
        if (frameTimes.size < 2) return 60.0
        val duration = (frameTimes.last() - frameTimes.first()) / 1_000_000_000.0
        return if (duration > 0) (frameTimes.size - 1) / duration else 60.0
    }

    /**
     * 是否应该降级动画
     *
     * 当连续出现慢帧时，建议降级动画配置
     */
    fun shouldDowngradeAnimations(): Boolean {
        return consecutiveSlowFrames >= 5 || getAverageFps() < 24.0
    }

    /**
     * 获取性能报告
     */
    fun getPerformanceReport(): PerformanceReport {
        val frameTimeList = frameTimes.toList()
        val averageFps = getAverageFps()
        val minFrameTime = frameTimeList.minOrNull() ?: 0
        val maxFrameTime = frameTimeList.maxOrNull() ?: 0
        val droppedFrames = frameTimeList.count { it > 33_333_333L }

        return PerformanceReport(
            averageFps = averageFps,
            minFrameTimeMs = minFrameTime / 1_000_000.0,
            maxFrameTimeMs = maxFrameTime / 1_000_000.0,
            totalFrames = frameTimeList.size,
            droppedFrames = droppedFrames,
            shouldDowngrade = shouldDowngradeAnimations(),
        )
    }

    /**
     * 重置监控数据
     */
    fun reset() {
        frameTimes.clear()
        consecutiveSlowFrames = 0
    }
}

/**
 * 性能报告
 */
data class PerformanceReport(
    val averageFps: Double,
    val minFrameTimeMs: Double,
    val maxFrameTimeMs: Double,
    val totalFrames: Int,
    val droppedFrames: Int,
    val shouldDowngrade: Boolean,
) {
    /**
     * 获取性能等级描述
     */
    val performanceGrade: String
        get() = when {
            averageFps >= 55 -> "优秀 (A)"
            averageFps >= 45 -> "良好 (B)"
            averageFps >= 30 -> "一般 (C)"
            averageFps >= 24 -> "较差 (D)"
            else -> "差 (F)"
        }

    /**
     * 掉帧率
     */
    val droppedFrameRate: Double
        get() = if (totalFrames > 0) droppedFrames.toDouble() / totalFrames else 0.0

    override fun toString(): String {
        return buildString {
            appendLine("=== Performance Report ===")
            appendLine("Average FPS: %.1f".format(averageFps))
            appendLine("Performance Grade: $performanceGrade")
            appendLine("Frame Time Range: %.1fms - %.1fms".format(minFrameTimeMs, maxFrameTimeMs))
            appendLine("Total Frames: $totalFrames")
            appendLine("Dropped Frames: $droppedFrames (%.1f%%)".format(droppedFrameRate * 100))
            appendLine("Should Downgrade: $shouldDowngrade")
            appendLine("==========================")
        }
    }
}

/**
 * Composable 函数：记忆设备性能等级
 */
@Composable
fun rememberDevicePerformanceTier(): DevicePerformanceTier {
    val context = LocalContext.current
    return remember { PerformanceDetector.detectTier(context) }
}

/**
 * Composable 函数：记忆动画配置
 */
@Composable
fun rememberAnimationConfig(): AnimationConfig {
    val tier = rememberDevicePerformanceTier()
    return remember(tier) { AnimationConfig.forTier(tier) }
}

/**
 * Composable 函数：记忆动画性能监控器
 */
@Composable
fun rememberAnimationPerformanceMonitor(): AnimationPerformanceMonitor {
    return remember { AnimationPerformanceMonitor() }
}
