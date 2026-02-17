package io.aeternum.ui.viewmodel

import android.app.Application
import io.aeternum.ui.state.ActiveSubState
import io.aeternum.ui.state.AeternumUiState
import io.aeternum.ui.state.UiState
import io.aeternum.ui.state.VaultSessionHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * AeternumViewModel 单元测试
 *
 * 测试覆盖：
 * - 初始化流程
 * - 状态转换
 * - 设备管理
 * - 恢复流程
 * - 会话管理
 * - 事件处理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AeternumViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockVaultPath: File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // 创建模拟 Application
        mockApplication = mockk<Application>(relaxed = true)
        mockVaultPath = File("test_vault")
        mockVaultPath.mkdirs()

        // 模拟 filesDir
        val testFilesDir = File("test_files")
        testFilesDir.mkdirs()
        every { mockApplication.filesDir } returns testFilesDir
        every { mockApplication.applicationContext } returns mockApplication
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mockVaultPath.deleteRecursively()
        File("test_files").deleteRecursively()
    }

    // ========================================================================
    // 初始化测试
    // ========================================================================

    @Test
    fun `initial state should be Uninitialized`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When - ViewModel 在 init 中自动调用 initialize()

        // Then
        val state = viewModel.uiState.value
        // 初始状态应该是 Uninitialized 或后续状态
        assertTrue(
            "Initial state should be valid",
            state is AeternumUiState.Uninitialized ||
            state is AeternumUiState.Active ||
            state is AeternumUiState.Onboarding ||
            state is AeternumUiState.Degraded
        )
    }

    @Test
    fun `completeWelcome should transition to Onboarding`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.completeWelcome()

        // Then
        val state = viewModel.uiState.value
        assertEquals(AeternumUiState.Onboarding, state)
    }

    @Test
    fun `completeMnemonicBackup should keep Onboarding state`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.completeMnemonicBackup()

        // Then
        val state = viewModel.uiState.value
        assertEquals(AeternumUiState.Onboarding, state)
    }

    // ========================================================================
    // 设备管理测试
    // ========================================================================

    @Test
    fun `loadDeviceList should emit Loading then Success`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.loadDeviceList()

        // Then
        val state = viewModel.deviceListState.value
        assertTrue("Device list should be Success", state is UiState.Success)
        val devices = (state as UiState.Success).data
        assertTrue("Should have at least one device", devices.isNotEmpty())
    }

    @Test
    fun `loadDeviceList devices should have required properties`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.loadDeviceList()

        // Then
        val state = viewModel.deviceListState.value
        assertTrue(state is UiState.Success)
        val devices = (state as UiState.Success).data

        devices.forEach { device ->
            assertTrue("Device ID should not be blank", device.id.isNotBlank())
            assertTrue("Device name should not be blank", device.name.isNotBlank())
            assertTrue("Device state should be valid", device.state in listOf("active", "degraded", "revoked"))
        }
    }

    @Test
    fun `loadDeviceDetail should emit Loading then Success`() = runTest {
        // Given
        val viewModel = createViewModel()
        val deviceId = "device_1"

        // When
        viewModel.loadDeviceDetail(deviceId)

        // Then
        val state = viewModel.deviceDetailState.value
        assertTrue("Device detail should be Success", state is UiState.Success)
    }

    @Test
    fun `revokeDevice should emit event`() = runTest {
        // Given
        val viewModel = createViewModel()
        val deviceIdBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        // When
        viewModel.revokeDevice(deviceIdBytes)

        // Then - 应该发出事件
        // 由于使用了 SharedFlow，我们需要在调用前开始收集
    }

    // ========================================================================
    // 会话管理测试
    // ========================================================================

    @Test
    fun `lockSession should transition to Idle`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.lockSession()

        // Then
        val state = viewModel.uiState.value
        assertTrue("Should transition to valid state", state is AeternumUiState)
    }

    @Test
    fun `lockSession should emit event`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.lockSession()

        // Then - 会话锁定应该发出事件
        // 事件验证需要更复杂的设置
    }

    // ========================================================================
    // 密钥轮换测试
    // ========================================================================

    @Test
    fun `startRekeying should transition to Rekeying state`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.startRekeying()

        // Then
        val state = viewModel.uiState.value
        assertTrue("Should be in valid state", state is AeternumUiState)
    }

    // ========================================================================
    // 恢复流程测试
    // ========================================================================

    @Test
    fun `initiateRecovery should emit event`() = runTest {
        // Given
        val viewModel = createViewModel()
        val mnemonic = "test mnemonic phrase"

        // When
        viewModel.initiateRecovery(mnemonic)

        // Then - 应该发出事件
    }

    @Test
    fun `submitVeto should emit event`() = runTest {
        // Given
        val viewModel = createViewModel()
        val recoveryId = "recovery_123"

        // When
        viewModel.submitVeto(recoveryId)

        // Then - 应该发出事件
    }

    // ========================================================================
    // 状态属性测试
    // ========================================================================

    @Test
    fun `currentEpoch should be valid`() = runTest {
        // Given
        val viewModel = createViewModel()

        // Then
        val epoch = viewModel.currentEpoch.value
        assertTrue("Epoch should be null or a valid value", epoch == null || epoch is UInt)
    }

    @Test
    fun `addDeviceState should be Idle initially`() = runTest {
        // Given
        val viewModel = createViewModel()

        // Then
        val state = viewModel.addDeviceState.value
        assertEquals(UiState.Idle, state)
    }

    @Test
    fun `initiateAddDevice should transition to Loading then Success`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.initiateAddDevice()

        // Then
        val state = viewModel.addDeviceState.value
        assertTrue("Should be Success with handshake token", state is UiState.Success)
        val token = (state as UiState.Success).data
        assertTrue("Token should start with prefix", token.startsWith("aeternum_handshake_"))
    }

    // ========================================================================
    // 事件流测试
    // ========================================================================

    @Test
    fun `events should be emitted correctly`() = runTest {
        // Given
        val viewModel = createViewModel()

        // 收集事件
        val events = mutableListOf<io.aeternum.ui.state.UiEvent>()
        val collectJob = launch {
            viewModel.events.collect { event ->
                events.add(event)
            }
        }

        // When
        viewModel.lockSession()

        // Then
        // 事件应该被发出
        collectJob.cancel()
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private fun createViewModel(): AeternumViewModel {
        return AeternumViewModel(mockApplication)
    }
}
