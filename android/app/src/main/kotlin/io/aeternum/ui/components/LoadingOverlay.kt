package io.aeternum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.DeepSpaceBackground

/**
 * Aeternum 加载遮罩组件
 *
 * 用于覆盖屏幕显示加载状态，防止用户在加载过程中进行其他操作。
 *
 * ## 设计理念
 * - **视觉反馈**: 清晰的加载动画和消息提示
 * - **操作阻断**: 防止用户在加载过程中进行其他操作
 * - **量子风格**: 使用量子动画增强科技感
 *
 * ## 遮罩模式
 * - **Fullscreen**: 全屏遮罩，覆盖整个屏幕
 * - **Dialog**: 对话框遮罩，居中显示
 * - **Partial**: 部分遮罩，仅覆盖特定区域
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅显示加载状态，不执行敏感操作
 * - 所有加载操作由 Rust Core 触发和管理
 *
 * @param isLoading 是否显示加载遮罩
 * @param modifier 修饰符
 * @param message 加载消息
 * @param type 遮罩类型
 * @param animationType 动画类型
 * @param cancelable 是否可取消
 * @param onCancel 取消回调
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    message: String? = null,
    type: LoadingOverlayType = LoadingOverlayType.Fullscreen,
    animationType: QuantumAnimationType = QuantumAnimationType.Rotating(),
    cancelable: Boolean = false,
    onCancel: (() -> Unit)? = null,
) {
    if (!isLoading) return

    when (type) {
        is LoadingOverlayType.Fullscreen -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(DeepSpaceBackground.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingContent(
                    message = message,
                    animationType = animationType,
                    cancelable = cancelable,
                    onCancel = onCancel,
                )
            }
        }
        is LoadingOverlayType.Dialog -> {
            Dialog(
                onDismissRequest = {
                    if (cancelable) {
                        onCancel?.invoke()
                    }
                },
                properties = DialogProperties(
                    dismissOnBackPress = cancelable,
                    dismissOnClickOutside = cancelable,
                ),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 8.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingContent(
                            message = message,
                            animationType = animationType,
                            cancelable = cancelable,
                            onCancel = onCancel,
                        )
                    }
                }
            }
        }
        is LoadingOverlayType.Partial -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .background(DeepSpaceBackground.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingContent(
                    message = message,
                    animationType = animationType,
                    cancelable = false,
                    onCancel = null,
                )
            }
        }
    }
}

/**
 * 加载内容
 */
@Composable
private fun LoadingContent(
    message: String?,
    animationType: QuantumAnimationType,
    cancelable: Boolean,
    onCancel: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 量子动画
        QuantumAnimation(
            type = animationType,
            size = 64.dp,
        )

        if (message != null) {
            Spacer(modifier = Modifier.size(24.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (cancelable && onCancel != null) {
            Spacer(modifier = Modifier.size(24.dp))
            ActionButton(
                text = "取消",
                onClick = onCancel,
                type = ButtonType.Secondary,
                size = ButtonSize.Small,
            )
        }
    }
}

/**
 * 加载遮罩类型
 */
sealed class LoadingOverlayType {
    /**
     * 全屏遮罩
     *
     * 覆盖整个屏幕，常用于初始化、数据加载等场景
     */
    data object Fullscreen : LoadingOverlayType()

    /**
     * 对话框遮罩
     *
     * 居中显示的对话框形式，常用于异步操作
     */
    data object Dialog : LoadingOverlayType()

    /**
     * 部分遮罩
     *
     * 仅覆盖特定区域，常用于列表项加载
     */
    data object Partial : LoadingOverlayType()
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun LoadingOverlayPreview_Fullscreen() {
    AeternumPreviewTheme {
        LoadingOverlay(
            isLoading = true,
            message = "正在初始化...",
            type = LoadingOverlayType.Fullscreen,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingOverlayPreview_Dialog() {
    AeternumPreviewTheme {
        LoadingOverlay(
            isLoading = true,
            message = "正在同步密钥...",
            type = LoadingOverlayType.Dialog,
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
private fun LoadingOverlayPreview_Partial() {
    AeternumPreviewTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            Text(
                text = "底层内容",
                modifier = Modifier.padding(16.dp),
            )
            LoadingOverlay(
                isLoading = true,
                message = "加载中...",
                type = LoadingOverlayType.Partial,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingOverlayPreview_WithCancel() {
    AeternumPreviewTheme {
        LoadingOverlay(
            isLoading = true,
            message = "正在执行 PQRR 密钥轮换，这可能需要几分钟时间...",
            type = LoadingOverlayType.Fullscreen,
            cancelable = true,
            onCancel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingOverlayPreview_Rotating() {
    AeternumPreviewTheme {
        LoadingOverlay(
            isLoading = true,
            message = "密钥轮换中...",
            type = LoadingOverlayType.Dialog,
            animationType = QuantumAnimationType.Rotating(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingOverlayPreview_Pulsing() {
    AeternumPreviewTheme {
        LoadingOverlay(
            isLoading = true,
            message = "等待其他设备响应...",
            type = LoadingOverlayType.Dialog,
            animationType = QuantumAnimationType.Pulsing,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingOverlayPreview_Fading() {
    AeternumPreviewTheme {
        LoadingOverlay(
            isLoading = true,
            message = "正在验证设备完整性...",
            type = LoadingOverlayType.Dialog,
            animationType = QuantumAnimationType.Fading,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun LoadingOverlayPreview_LongMessage() {
    AeternumPreviewTheme {
        LoadingOverlay(
            isLoading = true,
            message = "正在执行密码学纪元升级，请保持应用开启。此过程将生成新的密钥材料并重新加密您的数据，可能需要几分钟时间。",
            type = LoadingOverlayType.Dialog,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun LoadingOverlayPreview_NoMessage() {
    AeternumPreviewTheme {
        LoadingOverlay(
            isLoading = true,
            message = null,
            type = LoadingOverlayType.Fullscreen,
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun LoadingOverlayPreview_AllTypes() {
    AeternumPreviewTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("全屏遮罩:")
            LoadingOverlay(
                isLoading = true,
                message = "全屏加载中...",
                type = LoadingOverlayType.Fullscreen,
                modifier = Modifier.height(150.dp),
            )

            Spacer(modifier = Modifier.size(16.dp))
            Text("对话框遮罩:")
            LoadingOverlay(
                isLoading = true,
                message = "对话框加载中...",
                type = LoadingOverlayType.Dialog,
                modifier = Modifier.height(150.dp),
            )

            Spacer(modifier = Modifier.size(16.dp))
            Text("部分遮罩:")
            LoadingOverlay(
                isLoading = true,
                message = "部分加载中...",
                type = LoadingOverlayType.Partial,
                modifier = Modifier.height(150.dp),
            )
        }
    }
}
