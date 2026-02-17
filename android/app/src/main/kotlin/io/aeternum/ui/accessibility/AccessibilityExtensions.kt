package io.aeternum.ui.accessibility

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex

/**
 * Aeternum 无障碍扩展
 *
 * 提供屏幕阅读器 (TalkBack) 支持的语义描述扩展函数。
 *
 * ## 设计理念
 * - **清晰描述**: 所有交互元素都有明确的语义描述
 * - **状态可见**: 状态变化通过语音反馈
 * - **导航友好**: 合理的遍历顺序
 *
 * ## 架构约束
 * - INVARIANT: UI 层无障碍，不涉及密钥操作
 * - 敏感信息使用脱敏描述，不泄露密钥细节
 */

// ============================================================================
// 按钮无障碍扩展
// ============================================================================

/**
 * 为按钮添加无障碍描述
 *
 * @param description 功能描述（如 "确认保存更改"）
 * @param state 当前状态（如 "已禁用"、"可用"）
 * @param traversalIndex 遍历顺序索引
 */
fun Modifier.accessibleButton(
    description: String,
    state: String? = null,
    traversalIndex: Int? = null,
): Modifier = this.semantics {
    role = Role.Button
    contentDescription = description
    state?.let { stateDescription = it }
    traversalIndex?.let { this.traversalIndex = it.toFloat() }
}

/**
 * 为危险操作按钮添加无障碍描述
 *
 * 包含额外的警告提示
 */
fun Modifier.accessibleDangerButton(
    description: String,
    warning: String = "警告：此操作不可撤销",
    traversalIndex: Int? = null,
): Modifier = this.semantics {
    role = Role.Button
    contentDescription = "$description。$warning"
    traversalIndex?.let { this.traversalIndex = it.toFloat() }
}

/**
 * 为图标按钮添加无障碍描述
 *
 * @param iconDescription 图标含义描述
 * @param actionDescription 操作描述
 */
fun Modifier.accessibleIconButton(
    iconDescription: String,
    actionDescription: String,
): Modifier = this.semantics {
    role = Role.Button
    contentDescription = "$iconDescription，$actionDescription"
}

// ============================================================================
// 状态指示器无障碍扩展
// ============================================================================

/**
 * 为状态指示器添加无障碍描述
 *
 * @param status 状态名称（如 "安全"、"警告"）
 * @param details 状态详情
 */
fun Modifier.accessibleStatus(
    status: String,
    details: String? = null,
): Modifier = this.semantics {
    role = Role.Image
    contentDescription = if (details != null) {
        "状态：$status。$details"
    } else {
        "状态：$status"
    }
}

/**
 * 为纪元徽章添加无障碍描述
 *
 * @param epoch 纪元号
 * @param isLatest 是否为最新纪元
 */
fun Modifier.accessibleEpoch(
    epoch: Int,
    isLatest: Boolean,
): Modifier = this.semantics {
    role = Role.Image
    contentDescription = if (isLatest) {
        "当前纪元：第 $epoch 代，最新版本"
    } else {
        "历史纪元：第 $epoch 代"
    }
}

// ============================================================================
// 设备信息无障碍扩展
// ============================================================================

/**
 * 为设备卡片添加无障碍描述
 *
 * @param deviceName 设备名称
 * @param status 设备状态
 * @param role 设备角色（Owner/Recovery/Member）
 * @param isCurrentDevice 是否为当前设备
 */
fun Modifier.accessibleDevice(
    deviceName: String,
    status: String,
    role: String,
    isCurrentDevice: Boolean,
): Modifier = this.semantics {
    this.role = Role.Button
    val currentText = if (isCurrentDevice) "当前设备，" else ""
    contentDescription = "$currentText$deviceName，状态：$status，角色：$role。点击查看详情"
}

/**
 * 为设备撤销按钮添加无障碍描述
 */
fun Modifier.accessibleRevokeDevice(
    deviceName: String,
): Modifier = this.semantics {
    role = Role.Button
    contentDescription = "撤销设备 $deviceName。警告：撤销后该设备将无法访问您的数据"
}

// ============================================================================
// 密钥和恢复无障碍扩展
// ============================================================================

/**
 * 为助记词显示区域添加无障碍描述
 *
 * 注意：助记词本身不通过屏幕阅读器朗读，仅提供存在性描述
 */
fun Modifier.accessibleMnemonicArea(
    wordCount: Int = 24,
): Modifier = this.semantics {
    role = Role.Image
    contentDescription = "助记词区域，包含 $wordCount 个单词。请确保在安全环境下查看"
}

/**
 * 为恢复流程添加无障碍描述
 *
 * @param step 当前步骤
 * @param totalSteps 总步骤数
 */
fun Modifier.accessibleRecoveryStep(
    step: Int,
    totalSteps: Int,
    description: String,
): Modifier = this.semantics {
    contentDescription = "步骤 $step / $totalSteps：$description"
}

/**
 * 为否决按钮添加无障碍描述
 */
fun Modifier.accessibleVetoButton(
    remainingTime: String,
): Modifier = this.semantics {
    role = Role.Button
    contentDescription = "否决恢复请求。剩余时间：$remainingTime。点击将阻止此次恢复"
}

// ============================================================================
// 列表和导航无障碍扩展
// ============================================================================

/**
 * 为列表项添加无障碍描述
 *
 * @param index 当前索引
 * @param total 总数
 * @param content 内容描述
 */
fun Modifier.accessibleListItem(
    index: Int,
    total: Int,
    content: String,
): Modifier = this.semantics {
    role = Role.Button
    contentDescription = "$content，第 ${index + 1} 项，共 $total 项"
}

/**
 * 为导航项添加无障碍描述
 *
 * @param title 标题
 * @param isSelected 是否选中
 */
fun Modifier.accessibleNavItem(
    title: String,
    isSelected: Boolean,
): Modifier = this.semantics {
    role = Role.Tab
    contentDescription = title
    stateDescription = if (isSelected) "已选中" else "未选中"
}

// ============================================================================
// 表单输入无障碍扩展
// ============================================================================

/**
 * 为安全文本字段添加无障碍描述
 *
 * @param label 字段标签
 * @param isRequired 是否必填
 * @param errorMessage 错误信息（如有）
 */
fun Modifier.accessibleSecureField(
    label: String,
    isRequired: Boolean = false,
    errorMessage: String? = null,
): Modifier = this.semantics {
    // 注意: Role.TextBox 和 Role.TextField 在较新 API 中可能不可用
    // 改用 Role.DropdownMenuItem 或直接不设置 role
    val requiredText = if (isRequired) "（必填）" else ""
    contentDescription = "$label$requiredText"
    errorMessage?.let { stateDescription = "错误：$it" }
}

/**
 * 为倒计时显示添加无障碍描述
 *
 * @param remainingSeconds 剩余秒数
 * @param context 上下文描述
 */
fun Modifier.accessibleCountdown(
    remainingSeconds: Int,
    context: String,
): Modifier = this.semantics {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeText = if (minutes > 0) {
        "$minutes 分钟 $seconds 秒"
    } else {
        "$seconds 秒"
    }
    contentDescription = "$context：剩余 $timeText"
}

// ============================================================================
// 辅助函数
// ============================================================================

/**
 * 格式化时间为无障碍友好格式
 */
fun formatAccessibleTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return buildString {
        if (hours > 0) append("$hours 小时 ")
        if (minutes > 0) append("$minutes 分钟 ")
        if (secs > 0 || isEmpty()) append("$secs 秒")
    }.trim()
}

/**
 * 生成状态变化的无障碍播报文本
 */
fun generateStateChangeAnnouncement(
    fromState: String,
    toState: String,
    reason: String? = null,
): String {
    return buildString {
        append("状态已从 $fromState 变更为 $toState")
        reason?.let { append("。原因：$it") }
    }
}
