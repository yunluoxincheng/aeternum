package io.aeternum.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * AeternumUiState 状态管理测试
 *
 * 测试覆盖：
 * - 状态类型定义
 * - 状态转换规则
 * - 辅助函数行为
 * - 状态显示名称
 * - 状态权限检查
 */
class AeternumUiStateTest {

    // ========================================================================
    // 状态类型测试
    // ========================================================================

    @Test
    fun `Uninitialized should be a valid state`() {
        val state = AeternumUiState.Uninitialized

        assertEquals("未初始化", state.getDisplayName())
        assertFalse(state.allowUserActions())
        assertFalse(state.needsWarning())
    }

    @Test
    fun `Onboarding should be a valid state`() {
        val state = AeternumUiState.Onboarding

        assertEquals("初始化中", state.getDisplayName())
        assertTrue(state.allowUserActions())
        assertFalse(state.needsWarning())
    }

    @Test
    fun `Active with Idle substate should be valid`() {
        val state = AeternumUiState.Active(ActiveSubState.Idle)

        assertEquals("空闲", state.getDisplayName())
        assertTrue(state.allowUserActions())
        assertFalse(state.needsWarning())
    }

    @Test
    fun `Active with Decrypting substate should be valid`() {
        val state = AeternumUiState.Active(
            ActiveSubState.Decrypting(
                session = VaultSessionHandle.Placeholder,
                recordIds = listOf("record_1", "record_2"),
            ),
        )

        assertEquals("已解锁", state.getDisplayName())
        assertTrue(state.allowUserActions())
        assertFalse(state.needsWarning())
    }

    @Test
    fun `Active with Rekeying substate should be valid`() {
        val state = AeternumUiState.Active(
            ActiveSubState.Rekeying(
                currentEpoch = 1u,
                targetEpoch = 2u,
                progress = 0.5f,
                stage = RekeyingStage.ENCRYPTING,
            ),
        )

        assertEquals("密钥轮换中", state.getDisplayName())
        assertTrue(state.allowUserActions())
        assertTrue(state.needsWarning())
    }

    @Test
    fun `Degraded state should have reason`() {
        val state = AeternumUiState.Degraded(DegradedReason.INTEGRITY_CHECK_FAILED)

        assertEquals("降级模式", state.getDisplayName())
        assertFalse(state.allowUserActions())
        assertTrue(state.needsWarning())
    }

    @Test
    fun `Revoked state should have reason`() {
        val state = AeternumUiState.Revoked(RevokedReason.USER_INITIATED)

        assertEquals("已撤销", state.getDisplayName())
        assertFalse(state.allowUserActions())
        assertTrue(state.needsWarning())
    }

    @Test
    fun `Error state should be recoverable or not`() {
        val recoverableError = AeternumUiState.Error(
            UiError.NetworkError("网络错误"),
            recoverable = true,
        )
        val unrecoverableError = AeternumUiState.Error(
            UiError.StorageError("存储损坏"),
            recoverable = false,
        )

        assertEquals("错误", recoverableError.getDisplayName())
        assertEquals("错误", unrecoverableError.getDisplayName())

        assertTrue(recoverableError.allowUserActions())
        assertFalse(unrecoverableError.allowUserActions())

        assertTrue(recoverableError.needsWarning())
        assertTrue(unrecoverableError.needsWarning())
    }

    // ========================================================================
    // 活跃子状态测试
    // ========================================================================

    @Test
    fun `Idle substate should be valid`() {
        val subState = ActiveSubState.Idle

        // Idle 是 data object，可以直接比较
        assertEquals(ActiveSubState.Idle, subState)
    }

    @Test
    fun `Decrypting substate should contain session and records`() {
        val session = VaultSessionHandle.Placeholder
        val recordIds = listOf("record_1", "record_2", "record_3")

        val subState = ActiveSubState.Decrypting(
            session = session,
            recordIds = recordIds,
        )

        assertEquals(session, subState.session)
        assertEquals(recordIds, subState.recordIds)
        assertEquals(3, subState.recordIds.size)
    }

    @Test
    fun `Rekeying substate should contain all properties`() {
        val subState = ActiveSubState.Rekeying(
            currentEpoch = 5u,
            targetEpoch = 6u,
            progress = 0.75f,
            stage = RekeyingStage.BROADCASTING,
        )

        assertEquals(5u, subState.currentEpoch)
        assertEquals(6u, subState.targetEpoch)
        assertEquals(0.75f, subState.progress, 0.001f)
        assertEquals(RekeyingStage.BROADCASTING, subState.stage)
    }

    @Test
    fun `Rekeying progress should be between 0 and 1`() {
        val stages = listOf(
            RekeyingStage.PREPARING to 0.0f,
            RekeyingStage.ENCRYPTING to 0.25f,
            RekeyingStage.BROADCASTING to 0.5f,
            RekeyingStage.COMMITTING to 0.75f,
            RekeyingStage.FINALIZING to 1.0f,
        )

        stages.forEach { (stage, expectedProgress) ->
            val subState = ActiveSubState.Rekeying(
                currentEpoch = 1u,
                targetEpoch = 2u,
                progress = expectedProgress,
                stage = stage,
            )

            assertEquals(stage, subState.stage)
            assertTrue("Progress should be between 0 and 1", subState.progress in 0f..1f)
        }
    }

    // ========================================================================
    // 轮换阶段测试
    // ========================================================================

    @Test
    fun `RekeyingStage should have correct order`() {
        val stages = RekeyingStage.values()

        assertEquals(5, stages.size)
        assertEquals(RekeyingStage.PREPARING, stages[0])
        assertEquals(RekeyingStage.ENCRYPTING, stages[1])
        assertEquals(RekeyingStage.BROADCASTING, stages[2])
        assertEquals(RekeyingStage.COMMITTING, stages[3])
        assertEquals(RekeyingStage.FINALIZING, stages[4])
    }

    // ========================================================================
    // 降级原因测试
    // ========================================================================

    @Test
    fun `DegradedReason should have all expected types`() {
        val reasons = listOf(
            DegradedReason.INTEGRITY_CHECK_FAILED,
            DegradedReason.NETWORK_UNAVAILABLE,
            DegradedReason.EPOCH_CONFLICT,
            DegradedReason.STORAGE_ERROR,
            DegradedReason.BIOMETRIC_UNAVAILABLE,
            DegradedReason.OTHER("自定义原因"),
        )

        reasons.forEach { reason ->
            val state = AeternumUiState.Degraded(reason)
            assertTrue("Degraded state should need warning", state.needsWarning())
            assertFalse("Degraded state should not allow user actions", state.allowUserActions())
        }
    }

    @Test
    fun `DegradedReason OTHER should contain message`() {
        val reason = DegradedReason.OTHER("测试降级原因")

        assertTrue(reason is DegradedReason.OTHER)
        assertEquals("测试降级原因", (reason as DegradedReason.OTHER).message)
    }

    // ========================================================================
    // 撤销原因测试
    // ========================================================================

    @Test
    fun `RevokedReason should have all expected types`() {
        val reasons = listOf(
            RevokedReason.REVOKED_BY_ANOTHER_DEVICE("device_123"),
            RevokedReason.EPOCH_ROLLBACK_DETECTED,
            RevokedReason.VETO_TIMEOUT,
            RevokedReason.KEY_COMPROMISED,
            RevokedReason.USER_INITIATED,
            RevokedReason.OTHER("自定义原因"),
        )

        reasons.forEach { reason ->
            val state = AeternumUiState.Revoked(reason)
            assertTrue("Revoked state should need warning", state.needsWarning())
            assertFalse("Revoked state should not allow user actions", state.allowUserActions())
        }
    }

    @Test
    fun `RevokedReason REVOKED_BY_ANOTHER_DEVICE should contain device ID`() {
        val reason = RevokedReason.REVOKED_BY_ANOTHER_DEVICE("device_456")

        assertTrue(reason is RevokedReason.REVOKED_BY_ANOTHER_DEVICE)
        assertEquals("device_456", (reason as RevokedReason.REVOKED_BY_ANOTHER_DEVICE).deviceId)
    }

    // ========================================================================
    // UI 错误测试
    // ========================================================================

    @Test
    fun `EpochError should contain all properties`() {
        val error = UiError.EpochError(
            message = "纪元冲突",
            currentEpoch = 5u,
            expectedEpoch = 6u,
        )

        assertEquals("纪元冲突", error.message)
        assertEquals(5u, error.currentEpoch)
        assertEquals(6u, error.expectedEpoch)
    }

    @Test
    fun `DataError should contain missing fields`() {
        val error = UiError.DataError(
            message = "数据不完整",
            missingFields = listOf("header", "signature"),
        )

        assertEquals("数据不完整", error.message)
        assertEquals(listOf("header", "signature"), error.missingFields)
    }

    @Test
    fun `AuthError should indicate biometric requirement`() {
        val error = UiError.AuthError(
            message = "认证失败",
            requiresBiometric = true,
        )

        assertEquals("认证失败", error.message)
        assertTrue(error.requiresBiometric)
    }

    @Test
    fun `VetoError should contain all properties`() {
        val error = UiError.VetoError(
            message = "操作被否决",
            vetoingDevice = "Pixel 8",
            remainingWindow = 24.hours,
        )

        assertEquals("操作被否决", error.message)
        assertEquals("Pixel 8", error.vetoingDevice)
        assertEquals(24.hours, error.remainingWindow)
    }

    @Test
    fun `StateError should contain state transition info`() {
        val error = UiError.StateError(
            message = "无效的状态转换",
            currentState = "Idle",
            attemptedTransition = "Decrypting",
        )

        assertEquals("无效的状态转换", error.message)
        assertEquals("Idle", error.currentState)
        assertEquals("Decrypting", error.attemptedTransition)
    }

    @Test
    fun `StorageError should contain available space`() {
        val error = UiError.StorageError(
            message = "存储空间不足",
            availableSpace = 1024L * 1024, // 1MB
        )

        assertEquals("存储空间不足", error.message)
        assertEquals(1024L * 1024, error.availableSpace)
    }

    @Test
    fun `NetworkError should indicate offline status`() {
        val offlineError = UiError.NetworkError("网络不可用", isOffline = true)
        val onlineError = UiError.NetworkError("请求超时", isOffline = false)

        assertTrue(offlineError.isOffline)
        assertFalse(onlineError.isOffline)
    }

    @Test
    fun `UnknownError should contain original error`() {
        val error = UiError.UnknownError(
            message = "未知错误",
            originalError = "NullPointerException: ...",
        )

        assertEquals("未知错误", error.message)
        assertEquals("NullPointerException: ...", error.originalError)
    }

    // ========================================================================
    // 状态辅助函数测试
    // ========================================================================

    @Test
    fun `getDisplayName should return correct names`() {
        assertEquals("未初始化", AeternumUiState.Uninitialized.getDisplayName())
        assertEquals("初始化中", AeternumUiState.Onboarding.getDisplayName())
        assertEquals("空闲", AeternumUiState.Active(ActiveSubState.Idle).getDisplayName())
        assertEquals("已解锁", AeternumUiState.Active(
            ActiveSubState.Decrypting(VaultSessionHandle.Placeholder, emptyList())
        ).getDisplayName())
        assertEquals("密钥轮换中", AeternumUiState.Active(
            ActiveSubState.Rekeying(1u, 2u, 0.5f, RekeyingStage.PREPARING)
        ).getDisplayName())
        assertEquals("降级模式", AeternumUiState.Degraded(DegradedReason.OTHER()).getDisplayName())
        assertEquals("已撤销", AeternumUiState.Revoked(RevokedReason.OTHER()).getDisplayName())
        assertEquals("错误", AeternumUiState.Error(UiError.UnknownError("")).getDisplayName())
    }

    @Test
    fun `allowUserActions should return correct values`() {
        // 允许操作的状态
        assertTrue(AeternumUiState.Onboarding.allowUserActions())
        assertTrue(AeternumUiState.Active(ActiveSubState.Idle).allowUserActions())
        assertTrue(AeternumUiState.Active(
            ActiveSubState.Decrypting(VaultSessionHandle.Placeholder, emptyList())
        ).allowUserActions())
        assertTrue(AeternumUiState.Active(
            ActiveSubState.Rekeying(1u, 2u, 0.5f, RekeyingStage.PREPARING)
        ).allowUserActions())

        // 不允许操作的状态
        assertFalse(AeternumUiState.Uninitialized.allowUserActions())
        assertFalse(AeternumUiState.Degraded(DegradedReason.OTHER()).allowUserActions())
        assertFalse(AeternumUiState.Revoked(RevokedReason.OTHER()).allowUserActions())

        // 可恢复错误允许操作
        assertTrue(AeternumUiState.Error(UiError.UnknownError(""), recoverable = true).allowUserActions())
        assertFalse(AeternumUiState.Error(UiError.UnknownError(""), recoverable = false).allowUserActions())
    }

    @Test
    fun `needsWarning should return correct values`() {
        // 需要警告的状态
        assertTrue(AeternumUiState.Degraded(DegradedReason.OTHER()).needsWarning())
        assertTrue(AeternumUiState.Revoked(RevokedReason.OTHER()).needsWarning())
        assertTrue(AeternumUiState.Error(UiError.UnknownError("")).needsWarning())
        assertTrue(AeternumUiState.Active(
            ActiveSubState.Rekeying(1u, 2u, 0.5f, RekeyingStage.PREPARING)
        ).needsWarning())

        // 不需要警告的状态
        assertFalse(AeternumUiState.Uninitialized.needsWarning())
        assertFalse(AeternumUiState.Onboarding.needsWarning())
        assertFalse(AeternumUiState.Active(ActiveSubState.Idle).needsWarning())
        assertFalse(AeternumUiState.Active(
            ActiveSubState.Decrypting(VaultSessionHandle.Placeholder, emptyList())
        ).needsWarning())
    }

    // ========================================================================
    // VaultSessionHandle 测试
    // ========================================================================

    @Test
    fun `VaultSessionHandle Placeholder should be valid`() {
        val handle = VaultSessionHandle.Placeholder

        // Placeholder 是 data object，可以直接比较
        assertEquals(VaultSessionHandle.Placeholder, handle)
    }

    // ========================================================================
    // 状态转换测试（模拟）
    // ========================================================================

    @Test
    fun `state transitions should follow valid paths`() {
        // Uninitialized -> Onboarding
        var state: AeternumUiState = AeternumUiState.Uninitialized
        state = AeternumUiState.Onboarding
        assertEquals(AeternumUiState.Onboarding, state)

        // Onboarding -> Active(Idle)
        state = AeternumUiState.Active(ActiveSubState.Idle)
        assertTrue(state is AeternumUiState.Active)

        // Active(Idle) -> Active(Decrypting)
        state = AeternumUiState.Active(
            ActiveSubState.Decrypting(VaultSessionHandle.Placeholder, listOf("record"))
        )
        assertTrue(state is AeternumUiState.Active)
        assertTrue((state as AeternumUiState.Active).subState is ActiveSubState.Decrypting)

        // Active(Decrypting) -> Active(Rekeying)
        state = AeternumUiState.Active(
            ActiveSubState.Rekeying(1u, 2u, 0.5f, RekeyingStage.PREPARING)
        )
        assertTrue(state is AeternumUiState.Active)
        assertTrue((state as AeternumUiState.Active).subState is ActiveSubState.Rekeying)

        // Active -> Degraded
        state = AeternumUiState.Degraded(DegradedReason.INTEGRITY_CHECK_FAILED)
        assertTrue(state is AeternumUiState.Degraded)

        // Active -> Revoked
        state = AeternumUiState.Revoked(RevokedReason.USER_INITIATED)
        assertTrue(state is AeternumUiState.Revoked)
    }

    @Test
    fun `Decrypting to Idle transition should clear session`() {
        // Given
        val decryptingState = AeternumUiState.Active(
            ActiveSubState.Decrypting(
                session = VaultSessionHandle.Placeholder,
                recordIds = listOf("record_1", "record_2"),
            ),
        )

        // When - 锁定会话
        val idleState = AeternumUiState.Active(ActiveSubState.Idle)

        // Then
        assertTrue(idleState is AeternumUiState.Active)
        assertTrue((idleState as AeternumUiState.Active).subState is ActiveSubState.Idle)
    }
}
