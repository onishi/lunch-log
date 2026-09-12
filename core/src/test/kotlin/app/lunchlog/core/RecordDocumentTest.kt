package app.lunchlog.core

import app.lunchlog.core.model.GeoLocation
import app.lunchlog.core.model.LocationSource
import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import app.lunchlog.core.model.Photo
import app.lunchlog.core.model.PhotoKind
import app.lunchlog.core.model.RecordSource
import app.lunchlog.core.remote.RecordDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecordDocumentTest {

    private val now = Instant.parse("2026-09-12T03:35:00Z")

    private val full = LunchRecord(
        id = "01K0000000000000000000000A",
        eatenAt = now,
        mealType = MealType.LUNCH,
        dishName = "日替わり定食",
        restaurantName = "◯◯食堂",
        restaurantId = "rst_1",
        photos = listOf(
            Photo(
                id = "pho_1",
                kind = PhotoKind.DISH,
                localPath = "/data/local/pho_1.jpg",
                thumbPath = "users/u/thumbs/pho_1.jpg",
                displayPath = "users/u/photos/pho_1.jpg",
                width = 2048,
                height = 1536,
                takenAt = now.minusSeconds(60),
                order = 0,
            ),
        ),
        location = GeoLocation(35.6581, 139.7016, accuracy = 12.5f, source = LocationSource.DEVICE),
        price = 980,
        rating = 4,
        memo = "味噌汁がうまい",
        tags = listOf("定食", "リピート"),
        tabelogUrl = "https://tabelog.com/tokyo/A1303/",
        source = RecordSource.CAMERA,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `往復して同じ値に戻る`() {
        val restored = RecordDocument.fromMap(RecordDocument.toMap(full))
        assertNotNull(restored)
        // localPath は端末ごとの値なので同期しない。それ以外は一致する。
        assertEquals(full.copy(photos = full.photos.map { it.copy(localPath = null) }), restored)
    }

    @Test
    fun `日時はISO8601の文字列で保存する`() {
        val map = RecordDocument.toMap(full)
        assertEquals("2026-09-12T03:35:00Z", map["eatenAt"])
        // 文字列の辞書順が時刻順になる (一覧の並べ替えがこのまま成り立つ)
        val older = RecordDocument.toMap(full.copy(eatenAt = now.minusSeconds(3600)))
        assertTrue((older["eatenAt"] as String) < (map["eatenAt"] as String))
    }

    @Test
    fun `位置にはgeohashを添える`() {
        @Suppress("UNCHECKED_CAST")
        val location = RecordDocument.toMap(full)["location"] as Map<String, Any?>
        assertEquals("xn76fgr", location["geohash"])
    }

    @Test
    fun `idかeatenAtが読めないものは捨てる`() {
        val map = RecordDocument.toMap(full).toMutableMap()
        assertNull(RecordDocument.fromMap(map.toMutableMap().apply { remove("id") }))
        assertNull(RecordDocument.fromMap(map.toMutableMap().apply { remove("eatenAt") }))
        assertNull(RecordDocument.fromMap(map.toMutableMap().apply { put("eatenAt", "壊れた日時") }))
    }

    @Test
    fun `欠けた項目は既定値で埋める`() {
        // 別の端末や将来のバージョンが書いたドキュメントを読むことがある。
        val minimal = mapOf<String, Any?>(
            "id" to "rec_1",
            "eatenAt" to "2026-09-12T03:35:00Z",
        )
        val restored = RecordDocument.fromMap(minimal)!!
        assertEquals("rec_1", restored.id)
        assertEquals(MealType.OTHER, restored.mealType) // 未知の値は OTHER
        assertEquals("JPY", restored.currency)
        assertEquals(emptyList<String>(), restored.tags)
        assertTrue(restored.photos.isEmpty())
        assertNull(restored.location)
        assertEquals(restored.eatenAt, restored.createdAt) // createdAt 欠損は eatenAt で埋める
    }

    @Test
    fun `知らない項目は無視する`() {
        val map = RecordDocument.toMap(full) + mapOf("futureField" to "将来の項目")
        assertNotNull(RecordDocument.fromMap(map))
    }

    @Test
    fun `壊れた位置情報で全体を落とさない`() {
        // 1 件の壊れたドキュメントで一覧全体が落ちるのを防ぐ。
        val map = RecordDocument.toMap(full).toMutableMap()
        map["location"] = mapOf("lat" to 999.0, "lng" to 139.7)
        val restored = RecordDocument.fromMap(map)
        assertNotNull(restored)
        assertNull(restored!!.location)
    }

    @Test
    fun `Firestoreが返すLongをIntとして読む`() {
        // Firestore の数値は Long で返ってくる。
        val map = RecordDocument.toMap(full).toMutableMap()
        map["price"] = 980L
        map["rating"] = 4L
        val restored = RecordDocument.fromMap(map)!!
        assertEquals(980, restored.price)
        assertEquals(4, restored.rating)
    }

    @Test
    fun `写真はorder順に並べ直す`() {
        val map = RecordDocument.toMap(
            full.copy(
                photos = listOf(
                    Photo(id = "b", order = 2),
                    Photo(id = "a", order = 1),
                ),
            ),
        )
        assertEquals(listOf("a", "b"), RecordDocument.fromMap(map)!!.photos.map { it.id })
    }

    @Test
    fun `空文字はnullとして読む`() {
        val map = RecordDocument.toMap(full).toMutableMap()
        map["dishName"] = ""
        map["memo"] = "   "
        val restored = RecordDocument.fromMap(map)!!
        assertNull(restored.dishName)
        assertNull(restored.memo)
    }

    @Test
    fun `削除済みの記録も往復できる`() {
        val deleted = full.copy(deletedAt = now.plusSeconds(60))
        val restored = RecordDocument.fromMap(RecordDocument.toMap(deleted))!!
        assertTrue(restored.isDeleted)
        assertEquals(deleted.deletedAt, restored.deletedAt)
    }
}
