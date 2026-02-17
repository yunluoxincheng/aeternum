package io.aeternum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 离线操作队列
 *
 * 用于在网络不可用时暂存操作请求，待网络恢复后自动重试
 *
 * INVARIANT: 队列中的操作不包含敏感数据
 * INVARIANT: 操作执行顺序遵循 FIFO
 *
 * @param maxRetries 最大重试次数
 * @param processor 操作处理器
 */
class OfflineOperationQueue<T>(
    private val maxRetries: Int = 3,
    private val processor: suspend (T) -> Result<Unit>,
) {
    private val queue = ConcurrentLinkedQueue<QueuedOperation<T>>()
    private var isProcessing = false

    /**
     * 排队操作
     *
     * @param operation 要排队的操作
     */
    fun enqueue(operation: T) {
        queue.offer(QueuedOperation(operation, retryCount = 0))
    }

    /**
     * 获取队列大小
     */
    fun size(): Int = queue.size

    /**
     * 检查队列是否为空
     */
    fun isEmpty(): Boolean = queue.isEmpty()

    /**
     * 清空队列
     */
    fun clear() {
        queue.clear()
    }

    /**
     * 处理队列中的所有操作
     *
     * @return 处理结果统计
     */
    suspend fun processAll(): ProcessResult = withContext(Dispatchers.IO) {
        if (isProcessing) {
            return@withContext ProcessResult(alreadyProcessing = true)
        }

        isProcessing = true
        var successCount = 0
        var failureCount = 0
        var skippedCount = 0

        while (queue.isNotEmpty()) {
            val queuedOp = queue.poll() ?: break

            if (queuedOp.retryCount >= maxRetries) {
                skippedCount++
                continue
            }

            val result = processor(queuedOp.operation)
            if (result.isSuccess) {
                successCount++
            } else {
                failureCount++
                // 重新排队以供重试
                queue.offer(queuedOp.copy(retryCount = queuedOp.retryCount + 1))
            }
        }

        isProcessing = false
        ProcessResult(
            successCount = successCount,
            failureCount = failureCount,
            skippedCount = skippedCount,
        )
    }

    /**
     * 查看队首操作（不移除）
     */
    fun peek(): T? = queue.peek()?.operation

    /**
     * 获取所有待处理操作
     */
    fun getAll(): List<T> = queue.map { it.operation }

    /**
     * 排队操作包装类
     */
    data class QueuedOperation<T>(
        val operation: T,
        val retryCount: Int,
    )

    /**
     * 处理结果
     */
    data class ProcessResult(
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val skippedCount: Int = 0,
        val alreadyProcessing: Boolean = false,
    ) {
        val totalProcessed: Int
            get() = successCount + failureCount + skippedCount
    }
}

/**
 * 离线操作类型
 */
sealed class OfflineOperation {
    /**
     * 设备撤销操作
     */
    data class RevokeDevice(val deviceId: String, val timestamp: Long = System.currentTimeMillis()) : OfflineOperation()

    /**
     * 否决提交操作
     */
    data class SubmitVeto(val recoveryId: String, val timestamp: Long = System.currentTimeMillis()) : OfflineOperation()

    /**
     * 同步请求操作
     */
    data class SyncRequest(val syncToken: String, val timestamp: Long = System.currentTimeMillis()) : OfflineOperation()

    /**
     * 纪元升级确认操作
     */
    data class EpochUpgradeConfirm(val newEpoch: UInt, val timestamp: Long = System.currentTimeMillis()) : OfflineOperation()
}
