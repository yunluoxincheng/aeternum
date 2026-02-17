package io.aeternum.data

import uniffi.aeternum.AeternumEngine
import uniffi.aeternum.PqrrException
import uniffi.aeternum.VaultSession
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * VaultRepository 测试
 *
 * 测试 Vault 解锁流程、数据存储/检索流程和会话锁定功能
 *
 * INVARIANT: Kotlin 层仅持有 Rust 实例句柄，不持有明文密钥
 */
class VaultRepositoryTest {

    // ========================================================================
    // Mock 设置
    // ========================================================================

    private lateinit var mockEngine: AeternumEngine
    private lateinit var mockSession: VaultSession
    private lateinit var repository: VaultRepository

    @Before
    fun setup() {
        mockEngine = mockk(relaxed = true)
        mockSession = mockk(relaxed = true)

        // Mock AeternumEngine 伴生对象
        mockkStatic("uniffi.aeternum.AeternumEngineKt")

        repository = VaultRepository()
        // 通过反射设置内部的 engine
        val engineField = VaultRepository::class.java.getDeclaredField("_engine")
        engineField.isAccessible = true
        engineField.set(repository, mockEngine)
    }

    @After
    fun tearDown() {
        unmockkStatic("uniffi.aeternum.AeternumEngineKt")
    }

    // ========================================================================
    // 测试: Vault 解锁流程
    // ========================================================================

    @Test
    fun `initializeVault 应该成功初始化 Vault`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        every { mockEngine.initializeVault(any()) } just Runs

        // When
        val result = repository.initializeVault(hardwareKeyBlob)

        // Then
        assertTrue(result.isSuccess)
        verify { mockEngine.initializeVault(any()) }
    }

    @Test
    fun `initializeVault 应该处理 PqrrException 错误`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val pqrrError = mockk<PqrrException.StorageException>(relaxed = true) {
            every { message } returns "存储空间不足"
        }
        every { mockEngine.initializeVault(any()) } throws pqrrError

        // When
        val result = repository.initializeVault(hardwareKeyBlob)

        // Then
        assertTrue(result.isFailure)
        assertIs<java.io.IOException>(result.exceptionOrNull())
    }

    @Test
    fun `unlockVault 应该成功解锁并返回会话`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true

        // When
        val result = repository.unlockVault(hardwareKeyBlob)

        // Then
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        verify { mockEngine.unlock(any()) }
    }

    @Test
    fun `unlockVault 应该处理 EpochRegression 错误`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val epochError = mockk<PqrrException.EpochRegression>(relaxed = true) {
            every { current } returns 5u
            every { attempted } returns 3u
        }
        every { mockEngine.unlock(any()) } throws epochError

        // When
        val result = repository.unlockVault(hardwareKeyBlob)

        // Then
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("纪元回滚") == true)
    }

    @Test
    fun `unlockVault 应该处理 HeaderIncomplete 错误`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val headerError = mockk<PqrrException.HeaderIncomplete>(relaxed = true) {
            every { reason } returns "缺少必需字段"
        }
        every { mockEngine.unlock(any()) } throws headerError

        // When
        val result = repository.unlockVault(hardwareKeyBlob)

        // Then
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("Header 不完整") == true)
    }

    // ========================================================================
    // 测试: 数据存储/检索流程
    // ========================================================================

    @Test
    fun `storeEntry 应该成功存储数据（需要活跃会话）`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true
        every { mockSession.storeEntry(any(), any(), any()) } just Runs

        // 先解锁
        repository.unlockVault(hardwareKeyBlob)

        // When
        val result = repository.storeEntry("record_1", "password", "secret123")

        // Then
        assertTrue(result.isSuccess)
        verify { mockSession.storeEntry("record_1", "password", "secret123") }
    }

    @Test
    fun `storeEntry 应该在未解锁时失败`() = runTest {
        // Given - 没有活跃会话

        // When
        val result = repository.storeEntry("record_1", "password", "secret123")

        // Then
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("Vault is locked") == true)
    }

    @Test
    fun `retrieveEntry 应该成功检索数据（需要活跃会话）`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true
        every { mockSession.retrieveEntry("record_1", "password") } returns "secret123"

        // 先解锁
        repository.unlockVault(hardwareKeyBlob)

        // When
        val result = repository.retrieveEntry("record_1", "password")

        // Then
        assertTrue(result.isSuccess)
        assertEquals("secret123", result.getOrNull())
        verify { mockSession.retrieveEntry("record_1", "password") }
    }

    @Test
    fun `retrieveEntry 应该在未解锁时失败`() = runTest {
        // Given - 没有活跃会话

        // When
        val result = repository.retrieveEntry("record_1", "password")

        // Then
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("Vault is locked") == true)
    }

    @Test
    fun `retrieveEntry 应该正确处理存储和检索的往返`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val testRecordId = "test_record"
        val testFieldKey = "api_key"
        val testValue = "sk_live_51AbCdEf1234567890"

        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true
        every { mockSession.storeEntry(testRecordId, testFieldKey, testValue) } just Runs
        every { mockSession.retrieveEntry(testRecordId, testFieldKey) } returns testValue

        // 先解锁
        repository.unlockVault(hardwareKeyBlob)

        // When - 存储
        val storeResult = repository.storeEntry(testRecordId, testFieldKey, testValue)
        // Then - 验证存储成功
        assertTrue(storeResult.isSuccess)

        // When - 检索
        val retrieveResult = repository.retrieveEntry(testRecordId, testFieldKey)
        // Then - 验证检索成功且值正确
        assertTrue(retrieveResult.isSuccess)
        assertEquals(testValue, retrieveResult.getOrNull())
    }

    @Test
    fun `listRecordIds 应该成功列出记录 ID（需要活跃会话）`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val expectedIds = listOf("record_1", "record_2", "record_3")
        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true
        every { mockSession.listRecordIds() } returns expectedIds

        // 先解锁
        repository.unlockVault(hardwareKeyBlob)

        // When
        val result = repository.listRecordIds()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedIds, result.getOrNull())
        verify { mockSession.listRecordIds() }
    }

    @Test
    fun `listRecordIds 应该在未解锁时失败`() = runTest {
        // Given - 没有活跃会话

        // When
        val result = repository.listRecordIds()

        // Then
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    // ========================================================================
    // 测试: 会话锁定功能
    // ========================================================================

    @Test
    fun `lockVault 应该成功锁定并清除会话`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true
        every { mockSession.lock() } just Runs

        // 先解锁
        repository.unlockVault(hardwareKeyBlob)
        assertTrue(repository.isSessionValid())

        // When
        repository.lockVault()

        // Then
        verify { mockSession.lock() }
        assertFalse(repository.isSessionValid())
    }

    @Test
    fun `lockVault 在没有活跃会话时应该安全处理`() = runTest {
        // Given - 没有活跃会话

        // When - 不应该抛出异常
        repository.lockVault()

        // Then
        assertFalse(repository.isSessionValid())
    }

    @Test
    fun `lockVault 后应该拒绝所有数据操作`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true
        every { mockSession.lock() } just Runs

        // 解锁然后锁定
        repository.unlockVault(hardwareKeyBlob)
        repository.lockVault()

        // When - 尝试存储
        val storeResult = repository.storeEntry("record_1", "key", "value")
        // When - 尝试检索
        val retrieveResult = repository.retrieveEntry("record_1", "key")
        // When - 尝试列出
        val listResult = repository.listRecordIds()

        // Then - 所有操作都应该失败
        assertTrue(storeResult.isFailure)
        assertTrue(retrieveResult.isFailure)
        assertTrue(listResult.isFailure)
    }

    @Test
    fun `isSessionValid 应该正确反映会话状态`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true
        every { mockSession.lock() } just Runs

        // Initially - 初始状态无效
        assertFalse(repository.isSessionValid())

        // When - 解锁后有效
        repository.unlockVault(hardwareKeyBlob)
        assertTrue(repository.isSessionValid())

        // When - 锁定后无效
        repository.lockVault()
        assertFalse(repository.isSessionValid())
    }

    // ========================================================================
    // 测试: 错误映射
    // ========================================================================

    @Test
    fun `应该正确映射 InsufficientPrivileges 错误`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val privilegeError = mockk<PqrrException.InsufficientPrivileges>(relaxed = true) {
            every { role } returns "RECOVERY"
            every { operation } returns "σ_rotate"
        }
        every { mockEngine.unlock(any()) } throws privilegeError

        // When
        val result = repository.unlockVault(hardwareKeyBlob)

        // Then
        assertTrue(result.isFailure)
        assertIs<SecurityException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("权限不足") == true)
        assertTrue(result.exceptionOrNull()?.message?.contains("RECOVERY") == true)
    }

    @Test
    fun `应该正确映射 Vetoed 错误`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val vetoError = mockk<PqrrException.Vetoed>(relaxed = true) {
            every { requestId } returns "recovery_123"
            every { vetoCount } returns 2u
        }
        every { mockEngine.unlock(any()) } throws vetoError

        // When
        val result = repository.unlockVault(hardwareKeyBlob)

        // Then
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("恢复被否决") == true)
    }

    @Test
    fun `应该正确映射 InvalidStateTransition 错误`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val transitionError = mockk<PqrrException.InvalidStateTransition>(relaxed = true) {
            every { from } returns "IDLE"
            every { to } returns "REVOKED"
            every { reason } returns "缺少必需的轮换步骤"
        }
        every { mockEngine.unlock(any()) } throws transitionError

        // When
        val result = repository.unlockVault(hardwareKeyBlob)

        // Then
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("无效状态转换") == true)
    }

    // ========================================================================
    // 测试: 资源清理
    // ========================================================================

    @Test
    fun `close 应该锁定会话并关闭引擎`() = runTest {
        // Given
        val hardwareKeyBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        every { mockEngine.unlock(any()) } returns mockSession
        every { mockSession.isValid() } returns true
        every { mockSession.lock() } just Runs
        every { mockEngine.close() } just Runs

        // 解锁
        repository.unlockVault(hardwareKeyBlob)

        // When
        repository.close()

        // Then
        verify { mockSession.lock() }
        verify { mockEngine.close() }
    }

    @Test
    fun `close 在没有活跃会话时应该安全处理`() = runTest {
        // Given - 没有活跃会话
        every { mockEngine.close() } just Runs

        // When - 不应该抛出异常
        repository.close()

        // Then
        verify { mockEngine.close() }
    }
}
