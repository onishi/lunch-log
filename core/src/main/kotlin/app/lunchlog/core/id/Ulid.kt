package app.lunchlog.core.id

import java.security.SecureRandom
import java.time.Instant
import java.util.Random

/**
 * ULID の生成 (SPEC §7.2 の記録 ID)。
 *
 * 26 文字の Crockford Base32。先頭 10 文字が 48bit のミリ秒タイムスタンプ、
 * 残り 16 文字が 80bit のランダム。辞書順が生成時刻順と一致するため、
 * Firestore のドキュメント ID として並べ替えなしに時系列を保てる。
 */
object Ulid {

    private const val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford Base32 (I, L, O, U を除く)
    private const val TIME_LENGTH = 10
    private const val RANDOM_LENGTH = 16
    const val LENGTH = TIME_LENGTH + RANDOM_LENGTH

    private val secureRandom by lazy { SecureRandom() }

    fun generate(at: Instant = Instant.now(), random: Random = secureRandom): String =
        buildString(LENGTH) {
            appendTime(at.toEpochMilli())
            appendRandom(random)
        }

    /** ID から生成時刻を取り出す。形式が不正なら null。 */
    fun timestampOf(ulid: String): Instant? {
        if (!isValid(ulid)) return null
        var millis = 0L
        for (i in 0 until TIME_LENGTH) {
            val value = ENCODING.indexOf(ulid[i])
            if (value < 0) return null
            millis = millis * 32 + value
        }
        return Instant.ofEpochMilli(millis)
    }

    fun isValid(ulid: String): Boolean =
        ulid.length == LENGTH && ulid.all { it in ENCODING }

    private fun StringBuilder.appendTime(epochMilli: Long) {
        require(epochMilli >= 0) { "epochMilli は 0 以上であること: $epochMilli" }
        val chars = CharArray(TIME_LENGTH)
        var remaining = epochMilli
        for (i in TIME_LENGTH - 1 downTo 0) {
            chars[i] = ENCODING[(remaining % 32).toInt()]
            remaining /= 32
        }
        require(remaining == 0L) { "タイムスタンプが 48bit に収まらない: $epochMilli" }
        append(chars)
    }

    private fun StringBuilder.appendRandom(random: Random) {
        repeat(RANDOM_LENGTH) { append(ENCODING[random.nextInt(32)]) }
    }
}
