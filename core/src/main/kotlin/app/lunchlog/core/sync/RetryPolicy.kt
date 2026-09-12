package app.lunchlog.core.sync

import java.time.Duration

/**
 * 同期の再試行間隔 (SPEC F-111)。
 *
 * 圏外で記録して、復帰したら同期される、が MVP の受け入れ条件のひとつ。
 * 失敗をすぐ諦めない一方、電池と通信を浪費しないよう指数的に間隔を空ける。
 *
 * 実際の再試行は WorkManager が行うため、ここは**間隔の決め方だけ**を持つ。
 */
object RetryPolicy {

    val INITIAL_DELAY: Duration = Duration.ofSeconds(30)
    val MAX_DELAY: Duration = Duration.ofHours(1)
    const val BACKOFF_MULTIPLIER = 2.0

    /**
     * [attempt] 回目 (1 始まり) の失敗後に待つ時間。
     * 上限で頭打ちにする — 一日放置されても復帰時に 1 時間以内には再開する。
     */
    fun delayAfter(attempt: Int): Duration {
        require(attempt >= 1) { "attempt は 1 以上であること: $attempt" }
        val seconds = INITIAL_DELAY.seconds * Math.pow(BACKOFF_MULTIPLIER, (attempt - 1).toDouble())
        return if (seconds >= MAX_DELAY.seconds) MAX_DELAY else Duration.ofSeconds(seconds.toLong())
    }

    /**
     * 再試行を続けるべきか。
     *
     * **回数では諦めない。** 記録を失うくらいなら再試行し続けるほうがよい
     * (間隔は上限で頭打ちになるので負荷は増えない)。諦めるのは、
     * 再試行しても直らないと分かっているときだけ。
     */
    fun shouldRetry(error: SyncError): Boolean = when (error) {
        SyncError.NETWORK, SyncError.SERVER, SyncError.RATE_LIMITED -> true
        SyncError.UNAUTHENTICATED -> false // 再ログインが要る
        SyncError.PERMISSION_DENIED -> false // ルール違反。再試行しても同じ
        SyncError.INVALID_DATA -> false // 送る内容が壊れている
    }

    enum class SyncError {
        NETWORK,
        SERVER,
        RATE_LIMITED,
        UNAUTHENTICATED,
        PERMISSION_DENIED,
        INVALID_DATA,
    }
}
