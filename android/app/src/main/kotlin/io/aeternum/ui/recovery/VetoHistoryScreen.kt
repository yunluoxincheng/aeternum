package io.aeternum.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnQuantumGreen
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.SurfaceColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * 否决历史屏幕
 *
 * 显示历史上所有的否决记录，包括：
 * - 否决的恢复请求 ID
 * - 否决时间
 * - 否决原因
 * - 否决状态（成功/失败）
 * - 是否为本机否决
 *
 * INVARIANT: UI 层 - 仅显示脱敏数据，不持有任何密钥
 * 参考：design.md §恢复流程设计
 * 参考：Cold-Anchor-Recovery.md §3 否决机制
 *
 * @param vetoHistory 否决历史记录列表
 * @param onBack 返回回调
 * @param onItemClick 项目点击回调（查看详情）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetoHistoryScreen(
    vetoHistory: List<VetoHistoryItem>,
    onBack: () -> Unit,
    onItemClick: (VetoHistoryItem) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("否决历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = OnDeepSpaceBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceColor,
                    titleContentColor = OnDeepSpaceBackground,
                ),
            )
        },
        containerColor = DeepSpaceBackground,
    ) { paddingValues ->
        if (vetoHistory.isEmpty()) {
            // 空状态
            EmptyVetoHistoryState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            // 历史记录列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 统计摘要
                item {
                    VetoHistorySummary(vetoHistory = vetoHistory)
                }

                // 历史记录列表
                items(vetoHistory) { item ->
                    VetoHistoryCard(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

/**
 * 空状态 - 无否决历史
 */
@Composable
private fun EmptyVetoHistoryState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 空状态图标
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(SurfaceColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = OnSurfaceVariantColor,
                modifier = Modifier.size(60.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "暂无否决历史",
            style = MaterialTheme.typography.headlineSmall,
            color = OnDeepSpaceBackground,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "当您否决恢复请求时，记录将显示在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 否决历史统计摘要
 */
@Composable
private fun VetoHistorySummary(
    vetoHistory: List<VetoHistoryItem>,
) {
    val successfulVetoes = vetoHistory.count { it.status == VetoStatus.Successful }
    val failedVetoes = vetoHistory.count { it.status == VetoStatus.Failed }
    val myVetoes = vetoHistory.count { it.isThisDevice }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceColor,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "历史统计",
                style = MaterialTheme.typography.labelLarge,
                color = OnSurfaceVariantColor,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatisticItem(
                    label = "总计",
                    value = vetoHistory.size.toString(),
                    icon = Icons.Default.History,
                    color = QuantumGreen,
                )

                StatisticItem(
                    label = "成功",
                    value = successfulVetoes.toString(),
                    icon = Icons.Default.CheckCircle,
                    color = QuantumGreen,
                )

                StatisticItem(
                    label = "失败",
                    value = failedVetoes.toString(),
                    icon = Icons.Default.Info,
                    color = QuantumRed,
                )

                StatisticItem(
                    label = "本机",
                    value = myVetoes.toString(),
                    icon = Icons.Default.CheckCircle,
                    color = QuantumGreen,
                )
            }
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatisticItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = OnDeepSpaceBackground,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariantColor,
        )
    }
}

/**
 * 否决历史卡片
 */
@Composable
private fun VetoHistoryCard(
    item: VetoHistoryItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceColor,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // 顶部：状态和时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态标签
                VetoStatusBadge(status = item.status)

                // 时间
                Text(
                    text = formatTimestamp(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantColor,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 恢复 ID
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "恢复 ID: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariantColor,
                )

                Text(
                    text = item.recoveryId.take(12) + if (item.recoveryId.length > 12) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDeepSpaceBackground,
                    fontWeight = FontWeight.Medium,
                )
            }

            // 原因（如果有）
            if (item.reason != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "原因: ${item.reason.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 底部：设备信息和窗口状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 设备标识
                if (item.isThisDevice) {
                    Text(
                        text = "本机否决",
                        style = MaterialTheme.typography.labelSmall,
                        color = QuantumGreen,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = "来自 ${item.vetoingDeviceName ?: "其他设备"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantColor,
                    )
                }

                // 窗口状态
                Text(
                    text = when {
                        item.status == VetoStatus.Successful -> "恢复已取消"
                        item.status == VetoStatus.Failed -> "否决失败"
                        item.remainingWindow != null && item.remainingWindow!! > Duration.ZERO -> {
                            "窗口剩余: ${formatDuration(item.remainingWindow!!)}"
                        }
                        else -> "窗口已关闭"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.status == VetoStatus.Successful) {
                        OnQuantumGreen
                    } else {
                        OnSurfaceVariantColor
                    },
                )
            }
        }
    }
}

/**
 * 否决状态徽章
 */
@Composable
private fun VetoStatusBadge(status: VetoStatus) {
    val (text, color, onColor) = when (status) {
        VetoStatus.Successful -> Triple("成功", QuantumGreen, OnQuantumGreen)
        VetoStatus.Failed -> Triple("失败", QuantumRed, OnQuantumRed)
        VetoStatus.Pending -> Triple("处理中", QuantumGreen, OnQuantumGreen)
        VetoStatus.Expired -> Triple("已过期", QuantumRed, OnQuantumRed)
    }

    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.ofEpochSecond(timestamp)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

/**
 * 格式化持续时间
 */
private fun formatDuration(duration: Duration): String {
    return buildString {
        val days = duration.inWholeDays
        val hours = (duration - days.days).inWholeHours

        if (days > 0) {
            append("${days}天")
        }
        if (hours > 0) {
            append("${hours}时")
        }
        if (days == 0L && hours == 0L) {
            val minutes = (duration - days.days - hours.hours).inWholeMinutes
            append("${minutes}分")
        }
    }
}

/**
 * 否决历史记录项
 *
 * INVARIANT: 仅包含脱敏数据，不包含任何密钥材料
 *
 * @property recoveryId 恢复请求 ID（脱敏，仅用于显示）
 * @property timestamp 否决时间戳（Unix 时间戳，秒）
 * @property status 否决状态
 * @property reason 否决原因（可选）
 * @property vetoingDeviceName 否决设备名称（可选）
 * @property isThisDevice 是否为本机否决
 * @property remainingWindow 剩余窗口时间（可选）
 */
data class VetoHistoryItem(
    val recoveryId: String,
    val timestamp: Long,
    val status: VetoStatus,
    val reason: VetoReason? = null,
    val vetoingDeviceName: String? = null,
    val isThisDevice: Boolean = false,
    val remainingWindow: Duration? = null,
)

/**
 * 否决状态枚举
 */
enum class VetoStatus {
    /**
     * 否决成功 - 恢复请求已被取消
     */
    Successful,

    /**
     * 否决失败 - 恢复请求继续进行
     */
    Failed,

    /**
     * 处理中 - 否决请求正在处理
     */
    Pending,

    /**
     * 已过期 - 48h 窗口已关闭
     */
    Expired,
}
