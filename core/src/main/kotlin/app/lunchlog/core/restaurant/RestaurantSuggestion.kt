package app.lunchlog.core.restaurant

import app.lunchlog.core.model.LunchRecord
import java.time.Instant

/**
 * 店名の候補 1 件 (SPEC §6.1)。
 *
 * 位置情報から得た候補と、過去の記録から作った候補を同じ形で扱い、
 * UI では区別せず「チップを 1 タップ」で済ませる。
 */
data class RestaurantSuggestion(
    val name: String,
    val placeId: String? = null,
    val distanceMeters: Int? = null,
    val source: Source,
) {
    enum class Source {
        /** 位置情報からの候補。 */
        NEARBY,

        /** 過去の記録からの候補 (位置情報が使えないときのフォールバック)。 */
        HISTORY,
    }
}

/**
 * 店舗候補の組み立て (SPEC §6.1)。
 *
 * **位置情報が使えなくてもアプリが止まらないこと**が要件。権限拒否・取得失敗・
 * 圏外のいずれでも、直近に行った店を候補として出す。
 */
object RestaurantSuggestions {

    /** 候補チップの数。多すぎると選ぶのが面倒になる (SPEC §6.1 は上位 5 件)。 */
    const val MAX_SUGGESTIONS = 5

    /**
     * 過去の記録から候補を作る。
     *
     * 並びは「最近行った順」。回数の多い店を上げる案もあるが、ランチは
     * 同じ店が続くことが多く、直近のほうが当たりやすい。
     */
    fun fromHistory(
        records: List<LunchRecord>,
        limit: Int = MAX_SUGGESTIONS,
        since: Instant? = null,
    ): List<RestaurantSuggestion> =
        records
            .asSequence()
            .filterNot { it.isDeleted }
            .filter { since == null || !it.eatenAt.isBefore(since) }
            .sortedByDescending { it.eatenAt }
            .mapNotNull { record -> record.restaurantName?.trim()?.takeIf { it.isNotEmpty() } }
            .distinct()
            .take(limit)
            .map { RestaurantSuggestion(name = it, source = RestaurantSuggestion.Source.HISTORY) }
            .toList()

    /**
     * 位置情報からの候補を、過去に行った店を上げて並べ替える (SPEC §6.1)。
     *
     * 距離だけで並べると、隣のビルの初めての店が上に来る。実際には
     * 「前にも行った店」のほうが選ばれやすいので、訪問済みを優先する。
     * 営業時間による加点は、時間の情報を持っていないので入れていない。
     */
    fun rankByHistory(
        nearby: List<RestaurantSuggestion>,
        history: List<LunchRecord>,
        limit: Int = MAX_SUGGESTIONS,
    ): List<RestaurantSuggestion> {
        val visited = history
            .asSequence()
            .filterNot { it.isDeleted }
            .mapNotNull { it.restaurantName?.trim()?.takeIf(String::isNotEmpty) }
            .map(::normalizeName)
            .toSet()

        return nearby
            .sortedWith(
                compareByDescending<RestaurantSuggestion> { normalizeName(it.name) in visited }
                    .thenBy { it.distanceMeters ?: Int.MAX_VALUE }
                    .thenBy { it.name },
            )
            .take(limit)
    }

    /**
     * 位置情報からの候補と履歴の候補を合わせる。
     *
     * 位置情報の候補を先に置く (その場にいる店のほうが当たる)。
     * 同じ店名が両方に出たら位置情報側を残す — placeId が付いていて
     * 店舗マスタに紐づけられるため。
     */
    fun merge(
        nearby: List<RestaurantSuggestion>,
        history: List<RestaurantSuggestion>,
        limit: Int = MAX_SUGGESTIONS,
    ): List<RestaurantSuggestion> {
        val seen = mutableSetOf<String>()
        return (nearby + history)
            .filter { seen.add(normalizeName(it.name)) }
            .take(limit)
    }

    /** 表記ゆれで同じ店が二重に出るのを防ぐ。全角空白と前後の空白を無視する。 */
    private fun normalizeName(name: String): String =
        name.trim().replace('　', ' ').replace(Regex("\\s+"), " ").lowercase()
}
