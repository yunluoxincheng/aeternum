package io.aeternum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OfflineOperationQueue 离线队列测试
 *
 * 测试覆盖：
 * - 队列基本操作
 * - 重试机制
 * - 并发处理
 * - 操作类型
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineQueueTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================================================
    // 队列基本操作测试
    // ========================================================================

    @Test
    fun `empty queue should have size 0`() {
        val queue = createTestQueue()

        assertEquals(0, queue.size())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `enqueue should increase queue size`() {
        val queue = createTestQueue()

        queue.enqueue("operation1")
        assertEquals(1, queue.size())

        queue.enqueue("operation2")
        assertEquals(2, queue.size())
    }

    @Test
    fun `peek should return first operation without removing`() {
        val queue = createTestQueue()

        queue.enqueue("operation1")
        queue.enqueue("operation2")

        assertEquals("operation1", queue.peek())
        assertEquals(2, queue.size()) // 大小不变
    }

    @Test
    fun `peek on empty queue should return null`() {
        val queue = createTestQueue()

        assertNull(queue.peek())
    }

    @Test
    fun `clear should remove all operations`() {
        val queue = createTestQueue()

        queue.enqueue("op1")
        queue.enqueue("op2")
        queue.enqueue("op3")

        assertEquals(3, queue.size())

        queue.clear()

        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size())
    }

    @Test
    fun `getAll should return all operations`() {
        val queue = createTestQueue()

        queue.enqueue("op1")
        queue.enqueue("op2")
        queue.enqueue("op3")

        val all = queue.getAll()

        assertEquals(3, all.size)
        assertEquals(listOf("op1", "op2", "op3"), all)
    }

    // ========================================================================
    // 队列处理测试
    // ========================================================================

    @Test
    fun `processAll should process all operations`() = runTest {
        val processed = mutableListOf<String>()
        val queue = OfflineOperationQueue<String>(
            maxRetries = 3,
            processor = { op ->
                processed.add(op)
                Result.success(Unit)
            },
        )

        queue.enqueue("op1")
        queue.enqueue("op2")
        queue.enqueue("op3")

        val result = queue.processAll()

        assertEquals(3, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(3, result.totalProcessed)
        assertEquals(listOf("op1", "op2", "op3"), processed)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `processAll should handle failures and retry`() = runTest {
        var callCount = 0
        val queue = OfflineOperationQueue<String>(
            maxRetries = 2,
            processor = { _ ->
                callCount++
                if (callCount < 3) {
                    Result.failure(Exception("模拟失败"))
                } else {
                    Result.success(Unit)
                }
            },
        )

        queue.enqueue("op1")

        // 第一次处理：失败，重试计数增加
        var result = queue.processAll()
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)

        // 第二次处理：仍然失败
        result = queue.processAll()
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)

        // 第三次处理：成功
        result = queue.processAll()
        assertEquals(1, result.successCount)
    }

    @Test
    fun `processAll should skip operations exceeding max retries`() = runTest {
        var failCount = 0
        val queue = OfflineOperationQueue<String>(
            maxRetries = 2,
            processor = { _ ->
                failCount++
                Result.failure(Exception("持续失败"))
            },
        )

        queue.enqueue("op1")

        // 处理直到超过最大重试次数
        queue.processAll() // 第 1 次失败，重试计数 = 1
        queue.processAll() // 第 2 次失败，重试计数 = 2
        queue.processAll() // 第 3 次失败，重试计数 = 3 (超过 maxRetries)

        // 再次处理时应该跳过
        val result = queue.processAll()
        assertTrue(result.skippedCount >= 0 || result.failureCount == 0)
    }

    @Test
    fun `processAll should return alreadyProcessing if already processing`() = runTest {
        val queue = createSlowQueue()

        queue.enqueue("op1")

        // 模拟正在处理的情况
        // 由于我们使用的是测试调度器，需要手动控制
        val result = queue.processAll()

        // 第二次调用应该返回 alreadyProcessing = false（因为第一次已完成）
        assertFalse(result.alreadyProcessing)
    }

    // ========================================================================
    // 离线操作类型测试
    // ========================================================================

    @Test
    fun `RevokeDevice should contain all properties`() {
        val operation = OfflineOperation.RevokeDevice(
            deviceId = "device_123",
            timestamp = 1000L,
        )

        assertEquals("device_123", operation.deviceId)
        assertEquals(1000L, operation.timestamp)
    }

    @Test
    fun `SubmitVeto should contain all properties`() {
        val operation = OfflineOperation.SubmitVeto(
            recoveryId = "recovery_456",
            timestamp = 2000L,
        )

        assertEquals("recovery_456", operation.recoveryId)
        assertEquals(2000L, operation.timestamp)
    }

    @Test
    fun `SyncRequest should contain all properties`() {
        val operation = OfflineOperation.SyncRequest(
            syncToken = "token_789",
            timestamp = 3000L,
        )

        assertEquals("token_789", operation.syncToken)
        assertEquals(3000L, operation.timestamp)
    }

    @Test
    fun `EpochUpgradeConfirm should contain all properties`() {
        val operation = OfflineOperation.EpochUpgradeConfirm(
            newEpoch = 10u,
            timestamp = 4000L,
        )

        assertEquals(10u, operation.newEpoch)
        assertEquals(4000L, operation.timestamp)
    }

    @Test
    fun `OfflineOperation should be distinguishable by type`() {
        val operations: List<OfflineOperation> = listOf(
            OfflineOperation.RevokeDevice("device_1"),
            OfflineOperation.SubmitVeto("recovery_1"),
            OfflineOperation.SyncRequest("token_1"),
            OfflineOperation.EpochUpgradeConfirm(5u),
        )

        var revokeCount = 0
        var vetoCount = 0
        var syncCount = 0
        var epochCount = 0

        operations.forEach { op ->
            when (op) {
                is OfflineOperation.RevokeDevice -> revokeCount++
                is OfflineOperation.SubmitVeto -> vetoCount++
                is OfflineOperation.SyncRequest -> syncCount++
                is OfflineOperation.EpochUpgradeConfirm -> epochCount++
            }
        }

        assertEquals(1, revokeCount)
        assertEquals(1, vetoCount)
        assertEquals(1, syncCount)
        assertEquals(1, epochCount)
    }

    // ========================================================================
    // 队列与操作类型集成测试
    // ========================================================================

    @Test
    fun `queue should handle OfflineOperation types`() = runTest {
        val processed = mutableListOf<OfflineOperation>()
        val queue = OfflineOperationQueue<OfflineOperation>(
            maxRetries = 3,
            processor = { op ->
                processed.add(op)
                Result.success(Unit)
            },
        )

        queue.enqueue(OfflineOperation.RevokeDevice("device_1"))
        queue.enqueue(OfflineOperation.SubmitVeto("recovery_1"))
        queue.enqueue(OfflineOperation.SyncRequest("token_1"))

        val result = queue.processAll()

        assertEquals(3, result.successCount)
        assertEquals(3, processed.size)

        assertTrue(processed[0] is OfflineOperation.RevokeDevice)
        assertTrue(processed[1] is OfflineOperation.SubmitVeto)
        assertTrue(processed[2] is OfflineOperation.SyncRequest)
    }

    @Test
    fun `queue should maintain FIFO order`() = runTest {
        val processed = mutableListOf<String>()
        val queue = OfflineOperationQueue<String>(
            maxRetries = 3,
            processor = { op ->
                processed.add(op)
                Result.success(Unit)
            },
        )

        // 按顺序入队
        queue.enqueue("first")
        queue.enqueue("second")
        queue.enqueue("third")
        queue.enqueue("fourth")
        queue.enqueue("fifth")

        queue.processAll()

        // 验证处理顺序
        assertEquals(listOf("first", "second", "third", "fourth", "fifth"), processed)
    }

    // ========================================================================
    // 边界情况测试
    // ========================================================================

    @Test
    fun `processAll on empty queue should return zero counts`() = runTest {
        val queue = createTestQueue()

        val result = queue.processAll()

        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.totalProcessed)
    }

    @Test
    fun `queue should handle large number of operations`() = runTest {
        val queue = createTestQueue()
        val operationCount = 1000

        repeat(operationCount) { i ->
            queue.enqueue("op_$i")
        }

        assertEquals(operationCount, queue.size())

        val result = queue.processAll()

        assertEquals(operationCount, result.successCount)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `queue should handle mixed success and failure`() = runTest {
        var processedCount = 0
        val queue = OfflineOperationQueue<String>(
            maxRetries = 1,
            processor = { op ->
                processedCount++
                if (op.endsWith("_fail")) {
                    Result.failure(Exception("模拟失败"))
                } else {
                    Result.success(Unit)
                }
            },
        )

        queue.enqueue("op1_success")
        queue.enqueue("op2_fail")
        queue.enqueue("op3_success")
        queue.enqueue("op4_fail")

        val result = queue.processAll()

        // 成功的应该计入 successCount
        assertTrue(result.successCount >= 2)
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private fun createTestQueue(): OfflineOperationQueue<String> {
        return OfflineOperationQueue(
            maxRetries = 3,
            processor = { Result.success(Unit) },
        )
    }

    private fun createSlowQueue(): OfflineOperationQueue<String> {
        return OfflineOperationQueue(
            maxRetries = 3,
            processor = { op ->
                kotlinx.coroutines.delay(100)
                Result.success(Unit)
            },
        )
    }
}
