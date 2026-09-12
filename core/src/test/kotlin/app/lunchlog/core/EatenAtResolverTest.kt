package app.lunchlog.core

import app.lunchlog.core.record.EatenAtResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class EatenAtResolverTest {

    private val now = Instant.parse("2026-09-12T03:35:00Z")

    @Test
    fun `EXIFの撮影日時を優先する`() {
        // SPEC F-106: 既定は写真の撮影日時、無ければ保存時刻。
        val taken = Instant.parse("2026-09-12T03:20:00Z")
        assertEquals(taken, EatenAtResolver.resolve(taken, now))
    }

    @Test
    fun `EXIFがなければ保存時刻`() {
        assertEquals(now, EatenAtResolver.resolve(null, now))
    }

    @Test
    fun `古すぎる撮影日時は信用しない`() {
        // 日付が未設定のカメラでよくある値。そのまま採ると 1970 年の記録ができる。
        assertEquals(now, EatenAtResolver.resolve(Instant.parse("1970-01-01T00:00:00Z"), now))
        assertEquals(now, EatenAtResolver.resolve(Instant.parse("1999-12-31T23:59:59Z"), now))
    }

    @Test
    fun `未来すぎる撮影日時は信用しない`() {
        assertEquals(now, EatenAtResolver.resolve(now.plusSeconds(3 * 24 * 3600), now))
    }

    @Test
    fun `端末の時計が少し進んでいる程度は許す`() {
        val slightlyAhead = now.plusSeconds(600)
        assertEquals(slightlyAhead, EatenAtResolver.resolve(slightlyAhead, now))
    }

    @Test
    fun `複数枚なら最も古い撮影日時を採る`() {
        // メニュー写真を先に撮っているため、食事の開始時刻に近い。
        val menu = Instant.parse("2026-09-12T03:10:00Z")
        val dish = Instant.parse("2026-09-12T03:25:00Z")
        assertEquals(menu, EatenAtResolver.resolveFromPhotos(listOf(dish, menu), now))
    }

    @Test
    fun `複数枚で壊れた値が混ざっていても除外する`() {
        val broken = Instant.parse("1970-01-01T00:00:00Z")
        val dish = Instant.parse("2026-09-12T03:25:00Z")
        assertEquals(dish, EatenAtResolver.resolveFromPhotos(listOf(broken, dish), now))
    }

    @Test
    fun `使える撮影日時が1つもなければ保存時刻`() {
        assertEquals(now, EatenAtResolver.resolveFromPhotos(listOf(null, null), now))
        assertEquals(now, EatenAtResolver.resolveFromPhotos(emptyList(), now))
    }
}
