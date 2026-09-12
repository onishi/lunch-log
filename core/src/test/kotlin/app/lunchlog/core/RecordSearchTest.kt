package app.lunchlog.core

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import app.lunchlog.core.search.RecordQuery
import app.lunchlog.core.search.RecordSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecordSearchTest {

    private fun record(
        id: String,
        dishName: String? = "定食",
        restaurantName: String? = "◯◯食堂",
        at: String = "2026-09-12T03:00:00Z",
        mealType: MealType = MealType.LUNCH,
        tags: List<String> = emptyList(),
        rating: Int? = null,
        price: Int? = null,
        memo: String? = null,
        deleted: Boolean = false,
    ): LunchRecord {
        val instant = Instant.parse(at)
        return LunchRecord(
            id = id,
            eatenAt = instant,
            mealType = mealType,
            dishName = dishName,
            restaurantName = restaurantName,
            tags = tags,
            rating = rating,
            price = price,
            memo = memo,
            createdAt = instant,
            updatedAt = instant,
            deletedAt = if (deleted) instant else null,
        )
    }

    private val records = listOf(
        record("a", dishName = "からあげ定食", restaurantName = "鳥小屋", price = 900, rating = 4, tags = listOf("揚げ物")),
        record("b", dishName = "ラーメン", restaurantName = "麺屋", price = 1200, rating = 3, at = "2026-09-11T03:00:00Z"),
        record("c", dishName = "サラダ", restaurantName = "カフェ", price = 700, mealType = MealType.CAFE, memo = "軽め"),
        record("d", dishName = "消したもの", deleted = true),
    )

    @Test
    fun `削除済みは既定で除く`() {
        // 並びは新しい順なので b (前日) が最後に来る。
        val result = RecordSearch.apply(records, RecordQuery())
        assertEquals(listOf("a", "c", "b"), result.map { it.id })
    }

    @Test
    fun `ゴミ箱を見るときは削除済みも含める`() {
        val result = RecordSearch.apply(records, RecordQuery(includeDeleted = true))
        assertTrue("d" in result.map { it.id })
    }

    @Test
    fun `新しい順に並べる`() {
        assertEquals(listOf("a", "c", "b"), RecordSearch.apply(records, RecordQuery()).map { it.id })
    }

    @Test
    fun `メニュー名と店名とメモを横断して検索する`() {
        // SPEC F-205: 店名 / メニュー名 / メモ / タグの部分一致。
        assertEquals(listOf("a"), RecordSearch.apply(records, RecordQuery(text = "からあげ")).map { it.id })
        assertEquals(listOf("b"), RecordSearch.apply(records, RecordQuery(text = "麺屋")).map { it.id })
        assertEquals(listOf("c"), RecordSearch.apply(records, RecordQuery(text = "軽め")).map { it.id })
        assertEquals(listOf("a"), RecordSearch.apply(records, RecordQuery(text = "揚げ物")).map { it.id })
    }

    @Test
    fun `大文字小文字を区別しない`() {
        val withEnglish = listOf(record("e", dishName = "Curry Rice"))
        assertEquals(1, RecordSearch.apply(withEnglish, RecordQuery(text = "curry")).size)
    }

    @Test
    fun `食事種別で絞り込む`() {
        val result = RecordSearch.apply(records, RecordQuery(mealTypes = setOf(MealType.CAFE)))
        assertEquals(listOf("c"), result.map { it.id })
    }

    @Test
    fun `金額の範囲で絞り込む`() {
        assertEquals(listOf("a", "b"), RecordSearch.apply(records, RecordQuery(minPrice = 900)).map { it.id })
        assertEquals(listOf("a", "c"), RecordSearch.apply(records, RecordQuery(maxPrice = 1000)).map { it.id })
    }

    @Test
    fun `評価で絞り込む`() {
        assertEquals(listOf("a"), RecordSearch.apply(records, RecordQuery(minRating = 4)).map { it.id })
    }

    @Test
    fun `期間で絞り込む`() {
        val result = RecordSearch.apply(
            records,
            RecordQuery(from = Instant.parse("2026-09-12T00:00:00Z")),
        )
        assertEquals(listOf("a", "c"), result.map { it.id })
    }

    @Test
    fun `店舗で絞り込む`() {
        assertEquals(listOf("b"), RecordSearch.apply(records, RecordQuery(restaurantName = " 麺屋 ")).map { it.id })
    }

    @Test
    fun `条件を重ねられる`() {
        val result = RecordSearch.apply(
            records,
            RecordQuery(text = "定食", minPrice = 800, mealTypes = setOf(MealType.LUNCH)),
        )
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `一致しなければ空`() {
        assertTrue(RecordSearch.apply(records, RecordQuery(text = "存在しない")).isEmpty())
    }
}
