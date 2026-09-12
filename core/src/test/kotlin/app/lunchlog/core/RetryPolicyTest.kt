package app.lunchlog.core

import app.lunchlog.core.sync.RetryPolicy
import app.lunchlog.core.sync.RetryPolicy.SyncError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class RetryPolicyTest {

    @Test
    fun `間隔は指数的に伸びる`() {
        assertEquals(Duration.ofSeconds(30), RetryPolicy.delayAfter(1))
        assertEquals(Duration.ofSeconds(60), RetryPolicy.delayAfter(2))
        assertEquals(Duration.ofSeconds(120), RetryPolicy.delayAfter(3))
    }

    @Test
    fun `1時間で頭打ちになる`() {
        // 一日放置されても、復帰時に 1 時間以内には再開する。
        assertEquals(RetryPolicy.MAX_DELAY, RetryPolicy.delayAfter(20))
        assertEquals(RetryPolicy.MAX_DELAY, RetryPolicy.delayAfter(100))
    }

    @Test
    fun `0回目以下は例外`() {
        try {
            RetryPolicy.delayAfter(0)
            throw AssertionError("例外が投げられるはず")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `一時的な失敗は再試行する`() {
        // 記録を失うくらいなら再試行し続けるほうがよい (SPEC F-111)。
        assertTrue(RetryPolicy.shouldRetry(SyncError.NETWORK))
        assertTrue(RetryPolicy.shouldRetry(SyncError.SERVER))
        assertTrue(RetryPolicy.shouldRetry(SyncError.RATE_LIMITED))
    }

    @Test
    fun `再試行しても直らないものは諦める`() {
        assertFalse(RetryPolicy.shouldRetry(SyncError.UNAUTHENTICATED))
        assertFalse(RetryPolicy.shouldRetry(SyncError.PERMISSION_DENIED))
        assertFalse(RetryPolicy.shouldRetry(SyncError.INVALID_DATA))
    }
}
