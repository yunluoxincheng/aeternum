package io.aeternum.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aeternum.ui.theme.AeternumPreviewTheme
import io.aeternum.ui.theme.OnQuantumRed
import io.aeternum.ui.theme.QuantumBlue
import io.aeternum.ui.theme.QuantumRed
import io.aeternum.ui.theme.QuantumYellow

/**
 * Aeternum 安全文本字段组件
 *
 * 用于输入敏感信息（如助记词、密码），确保用户输入不被泄露。
 *
 * ## 设计理念
 * - **安全优先**: 默认隐藏输入内容，提供可见性切换
 * - **即时验证**: 实时显示输入验证状态
 * - **用户友好**: 清晰的错误提示和字符计数
 *
 * ## 安全特性
 * - **密码遮蔽**: 默认使用 PasswordVisualTransformation
 * - **防截屏**: 配合 MainActivity 设置 FLAG_SECURE
 * - **内存隔离**: 输入仅暂存于 UI 层，立即传递到 Rust Core
 *
 * ## 架构约束
 * - INVARIANT: UI 层仅收集用户输入，不存储明文
 * - 所有输入应立即通过 UniFFI 接口传递到 Rust Core 处理
 * - 不在日志中记录用户输入内容
 *
 * @param value 当前文本值
 * @param onValueChange 值变化回调
 * @param modifier 修饰符
 * @param label 标签
 * @param placeholder 占位符文本
 * @param isPassword 是否为密码字段
 * @param maxLines 最大行数
 * @param minLines 最小行数
 * @param maxLength 最大字符长度（null 表示无限制）
 * @param keyboardOptions 键盘选项
 * @param validationState 验证状态
 * @param errorMessage 错误消息
 * @param enabled 是否启用
 * @param readOnly 是否只读
 */
@Composable
fun SecureTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isPassword: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    maxLength: Int? = null,
    keyboardOptions: KeyboardOptions? = null,
    validationState: ValidationState = ValidationState.None,
    errorMessage: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = { newValue ->
                // 应用字符长度限制
                val filtered = if (maxLength != null && newValue.length > maxLength) {
                    newValue.take(maxLength)
                } else {
                    newValue
                }
                onValueChange(filtered)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> hasFocus = focusState.hasFocus },
            label = if (label != null) {
                { Text(label) }
            } else null,
            placeholder = if (placeholder != null) {
                { Text(placeholder) }
            } else null,
            leadingIcon = if (isPassword) {
                {
                    Icon(
                        imageVector = ImageVector.vectorResource(android.R.drawable.ic_lock_lock),
                        contentDescription = null,
                        tint = getValidationColor(validationState, hasFocus),
                    )
                }
            } else null,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 密码可见性切换按钮
                    if (isPassword) {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(
                                    if (isPasswordVisible) {
                                        android.R.drawable.ic_menu_view
                                    } else {
                                        android.R.drawable.ic_menu_close_clear_cancel
                                    }
                                ),
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    }
                    // 字符计数器
                    if (maxLength != null) {
                        Text(
                            text = "${value.length}/$maxLength",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (value.length > maxLength) {
                                QuantumRed
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            },
                        )
                    }
                }
            },
            visualTransformation = when {
                !isPassword -> VisualTransformation.None
                isPasswordVisible -> VisualTransformation.None
                else -> PasswordVisualTransformation()
            },
            keyboardOptions = keyboardOptions ?: KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                autoCorrect = false,
            ),
            singleLine = maxLines == 1 && minLines == 1,
            maxLines = maxLines,
            minLines = minLines,
            enabled = enabled,
            readOnly = readOnly,
            colors = getSecureTextFieldColors(validationState, hasFocus),
            shape = io.aeternum.ui.theme.InputFieldShapes.Secure,
        )

        // 错误消息
        if (errorMessage != null && validationState != ValidationState.None) {
            Text(
                text = errorMessage,
                color = QuantumRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

/**
 * 验证状态
 */
sealed class ValidationState {
    /** 无验证状态 */
    data object None : ValidationState()

    /** 验证通过 */
    data object Valid : ValidationState()

    /** 验证失败 */
    data object Invalid : ValidationState()

    /** 警告状态 */
    data object Warning : ValidationState()
}

/**
 * 根据验证状态获取颜色
 */
private fun getValidationColor(
    validationState: ValidationState,
    hasFocus: Boolean,
): Color {
    return when (validationState) {
        is ValidationState.Invalid -> QuantumRed
        is ValidationState.Warning -> QuantumYellow
        is ValidationState.Valid -> QuantumBlue
        is ValidationState.None -> if (hasFocus) QuantumBlue else Color.Gray
    }
}

/**
 * 获取安全文本字段的颜色配置
 */
@Composable
private fun getSecureTextFieldColors(
    validationState: ValidationState,
    hasFocus: Boolean,
): TextFieldColors {
    val borderColor = getValidationColor(validationState, hasFocus)
    val errorColor = QuantumRed

    return TextFieldDefaults.colors(
        focusedIndicatorColor = borderColor,
        unfocusedIndicatorColor = borderColor.copy(alpha = 0.5f),
        errorIndicatorColor = errorColor,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        errorContainerColor = errorColor.copy(alpha = 0.1f),
        cursorColor = borderColor,
        errorCursorColor = errorColor,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        errorTextColor = errorColor,
        focusedLabelColor = borderColor,
        unfocusedLabelColor = borderColor.copy(alpha = 0.7f),
        errorLabelColor = errorColor,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        errorPlaceholderColor = errorColor.copy(alpha = 0.5f),
        focusedLeadingIconColor = borderColor,
        unfocusedLeadingIconColor = borderColor.copy(alpha = 0.7f),
        errorLeadingIconColor = errorColor,
        focusedTrailingIconColor = borderColor,
        unfocusedTrailingIconColor = borderColor.copy(alpha = 0.7f),
        errorTrailingIconColor = errorColor,
        selectionColors = TextSelectionColors(
            handleColor = borderColor,
            backgroundColor = borderColor.copy(alpha = 0.4f),
        ),
    )
}

// ============================================================================
// 预览
// ============================================================================

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SecureTextFieldPreview_Password() {
    AeternumPreviewTheme {
        SecureTextField(
            value = "",
            onValueChange = {},
            label = "主密码",
            placeholder = "输入主密码",
            isPassword = true,
            maxLength = 64,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SecureTextFieldPreview_Password_Filled() {
    AeternumPreviewTheme {
        SecureTextField(
            value = "MySecurePassword123!",
            onValueChange = {},
            label = "主密码",
            placeholder = "输入主密码",
            isPassword = true,
            maxLength = 64,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SecureTextFieldPreview_Mnemonic() {
    AeternumPreviewTheme {
        SecureTextField(
            value = "",
            onValueChange = {},
            label = "助记词（24词）",
            placeholder = "输入24位助记词，用空格分隔",
            isPassword = false,
            maxLines = 3,
            minLines = 2,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SecureTextFieldPreview_Validation_Invalid() {
    AeternumPreviewTheme {
        SecureTextField(
            value = "short",
            onValueChange = {},
            label = "主密码",
            placeholder = "至少需要12个字符",
            isPassword = true,
            validationState = ValidationState.Invalid,
            errorMessage = "密码长度不足，至少需要12个字符",
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SecureTextFieldPreview_Validation_Valid() {
    AeternumPreviewTheme {
        SecureTextField(
            value = "SecurePassword123!",
            onValueChange = {},
            label = "主密码",
            placeholder = "输入主密码",
            isPassword = true,
            validationState = ValidationState.Valid,
            errorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SecureTextFieldPreview_Validation_Warning() {
    AeternumPreviewTheme {
        SecureTextField(
            value = "password123",
            onValueChange = {},
            label = "主密码",
            placeholder = "输入主密码",
            isPassword = true,
            validationState = ValidationState.Warning,
            errorMessage = "建议使用更复杂的密码组合",
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SecureTextFieldPreview_Disabled() {
    AeternumPreviewTheme {
        SecureTextField(
            value = "LockedPassword123",
            onValueChange = {},
            label = "主密码",
            placeholder = "输入主密码",
            isPassword = true,
            enabled = false,
        )
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SecureTextFieldPreview_CharacterCounter() {
    AeternumPreviewTheme {
        SecureTextField(
            value = "12345678901234",
            onValueChange = {},
            label = "验证码",
            placeholder = "输入16位验证码",
            isPassword = false,
            maxLength = 16,
        )
    }
}

@Preview(showBackground = true, widthDp = 350)
@Composable
private fun SecureTextFieldPreview_AllStates() {
    AeternumPreviewTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SecureTextField(
                value = "",
                onValueChange = {},
                label = "默认状态",
                isPassword = true,
            )
            Spacer(modifier = Modifier.size(8.dp))
            SecureTextField(
                value = "password123",
                onValueChange = {},
                label = "有效状态",
                isPassword = true,
                validationState = ValidationState.Valid,
            )
            Spacer(modifier = Modifier.size(8.dp))
            SecureTextField(
                value = "short",
                onValueChange = {},
                label = "无效状态",
                isPassword = true,
                validationState = ValidationState.Invalid,
                errorMessage = "密码太短",
            )
            Spacer(modifier = Modifier.size(8.dp))
            SecureTextField(
                value = "weak",
                onValueChange = {},
                label = "警告状态",
                isPassword = true,
                validationState = ValidationState.Warning,
                errorMessage = "建议使用更强密码",
            )
        }
    }
}
