package app.lunchlog.core

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import app.lunchlog.core.model.Photo
import app.lunchlog.core.model.PhotoKind
import app.lunchlog.core.validation.RecordError
import app.lunchlog.core.validation.RecordValidator
import app.lunchlog.core.validation.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecordValidatorTest {

    private val now = Instant.parse("2026-09-12T03:35:00Z")

    private fun record(
        dishName: String? = null,
        photos: List<Photo> = emptyList(),
        rating: Int? = null,
        price: Int? = null,
        memo: String? = null,
        tags: List<String> = emptyList(),
        restaurantName: String? = null,
    ) = LunchRecord(
        id = "01K0000000000000000000000A",
        eatenAt = now,
        mealType = MealType.LUNCH,
        dishName = dishName,
        restaurantName = restaurantName,
        photos = photos,
        rating = rating,
        price = price,
        memo = memo,
        tags = tags,
        createdAt = now,
        updatedAt = now,
    )

    private fun photo(order: Int = 0, kind: PhotoKind = PhotoKind.DISH) =
        Photo(id = "pho_$order", kind = kind, order = order)

    private fun errorsOf(record: LunchRecord): List<RecordError> =
        (RecordValidator.validate(record) as? ValidationResult.Invalid)?.errors ?: emptyList()

    @Test
    fun `写真だけでも保存できる`() {
        // SPEC §9.1: 必須は「写真 1 枚」か「メニュー名」のどちらか一方だけ。
        assertEquals(ValidationResult.Valid, RecordValidator.validate(record(photos = listOf(photo()))))
    }

    @Test
    fun `メニュー名だけでも保存できる`() {
        assertEquals(ValidationResult.Valid, RecordValidator.validate(record(dishName = "日替わり定食")))
    }

    @Test
    fun `写真もメニュー名もなければ保存できない`() {
        assertTrue(RecordError.EMPTY_RECORD in errorsOf(record(restaurantName = "◯◯食堂")))
    }

    @Test
    fun `空白だけのメニュー名は入力なしとみなす`() {
        assertTrue(RecordError.EMPTY_RECORD in errorsOf(record(dishName = "   ")))
    }

    @Test
    fun `評価は1から5まで`() {
        assertEquals(emptyList<RecordError>(), errorsOf(record(dishName = "定食", rating = 5)))
        assertTrue(RecordError.RATING_OUT_OF_RANGE in errorsOf(record(dishName = "定食", rating = 0)))
        assertTrue(RecordError.RATING_OUT_OF_RANGE in errorsOf(record(dishName = "定食", rating = 6)))
    }

    @Test
    fun `金額は負にできない`() {
        assertTrue(RecordError.NEGATIVE_PRICE in errorsOf(record(dishName = "定食", price = -1)))
        assertEquals(emptyList<RecordError>(), errorsOf(record(dishName = "定食", price = 0)))
    }

    @Test
    fun `写真は10枚まで`() {
        val eleven = (0..10).map { photo(order = it) }
        assertTrue(RecordError.TOO_MANY_PHOTOS in errorsOf(record(photos = eleven)))
        assertEquals(emptyList<RecordError>(), errorsOf(record(photos = eleven.take(10))))
    }

    @Test
    fun `長すぎる入力を弾く`() {
        assertTrue(RecordError.DISH_NAME_TOO_LONG in errorsOf(record(dishName = "あ".repeat(201))))
        assertTrue(RecordError.MEMO_TOO_LONG in errorsOf(record(dishName = "定食", memo = "あ".repeat(2001))))
    }

    @Test
    fun `normalizeは前後の空白を落とす`() {
        val normalized = RecordValidator.normalize(
            record(dishName = "  日替わり定食 ", restaurantName = " ◯◯食堂", memo = " うまい  "),
        )
        assertEquals("日替わり定食", normalized.dishName)
        assertEquals("◯◯食堂", normalized.restaurantName)
        assertEquals("うまい", normalized.memo)
    }

    @Test
    fun `normalizeは空文字をnullにする`() {
        val normalized = RecordValidator.normalize(record(dishName = "   ", photos = listOf(photo())))
        assertEquals(null, normalized.dishName)
    }

    @Test
    fun `normalizeはタグを整える`() {
        val normalized = RecordValidator.normalize(
            record(dishName = "定食", tags = listOf(" #定食 ", "定食", "", "リピート")),
        )
        // 先頭の # を落とし、空と重複を除き、順序は保つ
        assertEquals(listOf("定食", "リピート"), normalized.tags)
    }
}
