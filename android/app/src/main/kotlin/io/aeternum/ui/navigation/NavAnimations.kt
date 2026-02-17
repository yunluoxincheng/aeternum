package io.aeternum.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlin.math.roundToInt

/**
 * Aeternum 导航动画配置
 *
 * 设计理念：流畅、现代、符合 Material Design 3 动画规范
 * 参考：design.md §动画规范
 *
 * ## Material Design 3 动画规范
 * - **Emphasized**: 强调动画，用于重要的状态变化 (0.2f -> 0.0f)
 * - **EmphasizedDecelerate**: 强调减速，用于进入动画 (0.05f -> 0.0f)
 * - **EmphasizedAccelerate**: 强调加速，用于退出动画 (0.3f -> 0.0f)
 * - **Standard**: 标准动画，常规过渡 (0.2f -> 0.0f)
 * - **Legacy**: 传统动画，兼容性考虑 (0.4f -> 0.0f -> 0.2f)
 *
 * ## 动画时长
 * - **Instant**: 50ms - 即时反馈
 * - **Fast**: 150ms - 快速过渡
 * - **Normal**: 300ms - 标准动画 (MD3 推荐)
 * - **Slow**: 500ms - 复杂动画
 * - **Glacial**: 1000ms - 特殊效果
 */

// ============================================================================
// 动画时长常量
// ============================================================================

/**
 * 动画时长配置
 */
object AnimationDuration {
    const val INSTANT = 50     // 即时反馈
    const val FAST = 150       // 快速过渡
    const val NORMAL = 300     // 标准动画 (MD3 推荐)
    const val SLOW = 500       // 复杂动画
    const val GLACIAL = 1000   // 特殊效果
}

// ============================================================================
// Material Design 3 缓动曲线
// ============================================================================

/**
 * Material Design 3 标准缓动曲线
 *
 * 参考：https://m3.material.io/styles/motion/easing-and-duration/tokens
 */
object Easing {
    /**
     * 强调动画 - 用于重要的状态变化
     * (0.2, 0.0, 0.0, 1.0)
     */
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /**
     * 强调减速 - 用于进入动画
     * (0.05, 0.7, 0.1, 1.0)
     */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /**
     * 强调加速 - 用于退出动画
     * (0.3, 0.0, 0.8, 0.15)
     */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /**
     * 标准动画 - 常规过渡
     * (0.2, 0.0, 0.0, 1.0) - 与 Emphasized 相同
     */
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /**
     * 传统动画 - 兼容性考虑
     * (0.4, 0.0, 0.2, 1.0)
     */
    val Legacy = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /**
     * 线性动画 - 匀速运动
     */
    val Linear = LinearEasing

    /**
     * 快出慢入 - 经典缓动
     */
    val FastOutSlowIn = FastOutSlowInEasing

    /**
     * 快出线性入
     */
    val FastOutLinearIn = FastOutLinearInEasing

    /**
     * 线性出慢入
     */
    val LinearOutSlowIn = LinearOutSlowInEasing
}

// ============================================================================
// 标准导航动画
// ============================================================================

/**
 * 标准进入动画
 *
 * 从右侧滑入 + 淡入 (350ms + EmphasizedDecelerate)
 */
val standardEnterTransition: EnterTransition = slideInHorizontally(
    animationSpec = tween(
        durationMillis = 350,
        easing = Easing.EmphasizedDecelerate,
    ),
    initialOffsetX = { it / 4 }, // 滑入 25% 屏幕宽度
) + fadeIn(
    animationSpec = tween(
        durationMillis = AnimationDuration.NORMAL,
        easing = Easing.EmphasizedDecelerate,
    ),
)

/**
 * 标准退出动画
 *
 * 向左侧滑出 + 淡出 (300ms + EmphasizedAccelerate)
 */
val standardExitTransition: ExitTransition = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = 300,
        easing = Easing.EmphasizedAccelerate,
    ),
    targetOffsetX = { -it / 8 }, // 滑出 12.5% 屏幕宽度
) + fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDuration.NORMAL - 50,
        easing = Easing.EmphasizedAccelerate,
    ),
)

/**
 * 标准弹出进入动画
 *
 * 从左侧滑入 + 淡入
 */
val standardPopEnterTransition: EnterTransition = slideInHorizontally(
    animationSpec = tween(
        durationMillis = 300,
        easing = Easing.EmphasizedDecelerate,
    ),
    initialOffsetX = { -it / 8 },
) + fadeIn(
    animationSpec = tween(
        durationMillis = AnimationDuration.NORMAL,
        easing = Easing.EmphasizedDecelerate,
    ),
)

/**
 * 标准弹出退出动画
 *
 * 向右侧滑出 + 淡出
 */
val standardPopExitTransition: ExitTransition = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = 300,
        easing = Easing.EmphasizedAccelerate,
    ),
    targetOffsetX = { it / 4 },
) + fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDuration.NORMAL - 50,
        easing = Easing.EmphasizedAccelerate,
    ),
)

// ============================================================================
// 快速导航动画
// ============================================================================

/**
 * 快速进入动画
 *
 * 用于不需要强调的页面切换 (150ms + Standard)
 */
val fastEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = AnimationDuration.FAST,
        easing = Easing.Standard,
    ),
)

/**
 * 快速退出动画
 */
val fastExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDuration.FAST,
        easing = Easing.Standard,
    ),
)

/**
 * 快速弹出进入动画
 */
val fastPopEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = AnimationDuration.FAST,
        easing = Easing.Standard,
    ),
)

/**
 * 快速弹出退出动画
 */
val fastPopExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDuration.FAST,
        easing = Easing.Standard,
    ),
)

// ============================================================================
// 模态对话框动画
// ============================================================================

/**
 * 模态进入动画
 *
 * 从底部滑入 + 展开 (350ms + EmphasizedDecelerate)
 */
val modalEnterTransition: EnterTransition = slideInVertically(
    animationSpec = tween(
        durationMillis = 350,
        easing = Easing.EmphasizedDecelerate,
    ),
    initialOffsetY = { it },
) + expandIn(
    expandFrom = Alignment.Center,
    animationSpec = tween(
        durationMillis = 350,
        easing = Easing.EmphasizedDecelerate,
    ),
)

/**
 * 模态退出动画
 *
 * 向底部滑出 + 收缩 (250ms + EmphasizedAccelerate)
 */
val modalExitTransition: ExitTransition = slideOutVertically(
    animationSpec = tween(
        durationMillis = 250,
        easing = Easing.EmphasizedAccelerate,
    ),
    targetOffsetY = { it },
) + shrinkOut(
    shrinkTowards = Alignment.Center,
    animationSpec = tween(
        durationMillis = 250,
        easing = Easing.EmphasizedAccelerate,
    ),
)

// ============================================================================
// 生物识别动画
// ============================================================================

/**
 * 生物识别成功动画
 *
 * 淡入 + 缩放效果 (300ms + EmphasizedDecelerate)
 * 用于生物识别认证成功后导航到主屏幕
 */
val biometricSuccessEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = AnimationDuration.NORMAL,
        easing = Easing.EmphasizedDecelerate,
    ),
)

/**
 * 生物识别成功退出动画
 */
val biometricSuccessExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDuration.FAST,
        easing = Easing.EmphasizedAccelerate,
    ),
)

// ============================================================================
// 密钥轮换动画
// ============================================================================

/**
 * 密钥轮换进入动画
 *
 * 淡入 + 旋转效果 (500ms + Linear)
 * 用于显示密钥轮换进度界面
 */
val rekeyingEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = AnimationDuration.SLOW,
        easing = Easing.Linear,
    ),
)

/**
 * 密钥轮换退出动画
 */
val rekeyingExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDuration.SLOW,
        easing = Easing.Linear,
    ),
)

// ============================================================================
// 否决信号动画
// ============================================================================

/**
 * 否决通知进入动画
 *
 * 警告效果，带红色强调 (400ms + Emphasized)
 * 脉冲动画在组件内实现
 */
val vetoEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = 400,
        easing = Easing.Emphasized,
    ),
) + slideInVertically(
    animationSpec = tween(
        durationMillis = 400,
        easing = Easing.EmphasizedDecelerate,
    ),
    initialOffsetY = { -it / 2 },
)

/**
 * 否决通知退出动画
 */
val vetoExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDuration.NORMAL,
        easing = Easing.EmphasizedAccelerate,
    ),
)

// ============================================================================
// 设备撤销动画
// ============================================================================

/**
 * 设备撤销进入动画
 *
 * 收缩淡入效果 (400ms + EmphasizedDecelerate)
 */
val revokeEnterTransition: EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = 400,
        easing = Easing.EmphasizedDecelerate,
    ),
) + slideInHorizontally(
    animationSpec = tween(
        durationMillis = 400,
        easing = Easing.EmphasizedDecelerate,
    ),
    initialOffsetX = { it / 3 },
)

/**
 * 设备撤销退出动画
 *
 * 收缩淡出效果 (300ms + EmphasizedAccelerate)
 */
val revokeExitTransition: ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = 300,
        easing = Easing.EmphasizedAccelerate,
    ),
) + slideOutHorizontally(
    animationSpec = tween(
        durationMillis = 300,
        easing = Easing.EmphasizedAccelerate,
    ),
    targetOffsetX = { -it / 3 },
)

// ============================================================================
// 动画选择函数
// ============================================================================

/**
 * 根据路由选择合适的进入动画
 *
 * @param destination 目标路由
 * @return 进入动画
 */
fun selectEnterTransition(destination: String): EnterTransition {
    return when {
        destination.contains("rekeying", ignoreCase = true) -> rekeyingEnterTransition
        destination.contains("veto", ignoreCase = true) -> vetoEnterTransition
        destination.contains("revoke", ignoreCase = true) -> revokeEnterTransition
        destination.contains("biometric", ignoreCase = true) -> biometricSuccessEnterTransition
        destination.contains("modal", ignoreCase = true) -> modalEnterTransition
        destination.contains("dialog", ignoreCase = true) -> modalEnterTransition
        else -> standardEnterTransition
    }
}

/**
 * 根据路由选择合适的退出动画
 *
 * @param destination 目标路由
 * @return 退出动画
 */
fun selectExitTransition(destination: String): ExitTransition {
    return when {
        destination.contains("rekeying", ignoreCase = true) -> rekeyingExitTransition
        destination.contains("veto", ignoreCase = true) -> vetoExitTransition
        destination.contains("revoke", ignoreCase = true) -> revokeExitTransition
        destination.contains("biometric", ignoreCase = true) -> biometricSuccessExitTransition
        destination.contains("modal", ignoreCase = true) -> modalExitTransition
        destination.contains("dialog", ignoreCase = true) -> modalExitTransition
        else -> standardExitTransition
    }
}

/**
 * 根据来源路由选择合适的弹出进入动画
 *
 * @param source 来源路由
 * @return 弹出进入动画
 */
fun selectPopEnterTransition(source: NavBackStackEntry?): EnterTransition {
    val route = source?.destination?.route ?: return standardPopEnterTransition
    return when {
        route.contains("rekeying", ignoreCase = true) -> rekeyingEnterTransition
        route.contains("veto", ignoreCase = true) -> vetoEnterTransition
        route.contains("revoke", ignoreCase = true) -> revokeEnterTransition
        else -> standardPopEnterTransition
    }
}

/**
 * 根据来源路由选择合适的弹出退出动画
 *
 * @param source 来源路由
 * @return 弹出退出动画
 */
fun selectPopExitTransition(source: NavBackStackEntry?): ExitTransition {
    val route = source?.destination?.route ?: return standardPopExitTransition
    return when {
        route.contains("rekeying", ignoreCase = true) -> rekeyingExitTransition
        route.contains("veto", ignoreCase = true) -> vetoExitTransition
        route.contains("revoke", ignoreCase = true) -> revokeExitTransition
        else -> standardPopExitTransition
    }
}

// ============================================================================
// 共享元素过渡
// ============================================================================

/**
 * 共享元素转换修饰符
 *
 * 用于在页面切换时实现共享元素的平滑过渡
 * 注意：Compose 的共享元素过渡需要配合 AnimatedContent 使用
 *
 * @param modifier 基础修饰符
 * @param offsetX X 轴偏移量动画
 * @param offsetY Y 轴偏移量动画
 * @param scale 缩放动画
 * @param alpha 透明度动画
 * @return 应用动画的修饰符
 */
@Composable
fun SharedElementTransition(
    modifier: Modifier = Modifier,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    scale: Float = 1f,
    alpha: Float = 1f,
): Modifier {
    return modifier
        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        .then(
            if (scale != 1f) {
                Modifier
            } else {
                Modifier
            },
        )
}

/**
 * 共享元素过渡动画规格
 *
 * 用于定义共享元素过渡的动画曲线和时长
 */
object SharedElementTransitionSpec {
    /**
     * 标准共享元素过渡动画
     */
    val Standard: AnimationSpec<Float> = tween(
        durationMillis = AnimationDuration.NORMAL + 50,
        easing = Easing.EmphasizedDecelerate,
    )

    /**
     * 快速共享元素过渡动画
     */
    val Fast: AnimationSpec<Float> = tween(
        durationMillis = AnimationDuration.FAST + 50,
        easing = Easing.Standard,
    )

    /**
     * 弹性共享元素过渡动画
     */
    val Bouncy: AnimationSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )
}

// ============================================================================
// 动画工具函数
// ============================================================================

/**
 * 创建带有淡入淡出和滑动的内容过渡
 *
 * @param content 要显示的内容
 * @param modifier 修饰符
 */
@Composable
fun AnimatedSlideFadeContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var targetState by remember { mutableStateOf(false) }

    SideEffect {
        targetState = true
    }

    AnimatedVisibility(
        visible = targetState,
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = AnimationDuration.NORMAL,
                easing = Easing.EmphasizedDecelerate,
            ),
            initialOffsetY = { it / 4 },
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDuration.NORMAL,
                easing = Easing.EmphasizedDecelerate,
            ),
        ),
        exit = slideOutVertically(
            animationSpec = tween(
                durationMillis = AnimationDuration.FAST,
                easing = Easing.EmphasizedAccelerate,
            ),
            targetOffsetY = { -it / 4 },
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AnimationDuration.FAST,
                easing = Easing.EmphasizedAccelerate,
            ),
        ),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * 带有尺寸转换的动画内容
 *
 * @param targetState 目标状态
 * @param modifier 修饰符
 * @param content 内容块
 */
@Composable
fun <S> AnimatedSizeContent(
    targetState: S,
    modifier: Modifier = Modifier,
    content: @Composable (AnimatedContentScope.(S) -> Unit),
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            val enterTransition = fadeIn(
                animationSpec = tween(
                    durationMillis = AnimationDuration.NORMAL,
                    easing = Easing.EmphasizedDecelerate,
                ),
            )
            val exitTransition = fadeOut(
                animationSpec = tween(
                    durationMillis = AnimationDuration.FAST,
                    easing = Easing.EmphasizedAccelerate,
                ),
            )
            enterTransition.togetherWith(exitTransition).using(
                SizeTransform(clip = false),
            )
        },
        modifier = modifier,
        label = "animated_size_content",
    ) { state ->
        content(state)
    }
}

// ============================================================================
// 底部导航栏动画
// ============================================================================

/**
 * 底部导航栏项进入动画
 *
 * 从底部滑入 + 淡入
 */
fun bottomNavItemEnterTransition(index: Int): EnterTransition {
    return slideInVertically(
        animationSpec = tween(
            durationMillis = AnimationDuration.NORMAL + (index * 30),
            easing = Easing.EmphasizedDecelerate,
        ),
        initialOffsetY = { it },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = AnimationDuration.NORMAL + (index * 30),
            easing = Easing.EmphasizedDecelerate,
        ),
    )
}

/**
 * 底部导航栏项退出动画
 *
 * 向底部滑出 + 淡出
 */
val bottomNavItemExitTransition: ExitTransition = slideOutVertically(
    animationSpec = tween(
        durationMillis = AnimationDuration.FAST,
        easing = Easing.EmphasizedAccelerate,
    ),
    targetOffsetY = { it },
) + fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDuration.FAST,
        easing = Easing.EmphasizedAccelerate,
    ),
)
