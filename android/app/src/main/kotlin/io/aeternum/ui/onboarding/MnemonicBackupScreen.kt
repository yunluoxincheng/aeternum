package io.aeternum.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aeternum.ui.components.SecureScreen
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.DeepSpaceBackground
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Aeternum 助记词备份屏幕
 *
 * INVARIANT: UI 层 - 仅负责展示和用户交互
 * 此屏幕用于新用户首次创建助记词时安全地展示和确认助记词
 *
 * 安全原则：
 * - 助记词由调用方传入（从 Rust Core 获取）
 * - UI 层仅负责展示，不持久化存储
 * - 需要用户确认已安全保存
 * - 防止用户未仔细查看就跳过
 *
 * 设计理念：
 * - 清晰展示：24 个助记词以网格形式展示
 * - 安全警告：红色高亮的安全提示
 * - 防误操作：10 秒倒计时确认
 * - 可控性：用户可选择显示/隐藏助记词
 *
 * @param mnemonicWords 24 个助记词列表（从 Rust Core 获取）
 * @param onBack 点击返回按钮的回调
 * @param onConfirm 点击确认按钮的回调（用户已安全保存助记词）
 */
@Composable
fun MnemonicBackupScreen(
    mnemonicWords: List<String>,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    // 验证助记词数量
    require(mnemonicWords.size == 24) {
        "助记词必须为 24 个，当前为 ${mnemonicWords.size} 个"
    }

    // INVARIANT: 助记词屏幕必须启用 FLAG_SECURE，防止截屏
    SecureScreen {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DeepSpaceBackground
        ) {
            MnemonicBackupContent(
                mnemonicWords = mnemonicWords,
                onBack = onBack,
                onConfirm = onConfirm
            )
        }
    }
}

/**
 * 助记词备份屏幕内容
 *
 * 包含：
 * - 顶部导航栏（返回按钮 + 标题）
 * - 说明文本
 * - 助记词网格（可显示/隐藏）
 * - 操作按钮（复制、显示/隐藏）
 * - 安全警告横幅
 * - 确认按钮（带倒计时）
 *
 * @param mnemonicWords 24 个助记词列表
 * @param onBack 点击返回按钮的回调
 * @param onConfirm 点击确认按钮的回调
 */
@Composable
private fun MnemonicBackupContent(
    mnemonicWords: List<String>,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部导航栏
        MnemonicBackupTopBar(
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 说明文本
        MnemonicBackupDescription()

        Spacer(modifier = Modifier.height(24.dp))

        // 助记词展示区域
        val (isVisible, setIsVisible) = rememberSaveable { mutableStateOf(false) }
        MnemonicGridSection(
            mnemonicWords = mnemonicWords,
            isVisible = isVisible,
            onToggleVisibility = { setIsVisible(!isVisible) }
        )

        Spacer(modifier = Modifier.weight(1f))

        // 安全警告
        SecurityWarningBanner()

        Spacer(modifier = Modifier.height(16.dp))

        // 确认按钮（带倒计时）
        ConfirmButton(
            onConfirm = onConfirm
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 顶部导航栏
 *
 * 包含返回按钮和标题"创建备份"
 *
 * @param onBack 点击返回按钮的回调
 */
@Composable
private fun MnemonicBackupTopBar(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 标题
        Text(
            text = "创建备份",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * 说明文本
 *
 * 提示用户安全保存助记词的重要性
 */
@Composable
private fun MnemonicBackupDescription() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "请安全保存您的助记词",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "助记词是恢复您账户的唯一方式，\n请务必将其保存在安全的地方",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 助记词网格区域
 *
 * 包含：
 * - 助记词网格（24 个词，3 列布局）
 * - 显示/隐藏切换按钮
 * - 安全警告提示（禁止复制）
 *
 * INVARIANT: 助记词默认隐藏，用户主动选择显示
 * INVARIANT: 禁止复制助记词到剪贴板，防止泄露
 *
 * @param mnemonicWords 24 个助记词列表
 * @param isVisible 是否显示助记词明文
 * @param onToggleVisibility 切换显示/隐藏的回调
 */
@Composable
private fun MnemonicGridSection(
    mnemonicWords: List<String>,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 显示/隐藏按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isVisible) "隐藏" else "显示",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = onToggleVisibility
                    ) {
                        Icon(
                            imageVector = if (isVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (isVisible) "隐藏助记词" else "显示助记词",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 安全警告：禁止复制提示
                // INVARIANT: 不提供复制功能，防止助记词泄露到系统剪贴板
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "安全警告",
                        tint = QuantumRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "请手抄备份",
                        style = MaterialTheme.typography.labelMedium,
                        color = QuantumRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 助记词网格
            if (isVisible) {
                MnemonicWordsGrid(
                    mnemonicWords = mnemonicWords
                )
            } else {
                // 隐藏状态
                HiddenMnemonicPlaceholder()
            }
        }
    }
}

/**
 * 助记词网格
 *
 * 24 个助记词以 3 列网格形式展示
 * 每个助记词显示序号和单词
 *
 * @param mnemonicWords 24 个助记词列表
 */
@Composable
private fun MnemonicWordsGrid(
    mnemonicWords: List<String>,
) {
    // 入场动画
    val gridAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        gridAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(gridAlpha.value),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 将 24 个助记词分成 8 行，每行 3 个
        mnemonicWords.chunked(3).forEach { rowWords ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowWords.forEachIndexed { colIndex, word ->
                    val globalIndex = mnemonicWords.indexOf(word)
                    MnemonicWordCard(
                        index = globalIndex + 1,
                        word = word,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 如果这一行不足 3 个，用空白填充
                if (rowWords.size < 3) {
                    repeat(3 - rowWords.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 单个助记词卡片
 *
 * 显示序号和助记词
 *
 * @param index 助记词序号（1-24）
 * @param word 助记词文本
 * @param modifier 修饰符
 */
@Composable
private fun MnemonicWordCard(
    index: Int,
    word: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(48.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号
            Text(
                text = "$index.",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(24.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 助记词
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * 隐藏状态的占位符
 *
 * 当助记词隐藏时显示的占位内容
 */
@Composable
private fun HiddenMnemonicPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "点击上方【显示】按钮查看助记词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 安全警告横幅
 *
 * 红色高亮的安全提示，提醒用户注意助记词安全
 */
@Composable
private fun SecurityWarningBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = QuantumRed.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = QuantumRed.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 警告图标
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = QuantumRed,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 警告文本
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "重要安全警告",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = QuantumRed
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "• 请勿将助记词截图、拍照或发送给任何人\n" +
                           "• 请勿将助记词存储在云端或笔记应用中\n" +
                           "• 建议将其抄写在纸上，存放在安全的地方\n" +
                           "• 丢失助记词将无法恢复您的账户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}

/**
 * 确认按钮
 *
 * 带有 10 秒倒计时的确认按钮
 * 防止用户未仔细查看就跳过
 *
 * INVARIANT: 用户必须等待 10 秒才能确认，确保他们有时间阅读助记词
 *
 * @param onConfirm 点击确认按钮的回调
 */
@Composable
private fun ConfirmButton(
    onConfirm: () -> Unit,
) {
    var countdown by remember { mutableIntStateOf(10) }
    var canConfirm by remember { mutableStateOf(false) }

    // 倒计时逻辑
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
        canConfirm = true
    }

    Button(
        onClick = onConfirm,
        enabled = canConfirm,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = QuantumBlue,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (canConfirm) {
            Text(
                text = "我已经安全保存了助记词",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
        } else {
            Text(
                text = "请仔细查看助记词 ($countdown 秒)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// 预览
// ============================================================================

/**
 * 助记词备份屏幕预览
 */
@Preview(
    showBackground = true,
    name = "助记词备份预览（隐藏状态）"
)
@Composable
fun MnemonicBackupScreenHiddenPreview() {
    AeternumPreviewTheme {
        MnemonicBackupScreen(
            mnemonicWords = generateSampleMnemonic(),
            onBack = {},
            onConfirm = {}
        )
    }
}

/**
 * 助记词备份屏幕预览（显示状态）- 无法完全模拟，因为需要用户交互
 */
@Preview(
    showBackground = true,
    name = "助记词网格预览"
)
@Composable
fun MnemonicWordsGridPreview() {
    AeternumPreviewTheme {
        Surface(
            color = DeepSpaceBackground
        ) {
            MnemonicWordsGrid(
                mnemonicWords = generateSampleMnemonic()
            )
        }
    }
}

/**
 * 安全警告横幅预览
 */
@Preview(
    showBackground = true,
    name = "安全警告预览"
)
@Composable
fun SecurityWarningBannerPreview() {
    AeternumPreviewTheme {
        Surface(
            color = DeepSpaceBackground
        ) {
            SecurityWarningBanner()
        }
    }
}

/**
 * 生成示例助记词（仅用于预览）
 *
 * INVARIANT: 这仅是示例数据，实际使用时必须从 Rust Core 获取真实助记词
 */
private fun generateSampleMnemonic(): List<String> = listOf(
    "abandon", "ability", "able", "about", "above", "absent",
    "absorb", "abstract", "absurd", "abuse", "access", "accident",
    "account", "accuse", "achieve", "acid", "acoustic", "acquire",
    "across", "act", "action", "actor", "actress", "actual"
)
