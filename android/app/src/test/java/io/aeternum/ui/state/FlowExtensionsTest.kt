package io.aeternum.ui.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * FlowExtensions 和 SessionTimeoutManager 测试
 *
 * 测试覆盖：
 * - StateFlow 扩展
 * - Flow 转换方法
 * - 会话超时管理
 * - 事件处理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowExtensionsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================================================
    // StateFlow 扩展测试
    // ========================================================================

    @Test
    fun `MutableStateFlow should emit initial value`() = runTest {
        val flow = MutableStateFlow("initial")

        assertEquals("initial", flow.value)
    }

    @Test
    fun `MutableStateFlow should update value`() = runTest {
        val flow = MutableStateFlow("initial")

        flow.value = "updated"

        assertEquals("updated", flow.value)
    }

    @Test
    fun `asStateFlow should expose read-only StateFlow`() = runTest {
        val mutableFlow = MutableStateFlow("initial")
        val stateFlow = mutableFlow.asStateFlow()

        assertEquals("initial", stateFlow.value)
    }

    // ========================================================================
    // Flow 转换测试
    // ========================================================================

    @Test
    fun `flow should emit values in order`() = runTest {
        val flow = flowOf(1, 2, 3)
        val collected = mutableListOf<Int>()

        flow.collect { collected.add(it) }

        assertEquals(listOf(1, 2, 3), collected)
    }

    @Test
    fun `StateFlow should replay last value`() = runTest {
        val flow = MutableStateFlow(0)

        flow.value = 1
        flow.value = 2
        flow.value = 3

        assertEquals(3, flow.value)
    }

    // ========================================================================
    // UiEvent 测试
    // ========================================================================

    @Test
    fun `Navigate event should contain route and args`() {
        val event = UiEvent.Navigate("home", mapOf("id" to "123"))

        assertEquals("home", event.route)
        assertEquals(mapOf("id" to "123"), event.args)
    }

    @Test
    fun `NavigateBack should be valid event`() {
        val event = UiEvent.NavigateBack

        assertEquals(UiEvent.NavigateBack, event)
    }

    @Test
    fun `ShowSnackbar should contain message and duration`() {
        val shortEvent = UiEvent.ShowSnackbar("短消息", SnackbarDuration.Short)
        val longEvent = UiEvent.ShowSnackbar("长消息", SnackbarDuration.Long)
        val indefiniteEvent = UiEvent.ShowSnackbar("永久消息", SnackbarDuration.Indefinite)

        assertEquals("短消息", shortEvent.message)
        assertEquals(SnackbarDuration.Short, shortEvent.duration)

        assertEquals("长消息", longEvent.message)
        assertEquals(SnackbarDuration.Long, longEvent.duration)

        assertEquals("永久消息", indefiniteEvent.message)
        assertEquals(SnackbarDuration.Indefinite, indefiniteEvent.duration)
    }

    @Test
    fun `ShowDialog should contain all properties`() {
        var confirmCalled = false
        val event = UiEvent.ShowDialog(
            title = "确认删除",
            message = "此操作不可恢复",
            confirmText = "删除",
            onConfirm = { confirmCalled = true },
        )

        assertEquals("确认删除", event.title)
        assertEquals("此操作不可恢复", event.message)
        assertEquals("删除", event.confirmText)

        event.onConfirm()
        assertTrue(confirmCalled)
    }

    @Test
    fun `RequestBiometric should be valid event`() {
        val event = UiEvent.RequestBiometric

        assertEquals(UiEvent.RequestBiometric, event)
    }

    @Test
    fun `LockSession should be valid event`() {
        val event = UiEvent.LockSession

        assertEquals(UiEvent.LockSession, event)
    }

    @Test
    fun `Clear should be valid event`() {
        val event = UiEvent.Clear

        assertEquals(UiEvent.Clear, event)
    }

    // ========================================================================
    // SnackbarDuration 测试
    // ========================================================================

    @Test
    fun `SnackbarDuration should have correct values`() {
        val values = SnackbarDuration.values()

        assertEquals(3, values.size)
        assertTrue(values.contains(SnackbarDuration.Short))
        assertTrue(values.contains(SnackbarDuration.Long))
        assertTrue(values.contains(SnackbarDuration.Indefinite))
    }

    // ========================================================================
    // SessionTimeoutManager 测试
    // ========================================================================

    @Test
    fun `SessionTimeoutManager should initialize correctly`() {
        var timeoutCalled = false
        val manager = SessionTimeoutManager(
            timeout = 30.seconds,
            onTimeout = { timeoutCalled = true },
        )

        // 初始状态不应触发超时
        manager.checkTimeout()
        assertTrue(!timeoutCalled)
    }

    @Test
    fun `SessionTimeoutManager should detect timeout`() {
        var timeoutCalled = false
        val manager = SessionTimeoutManager(
            timeout = 100.milliseconds,
            onTimeout = { timeoutCalled = true },
        )

        manager.startMonitoring()

        // 等待超过超时时间
        Thread.sleep(150)

        manager.checkTimeout()
        assertTrue(timeoutCalled)
    }

    @Test
    fun `SessionTimeoutManager should reset on activity update`() {
        var timeoutCalled = false
        val manager = SessionTimeoutManager(
            timeout = 100.milliseconds,
            onTimeout = { timeoutCalled = true },
        )

        manager.startMonitoring()
        manager.updateActivity()

        // 等待但不超过超时时间
        Thread.sleep(50)

        manager.updateActivity() // 重置计时器

        Thread.sleep(50) // 从重置点开始，还没超时

        manager.checkTimeout()
        assertTrue(!timeoutCalled)
    }

    @Test
    fun `SessionTimeoutManager should stop monitoring`() {
        var timeoutCalled = false
        val manager = SessionTimeoutManager(
            timeout = 50.milliseconds,
            onTimeout = { timeoutCalled = true },
        )

        manager.startMonitoring()
        manager.stopMonitoring()

        Thread.sleep(100)

        manager.checkTimeout()
        // 停止监控后不应触发超时
        assertTrue(!timeoutCalled)
    }

    @Test
    fun `SessionTimeoutManager reset should clear timeout`() {
        var timeoutCalled = false
        val manager = SessionTimeoutManager(
            timeout = 50.milliseconds,
            onTimeout = { timeoutCalled = true },
        )

        manager.startMonitoring()
        manager.reset()

        // 重置后计时器应该重新开始
        Thread.sleep(30)

        manager.checkTimeout()
        assertTrue(!timeoutCalled)
    }

    // ========================================================================
    // 实际使用场景测试
    // ========================================================================

    @Test
    fun `simulate session timeout flow`() = runTest {
        var isLocked = false
        val manager = SessionTimeoutManager(
            timeout = 30.seconds,
            onTimeout = { isLocked = true },
        )

        // 用户活动
        manager.updateActivity()
        manager.startMonitoring()

        // 后续活动
        manager.updateActivity()

        // 模拟超时
        manager.reset()
        Thread.sleep(50)
        manager.checkTimeout()

        // 不应锁定（因为时间未到）
        assertTrue(!isLocked)
    }

    @Test
    fun `UiState with StateFlow should work together`() = runTest {
        val flow = MutableStateFlow<UiState<String>>(UiState.Idle)

        assertEquals(UiState.Idle, flow.value)

        flow.value = UiState.Loading
        assertEquals(UiState.Loading, flow.value)

        flow.value = UiState.Success("data")
        assertEquals(UiState.Success("data"), flow.value)

        flow.value = UiState.Error("error")
        assertEquals(UiState.Error("error"), flow.value)
    }

    @Test
    fun `AeternumUiState with StateFlow should work together`() = runTest {
        val flow = MutableStateFlow<AeternumUiState>(AeternumUiState.Uninitialized)

        assertEquals(AeternumUiState.Uninitialized, flow.value)

        flow.value = AeternumUiState.Onboarding
        assertEquals(AeternumUiState.Onboarding, flow.value)

        flow.value = AeternumUiState.Active(ActiveSubState.Idle)
        assertTrue(flow.value is AeternumUiState.Active)

        flow.value = AeternumUiState.Degraded(DegradedReason.INTEGRITY_CHECK_FAILED)
        assertTrue(flow.value is AeternumUiState.Degraded)

        flow.value = AeternumUiState.Revoked(RevokedReason.USER_INITIATED)
        assertTrue(flow.value is AeternumUiState.Revoked)
    }
}
