package app.lunchlog.core.search

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import java.time.Instant

/**
 * 記録の検索とフィルタ (SPEC F-205, F-206)。
 *
 * 件数は多くても数千で、すべて端末内にある。全文検索の仕組みは入れず、
 * その場で絞り込む (SPEC §7.5 の方針)。
 */
data class RecordQuery(
    /** 店名 / メニュー名 / メモ / タグへの部分一致。 */
    val text: String = "",
    /** 空なら種別で絞らない。 */
    val mealTypes: Set<MealType> = emptySet(),
    val tags: Set<String> = emptySet(),
    val restaurantName: String? = null,
    val minRating: Int? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    /** 削除済み (ゴミ箱) を対象にするか。 */
    val includeDeleted: Boolean = false,
)

object RecordSearch {

    fun apply(records: List<LunchRecord>, query: RecordQuery): List<LunchRecord> {
        val text = query.text.trim().lowercase()
        val tags = query.tags.map { it.lowercase() }.toSet()
        val restaurant = query.restaurantName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        return records
            .asSequence()
            .filter { query.includeDeleted || !it.isDeleted }
            .filter { query.mealTypes.isEmpty() || it.mealType in query.mealTypes }
            .filter { text.isEmpty() || it.matches(text) }
            .filter { tags.isEmpty() || it.tags.any { tag -> tag.lowercase() in tags } }
            .filter { restaurant == null || it.restaurantName?.trim()?.lowercase() == restaurant }
            .filter { record -> query.minRating?.let { (record.rating ?: 0) >= it } ?: true }
            .filter { record -> query.minPrice?.let { (record.price ?: 0) >= it } ?: true }
            .filter { record -> query.maxPrice?.let { (record.price ?: Int.MAX_VALUE) <= it } ?: true }
            .filter { record -> query.from?.let { !record.eatenAt.isBefore(it) } ?: true }
            .filter { record -> query.to?.let { !record.eatenAt.isAfter(it) } ?: true }
            .sortedByDescending { it.eatenAt }
            .toList()
    }

    /** 検索語が本文のどこかに含まれるか。大文字小文字と前後の空白は無視する。 */
    private fun LunchRecord.matches(lowercaseText: String): Boolean =
        listOfNotNull(dishName, restaurantName, memo)
            .plus(tags)
            .any { it.lowercase().contains(lowercaseText) }
}
