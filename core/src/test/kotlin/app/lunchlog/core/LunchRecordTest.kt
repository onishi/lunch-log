package app.lunchlog.core

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.Photo
import app.lunchlog.core.model.PhotoKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LunchRecordTest {

    private val now = Instant.parse("2026-09-12T03:35:00Z")

    private fun record(photos: List<Photo> = emptyList(), deletedAt: Instant? = null) = LunchRecord(
        id = "01K0000000000000000000000A",
        eatenAt = now,
        dishName = "日替わり定食",
        restaurantName = "◯◯食堂",
        photos = photos,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    @Test
    fun `カバー写真は料理写真を優先する`() {
        val menu = Photo(id = "p1", kind = PhotoKind.MENU, order = 0)
        val dish = Photo(id = "p2", kind = PhotoKind.DISH, order = 1)
        assertEquals("p2", record(photos = listOf(menu, dish)).coverPhoto?.id)
    }

    @Test
    fun `料理写真がなければ先頭の写真を使う`() {
        val menu = Photo(id = "p1", kind = PhotoKind.MENU, order = 1)
        val receipt = Photo(id = "p2", kind = PhotoKind.RECEIPT, order = 0)
        assertEquals("p2", record(photos = listOf(menu, receipt)).coverPhoto?.id)
    }

    @Test
    fun `写真がなければカバー写真はnull`() {
        assertNull(record().coverPhoto)
    }

    @Test
    fun `論理削除の判定`() {
        assertFalse(record().isDeleted)
        assertTrue(record(deletedAt = now).isDeleted)
    }

    @Test
    fun `表示名はメニュー名を優先し店名にフォールバックする`() {
        assertEquals("日替わり定食", record().displayTitle)
        assertEquals("◯◯食堂", record().copy(dishName = null).displayTitle)
        assertEquals("◯◯食堂", record().copy(dishName = " ").displayTitle)
        assertNull(record().copy(dishName = null, restaurantName = null).displayTitle)
    }

    @Test
    fun `アップロード済みかどうかを表示用パスで判定する`() {
        assertFalse(Photo(id = "p1").isUploaded)
        assertTrue(Photo(id = "p1", displayPath = "users/u/photos/p1.jpg").isUploaded)
    }
}
