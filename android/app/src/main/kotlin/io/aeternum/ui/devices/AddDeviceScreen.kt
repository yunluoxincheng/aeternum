package io.aeternum.ui.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.aeternum.ui.components.ActionButton
import io.aeternum.ui.components.QuantumAnimation
import io.aeternum.ui.components.QuantumAnimationType
import io.aeternum.ui.theme.MachineStateColor
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumGreen
import io.aeternum.ui.theme.SurfaceColor
import io.aeternum.ui.viewmodel.AeternumViewModel

/**
 * 添加设备屏幕
 *
 * 用于引导用户完成新设备添加流程。
 *
 * ## 设计理念
 * - **清晰流程**: 分步骤展示添加流程，用户随时了解当前进度
 * - **多维在场确认**: QR 码扫描 + 近场通信（未来扩展）
 * - **安全第一**: 所有验证通过 Rust Core 执行，UI 仅展示流程
 *
 * ## 架构约束
 * - INVARIANT: UI 层不执行设备添加逻辑，仅收集用户输入
 * - INVARIANT: QR 码内容为加密的握手令牌，由 Rust Core 生成
 * - 所有安全验证由 Rust Core 的 PQRR 协议处理
 *
 * ## 设备添加流程
 * 1. **准备阶段**: 生成握手令牌，显示 QR 码
 * 2. **扫描阶段**: 新设备扫描 QR 码（或输入验证码）
 * 3. **验证阶段**: 双向身份验证，建立加密隧道
 * 4. **完成阶段**: 设备注册成功，更新设备列表
 *
 * @param viewModel Aeternum ViewModel
 * @param onNavigateBack 返回上一页的回调
 * @param onDeviceAdded 设备添加成功后的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    viewModel: AeternumViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onDeviceAdded: () -> Unit = {},
) {
    // 收集添加设备状态
    val addDeviceState by viewModel.addDeviceState.collectAsState()

    // 当前步骤
    var currentStep by remember { mutableStateOf(AddDeviceStep.Prepare) }

    // 自动导航：准备完成后进入扫描阶段
    LaunchedEffect(addDeviceState) {
        when (val state = addDeviceState) {
            is io.aeternum.ui.state.UiState.Success -> {
                when (currentStep) {
                    AddDeviceStep.Prepare -> {
                        // 准备完成，进入扫描阶段
                        currentStep = AddDeviceStep.Scan
                    }
                    AddDeviceStep.Scan -> {
                        // 扫描完成，进入验证阶段
                        currentStep = AddDeviceStep.Verify
                    }
                    AddDeviceStep.Verify -> {
                        // 验证完成，进入完成阶段
                        currentStep = AddDeviceStep.Complete
                    }
                    AddDeviceStep.Complete -> {
                        // 全部完成，返回设备列表
                        onDeviceAdded()
                    }
                }
            }
            is io.aeternum.ui.state.UiState.Error -> {
                // 保持当前步骤，显示错误
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            AddDeviceTopBar(
                onNavigateBack = onNavigateBack,
                currentStep = currentStep,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (currentStep) {
                AddDeviceStep.Prepare -> {
                    PrepareStepContent(
                        state = addDeviceState,
                        onStartAdd = {
                            viewModel.initiateAddDevice()
                        },
                    )
                }
                AddDeviceStep.Scan -> {
                    ScanStepContent(
                        state = addDeviceState,
                        onManualInput = {
                            // TODO: 实现手动输入验证码
                        },
                    )
                }
                AddDeviceStep.Verify -> {
                    VerifyStepContent(
                        state = addDeviceState,
                    )
                }
                AddDeviceStep.Complete -> {
                    CompleteStepContent(
                        onNavigateBack = onNavigateBack,
                    )
                }
            }
        }
    }
}

/**
 * 添加设备顶部栏
 *
 * @param onNavigateBack 返回回调
 * @param currentStep 当前步骤
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceTopBar(
    onNavigateBack: () -> Unit,
    currentStep: AddDeviceStep,
) {
    TopAppBar(
        title = {
            Text(
                text = when (currentStep) {
                    AddDeviceStep.Prepare -> "添加设备 - 准备"
                    AddDeviceStep.Scan -> "添加设备 - 扫描"
                    AddDeviceStep.Verify -> "添加设备 - 验证"
                    AddDeviceStep.Complete -> "添加设备 - 完成"
                },
                color = OnDeepSpaceBackground,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = OnDeepSpaceBackground,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * 准备步骤内容
 *
 * @param state 当前状态
 * @param onStartAdd 开始添加设备回调
 */
@Composable
private fun PrepareStepContent(
    state: io.aeternum.ui.state.UiState<String>,
    onStartAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 量子动画
        QuantumAnimation(
            modifier = Modifier.size(120.dp),
            type = QuantumAnimationType.Pulsing,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 标题
        Text(
            text = "添加新设备",
            style = MaterialTheme.typography.headlineMedium,
            color = OnDeepSpaceBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 描述
        Text(
            text = "通过扫描二维码，您可以安全地将新设备添加到您的 Aeternum 网络。",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariantColor,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 安全说明卡片
        SecurityInfoCard()

        Spacer(modifier = Modifier.height(48.dp))

        // 开始按钮
        when (state) {
            is io.aeternum.ui.state.UiState.Idle,
            is io.aeternum.ui.state.UiState.Error -> {
                ActionButton(
                    text = "开始添加设备",
                    onClick = onStartAdd,
                    type = io.aeternum.ui.components.ButtonType.Primary,
                    fullWidth = true,
                )
            }
            is io.aeternum.ui.state.UiState.Loading -> {
                ActionButton(
                    text = "准备中...",
                    onClick = {},
                    type = io.aeternum.ui.components.ButtonType.Primary,
                    isLoading = true,
                    enabled = false,
                    fullWidth = true,
                )
            }
            else -> {}
        }

        // 错误信息
        if (state is io.aeternum.ui.state.UiState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MachineStateColor.Revoked.color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 扫描步骤内容
 *
 * @param state 当前状态
 * @param onManualInput 手动输入回调
 */
@Composable
private fun ScanStepContent(
    state: io.aeternum.ui.state.UiState<String>,
    onManualInput: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // 进度指示
        StepProgressIndicator(
            currentStep = 1,
            totalSteps = 3,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 扫描图标和动画
        ScanningAnimation()

        Spacer(modifier = Modifier.height(32.dp))

        // 标题
        Text(
            text = "扫描二维码",
            style = MaterialTheme.typography.headlineMedium,
            color = OnDeepSpaceBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 描述
        Text(
            text = "在新设备上打开 Aeternum 并扫描此二维码以继续设备添加流程。",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariantColor,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 二维码占位符
        QRCodePlaceholder()

        Spacer(modifier = Modifier.height(32.dp))

        // 手动输入选项
        ActionButton(
            text = "手动输入验证码",
            onClick = onManualInput,
            type = io.aeternum.ui.components.ButtonType.Secondary,
            fullWidth = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 等待状态
        if (state is io.aeternum.ui.state.UiState.Loading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = QuantumBlue)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "等待新设备扫描...",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }
        }
    }
}

/**
 * 验证步骤内容
 *
 * @param state 当前状态
 */
@Composable
private fun VerifyStepContent(
    state: io.aeternum.ui.state.UiState<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // 进度指示
        StepProgressIndicator(
            currentStep = 2,
            totalSteps = 3,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 验证动画
        VerifyingAnimation()

        Spacer(modifier = Modifier.height(32.dp))

        // 标题
        Text(
            text = "验证设备",
            style = MaterialTheme.typography.headlineMedium,
            color = OnDeepSpaceBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 描述
        Text(
            text = "正在验证新设备的身份并建立加密通道。请确保两台设备都在附近。",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariantColor,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 验证进度
        when (state) {
            is io.aeternum.ui.state.UiState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = QuantumBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在验证设备身份...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariantColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "此过程可能需要几秒钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantColor,
                    )
                }
            }
            is io.aeternum.ui.state.UiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusWeak,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MachineStateColor.Revoked.color,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "验证失败",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnDeepSpaceBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MachineStateColor.Revoked.color,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {}
        }
    }
}

/**
 * 完成步骤内容
 *
 * @param onNavigateBack 返回回调
 */
@Composable
private fun CompleteStepContent(
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 成功动画
        SuccessAnimation()

        Spacer(modifier = Modifier.height(32.dp))

        // 标题
        Text(
            text = "设备添加成功",
            style = MaterialTheme.typography.headlineMedium,
            color = OnDeepSpaceBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 描述
        Text(
            text = "新设备已成功添加到您的 Aeternum 网络。您现在可以在设备列表中查看它。",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariantColor,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 完成按钮
        ActionButton(
            text = "返回设备列表",
            onClick = onNavigateBack,
            type = io.aeternum.ui.components.ButtonType.Primary,
            fullWidth = true,
        )
    }
}

/**
 * 安全信息卡片
 */
@Composable
private fun SecurityInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = QuantumBlue,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "安全保证",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnDeepSpaceBackground,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SecurityItem(
                icon = "🔒",
                text = "后量子加密：使用 Kyber-1024 保护传输",
            )
            SecurityItem(
                icon = "🔐",
                text = "双向验证：两台设备都需要身份确认",
            )
            SecurityItem(
                icon = "🛡️",
                text = "零知识证明：服务器无法获取密钥信息",
            )
        }
    }
}

/**
 * 安全项目
 */
@Composable
private fun SecurityItem(
    icon: String,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantColor,
        )
    }
}

/**
 * 步骤进度指示器
 *
 * @param currentStep 当前步骤
 * @param totalSteps 总步骤数
 */
@Composable
private fun StepProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isCompleted = index < currentStep
            val isCurrent = index == currentStep

            // 步骤圆圈
            Surface(
                color = when {
                    isCompleted -> QuantumGreen
                    isCurrent -> QuantumBlue
                    else -> SurfaceColor
                },
                shape = CircleShape,
                border = if (!isCompleted && !isCurrent) {
                    BorderStroke(1.dp, OnSurfaceVariantColor)
                } else null,
                modifier = Modifier.size(32.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) OnDeepSpaceBackground else OnSurfaceVariantColor,
                        )
                    }
                }
            }

            // 连接线
            if (index < totalSteps - 1) {
                val lineColor = if (index < currentStep) QuantumGreen else OnSurfaceVariantColor.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .drawBehind {
                            drawRect(lineColor)
                        },
                )
            }
        }
    }
}

/**
 * 扫描动画
 */
@Composable
private fun ScanningAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 外圈
        Surface(
            color = QuantumBlue.copy(alpha = 0.1f),
            shape = CircleShape,
            modifier = Modifier
                .size(100.dp)
                .rotate(rotation),
        ) {}

        // 中圈
        Surface(
            color = QuantumBlue.copy(alpha = 0.2f),
            shape = CircleShape,
            modifier = Modifier
                .size(70.dp)
                .rotate(-rotation),
        ) {}

        // 内圈
        Surface(
            color = QuantumBlue.copy(alpha = 0.3f),
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = QuantumBlue,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * 验证动画
 */
@Composable
private fun VerifyingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "verify")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = QuantumBlue.copy(alpha = 0.2f),
            shape = CircleShape,
            modifier = Modifier
                .size(100.dp * scale),
        ) {
            Box(
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = QuantumBlue,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

/**
 * 成功动画
 */
@Composable
private fun SuccessAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "success")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 脉冲圆圈
        Surface(
            color = QuantumGreen.copy(alpha = 0.3f),
            shape = CircleShape,
            modifier = Modifier
                .size(100.dp)
                .alpha(alpha),
        ) {}

        // 成功图标
        Surface(
            color = QuantumGreen,
            shape = CircleShape,
            modifier = Modifier.size(70.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

/**
 * 二维码占位符
 */
@Composable
private fun QRCodePlaceholder() {
    OutlinedCard(
        modifier = Modifier.size(200.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, QuantumBlue),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = QuantumBlue,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "二维码",
                    style = MaterialTheme.typography.titleMedium,
                    color = QuantumBlue,
                )
                Text(
                    text = "由 Rust Core 生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantColor,
                )
            }
        }
    }
}

/**
 * 设备添加步骤
 */
private enum class AddDeviceStep {
    /** 准备阶段 */
    Prepare,
    /** 扫描阶段 */
    Scan,
    /** 验证阶段 */
    Verify,
    /** 完成阶段 */
    Complete,
}
