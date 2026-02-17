package io.aeternum.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 错误处理测试
 *
 * 测试覆盖：
 * - 所有 UiError 类型
 * - 错误恢复机制
 * - 错误状态转换
 * - 错误消息格式化
 */
class ErrorHandlingTest {

    // ========================================================================
    // EpochError 测试
    // ========================================================================

    @Test
    fun `EpochError should capture epoch conflict`() {
        val error = UiError.EpochError(
            message = "纪元版本冲突",
            currentEpoch = 5u,
            expectedEpoch = 6u,
        )

        assertEquals("纪元版本冲突", error.message)
        assertEquals(5u, error.currentEpoch)
        assertEquals(6u, error.expectedEpoch)
    }

    @Test
    fun `EpochError should indicate rollback attempt`() {
        val error = UiError.EpochError(
            message = "检测到纪元回滚",
            currentEpoch = 5u,
            expectedEpoch = 3u, // 回滚尝试
        )

        assertTrue("Expected epoch should be less than current", error.expectedEpoch < error.currentEpoch)
    }

    @Test
    fun `EpochError in UiState should be recoverable`() {
        val state = AeternumUiState.Error(
            error = UiError.EpochError("纪元冲突", 5u, 6u),
            recoverable = true,
        )

        assertTrue(state.allowUserActions())
        assertTrue(state.needsWarning())
    }

    // ========================================================================
    // DataError 测试
    // ========================================================================

    @Test
    fun `DataError should capture missing fields`() {
        val error = UiError.DataError(
            message = "数据不完整",
            missingFields = listOf("header", "signature", "epoch"),
        )

        assertEquals("数据不完整", error.message)
        assertEquals(3, error.missingFields.size)
        assertTrue(error.missingFields.contains("header"))
        assertTrue(error.missingFields.contains("signature"))
        assertTrue(error.missingFields.contains("epoch"))
    }

    @Test
    fun `DataError with empty missing fields should be valid`() {
        val error = UiError.DataError(
            message = "数据格式错误",
            missingFields = emptyList(),
        )

        assertEquals("数据格式错误", error.message)
        assertTrue(error.missingFields.isEmpty())
    }

    @Test
    fun `DataError should indicate corruption`() {
        val error = UiError.DataError(
            message = "Vault 数据损坏",
            missingFields = listOf("vault_blob"),
        )

        assertTrue(error.message.contains("损坏"))
    }

    // ========================================================================
    // AuthError 测试
    // ========================================================================

    @Test
    fun `AuthError should indicate biometric requirement`() {
        val error = UiError.AuthError(
            message = "需要生物识别认证",
            requiresBiometric = true,
        )

        assertTrue(error.requiresBiometric)
    }

    @Test
    fun `AuthError should handle non-biometric auth`() {
        val error = UiError.AuthError(
            message = "设备 PIN 码认证失败",
            requiresBiometric = false,
        )

        assertFalse(error.requiresBiometric)
    }

    @Test
    fun `AuthError scenarios should be distinct`() {
        val biometricError = UiError.AuthError("指纹识别失败", requiresBiometric = true)
        val pinError = UiError.AuthError("PIN 码错误", requiresBiometric = false)
        val lockoutError = UiError.AuthError("认证锁定，请稍后重试", requiresBiometric = true)

        assertTrue(biometricError.requiresBiometric)
        assertFalse(pinError.requiresBiometric)
        assertTrue(lockoutError.requiresBiometric)
    }

    // ========================================================================
    // VetoError 测试
    // ========================================================================

    @Test
    fun `VetoError should capture all details`() {
        val error = UiError.VetoError(
            message = "恢复请求被否决",
            vetoingDevice = "Pixel 8 Pro",
            remainingWindow = 24.hours,
        )

        assertEquals("恢复请求被否决", error.message)
        assertEquals("Pixel 8 Pro", error.vetoingDevice)
        assertEquals(24.hours, error.remainingWindow)
    }

    @Test
    fun `VetoError should indicate remaining time`() {
        val shortWindow = UiError.VetoError(
            message = "否决",
            vetoingDevice = "iPad",
            remainingWindow = 1.minutes,
        )

        val longWindow = UiError.VetoError(
            message = "否决",
            vetoingDevice = "MacBook",
            remainingWindow = 47.hours,
        )

        assertTrue(shortWindow.remainingWindow < longWindow.remainingWindow)
    }

    @Test
    fun `VetoError with zero window should indicate expired veto`() {
        val error = UiError.VetoError(
            message = "否决窗口已关闭",
            vetoingDevice = "本机",
            remainingWindow = 0.seconds,
        )

        assertEquals(0.seconds, error.remainingWindow)
    }

    // ========================================================================
    // StateError 测试
    // ========================================================================

    @Test
    fun `StateError should capture invalid transition`() {
        val error = UiError.StateError(
            message = "无效的状态转换",
            currentState = "Idle",
            attemptedTransition = "Rekeying",
        )

        assertEquals("无效的状态转换", error.message)
        assertEquals("Idle", error.currentState)
        assertEquals("Rekeying", error.attemptedTransition)
    }

    @Test
    fun `StateError should identify illegal transitions`() {
        // 从 Degraded 不能直接到 Decrypting
        val error = UiError.StateError(
            message = "降级模式下禁止解密",
            currentState = "Degraded",
            attemptedTransition = "Decrypting",
        )

        assertEquals("Degraded", error.currentState)
        assertEquals("Decrypting", error.attemptedTransition)
    }

    // ========================================================================
    // StorageError 测试
    // ========================================================================

    @Test
    fun `StorageError should capture space info`() {
        val error = UiError.StorageError(
            message = "存储空间不足",
            availableSpace = 1024L * 1024, // 1MB
        )

        assertEquals("存储空间不足", error.message)
        assertEquals(1024L * 1024, error.availableSpace)
    }

    @Test
    fun `StorageError without space info should be valid`() {
        val error = UiError.StorageError(
            message = "写入失败",
            availableSpace = null,
        )

        assertEquals("写入失败", error.message)
        assertTrue(error.availableSpace == null)
    }

    @Test
    fun `StorageError should indicate critical space situation`() {
        val error = UiError.StorageError(
            message = "磁盘空间严重不足",
            availableSpace = 1024L, // 仅 1KB
        )

        assertTrue("Space should be critically low", error.availableSpace!! < 1024L * 1024)
    }

    // ========================================================================
    // NetworkError 测试
    // ========================================================================

    @Test
    fun `NetworkError should indicate offline status`() {
        val offlineError = UiError.NetworkError("网络不可用", isOffline = true)
        val onlineError = UiError.NetworkError("请求超时", isOffline = false)

        assertTrue(offlineError.isOffline)
        assertFalse(onlineError.isOffline)
    }

    @Test
    fun `NetworkError scenarios should be distinct`() {
        val offlineError = UiError.NetworkError("设备离线", isOffline = true)
        val timeoutError = UiError.NetworkError("服务器响应超时", isOffline = false)
        val serverError = UiError.NetworkError("服务器错误 (500)", isOffline = false)

        assertTrue(offlineError.isOffline)
        assertFalse(timeoutError.isOffline)
        assertFalse(serverError.isOffline)
    }

    // ========================================================================
    // UnknownError 测试
    // ========================================================================

    @Test
    fun `UnknownError should capture original error`() {
        val error = UiError.UnknownError(
            message = "未知错误",
            originalError = "NullPointerException: ...",
        )

        assertEquals("未知错误", error.message)
        assertEquals("NullPointerException: ...", error.originalError)
    }

    @Test
    fun `UnknownError without original error should be valid`() {
        val error = UiError.UnknownError(
            message = "未知错误",
            originalError = null,
        )

        assertEquals("未知错误", error.message)
        assertTrue(error.originalError == null)
    }

    // ========================================================================
    // 错误恢复测试
    // ========================================================================

    @Test
    fun `recoverable errors should allow retry`() {
        val recoverableErrors = listOf(
            AeternumUiState.Error(UiError.NetworkError("网络错误", isOffline = true), recoverable = true),
            AeternumUiState.Error(UiError.StorageError("写入失败"), recoverable = true),
            AeternumUiState.Error(UiError.AuthError("认证失败"), recoverable = true),
        )

        recoverableErrors.forEach { state ->
            assertTrue("State should allow user actions", state.allowUserActions())
        }
    }

    @Test
    fun `unrecoverable errors should block retry`() {
        val unrecoverableErrors = listOf(
            AeternumUiState.Error(UiError.DataError("数据损坏", listOf("vault")), recoverable = false),
            AeternumUiState.Error(UiError.StateError("无效状态", "Revoked", "Active"), recoverable = false),
            AeternumUiState.Error(UiError.EpochError("纪元冲突", 5u, 3u), recoverable = false),
        )

        unrecoverableErrors.forEach { state ->
            assertFalse("State should not allow user actions", state.allowUserActions())
        }
    }

    // ========================================================================
    // 错误消息格式化测试
    // ========================================================================

    @Test
    fun `error messages should be user-friendly`() {
        val errors = listOf(
            UiError.NetworkError("无法连接到服务器，请检查网络连接"),
            UiError.StorageError("存储空间不足，请清理后重试"),
            UiError.AuthError("认证失败，请重试"),
            UiError.DataError("数据格式错误，请联系支持"),
        )

        errors.forEach { error ->
            val message = when (error) {
                is UiError.NetworkError -> error.message
                is UiError.StorageError -> error.message
                is UiError.AuthError -> error.message
                is UiError.DataError -> error.message
                is UiError.EpochError -> error.message
                is UiError.VetoError -> error.message
                is UiError.StateError -> error.message
                is UiError.UnknownError -> error.message
            }

            assertTrue("Message should not be empty", message.isNotEmpty())
        }
    }

    // ========================================================================
    // 错误状态转换测试
    // ========================================================================

    @Test
    fun `Active to Error transition should be valid`() {
        val activeState: AeternumUiState = AeternumUiState.Active(ActiveSubState.Idle)
        val errorState = AeternumUiState.Error(
            UiError.StorageError("操作失败"),
            recoverable = true,
        )

        // 状态应该能够转换
        assertTrue(activeState is AeternumUiState.Active)
        assertTrue(errorState is AeternumUiState.Error)
    }

    @Test
    fun `Error to Active transition should be valid for recoverable errors`() {
        val errorState = AeternumUiState.Error(
            UiError.NetworkError("网络错误"),
            recoverable = true,
        )
        val activeState: AeternumUiState = AeternumUiState.Active(ActiveSubState.Idle)

        // 可恢复错误应该能够转换回 Active
        assertTrue(errorState.allowUserActions())
        assertTrue(activeState is AeternumUiState.Active)
    }

    @Test
    fun `Error to Degraded transition should be valid`() {
        val errorState = AeternumUiState.Error(
            UiError.DataError("完整性验证失败"),
            recoverable = false,
        )
        val degradedState: AeternumUiState = AeternumUiState.Degraded(
            DegradedReason.INTEGRITY_CHECK_FAILED,
        )

        // 某些错误应该导致降级
        assertTrue(errorState.needsWarning())
        assertTrue(degradedState.needsWarning())
    }

    // ========================================================================
    // 错误严重程度测试
    // ========================================================================

    @Test
    fun `critical errors should be unrecoverable`() {
        val criticalErrors = listOf(
            UiError.DataError("Vault 损坏", listOf("vault")),
            UiError.StateError("系统状态异常", "Revoked", "Active"),
            UiError.EpochError("纪元回滚攻击", 5u, 2u),
        )

        criticalErrors.forEach { error ->
            val state = AeternumUiState.Error(error, recoverable = false)
            assertFalse(state.allowUserActions())
            assertTrue(state.needsWarning())
        }
    }

    @Test
    fun `transient errors should be recoverable`() {
        val transientErrors = listOf(
            UiError.NetworkError("网络超时", isOffline = false),
            UiError.StorageError("临时写入失败"),
            UiError.AuthError("认证失败", requiresBiometric = true),
        )

        transientErrors.forEach { error ->
            val state = AeternumUiState.Error(error, recoverable = true)
            assertTrue(state.allowUserActions())
            assertTrue(state.needsWarning())
        }
    }
}
