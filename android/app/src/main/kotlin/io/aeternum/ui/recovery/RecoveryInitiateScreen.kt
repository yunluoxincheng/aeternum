package io.aeternum.ui.recovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aeternum.ui.components.SecureScreen
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.OnDeepSpaceBackground
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.OnSurfaceVariantColor
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumBlueContainer
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.SurfaceColor
import io.aeternum.ui.theme.SurfaceVariantColor
import kotlinx.coroutines.delay

/**
 * 恢复发起屏幕
 *
 * 用于输入 24 位助记词发起恢复流程
 *
 * INVARIANT: UI 层 - 仅处理输入和显示，助记词验证通过 Rust Core
 * INVARIANT: 助记词屏幕必须启用 FLAG_SECURE，防止截屏
 * INVARIANT: 离开屏幕时清除内存中的助记词
 * 参考：design.md §恢复流程设计
 *
 * @param onBack 返回回调
 * @param onRecoveryInitiated 恢复发起成功回调
 * @param viewModel AeternumViewModel
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecoveryInitiateScreen(
    onBack: () -> Unit,
    onRecoveryInitiated: (recoveryId: String) -> Unit,
    viewModel: io.aeternum.ui.viewmodel.AeternumViewModel,
) {
    // INVARIANT: 使用 SecureScreen 防止截屏/录屏
    SecureScreen {
        RecoveryInitiateContent(
            onBack = onBack,
            onRecoveryInitiated = onRecoveryInitiated,
            viewModel = viewModel,
        )
    }
}

/**
 * 恢复发起内容
 *
 * 分离出来以便被 SecureScreen 包裹
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecoveryInitiateContent(
    onBack: () -> Unit,
    onRecoveryInitiated: (recoveryId: String) -> Unit,
    viewModel: io.aeternum.ui.viewmodel.AeternumViewModel,
) {
    // 助记词输入状态（24 个词）
    val mnemonicWords = remember { mutableStateListOf<String>() }
    // 初始化 24 个空字符串
    LaunchedEffect(Unit) {
        if (mnemonicWords.isEmpty()) {
            repeat(24) {
                mnemonicWords.add("")
            }
        }
    }

    // INVARIANT: 离开屏幕时清除内存中的助记词
    DisposableEffect(Unit) {
        onDispose {
            // 安全擦除：覆盖所有助记词为空字符串
            mnemonicWords.indices.forEach { mnemonicWords[it] = "" }
            mnemonicWords.clear()
        }
    }

    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // 自动聚焦到第一个输入框
    val focusRequesters = remember { List(24) { FocusRequester() } }

    // 提交恢复请求
    fun submitRecovery() {
        // 检查是否所有 24 个框都已填写
        if (mnemonicWords.any { it.isBlank() }) {
            errorMessage = "请填写全部 24 个助记词"
            showError = true
            return
        }

        isSubmitting = true
        showError = false

        // 拼接助记词
        val mnemonicPhrase = mnemonicWords.joinToString(" ")

        // 调用 ViewModel 发起恢复
        viewModel.initiateRecovery(mnemonicPhrase)

        // INVARIANT: 提交后立即清除内存中的助记词
        mnemonicWords.indices.forEach { mnemonicWords[it] = "" }

        // TODO: 实际实现中应该监听恢复结果
        // 这里简化为直接导航到否决通知屏幕
        isSubmitting = false
        onRecoveryInitiated("recovery_${System.currentTimeMillis()}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("恢复账户") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标题和说明
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "输入助记词",
                style = MaterialTheme.typography.headlineMedium,
                color = OnDeepSpaceBackground,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "请按顺序输入您的 24 位助记词以发起恢复",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 助记词输入网格（24 个输入框）
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 4,
            ) {
                mnemonicWords.forEachIndexed { index, word ->
                    MnemonicWordInput(
                        index = index,
                        value = word,
                        onValueChange = { newValue ->
                            if (index < mnemonicWords.size) {
                                mnemonicWords[index] = newValue.trim()
                            }
                            // 自动移动焦点到下一个输入框
                            if (newValue.isNotEmpty() && index < 23) {
                                focusRequesters[index + 1].requestFocus()
                            }
                            showError = false
                        },
                        focusRequester = focusRequesters[index],
                        showError = showError && word.isBlank(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 错误提示
            AnimatedVisibility(
                visible = showError,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(durationMillis = 300)),
            ) {
                ErrorBanner(message = errorMessage)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 48h 否决窗口警告
            VetoWarningBanner()

            Spacer(modifier = Modifier.height(24.dp))

            // 提交按钮
            val alpha by animateFloatAsState(
                targetValue = if (isSubmitting) 0.7f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "button_alpha",
            )

            Button(
                onClick = { submitRecovery() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .alpha(alpha),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuantumBlue,
                    contentColor = Color.White,
                    disabledContainerColor = QuantumBlueContainer,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isSubmitting) {
                    Text(
                        text = "处理中...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = "发起恢复",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 安全提示
            SecurityHintBanner()
        }
    }

    // 自动聚焦到第一个输入框
    LaunchedEffect(Unit) {
        delay(100)
        if (focusRequesters.isNotEmpty()) {
            focusRequesters[0].requestFocus()
        }
    }
}

/**
 * 助记词输入框
 *
 * @param index 索引（从 0 开始）
 * @param value 当前值
 * @param onValueChange 值变化回调
 * @param focusRequester 焦点控制器
 * @param showError 是否显示错误
 */
@Composable
private fun MnemonicWordInput(
    index: Int,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    showError: Boolean,
) {
    val displayIndex = index + 1

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .size(width = 100.dp, height = 56.dp)
            .focusRequester(focusRequester),
        label = {
            Text(
                text = "$displayIndex",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariantColor,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            autoCorrect = false,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceVariantColor,
            unfocusedContainerColor = SurfaceVariantColor,
            focusedIndicatorColor = QuantumBlue,
            unfocusedIndicatorColor = SurfaceColor,
            errorIndicatorColor = QuantumRed,
            cursorColor = QuantumBlue,
            focusedLabelColor = QuantumBlue,
            unfocusedLabelColor = OnSurfaceVariantColor,
        ),
        isError = showError,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = OnDeepSpaceBackground,
            textAlign = TextAlign.Center,
        ),
    )
}

/**
 * 错误提示横幅
 *
 * @param message 错误消息
 */
@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = QuantumRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = 1.dp,
                color = QuantumRed,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = QuantumRed,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = OnQuantumRed,
        )
    }
}

/**
 * 否决窗口警告横幅
 *
 * 警告用户 48 小时否决窗口机制
 */
@Composable
private fun VetoWarningBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = QuantumBlue.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = 1.dp,
                color = QuantumBlue,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = QuantumBlue,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "48 小时否决窗口",
                style = MaterialTheme.typography.titleSmall,
                color = QuantumBlue,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "发起恢复后，系统将进入 48 小时冻结期。在此期间，任何已连接的设备都可以否决此次恢复操作。这是为了防止助记词被盗后的未经授权恢复。",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantColor,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        )
    }
}

/**
 * 安全提示横幅
 */
@Composable
private fun SecurityHintBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceVariantColor,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = "安全提示",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariantColor,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "• 请确保您输入的是正确的 24 位助记词\n" +
                    "• 恢复过程中请保持网络连接\n" +
                    "• 恢复成功后将自动执行密钥轮换\n" +
                    "• 旧设备将被撤销访问权限",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantColor,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        )
    }
}
