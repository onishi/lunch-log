package app.lunchlog.core

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.suggest.DishSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DishSuggestionsTest {

    private fun record(
        dishName: String?,
        restaurantName: String? = "◯◯食堂",
        at: String = "2026-09-12T03:00:00Z",
        deleted: Boolean = false,
    ): LunchRecord {
        val instant = Instant.parse(at)
        return LunchRecord(
            id = "rec-$dishName-$at",
            eatenAt = instant,
            dishName = dishName,
            restaurantName = restaurantName,
            createdAt = instant,
            updatedAt = instant,
            deletedAt = if (deleted) instant else null,
        )
    }

    @Test
    fun `最近食べた順に出す`() {
        val suggestions = DishSuggestions.suggest(
            listOf(
                record("古いもの", at = "2026-09-01T03:00:00Z"),
                record("新しいもの", at = "2026-09-11T03:00:00Z"),
            ),
        )
        assertEquals(listOf("新しいもの", "古いもの"), suggestions)
    }

    @Test
    fun `同じ店の履歴を優先する`() {
        // ランチは同じ店で同じものを頼むことが多い。
        val suggestions = DishSuggestions.suggest(
            listOf(
                record("他店のもの", restaurantName = "別の店", at = "2026-09-11T03:00:00Z"),
                record("この店のもの", restaurantName = "◯◯食堂", at = "2026-09-01T03:00:00Z"),
            ),
            restaurantName = "◯◯食堂",
        )
        assertEquals(listOf("この店のもの", "他店のもの"), suggestions)
    }

    @Test
    fun `入力中の文字で絞り込む`() {
        val suggestions = DishSuggestions.suggest(
            listOf(record("からあげ定食"), record("ラーメン", at = "2026-09-10T03:00:00Z")),
            query = "から",
        )
        assertEquals(listOf("からあげ定食"), suggestions)
    }

    @Test
    fun `同じメニューは1件にまとめる`() {
        val suggestions = DishSuggestions.suggest(
            listOf(record("カレー", at = "2026-09-11T03:00:00Z"), record("カレー", at = "2026-09-01T03:00:00Z")),
        )
        assertEquals(listOf("カレー"), suggestions)
    }

    @Test
    fun `メニュー名のない記録と削除済みは無視する`() {
        val suggestions = DishSuggestions.suggest(
            listOf(record(null), record("  "), record("消したもの", deleted = true), record("残るもの")),
        )
        assertEquals(listOf("残るもの"), suggestions)
    }

    @Test
    fun `上限まで`() {
        val records = (1..10).map { record("メニュー$it", at = "2026-09-%02dT03:00:00Z".format(it)) }
        assertEquals(DishSuggestions.MAX_SUGGESTIONS, DishSuggestions.suggest(records).size)
    }

    @Test
    fun `履歴がなければ空`() {
        assertTrue(DishSuggestions.suggest(emptyList()).isEmpty())
    }
}
