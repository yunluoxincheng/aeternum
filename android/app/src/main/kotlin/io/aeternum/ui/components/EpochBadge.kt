package io.aeternum.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aeternum.ui.theme.EpochBadgeShape
import io.aeternum.ui.theme.EpochBadgeTextStyle
import io.aeternum.ui.theme.OnQuantumBlue
import io.aeternum.ui.theme.OnQuantumBlueContainer
import io.aeternum.ui.theme.OnQuantumGreen
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.OnQuantumYellow
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumBlueContainer
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.QuantumYellow

/**
 * Aeternum 纪元徽章组件
 *
 * 用于显示当前密码学纪元（Crypto Epoch）信息，是用户了解密钥轮换状态的重要视觉元素。
 *
 * ## 设计理念
 * - **不对称美学**: 使用不对称圆角设计增加视觉趣味
 * - **状态透明**: 清晰展示当前纪元和升级状态
 * - **动画引导**: 升级时使用动画吸引用户注意
 *
 * ## 纪元状态
 * - **Normal**: 正常运行，显示当前纪元号
 * - **Upgrading**: 纪元升级中，显示新纪元号和动画
 * - **Conflict**: 纪元冲突，警告用户可能存在安全问题
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示脱敏的纪元号，不接触密钥材料
 * - 所有纪元验证由 Rust Core 完成
 *
 * @param modifier 修饰符
 * @param currentEpoch 当前纪元号
 * @param status 纪元状态
 * @param style 徽章样式
 */
@Composable
fun EpochBadge(
    modifier: Modifier = Modifier,
    currentEpoch: UInt,
    status: EpochStatus = EpochStatus.Normal,
    style: EpochBadgeStyle = EpochBadgeStyle.Standard,
) {
    val (backgroundColor, contentColor, iconRes) = when (status) {
        is EpochStatus.Normal -> Triple(
            QuantumBlueContainer,
            OnQuantumBlueContainer,
            null,
        )
        is EpochStatus.Upgrading -> Triple(
            QuantumYellow,
            OnQuantumYellow,
            android.R.drawable.ic_menu_rotate,
        )
        is EpochStatus.Conflict -> Triple(
            QuantumRed,
            OnQuantumRed,
            android.R.drawable.stat_notify_error,
        )
    }

    // 升级动画
    val scale by animateFloatAsState(
        targetValue = if (status is EpochStatus.Upgrading) 1.05f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "scale",
    )

    val alpha by animateFloatAsState(
        targetValue = if (status is EpochStatus.Upgrading) 0.9f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "alpha",
    )

    when (style) {
        is EpochBadgeStyle.Compact -> {
            CompactEpochBadge(
                currentEpoch = currentEpoch,
                targetEpoch = (status as? EpochStatus.Upgrading)?.targetEpoch,
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                iconRes = iconRes,
                modifier = modifier
                    .scale(scale)
                    .alpha(alpha),
            )
        }
        is EpochBadgeStyle.Standard -> {
            StandardEpochBadge(
                currentEpoch = currentEpoch,
                targetEpoch = (status as? EpochStatus.Upgrading)?.targetEpoch,
                status = status,
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                iconRes = iconRes,
                modifier = modifier
                    .scale(scale)
                    .alpha(alpha),
            )
        }
    }
}

/**
 * 紧凑纪元徽章
 *
 * 仅显示纪元号，适合空间受限的场景
 */
@Composable
private fun CompactEpochBadge(
    currentEpoch: UInt,
    targetEpoch: UInt?,
    backgroundColor: Color,
    contentColor: Color,
    iconRes: Int?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(backgroundColor, EpochBadgeShape),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (iconRes != null) {
                Icon(
                    imageVector = ImageVector.vectorResource(iconRes),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = if (targetEpoch != null) {
                    "E$currentEpoch → $targetEpoch"
                } else {
                    "E$currentEpoch"
                },
                style = EpochBadgeTextStyle,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 标准纪元徽章
 *
 * 显示完整纪元信息和状态描述
 */
@Composable
private fun StandardEpochBadge(
    currentEpoch: UInt,
    targetEpoch: UInt?,
    status: EpochStatus,
    backgroundColor: Color,
    contentColor: Color,
    iconRes: Int?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(backgroundColor, EpochBadgeShape),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (iconRes != null) {
                Icon(
                    imageVector = ImageVector.vectorResource(iconRes),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = "纪元 $currentEpoch",
                style = EpochBadgeTextStyle,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
            )
            if (targetEpoch != null) {
                Text(
                    text = " → $targetEpoch",
                    style = EpochBadgeTextStyle,
                    color = contentColor.copy(alpha = 0.7f),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (status) {
                    is EpochStatus.Normal -> "正常"
                    is EpochStatus.Upgrading -> "升级中"
                    is EpochStatus.Conflict -> "冲突"
                },
                style = EpochBadgeTextStyle.copy(fontSize = 10.sp),
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * 纪元状态
 */
sealed class EpochStatus {
    /**
     * 正常状态
     *
     * 纪元运行正常，无冲突
     */
    data object Normal : EpochStatus()

    /**
     * 升级状态
     *
     * 正在执行 PQRR 密钥轮换，纪元即将升级
     *
     * @property targetEpoch 目标纪元号
     */
    data class Upgrading(val targetEpoch: UInt) : EpochStatus()

    /**
     * 冲突状态
     *
     * 检测到纪元冲突，可能存在安全问题
     *
     * @property expectedEpoch 期望的纪元号
     */
    data class Conflict(val expectedEpoch: UInt) : EpochStatus()
}

/**
 * 纪元徽章样式
 */
sealed class EpochBadgeStyle {
    /**
     * 紧凑样式
     *
     * 仅显示纪元号，适合内联显示
     */
    data object Compact : EpochBadgeStyle()

    /**
     * 标准样式
     *
     * 显示完整信息和状态描述
     */
    data object Standard : EpochBadgeStyle()
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun EpochBadgePreview_Normal() {
    io.aeternum.ui.theme.AeternumPreviewTheme {
        EpochBadge(
            currentEpoch = 1u,
            status = EpochStatus.Normal,
            style = EpochBadgeStyle.Standard,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EpochBadgePreview_Upgrading() {
    io.aeternum.ui.theme.AeternumPreviewTheme {
        EpochBadge(
            currentEpoch = 1u,
            status = EpochStatus.Upgrading(2u),
            style = EpochBadgeStyle.Standard,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EpochBadgePreview_Conflict() {
    io.aeternum.ui.theme.AeternumPreviewTheme {
        EpochBadge(
            currentEpoch = 1u,
            status = EpochStatus.Conflict(2u),
            style = EpochBadgeStyle.Standard,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EpochBadgePreview_Compact() {
    io.aeternum.ui.theme.AeternumPreviewTheme {
        Row {
            EpochBadge(
                currentEpoch = 1u,
                status = EpochStatus.Normal,
                style = EpochBadgeStyle.Compact,
            )
            Spacer(modifier = Modifier.width(8.dp))
            EpochBadge(
                currentEpoch = 1u,
                status = EpochStatus.Upgrading(2u),
                style = EpochBadgeStyle.Compact,
            )
            Spacer(modifier = Modifier.width(8.dp))
            EpochBadge(
                currentEpoch = 1u,
                status = EpochStatus.Conflict(2u),
                style = EpochBadgeStyle.Compact,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EpochBadgePreview_All() {
    io.aeternum.ui.theme.AeternumPreviewTheme {
        Row {
            EpochBadge(
                currentEpoch = 1u,
                status = EpochStatus.Normal,
                style = EpochBadgeStyle.Compact,
            )
            Spacer(modifier = Modifier.width(8.dp))
            EpochBadge(
                currentEpoch = 2u,
                status = EpochStatus.Upgrading(3u),
                style = EpochBadgeStyle.Compact,
            )
            Spacer(modifier = Modifier.width(8.dp))
            EpochBadge(
                currentEpoch = 5u,
                status = EpochStatus.Conflict(6u),
                style = EpochBadgeStyle.Compact,
            )
        }
    }
}
