package io.aeternum.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes

/**
 * Aeternum 形状系统
 *
 * 设计理念：圆角设计，现代且友好
 * 参考：Material Design 3 Shape System
 */

/**
 * Aeternum 形状规范
 *
 * 定义各种组件的圆角大小
 */
val AeternumShapes = Shapes(
    /**
     * 超小圆角 - 4dp
     *
     * 用途：标签、徽章、小按钮
     */
    extraSmall = RoundedCornerShape(4.dp),

    /**
     * 小圆角 - 8dp
     *
     * 用途：小卡片、芯片
     */
    small = RoundedCornerShape(8.dp),

    /**
     * 中等圆角 - 12dp
     *
     * 用途：标准卡片、对话框
     */
    medium = RoundedCornerShape(12.dp),

    /**
     * 大圆角 - 16dp
     *
     * 用途：大卡片、底部导航栏
     */
    large = RoundedCornerShape(16.dp),

    /**
     * 超大圆角 - 28dp
     *
     * 用途：特殊形状容器、欢迎屏幕卡片
     */
    extraLarge = RoundedCornerShape(28.dp),
)

// ============================================================================
// 自定义形状
// ============================================================================

/**
 * 纪元徽章形状
 *
 * 圆角矩形，用于显示纪元信息
 */
val EpochBadgeShape = RoundedCornerShape(
    topStart = CornerSize(4.dp),
    topEnd = CornerSize(12.dp),
    bottomEnd = CornerSize(12.dp),
    bottomStart = CornerSize(4.dp),
)

/**
 * 状态指示器形状
 *
 * 完全圆形，用于显示状态点
 */
val StatusIndicatorShape = RoundedCornerShape(50) // 50% = 圆形

/**
 * 量子动画容器形状
 *
 * 大圆角，用于动画容器
 */
val QuantumAnimationShape = RoundedCornerShape(24.dp)

/**
 * 设备卡片形状
 *
 * 不对称圆角，增加视觉趣味
 */
val DeviceCardShape = RoundedCornerShape(
    topStart = CornerSize(16.dp),
    topEnd = CornerSize(16.dp),
    bottomEnd = CornerSize(4.dp),
    bottomStart = CornerSize(16.dp),
)

/**
 * 警告横幅形状
 *
 * 小圆角，用于警告提示
 */
val WarningBannerShape = RoundedCornerShape(8.dp)

/**
 * 按钮形状集合
 */
object ButtonShapes {
    /**
     * 主要按钮形状 - 标准圆角
     */
    val Primary = RoundedCornerShape(12.dp)

    /**
     * 次要按钮形状 - 较小圆角
     */
    val Secondary = RoundedCornerShape(8.dp)

    /**
     * 危险按钮形状 - 标准圆角
     */
    val Danger = RoundedCornerShape(12.dp)

    /**
     * 图标按钮形状 - 圆形
     */
    val Icon = RoundedCornerShape(50)
}

/**
 * 卡片形状集合
 */
object CardShapes {
    /**
     * 标准卡片形状
     */
    val Standard = RoundedCornerShape(16.dp)

    /**
     * 小卡片形状
     */
    val Small = RoundedCornerShape(12.dp)

    /**
     * 大卡片形状
     */
    val Large = RoundedCornerShape(20.dp)

    /**
     * 可交互卡片形状
     */
    val Interactive = RoundedCornerShape(16.dp)
}

/**
 * 输入框形状集合
 */
object InputFieldShapes {
    /**
     * 标准输入框形状
     */
    val Standard = RoundedCornerShape(8.dp)

    /**
     * 安全输入框形状
     */
    val Secure = RoundedCornerShape(12.dp)
}

/**
 * 对话框形状
 */
val DialogShape = RoundedCornerShape(28.dp)

/**
 * 底部导航栏形状
 */
val BottomNavShape = RoundedCornerShape(
    topStart = CornerSize(0.dp),
    topEnd = CornerSize(0.dp),
    bottomEnd = CornerSize(16.dp),
    bottomStart = CornerSize(16.dp),
)

/**
 * 模态框形状
 */
val ModalShape = RoundedCornerShape(28.dp)

/**
 * 浮动操作按钮 (FAB) 形状
 */
val FabShape = RoundedCornerShape(16.dp)

/**
 * 扩展浮动操作按钮形状
 */
val ExtendedFabShape = RoundedCornerShape(16.dp)
