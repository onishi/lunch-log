package app.lunchlog.core.suggest

import app.lunchlog.core.model.LunchRecord

/**
 * メニュー名の候補 (SPEC F-103)。
 *
 * 出どころは自分の過去の入力。**同じ店の履歴を最優先**にする —
 * ランチは同じ店で同じものを頼むことが多く、いちばん当たりやすい。
 */
object DishSuggestions {

    const val MAX_SUGGESTIONS = 5

    /**
     * @param restaurantName 入力中の店名。一致する記録を優先する。
     * @param query 入力途中の文字列。空なら履歴をそのまま出す。
     */
    fun suggest(
        records: List<LunchRecord>,
        restaurantName: String? = null,
        query: String = "",
        limit: Int = MAX_SUGGESTIONS,
    ): List<String> {
        val normalizedQuery = query.trim().lowercase()
        val normalizedRestaurant = restaurantName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        val usable = records
            .asSequence()
            .filterNot { it.isDeleted }
            .sortedByDescending { it.eatenAt }
            .mapNotNull { record ->
                record.dishName?.trim()?.takeIf { it.isNotEmpty() }?.let { name -> name to record }
            }
            .filter { (name, _) -> normalizedQuery.isEmpty() || name.lowercase().contains(normalizedQuery) }
            .toList()

        val sameRestaurant = usable
            .filter { (_, record) -> record.restaurantName?.trim()?.lowercase() == normalizedRestaurant }
            .map { (name, _) -> name }

        val others = usable.map { (name, _) -> name }

        return (sameRestaurant + others).distinct().take(limit)
    }
}
