package app.lunchlog.core

import app.lunchlog.core.id.Ulid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Random

class UlidTest {

    @Test
    fun `26文字のCrockfordBase32になる`() {
        val ulid = Ulid.generate()
        assertEquals(26, ulid.length)
        assertTrue(Ulid.isValid(ulid))
        // I, L, O, U は含まない (読み違いを避ける Crockford の規約)
        assertTrue(ulid.none { it in "ILOU" })
    }

    @Test
    fun `生成時刻を復元できる`() {
        val at = Instant.parse("2026-09-12T03:35:00Z")
        val ulid = Ulid.generate(at)
        assertEquals(at, Ulid.timestampOf(ulid))
    }

    @Test
    fun `辞書順が時刻順と一致する`() {
        // Firestore の ID 順＝時系列、を成り立たせるための性質。
        val fixed = Random(42)
        val early = Ulid.generate(Instant.parse("2026-09-12T03:00:00Z"), fixed)
        val late = Ulid.generate(Instant.parse("2026-09-12T12:00:00Z"), fixed)
        assertTrue("$early < $late であること", early < late)
    }

    @Test
    fun `同じ時刻でも別のIDになる`() {
        val at = Instant.parse("2026-09-12T03:35:00Z")
        assertNotEquals(Ulid.generate(at), Ulid.generate(at))
    }

    @Test
    fun `不正な文字列は弾く`() {
        assertTrue(!Ulid.isValid("short"))
        assertTrue(!Ulid.isValid("I".repeat(26))) // 使わない文字
        assertNull(Ulid.timestampOf("not-a-ulid"))
    }
}
