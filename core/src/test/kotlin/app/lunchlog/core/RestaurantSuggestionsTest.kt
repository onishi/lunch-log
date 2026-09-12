package app.lunchlog.core

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.restaurant.RestaurantSuggestion
import app.lunchlog.core.restaurant.RestaurantSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RestaurantSuggestionsTest {

    private fun record(name: String?, at: String, deleted: Boolean = false): LunchRecord {
        val instant = Instant.parse(at)
        return LunchRecord(
            id = "rec-$name-$at",
            eatenAt = instant,
            restaurantName = name,
            dishName = "定食",
            createdAt = instant,
            updatedAt = instant,
            deletedAt = if (deleted) instant else null,
        )
    }

    private fun nearby(name: String, distance: Int, placeId: String = name) = RestaurantSuggestion(
        name = name,
        placeId = placeId,
        distanceMeters = distance,
        source = RestaurantSuggestion.Source.NEARBY,
    )

    @Test
    fun `履歴は最近行った順`() {
        // SPEC §6.1 のフォールバック: 位置情報が使えないとき直近の店を出す。
        val suggestions = RestaurantSuggestions.fromHistory(
            listOf(
                record("古い店", "2026-09-01T03:00:00Z"),
                record("最近の店", "2026-09-11T03:00:00Z"),
                record("中くらいの店", "2026-09-05T03:00:00Z"),
            ),
        )
        assertEquals(listOf("最近の店", "中くらいの店", "古い店"), suggestions.map { it.name })
        assertTrue(suggestions.all { it.source == RestaurantSuggestion.Source.HISTORY })
    }

    @Test
    fun `同じ店は1件にまとめる`() {
        val suggestions = RestaurantSuggestions.fromHistory(
            listOf(
                record("◯◯食堂", "2026-09-11T03:00:00Z"),
                record("◯◯食堂", "2026-09-09T03:00:00Z"),
                record("△△亭", "2026-09-10T03:00:00Z"),
            ),
        )
        assertEquals(listOf("◯◯食堂", "△△亭"), suggestions.map { it.name })
    }

    @Test
    fun `店名のない記録と削除済みは候補にしない`() {
        val suggestions = RestaurantSuggestions.fromHistory(
            listOf(
                record(null, "2026-09-11T03:00:00Z"),
                record("  ", "2026-09-10T03:00:00Z"),
                record("消した店", "2026-09-09T03:00:00Z", deleted = true),
                record("残る店", "2026-09-08T03:00:00Z"),
            ),
        )
        assertEquals(listOf("残る店"), suggestions.map { it.name })
    }

    @Test
    fun `上限5件まで`() {
        val records = (1..10).map { record("店$it", "2026-09-%02dT03:00:00Z".format(it)) }
        assertEquals(RestaurantSuggestions.MAX_SUGGESTIONS, RestaurantSuggestions.fromHistory(records).size)
    }

    @Test
    fun `期間で絞り込める`() {
        val suggestions = RestaurantSuggestions.fromHistory(
            listOf(record("古い店", "2026-08-01T03:00:00Z"), record("新しい店", "2026-09-11T03:00:00Z")),
            since = Instant.parse("2026-09-01T00:00:00Z"),
        )
        assertEquals(listOf("新しい店"), suggestions.map { it.name })
    }

    @Test
    fun `位置情報の候補を履歴より先に出す`() {
        val merged = RestaurantSuggestions.merge(
            nearby = listOf(nearby("目の前の店", 20)),
            history = listOf(RestaurantSuggestion("昨日の店", source = RestaurantSuggestion.Source.HISTORY)),
        )
        assertEquals(listOf("目の前の店", "昨日の店"), merged.map { it.name })
    }

    @Test
    fun `両方に出た店は位置情報側を残す`() {
        // placeId が付いているほうが店舗マスタに紐づけられる。
        val merged = RestaurantSuggestions.merge(
            nearby = listOf(nearby("◯◯食堂", 20, placeId = "ChIJ1")),
            history = listOf(RestaurantSuggestion("◯◯食堂", source = RestaurantSuggestion.Source.HISTORY)),
        )
        assertEquals(1, merged.size)
        assertEquals("ChIJ1", merged.single().placeId)
    }

    @Test
    fun `表記ゆれの重複も1件にする`() {
        val merged = RestaurantSuggestions.merge(
            nearby = listOf(nearby("◯◯ 食堂", 20)),
            history = listOf(RestaurantSuggestion("◯◯　食堂 ", source = RestaurantSuggestion.Source.HISTORY)),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `行ったことのある店を距離より優先する`() {
        // SPEC §6.1: 訪問済みは大きく加点する。距離だけで並べると、
        // 隣のビルの初めての店が上に来てしまう。
        val ranked = RestaurantSuggestions.rankByHistory(
            nearby = listOf(nearby("初めての店", 20), nearby("いつもの店", 120)),
            history = listOf(record("いつもの店", "2026-09-11T03:00:00Z")),
        )
        assertEquals(listOf("いつもの店", "初めての店"), ranked.map { it.name })
    }

    @Test
    fun `どちらも未訪問なら距離順`() {
        val ranked = RestaurantSuggestions.rankByHistory(
            nearby = listOf(nearby("遠い店", 200), nearby("近い店", 30)),
            history = emptyList(),
        )
        assertEquals(listOf("近い店", "遠い店"), ranked.map { it.name })
    }

    @Test
    fun `削除済みの記録は訪問済みとみなさない`() {
        val ranked = RestaurantSuggestions.rankByHistory(
            nearby = listOf(nearby("近い店", 30), nearby("消した店", 200)),
            history = listOf(record("消した店", "2026-09-11T03:00:00Z", deleted = true)),
        )
        assertEquals(listOf("近い店", "消した店"), ranked.map { it.name })
    }

    @Test
    fun `合わせても上限を超えない`() {
        val merged = RestaurantSuggestions.merge(
            nearby = (1..4).map { nearby("近$it", it * 10) },
            history = (1..4).map { RestaurantSuggestion("履歴$it", source = RestaurantSuggestion.Source.HISTORY) },
        )
        assertEquals(RestaurantSuggestions.MAX_SUGGESTIONS, merged.size)
    }
}
