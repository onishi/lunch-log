package app.lunchlog.core

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import app.lunchlog.core.timeline.TimelineGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimelineGroupingTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun record(
        id: String,
        at: String,
        mealType: MealType = MealType.LUNCH,
        deleted: Boolean = false,
    ): LunchRecord {
        val instant = Instant.parse(at)
        return LunchRecord(
            id = id,
            eatenAt = instant,
            mealType = mealType,
            dishName = "定食",
            createdAt = instant,
            updatedAt = instant,
            deletedAt = if (deleted) instant else null,
        )
    }

    @Test
    fun `日付ごとにまとめ新しい順に並べる`() {
        val sections = TimelineGrouping.group(
            listOf(
                record("a", "2026-09-10T03:00:00Z"),
                record("b", "2026-09-12T03:00:00Z"),
                record("c", "2026-09-12T04:00:00Z"),
            ),
            tokyo,
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 10)), sections.map { it.date })
        // 同じ日の中も新しい順
        assertEquals(listOf("c", "b"), sections[0].records.map { it.id })
        assertEquals(listOf("a"), sections[1].records.map { it.id })
    }

    @Test
    fun `日付の区切りは利用者のタイムゾーン`() {
        // 2026-09-12T15:30Z = 東京では 9/13 00:30。UTC で切ると前日に紛れ込む。
        val sections = TimelineGrouping.group(
            listOf(record("late", "2026-09-12T15:30:00Z", MealType.SNACK)),
            tokyo,
            mealTypes = emptySet(),
        )
        assertEquals(LocalDate.of(2026, 9, 13), sections.single().date)
    }

    @Test
    fun `既定ではランチだけを表示する`() {
        // SPEC F-206: 一覧の既定フィルタはランチのみ。
        val sections = TimelineGrouping.group(
            listOf(
                record("lunch", "2026-09-12T03:00:00Z", MealType.LUNCH),
                record("dinner", "2026-09-12T11:00:00Z", MealType.DINNER),
            ),
            tokyo,
        )
        assertEquals(listOf("lunch"), sections.flatMap { it.records }.map { it.id })
    }

    @Test
    fun `空集合を渡すと全種別を表示する`() {
        val sections = TimelineGrouping.group(
            listOf(
                record("lunch", "2026-09-12T03:00:00Z", MealType.LUNCH),
                record("dinner", "2026-09-12T11:00:00Z", MealType.DINNER),
            ),
            tokyo,
            mealTypes = emptySet(),
        )
        assertEquals(2, sections.flatMap { it.records }.size)
    }

    @Test
    fun `削除済みは一覧に出さない`() {
        val sections = TimelineGrouping.group(
            listOf(record("kept", "2026-09-12T03:00:00Z"), record("gone", "2026-09-12T04:00:00Z", deleted = true)),
            tokyo,
        )
        assertEquals(listOf("kept"), sections.flatMap { it.records }.map { it.id })
    }

    @Test
    fun `記録がなければ空`() {
        assertTrue(TimelineGrouping.group(emptyList(), tokyo).isEmpty())
    }
}
