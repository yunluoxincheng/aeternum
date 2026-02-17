package io.aeternum.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UiState 通用状态封装测试
 *
 * 测试覆盖：
 * - 状态类型定义
 * - 状态属性检查
 * - 数据获取方法
 * - 状态转换方法
 * - 工厂方法
 */
class UiStateTest {

    // ========================================================================
    // 状态类型测试
    // ========================================================================

    @Test
    fun `Idle should be valid state`() {
        val state: UiState<Nothing> = UiState.Idle

        assertFalse(state.isLoading)
        assertFalse(state.isError)
        assertFalse(state.isSuccess)
        assertNull(state.getOrNull())
    }

    @Test
    fun `Loading should be valid state`() {
        val state: UiState<Nothing> = UiState.Loading

        assertTrue(state.isLoading)
        assertFalse(state.isError)
        assertFalse(state.isSuccess)
        assertNull(state.getOrNull())
    }

    @Test
    fun `Success should contain data`() {
        val data = listOf("item1", "item2", "item3")
        val state: UiState<List<String>> = UiState.Success(data)

        assertTrue(state.isSuccess)
        assertFalse(state.isLoading)
        assertFalse(state.isError)
        assertEquals(data, state.getOrNull())
        assertEquals(data, (state as UiState.Success).data)
    }

    @Test
    fun `Error should contain message and recoverable flag`() {
        val recoverableError: UiState<Nothing> = UiState.Error("网络错误", recoverable = true)
        val unrecoverableError: UiState<Nothing> = UiState.Error("致命错误", recoverable = false)

        assertTrue(recoverableError.isError)
        assertTrue(unrecoverableError.isError)

        assertEquals("网络错误", (recoverableError as UiState.Error).error)
        assertEquals("致命错误", (unrecoverableError as UiState.Error).error)
        assertTrue(recoverableError.recoverable)
        assertFalse(unrecoverableError.recoverable)
    }

    // ========================================================================
    // 数据获取测试
    // ========================================================================

    @Test
    fun `getOrNull should return data for Success`() {
        val data = "test_data"
        val state: UiState<String> = UiState.Success(data)

        assertEquals(data, state.getOrNull())
    }

    @Test
    fun `getOrNull should return null for non-Success states`() {
        assertNull((UiState.Idle as UiState<String>).getOrNull())
        assertNull((UiState.Loading as UiState<String>).getOrNull())
        assertNull((UiState.Error("error") as UiState<String>).getOrNull())
    }

    @Test
    fun `getOrElse should return data for Success`() {
        val data = "test_data"
        val default = "default"
        val state: UiState<String> = UiState.Success(data)

        assertEquals(data, state.getOrElse(default))
    }

    @Test
    fun `getOrElse should return default for non-Success states`() {
        val default = "default"

        assertEquals(default, (UiState.Idle as UiState<String>).getOrElse(default))
        assertEquals(default, (UiState.Loading as UiState<String>).getOrElse(default))
        assertEquals(default, (UiState.Error("error") as UiState<String>).getOrElse(default))
    }

    // ========================================================================
    // 状态转换测试
    // ========================================================================

    @Test
    fun `map should transform Success data`() {
        val state: UiState<Int> = UiState.Success(5)
        val mapped = state.map { it * 2 }

        assertTrue(mapped is UiState.Success)
        assertEquals(10, (mapped as UiState.Success).data)
    }

    @Test
    fun `map should preserve Idle state`() {
        val state: UiState<Int> = UiState.Idle
        val mapped = state.map { it * 2 }

        assertEquals(UiState.Idle, mapped)
    }

    @Test
    fun `map should preserve Loading state`() {
        val state: UiState<Int> = UiState.Loading
        val mapped = state.map { it * 2 }

        assertEquals(UiState.Loading, mapped)
    }

    @Test
    fun `map should preserve Error state with message`() {
        val state: UiState<Int> = UiState.Error("原始错误", recoverable = true)
        val mapped = state.map { it * 2 }

        assertTrue(mapped is UiState.Error)
        assertEquals("原始错误", (mapped as UiState.Error).error)
        assertTrue(mapped.recoverable)
    }

    @Test
    fun `map should chain multiple transformations`() {
        val state: UiState<Int> = UiState.Success(1)
        val result = state
            .map { it + 1 }     // 2
            .map { it * 3 }     // 6
            .map { it - 2 }     // 4

        assertTrue(result is UiState.Success)
        assertEquals(4, (result as UiState.Success).data)
    }

    // ========================================================================
    // 工厂方法测试
    // ========================================================================

    @Test
    fun `fromNullable should create Success for non-null value`() {
        val value = "test"
        val state = UiState.fromNullable(value)

        assertTrue(state is UiState.Success)
        assertEquals(value, (state as UiState.Success).data)
    }

    @Test
    fun `fromNullable should create Error for null value`() {
        val state = UiState.fromNullable<String?>(null)

        assertTrue(state is UiState.Error)
        assertEquals("值为空", (state as UiState.Error).error)
        assertFalse(state.recoverable)
    }

    // ========================================================================
    // 类型安全测试
    // ========================================================================

    @Test
    fun `UiState should handle complex types`() {
        data class User(val id: Int, val name: String)

        val user = User(1, "张三")
        val state: UiState<User> = UiState.Success(user)

        assertTrue(state.isSuccess)
        assertEquals(user, state.getOrNull())
    }

    @Test
    fun `UiState should handle nullable types`() {
        val state: UiState<String?> = UiState.Success(null)

        assertTrue(state.isSuccess)
        assertNull(state.getOrNull())
    }

    @Test
    fun `UiState should handle collections`() {
        val items = listOf(1, 2, 3, 4, 5)
        val state: UiState<List<Int>> = UiState.Success(items)

        assertTrue(state.isSuccess)
        assertEquals(5, state.getOrNull()?.size)
    }

    @Test
    fun `UiState should handle empty collections`() {
        val items = emptyList<String>()
        val state: UiState<List<String>> = UiState.Success(items)

        assertTrue(state.isSuccess)
        assertTrue(state.getOrNull()?.isEmpty() == true)
    }

    // ========================================================================
    // 状态比较测试
    // ========================================================================

    @Test
    fun `Idle should equal Idle`() {
        assertEquals(UiState.Idle, UiState.Idle)
    }

    @Test
    fun `Loading should equal Loading`() {
        assertEquals(UiState.Loading, UiState.Loading)
    }

    @Test
    fun `Success with same data should be equal`() {
        val data = "same_data"
        assertEquals(UiState.Success(data), UiState.Success(data))
    }

    @Test
    fun `Error with same message should be equal`() {
        assertEquals(
            UiState.Error("error", recoverable = true),
            UiState.Error("error", recoverable = true)
        )
    }

    // ========================================================================
    // 实际使用场景测试
    // ========================================================================

    @Test
    fun `simulate loading flow`() {
        // 初始状态
        var state: UiState<String> = UiState.Idle
        assertFalse(state.isLoading)

        // 开始加载
        state = UiState.Loading
        assertTrue(state.isLoading)

        // 加载成功
        state = UiState.Success("loaded_data")
        assertTrue(state.isSuccess)
        assertEquals("loaded_data", state.getOrNull())
    }

    @Test
    fun `simulate error and retry flow`() {
        // 初始加载
        var state: UiState<String> = UiState.Loading

        // 加载失败
        state = UiState.Error("网络错误", recoverable = true)
        assertTrue(state.isError)
        assertTrue((state as UiState.Error).recoverable)

        // 重试
        state = UiState.Loading
        assertTrue(state.isLoading)

        // 成功
        state = UiState.Success("loaded_data")
        assertTrue(state.isSuccess)
    }

    @Test
    fun `simulate list loading with empty result`() {
        var state: UiState<List<String>> = UiState.Loading

        // 空结果
        state = UiState.Success(emptyList())
        assertTrue(state.isSuccess)
        assertTrue(state.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `simulate list loading with data`() {
        var state: UiState<List<String>> = UiState.Loading

        // 有数据
        val data = listOf("item1", "item2", "item3")
        state = UiState.Success(data)
        assertTrue(state.isSuccess)
        assertEquals(3, state.getOrNull()?.size)
    }
}
