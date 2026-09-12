package app.lunchlog.core

import app.lunchlog.core.geo.Geohash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeohashTest {

    @Test
    fun `既知の値と一致する`() {
        // geohash の仕様でよく使われる検証用の座標 (デンマーク)。
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, precision = 11))
        assertEquals("u4pruyd", Geohash.encode(57.64911, 10.40744, precision = 7))
    }

    @Test
    fun `精度を下げると前方一致になる`() {
        val full = Geohash.encode(35.6581, 139.7016, precision = 9)
        val short = Geohash.encode(35.6581, 139.7016, precision = 5)
        assertTrue("$full が $short で始まること", full.startsWith(short))
    }

    @Test
    fun `近い2点は同じ精度5のハッシュになる`() {
        // 渋谷駅前の 2 点 (約 100m 差)。店舗候補のキャッシュキーとして使える粒度。
        val a = Geohash.encode(35.6581, 139.7016, precision = 5)
        val b = Geohash.encode(35.6589, 139.7020, precision = 5)
        assertEquals(a, b)
    }

    @Test
    fun `遠い2点は異なるハッシュになる`() {
        val shibuya = Geohash.encode(35.6581, 139.7016)
        val osaka = Geohash.encode(34.7025, 135.4959)
        assertNotEquals(shibuya, osaka)
    }

    @Test
    fun `範囲外の座標は例外`() {
        listOf(
            { Geohash.encode(91.0, 0.0) },
            { Geohash.encode(0.0, 181.0) },
            { Geohash.encode(0.0, 0.0, precision = 0) },
        ).forEach { block ->
            try {
                block()
                throw AssertionError("例外が投げられるはず")
            } catch (expected: IllegalArgumentException) {
                // ok
            }
        }
    }
}
