package app.lunchlog.core

import app.lunchlog.core.model.MealType
import app.lunchlog.core.model.MealTypeInference
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class MealTypeInferenceTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun `11時から15時まではランチ`() {
        // SPEC F-116 が明示している唯一の範囲。ここは崩さない。
        assertEquals(MealType.LUNCH, MealTypeInference.infer(LocalTime.of(11, 0)))
        assertEquals(MealType.LUNCH, MealTypeInference.infer(LocalTime.of(12, 35)))
        assertEquals(MealType.LUNCH, MealTypeInference.infer(LocalTime.of(14, 59)))
    }

    @Test
    fun `11時の直前と15時ちょうどはランチではない`() {
        assertEquals(MealType.BREAKFAST, MealTypeInference.infer(LocalTime.of(10, 59)))
        assertEquals(MealType.CAFE, MealTypeInference.infer(LocalTime.of(15, 0)))
    }

    @Test
    fun `ランチ以外の時間帯`() {
        assertEquals(MealType.BREAKFAST, MealTypeInference.infer(LocalTime.of(7, 30)))
        assertEquals(MealType.CAFE, MealTypeInference.infer(LocalTime.of(16, 0)))
        assertEquals(MealType.DINNER, MealTypeInference.infer(LocalTime.of(19, 0)))
        assertEquals(MealType.SNACK, MealTypeInference.infer(LocalTime.of(23, 30)))
        assertEquals(MealType.SNACK, MealTypeInference.infer(LocalTime.of(3, 0)))
    }

    @Test
    fun `UTCではなく利用者のタイムゾーンで判定する`() {
        // 2026-09-12T03:35:00Z = 東京時間 12:35 → ランチ
        val at = Instant.parse("2026-09-12T03:35:00Z")
        assertEquals(MealType.LUNCH, MealTypeInference.infer(at, tokyo))
        // 同じ瞬間でも UTC で見れば 03:35 なので夜食扱いになる。取り違えの検知用。
        assertEquals(MealType.SNACK, MealTypeInference.infer(at, ZoneId.of("UTC")))
    }

    @Test
    fun `未知のidはOTHERとして読み込む`() {
        assertEquals(MealType.OTHER, MealType.fromId("brunch"))
        assertEquals(MealType.OTHER, MealType.fromId(null))
        assertEquals(MealType.LUNCH, MealType.fromId("lunch"))
    }
}
